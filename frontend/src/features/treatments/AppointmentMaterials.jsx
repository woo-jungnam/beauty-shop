import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { Button } from '../../shared/ui/Button';
import { rows, errorMessage, money } from './treatmentApi';

export const AppointmentMaterials = ({ appointment, canPerformItem = () => false }) => {
  const [items, setItems] = useState([]), [warehouses, setWarehouses] = useState([]), [stocks, setStocks] = useState([]);
  const [materialOptions,setMaterialOptions]=useState([]);
  const [itemId, setItemId] = useState(''), [warehouseId, setWarehouseId] = useState(''), [variantId, setVariantId] = useState('');
  const [quantity, setQuantity] = useState('1'), [reason, setReason] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const receipt = useRef(null), locked = useRef(false);
  const base = `/api/v1/spa/appointments/${appointment.id}`;
  const load = async () => setItems(rows(await apiClient.get(`${base}/materials`)));
  useEffect(() => { load().catch(e => setError(errorMessage(e))); apiClient.get('/api/v1/spa/material-options').then(r => {const choices=rows(r);setMaterialOptions(choices);setWarehouses([...new Map(choices.map(o=>[o.warehouseId,{id:o.warehouseId,name:o.warehouseName}])).values()]);}).catch(e => setError(errorMessage(e))); }, [base]);
  useEffect(() => { setVariantId('');setStocks(materialOptions.filter(o=>String(o.warehouseId)===warehouseId).map(o=>({...o,productVariantId:o.variantId}))); }, [warehouseId,materialOptions]);
  const active = (appointment.items || []).filter(i => i.executionStatus === 'IN_PROGRESS' && canPerformItem(i));
  const choices = [...new Map(stocks.map(s => [s.productVariantId, s])).values()];
  const submit = async waiver => {
    if (locked.current) return; locked.current = true; setBusy(true); setError('');
    try {
      if (!itemId || !reason.trim()) throw new Error('Chọn dịch vụ và nhập lý do.');
      if (waiver) await apiClient.post(`${base}/items/${itemId}/material-waiver`, { reason });
      else {
        const payload = { variantId: Number(variantId), warehouseId: Number(warehouseId), quantity: Number(quantity), reason: reason.trim() };
        if (!variantId || !warehouseId || !Number.isInteger(payload.quantity) || payload.quantity < 1) throw new Error('Chọn vật tư, kho và số lượng nguyên dương.');
        const signature=JSON.stringify({itemId,payload});
        if (receipt.current && receipt.current.signature !== signature) throw new Error('Phiếu trước chưa xác minh; giữ nguyên dịch vụ và dữ liệu để gửi lại hoặc tải lại danh sách.');
        receipt.current ||= { key: crypto.randomUUID(), payload, signature };
        await apiClient.post(`${base}/items/${itemId}/material-usages`, payload, { headers: { 'Idempotency-Key': receipt.current.key } }); receipt.current = null;
      }
      await load(); setReason('');
    } catch (e) { setError(errorMessage(e)); } finally { locked.current = false; setBusy(false); }
  };
  return <details className="treatment-form-session"><summary>Vật tư thực tế & truy vết lô</summary>{error && <p role="alert">{error}</p>}
    <Button variant="outline" onClick={() => load().catch(e => setError(errorMessage(e)))} disabled={busy}>Tải lại phiếu vật tư</Button>
    {items.map(item => <div key={item.appointmentItemId}><strong>{appointment.items?.find(i => i.id === item.appointmentItemId)?.serviceName}</strong>
      <p>Định mức lúc đặt: {item.planned.map(m => `SKU #${m.variantId}: ${m.quantity}`).join(' · ') || 'Không có'}</p>
      {item.usages.map(u => <p key={u.id}>SKU #{u.variantId} · {u.quantity} đơn vị · Giá vốn {money(u.totalCost)}<br />{u.reason}<br /><small>Lô: {(() => { try { return JSON.parse(u.allocationJson).map(b => `${b.batchCode || 'Không mã'} × ${b.quantity}`).join(', '); } catch { return u.allocationJson; } })()}</small></p>)}
      {item.waiverReason && <p>Không sử dụng: {item.waiverReason}</p>}</div>)}
    {appointment.status === 'IN_PROGRESS' && active.length > 0 && <div className="treatment-form"><label>Dịch vụ đang thực hiện<select value={itemId} onChange={e => setItemId(e.target.value)}><option value="">Chọn dịch vụ</option>{active.map(i => <option key={i.id} value={i.id}>{i.serviceName}</option>)}</select></label>
      <label>Kho xuất vật tư<select value={warehouseId} onChange={e => setWarehouseId(e.target.value)}><option value="">Chọn kho</option>{warehouses.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}</select></label>
      <label>Vật tư<select value={variantId} onChange={e => setVariantId(e.target.value)}><option value="">Chọn SKU</option>{choices.map(s => <option key={s.productVariantId} value={s.productVariantId}>{s.productName} · {s.sku || `SKU #${s.productVariantId}`} · Còn {s.availableQuantity}</option>)}</select></label>
      <label>Số lượng thực tế theo đơn vị SKU<input type="number" min="1" step="1" value={quantity} onChange={e => setQuantity(e.target.value)} /></label>
      <label>Lý do / ghi chú<textarea maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
      <div className="treatment-actions"><Button disabled={busy} onClick={() => submit(false)}>Ghi nhận xuất dùng</Button><Button disabled={busy} variant="outline" onClick={() => submit(true)}>Ghi lý do không dùng vật tư</Button></div>
    </div>}
  </details>;
};
