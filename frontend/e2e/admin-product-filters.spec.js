import { test, expect } from '@playwright/test';

const admin = { id: 1, username: 'admin', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: [] };
const path = '/api/v1/admin/products';
const row = { id: 42, name: 'Serum phục hồi', slug: 'serum-phuc-hoi', status: 'ACTIVE', categoryId: 4, brandId: 5, minPrice: 100000, maxPrice: 150000 };

async function setup(page) {
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);
  const state = { calls: [], mutations: [], errors: [], fail: false, catalogFail: false, beforeList: null, lastRemoved: false, lastEdited: false };
  page.on('pageerror', error => state.errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') state.errors.push(message.text()); });
  page.on('requestfailed', request => state.errors.push(`${request.url()}: ${request.failure()?.errorText}`));
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url());
    const method = request.method();
    let data = [], status = 200;
    if (url.pathname === '/api/v1/auth/profile') data = admin;
    else if (url.pathname === '/api/v1/categories') {
      status = state.catalogFail ? 503 : 200;
      data = [{ id: 4, name: 'Serum', slug: 'serum' }, { id: 9, name: 'Serum phục hồi', slug: 'serum-phuc-hoi', parentId: 4 }];
    } else if (url.pathname === '/api/v1/brands') data = { content: [{ id: 5, name: 'Beauty Lab', slug: 'beauty-lab' }], totalPages: 1 };
    else if (url.pathname === path) {
      state.calls.push(new URLSearchParams(url.search));
      if (state.beforeList) await state.beforeList(url.searchParams);
      status = state.fail ? 503 : 200;
      const current = Number(url.searchParams.get('page') || 0);
      const keyword = url.searchParams.get('keyword');
      const empty = keyword === 'empty';
      const shrinking = state.lastRemoved || state.lastEdited;
      const totalElements = empty ? 0 : shrinking ? 15 : 16;
      const content = empty || (shrinking && current > 0) ? [] : [{ ...row, id: current ? 43 : 42,
        name: `${keyword || 'Serum'} ${current ? 'trang hai' : 'trang một'}`, status: url.searchParams.get('status') || 'ACTIVE' }];
      data = { content, page: current, size: 15, totalPages: Math.ceil(totalElements / 15), totalElements };
    } else if (/\/admin\/products\/\d+$/.test(url.pathname)) {
      if (method === 'DELETE') state.lastRemoved = true;
      if (method === 'PUT') state.lastEdited = true;
      if (method !== 'GET') state.mutations.push({ method, body: request.postDataJSON() });
      data = { ...row, id: Number(url.pathname.split('/').at(-1)), description: '', shortDescription: '', categories: [{ id: 4 }], brand: { id: 5 }, variants: [] };
    }
    await route.fulfill({ status, json: { status, success: status === 200, data, message: status === 503 ? 'Lỗi kiểm thử' : '' } });
  });
  await page.goto('/#/admin/products');
  try { await expect(page.getByText('Tìm thấy 16 sản phẩm', { exact: true })).toBeVisible(); }
  catch (error) { throw new Error(`${error.message}\nBrowser errors: ${state.errors.join('\n')}`); }
  return state;
}

const form = page => page.getByRole('form', { name: 'Tìm kiếm và lọc sản phẩm' });
const resultRow = page => page.getByRole('row').filter({ hasText: 'trang một' });
const latest = state => Object.fromEntries(state.calls.at(-1));

async function apply(page, keyword = 'Serum') {
  await form(page).getByLabel('Tìm theo tên sản phẩm').fill(keyword);
  await form(page).getByRole('button', { name: 'Áp dụng', exact: true }).click();
}

