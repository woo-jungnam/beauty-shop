package com.core.beautyshop.shared.config;

import com.core.beautyshop.shared.security.StaffPermissionInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.*;

@Component
public class ApiDocumentationCustomizer implements GlobalOperationCustomizer, GlobalOpenApiCustomizer {

    private static final Map<String, String> TAG_DESCRIPTIONS = Map.ofEntries(
            // KHÁCH HÀNG (USER / PUBLIC)
            Map.entry("1. Khách hàng · Xác thực & Đăng nhập (Auth)", "Đăng ký, đăng nhập JWT, cấp mới token và đăng xuất tài khoản."),
            Map.entry("1. Khách hàng · Khôi phục tài khoản & Bản tin", "Quên mật khẩu, đặt lại mật khẩu và đăng ký nhận bản tin khuyến mãi."),
            Map.entry("1. Khách hàng · Hồ sơ tài khoản & Sổ địa chỉ", "Quản lý thông tin cá nhân, đổi mật khẩu và sổ địa chỉ nhận hàng."),
            Map.entry("1. Khách hàng · Hồ sơ làn da (Skin Profile)", "Khảo sát da, phân loại loại da, độ nhạy cảm và các dị ứng mỹ phẩm đã biết."),
            Map.entry("1. Khách hàng · Danh sách yêu thích (Wishlist)", "Thêm, xóa và tra cứu các sản phẩm mỹ phẩm đã lưu vào danh sách yêu thích."),
            Map.entry("1. Khách hàng · Thông báo cá nhân", "Tra cứu danh sách thông báo đơn hàng, lịch spa và đánh dấu đã đọc."),
            Map.entry("1. Khách hàng · Tra cứu & Chi tiết sản phẩm", "Tra cứu danh mục sản phẩm, biến thể SKU, bảng giá và lọc nâng cao."),
            Map.entry("1. Khách hàng · Danh mục sản phẩm", "Cây phân cấp danh mục mỹ phẩm & chăm sóc da công khai."),
            Map.entry("1. Khách hàng · Thương hiệu sản phẩm", "Danh sách thương hiệu mỹ phẩm chính hãng đối tác."),
            Map.entry("1. Khách hàng · Thuộc tính & Thẻ sản phẩm", "Tra cứu các thuộc tính dung tích, xuất xứ và tag phân loại sản phẩm."),
            Map.entry("1. Khách hàng · Tìm kiếm sản phẩm bằng hình ảnh AI", "Tìm kiếm sản phẩm trực quan bằng hình ảnh qua mô hình AI/CLIP."),
            Map.entry("1. Khách hàng · Banner quảng cáo công khai", "Danh sách banner chiến dịch quảng cáo trên trang chủ và ứng dụng."),
            Map.entry("1. Khách hàng · Giỏ hàng mua sắm", "Thêm sản phẩm theo SKU, cập nhật số lượng, xóa và gộp giỏ hàng khách vãng lai."),
            Map.entry("1. Khách hàng · Đặt hàng & Đơn hàng của tôi", "Đặt hàng (checkout), tra cứu đơn hàng của tôi, theo dõi vận đơn và hủy đơn chờ."),
            Map.entry("1. Khách hàng · Yêu cầu đổi trả sản phẩm", "Gửi yêu cầu đổi trả hàng kèm lý do, chứng từ và theo dõi tiến độ duyệt hoàn tiền."),
            Map.entry("1. Khách hàng · Cổng thanh toán & QR Pay", "Khởi tạo giao dịch thanh toán VietQR Pay, kiểm tra trạng thái thanh toán."),
            Map.entry("1. Khách hàng · Theo dõi thanh toán thời gian thực", "Cấp token đăng ký nhận thông báo trạng thái thanh toán thời gian thực qua WebSocket/SSE."),
            Map.entry("1. Khách hàng · Webhook thanh toán SePay", "Tiếp nhận webhook biến động số dư tài khoản ngân hàng từ SePay."),
            Map.entry("1. Khách hàng · Dịch vụ Spa & Bảng giá", "Tra cứu danh mục các dịch vụ Spa, chi tiết từng dịch vụ và bảng giá niêm yết."),
            Map.entry("1. Khách hàng · Lịch hoạt động & Giờ mở cửa Spa", "Tra cứu khung giờ mở cửa, lịch làm việc hàng tuần và ngày nghỉ lễ của Spa."),
            Map.entry("1. Khách hàng · Chính sách đặt lịch Spa", "Chính sách đặt lịch hẹn, thời gian hủy hẹn và quy định đặt cọc."),
            Map.entry("1. Khách hàng · Đặt lịch hẹn Spa", "Đặt lịch hẹn dịch vụ Spa trực tuyến, chọn dịch vụ và chuyên viên yêu thích."),
            Map.entry("1. Khách hàng · Đơn đặt dịch vụ Spa", "Tra cứu các đơn đặt lịch hẹn dịch vụ chưa lập hóa đơn của khách hàng."),
            Map.entry("1. Khách hàng · Đặt cọc & Tài chính lịch hẹn Spa", "Thanh toán cọc giữ chỗ, xem phiếu thu cọc và theo dõi nghĩa vụ tài chính lịch hẹn."),
            Map.entry("1. Khách hàng · Thanh toán hóa đơn buổi Spa", "Xem hóa đơn chi tiết các dịch vụ đã thực hiện sau buổi Spa và thanh toán phần còn lại."),
            Map.entry("1. Khách hàng · Chuẩn bị & Cam kết trước buổi Spa", "Khảo sát sức khỏe, tiền sử dị ứng và xác nhận cam kết trước khi làm liệu trình."),
            Map.entry("1. Khách hàng · Hướng dẫn sau buổi Spa", "Lời dặn dò và hướng dẫn chi tiết chăm sóc da tại nhà của chuyên viên sau buổi làm dịch vụ."),
            Map.entry("1. Khách hàng · Vé & Gói liệu trình Spa", "Mua vé gói liệu trình đa buổi, xem số lượt còn lại và lịch sử sử dụng lượt vé."),
            Map.entry("1. Khách hàng · Lộ trình chăm sóc da chuyên sâu", "Theo dõi tiến trình, hình ảnh da qua từng buổi của lộ trình điều trị da chuyên sâu."),
            Map.entry("1. Khách hàng · Thông báo tiến độ liệu trình", "Nhận thông báo nhắc lịch buổi tiếp theo và tiến độ liệu trình da."),
            Map.entry("1. Khách hàng · Yêu cầu bảo lưu & Chuyển nhượng gói Spa", "Gửi yêu cầu đổi gói, bảo lưu thời hạn hoặc chuyển nhượng gói liệu trình cho người thân."),
            Map.entry("1. Khách hàng · Flash Sale Giờ Vàng", "Danh sách các sản phẩm đang và sắp mở bán trong khung giờ Flash Sale."),
            Map.entry("1. Khách hàng · Khuyến mãi & Mã giảm giá Voucher", "Tra cứu danh sách voucher khả dụng và kiểm tra mức giảm giá cho giỏ hàng."),
            Map.entry("1. Khách hàng · Đánh giá sản phẩm", "Xem nhận xét, đánh giá thực tế và gửi đánh giá sản phẩm từ đơn hàng đã hoàn tất."),
            Map.entry("1. Khách hàng · Trợ lý Chatbot AI", "Trò chuyện trực tuyến nhận tư vấn mỹ phẩm & routine da từ Trợ lý AI (hỗ trợ SSE)."),
            Map.entry("1. Khách hàng · Cấu hình & Chính sách công khai", "Tra cứu chính sách Freeship, hotline hỗ trợ và thông tin công khai của hệ thống."),

            // QUẢN TRỊ & NHÂN VIÊN (ADMIN & STAFF)
            Map.entry("2. Quản trị · Quản lý tài khoản người dùng", "Danh sách người dùng, xem thông tin tài khoản, khóa và mở khóa tài khoản."),
            Map.entry("2. Quản trị · Quản lý vai trò (Roles)", "Quản lý danh mục các vai trò trong hệ thống (ADMIN, STAFF, CUSTOMER)."),
            Map.entry("2. Quản trị · Phân quyền nhân viên (Permissions)", "Quản lý ma trận phân quyền chi tiết cho nhân viên (ORDERS, SPA, INVENTORY...)."),
            Map.entry("2. Quản trị · Kiểm tra quyền truy cập hệ thống", "Thử nghiệm phân quyền và kiểm tra tính toàn vẹn bảo mật hệ thống."),
            Map.entry("2. Quản trị · Quản lý Sản phẩm & Biến thể SKU", "Thêm mới, chỉnh sửa sản phẩm, cấu hình biến thể SKU, giá nhập, giá bán."),
            Map.entry("2. Quản trị · Quản lý danh mục sản phẩm", "Thêm, sửa, xóa cấu trúc cây danh mục sản phẩm mỹ phẩm."),
            Map.entry("2. Quản trị · Quản lý thương hiệu sản phẩm", "Quản lý danh mục thương hiệu, logo thương hiệu và xuất xứ."),
            Map.entry("2. Quản trị · Quản lý thuộc tính & Thẻ sản phẩm", "Tạo và chỉnh sửa các thuộc tính kỹ thuật và thẻ tag sản phẩm."),
            Map.entry("2. Quản trị · Dược mỹ phẩm & Routine da liễu", "Cấu hình phân loại loại da, vấn đề da và routine sáng/tối."),
            Map.entry("2. Quản trị · Quản lý hoạt chất mỹ phẩm (INCI)", "Quản lý thư viện hoạt chất mỹ phẩm INCI, nồng độ an toàn và mapping vào sản phẩm."),
            Map.entry("2. Quản trị · Tải lên tệp Media & Thư viện ảnh", "Tải lên hình ảnh sản phẩm, kiểm tra dung lượng và trả về URL metadata."),
            Map.entry("2. Quản trị · Quản lý Banner quảng cáo", "Quản lý banner chiến dịch quảng cáo, vị trí hiển thị và liên kết trang đích."),
            Map.entry("2. Quản trị · Gợi ý sản phẩm AI & Content-Based", "Cấu hình trọng số và thuật toán gợi ý sản phẩm AI dựa trên thành phần và loại da."),
            Map.entry("2. Quản trị · Quản lý Đơn hàng toàn hệ thống", "Danh sách đơn hàng toàn hệ thống, chuyển trạng thái đơn, cập nhật mã vận đơn."),
            Map.entry("2. Quản trị · Quản lý Đổi trả hàng", "Tiếp nhận, thẩm định và phê duyệt (APPROVED) hoặc từ chối (REJECTED) yêu cầu đổi trả."),
            Map.entry("2. Quản trị · Xác nhận hoàn tiền đơn hàng", "Ghi nhận chứng từ kế toán và xác nhận hoàn tiền cho đơn hàng đã hủy hoặc đổi trả."),
            Map.entry("2. Quản trị · Đối soát & Giao dịch thanh toán", "Báo cáo đối soát dòng tiền thanh toán qua VietQR/SePay, kiểm tra giao dịch thực tế."),
            Map.entry("2. Quản trị · Xử lý sai lệch thanh toán", "Xử lý các giao dịch chuyển tiền sai lệch: thừa tiền, thiếu tiền hoặc sai cú pháp."),
            Map.entry("2. Quản trị · Quản lý kho bãi & Điều chuyển", "Quản lý danh sách kho hàng, xuất nhập kho nội bộ và điều chuyển giữa các kho."),
            Map.entry("2. Quản trị · Sổ cái tồn kho & Cảnh báo", "Sổ cái biến động tồn (Stock Ledger) theo từng giao dịch, cảnh báo tồn thấp và cận date."),
            Map.entry("2. Quản trị · Kiểm tra hàng cách ly (Quarantine)", "Kiểm tra và phân loại hàng đổi trả đang lưu trữ tại khu vực cách ly (Quarantine)."),
            Map.entry("2. Quản trị · Thu hồi lô hàng (Batch Recall)", "Khởi tạo và quản lý chiến dịch thu hồi lô sản phẩm lỗi, truy vết khách hàng đã mua."),
            Map.entry("2. Quản trị · Nhà cung cấp & Đơn mua hàng (PO)", "Quản lý danh mục nhà cung cấp và lập đơn đặt hàng mua nguyên vật liệu/hàng hóa (PO)."),
            Map.entry("2. Quản trị · Nhập kho mua hàng", "Tiếp nhận lô hàng thực tế từ nhà cung cấp về kho và ghi nhận biên bản kiểm đếm."),
            Map.entry("2. Quản trị · Thanh toán công nợ mua hàng", "Thanh toán công nợ theo từng đợt cho nhà cung cấp dựa trên đơn mua hàng (PO)."),
            Map.entry("2. Quản trị · Dịch vụ & Gói liệu trình Spa", "Quản lý danh mục dịch vụ Spa, thiết lập gói liệu trình đa buổi và quyền lợi kèm theo."),
            Map.entry("2. Quản trị · Chuyên viên & Lịch trực Spa", "Hồ sơ kỹ thuật viên/chuyên viên Spa, xếp ca trực và phân công lịch làm việc."),
            Map.entry("2. Quản trị · Tài nguyên phòng & Giường Spa", "Quản lý phòng trị liệu, giường Spa, máy móc chuyên dụng và trạng thái sẵn sàng."),
            Map.entry("2. Quản trị · Định mức tài nguyên dịch vụ Spa", "Thiết lập định mức phòng, giường và loại máy móc cần thiết cho từng dịch vụ Spa."),
            Map.entry("2. Quản trị · Biểu mẫu & Chính sách chuẩn bị Spa", "Quản lý các mẫu khảo sát chuẩn bị trước làm dịch vụ và chính sách cam kết áp dụng."),
            Map.entry("2. Quản trị · Thẻ liệu trình Spa khách hàng", "Tra cứu thẻ liệu trình của khách, gia hạn ngày hết hạn và bù buổi liệu trình."),
            Map.entry("2. Quản trị · Mẫu phác đồ điều trị Spa", "Thiết kế mẫu phác đồ chăm sóc da chuyên sâu, số buổi và quy trình thực hiện mẫu."),
            Map.entry("2. Quản trị · Vận hành lộ trình điều trị Spa", "Bác sĩ/chuyên viên ghi nhận kết quả từng buổi điều trị, ảnh trước/sau và đánh giá da."),
            Map.entry("2. Quản trị · Điều phối & Check-in lịch hẹn Spa", "Tiếp đón khách hàng tại quầy Spa, check-in, phân công kỹ thuật viên thực hiện."),
            Map.entry("2. Quản trị · Đơn đặt dịch vụ Spa", "Tra cứu toàn bộ các đơn đặt lịch hẹn dịch vụ của khách hàng trên hệ thống."),
            Map.entry("2. Quản trị · Vật tư & Tiêu hao mỹ phẩm Spa", "Định mức vật tư tiêu hao (bông tẩy trang, tinh chất, serum) cho từng dịch vụ và duyệt hao hụt."),
            Map.entry("2. Quản trị · Hoàn cọc lịch hẹn Spa", "Duyệt hoàn cọc cho khách hàng theo đúng chính sách tài chính của Spa."),
            Map.entry("2. Quản trị · Quy trình & Chăm sóc Spa (Thao tác chuyên viên)", "Thao tác xác nhận cảnh báo chuẩn bị, miễn trừ tiêu hao và ghi chú chăm sóc sau buổi."),
            Map.entry("2. Quản trị · CRM & Chăm sóc khách hàng", "Hồ sơ 360 độ khách hàng, lịch sử làm đẹp, hạng thành viên và ghi chú nội bộ."),
            Map.entry("2. Quản trị · Nhiệm vụ chăm sóc CRM", "Quản lý danh sách nhiệm vụ chăm sóc khách hàng (nhắc lịch, hỏi thăm sau làm dịch vụ)."),
            Map.entry("2. Quản trị · Chiến dịch Flash Sale", "Thiết lập khung giờ vàng Flash Sale, giá ưu đãi và số lượng hàng giới hạn."),
            Map.entry("2. Quản trị · Mã giảm giá Voucher", "Tạo mã giảm giá, điều kiện áp dụng và số lượng phát hành."),
            Map.entry("2. Quản trị · Kiểm duyệt đánh giá sản phẩm", "Duyệt đánh giá của khách hàng, gửi phản hồi chính thức từ shop hoặc ẩn đánh giá vi phạm."),
            Map.entry("2. Quản trị · Dashboard & Báo cáo điều hành", "Số liệu tổng quan doanh thu, đơn hàng, khách hàng mới và top sản phẩm bán chạy."),
            Map.entry("2. Quản trị · Lợi nhuận & Chi phí vận hành", "Báo cáo lợi nhuận gộp/ròng, quản lý chi phí vận hành (mặt bằng, điện nước, marketing)."),
            Map.entry("2. Quản trị · Báo cáo hiệu suất Spa", "Báo cáo công suất phục vụ của Spa, tỷ lệ hoàn tất buổi hẹn và đánh giá kỹ thuật viên."),
            Map.entry("2. Quản trị · Cảnh báo vận hành hệ thống", "Tổng hợp các chỉ số cảnh báo: đơn trễ hạn, kho sắp hết hàng, lịch spa quá tải, thanh toán lệch."),
            Map.entry("2. Quản trị · Cấu hình hệ thống & Mục tiêu KPI", "Quản lý tham số cấu hình nghiệp vụ và thiết lập chỉ tiêu doanh số KPI tháng."),
            Map.entry("2. Quản trị · Nhật ký kiểm toán hệ thống (Audit Log)", "Truy vết toàn bộ nhật ký thao tác dữ liệu, đăng nhập và bảo mật của Admin & Staff."),
            Map.entry("2. Quản trị · Đồng bộ dữ liệu Chatbot AI", "Kích hoạt đồng bộ hóa dữ liệu danh mục sản phẩm và kiến thức Spa sang AI Chatbot service.")
    );

    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/api/v1/products", "/api/v1/categories", "/api/v1/brands", "/api/v1/attributes",
            "/api/v1/tags", "/api/v1/spa/services", "/api/v1/spa/treatment-templates",
            "/api/v1/banners", "/api/v1/system-configs/public", "/api/v1/reviews"
    );

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "/api/v1/test/public", "/api/v1/chatbot/chat", "/api/v1/chatbot/chat/stream",
            "/api/v1/chatbot/health", "/api/v1/chatbot/openapi.json", "/api/v1/newsletter/subscriptions",
            "/api/v1/spa/business-calendar", "/api/v1/spa/financial-policy"
    );

    public static String resolveStandardTag(String path, Method method, Class<?> type) {
        boolean isAdmin = isManagementMethod(method);

        if (!isAdmin) {
            // USER / KHÁCH HÀNG / PUBLIC
            if (path.startsWith("/api/v1/auth/forgot-password") || path.startsWith("/api/v1/auth/reset-password")
                    || path.startsWith("/api/v1/newsletter")) {
                return "1. Khách hàng · Khôi phục tài khoản & Bản tin";
            }
            if (path.startsWith("/api/v1/auth")) {
                return "1. Khách hàng · Xác thực & Đăng nhập (Auth)";
            }
            if (path.startsWith("/api/v1/users/wishlist")) {
                return "1. Khách hàng · Danh sách yêu thích (Wishlist)";
            }
            if (path.startsWith("/api/v1/users/skin-profile")) {
                return "1. Khách hàng · Hồ sơ làn da (Skin Profile)";
            }
            if (path.startsWith("/api/v1/users/notifications")) {
                return "1. Khách hàng · Thông báo cá nhân";
            }
            if (path.startsWith("/api/v1/users")) {
                return "1. Khách hàng · Hồ sơ tài khoản & Sổ địa chỉ";
            }
            if (path.contains("/image-search")) {
                return "1. Khách hàng · Tìm kiếm sản phẩm bằng hình ảnh AI";
            }
            if (path.startsWith("/api/v1/categories")) {
                return "1. Khách hàng · Danh mục sản phẩm";
            }
            if (path.startsWith("/api/v1/brands")) {
                return "1. Khách hàng · Thương hiệu sản phẩm";
            }
            if (path.startsWith("/api/v1/attributes") || path.startsWith("/api/v1/tags")) {
                return "1. Khách hàng · Thuộc tính & Thẻ sản phẩm";
            }
            if (path.startsWith("/api/v1/banners")) {
                return "1. Khách hàng · Banner quảng cáo công khai";
            }
            if (path.startsWith("/api/v1/products")) {
                return "1. Khách hàng · Tra cứu & Chi tiết sản phẩm";
            }
            if (path.startsWith("/api/v1/cart")) {
                return "1. Khách hàng · Giỏ hàng mua sắm";
            }
            if (path.matches("/api/v1/orders/[^/]+/returns.*")) {
                return "1. Khách hàng · Yêu cầu đổi trả sản phẩm";
            }
            if (path.startsWith("/api/v1/orders")) {
                return "1. Khách hàng · Đặt hàng & Đơn hàng của tôi";
            }
            if (path.startsWith("/api/v1/payment/sepay-webhook")) {
                return "1. Khách hàng · Webhook thanh toán SePay";
            }
            if (path.startsWith("/api/v1/payment-subscription")) {
                return "1. Khách hàng · Theo dõi thanh toán thời gian thực";
            }
            if (path.startsWith("/api/v1/payment")) {
                return "1. Khách hàng · Cổng thanh toán & QR Pay";
            }
            if (path.startsWith("/api/v1/spa/business-calendar")) {
                return "1. Khách hàng · Lịch hoạt động & Giờ mở cửa Spa";
            }
            if (path.startsWith("/api/v1/spa/booking-policy") || path.startsWith("/api/v1/spa/financial-policy")) {
                return "1. Khách hàng · Chính sách đặt lịch Spa";
            }
            if (path.startsWith("/api/v1/spa/services")) {
                return "1. Khách hàng · Dịch vụ Spa & Bảng giá";
            }
            if (path.contains("/tickets") || path.contains("/ticket-movements")) {
                return "1. Khách hàng · Vé & Gói liệu trình Spa";
            }
            if (path.contains("/treatment-courses")) {
                return "1. Khách hàng · Lộ trình chăm sóc da chuyên sâu";
            }
            if (path.contains("/treatment-notifications")) {
                return "1. Khách hàng · Thông báo tiến độ liệu trình";
            }
            if (path.contains("/package-requests")) {
                return "1. Khách hàng · Yêu cầu bảo lưu & Chuyển nhượng gói Spa";
            }
            if (path.contains("/preparation")) {
                return "1. Khách hàng · Chuẩn bị & Cam kết trước buổi Spa";
            }
            if (path.contains("/follow-up-instructions")) {
                return "1. Khách hàng · Hướng dẫn sau buổi Spa";
            }
            if (path.contains("/finance")) {
                return "1. Khách hàng · Đặt cọc & Tài chính lịch hẹn Spa";
            }
            if (path.contains("/invoice")) {
                return "1. Khách hàng · Thanh toán hóa đơn buổi Spa";
            }
            if (path.contains("/my-service-orders")) {
                return "1. Khách hàng · Đơn đặt dịch vụ Spa";
            }
            if (path.startsWith("/api/v1/appointments")) {
                return "1. Khách hàng · Đặt lịch hẹn Spa";
            }
            if (path.startsWith("/api/v1/flash-sales")) {
                return "1. Khách hàng · Flash Sale Giờ Vàng";
            }
            if (path.startsWith("/api/v1/promotions") || path.contains("/vouchers")) {
                return "1. Khách hàng · Khuyến mãi & Mã giảm giá Voucher";
            }
            if (path.startsWith("/api/v1/reviews")) {
                return "1. Khách hàng · Đánh giá sản phẩm";
            }
            if (path.startsWith("/api/v1/chatbot")) {
                return "1. Khách hàng · Trợ lý Chatbot AI";
            }
            if (path.startsWith("/api/v1/system-configs")) {
                return "1. Khách hàng · Cấu hình & Chính sách công khai";
            }
            return "1. Khách hàng · Xác thực & Đăng nhập (Auth)";
        } else {
            // ADMIN / QUẢN TRỊ & NHÂN VIÊN
            if (path.contains("staff-permissions") || path.contains("/permissions")) {
                return "2. Quản trị · Phân quyền nhân viên (Permissions)";
            }
            if (path.startsWith("/api/v1/roles")) {
                return "2. Quản trị · Quản lý vai trò (Roles)";
            }
            if (path.startsWith("/api/v1/admin/users")) {
                return "2. Quản trị · Quản lý tài khoản người dùng";
            }
            if (path.startsWith("/api/v1/test")) {
                return "2. Quản trị · Kiểm tra quyền truy cập hệ thống";
            }
            if (path.contains("/recommendations")) {
                return "2. Quản trị · Gợi ý sản phẩm AI & Content-Based";
            }
            if (path.startsWith("/api/v1/admin/banners")) {
                return "2. Quản trị · Quản lý Banner quảng cáo";
            }
            if (path.startsWith("/api/v1/admin/media")) {
                return "2. Quản trị · Tải lên tệp Media & Thư viện ảnh";
            }
            if (path.startsWith("/api/v1/admin/ingredients")) {
                return "2. Quản trị · Quản lý hoạt chất mỹ phẩm (INCI)";
            }
            if (path.startsWith("/api/v1/admin/dermatology")) {
                return "2. Quản trị · Dược mỹ phẩm & Routine da liễu";
            }
            if (path.startsWith("/api/v1/categories")) {
                return "2. Quản trị · Quản lý danh mục sản phẩm";
            }
            if (path.startsWith("/api/v1/brands")) {
                return "2. Quản trị · Quản lý thương hiệu sản phẩm";
            }
            if (path.startsWith("/api/v1/attributes") || path.startsWith("/api/v1/tags")) {
                return "2. Quản trị · Quản lý thuộc tính & Thẻ sản phẩm";
            }
            if (path.startsWith("/api/v1/admin/products") || path.startsWith("/api/v1/products")) {
                return "2. Quản trị · Quản lý Sản phẩm & Biến thể SKU";
            }
            if (path.startsWith("/api/v1/admin/commerce/returns")) {
                return "2. Quản trị · Quản lý Đổi trả hàng";
            }
            if (path.contains("/refund-confirmation")) {
                return "2. Quản trị · Xác nhận hoàn tiền đơn hàng";
            }
            if (path.startsWith("/api/v1/admin/orders")) {
                return "2. Quản trị · Quản lý Đơn hàng toàn hệ thống";
            }
            if (path.startsWith("/api/v1/admin/payments/discrepancies")) {
                return "2. Quản trị · Xử lý sai lệch thanh toán";
            }
            if (path.startsWith("/api/v1/admin/payments")) {
                return "2. Quản trị · Đối soát & Giao dịch thanh toán";
            }
            if (path.startsWith("/api/v1/admin/warehouses")) {
                return "2. Quản trị · Quản lý kho bãi & Điều chuyển";
            }
            if (path.contains("/inventory/ledger") || path.contains("/inventory/alerts")) {
                return "2. Quản trị · Sổ cái tồn kho & Cảnh báo";
            }
            if (path.contains("/inventory/quarantine")) {
                return "2. Quản trị · Kiểm tra hàng cách ly (Quarantine)";
            }
            if (path.contains("/inventory/recalls")) {
                return "2. Quản trị · Thu hồi lô hàng (Batch Recall)";
            }
            if (path.contains("/procurement/orders") && path.contains("/receipts")) {
                return "2. Quản trị · Nhập kho mua hàng";
            }
            if (path.contains("/procurement/orders") && path.contains("/payments")) {
                return "2. Quản trị · Thanh toán công nợ mua hàng";
            }
            if (path.startsWith("/api/v1/admin/procurement")) {
                return "2. Quản trị · Nhà cung cấp & Đơn mua hàng (PO)";
            }
            if (path.contains("/customer-tickets")) {
                return "2. Quản trị · Thẻ liệu trình Spa khách hàng";
            }
            if (path.contains("/treatment-templates")) {
                return "2. Quản trị · Mẫu phác đồ điều trị Spa";
            }
            if (path.contains("/treatment-executions")) {
                return "2. Quản trị · Vận hành lộ trình điều trị Spa";
            }
            if (path.contains("/preparation-policies")) {
                return "2. Quản trị · Biểu mẫu & Chính sách chuẩn bị Spa";
            }
            if (path.contains("/consumables")) {
                return "2. Quản trị · Vật tư & Tiêu hao mỹ phẩm Spa";
            }
            if (path.contains("/settlements")) {
                return "2. Quản trị · Quyết toán doanh thu & Hoa hồng Spa";
            }
            if (path.contains("/admin/spa/refunds") || path.contains("/refund")) {
                return "2. Quản trị · Hoàn cọc lịch hẹn Spa";
            }
            if (path.startsWith("/api/v1/admin/staff") || path.startsWith("/api/v1/staff/schedules")) {
                return "2. Quản trị · Chuyên viên & Lịch trực Spa";
            }
            if (path.contains("/facilities") || path.contains("/admin/spa/resources")) {
                return "2. Quản trị · Tài nguyên phòng & Giường Spa";
            }
            if (path.contains("/service-resource-requirements") || (path.contains("/admin/spa/services") && path.contains("/resources"))) {
                return "2. Quản trị · Định mức tài nguyên dịch vụ Spa";
            }
            if (path.contains("/service-orders")) {
                return "2. Quản trị · Đơn đặt dịch vụ Spa";
            }
            if (path.startsWith("/api/v1/admin/spa/services") || path.startsWith("/api/v1/admin/spa/packages")) {
                return "2. Quản trị · Dịch vụ & Gói liệu trình Spa";
            }
            if (path.startsWith("/api/v1/appointments") || path.contains("/check-in") || path.contains("/assign")) {
                return "2. Quản trị · Điều phối & Check-in lịch hẹn Spa";
            }
            if (path.contains("/preparation/") || path.contains("/follow-up-instructions/") || path.contains("/care-summary")) {
                return "2. Quản trị · Quy trình & Chăm sóc Spa (Thao tác chuyên viên)";
            }
            if (path.contains("/crm/work-items") || path.contains("/crm/tasks")) {
                return "2. Quản trị · Nhiệm vụ chăm sóc CRM";
            }
            if (path.startsWith("/api/v1/admin/crm")) {
                return "2. Quản trị · CRM & Chăm sóc khách hàng";
            }
            if (path.startsWith("/api/v1/admin/flash-sales")) {
                return "2. Quản trị · Chiến dịch Flash Sale";
            }
            if (path.startsWith("/api/v1/admin/vouchers")) {
                return "2. Quản trị · Mã giảm giá Voucher";
            }
            if (path.startsWith("/api/v1/admin/reviews")) {
                return "2. Quản trị · Kiểm duyệt đánh giá sản phẩm";
            }
            if (path.contains("/dashboard/profitability")) {
                return "2. Quản trị · Lợi nhuận & Chi phí vận hành";
            }
            if (path.contains("/dashboard/spa-performance") || path.contains("/dashboard/spa-financials")) {
                return "2. Quản trị · Báo cáo hiệu suất Spa";
            }
            if (path.startsWith("/api/v1/admin/dashboard")) {
                return "2. Quản trị · Dashboard & Báo cáo điều hành";
            }
            if (path.startsWith("/api/v1/admin/operational-alerts")) {
                return "2. Quản trị · Cảnh báo vận hành hệ thống";
            }
            if (path.startsWith("/api/v1/admin/system-configs")) {
                return "2. Quản trị · Cấu hình hệ thống & Mục tiêu KPI";
            }
            if (path.startsWith("/api/v1/admin/audit-logs")) {
                return "2. Quản trị · Nhật ký kiểm toán hệ thống (Audit Log)";
            }
            if (path.startsWith("/api/v1/chatbot/sync")) {
                return "2. Quản trị · Đồng bộ dữ liệu Chatbot AI";
            }
            return "2. Quản trị · Cấu hình hệ thống & Mục tiêu KPI";
        }
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        String path = path(handler.getBeanType(), handler.getMethod());
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
        boolean get = mapping != null && Arrays.asList(mapping.method()).contains(RequestMethod.GET);
        PreAuthorize authorization = authorization(handler.getMethod(), handler.getBeanType());
        String mode;
        if (path.equals("/api/v1/payment/sepay-webhook")) {
            mode = "sepay-key";
            operation.setSecurity(List.of(new SecurityRequirement().addList("sepayApiKey")));
        } else if (authorization != null) {
            mode = "bearer";
            operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
            operation.addExtension("x-method-authorization", authorization.value());
        } else if (path.startsWith("/api/v1/cart") || path.equals("/api/v1/orders/checkout") || (get && path.matches("/api/v1/orders/[^/]+(?:/payment-subscription)?")) || path.matches("/api/v1/orders/[^/]+/returns")) {
            mode = "optional-bearer";
            operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList("bearerAuth")));
        } else if (path.startsWith("/api/v1/auth/") || PUBLIC_ROUTES.contains(path) || (get && PUBLIC_GET_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/")))) {
            mode = "public";
            operation.setSecurity(List.of());
        } else {
            mode = "bearer";
            operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
        }
        operation.addExtension("x-access-mode", mode);
        operation.addExtension("x-module", module(handler.getBeanType()));
        String verb = mapping == null || mapping.method().length == 0 ? "GET" : mapping.method()[0].name();
        String permission = StaffPermissionInterceptor.requiredPermission(path, verb);
        if (permission != null) operation.addExtension("x-staff-permission", permission);
        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            String action = switch (verb) {
                case "GET" -> "Tra cứu";
                case "DELETE" -> "Xóa";
                case "PUT", "PATCH" -> "Cập nhật";
                default -> "Thực hiện";
            };
            operation.setSummary(action + " " + path);
        }

        // Apply standardized, specific tag
        String standardTag = resolveStandardTag(path, handler.getMethod(), handler.getBeanType());
        operation.setTags(List.of(standardTag));

        if (get && path.equals("/api/v1/products/search") && operation.getParameters() != null) {
            operation.getParameters().stream().filter(parameter -> Set.of("page", "size").contains(parameter.getName()))
                    .forEach(parameter -> parameter.setRequired(false));
        }
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(parameter -> {
                if ("page".equals(parameter.getName()) && (parameter.getDescription() == null || parameter.getDescription().contains("Zero-based"))) {
                    parameter.setDescription("Số thứ tự trang, bắt đầu từ 0 (0..N)");
                } else if ("size".equals(parameter.getName()) && (parameter.getDescription() == null || parameter.getDescription().contains("size of the page"))) {
                    parameter.setDescription("Số lượng bản ghi trên mỗi trang");
                } else if ("sort".equals(parameter.getName()) && (parameter.getDescription() == null || parameter.getDescription().contains("Sorting criteria"))) {
                    parameter.setDescription("Tiêu chí sắp xếp: tên_trường,(asc|desc)");
                }
            });
        }
        if (operation.getResponses() != null) {
            addError(operation, "400", "JSON, tham số hoặc dữ liệu không hợp lệ; quy tắc nghiệp vụ bị từ chối.");
            addError(operation, "401", mode.equals("sepay-key") ? "Thiếu hoặc sai API key SePay." : "Thiếu, sai hoặc hết hạn thông tin xác thực khi API yêu cầu.");
            if (!mode.equals("public") && !mode.equals("sepay-key")) addError(operation, "403", "Không đủ vai trò, quyền sở hữu hoặc phạm vi được phân công.");
            addError(operation, "404", "Không tìm thấy tài nguyên hoặc đường dẫn.");
            addError(operation, "409", "Dữ liệu trùng, xung đột đồng thời hoặc trạng thái nghiệp vụ không cho phép.");
            addError(operation, "415", "Content-Type không được hỗ trợ.");
            addError(operation, "500", "Lỗi nội bộ; phản hồi không chứa chi tiết kỹ thuật nhạy cảm.");
        }
        return operation;
    }

    private void addError(Operation operation, String status, String description) {
        ApiResponse response = operation.getResponses().get(status);
        if (response == null) response = new ApiResponse().description(description);
        else if (response.getDescription() == null || response.getDescription().equals("Bad Request") || response.getDescription().equals("Internal Server Error")) response.setDescription(description);
        response.setContent(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
        operation.getResponses().addApiResponse(status, response);
    }

    @Override
    public void customise(OpenAPI api) {
        if (api.getComponents() != null) api.getComponents().addSchemas("ApiErrorResponse", new ObjectSchema()
                .description("Phản hồi lỗi chung. Các trường null được bỏ qua; errorCode có thể vắng ở lỗi từ filter/webhook.")
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("errorCode", new StringSchema().example("SYS_002"))
                .addProperty("message", new StringSchema().example("Thiếu header bắt buộc: Idempotency-Key"))
                .addProperty("errors", new Schema<>().description("Chi tiết kiểm thực nếu có; thường là mảng field/message/rejectedValue, không chứa giá trị bị từ chối."))
                .addProperty("timestamp", new StringSchema().description("LocalDateTime của máy chủ, không có offset/Z; không phải RFC3339 Instant.").example("2026-10-02T17:00:00"))
                .addProperty("path", new StringSchema().example("/api/v1/appointments/1/invoice"))
                .required(List.of("status", "message", "timestamp")));
        if (api.getPaths() == null) return;

        Map<String, Tag> registered = new HashMap<>();
        TAG_DESCRIPTIONS.forEach((tagName, desc) -> {
            registered.put(tagName, new Tag().name(tagName).description(desc));
        });
        if (api.getTags() != null) {
            api.getTags().forEach(tag -> registered.putIfAbsent(tag.getName(), tag));
        }

        Set<String> usedTags = new HashSet<>();
        api.getPaths().values().forEach(item -> item.readOperations().forEach(op -> {
            if (op.getTags() != null) usedTags.addAll(op.getTags());
        }));

        api.setTags(usedTags.stream()
                .sorted(Comparator.naturalOrder())
                .map(name -> registered.getOrDefault(name, new Tag().name(name))).toList());

        var ordered = new io.swagger.v3.oas.models.Paths();
        api.getPaths().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var item = new io.swagger.v3.oas.models.PathItem();
            BeanUtils.copyProperties(entry.getValue(), item);
            entry.getValue().readOperationsMap().forEach((method, operation) -> {
                Operation distinct = new Operation();
                BeanUtils.copyProperties(operation, distinct);
                String route = entry.getKey().replaceAll("\\{([^}]+)\\}", "by_$1")
                        .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
                distinct.setOperationId(method.name().toLowerCase(Locale.ROOT) + "_" + route);
                item.operation(method, distinct);
            });
            ordered.addPathItem(entry.getKey(), item);
        });
        api.setPaths(ordered);
    }

    public static boolean isManagementMethod(Method method) {
        PreAuthorize authorization = authorization(method, method.getDeclaringClass());
        String path = path(method.getDeclaringClass(), method);
        boolean staffServiceAction = path.matches("/api/v1/spa/appointments/[^/]+/(care-summary|preparation/items/[^/]+/(acknowledge-warnings|override)|follow-up-instructions/items/[^/]+)");
        return path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/staff/") || staffServiceAction
                || (authorization != null && authorization.value().contains("ADMIN"));
    }

    private static PreAuthorize authorization(Method method, Class<?> type) {
        PreAuthorize methodRule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        return methodRule == null ? AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class) : methodRule;
    }

    private static String path(Class<?> type, Method method) {
        return firstPath(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class)) + firstPath(AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class));
    }

    private static String firstPath(RequestMapping mapping) {
        return mapping == null || mapping.path().length == 0 ? "" : mapping.path()[0];
    }

    private static String module(Class<?> type) {
        String[] segments = type.getPackageName().split("\\.");
        for (int index = 0; index + 1 < segments.length; index++) if (segments[index].equals("modules")) return segments[index + 1];
        return "system";
    }
}
