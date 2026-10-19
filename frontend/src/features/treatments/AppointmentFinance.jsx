import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { Button } from '../../shared/ui/Button';
import { unwrap, money, errorMessage } from './treatmentApi';
import { useAuth } from '../../app/providers/AuthProvider';
export const AppointmentFinance = ({ appointmentId, role }) => {
 const {isAdmin,isSpaReception}=useAuth();
 const canCollect=role!=='customer'&&isSpaReception;
 const canRefund=role!=='customer'&&isAdmin;
 const [finance,setFinance]=useState(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const [kind,setKind]=useState('deposit-receipts'),[amount,setAmount]=useState(''),[method,setMethod]=useState('CASH'),[reference,setReference]=useState(''),[reason,setReason]=useState('');
 const pending=useRef(null),lock=useRef(false); const base=`/api/v1/spa/appointments/${appointmentId}`;
 const load=async()=>setFinance(unwrap(await apiClient.get(`${base}/finance`)));
 useEffect(()=>{load().catch(e=>setError(errorMessage(e)));},[base]);
 const submit=async()=>{if(lock.current)return;lock.current=true;setBusy(true);setError('');try{
  const body={amount:Number(amount),method,externalReference:reference.trim(),reason:reason.trim()};
  if(!Number.isSafeInteger(body.amount)||body.amount<=0||!body.externalReference||!body.reason)throw new Error('Nhập số tiền nguyên dương, mã đối chiếu và lý do.');
  const signature=JSON.stringify({kind,body});if(pending.current&&pending.current.signature!==signature)throw new Error('Phiếu trước chưa xác minh; tải lại và giữ nguyên nội dung để thử lại.');
  pending.current||={key:crypto.randomUUID(),signature};await apiClient.post(`${base}/${kind}`,body,{headers:{'Idempotency-Key':pending.current.key}});pending.current=null;await load();setAmount('');setReference('');setReason('');
 }catch(e){setError(errorMessage(e));}finally{lock.current=false;setBusy(false);}};
 return <details className="treatment-form-session"><summary>Cọc, phí hủy/vắng hẹn & hoàn cọc</summary>{error&&<p role="alert">{error}</p>}{finance&&<>
 <p>Cọc cần thu: <strong>{money(finance.depositRequired)}</strong> · Đã thu: {money(finance.depositCollected)} · Đã trừ vào hóa đơn: {money(finance.appliedToInvoice)}</p>
 <p>Phí theo chính sách: {money(finance.assessedFee)} · Phí còn thiếu: {money(finance.feeDue)} · Cọc cần hoàn: {money(finance.refundable)} · Đã hoàn: {money(finance.refunded)}</p>
 <p>Chuyển khoản/cọc được đối chiếu thủ công tại quầy. Phiếu hoàn chỉ xác nhận giao dịch đã thực hiện.</p>
 {finance.entries.map(e=><p key={e.id}>{({DEPOSIT:'Thu cọc',FEE:'Thu phí',REFUND:'Đã hoàn',APPLY:'Trừ cọc vào hóa đơn'})[e.kind]} · {money(e.amount)} · {e.method} · {e.externalReference}<br/><small>{e.reason}</small></p>)}
 </>}{canCollect&&<div className="treatment-form"><label>Loại phiếu<select value={kind} onChange={e=>setKind(e.target.value)}><option value="deposit-receipts">Thu cọc đã nhận</option><option value="fee-receipts">Thu phí còn thiếu</option>{canRefund&&<option value="deposit-refund-confirmations">Xác nhận đã hoàn cọc</option>}</select></label>
 <label>Số tiền VND<input type="number" min="1" step="1" value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>Phương thức<select value={method} onChange={e=>setMethod(e.target.value)}><option value="CASH">Tiền mặt</option><option value="BANK">Chuyển khoản đã đối chiếu</option></select></label>
 <label>Mã phiếu tiền mặt / mã giao dịch ngân hàng<input maxLength={128} value={reference} onChange={e=>setReference(e.target.value)}/></label><label>Lý do<textarea maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label><Button disabled={busy} onClick={submit}>Ghi nhận bằng chứng giao dịch</Button></div>}
 <Button variant="outline" disabled={busy} onClick={()=>load().catch(e=>setError(errorMessage(e)))}>Tải lại sổ cọc</Button></details>;
};
