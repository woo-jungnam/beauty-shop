Listed directory khoaluantotnghiep
Listed directory backend
Viewed pom.xml:1-148
Listed directory beautyshop
Listed directory entities
Listed directory controller
Listed directory service
Listed directory repository
Listed directory security
Listed directory config
Listed directory exception
Listed directory resources
Viewed application.yaml:1-20
Viewed SecurityConfig.java:1-117
Viewed GlobalExceptionHandler.java:1-96
Viewed ProductController.java:1-181
Viewed ProductServiceImpl.java:1-292
Listed directory common
Viewed Base.java:1-50
Listed directory mapper
Listed directory factory
Listed directory order
Viewed OrderServiceImpl.java:1-236
Listed directory inventory
Viewed InventoryServiceImpl.java:1-71
Viewed Product.java:1-131
Searched for "Kafka"
Searched for "@Cache"
Listed directory java
Listed directory beautyshop
Listed directory service
Listed directory controller
Viewed ProductAttributeController.java:1-101

Với vai trò là **Senior Java Spring Boot Backend Developer** và **Solution Architect**, tôi đã thực hiện rà soát, mổ xẻ toàn diện mã nguồn dự án (**Beauty Shop / Spa Platform**). Dưới đây là báo cáo phân tích kiến trúc, đánh giá mã nguồn, chỉ ra các điểm thiếu sót kỹ thuật và đề xuất giải pháp chuẩn hóa theo tiêu chuẩn công nghiệp (Enterprise Standards).

---

# I. TỔNG QUAN HỆ THỐNG & ĐÁNH GIÁ NHANH

| Tiêu chí | Đánh giá | Điểm số (10) | Ghi chú |
| :--- | :--- | :---: | :--- |
| **Kiến trúc tổng thể** | Layered Architecture kết hợp Strategy/Factory | **7.5** | Phân tầng rõ ràng, áp dụng Design Patterns tốt ở tầng Order/Payment |
| **Tổ chức Package** | Lai tạp (Hybrid) giữa Feature và Layer | **6.0** | Thiếu nhất quán (`entities`, `service` chia feature; `controller`, `repository` để phẳng) |
| **Thiết kế Domain/Entity** | Tương đối đầy đủ quan hệ E-Commerce & Spa | **6.5** | Thiếu Indexing, Soft-delete thủ công, nguy cơ N+1 và Fetch overhead |
| **Xử lý Đơn hàng & Kho** | Có chia status & stock reservation | **5.0** | **RỦI RO CAO**: Race Condition / Thiếu Lock khi trừ tồn kho đồng thời |
| **RESTful API Design** | Chuẩn hóa DTO và ApiResponse bọc chuẩn | **8.0** | Phân trang tốt (`PageResponse`), HTTP Status code chuẩn mực |
| **Security & Auth** | Stateless JWT + Method Security | **7.5** | Hoạt động tốt, bắt lỗi phân quyền chặt chẽ |
| **Dependencies & Testing** | Rác dependency, Test coverage quá thấp | **4.0** | `spring-kafka`, `cache` bỏ không; Unit Test < 5% |

---

# II. PHÂN TÍCH KIẾN TRÚC TỔNG THỂ & TỔ CHỨC MÃ NGUỒN

### 1. Kiến trúc tổng thể (Architecture Style)
* **Mô hình hiện tại**: **Layered Monolithic Architecture** (Controller $\rightarrow$ Service $\rightarrow$ Repository $\rightarrow$ Database).
* **Điểm sáng**:
  * Đã áp dụng **Design Patterns** rất sáng tạo và đúng chỗ:
    * `PaymentStrategyFactory` + `PaymentStrategy` (Strategy Pattern) cho việc xử lý thanh toán đa phương thức (COD, Sepay, v.v.).
    * `OrderFactory`, `UserFactory` (Factory Pattern) để tách biệt logic khởi tạo object phức tạp.
  * Tách biệt rõ ràng giữa Business logic và REST endpoints.

