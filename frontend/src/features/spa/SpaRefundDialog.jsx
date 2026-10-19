import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { unwrap, money, errorMessage } from '../treatments/treatmentApi';
import './spa-benefits.css';
export const SpaRefundDialog = ({ ticketId, appointmentId, onClose, onUpdated }) => {
  const path = ticketId ? `/api/v1/admin/spa/tickets/${ticketId}/settlement` : `/api/v1/admin/spa/appointments/${appointmentId}/service-refunds`;
  const [quote,setQuote]=useState(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[line,setLine]=useState(''),[amount,setAmount]=useState(''),[reference,setReference]=useState(''),[reason,setReason]=useState(''),[confirmed,setConfirmed]=useState(false);
  const [submitted,setSubmitted]=useState(false),[revision,setRevision]=useState(0);
  const pending=useRef(null), lock=useRef(false);
  useEffect(()=>{let active=true;apiClient.get(path).then(r=>{if(active)setQuote(unwrap(r));}).catch(e=>{if(active)setError(errorMessage(e));});return()=>{active=false;};},[path,revision]);
  const submit=async e=>{e.preventDefault();if(lock.current || !confirmed)return;lock.current=true;setBusy(true);setSubmitted(true);setError('');
    try{pending.current ||= { path: ticketId ? path : `/api/v1/admin/spa/appointments/${appointmentId}/items/${line}/service-refunds`, data:{amount:Number(ticketId?quote.refundableAmount:amount),reference:reference.trim(),reason:reason.trim()} };
      await apiClient.post(pending.current.path,pending.current.data);onUpdated?.();onClose();
    }catch(err){setError(`${errorMessage(err)} Yêu cầu được giữ nguyên khi thử lại; kiểm tra chứng từ trước khi tạo yêu cầu mới.`);}finally{lock.current=false;setBusy(false);}};
  return <Modal isOpen title={ticketId?'Quyết toán phần chưa dùng & thu hồi vé':'Hoàn tiền theo dịch vụ trên hóa đơn'} onClose={()=>{if(!busy)onClose();}}><form onSubmit={submit} className="spa-benefit-form">
    {error&&<p role="alert">{error}</p>}{!quote?<Button onClick={()=>{setError('');setRevision(v=>v+1);}}>Tải báo giá</Button>:<>
      {ticketId?<><p>Giá mua: {quote.purchasedAmount == null ? 'Chưa có giá lúc mua' : money(quote.purchasedAmount)} · Còn {quote.unusedSessions} lượt</p><strong>Số phải hoàn: {quote.blockedReason ? 'Chưa thể quyết toán' : money(quote.refundableAmount)}</strong>{quote.blockedReason&&<p role="alert">{quote.blockedReason}</p>}<p>Xác nhận sẽ thu hồi toàn bộ vé, gồm lượt bù. Gói không thể đặt tiếp sau quyết toán.</p></>:<>
        <p>Tiền thực thu chưa hoàn: {money(quote.availableReceivedAmount)}</p><label>Dịch vụ<select required value={line} disabled={busy||submitted} onChange={e=>{setLine(e.target.value);setAmount('');}}><option value="">Chọn dịch vụ</option>{quote.items.map(s=><option key={s.appointmentItemId} value={s.appointmentItemId}>{s.serviceName} · Tối đa {money(s.refundableAmount)}</option>)}</select></label>
        <label>Số tiền hoàn (VND)<input required type="number" min="1" step="1" max={Math.min(Number(quote.availableReceivedAmount),Number(quote.items.find(s=>String(s.appointmentItemId)===line)?.refundableAmount||0))} value={amount} onChange={e=>setAmount(e.target.value)} disabled={busy||submitted}/></label></>}
      <label>Chứng từ chuyển tiền / biên nhận<input required maxLength={100} value={reference} onChange={e=>setReference(e.target.value)} disabled={busy||submitted}/></label><label>Lý do phê duyệt<textarea required maxLength={250} value={reason} onChange={e=>setReason(e.target.value)} disabled={busy||submitted}/></label>
      <label><input type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)} /> Tôi đã kiểm tra số tiền và có chứng từ thực tế. Thao tác này ghi nhận tiền đã hoàn.</label><Button type="submit" loading={busy} disabled={!!quote.blockedReason || !confirmed}>Xác nhận {ticketId?'quyết toán & thu hồi':'hoàn tiền dịch vụ'}</Button>
    </>}
  </form></Modal>;
};
