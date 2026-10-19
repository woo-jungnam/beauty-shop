import { test, expect } from '@playwright/test';

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS9sAAAAASUVORK5CYII=', 'base64');
const upload = name => ({ name, mimeType: 'image/png', buffer: png });
const response = name => ({ status: 200, data: { matchStatus: 'CANDIDATES', indexVersion: 'test', items: [
  { product: { id: 123, name, minPrice: 150000, maxPrice: 150000, status: 'ACTIVE', brandName: 'Thương hiệu thử nghiệm' },
    inStock: true, detailPath: 'product/123' },
] } });

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', async route => {
    await route.fulfill({ json: { status: 200, data: [] } });
  });
});

test('uploads multipart, shows results and opens existing detail route', async ({ page }) => {
  await page.route('**/api/v1/products/search-by-image', async route => {
    expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=');
    expect(route.request().postDataBuffer().toString()).toContain('name="image"');
    await route.fulfill({ json: response('Sản phẩm ảnh kiểm thử') });
  });
  await page.goto('/#/image-search');
  await expect(page.getByRole('heading', { name: 'Tìm sản phẩm bằng ảnh' })).toBeVisible();
  await page.getByLabel('Chọn hoặc chụp ảnh sản phẩm').setInputFiles(upload('query.png'));
  await expect(page.getByAltText('Ảnh sản phẩm bạn đã chọn')).toBeVisible();
  await page.locator('.image-search-panel').getByRole('button', { name: 'Tìm bằng ảnh', exact: true }).click();
  await expect(page.getByText('Sản phẩm ảnh kiểm thử', { exact: true })).toBeVisible();
  await expect(page.getByText('Còn hàng', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem chi tiết và chọn dung tích' }).click();
  await expect(page).toHaveURL(/#\/product\/123/);
});

test('shows service error and allows retry', async ({ page }) => {
  await page.route('**/api/v1/products/search-by-image', route => route.fulfill({
    status: 503, json: { message: 'Tìm kiếm ảnh chưa sẵn sàng.' },
  }));
  await page.goto('/#/image-search');
  await page.getByLabel('Chọn hoặc chụp ảnh sản phẩm').setInputFiles(upload('query.png'));
  await page.locator('.image-search-panel').getByRole('button', { name: 'Tìm bằng ảnh', exact: true }).click();
  await expect(page.locator('.image-search-page').getByRole('alert')).toContainText('chưa sẵn sàng');
  await expect(page.locator('.image-search-panel').getByRole('button', { name: 'Tìm bằng ảnh', exact: true })).toBeEnabled();
});

test('changing image clears pending results and prevents stale response', async ({ page }) => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.route('**/api/v1/products/search-by-image', async route => {
    await gate;
    await route.fulfill({ json: response('Kết quả ảnh cũ') }).catch(() => {});
  });
  await page.goto('/#/image-search');
  await page.getByLabel('Chọn hoặc chụp ảnh sản phẩm').setInputFiles(upload('first.png'));
  const requested = page.waitForRequest('**/api/v1/products/search-by-image');
  await page.locator('.image-search-panel').getByRole('button', { name: 'Tìm bằng ảnh', exact: true }).click();
  await requested;
  await expect(page.getByRole('button', { name: 'Đang tìm…' })).toBeDisabled();
  await page.getByLabel('Chọn hoặc chụp ảnh sản phẩm').setInputFiles(upload('second.png'));
  release();
  await expect(page.getByText('second.png', { exact: true })).toBeVisible();
  await expect(page.getByText('Kết quả ảnh cũ', { exact: true })).toHaveCount(0);
  await expect(page.locator('.image-search-panel').getByRole('button', { name: 'Tìm bằng ảnh', exact: true })).toBeEnabled();
});

test('empty success and invalid file have distinct messages', async ({ page }) => {
  await page.route('**/api/v1/products/search-by-image', route => route.fulfill({
    json: { status: 200, data: { matchStatus: 'EMPTY', indexVersion: 'test', items: [] } },
  }));
  await page.goto('/#/image-search');
  await page.getByLabel('Chọn hoặc chụp ảnh sản phẩm').setInputFiles({ name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from('bad') });
  await expect(page.locator('.image-search-page').getByRole('alert')).toContainText('JPEG');
  await page.getByLabel('Chọn hoặc chụp ảnh sản phẩm').setInputFiles(upload('query.png'));
  await page.locator('.image-search-panel').getByRole('button', { name: 'Tìm bằng ảnh', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Chưa có sản phẩm');
});
