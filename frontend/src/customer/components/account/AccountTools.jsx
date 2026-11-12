import React, { useEffect, useState } from 'react';
import { apiClient } from '../../../shared/api/client';
import { useAuth } from '../../../app/providers/AuthProvider';
import { getVietnamDateString } from '../../../shared/utils/formatters';
import { ProductCard } from '../product/ProductCard';
import './account-tools.css';

export const unwrap = value => value?.data ?? value;
export const listRows = value => { const data = unwrap(value); return data?.content ?? (Array.isArray(data) ? data : []); };
export const message = error => {
  const data = error?.response?.data || error;
  if (Array.isArray(data?.errors) && data.errors.length > 0) {
    return data.errors.map(e => `${e.field ? e.field + ': ' : ''}${e.message}`).join('; ');
  }
  return data?.message || error?.message || 'Chưa thực hiện được. Vui lòng thử lại.';
};

export function AccountSettings({ profile, onSaved }) {
  const { logout } = useAuth();
  const [form, setForm] = useState({ fullName: '', phone: '', bio: '', avatarUrl: '', gender: '', dateOfBirth: '' });
  const [password, setPassword] = useState({ currentPassword: '', newPassword: '' });
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState({ type: '', text: '' });
  useEffect(() => { setForm(Object.fromEntries(Object.keys(form).map(key => [key, profile?.[key] || '']))); }, [profile]);
  const save = async event => {
    event.preventDefault(); setBusy(true); setFeedback({ type: '', text: '' });
    try {
      const payload = {
        fullName: form.fullName?.trim() || null,
        phone: form.phone !== undefined ? form.phone.trim() : null,
        bio: form.bio !== undefined ? form.bio.trim() : null,
        avatarUrl: form.avatarUrl !== undefined ? form.avatarUrl.trim() : null,
        gender: form.gender || null,
        dateOfBirth: form.dateOfBirth || null
      };
      await apiClient.put('/api/v1/users/profile', payload);
      window.dispatchEvent(new Event('auth:profile-updated'));
      await onSaved();
      setFeedback({ type: 'success', text: 'Đã cập nhật thông tin cá nhân thành công.' });
    } catch (error) {
      setFeedback({ type: 'error', text: message(error) });
    } finally { setBusy(false); }
  };
  const changePassword = async event => {
    event.preventDefault(); setBusy(true); setFeedback({ type: '', text: '' });
    try {
      await apiClient.put('/api/v1/users/password', password);
      await logout();
      window.location.hash = '/login';
    } catch (error) {
      setFeedback({ type: 'error', text: message(error) });
    } finally { setBusy(false); }
  };
  return (
    <div className="account-stack">
      {feedback.text && (
        <div style={{
          padding: '10px 14px',
          borderRadius: 6,
          fontSize: 13,
          backgroundColor: feedback.type === 'success' ? '#ECFDF5' : '#FEF2F2',
          color: feedback.type === 'success' ? '#065F46' : '#991B1B',
          border: `1px solid ${feedback.type === 'success' ? '#A7F3D0' : '#FECACA'}`
        }}>
          {feedback.text}
        </div>
      )}
      <form onSubmit={save} className="account-card account-form">
        <h3>Thông tin cá nhân</h3>
        <label>Họ và tên *<input required maxLength={100} value={form.fullName} onChange={event => setForm({ ...form, fullName: event.target.value })} /></label>
        <label>Số điện thoại<input type="tel" placeholder="0987654321 hoặc +84987654321" pattern="(0|\\+84)[35789][0-9]{8}" value={form.phone} onChange={event => setForm({ ...form, phone: event.target.value })} /></label>
        <label>Ngày sinh<input type="date" max={new Date(Date.parse(`${getVietnamDateString()}T12:00:00+07:00`) - 86400000).toISOString().slice(0, 10)} value={form.dateOfBirth} onChange={event => setForm({ ...form, dateOfBirth: event.target.value })} /></label>
        <label>Giới tính
          <select value={form.gender} onChange={event => setForm({ ...form, gender: event.target.value })}>
            <option value="">Chưa cung cấp</option>
            <option value="FEMALE">Nữ</option>
            <option value="MALE">Nam</option>
            <option value="OTHER">Khác</option>
          </select>
        </label>
        <label>Ảnh đại diện (URL)<input type="text" maxLength={500} placeholder="https://example.com/avatar.jpg hoặc /uploads/..." value={form.avatarUrl} onChange={event => setForm({ ...form, avatarUrl: event.target.value })} /></label>
        <label>Giới thiệu bản thân<textarea maxLength={500} placeholder="Chia sẻ đôi nét về bạn..." value={form.bio} onChange={event => setForm({ ...form, bio: event.target.value })} /></label>
        <button className="btn-luxury-primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu thông tin'}</button>
      </form>
      <form className="account-card account-form" onSubmit={changePassword}>
        <h3>Đổi mật khẩu</h3>
        <p>Sau khi đổi mật khẩu, vui lòng đăng nhập lại trên các thiết bị.</p>
        <label>Mật khẩu hiện tại *<input required type="password" autoComplete="current-password" value={password.currentPassword} onChange={event => setPassword({ ...password, currentPassword: event.target.value })} /></label>
        <label>Mật khẩu mới (tối thiểu 8 ký tự) *<input required type="password" minLength={8} maxLength={128} autoComplete="new-password" value={password.newPassword} onChange={event => setPassword({ ...password, newPassword: event.target.value })} /></label>
        <button disabled={busy} className="btn-luxury-outline">{busy ? 'Đang đổi...' : 'Đổi mật khẩu'}</button>
      </form>
    </div>
  );
}