### 2. Bất cập trong tổ chức thư mục / Gói (Package Structure Inconsistency)
Hiện tại dự án đang bị **mâu thuẫn cấu trúc package (Hybrid Anti-pattern)**:
```
com.core.beautyshop
├── controller/          <-- Flat (tất cả controller gom chung 1 thư mục)
├── repository/          <-- Flat (23 repositories gom chung 1 thư mục)
├── entities/            <-- Package-by-Feature (booking, cart, order, product, warehouse...)
├── service/             <-- Package-by-Feature (auth, brand, cart, order, product...)
├── dto/                 <-- Chia theo request / response / common
└── mapper/              <-- Gom chung (chỉ có 2 mapper, còn lại map tay trong Service)
```
* **Vấn đề**:
  1. Khi hệ thống mở rộng lên 50-100 API, thư mục `controller/` và `repository/` sẽ bị phình to (god package), khó quản lý quyền truy cập gói (`package-private`).
  2. Không tuân thủ hoàn toàn **Package-by-Feature** (khuyên dùng cho dự án nghiệp vụ phức tạp) hoặc **Clean Architecture/Hexagonal Architecture** (Port & Adapter).

---

# III. PHÂN TÍCH CỤC BỘ TỪNG TẦNG (DEEP-DIVE LAYER ANALYSIS)

---

### 1. Tầng Domain & Entities (JPA / Hibernate)

#### 🔴 Điểm thiếu sót & Vi phạm:
1. **Cơ chế Soft-Delete thủ công (Manual Soft Delete)**:
   * [Base.java](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/entities/common/Base.java) định nghĩa `isDeleted`, nhưng ở [ProductServiceImpl.java](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/service/product/ProductServiceImpl.java) phải viết thủ công `productRepository.findByIdAndIsDeletedFalse(...)`.
   * **Hệ quả**: Nếu một query join quan hệ `@OneToMany` (ví dụ `product.getVariants()`), Hibernate vẫn load cả các variant đã bị soft-deleted vì không có filter cấp ORM.
   * **Giải pháp chuẩn**: Dùng `@SQLRestriction("is_deleted = false")` (Hibernate 6.x) và `@SQLDelete(sql = "UPDATE ... SET is_deleted = true WHERE id = ?")`.

2. **Sử dụng `java.util.List` cho nhiều quan hệ `@OneToMany` và `@ManyToMany`**:
   * Trong [Product.java](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/entities/product/Product.java): `categories`, `variants`, `images`, `tags`, `attributeValues` đều dùng `List`.
   * **Hệ quả**: Gây ra lỗi `MultipleBagFetchException` khi cố gắng eager fetch, hoặc tạo ra tích đề-các (Cartesian Product Problem) làm chậm database nghiêm trọng khi JOIN FETCH.
   * **Giải pháp chuẩn**: Chuyển các quan hệ không có thứ tự bắt buộc sang `java.util.Set`.

3. **Thiếu Indexing ở Database**:
   * Các cột thường xuyên `WHERE`, `ORDER BY` hoặc `JOIN` như `slug`, `status`, `brand_id`, `category_id`, `created_at` chưa được cấu hình `@Table(indexes = { @Index(...) })`.

---

### 2. Tầng Business Logic (Service Layer) & Quản lý Đồng thời (Concurrency)

#### 🔴 Rủi ro Nghiêm trọng: Race Condition khi Giữ / Trừ Tồn Kho (Overselling)
Trong [InventoryServiceImpl.java](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/service/inventory/InventoryServiceImpl.java):
```java
// Dòng 34-35: Kiểm tra số lượng
if (!isStockAvailable(variantId, quantityToReserve)) {
    throw new InsufficientStockException(...);
}
// Dòng 38: Lấy danh sách kho và duyệt trừ số lượng
List<WarehouseStock> stocks = warehouseStockRepository.findByProductVariantId(variantId);
for (WarehouseStock stock : stocks) {
    ...
    stock.setReservedQuantity(...);
    warehouseStockRepository.save(stock);
}
```
* **Lỗ hổng (Check-Then-Act Anti-Pattern)**: Không có cơ chế **Locking** (`Pessimistic Lock` hoặc `Optimistic Lock`).
* **Hậu quả**: Khi 2 khách hàng cùng đặt 1 sản phẩm chỉ còn 1 cái trong kho vào cùng 1 thời điểm:
  * Thread A đọc: còn 1.
  * Thread B đọc: còn 1.
  * Cả 2 đều pass qua điều kiện `isStockAvailable` $\rightarrow$ Đơn hàng đều tạo thành công $\rightarrow$ **Âm kho (Overselling)**.
