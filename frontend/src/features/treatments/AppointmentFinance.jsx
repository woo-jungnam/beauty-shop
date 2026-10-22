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
 const createPayment=async()=>{if(lock.current)return;lock.current=true;setBusy(true);const version=session.current;try{const res=await apiClient.post(`${base}/deposit-payment`);if(version===session.current){setFinance(unwrap(res));setError('');}}catch(e){if(version===session.current)setError(errorMessage(e));}finally{lock.current=false;if(version===session.current)setBusy(false);}};
 const submit=async()=>{if(lock.current)return;lock.current=true;setBusy(true);setError('');try{
  const body={amount:Number(amount),method,externalReference:reference.trim(),reason:reason.trim()};
  if(!Number.isSafeInteger(body.amount)||body.amount<=0||!body.externalReference||!body.reason)throw new Error('Nhập số tiền nguyên dương, mã đối chiếu và lý do.');
  const signature=JSON.stringify({kind,body});if(pending.current&&pending.current.signature!==signature)throw new Error('Phiếu trước chưa xác minh; tải lại và giữ nguyên nội dung để thử lại.');
  pending.current||={key:crypto.randomUUID(),signature};await apiClient.post(`${base}/${kind}`,body,{headers:{'Idempotency-Key':pending.current.key}});pending.current=null;await load();setAmount('');setReference('');setReason('');
 }catch(e){setError(errorMessage(e));}finally{lock.current=false;setBusy(false);}};
 return <details className="treatment-form-session" open={defaultOpen} onToggle={e=>setOpen(e.currentTarget.open)}><summary>Cọc, phí hủy/vắng hẹn & hoàn cọc</summary>{error&&<p role="alert">{error}</p>}{finance&&<>
 <p>Cọc cần thu: <strong>{money(finance.depositRequired)}</strong> · Đã thu: {money(finance.depositCollected)} · Còn thiếu: {money(finance.depositDue)} · Đã trừ vào hóa đơn: {money(finance.appliedToInvoice)}</p>
 <p>Phí theo chính sách: {money(finance.assessedFee)} · Phí còn thiếu: {money(finance.feeDue)} · Cọc cần hoàn: {money(finance.refundable)} · Đã hoàn: {money(finance.refunded)}</p>
 {(finance.reviewReasons||[]).map((r,i)=><p key={i} role="alert" className="treatment-notice warning">{({OVERPAYMENT:'Đã thu vượt cọc yêu cầu; cần đối soát hoàn tiền.',LATE_RECEIPT:'Tiền đến sau khi lịch đã đóng; cần xem xét hoàn.',UNALLOCATED_AFTER_INVOICE:'Cọc chưa phân bổ sau hóa đơn; đợi hoàn.',LEGACY_BANK_REVIEW:'Phiếu BANK chưa liên kết giao dịch SePay; cần đối soát.'})[r]||r}</p>)}
 {finance.depositPayment?.canPay && <><h4>Quét VietQR để đặt cọc</h4><BankPaymentInstruction instruction={finance.depositPayment.paymentInstruction} label="Mã VietQR thanh toán cọc Spa" /><p>Hệ thống tự kiểm tra mỗi 5 giây; chỉ hoàn tất khi nhận xác nhận thanh toán.</p></>}
 {!finance.depositPayment && Number(finance.depositDue)>0 && !finance.closed && <Button disabled={busy} onClick={createPayment}>Tạo mã thanh toán cọc</Button>}
 {(finance.entries||[]).map(e=><p key={e.id}>{({DEPOSIT:'Thu cọc',FEE:'Thu phí',REFUND:'Đã hoàn',APPLY:'Trừ cọc vào hóa đơn'})[e.kind]} · {money(e.amount)} · {e.method}{e.externalReference&&` · ${e.externalReference}`}<br/><small>{e.reason}</small></p>)}
 </>}{canCollect&&<div className="treatment-form"><label>Loại phiếu<select value={kind} onChange={e=>setKind(e.target.value)}><option value="deposit-receipts">Thu cọc đã nhận</option><option value="fee-receipts">Thu phí còn thiếu</option>{canRefund&&<option value="deposit-refund-confirmations">Xác nhận đã hoàn cọc</option>}</select></label>
 <label>Số tiền VND<input type="number" min="1" step="1" value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>Phương thức<select value={method} onChange={e=>setMethod(e.target.value)}><option value="CASH">Tiền mặt</option><option value="BANK">Chuyển khoản đã đối chiếu</option></select></label>
 <label>Mã phiếu tiền mặt / mã giao dịch ngân hàng<input maxLength={128} value={reference} onChange={e=>setReference(e.target.value)}/></label><label>Lý do<textarea maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label><Button disabled={busy} onClick={submit}>Ghi nhận bằng chứng giao dịch</Button></div>}
 <Button variant="outline" disabled={busy} onClick={()=>load().catch(e=>setError(errorMessage(e)))}>Tải lại sổ cọc</Button></details>;
};
