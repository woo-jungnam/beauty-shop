import { test, expect } from '@playwright/test';

async function checkout(page, existing = false) {
  const profile = { id: 1, username: 'reception-test', fullName: 'Test Reception', roles: ['ROLE_STAFF'] };
  await page.addInitScript((user) => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, profile);
  let invoice = existing ? makeInvoice() : null;
  const receipts = [];
  await page.route('**/img.vietqr.io/**', (route) => route.abort());
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    let data = [];
    if (path.endsWith('/auth/profile')) data = profile;
    else if (path.endsWith('/invoice/cash-receipts')) {
      const amount = request.postDataJSON().amount;
      receipts.push({ amount, key: request.headers()['idempotency-key'] });
      invoice = { ...invoice, paidAmount: invoice.paidAmount + amount, amountDue: invoice.amountDue - amount };
      if (invoice.amountDue === 0) invoice = { ...invoice, paymentStatus: 'PAID', paymentInstruction: null };
      data = invoice;
    } else if (path.endsWith('/invoice')) {
      if (request.method() === 'POST') invoice = makeInvoice();
      if (!invoice) return route.fulfill({ status: 404, json: { message: 'No invoice' } });
      data = invoice;
    } else if (path.includes('/appointments/admin/all')) data = { content: [{ id: 42, status: 'COMPLETED', orderId: existing ? 9 : null, customerName: 'Test Guest', appointmentDate: '2026-10-08', startTime: '10:00', items: [{ serviceName: 'Test Service', price: 900 }] }] };
    await route.fulfill({ json: { data } });
  });
  await page.goto('/#/reception');
  await page.getByRole('button', { name: /Thu Ngân & Hoá Đơn POS/ }).click();
  await page.getByText('Test Guest', { exact: true }).click();
  return {
    receipts,
    partial() { invoice = { ...invoice, paidAmount: 100, amountDue: 201, paymentInstruction: { ...invoice.paymentInstruction, qrCodeUrl: invoice.paymentInstruction.qrCodeUrl.replace('amount=301', 'amount=201') } }; },
    paid() { invoice = { ...invoice, paidAmount: 301, amountDue: 0, paymentStatus: 'PAID', paymentInstruction: null }; },
  };
}

function makeInvoice() {
  return {
    orderNumber: `ORD-${'A'.repeat(32)}`, totalAmount: 301, paidAmount: 0, amountDue: 301,
    refundedAmount: 0, paymentMethod: 'BANK', paymentStatus: 'PENDING',
    items: [{ appointmentItemId: 1, serviceName: 'Performed Service', unitPrice: 301 }],
    paymentInstruction: { bankName: 'MBBank', bankAccountName: 'BEAUTY SHOP', bankAccountNumber: '0902588750', transferSyntax: `ORD-${'A'.repeat(32)}`, qrCodeUrl: `https://img.vietqr.io/image/970422-0902588750-compact.jpg?amount=301&addInfo=ORD-${'A'.repeat(32)}` },
  };
}

test('Spa BANK waits for payment; partial transfers update QR; PAID hides QR', async ({ page }) => {
  const flow = await checkout(page);
  await page.getByRole('button', { name: 'Lập hóa đơn & tạo QR' }).click();
  await expect(page.getByText('Chờ thanh toán', { exact: true })).toBeVisible();
  await expect(page.getByText('0902588750', { exact: true })).toBeVisible();
  await expect(page.getByText('Thanh toán hoàn tất', { exact: true })).toHaveCount(0);
  flow.partial();
  await page.getByRole('button', { name: 'Kiểm tra thanh toán' }).click();
  await expect(page.getByLabel('Số tiền thực thu tiền mặt (VND)')).toHaveValue('201');
  flow.paid();
  await expect(page.getByText('Thanh toán hoàn tất', { exact: true })).toBeVisible({ timeout: 10000 });
  await expect(page.getByRole('button', { name: 'Sao chép số tài khoản' })).toHaveCount(0);
  expect(flow.receipts).toHaveLength(0);
});

test('Existing Spa invoice reopens without recreating; cash collects outstanding balance only', async ({ page }) => {
  const flow = await checkout(page, true);
  flow.partial();
  await page.getByRole('button', { name: 'Kiểm tra thanh toán' }).click();
  await expect(page.getByLabel('Số tiền thực thu tiền mặt (VND)')).toHaveValue('201');
  await page.getByRole('button', { name: 'Xác nhận đã nhận tiền mặt' }).click();
  await expect(page.getByText('Thanh toán hoàn tất', { exact: true })).toBeVisible();
  expect(flow.receipts).toHaveLength(1);
  expect(flow.receipts[0].amount).toBe(201);
  expect(flow.receipts[0].key).toBeTruthy();
});
