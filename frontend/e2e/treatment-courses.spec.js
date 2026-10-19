import { test, expect } from '@playwright/test';

// Client contract tests. API transactions, permissions and payment issuance have
// separate backend tests; these cover the navigable UI and exact request payloads.
const plan = { name: 'Lộ trình phục hồi 2 buổi', description: 'Theo dõi phục hồi từng buổi.', serviceId: 9, price: 1200000, validityDays: 90, terms: 'Đặt trước. Hủy theo chính sách và hoàn phần chưa dùng.', sessions: [
  { sequence: 1, title: 'Tiếp nhận và làm sạch', minGapDays: 0, maxGapDays: null, required: true, items: [{ serviceId: 2, serviceName: 'Làm sạch da', required: true }] },
  { sequence: 2, title: 'Theo dõi phục hồi', minGapDays: 7, maxGapDays: 14, required: true, items: [{ serviceId: 3, serviceName: 'Phục hồi da', required: true }] },
] };
const template = { ...plan, id: 11, versionId: 31, versionNumber: 1, status: 'PUBLISHED', packageId: 71, allowedActions: ['PURCHASE'] };
const newCourse = () => ({ id: 41, userId: 19, templateId: 11, versionId: 31, name: plan.name, status: 'PENDING_INTAKE', version: 1, orderId: 800, ticketId: 91, assignedStaffId: 5, expiryDate: '2027-01-16T10:00:00Z', paidAmount: 1200000, completedSessions: 0, totalSessions: 2, progressPercent: 0, plan: structuredClone(plan), sessions: plan.sessions.map((session, index) => ({ ...session, id: 51 + index, status: 'PLANNED', earliestBookingAt: null, latestBookingAt: null, blockedReason: index ? 'Hoàn tất buổi trước khi đặt buổi này.' : null, allowedActions: index ? [] : ['BOOK'], results: [], items: session.items.map(item => ({ ...item, id: 61 + index, ticketId: 91, status: 'PLANNED' })) })), changes: [], requests: [], audit: [], allowedActions: ['INTAKE', 'PROPOSE_CHANGE', 'REQUEST_CANCEL', 'REQUEST_EXTEND'] });