* **Giải pháp chuẩn**:
  * Sử dụng **Pessimistic Locking** trên `WarehouseStockRepository`:
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.productVariant.id = :variantId")
    List<WarehouseStock> findByProductVariantIdForUpdate(Long variantId);
    ```
  * Hoặc dùng Atomic Update query trực tiếp trong SQL: `UPDATE warehouse_stocks SET reserved_quantity = reserved_quantity + :qty WHERE ... AND quantity - reserved_quantity >= :qty`.

#### 🔴 Boilerplate DTO Mapping:
* [ProductServiceImpl.java](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/service/product/ProductServiceImpl.java#L206-L291) tốn gần 90 dòng code chỉ để map thủ công từ Entity sang Response (`mapToProductResponse`, `mapToProductListResponse`).
* Dự án có `mapper/` nhưng chỉ dùng cho `Auth` và `Order`.
* **Giải pháp**: Áp dụng thư viện **MapStruct** để auto-generate code mapping ở compile-time, tăng hiệu năng và code cực kỳ gọn gàng.

---

### 3. Tầng Controller & API Design

#### 🟢 Ưu điểm:
* RESTful Endpoint đặt tên danh từ số nhiều chuẩn mực (`/api/v1/products`, `/api/v1/attributes`).
* Dùng `ApiResponse<T>` đồng nhất schema:
  ```json
  { "status": 200, "message": "...", "data": {...}, "timestamp": "...", "path": "..." }
  ```
* Bắt lỗi Validation `@Valid` và trả về chi tiết từng `fieldErrors` thông qua [GlobalExceptionHandler.java](file:///d:/khoaluantotnghiep/backend/src/main/java/com/core/beautyshop/exception/GlobalExceptionHandler.java).

#### 🔴 Điểm cần cải thiện:
1. **Lạm dụng tham số `HttpServletRequest req` trong Controller**:
   * Hầu như controller method nào cũng khai báo `HttpServletRequest req` chỉ để lấy `req.getRequestURI()` truyền vào `ApiResponse`.
   * **Giải pháp**: Viết một `ResponseBodyAdvice` hoặc `HandlerInterceptor` để tự động gán `path` và `timestamp` trước khi response trả về client, giúp các phương thức trong Controller sạch đẹp và dễ viết Unit Test hơn.
2. **Thiếu Dynamic Filtering / Specification**:
   * Việc search/filter sản phẩm đang viết các endpoint rời rạc: `/category/{id}`, `/brand/{id}`, `/search?keyword=...`.
   * **Giải pháp**: Nên dùng **Spring Data JPA Specification** hoặc **QueryDSL** để hỗ trợ Dynamic Filter linh hoạt: `/api/v1/products?categoryId=1&brandId=2&minPrice=100&sort=price,desc`.

---

### 4. Tầng Cấu hình & Bảo mật (Configuration & Security)

#### 🔴 Rủi ro & Bất cập:
1. **Database Schema DDL Auto**:
   * [application.yaml](file:///d:/khoaluantotnghiep/backend/src/main/resources/application.yaml#L11): `ddl-auto: update`.
   * **Rủi ro**: Trong môi trường Production, `ddl-auto: update` có thể làm hỏng index, không xóa được column cũ, và không thể rollback khi deploy lỗi.
   * **Giải pháp**: Chuyển sang dùng **Flyway** hoặc **Liquibase** để quản lý database migrations theo version (`V1__init_schema.sql`, `V2__add_index.sql`).

2. **Hardcoded Secrets trong Config**:
   * Default fallback cho `jwt.secret` và `spring.datasource.password` để plaintext trong source.
   * Khi deploy, nếu quên cấu hình Environment Variable, hệ thống sẽ chạy với key mặc định gây lỗ hổng bảo mật nghiêm trọng.

3. **Cấu hình Spring Boot Version bất thường**:
   * Trong [pom.xml](file:///d:/khoaluantotnghiep/backend/pom.xml#L8): `<version>4.1.0</version>` cho `spring-boot-starter-parent`. Hiện tại Spring Boot bản ổn định là `3.3.x / 3.4.x` (Spring Boot 4 chưa phát hành chính thức). Điều này có thể dẫn tới sự không tương thích của các dependency bên thứ 3.

---

### 5. Quản lý Dependencies & Testing

1. **Dead / Unused Dependencies**:
   * `spring-kafka` và `spring-kafka-test` được khai báo nhưng **hoàn toàn không có class nào sử dụng Kafka** trong code.
   * `spring-boot-starter-cache` có mặt nhưng không có cấu hình Cache Manager (Redis / Caffeine) và không có `@EnableCaching` hay `@Cacheable` trên Service.
   * **Khắc phục**: Xóa bớt dependency thừa để giảm dung lượng JAR build và thời gian khởi động container.

2. **Kiểm thử (Unit / Integration Testing)**:
   * Mã nguồn hiện chỉ có 2 test files đơn giản. Tầng Core Service (`ProductService`, `OrderService`, `InventoryService`) có độ phủ **0% test**.
   * Thiếu các kịch bản test quan trọng: Test luồng tính tiền Order, test trừ tồn kho khi concurrent, test validation input.

---

# IV. BẢNG TỔNG HỢP CÁC VI PHẠM & ĐỀ XUẤT GIẢI PHÁP (ACTIONABLE ROADMAP)

| Hạng mục | Hiện trạng (Lỗi / Thiếu sót) | Chuẩn đề xuất (Best Practice) | Mức độ ưu tiên |
| :--- | :--- | :--- | :---: |
| **Concurrency / Inventory** | Trừ kho không có Lock gây nguy cơ Race Condition | Thêm `Pessimistic Lock` (`FOR UPDATE`) khi reserve kho | 🔴 **CRITICAL** |
| **Database Migration** | Sử dụng `hibernate.ddl-auto: update` | Tích hợp **Flyway Migration** | 🔴 **HIGH** |
| **Soft Delete Pattern** | Xử lý filter `isDeleted` thủ công ở repo/service | Dùng `@SQLRestriction` và `@SQLDelete` của Hibernate | 🟡 **MEDIUM** |
| **Data Mapping** | Map Entity $\leftrightarrow$ DTO thủ công lặp lại nhiều dòng | Dùng **MapStruct** | 🟡 **MEDIUM** |
| **Search / Filter API** | Endpoint tìm kiếm bị phân mảnh | Dùng **JPA Specification** hoặc **QueryDSL** | 🟡 **MEDIUM** |
| **Architecture Organization** | Controller/Repo gom phẳng, Service chia feature | Chuẩn hóa theo **Package-by-Feature** hoặc **Hexagonal** | 🟢 **LOW** |
| **Dependency Cleanup** | Dư thừa `spring-kafka`, `spring-boot-starter-cache` | Gỡ bỏ dependency thừa, chuẩn hóa Spring Boot `3.3.x` | 🟢 **LOW** |
| **Unit & Integration Test** | Test coverage < 5% | Viết Unit Test với JUnit 5 + Mockito cho Service | 🔴 **HIGH** |

---

# V. KẾT LUẬN & ĐỊNH HƯỚNG MỞ RỘNG (ARCHITECT'S VERDICT)

Mã nguồn dự án của bạn có **nền tảng tốt**: code viết tương đối sạch, áp dụng OOP và Design Patterns tốt (Factory, Strategy, Repository), cấu trúc Response/Error Handler rất bài bản.

Tuy nhiên, để hệ thống đạt chuẩn **Enterprise-Ready**, sẵn sàng chịu tải thực tế và bảo vệ tính toàn vẹn dữ liệu trong môi trường thương mại điện tử, việc **sửa lỗi Race Condition ở Kho hàng**, **thêm Database Migration (Flyway)**, và **áp dụng MapStruct + JPA Specification** là những bước đi quan trọng nhất cần thực hiện ngay.