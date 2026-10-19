import { test, expect } from '@playwright/test';

const customer = { id: 7, username: 'customer-ui', fullName: 'Khách hàng kiểm thử', email: 'customer@example.test', roles: ['ROLE_CUSTOMER'] };
const products = [
  { id: 42, name: 'Serum phục hồi B5', status: 'ACTIVE', brandName: 'Beauty Lab', basePrice: 250000, minPrice: 250000, totalSold: 10, stockQuantity: 20, variants: [{ id: 99, sku: 'SERUM-B5', price: 280000, discountPrice: 250000, stockQuantity: 20, isActive: true, isDefault: true }] },
  { id: 43, name: 'Kem dưỡng Ceramide', status: 'ACTIVE', brandName: 'Beauty Lab', basePrice: 500000, minPrice: 500000, totalSold: 5, stockQuantity: 20, variants: [{ id: 100, sku: 'CREAM-CERAMIDE', price: 500000, stockQuantity: 20, isActive: true, isDefault: true }] },
];
const cart = { id: 1, totalPrice: 250000, items: [{ id: 10, variantId: 99, sku: 'SERUM-B5', variantName: 'Serum phục hồi B5', price: 250000, quantity: 1, available: true }] };

async function storefront(page, { authenticated = false, withCart = false, catalogFailure = false, profileData = {}, profileFailure = false, loadFonts = false } = {}) {
  let failed = catalogFailure;
  const catalogRequests = [];
  const currentCart = withCart ? JSON.parse(JSON.stringify(cart)) : { id: 1, items: [], totalPrice: 0 };
  if (authenticated) {
    await page.addInitScript((user) => {
      localStorage.setItem('beautyshop_admin_token', 'ui-test-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
    }, customer);
  }
  if (!loadFonts) {
    await page.route('**/fonts.googleapis.com/**', (route) => route.abort());
    await page.route('**/fonts.gstatic.com/**', (route) => route.abort());
  }
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (!path.startsWith('/api/')) return route.continue();
    let data = [];
    if (Object.hasOwn(profileData, path)) {
      if (profileFailure && path === '/api/v1/users/order-statistics') return route.fulfill({ status: 503, json: { message: 'Không thể tải thống kê thử nghiệm' } });
      data = profileData[path];
    }
    else if (path.endsWith('/auth/profile')) data = customer;
    else if (path === '/api/v1/cart') data = currentCart;
    else if (path === '/api/v1/cart/add') {
      const payload = route.request().postDataJSON();
      const product = products.find((entry) => entry.variants.some((variant) => variant.id === payload.variantId));
      const variant = product.variants.find((entry) => entry.id === payload.variantId);
      const existing = currentCart.items.find((entry) => entry.variantId === variant.id);
      if (existing) existing.quantity += payload.quantity;
      else currentCart.items.push({ id: 10 + currentCart.items.length, variantId: variant.id, sku: variant.sku, variantName: product.name, quantity: payload.quantity, price: variant.discountPrice ?? variant.price, available: true });
      currentCart.totalPrice = currentCart.items.reduce((total, entry) => total + entry.quantity * entry.price, 0);
      data = currentCart;
    }
    else if (path === '/api/v1/products/search') {
      catalogRequests.push(url.searchParams.toString());
      if (failed) return route.fulfill({ status: 503, json: { message: 'Catalog temporarily unavailable' } });
      const matching = url.searchParams.get('onSale') === 'true'
        ? products.filter(product => product.variants.some(variant => Number(variant.discountPrice) < Number(variant.price))) : products;
      data = { content: matching, totalElements: matching.length, totalPages: 1 };
    } else if (path === '/api/v1/products/42') data = products[0];
    else if (path === '/api/v1/products/43') data = products[1];
    else if (path === '/api/v1/products' || path === '/api/v1/brands') data = { content: path.endsWith('/brands') ? [{ id: 5, name: 'Beauty Lab' }] : products, totalElements: 2, totalPages: 1 };
    else if (path === '/api/v1/categories' || path === '/api/v1/categories/root') data = [{ id: 4, name: 'Serum', slug: 'serum', children: [] }];
    else if (path === '/api/v1/spa/services') data = [{ id: 8, name: 'Chăm sóc da phục hồi', basePrice: 300000, durationMinutes: 60, isActive: true, categoryId: 1, categoryName: 'Chăm sóc da' }];
    else if (path === '/api/v1/chatbot/health') data = { status: 'ok' };
    else if (path === '/api/v1/orders/my-orders' || path === '/api/v1/reviews') data = { content: [], totalElements: 0, totalPages: 0 };
    return route.fulfill({ json: { success: true, data } });
  });
  return { recover: () => { failed = false; }, catalogRequests };
}

