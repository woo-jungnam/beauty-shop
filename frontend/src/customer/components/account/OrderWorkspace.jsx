import React, { useCallback, useEffect, useRef, useState, useMemo } from 'react';
import {
  Package,
  Truck,
  CreditCard,
  Clock,
  Calendar,
  CheckCircle2,
  AlertCircle,
  ExternalLink,
  RotateCcw,
  RefreshCw,
  Search,
  ShoppingBag,
  MapPin,
  ChevronRight,
  FileText,
  DollarSign,
  User,
  Phone,
  Tag,
  ArrowRight,
  Info,
  ShieldCheck,
  XCircle,
  Printer,
  Sparkles,
  Star
} from 'lucide-react';
import { apiClient } from '../../../shared/api/client';
import { useAuth } from '../../../app/providers/AuthProvider';
import { Modal } from '../../../shared/ui/Modal';
import { getGuestSessionId, useCustomerCart } from '../../stores/customerCartStore';
import { Pagination, listRows, unwrap } from './AccountTools';
import {
  resolveMediaUrl,
  getOrderTypeBadge,
  getOrderType,
  getOrderStatusBadge,
  getPaymentStatusBadge,
  getPaymentMethodLabel,
  formatCurrency,
  formatDateTime
} from '../../../shared/utils/formatters';
import { ServiceBookingOrders } from '../../../features/spa/ServiceBookingOrders';
import { PRODUCT_PLACEHOLDER, resolveProductImage } from '../product/ProductCard';
import { orderBenefitDestination } from '../../../features/spa/benefitModel';

export const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(value || 0));

const statuses = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  RETURNED: 'Đã trả hàng',
  COMPLETED: 'Hoàn tất',
  DELIVERY_FAILED: 'Giao không thành công',
  RETURN_TO_SENDER: 'Hàng chuyển về shop',
  REQUESTED: 'Chờ xét duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  RECEIVED: 'Đã nhận hàng trả',
  REFUNDED: 'Đã hoàn tiền',
  REFUND_PENDING: 'Chờ hoàn tiền',
  PAID: 'Đã thanh toán'
};

const getStatusBadgeStyle = (status) => {
  switch (status) {
    case 'DELIVERED':
    case 'COMPLETED':
      return { bg: '#ECFDF5', color: '#047857', border: '#A7F3D0' };
    case 'SHIPPED':
      return { bg: '#EFF6FF', color: '#1D4ED8', border: '#BFDBFE' };
    case 'CONFIRMED':
    case 'PROCESSING':
      return { bg: '#F0FDF4', color: '#15803D', border: '#BBF7D0' };
    case 'PENDING':
      return { bg: '#FFFBEB', color: '#B45309', border: '#FDE68A' };
    case 'CANCELLED':
    case 'RETURNED':
    case 'DELIVERY_FAILED':
    case 'RETURN_TO_SENDER':
      return { bg: '#FEF2F2', color: '#B91C1C', border: '#FECACA' };
    case 'REQUESTED':
      return { bg: '#FFF7ED', color: '#C2410C', border: '#FFEDD5' };
    case 'APPROVED':
    case 'RECEIVED':
      return { bg: '#EFF6FF', color: '#1D4ED8', border: '#BFDBFE' };
    case 'REFUNDED':
      return { bg: '#F5F3FF', color: '#6D28D9', border: '#DDD6FE' };
    default:
      return { bg: '#F3F4F6', color: '#374151', border: '#E5E7EB' };
  }
};

const getPaymentBadgeStyle = (paymentStatus) => {
  switch (paymentStatus) {
    case 'PAID':
      return { bg: '#ECFDF5', color: '#047857', border: '#A7F3D0' };
    case 'PENDING':
    case 'UNPAID':
      return { bg: '#FFFBEB', color: '#B45309', border: '#FDE68A' };
    case 'PARTIALLY_PAID':
      return { bg: '#EFF6FF', color: '#1D4ED8', border: '#BFDBFE' };
    case 'OVERDUE':
    case 'FAILED':
      return { bg: '#FEF2F2', color: '#B91C1C', border: '#FECACA' };
    case 'REFUND_PENDING':
      return { bg: '#FFFBEB', color: '#B45309', border: '#FDE68A' };
    case 'REFUNDED':
      return { bg: '#F5F3FF', color: '#6D28D9', border: '#DDD6FE' };
    default:
      return { bg: '#F3F4F6', color: '#374151', border: '#E5E7EB' };
  }
};

const PRODUCT_FILTER_TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xác nhận', statuses: ['PENDING'] },
  { key: 'PROCESSING', label: 'Đang xử lý / Vận chuyển', statuses: ['CONFIRMED', 'PROCESSING', 'SHIPPED'] },
  { key: 'COMPLETED', label: 'Đã nhận hàng', statuses: ['DELIVERED', 'COMPLETED'] },
  { key: 'CANCELLED', label: 'Đã hủy / Đổi trả', statuses: ['CANCELLED', 'RETURNED', 'DELIVERY_FAILED', 'RETURN_TO_SENDER'] }
];

const SERVICE_FILTER_TABS = [
  { key: 'ALL', label: 'Tất cả dịch vụ' },
  { key: 'SPA_VISIT', label: 'Hóa đơn buổi tại quầy' },
  { key: 'SPA_PACKAGE', label: 'Gói liệu trình & Lộ trình' },
  { key: 'UNINVOICED', label: 'Buổi hẹn chưa lập hóa đơn' }
];

