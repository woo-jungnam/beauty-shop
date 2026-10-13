import { test, expect } from '@playwright/test';

const today = '2026-10-13';
const profile = { id: 99, username: 'reception-test', fullName: 'Lễ tân BeautyShop', roles: ['ROLE_STAFF'] };
const customer = { id: 501, fullName: 'Nguyễn Lan', phone: '+84981234567', membershipTier: 'GOLD', status: 'ACTIVE' };
const staff = [{ id: 11, fullName: 'Mai Anh', specialty: 'Chăm sóc da' }, { id: 12, fullName: 'Thu Hà' }];
const facility = (id, active = true) => ({ id, name: `Giường ${id}`, type: 'BED', capacity: 1, active });
const item = (id, extra = {}) => ({ id, serviceId: 101, serviceName: 'Chăm sóc da chuyên sâu', price: 350000, startTime: '10:00:00', endTime: '11:00:00', executionStatus: 'PLANNED', ...extra });
const appointment = (id, status, extra = {}) => ({ id, status, appointmentDate: today, startTime: '10:00:00', endTime: '11:00:00', customerName: `Khách ${id}`, customerPhone: `090000000${id}`, items: [item(id)], ...extra });

async function setup(page, options = {}) {
  await page.clock.install({ time: new Date(`${today}T10:15:00+07:00`) });
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, profile);
  const calls = [];
  let appointments = [appointment(1, 'PENDING', { startTime: '11:00', endTime: '12:00', items: [item(1, { startTime: '11:00', endTime: '12:00', resources: [{ facilityId: 1, name: 'Giường 1', quantity: 1 }] })] }),
    appointment(2, 'CONFIRMED', { items: [item(2, { staffId: 11, staffName: 'Mai Anh' })] }),
    appointment(3, 'IN_PROGRESS', { checkedInAt: '2026-10-13T03:00:00Z', items: [item(3, { executionStatus: 'IN_PROGRESS', resources: [{ facilityId: 2, name: 'Giường 2', quantity: 1 }] })] }),
    appointment(4, 'COMPLETED', { orderId: 401, items: [item(4, { executionStatus: 'PERFORMED', ticketId: 88, ticketUsageState: 'REDEEMED' })] }), appointment(5, 'CANCELLED')];
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    let data = [];
    if (path.endsWith('/auth/profile')) data = profile;
    else if (path.endsWith('/appointments/admin/all')) {
      const currentPage = Number(url.searchParams.get('page') || 0);
      data = { content: currentPage === 0 ? appointments.slice(0, 3) : appointments.slice(3), totalPages: 2, last: currentPage === 1 };
    } else if (path.endsWith('/spa/facilities')) data = [facility(1), facility(2), facility(3, false), facility(4)];
    else if (path.endsWith('/blocks')) {
      if (options.blocksFail && path.includes('/1/')) return route.fulfill({ status: 500, json: { message: 'Database unavailable' } });
      data = path.includes('/4/') ? [{ startAt: `${today}T10:00:00`, endAt: `${today}T11:00:00`, reason: 'Khử trùng và vệ sinh' }] : [];
    } else if (path.endsWith('/crm/customers')) {
      if (request.method() === 'POST') { calls.push({ path, body: request.postDataJSON() }); data = { id: 502, fullName: request.postDataJSON().fullName, phone: request.postDataJSON().phone, status: 'ACTIVE' }; }
      else data = url.searchParams.get('keyword') === '981234567' ? [customer] : [];
    } else if (path.endsWith('/available-slots')) data = url.searchParams.get('staffId') === '11' ? ['10:30', '11:00'] : ['12:00'];
    else if (path.endsWith('/services/101/staff') || path.endsWith('/admin/staff') || path.endsWith('/services/staff')) data = staff;
    else if (path.endsWith('/spa/services')) data = [{ id: 101, name: 'Chăm sóc da chuyên sâu', basePrice: 350000, durationMinutes: 60 }];
    else if (path.endsWith('/spa/tickets')) { calls.push({ path, userId: url.searchParams.get('userId') }); data = [{ id: 88, packageName: 'Liệu trình phục hồi', status: 'ACTIVE', totalSessions: 5, usedSessions: 2, reservedSessions: 1, remainingSessions: 2, remainingByService: { 101: 2 } }]; }
    else if (path.endsWith('/appointments/book')) {
      const body = request.postDataJSON(); calls.push({ path, body });
      if (options.bookingFail) return route.fulfill({ status: options.bookingStatus || 409, json: { message: 'Slot conflict' } });
      data = appointment(8, 'PENDING', { appointmentDate: body.appointmentDate, startTime: body.startTime, customerName: body.targetUserId === 501 ? customer.fullName : 'Khách mới', items: [item(81, { staffId: body.items[0].staffId })] });
      appointments.push(data);
    } else if (path.endsWith('/status')) {
      const id = Number(path.split('/').at(-2)); const body = request.postDataJSON(); calls.push({ path, body });
      appointments = appointments.map(row => row.id === id ? { ...row, status: body.status, items: row.items.map(service => ({ ...service, staffId: body.staffAssignments?.[service.id] || service.staffId })) } : row);
      data = appointments.find(row => row.id === id);
    } else if (path.endsWith('/check-in')) {
      calls.push({ path });
      if (options.checkInFail) return route.fulfill({ status: 409, json: { message: 'Check-in unavailable' } });
      const id = Number(path.split('/').at(-2)); appointments = appointments.map(row => row.id === id ? { ...row, checkedInAt: '2026-10-13T03:15:00Z' } : row); data = appointments.find(row => row.id === id);
    } else if (path.endsWith('/invoice')) return route.fulfill({ status: 404, json: { message: 'No invoice' } });
    await route.fulfill({ json: { data } });
  });
  await page.goto('/#/reception');
  await expect(page.getByRole('heading', { name: 'Quầy lễ tân Spa' })).toBeVisible();
  await expect(page.locator('[data-appointment-id="4"]')).toBeVisible();
  return calls;
}

