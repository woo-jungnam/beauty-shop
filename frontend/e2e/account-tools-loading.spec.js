import { test, expect } from '@playwright/test';

const user = { id: 7, username: 'account-check', fullName: 'Khách thử nghiệm', email: 'account@example.test', roles: ['ROLE_CUSTOMER'] };
const products = Array.from({ length: 9 }, (_, i) => ({ id: i + 1, name: `Sản phẩm đã lưu ${i + 1}`, minPrice: 120000 }));

async function setup(page, respond) {
  await page.addInitScript(profile => {
    localStorage.setItem('beautyshop_admin_token', 'test-access-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(profile));
  }, user);
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    if (route.request().method() !== 'GET') throw new Error(`Unexpected write: ${route.request().method()} ${url.pathname}`);
    const result = await respond(url);
    if (result) return route.fulfill(result);
    let data = [];
    if (url.pathname === '/api/v1/auth/profile' || url.pathname === '/api/v1/users/profile') data = user;
    if (url.pathname === '/api/v1/users/order-statistics') data = { totalOrders: 0, totalPaid: 0, totalRefunded: 0, netSpent: 0 };
    if (url.pathname === '/api/v1/orders/my-orders') data = { content: [], totalPages: 0, totalElements: 0 };
    if (url.pathname === '/api/v1/cart') data = { id: 1, items: [], totalPrice: 0 };
    return route.fulfill({ json: { data } });
  });
}

test('wishlist initial delay, pagination and refresh retain verified cards without false empty state', async ({ page }) => {
  let release;
  let waiting = new Promise(resolve => { release = resolve; });
  await setup(page, async url => {
    if (url.pathname !== '/api/v1/users/wishlist') return;
    await waiting;
    return { json: { data: products } };
  });
  await page.goto('/#/profile?tab=wishlist');
  await expect(page.getByRole('status', { name: 'Đang tải sản phẩm yêu thích…' })).toBeVisible();
  await expect(page.getByText('Bạn chưa lưu sản phẩm nào.', { exact: false })).toHaveCount(0);
  release();
  const cards = page.locator('.account-collection-grid .product-card-resolved');
  await expect(cards).toHaveCount(8);
  const pagination = page.getByRole('navigation', { name: 'Phân trang sản phẩm yêu thích' });
  await pagination.getByRole('button', { name: 'Trang sau' }).click();
  await expect(cards).toHaveCount(1);
  await expect(cards).toContainText(products[8].name);
  waiting = new Promise(resolve => { release = resolve; });
  await page.evaluate(() => window.dispatchEvent(new Event('wishlist:changed')));
  await expect(page.getByText('Đang cập nhật sản phẩm yêu thích…')).toBeVisible();
  await expect(cards).toHaveCount(1);
  await expect(pagination.getByRole('button', { name: 'Trang trước' })).toBeDisabled();
  release();
  await expect(pagination.getByRole('button', { name: 'Trang trước' })).toBeEnabled();
  await expect(cards).toContainText(products[8].name);
});