export function OrderWorkspace({ onNavigate, initialOrderId, onOrderChanged }) {
  const { fetchCart, setCartOpen } = useCustomerCart();
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [] });
  const [selected, setSelected] = useState(initialOrderId || null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [scopeTab, setScopeTab] = useState('PRODUCTS'); // 'PRODUCTS' | 'SERVICES'
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [serviceSubFilter, setServiceSubFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [reorderingId, setReorderingId] = useState(null);
  const loadRequest = useRef(null);

  const load = useCallback(async () => {
    loadRequest.current?.abort();
    const controller = new AbortController();
    loadRequest.current = controller;
    setLoading(true);
    try {
      const response = await apiClient.get(`/api/v1/orders/my-orders?page=${page}&size=50&sort=createdAt,desc`, { signal: controller.signal });
      if (controller.signal.aborted) return;
      setData(unwrap(response));
      setError('');
    } catch (e) {
      if (!controller.signal.aborted) setError(e.message || 'Không thể tải danh sách đơn hàng.');
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    load();
    return () => loadRequest.current?.abort();
  }, [load]);

  const allOrders = listRows(data);

  useEffect(() => {
    if (!initialOrderId) return;
    let active = true;
    const match = allOrders.find(o => String(o.id) === String(initialOrderId));
    if (match) {
      setSelected(initialOrderId);
      setScopeTab(getOrderType(match) !== 'PRODUCT' ? 'SERVICES' : 'PRODUCTS');
    } else {
      setSelected(initialOrderId);
      apiClient.get(`/api/v1/orders/${initialOrderId}`, { headers: { 'X-Guest-Session-Id': getGuestSessionId() } })
        .then(res => {
          if (!active) return;
          const ord = unwrap(res);
          if (ord && getOrderType(ord) !== 'PRODUCT') {
            setScopeTab('SERVICES');
          }
        })
        .catch(() => {});
    }
    return () => { active = false; };
  }, [initialOrderId, allOrders]);

  const productOrders = useMemo(() => allOrders.filter(o => getOrderType(o) === 'PRODUCT'), [allOrders]);
  const serviceOrders = useMemo(() => allOrders.filter(o => getOrderType(o) !== 'PRODUCT'), [allOrders]);

  // Quick reorder handler directly from order card
  const handleQuickReorder = async (order) => {
    if (!order.items || order.items.length === 0) return;
    setReorderingId(order.id);
    try {
      let added = 0;
      for (const item of order.items) {
        if (!item.variantId) continue;
        try {
          await apiClient.post('/api/v1/cart/add', {
            variantId: item.variantId,
            quantity: item.quantity || 1,
            sessionId: getGuestSessionId()
          });
          added++;
        } catch (err) {
          console.warn(`Could not add item ${item.productName}:`, err);
        }
      }
      if (added > 0) {
        await fetchCart();
        setCartOpen(true);
      }
    } catch (err) {
      console.error('Reorder error:', err);
    } finally {
      setReorderingId(null);
    }
  };

  // Filter product orders
  const filteredProductOrders = useMemo(() => {
    return productOrders.filter(order => {
      if (statusFilter !== 'ALL') {
        const tab = PRODUCT_FILTER_TABS.find(t => t.key === statusFilter);
        if (tab && !tab.statuses.includes(order.status)) {
          return false;
        }
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchCode = (order.orderNumber || '').toLowerCase().includes(q);
        const matchCustomer = (order.customerName || '').toLowerCase().includes(q);
        const matchPhone = (order.customerPhone || '').includes(q);
        const matchCarrier = (order.carrierName || '').toLowerCase().includes(q) || (order.trackingCode || '').toLowerCase().includes(q);
        const matchItems = (order.items || []).some(item =>
          (item.productName || '').toLowerCase().includes(q) ||
          (item.variantName || '').toLowerCase().includes(q) ||
          (item.sku || '').toLowerCase().includes(q)
        );

        if (!matchCode && !matchCustomer && !matchPhone && !matchCarrier && !matchItems) {
          return false;
        }
      }

      return true;
    });
  }, [productOrders, statusFilter, searchQuery]);

  // Filter service orders
  const filteredServiceOrders = useMemo(() => {
    return serviceOrders.filter(order => {
      const type = getOrderType(order);
      if (serviceSubFilter === 'SPA_VISIT' && type !== 'SPA_VISIT') return false;
      if (serviceSubFilter === 'SPA_PACKAGE' && type !== 'SPA_PACKAGE') return false;

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchCode = (order.orderNumber || '').toLowerCase().includes(q);
        const matchCustomer = (order.customerName || '').toLowerCase().includes(q);
        const matchPhone = (order.customerPhone || '').includes(q);
        const matchServices = (order.spaVisitItems || []).some(s =>
          (s.serviceName || '').toLowerCase().includes(q)
        );
        const matchPackageId = order.servicePackageId && String(order.servicePackageId).includes(q);
        const matchAppointmentId = order.appointmentId && String(order.appointmentId).includes(q);

        if (!matchCode && !matchCustomer && !matchPhone && !matchServices && !matchPackageId && !matchAppointmentId) {
          return false;
        }
      }

      return true;
    });
  }, [serviceOrders, serviceSubFilter, searchQuery]);

  return (
    <div className="account-stack" style={{ gap: 20 }}>
      {/* Top Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: 'var(--c-primary, #721B29)' }}>
            {scopeTab === 'PRODUCTS' ? 'Lịch sử đơn hàng mua sắm' : 'Lịch sử dịch vụ Spa & Liệu trình'}
          </h3>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: '#6B7280' }}>
            {scopeTab === 'PRODUCTS'
              ? 'Theo dõi chi tiết các sản phẩm mỹ phẩm đã mua, lộ trình vận chuyển và thông tin thanh toán.'
              : 'Theo dõi hóa đơn các buổi chăm sóc da tại cơ sở, gói dịch vụ Spa và lộ trình điều trị.'}
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="btn-luxury-outline"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            padding: '8px 16px',
            fontSize: 13,
            cursor: loading ? 'not-allowed' : 'pointer'
          }}
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Làm mới
        </button>
      </div>

      {error && (
        <div style={{ padding: '12px 16px', backgroundColor: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, color: '#991B1B', fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }} role="alert">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      {/* Main Scope Switcher Tabs */}
      <div style={{ display: 'flex', gap: 8, borderBottom: '2px solid #E5E7EB', paddingBottom: 0 }}>
        <button
          type="button"
          onClick={() => { setScopeTab('PRODUCTS'); setStatusFilter('ALL'); setSearchQuery(''); }}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '12px 20px',
            fontSize: 14,
            fontWeight: 700,
            border: 'none',
            borderBottom: scopeTab === 'PRODUCTS' ? '3px solid var(--c-primary, #721B29)' : '3px solid transparent',
            backgroundColor: 'transparent',
            color: scopeTab === 'PRODUCTS' ? 'var(--c-primary, #721B29)' : '#6B7280',
            cursor: 'pointer',
            transition: 'all 0.15s ease',
            marginBottom: -2
          }}
        >
          <ShoppingBag size={17} />
          <span>Đơn hàng mua sắm</span>
          <span style={{ fontSize: 12, padding: '2px 8px', borderRadius: 10, backgroundColor: scopeTab === 'PRODUCTS' ? 'var(--c-primary, #721B29)' : '#F3F4F6', color: scopeTab === 'PRODUCTS' ? '#FFFFFF' : '#4B5563' }}>
            {productOrders.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => { setScopeTab('SERVICES'); setServiceSubFilter('ALL'); setSearchQuery(''); }}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '12px 20px',
            fontSize: 14,
            fontWeight: 700,
            border: 'none',
            borderBottom: scopeTab === 'SERVICES' ? '3px solid var(--c-primary, #721B29)' : '3px solid transparent',
            backgroundColor: 'transparent',
            color: scopeTab === 'SERVICES' ? 'var(--c-primary, #721B29)' : '#6B7280',
            cursor: 'pointer',
            transition: 'all 0.15s ease',
            marginBottom: -2
          }}
        >
          <Sparkles size={17} />
          <span>Lịch sử dịch vụ Spa</span>
          <span style={{ fontSize: 12, padding: '2px 8px', borderRadius: 10, backgroundColor: scopeTab === 'SERVICES' ? 'var(--c-primary, #721B29)' : '#F3F4F6', color: scopeTab === 'SERVICES' ? '#FFFFFF' : '#4B5563' }}>
            {serviceOrders.length}
          </span>
        </button>
      </div>

      {/* Scope Sub-Filters & Search */}
      {scopeTab === 'PRODUCTS' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, backgroundColor: '#FFFFFF', padding: '14px 18px', borderRadius: 10, border: '1px solid #E5E7EB', boxShadow: '0 1px 2px rgba(0,0,0,0.03)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflowX: 'auto', paddingBottom: 4 }}>
            {PRODUCT_FILTER_TABS.map(tab => {
              const isActive = statusFilter === tab.key;
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setStatusFilter(tab.key)}
                  style={{
                    padding: '7px 14px',
                    borderRadius: 20,
                    fontSize: 13,
                    fontWeight: isActive ? 600 : 500,
                    border: isActive ? '1px solid var(--c-primary, #721B29)' : '1px solid #E5E7EB',
                    backgroundColor: isActive ? 'var(--c-primary, #721B29)' : '#F9FAFB',
                    color: isActive ? '#FFFFFF' : '#4B5563',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 10, position: 'relative' }}>
            <Search size={16} style={{ position: 'absolute', left: 12, color: '#9CA3AF' }} />
            <input
              type="text"
              placeholder="Tìm theo mã đơn hàng, tên sản phẩm, phân loại, SKU hoặc số điện thoại..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={{
                width: '100%',
                padding: '9px 12px 9px 36px',
                border: '1px solid #D1D5DB',
                borderRadius: 8,
                fontSize: 13,
                backgroundColor: '#FAFAFA'
              }}
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                style={{
                  position: 'absolute',
                  right: 10,
                  border: 0,
                  background: 'none',
                  color: '#9CA3AF',
                  cursor: 'pointer',
                  fontSize: 13,
                  padding: '4px 8px'
                }}
              >
                Hủy
              </button>
            )}
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, backgroundColor: '#FFFFFF', padding: '14px 18px', borderRadius: 10, border: '1px solid #E5E7EB', boxShadow: '0 1px 2px rgba(0,0,0,0.03)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflowX: 'auto', paddingBottom: 4 }}>
            {SERVICE_FILTER_TABS.map(tab => {
              const isActive = serviceSubFilter === tab.key;
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setServiceSubFilter(tab.key)}
                  style={{
                    padding: '7px 14px',
                    borderRadius: 20,
                    fontSize: 13,
                    fontWeight: isActive ? 600 : 500,
                    border: isActive ? '1px solid var(--c-primary, #721B29)' : '1px solid #E5E7EB',
                    backgroundColor: isActive ? 'var(--c-primary, #721B29)' : '#F9FAFB',
                    color: isActive ? '#FFFFFF' : '#4B5563',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          {serviceSubFilter !== 'UNINVOICED' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, position: 'relative' }}>
              <Search size={16} style={{ position: 'absolute', left: 12, color: '#9CA3AF' }} />
              <input
                type="text"
                placeholder="Tìm theo mã đơn, mã gói, tên dịch vụ hoặc mã buổi hẹn..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
                  border: '1px solid #D1D5DB',
                  borderRadius: 8,
                  fontSize: 13,
                  backgroundColor: '#FAFAFA'
                }}
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  style={{
                    position: 'absolute',
                    right: 10,
                    border: 0,
                    background: 'none',
                    color: '#9CA3AF',
                    cursor: 'pointer',
                    fontSize: 13,
                    padding: '4px 8px'
                  }}
                >
                  Hủy
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* Scope Content Render */}
      {scopeTab === 'PRODUCTS' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {filteredProductOrders.map(order => {
            const statusStyle = getStatusBadgeStyle(order.status);
            const paymentStyle = getPaymentBadgeStyle(order.paymentStatus);
            const totalItemsCount = (order.items || []).reduce((acc, item) => acc + (item.quantity || 1), 0);

            return (
              <article
                className="account-card"
                key={order.id}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 14,
                  padding: '18px 20px',
                  border: '1px solid #E5E7EB',
                  borderRadius: 12,
                  backgroundColor: '#FFFFFF',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #F3F4F6', paddingBottom: 12, flexWrap: 'wrap', gap: 10 }}>
                  <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                    <span style={{ fontSize: 11, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Mã đơn:</span>
                    <strong style={{ fontFamily: 'monospace', fontSize: 15, color: 'var(--c-primary, #721B29)' }}>
                      {order.orderNumber}
                    </strong>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#9CA3AF', marginLeft: 4 }}>
                      <Clock size={13} />
                      {order.createdAt && formatDateTime(order.createdAt)}
                    </span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 12, fontWeight: 600, padding: '3px 10px', borderRadius: 12, backgroundColor: statusStyle.bg, color: statusStyle.color, border: `1px solid ${statusStyle.border}` }}>
                      {statuses[order.status] || order.status}
                    </span>
                    <span style={{ fontSize: 12, fontWeight: 600, padding: '3px 10px', borderRadius: 12, backgroundColor: paymentStyle.bg, color: paymentStyle.color, border: `1px solid ${paymentStyle.border}` }}>
                      {getPaymentStatusBadge(order.paymentStatus).text}
                    </span>
                  </div>
                </div>

                {/* Product items list */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '4px 0' }}>
                  {(order.items || []).map((item, idx) => {
                    const unitPrice = Number(item.price || 0);
                    const discount = Number(item.discount || 0);
                    const effective = Math.max(0, unitPrice - discount);
                    const lineTotal = effective * Number(item.quantity || 1);
                    const productLink = item.productId ? `#/product/${item.productId}` : null;

                    return (
                      <div
                        key={item.id || idx}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: 14,
                          padding: '10px 12px',
                          backgroundColor: '#FAFAFA',
                          borderRadius: 8,
                          border: '1px solid #F3F4F6'
                        }}
                      >
                        <div
                          style={{
                            width: 64,
                            height: 64,
                            borderRadius: 8,
                            overflow: 'hidden',
                            backgroundColor: '#FFFFFF',
                            border: '1px solid #E5E7EB',
                            flexShrink: 0,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                          }}
                        >
                          <img
                            src={resolveProductImage(item)}
                            alt={item.productName || 'Sản phẩm'}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                            onError={e => {
                              if (e.currentTarget.src !== PRODUCT_PLACEHOLDER) {
                                e.currentTarget.src = PRODUCT_PLACEHOLDER;
                              }
                            }}
                          />
                        </div>

                        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
                          {productLink ? (
                            <a
                              href={productLink}
                              style={{
                                fontWeight: 600,
                                fontSize: 14,
                                color: 'var(--c-primary, #721B29)',
                                textDecoration: 'none',
                                lineHeight: 1.4,
                                display: '-webkit-box',
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: 'vertical',
                                overflow: 'hidden'
                              }}
                              title={item.productName}
                            >
                              {item.productName}
                            </a>
                          ) : (
                            <span
                              style={{
                                fontWeight: 600,
                                fontSize: 14,
                                color: '#1F2937',
                                lineHeight: 1.4,
                                display: '-webkit-box',
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: 'vertical',
                                overflow: 'hidden'
                              }}
                            >
                              {item.productName}
                            </span>
                          )}

                          <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8, fontSize: 12 }}>
                            {item.variantName && (
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '2px 8px', borderRadius: 4, backgroundColor: '#EFF6FF', color: '#1E40AF', fontSize: 11, fontWeight: 500 }}>
                                <Tag size={11} />
                                Phân loại: <strong>{item.variantName}</strong>
                              </span>
                            )}
                            {item.sku && (
                              <span style={{ fontSize: 11, color: '#6B7280' }}>
                                SKU: <code style={{ fontFamily: 'monospace', backgroundColor: '#FFFFFF', padding: '1px 5px', borderRadius: 3, border: '1px solid #E5E7EB' }}>{item.sku}</code>
                              </span>
                            )}
                          </div>

                          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, fontSize: 12, marginTop: 2 }}>
                            <span style={{ color: '#6B7280' }}>Đơn giá:</span>
                            {discount > 0 ? (
                              <>
                                <strong style={{ color: '#111827', fontWeight: 600 }}>{money(effective)}</strong>
                                <span style={{ color: '#9CA3AF', textDecoration: 'line-through', fontSize: 11 }}>{money(unitPrice)}</span>
                                <span style={{ color: '#059669', fontSize: 11, fontWeight: 500 }}>(-{money(discount)})</span>
                              </>
                            ) : (
                              <strong style={{ color: '#111827', fontWeight: 600 }}>{money(unitPrice)}</strong>
                            )}
                          </div>
                        </div>

                        <div style={{ textAlign: 'right', flexShrink: 0, minWidth: 90 }}>
                          <div style={{ fontSize: 12, color: '#6B7280' }}>
                            Số lượng: <strong style={{ color: '#111827', fontSize: 13 }}>x{item.quantity}</strong>
                          </div>
                          <div style={{ marginTop: 4, fontSize: 14, fontWeight: 700, color: 'var(--c-primary, #721B29)', fontFamily: 'monospace' }}>
                            {money(lineTotal)}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Shipping & Payment Bar */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
                    gap: 12,
                    padding: '12px 14px',
                    backgroundColor: '#F9FAFB',
                    borderRadius: 8,
                    border: '1px solid #F3F4F6',
                    fontSize: 12
                  }}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#374151' }}>
                      <MapPin size={14} style={{ color: 'var(--c-primary, #721B29)' }} />
                      <span>Địa chỉ nhận hàng</span>
                    </div>
                    <div style={{ color: '#1F2937', fontWeight: 500 }}>
                      {order.customerName} {order.customerPhone ? `· ${order.customerPhone}` : ''}
                    </div>
                    <div style={{ color: '#6B7280', lineHeight: 1.4 }}>
                      {order.shippingAddress || 'Chưa cập nhật địa chỉ'}
                    </div>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#374151' }}>
                      <Truck size={14} style={{ color: '#2563EB' }} />
                      <span>Vận chuyển</span>
                    </div>
                    {order.carrierName ? (
                      <div style={{ color: '#1F2937' }}>
                        ĐVVC: <strong>{order.carrierName}</strong>
                        {order.trackingCode && (
                          <div style={{ color: '#4B5563', marginTop: 2 }}>
                            Mã vận đơn: <code style={{ fontFamily: 'monospace', fontWeight: 700, backgroundColor: '#FFFFFF', padding: '1px 5px', borderRadius: 3, border: '1px solid #E5E7EB' }}>{order.trackingCode}</code>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div style={{ color: '#6B7280' }}>
                        {order.status === 'SHIPPED' || order.status === 'DELIVERED' ? 'Đang cập nhật thông tin vận chuyển' : 'Chuẩn bị đóng gói & vận chuyển'}
                      </div>
                    )}
                    {order.deliveredAt && (
                      <div style={{ color: '#059669', fontSize: 11, fontWeight: 500 }}>
                        Đã giao lúc: {formatDateTime(order.deliveredAt)}
                      </div>
                    )}
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#374151' }}>
                      <CreditCard size={14} style={{ color: '#059669' }} />
                      <span>Thanh toán</span>
                    </div>
                    <div style={{ color: '#1F2937', fontWeight: 500 }}>
                      {getPaymentMethodLabel(order.paymentMethod)}
                    </div>
                    <div style={{ color: paymentStyle.color, fontWeight: 600, fontSize: 11.5 }}>
                      {getPaymentStatusBadge(order.paymentStatus).text}
                      {order.paidAmount > 0 && ` (${money(order.paidAmount)})`}
                    </div>
                    {order.paymentDeadline && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (
                      <div style={{ color: '#B45309', fontSize: 11 }}>
                        Hạn TT: {formatDateTime(order.paymentDeadline)}
                      </div>
                    )}
                  </div>
                </div>

                {/* Card Footer */}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    paddingTop: 10,
                    borderTop: '1px solid #F3F4F6',
                    flexWrap: 'wrap',
                    gap: 12
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 13, color: '#6B7280' }}>
                      Tổng cộng: <strong style={{ color: '#111827' }}>{totalItemsCount}</strong> mặt hàng
                    </span>
                    <div style={{ fontSize: 14 }}>
                      Tổng tiền:{' '}
                      <strong style={{ color: 'var(--c-deal-red, #E11D48)', fontFamily: 'monospace', fontSize: 16 }}>
                        {money(order.totalAmount)}
                      </strong>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      disabled={reorderingId === order.id}
                      className="btn-luxury-outline"
                      style={{ padding: '6px 12px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
                      onClick={() => handleQuickReorder(order)}
                    >
                      <RotateCcw size={13} className={reorderingId === order.id ? 'animate-spin' : ''} />
                      Mua lại
                    </button>

                    {order.paymentMethod === 'BANK' && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (!order.paymentDeadline || Date.parse(order.paymentDeadline) > Date.now()) && (
                      <button
                        type="button"
                        className="btn-luxury-primary"
                        style={{ padding: '6px 14px', fontSize: 12, backgroundColor: '#D97706', borderColor: '#B45309', color: '#FFFFFF', fontWeight: 600 }}
                        onClick={() => onNavigate(`order-success?id=${order.id}&method=BANK`)}
                      >
                        Thanh toán ngay
                      </button>
                    )}

                    <button
                      className="btn-luxury-outline"
                      style={{ padding: '6px 14px', fontSize: 12, fontWeight: 600 }}
                      onClick={() => setSelected(order.id)}
                    >
                      Chi tiết đơn hàng
                    </button>
                  </div>
                </div>
              </article>
            );
          })}

          {!loading && !error && filteredProductOrders.length === 0 && (
            <div style={{ textAlign: 'center', padding: '40px 20px', backgroundColor: '#FFFFFF', borderRadius: 12, border: '1px solid #E5E7EB' }}>
              <ShoppingBag size={40} style={{ color: '#D1D5DB', margin: '0 auto 12px' }} />
              <h4 style={{ margin: '0 0 6px', fontSize: 16, color: '#374151' }}>
                {searchQuery || statusFilter !== 'ALL' ? 'Không tìm thấy đơn hàng mua sắm nào khớp với bộ lọc' : 'Bạn chưa có đơn mua sắm nào'}
              </h4>
              <p style={{ margin: 0, fontSize: 13, color: '#6B7280' }}>
                {searchQuery || statusFilter !== 'ALL' ? 'Thử xóa từ khóa tìm kiếm hoặc chọn bộ lọc trạng thái khác.' : 'Khám phá sản phẩm và bắt đầu mua sắm ngay hôm nay.'}
              </p>
            </div>
          )}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {serviceSubFilter === 'UNINVOICED' ? (
            <ServiceBookingOrders onNavigate={onNavigate} refreshKey={data} />
          ) : (
            <>
              {filteredServiceOrders.map(order => {
                const isPackage = order.servicePackageId != null;
                const statusStyle = getStatusBadgeStyle(order.status);
                const paymentStyle = getPaymentBadgeStyle(order.paymentStatus);

                return (
                  <article
                    className="account-card"
                    key={order.id}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 14,
                      padding: '18px 20px',
                      border: '1px solid #E5E7EB',
                      borderRadius: 12,
                      backgroundColor: '#FFFFFF',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.04)'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #F3F4F6', paddingBottom: 12, flexWrap: 'wrap', gap: 10 }}>
                      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                        <span style={{ fontSize: 11, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                          {isPackage ? 'Mã đơn gói:' : 'Mã hóa đơn:'}
                        </span>
                        <strong style={{ fontFamily: 'monospace', fontSize: 15, color: 'var(--c-primary, #721B29)' }}>
                          {order.orderNumber}
                        </strong>
                        <span
                          style={{
                            fontSize: 11,
                            padding: '2px 8px',
                            borderRadius: 4,
                            background: isPackage ? '#FDF2F8' : '#EFF6FF',
                            color: isPackage ? '#9D174D' : '#1D4ED8',
                            fontWeight: 600
                          }}
                        >
                          {isPackage
                            ? (order.spaBenefitType === 'TREATMENT_COURSE' ? 'Lộ trình chăm sóc da' : 'Gói dịch vụ Spa')
                            : 'Hóa đơn dịch vụ tại quầy'}
                        </span>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#9CA3AF', marginLeft: 4 }}>
                          <Clock size={13} />
                          {order.createdAt && formatDateTime(order.createdAt)}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                        <span style={{ fontSize: 12, fontWeight: 600, padding: '3px 10px', borderRadius: 12, backgroundColor: statusStyle.bg, color: statusStyle.color, border: `1px solid ${statusStyle.border}` }}>
                          {statuses[order.status] || order.status}
                        </span>
                        <span style={{ fontSize: 12, fontWeight: 600, padding: '3px 10px', borderRadius: 12, backgroundColor: paymentStyle.bg, color: paymentStyle.color, border: `1px solid ${paymentStyle.border}` }}>
                          {getPaymentStatusBadge(order.paymentStatus).text}
                        </span>
                      </div>
                    </div>

                    {/* Service Details Body */}
                    {isPackage ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '12px 14px', backgroundColor: '#FDF2F8', border: '1px solid #FBCFE8', borderRadius: 8, fontSize: 13 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#9D174D', fontWeight: 700 }}>
                          <Package size={17} />
                          <span>Thông tin gói dịch vụ điện tử</span>
                        </div>
                        <div style={{ color: '#1F2937' }}>
                          Mã gói liệu trình: <strong style={{ color: 'var(--c-primary, #721B29)' }}>#{order.servicePackageId}</strong>
                        </div>
                        <div style={{ color: '#6B7280', fontSize: 12 }}>
                          Quyền lợi: <strong>{order.spaBenefitType === 'TREATMENT_COURSE' ? 'Lộ trình chăm sóc da chuyên sâu đa buổi' : 'Gói dịch vụ Spa nhiều lượt'}</strong>.
                          Lượt vé điện tử được lưu trong hồ sơ tài khoản và sử dụng khi đặt lịch chăm sóc da.
                        </div>
                      </div>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#1E40AF', padding: '8px 12px', backgroundColor: '#EFF6FF', borderRadius: 6, border: '1px solid #DBEAFE' }}>
                          <Calendar size={15} />
                          <span>Buổi chăm sóc da tại cơ sở {order.appointmentId ? `(Mã buổi hẹn: SPA-APT-#${order.appointmentId})` : ''}</span>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                          {(order.spaVisitItems || []).map((item, idx) => (
                            <div
                              key={item.appointmentItemId || idx}
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                padding: '10px 14px',
                                backgroundColor: '#FAFAFA',
                                borderRadius: 8,
                                border: '1px solid #F3F4F6',
                                fontSize: 13
                              }}
                            >
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <Sparkles size={15} style={{ color: 'var(--c-primary, #721B29)' }} />
                                <div>
                                  <strong>{item.serviceName}</strong>
                                  {item.appointmentItemId && (
                                    <span style={{ fontSize: 11, color: '#6B7280', display: 'block' }}>
                                      Dịch vụ thực hiện tại quầy
                                    </span>
                                  )}
                                </div>
                              </div>
                              <strong style={{ fontFamily: 'monospace', color: 'var(--c-primary, #721B29)' }}>
                                {money(item.unitPrice || item.price || item.amount)}
                              </strong>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Service Info and Payment Summary */}
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
                        gap: 12,
                        padding: '12px 14px',
                        backgroundColor: '#F9FAFB',
                        borderRadius: 8,
                        border: '1px solid #F3F4F6',
                        fontSize: 12
                      }}
                    >
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#374151' }}>
                          <User size={14} style={{ color: 'var(--c-primary, #721B29)' }} />
                          <span>Khách hàng</span>
                        </div>
                        <div style={{ color: '#1F2937', fontWeight: 500 }}>
                          {order.customerName} {order.customerPhone ? `· ${order.customerPhone}` : ''}
                        </div>
                        <div style={{ color: '#6B7280' }}>
                          Cơ sở: <strong>BeautyShop Clinic & Spa</strong>
                        </div>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#374151' }}>
                          <CreditCard size={14} style={{ color: '#059669' }} />
                          <span>Thanh toán</span>
                        </div>
                        <div style={{ color: '#1F2937', fontWeight: 500 }}>
                          {getPaymentMethodLabel(order.paymentMethod)}
                        </div>
                        <div style={{ color: paymentStyle.color, fontWeight: 600, fontSize: 11.5 }}>
                          {getPaymentStatusBadge(order.paymentStatus).text}
                          {order.paidAmount > 0 && ` (${money(order.paidAmount)})`}
                        </div>
                      </div>

                      {order.discountAmount != null && Number(order.discountAmount) > 0 && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                          <div style={{ fontWeight: 600, color: '#059669' }}>
                            Ưu đãi đã áp dụng
                          </div>
                          <div style={{ color: '#059669', fontWeight: 600 }}>
                            -{money(order.discountAmount)}
                          </div>
                          <div style={{ color: '#6B7280', fontSize: 11 }}>
                            Chiết khấu thành viên / ưu đãi dịch vụ
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Service Card Footer */}
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        paddingTop: 10,
                        borderTop: '1px solid #F3F4F6',
                        flexWrap: 'wrap',
                        gap: 12
                      }}
                    >
                      <div>
                        <span style={{ fontSize: 12, color: '#6B7280' }}>Tổng thanh toán: </span>
                        <strong style={{ color: 'var(--c-deal-red, #E11D48)', fontFamily: 'monospace', fontSize: 16 }}>
                          {money(order.totalAmount)}
                        </strong>
                      </div>

                      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                        {isPackage && (
                          <button
                            type="button"
                            className="btn-luxury-primary"
                            style={{ padding: '6px 14px', fontSize: 12, fontWeight: 600 }}
                            onClick={() => onNavigate(orderBenefitDestination(order))}
                          >
                            {order.spaBenefitType === 'TREATMENT_COURSE' ? 'Xem tiến trình lộ trình' : 'Mở ví vé liệu trình'}
                          </button>
                        )}

                        {order.paymentMethod === 'BANK' && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (!order.paymentDeadline || Date.parse(order.paymentDeadline) > Date.now()) && (
                          <button
                            type="button"
                            className="btn-luxury-primary"
                            style={{ padding: '6px 14px', fontSize: 12, backgroundColor: '#D97706', borderColor: '#B45309', color: '#FFFFFF', fontWeight: 600 }}
                            onClick={() => onNavigate(`order-success?id=${order.id}&method=BANK`)}
                          >
                            Thanh toán ngay
                          </button>
                        )}

                        <button
                          className="btn-luxury-outline"
                          style={{ padding: '6px 14px', fontSize: 12, fontWeight: 600 }}
                          onClick={() => setSelected(order.id)}
                        >
                          Xem chi tiết hóa đơn
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}

              {!loading && !error && filteredServiceOrders.length === 0 && (
                <div style={{ textAlign: 'center', padding: '40px 20px', backgroundColor: '#FFFFFF', borderRadius: 12, border: '1px solid #E5E7EB' }}>
                  <Calendar size={40} style={{ color: '#D1D5DB', margin: '0 auto 12px' }} />
                  <h4 style={{ margin: '0 0 6px', fontSize: 16, color: '#374151' }}>
                    {searchQuery ? 'Không tìm thấy hóa đơn dịch vụ Spa nào khớp với tìm kiếm' : 'Bạn chưa có hóa đơn dịch vụ Spa nào'}
                  </h4>
                  <p style={{ margin: 0, fontSize: 13, color: '#6B7280' }}>
                    {searchQuery ? 'Thử xóa từ khóa tìm kiếm hoặc chọn bộ lọc dịch vụ khác.' : 'Trải nghiệm dịch vụ chăm sóc da chuyên sâu tại BeautyShop Spa.'}
                  </p>
                </div>
              )}

              {/* In All Services mode, also show uninvoiced appointments at bottom */}
              {serviceSubFilter === 'ALL' && (
                <ServiceBookingOrders onNavigate={onNavigate} refreshKey={data} />
              )}
            </>
          )}
        </div>
      )}

      <Pagination page={page} totalPages={data.totalPages || 1} onChange={setPage} />

      {selected && (
        <OrderDetailDialog
          orderId={selected}
          onClose={() => setSelected(null)}
          onChanged={() => { load(); onOrderChanged?.(); }}
          onNavigate={onNavigate}
        />
      )}
    </div>
  );
}

