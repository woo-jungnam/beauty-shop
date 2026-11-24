import { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
  getOrderStatusBadge,
  getPaymentMethodLabel,
  getPaymentStatusBadge,
  resolveMediaUrl,
} from '../../shared/utils/formatters';
import { Badge } from '../../shared/ui/Badge';
import './delivery-proof.css';

const formatAmount = (amount) => amount == null ? '—' : formatCurrency(amount);

export function DeliveryProofDetails({ order }) {
  const [detail, setDetail] = useState(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const loadDetail = async () => {
      try {
        const response = await apiClient.get(ENDPOINTS.ORDERS.ADMIN_DETAIL(order.id), {
          signal: controller.signal,
        });
        const result = response.data || response;
        if (result?.id !== order.id || !Array.isArray(result.items)) {
          throw new Error('Dữ liệu chi tiết đơn hàng không đầy đủ.');
        }
        if (!controller.signal.aborted) setDetail(result);
      } catch {
        if (!controller.signal.aborted) setLoadFailed(true);
      }
    };
    loadDetail();
    return () => controller.abort();
  }, [order.id]);

  const data = detail || order;
  const items = data.items ?? [];
  const status = getOrderStatusBadge(data.status);
  const payment = getPaymentStatusBadge(data.paymentStatus);

  return (
    <div className="delivery-proof">
      <img className="delivery-proof-image" src={resolveMediaUrl(data.deliveryProofUrl || order.deliveryProofUrl)}
        alt={`Ảnh bằng chứng giao đơn ${data.orderNumber || `#${data.id}`}`} />
      <div className="delivery-proof-details">
        <section>
          <h4>Thông tin tài xế</h4>
          <dl>
            <dt>Họ tên</dt><dd>{data.shipperName || 'Chưa ghi nhận'}</dd>
            <dt>Mã tài xế</dt><dd>{data.shipperId ?? '—'}</dd>
            <dt>Số điện thoại</dt><dd>{data.shipperPhone || '—'}</dd>
            <dt>Thời gian giao</dt><dd>{data.deliveredAt ? formatDateTime(data.deliveredAt) : '—'}</dd>
            {data.shipperCodCollected != null && (
              <><dt>Tiền thu hộ đã thu</dt><dd>{formatAmount(data.shipperCodCollected)}</dd></>
            )}
            <dt>Ghi chú giao hàng</dt><dd>{data.deliveredNotes || '—'}</dd>
          </dl>
        </section>
        <section>
          <h4>Chi tiết đơn hàng</h4>
          <dl>
            <dt>Mã đơn</dt><dd>{data.orderNumber || `#${data.id}`}</dd>
            <dt>Trạng thái</dt><dd><Badge variant={status.variant}>{status.text}</Badge></dd>
            <dt>Người nhận</dt><dd>{data.customerName || '—'}</dd>
            <dt>Số điện thoại</dt><dd>{data.customerPhone || '—'}</dd>
            <dt>Địa chỉ giao hàng</dt><dd>{data.shippingAddress || '—'}</dd>
            {data.trackingCode && <><dt>Mã vận đơn</dt><dd>{data.trackingCode}</dd></>}
            <dt>Thanh toán</dt>
            <dd>{getPaymentMethodLabel(data.paymentMethod)} <Badge variant={payment.variant}>{payment.text}</Badge></dd>
            {data.shippingFee != null && <><dt>Phí giao hàng</dt><dd>{formatAmount(data.shippingFee)}</dd></>}
            {Number(data.discountAmount) > 0 && <><dt>Giảm giá</dt><dd>{formatAmount(data.discountAmount)}</dd></>}
            <dt>Tổng tiền</dt><dd><strong>{formatAmount(data.totalAmount)}</strong></dd>
          </dl>
        </section>
        <section aria-busy={!detail && !loadFailed}>
          <h4>Sản phẩm trong đơn</h4>
          {!detail && <p role="status">{loadFailed ? 'Không tải được chi tiết đơn hàng.' : 'Đang tải chi tiết đơn hàng…'}</p>}
          {items.length > 0 && (
            <table className="delivery-proof-items">
              <thead><tr><th scope="col">Sản phẩm</th><th scope="col">SL</th><th scope="col">Đơn giá</th></tr></thead>
              <tbody>
                {items.map((item, index) => (
                  <tr key={item.id ?? index}>
                    <td>{item.productName || item.sku || 'Sản phẩm'}{item.variantName && <small>{item.variantName}</small>}</td>
                    <td>{item.quantity ?? '—'}</td>
                    <td>{formatAmount(item.price)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {detail && items.length === 0 && <p>Không có sản phẩm trong đơn.</p>}
        </section>
      </div>
    </div>
  );
}
