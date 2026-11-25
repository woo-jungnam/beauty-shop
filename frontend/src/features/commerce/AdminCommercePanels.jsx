import React, { useEffect, useState, useCallback } from 'react';
import { apiClient } from '../../shared/api/client';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { useAuth } from '../../app/providers/AuthProvider';
import { formatCurrency, formatDateTime, getOrderStatusBadge, getReturnStatusBadge, getOutreachStatusBadge, getPurchaseOrderStatus, resolveMediaUrl } from '../../shared/utils/formatters';
import { buildVietQrUrl } from '../../shared/utils/vietnamBanks';
import { PaymentExceptionPanel } from '../../pages/PaymentExceptionPanel';
export { ProfitabilityPanel, SpaPerformancePanel } from './DashboardFinancePanels';

const outstandingAmount = (order, operation) => operation === 'cod-settlement' && Number(order?.shipperCodCollected) > 0
  ? Number(order.shipperCodCollected) - Number(order.shipperCodRemitted || 0)
  : Number(order?.totalAmount || 0) - Number(order?.paidAmount || 0);

const data = response => response?.data ?? response;
const documentRef = () => crypto.randomUUID();
const fields = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 12, alignItems: 'end' };
const card = { padding: 20, margin: '20px 0', border: '1px solid var(--border-subtle)', borderRadius: 12 };
function Feedback({ error, message }) { return <>{error && <p role="alert" style={{ color: 'var(--color-danger-700)' }}>{error}</p>}{message && <p role="status">{message}</p>}</>; }
function useCommand() {
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  const run = useCallback(async fn => { setBusy(true); setError(''); setMessage(''); try { await fn(); setMessage('Đã lưu thành công.'); } catch (e) { setError(e.message || 'Không thể lưu.'); } finally { setBusy(false); } }, []);
  return { busy, error, message, run };
}
function Pager({ page, pages, setPage }) { return <div style={{ display: 'flex', gap: 10, marginTop: 12 }}><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</Button><span>Trang {page + 1}/{Math.max(1, pages)}</span><Button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Sau</Button></div>; }

