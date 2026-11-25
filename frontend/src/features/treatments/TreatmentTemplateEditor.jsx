import React, { useEffect, useRef, useState } from 'react';
import { Plus, Trash2, Upload } from 'lucide-react';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { apiClient, uploadFile } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import { errorMessage, rows, templateRoot, treatmentApi } from './treatmentApi';
import { planPayload } from './treatmentModel';

const blankSession = () => ({ title: '', minGapDays: 0, maxGapDays: '', required: true, items: [{ serviceId: '', required: true }] });
const formOf = source => ({ name: source?.name || '', description: source?.description || '', serviceId: String(source?.serviceId || ''), thumbnailUrl: source?.thumbnailUrl || '', price: source?.price ?? '', validityDays: source ? source.validityDays ?? '' : 90, terms: source?.terms || '', sessions: source?.sessions?.length ? source.sessions.map(session => ({ ...session, maxGapDays: session.maxGapDays ?? '', items: (session.items || []).map(item => ({ ...item, serviceId: String(item.serviceId) })) })) : [blankSession()] });

export const TreatmentTemplateEditor = ({ template, revision = false, onClose, onSaved, onSubmitPlan }) => {
  const [form, setForm] = useState(() => formOf(template));
  const [reason, setReason] = useState('');
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  const fileInputRef = useRef(null);
  const loadServices = async signal => {
    setLoading(true); setError('');
    try { const data = rows(await apiClient.get('/api/v1/spa/services', { signal })); if (!signal?.aborted) setServices(data); }
    catch (err) { if (!signal?.aborted) setError(errorMessage(err)); }
    finally { if (!signal?.aborted) setLoading(false); }
  };
  useEffect(() => { const controller = new AbortController(); loadServices(controller.signal); return () => controller.abort(); }, []);
  const update = (field, value) => setForm(previous => ({ ...previous, [field]: value }));
  const updateSession = (index, field, value) => setForm(previous => ({ ...previous, sessions: previous.sessions.map((session, i) => index === i ? { ...session, [field]: value } : session) }));
  const handleImageUpload = async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { setError('Vui lòng chọn tệp hình ảnh hợp lệ.'); return; }
    if (file.size > 10 * 1024 * 1024) { setError('Kích thước ảnh tối đa 10 MiB.'); return; }
    setUploadingImage(true); setError('');
    try {
      const res = await uploadFile(ENDPOINTS.CATALOG.MEDIA_UPLOAD, file);
      const uploadedUrl = res?.url || res?.publicUrl || (typeof res === 'string' ? res : '');
      if (!uploadedUrl) throw new Error('Máy chủ không trả về đường dẫn ảnh hợp lệ.');
      update('thumbnailUrl', uploadedUrl);
    } catch (err) {
      setError(`Lỗi tải ảnh lên: ${errorMessage(err)}`);
    } finally {
      setUploadingImage(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };
  const courseServices = services.filter(service => service.serviceType === 'TREATMENT_COURSE' || String(service.id) === form.serviceId);
  const sessionServices = services.filter(service => service.serviceType !== 'TREATMENT_COURSE');
  const submit = async event => {
    event.preventDefault(); if (lock.current || uncertain) return;
    const plan = planPayload(form);
    if (!plan.sessions.length || plan.sessions.some(session => !session.items.length || session.minGapDays < 0 || (session.maxGapDays != null && session.maxGapDays < session.minGapDays) || new Set(session.items.map(item => item.serviceId)).size !== session.items.length)) { setError('Mỗi buổi cần ít nhất một dịch vụ, không trùng dịch vụ trong cùng buổi và khoảng giãn tối đa tham khảo phải lớn hơn hoặc bằng tối thiểu.'); return; }
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
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px' }}>
            <label style={{ flex: 1 }}>Ảnh đại diện gói lộ trình (tùy chọn — mặc định lấy theo dịch vụ cha)<input maxLength={500} placeholder="https://... hoặc /uploads/anh-goi.jpg" value={form.thumbnailUrl} onChange={event => update('thumbnailUrl', event.target.value)} disabled={uploadingImage} /></label>
            <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/avif,image/bmp" style={{ display: 'none' }} onChange={handleImageUpload} disabled={uploadingImage} />
            <Button type="button" variant="outline" loading={uploadingImage} onClick={() => fileInputRef.current?.click()} style={{ height: '42px', whiteSpace: 'nowrap' }}><Upload size={15} />Tải ảnh lên</Button>
          </div>
          {form.thumbnailUrl && <div style={{ width: '140px', height: '86px', borderRadius: '8px', overflow: 'hidden', border: '1px solid #e5e5e9' }}><img src={resolveMediaUrl(form.thumbnailUrl)} alt="Ảnh đại diện lộ trình" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /></div>}
        </div>
        <div className="treatment-form-columns"><label>Giá toàn bộ lộ trình (VND)<input type="number" required min={0} step={1} value={form.price} onChange={event => update('price', event.target.value)} /></label><label>Hiệu lực từ khi thanh toán (ngày, để trống nếu không giới hạn)<input type="number" min={1} max={3650} value={form.validityDays} onChange={event => update('validityDays', event.target.value)} /></label></div>
        <label>Điều khoản đặt / đổi / hủy / vắng hẹn / hoàn tiền<textarea required rows={4} maxLength={10000} value={form.terms} onChange={event => update('terms', event.target.value)} /></label>
        <div className="treatment-card-heading"><h3>Kế hoạch từng buổi</h3><Button variant="outline" onClick={() => update('sessions', [...form.sessions, blankSession()])}><Plus size={15} />Thêm buổi</Button></div>
        {form.sessions.map((session, index) => <section className="treatment-form-session" key={index} aria-label={`Cấu hình buổi ${index + 1}`}>
          <div className="treatment-card-heading"><strong>Buổi {index + 1}</strong><Button variant="ghost" disabled={form.sessions.length === 1} aria-label={`Xóa buổi ${index + 1}`} onClick={() => update('sessions', form.sessions.filter((_, i) => i !== index))}><Trash2 size={16} /></Button></div>
          <label>Nội dung buổi {index + 1}<input required maxLength={200} value={session.title} onChange={event => updateSession(index, 'title', event.target.value)} /></label>
          <div className="treatment-form-columns"><label>Giãn cách tham khảo tối thiểu (ngày)<input type="number" min={0} required value={session.minGapDays} onChange={event => updateSession(index, 'minGapDays', event.target.value)} /></label><label>Giãn cách tham khảo tối đa (ngày, để trống nếu không giới hạn)<input type="number" min={session.minGapDays} value={session.maxGapDays} onChange={event => updateSession(index, 'maxGapDays', event.target.value)} /></label></div>
          <label className="treatment-check"><input type="checkbox" checked={session.required} onChange={event => updateSession(index, 'required', event.target.checked)} />Buổi bắt buộc để kết thúc lộ trình</label>
          {session.items.map((item, position) => <div className="treatment-form-item" key={position}><label>Dịch vụ {position + 1}<select required value={item.serviceId} onChange={event => updateSession(index, 'items', session.items.map((row, i) => i === position ? { ...row, serviceId: event.target.value } : row))}><option value="">Chọn dịch vụ một lần</option>{sessionServices.map(service => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label><label className="treatment-check"><input type="checkbox" checked={item.required} onChange={event => updateSession(index, 'items', session.items.map((row, i) => i === position ? { ...row, required: event.target.checked } : row))} />Bắt buộc</label><Button variant="ghost" aria-label={`Xóa dịch vụ ${position + 1} buổi ${index + 1}`} disabled={session.items.length === 1} onClick={() => updateSession(index, 'items', session.items.filter((_, i) => i !== position))}><Trash2 size={15} /></Button></div>)}
          <Button variant="outline" onClick={() => updateSession(index, 'items', [...session.items, { serviceId: '', required: true }])}><Plus size={15} />Thêm dịch vụ vào buổi</Button>
        </section>)}
        <p className="treatment-muted">Mức giãn thời gian giữa các buổi chỉ mang tính tham khảo / gợi ý cho khách hàng và kỹ thuật viên, không khóa cứng việc đặt lịch. Bản đã xuất bản giữ nguyên cho các lần mua trước.</p>
      </fieldset>
      <div className="treatment-actions"><Button variant="outline" disabled={busy} onClick={onClose}>Đóng</Button><Button type="submit" loading={busy} disabled={loading || uncertain || !services.length}>{revision ? 'Gửi đề xuất điều chỉnh' : 'Lưu bản nháp'}</Button></div>
    </form>
  </Modal>;
};
