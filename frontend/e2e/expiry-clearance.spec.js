import { test, expect } from '@playwright/test';

const normal = { id: 42, name: 'Serum kiểm thử', slug: 'serum-kiem-thu', status: 'ACTIVE', minPrice: 100000, maxPrice: 150000,
  variants: [
    { id: 11, sku: 'DEFAULT-11', variantName: '50 ml', price: 150000, isActive: true, isDefault: true, stockQuantity: 8 },
    { id: 22, sku: 'CLEARANCE-22', variantName: '30 ml', price: 100000, discountPrice: 0, isActive: true, stockQuantity: 3 },
  ], images: [], categories: [], totalSold: 5 };
const clearance = { ...normal, variants: undefined, minPrice: 0, maxPrice: 0, earliestExpirationDate: '2026-12-01', daysRemaining: 45,
  clearanceStock: 3, clearanceVariant: normal.variants[1] };

async function mock(page, { missing = false, empty = false, failed = false, soldOut = false } = {}) {
  const requests = [], errors = [];
  const product = { ...normal, variants: missing ? [normal.variants[0]] : normal.variants.map(item => soldOut && item.id === 22 ? { ...item, stockQuantity: 0 } : item) };
  page.on('pageerror', error => errors.push(error.message));
  await page.route('https://**/*', route => route.abort());
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data = [];
    if (path === '/api/v1/products/expiring-soon') {
      if (failed) return route.fulfill({ status: 503, json: { message: 'Unavailable' } });
      data = empty ? [] : [clearance];
    } else if (path === '/api/v1/products/42') data = product;
    else if (path === '/api/v1/products' || path === '/api/v1/products/search') data = { content: [normal], totalElements: 1, totalPages: 1, page: 0 };
    else if (path === '/api/v1/cart') data = { id: 1, items: [], totalPrice: 0 };
    else if (path === '/api/v1/cart/add') {
      const body = route.request().postDataJSON(); requests.push(body);
      data = { id: 1, items: [{ id: 1, variantId: body.variantId, sku: 'CLEARANCE-22', variantName: normal.name, quantity: 1, price: 0, available: true }], totalPrice: 0 };
    } else if (path === '/api/v1/reviews') data = { content: [], totalElements: 0, totalPages: 0 };
    else if (path === '/api/v1/system-configs/public') data = { 'shipping.free_threshold': 249000, 'shipping.default_fee': 25000 };
    else if (path === '/api/v1/chatbot/health') data = { status: 'ok' };
    return route.fulfill({ json: { status: 200, success: true, data } });
  });
  return { requests, errors };
}

test('clearance card submits exact nondefault SKU and honest data', async ({ page }) => {
  const api = await mock(page);
  await page.goto('/');
  const area = page.locator('#flash-sale');
  await expect(area.locator('.product-card-resolved')).toHaveCount(1);
  await expect(area).toContainText('CLEARANCE-22');
  await expect(area).toContainText('45 ngày');
  await expect(area.getByText(/đã bán.*%|kết thúc sau/i)).toHaveCount(0);
  await area.scrollIntoViewIfNeeded();
  await page.screenshot({ path: test.info().outputPath('clearance-desktop.png') });
  await area.getByRole('button', { name: `Thêm ${normal.name} vào giỏ`, exact: true }).click();
  await expect.poll(() => api.requests.length).toBe(1);
  expect(api.requests[0].variantId).toBe(22);
  expect(api.errors).toEqual([]);
});

test('clearance quick view keeps requested SKU, never chooses default when removed', async ({ page }) => {
  const api = await mock(page, { missing: true });
  await page.goto('/');
  await page.locator('#flash-sale').getByTitle('Xem nhanh chi tiết').click();
  const dialog = page.getByRole('dialog', { name: normal.name, exact: true });
  await expect(dialog).toContainText('Phân loại đã chọn không còn khả dụng');
  await expect(dialog.getByRole('button', { name: /Thêm Vào Giỏ Hàng/ })).toBeDisabled();
  expect(api.requests).toEqual([]);
  expect(api.errors).toEqual([]);
});

test('clearance detail link selects exact SKU, missing ID cannot silently switch', async ({ page }) => {
  const api = await mock(page);
  await page.goto('/#/product/42?variantId=22');
  const actions = page.locator('.pd-purchase-actions');
  await actions.getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true }).click();
  await expect.poll(() => api.requests.length).toBe(1);
  expect(api.requests[0].variantId).toBe(22);
  await page.goto('/#/product/42?variantId=999');
  await expect(page.locator('.pd-purchase-actions').getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true })).toBeDisabled();
  expect(api.errors).toEqual([]);
});

test('retry preserves manual selection rather than switching back to default SKU', async ({ page }) => {
  const api = await mock(page, { soldOut: true });
  await page.goto('/#/product/42');
  await page.getByRole('group', { name: 'Phân loại sản phẩm' }).getByRole('button', { name: '30 ml', exact: true }).click();
  await page.getByRole('button', { name: 'Tải lại phân loại đã chọn', exact: true }).click();
  const selected = page.getByRole('group', { name: 'Phân loại sản phẩm' }).getByRole('button', { name: '30 ml', exact: true });
  await expect(selected).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.pd-purchase-actions').getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true })).toBeDisabled();
  await page.goto('/');
  await page.locator('#store-catalog').getByTitle('Xem nhanh chi tiết').click();
  const dialog = page.getByRole('dialog', { name: normal.name, exact: true });
  await dialog.getByRole('button', { name: 'CLEARANCE-22', exact: true }).click();
  await dialog.getByRole('button', { name: 'Tải lại phân loại', exact: true }).click();
  await expect(dialog.getByRole('button', { name: 'CLEARANCE-22', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(dialog.getByRole('button', { name: /Thêm Vào Giỏ Hàng/ })).toBeDisabled();
  expect(api.requests).toEqual([]);
  expect(api.errors).toEqual([]);
});

test('empty explicit variant ID cannot retain previous default selection', async ({ page }) => {
  const api = await mock(page);
  await page.goto('/#/product/42');
  await expect(page.locator('.pd-purchase-actions').getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true })).toBeEnabled();
  await page.evaluate(() => { window.location.hash = '/product/42?variantId='; });
  await expect(page.locator('.pd-purchase-actions').getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true })).toBeDisabled();
  expect(api.requests).toEqual([]);
});

for (const failed of [false, true]) {
  test(`clearance ${failed ? '503' : 'empty'} never uses normal catalog fallback`, async ({ page }) => {
    const api = await mock(page, { empty: !failed, failed });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    const area = page.locator('#flash-sale');
    await expect(area).toContainText(failed ? 'Chưa tải được sản phẩm xả kho' : 'Hiện chưa có sản phẩm xả kho đủ điều kiện');
    await expect(area.locator('.product-card-resolved')).toHaveCount(0);
    await expect(area.getByText(/kết thúc sau|đã bán.*%|chỉ còn 10/i)).toHaveCount(0);
    await area.scrollIntoViewIfNeeded();
    await page.screenshot({ path: test.info().outputPath(`clearance-${failed ? 'error' : 'empty'}-mobile.png`) });
    expect(api.errors).toEqual([]);
  });
}
