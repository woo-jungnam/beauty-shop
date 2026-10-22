import React, { useState, useEffect } from 'react';
import {
  User, ShoppingBag, Calendar, Clock, Mail, Phone,
  Heart, MapPin, Bell, Shield, LogOut, LayoutDashboard,
  ExternalLink, RefreshCw, Ticket, ArrowLeft
} from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { AccountSettings, AddressBook, SkinProfile, NotificationInbox, WishlistPage } from '../components/account/AccountTools';
import { OrderWorkspace } from '../components/account/OrderWorkspace';
import { AppointmentTools } from '../components/account/AppointmentTools';
import { TicketDetails } from '../../features/spa/TicketDetails';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

const knownCount = (value) => {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') return null;
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : null;
};

const getAptDateParts = (dateStr) => {
  if (!dateStr) return { day: '--', month: 'HẸN SPA' };
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) {
      const parts = String(dateStr).split('-');
      if (parts.length === 3) return { day: parts[2], month: `THÁNG ${parts[1]}` };
      return { day: dateStr, month: 'LỊCH HẸN' };
    }
    const day = d.getDate().toString().padStart(2, '0');
    const month = `THÁNG ${(d.getMonth() + 1).toString().padStart(2, '0')}`;
    return { day, month };
  } catch {
    return { day: dateStr, month: 'LỊCH HẸN' };
  }
};

const getOrderStepIndex = (status) => {
  const s = String(status || '').toUpperCase();
  if (['DELIVERED', 'COMPLETED', 'RECEIVED'].includes(s)) return 4;
  if (['SHIPPED', 'DELIVERING', 'IN_TRANSIT'].includes(s)) return 3;
  if (['CONFIRMED', 'PROCESSING', 'PAID', 'PACKING'].includes(s)) return 2;
  return 1;
};

