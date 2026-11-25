export const EXPENSE_CATEGORIES = [
  { value: 'OPERATING', label: 'Vận hành' },
  { value: 'SHIPPING', label: 'Vận chuyển' },
  { value: 'PAYMENT_FEE', label: 'Phí thanh toán' },
  { value: 'RENT', label: 'Mặt bằng' },
  { value: 'SALARY', label: 'Lương' },
  { value: 'COMMISSION', label: 'Hoa hồng' },
  { value: 'MARKETING', label: 'Tiếp thị' },
  { value: 'OTHER', label: 'Khác' },
];

const EXPENSE_CATEGORY_MAP = Object.fromEntries(
  EXPENSE_CATEGORIES.map((item) => [item.value, item.label])
);

export const getExpenseCategoryLabel = (category) => {
  const key = String(category || '').trim().toUpperCase();
  return EXPENSE_CATEGORY_MAP[key] || category || 'Khác';
};

export const exclusiveEndDate = (key) => {
  if (!key) return '';
  const date = new Date(`${key}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
};

export const reportParams = (from, to, extra = {}) => {
  const params = new URLSearchParams();
  if (from) params.set('from', new Date(`${from}T00:00:00+07:00`).toISOString());
  if (to) params.set('to', new Date(`${to}T00:00:00+07:00`).toISOString());
  for (const [key, value] of Object.entries(extra)) {
    if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
      params.set(key, String(value));
    }
  }
  return params;
};

export const calcPercent = (part, total) => {
  const p = Number(part || 0);
  const t = Number(total || 0);
  if (t <= 0 || p <= 0) return 0;
  return Math.min(100, Math.round((p / t) * 1000) / 10);
};

export const isInstantInRange = (isoString, fromDate, toExclusiveDate) => {
  if (!isoString || (!fromDate && !toExclusiveDate)) return true;
  const ts = Date.parse(isoString);
  if (Number.isNaN(ts)) return false;
  if (fromDate && ts < Date.parse(`${fromDate}T00:00:00+07:00`)) return false;
  if (toExclusiveDate && ts >= Date.parse(`${toExclusiveDate}T00:00:00+07:00`)) return false;
  return true;
};

export const summarizeProfitability = (report, loadedExpenses = []) => {
  if (!report) return null;
  const netSales = Number(report.deliveredProductNetSales || 0);
  const knownCost = Number(report.knownProductCost || 0);
  const grossProfit = report.productGrossProfit == null ? null : Number(report.productGrossProfit);
  const missingCostCount = Number(report.allocationsMissingCost || 0);
  const legacyMissingTime = Number(report.legacyDeliveriesMissingTime || 0);
  const operatingExpenses = Number(report.operatingExpenses || 0);
  const serviceConsumables = Number(report.serviceConsumableCost || 0);
  const supplierPayments = Number(report.supplierPayments || 0);
  const supplierRecoveries = Number(report.supplierRecoveries || 0);
  const unassignedRefunds = Number(report.unassignedReceiptRefunds || 0);

  const grossMarginPercent = grossProfit != null && netSales > 0
    ? Math.round((grossProfit / netSales) * 1000) / 10
    : null;
  const costRatioPercent = netSales > 0 ? Math.min(100, Math.round((knownCost / netSales) * 1000) / 10) : 0;
  const periodOutflowTotal = operatingExpenses + serviceConsumables;
  const supplierNetOutflow = supplierPayments - supplierRecoveries;

  let breakdown = Array.isArray(report.expenseBreakdown) ? report.expenseBreakdown : [];
  if (!breakdown.length && loadedExpenses.length > 0 && operatingExpenses > 0) {
    const grouped = new Map();
    for (const item of loadedExpenses) {
      const key = String(item?.category || 'OTHER').toUpperCase();
      const current = grouped.get(key) || { category: key, amount: 0, count: 0 };
      current.amount += Number(item?.amount || 0);
      current.count += 1;
      grouped.set(key, current);
    }
    breakdown = [...grouped.values()].sort((a, b) => Number(b.amount) - Number(a.amount));
  }

  return {
    netSales,
    knownCost,
    grossProfit,
    grossMarginPercent,
    costRatioPercent,
    missingCostCount,
    legacyMissingTime,
    operatingExpenses,
    serviceConsumables,
    periodOutflowTotal,
    supplierPayments,
    supplierRecoveries,
    supplierNetOutflow,
    unassignedRefunds,
    breakdown: breakdown.map((entry) => ({
      category: entry.category,
      label: getExpenseCategoryLabel(entry.category),
      amount: Number(entry.amount || 0),
      count: Number(entry.count || 0),
      sharePercent: calcPercent(entry.amount, Math.max(operatingExpenses, 1)),
    })),
  };
};

export const formatSpaDuration = (items, minutes) => {
  const count = Number(items || 0);
  const mins = Number(minutes || 0);
  if (count <= 0) return '0 phút';
  if (mins <= 0) return '0 phút (< 1p/thao tác)';
  return `${new Intl.NumberFormat('vi-VN').format(mins)} phút`;
};

export const summarizeSpaPerformance = (report) => {
  if (!report) return null;
  const services = Array.isArray(report.services) ? report.services : [];
  const totals = {
    serviceCount: services.length,
    sufficientServiceCount: 0,
    incompleteServiceCount: 0,
    performedItems: 0,
    performedMinutes: 0,
    visitInvoiceRevenue: 0,
    ordinaryPackageRevenue: 0,
    treatmentRevenue: 0,
    earnedRevenue: 0,
    recordedMaterialCost: 0,
    contributionKnownTotal: 0,
    unbilledItems: 0,
    unbilledSnapshotValue: 0,
    missingRevenueItems: 0,
    missingPlannedMaterialItems: 0,
    untrackedMaterialItems: 0,
    materialWaiverItems: 0,
    evidenceGapCount: 0,
    performedItemsMissingTime: Number(report.performedItemsMissingTime || 0),
    excludedLegacyItems: Number(report.excludedLegacyItems || 0),
  };

  let maxScaleValue = 0;
  for (const row of services) {
    const earned = Number(row.earnedRevenue || 0);
    const unbilledVal = Number(row.unbilledSnapshotValue || 0);
    maxScaleValue = Math.max(maxScaleValue, earned + unbilledVal);

    totals.performedItems += Number(row.performedItems || 0);
    totals.performedMinutes += Number(row.performedMinutes || 0);
    totals.visitInvoiceRevenue += Number(row.visitInvoiceRevenue || 0);
    totals.ordinaryPackageRevenue += Number(row.ordinaryPackageRevenue || 0);
    totals.treatmentRevenue += Number(row.treatmentRevenue || 0);
    totals.earnedRevenue += earned;
    totals.recordedMaterialCost += Number(row.recordedMaterialCost || 0);
    totals.unbilledItems += Number(row.unbilledItems || 0);
    totals.unbilledSnapshotValue += unbilledVal;
    totals.missingRevenueItems += Number(row.missingRevenueItems || 0);
    totals.missingPlannedMaterialItems += Number(row.missingPlannedMaterialItems || 0);
    totals.untrackedMaterialItems += Number(row.untrackedMaterialItems || 0);
    totals.materialWaiverItems += Number(row.materialWaiverItems || 0);

    if (row.contributionAfterRecordedMaterials == null) {
      totals.incompleteServiceCount += 1;
    } else {
      totals.sufficientServiceCount += 1;
      totals.contributionKnownTotal += Number(row.contributionAfterRecordedMaterials || 0);
    }
  }

  totals.evidenceGapCount =
    totals.unbilledItems +
    totals.missingRevenueItems +
    totals.missingPlannedMaterialItems +
    totals.untrackedMaterialItems;

  return {
    ...totals,
    maxScaleValue,
    materialRatioPercent: calcPercent(totals.recordedMaterialCost, totals.earnedRevenue),
  };
};

export const filterAndSortSpaServices = (services = [], { query = '', evidenceFilter = 'ALL', sortBy = 'REVENUE' } = {}) => {
  const normalizedQuery = String(query || '').trim().toLowerCase();
  const rows = services.filter((row) => {
    if (normalizedQuery && !String(row.serviceName || '').toLowerCase().includes(normalizedQuery)) {
      return false;
    }
    const gaps =
      Number(row.unbilledItems || 0) +
      Number(row.missingRevenueItems || 0) +
      Number(row.missingPlannedMaterialItems || 0) +
      Number(row.untrackedMaterialItems || 0);
    if (evidenceFilter === 'GAPS') return gaps > 0;
    if (evidenceFilter === 'COMPLETE') return gaps === 0;
    return true;
  });

  rows.sort((a, b) => {
    if (sortBy === 'ITEMS') {
      return Number(b.performedItems || 0) - Number(a.performedItems || 0);
    }
    if (sortBy === 'GAPS') {
      const gapA = Number(a.unbilledItems || 0) + Number(a.missingRevenueItems || 0) + Number(a.missingPlannedMaterialItems || 0) + Number(a.untrackedMaterialItems || 0);
      const gapB = Number(b.unbilledItems || 0) + Number(b.missingRevenueItems || 0) + Number(b.missingPlannedMaterialItems || 0) + Number(b.untrackedMaterialItems || 0);
      return gapB - gapA || Number(b.earnedRevenue || 0) - Number(a.earnedRevenue || 0);
    }
    return Number(b.earnedRevenue || 0) + Number(b.unbilledSnapshotValue || 0) - (Number(a.earnedRevenue || 0) + Number(a.unbilledSnapshotValue || 0));
  });

  return rows;
};

