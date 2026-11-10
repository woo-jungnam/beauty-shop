import re
from typing import Any, Dict, Optional
from app.core.logging import getLogger
from app.services.ingestion.extractor import dbExtractor

logger = getLogger("order_repository")


class OrderRepository:
    # OrderFactory.generateOrderNumber: ORD- followed by a UUID without hyphens.
    ORDER_NUMBER_PATTERN = re.compile(r"(?<![\w-])ORD-[A-F0-9]{32}(?![\w-])", re.I)
    PHONE_PATTERN = re.compile(r"(?<![\w+-])(?:0|\+84)[35789][0-9]{8}(?![\w+-])")

    STATUS_MAP = {
        "PENDING": "Chờ xác nhận",
        "CONFIRMED": "Đã xác nhận",
        "PROCESSING": "Đang đóng gói chuẩn bị hàng",
        "SHIPPING": "Đang giao hàng",
        "DELIVERED": "Giao hàng thành công",
        "CANCELLED": "Đã hủy đơn hàng",
        "RETURNED": "Đã hoàn trả",
    }

    PAYMENT_STATUS_MAP = {
        "PENDING": "Chưa thanh toán (COD / Chờ chuyển khoản)",
        "PAID": "Đã thanh toán thành công",
        "FAILED": "Thanh toán thất bại",
        "REFUNDED": "Đã hoàn tiền",
    }

    @staticmethod
    def maskPhone(phone: str) -> str:
        if re.fullmatch(r"\*{6}[0-9]{4}", phone):
            return phone
        if OrderRepository.PHONE_PATTERN.fullmatch(phone):
            return f"******{phone[-4:]}"
        return "Đã ẩn"

    def findOrder(self, orderNumber: str, customerPhone: Optional[str] = None) -> Optional[Dict[str, Any]]:
        cleanOrder = (orderNumber or "").strip().removeprefix("#").upper()
        cleanPhone = (customerPhone or "").strip()
        if not self.ORDER_NUMBER_PATTERN.fullmatch(cleanOrder) or not self.PHONE_PATTERN.fullmatch(cleanPhone):
            return None
        cleanPhone = cleanPhone.replace("+84", "0", 1)

        try:
            conn = dbExtractor.getConnection()
        except Exception:
            logger.warning("Không thể kết nối MySQL khi tra cứu đơn hàng")
            return None

        try:
            with conn.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT id, order_number, status, payment_status
                    FROM orders
                    WHERE order_number = %s
                      AND REPLACE(customer_phone, '+84', '0') = %s
                      AND is_deleted = 0
                    LIMIT 1
                    """,
                    (cleanOrder, cleanPhone),
                )
                row = cursor.fetchone()
                if not row:
                    return None

                order = {key: row[key] for key in ("id", "order_number", "status", "payment_status")}
                order["customer_phone"] = self.maskPhone(cleanPhone)
                order["status_vi"] = self.STATUS_MAP.get(order["status"], "Chưa cập nhật")
                order["payment_status_vi"] = self.PAYMENT_STATUS_MAP.get(order["payment_status"], "Chưa cập nhật")
                return order
        except Exception:
            logger.error("Lỗi truy vấn cơ sở dữ liệu khi tra cứu đơn")
            return None
        finally:
            try:
                conn.close()
            except Exception:
                pass

    find_order = findOrder

    def formatOrderSummary(self, order: Dict[str, Any]) -> str:
        orderNumber = order.get("order_number", "")
        statusVi = self.STATUS_MAP.get(order.get("status"), "Chưa cập nhật")
        payStatusVi = self.PAYMENT_STATUS_MAP.get(order.get("payment_status"), "Chưa cập nhật")
        phone = self.maskPhone(order.get("customer_phone") or "")
        return "\n".join([
            f"**Thông tin đơn hàng #{orderNumber}**",
            f"- **Trạng thái giao vận**: {statusVi}",
            f"- **Thanh toán**: {payStatusVi}",
            f"- **Số điện thoại đặt hàng**: {phone}",
        ])

    format_order_summary = formatOrderSummary


orderRepository = OrderRepository()
order_repository = orderRepository
