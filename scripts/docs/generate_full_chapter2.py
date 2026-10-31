# -*- coding: utf-8 -*-
"""
Script tạo tài liệu Word (DOCX) hoàn chỉnh cho Chương 2: Cơ sở lý thuyết và công nghệ liên quan.
Thiết kế theo chuẩn định dạng Khóa luận tốt nghiệp Đại học:
- Khổ giấy A4, lề: Trái 3.0 cm, Phải 2.0 cm, Trên 2.0 cm, Dưới 2.0 cm.
- Font chữ: Times New Roman, cỡ 13pt (Tiêu đề 14-16pt, Bảng 11-12pt).
- Giãn dòng: 1.3 lines, Spacing before/after chuẩn mực.
"""

import os
import sys
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml, OxmlElement
from docx.oxml.ns import nsdecls, qn

def create_document():
    doc = docx.Document()

    # 1. Cấu hình lề trang chuẩn khóa luận
    for section in doc.sections:
        section.page_width = Inches(8.27)   # A4
        section.page_height = Inches(11.69)
        section.top_margin = Inches(0.79)    # 2.0 cm
        section.bottom_margin = Inches(0.79) # 2.0 cm
        section.left_margin = Inches(1.18)   # 3.0 cm
        section.right_margin = Inches(0.79)  # 2.0 cm

    # 2. Cấu hình Style Normal
    style_normal = doc.styles['Normal']
    style_normal.font.name = 'Times New Roman'
    style_normal.font.size = Pt(13)
    style_normal.font.color.rgb = RGBColor(0, 0, 0)
    style_normal.paragraph_format.line_spacing = 1.3
    style_normal.paragraph_format.space_after = Pt(4)
    style_normal.paragraph_format.space_before = Pt(0)

    def set_cell_border(cell, **kwargs):
        """Thiết lập viền bảng"""
        tc = cell._tc
        tcPr = tc.get_or_add_tcPr()
        tcBorders = parse_xml(f'''
            <w:tcBorders {nsdecls("w")}>
                <w:top w:val="{kwargs.get('top', 'single')}" w:sz="{kwargs.get('top_sz', '4')}" w:space="0" w:color="{kwargs.get('top_color', 'auto')}"/>
                <w:left w:val="{kwargs.get('left', 'single')}" w:sz="{kwargs.get('left_sz', '4')}" w:space="0" w:color="{kwargs.get('left_color', 'auto')}"/>
                <w:bottom w:val="{kwargs.get('bottom', 'single')}" w:sz="{kwargs.get('bottom_sz', '4')}" w:space="0" w:color="{kwargs.get('bottom_color', 'auto')}"/>
                <w:right w:val="{kwargs.get('right', 'single')}" w:sz="{kwargs.get('right_sz', '4')}" w:space="0" w:color="{kwargs.get('right_color', 'auto')}"/>
            </w:tcBorders>
        ''')
        tcPr.append(tcBorders)

    def add_h1(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(14)
        p.paragraph_format.space_after = Pt(8)
        p.paragraph_format.keep_with_next = True
        run = p.add_run(text)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(16)
        run.font.color.rgb = RGBColor(0, 32, 96) # Xanh Navy đậm sang trọng
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
        run.font.size = Pt(14)
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
        return p

    def add_p(text, bold_prefix=None, italic_prefix=None):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        p.paragraph_format.line_spacing = 1.3
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.first_line_indent = Inches(0.4) # Thụt lề đầu dòng 1cm
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

    def add_formula(formula_text, label=""):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(4)
        rf = p.add_run(formula_text)
        rf.font.name = 'Times New Roman'
        rf.font.size = Pt(12.5)
        rf.bold = True
        rf.italic = True
        if label:
            p_tab = p.add_run(f"\t\t\t({label})")
            p_tab.font.name = 'Times New Roman'
            p_tab.italic = False
            p_tab.bold = False
        return p

    # ==================== NỘI DUNG TOÀN VĂN ====================

    # TIÊU ĐỀ CHƯƠNG
    add_h1("CHƯƠNG 2: CƠ SỞ LÝ THUYẾT VÀ CÔNG NGHỆ LIÊN QUAN")

    add_p(
        "Chương này trình bày toàn diện nền tảng lý thuyết khoa học, cơ sở toán học và kiến trúc công nghệ được nghiên cứu, kế thừa và triển khai trong đề tài. Nội dung trọng tâm tập trung vào hai trụ cột công nghệ chính: (1) Hệ thống Trợ lý tư vấn mỹ phẩm thông minh ứng dụng Mô hình ngôn ngữ lớn (LLM), Quản lý hội thoại đa tầng (Dialogue Management) và Kỹ thuật tạo sinh dựa trên truy xuất tăng cường (Advanced RAG); (2) Hệ thống gợi ý cá nhân hóa dựa trên nội dung (Content-Based Recommendation System) ứng dụng mô hình không gian vector TF-IDF có trọng số trường và vector sở thích người dùng theo thời gian. Đồng thời, chương cũng phân tích kỹ thuật tìm kiếm sản phẩm bằng hình ảnh thông qua mô hình thị giác ngôn ngữ CLIP và các chỉ số đo lường định lượng chất lượng hệ thống."
    )

    # 2.1. MÔ HÌNH NGÔN NGỮ LỚN
    add_h2("2.1. Mô hình ngôn ngữ lớn và Quản lý hội thoại đa tầng")

    add_h3("2.1.1. Kiến trúc Transformer và Mô hình ngôn ngữ tự hồi quy")
    add_p(
        "Mô hình ngôn ngữ lớn (Large Language Model - LLM) là bước phát triển vượt bậc của lĩnh vực Xử lý ngôn ngữ tự nhiên (NLP), được xây dựng trên nền tảng kiến trúc Transformer do Vaswani và các cộng sự giới thiệu năm 2017. Trọng tâm của Transformer là cơ chế tự chú ý đa đầu (Multi-Head Self-Attention), cho phép mô hình nắm bắt mối quan hệ phụ thuộc xa giữa các từ trong văn bản mà không bị giới hạn bởi tính tuần tự như các kiến trúc RNN hay LSTM truyền thống."
    )
    add_p(
        "Trong các tác vụ tạo sinh văn bản đàm thoại, mô hình ngôn ngữ tự hồi quy (Autoregressive Language Model) như dòng GPT (Generative Pre-trained Transformer) hay Gemini là phương pháp tiêu chuẩn. Quá trình sinh chuỗi văn bản W = (w_1, w_2, ..., w_T) được mô hình hóa dưới dạng phân phối xác suất có điều kiện tích lũy:"
    )
    add_formula("P(W) = ∏_{t=1}^{T} P(w_t | w_1, w_2, ..., w_{t-1}; θ)", "CT 2.1")
    add_p(
        "Trong đó, θ biểu thị tập tham số học được của mô hình, và xác suất của mỗi token tiếp theo w_t phụ thuộc vào toàn bộ chuỗi ngữ cảnh đã được sinh ra trước đó. Trong phạm vi đề tài, LLM đóng vai trò tổng hợp tri thức, diễn giải ngôn ngữ tự nhiên, giải thích cơ chế thành phần mỹ phẩm và biên soạn câu trả lời tư vấn một cách tự nhiên, chuẩn mực."
    )

    add_h3("2.1.2. Kỹ nghệ nhắc và Bộ phân tích hiểu ý định 6 tầng (Structured NLU)")
    add_p(
        "Khác với các ứng dụng trò chuyện thông thường, tư vấn mỹ phẩm và chăm sóc da đòi hỏi độ chính xác cao về mặt chỉ định lâm sàng. Nếu đưa trực tiếp câu hỏi người dùng vào LLM mà không tiền xử lý cấu trúc, mô hình dễ bỏ sót các ràng buộc nghiêm ngặt về loại da (da khô, da dầu, da nhạy cảm) hoặc hoạt chất kích ứng (cồn, hương liệu, paraben). Đề tài thiết kế Bộ phân tích ngôn ngữ tự nhiên (NLU) 6 tầng thông qua kỹ nghệ nhắc chuyên sâu (Few-shot System Prompting), trích xuất thông tin truy vấn thành cấu trúc JSON chuẩn mực:"
    )
    add_p(
        " Phân loại mục đích cốt lõi của người dùng thành các nhãn chuyên biệt: Tư vấn tìm sản phẩm (PRODUCT_RECOMMENDATION), So sánh hai hay nhiều sản phẩm (COMPARISON), Hỏi chi tiết công dụng/thành phần (PRODUCT_DETAIL), Xây dựng chu trình dưỡng da nhiều bước (ROUTINE), Hỏi chính sách cửa hàng/spa (POLICY), hoặc Chào hỏi xã giao (GREETING).",
        bold_prefix="1. Tầng phân loại ý định (Intent Classification): "
    )
    add_p(
        " Chuẩn hóa tên loại mỹ phẩm người dùng gọi dân dã về cây danh mục quy chuẩn của hệ thống (Ví dụ: 'sữa rửa mặt', 'gel rửa mặt' quy về 'Cleanser'; 'kem chống nắng', 'chống nắng vật lý' quy về 'Sunscreen').",
        bold_prefix="2. Tầng chuẩn hóa danh mục (Category Normalization): "
    )
    add_p(
        " Trích xuất các điều kiện biên bắt buộc gồm khoảng giá tối đa/tối thiểu (min_price, max_price), loại da chỉ định (skin_type: Oily, Dry, Sensitive, Combination, Normal) và quy cách dạng sản phẩm (form_factor: Gel, Cream, Serum, Toner, Lotion).",
        bold_prefix="3. Tầng trích xuất ràng buộc cứng (Hard Constraints): "
    )
    add_p(
        " Nhận diện các vấn đề da liễu người dùng đang gặp phải (concerns: mụn ẩn, mụn viêm, thâm đỏ, lão hóa, lỗ chân lông to) và các hoạt chất mong muốn có mặt (ingredients: Vitamin B5, Niacinamide, Retinol, Hyaluronic Acid, Salicylic Acid).",
        bold_prefix="4. Tầng sở thích & hoạt chất (Preferences & Actives): "
    )
    add_p(
        " Trích xuất danh sách các thành phần người dùng chủ động yêu cầu loại bỏ (Ví dụ: 'da tôi dị ứng cồn và hương liệu' trích xuất negative_preferences: ['alcohol', 'fragrance']). Đây là cơ sở cốt tử để bộ Reranker phạt điểm hoặc loại trừ sản phẩm.",
        bold_prefix="5. Tầng sở thích phủ định (Negative Preferences): "
    )
    add_p(
        " Nhận diện số lượng sản phẩm mong muốn (Ví dụ: 'gợi ý cho tôi 1 chai duy nhất', 'chỉ lấy 2 loại') và đánh cờ yêu cầu làm rõ (needs_clarification = True) kèm câu hỏi gợi ý khi câu truy vấn thiếu dữ liệu tối thiểu.",
        bold_prefix="6. Tầng số lượng và điều kiện làm rõ (Target Quantity & Clarification): "
    )

    add_h3("2.1.3. Theo dõi trạng thái hội thoại và Bộ nhớ thực thể đa lượt (Entity Memory)")
    add_p(
        "Hội thoại tư vấn mua sắm thường mang tính nối tiếp qua nhiều lượt trao đổi (Multi-turn Conversation), trong đó người dùng thường xuyên sử dụng đại từ thay thế hoặc câu hỏi tỉnh lược (Anaphora Resolution - Ví dụ: 'Chai này giá bao nhiêu?', 'Sản phẩm thứ hai có trị mụn không?', 'Có loại nào rẻ hơn không?'). Nếu không có cơ chế lưu vết trạng thái, chatbot sẽ mất ngữ cảnh ngay ở lượt thứ hai."
    )
    add_p(
        "Đề tài xây dựng module Quản lý trạng thái đàm thoại (Conversation Manager) kết hợp Bộ nhớ thực thể (Entity Memory) thực hiện các nhiệm vụ:",
        bold_prefix="Cơ chế vận hành của Entity Memory: "
    )
    add_p(
        " Duy trì danh sách các sản phẩm đang được nhắc tới ở lượt hội thoại gần nhất, gán nhãn thứ tự xuất hiện (index 1, 2, 3) để ánh xạ chính xác cụm từ 'loại thứ nhất', 'sản phẩm thứ hai'.",
        bold_prefix="• Lưu vết thực thể trọng tâm (Active Focus Entity): "
    )
    add_p(
        " Nhận diện các đại từ chỉ định ('nó', 'em này', 'chai này', 'sản phẩm trên') và tự động thay thế bằng ID, tên và thương hiệu của sản phẩm đang trong vùng trọng tâm.",
        bold_prefix="• Giải quyết đại từ chỉ định (Anaphora Resolution): "
    )
    add_p(
        " Tích lũy các ràng buộc về loại da và ngân sách đã xác nhận ở lượt trước vào lượt hiện tại, trừ khi người dùng chủ động tuyên bố thay đổi (Ví dụ: lượt 1 nói 'da dầu', lượt 2 hỏi 'serum nào tốt' thì hệ thống tự động kế thừa điều kiện 'da dầu').",
        bold_prefix="• Tích lũy ràng buộc phiên (Constraint Accumulation): "
    )

    add_h3("2.1.4. Bộ lọc định tuyến nhanh (Rule-based Fast-path Routing)")
    add_p(
        "Nhằm tối ưu hóa tài nguyên tính toán và giảm thiểu tối đa độ trễ phản hồi (latency), hệ thống tích hợp bộ lọc nhanh Fast-path Router hoạt động dựa trên các biểu thức chính quy (Regex) và từ điển mẫu câu. Đối với các lượt trao đổi chào hỏi ('Xin chào', 'Hello', 'Hi bot'), lời cảm ơn ('Cảm ơn shop', 'Thanks'), hoặc xác nhận ngắn, Fast-path Router xử lý và trả về phản hồi chuẩn xác tức thì trong thời gian dưới 5ms mà không cần kích hoạt lời gọi mô hình LLM qua mạng, giúp tiết kiệm đáng kể chi phí token và giải phóng tài nguyên cho các truy vấn phức tạp."
    )

    # 2.2. ADVANCED RAG
    add_h2("2.2. Kỹ thuật Tạo sinh dựa trên Truy xuất Tăng cường (Advanced RAG)")

    add_h3("2.2.1. Hạn chế cốt lõi của LLM và Nguyên lý Kiến trúc Advanced RAG")
    add_p(
        "Dù sở hữu năng lực ngôn ngữ vượt trội, các mô hình ngôn ngữ lớn độc lập đối mặt với hai rào cản mang tính bản chất: (1) Hiện tượng ảo giác (Hallucination) - sinh ra các thông tin sai sự thật nhưng với giọng văn vô cùng tự tin, và (2) Ranh giới tri thức cố định (Knowledge Cutoff) - không thể tự cập nhật giá bán, số lượng tồn kho theo thời gian thực và các chương trình khuyến mãi hiện hành. Nếu ứng dụng trong thương mại điện tử mỹ phẩm, việc mô hình tự bịa giá hoặc tư vấn sai thành phần có thể dẫn đến hậu quả nghiêm trọng về trải nghiệm người dùng và pháp lý."
    )
    add_p(
        "Kỹ thuật Tạo sinh dựa trên Truy xuất Tăng cường (Retrieval-Augmented Generation - RAG), do Lewis và cộng sự công bố năm 2020, giải quyết triệt để vấn đề này bằng cách kết hợp giai đoạn truy xuất dữ liệu thực tế từ cơ sở tri thức bên ngoài làm ngữ cảnh tham chiếu (grounding context) trước khi đưa vào mô hình sinh. Đề tài không dừng lại ở kiến trúc RAG cơ bản (Naive RAG vốn chỉ gồm Vector Search đơn giản) mà triển khai kiến trúc RAG nâng cao (Advanced RAG) đa tầng khép kín: Tiền xử lý & Chunking -> Truy xuất kết hợp 3 kênh (3-Way Hybrid) -> Hợp nhất thứ hạng RRF -> Tái xếp hạng đa tiêu chí (Reranker) -> Kiểm duyệt tồn kho thời gian thực (Live Inventory Verification) -> Sinh phản hồi có kiểm soát."
    )

    add_h3("2.2.2. Phân đoạn tài liệu (Chunking) và Biểu diễn ngữ nghĩa (Text Embeddings)")
    add_p(
        "Dữ liệu sản phẩm mỹ phẩm chứa nhiều trường thông tin đặc thù. Việc phân đoạn tài liệu (Chunking) cần đảm bảo tính toàn vẹn ngữ nghĩa của từng khía cạnh sản phẩm. Hệ thống phân rã thông tin mỗi sản phẩm thành các đoạn độc lập có gắn kèm siêu dữ liệu (Metadata):"
    )
    add_p(
        " Tên, thương hiệu, danh mục, dung tích, giá bán, mô tả tổng quan và chứng nhận nguồn gốc.",
        bold_prefix="• Đoạn thông tin chung (General Details Chunk): "
    )
    add_p(
        " Cơ chế tác động, hiệu quả cải thiện da, đối tượng sử dụng và hướng dẫn sử dụng an toàn.",
        bold_prefix="• Đoạn công dụng & điều trị (Benefits & Routine Chunk): "
    )
    add_p(
        " Danh sách hoạt chất chính, nồng độ %, bảng thành phần chuẩn quốc tế INCI, nồng độ cồn và hương liệu.",
        bold_prefix="• Đoạn thành phần hoạt chất (Ingredients & Actives Chunk): "
    )
    add_p(
        "Mỗi đoạn văn bản d được biến đổi thành một vector nhúng d chiều E(d) ∈ ℝ^{768} thông qua mô hình Embedding tiếng Việt chuyên sâu (Vietnamese Bi-Encoder hoặc Gemini Embedding API). Kho vector được lưu trữ và lập chỉ mục không gian trong cơ sở dữ liệu vector Qdrant, cho phép tìm kiếm láng giềng gần nhất (Approximate Nearest Neighbors - ANN) với tốc độ cao dựa trên độ đo khoảng cách Cosine:"
    )
    add_formula("Sim_{Cosine}(E(q), E(d)) = (E(q) · E(d)) / (||E(q)||_2 · ||E(d)||_2)", "CT 2.2")

    add_h3("2.2.3. Cơ chế truy xuất kết hợp 3 kênh song song (3-Way Hybrid Retrieval)")
    add_p(
        "Trong lĩnh vực mỹ phẩm, việc chỉ dựa vào tìm kiếm vector ngữ nghĩa (Dense Retrieval) thường dẫn đến hiện tượng trôi dạt ngữ cảnh đối với các truy vấn chứa từ khóa kỹ thuật cụ thể (Ví dụ: khách tìm chính xác hoạt chất 'B5' hoặc mã 'DV01', mô hình ngữ nghĩa có thể trả về 'B3' hoặc 'Vitamin C' do chúng có độ tương đồng ngữ cảnh gần nhau). Ngược lại, tìm kiếm từ khóa thuần túy lại thất bại khi người dùng mô tả triệu chứng da tự nhiên ('da bong tróc đỏ rát khi đổi thời tiết'). Do đó, hệ thống thiết kế cơ chế truy xuất 3 kênh đồng thời:"
    )
    add_p(
        " Truy vấn trực tiếp cơ sở dữ liệu quan hệ (SQLite/MySQL) để áp dụng các bộ lọc điều kiện cứng về danh mục chuẩn, khoảng giá ngân sách, loại da chỉ định và dạng kết cấu. Kênh này đảm bảo tính chính xác 100% đối với các ràng buộc số học và danh mục.",
        bold_prefix="1. Kênh 1 - Lọc cấu trúc SQL (Relational Filter Search): "
    )
    add_p(
        " Thực hiện tìm kiếm từ vựng và cụm bi-gram trên toàn bộ kho chunk sản phẩm dựa trên mô hình xác suất BM25Okapi, có kết hợp từ điển mở rộng từ đồng nghĩa y khoa mỹ phẩm. Công thức tính điểm BM25 cho tài liệu D đối với truy vấn Q = (q_1, q_2, ..., q_n):",
        bold_prefix="2. Kênh 2 - Truy xuất từ vựng BM25 (Lexical Search): "
    )
    add_formula(
        "Score_{BM25}(D, Q) = ∑_{i=1}^{n} IDF(q_i) · [f(q_i, D) · (k_1 + 1)] / [f(q_i, D) + k_1 · (1 - b + b · (|D| / avgdl))]",
        "CT 2.3"
    )
    add_p(
        "Trong đó f(q_i, D) là tần số xuất hiện của từ q_i trong D; |D| và avgdl lần lượt là độ dài của tài liệu D và độ dài trung bình toàn bộ tập văn bản; k_1 và b là các tham số điều chỉnh độ bão hòa tần số từ và chuẩn hóa độ dài văn bản (thiết lập chuẩn: k_1 = 1.5, b = 0.75)."
    )
    add_p(
        " Trích xuất vector truy vấn và tìm kiếm ngữ nghĩa trên không gian 768 chiều của Qdrant, giúp bao quát các nhu cầu mô tả triệu chứng, cảm nhận và ngữ nghĩa phức tạp.",
        bold_prefix="3. Kênh 3 - Truy xuất ngữ nghĩa Dense Vector (Vector Search): "
    )

    add_h3("2.2.4. Thuật toán Hợp nhất thứ hạng RRF (Reciprocal Rank Fusion)")
    add_p(
        "Do 3 kênh tìm kiếm trên sinh ra các thang điểm hoàn toàn khác biệt (SQL trả về nhị phân 0/1, BM25 có miền giá trị không chặn trên [0, +∞), Vector Search có thang điểm Cosine [-1, 1]), việc chuẩn hóa điểm số tuyến tính thường gây sai lệch lớn. Hệ thống áp dụng thuật toán Reciprocal Rank Fusion (RRF) - giải thuật hợp nhất thứ hạng chuẩn quốc tế không phụ thuộc vào biên độ điểm gốc:"
    )
    add_formula("RRF_Score(d) = ∑_{m ∈ M} 1 / (k + r_m(d))", "CT 2.4")
    add_p(
        "Trong đó: M = {SQL, BM25, Vector} là tập các phương pháp truy xuất; r_m(d) là thứ hạng xếp hạng của tài liệu d trong kênh m (nếu d không xuất hiện trong kênh m thì r_m(d) = ∞); k là hằng số làm mịn được thiết lập bằng 60 theo các nghiên cứu thực nghiệm tối ưu của Cormack (2009). Điểm RRF phản ánh sự đồng thuận của cả 3 kênh: tài liệu nào được xếp hạng cao ở nhiều kênh độc lập sẽ đạt điểm tổng hợp vượt trội."
    )

    add_h3("2.2.5. Bộ tái xếp hạng chuyên sâu kết hợp suy diễn Bayes (Multi-criteria Reranker)")
    add_p(
        "Danh sách ứng viên tiềm năng sau khi hợp nhất RRF được đưa vào Bộ tái xếp hạng chuyên sâu (Product Reranker). Đây là mắt xích quyết định tính phù hợp thực tế của sản phẩm trước khi đưa vào ngữ cảnh sinh, áp dụng đồng thời 4 bộ quy tắc trọng số:"
    )
    add_p(
        " Thay vì sử dụng điểm trung bình số học đơn giản (dễ bị thiên lệch khi sản phẩm chỉ có 1 đánh giá 5 sao), hệ thống sử dụng công thức Trung bình Bayes (Bayesian Average Rating) để làm trơn điểm đánh giá:",
        bold_prefix="1. Điểm tín nhiệm xã hội (Social Proof Score): "
    )
    add_formula("Rating_{Bayes} = (v · R + m · C) / (v + m)", "CT 2.5")
    add_p(
        "Trong đó: v là số lượt đánh giá thực tế của sản phẩm; R là điểm sao trung bình; m là ngưỡng số lượt đánh giá tối thiểu của hệ thống (m = 5.0); C là điểm sao bình quân toàn sàn (C = 4.5). Điểm xã hội tổng hợp kết hợp thêm hàm logarit tăng trưởng số lượng đã bán: Social_Score = (Rating_{Bayes} - 3.0) · 0.8 + 0.6 · log_{10}(v + 1) + 0.4 · log_{10}(sold + 1)."
    )
    add_p(
        " So khớp đối chiếu loại da người dùng với phân loại sản phẩm: Khớp chính xác (+ điểm tối đa), dòng sản phẩm trung tính cho mọi loại da (+ điểm vừa phải), hoặc lệch chỉ định da liễu (- phạt điểm nặng để đẩy lùi về cuối danh sách).",
        bold_prefix="2. Điểm khớp loại da (Skin Type Match): "
    )
    add_p(
        " Tăng điểm cộng thưởng tuyến tính theo số lượng hoạt chất mong muốn mà người dùng đã đề cập xuất hiện trong bảng thành phần.",
        bold_prefix="3. Điểm khớp hoạt chất (Ingredient Match): "
    )
    add_p(
        " Rà soát nghiêm ngặt các hoạt chất người dùng kiêng kỵ (cồn khô, paraben, hương liệu tổng hợp, silicones). Nếu sản phẩm chứa thành phần cấm, hệ thống trừ điểm triệt để hoặc loại bỏ trực tiếp khỏi danh sách phản hồi.",
        bold_prefix="4. Xử lý thành phần kiêng kỵ (Negative Preference Penalty): "
    )

    add_h3("2.2.6. Chốt chặn kiểm soát tồn kho thời gian thực (Live Inventory Verification)")
    add_p(
        "Một trong những nguyên nhân khiến chatbot thương mại điện tử bị đánh giá thấp là việc tư vấn nhiệt tình cho khách một sản phẩm... đã hết hàng trong kho. Để loại bỏ hoàn toàn rủi ro này, đề tài xây dựng chốt chặn Live Inventory Verification Guardrail chạy đồng bộ ngay trước bước sinh phản hồi: Hệ thống thực hiện truy vấn tức thời bảng kiểm kê kho vận của Backend, kiểm tra số lượng tồn kho khả dụng (available_quantity > 0) và hạn sử dụng của từng lô hàng (FEFO). Những sản phẩm hết hàng hoặc cận hạn ngừng bán sẽ bị loại bỏ khỏi danh sách tư vấn, bảo đảm 100% sản phẩm chatbot giới thiệu đều sẵn sàng để khách hàng đặt mua."
    )

    # 2.3. HỆ THỐNG GỢI Ý DỰA TRÊN NỘI DUNG
    add_h2("2.3. Hệ thống Gợi ý dựa trên Nội dung (Content-Based Recommendation System)")

    add_h3("2.3.1. Nguyên lý tổng quát Lọc dựa trên nội dung")
    add_p(
        "Hệ thống gợi ý dựa trên nội dung (Content-Based Filtering) là phương pháp khuyến nghị các sản phẩm có đặc trưng tương đồng với những sản phẩm mà người dùng cụ thể đã từng quan tâm, đánh giá cao hoặc thực hiện hành vi mua sắm trong quá khứ. Trong ngành bán lẻ mỹ phẩm và dịch vụ làm đẹp, Content-Based Filtering sở hữu những ưu thế vượt trội so với phương pháp Lọc cộng tác (Collaborative Filtering):"
    )
    add_p(
        " Sản phẩm mới nhập kho chỉ cần có đầy đủ thuộc tính, thành phần và mô tả là có thể được đưa vào danh sách gợi ý ngay lập tức mà không cần chờ đợi phát sinh tương tác từ cộng đồng người dùng.",
        bold_prefix="• Giải quyết triệt để bài toán Khởi đầu lạnh cho sản phẩm mới (New-Item Cold-Start): "
    )
    add_p(
        " Không phụ thuộc vào mật độ tương tác chéo giữa các người dùng (vốn rất thưa thớt ở các hệ thống mới triển khai - Sparsity Problem).",
        bold_prefix="• Độc lập với ma trận người dùng: "
    )
    add_p(
        " Cho phép giải thích tường minh lý do gợi ý cho người dùng ('Gợi ý vì sản phẩm này chứa Vitamin B5 và phù hợp da dầu tương tự sản phẩm bạn đã xem').",
        bold_prefix="• Tính minh bạch và khả năng giải thích (Explainability): "
    )

    add_h3("2.3.2. Không gian đặc trưng sản phẩm và Biểu diễn TF-IDF có trọng số trường")
    add_p(
        "Mỗi sản phẩm mỹ phẩm d trong danh mục được mô tả thông qua 8 trường thông tin thuộc tính cốt lõi: Tên sản phẩm (name), Danh mục (category), Thương hiệu (brand), Mô tả chi tiết (description), Bảng thành phần quốc tế (ingredients_inci), Hoạt chất chuẩn hóa (ingredients_mapped), Phân loại da chỉ định (skin_types) và Nhu cầu da liễu (skin_concerns). Nhằm lượng hóa giá trị ngữ nghĩa nhưng tránh việc từ ngữ xuất hiện quá nhiều lần lấn át đặc trưng, hệ thống áp dụng công thức Tần suất từ dưới tuyến tính (Sub-linear TF) và Nghịch đảo tần suất tài liệu làm trơn (Smoothed IDF):"
    )
    add_formula("TF_{sub}(t, d) = 1 + ln(f(t, d))  (với f(t, d) > 0, ngược lại bằng 0)", "CT 2.6")
    add_formula("IDF_{smooth}(t, D) = ln((1 + |D|) / (1 + df(t))) + 1", "CT 2.7")
    add_p(
        "Trong đó: f(t, d) là số lần từ t xuất hiện trong văn bản trường của sản phẩm d; |D| là tổng số sản phẩm hợp lệ trong danh mục; df(t) là số lượng sản phẩm chứa từ t. Trọng số TF-IDF cơ sở là tích: TF-IDF(t, d, D) = TF_{sub}(t, d) · IDF_{smooth}(t, D)."
    )
    add_p(
        "Để phản ánh chính xác tầm quan trọng khác nhau giữa các trường thông tin trong ngành mỹ phẩm (ví dụ: Thành phần hoạt chất và Loại da quan trọng hơn nhiều so với từ ngữ mô tả quảng cáo), hệ thống áp dụng trọng số trường w_f (Field Weighting). Vector biểu diễn của từng trường V_f(d) được chuẩn hóa L2, nhân với căn bậc hai của trọng số trường √(w_f), sau đó nối lại thành vector tổng hợp và thực hiện chuẩn hóa L2 cuối cùng:"
    )
    add_formula("V(d) = L2_Normalize( ⨁_{f ∈ Fields} √(w_f) · L2_Normalize(V_f(d)) )", "CT 2.8")
    add_p(
        "Trong đó: ⨁ là phép toán nối vector; Fields bao gồm 8 trường dữ liệu đã nêu; L2_Normalize(X) = X / ||X||_2. Cấu trúc này đảm bảo vector đại diện của sản phẩm V(d) luôn là một vector đơn vị nằm trên mặt cầu siêu không gian đa chiều, giúp các phép tính tương đồng không bị ảnh hưởng bởi độ dài văn bản."
    )

    add_h3("2.3.3. Mô hình hóa Hồ sơ người dùng theo trọng số hành vi và phân rã thời gian")
    add_p(
        "Hồ sơ sở thích của người dùng u (User Profile) được tổng hợp từ lịch sử các hành vi tương tác trên sàn thương mại điện tử. Tuy nhiên, các hành vi khác nhau thể hiện mức độ cam kết và ý định quan tâm hoàn toàn khác nhau. Một lượt mua hàng hoặc thêm vào giỏ phản ánh sở thích mạnh mẽ hơn nhiều so với một lượt xem lướt qua. Đồng thời, sở thích của người dùng có tính suy giảm theo thời gian (sở thích xem hôm nay quan trọng hơn sở thích từ một tháng trước)."
    )
    add_p(
        "Đề tài mô hình hóa Hồ sơ người dùng dưới dạng Vector Trung tâm có trọng số hành vi và suy giảm thời gian (Action-Weighted Decayed User Centroid Vector) tính toán trên cửa sổ N tương tác gần nhất:"
    )
    add_formula(
        "P_u = L2_Normalize( ∑_{j=1}^{N} α(a_j) · exp(-ln(2) · Δt_j / T_{half}) · min(1.0, W_{cap} / w_{cum}(p_j)) · V(p_j) )",
        "CT 2.9"
    )
    add_p(
        "Các tham số thành phần trong công thức được định nghĩa cụ thể:",
        bold_prefix="Giải thích các tham số trong mô hình hồ sơ người dùng: "
    )
    add_p(
        " Hệ số phản ánh cường độ quan tâm của loại hành vi tương tác thứ j. Được quy chuẩn theo thang bậc: VIEW = 1.0 (Xem chi tiết); SEARCH_CLICK = 1.5 (Nhấp từ kết quả tìm kiếm); ADD_TO_CART = 3.0 (Thêm vào giỏ hàng); PURCHASE = 5.0 (Đặt hàng thành công).",
        bold_prefix="• Trọng số hành vi α(a_j): "
    )
    add_p(
        " Trong đó Δt_j là khoảng thời gian tính từ lúc tương tác j diễn ra đến hiện tại; T_{half} là chu kỳ bán rã sở thích (thiết lập mặc định 14 ngày). Tương tác càng cũ thì ảnh hưởng của nó tới hồ sơ hiện tại càng suy giảm theo hàm mũ.",
        bold_prefix="• Hàm suy giảm thời gian exp(-ln(2) · Δt_j / T_{half}): "
    )
    add_p(
        " Tránh hiện tượng một sản phẩm người dùng nhấp xem liên tục nhiều lần trong một phiên làm bão hòa và lệch lạc toàn bộ hồ sơ sở thích.",
        bold_prefix="• Ngưỡng chặn trọng số trần trên một sản phẩm W_{cap}: "
    )

    add_h3("2.3.4. Xếp hạng tương đồng Cosine và Chiến lược xử lý khởi đầu lạnh")
    add_p(
        "Do cả vector hồ sơ người dùng P_u và vector sản phẩm V(p) đều đã được chuẩn hóa L2 thành vector đơn vị, độ tương đồng Cosine (Cosine Similarity) giữa người dùng u và sản phẩm ứng viên p được tính toán một cách tối ưu bằng tích vô hướng (Dot Product):"
    )
    add_formula("Score(u, p) = Cosine_Sim(P_u, V(p)) = P_u · V(p) = ∑_{k} P_{u, k} · V_{k}(p)", "CT 2.10")
    add_p(
        "Hệ thống sắp xếp toàn bộ kho sản phẩm theo điểm Score(u, p) giảm dần, loại bỏ các sản phẩm người dùng đã mua hoặc đã tương tác gần đây (theo cấu hình loại trừ), và trích xuất Top-K sản phẩm có điểm tương đồng cao nhất trả về cho giao diện người dùng."
    )
    add_p(
        "Đối với người dùng mới hoàn toàn chưa đăng nhập hoặc chưa phát sinh bất kỳ hành vi tương tác nào trong lịch sử (User Cold-Start), vector P_u không thể xác định. Để đảm bảo trải nghiệm người dùng liền mạch, hệ thống tự động kích hoạt Chiến lược dự phòng phổ biến (POPULAR_FALLBACK): truy xuất danh sách các sản phẩm đang có số lượng bán chạy nhất toàn sàn kết hợp đánh giá sao cao và còn hàng khả dụng. Phản hồi API phân định rõ ràng cờ trạng thái (CONTENT_BASED, COLD_START, hoặc MIXED) để đảm bảo tính minh bạch dữ liệu."
    )

    # 2.4. CLIP IMAGE SEARCH
    add_h2("2.4. Hệ thống Tìm kiếm Sản phẩm bằng Hình ảnh (Visual Product Search)")
    add_p(
        "Trong thương mại điện tử mỹ phẩm, khách hàng thường xuyên chụp lại ảnh bao bì sản phẩm từ mạng xã hội hoặc ảnh chụp thực tế nhưng không nhớ rõ tên gọi chính xác. Để hỗ trợ trải nghiệm đa phương thức, hệ thống tích hợp phân hệ Tìm kiếm bằng hình ảnh ứng dụng mô hình Contrastive Language-Image Pre-training (CLIP) do OpenAI giới thiệu."
    )
    add_p(
        "Mô hình CLIP (phiên bản ViT-B/32) bao gồm hai nhánh mạng nơ-ron được huấn luyện đồng thời: một nhánh Vision Transformer trích xuất đặc trưng thị giác từ hình ảnh và một nhánh Text Transformer trích xuất đặc trưng từ văn bản, cùng ánh xạ vào một không gian nhúng chung 512 chiều. Nhờ đó, vector biểu diễn của ảnh sản phẩm và vector mô tả sản phẩm có sự tương thích cao về mặt ngữ nghĩa hình học."
    )
    add_p(
        "Quy trình tìm kiếm hình ảnh trong hệ thống:",
        bold_prefix="Quy trình xử lý ảnh: "
    )
    add_p(
        " Hệ thống kiểm tra magic bytes của tệp ảnh tải lên (JPEG, PNG, WebP), giới hạn dung lượng tối đa 5MB và số lượng pixel tối đa 20.000.000 pixels để ngăn chặn tấn công Decompression Bomb.",
        bold_prefix="1. Tiền xử lý & Kiểm soát an toàn: "
    )
    add_p(
        " Ảnh được chuẩn hóa kích thước, đưa qua nhánh Vision Encoder của CLIP để trích xuất vector đặc trưng thị giác v_{img} ∈ ℝ^{512} và chuẩn hóa L2.",
        bold_prefix="2. Trích xuất đặc trưng thị giác: "
    )
    add_p(
        " Vector v_{img} được so khớp Cosine Similarity với kho vector ảnh sản phẩm đã được trích xuất sẵn (Pre-computed Image Snapshots lưu trữ trong file index.npz).",
        bold_prefix="3. So khớp không gian vector: "
    )
    add_p(
        " Danh sách các ứng viên có độ tương đồng hình ảnh cao nhất được chuyển tiếp sang Backend Java để kiểm tra tính khả dụng của tồn kho thực tế trước khi hiển thị cho người dùng.",
        bold_prefix="4. Lọc tồn kho & Trả kết quả: "
    )

    # 2.5. ĐỘ ĐO ĐÁNH GIÁ
    add_h2("2.5. Các phương pháp và Độ đo Đánh giá Định lượng Hệ thống")

    add_h3("2.5.1. Độ đo đánh giá hệ thống gợi ý và truy xuất")
    add_p(
        "Để đánh giá khách quan năng lực của hệ thống gợi ý Content-Based và bộ máy truy xuất RAG, các chỉ số đo lường chuẩn mực của lĩnh vực Truy hồi thông tin (Information Retrieval) được áp dụng trên tập kiểm thử phân chia theo dòng thời gian (Holdout Validation):"
    )
    add_p(
        " Tỷ lệ các lượt kiểm thử mà sản phẩm thực tế người dùng tương tác xuất hiện trong danh sách Top-K gợi ý:",
        bold_prefix="• Tỷ lệ trúng đích (Hit Rate - Hit@K): "
    )
    add_formula("Hit@K = (1 / |U|) · ∑_{u ∈ U} 𝕀(rank(u) ≤ K)", "CT 2.11")
    add_p(
        " Nghịch đảo thứ hạng của sản phẩm liên quan xuất hiện đầu tiên trong danh sách đề xuất:",
        bold_prefix="• Nghịch đảo thứ hạng trung bình (Mean Reciprocal Rank - MRR): "
    )
    add_formula("MRR = (1 / |Q|) · ∑_{i=1}^{|Q|} (1 / rank_i)", "CT 2.12")
    add_p(
        " Độ đo đánh giá chất lượng xếp hạng có tính đến vị trí ưu tiên: sản phẩm phù hợp xuất hiện ở vị trí càng cao thì điểm số đóng góp càng lớn, suy giảm theo logarit của vị trí xếp hạng:",
        bold_prefix="• Điểm trừ tích lũy chuẩn hóa (NDCG@K): "
    )
    add_formula("DCG@K = ∑_{i=1}^{K} (2^{rel_i} - 1) / log_2(i + 1);  NDCG@K = DCG@K / IDCG@K", "CT 2.13")

    add_h3("2.5.2. Độ đo đánh giá chất lượng hội thoại RAG (RAG Triad)")
    add_p(
        "Chất lượng sinh phản hồi của Trợ lý ảo AI được lượng hóa thông qua bộ ba tiêu chuẩn RAG Triad, bảo đảm kiểm soát toàn diện từ khâu truy xuất tài liệu đến khâu tạo sinh câu trả lời:"
    )
    add_p(
        " Lượng hóa tỷ lệ các tuyên bố, khẳng định trong câu trả lời sinh ra có bằng chứng xác thực trực tiếp từ các đoạn văn bản ngữ cảnh được truy xuất. Đây là chỉ số quan trọng nhất nhằm chứng minh hệ thống đã triệt tiêu hiện tượng ảo giác (Hallucination).",
        bold_prefix="1. Độ trung thực của phản hồi (Faithfulness): "
    )
    add_p(
        " Đo lường mức độ câu trả lời giải quyết trực tiếp, đầy đủ và trúng trọng tâm câu hỏi của người dùng, không đưa ra các thông tin lan man thừa thãi.",
        bold_prefix="2. Độ phù hợp của câu trả lời (Answer Relevance): "
    )
    add_p(
        " Đo lường tỷ lệ các đoạn văn bản được bộ Hybrid RAG truy xuất thực sự chứa thông tin cần thiết phục vụ cho việc trả lời câu hỏi.",
        bold_prefix="3. Độ bao phủ ngữ cảnh (Context Recall): "
    )

    # BẢNG TỔNG KẾT CÔNG NGHỆ
    add_h3("Bảng 2.1. Bảng đối chiếu tổng hợp kiến trúc công nghệ và mô hình toán học trong hệ thống")
    table = doc.add_table(rows=1, cols=4)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False

    # Header
    col_widths = [Inches(1.5), Inches(2.0), Inches(1.8), Inches(2.2)]
    hdr_titles = ["Phân hệ chức năng", "Mô hình / Thuật toán", "Công nghệ / Thư viện", "Mục tiêu kỹ thuật cốt lõi"]
    hdr_cells = table.rows[0].cells
    for i, title in enumerate(hdr_titles):
        hdr_cells[i].width = col_widths[i]
        hdr_cells[i].paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = hdr_cells[i].paragraphs[0].add_run(title)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(11)
        shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="002060"/>') # Xanh Navy
        hdr_cells[i]._tc.get_or_add_tcPr().append(shd)
        run.font.color.rgb = RGBColor(255, 255, 255)
        set_cell_border(hdr_cells[i], top="single", bottom="single", left="single", right="single", top_color="002060", bottom_color="002060")

    table_data = [
        ("Hiểu ý định (NLU)", "Few-shot Prompting, 6-Layer Extraction", "Gemini 3.5 Flash-lite / 2.5 Flash", "Trích xuất ý định, loại da, khoảng giá và thành phần kiêng kỵ chuẩn JSON"),
        ("Quản lý hội thoại", "Rule-based Fast-path, Entity Memory", "Python Custom Orchestrator", "Phản hồi câu chào xã giao <5ms; lưu vết thực thể và giải quyết đại từ chỉ định"),
        ("Truy xuất RAG", "3-Way Hybrid (SQL + BM25Okapi + Dense Vector)", "SQLite, rank-bm25, Qdrant (768d)", "Dung hòa giữa khớp từ khóa y khoa chính xác và hiểu ngữ nghĩa mở rộng"),
        ("Hợp nhất thứ hạng", "Reciprocal Rank Fusion (RRF, k=60)", "Custom RRF Algorithm", "Chuẩn hóa và cộng dồn thứ hạng đa kênh mà không phụ thuộc biên độ điểm số"),
        ("Tái xếp hạng", "Bayesian Reranker, Skincare Matching", "Custom Mathematical Module", "Làm trơn điểm sao bằng Bayes; cộng điểm hoạt chất; loại trừ thành phần cấm"),
        ("Kiểm soát tồn kho", "Live Inventory Verification Guardrail", "Direct Backend DB Bridge", "Chặn đứng việc giới thiệu sản phẩm hết hàng hoặc cận hạn ngừng bán"),
        ("Gợi ý Content-Based", "Sub-linear TF-IDF có trọng số 8 trường", "Custom Vector Space Engine (NumPy)", "Biểu diễn đặc trưng mỹ phẩm có kiểm soát; giải quyết bài toán khởi đầu lạnh"),
        ("Mô hình người dùng", "Action-Weighted Decayed User Centroid", "Centroid Formula with Exp Decay", "Tích lũy hồ sơ sở thích theo cường độ hành vi (Mua > Giỏ > Click > Xem) và thời gian"),
        ("Tìm kiếm hình ảnh", "Vision-Language Embedding (CLIP)", "clip-ViT-B-32, Pillow", "Trích xuất đặc trưng thị giác 512 chiều; so khớp ảnh bao bì sản phẩm thực tế")
    ]

    for row_idx, row_values in enumerate(table_data):
        row = table.add_row()
        bg_color = "F2F5F9" if row_idx % 2 == 1 else "FFFFFF"
        for i, val in enumerate(row_values):
            cell = row.cells[i]
            cell.width = col_widths[i]
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{bg_color}"/>')
            cell._tc.get_or_add_tcPr().append(shd)
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if i in (0, 2) else WD_ALIGN_PARAGRAPH.LEFT
            r = p.add_run(val)
            r.font.name = 'Times New Roman'
            r.font.size = Pt(10.5)
            set_cell_border(cell, top="single", bottom="single", left="single", right="single", top_color="D3D3D3", bottom_color="D3D3D3")

    # TÓM TẮT CHƯƠNG
    add_h2("2.6. Tóm tắt chương 2")
    add_p(
        "Chương 2 đã xây dựng một nền tảng cơ sở lý thuyết vững chắc và có hệ thống phục vụ cho việc thiết kế và cài đặt hệ thống. Toàn bộ các mô hình toán học và giải thuật - từ cơ chế NLU 6 tầng, kỹ thuật Advanced RAG kết hợp 3 kênh tìm kiếm, thuật toán hợp nhất RRF, bộ tái xếp hạng Bayes, cho đến mô hình gợi ý Content-Based TF-IDF trọng số trường và vector người dùng suy giảm theo thời gian - đều được phân tích chi tiết, có công thức toán tường minh và chỉ rõ vai trò trong nghiệp vụ mỹ phẩm. Đây là kim chỉ nam khoa học trực tiếp định hướng cho công tác Thiết kế kiến trúc (Chương 3) và Triển khai thực nghiệm (Chương 4) của khóa luận."
    )

    out_path = r"C:\Users\NGUYEN THANH NAM\Desktop\Chuong_2_Co_So_Ly_Thuyet_Chatbot_RecSys.docx"
    doc.save(out_path)
    print("SUCCESS: File da duoc tao thanh cong tai:", out_path)

if __name__ == "__main__":
    create_document()