export const CustomerProfilePage = ({ onNavigate, initialTab = 'overview' }) => {
  const { user, isAuthenticated, logout } = useAuth();
  const [activeTab, setActiveTab] = useState(initialTab);
  const [profile, setProfile] = useState(user);
  const [spaTickets, setSpaTickets] = useState([]);
  const [orders, setOrders] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [statistics, setStatistics] = useState(null);
  const [loadErrors, setLoadErrors] = useState([]);
  const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
  const [loading, setLoading] = useState(true);
  const [cancellingAptId, setCancellingAptId] = useState(null);

  const loadData = async () => {
    setLoading(true);
    setLoadErrors([]);
    try {
      try {
        const res = await apiClient.get('/api/v1/users/order-statistics');
        setStatistics(res.data || res);
      } catch (error) {
        setLoadErrors((previous) => [...previous, error.message]);
      }

      const [profileRes, ticketsRes, ordersRes, aptsRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.AUTH.PROFILE),
        apiClient.get(ENDPOINTS.SPA.MY_TICKETS || '/api/v1/spa/tickets/my-tickets'),
        apiClient.get(ENDPOINTS.ORDERS.MY_ORDERS + '?size=20&sort=createdAt,desc'),
        apiClient.get(ENDPOINTS.SPA.MY_APPOINTMENTS || '/api/v1/appointments/my-appointments')
      ]);

      setLoadErrors((previous) => [
        ...previous,
        ...[profileRes, ticketsRes, ordersRes, aptsRes]
          .filter((row) => row.status === 'rejected')
          .map((row) => row.reason.message)
      ]);

      if (profileRes.status === 'fulfilled') {
        setProfile(profileRes.value?.data || profileRes.value);
      }
      if (ticketsRes.status === 'fulfilled') {
        const tData = ticketsRes.value?.data || ticketsRes.value;
        setSpaTickets(Array.isArray(tData) ? tData : []);
      }
      if (ordersRes.status === 'fulfilled') {
        const oData = ordersRes.value?.data || ordersRes.value;
        const orderList = oData?.content || (Array.isArray(oData) ? oData : []);
        setOrders(orderList);
      }
      if (aptsRes.status === 'fulfilled') {
        const aData = aptsRes.value?.data || aptsRes.value;
        setAppointments(Array.isArray(aData) ? aData : []);
      }
    } catch (e) {
      console.warn('Failed to load profile data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isAuthenticated) return;
    loadData();
  }, [isAuthenticated]);

  useEffect(() => {
    if (!initialTab) return;
    setActiveTab(initialTab);
  }, [initialTab]);

  const handleTabChange = (tabId) => {
    setActiveTab(tabId);
    onNavigate?.(`profile?tab=${tabId}`, { scroll: false });
  };

  const handleCancelAppointment = async (aptId) => {
    if (!window.confirm('Bạn có chắc chắn muốn hủy lịch hẹn Spa này không?')) return;
    setCancellingAptId(aptId);
    try {
      await apiClient.put(`/api/v1/appointments/${aptId}/cancel`);
      await loadData();
    } catch (err) {
      alert(err.message || 'Không thể hủy lịch hẹn');
    } finally {
      setCancellingAptId(null);
    }
  };

  const handleLogout = async () => {
    if (window.confirm('Bạn có chắc chắn muốn đăng xuất tài khoản?')) {
      await logout?.();
      onNavigate?.('');
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="customer-profile-page profile-login-prompt">
        <h2>Vui lòng đăng nhập</h2>
        <p>Đăng nhập để xem vé liệu trình, lịch sử đơn hàng và thông tin thành viên.</p>
        <button onClick={() => onNavigate('login')} className="btn-editorial-primary">Đăng nhập ngay</button>
      </div>
    );
  }

  const displayUser = profile || user || {};
  const displayName = displayUser.fullName || displayUser.username || 'Khách hàng BeautyShop';
  const avatarLetter = displayName.charAt(0).toUpperCase();
  const membershipTier = typeof displayUser.membershipTier === 'string' ? displayUser.membershipTier.trim() : '';
  const totalSpent = statistics?.netSpent;
  const deliveredOrders = orders.filter((order) => order.status === 'DELIVERED').length;
  const nextTierPoints = { MEMBER: 1000, BRONZE: 1000, SILVER: 5000, GOLD: 10000 }[membershipTier] ?? null;
  const nextTierName = { MEMBER: 'HẠNG ĐỒNG', BRONZE: 'HẠNG BẠC', SILVER: 'HẠNG VÀNG', GOLD: 'HẠNG BẠCH KIM' }[membershipTier] ?? 'HẠNG VIP';
  const isHighestTier = membershipTier === 'PLATINUM' || membershipTier === 'DIAMOND';
  const loyaltyPoints = knownCount(displayUser.loyaltyPoints);

  const getAppointmentStatusText = (status) => {
    switch (status) {
      case 'PENDING': return 'Chờ xác nhận';
      case 'CONFIRMED': return 'Đã xác nhận';
      case 'CHECKED_IN':
      case 'IN_PROGRESS': return 'Đang thực hiện';
      case 'COMPLETED': return 'Hoàn tất';
      case 'CANCELLED': return 'Đã hủy hẹn';
      case 'NO_SHOW': return 'Vắng mặt';
      default: return typeof status === 'string' && status.trim() ? status : 'Chờ thực hiện';
    }
  };

  const getTicketStatusText = (status) => {
    switch (status) {
      case 'ACTIVE': return 'Còn hiệu lực';
      case 'EXPIRED': return 'Đã hết hạn';
      case 'COMPLETED': return 'Đã dùng hết';
      case 'REVOKED': return 'Đã thu hồi';
      case 'USED': return 'Đã dùng hết';
      case 'CANCELLED': return 'Đã hủy';
      default: return typeof status === 'string' && status.trim() ? status : 'Khả dụng';
    }
  };

  const getTierText = (tier) => {
    switch (tier) {
      case 'DIAMOND': return 'Hạng Kim Cương';
      case 'PLATINUM': return 'Hạng Bạch Kim';
      case 'GOLD': return 'Hạng Vàng';
      case 'SILVER': return 'Hạng Bạc';
      case 'BRONZE': return 'Hạng Đồng';
      case 'MEMBER': return 'Hạng Thành Viên';
      default: return tier ? `Hạng ${tier}` : 'Chưa có thông tin hạng';
    }
  };

  const upcomingAppointment = appointments.find((a) =>
    ['PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS'].includes(a.status)
  ) || appointments[0];
  const aptDateParts = getAptDateParts(upcomingAppointment?.appointmentDate);

  const latestOrder = orders[0];
  const orderStepIndex = getOrderStepIndex(latestOrder?.status);

  const activeTickets = spaTickets.filter((t) => (t.remainingSessions == null || t.remainingSessions > 0) && t.status !== 'EXPIRED' && t.status !== 'REVOKED');

  const navSections = [
    { title: '', items: [{ id: 'overview', label: 'Bảng điều khiển', icon: LayoutDashboard }] },
    {
      title: 'Chăm sóc & trị liệu',
      items: [
        { id: 'appointments', label: 'Lịch hẹn trị liệu', icon: Calendar },
        { id: 'tickets', label: 'Vé liệu trình điện tử', icon: Ticket },
        { id: 'skin', label: 'Hồ sơ làn da cá nhân', icon: User }
      ]
    },
    {
      title: 'Mua sắm',
      items: [
        { id: 'orders', label: 'Lịch sử mua sắm', icon: ShoppingBag },
        { id: 'wishlist', label: 'Sản phẩm yêu thích', icon: Heart }
      ]
    },
    {
      title: 'Tài khoản',
      items: [
        { id: 'settings', label: 'Thông tin & Bảo mật', icon: Shield },
        { id: 'addresses', label: 'Sổ địa chỉ nhận hàng', icon: MapPin },
        { id: 'notifications', label: 'Hộp thư thông báo', icon: Bell }
      ]
    }
  ];

  const currentTabTitle = {
    overview: 'Tổng quan tài khoản',
    orders: 'Đơn hàng mua sắm',
    tickets: 'Ví vé liệu trình Spa',
    appointments: 'Lịch hẹn chăm sóc & Trị liệu',
    skin: 'Hồ sơ phân tích làn da',
    settings: 'Cài đặt tài khoản & Bảo mật',
    addresses: 'Sổ địa chỉ giao nhận',
    wishlist: 'Bộ sưu tập yêu thích',
    notifications: 'Thông báo & Khuyến mãi'
  }[activeTab] || 'Tài khoản của tôi';

  return (
    <div className="customer-profile-page">
      <div className="profile-shell">
        <div className="profile-toolbar">
          <nav className="profile-breadcrumb" aria-label="Đường dẫn tài khoản">
            <button onClick={() => onNavigate('')}>Trang chủ</button>
            <span aria-hidden="true">/</span>
            <strong>Tài khoản của tôi</strong>
            {activeTab !== 'overview' && <><span aria-hidden="true">/</span><span>{currentTabTitle}</span></>}
          </nav>
          <button onClick={loadData} disabled={loading} className="btn-editorial-outline" title="Làm mới dữ liệu">
            <RefreshCw size={18} className={loading ? 'spin' : ''} />
            {loading ? 'Đang đồng bộ...' : 'Làm mới'}
          </button>
        </div>

        {loadErrors.length > 0 && (
          <div role="alert" className="profile-load-error">
            {loadErrors.map((text, index) => <p key={index}>{text}</p>)}
            <button onClick={loadData} className="btn-editorial-outline">Thử lại</button>
          </div>
        )}

        <div className="profile-layout-container">
          <aside className="profile-sidebar">
            <div className="profile-vip-card">
              <div className="profile-vip-header">
                <div className="profile-vip-avatar">
                  {displayUser.avatarUrl ? <img src={displayUser.avatarUrl} alt={displayName} /> : avatarLetter}
                </div>
                <div className="profile-vip-identity">
                  <h3>{displayName}</h3>
                  <div className="profile-vip-username">@{displayUser.username || 'client'}</div>
                  <div className="profile-membership">{getTierText(membershipTier)}</div>
                </div>
              </div>
              <div className="profile-vip-points-box">
                <div className="profile-vip-points-heading">
                  <span>Điểm tích lũy</span>
                  <strong>{loyaltyPoints != null ? `${loyaltyPoints.toLocaleString('vi-VN')} điểm` : 'Chưa có thông tin điểm Loyalty'}</strong>
                </div>
                {nextTierPoints !== null && loyaltyPoints !== null && !isHighestTier && (
                  <>
                    <div className="profile-vip-progress-bar">
                      <div className="profile-vip-progress-fill" style={{ width: `${Math.min(100, Math.max(5, Math.round((loyaltyPoints / nextTierPoints) * 100)))}%` }} />
                    </div>
                    <div className="profile-vip-note">Cần thêm <strong>{Math.max(0, nextTierPoints - loyaltyPoints).toLocaleString('vi-VN')}</strong> điểm để lên {nextTierName}.</div>
                  </>
                )}
                {isHighestTier && <div className="profile-vip-note">Bạn đang ở hạng thành viên cao nhất.</div>}
              </div>
              <div className="profile-vip-contact">
                <div className="profile-icon-label"><Mail size={16} /><span>{displayUser.email || 'Chưa liên kết email'}</span></div>
                <div className="profile-icon-label"><Phone size={16} /><span>{displayUser.phone || 'Chưa cập nhật số điện thoại'}</span></div>
              </div>
            </div>

            <nav className="profile-nav-card" aria-label="Menu tài khoản">
              {navSections.map((group) => (
                <div className="profile-nav-group" key={group.title}>
                  {group.title && <div className="profile-nav-section-title">{group.title}</div>}
                  {group.items.map((item) => {
                    const Icon = item.icon;
                    const isActive = activeTab === item.id;
                    return (
                      <button key={item.id} type="button" onClick={() => handleTabChange(item.id)} aria-pressed={isActive} className={`profile-nav-item ${isActive ? 'is-active' : ''}`}>
                        <span className="profile-nav-item-left"><Icon size={20} /><span>{item.label}</span></span>
                      </button>
                    );
                  })}
                </div>
              ))}
              <div className="profile-nav-footer">
                <a href="#/treatments?tab=courses" className="profile-nav-item">
                  <span className="profile-nav-item-left"><ExternalLink size={20} /><span>Lộ trình điều trị da y khoa</span></span>
                </a>
                <button type="button" onClick={handleLogout} className="profile-nav-item profile-danger">
                  <span className="profile-nav-item-left"><LogOut size={20} /><span>Đăng xuất tài khoản</span></span>
                </button>
              </div>
            </nav>
          </aside>

          <div className={`profile-main-content${activeTab !== 'overview' ? ' profile-main-detail' : ''}`}>
            {activeTab !== 'overview' && (
              <div className="profile-tab-heading">
                <h1>{currentTabTitle}</h1>
                <button type="button" onClick={() => handleTabChange('overview')} className="profile-text-button"><ArrowLeft size={18} />Bảng điều khiển</button>
              </div>
            )}

            {activeTab === 'overview' && (
              <div className="profile-overview">
                <section className="profile-overview-summary" aria-label="Tổng quan tài khoản">
                <div className="bento-hero-banner">
                  <div>
                    <h1>Xin chào, {displayName}</h1>
                    <p>Theo dõi liệu trình điều trị da liễu, vé dịch vụ Spa và tiến độ đơn hàng mua sắm.</p>
                  </div>
                  <div className="profile-actions">
                    <button onClick={() => onNavigate('booking')} className="btn-editorial-primary"><Calendar size={20} />Đặt hẹn Spa</button>
                    <button onClick={() => onNavigate('products')} className="btn-editorial-outline"><ShoppingBag size={20} />Khám phá sản phẩm</button>
                  </div>
                </div>

                <div className="bento-kpi-grid">
                  <div className="bento-kpi-card">
                    <div className="bento-kpi-label">Đơn mua hàng</div>
                    <div><div className="bento-kpi-value">{(statistics?.totalOrders ?? orders.length).toString().padStart(2, '0')}</div><div className="bento-kpi-sub">{deliveredOrders} đơn giao thành công</div></div>
                  </div>
                  <div className="bento-kpi-card">
                    <div className="bento-kpi-label">Tích lũy chi tiêu</div>
                    <div><div className="bento-kpi-value bento-kpi-currency">{totalSpent == null ? 'Chưa tải được' : formatCurrency(totalSpent)}</div><div className="bento-kpi-sub">Thực trả sau hoàn tiền</div></div>
                  </div>
                  <div className="bento-kpi-card">
                    <div className="bento-kpi-label">Vé liệu trình</div>
                    <div><div className="bento-kpi-value">{spaTickets.length.toString().padStart(2, '0')}</div><div className="bento-kpi-sub">{activeTickets.length} vé còn hiệu lực</div></div>
                  </div>
                  <div className="bento-kpi-card">
                    <div className="bento-kpi-label">Điểm tích lũy</div>
                    <div><div className="bento-kpi-value">{loyaltyPoints != null ? loyaltyPoints.toLocaleString('vi-VN') : '0'}</div><div className="bento-kpi-sub">{getTierText(membershipTier)}</div></div>
                  </div>
                </div>

                </section>

                <div className="bento-asymmetric-row">
                  <div className="bento-ticket-widget">
                    <div className="bento-ticket-header">
                      <h2>Lịch hẹn Spa</h2>
                      {upcomingAppointment && <span className="profile-status-badge">{getAppointmentStatusText(upcomingAppointment.status)}</span>}
                    </div>
                    {upcomingAppointment ? (
                      <>
                        <div className="bento-ticket-body">
                          <div className="bento-ticket-date-badge">
                            <div className="bento-ticket-date-month">{aptDateParts.month}</div>
                            <div className="bento-ticket-date-day">{aptDateParts.day}</div>
                            <div className="bento-ticket-date-time">{upcomingAppointment.startTime ? String(upcomingAppointment.startTime).slice(0, 5) : '10:00'}</div>
                          </div>
                          <div>
                            <h3>{upcomingAppointment.items && upcomingAppointment.items.length > 0 ? upcomingAppointment.items.map((it) => it.serviceName || `Dịch vụ #${it.serviceId}`).join(', ') : 'Chăm sóc & Phục hồi da chuyên sâu'}</h3>
                            <div className="profile-metadata">
                              <span className="profile-icon-label"><Clock size={18} /><span>{upcomingAppointment.startTime ? String(upcomingAppointment.startTime).slice(0, 5) : ''} - {upcomingAppointment.endTime ? String(upcomingAppointment.endTime).slice(0, 5) : ''}</span></span>
                              {upcomingAppointment.items?.[0]?.staffName && <span className="profile-icon-label"><User size={18} /><span>KTV: <strong>{upcomingAppointment.items[0].staffName}</strong></span></span>}
                            </div>
                          </div>
                        </div>
                        <div className="bento-ticket-footer">
                          <div className="profile-reference">Mã lịch hẹn: <strong>SPA-APT-#{upcomingAppointment.id}</strong></div>
                          <div className="profile-actions">
                            <button onClick={() => handleTabChange('appointments')} className="btn-editorial-outline">Chi tiết lịch hẹn</button>
                            <button onClick={() => onNavigate('booking')} className="btn-editorial-primary">Đặt hẹn mới</button>
                          </div>
                        </div>
                      </>
                    ) : (
                      <div className="profile-empty-state">
                        <Calendar size={40} />
                        <h4>Bạn chưa có lịch hẹn trị liệu nào sắp tới</h4>
                        <p>Trải nghiệm không gian thư giãn tiêu chuẩn y khoa và các phác đồ làm đẹp độc quyền tại BeautyShop.</p>
                        <button onClick={() => onNavigate('booking')} className="btn-editorial-primary">Đặt lịch hẹn Spa ngay</button>
                      </div>
                    )}
                  </div>

                  <div className="bento-order-card">
                    <div>
                      <div className="profile-card-heading">
                        <h2>Đơn hàng gần nhất</h2>
                        {latestOrder && <span className="profile-status-badge">{({ PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PROCESSING: 'Đang xử lý', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', RETURNED: 'Đã trả hàng' })[latestOrder.status] || latestOrder.status || 'Chưa có trạng thái'}</span>}
                      </div>
                      {latestOrder ? (
                        <div>
                          <div className="profile-muted">Mã đơn hàng:</div>
                          <div className="profile-order-code">#{latestOrder.orderCode || latestOrder.id}</div>
                          <div className="editorial-progress-track">
                            {['Đã đặt', 'Xác nhận', 'Đang giao', 'Hoàn tất'].map((label, index) => (
                              <div key={label} className={`editorial-progress-step ${orderStepIndex >= index + 1 ? 'is-done' : ''}`}>
                                <div className="editorial-progress-dot" /><span className="editorial-progress-label">{label}</span>
                              </div>
                            ))}
                          </div>
                          <div className="profile-order-total"><span>Tổng thanh toán:</span><strong>{formatCurrency(latestOrder.totalAmount || latestOrder.finalAmount)}</strong></div>
                        </div>
                      ) : (
                        <div className="profile-empty-state">
                          <p>Bạn chưa có đơn đặt hàng nào gần đây.</p>
                          <button onClick={() => onNavigate('products')} className="btn-editorial-outline">Khám phá mỹ phẩm</button>
                        </div>
                      )}
                    </div>
                    <div className="profile-card-footer"><button onClick={() => handleTabChange('orders')} className="btn-editorial-outline profile-full-width">Quản lý tất cả đơn hàng</button></div>
                  </div>
                </div>

                <div className="bento-bottom-row">
                  <div className="bento-bottom-card">
                    <div className="profile-card-heading">
                      <h2>Vé liệu trình Spa</h2>
                      {spaTickets.length > 0 && <button onClick={() => handleTabChange('tickets')} className="profile-text-button">Xem tất cả ({spaTickets.length})</button>}
                    </div>
                    {spaTickets.length === 0 ? (
                      <div className="profile-empty-state">
                        <p>Bạn chưa sở hữu thẻ liệu trình Spa điện tử.</p>
                        <button onClick={() => onNavigate('service-packages')} className="btn-editorial-outline">Xem gói liệu trình tiết kiệm</button>
                      </div>
                    ) : (
                      <div className="profile-mini-pass-grid">
                        {spaTickets.slice(0, 2).map((tck) => {
                          const remaining = knownCount(tck.remainingSessions);
                          const used = knownCount(tck.usedSessions);
                          const total = knownCount(tck.totalSessions);
                          return (
                            <div key={tck.id} className="profile-mini-pass-card">
                              <div className="profile-pass-heading"><span>Mã vé #{tck.id}</span><span className="profile-muted">{tck.expiryDate ? new Date(tck.expiryDate).toLocaleDateString('vi-VN') : 'Vô hạn'}</span></div>
                              <h4>{tck.packageName || `Gói Liệu Trình #${tck.id}`}</h4>
                              <div className="profile-pass-usage">Còn lại: {remaining ?? '—'} buổi (Đã dùng {used ?? '—'} / {total ?? '—'})</div>
                              {used !== null && total !== null && total > 0 && <div className="profile-pass-progress"><div style={{ width: `${Math.min(100, Math.round((used / total) * 100))}%` }} /></div>}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                  <div className="bento-bottom-card profile-care-card">
                    <div>
                      <h2>Hồ sơ chăm sóc cá nhân</h2>
                      <p>Nhận phân tích tình trạng da từ chuyên gia da liễu và theo dõi hình ảnh tiến trình qua các buổi trị liệu y khoa.</p>
                    </div>
                    <div className="profile-actions">
                      <button onClick={() => handleTabChange('skin')} className="btn-editorial-outline">Hồ sơ da liễu</button>
                      <a href="#/treatments?tab=courses" className="btn-editorial-primary">Tiến trình điều trị</a>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'tickets' && (
              <div className="profile-detail-stack">
                <p>Cung cấp mã vé hoặc số điện thoại tại quầy lễ tân để nhân viên check-in và thực hiện dịch vụ.</p>
                {spaTickets.length === 0 ? (
                  <div className="account-card profile-empty-state">
                    <h3>Bạn chưa có vé liệu trình Spa nào</h3>
                    <p>Mua gói dịch vụ để tiết kiệm chi phí và hưởng ưu đãi theo liệu trình dài hạn.</p>
                    <button onClick={() => onNavigate('service-packages')} className="btn-editorial-primary">Khám phá gói dịch vụ Spa</button>
                  </div>
                ) : (
                  <div className="customer-profile-ticket-grid">
                    {spaTickets.map((tck) => {
                      const remaining = knownCount(tck.remainingSessions);
                      const used = knownCount(tck.usedSessions);
                      const total = knownCount(tck.totalSessions);
                      return (
                        <div key={tck.id} className="account-card profile-ticket-card">
                          <div className="profile-card-heading">
                            <h3>{tck.packageName || `Gói Liệu Trình #${tck.id}`}</h3>
                          </div>
                          <div className="profile-pass-heading"><span>Còn lại: {remaining === null ? 'Chưa có thông tin' : `${remaining} buổi`}</span><span className="profile-muted">Đã sử dụng: {used ?? '—'} / {total ?? '—'}</span></div>
                          {used !== null && total !== null && total > 0 && <div className="profile-pass-progress"><div style={{ width: `${Math.min(100, Math.round((used / total) * 100))}%` }} /></div>}
                          <div className="profile-ticket-info">
                            <div>• Mã vé: <strong className="profile-reference">#{tck.id}</strong></div>
                            <div>• Hạn sử dụng: <strong>{tck.expiryDate ? new Date(tck.expiryDate).toLocaleDateString('vi-VN') : 'Không giới hạn'}</strong></div>
                            <div>• Trạng thái: <strong>{getTicketStatusText(tck.status)}</strong></div>
                          </div>
                          <TicketDetails ticket={tck} onNavigate={onNavigate} />
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'appointments' && (
              <div className="profile-detail-stack">
                <div className="profile-card-heading"><p>Danh sách các buổi hẹn trị liệu và chăm sóc da tại BeautyShop Clinic.</p><button onClick={() => onNavigate('booking')} className="btn-editorial-primary">Đặt lịch hẹn mới</button></div>
                {appointments.length === 0 ? (
                  <div className="account-card profile-empty-state">
                    <Calendar size={40} /><h3>Bạn chưa có lịch hẹn Spa nào</h3>
                    <p>Chọn dịch vụ và thời gian mong muốn để chuyên viên chuẩn bị phòng chu đáo nhất.</p>
                    <button onClick={() => onNavigate('booking')} className="btn-editorial-primary">Đặt lịch hẹn ngay</button>
                  </div>
                ) : appointments.map((apt) => {
                  const isCancellable = apt.status === 'PENDING' || apt.status === 'CONFIRMED';
                  return (
                    <div key={apt.id} className="account-card profile-appointment-card">
                      <div>
                        <div className="profile-metadata"><strong className="profile-reference">SPA-APT-#{apt.id}</strong><span className="profile-status-badge">{getAppointmentStatusText(apt.status)}</span></div>
                        <div className="profile-metadata">
                          <span className="profile-icon-label"><Calendar size={18} /><strong>{apt.appointmentDate}</strong></span>
                          <span className="profile-icon-label"><Clock size={18} /><span>{apt.startTime ? String(apt.startTime).slice(0, 5) : ''} - {apt.endTime ? String(apt.endTime).slice(0, 5) : ''}</span></span>
                        </div>
                        <h3>{apt.items && apt.items.length > 0 ? apt.items.map((it) => it.serviceName || `Dịch vụ #${it.serviceId}`).join(', ') : 'Dịch vụ Chăm Sóc Da Spa'}</h3>
                        {apt.items && apt.items[0]?.staffName && <div className="profile-muted">Kỹ thuật viên: <strong>{apt.items[0].staffName}</strong></div>}
                      </div>
                      <div className="account-actions">
                        <AppointmentTools appointment={apt} onSaved={loadData} autoOpen={String(apt.id) === params.get('appointment')} />
                        {isCancellable && <button onClick={() => handleCancelAppointment(apt.id)} disabled={cancellingAptId === apt.id} className="btn-editorial-outline profile-danger">{cancellingAptId === apt.id ? 'Đang hủy...' : 'Hủy lịch hẹn'}</button>}
                        <button onClick={() => onNavigate('booking')} className="btn-editorial-primary">Đặt hẹn mới</button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {activeTab === 'orders' && <OrderWorkspace onNavigate={onNavigate} initialOrderId={params.get('order')} />}
            {activeTab === 'skin' && <SkinProfile />}
            {activeTab === 'settings' && <AccountSettings profile={displayUser} onSaved={loadData} />}
            {activeTab === 'addresses' && <AddressBook />}
            {activeTab === 'wishlist' && <WishlistPage onNavigate={onNavigate} />}
            {activeTab === 'notifications' && <NotificationInbox onNavigate={onNavigate} />}
          </div>
        </div>
      </div>
    </div>
  );
};
