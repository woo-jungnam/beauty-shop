import { getOrderType } from '../../shared/utils/formatters.js';

export const WHOLE_ORDER_RETURN_STATUSES = [
  'CANCELLED',
  'RETURNED',
  'RETURN_TO_SENDER',
  'RETURNING_TO_SENDER',
  'DELIVERY_FAILED',
];

export const buildOrderItemMap = (items = []) => {
  const map = {};
  for (const item of items || []) {
    if (item?.id != null) {
      map[String(item.id)] = item;
    }
  }
  return map;
};

export const formatReturnLineLabel = (line, itemMap = {}) => {
  const item = itemMap[String(line?.orderItemId)];
  const qty = Number(line?.quantity || 0);
  if (!item) {
    return `Mặt hàng #${line?.orderItemId ?? '?'} — SL trả: ${qty}`;
  }
  const variantText = item.variantName ? ` (${item.variantName})` : '';
  const skuText = item.sku ? ` · SKU: ${item.sku}` : '';
  return `${item.productName}${variantText}${skuText} — Trả ${qty}/${item.quantity}`;
};

export const hasOrderReturnOrRefundAttention = (order) => {
  if (!order) return false;
  if (Number(order.activeReturnCount || 0) > 0) return true;
  if (order.paymentStatus === 'REFUND_PENDING') return true;
  if (
    WHOLE_ORDER_RETURN_STATUSES.includes(order.status) &&
    ['PAID', 'PARTIALLY_REFUNDED'].includes(order.paymentStatus)
  ) {
    return true;
  }
  const paid = Number(order.paidAmount || 0);
  const refunded = Number(order.refundedAmount || 0);
  const total = Number(order.totalAmount || 0);
  if (paid - refunded > total && total >= 0) {
    return true;
  }
  if (
    order.paymentMethod === 'COD' &&
    Number(order.shipperCodCollected || 0) > Number(order.shipperCodRemitted || 0)
  ) {
    return true;
  }
  return false;
};

export const shouldShowOrderCommerceButton = (order) => {
  if (!order) return false;
  if (getOrderType(order) === 'PRODUCT') return true;
  if (Number(order.returnRequestCount || 0) > 0) return true;
  return hasOrderReturnOrRefundAttention(order);
};