test('Mobile search remains visible and submits an encoded keyword', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const api = await storefront(page);
  await page.goto('/');
  const search = page.getByRole('searchbox', { name: 'Tìm kiếm sản phẩm' });
  await expect(search).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await search.fill('B5 & phục hồi');
  await search.press('Enter');
  await expect(page).toHaveURL(/search=B5%20%26%20ph%E1%BB%A5c%20h%E1%BB%93i/);
  await expect(page.getByRole('heading', { name: /Kết Quả Cho/ })).toBeVisible();
  await expect.poll(() => api.catalogRequests.some((query) => new URLSearchParams(query).get('keyword') === 'B5 & phục hồi')).toBeTruthy();
});

test('Mobile menu traps keyboard focus and Escape restores its trigger', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await storefront(page);
  await page.goto('/');
  const trigger = page.getByRole('button', { name: 'Mở menu điều hướng' });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: 'Khám phá cửa hàng' });
  await expect(dialog).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đóng menu điều hướng' })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBeTruthy();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe('hidden');
  await trigger.click();
  await dialog.getByRole('link', { name: 'Sản phẩm', exact: true }).click();
  await expect(page).toHaveURL(/#\/products$/);
  await expect(dialog).toHaveCount(0);
});

test('Desktop category dropdown is keyboard accessible and uses catalog IDs', async ({ page }) => {
  await storefront(page);
  await page.goto('/');
  const toggle = page.getByRole('button', { name: 'Mở danh sách sản phẩm' });
  await toggle.focus();
  await page.keyboard.press('Enter');
  const category = page.locator('#storefront-categories').getByRole('link', { name: 'Serum' });
  await expect(category).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(toggle).toBeFocused();
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await toggle.click();
  await category.click();
  await expect(page).toHaveURL(/categoryId=4/);
  await expect(page.getByRole('heading', { name: 'Serum', exact: true })).toBeVisible();
});

test('Catalog failure offers a retry and preserves the current search', async ({ page }) => {
  const api = await storefront(page, { catalogFailure: true });
  await page.goto('/#/products?search=B5');
  await expect(page.getByRole('heading', { name: 'Chưa tải được sản phẩm' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Không tìm thấy sản phẩm phù hợp' })).toHaveCount(0);
  api.recover();
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.locator('.product-card-resolved')).toHaveCount(2);
  expect(api.catalogRequests.every((query) => new URLSearchParams(query).get('keyword') === 'B5')).toBeTruthy();
});

test('Returning to the full catalog clears the sale filter', async ({ page }) => {
  await storefront(page);
  await page.goto('/#/products?filter=sale');
  await expect(page.locator('.product-card-resolved')).toHaveCount(1);
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('link', { name: 'Sản phẩm', exact: true }).click();
  await expect(page.locator('.product-card-resolved')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'Mỹ Phẩm Khoa Học & Phục Hồi Làn Da' })).toBeVisible();
});