test('Apply/Enter/Clear combine filters and preserve applied values across pages, refresh and tabs', async ({ page }, testInfo) => {
  const state = await setup(page);
  const count = state.calls.length;
  await form(page).getByLabel('Tìm theo tên sản phẩm').fill('  Serum %_! & B5  ');
  await form(page).getByLabel('Lọc danh mục').selectOption('9');
  await form(page).getByLabel('Lọc thương hiệu').selectOption('5');
  await form(page).getByLabel('Lọc trạng thái').selectOption('COMING_SOON');
  expect(state.calls).toHaveLength(count);
  await form(page).getByLabel('Tìm theo tên sản phẩm').press('Enter');
  await expect.poll(() => latest(state)).toMatchObject({ keyword: 'Serum %_! & B5', categoryId: '9', brandId: '5', status: 'COMING_SOON', page: '0', size: '15' });
  expect(state.calls.at(-1).getAll('sort')).toEqual(['createdAt,desc', 'id,desc']);
  await expect(resultRow(page)).toContainText('Sắp ra mắt');
  await page.screenshot({ path: testInfo.outputPath('admin-product-filters.png'), fullPage: true });
  await form(page).getByLabel('Tìm theo tên sản phẩm').fill('Chưa áp dụng');
  await page.getByRole('button', { name: 'Trang tiếp', exact: true }).click();
  await expect.poll(() => latest(state)).toMatchObject({ keyword: 'Serum %_! & B5', page: '1' });
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: 'trang hai' })).toBeVisible();
  expect(latest(state)).toMatchObject({ keyword: 'Serum %_! & B5', page: '1' });
  await page.getByRole('button', { name: /Danh mục sản phẩm \(/ }).click();
  await page.getByRole('button', { name: /Sản phẩm & Biến thể SKU/ }).click();
  await expect(page.getByRole('row').filter({ hasText: 'trang hai' })).toBeVisible();
  expect(latest(state)).toMatchObject({ keyword: 'Serum %_! & B5', page: '1' });
  await form(page).getByRole('button', { name: 'Xóa bộ lọc', exact: true }).click();
  await expect.poll(() => latest(state)).toEqual({ page: '0', size: '15', sort: 'id,desc' });
  await expect(form(page).getByLabel('Tìm theo tên sản phẩm')).toHaveValue('');
  await expect(form(page).getByLabel('Lọc trạng thái')).toHaveValue('');
});

test('empty/error/retry and incomplete catalog never masquerade as successful results', async ({ page }) => {
  const state = await setup(page);
  await apply(page, 'empty');
  await expect(page.getByText('Không có sản phẩm phù hợp với điều kiện tìm kiếm.')).toBeVisible();
  state.fail = true;
  await apply(page, 'retry');
  await expect(page.getByRole('alert')).toContainText('Không tải được sản phẩm');
  await expect(page.getByText('Không có sản phẩm phù hợp với điều kiện tìm kiếm.')).toHaveCount(0);
  state.fail = false;
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(resultRow(page)).toContainText('retry');
  expect(latest(state).keyword).toBe('retry');
  state.catalogFail = true;
  await page.getByRole('button', { name: /Danh mục sản phẩm \(/ }).click();
  await page.getByRole('button', { name: /Sản phẩm & Biến thể SKU/ }).click();
  await expect(page.getByText('Lựa chọn lọc chưa đầy đủ', { exact: true })).toBeVisible();
  state.catalogFail = false;
  await page.getByRole('button', { name: 'Tải lại danh mục và thương hiệu' }).click();
  await expect(page.getByText('Lựa chọn lọc chưa đầy đủ', { exact: true })).toHaveCount(0);
});

test('stale success or failure cannot replace newer filters', async ({ page }) => {
  const state = await setup(page);
  let release;
  state.beforeList = params => params.get('keyword') === 'slow' ? new Promise(resolve => { release = resolve; }) : undefined;
  await apply(page, 'slow');
  await expect.poll(() => typeof release).toBe('function');
  await apply(page, 'new');
  await expect(resultRow(page)).toContainText('new');
  release();
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(resultRow(page)).toContainText('new');
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('delete on last page retains filters and reloads the last valid page', async ({ page }) => {
  const state = await setup(page);
  await apply(page, 'Delete');
  await page.getByRole('button', { name: 'Trang tiếp', exact: true }).click();
  const last = page.getByRole('row').filter({ hasText: 'trang hai' });
  await expect(last).toBeVisible();
  page.on('dialog', dialog => dialog.accept());
  await last.getByRole('button', { name: 'Xóa sản phẩm', exact: true }).click();
  await expect(page.getByText('Tìm thấy 15 sản phẩm', { exact: true })).toBeVisible();
  await expect(resultRow(page)).toContainText('Delete');
  expect(latest(state)).toMatchObject({ keyword: 'Delete', page: '0' });
  expect(state.mutations.at(-1).method).toBe('DELETE');
});

test('editing retains filters and phone form stays inside the viewport', async ({ page }, testInfo) => {
  const state = await setup(page);
  await apply(page, 'Edit');
  await resultRow(page).getByRole('button', { name: 'Sửa', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: /Lưu/ })).toBeEnabled();
  await dialog.getByRole('button', { name: /Lưu/ }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByText('Tìm thấy 15 sản phẩm', { exact: true })).toBeVisible();
  expect(latest(state)).toMatchObject({ keyword: 'Edit', page: '0' });
  expect(state.errors).toEqual([]);
  await page.setViewportSize({ width: 390, height: 844 });
  await form(page).scrollIntoViewIfNeeded();
  expect(await form(page).evaluate(element => {
    const rect = element.getBoundingClientRect();
    return rect.left >= 0 && rect.right <= window.innerWidth;
  })).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('admin-product-filters-mobile.png'), fullPage: true });
});
