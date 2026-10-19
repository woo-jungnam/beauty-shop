import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../../shared/api/client';
import { Modal } from '../../../shared/ui/Modal';
import { getGuestSessionId, useCustomerCart } from '../../stores/customerCartStore';
import { Pagination, listRows, unwrap } from './AccountTools';

export const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(value || 0));
const statuses = { PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PROCESSING: 'Đang xử lý', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy', RETURNED: 'Đã trả hàng', COMPLETED: 'Hoàn tất', DELIVERY_FAILED: 'Giao không thành công', RETURN_TO_SENDER: 'Hàng chuyển về shop', REQUESTED: 'Chờ xét duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối', RECEIVED: 'Đã nhận hàng trả', REFUNDED: 'Đã hoàn tiền', REFUND_PENDING: 'Chờ hoàn tiền', PAID: 'Đã thanh toán' };
export function OrderWorkspace({ onNavigate, initialOrderId }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [] });
  const [selected, setSelected] = useState(initialOrderId || null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const load = async () => { setLoading(true); try { setData(unwrap(await apiClient.get(`/api/v1/orders/my-orders?page=${page}&size=20&sort=createdAt,desc`))); setError(''); } catch (e) { setError(e.message); } finally { setLoading(false); } };
  useEffect(() => { load(); }, [page]);
  useEffect(() => { if (initialOrderId) setSelected(initialOrderId); }, [initialOrderId]);
  return <div className="account-stack"><h3>Đơn hàng của tôi</h3>{error && <p role="alert">{error}</p>}<button onClick={load} disabled={loading}>Cập nhật đơn hàng</button>{listRows(data).map(order => <article className="account-card" key={order.id}><strong>{order.orderNumber}</strong><p>{order.createdAt && new Date(order.createdAt).toLocaleString('vi-VN')} · {statuses[order.status] || order.status} · {statuses[order.paymentStatus] || order.paymentStatus}</p><p>{(order.items || []).map(item => `${item.productName} × ${item.quantity}`).join(', ') || 'Đơn dịch vụ Spa'}</p><strong>{money(order.totalAmount)}</strong><div className="account-actions"><button onClick={() => setSelected(order.id)}>Chi tiết & thao tác</button></div></article>)}{!loading && !error && !listRows(data).length && <p>Bạn chưa có đơn hàng.</p>}<Pagination page={page} totalPages={data.totalPages || 1} onChange={setPage} />{selected && <OrderDetailDialog orderId={selected} onClose={() => setSelected(null)} onChanged={load} onNavigate={onNavigate} />}</div>;
}

