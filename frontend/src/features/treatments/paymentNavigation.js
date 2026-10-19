export const openTreatmentPayment = order => {
  const id = order.orderId || order.id;
  const stored = { ...order, id, orderNumber: order.orderNumber || order.orderCode };
  sessionStorage.setItem('last_created_order', JSON.stringify(stored));
  window.location.hash = `/order-success?${new URLSearchParams({ id: String(id), code: stored.orderNumber || '', method: order.paymentMethod || 'BANK', amount: String(order.totalAmount || 0) })}`;
};
