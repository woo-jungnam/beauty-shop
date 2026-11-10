# -*- coding: utf-8 -*-
"""
Script tao tai lieu Word (DOCX) chuyen sau ve Kien truc, Cong nghe, Ngon ngu va Quy trinh Trien khai he thong.
Dinh dang chuan khoa luan tot nghiep Dai hoc.
Luu vao thu muc dich: C:\\Users\\NGUYEN THANH NAM\\OneDrive\\Desktop\\kk
"""

import os
import sys
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml, OxmlElement
from docx.oxml.ns import nsdecls, qn

def set_cell_border(cell, **kwargs):
    """Thiet lap vien bang tuy bien"""
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(f'''
        <w:tcBorders {nsdecls("w")}>
            <w:top w:val="{kwargs.get('top', 'single')}" w:sz="{kwargs.get('top_sz', '4')}" w:space="0" w:color="{kwargs.get('top_color', 'auto')}"/>
            <w:left w:val="{kwargs.get('left', 'single')}" w:sz="{kwargs.get('left_sz', '4')}" w:space="0" w:color="{kwargs.get('left_color', 'auto')}"/>
            <w:bottom w:val="{kwargs.get('bottom', 'single')}" w:sz="{kwargs.get('bottom_sz', '4')}" w:space="0" w:color="{kwargs.get('bottom_color', 'auto')}"/>
            <w:right w:val="{kwargs.get('right', 'single')}" w:sz="{kwargs.get('right_sz', '4')}" w:space="0" w:color="{kwargs.get('right_color', 'auto')}"/>
        </w:tcBorders>
    ''')
    tcPr.append(tcBorders)

def set_cell_background(cell, fill_hex):
    """Dat mau nen cho o trong bang"""
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    cell._tc.get_or_add_tcPr().append(shd)

