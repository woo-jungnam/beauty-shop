# Tài liệu API backend

Tra cứu trực tiếp tại **`/swagger-ui.html`** trên máy chủ backend. Chọn nhóm nghiệp vụ trong danh sách đầu trang; nhóm **00 · Toàn bộ API** chứa toàn bộ endpoint.

| Tài liệu | Dùng khi nào |
| --- | --- |
| [Hướng dẫn API và Swagger](API_GUIDE.md) | Xác thực, nhóm API, response, phân trang, trình tự gọi và lưu ý tích hợp |
| [Tìm kiếm sản phẩm](PRODUCT_SEARCH.md) | Bộ lọc kết hợp, giá/tồn cùng SKU, sắp xếp, phân trang và ví dụ gọi API |
| [Danh mục endpoint](API_ENDPOINTS.md) | Tra cứu method/path/quyền/trạng thái HTTP, sinh từ OpenAPI đã kiểm chứng |
| [OpenAPI JSON](openapi.json) | Import vào công cụ API/client; snapshot của mã tại thời điểm kiểm chứng |
| [Báo cáo đối chiếu và sửa](API_DOCS_REVIEW_2026-10-02.md) | Các sai lệch được sửa, phạm vi kiểm thử và giới hạn còn lại |
| [Chuẩn bị hồ sơ và chăm sóc Spa](../SPA_CARE_WORKFLOW_2026-10-02.md) | Form phiên bản, consent, cảnh báo và thông báo |
| [Tài nguyên Spa](../SPA_RESOURCE_MANAGEMENT.md) | Giường/phòng/máy, capacity, bảo trì và phân bổ |
| [Thanh toán buổi Spa](../SPA_VISIT_CHECKOUT.md) | Invoice, CASH/BANK, idempotency và xác nhận đã hoàn thủ công |
| [Rà soát nghiệp vụ Spa](../SPA_BUSINESS_REVIEW_2026-10-02.md) | Quy tắc vận hành đã có và những nghiệp vụ cần thiết kế tiếp |

OpenAPI chạy trực tiếp tại `/v3/api-docs` phản ánh phiên bản backend đang chạy. Snapshot JSON và danh mục Markdown cần sinh lại khi thay controller/DTO; hướng dẫn kiểm chứng nằm trong API_GUIDE.
