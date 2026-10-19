import React, { useEffect, useId, useRef, useState } from 'react';
import { Building, Clock, Plus, RefreshCw, Sparkles, Trash2 } from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { formatCurrency, resolveMediaUrl } from '../../shared/utils/formatters';
import { spaErrorMessage } from '../../shared/utils/spaMessages';
import { RESOURCE_NAMES, resourceName, slugify, unwrap } from './catalogUtils';
import { ServiceMaterialsEditor } from './ServiceMaterialsEditor';

const initialForm = service => ({
  name: service?.name || '', slug: service?.slug || '', categoryId: String(service?.categoryId || ''),
  basePrice: String(service?.basePrice ?? 500000), durationMinutes: String(service?.durationMinutes ?? 60),
  preparationTimeMinutes: String(service?.preparationTimeMinutes ?? 15), thumbnailUrl: service?.thumbnailUrl || '',
  shortDescription: service?.shortDescription || '', description: service?.description || '', active: service?.isActive ?? true,
  serviceType: service?.serviceType || 'SINGLE_SESSION',
  requiresCertification: Boolean(service?.requiresCertification),
});
const payloadOf = form => ({
  name: form.name.trim(), slug: form.slug.trim() || slugify(form.name), categoryId: form.categoryId ? Number(form.categoryId) : null,
  basePrice: Number(form.basePrice), durationMinutes: Number(form.durationMinutes), preparationTimeMinutes: Number(form.preparationTimeMinutes),
  thumbnailUrl: form.thumbnailUrl.trim() || null, shortDescription: form.shortDescription.trim(), description: form.description.trim(), active: form.active, serviceType: form.serviceType, requiresCertification: form.requiresCertification,
});
const normalizeResources = resources => resources.map(row => ({ type: String(row.type).trim().toUpperCase(), units: Number(row.units) })).sort((a, b) => a.type.localeCompare(b.type));

