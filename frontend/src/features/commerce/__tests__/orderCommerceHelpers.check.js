import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  buildOrderItemMap,
  formatReturnLineLabel,
  hasOrderReturnOrRefundAttention,
  shouldShowOrderCommerceButton,
} from '../orderCommerceHelpers.js';
import {
  buildCsvContent,
  computeOrderCollectedCod,
  computeOrderRemittedCod,
  computeOrderUnsettledCod,
  getCodReconciliationBadge,
  resolveChannelDrilldown,
} from '../orderReconciliationHelpers.js';

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
assert.equal(shouldShowOrderCommerceButton({ status: 'PENDING', paymentStatus: 'PENDING', paymentMethod: 'COD' }), false);
assert.equal(
  shouldShowOrderCommerceButton({
    status: 'PENDING',
    paymentStatus: 'PAID',
    paymentMethod: 'BANK',
    paidAmount: 260000,
    totalAmount: 260000,
  }),
  false
);
assert.equal(shouldShowOrderCommerceButton({ status: 'CONFIRMED', paymentStatus: 'PENDING', paymentMethod: 'COD' }), false);
assert.equal(
  shouldShowOrderCommerceButton({
    status: 'PROCESSING',
    paymentStatus: 'PAID',
    paymentMethod: 'BANK',
    paidAmount: 260000,
    totalAmount: 260000,
  }),
  false
);
assert.equal(shouldShowOrderCommerceButton({ status: 'SHIPPED', paymentStatus: 'PENDING', paymentMethod: 'COD' }), false);
assert.equal(shouldShowOrderCommerceButton({ status: 'CANCELLED', paymentStatus: 'PENDING', paymentMethod: 'COD' }), false);
assert.equal(
  shouldShowOrderCommerceButton({ status: 'PENDING', paymentStatus: 'PAID', paidAmount: 300000, totalAmount: 260000 }),
  true
);
assert.equal(
  shouldShowOrderCommerceButton({ status: 'CANCELLED', paymentStatus: 'REFUNDED', refundedAmount: 260000 }),
  true
);

const ordersPageSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../pages/OrdersPage.jsx'),
  'utf8'
);
assert.equal(ordersPageSource.includes('CommerceOperationsPanel'), false);
assert.equal(ordersPageSource.includes('PaymentExceptionPanel'), false);
assert.equal(ordersPageSource.includes('OrderCommerceModal'), true);
assert.equal(ordersPageSource.includes('OrderReconciliationPanel'), true);
assert.equal(ordersPageSource.includes('COMMERCE_REALLOCATE'), true);

// COD reconciliation helpers
assert.equal(
  computeOrderUnsettledCod({ paymentMethod: 'COD', status: 'DELIVERED', shipperCodCollected: 350000, shipperCodRemitted: 100000 }),
  250000
);
assert.equal(
  computeOrderUnsettledCod({ paymentMethod: 'COD', status: 'DELIVERED', totalAmount: 200000, paidAmount: 0 }),
  200000
);
assert.equal(
  computeOrderCollectedCod({ paymentMethod: 'COD', status: 'DELIVERED', totalAmount: 200000, paidAmount: 0 }),
  200000
);
assert.equal(
  computeOrderRemittedCod({ paymentMethod: 'COD', status: 'DELIVERED', shipperCodCollected: 350000, shipperCodRemitted: 100000 }),
  100000
);
assert.equal(
  getCodReconciliationBadge({ paymentMethod: 'COD', status: 'DELIVERED', shipperCodCollected: 350000, shipperCodRemitted: 350000 }).text,
  'Đã đối soát đủ'
);
assert.equal(
  getCodReconciliationBadge({ paymentMethod: 'COD', status: 'SHIPPED' }).text,
  'Đang giao COD'
);
assert.ok(buildCsvContent(['A', 'B'], [['x,y', 'z']]).includes('"x,y",z'));

// Channel summary drilldown resolution ("Xem đơn" / "Đối soát kênh này")
assert.deepEqual(
  resolveChannelDrilldown({
    shipperId: 17,
    carrierChannel: false,
    pendingOrdersCount: 0,
    settledOrdersCount: 3,
    inTransitOrdersCount: 2,
    remittedAmount: 10126050,
    pendingAmount: 0,
    pendingPickupReturnsCount: 0,
    inCustodyReturnsCount: 0,
  }),
  { subTab: 'orders', codState: 'ALL', channelFilter: 'SHIPPER:17' }
);
assert.deepEqual(
  resolveChannelDrilldown({
    shipperId: 17,
    carrierChannel: false,
    pendingOrdersCount: 2,
    pendingAmount: 500000,
  }),
  { subTab: 'orders', codState: 'PENDING', channelFilter: 'SHIPPER:17' }
);
assert.deepEqual(
  resolveChannelDrilldown({
    shipperId: 17,
    carrierChannel: false,
    pendingOrdersCount: 0,
    settledOrdersCount: 0,
    inTransitOrdersCount: 2,
    remittedAmount: 0,
    pendingAmount: 0,
  }),
  { subTab: 'orders', codState: 'IN_TRANSIT', channelFilter: 'SHIPPER:17' }
);
assert.deepEqual(
  resolveChannelDrilldown({
    shipperId: 17,
    carrierChannel: false,
    pendingOrdersCount: 0,
    settledOrdersCount: 0,
    inTransitOrdersCount: 0,
    remittedAmount: 0,
    pendingPickupReturnsCount: 1,
  }),
  { subTab: 'returns', codState: 'ALL', channelFilter: 'SHIPPER:17' }
);
assert.deepEqual(
  resolveChannelDrilldown({
    carrierChannel: true,
    pendingOrdersCount: 0,
    settledOrdersCount: 1,
    remittedAmount: 396000,
  }),
  { subTab: 'orders', codState: 'ALL', channelFilter: 'CARRIER' }
);

const endpointsSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../shared/api/endpoints.js'),
  'utf8'
);
assert.equal(endpointsSource.includes('COMMERCE_RETURN_LIST'), true);
assert.equal(endpointsSource.includes('COMMERCE_RETURN_ASSIGN_PICKUP'), true);

const modalSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../OrderCommerceModal.jsx'),
  'utf8'
);
assert.equal(modalSource.includes('COMMERCE_RETURN_ASSIGN_PICKUP'), true);
assert.equal(modalSource.includes('SHIPPER_PICKUP'), true);
assert.equal(modalSource.includes('pickupProofUrl'), true);

const reconciliationPanelSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../OrderReconciliationPanel.jsx'),
  'utf8'
);
assert.equal(reconciliationPanelSource.includes('inCustodyReturnsCount'), true);
assert.equal(reconciliationPanelSource.includes('pendingPickupReturnsCount'), true);
assert.equal(reconciliationPanelSource.includes('COMMERCE_RETURN_LIST'), true);

const workspaceSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../customer/components/account/OrderWorkspace.jsx'),
  'utf8'
);
assert.equal(workspaceSource.includes('SHIPPER_PICKUP'), true);
assert.equal(workspaceSource.includes('pickupProofUrl'), true);

const adminUiCssSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../app/styles/admin-ui.css'),
  'utf8'
);
assert.equal(adminUiCssSource.includes(':where(.admin-shell, .admin-auth) .badge'), true);
assert.equal(adminUiCssSource.includes('color: #111827;'), true);

console.log('✓ All order commerce helper, reconciliation helper & OrdersPage checks passed.');

