import { getVietnamDateString } from '../../shared/utils/formatters';

export const SPA_STATUS = {
  PENDING: { label: 'Chờ tiếp đón', tone: 'waiting' },
  CONFIRMED: { label: 'Đã xác nhận', tone: 'confirmed' },
  IN_PROGRESS: { label: 'Đang làm dịch vụ', tone: 'progress' },
  COMPLETED: { label: 'Hoàn tất / Thu ngân', tone: 'complete' },
  CANCELLED: { label: 'Đã hủy', tone: 'neutral' },
  NO_SHOW: { label: 'Khách vắng mặt', tone: 'neutral' },
};
export const KANBAN_COLUMNS = [
  { id: 'PENDING', title: 'Chờ tiếp đón', caption: 'Xác nhận khách và phân ca', tone: 'waiting' },
  { id: 'CONFIRMED', title: 'Đã check-in', caption: 'Khách tại sảnh / đã xác nhận', tone: 'confirmed' },
  { id: 'IN_PROGRESS', title: 'Đang làm dịch vụ', caption: 'Theo dõi ca và kỹ thuật viên', tone: 'progress' },
  { id: 'COMPLETED', title: 'Hoàn tất / Thu ngân', caption: 'Đối soát vé và khoản cần thu', tone: 'complete' },
];
export const unwrap = response => response?.data ?? response;
export const asList = response => {
  const data = unwrap(response);
  return Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : [];
};
export const spaTime = date => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
}).format(date);
export const localInstant = (date, time) => date && time ? Date.parse(`${date}T${String(time).slice(0, 8).padEnd(8, ':00')}+07:00`) : NaN;
export const normalizePhone = value => String(value || '').replace(/\D/g, '').replace(/^84(?=\d{9}$)/, '0');
export const appointmentCode = appointment => `SPA-${String(appointment.id).padStart(5, '0')}`;
export const isFinalItem = item => ['PERFORMED', 'SKIPPED', 'LEGACY_FINALIZED'].includes(item.executionStatus);
export const canFinish = appointment => Boolean(appointment.items?.length) && appointment.items.every(isFinalItem);
export const canCheckInToday = (appointment, now) => appointment.appointmentDate === getVietnamDateString(now);
export const bookingDefaults = (services = [], facilityId = '') => {
  const next = new Date(Date.now() + 30 * 60 * 1000);
  const parts = spaTime(next).split(':').map(Number);
  const minutes = Math.floor((parts[0] * 60 + parts[1]) / 30) * 30;
  const time = minutes < 480 ? '08:00' : minutes >= 1200 ? '08:00' : `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
  const date = minutes >= 1200 ? getVietnamDateString(new Date(next.getTime() + 86400000)) : getVietnamDateString(next);
  return { customerName: '', phone: '', customerId: '', serviceId: String(services[0]?.id || ''), staffId: '', facilityId: String(facilityId), ticketId: '', appointmentDate: date, startTime: `${time}:00`, notes: '', autoCheckIn: date === getVietnamDateString() };
};

export const facilityState = (facility, appointments, blocks, now, known = true) => {
  const capacity = Math.max(1, Number(facility.capacity) || 1);
  if (!known) return { state: 'unknown', label: 'Chưa xác minh', detail: 'Đang chờ dữ liệu mới', available: 0, capacity };
  const maintenance = (blocks || []).find(block => {
    const start = Date.parse(`${block.startAt}${/(Z|[+-]\d{2}:\d{2})$/.test(block.startAt) ? '' : '+07:00'}`);
    const end = Date.parse(`${block.endAt}${/(Z|[+-]\d{2}:\d{2})$/.test(block.endAt) ? '' : '+07:00'}`);
    return start <= now.getTime() && now.getTime() < end;
  });
  if (facility.active === false || facility.isActive === false || maintenance) {
    return { state: 'paused', label: maintenance && /vệ sinh|khử trùng/i.test(maintenance.reason) ? 'Đang vệ sinh' : 'Tạm dừng', detail: maintenance?.reason || 'Chưa sẵn sàng phục vụ', available: 0, capacity };
  }
  const uses = [];
  for (const appointment of appointments) {
    if (!['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(appointment.status)) continue;
    for (const item of appointment.items || []) {
      const allocation = item.resources?.find(resource => String(resource.facilityId) === String(facility.id));
      if (!allocation && String(item.facilityId) !== String(facility.id)) continue;
      const start = localInstant(appointment.appointmentDate, item.startTime || appointment.startTime);
      const end = localInstant(appointment.appointmentDate, item.endTime || appointment.endTime);
      const executing = item.executionStatus === 'IN_PROGRESS' || (appointment.status === 'IN_PROGRESS' && !isFinalItem(item) && start <= now.getTime());
      if (!executing && !(start <= now.getTime() && now.getTime() < end)) continue;
      if (item.executionStatus === 'SKIPPED') continue;
      uses.push({ appointment, item, end, executing, quantity: Math.max(1, Number(allocation?.quantity) || 1) });
    }
  }
  const used = uses.reduce((sum, use) => sum + use.quantity, 0);
  const active = uses.find(use => use.executing);
  if (uses.length) {
    const end = active?.end || Math.max(...uses.map(use => use.end));
    const minutes = Number.isFinite(end) ? Math.ceil((end - now.getTime()) / 60000) : null;
    return { state: 'busy', label: active ? 'Đang phục vụ' : 'Đã giữ chỗ', detail: minutes === null ? 'Chờ xác nhận thời gian' : minutes > 0 ? `Còn khoảng ${minutes} phút` : `Quá dự kiến ${Math.abs(minutes)} phút`, available: Math.max(0, capacity - used), capacity, customer: active?.appointment.customerName };
  }
  return { state: 'free', label: 'Sẵn sàng nhận khách', detail: `${capacity} ${facility.type === 'ROOM' ? 'chỗ' : 'đơn vị'} đang trống`, available: capacity, capacity };
};
