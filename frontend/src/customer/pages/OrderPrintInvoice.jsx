import React from 'react';
import {
  formatCurrency,
  formatDateTime,
  getOrderStatusText,
  getOrderType,
  getPaymentMethodLabel,
  getPaymentStatusBadge,
} from '../../shared/utils/formatters';
import './order-print-invoice.css';

const money = amount => amount == null ? '—' : formatCurrency(amount);

const InvoiceField = ({ label, children }) => (
  <div><dt>{label}</dt><dd>{children ?? '—'}</dd></div>
);

export default function OrderPrintInvoice({ order, paymentExpired }) {
  const orderType = getOrderType(order);
  const paymentMethod = order.paymentMethod === 'VIETQR_SEPAY' ? 'BANK' : order.paymentMethod;
  const productLines = (order.items ?? []).map(item => ({
    id: item.id,
    name: item.productName,
    details: [item.variantName, item.sku && `SKU ${item.sku}`].filter(Boolean).join(' · '),
    quantity: item.quantity,
    price: item.price,
    discount: item.discount,
  }));
  const spaLines = (order.spaVisitItems ?? []).map(item => ({
    id: item.appointmentItemId,
    name: item.serviceName,
    quantity: 1,
    price: item.unitPrice,
  }));
  const lines = [...productLines, ...spaLines];
  if (orderType === 'SPA_PACKAGE' && lines.length === 0) {
    lines.push({ name: 'Gói dịch vụ Spa', details: `Mã gói ${order.servicePackageId}`, quantity: 1, price: order.subTotal });
  }

  return (
    <article className="order-print-invoice">
      <p className="order-print-brand">BeautyShop</p>
      <h1>Hóa đơn đơn hàng</h1>
      <div className="order-print-details">
        <section aria-labelledby="invoice-order-heading">
          <h2 id="invoice-order-heading">Thông tin đơn hàng</h2>
          <dl>
            <InvoiceField label="Mã đơn hàng">{order.orderNumber || order.orderCode || order.id}</InvoiceField>
            <InvoiceField label="Ngày đặt">{formatDateTime(order.createdAt)}</InvoiceField>
            <InvoiceField label="Trạng thái đơn">{getOrderStatusText(order.status)}</InvoiceField>
            <InvoiceField label="Phương thức">{getPaymentMethodLabel(paymentMethod)}</InvoiceField>
            <InvoiceField label="Thanh toán">
              {paymentExpired ? 'Hết hạn thanh toán' : getPaymentStatusBadge(order.paymentStatus).text}
            </InvoiceField>
            {order.paidAt && <InvoiceField label="Ngày thanh toán">{formatDateTime(order.paidAt)}</InvoiceField>}
          </dl>
        </section>
        <section aria-labelledby="invoice-customer-heading">
          <h2 id="invoice-customer-heading">{orderType === 'PRODUCT' ? 'Thông tin khách hàng và giao hàng' : 'Thông tin khách hàng'}</h2>
          <dl>
            <InvoiceField label="Họ và tên">{order.customerName || '—'}</InvoiceField>
            <InvoiceField label="Số điện thoại">{order.customerPhone || '—'}</InvoiceField>
            <InvoiceField label="Email">{order.customerEmail || '—'}</InvoiceField>
            {orderType === 'PRODUCT' && <InvoiceField label="Địa chỉ giao hàng">{order.shippingAddress || '—'}</InvoiceField>}
          </dl>
        </section>
      </div>
      {order.notes && <p className="order-print-notes"><strong>Ghi chú</strong> {order.notes}</p>}
      <table>
        <caption>Chi tiết đơn hàng</caption>
        <thead>
          <tr>
            <th scope="col">STT</th>
            <th scope="col">Sản phẩm / Dịch vụ</th>
            <th scope="col">SL</th>
            <th scope="col">Đơn giá</th>
            <th scope="col">Giảm / đơn vị</th>
            <th scope="col">Thành tiền</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line, index) => (
            <tr key={line.id ?? index}>
              <td>{index + 1}</td>
              <td>
                {line.name}
                {line.details && <small>{line.details}</small>}
              </td>
              <td>{line.quantity}</td>
              <td>{money(line.price)}</td>
              <td>{money(line.discount ?? 0)}</td>
              <td>{money(line.price == null || line.quantity == null
                ? null
                : (Number(line.price) - Number(line.discount ?? 0)) * line.quantity)}</td>
            </tr>
          ))}
          {lines.length === 0 && <tr><td colSpan={6}>Chưa có dữ liệu mặt hàng.</td></tr>}
        </tbody>
      </table>
      <dl className="order-print-totals">
        <InvoiceField label="Tạm tính">{money(order.subTotal)}</InvoiceField>
        <InvoiceField label="Phí vận chuyển">{money(order.shippingFee)}</InvoiceField>
        <InvoiceField label="Giảm giá đơn hàng">{money(order.discountAmount)}</InvoiceField>
        <InvoiceField label="Tổng giá trị đơn hàng">{money(order.totalAmount)}</InvoiceField>
        {order.paidAmount != null && <InvoiceField label="Đã thu">{money(order.paidAmount)}</InvoiceField>}
        {Number(order.refundedAmount) > 0 && <InvoiceField label="Đã hoàn tiền">{money(order.refundedAmount)}</InvoiceField>}
      </dl>
    </article>
  );
}
