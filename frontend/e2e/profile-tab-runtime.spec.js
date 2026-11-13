import { test, expect } from '@playwright/test';

const user = { id: 7, username: 'profile-check', fullName: 'Khách kiểm tra hồ sơ', email: 'profile@example.test', phone: '0912345678', roles: ['ROLE_CUSTOMER'], membershipTier: 'SILVER', loyaltyPoints: 1200 };
const order = { id: 21, userId: user.id, orderNumber: 'ORD-PROFILE-21', status: 'DELIVERED', paymentStatus: 'PAID', paymentMethod: 'BANK', subTotal: 100000, totalAmount: 95000, paidAmount: 95000, refundedAmount: 0, shippingFee: 0, discountAmount: 5000, items: [{ id: 31, productId: 12, variantId: 44, productName: 'Sản phẩm kiểm tra', quantity: 1, price: 100000 }], createdAt: '2026-11-13T07:00:00Z' };
const ticket = { id: 80, orderId: 22, packageName: 'Gói Spa kiểm tra', nameSnapshot: true, benefitType: 'SERVICE_PACKAGE', status: 'ACTIVE', remainingSessions: 2, totalSessions: 3, usedSessions: 1, reservedSessions: 0, services: [{ serviceId: 11, serviceName: 'Chăm sóc da', total: 3, used: 1, reserved: 0, available: 2 }] };
const appointment = { id: 71, userId: user.id, status: 'CONFIRMED', appointmentDate: '2099-11-14', startTime: '09:00:00', endTime: '10:00:00', items: [{ id: 72, serviceId: 11, serviceName: 'Chăm sóc da', staffName: 'Chuyên viên', executionStatus: 'PLANNED' }] };

async function setup(page) {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(profile => {
    localStorage.setItem('beautyshop_admin_token', 'profile-runtime-test');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(profile));
  }, user);
  await page.route('**/api/v1/**', async route => {
    const { pathname } = new URL(route.request().url());
    expect(route.request().method(), `Unexpected write: ${pathname}`).toBe('GET');
    let data = [];
    if (pathname === '/api/v1/auth/profile') data = user;
    else if (pathname === '/api/v1/orders/my-orders') data = { content: [order], totalElements: 1, totalPages: 1 };
    else if (pathname === '/api/v1/orders/21') data = order;
    else if (pathname === '/api/v1/users/order-statistics') data = { totalOrders: 1, totalPaid: 95000, totalRefunded: 0, netSpent: 95000 };
    else if (pathname === '/api/v1/spa/tickets/my-tickets') data = [ticket];
    else if (pathname === '/api/v1/appointments/my-appointments') data = [appointment];
    else if (pathname === '/api/v1/appointments/71') data = appointment;
    else if (pathname === '/api/v1/spa/appointments/71/finance') data = { appointmentId: 71, entries: [], depositRequired: 0, depositCollected: 0, feeDue: 0, refundable: 0, refunded: 0 };
    else if (pathname === '/api/v1/users/skin-profile') data = { skinType: 'OILY' };
    else if (pathname === '/api/v1/users/notifications' || pathname === '/api/v1/appointments/my-service-orders') data = { content: [], totalPages: 0, totalElements: 0 };
    else if (pathname === '/api/v1/cart') data = { id: 1, items: [], totalPrice: 0 };
    await route.fulfill({ json: { data } });
  });
  return errors;
}

test('every profile tab renders without uncaught errors when switching back and forth', async ({ page }) => {
  const errors = await setup(page);
  await page.goto('/#/profile');
  const navigation = page.getByRole('navigation', { name: 'Menu tài khoản', exact: true });
  const tabs = ['Tổng quan', 'Lịch sử giao dịch', 'Lịch hẹn Spa', 'Ví vé Spa', 'Hồ sơ da', 'Sổ địa chỉ', 'Yêu thích', 'Thông báo & Ưu đãi', 'Thông tin & Bảo mật'];
  for (const title of [...tabs, ...tabs.toReversed()]) {
    await navigation.getByRole('button', { name: title, exact: false }).click();
    await expect(page.locator('#profile-workspace-title')).toHaveText(title);
    await expect(page.getByRole('heading', { name: 'Không gian cá nhân', exact: true })).toBeVisible();
    expect(errors, `Runtime errors on ${title}`).toEqual([]);
  }
});

test('order history and order detail both render useCallback after profile navigation', async ({ page }) => {
  const errors = await setup(page);
  await page.goto('/#/profile');
  await page.getByRole('navigation', { name: 'Menu tài khoản', exact: true }).getByRole('button', { name: 'Lịch sử giao dịch' }).click();
  await expect(page.locator('#profile-workspace-title')).toHaveText('Lịch sử giao dịch');
  await page.getByRole('button', { name: 'Chi tiết đơn hàng', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText(order.orderNumber);
  await expect(page.getByRole('dialog')).toContainText('Sản phẩm kiểm tra');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('navigation', { name: 'Menu tài khoản', exact: true }).getByRole('button', { name: 'Hồ sơ da' }).click();
  await expect(page.getByRole('combobox', { name: 'Loại da' })).toHaveValue('OILY');
  expect(errors).toEqual([]);
});

test('summary order detail and appointment dialog preserve the profile after closing', async ({ page }) => {
  const errors = await setup(page);
  await page.goto('/#/profile');
  await page.getByRole('button', { name: 'Xem chi tiết đơn', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText(order.orderNumber);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Chi tiết lịch hẹn', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Lịch hẹn #71');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('#profile-workspace-title')).toHaveText('Tổng quan');
  expect(errors).toEqual([]);
  await page.screenshot({ path: test.info().outputPath('profile-runtime.png'), fullPage: true });
});
