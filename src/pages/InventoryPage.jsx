import React, { useState, useEffect } from 'react';
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
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';

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
  const [activeTab, setActiveTab] = useState('ledger');
  const [loading, setLoading] = useState(false);

  // Sổ cái biến động kho
  const [transactions, setTransactions] = useState([]);
  const [ledgerPage, setLedgerPage] = useState(0);
  const [ledgerTotalPages, setLedgerTotalPages] = useState(1);
  const [ledgerTotalElements, setLedgerTotalElements] = useState(0);

  // Cảnh báo tồn & cận hạn
  const [lowStockList, setLowStockList] = useState([]);
  const [expiringList, setExpiringList] = useState([]);

  // Kho bãi & Lô tồn
  const [warehouses, setWarehouses] = useState([]);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [warehouseStocks, setWarehouseStocks] = useState([]);

  // Modal xử lý hàng hoàn trả cách ly
  const [inspectModalOpen, setInspectModalOpen] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [inspectQuantity, setInspectQuantity] = useState(1);
  const [inspectRestock, setInspectRestock] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Modal tạo kho mới
  const [createWhModalOpen, setCreateWhModalOpen] = useState(false);
  const [whName, setWhName] = useState('');
  const [whCode, setWhCode] = useState('');
  const [whAddress, setWhAddress] = useState('');
  const [whCity, setWhCity] = useState('TP. Hồ Chí Minh');
  const [whPhone, setWhPhone] = useState('');
  const [whManager, setWhManager] = useState('');
  const [whType, setWhType] = useState('CENTRAL');
  const [whSubmitting, setWhSubmitting] = useState(false);

  // Modal nhập kho hàng hóa
  const [inboundModalOpen, setInboundModalOpen] = useState(false);
  const [inboundVariantId, setInboundVariantId] = useState('');
  const [inboundQuantity, setInboundQuantity] = useState('50');
  const [inboundCostPrice, setInboundCostPrice] = useState('150000');
  const [inboundBatchCode, setInboundBatchCode] = useState('LO-2026-10');
  const [inboundExpDate, setInboundExpDate] = useState('2027-12-31');
  const [inboundMinQty, setInboundMinQty] = useState('10');
  const [inboundMaxQty, setInboundMaxQty] = useState('500');
  const [inboundSubmitting, setInboundSubmitting] = useState(false);

  // Modal điều chỉnh số lượng sau kiểm kê
  const [adjustModalOpen, setAdjustModalOpen] = useState(false);
  const [selectedStockForAdjust, setSelectedStockForAdjust] = useState(null);
  const [adjustQuantityAfter, setAdjustQuantityAfter] = useState('0');
  const [adjustReason, setAdjustReason] = useState('Kiểm kê kho định kỳ');
  const [adjustSubmitting, setAdjustSubmitting] = useState(false);

  // Modal điều chuyển kho
  const [transferModalOpen, setTransferModalOpen] = useState(false);
  const [selectedStockForTransfer, setSelectedStockForTransfer] = useState(null);
  const [targetWhId, setTargetWhId] = useState('');
  const [transferQuantity, setTransferQuantity] = useState('1');
  const [transferReason, setTransferReason] = useState('Điều phối luân chuyển hàng giữa các kho');
  const [transferSubmitting, setTransferSubmitting] = useState(false);

  // Tải dữ liệu Sổ cái
  const fetchLedger = async (p = 0) => {
    setLoading(true);
    try {
      const res = await apiClient.get(`${ENDPOINTS.INVENTORY.TRANSACTIONS}?page=${p}&size=15`);
      const pageData = res.data || res;
      setTransactions(pageData.content || []);
      setLedgerPage(pageData.page ?? p);
      setLedgerTotalPages(pageData.totalPages ?? 1);
      setLedgerTotalElements(pageData.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  };

  // Tải dữ liệu Cảnh báo
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

  // Tải danh sách kho
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

  // Tải tồn kho theo kho bãi
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
    if (activeTab === 'ledger') fetchLedger(0);
    else if (activeTab === 'alerts') fetchAlerts();
    else if (activeTab === 'warehouses') fetchWarehouses();
  }, [activeTab]);

  useEffect(() => {
    if (activeTab === 'warehouses' && selectedWarehouseId) {
      fetchWarehouseStocks(selectedWarehouseId);
    }
  }, [selectedWarehouseId, activeTab]);

  const handleOpenInspect = (stock) => {
    setSelectedStock(stock);
    const maxQty = stock.quarantinedQuantity || 1;
    setInspectQuantity(Math.min(1, maxQty));
    setInspectRestock(true);
    setInspectModalOpen(true);
  };

  const handleInspectSubmit = async (e) => {
    e.preventDefault();
    if (!selectedStock) return;
    setSubmitting(true);
    try {
      await apiClient.post(ENDPOINTS.INVENTORY.INSPECTION(selectedStock.id || selectedStock.stockId), {
        quantity: parseInt(inspectQuantity, 10),
        restock: inspectRestock,
      });
      setInspectModalOpen(false);
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

  const handleInboundStock = async (e) => {
    e.preventDefault();
    if (!selectedWarehouseId) {
      alert('Vui lòng chọn một kho bãi trước khi nhập hàng.');
      return;
    }
    setInboundSubmitting(true);
    try {
      const payload = {
        productVariantId: parseInt(inboundVariantId, 10),
        quantity: parseInt(inboundQuantity, 10),
        reservedQuantity: 0,
        costPrice: parseFloat(inboundCostPrice),
        batchCode: inboundBatchCode.trim() || undefined,
        expirationDate: inboundExpDate || undefined,
        minQuantity: parseInt(inboundMinQty, 10) || 0,
        maxQuantity: inboundMaxQty ? parseInt(inboundMaxQty, 10) : undefined,
      };
      await apiClient.post(ENDPOINTS.INVENTORY.WAREHOUSE_RECEIPTS(selectedWarehouseId), payload);
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
    if (!selectedStockForAdjust) return;
    setAdjustSubmitting(true);
    try {
      await apiClient.put(ENDPOINTS.INVENTORY.STOCK_ADJUSTMENT(selectedStockForAdjust.id), {
        quantityAfter: parseInt(adjustQuantityAfter, 10),
        reason: adjustReason.trim(),
      });
      setAdjustModalOpen(false);
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
      });
      setTransferModalOpen(false);
      fetchWarehouseStocks(selectedWarehouseId);
    } catch (err) {
      alert(err.message || 'Lỗi khi điều chuyển kho');
    } finally {
      setTransferSubmitting(false);
    }
  };

  // Cột bảng: Sổ cái biến động kho
  const ledgerColumns = [
    {
      header: 'Thời điểm',
      accessor: (row) => formatDateTime(row.occurredAt),
      width: '140px',
    },
    {
      header: 'Biến thể SKU / Sản phẩm',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.productName || `Biến thể #${row.variantId}`}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Mã SKU: {row.sku || '-'}</div>
        </div>
      ),
    },
    {
      header: 'Kho bãi',
      accessor: (row) => row.warehouseName || `Kho #${row.warehouseId}`,
    },
    {
      header: 'Loại biến động',
      accessor: (row) => {
        const type = row.transactionType;
        const meta = TRANSACTION_TYPE_MAP[type] || { label: type, variant: 'default' };
        return <Badge variant={meta.variant}>{meta.label}</Badge>;
      },
      width: '150px',
    },
    {
      header: 'Biến động',
      accessor: (row) => (
        <span
          style={{
            fontWeight: 700,
            fontVariantNumeric: 'tabular-nums',
            color: row.quantityChange > 0 ? 'var(--color-success-700)' : 'var(--color-danger-700)',
          }}
        >
          {row.quantityChange > 0 ? `+${row.quantityChange}` : row.quantityChange}
        </span>
      ),
      align: 'right',
      width: '100px',
    },
    {
      header: 'Đơn giá vốn',
      accessor: (row) => formatCurrency(row.unitCost),
      align: 'right',
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
  ];

  // Cột bảng: Cảnh báo tồn kho & Cận hạn
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
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.sku ? `SKU: ${row.sku}` : `Biến thể #${row.productVariantId}`}</div>
          {row.batchCode && (
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Mã lô: {row.batchCode}</div>
          )}
        </div>
      ),
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
          title="Kiểm đếm & điều chỉnh số lượng tồn"
        >
          Kiểm kê
        </Button>
      ),
      align: 'right',
      width: '110px',
    },
  ];

  // Cột bảng: Tồn kho biến thể theo kho bãi
  const warehouseStockColumns = [
    {
      header: 'Mã lô',
      accessor: 'id',
      width: '70px',
    },
    {
      header: 'Biến thể SKU',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.sku || `Biến thể #${row.productVariantId}`}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID biến thể: {row.productVariantId}</div>
        </div>
      ),
    },
    {
      header: 'Mã lô sản xuất',
      accessor: (row) => row.batchCode || 'Mặc định (Không mã)',
    },
    {
      header: 'Tồn bán được',
      accessor: (row) => <strong>{formatNumber(row.quantity)}</strong>,
      align: 'right',
    },
    {
      header: 'Đang giữ cho đơn',
      accessor: (row) => formatNumber(row.reservedQuantity || 0),
      align: 'right',
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
    },
    {
      header: 'Đơn giá vốn',
      accessor: (row) => formatCurrency(row.costPrice),
      align: 'right',
    },
    {
      header: 'Hạn sử dụng',
      accessor: (row) => formatDate(row.expirationDate) || 'Không có',
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
              <Button variant="primary" size="sm" onClick={() => setInboundModalOpen(true)} icon={PackagePlus}>
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

      {/* Danh sách Tab */}
      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'ledger' ? 'active' : ''}`}
          onClick={() => setActiveTab('ledger')}
        >
          <Boxes size={15} />
          Sổ cái biến động kho ({ledgerTotalElements})
        </button>
        <button
          className={`tab-btn ${activeTab === 'alerts' ? 'active' : ''}`}
          onClick={() => setActiveTab('alerts')}
        >
          <AlertTriangle size={15} />
          Cảnh báo tồn kho & Cận hạn ({lowStockList.length + expiringList.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'warehouses' ? 'active' : ''}`}
          onClick={() => setActiveTab('warehouses')}
        >
          <Building2 size={15} />
          Kho bãi & Quản lý lô tồn ({warehouses.length})
        </button>
      </div>

      {/* Tab 1: Sổ cái biến động */}
      {activeTab === 'ledger' && (
        <div className="card">
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

      {/* Tab 2: Cảnh báo */}
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

      {/* Tab 3: Kho bãi & Lô tồn */}
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

      {/* MODAL 1: TẠO KHO BÃI MỚI */}
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

      {/* MODAL 2: NHẬP KHO HÀNG HÓA */}
      <Modal
        isOpen={inboundModalOpen}
        onClose={() => setInboundModalOpen(false)}
        title={`Nhập Hàng Vào Kho: ${warehouses.find((w) => String(w.id) === String(selectedWarehouseId))?.name || 'Kho đã chọn'}`}
        maxWidth="540px"
      >
        <form onSubmit={handleInboundStock}>
          <Input
            label="ID Biến thể Sản phẩm (Variant ID) *"
            type="number"
            value={inboundVariantId}
            onChange={(e) => setInboundVariantId(e.target.value)}
            required
            placeholder="VD: 1, 2, 27..."
          />
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Số lượng nhập *" type="number" min={1} value={inboundQuantity} onChange={(e) => setInboundQuantity(e.target.value)} required />
            <Input label="Đơn giá vốn (VNĐ) *" type="number" value={inboundCostPrice} onChange={(e) => setInboundCostPrice(e.target.value)} required />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Mã lô sản xuất (Batch Code)" value={inboundBatchCode} onChange={(e) => setInboundBatchCode(e.target.value)} placeholder="LO-2026-10" />
            <Input label="Hạn sử dụng (Expiration Date)" type="date" value={inboundExpDate} onChange={(e) => setInboundExpDate(e.target.value)} />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Định mức tồn tối thiểu (Min)" type="number" value={inboundMinQty} onChange={(e) => setInboundMinQty(e.target.value)} />
            <Input label="Định mức tồn tối đa (Max)" type="number" value={inboundMaxQty} onChange={(e) => setInboundMaxQty(e.target.value)} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setInboundModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={inboundSubmitting}>Xác nhận nhập kho</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 3: XỬ LÝ HÀNG HOÀN TRẢ CÁCH LY */}
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

      {/* MODAL 4: ĐIỀU CHỈNH SỐ LƯỢNG SAU KIỂM KÊ */}
      <Modal
        isOpen={adjustModalOpen}
        onClose={() => setAdjustModalOpen(false)}
        title="Điều chỉnh số lượng sau kiểm kê"
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
            <Button variant="primary" type="submit" loading={adjustSubmitting}>Lưu kết quả kiểm kê</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 5: ĐIỀU CHUYỂN KHO */}
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
    </div>
  );
};
