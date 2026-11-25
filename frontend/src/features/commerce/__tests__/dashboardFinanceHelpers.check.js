import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  calcPercent,
  exclusiveEndDate,
  filterAndSortSpaServices,
  formatSpaDuration,
  getExpenseCategoryLabel,
  isInstantInRange,
  summarizeProfitability,
  summarizeSpaPerformance,
} from '../dashboardFinanceHelpers.js';

// 1. Category Vietnamese translation
assert.equal(getExpenseCategoryLabel('OPERATING'), 'Vận hành');
assert.equal(getExpenseCategoryLabel('RENT'), 'Mặt bằng');
assert.equal(getExpenseCategoryLabel('PAYMENT_FEE'), 'Phí thanh toán');

// 2. Exclusive date helper and range containment in Asia/Ho_Chi_Minh (+07:00)
assert.equal(exclusiveEndDate('2026-10-31'), '2026-11-01');
assert.equal(isInstantInRange('2026-10-31T16:59:59.000Z', '2026-10-01', '2026-11-01'), true);
assert.equal(isInstantInRange('2026-10-31T17:00:00.000Z', '2026-10-01', '2026-11-01'), false);

// 3. Profitability summary with backend expenseBreakdown + gross margin %
const profitSummary = summarizeProfitability({
  deliveredProductNetSales: 30127050,
  knownProductCost: 12840800,
  allocationsMissingCost: 0,
  legacyDeliveriesMissingTime: 1,
  productGrossProfit: 17286250,
  operatingExpenses: 2500000,
  serviceConsumableCost: 500000,
  supplierPayments: 10000000,
  supplierRecoveries: 2000000,
  unassignedReceiptRefunds: 0,
  expenseBreakdown: [
    { category: 'RENT', amount: 2000000, count: 1 },
    { category: 'OPERATING', amount: 500000, count: 2 },
  ],
});
assert.equal(profitSummary.grossMarginPercent, 57.4);
assert.equal(profitSummary.periodOutflowTotal, 3000000);
assert.equal(profitSummary.supplierNetOutflow, 8000000);
assert.equal(profitSummary.breakdown[0].label, 'Mặt bằng');
assert.equal(profitSummary.breakdown[0].sharePercent, 80);

// 4. Null gross profit keeps margin null when historical costs missing
const missingCostSummary = summarizeProfitability({
  deliveredProductNetSales: 1000000,
  knownProductCost: 400000,
  allocationsMissingCost: 2,
  productGrossProfit: null,
});
assert.equal(missingCostSummary.grossProfit, null);
assert.equal(missingCostSummary.grossMarginPercent, null);

// 5. Spa duration and performance summary
assert.equal(formatSpaDuration(3, 0), '0 phút (< 1p/thao tác)');
assert.equal(formatSpaDuration(3, 135), '135 phút');
assert.equal(calcPercent(900000, 1325000), 67.9);

const spaSummary = summarizeSpaPerformance({
  performedItemsMissingTime: 2,
  excludedLegacyItems: 4,
  services: [
    {
      serviceId: 1,
      serviceName: 'Aqua Peel',
      performedItems: 3,
      performedMinutes: 0,
      visitInvoiceRevenue: 900000,
      ordinaryPackageRevenue: 0,
      treatmentRevenue: 425000,
      earnedRevenue: 1325000,
      unbilledSnapshotValue: 0,
      unbilledItems: 0,
      missingRevenueItems: 0,
      recordedMaterialCost: 0,
      missingPlannedMaterialItems: 0,
      untrackedMaterialItems: 3,
      materialWaiverItems: 0,
      contributionAfterRecordedMaterials: null,
    },
    {
      serviceId: 2,
      serviceName: 'Căng bóng Vitamin C',
      performedItems: 1,
      performedMinutes: 45,
      visitInvoiceRevenue: 0,
      ordinaryPackageRevenue: 500000,
      treatmentRevenue: 250000,
      earnedRevenue: 750000,
      unbilledSnapshotValue: 200000,
      unbilledItems: 1,
      missingRevenueItems: 0,
      recordedMaterialCost: 50000,
      missingPlannedMaterialItems: 0,
      untrackedMaterialItems: 0,
      materialWaiverItems: 0,
      contributionAfterRecordedMaterials: null,
    },
    {
      serviceId: 3,
      serviceName: 'Massage cổ vai gáy',
      performedItems: 2,
      performedMinutes: 90,
      visitInvoiceRevenue: 600000,
      ordinaryPackageRevenue: 0,
      treatmentRevenue: 0,
      earnedRevenue: 600000,
      unbilledSnapshotValue: 0,
      unbilledItems: 0,
      missingRevenueItems: 0,
      recordedMaterialCost: 40000,
      missingPlannedMaterialItems: 0,
      untrackedMaterialItems: 0,
      materialWaiverItems: 0,
      contributionAfterRecordedMaterials: 560000,
    },
  ],
});

assert.equal(spaSummary.serviceCount, 3);
assert.equal(spaSummary.performedItems, 6);
assert.equal(spaSummary.performedMinutes, 135);
assert.equal(spaSummary.earnedRevenue, 2675000);
assert.equal(spaSummary.recordedMaterialCost, 90000);
assert.equal(spaSummary.sufficientServiceCount, 1);
assert.equal(spaSummary.incompleteServiceCount, 2);
assert.equal(spaSummary.contributionKnownTotal, 560000);
assert.equal(spaSummary.evidenceGapCount, 4);

const gapOnly = filterAndSortSpaServices(
  [
    { serviceId: 1, serviceName: 'Aqua Peel', earnedRevenue: 1325000, untrackedMaterialItems: 3 },
    { serviceId: 3, serviceName: 'Massage', earnedRevenue: 600000 },
  ],
  { evidenceFilter: 'GAPS' }
);
assert.equal(gapOnly.length, 1);
assert.equal(gapOnly[0].serviceId, 1);

// 6. Contract assertions for Playwright dashboard-financials.spec.js
const panelSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../DashboardFinancePanels.jsx'),
  'utf8'
);
assert.equal(panelSource.includes('Lãi gộp mỹ phẩm, giá vốn và sổ chi phí'), true);
assert.equal(panelSource.includes('Doanh thu đã thực hiện và vật tư theo dịch vụ Spa'), true);
assert.equal(panelSource.includes('label="Giao hàng từ ngày"'), true);
assert.equal(panelSource.includes('label="Hoàn thành từ ngày"'), true);
assert.equal(panelSource.includes('Đã lưu thành công.'), false);

console.log('✓ All dashboard finance panel helper and contract checks passed cleanly.');

