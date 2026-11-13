import assert from 'node:assert/strict';
import { availableTicket, knownCount, pageWindow, upcomingAppointments } from './profileModel.js';

const now = Date.parse('2026-11-13T07:00:00Z');
const appointments = [
  { id: 1, status: 'CONFIRMED', appointmentDate: '2026-11-20', startTime: '10:00:00' },
  { id: 2, status: 'CONFIRMED', appointmentDate: '2026-11-14', startTime: '10:00:00' },
  { id: 3, status: 'CONFIRMED', appointmentDate: '2026-11-13', startTime: '13:59:00' },
  { id: 4, status: 'CANCELLED', appointmentDate: '2026-11-14', startTime: '10:00:00' },
  { id: 5, status: 'IN_PROGRESS', appointmentDate: '2026-11-13', startTime: '13:00:00' },
];
assert.deepEqual(upcomingAppointments(appointments, now).map(row => row.id), [5, 2, 1]);
assert.equal(knownCount(null), null);
assert.equal(knownCount('0'), 0);
assert.equal(knownCount(-1), null);
const ticket = { status: 'ACTIVE', orderId: 4, remainingSessions: 1, expiryDate: '2026-12-01T00:00:00Z' };
assert.equal(availableTicket(ticket, now), true);
for (const patch of [{ status: null }, { remainingSessions: null }, { remainingSessions: 0 }, { orderId: null }, { expiryDate: 'invalid' }, { expiryDate: '2026-11-01T00:00:00Z' }]) {
  assert.equal(availableTicket({ ...ticket, ...patch }, now), false);
}
assert.deepEqual(pageWindow([1, 2, 3, 4, 5], 9, 2), { page: 2, totalPages: 3, rows: [5] });
assert.deepEqual(pageWindow([], 3, 6), { page: 0, totalPages: 1, rows: [] });
console.log('profileModel: all checks passed');
