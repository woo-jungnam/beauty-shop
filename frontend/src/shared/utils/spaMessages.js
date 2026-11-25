export const spaErrorMessage = (error, fallback = 'Thao tác chưa hoàn tất. Vui lòng thử lại.') => {
  const status = error?.status || error?.response?.status;
  if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
  if (status === 403) return 'Bạn chưa có quyền thực hiện thao tác này.';
  const message = error?.response?.data?.message || error?.message || '';

  if (typeof message === 'string' && message.trim()) {
    const raw = message.trim();
    if (raw.includes('Intake must be accepted') || raw.includes('chưa được Bác sĩ tiếp nhận')) {
      return 'Hồ sơ liệu trình chưa sẵn sàng để bắt đầu. Vui lòng làm mới lịch và kiểm tra trạng thái gói.';
    }
    if (raw.includes('Complete the previous') || raw.includes('hoàn tất buổi điều trị trước')) {
      return 'Cần hoàn tất buổi điều trị trước đó trước khi bắt đầu buổi này.';
    }
    if (raw.includes('permitted interval') || raw.includes('allowed interval') || raw.includes('khoảng thời gian quy định')) {
      return 'Lịch hẹn chưa phù hợp với khung giờ hoạt động hoặc thời hạn của gói.';
    }
    if (raw.includes('Cancel future treatment bookings') || raw.includes('hủy các lịch hẹn điều trị tương lai')) {
      return 'Vui lòng hủy các lịch hẹn tương lai trước khi áp dụng phác đồ mới.';
    }
    if (raw.includes('outside your role')) {
      return 'Bạn không có quyền thực hiện thao tác này trên lịch hẹn.';
    }
    if (!/Exception|SQL|stack trace|HTTP \d|java\.|org\.hibernate/i.test(raw)) {
      if (/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(raw)) {
        return raw;
      }

    }
  }

  if (status === 409) return 'Thông tin vừa thay đổi hoặc ca đã được xếp. Vui lòng làm mới và kiểm tra lại.';
  return fallback;
};
