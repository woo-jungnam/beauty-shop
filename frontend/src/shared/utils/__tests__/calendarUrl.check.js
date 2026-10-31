import assert from 'node:assert/strict';
import { buildGoogleCalendarUrl } from '../formatters.js';

// Test 1: Full valid inputs
const url1 = buildGoogleCalendarUrl({
  title: 'Chăm sóc da chuyên sâu',
  date: '2026-11-05',
  startTime: '10:00',
  durationMinutes: 60,
  details: 'Mã lịch: SPA-APT-#101\nKTV: Nguyễn Văn A',
  location: '123 Đồng Khởi, Quận 1, TP.HCM',
});
assert.ok(url1.startsWith('https://calendar.google.com/calendar/render?action=TEMPLATE'));
assert.ok(url1.includes('text=Ch%C4%83m%20s%C3%B3c%20da%20chuy%C3%AAn%20s%C3%A2u'));
assert.ok(url1.includes('dates=20261105T030000Z%2F20261105T040000Z'));
assert.ok(url1.includes('location=123%20%C4%90%E1%BB%93ng%20Kh%E1%BB%9Fi%2C%20Qu%E1%BA%ADn%201%2C%20TP.HCM'));

// Test 2: Edge cases - Missing date or time returns empty string
assert.equal(buildGoogleCalendarUrl({ date: '', startTime: '10:00' }), '');
assert.equal(buildGoogleCalendarUrl({ date: '2026-11-05', startTime: '' }), '');
assert.equal(buildGoogleCalendarUrl({ date: 'invalid-date', startTime: 'invalid-time' }), '');

// Test 3: Duration calculation check
const url3 = buildGoogleCalendarUrl({
  title: 'Massage 90 phút',
  date: '2026-11-05',
  startTime: '14:30:00',
  durationMinutes: 90,
});
// 14:30 +07:00 is 07:30 UTC. 90 mins later is 09:00 UTC.
assert.ok(url3.includes('dates=20261105T073000Z%2F20261105T090000Z'));

console.log('✓ All Google Calendar URL check tests passed successfully.');
