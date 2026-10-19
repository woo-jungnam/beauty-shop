import { test, expect } from '@playwright/test';

test('ADMIN explicitly publishes contact settings while ordinary value edits preserve visibility', async ({ page }) => {
  const admin = { id: 1, username: 'admin-config-test', roles: ['ROLE_ADMIN'], permissions: [] };
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-config-test');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);
  const calls = [];
  let configs = [
    { id: 1, configKey: 'store.email', configValue: 'old@example.test', isPublic: false },
    { id: 2, configKey: 'store.hotline', configValue: '0901234567', isPublic: true },
    { id: 3, configKey: 'dashboard.monthly_order_target', configValue: '50', isPublic: false },
  ];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    let data = [];
    if (path === '/api/v1/auth/profile') data = admin;
    else if (path === '/api/v1/admin/system-configs') {
      if (request.method() === 'POST') {
        const body = request.postDataJSON(); calls.push({ method: 'POST', ...body });
        configs = [...configs, { id: configs.length + 1, configKey: body.key, configValue: body.value, isPublic: body.isPublic ?? false }];
      }
      data = configs;
    } else if (path.startsWith('/api/v1/admin/system-configs/')) {
      const key = decodeURIComponent(path.slice('/api/v1/admin/system-configs/'.length));
      const body = request.postDataJSON(); calls.push({ method: request.method(), key, ...body });
      configs = configs.map(config => config.configKey === key ? { ...config, configValue: body.value, isPublic: body.isPublic ?? config.isPublic } : config);
      data = configs.find(config => config.configKey === key);
    } else if (path.startsWith('/api/v1/admin/payments/')) data = { content: [], totalPages: 0 };
    else if (path === '/api/v1/admin/payments') data = { content: [], totalPages: 0 };
    await route.fulfill({ json: { success: true, data } });
  });
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.goto('/#/admin/operations');
  await page.getByRole('button', { name: 'Cấu hình KPI', exact: true }).click();
  await expect(page.getByLabel('Công khai trên website: store.hotline', { exact: true })).toBeChecked();
  await expect(page.getByLabel('Công khai trên website: store.email', { exact: true })).not.toBeChecked();
  await expect(page.getByLabel('Công khai trên website: dashboard.monthly_order_target', { exact: true })).toBeDisabled();

  const hotline = page.locator('.admin-config-row').filter({ has: page.getByText('store.hotline', { exact: true }) });
  await hotline.getByRole('textbox').fill('0907654321');
  await hotline.getByRole('button', { name: 'Lưu', exact: true }).click();
  await expect.poll(() => calls.find(call => call.key === 'store.hotline')).toMatchObject({ method: 'PUT', value: '0907654321', isPublic: null });

  const email = page.locator('.admin-config-row').filter({ has: page.getByText('store.email', { exact: true }) });
  await email.getByRole('checkbox').check();
  await email.getByRole('button', { name: 'Lưu', exact: true }).click();
  await expect.poll(() => calls.find(call => call.key === 'store.email')).toMatchObject({ method: 'PUT', isPublic: true });

  await page.getByRole('button', { name: 'Thêm cấu hình', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Khóa cấu hình (KEY) *', { exact: true }).fill('store.address');
  await dialog.getByLabel('Giá trị *', { exact: true }).fill('Test address from ADMIN');
  await expect(dialog.getByRole('checkbox', { name: 'Công khai trên website', exact: true })).not.toBeChecked();
  await dialog.getByRole('checkbox', { name: 'Công khai trên website', exact: true }).check();
  await dialog.getByRole('button', { name: 'Tạo cấu hình', exact: true }).click();
  await expect.poll(() => calls.find(call => call.method === 'POST')).toMatchObject({ key: 'store.address', isPublic: true });
  await expect(dialog).toHaveCount(0);
});
