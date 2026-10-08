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

export const InventoryPage = () => {
  const [activeTab, setActiveTab] = useState('ledger');
  const [loading, setLoading] = useState(false);

  // Ledger state
  const [transactions, setTransactions] = useState([]);
  const [ledgerPage, setLedgerPage] = useState(0);
  const [ledgerTotalPages, setLedgerTotalPages] = useState(1);
  const [ledgerTotalElements, setLedgerTotalElements] = useState(0);

  // Alerts
  const [lowStockList, setLowStockList] = useState([]);
  const [expiringList, setExpiringList] = useState([]);

  // Warehouses
  const [warehouses, setWarehouses] = useState([]);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [warehouseStocks, setWarehouseStocks] = useState([]);

  // Inspection modal
  const [inspectModalOpen, setInspectModalOpen] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [inspectQuantity, setInspectQuantity] = useState(1);
  const [inspectRestock, setInspectRestock] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Create Warehouse modal
  const [createWhModalOpen, setCreateWhModalOpen] = useState(false);
  const [whName, setWhName] = useState('');
  const [whCode, setWhCode] = useState('');
  const [whAddress, setWhAddress] = useState('');
  const [whCity, setWhCity] = useState('TP. Hồ Chí Minh');
  const [whPhone, setWhPhone] = useState('');
  const [whManager, setWhManager] = useState('');
  const [whType, setWhType] = useState('CENTRAL');
  const [whSubmitting, setWhSubmitting] = useState(false);

  // Inbound Stock Receipt modal
  const [inboundModalOpen, setInboundModalOpen] = useState(false);
  const [inboundVariantId, setInboundVariantId] = useState('');
  const [inboundQuantity, setInboundQuantity] = useState('50');
  const [inboundCostPrice, setInboundCostPrice] = useState('150000');
  const [inboundBatchCode, setInboundBatchCode] = useState('BATCH-2026-09');
  const [inboundExpDate, setInboundExpDate] = useState('2027-12-31');
  const [inboundMinQty, setInboundMinQty] = useState('10');
  const [inboundMaxQty, setInboundMaxQty] = useState('500');
  const [inboundSubmitting, setInboundSubmitting] = useState(false);

  // Adjust stock modal
  const [adjustModalOpen, setAdjustModalOpen] = useState(false);
  const [selectedStockForAdjust, setSelectedStockForAdjust] = useState(null);
  const [adjustQuantityAfter, setAdjustQuantityAfter] = useState('0');
  const [adjustReason, setAdjustReason] = useState('Kiểm kê định kỳ');
  const [adjustSubmitting, setAdjustSubmitting] = useState(false);

  // Transfer stock modal
  const [transferModalOpen, setTransferModalOpen] = useState(false);
  const [selectedStockForTransfer, setSelectedStockForTransfer] = useState(null);
  const [targetWhId, setTargetWhId] = useState('');
  const [transferQuantity, setTransferQuantity] = useState('1');
  const [transferReason, setTransferReason] = useState('Điều phối luân chuyển kho');
  const [transferSubmitting, setTransferSubmitting] = useState(false);

  // Load Transactions
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

  // Load Alerts
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

  // Load Warehouses
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

  // Load Warehouse Stocks
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
    setInspectQuantity(1);
    setInspectRestock(false);
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
      alert(err.message || 'Lỗi kiểm kê tồn kho');
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
    if (!window.confirm(`Bạn có chắc chắn muốn xóa kho bãi #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.INVENTORY.WAREHOUSE_DETAIL(id));
      setSelectedWarehouseId(null);
      fetchWarehouses();
    } catch (err) {
      alert(err.message || 'Lỗi xóa kho bãi');
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
    if (!window.confirm(`Xóa bản ghi tồn kho #${stockId}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.INVENTORY.STOCK_DELETE(stockId));
      fetchWarehouseStocks(selectedWarehouseId);
    } catch (err) {
      alert(err.message || 'Lỗi xóa bản ghi tồn kho');
    }
  };

  const handleOpenAdjustModal = (stock) => {
    setSelectedStockForAdjust(stock);
    setAdjustQuantityAfter(String(stock.quantity ?? 0));
    setAdjustReason('Kiểm kê định kỳ');
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
      fetchWarehouseStocks(selectedWarehouseId);
    } catch (err) {
      alert(err.message || 'Lỗi điều chỉnh tồn kho');
    } finally {
      setAdjustSubmitting(false);
    }
  };

  const handleOpenTransferModal = (stock) => {
    setSelectedStockForTransfer(stock);
    const targets = warehouses.filter((warehouse) => String(warehouse.id) !== String(selectedWarehouseId));
    setTargetWhId(targets[0]?.id ? String(targets[0].id) : '');
    setTransferQuantity('1');
    setTransferReason('Điều phối tồn kho');
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
      alert(err.message || 'Lỗi chuyển kho');
    } finally {
      setTransferSubmitting(false);
    }
  };

  // Ledger Table Columns
  const ledgerColumns = [
    {
      header: 'Thời điểm',
      accessor: (row) => formatDateTime(row.occurredAt),
      width: '140px',
    },
    {
      header: 'Biến thể SKU / Tên',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.productName || `Variant #${row.variantId}`}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>SKU: {row.sku || '-'}</div>
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
        let variant = 'default';
        if (type === 'INBOUND' || type === 'RETURN') variant = 'success';
        else if (type === 'OUTBOUND') variant = 'info';
        else if (type === 'EXPIRED') variant = 'danger';
        else if (type === 'ADJUSTMENT') variant = 'warning';
        return <Badge variant={variant}>{type}</Badge>;
      },
      width: '120px',
    },
    {
      header: 'Số lượng thay đổi',
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
      width: '130px',
    },
    {
      header: 'Đơn giá vốn',
      accessor: (row) => formatCurrency(row.unitCost),
      align: 'right',
    },
    {
      header: 'Tham chiếu / Ghi chú',
      accessor: (row) => (
        <div style={{ fontSize: '12px' }}>
          <div>{row.referenceType ? `${row.referenceType}: ${row.referenceId}` : '-'}</div>
          {row.note && <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{row.note}</div>}
        </div>
      ),
    },
  ];

  // Alert Columns
  const alertColumns = [
    {
      header: 'Mã tồn / Stock ID',
      accessor: 'stockId',
      width: '90px',
    },
    {
      header: 'Kho',
      accessor: 'warehouseName',
    },
    {
      header: 'Sản phẩm & SKU',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.productName}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            Biến thể: {row.variantName} (SKU: {row.sku})
          </div>
        </div>
      ),
    },
    {
      header: 'Tồn thực',
      accessor: 'quantity',
      align: 'right',
    },
    {
      header: 'Khả dụng',
      accessor: 'availableQuantity',
      align: 'right',
    },
    {
      header: 'Tối thiểu',
      accessor: 'minQuantity',
      align: 'right',
    },
    {
      header: 'Hạn dùng (FEFO)',
      accessor: (row) => (
        <span style={{ color: row.daysUntilExpiration <= 7 ? 'var(--color-danger-700)' : 'inherit', fontWeight: 600 }}>
          {formatDate(row.expirationDate)}
          {row.daysUntilExpiration != null && (
            <span style={{ fontSize: '11px', display: 'block' }}>
              (Còn {row.daysUntilExpiration} ngày)
            </span>
          )}
        </span>
      ),
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <Button variant="outline" size="sm" onClick={() => handleOpenInspect(row)} icon={ClipboardCheck}>
          Kiểm định
        </Button>
      ),
      align: 'right',
      width: '110px',
    },
  ];

  // Warehouse Stocks Table Columns
  const warehouseStockColumns = [
    {
      header: 'ID',
      accessor: 'id',
      width: '60px',
    },
    {
      header: 'Biến thể SKU',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.productName || `Variant #${row.productVariantId}`}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>SKU: {row.sku || row.variantName}</div>
        </div>
      ),
    },
    {
      header: 'Lô hàng',
      accessor: (row) => row.batchCode || row.batchNumber || 'Mặc định',
    },
    {
      header: 'Số lượng tồn',
      accessor: (row) => <strong>{formatNumber(row.quantity)}</strong>,
      align: 'right',
    },
    {
      header: 'Tạm giữ',
      accessor: (row) => formatNumber(row.reservedQuantity || 0),
      align: 'right',
    },
    {
      header: 'Giá vốn',
      accessor: (row) => formatCurrency(row.costPrice),
      align: 'right',
    },
    {
      header: 'Hạn sử dụng',
      accessor: (row) => formatDate(row.expirationDate),
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenInspect(row)} icon={ClipboardCheck} title="Kiểm định lô" />
          <Button variant="outline" size="sm" onClick={() => handleOpenAdjustModal(row)} icon={ArrowUpRight} title="Điều chỉnh sau kiểm kê" />
          <Button variant="outline" size="sm" onClick={() => handleOpenTransferModal(row)} icon={ArrowDownLeft} title="Chuyển sang kho khác" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteStock(row.id)} icon={Trash2} title="Xóa tồn kho" />
        </div>
      ),
      align: 'right',
      width: '130px',
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản trị Kho bãi, Tồn kho & Sổ cái FEFO</h1>
          <p className="page-subtitle">
            Giám sát mức an toàn tồn kho, hạn sử dụng mỹ phẩm, điều phối kho bãi và nhập kho hàng hóa
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

      {/* Tabs */}
      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'ledger' ? 'active' : ''}`}
          onClick={() => setActiveTab('ledger')}
        >
          <Boxes size={15} />
          Sổ cái luân chuyển ({ledgerTotalElements})
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
          Kho bãi & Nhập tồn kho thực tế ({warehouses.length})
        </button>
      </div>

      {/* Tab Content: Ledger */}
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

      {/* Tab Content: Alerts */}
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

      {/* Tab Content: Warehouses */}
      {activeTab === 'warehouses' && (
        <div>
          <div className="admin-warehouse-toolbar">
            <div className="admin-warehouse-picker">
              <label htmlFor="admin-warehouse-select" className="form-label">Chọn kho vận hành</label>
              <Select
                id="admin-warehouse-select"
                value={selectedWarehouseId || ''}
                onChange={(e) => setSelectedWarehouseId(e.target.value)}
                options={warehouses.map((w) => ({ label: `${w.name} (${w.code})`, value: String(w.id) }))}
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
              <span className="card-title">Danh sách tồn kho biến thể trong kho</span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Tổng cộng {warehouseStocks.length} SKU
              </span>
            </div>
            <DataTable
              columns={warehouseStockColumns}
              data={warehouseStocks}
              loading={loading}
              emptyMessage="Kho bãi này chưa có dữ liệu tồn kho biến thể nào. Nhấn 'Nhập kho hàng hóa' để nhập."
            />
          </div>
        </div>
      )}

      {/* MODAL 1: CREATE NEW WAREHOUSE */}
      <Modal
        isOpen={createWhModalOpen}
        onClose={() => setCreateWhModalOpen(false)}
        title="Thêm Kho Bãi Mới"
        maxWidth="520px"
      >
        <form onSubmit={handleCreateWarehouse}>
          <div className="grid-2">
            <Input label="Tên kho *" value={whName} onChange={(e) => setWhName(e.target.value)} required placeholder="Kho Trung Tâm Tân Bình" />
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
            <Input label="Tên thủ kho / Quản lý" value={whManager} onChange={(e) => setWhManager(e.target.value)} placeholder="Nguyễn Văn A" />
            <Select
              label="Loại kho"
              value={whType}
              onChange={(e) => setWhType(e.target.value)}
              options={[
                { label: 'Kho Tổng (Central)', value: 'CENTRAL' },
                { label: 'Kho Cửa Hàng / Spa (Branch)', value: 'BRANCH' },
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

      {/* MODAL 2: INBOUND STOCK RECEIPT */}
      <Modal
        isOpen={inboundModalOpen}
        onClose={() => setInboundModalOpen(false)}
        title={`Nhập Tồn Kho vào: ${warehouses.find((w) => String(w.id) === String(selectedWarehouseId))?.name || 'Kho đã chọn'}`}
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
            <Input label="Mã lô sản xuất (Batch Code)" value={inboundBatchCode} onChange={(e) => setInboundBatchCode(e.target.value)} />
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

      {/* MODAL 3: INSPECTION */}
      <Modal
        isOpen={inspectModalOpen}
        onClose={() => setInspectModalOpen(false)}
        title="Ghi nhận kiểm định tồn kho thực tế"
      >
        <form onSubmit={handleInspectSubmit}>
          <div
            style={{
              padding: '10px 12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '12px',
              marginBottom: '16px',
            }}
          >
            Sản phẩm / SKU kiểm định: <strong>{selectedStock?.productName || selectedStock?.variantName || selectedStock?.sku}</strong>
          </div>

          <Input
            label="Số lượng mẫu kiểm định / hư hại"
            type="number"
            min={1}
            value={inspectQuantity}
            onChange={(e) => setInspectQuantity(e.target.value)}
            required
          />

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px' }}>
            <input
              type="checkbox"
              id="restockCheck"
              checked={inspectRestock}
              onChange={(e) => setInspectRestock(e.target.checked)}
              style={{ width: '16px', height: '16px' }}
            />
            <label htmlFor="restockCheck" style={{ fontSize: '13px', fontWeight: 500, cursor: 'pointer' }}>
              Đã kiểm tra chất lượng đạt chuẩn và nhập lại kho (Restock)
            </label>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setInspectModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={submitting}>Ghi nhận kiểm kê</Button>
          </div>
        </form>
      </Modal>

      {/* Adjust Stock Modal */}
      <Modal
        isOpen={adjustModalOpen}
        onClose={() => setAdjustModalOpen(false)}
        title="Điều chỉnh số tồn sau kiểm đếm"
        maxWidth="500px"
      >
        <form onSubmit={handleAdjustSubmit}>
          <div
            style={{
              padding: '10px 12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '12px',
              marginBottom: '16px',
            }}
          >
            <div>Lô SKU: <strong>{selectedStockForAdjust?.productName || selectedStockForAdjust?.sku}</strong></div>
            <div>Số lượng hiện tại: <strong>{selectedStockForAdjust?.quantity ?? 0}</strong> (Tạm giữ: <strong>{selectedStockForAdjust?.reservedQuantity ?? 0}</strong>)</div>
          </div>

          <Input
            label="Số lượng tồn thực tế sau kiểm kê (≥ tạm giữ)"
            type="number"
            min={selectedStockForAdjust?.reservedQuantity ?? 0}
            value={adjustQuantityAfter}
            onChange={(e) => setAdjustQuantityAfter(e.target.value)}
            required
          />

          <div style={{ marginTop: '12px' }}>
            <Input
              label="Lý do điều chỉnh (bắt buộc)"
              value={adjustReason}
              onChange={(e) => setAdjustReason(e.target.value)}
              placeholder="VD: Kiểm kê định kỳ, sai lệch kiểm đếm..."
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setAdjustModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={adjustSubmitting}>Xác nhận điều chỉnh</Button>
          </div>
        </form>
      </Modal>

      {/* Transfer Stock Modal */}
      <Modal
        isOpen={transferModalOpen}
        onClose={() => setTransferModalOpen(false)}
        title="Điều chuyển tồn kho sang kho khác"
        maxWidth="500px"
      >
        <form onSubmit={handleTransferSubmit}>
          <div
            style={{
              padding: '10px 12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '12px',
              marginBottom: '16px',
            }}
          >
            <div>Lô SKU: <strong>{selectedStockForTransfer?.productName || selectedStockForTransfer?.sku}</strong></div>
            <div>
              Khả dụng để chuyển: <strong>{Math.max(0, (selectedStockForTransfer?.quantity ?? 0) - (selectedStockForTransfer?.reservedQuantity ?? 0))}</strong>
            </div>
          </div>

          <Select
            label="Chọn kho đích nhận hàng"
            value={targetWhId}
            onChange={(e) => setTargetWhId(e.target.value)}
            options={warehouses
              .filter((w) => String(w.id) !== String(selectedWarehouseId))
              .map((w) => ({ label: `${w.name} (${w.warehouseCode || `#${w.id}`})`, value: String(w.id) }))}
            required
          />

          <div style={{ marginTop: '12px' }}>
            <Input
              label="Số lượng cần chuyển"
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
              label="Lý do điều chuyển"
              value={transferReason}
              onChange={(e) => setTransferReason(e.target.value)}
              placeholder="VD: Điều phối tồn kho, chi viện cơ sở..."
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
