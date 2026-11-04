import re
from typing import Any, Dict, List, Optional
from app.core.logging import getLogger
from app.services.ingestion.extractor import dbExtractor

logger = getLogger("order_repository")


class OrderRepository:

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

    def findOrder(self, identifier: str) -> Optional[Dict[str, Any]]:
        cleanId = identifier.strip().lstrip("#")
        try:
            conn = dbExtractor.getConnection()
        except Exception as exc:
            logger.warning("Không thể kết nối MySQL khi tra cứu đơn hàng: %s", exc)
            return None

        try:
            with conn.cursor() as cursor:
                sqlOrder = """
                    SELECT
                        id,
                        order_number,
                        status,
                        payment_method,
                        payment_status,
                        customer_name,
                        customer_phone,
                        shipping_address,
                        ward,
                        district,
                        city,
                        sub_total,
                        shipping_fee,
                        discount_amount,
                        total_amount,
                        created_at
                    FROM orders
                    WHERE (order_number = %s OR customer_phone = %s)
                      AND is_deleted = 0
                    ORDER BY created_at DESC
                    LIMIT 1
                """
                cursor.execute(sqlOrder, (cleanId, cleanId))
                order = cursor.fetchone()
                if not order:
                    return None

                sqlItems = """
                    SELECT
                        id,
                        product_name,
                        variant_name,
                        quantity,
                        price,
                        discount
                    FROM order_items
                    WHERE order_id = %s
                      AND is_deleted = 0
                """
                cursor.execute(sqlItems, (order["id"],))
                order["items"] = cursor.fetchall() or []
                order["status_vi"] = self.STATUS_MAP.get(order["status"], order["status"])
                order["payment_status_vi"] = self.PAYMENT_STATUS_MAP.get(
                    order["payment_status"], order["payment_status"]
                )
                return order
        except Exception as exc:
            logger.error("Lỗi truy vấn cơ sở dữ liệu khi tra cứu đơn: %s", exc)
            return None
        finally:
            try:
                conn.close()
            except Exception:
                pass

    find_order = findOrder

    def formatOrderSummary(self, order: Dict[str, Any]) -> str:
        orderNumber = order.get("order_number", "")
        statusVi = order.get("status_vi", order.get("status", ""))
        payStatusVi = order.get("payment_status_vi", order.get("payment_status", ""))
        customerName = order.get("customer_name", "Quý khách")
        phone = order.get("customer_phone", "")
        address = order.get("shipping_address", "")
        city = order.get("city", "")
        fullAddress = f"{address}, {city}".strip(", ") if city else address
        totalAmount = float(order.get("total_amount", 0.0) or 0.0)

        items = order.get("items", [])
        itemsText = ""
        if items:
            itemList = []
            for it in items:
                pName = it.get("product_name", "Sản phẩm")
                vName = f" ({it.get('variant_name')})" if it.get("variant_name") else ""
                qty = it.get("quantity", 1)
                price = float(it.get("price", 0.0) or 0.0)
                itemList.append(f"- **{pName}{vName}** x{qty}: {price:,.0f}đ")
            itemsText = "\n".join(itemList)
        else:
            itemsText = "- Chưa có thông tin sản phẩm"

        lines = [
            f"**Thông tin đơn hàng #{orderNumber}**",
            f"- **Trạng thái giao vận**: {statusVi}",
            f"- **Thanh toán**: {payStatusVi}",
            f"- **Người nhận**: {customerName} ({phone})",
            f"- **Địa chỉ giao hàng**: {fullAddress}",
            f"- **Danh sách sản phẩm**:\n{itemsText}",
            f"- **Tổng tiền thanh toán**: **{totalAmount:,.0f}đ**",
        ]
        return "\n".join(lines)

    format_order_summary = formatOrderSummary


orderRepository = OrderRepository()
order_repository = orderRepository
