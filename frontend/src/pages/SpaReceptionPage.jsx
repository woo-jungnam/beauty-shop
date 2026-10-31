import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowRight, BedDouble, CalendarDays, Check, CheckCircle2, Clock3, LayoutGrid, List, LogOut, MoreHorizontal, Phone, Plus, RefreshCw, Search, Sparkles, Ticket, Users } from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Modal } from '../shared/ui/Modal';
import { DataTable } from '../shared/ui/DataTable';
import { SpaVisitCheckout } from '../shared/ui/SpaVisitCheckout';
import { useAuth } from '../app/providers/AuthProvider';
import { formatCurrency, formatDate, getVietnamDateString } from '../shared/utils/formatters';
import { spaErrorMessage } from '../shared/utils/spaMessages';
import { appointmentCode, asList, canCheckInToday, canFinish, facilityState, KANBAN_COLUMNS, localInstant, normalizePhone, SPA_STATUS, spaTime, unwrap } from './spa-reception/receptionUtils';
import { useReceptionData } from './spa-reception/useReceptionData';
import { WalkInDrawer } from './spa-reception/WalkInDrawer';
import { TreatmentAppointmentDialog } from '../features/treatments/TreatmentAppointmentDialog';
import './spa-reception/spa-reception.css';


const Status = ({ appointment }) => <span className={`reception-status ${SPA_STATUS[appointment.status]?.tone || 'neutral'}`}>
  {appointment.status === 'CONFIRMED' ? appointment.checkedInAt ? 'Đã check-in' : 'Chưa check-in' : SPA_STATUS[appointment.status]?.label || 'Chưa xác định'}
</span>;

const AssignDialog = ({ appointment, checkIn, onClose, onAssign }) => {
  const [choices, setChoices] = useState({});
  const [assignments, setAssignments] = useState(() => Object.fromEntries((appointment.items || []).map(item => [item.id, String(item.staffId || '')])));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    Promise.allSettled((appointment.items || []).map(item => apiClient.get(ENDPOINTS.SPA.PUBLIC_SERVICE_STAFF(item.serviceId), { signal: controller.signal })))
      .then(results => {
        if (controller.signal.aborted) return;
        const options = {};
        results.forEach((result, index) => { if (result.status === 'fulfilled') options[appointment.items[index].id] = asList(result.value); });
        setChoices(options);
        if (results.some(result => result.status === 'rejected')) setError('Chưa tải được KTV phù hợp cho một số dịch vụ. Vui lòng mở lại cửa sổ để thử lại.');
      });
    return () => controller.abort();
  }, [appointment]);
  const submit = async event => {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true; setBusy(true); setError('');
    try {
      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointment.id), { status: 'CONFIRMED', staffAssignments: Object.fromEntries(Object.entries(assignments).map(([key, value]) => [key, Number(value)])) });
      if (checkIn) {
        try { await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(appointment.id)); }
        catch (err) { onAssign('Đã chỉ định KTV và xác nhận lịch. ' + spaErrorMessage(err, 'Chưa check-in được khách. Vui lòng kiểm tra lịch và thử lại.'), 'warning'); onClose(); return; }
      }
      onAssign(checkIn ? 'Đã chỉ định KTV và check-in khách.' : (appointment.status === 'CONFIRMED' ? 'Đã cập nhật kỹ thuật viên.' : 'Đã chỉ định KTV và xác nhận lịch.')); onClose();
    } catch (err) { setError(spaErrorMessage(err, 'Chưa chỉ định KTV được. Vui lòng kiểm tra lịch làm việc của KTV.')); }
    finally { submitting.current = false; setBusy(false); }
  };
  return <Modal isOpen onClose={() => { if (!busy) onClose(); }} title={checkIn ? 'Chỉ định KTV & check-in' : (appointment.status === 'CONFIRMED' ? 'Đổi chuyên viên' : 'Chỉ định KTV nhanh')} className="reception-dialog">
    <form className="reception-form" onSubmit={submit}>
      <p><strong>{appointment.customerName}</strong> · {appointmentCode(appointment)}<br /><small className="reception-muted">{formatDate(appointment.appointmentDate)} · {appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</small></p>
      {error && <div role="alert" className="reception-notice error">{error}</div>}
      {(appointment.items || []).map(item => <Select key={item.id} label={item.serviceName} required disabled={busy} value={assignments[item.id] || ''}
        onChange={event => setAssignments(previous => ({ ...previous, [item.id]: event.target.value }))} options={[
          { value: '', label: choices[item.id] ? 'Chọn KTV đủ chuyên môn' : 'Đang tải KTV…' }, ...(choices[item.id] || []).map(staff => ({ value: String(staff.id), label: `${staff.fullName}${staff.specialty ? ' · ' + staff.specialty : ''}` })),
        ]} />)}
      <p className="reception-muted">Hệ thống kiểm tra ca làm việc và lịch trùng khi xác nhận chỉ định KTV.</p>
      <Button type="submit" loading={busy} disabled={(appointment.items || []).some(item => !choices[item.id] || !assignments[item.id])}>{checkIn ? 'Xác nhận & check-in' : (appointment.status === 'CONFIRMED' ? 'Lưu thay đổi kỹ thuật viên' : 'Xác nhận chỉ định KTV')}</Button>
    </form>
  </Modal>;
};

