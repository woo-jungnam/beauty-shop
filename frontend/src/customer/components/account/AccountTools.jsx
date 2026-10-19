import React, { useEffect, useState } from 'react';
import { apiClient } from '../../../shared/api/client';
import { useAuth } from '../../../app/providers/AuthProvider';
import { ProductCard } from '../product/ProductCard';
import './account-tools.css';

export const unwrap = value => value?.data ?? value;
export const listRows = value => { const data = unwrap(value); return data?.content ?? (Array.isArray(data) ? data : []); };
const message = error => error?.message || 'Chưa thực hiện được. Vui lòng thử lại.';

export function AccountSettings({ profile, onSaved }) {
  const { logout } = useAuth();
  const [form, setForm] = useState({ fullName: '', phone: '', bio: '', avatarUrl: '', gender: '', dateOfBirth: '' });
  const [password, setPassword] = useState({ currentPassword: '', newPassword: '' });
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState('');
  useEffect(() => { setForm(Object.fromEntries(Object.keys(form).map(key => [key, profile?.[key] || '']))); }, [profile]);
  const save = async event => {
    event.preventDefault(); setBusy(true); setFeedback('');
    try { await apiClient.put('/api/v1/users/profile', { ...form, gender: form.gender || null, dateOfBirth: form.dateOfBirth || null }); window.dispatchEvent(new Event('auth:profile-updated')); await onSaved(); setFeedback('Đã cập nhật thông tin cá nhân.'); }
    catch (error) { setFeedback(message(error)); } finally { setBusy(false); }
  };
  const changePassword = async event => {
    event.preventDefault(); setBusy(true); setFeedback('');
    try { await apiClient.put('/api/v1/users/password', password); await logout(); window.location.hash = '/login'; }
    catch (error) { setFeedback(message(error)); } finally { setBusy(false); }
  };
  return <div className="account-stack">{feedback && <p role="status">{feedback}</p>}<form onSubmit={save} className="account-card account-form"><h3>Thông tin cá nhân</h3><label>Họ và tên<input required maxLength={100} value={form.fullName} onChange={event => setForm({ ...form, fullName: event.target.value })} /></label><label>Số điện thoại<input type="tel" pattern="0[35789][0-9]{8}" value={form.phone} onChange={event => setForm({ ...form, phone: event.target.value })} /></label><label>Ngày sinh<input type="date" value={form.dateOfBirth} onChange={event => setForm({ ...form, dateOfBirth: event.target.value })} /></label><label>Giới tính<select value={form.gender} onChange={event => setForm({ ...form, gender: event.target.value })}><option value="">Chưa cung cấp</option><option value="FEMALE">Nữ</option><option value="MALE">Nam</option><option value="OTHER">Khác</option></select></label><label>Ảnh đại diện (URL)<input type="url" maxLength={500} value={form.avatarUrl} onChange={event => setForm({ ...form, avatarUrl: event.target.value })} /></label><label>Giới thiệu<textarea maxLength={500} value={form.bio} onChange={event => setForm({ ...form, bio: event.target.value })} /></label><button className="btn-luxury-primary" disabled={busy}>Lưu thông tin</button></form><form className="account-card account-form" onSubmit={changePassword}><h3>Đổi mật khẩu</h3><p>Sau khi đổi mật khẩu, vui lòng đăng nhập lại trên các thiết bị.</p><label>Mật khẩu hiện tại<input required type="password" autoComplete="current-password" value={password.currentPassword} onChange={event => setPassword({ ...password, currentPassword: event.target.value })} /></label><label>Mật khẩu mới<input required type="password" minLength={8} maxLength={128} autoComplete="new-password" value={password.newPassword} onChange={event => setPassword({ ...password, newPassword: event.target.value })} /></label><button disabled={busy} className="btn-luxury-outline">Đổi mật khẩu</button></form></div>;
}

export function SkinProfile() {
  const empty = { skinType: '', sensitivity: '', allergies: '', contraindications: '', preferredIngredients: '', notes: '' };
  const [form, setForm] = useState(empty);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const load = async () => { try { const data = unwrap(await apiClient.get('/api/v1/users/skin-profile')); setForm({ ...empty, ...(data || {}) }); setLoaded(true); setError(''); } catch (e) { setError(message(e)); } };
  useEffect(() => { load(); }, []);
  return <form className="account-card account-form" onSubmit={async event => { event.preventDefault(); setBusy(true); try { await apiClient.put('/api/v1/users/skin-profile', form); setError('Đã lưu hồ sơ do bạn cung cấp.'); } catch (e) { setError(message(e)); } finally { setBusy(false); } }}><h3>Hồ sơ làn da của bạn</h3><p>Thông tin do bạn cung cấp để chuyên viên tham khảo. Có thể để trống những mục chưa biết và cập nhật sau khi tư vấn.</p>{error && <p role="status">{error}</p>}{!loaded ? <button type="button" onClick={load}>Tải lại hồ sơ</button> : <><label>Loại da<select value={form.skinType || ''} onChange={e => setForm({ ...form, skinType: e.target.value })}><option value="">Chưa xác định</option><option value="DRY">Da khô</option><option value="OILY">Da dầu</option><option value="COMBINATION">Da hỗn hợp</option><option value="NORMAL">Da thường</option><option value="SENSITIVE">Da nhạy cảm</option></select></label>{[['sensitivity', 'Mức độ nhạy cảm'], ['allergies', 'Dị ứng đã biết'], ['contraindications', 'Lưu ý hoặc chống chỉ định đã được tư vấn'], ['preferredIngredients', 'Hoạt chất ưu tiên'], ['notes', 'Thông tin khác']].map(([key, label]) => <label key={key}>{label}<textarea maxLength={key === 'sensitivity' ? 100 : key === 'notes' ? 2000 : 1000} value={form[key] || ''} onChange={event => setForm({ ...form, [key]: event.target.value })} /></label>)}<button className="btn-luxury-primary" disabled={busy}>Lưu hồ sơ da</button></>}</form>;
}

