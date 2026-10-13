#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Script: 1_generate_dataset.py (Expanded Vocabulary & Large Dataset)
Mục đích: Tự động tạo tập dữ liệu Triplet phong phú, đa dạng từ ngữ chuyên sâu
từ toàn bộ các file catalog sản phẩm & dịch vụ spa thực tế.

Mở rộng từ vựng (Vocabulary Expansion):
1. Khẩu ngữ & Slang 3 miền: đổ nhớt, khét lẹt, châm chích, mẩn đỏ, bạt sừng, rát da, mần đẹp, dính dớp, nặng mặt, nhẹ tênh, bí tắc, sần sùi, gom cồi, bong vảy.
2. Code-Switching chuyên sâu: treatment, breakout, finish ráo/matte/dewy, tone-up, layer skincare, barrier màng ẩm, broad-spectrum phổ rộng, non-comedogenic, oil-free, water-resistant, active ingredients, peel BHA, Retinol, Tretinoin, Niacinamide, B5 Panthenol, Hyaluronic Acid, Centella rau má, Ceramide, Peptide.
3. Dịch vụ Spa & Liệu trình: lấy nhân mụn y khoa, peel da sinh học, điện di ion lạnh B5/HA, triệt lông Diode Laser, phục hồi chuyên sâu, soi da vi điểm.
"""

import os
import sys
import io
import json
import random
import asyncio
from pathlib import Path
from typing import List, Dict, Any
import httpx
from dotenv import load_dotenv

# Đảm bảo in tiếng Việt chuẩn trên mọi console Windows
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "buffer"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

CURRENT_DIR = Path(__file__).resolve().parent
BASE_DIR = CURRENT_DIR.parent
DATA_DIR = BASE_DIR / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)

# Tìm file .env
ENV_PATHS = [
    BASE_DIR / ".env",
    BASE_DIR.parent / "product-agent" / ".env",
    BASE_DIR.parent.parent / ".env"
]
for ep in ENV_PATHS:
    if ep.exists():
        load_dotenv(ep)
        break

GEMINI_API_KEY = os.getenv("LLM_API_KEY") or os.getenv("GEMINI_API_KEY", "")

# Đường dẫn đến toàn bộ kho dữ liệu thực tế
PRODUCT_DATA_DIR = BASE_DIR.parent / "product-agent" / "app" / "data"

GENERATION_PROMPT_TEMPLATE = """Bạn là chuyên gia ngôn ngữ học & AI Dataset Engineer hàng đầu về lĩnh vực Mỹ phẩm, Làm đẹp và Spa Da liễu tại Việt Nam.

Dưới đây là thông tin về một sản phẩm hoặc dịch vụ thực tế:
- Tên: {name}
- Thương hiệu / Cơ sở: {brand}
- Phân loại / Danh mục: {category} (Mục tiêu: {target_type})
- Phù hợp làn da: {skin_type}
- Vấn đề giải quyết: {concerns}
- Mô tả chi tiết: {description}
- Lợi ích nổi bật: {benefits}
- Hoạt chất / Công nghệ: {ingredients}

Nhiệm vụ của bạn là tạo ra chính xác 3 bộ mẫu Triplet (Anchor - Positive - Negative) với TỪ VỰNG CỰC KỲ PHONG PHÚ, TỰ NHIÊN:

1. MẪU 1 (Khẩu ngữ 3 miền & Tiếng lóng giới trẻ):
   - Sử dụng các từ ngữ đời thường, giàu hình ảnh: 'đổ nhớt', 'khét lẹt', 'châm chích', 'bạt sừng', 'rát mặt', 'bí da', 'nặng mặt', 'nhẹ tênh', 'mặt sần sùi gom cồi', 'bong vảy'...
2. MẪU 2 (Ngôn ngữ lai Code-Switching chuyên sâu Da liễu & Treatment):
   - Sử dụng từ ngữ chuyên ngành giới skincare hay dùng: 'treatment', 'breakout', 'finish ráo/matte/dewy', 'tone-up sáng hồng', 'barrier màng ẩm', 'layer skincare', 'broad-spectrum', 'non-comedogenic', 'purging đẩy mụn', 'oil-free', 'peel BHA/AHA', 'Retinol', 'B5 Panthenol', 'Niacinamide'...
3. MẪU 3 (Đối nghịch kết cấu Texture Contrast hoặc Bẫy cùng thương hiệu):
   - Nhấn mạnh vào kết cấu đối nghịch (dạng gel mỏng nhẹ thấm nhanh vs kem đặc bơ sáp bết dính; hoặc serum lỏng vs dầu dưỡng dày; hoặc liệu trình spa chuẩn y khoa vs spa thông thường).

Quy tắc cho từng trường:
- "anchor": Câu hỏi tự nhiên của khách hàng (12 - 25 từ).
- "positive": Đoạn mô tả chuẩn xác vì sao sản phẩm/dịch vụ {name} này đáp ứng trọn vẹn nhu cầu trên (25 - 45 từ).
- "negative": Một đoạn mô tả về sản phẩm hoặc dịch vụ bẫy (hoặc cùng thương hiệu {brand} nhưng sai kết cấu/sai công năng/dễ gây bít tắc) khiến khách hàng không nên chọn.

