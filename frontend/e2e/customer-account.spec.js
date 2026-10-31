import { test, expect } from '@playwright/test';

const user = { id: 7, username: 'khachhang', fullName: 'Nguyễn An', email: 'an@example.test', roles: ['ROLE_CUSTOMER'], loyaltyPoints: 20 };
const product = { id: 12, name: 'Kem dưỡng thử nghiệm', brandName: 'Beauty', price: 120000, minPrice: 120000, thumbnailUrl: '', variants: [{ id: 44, sku: 'TEST-44', price: 120000, isActive: true, stockQuantity: 5, isDefault: true }] };
const order = { id: 21, orderNumber: 'ORD-21', status: 'DELIVERED', paymentStatus: 'PAID', paymentMethod: 'BANK', totalAmount: 240000, paidAmount: 240000, refundedAmount: 0, items: [{ id: 31, variantId: 44, productName: 'Kem dưỡng thử nghiệm', quantity: 2, price: 120000 }] };

async function setup(page, authenticated = true, wishlistProducts = [product]) {
  if (authenticated) await page.addInitScript(profile => { localStorage.setItem('beautyshop_admin_token', 'test-access-token'); localStorage.setItem('beautyshop_admin_user', JSON.stringify(profile)); }, user);
  const requests = [];
  let wishlist = structuredClone(wishlistProducts), skin = {}, returns = [], addresses = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname;
    const body = request.postDataJSON(); requests.push({ path, method: request.method(), body, query: url.search });
    let data = [];
    if (path === '/api/v1/auth/profile' || path === '/api/v1/users/profile') data = user;
    else if (path === '/api/v1/users/order-statistics') data = { totalOrders: 21, totalPaid: 240000, totalRefunded: 0, netSpent: 240000 };
    else if (path === '/api/v1/users/skin-profile') { if (request.method() === 'PUT') skin = body; data = skin; }
    else if (path === '/api/v1/users/wishlist') data = wishlist;
    else if (/^\/api\/v1\/users\/wishlist\/\d+$/.test(path)) { const id = Number(path.split('/').at(-1)); wishlist = request.method() === 'DELETE' ? wishlist.filter(row => row.id !== id) : [...wishlist.filter(row => row.id !== id), { ...product, id }]; data = {}; }
    else if (path === '/api/v1/orders/my-orders') data = { content: url.searchParams.get('page') === '1' ? [{ ...order, id: 22, orderNumber: 'ORD-22' }] : [order], totalPages: 2, totalElements: 21 };
    else if (path === '/api/v1/orders/21') data = order;
    else if (path === '/api/v1/orders/21/returns') { if (request.method() === 'POST') returns.push({ id: 1, ...body, status: 'REQUESTED', refundAmount: 120000 }); data = returns; }
    else if (path === '/api/v1/users/addresses') { if (request.method() === 'POST') addresses.push({ id: 1, ...body }); data = addresses; }
    else if (path === '/api/v1/users/notifications') data = { content: [], totalPages: 1 };
    else if (path === '/api/v1/auth/forgot-password') data = {};
    else if (path === '/api/v1/newsletter/subscriptions') data = {};
    else if (path === '/api/v1/cart') data = { id: 1, items: [], totalPrice: 0 };
    else if (path.includes('/system-configs')) data = [];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 200, success: true, data, message: path === '/api/v1/auth/forgot-password' ? 'Yêu cầu khôi phục đã được tiếp nhận.' : 'Success' }) });
  });
  return requests;
}

