import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { Button } from '../../shared/ui/Button';
import { unwrap, money, errorMessage } from './treatmentApi';
import { useAuth } from '../../app/providers/AuthProvider';
import { BankPaymentInstruction } from '../../shared/ui/BankPaymentInstruction';
export const AppointmentFinance = ({ appointmentId, role, defaultOpen = false }) => {
 const {isAdmin,isSpaReception,user,isAuthenticated}=useAuth();
 const canCollect=role!=='customer'&&isSpaReception;
 const canRefund=role!=='customer'&&isAdmin;
 const [finance,setFinance]=useState(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const [kind,setKind]=useState('deposit-receipts'),[amount,setAmount]=useState(''),[method,setMethod]=useState('CASH'),[reference,setReference]=useState(''),[reason,setReason]=useState('');
 const pending=useRef(null),lock=useRef(false); const base=`/api/v1/spa/appointments/${appointmentId}`;
 const [open,setOpen]=useState(defaultOpen),[visible,setVisible]=useState(!document.hidden);
 const [timeLeft,setTimeLeft]=useState('');
 const session=useRef(0);
 const load=async()=>{const version=session.current;const data=unwrap(await apiClient.get(`${base}/finance`));if(version===session.current){setFinance(data);setError('');}};
 useEffect(()=>{
  session.current++;setFinance(null);setError('');pending.current=null;
  if(!isAuthenticated)return;
  const controller=new AbortController();
  apiClient.get(`${base}/finance`,{signal:controller.signal}).then(res=>{if(!controller.signal.aborted)setFinance(unwrap(res));}).catch(e=>{if(!controller.signal.aborted)setError(errorMessage(e));});
  return()=>{controller.abort();session.current++;};
 },[base,user?.id,isAuthenticated]);
 useEffect(()=>{const change=()=>setVisible(!document.hidden);document.addEventListener('visibilitychange',change);return()=>document.removeEventListener('visibilitychange',change);},[]);
 const polling=Boolean(finance?.depositPayment?.canPay);
 useEffect(()=>{
  if(!polling||!open||!visible||!isAuthenticated)return;
  const controller=new AbortController();let timer;
  const poll=async()=>{let again=true;try{const res=await apiClient.get(`${base}/finance`,{signal:controller.signal});if(controller.signal.aborted)return;const data=unwrap(res);setFinance(data);setError('');again=Boolean(data.depositPayment?.canPay);}catch(e){if(!controller.signal.aborted)setError(errorMessage(e));}finally{if(!controller.signal.aborted&&again)timer=setTimeout(poll,5000);}};
  poll();return()=>{controller.abort();clearTimeout(timer);};
 },[base,polling,open,visible,user?.id,isAuthenticated]);
 useEffect(()=>{
  if(!finance?.pendingExpiresAt||finance?.status!=='PENDING'||Number(finance?.depositDue)<=0){setTimeLeft('');return;}
  const calc=()=>{
   const diff=Math.max(0,Math.floor((new Date(finance.pendingExpiresAt).getTime()-Date.now())/1000));
   const m=Math.floor(diff/60),s=diff%60;
   setTimeLeft(diff>0?`${m}:${s<10?'0':''}${s}`:'Hết hạn giữ chỗ');
  };
  calc();const t=setInterval(calc,1000);return()=>clearInterval(t);
 },[finance?.pendingExpiresAt,finance?.status,finance?.depositDue]);
 const createPayment=async()=>{if(lock.current)return;lock.current=true;setBusy(true);const version=session.current;try{const res=await apiClient.post(`${base}/deposit-payment`);if(version===session.current){setFinance(unwrap(res));setError('');}}catch(e){if(version===session.current)setError(errorMessage(e));}finally{lock.current=false;if(version===session.current)setBusy(false);}};
 const submit=async()=>{if(lock.current)return;lock.current=true;setBusy(true);setError('');try{
  const body={amount:Number(amount),method,externalReference:reference.trim(),reason:reason.trim()};
  if(!Number.isSafeInteger(body.amount)||body.amount<=0||!body.externalReference||!body.reason)throw new Error('Nhập số tiền nguyên dương, mã đối chiếu và lý do.');
  const signature=JSON.stringify({kind,body});if(pending.current&&pending.current.signature!==signature)throw new Error('Phiếu trước chưa xác minh; tải lại và giữ nguyên nội dung để thử lại.');
  pending.current||={key:crypto.randomUUID(),signature};await apiClient.post(`${base}/${kind}`,body,{headers:{'Idempotency-Key':pending.current.key}});pending.current=null;await load();setAmount('');setReference('');setReason('');
 }catch(e){setError(errorMessage(e));}finally{lock.current=false;setBusy(false);}};
 return <details className="treatment-form-session" open={defaultOpen} onToggle={e=>setOpen(e.currentTarget.open)}><summary>{role === 'customer' ? 'Thông tin thanh toán & Hướng dẫn VietQR' : 'Cọc, phí hủy/vắng hẹn & hoàn cọc'}</summary>{error&&<p role="alert">{error}</p>}{finance&&<>
 {finance.status==='CONFIRMED'&&Number(finance.depositCollected)>0&&<div style={{padding:'10px 14px',background:'#ecfdf5',color:'#065f46',borderRadius:'8px',marginBottom:'12px',fontWeight:600}}>✓ Đã nhận thanh toán thành công! Lịch hẹn đã được xác nhận tự động.</div>}
 {timeLeft&&<div style={{padding:'10px 14px',background:'#fffbeb',color:'#92400e',borderRadius:'8px',marginBottom:'12px',fontWeight:600}}>⏳ Thời gian giữ chỗ còn lại: <strong style={{color:'#b45309'}}>{timeLeft}</strong>. Vui lòng thanh toán trước khi hết hạn để tránh bị tự động hủy lịch.</div>}
 {role === 'customer' ? (
  <p>Thanh toán trước yêu cầu: <strong>{money(finance.depositRequired)}</strong> · Đã chuyển: {money(finance.depositCollected)} · Còn lại cần chuyển: {money(finance.depositDue)}</p>
 ) : (
  <p>Cọc cần thu: <strong>{money(finance.depositRequired)}</strong> · Đã thu: {money(finance.depositCollected)} · Còn thiếu: {money(finance.depositDue)} · Đã trừ vào hóa đơn: {money(finance.appliedToInvoice)}</p>
 )}
 {(role !== 'customer' || Number(finance.assessedFee) > 0 || Number(finance.refundable) > 0) && (
  <p>Phí theo chính sách: {money(finance.assessedFee)} · Phí còn thiếu: {money(finance.feeDue)} · Cọc cần hoàn: {money(finance.refundable)} · Đã hoàn: {money(finance.refunded)}</p>
 )}
 {(finance.reviewReasons||[]).map((r,i)=><p key={i} role="alert" className="treatment-notice warning">{({
  OVERPAYMENT: role === 'customer' ? 'Bạn đã chuyển nhiều hơn số tiền yêu cầu. Nhân viên Spa sẽ đối chiếu và hoàn lại phần tiền thừa trực tiếp tại quầy.' : 'Đã thu vượt cọc yêu cầu; cần đối soát hoàn tiền.',
  LATE_RECEIPT:'Tiền đến sau khi lịch đã đóng; cần xem xét hoàn.',
  UNALLOCATED_AFTER_INVOICE:'Cọc chưa phân bổ sau hóa đơn; đợi hoàn.',
  LEGACY_BANK_REVIEW:'Phiếu BANK chưa liên kết giao dịch SePay; cần đối soát.'
 })[r]||r}</p>)}
 {finance.depositPayment?.canPay && <>
  <h4>{Number(finance.depositRequired) > 100000 ? 'Quét VietQR để thanh toán dịch vụ' : 'Quét VietQR để đặt cọc giữ chỗ'}</h4>
  <BankPaymentInstruction instruction={finance.depositPayment.paymentInstruction} label="Mã VietQR thanh toán Spa" />
  <p style={{fontSize: '12px', color: '#64748b'}}>Hệ thống tự kiểm tra mỗi 5 giây; lịch sẽ được xác nhận ngay khi ngân hàng báo nhận tiền.</p>
 </>}
 {!finance.depositPayment && Number(finance.depositDue)>0 && !finance.closed && <Button disabled={busy} onClick={createPayment}>Tạo mã thanh toán</Button>}
 {(finance.entries||[]).map(e=><p key={e.id}>{({DEPOSIT:'Thu cọc',FEE:'Thu phí',REFUND:'Đã hoàn',APPLY:'Trừ cọc vào hóa đơn'})[e.kind]} · {money(e.amount)} · {e.method}{e.externalReference&&` · ${e.externalReference}`}<br/><small>{e.reason}</small></p>)}
 </>}{canCollect&&<div className="treatment-form"><label>Loại phiếu<select value={kind} onChange={e=>setKind(e.target.value)}><option value="deposit-receipts">Thu cọc đã nhận</option><option value="fee-receipts">Thu phí còn thiếu</option>{canRefund&&<option value="deposit-refund-confirmations">Xác nhận đã hoàn cọc</option>}</select></label>
 <label>Số tiền VND<input type="number" min="1" step="1" value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>Phương thức<select value={method} onChange={e=>setMethod(e.target.value)}><option value="CASH">Tiền mặt</option><option value="BANK">Chuyển khoản đã đối chiếu</option></select></label>
 <label>Mã phiếu tiền mặt / mã giao dịch ngân hàng<input maxLength={128} value={reference} onChange={e=>setReference(e.target.value)}/></label><label>Lý do<textarea maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label><Button disabled={busy} onClick={submit}>Ghi nhận bằng chứng giao dịch</Button></div>}
 <Button variant="outline" disabled={busy} onClick={()=>load().catch(e=>setError(errorMessage(e)))}>Tải lại sổ cọc</Button></details>;
};
