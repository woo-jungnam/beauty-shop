import React, { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { AccountLoading, Pagination } from '../../customer/components/account/AccountTools';
import { unwrap, errorMessage, money } from '../treatments/treatmentApi';
import { movementLabel, ticketDestination } from './benefitModel';
import './spa-benefits.css';
import { PackageRequests } from './PackageRequests';
export const TicketDetails = ({ ticket, onNavigate, administrator = false, onUpdated }) => {
  const [open, setOpen] = useState(false);
  const [snapshot, setSnapshot] = useState(null);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [retry, setRetry] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 60000); return () => clearInterval(timer); }, []);
  useEffect(() => { setPage(0); setSnapshot(null); setError(''); }, [ticket.id]);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true); setError('');
    Promise.all([apiClient.get(`/api/v1/spa/tickets/${ticket.id}/movements?page=${page}&size=10`), apiClient.get(`/api/v1/spa/tickets/${ticket.id}/refunds`)]).then(([a, b]) => {
      if (!active) return;
      const data = unwrap(a), refunds = unwrap(b);
      if (!Array.isArray(data?.content) || !Array.isArray(refunds) || !Number.isSafeInteger(data.totalPages) || data.totalPages < 0) {
        throw new Error('Dữ liệu lịch sử vé không hợp lệ. Vui lòng thử lại.');
      }
      const totalPages = Math.max(1, data.totalPages);
      if (page >= totalPages) { setPage(totalPages - 1); return; }
      setSnapshot({ ticketId: ticket.id, page, history: data.content, refunds, totalPages });
    }).catch(e => { if (active) setError(errorMessage(e)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, ticket.id, page, retry]);
  const current = snapshot?.ticketId === ticket.id && snapshot.page === page ? snapshot : null;
  const usable = ticket.status === 'ACTIVE' && ticket.remainingSessions > 0 && (!ticket.expiryDate || Date.parse(ticket.expiryDate) > now);
  return <div className="spa-ticket-detail">
    {ticket.benefitType !== 'TREATMENT_COURSE' && (onNavigate || administrator) && <PackageRequests ticket={ticket} admin={administrator} onUpdated={onUpdated} />}
    {!ticket.nameSnapshot && <small>Tên hiện tại; vé cũ chưa lưu tên lúc mua.</small>}
    <p>{ticket.benefitType === 'TREATMENT_COURSE' ? 'Quyền theo lộ trình chăm sóc' : 'Gói nhiều lượt thông thường'} · Đang giữ: {ticket.reservedSessions ?? 'Chưa xác định'} lượt</p>
    <ul>{(ticket.services || []).map(s => <li key={s.serviceId}><strong>{s.serviceName}</strong>: {s.available} {usable ? 'khả dụng' : 'chưa dùng (vé không khả dụng)'} / {s.total} tổng · {s.used} đã dùng hoặc mất lượt · {s.reserved} đang giữ</li>)}</ul>
    <div className="spa-benefit-actions">{onNavigate && <button type="button" className="btn-luxury-outline" disabled={ticket.benefitType !== 'TREATMENT_COURSE' && !usable} onClick={() => onNavigate(ticketDestination(ticket))}>Mở {ticket.benefitType === 'TREATMENT_COURSE' ? 'lộ trình chăm sóc' : 'đặt lịch sử dụng vé'}</button>}<button type="button" className="btn-luxury-outline" aria-expanded={open} onClick={() => { setError(''); setOpen(v => !v); }}>{open ? 'Ẩn' : 'Xem'} lịch sử vé</button></div>
    {open && <div aria-busy={loading}>
      {error && <p role="alert">{error} <button type="button" disabled={loading} onClick={() => setRetry(value => value + 1)}>Thử lại lịch sử vé</button></p>}
      {!current && !error && <AccountLoading label="Đang tải lịch sử vé…" count={2} />}
      {current && loading && <p role="status">Đang cập nhật lịch sử vé…</p>}
      {current?.history.map(m => <p key={m.id}>{new Date(m.createdAt).toLocaleString('vi-VN')} · {movementLabel(m.operation)}{m.serviceId ? ` · Dịch vụ #${m.serviceId}` : ''}<br /><small>{m.reason}</small></p>)}
      {current && !loading && !error && !current.history.length && <p>Chưa có biến động được ghi nhận.</p>}
      <Pagination page={page} totalPages={snapshot?.ticketId === ticket.id ? snapshot.totalPages : 1} disabled={loading || !current} onChange={setPage} label="Phân trang lịch sử vé" />
      {current?.refunds.map((r, i) => <p key={r.id ?? i}>Quyết toán: {money(r.amount)} · Chứng từ {r.reference} · {new Date(r.createdAt).toLocaleString('vi-VN')}<br />{r.reason}</p>)}
    </div>}
  </div>;
};
