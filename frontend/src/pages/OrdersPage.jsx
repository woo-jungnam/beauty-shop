import { CommerceOperationsPanel } from '../features/commerce/AdminCommercePanels';
import { ServiceBookingOrders } from '../features/spa/ServiceBookingOrders';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Search,
  Filter,
  Eye,
  Edit,
  DollarSign,
  RefreshCw,
  CheckCircle2,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
  getOrderStatusBadge,
  getOrderType,
  getOrderTypeBadge,
  getPaymentStatusBadge,
  getPaymentMethodLabel,
  resolveMediaUrl,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';
import { ConfirmModal } from '../shared/ui/ConfirmModal';
import { useAuth } from '../app/providers/AuthProvider';

export const OrdersPage = () => {
  const { isAdmin } = useAuth();
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const [keyword, setKeyword] = useState('');
  const [orderTypeTab, setOrderTypeTab] = useState('ALL');
  const getInitialStatus = () => {
    const match = window.location.hash.match(/[?&]tab=([^&]+)/);
    return match ? match[1] : '';
  };
  const [status, setStatusState] = useState(getInitialStatus);

  const setStatus = useCallback((nextStatus) => {
    setStatusState(nextStatus);
    const base = window.location.hash.split('?')[0];
    window.location.hash = nextStatus ? `${base}?tab=${nextStatus}` : base;
  }, []);

  useEffect(() => {
    const handleHashSync = () => {
      const match = window.location.hash.match(/[?&]tab=([^&]+)/);
      if (match && match[1]) {
        setStatusState(match[1]);
      } else if (!window.location.hash.includes('?tab=')) {
        setStatusState('');
      }
    };
    window.addEventListener('hashchange', handleHashSync);
    return () => window.removeEventListener('hashchange', handleHashSync);
  }, []);
  const [paymentStatus, setPaymentStatus] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [selectedOrder, setSelectedOrder] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const detailRequest = useRef(0);
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [refundModalOpen, setRefundModalOpen] = useState(false);

  const [targetStatus, setTargetStatus] = useState('CONFIRMED');
  const [carrierName, setCarrierName] = useState('');
  const [trackingCode, setTrackingCode] = useState('');
  const [cancelReason, setCancelReason] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [refundReference, setRefundReference] = useState('');
  const [refundReason, setRefundReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [confirmConfig, setConfirmConfig] = useState({
    isOpen: false,
    title: '',
    message: '',
    detail: '',
    isPermanent: false,
    loading: false,
    onConfirm: null,
  });

  const askConfirm = ({ title, message, detail, isPermanent = false, confirmText, onConfirm }) => {
    setConfirmConfig({
      isOpen: true,
      title,
      message,
      detail,
      isPermanent,
      confirmText,
      loading: false,
      onConfirm,
    });
  };

  const closeConfirm = () => {
    setConfirmConfig((prev) => ({ ...prev, isOpen: false, loading: false }));
  };

  const executeConfirm = async () => {
    if (!confirmConfig.onConfirm) return;
    setConfirmConfig((prev) => ({ ...prev, loading: true }));
    try {
      await confirmConfig.onConfirm();
      closeConfirm();
    } catch (err) {
      alert(err.message || 'Thao tác không thành công');
      setConfirmConfig((prev) => ({ ...prev, loading: false }));
    }
  };

  const fetchOrders = async (pageNum = 0) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', pageNum);
      params.append('size', '15');
      if (keyword.trim()) params.append('keyword', keyword.trim());
      if (status) params.append('status', status);
      if (paymentStatus) params.append('paymentStatus', paymentStatus);
      if (fromDate) params.append('from', `${fromDate}T00:00:00Z`);
      if (toDate) params.append('to', `${toDate}T23:59:59Z`);
      if (orderTypeTab && orderTypeTab !== 'ALL') params.append('orderType', orderTypeTab);

      const res = await apiClient.get(`${ENDPOINTS.ORDERS.LIST}?${params.toString()}`);
      const pageData = res.data || res;
      setOrders(pageData.content || []);
      setPage(pageData.page ?? pageNum);
      setTotalPages(pageData.totalPages ?? 1);
      setTotalElements(pageData.totalElements ?? 0);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(0);
  }, [status, paymentStatus, fromDate, toDate, orderTypeTab]);

  const handleSearch = (e) => {
    e.preventDefault();
    fetchOrders(0);
  };

  const handleOpenDetail = async (order) => {
    const request = ++detailRequest.current;
    setSelectedOrder(order);
    setDetailModalOpen(true);
    setDetailLoading(true);
    setDetailError('');
    try {
      const res = await apiClient.get(ENDPOINTS.ORDERS.ADMIN_DETAIL(order.id));
      const fullOrder = res.data || res;
      if (!fullOrder?.id || !Array.isArray(fullOrder.items)) throw new Error('Dữ liệu chi tiết đơn hàng không đầy đủ.');
      if (request === detailRequest.current) setSelectedOrder(fullOrder);
    } catch (e) {
      if (request === detailRequest.current) setDetailError(e.message || 'Không tải được chi tiết đơn hàng.');
    } finally {
      if (request === detailRequest.current) setDetailLoading(false);
    }
  };

  const handleOpenStatusModal = (order) => {
    setSelectedOrder(order);
    setTargetStatus(order.status === 'PENDING' ? 'CONFIRMED' : order.status);
    setCarrierName(order.carrierName || '');
    setTrackingCode(order.trackingCode || '');
    setCancelReason('');
    setErrorMessage('');
    setStatusModalOpen(true);
  };

  const handleUpdateStatusSubmit = async (e) => {
    e.preventDefault();
    if (!selectedOrder) return;
    setSubmitting(true);
    setErrorMessage('');

    try {
      await apiClient.put(ENDPOINTS.ORDERS.UPDATE_STATUS(selectedOrder.id), {
        status: targetStatus,
        carrierName: targetStatus === 'SHIPPED' ? carrierName : undefined,
        trackingCode: targetStatus === 'SHIPPED' ? trackingCode : undefined,
        notes: ['CANCELLED', 'DELIVERY_FAILED', 'RETURN_TO_SENDER'].includes(targetStatus) ? cancelReason.trim() : undefined,
      });
      setStatusModalOpen(false);
      fetchOrders(page);
    } catch (err) {
      setErrorMessage(err.message || 'Lỗi cập nhật trạng thái đơn hàng');
    } finally {
      setSubmitting(false);
    }
  };

  const handleOpenRefundModal = (order) => {
    setSelectedOrder(order);
    setRefundAmount(String(Math.max(0, Number(order.paidAmount || 0) - Number(order.refundedAmount || 0))));
    setRefundReference(`REF-${order.orderCode || order.id}-${Date.now().toString().slice(-6)}`);
    setRefundReason('Hoàn tiền theo yêu cầu hủy / trả hàng');
    setErrorMessage('');
    setRefundModalOpen(true);
  };

  const handleRefundSubmit = async (e) => {
    e.preventDefault();
    if (!selectedOrder) return;
    setSubmitting(true);
    setErrorMessage('');

    try {
      await apiClient.post(ENDPOINTS.ORDERS.CONFIRM_REFUND(selectedOrder.id), {
        reference: refundReference.trim() || `REF-${selectedOrder.id}-${Date.now()}`,
        amount: parseFloat(refundAmount),
        approvalReason: refundReason.trim(),
      });
      setRefundModalOpen(false);
      fetchOrders(page);
    } catch (err) {
      setErrorMessage(err.message || 'Lỗi xác nhận hoàn tiền');
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmPayment = (order) => {
    askConfirm({
      title: 'Xác nhận thanh toán đơn hàng',
      message: `Đánh dấu đơn hàng ${order.orderCode || '#' + order.id} đã thanh toán (PAID)?`,
      detail: 'Hệ thống sẽ cập nhật trạng thái thanh toán sang Đã thanh toán và kích hoạt quy trình chuẩn bị giao hàng hoặc cấp phát vé liệu trình tương ứng.',
      isPermanent: false,
      confirmText: 'Xác nhận PAID',
      onConfirm: async () => {
        await apiClient.post(ENDPOINTS.ORDERS.CONFIRM_PAYMENT(order.id));
        if (selectedOrder && selectedOrder.id === order.id) {
          setSelectedOrder((prev) => ({ ...prev, paymentStatus: 'PAID' }));
        }
        fetchOrders(page);
      },
    });
  };

  const columns = [
    {
      header: 'Mã đơn / ID',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>{row.orderCode || row.orderNumber || `#${row.id}`}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID: {row.id}</div>
        </div>
      ),
      width: '130px',
    },
    {
      header: 'Loại đơn',
      accessor: (row) => {
        const typeBadge = getOrderTypeBadge(row);
        return (
          <Badge variant={typeBadge.variant}>
            {typeBadge.text}
          </Badge>
        );
      },
      width: '140px',
    },
    {
      header: 'Khách hàng',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.customerName || 'Khách vãng lai'}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{row.customerPhone || 'Không có SĐT'}</div>
        </div>
      ),
    },
    {
      header: 'Tổng tiền',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>{formatCurrency(row.totalAmount)}</div>
          {row.discountAmount > 0 && (
            <div style={{ fontSize: '11px', color: 'var(--color-success-700)' }}>
              Giảm: {formatCurrency(row.discountAmount)}
            </div>
          )}
        </div>
      ),
      align: 'right',
    },
    {
      header: 'Thanh toán',
      accessor: (row) => {
        const badge = getPaymentStatusBadge(row.paymentStatus);
        return <Badge variant={badge.variant}>{badge.text}</Badge>;
      },
    },
    {
      header: 'Trạng thái đơn',
      accessor: (row) => {
        const badge = getOrderStatusBadge(row.status);
        return <Badge variant={badge.variant}>{badge.text}</Badge>;
      },
    },
    {
      header: 'Ngày đặt',
      accessor: (row) => formatDateTime(row.createdAt),
      width: '140px',
    },
    {
      header: 'Thao tác',
      align: 'right',
      accessor: (row) => (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleOpenDetail(row)}
            title="Xem chi tiết đơn"
          >
            <Eye size={14} />
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenStatusModal(row)}
            title="Đổi trạng thái"
          >
            <Edit size={14} />
          </Button>
          {row.paymentStatus !== 'PAID' && row.status !== 'CANCELLED' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleConfirmPayment(row)}
              title="Đánh dấu đã thanh toán (PAID)"
              style={{ color: '#059669', borderColor: '#059669' }}
            >
              <CheckCircle2 size={14} />
            </Button>
          )}
          {isAdmin && (row.status === 'CANCELLED' || row.status === 'RETURNED' || row.paymentStatus === 'REFUND_PENDING') &&
            row.paymentStatus !== 'REFUNDED' && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => handleOpenRefundModal(row)}
                title="Xác nhận hoàn tiền"
              >
                <DollarSign size={14} />
              </Button>
            )}
        </div>
      ),
      render: (row) => (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleOpenDetail(row)}
            title="Xem chi tiết đơn"
          >
            <Eye size={14} />
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenStatusModal(row)}
            title="Đổi trạng thái"
          >
            <Edit size={14} />
          </Button>
          {row.paymentStatus !== 'PAID' && row.status !== 'CANCELLED' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleConfirmPayment(row)}
              title="Đánh dấu đã thanh toán (PAID)"
              style={{ color: '#059669', borderColor: '#059669' }}
            >
              <CheckCircle2 size={14} />
            </Button>
          )}
          {isAdmin && (row.status === 'CANCELLED' || row.status === 'RETURNED' || row.paymentStatus === 'REFUND_PENDING') &&
            row.paymentStatus !== 'REFUNDED' && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => handleOpenRefundModal(row)}
                title="Xác nhận hoàn tiền"
              >
                <DollarSign size={14} />
              </Button>
            )}
        </div>
      ),
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản lý Đơn hàng & Luân chuyển</h1>
          <p className="page-subtitle">
            Theo dõi vòng đời đơn hàng, xác nhận giao dịch, xuất kho và xử lý hoàn trả
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => fetchOrders(page)}
          loading={loading}
          icon={RefreshCw}
        >
          Làm mới
        </Button>
      </div>

      {isAdmin && orderTypeTab !== 'PRODUCT' && <ServiceBookingOrders admin refreshKey={orders} />}
      <CommerceOperationsPanel orders={orders} onChanged={() => fetchOrders(page)} />
      <div
        className="card"
        style={{ padding: '16px', marginBottom: '20px' }}
      >
        <form
          onSubmit={handleSearch}
          className="admin-order-filters"
        >
          <Input
            label="Tìm kiếm từ khóa"
            placeholder="Mã đơn, tên khách, số điện thoại..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            icon={Search}
            className="form-group-compact"
          />

          <Select
            label="Trạng thái đơn hàng"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="form-group-compact"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Chờ xác nhận</option>
            <option value="CONFIRMED">Đã xác nhận</option>
            <option value="PROCESSING">Đang chuẩn bị hàng</option>
            <option value="DELIVERY_FAILED">Giao không thành công</option>
            <option value="RETURN_TO_SENDER">Hàng đã hoàn về kho</option>
            <option value="SHIPPED">Đang giao hàng</option>
            <option value="DELIVERED">Đã giao thành công</option>
            <option value="COMPLETED">Đã hoàn tất (Dịch vụ Spa)</option>
            <option value="CANCELLED">Đã hủy đơn</option>
            <option value="RETURNED">Đã hoàn trả</option>
          </Select>

          <Select
            label="Trạng thái thanh toán"
            value={paymentStatus}
            onChange={(e) => setPaymentStatus(e.target.value)}
            className="form-group-compact"
          >
            <option value="">Tất cả thanh toán</option>
            <option value="PENDING">Chờ thanh toán</option>
            <option value="PAID">Đã thanh toán</option>
            <option value="PARTIALLY_PAID">Thanh toán một phần</option>
            <option value="UNPAID">Chưa thanh toán</option>
            <option value="REFUND_PENDING">Chờ hoàn tiền</option>
            <option value="REFUNDED">Đã hoàn tiền</option>
            <option value="FAILED">Thanh toán thất bại</option>
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
            Lọc đơn
          </Button>

          {(keyword || status || paymentStatus || fromDate || toDate) && (
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setKeyword('');
                setStatus('');
                setPaymentStatus('');
                setFromDate('');
                setToDate('');
              }}
            >
              Đặt lại
            </Button>
          )}
        </form>
      </div>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '12px', flexWrap: 'wrap' }}>
        <button
          type="button"
          className={`btn ${orderTypeTab === 'ALL' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setOrderTypeTab('ALL')}
          style={{ fontSize: '13px', padding: '6px 14px' }}
        >
          Tất cả đơn
        </button>
        <button
          type="button"
          className={`btn ${orderTypeTab === 'PRODUCT' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setOrderTypeTab('PRODUCT')}
          style={{ fontSize: '13px', padding: '6px 14px' }}
        >
          Đơn Mỹ phẩm (Vận chuyển)
        </button>
        <button
          type="button"
          className={`btn ${orderTypeTab === 'SPA' ? 'btn-primary' : 'btn-outline'}`}
          onClick={() => setOrderTypeTab('SPA')}
          style={{ fontSize: '13px', padding: '6px 14px' }}
        >
          Dịch vụ & Liệu trình Spa
        </button>
      </div>

      <DataTable
        columns={columns}
        data={orders}
        loading={loading}
        emptyMessage="Không tìm thấy đơn hàng nào phù hợp bộ lọc."
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => fetchOrders(p)}
      />

      <Modal
        isOpen={detailModalOpen}
        onClose={() => { detailRequest.current++; setDetailModalOpen(false); }}
        title={`Chi tiết đơn hàng: ${selectedOrder?.orderNumber || selectedOrder?.orderCode || `#${selectedOrder?.id}`}`}
        maxWidth="680px"
      >
        {detailLoading && <p role="status">Đang tải chi tiết đơn hàng…</p>}
        {detailError && <div role="alert"><p>{detailError}</p><Button onClick={() => handleOpenDetail(selectedOrder)}>Thử lại</Button></div>}
        {selectedOrder && !detailLoading && !detailError && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div className="grid-2" style={{ gap: '12px' }}>
              <div style={{ padding: '12px', border: '1px solid var(--border-subtle)', background: 'var(--color-primary-50)' }}>
                <div style={{ fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                  Thông tin người nhận
                </div>
                <div style={{ fontWeight: 600, marginTop: '4px' }}>{selectedOrder.customerName}</div>
                <div>Điện thoại: {selectedOrder.customerPhone}</div>
                <div>Địa chỉ: {selectedOrder.shippingAddress || 'Nhận tại cửa hàng'}</div>
              </div>

              <div style={{ padding: '12px', border: '1px solid var(--border-subtle)', background: 'var(--color-primary-50)' }}>
                <div style={{ fontWeight: 700, fontSize: '11px', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                  Trạng thái vận đơn
                </div>
                <div style={{ display: 'flex', gap: '6px', marginTop: '6px' }}>
                  <Badge variant={getOrderStatusBadge(selectedOrder.status).variant}>
                    {getOrderStatusBadge(selectedOrder.status).text}
                  </Badge>
                  <Badge variant={getPaymentStatusBadge(selectedOrder.paymentStatus).variant}>
                    {getPaymentStatusBadge(selectedOrder.paymentStatus).text}
                  </Badge>
                </div>
                {selectedOrder.paymentStatus !== 'PAID' && selectedOrder.status !== 'CANCELLED' && (
                  <div style={{ marginTop: '8px' }}>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleConfirmPayment(selectedOrder)}
                      style={{ color: '#059669', borderColor: '#059669', fontSize: '12px', padding: '4px 8px' }}
                    >
                      <CheckCircle2 size={13} style={{ marginRight: '4px' }} />
                      Xác nhận đã thanh toán (PAID)
                    </Button>
                  </div>
                )}
                {selectedOrder.paymentMethod && (
                  <div style={{ marginTop: '8px', fontSize: '12px' }}>
                    Hình thức: <strong>{getPaymentMethodLabel(selectedOrder.paymentMethod)}</strong>
                  </div>
                )}
                {selectedOrder.carrierName && (
                  <div style={{ marginTop: '4px', fontSize: '12px' }}>
                    Đơn vị giao: <strong>{selectedOrder.carrierName}</strong>
                  </div>
                )}
                {selectedOrder.trackingCode && (
                  <div style={{ fontSize: '12px' }}>
                    Mã vận đơn: <strong>{selectedOrder.trackingCode}</strong>
                  </div>
                )}
                {selectedOrder.cancelReason && (
                  <div style={{ marginTop: '4px', fontSize: '12px', color: 'var(--color-danger-700)' }}>
                    Lý do hủy: {selectedOrder.cancelReason}
                  </div>
                )}
              </div>
            </div>

            {selectedOrder.servicePackageId && (
              <div style={{ padding: '12px 16px', background: '#FDF2F8', border: '1px solid #FBCFE8', borderRadius: '8px' }}>
                <div style={{ fontWeight: 700, color: '#9D174D', fontSize: '13px' }}>
                  Gói dịch vụ Spa / Liệu trình
                </div>
                <div style={{ fontSize: '12px', marginTop: '4px', color: '#831843' }}>
                  Mã gói dịch vụ: <strong>#{selectedOrder.servicePackageId}</strong>
                  {selectedOrder.spaBenefitType && (
                    <span> · Loại quyền lợi: <strong>{selectedOrder.spaBenefitType === 'TREATMENT_COURSE' ? 'Lộ trình chăm sóc da chuyên sâu' : 'Gói dịch vụ Spa'}</strong></span>
                  )}
                </div>
              </div>
            )}

            <div>
              <div style={{ fontWeight: 700, fontSize: '12px', textTransform: 'uppercase', marginBottom: '8px' }}>
                Danh sách mặt hàng ({selectedOrder.items?.length || 0})
              </div>
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th style={{ width: '56px' }}>Ảnh</th>
                      <th>Sản phẩm / Biến thể</th>
                      <th style={{ textAlign: 'right' }}>Đơn giá</th>
                      <th style={{ textAlign: 'center' }}>Số lượng</th>
                      <th style={{ textAlign: 'right' }}>Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(selectedOrder.items || []).map((item, idx) => {
                      const unitPrice = Number(item.price || 0);
                      const discount = Number(item.discount || 0);
                      const effectivePrice = Math.max(0, unitPrice - discount);
                      const lineTotal = effectivePrice * Number(item.quantity || 1);
                      const img = item.imageUrl ? resolveMediaUrl(item.imageUrl) : null;

                      return (
                        <tr key={idx}>
                          <td>
                            <div style={{ width: 44, height: 44, borderRadius: 4, overflow: 'hidden', backgroundColor: 'var(--color-primary-50)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                              {img ? (
                                <img src={img} alt={item.productName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={e => { e.currentTarget.style.display = 'none'; }} />
                              ) : (
                                <Eye size={18} color="var(--text-muted)" />
                              )}
                            </div>
                          </td>
                          <td>
                            <div style={{ fontWeight: 600 }}>{item.productName}</div>
                            <div style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 2 }}>
                              {item.variantName && <span>Phân loại: <strong>{item.variantName}</strong></span>}
                              {item.sku && <span>SKU: <code>{item.sku}</code></span>}
                            </div>
                          </td>
                          <td style={{ textAlign: 'right' }}>
                            {discount > 0 ? (
                              <div>
                                <div style={{ fontWeight: 600 }}>{formatCurrency(effectivePrice)}</div>
                                <div style={{ fontSize: '11px', textDecoration: 'line-through', color: 'var(--text-muted)' }}>{formatCurrency(unitPrice)}</div>
                              </div>
                            ) : (
                              <span>{formatCurrency(unitPrice)}</span>
                            )}
                          </td>
                          <td style={{ textAlign: 'center', fontWeight: 600 }}>{item.quantity}</td>
                          <td style={{ textAlign: 'right', fontWeight: 700, fontFamily: 'monospace' }}>
                            {formatCurrency(lineTotal)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {selectedOrder.spaVisitItems && selectedOrder.spaVisitItems.length > 0 && (
              <div style={{ marginTop: '16px' }}>
                <div style={{ fontWeight: 700, fontSize: '12px', textTransform: 'uppercase', marginBottom: '8px' }}>
                  Dịch vụ Spa thực hiện ({selectedOrder.spaVisitItems.length})
                </div>
                <div className="table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Dịch vụ Spa</th>
                        <th style={{ textAlign: 'right' }}>Đơn giá</th>
                        <th style={{ textAlign: 'center' }}>Số buổi</th>
                        <th style={{ textAlign: 'right' }}>Thành tiền</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedOrder.spaVisitItems.map((sv, idx) => (
                        <tr key={idx}>
                          <td><strong>{sv.serviceName}</strong></td>
                          <td style={{ textAlign: 'right' }}>{formatCurrency(sv.price)}</td>
                          <td style={{ textAlign: 'center' }}>{sv.performedSessions ?? 1}</td>
                          <td style={{ textAlign: 'right', fontWeight: 600 }}>
                            {formatCurrency((sv.price || 0) * (sv.performedSessions || 1))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
                alignItems: 'flex-end',
                paddingTop: '14px',
                borderTop: '1px solid var(--border-subtle)',
                fontSize: '13px',
              }}
            >
              {selectedOrder.subTotal != null && (
                <div style={{ display: 'flex', gap: 20 }}>
                  <span style={{ color: 'var(--text-muted)' }}>Tạm tính tiền hàng:</span>
                  <span style={{ fontFamily: 'monospace' }}>{formatCurrency(selectedOrder.subTotal)}</span>
                </div>
              )}
              {selectedOrder.discountAmount != null && Number(selectedOrder.discountAmount) > 0 && (
                <div style={{ display: 'flex', gap: 20, color: 'var(--color-success-700)' }}>
                  <span>Chiết khấu / Voucher:</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>-{formatCurrency(selectedOrder.discountAmount)}</span>
                </div>
              )}
              {selectedOrder.shippingFee != null && (
                <div style={{ display: 'flex', gap: 20 }}>
                  <span style={{ color: 'var(--text-muted)' }}>Phí vận chuyển:</span>
                  <span style={{ fontFamily: 'monospace' }}>{Number(selectedOrder.shippingFee) === 0 ? 'MIỄN PHÍ' : formatCurrency(selectedOrder.shippingFee)}</span>
                </div>
              )}
              <div style={{ display: 'flex', gap: 20, fontSize: '15px', fontWeight: 700, paddingTop: 4, borderTop: '1px dashed var(--border-subtle)' }}>
                <span>Tổng giá trị đơn:</span>
                <span style={{ color: 'var(--color-primary-800)', fontFamily: 'monospace' }}>{formatCurrency(selectedOrder.totalAmount)}</span>
              </div>
              <div style={{ display: 'flex', gap: 20, color: '#059669' }}>
                <span>Đã thanh toán:</span>
                <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{formatCurrency(selectedOrder.paidAmount || 0)}</span>
              </div>
              {selectedOrder.refundedAmount != null && Number(selectedOrder.refundedAmount) > 0 && (
                <div style={{ display: 'flex', gap: 20, color: '#DC2626' }}>
                  <span>Đã hoàn tiền:</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>-{formatCurrency(selectedOrder.refundedAmount)}</span>
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>

      <Modal
        isOpen={statusModalOpen}
        onClose={() => setStatusModalOpen(false)}
        title={`Cập nhật trạng thái đơn: ${selectedOrder?.orderCode || `#${selectedOrder?.id}`}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setStatusModalOpen(false)}>
              Hủy bỏ
            </Button>
            <Button
              variant="primary"
              onClick={handleUpdateStatusSubmit}
              loading={submitting}
            >
              Lưu thay đổi
            </Button>
          </>
        }
      >
        <form onSubmit={handleUpdateStatusSubmit}>
          {errorMessage && (
            <div
              style={{
                padding: '8px 12px',
                backgroundColor: 'var(--color-danger-50)',
                border: '1px solid var(--color-danger-600)',
                color: 'var(--color-danger-700)',
                fontSize: '12px',
                marginBottom: '14px',
              }}
            >
              {errorMessage}
            </div>
          )}

          {selectedOrder && getOrderType(selectedOrder) !== 'PRODUCT' ? (
            <>
              <div style={{ padding: '8px 12px', backgroundColor: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: '6px', fontSize: '12px', color: '#1E40AF', marginBottom: '14px' }}>
                ℹ️ Đơn dịch vụ Spa (thực hiện tại cơ sở hoặc kích hoạt vé điện tử), không qua khâu vận chuyển hàng hóa.
              </div>
              <Select
                label="Chọn trạng thái đơn dịch vụ mới"
                value={targetStatus}
                onChange={(e) => setTargetStatus(e.target.value)}
              >
                <option value="CONFIRMED">Xác nhận đơn dịch vụ</option>
                <option value="COMPLETED">Đã hoàn tất dịch vụ</option>
                <option value="CANCELLED">Hủy đơn dịch vụ</option>
              </Select>
            </>
          ) : (
            <>
              <Select
                label="Chọn trạng thái luân chuyển mới"
                value={targetStatus}
                onChange={(e) => setTargetStatus(e.target.value)}
              >
                <option value="CONFIRMED">Xác nhận đơn hàng</option>
                <option value="PROCESSING">Chuyển sang chuẩn bị hàng</option>
                <option value="DELIVERY_FAILED">Giao không thành công</option>
                <option value="RETURN_TO_SENDER">Hàng đã hoàn về kho</option>
                <option value="SHIPPED">Xuất kho bàn giao vận chuyển</option>
                <option value="DELIVERED">Xác nhận giao thành công</option>
                <option value="CANCELLED">Hủy đơn hàng</option>
                <option value="RETURNED">Khách trả hàng về kho</option>
              </Select>

              {targetStatus === 'SHIPPED' && (
                <div style={{ marginTop: '12px' }}>
                  <Input
                    label="Đơn vị vận chuyển (Carrier)"
                    placeholder="Ví dụ: Giao Hàng Tiết Kiệm, Viettel Post, GrabExpress..."
                    value={carrierName}
                    onChange={(e) => setCarrierName(e.target.value)}
                    required
                  />
                  <Input
                    label="Mã vận đơn theo dõi (Tracking Code)"
                    placeholder="Ví dụ: GHTK893284920"
                    value={trackingCode}
                    onChange={(e) => setTrackingCode(e.target.value)}
                    required
                  />
                </div>
              )}
            </>
          )}

          {['CANCELLED', 'DELIVERY_FAILED', 'RETURN_TO_SENDER'].includes(targetStatus) && (
            <div style={{ marginTop: '12px' }}>
              <Input
                label="Lý do hủy đơn hàng"
                placeholder="Khách đổi ý, hết hàng dự trữ, sai thông tin..."
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                required
              />
            </div>
          )}
        </form>
      </Modal>

      <Modal
        isOpen={refundModalOpen}
        onClose={() => setRefundModalOpen(false)}
        title={`Xác nhận hoàn tiền cho đơn: ${selectedOrder?.orderCode || `#${selectedOrder?.id}`}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setRefundModalOpen(false)}>
              Hủy
            </Button>
            <Button
              variant="danger"
              onClick={handleRefundSubmit}
              loading={submitting}
              icon={DollarSign}
            >
              Xác nhận hoàn tiền
            </Button>
          </>
        }
      >
        <form onSubmit={handleRefundSubmit}>
          {errorMessage && (
            <div
              style={{
                padding: '8px 12px',
                backgroundColor: 'var(--color-danger-50)',
                border: '1px solid var(--color-danger-600)',
                color: 'var(--color-danger-700)',
                fontSize: '12px',
                marginBottom: '14px',
              }}
            >
              {errorMessage}
            </div>
          )}

          <div
            style={{
              padding: '12px',
              backgroundColor: 'var(--color-warning-50)',
              border: '1px solid var(--color-warning-600)',
              color: 'var(--color-warning-700)',
              fontSize: '12px',
              marginBottom: '16px',
            }}
          >
            Hành động này sẽ cập nhật trạng thái thanh toán sang REFUNDED và ghi nhận sổ quỹ hoàn trả cho khách hàng.
          </div>

          <Input
            label="Mã giao dịch / Mã tham chiếu hoàn tiền (Reference)"
            value={refundReference}
            onChange={(e) => setRefundReference(e.target.value)}
            placeholder="REF-ORD123-987654..."
            required
          />

          <Input
            label="Số tiền hoàn lại (VNĐ)"
            type="number"
            value={refundAmount}
            onChange={(e) => setRefundAmount(e.target.value)}
            required
          />

          <Input
            label="Ghi chú hoàn tiền"
            value={refundReason}
            onChange={(e) => setRefundReason(e.target.value)}
            placeholder="Lý do hoàn trả cho khách..."
          />
        </form>
      </Modal>

      <ConfirmModal
        isOpen={confirmConfig.isOpen}
        onClose={closeConfirm}
        onConfirm={executeConfirm}
        title={confirmConfig.title}
        message={confirmConfig.message}
        detail={confirmConfig.detail}
        isPermanent={confirmConfig.isPermanent}
        confirmText={confirmConfig.confirmText}
        loading={confirmConfig.loading}
      />
    </div>
  );
};
