export const hasAction = (resource, action) => Array.isArray(resource?.allowedActions) && resource.allowedActions.includes(action);
export const money = amount => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount || 0);
export const dateLabel = value => {
  if (!value) return 'Chưa xác định';
  const date = new Date(String(value).length === 10 ? `${value}T12:00:00+07:00` : value);
  return Number.isNaN(date.valueOf()) ? 'Chưa xác định' : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'medium' }).format(date);
};
export const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
export const idempotencyKey = () => globalThis.crypto?.randomUUID?.() || `treatment-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const statuses = {
  DRAFT: 'Bản nháp', PUBLISHED: 'Đang bán', ARCHIVED: 'Ngừng bán',
  PENDING_INTAKE: 'Chờ tiếp nhận', AWAITING_INTAKE: 'Chờ tiếp nhận', ACTIVE: 'Đang điều trị', PAUSED: 'Tạm dừng', ON_HOLD: 'Chờ đánh giá', PENDING_REVIEW: 'Chờ đánh giá sự cố',
  PENDING: 'Chờ xử lý', CONFIRMED: 'Đã xác nhận', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã dừng', REFUND_PENDING: 'Chờ hoàn tiền', AWAITING_REFUND: 'Chờ chứng từ hoàn tiền', REFUNDED: 'Đã hoàn tiền', EXPIRED: 'Hết hiệu lực',
  PLANNED: 'Chưa đặt', READY: 'Có thể đặt', BOOKED: 'Đã đặt lịch', SCHEDULED: 'Đã đặt lịch', IN_PROGRESS: 'Đang thực hiện', PARTIAL: 'Đã làm một phần', PERFORMED: 'Đã thực hiện', SKIPPED: 'Chưa thực hiện', WAIVED: 'Đã miễn theo kế hoạch', NO_SHOW: 'Vắng hẹn',
  PENDING_APPROVAL: 'Chờ quản trị duyệt', PENDING_CUSTOMER: 'Chờ khách xác nhận', PENDING_CUSTOMER_ACCEPTANCE: 'Chờ khách xác nhận', AWAITING_CUSTOMER: 'Chờ khách xác nhận', AWAITING_SETTLEMENT: 'Chờ thanh toán chênh lệch', APPROVED: 'Đã duyệt', ACCEPTED: 'Đã chấp thuận', REJECTED: 'Đã từ chối', APPLIED: 'Đã áp dụng',
  PAUSE: 'Tạm dừng', RESUME: 'Tiếp tục', CANCEL: 'Dừng / hoàn tiền', EXTEND: 'Gia hạn', INCIDENT: 'Đánh giá sự cố / phản ứng', PRICE_ADJUSTMENT: 'Hoàn chênh lệch giảm giá', UNAPPLIED_SETTLEMENT: 'Hoàn thanh toán điều chỉnh chưa áp dụng', SETTLEMENT_REFUND_PENDING: 'Chờ hoàn thanh toán điều chỉnh', AWAITING_RESULT: 'Chờ ghi kết quả buổi',
};
export const statusLabel = value => statuses[value] || value || 'Chưa xác định';
export const planPayload = form => ({ name: form.name.trim(), description: form.description || '', terms: form.terms || '', serviceId: Number(form.serviceId), price: Number(form.price), validityDays: Number(form.validityDays), sessions: form.sessions.map((session, index) => ({ sequence: index + 1, title: session.title.trim(), minGapDays: Number(session.minGapDays), maxGapDays: session.maxGapDays === '' || session.maxGapDays == null ? null : Number(session.maxGapDays), required: session.required, items: session.items.map(item => ({ serviceId: Number(item.serviceId), required: item.required })) })) });
export const bookingPayload = ({ course, session, role, date, slot, notes = '' }) => ({ appointmentDate: date, startTime: slot.length === 5 ? `${slot}:00` : slot, notes: notes.trim(), ...(role !== 'customer' ? { targetUserId: course.userId } : {}), items: session.items.filter(item => !['PERFORMED', 'COMPLETED'].includes(item.status)).map(item => ({ serviceId: item.serviceId, ticketId: item.ticketId, treatmentSessionItemId: item.id })) });
