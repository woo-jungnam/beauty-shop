import React, { useEffect, useState } from 'react';
import { Check, PackageCheck, Plus, RefreshCw, X } from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Modal } from '../shared/ui/Modal';
import { DataTable } from '../shared/ui/DataTable';
import { Badge } from '../shared/ui/Badge';
import { AlertBanner } from '../shared/ui/AlertBanner';
import { formatCurrency, formatDate } from '../shared/utils/formatters';

const blankItem = () => ({ variantId: '', quantity: 1, unitCost: '', batchCode: '', expirationDate: '' });

export const ProcurementPage = () => {
  const [tab, setTab] = useState('orders');
  const [suppliers, setSuppliers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [supplierOpen, setSupplierOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState(null);
  const [orderOpen, setOrderOpen] = useState(false);
  const [supplierForm, setSupplierForm] = useState({ code: '', name: '', contactName: '', email: '', phone: '', address: '', taxCode: '', active: true });
  const [orderForm, setOrderForm] = useState({ supplierId: '', warehouseId: '', expectedDate: '', note: '', items: [blankItem()] });

  const load = async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [supplierResult, orderResult, warehouseResult] = await Promise.allSettled([
        apiClient.get(`${ENDPOINTS.PROCUREMENT.SUPPLIERS}?size=100`),
        apiClient.get(`${ENDPOINTS.PROCUREMENT.ORDERS}?size=100&sort=createdAt,desc`),
        apiClient.get(ENDPOINTS.INVENTORY.WAREHOUSES),
      ]);
      const failures = [];
      if (supplierResult.status === 'fulfilled') {
        const response = supplierResult.value.data || supplierResult.value;
        setSuppliers(response.content || []);
      } else {
        failures.push('nhà cung cấp');
      }
      if (orderResult.status === 'fulfilled') {
        const response = orderResult.value.data || orderResult.value;
        setOrders(response.content || []);
      } else {
        failures.push('phiếu mua hàng');
      }
      if (warehouseResult.status === 'fulfilled') {
        setWarehouses(warehouseResult.value.data || warehouseResult.value || []);
      } else {
        failures.push('kho nhận hàng');
      }
      if (failures.length) {
        setLoadError(`Không thể tải ${failures.join(', ')}. Các dữ liệu còn lại vẫn được hiển thị.`);
      }
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { load(); }, []);

  const handleOpenCreateSupplier = () => {
    setEditingSupplier(null);
    setSupplierForm({ code: '', name: '', contactName: '', email: '', phone: '', address: '', taxCode: '', active: true });
    setSupplierOpen(true);
  };

  const handleOpenEditSupplier = (s) => {
    setEditingSupplier(s);
    setSupplierForm({
      code: s.code || '',
      name: s.name || '',
      contactName: s.contactName || '',
      email: s.email || '',
      phone: s.phone || '',
      address: s.address || '',
      taxCode: s.taxCode || '',
      active: s.active ?? true,
    });
    setSupplierOpen(true);
  };

  const handleDeleteSupplier = async (id) => {
    if (!window.confirm(`Xóa nhà cung cấp #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.PROCUREMENT.DELETE_SUPPLIER(id));
      load();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa nhà cung cấp');
    }
  };

  const saveSupplier = async (e) => {
    e.preventDefault();
    if (editingSupplier) {
      await apiClient.put(ENDPOINTS.PROCUREMENT.SUPPLIER(editingSupplier.id), supplierForm);
    } else {
      await apiClient.post(ENDPOINTS.PROCUREMENT.SUPPLIERS, supplierForm);
    }
    setSupplierOpen(false);
    setEditingSupplier(null);
    setSupplierForm({ code: '', name: '', contactName: '', email: '', phone: '', address: '', taxCode: '', active: true });
    load();
  };
  const createOrder = async (e) => {
    e.preventDefault();
    const payload = { ...orderForm, expectedDate: orderForm.expectedDate || null, supplierId: Number(orderForm.supplierId), warehouseId: Number(orderForm.warehouseId), items: orderForm.items.map((item) => ({ ...item, variantId: Number(item.variantId), quantity: Number(item.quantity), unitCost: Number(item.unitCost), expirationDate: item.expirationDate || null })) };
    await apiClient.post(ENDPOINTS.PROCUREMENT.ORDERS, payload);
    setOrderOpen(false); setOrderForm({ supplierId: '', warehouseId: '', expectedDate: '', note: '', items: [blankItem()] }); load();
  };
  const transition = async (endpoint, prompt) => { if (!window.confirm(prompt)) return; await apiClient.put(endpoint); load(); };

  const supplierColumns = [
    { header: 'Mã', accessor: 'code' }, { header: 'Nhà cung cấp', accessor: (row) => <strong>{row.name}</strong> },
    { header: 'Liên hệ', accessor: (row) => <div>{row.contactName}<br /><small>{row.phone} · {row.email}</small></div> },
    { header: 'Mã số thuế', accessor: 'taxCode' }, { header: 'Trạng thái', accessor: (row) => <Badge variant={row.active ? 'success' : 'default'}>{row.active ? 'ACTIVE' : 'OFF'}</Badge> },
    { header: 'Thao tác', align: 'right', accessor: (row) => <div className="table-actions">
      <Button size="sm" variant="outline" onClick={() => handleOpenEditSupplier(row)}>Sửa</Button>
      <Button size="sm" variant="danger" icon={X} onClick={() => handleDeleteSupplier(row.id)} title="Xóa nhà cung cấp" />
    </div> },
  ];
  const orderColumns = [
    { header: 'Phiếu mua', accessor: (row) => <strong>{row.orderNumber}</strong> }, { header: 'Nhà cung cấp', accessor: 'supplierName' },
    { header: 'Kho nhận', accessor: (row) => warehouses.find((w) => w.id === row.warehouseId)?.name || `Kho #${row.warehouseId}` },
    { header: 'Ngày dự kiến', accessor: (row) => formatDate(row.expectedDate) }, { header: 'Tổng tiền', accessor: (row) => formatCurrency(row.totalAmount), align: 'right' },
    { header: 'Trạng thái', accessor: (row) => <Badge variant={row.status === 'RECEIVED' ? 'success' : row.status === 'CANCELLED' ? 'danger' : 'warning'}>{row.status}</Badge> },
    { header: 'Thao tác', align: 'right', accessor: (row) => <div className="table-actions">
      {row.status === 'DRAFT' && <Button size="sm" icon={Check} onClick={() => transition(ENDPOINTS.PROCUREMENT.APPROVE(row.id), 'Duyệt phiếu mua này?')}>Duyệt</Button>}
      {row.status === 'APPROVED' && <Button size="sm" icon={PackageCheck} onClick={() => transition(ENDPOINTS.PROCUREMENT.RECEIVE(row.id), 'Xác nhận đã nhận đủ hàng? Tồn kho sẽ được cộng theo lô.')}>Nhận hàng</Button>}
      {!['RECEIVED','CANCELLED'].includes(row.status) && <Button size="sm" variant="danger" icon={X} onClick={() => transition(ENDPOINTS.PROCUREMENT.CANCEL(row.id), 'Hủy phiếu mua này?')} />}
    </div> },
  ];

  return <div className="content-container">
    <div className="page-header"><div><h1 className="page-title">Nhà cung cấp & Mua hàng</h1><p className="page-subtitle">Kiểm soát phê duyệt, nhận hàng theo lô và cập nhật tồn kho</p></div><div style={{display:'flex',gap:8}}><Button variant="outline" icon={RefreshCw} onClick={load} loading={loading}>Làm mới</Button><Button icon={Plus} onClick={() => tab === 'orders' ? setOrderOpen(true) : handleOpenCreateSupplier()}>{tab === 'orders' ? 'Tạo phiếu mua' : 'Thêm nhà cung cấp'}</Button></div></div>
    {loadError && <AlertBanner type="danger" title="Không thể tải đầy đủ dữ liệu" message={loadError} action={<Button size="sm" variant="outline" onClick={load}>Thử lại</Button>} />}
    <div className="tabs-header"><button className={`tab-btn ${tab==='orders'?'active':''}`} onClick={() => setTab('orders')}>Phiếu mua hàng</button><button className={`tab-btn ${tab==='suppliers'?'active':''}`} onClick={() => setTab('suppliers')}>Nhà cung cấp</button></div>
    <div className="card"><DataTable columns={tab === 'orders' ? orderColumns : supplierColumns} data={tab === 'orders' ? orders : suppliers} loading={loading} /></div>
    <Modal isOpen={supplierOpen} onClose={() => setSupplierOpen(false)} title={editingSupplier ? "Cập nhật nhà cung cấp" : "Thêm nhà cung cấp"}><form onSubmit={saveSupplier}><div className="grid-2"><Input label="Mã *" required value={supplierForm.code} onChange={(e)=>setSupplierForm({...supplierForm,code:e.target.value})}/><Input label="Tên *" required value={supplierForm.name} onChange={(e)=>setSupplierForm({...supplierForm,name:e.target.value})}/><Input label="Người liên hệ" value={supplierForm.contactName} onChange={(e)=>setSupplierForm({...supplierForm,contactName:e.target.value})}/><Input label="Số điện thoại" value={supplierForm.phone} onChange={(e)=>setSupplierForm({...supplierForm,phone:e.target.value})}/><Input label="Email" type="email" value={supplierForm.email} onChange={(e)=>setSupplierForm({...supplierForm,email:e.target.value})}/><Input label="Mã số thuế" value={supplierForm.taxCode} onChange={(e)=>setSupplierForm({...supplierForm,taxCode:e.target.value})}/></div><Input label="Địa chỉ" value={supplierForm.address} onChange={(e)=>setSupplierForm({...supplierForm,address:e.target.value})}/><div style={{display:'flex',justifyContent:'flex-end',marginTop:16}}><Button type="submit">Lưu nhà cung cấp</Button></div></form></Modal>
    <Modal isOpen={orderOpen} onClose={() => setOrderOpen(false)} title="Tạo phiếu mua hàng" maxWidth="760px"><form onSubmit={createOrder}><div className="grid-2"><Select label="Nhà cung cấp *" required value={orderForm.supplierId} onChange={(e)=>setOrderForm({...orderForm,supplierId:e.target.value})} options={suppliers.map((s)=>({label:`${s.code} - ${s.name}`,value:String(s.id)}))}/><Select label="Kho nhận *" required value={orderForm.warehouseId} onChange={(e)=>setOrderForm({...orderForm,warehouseId:e.target.value})} options={warehouses.map((w)=>({label:w.name,value:String(w.id)}))}/><Input label="Ngày dự kiến" type="date" value={orderForm.expectedDate} onChange={(e)=>setOrderForm({...orderForm,expectedDate:e.target.value})}/><Input label="Ghi chú" value={orderForm.note} onChange={(e)=>setOrderForm({...orderForm,note:e.target.value})}/></div><h4>Chi tiết hàng hóa</h4>{orderForm.items.map((item,index)=><div key={index} className="grid-3" style={{marginBottom:10}}><Input label="ID biến thể *" type="number" required value={item.variantId} onChange={(e)=>setOrderForm({...orderForm,items:orderForm.items.map((x,i)=>i===index?{...x,variantId:e.target.value}:x)})}/><Input label="Số lượng *" type="number" min="1" required value={item.quantity} onChange={(e)=>setOrderForm({...orderForm,items:orderForm.items.map((x,i)=>i===index?{...x,quantity:e.target.value}:x)})}/><Input label="Đơn giá vốn *" type="number" min="0" required value={item.unitCost} onChange={(e)=>setOrderForm({...orderForm,items:orderForm.items.map((x,i)=>i===index?{...x,unitCost:e.target.value}:x)})}/><Input label="Mã lô" value={item.batchCode} onChange={(e)=>setOrderForm({...orderForm,items:orderForm.items.map((x,i)=>i===index?{...x,batchCode:e.target.value}:x)})}/><Input label="Hạn dùng" type="date" value={item.expirationDate} onChange={(e)=>setOrderForm({...orderForm,items:orderForm.items.map((x,i)=>i===index?{...x,expirationDate:e.target.value}:x)})}/><Button type="button" variant="danger" onClick={()=>setOrderForm({...orderForm,items:orderForm.items.filter((_,i)=>i!==index)})}>Xóa dòng</Button></div>)}<Button type="button" variant="outline" onClick={()=>setOrderForm({...orderForm,items:[...orderForm.items,blankItem()]})}>+ Thêm dòng</Button><div style={{display:'flex',justifyContent:'flex-end',marginTop:16}}><Button type="submit" disabled={!orderForm.items.length}>Tạo phiếu</Button></div></form></Modal>
  </div>;
};
