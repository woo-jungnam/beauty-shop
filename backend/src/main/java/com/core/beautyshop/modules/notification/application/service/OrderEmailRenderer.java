package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.order.api.OrderFacade.OrderEmailDetailDto;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.Locale;

/** Table layout and inline styles for email clients; all customer content is escaped. */
final class OrderEmailRenderer {
    private OrderEmailRenderer() {}

    static String render(EmailMessageDto message, OrderEmailDetailDto order, String supportEmail,
                         String supportPhone, String mediaBaseUrl) {
        String title = message.getSubject() == null ? "Thông báo đơn hàng" : message.getSubject();
        String body = message.getParameters() == null ? "" : String.valueOf(message.getParameters().getOrDefault("body", ""));
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>@media screen and (max-width:600px){.content{padding-left:16px!important;padding-right:16px!important}.line td{padding:10px 4px!important;font-size:12px!important}.product-image{display:none!important}}</style>
                </head><body style="margin:0;background-color:#f5f1f3;font-family:Arial,sans-serif;color:#352d30;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" bgcolor="#f5f1f3"><tr><td align="center" style="padding:24px 8px;">
                <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%;max-width:600px;background-color:#ffffff;border-radius:12px;">
                <tr><td bgcolor="#8c2a47" style="background-color:#8c2a47;background-image:linear-gradient(135deg,#8c2a47,#d81b60);padding:28px 20px;text-align:center;color:#ffffff;">
                <div style="font-size:26px;font-weight:bold;">BeautyShop</div>
                <p style="margin:8px 0 0;font-size:13px;">Mỹ phẩm &amp; Spa chăm sóc sắc đẹp</p></td></tr>
                <tr><td class="content" style="padding:24px 32px;">
                """);
        html.append("<h1 style=\"font-size:21px;color:#8c2a47;margin:0 0 16px;\">").append(esc(title)).append("</h1>");
        html.append("<p>Xin chào <strong>").append(esc(blank(message.getRecipientName()) ? "Quý khách" : message.getRecipientName())).append("</strong>,</p>");
        html.append("<p style=\"line-height:1.6;\">").append(esc(body).replace("\n", "<br>")).append("</p>");
        if (order == null) {
            html.append("<p>Chi tiết đơn hàng hiện chưa có trong email này. Vui lòng tra cứu đơn hàng trên website BeautyShop.</p>");
        } else {
            html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" style=\"background-color:#fdf2f6;padding:12px;border-radius:8px;\">");
            info(html, "Mã đơn hàng", order.orderNumber());
            info(html, "Ngày đặt (giờ Việt Nam)", date(order.createdAt()));
            info(html, "Trạng thái đơn hàng", status(order.status()));
            info(html, "Trạng thái thanh toán", paymentStatus(order.paymentStatus()));
            info(html, "Phương thức thanh toán", paymentMethod(order.paymentMethod()));
            info(html, "Thời điểm thanh toán (giờ Việt Nam)", date(order.paidAt()));
            html.append("</table><h2 style=\"font-size:16px;margin-top:24px;\">Thông tin khách hàng</h2><table role=\"presentation\" width=\"100%\">");
            info(html, "Người nhận", order.customerName());
            info(html, "Điện thoại", order.customerPhone());
            info(html, "Địa chỉ giao hàng", order.shippingAddress());
            info(html, "Đơn vị vận chuyển", order.carrierName());
            info(html, "Mã vận đơn", order.trackingCode());
            info(html, "Ghi chú", order.notes());
            html.append("</table>");
            if (order.items() != null && !order.items().isEmpty()) {
                html.append("<h2 style=\"font-size:16px;margin-top:24px;\">Chi tiết sản phẩm</h2><table width=\"100%\" cellspacing=\"0\" style=\"border-collapse:collapse;\"><thead><tr bgcolor=\"#f7f3f5\"><th align=\"left\" style=\"padding:10px 4px;\">Sản phẩm</th><th>SL</th><th align=\"right\">Đơn giá</th><th align=\"right\">Thành tiền</th></tr></thead><tbody>");
                for (var item : order.items()) {
                    BigDecimal price = zero(item.price()), discount = zero(item.discount());
                    BigDecimal net = price.subtract(discount).max(BigDecimal.ZERO);
                    html.append("<tr class=\"line\"><td style=\"padding:12px 4px;border-bottom:1px solid #eee;overflow-wrap:anywhere;\">");
                    String image = imageUrl(item.imageUrl(), mediaBaseUrl);
                    if (!image.isEmpty()) html.append("<img class=\"product-image\" src=\"").append(esc(image)).append("\" alt=\"Ảnh sản phẩm\" width=\"44\" height=\"44\" style=\"display:block;object-fit:cover;margin-bottom:6px;border-radius:4px;\">");
                    html.append("<strong>").append(esc(item.productName())).append("</strong>");
                    if (!blank(item.variantName())) html.append("<br><span style=\"font-size:12px;color:#716762;\">Phân loại: ").append(esc(item.variantName())).append("</span>");
                    if (!blank(item.sku())) html.append("<br><span style=\"font-size:12px;color:#716762;\">SKU: ").append(esc(item.sku())).append("</span>");
                    html.append("</td><td align=\"center\" style=\"padding:12px 4px;border-bottom:1px solid #eee;\">").append(item.quantity()).append("</td><td align=\"right\" style=\"padding:12px 4px;border-bottom:1px solid #eee;\">").append(money(net));
                    if (discount.signum() > 0) html.append("<br><del style=\"font-size:12px;color:#888;\">").append(money(price)).append("</del><br><span style=\"font-size:11px;color:#267548;\">Giảm ").append(money(discount)).append("/SP</span>");
                    html.append("</td><td align=\"right\" style=\"padding:12px 4px;border-bottom:1px solid #eee;\">").append(money(net.multiply(BigDecimal.valueOf(item.quantity())))).append("</td></tr>");
                }
                html.append("</tbody></table>");
            }
            if (order.servicePackageId() != null || order.appointmentId() != null || (order.services() != null && !order.services().isEmpty())) {
                html.append("<h2 style=\"font-size:16px;margin-top:24px;\">Dịch vụ Spa / Liệu trình</h2><table role=\"presentation\" width=\"100%\">");
                info(html, "Mã gói dịch vụ", order.servicePackageId() == null ? null : "#" + order.servicePackageId());
                Object purchased = message.getParameters() == null ? null : message.getParameters().get("spaPackage");
                if (purchased instanceof com.core.beautyshop.modules.spa.api.SpaOrderEmailFacade.PackageDetail pack) {
                    info(html, "Tên gói", pack.name());
                    info(html, "Thời hạn sử dụng", pack.validityDays() == null ? null : pack.validityDays() + " ngày");
                    if (pack.entitlements() != null) for (var benefit : pack.entitlements())
                        info(html, benefit.serviceName(), benefit.sessions() + " buổi");
                }
                info(html, "Quyền lợi", "TREATMENT_COURSE".equals(order.spaBenefitType()) ? "Liệu trình chăm sóc da" : order.spaBenefitType() == null ? null : "Gói dịch vụ Spa");
                info(html, "Mã lịch hẹn", order.appointmentId() == null ? null : "#" + order.appointmentId());
                if (order.services() != null) for (var service : order.services()) info(html,
                        service.serviceName() + " × " + service.quantity(),
                        money(zero(service.amount()).multiply(BigDecimal.valueOf(service.quantity()))));
                html.append("</table>");
            }
            html.append("<table role=\"presentation\" width=\"100%\" style=\"margin-top:24px;border-top:2px solid #8c2a47;padding-top:12px;\">");
            total(html, "Tạm tính", order.subTotal(), false);
            info(html, "Phí vận chuyển", order.shippingFee() == null ? null : order.shippingFee().signum() == 0 ? "Miễn phí" : money(order.shippingFee()));
            if (zero(order.discountAmount()).signum() > 0) info(html, "Giảm giá đơn hàng", "−" + money(order.discountAmount()));
            total(html, "Tổng thanh toán", order.totalAmount(), true);
            total(html, "Đã thanh toán", order.paidAmount(), true);
            if (zero(order.refundedAmount()).signum() > 0) total(html, "Đã hoàn tiền", order.refundedAmount(), false);
            if (order.totalAmount() != null && order.paidAmount() != null && !"CANCELLED".equals(order.status()))
                total(html, "Còn phải thanh toán", order.totalAmount().subtract(order.paidAmount()).max(BigDecimal.ZERO), false);
            html.append("</table>");
        }
        html.append("<p style=\"margin-top:24px;line-height:1.6;color:#716762;font-size:13px;\">Bạn có thể xem tiến độ và chi tiết đơn hàng tại mục Đơn hàng của tôi hoặc Tra cứu đơn hàng trên website BeautyShop.</p>");
        html.append("</td></tr><tr><td class=\"content\" bgcolor=\"#faf7f9\" style=\"padding:20px 32px;color:#716762;font-size:12px;text-align:center;line-height:1.7;\">");
        if (!blank(supportPhone) || !blank(supportEmail)) {
            html.append("Cần hỗ trợ? ");
            if (!blank(supportPhone)) html.append("Điện thoại: ").append(esc(supportPhone)).append("<br>");
            if (!blank(supportEmail)) html.append("Email: ").append(esc(supportEmail)).append("<br>");
        }
        html.append("Cảm ơn bạn đã lựa chọn BeautyShop.<br>Đây là email tự động. Vui lòng không phản hồi trực tiếp email này.</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private static void total(StringBuilder html, String label, BigDecimal amount, boolean bold) {
        if (amount != null) info(html, label, money(amount), bold);
    }
    private static void info(StringBuilder html, String label, String value) { info(html, label, value, false); }
    private static void info(StringBuilder html, String label, String value, boolean bold) {
        if (blank(value)) return;
        html.append("<tr><td style=\"padding:6px 4px;font-size:13px;vertical-align:top;\">").append(esc(label))
                .append("</td><td align=\"right\" style=\"padding:6px 4px;font-size:13px;overflow-wrap:anywhere;")
                .append(bold ? "font-weight:bold;color:#8c2a47;" : "").append("\">").append(esc(value)).append("</td></tr>");
    }
    private static String imageUrl(String value, String base) {
        if (blank(value)) return "";
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() && !blank(base)) uri = URI.create(base.replaceAll("/+$", "") + "/").resolve(uri);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null ? uri.toASCIIString() : "";
        } catch (IllegalArgumentException ex) { return ""; }
    }
    private static String date(Instant value) { return value == null ? null : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(value); }
    private static String money(BigDecimal value) { return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(zero(value)) + " ₫"; }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String esc(String value) { return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8"); }
    private static String paymentMethod(String value) { if (value == null) return null; return switch(value) { case "COD" -> "Thanh toán khi nhận hàng (COD)"; case "BANK" -> "Chuyển khoản ngân hàng"; case "CASH" -> "Tiền mặt"; case "VNPAY" -> "VNPay"; default -> value; }; }
    private static String paymentStatus(String value) { if (value == null) return null; return switch(value) { case "PAID" -> "Đã thanh toán"; case "PENDING" -> "Chờ thanh toán"; case "PARTIALLY_PAID" -> "Đã thanh toán một phần"; case "REFUNDED" -> "Đã hoàn tiền"; case "REFUND_PENDING" -> "Chờ hoàn tiền"; case "FAILED" -> "Thanh toán thất bại"; default -> value; }; }
    private static String status(String value) { if (value == null) return null; return switch(value) { case "PENDING" -> "Chờ xác nhận"; case "CONFIRMED" -> "Đã xác nhận"; case "PROCESSING" -> "Đang chuẩn bị"; case "SHIPPED" -> "Đang giao hàng"; case "DELIVERED" -> "Đã giao hàng"; case "COMPLETED" -> "Hoàn tất"; case "CANCELLED" -> "Đã hủy"; case "RETURNED" -> "Đã trả hàng"; case "DELIVERY_FAILED" -> "Giao hàng chưa thành công"; case "RETURN_TO_SENDER" -> "Hàng chuyển về cửa hàng"; default -> value; }; }
}