export function OrderDetailDialog({ orderId, onClose, onChanged, onNavigate }) {
  const { fetchCart, setCartOpen } = useCustomerCart();
  const [order, setOrder] = useState(null);
  const [returns, setReturns] = useState([]);
  const [quantities, setQuantities] = useState({});
  const [reason, setReason] = useState('');
  const returnReplay = useRef({ fingerprint: '', key: '' });
  const operationLock = useRef(false);
  const [evidence, setEvidence] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const load = async () => {
    const results = await Promise.allSettled([apiClient.get(`/api/v1/orders/${orderId}`, { headers: { 'X-Guest-Session-Id': getGuestSessionId() } }), apiClient.get(`/api/v1/orders/${orderId}/returns`, { headers: { 'X-Guest-Session-Id': getGuestSessionId() } })]);
    if (results[0].status === 'fulfilled') setOrder(unwrap(results[0].value)); else setError(results[0].reason.message);
    if (results[1].status === 'fulfilled') setReturns(listRows(results[1].value)); else setError(`Chưa tải được yêu cầu đổi trả. ${results[1].reason.message}`);
  };
  useEffect(() => { load(); }, [orderId]);
  const run = async work => { if (operationLock.current) return; operationLock.current = true; setBusy(true); setError(''); try { await work(); await load(); await onChanged?.(); } catch (e) { setError(e.message); } finally { operationLock.current = false; setBusy(false); } };
  const reorder = async () => {
    const failed = []; let added = 0;
    for (const item of order.items || []) {
      if (!item.variantId) { failed.push(item.productName); continue; }
      try { await apiClient.post('/api/v1/cart/add', { variantId: item.variantId, quantity: item.quantity, sessionId: getGuestSessionId() }); added++; } catch (e) { failed.push(`${item.productName}: ${e.message}`); }
    }
    if (added) { await fetchCart(); setCartOpen(true); }
    if (failed.length) throw new Error(`Đã thêm ${added} mặt hàng. Không thể thêm: ${failed.join('; ')}. Giá và tồn kho áp dụng tại thời điểm mua lại.`);
    setError('Đã thêm các mặt hàng vào giỏ với giá hiện tại.');
  };
  const reserved = itemId => returns.filter(row => row.status !== 'REJECTED').reduce((sum, row) => sum + (row.lines || []).filter(line => Number(line.orderItemId) === Number(itemId)).reduce((n, line) => n + Number(line.quantity), 0), 0);
  return <Modal isOpen onClose={() => { if (!busy) onClose(); }} title={order?.orderNumber || `Đơn hàng #${orderId}`} maxWidth="860px"><div className="account-stack">{error && <p role="status">{error}</p>}<button disabled={busy} onClick={load}>Tải lại chi tiết</button>{order ? <><p className="account-status">{statuses[order.status] || order.status} · {statuses[order.paymentStatus] || order.paymentStatus}</p><p>{order.customerName} · {order.customerPhone}<br />{order.shippingAddress}</p>{order.carrierName && <p>Đơn vị vận chuyển: {order.carrierName} · Mã vận đơn: <strong>{order.trackingCode || 'Chưa có'}</strong></p>}<table className="account-order-lines"><thead><tr><th>Sản phẩm</th><th>Số lượng</th><th>Thành tiền</th></tr></thead><tbody>{(order.items || []).map(item => <tr key={item.id}><td>{item.productName}<br /><small>{item.variantName || item.sku}</small></td><td>{item.quantity}</td><td>{money((Number(item.price) - Number(item.discount || 0)) * item.quantity)}</td></tr>)}</tbody></table>{(order.spaVisitItems || []).map((item, index) => <p key={index}>{item.serviceName} · {money(item.amount || item.price)}</p>)}<p>Tổng đơn: {money(order.totalAmount)} · Đã thu: {money(order.paidAmount)} · Đã hoàn: {money(order.refundedAmount)}</p>{order.paymentDeadline && <p>Hạn thanh toán: {new Date(order.paymentDeadline).toLocaleString('vi-VN')}</p>}{order.cancelReason && <p>Lý do hủy: {order.cancelReason}</p>}<div className="account-actions">{['PENDING', 'CONFIRMED'].includes(order.status) && <button disabled={busy} onClick={() => { if (window.confirm('Hủy đơn hàng này?')) run(() => apiClient.delete(`/api/v1/orders/${order.id}/cancel`)); }}>Hủy đơn</button>}{order.paymentMethod === 'BANK' && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (!order.paymentDeadline || Date.parse(order.paymentDeadline) > Date.now()) && <button onClick={() => { onClose(); onNavigate(`order-success?id=${order.id}&method=BANK`); }}>Tiếp tục thanh toán</button>}{order.items?.length > 0 && <button disabled={busy} onClick={() => run(reorder)}>Mua lại theo giá hiện tại</button>}</div><details><summary>Lịch sử trạng thái</summary>{(order.statusHistories || []).map((row, index) => <p key={row.id || index}>{row.createdAt && new Date(row.createdAt).toLocaleString('vi-VN')} · {statuses[row.status] || row.status || row.toStatus} {row.notes || row.reason}</p>)}</details><section className="account-stack"><h3>Yêu cầu đổi trả</h3>{returns.map(row => <article className="account-card" key={row.id}><strong>#{row.id} · {statuses[row.status] || row.status}</strong><p>{row.reason}</p><p>{(row.lines || []).map(line => `${order.items?.find(item => Number(item.id) === Number(line.orderItemId))?.productName || `Dòng #${line.orderItemId}`} × ${line.quantity}`).join(', ')}</p>{row.decisionReason && <p>Phản hồi: {row.decisionReason}</p>}<p>Tiền hoàn dự kiến/đã duyệt: {money(row.refundAmount)}</p></article>)}{order.status === 'DELIVERED' && <form className="account-form" onSubmit={event => { event.preventDefault(); const lines = Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0).map(([id, quantity]) => ({ orderItemId: Number(id), quantity: Number(quantity) })); if (!lines.length) { setError('Chọn ít nhất một sản phẩm cần đổi trả.'); return; } run(async () => { const payload = { reason: reason.trim(), lines, evidenceUrls: evidence.split('\n').map(url => url.trim()).filter(Boolean) }; if (payload.evidenceUrls.length > 8 || payload.evidenceUrls.some(url => !/^https?:\/\//i.test(url))) throw new Error('Dùng tối đa 8 đường dẫn ảnh HTTP hoặc HTTPS.'); const fingerprint = JSON.stringify(payload); if (returnReplay.current.fingerprint !== fingerprint) returnReplay.current = { fingerprint, key: crypto.randomUUID() }; await apiClient.post(`/api/v1/orders/${order.id}/returns`, payload, { headers: { 'X-Guest-Session-Id': getGuestSessionId(), 'Idempotency-Key': returnReplay.current.key } }); returnReplay.current = { fingerprint: '', key: '' }; setReason(''); setEvidence(''); setQuantities({}); }); }}><h4>Gửi yêu cầu mới</h4>{(order.items || []).map(item => { const max = Math.max(0, item.quantity - reserved(item.id)); return <label key={item.id}>{item.productName} · Có thể yêu cầu: {max}<input type="number" min={0} max={max} step={1} disabled={max === 0 || busy} value={quantities[item.id] || 0} onChange={event => setQuantities({ ...quantities, [item.id]: event.target.value })} /></label>; })}<label>Lý do<textarea required minLength={5} maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} /></label><label>Ảnh/chứng từ (mỗi URL HTTPS một dòng)<textarea value={evidence} onChange={event => setEvidence(event.target.value)} /></label><p>Yêu cầu được nhân viên xét duyệt; số tiền hoàn hiển thị theo kết quả xử lý.</p><button disabled={busy}>Gửi yêu cầu đổi trả</button></form>}</section></> : <p>Đang tải đơn hàng…</p>}</div></Modal>;
}