function ReturnReplacementPanel({ rows, onSaved }) {
  const candidates = rows.filter(r => r.status === 'REFUNDED');
  const [id, setId] = useState(''), [replacementOrderId, setReplacementOrderId] = useState(''), [reason, setReason] = useState(''); const command = useCommand();
  if (!candidates.length) return null;
  return <section style={{ marginTop: 20 }}><h3>Đổi hàng qua đơn thay thế</h3><p>Đơn bán thay thế được tạo và thanh toán theo quy trình đơn hàng thông thường của cùng khách. Liên kết này giữ chứng cứ đổi hàng sau khi khoản hoàn đã được xác nhận.</p><form style={fields} onSubmit={e => { e.preventDefault(); command.run(async () => { await apiClient.post(`/api/v1/admin/commerce/returns/${id}/replacement`, { replacementOrderId: Number(replacementOrderId), reason }); await onSaved(); }); }}><Select label="Yêu cầu đã hoàn tiền" value={id} onChange={e => setId(e.target.value)} required><option value="">Chọn yêu cầu</option>{candidates.map(r => <option key={r.id} value={r.id}>#{r.id} · Đơn gốc #{r.orderId}{r.replacementOrderId ? ` · Đã liên kết đơn #${r.replacementOrderId}` : ''}</option>)}</Select><Input label="ID đơn thay thế cùng khách" type="number" min="1" value={replacementOrderId} onChange={e => setReplacementOrderId(e.target.value)} required /><Input label="Lý do và thỏa thuận đổi hàng" value={reason} onChange={e => setReason(e.target.value)} maxLength={500} required /><Button type="submit" loading={command.busy}>Liên kết đơn đổi</Button></form><Feedback {...command} /></section>;
}

function RecallFollowUpPanel({ recall, rows, onChanged }) {
  const [subject, setSubject] = useState(''), [status, setStatus] = useState('CONTACTED'), [outcome, setOutcome] = useState(''), [history, setHistory] = useState([]); const command = useCommand();
  const unique = [...new Map(rows.map(r => [`${r.subjectType}:${r.subjectId}`, r])).entries()];
  const loadHistory = async () => setHistory(data(await apiClient.get(`/api/v1/admin/inventory/recalls/${recall.id}/outreach-history`)) || []);
  return <section style={{ margin: '16px 0' }}><h4>Theo dõi liên hệ và kết quả thu hồi</h4><form style={fields} onSubmit={e => { e.preventDefault(); const [subjectType, subjectId] = subject.split(':'); command.run(async () => { await apiClient.post(`/api/v1/admin/inventory/recalls/${recall.id}/outreach`, { subjectType, subjectId: Number(subjectId), status, outcome }); await onChanged(); await loadHistory(); }); }}><Select label="Đơn / buổi Spa bị ảnh hưởng" value={subject} onChange={e => { setSubject(e.target.value); const row = unique.find(([key]) => key === e.target.value)?.[1]; setStatus(row?.outreachStatus === 'OPEN' ? 'CONTACTED' : row?.outreachStatus || 'CONTACTED'); setOutcome(row?.outreachOutcome || ''); }} required><option value="">Chọn khách liên quan</option>{unique.map(([key, r]) => <option key={key} value={key}>{r.subjectType === 'APPOINTMENT' ? 'Spa' : 'Đơn'} #{r.subjectId} · {r.customerName} · {getOutreachStatusBadge(r.outreachStatus).text}</option>)}</Select><Select label="Tiến độ" value={status} onChange={e => setStatus(e.target.value)}><option value="OPEN">Chưa liên hệ</option><option value="CONTACTED">Đã liên hệ</option><option value="RESOLVED">Đã xử lý xong</option></Select><Input label="Kết quả thực tế / hướng xử lý" value={outcome} onChange={e => setOutcome(e.target.value)} required maxLength={1000} /><Button type="submit" loading={command.busy}>Lưu kết quả</Button><Button variant="outline" onClick={() => command.run(loadHistory)}>Xem lịch sử</Button></form><div className="table-wrapper"><table className="data-table"><thead><tr><th>Thời điểm</th><th>Hồ sơ</th><th>Tiến độ</th><th>Kết quả</th><th>Nhân viên</th></tr></thead><tbody>{history.map((h, i) => <tr key={i}><td>{formatDateTime(h.createdAt)}</td><td>{h.subjectType === 'APPOINTMENT' ? 'Lịch hẹn' : 'Đơn hàng'} #{h.subjectId}</td><td>{getOutreachStatusBadge(h.status).text}</td><td>{h.outcome}</td><td>#{h.actorId}</td></tr>)}</tbody></table></div><Feedback {...command} /></section>;
}

export function CommerceOperationsPanel({ orders = [], onChanged }) {
  const { isAdmin } = useAuth();
  const [rows, setRows] = useState([]), [page, setPage] = useState(0), [pages, setPages] = useState(1), [selected, setSelected] = useState(null);
  const [op, setOp] = useState('decision'), [approved, setApproved] = useState('true'), [reason, setReason] = useState(''), [reference, setReference] = useState(documentRef);
  const [orderId, setOrderId] = useState(''), [orderOp, setOrderOp] = useState('reallocate'), [amount, setAmount] = useState('');
  const command = useCommand(), orderCommand = useCommand();
  const [balanceRevision, setBalanceRevision] = useState(0);
  useEffect(() => {
    if (!orderId || orderOp === 'reallocate') return;
    let cancelled = false;
    apiClient.get(`/api/v1/admin/commerce/orders/${orderId}/balances`).then(response => {
      if (!cancelled) { const balance = data(response); setAmount(String(orderOp === 'overpayment-refund' ? balance.remainingOverpayment : balance.unsettledCod)); }
    }).catch(() => {});
    return () => { cancelled = true; };
  }, [orderId, orderOp, balanceRevision]);
  const run = command.run;
  const load = useCallback(async () => { const result = data(await apiClient.get(`/api/v1/admin/commerce/returns?page=${page}&size=20&sort=id,desc`)); setRows(result.content || []); setPages(result.totalPages || 1); }, [page]);
  useEffect(() => { Promise.resolve().then(load).catch(e => run(() => Promise.reject(e))); }, [load, run]);
  const choose = row => { setSelected(row); setOp(row.status === 'REQUESTED' ? 'decision' : row.status === 'APPROVED' ? 'receive' : 'refund'); setReference(row.status === 'RECEIVED' && row.refundMethod === 'VOUCHER' ? '' : documentRef()); setReason(''); };
  const submit = e => { e.preventDefault(); if (!selected) return; command.run(async () => { await apiClient.post(`/api/v1/admin/commerce/returns/${selected.id}/${op}`, op === 'decision' ? { approved: approved === 'true', reason } : { reference, reason }); await load(); setSelected(null); onChanged?.(); }); };
  const submitOrder = e => { e.preventDefault(); orderCommand.run(async () => { await apiClient.post(`/api/v1/admin/commerce/orders/${orderId}/${orderOp}`, orderOp === 'reallocate' ? {} : { amount: Number(amount), reference, reason }); setReference(documentRef()); setBalanceRevision(value => value + 1); onChanged?.(); }); };
  const selectedVietQr = selected && op === 'refund' && selected.refundMethod !== 'VOUCHER'
    ? buildVietQrUrl({
        bankBin: selected.bankBin,
        accountNumber: selected.accountNumber,
        accountName: selected.accountName,
        amount: selected.refundAmount,
        addInfo: `HOAN TIEN DON #${selected.orderId} YC #${selected.id}`,
      })
    : '';
  return <details className="card" style={card}><summary style={{ fontWeight: 700, cursor: 'pointer' }}>Đổi trả từng sản phẩm, giao thất bại và đối soát COD</summary>
    <p>Hàng khách trả được cách ly để kiểm định. Xác nhận hoàn tiền chỉ ghi nhận khoản đã chuyển thực tế hoặc cấp Voucher hoàn tiền cá nhân. COD chỉ được thu vào sổ khi hãng vận chuyển bàn giao tiền.</p>
    <div className="table-wrapper"><table className="data-table"><thead><tr><th>Yêu cầu / Đơn</th><th>Trạng thái</th><th>Mặt hàng trả</th><th>Lý do / Chứng cứ</th><th>Hoàn tiền / Kênh nhận</th><th>Thao tác</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td>#{row.id} / #{row.orderId}</td><td><span style={{ fontSize: '11.5px', padding: '3px 8px', borderRadius: 4, fontWeight: 600, background: '#F3F4F6' }}>{getReturnStatusBadge(row.status).text}</span></td><td>{row.lines.map(line => `Dòng #${line.orderItemId}: ${line.quantity}`).join(', ')}</td><td><div>{row.reason}</div>{row.evidenceUrls?.length > 0 && <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>{row.evidenceUrls.map((url, i) => <a key={`${url}-${i}`} href={resolveMediaUrl(url)} target="_blank" rel="noreferrer" title={`Ảnh chứng cứ ${i + 1}`}><img src={resolveMediaUrl(url)} alt={`Chứng cứ ${i + 1}`} style={{ width: 42, height: 42, objectFit: 'cover', borderRadius: 6, border: '1px solid #E5E7EB' }} /></a>)}</div>}</td><td><strong>{formatCurrency(row.refundAmount)}</strong><div style={{ fontSize: 12, color: '#4B5563', marginTop: 4 }}>{row.refundMethod === 'VOUCHER' ? 'Voucher hoàn tiền' : 'Chuyển khoản NH'}</div>{row.refundVoucherCode && <div style={{ fontSize: 12, color: '#047857', fontWeight: 700 }}>Mã: {row.refundVoucherCode}</div>}{row.accountNumber && <div style={{ fontSize: 11.5, color: '#374151', marginTop: 2 }}>{row.bankName} · {row.accountNumber} · {row.accountName}</div>}</td><td>{!['REJECTED', 'REFUNDED'].includes(row.status) && <Button size="sm" onClick={() => choose(row)}>Xử lý</Button>}</td></tr>)}</tbody></table>{rows.length === 0 && <p>Chưa có yêu cầu trả hàng.</p>}</div>
    <Pager page={page} pages={pages} setPage={setPage} />
    <ReturnReplacementPanel rows={rows} onSaved={load} />
    {selected && <form onSubmit={submit} style={{ marginTop: 16, padding: 14, border: '1px solid var(--border-subtle)', borderRadius: 10 }}>
      <div style={{ marginBottom: 10 }}><strong>Yêu cầu #{selected.id} · Đơn #{selected.orderId} · Hoàn {formatCurrency(selected.refundAmount)} ({selected.refundMethod === 'VOUCHER' ? 'Voucher' : 'Chuyển khoản'})</strong></div>
      {op === 'refund' && selected.refundMethod === 'VOUCHER' && <p style={{ margin: '0 0 12px', padding: 10, borderRadius: 8, background: '#ECFDF5', color: '#065F46', fontSize: 13 }}>Khách chọn nhận hoàn tiền qua <strong>Voucher cá nhân</strong>. Khi lưu xác nhận, hệ thống tự tạo mã Voucher trị giá <strong>{formatCurrency(selected.refundAmount)}</strong> gắn riêng cho tài khoản khách hàng.</p>}
      {op === 'refund' && selected.refundMethod !== 'VOUCHER' && (selected.accountNumber || selectedVietQr) && <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap', marginBottom: 12, padding: 12, borderRadius: 8, background: '#F8FAFC', border: '1px solid #CBD5E1' }}>
        {selectedVietQr && <img src={selectedVietQr} alt="VietQR hoàn tiền cho khách" style={{ width: 150, height: 'auto', borderRadius: 8, border: '1px solid #E2E8F0', background: '#fff' }} />}
        <div style={{ fontSize: 13, lineHeight: 1.6 }}>
          <div><strong>Thông tin tài khoản nhận hoàn tiền của khách:</strong></div>
          <div>Ngân hàng: {selected.bankName || 'Chưa cung cấp'} {selected.bankBin ? `(BIN: ${selected.bankBin})` : ''}</div>
          <div>Số tài khoản: <code>{selected.accountNumber || '—'}</code></div>
          <div>Chủ tài khoản: <strong>{selected.accountName || '—'}</strong></div>
          <div>Số tiền chuyển: <strong style={{ color: '#B91C1C' }}>{formatCurrency(selected.refundAmount)}</strong></div>
        </div>
      </div>}
      <div style={fields}>
        <Select label="Thao tác" value={op} onChange={e => setOp(e.target.value)}>{selected.status === 'REQUESTED' && <option value="decision">Duyệt yêu cầu</option>}{selected.status === 'APPROVED' && <option value="receive">Xác nhận nhận hàng về</option>}{selected.status === 'RECEIVED' && isAdmin && <option value="refund">{selected.refundMethod === 'VOUCHER' ? 'Xác nhận cấp Voucher hoàn tiền' : 'Ghi nhận đã hoàn tiền'}</option>}</Select>
        {op === 'decision' ? <Select label="Quyết định" value={approved} onChange={e => setApproved(e.target.value)}><option value="true">Chấp thuận</option><option value="false">Từ chối</option></Select> : <Input label={op === 'refund' && selected.refundMethod === 'VOUCHER' ? 'Mã tham chiếu (để trống sẽ tự lấy mã Voucher)' : 'Mã phiếu nhận / giao dịch hoàn'} value={reference} onChange={e => setReference(e.target.value)} required={!(op === 'refund' && selected.refundMethod === 'VOUCHER')} maxLength={100} />}
        <Input label="Lý do / bằng chứng xác nhận" value={reason} onChange={e => setReason(e.target.value)} required maxLength={250} />
        <Button type="submit" loading={command.busy} disabled={op === 'refund' && !isAdmin}>Lưu xác nhận</Button>
      </div>
    </form>}
    <Feedback {...command} />
    <form onSubmit={submitOrder} style={{ ...fields, marginTop: 24 }}><Select label="Đơn hàng đang hiển thị" value={orderId} onChange={e => { const id = e.target.value; setOrderId(id); const row = orders.find(o => String(o.id) === id); setAmount(String(Math.max(0, outstandingAmount(row, orderOp)))); setReference(documentRef()); }} required><option value="">Chọn đơn</option>{orders.filter(o => !o.appointmentId && !o.servicePackageId).map(o => <option key={o.id} value={o.id}>{o.orderNumber} · {getPurchaseOrderStatus(o.status).label}</option>)}</Select><Select label="Nghiệp vụ" value={orderOp} onChange={e => { setOrderOp(e.target.value); const row = orders.find(o => String(o.id) === orderId); setAmount(String(Math.max(0, e.target.value === 'overpayment-refund' ? Number(row?.paidAmount || 0) - Number(row?.refundedAmount || 0) - Number(row?.totalAmount || 0) : outstandingAmount(row, e.target.value)))); }}><option value="reallocate">Đổi lô trước xuất hàng</option>{isAdmin && <><option value="cod-settlement">Xác nhận bàn giao tiền COD</option><option value="overpayment-refund">Ghi nhận hoàn tiền chuyển thừa</option></>}</Select>{orderOp !== 'reallocate' && <><Input label="Số tiền thực tế (VND)" type="number" min="1" step="1" value={amount} onChange={e => setAmount(e.target.value)} required /><Input label="Mã chứng từ ngân hàng" value={reference} onChange={e => setReference(e.target.value)} required maxLength={80} /><Input label="Nội dung xác nhận" value={reason} onChange={e => setReason(e.target.value)} required maxLength={250} /></>}<Button type="submit" loading={orderCommand.busy}>Thực hiện</Button></form><Feedback {...orderCommand} />
    {isAdmin && <PaymentExceptionPanel onChanged={onChanged} />}
  </details>;
}

export function InventoryRecallPanel() {
  const { isAdmin } = useAuth(); const [rows, setRows] = useState([]), [trace, setTrace] = useState([]), [selected, setSelected] = useState(null), [page, setPage] = useState(0), [pages, setPages] = useState(1);
  const [form, setForm] = useState({ reference: documentRef(), variantId: '', batchCode: '', reason: '' }), [closure, setClosure] = useState(''); const command = useCommand();
  const run = command.run;
  const load = useCallback(async () => { const result = data(await apiClient.get(`/api/v1/admin/inventory/recalls?page=${page}&size=20&sort=id,desc`)); setRows(result.content || []); setPages(result.totalPages || 1); }, [page]);
  useEffect(() => { Promise.resolve().then(load).catch(e => run(() => Promise.reject(e))); }, [load, run]);
  const edit = key => e => setForm({ ...form, [key]: e.target.value });
  return <details className="card" style={card}><summary style={{ fontWeight: 700, cursor: 'pointer' }}>Thu hồi lô và truy xuất khách đã mua</summary><p>Khóa lô theo SKU và mã lô ở mọi kho, chặn bán mới và xuất các đơn đã thanh toán. Hàng đang giữ cho đơn cần đổi sang lô khác trước khi xuất.</p><form style={fields} onSubmit={e => { e.preventDefault(); command.run(async () => { await apiClient.post('/api/v1/admin/inventory/recalls', { ...form, variantId: Number(form.variantId) }); await load(); setForm({ reference: documentRef(), variantId: '', batchCode: '', reason: '' }); }); }}><Input label="ID SKU" type="number" min="1" value={form.variantId} onChange={edit('variantId')} required /><Input label="Mã lô chính xác" value={form.batchCode} onChange={edit('batchCode')} required maxLength={100} /><Input label="Mã quyết định thu hồi" value={form.reference} onChange={edit('reference')} required maxLength={100} /><Input label="Lý do thu hồi" value={form.reason} onChange={edit('reason')} required maxLength={500} /><Button type="submit" loading={command.busy}>Khóa và lập đợt thu hồi</Button></form><div className="table-wrapper"><table className="data-table"><thead><tr><th>Đợt thu hồi</th><th>SKU / Lô</th><th>Lý do</th><th>Trạng thái</th><th>Truy xuất</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td>{row.reference}</td><td>#{row.variantId} / {row.batchCode}</td><td>{row.reason}</td><td>{row.active ? 'Đang khóa' : 'Đã đóng'}</td><td><Button size="sm" onClick={() => command.run(async () => { setSelected(row); setTrace(data(await apiClient.get(`/api/v1/admin/inventory/recalls/${row.id}/customers`)) || []); })}>Khách liên quan</Button></td></tr>)}</tbody></table></div><Pager page={page} pages={pages} setPage={setPage} />{selected && <><h4>Khách liên quan lô {selected.batchCode}</h4><div className="table-wrapper"><table className="data-table"><thead><tr><th>Đơn hàng</th><th>Khách / Liên hệ</th><th>Số lượng đã mua / đã trả</th><th>Trạng thái</th></tr></thead><tbody>{trace.map((row, i) => <tr key={i}><td>{row.subjectType === 'APPOINTMENT' ? `Spa #${row.appointmentId}` : row.orderNumber}</td><td>{row.customerName} · {row.customerPhone} · {row.customerEmail || ''}</td><td>{row.quantity} / {row.returnedQuantity}</td><td>{getOrderStatusBadge(row.status).text}</td></tr>)}</tbody></table>{trace.length === 0 && <p>Chưa có khách đã nhận lô này.</p>}</div><RecallFollowUpPanel key={selected.id} recall={selected} rows={trace} onChanged={async () => setTrace(data(await apiClient.get(`/api/v1/admin/inventory/recalls/${selected.id}/customers`)) || [])} />{selected.active && isAdmin && <form style={fields} onSubmit={e => { e.preventDefault(); command.run(async () => { await apiClient.post(`/api/v1/admin/inventory/recalls/${selected.id}/close`, { reason: closure }); setSelected(null); await load(); }); }}><Input label="Lý do cho phép kết thúc thu hồi" value={closure} onChange={e => setClosure(e.target.value)} required maxLength={500} /><Button type="submit" loading={command.busy}>Đóng đợt thu hồi</Button></form>}</>}<Feedback {...command} /></details>;
}

function PurchaseOrderLookup({ busy, onLookup }) {
  const [value, setValue] = useState('');
  return <form style={{ ...fields, margin: '16px 0' }} onSubmit={e => { e.preventDefault(); onLookup(Number(value)); }}>
    <Input label="Tra phiếu mua theo ID (kể cả phiếu cũ)" type="number" min="1" step="1" value={value} onChange={e => setValue(e.target.value)} required />
    <Button type="submit" loading={busy}>Mở phiếu mua</Button>
  </form>;
}

export function ProcurementOperationsPanel({ orders = [], onChanged }) {
  const { isAdmin } = useAuth(); const [id, setId] = useState(''), [detail, setDetail] = useState(null), [financial, setFinancial] = useState(null), [op, setOp] = useState('receipts');
  const [itemId, setItemId] = useState(''), [quantity, setQuantity] = useState('1'), [batchCode, setBatchCode] = useState(''), [expirationDate, setExpirationDate] = useState(''), [amount, setAmount] = useState(''), [reference, setReference] = useState(documentRef), [reason, setReason] = useState(''); const command = useCommand();
  const load = async selectedId => { const [a, b] = await Promise.all([apiClient.get(`/api/v1/admin/procurement/orders/${selectedId}`), apiClient.get(`/api/v1/admin/procurement/orders/${selectedId}/financials`)]); setDetail(data(a)); setFinancial(data(b)); };
  const goods = ['receipts', 'supplier-returns'].includes(op);
  return <details className="card" style={card}><summary style={{ fontWeight: 700, cursor: 'pointer' }}>Nhận từng đợt, trả nhà cung cấp và công nợ</summary><p>Nhập số lượng thực nhận và mã lô thực tế. Chứng từ không được ghi hai lần. Thanh toán nhà cung cấp và thu hồi khoản đã trả thừa cần quản trị viên.</p><PurchaseOrderLookup busy={command.busy} onLookup={selectedId => command.run(async () => { await load(selectedId); setId(String(selectedId)); setItemId(''); })} /><Select label="Phiếu mua hàng" value={id} onChange={e => { const selectedId = e.target.value; setId(selectedId); setFinancial(null); setItemId(''); if (selectedId) command.run(() => load(selectedId)); }}><option value="">Chọn phiếu</option>{id && !orders.some(o => String(o.id) === id) && <option value={id}>{detail?.orderNumber || `#${id}`} / {getPurchaseOrderStatus(detail?.status).label}</option>}{orders.map(o => <option key={o.id} value={o.id}>{o.orderNumber} · {getPurchaseOrderStatus(o.status).label}</option>)}</Select>{financial && <><p>Giá trị đã nhận ròng: <strong>{formatCurrency(financial.receivedValue)}</strong> · Đã trả: <strong>{formatCurrency(financial.paidAmount)}</strong> · {financial.dueAmount < 0 ? 'Cần thu hồi từ NCC' : 'Còn phải trả'}: <strong>{formatCurrency(Math.abs(financial.dueAmount))}</strong></p><form style={fields} onSubmit={e => { e.preventDefault(); command.run(async () => { await apiClient.post(`/api/v1/admin/procurement/orders/${id}/${op}`, goods ? { reference, reason, lines: [{ itemId: Number(itemId), quantity: Number(quantity), batchCode, expirationDate: expirationDate || null }] } : { reference, reason, amount: Number(amount) }); setReference(documentRef()); await load(id); onChanged?.(); }); }}><Select label="Nghiệp vụ" value={op} onChange={e => setOp(e.target.value)}><option value="receipts">Nhận hàng từng phần</option><option value="supplier-returns">Trả hàng cho NCC</option>{isAdmin && <><option value="payments">Xác nhận đã thanh toán NCC</option><option value="supplier-refunds">Xác nhận NCC trả lại tiền</option></>}</Select>{goods ? <><Select label="Mặt hàng theo phiếu" value={itemId} onChange={e => { setItemId(e.target.value); const item = detail?.items?.find(i => String(i.id) === e.target.value); setBatchCode(item?.batchCode || ''); setExpirationDate(item?.expirationDate || ''); }} required><option value="">Chọn mặt hàng</option>{detail?.items?.map(i => <option key={i.id} value={i.id}>SKU #{i.variantId} · Đặt {i.quantity} · Đã nhận {i.receivedQuantity}</option>)}</Select><Input label="Số lượng thực tế" type="number" min="1" step="1" value={quantity} onChange={e => setQuantity(e.target.value)} required /><Input label="Mã lô thực tế" value={batchCode} onChange={e => setBatchCode(e.target.value)} required maxLength={100} /><Input label="Hạn sử dụng" type="date" value={expirationDate} onChange={e => setExpirationDate(e.target.value)} required={op === 'receipts'} /></> : <Input label="Số tiền thực tế (VND)" type="number" min="1" step="1" value={amount} onChange={e => setAmount(e.target.value)} required />}<Input label="Mã chứng từ duy nhất" value={reference} onChange={e => setReference(e.target.value)} required maxLength={100} /><Input label="Nội dung chứng từ" value={reason} onChange={e => setReason(e.target.value)} required maxLength={500} /><Button type="submit" loading={command.busy}>Ghi nhận</Button></form><div className="table-wrapper"><table className="data-table"><thead><tr><th>Thời điểm</th><th>Loại</th><th>Mã chứng từ</th><th>Số tiền</th><th>Nội dung</th></tr></thead><tbody>{financial.movements.map(m => <tr key={m.id}><td>{formatDateTime(m.createdAt)}</td><td>{m.kind}</td><td>{m.reference}</td><td>{formatCurrency(m.amount)}</td><td>{m.reason}</td></tr>)}</tbody></table></div></>}<Feedback {...command} /></details>;
}
