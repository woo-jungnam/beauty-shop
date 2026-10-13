package com.core.beautyshop.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SYSTEM_ERROR("SYS_001", HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống không mong muốn"),
    INVALID_PARAMETER("SYS_002", HttpStatus.BAD_REQUEST, "Tham số truyền vào không hợp lệ"),
    VALIDATION_FAILED("SYS_003", HttpStatus.BAD_REQUEST, "Dữ liệu xác thực không hợp lệ"),
    RESOURCE_NOT_FOUND("SYS_004", HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu"),
    METHOD_NOT_ALLOWED("SYS_005", HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),
    UNSUPPORTED_MEDIA_TYPE("SYS_006", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng dữ liệu không được hỗ trợ"),
    DATA_INTEGRITY_VIOLATION("SYS_007", HttpStatus.CONFLICT, "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn cơ sở dữ liệu"),
    BUSINESS_RULE_VIOLATION("SYS_008", HttpStatus.BAD_REQUEST, "Vi phạm quy tắc nghiệp vụ"),

    UNAUTHORIZED("AUTH_001", HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn"),
    ACCESS_DENIED("AUTH_002", HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    INVALID_CREDENTIALS("AUTH_003", HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác"),
    TOKEN_EXPIRED("AUTH_004", HttpStatus.UNAUTHORIZED, "Token đã hết hạn, vui lòng làm mới token hoặc đăng nhập lại"),
    INVALID_REFRESH_TOKEN("AUTH_005", HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã bị thu hồi"),
    USER_ALREADY_EXISTS("AUTH_006", HttpStatus.CONFLICT, "Email hoặc số điện thoại đã được đăng ký trên hệ thống"),
    USER_NOT_FOUND("AUTH_007", HttpStatus.NOT_FOUND, "Không tìm thấy thông tin người dùng"),

    PRODUCT_NOT_FOUND("CAT_001", HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"),
    PRODUCT_SLUG_EXISTS("CAT_002", HttpStatus.CONFLICT, "Slug sản phẩm đã tồn tại"),
    CATEGORY_NOT_FOUND("CAT_003", HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"),
    BRAND_NOT_FOUND("CAT_004", HttpStatus.NOT_FOUND, "Không tìm thấy thương hiệu"),
    BRAND_SLUG_EXISTS("CAT_005", HttpStatus.CONFLICT, "Slug thương hiệu đã tồn tại"),
    PRODUCT_VARIANT_NOT_FOUND("CAT_006", HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm"),

    INSUFFICIENT_STOCK("INV_001", HttpStatus.BAD_REQUEST, "Số lượng sản phẩm trong kho không đủ để đáp ứng"),
    WAREHOUSE_NOT_FOUND("INV_002", HttpStatus.NOT_FOUND, "Không tìm thấy kho hàng"),

    CART_EMPTY("ORD_001", HttpStatus.BAD_REQUEST, "Giỏ hàng hiện đang trống"),
    ORDER_NOT_FOUND("ORD_002", HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"),
    ORDER_CANNOT_CANCEL("ORD_003", HttpStatus.BAD_REQUEST, "Đơn hàng đang ở trạng thái không thể hủy"),
    INVALID_ORDER_STATUS("ORD_004", HttpStatus.BAD_REQUEST, "Chuyển trạng thái đơn hàng không hợp lệ"),

    PAYMENT_FAILED("PAY_001", HttpStatus.BAD_REQUEST, "Giao dịch thanh toán thất bại"),
    PAYMENT_METHOD_NOT_SUPPORTED("PAY_002", HttpStatus.BAD_REQUEST, "Phương thức thanh toán không được hỗ trợ"),
    PAYMENT_ALREADY_COMPLETED("PAY_003", HttpStatus.CONFLICT, "Đơn hàng này đã được thanh toán trước đó"),

    SPA_SERVICE_NOT_FOUND("SPA_001", HttpStatus.NOT_FOUND, "Không tìm thấy dịch vụ spa"),
    APPOINTMENT_NOT_FOUND("SPA_002", HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn"),
    APPOINTMENT_SLOT_UNAVAILABLE("SPA_003", HttpStatus.CONFLICT, "Khung giờ hẹn đã có khách đặt hoặc kỹ thuật viên bận"),

    CHATBOT_SERVICE_UNAVAILABLE("AI_001", HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI Chatbot tạm thời không khả dụng, vui lòng thử lại sau");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
