import { test, expect } from '@playwright/test';

const staff = { id: 7, username: 'staff', fullName: 'Nhân viên đơn hàng', roles: ['ROLE_STAFF'], permissions: ['ORDERS'] };
const order = { id: 21, orderNumber: 'ORD-DETAIL-21', customerName: 'Khách kiểm thử', status: 'PENDING', paymentStatus: 'PENDING', paymentMethod: 'COD', totalAmount: 180000 };
const item = { id: 3, productName: 'Serum phục hồi kiểm thử', variantName: 'Chai 30ml', sku: 'SERUM-30', price: 100000, discount: 10000, quantity: 2 };

async function setup(page, state) {
  const listedOrder = state.order || order;
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, staff);
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data = [], status = 200;
    if (path === '/api/v1/auth/profile') data = staff;
    if (path === '/api/v1/admin/orders') data = { content: [listedOrder], totalPages: 1, totalElements: 1 };
    if (path === '/api/v1/orders/21') {
      state.customerCalls++;
      status = 403;
    }
    if (path === '/api/v1/admin/orders/21') {
      state.adminCalls++;
      if (state.gate) await state.gate;
      status = state.fail ? 503 : 200;
      data = { ...listedOrder, items: [item] };
    }
    await route.fulfill({ status, json: { data, message: status === 503 ? 'Không tải được đơn hàng kiểm thử' : '' } });
  });
  await page.goto('/#/admin/orders');
}

test('staff loads product details using the admin endpoint and shows loading until it resolves', async ({ page }) => {
  let release;
  const state = { customerCalls: 0, adminCalls: 0, gate: new Promise(resolve => { release = resolve; }) };
  await setup(page, state);
  await page.getByTitle('Xem chi tiết đơn', { exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('status')).toHaveText('Đang tải chi tiết đơn hàng…');
  await expect(dialog.getByText('Danh sách mặt hàng (0)')).toHaveCount(0);
  release();
  await expect(dialog.getByText(item.productName, { exact: true })).toBeVisible();
  await expect(dialog.getByText(item.variantName, { exact: true })).toBeVisible();
  await expect(dialog.getByText(item.sku, { exact: true })).toBeVisible();
  const row = dialog.getByRole('row').filter({ hasText: item.productName });
  await expect(row).toContainText('90.000');
  await expect(row).toContainText('180.000');
  expect(state.adminCalls).toBe(1);
  expect(state.customerCalls).toBe(0);
});

test('failed detail requests display an error and retry loads the products', async ({ page }) => {
  const state = { customerCalls: 0, adminCalls: 0, fail: true };
  await setup(page, state);
  await page.getByTitle('Xem chi tiết đơn', { exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('alert')).toContainText('Không tải được đơn hàng kiểm thử');
  await expect(dialog.getByText('Danh sách mặt hàng (0)')).toHaveCount(0);
  state.fail = false;
  await dialog.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(dialog.getByText(item.productName, { exact: true })).toBeVisible();
  expect(state.adminCalls).toBe(2);
  expect(state.customerCalls).toBe(0);
});

test('delivery proof shows the photo, shipper and products for the selected order', async ({ page }) => {
  const deliveredOrder = {
    ...order,
    status: 'DELIVERED',
    paymentStatus: 'PAID',
    deliveryProofUrl: '/uploads/proof.jpg',
    shipperId: 8,
    shipperName: 'Tài xế kiểm thử',
    shipperPhone: '0900000008',
    deliveredAt: '2026-11-22T08:30:00Z',
    deliveredNotes: 'Đã giao tận tay',
    shippingAddress: '123 Đường Kiểm Thử',
  };
  const state = { customerCalls: 0, adminCalls: 0, order: deliveredOrder };
  await setup(page, state);
  await page.getByTitle('Xem ảnh bằng chứng giao hàng (PoD)', { exact: true }).click();

  const dialog = page.getByRole('dialog', { name: 'Bằng chứng giao hàng' });
  await expect(dialog.getByRole('img', { name: `Ảnh bằng chứng giao đơn ${order.orderNumber}` })).toBeVisible();
  await expect(dialog.getByText(deliveredOrder.shipperName)).toBeVisible();
  await expect(dialog.getByText(deliveredOrder.shipperPhone)).toBeVisible();
  await expect(dialog.getByText(deliveredOrder.deliveredNotes)).toBeVisible();
  await expect(dialog.getByText(deliveredOrder.shippingAddress)).toBeVisible();
  await expect(dialog.getByRole('row').filter({ hasText: item.productName })).toBeVisible();
  expect(state.adminCalls).toBe(1);
  expect(state.customerCalls).toBe(0);
});