export function SkinProfile() {
  const empty = { skinType: '', sensitivity: '', allergies: '', contraindications: '', preferredIngredients: '', notes: '' };
  const [form, setForm] = useState(empty);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [loaded, setLoaded] = useState(false);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [feedback, setFeedback] = useState({ type: '', text: '' });
  const [busy, setBusy] = useState(false);

  const load = async () => {
    setLoadingProfile(true);
    try {
      const data = unwrap(await apiClient.get('/api/v1/users/skin-profile'));
      setForm({
        skinType: data?.skinType || '',
        sensitivity: data?.sensitivity || '',
        allergies: data?.allergies || '',
        contraindications: data?.contraindications || '',
        preferredIngredients: data?.preferredIngredients || '',
        notes: data?.notes || ''
      });
      if (data?.updatedAt) {
        setLastUpdated(new Date(data.updatedAt).toLocaleString('vi-VN'));
      }
      setLoaded(true);
      setFeedback({ type: '', text: '' });
    } catch (e) {
      setFeedback({ type: 'error', text: message(e) });
    } finally {
      setLoadingProfile(false);
    }
  };

  useEffect(() => { load(); }, []);

  const save = async event => {
    event.preventDefault();
    setBusy(true);
    setFeedback({ type: '', text: '' });
    try {
      const payload = {
        skinType: form.skinType || null,
        sensitivity: form.sensitivity?.trim() || null,
        allergies: form.allergies?.trim() || null,
        contraindications: form.contraindications?.trim() || null,
        preferredIngredients: form.preferredIngredients?.trim() || null,
        notes: form.notes?.trim() || null
      };
      const res = await apiClient.put('/api/v1/users/skin-profile', payload);
      const updated = unwrap(res);
      if (updated?.updatedAt) {
        setLastUpdated(new Date(updated.updatedAt).toLocaleString('vi-VN'));
      }
      setFeedback({ type: 'success', text: 'Đã lưu hồ sơ làn da thành công.' });
    } catch (e) {
      setFeedback({ type: 'error', text: message(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="account-stack">
      {feedback.text && (
        <div style={{
          padding: '10px 14px',
          borderRadius: 6,
          fontSize: 13,
          backgroundColor: feedback.type === 'success' ? '#ECFDF5' : '#FEF2F2',
          color: feedback.type === 'success' ? '#065F46' : '#991B1B',
          border: `1px solid ${feedback.type === 'success' ? '#A7F3D0' : '#FECACA'}`
        }}>
          {feedback.text}
        </div>
      )}
      <form className="account-card account-form" onSubmit={save}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
          <h3 style={{ margin: 0 }}>Hồ sơ làn da của bạn</h3>
          {lastUpdated && <small style={{ color: '#6B7280' }}>Cập nhật lần cuối: {lastUpdated}</small>}
        </div>
        <p>Thông tin do bạn cung cấp để chuyên viên tham khảo. Có thể để trống những mục chưa biết và cập nhật sau khi tư vấn.</p>
        {!loaded ? (
          loadingProfile ? <p role="status">Đang tải hồ sơ làn da…</p> : <button type="button" onClick={load} className="btn-luxury-outline">Tải lại hồ sơ</button>
        ) : (
          <>
            <label>Loại da
              <select value={form.skinType || ''} onChange={e => setForm({ ...form, skinType: e.target.value })}>
                <option value="">Chưa xác định</option>
                <option value="DRY">Da khô</option>
                <option value="OILY">Da dầu</option>
                <option value="COMBINATION">Da hỗn hợp</option>
                <option value="NORMAL">Da thường</option>
                <option value="SENSITIVE">Da nhạy cảm</option>
              </select>
            </label>
            {[
              ['sensitivity', 'Mức độ nhạy cảm', 100],
              ['allergies', 'Dị ứng đã biết', 1000],
              ['contraindications', 'Lưu ý hoặc chống chỉ định đã được tư vấn', 1000],
              ['preferredIngredients', 'Hoạt chất ưu tiên', 1000],
              ['notes', 'Thông tin khác', 2000]
            ].map(([key, label, max]) => (
              <label key={key}>{label}
                <textarea
                  maxLength={max}
                  value={form[key] || ''}
                  onChange={event => setForm({ ...form, [key]: event.target.value })}
                />
              </label>
            ))}
            <button className="btn-luxury-primary" disabled={busy}>
              {busy ? 'Đang lưu...' : 'Lưu hồ sơ da'}
            </button>
          </>
        )}
      </form>
    </div>
  );
}

export function NotificationInbox({ onNavigate }) {
  const [data, setData] = useState({ content: [] });
  const [vouchers, setVouchers] = useState([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [copiedCode, setCopiedCode] = useState('');
  const load = async () => {
    try {
      const [notifRes, voucherRes] = await Promise.allSettled([
        apiClient.get(`/api/v1/users/notifications?page=${page}&size=20`),
        apiClient.get('/api/v1/promotions/available')
      ]);
      if (notifRes.status === 'fulfilled') setData(unwrap(notifRes.value));
      if (voucherRes.status === 'fulfilled') {
        const vData = unwrap(voucherRes.value);
        setVouchers(Array.isArray(vData) ? vData : []);
      }
      setError('');
    } catch (e) {
      setError(message(e));
    }
  };
  useEffect(() => { load(); }, [page]);

  const copyVoucher = (code) => {
    try {
      navigator.clipboard.writeText(code);
      setCopiedCode(code);
      setTimeout(() => setCopiedCode(''), 3000);
    } catch {}
  };

  return (
    <div className="account-stack">
      {vouchers.length > 0 && (
        <section className="account-card" style={{ border: '1px solid #FBCFE8', backgroundColor: '#FDF2F8', padding: '16px 20px', borderRadius: 8 }}>
          <h3 style={{ margin: '0 0 12px 0', fontSize: '15px', color: '#9D174D', display: 'flex', alignItems: 'center', gap: 6 }}>
            <span>🎁</span> Mã ưu đãi & Khuyến mãi hiện có cho bạn ({vouchers.length})
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '12px' }}>
            {vouchers.map(v => {
              const discountText = v.discountType === 'PERCENTAGE'
                ? `Giảm ${v.discountValue}%${v.maxDiscountAmount != null ? ` (tối đa ${new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v.maxDiscountAmount)})` : ''}`
                : `Giảm ${new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v.discountValue)}`;
              const isCopied = copiedCode === v.code;
              return (
                <div key={v.id} style={{
                  backgroundColor: '#FFFFFF',
                  borderRadius: 6,
                  padding: '12px 14px',
                  border: '1px dashed #F472B6',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: 8
                }}>
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontFamily: 'var(--font-mono, monospace)', fontWeight: 800, fontSize: '13px', color: '#9D174D', background: '#FCE7F3', padding: '2px 8px', borderRadius: 4, letterSpacing: '0.5px' }}>
                        {v.code}
                      </span>
                      <button
                        type="button"
                        onClick={() => copyVoucher(v.code)}
                        style={{
                          background: isCopied ? '#ECFDF5' : '#F3F4F6',
                          color: isCopied ? '#065F46' : '#374151',
                          border: `1px solid ${isCopied ? '#A7F3D0' : '#E5E7EB'}`,
                          borderRadius: 4,
                          padding: '3px 8px',
                          fontSize: '11px',
                          cursor: 'pointer',
                          fontWeight: 600
                        }}
                      >
                        {isCopied ? 'Đã chép!' : 'Sao chép'}
                      </button>
                    </div>
                    <strong style={{ display: 'block', fontSize: '12.5px', marginTop: 6, color: '#1F2937' }}>{v.name}</strong>
                    <div style={{ fontSize: '12px', color: '#059669', fontWeight: 600, marginTop: 2 }}>{discountText}</div>
                    <div style={{ fontSize: '11px', color: '#6B7280', marginTop: 2 }}>
                      Đơn tối thiểu: {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v.minOrderAmount || 0)}
                      {v.endsAt && ` · HSD: ${new Date(v.endsAt).toLocaleDateString('vi-VN')}`}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onNavigate?.('products')}
                    className="btn-luxury-outline"
                    style={{ fontSize: '11px', padding: '4px 10px', width: '100%', textAlign: 'center' }}
                  >
                    Dùng mã mua sắm
                  </button>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <h3>Thông báo của bạn</h3>
      {error && <p role="alert">{error}<button onClick={load}>Thử lại</button></p>}
      {listRows(data).map(row => (
        <article className="account-card" key={row.id}>
          <strong>{!row.readAt && '● '}{row.title}</strong>
          <p>{row.message || row.body}</p>
          <small>{row.createdAt && new Date(row.createdAt).toLocaleString('vi-VN')}</small>
          <div className="account-actions">
            {!row.readAt && (
              <button onClick={async () => {
                try {
                  await apiClient.put(`/api/v1/users/notifications/${row.id}/read`);
                  await load();
                } catch (e) {
                  setError(message(e));
                }
              }}>Đánh dấu đã đọc</button>
            )}
            {typeof row.target === 'string' && /^(profile|treatments|order-lookup)(\?|$)/.test(row.target) && (
              <button onClick={() => onNavigate(row.target)}>Mở nội dung liên quan</button>
            )}
          </div>
        </article>
      ))}
      {!listRows(data).length && !error && <p>Chưa có thông báo.</p>}
      <Pagination page={page} totalPages={data?.totalPages || 1} onChange={setPage} />
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) { return <div className="account-actions" aria-label="Phân trang"><button disabled={page <= 0} onClick={() => onChange(page - 1)}>Trang trước</button><span>Trang {page + 1} / {Math.max(1, totalPages)}</span><button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Trang sau</button></div>; }

export const addressFields = [['recipientName', 'Người nhận'], ['phone', 'Số điện thoại'], ['address', 'Số nhà, tên đường'], ['ward', 'Phường / xã'], ['district', 'Quận / huyện (nếu có)'], ['city', 'Tỉnh / thành phố']];
export function AddressBook({ onSelect }) {
  const blank = Object.fromEntries(addressFields.map(([key]) => [key, '']));
  const [items, setItems] = useState([]);
  const [form, setForm] = useState({ ...blank, isDefault: false });
  const [feedback, setFeedback] = useState({ type: '', text: '' });
  const [busy, setBusy] = useState(false);
  const load = async () => { try { setItems(listRows(await apiClient.get('/api/v1/users/addresses'))); } catch (e) { setFeedback({ type: 'error', text: message(e) }); } };
  useEffect(() => {
    load();
    const handleUpdated = () => load();
    window.addEventListener('auth:address-updated', handleUpdated);
    return () => window.removeEventListener('auth:address-updated', handleUpdated);
  }, []);
  return (
    <section className="account-stack">
      <h3>Sổ địa chỉ</h3>
      {feedback.text && (
        <div role={feedback.type === 'error' ? 'alert' : 'status'} style={{
          padding: '10px 14px',
          borderRadius: 6,
          fontSize: 13,
          backgroundColor: feedback.type === 'success' ? '#ECFDF5' : '#FEF2F2',
          color: feedback.type === 'success' ? '#065F46' : '#991B1B',
          border: `1px solid ${feedback.type === 'success' ? '#A7F3D0' : '#FECACA'}`
        }}>
          {feedback.text}
        </div>
      )}
      {items.map(row => (
        <article key={row.id} className="account-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <strong>{row.recipientName} {row.isDefault && <span style={{ fontSize: 11, background: '#E0E7FF', color: '#3730A3', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>Mặc định</span>}</strong>
              <p style={{ margin: '6px 0 0 0', fontSize: 13 }}>{row.phone} · {[row.address, row.ward, row.district, row.city].filter(Boolean).join(', ')}</p>
            </div>
          </div>
          <div className="account-actions" style={{ marginTop: 10 }}>
            {onSelect && <button type="button" className="btn-luxury-primary" style={{ fontSize: 12, padding: '6px 12px' }} onClick={() => onSelect(row)}>Giao tới địa chỉ này</button>}
            <button type="button" onClick={() => setForm({
              ...blank,
              ...Object.fromEntries(addressFields.map(([key]) => [key, row[key] || ''])),
              id: row.id,
              isDefault: Boolean(row.isDefault),
            })}>Sửa</button>
            <button type="button" disabled={busy} onClick={async () => {
              if (!window.confirm('Xóa địa chỉ này khỏi sổ địa chỉ?')) return;
              setBusy(true);
              setFeedback({ type: '', text: '' });
              try {
                await apiClient.delete(`/api/v1/users/addresses/${row.id}`);
                window.dispatchEvent(new Event('auth:address-updated'));
                await load();
                setFeedback({ type: 'success', text: 'Đã xóa địa chỉ thành công.' });
              } catch (e) {
                setFeedback({ type: 'error', text: message(e) });
              } finally {
                setBusy(false);
              }
            }}>Xóa</button>
          </div>
        </article>
      ))}
      <form className="account-card account-form" onSubmit={async event => {
        event.preventDefault(); setBusy(true); setFeedback({ type: '', text: '' });
        try {
          if (form.id) await apiClient.put(`/api/v1/users/addresses/${form.id}`, form);
          else await apiClient.post('/api/v1/users/addresses', form);
          setForm({ ...blank, isDefault: false });
          window.dispatchEvent(new Event('auth:address-updated'));
          await load();
          setFeedback({ type: 'success', text: 'Đã lưu địa chỉ thành công.' });
        } catch (e) {
          setFeedback({ type: 'error', text: message(e) });
        } finally {
          setBusy(false);
        }
      }}>
        <h4>{form.id ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}</h4>
        {addressFields.map(([key, label]) => (
          <label key={key}>{label}{key !== 'district' ? ' *' : ''}
            <input
              required={key !== 'district'}
              maxLength={key === 'address' ? 500 : 100}
              type={key === 'phone' ? 'tel' : 'text'}
              pattern={key === 'phone' ? '(0|\\+84)[35789][0-9]{8}' : undefined}
              placeholder={key === 'phone' ? '0987654321' : ''}
              value={form[key] || ''}
              onChange={e => setForm({ ...form, [key]: e.target.value })}
            />
          </label>
        ))}
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
          <input type="checkbox" checked={form.isDefault} onChange={e => setForm({ ...form, isDefault: e.target.checked })} />
          Đặt làm địa chỉ nhận hàng mặc định
        </label>
        <div className="account-actions">
          <button disabled={busy} className="btn-luxury-primary">{busy ? 'Đang lưu...' : 'Lưu địa chỉ'}</button>
          {form.id && <button type="button" onClick={() => setForm({ ...blank, isDefault: false })}>Hủy sửa</button>}
        </div>
      </form>
    </section>
  );
}

export function WishlistPage({ onNavigate }) {
  const [products, setProducts] = useState([]);
  const [error, setError] = useState('');
  const load = async () => { try { setProducts(listRows(await apiClient.get('/api/v1/users/wishlist'))); } catch (e) { setError(message(e)); } };
  useEffect(() => { load(); const listener = () => load(); window.addEventListener('wishlist:changed', listener); return () => window.removeEventListener('wishlist:changed', listener); }, []);
  return <section className="account-stack"><h3>Sản phẩm yêu thích</h3>{error && <p role="alert">{error}</p>}<div className="account-product-grid">{products.map(row => <ProductCard key={row.id} product={row} onNavigate={onNavigate} />)}</div>{!products.length && !error && <p>Bạn chưa lưu sản phẩm nào. Dùng nút trái tim trên sản phẩm để lưu lại.</p>}</section>;
}

export function PasswordRecoveryPage() {
  const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
  const [token, setToken] = useState(params.get('token') || '');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [reset, setReset] = useState(Boolean(params.get('token')));
  const [feedback, setFeedback] = useState('');
  const [busy, setBusy] = useState(false);
  return <main className="customer-container account-form" style={{ maxWidth: 540, padding: '50px 20px' }}><h1>{reset ? 'Đặt lại mật khẩu' : 'Khôi phục tài khoản'}</h1><form className="account-form" onSubmit={async e => { e.preventDefault(); setBusy(true); try { const response = await apiClient.post(`/api/v1/auth/${reset ? 'reset-password' : 'forgot-password'}`, reset ? { token, newPassword: password } : { email }); setFeedback(response?.message || 'Yêu cầu đã được tiếp nhận. Vui lòng kiểm tra email.'); if (reset) { setPassword(''); setToken(''); } } catch (error) { setFeedback(message(error)); } finally { setBusy(false); } }}>{reset ? <><label>Mã khôi phục<input required value={token} onChange={e => setToken(e.target.value)} /></label><label>Mật khẩu mới<input required type="password" minLength={8} maxLength={128} autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)} /></label></> : <label>Email tài khoản<input required type="email" value={email} onChange={e => setEmail(e.target.value)} /></label>}<button disabled={busy} className="btn-luxury-primary">{reset ? 'Đặt lại mật khẩu' : 'Gửi hướng dẫn qua email'}</button></form>{feedback && <p role="status">{feedback}</p>}<button type="button" onClick={() => { setReset(!reset); setFeedback(''); }}>{reset ? 'Gửi lại email khôi phục' : 'Tôi đã có mã khôi phục'}</button><a href="#/login">Về đăng nhập</a></main>;
}
