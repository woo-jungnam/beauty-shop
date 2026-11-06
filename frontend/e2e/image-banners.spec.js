import { test, expect } from '@playwright/test';

const banner = (id, position, targetUrl) => ({ id, title: `Ảnh quảng bá ${id}`, badge: 'CHỮ CŨ', description: 'Mô tả phủ ảnh cũ', ctaText: 'Nút cũ',
  imageUrl: `/test-banner-${id}.svg`, position, targetUrl, sortOrder: id, isActive: true });

async function setup(page, admin = false) {
  const profile = { id: 1, username: 'banner-admin', fullName: 'Admin', roles: ['ROLE_ADMIN'], permissions: [] };
  if (admin) await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'test-token'); localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, profile);
  const state = { banners: [banner(1, 'HERO_SLIDE', 'products?categoryId=4'), banner(2, 'HERO_SLIDE', '#/booking'),
    banner(3, 'HERO_SIDE', '/#/treatments'), banner(4, 'HERO_SIDE', 'https://example.com/deals')], saves: [], errors: [] };
  page.on('pageerror', error => state.errors.push(error.message));
  await page.route('**/test-banner-*.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="500"><rect width="1600" height="500" fill="#d7efe7"/><circle cx="1200" cy="250" r="180" fill="#72bda6"/></svg>' }));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let data = [];
    if (path.endsWith('/auth/profile')) data = profile;
    else if (path.endsWith('/admin/banners') || /\/admin\/banners\/\d+$/.test(path)) {
      if (route.request().method() !== 'GET') {
        const body = route.request().postDataJSON(); state.saves.push(body);
        const id = Number(path.split('/').at(-1)) || 5;
        state.banners = [...state.banners.filter(row => row.id !== id), { ...body, id }]; data = { ...body, id };
      } else data = state.banners;
    } else if (path.endsWith('/banners')) data = state.banners;
    else if (path.endsWith('/admin/products')) data = { content: [], totalPages: 1 };
    else if (path.includes('config')) data = {};
    await route.fulfill({ json: { data } });
  });
  return state;
}

for (const width of [1280, 390]) {
  test(`image-only banners fill their frames and follow configured destinations at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const state = await setup(page);
    await page.goto('/#/');
    const gallery = page.getByLabel('Banner nổi bật', { exact: true });
    await expect(gallery).toBeVisible();
    await gallery.hover();
    await expect(gallery.locator('img')).toHaveCount(3);
    expect(await gallery.innerText()).toBe('');
    await expect(gallery.getByRole('link', { name: 'Ảnh quảng bá 1' })).toHaveAttribute('href', '#/products?categoryId=4');
    await expect(gallery.getByRole('link', { name: 'Ảnh quảng bá 3' })).toHaveAttribute('href', '#/treatments');
    await expect(gallery.getByRole('link', { name: 'Ảnh quảng bá 4' })).toHaveAttribute('href', 'https://example.com/deals');
    const image = gallery.locator('.image-banner-carousel img');
    await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0)).toBe(true);
    const imageBox = await image.boundingBox(), frame = await gallery.locator('.image-banner-carousel').boundingBox();
    expect(Math.abs(imageBox.width - frame.width)).toBeLessThan(1);
    expect(Math.abs(imageBox.height - frame.height)).toBeLessThan(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await gallery.getByRole('button', { name: 'Xem banner 2', exact: true }).click();
    await expect(gallery.getByRole('link', { name: 'Ảnh quảng bá 2' })).toHaveAttribute('href', '#/booking');
    await gallery.getByRole('link', { name: 'Ảnh quảng bá 3' }).click();
    await expect(page).toHaveURL(/#\/treatments$/);
    expect(state.errors).toEqual([]);
  });
}

test('admin configures an image and destination without overlay text fields', async ({ page }) => {
  const state = await setup(page, true);
  await page.goto('/#/admin/products?tab=banners');
  await page.getByRole('button', { name: 'Thêm banner mới' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Tên banner / mô tả ảnh *', { exact: true }).fill('Ưu đãi liệu trình');
  await dialog.getByPlaceholder('Hoặc dán URL ảnh trực tiếp (/uploads/... hoặc https://...)').fill('/test-banner-5.svg');
  await dialog.getByLabel('Trang đích khi nhấp vào ảnh *', { exact: true }).fill('treatments?tab=catalog');
  await expect(dialog.getByLabel('Chữ nút bấm (CTA)')).toHaveCount(0);
  await expect(dialog.getByLabel('Huy hiệu / Badge')).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Lưu banner', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  expect(state.saves[0]).toMatchObject({ title: 'Ưu đãi liệu trình', imageUrl: '/test-banner-5.svg', targetUrl: 'treatments?tab=catalog', badge: '', description: '', ctaText: '' });
  await page.goto('/#/');
  const gallery = page.getByLabel('Banner nổi bật', { exact: true });
  await gallery.getByRole('button', { name: 'Xem banner 3', exact: true }).click();
  await expect(gallery.getByRole('link', { name: 'Ưu đãi liệu trình' })).toHaveAttribute('href', '#/treatments?tab=catalog');
  expect(await gallery.innerText()).toBe('');
  expect(state.errors).toEqual([]);
});

test('turning off configured banners does not display hard-coded promotions', async ({ page }) => {
  const state = await setup(page);
  state.banners = state.banners.map(row => ({ ...row, isActive: false }));
  await page.goto('/#/');
  await expect(page.locator('.customer-home-page')).toBeVisible();
  await expect(page.getByLabel('Banner nổi bật', { exact: true })).toHaveCount(0);
  await expect(page.locator('.home-banner-main-content')).toHaveCount(0);
});
