export const getMovementKindMeta = (kind) => {
  switch ((kind || '').toUpperCase()) {
    case 'RECEIPT':
      return { label: 'Nhập kho', variant: 'success' };
    case 'SUPPLIER_RETURN':
      return { label: 'Trả hàng NCC', variant: 'warning' };
    case 'PAYMENT':
      return { label: 'Thanh toán NCC', variant: 'info' };
    case 'SUPPLIER_REFUND':
      return { label: 'NCC hoàn tiền', variant: 'primary' };
    default:
      return { label: kind || 'Chứng từ', variant: 'neutral' };
  }
};

export const computePoNetReceivedQty = (po) => {
  if (!po) return 0;
  const received = Number(po.receivedQuantity ?? 0);
  const returned = Number(po.returnedQuantity ?? 0);
  return Math.max(0, received - returned);
};

export const computePoPayable = (po) => {
  if (!po) return 0;
  const receivedVal = Number(po.receivedAmount ?? 0);
  const paid = Number(po.paidAmount ?? 0);
  return receivedVal - paid;
};

export const formatLinePreview = (lines = []) => {
  if (!Array.isArray(lines) || lines.length === 0) return null;
  const first = lines[0];
  const label = first.productName || first.sku || (first.variantId ? `SKU #${first.variantId}` : `Dòng #${first.itemId}`);
  if (lines.length === 1) return label;
  return `${label} (+${lines.length - 1} dòng)`;
};

