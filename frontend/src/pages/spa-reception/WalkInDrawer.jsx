import React, { useEffect, useRef, useState } from 'react';
import { Phone, UserRound, Sparkles, Check, Ticket } from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { formatCurrency, getVietnamDateString } from '../../shared/utils/formatters';
import { spaErrorMessage } from '../../shared/utils/spaMessages';
import { asList, bookingDefaults, normalizePhone, unwrap } from './receptionUtils';

export const WalkInDrawer = ({ services, facilities, facilityId, onClose, onBooked }) => {
  const [form, setForm] = useState(() => bookingDefaults(services, facilityId));
  const [lookup, setLookup] = useState({ state: 'idle', customers: [] });
  const [availableStaff, setAvailableStaff] = useState([]);
  const [staffLoading, setStaffLoading] = useState(false);
  const [staffError, setStaffError] = useState('');
  const [tickets, setTickets] = useState([]);
  const [ticketError, setTicketError] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [createdAppointment, setCreatedAppointment] = useState(null);
  const [uncertainBooking, setUncertainBooking] = useState(false);
  const submitting = useRef(false);
  const update = (key, value) => setForm(previous => ({ ...previous, [key]: value }));
  const selectCustomer = customer => {
    setForm(previous => ({ ...previous, customerId: String(customer.id), customerName: customer.fullName || '', ticketId: '' }));
    setLookup({ state: 'found', customers: [customer] });
  };
  useEffect(() => {
    const phone = normalizePhone(form.phone);
    if (!/^0[35789]\d{8}$/.test(phone)) { setLookup({ state: 'idle', customers: [] }); return; }
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setLookup({ state: 'loading', customers: [] });
      try {
        const response = await apiClient.get(`${ENDPOINTS.CRM.CUSTOMERS}?keyword=${encodeURIComponent(phone.slice(-9))}`, { signal: controller.signal });
        if (controller.signal.aborted) return;
        const customers = asList(response).filter(customer => normalizePhone(customer.phone) === phone && customer.status === 'ACTIVE');
        if (customers.length === 1) selectCustomer(customers[0]);
        else setLookup({ state: customers.length ? 'choose' : 'new', customers });
      } catch (err) {
        if (!controller.signal.aborted) { setLookup({ state: 'error', customers: [] }); setError(spaErrorMessage(err, 'Chưa tra cứu được CRM. Vui lòng thử nhập lại SĐT.')); }
      }
    }, 350);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [form.phone]);
  useEffect(() => {
    const controller = new AbortController();
    if (!form.serviceId || !form.appointmentDate || !form.startTime) return;
    setStaffLoading(true); setStaffError(''); setAvailableStaff([]);
    const load = async () => {
      try {
        const qualified = asList(await apiClient.get(ENDPOINTS.SPA.PUBLIC_SERVICE_STAFF(form.serviceId), { signal: controller.signal }));
        const results = await Promise.allSettled(qualified.map(staff => apiClient.get(
          ENDPOINTS.SPA.PUBLIC_AVAILABLE_SLOTS(form.serviceId, form.appointmentDate, staff.id), { signal: controller.signal })));
        if (controller.signal.aborted) return;
        const candidates = qualified.map((staff, index) => ({ ...staff, available: results[index].status === 'fulfilled'
          ? asList(results[index].value).some(slot => String(slot).slice(0, 5) === form.startTime.slice(0, 5)) : null }));
        setAvailableStaff(candidates);
        if (results.some(result => result.status === 'rejected')) setStaffError('Chưa kiểm tra được lịch của một số KTV. Vui lòng chọn giờ khác để kiểm tra lại.');
        setForm(previous => ({ ...previous, staffId: candidates.some(staff => String(staff.id) === previous.staffId && staff.available) ? previous.staffId : '' }));
      } catch (err) { if (!controller.signal.aborted) setStaffError(spaErrorMessage(err, 'Chưa tải được lịch KTV. Vui lòng chọn lại dịch vụ.')); }
      finally { if (!controller.signal.aborted) setStaffLoading(false); }
    };
    load();
    return () => controller.abort();
  }, [form.serviceId, form.appointmentDate, form.startTime]);
  useEffect(() => {
    const controller = new AbortController();
    setTickets([]); setTicketError('');
    if (!form.customerId) return;
    apiClient.get(`${ENDPOINTS.SPA.TICKETS}?userId=${form.customerId}&status=ACTIVE&size=100`, { signal: controller.signal })
      .then(response => { if (!controller.signal.aborted) setTickets(asList(response)); })
      .catch(err => { if (!controller.signal.aborted) setTicketError(spaErrorMessage(err, 'Chưa tra cứu được vé liệu trình của khách.')); });
    return () => controller.abort();
  }, [form.customerId]);
  const service = services.find(item => String(item.id) === form.serviceId);
  const eligibleTickets = tickets.filter(ticket => Number(ticket.remainingByService?.[form.serviceId]) > 0
    && (!ticket.expiryDate || Date.parse(ticket.expiryDate) > Date.now()));
  const checkInAllowed = Boolean(form.staffId) && form.appointmentDate === getVietnamDateString();
  const submit = async event => {
    event.preventDefault();
    if (submitting.current || createdAppointment || uncertainBooking) return;
    if (!/^0[35789]\d{8}$/.test(normalizePhone(form.phone))) { setError('Vui lòng nhập số điện thoại di động Việt Nam hợp lệ.'); return; }
    if (lookup.state === 'loading' || lookup.state === 'error' || lookup.state === 'choose') { setError('Vui lòng hoàn tất tra cứu và chọn đúng hồ sơ khách trước khi đặt lịch.'); return; }
    submitting.current = true; setBusy(true); setError('');
    let appointment;
    let bookingAttempted = false;
    try {
      let customerId = form.customerId;
      if (!customerId) {
        const customer = unwrap(await apiClient.post(ENDPOINTS.CRM.CUSTOMERS, { fullName: form.customerName.trim(), phone: normalizePhone(form.phone) }));
        customerId = String(customer.id);
        setForm(previous => ({ ...previous, customerId, customerName: customer.fullName }));
      }
      bookingAttempted = true;
      appointment = unwrap(await apiClient.post(ENDPOINTS.SPA.BOOK_APPOINTMENT, {
        targetUserId: Number(customerId), appointmentDate: form.appointmentDate, startTime: form.startTime,
        notes: `[Tiếp đón tại quầy] ${form.notes.trim()}`.trim(),
        items: [{ serviceId: Number(form.serviceId), ...(form.staffId ? { staffId: Number(form.staffId) } : {}),
          ...(form.facilityId ? { facilityId: Number(form.facilityId) } : {}), ...(form.ticketId ? { ticketId: Number(form.ticketId) } : {}) }],
      }));
      setCreatedAppointment(appointment);
      if (form.autoCheckIn && checkInAllowed) {
        if (appointment.status === 'PENDING') {
          await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointment.id), {
            status: 'CONFIRMED', staffAssignments: Object.fromEntries((appointment.items || []).map(item => [item.id, Number(form.staffId)])),
          });
        }
        await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(appointment.id));
      }
      onBooked('Đã tạo lịch hẹn' + (form.autoCheckIn && checkInAllowed ? ' và check-in khách.' : ' cho khách.'));
      onClose();
    } catch (err) {
      if (appointment) {
        setError(`Đã tạo lịch SPA-${appointment.id}, nhưng chưa hoàn tất check-in. ${spaErrorMessage(err, 'Vui lòng kiểm tra lịch tại quầy và check-in lại.')}`);
        onBooked('Lịch đã được tạo. Vui lòng kiểm tra bước xác nhận / check-in.', 'warning');
      } else if (bookingAttempted && (!(err.status || err.response?.status) || (err.status || err.response?.status) >= 500 || (err.status || err.response?.status) === 408)) {
        setUncertainBooking(true);
        setError('Kết nối gián đoạn khi đặt lịch. Vui lòng đóng drawer và kiểm tra lịch theo SĐT trước khi tạo lại.');
        onBooked('Vui lòng kiểm tra danh sách lịch hẹn trước khi tạo lại.', 'warning');
      } else setError(spaErrorMessage(err, 'Chưa tạo được lịch hẹn. Vui lòng kiểm tra phòng, KTV và khung giờ.'));
    } finally { submitting.current = false; setBusy(false); }
  };
  return <Modal isOpen onClose={() => { if (!busy) onClose(); }} title="Đón khách tại quầy" className="reception-dialog" presentation="drawer" maxWidth="580px">
    <form onSubmit={submit} className="walkin-form">
      <p className="reception-muted">Tra cứu khách, chọn dịch vụ và chuẩn bị ca chăm sóc.</p>
      {error && <div className="reception-notice error" role="alert">{error}</div>}
      <fieldset disabled={busy || Boolean(createdAppointment) || uncertainBooking}>
        <div className="drawer-step"><span>01</span><h4><UserRound size={17} /> Hồ sơ khách hàng</h4></div>
        <Input label="Số điện thoại khách" type="tel" autoComplete="tel" required maxLength={20} placeholder="090 123 4567" value={form.phone}
          onChange={event => { setForm(previous => ({ ...previous, phone: event.target.value, customerId: '', customerName: '', ticketId: '' })); setError(''); }} />
        <div className={`crm-lookup ${lookup.state}`} role="status">
          <Phone size={15} /> {lookup.state === 'loading' ? 'Đang tra cứu hồ sơ CRM…' : lookup.state === 'found' ? `Khách quen · ${lookup.customers[0]?.membershipTier || 'Thành viên'}`
            : lookup.state === 'new' ? 'Khách mới · Hồ sơ sẽ được tạo khi xác nhận đón khách.' : lookup.state === 'error' ? 'Chưa tra cứu được hồ sơ.' : 'Nhập đủ SĐT để tìm khách quen.'}
        </div>
        {lookup.state === 'choose' && <Select label="Chọn hồ sơ khách" value={form.customerId} required onChange={event => selectCustomer(lookup.customers.find(customer => String(customer.id) === event.target.value))}
          options={[{ value: '', label: 'Chọn đúng khách hàng' }, ...lookup.customers.map(customer => ({ value: String(customer.id), label: `${customer.fullName} · ${customer.phone}` }))]} />}
        <Input label="Họ và tên khách" required maxLength={100} readOnly={Boolean(form.customerId)} value={form.customerName} onChange={event => update('customerName', event.target.value)} placeholder="Nhập tên khách hàng" />
        <div className="drawer-step"><span>02</span><h4><Sparkles size={17} /> Dịch vụ & lịch chăm sóc</h4></div>
        <Select label="Dịch vụ" required value={form.serviceId} onChange={event => { update('serviceId', event.target.value); update('ticketId', ''); update('staffId', ''); }}
          options={[{ value: '', label: 'Chọn dịch vụ' }, ...services.map(item => ({ value: String(item.id), label: `${item.name} · ${formatCurrency(item.basePrice ?? item.price)}` }))]} />
        <div className="drawer-grid"><Input label="Ngày hẹn" type="date" required min={getVietnamDateString()} value={form.appointmentDate} onChange={event => update('appointmentDate', event.target.value)} />
          <Input label="Giờ bắt đầu" type="time" required min="08:00" max="19:30" step={1800} value={form.startTime.slice(0, 5)} onChange={event => update('startTime', `${event.target.value}:00`)} /></div>
        <Select label="Phòng / giường ưu tiên" value={form.facilityId} onChange={event => update('facilityId', event.target.value)} options={[
          { value: '', label: 'Hệ thống tự xếp tài nguyên phù hợp' }, ...facilities.filter(item => item.active !== false).map(item => ({ value: String(item.id), label: item.name })),
        ]} />
        <div className="staff-suggestions"><div className="reception-label">KTV rảnh tại khung giờ đã chọn</div>
          {staffLoading ? <p role="status">Đang kiểm tra ca KTV…</p> : <>
            <div className="staff-options">{availableStaff.filter(staff => staff.available).map(staff => <button type="button" key={staff.id} aria-pressed={form.staffId === String(staff.id)} onClick={() => update('staffId', String(staff.id))}>
              <span className="staff-avatar">{staff.fullName?.slice(0, 1) || 'K'}</span><span>{staff.fullName}<small>{staff.specialty || 'Kỹ thuật viên'}</small></span>{form.staffId === String(staff.id) && <Check size={16} />}</button>)}</div>
            {!availableStaff.some(staff => staff.available) && <p className="reception-muted">Chưa có KTV rảnh đã xác minh. Có thể đổi giờ hoặc tạo lịch chờ phân ca.</p>}
            {form.staffId && <button type="button" className="reception-text-button" onClick={() => update('staffId', '')}>Để phân ca sau</button>}
          </>}
          {staffError && <p role="alert" className="reception-muted">{staffError}</p>}
        </div>
        <div className="drawer-step"><span>03</span><h4><Ticket size={17} /> Vé liệu trình & ghi chú</h4></div>
        <Select label="Vé liệu trình của khách" value={form.ticketId} onChange={event => update('ticketId', event.target.value)} options={[
          { value: '', label: 'Thanh toán dịch vụ riêng tại quầy' }, ...eligibleTickets.map(ticket => ({ value: String(ticket.id), label: `${ticket.packageName} · Còn ${ticket.remainingByService[form.serviceId]} lượt dịch vụ này` })),
        ]} />
        {ticketError && <p role="alert" className="reception-muted">{ticketError}</p>}
        <Input label="Ghi chú tiếp đón" maxLength={450} value={form.notes} onChange={event => update('notes', event.target.value)} placeholder="Yêu cầu của khách, lưu ý khi đón…" />
        <label className="reception-checkbox"><input type="checkbox" checked={form.autoCheckIn && checkInAllowed} disabled={!checkInAllowed} onChange={event => update('autoCheckIn', event.target.checked)} /> Check-in ngay sau khi tạo lịch</label>
        {!checkInAllowed && <small className="reception-muted">Check-in áp dụng cho lịch hôm nay đã chọn KTV.</small>}
      </fieldset>
      <div className="drawer-submit"><div><small>Dịch vụ đã chọn</small><strong>{service?.name || 'Chưa chọn dịch vụ'}</strong><span>{form.ticketId ? 'Sử dụng vé liệu trình' : formatCurrency(service?.basePrice ?? service?.price ?? 0)}</span></div>
        {createdAppointment || uncertainBooking ? <Button onClick={onClose}>Đóng & kiểm tra lịch</Button> : <Button type="submit" loading={busy} disabled={!services.length || staffLoading || lookup.state === 'loading'}>Xác nhận đón khách</Button>}
      </div>
    </form>
  </Modal>;
};