test('Mobile filters close with Escape and restore focus', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await storefront(page);
  await page.goto('/#/products');
  const trigger = page.getByRole('button', { name: 'Bộ lọc sản phẩm' });
  await trigger.click();
  await expect(page.getByRole('dialog', { name: 'Bộ lọc sản phẩm' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Bộ lọc sản phẩm' })).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test('The cart drawer supports Escape and continues to a populated checkout', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await storefront(page, { withCart: true });
  await page.goto('/');
  const trigger = page.getByRole('button', { name: /Giỏ hàng/ });
  await trigger.click();
  const drawer = page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ });
  await expect(drawer).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đóng giỏ hàng' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(drawer).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await trigger.click();
  await drawer.getByRole('button', { name: 'Tiến hành thanh toán' }).click();
  await expect(page).toHaveURL(/#\/checkout$/);
  await expect(page.getByRole('heading', { name: 'Thanh toán đơn hàng' })).toBeVisible();
  await expect(drawer).toHaveCount(0);
});

test('Spa ticket links open the ticket tab and profile tabs survive reload', async ({ page }) => {
  await storefront(page, { authenticated: true });
  await page.goto('/#/profile?tab=tickets');
  await expect(page.getByRole('heading', { name: 'Bạn chưa có vé liệu trình Spa nào' })).toBeVisible();
  const menu = page.getByRole('navigation', { name: 'Menu tài khoản' });
  await expect(menu.getByRole('button', { name: 'Vé liệu trình điện tử', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await menu.getByRole('button', { name: 'Lịch sử mua sắm', exact: true }).click();
  await expect(page).toHaveURL(/profile\?tab=orders$/);
  await page.reload();
  await expect(menu.getByRole('button', { name: 'Lịch sử mua sắm', exact: true })).toHaveAttribute('aria-pressed', 'true');
});

test('Product cards can open their details from the keyboard', async ({ page }) => {
  await storefront(page);
  await page.goto('/#/products');
  const product = page.getByRole('link', { name: 'Serum phục hồi B5', exact: true });
  await product.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/product\/42$/);
  await expect(page.locator('.product-detail-page')).toBeVisible();
});

for (const width of [320, 390, 768, 1440]) {
  test(`Customer pages fit a ${width}px viewport without visible source code`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await storefront(page, { authenticated: true, withCart: true });
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    for (const route of ['', 'products', 'product/42', 'booking', 'checkout', 'profile?tab=tickets', 'order-success?method=COD', 'login']) {
      await page.goto(`/#/${route}`);
      await expect(page.locator('.customer-app'), route).toBeVisible();
      if (route === 'checkout') await expect(page.getByRole('heading', { name: 'Thanh toán đơn hàng' })).toBeVisible();
      if (route === 'product/42') await expect(page.locator('.product-detail-page')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), route).toBeTruthy();
      const content = await page.locator('body').innerText();
      expect(content).not.toMatch(/@keyframes|@media|import React|<<<<<<<|grid-template-columns:/);
      if (route === 'checkout') {
        const bounds = await page.locator('.customer-checkout-grid').boundingBox();
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(width + 1);
        await expect(page.getByLabel('HỌ VÀ TÊN *', { exact: true })).toBeVisible();
        await expect(page.getByLabel('SỐ ĐIỆN THOẠI *', { exact: true })).toBeVisible();
      }
    }
    expect(errors).toEqual([]);
  });
}

const profileCustomer = { ...customer, fullName: 'Nguyễn Khách Hàng Chăm Sóc Da Chuyên Sâu', email: 'khachhang.chamsoc.dachuyensau@example.test', phone: '0912345678', membershipTier: 'GOLD', loyaltyPoints: 7654 };
const profileFixture = {
  '/api/v1/auth/profile': profileCustomer,
  '/api/v1/users/order-statistics': { totalOrders: 28, netSpent: 1234567890 },
  '/api/v1/orders/my-orders': { content: [{ id: 21, orderCode: 'ORD-20261019-00000021', status: 'SHIPPED', totalAmount: 12345678 }], totalPages: 1, totalElements: 28 },
  '/api/v1/appointments/my-appointments': [{ id: 71, status: 'CONFIRMED', appointmentDate: '2026-10-20', startTime: '09:00:00', endTime: '10:00:00', items: [{ serviceId: 8, serviceName: 'Chăm sóc phục hồi hàng rào bảo vệ da chuyên sâu', staffName: 'Chuyên viên kiểm thử' }] }],
  '/api/v1/spa/tickets/my-tickets': [{ id: 80, packageName: 'Liệu trình phục hồi và chăm sóc làn da nhạy cảm chuyên sâu', status: 'ACTIVE', remainingSessions: 7, usedSessions: 3, totalSessions: 10, expiryDate: '2027-10-20' }],
};

async function expectProfileFits(page) {
  const problems = await page.locator('.customer-profile-page').evaluate(root => {
    const failures = [];
    for (const el of root.querySelectorAll('*')) {
      const rect = el.getBoundingClientRect();
      if (!rect.width || !rect.height || getComputedStyle(el).position === 'fixed' || el.closest('[role="dialog"]')) continue;
      if (rect.left < -1 || rect.right > innerWidth + 1) failures.push(`${el.className || el.tagName}: outside viewport`);
      if (el.scrollWidth > el.clientWidth + 2 && !['svg', 'path', 'input', 'textarea'].includes(el.tagName.toLowerCase()) && !['inline', 'contents'].includes(getComputedStyle(el).display)) failures.push(`${el.className || el.tagName}: clipped content`);
    }
    return failures;
  });
  expect(problems).toEqual([]);
}

for (const viewport of [{ width: 1920, height: 1080 }, { width: 1440, height: 900 }, { width: 1280, height: 800 }, { width: 1024, height: 768 }, { width: 768, height: 1024 }, { width: 390, height: 844 }, { width: 360, height: 800 }, { width: 1440, height: 600 }, { width: 720, height: 450 }]) {
  test(`Profile sizing uses available space at ${viewport.width}x${viewport.height}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    await storefront(page, { authenticated: true, profileData: profileFixture, loadFonts: process.env.PROFILE_LIVE_FONTS === '1' });
    await page.goto('/#/profile');
    await expect(page.getByRole('button', { name: 'Làm mới', exact: true })).toBeEnabled();
    if (process.env.PROFILE_LIVE_FONTS === '1') {
      const fonts = await page.evaluate(async () => { await document.fonts.ready; return Promise.all(['500 16px "Be Vietnam Pro"', '700 32px "Be Vietnam Pro"'].map(font => document.fonts.load(font).then(faces => faces.length))); });
      expect(fonts.every(count => count > 0)).toBeTruthy();
    }
    await expect(page.locator('.bento-kpi-currency')).toHaveText(/1\.234\.567\.890\s*₫/);
    await expectProfileFits(page);
    await expect(page.locator('.profile-overview-summary .bento-hero-banner')).toHaveCount(1);
    await expect(page.locator('.profile-overview-summary .bento-kpi-grid')).toHaveCount(1);
    await expect(page.locator('.profile-vip-tier-tag, .profile-nav-badge, .profile-eyebrow, .bento-ticket-perforation')).toHaveCount(0);
    expect(await page.locator('.customer-profile-page').innerText()).not.toMatch(/PRIVÉ|Privé|SPA CLINIC PASS|\bPASS\b|\p{Extended_Pictographic}/u);
    const panelStyle = await page.locator('.profile-sidebar').evaluate(el => ({ border: getComputedStyle(el).borderTopWidth, gap: getComputedStyle(el).rowGap }));
    expect(panelStyle.border).toBe('1px');
    expect(['normal', '0px']).toContain(panelStyle.gap);
    const headingStyle = await page.locator('.profile-overview h1').evaluate(el => ({ family: getComputedStyle(el).fontFamily, weight: Number(getComputedStyle(el).fontWeight) }));
    expect(headingStyle.family).toContain('Be Vietnam Pro');
    expect(headingStyle.weight).toBeGreaterThanOrEqual(600);
    const sizes = await page.locator('.profile-shell').evaluate(shell => {
      const main = shell.querySelector('.profile-main-content');
      const grid = shell.querySelector('.bento-kpi-grid');
      return { width: shell.getBoundingClientRect().width, main: main.clientWidth, columns: getComputedStyle(grid).gridTemplateColumns.split(' ').length, title: parseFloat(getComputedStyle(shell.querySelector('h1')).fontSize), metric: parseFloat(getComputedStyle(shell.querySelector('.bento-kpi-value')).fontSize) };
    });
    expect(sizes.width).toBeGreaterThan(viewport.width * .9 - 1);
    expect(sizes.columns).toBe(sizes.main >= 1000 ? 4 : sizes.main >= 600 ? 2 : 1);
    expect(sizes.title).toBeGreaterThanOrEqual(32);
    expect(sizes.metric).toBeGreaterThanOrEqual(30);
    for (const locator of [page.locator('.profile-nav-item'), page.locator('.btn-editorial-primary'), page.locator('.btn-editorial-outline')]) {
      const heights = await locator.evaluateAll(elements => elements.map(el => el.getBoundingClientRect().height));
      expect(heights.every(height => height >= 44)).toBeTruthy();
    }
    if ([1920, 390].includes(viewport.width)) await page.screenshot({ path: testInfo.outputPath(`profile-${viewport.width}.png`), fullPage: true });
    const menu = page.getByRole('navigation', { name: 'Menu tài khoản' });
    await menu.getByRole('button', { name: /Thông tin & Bảo mật/ }).click();
    await expect(page.getByLabel('Họ và tên', { exact: true })).toHaveValue(profileCustomer.fullName);
    await expectProfileFits(page);
    const save = page.getByRole('button', { name: 'Lưu thông tin', exact: true });
    expect(await save.evaluate(el => ({ background: getComputedStyle(el).backgroundColor, color: getComputedStyle(el).color }))).toEqual({ background: 'rgb(114, 27, 41)', color: 'rgb(255, 255, 255)' });
    await menu.getByRole('button', { name: /Vé liệu trình điện tử/ }).click();
    await expect(page.locator('.profile-ticket-card')).toContainText('Còn lại: 7 buổi');
    await expectProfileFits(page);
    await menu.getByRole('button', { name: /Lịch hẹn trị liệu/ }).click();
    await expect(page.locator('.profile-appointment-card')).toContainText('SPA-APT-#71');
    await expectProfileFits(page);
    for (const [label, tab] of [['Lịch sử mua sắm', 'orders'], ['Hồ sơ làn da cá nhân', 'skin'], ['Sổ địa chỉ nhận hàng', 'addresses'], ['Sản phẩm yêu thích', 'wishlist'], ['Hộp thư thông báo', 'notifications']]) {
      await menu.getByRole('button', { name: new RegExp(label) }).click();
      await expect(page).toHaveURL(new RegExp(`tab=${tab}$`));
      await expectProfileFits(page);
    }
  });
}

test('Profile keeps its enlarged frame for empty and failed data', async ({ page }) => {
  await storefront(page, { authenticated: true, profileData: { '/api/v1/users/order-statistics': {} }, profileFailure: true });
  await page.goto('/#/profile');
  await expect(page.getByRole('alert')).toContainText('Không thể tải thống kê thử nghiệm');
  await expect(page.getByRole('button', { name: 'Làm mới', exact: true })).toBeEnabled();
  await expect(page.locator('.bento-order-card')).toContainText('Bạn chưa có đơn đặt hàng nào gần đây.');
  await expectProfileFits(page);
});

test('Profile refresh keeps its frame and exposes keyboard focus', async ({ page }) => {
  await storefront(page, { authenticated: true, profileData: profileFixture });
  await page.goto('/#/profile');
  const refresh = page.getByRole('button', { name: 'Làm mới', exact: true });
  await expect(refresh).toBeEnabled();
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await page.route('**/api/v1/users/order-statistics', async route => {
    await pending;
    await route.fulfill({ json: { data: profileFixture['/api/v1/users/order-statistics'] } });
  });
  const before = await page.locator('.bento-kpi-grid').boundingBox();
  await refresh.click();
  await expect(page.getByRole('button', { name: 'Đang đồng bộ...', exact: true })).toBeDisabled();
  const during = await page.locator('.bento-kpi-grid').boundingBox();
  expect(during.width).toBeCloseTo(before.width, 0);
  expect(during.height).toBeCloseTo(before.height, 0);
  release();
  await expect(refresh).toBeEnabled();
  await page.locator('.profile-breadcrumb').getByRole('button', { name: 'Trang chủ', exact: true }).focus();
  await page.keyboard.press('Tab');
  await expect(refresh).toBeFocused();
  expect(await refresh.evaluate(el => getComputedStyle(el).outlineStyle)).not.toBe('none');
  await expectProfileFits(page);
});

test('Profile text and control palette clears contrast thresholds', async ({ page }) => {
  await storefront(page, { authenticated: true, profileData: profileFixture });
  await page.goto('/#/profile');
  await expect(page.getByRole('button', { name: 'Làm mới', exact: true })).toBeEnabled();
  const contrasts = await page.locator('.customer-profile-page').evaluate(root => {
    const styles = getComputedStyle(root);
    const luminance = hex => {
      const values = hex.trim().replace('#', '').match(/../g).map(part => parseInt(part, 16) / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4);
      return .2126 * values[0] + .7152 * values[1] + .0722 * values[2];
    };
    const contrast = (a, b) => { const values = [luminance(a), luminance(b)].sort((x, y) => y - x); return (values[0] + .05) / (values[1] + .05); };
    return ['--ed-charcoal', '--ed-charcoal-muted', '--ed-charcoal-subtle', '--ed-wine'].flatMap(token => ['--ed-card', '--ed-bg', '--ed-champagne-rose'].map(surface => contrast(styles.getPropertyValue(token), styles.getPropertyValue(surface))));
  });
  expect(contrasts.every(ratio => ratio >= 4.5)).toBeTruthy();
});

test('Customer login and registration expose only the active form to keyboard users', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await storefront(page);
  await page.goto('/#/login');
  await expect(page.getByLabel('Tên đăng nhập hoặc Email *', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng Ký Tài Khoản', exact: true }).click();
  await expect(page.getByLabel('Họ và tên *', { exact: true })).toBeVisible();
  await expect(page.locator('.auth-slide-pane').first()).toHaveAttribute('inert', '');
  await expect(page.locator('.auth-slide-pane').last()).not.toHaveAttribute('inert', '');
  await page.getByRole('button', { name: 'Đăng Nhập', exact: true }).click();
  await expect(page.getByLabel('Tên đăng nhập hoặc Email *', { exact: true })).toBeVisible();
});