async function fixture(page, role, options = {}) {
  const profile = { id: role === 'USER' ? 19 : 99, username: `treatment-${role.toLowerCase()}`, fullName: `Kiểm thử ${role}`, roles: [`ROLE_${role}`], permissions: role === 'STAFF' ? (options.permissions || ['SPA_EXECUTION']) : [] };
  await page.addInitScript(user => { localStorage.setItem('beautyshop_admin_token', 'test-token'); localStorage.setItem('beautyshop_admin_user', JSON.stringify(user)); }, profile);
  const state = { course: newCourse(), calls: [], reads: [], purchaseFailures: options.purchaseFailures || 0, template: { ...structuredClone(template), allowedActions: role === 'ADMIN' ? ['CREATE_VERSION', 'ARCHIVE'] : ['PURCHASE'] } };
  const pageErrors = []; page.on('pageerror', error => pageErrors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const req = route.request(); const path = new URL(req.url()).pathname; const method = req.method();
    const body = method === 'GET' ? null : req.postData() ? req.postDataJSON() : null;
    if (method !== 'GET') state.calls.push({ path, method, body }); else state.reads.push(path);
    let data = [];
    if (path.endsWith('/auth/profile')) data = profile;
    else if (path.endsWith('/staff')) data = [{ id: 5, userId: profile.id, fullName: 'Assigned therapist' }];
    else if (path.endsWith('/appointments/701/finance')) data = { depositRequired: 0, depositCollected: 0, appliedToInvoice: 0, assessedFee: 0, feeDue: 0, refundable: 0, refunded: 0, entries: [] };
    else if (path.endsWith('/spa/services')) data = [{ id: 9, name: plan.name, serviceType: 'TREATMENT_COURSE', isActive: true }, { id: 2, name: 'Làm sạch da', serviceType: 'SINGLE_SESSION', isActive: true }, { id: 3, name: 'Phục hồi da', serviceType: 'SINGLE_SESSION', isActive: true }];
    else if (path.endsWith('/treatment-templates')) data = [state.template];
    else if (path.endsWith('/treatment-templates/11/versions')) { state.template = { ...state.template, versionId: 32, versionNumber: 2, status: 'DRAFT', allowedActions: ['EDIT', 'PUBLISH', 'ARCHIVE'] }; data = state.template; }
    else if (path.endsWith('/treatment-templates/11/publish')) { state.template.status = 'PUBLISHED'; state.template.allowedActions = ['CREATE_VERSION', 'ARCHIVE']; data = state.template; }
    else if (path.endsWith('/treatment-purchases')) {
      if (state.purchaseFailures-- > 0) return route.fulfill({ status: 503, json: { message: 'Tạm mất kết nối khi tạo đơn' } });
      data = { orderId: 800, orderNumber: 'SPA-TREATMENT-800', servicePackageId: 71, totalAmount: 1200000, paymentMethod: 'BANK', paymentStatus: 'PENDING', status: 'PENDING' };
    }
    else if (path.endsWith('/orders/800')) data = { id: 800, orderNumber: 'SPA-TREATMENT-800', servicePackageId: 71, totalAmount: 1200000, paymentMethod: 'BANK', paymentStatus: 'PENDING', status: 'PENDING' };
    else if (path.endsWith('/orders/my-orders')) data = { content: [] };
    else if (path.endsWith('/spa/treatment-notifications')) data = [{ id: 101, courseId: 41, kind: 'PAYMENT', title: 'Đã cấp hồ sơ điều trị', message: 'Lộ trình đã sẵn sàng để đặt buổi đầu.', createdAt: '2026-10-16T10:00:00Z', readAt: null }];
    else if (path.endsWith('/spa/my-treatments') || /\/(staff|admin)\/spa\/treatments$/.test(path)) data = [state.course];
    else if (path.endsWith('/treatments/41')) data = state.course;
    else if (path.endsWith('/treatments/41/intake')) { state.course.status = 'ACTIVE'; state.course.intakeNotes = body.notes; state.course.allowedActions = state.course.allowedActions.filter(action => action !== 'INTAKE'); data = state.course; }
    else if (path.endsWith('/appointments/701')) data = state.appointment;
    else if (path.endsWith('/appointments/701/items/702/execution')) { state.appointment.items[0].executionStatus = body.status; state.appointment.items[0].executionNotes = body.notes; data = state.appointment; }
    else if (path.endsWith('/sessions/51/available-slots')) data = ['09:00', '10:30'];
    else if (path.endsWith('/sessions/51/book')) { Object.assign(state.course.sessions[0], { status: 'SCHEDULED', appointmentId: 701, appointmentDate: body.appointmentDate, startTime: body.startTime, allowedActions: [] }); state.course.sessions[0].items[0].appointmentStatus = 'PENDING'; data = { id: 701, status: 'PENDING' }; }
    else if (path.endsWith('/requests/81/approve')) { state.course.status = 'CANCELLED'; state.course.requests[0].status = 'AWAITING_REFUND'; state.course.requests[0].refundAmount = 1200000; data = state.course; }
    else if (path.endsWith('/requests/81/refund-confirmation')) { state.course.requests[0].status = 'REFUNDED'; state.course.requests[0].refundReference = body.reference; data = state.course; }
    else if (path.endsWith('/treatments/report')) data = { totalCourses: 1, pendingIntake: 1, activeCourses: 0, pausedCourses: 0, completedCourses: 0, expiredCourses: 0, sessionsDue: 1, customersWithoutNextBooking: 1, collectedAmount: 1200000, refundedAmount: 0, unusedEntitlementAmount: 1200000, byStatus: { PENDING_INTAKE: 1 } };
    await route.fulfill({ json: { data } });
  });
  return { state, pageErrors };
}

test('USER buys a published treatment and retries the same purchase idempotency key', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'USER', { purchaseFailures: 1 });
  await page.goto('/#/treatments');
  await page.getByRole('button', { name: 'Xem & đăng ký lộ trình' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: 'Tạo đơn & thanh toán' })).toBeDisabled();
  await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button', { name: 'Tạo đơn & thanh toán' }).click();
  await expect(dialog.getByRole('alert')).toContainText('Tạm mất kết nối');
  await dialog.getByRole('button', { name: 'Kiểm tra lại đơn & thanh toán' }).click();
  await expect(page).toHaveURL(/order-success\?id=800/);
  const purchases = state.calls.filter(call => call.path.endsWith('/treatment-purchases'));
  expect(purchases).toHaveLength(2); expect(purchases[0].body).toEqual(purchases[1].body);
  expect(purchases[0].body).toMatchObject({ templateId: 11, versionId: 31, acceptedTerms: true });
  expect(pageErrors).toEqual([]);
});

