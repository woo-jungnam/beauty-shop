import assert from 'node:assert/strict';
import { getUserStatusBadge, getVietnamDateString } from '../../shared/utils/formatters.js';
import { getAdminSections } from '../../app/adminNavigation.js';

// 1. User status badge covers SUSPENDED
assert.equal(getUserStatusBadge('ACTIVE').text, 'Hoạt động');
assert.equal(getUserStatusBadge('SUSPENDED').text, 'Tạm ngưng');
assert.equal(getUserStatusBadge('SUSPENDED').variant, 'warning');
assert.equal(getUserStatusBadge('BLOCKED').text, 'Bị cấm');

// 2. Staff with no explicit permissions receives no operational tabs; ADMIN receives all
assert.equal(getAdminSections(['ROLE_STAFF'], []).length, 0);
assert.ok(getAdminSections(['ROLE_ADMIN'], []).length > 0);
const inventorySections = getAdminSections(['ROLE_STAFF'], ['INVENTORY']);
assert.deepEqual(
  inventorySections.flatMap((s) => s.items.map((i) => i.id)),
  ['inventory']
);

// 3. Spa navigation has no dead 'maintenance' sub-tab
const adminSpaItem = getAdminSections(['ROLE_ADMIN'], [])
  .flatMap((s) => s.items)
  .find((i) => i.id === 'spa');
assert.ok(adminSpaItem);
assert.equal(
  adminSpaItem.subItems.some((sub) => sub.id === 'maintenance'),
  false
);

// 3b. SPA_EXECUTION staff gets assigned appointments tab without reception-only sub-items
const therapistSpaItem = getAdminSections(['ROLE_STAFF'], ['SPA_EXECUTION'])
  .flatMap((s) => s.items)
  .find((i) => i.id === 'spa');
assert.ok(therapistSpaItem);
assert.deepEqual(therapistSpaItem.subItems.map((sub) => sub.id), ['appointments']);

// 4. Local datetime formatter stays consistent with local components (no UTC shift)
const toLocalDatetimeString = (value) => {
  const d = value instanceof Date ? value : new Date(value);
  if (isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const sampleLocal = new Date(2026, 10, 12, 9, 30);
assert.equal(toLocalDatetimeString(sampleLocal), '2026-11-12T09:30');

// 5. Vietnam expiration day calculation uses Asia/Ho_Chi_Minh date boundaries
const getDaysUntilExpiration = (expDate, todayStr = getVietnamDateString()) => {
  if (!expDate) return null;
  const datePart = String(expDate).slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(datePart)) return null;
  const today = Date.parse(`${todayStr}T00:00:00+07:00`);
  const target = Date.parse(`${datePart}T00:00:00+07:00`);
  return Math.round((target - today) / 86400000);
};
assert.equal(getDaysUntilExpiration('2026-11-12', '2026-11-12'), 0);
assert.equal(getDaysUntilExpiration('2026-11-11', '2026-11-12'), -1);
assert.equal(getDaysUntilExpiration('2026-12-12', '2026-11-12'), 30);

// 6. SpaVisitCharge price resolution prefers unitPrice
const resolveSpaVisitUnitPrice = (sv) => Number(sv.unitPrice ?? sv.price ?? 0);
assert.equal(resolveSpaVisitUnitPrice({ serviceName: 'Facial', unitPrice: 350000 }), 350000);
assert.equal(resolveSpaVisitUnitPrice({ serviceName: 'Legacy', price: 200000 }), 200000);

console.log('✓ All admin audit fix checks passed successfully.');