export function NotificationInbox({ onNavigate }) {
  const [data, setData] = useState({ content: [] });
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const load = async () => { try { setData(unwrap(await apiClient.get(`/api/v1/users/notifications?page=${page}&size=20`))); setError(''); } catch (e) { setError(message(e)); } };
  useEffect(() => { load(); }, [page]);
  return <div className="account-stack"><h3>Thông báo của bạn</h3>{error && <p role="alert">{error}<button onClick={load}>Thử lại</button></p>}{listRows(data).map(row => <article className="account-card" key={row.id}><strong>{!row.readAt && '● '}{row.title}</strong><p>{row.message || row.body}</p><small>{row.createdAt && new Date(row.createdAt).toLocaleString('vi-VN')}</small><div className="account-actions">{!row.readAt && <button onClick={async () => { try { await apiClient.put(`/api/v1/users/notifications/${row.id}/read`); await load(); } catch (e) { setError(message(e)); } }}>Đánh dấu đã đọc</button>}{typeof row.target === 'string' && /^(profile|treatments|order-lookup)(\?|$)/.test(row.target) && <button onClick={() => onNavigate(row.target)}>Mở nội dung liên quan</button>}{row.orderId && <button onClick={() => onNavigate(`profile?tab=orders&order=${row.orderId}`)}>Xem đơn hàng</button>}{row.appointmentId && <button onClick={() => onNavigate(`profile?tab=appointments&appointment=${row.appointmentId}`)}>Xem lịch hẹn</button>}{row.courseId && <button onClick={() => onNavigate(`treatments?course=${row.courseId}&tab=courses`)}>Xem lộ trình</button>}</div></article>)}{!listRows(data).length && !error && <p>Chưa có thông báo.</p>}<Pagination page={page} totalPages={data?.totalPages || 1} onChange={setPage} /></div>;
}

export function Pagination({ page, totalPages, onChange }) { return <div className="account-actions" aria-label="Phân trang"><button disabled={page <= 0} onClick={() => onChange(page - 1)}>Trang trước</button><span>Trang {page + 1} / {Math.max(1, totalPages)}</span><button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Trang sau</button></div>; }

export const addressFields = [['recipientName', 'Người nhận'], ['phone', 'Số điện thoại'], ['address', 'Số nhà, tên đường'], ['ward', 'Phường / xã'], ['district', 'Quận / huyện (nếu có)'], ['city', 'Tỉnh / thành phố']];
export function AddressBook({ onSelect }) {
  const blank = Object.fromEntries(addressFields.map(([key]) => [key, '']));
  const [items, setItems] = useState([]);
  const [form, setForm] = useState({ ...blank, isDefault: false });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const load = async () => { try { setItems(listRows(await apiClient.get('/api/v1/users/addresses'))); } catch (e) { setError(message(e)); } };
  useEffect(() => { load(); }, []);
  return <section className="account-stack"><h3>Sổ địa chỉ</h3>{error && <p role="status">{error}</p>}{items.map(row => <article key={row.id} className="account-card"><strong>{row.recipientName} {row.isDefault && '· Mặc định'}</strong><p>{row.phone} · {[row.address, row.ward, row.district, row.city].filter(Boolean).join(', ')}</p><div className="account-actions">{onSelect && <button onClick={() => onSelect(row)}>Giao tới địa chỉ này</button>}<button onClick={() => setForm({ ...blank, ...row })}>Sửa</button><button disabled={busy} onClick={async () => { if (!window.confirm('Xóa địa chỉ này?')) return; setBusy(true); try { await apiClient.delete(`/api/v1/users/addresses/${row.id}`); await load(); } catch (e) { setError(message(e)); } finally { setBusy(false); } }}>Xóa</button></div></article>)}<form className="account-card account-form" onSubmit={async event => { event.preventDefault(); setBusy(true); try { if (form.id) await apiClient.put(`/api/v1/users/addresses/${form.id}`, form); else await apiClient.post('/api/v1/users/addresses', form); setForm({ ...blank, isDefault: false }); await load(); setError('Đã lưu địa chỉ.'); } catch (e) { setError(message(e)); } finally { setBusy(false); } }}><h4>{form.id ? 'Sửa địa chỉ' : 'Thêm địa chỉ'}</h4>{addressFields.map(([key, label]) => <label key={key}>{label}<input required={key !== 'district'} maxLength={key === 'address' ? 500 : 100} type={key === 'phone' ? 'tel' : 'text'} pattern={key === 'phone' ? '0[35789][0-9]{8}' : undefined} value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })} /></label>)}<label><input type="checkbox" checked={form.isDefault} onChange={e => setForm({ ...form, isDefault: e.target.checked })} />Địa chỉ mặc định</label><div className="account-actions"><button disabled={busy} className="btn-luxury-primary">Lưu địa chỉ</button>{form.id && <button type="button" onClick={() => setForm({ ...blank, isDefault: false })}>Hủy sửa</button>}</div></form></section>;
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
