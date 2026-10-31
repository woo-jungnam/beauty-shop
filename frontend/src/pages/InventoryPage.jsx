import { InventoryRecallPanel } from '../features/commerce/AdminCommercePanels';
import React, { useState, useEffect, useRef } from 'react';
import {
  Boxes,
  AlertTriangle,
  Clock,
  ClipboardCheck,
  Building2,
  RefreshCw,
  ArrowUpRight,
  ArrowDownLeft,
  Trash2,
  PackagePlus,
  Warehouse,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
  formatDate,
  formatNumber,
  resolveMediaUrl,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';
import { AlertBanner } from '../shared/ui/AlertBanner';

const TRANSACTION_TYPE_MAP = {
  RECEIPT: { label: 'Nhập kho', variant: 'success' },
  SALE: { label: 'Xuất bán hàng', variant: 'info' },
  RETURN: { label: 'Hàng trả (Cách ly)', variant: 'warning' },
  CANCELLATION: { label: 'Hủy đơn (Nhả giữ chỗ)', variant: 'default' },
  ADJUSTMENT: { label: 'Điều chỉnh kiểm kê', variant: 'warning' },
  DISPOSAL: { label: 'Xuất tiêu hủy', variant: 'danger' },
  TRANSFER_IN: { label: 'Nhận chuyển kho', variant: 'success' },
  TRANSFER_OUT: { label: 'Xuất chuyển kho', variant: 'info' },
};

const REFERENCE_TYPE_MAP = {
  ORDER: 'Đơn hàng',
  TRANSFER: 'Điều chuyển kho',
  STOCKTAKE: 'Kiểm kê kho',
  STOCK_DELETE: 'Xóa lô hàng',
  MANUAL_STOCK: 'Cập nhật lô hàng',
  MANUAL_RECEIPT: 'Nhập kho trực tiếp',
  PURCHASE_ORDER: 'Phiếu mua hàng',
  RETURN_INSPECTION: 'Xử lý hàng hoàn',
};

const WAREHOUSE_TYPE_MAP = {
  CENTRAL: 'Kho Tổng',
  BRANCH: 'Chi Nhánh / Spa',
  TRANSIT: 'Trung Chuyển',
};

const getDaysUntilExpiration = (expDate) => {
  if (!expDate) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(expDate);
  target.setHours(0, 0, 0, 0);
  const diffTime = target - today;
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
};

export const InventoryPage = () => {
  const [activeTab, setActiveTab] = useState('warehouses');
  const [inventoryNotice, setInventoryNotice] = useState('');
  const inboundKey = useRef(crypto.randomUUID());
  const transferKey = useRef(crypto.randomUUID());
  const [loading, setLoading] = useState(false);

  const [transactions, setTransactions] = useState([]);
  const [ledgerPage, setLedgerPage] = useState(0);
  const [ledgerTotalPages, setLedgerTotalPages] = useState(1);
  const [ledgerTotalElements, setLedgerTotalElements] = useState(0);
  const [filterStockId, setFilterStockId] = useState('');
  const [filterVariantId, setFilterVariantId] = useState('');
  const [appliedFilters, setAppliedFilters] = useState({ stockId: '', variantId: '' });
  const [selectedTx, setSelectedTx] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);

  const [lowStockList, setLowStockList] = useState([]);
  const [expiringList, setExpiringList] = useState([]);

  const [warehouses, setWarehouses] = useState([]);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [warehouseStocks, setWarehouseStocks] = useState([]);

  const [inspectModalOpen, setInspectModalOpen] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [inspectQuantity, setInspectQuantity] = useState(1);
  const [inspectRestock, setInspectRestock] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [createWhModalOpen, setCreateWhModalOpen] = useState(false);
  const [whName, setWhName] = useState('');
  const [whCode, setWhCode] = useState('');
  const [whAddress, setWhAddress] = useState('');
  const [whCity, setWhCity] = useState('TP. Hồ Chí Minh');
  const [whPhone, setWhPhone] = useState('');
  const [whManager, setWhManager] = useState('');
  const [whType, setWhType] = useState('CENTRAL');
  const [whSubmitting, setWhSubmitting] = useState(false);

  const [inboundModalOpen, setInboundModalOpen] = useState(false);
  const [inboundMethod, setInboundMethod] = useState('RECEIPT');
  const [inboundVariantId, setInboundVariantId] = useState('');
  const [inboundQuantity, setInboundQuantity] = useState('50');
  const [inboundCostPrice, setInboundCostPrice] = useState('150000');
  const [inboundBatchCode, setInboundBatchCode] = useState('');
  const [inboundExpDate, setInboundExpDate] = useState('');
  const [inboundLocation, setInboundLocation] = useState('Kệ A-01');
  const [inboundMinQty, setInboundMinQty] = useState('10');
  const [inboundMaxQty, setInboundMaxQty] = useState('500');
  const [inboundSubmitting, setInboundSubmitting] = useState(false);
  const [catalogVariants, setCatalogVariants] = useState([]);
  const [variantSearch, setVariantSearch] = useState('');

  const [adjustModalOpen, setAdjustModalOpen] = useState(false);
  const [selectedStockForAdjust, setSelectedStockForAdjust] = useState(null);
  const [adjustQuantityAfter, setAdjustQuantityAfter] = useState('0');
  const [adjustReason, setAdjustReason] = useState('Kiểm kê kho định kỳ');
  const [adjustSubmitting, setAdjustSubmitting] = useState(false);

  const [transferModalOpen, setTransferModalOpen] = useState(false);
  const [selectedStockForTransfer, setSelectedStockForTransfer] = useState(null);
  const [targetWhId, setTargetWhId] = useState('');
  const [transferQuantity, setTransferQuantity] = useState('1');
  const [transferReason, setTransferReason] = useState('Điều phối luân chuyển hàng giữa các kho');
  const [transferSubmitting, setTransferSubmitting] = useState(false);

  const fetchLedger = async (p = 0, currentFilters = appliedFilters) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(p), size: '15' });
      if (currentFilters.stockId?.trim()) params.append('stockId', currentFilters.stockId.trim());
      else if (currentFilters.variantId?.trim()) params.append('variantId', currentFilters.variantId.trim());
      const res = await apiClient.get(`${ENDPOINTS.INVENTORY.TRANSACTIONS}?${params.toString()}`);
      const pageData = res.data || res;
      setTransactions(pageData.content || []);
      setLedgerPage(pageData.page ?? p);
      setLedgerTotalPages(pageData.totalPages ?? 1);
      setLedgerTotalElements(pageData.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  };

  const handleApplyFilter = (e) => {
    e.preventDefault();
    const newFilters = { stockId: filterStockId, variantId: filterVariantId };
    setAppliedFilters(newFilters);
    fetchLedger(0, newFilters);
  };

  const handleResetFilter = () => {
    setFilterStockId('');
    setFilterVariantId('');
    const newFilters = { stockId: '', variantId: '' };
    setAppliedFilters(newFilters);
    fetchLedger(0, newFilters);
  };

  const handleOpenTxDetail = async (tx) => {
    setSelectedTx(tx);
    setDetailModalOpen(true);
    if (tx?.id) {
      try {
        const res = await apiClient.get(ENDPOINTS.INVENTORY.TRANSACTION_DETAIL(tx.id));
        const fullData = res.data || res;
        if (fullData) setSelectedTx(fullData);
      } catch (err) {
        console.warn('Could not fetch latest transaction detail', err);
      }
    }
  };

  const fetchAlerts = async () => {
    setLoading(true);
    try {
      const [lowRes, expRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.INVENTORY.LOW_STOCK),
        apiClient.get(`${ENDPOINTS.INVENTORY.EXPIRING_SOON}?days=30`),
      ]);
      if (lowRes.status === 'fulfilled') setLowStockList(lowRes.value.data || lowRes.value || []);
      if (expRes.status === 'fulfilled') setExpiringList(expRes.value.data || expRes.value || []);
    } finally {
      setLoading(false);
    }
  };

  const fetchWarehouses = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get(ENDPOINTS.INVENTORY.WAREHOUSES);
      const list = res.data || res || [];
      setWarehouses(list);
      if (list.length > 0 && !selectedWarehouseId) {
        setSelectedWarehouseId(list[0].id);
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchWarehouseStocks = async (whId) => {
    if (!whId) return;
    setLoading(true);
    try {
      const res = await apiClient.get(ENDPOINTS.INVENTORY.WAREHOUSE_STOCKS(whId));
      setWarehouseStocks(res.data || res || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'warehouses') fetchWarehouses();
    else if (activeTab === 'alerts') fetchAlerts();
    else if (activeTab === 'ledger') fetchLedger(0);
  }, [activeTab]);

  useEffect(() => {
    if (activeTab === 'warehouses' && selectedWarehouseId) {
      fetchWarehouseStocks(selectedWarehouseId);
    }
  }, [selectedWarehouseId, activeTab]);

  const handleOpenInspect = (stock) => {
    setInventoryNotice('');
    setSelectedStock(stock);
    const maxQty = stock.quarantinedQuantity || 1;
    setInspectQuantity(Math.min(1, maxQty));
    setInspectRestock(true);
    setInspectModalOpen(true);
  };

  const handleInspectSubmit = async (e) => {
    e.preventDefault();
    const stockId = selectedStock?.id || selectedStock?.stockId;
    if (!stockId) {
      alert('Không tìm thấy thông tin lô hàng hợp lệ.');
      return;
    }
    const cleanQtyStr = String(inspectQuantity).replace(/\./g, '').replace(/,/g, '').trim();
    const parsedQty = parseInt(cleanQtyStr, 10);
    const maxQuarantined = selectedStock?.quarantinedQuantity || 0;
    if (isNaN(parsedQty) || parsedQty <= 0) {
      alert('Số lượng hàng kiểm định phải là số nguyên lớn hơn 0.');
      return;
    }
    if (parsedQty > maxQuarantined) {
      alert(`Số lượng xử lý không được vượt quá số lượng hàng đang cách ly (${maxQuarantined}).`);
      return;
    }
    setSubmitting(true);
    try {
      await apiClient.post(ENDPOINTS.INVENTORY.INSPECTION(stockId), {
        quantity: parsedQty,
        restock: inspectRestock,
      });
      setInspectModalOpen(false);
      setInventoryNotice(`Xử lý kiểm định thành công ${parsedQty} sản phẩm hoàn trả (${inspectRestock ? 'Đã nhập lại kho bán' : 'Đã xuất tiêu hủy'}).`);
      if (activeTab === 'warehouses') fetchWarehouseStocks(selectedWarehouseId);
      else fetchAlerts();
    } catch (err) {
      alert(err.message || 'Lỗi khi xử lý hàng kiểm định');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateWarehouse = async (e) => {
    e.preventDefault();
    setWhSubmitting(true);
    try {
      const payload = {
        name: whName.trim(),
        code: whCode.trim().toUpperCase(),
        address: whAddress.trim(),
        city: whCity.trim(),
        phone: whPhone.trim(),
        managerName: whManager.trim(),
        warehouseType: whType,
      };
      await apiClient.post(ENDPOINTS.INVENTORY.WAREHOUSES, payload);
      setCreateWhModalOpen(false);
      fetchWarehouses();
    } catch (err) {
      alert(err.message || 'Lỗi tạo kho bãi');
    } finally {
      setWhSubmitting(false);
    }
  };

  const handleDeleteWarehouse = async (id) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa kho bãi #${id}? (Kho phải hết sạch tồn kho mới được xóa)`)) return;
    try {
      await apiClient.delete(ENDPOINTS.INVENTORY.WAREHOUSE_DETAIL(id));
      setSelectedWarehouseId(null);
      fetchWarehouses();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa kho bãi');
    }
  };

  const fetchCatalogVariants = async () => {
    try {
      const res = await apiClient.get('/api/v1/products?size=100');
      const list = res?.data?.content || res?.content || res?.data || (Array.isArray(res) ? res : []);
      const flattened = [];
      list.forEach(p => {
        (p.variants || []).forEach(v => {
          flattened.push({
            id: v.id,
            productId: p.id,
            productName: p.name,
            variantName: v.variantName || v.volume || 'Tiêu chuẩn',
            sku: v.sku,
            price: v.price || p.minPrice || 0,
            imageUrl: v.imageUrl || p.thumbnailUrl,
          });
        });
      });
      setCatalogVariants(flattened);
      if (flattened.length > 0 && !inboundVariantId) {
        setInboundVariantId(String(flattened[0].id));
        setInboundCostPrice(String(Math.round(flattened[0].price * 0.7)));
      }
    } catch (e) {
      console.warn('Could not load products for inbound picker', e);
    }
  };

  const handleOpenInboundModal = () => {
    fetchCatalogVariants();
    const todayStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    setInboundBatchCode(`LOT-${todayStr}-01`);
    const futureDate = new Date();
    futureDate.setFullYear(futureDate.getFullYear() + 2);
    setInboundExpDate(futureDate.toISOString().slice(0, 10));
    setInboundModalOpen(true);
  };

  const handleInboundStock = async (e) => {
    e.preventDefault();
    if (!selectedWarehouseId) {
      alert('Vui lòng chọn một kho bãi trước khi nhập hàng.');
      return;
    }
    if (!inboundVariantId) {
      alert('Vui lòng chọn sản phẩm / biến thể cần nhập kho.');
      return;
    }
    setInboundSubmitting(true);
    try {
      const payload = {
        productVariantId: parseInt(inboundVariantId, 10),
        quantity: parseInt(inboundQuantity, 10),
        reservedQuantity: 0,
        costPrice: parseFloat(inboundCostPrice || 0),
        batchCode: inboundBatchCode.trim() || undefined,
        expirationDate: inboundExpDate || undefined,
        location: inboundLocation.trim() || undefined,
        minQuantity: parseInt(inboundMinQty, 10) || 0,
        maxQuantity: inboundMaxQty ? parseInt(inboundMaxQty, 10) : undefined,
      };
      if (inboundMethod === 'RECEIPT') {
        await apiClient.post(ENDPOINTS.INVENTORY.WAREHOUSE_RECEIPTS(selectedWarehouseId), payload, { headers: { 'Idempotency-Key': inboundKey.current } });
        inboundKey.current = crypto.randomUUID();
      } else {
        await apiClient.post(ENDPOINTS.INVENTORY.WAREHOUSE_STOCKS(selectedWarehouseId), payload);
      }
      setInboundModalOpen(false);
      fetchWarehouseStocks(selectedWarehouseId);
    } catch (err) {
      alert(err.message || 'Lỗi nhập hàng vào kho');
    } finally {
      setInboundSubmitting(false);
    }
  };

  const handleDeleteStock = async (stockId) => {
    if (!window.confirm(`Bạn có chắc muốn xóa lô tồn kho #${stockId}? Số tồn còn lại sẽ được ghi nhận xuất hủy.`)) return;
    try {
      await apiClient.delete(ENDPOINTS.INVENTORY.STOCK_DELETE(stockId));
      fetchWarehouseStocks(selectedWarehouseId);
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa lô tồn kho');
    }
  };

  const handleOpenAdjustModal = (stock) => {
    setInventoryNotice('');
    const normalizedStock = {
      ...stock,
      id: stock.id || stock.stockId,
    };
    setSelectedStockForAdjust(normalizedStock);
    setAdjustQuantityAfter(String(stock.quantity ?? 0));
    setAdjustReason('Kiểm kê kho định kỳ');
    setAdjustModalOpen(true);
  };

  const handleAdjustSubmit = async (e) => {
    e.preventDefault();
    const stockId = selectedStockForAdjust?.id || selectedStockForAdjust?.stockId;
    if (!stockId) {
      alert('Không tìm thấy mã lô tồn kho hợp lệ để kiểm kê.');
      return;
    }
    const cleanQtyStr = String(adjustQuantityAfter).replace(/\./g, '').replace(/,/g, '').trim();
    const parsedQty = parseInt(cleanQtyStr, 10);
    if (isNaN(parsedQty) || parsedQty < 0) {
      alert('Số lượng tồn thực tế sau kiểm kê phải là số nguyên không âm.');
      return;
    }
    const reserved = selectedStockForAdjust?.reservedQuantity ?? 0;
    if (parsedQty < reserved) {
      alert(`Số lượng sau kiểm kê không được nhỏ hơn số lượng hàng đang giữ chỗ cho đơn (${reserved}).`);
      return;
    }
    if (!adjustReason.trim()) {
      alert('Vui lòng nhập lý do điều chỉnh kiểm kê.');
      return;
    }
    setAdjustSubmitting(true);
    try {
      await apiClient.put(ENDPOINTS.INVENTORY.STOCK_ADJUSTMENT(stockId), {
        quantityAfter: parsedQty,
        reason: adjustReason.trim(),
      });
      setAdjustModalOpen(false);
      const stockLabel = selectedStockForAdjust.sku || selectedStockForAdjust.batchCode || selectedStockForAdjust.productName || `#${stockId}`;
      setInventoryNotice(`Ghi nhận kiểm kê kho thành công cho lô ${stockLabel}. Số tồn mới: ${parsedQty}.`);
      if (activeTab === 'warehouses') fetchWarehouseStocks(selectedWarehouseId);
      else fetchAlerts();
    } catch (err) {
      alert(err.message || 'Lỗi điều chỉnh tồn kho');
    } finally {
      setAdjustSubmitting(false);
    }
  };

  const handleOpenTransferModal = (stock) => {
    const normalizedStock = {
      ...stock,
      id: stock.id || stock.stockId,
    };
    setSelectedStockForTransfer(normalizedStock);
    const targets = warehouses.filter((warehouse) => String(warehouse.id) !== String(selectedWarehouseId));
    setTargetWhId(targets[0]?.id ? String(targets[0].id) : '');
    setTransferQuantity('1');
    setTransferReason('Điều phối hàng giữa các kho');
    setTransferModalOpen(true);
  };

  const handleTransferSubmit = async (e) => {
    e.preventDefault();
    if (!selectedStockForTransfer || !targetWhId) return;
    setTransferSubmitting(true);
    try {
      await apiClient.post(ENDPOINTS.INVENTORY.STOCK_TRANSFER(selectedStockForTransfer.id), {
        targetWarehouseId: parseInt(targetWhId, 10),
        quantity: parseInt(transferQuantity, 10),
        reason: transferReason.trim(),
      }, { headers: { 'Idempotency-Key': transferKey.current } });
      transferKey.current = crypto.randomUUID();
      setTransferModalOpen(false);
      fetchWarehouseStocks(selectedWarehouseId);
    } catch (err) {
      alert(err.message || 'Lỗi khi điều chuyển kho');
    } finally {
      setTransferSubmitting(false);
    }
  };

  const ledgerColumns = [
    {
      header: 'Thời điểm',
      accessor: (row) => formatDateTime(row.occurredAt),
      width: '130px',
    },
    {
      header: 'Biến thể SKU / Sản phẩm',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.productName || (row.sku ? `SKU: ${row.sku}` : `Biến thể #${row.productVariantId || row.variantId}`)}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            Mã SKU: {row.sku || '-'}
            {row.variantName ? ` · ${row.variantName}` : ''}
            {row.batchCode ? ` · Lô: ${row.batchCode}` : ''}
          </div>
        </div>
      ),
    },
    {
      header: 'Kho bãi',
      accessor: (row) => row.warehouseName || `Kho #${row.warehouseId}`,
      width: '130px',
    },
    {
      header: 'Loại biến động',
      accessor: (row) => {
        const type = row.type || row.transactionType;
        const meta = TRANSACTION_TYPE_MAP[type] || { label: type || 'Khác', variant: 'default' };
        return <Badge variant={meta.variant}>{meta.label}</Badge>;
      },
      width: '140px',
    },
    {
      header: 'Biến động',
      accessor: (row) => {
        const qty = row.quantity ?? row.quantityChange ?? 0;
        return (
          <span
            style={{
              fontWeight: 700,
              fontVariantNumeric: 'tabular-nums',
              color: qty > 0 ? 'var(--color-success-700)' : qty < 0 ? 'var(--color-danger-700)' : 'var(--text-muted)',
            }}
          >
            {qty > 0 ? `+${qty}` : qty}
          </span>
        );
      },
      align: 'right',
      width: '90px',
    },
    {
      header: 'Tồn sau',
      accessor: (row) => (
        <span style={{ fontSize: '12px', fontVariantNumeric: 'tabular-nums' }}>
          {row.quantityBefore != null && row.quantityAfter != null
            ? `${row.quantityBefore} → ${row.quantityAfter}`
            : (row.quantityAfter ?? '-')}
        </span>
      ),
      align: 'right',
      width: '100px',
    },
    {
      header: 'Đơn giá vốn',
      accessor: (row) => formatCurrency(row.unitCost),
      align: 'right',
      width: '110px',
    },
    {
      header: 'Tham chiếu & Ghi chú',
      accessor: (row) => {
        const refLabel = REFERENCE_TYPE_MAP[row.referenceType] || row.referenceType;
        return (
          <div style={{ fontSize: '12px' }}>
            <div>{refLabel ? `${refLabel}: ${row.referenceId || ''}` : '-'}</div>
            {row.note && <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{row.note}</div>}
          </div>
        );
      },
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <Button size="sm" variant="outline" onClick={() => handleOpenTxDetail(row)}>
          Chi tiết
        </Button>
      ),
      align: 'center',
      width: '85px',
    },
  ];

  const alertColumns = [
    {
      header: 'Mã lô',
      accessor: 'stockId',
      width: '80px',
    },
    {
      header: 'Kho bãi',
      accessor: 'warehouseName',
    },
    {
      header: 'Sản phẩm & Phân loại',
      accessor: (row) => {
        const img = row.productThumbnailUrl ? resolveMediaUrl(row.productThumbnailUrl) : null;
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {img && (
              <img src={img} alt="" style={{ width: 34, height: 34, objectFit: 'cover', borderRadius: 4, flexShrink: 0 }} onError={e => { e.currentTarget.style.display = 'none'; }} />
            )}
            <div>
              <div style={{ fontWeight: 600 }}>{row.productName || (row.sku ? `SKU: ${row.sku}` : `Biến thể #${row.productVariantId}`)}</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', gap: 6 }}>
                {row.variantName && <span>{row.variantName}</span>}
                {row.sku && <span>· SKU: <code>{row.sku}</code></span>}
                {row.batchCode && <span>· Lô: {row.batchCode}</span>}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      header: 'Tồn thực tế',
      accessor: (row) => formatNumber(row.quantity),
      align: 'right',
    },
    {
      header: 'Khả dụng bán',
      accessor: (row) => (
        <strong style={{ color: 'var(--color-primary-700)' }}>
          {formatNumber(Math.max(0, (row.quantity ?? 0) - (row.reservedQuantity ?? 0)))}
        </strong>
      ),
      align: 'right',
    },
    {
      header: 'Mức tối thiểu',
      accessor: (row) => formatNumber(row.minQuantity ?? 0),
      align: 'right',
    },
    {
      header: 'Hạn dùng (FEFO)',
      accessor: (row) => {
        const daysLeft = getDaysUntilExpiration(row.expirationDate);
        return (
          <span style={{ color: daysLeft != null && daysLeft <= 7 ? 'var(--color-danger-700)' : 'inherit', fontWeight: 600 }}>
            {formatDate(row.expirationDate) || 'Không có'}
            {daysLeft != null && (
              <span style={{ fontSize: '11px', display: 'block' }}>
                {daysLeft < 0 ? '(Đã hết hạn)' : `(Còn ${daysLeft} ngày)`}
              </span>
            )}
          </span>
        );
      },
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <Button
          variant="outline"
          size="sm"
          onClick={() => handleOpenAdjustModal(row)}
          icon={ArrowUpRight}
          title="Kiểm kê / Kiểm định số lượng tồn kho"
        >
          Kiểm kê
        </Button>
      ),
      align: 'right',
      width: '110px',
    },
  ];

  const warehouseStockColumns = [
    {
      header: 'Mã lô',
      accessor: 'id',
      width: '60px',
    },
    {
      header: 'Sản phẩm & Biến thể',
      accessor: (row) => {
        const img = row.productThumbnailUrl ? resolveMediaUrl(row.productThumbnailUrl) : null;
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 40, height: 40, borderRadius: 4, overflow: 'hidden', backgroundColor: 'var(--color-primary-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              {img ? (
                <img src={img} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={e => { e.currentTarget.style.display = 'none'; }} />
              ) : (
                <Boxes size={18} color="var(--text-muted)" />
              )}
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>{row.productName || `Biến thể #${row.productVariantId}`}</div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', gap: 6, marginTop: 2 }}>
                {row.variantName && <span>{row.variantName}</span>}
                {row.sku && <span>· SKU: <code>{row.sku}</code></span>}
              </div>
            </div>
          </div>
        );
      },
    },
    {
      header: 'Mã lô SX',
      accessor: (row) => row.batchCode ? <code style={{ fontWeight: 600 }}>{row.batchCode}</code> : 'Mặc định',
      width: '110px',
    },
    {
      header: 'Tồn bán được',
      accessor: (row) => <strong style={{ color: 'var(--color-success-700)', fontSize: 13 }}>{formatNumber(row.quantity)}</strong>,
      align: 'right',
      width: '100px',
    },
    {
      header: 'Đang giữ cho đơn',
      accessor: (row) => formatNumber(row.reservedQuantity || 0),
      align: 'right',
      width: '110px',
    },
    {
      header: 'Hàng cách ly (Hoàn)',
      accessor: (row) =>
        row.quarantinedQuantity > 0 ? (
          <Badge variant="warning">{formatNumber(row.quarantinedQuantity)}</Badge>
        ) : (
          <span style={{ color: 'var(--text-muted)' }}>0</span>
        ),
      align: 'right',
      width: '110px',
    },
    {
      header: 'Đơn giá vốn',
      accessor: (row) => formatCurrency(row.costPrice),
      align: 'right',
      width: '110px',
    },
    {
      header: 'Hạn dùng & Vị trí',
      accessor: (row) => {
        const daysLeft = getDaysUntilExpiration(row.expirationDate);
        return (
          <div>
            <div style={{ color: daysLeft != null && daysLeft <= 30 ? 'var(--color-danger-700)' : 'inherit', fontWeight: 600 }}>
              {formatDate(row.expirationDate) || 'Không có'}
              {daysLeft != null && (
                <span style={{ fontSize: '11px', display: 'block', fontWeight: 400 }}>
                  {daysLeft < 0 ? '(Đã hết hạn)' : `(Còn ${daysLeft} ngày)`}
                </span>
              )}
            </div>
            {row.location && <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: 2 }}>Vị trí: {row.location}</div>}
          </div>
        );
      },
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          {row.quarantinedQuantity > 0 && (
            <Button
              variant="warning"
              size="sm"
              onClick={() => handleOpenInspect(row)}
              icon={ClipboardCheck}
              title="Xử lý hàng hoàn trả cách ly"
            >
              Xử lý hoàn
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenAdjustModal(row)}
            icon={ArrowUpRight}
            title="Điều chỉnh số lượng sau kiểm kê"
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenTransferModal(row)}
            icon={ArrowDownLeft}
            title="Chuyển sang kho khác"
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleDeleteStock(row.id)}
            icon={Trash2}
            title="Xóa lô tồn kho này"
          />
        </div>
      ),
      align: 'right',
      width: '160px',
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản lý Kho bãi & Sổ cái Tồn kho FEFO</h1>
          <p className="page-subtitle">
            Theo dõi luân chuyển tồn kho, kiểm soát hạn sử dụng mỹ phẩm và điều phối hàng hóa giữa các kho
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          {activeTab === 'warehouses' && (
            <>
              <Button variant="outline" size="sm" onClick={() => setCreateWhModalOpen(true)} icon={Warehouse}>
                Tạo kho mới
              </Button>
              <Button variant="primary" size="sm" onClick={handleOpenInboundModal} icon={PackagePlus}>
                Nhập kho hàng hóa
              </Button>
            </>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (activeTab === 'ledger') fetchLedger(ledgerPage);
              else if (activeTab === 'alerts') fetchAlerts();
              else if (activeTab === 'warehouses') fetchWarehouseStocks(selectedWarehouseId);
            }}
            loading={loading}
            icon={RefreshCw}
          >
            Làm mới
          </Button>
        </div>
      </div>
      {inventoryNotice && (
        <AlertBanner
          type="success"
          title="Thành công"
          message={inventoryNotice}
          action={<Button size="sm" variant="outline" onClick={() => setInventoryNotice('')}>Đóng</Button>}
        />
      )}

      <InventoryRecallPanel />
      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'warehouses' ? 'active' : ''}`}
          onClick={() => setActiveTab('warehouses')}
        >
          <Building2 size={15} />
          Kho bãi & Quản lý lô hàng ({warehouses.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'alerts' ? 'active' : ''}`}
          onClick={() => setActiveTab('alerts')}
        >
          <AlertTriangle size={15} />
          Cảnh báo tồn kho & Cận hạn ({lowStockList.length + expiringList.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'ledger' ? 'active' : ''}`}
          onClick={() => setActiveTab('ledger')}
        >
          <Boxes size={15} />
          Sổ cái biến động kho (Lịch sử xuất nhập) ({ledgerTotalElements})
        </button>
      </div>

      {activeTab === 'warehouses' && (
        <div>
          <div className="admin-warehouse-toolbar">
            <div className="admin-warehouse-picker">
              <label htmlFor="admin-warehouse-select" className="form-label">Chọn kho làm việc</label>
              <Select
                id="admin-warehouse-select"
                value={selectedWarehouseId || ''}
                onChange={(e) => setSelectedWarehouseId(e.target.value)}
                options={warehouses.map((w) => ({
                  label: `${w.name} (${w.code}) · ${WAREHOUSE_TYPE_MAP[w.warehouseType] || w.warehouseType || 'Kho'}`,
                  value: String(w.id),
                }))}
                className="admin-warehouse-field"
              />
            </div>
            {selectedWarehouseId && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleDeleteWarehouse(selectedWarehouseId)}
                icon={Trash2}
                title="Xóa kho bãi này"
              >
                Xóa kho bãi
              </Button>
            )}
          </div>

          <div className="card">
            <div className="card-header">
              <span className="card-title">Danh sách các lô tồn kho trong kho đang chọn</span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Tổng cộng {warehouseStocks.length} lô hàng
              </span>
            </div>
            <DataTable
              columns={warehouseStockColumns}
              data={warehouseStocks}
              loading={loading}
              emptyMessage="Kho này hiện chưa có dữ liệu tồn kho. Bấm 'Nhập kho hàng hóa' để thêm."
            />
          </div>
        </div>
      )}

      {activeTab === 'alerts' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
              <AlertTriangle size={18} color="var(--color-warning-600)" />
              <h2 style={{ fontSize: '15px', fontWeight: 700, textTransform: 'uppercase' }}>
                Mặt hàng chạm ngưỡng tồn kho tối thiểu ({lowStockList.length})
              </h2>
            </div>
            <DataTable
              columns={alertColumns}
              data={lowStockList}
              loading={loading}
              emptyMessage="Không có mặt hàng nào bị cạn kho."
            />
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
              <Clock size={18} color="var(--color-danger-600)" />
              <h2 style={{ fontSize: '15px', fontWeight: 700, textTransform: 'uppercase' }}>
                Lô hàng cận hạn sử dụng trong 30 ngày (FEFO) ({expiringList.length})
              </h2>
            </div>
            <DataTable
              columns={alertColumns}
              data={expiringList}
              loading={loading}
              emptyMessage="Không có lô mỹ phẩm nào cận hạn dùng."
            />
          </div>
        </div>
      )}

      {activeTab === 'ledger' && (
        <div className="card">
          <form
            onSubmit={handleApplyFilter}
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '12px',
              alignItems: 'flex-end',
              marginBottom: '16px',
              paddingBottom: '16px',
              borderBottom: '1px solid var(--border-subtle)',
            }}
          >
            <div style={{ flex: '1 1 180px' }}>
              <Input
                label="Lọc theo ID lô hàng (Stock ID)"
                type="number"
                min="1"
                placeholder="VD: 10"
                value={filterStockId}
                onChange={(e) => setFilterStockId(e.target.value)}
              />
            </div>
            <div style={{ flex: '1 1 180px' }}>
              <Input
                label="Lọc theo ID biến thể SKU"
                type="number"
                min="1"
                placeholder="VD: 30"
                value={filterVariantId}
                onChange={(e) => setFilterVariantId(e.target.value)}
              />
            </div>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '2px' }}>
              <Button type="submit" variant="primary" size="sm">
                Lọc
              </Button>
              {(filterStockId || filterVariantId || appliedFilters.stockId || appliedFilters.variantId) && (
                <Button type="button" variant="outline" size="sm" onClick={handleResetFilter}>
                  Đặt lại
                </Button>
              )}
            </div>
          </form>

          <DataTable
            columns={ledgerColumns}
            data={transactions}
            loading={loading}
            emptyMessage="Chưa có bản ghi biến động kho nào."
          />
          {ledgerTotalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
              <Button variant="outline" size="sm" disabled={ledgerPage === 0} onClick={() => fetchLedger(ledgerPage - 1)}>
                Trang trước
              </Button>
              <span style={{ fontSize: '12px', display: 'flex', alignItems: 'center' }}>
                Trang {ledgerPage + 1} / {ledgerTotalPages}
              </span>
              <Button variant="outline" size="sm" disabled={ledgerPage >= ledgerTotalPages - 1} onClick={() => fetchLedger(ledgerPage + 1)}>
                Trang sau
              </Button>
            </div>
          )}
        </div>
      )}

      <Modal
        isOpen={createWhModalOpen}
        onClose={() => setCreateWhModalOpen(false)}
        title="Thêm Kho Bãi Mới"
        maxWidth="520px"
      >
        <form onSubmit={handleCreateWarehouse}>
          <div className="grid-2">
            <Input label="Tên kho *" value={whName} onChange={(e) => setWhName(e.target.value)} required placeholder="Kho Tổng Tân Bình" />
            <Input label="Mã kho (Code) *" value={whCode} onChange={(e) => setWhCode(e.target.value)} required placeholder="KHO-TB-01" />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Tỉnh / Thành phố" value={whCity} onChange={(e) => setWhCity(e.target.value)} />
            <Input label="Số điện thoại kho" value={whPhone} onChange={(e) => setWhPhone(e.target.value)} placeholder="0901234567" />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input label="Địa chỉ cụ thể" value={whAddress} onChange={(e) => setWhAddress(e.target.value)} placeholder="123 Hoàng Hoa Thám, P.13" />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Thủ kho / Người quản lý" value={whManager} onChange={(e) => setWhManager(e.target.value)} placeholder="Nguyễn Văn A" />
            <Select
              label="Loại kho bãi"
              value={whType}
              onChange={(e) => setWhType(e.target.value)}
              options={[
                { label: 'Kho Tổng (Central)', value: 'CENTRAL' },
                { label: 'Kho Chi Nhánh / Spa (Branch)', value: 'BRANCH' },
                { label: 'Kho Trung Chuyển (Transit)', value: 'TRANSIT' },
              ]}
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setCreateWhModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={whSubmitting}>Lưu kho bãi</Button>
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={inboundModalOpen}
        onClose={() => setInboundModalOpen(false)}
        title={`Nhập Hàng Vào Kho: ${warehouses.find((w) => String(w.id) === String(selectedWarehouseId))?.name || 'Kho đã chọn'}`}
        maxWidth="640px"
      >
        <form onSubmit={handleInboundStock}>
          <div style={{ backgroundColor: '#F9FAFB', padding: '12px 14px', borderRadius: 8, border: '1px solid #E5E7EB', marginBottom: 16 }}>
            <label style={{ fontSize: 12, fontWeight: 700, display: 'block', marginBottom: 8, color: 'var(--c-primary)' }}>Phương thức nhập kho *</label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <label style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '10px 12px', border: inboundMethod === 'RECEIPT' ? '2px solid var(--sp-primary)' : '1px solid #D1D5DB', backgroundColor: inboundMethod === 'RECEIPT' ? '#EFF6FF' : '#FFFFFF', borderRadius: 6, cursor: 'pointer' }}>
                <input type="radio" name="inboundMethod" value="RECEIPT" checked={inboundMethod === 'RECEIPT'} onChange={() => setInboundMethod('RECEIPT')} style={{ marginTop: 2 }} />
                <div>
                  <strong style={{ fontSize: 13, display: 'block' }}>Nhập lô hàng mới</strong>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Cộng dồn vào tồn kho & tự động tính giá vốn bình quân gia quyền</span>
                </div>
              </label>
              <label style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '10px 12px', border: inboundMethod === 'ABSOLUTE' ? '2px solid var(--sp-primary)' : '1px solid #D1D5DB', backgroundColor: inboundMethod === 'ABSOLUTE' ? '#EFF6FF' : '#FFFFFF', borderRadius: 6, cursor: 'pointer' }}>
                <input type="radio" name="inboundMethod" value="ABSOLUTE" checked={inboundMethod === 'ABSOLUTE'} onChange={() => setInboundMethod('ABSOLUTE')} style={{ marginTop: 2 }} />
                <div>
                  <strong style={{ fontSize: 13, display: 'block' }}>Đặt tồn tuyệt đối</strong>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Ghi đè số lượng tồn kho (khởi tạo kho hoặc đối soát kiểm kê)</span>
                </div>
              </label>
            </div>
          </div>

          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 700, display: 'block', marginBottom: 6 }}>Chọn mặt hàng / SKU cần nhập *</label>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
              <input
                type="text"
                placeholder="Tìm nhanh theo tên sản phẩm hoặc mã SKU..."
                value={variantSearch}
                onChange={e => setVariantSearch(e.target.value)}
                style={{ flex: 1, padding: '8px 12px', fontSize: 12.5, borderRadius: 6, border: '1px solid #D1D5DB' }}
              />
            </div>

            <select
              value={inboundVariantId}
              onChange={e => {
                const vid = e.target.value;
                setInboundVariantId(vid);
                const selected = catalogVariants.find(v => String(v.id) === String(vid));
                if (selected) {
                  setInboundCostPrice(String(Math.round(selected.price * 0.7)));
                }
              }}
              required
              style={{ width: '100%', padding: '9px 12px', fontSize: 13, borderRadius: 6, border: '1px solid #D1D5DB', backgroundColor: '#FFFFFF' }}
            >
              <option value="">-- Chọn sản phẩm & phân loại SKU --</option>
              {catalogVariants
                .filter(v => {
                  if (!variantSearch.trim()) return true;
                  const q = variantSearch.toLowerCase();
                  return (v.productName || '').toLowerCase().includes(q) || (v.sku || '').toLowerCase().includes(q) || (v.variantName || '').toLowerCase().includes(q);
                })
                .map(v => (
                  <option key={v.id} value={v.id}>
                    [{v.sku}] {v.productName} · {v.variantName} ({formatCurrency(v.price)})
                  </option>
                ))}
            </select>
          </div>

          {(() => {
            const currentSelected = catalogVariants.find(v => String(v.id) === String(inboundVariantId));
            if (!currentSelected) return null;
            const img = currentSelected.imageUrl ? resolveMediaUrl(currentSelected.imageUrl) : null;
            return (
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 14px', backgroundColor: '#F3F4F6', borderRadius: 6, marginBottom: 14 }}>
                {img && <img src={img} alt="" style={{ width: 44, height: 44, objectFit: 'cover', borderRadius: 4 }} />}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 700, fontSize: 13, color: '#111827' }}>{currentSelected.productName}</div>
                  <div style={{ fontSize: 11.5, color: '#4B5563', marginTop: 2 }}>
                    Phân loại: <strong>{currentSelected.variantName}</strong> · SKU: <code>{currentSelected.sku}</code> · Giá niêm yết: {formatCurrency(currentSelected.price)}
                  </div>
                </div>
              </div>
            );
          })()}

          <div className="grid-2">
            <Input label="Số lượng nhập hàng *" type="number" min={1} value={inboundQuantity} onChange={(e) => setInboundQuantity(e.target.value)} required />
            <Input label="Đơn giá vốn nhập (VNĐ) *" type="number" min={0} value={inboundCostPrice} onChange={(e) => setInboundCostPrice(e.target.value)} required />
          </div>

          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Mã lô sản xuất (Batch Code) *" value={inboundBatchCode} onChange={(e) => setInboundBatchCode(e.target.value)} required placeholder="VD: LOT-20261022-01" />
            <Input label="Hạn sử dụng (Expiration Date) *" type="date" value={inboundExpDate} onChange={(e) => setInboundExpDate(e.target.value)} required />
          </div>

          <div className="grid-3" style={{ marginTop: '12px' }}>
            <Input label="Vị trí lưu kho" value={inboundLocation} onChange={(e) => setInboundLocation(e.target.value)} placeholder="Kệ A1 - Tầng 2" />
            <Input label="Định mức tồn tối thiểu (Min)" type="number" min={0} value={inboundMinQty} onChange={(e) => setInboundMinQty(e.target.value)} />
            <Input label="Định mức tồn tối đa (Max)" type="number" min={0} value={inboundMaxQty} onChange={(e) => setInboundMaxQty(e.target.value)} />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setInboundModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={inboundSubmitting}>
              {inboundMethod === 'RECEIPT' ? 'Xác nhận nhập kho (+ Tồn)' : 'Thiết lập tồn kho'}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={inspectModalOpen}
        onClose={() => setInspectModalOpen(false)}
        title="Kiểm định & Xử lý hàng hoàn trả cách ly"
      >
        <form onSubmit={handleInspectSubmit}>
          <div
            style={{
              padding: '12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '13px',
              marginBottom: '16px',
              borderRadius: '6px',
            }}
          >
            <div>Lô hàng: <strong>{selectedStock?.sku || selectedStock?.productName || `Lô #${selectedStock?.id || selectedStock?.stockId}`}</strong></div>
            <div style={{ marginTop: '4px' }}>
              Số lượng đang cách ly: <strong style={{ color: 'var(--color-warning-700)' }}>{selectedStock?.quarantinedQuantity ?? 0}</strong> sản phẩm
            </div>
          </div>

          <Input
            label="Số lượng hàng hoàn cần xử lý *"
            type="number"
            min={1}
            max={selectedStock?.quarantinedQuantity || 1}
            value={inspectQuantity}
            onChange={(e) => setInspectQuantity(e.target.value)}
            required
          />

          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', marginTop: '14px' }}>
            <input
              type="checkbox"
              id="restockCheck"
              checked={inspectRestock}
              onChange={(e) => setInspectRestock(e.target.checked)}
              style={{ width: '18px', height: '18px', marginTop: '2px' }}
            />
            <label htmlFor="restockCheck" style={{ fontSize: '13px', fontWeight: 500, cursor: 'pointer' }}>
              Hàng đạt chuẩn chất lượng: Cho phép nhập lại kho bán tiếp (Restock)
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 400 }}>
                (Nếu bỏ chọn, số lượng này sẽ được ghi nhận xuất tiêu hủy / bỏ khỏi kho)
              </div>
            </label>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setInspectModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={submitting}>Xác nhận xử lý</Button>
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={adjustModalOpen}
        onClose={() => setAdjustModalOpen(false)}
        title="Kiểm kê & Điều chỉnh số lượng tồn kho"
        maxWidth="500px"
      >
        <form onSubmit={handleAdjustSubmit}>
          <div
            style={{
              padding: '12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '13px',
              marginBottom: '16px',
              borderRadius: '6px',
            }}
          >
            <div>Lô SKU: <strong>{selectedStockForAdjust?.sku || selectedStockForAdjust?.productName || `Lô #${selectedStockForAdjust?.id}`}</strong></div>
            <div style={{ marginTop: '4px' }}>
              Tồn bán được hiện tại: <strong>{selectedStockForAdjust?.quantity ?? 0}</strong> | Đang giữ chỗ cho đơn: <strong style={{ color: 'var(--color-danger-700)' }}>{selectedStockForAdjust?.reservedQuantity ?? 0}</strong>
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
              * Số lượng sau kiểm kê không được thấp hơn lượng hàng đang giữ chỗ cho các đơn hàng.
            </div>
          </div>

          <Input
            label="Số lượng tồn thực tế sau kiểm kê *"
            type="number"
            min={selectedStockForAdjust?.reservedQuantity ?? 0}
            value={adjustQuantityAfter}
            onChange={(e) => setAdjustQuantityAfter(e.target.value)}
            helperText="Nhập số nguyên không âm (VD: 50)"
            required
          />

          <div style={{ marginTop: '12px' }}>
            <Input
              label="Lý do điều chỉnh (bắt buộc) *"
              value={adjustReason}
              onChange={(e) => setAdjustReason(e.target.value)}
              placeholder="VD: Kiểm kê kho định kỳ, chênh lệch thực tế..."
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setAdjustModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={adjustSubmitting} title="Ghi nhận kiểm kê">Ghi nhận kiểm kê</Button>
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={transferModalOpen}
        onClose={() => setTransferModalOpen(false)}
        title="Điều chuyển tồn kho sang kho khác"
        maxWidth="500px"
      >
        <form onSubmit={handleTransferSubmit}>
          <div
            style={{
              padding: '12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '13px',
              marginBottom: '16px',
              borderRadius: '6px',
            }}
          >
            <div>Lô SKU: <strong>{selectedStockForTransfer?.sku || selectedStockForTransfer?.productName || `Lô #${selectedStockForTransfer?.id}`}</strong></div>
            <div style={{ marginTop: '4px' }}>
              Khả dụng để chuyển: <strong style={{ color: 'var(--color-success-700)' }}>{Math.max(0, (selectedStockForTransfer?.quantity ?? 0) - (selectedStockForTransfer?.reservedQuantity ?? 0))}</strong>
            </div>
          </div>

          <Select
            label="Chọn kho nhận hàng *"
            value={targetWhId}
            onChange={(e) => setTargetWhId(e.target.value)}
            options={warehouses
              .filter((w) => String(w.id) !== String(selectedWarehouseId))
              .map((w) => ({
                label: `${w.name} (${w.code || `#${w.id}`}) · ${WAREHOUSE_TYPE_MAP[w.warehouseType] || ''}`,
                value: String(w.id),
              }))}
            required
          />

          <div style={{ marginTop: '12px' }}>
            <Input
              label="Số lượng cần chuyển *"
              type="number"
              min={1}
              max={Math.max(1, (selectedStockForTransfer?.quantity ?? 0) - (selectedStockForTransfer?.reservedQuantity ?? 0))}
              value={transferQuantity}
              onChange={(e) => setTransferQuantity(e.target.value)}
              required
            />
          </div>

          <div style={{ marginTop: '12px' }}>
            <Input
              label="Lý do điều chuyển *"
              value={transferReason}
              onChange={(e) => setTransferReason(e.target.value)}
              placeholder="VD: Điều phối chi viện hàng cho chi nhánh..."
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setTransferModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={transferSubmitting}>Xác nhận điều chuyển</Button>
          </div>
        </form>
      </Modal>

      <Modal
        isOpen={detailModalOpen}
        onClose={() => setDetailModalOpen(false)}
        title={`Chi tiết giao dịch kho #${selectedTx?.id || ''}`}
        maxWidth="680px"
      >
        {selectedTx && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                gap: '12px',
                padding: '14px',
                backgroundColor: 'var(--bg-muted, #f8fafc)',
                borderRadius: '8px',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Loại biến động</div>
                <div style={{ marginTop: '4px' }}>
                  <Badge variant={TRANSACTION_TYPE_MAP[selectedTx.type || selectedTx.transactionType]?.variant || 'default'}>
                    {TRANSACTION_TYPE_MAP[selectedTx.type || selectedTx.transactionType]?.label || selectedTx.type || selectedTx.transactionType || '-'}
                  </Badge>
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Số lượng biến động</div>
                <div style={{ marginTop: '4px', fontSize: '16px', fontWeight: 700, color: (selectedTx.quantity ?? 0) > 0 ? 'var(--color-success-700)' : (selectedTx.quantity ?? 0) < 0 ? 'var(--color-danger-700)' : 'var(--text-main)' }}>
                  {(selectedTx.quantity ?? 0) > 0 ? `+${selectedTx.quantity}` : (selectedTx.quantity ?? 0)}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Tồn khả dụng (Trước → Sau)</div>
                <div style={{ marginTop: '4px', fontSize: '14px', fontWeight: 600 }}>
                  {selectedTx.quantityBefore != null && selectedTx.quantityAfter != null
                    ? `${selectedTx.quantityBefore} → ${selectedTx.quantityAfter}`
                    : (selectedTx.quantityAfter ?? '-')}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Thời điểm ghi nhận</div>
                <div style={{ marginTop: '4px', fontSize: '13px' }}>
                  {formatDateTime(selectedTx.occurredAt)}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Người thực hiện</div>
                <div style={{ marginTop: '4px', fontSize: '13px', fontWeight: 500 }}>
                  {selectedTx.createdBy || 'Hệ thống / Tự động'}
                </div>
              </div>
            </div>

            <div style={{ border: '1px solid var(--border-subtle)', borderRadius: '8px', padding: '14px' }}>
              <h4 style={{ margin: '0 0 10px 0', fontSize: '13px', textTransform: 'uppercase', color: 'var(--text-muted)' }}>Thông tin hàng hóa & Lô kho</h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', fontSize: '13px' }}>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Sản phẩm: </span>
                  <strong>{selectedTx.productName || `Sản phẩm #${selectedTx.productVariantId || selectedTx.variantId}`}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Biến thể: </span>
                  <span>{selectedTx.variantName || '-'}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Mã SKU: </span>
                  <code>{selectedTx.sku || '-'}</code>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Kho bãi: </span>
                  <strong>{selectedTx.warehouseName || `Kho #${selectedTx.warehouseId}`}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Mã lô SX: </span>
                  <span>{selectedTx.batchCode ? <Badge variant="info">{selectedTx.batchCode}</Badge> : 'Không có mã lô'}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Hạn sử dụng: </span>
                  <span>{selectedTx.expirationDate ? formatDate(selectedTx.expirationDate) : 'Không có hạn dùng'}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Vị trí lưu kho: </span>
                  <span>{selectedTx.location || 'Chưa định vị'}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Mã lô tồn (Stock ID): </span>
                  <span>#{selectedTx.stockId}</span>
                </div>
              </div>
            </div>

            <div style={{ border: '1px solid var(--border-subtle)', borderRadius: '8px', padding: '14px' }}>
              <h4 style={{ margin: '0 0 10px 0', fontSize: '13px', textTransform: 'uppercase', color: 'var(--text-muted)' }}>Tài chính & Chứng từ đối chiếu</h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', fontSize: '13px' }}>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Đơn giá vốn: </span>
                  <strong>{formatCurrency(selectedTx.unitCost)}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Tổng giá trị biến động: </span>
                  <strong style={{ color: 'var(--color-primary-600)' }}>{formatCurrency(Math.abs((selectedTx.quantity ?? 0) * (selectedTx.unitCost ?? 0)))}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Loại chứng từ: </span>
                  <span>{REFERENCE_TYPE_MAP[selectedTx.referenceType] || selectedTx.referenceType || 'Không có'}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Mã chứng từ: </span>
                  <strong>{selectedTx.referenceId || '-'}</strong>
                </div>
              </div>
              {selectedTx.note && (
                <div style={{ marginTop: '12px', padding: '8px 12px', backgroundColor: 'var(--bg-main, #fff)', border: '1px dashed var(--border-subtle)', borderRadius: '6px', fontSize: '12px' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Ghi chú: </span>
                  <span>{selectedTx.note}</span>
                </div>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '8px' }}>
              <Button variant="outline" onClick={() => setDetailModalOpen(false)}>Đóng</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