test('USER books a session using its purchased ticket and treatment item', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'USER');
  await page.goto('/#/treatments?course=41');
  await page.getByRole('button', { name: 'Đặt lịch buổi 1', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: '10:30', exact: true }).click();
  await dialog.getByRole('button', { name: 'Gửi yêu cầu đặt buổi' }).click();
  await expect(dialog).not.toBeVisible();
  expect(state.calls.find(call => call.path.endsWith('/sessions/51/book'))?.body.items).toEqual([{ serviceId: 2, ticketId: 91, treatmentSessionItemId: 61 }]);
  await expect(page.locator('[data-treatment-session-id="51"]')).toContainText('Lịch hẹn #701');
  expect(pageErrors).toEqual([]);
});

test('STAFF can open the assigned operational course and perform intake', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'STAFF');
  await page.goto('/#/staff/treatments');
  await page.getByRole('button', { name: 'Mở hồ sơ & tiến độ' }).click();
  await page.getByRole('button', { name: 'Tiếp nhận lộ trình', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Kết quả tiếp nhận (nội bộ)').fill('Đã rà soát hồ sơ, giữ kế hoạch đã mua.');
  await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  expect(state.calls.find(call => call.path.endsWith('/treatments/41/intake'))?.path).toBe('/api/v1/staff/spa/treatments/41/intake');
  await expect(page.getByRole('button', { name: 'Tiếp nhận lộ trình', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tạo mẫu lộ trình', exact: true })).toHaveCount(0);
  expect(pageErrors).toEqual([]);
});

test('ADMIN creates and publishes a new template version without editing the sold version', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'ADMIN');
  await page.goto('/#/admin/treatments');
  await page.getByRole('button', { name: 'Mẫu & phiên bản', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo phiên bản mới', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Tạo phiên bản mới', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button', { name: 'Xuất bản', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Xuất bản', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  expect(state.calls.map(call => call.path)).toEqual(['/api/v1/admin/spa/treatment-templates/11/versions', '/api/v1/admin/spa/treatment-templates/11/publish']);
  expect(state.calls[0].body).toBeNull();
  await expect(page.locator('[data-treatment-template-id="11"]')).toContainText('v2');
  expect(pageErrors).toEqual([]);
});

test('ADMIN approves cancellation then confirms a real refund reference separately', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'ADMIN');
  state.course.requests = [{ id: 81, type: 'CANCEL', status: 'PENDING_APPROVAL', reason: 'Khách muốn dừng điều trị.', refundAmount: 0, refundReference: null }];
  await page.goto('/#/admin/treatments');
  await page.getByRole('button', { name: 'Mở hồ sơ & tiến độ' }).click();
  await page.getByRole('button', { name: 'Duyệt yêu cầu', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Lý do', { exact: true }).fill('Đã đối soát phần chưa sử dụng.');
  await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận quyết định' }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button', { name: 'Xác nhận đã hoàn tiền', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Mã chứng từ hoàn tiền').fill('REFUND-REAL-001');
  await page.getByRole('dialog').getByLabel('Lý do', { exact: true }).fill('Đã hoàn qua ngân hàng.');
  await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  expect(state.calls.find(call => call.path.endsWith('/refund-confirmation'))?.body).toEqual({ amount: 1200000, reference: 'REFUND-REAL-001', reason: 'Đã hoàn qua ngân hàng.' });
  await expect(page.getByText('Số tiền hoàn theo quyết định:', { exact: false })).toContainText('REFUND-REAL-001');
  expect(pageErrors).toEqual([]);
});

test('ADMIN confirms a negative revision refund without cancelling the active course', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'ADMIN');
  state.course.status = 'ACTIVE';
  state.course.requests = [{ id: 81, type: 'PRICE_ADJUSTMENT', status: 'AWAITING_REFUND', reason: 'Đã chấp thuận giảm nội dung chưa thực hiện.', refundAmount: 60000, refundReference: null }];
  await page.goto('/#/admin/treatments');
  await page.getByRole('button', { name: 'Mở hồ sơ & tiến độ' }).click();
  await page.getByRole('button', { name: 'Xác nhận đã hoàn tiền', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Xác nhận đã hoàn chênh lệch giảm giá');
  await page.getByRole('dialog').getByLabel('Mã chứng từ hoàn tiền').fill('REFUND-ADJUSTMENT-001');
  await page.getByRole('dialog').getByLabel('Lý do', { exact: true }).fill('Đã hoàn khoản chênh lệch.');
  await page.getByRole('dialog').getByRole('button', { name: 'Xác nhận', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  expect(state.course.status).toBe('ACTIVE');
  expect(state.calls.find(call => call.path.endsWith('/refund-confirmation'))?.body.amount).toBe(60000);
  expect(pageErrors).toEqual([]);
});

test('STAFF stops an interrupted treatment item only with an explicit reason', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'STAFF');
  state.course.status = 'PENDING_REVIEW';
  Object.assign(state.course.sessions[0], { status: 'IN_PROGRESS', appointmentId: 701, appointmentDate: '2026-10-16', startTime: '10:30:00', allowedActions: [] });
  Object.assign(state.course.sessions[0].items[0], { status: 'IN_PROGRESS', appointmentId: 701, appointmentStatus: 'IN_PROGRESS' });
  state.appointment = { id: 701, status: 'IN_PROGRESS', appointmentDate: '2026-10-16', startTime: '10:30:00', endTime: '11:30:00', items: [{ id: 702, serviceId: 2, treatmentSessionItemId: 61, serviceName: 'Làm sạch da', executionStatus: 'IN_PROGRESS', staffId: 5, staffName: 'Nhân viên phụ trách' }] };
  await page.goto('/#/staff/treatments');
  await page.getByRole('button', { name: 'Mở hồ sơ & tiến độ' }).click();
  await page.getByRole('button', { name: 'Chuẩn bị / phân công / thực hiện', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: 'Check-in', exact: true })).toHaveCount(0);
  await expect(dialog.getByText('Cọc, phí hủy/vắng hẹn & hoàn cọc', { exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: 'Dừng do sự cố', exact: true })).toBeDisabled();
  await dialog.getByLabel('Ghi chú / lý do thao tác').fill('Khách có phản ứng trong lúc thực hiện, đã dừng để đánh giá.');
  await dialog.getByRole('button', { name: 'Dừng do sự cố', exact: true }).click();
  await expect(dialog.getByRole('button', { name: 'Dừng do sự cố', exact: true })).toHaveCount(0);
  expect(state.calls.find(call => call.path.endsWith('/items/702/execution'))?.body).toEqual({ status: 'SKIPPED', notes: 'Khách có phản ứng trong lúc thực hiện, đã dừng để đánh giá.' });
  expect(state.course.status).toBe('PENDING_REVIEW');
  expect(state.course.completedSessions).toBe(0);
  expect(pageErrors).toEqual([]);
});

test('SPA reception coordinates appointments and receipts without fetching treatment clinical forms', async ({ page }) => {
  const { state, pageErrors } = await fixture(page, 'STAFF', { permissions: ['SPA_RECEPTION'] });
  state.course.allowedActions = ['ASSIGN', 'REQUEST_CANCEL'];
  Object.assign(state.course.sessions[0], { status: 'SCHEDULED', appointmentId: 701, appointmentDate: '2026-10-20', startTime: '10:30:00', allowedActions: [] });
  Object.assign(state.course.sessions[0].items[0], { status: 'SCHEDULED', appointmentId: 701, appointmentStatus: 'CONFIRMED' });
  state.appointment = { id: 701, userId: 19, status: 'CONFIRMED', appointmentDate: '2026-10-20', startTime: '10:30:00', endTime: '11:30:00', items: [{ id: 702, serviceId: 2, treatmentSessionItemId: 61, serviceName: 'Làm sạch da', executionStatus: 'PLANNED', staffId: 5, staffName: 'Assigned therapist' }] };
  await page.goto('/#/staff/treatments');
  await page.getByRole('button', { name: 'Mở hồ sơ & tiến độ' }).click();
  await page.getByRole('button', { name: 'Chuẩn bị / phân công / thực hiện', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: 'Check-in', exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Xác nhận & phân công', exact: true })).toBeVisible();
  await dialog.getByText('Cọc, phí hủy/vắng hẹn & hoàn cọc', { exact: true }).click();
  await expect(dialog.getByLabel('Loại phiếu')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Bắt đầu dịch vụ', exact: true })).toHaveCount(0);
  expect(state.reads).not.toContain('/api/v1/spa/appointments/701/preparation');
  expect(state.reads).not.toContain('/api/v1/spa/appointments/701/care-summary');
  expect(state.reads).not.toContain('/api/v1/spa/appointments/701/follow-up-instructions');
  expect(pageErrors).toEqual([]);
});
