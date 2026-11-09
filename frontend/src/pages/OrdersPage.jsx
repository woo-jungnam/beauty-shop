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
  Package,
  Truck,
  Sparkles,
  Ticket,
  User,
  MapPin,
  Clock,
  ExternalLink,
  ShoppingBag,
  FileText,
  Calendar,
  Check,
  ChevronRight,
  Receipt,
  Printer,
  CreditCard,
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
import { AdminSectionNav } from '../shared/ui/AdminSectionNav';
import { useAuth } from '../app/providers/AuthProvider';

export const OrdersPage = () => {
  const { isAdmin } = useAuth();
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const [keyword, setKeyword] = useState('');
  const getInitialType = () => {
    const match = window.location.hash.match(/[?&]type=([^&]+)/);
    return match ? match[1].toUpperCase() : 'ALL';
  };
  const [orderTypeTab, setOrderTypeTabState] = useState(getInitialType);

  const setOrderTypeTab = useCallback((nextType) => {
    setOrderTypeTabState(nextType);
    setPage(0);
    const hash = window.location.hash || '#/admin/orders';
    const [path, query] = hash.split('?');
    const params = new URLSearchParams(query || '');
    if (nextType && nextType !== 'ALL') {
      params.set('type', nextType);
    } else {
      params.delete('type');
    }
    const qs = params.toString();
    window.location.hash = qs ? `${path}?${qs}` : path;
  }, []);

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
      } else if (!window.location.hash.includes('tab=')) {
        setStatusState('');
      }

      const typeMatch = window.location.hash.match(/[?&]type=([^&]+)/);
      if (typeMatch && typeMatch[1]) {
        setOrderTypeTabState(typeMatch[1].toUpperCase());
      } else if (!window.location.hash.includes('type=')) {
        setOrderTypeTabState('ALL');
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
      if (!fullOrder?.id) throw new Error('Dữ liệu chi tiết đơn hàng không đầy đủ.');
      fullOrder.items = Array.isArray(fullOrder.items) ? fullOrder.items : [];
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
        const type = getOrderType(row);
        let IconComponent = Package;
        let label = 'Đơn sản phẩm';
        if (type === 'SPA_PACKAGE') {
          IconComponent = Ticket;
          label = 'Đơn gói dịch vụ';
        } else if (type === 'SPA_VISIT') {
          IconComponent = Sparkles;
          label = 'Đơn dịch vụ đơn';
        }
        return (
          <Badge
            variant={typeBadge.variant}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '5px',
              padding: '3px 8px',
              fontWeight: 600,
              fontSize: '11.5px',
            }}
          >
            <IconComponent size={12} />
            <span>{label}</span>
          </Badge>
        );
      },
      width: '165px',
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
      {/* Header Điều hướng & Thao tác */}
      <AdminSectionNav
        rootTitle="Bán hàng & Kho vận"
        activeTab={orderTypeTab}
        onChange={(nextTab) => setOrderTypeTab(nextTab)}
        subtitle="Quản lý đơn hàng sản phẩm, gói dịch vụ và dịch vụ Spa"
        items={[
          {
            id: 'ALL',
            label: 'Tất cả đơn hàng',
            icon: ShoppingBag,
            description: 'Toàn bộ đơn hàng trong hệ thống',
            count: orderTypeTab === 'ALL' ? totalElements : null,
          },
          {
            id: 'PRODUCT',
            label: 'Đơn sản phẩm',
            icon: Package,
            description: 'Đóng gói xuất kho và giao hàng COD',
            count: orderTypeTab === 'PRODUCT' ? totalElements : null,
          },
          {
            id: 'SPA_PACKAGE',
            label: 'Đơn gói dịch vụ',
            icon: Ticket,
            description: 'Thẻ dịch vụ và liệu trình Spa trọn gói',
            count: orderTypeTab === 'SPA_PACKAGE' ? totalElements : null,
          },
          {
            id: 'SPA_VISIT',
            label: 'Đơn dịch vụ đơn',
            icon: Sparkles,
            description: 'Lịch hẹn và dịch vụ chăm sóc tại Spa',
            count: orderTypeTab === 'SPA_VISIT' ? totalElements : null,
          },
        ]}
        actions={
          <div className="page-header" style={{ margin: 0, padding: 0, border: 'none' }}>
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
        }
        showQuickPills
      />

      {/* Phân loại đơn hàng */}
      <div
        className="order-types-hub"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: '14px',
          marginBottom: '20px',
        }}
      >
        {/* Đơn sản phẩm */}
        <div
          role="button"
          tabIndex={0}
          onClick={() => setOrderTypeTab(orderTypeTab === 'PRODUCT' ? 'ALL' : 'PRODUCT')}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setOrderTypeTab(orderTypeTab === 'PRODUCT' ? 'ALL' : 'PRODUCT'); }}
          style={{
            padding: '14px 16px',
            background: orderTypeTab === 'PRODUCT' ? '#F5F3FF' : '#FFFFFF',
            border: `1.5px solid ${orderTypeTab === 'PRODUCT' ? '#7C3AED' : '#E5E7EB'}`,
            borderRadius: '10px',
            cursor: 'pointer',
            boxShadow: orderTypeTab === 'PRODUCT' ? '0 2px 8px rgba(124, 58, 237, 0.12)' : '0 1px 3px rgba(0,0,0,0.03)',
            transition: 'all 0.2s cubic-bezier(0.22, 1, 0.36, 1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '8px',
                background: '#EEF2FF',
                color: '#4F46E5',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <Package size={20} />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: '14px', color: '#1F2937' }}>
                Đơn sản phẩm
              </div>
              <div style={{ fontSize: '12px', color: '#6B7280' }}>
                Đóng gói & giao hàng COD
              </div>
            </div>
          </div>
          {orderTypeTab === 'PRODUCT' && (
            <Badge variant="primary" size="sm">Đang lọc</Badge>
          )}
        </div>

        {/* Đơn gói dịch vụ */}
        <div
          role="button"
          tabIndex={0}
          onClick={() => setOrderTypeTab(orderTypeTab === 'SPA_PACKAGE' ? 'ALL' : 'SPA_PACKAGE')}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setOrderTypeTab(orderTypeTab === 'SPA_PACKAGE' ? 'ALL' : 'SPA_PACKAGE'); }}
          style={{
            padding: '14px 16px',
            background: orderTypeTab === 'SPA_PACKAGE' ? '#F0F9FF' : '#FFFFFF',
            border: `1.5px solid ${orderTypeTab === 'SPA_PACKAGE' ? '#0284C7' : '#E5E7EB'}`,
            borderRadius: '10px',
            cursor: 'pointer',
            boxShadow: orderTypeTab === 'SPA_PACKAGE' ? '0 2px 8px rgba(2, 132, 199, 0.12)' : '0 1px 3px rgba(0,0,0,0.03)',
            transition: 'all 0.2s cubic-bezier(0.22, 1, 0.36, 1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '8px',
                background: '#E0F2FE',
                color: '#0284C7',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <Ticket size={20} />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: '14px', color: '#1F2937' }}>
                Đơn gói dịch vụ
              </div>
              <div style={{ fontSize: '12px', color: '#6B7280' }}>
                Thẻ liệu trình trọn gói
              </div>
            </div>
          </div>
          {orderTypeTab === 'SPA_PACKAGE' && (
            <Badge variant="primary" size="sm">Đang lọc</Badge>
          )}
        </div>

        {/* Đơn dịch vụ đơn */}
        <div
          role="button"
          tabIndex={0}
          onClick={() => setOrderTypeTab(orderTypeTab === 'SPA_VISIT' ? 'ALL' : 'SPA_VISIT')}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setOrderTypeTab(orderTypeTab === 'SPA_VISIT' ? 'ALL' : 'SPA_VISIT'); }}
          style={{
            padding: '14px 16px',
            background: orderTypeTab === 'SPA_VISIT' ? '#FFFBEB' : '#FFFFFF',
            border: `1.5px solid ${orderTypeTab === 'SPA_VISIT' ? '#D97706' : '#E5E7EB'}`,
            borderRadius: '10px',
            cursor: 'pointer',
            boxShadow: orderTypeTab === 'SPA_VISIT' ? '0 2px 8px rgba(217, 119, 6, 0.12)' : '0 1px 3px rgba(0,0,0,0.03)',
            transition: 'all 0.2s cubic-bezier(0.22, 1, 0.36, 1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '8px',
                background: '#FEF3C7',
                color: '#D97706',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <Sparkles size={20} />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: '14px', color: '#1F2937' }}>
                Đơn dịch vụ đơn
              </div>
              <div style={{ fontSize: '12px', color: '#6B7280' }}>
                Lịch hẹn & buổi lẻ tại Spa
              </div>
            </div>
          </div>
          {orderTypeTab === 'SPA_VISIT' && (
            <Badge variant="primary" size="sm">Đang lọc</Badge>
          )}
        </div>
      </div>

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

          {(keyword || status || paymentStatus || fromDate || toDate || orderTypeTab !== 'ALL') && (
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setKeyword('');
                setStatus('');
                setPaymentStatus('');
                setFromDate('');
                setToDate('');
                setOrderTypeTab('ALL');
              }}
            >
              Đặt lại
            </Button>
          )}
        </form>
      </div>

      {/* Tabs chuyển đổi phân loại đơn hàng */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px', marginBottom: '16px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '12px', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button
            type="button"
            className={`btn ${orderTypeTab === 'ALL' ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setOrderTypeTab('ALL')}
            style={{ fontSize: '13px', padding: '6px 14px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <ShoppingBag size={14} />
            <span>Tất cả đơn</span>
          </button>
          <button
            type="button"
            className={`btn ${orderTypeTab === 'PRODUCT' ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setOrderTypeTab('PRODUCT')}
            style={{ fontSize: '13px', padding: '6px 14px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Package size={14} />
            <span>Đơn sản phẩm</span>
          </button>
          <button
            type="button"
            className={`btn ${orderTypeTab === 'SPA_PACKAGE' ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setOrderTypeTab('SPA_PACKAGE')}
            style={{ fontSize: '13px', padding: '6px 14px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Ticket size={14} />
            <span>Đơn gói dịch vụ</span>
          </button>
          <button
            type="button"
            className={`btn ${orderTypeTab === 'SPA_VISIT' ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setOrderTypeTab('SPA_VISIT')}
            style={{ fontSize: '13px', padding: '6px 14px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Sparkles size={14} />
            <span>Đơn dịch vụ đơn</span>
          </button>
        </div>
        <div style={{ fontSize: '12.5px', color: 'var(--text-muted)' }}>
          Hiển thị: <strong>{totalElements.toLocaleString('vi-VN')}</strong> đơn hàng
        </div>
      </div>

      {/* Khu vực hiển thị bảng theo phân loại đơn hàng */}
      {orderTypeTab === 'SPA_VISIT' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Khối đơn đặt dịch vụ đơn lẻ */}
          <ServiceBookingOrders admin refreshKey={orders} />

          {/* Khối hóa đơn dịch vụ đơn đã quyết toán (nếu có) */}
          <div className="card" style={{ padding: '16px' }}>
            <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '12px', color: 'var(--text-main)' }}>
              Hóa đơn dịch vụ đơn đã lập & quyết toán
            </div>
            <DataTable
              columns={columns}
              data={orders}
              loading={loading}
              emptyMessage="Chưa có hóa đơn dịch vụ đơn nào đã lập."
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={(p) => fetchOrders(p)}
            />
          </div>
        </div>
      ) : (
        <>
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

          {/* Dịch vụ đơn đã đặt (hiển thị khi xem tất cả để theo dõi toàn bộ luồng) */}
          {isAdmin && orderTypeTab === 'ALL' && (
            <ServiceBookingOrders admin refreshKey={orders} />
          )}

          {/* Đổi trả sản phẩm & đối soát COD (chỉ hiển thị khi xem sản phẩm hoặc tất cả) */}
          {(orderTypeTab === 'ALL' || orderTypeTab === 'PRODUCT') && (
            <CommerceOperationsPanel orders={orders} onChanged={() => fetchOrders(page)} />
          )}
        </>
      )}

            <Modal
        isOpen={detailModalOpen}
        onClose={() => { detailRequest.current++; setDetailModalOpen(false); }}
        title={`Chi tiết đơn hàng: ${selectedOrder?.orderNumber || selectedOrder?.orderCode || `#${selectedOrder?.id}`}`}
        maxWidth="840px"
        footer={
          selectedOrder && !detailLoading && !detailError ? (
            <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <Button
                variant="outline"
                size="sm"
                icon={Printer}
                onClick={() => window.print()}
                title="In hóa đơn chi tiết"
              >
                In hóa đơn
              </Button>
              <div style={{ display: 'flex', gap: '8px' }}>
                <Button
                  variant="outline"
                  size="sm"
                  icon={Edit}
                  onClick={() => handleOpenStatusModal(selectedOrder)}
                >
                  Cập nhật trạng thái
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => {
                    detailRequest.current++;
                    setDetailModalOpen(false);
                  }}
                >
                  Đóng
                </Button>
              </div>
            </div>
          ) : null
        }
      >
        {detailLoading && <p role="status" style={{ padding: '20px 0', textAlign: 'center', color: 'var(--text-muted)' }}>Đang tải chi tiết đơn hàng…</p>}
        {detailError && (
          <div role="alert" style={{ padding: '12px', background: 'var(--color-danger-50)', border: '1px solid var(--color-danger-200)', borderRadius: '4px' }}>
            <p style={{ color: 'var(--color-danger-700)', marginBottom: '8px' }}>{detailError}</p>
            <Button size="sm" onClick={() => handleOpenDetail(selectedOrder)}>Thử lại</Button>
          </div>
        )}
        {selectedOrder && !detailLoading && !detailError && (() => {
          const orderItems = selectedOrder.items || [];
          const spaVisitItems = selectedOrder.spaVisitItems || [];
          const hasAnyItems = orderItems.length > 0 || spaVisitItems.length > 0;
          const orderType = getOrderType(selectedOrder);
          const isProductOrder = orderType === 'PRODUCT';
          const isSpaPackageOrder = orderType === 'SPA_PACKAGE';
          const isSpaVisitOrder = orderType === 'SPA_VISIT';
          const isSpaOrder = !isProductOrder;

          const renderRow = (label, value, isLast = false) => (
            <div
              key={label}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                padding: '7px 0',
                borderBottom: isLast ? 'none' : '1px solid #F1F5F9',
                fontSize: '12.5px',
                lineHeight: '1.45',
                gap: '12px',
              }}
            >
              <span
                style={{
                  width: '135px',
                  minWidth: '135px',
                  color: '#64748B',
                  fontWeight: 500,
                  flexShrink: 0,
                }}
              >
                {label}
              </span>
              <span
                style={{
                  flex: 1,
                  color: '#1E293B',
                  fontWeight: 500,
                  wordBreak: 'break-word',
                }}
              >
                {value}
              </span>
            </div>
          );

          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', fontSize: '13px' }}>
              {/* Phân loại & Trạng thái đơn hàng */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  background: '#F8FAFC',
                  border: '1px solid var(--border-subtle)',
                  gap: '12px',
                  flexWrap: 'wrap',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '12px', color: '#6B7280', fontWeight: 500 }}>Loại đơn hàng:</span>
                  <Badge variant={getOrderTypeBadge(selectedOrder).variant} size="sm">
                    {getOrderTypeBadge(selectedOrder).label}
                  </Badge>
                  {isSpaVisitOrder && selectedOrder.appointmentId && (
                    <span style={{ fontSize: '12px', color: '#6B7280' }}>
                      (Mã lịch hẹn: LH-{selectedOrder.appointmentId})
                    </span>
                  )}
                </div>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <span style={{ fontSize: '12px', color: '#6B7280', fontWeight: 500 }}>Trạng thái:</span>
                  <Badge variant={getOrderStatusBadge(selectedOrder.status).variant} size="sm">
                    {getOrderStatusBadge(selectedOrder.status).label}
                  </Badge>
                  <Badge variant={getPaymentStatusBadge(selectedOrder.paymentStatus).variant} size="sm">
                    {getPaymentStatusBadge(selectedOrder.paymentStatus).label}
                  </Badge>
                </div>
              </div>

              {/* Thông tin khách hàng & Thông tin đơn hàng */}
              <div className="grid-2" style={{ gap: '14px' }}>
                {/* Thông tin khách hàng / Người đặt */}
                <div
                  style={{
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    background: '#FFFFFF',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      padding: '9px 14px',
                      background: '#F8FAFC',
                      borderBottom: '1px solid var(--border-subtle)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontSize: '12px',
                      fontWeight: 700,
                      color: '#334155',
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                    }}
                  >
                    <User size={14} style={{ color: 'var(--color-primary-600, #4F46E5)' }} />
                    <span>Thông tin người đặt</span>
                  </div>
                  <div style={{ padding: '6px 14px' }}>
                    {renderRow('Khách hàng:', <strong>{selectedOrder.customerName || 'Khách vãng lai'}</strong>)}
                    {renderRow('Số điện thoại:', selectedOrder.customerPhone || '—')}
                    {selectedOrder.customerEmail && renderRow('Email:', selectedOrder.customerEmail)}
                    {renderRow(
                      isProductOrder ? 'Địa chỉ nhận:' : 'Địa điểm phục vụ:',
                      isProductOrder ? (selectedOrder.shippingAddress || '—') : 'Cơ sở Spa & Clinic BeautyShop'
                    )}
                    {selectedOrder.notes && renderRow('Ghi chú:', <span style={{ fontStyle: 'italic', color: '#475569' }}>{selectedOrder.notes}</span>, true)}
                  </div>
                </div>

                {/* Thông tin đơn hàng & Giao dịch */}
                <div
                  style={{
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    background: '#FFFFFF',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      padding: '9px 14px',
                      background: '#F8FAFC',
                      borderBottom: '1px solid var(--border-subtle)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontSize: '12px',
                      fontWeight: 700,
                      color: '#334155',
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                    }}
                  >
                    <FileText size={14} style={{ color: 'var(--color-primary-600, #4F46E5)' }} />
                    <span>Thông tin đơn hàng</span>
                  </div>
                  <div style={{ padding: '6px 14px' }}>
                    {renderRow(
                      'Trạng thái đơn:',
                      <Badge variant={getOrderStatusBadge(selectedOrder.status).variant}>
                        {getOrderStatusBadge(selectedOrder.status).text}
                      </Badge>
                    )}
                    {renderRow(
                      'Thanh toán:',
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                        <Badge variant={getPaymentStatusBadge(selectedOrder.paymentStatus).variant}>
                          {getPaymentStatusBadge(selectedOrder.paymentStatus).text}
                        </Badge>
                        <span style={{ color: '#475569' }}>· {getPaymentMethodLabel(selectedOrder.paymentMethod)}</span>
                        {selectedOrder.paymentStatus !== 'PAID' && selectedOrder.status !== 'CANCELLED' && (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleConfirmPayment(selectedOrder)}
                            style={{ fontSize: '11px', padding: '2px 8px', height: '24px' }}
                          >
                            Xác nhận đã thanh toán
                          </Button>
                        )}
                      </div>
                    )}
                    {selectedOrder.createdAt && renderRow('Thời gian tạo:', formatDateTime(selectedOrder.createdAt))}
                    {isProductOrder && (
                      <>
                        {renderRow('Đơn vị vận chuyển:', selectedOrder.carrierName || 'Chưa chỉ định')}
                        {renderRow('Mã vận đơn:', selectedOrder.trackingCode || 'Chưa tạo vận đơn')}
                      </>
                    )}
                    {isSpaPackageOrder && selectedOrder.servicePackageId && (
                      renderRow('Mã gói liệu trình:', `#${selectedOrder.servicePackageId}`)
                    )}
                    {isSpaVisitOrder && selectedOrder.appointmentId && (
                      renderRow('Mã lịch hẹn Spa:', `LH-#${selectedOrder.appointmentId}`)
                    )}
                    {selectedOrder.paidAt && renderRow('Thời gian TT:', formatDateTime(selectedOrder.paidAt))}
                    {selectedOrder.cancelReason && renderRow(
                      'Lý do hủy:',
                      <span style={{ color: '#DC2626', fontWeight: 600 }}>{selectedOrder.cancelReason}</span>,
                      true
                    )}
                  </div>
                </div>
              </div>

              {/* Danh sách sản phẩm / Dịch vụ */}
              <div
                style={{
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  overflow: 'hidden',
                  background: '#FFFFFF',
                }}
              >
                <div
                  style={{
                    padding: '10px 14px',
                    background: '#F8FAFC',
                    borderBottom: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontSize: '12px',
                      fontWeight: 700,
                      color: '#334155',
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                    }}
                  >
                    <ShoppingBag size={14} style={{ color: 'var(--color-primary-600, #4F46E5)' }} />
                    <span>Danh sách sản phẩm / dịch vụ</span>
                  </div>
                  <span style={{ fontSize: '12px', color: '#64748B', fontWeight: 500 }}>
                    {orderItems.length + spaVisitItems.length || (hasAnyItems ? 1 : 0)} mục
                  </span>
                </div>
                <div className="table-wrapper" style={{ margin: 0, overflowX: 'auto' }}>
                  <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr style={{ background: '#F8FAFC' }}>
                        <th style={{ width: '56px', textAlign: 'center' }}>Ảnh</th>
                        <th style={{ textAlign: 'left' }}>Tên sản phẩm / Dịch vụ</th>
                        <th style={{ textAlign: 'right', width: '120px' }}>Đơn giá</th>
                        <th style={{ textAlign: 'center', width: '80px' }}>Số lượng</th>
                        <th style={{ textAlign: 'right', width: '130px' }}>Thành tiền</th>
                      </tr>
                    </thead>
                    <tbody>
                      {orderItems.map((item, idx) => {
                        const unitPrice = Number(item.price || 0);
                        const discount = Number(item.discount || 0);
                        const effectivePrice = Math.max(0, unitPrice - discount);
                        const lineTotal = effectivePrice * Number(item.quantity || 1);
                        const resolvedImg = item.imageUrl ? resolveMediaUrl(item.imageUrl) : '';

                        return (
                          <tr key={`item-${idx}`}>
                            <td style={{ width: '56px', textAlign: 'center', verticalAlign: 'middle', padding: '8px' }}>
                              {resolvedImg ? (
                                <img
                                  src={resolvedImg}
                                  alt={item.productName || 'Sản phẩm'}
                                  style={{
                                    width: '40px',
                                    height: '40px',
                                    objectFit: 'cover',
                                    borderRadius: '4px',
                                    border: '1px solid #E5E7EB',
                                    display: 'block',
                                    margin: '0 auto',
                                  }}
                                  onError={(e) => {
                                    e.currentTarget.style.display = 'none';
                                    if (e.currentTarget.nextSibling) {
                                      e.currentTarget.nextSibling.style.display = 'flex';
                                    }
                                  }}
                                />
                              ) : null}
                              <div
                                style={{
                                  width: '40px',
                                  height: '40px',
                                  borderRadius: '4px',
                                  background: '#F3F4F6',
                                  border: '1px solid #E5E7EB',
                                  display: resolvedImg ? 'none' : 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  margin: '0 auto',
                                  color: '#9CA3AF',
                                }}
                              >
                                <Package size={18} />
                              </div>
                            </td>
                            <td>
                              <div style={{ fontWeight: 600, color: '#1E293B' }}>{item.productName}</div>
                              {item.variantName && (
                                <div style={{ fontSize: '11px', color: '#6B7280', marginTop: '2px' }}>
                                  Phân loại: <span>{item.variantName}</span>
                                </div>
                              )}
                              {item.sku && (
                                <div style={{ fontSize: '11px', color: '#9CA3AF', marginTop: '1px' }}>
                                  SKU: <span>{item.sku}</span>
                                </div>
                              )}
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              {discount > 0 ? (
                                <div>
                                  <div style={{ fontWeight: 600, color: '#1E293B' }}>{formatCurrency(effectivePrice)}</div>
                                  <div style={{ fontSize: '11px', color: '#9CA3AF', textDecoration: 'line-through' }}>
                                    {formatCurrency(unitPrice)}
                                  </div>
                                </div>
                              ) : (
                                <span style={{ fontWeight: 600, color: '#1E293B' }}>{formatCurrency(effectivePrice)}</span>
                              )}
                            </td>
                            <td style={{ textAlign: 'center', fontWeight: 600, color: '#1E293B' }}>{item.quantity}</td>
                            <td style={{ textAlign: 'right', fontWeight: 700, color: '#1E293B' }}>{formatCurrency(lineTotal)}</td>
                          </tr>
                        );
                      })}

                      {spaVisitItems.map((sv, idx) => (
                        <tr key={`spa-${idx}`}>
                          <td style={{ width: '56px', textAlign: 'center', verticalAlign: 'middle', padding: '8px' }}>
                            <div
                              style={{
                                width: '40px',
                                height: '40px',
                                borderRadius: '4px',
                                background: '#F3F4F6',
                                border: '1px solid #E5E7EB',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                margin: '0 auto',
                                color: '#6B7280',
                              }}
                            >
                              <Sparkles size={18} />
                            </div>
                          </td>
                          <td>
                            <div style={{ fontWeight: 600, color: '#1E293B' }}>{sv.serviceName}</div>
                            <div style={{ fontSize: '11px', color: '#6B7280', marginTop: '2px' }}>
                              Dịch vụ Spa thực hiện tại cơ sở
                            </div>
                          </td>
                          <td style={{ textAlign: 'right', fontWeight: 600, color: '#1E293B' }}>{formatCurrency(sv.price)}</td>
                          <td style={{ textAlign: 'center', fontWeight: 600, color: '#1E293B' }}>{sv.performedSessions ?? 1}</td>
                          <td style={{ textAlign: 'right', fontWeight: 700, color: '#1E293B' }}>
                            {formatCurrency((sv.price || 0) * (sv.performedSessions || 1))}
                          </td>
                        </tr>
                      ))}

                      {!hasAnyItems && isSpaOrder && (
                        <tr>
                          <td style={{ width: '56px', textAlign: 'center', verticalAlign: 'middle', padding: '8px' }}>
                            <div
                              style={{
                                width: '40px',
                                height: '40px',
                                borderRadius: '4px',
                                background: '#F3F4F6',
                                border: '1px solid #E5E7EB',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                margin: '0 auto',
                                color: '#6B7280',
                              }}
                            >
                              <Sparkles size={18} />
                            </div>
                          </td>
                          <td>
                            <div style={{ fontWeight: 600, color: '#1E293B' }}>
                              {selectedOrder.notes || (selectedOrder.servicePackageId ? `Gói dịch vụ / liệu trình Spa #${selectedOrder.servicePackageId}` : 'Dịch vụ Spa tại viện')}
                            </div>
                            <div style={{ fontSize: '11px', color: '#6B7280', marginTop: '2px' }}>
                              {selectedOrder.spaBenefitType === 'TREATMENT_COURSE'
                                ? 'Liệu trình chăm sóc chuyên sâu'
                                : selectedOrder.servicePackageId
                                ? 'Gói dịch vụ Spa'
                                : 'Dịch vụ Spa tại cơ sở'}
                            </div>
                          </td>
                          <td style={{ textAlign: 'right', fontWeight: 600, color: '#1E293B' }}>
                            {formatCurrency(selectedOrder.subTotal || selectedOrder.totalAmount || 0)}
                          </td>
                          <td style={{ textAlign: 'center', fontWeight: 600, color: '#1E293B' }}>1</td>
                          <td style={{ textAlign: 'right', fontWeight: 700, color: '#1E293B' }}>
                            {formatCurrency(selectedOrder.totalAmount || selectedOrder.subTotal || 0)}
                          </td>
                        </tr>
                      )}

                      {!hasAnyItems && !isSpaOrder && (
                        <tr>
                          <td colSpan={5} style={{ textAlign: 'center', padding: '24px', color: '#9CA3AF' }}>
                            Không có sản phẩm nào trong đơn hàng
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Tổng kết thanh toán */}
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <div
                  style={{
                    width: '100%',
                    maxWidth: '400px',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    background: '#FFFFFF',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      padding: '9px 14px',
                      background: '#F8FAFC',
                      borderBottom: '1px solid var(--border-subtle)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontSize: '12px',
                      fontWeight: 700,
                      color: '#334155',
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                    }}
                  >
                    <Receipt size={14} style={{ color: 'var(--color-primary-600, #4F46E5)' }} />
                    <span>Tổng kết thanh toán</span>
                  </div>
                  <div style={{ padding: '6px 14px' }}>
                    {selectedOrder.subTotal != null && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #F1F5F9', fontSize: '12.5px' }}>
                        <span style={{ color: '#64748B' }}>Tạm tính:</span>
                        <span style={{ fontWeight: 500, color: '#1E293B' }}>{formatCurrency(selectedOrder.subTotal)}</span>
                      </div>
                    )}
                    {selectedOrder.discountAmount != null && Number(selectedOrder.discountAmount) > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #F1F5F9', fontSize: '12.5px', color: '#059669' }}>
                        <span>Giảm giá / Voucher:</span>
                        <span style={{ fontWeight: 600 }}>-{formatCurrency(selectedOrder.discountAmount)}</span>
                      </div>
                    )}
                    {selectedOrder.shippingFee != null && Number(selectedOrder.shippingFee) > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #F1F5F9', fontSize: '12.5px' }}>
                        <span style={{ color: '#64748B' }}>Phí vận chuyển:</span>
                        <span style={{ fontWeight: 500, color: '#1E293B' }}>{formatCurrency(selectedOrder.shippingFee)}</span>
                      </div>
                    )}
                    <div style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      padding: '10px 0',
                      borderBottom: '1px solid #E2E8F0',
                      fontSize: '13.5px',
                      fontWeight: 700,
                      color: 'var(--color-primary-800, #312E81)',
                    }}>
                      <span>Tổng cộng:</span>
                      <span style={{ fontSize: '15px' }}>{formatCurrency(selectedOrder.totalAmount)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #F1F5F9', fontSize: '12.5px', color: '#059669' }}>
                      <span>Đã thanh toán:</span>
                      <span style={{ fontWeight: 600 }}>{formatCurrency(selectedOrder.paidAmount || 0)}</span>
                    </div>
                    {selectedOrder.refundedAmount != null && Number(selectedOrder.refundedAmount) > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #F1F5F9', fontSize: '12.5px', color: '#DC2626' }}>
                        <span>Đã hoàn tiền:</span>
                        <span style={{ fontWeight: 600 }}>-{formatCurrency(selectedOrder.refundedAmount)}</span>
                      </div>
                    )}
                    {(() => {
                      const remaining = Math.max(0, Number(selectedOrder.totalAmount || 0) - Number(selectedOrder.paidAmount || 0));
                      return (
                        <div style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          padding: '8px 0 4px',
                          fontSize: '12.5px',
                          fontWeight: 600,
                          color: remaining > 0 ? '#D97706' : '#059669',
                        }}>
                          <span>{remaining > 0 ? 'Còn cần thu:' : 'Tất toán:'}</span>
                          <span>{remaining > 0 ? formatCurrency(remaining) : 'Đã thanh toán đủ'}</span>
                        </div>
                      );
                    })()}
                  </div>
                </div>
              </div>
            </div>
          );
        })()}
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
                Đơn dịch vụ Spa (thực hiện tại cơ sở hoặc kích hoạt vé điện tử), không qua khâu vận chuyển hàng hóa.
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
