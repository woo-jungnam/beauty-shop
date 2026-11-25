import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  buildOrderItemMap,
  formatReturnLineLabel,
  hasOrderReturnOrRefundAttention,
  shouldShowOrderCommerceButton,
} from '../orderCommerceHelpers.js';

const itemMap = buildOrderItemMap([
  {
    id: 59,
    productName: 'Nước Tẩy Trang Bioderma',
    variantName: '500ml Nắp Hồng',
    sku: 'BIO-500',
    quantity: 2,
  },
]);

assert.equal(
  formatReturnLineLabel({ orderItemId: 59, quantity: 1 }, itemMap),
  'Nước Tẩy Trang Bioderma (500ml Nắp Hồng) · SKU: BIO-500 — Trả 1/2'
);
assert.equal(formatReturnLineLabel({ orderItemId: 999, quantity: 2 }, itemMap), 'Mặt hàng #999 — SL trả: 2');

assert.equal(hasOrderReturnOrRefundAttention({ activeReturnCount: 1, status: 'DELIVERED', paymentStatus: 'PAID' }), true);
assert.equal(hasOrderReturnOrRefundAttention({ activeReturnCount: 0, status: 'CANCELLED', paymentStatus: 'PAID' }), true);
assert.equal(hasOrderReturnOrRefundAttention({ activeReturnCount: 0, status: 'CANCELLED', paymentStatus: 'REFUNDED' }), false);
assert.equal(
  hasOrderReturnOrRefundAttention({ activeReturnCount: 0, status: 'DELIVERED', paymentStatus: 'PAID', paidAmount: 500000, totalAmount: 400000, refundedAmount: 0 }),
  true
);
assert.equal(
  hasOrderReturnOrRefundAttention({ paymentMethod: 'COD', shipperCodCollected: 418000, shipperCodRemitted: 0 }),
  true
);

assert.equal(shouldShowOrderCommerceButton({ servicePackageId: 10, status: 'COMPLETED', paymentStatus: 'PAID' }), false);
assert.equal(shouldShowOrderCommerceButton({ servicePackageId: 10, status: 'CANCELLED', paymentStatus: 'PAID' }), true);
assert.equal(shouldShowOrderCommerceButton({ status: 'DELIVERED', paymentStatus: 'PAID' }), true);

const ordersPageSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../pages/OrdersPage.jsx'),
  'utf8'
);
assert.equal(ordersPageSource.includes('CommerceOperationsPanel'), false);
assert.equal(ordersPageSource.includes('PaymentExceptionPanel'), false);
assert.equal(ordersPageSource.includes('OrderCommerceModal'), true);

console.log('✓ All order commerce helper & OrdersPage checks passed.');