export const SpaReceptionPage = () => {
  const { user, logout, isAdmin } = useAuth();
  const data = useReceptionData();
  const [now, setNow] = useState(() => new Date());
  const [workspaceTab, setWorkspaceTab] = useState('queue');
  const [view, setView] = useState(() => { try { return localStorage.getItem('spa_reception_view') === 'list' ? 'list' : 'kanban'; } catch { return 'kanban'; } });
  const [date, setDate] = useState('');
  const [status, setStatus] = useState('ALL');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [walkIn, setWalkIn] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const [workspaceId, setWorkspaceId] = useState(null);
  const [assign, setAssign] = useState(null);
  const [edit, setEdit] = useState(null);
  const [editForm, setEditForm] = useState({ date: '', time: '', note: '' });
  const [editError, setEditError] = useState('');
  const [editSlots,setEditSlots]=useState([]),[editSlotsLoading,setEditSlotsLoading]=useState(false);
  useEffect(()=>{
    if(edit?.type!=='reschedule'||!editForm.date)return;
    const controller=new AbortController();setEditSlots([]);setEditSlotsLoading(true);
    apiClient.get(`/api/v1/appointments/${edit.appointment.id}/available-slots?date=${editForm.date}`,{signal:controller.signal})
      .then(r=>{if(!controller.signal.aborted)setEditSlots(asList(r).map(s=>String(s).slice(0,5)));})
      .catch(e=>{if(!controller.signal.aborted)setEditError(spaErrorMessage(e,'Chưa tải được giờ trống.'));})
      .finally(()=>{if(!controller.signal.aborted)setEditSlotsLoading(false);});
    return()=>controller.abort();
  },[edit?.appointment?.id,edit?.type,editForm.date]);
  const [ticketOpen, setTicketOpen] = useState(false);
  const [ticketPhone, setTicketPhone] = useState('');
  const [ticketResult, setTicketResult] = useState(null);
  const [ticketError, setTicketError] = useState('');
  const [ticketLoading, setTicketLoading] = useState(false);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState('');
  const actionLock = useRef(false);
  useEffect(() => { const timer = setInterval(() => setNow(new Date()), 10000); return () => clearInterval(timer); }, []);
  useEffect(() => { setPage(0); }, [date, status, search]);
  useEffect(() => { setCheckout(previous => previous ? data.appointments.find(appointment => appointment.id === previous.id) || previous : null); }, [data.appointments]);
  const changeView = next => { setView(next); try { localStorage.setItem('spa_reception_view', next); } catch {} };
  const afterAction = (message, tone = 'success') => { setNotice({ message, tone }); data.refresh(); };
  const today = getVietnamDateString(now);
  const currentFacilities = useMemo(() => data.facilities.map(facility => ({ ...facility,
    live: facilityState(facility, data.appointments, data.blocks[facility.id], now,
      Boolean(data.updatedAt) && now.getTime() - data.updatedAt < 90000 && data.blocks[facility.id] !== undefined),
  })), [data.facilities, data.appointments, data.blocks, data.updatedAt, now]);
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi');
    return data.appointments.filter(appointment => (!date || appointment.appointmentDate === date) && (status === 'ALL' || appointment.status === status)
      && (!term || [appointment.customerName, appointment.customerPhone, appointmentCode(appointment), `SPA-${appointment.id}`, `SPA-APT-#${appointment.id}`, ...((appointment.items || []).map(item => item.serviceName))].some(value => String(value || '').toLocaleLowerCase('vi').includes(term))))
      .sort((a, b) => localInstant(a.appointmentDate, a.startTime) - localInstant(b.appointmentDate, b.startTime) || a.id - b.id);
  }, [data.appointments, date, status, search]);
  const todayAppointments = data.appointments.filter(appointment => appointment.appointmentDate === today);
  const checkedInCount = todayAppointments.filter(a => a.status === 'CONFIRMED' && Boolean(a.checkedInAt)).length;
  const inProgressCount = todayAppointments.filter(a => a.status === 'IN_PROGRESS').length;
  const waitingStaffCount = todayAppointments.filter(a => a.status === 'PENDING' || (a.status === 'CONFIRMED' && (a.items || []).some(item => !item.staffId))).length;
  const availableBeds = currentFacilities.reduce((sum, f) => sum + (f.live?.available || 0), 0);
  const totalBeds = currentFacilities.reduce((sum, f) => sum + (f.live?.capacity || 0), 0);
  const exceptions = filtered.filter(appointment => ['CANCELLED', 'NO_SHOW'].includes(appointment.status)).length;

  useEffect(() => {
    const onKeyDown = event => {
      const tag = event.target?.tagName?.toLowerCase();
      const isInput = tag === 'input' || tag === 'textarea' || tag === 'select' || event.target?.isContentEditable;

      if (event.key === 'Escape') {
        if (walkIn) { setWalkIn(null); return; }
        if (assign) { setAssign(null); return; }
        if (checkout) { setCheckout(null); return; }
        if (edit) { setEdit(null); return; }
        if (ticketOpen) { setTicketOpen(false); return; }
        if (workspaceId) { setWorkspaceId(null); return; }
        return;
      }

      if (isInput) return;

      if (event.key === '/') {
        event.preventDefault();
        document.getElementById('reception-search-input')?.focus();
      } else if (event.key === 'n' || event.key === 'N') {
        event.preventDefault();
        setWalkIn({ facilityId: '' });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [walkIn, assign, checkout, edit, ticketOpen, workspaceId]);

  const getCardTiming = appointment => {
    const nowMs = now.getTime();
    const startMs = localInstant(appointment.appointmentDate, appointment.startTime);
    const endMs = localInstant(appointment.appointmentDate, appointment.endTime);

    if (appointment.status === 'IN_PROGRESS') {
      if (!isNaN(endMs)) {
        const diffMin = Math.round((endMs - nowMs) / 60000);
        if (diffMin > 0) {
          return { type: 'countdown', label: `Còn ~${diffMin}p`, urgent: diffMin <= 10 };
        } else {
          const overdue = Math.abs(diffMin);
          return { type: 'delay', label: `Quá ${overdue}p`, delayed: true };
        }
      }
    }

    if (['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt) {
      if (!isNaN(startMs) && appointment.appointmentDate === today) {
        const diffStart = Math.round((nowMs - startMs) / 60000);
        if (diffStart >= 10) {
          return { type: 'delay', label: `Trễ ${diffStart}p`, delayed: true };
        }
      }
    }

    return null;
  };

  const getStaffInitials = fullName => {
    if (!fullName) return 'KT';
    const words = fullName.trim().split(/\s+/);
    if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
    return (words[0][0] + words[words.length - 1][0]).toUpperCase();
  };
  const run = async (key, operation, message, onSuccess) => {
    if (actionLock.current) return;
    actionLock.current = true; setBusy(key);
    try { const result = await operation(); afterAction(message); onSuccess?.(result); }
    catch (err) { setNotice({ tone: 'error', message: spaErrorMessage(err, 'Thao tác chưa hoàn tất. Vui lòng làm mới lịch và thử lại.') }); data.refresh(); }
    finally { actionLock.current = false; setBusy(''); }
  };
  const checkIn = appointment => {
    if (appointment.status === 'PENDING' && (appointment.items || []).some(item => !item.staffId)) { setAssign({ appointment, checkIn: true }); return; }
    run(`check-${appointment.id}`, async () => {
      if (appointment.status === 'PENDING') await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointment.id), {
        status: 'CONFIRMED', staffAssignments: Object.fromEntries((appointment.items || []).map(item => [item.id, item.staffId])),
      });
      await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(appointment.id));
    }, 'Đã check-in khách tại quầy.');
  };
  const complete = appointment => run(`finish-${appointment.id}`, () => apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointment.id), {
    status: 'COMPLETED', notes: 'Lễ tân xác nhận khách đã hoàn tất dịch vụ.',
  }), 'Đã hoàn tất dịch vụ. Có thể đối soát và thu ngân.', response => { setCheckout(unwrap(response) || { ...appointment, status: 'COMPLETED' }); });
  const rescheduleDuration = useMemo(() => {
    if (!edit?.appointment?.startTime || !edit?.appointment?.endTime) return 60;
    const [sh, sm] = edit.appointment.startTime.slice(0, 5).split(':').map(Number);
    const [eh, em] = edit.appointment.endTime.slice(0, 5).split(':').map(Number);
    const diff = (eh * 60 + em) - (sh * 60 + sm);
    return diff > 0 ? diff : 60;
  }, [edit?.appointment]);

  const rescheduleEstimatedEnd = useMemo(() => {
    if (!editForm.time) return '';
    const [h, m] = editForm.time.slice(0, 5).split(':').map(Number);
    if (isNaN(h) || isNaN(m)) return '';
    const total = h * 60 + m + rescheduleDuration;
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }, [editForm.time, rescheduleDuration]);

  const rescheduleExceedsClosing = useMemo(() => {
    return editSlotsLoading || !editSlots.includes(editForm.time);
  }, [editSlotsLoading,editSlots,editForm.time]);

  const openEdit = (appointment, type) => { setEdit({ appointment, type }); setEditForm({ date: appointment.appointmentDate, time: appointment.startTime?.slice(0, 5) || '09:00', note: '' }); setEditError(''); };
  const submitEdit = async event => {
    event.preventDefault();
    if (actionLock.current) return;
    if (edit.type === 'no-show' && !editForm.note.trim()) { setEditError('Vui lòng nhập lý do khách vắng mặt.'); return; }
    if (edit.type === 'reschedule' && rescheduleExceedsClosing) {
      setEditError('Chọn một giờ khả dụng theo ca trực, tài nguyên và giờ hoạt động của ngày mới.');
      return;
    }
    actionLock.current = true; setBusy('edit'); setEditError('');
    try {
      if (edit.type === 'reschedule') await apiClient.put(ENDPOINTS.SPA.RESCHEDULE_APPOINTMENT(edit.appointment.id), { appointmentDate: editForm.date, startTime: `${editForm.time}:00`, notes: editForm.note.trim() });
      else await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(edit.appointment.id), { status: 'NO_SHOW', notes: editForm.note.trim() });
      afterAction(edit.type === 'reschedule' ? 'Đã cập nhật thời gian hẹn.' : 'Đã ghi nhận khách vắng mặt.'); setEdit(null);
    } catch (err) { setEditError(spaErrorMessage(err, 'Chưa cập nhật được lịch hẹn. Vui lòng kiểm tra và thử lại.')); }
    finally { actionLock.current = false; setBusy(''); }
  };
  const lookupTickets = async event => {
    event.preventDefault();
    if (ticketLoading) return;
    setTicketLoading(true); setTicketError(''); setTicketResult(null);
    try {
      const phone = normalizePhone(ticketPhone);
      if (!/^0[35789]\d{8}$/.test(phone)) { setTicketError('Vui lòng nhập SĐT di động Việt Nam hợp lệ.'); return; }
      const customers = asList(await apiClient.get(`${ENDPOINTS.CRM.CUSTOMERS}?keyword=${encodeURIComponent(phone.slice(-9))}`)).filter(customer => normalizePhone(customer.phone) === phone);
      if (customers.length !== 1) { setTicketError(customers.length ? 'SĐT khớp nhiều hồ sơ. Vui lòng kiểm tra tại CRM.' : 'Chưa tìm thấy hồ sơ khách với SĐT này.'); return; }
      const tickets = asList(await apiClient.get(`${ENDPOINTS.SPA.TICKETS}?userId=${customers[0].id}&size=100`));
      setTicketResult({ customer: customers[0], tickets });
    } catch (err) { setTicketError(spaErrorMessage(err, 'Chưa tra cứu được vé liệu trình. Vui lòng thử lại.')); }
    finally { setTicketLoading(false); }
  };
  const actions = appointment => <div className="reception-card-actions">
    {['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt && <Button size="sm" disabled={Boolean(busy) || !canCheckInToday(appointment, now)} loading={busy === `check-${appointment.id}`} onClick={() => checkIn(appointment)}><Check size={15} /> Check-in ngay</Button>}
    {appointment.status === 'PENDING' && <Button size="sm" variant="outline" disabled={Boolean(busy)} onClick={() => setAssign({ appointment, checkIn: false })}>Chỉ định KTV</Button>}
    {appointment.status === 'CONFIRMED' && !appointment.checkedInAt && <Button size="sm" variant="outline" disabled={Boolean(busy)} onClick={() => setAssign({ appointment, checkIn: false })}>Đổi KTV</Button>}
    {appointment.status === 'CONFIRMED' && appointment.checkedInAt && <>
      <Button size="sm" disabled={Boolean(busy) || !canCheckInToday(appointment, now) || localInstant(appointment.appointmentDate, appointment.startTime) - 3600000 > now.getTime()} loading={busy === `start-${appointment.id}`}
        onClick={() => run(`start-${appointment.id}`, () => apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointment.id), { status: 'IN_PROGRESS', notes: 'Lễ tân bắt đầu dịch vụ.' }), 'Đã bắt đầu dịch vụ.')} aria-label="Bắt đầu ca"><ArrowRight size={15} /> Bắt đầu dịch vụ</Button>
      <Button size="sm" variant="outline" disabled={Boolean(busy)} onClick={() => setAssign({ appointment, checkIn: false })}>Đổi KTV</Button>
    </>}
    {appointment.status === 'IN_PROGRESS' && <Button size="sm" disabled={Boolean(busy)} loading={busy === `finish-${appointment.id}`} onClick={() => canFinish(appointment) ? complete(appointment) : setWorkspaceId(appointment.id)} aria-label="Hoàn tất & thu ngân">Xong dịch vụ & Thu ngân</Button>}
    {appointment.status === 'COMPLETED' && <Button size="sm" onClick={() => setCheckout(appointment)}>Mở thu ngân <ArrowRight size={15} /></Button>}
    <details className="reception-more"><summary aria-label={`Thao tác khác cho ${appointmentCode(appointment)}`}><MoreHorizontal size={18} /></summary><div>
      <button type="button" onClick={() => setWorkspaceId(appointment.id)}>Chuẩn bị / thực hiện ca</button>
      {['PENDING', 'CONFIRMED'].includes(appointment.status) && <button type="button" onClick={() => openEdit(appointment, 'reschedule')}>Đổi lịch hẹn</button>}
      {appointment.status === 'CONFIRMED' && <button type="button" onClick={() => setAssign({ appointment, checkIn: false })}>Đổi chuyên viên</button>}
      {appointment.status === 'CONFIRMED' && <button type="button" onClick={() => openEdit(appointment, 'no-show')}>Ghi nhận vắng mặt</button>}
    </div></details>
  </div>;
  const columns = [
    { key: 'customer', header: 'Khách hàng', render: appointment => <div className="reception-table-customer"><strong>{appointment.customerName || 'Khách hàng'}</strong><small>{appointment.customerPhone || 'Chưa có SĐT'} · {appointmentCode(appointment)}</small></div> },
    { key: 'time', header: 'Lịch hẹn', render: appointment => <><strong>{appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</strong><small className="reception-block reception-muted">{formatDate(appointment.appointmentDate)}</small></> },
    { key: 'services', header: 'Dịch vụ / KTV', render: appointment => (appointment.items || []).map(item => <div key={item.id}>{item.serviceName}<small className="reception-block reception-muted">{item.staffName || 'Chưa chỉ định KTV'}{item.ticketId ? ' · Vé liệu trình' : ''}</small></div>) },
    { key: 'status', header: 'Trạng thái', render: appointment => <Status appointment={appointment} /> },
    { key: 'actions', header: 'Thao tác', render: actions },
  ];
  return <div className="spa-reception-page">
    {workspaceId && <TreatmentAppointmentDialog appointmentId={workspaceId} role="staff" onClose={() => setWorkspaceId(null)} onSaved={afterAction} />}
    <header className="reception-header"><div className="reception-brand"><span><Sparkles size={22} /></span><div><strong>BeautyShop <em>Atelier Spa</em></strong><small>KHÔNG GIAN CHĂM SÓC · QUẦY LỄ TÂN</small></div></div>
      <div className="reception-user"><span><strong>{user?.fullName || user?.username || 'Lễ tân'}</strong><small>{spaTime(now)} · {formatDate(today)}</small></span><a href="#/staff/treatments">Lộ trình điều trị</a><a href="#/staff">Ca KTV</a>{isAdmin && <a href="#/admin/spa">Quản trị Spa</a>}<Button variant="ghost" size="sm" onClick={logout} aria-label="Đăng xuất"><LogOut size={17} /></Button></div>
    </header>
    <main className="reception-main">
      <div className="reception-title-row">
        <div>
          <span className="reception-eyebrow">ĐIỀU PHỐI & TIẾP ĐÓN</span>
          <h1>Quầy lễ tân Spa</h1>
          <p>Mỗi lượt đón khách, một trải nghiệm chăm sóc trọn vẹn.</p>
        </div>
      </div>

      <nav className="reception-nav-bar" aria-label="Khu vực làm việc lễ tân">
        <div className="reception-nav-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={workspaceTab === 'queue'}
            className={`reception-nav-tab ${workspaceTab === 'queue' ? 'is-active' : ''}`}
            onClick={() => setWorkspaceTab('queue')}
          >
            <CalendarDays size={17} />
            <span>Tiếp đón & Lịch hẹn</span>
            <span className="reception-tab-badge">{filtered.length}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={workspaceTab === 'rooms'}
            className={`reception-nav-tab ${workspaceTab === 'rooms' ? 'is-active' : ''}`}
            onClick={() => setWorkspaceTab('rooms')}
          >
            <BedDouble size={17} />
            <span>Sơ đồ Buồng phòng</span>
            <span className="reception-tab-badge highlight">
              {currentFacilities.filter(f => f.live.available > 0).length}/{currentFacilities.length} trống
            </span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={workspaceTab === 'tickets'}
            className={`reception-nav-tab ${workspaceTab === 'tickets' ? 'is-active' : ''}`}
            onClick={() => {
              setWorkspaceTab('tickets');
              setTicketError('');
            }}
          >
            <Ticket size={17} />
            <span>Tra cứu vé liệu trình</span>
          </button>
        </div>
        <div className="reception-quick-actions">
          <Button variant="outline" size="sm" onClick={() => { setTicketOpen(true); setTicketError(''); setTicketResult(null); }}>
            <Ticket size={16} /> Tra cứu nhanh
          </Button>
          <Button size="sm" onClick={() => setWalkIn({ facilityId: '' })} disabled={!data.services.length} aria-label="Đón khách vãng lai">
            <Plus size={16} /> Tạo lịch tại quầy <kbd className="reception-kbd">N</kbd>
          </Button>
        </div>
      </nav>

      {notice && <div className={`reception-notice ${notice.tone}`} role={notice.tone === 'error' || notice.tone === 'warning' ? 'alert' : 'status'}>{notice.message}<button type="button" onClick={() => setNotice(null)} aria-label="Đóng thông báo">×</button></div>}
      {data.error && <div className="reception-notice error" role="alert">{data.error}</div>}
      {data.appointmentsError && <div className="reception-notice error" role="alert">
        {data.appointmentsError} {data.appointmentsReady && 'Dữ liệu lịch hẹn chưa được cập nhật; đang hiển thị lần tải thành công trước đó.'}
        <Button size="sm" variant="outline" loading={data.refreshing} onClick={data.refresh}>Thử lại</Button>
      </div>}

      {workspaceTab === 'rooms' && (
        <section className="reception-facility-panel" aria-label="Sơ đồ phòng và giường chi tiết">
          <div className="reception-section-heading">
            <div>
              <h2><BedDouble size={19} /> Sơ đồ toàn bộ phòng & giường ({currentFacilities.length} phòng)</h2>
              <p>{data.updatedAt ? `Cập nhật lúc ${spaTime(new Date(data.updatedAt))} · Tự làm mới mỗi 30 giây` : 'Đang chờ dữ liệu'}</p>
            </div>
            <div className="facility-legend">
              <span className="free">Trống</span>
              <span className="busy">Đang có khách</span>
              <span className="paused">Tạm dừng</span>
              <Button variant="ghost" size="sm" loading={data.refreshing} onClick={data.refresh} aria-label="Làm mới dữ liệu quầy">
                <RefreshCw size={16} />
              </Button>
            </div>
          </div>
          <div className="facility-grid">
            {currentFacilities.map(facility => (
              <div key={facility.id} className={`facility-card-detailed ${facility.live.state}`}>
                <div className="facility-card-top">
                  <span className="facility-icon"><BedDouble size={22} /></span>
                  <span className="facility-state-dot" />
                </div>
                <div className="facility-card-body">
                  <h3>{facility.name}</h3>
                  <p>{facility.description || 'Khu vực dịch vụ chăm sóc da'}</p>
                </div>
                <div className="facility-card-meta">
                  <div><strong>Sức chứa:</strong> {facility.live.capacity} giường (Còn trống: {facility.live.available})</div>
                  <div><strong>Trạng thái:</strong> {facility.live.label}</div>
                  {facility.live.detail && <div><strong>Chi tiết:</strong> {facility.live.detail}</div>}
                </div>
                <Button
                  size="sm"
                  variant={facility.live.available > 0 ? "primary" : "outline"}
                  disabled={facility.live.available <= 0 || !data.services.length}
                  onClick={() => setWalkIn({ facilityId: String(facility.id) })}
                >
                  {facility.live.available > 0 ? 'Xếp khách vào phòng này' : 'Phòng đang đầy'}
                </Button>
              </div>
            ))}
          </div>
        </section>
      )}

      {workspaceTab === 'tickets' && (
        <section className="reception-ticket-workspace" aria-label="Tra cứu vé liệu trình">
          <div className="reception-section-heading">
            <div>
              <h2><Ticket size={19} /> Tra cứu & Quản lý vé liệu trình</h2>
              <p>Nhập số điện thoại khách hàng để kiểm tra số buổi còn lại và hạn sử dụng vé</p>
            </div>
          </div>
          <form className="reception-form" onSubmit={lookupTickets} style={{ maxWidth: 460, marginBottom: 20 }}>
            <Input
              label="Số điện thoại khách hàng"
              type="tel"
              placeholder="VD: 0981234567"
              required
              value={ticketPhone}
              onChange={event => setTicketPhone(event.target.value)}
            />
            <Button type="submit" loading={ticketLoading}>
              <Search size={16} /> Tra cứu vé
            </Button>
          </form>
          {ticketError && <div role="alert" className="reception-notice error">{ticketError}</div>}
          {ticketResult && (
            <div style={{ background: '#FAF7F8', padding: 18, borderRadius: 12, border: '1px solid #EDE3E7' }}>
              <h3 style={{ fontSize: 16, margin: '0 0 8px' }}>Khách hàng: {ticketResult.customer.fullName} · {ticketResult.customer.phone}</h3>
              <div className="reception-ticket-list" style={{ marginTop: 12 }}>
                {ticketResult.tickets.map(ticket => (
                  <div key={ticket.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'white', padding: 14, borderRadius: 10, border: '1px solid #EDE3E7', marginBottom: 10 }}>
                    <div>
                      <strong>{ticket.packageName}</strong>
                      <p style={{ margin: '4px 0', fontSize: 13 }}>Còn <strong>{ticket.remainingSessions}</strong> buổi · Đã dùng {ticket.usedSessions || 0} · Đang giữ {ticket.reservedSessions || 0}</p>
                      <small style={{ color: '#7C7076' }}>{ticket.status === 'ACTIVE' ? 'Đang hoạt động' : ticket.status === 'EXPIRED' ? 'Đã hết hạn' : 'Không còn khả dụng'}{ticket.expiryDate ? ` · Hạn ${formatDate(ticket.expiryDate)}` : ' · Không giới hạn thời gian'}</small>
                    </div>
                    {ticket.status === 'ACTIVE' && ticket.remainingSessions > 0 && (
                      <Button size="sm" onClick={() => setWalkIn({ facilityId: '' })}>
                        Tạo lịch dùng vé
                      </Button>
                    )}
                  </div>
                ))}
                {!ticketResult.tickets.length && <p>Khách hàng chưa có vé liệu trình nào.</p>}
              </div>
            </div>
          )}
        </section>
      )}

      {workspaceTab === 'queue' && (
        <>
          <section className="reception-facility-panel" aria-label="Trạng thái phòng và giường">
            <div className="reception-section-heading"><div><h2><BedDouble size={19} /> Phòng & giường trực tiếp <span className={`live-dot ${data.updatedAt ? '' : 'offline'}`} /></h2><p>{data.updatedAt ? `Cập nhật lúc ${spaTime(new Date(data.updatedAt))} · Tự làm mới mỗi 30 giây` : 'Đang chờ dữ liệu phòng / giường'}</p></div><div className="facility-legend"><span className="free">Trống</span><span className="busy">Đang có khách</span><span className="paused">Tạm dừng</span><Button variant="ghost" size="sm" loading={data.refreshing} onClick={data.refresh} aria-label="Làm mới dữ liệu quầy"><RefreshCw size={16} /></Button></div></div>
            <div className="facility-strip">{currentFacilities.map(facility => <button type="button" key={facility.id} className={`facility-tile ${facility.live.state}`} disabled={facility.live.available <= 0 || !data.services.length} onClick={() => setWalkIn({ facilityId: String(facility.id) })} aria-label={`${facility.name}: ${facility.live.label}. ${facility.live.detail}`}>
              <div><span className="facility-icon"><BedDouble size={21} /></span><span className="facility-state-dot" /></div><strong>{facility.name}</strong><span className="facility-label">Trống: {facility.live.available} / {facility.live.capacity} giường</span><small>{facility.live.state === 'paused' ? facility.live.label : facility.live.available > 0 ? `Còn trống ${facility.live.available} giường` : 'Hết giường trống'}</small>{facility.live.available > 0 && <span className="facility-cta">Nhận khách phòng này <Plus size={13} /></span>}
            </button>)}</div>
            {!currentFacilities.length && <p className="reception-muted">{data.refreshing ? 'Đang tải phòng / giường…' : data.facilitiesReady ? 'Chưa có phòng / giường được cấu hình.' : 'Chưa xác minh được phòng / giường.'}</p>}
          </section>

          <div className="reception-kpi-bar" aria-label="Chỉ số tiếp đón hôm nay">
            <div className="reception-kpi-pill rose" title="Khách đã check-in tại quầy">
              <CheckCircle2 size={14} />
              <span>Đã check-in:</span>
              <strong>{checkedInCount}</strong>
            </div>
            <div className="reception-kpi-pill blue" title="Khách đang thực hiện dịch vụ">
              <Sparkles size={14} />
              <span>Đang làm:</span>
              <strong>{inProgressCount}</strong>
            </div>
            <div className="reception-kpi-pill gold" title="Khách chờ tiếp đón hoặc cần KTV">
              <Users size={14} />
              <span>Chờ KTV:</span>
              <strong>{waitingStaffCount}</strong>
            </div>
            <div className="reception-kpi-pill green" title="Giường trống khả dụng trong tổng sức chứa">
              <BedDouble size={14} />
              <span>Giường trống:</span>
              <strong>{availableBeds} / {totalBeds}</strong>
            </div>
          </div>

          <section className="reception-queue" aria-label="Danh sách tiếp đón">
            <div className="reception-section-heading"><div><h2>Luồng tiếp đón <span className="reception-count">{filtered.length}</span></h2><p>{date ? formatDate(date) : 'Tất cả ngày hẹn'} · Theo dõi từ tiếp đón đến thu ngân</p></div><div className="reception-view-switch" role="group" aria-label="Chế độ hiển thị"><button type="button" aria-pressed={view === 'kanban'} onClick={() => changeView('kanban')}><LayoutGrid size={16} /> Bảng tiến độ</button><button type="button" aria-pressed={view === 'list'} onClick={() => changeView('list')}><List size={16} /> Danh sách</button></div></div>
            <div className="reception-filter-bar"><div className="reception-search"><Search size={17} /><Input id="reception-search-input" aria-label="Tìm tên, SĐT hoặc mã lịch hẹn" placeholder="Tìm tên, SĐT, mã lịch… (/)" value={search} onChange={event => setSearch(event.target.value)} /><kbd className="reception-search-kbd">/</kbd></div><Input aria-label="Lọc ngày hẹn" type="date" value={date} onChange={event => setDate(event.target.value)} /><div className="reception-date-shortcuts"><button type="button" aria-pressed={date === today} onClick={() => setDate(today)}>Hôm nay</button><button type="button" aria-pressed={!date} onClick={() => setDate('')}>Tất cả ngày</button></div><Select aria-label="Lọc trạng thái" value={status} onChange={event => setStatus(event.target.value)} options={[{ value: 'ALL', label: 'Tất cả trạng thái' }, ...Object.entries(SPA_STATUS).map(([value, state]) => ({ value, label: state.label }))]} /></div>
            {data.refreshing && <p className="reception-muted" role="status">Đang tải lịch hẹn…</p>}
            {data.appointmentsReady && (!data.appointmentsError || filtered.length > 0) && (view === 'kanban' ? <>
              {exceptions > 0 && <div className="reception-exceptions">Có {exceptions} lịch đã hủy / vắng mặt trong bộ lọc. <button type="button" onClick={() => changeView('list')}>Xem trong danh sách</button></div>}
              <div className="reception-kanban">{KANBAN_COLUMNS.map(column => {
                const appointments = filtered.filter(appointment => appointment.status === column.id);
                return <section className={`kanban-column ${column.tone}`} key={column.id} aria-label={column.title}><header><div><h3>{column.title} <span>{appointments.length}</span></h3><p>{column.caption}</p></div></header><div className="kanban-cards">{appointments.map(appointment => <article key={appointment.id} className="reception-appointment-card" data-appointment-id={appointment.id}>
                  <div className="reception-card-top">
                    <span>{appointmentCode(appointment)}</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      {(() => {
                        const timing = getCardTiming(appointment);
                        if (!timing) return null;
                        if (timing.type === 'delay') {
                          return <span className="reception-delay-badge" title="Lịch hẹn trễ so với giờ dự kiến">⚠️ {timing.label}</span>;
                        }
                        return <span className={`reception-countdown-badge ${timing.urgent ? 'urgent' : ''}`}><Clock3 size={11} /> {timing.label}</span>;
                      })()}
                      <Status appointment={appointment} />
                    </div>
                  </div>
                  <h4>{appointment.customerName || 'Khách hàng'}</h4><p className="reception-card-phone"><Phone size={13} /> {appointment.customerPhone || 'Chưa có SĐT'}</p>
                  <div className="reception-card-time"><Clock3 size={15} /><strong>{appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</strong><span>{formatDate(appointment.appointmentDate)}</span></div>
                  <div className="reception-card-services">{(appointment.items || []).map(item => <div key={item.id}>
                    <strong>{item.serviceName}</strong>
                    {item.staffName ? (
                      <div className="reception-staff-row">
                        <div className="reception-staff-chip">
                          <span className="reception-staff-avatar">{getStaffInitials(item.staffName)}</span>
                          <span>{item.staffName}</span>
                        </div>
                        {item.ticketId && <small style={{ color: '#AD3F5A', fontWeight: 600 }}>Vé liệu trình</small>}
                      </div>
                    ) : (
                      <small className="reception-block reception-muted">Chưa chỉ định KTV{item.ticketId ? ' · Vé liệu trình' : ''}</small>
                    )}
                    {(item.resources || []).length > 0 && <small className="reception-block" style={{ color: '#6A5D64', fontSize: '10px', marginTop: 2 }}>📍 {item.resources.map(resource => resource.name).join(' · ')}</small>}
                  </div>)}</div>
                  {appointment.status === 'COMPLETED' && <div className="reception-card-billing">{appointment.orderId ? 'Đã lập hóa đơn · Mở để đối soát' : 'Chưa lập hóa đơn tại quầy'}<strong>{formatCurrency((appointment.items || []).filter(item => !item.ticketId && item.executionStatus !== 'SKIPPED').reduce((total, item) => total + Number(item.price || 0), 0))}</strong></div>}
                  {appointment.status === 'CONFIRMED' && !appointment.checkedInAt && <small className="reception-card-hint">Đã xác nhận lịch · Chờ khách tới quầy</small>}
                  {appointment.status === 'IN_PROGRESS' && !canFinish(appointment) && <small className="reception-card-hint">Cần kiểm tra dịch vụ đã thực hiện trước khi hoàn tất</small>}
                  {actions(appointment)}
                </article>)}{!appointments.length && !data.appointmentsError && <div className="kanban-empty"><span><CheckCircle2 size={22} /></span><p>Chưa có lịch trong cột này</p></div>}</div></section>;
              })}</div>
            </> : <div className="reception-list"><DataTable columns={columns} data={filtered.slice(page * 20, (page + 1) * 20)} emptyMessage="Không có lịch hẹn phù hợp. Thử đổi ngày hoặc bộ lọc." />{filtered.length > 20 && <div className="reception-pagination"><span>{Math.min(page * 20 + 1, filtered.length)}–{Math.min((page + 1) * 20, filtered.length)} / {filtered.length} lịch</span><Button variant="outline" size="sm" disabled={!page} onClick={() => setPage(previous => previous - 1)}>Trang trước</Button><Button variant="outline" size="sm" disabled={(page + 1) * 20 >= filtered.length} onClick={() => setPage(previous => previous + 1)}>Trang sau</Button></div>}</div>)}
          </section>
        </>
      )}

      <footer className="reception-footer"><Sparkles size={14} /> BeautyShop Atelier Spa <span>Chăm sóc tận tâm, vận hành rõ ràng.</span></footer>
    </main>
    {walkIn && <WalkInDrawer services={data.services} facilities={data.facilities} facilityId={walkIn.facilityId} onClose={() => setWalkIn(null)} onBooked={afterAction} />}
    {assign && <AssignDialog {...assign} onClose={() => setAssign(null)} onAssign={afterAction} />}
    {checkout && <Modal isOpen onClose={() => setCheckout(null)} title="Thu ngân tại quầy" maxWidth="660px" className="reception-dialog"><SpaVisitCheckout key={checkout.id} appointment={checkout} onUpdated={data.refresh} /></Modal>}
    {edit && <Modal isOpen onClose={() => { if (!busy) setEdit(null); }} title={edit.type === 'reschedule' ? 'Đổi lịch hẹn' : 'Ghi nhận khách vắng mặt'} className="reception-dialog"><form className="reception-form" onSubmit={submitEdit}><p><strong>{edit.appointment.customerName}</strong> · {appointmentCode(edit.appointment)}</p>{editError && <div role="alert" className="reception-notice error">{editError}</div>}{edit.type === 'reschedule' && <>
      <div className="reception-reschedule-summary">
        <div><strong>Lịch hiện tại:</strong> {edit.appointment.startTime?.slice(0, 5)}–{edit.appointment.endTime?.slice(0, 5)} · {formatDate(edit.appointment.appointmentDate)} ({rescheduleDuration} phút)</div>
        <div><strong>Dịch vụ:</strong> {(edit.appointment.items || []).map(item => `${item.serviceName}${item.staffName ? ` (${item.staffName})` : ''}`).join(', ')}</div>
      </div>
      <div className="drawer-grid"><Input label="Ngày hẹn mới" type="date" required min={today} value={editForm.date} onChange={event => setEditForm(previous => ({ ...previous, date: event.target.value }))} /><Select label="Giờ hẹn mới" required value={editSlots.includes(editForm.time)?editForm.time:''} onChange={event => setEditForm(previous => ({ ...previous, time: event.target.value }))} options={[{value:'',label:editSlotsLoading?'Đang kiểm tra giờ trống…':'Chọn giờ khả dụng'},...editSlots.map(slot=>({value:slot,label:slot}))]} /></div>
      <div className="reception-time-preset-row">
        <small className="reception-muted">Khung giờ gợi ý:</small>
        <div className="reception-time-presets">
          {editSlots.map(slot => (
            <button
              key={slot}
              type="button"
              className="reception-time-chip"
              aria-pressed={editForm.time === slot}
              onClick={() => setEditForm(previous => ({ ...previous, time: slot }))}
            >
              {slot}
            </button>
          ))}
        </div>
      </div>
      {rescheduleEstimatedEnd && (
        <div className={`reception-preview-time ${rescheduleExceedsClosing ? 'warning' : ''}`}>
          <Clock3 size={14} />
          <span>
            {rescheduleExceedsClosing
              ? 'Khung giờ này chưa khả dụng. Chọn giờ trống theo lịch cơ sở.'
              : `Dự kiến mới: ${editForm.time}–${rescheduleEstimatedEnd} · ${formatDate(editForm.date)}`}
          </span>
        </div>
      )}
    </>}<Input label={edit.type === 'reschedule' ? 'Ghi chú đổi lịch' : 'Lý do khách vắng mặt'} required={edit.type === 'no-show'} maxLength={500} value={editForm.note} onChange={event => setEditForm(previous => ({ ...previous, note: event.target.value }))} /><Button type="submit" loading={busy === 'edit'} disabled={edit.type === 'reschedule' && rescheduleExceedsClosing}>Xác nhận {edit.type === 'reschedule' ? 'đổi lịch' : 'vắng mặt'}</Button></form></Modal>}
    {ticketOpen && <Modal isOpen onClose={() => { if (!ticketLoading) setTicketOpen(false); }} title="Tra cứu vé liệu trình" className="reception-dialog"><form className="reception-form" onSubmit={lookupTickets}><Input label="SĐT khách cần tra cứu vé" type="tel" required value={ticketPhone} onChange={event => setTicketPhone(event.target.value)} /><Button type="submit" loading={ticketLoading}><Search size={16} /> Tra cứu vé của khách</Button></form>{ticketError && <p role="alert" className="reception-notice error">{ticketError}</p>}{ticketResult && <div className="reception-ticket-list"><h4>{ticketResult.customer.fullName}</h4>{ticketResult.tickets.map(ticket => <div key={ticket.id}><strong>{ticket.packageName}</strong><p>Còn {ticket.remainingSessions} buổi · Đã dùng {ticket.usedSessions || 0} · Đang giữ {ticket.reservedSessions || 0}</p><small>{ticket.status === 'ACTIVE' ? 'Đang hoạt động' : ticket.status === 'EXPIRED' ? 'Đã hết hạn' : 'Không còn khả dụng'}{ticket.expiryDate ? ` · Hạn ${formatDate(ticket.expiryDate)}` : ' · Không giới hạn thời gian'}</small></div>)}{!ticketResult.tickets.length && <p>Khách chưa có vé liệu trình.</p>}</div>}</Modal>}
  </div>;
};
