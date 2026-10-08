import React, { useState, useEffect } from 'react';
import {
  Search,
  Filter,
  Eye,
  Edit,
  DollarSign,
  RefreshCw,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
  getOrderStatusBadge,
  getPaymentStatusBadge,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';
import { useAuth } from '../app/providers/AuthProvider';

export const OrdersPage = () => {
  const { isAdmin } = useAuth();
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  // Filters
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');

  // Modals state
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [refundModalOpen, setRefundModalOpen] = useState(false);

  // Form states
  const [targetStatus, setTargetStatus] = useState('CONFIRMED');
  const [carrierName, setCarrierName] = useState('');
  const [trackingCode, setTrackingCode] = useState('');
  const [cancelReason, setCancelReason] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [refundReference, setRefundReference] = useState('');
  const [refundReason, setRefundReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const fetchOrders = async (pageNum = 0) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', pageNum);
      params.append('size', '15');
      if (keyword.trim()) params.append('keyword', keyword.trim());
      if (status) params.append('status', status);
      if (paymentStatus) params.append('paymentStatus', paymentStatus);

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
  }, [status, paymentStatus]);

  const handleSearch = (e) => {
    e.preventDefault();
    fetchOrders(0);
  };

  const handleOpenDetail = async (order) => {
    setSelectedOrder(order);
    setDetailModalOpen(true);
    try {
      const res = await apiClient.get(ENDPOINTS.ORDERS.DETAIL(order.id));
      const fullOrder = res.data || res;
      if (fullOrder && fullOrder.id) {
        setSelectedOrder(fullOrder);
      }
    } catch (e) {
      console.error(e);
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
        notes: targetStatus === 'CANCELLED' ? cancelReason.trim() : undefined,
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
    setRefundAmount(order.paidAmount ? String(order.paidAmount) : String(order.totalAmount || '0'));
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
      });
      setRefundModalOpen(false);
      fetchOrders(page);
    } catch (err) {
      setErrorMessage(err.message || 'Lỗi xác nhận hoàn tiền');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      header: 'Mã đơn / ID',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>{row.orderCode || `#${row.id}`}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID: {row.id}</div>
        </div>
      ),
      width: '130px',
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

      {/* Filter Control Bar */}
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
            <option value="PENDING">Chờ xác nhận (PENDING)</option>
            <option value="CONFIRMED">Đã xác nhận (CONFIRMED)</option>
            <option value="PROCESSING">Đang chuẩn bị hàng (PROCESSING)</option>
            <option value="SHIPPED">Đang vận chuyển (SHIPPED)</option>
            <option value="DELIVERED">Đã giao thành công (DELIVERED)</option>
            <option value="CANCELLED">Đã hủy đơn (CANCELLED)</option>
            <option value="RETURNED">Đã trả hàng (RETURNED)</option>
          </Select>

          <Select
            label="Trạng thái thanh toán"
            value={paymentStatus}
            onChange={(e) => setPaymentStatus(e.target.value)}
            className="form-group-compact"
          >
            <option value="">Tất cả thanh toán</option>
            <option value="PENDING">Chờ thanh toán</option>
            <option value="PAID">Đã thanh toán (PAID)</option>
            <option value="REFUND_PENDING">Chờ hoàn tiền</option>
            <option value="REFUNDED">Đã hoàn tiền (REFUNDED)</option>
            <option value="FAILED">Thanh toán thất bại</option>
          </Select>

          <Button type="submit" variant="primary" icon={Filter}>
            Lọc đơn
          </Button>
        </form>
      </div>

      {/* Orders Table */}
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

      {/* Order Detail Modal */}
      <Modal
        isOpen={detailModalOpen}
        onClose={() => setDetailModalOpen(false)}
        title={`Chi tiết đơn hàng: ${selectedOrder?.orderCode || `#${selectedOrder?.id}`}`}
        maxWidth="680px"
      >
        {selectedOrder && (
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
                {selectedOrder.carrierName && (
                  <div style={{ marginTop: '8px', fontSize: '12px' }}>
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

            {/* Line Items List */}
            <div>
              <div style={{ fontWeight: 700, fontSize: '12px', textTransform: 'uppercase', marginBottom: '8px' }}>
                Danh sách mặt hàng ({selectedOrder.items?.length || 0})
              </div>
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Sản phẩm / Biến thể</th>
                      <th style={{ textAlign: 'right' }}>Đơn giá</th>
                      <th style={{ textAlign: 'center' }}>Số lượng</th>
                      <th style={{ textAlign: 'right' }}>Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(selectedOrder.items || []).map((item, idx) => (
                      <tr key={idx}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{item.productName}</div>
                          {item.variantName && (
                            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                              Biến thể: {item.variantName} (SKU: {item.sku})
                            </div>
                          )}
                        </td>
                        <td style={{ textAlign: 'right' }}>{formatCurrency(item.price)}</td>
                        <td style={{ textAlign: 'center' }}>{item.quantity}</td>
                        <td style={{ textAlign: 'right', fontWeight: 600 }}>
                          {formatCurrency(item.price * item.quantity)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Spa Visit Items if applicable */}
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

            {/* Summary */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '4px',
                alignItems: 'flex-end',
                paddingTop: '12px',
                borderTop: '1px solid var(--border-subtle)',
              }}
            >
              <div>Tổng tiền hàng: <strong>{formatCurrency(selectedOrder.totalAmount)}</strong></div>
              <div>Đã thanh toán: <strong>{formatCurrency(selectedOrder.paidAmount || 0)}</strong></div>
            </div>
          </div>
        )}
      </Modal>

      {/* Update Status Modal */}
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

          <Select
            label="Chọn trạng thái luân chuyển mới"
            value={targetStatus}
            onChange={(e) => setTargetStatus(e.target.value)}
          >
            <option value="CONFIRMED">Xác nhận đơn hàng (CONFIRMED)</option>
            <option value="PROCESSING">Chuyển sang chuẩn bị hàng (PROCESSING)</option>
            <option value="SHIPPED">Xuất kho bàn giao vận chuyển (SHIPPED)</option>
            <option value="DELIVERED">Xác nhận giao thành công (DELIVERED)</option>
            <option value="CANCELLED">Hủy đơn hàng (CANCELLED)</option>
            <option value="RETURNED">Khách trả hàng về kho (RETURNED)</option>
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

          {targetStatus === 'CANCELLED' && (
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

      {/* Refund Confirmation Modal */}
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
    </div>
  );
};
