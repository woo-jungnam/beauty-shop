# -*- coding: utf-8 -*-
"""
Script ve 4 so do kien truc trien khai he thong chuyen nghiep bang Matplotlib:
1. So do kien truc trien khai tong the (Overall System Deployment Architecture)
2. So do luong du lieu va tich hop da dich vu (Multi-service Data Flow & Integration)
3. So do phan tang an ninh va co lap ha tang mang (Zero-Trust Security & Network Isolation)
4. So do quy trinh CI/CD va chu trinh dieu phoi khoi dong dich vu (CI/CD Pipeline & Orchestration)
"""

import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

def setup_plot(width=16, height=10):
    fig, ax = plt.subplots(figsize=(width, height), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')
    fig.patch.set_facecolor('#FFFFFF')
    return fig, ax

def draw_box(ax, x, y, w, h, title, subtitle="", bg_color="#EBF3FA", border_color="#1B365D",
             title_color="#002060", title_size=10, sub_size=8, radius=1.5, border_width=1.5,
             border_style='solid'):
    box = FancyBboxPatch((x, y), w, h,
                         boxstyle=f"round,pad=0.2,rounding_size={radius}",
                         linewidth=border_width,
                         edgecolor=border_color,
                         facecolor=bg_color,
                         linestyle=border_style)
    ax.add_patch(box)
    
    if title:
        ty = y + h - (h * 0.35 if subtitle else h * 0.5)
        ax.text(x + w/2, ty, title, color=title_color, fontsize=title_size,
                fontweight='bold', ha='center', va='center', family='sans-serif')
    if subtitle:
        sy = y + (h * 0.3)
        ax.text(x + w/2, sy, subtitle, color='#4A5568', fontsize=sub_size,
                ha='center', va='center', family='sans-serif')

def draw_arrow(ax, x1, y1, x2, y2, color="#1B365D", style="-|>", width=1.5, label="",
               label_pos=0.5, label_offset=(0, 1.5), dashed=False, label_color="#1A202C",
               label_size=8):
    ls = '--' if dashed else '-'
    arrow = FancyArrowPatch((x1, y1), (x2, y2),
                            arrowstyle=style,
                            mutation_scale=12,
                            linewidth=width,
                            color=color,
                            linestyle=ls)
    ax.add_patch(arrow)
    if label:
        lx = x1 + (x2 - x1) * label_pos + label_offset[0]
        ly = y1 + (y2 - y1) * label_pos + label_offset[1]
        ax.text(lx, ly, label, color=label_color, fontsize=label_size,
                fontweight='bold', ha='center', va='center', family='sans-serif',
                bbox=dict(boxstyle="round,pad=0.2", facecolor="#FFFFFF", edgecolor="#CBD5E0", alpha=0.9))

# ==============================================================================
# SƠ ĐỒ 1: KIẾN TRÚC TRIỂN KHAI TỔNG THỂ HỆ THỐNG
# ==============================================================================
def generate_diagram_1(out_path):
    fig, ax = setup_plot(16, 11)
    
    # Tiêu đề sơ đồ
    ax.text(50, 97, "SƠ ĐỒ KIẾN TRÚC TRIỂN KHAI TỔNG THỂ HỆ THỐNG (SYSTEM DEPLOYMENT ARCHITECTURE)",
            color="#002060", fontsize=14, fontweight='bold', ha='center', va='center')
    ax.text(50, 94.5, "Mô hình Modular Monolith (Spring Boot 3 / Java 21) kết hợp Microservice AI (FastAPI / Python) & Cổng bảo mật Cloudflare Tunnel",
            color="#4A5568", fontsize=10, ha='center', va='center')

    # Vùng 1: Tầng Máy khách (Client Layer)
    draw_box(ax, 2, 75, 96, 17, "TẦNG TRẢI NGHIỆM NGƯỜI DÙNG & MÁY KHÁCH (CLIENT & APPLICATION LAYER)", "",
             bg_color="#F8FAFC", border_color="#94A3B8", title_color="#334155", title_size=10, radius=2, border_style='--')
    
    draw_box(ax, 4, 77, 21, 12, "Customer Web SPA", "React 19 / Vite / Tailwind\n(https://www.dermascan.world)",
             bg_color="#E0F2FE", border_color="#0284C7", title_color="#0369A1", title_size=9.5, sub_size=8)
    draw_box(ax, 28, 77, 21, 12, "Spa Staff Reception", "React 19 / Vite / Lucide\nTiếp đón & Quản lý buồng trị liệu",
             bg_color="#E0F2FE", border_color="#0284C7", title_color="#0369A1", title_size=9.5, sub_size=8)
    draw_box(ax, 52, 77, 21, 12, "Mobile Multiplatform", "Flutter 3.x / Dart / Provider\n(Android / iOS / macOS / Windows)",
             bg_color="#DCFCE7", border_color="#16A34A", title_color="#15803D", title_size=9.5, sub_size=8)
    draw_box(ax, 76, 77, 20, 12, "Admin Management", "React 19 / SheetJS / Oxlint\nQuản trị kho, đơn hàng & tài chính",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#B45309", title_size=9.5, sub_size=8)

    # Vùng 2: Biên mạng & Cổng Cloudflare (Edge Layer)
    draw_box(ax, 20, 58, 60, 13, "BIÊN MẠNG TOÀN CẦU CLOUDFLARE (GLOBAL EDGE & ZERO-TRUST INGRESS)", "",
             bg_color="#FFFBEB", border_color="#F59E0B", title_color="#B45309", title_size=10, radius=2, border_style='--')
    draw_box(ax, 22, 60, 26, 8.5, "Cloudflare Edge Network", "Anycast CDN - WAF - Auto SSL/TLS\nChống tấn công DDoS L3/L4/L7",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#92400E", title_size=9, sub_size=7.5)
    draw_box(ax, 51, 60, 27, 8.5, "Cloudflare Tunnel (cloudflared)", "Đường hầm mã hóa TLS hướng ra ngoài\nZero Inbound Open Ports",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#92400E", title_size=9, sub_size=7.5)

    # Mũi tên từ Client đến Cloudflare
    draw_arrow(ax, 14.5, 77, 30, 68.5, color="#0284C7", width=1.5, label="HTTPS (443)", label_offset=(-2, 0))
    draw_arrow(ax, 62.5, 77, 62.5, 68.5, color="#16A34A", width=1.5, label="REST API (HTTPS)", label_offset=(4, 0))
    draw_arrow(ax, 86, 77, 70, 68.5, color="#D97706", width=1.5, label="HTTPS (443)", label_offset=(2, 0))

    # Vùng 3: Cổng Nginx Reverse Proxy
    draw_box(ax, 15, 43, 70, 11, "NGINX REVERSE PROXY & API GATEWAY (CONTAINER: beautyshop_nginx)",
             "Cổng 80 (Cổng duy nhất mở Inbound) | Phục hồi Real-IP Cloudflare | Giới hạn tần suất Leaky Bucket (30r/s & 5r/s)\nTối ưu Zero-copy Native File Streaming (/uploads/) | Tắt bộ đệm Streaming SSE (/api/v1/chatbot/chat/stream)",
             bg_color="#F1F5F9", border_color="#475569", title_color="#1E293B", title_size=10, sub_size=8)

    draw_arrow(ax, 64.5, 60, 50, 54, color="#D97706", width=2, label="Forward Request qua Tunnel", label_offset=(0, 2))

    # Vùng 4: Tầng Xử lý Nghiệp vụ & AI Service (Application & AI Layer)
    draw_box(ax, 2, 22, 58, 17, "CORE BACKEND MODULAR MONOLITH (beautyshop_backend)",
             "Spring Boot 3.4.3 | Java 21 LTS | 14 Bounded Contexts (DDD)\nKiểm soát ranh giới module bằng ArchUnit | Xác thực JJWT & Token Versioning\nTransactional Outbox Publisher | WebSocket STOMP Hub | Port 127.0.0.1:8080",
             bg_color="#EFF6FF", border_color="#1D4ED8", title_color="#1E40AF", title_size=10, sub_size=8)

    draw_box(ax, 63, 22, 35, 17, "AI MICROSERVICE & SEMANTIC SEARCH (beautyshop_chatbot)",
             "FastAPI | Uvicorn | Python 3.10+\nBộ phân tích hiểu ý định NLU 6 tầng | Streaming SSE\n3-Way Hybrid RAG (SQL + BM25 + Qdrant Vector)\nCLIP Visual Search (ViT-B-32) | Port 127.0.0.1:18000",
             bg_color="#FAF5FF", border_color="#7E22CE", title_color="#6B21A8", title_size=10, sub_size=8)

    # Mũi tên từ Nginx vào Backend và Frontend
    draw_arrow(ax, 35, 43, 31, 39, color="#1D4ED8", width=1.8, label="/api/*, /ws/*", label_offset=(-3, 0))
    draw_arrow(ax, 65, 43, 75, 39, color="#7E22CE", width=1.5, label="/api/v1/chatbot/chat/stream", label_offset=(4, 0))
    # Tương tác giữa Backend và Chatbot
    draw_arrow(ax, 60, 30.5, 63, 30.5, color="#4F46E5", width=1.8, label="HTTP RPC", label_offset=(0, 1.5))
    draw_arrow(ax, 63, 28, 60, 28, color="#4F46E5", width=1.5, label="Live Stock Check", label_offset=(0, -1.5), dashed=True)

    # Vùng 5: Tầng Dữ liệu, Lưu trữ, Message Queue & Giám sát
    draw_box(ax, 2, 2, 22, 16, "MySQL 8.0 (beautyshop_mysql)",
             "Relational DB (ACID)\n70 Flyway Migrations (V1-V70)\nInnoDB Buffer Pool 384MB\nBinding 127.0.0.1:3307",
             bg_color="#ECFDF5", border_color="#059669", title_color="#065F46", title_size=9.5, sub_size=7.5)

    draw_box(ax, 26, 2, 22, 16, "Redis 7.2 & Kafka 3.7",
             "Redis: L2 Cache & ShedLock\nKafka: KRaft Event Streaming\nOutbox Pattern Messages\nBinding 127.0.0.1:6379, 9092",
             bg_color="#ECFDF5", border_color="#059669", title_color="#065F46", title_size=9.5, sub_size=7.5)

    draw_box(ax, 50, 2, 22, 16, "Qdrant Vector DB & Model",
             "Lưu trữ Vector nhúng 768d\nChỉ mục ANN HNSW siêu tốc\nMô hình CLIP ViT-B-32 (512d)\nSQLite Agent Local Storage",
             bg_color="#FAF5FF", border_color="#7E22CE", title_color="#6B21A8", title_size=9.5, sub_size=7.5)

    draw_box(ax, 74, 2, 24, 16, "Giám sát APM (Prometheus/Grafana)",
             "Prometheus v2.51 (Scrape 5s)\nSpring Boot Actuator Metrics\nGrafana v10.4 Dashboards\nBinding 127.0.0.1:9090, 3000",
             bg_color="#FFF1F2", border_color="#E11D48", title_color="#9F1239", title_size=9.5, sub_size=7.5)

    # Mũi tên kết nối xuống tầng dữ liệu
    draw_arrow(ax, 13, 22, 13, 18, color="#059669", width=1.5, label="JDBC (JPA)")
    draw_arrow(ax, 37, 22, 37, 18, color="#059669", width=1.5, label="Cache & Outbox")
    draw_arrow(ax, 72, 22, 61, 18, color="#7E22CE", width=1.5, label="Vector Search")
    draw_arrow(ax, 50, 22, 80, 18, color="#E11D48", width=1.5, label="Metrics /actuator", dashed=True)

    plt.tight_layout()
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print("Tao thanh cong:", out_path)

# ==============================================================================
# SƠ ĐỒ 2: LUỒNG DỮ LIỆU VÀ TÍCH HỢP ĐA DỊCH VỤ
# ==============================================================================
def generate_diagram_2(out_path):
    fig, ax = setup_plot(16, 11)
    
    ax.text(50, 97, "SƠ ĐỒ LUỒNG XỬ LÝ DỮ LIỆU & TÍCH HỢP NGHIỆP VỤ ĐA DỊCH VỤ",
            color="#002060", fontsize=14, fontweight='bold', ha='center', va='center')
    ax.text(50, 94.5, "Minh họa 4 luồng nghiệp vụ cốt lõi: Thanh toán VietQR, Tư vấn AI Streaming, Visual Search và Sự kiện Kafka",
            color="#4A5568", fontsize=10, ha='center', va='center')

    # Luồng 1: Thanh toán VietQR SePay Webhook
    draw_box(ax, 2, 70, 96, 21, "LUỒNG 1: XÁC THỰC THANH TOÁN TỰ ĐỘNG VIETQR (SEPAY WEBHOOK & WEBSOCKET SYNC)", "",
             bg_color="#F0FDF4", border_color="#16A34A", title_color="#15803D", title_size=10.5, radius=1.5, border_style='solid')
    draw_box(ax, 4, 72, 17, 13, "Ngân hàng MBBank\n& SePay Gateway", "Khách quét VietQR\nMBBank bắn Webhook",
             bg_color="#DCFCE7", border_color="#16A34A", title_color="#14532D", title_size=9, sub_size=7.5)
    draw_box(ax, 26, 72, 20, 13, "Nginx Ingress Gateway", "Xác thực IP Cloudflare\nChuyển tiếp Webhook an toàn",
             bg_color="#DCFCE7", border_color="#16A34A", title_color="#14532D", title_size=9, sub_size=7.5)
    draw_box(ax, 51, 72, 23, 13, "Spring Backend Payment", "So sánh khóa bằng MessageDigest\nCập nhật trạng thái PAID trong DB",
             bg_color="#DCFCE7", border_color="#16A34A", title_color="#14532D", title_size=9, sub_size=7.5)
    draw_box(ax, 79, 72, 17, 13, "Web/App Client UI", "Nhận tin qua WebSocket STOMP\nChuyển sang trang Hóa đơn tức thì",
             bg_color="#DCFCE7", border_color="#16A34A", title_color="#14532D", title_size=9, sub_size=7.5)
    
    draw_arrow(ax, 21, 78.5, 26, 78.5, color="#15803D", width=1.5, label="POST Webhook")
    draw_arrow(ax, 46, 78.5, 51, 78.5, color="#15803D", width=1.5, label="Proxy Pass")
    draw_arrow(ax, 74, 78.5, 79, 78.5, color="#15803D", width=1.5, label="WebSocket Push")

    # Luồng 2: Tư vấn AI & Advanced Hybrid RAG
    draw_box(ax, 2, 45, 96, 22, "LUỒNG 2: TƯ VẤN DA LIỄU THỜI GIAN THỰC (ADVANCED HYBRID RAG & SSE TOKEN STREAMING)", "",
             bg_color="#FAF5FF", border_color="#9333EA", title_color="#7E22CE", title_size=10.5, radius=1.5, border_style='solid')
    draw_box(ax, 4, 47, 15, 14, "Người dùng\n(Hỏi tư vấn)", "Nhập câu hỏi tự nhiên\n(Loại da, hoạt chất, mụn)",
             bg_color="#F3E8FF", border_color="#9333EA", title_color="#581C87", title_size=9, sub_size=7.5)
    draw_box(ax, 22, 47, 17, 14, "Nginx Proxy\n(Buffering: OFF)", "Bật X-Accel-Buffering: no\nTruyền phát dòng SSE trực tiếp",
             bg_color="#F3E8FF", border_color="#9333EA", title_color="#581C87", title_size=9, sub_size=7.5)
    draw_box(ax, 42, 47, 21, 14, "AI Agent Orchestrator", "Phân tích NLU 6 tầng\nFast-path Router (<5ms)\nEntity Memory đa lượt",
             bg_color="#F3E8FF", border_color="#9333EA", title_color="#581C87", title_size=9, sub_size=7.5)
    draw_box(ax, 66, 47, 17, 14, "3-Way Hybrid RAG", "Kênh 1: SQL Filter\nKênh 2: BM25 Lexical\nKênh 3: Qdrant Vector (768d)\nHợp nhất thứ hạng RRF (k=60)",
             bg_color="#F3E8FF", border_color="#9333EA", title_color="#581C87", title_size=8.5, sub_size=7)
    draw_box(ax, 86, 47, 10, 14, "Gemini LLM\n& Live Guard", "Lọc tồn kho thật\nSinh token SSE",
             bg_color="#F3E8FF", border_color="#9333EA", title_color="#581C87", title_size=8.5, sub_size=7)

    draw_arrow(ax, 19, 54, 22, 54, color="#7E22CE", width=1.5, label="Chat Req")
    draw_arrow(ax, 39, 54, 42, 54, color="#7E22CE", width=1.5, label="Stream RPC")
    draw_arrow(ax, 63, 54, 66, 54, color="#7E22CE", width=1.5, label="Query")
    draw_arrow(ax, 83, 54, 86, 54, color="#7E22CE", width=1.5, label="Prompt")

    # Luồng 3: Tìm kiếm hình ảnh bằng CLIP
    draw_box(ax, 2, 23, 96, 19, "LUỒNG 3: TÌM KIẾM SẢN PHẨM BẰNG HÌNH ẢNH (CLIP VISUAL SEARCH & INVENTORY MATCH)", "",
             bg_color="#EFF6FF", border_color="#2563EB", title_color="#1D4ED8", title_size=10.5, radius=1.5, border_style='solid')
    draw_box(ax, 4, 25, 17, 13, "Tải ảnh sản phẩm", "Kiểm tra kích thước <=5MB\nMagic bytes: JPG/PNG/WebP",
             bg_color="#DBEAFE", border_color="#2563EB", title_color="#1E3A8A", title_size=9, sub_size=7.5)
    draw_box(ax, 26, 25, 21, 13, "CLIP Vision Encoder", "Trích xuất đặc trưng 512d\nChuẩn hóa L2 vector ảnh",
             bg_color="#DBEAFE", border_color="#2563EB", title_color="#1E3A8A", title_size=9, sub_size=7.5)
    draw_box(ax, 52, 25, 23, 13, "Cosine Index Matching", "So khớp kho vector index.npz\nTrích xuất Top-K sản phẩm",
             bg_color="#DBEAFE", border_color="#2563EB", title_color="#1E3A8A", title_size=9, sub_size=7.5)
    draw_box(ax, 80, 25, 16, 13, "Lọc kho & Hiển thị", "Loại bỏ hàng hết hoặc cận hạn\nTrả kết quả kèm giá bán",
             bg_color="#DBEAFE", border_color="#2563EB", title_color="#1E3A8A", title_size=9, sub_size=7.5)

    draw_arrow(ax, 21, 31.5, 26, 31.5, color="#1D4ED8", width=1.5, label="Upload")
    draw_arrow(ax, 47, 31.5, 52, 31.5, color="#1D4ED8", width=1.5, label="Vector 512d")
    draw_arrow(ax, 75, 31.5, 80, 31.5, color="#1D4ED8", width=1.5, label="Product IDs")

    # Luồng 4: Transactional Outbox & Apache Kafka
    draw_box(ax, 2, 2, 96, 18, "LUỒNG 4: PHÂN PHỐI SỰ KIỆN PHÂN TÁN (TRANSACTIONAL OUTBOX PATTERN & APACHE KAFKA)", "",
             bg_color="#FFFBEB", border_color="#D97706", title_color="#B45309", title_size=10.5, radius=1.5, border_style='solid')
    draw_box(ax, 4, 4, 21, 12, "Tác vụ Nghiệp vụ Lõi", "Đặt đơn hàng / Dùng vé Spa\nGhi bảng chính + Bảng Outbox\n(Cùng 1 giao dịch ACID MySQL)",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#78350F", title_size=9, sub_size=7.5)
    draw_box(ax, 30, 4, 20, 12, "Outbox Message Poller", "Quét thông điệp trạng thái PENDING\nĐẩy lên Apache Kafka Broker\nCập nhật SENT hoặc FAILED",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#78350F", title_size=9, sub_size=7.5)
    draw_box(ax, 55, 4, 18, 12, "Kafka Broker (KRaft)", "Topic: order-events\nTopic: spa-notifications\nĐảm bảo phân phối At-least-once",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#78350F", title_size=9, sub_size=7.5)
    draw_box(ax, 78, 4, 18, 12, "Các Event Consumers", "Trừ tồn kho tự động\nTích điểm thành viên VIP\nGửi Email xác nhận qua SMTP",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#78350F", title_size=9, sub_size=7.5)

    draw_arrow(ax, 25, 10, 30, 10, color="#B45309", width=1.5, label="Outbox Read")
    draw_arrow(ax, 50, 10, 55, 10, color="#B45309", width=1.5, label="Produce")
    draw_arrow(ax, 73, 10, 78, 10, color="#B45309", width=1.5, label="Consume")

    plt.tight_layout()
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print("Tao thanh cong:", out_path)

# ==============================================================================
# SƠ ĐỒ 3: PHÂN TẦNG AN NINH & CÔ LẬP MẠNG
# ==============================================================================
def generate_diagram_3(out_path):
    fig, ax = setup_plot(16, 10)
    
    ax.text(50, 97, "SƠ ĐỒ PHÂN TẦNG BẢO MẬT & CÔ LẬP HẠ TẦNG MẠNG (ZERO-TRUST NETWORK ISOLATION)",
            color="#002060", fontsize=14, fontweight='bold', ha='center', va='center')
    ax.text(50, 94.5, "Mô hình 4 lớp phòng thủ: Internet công cộng -> Cloudflare Edge -> Nginx Security Gateway -> Docker Subnet cô lập",
            color="#4A5568", fontsize=10, ha='center', va='center')

    # Phân vùng 1: Internet công cộng
    draw_box(ax, 3, 20, 18, 70, "VÙNG 1: INTERNET CÔNG CỘNG\n(UNTRUSTED ZONE)",
             "Khách hàng Web & Mobile\nNgười dùng vãng lai\nKẻ tấn công dò quét IP\nRobot & Crawler tự động\n\nToàn bộ lưu lượng xuất phát\ntừ mạng công cộng đều không\nđược phép đi thẳng vào máy chủ",
             bg_color="#FFF1F2", border_color="#E11D48", title_color="#9F1239", title_size=10, sub_size=8.5, radius=2)

    # Phân vùng 2: Cloudflare Zero Trust Edge
    draw_box(ax, 25, 20, 21, 70, "VÙNG 2: BIÊN MẠNG CLOUDFLARE\n(EDGE DEFENSE ZONE)",
             "Hơn 300 Trung tâm dữ liệu toàn cầu\n\n• Miễn nhiễm tấn công DDoS L3/L4/L7\n• Tường lửa WAF phát hiện SQLi/XSS\n• Tự động cấp phát chứng chỉ SSL/TLS\n• Ẩn giấu hoàn toàn địa chỉ IP gốc\n• Cloudflare Tunnel (cloudflared):\n  Thiết lập 4 luồng kết nối Outbound\n  an toàn về máy chủ nội bộ\n\n(Không mở cổng Inbound trên Firewall)",
             bg_color="#FEF3C7", border_color="#D97706", title_color="#92400E", title_size=10, sub_size=8.5, radius=2)

    # Phân vùng 3: Nginx Reverse Proxy DMZ
    draw_box(ax, 50, 20, 22, 70, "VÙNG 3: NGINX GATEWAY DMZ\n(TRAFFIC FILTERING ZONE)",
             "Chỉ duy nhất cổng 80 mở Inbound\n\n• Lọc và khôi phục Real-IP thật\n  từ 15 dải IPv4 Cloudflare\n• Giới hạn tần suất Leaky Bucket:\n  - API chung: 30 req/s (burst 50)\n  - API Auth: 5 req/s (chống Brute-force)\n• Chặn đứng truy cập Actuator:\n  Request /actuator/ từ Internet\n  bị từ chối bằng HTTP 403 Forbidden\n• Bổ sung 4 Security Headers\n• Tối ưu SSE & Stream ảnh tệp tĩnh",
             bg_color="#EFF6FF", border_color="#2563EB", title_color="#1E40AF", title_size=10, sub_size=8.5, radius=2)

    # Phân vùng 4: Mạng nội bộ Docker cô lập (127.0.0.1)
    draw_box(ax, 76, 20, 21, 70, "VÙNG 4: MẠNG NỘI BỘ DOCKER\n(ISOLATED PRIVATE ZONE)",
             "Toàn bộ cổng gắn với 127.0.0.1\n\n• Core Backend: 127.0.0.1:8080\n• Chatbot Python: 127.0.0.1:18000\n• MySQL Database: 127.0.0.1:3307\n• Redis Cache: 127.0.0.1:6379\n• Kafka Message: 127.0.0.1:9092\n• Prometheus APM: 127.0.0.1:9090\n• Grafana Dashboard: 127.0.0.1:3000\n\nKhông một thiết bị nào trong LAN\nhoặc Internet có thể kết nối trực tiếp",
             bg_color="#F0FDF4", border_color="#16A34A", title_color="#166534", title_size=10, sub_size=8.5, radius=2)

    # Các mũi tên luồng bảo mật giữa các vùng
    draw_arrow(ax, 21, 65, 25, 65, color="#E11D48", width=2, label="Truy cập Web/App")
    draw_arrow(ax, 46, 65, 50, 65, color="#D97706", width=2, label="Đường hầm Tunnel")
    draw_arrow(ax, 72, 65, 76, 65, color="#2563EB", width=2, label="Nội bộ Docker Net")

    # Mũi tên bị chặn (Block Red Arrow)
    draw_arrow(ax, 21, 35, 76, 35, color="#DC2626", width=2, label="CHẶN TUYỆT ĐỐI KẾT NỐI TRỰC TIẾP", dashed=True)
    ax.text(48, 38, "X Bị chặn bởi Tường lửa & Port Binding 127.0.0.1", color="#DC2626", fontsize=9, fontweight='bold', ha='center')

    # Hộp thông tin bổ sung phía dưới
    draw_box(ax, 3, 3, 94, 13, "KẾT QUẢ ĐẠT ĐƯỢC VỀ AN TOÀN HẠ TẦNG KHI PUBLIC RA TOÀN CẦU",
             "1. Triệt tiêu hoàn toàn rủi ro bị tấn công trực diện vào Cơ sở dữ liệu và Message Broker nhờ cơ chế cô lập cổng nội bộ.\n2. Ẩn giấu hoàn toàn máy chủ thật; giảm 100% nguy cơ DDoS mạng nhờ năng lực phân tán của hơn 300 điểm PoP Cloudflare.\n3. Ngăn ngừa triệt để các cuộc tấn công Brute-force mật khẩu và khai thác lộ lọt thông tin đo lường Actuator.\n4. Đảm bảo toàn vẹn dữ liệu giao dịch ngân hàng VietQR qua thuật toán so sánh khóa thời gian không đổi MessageDigest.",
             bg_color="#F8FAFC", border_color="#64748B", title_color="#0F172A", title_size=10, sub_size=8.5)

    plt.tight_layout()
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print("Tao thanh cong:", out_path)

# ==============================================================================
# SƠ ĐỒ 4: QUY TRÌNH CI/CD & ĐIỀU PHỐI DỊCH VỤ DOCKER COMPOSE
# ==============================================================================
def generate_diagram_4(out_path):
    fig, ax = setup_plot(16, 11)
    
    ax.text(50, 97, "SƠ ĐỒ QUY TRÌNH TỰ ĐỘNG HÓA CI/CD & CHU TRÌNH ĐIỀU PHỐI KHỞI ĐỘNG HỆ THỐNG",
            color="#002060", fontsize=14, fontweight='bold', ha='center', va='center')
    ax.text(50, 94.5, "Quy trình kiểm thử 5 bước trên GitHub Actions & Chuỗi khởi động phụ thuộc có Healthcheck trên Docker Compose",
            color="#4A5568", fontsize=10, ha='center', va='center')

    # Phần trên: Pipeline CI/CD GitHub Actions
    draw_box(ax, 2, 53, 96, 38, "PHẦN A: PIPELINE TỰ ĐỘNG HÓA TÍCH HỢP & PHÁT HÀNH (GITHUB ACTIONS CI/CD WORKFLOW)", "",
             bg_color="#F8FAFC", border_color="#3B82F6", title_color="#1D4ED8", title_size=10.5, radius=2, border_style='solid')
    
    draw_box(ax, 4, 57, 16, 28, "Job 1: Backend CI\n(Java 21 & Maven)",
             "• Setup JDK 21 Temurin\n• Biên dịch mã nguồn\n• Unit Tests (JUnit 5)\n• ArchUnit Tests:\n  - Kiểm tra Bounded Context\n  - Chặn Cross-Repo Access\n  - Kiểm tra ranh giới DDD",
             bg_color="#EFF6FF", border_color="#3B82F6", title_color="#1E40AF", title_size=9.5, sub_size=8)
    
    draw_box(ax, 23, 57, 16, 28, "Job 2: Docker Build\n(Buildx Engine)",
             "• Setup Docker Buildx\n• Cache tầng lớp GitHub\n• Multi-stage Build:\n  - Eclipse Temurin JRE\n  - Tối ưu hóa kích thước\n• Tạo image backend\n  không kèm secrets",
             bg_color="#EFF6FF", border_color="#3B82F6", title_color="#1E40AF", title_size=9.5, sub_size=8)
    
    draw_box(ax, 42, 57, 17, 28, "Job 3: DevOps Valid\n(Infra Checks)",
             "• Validate cú pháp\n  Docker Compose v2\n• Kiểm tra cấu hình Nginx:\n  - Thử nghiệm trong container\n  - Cú pháp lệnh nginx -t\n• Đối soát biến môi trường",
             bg_color="#EFF6FF", border_color="#3B82F6", title_color="#1E40AF", title_size=9.5, sub_size=8)

    draw_box(ax, 62, 57, 16, 28, "Job 4: Mobile CI\n(Flutter Test)",
             "• Setup Flutter 3.x\n• Cài đặt dependencies\n• Flutter Analyze:\n  - Rà soát cảnh báo code\n• Chạy Flutter Tests:\n  - Parity Tests\n  - Appointment Tests",
             bg_color="#DCFCE7", border_color="#16A34A", title_color="#15803D", title_size=9.5, sub_size=8)

    draw_box(ax, 81, 57, 15, 28, "Job 5: Frontend CI\n(Vite Build)",
             "• Setup Node.js 20/24\n• Cài đặt npm dependencies\n• Kiểm tra biến VITE_*\n  chặn lộ lọt secret\n• Vite Production Build\n• Xuất bản thư mục dist",
             bg_color="#E0F2FE", border_color="#0284C7", title_color="#0369A1", title_size=9.5, sub_size=8)

    draw_arrow(ax, 20, 71, 23, 71, color="#3B82F6", width=1.8)
    draw_arrow(ax, 39, 71, 42, 71, color="#3B82F6", width=1.8)
    draw_arrow(ax, 59, 71, 62, 71, color="#3B82F6", width=1.8)
    draw_arrow(ax, 78, 71, 81, 71, color="#3B82F6", width=1.8)

    # Phần dưới: Chuỗi điều phối khởi động dịch vụ Docker Compose
    draw_box(ax, 2, 2, 96, 47, "PHẦN B: CHU TRÌNH ĐIỀU PHỐI KHỞI ĐỘNG DỊCH VỤ ĐỒNG BỘ (DOCKER COMPOSE ORCHESTRATION)", "",
             bg_color="#FAF5FF", border_color="#8B5CF6", title_color="#6D28D9", title_size=10.5, radius=2, border_style='solid')

    draw_box(ax, 4, 6, 20, 36, "Bước 1: Hạ tầng Dữ liệu\n(Data Storage Stage)",
             "Khởi động song song 3 dịch vụ:\n• MySQL 8.0: Chờ ping sẵn sàng\n• Redis 7.2: Chờ redis-cli ping\n• Kafka 3.7: Chờ tạo topic KRaft\n\n[Healthcheck OK: ~15-20s]",
             bg_color="#F3E8FF", border_color="#8B5CF6", title_color="#5B21B6", title_size=9.5, sub_size=8)

    draw_box(ax, 28, 6, 21, 36, "Bước 2: Backend & Di trú\n(Migration & API Stage)",
             "• Nạp biến cấu hình từ .env\n• Flyway kích hoạt:\n  Chạy tuần tự 70 migrations\n• Khởi tạo Hibernate JPA\n• Mở kết nối Redis & Kafka\n• Sẵn sàng phục vụ Actuator\n\n[Healthcheck OK: ~30-45s]",
             bg_color="#F3E8FF", border_color="#8B5CF6", title_color="#5B21B6", title_size=9.5, sub_size=8)

    draw_box(ax, 53, 6, 21, 36, "Bước 3: AI & Web Frontend\n(AI & Presentation Stage)",
             "• Chatbot nạp cấu hình Gemini\n• Nạp mô hình nhúng & Qdrant\n• Đồng bộ tồn kho từ MySQL\n• Frontend nạp bản build tĩnh\n• Khởi động máy chủ web cục bộ\n\n[Healthcheck OK: ~15s]",
             bg_color="#F3E8FF", border_color="#8B5CF6", title_color="#5B21B6", title_size=9.5, sub_size=8)

    draw_box(ax, 78, 6, 18, 36, "Bước 4: Cổng & Giám sát\n(Gateway & Monitoring)",
             "• Nginx kiểm tra upstream\n• Mở cổng 80 tiếp nhận traffic\n• Prometheus bắt đầu scrape\n• Grafana kích hoạt Dashboard\n• Khởi chạy Cloudflare Tunnel\n\n[TOÀN HỆ THỐNG ONLINE]",
             bg_color="#ECFDF5", border_color="#10B981", title_color="#047857", title_size=9.5, sub_size=8)

    draw_arrow(ax, 24, 24, 28, 24, color="#8B5CF6", width=2, label="DB Healthy")
    draw_arrow(ax, 49, 24, 53, 24, color="#8B5CF6", width=2, label="API Healthy")
    draw_arrow(ax, 74, 24, 78, 24, color="#10B981", width=2, label="Services OK")

    plt.tight_layout()
    plt.savefig(out_path, dpi=300, bbox_inches='tight')
    plt.close()
    print("Tao thanh cong:", out_path)

if __name__ == "__main__":
    out_dir = r"D:\khoaluantotnghiep\scripts\docs\diagrams"
    generate_diagram_1(os.path.join(out_dir, "diagram_1_deployment_architecture.png"))
    generate_diagram_2(os.path.join(out_dir, "diagram_2_data_flow_integration.png"))
    generate_diagram_3(os.path.join(out_dir, "diagram_3_security_network_isolation.png"))
    generate_diagram_4(os.path.join(out_dir, "diagram_4_orchestration_ci_cd.png"))
