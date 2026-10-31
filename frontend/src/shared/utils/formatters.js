import { serverBaseUrl } from '../api/baseUrl';

export const formatCurrency = (amount) => {
  if (amount === undefined || amount === null) return '0 đ';
  const num = typeof amount === 'number' ? amount : parseFloat(amount);
  if (isNaN(num)) return '0 đ';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(num);
};

export const formatDateTime = (isoString) => {
  if (!isoString) return '-';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '-';
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  } catch {
    return isoString;
  }
};

export const formatDate = (isoString) => {
  if (!isoString) return '-';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '-';
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
  } catch {
    return isoString;
  }
};

export const formatNumber = (num) => {
  if (num === undefined || num === null) return '0';
  return new Intl.NumberFormat('vi-VN').format(num);
};

export const getOrderStatusBadge = (status) => {
  switch (status) {
    case 'PENDING':
      return { variant: 'warning', text: 'Chờ xác nhận' };
    case 'CONFIRMED':
      return { variant: 'info', text: 'Đã xác nhận' };
    case 'PROCESSING':
      return { variant: 'info', text: 'Đang xử lý' };
    case 'DELIVERY_FAILED':
      return { text: 'Giao không thành công', variant: 'warning' };
    case 'RETURN_TO_SENDER':
      return { text: 'Hàng hoàn về kho', variant: 'danger' };
    case 'SHIPPED':
      return { variant: 'default', text: 'Đang giao hàng' };
    case 'DELIVERED':
      return { variant: 'success', text: 'Đã giao thành công' };
    case 'COMPLETED':
      return { variant: 'success', text: 'Hoàn tất' };
    case 'CANCELLED':
      return { variant: 'danger', text: 'Đã hủy đơn' };
    case 'RETURNED':
      return { variant: 'danger', text: 'Đã hoàn trả hàng' };
    case 'REQUESTED':
      return { variant: 'warning', text: 'Yêu cầu đổi trả' };
    case 'APPROVED':
      return { variant: 'info', text: 'Đã duyệt đổi trả' };
    case 'RECEIVED':
      return { variant: 'info', text: 'Đã nhận hàng trả' };
    case 'REJECTED':
      return { variant: 'danger', text: 'Từ chối đổi trả' };
    case 'REFUNDED':
      return { variant: 'success', text: 'Đã hoàn tiền' };
    default:
      return { variant: 'default', text: status || 'Chưa xác định' };
  }
};

export const getOrderType = (order) => {
  if (!order) return 'PRODUCT';
  if (order.servicePackageId != null) return 'SPA_PACKAGE';
  if (order.appointmentId != null || (order.spaVisitItems && order.spaVisitItems.length > 0)) return 'SPA_VISIT';
  return 'PRODUCT';
};

export const getOrderTypeBadge = (order) => {
  const type = typeof order === 'string' ? order : getOrderType(order);
  switch (type) {
    case 'SPA_PACKAGE':
      return { variant: 'info', text: 'Gói liệu trình Spa' };
    case 'SPA_VISIT':
      return { variant: 'warning', text: 'Hóa đơn tại quầy' };
    case 'PRODUCT':
    default:
      return { variant: 'default', text: 'Sản phẩm mỹ phẩm' };
  }
};

export const getOrderStatusText = (status) => {
  return getOrderStatusBadge(status).text;
};

export const getAppointmentStatusBadge = (status) => {
  switch (status) {
    case 'PENDING':
      return { variant: 'warning', text: 'Chờ duyệt' };
    case 'CONFIRMED':
      return { variant: 'info', text: 'Đã xác nhận' };
    case 'IN_PROGRESS':
      return { variant: 'info', text: 'Đang thực hiện' };
    case 'COMPLETED':
      return { variant: 'success', text: 'Đã hoàn tất' };
    case 'CANCELLED':
      return { variant: 'danger', text: 'Đã hủy hẹn' };
    case 'NO_SHOW':
      return { variant: 'danger', text: 'Vắng mặt' };
    default:
      return { variant: 'default', text: status || 'Chưa xác định' };
  }
};

export const getAppointmentStatusText = (status) => {
  return getAppointmentStatusBadge(status).text;
};

export const getTicketStatusBadge = (status) => {
  switch (status) {
    case 'COMPLETED':
      return { variant: 'default', text: 'Đã dùng hết' };
    case 'REVOKED':
      return { variant: 'danger', text: 'Đã thu hồi' };
    case 'ACTIVE':
      return { variant: 'success', text: 'Còn hiệu lực' };
    case 'EXPIRED':
      return { variant: 'danger', text: 'Đã hết hạn' };
    case 'USED':
      return { variant: 'default', text: 'Đã dùng hết' };
    case 'CANCELLED':
      return { variant: 'danger', text: 'Đã hủy' };
    default:
      return { variant: 'default', text: status || 'Chưa xác định' };
  }
};

export const getTicketStatusText = (status) => {
  return getTicketStatusBadge(status).text;
};

export const getPaymentMethodLabel = (method) => {
  switch (method) {
    case 'COD':
      return 'Thanh toán khi nhận hàng (COD)';
    case 'BANK':
      return 'Chuyển khoản ngân hàng (QR Pay)';
    case 'VNPAY':
      return 'Cổng thanh toán VNPAY';
    case 'MOMO':
      return 'Ví MoMo';
    case 'WALLET':
      return 'Ví tiền tài khoản';
    case 'CASH':
      return 'Tiền mặt tại quầy';
    case 'WAIVE_WALK_IN':
      return 'Miễn cọc tại quầy';
    case 'DEPOSIT_AT_COUNTER':
      return 'Đặt cọc tại quầy';
    case 'PAY_FULL':
      return 'Thanh toán đủ 100% (VietQR)';
    case 'TICKET':
      return 'Khấu trừ vé liệu trình';
    default:
      return method || 'Chưa chọn';
  }
};

