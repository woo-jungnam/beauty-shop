import assert from 'node:assert/strict';

// Test normalizeTimeline logic with productRevenue and spaRevenue
function normalizeTimeline(records, period, range) {
  if (!Array.isArray(records)) return [];
  const map = new Map(records.map((record) => [record.period, record]));
  const points = [];
  const cursor = new Date(`${range.from}T12:00:00Z`);
  const end = new Date(`${range.to}T12:00:00Z`);
  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, period === 'month' ? 7 : 10);
    const record = map.get(key);
    points.push({
      key,
      revenue: record ? Number(record.revenue || 0) : 0,
      productRevenue: record ? Number(record.productRevenue || 0) : 0,
      spaRevenue: record ? Number(record.spaRevenue || 0) : 0,
      orderCount: record ? Number(record.orderCount || 0) : 0,
      refundedAmount: record ? Number(record.refundedAmount || 0) : 0,
    });
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return points;
}

const mockRecords = [
  {
    period: '2026-10-01',
    revenue: 5000000,
    productRevenue: 3000000,
    spaRevenue: 2000000,
    orderCount: 10,
    refundedAmount: 100000,
  },
  {
    period: '2026-10-02',
    revenue: 12000000,
    productRevenue: 2000000,
    spaRevenue: 10000000,
    orderCount: 4,
    refundedAmount: 0,
  },
];

const range = { from: '2026-10-01', to: '2026-10-02' };
const points = normalizeTimeline(mockRecords, 'day', range);

assert.equal(points.length, 2);

// Check day 1
assert.equal(points[0].revenue, 5000000);
assert.equal(points[0].productRevenue, 3000000);
assert.equal(points[0].spaRevenue, 2000000);
assert.equal(points[0].productRevenue + points[0].spaRevenue, points[0].revenue);

// Check day 2
assert.equal(points[1].revenue, 12000000);
assert.equal(points[1].productRevenue, 2000000);
assert.equal(points[1].spaRevenue, 10000000);

console.log('✓ All dashboard revenue breakdown tests passed successfully.');
