import React, { useState } from 'react';
import { ArrowLeft, Search, AlertCircle, ShoppingBag } from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { OrderDetailDialog } from '../components/account/OrderWorkspace';
import { getGuestSessionId } from '../stores/customerCartStore';
import { apiClient } from '../../shared/api/client';

export function OrderLookupPage({ onNavigate }) {
  const { isAuthenticated } = useAuth();
  const query = new URLSearchParams(window.location.hash.split('?')[1] || '');
  const [orderNumber, setOrderNumber] = useState(query.get('code') || query.get('orderNumber') || '');
  const [phone, setPhone] = useState(query.get('phone') || '');
  const [opened, setOpened] = useState(query.get('id') || null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const saved = (() => {
    try {
      return JSON.parse(localStorage.getItem(`beautyshop_guest_orders:${getGuestSessionId()}`) || '[]');
    } catch {
      return [];
    }
  })();

  const handleLookup = async (e) => {
    e.preventDefault();
    setError('');
    const cleanCode = orderNumber.trim();
    const cleanPhone = phone.trim();
    if (!cleanCode || !cleanPhone) {
      setError('Vui lòng nhập cả Mã đơn hàng và Số điện thoại đặt hàng.');
      return;
    }
    setBusy(true);
    try {
      const res = await apiClient.get(`/api/v1/orders/lookup?orderNumber=${encodeURIComponent(cleanCode)}&phone=${encodeURIComponent(cleanPhone)}`);
      const data = res?.data || res;
      if (data?.id) {
        setOpened(data.id);
      } else {
        setError('Không tìm thấy thông tin đơn hàng khớp với yêu cầu.');
      }
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Không tìm thấy đơn hàng. Vui lòng kiểm tra lại mã đơn và số điện thoại.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="customer-container account-stack" style={{ maxWidth: 760, padding: '40px 20px' }}>
      <nav className="profile-breadcrumb" aria-label="Đường dẫn tra cứu" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--c-text-light, #716762)', marginBottom: 8 }}>
        <button type="button" onClick={() => onNavigate?.('')} style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <ArrowLeft size={14} /> Trang chủ
        </button>
        <span aria-hidden="true">/</span>
        <strong style={{ color: 'var(--c-primary, #721B29)' }}>Tra cứu đơn hàng</strong>
      </nav>

      <div style={{ marginBottom: 12 }}>
        <h1 style={{ fontSize: '24px', fontWeight: 700, margin: '0 0 6px 0', color: 'var(--c-primary, #721B29)' }}>Tra cứu đơn hàng</h1>
        <p style={{ margin: 0, fontSize: '13.5px', color: 'var(--c-text-muted, #6B7280)' }}>
          Nhập mã đơn hàng và số điện thoại người nhận để theo dõi tiến độ giao hàng và thông tin thanh toán.
        </p>
      </div>

      {isAuthenticated && (
        <div style={{ padding: '12px 16px', backgroundColor: '#FDF2F8', border: '1px solid #FBCFE8', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: '13px', color: '#9D174D' }}>Bạn đã đăng nhập tài khoản. Có thể xem toàn bộ đơn hàng trong mục cá nhân.</span>
          <button type="button" className="btn-luxury-primary" style={{ padding: '6px 14px', fontSize: '12px', flexShrink: 0 }} onClick={() => onNavigate('profile?tab=orders')}>
            Đơn hàng của tôi
          </button>
        </div>
      )}

      {error && (
        <div style={{ padding: '10px 14px', backgroundColor: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 6, color: '#991B1B', fontSize: '13px', display: 'flex', alignItems: 'center', gap: 8 }}>
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      <form className="account-card account-form" onSubmit={handleLookup}>
        <label>
          Mã đơn hàng *
          <input
            required
            type="text"
            placeholder="Ví dụ: ORD-9E7... hoặc BS-1001"
            value={orderNumber}
            onChange={event => setOrderNumber(event.target.value)}
          />
        </label>

        <label>
          Số điện thoại đặt hàng *
          <input
            required
            type="tel"
            pattern="(0|\\+84)[35789][0-9]{8}"
            placeholder="Ví dụ: 0987654321"
            value={phone}
            onChange={event => setPhone(event.target.value)}
          />
        </label>

        <div className="account-actions" style={{ marginTop: 8 }}>
          <button disabled={busy} className="btn-luxury-primary" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Search size={14} />
            {busy ? 'Đang tra cứu...' : 'Tra cứu đơn hàng'}
          </button>
        </div>
      </form>

      {saved.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <h3 style={{ fontSize: '15px', fontWeight: 600, marginBottom: 10, color: 'var(--c-primary, #721B29)' }}>
            Đơn hàng vừa đặt trên trình duyệt này
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {saved.map(row => (
              <article key={row.id} className="account-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px' }}>
                <div>
                  <strong style={{ fontFamily: 'var(--font-mono, monospace)', fontSize: '14px', color: 'var(--c-primary, #721B29)' }}>
                    {row.orderNumber || `Đơn #${row.id}`}
                  </strong>
                  <div style={{ fontSize: '12px', color: 'var(--c-text-muted, #6B7280)', marginTop: 2 }}>
                    Đơn hàng khách vãng lai
                  </div>
                </div>
                <button type="button" className="btn-luxury-outline" style={{ fontSize: '12px', padding: '6px 14px' }} onClick={() => setOpened(row.id)}>
                  Xem chi tiết
                </button>
              </article>
            ))}
          </div>
        </div>
      )}

      {opened && <OrderDetailDialog orderId={opened} onClose={() => setOpened(null)} onNavigate={onNavigate} />}
    </div>
  );
}
