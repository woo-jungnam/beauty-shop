import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { apiClient } from '../../shared/api/client';
import { dateLabel, errorMessage, rows, statusLabel, treatmentApi, unwrap } from './treatmentApi';
import { AppointmentMaterials } from './AppointmentMaterials';
import { AppointmentFinance } from './AppointmentFinance';
import { useAuth } from '../../app/providers/AuthProvider';

export const TreatmentAppointmentDialog = ({ appointmentId, role, onClose, onSaved }) => {
  const { user, isAdmin, isSpaReception, isSpaTherapist } = useAuth();
  const [appointment, setAppointment] = useState(null);
  const [preparation, setPreparation] = useState([]);
  const [care, setCare] = useState(null);
  const [instructions, setInstructions] = useState([]);
  const [aftercare, setAftercare] = useState({});
  const [staffOptions, setStaffOptions] = useState({});
  const [assignments, setAssignments] = useState({});
  const [answers, setAnswers] = useState({});
  const [acknowledgments, setAcknowledgments] = useState({});
  const [overrideReasons, setOverrideReasons] = useState({});
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [loading, setLoading] = useState(true);
  const [ownStaffIds, setOwnStaffIds] = useState([]);
  const lock = useRef(false);
  const operator = role !== 'customer';
  const admin = operator && isAdmin;
  const reception = operator && isSpaReception;
  const execution = operator && isSpaTherapist;
  const canPerformItem = item => admin || (execution && ownStaffIds.includes(String(item.staffId)));
  const canUpdateVisit = reception || appointment?.items?.some(canPerformItem);
  const base = `/api/v1/appointments/${appointmentId}`;
  const prepBase = `/api/v1/spa/appointments/${appointmentId}`;
  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const results = await Promise.allSettled([apiClient.get(base), execution ? apiClient.get('/api/v1/spa/services/staff') : Promise.resolve([])]);
      if (results[0].status !== 'fulfilled') throw results[0].reason;
      const detail = unwrap(results[0].value); setAppointment(detail);
      const staffIds = results[1].status === 'fulfilled' ? rows(results[1].value).filter(person => String(person.userId) === String(user?.id)).map(person => String(person.id)) : [];
      setOwnStaffIds(staffIds); setCare(null); setPreparation([]);
      if (execution && results[1].status === 'rejected') setError(errorMessage(results[1].reason));
      const assigned = execution && detail.items?.some(item => staffIds.includes(String(item.staffId)));
      const treatment = detail.items?.some(item => item.treatmentSessionItemId != null);
      const mayReadPreparation = !operator || admin || assigned || (reception && !treatment);
      const mayReadCare = admin || assigned || (operator && !treatment && user?.permissions?.includes('CRM'));
      const preparationResults = await Promise.allSettled([mayReadPreparation ? apiClient.get(`${prepBase}/preparation`) : Promise.resolve([]), mayReadCare ? apiClient.get(`${prepBase}/care-summary`) : Promise.resolve(null)]);
      setInstructions([]);
      if (mayReadPreparation) { try { setInstructions(rows(await apiClient.get(`${prepBase}/follow-up-instructions`))); } catch (err) { setError(errorMessage(err)); } }
      setAssignments(Object.fromEntries((detail.items || []).map(item => [item.id, String(item.staffId || '')])));
      if (preparationResults[0].status === 'fulfilled') {
        const prep = rows(preparationResults[0].value); setPreparation(prep);
        setAnswers(Object.fromEntries(prep.flatMap(item => (item.forms || []).map(form => [form.id, form.latestResponse?.answers || {}]))));
        setAcknowledgments(Object.fromEntries(prep.flatMap(item => (item.forms || []).map(form => [form.id, Boolean(form.latestResponse?.acknowledged)]))));
      } else setError(`Chưa tải được yêu cầu chuẩn bị. ${errorMessage(preparationResults[0].reason)}`);
      if (mayReadCare && preparationResults[1].status === 'fulfilled') setCare(unwrap(preparationResults[1].value));
      if (reception && ['PENDING', 'CONFIRMED'].includes(detail.status)) {
        const staffResults = await Promise.allSettled((detail.items || []).map(item => apiClient.get(`/api/v1/spa/services/${item.serviceId}/staff`)));
        const options = {}; staffResults.forEach((result, index) => { if (result.status === 'fulfilled') options[detail.items[index].id] = rows(result.value); }); setStaffOptions(options);
        if (staffResults.some(result => result.status === 'rejected')) setError('Chưa tải đủ danh sách nhân viên. Bấm tải lại trước khi phân công.');
      }
    } catch (err) { setError(errorMessage(err)); } finally { setLoading(false); }
  }, [base, prepBase, operator, execution, reception, admin, user?.id, user?.permissions]);
  useEffect(() => { load(); }, [load]);
  const run = async (key, request, success) => {
    if (lock.current) return; lock.current = true; setBusy(key); setError('');
    try { await request(); await load(); await onSaved(success || 'Đã cập nhật lịch hẹn và tiến độ lộ trình.'); }
    catch (err) { setError(errorMessage(err)); await onSaved('Hồ sơ đã được tải lại để kiểm tra trạng thái mới nhất.', 'warning'); }
    finally { lock.current = false; setBusy(''); }
  };
  const updateStatus = (status, extra = {}) => apiClient.put(`/api/v1/appointments/admin/${appointmentId}/status`, { status, ...(reception ? { notes: notes.trim() || undefined } : {}), ...extra });
  return <Modal isOpen title={`Lịch hẹn #${appointmentId} · Chuẩn bị & thực hiện`} onClose={() => { if (!busy) onClose(); }} maxWidth="900px" className="treatment-dialog"><div className="treatment-form">
    {error && <div role="alert" className="treatment-notice error">{error}</div>}
    <div className="treatment-actions"><Button variant="outline" onClick={load} disabled={Boolean(busy) || loading}>Tải lại lịch hẹn</Button></div>
    {loading && !appointment && <p role="status">Đang tải lịch hẹn…</p>}
    {appointment && <><div className="treatment-card-heading"><strong>{dateLabel(appointment.appointmentDate)} · {appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</strong><span className="treatment-badge">{statusLabel(appointment.status)}</span></div>
      {(reception || String(appointment.userId) === String(user?.id)) && <AppointmentFinance appointmentId={appointment.id} role={role} />}
      {preparation.map(item => {
        const canPrepare = ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(appointment.status) && appointment.items?.find(row => row.id === item.appointmentItemId)?.executionStatus === 'PLANNED';
        return <section className="treatment-form-session" key={item.appointmentItemId}><h3>Chuẩn bị dịch vụ #{item.appointmentItemId}</h3>{item.legacyBooking && <p className="treatment-muted">Lịch hẹn sử dụng chính sách cũ.</p>}
        {(item.forms || []).map(requirement => <form className="treatment-form" key={requirement.id} onSubmit={event => { event.preventDefault(); run(`form-${requirement.id}`, () => treatmentApi.post(`${prepBase}/preparation/forms/${requirement.id}/responses`, { acknowledged: Boolean(acknowledgments[requirement.id]), answers: answers[requirement.id] || {} }), 'Đã lưu thông tin và xác nhận chuẩn bị.'); }}>
          <strong>{requirement.form?.title || 'Phiếu thông tin và đồng ý thực hiện'}</strong>
          {(requirement.form?.questions || []).map(question => <label key={question.key}>{question.label}{question.type === 'BOOLEAN' ? <select disabled={operator || !canPrepare || Boolean(busy)} required={question.required} value={answers[requirement.id]?.[question.key] === true ? 'true' : answers[requirement.id]?.[question.key] === false ? 'false' : ''} onChange={event => setAnswers(previous => ({ ...previous, [requirement.id]: { ...previous[requirement.id], [question.key]: event.target.value === '' ? null : event.target.value === 'true' } }))}><option value="">Chọn câu trả lời</option><option value="true">Có</option><option value="false">Không</option></select> : <input disabled={operator || !canPrepare || Boolean(busy)} required={question.required} value={answers[requirement.id]?.[question.key] || ''} onChange={event => setAnswers(previous => ({ ...previous, [requirement.id]: { ...previous[requirement.id], [question.key]: event.target.value } }))} />}</label>)}
          <label className="treatment-check"><input type="checkbox" disabled={operator || !canPrepare || Boolean(busy)} checked={Boolean(acknowledgments[requirement.id])} onChange={event => setAcknowledgments(previous => ({ ...previous, [requirement.id]: event.target.checked }))} />Khách xác nhận thông tin và đồng ý nội dung phiếu</label>
          {!operator && canPrepare && <Button type="submit" disabled={Boolean(busy) || !acknowledgments[requirement.id]} loading={busy === `form-${requirement.id}`}>Gửi phiếu chuẩn bị</Button>}
        </form>)}
        {canPerformItem(appointment.items?.find(row => row.id === item.appointmentItemId) || {}) && item.warningsRequired && <><p className="treatment-notice warning">Cần đọc thông tin khách và xác nhận cảnh báo trước thực hiện.</p>{care && <div className="treatment-result internal"><strong>Thông tin cần lưu ý</strong>{Array.isArray(care.notes) ? care.notes.map((note, index) => typeof note === 'string' ? <p key={index}>{note}</p> : <div key={note.id || index}><p>{note.content}</p>{note.skinProfile && <p>Hồ sơ da: {note.skinProfile}</p>}{note.allergies && <p><strong>Dị ứng:</strong> {note.allergies}</p>}{note.contraindications && <p><strong>Chống chỉ định:</strong> {note.contraindications}</p>}</div>) : <p>Không có ghi chú bổ sung.</p>}</div>}<Button variant="outline" disabled={Boolean(busy) || !care?.versionHash || !canPrepare} loading={busy === `warn-${item.appointmentItemId}`} onClick={() => run(`warn-${item.appointmentItemId}`, () => treatmentApi.post(`${prepBase}/preparation/items/${item.appointmentItemId}/acknowledge-warnings`, { versionHash: care.versionHash }), 'Đã xác nhận đã đọc cảnh báo phiên bản hiện tại.')}>Đã đọc và kiểm tra cảnh báo</Button>{(item.warningAcknowledgments || []).length > 0 && <small>Đã có {item.warningAcknowledgments.length} xác nhận đọc cảnh báo. Người bắt đầu dịch vụ cần xác nhận phiên bản hiện tại của chính mình.</small>}</>}
        {admin && canPrepare && !item.legacyBooking && <details><summary>Duyệt ngoại lệ yêu cầu chuẩn bị</summary><p className="treatment-muted">Lưu quyết định ngoại lệ riêng của quản trị, kèm lý do. Quyết định này không tạo phiếu đồng ý thay khách.</p><label>Lý do duyệt ngoại lệ<input maxLength={1000} value={overrideReasons[item.appointmentItemId] || ''} onChange={event => setOverrideReasons(previous => ({ ...previous, [item.appointmentItemId]: event.target.value }))} /></label><Button variant="outline" disabled={Boolean(busy) || !overrideReasons[item.appointmentItemId]?.trim()} onClick={() => run(`override-${item.appointmentItemId}`, () => treatmentApi.post(`${prepBase}/preparation/items/${item.appointmentItemId}/override`, { reason: overrideReasons[item.appointmentItemId].trim() }), 'Đã lưu quyết định ngoại lệ của quản trị.')}>Lưu quyết định ngoại lệ</Button></details>}
        {(item.overrides || []).map((override, index) => <p className="treatment-notice warning" key={index}>Ngoại lệ đã duyệt: {override.reason}</p>)}
      </section>; })}
      {operator && <><label>Ghi chú / lý do thao tác<textarea maxLength={2000} rows={3} value={notes} onChange={event => setNotes(event.target.value)} disabled={Boolean(busy)} placeholder="Bắt buộc khi bỏ dịch vụ hoặc ghi nhận vắng hẹn" /></label>
        {(reception || appointment.items?.some(canPerformItem)) && <AppointmentMaterials appointment={appointment} canPerformItem={canPerformItem} />}
        <div className="treatment-actions">{reception && ['PENDING', 'CONFIRMED'].includes(appointment.status) && <Button disabled={Boolean(busy) || !appointment.items?.every(item => assignments[item.id] && staffOptions[item.id])} loading={busy === 'assign'} onClick={() => run('assign', () => updateStatus('CONFIRMED', { staffAssignments: Object.fromEntries(Object.entries(assignments).map(([key, value]) => [key, Number(value)])) }), 'Đã xác nhận lịch và phân công nhân viên.')}>Xác nhận & phân công</Button>}
          {reception && appointment.status === 'CONFIRMED' && !appointment.checkedInAt && <Button variant="outline" disabled={Boolean(busy)} loading={busy === 'checkin'} onClick={() => run('checkin', () => apiClient.put(`${base}/check-in`), 'Đã check-in khách.')}>Check-in</Button>}
          {canUpdateVisit && appointment.status === 'CONFIRMED' && appointment.checkedInAt && <Button disabled={Boolean(busy)} loading={busy === 'start'} onClick={() => run('start', () => updateStatus('IN_PROGRESS'), 'Đã bắt đầu lịch hẹn.')}>Bắt đầu buổi hẹn</Button>}
          {reception && appointment.status === 'CONFIRMED' && !appointment.checkedInAt && <Button variant="outline" disabled={Boolean(busy) || !notes.trim()} loading={busy === 'noshow'} onClick={() => run('noshow', () => updateStatus('NO_SHOW'), 'Đã ghi nhận vắng hẹn theo chính sách; không tính là đã điều trị.')}>Ghi nhận vắng hẹn</Button>}
        </div>
        {(appointment.items || []).map(item => <section className="treatment-form-session" key={item.id}><div className="treatment-card-heading"><strong>{item.serviceName}</strong><span className="treatment-badge">{statusLabel(item.executionStatus)}</span></div><small>{item.staffName} · {item.startTime?.slice(0, 5)}–{item.endTime?.slice(0, 5)}</small>
          {reception && ['PENDING', 'CONFIRMED'].includes(appointment.status) && <label>Nhân viên đủ chuyên môn<select disabled={Boolean(busy)} value={assignments[item.id] || ''} onChange={event => setAssignments(previous => ({ ...previous, [item.id]: event.target.value }))}><option value="">Chọn nhân viên thực hiện</option>{(staffOptions[item.id] || []).map(staff => <option key={staff.id} value={staff.id}>{staff.fullName || staff.name || `Nhân viên #${staff.id}`}</option>)}</select></label>}
          {item.executionNotes && <p>{item.executionNotes}</p>}
          {canPerformItem(item) && item.executionStatus === 'PERFORMED' && <div className="treatment-form"><label>Hướng dẫn chăm sóc sau buổi<textarea maxLength={4000} value={aftercare[item.id] || ''} onChange={e => setAftercare(previous => ({ ...previous, [item.id]: e.target.value }))} /></label><Button variant="outline" disabled={Boolean(busy) || !aftercare[item.id]?.trim()} onClick={() => run(`aftercare-${item.id}`, () => treatmentApi.post(`${prepBase}/follow-up-instructions/items/${item.id}`, { content: aftercare[item.id].trim(), dueAt: new Date().toISOString() }), 'Đã lưu hướng dẫn chăm sóc cho khách.')}>Lưu hướng dẫn sau buổi</Button></div>}
          {canPerformItem(item) && appointment.status === 'IN_PROGRESS' && <div className="treatment-actions">
            {item.executionStatus === 'PLANNED' && <><Button disabled={Boolean(busy)} loading={busy === `start-${item.id}`} onClick={() => run(`start-${item.id}`, () => apiClient.put(`${base}/items/${item.id}/execution`, { status: 'IN_PROGRESS', notes }), 'Đã bắt đầu dịch vụ.')}>Bắt đầu dịch vụ</Button><Button variant="outline" disabled={Boolean(busy) || !notes.trim()} onClick={() => run(`skip-${item.id}`, () => apiClient.put(`${base}/items/${item.id}/execution`, { status: 'SKIPPED', notes }), 'Đã ghi nhận chưa thực hiện dịch vụ; phần chưa làm không tăng tiến độ.')}>Bỏ dịch vụ có lý do</Button></>}
            {item.executionStatus === 'IN_PROGRESS' && <>
              <Button disabled={Boolean(busy)} loading={busy === `done-${item.id}`} onClick={() => run(`done-${item.id}`, () => apiClient.put(`${base}/items/${item.id}/execution`, { status: 'PERFORMED', notes }), 'Đã hoàn thành dịch vụ và đối soát lượt sử dụng.')}>Hoàn thành dịch vụ</Button>
              <Button variant="outline" disabled={Boolean(busy) || !notes.trim()} loading={busy === `incident-stop-${item.id}`} onClick={() => run(`incident-stop-${item.id}`, () => apiClient.put(`${base}/items/${item.id}/execution`, { status: 'SKIPPED', notes: notes.trim() }), 'Đã dừng dịch vụ do sự cố và trả lượt đang giữ. Phần bị gián đoạn không được tính hoàn thành.')}>Dừng do sự cố</Button>
            </>}
          </div>}
        </section>)}
        {canUpdateVisit && appointment.status === 'IN_PROGRESS' && <Button disabled={Boolean(busy) || !appointment.items?.every(item => ['PERFORMED', 'SKIPPED', 'LEGACY_FINALIZED'].includes(item.executionStatus))} loading={busy === 'complete'} onClick={() => run('complete', () => updateStatus('COMPLETED'), 'Đã kết thúc lịch hẹn. Vui lòng ghi kết quả buổi trong hồ sơ lộ trình.')}>Kết thúc lịch hẹn đã xử lý đủ các mục</Button>}
      </>}
      {instructions.length > 0 && <section className="treatment-form-session"><h3>Hướng dẫn sau buổi</h3>{instructions.map(instruction => <p key={instruction.id}>{instruction.content}</p>)}</section>}
    </>}
  </div></Modal>;
};
