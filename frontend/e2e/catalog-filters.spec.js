import { test, expect } from '@playwright/test';

const categories = [
  { id: 4, name: 'Serum', slug: 'serum', children: [{ id: 9, name: 'Serum phục hồi', slug: 'serum-phuc-hoi' }] },
  { id: 8, name: 'Kem dưỡng', slug: 'kem-duong', children: [] },
];
const brands = [{ id: 5, name: 'Beauty Lab', slug: 'beauty-lab' }, { id: 6, name: 'Skin Lab', slug: 'skin-lab' }];
const product = { id: 42, name: 'Serum phục hồi B5', minPrice: 250000, brandId: 5, categoryId: 4, thumbnailUrl: '/images/spa/facial-care.jpg' };

async function catalog(page, { pagination = false, failed = false, delayed = false } = {}) {
  const requests = [];
  let failure = failed;
  const allProducts = pagination ? Array.from({ length: 121 }, (_, i) => ({ ...product, id: 42 + i, name: `Serum ${i + 1}` })) : [product];
  await page.route('**/fonts.googleapis.com/**', route => route.abort());
  await page.route('**/fonts.gstatic.com/**', route => route.abort());
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    let data = [];
    if (url.pathname === '/api/v1/categories' || url.pathname === '/api/v1/categories/root') data = categories;
    else if (url.pathname === '/api/v1/brands') data = { content: brands, totalPages: 1 };
    else if (url.pathname === '/api/v1/products/search') {
      requests.push(new URLSearchParams(url.search));
      if (failure) return route.fulfill({ status: 503, json: { message: 'Temporary failure' } });
      if (delayed && url.searchParams.get('brandIds') === '5') await new Promise(resolve => setTimeout(resolve, 400));
      const pageIndex = Number(url.searchParams.get('page') || 0);
      data = { content: allProducts.slice(pageIndex * 60, (pageIndex + 1) * 60), totalElements: allProducts.length, totalPages: Math.ceil(allProducts.length / 60) };
    }
    await route.fulfill({ json: { success: true, data } });
  });
  return { requests, recover: () => { failure = false; } };
}

const categoryGroup = page => page.getByRole('group', { name: 'Danh mục sản phẩm', exact: true });
const brandGroup = page => page.getByRole('group', { name: 'Thương hiệu chính hãng', exact: true });
const ids = (params, key) => (params.get(key) || '').split(',').filter(Boolean).sort();

test('Multiple brands and categories preserve keyword, price, skin type and sort', async ({ page }) => {
  const api = await catalog(page);
  await page.goto('/#/products?search=Serum');
  await categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true }).check();
  await categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true }).check();
  await brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' }).check();
  await brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' }).check();
  await page.getByRole('radio', { name: 'Da Dầu & Lỗ Chân Lông To' }).check();
  await page.getByRole('radio', { name: 'Dưới 300.000 ₫' }).check();
  await page.getByLabel('Sắp xếp sản phẩm').selectOption('price_asc');
  await expect.poll(() => {
    const params = api.requests.at(-1);
    return params ? { brands: ids(params, 'brandIds'), categories: ids(params, 'categoryIds'), keyword: params.get('keyword'), price: params.get('maxPrice'), skin: params.get('skinType'), sort: params.get('sort') } : null;
  }).toEqual({ brands: ['5', '6'], categories: ['4', '8'], keyword: 'Serum', price: '299999', skin: 'OILY', sort: 'price,asc' });
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true })).toBeChecked();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await expect(page.getByRole('link', { name: product.name, exact: true })).toBeVisible();
});

test('Removing one chip or clearing one group leaves the other filters selected', async ({ page }) => {
  const api = await catalog(page);
  await page.goto('/#/products?categoryIds=4,8&brandIds=5,6&search=Serum&skinType=OILY&priceRange=UNDER_300');
  await page.getByRole('button', { name: 'Bỏ Danh mục: Serum', exact: true }).click();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true })).not.toBeChecked();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true })).toBeChecked();
  await brandGroup(page).getByRole('button', { name: 'Tất cả', exact: true }).click();
  await expect.poll(() => ({ categories: ids(api.requests.at(-1), 'categoryIds'), brands: ids(api.requests.at(-1), 'brandIds'), keyword: api.requests.at(-1)?.get('keyword') })).toEqual({ categories: ['8'], brands: [], keyword: 'Serum' });
  await expect(page.getByRole('radio', { name: 'Da Dầu & Lỗ Chân Lông To' })).toBeChecked();
  await expect(page.getByRole('radio', { name: 'Dưới 300.000 ₫' })).toBeChecked();
  await page.getByRole('button', { name: 'Xóa tất cả', exact: true }).click();
  await expect(page).toHaveURL(/#\/products$/);
  await expect(page.getByLabel('Từ khóa tìm kiếm', { exact: true })).toHaveValue('');
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true })).not.toBeChecked();
});

