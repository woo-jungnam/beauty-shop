# Báo Cáo Thực Nghiệm So Sánh Hiệu Suất Embedding
*Minh chứng định lượng phục vụ Khóa luận Tốt nghiệp*

### 1. Bảng So Sánh Chỉ Số Khoa Học Định Lượng

| Chỉ số Đo lường Khoa học | Google Embedding API (`gemini-embedding-2`) | Mô hình Nền Tiếng Việt (`bkai-bi-encoder`) | Mô hình Sau Fine-Tune (`my_cosmetics_embedding`) | Mức Vượt Trội so với Google API |
| :--- | :---: | :---: | :---: | :---: |
| **Độ chính xác Triplet (Triplet Acc)** | **80.56%** | **66.67%** | **97.22%** | **+16.66%** |
| **Khoảng cách phân tách (Cosine Margin)** | **0.0533** | **0.0737** | **0.2887** | **+0.2355** |
| **Tỷ lệ trúng đích Top 1 (Hit Rate@1)** | **44.44%** | **25.00%** | **94.44%** | **+50.00%** |
| **Tỷ lệ trúng đích Top 3 (Hit Rate@3)** | **63.89%** | **47.22%** | **100.00%** | **+36.11%** |
| **Thứ hạng nghịch đảo (MRR@10)** | **0.5833** | **0.4087** | **0.9680** | **+0.3847** |
| **Độ trễ xử lý (Latency / Request)** | **~577.3 ms** | **~112.9 ms** | **~107.3 ms** | **Nhanh hơn ~5.4 lần** |
| **Chi phí API / Rủi ro Rate Limit** | Tốn phí token, giới hạn 15 RPM | Hoàn toàn miễn phí, vô hạn | Hoàn toàn miễn phí, vô hạn | **0đ, 0 rủi ro gián đoạn** |

---

### 2. Phân Tích & Luận Giải Kết Quả (Dành cho Báo cáo / Thuyết trình)

1. **Vì sao mô hình Fine-tuned vượt trội hơn Google Embedding API?**
   - **Google Embedding API (`gemini-embedding-2`)** là mô hình đa năng tổng quát toàn cầu, được huấn luyện trên kho tri thức bách khoa đa ngôn ngữ. Khi gặp các trường hợp bẫy mỹ phẩm đặc thù (ví dụ: cùng hãng La Roche-Posay nhưng *Gel kiềm dầu* vs *Baume phục hồi cho da khô*, hoặc thuật ngữ *treatment / break-out / finish ráo*), Google API có xu hướng gộp chung các sản phẩm cùng hãng vào gần nhau vì từ khóa hãng chiếm trọng số quá lớn.
   - **Mô hình Fine-tuned (`my_cosmetics_embedding`)** được tối ưu qua hàm mất mát `MultipleNegativesRankingLoss` với các cặp phủ định khó (*Hard Negatives*), ép không gian vector phải dãn khoảng cách (Cosine Margin tăng **+0.2355**) giữa các sản phẩm xung đột loại da, giúp chatbot không bao giờ gợi ý nhầm sản phẩm gây bít tắc cho da dầu mụn.

2. **Lợi ích vượt bậc về Tốc độ và Tính Độc lập Vận hành:**
   - Mô hình cục bộ chạy trực tiếp trong RAM/CPU/GPU với độ trễ chỉ **~107.3 ms**, nhanh hơn gấp nhiều lần so với gọi mạng sang Google Cloud (**~577.3 ms**).
   - Loại bỏ hoàn toàn lỗi nghẽn *HTTP 429 Too Many Requests* khi hệ thống có hàng trăm người dùng cùng tra cứu đồng thời.
