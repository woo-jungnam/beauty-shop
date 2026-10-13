import { test, expect } from '@playwright/test';

const config = { 'weight.view': 1, 'weight.search_click': 1.5, 'weight.add_to_cart': 3, 'weight.purchase': 5,
  history_window: 100, half_life_days: 30, max_product_weight: 10, min_similarity: 0,
  catalog_refresh_seconds: 30, exclude_seen: true, in_stock_only: true,
  'field.name': 1, 'field.category': 2, 'field.brand': 0.5, 'field.description': 1,
  'field.ingredients': 3, 'field.skin': 2, 'field.concerns': 2, 'field.formula': 0.5 };
const user = { id: 99, username: 'rec-admin', fullName: 'Quản lý', roles: ['ROLE_ADMIN'] };

async function setup(page, options = {}) {
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, user);
  const calls = [];
  let values = { ...config };
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    let data = { content: [], totalElements: 0 };
    let message = 'Thành công';
    if (path.includes('/auth/me') || path.includes('/users/me') || path.endsWith('/auth/profile')) data = user;
    else if (path.endsWith('/recommendations/config')) {
      if (request.method() === 'PUT') {
        const body = request.postDataJSON(); calls.push({ path, body });
        if (options.saveFailure) return route.fulfill({ status: 503, json: { message: 'Chưa lưu được cấu hình' } });
        values = body;
        data = { config: values, applied: !options.pendingApply };
        message = options.pendingApply ? 'Đã lưu cấu hình; đang chờ dịch vụ đọc lại.' : 'Đã lưu và áp dụng cấu hình gợi ý';
      } else data = values;
    } else if (path.endsWith('/recommendations/status') || path.endsWith('/recommendations/refresh')) {
      if (options.statusFailure) return route.fulfill({ status: 503, json: { message: 'Chưa kết nối được hệ thống gợi ý' } });
      data = { status: 'ok', indexed_products: 229, features: 5000, index_version: 'fixture-v1', last_refresh: '2026-10-13T06:00:00Z', config: values };
    } else if (path.endsWith('/recommendations/preview')) {
      calls.push({ path, query: Object.fromEntries(url.searchParams) });
      data = { source: options.coldStart ? 'COLD_START' : 'CONTENT_BASED', total: 1, matched_history_products: 0,
        items: [{ id: 2, name: 'Serum phục hồi', score: options.coldStart ? null : 0.75, source: options.coldStart ? 'POPULAR' : 'CONTENT_BASED', reason: options.coldStart ? 'Sản phẩm phổ biến.' : 'Tương đồng về thành phần, loại da.' }] };
    }
    await route.fulfill({ json: { status: 200, message, data } });
  });
  await page.goto('/#/admin/operations');
  await page.getByRole('button', { name: 'Gợi ý Content-Based', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Gợi ý sản phẩm Content-Based' })).toBeVisible();
  return calls;
}

test('Admin xem trạng thái và lưu cấu hình có áp dụng', async ({ page }) => {
  const calls = await setup(page);
  await expect(page.getByText('229 sản phẩm · 5000 đặc trưng')).toBeVisible();
  await page.getByLabel('Trọng số mua thành công').fill('8');
  await page.getByRole('button', { name: 'Lưu và áp dụng cấu hình' }).click();
  await expect(page.getByRole('status')).toContainText('Đã lưu và áp dụng');
  expect(calls[0].body['weight.purchase']).toBe(8);
});

test('Không cho lưu khi mọi trọng số bằng không', async ({ page }) => {
  const calls = await setup(page);
  for (const name of ['Trọng số xem sản phẩm','Trọng số chọn kết quả tìm kiếm','Trọng số thêm giỏ thành công','Trọng số mua thành công']) await page.getByLabel(name).fill('0');
  await page.getByRole('button', { name: 'Lưu và áp dụng cấu hình' }).click();
  await expect(page.getByRole('alert')).toContainText('ít nhất một trọng số');
  expect(calls).toHaveLength(0);
});

test('Lỗi lưu vẫn giữ giá trị đang nhập', async ({ page }) => {
  await setup(page, { saveFailure: true });
  await page.getByLabel('Trọng số mua thành công').fill('8');
  await page.getByRole('button', { name: 'Lưu và áp dụng cấu hình' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByLabel('Trọng số mua thành công')).toHaveValue('8');
});

test('Lưu chưa áp dụng không báo thành công giả', async ({ page }) => {
  await setup(page, { pendingApply: true });
  await page.getByRole('button', { name: 'Lưu và áp dụng cấu hình' }).click();
  await expect(page.getByRole('alert')).toContainText('đang chờ');
});

test('Xem thử hiển thị điểm cosine và lý do thực tế', async ({ page }) => {
  const calls = await setup(page);
  await page.getByRole('button', { name: 'Xem gợi ý', exact: true }).click();
  await expect(page.getByText(/Cosine: 0.7500/)).toBeVisible();
  await expect(page.getByText(/Tương đồng về thành phần/)).toBeVisible();
  expect(calls[0].query.productId).toBe('1');
});

test('Popular fallback không có điểm cosine giả', async ({ page }) => {
  await setup(page, { coldStart: true });
  await page.getByLabel('Kiểu gợi ý').selectOption('user');
  await page.getByRole('button', { name: 'Xem gợi ý', exact: true }).click();
  await expect(page.getByText(/Không áp dụng cho fallback phổ biến/)).toBeVisible();
});

test('Dịch vụ lỗi vẫn tải được cấu hình và không hiển thị online', async ({ page }) => {
  await setup(page, { statusFailure: true });
  await expect(page.getByLabel('Trọng số mua thành công')).toHaveValue('5');
  await expect(page.getByText('Đang hoạt động', { exact: true })).toHaveCount(0);
});

test('Giao diện quản trị không tràn ngang trên mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy();
});
