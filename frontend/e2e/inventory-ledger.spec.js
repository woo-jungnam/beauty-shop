import { test, expect } from '@playwright/test';

const admin = { id: 1, username: 'administrator', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: ['INVENTORY'] };

const sampleTransaction = {
  id: 1,
  stockId: 10,
  warehouseId: 20,
  warehouseName: 'Kho Tổng TP.HCM',
  productVariantId: 30,
  sku: 'SKU-SERUM-30',
  productName: 'Serum Dưỡng Ẩm Chuyên Sâu',
  variantName: 'Chai 30ml',
  batchCode: 'LOT-TEST-99',
  expirationDate: '2028-12-31',
  location: 'Kệ A-01',
  type: 'RECEIPT',
  quantity: 50,
  quantityBefore: 0,
  quantityAfter: 50,
  unitCost: 150000,
  referenceType: 'PURCHASE_ORDER',
  referenceId: 'PO-TEST-001',
  note: 'Nhập hàng theo đơn mua đã duyệt',
  createdBy: 'admin_tester',
  occurredAt: '2026-10-22T08:30:00Z',
};

test.describe('Inventory Ledger and Transaction Detail', () => {
  test('displays correct transaction types, signed quantities, before-after stock, and opens full detail modal', async ({ page }) => {
    const calls = [];
    await page.addInitScript(user => {
      localStorage.setItem('beautyshop_admin_token', 'test-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
    }, admin);

    await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
    await page.route('**/api/v1/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname;
      calls.push({ path, query: Object.fromEntries(url.searchParams.entries()) });

      if (path === '/api/v1/auth/profile') {
        return route.fulfill({ json: { status: 200, success: true, data: admin } });
      }
      if (path === '/api/v1/admin/inventory/transactions/1') {
        return route.fulfill({ json: { status: 200, success: true, data: sampleTransaction } });
      }
      if (path === '/api/v1/admin/inventory/transactions') {
        return route.fulfill({
          json: {
            status: 200,
            success: true,
            data: {
              content: [sampleTransaction],
              page: 0,
              size: 15,
              totalElements: 1,
              totalPages: 1,
            },
          },
        });
      }
      if (path === '/api/v1/admin/inventory/low-stock' || path === '/api/v1/admin/inventory/expiring-soon') {
        return route.fulfill({ json: { status: 200, success: true, data: [] } });
      }
      if (path === '/api/v1/admin/warehouses') {
        return route.fulfill({ json: { status: 200, success: true, data: [{ id: 20, name: 'Kho Tổng TP.HCM' }] } });
      }
      if (path.startsWith('/api/v1/admin/inventory/recalls')) {
        return route.fulfill({ json: { status: 200, success: true, data: { content: [], totalPages: 1 } } });
      }
      return route.fulfill({ json: { status: 200, success: true, data: [] } });
    });

    await page.goto('/#/admin/inventory');

    // Tab "Sổ cái biến động kho" is active by default
    await expect(page.getByRole('button', { name: /Sổ cái biến động kho/ })).toBeVisible();

    // Verify row displays correctly
    const table = page.locator('.data-table');
    await expect(table.getByText('Nhập kho', { exact: true })).toBeVisible();
    await expect(table.getByText('+50', { exact: true })).toBeVisible();
    await expect(table.getByText('0 → 50', { exact: true })).toBeVisible();
    await expect(table.getByText('Serum Dưỡng Ẩm Chuyên Sâu')).toBeVisible();
    await expect(table.getByText(/Chai 30ml/)).toBeVisible();
    await expect(table.getByText('Kho Tổng TP.HCM')).toBeVisible();

    // Click "Chi tiết"
    await table.getByRole('button', { name: 'Chi tiết' }).first().click();

    // Modal should open
    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal.getByText('Chi tiết giao dịch kho #1')).toBeVisible();

    // Verify modal contents
    await expect(modal.getByText('admin_tester')).toBeVisible();
    await expect(modal.getByText('LOT-TEST-99')).toBeVisible();
    await expect(modal.getByText('Kệ A-01')).toBeVisible();
    await expect(modal.getByText('PO-TEST-001')).toBeVisible();
    await expect(modal.getByText('Nhập hàng theo đơn mua đã duyệt')).toBeVisible();

    // Close modal
    await modal.getByRole('button', { name: 'Đóng' }).click();
    await expect(modal).toHaveCount(0);

    // Test filter by stockId
    await page.getByLabel('Lọc theo ID lô hàng (Stock ID)').fill('10');
    await page.getByRole('button', { name: 'Lọc', exact: true }).click();
    await expect.poll(() => calls.some(c => c.path === '/api/v1/admin/inventory/transactions' && c.query.stockId === '10')).toBeTruthy();
  });
});
