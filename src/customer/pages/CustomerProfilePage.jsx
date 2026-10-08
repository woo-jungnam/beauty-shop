import React, { useState, useEffect } from 'react';
import {
  User,
  QrCode,
  ShoppingBag,
  Sparkles,
  Calendar,
  Clock,
  CheckCircle2,
  Award,
  ChevronRight,
  RefreshCw,
  Tag,
  AlertCircle,
  Mail,
  Phone
} from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const CustomerProfilePage = ({ onNavigate, initialTab = 'overview' }) => {
  const { user, isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState(initialTab === 'tickets' ? 'overview' : initialTab);
  const [profile, setProfile] = useState(user);
  const [spaTickets, setSpaTickets] = useState([]);
  const [orders, setOrders] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [cancellingAptId, setCancellingAptId] = useState(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [profileRes, ticketsRes, ordersRes, aptsRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.AUTH.PROFILE),
        apiClient.get(ENDPOINTS.SPA.MY_TICKETS || '/api/v1/spa/tickets/my-tickets'),
        apiClient.get(ENDPOINTS.ORDERS.MY_ORDERS + '?size=20&sort=createdAt,desc'),
        apiClient.get(ENDPOINTS.SPA.MY_APPOINTMENTS || '/api/v1/appointments/my-appointments')
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
    setActiveTab(initialTab === 'tickets' ? 'overview' : initialTab);
  }, [initialTab]);

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

  if (!isAuthenticated) {
    return (
      <div className="customer-container" style={{ padding: '80px 20px', textAlign: 'center' }}>
        <h2>Vui lòng đăng nhập</h2>
        <p>Đăng nhập để xem ví thẻ liệu trình Spa, đơn hàng đã mua và điểm tích lũy thành viên.</p>
        <button onClick={() => onNavigate('login')} className="btn-luxury-primary" style={{ marginTop: '16px' }}>
          Đăng nhập ngay
        </button>
      </div>
    );
  }

  const displayUser = profile || user || {};
  const displayName = displayUser.fullName || displayUser.username || 'Khách hàng BeautyShop';
  const avatarLetter = displayName.charAt(0).toUpperCase();
  const membershipTier = displayUser.membershipTier || 'MEMBER';
  const totalSpent = orders.reduce((sum, order) => sum + Number(order.totalAmount || 0), 0);
  const deliveredOrders = orders.filter((order) => order.status === 'DELIVERED').length;
  const nextTierPoints = membershipTier === 'PLATINUM' ? null : membershipTier === 'GOLD' ? 2000 : membershipTier === 'SILVER' ? 1000 : 300;
  const loyaltyPoints = Number(displayUser.loyaltyPoints || 0);

  const getOrderStatusText = (status) => {
    switch (status) {
      case 'PENDING': return 'Chờ xác nhận';
      case 'CONFIRMED': return 'Đã xác nhận';
      case 'PROCESSING': return 'Đang xử lý';
      case 'SHIPPED': return 'Đang giao hàng';
      case 'DELIVERED': return 'Giao hàng thành công';
      case 'CANCELLED': return 'Đã hủy';
      default: return status || 'Đang xử lý';
    }
  };

  const getAppointmentStatusText = (status) => {
    switch (status) {
      case 'PENDING': return 'Chờ xác nhận';
      case 'CONFIRMED': return 'Đã xác nhận';
      case 'CHECKED_IN': return 'Đang thực hiện';
      case 'COMPLETED': return 'Hoàn tất';
      case 'CANCELLED': return 'Đã hủy';
      default: return status || 'Chờ xác nhận';
    }
  };

  return (
    <div className="customer-container" style={{ padding: '36px 20px 80px 20px' }}>
      <section style={{ marginBottom: '28px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
          <User size={20} color="var(--c-primary)" />
          <div>
            <div style={{ color: 'var(--c-text-gold)', fontSize: '11px', fontWeight: 800, letterSpacing: '0.08em', textTransform: 'uppercase' }}>Hồ sơ cá nhân</div>
            <h1 style={{ margin: '2px 0 0', color: 'var(--c-primary)', fontSize: '26px', fontWeight: 850 }}>Tài khoản của tôi</h1>
          </div>
        </div>

        <div style={{
          background: 'linear-gradient(135deg, #FFFFFF 0%, var(--c-primary-light, #FFF0F3) 100%)',
          borderRadius: 'var(--radius-lg)',
          border: '1px solid var(--c-border)',
          padding: '28px 32px',
          boxShadow: 'var(--shadow-card)',
          display: 'grid',
          gridTemplateColumns: 'minmax(260px, 1.4fr) minmax(220px, 1fr)',
          gap: '24px',
          marginBottom: '18px'
        }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '20px' }}>
            <div style={{
              width: '76px',
              height: '76px',
              borderRadius: '22px',
              backgroundColor: '#FFFFFF',
              border: '2px solid var(--c-gold)',
              color: 'var(--c-text-gold)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '30px',
              fontWeight: 800,
              boxShadow: '0 10px 24px rgba(212, 93, 121, 0.16)',
              overflow: 'hidden',
              flexShrink: 0
            }}>
              {displayUser.avatarUrl ? <img src={displayUser.avatarUrl} alt={displayName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : avatarLetter}
            </div>

            <div style={{ minWidth: 0 }}>
              <h2 style={{ margin: 0, fontSize: '22px', fontFamily: 'var(--font-sans)', fontWeight: 850, color: 'var(--c-primary)' }}>
                {displayName}
              </h2>
              <div style={{ display: 'grid', gap: '6px', marginTop: '10px', fontSize: '13px', color: 'var(--c-text-muted)' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '7px' }}><Mail size={14} /> {displayUser.email || 'Chưa cập nhật email'}</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '7px' }}><Phone size={14} /> {displayUser.phone || 'Chưa cập nhật số điện thoại'}</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '7px' }}><User size={14} /> @{displayUser.username || 'customer'}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '12px', flexWrap: 'wrap' }}>
                <span className="badge-dermatology gold" style={{ fontSize: '11px' }}>
                  {membershipTier}
                </span>
                <span style={{ fontSize: '12px', color: 'var(--c-text-gold)', fontWeight: 700 }}>
                  {loyaltyPoints} Điểm Loyalty
                </span>
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '12px' }}>
            <div style={{ padding: '14px', backgroundColor: '#FFFFFF', border: '1px solid var(--c-border-subtle)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ color: 'var(--c-text-light)', fontSize: '11px' }}>Đơn hàng</div>
              <strong style={{ color: 'var(--c-primary)', fontSize: '20px' }}>{orders.length}</strong>
              <div style={{ color: 'var(--c-safe-green)', fontSize: '11px' }}>{deliveredOrders} đã giao</div>
            </div>
            <div style={{ padding: '14px', backgroundColor: '#FFFFFF', border: '1px solid var(--c-border-subtle)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ color: 'var(--c-text-light)', fontSize: '11px' }}>Tổng chi tiêu</div>
              <strong style={{ color: 'var(--c-primary)', fontSize: '16px', fontFamily: 'var(--font-mono)' }}>{formatCurrency(totalSpent)}</strong>
            </div>
            <div style={{ padding: '14px', backgroundColor: '#FFFFFF', border: '1px solid var(--c-border-subtle)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ color: 'var(--c-text-light)', fontSize: '11px' }}>Vé Spa</div>
              <strong style={{ color: 'var(--c-primary)', fontSize: '20px' }}>{spaTickets.length}</strong>
            </div>
            <div style={{ padding: '14px', backgroundColor: '#FFFFFF', border: '1px solid var(--c-border-subtle)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ color: 'var(--c-text-light)', fontSize: '11px' }}>Lịch hẹn</div>
              <strong style={{ color: 'var(--c-primary)', fontSize: '20px' }}>{appointments.length}</strong>
            </div>
          </div>
        </div>

        {nextTierPoints && (
          <div style={{ padding: '12px 16px', backgroundColor: '#FFFFFF', border: '1px solid var(--c-border-subtle)', borderRadius: 'var(--radius-sm)', color: 'var(--c-text-muted)', fontSize: '12.5px' }}>
            Còn <strong style={{ color: 'var(--c-primary)' }}>{Math.max(0, nextTierPoints - loyaltyPoints)}</strong> điểm để lên hạng tiếp theo.
          </div>
        )}
      </section>

      {/* Tabs Bar */}
      <div style={{
        display: 'flex',
        gap: '24px',
        borderBottom: '1px solid var(--c-border-subtle)',
        marginBottom: '28px'
      }}>
        {[
          { id: 'overview', label: 'Tổng quan' },
          { id: 'orders', label: `Đơn hàng (${orders.length})` },
          { id: 'tickets', label: `Vé Spa (${spaTickets.length})` },
          { id: 'appointments', label: `Lịch hẹn (${appointments.length})` },
          { id: 'skin', label: 'Hồ sơ da' }
        ].map((tab) => {
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                background: 'transparent',
                border: 'none',
                padding: '12px 4px',
                fontSize: '15px',
                fontWeight: active ? 700 : 500,
                color: active ? 'var(--c-primary)' : 'var(--c-text-muted)',
                cursor: 'pointer',
                position: 'relative',
                transition: 'color 0.2s ease'
              }}
            >
              {tab.label}
              {active && (
                <span style={{
                  position: 'absolute',
                  bottom: '-1.5px',
                  left: 0,
                  right: 0,
                  height: '2.5px',
                  backgroundColor: 'var(--c-gold)',
                  borderRadius: '2px'
                }} />
              )}
            </button>
          );
        })}
      </div>

      {activeTab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px', marginBottom: '28px' }}>
          {[
            { id: 'orders', icon: ShoppingBag, title: 'Đơn hàng của tôi', desc: `${orders.length} đơn đã đặt`, action: 'Theo dõi mua hàng' },
            { id: 'tickets', icon: QrCode, title: 'Ví vé Spa', desc: `${spaTickets.length} vé liệu trình`, action: 'Quản lý vé' },
            { id: 'appointments', icon: Calendar, title: 'Lịch hẹn Spa', desc: `${appointments.length} lịch hẹn`, action: 'Xem lịch' },
            { id: 'skin', icon: Sparkles, title: 'Hồ sơ làn da', desc: 'Thông tin chăm sóc cá nhân', action: 'Xem hồ sơ' },
          ].map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveTab(item.id)}
                className="luxury-card"
                style={{ padding: '20px', textAlign: 'left', backgroundColor: '#FFFFFF', border: '1px solid var(--c-border)', borderRadius: 'var(--radius-md)', cursor: 'pointer' }}
              >
                <div style={{ width: '42px', height: '42px', borderRadius: '12px', backgroundColor: 'var(--c-primary-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '14px' }}>
                  <Icon size={20} color="var(--c-primary)" />
                </div>
                <strong style={{ display: 'block', color: 'var(--c-primary)', fontSize: '15px', marginBottom: '5px' }}>{item.title}</strong>
                <span style={{ display: 'block', color: 'var(--c-text-muted)', fontSize: '12.5px', marginBottom: '12px' }}>{item.desc}</span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', color: 'var(--c-text-gold)', fontSize: '12px', fontWeight: 700 }}>
                  {item.action} <ChevronRight size={13} />
                </span>
              </button>
            );
          })}
        </div>
      )}

      {/* ================= TAB 1: SPA DIGITAL TICKETS ================= */}
      {activeTab === 'tickets' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ fontSize: '14px', color: 'var(--c-text-muted)' }}>
            Quét mã QR tại quầy tiếp đón của Spa BeautyShop để tự động điểm danh và trừ số buổi còn lại.
          </div>

          {spaTickets.length === 0 ? (
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border-subtle)',
              padding: '40px',
              textAlign: 'center',
              color: 'var(--c-text-muted)'
            }}>
              <p>Bạn chưa có vé liệu trình Spa nào.</p>
              <button onClick={() => onNavigate('booking')} className="btn-luxury-primary" style={{ marginTop: '12px' }}>
                Khám phá dịch vụ Spa
              </button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px' }}>
              {spaTickets.map((tck) => (
                <div
                  key={tck.id}
                  className="luxury-card"
                  style={{
                    padding: '24px',
                    border: '1.5px solid var(--c-gold-border)',
                    backgroundColor: '#FFFFFF',
                    position: 'relative',
                    overflow: 'hidden'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                    <div>
                      <span className="badge-dermatology gold" style={{ fontSize: '10px' }}>
                        THẺ ĐIỆN TỬ
                      </span>
                      <h3 style={{ fontSize: '16px', fontWeight: 700, margin: '6px 0 0 0', color: 'var(--c-primary)', fontFamily: 'var(--font-serif)' }}>
                        {tck.packageName || `Gói Liệu Trình #${tck.id}`}
                      </h3>
                    </div>

                    <div style={{
                      width: '64px',
                      height: '64px',
                      backgroundColor: 'var(--c-canvas)',
                      border: '1px solid var(--c-border)',
                      borderRadius: '8px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0
                    }}>
                      <QrCode size={48} color="var(--c-primary)" />
                    </div>
                  </div>

                  <div style={{ margin: '16px 0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>
                      <span style={{ color: 'var(--c-safe-green)' }}>
                        Còn lại: {tck.remainingSessions ?? (tck.totalSessions - (tck.usedSessions || 0))} buổi
                      </span>
                      <span style={{ color: 'var(--c-text-light)' }}>
                        Đã sử dụng: {tck.usedSessions || 0} / {tck.totalSessions || 1}
                      </span>
                    </div>
                    <div style={{ width: '100%', height: '8px', backgroundColor: 'var(--c-canvas-subtle)', borderRadius: '9999px', overflow: 'hidden' }}>
                      <div style={{
                        width: `${Math.min(100, Math.round(((tck.usedSessions || 0) / (tck.totalSessions || 1)) * 100))}%`,
                        height: '100%',
                        backgroundColor: 'var(--c-gold)'
                      }} />
                    </div>
                  </div>

                  <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', display: 'flex', flexDirection: 'column', gap: '4px', paddingTop: '12px', borderTop: '1px solid var(--c-border-subtle)' }}>
                    <div>• Mã vé: <strong style={{ fontFamily: 'var(--font-mono)' }}>TICKET-{tck.id}</strong></div>
                    <div>• Hạn sử dụng: <strong>{tck.expiryDate ? new Date(tck.expiryDate).toLocaleDateString('vi-VN') : 'Không giới hạn'}</strong></div>
                    <div>• Trạng thái: <strong>{tck.status || 'ACTIVE'}</strong></div>
                  </div>

                  <button
                    onClick={() => onNavigate('booking')}
                    className="btn-luxury-outline"
                    style={{ width: '100%', marginTop: '16px', fontSize: '12px' }}
                  >
                    <Calendar size={13} />
                    <span>Đặt Hẹn Sử Dụng Buổi Tiếp Theo</span>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ================= TAB: SPA APPOINTMENTS ================= */}
      {activeTab === 'appointments' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {appointments.length === 0 ? (
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border-subtle)',
              padding: '40px',
              textAlign: 'center',
              color: 'var(--c-text-muted)'
            }}>
              <Calendar size={36} color="var(--c-gold)" style={{ margin: '0 auto 12px auto' }} />
              <p style={{ margin: 0, fontWeight: 600 }}>Bạn chưa có lịch hẹn Spa nào được đặt.</p>
              <button onClick={() => onNavigate('booking')} className="btn-luxury-primary" style={{ marginTop: '16px' }}>
                Đặt lịch hẹn ngay
              </button>
            </div>
          ) : (
            appointments.map((apt) => {
              const isCancellable = apt.status === 'PENDING' || apt.status === 'CONFIRMED';
              return (
                <div
                  key={apt.id}
                  className="luxury-card"
                  style={{
                    padding: '22px 24px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '16px',
                    backgroundColor: '#FFFFFF',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--c-border)'
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                      <strong style={{ fontSize: '15px', color: 'var(--c-primary)', fontFamily: 'var(--font-mono)' }}>
                        SPA-APT-#{apt.id}
                      </strong>
                      <span className="badge-dermatology gold" style={{ fontSize: '11px' }}>
                        {getAppointmentStatusText(apt.status)}
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px', fontSize: '13px', color: 'var(--c-text-muted)', marginBottom: '8px' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <Calendar size={14} color="var(--c-gold)" />
                        <strong>{apt.appointmentDate}</strong>
                      </span>
                      <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <Clock size={14} color="var(--c-gold)" />
                        <span>{apt.startTime ? String(apt.startTime).slice(0, 5) : '09:00'} - {apt.endTime ? String(apt.endTime).slice(0, 5) : '10:00'}</span>
                      </span>
                    </div>

                    <div style={{ fontSize: '13.5px', color: 'var(--c-primary)', fontWeight: 600 }}>
                      {apt.items && apt.items.length > 0
                        ? apt.items.map(it => it.serviceName || `Dịch vụ #${it.serviceId}`).join(', ')
                        : 'Dịch vụ Chăm Sóc Da Spa'}
                    </div>

                    {apt.items && apt.items[0]?.staffName && (
                      <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '4px' }}>
                        Kỹ thuật viên: <strong>{apt.items[0].staffName}</strong>
                      </div>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '10px' }}>
                    {isCancellable && (
                      <button
                        onClick={() => handleCancelAppointment(apt.id)}
                        disabled={cancellingAptId === apt.id}
                        className="btn-luxury-outline"
                        style={{ fontSize: '12px', padding: '8px 16px', color: 'var(--c-danger-red)', borderColor: '#FCA5A5' }}
                      >
                        {cancellingAptId === apt.id ? 'Đang hủy...' : 'Hủy lịch hẹn'}
                      </button>
                    )}
                    <button
                      onClick={() => onNavigate('booking')}
                      className="btn-luxury-primary"
                      style={{ fontSize: '12px', padding: '8px 16px' }}
                    >
                      Đặt hẹn mới
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* ================= TAB 2: ORDER HISTORY ================= */}
      {activeTab === 'orders' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {orders.length === 0 ? (
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border-subtle)',
              padding: '40px',
              textAlign: 'center',
              color: 'var(--c-text-muted)'
            }}>
              <p>Bạn chưa có đơn hàng nào.</p>
              <button onClick={() => onNavigate('products')} className="btn-luxury-primary" style={{ marginTop: '12px' }}>
                Mua sắm ngay
              </button>
            </div>
          ) : (
            orders.map((ord) => (
              <div
                key={ord.id || ord.orderNumber}
                className="luxury-card"
                style={{
                  padding: '20px 24px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '16px'
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                    <strong style={{ fontSize: '15px', color: 'var(--c-primary)', fontFamily: 'var(--font-mono)' }}>
                      {ord.orderNumber}
                    </strong>
                    <span style={{ fontSize: '12px', color: 'var(--c-text-light)' }}>
                      • {ord.createdAt ? new Date(ord.createdAt).toLocaleDateString('vi-VN') : ''}
                    </span>
                    <span className="badge-dermatology safe" style={{ fontSize: '11px' }}>
                      {getOrderStatusText(ord.status)}
                    </span>
                  </div>
                  <div style={{ fontSize: '13px', color: 'var(--c-text-muted)' }}>
                    {ord.items && ord.items.length > 0
                      ? ord.items.map(it => `${it.productName || it.sku || 'Sản phẩm'} × ${it.quantity}`).join(', ')
                      : 'Đơn hàng dược mỹ phẩm'}
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Tổng thanh toán</div>
                    <div style={{ fontSize: '16px', fontWeight: 800, color: 'var(--c-primary)', fontFamily: 'var(--font-mono)' }}>
                      {formatCurrency(ord.totalAmount)}
                    </div>
                  </div>

                  <button
                    onClick={() => onNavigate('products')}
                    className="btn-luxury-outline"
                    style={{ fontSize: '12px', padding: '8px 16px' }}
                  >
                    <RefreshCw size={13} />
                    <span>Mua Lại</span>
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* ================= TAB 3: SKIN PROFILE ================= */}
      {activeTab === 'skin' && (
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 'var(--radius-md)',
          border: '1px solid var(--c-border)',
          padding: '32px'
        }}>
          <h3 style={{ fontSize: '18px', fontWeight: 700, margin: '0 0 16px 0', fontFamily: 'var(--font-serif)' }}>
            Hồ Sơ Làn Da Khách Hàng (Dermatology Profile)
          </h3>
          <p style={{ fontSize: '13.5px', color: 'var(--c-text-muted)', lineHeight: 1.6 }}>
            Hồ sơ da được chuẩn hóa theo phân loại da Fitzpatrick & Baumann Skin Typing System. Hệ thống AI Skincare Assistant sẽ sử dụng thông số này để sàng lọc và tự động cảnh báo các hoạt chất chống chỉ định khi bạn duyệt sản phẩm.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginTop: '24px' }}>
            <div style={{ padding: '16px', backgroundColor: 'var(--c-canvas)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Phân Loại Da (Baumann)</div>
              <strong style={{ fontSize: '15px', color: 'var(--c-primary)', marginTop: '4px', display: 'block' }}>OSNW (Dầu, Nhạy Cảm)</strong>
            </div>
            <div style={{ padding: '16px', backgroundColor: 'var(--c-canvas)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Mức Độ Nhạy Cảm</div>
              <strong style={{ fontSize: '15px', color: 'var(--c-concern-amber)', marginTop: '4px', display: 'block' }}>Trung Bình (Cần test dị ứng)</strong>
            </div>
            <div style={{ padding: '16px', backgroundColor: 'var(--c-canvas)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Hoạt Chất Ưu Tiên</div>
              <strong style={{ fontSize: '15px', color: 'var(--c-safe-green)', marginTop: '4px', display: 'block' }}>Niacinamide, B5, Ceramide</strong>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
