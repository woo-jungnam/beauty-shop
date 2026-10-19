import React,{useEffect,useState} from 'react';
import {apiClient} from '../../shared/api/client';
import {Button} from '../../shared/ui/Button';
const rows=r=>{const d=r?.data??r;return Array.isArray(d)?d:d?.content||[];};
export const ServiceMaterialsEditor=({serviceId})=>{
 const [materials,setMaterials]=useState([]),[options,setOptions]=useState([]),[message,setMessage]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{if(!serviceId)return;apiClient.get(`/api/v1/admin/spa/services/${serviceId}/materials`).then(r=>setMaterials(rows(r))).catch(e=>setMessage(e.message));
  apiClient.get('/api/v1/spa/material-options').then(r=>setOptions([...new Map(rows(r).map(s=>[s.variantId,{...s,productVariantId:s.variantId}])).values()])).catch(e=>setMessage(e.message));
 },[serviceId]);
 const save=async()=>{if(busy)return;setBusy(true);try{await apiClient.put(`/api/v1/admin/spa/services/${serviceId}/materials`,materials.map(m=>({variantId:Number(m.variantId),quantity:Number(m.quantity)})));setMessage('Đã lưu định mức cho lịch đặt mới.');}catch(e){setMessage(e.message);}finally{setBusy(false);}};
 if(!serviceId)return <p>Lưu dịch vụ trước, sau đó mở chỉnh sửa để khai báo định mức vật tư.</p>;
 return <details><summary>Định mức mỹ phẩm / vật tư tiêu hao</summary><p>Số lượng nguyên theo đơn vị SKU. Mỗi lịch mới lưu bản định mức riêng; kỹ thuật viên ghi lượng dùng thực tế và hệ thống truy vết lô xuất.</p>{message&&<p role="status">{message}</p>}
 {materials.map((m,i)=><div key={i} style={{display:'flex',gap:8,marginBottom:8}}><select aria-label={`Vật tư ${i+1}`} value={m.variantId} onChange={e=>setMaterials(list=>list.map((v,n)=>n===i?{...v,variantId:e.target.value}:v))}><option value="">Chọn SKU</option>{options.map(o=><option key={o.productVariantId} value={o.productVariantId}>{o.sku||`SKU #${o.productVariantId}`}</option>)}{m.variantId&&!options.some(o=>String(o.productVariantId)===String(m.variantId))&&<option value={m.variantId}>SKU #{m.variantId}</option>}</select><input aria-label={`Số lượng vật tư ${i+1}`} type="number" min="1" step="1" value={m.quantity} onChange={e=>setMaterials(list=>list.map((v,n)=>n===i?{...v,quantity:e.target.value}:v))}/><Button variant="outline" onClick={()=>setMaterials(list=>list.filter((_,n)=>n!==i))}>Xóa</Button></div>)}
 <Button variant="outline" onClick={()=>setMaterials(list=>[...list,{variantId:'',quantity:1}])}>Thêm vật tư</Button><Button disabled={busy} onClick={save}>Lưu định mức vật tư</Button></details>;
};
