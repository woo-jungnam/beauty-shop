import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  availableTicket,
  classifyOverviewAppointments,
  formatVoucherCardInfo,
  knownCount,
  pageWindow,
  summarizeTickets,
  upcomingAppointments,
} from './profileModel.js';

const now = Date.parse('2026-11-13T07:00:00Z');
const appointments = [
  { id: 1, status: 'CONFIRMED', appointmentDate: '2026-11-20', startTime: '10:00:00' },
  { id: 2, status: 'CONFIRMED', appointmentDate: '2026-11-14', startTime: '10:00:00' },
  { id: 3, status: 'CONFIRMED', appointmentDate: '2026-11-13', startTime: '13:59:00' },
  { id: 4, status: 'CANCELLED', appointmentDate: '2026-11-14', startTime: '10:00:00' },
  { id: 5, status: 'IN_PROGRESS', appointmentDate: '2026-11-13', startTime: '13:00:00' },
];
assert.deepEqual(upcomingAppointments(appointments, now).map(row => row.id), [5, 2, 1]);

// Past-day checked-in appointment should be classified as staleActive rather than true upcoming
const staleCheckedIn = { id: 38, status: 'CONFIRMED', checkedInAt: '2026-11-15T02:00:00Z', appointmentDate: '2026-11-15', startTime: '09:00:00' };
const nov25Now = Date.parse('2026-11-25T10:00:00Z');
const classified = classifyOverviewAppointments([staleCheckedIn], nov25Now);
assert.equal(classified.isStaleActive, true);
assert.equal(classified.primaryAppointment.id, 38);
assert.equal(classified.futureAppointments.length, 0);

assert.equal(knownCount(null), null);
assert.equal(knownCount('0'), 0);
assert.equal(knownCount(-1), null);
const ticket = { status: 'ACTIVE', orderId: 4, remainingSessions: 1, expiryDate: '2026-12-01T00:00:00Z' };
assert.equal(availableTicket(ticket, now), true);
for (const patch of [{ status: null }, { remainingSessions: null }, { remainingSessions: 0 }, { orderId: null }, { expiryDate: 'invalid' }, { expiryDate: '2026-11-01T00:00:00Z' }]) {
  assert.equal(availableTicket({ ...ticket, ...patch }, now), false);
}

// Total remaining sessions sums across all active tickets
const ticketSummary = summarizeTickets([
  { id: 7, status: 'ACTIVE', orderId: 4, remainingSessions: 4, benefitType: 'TREATMENT_COURSE' },
  { id: 8, status: 'ACTIVE', orderId: 5, remainingSessions: 3, benefitType: 'SERVICE_PACKAGE' },
  { id: 9, status: 'EXHAUSTED', orderId: 6, remainingSessions: 0 },
], now);
assert.equal(ticketSummary.activeTickets.length, 2);
assert.equal(ticketSummary.totalRemainingSessions, 7);
assert.equal(ticketSummary.hasTreatmentCourse, true);

// Voucher formatting uses live fields
const vInfo = formatVoucherCardInfo({
  code: 'REAL10',
  name: 'Ưu đãi tháng 11',
  discountType: 'PERCENTAGE',
  discountValue: 10,
  maxDiscountAmount: 100000,
  minOrderAmount: 500000,
  endsAt: '2026-11-30T16:59:59Z',
});
assert.equal(vInfo.discountLabel.includes('10%'), true);
assert.equal(vInfo.conditionLabel.includes('500.000'), true);
assert.equal(vInfo.expiryLabel.startsWith('HSD:'), true);

assert.deepEqual(pageWindow([1, 2, 3, 4, 5], 9, 2), { page: 2, totalPages: 3, rows: [5] });
assert.deepEqual(pageWindow([], 3, 6), { page: 0, totalPages: 1, rows: [] });

// Ensure hardcoded fake vouchers & alert are removed from CustomerProfilePage
const pageSource = fs.readFileSync(
  path.resolve(import.meta.dirname, '../pages/CustomerProfilePage.jsx'),
  'utf8'
);
assert.equal(pageSource.includes('BS-VIP300K'), false);
assert.equal(pageSource.includes('5 MÃ KHẢ DỤNG'), false);
assert.equal(pageSource.includes('ENDPOINTS.VOUCHERS.AVAILABLE'), true);

console.log('profileModel: all checks passed');
