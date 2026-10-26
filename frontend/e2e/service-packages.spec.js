import { test, expect } from '@playwright/test';

// Browser contract tests use mocked HTTP; backend tests validate actual transactions.
const customer = { id: 19, username: 'package-customer', fullName: 'Khách gói', roles: ['ROLE_USER'] };
const pack = { id: 71, name: 'Gói chăm sóc 3 lượt', description: 'Chăm sóc theo nhu cầu', price: 900000, validityDays: 60, purchaseToken: 'original-offer', items: [{ serviceId: 2, serviceName: 'Chăm sóc da', quantity: 3 }] };
const ticket = { id: 91, packageName: 'Tên gói lúc mua', nameSnapshot: true, benefitType: 'SERVICE_PACKAGE', totalSessions: 3, usedSessions: 1, reservedSessions: 1, remainingSessions: 1, remainingByService: { 2: 1 }, status: 'ACTIVE', expiryDate: '2099-01-01T00:00:00Z', services: [{ serviceId: 2, serviceName: 'Chăm sóc da lúc mua', total: 3, used: 1, reserved: 1, available: 1 }] };
async function setup(page, handler = () => undefined, profile = customer) {
  await page.addInitScript(user => { localStorage.setItem('beautyshop_admin_token', 'test-token'); localStorage.setItem('beautyshop_admin_user', JSON.stringify(user)); }, profile);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (await handler(route, path)) return;
    let data = [];
    if (path.endsWith('/auth/profile')) data = profile;
    else if (path.endsWith('/spa/services/packages')) data = [pack];
    else if (path.endsWith('/spa/tickets/my-tickets')) data = [ticket];
    else if (path.endsWith('/movements')) data = { content: [{ id: 1, operation: 'FORFEIT', createdAt: '2026-10-01T00:00:00Z', reason: 'Vắng mặt theo chính sách' }], totalPages: 1, last: true };
    else if (path.endsWith('/refunds')) data = [];
    await route.fulfill({ json: { data } });
  });
}
test('ordinary package purchase confirms rights and preserves idempotency on retry', async ({ page }) => {
  const calls = []; let fail = true;
  await setup(page, async (route,path) => {
    if (path.endsWith('/tickets/purchase')) {
      calls.push({ body: route.request().postDataJSON(), key: route.request().headers()['idempotency-key'] });
      await route.fulfill(fail ? { status: 503, json: { message: 'Thử lại cùng yêu cầu' } } : { json: { data: { orderId: 800, orderNumber: 'ORD-PACKAGE-800', servicePackageId: 71, totalAmount: 900000, paymentMethod: 'BANK', paymentStatus: 'PENDING' } } });
      fail = false; return true;
    }
  });
  await page.goto('/#/service-packages');
  await expect(page.getByRole('heading', { name: pack.name, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem điều kiện & mua gói' }).click();
  await expect(page.getByRole('button', { name: 'Tạo đơn & thanh toán' })).toBeDisabled();
  await page.getByRole('checkbox', { name: 'Tôi xác nhận giá, quyền lợi, hạn dùng và điều kiện trên.' }).check();
  await page.getByRole('button', { name: 'Tạo đơn & thanh toán' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Thử lại cùng yêu cầu');
  await page.getByRole('button', { name: 'Tạo đơn & thanh toán' }).click();
  await expect(page).toHaveURL(/order-success\?id=800/);
  expect(calls).toHaveLength(2); expect(calls[0].key).toBeTruthy(); expect(calls[1]).toEqual(calls[0]);
  expect(calls[0].body).toEqual({ packageId: 71, purchaseToken: 'original-offer' });
});
for (const type of ['SERVICE_PACKAGE', 'TREATMENT_COURSE']) {
  test(`paid ${type} opens the matching benefit area`, async ({ page }) => {
    await setup(page, async (route,path) => {
      if (path === '/api/v1/orders/800') { await route.fulfill({ json: { data: { id:800, orderNumber:'ORD-PACKAGE-800', servicePackageId:71, spaBenefitType:type, paymentStatus:'PAID', paymentMethod:'BANK', status:'PROCESSING', totalAmount:900000, paidAmount:900000, items:[] } } }); return true; }
    });
    await page.goto('/#/order-success?id=800');
    await page.getByRole('button', { name: type === 'TREATMENT_COURSE' ? 'Mở lộ trình chăm sóc của tôi' : 'Mở ví vé Spa của tôi' }).click();
    await expect(page).toHaveURL(type === 'TREATMENT_COURSE' ? /treatments\?tab=courses/ : /profile\?tab=tickets/);
  });
}
test('wallet shows service balances, reservation and distinct forfeiture history', async ({ page }) => {
  await setup(page);
  await page.goto('/#/profile?tab=tickets');
  await expect(page.getByText('Chăm sóc da lúc mua', { exact: true })).toBeVisible();
  await expect(page.getByText(/Đang giữ: 1 lượt/)).toBeVisible();
  await page.getByRole('button', { name: 'Xem lịch sử vé' }).click();
  await expect(page.getByText(/Mất lượt theo chính sách/)).toBeVisible();
  await page.getByRole('button', { name: 'Mở đặt lịch sử dụng vé' }).click();
  await expect(page).toHaveURL(/booking\?ticketId=91&serviceId=2/);
});
test('revoked ticket keeps audit balance and disables ordinary booking', async ({ page }) => {
  await setup(page,async(route,path)=>{
    if(path.endsWith('/spa/tickets/my-tickets')){await route.fulfill({json:{data:[{...ticket,status:'REVOKED'}]}});return true;}
  });
  await page.goto('/#/profile?tab=tickets');
  await expect(page.getByText(/chưa dùng \(vé không khả dụng\)/)).toBeVisible();
  await expect(page.getByText('Đã thu hồi',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Mở đặt lịch sử dụng vé'})).toBeDisabled();
});

test('administrator confirms quoted package settlement with receipt and reason', async ({ page }) => {
  let command;
  await setup(page, async (route,path) => {
    if(path==='/api/v1/admin/spa/tickets'){await route.fulfill({json:{data:{content:[ticket],totalPages:1,totalElements:1}}});return true;}
    if(path.endsWith('/tickets/91/settlement')){
      if(route.request().method()==='POST')command=route.request().postDataJSON();
      await route.fulfill({json:{data:command?{id:1}:{ticketId:91,purchasedAmount:900000,refundableAmount:600000,unusedSessions:2,blockedReason:null}}});return true;
    }
  },{...customer,roles:['ROLE_ADMIN']});
  await page.goto('/#/admin/spa');
  await page.getByRole('button',{name:/Vé dịch vụ & Gói khách hàng/}).click();
  await page.getByRole('button',{name:'Quyết toán',exact:true}).click();
  const dialog=page.getByRole('dialog');
  await expect(dialog.getByText(/Số phải hoàn/)).toContainText('600.000');
  await dialog.getByLabel('Chứng từ chuyển tiền / biên nhận').fill('BANK-SETTLEMENT-001');
  await dialog.getByLabel('Lý do phê duyệt').fill('Khách ngừng sử dụng và đã đối soát');
  await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button',{name:'Xác nhận quyết toán & thu hồi'}).click();
  await expect(dialog).toHaveCount(0);
  expect(command).toEqual({amount:600000,reference:'BANK-SETTLEMENT-001',reason:'Khách ngừng sử dụng và đã đối soát'});
});

test('administrator chooses invoice service for a partial refund', async ({ page }) => {
  let command;
  await setup(page,async(route,path)=>{
    let data;
    if(path.endsWith('/appointments/admin/all'))data={content:[{id:42,status:'COMPLETED',orderId:9,customerName:'Khách hoàn tiền',appointmentDate:'2026-10-17',startTime:'10:00',items:[{id:101,serviceName:'Facial',price:300,executionStatus:'PERFORMED'}]}]};
    else if(path.endsWith('/appointments/42/invoice'))data={orderId:9,orderNumber:'ORD-VISIT',totalAmount:300,paidAmount:300,refundedAmount:0,amountDue:0,paymentStatus:'PAID',paymentMethod:'CASH',items:[{appointmentItemId:101,serviceName:'Facial',unitPrice:300}]};
    else if(path.endsWith('/appointments/42/service-refunds'))data={appointmentId:42,availableReceivedAmount:300,items:[{appointmentItemId:101,serviceName:'Facial',allocatedAmount:300,refundedAmount:0,refundableAmount:300}]};
    else if(path.endsWith('/items/101/service-refunds')){command=route.request().postDataJSON();data={id:2};}
    if(data!==undefined){await route.fulfill({json:{data}});return true;}
  },{...customer,roles:['ROLE_ADMIN']});
  await page.goto('/#/reception');
  await page.getByRole('button',{name:'Mở thu ngân'}).click();
  await page.getByRole('button',{name:'Hoàn tiền theo dịch vụ',exact:true}).click();
  const dialog=page.getByRole('dialog').last();
  await dialog.getByRole('combobox',{name:'Dịch vụ',exact:true}).selectOption('101');
  await dialog.getByLabel('Số tiền hoàn (VND)').fill('100');
  await dialog.getByLabel('Chứng từ chuyển tiền / biên nhận').fill('BANK-LINE-001');
  await dialog.getByLabel('Lý do phê duyệt').fill('Phê duyệt hoàn một phần dịch vụ');
  await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button',{name:'Xác nhận hoàn tiền dịch vụ'}).click();
  await expect.poll(()=>command).toEqual({amount:100,reference:'BANK-LINE-001',reason:'Phê duyệt hoàn một phần dịch vụ'});
});
