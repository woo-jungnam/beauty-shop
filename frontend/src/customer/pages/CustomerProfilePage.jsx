import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  User, ShoppingBag, Calendar, Clock, Mail, Phone, Heart, MapPin, Bell,
  Shield, LogOut, LayoutDashboard, ExternalLink, RefreshCw, Ticket, ArrowLeft,
  ChevronRight, PanelLeftClose, PanelLeftOpen, Sparkles, Percent, Award, Store, Check,
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
import {
  buildGoogleCalendarUrl, formatCurrency, getOrderStatusBadge,
  getPaymentStatusBadge, getTicketStatusText, resolveMediaUrl,
} from '../../shared/utils/formatters';
import { availableTicket, knownCount, pageWindow, upcomingAppointments } from '../utils/profileModel';
import '../styles/customer-profile-beautyshop.css';

const APT_PAGE_SIZE = 5;
const TICKET_PAGE_SIZE = 6;

const TABS = [
  ['overview', 'Tổng quan', LayoutDashboard],
  ['orders', 'Lịch sử mua hàng', ShoppingBag],
  ['appointments', 'Lịch hẹn Spa', Calendar],
  ['tickets', 'Ví vé Spa', Ticket],
  ['skin', 'Hồ sơ da & Phác đồ', Sparkles],
  ['addresses', 'Sổ địa chỉ', MapPin],
  ['wishlist', 'Sản phẩm yêu thích', Heart],
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

const StateNotice = ({ children, error = false }) => (
  <div className={`profile-state-notice${error ? ' is-error' : ''}`} role={error ? 'alert' : undefined}>
    {children}
  </div>
);

const CardBody = ({ data, error, children }) => data == null
  ? error ? <StateNotice error>Chưa tải được dữ liệu. Bấm nút Làm mới phía trên.</StateNotice> : <AccountLoading count={1} label="Đang đồng bộ dữ liệu thẻ…" />
  : children;

export const CustomerProfilePage = props => {
  const { user } = useAuth();
  return <ProfileContent key={user?.id ?? 'guest'} {...props} />;
};

function ProfileContent({ onNavigate, initialTab = 'overview' }) {
  const { user, isAuthenticated, logout } = useAuth();
  const [activeTab, setActiveTab] = useState(() => normalizeTab(initialTab));
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
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

  const refresh = () => {
    setRefreshTick(tick => tick + 1);
    loadData();
  };

  const cancelAppointment = async id => {
    if (actionLock.current || !window.confirm('Hủy lịch hẹn này? Phí hủy và hoàn cọc áp dụng theo chính sách đã xác nhận khi đặt.')) return;
    actionLock.current = true;
    setCancellingId(id);
    setActionError('');
    try {
      await apiClient.put(`/api/v1/appointments/${id}/cancel`);
      await loadData();
    } catch (error) {
      setActionError(message(error));
    } finally {
      actionLock.current = false;
      setCancellingId(null);
    }
  };

  const handleLogout = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn đăng xuất tài khoản?')) return;
    await logout();
    onNavigate('');
  };

  if (!isAuthenticated) {
    return (
      <div className="customer-profile-page profile-login-prompt">
        <h1>Vui lòng đăng nhập</h1>
        <p>Đăng nhập để xem lịch hẹn, vé Spa và đơn mua sắm của bạn.</p>
        <button type="button" className="pf-btn-primary" onClick={() => onNavigate('login')}>Đăng nhập</button>
        <a href="#/" style={{ color: 'var(--pf-pink)', textDecoration: 'none', fontWeight: 600 }}>Về cửa hàng</a>
      </div>
    );
  }

  const profile = data.profile || user || {};
  const displayName = profile.fullName || profile.username || 'Khách hàng';
  const points = knownCount(profile.loyaltyPoints);
  const tier = profile.membershipTier || 'MEMBER';
  const nextTier = nextTiers[tier];
  const appointments = data.appointments || [];
  const upcoming = upcomingAppointments(appointments, now);
  const nextAppointment = upcoming[0];
  const tickets = data.tickets || [];
  const activeTickets = tickets.filter(ticket => availableTicket(ticket, now));
  const firstTicket = activeTickets[0];
  const latestOrder = data.orders?.content?.[0];
  const orderCount = knownCount(data.orders?.totalElements) ?? knownCount(data.statistics?.totalOrders) ?? 0;
  const aptWindow = pageWindow(appointments, aptPage, APT_PAGE_SIZE);
  const ticketWindow = pageWindow(tickets, ticketPage, TICKET_PAGE_SIZE);
  const badges = {
    orders: orderCount,
    appointments: data.appointments ? upcoming.length : null,
    tickets: data.tickets ? activeTickets.length : null,
  };
  const title = TITLES[activeTab];
  const listTab = activeTab === 'appointments' ? 'appointments' : activeTab === 'tickets' ? 'tickets' : null;

  // Progress to next tier
  const targetPoints = nextTier ? nextTier[0] : 10000;
  const currentPoints = points ?? 0;
  const progressPercent = Math.min(100, Math.max(8, Math.round((currentPoints / targetPoints) * 100)));
  const pointsRemaining = Math.max(0, targetPoints - currentPoints);

  const maskedPhone = profile.phone
    ? (profile.phone.length > 5 ? `${profile.phone.slice(0, 3)}*****${profile.phone.slice(-2)}` : profile.phone)
    : '090*****50';

  return (
    <div className="customer-profile-page">
      <div className="profile-shell">

        {/* 1. HERO BAR - BEAUTYSHOP MEMBER CLUB (MONOLITHIC NOIR & ROSE) */}
        <header className="pf-hero-card">
          {/* Cột 1: Thông tin người dùng & Hạng */}
          <div className="pf-hero-identity">
            <div className="pf-hero-avatar-box">
              <div className="pf-hero-avatar-img">
                {profile.avatarUrl && !avatarFailed ? (
                  <img src={resolveMediaUrl(profile.avatarUrl)} alt={displayName} onError={() => setAvatarFailed(true)} />
                ) : (
                  displayName.charAt(0).toUpperCase()
                )}
              </div>
              <div className="pf-hero-avatar-badge" title="Tài khoản đã xác thực">
                <Check size={13} strokeWidth={3} />
              </div>
            </div>
            <div className="pf-hero-meta">
              <h1 className="pf-hero-name">
                <span>{displayName}</span>
              </h1>
              <div className="pf-hero-phone">
                <span>{maskedPhone}</span>
              </div>
              <div className="pf-hero-tags-row">
                <span className="pf-tier-tag pf-tier-tag-black">
                  {tierNames[tier] || 'THÀNH VIÊN'}
                </span>
                <span className="pf-tier-tag pf-tier-tag-pink">
                  BEAUTYSHOP CLUB
                </span>
              </div>
              <div className="pf-hero-update-text">
                Cập nhật xét hạng sau: 01/01/2027
              </div>
            </div>
          </div>

          {/* Cột 2: Đơn hàng đã mua */}
          <div className="pf-hero-metric-cell">
            <div className="pf-hero-metric-icon">
              <ShoppingBag size={24} />
            </div>
            <div>
              <div className="pf-hero-metric-val">{orderCount}</div>
              <div className="pf-hero-metric-label">Tổng số đơn hàng đã mua</div>
            </div>
          </div>

          {/* Cột 3: Tổng tích lũy & Tiến trình thăng hạng */}
          <div className="pf-hero-spending-cell">
            <div className="pf-hero-spending-header">
              <span className="pf-hero-spending-amount">
                {data.statistics?.totalPaid != null ? formatCurrency(data.statistics.totalPaid) : '0đ'}
              </span>
              <span className="pf-hero-spending-period">Từ 01/01/2026</span>
            </div>
            <div className="pf-hero-progress-track">
              <div className="pf-hero-progress-fill" style={{ width: `${progressPercent}%` }} />
            </div>
            <div className="pf-hero-spending-note">
              {nextTier ? (
                <>
                  <span>Tiến độ: <strong>{progressPercent}%</strong></span>
                  <span>Cần tích lũy thêm <strong>{pointsRemaining.toLocaleString('vi-VN')} điểm</strong> lên {nextTier[1]}</span>
                </>
              ) : (
                <span>Bạn đang ở hạng thành viên cao nhất.</span>
              )}
            </div>
          </div>

          {/* Cột 4: Kênh thành viên & Switcher */}
          <div className="pf-hero-channel-cell">
            <div className="pf-channel-label">Bạn đang ở kênh thành viên</div>
            <div className="pf-channel-pill" onClick={() => onNavigate('')} title="Hệ thống cửa hàng & Spa BeautyShop">
              <div className="pf-channel-logo">BS</div>
              <span>BeautyShop VN</span>
            </div>
            <a href="#/" className="pf-channel-url">
              <span>beautyshop.vn</span>
              <ExternalLink size={12} />
            </a>
          </div>
        </header>

        {/* 2. QUICK-ACTION CHIPS (MONOLITHIC CONNECTOR) */}
        <nav className="pf-quick-chips-bar" aria-label="Thao tác nhanh hồ sơ">
          {TABS.map(([id, label, Icon]) => (
            <button
              key={id}
              type="button"
              className={`pf-quick-chip${activeTab === id ? ' is-active' : ''}`}
              onClick={() => navigateTab(id)}
            >
              <Icon size={16} />
              <span>{label}</span>
              {badges[id] != null && <span style={{ opacity: 0.8 }}>({badges[id]})</span>}
            </button>
          ))}
        </nav>

        {/* 3. 2-COLUMN WORKSPACE WITH COLLAPSIBLE SIDEBAR & DYNAMIC CANVAS */}
        <section className={`pf-workspace-layout${sidebarCollapsed ? ' is-collapsed' : ''}`}>

          {/* LEFT SIDEBAR MENU */}
          <aside className="pf-sidebar" aria-label="Menu tài khoản">
            <div className="pf-sidebar-header">
              <span className="pf-sidebar-title">Menu Thành Viên</span>
              <button
                type="button"
                className="pf-sidebar-toggle-btn"
                title={sidebarCollapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
                onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
              >
                {sidebarCollapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
              </button>
            </div>

            <ul className="pf-sidebar-nav-list">
              {TABS.map(([id, label, Icon]) => (
                <li key={id} className="pf-sidebar-nav-item">
                  <button
                    type="button"
                    className={`pf-sidebar-btn${activeTab === id ? ' is-active' : ''}`}
                    data-tooltip={label}
                    onClick={() => navigateTab(id)}
                  >
                    <Icon size={18} />
                    <span className="pf-label">{label}</span>
                    {badges[id] != null && <span className="pf-sidebar-badge">{badges[id]}</span>}
                  </button>
                </li>
              ))}

              <div className="pf-sidebar-divider" />

              <li className="pf-sidebar-nav-item">
                <button
                  type="button"
                  className="pf-sidebar-btn"
                  data-tooltip="Hệ thống chi nhánh"
                  onClick={() => alert('Đang mở danh sách hệ thống cửa hàng & Spa BeautyShop')}
                >
                  <Store size={18} />
                  <span className="pf-label">Hệ thống chi nhánh</span>
                </button>
              </li>

              <li className="pf-sidebar-nav-item">
                <button
                  type="button"
                  className="pf-sidebar-btn"
                  data-tooltip="Chính sách bảo hành"
                  onClick={() => onNavigate('spa-policy')}
                >
                  <Award size={18} />
                  <span className="pf-label">Chính sách cam kết</span>
                </button>
              </li>

              <li className="pf-sidebar-nav-item">
                <button
                  type="button"
                  className="pf-sidebar-btn pf-sidebar-danger"
                  data-tooltip="Đăng xuất"
                  onClick={handleLogout}
                >
                  <LogOut size={18} />
                  <span className="pf-label">Đăng xuất</span>
                </button>
              </li>
            </ul>
          </aside>

          {/* RIGHT CONTENT CANVAS */}
          <section className="pf-content-canvas">

            {/* TAB ACTION HEADER */}
            <header className="pf-tab-action-header">
              <div className="pf-tab-title-group">
                <h2 className="pf-tab-main-heading">
                  <span>{title}</span>
                </h2>
                <p className="pf-tab-sub-heading">
                  {activeTab === 'overview' && 'Tổng hợp đơn hàng gần đây, ưu đãi và gói liệu trình bảo hành cam kết.'}
                  {activeTab === 'orders' && 'Lịch sử đơn mua sắm mỹ phẩm, mã vận đơn và hóa đơn thanh toán.'}
                  {activeTab === 'appointments' && 'Lịch hẹn chăm sóc da định kỳ tại hệ thống BeautyShop Spa.'}
                  {activeTab === 'tickets' && 'Ví vé điện tử gói liệu trình và suất chăm sóc da đã thanh toán.'}
                  {activeTab === 'skin' && 'Hồ sơ soi da phân tích chỉ số độ ẩm, sắc tố và phác đồ điều trị.'}
                  {activeTab === 'addresses' && 'Danh bạ sổ địa chỉ giao nhận mỹ phẩm hỏa tốc.'}
                  {activeTab === 'wishlist' && 'Danh sách các sản phẩm và dịch vụ bạn đã lưu.'}
                  {activeTab === 'notifications' && 'Hộp thư thông báo cập nhật đơn hàng và khuyến mãi độc quyền.'}
                  {activeTab === 'settings' && 'Quản lý thông tin tài khoản, mật khẩu và xác thực bảo mật 2 lớp.'}
                </p>
              </div>

              <div className="pf-tab-actions-group">
                <button
                  type="button"
                  onClick={refresh}
                  disabled={loading}
                  className="pf-btn-outline"
                >
                  <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
                  <span>{loading ? 'Đang cập nhật…' : 'Làm mới'}</span>
                </button>

                {activeTab === 'appointments' && (
                  <button type="button" className="pf-btn-primary" onClick={() => onNavigate('booking')}>
                    Đặt lịch Spa mới
                  </button>
                )}
                {activeTab === 'tickets' && (
                  <a href="#/service-packages" className="pf-btn-primary" style={{ textDecoration: 'none' }}>
                    Khám phá gói Spa
                  </a>
                )}
                {activeTab === 'overview' && (
                  <button type="button" className="pf-btn-primary" onClick={() => onNavigate('booking')}>
                    Đặt lịch hẹn Spa
                  </button>
                )}
              </div>
            </header>

            {/* SKELETON SHIMMER LOADER WHILE REFRESHING / LOADING */}
            {loading && (
              <div className="pf-skeleton-panel" role="status" aria-label="Đang tải dữ liệu hồ sơ">
                <div className="pf-shimmer" style={{ height: '120px', width: '100%' }} />
                <div style={{ display: 'grid', gridTemplateColumns: '1.55fr 1.05fr', gap: '20px' }}>
                  <div className="pf-shimmer" style={{ height: '220px' }} />
                  <div className="pf-shimmer" style={{ height: '220px' }} />
                </div>
                <div className="pf-shimmer" style={{ height: '140px', width: '100%' }} />
              </div>
            )}

            {/* ERROR SUMMARY IF ANY */}
            {Object.keys(errors).length > 0 && !loading && (
              <div className="profile-load-error" role="alert">
                <strong>Một số dữ liệu chưa thể cập nhật từ máy chủ:</strong>
                <ul>
                  {Object.entries(errors).map(([key, text]) => (
                    <li key={key}>{resourceNames[key] || key}: {text}</li>
                  ))}
                </ul>
                <button type="button" onClick={refresh} className="pf-btn-outline" style={{ marginTop: '10px' }}>
                  Thử lại
                </button>
              </div>
            )}

            {/* TAB CONTENT BODY */}
            {!loading && (
              <div className="pf-tab-body">

                {/* TAB 1: OVERVIEW - BEAUTYSHOP BENTO GRID */}
                {activeTab === 'overview' && (
                  <>
                    <div className="pf-bento-grid">

                      {/* CARD 1: ĐƠN HÀNG GẦN ĐÂY */}
                      <article className="pf-bento-card">
                        <header className="pf-bento-header">
                          <h3 className="pf-bento-title">
                            <ShoppingBag size={18} />
                            <span>Đơn hàng gần đây</span>
                          </h3>
                          {latestOrder && (
                            <span className="pf-status-badge-green">
                              {getOrderStatusBadge(latestOrder.status).text}
                            </span>
                          )}
                        </header>
                        <div className="pf-bento-body">
                          <CardBody data={data.orders} error={errors.orders}>
                            {latestOrder ? (
                              <div className="pf-order-snippet">
                                <div className="pf-order-thumb">
                                  {latestOrder.items?.[0]?.productImageUrl ? (
                                    <img src={resolveMediaUrl(latestOrder.items[0].productImageUrl)} alt="Sản phẩm" />
                                  ) : (
                                    <ShoppingBag size={28} style={{ color: 'var(--pf-pink)' }} />
                                  )}
                                </div>
                                <div className="pf-order-meta">
                                  <div className="pf-order-top-row">
                                    <span className="pf-order-code">
                                      Đơn hàng: {latestOrder.orderNumber || `#${latestOrder.id}`}
                                    </span>
                                    <span style={{ fontSize: '12px', color: 'var(--pf-text-dim)' }}>
                                      {dateLabel(latestOrder.createdAt)}
                                    </span>
                                  </div>
                                  <div className="pf-order-name">
                                    {latestOrder.items?.[0]?.productName || 'Sản phẩm mỹ phẩm BeautyShop'}
                                  </div>
                                  <div className="pf-order-bot-row">
                                    <span className="pf-order-price">
                                      {latestOrder.totalAmount != null ? formatCurrency(latestOrder.totalAmount) : '0đ'}
                                    </span>
                                    <button
                                      type="button"
                                      className="pf-order-link"
                                      onClick={() => setSelectedOrder(latestOrder.id)}
                                    >
                                      <span>Xem chi tiết</span>
                                      <ChevronRight size={14} />
                                    </button>
                                  </div>
                                </div>
                              </div>
                            ) : (
                              <StateNotice>
                                Bạn chưa có đơn mua sắm sản phẩm gần đây.
                              </StateNotice>
                            )}
                          </CardBody>
                        </div>
                        <footer className="pf-bento-footer">
                          <span style={{ fontSize: '13px', color: 'var(--pf-text-dim)' }}>
                            {latestOrder?.carrierName ? `Vận chuyển: ${latestOrder.carrierName}` : 'Đồng bộ tự động từ kho vận'}
                          </span>
                          <button
                            type="button"
                            className="pf-btn-outline"
                            style={{ padding: '6px 14px', fontSize: '12.5px' }}
                            onClick={() => navigateTab('orders')}
                          >
                            Toàn bộ lịch sử đơn
                          </button>
                        </footer>
                      </article>

                      {/* CARD 2: ƯU ĐÃI CỦA BẠN (VOUCHERS) */}
                      <article className="pf-bento-card">
                        <header className="pf-bento-header">
                          <h3 className="pf-bento-title">
                            <Percent size={18} />
                            <span>Ưu đãi của bạn</span>
                          </h3>
                          <span className="pf-tier-tag pf-tier-tag-pink">5 MÃ KHẢ DỤNG</span>
                        </header>
                        <div className="pf-bento-body">
                          <div className="pf-voucher-item">
                            <div className="pf-voucher-meta">
                              <span className="pf-voucher-badge">BS-VIP300K</span>
                              <div className="pf-voucher-desc">Giảm 300.000đ khi đặt lịch Spa từ 1.500.000đ</div>
                              <div className="pf-voucher-date">HSD: 30/11/2026 • Áp dụng toàn hệ thống BeautyShop</div>
                            </div>
                            <button
                              type="button"
                              className="pf-btn-primary"
                              style={{ padding: '6px 14px', fontSize: '12px' }}
                              onClick={() => alert('Đã lưu mã giảm giá vào ví thanh toán!')}
                            >
                              Dùng Ngay
                            </button>
                          </div>

                          <div className="pf-voucher-item">
                            <div className="pf-voucher-meta">
                              <span className="pf-voucher-badge">BS-FREESHIP</span>
                              <div className="pf-voucher-desc">Miễn phí giao hàng hỏa tốc nội thành</div>
                              <div className="pf-voucher-date">HSD: 31/12/2026 • Áp dụng mọi đơn mỹ phẩm BeautyShop</div>
                            </div>
                            <button
                              type="button"
                              className="pf-btn-primary"
                              style={{ padding: '6px 14px', fontSize: '12px' }}
                              onClick={() => alert('Mã Freeship đã sẵn sàng!')}
                            >
                              Dùng Ngay
                            </button>
                          </div>
                        </div>
                        <footer className="pf-bento-footer">
                          <span style={{ fontSize: '12.5px', color: 'var(--pf-text-dim)' }}>
                            Tự động áp dụng mã tốt nhất khi thanh toán
                          </span>
                          <button
                            type="button"
                            className="pf-order-link"
                            onClick={() => navigateTab('notifications')}
                          >
                            Xem tất cả ưu đãi
                          </button>
                        </footer>
                      </article>

                      {/* CARD 3: GÓI CAM KẾT & LIỆU TRÌNH SPA BEAUTYSHOP */}
                      <article className="pf-bento-card">
                        <header className="pf-bento-header">
                          <h3 className="pf-bento-title">
                            <Ticket size={18} />
                            <span>Gói cam kết & Liệu trình Spa</span>
                          </h3>
                          {firstTicket && (
                            <span className="pf-pill-pink">
                              {firstTicket.remainingSessions} LƯỢT KHẢ DỤNG
                            </span>
                          )}
                        </header>
                        <div className="pf-bento-body">
                          <CardBody data={data.tickets} error={errors.tickets}>
                            {firstTicket ? (
                              <div className="pf-treatment-card">
                                <div className="pf-treatment-row">
                                  <span className="pf-treatment-title">
                                    {firstTicket.packageName || `Gói Spa #${firstTicket.id}`}
                                  </span>
                                  <span style={{ fontSize: '12px', color: 'var(--pf-text-dim)' }}>
                                    Vé #{firstTicket.id}
                                  </span>
                                </div>
                                <div className="pf-facts-3col">
                                  <div className="pf-fact-cell">
                                    <span className="pf-fact-lbl">Còn lại</span>
                                    <span className="pf-fact-val" style={{ color: 'var(--pf-pink)' }}>
                                      {firstTicket.remainingSessions} buổi
                                    </span>
                                  </div>
                                  <div className="pf-fact-cell">
                                    <span className="pf-fact-lbl">Đã sử dụng</span>
                                    <span className="pf-fact-val">{knownCount(firstTicket.usedSessions) ?? 0} buổi</span>
                                  </div>
                                  <div className="pf-fact-cell">
                                    <span className="pf-fact-lbl">Hạn dùng</span>
                                    <span className="pf-fact-val">
                                      {firstTicket.expiryDate ? dateLabel(firstTicket.expiryDate) : 'Vô thời hạn'}
                                    </span>
                                  </div>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '13px' }}>
                                  <span style={{ color: 'var(--pf-text-muted)' }}>
                                    {firstTicket.benefitType === 'TREATMENT_COURSE' ? 'Lộ trình phục hồi da chuyên sâu' : 'Gói dịch vụ cao cấp'}
                                  </span>
                                  <button
                                    type="button"
                                    className="pf-btn-primary"
                                    style={{ padding: '6px 14px', fontSize: '12px' }}
                                    onClick={() => onNavigate(ticketDestination(firstTicket))}
                                  >
                                    Đặt Lịch Bằng Vé
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <StateNotice>
                                Bạn chưa có vé Spa khả dụng. Hãy mua gói để hưởng giá ưu đãi và cam kết kết quả.
                              </StateNotice>
                            )}
                          </CardBody>
                        </div>
                        <footer className="pf-bento-footer">
                          <span style={{ fontSize: '12.5px', color: 'var(--pf-text-dim)' }}>
                            Cam kết hoàn tiền 100% nếu không cải thiện liệu trình
                          </span>
                          <button
                            type="button"
                            className="pf-btn-outline"
                            style={{ padding: '6px 14px', fontSize: '12.5px' }}
                            onClick={() => navigateTab('tickets')}
                          >
                            Quản lý ví vé ({tickets.length})
                          </button>
                        </footer>
                      </article>

                      {/* CARD 4: LỊCH HẸN SẮP TỚI & SẢN PHẨM YÊU THÍCH */}
                      <article className="pf-bento-card">
                        <header className="pf-bento-header">
                          <h3 className="pf-bento-title">
                            <Calendar size={18} />
                            <span>Lịch hẹn sắp tới</span>
                          </h3>
                          {nextAppointment && (
                            <span className="pf-status-badge-green">
                              {appointmentLabel(nextAppointment)}
                            </span>
                          )}
                        </header>
                        <div className="pf-bento-body">
                          <CardBody data={data.appointments} error={errors.appointments}>
                            {nextAppointment ? (
                              <div className="pf-treatment-card">
                                <div className="pf-treatment-row">
                                  <span className="pf-treatment-title">
                                    {dateLabel(nextAppointment.appointmentDate)} • {timeLabel(nextAppointment.startTime)}
                                  </span>
                                  <span style={{ fontSize: '12px', color: 'var(--pf-text-dim)' }}>
                                    SPA-APT-#{nextAppointment.id}
                                  </span>
                                </div>
                                <ul style={{ listStyle: 'none', padding: 0, margin: '6px 0', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                                  {(nextAppointment.items || []).map((item, idx) => (
                                    <li key={item.id || idx} style={{ fontSize: '13.5px', display: 'flex', justifyContent: 'space-between' }}>
                                      <strong>{item.serviceName || `Dịch vụ #${item.serviceId}`}</strong>
                                      <span style={{ color: 'var(--pf-text-muted)' }}>{staffLabel(item.staffName)}</span>
                                    </li>
                                  ))}
                                </ul>
                                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '6px' }}>
                                  <button
                                    type="button"
                                    className="pf-btn-primary"
                                    style={{ padding: '6px 14px', fontSize: '12px' }}
                                    onClick={() => setSelectedAppointment(nextAppointment.id)}
                                  >
                                    Chi Tiết Lịch Hẹn
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <StateNotice>
                                Chưa có lịch hẹn sắp tới. Đặt lịch ngay để được phục vụ chu đáo nhất.
                              </StateNotice>
                            )}
                          </CardBody>
                        </div>
                        <footer className="pf-bento-footer">
                          <span style={{ fontSize: '12.5px', color: 'var(--pf-text-dim)' }}>
                            Nhắc hẹn tự động qua tin nhắn và Zalo trước 2 giờ
                          </span>
                          <button
                            type="button"
                            className="pf-btn-outline"
                            style={{ padding: '6px 14px', fontSize: '12.5px' }}
                            onClick={() => navigateTab('appointments')}
                          >
                            Toàn bộ lịch hẹn ({appointments.length})
                          </button>
                        </footer>
                      </article>

                    </div>

                    {/* FINANCIAL & ORDER STATISTICS ROW */}
                    <div className="pf-stats-row" aria-label="Thống kê giao dịch tài khoản">
                      <div className="pf-stat-box">
                        <span className="pf-stat-lbl">Tổng giao dịch tích lũy</span>
                        <span className="pf-stat-val">
                          {data.statistics?.totalOrders ?? orderCount} đơn
                        </span>
                      </div>
                      <div className="pf-stat-box">
                        <span className="pf-stat-lbl">Đã thanh toán thực tế</span>
                        <span className="pf-stat-val is-pink">
                          {data.statistics?.totalPaid != null ? formatCurrency(data.statistics.totalPaid) : '0đ'}
                        </span>
                      </div>
                      <div className="pf-stat-box">
                        <span className="pf-stat-lbl">Đã hoàn tiền</span>
                        <span className="pf-stat-val">
                          {data.statistics?.totalRefunded != null ? formatCurrency(data.statistics.totalRefunded) : '0đ'}
                        </span>
                      </div>
                      <div className="pf-stat-box">
                        <span className="pf-stat-lbl">Chi tiêu thực trả</span>
                        <span className="pf-stat-val">
                          {data.statistics?.netSpent != null ? formatCurrency(data.statistics.netSpent) : '0đ'}
                        </span>
                      </div>
                    </div>
                  </>
                )}

                {/* TAB 2: ORDERS WORKSPACE */}
                {activeTab === 'orders' && (
                  <OrderWorkspace
                    refreshKey={refreshTick}
                    onNavigate={onNavigate}
                    initialOrderId={params.get('order')}
                    onOrderChanged={loadData}
                  />
                )}

                {/* TAB 3 & 4: APPOINTMENTS & TICKETS */}
                {listTab && (
                  <div className="pf-bento-card" style={{ padding: '24px' }}>
                    {actionError && <StateNotice error>{actionError}</StateNotice>}

                    {listTab === 'tickets' && (
                      <>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', fontSize: '13px', color: 'var(--pf-text-dim)' }}>
                          <span>{tickets.length} vé • {activeTickets.length} vé đang khả dụng</span>
                          <span>6 vé mỗi trang</span>
                        </div>
                        {tickets.length > 0 ? (
                          <div className="customer-profile-ticket-grid">
                            {ticketWindow.rows.map(ticket => (
                              <article key={ticket.id} className="account-card profile-ticket-card">
                                <header className="profile-card-heading">
                                  <h3>{ticket.packageName || `Gói #${ticket.id}`}</h3>
                                  <span className="pf-pill-pink">{getTicketStatusText(ticket.status)}</span>
                                </header>
                                <dl className="profile-card-facts">
                                  <div><dt>Mã vé</dt><dd>#{ticket.id}</dd></div>
                                  <div><dt>Khả dụng / Tổng lượt</dt><dd>{knownCount(ticket.remainingSessions) ?? 'Chưa rõ'} / {knownCount(ticket.totalSessions) ?? 'Chưa rõ'}</dd></div>
                                  <div><dt>Đã dùng / mất lượt</dt><dd>{knownCount(ticket.usedSessions) ?? 'Chưa rõ'}</dd></div>
                                  <div><dt>Đang giữ</dt><dd>{knownCount(ticket.reservedSessions) ?? 'Chưa rõ'}</dd></div>
                                  <div><dt>Hạn sử dụng</dt><dd>{ticket.expiryDate ? dateLabel(ticket.expiryDate) : 'Không giới hạn'}</dd></div>
                                </dl>
                                <TicketDetails ticket={ticket} onNavigate={onNavigate} onUpdated={loadData} />
                              </article>
                            ))}
                          </div>
                        ) : (
                          <StateNotice>
                            Bạn chưa có vé Spa. Các gói dịch vụ đã mua sẽ xuất hiện tại đây.
                          </StateNotice>
                        )}
                        <Pagination
                          page={ticketWindow.page}
                          totalPages={ticketWindow.totalPages}
                          onChange={setTicketPage}
                          label="Phân trang vé Spa"
                        />
                      </>
                    )}

                    {listTab === 'appointments' && (
                      <>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', fontSize: '13px', color: 'var(--pf-text-dim)' }}>
                          <span>{appointments.length} lịch hẹn • {upcoming.length} lịch sắp tới</span>
                          <span>5 lịch mỗi trang</span>
                        </div>
                        {appointments.length > 0 ? (
                          <div className="profile-appointment-list">
                            {aptWindow.rows.map(appointment => {
                              const calendar = ['PENDING', 'CONFIRMED'].includes(appointment.status) ? calendarUrl(appointment) : '';
                              return (
                                <article key={appointment.id} className="account-card profile-appointment-card">
                                  <div>
                                    <header className="profile-card-heading">
                                      <strong>SPA-APT-#{appointment.id}</strong>
                                      <span className="pf-status-badge-green">{appointmentLabel(appointment)}</span>
                                    </header>
                                    <div className="profile-metadata">
                                      <span><Calendar size={16} />{dateLabel(appointment.appointmentDate)}</span>
                                      <span><Clock size={16} />{timeLabel(appointment.startTime)} – {timeLabel(appointment.endTime)}</span>
                                    </div>
                                    <ul className="profile-appointment-services">
                                      {(appointment.items || []).map((item, index) => (
                                        <li key={item.id || index}>
                                          <strong>{item.serviceName || `Dịch vụ #${item.serviceId}`}</strong>
                                          <span>{staffLabel(item.staffName)}</span>
                                        </li>
                                      ))}
                                    </ul>
                                  </div>
                                  <div className="profile-appointment-actions">
                                    <AppointmentTools
                                      appointment={appointment}
                                      onSaved={loadData}
                                      autoOpen={String(appointment.id) === requestedAppointment}
                                    />
                                    <div className="profile-actions">
                                      {calendar && (
                                        <a href={calendar} target="_blank" rel="noopener noreferrer" className="pf-order-link">
                                          Lưu vào lịch Google
                                        </a>
                                      )}
                                      {['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt && (
                                        <button
                                          type="button"
                                          disabled={cancellingId != null}
                                          onClick={() => cancelAppointment(appointment.id)}
                                          className="pf-btn-outline"
                                          style={{ color: '#DC2626', borderColor: '#FCA5A5' }}
                                        >
                                          {cancellingId === appointment.id ? 'Đang hủy…' : 'Hủy lịch hẹn'}
                                        </button>
                                      )}
                                    </div>
                                  </div>
                                </article>
                              );
                            })}
                          </div>
                        ) : (
                          <StateNotice>
                            Bạn chưa có lịch hẹn Spa. Hãy chọn Đặt lịch Spa mới để bắt đầu.
                          </StateNotice>
                        )}
                        <Pagination
                          page={aptWindow.page}
                          totalPages={aptWindow.totalPages}
                          onChange={setAptPage}
                          label="Phân trang lịch hẹn"
                        />
                      </>
                    )}
                  </div>
                )}

                {/* TAB 5: HỒ SƠ DA */}
                {activeTab === 'skin' && <SkinProfile refreshKey={refreshTick} />}

                {/* TAB 6: SỔ ĐỊA CHỈ */}
                {activeTab === 'addresses' && <AddressBook refreshKey={refreshTick} />}

                {/* TAB 7: YÊU THÍCH */}
                {activeTab === 'wishlist' && <WishlistPage refreshKey={refreshTick} onNavigate={onNavigate} />}

                {/* TAB 8: THÔNG BÁO & ƯU ĐÃI */}
                {activeTab === 'notifications' && <NotificationInbox refreshKey={refreshTick} onNavigate={onNavigate} />}

                {/* TAB 9: CÀI ĐẶT THÔNG TIN & BẢO MẬT */}
                {activeTab === 'settings' && <AccountSettings profile={profile} onSaved={loadData} />}

              </div>
            )}

          </section>
        </section>

      </div>

      {/* MODAL DIALOGS */}
      {selectedOrder && (
        <OrderDetailDialog
          key={selectedOrder}
          orderId={selectedOrder}
          onClose={() => setSelectedOrder(null)}
          onNavigate={onNavigate}
          onChanged={loadData}
        />
      )}
      {selectedAppointment && (
        <TreatmentAppointmentDialog
          key={selectedAppointment}
          appointmentId={selectedAppointment}
          role="customer"
          onClose={() => setSelectedAppointment(null)}
          onSaved={loadData}
        />
      )}
    </div>
  );
}
