// spaBookingContract.check.js - Pure Node assert self-check for SPA booking contract
import assert from 'node:assert/strict';

// 1. Contract validation: BookAppointmentRequest payload structure
function buildBookingPayload({ appointmentDate, startTime, skinNote, serviceId, staffId, ticketId }) {
  assert(appointmentDate, 'appointmentDate is required');
  assert(startTime, 'startTime is required');
  assert(serviceId, 'serviceId is required');

  return {
    appointmentDate,
    startTime: startTime.length === 5 ? `${startTime}:00` : startTime,
    notes: skinNote?.trim() || undefined,
    items: [
      {
        serviceId: Number(serviceId),
        staffId: staffId ? Number(staffId) : null,
        ticketId: ticketId ? Number(ticketId) : null,
      }
    ]
  };
}

// Test case 1: Standard booking without ticket
const p1 = buildBookingPayload({
  appointmentDate: '2026-10-10',
  startTime: '10:00',
  skinNote: 'Da nhạy cảm',
  serviceId: 1,
  staffId: '2',
  ticketId: null
});
assert.equal(p1.appointmentDate, '2026-10-10');
assert.equal(p1.startTime, '10:00:00');
assert.equal(p1.notes, 'Da nhạy cảm');
assert.equal(p1.items.length, 1);
assert.equal(p1.items[0].serviceId, 1);
assert.equal(p1.items[0].staffId, 2);
assert.equal(p1.items[0].ticketId, null);

// Test case 2: Booking with prepaid ticket
const p2 = buildBookingPayload({
  appointmentDate: '2026-10-12',
  startTime: '14:30:00',
  serviceId: 3,
  ticketId: 105
});
assert.equal(p2.items[0].ticketId, 105);
assert.equal(p2.items[0].staffId, null);
assert.equal(p2.notes, undefined);

// 2. Contract validation: Admin staff assignment map
function buildStaffAssignments(items, rawAssignments) {
  // Must map by item.id, NOT item.serviceId
  return Object.fromEntries(
    items
      .filter(item => rawAssignments[item.id] !== undefined && rawAssignments[item.id] !== '')
      .map(item => [String(item.id), Number(rawAssignments[item.id])])
  );
}

const mockItems = [
  { id: 101, serviceId: 1, serviceName: 'Aqua Peel' },
  { id: 102, serviceId: 2, serviceName: 'Trị mụn y khoa' }
];
const rawAssignments = { 101: '1', 102: '2' };
const assignments = buildStaffAssignments(mockItems, rawAssignments);
assert.deepEqual(assignments, { '101': 1, '102': 2 });
assert.equal(assignments['1'], undefined, 'Must not map by serviceId');

// 3. Status enum check
const VALID_SPA_STATUSES = new Set(['PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW']);
assert(VALID_SPA_STATUSES.has('IN_PROGRESS'), 'IN_PROGRESS must be valid');
assert(!VALID_SPA_STATUSES.has('IN_SERVICE'), 'IN_SERVICE must not be used');

console.log('SPA booking contract check: ALL PASSED');
