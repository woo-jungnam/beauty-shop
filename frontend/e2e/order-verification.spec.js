import { test, expect } from '@playwright/test';

const order = {
  id: 21, orderNumber: 'ORD-VERIFIED-21', status: 'PENDING',
  paymentMethod: 'BANK', paymentStatus: 'PENDING', totalAmount: 120000, paidAmount: 0,
  paymentDeadline: new Date(Date.now() + 600000).toISOString(),
  paymentInstruction: { bankName: 'Test bank', bankAccountNumber: '123456789', transferSyntax: 'ORDVERIFIED21', qrCodeUrl: '' },
};

async function setup(page, orderResponse) {
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (/\/orders\/\d+$/.test(path)) return orderResponse(route, path);
    const data = path.endsWith('/payment-subscription') ? { subscriptionToken: 'scoped-test-token' } : [];
    return route.fulfill({ json: { data } });
  });
  await page.routeWebSocket('**/ws/payment?*', () => {});
}

test('order verification ignores cached success and URL payment claims until the API answers', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('last_created_order', JSON.stringify({ id: 21, orderNumber: 'CACHED-UNVERIFIED', paymentStatus: 'PAID', totalAmount: 0 })));
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await setup(page, async route => { await gate; return route.fulfill({ json: { data: order } }); });
  await page.goto('/#/order-success?id=21&method=COD&code=URL-CLAIM&amount=0');
  await expect(page.getByRole('heading', { name: 'Đang xác minh đơn hàng', exact: true })).toBeVisible();
  await expect(page.getByText('CACHED-UNVERIFIED', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Đặt Hàng Thành Công!', exact: true })).toHaveCount(0);
  release();
  await expect(page.getByRole('heading', { name: 'Đơn Hàng Đã Tạo — Vui Lòng Chuyển Khoản', exact: true })).toBeVisible();
  await expect(page.getByText('ORD-VERIFIED-21', { exact: true })).toBeVisible();
  await expect(page.getByText('Chưa tải được mã QR.', { exact: false })).toBeVisible();
  await expect(page.getByText('Hình Thức: Thanh Toán Tiền Mặt Khi Nhận Hàng (COD)', { exact: true })).toHaveCount(0);
});

test('a failed order request stays unverified and a retry can confirm a genuine zero-price COD order', async ({ page }) => {
  let failing = true;
  await setup(page, route => failing
    ? route.fulfill({ status: 404, json: { message: 'Không tìm thấy đơn hàng của bạn' } })
    : route.fulfill({ json: { data: { ...order, paymentMethod: 'COD', totalAmount: 0, paymentInstruction: null } } }));
  await page.goto('/#/order-success?id=21&method=BANK&amount=500000');
  await expect(page.getByRole('heading', { name: 'Chưa xác minh được đơn hàng', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Đặt Hàng Thành Công!', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Sao chép số tài khoản' })).toHaveCount(0);
  failing = false;
  await page.getByRole('button', { name: 'Tải lại hướng dẫn thanh toán', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đặt Hàng Thành Công!', exact: true })).toBeVisible();
  await expect(page.getByText('0 ₫', { exact: true })).toBeVisible();
});

test('a cancelled order cannot display payment instructions or a transfer success claim', async ({ page }) => {
  await setup(page, route => route.fulfill({ json: { data: { ...order, status: 'CANCELLED', paymentStatus: 'PAID' } } }));
  await page.goto('/#/order-success?id=21&method=BANK');
  await expect(page.getByRole('heading', { name: 'Đơn hàng đã hủy', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Thanh Toán Thành Công!', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Sao chép số tài khoản' })).toHaveCount(0);
});

test('missing order amount cannot become a zero-value bank instruction', async ({ page }) => {
  await setup(page, route => route.fulfill({ json: { data: { ...order, totalAmount: null } } }));
  await page.goto('/#/order-success?id=21&method=BANK&amount=0');
  await expect(page.getByText('Chưa có đủ thông tin thanh toán đã xác minh', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sao chép số tài khoản' })).toHaveCount(0);
  await expect(page.getByText('0 ₫', { exact: true })).toHaveCount(0);
});

test('an expired bank order does not invite a new transfer', async ({ page }) => {
  await setup(page, route => route.fulfill({ json: { data: { ...order, paymentDeadline: '2020-01-01T00:00:00Z' } } }));
  await page.goto('/#/order-success?id=21&method=BANK');
  await expect(page.getByRole('heading', { name: 'Đã hết hạn thanh toán đơn hàng', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sao chép số tài khoản' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Đơn Hàng Đã Tạo — Vui Lòng Chuyển Khoản', exact: true })).toHaveCount(0);
});

test('switching order IDs cannot retain the previous paid state while the next order loads', async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await setup(page, async (route, path) => {
    if (path.endsWith('/42')) { await gate; return route.fulfill({ json: { data: { ...order, id: 42, orderNumber: 'ORD-42' } } }); }
    return route.fulfill({ json: { data: { ...order, paymentStatus: 'PAID' } } });
  });
  await page.goto('/#/order-success?id=21&method=BANK');
  await expect(page.getByRole('heading', { name: 'Thanh Toán Thành Công!', exact: true })).toBeVisible();
  await page.evaluate(() => { window.location.hash = '/order-success?id=42&method=COD'; });
  await expect(page.getByRole('heading', { name: 'Đang xác minh đơn hàng', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Thanh Toán Thành Công!', exact: true })).toHaveCount(0);
  release();
  await expect(page.getByText('ORD-42', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Đơn Hàng Đã Tạo — Vui Lòng Chuyển Khoản', exact: true })).toBeVisible();
});
