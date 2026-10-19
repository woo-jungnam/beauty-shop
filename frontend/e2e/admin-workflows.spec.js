import { test, expect } from '@playwright/test';

const admin = { id: 1, username: 'administrator', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: [] };
const staff = { id: 8, username: 'care_staff', fullName: 'Nhân viên chăm sóc', email: 'care@example.test', status: 'ACTIVE', roles: ['ROLE_STAFF'], permissions: ['CATALOG'] };

async function setup(page) {
  await page.addInitScript(user => { localStorage.setItem('beautyshop_admin_token', 'isolated-test-token'); localStorage.setItem('beautyshop_admin_user', JSON.stringify(user)); }, admin);
  const calls = [];
  let grants = ['CATALOG'];
  let item = { id: 4, customerId: 9, customerName: 'Khách hàng An', kind: 'FOLLOW_UP', title: 'Liên hệ sau buổi Spa', description: 'Hỏi kết quả chăm sóc', status: 'OPEN', dueAt: '2026-10-16T10:00:00Z', assigneeId: null, version: 4, overdue: true };
  let exception = { id: 40, referenceCode: 'BANK-40', content: 'Chuyen khoan khong co ma', amount: 150000, status: 'ORDER_NOT_FOUND', createdAt: '2026-10-17T03:00:00Z' };
  let resolutions = [];
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname;
    const body = request.postDataJSON(); calls.push({ method: request.method(), path, body, query: url.searchParams });
    let data = [];
    if (path === '/api/v1/auth/profile') data = admin;
    else if (path === '/api/v1/admin/users') data = { content: [staff], totalPages: 1, totalElements: 1 };
    else if (path === '/api/v1/roles') data = [{ id: 1, roleName: 'ROLE_ADMIN' }, { id: 2, roleName: 'ROLE_STAFF' }];
    else if (path === '/api/v1/admin/users/8/permissions') { if (request.method() === 'PUT') grants = body.permissions; data = grants; }
    else if (path === '/api/v1/admin/crm/customers/page') data = { content: [], totalPages: 1 };
    else if (path === '/api/v1/admin/crm/work-items') data = { content: !url.searchParams.get('status') || url.searchParams.get('status') === item.status ? [item] : [], totalPages: 1 };
    else if (path === '/api/v1/admin/crm/work-items/4') { item = { ...item, ...body, version: item.version + 1, overdue: false }; data = item; }
    else if (path === '/api/v1/admin/payments/exceptions') data = { content: exception ? [exception] : [], totalPages: 1 };
    else if (path === '/api/v1/admin/payments/resolutions') data = { content: resolutions, totalPages: 1 };
    else if (path === '/api/v1/admin/payments/40/resolution') { resolutions = [{ id: 1, ...body, amount: exception.amount }]; exception = null; data = resolutions[0]; }
    else if (path === '/api/v1/admin/payments') data = { content: [], totalPages: 1 };
    else if (path === '/api/v1/admin/payments/summary') data = { totalTransactions: 1, receivedAmount: 150000, failedTransactions: 0 };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 200, data, success: true }) });
  });
  return calls;
}

test('admin staff work editor persists only explicitly selected grants', async ({ page }) => {
  const calls = await setup(page);
  await page.goto('/#/admin/users');
  await page.getByRole('button', { name: 'Công việc', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Sản phẩm và khuyến mãi')).toBeChecked();
  await dialog.getByLabel('Sản phẩm và khuyến mãi').uncheck();
  await dialog.getByLabel('Chăm sóc khách hàng', { exact: true }).check();
  await dialog.getByRole('button', { name: 'Lưu quyền công việc' }).click();
  await expect(dialog).toHaveCount(0);
  expect(calls.find(call => call.path === '/api/v1/admin/users/8/permissions' && call.method === 'PUT').body).toEqual({ permissions: ['CRM'] });
});

test('CRM due queue filters work and requires an outcome before closing the current version', async ({ page }) => {
  const calls = await setup(page);
  await page.goto('/#/admin/crm');
  const work = page.getByRole('region', { name: 'Công việc chăm sóc và khiếu nại' });
  await work.getByLabel('Đến hạn / quá hạn').check();
  await expect.poll(() => calls.some(call => call.path === '/api/v1/admin/crm/work-items' && call.query.has('dueBefore'))).toBeTruthy();
  await work.getByRole('button', { name: 'Phân công / cập nhật' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Trạng thái').selectOption('DONE');
  await dialog.getByLabel('Người phụ trách').selectOption('1');
  await dialog.getByRole('button', { name: 'Lưu công việc' }).click();
  expect(calls.filter(call => call.path === '/api/v1/admin/crm/work-items/4' && call.method === 'PUT')).toHaveLength(0);
  await dialog.getByLabel('Kết quả / lý do').fill('Đã liên hệ và ghi nhận phản hồi khách hàng');
  await dialog.getByRole('button', { name: 'Lưu công việc' }).click();
  await expect(dialog).toHaveCount(0);
  expect(calls.find(call => call.path === '/api/v1/admin/crm/work-items/4' && call.method === 'PUT').body).toMatchObject({ status: 'DONE', assigneeId: 1, expectedVersion: 4, outcome: 'Đã liên hệ và ghi nhận phản hồi khách hàng' });
});

test('payment exception resolution requires supporting input and records a real matching request', async ({ page }) => {
  const calls = await setup(page);
  await page.goto('/#/admin/operations');
  const panel = page.getByRole('region', { name: 'Xử lý giao dịch chưa nhận diện' });
  await panel.getByRole('button', { name: 'Đối soát', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Gắn vào đơn hàng' }).click();
  expect(calls.filter(call => call.path === '/api/v1/admin/payments/40/resolution')).toHaveLength(0);
  await dialog.getByLabel('Mã đơn hàng đích').fill('ORD-21');
  await dialog.getByLabel('Căn cứ và lý do').fill('Đối chiếu biên nhận và xác minh người chuyển tiền');
  await dialog.getByRole('button', { name: 'Gắn vào đơn hàng' }).click();
  await expect(dialog).toHaveCount(0);
  expect(calls.find(call => call.path === '/api/v1/admin/payments/40/resolution').body).toMatchObject({ kind: 'MATCH', orderNumber: 'ORD-21', reference: null, reason: 'Đối chiếu biên nhận và xác minh người chuyển tiền' });
  await expect(panel.getByText('Không còn giao dịch chưa xử lý trong bộ lọc.')).toBeVisible();
});