test('all pages load; facility strip respects occupancy and cleaning independently of queue filters', async ({ page }) => {
  await setup(page);
  await expect(page.getByRole('button', { name: /Giường 1: Sẵn sàng/ })).toBeEnabled();
  await expect(page.getByRole('button', { name: /Giường 2: Đang phục vụ.*45 phút/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: /Giường 3: Tạm dừng/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: /Giường 4: Đang vệ sinh/ })).toBeDisabled();
  await page.screenshot({ path: 'test-results/spa-reception-desktop.png', fullPage: true });
  await page.getByLabel('Lọc trạng thái').selectOption('PENDING');
  await page.getByLabel('Lọc ngày hẹn').fill('2026-10-14');
  await expect(page.getByRole('button', { name: /Giường 2: Đang phục vụ/ })).toBeDisabled();
  await page.getByRole('button', { name: /Giường 1: Sẵn sàng/ }).click();
  await expect(page.getByRole('dialog', { name: 'Đón khách tại quầy' })).toBeVisible();
  await expect(page.getByLabel('Phòng / giường ưu tiên')).toHaveValue('1');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('list renders table headings; search and date/status filters combine', async ({ page }) => {
  await setup(page);
  await page.getByRole('button', { name: 'Danh sách', exact: true }).click();
  await expect(page.getByRole('columnheader', { name: 'Khách hàng' })).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(6);
  await page.getByLabel('Lọc trạng thái').selectOption('CONFIRMED');
  await page.getByLabel('Lọc ngày hẹn').fill(today);
  await page.getByLabel('Tìm tên, SĐT hoặc mã lịch hẹn').fill('0900000002');
  await expect(page.getByRole('row')).toHaveCount(2);
  await expect(page.getByRole('cell', { name: /Khách 2/ })).toBeVisible();
});

test('walk-in creates CRM profile once, binds correct customer and facility, then confirms and checks in', async ({ page }) => {
  const calls = await setup(page);
  await page.getByRole('button', { name: /Giường 1: Sẵn sàng/ }).click();
  await page.getByLabel('Số điện thoại khách').fill('0931234567');
  await expect(page.getByText(/Khách mới · Hồ sơ/)).toBeVisible();
  await page.getByLabel('Họ và tên khách').fill('Khách mới');
  await page.getByRole('button', { name: /Mai Anh/ }).click();
  await expect(page.getByRole('button', { name: /Thu Hà/ })).toHaveCount(0);
  await page.getByRole('button', { name: 'Xác nhận đón khách' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(calls.filter(call => call.path.endsWith('/crm/customers'))).toHaveLength(1);
  const booking = calls.find(call => call.path.endsWith('/book')).body;
  expect(booking.targetUserId).toBe(502);
  expect(booking.items).toEqual([{ serviceId: 101, staffId: 11, facilityId: 1 }]);
  expect(calls.some(call => call.path.endsWith('/8/status') && call.body.staffAssignments['81'] === 11)).toBe(true);
  expect(calls.some(call => call.path.endsWith('/8/check-in'))).toBe(true);
});

test('returning customer lookup normalizes +84 and binds ticket to exact CRM owner', async ({ page }) => {
  const calls = await setup(page);
  await page.getByRole('button', { name: 'Đón khách vãng lai' }).click();
  await page.getByLabel('Số điện thoại khách').fill('0981234567');
  await expect(page.getByLabel('Họ và tên khách')).toHaveValue('Nguyễn Lan');
  await expect(page.getByText(/Khách quen/)).toBeVisible();
  await page.getByLabel('Vé liệu trình của khách').selectOption('88');
  await page.getByRole('button', { name: 'Xác nhận đón khách' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(calls.filter(call => call.path.endsWith('/crm/customers'))).toHaveLength(0);
  expect(calls.find(call => call.path.endsWith('/book')).body.targetUserId).toBe(501);
  expect(calls.find(call => call.path.endsWith('/book')).body.items[0].ticketId).toBe(88);
  expect(calls.find(call => call.path.endsWith('/tickets')).userId).toBe('501');
});

test('failed check-in after successful booking reports partial success and cannot duplicate booking', async ({ page }) => {
  const calls = await setup(page, { checkInFail: true });
  await page.getByRole('button', { name: 'Đón khách vãng lai' }).click();
  await page.getByLabel('Số điện thoại khách').fill('0981234567');
  await expect(page.getByLabel('Họ và tên khách')).toHaveValue('Nguyễn Lan');
  await page.getByRole('button', { name: /Mai Anh/ }).click();
  await page.getByRole('button', { name: 'Xác nhận đón khách' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Đã tạo lịch SPA-8');
  await expect(page.getByRole('button', { name: 'Xác nhận đón khách' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đóng & kiểm tra lịch' }).click();
  expect(calls.filter(call => call.path.endsWith('/book'))).toHaveLength(1);
});

test('unverified block data never advertises a facility as free', async ({ page }) => {
  await setup(page, { blocksFail: true });
  await expect(page.getByRole('button', { name: /Giường 1: Chưa xác minh/ })).toBeDisabled();
  await expect(page.getByRole('alert')).toContainText('Chưa tải được lịch tạm dừng');
});

test('mobile board and drawer fit viewport, trap focus and restore body scroll', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Đón khách vãng lai' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await page.screenshot({ path: 'test-results/spa-reception-mobile-drawer.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByLabel('Số điện thoại khách').focus();
  await page.keyboard.press('Shift+Tab');
  expect(await page.evaluate(() => Boolean(document.activeElement.closest('[role="dialog"]')))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe('hidden');
});

test('check-in with unassigned staff opens qualified assignment and completes both API steps', async ({ page }) => {
  const calls = await setup(page);
  await page.locator('[data-appointment-id="1"]').getByRole('button', { name: 'Check-in ngay' }).click();
  await page.getByLabel('Chăm sóc da chuyên sâu', { exact: true }).selectOption('11');
  await page.getByRole('button', { name: 'Xác nhận & check-in' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(calls.find(call => call.path.endsWith('/1/status')).body.staffAssignments).toEqual({ 1: 11 });
  expect(calls.some(call => call.path.endsWith('/1/check-in'))).toBe(true);
});

test('booking conflicts retain created CRM profile for retry without creating another customer', async ({ page }) => {
  const calls = await setup(page, { bookingFail: true });
  await page.getByRole('button', { name: 'Đón khách vãng lai' }).click();
  await page.getByLabel('Số điện thoại khách').fill('0931234567');
  await expect(page.getByText(/Khách mới · Hồ sơ/)).toBeVisible();
  await page.getByLabel('Họ và tên khách').fill('Khách mới');
  await page.getByRole('button', { name: 'Xác nhận đón khách' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Thông tin vừa thay đổi');
  await page.getByRole('button', { name: 'Xác nhận đón khách' }).click();
  await expect.poll(() => calls.filter(call => call.path.endsWith('/book')).length).toBe(2);
  expect(calls.filter(call => call.path.endsWith('/crm/customers'))).toHaveLength(1);
});

test('uncertain server error after booking forces queue verification instead of duplicate submit', async ({ page }) => {
  const calls = await setup(page, { bookingFail: true, bookingStatus: 500 });
  await page.getByRole('button', { name: 'Đón khách vãng lai' }).click();
  await page.getByLabel('Số điện thoại khách').fill('0981234567');
  await expect(page.getByLabel('Họ và tên khách')).toHaveValue('Nguyễn Lan');
  await page.getByRole('button', { name: 'Xác nhận đón khách' }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('kiểm tra lịch theo SĐT');
  await expect(page.getByRole('button', { name: 'Xác nhận đón khách' })).toHaveCount(0);
  expect(calls.filter(call => call.path.endsWith('/book'))).toHaveLength(1);
});

test('start requires check-in; incomplete executions require explicit confirmation before completion', async ({ page }) => {
  const calls = await setup(page);
  const card = page.locator('[data-appointment-id="2"]');
  await expect(card.getByRole('button', { name: 'Bắt đầu ca' })).toHaveCount(0);
  await card.getByRole('button', { name: 'Check-in ngay' }).click();
  await card.getByRole('button', { name: 'Bắt đầu ca' }).click();
  await card.getByRole('button', { name: 'Hoàn tất & thu ngân' }).click();
  await expect(page.getByRole('dialog', { name: 'Xác nhận hoàn tất ca chăm sóc' })).toBeVisible();
  expect(calls.some(call => call.body?.status === 'COMPLETED')).toBe(false);
  await page.getByRole('button', { name: 'Xác nhận đã thực hiện đủ & hoàn tất' }).click();
  await expect(page.getByRole('dialog', { name: 'Thu ngân tại quầy' })).toBeVisible();
  expect(calls.filter(call => call.path.endsWith('/2/status')).map(call => call.body.status)).toEqual(['IN_PROGRESS', 'COMPLETED']);
});
