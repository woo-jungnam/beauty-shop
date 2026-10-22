import { ProcurementOperationsPanel } from '../features/commerce/AdminCommercePanels';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { businessToday, downloadTemplate, matchVariants, orderPayload, validDate, validateImportFile, validateItem } from '../features/procurement/procurementImport';
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
  const [orderPage, setOrderPage] = useState(0);
  const [orderPages, setOrderPages] = useState(1);
  const [orderCount, setOrderCount] = useState(0);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [supplierOpen, setSupplierOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState(null);
  const [orderOpen, setOrderOpen] = useState(false);
  const [supplierForm, setSupplierForm] = useState({ code: '', name: '', contactName: '', email: '', phone: '', address: '', taxCode: '', active: true });
  const [orderForm, setOrderForm] = useState({ supplierId: '', warehouseId: '', expectedDate: '', note: '', items: [blankItem()] });
  const [importBusy, setImportBusy] = useState(false);
  const [importError, setImportError] = useState('');
  const [orderSaving, setOrderSaving] = useState(false);
  const savingRef = useRef(false);
  const importTask = useRef({ generation: 0, worker: null, timer: null, reject: null });
  const cancelImport = useCallback(() => {
    importTask.current.generation++;
    importTask.current.worker?.terminate();
    clearTimeout(importTask.current.timer);
    importTask.current.reject?.(new Error('Đã hủy đọc Excel.'));
    importTask.current.reject = null;
    importTask.current.worker = null;
  }, []);
  useEffect(() => () => cancelImport(), [cancelImport]);
  const closeOrder = () => {
    if (savingRef.current) return;
    cancelImport(); setImportBusy(false); setOrderOpen(false);
  };
  const lookupItems = async items => {
    const skus = [...new Set(items.filter(item => item.sku?.trim() && item.sku.trim().length <= 100 && !item.cellErrors?.sku).map(item => item.sku.trim()))];
    if (!skus.length) return matchVariants(items, []);
    const response = await apiClient.post(ENDPOINTS.PROCUREMENT.VARIANT_LOOKUP, { skus });
    return matchVariants(items, response.data || response);
  };
  const lookupAgain = async () => {
    cancelImport();
    const generation = importTask.current.generation;
    setImportBusy(true); setImportError('');
    try {
      const items = await lookupItems(orderForm.items);
      if (generation === importTask.current.generation) setOrderForm(form => ({ ...form, items }));
    } catch (error) {
      if (generation === importTask.current.generation) setImportError(error.message || 'Không thể đối chiếu SKU.');
    } finally {
      if (generation === importTask.current.generation) setImportBusy(false);
    }
  };
  const importFile = async event => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || savingRef.current) return;
    cancelImport(); setImportBusy(false); setImportError('');
    try { validateImportFile(file); } catch (error) { setImportError(error.message); return; }
    const hasItems = orderForm.items.some(item => item.sku !== undefined || item.variantId || item.unitCost !== '' || item.batchCode || item.expirationDate || Number(item.quantity) !== 1);
    if (hasItems && !window.confirm('Thay danh sách hàng hiện tại bằng Excel? Nhà cung cấp và kho được giữ nguyên.')) return;
    const generation = importTask.current.generation;
    setImportBusy(true);
    try {
      const buffer = await file.arrayBuffer();
      if (generation !== importTask.current.generation) return;
      const rows = await new Promise((resolve, reject) => {
        const worker = new Worker(new URL('../features/procurement/procurementImport.worker.js', import.meta.url), { type: 'module' });
        importTask.current.worker = worker;
        importTask.current.reject = reject;
        const finish = (error, items) => {
          if (generation !== importTask.current.generation) return;
          worker.terminate(); clearTimeout(importTask.current.timer); importTask.current.worker = null; importTask.current.reject = null;
          if (error) reject(new Error(error)); else resolve(items);
        };
        importTask.current.timer = setTimeout(() => finish('Đọc Excel quá 10 giây. Hãy kiểm tra hoặc chia nhỏ file.'), 10000);
        worker.onmessage = ({ data }) => finish(data.error, data.rows);
        worker.onerror = () => finish('Không đọc được Excel. Hãy kiểm tra file và thử lại.');
        worker.postMessage(buffer, [buffer]);
      });
      if (generation !== importTask.current.generation) return;
      const items = await lookupItems(rows);
      if (generation === importTask.current.generation) setOrderForm(form => ({ ...form, items }));
    } catch (error) {
      if (generation === importTask.current.generation) setImportError(error.message || 'Không đọc được Excel.');
    } finally {
      if (generation === importTask.current.generation) setImportBusy(false);
    }
  };
  const editItem = (index, key, value) => {
    setOrderForm(form => ({ ...form, items: form.items.map((item, i) => {
      if (i !== index) return item;
      const cellErrors = { ...item.cellErrors }; delete cellErrors[key];
      return { ...item, [key]: value, cellErrors, ...(key === 'sku' ? { variantId: '', variantLabel: '' } : {}) };
    }) }));
  };
  const itemErrors = orderForm.items.map(item => validateItem(item));
  const hasItemErrors = itemErrors.some(errors => Object.keys(errors).length);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [supplierResult, orderResult, warehouseResult] = await Promise.allSettled([
        apiClient.get(`${ENDPOINTS.PROCUREMENT.SUPPLIERS}?size=100`),
        apiClient.get(`${ENDPOINTS.PROCUREMENT.ORDERS}?page=${orderPage}&size=20&sort=createdAt,desc&sort=id,desc`),
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
        setOrderPages(response.totalPages || 1);
        setOrderCount(response.totalElements || 0);
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
  }, [orderPage]);
  useEffect(() => { const fetchPage = async () => { await load(); }; fetchPage(); }, [load]);

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
    try {
      if (editingSupplier) {
        await apiClient.put(ENDPOINTS.PROCUREMENT.SUPPLIER(editingSupplier.id), supplierForm);
      } else {
        await apiClient.post(ENDPOINTS.PROCUREMENT.SUPPLIERS, supplierForm);
      }
      setSupplierOpen(false);
      setEditingSupplier(null);
      setSupplierForm({ code: '', name: '', contactName: '', email: '', phone: '', address: '', taxCode: '', active: true });
      load();
    } catch (err) {
      alert(err.message || 'Lỗi lưu nhà cung cấp');
    }
  };
  const createOrder = async (e) => {
    e.preventDefault();
    if (savingRef.current || importBusy) return;
    if (!orderForm.supplierId || !orderForm.warehouseId) {
      setImportError('Vui lòng chọn nhà cung cấp và kho nhận.'); return;
    }
    if (!orderForm.items.length || orderForm.items.some(item => Object.keys(validateItem(item)).length)) {
      setImportError('Sửa hoặc xóa các dòng lỗi trước khi tạo phiếu.'); return;
    }
    if (orderForm.items.reduce((sum, item) => sum + Math.round(Number(item.unitCost) * 100) * Number(item.quantity), 0) > 99999999999999) {
      setImportError('Tổng giá vốn vượt giới hạn 999999999999.99 của phiếu mua.'); return;
    }
    if (orderForm.note.length > 500 || (orderForm.expectedDate && (!validDate(orderForm.expectedDate) || orderForm.expectedDate < businessToday()))) {
      setImportError('Ghi chú tối đa 500 ký tự; ngày dự kiến phải từ hôm nay trở đi.'); return;
    }
    savingRef.current = true; setOrderSaving(true); setImportError('');
    try {
      await apiClient.post(ENDPOINTS.PROCUREMENT.ORDERS, orderPayload(orderForm));
      setOrderOpen(false); setOrderForm({ supplierId: '', warehouseId: '', expectedDate: '', note: '', items: [blankItem()] });
      if (orderPage === 0) load(); else setOrderPage(0);
    } catch (err) {
      setImportError(`${err.message || 'Lỗi tạo phiếu mua hàng'}.${!err.status || err.status >= 500 ? ' Kết quả lưu chưa rõ. Kiểm tra danh sách phiếu trước khi thử lại để tránh tạo trùng.' : ''}`);
    } finally {
      savingRef.current = false; setOrderSaving(false);
    }
  };
  const transition = async (endpoint, prompt) => {
    if (!window.confirm(prompt)) return;
    try {
      await apiClient.put(endpoint);
      load();
    } catch (err) {
      alert(err.message || 'Lỗi xử lý phiếu mua hàng');
    }
  };

  const ORDER_STATUS_MAP = {
    PARTIALLY_RECEIVED: { label: 'Đã nhận một phần', variant: 'info' },
    DRAFT: { label: 'Bản nháp', variant: 'warning' },
    APPROVED: { label: 'Đã duyệt', variant: 'info' },
    RECEIVED: { label: 'Đã nhập kho', variant: 'success' },
    CANCELLED: { label: 'Đã hủy', variant: 'danger' },
  };

  const supplierColumns = [
    { header: 'Mã', accessor: 'code' }, { header: 'Nhà cung cấp', accessor: (row) => <strong>{row.name}</strong> },
    { header: 'Liên hệ', accessor: (row) => <div>{row.contactName}<br /><small>{row.phone} · {row.email}</small></div> },
    { header: 'Mã số thuế', accessor: 'taxCode' }, { header: 'Trạng thái', accessor: (row) => <Badge variant={row.active ? 'success' : 'default'}>{row.active ? 'Đang hoạt động' : 'Tạm ngưng'}</Badge> },
    { header: 'Thao tác', align: 'right', accessor: (row) => <div className="table-actions">
      <Button size="sm" variant="outline" onClick={() => handleOpenEditSupplier(row)}>Sửa</Button>
      <Button size="sm" variant="danger" icon={X} onClick={() => handleDeleteSupplier(row.id)} title="Xóa nhà cung cấp" />
    </div> },
  ];
  const orderColumns = [
    { header: 'Mã phiếu mua', accessor: (row) => <strong>{row.orderNumber}</strong> }, { header: 'Nhà cung cấp', accessor: 'supplierName' },
    { header: 'Kho nhận hàng', accessor: (row) => warehouses.find((w) => w.id === row.warehouseId)?.name || `Kho #${row.warehouseId}` },
    { header: 'Ngày dự kiến nhận', accessor: (row) => formatDate(row.expectedDate) }, { header: 'Tổng tiền hàng', accessor: (row) => formatCurrency(row.totalAmount), align: 'right' },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const meta = ORDER_STATUS_MAP[row.status] || { label: row.status, variant: 'default' };
        return <Badge variant={meta.variant}>{meta.label}</Badge>;
      },
    },
    { header: 'Thao tác', align: 'right', accessor: (row) => <div className="table-actions">
      {row.status === 'DRAFT' && <Button size="sm" icon={Check} onClick={() => transition(ENDPOINTS.PROCUREMENT.APPROVE(row.id), 'Phê duyệt phiếu mua hàng này?')}>Duyệt</Button>}
      {row.status === 'APPROVED' && <Button size="sm" icon={PackageCheck} onClick={() => transition(ENDPOINTS.PROCUREMENT.RECEIVE(row.id), 'Xác nhận đã nhận đủ hàng? Hàng sẽ được nhập tăng tồn kho theo lô.')}>Nhập kho</Button>}
      {!['RECEIVED','CANCELLED'].includes(row.status) && <Button size="sm" variant="danger" icon={X} onClick={() => transition(ENDPOINTS.PROCUREMENT.CANCEL(row.id), 'Hủy bỏ phiếu mua hàng này?')} title="Hủy phiếu" />}
    </div> },
  ];

  return <div className="content-container">
    <div className="page-header"><div><h1 className="page-title">Nhà cung cấp & Mua hàng</h1><p className="page-subtitle">Kiểm soát phê duyệt, nhận hàng theo lô và cập nhật tồn kho</p></div><div style={{display:'flex',gap:8}}><Button variant="outline" icon={RefreshCw} onClick={load} loading={loading}>Làm mới</Button><Button icon={Plus} onClick={() => tab === 'orders' ? setOrderOpen(true) : handleOpenCreateSupplier()}>{tab === 'orders' ? 'Tạo phiếu mua' : 'Thêm nhà cung cấp'}</Button></div></div>
    {loadError && <AlertBanner type="danger" title="Không thể tải đầy đủ dữ liệu" message={loadError} action={<Button size="sm" variant="outline" onClick={load}>Thử lại</Button>} />}
    <ProcurementOperationsPanel orders={orders} onChanged={load} />
    <div className="tabs-header"><button className={`tab-btn ${tab==='orders'?'active':''}`} onClick={() => setTab('orders')}>Phiếu mua hàng</button><button className={`tab-btn ${tab==='suppliers'?'active':''}`} onClick={() => setTab('suppliers')}>Nhà cung cấp</button></div>
    <div className="card"><DataTable columns={tab === 'orders' ? orderColumns : supplierColumns} data={tab === 'orders' ? orders : suppliers} loading={loading} page={tab === 'orders' ? orderPage : 0} totalPages={tab === 'orders' ? orderPages : 1} totalElements={tab === 'orders' ? orderCount : suppliers.length} onPageChange={tab === 'orders' ? setOrderPage : undefined} /></div>
    <Modal isOpen={supplierOpen} onClose={() => setSupplierOpen(false)} title={editingSupplier ? "Cập nhật nhà cung cấp" : "Thêm nhà cung cấp"}><form onSubmit={saveSupplier}><div className="grid-2"><Input label="Mã *" required value={supplierForm.code} onChange={(e)=>setSupplierForm({...supplierForm,code:e.target.value})}/><Input label="Tên *" required value={supplierForm.name} onChange={(e)=>setSupplierForm({...supplierForm,name:e.target.value})}/><Input label="Người liên hệ" value={supplierForm.contactName} onChange={(e)=>setSupplierForm({...supplierForm,contactName:e.target.value})}/><Input label="Số điện thoại" value={supplierForm.phone} onChange={(e)=>setSupplierForm({...supplierForm,phone:e.target.value})}/><Input label="Email" type="email" value={supplierForm.email} onChange={(e)=>setSupplierForm({...supplierForm,email:e.target.value})}/><Input label="Mã số thuế" value={supplierForm.taxCode} onChange={(e)=>setSupplierForm({...supplierForm,taxCode:e.target.value})}/></div><Input label="Địa chỉ" value={supplierForm.address} onChange={(e)=>setSupplierForm({...supplierForm,address:e.target.value})}/><div style={{display:'flex',justifyContent:'flex-end',marginTop:16}}><Button type="submit">Lưu nhà cung cấp</Button></div></form></Modal>
    <Modal isOpen={orderOpen} onClose={closeOrder} title="Tạo phiếu mua hàng" maxWidth="900px">
      <form onSubmit={createOrder}>
        <p>Excel chỉ điền sẵn phiếu nháp. Kiểm tra thông tin trước khi lưu; chưa cộng tồn kho.</p>
        {importError && <AlertBanner type="danger" title="Chưa thể hoàn tất" message={importError} />}
        <fieldset disabled={orderSaving} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
          <Button variant="outline" disabled={importBusy} onClick={async () => { try { await downloadTemplate(); } catch { setImportError('Không tải được mẫu Excel.'); } }}>Tải mẫu Excel</Button>
          <Input label="Nhập file Excel" type="file" accept=".xlsx" onChange={importFile} helperText="Một sheet, tối đa 2 MiB / 500 dòng. SKU dạng text; ngày YYYY-MM-DD. Mã 000123 trong mẫu chỉ minh họa, cần thay bằng SKU thực tế." />
          <fieldset disabled={importBusy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
            <div className="grid-2">
              <Select label="Nhà cung cấp *" required value={orderForm.supplierId} onChange={e => setOrderForm({ ...orderForm, supplierId: e.target.value })} options={[{ label: 'Chọn nhà cung cấp', value: '' }, ...suppliers.map(s => ({ label: `${s.code} - ${s.name}`, value: String(s.id) }))]} />
              <Select label="Kho nhận *" required value={orderForm.warehouseId} onChange={e => setOrderForm({ ...orderForm, warehouseId: e.target.value })} options={[{ label: 'Chọn kho nhận', value: '' }, ...warehouses.map(w => ({ label: w.name, value: String(w.id) }))]} />
              <Input label="Ngày dự kiến" type="date" min={businessToday()} value={orderForm.expectedDate} onChange={e => setOrderForm({ ...orderForm, expectedDate: e.target.value })} />
              <Input label="Ghi chú" maxLength={500} value={orderForm.note} onChange={e => setOrderForm({ ...orderForm, note: e.target.value })} />
            </div>
            <h4>Chi tiết hàng hóa ({orderForm.items.length} dòng)</h4>
            {orderForm.items.some(item => item.sku !== undefined) && <Button variant="outline" onClick={lookupAgain}>Đối chiếu lại SKU</Button>}
            {orderForm.items.map((item, index) => <section key={index} aria-label={`Dòng hàng ${index + 1}`} style={{ borderBottom: '1px solid var(--border-subtle)', padding: '12px 0' }}>
              <strong>{item.sourceRow ? `Dòng Excel ${item.sourceRow}` : `Dòng ${index + 1}`}</strong>
              <div className="grid-3">
                {item.sku !== undefined
                  ? <Input label="SKU *" required maxLength={100} value={item.sku} error={itemErrors[index].sku} helperText={item.variantLabel ? `${item.variantLabel} · ID ${item.variantId}` : 'Sửa SKU rồi bấm Đối chiếu lại SKU.'} onChange={e => editItem(index, 'sku', e.target.value)} />
                  : <Input label="ID biến thể *" type="number" min="1" step="1" required value={item.variantId} error={itemErrors[index].variantId} onChange={e => editItem(index, 'variantId', e.target.value)} />}
                <Input label="Số lượng *" type="number" min="1" max="2147483647" step="1" required value={item.quantity} error={itemErrors[index].quantity} onChange={e => editItem(index, 'quantity', e.target.value)} />
                <Input label="Đơn giá vốn *" type="number" min="0" step="0.01" required value={item.unitCost} error={itemErrors[index].unitCost} onChange={e => editItem(index, 'unitCost', e.target.value)} />
                <Input label="Mã lô" maxLength={100} value={item.batchCode} error={itemErrors[index].batchCode} onChange={e => editItem(index, 'batchCode', e.target.value)} />
                <Input label="Hạn dùng" type={itemErrors[index].expirationDate ? 'text' : 'date'} value={item.expirationDate} error={itemErrors[index].expirationDate} onChange={e => editItem(index, 'expirationDate', e.target.value)} />
                <Button variant="danger" onClick={() => setOrderForm({ ...orderForm, items: orderForm.items.filter((_, i) => i !== index) })}>Xóa dòng</Button>
              </div>
            </section>)}
            <Button variant="outline" onClick={() => setOrderForm({ ...orderForm, items: [...orderForm.items, blankItem()] })}>+ Thêm dòng</Button>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}><Button type="submit" loading={orderSaving} disabled={!orderForm.items.length || hasItemErrors}>Tạo phiếu nháp</Button></div>
          </fieldset>
        </fieldset>
        {importBusy && <p role="status">Đang đọc Excel / đối chiếu SKU…</p>}
      </form>
    </Modal>
  </div>;
};
