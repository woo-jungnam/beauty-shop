import { chromium, expect } from '@playwright/test';
import { createServer } from 'vite';

const { SPA_TEST_PORT, SPA_TEST_USERNAME, SPA_TEST_PASSWORD, SPA_TEST_DATE, SPA_TEST_SERVICE, SPA_TEST_TICKET } = process.env;
if (!/^\d+$/.test(SPA_TEST_PORT || '')) throw new Error('Run through SpaPackageBrowserIntegrationTest');
const target = `http://127.0.0.1:${SPA_TEST_PORT}`;
const server = await createServer({ define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('') }, server: { host: '127.0.0.1', port: 15973, strictPort: true, proxy: { '/api': { target, changeOrigin: true } } } });
await server.listen();
const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await page.route('**/*', route => {
    const host = new URL(route.request().url()).hostname;
    return ['localhost', '127.0.0.1'].includes(host) ? route.continue() : route.abort();
  });
  const login = await page.request.post(`${target}/api/v1/auth/login`, {
    data: { usernameOrEmail: SPA_TEST_USERNAME, password: SPA_TEST_PASSWORD },
  });
  expect(login.status()).toBe(200);
  const { data: user } = await login.json();
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', user.accessToken);
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, user);
  await page.goto(`${server.resolvedUrls.local[0]}#/booking?serviceId=${SPA_TEST_SERVICE}`, { waitUntil: "domcontentloaded" });
  await page.getByLabel('Áp dụng vé liệu trình').selectOption(SPA_TEST_TICKET);
  await page.getByRole('checkbox', { name: /Second benefit/ }).check();
  await page.getByRole('button', { name: 'Tiếp tục chọn giờ', exact: true }).click();
  await page.locator('#spa-date').fill(SPA_TEST_DATE);
  await page.getByRole('button', { name: '10:00', exact: true }).click();
  await page.getByRole('button', { name: 'Tiếp tục điền thông tin', exact: true }).click();
  await page.getByPlaceholder('Số điện thoại liên hệ').fill('0987654321');
  const booking = page.waitForResponse(r => r.url().endsWith('/appointments/book'));
  await page.getByRole('button', { name: 'Xác nhận lịch hẹn', exact: true }).click();
  const response = await booking;
  expect(response.ok(), await response.text()).toBe(true);
  const { data } = await response.json();
  expect(data.items).toHaveLength(2);
  await expect(page.getByText(`SPA-${data.id}`, { exact: true })).toBeVisible();
} finally {
  await browser.close();
  await server.close();
}
