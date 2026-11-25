export const computeOrderUnsettledCod = (order) => {
  if (!order || order.paymentMethod !== 'COD') return 0;
  if (order.unsettledCod != null && !Number.isNaN(Number(order.unsettledCod))) {
    return Math.max(0, Number(order.unsettledCod));
  }
  const collected = Number(order.shipperCodCollected || 0);
  const remitted = Number(order.shipperCodRemitted || 0);
  if (collected > 0 && ['DELIVERED', 'RETURNED'].includes(order.status)) {
    return Math.max(0, collected - remitted);
  }
  if (order.status === 'DELIVERED') {
    return Math.max(0, Number(order.totalAmount || 0) - Number(order.paidAmount || 0));
  }
  return 0;
};

export const computeOrderCollectedCod = (order) => {
  if (!order || order.paymentMethod !== 'COD') return 0;
  const collected = Number(order.shipperCodCollected || 0);
  if (collected > 0) return collected;
  if (order.status === 'DELIVERED') return Math.max(0, Number(order.totalAmount || 0));
  return 0;
};

export const computeOrderRemittedCod = (order) => {
  if (!order || order.paymentMethod !== 'COD') return 0;
  const collected = computeOrderCollectedCod(order);
  if (collected <= 0) return 0;
  if (Number(order.shipperCodCollected || 0) > 0) {
    return Math.min(collected, Math.max(0, Number(order.shipperCodRemitted || 0)));
  }
  if (order.status === 'DELIVERED') {
    return Math.min(collected, Math.max(0, Number(order.paidAmount || 0)));
  }
  return 0;
};

export const getCodReconciliationBadge = (order) => {
  if (!order || order.paymentMethod !== 'COD' || order.appointmentId || order.servicePackageId) {
    return { text: 'Không áp dụng', variant: 'neutral' };
  }
  const unsettled = computeOrderUnsettledCod(order);
  if (unsettled > 0) {
    return { text: 'Chờ bàn giao COD', variant: 'warning' };
  }
  if (['SHIPPED', 'DELIVERY_FAILED', 'RETURNING_TO_SENDER'].includes(order.status)) {
    return { text: 'Đang giao COD', variant: 'info' };
  }
  if (['DELIVERED', 'RETURNED'].includes(order.status) && computeOrderCollectedCod(order) > 0) {
    return { text: 'Đã đối soát đủ', variant: 'success' };
  }
  return { text: 'Chờ thu hộ', variant: 'neutral' };
};

export const createBatchSettlementRef = (now = Date.now()) => {
  const stamp = new Date(now).toISOString().slice(2, 10).replace(/-/g, '');
  const suffix = String(now).slice(-5);
  return `BATCH-${stamp}-${suffix}`;
};

export const csvEscape = (value) => {
  const str = value == null ? '' : String(value);
  if (/[",\n\r]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
};

export const buildCsvContent = (headers, rows) => {
  const lines = [
    headers.map(csvEscape).join(','),
    ...rows.map((row) => row.map(csvEscape).join(',')),
  ];
  return `\uFEFF${lines.join('\r\n')}`;
};

