// receptionReschedule.check.js - Pure Node assert self-check for Reception Reschedule UX logic
import assert from 'node:assert/strict';

function calculateDuration(startTime, endTime) {
  if (!startTime || !endTime) return 60;
  const [sh, sm] = startTime.slice(0, 5).split(':').map(Number);
  const [eh, em] = endTime.slice(0, 5).split(':').map(Number);
  const diff = (eh * 60 + em) - (sh * 60 + sm);
  return diff > 0 ? diff : 60;
}

function calculateEstimatedEnd(startTime, durationMinutes) {
  if (!startTime) return '';
  const [h, m] = startTime.slice(0, 5).split(':').map(Number);
  const total = h * 60 + m + durationMinutes;
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

function isExceedingClosing(estimatedEnd, closingHour = 20) {
  if (!estimatedEnd) return false;
  const [h, m] = estimatedEnd.split(':').map(Number);
  return (h * 60 + m) > closingHour * 60;
}

// 1. Duration calculation
assert.equal(calculateDuration('10:00:00', '11:30:00'), 90);
assert.equal(calculateDuration('09:00', '09:45'), 45);
assert.equal(calculateDuration(null, null), 60);

// 2. Estimated end time calculation
assert.equal(calculateEstimatedEnd('14:30', 90), '16:00');
assert.equal(calculateEstimatedEnd('18:30', 60), '19:30');

// 3. Exceeds closing time check (closing at 20:00)
assert.equal(isExceedingClosing(calculateEstimatedEnd('18:30', 60)), false); // Ends 19:30 <= 20:00
assert.equal(isExceedingClosing(calculateEstimatedEnd('19:00', 60)), false); // Ends 20:00 <= 20:00
assert.equal(isExceedingClosing(calculateEstimatedEnd('19:30', 60)), true);  // Ends 20:30 > 20:00
assert.equal(isExceedingClosing(calculateEstimatedEnd('18:30', 120)), true); // Ends 20:30 > 20:00

console.log('receptionReschedule.check.js: All assert checks passed.');