Trả về kết quả DUY NHẤT dưới dạng JSON Array gồm 3 objects, không có bất kỳ văn bản thừa nào:
[
  {{
    "strategy": "SLANG_COLLOQUIAL",
    "anchor": "...",
    "positive": "...",
    "negative": "..."
  }},
  {{
    "strategy": "CODE_SWITCHING_TREATMENT",
    "anchor": "...",
    "positive": "...",
    "negative": "..."
  }},
  {{
    "strategy": "TEXTURE_CONTRAST_HARD_NEGATIVE",
    "anchor": "...",
    "positive": "...",
    "negative": "..."
  }}
]
"""

async def generate_triplets_for_product(client: httpx.AsyncClient, product: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Gửi thông tin sản phẩm tới Gemini để sinh các mẫu Triplet phong phú"""
    if not GEMINI_API_KEY:
        return create_fallback_triplets(product)

    prompt = GENERATION_PROMPT_TEMPLATE.format(
        name=product.get("name", ""),
        brand=product.get("brand", ""),
        category=product.get("category", ""),
        target_type=product.get("target_type", "PRODUCT"),
        skin_type=", ".join(product.get("skin_type", ["mọi loại da"])) if isinstance(product.get("skin_type"), list) else str(product.get("skin_type")),
        concerns=", ".join(product.get("concerns", [])) if isinstance(product.get("concerns"), list) else str(product.get("concerns")),
        description=product.get("description", ""),
        benefits=" | ".join(product.get("benefits", [])) if isinstance(product.get("benefits"), list) else str(product.get("benefits")),
        ingredients="; ".join(product.get("ingredients", [])) if isinstance(product.get("ingredients"), list) else str(product.get("ingredients")),
    )

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={GEMINI_API_KEY}"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.4,
            "responseMimeType": "application/json"
        }
    }

    try:
        res = await client.post(url, json=payload, timeout=45.0)
        if res.status_code == 200:
            res_json = res.json()
            raw_text = res_json["candidates"][0]["content"]["parts"][0]["text"]
            triplets = json.loads(raw_text)
            for t in triplets:
                t["product_id"] = product.get("id", "")
                t["product_name"] = product.get("name", "")
                t["brand"] = product.get("brand", "")
                t["category"] = product.get("category", "")
            return triplets
    except Exception as e:
        print(f"Lỗi API cho sản phẩm {product.get('name')}: {e}")

    return create_fallback_triplets(product)