test('ticket history waits for verified data, retries and hides previous movement page', async ({ page }) => {
  let fail = true, release;
  const waiting = new Promise(resolve => { release = resolve; });
  await setup(page, async url => {
    if (url.pathname === '/api/v1/spa/tickets/my-tickets') return { json: { data: [{ id: 80, nameSnapshot: 'Vé kiểm tra', packageName: 'Vé kiểm tra', benefitType: 'TREATMENT_COURSE', status: 'ACTIVE', remainingSessions: 2, totalSessions: 2, usedSessions: 0 }] } };
    if (url.pathname === '/api/v1/spa/tickets/80/refunds') return { json: { data: [] } };
    if (url.pathname !== '/api/v1/spa/tickets/80/movements') return;
    if (fail) return { status: 503, json: { message: 'Lịch sử tạm ngừng' } };
    const index = Number(url.searchParams.get('page'));
    if (index === 1) await waiting;
    return { json: { data: { content: [{ id: index + 1, createdAt: '2026-01-01', operation: 'PURCHASE', reason: `Biến động trang ${index + 1}` }], totalPages: 2 } } };
  });
  await page.goto('/#/profile?tab=tickets');
  await page.getByRole('button', { name: 'Xem lịch sử vé', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Lịch sử tạm ngừng' })).toBeVisible();
  await expect(page.getByText('Chưa có biến động được ghi nhận.', { exact: true })).toHaveCount(0);
  fail = false;
  await page.getByRole('button', { name: 'Thử lại lịch sử vé' }).click();
  await expect(page.getByText('Biến động trang 1', { exact: true })).toBeVisible();
  await page.getByRole('navigation', { name: 'Phân trang lịch sử vé' }).getByRole('button', { name: 'Trang sau' }).click();
  await expect(page.getByRole('status', { name: 'Đang tải lịch sử vé…' })).toBeVisible();
  await expect(page.getByText('Biến động trang 1', { exact: true })).toHaveCount(0);
  release();
  await expect(page.getByText('Biến động trang 2', { exact: true })).toBeVisible();
});

test('address pages and unsaved profile fields survive a manual refresh', async ({ page }) => {
  const addresses = Array.from({ length: 5 }, (_, i) => ({ id: i + 1, recipientName: `Người nhận ${i + 1}`, phone: '0912345678', address: '12 Đường Hoa', city: 'Đồng Nai' }));
  await setup(page, async url => {
    if (url.pathname === '/api/v1/users/addresses') return { json: { data: addresses } };
  });
  await page.goto('/#/profile?tab=addresses');
  const list = page.locator('.account-address-list');
  await expect(list.locator('article')).toHaveCount(4);
  await list.getByRole('button', { name: 'Trang sau' }).click();
  await expect(list.locator('article')).toHaveCount(1);
  await expect(list).toContainText('Người nhận 5');
  await page.reload();
  await expect(list.locator('article')).toHaveCount(4);
  await page.goto('/#/profile?tab=settings');
  await page.getByLabel('Họ và tên').fill('Tên đang soạn');
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Làm mới', exact: true })).toBeEnabled();
  await expect(page.getByLabel('Họ và tên')).toHaveValue('Tên đang soạn');
});

test('notification and promotion failures stay separate; page changes never show previous history', async ({ page }) => {
  let fail = true, release;
  const waiting = new Promise(resolve => { release = resolve; });
  await setup(page, async url => {
    if (url.pathname === '/api/v1/promotions/available') return { status: 503, json: { message: 'Ưu đãi tạm ngừng' } };
    if (url.pathname !== '/api/v1/users/notifications') return;
    if (fail) return { status: 503, json: { message: 'Thông báo tạm ngừng' } };
    const index = Number(url.searchParams.get('page'));
    if (index === 1) await waiting;
    return { json: { data: { content: [{ id: index + 1, title: `Thông báo trang ${index + 1}`, body: 'Nội dung đã xác minh', readAt: '2026-01-01' }], totalPages: 2 } } };
  });
  await page.goto('/#/profile?tab=notifications');
  await expect(page.getByRole('alert').filter({ hasText: 'Không tải được thông báo:' })).toBeVisible();
  await expect(page.getByRole('alert').filter({ hasText: 'Không tải được mã ưu đãi:' })).toBeVisible();
  await expect(page.getByText('Chưa có thông báo.', { exact: true })).toHaveCount(0);
  fail = false;
  await page.getByRole('button', { name: 'Thử lại thông báo', exact: true }).click();
  await expect(page.getByText('Thông báo trang 1', { exact: true })).toBeVisible();
  await page.getByRole('navigation', { name: 'Phân trang thông báo' }).getByRole('button', { name: 'Trang sau' }).click();
  await expect(page.getByRole('status', { name: 'Đang tải thông báo…' })).toBeVisible();
  await expect(page.getByText('Thông báo trang 1', { exact: true })).toHaveCount(0);
  release();
  await expect(page.getByText('Thông báo trang 2', { exact: true })).toBeVisible();
});
