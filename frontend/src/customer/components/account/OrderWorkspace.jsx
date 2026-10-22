import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../../shared/api/client';
import { Modal } from '../../../shared/ui/Modal';
import { getGuestSessionId, useCustomerCart } from '../../stores/customerCartStore';
import { Pagination, listRows, unwrap } from './AccountTools';
import { resolveMediaUrl } from '../../../shared/utils/formatters';

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
  return (
    <div className="account-stack">
      <h3>Đơn hàng của tôi</h3>
      {error && <p role="alert">{error}</p>}
      <button onClick={load} disabled={loading}>Cập nhật đơn hàng</button>
      {listRows(data).map(order => (
        <article className="account-card" key={order.id} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-subtle)', paddingBottom: 8 }}>
            <strong>{order.orderNumber}</strong>
            <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--c-primary)' }}>
              {statuses[order.status] || order.status} · {statuses[order.paymentStatus] || order.paymentStatus}
            </span>
          </div>

          <p style={{ margin: 0, fontSize: 12, color: 'var(--text-muted)' }}>
            {order.createdAt && new Date(order.createdAt).toLocaleString('vi-VN')}
          </p>

          <div style={{ display: 'flex', gap: 8, overflowX: 'auto', padding: '4px 0' }}>
            {(order.items || []).map((item, idx) => {
              const img = item.imageUrl ? resolveMediaUrl(item.imageUrl) : null;
              return (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 8, backgroundColor: '#FAFAFA', padding: '6px 10px', borderRadius: 6, border: '1px solid #E5E7EB', flexShrink: 0, maxWidth: 260 }}>
                  {img && <img src={img} alt="" style={{ width: 36, height: 36, objectFit: 'cover', borderRadius: 4 }} onError={e => { e.currentTarget.style.display = 'none'; }} />}
                  <div style={{ minWidth: 0, overflow: 'hidden' }}>
                    <div style={{ fontSize: 12, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.productName}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{item.variantName ? `${item.variantName} · ` : ''}x{item.quantity}</div>
                  </div>
                </div>
              );
            })}
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
            <div>Tổng thanh toán: <strong style={{ color: 'var(--c-deal-red, #E11D48)', fontFamily: 'monospace', fontSize: 15 }}>{money(order.totalAmount)}</strong></div>
            <button className="btn-luxury-outline" style={{ padding: '6px 14px', fontSize: 12 }} onClick={() => setSelected(order.id)}>Chi tiết & thao tác</button>
          </div>
        </article>
      ))}
      {!loading && !error && !listRows(data).length && <p>Bạn chưa có đơn hàng.</p>}
      <Pagination page={page} totalPages={data.totalPages || 1} onChange={setPage} />
      {selected && <OrderDetailDialog orderId={selected} onClose={() => setSelected(null)} onChanged={load} onNavigate={onNavigate} />}
    </div>
  );
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
  return (
    <Modal isOpen onClose={() => { if (!busy) onClose(); }} title={order?.orderNumber || `Đơn hàng #${orderId}`} maxWidth="860px">
      <div className="account-stack">
        {error && <p role="status">{error}</p>}
        <button disabled={busy} onClick={load}>Tải lại chi tiết</button>
        {order ? (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#F9FAFB', padding: '12px 16px', borderRadius: 8, border: '1px solid #E5E7EB' }}>
              <div>
                <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--c-primary)' }}>
                  {statuses[order.status] || order.status} · {statuses[order.paymentStatus] || order.paymentStatus}
                </span>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                  Ngày tạo: {order.createdAt && new Date(order.createdAt).toLocaleString('vi-VN')}
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Mã đơn hàng</span>
                <div style={{ fontFamily: 'monospace', fontWeight: 800, fontSize: 15, color: 'var(--c-deal-red)' }}>{order.orderNumber}</div>
              </div>
            </div>

            <div style={{ backgroundColor: '#FFFFFF', padding: 14, borderRadius: 8, border: '1px solid #E5E7EB', fontSize: 13 }}>
              <strong>Địa chỉ nhận hàng:</strong>
              <div style={{ marginTop: 4 }}>
                {order.customerName} · {order.customerPhone}
              </div>
              <div style={{ color: 'var(--text-muted)', marginTop: 2 }}>
                {order.shippingAddress}
              </div>
              {order.carrierName && (
                <div style={{ marginTop: 6, paddingTop: 6, borderTop: '1px dashed #E5E7EB', color: 'var(--c-primary)' }}>
                  Đơn vị vận chuyển: <strong>{order.carrierName}</strong> · Mã vận đơn: <strong style={{ fontFamily: 'monospace' }}>{order.trackingCode || 'Đang cập nhật'}</strong>
                </div>
              )}
            </div>

            <table className="account-order-lines" style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #E5E7EB', textAlign: 'left' }}>
                  <th style={{ width: 56, padding: '8px 4px' }}>Ảnh</th>
                  <th style={{ padding: '8px 6px' }}>Sản phẩm / Phân loại</th>
                  <th style={{ textAlign: 'right', padding: '8px 6px' }}>Đơn giá</th>
                  <th style={{ textAlign: 'center', width: 60, padding: '8px 4px' }}>Số lượng</th>
                  <th style={{ textAlign: 'right', padding: '8px 6px' }}>Thành tiền</th>
                </tr>
              </thead>
              <tbody>
                {(order.items || []).map(item => {
                  const unitPrice = Number(item.price || 0);
                  const discount = Number(item.discount || 0);
                  const effective = Math.max(0, unitPrice - discount);
                  const lineTotal = effective * Number(item.quantity || 1);
                  const img = item.imageUrl ? resolveMediaUrl(item.imageUrl) : null;
                  const productLink = item.productId ? `#/product/${item.productId}` : null;

                  return (
                    <tr key={item.id} style={{ borderBottom: '1px solid #F3F4F6' }}>
                      <td style={{ padding: '8px 4px' }}>
                        <div style={{ width: 44, height: 44, borderRadius: 4, overflow: 'hidden', backgroundColor: '#F3F4F6', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          {img ? (
                            <img src={img} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={e => { e.currentTarget.style.display = 'none'; }} />
                          ) : (
                            <span style={{ fontSize: 10, color: '#9CA3AF' }}>Mỹ phẩm</span>
                          )}
                        </div>
                      </td>
                      <td style={{ padding: '8px 6px' }}>
                        {productLink ? (
                          <a href={productLink} style={{ fontWeight: 600, color: 'var(--c-primary)', textDecoration: 'none' }} onClick={() => onClose()}>
                            {item.productName}
                          </a>
                        ) : (
                          <span style={{ fontWeight: 600, color: '#1F2937' }}>{item.productName}</span>
                        )}
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2, display: 'flex', gap: 8 }}>
                          {item.variantName && <span>Phân loại: <strong>{item.variantName}</strong></span>}
                          {item.sku && <span>SKU: <code style={{ fontSize: 10 }}>{item.sku}</code></span>}
                        </div>
                      </td>
                      <td style={{ textAlign: 'right', padding: '8px 6px' }}>
                        {discount > 0 ? (
                          <div>
                            <div style={{ fontWeight: 600 }}>{money(effective)}</div>
                            <div style={{ fontSize: 10.5, color: '#9CA3AF', textDecoration: 'line-through' }}>{money(unitPrice)}</div>
                          </div>
                        ) : (
                          <span>{money(unitPrice)}</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'center', fontWeight: 600, padding: '8px 4px' }}>{item.quantity}</td>
                      <td style={{ textAlign: 'right', fontWeight: 700, fontFamily: 'monospace', padding: '8px 6px' }}>
                        {money(lineTotal)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {(order.spaVisitItems || []).map((item, index) => (
              <div key={index} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', backgroundColor: '#FFF5F7', borderRadius: 6, fontSize: 13 }}>
                <span>Dịch vụ Spa: <strong>{item.serviceName}</strong></span>
                <span style={{ fontWeight: 700, fontFamily: 'monospace' }}>{money(item.amount || item.price)}</span>
              </div>
            ))}

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end', paddingTop: 10, borderTop: '2px solid #E5E7EB', fontSize: 13 }}>
              {order.subTotal != null && (
                <div style={{ display: 'flex', gap: 24 }}>
                  <span style={{ color: 'var(--text-muted)' }}>Tạm tính tiền hàng:</span>
                  <span style={{ fontFamily: 'monospace' }}>{money(order.subTotal)}</span>
                </div>
              )}
              {order.discountAmount != null && Number(order.discountAmount) > 0 && (
                <div style={{ display: 'flex', gap: 24, color: '#059669' }}>
                  <span>Voucher giảm giá:</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>-{money(order.discountAmount)}</span>
                </div>
              )}
              {order.shippingFee != null && (
                <div style={{ display: 'flex', gap: 24 }}>
                  <span style={{ color: 'var(--text-muted)' }}>Phí vận chuyển:</span>
                  <span style={{ fontFamily: 'monospace' }}>{Number(order.shippingFee) === 0 ? 'MIỄN PHÍ' : money(order.shippingFee)}</span>
                </div>
              )}
              <div style={{ display: 'flex', gap: 24, fontSize: 16, fontWeight: 800, color: 'var(--c-primary)', paddingTop: 4, borderTop: '1px dashed #E5E7EB' }}>
                <span>Tổng thanh toán:</span>
                <span style={{ color: 'var(--c-deal-red)', fontFamily: 'monospace' }}>{money(order.totalAmount)}</span>
              </div>
              <div style={{ display: 'flex', gap: 24, color: '#059669', fontSize: 12 }}>
                <span>Đã thanh toán:</span>
                <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{money(order.paidAmount || 0)}</span>
              </div>
              {order.refundedAmount != null && Number(order.refundedAmount) > 0 && (
                <div style={{ display: 'flex', gap: 24, color: '#DC2626', fontSize: 12 }}>
                  <span>Đã hoàn tiền:</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>-{money(order.refundedAmount)}</span>
                </div>
              )}
            </div>

            {order.paymentDeadline && <p style={{ fontSize: 12, color: '#B45309' }}>Hạn thanh toán: {new Date(order.paymentDeadline).toLocaleString('vi-VN')}</p>}
            {order.cancelReason && <p style={{ fontSize: 12, color: '#DC2626' }}>Lý do hủy: {order.cancelReason}</p>}

            <div className="account-actions">
              {['PENDING', 'CONFIRMED'].includes(order.status) && (
                <button disabled={busy} onClick={() => { if (window.confirm('Hủy đơn hàng này?')) run(() => apiClient.delete(`/api/v1/orders/${order.id}/cancel`)); }}>
                  Hủy đơn
                </button>
              )}
              {order.paymentMethod === 'BANK' && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (!order.paymentDeadline || Date.parse(order.paymentDeadline) > Date.now()) && (
                <button onClick={() => { onClose(); onNavigate(`order-success?id=${order.id}&method=BANK`); }}>
                  Tiếp tục thanh toán
                </button>
              )}
              {order.items?.length > 0 && (
                <button disabled={busy} onClick={() => run(reorder)}>
                  Mua lại theo giá hiện tại
                </button>
              )}
            </div>

            <details>
              <summary>Lịch sử trạng thái</summary>
              {(order.statusHistories || []).map((row, index) => (
                <p key={row.id || index}>
                  {row.createdAt && new Date(row.createdAt).toLocaleString('vi-VN')} · {statuses[row.status] || row.status || row.toStatus} {row.notes || row.reason}
                </p>
              ))}
            </details>

            <section className="account-stack">
              <h3>Yêu cầu đổi trả</h3>
              {returns.map(row => (
                <article className="account-card" key={row.id}>
                  <strong>#{row.id} · {statuses[row.status] || row.status}</strong>
                  <p>{row.reason}</p>
                  <p>{(row.lines || []).map(line => `${order.items?.find(item => Number(item.id) === Number(line.orderItemId))?.productName || `Dòng #${line.orderItemId}`} × ${line.quantity}`).join(', ')}</p>
                  {row.decisionReason && <p>Phản hồi: {row.decisionReason}</p>}
                  <p>Tiền hoàn dự kiến/đã duyệt: {money(row.refundAmount)}</p>
                </article>
              ))}
              {order.status === 'DELIVERED' && (
                <form className="account-form" onSubmit={event => {
                  event.preventDefault();
                  const lines = Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0).map(([id, quantity]) => ({ orderItemId: Number(id), quantity: Number(quantity) }));
                  if (!lines.length) { setError('Chọn ít nhất một sản phẩm cần đổi trả.'); return; }
                  run(async () => {
                    const payload = { reason: reason.trim(), lines, evidenceUrls: evidence.split('\n').map(url => url.trim()).filter(Boolean) };
                    if (payload.evidenceUrls.length > 8 || payload.evidenceUrls.some(url => !/^https?:\/\//i.test(url))) throw new Error('Dùng tối đa 8 đường dẫn ảnh HTTP hoặc HTTPS.');
                    const fingerprint = JSON.stringify(payload);
                    if (returnReplay.current.fingerprint !== fingerprint) returnReplay.current = { fingerprint, key: crypto.randomUUID() };
                    await apiClient.post(`/api/v1/orders/${order.id}/returns`, payload, { headers: { 'X-Guest-Session-Id': getGuestSessionId(), 'Idempotency-Key': returnReplay.current.key } });
                    returnReplay.current = { fingerprint: '', key: '' };
                    setReason(''); setEvidence(''); setQuantities({});
                  });
                }}>
                  <h4>Gửi yêu cầu mới</h4>
                  {(order.items || []).map(item => {
                    const max = Math.max(0, item.quantity - reserved(item.id));
                    return (
                      <label key={item.id}>
                        {item.productName} · Có thể yêu cầu: {max}
                        <input type="number" min={0} max={max} step={1} disabled={max === 0 || busy} value={quantities[item.id] || 0} onChange={event => setQuantities({ ...quantities, [item.id]: event.target.value })} />
                      </label>
                    );
                  })}
                  <label>Lý do<textarea required minLength={5} maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} /></label>
                  <label>Ảnh/chứng từ (mỗi URL HTTPS một dòng)<textarea value={evidence} onChange={event => setEvidence(event.target.value)} /></label>
                  <p>Yêu cầu được nhân viên xét duyệt; số tiền hoàn hiển thị theo kết quả xử lý.</p>
                  <button disabled={busy}>Gửi yêu cầu đổi trả</button>
                </form>
              )}
            </section>
          </>
        ) : (
          <p>Đang tải đơn hàng…</p>
        )}
      </div>
    </Modal>
  );
}
