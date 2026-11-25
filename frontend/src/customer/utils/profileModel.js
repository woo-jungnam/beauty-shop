export const knownCount = value => {
  if (!['number', 'string'].includes(typeof value) || String(value).trim() === '') return null;
  const count = Number(value);
  return Number.isSafeInteger(count) && count >= 0 ? count : null;
};

const vietnamDateKey = (now = Date.now()) =>
  new Date(now).toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });

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

export const isAppointmentPastDay = (appointment, now = Date.now()) => {
  const date = String(appointment?.appointmentDate || '').slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return false;
  return date < vietnamDateKey(now);
};

export const classifyOverviewAppointments = (appointments = [], now = Date.now()) => {
  const activeOrUpcoming = upcomingAppointments(appointments, now);
  const futureAppointments = activeOrUpcoming.filter(item => !isAppointmentPastDay(item, now));
  const staleActiveAppointments = activeOrUpcoming.filter(item => isAppointmentPastDay(item, now));
  const primaryAppointment = futureAppointments[0] || staleActiveAppointments[0] || null;
  return {
    activeOrUpcoming,
    futureAppointments,
    staleActiveAppointments,
    primaryAppointment,
    isStaleActive: Boolean(primaryAppointment && !futureAppointments.length && staleActiveAppointments.length),
  };
};

export const availableTicket = (ticket, now = Date.now()) => ticket.status === 'ACTIVE'
  && Number(ticket.orderId) > 0 && knownCount(ticket.remainingSessions) > 0
  && (!ticket.expiryDate || Date.parse(ticket.expiryDate) > now);

export const summarizeTickets = (tickets = [], now = Date.now()) => {
  const activeTickets = tickets.filter(ticket => availableTicket(ticket, now));
  const totalRemainingSessions = activeTickets.reduce(
    (sum, ticket) => sum + (knownCount(ticket.remainingSessions) ?? 0),
    0
  );
  const firstTicket = activeTickets[0] || null;
  const hasTreatmentCourse = activeTickets.some(ticket => ticket.benefitType === 'TREATMENT_COURSE');
  return {
    activeTickets,
    firstTicket,
    totalRemainingSessions,
    hasTreatmentCourse,
  };
};

export const formatVoucherCardInfo = voucher => {
  if (!voucher) return { discountLabel: '', conditionLabel: '', expiryLabel: '' };
  const money = value => new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value || 0));

  const discountLabel = voucher.discountType === 'PERCENTAGE'
    ? `Giảm ${voucher.discountValue}%${voucher.maxDiscountAmount != null ? ` (tối đa ${money(voucher.maxDiscountAmount)})` : ''}`
    : `Giảm ${money(voucher.discountValue)}`;

  const conditionParts = [];
  if (voucher.name) conditionParts.push(voucher.name);
  if (Number(voucher.minOrderAmount || 0) > 0) {
    conditionParts.push(`Đơn tối thiểu ${money(voucher.minOrderAmount)}`);
  }

  let expiryLabel = 'Không giới hạn hạn dùng';
  if (voucher.endsAt && Number.isFinite(Date.parse(voucher.endsAt))) {
    const formattedDate = new Date(voucher.endsAt).toLocaleDateString('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
    });
    expiryLabel = `HSD: ${formattedDate}`;
  }

  return {
    discountLabel,
    conditionLabel: voucher.description || conditionParts.join(' · ') || 'Áp dụng tại bước thanh toán',
    expiryLabel,
  };
};

export const pageWindow = (rows, page, pageSize) => {
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const current = Math.max(0, Math.min(Number.isSafeInteger(page) ? page : 0, totalPages - 1));
  return { page: current, totalPages, rows: rows.slice(current * pageSize, (current + 1) * pageSize) };
};
