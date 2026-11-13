import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  User, ShoppingBag, Calendar, Clock, Mail, Phone, Heart, MapPin, Bell,
  Shield, LogOut, LayoutDashboard, ExternalLink, RefreshCw, Ticket, ArrowLeft,
} from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import {
  AccountSettings, AddressBook, SkinProfile, NotificationInbox, WishlistPage,
  Pagination, AccountLoading, message, unwrap,
} from '../components/account/AccountTools';
import { OrderWorkspace, OrderDetailDialog } from '../components/account/OrderWorkspace';
import { AppointmentTools } from '../components/account/AppointmentTools';
import { TreatmentAppointmentDialog } from '../../features/treatments/TreatmentAppointmentDialog';
import { TicketDetails } from '../../features/spa/TicketDetails';
import { ticketDestination } from '../../features/spa/benefitModel';
import { buildGoogleCalendarUrl, formatCurrency, getOrderStatusBadge, getPaymentStatusBadge, getTicketStatusText, resolveMediaUrl } from '../../shared/utils/formatters';
import { availableTicket, knownCount, pageWindow, upcomingAppointments } from '../utils/profileModel';

const APT_PAGE_SIZE = 5;
const TICKET_PAGE_SIZE = 6;
const TABS = [
  ['overview', 'Tổng quan', LayoutDashboard],
  ['orders', 'Lịch sử giao dịch', ShoppingBag],
  ['appointments', 'Lịch hẹn Spa', Calendar],
  ['tickets', 'Ví vé Spa', Ticket],
  ['skin', 'Hồ sơ da', User],
  ['addresses', 'Sổ địa chỉ', MapPin],
  ['wishlist', 'Yêu thích', Heart],
  ['notifications', 'Thông báo & Ưu đãi', Bell],
  ['settings', 'Thông tin & Bảo mật', Shield],
];
const TITLES = Object.fromEntries(TABS.map(([id, title]) => [id, title]));
const normalizeTab = tab => Object.hasOwn(TITLES, tab) ? tab : 'overview';
const tierNames = { MEMBER: 'Hạng Thành Viên', SILVER: 'Hạng Bạc', GOLD: 'Hạng Vàng', PLATINUM: 'Hạng Bạch Kim' };
const nextTiers = { MEMBER: [1000, 'Hạng Bạc'], SILVER: [5000, 'Hạng Vàng'], GOLD: [10000, 'Hạng Bạch Kim'] };
const resourceNames = { profile: 'Hồ sơ thành viên', orders: 'Đơn mua sắm', appointments: 'Lịch hẹn Spa', tickets: 'Ví vé Spa', statistics: 'Thống kê giao dịch' };
const blankData = () => ({ profile: null, orders: null, appointments: null, tickets: null, statistics: null });
const dateLabel = value => {
  if (!value || !Number.isFinite(Date.parse(value))) return 'Chưa có ngày';
  return new Date(String(value).length === 10 ? `${value}T12:00:00+07:00` : value)
    .toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
};
const timeLabel = value => value ? String(value).slice(0, 5) : 'Chưa có giờ';
const staffLabel = name => !name || name === 'No preference' ? 'Spa sắp xếp chuyên viên' : name;
const appointmentLabel = appointment => appointment.status === 'CONFIRMED' && appointment.checkedInAt ? 'Đã check-in'
  : ({ PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', CHECKED_IN: 'Đã check-in', IN_PROGRESS: 'Đang thực hiện', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy hẹn', NO_SHOW: 'Vắng hẹn' })[appointment.status] || appointment.status || 'Chưa có trạng thái';
const listData = response => {
  const data = unwrap(response);
  if (!Array.isArray(data)) throw new Error('Chưa đọc được danh sách từ máy chủ.');
  return data;
};
const calendarUrl = appointment => buildGoogleCalendarUrl({
  title: `Lịch hẹn Spa: ${appointment.items?.map(item => item.serviceName).filter(Boolean).join(', ') || 'Chăm sóc da'}`,
  date: appointment.appointmentDate, startTime: appointment.startTime,
  endTime: appointment.endTime, details: `Mã lịch hẹn: SPA-APT-#${appointment.id}`,
});
const StateNotice = ({ children, error = false }) => <div className={`profile-state-notice${error ? ' is-error' : ''}`} role={error ? 'alert' : undefined}>{children}</div>;
const CardBody = ({ data, error, children }) => data == null
  ? error ? <StateNotice error>Chưa tải được dữ liệu. Dùng nút Thử lại phía trên.</StateNotice> : <AccountLoading count={1} label="Đang tải thông tin thẻ…" />
  : children;

