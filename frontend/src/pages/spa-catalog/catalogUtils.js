export const unwrap = response => response?.data ?? response;
export const asList = response => {
  const data = unwrap(response);
  return Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : [];
};
export const slugify = value => String(value).trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/[đĐ]/g, 'd').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
export const RESOURCE_NAMES = { BED: 'Giường chăm sóc', ROOM: 'Phòng trị liệu', MACHINE: 'Máy chuyên dụng', GENERAL: 'Khu vực chung', LASER: 'Máy Laser', HIFU: 'Máy HIFU' };
export const resourceName = type => RESOURCE_NAMES[type] || `Thiết bị ${type}`;
export const packageValue = (pack, services) => {
  const prices = new Map(services.map(service => [String(service.id), Number(service.basePrice)]));
  const known = (pack.items || []).every(item => prices.has(String(item.serviceId)));
  const original = (pack.items || []).reduce((sum, item) => sum + (prices.get(String(item.serviceId)) || 0) * Number(item.quantity || 0), 0);
  return { original, known, percent: original > Number(pack.price) && known ? (original - Number(pack.price)) / original * 100 : 0 };
};