function SparklesIcon() {
  return (
    <span style={{ display: 'inline-flex', padding: 6, borderRadius: 6, backgroundColor: '#FDF2F8', color: '#DB2777' }}>
      <Sparkles size={14} />
    </span>
  );
}

export function OrderDetailDialog({ orderId, initialOrder = null, onClose, onChanged, onNavigate }) {
  const { isAuthenticated } = useAuth();
  const { fetchCart, setCartOpen } = useCustomerCart();
  const [order, setOrder] = useState(initialOrder);
  const [returns, setReturns] = useState([]);
  const [sessionOwned, setSessionOwned] = useState(!initialOrder);
  const [quantities, setQuantities] = useState({});
  const [reason, setReason] = useState('');
  const returnReplay = useRef({ fingerprint: '', key: '' });
  const operationLock = useRef(false);
  const [evidence, setEvidence] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const dialogRequest = useRef(null);

  const load = useCallback(async () => {
    dialogRequest.current?.abort();
    const controller = new AbortController();
    dialogRequest.current = controller;
    const results = await Promise.allSettled([
      apiClient.get(`/api/v1/orders/${orderId}`, { headers: { 'X-Guest-Session-Id': getGuestSessionId() }, signal: controller.signal }),
      apiClient.get(`/api/v1/orders/${orderId}/returns`, { headers: { 'X-Guest-Session-Id': getGuestSessionId() }, signal: controller.signal })
    ]);
    if (controller.signal.aborted) return;
    if (results[0].status === 'fulfilled') {
      setOrder(unwrap(results[0].value));
      setSessionOwned(true);
    } else if (!initialOrder) {
      setError(results[0].reason?.message || 'Không tải được chi tiết đơn hàng.');
    } else {
      setSessionOwned(false);
    }
    if (results[1].status === 'fulfilled') {
      setReturns(listRows(results[1].value));
    } else if (!initialOrder || results[1].reason?.status !== 403) {
      setError(`Chưa tải được yêu cầu đổi trả. ${results[1].reason?.message || ''}`);
    }
  }, [orderId, initialOrder]);

  useEffect(() => {
    setOrder(initialOrder);
    setReturns([]);
    load();
    return () => dialogRequest.current?.abort();
  }, [orderId, load]);

  const run = async work => {
    if (operationLock.current) return;
    operationLock.current = true;
    setBusy(true);
    setError('');
    try {
      await work();
      await load();
      await onChanged?.();
    } catch (e) {
      setError(e.message);
    } finally {
      operationLock.current = false;
      setBusy(false);
    }
  };

  const reorder = async () => {
    const failed = [];
    let added = 0;
    for (const item of order.items || []) {
      if (!item.variantId) {
        failed.push(item.productName);
        continue;
      }
      try {
        await apiClient.post('/api/v1/cart/add', {
          variantId: item.variantId,
          quantity: item.quantity,
          sessionId: getGuestSessionId()
        });
        added++;
      } catch (e) {
        failed.push(`${item.productName}: ${e.message}`);
      }
    }
    if (added) {
      await fetchCart();
      setCartOpen(true);
    }
    if (failed.length) {
      throw new Error(`Đã thêm ${added} mặt hàng. Không thể thêm: ${failed.join('; ')}. Giá và tồn kho áp dụng tại thời điểm mua lại.`);
    }
    setError('Đã thêm các mặt hàng vào giỏ với giá hiện tại.');
  };

  const reserved = itemId =>
    returns
      .filter(row => row.status !== 'REJECTED')
      .reduce((sum, row) => sum + (row.lines || []).filter(line => Number(line.orderItemId) === Number(itemId)).reduce((n, line) => n + Number(line.quantity), 0), 0);

  const statusStyle = order ? getStatusBadgeStyle(order.status) : {};
  const paymentStyle = order ? getPaymentBadgeStyle(order.paymentStatus) : {};
  const typeBadge = order ? getOrderTypeBadge(order) : null;
  const isSpaOrder = order ? getOrderType(order) !== 'PRODUCT' : false;

  return (
    <Modal
      isOpen
      onClose={() => { if (!busy) onClose(); }}
      title={order?.orderNumber ? (isSpaOrder ? `Hóa đơn dịch vụ #${order.orderNumber}` : `Chi tiết đơn hàng #${order.orderNumber}`) : `Đơn hàng #${orderId}`}
      maxWidth="860px"
    >
      <div className="account-stack" style={{ gap: 18 }}>
        {error && (
          <div style={{ padding: '10px 14px', backgroundColor: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, color: '#991B1B', fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }} role="status">
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        <div className="no-print" style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, flexWrap: 'wrap' }}>
          {order && (
            <button
              type="button"
              disabled={busy}
              onClick={() => window.print()}
              className="btn-luxury-outline"
              style={{ padding: '6px 14px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
            >
              <Printer size={13} />
              In hóa đơn
            </button>
          )}
          <button
            disabled={busy}
            onClick={load}
            className="btn-luxury-outline"
            style={{ padding: '6px 14px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <RefreshCw size={13} className={busy ? 'animate-spin' : ''} />
            Tải lại chi tiết
          </button>
        </div>

        {order ? (
          <>
            {/* Header Status Bar */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                backgroundColor: '#F9FAFB',
                padding: '14px 18px',
                borderRadius: 10,
                border: '1px solid #E5E7EB',
                flexWrap: 'wrap',
                gap: 12
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      padding: '3px 10px',
                      borderRadius: 12,
                      backgroundColor: statusStyle.bg,
                      color: statusStyle.color,
                      border: `1px solid ${statusStyle.border}`
                    }}
                  >
                    {statuses[order.status] || order.status}
                  </span>
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      padding: '3px 10px',
                      borderRadius: 12,
                      backgroundColor: paymentStyle.bg,
                      color: paymentStyle.color,
                      border: `1px solid ${paymentStyle.border}`
                    }}
                  >
                    {getPaymentStatusBadge(order.paymentStatus).text}
                  </span>
                  {typeBadge && (
                    <span style={{ fontSize: 12, color: '#4B5563', backgroundColor: '#F3F4F6', padding: '2px 8px', borderRadius: 4, fontWeight: 500 }}>
                      {typeBadge.text}
                    </span>
                  )}
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-muted, #6B7280)', marginTop: 6, display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Clock size={13} />
                  Thời gian: {order.createdAt && formatDateTime(order.createdAt)}
                </div>
              </div>

              <div style={{ textAlign: 'right' }}>
                <span style={{ fontSize: 11, color: 'var(--text-muted, #6B7280)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  {isSpaOrder ? 'Mã chứng từ' : 'Mã đơn hàng'}
                </span>
                <div style={{ fontFamily: 'monospace', fontWeight: 800, fontSize: 16, color: 'var(--c-deal-red, #E11D48)' }}>
                  {order.orderNumber}
                </div>
              </div>
            </div>

            {/* Recipient & Shipping Information Grid (or Spa Location Grid) */}
            {!isSpaOrder ? (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                  gap: 14,
                  backgroundColor: '#FFFFFF',
                  borderRadius: 10,
                  border: '1px solid #E5E7EB',
                  padding: '16px 18px',
                  fontSize: 13
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, color: 'var(--c-primary, #721B29)' }}>
                    <MapPin size={16} />
                    <span>Thông tin nhận hàng</span>
                  </div>
                  <div style={{ fontWeight: 600, color: '#1F2937' }}>
                    {order.customerName} {order.customerPhone ? `· ${order.customerPhone}` : ''}
                  </div>
                  {order.customerEmail && (
                    <div style={{ color: '#6B7280', fontSize: 12 }}>
                      Email: {order.customerEmail}
                    </div>
                  )}
                  <div style={{ color: '#4B5563', lineHeight: 1.5, marginTop: 2 }}>
                    {order.shippingAddress || 'Chưa cung cấp địa chỉ'}
                  </div>
                  {order.notes && (
                    <div style={{ color: '#6B7280', fontSize: 12, fontStyle: 'italic' }}>
                      Ghi chú: {order.notes}
                    </div>
                  )}
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, color: '#2563EB' }}>
                    <Truck size={16} />
                    <span>Vận chuyển & Giao hàng</span>
                  </div>
                  <div style={{ color: '#1F2937' }}>
                    Đơn vị vận chuyển: <strong>{order.carrierName || 'Đang sắp xếp'}</strong>
                  </div>
                  <div style={{ color: '#4B5563' }}>
                    Mã vận đơn:{' '}
                    <strong style={{ fontFamily: 'monospace', backgroundColor: '#F3F4F6', padding: '2px 6px', borderRadius: 4 }}>
                      {order.trackingCode || 'Đang cập nhật'}
                    </strong>
                  </div>
                  {order.deliveredAt && (
                    <div style={{ color: '#059669', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}>
                      <CheckCircle2 size={14} />
                      Đã giao: {formatDateTime(order.deliveredAt)}
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                  gap: 14,
                  backgroundColor: '#FFFFFF',
                  borderRadius: 10,
                  border: '1px solid #E5E7EB',
                  padding: '16px 18px',
                  fontSize: 13
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, color: 'var(--c-primary, #721B29)' }}>
                    <User size={16} />
                    <span>Khách hàng sử dụng</span>
                  </div>
                  <div style={{ fontWeight: 600, color: '#1F2937' }}>
                    {order.customerName} {order.customerPhone ? `· ${order.customerPhone}` : ''}
                  </div>
                  {order.customerEmail && (
                    <div style={{ color: '#6B7280', fontSize: 12 }}>
                      Email: {order.customerEmail}
                    </div>
                  )}
                  <div style={{ color: '#4B5563', lineHeight: 1.5, marginTop: 2 }}>
                    Cơ sở thực hiện: <strong>BeautyShop Clinic & Spa</strong>
                  </div>
                  {order.notes && (
                    <div style={{ color: '#6B7280', fontSize: 12, fontStyle: 'italic' }}>
                      Ghi chú: {order.notes}
                    </div>
                  )}
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, color: '#9D174D' }}>
                    <Sparkles size={16} />
                    <span>Thông tin dịch vụ Spa</span>
                  </div>
                  <div>
                    Hình thức: <strong>{order.servicePackageId ? (order.spaBenefitType === 'TREATMENT_COURSE' ? 'Lộ trình chăm sóc da chuyên sâu' : 'Gói dịch vụ Spa nhiều lượt') : 'Hóa đơn buổi chăm sóc da tại quầy'}</strong>
                  </div>
                  {order.appointmentId && (
                    <div style={{ color: '#6B7280' }}>
                      Mã buổi hẹn: <strong style={{ color: 'var(--c-primary, #721B29)' }}>SPA-APT-#{order.appointmentId}</strong>
                    </div>
                  )}
                  {order.servicePackageId && (
                    <div style={{ color: '#6B7280' }}>
                      Mã gói liệu trình: <strong style={{ color: 'var(--c-primary, #721B29)' }}>#{order.servicePackageId}</strong>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Payment Details Section */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: 14,
                backgroundColor: '#FFFFFF',
                borderRadius: 10,
                border: '1px solid #E5E7EB',
                padding: '14px 18px',
                fontSize: 13
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, color: '#059669' }}>
                  <CreditCard size={16} />
                  <span>Phương thức thanh toán</span>
                </div>
                <div style={{ color: '#1F2937', fontWeight: 600 }}>
                  {getPaymentMethodLabel(order.paymentMethod)}
                </div>
                <div style={{ color: paymentStyle.color, fontWeight: 600, fontSize: 12 }}>
                  Trạng thái: {getPaymentStatusBadge(order.paymentStatus).text}
                </div>
                {order.paidAt && (
                  <div style={{ color: '#6B7280', fontSize: 12 }}>
                    Thanh toán lúc: {formatDateTime(order.paidAt)}
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ fontWeight: 700, color: '#374151' }}>
                  Tiến độ thanh toán
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', color: '#4B5563' }}>
                  <span>Đã thanh toán:</span>
                  <strong style={{ fontFamily: 'monospace', color: '#059669' }}>{money(order.paidAmount || 0)}</strong>
                </div>
                {order.refundedAmount != null && Number(order.refundedAmount) > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: '#DC2626' }}>
                    <span>Đã hoàn tiền:</span>
                    <strong style={{ fontFamily: 'monospace' }}>-{money(order.refundedAmount)}</strong>
                  </div>
                )}
                {order.paymentDeadline && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (
                  <div style={{ color: '#B45309', fontSize: 12, marginTop: 2 }}>
                    Hạn thanh toán: <strong>{formatDateTime(order.paymentDeadline)}</strong>
                  </div>
                )}
              </div>
            </div>

            {/* Service Package Highlight */}
            {order.servicePackageId && (
              <div style={{ padding: '12px 16px', backgroundColor: '#FDF2F8', border: '1px solid #FBCFE8', borderRadius: 8, fontSize: 13, color: '#9D174D' }}>
                <strong>Đơn dịch vụ Spa / Liệu trình</strong>: Mã gói #{order.servicePackageId}
                {order.spaBenefitType && (
                  <span style={{ display: 'block', fontSize: 12, marginTop: 4 }}>
                    Quyền lợi: {order.spaBenefitType === 'TREATMENT_COURSE' ? 'Lộ trình chăm sóc da chuyên sâu' : 'Gói dịch vụ Spa'}
                  </span>
                )}
              </div>
            )}

            {/* Product Table */}
            <div>
              <h4 style={{ margin: '0 0 10px', fontSize: 14, fontWeight: 700, color: '#374151' }}>
                Danh sách sản phẩm trong đơn ({order.items?.length || 0})
              </h4>
              <div style={{ border: '1px solid #E5E7EB', borderRadius: 8, overflow: 'hidden' }}>
                <table className="account-order-lines" style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, backgroundColor: '#FFFFFF' }}>
                  <thead>
                    <tr style={{ backgroundColor: '#F9FAFB', borderBottom: '1px solid #E5E7EB', textAlign: 'left', color: '#4B5563', fontSize: 12 }}>
                      <th style={{ width: 64, padding: '10px 12px' }}>Ảnh</th>
                      <th style={{ padding: '10px 12px' }}>Sản phẩm / Phân loại</th>
                      <th style={{ textAlign: 'right', padding: '10px 12px' }}>Đơn giá</th>
                      <th style={{ textAlign: 'center', width: 70, padding: '10px 12px' }}>Số lượng</th>
                      <th style={{ textAlign: 'right', padding: '10px 12px' }}>Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(order.items || []).map((item, idx) => {
                      const unitPrice = Number(item.price || 0);
                      const discount = Number(item.discount || 0);
                      const effective = Math.max(0, unitPrice - discount);
                      const lineTotal = effective * Number(item.quantity || 1);
                      const productLink = item.productId ? `#/product/${item.productId}` : null;

                      return (
                        <tr key={item.id || idx} style={{ borderBottom: '1px solid #F3F4F6' }}>
                          <td style={{ padding: '10px 12px' }}>
                            <div
                              style={{
                                width: 52,
                                height: 52,
                                borderRadius: 6,
                                overflow: 'hidden',
                                backgroundColor: '#F9FAFB',
                                border: '1px solid #E5E7EB',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center'
                              }}
                            >
                              <img
                                src={resolveProductImage(item)}
                                alt={item.productName || 'Sản phẩm'}
                                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                onError={e => {
                                  if (e.currentTarget.src !== PRODUCT_PLACEHOLDER) {
                                    e.currentTarget.src = PRODUCT_PLACEHOLDER;
                                  }
                                }}
                              />
                            </div>
                          </td>
                          <td style={{ padding: '10px 12px' }}>
                            {productLink ? (
                              <a
                                href={productLink}
                                style={{ fontWeight: 600, color: 'var(--c-primary, #721B29)', textDecoration: 'none', lineHeight: 1.4 }}
                                onClick={() => onClose()}
                              >
                                {item.productName}
                              </a>
                            ) : (
                              <span style={{ fontWeight: 600, color: '#1F2937', lineHeight: 1.4 }}>
                                {item.productName}
                              </span>
                            )}
                            <div style={{ fontSize: 11, color: '#6B7280', marginTop: 4, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                              {item.variantName && (
                                <span style={{ backgroundColor: '#EFF6FF', color: '#1E40AF', padding: '1px 6px', borderRadius: 4, fontWeight: 500 }}>
                                  Phân loại: <strong>{item.variantName}</strong>
                                </span>
                              )}
                              {item.sku && (
                                <span>
                                  SKU: <code style={{ fontSize: 10.5, fontFamily: 'monospace', backgroundColor: '#F3F4F6', padding: '1px 4px', borderRadius: 3 }}>{item.sku}</code>
                                </span>
                              )}
                              {order.status === 'DELIVERED' && item.productId && (
                                <a
                                  href={`#/product/${item.productId}#product-reviews`}
                                  onClick={() => onClose()}
                                  style={{
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: 4,
                                    fontSize: 11.5,
                                    fontWeight: 600,
                                    color: '#92400E',
                                    backgroundColor: '#FEF3C7',
                                    border: '1px solid #FDE68A',
                                    padding: '2px 8px',
                                    borderRadius: 4,
                                    textDecoration: 'none'
                                  }}
                                >
                                  <Star size={12} fill="#92400E" color="#92400E" />
                                  <span>Đánh giá sản phẩm</span>
                                </a>
                              )}
                            </div>
                          </td>
                          <td style={{ textAlign: 'right', padding: '10px 12px' }}>
                            {discount > 0 ? (
                              <div>
                                <div style={{ fontWeight: 600, color: '#111827' }}>{money(effective)}</div>
                                <div style={{ fontSize: 11, color: '#9CA3AF', textDecoration: 'line-through' }}>{money(unitPrice)}</div>
                              </div>
                            ) : (
                              <span style={{ fontWeight: 600, color: '#111827' }}>{money(unitPrice)}</span>
                            )}
                          </td>
                          <td style={{ textAlign: 'center', fontWeight: 600, padding: '10px 12px' }}>
                            {item.quantity}
                          </td>
                          <td style={{ textAlign: 'right', fontWeight: 700, fontFamily: 'monospace', padding: '10px 12px', color: 'var(--c-primary, #721B29)' }}>
                            {money(lineTotal)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Spa Visit Services */}
            {(order.spaVisitItems || []).map((item, index) => (
              <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 14px', backgroundColor: '#FFF5F7', borderRadius: 8, border: '1px solid #FCE7F3', fontSize: 13 }}>
                <span>Dịch vụ Spa: <strong>{item.serviceName}</strong></span>
                <span style={{ fontWeight: 700, fontFamily: 'monospace', color: '#BE185D' }}>{money(item.amount || item.price || item.unitPrice)}</span>
              </div>
            ))}

            {/* Financial Summary */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
                alignItems: 'flex-end',
                padding: '14px 18px',
                backgroundColor: '#F9FAFB',
                borderRadius: 8,
                border: '1px solid #E5E7EB',
                fontSize: 13
              }}
            >
              {order.subTotal != null && (
                <div style={{ display: 'flex', justifyContent: 'space-between', minWidth: 260 }}>
                  <span style={{ color: '#6B7280' }}>Tạm tính tiền hàng:</span>
                  <span style={{ fontFamily: 'monospace' }}>{money(order.subTotal)}</span>
                </div>
              )}
              {order.discountAmount != null && Number(order.discountAmount) > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', minWidth: 260, color: '#059669' }}>
                  <span>Giảm giá & ưu đãi:</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>-{money(order.discountAmount)}</span>
                </div>
              )}
              {order.shippingFee != null && (
                <div style={{ display: 'flex', justifyContent: 'space-between', minWidth: 260 }}>
                  <span style={{ color: '#6B7280' }}>Phí vận chuyển:</span>
                  <span style={{ fontFamily: 'monospace' }}>
                    {Number(order.shippingFee) === 0 ? 'MIỄN PHÍ' : money(order.shippingFee)}
                  </span>
                </div>
              )}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  minWidth: 260,
                  fontSize: 16,
                  fontWeight: 800,
                  color: 'var(--c-primary, #721B29)',
                  paddingTop: 8,
                  borderTop: '1px dashed #D1D5DB'
                }}
              >
                <span>Tổng thanh toán:</span>
                <span style={{ color: 'var(--c-deal-red, #E11D48)', fontFamily: 'monospace' }}>
                  {money(order.totalAmount)}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', minWidth: 260, color: '#059669', fontSize: 12 }}>
                <span>Đã thanh toán:</span>
                <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{money(order.paidAmount || 0)}</span>
              </div>
              {order.refundedAmount != null && Number(order.refundedAmount) > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', minWidth: 260, color: '#DC2626', fontSize: 12 }}>
                  <span>Đã hoàn tiền{order.refundReference ? ` (${order.refundReference})` : ''}:</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>-{money(order.refundedAmount)}</span>
                </div>
              )}
            </div>

            {order.cancelReason && (
              <div style={{ padding: '10px 14px', backgroundColor: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, color: '#DC2626', fontSize: 13 }}>
                <strong>Lý do hủy đơn:</strong> {order.cancelReason}
              </div>
            )}

            {/* Action Buttons */}
            <div className="account-actions no-print" style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 4, alignItems: 'center' }}>
              {order.status === 'PENDING' && isAuthenticated && order.userId != null && (
                <button
                  type="button"
                  disabled={busy}
                  className="btn-luxury-outline"
                  style={{ color: '#DC2626', borderColor: '#FCA5A5' }}
                  onClick={() => {
                    if (window.confirm('Bạn có chắc chắn muốn hủy đơn hàng này?')) {
                      run(() => apiClient.delete(`/api/v1/orders/${order.id}/cancel`));
                    }
                  }}
                >
                  Hủy đơn hàng
                </button>
              )}
              {order.status === 'PENDING' && (!isAuthenticated || order.userId == null) && (
                <span style={{ fontSize: 12, color: '#6B7280', fontStyle: 'italic' }}>
                  Đơn khách vãng lai không hỗ trợ tự hủy trực tuyến. Vui lòng liên hệ hotline CSKH nếu cần hủy đơn.
                </span>
              )}
              {['CONFIRMED', 'PROCESSING'].includes(order.status) && (
                <span style={{ fontSize: 12, color: '#6B7280', fontStyle: 'italic' }}>
                  Đơn hàng đang được chuẩn bị đóng gói. Nếu cần thay đổi hoặc hủy, vui lòng liên hệ hotline CSKH.
                </span>
              )}
              {order.paymentMethod === 'BANK' && order.paymentStatus === 'PENDING' && order.status !== 'CANCELLED' && (!order.paymentDeadline || Date.parse(order.paymentDeadline) > Date.now()) && (
                <button
                  type="button"
                  className="btn-luxury-primary"
                  style={{ backgroundColor: '#D97706', borderColor: '#B45309' }}
                  onClick={() => {
                    onClose();
                    onNavigate(`order-success?id=${order.id}&method=BANK`);
                  }}
                >
                  Tiếp tục thanh toán
                </button>
              )}
              {order.items?.length > 0 && !order.servicePackageId && !order.appointmentId && (
                <button
                  type="button"
                  disabled={busy}
                  className="btn-luxury-outline"
                  onClick={() => run(reorder)}
                >
                  Mua lại theo giá hiện tại
                </button>
              )}
            </div>

            {/* Status Timeline */}
            <details className="no-print" style={{ backgroundColor: '#F9FAFB', padding: '12px 16px', borderRadius: 8, border: '1px solid #E5E7EB' }}>
              <summary style={{ fontWeight: 600, cursor: 'pointer', color: '#374151', fontSize: 13 }}>
                Lịch sử trạng thái đơn hàng ({(order.statusHistories || []).length})
              </summary>
              <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(order.statusHistories || []).map((row, index) => (
                  <div key={row.id || index} style={{ fontSize: 12, padding: '6px 10px', backgroundColor: '#FFFFFF', borderRadius: 6, border: '1px solid #F3F4F6', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <strong style={{ color: 'var(--c-primary, #721B29)' }}>{statuses[row.status] || row.status || row.toStatus}</strong>
                      {row.notes && <span style={{ color: '#4B5563', marginLeft: 6 }}>· {row.notes}</span>}
                      {row.reason && <span style={{ color: '#DC2626', marginLeft: 6 }}>· {row.reason}</span>}
                    </div>
                    <span style={{ color: '#9CA3AF', fontSize: 11 }}>
                      {row.createdAt && formatDateTime(row.createdAt)}
                    </span>
                  </div>
                ))}
              </div>
            </details>

            {/* Returns & Refund Section (Physical products only) */}
            {!isSpaOrder && (
              <section className="account-stack no-print" style={{ gap: 12 }}>
                <h4 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: '#374151' }}>
                  Yêu cầu đổi trả ({returns.length})
                </h4>
                {returns.map(row => (
                  <article className="account-card" key={row.id} style={{ padding: '12px 16px', backgroundColor: '#F9FAFB', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 13 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                      <strong>#{row.id} · {statuses[row.status] || row.status}</strong>
                      <span style={{ fontWeight: 700, fontFamily: 'monospace', color: '#059669' }}>
                        Tiền hoàn: {money(row.refundAmount)}
                      </span>
                    </div>
                    <p style={{ margin: '0 0 4px', color: '#4B5563' }}>Lý do: {row.reason}</p>
                    <p style={{ margin: '0 0 4px', fontSize: 12, color: '#6B7280' }}>
                      Sản phẩm: {(row.lines || []).map(line => `${order.items?.find(item => Number(item.id) === Number(line.orderItemId))?.productName || `Dòng #${line.orderItemId}`} × ${line.quantity}`).join(', ')}
                    </p>
                    {row.decisionReason && (
                      <p style={{ margin: 0, fontSize: 12, color: '#1E40AF', backgroundColor: '#EFF6FF', padding: '4px 8px', borderRadius: 4 }}>
                        Phản hồi từ shop: {row.decisionReason}
                      </p>
                    )}
                  </article>
                ))}

                {order.status === 'DELIVERED' && sessionOwned && (
                  <form
                    className="account-form"
                    style={{ backgroundColor: '#F9FAFB', padding: '16px 18px', borderRadius: 8, border: '1px solid #E5E7EB', display: 'flex', flexDirection: 'column', gap: 12 }}
                    onSubmit={event => {
                      event.preventDefault();
                      const lines = Object.entries(quantities)
                        .filter(([, quantity]) => Number(quantity) > 0)
                        .map(([id, quantity]) => ({ orderItemId: Number(id), quantity: Number(quantity) }));
                      if (!lines.length) {
                        setError('Chọn ít nhất một sản phẩm cần đổi trả.');
                        return;
                      }
                      run(async () => {
                        const payload = {
                          reason: reason.trim(),
                          lines,
                          evidenceUrls: evidence.split('\n').map(url => url.trim()).filter(Boolean)
                        };
                        if (payload.evidenceUrls.length > 8 || payload.evidenceUrls.some(url => !/^https?:\/\//i.test(url))) {
                          throw new Error('Dùng tối đa 8 đường dẫn ảnh HTTP hoặc HTTPS.');
                        }
                        const fingerprint = JSON.stringify(payload);
                        if (returnReplay.current.fingerprint !== fingerprint) {
                          returnReplay.current = { fingerprint, key: crypto.randomUUID() };
                        }
                        await apiClient.post(`/api/v1/orders/${order.id}/returns`, payload, {
                          headers: {
                            'X-Guest-Session-Id': getGuestSessionId(),
                            'Idempotency-Key': returnReplay.current.key
                          }
                        });
                        returnReplay.current = { fingerprint: '', key: '' };
                        setReason('');
                        setEvidence('');
                        setQuantities({});
                      });
                    }}
                  >
                    <h4 style={{ margin: 0, fontSize: 14 }}>Gửi yêu cầu mới</h4>
                    {(order.items || []).map(item => {
                      const max = Math.max(0, item.quantity - reserved(item.id));
                      return (
                        <label key={item.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 13 }}>
                          <span>
                            {item.productName} · Có thể yêu cầu: <strong>{max}</strong>
                          </span>
                          <input
                            type="number"
                            min={0}
                            max={max}
                            step={1}
                            disabled={max === 0 || busy}
                            value={quantities[item.id] || 0}
                            onChange={event => setQuantities({ ...quantities, [item.id]: event.target.value })}
                            style={{ width: 80, padding: '4px 8px', borderRadius: 4, border: '1px solid #D1D5DB' }}
                          />
                        </label>
                      );
                    })}
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
                      Lý do
                      <textarea
                        required
                        minLength={5}
                        maxLength={1000}
                        value={reason}
                        onChange={event => setReason(event.target.value)}
                        placeholder="Mô tả lý do đổi trả chi tiết..."
                        style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #D1D5DB', minHeight: 70 }}
                      />
                    </label>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
                      Ảnh/chứng từ (mỗi URL HTTPS một dòng)
                      <textarea
                        value={evidence}
                        onChange={event => setEvidence(event.target.value)}
                        placeholder="https://example.com/image1.jpg"
                        style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #D1D5DB', minHeight: 60 }}
                      />
                    </label>
                    <p style={{ margin: 0, fontSize: 12, color: '#6B7280' }}>
                      Yêu cầu được nhân viên xét duyệt; số tiền hoàn hiển thị theo kết quả xử lý.
                    </p>
                    <button
                      disabled={busy}
                      className="btn-luxury-primary"
                      style={{ alignSelf: 'flex-start', padding: '8px 18px', fontSize: 13 }}
                    >
                      Gửi yêu cầu đổi trả
                    </button>
                  </form>
                )}
              </section>
            )}
          </>
        ) : (
          <p style={{ textAlign: 'center', padding: '20px 0', color: '#6B7280' }}>Đang tải đơn hàng…</p>
        )}
      </div>
    </Modal>
  );
}
