import { test, expect } from '@playwright/test';

const admin = { id: 1, username: 'admin', fullName: 'Admin', roles: ['ROLE_ADMIN'], permissions: [] };

test('financial reports follow dashboard dates and refresh; receipt categories remain separate', async ({ page }) => {
  const calls = [];
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    calls.push({ path, from: url.searchParams.get('from'), to: url.searchParams.get('to') });
    let data = [];
    if (path === '/api/v1/auth/profile') data = admin;
    else if (path.endsWith('/dashboard/profitability')) {
      // Force old-period responses to finish after the selected-period response.
      if (url.searchParams.get('from') !== '2026-09-30T17:00:00.000Z') await new Promise(resolve => setTimeout(resolve, 400));
      data = { deliveredProductNetSales: 800, knownProductCost: 100, productGrossProfit: 700 };
    } else if (path.endsWith('/dashboard/spa-performance')) data = { services: [], performedItemsMissingTime: 0, excludedLegacyItems: 0 };
    else if (path.endsWith('/dashboard/expenses') || path.endsWith('/admin/orders')) data = { content: [], totalPages: 1 };
    else if (path.endsWith('/dashboard/spa-occupancy')) data = { scheduledMinutes: 0, occupancyRatePercent: 0 };
    else if (path.endsWith('/dashboard')) data = { revenue: 1000, productRevenue: 300, spaRevenue: 500, bankAmount: 700, cashAmount: 100, codAmount: 0, manualAmount: 200, receiptCount: 4, totalOrders: 2, productPaidOrders: 1, spaPaidOrders: 1 };
    return route.fulfill({ json: { success: true, data } }).catch(() => {});
  });
  await page.goto('/#/admin');
  await expect(page.getByLabel('Từ ngày', { exact: true })).toBeVisible();
  await page.getByLabel('Từ ngày', { exact: true }).fill('2026-10-01');
  await page.getByLabel('Đến ngày', { exact: true }).fill('2026-10-31');
  await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  const reports = ['/api/v1/admin/dashboard/profitability', '/api/v1/admin/dashboard/spa-performance'];
  for (const path of reports) {
    await expect.poll(() => calls.filter(c => c.path === path).at(-1)?.from).toBe('2026-09-30T17:00:00.000Z');
    expect(calls.filter(c => c.path === path).at(-1)?.to).toBe('2026-10-31T17:00:00.000Z');
  }
  await page.getByText('Lãi gộp mỹ phẩm, giá vốn và sổ chi phí', { exact: true }).click();
  await page.getByText('Doanh thu đã thực hiện và vật tư theo dịch vụ Spa', { exact: true }).click();
  await expect(page.getByLabel('Giao hàng từ ngày')).toHaveValue('2026-10-01');
  await expect(page.getByLabel('Hoàn thành từ ngày')).toHaveValue('2026-10-01');
  const before = calls.filter(c => reports.includes(c.path)).length;
  await page.getByRole('button', { name: 'Làm mới dữ liệu tổng quan' }).click();
  await expect.poll(() => calls.filter(c => reports.includes(c.path)).length).toBeGreaterThanOrEqual(before + 2);
  for (const path of reports) expect(calls.filter(c => c.path === path).at(-1)?.to).toBe('2026-10-31T17:00:00.000Z');
  await page.locator('.atelier-metrics').getByText('Tổng tiền vào', { exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Xác nhận thủ công', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Tiền vào chưa phân loại', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Xác nhận thủ công', { exact: true }).locator('..')).toContainText('200');
  await expect(dialog.getByText('Tiền vào chưa phân loại', { exact: true }).locator('..')).toContainText('200');
});
