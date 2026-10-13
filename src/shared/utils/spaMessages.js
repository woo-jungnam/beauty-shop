export const spaErrorMessage = (error, fallback = 'Thao tác chưa hoàn tất. Vui lòng thử lại.') => {
  const status = error?.status || error?.response?.status;
  if (status === 401) return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
  if (status === 403) return 'Bạn chưa có quyền thực hiện thao tác này.';
  const message = error?.response?.data?.message || error?.message || '';
  if (/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(message)
      && !/Exception|SQL|stack trace|HTTP \d|java\./i.test(message)) return message;
  if (status === 409) return 'Thông tin vừa thay đổi hoặc ca đã được xếp. Vui lòng làm mới và kiểm tra lại.';
  return fallback;
};
