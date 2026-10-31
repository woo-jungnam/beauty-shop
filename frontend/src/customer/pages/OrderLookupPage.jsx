import React, { useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { OrderDetailDialog } from '../components/account/OrderWorkspace';
import { getGuestSessionId } from '../stores/customerCartStore';

export function OrderLookupPage({ onNavigate }) {
  const { isAuthenticated } = useAuth();
  const query = new URLSearchParams(window.location.hash.split('?')[1] || '');
  const [id, setId] = useState(query.get('id') || '');
  const [opened, setOpened] = useState(query.get('id') || null);
  const saved = (() => { try { return JSON.parse(localStorage.getItem(`beautyshop_guest_orders:${getGuestSessionId()}`) || '[]'); } catch { return []; } })();
  return (
    <div className="customer-container account-stack" style={{ maxWidth: 850, padding: '40px 20px' }}>
      <nav className="profile-breadcrumb" aria-label="Đường dẫn tra cứu" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--c-text-light, #716762)', marginBottom: 8 }}>
        <button type="button" onClick={() => onNavigate?.('')} style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <ArrowLeft size={14} /> Trang chủ
        </button>
        <span aria-hidden="true">/</span>
        <strong style={{ color: 'var(--c-primary, #721B29)' }}>Tra cứu đơn hàng</strong>
      </nav>
      <h1>Tra cứu đơn hàng</h1>
      <p>Mở đơn đã đặt trên thiết bị này. Đơn tài khoản cần đăng nhập đúng chủ đơn.</p>
      {isAuthenticated && <button onClick={() => onNavigate('profile?tab=orders')}>Toàn bộ đơn hàng tài khoản</button>}
      <form className="account-form" onSubmit={event => { event.preventDefault(); setOpened(id); }}>
        <label>Số ID đơn hàng trong xác nhận<input required type="number" min={1} step={1} value={id} onChange={event => setId(event.target.value)} /></label>
        <button className="btn-luxury-primary">Mở đơn hàng</button>
      </form>
      {saved.map(row => <article className="account-card" key={row.id}><strong>{row.orderNumber || `Đơn #${row.id}`}</strong><button onClick={() => setOpened(row.id)}>Chi tiết & theo dõi</button></article>)}
      {opened && <OrderDetailDialog orderId={opened} onClose={() => setOpened(null)} onNavigate={onNavigate} />}
    </div>
  );
}
