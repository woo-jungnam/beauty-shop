import { test, expect } from '@playwright/test';

const admin = { id: 1, username: 'administrator', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: [] };
const ordersPath = '/api/v1/admin/procurement/orders';
const order = {
  id: 9, orderNumber: 'PO-DETAIL', supplierName: 'Nhà cung cấp A', warehouseId: 2,
  status: 'PARTIALLY_RECEIVED', totalAmount: 300000, note: 'Đơn nhập kiểm thử',
  createdAt: '2026-10-20T02:00:00Z', expectedDate: '2026-10-22', receivedAt: '2026-10-21T03:00:00Z',
  items: [{ id: 91, variantId: 12, productName: 'Serum kiểm thử', variantName: '30ml', sku: '000123',
    quantity: 3, receivedQuantity: 1, unitCost: 100000, batchCode: 'LOT-PLAN', expirationDate: '2099-01-01' }],
};

async function setup(page) {
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);
  const state = { detailFails: false, receiptFails: false, corruptReceipt: false, beforeDetail: null, calls: [] };
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    state.calls.push({ path, method: route.request().method() });
    let data = [], status = 200;
    if (path === '/api/v1/auth/profile') data = admin;
    else if (path === '/api/v1/admin/procurement/suppliers') data = { content: [], totalPages: 1 };
    else if (path === '/api/v1/admin/warehouses') data = [{ id: 2, name: 'Kho A' }];
    else if (path === ordersPath) data = { content: [order, { ...order, id: 10, orderNumber: 'PO-SECOND', status: 'DRAFT' }], totalPages: 1, totalElements: 2 };
    else if (path === `${ordersPath}/9`) {
      if (state.beforeDetail) await state.beforeDetail();
      status = state.detailFails ? 500 : 200;
      data = order;
    } else if (path === `${ordersPath}/10`) data = { ...order, id: 10, orderNumber: 'PO-SECOND', status: 'DRAFT', receivedAt: null,
      items: [{ ...order.items[0], id: 101, variantId: 99, productName: null, variantName: null, sku: null, receivedQuantity: 0, batchCode: '', expirationDate: null }] };
    else if (path.endsWith('/9/financials')) {
      status = state.receiptFails ? 500 : 200;
      data = { movements: [{ id: 1, kind: 'RECEIPT', reference: 'GR-001', amount: 100000, reason: 'Nhận đợt một',
        createdAt: '2026-10-21T03:00:00Z', linesJson: state.corruptReceipt ? 'broken' : JSON.stringify([{ itemId: 91, quantity: 1, batchCode: 'LOT-ACTUAL', expirationDate: '2099-06-30' }]) }] };
    } else if (path.endsWith('/financials')) data = { movements: [] };
    await route.fulfill({ status, json: { status, success: status === 200, data, message: status === 500 ? 'Lỗi kiểm thử' : '' } });
  });
  await page.goto('/#/admin/procurement');
  return state;
}

test('Chi tiết shows ordered goods and actual batches without changing stock', async ({ page }, testInfo) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const state = await setup(page);
  await page.getByRole('button', { name: 'Chi tiết phiếu PO-DETAIL', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'Chi tiết phiếu mua PO-DETAIL', exact: true })).toBeVisible();
  await expect(dialog.getByText('Nhà cung cấp A', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Kho A', { exact: true })).toBeVisible();
  const item = dialog.getByRole('row').filter({ hasText: 'LOT-PLAN' });
  await expect(item).toContainText('Serum kiểm thử'); await expect(item).toContainText('30ml');
  await expect(item).toContainText('000123'); await expect(item).toContainText('01/01/2099');
  await expect(item.getByRole('cell').nth(3)).toHaveText('3');
  await expect(item.getByRole('cell').nth(4)).toHaveText('1');
  await expect(item.getByRole('cell').nth(5)).toContainText('100.000');
  await expect(item.getByRole('cell').nth(6)).toContainText('300.000');
  const receipt = dialog.getByRole('region', { name: 'Đợt nhập GR-001' });
  await expect(receipt).toContainText('LOT-ACTUAL'); await expect(receipt).toContainText('30/06/2099');
  await expect(receipt.getByRole('row').nth(1).getByRole('cell').nth(3)).toHaveText('1');
  await expect(receipt.getByRole('row').nth(1).getByRole('cell').nth(5)).toContainText('100.000');
  await page.screenshot({ path: testInfo.outputPath('procurement-detail.png'), fullPage: true });
  await receipt.getByRole('table').scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath('procurement-detail-receipt.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(dialog.getByRole('heading', { name: 'Chi tiết phiếu mua PO-DETAIL', exact: true })).toBeVisible();
  expect(await dialog.evaluate(element => {
    const bounds = element.getBoundingClientRect();
    return bounds.left >= 0 && bounds.right <= window.innerWidth && element.scrollWidth <= element.clientWidth;
  })).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('procurement-detail-mobile.png'), fullPage: true });
  await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: 'Chi tiết phiếu PO-SECOND', exact: true }).click();
  await expect(dialog.getByText('Biến thể #99', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Không ghi mã lô', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Chưa có đợt nhập kho nào được ghi nhận.')).toBeVisible();
  await expect(dialog.getByText('LOT-ACTUAL')).toHaveCount(0);
  expect(state.calls.every(call => call.method === 'GET')).toBe(true);
  expect(errors).toEqual([]);
});

