# -*- coding: utf-8 -*-
"""
Script tao bao cao chuyen sau da refactor hoan toan:
- Chen day du 4 so do kien truc thuc te (anh PNG 300 DPI)
- Co ban thuyet minh bo cuc dep, sau sac phia duoi moi so do
- TUYET DOI KHONG GHI CODE, HAM CODE, LENH TERMINAL TRONG TAI LIEU
- Thiet ke theo tieu chuan khoa luan tot nghiep Dai hoc (kho A4, Times New Roman, le 3-2-2-2 cm)
- Xuat truc tiep vao: C:\\Users\\NGUYEN THANH NAM\\OneDrive\\Desktop\\kk
"""

import os
import shutil
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

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

    # 1. Cau hinh trang A4 va le chuan khoa luan
    for section in doc.sections:
        section.page_width = Inches(8.27)
        section.page_height = Inches(11.69)
        section.top_margin = Inches(0.79)    # 2.0 cm
        section.bottom_margin = Inches(0.79) # 2.0 cm
        section.left_margin = Inches(1.18)   # 3.0 cm
        section.right_margin = Inches(0.79)  # 2.0 cm

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
        frun = fp.add_run("Báo cáo Kiến trúc Hạ tầng, Công nghệ & Quy trình Triển khai Hệ thống")
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
        p.paragraph_format.space_before = Pt(16)
        p.paragraph_format.space_after = Pt(6)
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(17)
        run.font.color.rgb = RGBColor(0, 32, 96)
        return p

    def add_subtitle(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(16)
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(13.5)
        run.font.color.rgb = RGBColor(70, 70, 70)
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
        run.font.size = Pt(14.5)
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

    def add_callout(text, bold_label="LƯU Ý THIẾT KẾ:"):
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

    def insert_diagram(img_path, caption_title):
        """Chen anh so do va caption chuan hoa"""
        if os.path.exists(img_path):
            p_img = doc.add_paragraph()
            p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_img.paragraph_format.space_before = Pt(8)
            p_img.paragraph_format.space_after = Pt(4)
            run = p_img.add_run()
            run.add_picture(img_path, width=Inches(6.3))

            p_cap = doc.add_paragraph()
            p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_cap.paragraph_format.space_before = Pt(2)
            p_cap.paragraph_format.space_after = Pt(8)
            r_cap = p_cap.add_run(caption_title)
            r_cap.font.name = 'Times New Roman'
            r_cap.font.size = Pt(10.5)
            r_cap.bold = True
            r_cap.italic = True
            r_cap.font.color.rgb = RGBColor(0, 32, 96)

    diagrams_dir = r"D:\khoaluantotnghiep\scripts\docs\diagrams"

    # ==================== NỘI DUNG TÀI LIỆU ====================

    add_title("BÁO CÁO KỸ THUẬT VÀ KIẾN TRÚC HỆ THỐNG")
    add_subtitle("HỆ THỐNG THƯƠNG MẠI ĐIỆN TỬ DƯỢC MỸ PHẨM & QUẢN LÝ DỊCH VỤ PHÒNG KHÁM SPA CLINIC TÍCH HỢP TRÍ TUỆ NHÂN TẠO (DERMASCAN / BEAUTYSHOP)")

    add_callout(
        "Báo cáo này tập trung phân tích chuyên sâu các khía cạnh kiến trúc kỹ thuật, lựa chọn công nghệ, "
        "cơ chế an toàn mạng khi phát hành ra Internet toàn cầu và quy trình triển khai vận hành của hệ thống DermaScan. "
        "Tài liệu được biên soạn hoàn toàn dưới góc độ phân tích kiến trúc, lược đồ luồng dữ liệu và nguyên lý kỹ thuật, "
        "không đưa các đoạn mã nguồn hoặc cú pháp lệnh chi tiết vào nội dung, nhằm đảm bảo tính học thuật, "
        "tính khái quát hóa và đáp ứng chuẩn mực thẩm định của Khóa luận tốt nghiệp Đại học.",
        bold_label="ĐẶC TẢ NỘI DUNG BÁO CÁO:"
    )

    # -------------------------------------------------------------------------
    # PHẦN 1: TỔNG QUAN HỆ THỐNG VÀ KIẾN TRÚC TRIỂN KHAI
    # -------------------------------------------------------------------------
    add_h1("PHẦN 1: TỔNG QUAN HỆ THỐNG VÀ KIẾN TRÚC TRIỂN KHAI")

    add_h2("1.1. Bối cảnh đề tài và Mục tiêu thiết kế kiến trúc")
    add_p(
        "Trong kỷ nguyên chuyển đổi số ngành làm đẹp và y tế thẩm mỹ, việc kết hợp giữa kinh doanh bán lẻ dược mỹ phẩm "
        "với vận hành dịch vụ trị liệu da liễu tại phòng khám (Spa Clinic) đặt ra những thách thức lớn về mặt kỹ thuật phần mềm. "
        "Hệ thống không chỉ đòi hỏi năng lực xử lý các giao dịch thương mại điện tử với tốc độ cao, quản lý giỏ hàng và thanh toán trực tuyến "
        "mà còn phải giải quyết bài toán phức tạp về quản trị vòng đời dịch vụ y khoa: theo dõi liệu trình điều trị nhiều buổi kéo dài hàng tháng, "
        "quản lý số lượng buồng/giường trị liệu, điều phối ca kíp của bác sĩ và kỹ thuật viên có chứng chỉ chuyên môn, "
        "theo dõi mức độ tiêu hao từng mililit dược mỹ phẩm trong phòng thủ thuật, và quản lý lô hàng nhập kho theo chuẩn hạn dùng cận hạn."
    )
    add_p(
        "Nhằm nâng cao trải nghiệm người dùng và xóa bỏ rào cản tư vấn, đề tài tích hợp hai năng lực Trí tuệ Nhân tạo hiện đại: "
        "Trợ lý tư vấn da liễu thông minh ứng dụng kỹ thuật Tạo sinh dựa trên Truy xuất Tăng cường (Advanced RAG) tích hợp Mô hình Ngôn ngữ Lớn, "
        "và Hệ thống Tìm kiếm sản phẩm bằng hình ảnh thông qua mô hình thị giác ngôn ngữ đa phương thức CLIP."
    )

    add_h2("1.2. Mô hình kiến trúc Modular Monolith kết hợp AI Microservice")
    add_p(
        "Về mặt cấu trúc phần mềm, hệ thống áp dụng mô hình kiến trúc Modular Monolith (Đơn khối hướng Module) cho phân hệ Nghiệp vụ Cốt lõi (Backend), "
        "kết hợp phân tách độc lập Phân hệ Trợ lý Trí tuệ Nhân tạo thành một Microservice chuyên biệt. "
        "Sự kết hợp này được xây dựng dựa trên các luận cứ kỹ thuật vững chắc:",
        bold_prefix="Triết lý phân chia kiến trúc: "
    )
    add_p(
        " Toàn bộ nghiệp vụ được phân tách thành 14 Bounded Contexts hoàn toàn độc lập gồm: Cart (Giỏ hàng), Catalog (Danh mục & Dược mỹ phẩm), "
        "Chatbot (Cầu nối AI), CRM (Quản lý khách hàng & Phân quyền nhân sự), Dashboard (Phân tích chỉ số kinh doanh), "
        "Identity (Định danh, Phân quyền & Quản lý phiên), Inventory (Kho vận, Lô hàng & Hạn sử dụng FEFO), "
        "Notification (Thông báo đa kênh), Order (Đơn hàng thương mại), Payment (Thanh toán & Đối soát ngân hàng), "
        "Procurement (Mua hàng & Nhà cung cấp), Promotion (Mã giảm giá & Flash Sale), Review (Đánh giá & Trải nghiệm), "
        "và Spa (Phòng khám, Lịch hẹn, Buồng trị liệu, Vé liệu trình, Tiêu hao vật tư).",
        bold_prefix="1. Thiết kế theo Nguyên lý Domain-Driven Design (DDD): "
    )
    add_p(
        " Mỗi module tuân thủ cấu trúc 4 phân tầng độc lập: Tầng API (Tiếp nhận giao tiếp bên ngoài và chuyển đổi DTO), "
        "Tầng Application (Điều phối use-cases, thực thi logic nghiệp vụ và facade công khai), Tầng Domain (Chứa các Entity nghiệp vụ giàu hành vi, "
        "Value Objects, Enums và Domain Events), và Tầng Infrastructure (Thực thi lưu trữ cơ sở dữ liệu, bộ đệm và tích hợp bên thứ ba). "
        "Tính toàn vẹn của ranh giới ngữ cảnh được bảo vệ tuyệt đối bằng các bài kiểm thử kiểm toán kiến trúc tự động, "
        "cấm mọi hành vi truy cập chéo Repository hoặc tham chiếu trực tiếp Entity nội bộ của module khác.",
        bold_prefix="2. Kiểm soát ranh giới phân tầng nghiêm ngặt: "
    )
    add_p(
        " Do các tác vụ xử lý ma trận vector, tìm kiếm ngữ nghĩa Dense Vector, mô hình thị giác máy tính CLIP và điều phối hội thoại LLM "
        "đòi hỏi chu kỳ tính toán CPU/RAM chuyên sâu và sử dụng hệ sinh thái thư viện khoa học dữ liệu bằng Python, "
        "phân hệ này được tách thành một dịch vụ độc lập giao tiếp với Core Backend thông qua giao thức HTTP REST API nội bộ và Server-Sent Events.",
        bold_prefix="3. Độc lập hóa Phân hệ Trí tuệ Nhân tạo: "
    )

    add_h2("1.3. Sơ đồ kiến trúc triển khai tổng thể hệ thống")
    add_p(
        "Dưới đây là sơ đồ kiến trúc triển khai hoàn chỉnh của hệ thống DermaScan, minh họa sự phân bổ các tầng thành phần, "
        "cách thức container hóa, các điểm tiếp nhận lưu lượng và cơ chế giao tiếp giữa các phân hệ:"
    )

    # Chèn Sơ đồ 1
    insert_diagram(os.path.join(diagrams_dir, "diagram_1_deployment_architecture.png"),
                   "Hình 1.1: Sơ đồ kiến trúc triển khai tổng thể hệ thống DermaScan / BeautyShop")

    add_callout(
        "BẢN THUYẾT MINH MÔ TẢ CHI TIẾT SƠ ĐỒ KIẾN TRÚC TRIỂN KHAI (HÌNH 1.1):\n\n"
        "1. Tầng Trải nghiệm Người dùng (Client Layer - Phía trên cùng):\n"
        "   Hệ thống phục vụ 4 nhóm giao diện người dùng chuyên biệt: (1) Ứng dụng Web Khách hàng (Customer Web SPA) xây dựng trên nền tảng React 19 "
        "   cung cấp trải nghiệm mua sắm, đặt lịch hẹn và tư vấn trực tuyến; (2) Giao diện Lễ tân Tiếp đón Spa (Spa Staff Reception) hỗ trợ thủ tục check-in "
        "   khách hàng, phân bổ buồng điều trị và điều phối kỹ thuật viên; (3) Ứng dụng Di động Đa nền tảng (Mobile App) phát triển bằng Flutter "
        "   mang lại trải nghiệm native mượt mà trên Android và iOS; (4) Cổng Quản trị Trung tâm (Admin Portal) quản lý kho vận, nhập xuất hàng hóa và tài chính.\n\n"
        "2. Tầng Biên mạng Toàn cầu (Global Edge & Ingress Layer - Khối màu vàng):\n"
        "   Đóng vai trò tuyến phòng thủ đầu tiên tiếp nhận lưu lượng từ mạng Internet. Toàn bộ yêu cầu truy cập từ Web và Mobile "
        "   được định tuyến qua Mạng lưới Anycast CDN của Cloudflare, hưởng lợi từ hệ thống Tường lửa Ứng dụng Web (WAF) và Tự động chống tấn công DDoS. "
        "   Tại đây, công nghệ Cloudflare Tunnel thiết lập đường hầm mã hóa TLS hướng ra ngoài kết nối trực tiếp về máy chủ nội bộ, "
        "   giúp hệ thống public an toàn mà không cần mở bất kỳ cổng vào nào trên Router mạng.\n\n"
        "3. Tầng Cổng Đảo chiều & API Gateway (Nginx Reverse Proxy - Khối màu xám đậm):\n"
        "   Là điểm vào duy nhất mở cổng 80 tiếp nhận lưu lượng từ đường hầm Cloudflare Tunnel. Nginx đảm nhiệm các vai trò trọng yếu: "
        "   phục hồi địa chỉ IP thật của người dùng từ tiêu đề CF-Connecting-IP; áp dụng thuật toán Leaky Bucket giới hạn tần suất gọi API; "
        "   tối ưu hóa luồng phát trực tiếp Server-Sent Events bằng cách vô hiệu hóa bộ đệm proxy; truyền phát tệp hình ảnh tĩnh theo cơ chế zero-copy; "
        "   và chặn đứng mọi truy cập trái phép từ Internet vào các endpoint đo lường Actuator.\n\n"
        "4. Tầng Xử lý Nghiệp vụ & Trí tuệ Nhân tạo (Application & AI Core - Khối màu xanh dương & tím):\n"
        "   Bao gồm hai dịch vụ container cốt lõi: Backend Spring Boot (Java 21) quản lý toàn bộ 14 Bounded Contexts, xác thực phiên người dùng, "
        "   điều phối giao dịch và đẩy sự kiện Outbox; song song với đó là Chatbot Service (FastAPI / Python) thực thi quy trình NLU 6 tầng, "
        "   tìm kiếm đa kênh 3-Way Hybrid RAG và mô hình thị giác máy tính CLIP.\n\n"
        "5. Tầng Dữ liệu, Bộ đệm, Message Queue & Giám sát (Infrastructure Backbone - Hàng dưới cùng):\n"
        "   Hệ thống lưu trữ cơ sở dữ liệu quan hệ MySQL 8.0 được cấu hình bộ đệm InnoDB 384MB và đồng bộ giao dịch ACID nghiêm ngặt; "
        "   Bộ nhớ đệm phân tán Redis 7.2 kết hợp cơ chế khóa phân tán ShedLock; Xương sống truyền thông sự kiện Apache Kafka 3.7 vận hành ở chế độ KRaft; "
        "   Cơ sở dữ liệu vector Qdrant phục vụ tìm kiếm ngữ nghĩa; và cụm đo lường Prometheus/Grafana giám sát sức khỏe toàn diện theo thời gian thực.",
        bold_label=""
    )

    # -------------------------------------------------------------------------
    # PHẦN 2: DANH MỤC TOÀN BỘ NGÔN NGỮ LẬP TRÌNH ĐƯỢC SỬ DỤNG
    # -------------------------------------------------------------------------
    add_h1("PHẦN 2: DANH MỤC TOÀN BỘ NGÔN NGỮ LẬP TRÌNH VÀ VAI TRÒ")

    add_p(
        "Hệ thống là một công trình phần mềm đa ngôn ngữ (Polyglot Engineering), trong đó mỗi ngôn ngữ lập trình được cân nhắc "
        "và chỉ định chính xác cho phân hệ chức năng phát huy tối đa thế mạnh về mặt hiệu năng, an toàn kiểu dữ liệu và hệ sinh thái thư viện:"
    )

    lang_headers = ["Ngôn ngữ lập trình", "Phiên bản / Môi trường", "Phân hệ đảm nhiệm", "Vai trò và Trách nhiệm kỹ thuật cốt lõi"]
    lang_widths = [Inches(1.5), Inches(1.3), Inches(1.5), Inches(2.7)]
    lang_rows = [
        ["Java", "Java 21 (Temurin LTS)", "Core Backend Monolith", "Xây dựng khung logic nghiệp vụ cấp doanh nghiệp, bảo đảm an toàn kiểu dữ liệu hướng đối tượng, điều phối giao dịch ACID, phân quyền đa tầng và kiểm soát kiến trúc DDD."],
        ["Python", "Python 3.10 - 3.12", "AI Agent & RecSys Service", "Xử lý trí tuệ nhân tạo, tính toán đại số tuyến tính trên ma trận vector, thực thi giải thuật BM25/RRF, trích xuất đặc trưng hình ảnh CLIP và huấn luyện mô hình nhúng trên Colab."],
        ["JavaScript (ES2022+)", "Node.js 20 & 24 / Browser", "Web SPA & Automation", "Phát triển giao diện ứng dụng web Single Page hiện đại (React 19), điều phối luồng trạng thái máy khách, xử lý định dạng tệp báo cáo Excel và tự động hóa kiểm thử trình duyệt."],
        ["Dart", "Dart SDK 3.12+ (Flutter 3)", "Mobile Multiplatform App", "Biên dịch ứng dụng di động native đa nền tảng (Android, iOS, Desktop), bảo đảm tốc độ dựng hình mượt mà, quản lý trạng thái phản ứng và lưu trữ an toàn thiết bị."],
        ["SQL (MySQL Dialect)", "MySQL 8.0 Engine", "Lược đồ & Di trú Dữ liệu", "Đặc tả cấu trúc bảng, khóa ngoại, chỉ mục tìm kiếm và các ràng buộc toàn vẹn thông qua 70 bản di trú tự động Flyway; tối ưu hóa các phép tính giao dịch tài chính."],
        ["Shell Script (Bash & Batch)", "Windows (.bat) & Linux (.sh)", "DevOps & Vận hành", "Tự động hóa chu trình khởi tạo kết nối đường hầm bảo mật Cloudflare Tunnel, trích xuất an toàn biến môi trường và quản lý tiến trình máy chủ."],
        ["Config DSL (YAML, Nginx DSL)", "Docker Compose, Nginx, CI/CD", "Điều phối & Hạ tầng", "Định nghĩa hạ tầng 8 container dịch vụ, cấu hình quy tắc phân luồng Reverse Proxy, quy tắc bảo mật biên mạng và kịch bản kiểm thử tích hợp liên tục."]
    ]
    add_table_custom(lang_headers, lang_rows, lang_widths,
                     [WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT])

    add_h2("2.1. Phân tích chi tiết vai trò của các ngôn ngữ cốt lõi")
    add_p(
        "Việc lựa chọn Java 21 LTS cho phân hệ Backend xuất phát từ yêu cầu khắt khe về tính ổn định và độ tin cậy của một hệ thống "
        "thương mại điện tử kết hợp quản lý phòng khám y khoa. Java 21 cung cấp cơ chế quản lý bộ nhớ vượt trội, "
        "các tính năng cú pháp hiện đại như Record Classes giúp định nghĩa các Data Transfer Object (DTO) bất biến, "
        "Pattern Matching gia tăng tính an toàn khi phân rã các trạng thái đơn hàng phức tạp, và nền tảng máy ảo HotSpot "
        "đảm bảo khả năng xử lý hàng ngàn giao dịch đồng thời mà không suy giảm hiệu năng.",
        bold_prefix="• Ngôn ngữ Java (Phiên bản 21 LTS): "
    )
    add_p(
        "Python được lựa chọn làm ngôn ngữ trung tâm cho phân hệ Trí tuệ Nhân tạo nhờ sở hữu hệ sinh thái khoa học dữ liệu và học máy phong phú nhất thế giới. "
        "Python cho phép tích hợp mượt mà giữa các thư viện tính toán tensor như PyTorch, thư viện mô hình Transformer từ Hugging Face, "
        "bộ máy vector Qdrant Client, và các thuật toán xếp hạng văn bản BM25. Cấu trúc bất đồng bộ của Python kết hợp cùng FastAPI "
        "cung cấp khả năng xử lý các kết nối truyền phát Server-Sent Events (SSE) liên tục với độ trễ tính bằng mili-giây.",
        bold_prefix="• Ngôn ngữ Python (Phiên bản 3.10 - 3.12): "
    )
    add_p(
        "Hệ sinh thái JavaScript hiện đại (ECMAScript 2022 trở lên) trên nền tảng React 19 cung cấp khả năng xây dựng giao diện người dùng "
        "tương tác cao theo cơ chế cây thành phần (Component Tree). Việc sử dụng JavaScript chuẩn giúp giao diện dễ dàng quản lý các trạng thái phức tạp "
        "như giỏ hàng, bộ lọc danh mục đa tiêu chí, sơ đồ giường phòng spa theo thời gian thực và kết nối WebSocket cập nhật thanh toán.",
        bold_prefix="• Ngôn ngữ JavaScript / TypeScript: "
    )
    add_p(
        "Dart được thiết kế tối ưu cho phát triển giao diện phía máy khách với khả năng biên dịch Trước thời hạn (Ahead-Of-Time - AOT) "
        "thành mã máy ARM native cho các thiết bị di động. Điều này giúp ứng dụng khởi động tức thì, loại bỏ độ trễ thu gom rác (Jank-free) "
        "và mang lại trải nghiệm cảm ứng trực quan như ứng dụng gốc của hệ điều hành.",
        bold_prefix="• Ngôn ngữ Dart (Bộ công cụ Flutter): "
    )

    # -------------------------------------------------------------------------
    # PHẦN 3: HỆ SINH THÁI CÔNG NGHỆ, FRAMEWORK VÀ CÔNG CỤ
    # -------------------------------------------------------------------------
    add_h1("PHẦN 3: DANH MỤC TOÀN BỘ CÔNG NGHỆ, FRAMEWORK VÀ CÔNG CỤ")

    add_h2("3.1. Phân hệ Backend & Lõi nghiệp vụ doanh nghiệp")
    add_p("• Spring Boot 3.4.3: Khung phát triển ứng dụng cấp doanh nghiệp hàng đầu thế giới, cung cấp cơ chế tự động cấu hình và quản lý vòng đời bean tối ưu.")
    add_p("• Spring Security & JJWT 0.12.6: Hệ thống kiểm soát truy cập và bảo vệ danh tính đa tầng. Áp dụng mô hình Access Token ngắn hạn (15 phút) kết hợp Refresh Token Session lưu trữ trong cơ sở dữ liệu. Bổ sung cơ chế Quản lý Phiên bản Token (Token Versioning) cho phép vô hiệu hóa tức thời toàn bộ các phiên hoạt động của người dùng khi có sự cố đổi mật khẩu hoặc phát hiện xâm nhập.")
    add_p("• Spring Data JPA & Hibernate 6: Công nghệ ánh xạ quan hệ đối tượng (ORM) tiên tiến, tự động hóa các thao tác cơ sở dữ liệu với tiêu chí tối ưu hóa câu truy vấn, hỗ trợ cơ chế Specification xây dựng bộ lọc tìm kiếm động.")
    add_p("• Flyway Database Migration Engine: Quản lý phiên bản cấu trúc cơ sở dữ liệu tự động với 70 tệp kịch bản di trú tuần tự từ V1 đến V70, bảo đảm tính đồng nhất schema tuyệt đối trên toàn bộ các môi trường phát triển, kiểm thử và sản xuất.")
    add_p("• Apache Kafka & Spring Kafka: Nền tảng luồng sự kiện phân tán vận hành ở kiến trúc KRaft mode (loại bỏ phụ thuộc ZooKeeper), đảm nhận vai trò xương sống truyền thông tin bất đồng bộ cho đơn hàng, sự kiện liệu trình spa và gửi thông báo.")
    add_p("• Transactional Outbox Pattern: Cơ chế đảm bảo tính nhất quán dữ liệu phân tán (At-least-once delivery). Các sự kiện biến động nghiệp vụ được ghi trực tiếp vào bảng outbox trong cùng giao dịch ACID của MySQL trước khi được đẩy lên Kafka, ngăn ngừa triệt để nguy cơ thất thoát dữ liệu khi có sự cố mạng.")
    add_p("• Caffeine Cache & Spring Data Redis: Mô hình bộ đệm hai tầng kết hợp: Tầng L1 cục bộ trong bộ nhớ RAM máy ảo Java bằng Caffeine và Tầng L2 phân tán bằng Redis 7.2 cho các dữ liệu có tần suất đọc lớn như danh mục, cấu hình và bảng giá dịch vụ.")
    add_p("• ShedLock: Cơ chế khóa phân tán dựa trên nền tảng Redis, bảo đảm các tác vụ lập lịch định kỳ (như tác vụ tự động dọn dẹp dữ liệu lưu trữ cũ vào ban đêm) chỉ được thực thi duy nhất bởi một máy chủ trong cụm.")
    add_p("• Spring WebSocket & STOMP Protocol: Giao thức truyền thông hai chiều thời gian thực giữa máy chủ và giao diện người dùng, phục vụ đồng bộ hóa trạng thái giao dịch ngân hàng VietQR và thông báo tiếp đón buồng trị liệu tức thời.")
    add_p("• ArchUnit: Thư viện kiểm thử kiến trúc tự động, đóng vai trò chốt chặn kiểm toán ranh giới giữa 14 Bounded Contexts trong quá trình biên dịch phần mềm, ngăn ngừa việc vi phạm kiến trúc Modular Monolith.")
    add_p("• Testcontainers: Khởi tạo các container cơ sở dữ liệu MySQL độc lập và sạch sẽ trong quá trình chạy kiểm thử tích hợp, bảo đảm môi trường kiểm thử tương đồng 100% với hạ tầng thực tế.")

    add_h2("3.2. Phân hệ Trí tuệ Nhân tạo & Tìm kiếm Thị giác")
    add_p("• FastAPI & Uvicorn: Web framework bất đồng bộ hiệu năng cao viết bằng Python, cung cấp nền tảng truyền phát dữ liệu thời gian thực theo chuẩn Server-Sent Events (SSE).")
    add_p("• Pydantic v2: Bộ công cụ thẩm định và chuẩn hóa cấu trúc dữ liệu JSON đầu vào và đầu ra của mô hình ngôn ngữ lớn với tốc độ cao.")
    add_p("• Qdrant Vector Database: Cơ sở dữ liệu vector chuyên dụng, lưu trữ và lập chỉ mục không gian vector nhúng ngữ nghĩa 768 chiều với thuật toán HNSW, cho phép thực thi các phép tìm kiếm láng giềng gần nhất (ANN) với độ trễ dưới 10 mili-giây.")
    add_p("• rank-bm25: Triển khai giải thuật tìm kiếm từ vựng xác suất BM25Okapi, phục vụ kênh truy xuất các thuật ngữ y khoa và tên hoạt chất mỹ phẩm cụ thể.")
    add_p("• Google Gemini API (Gemini 1.5 Flash / Flash-lite): Mô hình ngôn ngữ lớn đóng vai trò hạt nhân suy luận, chịu trách nhiệm diễn giải ngôn ngữ tự nhiên, phân tích triệu chứng da và biên soạn câu trả lời tư vấn có căn cứ y khoa.")
    add_p("• Sentence-Transformers & CLIP (ViT-B-32): Mô hình thị giác ngôn ngữ chạy trên nền PyTorch, thực hiện trích xuất vector đặc trưng hình ảnh 512 chiều từ ảnh chụp bao bì sản phẩm do khách hàng tải lên để tìm kiếm sản phẩm tương đồng.")
    add_p("• SQLAlchemy 2.0: Tầng truy cập dữ liệu đa năng, cho phép Chatbot đọc dữ liệu tồn kho thời gian thực từ bản sao MySQL hoặc cơ sở dữ liệu SQLite đệm.")

    add_h2("3.3. Phân hệ Giao diện Người dùng Web & Di động")
    add_p("• React 19: Nền tảng xây dựng giao diện ứng dụng web hiện đại với cơ chế render tối ưu và khả năng quản lý trạng thái phản ứng linh hoạt.")
    add_p("• Vite 8: Bộ công cụ biên dịch và đóng gói ứng dụng web thế hệ mới, tối ưu hóa kích thước tệp xuất bản tĩnh và tăng tốc độ tải trang.")
    add_p("• Lucide React: Hệ thống biểu tượng vector chuẩn hóa, sắc nét và có dung lượng nhẹ.")
    add_p("• Axios: Thư viện giao tiếp HTTP với các bộ chặn thông minh tự động đính kèm mã xác thực JWT và tự động làm mới phiên khi hết hạn.")
    add_p("• SheetJS: Thư viện trích xuất dữ liệu báo cáo kinh doanh, thống kê tiêu hao vật tư spa và sổ cái thanh toán ra định dạng bảng tính Excel.")
    add_p("• Oxlint: Công cụ kiểm tra an toàn mã nguồn và quy chuẩn cú pháp viết bằng Rust, nâng cao tốc độ kiểm thử tĩnh gấp nhiều lần so với các linter truyền thống.")
    add_p("• Playwright: Khung tự động hóa kiểm thử trình duyệt End-to-End, mô phỏng toàn diện hành vi người dùng thật trên các trình duyệt Chromium, Firefox và WebKit.")
    add_p("• Flutter 3.x & Provider: Bộ công cụ UI đa nền tảng và cơ chế quản lý trạng thái chuẩn mực, tạo lập ứng dụng di động mượt mà cho cả hệ điều hành Android và iOS.")

    add_h2("3.4. Phân hệ Hạ tầng, Mạng & Vận hành Giám sát (DevOps & APM)")
    add_p("• Docker & Docker Compose: Công nghệ đóng gói và điều phối 8 container dịch vụ đồng bộ, bảo đảm tính tái lập môi trường tuyệt đối.")
    add_p("• Nginx Alpine: Máy chủ web và Reverse Proxy siêu nhẹ (<30MB RAM), chịu trách nhiệm định tuyến, nén dữ liệu, bảo mật và cân bằng tải.")
    add_p("• Cloudflare Tunnel (daemon cloudflared): Giải pháp kết nối mạng riêng ảo mã hóa đưa ứng dụng nội bộ ra Internet mà không cần mở cổng trên tường lửa.")
    add_p("• Prometheus 2.51: Hệ thống cơ sở dữ liệu chuỗi thời gian thu thập các chỉ số vận hành chi tiết của máy chủ và ứng dụng theo chu kỳ 5 giây một lần.")
    add_p("• Grafana 10.4: Bảng điều khiển phân tích số liệu trực quan, theo dõi mức độ chiếm dụng CPU, RAM, dung lượng kết nối cơ sở dữ liệu và tần suất lỗi HTTP.")
    add_p("• GitHub Actions: Nền tảng tự động hóa toàn diện quy trình kiểm thử và tích hợp liên tục (CI/CD) với 5 luồng công việc độc lập.")

    # -------------------------------------------------------------------------
    # PHẦN 4: LUỒNG DỮ LIỆU VÀ TÍCH HỢP NGHIỆP VỤ ĐA DỊCH VỤ
    # -------------------------------------------------------------------------
    add_h1("PHẦN 4: LUỒNG DỮ LIỆU VÀ TÍCH HỢP NGHIỆP VỤ ĐA DỊCH VỤ")

    add_p(
        "Nhằm làm sáng tỏ phương thức tương tác giữa các phân hệ công nghệ trong quá trình vận hành thực tế, "
        "dưới đây là sơ đồ chi tiết biểu diễn 4 luồng nghiệp vụ cốt lõi của hệ thống:"
    )

    # Chèn Sơ đồ 2
    insert_diagram(os.path.join(diagrams_dir, "diagram_2_data_flow_integration.png"),
                   "Hình 4.1: Sơ đồ luồng xử lý dữ liệu và tích hợp nghiệp vụ đa dịch vụ")

    add_callout(
        "BẢN THUYẾT MINH MÔ TẢ CHI TIẾT SƠ ĐỒ LUỒNG DỮ LIỆU & TÍCH HỢP (HÌNH 4.1):\n\n"
        "1. Luồng 1 - Xác thực Thanh toán Tự động VietQR (Khối màu xanh lá cây):\n"
        "   Khi khách hàng thực hiện chuyển khoản bằng mã VietQR, Cổng thanh toán SePay nhận biến động số dư từ Ngân hàng MBBank "
        "   và tự động phát tín hiệu Webhook an toàn đến endpoint tiếp nhận của hệ thống. Yêu cầu đi qua Nginx Reverse Proxy "
        "   và được chuyển tiếp tới Module Payment của Backend Spring Boot. Hệ thống thực hiện giải thuật băm so sánh khóa bảo mật "
        "   theo thời gian không đổi nhằm ngăn chặn tấn công Timing Attack. Ngay khi giao dịch được xác thực hợp lệ, "
        "   trạng thái đơn hàng được cập nhật thành Đã Thanh Toán (PAID) trong cơ sở dữ liệu, đồng thời máy chủ đẩy thông điệp tức thời "
        "   qua kênh WebSocket STOMP tới màn hình khách hàng, giúp giao diện tự động chuyển sang trang Hóa đơn hoàn tất trong tích tắc.\n\n"
        "2. Luồng 2 - Tư vấn Da liễu Thời gian thực qua Advanced Hybrid RAG (Khối màu tím):\n"
        "   Người dùng nhập câu hỏi tư vấn về tình trạng da hoặc tìm kiếm sản phẩm. Luồng dữ liệu đi qua Nginx với cấu hình tắt bộ đệm proxy "
        "   để phục vụ truyền phát dữ liệu liên tục Server-Sent Events (SSE). Bộ não AI Agent kích hoạt Phân tích NLU 6 tầng "
        "   để trích xuất ý định, loại da chỉ định, hoạt chất mong muốn và thành phần kiêng kỵ. Hệ thống kích hoạt cơ chế Truy xuất Kết hợp 3 kênh song song "
        "   (SQL Filter + BM25 Lexical + Qdrant Dense Vector) và hợp nhất thứ hạng bằng thuật toán Reciprocal Rank Fusion (RRF, k=60). "
        "   Trước khi sinh câu trả lời, chốt chặn Kiểm duyệt Tồn kho Khả dụng (Live Inventory Guardrail) trực tiếp đối soát số lượng tồn khả dụng "
        "   và hạn dùng của từng lô hàng, loại bỏ triệt để các sản phẩm hết hàng khỏi ngữ cảnh trước khi đưa vào mô hình Gemini LLM để sinh token phản hồi.\n\n"
        "3. Luồng 3 - Tìm kiếm Sản phẩm bằng Hình ảnh qua Mô hình CLIP (Khối màu xanh dương):\n"
        "   Khách hàng tải lên ảnh chụp sản phẩm từ Web hoặc ứng dụng di động. Nginx và Backend tiếp nhận, kiểm soát chặt chẽ kích thước (tối đa 5MB) "
        "   và loại tệp an toàn (JPEG, PNG, WebP). Tệp ảnh được chuyển tiếp tới dịch vụ AI để nhánh Vision Encoder của mô hình CLIP (ViT-B-32) "
        "   trích xuất vector đặc trưng thị giác 512 chiều đã chuẩn hóa L2. Vector này được so khớp khoảng cách Cosine với cơ sở dữ liệu "
        "   vector ảnh sản phẩm đã lập chỉ mục sẵn, trích xuất Top-K sản phẩm có độ tương đồng hình học cao nhất, "
        "   kết hợp kiểm tra trạng thái kinh doanh và tồn kho thực tế trước khi hiển thị cho người dùng.\n\n"
        "4. Luồng 4 - Phân phối Sự kiện Phân tán qua Transactional Outbox & Kafka (Khối màu vàng cam):\n"
        "   Mọi hành động làm thay đổi trạng thái nghiệp vụ then chốt (như đặt hàng thành công, trừ số lượt vé spa, hoàn tiền) "
        "   được ghi đồng thời vào bảng chính và bảng thông điệp Outbox trong cùng một giao dịch ACID của MySQL. "
        "   Tiến trình quét Outbox định kỳ đọc các thông điệp chưa gửi và đẩy lên các Topic tương ứng trên Apache Kafka Broker. "
        "   Các Consumer độc lập tiếp nhận sự kiện để kích hoạt các nghiệp vụ thứ cấp: tự động cập nhật số dư kho vận, "
        "   cộng điểm thành viên thân thiết (Loyalty Points), tạo thông báo đẩy trên ứng dụng và kích hoạt gửi email xác nhận qua giao thức SMTP.",
        bold_label=""
    )

    # -------------------------------------------------------------------------
    # PHẦN 5: ĐÁNH GIÁ KHẢ NĂNG PUBLIC VÀ KIẾN TRÚC AN NINH BIÊN MẠNG
    # -------------------------------------------------------------------------
    add_h1("PHẦN 5: ĐÁNH GIÁ KHẢ NĂNG PUBLIC VÀ KIẾN TRÚC AN NINH BIÊN MẠNG")

    add_h2("5.1. Khẳng định trạng thái Public và Hệ thống Tên miền chính thức")
    add_p(
        "Hệ thống DermaScan / BeautyShop HOÀN TOÀN ĐƯỢC THIẾT KẾ ĐỂ PUBLIC CÔNG KHAI RA MẠNG INTERNET TOÀN CẦU. "
        "Toàn bộ cấu trúc định tuyến mạng, cấu hình cổng dịch vụ, chính sách bảo mật máy chủ và ứng dụng di động "
        "đều đã được tích hợp đồng bộ với hệ thống tên miền công cộng chính thức:"
    )

    domain_headers = ["Phân hệ / Cổng dịch vụ", "Địa chỉ Tên miền Public", "Giao thức", "Cơ chế Ánh xạ & Bảo vệ"]
    domain_widths = [Inches(1.8), Inches(2.2), Inches(0.9), Inches(2.4)]
    domain_rows = [
        ["Giao diện Web Khách hàng", "https://www.dermascan.world", "HTTPS (SSL)", "Phân phối qua Cloudflare Pages & Mạng lưới Edge CDN toàn cầu"],
        ["Cổng API Gateway Backend", "https://api.dermascan.world", "HTTPS (SSL)", "Cloudflare Tunnel -> Nginx cổng 80 -> Cụm Spring Boot Backend"],
        ["Cổng Webhook Ngân hàng", "https://api.dermascan.world/api/v1/payments/sepay/webhook", "HTTPS (SSL)", "SePay tự động đẩy thông tin giao dịch ngân hàng thời gian thực"],
        ["API Trợ lý AI Streaming", "https://api.dermascan.world/api/v1/chatbot/chat/stream", "HTTPS (SSE)", "Nginx tắt bộ đệm, truyền phát dòng token liên tục tới máy khách"],
        ["API Tìm kiếm Hình ảnh", "https://api.dermascan.world/api/v1/products/search-by-image", "HTTPS (SSL)", "Giới hạn payload 5.3MB, kiểm duyệt an toàn file trước khi gọi CLIP"]
    ]
    add_table_custom(domain_headers, domain_rows, domain_widths,
                     [WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT])

    add_h2("5.2. Sơ đồ phân tầng bảo mật và cô lập hạ tầng mạng")
    add_p(
        "Khi đưa một hệ thống có chứa dữ liệu y tế da liễu và giao dịch tài chính ra mạng Internet, "
        "an toàn thông tin là ưu tiên hàng đầu. Dưới đây là sơ đồ phân tầng phòng thủ 4 lớp theo triết lý Zero-Trust Network:"
    )

    # Chèn Sơ đồ 3
    insert_diagram(os.path.join(diagrams_dir, "diagram_3_security_network_isolation.png"),
                   "Hình 5.1: Sơ đồ phân tầng bảo mật và cô lập hạ tầng mạng khi Public ra Internet")

    add_callout(
        "BẢN THUYẾT MINH MÔ TẢ CHI TIẾT SƠ ĐỒ AN NINH HẠ TẦNG (HÌNH 5.1):\n\n"
        "1. Vùng 1 - Internet Công cộng (Vùng Không tin cậy - Cột màu đỏ ngoài cùng bên trái):\n"
        "   Bao gồm toàn bộ người dùng hợp lệ truy cập từ trình duyệt web, ứng dụng di động, đối tác ngân hàng SePay, "
        "   đồng thời cũng là nơi xuất phát của các nguy cơ tiềm ẩn: tin tặc rà quét cổng, các cuộc tấn công từ chối dịch vụ (DDoS), "
        "   robot dò quét mã nguồn và các nỗ lực tấn công vét cạn mật khẩu (Brute-Force). "
        "   Nguyên tắc bất di bất dịch của kiến trúc là KHÔNG MỘT KẾT NỐI NÀO từ vùng này được phép đi thẳng vào máy chủ nội bộ.\n\n"
        "2. Vùng 2 - Biên mạng Toàn cầu Cloudflare (Vùng Phòng thủ Biên - Cột màu vàng cam):\n"
        "   Lưu lượng truy cập từ Internet được tiếp nhận bởi hơn 300 trung tâm dữ liệu Anycast của Cloudflare. "
        "   Tại đây, hệ thống được hưởng lợi từ các lớp bảo vệ tự động: hấp thụ và triệt tiêu các cuộc tấn công DDoS ở tầng mạng L3/L4 và tầng ứng dụng L7; "
        "   tự động phân tích và ngăn chặn các mẫu tấn công SQL Injection hoặc Cross-Site Scripting thông qua Web Application Firewall (WAF); "
        "   tự động quản lý và cấp phát chứng chỉ số SSL/TLS với thuật toán mã hóa đầu cuối hiện đại. "
        "   Đặc biệt, daemon Cloudflare Tunnel thiết lập đường hầm mã hóa TLS hướng ra ngoài (Outbound) kết nối về máy chủ cục bộ, "
        "   giúp che giấu hoàn toàn địa chỉ IP gốc của máy chủ trước thế giới Internet.\n\n"
        "3. Vùng 3 - Nginx Security Gateway DMZ (Vùng Lọc Lưu lượng - Cột màu xanh dương):\n"
        "   Là chốt chặn duy nhất mở cổng 80 tiếp nhận dữ liệu từ đường hầm Cloudflare Tunnel. Nginx thực thi các nhiệm vụ kiểm soát an toàn nghiêm ngặt: "
        "   phục hồi địa chỉ IP gốc thật của người dùng từ 15 dải IPv4 chính thức của Cloudflare; áp dụng thuật toán Leaky Bucket "
        "   giới hạn tần suất 30 lượt/giây cho API thông thường và siết chặt 5 lượt/giây cho các API đăng nhập để chống Brute-force; "
        "   áp dụng luật cấm tuyệt đối (HTTP 403 Forbidden) đối với mọi yêu cầu truy cập từ bên ngoài vào đường dẫn đo lường /actuator/; "
        "   bổ sung đầy đủ 4 tiêu đề bảo mật HTTP chuẩn (X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, Referrer-Policy).\n\n"
        "4. Vùng 4 - Mạng Nội bộ Docker Cô lập (Vùng Riêng tư An toàn Tuyệt đối - Cột màu xanh lá bên phải):\n"
        "   Toàn bộ các cổng dịch vụ nhạy cảm bao gồm Cơ sở dữ liệu MySQL (3307), Bộ nhớ đệm Redis (6379), Message Broker Kafka (9092), "
        "   Backend Spring Boot (8080), Chatbot AI (18000), Prometheus (9090) và Grafana (3000) đều được cấu hình gắn chặt (Port Binding) "
        "   với địa chỉ vòng lặp cục bộ 127.0.0.1. Điều này bảo đảm không một thiết bị nào trong mạng LAN hay ngoài Internet có thể kết nối trực tiếp "
        "   tới các dịch vụ này nếu không đi qua các tầng kiểm soát của Nginx.",
        bold_label=""
    )

    # -------------------------------------------------------------------------
    # PHẦN 6: QUY TRÌNH SETUP VÀ HƯỚNG DẪN TRIỂN KHAI VẬN HÀNH
    # -------------------------------------------------------------------------
    add_h1("PHẦN 6: QUY TRÌNH SETUP VÀ HƯỚNG DẪN TRIỂN KHAI VẬN HÀNH")

    add_h2("6.1. Yêu cầu môi trường tiền đề và Chuẩn bị hạ tầng")
    add_p(
        "Để khởi chạy thành công toàn bộ hệ sinh thái dịch vụ của đề tài, môi trường phần cứng và phần mềm máy chủ "
        "cần thỏa mãn các tiêu chuẩn kỹ thuật sau:",
        bold_prefix="Điều kiện tiền đề: "
    )
    add_p("• Hệ điều hành: Tương thích hoàn toàn với Windows 10/11 64-bit (sử dụng nhân ảo hóa WSL2) hoặc các bản phân phối Linux như Ubuntu 22.04 LTS / 24.04 LTS.")
    add_p("• Cấu hình phần cứng tối thiểu: Bộ nhớ RAM 16GB (khuyến nghị 32GB RAM để vận hành mượt mà đồng thời 8 container dịch vụ, nạp mô hình thị giác CLIP vào bộ nhớ và duy trì cơ sở dữ liệu), Vi xử lý tối thiểu 4 nhân / 8 luồng, và ổ cứng thể rắn SSD còn trống tối thiểu 50GB dung lượng lưu trữ.")
    add_p("• Công cụ phần mềm cài đặt: Docker Desktop phiên bản 25.0 trở lên (hỗ trợ Compose v2), Git, Môi trường máy ảo Java JDK 21 (Eclipse Temurin), Môi trường Node.js phiên bản 20 hoặc 24 LTS, Môi trường Python phiên bản 3.10 trở lên, Bộ công cụ Flutter SDK 3.x, và công cụ dòng lệnh Cloudflared.")

    add_h2("6.2. Thiết lập cấu hình biến môi trường an toàn (Twelve-Factor App)")
    add_p(
        "Hệ thống tuân thủ nghiêm ngặt nguyên tắc Twelve-Factor trong phát triển phần mềm hiện đại: tách biệt hoàn toàn thông tin cấu hình nhạy cảm "
        "khỏi mã nguồn lưu trên kho Git. Các tham số bí mật bao gồm mật khẩu cơ sở dữ liệu MySQL, khóa bí mật ký số JWT (được tạo ngẫu nhiên "
        "bằng thuật toán sinh chuỗi an toàn tối thiểu 32 byte), mã khóa xác thực Webhook SePay, thông tin tài khoản ngân hàng thụ hưởng, "
        "mã Token của đường hầm Cloudflare Tunnel, và API Key của mô hình Google Gemini đều được lưu trữ trong tệp cấu hình môi trường cục bộ "
        "và được loại trừ khỏi kho lưu trữ mã nguồn thông qua tệp cấu hình bảo vệ Git.",
        bold_prefix="Chính sách bảo mật cấu hình: "
    )

    add_h2("6.3. Sơ đồ quy trình CI/CD và Chu trình điều phối dịch vụ Docker Compose")
    add_p(
        "Quá trình xây dựng, kiểm thử tự động và điều phối khởi động hệ thống được chuẩn hóa qua hai quy trình kỹ thuật độc lập "
        "nhưng liên kết chặt chẽ: Pipeline Tích hợp Liên tục (CI/CD) trên GitHub Actions và Chuỗi điều phối khởi động có kiểm tra sức khỏe trên Docker Compose:"
    )

    # Chèn Sơ đồ 4
    insert_diagram(os.path.join(diagrams_dir, "diagram_4_orchestration_ci_cd.png"),
                   "Hình 6.1: Sơ đồ quy trình CI/CD tự động hóa và Chu trình điều phối khởi động hệ thống")

    add_callout(
        "BẢN THUYẾT MINH MÔ TẢ CHI TIẾT SƠ ĐỒ ĐIỀU PHỐI VẬN HÀNH (HÌNH 6.1):\n\n"
        "1. Phần A - Pipeline Tự động hóa CI/CD trên GitHub Actions (Nửa trên sơ đồ):\n"
        "   Quy trình kiểm thử chất lượng mã nguồn tự động được kích hoạt mỗi khi có mã nguồn mới được đẩy lên các nhánh chính. "
        "   Pipeline bao gồm 5 công việc (Jobs) chạy tuần tự và song song:\n"
        "   • Job 1 (Backend CI): Khởi tạo môi trường JDK 21 Temurin, biên dịch mã nguồn, thực thi toàn bộ các bài kiểm tra đơn vị (Unit Tests) "
        "     và đặc biệt là kích hoạt ArchUnit Tests để xác thực tính toàn vẹn của 14 Bounded Contexts, bảo đảm không có vi phạm ranh giới DDD.\n"
        "   • Job 2 (Docker Build): Ứng dụng Docker Buildx cùng bộ đệm phân tầng GitHub Cache để xây dựng Docker image cho Backend "
        "     theo phương pháp Multi-stage Build, tạo ra image tối ưu dung lượng và tuyệt đối không nhúng các thông tin bí mật vào image.\n"
        "   • Job 3 (DevOps Validation): Kiểm tra tính hợp lệ về mặt cú pháp của tệp cấu hình Docker Compose v2, "
        "     đồng thời khởi tạo container Nginx thử nghiệm để kiểm tra tính toàn vẹn cú pháp của tệp cấu hình nginx.conf trước khi xuất bản.\n"
        "   • Job 4 (Mobile CI): Thiết lập môi trường Flutter 3.x, thực hiện phân tích mã nguồn tĩnh (Flutter Analyze) để loại bỏ cảnh báo "
        "     và chạy bộ kiểm thử kiểm tra tính đồng bộ tính năng giữa Web và Mobile.\n"
        "   • Job 5 (Frontend CI): Thiết lập Node.js, thẩm định các biến môi trường phía máy khách nhằm chặn đứng việc lộ lọt secret, "
        "     và thực thi biên dịch gói tệp tĩnh sẵn sàng xuất bản.\n\n"
        "2. Phần B - Chu trình Điều phối Khởi động Hệ thống trên Docker Compose (Nửa dưới sơ đồ):\n"
        "   Để giải quyết triệt để vấn đề xung đột thời gian khởi động (Race Conditions) trong các hệ thống phân tán, "
        "   Docker Compose áp dụng chuỗi điều phối phụ thuộc dựa trên bài kiểm tra sức khỏe thực tế (Healthcheck Conditions) qua 4 bước:\n"
        "   • Bước 1 (Hạ tầng Dữ liệu): Khởi động đồng thời MySQL 8.0, Redis 7.2 và Kafka 3.7. Container MySQL thực hiện bài kiểm tra định kỳ "
        "     đến khi cơ sở dữ liệu hoàn tất mở cổng tiếp nhận kết nối (khoảng 15-20 giây).\n"
        "   • Bước 2 (Lõi Backend & Di trú Lược đồ): Khi MySQL đã khỏe mạnh, container Backend mới bắt đầu nạp cấu hình và kích hoạt "
        "     công cụ Flyway chạy tuần tự 70 bản di trú dữ liệu (V1 đến V70). Khi Backend vượt qua bài kiểm tra sức khỏe tại endpoint /actuator/health "
        "     (trạng thái UP), bước tiếp theo mới được kích hoạt.\n"
        "   • Bước 3 (Trí tuệ Nhân tạo & Web Frontend): Container Chatbot AI và Frontend khởi động. Chatbot kết nối tới cơ sở dữ liệu đã sẵn sàng, "
        "     nạp chỉ mục vector Qdrant và thiết lập cầu nối với mô hình Gemini.\n"
        "   • Bước 4 (Cổng Gateway & Giám sát): Container Nginx kiểm tra tính khả dụng của toàn bộ các dịch vụ upstream, "
        "     mở cổng 80 tiếp nhận lưu lượng; Prometheus bắt đầu chu kỳ thu thập chỉ số; Grafana kích hoạt bảng điều khiển; "
        "     và Cloudflare Tunnel mở đường hầm đưa toàn bộ hệ thống online an toàn.",
        bold_label=""
    )

    add_h2("6.4. Bộ kịch bản kiểm tra nghiệm thu chất lượng hệ thống (Smoke Tests & E2E)")
    add_p(
        "Sau khi hoàn tất quá trình triển khai, kỹ sư vận hành thực hiện bộ quy trình kiểm thử nghiệm thu 3 tầng "
        "nhằm bảo đảm mọi phân hệ chức năng hoạt động hoàn hảo trong môi trường thực tế:",
        bold_prefix="Quy trình nghiệm thu kỹ thuật: "
    )
    add_p("1. Kiểm tra Sức khỏe Phân hệ Cốt lõi: Truy vấn trạng thái tại các endpoint kiểm tra sức khỏe của Backend và Chatbot. Hệ thống yêu cầu trạng thái phản hồi phải đạt chỉ số khả dụng hoàn toàn (UP) cho toàn bộ các thành phần kết nối gồm Cơ sở dữ liệu, Bộ đệm Redis, và Khung liên lạc Kafka.")
    add_p("2. Kiểm thử Tích hợp Trí tuệ Nhân tạo: Kích hoạt kịch bản kiểm thử API tự động của Chatbot nhằm đối soát khả năng truy vấn dữ liệu tồn kho thật từ MySQL, kiểm tra độ trễ phản hồi từ mô hình ngôn ngữ lớn Gemini, và bảo đảm tính toàn vẹn của luồng truyền phát sự kiện Server-Sent Events từ đầu đến cuối.")
    add_p("3. Kiểm thử Tự động hóa Trình duyệt Toàn diện (Playwright E2E): Khởi chạy bộ kịch bản kiểm thử trình duyệt tự động trên nền tảng Playwright. Robot kiểm thử tự động mô phỏng chu trình người dùng thực tế: đăng nhập hệ thống bằng tài khoản khách hàng, duyệt xem danh mục sản phẩm y tế, mở cửa sổ tư vấn trực tuyến với Trợ lý AI, thêm sản phẩm được gợi ý vào giỏ hàng, và dọn dẹp dữ liệu phiên kiểm thử sau khi hoàn tất.")
    add_p("4. Đánh giá Năng lực Chịu tải (Load Testing K6): Căn cứ báo cáo kiểm thử tải độc lập trong tài liệu vận hành của dự án, hệ thống đã vượt qua bài kiểm thử tải mô phỏng 2.000 yêu cầu đồng thời với tỷ lệ lỗi 0% và thời gian phản hồi trung bình duy trì dưới 120 mili-giây đối với các API nghiệp vụ quan trọng.")

    add_h2("6.5. Quy trình bảo trì định kỳ, Sao lưu và Phục hồi sự cố (Disaster Recovery)")
    add_p(
        "1. Cơ chế Tự động Dọn dẹp Dữ liệu Lưu trữ (DataRetentionJob): Nhằm ngăn ngừa hiện tượng phình to kích thước cơ sở dữ liệu làm suy giảm hiệu năng chỉ mục, "
        "hệ thống thiết lập tác vụ định kỳ tự động kích hoạt vào lúc 03:00 sáng mỗi Chủ Nhật hàng tuần. Dưới sự bảo vệ của khóa phân tán ShedLock, "
        "tác vụ thực hiện dọn dẹp các bản ghi thông điệp Outbox đã hoàn tất phân phối và xóa sạch các phiên Refresh Token đã hết hạn quá 14 ngày."
    )
    add_p(
        "2. Quy trình Sao lưu Cơ sở Dữ liệu Định kỳ: Toàn bộ dữ liệu của hệ thống MySQL được sao lưu tự động ra các tệp kết xuất dạng SQL "
        "được lưu trữ tại thư mục bảo vệ nội bộ. Tệp sao lưu được gắn nhãn thời gian chính xác, bảo đảm khả năng phục hồi dữ liệu về bất kỳ thời điểm nào trong quá khứ."
    )
    add_p(
        "3. Kế hoạch Phục hồi Thảm họa (Disaster Recovery): Khi phát sinh sự cố phần cứng hoặc thảm họa máy chủ, kỹ sư vận hành kích hoạt "
        "quy trình khôi phục căn cứ theo tài liệu vận hành đã thẩm định trong mã nguồn. Cơ chế Flyway Migration tự động đối soát tổng kiểm tra (checksum) "
        "và khôi phục hệ thống trở lại trạng thái hoạt động bình thường trong thời gian dưới 5 phút mà không làm mất mát bất kỳ giao dịch nào của người dùng."
    )

    # -------------------------------------------------------------------------
    # PHẦN 7: KẾT LUẬN VÀ ĐÁNH GIÁ TỔNG KẾT
    # -------------------------------------------------------------------------
    add_h1("PHẦN 7: KẾT LUẬN VÀ ĐÁNH GIÁ TỔNG KẾT")

    add_p(
        "Hệ thống DermaScan / BeautyShop là một công trình phần mềm hoàn chỉnh, phản ánh trình độ làm chủ công nghệ và tư duy kiến trúc "
        "vững vàng trong việc giải quyết bài toán nghiệp vụ phức hợp kết hợp giữa Bán lẻ Dược mỹ phẩm và Quản lý Điều trị Da liễu Y khoa. "
        "Việc áp dụng thành công mô hình Modular Monolith theo triết lý Domain-Driven Design (DDD) đã giúp phân tách rạch ròi 14 Bounded Contexts, "
        "giữ cho mã nguồn luôn trong sáng, dễ mở rộng, đồng thời loại trừ được các chi phí vận hành và rủi ro trễ mạng của kiến trúc phân tán microservices."
    )
    add_p(
        "Phân hệ Trí tuệ Nhân tạo là một điểm sáng nổi bật của đề tài khi tích hợp hài hòa các kỹ thuật tiên tiến nhất: "
        "từ mô hình ngôn ngữ lớn Gemini, cơ chế phân tích ý định NLU 6 tầng, thuật toán truy xuất kết hợp 3 kênh song song 3-Way Hybrid RAG, "
        "hợp nhất thứ hạng RRF, tái xếp hạng Bayes, cho đến mô hình tìm kiếm thị giác CLIP ViT-B-32 và chốt chặn kiểm soát tồn kho thời gian thực. "
        "Tất cả tạo nên một trợ lý tư vấn da liễu có căn cứ khoa học, loại bỏ ảo giác và đồng hành tin cậy cùng khách hàng."
    )
    add_p(
        "Đặc biệt, giải pháp Public hệ thống ra mạng Internet toàn cầu thông qua Cloudflare Tunnel kết hợp cổng Nginx Reverse Proxy "
        "và chính sách cô lập cổng dịch vụ nội bộ (Zero Inbound Open Ports, Port Binding 127.0.0.1, Rate Limiting, Chặn Actuator, Phục hồi Real-IP) "
        "đã chứng minh năng lực thực thi an ninh hạ tầng ở cấp độ doanh nghiệp. "
        "Hệ thống hoàn toàn đáp ứng đầy đủ các tiêu chuẩn khắt khe về tính sẵn sàng, độ tin cậy và hiệu năng thực tế, "
        "xứng đáng là một công trình nghiên cứu và ứng dụng tiêu biểu trong Khóa luận tốt nghiệp Đại học."
    )

    # Luu file vao thu muc dich
    target_dir = r"C:\Users\NGUYEN THANH NAM\OneDrive\Desktop\kk"
    if not os.path.exists(target_dir):
        os.makedirs(target_dir, exist_ok=True)
    out_file = os.path.join(target_dir, "Bao_Cao_Kien_Truc_Cong_Nghe_Va_Quy_Trinh_Trien_Khai_He_Thong.docx")
    out_file_refactored = os.path.join(target_dir, "Bao_Cao_Kien_Truc_Cong_Nghe_Va_Quy_Trinh_Trien_Khai_He_Thong_Refactored.docx")

    try:
        doc.save(out_file)
        print("XUAT FILE WORD THANH CONG: " + out_file)
    except PermissionError:
        print("CANH BAO: Tep cu dang duoc mo boi WPS Office. Dang luu vao tep moi: " + out_file_refactored)
        doc.save(out_file_refactored)
        print("XUAT FILE WORD THANH CONG: " + out_file_refactored)

    # Copy them thu muc anh diagrams sang Desktop de nguoi dung tien su dung
    dest_diagrams_dir = os.path.join(target_dir, "diagrams")
    if os.path.exists(diagrams_dir):
        shutil.copytree(diagrams_dir, dest_diagrams_dir, dirs_exist_ok=True)
        print("COPY ANH SO DO THANH CONG VAO: " + dest_diagrams_dir)

if __name__ == "__main__":
    generate_report()
