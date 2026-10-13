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
        catch (err) { onAssign('Đã phân ca và xác nhận lịch. ' + spaErrorMessage(err, 'Chưa check-in được khách. Vui lòng kiểm tra lịch và thử lại.'), 'warning'); onClose(); return; }
      }
      onAssign(checkIn ? 'Đã phân ca và check-in khách.' : 'Đã phân ca KTV và xác nhận lịch.'); onClose();
    } catch (err) { setError(spaErrorMessage(err, 'Chưa phân ca được. Vui lòng kiểm tra lịch làm việc của KTV.')); }
    finally { submitting.current = false; setBusy(false); }
  };
  return <Modal isOpen onClose={() => { if (!busy) onClose(); }} title={checkIn ? 'Phân ca & check-in' : 'Phân ca KTV nhanh'} className="reception-dialog">
    <form className="reception-form" onSubmit={submit}>
      <p><strong>{appointment.customerName}</strong> · {appointmentCode(appointment)}<br /><small className="reception-muted">{formatDate(appointment.appointmentDate)} · {appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</small></p>
      {error && <div role="alert" className="reception-notice error">{error}</div>}
      {(appointment.items || []).map(item => <Select key={item.id} label={item.serviceName} required disabled={busy} value={assignments[item.id] || ''}
        onChange={event => setAssignments(previous => ({ ...previous, [item.id]: event.target.value }))} options={[
          { value: '', label: choices[item.id] ? 'Chọn KTV đủ chuyên môn' : 'Đang tải KTV…' }, ...(choices[item.id] || []).map(staff => ({ value: String(staff.id), label: `${staff.fullName}${staff.specialty ? ' · ' + staff.specialty : ''}` })),
        ]} />)}
      <p className="reception-muted">Hệ thống kiểm tra ca làm việc và lịch trùng khi xác nhận phân ca.</p>
      <Button type="submit" loading={busy} disabled={(appointment.items || []).some(item => !choices[item.id] || !assignments[item.id])}>{checkIn ? 'Xác nhận & check-in' : 'Xác nhận phân ca'}</Button>
    </form>
  </Modal>;
};

