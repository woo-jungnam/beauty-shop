import React, { useMemo, useRef, useState } from 'react';
import { Award, Check, RefreshCw, Search, Users } from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Modal } from '../../shared/ui/Modal';
import { spaErrorMessage } from '../../shared/utils/spaMessages';

const unwrap = (response) => response?.data ?? response;
const staffList = (response) => {
  const data = unwrap(response);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.content)) return data.content;
  throw new Error('Chưa đọc được danh sách kỹ thuật viên. Vui lòng đồng bộ lại.');
};
const skillFor = (row, serviceId) => row.skills?.find((skill) => String(skill.serviceId) === String(serviceId));
const permissionFor = (row, serviceId) => {
  const skill = skillFor(row, serviceId);
  return { granted: Boolean(skill), certified: Boolean(skill?.certified) };
};
const permissionsFor = (rows, serviceId) => Object.fromEntries(rows.map((row) => [row.id, permissionFor(row, serviceId)]));
const samePermission = (first, second) => Boolean(first?.granted) === Boolean(second?.granted)
  && (!first?.granted || Boolean(first?.certified) === Boolean(second?.certified));
const searchText = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase();
const nameFor = (row) => row.fullName || `Kỹ thuật viên #${row.id}`;
const applyPermission = (row, service, permission) => ({
  ...row,
  skills: [
    ...(row.skills || []).filter((skill) => String(skill.serviceId) !== String(service.id)),
    ...(permission.granted ? [{ serviceId: service.id, serviceName: service.name, certified: permission.certified }] : []),
  ],
});

// Certification records training; an active staff profile with the assigned skill is eligible.
const getEligibleStaffCount = (serviceId, staff = []) => staff.filter((row) => row.active === true && skillFor(row, serviceId)).length;

