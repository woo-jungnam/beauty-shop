import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Banknote,
  Camera,
  CheckCircle2,
  Download,
  ExternalLink,
  Eye,
  FileCheck2,
  Filter,
  History,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Search,
  Truck,
  UserCheck,
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
  getOrderStatusBadge,
  getPaymentStatusBadge,
  getReturnMethodLabel,
  getReturnStatusBadge,
  resolveMediaUrl,
} from '../../shared/utils/formatters';
import { AlertBanner } from '../../shared/ui/AlertBanner';
import { Badge } from '../../shared/ui/Badge';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { Input } from '../../shared/ui/Input';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';
import {
  buildCsvContent,
  computeOrderCollectedCod,
  computeOrderRemittedCod,
  computeOrderUnsettledCod,
  createBatchSettlementRef,
  getCodReconciliationBadge,
  resolveChannelDrilldown,
} from './orderReconciliationHelpers';

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

export function OrderReconciliationPanel({
  isAdmin,
  refreshRevision = 0,
  onOpenOrderDetail,
  onOpenProof,
  onOpenCommerce,
  onOpenAttentionTab,
  onSettled,
}) {
  const [subTab, setSubTab] = useState('orders'); // 'orders' | 'settlements' | 'returns'
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [codState, setCodState] = useState('PENDING'); // 'PENDING' | 'SETTLED' | 'IN_TRANSIT' | 'ALL'
  const [returnStatusFilter, setReturnStatusFilter] = useState('ALL');
  const [channelFilter, setChannelFilter] = useState('ALL'); // 'ALL' | 'CARRIER' | 'SHIPPER:<id>'
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);

  const [orders, setOrders] = useState([]);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersTotalPages, setOrdersTotalPages] = useState(1);
  const [ordersTotalElements, setOrdersTotalElements] = useState(0);
  const [ordersLoading, setOrdersLoading] = useState(false);

  const [settlements, setSettlements] = useState([]);
  const [settlementPage, setSettlementPage] = useState(0);
  const [settlementTotalPages, setSettlementTotalPages] = useState(1);
  const [settlementTotalElements, setSettlementTotalElements] = useState(0);
  const [settlementLoading, setSettlementLoading] = useState(false);

  const [returns, setReturns] = useState([]);
  const [returnsPage, setReturnsPage] = useState(0);
  const [returnsTotalPages, setReturnsTotalPages] = useState(1);
  const [returnsTotalElements, setReturnsTotalElements] = useState(0);
  const [returnsLoading, setReturnsLoading] = useState(false);

  const [selectedOrderMap, setSelectedOrderMap] = useState({});
  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [batchRef, setBatchRef] = useState('');
  const [batchReason, setBatchReason] = useState('Đối soát bàn giao tiền COD theo bảng kê');
  const [batchBusy, setBatchBusy] = useState(false);
  const [feedback, setFeedback] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
  const listSectionRef = useRef(null);

  const appendDateParams = useCallback(
    (params) => {
      if (fromDate) params.append('from', new Date(`${fromDate}T00:00:00+07:00`).toISOString());
      if (toDate) params.append('to', new Date(`${toDate}T23:59:59.999+07:00`).toISOString());
    },
    [fromDate, toDate]
  );

  const appendChannelParams = useCallback(
    (params) => {
      if (channelFilter === 'CARRIER') {
        params.append('carrierOnly', 'true');
      } else if (channelFilter.startsWith('SHIPPER:')) {
        params.append('shipperId', channelFilter.slice('SHIPPER:'.length));
      }
    },
    [channelFilter]
  );

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true);
    try {
      const params = new URLSearchParams();
      if (keyword.trim()) params.append('keyword', keyword.trim());
      appendDateParams(params);
      const res = await apiClient.get(`${ENDPOINTS.ORDERS.RECONCILIATION_SUMMARY}?${params.toString()}`);
      setSummary(unwrap(res));
    } catch (err) {
      setErrorMsg(err.message || 'Không tải được số liệu tổng hợp đối soát COD.');
    } finally {
      setSummaryLoading(false);
    }
  }, [keyword, appendDateParams]);

  const loadOrders = useCallback(
    async (targetPage = 0) => {
      setOrdersLoading(true);
      setErrorMsg('');
      try {
        const params = new URLSearchParams();
        params.append('page', String(targetPage));
        params.append('size', '15');
        params.append('codReconciliation', codState);
        if (keyword.trim()) params.append('keyword', keyword.trim());
        appendDateParams(params);
        appendChannelParams(params);

        const res = await apiClient.get(`${ENDPOINTS.ORDERS.LIST}?${params.toString()}`);
        const pageData = unwrap(res) || {};
        setOrders(pageData.content || []);
        setOrdersPage(pageData.page ?? targetPage);
        setOrdersTotalPages(pageData.totalPages ?? 1);
        setOrdersTotalElements(pageData.totalElements ?? 0);
      } catch (err) {
        setErrorMsg(err.message || 'Không tải được danh sách đơn hàng đối soát.');
      } finally {
        setOrdersLoading(false);
      }
    },
    [codState, keyword, appendDateParams, appendChannelParams]
  );

  const loadSettlements = useCallback(
    async (targetPage = 0) => {
      setSettlementLoading(true);
      setErrorMsg('');
      try {
        const params = new URLSearchParams();
        params.append('page', String(targetPage));
        params.append('size', '15');
        if (keyword.trim()) params.append('keyword', keyword.trim());
        appendDateParams(params);

        const res = await apiClient.get(`${ENDPOINTS.ORDERS.RECONCILIATION_SETTLEMENTS}?${params.toString()}`);
        const pageData = unwrap(res) || {};
        setSettlements(pageData.content || []);
        setSettlementPage(pageData.page ?? targetPage);
        setSettlementTotalPages(pageData.totalPages ?? 1);
        setSettlementTotalElements(pageData.totalElements ?? 0);
      } catch (err) {
        setErrorMsg(err.message || 'Không tải được lịch sử chứng từ đối soát COD.');
      } finally {
        setSettlementLoading(false);
      }
    },
    [keyword, appendDateParams]
  );

  const loadReturns = useCallback(
    async (targetPage = 0) => {
      setReturnsLoading(true);
      setErrorMsg('');
      try {
        const params = new URLSearchParams();
        params.append('page', String(targetPage));
        params.append('size', '15');
        params.append('sort', 'id,desc');
        params.append('returnMethod', 'SHIPPER_PICKUP');
        if (returnStatusFilter && returnStatusFilter !== 'ALL') {
          params.append('status', returnStatusFilter);
        }
        if (channelFilter.startsWith('SHIPPER:')) {
          params.append('pickupShipperId', channelFilter.slice('SHIPPER:'.length));
        }
        const res = await apiClient.get(`${ENDPOINTS.ORDERS.COMMERCE_RETURN_LIST}?${params.toString()}`);
        const pageData = unwrap(res) || {};
        setReturns(pageData.content || []);
        setReturnsPage(pageData.page ?? targetPage);
        setReturnsTotalPages(pageData.totalPages ?? 1);
        setReturnsTotalElements(pageData.totalElements ?? 0);
      } catch (err) {
        setErrorMsg(err.message || 'Không tải được danh sách đối soát thu hồi hàng hoàn.');
      } finally {
        setReturnsLoading(false);
      }
    },
    [returnStatusFilter, channelFilter]
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
      if (subTab === 'orders') {
        loadOrders(0);
      } else if (subTab === 'settlements') {
        loadSettlements(0);
      } else {
        loadReturns(0);
      }
    });
    return () => {
      active = false;
    };
  }, [subTab, loadOrders, loadSettlements, loadReturns, refreshRevision]);

  const handleRefreshAll = () => {
    setFeedback(null);
    loadSummary();
    if (subTab === 'orders') {
      loadOrders(ordersPage);
    } else if (subTab === 'settlements') {
      loadSettlements(settlementPage);
    } else {
      loadReturns(returnsPage);
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setFeedback(null);
    setKeyword(keywordInput);
    if (keywordInput === keyword) {
      loadSummary();
      if (subTab === 'orders') {
        loadOrders(0);
      } else if (subTab === 'settlements') {
        loadSettlements(0);
      } else {
        loadReturns(0);
      }
    }
  };

  const handleResetFilters = () => {
    setKeywordInput('');
    setKeyword('');
    setCodState('PENDING');
    setReturnStatusFilter('ALL');
    setChannelFilter('ALL');
    setFromDate('');
    setToDate('');
    setSelectedOrderMap({});
    setFeedback(null);
  };

  const pendingRowsOnPage = useMemo(
    () => orders.filter((row) => computeOrderUnsettledCod(row) > 0),
    [orders]
  );

  const allPendingSelected =
    pendingRowsOnPage.length > 0 && pendingRowsOnPage.every((row) => Boolean(selectedOrderMap[row.id]));

  const toggleSelectOrder = (row) => {
    const due = computeOrderUnsettledCod(row);
    if (due <= 0) return;
    setSelectedOrderMap((prev) => {
      const next = { ...prev };
      if (next[row.id]) {
        delete next[row.id];
      } else {
        next[row.id] = row;
      }
      return next;
    });
  };

  const toggleSelectAllPending = () => {
    if (pendingRowsOnPage.length === 0) return;
    setSelectedOrderMap((prev) => {
      const next = { ...prev };
      if (allPendingSelected) {
        for (const row of pendingRowsOnPage) delete next[row.id];
      } else {
        for (const row of pendingRowsOnPage) next[row.id] = row;
      }
      return next;
    });
  };

  const selectedOrdersList = useMemo(
    () => Object.values(selectedOrderMap).filter((row) => computeOrderUnsettledCod(row) > 0),
    [selectedOrderMap]
  );

  const selectedTotalAmount = useMemo(
    () => selectedOrdersList.reduce((sum, row) => sum + computeOrderUnsettledCod(row), 0),
    [selectedOrdersList]
  );

  const openBatchModal = () => {
    if (!selectedOrdersList.length) return;
    setBatchRef(createBatchSettlementRef());
    setBatchReason('Đối soát bàn giao tiền COD theo bảng kê');
    setErrorMsg('');
    setBatchModalOpen(true);
  };

  const handleBatchSubmit = async (e) => {
    e.preventDefault();
    if (batchBusy || !selectedOrdersList.length) return;
    setBatchBusy(true);
    setErrorMsg('');
    try {
      const res = await apiClient.post(ENDPOINTS.ORDERS.COMMERCE_COD_SETTLEMENT_BATCH, {
        orderIds: selectedOrdersList.map((o) => o.id),
        reference: batchRef.trim(),
        reason: batchReason.trim(),
      });
      const result = unwrap(res);
      setBatchModalOpen(false);
      setSelectedOrderMap({});
      setFeedback({
        type: 'success',
        message: `Đã đối soát thành công ${result?.settledCount ?? selectedOrdersList.length} đơn hàng · Tổng ${formatCurrency(
          result?.totalSettledAmount ?? selectedTotalAmount
        )} (Mã phiếu: ${result?.batchReference || batchRef.trim()}).`,
      });
      await Promise.all([loadSummary(), loadOrders(ordersPage)]);
      onSettled?.();
    } catch (err) {
      setErrorMsg(err.message || 'Không thể hoàn tất đối soát COD hàng loạt.');
    } finally {
      setBatchBusy(false);
    }
  };

  const handleSelectChannelFromSummary = (channel) => {
    const next = resolveChannelDrilldown(channel);
    setSubTab(next.subTab);
    setCodState(next.codState);
    setChannelFilter(next.channelFilter);
    setSelectedOrderMap({});
    setFeedback(null);
    requestAnimationFrame(() => {
      listSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  };

  const handleExportCurrentCsv = () => {
    if (subTab === 'returns') {
      downloadCsv(
        `doi-soat-thu-hoi-hoan-${new Date().toISOString().slice(0, 10)}.csv`,
        ['Mã YC', 'Mã đơn', 'Khách hàng', 'Tài xế thu hồi', 'Trạng thái', 'Giá trị hoàn', 'Ngày lấy hàng', 'Báo bàn giao kho'],
        returns.map((row) => [
          `#${row.id}`,
          row.orderNumber || `#${row.orderId}`,
          `${row.customerName || ''} (${row.customerPhone || ''})`.trim(),
          row.pickupShipperName || 'Chưa phân công',
          getReturnStatusBadge(row.status).text,
          row.refundAmount || 0,
          row.pickedUpAt ? formatDateTime(row.pickedUpAt) : '',
          row.handoverRequestedAt ? formatDateTime(row.handoverRequestedAt) : '',
        ])
      );
      return;
    }
    if (subTab === 'orders') {
      downloadCsv(
        `doi-soat-cod-${new Date().toISOString().slice(0, 10)}.csv`,
        [
          'Mã đơn',
          'Ngày tạo',
          'Khách hàng',
          'Tài xế / Hãng VC',
          'Mã vận đơn',
          'Trạng thái đơn',
          'Đã thu COD',
          'Đã bàn giao',
          'Còn chờ đối soát',
        ],
        orders.map((row) => [
          row.orderNumber || `#${row.id}`,
          formatDateTime(row.createdAt),
          `${row.customerName || ''} (${row.customerPhone || ''})`.trim(),
          row.shipperName || row.carrierName || 'Chưa phân công',
          row.trackingCode || '',
          getOrderStatusBadge(row.status).text,
          computeOrderCollectedCod(row),
          computeOrderRemittedCod(row),
          computeOrderUnsettledCod(row),
        ])
      );
      return;
    }

    downloadCsv(
      `lich-su-chung-tu-cod-${new Date().toISOString().slice(0, 10)}.csv`,
      ['Thời điểm', 'Mã chứng từ', 'Mã đơn hàng', 'Tài xế / Hãng VC', 'Số tiền bàn giao', 'Nội dung'],
      settlements.map((row) => [
        formatDateTime(row.createdAt),
        row.referenceCode,
        row.orderNumber,
        row.shipperName || row.carrierName || '',
        row.amount,
        row.content || '',
      ])
    );
  };

  const shipperOptions = useMemo(() => {
    const channels = summary?.byShipper || [];
    return channels.filter((item) => !item.carrierChannel && item.shipperId != null);
  }, [summary?.byShipper]);

  const selectedChannelSummary = useMemo(() => {
    const channels = summary?.byShipper || [];
    if (channelFilter === 'ALL') return null;
    if (channelFilter === 'CARRIER') {
      const carrierChannels = channels.filter((item) => item.carrierChannel);
      if (!carrierChannels.length) return null;
      return carrierChannels.reduce(
        (acc, item) => ({
          label: carrierChannels.length === 1 ? item.label : 'Tất cả đơn vị vận chuyển / Tự giao',
          pendingOrdersCount: acc.pendingOrdersCount + Number(item.pendingOrdersCount || 0),
          inTransitOrdersCount: acc.inTransitOrdersCount + Number(item.inTransitOrdersCount || 0),
          settledOrdersCount: acc.settledOrdersCount + Number(item.settledOrdersCount || 0),
          collectedAmount: acc.collectedAmount + Number(item.collectedAmount || 0),
          remittedAmount: acc.remittedAmount + Number(item.remittedAmount || 0),
          pendingAmount: acc.pendingAmount + Number(item.pendingAmount || 0),
          pendingPickupReturnsCount: acc.pendingPickupReturnsCount + Number(item.pendingPickupReturnsCount || 0),
          inCustodyReturnsCount: acc.inCustodyReturnsCount + Number(item.inCustodyReturnsCount || 0),
        }),
        {
          label: 'Đơn vị vận chuyển / Tự giao',
          pendingOrdersCount: 0,
          inTransitOrdersCount: 0,
          settledOrdersCount: 0,
          collectedAmount: 0,
          remittedAmount: 0,
          pendingAmount: 0,
          pendingPickupReturnsCount: 0,
          inCustodyReturnsCount: 0,
        }
      );
    }
    if (channelFilter.startsWith('SHIPPER:')) {
      const targetId = channelFilter.slice('SHIPPER:'.length);
      return channels.find((item) => !item.carrierChannel && String(item.shipperId) === targetId) || null;
    }
    return null;
  }, [summary?.byShipper, channelFilter]);

  const orderColumns = [
    {
      header: (
        <input
          type="checkbox"
          aria-label="Chọn tất cả đơn chờ đối soát trên trang"
          checked={allPendingSelected}
          disabled={pendingRowsOnPage.length === 0}
          onChange={toggleSelectAllPending}
        />
      ),
      width: '38px',
      render: (row) => {
        const due = computeOrderUnsettledCod(row);
        return (
          <input
            type="checkbox"
            aria-label={`Chọn đơn ${row.orderNumber || row.id}`}
            checked={Boolean(selectedOrderMap[row.id])}
            disabled={due <= 0}
            onChange={() => toggleSelectOrder(row)}
          />
        );
      },
    },
    {
      header: 'Đơn hàng',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>{row.orderNumber || `#${row.id}`}</div>
          <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
            {row.customerName || 'Khách'} · {row.customerPhone || '—'}
          </div>
          <div style={{ fontSize: '11px', color: '#64748B' }}>
            {row.deliveredAt ? `Giao: ${formatDateTime(row.deliveredAt)}` : formatDateTime(row.createdAt)}
          </div>
        </div>
      ),
    },
    {
      header: 'Tài xế / Vận chuyển',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>
            {row.shipperName || row.carrierName || <span style={{ color: '#94A3B8' }}>Chưa phân công</span>}
          </div>
          {row.shipperPhone && (
            <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{row.shipperPhone}</div>
          )}
          {row.trackingCode && (
            <div style={{ fontSize: '11px', color: '#475569' }}>VĐ: {row.trackingCode}</div>
          )}
        </div>
      ),
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const st = getOrderStatusBadge(row.status);
        const rec = getCodReconciliationBadge(row);
        const pay = getPaymentStatusBadge(row.paymentStatus);
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-start' }}>
            <Badge variant={st.variant}>{st.text}</Badge>
            <Badge variant={rec.variant}>{rec.text}</Badge>
            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>TT: {pay.text}</span>
          </div>
        );
      },
    },
    {
      header: 'Đã thu COD',
      align: 'right',
      accessor: (row) => (
        <div>
          <strong>{formatCurrency(computeOrderCollectedCod(row))}</strong>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            Tổng đơn: {formatCurrency(row.totalAmount)}
          </div>
        </div>
      ),
    },
    {
      header: 'Đã bàn giao',
      align: 'right',
      accessor: (row) => (
        <span style={{ fontWeight: 600, color: '#059669' }}>
          {formatCurrency(computeOrderRemittedCod(row))}
        </span>
      ),
    },
    {
      header: 'Còn chờ nộp',
      align: 'right',
      accessor: (row) => {
        const due = computeOrderUnsettledCod(row);
        return due > 0 ? (
          <strong style={{ color: '#B45309' }}>{formatCurrency(due)}</strong>
        ) : (
          <span style={{ fontSize: '12px', color: '#059669', fontWeight: 600 }}>0 ₫</span>
        );
      },
    },
    {
      header: 'Thao tác',
      align: 'right',
      render: (row) => {
        const due = computeOrderUnsettledCod(row);
        return (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px', flexWrap: 'wrap' }}>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onOpenOrderDetail?.(row)}
              title="Xem chi tiết đơn"
            >
              <Eye size={14} />
            </Button>
            {row.deliveryProofUrl && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onOpenProof?.(row)}
                title="Xem ảnh bằng chứng giao hàng (PoD)"
                style={{ color: '#059669', borderColor: '#059669' }}
              >
                <Camera size={14} />
              </Button>
            )}
            <Button
              variant={due > 0 ? 'primary' : 'outline'}
              size="sm"
              icon={Banknote}
              onClick={() => onOpenCommerce?.(row)}
              title="Mở đối soát & bàn giao COD cho đơn này"
            >
              {due > 0 ? 'Đối soát' : 'Số dư'}
            </Button>
          </div>
        );
      },
    },
  ];

  const settlementColumns = [
    {
      header: 'Thời điểm ghi nhận',
      accessor: (row) => formatDateTime(row.createdAt),
      width: '155px',
    },
    {
      header: 'Mã chứng từ',
      accessor: (row) => (
        <code style={{ fontSize: '12px', fontWeight: 700, color: '#1E293B' }}>{row.referenceCode}</code>
      ),
    },
    {
      header: 'Đơn hàng',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>{row.orderNumber}</div>
          {row.customerName && (
            <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{row.customerName}</div>
          )}
        </div>
      ),
    },
    {
      header: 'Tài xế / Vận chuyển',
      accessor: (row) =>
        row.shipperName || row.carrierName || <span style={{ color: '#94A3B8' }}>Chưa rõ</span>,
    },
    {
      header: 'Số tiền bàn giao',
      align: 'right',
      accessor: (row) => (
        <strong style={{ color: '#059669' }}>{formatCurrency(row.amount)}</strong>
      ),
    },
    {
      header: 'Nội dung xác nhận',
      accessor: (row) => row.content || '—',
    },
    {
      header: 'Chi tiết',
      align: 'right',
      render: (row) =>
        row.orderId ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onOpenOrderDetail?.({ id: row.orderId, orderNumber: row.orderNumber })}
            title="Xem chi tiết đơn hàng"
          >
            <Eye size={14} />
          </Button>
        ) : (
          '—'
        ),
    },
  ];

  const filteredReturns = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    if (!q) return returns;
    return returns.filter((row) =>
      String(row.id || '').includes(q) ||
      (row.orderNumber || '').toLowerCase().includes(q) ||
      (row.customerName || '').toLowerCase().includes(q) ||
      (row.customerPhone || '').toLowerCase().includes(q) ||
      (row.pickupShipperName || '').toLowerCase().includes(q)
    );
  }, [returns, keyword]);

  const returnColumns = [
    {
      header: 'Phiếu / Đơn',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>YC #{row.id} · {row.orderNumber || `Đơn #${row.orderId}`}</div>
          <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
            {getReturnMethodLabel(row.returnMethod)}
          </div>
          {row.createdAt && (
            <div style={{ fontSize: '11px', color: '#64748B' }}>Tạo: {formatDateTime(row.createdAt)}</div>
          )}
        </div>
      ),
    },
    {
      header: 'Khách hàng',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.customerName || 'Khách hàng'}</div>
          <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{row.customerPhone || '—'}</div>
          {row.pickupAddress && (
            <div style={{ fontSize: '11px', color: '#64748B', maxWidth: '220px' }}>{row.pickupAddress}</div>
          )}
        </div>
      ),
    },
    {
      header: 'Tài xế thu hồi',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>
            {row.pickupShipperName || <span style={{ color: '#94A3B8' }}>Chưa phân công</span>}
          </div>
          {row.pickupShipperPhone && (
            <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>{row.pickupShipperPhone}</div>
          )}
          {row.pickedUpAt && (
            <div style={{ fontSize: '11px', color: '#059669' }}>Lấy: {formatDateTime(row.pickedUpAt)}</div>
          )}
        </div>
      ),
    },
    {
      header: 'Trạng thái & Đối soát',
      accessor: (row) => {
        const badge = getReturnStatusBadge(row.status);
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-start' }}>
            <Badge variant={badge.variant}>{badge.text}</Badge>
            {row.handoverRequestedAt && row.status === 'PICKED_UP' && (
              <Badge variant="warning">Đã báo bàn giao kho</Badge>
            )}
            {row.pickupFailureReason && (
              <span style={{ fontSize: '11px', color: '#B91C1C' }}>
                Lỗi lấy (lần {row.pickupAttemptCount}): {row.pickupFailureReason}
              </span>
            )}
          </div>
        );
      },
    },
    {
      header: 'Ảnh lấy hàng',
      accessor: (row) =>
        row.pickupProofUrl ? (
          <a href={resolveMediaUrl(row.pickupProofUrl)} target="_blank" rel="noreferrer" title="Mở ảnh lấy hàng hoàn">
            <img
              src={resolveMediaUrl(row.pickupProofUrl)}
              alt={`Ảnh thu hồi #${row.id}`}
              style={{ width: '44px', height: '44px', objectFit: 'cover', borderRadius: '6px', border: '1px solid #CBD5E1' }}
            />
          </a>
        ) : (
          <span style={{ fontSize: '11.5px', color: '#94A3B8' }}>Chưa có</span>
        ),
    },
    {
      header: 'Giá trị hoàn',
      align: 'right',
      accessor: (row) => <strong style={{ color: '#B45309' }}>{formatCurrency(row.refundAmount)}</strong>,
    },
    {
      header: 'Thao tác',
      align: 'right',
      render: (row) => (
        <Button
          size="sm"
          variant={row.status === 'PICKED_UP' || row.status === 'APPROVED' ? 'primary' : 'outline'}
          icon={PackageCheck}
          onClick={() => onOpenCommerce?.({ id: row.orderId, orderNumber: row.orderNumber })}
        >
          {row.status === 'PICKED_UP'
            ? 'Kiểm nhận kho'
            : row.status === 'APPROVED'
            ? 'Điều phối'
            : 'Xử lý'}
        </Button>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {feedback && <AlertBanner type={feedback.type} message={feedback.message} />}
      {errorMsg && !batchModalOpen && <AlertBanner type="danger" message={errorMsg} />}

      {/* KPI Tổng hợp đối soát COD */}
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
            setSubTab('orders');
            setCodState('PENDING');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSubTab('orders');
              setCodState('PENDING');
            }
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'orders' && codState === 'PENDING' ? '#F59E0B' : undefined,
            background: subTab === 'orders' && codState === 'PENDING' ? '#FFFBEB' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#92400E' }}>COD chờ đối soát / bàn giao</span>
            <Banknote size={18} color="#D97706" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#B45309', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.pendingCodAmount || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {(summary?.pendingCodOrders || 0).toLocaleString('vi-VN')} đơn · Shipper:{' '}
            {formatCurrency(summary?.shipperPendingCodAmount || 0)}
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => {
            setSubTab('orders');
            setCodState('SETTLED');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSubTab('orders');
              setCodState('SETTLED');
            }
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'orders' && codState === 'SETTLED' ? '#10B981' : undefined,
            background: subTab === 'orders' && codState === 'SETTLED' ? '#ECFDF5' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#065F46' }}>COD đã đối soát / đã nộp</span>
            <CheckCircle2 size={18} color="#059669" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#059669', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.settledCodAmount || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {(summary?.settledCodOrders || 0).toLocaleString('vi-VN')} đơn đã hoàn tất bàn giao
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => {
            setSubTab('orders');
            setCodState('IN_TRANSIT');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              setSubTab('orders');
              setCodState('IN_TRANSIT');
            }
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'orders' && codState === 'IN_TRANSIT' ? '#3B82F6' : undefined,
            background: subTab === 'orders' && codState === 'IN_TRANSIT' ? '#EFF6FF' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#1E40AF' }}>Đơn COD đang vận chuyển</span>
            <Truck size={18} color="#2563EB" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#1D4ED8', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : formatCurrency(summary?.inTransitCodAmount || 0)}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            {(summary?.inTransitCodOrders || 0).toLocaleString('vi-VN')} đơn đang giao / chờ hoàn kho
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => setSubTab('returns')}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') setSubTab('returns');
          }}
          style={{
            padding: '14px 16px',
            cursor: 'pointer',
            borderColor: subTab === 'returns' ? '#7C3AED' : undefined,
            background: subTab === 'returns' ? '#F5F3FF' : '#FFFFFF',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#5B21B6' }}>Hàng hoàn Shipper đang giữ</span>
            <PackageCheck size={18} color="#7C3AED" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#6D28D9', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : `${(summary?.inCustodyReturnsCount || 0).toLocaleString('vi-VN')} kiện`}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
            Giá trị: {formatCurrency(summary?.inCustodyReturnValue || 0)} · Chờ lấy:{' '}
            {(summary?.pendingPickupReturnsCount || 0).toLocaleString('vi-VN')}
          </div>
        </div>

        <div
          className="card"
          role="button"
          tabIndex={0}
          onClick={() => onOpenAttentionTab?.()}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') onOpenAttentionTab?.();
          }}
          style={{ padding: '14px 16px', cursor: 'pointer', background: '#FFFFFF' }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: '#9A3412' }}>Hoàn hàng / Hoàn tiền cần xử lý</span>
            <AlertTriangle size={18} color="#EA580C" />
          </div>
          <div style={{ fontSize: '20px', fontWeight: 800, color: '#C2410C', marginTop: '6px' }}>
            {summaryLoading && !summary ? '…' : `${(summary?.attentionOrdersCount || 0).toLocaleString('vi-VN')} đơn`}
          </div>
          <div style={{ fontSize: '12px', color: '#C2410C', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <RotateCcw size={12} />
            <span>Mở danh sách cần hoàn trả</span>
          </div>
        </div>
      </div>

      {/* Bảng công nợ theo từng tài xế / hãng vận chuyển */}
      {(summary?.byShipper?.length || 0) > 0 && (
        <div className="card" style={{ padding: '16px' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '10px',
              flexWrap: 'wrap',
              marginBottom: '12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <UserCheck size={16} color="var(--color-primary-600, #4F46E5)" />
              <strong style={{ fontSize: '14px' }}>Công nợ thu hộ COD theo Tài xế / Vận chuyển</strong>
            </div>
            {channelFilter !== 'ALL' && (
              <Button size="sm" variant="outline" onClick={() => setChannelFilter('ALL')}>
                Xem tất cả tài xế
              </Button>
            )}
          </div>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Tài xế / Kênh vận chuyển</th>
                  <th style={{ textAlign: 'right' }}>Đơn chờ nộp</th>
                  <th style={{ textAlign: 'right' }}>Đang giao COD</th>
                  <th style={{ textAlign: 'right' }}>Đã thu COD</th>
                  <th style={{ textAlign: 'right' }}>Đã bàn giao</th>
                  <th style={{ textAlign: 'right' }}>Còn phải nộp</th>
                  <th style={{ textAlign: 'right' }}>Thu hồi chờ lấy</th>
                  <th style={{ textAlign: 'right' }}>Hàng hoàn giữ trả kho</th>
                  <th style={{ textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {summary.byShipper.map((ch) => {
                  const isActive =
                    (ch.carrierChannel && channelFilter === 'CARRIER') ||
                    (!ch.carrierChannel && channelFilter === `SHIPPER:${ch.shipperId}`);
                  return (
                    <tr
                      key={ch.channelKey}
                      style={isActive ? { background: '#FFFBEB' } : undefined}
                    >
                      <td>
                        <div style={{ fontWeight: 700 }}>{ch.label}</div>
                        <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                          {ch.carrierChannel ? 'Đơn vị vận chuyển / Tự giao' : ch.phone || `ID #${ch.shipperId}`}
                        </div>
                      </td>
                      <td style={{ textAlign: 'right' }}>{ch.pendingOrdersCount}</td>
                      <td style={{ textAlign: 'right' }}>
                        <div>{ch.inTransitOrdersCount}</div>
                        {Number(ch.inTransitOrdersCount || 0) > 0 && (
                          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                            {formatCurrency(ch.inTransitAmount || 0)}
                          </div>
                        )}
                      </td>
                      <td style={{ textAlign: 'right', fontWeight: 600 }}>{formatCurrency(ch.collectedAmount)}</td>
                      <td style={{ textAlign: 'right', fontWeight: 600, color: '#111827' }}>
                        <div>{formatCurrency(ch.remittedAmount)}</div>
                        <div style={{ fontSize: '11px', fontWeight: 500, color: 'var(--text-muted)' }}>
                          {Number(ch.settledOrdersCount || 0)} đơn
                        </div>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        {Number(ch.pendingAmount) > 0 ? (
                          <strong style={{ color: '#111827' }}>{formatCurrency(ch.pendingAmount)}</strong>
                        ) : (
                          <span style={{ color: '#111827', fontWeight: 600 }}>0 ₫</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'right' }}>{ch.pendingPickupReturnsCount ?? 0}</td>
                      <td style={{ textAlign: 'right' }}>
                        {Number(ch.inCustodyReturnsCount || 0) > 0 ? (
                          <div>
                            <strong style={{ color: '#111827' }}>{ch.inCustodyReturnsCount} kiện</strong>
                            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                              {formatCurrency(ch.inCustodyReturnAmount || 0)}
                            </div>
                          </div>
                        ) : (
                          <span style={{ color: '#111827', fontWeight: 600 }}>0</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <Button
                          size="sm"
                          variant={isActive ? 'primary' : 'outline'}
                          onClick={() => handleSelectChannelFromSummary(ch)}
                        >
                          {Number(ch.pendingAmount) > 0 ||
                          Number(ch.pendingOrdersCount || 0) > 0 ||
                          Number(ch.inCustodyReturnsCount || 0) > 0 ||
                          Number(ch.pendingPickupReturnsCount || 0) > 0
                            ? 'Đối soát kênh này'
                            : 'Xem đơn'}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Bộ lọc & Chuyển đổi tab Danh sách đối soát / Lịch sử phiếu */}
      <div className="card" ref={listSectionRef} style={{ padding: '16px' }}>
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
              variant={subTab === 'orders' ? 'primary' : 'outline'}
              icon={FileCheck2}
              onClick={() => setSubTab('orders')}
            >
              Đơn hàng đối soát COD ({ordersTotalElements})
            </Button>
            <Button
              size="sm"
              variant={subTab === 'returns' ? 'primary' : 'outline'}
              icon={PackageCheck}
              onClick={() => setSubTab('returns')}
            >
              Đối soát thu hồi hoàn ({returnsTotalElements})
            </Button>
            <Button
              size="sm"
              variant={subTab === 'settlements' ? 'primary' : 'outline'}
              icon={History}
              onClick={() => setSubTab('settlements')}
            >
              Lịch sử chứng từ đối soát ({settlementTotalElements})
            </Button>
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <Button size="sm" variant="outline" icon={Download} onClick={handleExportCurrentCsv}>
              Xuất CSV
            </Button>
            <Button
              size="sm"
              variant="outline"
              icon={RefreshCw}
              loading={summaryLoading || ordersLoading || settlementLoading || returnsLoading}
              onClick={handleRefreshAll}
            >
              Làm mới
            </Button>
          </div>
        </div>

        <form onSubmit={handleSearchSubmit} className="admin-order-filters" style={{ marginBottom: '14px' }}>
          <Input
            label="Từ khóa tìm kiếm"
            placeholder={
              subTab === 'orders'
                ? 'Mã đơn, tên khách, SĐT, tên tài xế, mã vận đơn...'
                : 'Mã chứng từ COD-SETTLED-..., mã đơn, ghi chú...'
            }
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            icon={Search}
            className="form-group-compact"
          />

          {subTab === 'orders' && (
            <>
              <Select
                label="Trạng thái đối soát COD"
                value={codState}
                onChange={(e) => {
                  setCodState(e.target.value);
                  setSelectedOrderMap({});
                }}
                className="form-group-compact"
              >
                <option value="PENDING">Chờ bàn giao COD</option>
                <option value="SETTLED">Đã bàn giao đủ</option>
                <option value="IN_TRANSIT">Đang giao COD</option>
                <option value="ALL">Tất cả đơn COD giao vận</option>
              </Select>

              <Select
                label="Tài xế / Kênh giao hàng"
                value={channelFilter}
                onChange={(e) => {
                  setChannelFilter(e.target.value);
                  setSelectedOrderMap({});
                }}
                className="form-group-compact"
              >
                <option value="ALL">Tất cả tài xế & vận chuyển</option>
                <option value="CARRIER">Đơn vị vận chuyển ngoài / Tự giao</option>
                {shipperOptions.map((s) => (
                  <option key={s.shipperId} value={`SHIPPER:${s.shipperId}`}>
                    {s.label} {s.phone ? `(${s.phone})` : ''}
                  </option>
                ))}
              </Select>
            </>
          )}

          {subTab === 'returns' && (
            <>
              <Select
                label="Trạng thái thu hồi hoàn"
                value={returnStatusFilter}
                onChange={(e) => setReturnStatusFilter(e.target.value)}
                className="form-group-compact"
              >
                <option value="ALL">Tất cả phiếu Shipper thu hồi</option>
                <option value="APPROVED">Đã duyệt · Chờ điều phối</option>
                <option value="PICKUP_ASSIGNED">Đã điều phối · Chờ lấy</option>
                <option value="PICKED_UP">Shipper đã lấy · Chờ trả kho</option>
                <option value="RECEIVED">Kho đã kiểm nhận</option>
                <option value="REFUNDED">Đã hoàn tiền</option>
              </Select>

              <Select
                label="Tài xế thu hồi"
                value={channelFilter}
                onChange={(e) => setChannelFilter(e.target.value)}
                className="form-group-compact"
              >
                <option value="ALL">Tất cả tài xế</option>
                {shipperOptions.map((s) => (
                  <option key={s.shipperId} value={`SHIPPER:${s.shipperId}`}>
                    {s.label} {s.phone ? `(${s.phone})` : ''}
                  </option>
                ))}
              </Select>
            </>
          )}

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

          {(keywordInput ||
            keyword ||
            codState !== 'PENDING' ||
            returnStatusFilter !== 'ALL' ||
            channelFilter !== 'ALL' ||
            fromDate ||
            toDate) && (
            <Button type="button" variant="outline" onClick={handleResetFilters}>
              Đặt lại
            </Button>
          )}
        </form>

        {selectedChannelSummary && subTab !== 'settlements' && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '10px',
              flexWrap: 'wrap',
              padding: '10px 12px',
              marginBottom: '14px',
              borderRadius: '10px',
              background: '#F9FAFB',
              border: '1px solid #D1D5DB',
              color: '#111827',
              fontSize: '12.5px',
            }}
          >
            <div>
              Đang lọc theo <strong>{selectedChannelSummary.label}</strong> · Chờ nộp:{' '}
              <strong>{selectedChannelSummary.pendingOrdersCount}</strong> · Đang giao:{' '}
              <strong>{selectedChannelSummary.inTransitOrdersCount}</strong> · Đã bàn giao:{' '}
              <strong>{selectedChannelSummary.settledOrdersCount}</strong> đơn (
              <strong>{formatCurrency(selectedChannelSummary.remittedAmount)}</strong>)
              {(selectedChannelSummary.pendingPickupReturnsCount > 0 ||
                selectedChannelSummary.inCustodyReturnsCount > 0) && (
                <>
                  {' '}
                  · Thu hồi hoàn:{' '}
                  <strong>
                    {selectedChannelSummary.pendingPickupReturnsCount + selectedChannelSummary.inCustodyReturnsCount}
                  </strong>
                </>
              )}
            </div>
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
              {(subTab !== 'orders' || codState !== 'ALL') && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setSubTab('orders');
                    setCodState('ALL');
                  }}
                >
                  Xem tất cả đơn COD
                </Button>
              )}
              {selectedChannelSummary.inTransitOrdersCount > 0 &&
                (subTab !== 'orders' || codState !== 'IN_TRANSIT') && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      setSubTab('orders');
                      setCodState('IN_TRANSIT');
                    }}
                  >
                    Đang giao COD ({selectedChannelSummary.inTransitOrdersCount})
                  </Button>
                )}
              <Button size="sm" variant="outline" onClick={() => setChannelFilter('ALL')}>
                Bỏ lọc kênh
              </Button>
            </div>
          </div>
        )}

        {/* Thanh đối soát hàng loạt khi có đơn được tick */}
        {subTab === 'orders' && selectedOrdersList.length > 0 && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '12px',
              flexWrap: 'wrap',
              padding: '12px 14px',
              marginBottom: '14px',
              borderRadius: '10px',
              background: '#FFFBEB',
              border: '1px solid #FDE68A',
            }}
          >
            <div style={{ fontSize: '13px', color: '#78350F' }}>
              Đã chọn <strong>{selectedOrdersList.length} đơn hàng</strong> · Tổng tiền COD cần bàn giao:{' '}
              <strong style={{ fontSize: '15px', color: '#B45309' }}>
                {formatCurrency(selectedTotalAmount)}
              </strong>
              {!isAdmin && (
                <span style={{ marginLeft: '8px', color: '#9A3412' }}>
                  (Chỉ tài khoản ADMIN mới có quyền xác nhận thu tiền COD)
                </span>
              )}
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <Button size="sm" variant="outline" onClick={() => setSelectedOrderMap({})}>
                Bỏ chọn
              </Button>
              {isAdmin && (
                <Button size="sm" variant="primary" icon={Banknote} onClick={openBatchModal}>
                  Xác nhận đối soát hàng loạt
                </Button>
              )}
            </div>
          </div>
        )}

        {subTab === 'orders' ? (
          <DataTable
            columns={orderColumns}
            data={orders}
            loading={ordersLoading}
            emptyMessage="Không có đơn hàng COD nào thuộc bộ lọc đối soát hiện tại."
            page={ordersPage}
            totalPages={ordersTotalPages}
            totalElements={ordersTotalElements}
            onPageChange={(p) => loadOrders(p)}
          />
        ) : subTab === 'returns' ? (
          <DataTable
            columns={returnColumns}
            data={filteredReturns}
            loading={returnsLoading}
            emptyMessage="Chưa có phiếu thu hồi hàng hoàn SHIPPER_PICKUP nào thuộc bộ lọc hiện tại."
            page={returnsPage}
            totalPages={returnsTotalPages}
            totalElements={returnsTotalElements}
            onPageChange={(p) => loadReturns(p)}
          />
        ) : (
          <DataTable
            columns={settlementColumns}
            data={settlements}
            loading={settlementLoading}
            emptyMessage="Chưa ghi nhận chứng từ bàn giao COD nào."
            page={settlementPage}
            totalPages={settlementTotalPages}
            totalElements={settlementTotalElements}
            onPageChange={(p) => loadSettlements(p)}
          />
        )}
      </div>

      {/* Modal xác nhận đối soát COD hàng loạt */}
      <Modal
        isOpen={batchModalOpen}
        onClose={() => !batchBusy && setBatchModalOpen(false)}
        title={`Xác nhận đối soát COD hàng loạt (${selectedOrdersList.length} đơn)`}
        maxWidth="680px"
        footer={
          <>
            <Button variant="outline" onClick={() => setBatchModalOpen(false)} disabled={batchBusy}>
              Hủy
            </Button>
            <Button variant="primary" onClick={handleBatchSubmit} loading={batchBusy}>
              Xác nhận đã nhận {formatCurrency(selectedTotalAmount)}
            </Button>
          </>
        }
      >
        <form onSubmit={handleBatchSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {errorMsg && <AlertBanner type="danger" message={errorMsg} />}

          <div
            style={{
              background: '#F8FAFC',
              border: '1px solid var(--border-subtle)',
              borderRadius: '8px',
              padding: '10px 12px',
              maxHeight: '220px',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
            }}
          >
            {selectedOrdersList.map((o) => (
              <div
                key={o.id}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  fontSize: '12.5px',
                  borderBottom: '1px solid #E2E8F0',
                  paddingBottom: '4px',
                }}
              >
                <span>
                  <strong>{o.orderNumber || `#${o.id}`}</strong> · {o.shipperName || o.carrierName || 'Chưa gán'}
                </span>
                <strong style={{ color: '#B45309' }}>{formatCurrency(computeOrderUnsettledCod(o))}</strong>
              </div>
            ))}
          </div>

          <Input
            label="Mã phiếu / chứng từ đối soát tổng *"
            value={batchRef}
            onChange={(e) => setBatchRef(e.target.value)}
            maxLength={50}
            required
          />

          <Input
            label="Nội dung xác nhận bàn giao *"
            value={batchReason}
            onChange={(e) => setBatchReason(e.target.value)}
            maxLength={250}
            required
          />
        </form>
      </Modal>
    </div>
  );
}
