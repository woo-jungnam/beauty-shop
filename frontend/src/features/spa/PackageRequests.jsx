import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { unwrap, errorMessage } from '../treatments/treatmentApi';

const labels = { PENDING_APPROVAL: 'Chờ admin duyệt', APPROVED: 'Đã gia hạn', REJECTED: 'Đã từ chối', AWAITING_SETTLEMENT: 'Đã duyệt dừng, chờ quyết toán và chứng từ hoàn', COMPLETED: 'Đã quyết toán' };
export const PackageRequests = ({ ticket, admin = false, onUpdated }) => {
  const [open, setOpen] = useState(false), [requests, setRequests] = useState([]);
  const [type, setType] = useState('EXTEND'), [days, setDays] = useState(30), [reason, setReason] = useState('');
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [reviewReason, setReviewReason] = useState('');
  const lock = useRef(false);
  const root = `/api/v1/${admin ? 'admin/' : ''}spa/tickets/${ticket.id}/requests`;
  const load = async () => setRequests(unwrap(await apiClient.get(root)) || []);
  const run = async action => {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try { await action(); } catch (e) { setError(errorMessage(e)); }
    finally { lock.current = false; setBusy(false); }
  };
  const create = event => {
    event.preventDefault();
    run(async () => { await apiClient.post(root, { type, reason, ...(type === 'EXTEND' ? { extensionDays: Number(days) } : {}) }); setReason(''); await load(); });
  };
  const review = (request, approve) => run(async () => {
    await apiClient.post(`${root}/${request.id}/review`, { approve, reason: reviewReason });
    setReviewReason(''); await load(); onUpdated?.();
  });
  return <div className="spa-package-requests">
    <button type="button" disabled={busy} onClick={() => { if (!open) run(load); setOpen(v => !v); }}>{open ? 'Ẩn yêu cầu' : 'Yêu cầu gia hạn / dừng gói'}</button>
    {open && <div aria-live="polite">
      {error && <p role="alert">{error}</p>}
      <p>Gia hạn chỉ có hiệu lực sau khi admin duyệt. Dừng gói cần quyết toán phần chưa dùng và chứng từ hoàn tiền riêng. Hãy hủy các lịch đang giữ lượt trước khi quyết toán.</p>
      {requests.map(request => <div key={request.id}>
        <strong>{request.type === 'EXTEND' ? `Gia hạn ${request.extensionDays} ngày` : 'Dừng / hoàn tiền'} · {labels[request.status] || request.status}</strong>
        <p>{request.reason}{request.reviewReason ? ` · Phản hồi: ${request.reviewReason}` : ''}</p>
        {admin && request.status === 'PENDING_APPROVAL' && <div>
          <label>Lý do xử lý<textarea maxLength={1000} value={reviewReason} onChange={e => setReviewReason(e.target.value)} /></label>
          <button type="button" disabled={busy || !reviewReason.trim()} onClick={() => review(request, true)}>Duyệt yêu cầu</button>
          <button type="button" disabled={busy || !reviewReason.trim()} onClick={() => review(request, false)}>Từ chối</button>
        </div>}
        {admin && request.status === 'AWAITING_SETTLEMENT' && <p>Dùng nút “Quyết toán” của vé để kiểm tra số hoàn hiện tại và xác nhận chứng từ.</p>}
      </div>)}
      {!busy && !requests.length && <p>Chưa có yêu cầu.</p>}
      {!admin && ticket.status !== 'REVOKED' && !requests.some(r => ['PENDING_APPROVAL', 'AWAITING_SETTLEMENT'].includes(r.status)) && <form onSubmit={create}>
        <label>Loại yêu cầu<select value={type} onChange={e => setType(e.target.value)}><option value="EXTEND" disabled={!ticket.expiryDate}>Gia hạn</option><option value="CANCEL">Dừng / hoàn tiền</option></select></label>
        {type === 'EXTEND' && <label>Số ngày<input type="number" min={1} max={3650} required value={days} onChange={e => setDays(e.target.value)} /></label>}
        <label>Lý do<textarea required maxLength={1000} value={reason} onChange={e => setReason(e.target.value)} /></label>
        <button type="submit" disabled={busy || (type === 'EXTEND' && !ticket.expiryDate)}>Gửi yêu cầu</button>
      </form>}
    </div>}
  </div>;
};

export const PackageRequestQueue = ({ onUpdated, onSettle }) => {
  const [requests, setRequests] = useState([]), [error, setError] = useState('');
  const load = async () => {
    try { setRequests(unwrap(await apiClient.get('/api/v1/admin/spa/package-requests')) || []); setError(''); }
    catch (e) { setError(errorMessage(e)); }
  };
  useEffect(() => { load(); }, []);
  const refresh = () => { load(); onUpdated?.(); };
  return <section className="spa-ticket-detail">
    <strong>Yêu cầu gói nhiều lượt cần xử lý</strong> <button type="button" onClick={load}>Tải lại yêu cầu</button>
    {error && <p role="alert">{error}</p>}
    {requests.map(request => <div key={request.id}>
      <p>Vé #{request.ticketId} · Khách #{request.userId} · {request.type === 'EXTEND' ? `Gia hạn ${request.extensionDays} ngày` : 'Dừng / hoàn tiền'} · {labels[request.status]}</p>
      <PackageRequests ticket={{ id: request.ticketId }} admin onUpdated={refresh} />
      {request.status === 'AWAITING_SETTLEMENT' && <button type="button" onClick={() => onSettle?.(request.ticketId)}>Mở quyết toán vé #{request.ticketId}</button>}
    </div>)}
    {!requests.length && !error && <p>Không có yêu cầu đang chờ xử lý.</p>}
  </section>;
};
