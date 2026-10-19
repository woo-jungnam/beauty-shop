import React, { useMemo, useRef, useState } from 'react';
import { Clock, Plus, Ticket, Trash2 } from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { formatCurrency } from '../../shared/utils/formatters';
import { spaErrorMessage } from '../../shared/utils/spaMessages';

const positiveInteger = value => /^\d+$/.test(String(value)) && Number.isSafeInteger(Number(value)) && Number(value) > 0;

export const PackageBuilder = ({ packageItem, services, onClose, onSaved }) => {
  const editing = Boolean(packageItem?.id);
  const catalog = useMemo(() => Array.isArray(services) ? services : [], [services]);
  const [form, setForm] = useState(() => ({
    name: packageItem?.name || '',
    description: packageItem?.description || '',
    price: packageItem?.price == null ? '' : String(packageItem.price),
    validityDays: editing ? String(packageItem.validityDays ?? '') : '90',
    unlimited: editing && packageItem.validityDays == null,
    thumbnailUrl: packageItem?.thumbnailUrl || '',
    active: packageItem?.isActive !== false,
  }));
  const [rows, setRows] = useState(() => packageItem?.items?.length
    ? packageItem.items.map((item, index) => ({ key: index, serviceId: String(item.serviceId), quantity: String(item.quantity), serviceName: item.serviceName }))
    : [{ key: 0, serviceId: '', quantity: '1', serviceName: '' }]);
  const nextRowKey = useRef(rows.length);
  const submitting = useRef(false);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const serviceMap = useMemo(() => new Map(catalog.map(service => [String(service.id), service])), [catalog]);
  const activeServices = catalog.filter(service => service.isActive !== false && service.serviceType !== 'TREATMENT_COURSE');
  const selectedIds = new Set(rows.map(row => row.serviceId).filter(Boolean));
  const totals = rows.reduce((result, row) => {
    const service = serviceMap.get(row.serviceId);
    const valid = service && service.isActive !== false && service.serviceType !== 'TREATMENT_COURSE' && positiveInteger(row.quantity) && Number.isFinite(Number(service.basePrice)) && Number(service.basePrice) >= 0;
    return {
      original: result.original + (valid ? Number(service.basePrice) * Number(row.quantity) : 0),
      sessions: result.sessions + (valid ? Number(row.quantity) : 0),
      complete: result.complete && Boolean(valid),
    };
  }, { original: 0, sessions: 0, complete: rows.length > 0 });
  const enteredPrice = Number(form.price);
  const validPrice = positiveInteger(form.price) && enteredPrice <= 99999999;
  const saving = validPrice && totals.complete ? totals.original - enteredPrice : 0;
  const discount = totals.original > 0 && saving > 0 ? saving / totals.original * 100 : 0;
  const discountLabel = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(discount);
  const update = (key, value) => {
    setForm(previous => ({ ...previous, [key]: value }));
    setFieldErrors(previous => ({ ...previous, [key]: '' }));
    setError('');
  };
  const updateRow = (key, field, value) => {
    setRows(previous => previous.map(row => row.key === key ? { ...row, [field]: value } : row));
    setFieldErrors(previous => ({ ...previous, [`row-${key}`]: '' }));
    setError('');
  };
  const addRow = () => {
    if (busy || uncertain) return;
    setRows(previous => [...previous, { key: nextRowKey.current++, serviceId: '', quantity: '1', serviceName: '' }]);
    setError('');
  };
  const removeRow = key => {
    if (busy || uncertain) return;
    setRows(previous => previous.filter(row => row.key !== key));
    setError('');
  };
  const close = () => { if (!submitting.current) onClose(); };

  const save = async event => {
    event.preventDefault();
    if (submitting.current || uncertain) return;
    const problems = {};
    if (!form.name.trim()) problems.name = 'Vui lòng nhập tên gói liệu trình.';
    else if (form.name.trim().length > 150) problems.name = 'Tên gói liệu trình không được vượt quá 150 ký tự.';
    if (!validPrice) problems.price = 'Giá gói phải là số nguyên từ 1 đến 99.999.999 đồng.';
    if (!form.unlimited && (!positiveInteger(form.validityDays) || Number(form.validityDays) > 2147483647)) {
      problems.validityDays = 'Thời hạn phải là số ngày nguyên dương, tối đa 2.147.483.647 ngày.';
    }
    if (form.thumbnailUrl.trim().length > 500) problems.thumbnailUrl = 'Đường dẫn ảnh không được vượt quá 500 ký tự.';
    const seen = new Set();
    rows.forEach(row => {
      const service = serviceMap.get(row.serviceId);
      if (!service) problems[`row-${row.key}`] = 'Vui lòng chọn dịch vụ có trong danh mục hiện tại.';
      else if (service.serviceType === 'TREATMENT_COURSE') problems[`row-${row.key}`] = 'Dịch vụ điều trị theo lộ trình cần mẫu riêng tại mục Lộ trình điều trị. Vui lòng thay bằng dịch vụ một lần.';
      else if (service.isActive === false) problems[`row-${row.key}`] = 'Dịch vụ đang tạm dừng. Vui lòng chọn dịch vụ đang hoạt động.';
      else if (!Number.isFinite(Number(service.basePrice)) || Number(service.basePrice) < 0) problems[`row-${row.key}`] = 'Giá dịch vụ chưa hợp lệ. Vui lòng tải lại danh mục dịch vụ.';
      else if (seen.has(row.serviceId)) problems[`row-${row.key}`] = 'Mỗi dịch vụ chỉ xuất hiện một lần. Hãy tăng số buổi của dòng đã chọn.';
      else if (!positiveInteger(row.quantity) || Number(row.quantity) > 2147483647) problems[`row-${row.key}`] = 'Số buổi phải là số nguyên dương, tối đa 2.147.483.647 buổi.';
      seen.add(row.serviceId);
    });
    setFieldErrors(problems);
    if (!catalog.length || !rows.length || Object.keys(problems).length || !Number.isFinite(totals.original) || totals.original > Number.MAX_SAFE_INTEGER) {
      setError(!catalog.length ? 'Chưa có danh mục dịch vụ để lập gói. Vui lòng tải lại hoặc tạo dịch vụ trước.'
        : !rows.length ? 'Gói liệu trình cần ít nhất một dịch vụ thành phần.'
          : !Number.isFinite(totals.original) || totals.original > Number.MAX_SAFE_INTEGER ? 'Tổng giá gốc quá lớn. Vui lòng kiểm tra lại số buổi và giá dịch vụ.'
            : 'Vui lòng kiểm tra các thông tin được đánh dấu bên dưới.');
      return;
    }
    submitting.current = true;
    setBusy(true);
    setError('');
    let saved = false;
    try {
      const command = {
        name: form.name.trim(),
        description: form.description.trim(),
        price: enteredPrice,
        validityDays: form.unlimited ? null : Number(form.validityDays),
        thumbnailUrl: form.thumbnailUrl.trim(),
        active: form.active,
        items: rows.map(row => ({ serviceId: Number(row.serviceId), quantity: Number(row.quantity) })),
      };
      if (editing) await apiClient.put(ENDPOINTS.SPA.PACKAGE_DETAIL(packageItem.id), command);
      else {
        const response = await apiClient.post(ENDPOINTS.SPA.PACKAGES, command);
        const created = response?.data ?? response;
        if (!Number.isSafeInteger(Number(created?.id)) || Number(created.id) <= 0) {
          throw new Error('Phản hồi tạo gói chưa có mã hợp lệ.');
        }
      }
      saved = true;
    } catch (err) {
      const status = err.status || err.response?.status;
      if (!editing && (!status || status === 408 || status >= 500)) {
        setUncertain(true);
        setError('Chưa xác định được kết quả tạo gói liệu trình. Vui lòng đóng cửa sổ, làm mới danh mục và kiểm tra gói vừa tạo trước khi tạo lại.');
        onSaved('Vui lòng kiểm tra danh mục để xác nhận kết quả tạo gói liệu trình.', 'warning');
      } else setError(spaErrorMessage(err, 'Chưa lưu được gói liệu trình. Vui lòng kiểm tra thông tin và thử lại.'));
    } finally {
      submitting.current = false;
      setBusy(false);
    }
    if (saved) {
      onSaved(editing ? 'Đã cập nhật gói liệu trình.' : 'Đã tạo gói liệu trình mới.');
      onClose();
    }
  };

  return (
    <Modal isOpen onClose={close} title={editing ? 'Chỉnh sửa gói liệu trình' : 'Tạo gói liệu trình'} maxWidth="900px" className="spa-catalog-dialog">
      <form className="spa-package-builder" noValidate onSubmit={save}>
        {error && <div className="spa-package-error" role="alert">{error}</div>}
        <div className="spa-package-layout">
          <div className="spa-package-fields">
            <Input label="Tên gói liệu trình" value={form.name} maxLength={150} onChange={event => update('name', event.target.value)} disabled={busy || uncertain} error={fieldErrors.name} placeholder="Ví dụ: Phục hồi da chuyên sâu 6 buổi" />
            <div className="form-group">
              <label className="form-label" htmlFor="spa-package-description">Mô tả gói liệu trình</label>
              <textarea id="spa-package-description" className="form-input" rows={3} value={form.description} onChange={event => update('description', event.target.value)} disabled={busy || uncertain} placeholder="Mục tiêu chăm sóc và những quyền lợi trong gói" />
            </div>
            <Input label="Đường dẫn ảnh đại diện" value={form.thumbnailUrl} maxLength={500} onChange={event => update('thumbnailUrl', event.target.value)} disabled={busy || uncertain} error={fieldErrors.thumbnailUrl} placeholder="https://... hoặc /uploads/..." />
            <section className="spa-package-items" aria-labelledby="spa-package-items-title">
              <div className="spa-package-section-heading">
                <div><h4 id="spa-package-items-title"><Ticket size={18} aria-hidden="true" /> Dịch vụ thành phần</h4><p>Chọn từng dịch vụ và số buổi khách được sử dụng.</p></div>
                <Button variant="secondary" size="sm" icon={Plus} onClick={addRow} disabled={busy || uncertain || rows.length >= activeServices.length}>Thêm dịch vụ</Button>
              </div>
              {!activeServices.length && <p className="spa-package-empty">Chưa có dịch vụ một lần đang hoạt động để thêm vào gói.</p>}
              {rows.map((row, index) => {
                const service = serviceMap.get(row.serviceId);
                const available = catalog.filter(candidate => String(candidate.id) === row.serviceId || (candidate.isActive !== false && candidate.serviceType !== 'TREATMENT_COURSE' && !selectedIds.has(String(candidate.id))));
                const rowPrice = service && service.serviceType !== 'TREATMENT_COURSE' ? Number(service.basePrice) : 0;
                const lineAmount = positiveInteger(row.quantity) ? rowPrice * Number(row.quantity) : 0;
                return (
                  <div key={row.key} className="spa-package-item">
                    <div className="spa-package-item-fields">
                      <Select label={`Dịch vụ ${index + 1}`} value={row.serviceId} onChange={event => updateRow(row.key, 'serviceId', event.target.value)} disabled={busy || uncertain} error={fieldErrors[`row-${row.key}`]}>
                        <option value="">Chọn dịch vụ</option>
                        {row.serviceId && !service && <option value={row.serviceId} disabled>{row.serviceName || 'Dịch vụ không còn trong danh mục'}</option>}
                        {available.map(candidate => <option key={candidate.id} value={candidate.id} disabled={candidate.isActive === false || candidate.serviceType === 'TREATMENT_COURSE'}>{candidate.name}{candidate.serviceType === 'TREATMENT_COURSE' ? ' — Lộ trình cần mẫu riêng, vui lòng thay dịch vụ' : candidate.isActive === false ? ' — Tạm dừng' : ''}</option>)}
                      </Select>
                      <Input label={`Số buổi dịch vụ ${index + 1}`} type="number" min={1} max={2147483647} step={1} inputMode="numeric" value={row.quantity} onChange={event => updateRow(row.key, 'quantity', event.target.value)} disabled={busy || uncertain} />
                      <Button variant="ghost" icon={Trash2} onClick={() => removeRow(row.key)} disabled={busy || uncertain || rows.length <= 1} aria-label={`Xóa dịch vụ ${index + 1} khỏi gói`} className="spa-package-remove">Xóa</Button>
                    </div>
                    <div className="spa-package-item-cost"><span>{service?.serviceType === 'TREATMENT_COURSE' ? 'Lộ trình không dùng làm dịch vụ thành phần của gói lượt.' : service ? `${formatCurrency(rowPrice)} / buổi` : 'Chọn dịch vụ để xem giá'}</span><strong>{formatCurrency(lineAmount)}</strong></div>
                  </div>
                );
              })}
            </section>
          </div>
          <aside className="spa-package-summary" aria-label="Giá và hiệu lực gói liệu trình">
            <h4><Ticket size={18} aria-hidden="true" /> Giá gói liệu trình</h4>
            <div className="spa-package-summary-line"><span>Tổng số buổi</span><strong>{totals.sessions} buổi</strong></div>
            <div className="spa-package-summary-line"><span>Tổng giá gốc</span><strong>{formatCurrency(totals.original)}</strong></div>
            {!totals.complete && <p className="spa-package-summary-hint">Tổng giá gốc sẽ đầy đủ khi các dịch vụ và số buổi hợp lệ.</p>}
            <Input label="Giá bán gói (VND)" type="number" min={1} max={99999999} step={1} inputMode="numeric" value={form.price} onChange={event => update('price', event.target.value)} disabled={busy || uncertain} error={fieldErrors.price} placeholder="Ví dụ: 1.800.000" />
            <div className={`spa-package-saving ${saving < 0 ? 'is-above-base' : ''}`} aria-live="polite">
              {totals.complete && validPrice ? saving > 0 ? <><strong>Ưu đãi {discountLabel}%</strong><span>Khách tiết kiệm {formatCurrency(saving)}</span></>
                : saving < 0 ? <><strong>Giá gói cao hơn giá gốc</strong><span>Chênh lệch {formatCurrency(-saving)}. Hãy kiểm tra quyền lợi bổ sung của gói.</span></>
                  : <><strong>Giá gói bằng giá gốc</strong><span>Chưa áp dụng giảm giá.</span></>
                : <><strong>So sánh giá tự động</strong><span>Chọn dịch vụ và nhập giá bán để xem mức ưu đãi.</span></>}
            </div>
            <h4><Clock size={18} aria-hidden="true" /> Thời hạn sử dụng</h4>
            <label className="spa-package-toggle"><input type="checkbox" checked={form.unlimited} onChange={event => update('unlimited', event.target.checked)} disabled={busy || uncertain} /><span>Không giới hạn thời hạn</span></label>
            <Input label="Hiệu lực (ngày)" type="number" min={1} max={2147483647} step={1} inputMode="numeric" value={form.validityDays} onChange={event => update('validityDays', event.target.value)} disabled={busy || uncertain || form.unlimited} error={!form.unlimited ? fieldErrors.validityDays : undefined} helperText={form.unlimited ? 'Vé liệu trình được cấp không có ngày hết hạn.' : 'Tính từ ngày thanh toán và cấp vé liệu trình.'} />
            <Select label="Trạng thái kinh doanh" value={form.active ? 'active' : 'paused'} onChange={event => update('active', event.target.value === 'active')} disabled={busy || uncertain} options={[{ value: 'active', label: 'Đang hoạt động' }, { value: 'paused', label: 'Tạm dừng' }]} />
            <p className="spa-package-summary-hint">Thay đổi cấu hình gói áp dụng cho lượt mua mới. Quyền lợi của vé đã cấp được giữ theo thông tin lúc mua.</p>
          </aside>
        </div>
        <div className="spa-package-actions"><Button variant="secondary" onClick={close} disabled={busy}>{uncertain ? 'Đóng & kiểm tra danh mục' : 'Hủy'}</Button>{!uncertain && <Button type="submit" icon={Ticket} loading={busy} disabled={!catalog.length || !activeServices.length}>{editing ? 'Lưu thay đổi gói' : 'Tạo gói liệu trình'}</Button>}</div>
      </form>
    </Modal>
  );
};