test('skin profile uses customer data and persists edits instead of invented values', async ({ page }) => {
  const requests = await setup(page);
  await page.goto('/#/profile?tab=skin');
  await expect(page.getByRole('heading', { name: 'Hồ sơ làn da của bạn' })).toBeVisible();
  await expect(page.getByText('OSNW', { exact: true })).toHaveCount(0);
  await page.getByLabel('Dị ứng đã biết').fill('Hương liệu');
  await page.getByRole('button', { name: 'Lưu hồ sơ da' }).click();
  await expect(page.getByText('Đã lưu hồ sơ do bạn cung cấp.')).toBeVisible();
  expect(requests.find(row => row.path.endsWith('/skin-profile') && row.method === 'PUT').body.allergies).toBe('Hương liệu');
  await page.reload();
  await expect(page.getByLabel('Dị ứng đã biết')).toHaveValue('Hương liệu');
});

test('customer order detail submits selected return quantity and pages through complete history', async ({ page }) => {
  const requests = await setup(page);
  await page.goto('/#/profile?tab=orders');
  await page.getByRole('button', { name: 'Chi tiết & thao tác' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('spinbutton').fill('1');
  await page.getByLabel('Lý do', { exact: true }).fill('Giao sai sản phẩm đã đặt');
  await page.getByRole('button', { name: 'Gửi yêu cầu đổi trả' }).click();
  await expect(page.getByText('#1 · Chờ xét duyệt')).toBeVisible();
  expect(requests.find(row => row.path.endsWith('/returns') && row.method === 'POST').body.lines).toEqual([{ orderItemId: 31, quantity: 1 }]);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Trang sau' }).click();
  await expect(page.getByText('ORD-22', { exact: true })).toBeVisible();
});

test('wishlist removal is persisted to the account and survives reload', async ({ page }) => {
  const requests = await setup(page);
  await page.goto('/#/profile?tab=wishlist');
  await expect(page.getByText(product.name, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Bỏ yêu thích', exact: true }).click();
  await expect(page.getByText('Bạn chưa lưu sản phẩm nào.', { exact: false })).toBeVisible();
  await page.reload();
  await expect(page.getByText('Bạn chưa lưu sản phẩm nào.', { exact: false })).toBeVisible();
  expect(requests.some(row => row.path === '/api/v1/users/wishlist/12' && row.method === 'DELETE')).toBeTruthy();
});

for (const width of [1440, 375, 320]) {
  for (const count of [1, 2, 8]) {
    test(`beauty-report: wishlist ${count} cards stay bounded at ${width}px without overflow`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      const products = Array.from({ length: count }, (_, index) => ({ ...product, id: 12 + index, name: `${product.name} ${index + 1}` }));
      await setup(page, true, products);
      await page.goto('/#/profile?tab=wishlist');
      const cards = page.locator('.account-product-grid .product-card-resolved');
      await expect(cards).toHaveCount(count);
      await expect.poll(() => cards.evaluateAll(elements => elements.every(element => {
        const rect = element.getBoundingClientRect();
        return rect.width > 0 && rect.width <= 260.5 && rect.left >= 0 && rect.right <= window.innerWidth;
      }))).toBe(true);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    });
  }
}

test('beauty-report: wishlist removal leaves one card at its original bounded width', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  const remaining = { ...product, id: 13, name: 'Sản phẩm yêu thích còn lại' };
  const requests = await setup(page, true, [product, remaining]);
  await page.goto('/#/profile?tab=wishlist');
  const cards = page.locator('.account-product-grid .product-card-resolved');
  await expect(cards).toHaveCount(2);
  const before = await cards.last().boundingBox();
  expect(before.width).toBeLessThanOrEqual(260.5);
  await cards.first().getByRole('button', { name: 'Bỏ yêu thích', exact: true }).click();
  await expect(cards).toHaveCount(1);
  await expect(cards).toContainText(remaining.name);
  await expect.poll(async () => Math.abs((await cards.boundingBox()).width - before.width)).toBeLessThan(0.5);
  expect(requests.some(row => row.path === '/api/v1/users/wishlist/12' && row.method === 'DELETE')).toBe(true);
  await page.reload();
  await expect(cards).toHaveCount(1);
  expect((await cards.boundingBox()).width).toBeLessThanOrEqual(260.5);
});

test('address book accepts destinations outside the former hardcoded cities', async ({ page }) => {
  const requests = await setup(page);
  await page.goto('/#/profile?tab=addresses');
  await page.getByLabel('Người nhận', { exact: true }).fill('Nguyễn An');
  await page.getByLabel('Số điện thoại', { exact: true }).fill('0912345678');
  await page.getByLabel('Số nhà, tên đường').fill('12 Đường Hoa');
  await page.getByLabel('Phường / xã', { exact: true }).fill('Phường Tân Phú');
  await page.getByLabel('Tỉnh / thành phố', { exact: true }).fill('Đồng Nai');
  await page.getByRole('button', { name: 'Lưu địa chỉ' }).click();
  await expect(page.getByText('Đã lưu địa chỉ.')).toBeVisible();
  expect(requests.find(row => row.path === '/api/v1/users/addresses' && row.method === 'POST').body.city).toBe('Đồng Nai');
});

test('password recovery submits email with clear server feedback', async ({ page }) => {
  const requests = await setup(page, false);
  await page.goto('/#/recover-password');
  await page.getByLabel('Email tài khoản').fill('an@example.test');
  await page.getByRole('button', { name: 'Gửi hướng dẫn qua email' }).click();
  await expect(page.getByText('Yêu cầu khôi phục đã được tiếp nhận.')).toBeVisible();
  expect(requests.find(row => row.path === '/api/v1/auth/forgot-password').body).toEqual({ email: 'an@example.test' });
});

test('payment websocket requires owner-authorized subscription token and uses server deadline', async ({ page }) => {
  await setup(page);
  const deadline = new Date(Date.now() + 90000).toISOString();
  await page.route('**/api/v1/orders/21', route => route.fulfill({ json: { data: { ...order, status: 'PENDING', paymentStatus: 'PENDING', paymentDeadline: deadline, paymentInstruction: { bankAccountNumber: '123', transferSyntax: 'ORD21', qrCodeUrl: '' } } } }));
  await page.route('**/api/v1/orders/21/payment-subscription', route => route.fulfill({ json: { data: { subscriptionToken: 'owner-only-token', expiresAt: new Date(Date.now() + 300000).toISOString() } } }));
  let socketUrl = '';
  await page.routeWebSocket('**/ws/payment?*', socket => { socketUrl = socket.url(); });
  await page.goto('/#/order-success?id=21&method=BANK');
  await expect.poll(() => socketUrl).toContain('subscriptionToken=owner-only-token');
  expect(socketUrl).not.toContain('orderCode=');
  await expect(page.getByText(/^01:[0-5][0-9]$/)).toBeVisible();
});

test('an ordinary Spa notification opens the exact appointment and self-service reschedule uses checked slots', async ({ page }) => {
  const requests = await setup(page);
  const appointment = { id: 71, userId: user.id, status: 'CONFIRMED', appointmentDate: '2026-10-20', startTime: '09:00:00', endTime: '10:00:00', items: [{ id: 72, serviceId: 11, serviceName: 'Chăm sóc da', executionStatus: 'PLANNED' }] };
  await page.route('**/api/v1/appointments/my-appointments', route => route.fulfill({ json: { data: [appointment] } }));
  await page.route('**/api/v1/appointments/71', route => route.fulfill({ json: { data: appointment } }));
  await page.route('**/api/v1/spa/appointments/71/preparation', route => route.fulfill({ json: { data: [] } }));
  await page.route('**/api/v1/spa/appointments/71/finance', route => route.fulfill({ json: { data: { appointmentId: 71, depositRequired: 0, depositCollected: 0, appliedToInvoice: 0, assessedFee: 0, feePaid: 0, feeDue: 0, refundable: 0, refunded: 0, entries: [] } } }));
  await page.route('**/api/v1/users/notifications?*', route => route.fulfill({ json: { data: { content: [{ id: 1, title: 'Lịch hẹn đã xác nhận', body: 'Mở lịch hẹn của bạn', target: 'profile?tab=appointments&appointment=71' }], totalPages: 1 } } }));
  await page.route('**/api/v1/appointments/71/available-slots?*', route => route.fulfill({ json: { data: ['10:30:00'] } }));
  await page.goto('/#/profile?tab=notifications');
  await page.getByRole('button', { name: 'Mở nội dung liên quan' }).click();
  await expect(page.getByRole('dialog')).toContainText('Lịch hẹn #71');
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Đổi lịch', exact: true }).click();
  await page.getByLabel('Giờ còn trống').selectOption('10:30:00');
  await page.getByLabel('Lý do đổi lịch').fill('Khách muốn đến muộn hơn');
  await page.getByRole('button', { name: 'Xác nhận đổi lịch' }).click();
  await expect.poll(() => requests.find(row => row.path === '/api/v1/appointments/71/reschedule')).toBeTruthy();
  expect(requests.find(row => row.path === '/api/v1/appointments/71/reschedule').body).toMatchObject({ appointmentDate: '2026-10-20', startTime: '10:30:00', notes: 'Khách muốn đến muộn hơn' });
});

test('missing account data never grants a tier, points or a valid booking status', async ({ page }) => {
  await setup(page);
  const incomplete = { ...user, membershipTier: null, loyaltyPoints: null };
  await page.route('**/api/v1/auth/profile', route => route.fulfill({ json: { data: incomplete } }));
  await page.route('**/api/v1/spa/tickets/my-tickets', route => route.fulfill({ json: { data: [{ id: 80, packageName: 'Vé thiếu thông tin' }] } }));
  await page.route('**/api/v1/appointments/my-appointments', route => route.fulfill({ json: { data: [{ id: 71, userId: user.id, appointmentDate: '2026-10-20', items: [] }] } }));
  await page.goto('/#/profile?tab=tickets');
  await expect(page.getByText('Chưa có thông tin hạng', { exact: true })).toBeVisible();
  await expect(page.getByText('Chưa có thông tin điểm Loyalty', { exact: true })).toBeVisible();
  await expect(page.getByText(/điểm để lên hạng tiếp theo/)).toHaveCount(0);
  const ticket = page.locator('.customer-profile-ticket-grid .luxury-card');
  await expect(ticket).toContainText('Chưa có trạng thái');
  await expect(ticket).toContainText('Còn lại: Chưa có thông tin');
  await expect(ticket).toContainText('Đã sử dụng: — / —');
  await expect(ticket.getByText('Còn hiệu lực', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Lịch hẹn (1)', exact: true }).click();
  const appointment = page.locator('.luxury-card').filter({ hasText: 'SPA-APT-#71' });
  await expect(appointment).toContainText('Chưa có trạng thái');
  await expect(appointment.getByText('Chờ xác nhận', { exact: true })).toHaveCount(0);
  await expect(appointment.getByRole('button', { name: 'Hủy lịch hẹn' })).toHaveCount(0);
  await expect(appointment.getByRole('button', { name: 'Đổi lịch', exact: true })).toHaveCount(0);
});

test('zero account values from the API remain visible alongside explicit member and active statuses', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/auth/profile', route => route.fulfill({ json: { data: { ...user, membershipTier: 'MEMBER', loyaltyPoints: 0 } } }));
  await page.route('**/api/v1/spa/tickets/my-tickets', route => route.fulfill({ json: { data: [{ id: 80, packageName: 'Vé có số liệu', status: 'ACTIVE', totalSessions: 0, usedSessions: 0, remainingSessions: 0 }] } }));
  await page.route('**/api/v1/appointments/my-appointments', route => route.fulfill({ json: { data: [{ id: 71, userId: user.id, status: 'PENDING', appointmentDate: '2026-10-20', items: [] }] } }));
  await page.goto('/#/profile?tab=tickets');
  await expect(page.getByText('Hạng Thành Viên', { exact: true })).toBeVisible();
  await expect(page.getByText('0 Điểm Loyalty', { exact: true })).toBeVisible();
  await expect(page.getByText(/điểm để lên hạng tiếp theo/)).toContainText('1000');
  const ticket = page.locator('.customer-profile-ticket-grid .luxury-card');
  await expect(ticket).toContainText('Còn hiệu lực');
  await expect(ticket).toContainText('Còn lại: 0 buổi');
  await expect(ticket).toContainText('Đã sử dụng: 0 / 0');
  await page.getByRole('button', { name: 'Lịch hẹn (1)', exact: true }).click();
  await expect(page.locator('.luxury-card').filter({ hasText: 'SPA-APT-#71' })).toContainText('Chờ xác nhận');
});

test('ProductCard distinguishes missing facts from valid zero and uses API-confirmed cart values', async ({ page }) => {
  const requests = await setup(page);
  const noFacts = { id: 13, name: 'Sản phẩm thiếu thông tin' };
  const zeroProduct = { ...product, id: 14, name: 'Mẫu thử giá không đồng', minPrice: 0, price: 0, totalSold: 0, totalReviews: 0, averageRating: 0, variants: [{ id: 45, sku: 'FREE-45', price: 0, isActive: true, stockQuantity: 3, isDefault: true }] };
  const ratedProduct = { ...product, id: 15, name: 'Sản phẩm đủ thông tin', averageRating: 4.5, totalReviews: 2, totalSold: 8 };
  await page.route('**/api/v1/users/wishlist', route => route.fulfill({ json: { data: [noFacts, zeroProduct, ratedProduct] } }));
  await page.route('**/api/v1/cart/add', route => {
    requests.push({ path: '/api/v1/cart/add', body: route.request().postDataJSON() });
    return route.fulfill({ json: { data: { id: 1, items: [{ id: 1, variantId: 45, sku: 'FREE-45', variantName: zeroProduct.name, price: 0, quantity: 1, available: true }], totalPrice: 0 } } });
  });
  await page.goto('/#/profile?tab=wishlist');
  const missing = page.getByRole('article', { name: `Xem sản phẩm ${noFacts.name}`, exact: true });
  await expect(missing).toContainText('Chưa có thông tin giá');
  await expect(missing).toContainText('Chưa có thông tin thương hiệu');
  await expect(missing).toContainText('Chưa có dữ liệu đánh giá');
  await expect(missing.locator('img')).toHaveAttribute('src', /^data:image\/svg\+xml,/);
  await expect(missing.locator('.product-card-rating')).not.toContainText(/Mới|Còn hàng|Đã bán/);
  await expect(missing.locator('.product-card-price')).not.toContainText(/0\s*₫/);
  const zero = page.getByRole('article', { name: `Xem sản phẩm ${zeroProduct.name}`, exact: true });
  await expect(zero.locator('.product-card-price')).toHaveText(/0\s*₫/);
  await expect(zero).toContainText('Đã bán 0');
  await expect(zero).toContainText('Chưa có đánh giá');
  const rated = page.getByRole('article', { name: `Xem sản phẩm ${ratedProduct.name}`, exact: true });
  await expect(rated).toContainText('Beauty');
  await expect(rated.locator('.product-card-price')).toHaveText(/120\.000\s*₫/);
  await expect(rated.locator('.product-card-rating')).toContainText('4.5');
  await expect(rated.locator('.product-card-rating')).toContainText('(2)');
  await expect(rated).toContainText('Đã bán 8');
  await zero.hover();
  await zero.getByRole('button', { name: `Thêm ${zeroProduct.name} vào giỏ`, exact: true }).click();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toContainText(zeroProduct.name);
  expect(requests.find(row => row.path === '/api/v1/cart/add').body).toMatchObject({ variantId: 45, quantity: 1 });
  expect(requests.find(row => row.path === '/api/v1/cart/add').body).not.toHaveProperty('price');
});