test('Legacy links, repeated query parameters, reload and browser history preserve selections', async ({ page }) => {
  await catalog(page);
  await page.goto('/#/products?categoryId=4&brandId=5&search=Serum');
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' })).toBeChecked();
  await brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' }).check();
  await page.reload();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true }).check();
  await page.goBack();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true })).not.toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await page.goForward();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true })).toBeChecked();
  await page.goto('/#/products?brandIds=5&brandIds=6&categoryIds=4,9');
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum phục hồi', exact: true })).toBeChecked();
});

test('Multiple active ingredients use an independent parameter instead of overwriting search', async ({ page }) => {
  const api = await catalog(page);
  await page.goto('/#/products?search=Serum&brandIds=5&categoryIds=4');
  const group = page.getByRole('group', { name: 'Hoạt chất đặc trị', exact: true });
  await group.getByRole('checkbox', { name: 'Niacinamide (Làm sáng/Kiềm dầu)' }).check();
  await group.getByRole('checkbox', { name: 'Ceramide (Hàng rào bảo vệ)' }).check();
  await expect.poll(() => ({ keyword: api.requests.at(-1)?.get('keyword'), actives: ids(api.requests.at(-1), 'activeIngredients'), brands: ids(api.requests.at(-1), 'brandIds'), categories: ids(api.requests.at(-1), 'categoryIds') })).toEqual({ keyword: 'Serum', actives: ['ceramide', 'niacinamide'], brands: ['5'], categories: ['4'] });
  await page.reload();
  await expect(group.getByRole('checkbox', { name: 'Niacinamide (Làm sáng/Kiềm dầu)' })).toBeChecked();
  await expect(group.getByRole('checkbox', { name: 'Ceramide (Hàng rào bảo vệ)' })).toBeChecked();
});

test('Mobile selections remain open and keyboard controls can select multiple options', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await catalog(page);
  await page.goto('/#/products');
  await page.getByRole('button', { name: 'Bộ lọc sản phẩm', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Bộ lọc sản phẩm' });
  await categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true }).check();
  await brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' }).check();
  await brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' }).focus();
  await page.keyboard.press('Space');
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Bộ lọc sản phẩm' })).toBeFocused();
  await page.getByRole('button', { name: 'Bộ lọc sản phẩm' }).click();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' })).toBeChecked();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy();
});

test('Retry keeps every selected filter and obsolete responses cannot replace new results', async ({ page }) => {
  const api = await catalog(page, { failed: true, delayed: true });
  await page.goto('/#/products?categoryIds=4,8&brandIds=5,6&search=Serum');
  await expect(page.getByRole('heading', { name: 'Chưa tải được sản phẩm' })).toBeVisible();
  api.recover();
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('link', { name: product.name, exact: true })).toBeVisible();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' }).uncheck();
  await brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' }).check();
  await expect.poll(() => ids(api.requests.at(-1), 'brandIds')).toEqual(['5', '6']);
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
});

test('Pagination uses API totals and changing a filter resets the page without losing other groups', async ({ page }) => {
  const api = await catalog(page, { pagination: true });
  await page.goto('/#/products?brandIds=5,6&categoryIds=4,8');
  await expect(page.getByRole('status')).toContainText('121');
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click();
  await expect.poll(() => api.requests.at(-1)?.get('page')).toBe('1');
  await expect(page.getByRole('navigation', { name: 'Phân trang sản phẩm' })).toContainText('Trang 2 / 3');
  await categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true }).uncheck();
  await expect.poll(() => ({ page: api.requests.at(-1)?.get('page'), brands: ids(api.requests.at(-1), 'brandIds'), categories: ids(api.requests.at(-1), 'categoryIds') })).toEqual({ page: '0', brands: ['5', '6'], categories: ['4'] });
});

test('Header brand and search navigation preserve the catalog category filters', async ({ page }) => {
  await catalog(page);
  await page.goto('/#/products?categoryIds=4,8&brandIds=5');
  await page.getByRole('button', { name: 'Mở danh sách thương hiệu' }).click();
  await page.locator('#storefront-brands').getByRole('link', { name: 'Skin Lab' }).click();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true })).toBeChecked();
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Kem dưỡng', exact: true })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Beauty Lab' })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await page.getByRole('searchbox', { name: 'Tìm kiếm sản phẩm' }).fill('B5');
  await page.getByRole('searchbox', { name: 'Tìm kiếm sản phẩm' }).press('Enter');
  await expect(categoryGroup(page).getByRole('checkbox', { name: 'Serum', exact: true })).toBeChecked();
  await expect(brandGroup(page).getByRole('checkbox', { name: 'Skin Lab' })).toBeChecked();
  await expect(page.getByLabel('Từ khóa tìm kiếm', { exact: true })).toHaveValue('B5');
});