export const CustomerProfilePage = props => {
  const { user } = useAuth();
  return <ProfileContent key={user?.id ?? 'guest'} {...props} />;
};

function ProfileContent({ onNavigate, initialTab = 'overview' }) {
  const { user, isAuthenticated, logout } = useAuth();
  const [activeTab, setActiveTab] = useState(() => normalizeTab(initialTab));
  const [data, setData] = useState(blankData);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshTick, setRefreshTick] = useState(0);
  const [avatarFailed, setAvatarFailed] = useState(false);
  const [aptPage, setAptPage] = useState(0);
  const [ticketPage, setTicketPage] = useState(0);
  const [cancellingId, setCancellingId] = useState(null);
  const [actionError, setActionError] = useState('');
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [selectedAppointment, setSelectedAppointment] = useState(null);
  const [now, setNow] = useState(Date.now);
  const request = useRef(null);
  const actionLock = useRef(false);
  const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
  const requestedAppointment = params.get('appointment');

  const loadData = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setErrors({});
    const jobs = [
      ['profile', ENDPOINTS.AUTH.PROFILE, response => {
        const profile = unwrap(response);
        if (!profile || String(profile.id) !== String(user?.id)) throw new Error('Chưa xác minh được hồ sơ tài khoản.');
        return profile;
      }],
      ['tickets', ENDPOINTS.SPA.MY_TICKETS, listData],
      ['appointments', ENDPOINTS.SPA.MY_APPOINTMENTS, listData],
      ['orders', `${ENDPOINTS.ORDERS.MY_ORDERS}?type=PRODUCT&size=1&sort=createdAt,desc`, response => {
        const page = unwrap(response);
        if (!Array.isArray(page?.content) || knownCount(page.totalElements) == null) throw new Error('Chưa đọc được lịch sử đơn mua sắm.');
        return page;
      }],
      ['statistics', '/api/v1/users/order-statistics', response => {
        const statistics = unwrap(response);
        if (knownCount(statistics?.totalOrders) == null) throw new Error('Chưa đọc được thống kê giao dịch.');
        return statistics;
      }],
    ];
    await Promise.allSettled(jobs.map(async ([key, url, parse]) => {
      try {
        const value = parse(await apiClient.get(url, { signal: controller.signal }));
        if (!controller.signal.aborted) setData(previous => ({ ...previous, [key]: value }));
      } catch (error) {
        if (!controller.signal.aborted) setErrors(previous => ({ ...previous, [key]: message(error) }));
      }
    }));
    if (!controller.signal.aborted) setLoading(false);
  }, [user?.id]);

  useEffect(() => {
    if (isAuthenticated) loadData();
    return () => request.current?.abort();
  }, [isAuthenticated, loadData]);
  useEffect(() => { setActiveTab(normalizeTab(initialTab)); }, [initialTab]);
  useEffect(() => { setAvatarFailed(false); }, [data.profile?.avatarUrl]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!requestedAppointment || !data.appointments) return;
    const index = data.appointments.findIndex(row => String(row.id) === requestedAppointment);
    if (index >= 0) setAptPage(Math.floor(index / APT_PAGE_SIZE));
  }, [requestedAppointment, data.appointments]);

  const navigateTab = tab => {
    setActiveTab(tab);
    onNavigate(`profile?tab=${tab}`, { scroll: false });
  };
  const refresh = () => { setRefreshTick(tick => tick + 1); loadData(); };
  const cancelAppointment = async id => {
    if (actionLock.current || !window.confirm('Hủy lịch hẹn này? Phí hủy và hoàn cọc áp dụng theo chính sách đã xác nhận khi đặt.')) return;
    actionLock.current = true;
    setCancellingId(id); setActionError('');
    try { await apiClient.put(`/api/v1/appointments/${id}/cancel`); await loadData(); }
    catch (error) { setActionError(message(error)); }
    finally { actionLock.current = false; setCancellingId(null); }
  };
  const handleLogout = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn đăng xuất tài khoản?')) return;
    await logout(); onNavigate('');
  };

  if (!isAuthenticated) return <div className="customer-profile-page profile-login-prompt">
    <h1>Vui lòng đăng nhập</h1><p>Đăng nhập để xem lịch hẹn, vé Spa và đơn mua sắm của bạn.</p>
    <button type="button" className="btn-editorial-primary" onClick={() => onNavigate('login')}>Đăng nhập</button>
    <a href="#/">Về cửa hàng</a>
  </div>;

  const profile = data.profile || user || {};
  const displayName = profile.fullName || profile.username || 'Khách hàng';
  const points = knownCount(profile.loyaltyPoints);
  const tier = profile.membershipTier;
  const nextTier = nextTiers[tier];
  const appointments = data.appointments || [];
  const upcoming = upcomingAppointments(appointments, now);
  const nextAppointment = upcoming[0];
  const tickets = data.tickets || [];
  const activeTickets = tickets.filter(ticket => availableTicket(ticket, now));
  const firstTicket = activeTickets[0];
  const latestOrder = data.orders?.content[0];
  const orderCount = knownCount(data.orders?.totalElements);
  const aptWindow = pageWindow(appointments, aptPage, APT_PAGE_SIZE);
  const ticketWindow = pageWindow(tickets, ticketPage, TICKET_PAGE_SIZE);
  const badges = { orders: orderCount, appointments: data.appointments ? upcoming.length : null, tickets: data.tickets ? activeTickets.length : null };
  const title = TITLES[activeTab];
  const listTab = activeTab === 'appointments' ? 'appointments' : activeTab === 'tickets' ? 'tickets' : null;

  return <div className="customer-profile-page">
    <div className="profile-shell">
      <header className="profile-toolbar">
        <div>
          <nav className="profile-breadcrumb" aria-label="Đường dẫn tài khoản"><a href="#/">Trang chủ</a><span aria-hidden="true">/</span><span>Tài khoản của tôi</span></nav>
          <h1>Không gian cá nhân</h1>
          <p>Lịch hẹn, mua sắm và quyền lợi thành viên trong cùng một nơi.</p>
        </div>
        <div className="profile-actions">
          <button type="button" onClick={refresh} disabled={loading} className="btn-editorial-outline"><RefreshCw size={16} className={loading ? 'animate-spin' : ''} />{loading ? 'Đang cập nhật…' : 'Làm mới dữ liệu'}</button>
          <button type="button" className="profile-text-button profile-danger" onClick={handleLogout}><LogOut size={16} />Đăng xuất</button>
        </div>
      </header>

      <div className="profile-unified-frame">
        <div className="profile-sync-status" role="status" aria-live="polite">{loading ? 'Đang đồng bộ dữ liệu. Các thông tin đã tải vẫn được giữ.' : Object.keys(errors).length ? 'Một số dữ liệu chưa được cập nhật.' : 'Thông tin đã được cập nhật.'}</div>
        {Object.keys(errors).length > 0 && <div className="profile-load-error" role="alert">
          <strong>Chưa tải đủ thông tin</strong>
          <ul>{Object.entries(errors).map(([key, text]) => <li key={key}>{resourceNames[key]}: {text}</li>)}</ul>
          <button type="button" onClick={refresh} disabled={loading} className="btn-editorial-outline">Thử lại</button>
        </div>}

        <section className="profile-bento-panel" aria-label="Tóm tắt tài khoản">
          <div className="profile-bento-modular-grid">
            <article className="bento-modular-card">
              <div className="bento-card-header"><h2><Shield size={17} />Thành viên</h2><span className={`profile-tier tier-${String(tier || 'unknown').toLowerCase()}`}>{tierNames[tier] || 'Chưa có hạng'}</span></div>
              <div className="bento-card-body">
                <div className="bento-vip-identity"><div className="bento-vip-avatar">{profile.avatarUrl && !avatarFailed ? <img src={resolveMediaUrl(profile.avatarUrl)} alt="" onError={() => setAvatarFailed(true)} /> : displayName.charAt(0).toUpperCase()}</div><div className="bento-vip-meta"><h3>{displayName}</h3><span>@{profile.username || 'Chưa có tên tài khoản'}</span></div></div>
                <CardBody data={data.profile} error={errors.profile}>
                  <dl className="profile-card-facts"><div><dt><Mail size={14} />Email</dt><dd>{profile.email || 'Chưa cập nhật'}</dd></div><div><dt><Phone size={14} />Điện thoại</dt><dd>{profile.phone || 'Chưa cập nhật'}</dd></div><div><dt>Điểm tích lũy</dt><dd>{points == null ? 'Chưa có dữ liệu' : `${points.toLocaleString('vi-VN')} điểm`}</dd></div></dl>
                  {nextTier && points != null && <div><progress className="profile-meter" max={nextTier[0]} value={Math.min(points, nextTier[0])} aria-label={`Tiến độ lên ${nextTier[1]}`} /><p className="profile-card-note">Còn {Math.max(0, nextTier[0] - points).toLocaleString('vi-VN')} điểm để lên {nextTier[1]}.</p></div>}
                  {tier === 'PLATINUM' && <p className="profile-card-note">Bạn đang ở hạng thành viên cao nhất.</p>}
                </CardBody>
              </div>
              <div className="bento-card-footer"><button type="button" className="btn-editorial-outline" onClick={() => navigateTab('settings')}>Thông tin & Bảo mật</button></div>
            </article>

            <article className="bento-modular-card">
              <div className="bento-card-header"><h2><ShoppingBag size={17} />Đơn mua gần nhất</h2>{latestOrder && <span className="profile-status-badge">{getOrderStatusBadge(latestOrder.status).text}</span>}</div>
              <div className="bento-card-body"><CardBody data={data.orders} error={errors.orders}>
                {latestOrder ? <><p className="profile-card-reference">{latestOrder.orderNumber || `Đơn #${latestOrder.id}`}</p><dl className="profile-card-facts"><div><dt>Ngày đặt</dt><dd>{dateLabel(latestOrder.createdAt)}</dd></div><div><dt>Số sản phẩm</dt><dd>{latestOrder.items?.reduce((sum, item) => sum + Number(item.quantity || 0), 0) ?? 'Chưa có dữ liệu'}</dd></div><div><dt>Thanh toán</dt><dd>{getPaymentStatusBadge(latestOrder.paymentStatus).text}</dd></div><div><dt>Tổng đơn</dt><dd className="profile-card-amount">{latestOrder.totalAmount == null ? 'Chưa có dữ liệu' : formatCurrency(latestOrder.totalAmount)}</dd></div></dl><p className="profile-card-note">{latestOrder.carrierName ? `Vận chuyển: ${latestOrder.carrierName}${latestOrder.trackingCode ? ` · ${latestOrder.trackingCode}` : ''}` : 'Thông tin giao nhận được cập nhật trong chi tiết đơn.'}</p></>
                  : <StateNotice>Bạn chưa có đơn mua sản phẩm. Đơn gói và hóa đơn Spa nằm trong lịch sử dịch vụ.</StateNotice>}
              </CardBody></div>
              <div className="bento-card-footer">{latestOrder ? <button type="button" className="btn-editorial-primary" onClick={() => setSelectedOrder(latestOrder.id)}>Xem chi tiết đơn</button> : <button type="button" className="btn-editorial-outline" onClick={() => onNavigate('products')}>Khám phá mỹ phẩm</button>}</div>
            </article>

            <article className="bento-modular-card">
              <div className="bento-card-header"><h2><Calendar size={17} />Lịch hẹn sắp tới</h2>{nextAppointment && <span className="profile-status-badge">{appointmentLabel(nextAppointment)}</span>}</div>
              <div className="bento-card-body"><CardBody data={data.appointments} error={errors.appointments}>
                {nextAppointment ? <><p className="profile-card-reference">{dateLabel(nextAppointment.appointmentDate)} · {timeLabel(nextAppointment.startTime)}</p><ul className="profile-summary-services">{(nextAppointment.items || []).map((item, index) => <li key={item.id || index}><strong>{item.serviceName || `Dịch vụ #${item.serviceId}`}</strong><span>{staffLabel(item.staffName)}</span>{item.resources?.length > 0 && <small>{item.resources.map(resource => resource.facilityName || resource.name).filter(Boolean).join(', ')}</small>}</li>)}</ul><p className="profile-card-note">SPA-APT-#{nextAppointment.id} · Kết thúc dự kiến {timeLabel(nextAppointment.endTime)}.</p></>
                  : <StateNotice>Chưa có lịch hẹn sắp tới. Lịch đã hoàn tất hoặc hủy vẫn được giữ trong tab Lịch hẹn Spa.</StateNotice>}
              </CardBody></div>
              <div className="bento-card-footer">{nextAppointment ? <button type="button" className="btn-editorial-primary" onClick={() => setSelectedAppointment(nextAppointment.id)}>Chi tiết lịch hẹn</button> : <button type="button" className="btn-editorial-outline" onClick={() => onNavigate('booking')}>Đặt hẹn Spa</button>}</div>
            </article>

            <article className="bento-modular-card">
              <div className="bento-card-header"><h2><Ticket size={17} />Ví vé Spa</h2>{data.tickets && <span className="profile-status-badge">{activeTickets.length} vé khả dụng</span>}</div>
              <div className="bento-card-body"><CardBody data={data.tickets} error={errors.tickets}>
                {firstTicket ? <><h3>{firstTicket.packageName || `Gói #${firstTicket.id}`}</h3><p className="profile-card-note">{firstTicket.benefitType === 'TREATMENT_COURSE' ? 'Lộ trình chăm sóc da' : 'Gói dịch vụ nhiều lượt'} · Vé #{firstTicket.id}</p><dl className="profile-card-facts"><div><dt>Còn khả dụng</dt><dd>{firstTicket.remainingSessions} lượt</dd></div><div><dt>Đã dùng / mất lượt</dt><dd>{knownCount(firstTicket.usedSessions) ?? 'Chưa xác định'}</dd></div><div><dt>Đang giữ chỗ</dt><dd>{knownCount(firstTicket.reservedSessions) ?? 'Chưa xác định'}</dd></div><div><dt>Hạn dùng</dt><dd>{firstTicket.expiryDate ? dateLabel(firstTicket.expiryDate) : 'Không giới hạn'}</dd></div></dl></>
                  : <StateNotice>Chưa có vé khả dụng để đặt lịch. Vé đã dùng hết, đang giữ chỗ hoặc hết hạn vẫn xem được trong ví vé.</StateNotice>}
              </CardBody></div>
              <div className="bento-card-footer">{firstTicket ? <button type="button" className="btn-editorial-primary" onClick={() => onNavigate(ticketDestination(firstTicket))}>{firstTicket.benefitType === 'TREATMENT_COURSE' ? 'Mở lộ trình chăm sóc' : 'Dùng vé đặt lịch'}</button> : <button type="button" className="btn-editorial-outline" onClick={() => navigateTab('tickets')}>Xem ví vé</button>}</div>
            </article>
          </div>
        </section>

        <nav className="profile-horizontal-nav" aria-label="Menu tài khoản"><div className="profile-nav-tabs-group">
          {TABS.map(([id, label, Icon]) => <button key={id} type="button" className={`profile-tab-btn${activeTab === id ? ' is-active' : ''}`} aria-current={activeTab === id ? 'page' : undefined} onClick={() => navigateTab(id)}><Icon size={16} /><span>{label}</span>{badges[id] != null && <span className="profile-tab-badge">{badges[id]}</span>}</button>)}
        </div></nav>

        <section className="profile-workspace-canvas" aria-labelledby="profile-workspace-title">
          <div className="profile-tab-header-banner"><div><h2 id="profile-workspace-title">{title}</h2><p>{activeTab === 'orders' ? 'Đơn mua sản phẩm và giao dịch dịch vụ được quản lý riêng.' : 'Thông tin và thao tác của bạn được giữ trong cùng không gian hồ sơ.'}</p></div>{activeTab === 'appointments' && <button type="button" className="btn-editorial-primary" onClick={() => onNavigate('booking')}>Đặt lịch hẹn mới</button>}{activeTab === 'tickets' && <a href="#/service-packages" className="btn-editorial-outline">Khám phá gói Spa</a>}</div>
          <div className="profile-tab-content">
            {activeTab === 'overview' && <>
              <section className="profile-overview-stats" aria-label="Thống kê tài khoản"><CardBody data={data.statistics} error={errors.statistics}>
                <dl className="profile-stats-list"><div><dt>Tổng giao dịch</dt><dd>{data.statistics?.totalOrders}</dd><small>Gồm sản phẩm, gói và hóa đơn Spa</small></div><div><dt>Đã thanh toán</dt><dd>{data.statistics?.totalPaid == null ? 'Chưa có dữ liệu' : formatCurrency(data.statistics.totalPaid)}</dd></div><div><dt>Đã hoàn tiền</dt><dd>{data.statistics?.totalRefunded == null ? 'Chưa có dữ liệu' : formatCurrency(data.statistics.totalRefunded)}</dd></div><div><dt>Chi tiêu thực trả</dt><dd>{data.statistics?.netSpent == null ? 'Chưa có dữ liệu' : formatCurrency(data.statistics.netSpent)}</dd><small>Sau hoàn tiền, không phải số điểm tích lũy</small></div></dl>
              </CardBody></section>
              <div className="profile-overview-links"><article><h3>Lịch sử mua sắm và dịch vụ</h3><p>Theo dõi giao nhận sản phẩm, thanh toán gói và hóa đơn buổi Spa trong hai nhóm riêng.</p><button type="button" className="btn-editorial-outline" onClick={() => navigateTab('orders')}>Mở lịch sử giao dịch</button></article><article><h3>Hồ sơ chăm sóc da</h3><p>Cập nhật dị ứng, lưu ý chăm sóc và theo dõi lộ trình đã mua.</p><div className="profile-actions"><button type="button" className="btn-editorial-outline" onClick={() => navigateTab('skin')}>Cập nhật hồ sơ da</button><a href="#/treatments?tab=courses" className="profile-text-button"><ExternalLink size={15} />Lộ trình chăm sóc</a></div></article></div>
            </>}

            {listTab && <>
              {data[listTab] == null ? errors[listTab] ? <StateNotice error>Chưa tải được {resourceNames[listTab].toLowerCase()}. Vui lòng thử lại.</StateNotice> : <AccountLoading count={3} label={`Đang tải ${resourceNames[listTab].toLowerCase()}…`} /> : <>
                {actionError && <StateNotice error>{actionError}</StateNotice>}
                {listTab === 'tickets' && <div className="profile-list-meta"><span>{tickets.length} vé · {activeTickets.length} vé đang khả dụng</span><span>6 vé mỗi trang</span></div>}
                {listTab === 'appointments' && <div className="profile-list-meta"><span>{appointments.length} lịch hẹn · {upcoming.length} lịch sắp tới / đang thực hiện</span><span>5 lịch mỗi trang</span></div>}
                {listTab === 'tickets' && (tickets.length ? <div className="customer-profile-ticket-grid">{ticketWindow.rows.map(ticket => <article key={ticket.id} className="account-card profile-ticket-card"><header className="profile-card-heading"><h3>{ticket.packageName || `Gói #${ticket.id}`}</h3><span className="profile-status-badge">{getTicketStatusText(ticket.status)}</span></header><dl className="profile-card-facts"><div><dt>Mã vé</dt><dd>#{ticket.id}</dd></div><div><dt>Khả dụng / Tổng lượt</dt><dd>{knownCount(ticket.remainingSessions) ?? 'Chưa rõ'} / {knownCount(ticket.totalSessions) ?? 'Chưa rõ'}</dd></div><div><dt>Đã dùng / mất lượt</dt><dd>{knownCount(ticket.usedSessions) ?? 'Chưa rõ'}</dd></div><div><dt>Đang giữ</dt><dd>{knownCount(ticket.reservedSessions) ?? 'Chưa rõ'}</dd></div><div><dt>Hạn sử dụng</dt><dd>{ticket.expiryDate ? dateLabel(ticket.expiryDate) : 'Không giới hạn'}</dd></div></dl><TicketDetails ticket={ticket} onNavigate={onNavigate} onUpdated={loadData} /></article>)}</div> : <StateNotice>Bạn chưa có vé Spa. Các gói đã thanh toán sẽ xuất hiện sau khi hệ thống cấp quyền sử dụng.</StateNotice>)}
                {listTab === 'appointments' && (appointments.length ? <div className="profile-appointment-list">{aptWindow.rows.map(appointment => {
                  const calendar = ['PENDING', 'CONFIRMED'].includes(appointment.status) ? calendarUrl(appointment) : '';
                  return <article key={appointment.id} className="account-card profile-appointment-card"><div><header className="profile-card-heading"><strong>SPA-APT-#{appointment.id}</strong><span className="profile-status-badge">{appointmentLabel(appointment)}</span></header><div className="profile-metadata"><span><Calendar size={16} />{dateLabel(appointment.appointmentDate)}</span><span><Clock size={16} />{timeLabel(appointment.startTime)} – {timeLabel(appointment.endTime)}</span></div><ul className="profile-appointment-services">{(appointment.items || []).map((item, index) => <li key={item.id || index}><strong>{item.serviceName || `Dịch vụ #${item.serviceId}`}</strong><span>{staffLabel(item.staffName)}</span></li>)}</ul></div><div className="profile-appointment-actions"><AppointmentTools appointment={appointment} onSaved={loadData} autoOpen={String(appointment.id) === requestedAppointment} /><div className="profile-actions">{calendar && <a href={calendar} target="_blank" rel="noopener noreferrer" className="profile-text-button">Lưu vào lịch</a>}{['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt && <button type="button" disabled={cancellingId != null} onClick={() => cancelAppointment(appointment.id)} className="profile-text-button profile-danger">{cancellingId === appointment.id ? 'Đang hủy…' : 'Hủy lịch hẹn'}</button>}</div></div></article>;
                })}</div> : <StateNotice>Bạn chưa có lịch hẹn Spa. Chọn Đặt lịch hẹn mới để bắt đầu.</StateNotice>)}
                <Pagination page={listTab === 'tickets' ? ticketWindow.page : aptWindow.page} totalPages={listTab === 'tickets' ? ticketWindow.totalPages : aptWindow.totalPages} onChange={listTab === 'tickets' ? setTicketPage : setAptPage} label={listTab === 'tickets' ? 'Phân trang vé Spa' : 'Phân trang lịch hẹn'} />
              </>}
            </>}
            {activeTab === 'orders' && <OrderWorkspace refreshKey={refreshTick} onNavigate={onNavigate} initialOrderId={params.get('order')} onOrderChanged={loadData} />}
            {activeTab === 'skin' && <SkinProfile refreshKey={refreshTick} />}
            {activeTab === 'settings' && <AccountSettings profile={profile} onSaved={loadData} />}
            {activeTab === 'addresses' && <AddressBook refreshKey={refreshTick} />}
            {activeTab === 'wishlist' && <WishlistPage refreshKey={refreshTick} onNavigate={onNavigate} />}
            {activeTab === 'notifications' && <NotificationInbox refreshKey={refreshTick} onNavigate={onNavigate} />}
          </div>
        </section>
      </div>
      {selectedOrder && <OrderDetailDialog key={selectedOrder} orderId={selectedOrder} onClose={() => setSelectedOrder(null)} onNavigate={onNavigate} onChanged={loadData} />}
      {selectedAppointment && <TreatmentAppointmentDialog key={selectedAppointment} appointmentId={selectedAppointment} role="customer" onClose={() => setSelectedAppointment(null)} onSaved={loadData} />}
    </div>
  </div>;
}
