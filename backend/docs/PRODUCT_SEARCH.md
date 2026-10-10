# Tìm kiếm sản phẩm

`GET /api/v1/products/search` là API công khai, trả `ApiResponse<PageResponse<ProductListResponse>>`. Chỉ lấy sản phẩm `ACTIVE` chưa xóa. URL cũ `?keyword=...&page=0&size=20` tiếp tục dùng được. Không có từ khóa vẫn tìm bằng bộ lọc; không có bộ lọc trả danh sách công khai.

## Tham số

| Tham số | Hành vi |
| --- | --- |
| `keyword` | Tối đa 200 ký tự; trim, không phân biệt hoa thường. Tìm tên, slug, mô tả ngắn/chi tiết, thương hiệu, thành phần/hoạt chất (trường text và danh mục tên/INCI/slug), nhãn tên/slug, SKU/barcode/tên biến thể active chưa xóa. Quan hệ thương hiệu/thành phần/nhãn đã xóa không dùng để khớp. `%`, `_`, `!` và `\` là ký tự tìm kiếm thông thường. |
| `brandId`, `brandIds` | ID dương; danh sách OR. Nếu gửi cả hai, lấy hợp của chúng. Thương hiệu đã xóa không dùng để khớp bộ lọc. |
| `categoryId`, `categoryIds` | ID dương; danh sách OR và hợp với ID đơn. Khớp danh mục chính xác, active chưa xóa; không tự lấy danh mục con. |
| `tagIds` | OR giữa các ID nhãn chưa xóa. |
| `productType` | `PRODUCT`, `SERVICE`, `COMBO`. |
| `skinType` | `ALL_SKIN`, `OILY`, `DRY`, `COMBINATION`, `SENSITIVE`, `NORMAL`. Tương thích được đánh dấu không khuyến nghị cho loại da yêu cầu sẽ chặn sản phẩm. Khớp dữ liệu được khuyến nghị hoặc fallback trường `skinType`/`ALL_SKIN` khi không có chống chỉ định rõ ràng. Riêng lọc `ALL_SKIN`, bất kỳ bản ghi không khuyến nghị cho loại da nào cũng chặn kết quả. |
| `targetGender` | `MALE`, `FEMALE`, `UNISEX`, khớp chính xác; không tự thêm sản phẩm `UNISEX` khi lọc giới tính khác. |
| `minPrice`, `maxPrice` | VND, từ 0 đến 9.999.999.999,99, tối đa hai chữ số thập phân, bao gồm biên; `maxPrice >= minPrice`. Phải có **một SKU** active chưa xóa thỏa cả hai biên. |
| `minRating` | Điểm đánh giá trung bình tối thiểu, từ 0 đến 5. |
| `isFeatured` | Lọc cờ nổi bật chính xác. |
| `onSale` | `true`: có SKU có `discountPrice < price`; `false`: có SKU không giảm giá. Dùng giá catalog, chưa áp voucher. |
| `inStock` | `true`: có SKU còn khả dụng; `false`: không có SKU active chưa xóa còn khả dụng. |
| `hasFragrance`, `hasAlcohol` | Lọc cờ hương liệu/cồn chính xác. |
| `originCountry` | Tối đa 100 ký tự, trim tham số gửi vào rồi khớp chính xác không phân biệt hoa thường. |
| `ingredientIds` | OR giữa các ID thành phần có trong công thức. |
| `skinConcernIds` | OR giữa các ID vấn đề da được sản phẩm hỗ trợ. |
| `page`, `size` | Trang từ 0; size 1–100, mặc định 20. Giá trị sai trả 400, không tự ép về giới hạn. |
| `sort` | Dạng `sort=price,asc`; có thể lặp để sắp xếp nhiều tiêu chí. Trường cho phép: `id`, `name`, `createdAt`, `updatedAt`, `price`/`minPrice`, `maxPrice`, `averageRating`, `totalSold`. Mặc định `createdAt,desc`; tự thêm `id,desc` nếu chưa có để giữ thứ tự ổn định. |

Tất cả bộ lọc là tùy chọn. Kết hợp **AND giữa các nhóm**, **OR trong một danh sách ID**. Danh sách tối đa 50 ID mỗi tham số; có thể dùng `brandIds=1,2` hoặc lặp tham số. Boolean bỏ trống không lọc; `false` vẫn là điều kiện lọc. Tham số sai kiểu, enum, ID âm/0, khoảng giá hoặc sort ngoài danh sách trả HTTP 400. Nếu không ID nào trong một nhóm (kể cả hợp ID đơn và danh sách) khớp dữ liệu, trả trang rỗng; ID không tồn tại cùng ID hợp lệ vẫn áp dụng OR.

## Giá, tồn kho và phân trang

Giá hiệu lực của SKU là `discountPrice` khi có giá trị (kể cả 0), ngược lại `price`. Khi cùng gửi giá, `onSale` và `inStock=true`, **cùng một SKU phải thỏa toàn bộ điều kiện**. Không ghép giá của SKU A với tồn của SKU B. `inStock=false` loại sản phẩm có bất kỳ SKU active còn khả dụng, kể cả SKU đó ngoài khoảng giá.

Tồn khả dụng là `SUM(quantity - reservedQuantity)` qua các lô chưa xóa, chưa hết hạn (`expirationDate > CURRENT_DATE` hoặc không có hạn), thuộc kho active chưa xóa. Hàng cách ly nằm ở bucket riêng. Tìm kiếm phản ánh tồn tại thời điểm truy vấn; checkout vẫn kiểm lại tồn và giữ chỗ.

`minPrice`/`maxPrice` trong response vẫn là toàn bộ khoảng giá SKU active của sản phẩm, không chỉ SKU vừa khớp bộ lọc. Sản phẩm không có SKU active có giá hiển thị 0 và xuất hiện khi không lọc SKU; không khớp bộ lọc giá, sale hoặc `inStock=true`. Sắp xếp `price` là theo `minPrice` hiển thị, không theo giá của riêng SKU khớp.

Lọc, đếm và sắp xếp được thực hiện tại database **trước** phân trang. Quan hệ nhiều danh mục/nhãn/thành phần không làm trùng sản phẩm hoặc tăng sai `totalElements`. Offset vượt giới hạn truy vấn JPA trả 400.

## Ví dụ tích hợp

```http
GET /api/v1/products/search?keyword=serum&brandIds=1,2&categoryId=3&skinType=OILY&minPrice=100000&maxPrice=500000&inStock=true&sort=price,asc&page=0&size=20
GET /api/v1/products/search?onSale=true&hasFragrance=false&minRating=4&sort=totalSold,desc
GET /api/v1/products/search?keyword=ABC-SKU&sort=name,asc&sort=id,desc
```

Frontend cần gửi đầy đủ bộ lọc vào API và dùng `totalElements`/`totalPages` trả về. Trang Catalog hiện tải tối đa 60 sản phẩm rồi lọc/sắp xếp tại client; cần chuyển sang API này để tìm trên toàn bộ catalog. Backend giữ response hiện hữu để client đang dùng keyword tiếp tục hoạt động.

Các thuộc tính mở rộng động chưa có bộ lọc riêng trong API này. Tìm kiếm gần đúng, bỏ dấu tiếng Việt và tìm toàn văn không phải cam kết chung; khả năng so khớp dấu còn phụ thuộc collation cơ sở dữ liệu. Kết quả lọc chăm sóc da phản ánh dữ liệu catalog, không thay tư vấn cá nhân.

## Kiểm chứng

Bộ kiểm chứng: `ProductSearchIntegrationTest`, `MySqlProductSearchIntegrationTest`, `OpenApiContractIntegrationTest`, `ProductVisibilityIntegrationTest`, `ModularArchitectureTest` và `ComprehensiveSwaggerAndBearerApiTest`. Chạy qua `ops/verify_spa_backend.ps1`; sau khi đạt, sinh lại snapshot bằng `ops/generate_api_docs.py`.

Kết quả gần nhất ngày 02/10/2026: **54 kiểm tra đạt, 0 failure/error/skip**, gồm:

| Suite | Số ca đạt |
| --- | --- |
| ProductSearchIntegrationTest (H2, HTTP) | 15 |
| MySqlProductSearchIntegrationTest (MySQL 8, Flyway V35, Hibernate validate) | 15 |
| OpenApiContractIntegrationTest | 7 |
| ComprehensiveSwaggerAndBearerApiTest | 10 |
| ProductVisibilityIntegrationTest | 1 |
| ModularArchitectureTest | 6 |

Lượt đầu chạy 54 kiểm tra phát hiện một lỗi tài liệu: `page`/`size` bị đánh dấu bắt buộc do `@NotNull`, mặc dù request có giá trị mặc định. Đã sửa metadata để giữ validation HTTP đúng và thể hiện tham số tùy chọn. Lượt cuối **7 kiểm tra OpenAPI đạt, BUILD SUCCESS lúc 18:07:16 +07:00**; các suite nghiệp vụ đã đạt ở lượt đầu. Bảng tổng hợp kết quả gần nhất từng suite, không phải một lượt 54 kiểm tra mới sau sửa metadata.

Snapshot được sinh lại từ bộ OpenAPI đã đạt: **253 operations / 179 paths / 13 nhóm**, search có **23 tham số query tùy chọn**, không có object `pageable`/request trong query. [Bằng chứng và hash snapshot](PRODUCT_SEARCH_VALIDATION_2026-10-02.json), [OpenAPI JSON](openapi.json).

Chưa chạy benchmark trên catalog lớn. Thay đổi này không cần migration mới; MySQL V35 được dựng và kiểm trong database test. Frontend cần bước tích hợp đã nêu ở trên để sử dụng đầy đủ tìm kiếm mới.
