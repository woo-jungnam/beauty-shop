const actions = {
  HTTP_POST: 'Tạo hoặc thực hiện thao tác',
  HTTP_PUT: 'Cập nhật',
  HTTP_PATCH: 'Cập nhật một phần',
  HTTP_DELETE: 'Xóa',
  AUTH_LOGIN: 'Đăng nhập',
  AUTH_GOOGLE: 'Đăng nhập bằng Google',
  AUTH_LOGOUT: 'Đăng xuất',
  AUTH_REGISTER: 'Đăng ký',
  AUTH_REFRESH: 'Gia hạn phiên đăng nhập',
  ACKNOWLEDGE_CARE_WARNINGS: 'Xác nhận cảnh báo chăm sóc',
  ADD_IN_SESSION_ITEM: 'Thêm dịch vụ trong buổi hẹn',
  CANCEL_APPOINTMENT: 'Hủy lịch hẹn',
  COLLECT_SPA_VISIT_CASH: 'Thu tiền mặt tại quầy',
  CONFIRM_REFUND: 'Xác nhận hoàn tiền',
  CREATE_SPA_VISIT_INVOICE: 'Lập hóa đơn buổi chăm sóc',
  INSPECT_RETURN: 'Kiểm tra hàng trả',
  OVERRIDE_PREPARATION: 'Cho phép tiếp tục khi chưa đủ bước chuẩn bị',
  RATE_APPOINTMENT: 'Đánh giá buổi hẹn',
  RESCHEDULE_APPOINTMENT: 'Đổi lịch hẹn',
  SUBMIT_CUSTOMER_CONSENT: 'Ghi nhận đồng ý của khách',
  UPDATE_APPOINTMENT_STATUS: 'Cập nhật trạng thái lịch hẹn',
  WITHDRAW_CUSTOMER_CONSENT: 'Rút lại đồng ý của khách',
  WRITE_SPA_FOLLOW_UP: 'Ghi nhận chăm sóc sau buổi hẹn',
  PURCHASE_PAID: 'Đã thanh toán liệu trình',
  SETTLEMENT_PAID: 'Đã thanh toán chênh lệch',
  ASSIGNED: 'Phân công người phụ trách',
  INTAKE_ACCEPTED: 'Tiếp nhận liệu trình',
  CHANGE_PROPOSED: 'Đề xuất điều chỉnh',
  CHANGE_APPROVED: 'Duyệt điều chỉnh',
  CHANGE_REJECTED: 'Từ chối điều chỉnh',
  CUSTOMER_ACCEPTED_CHANGE: 'Khách đồng ý điều chỉnh',
  CUSTOMER_REJECTED_CHANGE: 'Khách từ chối điều chỉnh',
  REQUEST_CREATED: 'Tạo yêu cầu',
  REQUEST_APPROVED: 'Duyệt yêu cầu',
  REQUEST_REJECTED: 'Từ chối yêu cầu',
  REFUND_CONFIRMED: 'Xác nhận hoàn tiền',
  SESSION_RESULT: 'Ghi nhận kết quả buổi điều trị',
  SESSION_WAIVED: 'Miễn buổi điều trị',
  COURSE_COMPLETED: 'Hoàn tất liệu trình',
  CHANGE_APPLIED: 'Áp dụng điều chỉnh',
  SETTLEMENT_REFUND_PENDING: 'Chờ hoàn tiền chênh lệch',
};

const resources = {
  APPOINTMENT: 'Lịch hẹn',
  ORDER: 'Đơn hàng',
  ORDERS: 'Đơn hàng',
  WAREHOUSE_STOCK: 'Tồn kho',
  INVENTORY: 'Tồn kho',
  PROCUREMENT: 'Nhập hàng',
  PRODUCTS: 'Sản phẩm',
  CATEGORIES: 'Danh mục',
  BRANDS: 'Thương hiệu',
  ATTRIBUTES: 'Thuộc tính',
  TAGS: 'Thẻ phân loại',
  BANNERS: 'Ảnh quảng cáo',
  VOUCHERS: 'Mã giảm giá',
  'FLASH-SALES': 'Giảm giá theo khung giờ',
  PAYMENTS: 'Thanh toán',
  REVIEWS: 'Đánh giá',
  USERS: 'Tài khoản',
  ROLES: 'Vai trò',
  AUTH: 'Xác thực',
  CRM: 'Hồ sơ khách hàng',
  SPA: 'Vận hành Spa',
  'TREATMENT-COURSES': 'Liệu trình',
  'SYSTEM-CONFIGS': 'Cấu hình hệ thống',
  'IMAGE-SEARCH': 'Tìm kiếm bằng ảnh',
  RECOMMENDATIONS: 'Gợi ý sản phẩm',
  CHATBOT: 'Trợ lý tư vấn',
  SYSTEM: 'Hệ thống',
};

export const auditActionLabel = (code) =>
  Object.hasOwn(actions, code) ? actions[code] : 'Thao tác chưa có nhãn';

export const auditResourceLabel = (code) =>
  Object.hasOwn(resources, code) ? resources[code] : 'Đối tượng chưa có nhãn';

export const treatmentAuditDetails = (details) => details
  ?.replace(/^Purchased published plan version (\d+)$/, 'Đã mua phiên bản liệu trình #$1')
  .replace(/^Adjustment paid; resolve future bookings or validity before applying revision$/, 'Đã thanh toán chênh lệch. Kiểm tra lịch hẹn sắp tới hoặc thời hạn trước khi áp dụng điều chỉnh.')
  .replace(/^Staff assignment(?=; staffId=)/, 'Phân công nhân viên')
  .replace(/; staffId=(\d+)$/, ' · Nhân viên #$1')
  .replace(/^Responsible staff accepted the purchased plan$/, 'Nhân viên phụ trách đã tiếp nhận liệu trình đã mua.')
  .replace(/^Change (\d+)/, 'Điều chỉnh #$1')
  .replace(/^Refund reference (.+); amount=(.+)$/, 'Mã hoàn tiền $1 · Số tiền $2')
  .replace(/^Result recorded for session (\d+)$/, 'Đã ghi nhận kết quả buổi điều trị #$1')
  .replace(/^Responsible staff completed the treatment course$/, 'Nhân viên phụ trách đã hoàn tất liệu trình.')
  .replace(/^Accepted plan revision (\d+)$/, 'Đã áp dụng phiên bản điều chỉnh #$1')
  .replace(/^Unapplied paid adjustment requires refund$/, 'Cần hoàn tiền cho phần điều chỉnh đã thanh toán nhưng chưa áp dụng.');
