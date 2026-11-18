import { test, expect } from '@playwright/test';

async function storefront(page, configs, status = 200, user) {
  await page.route('**/fonts.googleapis.com/**', route => route.abort());
  await page.route('**/fonts.gstatic.com/**', route => route.abort());
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/system-configs/public') {
      return route.fulfill({ status, json: status === 200 ? { success: true, data: configs } : { message: 'Unavailable' } });
    }
    let data = [];
    if (user && path === '/api/v1/auth/login') {
      data = { ...user, accessToken: 'storefront-test-token' };
    } else if (user && path === '/api/v1/auth/profile') {
      data = user;
    }
    if (path === '/api/v1/cart') data = { id: 1, items: [], totalPrice: 0 };
    else if (path === '/api/v1/products/search' || path === '/api/v1/products' || path === '/api/v1/brands') {
      data = { content: [], totalElements: 0, totalPages: 0 };
    }
    await route.fulfill({ json: { success: true, data } });
  });
  const response = page.waitForResponse(result => new URL(result.url()).pathname === '/api/v1/system-configs/public');
  await page.goto('/#/products');
  await response;
  return page.getByRole('contentinfo');
}

test('footer uses only configured public contact values and creates their links', async ({ page }) => {
  const contact = { address: ' Địa chỉ kiểm thử & khu A ', hotline: ' +84 (90) 123-4567 ', email: ' care@example.test ' };
  const footer = await storefront(page, {
    'store.address': contact.address, 'store.hotline': contact.hotline, 'store.email': contact.email,
  });
  const map = footer.getByRole('link', { name: contact.address.trim() });
  await expect(map).toHaveAttribute('href', `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(contact.address.trim())}`);
  await expect(map).toHaveAttribute('rel', 'noopener noreferrer');
  await expect(footer.getByRole('link', { name: contact.hotline.trim() })).toHaveAttribute('href', 'tel:+84901234567');
  await expect(footer.getByRole('link', { name: contact.email.trim() })).toHaveAttribute('href', 'mailto:care@example.test');
  await expect(footer.getByText(/123 Lê Lợi|1800 6324|09:00 - 21:00/)).toHaveCount(0);
});

for (const scenario of [
  { name: 'missing public keys', configs: { 'shipping.free_threshold': 249000 } },
  { name: 'blank public values', configs: { 'store.address': ' ', 'store.hotline': '', 'store.email': ' ' } },
  { name: 'public API failure', configs: {}, status: 503 },
]) {
  test(`footer leaves contact unavailable without links for ${scenario.name}`, async ({ page }) => {
    const footer = await storefront(page, scenario.configs, scenario.status);
    await expect(footer.getByText('Địa chỉ cửa hàng: Chưa cập nhật.', { exact: true })).toBeVisible();
    await expect(footer.getByText('Hotline CSKH: Chưa cập nhật.', { exact: true })).toBeVisible();
    await expect(footer.getByText('Email liên hệ: Chưa cập nhật.', { exact: true })).toBeVisible();
    await expect(footer.locator('a[href^="tel:"], a[href^="mailto:"], a[href*="google.com/maps"]')).toHaveCount(0);
    await footer.getByRole('button', { name: 'Xem giờ trống và đặt lịch Spa' }).click();
    await expect(page).toHaveURL(/#\/booking$/);
  });
}

test('footer rejects malformed or non-text contact values instead of creating unsafe links', async ({ page }) => {
  const footer = await storefront(page, { 'store.address': { value: 'Not an address' }, 'store.hotline': 'javascript:123456', 'store.email': 'not-an-email' });
  await expect(footer.getByText('Địa chỉ cửa hàng: Chưa cập nhật.', { exact: true })).toBeVisible();
  await expect(footer.getByText('Hotline CSKH: Chưa cập nhật.', { exact: true })).toBeVisible();
  await expect(footer.getByText('Email liên hệ: Chưa cập nhật.', { exact: true })).toBeVisible();
  await expect(footer.locator('a[href^="tel:"], a[href^="mailto:"], a[href*="google.com/maps"]')).toHaveCount(0);
});

for (const role of ['ROLE_CUSTOMER', 'ROLE_ADMIN']) {
  test(`storefront login and footer Spa links stay in the storefront for ${role}`, async ({ page }) => {
    await storefront(page, {}, 200, { id: 5, username: 'test-user', roles: [role] });
    await page.goto('/#/login');
    await page.locator('#customer-auth-username').fill('test-user');
    await page.locator('#customer-auth-password').fill('test-password');
    await page.locator('.auth-slide-pane.active form').locator('[type="submit"]').click();
    await expect(page).toHaveURL(/#\/$/);
    await expect(page.locator('.customer-home-page')).toBeVisible();

    const footer = page.getByRole('contentinfo');
    for (const [name, hash] of [
      [/^Dịch Vụ Spa/, '#/spa'],
      ['Xem Danh Sách Dịch Vụ', '#/spa'],
      ['Chọn Nhân Viên Và Giờ Trống', '#/booking'],
      ['Đăng Ký Đặt Lịch Hẹn Trực Tuyến', '#/booking'],
    ]) {
      await footer.getByRole('link', { name }).click();
      await expect(page).toHaveURL(url => url.hash === hash);
      await expect(page.locator('#spa-booking-title')).toBeVisible();
    }
  });
}