test('detail failure can retry; receipt failure does not hide order lines', async ({ page }) => {
  const state = await setup(page);
  state.detailFails = true;
  await page.getByRole('button', { name: 'Chi tiết phiếu PO-DETAIL', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('alert')).toContainText('Không tải được chi tiết');
  state.detailFails = false; state.receiptFails = true;
  await dialog.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(dialog.getByText('LOT-PLAN', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Không tải được lịch sử nhập thực tế.', { exact: false })).toBeVisible();
  await dialog.getByRole('button', { name: 'Đóng', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  state.receiptFails = false; state.corruptReceipt = true;
  await page.getByRole('button', { name: 'Chi tiết phiếu PO-DETAIL', exact: true }).click();
  await expect(dialog.getByText('Không đọc được dòng hàng', { exact: true })).toBeVisible();
  await expect(dialog.getByText('LOT-PLAN', { exact: true })).toBeVisible();
});

test('closing a loading detail discards its response when another order is opened', async ({ page }) => {
  const state = await setup(page);
  let release;
  state.beforeDetail = () => new Promise(resolve => { release = resolve; });
  await page.getByRole('button', { name: 'Chi tiết phiếu PO-DETAIL', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('status')).toContainText('Đang tải');
  await expect.poll(() => typeof release).toBe('function');
  await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: 'Chi tiết phiếu PO-SECOND', exact: true }).click();
  await expect(dialog.getByRole('heading', { name: 'Chi tiết phiếu mua PO-SECOND', exact: true })).toBeVisible();
  release();
  await expect(dialog.getByText('Biến thể #99', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Serum kiểm thử', { exact: true })).toHaveCount(0);
});

test('Lịch sử nhập hàng filters KPI cards, CASHFLOW, payableOnly, net received qty, and PO lookup cleanly', async ({ page }) => {
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);
  const queries = [];
  const receivedOrder = {
    ...order,
    id: 12,
    orderNumber: 'PO-20261126-NET1',
    status: 'RECEIVED',
    totalQuantity: 10,
    receivedQuantity: 10,
    returnedQuantity: 2,
    receivedAmount: 800000,
    paidAmount: 300000,
    totalAmount: 1000000,
  };
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    queries.push(`${path}${url.search}`);
    let data = [];
    if (path === '/api/v1/auth/profile') data = admin;
    else if (path === '/api/v1/admin/procurement/suppliers') data = { content: [{ id: 1, code: 'NCC1', name: 'Nhà cung cấp A', active: true }], totalPages: 1 };
    else if (path === '/api/v1/admin/warehouses') data = [{ id: 2, name: 'Kho A' }];
    else if (path === '/api/v1/admin/procurement/history/summary') {
      data = {
        receiptCount: 1, totalReceiptAmount: 1000000,
        supplierReturnCount: 1, totalReturnAmount: 200000,
        totalPaymentAmount: 300000, totalRefundAmount: 0, netPaidAmount: 300000,
        receivedOrdersCount: 1, receivedOrdersValue: 800000, outstandingPayableAmount: 500000,
      };
    } else if (path === '/api/v1/admin/procurement/movements') {
      data = {
        content: [{
          id: 50, purchaseOrderId: 12, orderNumber: 'PO-20261126-NET1', orderStatus: 'RECEIVED',
          supplierName: 'Nhà cung cấp A', warehouseId: 2, kind: 'RECEIPT', reference: 'RCV-PO-20261126-NET1',
          amount: 1000000, reason: 'Nhập kho trực tiếp theo phiếu mua PO-20261126-NET1',
          createdAt: '2026-11-26T03:00:00Z', totalQuantity: 10, lines: [{ itemId: 91, variantId: 12, productName: 'Serum kiểm thử', sku: '000123', quantity: 10, unitCost: 100000, lineAmount: 1000000 }],
        }],
        page: 0, totalPages: 1, totalElements: 1,
      };
    } else if (path === ordersPath) {
      data = { content: [receivedOrder], page: 0, totalPages: 1, totalElements: 1 };
    } else if (path === `${ordersPath}/12`) {
      data = receivedOrder;
    } else if (path === `${ordersPath}/12/financials`) {
      data = { receivedValue: 800000, paidAmount: 300000, dueAmount: 500000, movements: [] };
    }
    await route.fulfill({ json: { status: 200, success: true, data } });
  });

  await page.goto('/#/admin/procurement?tab=history');
  await expect(page.getByText('RCV-PO-20261126-NET1')).toBeVisible();

  // Clicking Card 3 ("Thanh toán NCC (ròng)") queries kind=CASHFLOW
  await page.getByText('Thanh toán NCC (ròng)').click();
  await expect.poll(() => queries.some(q => q.includes('/movements?') && q.includes('kind=CASHFLOW'))).toBe(true);

  // Clicking Card 4 ("Công nợ NCC còn lại") switches to orders tab with payableOnly=true and shows net 8 / 10
  await page.getByText('Công nợ NCC còn lại').click();
  await expect.poll(() => queries.some(q => q.includes('/orders?') && q.includes('payableOnly=true'))).toBe(true);
  await expect(page.getByText('Nhận 10 · Trả 2')).toBeVisible();
  await expect(page.getByText('500.000 ₫').first()).toBeVisible();

  // Expanding operations panel supports looking up PO by orderNumber string
  await page.getByText('Nhận từng đợt, trả nhà cung cấp và công nợ').click();
  await page.getByLabel('Tra phiếu mua theo mã PO hoặc ID').fill('PO-20261126-NET1');
  await page.getByRole('button', { name: 'Mở phiếu mua', exact: true }).click();
  await expect(page.getByText('Giá trị đã nhận ròng:')).toBeVisible();
});
