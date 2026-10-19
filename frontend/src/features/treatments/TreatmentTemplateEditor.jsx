import React, { useEffect, useRef, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { apiClient } from '../../shared/api/client';
import { errorMessage, rows, templateRoot, treatmentApi } from './treatmentApi';
import { planPayload } from './treatmentModel';

const blankSession = () => ({ title: '', minGapDays: 0, maxGapDays: '', required: true, items: [{ serviceId: '', required: true }] });
const formOf = source => ({ name: source?.name || '', description: source?.description || '', serviceId: String(source?.serviceId || ''), price: source?.price ?? '', validityDays: source?.validityDays ?? 90, terms: source?.terms || '', sessions: source?.sessions?.length ? source.sessions.map(session => ({ ...session, maxGapDays: session.maxGapDays ?? '', items: (session.items || []).map(item => ({ ...item, serviceId: String(item.serviceId) })) })) : [blankSession()] });

export const TreatmentTemplateEditor = ({ template, revision = false, onClose, onSaved, onSubmitPlan }) => {
  const [form, setForm] = useState(() => formOf(template));
  const [reason, setReason] = useState('');
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  const loadServices = async signal => {
    setLoading(true); setError('');
    try { const data = rows(await apiClient.get('/api/v1/spa/services', { signal })); if (!signal?.aborted) setServices(data); }
    catch (err) { if (!signal?.aborted) setError(errorMessage(err)); }
    finally { if (!signal?.aborted) setLoading(false); }
  };
  useEffect(() => { const controller = new AbortController(); loadServices(controller.signal); return () => controller.abort(); }, []);
  const update = (field, value) => setForm(previous => ({ ...previous, [field]: value }));
  const updateSession = (index, field, value) => setForm(previous => ({ ...previous, sessions: previous.sessions.map((session, i) => index === i ? { ...session, [field]: value } : session) }));
  const courseServices = services.filter(service => service.serviceType === 'TREATMENT_COURSE' || String(service.id) === form.serviceId);
  const sessionServices = services.filter(service => service.serviceType !== 'TREATMENT_COURSE');
  const submit = async event => {
    event.preventDefault(); if (lock.current || uncertain) return;
    const plan = planPayload(form);
    if (!plan.sessions.length || plan.sessions.some(session => !session.items.length || session.minGapDays < 0 || (session.maxGapDays != null && session.maxGapDays < session.minGapDays) || new Set(session.items.map(item => item.serviceId)).size !== session.items.length)) { setError('Mỗi buổi cần ít nhất một dịch vụ, không trùng dịch vụ trong cùng buổi và khoảng cách tối đa phải lớn hơn hoặc bằng tối thiểu.'); return; }
    lock.current = true; setBusy(true); setError('');
    try {
      if (revision) await onSubmitPlan({ reason: reason.trim(), plan });
      else if (template?.id) await apiClient.put(`${templateRoot(true)}/${template.id}`, plan);
      else await treatmentApi.post(templateRoot(true), plan);
      onSaved(revision ? 'Đã gửi đề xuất. Kế hoạch hiện hành được giữ đến khi đủ phê duyệt và xác nhận.' : 'Đã lưu bản nháp lộ trình.'); onClose();
    } catch (err) {
      if ((!err.response || err.response.status >= 500) && !template?.id) setUncertain(true);
      setError(`${errorMessage(err)}${!err.response ? ' Kết nối gián đoạn; đóng và tải lại danh sách để kiểm tra kết quả trước khi gửi lại.' : ''}`);
    } finally { lock.current = false; setBusy(false); }
  };
  return <Modal isOpen title={revision ? 'Đề xuất điều chỉnh kế hoạch' : template?.id ? 'Chỉnh sửa bản nháp lộ trình' : 'Tạo mẫu lộ trình điều trị'} onClose={() => { if (!busy) onClose(); }} maxWidth="920px" className="treatment-dialog">
    <form className="treatment-form" onSubmit={submit}>
      {error && <div className="treatment-notice error" role="alert">{error}{!services.length && <Button variant="outline" onClick={() => loadServices()}>Tải lại dịch vụ</Button>}</div>}
      {loading && <p role="status">Đang tải dịch vụ…</p>}
      <fieldset disabled={busy || loading || uncertain}>
        {revision && <label>Lý do điều chỉnh<textarea rows={3} required maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} /></label>}
        <div className="treatment-form-columns"><label>Tên lộ trình<input required maxLength={150} value={form.name} onChange={event => update('name', event.target.value)} /></label><label>Dịch vụ theo lộ trình<select required disabled={Boolean(template?.serviceId)} value={form.serviceId} onChange={event => update('serviceId', event.target.value)}><option value="">Chọn dịch vụ đã phân loại theo lộ trình</option>{courseServices.map(service => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label></div>
        {!courseServices.length && !loading && <p className="treatment-notice warning">Tạo dịch vụ với loại “Điều trị theo lộ trình” tại Danh mục dịch vụ trước khi tạo mẫu.</p>}
        <label>Mô tả<textarea rows={3} maxLength={5000} value={form.description} onChange={event => update('description', event.target.value)} /></label>
        <div className="treatment-form-columns"><label>Giá toàn bộ lộ trình (VND)<input type="number" required min={0} step={1} value={form.price} onChange={event => update('price', event.target.value)} /></label><label>Hiệu lực từ khi thanh toán (ngày)<input type="number" required min={1} max={3650} value={form.validityDays} onChange={event => update('validityDays', event.target.value)} /></label></div>
        <label>Điều khoản đặt / đổi / hủy / vắng hẹn / hoàn tiền<textarea required rows={4} maxLength={10000} value={form.terms} onChange={event => update('terms', event.target.value)} /></label>
        <div className="treatment-card-heading"><h3>Kế hoạch từng buổi</h3><Button variant="outline" onClick={() => update('sessions', [...form.sessions, blankSession()])}><Plus size={15} />Thêm buổi</Button></div>
        {form.sessions.map((session, index) => <section className="treatment-form-session" key={index} aria-label={`Cấu hình buổi ${index + 1}`}>
          <div className="treatment-card-heading"><strong>Buổi {index + 1}</strong><Button variant="ghost" disabled={form.sessions.length === 1} aria-label={`Xóa buổi ${index + 1}`} onClick={() => update('sessions', form.sessions.filter((_, i) => i !== index))}><Trash2 size={16} /></Button></div>
          <label>Nội dung buổi {index + 1}<input required maxLength={200} value={session.title} onChange={event => updateSession(index, 'title', event.target.value)} /></label>
          <div className="treatment-form-columns"><label>Cách buổi trước tối thiểu (ngày)<input type="number" min={0} required value={session.minGapDays} onChange={event => updateSession(index, 'minGapDays', event.target.value)} /></label><label>Cách buổi trước tối đa (ngày, để trống nếu không giới hạn)<input type="number" min={session.minGapDays} value={session.maxGapDays} onChange={event => updateSession(index, 'maxGapDays', event.target.value)} /></label></div>
          <label className="treatment-check"><input type="checkbox" checked={session.required} onChange={event => updateSession(index, 'required', event.target.checked)} />Buổi bắt buộc để kết thúc lộ trình</label>
          {session.items.map((item, position) => <div className="treatment-form-item" key={position}><label>Dịch vụ {position + 1}<select required value={item.serviceId} onChange={event => updateSession(index, 'items', session.items.map((row, i) => i === position ? { ...row, serviceId: event.target.value } : row))}><option value="">Chọn dịch vụ một lần</option>{sessionServices.map(service => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label><label className="treatment-check"><input type="checkbox" checked={item.required} onChange={event => updateSession(index, 'items', session.items.map((row, i) => i === position ? { ...row, required: event.target.checked } : row))} />Bắt buộc</label><Button variant="ghost" aria-label={`Xóa dịch vụ ${position + 1} buổi ${index + 1}`} disabled={session.items.length === 1} onClick={() => updateSession(index, 'items', session.items.filter((_, i) => i !== position))}><Trash2 size={15} /></Button></div>)}
          <Button variant="outline" onClick={() => updateSession(index, 'items', [...session.items, { serviceId: '', required: true }])}><Plus size={15} />Thêm dịch vụ vào buổi</Button>
        </section>)}
        <p className="treatment-muted">Khoảng cách buổi là cấu hình nghiệp vụ do người phụ trách quyết định. Bản đã xuất bản giữ nguyên cho các lần mua trước.</p>
      </fieldset>
      <div className="treatment-actions"><Button variant="outline" disabled={busy} onClick={onClose}>Đóng</Button><Button type="submit" loading={busy} disabled={loading || uncertain || !services.length}>{revision ? 'Gửi đề xuất điều chỉnh' : 'Lưu bản nháp'}</Button></div>
    </form>
  </Modal>;
};