export const SpaReceptionPage = () => {
  const { user, logout } = useAuth();
  const data = useReceptionData();
  const [now, setNow] = useState(() => new Date());
  const [view, setView] = useState(() => { try { return localStorage.getItem('spa_reception_view') === 'list' ? 'list' : 'kanban'; } catch { return 'kanban'; } });
  const [date, setDate] = useState('');
  const [status, setStatus] = useState('ALL');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [walkIn, setWalkIn] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const [finish, setFinish] = useState(null);
  const [assign, setAssign] = useState(null);
  const [edit, setEdit] = useState(null);
  const [editForm, setEditForm] = useState({ date: '', time: '', note: '' });
  const [editError, setEditError] = useState('');
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
  const changeView = next => { setView(next); try { localStorage.setItem('spa_reception_view', next); } catch { /* View preference is optional. */ } };
  const afterAction = (message, tone = 'success') => { setNotice({ message, tone }); data.refresh(); };
  const today = getVietnamDateString(now);
  const currentFacilities = useMemo(() => data.facilities.map(facility => ({ ...facility,
    live: facilityState(facility, data.appointments, data.blocks[facility.id], now,
      Boolean(data.updatedAt) && now.getTime() - data.updatedAt < 90000 && data.blocks[facility.id] !== undefined),
  })), [data.facilities, data.appointments, data.blocks, data.updatedAt, now]);
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi');
    return data.appointments.filter(appointment => (!date || appointment.appointmentDate === date) && (status === 'ALL' || appointment.status === status)
      && (!term || [appointment.customerName, appointment.customerPhone, appointmentCode(appointment), ...((appointment.items || []).map(item => item.serviceName))].some(value => String(value || '').toLocaleLowerCase('vi').includes(term))))
      .sort((a, b) => localInstant(a.appointmentDate, a.startTime) - localInstant(b.appointmentDate, b.startTime) || a.id - b.id);
  }, [data.appointments, date, status, search]);
  const todayAppointments = data.appointments.filter(appointment => appointment.appointmentDate === today);
  const exceptions = filtered.filter(appointment => ['CANCELLED', 'NO_SHOW'].includes(appointment.status)).length;
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
    status: 'COMPLETED', notes: 'Lễ tân xác nhận khách đã hoàn tất ca chăm sóc.',
  }), 'Đã hoàn tất ca. Có thể đối soát và thu ngân.', response => { setFinish(null); setCheckout(unwrap(response) || { ...appointment, status: 'COMPLETED' }); });
  const openEdit = (appointment, type) => { setEdit({ appointment, type }); setEditForm({ date: appointment.appointmentDate, time: appointment.startTime?.slice(0, 5) || '09:00', note: '' }); setEditError(''); };
  const submitEdit = async event => {
    event.preventDefault();
    if (actionLock.current) return;
    if (edit.type === 'no-show' && !editForm.note.trim()) { setEditError('Vui lòng nhập lý do khách vắng mặt.'); return; }
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
    {appointment.status === 'PENDING' && <Button size="sm" variant="outline" disabled={Boolean(busy)} onClick={() => setAssign({ appointment, checkIn: false })}>Phân ca KTV</Button>}
    {appointment.status === 'CONFIRMED' && appointment.checkedInAt && <Button size="sm" disabled={Boolean(busy) || !canCheckInToday(appointment, now) || localInstant(appointment.appointmentDate, appointment.startTime) - 3600000 > now.getTime()} loading={busy === `start-${appointment.id}`}
      onClick={() => run(`start-${appointment.id}`, () => apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointment.id), { status: 'IN_PROGRESS', notes: 'Lễ tân bắt đầu ca chăm sóc.' }), 'Đã bắt đầu ca chăm sóc.')}><ArrowRight size={15} /> Bắt đầu ca</Button>}
    {appointment.status === 'IN_PROGRESS' && <Button size="sm" disabled={Boolean(busy)} loading={busy === `finish-${appointment.id}`} onClick={() => canFinish(appointment) ? complete(appointment) : setFinish(appointment)}>Hoàn tất & thu ngân</Button>}
    {appointment.status === 'COMPLETED' && <Button size="sm" onClick={() => setCheckout(appointment)}>Mở thu ngân <ArrowRight size={15} /></Button>}
    {['PENDING', 'CONFIRMED'].includes(appointment.status) && <details className="reception-more"><summary aria-label={`Thao tác khác cho ${appointmentCode(appointment)}`}><MoreHorizontal size={18} /></summary><div>
      <button type="button" onClick={() => openEdit(appointment, 'reschedule')}>Đổi lịch hẹn</button>
      {appointment.status === 'CONFIRMED' && <button type="button" onClick={() => openEdit(appointment, 'no-show')}>Ghi nhận vắng mặt</button>}
    </div></details>}
  </div>;
  const columns = [
    { key: 'customer', header: 'Khách hàng', render: appointment => <div className="reception-table-customer"><strong>{appointment.customerName || 'Khách hàng'}</strong><small>{appointment.customerPhone || 'Chưa có SĐT'} · {appointmentCode(appointment)}</small></div> },
    { key: 'time', header: 'Lịch hẹn', render: appointment => <><strong>{appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</strong><small className="reception-block reception-muted">{formatDate(appointment.appointmentDate)}</small></> },
    { key: 'services', header: 'Dịch vụ / KTV', render: appointment => (appointment.items || []).map(item => <div key={item.id}>{item.serviceName}<small className="reception-block reception-muted">{item.staffName || 'Chờ phân ca'}{item.ticketId ? ' · Vé liệu trình' : ''}</small></div>) },
    { key: 'status', header: 'Trạng thái', render: appointment => <Status appointment={appointment} /> },
    { key: 'actions', header: 'Thao tác', render: actions },
  ];
  return <div className="spa-reception-page">
    <header className="reception-header"><div className="reception-brand"><span><Sparkles size={22} /></span><div><strong>BeautyShop <em>Atelier Spa</em></strong><small>KHÔNG GIAN CHĂM SÓC · QUẦY LỄ TÂN</small></div></div>
      <div className="reception-user"><span><strong>{user?.fullName || user?.username || 'Lễ tân'}</strong><small>{spaTime(now)} · {formatDate(today)}</small></span><a href="#/staff">Ca KTV</a>{user?.roles?.includes('ROLE_ADMIN') && <a href="#/admin/spa">Quản trị Spa</a>}<Button variant="ghost" size="sm" onClick={logout} aria-label="Đăng xuất"><LogOut size={17} /></Button></div>
    </header>
    <main className="reception-main">
      <div className="reception-title-row"><div><span className="reception-eyebrow">ĐIỀU PHỐI & TIẾP ĐÓN</span><h1>Quầy lễ tân Spa</h1><p>Mỗi lượt đón khách, một trải nghiệm chăm sóc trọn vẹn.</p></div><div className="reception-top-actions"><Button variant="outline" onClick={() => { setTicketOpen(true); setTicketError(''); setTicketResult(null); }}><Ticket size={17} /> Tra cứu vé</Button><Button onClick={() => setWalkIn({ facilityId: '' })} disabled={!data.services.length}><Plus size={18} /> Đón khách vãng lai</Button></div></div>
      {notice && <div className={`reception-notice ${notice.tone}`} role={notice.tone === 'error' || notice.tone === 'warning' ? 'alert' : 'status'}>{notice.message}<button type="button" onClick={() => setNotice(null)} aria-label="Đóng thông báo">×</button></div>}
      {data.error && <div className="reception-notice error" role="alert">{data.error}</div>}
      <section className="reception-facility-panel" aria-label="Trạng thái phòng và giường">
        <div className="reception-section-heading"><div><h2><BedDouble size={19} /> Phòng & giường trực tiếp <span className={`live-dot ${data.updatedAt ? '' : 'offline'}`} /></h2><p>{data.updatedAt ? `Cập nhật lúc ${spaTime(new Date(data.updatedAt))} · Tự làm mới mỗi 30 giây` : 'Đang chờ dữ liệu phòng / giường'}</p></div><div className="facility-legend"><span className="free">Trống</span><span className="busy">Có khách / giữ chỗ</span><span className="paused">Tạm dừng</span><Button variant="ghost" size="sm" loading={data.refreshing} onClick={data.refresh} aria-label="Làm mới dữ liệu quầy"><RefreshCw size={16} /></Button></div></div>
        <div className="facility-strip">{currentFacilities.map(facility => <button type="button" key={facility.id} className={`facility-tile ${facility.live.state}`} disabled={facility.live.state !== 'free' || !data.services.length} onClick={() => setWalkIn({ facilityId: String(facility.id) })} aria-label={`${facility.name}: ${facility.live.label}. ${facility.live.detail}`}>
          <div><span className="facility-icon"><BedDouble size={21} /></span><span className="facility-state-dot" /></div><strong>{facility.name}</strong><span className="facility-label">{facility.live.label}</span><small>{facility.live.detail}</small>{facility.live.state === 'free' && <span className="facility-cta">Đón khách tại đây <Plus size={13} /></span>}
        </button>)}</div>
        {!currentFacilities.length && <p className="reception-muted">{data.refreshing ? 'Đang tải phòng / giường…' : 'Chưa có phòng / giường được cấu hình.'}</p>}
      </section>
      <section className="reception-stats" aria-label="Tổng quan hôm nay">{[
        { label: 'Lịch hẹn hôm nay', value: todayAppointments.length, icon: CalendarDays, tone: 'rose' },
        { label: 'Chờ tiếp đón', value: todayAppointments.filter(appointment => ['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt).length, icon: Users, tone: 'gold' },
        { label: 'Đang làm dịch vụ', value: todayAppointments.filter(appointment => appointment.status === 'IN_PROGRESS').length, icon: Sparkles, tone: 'blue' },
        { label: 'Hoàn tất dịch vụ', value: todayAppointments.filter(appointment => appointment.status === 'COMPLETED').length, icon: CheckCircle2, tone: 'green' },
      ].map(stat => <div key={stat.label} className={`reception-stat ${stat.tone}`}><span><stat.icon size={20} /></span><div><small>{stat.label}</small><strong>{stat.value}</strong></div></div>)}</section>
      <section className="reception-queue" aria-label="Danh sách tiếp đón">
        <div className="reception-section-heading"><div><h2>Luồng tiếp đón <span className="reception-count">{filtered.length}</span></h2><p>{date ? formatDate(date) : 'Tất cả ngày hẹn'} · Theo dõi từ tiếp đón đến thu ngân</p></div><div className="reception-view-switch" role="group" aria-label="Chế độ hiển thị"><button type="button" aria-pressed={view === 'kanban'} onClick={() => changeView('kanban')}><LayoutGrid size={16} /> Kanban</button><button type="button" aria-pressed={view === 'list'} onClick={() => changeView('list')}><List size={16} /> Danh sách</button></div></div>
        <div className="reception-filter-bar"><div className="reception-search"><Search size={17} /><Input aria-label="Tìm tên, SĐT hoặc mã lịch hẹn" placeholder="Tìm tên, SĐT, mã lịch…" value={search} onChange={event => setSearch(event.target.value)} /></div><Input aria-label="Lọc ngày hẹn" type="date" value={date} onChange={event => setDate(event.target.value)} /><div className="reception-date-shortcuts"><button type="button" aria-pressed={date === today} onClick={() => setDate(today)}>Hôm nay</button><button type="button" aria-pressed={!date} onClick={() => setDate('')}>Tất cả ngày</button></div><Select aria-label="Lọc trạng thái" value={status} onChange={event => setStatus(event.target.value)} options={[{ value: 'ALL', label: 'Tất cả trạng thái' }, ...Object.entries(SPA_STATUS).map(([value, state]) => ({ value, label: state.label }))]} /></div>
        {data.refreshing && !data.updatedAt && <p className="reception-muted" role="status">Đang tải lịch hẹn…</p>}
        {view === 'kanban' ? <>
          {exceptions > 0 && <div className="reception-exceptions">Có {exceptions} lịch đã hủy / vắng mặt trong bộ lọc. <button type="button" onClick={() => changeView('list')}>Xem trong danh sách</button></div>}
          <div className="reception-kanban">{KANBAN_COLUMNS.map(column => {
            const appointments = filtered.filter(appointment => appointment.status === column.id);
            return <section className={`kanban-column ${column.tone}`} key={column.id} aria-label={column.title}><header><div><h3>{column.title} <span>{appointments.length}</span></h3><p>{column.caption}</p></div></header><div className="kanban-cards">{appointments.map(appointment => <article key={appointment.id} className="reception-appointment-card" data-appointment-id={appointment.id}>
              <div className="reception-card-top"><span>{appointmentCode(appointment)}</span><Status appointment={appointment} /></div><h4>{appointment.customerName || 'Khách hàng'}</h4><p className="reception-card-phone"><Phone size={13} /> {appointment.customerPhone || 'Chưa có SĐT'}</p>
              <div className="reception-card-time"><Clock3 size={15} /><strong>{appointment.startTime?.slice(0, 5)}–{appointment.endTime?.slice(0, 5)}</strong><span>{formatDate(appointment.appointmentDate)}</span></div>
              <div className="reception-card-services">{(appointment.items || []).map(item => <div key={item.id}><strong>{item.serviceName}</strong><small>{item.staffName || 'Chờ phân ca'}{item.ticketId ? ' · Vé liệu trình' : ''}</small>{(item.resources || []).length > 0 && <small>{item.resources.map(resource => resource.name).join(' · ')}</small>}</div>)}</div>
              {appointment.status === 'COMPLETED' && <div className="reception-card-billing">{appointment.orderId ? 'Đã lập hóa đơn · Mở để đối soát' : 'Chưa lập hóa đơn tại quầy'}<strong>{formatCurrency((appointment.items || []).filter(item => !item.ticketId && item.executionStatus !== 'SKIPPED').reduce((total, item) => total + Number(item.price || 0), 0))}</strong></div>}
              {appointment.status === 'CONFIRMED' && !appointment.checkedInAt && <small className="reception-card-hint">Đã xác nhận lịch · Chờ khách tới quầy</small>}
              {appointment.status === 'IN_PROGRESS' && !canFinish(appointment) && <small className="reception-card-hint">Cần kiểm tra dịch vụ đã thực hiện trước khi hoàn tất</small>}
              {actions(appointment)}
            </article>)}{!appointments.length && <div className="kanban-empty"><span><CheckCircle2 size={22} /></span><p>Chưa có lịch trong cột này</p></div>}</div></section>;
          })}</div>
        </> : <div className="reception-list"><DataTable columns={columns} data={filtered.slice(page * 20, (page + 1) * 20)} loading={data.refreshing && !data.updatedAt} emptyMessage="Không có lịch hẹn phù hợp. Thử đổi ngày hoặc bộ lọc." />{filtered.length > 20 && <div className="reception-pagination"><span>{Math.min(page * 20 + 1, filtered.length)}–{Math.min((page + 1) * 20, filtered.length)} / {filtered.length} lịch</span><Button variant="outline" size="sm" disabled={!page} onClick={() => setPage(previous => previous - 1)}>Trang trước</Button><Button variant="outline" size="sm" disabled={(page + 1) * 20 >= filtered.length} onClick={() => setPage(previous => previous + 1)}>Trang sau</Button></div>}</div>}
      </section>
      <footer className="reception-footer"><Sparkles size={14} /> BeautyShop Atelier Spa <span>Chăm sóc tận tâm, vận hành rõ ràng.</span></footer>
    </main>
    {walkIn && <WalkInDrawer services={data.services} facilities={data.facilities} facilityId={walkIn.facilityId} onClose={() => setWalkIn(null)} onBooked={afterAction} />}
    {assign && <AssignDialog {...assign} onClose={() => setAssign(null)} onAssign={afterAction} />}
    {finish && <Modal isOpen onClose={() => { if (!busy) setFinish(null); }} title="Xác nhận hoàn tất ca chăm sóc" className="reception-dialog"><div className="reception-form"><p><strong>{finish.customerName}</strong> · {appointmentCode(finish)}</p><p>Một số dịch vụ chưa có kết quả cuối cùng. Khi xác nhận hoàn tất, hệ thống ghi nhận các dịch vụ còn lại đã thực hiện và đối soát lượt vé tương ứng.</p><ul>{(finish.items || []).filter(item => !['PERFORMED', 'SKIPPED', 'LEGACY_FINALIZED'].includes(item.executionStatus)).map(item => <li key={item.id}>{item.serviceName}{item.ticketId ? ' · Sử dụng vé liệu trình' : ''}</li>)}</ul><p className="reception-muted">Nếu khách chưa làm đủ dịch vụ, mở ca KTV để ghi nhận đúng kết quả từng dịch vụ trước.</p><a href="#/staff" className="reception-staff-link">Mở ca KTV <ArrowRight size={14} /></a><Button loading={busy === `finish-${finish.id}`} onClick={() => complete(finish)}>Xác nhận đã thực hiện đủ & hoàn tất</Button></div></Modal>}
    {checkout && <Modal isOpen onClose={() => setCheckout(null)} title="Thu ngân tại quầy" maxWidth="660px" className="reception-dialog"><SpaVisitCheckout key={checkout.id} appointment={checkout} onUpdated={data.refresh} /></Modal>}
    {edit && <Modal isOpen onClose={() => { if (!busy) setEdit(null); }} title={edit.type === 'reschedule' ? 'Đổi lịch hẹn' : 'Ghi nhận khách vắng mặt'} className="reception-dialog"><form className="reception-form" onSubmit={submitEdit}><p><strong>{edit.appointment.customerName}</strong> · {appointmentCode(edit.appointment)}</p>{editError && <div role="alert" className="reception-notice error">{editError}</div>}{edit.type === 'reschedule' && <div className="drawer-grid"><Input label="Ngày hẹn mới" type="date" required min={today} value={editForm.date} onChange={event => setEditForm(previous => ({ ...previous, date: event.target.value }))} /><Input label="Giờ hẹn mới" type="time" required min="08:00" max="19:30" step={1800} value={editForm.time} onChange={event => setEditForm(previous => ({ ...previous, time: event.target.value }))} /></div>}<Input label={edit.type === 'reschedule' ? 'Ghi chú đổi lịch' : 'Lý do khách vắng mặt'} required={edit.type === 'no-show'} maxLength={500} value={editForm.note} onChange={event => setEditForm(previous => ({ ...previous, note: event.target.value }))} /><Button type="submit" loading={busy === 'edit'}>Xác nhận {edit.type === 'reschedule' ? 'đổi lịch' : 'vắng mặt'}</Button></form></Modal>}
    {ticketOpen && <Modal isOpen onClose={() => { if (!ticketLoading) setTicketOpen(false); }} title="Tra cứu vé liệu trình" className="reception-dialog"><form className="reception-form" onSubmit={lookupTickets}><Input label="SĐT khách cần tra cứu vé" type="tel" required value={ticketPhone} onChange={event => setTicketPhone(event.target.value)} /><Button type="submit" loading={ticketLoading}><Search size={16} /> Tra cứu vé của khách</Button></form>{ticketError && <p role="alert" className="reception-notice error">{ticketError}</p>}{ticketResult && <div className="reception-ticket-list"><h4>{ticketResult.customer.fullName}</h4>{ticketResult.tickets.map(ticket => <div key={ticket.id}><strong>{ticket.packageName}</strong><p>Còn {ticket.remainingSessions} buổi · Đã dùng {ticket.usedSessions || 0} · Đang giữ {ticket.reservedSessions || 0}</p><small>{ticket.status === 'ACTIVE' ? 'Đang hoạt động' : ticket.status === 'EXPIRED' ? 'Đã hết hạn' : 'Không còn khả dụng'}{ticket.expiryDate ? ` · Hạn ${formatDate(ticket.expiryDate)}` : ' · Không giới hạn thời gian'}</small></div>)}{!ticketResult.tickets.length && <p>Khách chưa có vé liệu trình.</p>}</div>}</Modal>}
  </div>;
};