def create_fallback_triplets(product: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Tạo mẫu dữ liệu offline với từ vựng phong phú khi không có kết nối API"""
    p_name = product.get("name", "Sản phẩm mỹ phẩm")
    p_brand = product.get("brand", "Thương hiệu")
    p_cat = product.get("category", "chăm sóc da")
    p_skin = ", ".join(product.get("skin_type", ["mọi loại da"])) if isinstance(product.get("skin_type"), list) else "mọi loại da"
    p_concerns = ", ".join(product.get("concerns", ["làm đẹp"])) if isinstance(product.get("concerns"), list) else "chăm sóc da"
    
    return [
        {
            "strategy": "SLANG_COLLOQUIAL",
            "product_id": product.get("id", ""),
            "product_name": p_name,
            "anchor": f"Mặt dạo này đổ nhớt chán chê, đi ngoài đường nắng khét lẹt châm chích cần tìm {p_cat} của {p_brand} nhẹ mặt ráo thoáng",
            "positive": f"{p_name} ({p_brand}) giải quyết tình trạng đổ nhờn và làm dịu da {p_skin}, mỏng nhẹ thông thoáng, bảo vệ màng ẩm không gây nặng mặt.",
            "negative": f"Kem dưỡng dạng sáp dầu đậm đặc chuyên cấp ẩm sâu cho da khô bong tróc, chứa nhiều gốc dầu dễ gây dính dớp bí tắc cho da mụn."
        },
        {
            "strategy": "CODE_SWITCHING_TREATMENT",
            "product_id": product.get("id", ""),
            "product_name": p_name,
            "anchor": f"Da đang treatment BHA bị breakout và đỏ rát, tìm {p_cat} của {p_brand} finish ráo phục hồi barrier không bết dính",
            "positive": f"{p_name} tối ưu hóa quá trình phục hồi màng ẩm barrier cho da nhạy cảm sau treatment, kết cấu lỏng nhẹ finish ráo mịn không gây bết rít.",
            "negative": f"Serum tẩy tế bào chết nồng độ cao AHA 30% có thể gây phỏng rát và tăng kích ứng dữ dội cho làn da đang tổn thương."
        },
        {
            "strategy": "TEXTURE_CONTRAST_HARD_NEGATIVE",
            "product_id": product.get("id", ""),
            "product_name": p_name,
            "anchor": f"Tìm {p_cat} dạng gel mỏng nhẹ thấm nhanh không nhờn dính phù hợp da {p_skin} đang bị {p_concerns}",
            "positive": f"{p_name} có kết cấu dạng gel lỏng nhẹ, thông thoáng lỗ chân lông, kiềm dầu tối ưu cho da {p_skin}, không để lại màng trắng hay cảm giác bết dính.",
            "negative": f"Kem dưỡng ẩm dạng balm bơ sáp đặc quánh giàu dưỡng chất chuyên dụng phục hồi sâu cho làn da khô nứt nẻ mùa đông."
        }
    ]

def load_all_catalogs() -> List[Dict[str, Any]]:
    """Gộp dữ liệu từ tất cả các file catalog có trong dự án"""
    all_products = []
    seen_ids = set()

    catalog_files = [
        PRODUCT_DATA_DIR / "sample_cosmetics.json",
        PRODUCT_DATA_DIR / "authentic_catalog_expansion.json",
        PRODUCT_DATA_DIR / "authentic_catalog_part2.json"
    ]

    for cpath in catalog_files:
        if cpath.exists():
            try:
                with open(cpath, "r", encoding="utf-8") as f:
                    cdata = json.load(f)
                prods = cdata.get("products", cdata) if isinstance(cdata, dict) else cdata
                for p in prods:
                    pid = p.get("id") or p.get("name")
                    if pid and pid not in seen_ids:
                        seen_ids.add(pid)
                        all_products.append(p)
                print(f"Đã nạp {len(prods)} mục từ {cpath.name}")
            except Exception as e:
                print(f"Lỗi khi đọc {cpath.name}: {e}")

    return all_products

async def main():
    print("=" * 75)
    print("BẮT ĐẦU QUY TRÌNH MỞ RỘNG TỪ VỰNG & TẠO DATASET QUY MÔ LỚN")
    print("=" * 75)

    all_products = load_all_catalogs()
    print(f"\nTổng số sản phẩm & dịch vụ có trong kho: {len(all_products)} mục.")

    # Lựa chọn đa dạng các danh mục khác nhau
    # Lấy mẫu phong phú đại diện cho các nhóm chính: sunscreen, serum, cleanser, toner, cream, mask, spa services
    categories = {}
    for p in all_products:
        cat = p.get("category", "other")
        if cat not in categories:
            categories[cat] = []
        categories[cat].append(p)

    selected_products = []
    # Lấy cân đối từ mỗi danh mục
    for cat, items in categories.items():
        selected_products.extend(items[:8])  # Lấy tối đa 8 sản phẩm mỗi nhóm danh mục

    # Đảm bảo có ít nhất 60 - 80 sản phẩm đại diện
    if len(selected_products) < 60:
        selected_products = all_products[:80]
    else:
        selected_products = selected_products[:80]

    print(f"Đã chọn lọc {len(selected_products)} sản phẩm đại diện đa dạng mọi danh mục.")
    print(f"Bắt đầu gọi sinh dữ liệu với từ vựng phong phú...")

    all_triplets = []
    async with httpx.AsyncClient() as client:
        batch_size = 5
        for i in range(0, len(selected_products), batch_size):
            batch = selected_products[i:i + batch_size]
            tasks = [generate_triplets_for_product(client, p) for p in batch]
            batch_results = await asyncio.gather(*tasks)
            for res in batch_results:
                if res:
                    all_triplets.extend(res)
            print(f"Tiến độ: {min(i + batch_size, len(selected_products))}/{len(selected_products)} sản phẩm (Đã tạo {len(all_triplets)} mẫu Triplet)...")
            await asyncio.sleep(0.4)

    print(f"\nTổng cộng đã tạo ra: {len(all_triplets)} mẫu Triplet chất lượng cao.")

    # Xáo trộn dữ liệu ngẫu nhiên
    random.seed(42)
    random.shuffle(all_triplets)

    # Chia tập Train (85%) và Validation (15%)
    split_idx = int(len(all_triplets) * 0.85)
    train_data = all_triplets[:split_idx]
    val_data = all_triplets[split_idx:]

    # Lưu vào thư mục embedding-finetuning
    train_file = DATA_DIR / "train_triplets.json"
    val_file = DATA_DIR / "val_triplets.json"

    with open(train_file, "w", encoding="utf-8") as f:
        json.dump(train_data, f, ensure_ascii=False, indent=2)

    with open(val_file, "w", encoding="utf-8") as f:
        json.dump(val_data, f, ensure_ascii=False, indent=2)

    print(f"Đã lưu tập Train: {train_file} ({len(train_data)} mẫu)")
    print(f"Đã lưu tập Validation: {val_file} ({len(val_data)} mẫu)")

    print("\nHOÀN TẤT TẠO VÀ MỞ RỘNG TẬP DỮ LIỆU HUẤN LUYỆN!")

if __name__ == "__main__":
    asyncio.run(main())
