import asyncio
import json
import time
from typing import Any, Dict, List
import httpx

API_URL = "http://localhost:8000/chat"

# 100 Multi-turn Dialogue Benchmark Scenarios
BENCHMARK_SCENARIOS: List[Dict[str, Any]] = [
    # --- NHÓM 1: VẤN ĐỀ DA -> LÀM RÕ DANH MỤC -> TÌM KIẾM THÀNH CÔNG (25 Ca) ---
    {
        "id": "MT_001",
        "group": "Clarification_to_Category",
        "turns": [
            "Da em dạo này đổ nhiều dầu, xuất hiện nhiều mụn ẩn ở vùng cằm và trán",
            "toner"
        ],
        "expected_category": "toner",
        "expected_skin": "dầu",
        "expected_concern": "mụn"
    },
    {
        "id": "MT_002",
        "group": "Clarification_to_Category",
        "turns": [
            "Mặt mình bị khô căng tróc vảy hai bên má mùa đông này",
            "kem dưỡng"
        ],
        "expected_category": "cream",
        "expected_skin": "khô",
        "expected_concern": "ẩm"
    },
    {
        "id": "MT_003",
        "group": "Clarification_to_Category",
        "turns": [
            "Da mình siêu nhạy cảm, dễ bị mẩn đỏ khi đi nắng",
            "kem chống nắng"
        ],
        "expected_category": "sunscreen",
        "expected_skin": "nhạy cảm",
        "expected_concern": "chống nắng"
    },
    {
        "id": "MT_004",
        "group": "Clarification_to_Category",
        "turns": [
            "Mặt mình có nhiều vết thâm mụn đỏ sau khi nặn mụn",
            "serum"
        ],
        "expected_category": "serum",
        "expected_skin": None,
        "expected_concern": "thâm"
    },
    {
        "id": "MT_005",
        "group": "Clarification_to_Category",
        "turns": [
            "Da mình dầu nhiều, lỗ chân lông to bị bít tắc bã nhờn",
            "sữa rửa mặt"
        ],
        "expected_category": "cleanser",
        "expected_skin": "dầu",
        "expected_concern": "lỗ chân lông"
    },
    {
        "id": "MT_006",
        "group": "Clarification_to_Category",
        "turns": [
            "Da hỗn hợp thiên dầu bị mụn viêm sưng to",
            "serum trị mụn"
        ],
        "expected_category": "serum",
        "expected_skin": "hỗn hợp",
        "expected_concern": "mụn"
    },
    {
        "id": "MT_007",
        "group": "Clarification_to_Category",
        "turns": [
            "Mình bị tàn nhang và sạm nám do đi biển về",
            "serum làm sáng da"
        ],
        "expected_category": "serum",
        "expected_skin": None,
        "expected_concern": "nám"
    },
    {
        "id": "MT_008",
        "group": "Clarification_to_Category",
        "turns": [
            "Mặt mình đang bị đỏ rát sau khi peel da ở nhà",
            "kem phục hồi"
        ],
        "expected_category": "cream",
        "expected_skin": None,
        "expected_concern": "phục hồi"
    },
    {
        "id": "MT_009",
        "group": "Clarification_to_Category",
        "turns": [
            "Da dầu nhờn nhiều mụn đầu đen ở mũi",
            "tẩy tế bào chết"
        ],
        "expected_category": "Tẩy tế bào chết",
        "expected_skin": "dầu",
        "expected_concern": "mụn"
    },
    {
        "id": "MT_010",
        "group": "Clarification_to_Category",
        "turns": [
            "Mình da khô muốn cấp nước tức thì lúc ngồi điều hòa",
            "xịt khoáng"
        ],
        "expected_category": "Xịt khoáng",
        "expected_skin": "khô",
        "expected_concern": "cấp nước"
    },
    {
        "id": "MT_011",
        "group": "Clarification_to_Category",
        "turns": [
            "Da xuất hiện nếp nhăn li ti khóe mắt và rãnh cười",
            "kem chống lão hóa"
        ],
        "expected_category": "cream",
        "expected_skin": None,
        "expected_concern": "lão hóa"
    },
    {
        "id": "MT_012",
        "group": "Clarification_to_Category",
        "turns": [
            "Da dầu mụn cần làm sạch sâu cuối ngày",
            "nước tẩy trang"
        ],
        "expected_category": "cleanser",
        "expected_skin": "dầu",
        "expected_concern": "làm sạch"
    },
    {
        "id": "MT_013",
        "group": "Clarification_to_Category",
        "turns": [
            "Mặt mình hay bị nổi mẩn ngứa mỗi khi thời tiết hanh khô",
            "kem dưỡng ẩm làm dịu"
        ],
        "expected_category": "cream",
        "expected_skin": "nhạy cảm",
        "expected_concern": "dịu"
    },
    {
        "id": "MT_014",
        "group": "Clarification_to_Category",
        "turns": [
            "Da khô ráp thiếu sức sống cần đắp thư giãn tối",
            "mặt nạ"
        ],
        "expected_category": "Mặt nạ",
        "expected_skin": "khô",
        "expected_concern": "cấp ẩm"
    },
    {
        "id": "MT_015",
        "group": "Clarification_to_Category",
        "turns": [
            "Môi mình bị khô nứt nẻ chảy máu vào mùa đông",
            "son dưỡng"
        ],
        "expected_category": "Dưỡng môi",
        "expected_skin": None,
        "expected_concern": "khô môi"
    },
    {
        "id": "MT_016",
        "group": "Clarification_to_Category",
        "turns": [
            "Da bóng nhẫy như chảo mỡ sau khi ngủ dậy",
            "toner kiềm dầu"
        ],
        "expected_category": "toner",
        "expected_skin": "dầu",
        "expected_concern": "kiềm dầu"
    },
    {
        "id": "MT_017",
        "group": "Clarification_to_Category",
        "turns": [
            "Da treatment đang dùng retinol bị bong tróc đỏ rát",
            "kem b5"
        ],
        "expected_category": "cream",
        "expected_skin": None,
        "expected_concern": "phục hồi"
    },
    {
        "id": "MT_018",
        "group": "Clarification_to_Category",
        "turns": [
            "Da bị xỉn màu sạm đen do thức khuya nhiều",
            "serum vitamin c"
        ],
        "expected_category": "serum",
        "expected_skin": None,
        "expected_concern": "sáng da"
    },
    {
        "id": "MT_019",
        "group": "Clarification_to_Category",
        "turns": [
            "Da hỗn hợp thiên khô vùng chữ U bị mốc meo khi trang điểm",
            "kem lót dưỡng ẩm"
        ],
        "expected_category": "cream",
        "expected_skin": "hỗn hợp",
        "expected_concern": "ẩm"
    },
    {
        "id": "MT_020",
        "group": "Clarification_to_Category",
        "turns": [
            "Da có nhiều sợi bã nhờn li ti ở cánh mũi",
            "dung dịch bha"
        ],
        "expected_category": "Tẩy tế bào chết",
        "expected_skin": None,
        "expected_concern": "bã nhờn"
    },
    {
        "id": "MT_021",
        "group": "Clarification_to_Category",
        "turns": [
            "Da dễ nổi mụn kích ứng với cồn và hương liệu",
            "sữa rửa mặt tạo bọt dịu nhẹ"
        ],
        "expected_category": "cleanser",
        "expected_skin": "nhạy cảm",
        "expected_concern": "mụn"
    },
    {
        "id": "MT_022",
        "group": "Clarification_to_Category",
        "turns": [
            "Da dầu cần chống nắng đi bơi ngoài trời không trôi",
            "kem chống nắng kháng nước"
        ],
        "expected_category": "sunscreen",
        "expected_skin": "dầu",
        "expected_concern": "chống nắng"
    },
    {
        "id": "MT_023",
        "group": "Clarification_to_Category",
        "turns": [
            "Da bị mỏng lộ chỉ máu do xài kem trộn ngày xưa",
            "serum rau má centella"
        ],
        "expected_category": "serum",
        "expected_skin": "nhạy cảm",
        "expected_concern": "phục hồi"
    },
    {
        "id": "MT_024",
        "group": "Clarification_to_Category",
        "turns": [
            "Mặt bị mụn bọc sưng đỏ viêm nhức",
            "chấm mụn"
        ],
        "expected_category": "cream",
        "expected_skin": None,
        "expected_concern": "mụn"
    },
    {
        "id": "MT_025",
        "group": "Clarification_to_Category",
        "turns": [
            "Da khô ráp thiếu ẩm muốn cấp nước căng bóng kiểu hàn quốc",
            "serum ha hyaluronic acid"
        ],
        "expected_category": "serum",
        "expected_skin": "khô",
        "expected_concern": "cấp nước"
    },

    # --- NHÓM 2: LỌC GIÁ & BỔ SUNG NGÂN SÁCH (15 Ca) ---
    {
        "id": "MT_026",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Tìm kem chống nắng cho da dầu mụn",
            "dưới 300k nhé shop"
        ],
        "expected_category": "sunscreen",
        "expected_price_max": 300000
    },
    {
        "id": "MT_027",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Tư vấn serum phục hồi da sau nặn mụn",
            "tầm 400k đổ lại thôi"
        ],
        "expected_category": "serum",
        "expected_price_max": 400000
    },
    {
        "id": "MT_028",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Tìm sữa rửa mặt cho da nhạy cảm",
            "loại nào rẻ hơn 200 nghìn không shop"
        ],
        "expected_category": "cleanser",
        "expected_price_max": 200000
    },
    {
        "id": "MT_029",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Kem dưỡng ẩm cho da khô mùa đông",
            "ngân sách tầm 350k"
        ],
        "expected_category": "cream",
        "expected_price_max": 350000
    },
    {
        "id": "MT_030",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Tìm nước tẩy trang dịu nhẹ không cồn",
            "giá học sinh sinh viên dưới 150k"
        ],
        "expected_category": "cleanser",
        "expected_price_max": 150000
    },
    {
        "id": "MT_031",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Toner cấp ẩm cho da dầu",
            "tầm giá 250k"
        ],
        "expected_category": "toner",
        "expected_price_max": 250000
    },
    {
        "id": "MT_032",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Tìm serum trị thâm mụn sáng da",
            "dưới 500k nhé"
        ],
        "expected_category": "serum",
        "expected_price_max": 500000
    },
    {
        "id": "MT_033",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Kem chống nắng nâng tone tự nhiên",
            "tầm 400k"
        ],
        "expected_category": "sunscreen",
        "expected_price_max": 400000
    },
    {
        "id": "MT_034",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Tẩy tế bào chết hóa học bha",
            "khoảng 300k"
        ],
        "expected_category": "Tẩy tế bào chết",
        "expected_price_max": 300000
    },
    {
        "id": "MT_035",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Mặt nạ cấp ẩm phục hồi da",
            "loại hộp dưới 200k"
        ],
        "expected_category": "Mặt nạ",
        "expected_price_max": 200000
    },
    {
        "id": "MT_036",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Son dưỡng môi có màu xinh",
            "dưới 100k thôi shop"
        ],
        "expected_category": "Dưỡng môi",
        "expected_price_max": 100000
    },
    {
        "id": "MT_037",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Sữa rửa mặt dạng gel kiềm dầu",
            "dưới 250k"
        ],
        "expected_category": "cleanser",
        "expected_price_max": 250000
    },
    {
        "id": "MT_038",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Kem dưỡng phục hồi b5",
            "tầm 300k đổ lại"
        ],
        "expected_category": "cream",
        "expected_price_max": 300000
    },
    {
        "id": "MT_039",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Xịt khoáng làm dịu da nhạy cảm",
            "dưới 200k"
        ],
        "expected_category": "Xịt khoáng",
        "expected_price_max": 200000
    },
    {
        "id": "MT_040",
        "group": "Price_Constraint_Adjustment",
        "turns": [
            "Kem chống nắng kiềm dầu đi học",
            "dưới 180k thôi nha"
        ],
        "expected_category": "sunscreen",
        "expected_price_max": 180000
    },

    # --- NHÓM 3: BỔ SUNG YÊU CẦU HOẠT CHẤT & TIÊU CHÍ PHỦ ĐỊNH (15 Ca) ---
    {
        "id": "MT_041",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Tìm kem chống nắng cho da dầu",
            "không cồn không hương liệu nhé"
        ],
        "expected_category": "sunscreen",
        "expected_negative": "alcohol_free"
    },
    {
        "id": "MT_042",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Tư vấn nước tẩy trang làm sạch sâu",
            "không chứa cồn và paraben"
        ],
        "expected_category": "cleanser",
        "expected_negative": "alcohol_free"
    },
    {
        "id": "MT_043",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Tìm kem dưỡng cho da nhạy cảm",
            "không có mùi hương liệu"
        ],
        "expected_category": "cream",
        "expected_negative": "fragrance_free"
    },
    {
        "id": "MT_044",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Tìm sữa rửa mặt cho da mụn",
            "loại không chứa sulfate tạo bọt mạnh"
        ],
        "expected_category": "cleanser",
        "expected_negative": None
    },
    {
        "id": "MT_045",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Kem chống nắng cho da mụn",
            "không nâng tone không để lại vệt trắng"
        ],
        "expected_category": "sunscreen",
        "expected_negative": "no_white_cast"
    },
    {
        "id": "MT_046",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Tư vấn kem dưỡng ban ngày",
            "thấm nhanh không gây nhờn dính"
        ],
        "expected_category": "cream",
        "expected_negative": None
    },
    {
        "id": "MT_047",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Tìm serum làm dịu phục hồi",
            "chứa b5 và rau má centella"
        ],
        "expected_category": "serum",
        "expected_ingredient": "b5"
    },
    {
        "id": "MT_048",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Serum mờ thâm nám",
            "thành phần có chứa niacinamide"
        ],
        "expected_category": "serum",
        "expected_ingredient": "niacinamide"
    },
    {
        "id": "MT_049",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Kem dưỡng cho da treatment",
            "có thành phần ceramide khóa ẩm"
        ],
        "expected_category": "cream",
        "expected_ingredient": "ceramide"
    },
    {
        "id": "MT_050",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Sữa rửa mặt cho da dầu mụn",
            "chứa salicylic acid bha"
        ],
        "expected_category": "cleanser",
        "expected_ingredient": "salicylic acid"
    },
    {
        "id": "MT_051",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Toner cấp nước se khít chân lông",
            "loại không cồn khô của klairs"
        ],
        "expected_category": "toner",
        "expected_brand": "Klairs"
    },
    {
        "id": "MT_052",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Kem chống nắng cho da khô",
            "loại có dưỡng ẩm mỏng nhẹ"
        ],
        "expected_category": "sunscreen",
        "expected_skin": "khô"
    },
    {
        "id": "MT_053",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Serum chống lão hóa",
            "chứa retinol nồng độ thấp cho người mới"
        ],
        "expected_category": "serum",
        "expected_ingredient": "retinol"
    },
    {
        "id": "MT_054",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Kem dưỡng ẩm dạng gel",
            "không gây bít tắc chân lông sinh mụn"
        ],
        "expected_category": "cream",
        "expected_negative": None
    },
    {
        "id": "MT_055",
        "group": "Negative_and_Safety_Filter",
        "turns": [
            "Nước hoa hồng làm dịu da",
            "chiết xuất hoa cúc không hương liệu"
        ],
        "expected_category": "toner",
        "expected_negative": "fragrance_free"
    },

    # --- NHÓM 4: CHUYỂN ĐỔI NGÀNH HÀNG DOMAIN SWITCH (15 Ca) ---
    {
        "id": "MT_056",
        "group": "Domain_Switching",
        "turns": [
            "Tìm kem chống nắng cho da dầu mụn",
            "chuyển sang dầu gội trị gàu giúp mình"
        ],
        "expected_category": "Dầu gội",
        "expected_domain": "HAIRCARE"
    },
    {
        "id": "MT_057",
        "group": "Domain_Switching",
        "turns": [
            "Tư vấn sữa rửa mặt dịu nhẹ",
            "đổi sang sữa tắm dưỡng ẩm cơ thể"
        ],
        "expected_category": "Sữa tắm",
        "expected_domain": "BODYCARE"
    },
    {
        "id": "MT_058",
        "group": "Domain_Switching",
        "turns": [
            "Tìm kem dưỡng b5 phục hồi",
            "sang son môi có màu cam đào"
        ],
        "expected_category": "Son môi",
        "expected_domain": "LIPCARE"
    },
    {
        "id": "MT_059",
        "group": "Domain_Switching",
        "turns": [
            "Tư vấn serum trị thâm nám",
            "chuyển qua nước hoa mùi ngọt mát"
        ],
        "expected_category": "Nước hoa",
        "expected_domain": "FRAGRANCE"
    },
    {
        "id": "MT_060",
        "group": "Domain_Switching",
        "turns": [
            "Tìm kem nền che khuyết điểm",
            "đổi sang kem dưỡng ẩm phục hồi da"
        ],
        "expected_category": "cream",
        "expected_domain": "SKINCARE"
    },
    {
        "id": "MT_061",
        "group": "Domain_Switching",
        "turns": [
            "Dầu xả phục hồi tóc khô xơ",
            "sang nước tẩy trang mắt môi"
        ],
        "expected_category": "cleanser",
        "expected_domain": "SKINCARE"
    },
    {
        "id": "MT_062",
        "group": "Domain_Switching",
        "turns": [
            "Kem chống nắng đi biển",
            "chuyển sang lăn khử mùi không ố vàng áo"
        ],
        "expected_category": "Khử mùi",
        "expected_domain": "BODYCARE"
    },
    {
        "id": "MT_063",
        "group": "Domain_Switching",
        "turns": [
            "Son dưỡng môi khô nứt",
            "đổi sang dầu gội bưởi giảm rụng tóc"
        ],
        "expected_category": "Dầu gội",
        "expected_domain": "HAIRCARE"
    },
    {
        "id": "MT_064",
        "group": "Domain_Switching",
        "turns": [
            "Phấn phủ kiềm dầu",
            "sang xịt khoáng cấp ẩm dịu da"
        ],
        "expected_category": "Xịt khoáng",
        "expected_domain": "SKINCARE"
    },
    {
        "id": "MT_065",
        "group": "Domain_Switching",
        "turns": [
            "Sữa dưỡng thể trắng da",
            "chuyển qua serum b5 cho da mặt"
        ],
        "expected_category": "serum",
        "expected_domain": "SKINCARE"
    },
    {
        "id": "MT_066",
        "group": "Domain_Switching",
        "turns": [
            "Nước hoa hồng cấp ẩm",
            "đổi sang mascara chuốt dài mi"
        ],
        "expected_category": "Mascara",
        "expected_domain": "MAKEUP"
    },
    {
        "id": "MT_067",
        "group": "Domain_Switching",
        "turns": [
            "Kem trị mụn bọc",
            "chuyển sang kem dưỡng tay khô nẻ"
        ],
        "expected_category": "Kem dưỡng thể",
        "expected_domain": "BODYCARE"
    },
    {
        "id": "MT_068",
        "group": "Domain_Switching",
        "turns": [
            "Tẩy tế bào chết da mặt",
            "sang tẩy tế bào chết body cà phê"
        ],
        "expected_category": "Tẩy tế bào chết",
        "expected_domain": "BODYCARE"
    },
    {
        "id": "MT_069",
        "group": "Domain_Switching",
        "turns": [
            "Dầu gội thảo dược",
            "đổi sang kem chống nắng cho da nhạy cảm"
        ],
        "expected_category": "sunscreen",
        "expected_domain": "SKINCARE"
    },
    {
        "id": "MT_070",
        "group": "Domain_Switching",
        "turns": [
            "Son lì mịn môi 3ce",
            "sang sữa rửa mặt làm sạch sâu"
        ],
        "expected_category": "cleanser",
        "expected_domain": "SKINCARE"
    },

    # --- NHÓM 5: ĐẠI TỪ QUY CHIẾU COREFERENCE & ENTITY MEMORY (10 Ca) ---
    {
        "id": "MT_071",
        "group": "Coreference_Resolution",
        "turns": [
            "Kem dưỡng B5 La Roche-Posay có tốt không?",
            "nó dùng được cho da dầu mụn không shop"
        ],
        "expected_brand": "La Roche-Posay",
        "expected_category": "cream"
    },
    {
        "id": "MT_072",
        "group": "Coreference_Resolution",
        "turns": [
            "Kem chống nắng Anessa vàng",
            "loại này giá bao nhiêu tiền"
        ],
        "expected_brand": "Anessa",
        "expected_intent": "PRICE_QUERY"
    },
    {
        "id": "MT_073",
        "group": "Coreference_Resolution",
        "turns": [
            "Serum Torriden Dive-in Hyaluronic Acid",
            "sản phẩm này có chứa cồn không"
        ],
        "expected_brand": "Torriden",
        "expected_category": "serum"
    },
    {
        "id": "MT_074",
        "group": "Coreference_Resolution",
        "turns": [
            "Nước hoa hồng Klairs không mùi",
            "cái này bầu dùng được không"
        ],
        "expected_brand": "Klairs",
        "expected_category": "toner"
    },
    {
        "id": "MT_075",
        "group": "Coreference_Resolution",
        "turns": [
            "Sữa rửa mặt Cerave Foaming Facial Cleanser",
            "nó có dung tích bao nhiêu ml"
        ],
        "expected_brand": "Cerave",
        "expected_category": "cleanser"
    },
    {
        "id": "MT_076",
        "group": "Coreference_Resolution",
        "turns": [
            "Dung dịch BHA 2% Paula's Choice",
            "cách dùng nó như thế nào để không kích ứng"
        ],
        "expected_brand": "Paula's Choice",
        "expected_category": "Tẩy tế bào chết"
    },
    {
        "id": "MT_077",
        "group": "Coreference_Resolution",
        "turns": [
            "Kem chống nắng Martiderm The Originals",
            "loại này kiềm dầu tốt không"
        ],
        "expected_brand": "Martiderm",
        "expected_category": "sunscreen"
    },
    {
        "id": "MT_078",
        "group": "Coreference_Resolution",
        "turns": [
            "Nước tẩy trang Bioderma nắp hồng",
            "nó có cồn hay chất tạo mùi gì không"
        ],
        "expected_brand": "Bioderma",
        "expected_category": "cleanser"
    },
    {
        "id": "MT_079",
        "group": "Coreference_Resolution",
        "turns": [
            "Serum Vitamin C tươi Klairs Freshly Juiced",
            "nó có dễ bị oxy hóa ngả vàng không"
        ],
        "expected_brand": "Klairs",
        "expected_category": "serum"
    },
    {
        "id": "MT_080",
        "group": "Coreference_Resolution",
        "turns": [
            "Kem dưỡng ẩm Simple Kind to Skin",
            "cái này bôi lên có bị bết nhờn da không"
        ],
        "expected_brand": "Simple",
        "expected_category": "cream"
    },

    # --- NHÓM 6: DỊCH VỤ SPA & ĐẶT LỊCH HẸN (10 Ca) ---
    {
        "id": "MT_081",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Bên mình có gói nặn mụn chuẩn y khoa không?",
            "cho mình xem bảng giá chi tiết"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_082",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Viện có liệu trình peel da trị mụn không shop",
            "mình muốn đặt lịch chiều mai 14h tại chi nhánh quận 1"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_083",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Dịch vụ triệt lông nách công nghệ diode laser",
            "giá một buổi bao nhiêu tiền"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_084",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Gói điện di tinh chất b5 phục hồi da",
            "thời gian làm mất bao lâu vậy shop"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_085",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Dịch vụ soi da và phân tích biểu bì",
            "đặt lịch sáng thứ 7 này nha"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_086",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Liệu trình cấy căng bóng trẻ hóa da",
            "bên mình có chi nhánh ở đâu"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_087",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Gói gội đầu dưỡng sinh thư giãn",
            "giá bao nhiêu một suất 60 phút"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_088",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Viện có dịch vụ massage body thảo dược không",
            "đặt lịch 17h chiều nay nhé"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_089",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Chăm sóc da mặt chuyên sâu cho da mụn",
            "quy trình gồm những bước nào"
        ],
        "expected_target": "SERVICE"
    },
    {
        "id": "MT_090",
        "group": "Spa_Service_and_Booking",
        "turns": [
            "Gói peel da mờ thâm nách",
            "đặt lịch làm vào cuối tuần này"
        ],
        "expected_target": "SERVICE"
    },

    # --- NHÓM 7: SO SÁNH, ROUTINE & TƯƠNG TÁC HOẠT CHẤT (10 Ca) ---
    {
        "id": "MT_091",
        "group": "Comparison_and_Routine",
        "turns": [
            "So sánh serum B5 La Roche-Posay và Vichy 89",
            "da dầu treatment nên chọn cái nào hơn"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_092",
        "group": "Comparison_and_Routine",
        "turns": [
            "Kem chống nắng Anessa vàng và La Roche-Posay Anthelios",
            "cái nào kiềm dầu tốt hơn"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_093",
        "group": "Comparison_and_Routine",
        "turns": [
            "Nước tẩy trang Bioderma hồng và xanh lá",
            "da dầu mụn dùng chai nào phù hợp"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_094",
        "group": "Comparison_and_Routine",
        "turns": [
            "Sữa rửa mặt Cerave và Cosrx Low pH",
            "loại nào dịu nhẹ hơn cho da nhạy cảm"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_095",
        "group": "Comparison_and_Routine",
        "turns": [
            "Routine buổi tối của mình gồm BHA và Retinol",
            "dùng chung cùng một buổi tối được không shop"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_096",
        "group": "Comparison_and_Routine",
        "turns": [
            "Lên giúp mình chu trình skincare sáng tối",
            "cho da hỗn hợp thiên dầu bị mụn ẩn"
        ],
        "expected_intent": "PRODUCT_SEARCH"
    },
    {
        "id": "MT_097",
        "group": "Comparison_and_Routine",
        "turns": [
            "Vitamin C có dùng chung với Niacinamide được không?",
            "thứ tự thoa cái nào trước cái nào sau"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_098",
        "group": "Comparison_and_Routine",
        "turns": [
            "Kem dưỡng B5 La Roche-Posay và Klairs Midnight Blue",
            "da đỏ rát phục hồi cái nào nhanh hơn"
        ],
        "expected_intent": "COMPARISON"
    },
    {
        "id": "MT_099",
        "group": "Comparison_and_Routine",
        "turns": [
            "Routine sáng: Sữa rửa mặt -> Toner -> Vitamin C -> Kem chống nắng",
            "chu trình này có cần thêm kem dưỡng ẩm không shop"
        ],
        "expected_intent": "ROUTINE_RECOMMENDATION"
    },
    {
        "id": "MT_100",
        "group": "Comparison_and_Routine",
        "turns": [
            "Mình đang bôi Treti 0.05% bị bong da đỏ rát",
            "nên kết hợp thêm serum phục hồi nào an toàn"
        ],
        "expected_intent": "PRODUCT_SEARCH"
    }
]


async def run_scenario(client: httpx.AsyncClient, scenario: Dict[str, Any], semaphore: asyncio.Semaphore) -> Dict[str, Any]:
    scenario_id = scenario["id"]
    group = scenario["group"]
    turns = scenario["turns"]
    session_id = f"bench_{scenario_id}_{int(time.time()*1000)}"

    results_turns = []
    success = True
    failure_reasons = []

    async with semaphore:
        for idx, msg in enumerate(turns):
            start_t = time.perf_counter()
            try:
                resp = await client.post(
                    API_URL,
                    json={"session_id": session_id, "message": msg},
                    timeout=45.0
                )
                latency = round((time.perf_counter() - start_t) * 1000, 2)
                
                if resp.status_code != 200:
                    success = False
                    failure_reasons.append(f"Turn {idx+1} HTTP {resp.status_code}: {resp.text[:100]}")
                    results_turns.append({
                        "turn": idx + 1,
                        "msg": msg,
                        "status": resp.status_code,
                        "latency_ms": latency,
                        "error": resp.text[:100]
                    })
                    break

                data = resp.json()
                results_turns.append({
                    "turn": idx + 1,
                    "msg": msg,
                    "latency_ms": latency,
                    "intent": data.get("intent"),
                    "needs_clarification": data.get("needs_clarification"),
                    "products_count": len(data.get("products", [])),
                    "answer_snippet": data.get("answer", "")[:120],
                    "target_type": (data.get("understanding") or {}).get("target_type")
                })

            except Exception as exc:
                latency = round((time.perf_counter() - start_t) * 1000, 2)
                success = False
                failure_reasons.append(f"Turn {idx+1} Exception: {str(exc)}")
                results_turns.append({
                    "turn": idx + 1,
                    "msg": msg,
                    "latency_ms": latency,
                    "error": str(exc)
                })
                break

    # Đánh giá tiêu chuẩn chất lượng cuộc hội thoại
    final_turn = results_turns[-1] if results_turns else {}
    
    # 1. Kiểm tra Context Retention & Clarification Resolution:
    # Nếu lượt cuối người dùng đã cung cấp đủ danh mục hoặc yêu cầu, bot KHÔNG ĐƯỢC lặp lại hỏi làm rõ!
    if group == "Clarification_to_Category":
        if final_turn.get("needs_clarification") is True:
            success = False
            failure_reasons.append("Final turn still needs clarification (context forgotten or category not recognized)")
        if final_turn.get("products_count", 0) == 0:
            success = False
            failure_reasons.append("Zero products returned for completed category request")

    elif group == "Price_Constraint_Adjustment":
        if final_turn.get("needs_clarification") is True:
            success = False
            failure_reasons.append("Price refinement triggered clarification unexpectedly")

    elif group == "Domain_Switching":
        # Kiểm tra xem có chuyển đúng target hoặc category mới không
        exp_dom = scenario.get("expected_domain")
        ans_low = final_turn.get("answer_snippet", "").lower()
        if exp_dom == "HAIRCARE" and "da mặt" in ans_low and "tóc" not in ans_low:
            success = False
            failure_reasons.append("Domain switch to Haircare contaminated with facial skincare context")

    elif group == "Spa_Service_and_Booking":
        if final_turn.get("target_type") not in ("SERVICE", "COMBO"):
            success = False
            failure_reasons.append("Spa service query not classified as SERVICE target")

    total_latency = sum(t.get("latency_ms", 0) for t in results_turns)

    return {
        "id": scenario_id,
        "group": group,
        "turns_count": len(turns),
        "success": success,
        "failure_reasons": failure_reasons,
        "total_latency_ms": round(total_latency, 2),
        "avg_turn_latency_ms": round(total_latency / len(turns), 2) if turns else 0,
        "history": results_turns
    }


async def main():
    print("=" * 70)
    print("KHỞI CHẠY KIỂM CHUẨN 100 CUỘC TRÒ CHUYỆN ĐA LƯỢT (MULTI-TURN BENCHMARK)")
    print(f"Tổng số kịch bản: {len(BENCHMARK_SCENARIOS)} cuộc trò chuyện dài (2-3 lượt/cuộc)")
    print("=" * 70)

    semaphore = asyncio.Semaphore(3)  # Kiểm soát đồng thời 3 phiên để bảo đảm độ ổn định API
    start_all = time.perf_counter()

    async with httpx.AsyncClient() as client:
        tasks = [
            run_scenario(client, sc, semaphore)
            for sc in BENCHMARK_SCENARIOS
        ]
        results = await asyncio.gather(*tasks)

    duration_all = round(time.perf_counter() - start_all, 2)

    passed = [r for r in results if r["success"]]
    failed = [r for r in results if not r["success"]]

    pass_rate = round(len(passed) / len(results) * 100, 2)
    avg_conv_latency = round(sum(r["total_latency_ms"] for r in results) / len(results), 2)
    avg_turn_latency = round(sum(r["avg_turn_latency_ms"] for r in results) / len(results), 2)

    # Thống kê theo nhóm
    groups = {}
    for r in results:
        g = r["group"]
        if g not in groups:
            groups[g] = {"total": 0, "passed": 0, "latencies": []}
        groups[g]["total"] += 1
        if r["success"]:
            groups[g]["passed"] += 1
        groups[g]["latencies"].append(r["avg_turn_latency_ms"])

    print("\n" + "=" * 70)
    print("KẾT QUẢ TỔNG HỢP KIỂM THỬ 100 CUỘC TRÒ CHUYỆN")
    print("=" * 70)
    print(f"Tổng số kịch bản kiểm thử : {len(results)}")
    print(f"Thành công (PASS)          : {len(passed)}")
    print(f"Thất bại (FAIL)           : {len(failed)}")
    print(f"Tỷ lệ thành công (Pass Rate): {pass_rate}%")
    print(f"Tổng thời gian chạy test  : {duration_all} s")
    print(f"Độ trễ trung bình / lượt  : {avg_turn_latency} ms")
    print(f"Độ trễ trung bình / phiên : {avg_conv_latency} ms")
    print("-" * 70)
    print("KẾT QUẢ CHI TIẾT THEO 7 NHÓM BÀI TOÁN:")
    for g, stat in groups.items():
        g_rate = round(stat["passed"] / stat["total"] * 100, 1)
        g_lat = round(sum(stat["latencies"]) / len(stat["latencies"]), 1)
        print(f" • {g:<30}: {stat['passed']}/{stat['total']} ({g_rate}%) | Avg: {g_lat}ms")

    # Lưu kết quả JSON để phục vụ báo cáo khoa học
    report_data = {
        "summary": {
            "total_conversations": len(results),
            "passed": len(passed),
            "failed": len(failed),
            "pass_rate_percent": pass_rate,
            "total_benchmark_time_seconds": duration_all,
            "avg_turn_latency_ms": avg_turn_latency,
            "avg_conversation_latency_ms": avg_conv_latency,
        },
        "group_breakdown": groups,
        "failed_scenarios": failed,
        "all_results": results
    }

    with open("benchmark_100_multiturn_results.json", "w", encoding="utf-8") as f:
        json.dump(report_data, f, ensure_ascii=False, indent=2)

    print("\nĐã lưu toàn bộ dữ liệu chi tiết vào file benchmark_100_multiturn_results.json")


if __name__ == "__main__":
    asyncio.run(main())
