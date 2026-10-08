import { test, expect } from '@playwright/test';

test.skip(!process.env.E2E_BASE_URL, 'Set E2E_BASE_URL to the running Docker gateway to verify the live services.');

const unwrap = (body) => body?.data ?? body;

test.afterEach(async ({ page, request }) => {
  const session = await page.evaluate(() => ({
    guestId: localStorage.getItem('beautyshop_guest_session_id'),
    refreshToken: localStorage.getItem('beautyshop_admin_refresh_token'),
  })).catch(() => null);
  if (session?.guestId) {
    const cleanup = await request.delete('/api/v1/cart/clear', { params: { sessionId: session.guestId } });
    expect(cleanup.ok()).toBeTruthy();
  }
  if (session?.refreshToken) {
    const logout = await request.post('/api/v1/auth/logout', { data: { refreshToken: session.refreshToken } });
    expect(logout.ok()).toBeTruthy();
  }
});

test('Docker gateway serves the built storefront and live backend catalog', async ({ page, request }) => {
  const health = await request.get('/actuator/health');
  expect(health.ok()).toBeTruthy();
  expect((await health.json()).status).toBe('UP');

  const catalog = await request.get('/api/v1/products?size=12');
  expect(catalog.ok()).toBeTruthy();
  const products = unwrap(await catalog.json()).content;
  expect(products.length).toBeGreaterThan(0);

  const catalogResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/products');
  await page.goto('/#/products');
  expect((await catalogResponse).ok()).toBeTruthy();
  await expect(page.getByText(products[0].name, { exact: true }).first()).toBeVisible();

  const detailResponse = page.waitForResponse((response) => new URL(response.url()).pathname === `/api/v1/products/${products[0].id}`);
  await page.goto(`/#/product/${products[0].id}`);
  expect((await detailResponse).ok()).toBeTruthy();
  await expect(page.getByRole('heading', { name: products[0].name, exact: true, level: 1 })).toBeVisible();
});

test('Customer login refreshes an invalid access token and restores the profile after reload', async ({ page }) => {
  test.skip(!process.env.E2E_USERNAME || !process.env.E2E_PASSWORD, 'Set E2E_USERNAME and E2E_PASSWORD for a dedicated test account.');
  await page.goto('/#/login');
  await page.getByPlaceholder('Nhập username hoặc email').fill(process.env.E2E_USERNAME);
  await page.getByPlaceholder('••••••••').fill(process.env.E2E_PASSWORD);
  const loginResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/auth/login');
  await page.locator('form').getByRole('button', { name: /Đăng nhập/i }).click();
  const response = await loginResponse;
  expect(response.ok()).toBeTruthy();
  const account = unwrap(await response.json());
  expect(account.accessToken).toBeTruthy();
  await expect(page).toHaveURL(/#\/profile$/);
  await expect(page.getByRole('heading', { name: account.fullName || account.username })).toBeVisible();

  await page.evaluate(() => localStorage.setItem('beautyshop_admin_token', 'invalid-browser-smoke-token'));
  const refreshResponse = page.waitForResponse((result) => new URL(result.url()).pathname === '/api/v1/auth/refresh');
  const profileResponse = page.waitForResponse((result) => result.ok() && new URL(result.url()).pathname === '/api/v1/auth/profile');
  await page.reload();
  expect((await refreshResponse).ok()).toBeTruthy();
  expect((await profileResponse).ok()).toBeTruthy();
  await expect(page.getByRole('heading', { name: account.fullName || account.username })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('beautyshop_admin_token'))).not.toBe('invalid-browser-smoke-token');

});

test('Storefront sends a real chatbot request and recommendation adds the matching backend cart item', async ({ page, request }) => {
  test.setTimeout(180000);
  const aiHealth = await request.get('/api/v1/chatbot/health');
  expect(aiHealth.ok()).toBeTruthy();
  expect(['UP', 'ok']).toContain(unwrap(await aiHealth.json()).status);

  await page.goto('/');
  await page.getByRole('button', { name: 'Hỏi Bác Sĩ & AI Da Liễu' }).click();
  await expect(page.getByText('● AI Trực tuyến (RAG)')).toBeVisible();
  await page.getByPlaceholder('Nhập câu hỏi về da, thành phần...').fill(
    process.env.E2E_CHAT_QUERY || 'Tư vấn kem chống nắng cho da dầu dưới 500k',
  );
  const chatResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/chatbot/chat', { timeout: 130000 });
  await page.getByRole('button', { name: 'Gửi câu hỏi' }).click();
  const response = await chatResponse;
  expect(response.ok()).toBeTruthy();
  const answer = unwrap(await response.json());
  expect(answer.answer.length).toBeGreaterThan(20);
  await expect(page.locator('.chatbot-panel')).toContainText(answer.answer);

  const product = answer.products.find((item) => (item.target_type || item.targetType || 'PRODUCT') === 'PRODUCT');
  expect(product, 'The live chatbot must return a catalog product recommendation').toBeTruthy();
  const productId = String(product.id).replace(/^mysql_/, '');
  const productResponse = page.waitForResponse((result) => new URL(result.url()).pathname === `/api/v1/products/${productId}`);
  const cartResponse = page.waitForResponse((result) => new URL(result.url()).pathname === '/api/v1/cart/add');
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Ngay' }).first().click();
  expect((await productResponse).ok()).toBeTruthy();
  const cartResult = await cartResponse;
  expect(cartResult.ok()).toBeTruthy();
  const cart = unwrap(await cartResult.json());
  expect(cart.items.length).toBeGreaterThan(0);
  await expect(page.locator('.drawer-panel')).toBeVisible();

  const sessionId = await page.evaluate(() => localStorage.getItem('beautyshop_guest_session_id'));
  const storedCart = await request.get('/api/v1/cart', { params: { sessionId } });
  expect(unwrap(await storedCart.json()).items.map((item) => item.variantId)).toEqual(cart.items.map((item) => item.variantId));
});