export const ServiceEditor = ({ service, categories, facilities, initialTab = 'info', onClose, onSaved }) => {
  const [form, setForm] = useState(() => initialForm(service));
  const [tab, setTab] = useState(initialTab);
  const [requirements, setRequirements] = useState([]);
  const [resourceLoaded, setResourceLoaded] = useState(!service);
  const [resourceLoading, setResourceLoading] = useState(Boolean(service));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [partial, setPartial] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const serviceId = useRef(service?.id || null);
  const infoCheckpoint = useRef(service ? JSON.stringify(payloadOf(initialForm(service))) : null);
  const resourceCheckpoint = useRef('[]');
  const submitting = useRef(false);
  const stagedCreate = useRef(false);
  const tabId = useId();
  const request = useRef(null);
  const [previewFailed, setPreviewFailed] = useState(false);
  const update = (key, value) => setForm(previous => ({ ...previous, [key]: value }));
  const loadResources = async () => {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setResourceLoading(true); setError('');
    try {
      const rows = unwrap(await apiClient.get(ENDPOINTS.SPA.SERVICE_RESOURCES(serviceId.current), { signal: controller.signal }));
      if (!Array.isArray(rows)) throw new Error('Chưa đọc được định mức tài nguyên hiện tại. Vui lòng tải lại trước khi lưu.');
      if (controller.signal.aborted) return;
      setRequirements(rows); resourceCheckpoint.current = JSON.stringify(normalizeResources(rows)); setResourceLoaded(true);
    } catch (err) { if (!controller.signal.aborted) { setResourceLoaded(false); setError(spaErrorMessage(err, 'Chưa tải được định mức hiện tại. Vui lòng tải lại trước khi lưu để tránh mất cấu hình.')); } }
    finally { if (!controller.signal.aborted) setResourceLoading(false); }
  };
  useEffect(() => {
    if (service) loadResources();
    return () => request.current?.abort();
  }, [service]);
  const types = [...new Set([...Object.keys(RESOURCE_NAMES), ...facilities.map(facility => facility.type), ...requirements.map(row => row.type)].filter(Boolean))];
  const missingResources = requirements.filter(row => !facilities.some(facility => facility.type === String(row.type).trim().toUpperCase() && facility.active !== false));
  const submit = async event => {
    event.preventDefault();
    if (submitting.current || !resourceLoaded || uncertain) return;
    const payload = payloadOf(form);
    if (!payload.name || payload.name.length > 150 || !payload.slug || payload.slug.length > 150 || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(payload.slug)) {
      setTab('info'); setError('Tên dịch vụ và slug phải từ 1 đến 150 ký tự. Slug dùng chữ thường không dấu, số và dấu gạch ngang.'); return;
    }
    if (form.basePrice === '' || !Number.isSafeInteger(payload.basePrice) || payload.basePrice < 0 || payload.basePrice > 99999999) { setTab('info'); setError('Giá niêm yết phải là số nguyên từ 0 đến 99.999.999 VND.'); return; }
    if (!Number.isInteger(payload.durationMinutes) || payload.durationMinutes <= 0 || form.preparationTimeMinutes === '' || !Number.isInteger(payload.preparationTimeMinutes) || payload.preparationTimeMinutes < 0 || payload.durationMinutes + payload.preparationTimeMinutes > 720) {
      setTab('info'); setError('Thời gian thực hiện phải dương, chuẩn bị không được âm; tổng hai khoảng thời gian không vượt quá 720 phút.'); return;
    }
    if (payload.shortDescription.length > 250 || (payload.thumbnailUrl && (payload.thumbnailUrl.length > 500 || !/^(https?:\/\/|\/(?!\/))/.test(payload.thumbnailUrl)))) {
      setTab('info'); setError('Mô tả ngắn tối đa 250 ký tự. Ảnh dùng URL http/https hoặc đường dẫn bắt đầu bằng /, tối đa 500 ký tự.'); return;
    }
    const resources = normalizeResources(requirements);
    if (resources.some(row => !/^[A-Z][A-Z0-9_]{0,39}$/.test(row.type) || !Number.isSafeInteger(row.units) || row.units <= 0 || row.units > 2147483647)
      || new Set(resources.map(row => row.type)).size !== resources.length) {
      setTab('resources'); setError('Mỗi loại tài nguyên chỉ được chọn một lần. Mã loại dùng chữ in hoa, số hoặc gạch dưới; số đơn vị phải là số nguyên dương.'); return;
    }
    submitting.current = true; setBusy(true); setError('');
    let infoSaved = false;
    let creating = false;
    try {
      const staging = stagedCreate.current || (!serviceId.current && resources.length > 0);
      const infoPayload = staging ? { ...payload, active: false } : payload;
      if (JSON.stringify(infoPayload) !== infoCheckpoint.current) {
        creating = !serviceId.current;
        const response = creating ? await apiClient.post(ENDPOINTS.SPA.SERVICES, infoPayload) : await apiClient.put(ENDPOINTS.SPA.SERVICE_DETAIL(serviceId.current), infoPayload);
        const saved = unwrap(response);
        if (!Number.isSafeInteger(Number(saved?.id)) || Number(saved.id) <= 0) {
          if (creating) setUncertain(true);
          throw new Error('Chưa xác minh được mã dịch vụ sau khi lưu. Vui lòng làm mới danh mục và kiểm tra slug.');
        }
        serviceId.current = saved.id;
        stagedCreate.current = staging;
        infoCheckpoint.current = JSON.stringify(infoPayload); infoSaved = true;
      }
      if (JSON.stringify(resources) !== resourceCheckpoint.current) {
        await apiClient.put(ENDPOINTS.SPA.SERVICE_RESOURCES(serviceId.current), resources);
        resourceCheckpoint.current = JSON.stringify(resources);
      }
      if (stagedCreate.current && JSON.stringify(payload) !== infoCheckpoint.current) {
        await apiClient.put(ENDPOINTS.SPA.SERVICE_DETAIL(serviceId.current), payload);
        infoCheckpoint.current = JSON.stringify(payload);
      }
      stagedCreate.current = false;
      onSaved('Đã lưu dịch vụ và định mức tài nguyên.'); onClose();
    } catch (err) {
      if (infoSaved || partial) {
        setPartial(true); setTab('resources');
        const resourcesPending = JSON.stringify(resources) !== resourceCheckpoint.current;
        setError('Thông tin dịch vụ đã được lưu, nhưng cấu hình tài nguyên / kích hoạt chưa hoàn tất. ' + (stagedCreate.current && resourcesPending ? 'Dịch vụ mới vẫn tạm dừng để tránh nhận lịch thiếu tài nguyên. ' : stagedCreate.current ? 'Tài nguyên đã lưu; cần kiểm tra lại trạng thái kích hoạt. ' : '') + spaErrorMessage(err, 'Vui lòng kiểm tra và bấm lưu lại; hệ thống sẽ dùng đúng dịch vụ vừa tạo.'));
        onSaved('Đã lưu thông tin dịch vụ. Cần hoàn tất tài nguyên và trạng thái hoạt động.', 'warning');
      } else if (creating && (!(err.status || err.response?.status) || (err.status || err.response?.status) >= 500)) {
        setUncertain(true); setError('Kết nối gián đoạn khi tạo dịch vụ. Vui lòng đóng cửa sổ, làm mới danh mục và kiểm tra slug trước khi tạo lại.');
        onSaved('Vui lòng kiểm tra danh mục trước khi tạo lại dịch vụ.', 'warning');
      } else setError(spaErrorMessage(err, 'Chưa lưu được dịch vụ. Vui lòng kiểm tra thông tin và thử lại.'));
    } finally { submitting.current = false; setBusy(false); }
  };
  return <Modal isOpen onClose={() => { if (!busy) onClose(); }} title={service || partial ? 'Chỉnh sửa dịch vụ Spa' : 'Thêm dịch vụ Spa'} className="spa-catalog-dialog" maxWidth="880px">
    <form className="spa-service-editor" onSubmit={submit} noValidate>
      <div className="spa-editor-tabs" role="tablist" aria-label="Cấu hình dịch vụ">{[
        ['info', 'Thông tin dịch vụ', Sparkles], ['resources', 'Tài nguyên bắt buộc', Building],
      ].map(([key, label, Icon]) => <button key={key} type="button" role="tab" id={`${tabId}-${key}`} aria-selected={tab === key} aria-controls={`${tabId}-${key}-panel`} tabIndex={tab === key ? 0 : -1}
        onKeyDown={event => { if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) { event.preventDefault(); const next = key === 'info' ? 'resources' : 'info'; setTab(event.key === 'Home' ? 'info' : event.key === 'End' ? 'resources' : next); requestAnimationFrame(() => document.getElementById(`${tabId}-${event.key === 'Home' ? 'info' : event.key === 'End' ? 'resources' : next}`)?.focus()); } }}
        onClick={() => setTab(key)}><Icon size={16} />{label}{key === 'resources' && <span>{requirements.length}</span>}</button>)}</div>
      {error && <div role="alert" className="spa-catalog-notice error">{error}</div>}
      <fieldset disabled={busy || uncertain}>
        <div role="tabpanel" id={`${tabId}-info-panel`} aria-labelledby={`${tabId}-info`} hidden={tab !== 'info'}>
          <div className="spa-service-editor-layout"><div className="spa-editor-fields">
            <Input label="Tên dịch vụ" required maxLength={150} value={form.name} onChange={event => update('name', event.target.value)} placeholder="Ví dụ: Phục hồi da chuyên sâu" />
            <Select label="Loại dịch vụ" value={form.serviceType} onChange={event => update('serviceType', event.target.value)} options={[{ value: 'SINGLE_SESSION', label: 'Một lần — sử dụng trong ngày hẹn' }, { value: 'TREATMENT_COURSE', label: 'Điều trị theo lộ trình — nhiều buổi có theo dõi' }]} helperText="Dịch vụ theo lộ trình cần mẫu đã xuất bản tại mục Lộ trình điều trị trước khi khách mua." />
            <Input label="Đường dẫn slug" maxLength={150} value={form.slug} onChange={event => update('slug', event.target.value)} helperText={`Đường dẫn: /${form.slug || slugify(form.name) || 'ten-dich-vu'} · Tự tạo từ tên khi để trống.`} placeholder="phuc-hoi-da-chuyen-sau" />
            <Select label="Danh mục liệu trình" value={form.categoryId} onChange={event => update('categoryId', event.target.value)} options={[{ value: '', label: 'Chưa phân nhóm' }, ...categories.map(category => ({ value: String(category.id), label: category.name }))]} />
            <Input label="Giá niêm yết (VND)" type="number" min={0} max={99999999} step={1} required value={form.basePrice} onChange={event => update('basePrice', event.target.value)} />
            <div className="spa-editor-two-columns"><Input label="Thời gian thực hiện (phút)" type="number" min={1} max={720} step={1} required value={form.durationMinutes} onChange={event => update('durationMinutes', event.target.value)} />
              <Input label="Khử trùng / chuẩn bị (phút)" type="number" min={0} max={719} step={1} required value={form.preparationTimeMinutes} onChange={event => update('preparationTimeMinutes', event.target.value)} /></div>
            <Input label="Mô tả ngắn" maxLength={250} value={form.shortDescription} onChange={event => update('shortDescription', event.target.value)} />
            <label className="spa-editor-textarea">Mô tả quy trình<textarea rows={3} value={form.description} onChange={event => update('description', event.target.value)} placeholder="Các bước thực hiện và lưu ý khi chăm sóc…" /></label>
            <label className="spa-catalog-checkbox"><input type="checkbox" checked={form.requiresCertification} onChange={event => update('requiresCertification', event.target.checked)} />Chỉ giao cho nhân viên có chứng nhận còn hiệu lực</label>
            <label className="spa-catalog-checkbox"><input type="checkbox" checked={form.active} onChange={event => update('active', event.target.checked)} />Đang hoạt động</label>
            <ServiceMaterialsEditor serviceId={service?.id} />
          </div><aside className="spa-service-preview"><div className="spa-service-preview-image">{form.thumbnailUrl && !previewFailed ? <img src={resolveMediaUrl(form.thumbnailUrl)} alt="Ảnh xem trước dịch vụ" onError={() => setPreviewFailed(true)} /> : <Sparkles size={42} />}</div>
            <Input label="Ảnh thumbnail (URL)" maxLength={500} value={form.thumbnailUrl} onChange={event => { update('thumbnailUrl', event.target.value); setPreviewFailed(false); }} placeholder="/uploads/anh-dich-vu.jpg" />
            {previewFailed && <small className="spa-catalog-muted">Chưa tải được ảnh xem trước. Vui lòng kiểm tra đường dẫn.</small>}
            <strong>{form.name || 'Dịch vụ mới'}</strong><span>{formatCurrency(form.basePrice)}</span><p><Clock size={15} />{form.durationMinutes || 0} phút + {form.preparationTimeMinutes || 0}p chuẩn bị</p>
            <small>Tổng thời gian giữ tài nguyên: {Number(form.durationMinutes) + Number(form.preparationTimeMinutes)} phút.</small>
          </aside></div>
        </div>
        <div role="tabpanel" id={`${tabId}-resources-panel`} aria-labelledby={`${tabId}-resources`} hidden={tab !== 'resources'}>
          <p className="spa-catalog-muted">Chọn các loại giường, phòng và máy bắt buộc cho một ca. Hệ thống phân bổ tài nguyên cụ thể phù hợp khi đặt lịch.</p>
          {resourceLoading && <p role="status">Đang tải định mức tài nguyên…</p>}
          {!resourceLoaded && !resourceLoading && <Button variant="outline" onClick={loadResources}><RefreshCw size={16} />Tải lại định mức</Button>}
          {resourceLoaded && <><div className="spa-resource-rows">{requirements.map((row, index) => <div className="spa-resource-row" key={index}>
            <Select label={`Loại tài nguyên ${index + 1}`} value={row.type} onChange={event => setRequirements(previous => previous.map((item, position) => position === index ? { ...item, type: event.target.value } : item))}
              options={[{ value: '', label: 'Chọn loại tài nguyên' }, ...types.map(type => ({ value: type, label: `${resourceName(type)} (${type})` }))]} />
            <Input label={`Số đơn vị ${index + 1}`} type="number" required min={1} step={1} value={row.units} onChange={event => setRequirements(previous => previous.map((item, position) => position === index ? { ...item, units: event.target.value } : item))} />
            <Button variant="ghost" aria-label={`Xóa tài nguyên ${index + 1}`} onClick={() => setRequirements(previous => previous.filter((_, position) => position !== index))}><Trash2 size={17} /></Button>
            <small>{facilities.filter(facility => facility.type === row.type && facility.active !== false).map(facility => `${facility.name} (${facility.capacity} đơn vị)`).join(' · ') || 'Chưa có tài nguyên hoạt động thuộc loại này.'}</small>
          </div>)}</div>
          {!requirements.length && <div className="spa-catalog-empty compact"><Building size={27} /><strong>Chưa yêu cầu tài nguyên bắt buộc</strong><p>Thêm giường hoặc máy nếu dịch vụ cần giữ chỗ tại cơ sở.</p></div>}
          <Button variant="outline" onClick={() => { const type = types.find(value => !requirements.some(row => row.type === value)) || ''; setRequirements(previous => [...previous, { type, units: 1 }]); }}><Plus size={16} />Thêm loại tài nguyên</Button>
          {missingResources.length > 0 && <p className="spa-catalog-notice warning">Cơ sở chưa có tài nguyên hoạt động cho: {missingResources.map(row => resourceName(row.type)).join(', ')}. Cần bổ sung tại tab Phòng & Thiết bị trước khi nhận lịch cần các tài nguyên này.</p>}
          </>}
        </div>
      </fieldset>
      <div className="spa-editor-actions"><span className="spa-catalog-muted">{partial ? 'Tiếp tục lưu cho dịch vụ đã tạo.' : 'Áp dụng cho cấu hình dịch vụ và lịch đặt mới.'}</span><Button variant="outline" disabled={busy} onClick={onClose}>{uncertain ? 'Đóng & kiểm tra danh mục' : 'Hủy'}</Button>{!uncertain && <Button type="submit" loading={busy} disabled={!resourceLoaded || resourceLoading}>Lưu dịch vụ & tài nguyên</Button>}</div>
    </form>
  </Modal>;
};