export const getMembershipTierLabel = (tier) => {
  switch (tier) {
    case 'MEMBER':
      return 'Thành viên';
    case 'SILVER':
      return 'Hạng Bạc';
    case 'GOLD':
      return 'Hạng Vàng';
    case 'PLATINUM':
      return 'Hạng Bạch kim';
    case 'BRONZE':
      return 'Hạng Đồng';
    case 'DIAMOND':
      return 'Hạng Kim cương';
    default:
      return tier || 'Thành viên';
  }
};

export const getRoleLabel = (roleName) => {
  if (!roleName) return '-';
  const clean = roleName.replace(/^ROLE_/, '');
  switch (clean) {
    case 'ADMIN':
      return 'Quản trị viên (ADMIN)';
    case 'STAFF':
      return 'Nhân viên vận hành (STAFF)';
    case 'USER':
    case 'CUSTOMER':
      return 'Người dùng (USER)';
    default:
      return clean;
  }
};

export const getPaymentStatusBadge = (status) => {
  switch (status) {
    case 'PAID':
      return { variant: 'success', text: 'Đã thanh toán' };
    case 'PENDING':
      return { variant: 'warning', text: 'Chờ thanh toán' };
    case 'PARTIALLY_PAID':
      return { variant: 'info', text: 'Thanh toán một phần' };
    case 'UNPAID':
      return { variant: 'warning', text: 'Chưa thanh toán' };
    case 'OVERDUE':
      return { variant: 'danger', text: 'Quá hạn thanh toán' };
    case 'FAILED':
      return { variant: 'danger', text: 'Thất bại' };
    case 'REFUND_PENDING':
      return { variant: 'warning', text: 'Chờ hoàn tiền' };
    case 'REFUNDED':
      return { variant: 'default', text: 'Đã hoàn tiền' };
    default:
      return { variant: 'default', text: status || '-' };
  }
};

export const getReturnStatusBadge = (status) => {
  switch (status) {
    case 'REQUESTED':
      return { variant: 'warning', text: 'Chờ xét duyệt' };
    case 'APPROVED':
      return { variant: 'info', text: 'Đã chấp thuận' };
    case 'RECEIVED':
      return { variant: 'info', text: 'Đã nhận hàng trả' };
    case 'REFUNDED':
      return { variant: 'success', text: 'Đã hoàn tiền' };
    case 'REJECTED':
      return { variant: 'danger', text: 'Từ chối' };
    default:
      return { variant: 'default', text: status || 'Chưa xác định' };
  }
};

export const getOutreachStatusBadge = (status) => {
  switch (status) {
    case 'OPEN':
      return { variant: 'warning', text: 'Chưa liên hệ' };
    case 'CONTACTED':
      return { variant: 'info', text: 'Đã liên hệ' };
    case 'RESOLVED':
      return { variant: 'success', text: 'Đã xử lý xong' };
    default:
      return { variant: 'default', text: status || 'Chưa xử lý' };
  }
};

export const getUserStatusBadge = (status) => {
  switch (status) {
    case 'ACTIVE':
      return { variant: 'success', text: 'Hoạt động' };
    case 'INACTIVE':
      return { variant: 'default', text: 'Tạm khóa' };
    case 'BLOCKED':
      return { variant: 'danger', text: 'Bị cấm' };
    default:
      return { variant: 'default', text: status || '-' };
  }
};

export const getReviewStatusBadge = (status) => {
  switch (status) {
    case 'APPROVED':
      return { variant: 'success', text: 'Đã duyệt' };
    case 'PENDING':
      return { variant: 'warning', text: 'Chờ duyệt' };
    case 'REJECTED':
      return { variant: 'danger', text: 'Từ chối' };
    case 'HIDDEN':
      return { variant: 'default', text: 'Đã ẩn' };
    default:
      return { variant: 'default', text: status || '-' };
  }
};

export const resolveMediaUrl = (url) => {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:') || url.startsWith('blob:')) {
    return url;
  }
  const apiBase = serverBaseUrl(import.meta.env.VITE_API_BASE_URL);
  if (url.startsWith('/')) {
    return apiBase ? `${apiBase.replace(/\/$/, '')}${url}` : url;
  }
  return apiBase ? `${apiBase.replace(/\/$/, '')}/${url}` : `/${url}`;
};

export const cleanDisplayName = (name) => {
  if (!name || typeof name !== 'string') return '';
  return name.replace(/\s*[\(\[（].*?[\)\]）]\s*/g, ' ').replace(/\s+/g, ' ').trim();
};

export const getVietnamDateString = (date = new Date()) => {
  try {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(date instanceof Date ? date : new Date(date));
  } catch {
    const d = date instanceof Date ? date : new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
};

export const buildGoogleCalendarUrl = ({ title, date, startTime, durationMinutes = 60, details = '', location = '123 Đồng Khởi, Quận 1, TP.HCM' }) => {
  if (!date || !startTime) return '';
  try {
    const timeClean = startTime.length === 5 ? `${startTime}:00` : startTime;
    const start = new Date(`${date}T${timeClean}+07:00`);
    if (isNaN(start.getTime())) return '';
    const end = new Date(start.getTime() + durationMinutes * 60000);
    const formatIsoUtc = (d) => d.toISOString().replace(/-|:|\.\d\d\d/g, '');
    const dates = `${formatIsoUtc(start)}/${formatIsoUtc(end)}`;
    return `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${encodeURIComponent(title || 'Lịch hẹn BeautyShop Spa')}&dates=${dates}&details=${encodeURIComponent(details)}&location=${encodeURIComponent(location)}`;
  } catch {
    return '';
  }
};