def generate_report():
    doc = docx.Document()

    # 1. Cau hinh trang chuan A4 va le khoa luan
    for section in doc.sections:
        section.page_width = Inches(8.27)
        section.page_height = Inches(11.69)
        section.top_margin = Inches(0.79)    # 2.0 cm
        section.bottom_margin = Inches(0.79) # 2.0 cm
        section.left_margin = Inches(1.18)   # 3.0 cm
        section.right_margin = Inches(0.79)  # 2.0 cm

        # Header & Footer
        header = section.header
        hp = header.paragraphs[0]
        hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        hrun = hp.add_run("KHÓA LUẬN TỐT NGHIỆP: HỆ THỐNG DERMASCAN / BEAUTYSHOP")
        hrun.font.name = 'Times New Roman'
        hrun.font.size = Pt(8.5)
        hrun.italic = True
        hrun.font.color.rgb = RGBColor(128, 128, 128)

        footer = section.footer
        fp = footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        frun = fp.add_run("Báo cáo Kỹ thuật: Kiến trúc hệ thống, Công nghệ, Khả năng Public & Quy trình Triển khai")
        frun.font.name = 'Times New Roman'
        frun.font.size = Pt(8.5)
        frun.font.color.rgb = RGBColor(128, 128, 128)

    # 2. Cau hinh Normal Style
    normal = doc.styles['Normal']
    normal.font.name = 'Times New Roman'
    normal.font.size = Pt(13)
    normal.font.color.rgb = RGBColor(0, 0, 0)
    normal.paragraph_format.line_spacing = 1.3
    normal.paragraph_format.space_after = Pt(4)
    normal.paragraph_format.space_before = Pt(0)

    # Helper functions
    def add_title(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(18)
        p.paragraph_format.space_after = Pt(6)
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(18)
        run.font.color.rgb = RGBColor(0, 32, 96) # Navy Blue
        return p

    def add_subtitle(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(16)
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(14)
        run.font.color.rgb = RGBColor(80, 80, 80)
        return p

    def add_h1(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(16)
        p.paragraph_format.space_after = Pt(6)
        p.paragraph_format.keep_with_next = True
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(15)
        run.font.color.rgb = RGBColor(0, 32, 96)
        return p

    def add_h2(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(12)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.keep_with_next = True
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(13.5)
        run.font.color.rgb = RGBColor(0, 32, 96)
        return p

    def add_h3(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(8)
        p.paragraph_format.space_after = Pt(3)
        p.paragraph_format.keep_with_next = True
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(13)
        run.font.color.rgb = RGBColor(30, 30, 30)
        return p

    def add_p(text, bold_prefix=None, italic_prefix=None):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        p.paragraph_format.line_spacing = 1.3
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.first_line_indent = Inches(0.4)
        if bold_prefix:
            rb = p.add_run(bold_prefix)
            rb.bold = True
            rb.font.name = 'Times New Roman'
            rb.font.size = Pt(13)
        if italic_prefix:
            ri = p.add_run(italic_prefix)
            ri.italic = True
            ri.font.name = 'Times New Roman'
            ri.font.size = Pt(13)
        r = p.add_run(text)
        r.font.name = 'Times New Roman'
        r.font.size = Pt(13)
        return p

    def add_callout(text, bold_label="LƯU Ý QUAN TRỌNG:"):
        table = doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = False
        cell = table.cell(0, 0)
        cell.width = Inches(6.3)
        set_cell_background(cell, "F4F6F9")
        set_cell_border(cell, top="none", bottom="none", right="none", left="single", left_sz="24", left_color="002060")
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.2
        if bold_label:
            r_lbl = p.add_run(bold_label + " ")
            r_lbl.bold = True
            r_lbl.font.name = 'Times New Roman'
            r_lbl.font.size = Pt(11.5)
            r_lbl.font.color.rgb = RGBColor(0, 32, 96)
        r_txt = p.add_run(text)
        r_txt.font.name = 'Times New Roman'
        r_txt.font.size = Pt(11.5)
        r_txt.font.color.rgb = RGBColor(40, 40, 40)
        doc.add_paragraph().paragraph_format.space_after = Pt(2)

    def add_code_block(code_text):
        table = doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = False
        cell = table.cell(0, 0)
        cell.width = Inches(6.3)
        set_cell_background(cell, "2D3748") # Dark Slate Gray
        set_cell_border(cell, top="single", bottom="single", left="single", right="single",
                        top_color="1A202C", bottom_color="1A202C", left_color="1A202C", right_color="1A202C")
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = 1.15
        r = p.add_run(code_text)
        r.font.name = 'Consolas'
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(240, 240, 240)
        doc.add_paragraph().paragraph_format.space_after = Pt(2)

    def add_table_custom(headers, rows_data, col_widths, alignments=None):
        table = doc.add_table(rows=1, cols=len(headers))
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = False

        # Header Row
        hdr_cells = table.rows[0].cells
        for i, title in enumerate(headers):
            hdr_cells[i].width = col_widths[i]
            p = hdr_cells[i].paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(title)
            r.bold = True
            r.font.name = 'Times New Roman'
            r.font.size = Pt(10.5)
            r.font.color.rgb = RGBColor(255, 255, 255)
            set_cell_background(hdr_cells[i], "002060")
            set_cell_border(hdr_cells[i], top="single", bottom="single", left="single", right="single",
                            top_color="002060", bottom_color="002060", left_color="002060", right_color="002060")

        # Data Rows
        for row_idx, row_values in enumerate(rows_data):
            row = table.add_row()
            bg_color = "F2F5F9" if row_idx % 2 == 1 else "FFFFFF"
            for col_idx, val in enumerate(row_values):
                cell = row.cells[col_idx]
                cell.width = col_widths[col_idx]
                cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
                set_cell_background(cell, bg_color)
                p = cell.paragraphs[0]
                if alignments and col_idx < len(alignments):
                    p.alignment = alignments[col_idx]
                else:
                    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                r = p.add_run(val)
                r.font.name = 'Times New Roman'
                r.font.size = Pt(10)
                set_cell_border(cell, top="single", bottom="single", left="single", right="single",
                                top_color="D3D3D3", bottom_color="D3D3D3", left_color="D3D3D3", right_color="D3D3D3")
        doc.add_paragraph().paragraph_format.space_after = Pt(4)

    # ==================== NỘI DUNG CHI TIẾT BÁO CÁO ====================

    # TIÊU ĐỀ CHÍNH
    add_title("BÁO CÁO KỸ THUẬT & KIẾN TRÚC HỆ THỐNG")
    add_subtitle("HỆ THỐNG THƯƠNG MẠI ĐIỆN TỬ DƯỢC MỸ PHẨM & QUẢN LÝ DỊCH VỤ PHÒNG KHÁM SPA CLINIC TÍCH HỢP TRÍ TUỆ NHÂN TẠO (DERMASCAN / BEAUTYSHOP)")

    add_callout(
        "Báo cáo này được trích xuất và tổng hợp trực tiếp từ toàn bộ cấu trúc mã nguồn, cấu hình hạ tầng, "
        "kịch bản điều phối container, chính sách bảo mật mạng và tài liệu kỹ thuật của dự án tại thư mục D:\\khoaluantotnghiep. "
        "Tài liệu phân tích chuyên sâu 5 nội dung trọng tâm: (1) Cách thức tổ chức và triển khai kiến trúc hệ thống; "
        "(2) Toàn bộ các ngôn ngữ lập trình được sử dụng; (3) Toàn bộ hệ sinh thái công nghệ, thư viện, framework và công cụ; "
        "(4) Trạng thái Public ra Internet và giải pháp Cloudflare Tunnel bảo mật; "
        "(5) Hướng dẫn chi tiết từng bước setup và quy trình triển khai hoàn chỉnh.",
        bold_label="MỤC TIÊU VÀ PHẠM VI BÁO CÁO:"
    )

    # -------------------------------------------------------------------------
    # PHẦN 1: TỔNG QUAN HỆ THỐNG VÀ KIẾN TRÚC TRIỂN KHAI
    # -------------------------------------------------------------------------
    add_h1("PHẦN 1: TỔNG QUAN HỆ THỐNG VÀ KIẾN TRÚC TRIỂN KHAI")

    add_h2("1.1. Giới thiệu tổng quan hệ sinh thái DermaScan / BeautyShop")
    add_p(
        "Hệ thống DermaScan (mã nguồn định danh: BeautyShop) là một nền tảng chuyển đổi số toàn diện kết hợp chặt chẽ giữa hai phân hệ nghiệp vụ chính: "
        "(1) Bán lẻ Thương mại điện tử Dược mỹ phẩm chính hãng (E-Commerce) và "
        "(2) Quản lý Vận hành Dịch vụ Trị liệu Da liễu Y khoa (Clinical Spa Management). "
        "Hệ thống không chỉ phục vụ các giao dịch bán hàng tiêu chuẩn mà còn giải quyết bài toán phức tạp về quản lý liệu trình spa nhiều buổi (Treatment Courses), "
        "điều phối phòng trị liệu, quản lý lịch làm việc kỹ thuật viên/bác sĩ, theo dõi hao phí vật tư y tế, quản lý lô hàng và hạn sử dụng theo chuẩn FEFO "
        "(First Expired, First Out), tích hợp hệ thống thanh toán tự động VietQR qua Webhook ngân hàng thời gian thực."
    )
    add_p(
        "Đặc biệt, hệ thống được trang bị hai công nghệ Trí tuệ Nhân tạo đột phá: Trợ lý ảo tư vấn da liễu thông minh ứng dụng Advanced RAG "
        "(Retrieval-Augmented Generation đa kênh) tích hợp Mô hình ngôn ngữ lớn (LLM), và Phân hệ Tìm kiếm sản phẩm bằng hình ảnh (Visual Product Search) "
        "sử dụng mô hình thị giác ngôn ngữ CLIP (Contrastive Language-Image Pre-training)."
    )

    add_h2("1.2. Mô hình kiến trúc Modular Monolith kết hợp AI Microservice")
    add_p(
        "Trong thiết kế kiến trúc phần mềm, hệ thống áp dụng mô hình Modular Monolith (Monolith hướng Module) kết hợp Domain-Driven Design (DDD) "
        "thực dụng cho phần Core Backend, đồng thời tách biệt Phân hệ AI Chatbot thành một Microservice độc lập. "
        "Sự kết hợp này mang lại các lợi thế vượt trội:",
        bold_prefix="Triết lý thiết kế kiến trúc: "
    )
    add_p(
        " Thay vì phân tách thành hàng chục microservices phân tán gây phức tạp hóa hạ tầng, độ trễ mạng và rủi ro phân mảnh giao dịch, "
        "Backend của hệ thống được đóng gói trong một ứng dụng duy nhất nhưng phân chia ranh giới ngữ cảnh (Bounded Contexts) hoàn toàn độc lập gồm 14 modules chức năng: "
        "cart (giỏ hàng), catalog (danh mục & sản phẩm), chatbot (cầu nối AI), crm (chăm sóc khách hàng & phân quyền), dashboard (thống kê phân tích), "
        "identity (xác thực & phiên người dùng), inventory (kho vận, lô hàng & hạn sử dụng), notification (thông báo khách hàng), order (đơn hàng thương mại), "
        "payment (thanh toán & webhook ngân hàng), procurement (nhập hàng & nhà cung cấp), promotion (voucher & flash sale), review (đánh giá sản phẩm), "
        "và spa (vận hành phòng khám, lịch hẹn, liệu trình điều trị, tiêu hao vật tư).",
        bold_prefix="1. Modular Monolith với 14 Bounded Contexts: "
    )
    add_p(
        " Mỗi module tuân thủ cấu trúc 4 tầng chuẩn DDD: API (Controllers, DTOs) -> Application (Use Cases, Services, Facades) -> "
        "Domain (Entities, Value Objects, Domain Events) -> Infrastructure (Repositories, External Adapters). "
        "Hệ thống nghiêm cấm tuyệt đối việc truy cập chéo Repository hoặc truy cập trực tiếp Domain Entity giữa các module. "
        "Mọi giao tiếp liên module bắt buộc phải thông qua Facade công khai hoặc bắn Domain Event.",
        bold_prefix="2. Kiểm soát ranh giới nghiêm ngặt bằng ArchUnit: "
    )
    add_p(
        " Phân hệ Trợ lý ảo AI và Tìm kiếm hình ảnh được tách biệt thành một Service riêng biệt chạy bằng Python/FastAPI. "
        "Lý do là phân hệ này đòi hỏi các thư viện tính toán khoa học chuyên sâu (PyTorch, Transformers, NumPy, Qdrant Client, BM25) "
        "và có chu kỳ tiêu thụ CPU/RAM khác biệt với luồng xử lý Web I/O của Backend Spring Boot.",
        bold_prefix="3. AI Microservice chuyên biệt: "
    )

    add_h2("1.3. Sơ đồ kiến trúc hạ tầng và các phân lớp triển khai (Layered Deployment Architecture)")
    add_p(
        "Toàn bộ hệ thống được container hóa và điều phối trên hạ tầng Docker Compose bao gồm 8 dịch vụ cốt lõi, "
        "kết nối thông qua mạng nội bộ 'beautyshop_net' và được định tuyến bảo mật qua Nginx Reverse Proxy:"
    )

    infra_headers = ["Tên Container / Dịch vụ", "Image / Công nghệ", "Port Nội bộ", "Port Host", "Vai trò và Chức năng chính"]
    infra_widths = [Inches(1.6), Inches(1.3), Inches(1.0), Inches(1.0), Inches(2.4)]
    infra_rows = [
        ["beautyshop_nginx", "nginx:alpine", "80/tcp", "80 (Public)", "Reverse Proxy, API Gateway, SSL Termination, Rate Limiting, Static Asset Streaming"],
        ["beautyshop_frontend", "Node 24 -> Nginx Alpine", "80/tcp", "Không mở", "Cung cấp Single Page Application (React 19) phục vụ Customer, Staff, Admin"],
        ["beautyshop_backend", "Eclipse Temurin Java 21", "8080/tcp", "127.0.0.1:8080", "Core Business Logic, REST API, WebSocket STOMP, Flyway 70 migrations"],
        ["beautyshop_chatbot", "Python 3.10 / FastAPI", "8000/tcp", "127.0.0.1:18000", "AI Product Agent, Advanced Hybrid RAG, CLIP Visual Search, Qdrant Vector Store"],
        ["beautyshop_mysql", "mysql:8.0", "3306/tcp", "127.0.0.1:3307", "Cơ sở dữ liệu quan hệ chính (InnoDB Buffer Pool 384M, đồng bộ giao dịch ACID)"],
        ["beautyshop_redis", "redis:7.2-alpine", "6379/tcp", "127.0.0.1:6379", "Distributed Cache L2, Distributed Locking (ShedLock), Quản lý phiên làm việc"],
        ["beautyshop_kafka", "apache/kafka:3.7.0 (KRaft)", "29092/9092", "127.0.0.1:9092", "Event Backbone, Phân phối sự kiện Outbox (Đơn hàng, Vé Spa, Thông báo)"],
        ["beautyshop_prometheus", "prom/prometheus:v2.51.0", "9090/tcp", "127.0.0.1:9090", "Hệ thống giám sát chỉ số APM, tự động scrape metrics 5s/lần từ Backend Actuator"],
        ["beautyshop_grafana", "grafana/grafana:10.4.0", "3000/tcp", "127.0.0.1:3000", "Bảng điều khiển trực quan hóa hiệu năng hệ thống, CPU, RAM, JVM, HTTP status"]
    ]
    add_table_custom(infra_headers, infra_rows, infra_widths,
                     [WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT])

    add_h2("1.4. Luồng dữ liệu và tích hợp nghiệp vụ giữa các phân hệ")
    add_p(
        "1. Luồng Người dùng truy cập Web & Mobile: "
        "Client gửi yêu cầu qua HTTPS tới Cloudflare Edge -> Cloudflare Tunnel chuyển tiếp vào Nginx cổng 80 -> "
        "Nginx phân luồng: nếu là URL trang tĩnh (/) định tuyến tới 'beautyshop_frontend'; nếu là đường dẫn '/api/' định tuyến vào 'backend_cluster' (Spring Boot); "
        "nếu là tệp hình ảnh tĩnh (/uploads/) Nginx dùng cơ chế zero-copy native sendfile trả trực tiếp từ ổ đĩa mà không đi qua JVM Tomcat."
    )
    add_p(
        "2. Luồng Tư vấn AI và Truy xuất RAG thời gian thực: "
        "Khách hàng gửi câu hỏi tư vấn tại giao diện Chat -> Yêu cầu đi qua Nginx tại '/api/v1/chatbot/chat/stream' "
        "(Nginx tắt bộ đệm proxy_buffering để tối ưu Server-Sent Events - SSE) -> Backend Spring chuyển tiếp nội bộ tới 'http://chatbot:8000' -> "
        "FastAPI kích hoạt Bộ phân tích NLU 6 tầng -> Truy xuất 3 kênh kết hợp (SQL Filter + BM25 Lexical + Qdrant Dense Vector) -> "
        "Hợp nhất thứ hạng RRF (k=60) -> Tái xếp hạng đa tiêu chí (Bayesian Reranker) -> Kiểm duyệt tồn kho khả dụng thời gian thực (Live Inventory Verification) -> "
        "Gửi prompt ngữ cảnh tinh gọn tới Google Gemini LLM -> Bắn luồng token phản hồi theo thời gian thực (SSE) về máy khách."
    )
    add_p(
        "3. Luồng Tìm kiếm sản phẩm bằng hình ảnh (Visual Search): "
        "Khách hàng tải lên ảnh chụp sản phẩm qua Web hoặc Mobile App -> Backend kiểm tra kích thước (tối đa 5MB) và magic bytes an toàn -> "
        "Chuyển tiếp tệp ảnh sang FastAPI Service -> Mô hình CLIP (clip-ViT-B-32) trích xuất vector thị giác 512 chiều -> "
        "So khớp Cosine Similarity với cơ sở dữ liệu vector đặc trưng hình ảnh 'index.npz' -> "
        "Trả về danh sách mã sản phẩm tương đồng nhất -> Backend đối soát trạng thái kinh doanh, giá và tồn kho -> Trả kết quả hiển thị trên ứng dụng."
    )
    add_p(
        "4. Luồng Thanh toán tự động SePay & Cập nhật thời gian thực (Real-time Payment): "
        "Khách hàng quét mã VietQR chuyển khoản ngân hàng -> Ngân hàng gửi Webhook thông báo giao dịch thành công về endpoint "
        "'/api/v1/payments/sepay/webhook' -> Backend xác thực chữ ký API Key bảo mật bằng thuật toán MessageDigest.isEqual -> "
        "Cập nhật trạng thái đơn hàng thành PAID, đồng thời đẩy thông điệp thanh toán thành công qua kênh WebSocket STOMP ('/ws/') "
        "để màn hình thanh toán của khách tự động chuyển sang trang hóa đơn hoàn tất trong tích tắc mà không cần tải lại trang."
    )
    add_p(
        "5. Luồng Sự kiện phân tán qua Transactional Outbox Pattern & Kafka: "
        "Mọi biến động trạng thái cốt lõi (Đặt hàng thành công, Hoàn tất buổi spa, Kích hoạt voucher) được ghi đồng thời vào bảng 'outbox_messages' "
        "trong cùng giao dịch cơ sở dữ liệu ACID với bảng chính -> Tác vụ quét Outbox đọc và đẩy sự kiện lên Kafka Broker (chủ đề 'beautyshop-order-events', "
        "'spa-appointment-notifications') -> Các Consumer tiếp nhận xử lý trừ kho, tích điểm thành viên, gửi thông báo ứng dụng và gửi email xác nhận qua SMTP."
    )

    # -------------------------------------------------------------------------
    # PHẦN 2: DANH MỤC TOÀN BỘ NGÔN NGỮ LẬP TRÌNH ĐƯỢC SỬ DỤNG
    # -------------------------------------------------------------------------
    add_h1("PHẦN 2: DANH MỤC TOÀN BỘ NGÔN NGỮ LẬP TRÌNH VÀ VAI TRÒ")

    add_p(
        "Hệ thống là một dự án đa ngôn ngữ (Polyglot System) với sự phân chia vai trò rõ ràng, "
        "chọn lọc ngôn ngữ tối ưu nhất cho từng tác vụ chuyên biệt từ hệ thống nghiệp vụ quy mô lớn, "
        "mô hình trí tuệ nhân tạo, cho đến giao diện người dùng web và di động:"
    )

    lang_headers = ["Ngôn ngữ lập trình", "Phiên bản / Môi trường", "Phân hệ đảm nhiệm", "Vai trò và Trách nhiệm kỹ thuật trong hệ thống"]
    lang_widths = [Inches(1.5), Inches(1.3), Inches(1.5), Inches(2.7)]
    lang_rows = [
        ["Java", "Java 21 (Temurin LTS)", "Core Backend Monolith", "Ngôn ngữ xương sống của toàn bộ hạ tầng logic: Xử lý 14 modules nghiệp vụ, xác thực JWT, điều phối giao dịch ACID, tích hợp Kafka, ShedLock, WebSocket, ArchUnit."],
        ["Python", "Python 3.10 - 3.12", "AI Agent & RecSys Service", "Phân hệ Trí tuệ Nhân tạo: Xây dựng REST API bằng FastAPI, Pipeline Advanced RAG, thuật toán BM25/RRF, mô hình CLIP ViT-B-32, xử lý vector Qdrant, notebook Colab huấn luyện mô hình."],
        ["JavaScript (ES2022+)", "Node.js 20 & 24", "Web SPA & Automation", "Ngôn ngữ của giao diện Web React 19: Điều khiển logic giao diện khách hàng, lễ tân spa và admin; kịch bản kiểm thử trình duyệt tự động Playwright; script kiểm tra an toàn."],
        ["Dart", "Dart SDK 3.12+ (Flutter 3)", "Mobile Multiplatform App", "Phát triển ứng dụng di động đa nền tảng (Android, iOS, macOS, Windows, Web) với trải nghiệm native mượt mà, quản lý trạng thái bằng Provider, kết nối REST API."],
        ["SQL (MySQL Dialect)", "MySQL 8.0 Engine", "Cơ sở dữ liệu & Di trú", "Viết 70 bản script di trú Flyway (V1 đến V70): Định nghĩa lược đồ dữ liệu, bảng kiểm kê, chỉ mục tối ưu tìm kiếm, ràng buộc toàn vẹn khóa ngoại và dữ liệu khởi tạo chuẩn y khoa."],
        ["Shell Script (Bash & Batch)", "Windows (.bat) & Linux (.sh)", "DevOps & Vận hành", "Tự động hóa khởi chạy Cloudflare Tunnel an toàn, trích xuất token môi trường, script dọn dẹp và sao lưu tự động cơ sở dữ liệu."],
        ["Config DSL (YAML, Nginx DSL)", "Docker Compose, Nginx, CI/CD", "Hạ tầng & Điều phối", "Định nghĩa cụm 8 container trong Docker Compose, cấu hình Reverse Proxy/Security Headers trong nginx.conf, kịch bản CI/CD trong GitHub Actions."]
    ]
    add_table_custom(lang_headers, lang_rows, lang_widths,
                     [WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT])

    # -------------------------------------------------------------------------
    # PHẦN 3: DANH MỤC TOÀN BỘ CÔNG NGHỆ, FRAMEWORK VÀ CÔNG CỤ
    # -------------------------------------------------------------------------
    add_h1("PHẦN 3: DANH MỤC TOÀN BỘ CÔNG NGHỆ, FRAMEWORK VÀ CÔNG CỤ")

    add_h2("3.1. Phân hệ Backend & Lõi nghiệp vụ (Backend Technology Stack)")
    add_p("• Spring Boot 3.4.3: Framework ứng dụng cấp doanh nghiệp hiện đại nhất, chạy trên nền máy ảo Java 21, hỗ trợ cơ chế nạp cấu hình và khởi tạo dịch vụ siêu tốc.")
    add_p("• Spring Security & JJWT 0.12.6: Hệ thống bảo mật phân quyền đa tầng (RBAC - Role Based Access Control: ROLE_CUSTOMER, ROLE_STAFF, ROLE_ADMIN). Hỗ trợ xác thực kép Access Token (thời hạn 15 phút) và Refresh Token Session lưu trong database. Bổ sung cơ chế Token Versioning (token_version) cho phép vô hiệu hóa tức thời toàn bộ token của người dùng khi đổi mật khẩu hoặc phát hiện xâm nhập.")
    add_p("• Spring Data JPA & Hibernate 6.x: Quản lý ánh xạ đối tượng - quan hệ (ORM), tự động hóa các thao tác cơ sở dữ liệu với tiêu chí tối ưu hóa câu lệnh truy vấn, sử dụng Specification cho các bộ lọc tìm kiếm nâng cao.")
    add_p("• Flyway 10.x Database Migration: Quản lý phiên bản cơ sở dữ liệu tự động với 70 tệp script di trú nghiêm ngặt từ V1 đến V70, loại bỏ hoàn toàn tình trạng sai lệch schema giữa các môi trường.")
    add_p("• Apache Kafka & Spring Kafka: Nền tảng luồng sự kiện phân tán (Event Streaming) chạy ở chế độ KRaft (không cần ZooKeeper). Đảm nhận việc phân phối thông điệp đặt hàng, biến động vé spa, hóa đơn thanh toán bất đồng bộ.")
    add_p("• Transactional Outbox Pattern: Đảm bảo tính nhất quán dữ liệu phân tán (Eventual Consistency). Bản ghi nghiệp vụ và sự kiện outbox được ghi trong cùng một giao dịch ACID của MySQL, ngăn ngừa triệt để hiện tượng mất thông điệp.")
    add_p("• Caffeine Cache & Spring Data Redis: Kiến trúc Caching 2 tầng tối ưu: Bộ đệm L1 nội bộ cực nhanh bằng Caffeine (in-memory) và Bộ đệm L2 phân tán bằng Redis 7.2 cho các danh mục sản phẩm, cấu hình cửa hàng và số dư kho.")
    add_p("• ShedLock (shedlock-spring & shedlock-provider-redis-spring): Cơ chế khóa phân tán dựa trên Redis, đảm bảo các tác vụ lập lịch định kỳ (như DataRetentionJob dọn dẹp dữ liệu cũ mỗi Chủ Nhật) chỉ chạy duy nhất trên một instance máy chủ.")
    add_p("• Spring WebSocket & STOMP: Giao thức truyền thông hai chiều thời gian thực giữa Backend và Trình duyệt người dùng phục vụ cập nhật trạng thái thanh toán VietQR và thông báo tiếp đón tại Spa.")
    add_p("• Spring Mail: Tích hợp giao thức SMTP (Gmail API) gửi email tự động xác nhận đơn hàng, email phục hồi mật khẩu và thông báo liệu trình spa.")
    add_p("• Spring Boot Actuator & Micrometer Prometheus: Cung cấp các endpoint đo lường hiệu năng chuyên sâu (JVM memory, Garbage Collection, CPU, HTTP request latency, JDBC connection pool) cho hệ sinh thái Prometheus.")
    add_p("• Springdoc OpenAPI 2.7.0 (Swagger UI): Tự động tạo lập tài liệu đặc tả chuẩn OpenAPI 3.0 tại địa chỉ '/swagger-ui/index.html' với đầy đủ hơn 150 endpoints nghiệp vụ.")
    add_p("• ArchUnit 1.3.0: Framework kiểm thử kiến trúc tự động, thực thi trong quá trình Maven test để ngăn chặn vi phạm nguyên lý Modular Monolith và DDD.")
    add_p("• Testcontainers & JUnit 5: Khởi tạo container MySQL thật trong quá trình chạy kiểm thử tích hợp (Integration Tests), đảm bảo môi trường kiểm thử tương đồng 100% với môi trường Production.")

    add_h2("3.2. Phân hệ Trí tuệ Nhân tạo & Tìm kiếm Thị giác (AI, RAG & Computer Vision Stack)")
    add_p("• FastAPI & Uvicorn: Web framework bất đồng bộ (Asynchronous) hiệu năng cao viết bằng Python, xử lý hàng trăm kết nối streaming đồng thời với độ trễ tối thiểu.")
    add_p("• Pydantic v2: Xác thực cấu hình môi trường và cấu trúc dữ liệu JSON chặt chẽ cho toàn bộ các schema đầu vào/đầu ra của AI Agent.")
    add_p("• Qdrant Vector Database: Cơ sở dữ liệu vector mã nguồn mở chuyên dụng, lưu trữ và lập chỉ mục không gian vector nhúng 768 chiều với thuật toán HNSW (Hierarchical Navigable Small World) cho phép tìm kiếm láng giềng gần nhất (ANN) dưới 10ms.")
    add_p("• rank-bm25: Thư viện triển khai giải thuật tìm kiếm từ vựng BM25Okapi, phục vụ kênh truy xuất từ khóa kỹ thuật y khoa và thành phần hóa mỹ phẩm.")
    add_p("• Google Gemini API (Gemini 1.5 Flash / Flash-lite): Mô hình ngôn ngữ lớn tiên tiến đóng vai trò bộ não tổng hợp thông tin, trích xuất ý định đàm thoại (NLU) và tạo sinh phản hồi tư vấn có căn cứ.")
    add_p("• Contrastive Language-Image Pre-training (CLIP - ViT-B-32): Mô hình thị giác ngôn ngữ chạy trên nền PyTorch 2.5.1 và Hugging Face Transformers, trích xuất vector đặc trưng hình ảnh 512 chiều từ ảnh chụp bao bì sản phẩm do người dùng tải lên.")
    add_p("• SQLAlchemy 2.0: Tầng truy cập dữ liệu đa năng, cho phép Chatbot đọc dữ liệu kiểm kê tồn kho từ bản sao MySQL hoặc cơ sở dữ liệu cục bộ SQLite.")
    add_p("• Server-Sent Events (SSE): Cơ chế truyền phát dòng token liên tục (token streaming) từ AI Agent về Client, mang lại trải nghiệm gõ chữ mượt mà như ChatGPT.")

    add_h2("3.3. Phân hệ Giao diện Người dùng Web (Frontend Stack)")
    add_p("• React 19 (19.2.8): Phiên bản React mới nhất với các cải tiến vượt trội về hiệu năng render và quản lý trạng thái giao diện.")
    add_p("• Vite 8.3.0: Công cụ build và bundling hiện đại bậc nhất hiện nay, cung cấp môi trường phát triển Hot Module Replacement (HMR) cực nhanh.")
    add_p("• Lucide React (1.48.0): Bộ thư viện biểu tượng vector hiện đại, tối ưu dung lượng tải trang.")
    add_p("• Axios (1.20.0): Thư viện HTTP Client với các interceptor thông minh: tự động đính kèm Bearer JWT, tự động bắt lỗi 401 để gọi API refresh token mà không làm gián đoạn người dùng.")
    add_p("• SheetJS (xlsx 0.20.3): Thư viện xử lý và xuất dữ liệu thống kê doanh thu, tồn kho, hóa đơn spa trực tiếp ra file Excel (.xlsx).")
    add_p("• Oxlint (1.81.0): Linter siêu tốc viết bằng Rust, giúp rà soát lỗi cú pháp và an toàn mã nguồn JavaScript nhanh gấp 50-100 lần so với ESLint.")
    add_p("• Playwright (1.63.0): Framework kiểm thử tự động toàn diện (End-to-End Testing) mô phỏng hành vi người dùng thực tế trên các trình duyệt Chromium, Firefox, WebKit.")

    add_h2("3.4. Phân hệ Ứng dụng Di động Đa nền tảng (Mobile Stack)")
    add_p("• Flutter 3.x & Dart SDK 3.12+: Bộ công cụ UI đa nền tảng hàng đầu của Google, cho phép biên dịch ra ứng dụng native chạy mượt mà 60fps trên Android, iOS và Desktop.")
    add_p("• Provider 6.1.2: Thư viện quản lý trạng thái (State Management) chuẩn mực, giúp đồng bộ dữ liệu giỏ hàng, phiên đăng nhập, lịch hẹn spa xuyên suốt các màn hình.")
    add_p("• Dio 5.4.3: HTTP client mạnh mẽ hỗ trợ Interceptors, FormData tải ảnh, Timeout và quản lý kết nối an toàn.")
    add_p("• Flutter Secure Storage: Lưu trữ an toàn Access Token và Refresh Token trong Keychain (iOS) và KeyStore mã hóa AES (Android).")
    add_p("• Cached Network Image: Tối ưu hóa tải ảnh sản phẩm từ xa với cơ chế cache đệm trên đĩa, tự động hiển thị shimmer placeholder khi đang tải.")

    add_h2("3.5. Phân hệ Giám sát, Quản trị và DevOps (Monitoring & DevOps Stack)")
    add_p("• Docker & Docker Compose: Tiêu chuẩn đóng gói và điều phối 8 container dịch vụ đồng bộ, bảo đảm tính nhất quán 'xây dựng một lần, chạy mọi nơi'.")
    add_p("• Nginx Alpine: Web server và Reverse proxy hiệu năng cao, tối ưu dung lượng siêu nhẹ (<30MB RAM), đảm nhiệm bảo mật và phân tải.")
    add_p("• Cloudflare Tunnel ('cloudflared'): Giải pháp kết nối an toàn từ máy chủ nội bộ ra mạng Internet toàn cầu mà không cần mở port mạng.")
    add_p("• Prometheus 2.51.0: Hệ thống cơ sở dữ liệu chuỗi thời gian (TSDB) chuyên dụng thu thập và lưu trữ các chỉ số đo lường hiệu năng hệ thống.")
    add_p("• Grafana 10.4.0: Bảng điều khiển phân tích số liệu trực quan, cảnh báo sớm tình trạng nghẽn cổ chai tài nguyên máy chủ.")
    add_p("• GitHub Actions: Nền tảng CI/CD tự động hóa toàn diện quy trình kiểm thử (Backend CI, Docker Build, DevOps Validation, Mobile CI, Frontend CI).")

    # -------------------------------------------------------------------------
    # PHẦN 4: ĐÁNH GIÁ KHẢ NĂNG PUBLIC VÀ GIẢI PHÁP ĐƯA RA INTERNET
    # -------------------------------------------------------------------------
    add_h1("PHẦN 4: ĐÁNH GIÁ KHẢ NĂNG PUBLIC VÀ GIẢI PHÁP ĐƯA RA INTERNET")

    add_h2("4.1. Khẳng định trạng thái Public và Hệ thống Tên miền chính thức")
    add_p(
        "Hệ thống DermaScan / BeautyShop HOÀN TOÀN ĐƯỢC THIẾT KẾ ĐỂ PUBLIC CÔNG KHAI RA MẠNG INTERNET TOÀN CẦU. "
        "Mọi thành phần từ Backend, Frontend, Chatbot AI cho đến Ứng dụng Di động đều đã được tích hợp sẵn các cấu hình tên miền công cộng chính thức:"
    )

    domain_headers = ["Phân hệ / Cổng dịch vụ", "Địa chỉ Tên miền Public", "Giao thức", "Cơ chế Ánh xạ & Bảo vệ"]
    domain_widths = [Inches(1.8), Inches(2.2), Inches(0.9), Inches(2.4)]
    domain_rows = [
        ["Giao diện Web Khách hàng", "https://www.dermascan.world", "HTTPS (SSL)", "Cloudflare Pages & Cloudflare Edge CDN toàn cầu"],
        ["Cổng API Gateway Backend", "https://api.dermascan.world", "HTTPS (SSL)", "Cloudflare Tunnel -> Nginx cổng 80 -> Spring Boot Cluster"],
        ["Cổng Webhook Ngân hàng", "https://api.dermascan.world/api/v1/payments/sepay/webhook", "HTTPS (SSL)", "SePay Webhook tự động đẩy thông tin giao dịch VietQR MBBank"],
        ["API Trợ lý AI Streaming", "https://api.dermascan.world/api/v1/chatbot/chat/stream", "HTTPS (SSE)", "Nginx tắt bộ đệm (proxy_buffering off), streaming token trực tiếp"],
        ["API Tìm kiếm bằng hình ảnh", "https://api.dermascan.world/api/v1/products/search-by-image", "HTTPS (SSL)", "Giới hạn payload 5.3MB, kiểm duyệt an toàn file trước khi gọi CLIP"]
    ]
    add_table_custom(domain_headers, domain_rows, domain_widths,
                     [WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT])

    add_h2("4.2. Cơ chế Public đột phá thông qua Cloudflare Tunnel (cloudflared)")
    add_p(
        "Thay vì sử dụng phương pháp truyền thống như mua địa chỉ IP tĩnh công cộng đắt đỏ, mở cổng NAT (Port Forwarding) trên Router nhà mạng "
        "hoặc thuê máy chủ VPS đắt tiền, dự án triển khai giải pháp công nghệ Cloudflare Tunnel (sử dụng daemon 'cloudflared'). "
        "Đây là kiến trúc mạng hiện đại mang lại các ưu thế vượt trội:",
        bold_prefix="Nguyên lý hoạt động của Cloudflare Tunnel: "
    )
    add_p(
        " Khi daemon 'cloudflared' khởi chạy, nó thiết lập 4 kết nối hướng ra ngoài (Outbound Connections) được mã hóa TLS hai chiều từ máy chủ nội bộ "
        "tới 2 Trung tâm dữ liệu gần nhất của Cloudflare. Nhờ đó, máy chủ không cần mở bất kỳ cổng vào nào (Zero Inbound Open Ports) trên tường lửa mạng.",
        bold_prefix="1. Triệt tiêu hoàn toàn cổng mở vào (Zero Open Ports): "
    )
    add_p(
        " Kẻ tấn công trên mạng Internet hoàn toàn không thể quét được địa chỉ IP thật của máy chủ. Toàn bộ lưu lượng truy cập đều đi qua mạng lưới "
        "hơn 300 điểm hiện diện (PoP) của Cloudflare, được hưởng lợi từ hệ thống phòng thủ chống DDoS tự động và tường lửa WAF cấp độ doanh nghiệp.",
        bold_prefix="2. Ẩn hoàn toàn địa chỉ IP gốc (Origin IP Shielding): "
    )
    add_p(
        " Toàn bộ chứng chỉ bảo mật SSL/TLS đều được Cloudflare quản lý và tự động cấp phát, mã hóa an toàn từ trình duyệt khách hàng "
        "đến biên mạng và giải mã an toàn qua đường hầm mã hóa về Nginx cục bộ.",
        bold_prefix="3. Cấp phát chứng chỉ số SSL/TLS tự động: "
    )

    add_h2("4.3. Các chốt chặn an toàn cấu hình hạ tầng đã hoàn thiện (Infra Hardening)")
    add_p(
        "Căn cứ tài liệu nghiệm thu an toàn hạ tầng ngày 20/10/2026 trong mã nguồn (docs/operations/2026-10-20_ket_qua_an_toan_va_toi_uu_ha_tang.md), "
        "hệ thống đã hoàn tất và kiểm chứng thành công 6 hạng mục bảo mật tối quan trọng khi đưa ra Internet:"
    )
    add_p(
        " Trong tệp 'docker-compose.yml', toàn bộ các cổng nhạy cảm gồm MySQL (3307), Redis (6379), Kafka (9092), Spring Backend (8080), "
        "Prometheus (9090), Grafana (3000) đều được chuyển binding từ '0.0.0.0' sang '127.0.0.1'. "
        "Chỉ duy nhất một cổng '80' của container 'beautyshop_nginx' được phép lắng nghe, ngăn chặn triệt để nguy cơ tin tặc dò quét cổng từ mạng LAN hoặc Internet.",
        bold_prefix="• Đóng kín cổng dịch vụ nội bộ (SEC-01 - CRITICAL): "
    )
    add_p(
        " Nginx được nạp đầy đủ danh sách 15 dải địa chỉ IPv4 chính thức của Cloudflare và cấu hình 'real_ip_header CF-Connecting-IP; real_ip_recursive on;'. "
        "Cơ chế này giúp Backend và nhật ký truy cập ghi nhận chính xác 100% địa chỉ IP thực của khách hàng thay vì IP của proxy Cloudflare.",
        bold_prefix="• Phục hồi IP thật từ Cloudflare (SEC-02 - HIGH): "
    )
    add_p(
        " Nhờ cơ chế Real-IP chuẩn xác, Nginx thiết lập luật chặn cứng toàn bộ request xuất phát từ mạng Internet công cộng đến endpoint '/actuator/'. "
        "Chỉ duy nhất localhost và các dải mạng nội bộ (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) mới được phép lấy dữ liệu, bảo vệ an toàn các chỉ số đo lường.",
        bold_prefix="• Siết quyền truy cập Actuator & Metrics (SEC-06 - MEDIUM): "
    )
    add_p(
        " Triển khai thuật toán Leaky Bucket trên Nginx: giới hạn 30 req/s (burst 50) cho các API chung; siết chặt 5 req/s (burst 10) "
        "cho các API xác thực đăng nhập '/api/v1/auth/' nhằm vô hiệu hóa các cuộc tấn công Brute-Force mò mật khẩu.",
        bold_prefix="• Giới hạn tần suất gọi API (Rate Limiting): "
    )
    add_p(
        " Toàn bộ cấu hình CORS trong 'CorsConfig.java' và 'WebSocketConfig.java' được liên kết trực tiếp với biến môi trường 'CORS_ALLOWED_ORIGINS' "
        "và 'CUSTOMER_APP_URL', bảo đảm chỉ có các trang web hợp lệ mới được quyền gọi API.",
        bold_prefix="• Kiểm soát nguồn gốc linh hoạt (CORS Hardening): "
    )

    # -------------------------------------------------------------------------
    # PHẦN 5: QUY TRÌNH SETUP VÀ HƯỚNG DẪN TRIỂN KHAI CHI TIẾT
    # -------------------------------------------------------------------------
    add_h1("PHẦN 5: QUY TRÌNH SETUP VÀ HƯỚNG DẪN TRIỂN KHAI CHI TIẾT")

    add_h2("5.1. Yêu cầu môi trường tiền đề (Prerequisites)")
    add_p("Trước khi tiến hành cài đặt và vận hành hệ thống, máy chủ hoặc máy trạm cần đáp ứng các điều kiện kỹ thuật tối thiểu:")
    add_p("• Hệ điều hành: Windows 10/11 Pro/Enterprise (hỗ trợ WSL2) hoặc Linux Ubuntu 22.04 LTS / 24.04 LTS.")
    add_p("• Phần cứng đề xuất: Tối thiểu 16GB RAM (khuyến nghị 32GB RAM để vận hành đồng thời cụm 8 container, mô hình AI CLIP và cơ sở dữ liệu), CPU tối thiểu 4 nhân / 8 luồng, ổ cứng SSD tối thiểu 50GB dung lượng trống.")
    add_p("• Phần mềm cài sẵn: Docker Desktop phiên bản 25.0+ (hoặc Docker Engine & Docker Compose v2), Git, JDK 21 (Eclipse Temurin), Node.js 20+ hoặc 24+, Python 3.10+, Flutter SDK 3.x, và công cụ Cloudflared CLI.")

    add_h2("5.2. Chuẩn bị biến môi trường (.env)")
    add_p(
        "Hệ thống tuân thủ chặt chẽ nguyên tắc Twelve-Factor App: tách biệt hoàn toàn cấu hình nhạy cảm khỏi mã nguồn. "
        "Người triển khai cần tạo tệp '.env' tại thư mục gốc dự án từ tệp mẫu '.env.example' và tệp 'chatbot/product-agent/.env'."
    )
    add_p(
        "Lệnh sinh khóa bí mật JWT an toàn 32 byte bằng Python:",
        bold_prefix="Khởi tạo mã bí mật an toàn: "
    )
    add_code_block('python -c "import secrets; print(secrets.token_urlsafe(48))"')
    add_p(
        "Cấu hình mẫu tệp '.env' cốt lõi tại thư mục gốc:",
        bold_prefix="Nội dung file .env chuẩn:"
    )
    add_code_block(
'''SERVER_PORT=8080
CHATBOT_PORT=18000
DB_PASSWORD=YourStrongDatabasePassword2026!
JWT_SECRET=SinhTuLenhPythonTren_ToiThieu32BytesChuoiNgauNhien
JWT_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
SEPAY_WEBHOOK_API_KEY=YourSepayWebhookApiKeyHere
SEPAY_BANK_NAME=MBBank
SEPAY_BANK_ACCOUNT_NAME=NGUYEN THANH NAM
SEPAY_BANK_ACCOUNT_NUMBER=0987654321
SEPAY_BANK_BIN=970422
CUSTOMER_APP_URL=https://www.dermascan.world
CLOUDFLARE_TUNNEL_TOKEN=eyJhIjoiMTAzYzA1... (Token lay tu Cloudflare Zero Trust)
GRAFANA_ADMIN_PASSWORD=YourGrafanaAdminPass2026!
CORS_ALLOWED_ORIGINS=https://www.dermascan.world,https://dermascan.world'''
    )

    add_h2("5.3. Khởi động toàn bộ cụm dịch vụ bằng Docker Compose")
    add_p(
        "Tại thư mục gốc dự án 'D:\\khoaluantotnghiep', thực thi lệnh khởi động tự động. "
        "Lệnh này sẽ tự động tải các base images, biên dịch Backend Java 21 qua Multi-stage build, "
        "biên dịch Frontend React 19 thành tệp tĩnh, xây dựng Docker image cho Chatbot Python, "
        "và khởi động cụm 8 dịch vụ theo đúng thứ tự phụ thuộc (depends_on có healthcheck):"
    )
    add_code_block("docker compose up -d --build --wait --wait-timeout 300")
    add_p(
        "Cơ chế điều phối tự động: "
        "Compose sẽ khởi động MySQL, Redis, Kafka trước. Khi MySQL vượt qua bài kiểm tra healthcheck (mysqladmin ping), "
        "Spring Boot Backend mới bắt đầu khởi chạy và tự động thực thi 70 bản Flyway migrations. "
        "Khi Backend có trạng thái 'healthy' (vượt qua /actuator/health), Chatbot và Nginx mới được kích hoạt, "
        "đảm bảo không bao giờ xảy ra lỗi kết nối cơ sở dữ liệu khi hệ thống vừa thức giấc."
    )
    add_p("Kiểm tra trạng thái các container sau khi khởi động:")
    add_code_block("docker compose ps")

    add_h2("5.4. Khởi chạy Cloudflare Tunnel đưa hệ thống ra Public")
    add_p(
        "Để kết nối hệ thống từ máy trạm nội bộ ra tên miền công cộng 'api.dermascan.world' và 'www.dermascan.world', "
        "chạy kịch bản khởi động đã được tích hợp sẵn tại thư mục scripts:"
    )
    add_p("Trên môi trường Windows:", bold_prefix="• Thực thi trên Windows: ")
    add_code_block(".\\scripts\\cloudflare\\run_cloudflared.bat")
    add_p("Trên môi trường Linux / macOS:", bold_prefix="• Thực thi trên Linux: ")
    add_code_block("chmod +x ./scripts/cloudflare/run_cloudflared.sh\n./scripts/cloudflare/run_cloudflared.sh")
    add_p(
        "Kịch bản sẽ tự động trích xuất token bảo mật 'CLOUDFLARE_TUNNEL_TOKEN' từ file '.env' và khởi chạy kết nối 4 luồng an toàn tới Cloudflare Edge. "
        "Ngay lập tức, các yêu cầu từ mạng Internet đến 'https://api.dermascan.world' sẽ được chuyển tiếp an toàn vào Nginx cổng 80."
    )

    add_h2("5.5. Triển khai Frontend & Mobile App")
    add_p(
        "1. Triển khai Web Frontend: "
        "Tệp build tĩnh của Frontend đã được tự động tạo trong quá trình Docker build và phân phối qua Nginx. "
        "Ngoài ra, frontend còn hỗ trợ xuất bản tĩnh lên Cloudflare Pages thông qua công cụ Wrangler: 'npx wrangler pages deploy dist --project-name=dermascan'."
    )
    add_p(
        "2. Cấu hình và Build Mobile App (Flutter): "
        "Trong tệp 'mobile/lib/core/constants/api_constants.dart', cấu hình 'baseUrl' mặc định trỏ về 'https://api.dermascan.world'. "
        "Để build bản cài đặt cho Android hoặc iOS:",
        bold_prefix="Biên dịch Mobile App: "
    )
    add_code_block(
'''cd mobile
flutter pub get
flutter build apk --release'''
    )

    add_h2("5.6. Bộ kịch bản kiểm tra nghiệm thu (Smoke Test & E2E Verification)")
    add_p("Sau khi triển khai thành công, quản trị viên thực hiện các bài kiểm tra xác nhận tính toàn vẹn của hệ thống:")
    add_p("1. Kiểm tra trạng thái Health của Backend và Chatbot:")
    add_code_block(
'''Invoke-RestMethod http://localhost/actuator/health
Invoke-RestMethod http://localhost/api/v1/chatbot/health'''
    )
    add_p("2. Chạy kịch bản kiểm thử API Chatbot tự động (kiểm tra kết nối MySQL, LLM và SSE streaming):")
    add_code_block("python chatbot/product-agent/evaluation/smoke_api.py --base-url http://localhost/api/v1/chatbot --proxy")
    add_p("3. Chạy kịch bản kiểm thử trình duyệt End-to-End bằng Playwright:")
    add_code_block(
'''cd frontend
$env:E2E_BASE_URL = 'http://localhost'
npm run test:e2e -- e2e/docker-stack.spec.js --retries=0'''
    )

    add_h2("5.7. Quy trình bảo trì, Backup và khôi phục dữ liệu (Ops Runbook)")
    add_p(
        "1. Cơ chế tự động dọn dẹp dữ liệu (DataRetentionJob): "
        "Hệ thống cài đặt tác vụ định kỳ chạy vào 03:00 sáng mỗi Chủ Nhật (cron: '0 0 3 * * SUN') được bảo vệ bởi ShedLock. "
        "Tác vụ sẽ tự động xóa sạch các thông điệp Outbox đã hoàn tất xử lý và các phiên RefreshTokenSession hết hạn quá 14 ngày, "
        "giúp dung lượng database luôn duy trì ở mức tối ưu."
    )
    add_p(
        "2. Quy trình sao lưu cơ sở dữ liệu định kỳ (Database Backup): "
        "Dữ liệu MySQL được sao lưu tự động ra thư mục '.local/backups/' bằng lệnh 'mysqldump'. "
        "Các bản sao lưu được lưu trữ dạng tệp nén .sql có gắn nhãn thời gian."
    )
    add_p(
        "3. Quy trình khôi phục sự cố (Disaster Recovery): "
        "Trong trường hợp máy chủ gặp sự cố phần cứng, kỹ sư vận hành khôi phục môi trường căn cứ theo tài liệu "
        "'backend/ops/RECOVERY_2026-10-03.md'. Chỉ cần nạp lại bản backup SQL mới nhất vào container MySQL và khởi động lại Docker Compose, "
        "cơ chế Flyway sẽ tự động đối soát checksum và đưa hệ thống trở lại trạng thái vận hành ổn định trong vòng 5 phút."
    )

    # -------------------------------------------------------------------------
    # PHẦN 6: KẾT LUẬN VÀ ĐÁNH GIÁ TỔNG QUAN
    # -------------------------------------------------------------------------
    add_h1("PHẦN 6: KẾT LUẬN VÀ ĐÁNH GIÁ TỔNG QUAN")
    add_p(
        "Hệ thống DermaScan / BeautyShop là một công trình nghiên cứu và phát triển phần mềm quy mô lớn, "
        "kết hợp nhuần nhuyễn giữa tư duy kiến trúc hướng nghiệp vụ hiện đại (Domain-Driven Design, Modular Monolith) "
        "với các công nghệ trí tuệ nhân tạo tiên tiến nhất hiện nay (Advanced Hybrid RAG, Reciprocal Rank Fusion, Vision-Language CLIP). "
        "Hệ sinh thái công nghệ đa dạng bao gồm Java 21, Spring Boot 3, Python FastAPI, React 19, Flutter 3, MySQL 8, Redis 7, Kafka 3.7 "
        "được phối hợp nhịp nhàng, tối ưu hóa về mặt hiệu năng và tài nguyên."
    )
    add_p(
        "Việc ứng dụng thành công giải pháp Cloudflare Tunnel cùng kiến trúc an toàn hạ tầng đa tầng (Zero Open Ports, Nginx Rate Limiting, "
        "Real-IP Restoration, Token Versioning, ShedLock, Actuator Protection) đã chứng minh hệ thống hoàn toàn sẵn sàng cho môi trường "
        "Public Internet thực tế với độ tin cậy, khả năng chịu tải và tính bảo mật đạt chuẩn ứng dụng thương mại chuyên nghiệp."
    )

    # Luu file vao duong dan nguoi dung yeu cau
    target_dir = r"C:\Users\NGUYEN THANH NAM\OneDrive\Desktop\kk"
    if not os.path.exists(target_dir):
        os.makedirs(target_dir, exist_ok=True)
    out_file = os.path.join(target_dir, "Bao_Cao_Kien_Truc_Cong_Nghe_Va_Quy_Trinh_Trien_Khai_He_Thong.docx")
    doc.save(out_file)
    print(f"XUAT FILE THANH CONG: {out_file}")

if __name__ == "__main__":
    generate_report()
