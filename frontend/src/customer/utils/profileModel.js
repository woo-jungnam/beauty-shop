export const knownCount = value => {
  if (!['number', 'string'].includes(typeof value) || String(value).trim() === '') return null;
  const count = Number(value);
  return Number.isSafeInteger(count) && count >= 0 ? count : null;
};

export const upcomingAppointments = (appointments, now = Date.now()) => appointments.filter(appointment => {
  if (['CHECKED_IN', 'IN_PROGRESS'].includes(appointment.status) || appointment.status === 'CONFIRMED' && appointment.checkedInAt) return true;
  if (!['PENDING', 'CONFIRMED'].includes(appointment.status) || !appointment.appointmentDate || !appointment.startTime) return false;
  const start = Date.parse(`${appointment.appointmentDate}T${appointment.startTime}+07:00`);
  return Number.isFinite(start) && start > now;
}).sort((a, b) => {
  const active = appointment => Boolean(appointment.checkedInAt) || ['CHECKED_IN', 'IN_PROGRESS'].includes(appointment.status);
  return Number(active(b)) - Number(active(a))
    || `${a.appointmentDate}T${a.startTime}`.localeCompare(`${b.appointmentDate}T${b.startTime}`);
});

export const availableTicket = (ticket, now = Date.now()) => ticket.status === 'ACTIVE'
  && Number(ticket.orderId) > 0 && knownCount(ticket.remainingSessions) > 0
  && (!ticket.expiryDate || Date.parse(ticket.expiryDate) > now);

export const pageWindow = (rows, page, pageSize) => {
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const current = Math.max(0, Math.min(Number.isSafeInteger(page) ? page : 0, totalPages - 1));
  return { page: current, totalPages, rows: rows.slice(current * pageSize, (current + 1) * pageSize) };
};
