import React, { useCallback, useEffect, useState } from 'react';
import {
  Banknote,
  Building2,
  ClipboardList,
  Download,
  Eye,
  Filter,
  History,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Search,
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  getPurchaseOrderStatus,
} from '../../shared/utils/formatters';
import { AlertBanner } from '../../shared/ui/AlertBanner';
import { Badge } from '../../shared/ui/Badge';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { Input } from '../../shared/ui/Input';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';
import { buildCsvContent } from '../commerce/orderReconciliationHelpers';
import {
  computePoPayable,
  formatLinePreview,
  getMovementKindMeta,
} from './procurementHistoryHelpers';

const unwrap = (response) =>
  response && typeof response === 'object' && 'data' in response ? response.data : response;

const downloadCsv = (filename, headers, rows) => {
  const csv = buildCsvContent(headers, rows);
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
};

export function ProcurementHistoryPanel({
  suppliers = [],
  warehouses = [],
  refreshRevision = 0,
  onOpenPoDetail,
}) {
  const [subTab, setSubTab] = useState('movements'); // 'movements' | 'orders'
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [kind, setKind] = useState('ALL'); // 'ALL' | 'RECEIPT' | 'SUPPLIER_RETURN' | 'PAYMENT' | 'SUPPLIER_REFUND'
  const [receivedStatus, setReceivedStatus] = useState('ALL'); // 'ALL' | 'RECEIVED' | 'PARTIALLY_RECEIVED'
  const [supplierId, setSupplierId] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);

  const [movements, setMovements] = useState([]);
  const [movPage, setMovPage] = useState(0);
  const [movTotalPages, setMovTotalPages] = useState(1);
  const [movTotalElements, setMovTotalElements] = useState(0);
  const [movLoading, setMovLoading] = useState(false);

  const [receivedOrders, setReceivedOrders] = useState([]);
  const [poPage, setPoPage] = useState(0);
  const [poTotalPages, setPoTotalPages] = useState(1);
  const [poTotalElements, setPoTotalElements] = useState(0);
  const [poLoading, setPoLoading] = useState(false);

  const [selectedMovement, setSelectedMovement] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');

  const warehouseName = useCallback(
    (wId) => warehouses.find((w) => String(w.id) === String(wId))?.name || (wId ? `Kho #${wId}` : '—'),
    [warehouses]
  );

  const appendSharedFilters = useCallback(
    (params) => {
      if (keyword.trim()) params.append('keyword', keyword.trim());
      if (supplierId) params.append('supplierId', supplierId);
      if (warehouseId) params.append('warehouseId', warehouseId);
      if (fromDate) params.append('from', new Date(`${fromDate}T00:00:00+07:00`).toISOString());
      if (toDate) params.append('to', new Date(`${toDate}T23:59:59.999+07:00`).toISOString());
    },
    [keyword, supplierId, warehouseId, fromDate, toDate]
  );

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true);
    try {
      const params = new URLSearchParams();
      appendSharedFilters(params);
      const res = await apiClient.get(`${ENDPOINTS.PROCUREMENT.HISTORY_SUMMARY}?${params.toString()}`);
      setSummary(unwrap(res));
    } catch (err) {
      setErrorMsg(err.message || 'Không tải được số liệu tổng hợp lịch sử nhập hàng.');
    } finally {
      setSummaryLoading(false);
    }
  }, [appendSharedFilters]);

  const loadMovements = useCallback(
    async (targetPage = 0) => {
      setMovLoading(true);
      setErrorMsg('');
      try {
        const params = new URLSearchParams();
        params.append('page', String(targetPage));
        params.append('size', '15');
        params.append('sort', 'createdAt,desc');
        params.append('sort', 'id,desc');
        if (kind && kind !== 'ALL') params.append('kind', kind);
        appendSharedFilters(params);
        const res = await apiClient.get(`${ENDPOINTS.PROCUREMENT.MOVEMENTS}?${params.toString()}`);
        const pageData = unwrap(res) || {};
        setMovements(pageData.content || []);
        setMovPage(pageData.page ?? targetPage);
        setMovTotalPages(pageData.totalPages ?? 1);
        setMovTotalElements(pageData.totalElements ?? 0);
      } catch (err) {
        setErrorMsg(err.message || 'Không tải được nhật ký chứng từ nhập hàng.');
      } finally {
        setMovLoading(false);
      }
    },
    [kind, appendSharedFilters]
  );

  const loadReceivedOrders = useCallback(
    async (targetPage = 0) => {
      setPoLoading(true);
      setErrorMsg('');
      try {
        const params = new URLSearchParams();
        params.append('page', String(targetPage));
        params.append('size', '15');
        params.append('sort', 'receivedAt,desc');
        params.append('sort', 'id,desc');
        params.append('useReceivedDate', 'true');
        if (receivedStatus === 'ALL') {
          params.append('statuses', 'PARTIALLY_RECEIVED');
          params.append('statuses', 'RECEIVED');
        } else {
          params.append('status', receivedStatus);
        }
        appendSharedFilters(params);
        const res = await apiClient.get(`${ENDPOINTS.PROCUREMENT.ORDERS}?${params.toString()}`);
        const pageData = unwrap(res) || {};
        setReceivedOrders(pageData.content || []);
        setPoPage(pageData.page ?? targetPage);
        setPoTotalPages(pageData.totalPages ?? 1);
        setPoTotalElements(pageData.totalElements ?? 0);
      } catch (err) {
        setErrorMsg(err.message || 'Không tải được danh sách phiếu mua đã nhập kho.');
      } finally {
        setPoLoading(false);
      }
    },
    [receivedStatus, appendSharedFilters]
  );

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (active) loadSummary();
    });
    return () => {
      active = false;
    };
  }, [loadSummary, refreshRevision]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return;
      if (subTab === 'movements') {
        loadMovements(0);
      } else {
        loadReceivedOrders(0);
      }
    });
    return () => {
      active = false;
    };
  }, [subTab, loadMovements, loadReceivedOrders, refreshRevision]);

  const handleApplyFilters = (e) => {
    e.preventDefault();
    setKeyword(keywordInput);
    if (keywordInput === keyword) {
      loadSummary();
      if (subTab === 'movements') loadMovements(0);
      else loadReceivedOrders(0);
    }
  };

  const handleResetFilters = () => {
    setKeywordInput('');
    setKeyword('');
    setKind('ALL');
    setReceivedStatus('ALL');
    setSupplierId('');
    setWarehouseId('');
    setFromDate('');
    setToDate('');
  };

  const handleRefresh = () => {
    loadSummary();
    if (subTab === 'movements') loadMovements(movPage);
    else loadReceivedOrders(poPage);
  };

  const handleExportCsv = () => {
    if (subTab === 'movements') {
      downloadCsv(
        `lich-su-chung-tu-nhap-hang-${new Date().toISOString().slice(0, 10)}.csv`,
        ['Thời điểm', 'Loại', 'Mã chứng từ', 'Mã PO', 'Nhà cung cấp', 'Kho nhận', 'SL hàng', 'Giá trị', 'Nội dung'],
        movements.map((row) => [
          formatDateTime(row.createdAt),
          getMovementKindMeta(row.kind).label,
          row.reference,
          row.orderNumber,
          row.supplierName || '',
          warehouseName(row.warehouseId),
          row.totalQuantity || 0,
          row.amount || 0,
          row.reason || '',
        ])
      );
      return;
    }

    downloadCsv(
      `lich-su-phieu-nhap-kho-${new Date().toISOString().slice(0, 10)}.csv`,
      ['Mã PO', 'Nhà cung cấp', 'Kho nhận', 'Trạng thái', 'Ngày nhập gần nhất', 'SL nhận / đặt', 'Giá trị đã nhận', 'Đã trả NCC', 'Công nợ còn lại'],
      receivedOrders.map((po) => [
        po.orderNumber,
        po.supplierName || '',
        warehouseName(po.warehouseId),
        getPurchaseOrderStatus(po.status).label,
        formatDateTime(po.receivedAt || po.createdAt),
        `${po.receivedQuantity ?? 0}/${po.totalQuantity ?? 0}`,
        po.receivedAmount ?? po.totalAmount ?? 0,
        po.paidAmount ?? 0,
        computePoPayable(po),
      ])
    );
  };

  const movementColumns = [
    {
      header: 'Thời điểm',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{formatDateTime(row.createdAt)}</div>
          {row.createdBy && (
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Bởi: {row.createdBy}</div>
          )}
        </div>
      ),
      width: '150px',
    },
    {
      header: 'Loại chứng từ',
      accessor: (row) => {
        const meta = getMovementKindMeta(row.kind);
        return <Badge variant={meta.variant}>{meta.label}</Badge>;
      },
      width: '130px',
    },
    {
      header: 'Mã chứng từ',
      accessor: (row) => (
        <code style={{ fontSize: '12px', fontWeight: 700, color: '#1E293B' }}>{row.reference}</code>
      ),
    },
    {
      header: 'Phiếu mua (PO)',
      accessor: (row) => {
        const st = row.orderStatus ? getPurchaseOrderStatus(row.orderStatus) : null;
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-start' }}>
            <button
              type="button"
              onClick={() => onOpenPoDetail?.(row.purchaseOrderId)}
              style={{
                background: 'none',
                border: 0,
                padding: 0,
                cursor: 'pointer',
                fontWeight: 700,
                color: 'var(--color-primary-600, #4F46E5)',
                textDecoration: 'underline',
              }}
            >
              {row.orderNumber || `PO #${row.purchaseOrderId}`}
            </button>
            {st && <Badge variant={st.variant}>{st.label}</Badge>}
          </div>
        );
      },
    },
    {
      header: 'Nhà cung cấp & Kho',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.supplierName || '—'}</div>
          <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{warehouseName(row.warehouseId)}</div>
        </div>
      ),
    },
    {
      header: 'Hàng hóa / SL',
      accessor: (row) => {
        const preview = formatLinePreview(row.lines);
        if (!preview) return <span style={{ color: 'var(--text-muted)' }}>Chứng từ tiền tệ</span>;
        return (
          <div>
            <div style={{ fontWeight: 600 }}>{preview}</div>
            <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>Tổng SL: {row.totalQuantity}</div>
          </div>
        );
      },
    },
    {
      header: 'Giá trị',
      align: 'right',
      accessor: (row) => <strong>{formatCurrency(row.amount)}</strong>,
    },
    {
      header: 'Nội dung',
      accessor: (row) => row.reason || '—',
    },
    {
      header: 'Thao tác',
      align: 'right',
      render: (row) => (
        <div className="table-actions" style={{ justifyContent: 'flex-end' }}>
          {Array.isArray(row.lines) && row.lines.length > 0 && (
            <Button
              size="sm"
              variant="outline"
              icon={PackageCheck}
              onClick={() => setSelectedMovement(row)}
            >
              Dòng hàng
            </Button>
          )}
          <Button
            size="sm"
            variant="ghost"
            icon={Eye}
            onClick={() => onOpenPoDetail?.(row.purchaseOrderId)}
            title="Xem chi tiết phiếu mua hàng"
          />
        </div>
      ),
    },
  ];

  const receivedOrderColumns = [
    {
      header: 'Mã PO',
      accessor: (row) => (
        <div>
          <strong>{row.orderNumber}</strong>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID: {row.id}</div>
        </div>
      ),
    },
    {
      header: 'Nhà cung cấp & Kho',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.supplierName}</div>
          <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{warehouseName(row.warehouseId)}</div>
        </div>
      ),
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const meta = getPurchaseOrderStatus(row.status);
        return <Badge variant={meta.variant}>{meta.label}</Badge>;
      },
    },
    {
      header: 'Ngày nhập gần nhất',
      accessor: (row) => formatDateTime(row.receivedAt || row.createdAt),
      width: '155px',
    },
    {
      header: 'SL nhận / đặt',
      align: 'right',
      accessor: (row) => (
        <span>
          <strong>{row.receivedQuantity ?? 0}</strong> / {row.totalQuantity ?? 0}
        </span>
      ),
    },
    {
      header: 'Giá trị đã nhận',
      align: 'right',
      accessor: (row) => (
        <div>
          <strong>{formatCurrency(row.receivedAmount ?? row.totalAmount)}</strong>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            Giá trị đặt: {formatCurrency(row.totalAmount)}
          </div>
        </div>
      ),
    },
    {
      header: 'Đã trả NCC',
      align: 'right',
      accessor: (row) => (
        <span style={{ color: '#059669', fontWeight: 600 }}>{formatCurrency(row.paidAmount || 0)}</span>
      ),
    },
    {
      header: 'Công nợ NCC',
      align: 'right',
      accessor: (row) => {
        const payable = computePoPayable(row);
        if (payable > 0) return <strong style={{ color: '#B45309' }}>{formatCurrency(payable)}</strong>;
        if (payable < 0) {
          return <strong style={{ color: '#1D4ED8' }}>Thu hồi {formatCurrency(Math.abs(payable))}</strong>;
        }
        return <span style={{ color: '#059669', fontWeight: 600 }}>Đã đủ</span>;
      },
    },
    {
      header: 'Chi tiết',
      align: 'right',
      render: (row) => (
        <Button size="sm" variant="outline" icon={Eye} onClick={() => onOpenPoDetail?.(row.id)}>
          Chi tiết
        </Button>
      ),
    },
  ];

  const movementLineColumns = [
    {
      header: 'Sản phẩm / Biến thể',
      accessor: (line) => (
        <div>
          <strong>{line.productName || (line.variantId ? `Biến thể #${line.variantId}` : `Dòng #${line.itemId}`)}</strong>
          {line.variantName && <div style={{ fontSize: '12px' }}>{line.variantName}</div>}
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            SKU: {line.sku || (line.variantId ? `#${line.variantId}` : '—')}
          </div>
        </div>
      ),
    },
    { header: 'Mã lô thực tế', accessor: (line) => line.batchCode || 'Không mã lô' },
    { header: 'Hạn sử dụng', accessor: (line) => formatDate(line.expirationDate) },
    { header: 'SL', align: 'right', accessor: 'quantity' },
    {
      header: 'Đơn giá vốn',
      align: 'right',
      accessor: (line) => (line.unitCost != null ? formatCurrency(line.unitCost) : '—'),
    },
    {
      header: 'Thành tiền',
      align: 'right',
      accessor: (line) => (line.lineAmount != null ? <strong>{formatCurrency(line.lineAmount)}</strong> : '—'),
    },
  ];

  const hasFilters = Boolean(
    keywordInput || keyword || kind !== 'ALL' || receivedStatus !== 'ALL' || supplierId || warehouseId || fromDate || toDate
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {errorMsg && <AlertBanner type="danger" message={errorMsg} />}

      {/* KPI Tổng hợp lịch sử nhập hàng */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '12px',
        }}
      >
        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => {
            setSubTab('movements');
            setKind('RECEIPT');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSubTab('movements');
              setKind('RECEIPT');
            }
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'movements' && kind === 'RECEIPT' ? '#10B981' : undefined,
            background: subTab === 'movements' && kind === 'RECEIPT' ? '#ECFDF5' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#065F46' }}>Giá trị đợt nhập kho</span>
            <PackageCheck size={18} color="#059669" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#059669', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.totalReceiptAmount || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {(summary?.receiptCount || 0).toLocaleString('vi-VN')} chứng từ nhận từng đợt
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => setSubTab('orders')}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') setSubTab('orders');
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'orders' ? '#4F46E5' : undefined,
            background: subTab === 'orders' ? '#EEF2FF' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#3730A3' }}>Phiếu mua đã nhập kho</span>
            <ClipboardList size={18} color="#4F46E5" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#4338CA', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.receivedOrdersValue || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {(summary?.receivedOrdersCount || 0).toLocaleString('vi-VN')} phiếu đã nhận hàng ròng
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => {
            setSubTab('movements');
            setKind('PAYMENT');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSubTab('movements');
              setKind('PAYMENT');
            }
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'movements' && kind === 'PAYMENT' ? '#3B82F6' : undefined,
            background: subTab === 'movements' && kind === 'PAYMENT' ? '#EFF6FF' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#1E40AF' }}>Thanh toán NCC (ròng)</span>
            <Banknote size={18} color="#2563EB" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#1D4ED8', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.netPaidAmount || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Chi: {formatCurrency(summary?.totalPaymentAmount || 0)} · Hoàn: {formatCurrency(summary?.totalRefundAmount || 0)}
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => {
            setSubTab('movements');
            setKind('SUPPLIER_RETURN');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSubTab('movements');
              setKind('SUPPLIER_RETURN');
            }
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'movements' && kind === 'SUPPLIER_RETURN' ? '#F59E0B' : undefined,
            background: subTab === 'movements' && kind === 'SUPPLIER_RETURN' ? '#FFFBEB' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#92400E' }}>Công nợ NCC còn lại</span>
            <RotateCcw size={18} color="#D97706" />
          </div>
          <div
            style={{
              fontSize: '20px',
              fontWeight: 800,
              color: Number(summary?.outstandingPayableAmount || 0) > 0 ? '#B45309' : '#059669',
              marginTop: '6px',
            }}
          >
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.outstandingPayableAmount || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {(summary?.supplierReturnCount || 0).toLocaleString('vi-VN')} đợt trả NCC ({formatCurrency(summary?.totalReturnAmount || 0)})
          </div>
        </div>
      </div>

      {/* Bộ lọc + Bảng lịch sử */}
      <div className="card" style={{ padding: '16px' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '10px',
            flexWrap: 'wrap',
            marginBottom: '14px',
            borderBottom: '1px solid var(--border-subtle)',
            paddingBottom: '12px',
          }}
        >
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Button
              size="sm"
              variant={subTab === 'movements' ? 'primary' : 'outline'}
              icon={History}
              onClick={() => setSubTab('movements')}
            >
              Chứng từ nhập & công nợ ({movTotalElements})
            </Button>
            <Button
              size="sm"
              variant={subTab === 'orders' ? 'primary' : 'outline'}
              icon={Building2}
              onClick={() => setSubTab('orders')}
            >
              Phiếu mua đã nhập kho ({poTotalElements})
            </Button>
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Button size="sm" variant="outline" icon={Download} onClick={handleExportCsv}>
              Xuất CSV
            </Button>
            <Button
              size="sm"
              variant="outline"
              icon={RefreshCw}
              loading={summaryLoading || movLoading || poLoading}
              onClick={handleRefresh}
            >
              Làm mới
            </Button>
          </div>
        </div>

        <form onSubmit={handleApplyFilters} className="admin-order-filters" style={{ marginBottom: '14px' }}>
          <Input
            label="Từ khóa tìm kiếm"
            placeholder={subTab === 'movements' ? 'Mã chứng từ, mã PO, tên NCC, lý do...' : 'Mã PO, tên nhà cung cấp, ghi chú...'}
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            icon={Search}
            className="form-group-compact"
          />

          {subTab === 'movements' ? (
            <Select
              label="Loại chứng từ"
              value={kind}
              onChange={(e) => setKind(e.target.value)}
              className="form-group-compact"
            >
              <option value="ALL">Tất cả chứng từ</option>
              <option value="RECEIPT">Nhập kho</option>
              <option value="SUPPLIER_RETURN">Trả hàng NCC</option>
              <option value="PAYMENT">Thanh toán NCC</option>
              <option value="SUPPLIER_REFUND">NCC hoàn tiền</option>
            </Select>
          ) : (
            <Select
              label="Trạng thái nhập"
              value={receivedStatus}
              onChange={(e) => setReceivedStatus(e.target.value)}
              className="form-group-compact"
            >
              <option value="ALL">Tất cả phiếu đã nhập</option>
              <option value="RECEIVED">Đã nhận đủ</option>
              <option value="PARTIALLY_RECEIVED">Nhập một phần</option>
            </Select>
          )}

          <Select
            label="Nhà cung cấp"
            value={supplierId}
            onChange={(e) => setSupplierId(e.target.value)}
            className="form-group-compact"
          >
            <option value="">Tất cả nhà cung cấp</option>
            {suppliers.map((s) => (
              <option key={s.id} value={s.id}>
                {s.code} - {s.name}
              </option>
            ))}
          </Select>

          <Select
            label="Kho nhận hàng"
            value={warehouseId}
            onChange={(e) => setWarehouseId(e.target.value)}
            className="form-group-compact"
          >
            <option value="">Tất cả kho</option>
            {warehouses.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </Select>

          <Input
            label="Từ ngày"
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="form-group-compact"
          />

          <Input
            label="Đến ngày"
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="form-group-compact"
          />

          <Button type="submit" variant="primary" icon={Filter}>
            Lọc
          </Button>

          {hasFilters && (
            <Button type="button" variant="outline" onClick={handleResetFilters}>
              Đặt lại
            </Button>
          )}
        </form>

        {subTab === 'movements' ? (
          <DataTable
            columns={movementColumns}
            data={movements}
            loading={movLoading}
            emptyMessage="Chưa có chứng từ nhập hàng hoặc thanh toán NCC nào khớp bộ lọc."
            page={movPage}
            totalPages={movTotalPages}
            totalElements={movTotalElements}
            onPageChange={(p) => loadMovements(p)}
          />
        ) : (
          <DataTable
            columns={receivedOrderColumns}
            data={receivedOrders}
            loading={poLoading}
            emptyMessage="Chưa có phiếu mua hàng nào đã nhập kho khớp bộ lọc."
            page={poPage}
            totalPages={poTotalPages}
            totalElements={poTotalElements}
            onPageChange={(p) => loadReceivedOrders(p)}
          />
        )}
      </div>

      {/* Modal chi tiết các dòng hàng thực nhập của một chứng từ */}
      <Modal
        isOpen={Boolean(selectedMovement)}
        onClose={() => setSelectedMovement(null)}
        title={`Chi tiết chứng từ ${selectedMovement?.reference || ''} (${selectedMovement?.orderNumber || ''})`}
        maxWidth="900px"
        footer={
          <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
            {selectedMovement?.purchaseOrderId ? (
              <Button
                variant="outline"
                size="sm"
                icon={Eye}
                onClick={() => {
                  const poId = selectedMovement.purchaseOrderId;
                  setSelectedMovement(null);
                  onOpenPoDetail?.(poId);
                }}
              >
                Mở phiếu mua hàng
              </Button>
            ) : <span />}
            <Button variant="primary" size="sm" onClick={() => setSelectedMovement(null)}>
              Đóng
            </Button>
          </div>
        }
      >
        {selectedMovement && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                gap: '10px',
                background: '#F8FAFC',
                padding: '12px',
                borderRadius: '8px',
                border: '1px solid var(--border-subtle)',
                fontSize: '13px',
              }}
            >
              <div>
                <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>Loại chứng từ</div>
                <strong>{getMovementKindMeta(selectedMovement.kind).label}</strong>
              </div>
              <div>
                <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>Nhà cung cấp</div>
                <strong>{selectedMovement.supplierName || '—'}</strong>
              </div>
              <div>
                <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>Thời điểm</div>
                <strong>{formatDateTime(selectedMovement.createdAt)}</strong>
              </div>
              <div>
                <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>Tổng giá trị</div>
                <strong style={{ color: '#059669' }}>{formatCurrency(selectedMovement.amount)}</strong>
              </div>
            </div>
            {selectedMovement.reason && (
              <p style={{ margin: 0, fontSize: '13px' }}>
                <strong>Nội dung:</strong> {selectedMovement.reason}
              </p>
            )}
            <DataTable
              columns={movementLineColumns}
              data={selectedMovement.lines || []}
              emptyMessage="Chứng từ không có chi tiết dòng hàng."
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