export default function SkillsMatrix({ service, staff = [], onClose, onSaved }) {
  const [rows, setRows] = useState(() => staff);
  const [draft, setDraft] = useState(() => permissionsFor(staff, service.id));
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);
  const [syncNeeded, setSyncNeeded] = useState(false);
  const [notice, setNotice] = useState('');
  const [failures, setFailures] = useState([]);
  const lockRef = useRef(false);
  const pendingRef = useRef(new Map());

  const baseline = useMemo(() => permissionsFor(rows, service.id), [rows, service.id]);
  const changes = useMemo(() => rows.filter((row) => !samePermission(baseline[row.id], draft[row.id])), [rows, baseline, draft]);
  const visibleRows = useMemo(() => rows.filter((row) => searchText(`${nameFor(row)} ${row.specialty || ''}`).includes(searchText(keyword).trim())), [rows, keyword]);
  const currentEligible = getEligibleStaffCount(service.id, rows);
  const projectedEligible = rows.filter((row) => row.active === true && draft[row.id]?.granted).length;
  const removals = changes.filter((row) => baseline[row.id]?.granted && !draft[row.id]?.granted);

  const close = () => { if (!lockRef.current) onClose?.(); };
  const setPermission = (staffId, field, value) => {
    if (lockRef.current || syncNeeded) return;
    setNotice('');
    setFailures((previous) => previous.filter((failure) => String(failure.id) !== String(staffId)));
    setDraft((previous) => ({
      ...previous,
      [staffId]: { ...previous[staffId], [field]: value, ...(field === 'granted' && !value ? { certified: false } : {}) },
    }));
  };

  // Keep unsuccessful intentions only. Other rows follow the latest server state so
  // retrying does not overwrite a colleague's changes or repeat confirmed writes.
  const reconcile = (canonical, pending) => {
    const nextDraft = permissionsFor(canonical, service.id);
    const unresolved = new Map();
    for (const [id, target] of pending) {
      if (!(id in nextDraft)) continue;
      if (!samePermission(nextDraft[id], target)) {
        nextDraft[id] = target;
        unresolved.set(id, target);
      }
    }
    setRows(canonical);
    setDraft(nextDraft);
    pendingRef.current = unresolved;
    return unresolved;
  };

  const synchronize = async () => {
    if (lockRef.current) return;
    lockRef.current = true;
    setBusy(true);
    let synchronized = false;
    try {
      const canonical = staffList(await apiClient.get(ENDPOINTS.SPA.STAFF));
      const unresolved = reconcile(canonical, pendingRef.current);
      setSyncNeeded(false);
      setFailures([]);
      setNotice(unresolved.size
        ? `Đã đồng bộ với hệ thống. Còn ${unresolved.size} thay đổi chưa được lưu; vui lòng kiểm tra rồi lưu lại.`
        : 'Đã đồng bộ với hệ thống. Các quyền hiện tại đã được xác minh.');
      synchronized = true;
    } catch (error) {
      setNotice(`Chưa xác minh được kết quả lưu. ${spaErrorMessage(error, 'Không thể đọc lại danh sách kỹ thuật viên. Vui lòng thử đồng bộ lại.')}`);
    } finally {
      lockRef.current = false;
      setBusy(false);
    }
    if (synchronized) onSaved?.('Đã đồng bộ quyền thực hiện dịch vụ của kỹ thuật viên.', 'success');
  };

  const save = async () => {
    if (lockRef.current || syncNeeded || !changes.length) return;
    lockRef.current = true;
    setBusy(true);
    setNotice('');
    setFailures([]);
    const attempted = changes.map((row) => ({ row, target: { ...draft[row.id] } }));
    let confirmedRows = [...rows];
    const failed = [];
    const pending = new Map();
    let confirmed = 0;
    let notification;
    let closeAfterSave = false;

    try {
      for (const { row, target } of attempted) {
        try {
          if (target.granted) {
            const response = unwrap(await apiClient.put(ENDPOINTS.SPA.STAFF_SKILLS(row.id), {
              serviceId: Number(service.id), certified: target.certified,
            }));
            const confirmedRow = String(response?.id) === String(row.id) && Array.isArray(response.skills)
              ? response : applyPermission(row, service, target);
            confirmedRows = confirmedRows.map((item) => String(item.id) === String(row.id) ? confirmedRow : item);
          } else {
            await apiClient.delete(ENDPOINTS.SPA.STAFF_SKILL_DELETE(row.id, service.id));
            confirmedRows = confirmedRows.map((item) => String(item.id) === String(row.id) ? applyPermission(item, service, target) : item);
          }
          confirmed += 1;
        } catch (error) {
          pending.set(String(row.id), target);
          failed.push({ id: row.id, name: nameFor(row), message: spaErrorMessage(error, 'Chưa lưu được quyền thực hiện dịch vụ. Vui lòng thử lại.') });
        }
      }

      setRows(confirmedRows);
      setDraft((previous) => {
        const next = permissionsFor(confirmedRows, service.id);
        for (const [id, target] of pending) next[id] = previous[id] || target;
        return next;
      });
      pendingRef.current = pending;

      if (!failed.length) {
        notification = { message: `Đã cập nhật quyền thực hiện dịch vụ cho ${confirmed} kỹ thuật viên.`, tone: 'success' };
        closeAfterSave = true;
      } else {
        try {
          const canonical = staffList(await apiClient.get(ENDPOINTS.SPA.STAFF));
          const unresolved = reconcile(canonical, pending);
          const unresolvedFailures = failed.filter((failure) => unresolved.has(String(failure.id)));
          const missing = failed.filter((failure) => !canonical.some((row) => String(row.id) === String(failure.id)));
          const savedCount = attempted.length - unresolved.size - missing.length;
          setFailures([...unresolvedFailures, ...missing.map((failure) => ({ ...failure, message: 'Hồ sơ kỹ thuật viên không còn trong danh sách. Vui lòng kiểm tra lại hồ sơ trước khi cấp quyền.' }))]);
          const message = unresolved.size || missing.length
            ? `Đã xác minh ${savedCount}/${attempted.length} thay đổi được lưu. Còn ${unresolved.size + missing.length} thay đổi chưa hoàn tất; các thay đổi đã lưu được giữ nguyên.`
            : `Đã đọc lại dữ liệu: toàn bộ ${attempted.length} thay đổi đã được lưu thành công.`;
          setNotice(message);
          notification = { message, tone: unresolved.size || missing.length ? 'warning' : 'success' };
        } catch (error) {
          setSyncNeeded(true);
          setFailures(failed);
          const message = `Đã nhận xác nhận lưu ${confirmed}/${attempted.length} thay đổi. Chưa xác minh được các thay đổi còn lại. ${spaErrorMessage(error, 'Vui lòng đồng bộ lại danh sách trước khi lưu tiếp.')}`;
          setNotice(message);
          notification = { message, tone: 'warning' };
        }
      }
    } finally {
      lockRef.current = false;
      setBusy(false);
    }
    if (notification) onSaved?.(notification.message, notification.tone);
    if (closeAfterSave) onClose?.();
  };

  return (
    <Modal
      isOpen
      onClose={close}
      title="Ma trận kỹ năng & chứng nhận"
      maxWidth="760px"
      className="spa-catalog-dialog"
      footer={(
        <>
          <Button variant="secondary" onClick={close} disabled={busy}>Đóng</Button>
          {syncNeeded ? (
            <Button icon={RefreshCw} onClick={synchronize} loading={busy}>Đồng bộ lại danh sách</Button>
          ) : (
            <Button icon={Check} onClick={save} loading={busy} disabled={!changes.length}>
              {changes.length ? `Lưu ${changes.length} thay đổi` : 'Đã cập nhật'}
            </Button>
          )}
        </>
      )}
    >
      <div className="spa-skills-matrix">
        <div className="spa-skills-intro">
          <div className="spa-skills-intro-icon"><Award size={23} aria-hidden="true" /></div>
          <div>
            <h4>{service.name}</h4>
            <p>Gán quyền thực hiện dịch vụ và ghi nhận chứng nhận chuyên môn cho từng kỹ thuật viên.</p>
          </div>
        </div>
        <div className="spa-skills-stats">
          <div><Users size={18} aria-hidden="true" /><strong>{currentEligible}</strong><span>KTV đủ điều kiện hiện tại</span></div>
          <div><Award size={18} aria-hidden="true" /><strong>{projectedEligible}</strong><span>KTV dự kiến sau khi lưu</span></div>
        </div>
        <p className="spa-catalog-help">KTV đang hoạt động và được cấp quyền có thể nhận dịch vụ. Cờ “Đã chứng nhận” dùng để ghi nhận đào tạo; KTV tạm dừng không được phân ca.</p>
        <Input label="Tìm kỹ thuật viên" placeholder="Nhập tên hoặc chuyên môn…" icon={Search} value={keyword} onChange={(event) => setKeyword(event.target.value)} disabled={busy} />
        {notice && <div className={`spa-catalog-notice ${failures.length || syncNeeded ? 'is-warning' : 'is-success'}`} role={failures.length || syncNeeded ? 'alert' : 'status'}>{notice}</div>}
        {failures.length > 0 && (
          <ul className="spa-skills-errors">
            {failures.map((failure) => <li key={failure.id}><strong>{failure.name}:</strong> {failure.message}</li>)}
          </ul>
        )}
        <div className="spa-skills-table-wrap">
          <table className="spa-skills-table">
            <caption className="sr-only">Quyền thực hiện {service.name} và chứng nhận của kỹ thuật viên</caption>
            <thead><tr><th scope="col">Kỹ thuật viên</th><th scope="col">Quyền làm dịch vụ</th><th scope="col">Đã chứng nhận</th></tr></thead>
            <tbody>
              {visibleRows.map((row) => {
                const permission = draft[row.id] || { granted: false, certified: false };
                const changed = !samePermission(baseline[row.id], permission);
                return (
                  <tr key={row.id} className={changed ? 'is-changed' : ''}>
                    <th scope="row">
                      <div className="spa-skills-person">
                        <span className="spa-skills-avatar" aria-hidden="true">{nameFor(row).split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase()}</span>
                        <div><strong>{nameFor(row)}</strong><span>{row.specialty || 'Kỹ thuật viên spa'}</span><small className={row.active ? 'is-active' : 'is-paused'}>{row.active ? 'Đang hoạt động' : 'Tạm dừng'}{changed ? ' · Chưa lưu' : ''}</small></div>
                      </div>
                    </th>
                    <td>
                      <label className="spa-skills-check">
                        <input type="checkbox" checked={permission.granted} disabled={busy || syncNeeded} onChange={(event) => setPermission(row.id, 'granted', event.target.checked)} aria-label={`Cấp quyền làm dịch vụ cho ${nameFor(row)}`} />
                        <span>{permission.granted ? 'Được cấp quyền' : 'Chưa cấp quyền'}</span>
                      </label>
                    </td>
                    <td>
                      <label className="spa-skills-check">
                        <input type="checkbox" checked={permission.granted && permission.certified} disabled={busy || syncNeeded || !permission.granted} onChange={(event) => setPermission(row.id, 'certified', event.target.checked)} aria-label={`Đã chứng nhận cho ${nameFor(row)}`} />
                        <span>{permission.granted && permission.certified ? 'Đã chứng nhận' : 'Chưa ghi nhận'}</span>
                      </label>
                    </td>
                  </tr>
                );
              })}
              {!visibleRows.length && <tr><td colSpan={3} className="spa-skills-empty">{rows.length ? 'Không tìm thấy kỹ thuật viên phù hợp.' : 'Chưa có hồ sơ kỹ thuật viên. Hãy tạo hồ sơ tại mục Nhân sự spa trước khi cấp quyền.'}</td></tr>}
            </tbody>
          </table>
        </div>
        {changes.length > 0 && (
          <div className="spa-skills-review">
            <strong>{changes.length} thay đổi chờ lưu</strong>
            <ul>
              {changes.map((row) => (
                <li key={row.id}>
                  {nameFor(row)}: {!draft[row.id]?.granted ? 'Thu hồi quyền làm dịch vụ' : !baseline[row.id]?.granted ? `Cấp quyền làm dịch vụ${draft[row.id]?.certified ? ' · Đã chứng nhận' : ''}` : draft[row.id]?.certified ? 'Ghi nhận đã chứng nhận' : 'Bỏ ghi nhận chứng nhận'}.
                </li>
              ))}
            </ul>
            {removals.length > 0 && <p>Khi lưu, quyền làm dịch vụ của các KTV đã bỏ chọn sẽ được thu hồi. Nếu còn lịch hẹn đã phân công, cần chuyển hoặc hủy lịch trước khi thu hồi.</p>}
          </div>
        )}
      </div>
    </Modal>
  );
}
