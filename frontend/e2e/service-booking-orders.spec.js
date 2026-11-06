import { test, expect } from '@playwright/test';

for (const role of ['CUSTOMER', 'ADMIN']) {
  test(`${role} sees a service booking before invoicing and its invoice afterwards`, async ({ page }) => {
    const user = { id: 7, username: 'service-order-test', fullName: 'Khách kiểm thử', roles: [`ROLE_${role}`], permissions: [] };
    await page.addInitScript(profile => {
      localStorage.setItem('beautyshop_admin_token', 'test-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(profile));
    }, user);
    let invoiced = false;
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    const booking = { appointmentId: 31, customerName: user.fullName, appointmentDate: '2026-12-01', startTime: '09:00:00',
      status: 'CONFIRMED', estimatedAmount: 500000, collectedAmount: 500000, refundedAmount: 0, services: ['Chăm sóc da đơn lẻ'] };
    const invoice = { id: 101, orderNumber: 'ORD-SERVICE-101', appointmentId: 31, status: 'COMPLETED', paymentStatus: 'PAID',
      paymentMethod: 'BANK', totalAmount: 500000, paidAmount: 500000, items: [],
      spaVisitItems: [{ appointmentItemId: 1, serviceId: 2, serviceName: booking.services[0], unitPrice: 500000 }] };
    await page.route('**/api/v1/**', async route => {
      const path = new URL(route.request().url()).pathname;
      let data = [];
      if (path.endsWith('/auth/profile') || path.endsWith('/users/profile')) data = user;
      else if (path.endsWith('/my-service-orders') || path.endsWith('/admin/service-orders')) data = {
        content: invoiced ? [] : [booking], totalElements: invoiced ? 0 : 1, totalPages: 1, page: 0,
      };
      else if (path.endsWith('/orders/my-orders') || path.endsWith('/admin/orders')) data = {
        content: invoiced ? [invoice] : [], totalElements: invoiced ? 1 : 0, totalPages: 1, page: 0,
      };
      else if (path.endsWith('/appointments/31/finance')) data = {
        status: 'CONFIRMED', depositRequired: 500000, depositCollected: 500000, depositDue: 0, refunded: 0,
        depositPayment: { canPay: false }, reviewReasons: [], entries: [],
      };
      else if (path.includes('/commerce/')) data = { content: [], totalPages: 1 };
      else if (path.endsWith('/users/order-statistics')) data = {};
      await route.fulfill({ json: { data } });
    });
    await page.goto(role === 'ADMIN' ? '/#/admin/orders' : '/#/profile?tab=orders');
    const section = page.getByRole('region', { name: 'Dịch vụ đơn đã đặt', exact: true });
    await expect(section.getByText('LH-31', { exact: true })).toBeVisible();
    await expect(section.getByText(booking.services[0], { exact: true })).toBeVisible();
    await expect(section.getByText('Đã xác nhận', { exact: true })).toBeVisible();
    await section.getByRole('button', { name: 'Xem dịch vụ' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('Đã nhận thanh toán thành công');
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    invoiced = true;
    if (role === 'ADMIN') await page.locator('.page-header').getByRole('button', { name: 'Làm mới' }).click();
    else await page.getByRole('button', { name: 'Cập nhật đơn hàng', exact: true }).click();
    await expect(section.getByText('LH-31', { exact: true })).toHaveCount(0);
    await expect(page.getByText('ORD-SERVICE-101', { exact: true })).toBeVisible();
    expect(errors).toEqual([]);
  });
}
