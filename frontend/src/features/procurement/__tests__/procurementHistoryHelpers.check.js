import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  computePoPayable,
  formatLinePreview,
  getMovementKindMeta,
} from '../procurementHistoryHelpers.js';

assert.deepEqual(getMovementKindMeta('RECEIPT'), { label: 'Nhập kho', variant: 'success' });
assert.deepEqual(getMovementKindMeta('SUPPLIER_RETURN'), { label: 'Trả hàng NCC', variant: 'warning' });
assert.deepEqual(getMovementKindMeta('PAYMENT'), { label: 'Thanh toán NCC', variant: 'info' });
assert.deepEqual(getMovementKindMeta('SUPPLIER_REFUND'), { label: 'NCC hoàn tiền', variant: 'primary' });

assert.equal(computePoPayable({ receivedAmount: 600000, paidAmount: 250000 }), 350000);
assert.equal(computePoPayable({ receivedAmount: 200000, paidAmount: 250000 }), -50000);

assert.equal(formatLinePreview([]), null);
assert.equal(
  formatLinePreview([{ productName: 'Kem dưỡng', quantity: 3 }]),
  'Kem dưỡng'
);
assert.equal(
  formatLinePreview([{ sku: 'KCN-01' }, { sku: 'SRM-02' }]),
  'KCN-01 (+1 dòng)'
);

const pageSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../pages/ProcurementPage.jsx'),
  'utf8'
);
assert.equal(pageSource.includes('ProcurementHistoryPanel'), true);
assert.equal(pageSource.includes("id: 'history'"), true);

const navSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../../../app/adminNavigation.js'),
  'utf8'
);
assert.equal(navSource.includes('{ id: "history", label: "Lịch sử nhập hàng" }'), true);

console.log('✓ All procurement history helpers & UI integration checks passed.');

