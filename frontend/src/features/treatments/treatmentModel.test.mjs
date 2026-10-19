import assert from 'node:assert/strict';
import test from 'node:test';
import { bookingPayload, dateLabel, hasAction, planPayload, statusLabel } from './treatmentModel.js';

test('continuing a partially performed session books only remaining entitlements for the correct patient', () => {
  const course = { userId: 19 };
  const session = { items: [
    { id: 101, serviceId: 1, ticketId: 44, status: 'COMPLETED' },
    { id: 102, serviceId: 2, ticketId: 44, status: 'PLANNED' },
    { id: 103, serviceId: 3, ticketId: 45, status: 'PERFORMED' },
  ] };
  const payload = bookingPayload({ course, session, role: 'staff', date: '2026-10-20', slot: '10:30', notes: '  Theo yêu cầu khách  ' });
  assert.deepEqual(payload, { appointmentDate: '2026-10-20', startTime: '10:30:00', notes: 'Theo yêu cầu khách', targetUserId: 19, items: [{ serviceId: 2, ticketId: 44, treatmentSessionItemId: 102 }] });
  assert.equal(session.items.length, 3, 'Building a retry must not mutate the downloaded session');
  const customer = bookingPayload({ course, session, role: 'customer', date: '2026-10-20', slot: '10:30:00' });
  assert.equal(Object.hasOwn(customer, 'targetUserId'), false, 'Customer must not send an operator target user');
});

test('revision serialization keeps optional limits and required flags while dropping server-owned identifiers', () => {
  const source = { id: 88, version: 3, name: '  Kế hoạch đã mua  ', description: 'Mô tả', terms: 'Điều khoản snapshot', serviceId: '9', price: '1200000', validityDays: '90', sessions: [
    { id: 900, sequence: 9, title: '  Tiếp nhận  ', minGapDays: '0', maxGapDays: '', required: true, items: [{ id: 555, serviceId: '2', serviceName: 'Tên snapshot', required: false }, { serviceId: '3', required: true }] },
    { title: 'Theo dõi', minGapDays: '7', maxGapDays: '14', required: false, items: [{ serviceId: '2', required: true }] },
  ] };
  const plan = planPayload(source);
  assert.equal(plan.name, 'Kế hoạch đã mua');
  assert.equal(plan.terms, source.terms);
  assert.equal(plan.sessions[0].maxGapDays, null);
  assert.equal(plan.sessions[0].sequence, 1);
  assert.equal(plan.sessions[1].sequence, 2);
  assert.equal(plan.sessions[1].minGapDays, 7);
  assert.equal(plan.sessions[1].maxGapDays, 14);
  assert.equal(plan.sessions[1].required, false);
  assert.deepEqual(plan.sessions[0].items[0], { serviceId: 2, required: false });
  assert.equal(Object.hasOwn(plan, 'version'), false);
  assert.equal(source.sessions[0].sequence, 9);
});

test('UI actions require explicit server permission and invalid timestamps do not crash timeline', () => {
  assert.equal(hasAction({ status: 'ACTIVE' }, 'BOOK'), false);
  assert.equal(hasAction({ allowedActions: ['WRITE_RESULT'] }, 'BOOK'), false);
  assert.equal(hasAction({ allowedActions: ['BOOK'] }, 'BOOK'), true);
  assert.equal(dateLabel(null), 'Chưa xác định');
  assert.equal(dateLabel('invalid-time'), 'Chưa xác định');
  assert.equal(statusLabel('NO_SHOW'), 'Vắng hẹn');
  assert.equal(statusLabel('AWAITING_SETTLEMENT'), 'Chờ thanh toán chênh lệch');
  assert.notEqual(statusLabel('NO_SHOW'), statusLabel('COMPLETED'));
});
