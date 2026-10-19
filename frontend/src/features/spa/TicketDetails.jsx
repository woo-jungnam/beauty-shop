import React, { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { unwrap, errorMessage, money } from '../treatments/treatmentApi';
import { movementLabel, ticketDestination } from './benefitModel';
import './spa-benefits.css';
export const TicketDetails = ({ ticket, onNavigate }) => {
  const [open, setOpen] = useState(false), [history, setHistory] = useState([]), [refunds, setRefunds] = useState([]), [error, setError] = useState(''), [page, setPage] = useState(0), [more, setMore] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 60000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    if (!open) return; let active = true;
    Promise.all([apiClient.get(`/api/v1/spa/tickets/${ticket.id}/movements?page=${page}&size=10`), apiClient.get(`/api/v1/spa/tickets/${ticket.id}/refunds`)]).then(([a,b]) => {
      if (!active) return; const data = unwrap(a); setError(''); setHistory(data.content || []); setMore(!data.last && page + 1 < data.totalPages); setRefunds(unwrap(b));
    }).catch(e => { if (active) setError(errorMessage(e)); }); return () => { active = false; };
  }, [open, ticket.id, page]);
  const usable = ticket.status === 'ACTIVE' && ticket.remainingSessions > 0 && (!ticket.expiryDate || Date.parse(ticket.expiryDate) > now);
  return <div className="spa-ticket-detail">
    {!ticket.nameSnapshot && <small>Tên hiện tại; vé cũ chưa lưu tên lúc mua.</small>}
    <p>{ticket.benefitType === 'TREATMENT_COURSE' ? 'Quyền theo lộ trình điều trị' : 'Gói nhiều lượt thông thường'} · Đang giữ: {ticket.reservedSessions ?? 'Chưa xác định'} lượt</p>
    <ul>{(ticket.services || []).map(s => <li key={s.serviceId}><strong>{s.serviceName}</strong>: {s.available} {usable ? 'khả dụng' : 'chưa dùng (vé không khả dụng)'} / {s.total} tổng · {s.used} đã dùng hoặc mất lượt · {s.reserved} đang giữ</li>)}</ul>
    <div className="spa-benefit-actions">{onNavigate && <button type="button" className="btn-luxury-outline" disabled={ticket.benefitType !== 'TREATMENT_COURSE' && !usable} onClick={() => onNavigate(ticketDestination(ticket))}>Mở {ticket.benefitType === 'TREATMENT_COURSE' ? 'lộ trình điều trị' : 'đặt lịch sử dụng vé'}</button>}<button type="button" className="btn-luxury-outline" onClick={() => { setError(''); setOpen(v => !v); }}>{open ? 'Ẩn' : 'Xem'} lịch sử vé</button></div>
    {open && <div aria-live="polite">{error && <p role="alert">{error}<button onClick={() => { setOpen(false); }}>Đóng để thử lại</button></p>}
      {history.map(m => <p key={m.id}>{new Date(m.createdAt).toLocaleString('vi-VN')} · {movementLabel(m.operation)}{m.serviceId ? ` · Dịch vụ #${m.serviceId}` : ''}<br /><small>{m.reason}</small></p>)}
      {!error && !history.length && <p>Chưa có biến động được ghi nhận.</p>}
      <div className="spa-benefit-actions"><button type="button" disabled={page===0} onClick={() => setPage(p => p-1)}>Trang trước</button><button type="button" disabled={!more} onClick={() => setPage(p => p+1)}>Trang tiếp</button></div>
      {refunds.map((r,i) => <p key={i}>Quyết toán: {money(r.amount)} · Chứng từ {r.reference} · {new Date(r.createdAt).toLocaleString('vi-VN')}<br />{r.reason}</p>)}
    </div>}
  </div>;
};
