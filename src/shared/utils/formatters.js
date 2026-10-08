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
    case 'SHIPPED':
      return { variant: 'default', text: 'Đang giao hàng' };
    case 'DELIVERED':
      return { variant: 'success', text: 'Đã giao thành công' };
    case 'CANCELLED':
      return { variant: 'danger', text: 'Đã hủy' };
    case 'RETURNED':
      return { variant: 'danger', text: 'Đã trả hàng' };
    default:
      return { variant: 'default', text: status || 'Chưa xác định' };
  }
};

export const getPaymentStatusBadge = (status) => {
  switch (status) {
    case 'PAID':
      return { variant: 'success', text: 'Đã thanh toán' };
    case 'PENDING':
      return { variant: 'warning', text: 'Chờ thanh toán' };
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

