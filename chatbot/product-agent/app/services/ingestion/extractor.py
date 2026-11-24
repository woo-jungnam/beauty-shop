import os
import json
from collections import defaultdict
from typing import Any, List, Optional
import pymysql
import pymysql.cursors
from dotenv import load_dotenv
from app.core.config import getSettings
from app.core.logging import getLogger
from app.services.inventory_availability import AVAILABLE_STOCK_SQL, sale_cutoff

load_dotenv()
logger = getLogger("db_extractor")

CATEGORY_MAP = {
    "kem dưỡng thể": "Kem dưỡng thể",
    "dưỡng thể": "Kem dưỡng thể",
    "sữa tắm": "Sữa tắm",
    "khử mùi": "Khử mùi",
    "serum": "serum",
    "tinh chất": "serum",
    "ampoule": "serum",
    "essence": "serum",
    "kem chống nắng": "sunscreen",
    "chống nắng": "sunscreen",
    "sunscreen": "sunscreen",
    "sữa rửa mặt": "cleanser",
    "rửa mặt": "cleanser",
    "tẩy trang": "cleanser",
    "bông tẩy trang": "Bông tẩy trang",
    "cleanser": "cleanser",
    "nước hoa hồng": "toner",
    "toner": "toner",
    "nước cân bằng": "toner",
    "mặt nạ": "Mặt nạ",
    "tẩy tế bào chết": "Tẩy tế bào chết",
    "tẩy da chết": "Tẩy tế bào chết",
    "xịt khoáng": "Xịt khoáng",
    "kem dưỡng": "cream",
    "dưỡng ẩm": "cream",
    "cream": "cream",
    "lotion": "cream",
    "dầu gội": "Dầu gội",
    "dầu xả": "Dầu xả",
    "dưỡng môi": "Dưỡng môi",
    "son môi": "Son môi",
    "kem nền": "Kem nền",
    "phấn phủ": "Phấn phủ",
    "má hồng": "Má hồng",
    "kẻ mắt": "Kẻ mắt",
    "mascara": "Mascara",
    "chì kẻ mày": "Chì kẻ mày",
    "mút trang điểm": "Mút trang điểm",
    "nước hoa": "Nước hoa",
}

CONCERN_KEYWORDS = {
    "acne": ["mụn", "acne", "blemish", "kháng viêm", "gom cồi"],
    "dark_spots": ["thâm", "nám", "sạm", "đốm nâu", "tàn nhang", "dark spot", "melasma", "đều màu da"],
    "hydration": ["cấp ẩm", "khô", "thiếu nước", "mất nước", "bong tróc", "dưỡng ẩm"],
    "excess_oil": ["kiềm dầu", "dầu nhờn", "bã nhờn", "bóng dầu", "se khít lỗ chân lông", "lỗ chân lông to"],
    "aging": ["lão hóa", "nếp nhăn", "chảy xệ", "đàn hồi", "collagen", "trẻ hóa"],
    "sensitive": ["nhạy cảm", "kích ứng", "đỏ rát", "dịu da", "phục hồi", "lành tính"],
}


class DatabaseExtractor:

    def __init__(self):
        settings = getSettings()
        self.host = settings.mysql_host
        self.port = int(settings.mysql_port)
        self.user = settings.mysql_user
        self.password = settings.mysql_password
        self.database = settings.mysql_database

    def getConnection(self):
        return pymysql.connect(
            host=self.host,
            port=self.port,
            user=self.user,
            password=self.password,
            database=self.database,
            charset="utf8mb4",
            autocommit=True,
            connect_timeout=10,
            read_timeout=30,
            cursorclass=pymysql.cursors.DictCursor,
        )

    _get_connection = getConnection

    def extractProducts(self, limit: Optional[int] = None, *, include_unavailable: bool = False) -> List[dict[str, Any]]:
        cutoff = sale_cutoff()
        conn = self.getConnection()
        query = """
            SELECT
                p.id,
                p.name, 
                COALESCE(b.name, 'Khác') as brand,
                COALESCE(cat.category_name, 'Khác') as category,
                CAST(COALESCE(prices.price, prices.catalog_price, 0) AS DOUBLE) as price,
                p.status,
                p.skin_type,
                p.origin_country, p.volume, p.target_gender, p.product_type, p.key_actives_summary,
                usage_detail.when_to_use, usage_detail.frequency, usage_detail.instructions, usage_detail.warnings,
                p.is_featured, p.has_fragrance, p.has_alcohol,
                COALESCE(p.ingredients, '') as ingredients,
                COALESCE(p.how_to_use, '') as how_to_use,
                COALESCE(p.short_description, '') as short_description,
                COALESCE(p.description, '') as description,
                COALESCE(p.average_rating, 0.0) as rating,
                COALESCE(p.total_reviews, 0) as total_reviews,
                COALESCE(p.total_sold, 0) as total_sold,
                COALESCE(NULLIF(p.thumbnail_url, ''), (SELECT pi.image_url FROM product_images pi
                 WHERE pi.product_id = p.id AND pi.is_deleted = 0
                 ORDER BY pi.is_primary DESC, pi.display_order ASC, pi.id ASC
                 LIMIT 1)) as image_url,
                COALESCE(prices.total_stock, 0) as stock
            FROM products p
            LEFT JOIN (
                SELECT pv.product_id,
                       MIN(CASE WHEN safe_stock.available > 0 THEN COALESCE(pv.discount_price, pv.price) END) AS price,
                       MIN(COALESCE(pv.discount_price, pv.price)) AS catalog_price,
                       SUM(safe_stock.available) AS total_stock
                FROM product_variants pv
                LEFT JOIN (""" + AVAILABLE_STOCK_SQL + """) safe_stock ON safe_stock.product_variant_id = pv.id
                WHERE pv.is_deleted = 0 AND pv.is_active = 1
                GROUP BY pv.product_id
            ) prices ON p.id = prices.product_id
            LEFT JOIN brands b ON p.brand_id = b.id
            LEFT JOIN product_usage_details usage_detail ON usage_detail.product_id = p.id
            LEFT JOIN (
                SELECT pc.product_id, GROUP_CONCAT(DISTINCT c.name ORDER BY c.name SEPARATOR ' | ') as category_name
                FROM product_categories pc
                JOIN categories c ON pc.category_id = c.id
                WHERE c.is_deleted = 0
                GROUP BY pc.product_id
            ) cat ON p.id = cat.product_id
            WHERE p.is_deleted = 0 AND p.status = 'ACTIVE'
        """
        if not include_unavailable:
            query += " AND prices.total_stock > 0"
        query += " ORDER BY p.id ASC"
        if limit is not None and limit > 0:
            query += f" LIMIT {int(limit)}"

        try:
            with conn.cursor() as cur:
                cur.execute("START TRANSACTION READ ONLY")
                cur.execute(query, (cutoff,))
                rows = cur.fetchall()
                variants = self.readCatalogVariants(cur, [r["id"] for r in rows], cutoff)
                cur.execute("""
                    SELECT v.product_id, v.product_variant_id, d.attribute_name, d.unit,
                           v.value_string, v.value_text, v.value_number, v.value_boolean
                    FROM product_attribute_values v
                    JOIN product_attribute_definitions d ON d.id = v.attribute_definition_id
                    WHERE v.is_deleted = 0 AND d.is_deleted = 0 AND d.is_visible = 1
                    ORDER BY d.display_order, d.id, v.id
                """)
                attributes = defaultdict(list)
                for row in cur.fetchall():
                    value = row["value_string"] or row["value_text"]
                    if not value and row["value_number"] is not None:
                        value = f"{row['value_number']:g} {row['unit'] or ''}".strip()
                    if not value and row["value_boolean"] is not None:
                        value = "Có" if row["value_boolean"] else "Không"
                    if value:
                        attributes[(row["product_id"], row["product_variant_id"])].append(
                            f"{row['attribute_name']}: {value}"
                        )
                cur.execute("""
                    SELECT m.product_id, t.name FROM product_tag_mapping m
                    JOIN product_tags t ON t.id = m.tag_id
                    WHERE t.is_deleted = 0 ORDER BY t.name
                """)
                tags = defaultdict(list)
                for row in cur.fetchall():
                    tags[row["product_id"]].append(row["name"])
                cur.execute("SELECT pi.product_id, i.name, i.inci_name, i.functions, i.benefits FROM product_ingredients pi JOIN ingredients i ON i.id=pi.ingredient_id WHERE i.is_deleted=0")
                ingredientRows = cur.fetchall()
                cur.execute("SELECT c.product_id, s.code, c.score, c.is_recommended, c.contraindication_reason FROM product_skin_compatibility c JOIN skin_types s ON s.id=c.skin_type_id")
                skinRows = cur.fetchall()
                cur.execute("SELECT c.product_id, s.code, c.score FROM product_skin_concerns c JOIN skin_concerns s ON s.id=c.concern_id")
                concernRows = cur.fetchall()
        finally:
            conn.close()

        ingredientMap = defaultdict(list)
        skinMap = defaultdict(list)
        concernMap = defaultdict(list)
        for row in ingredientRows:
            parts = [row['name'], row['inci_name']]
            for key in ('functions', 'benefits'):
                value = row.get(key) or []
                if isinstance(value, str):
                    value = json.loads(value)
                parts.extend(str(x) for x in value)
            ingredientMap[row['product_id']].append(' '.join(parts))
        for row in skinRows:
            skinMap[row['product_id']].append(row)
        for row in concernRows:
            concernMap[row['product_id']].append(row)

        formattedProducts: List[dict[str, Any]] = []
        for r in rows:
            rawCategory = str(r["category"]).strip()
            catLower = rawCategory.lower()
            normalizedCategory = None
            for kw, stdCat in CATEGORY_MAP.items():
                if kw in catLower:
                    normalizedCategory = stdCat
                    break
            categoryVal = normalizedCategory or rawCategory

            rawSkin = str(r.get("skin_type") or "").upper().strip()
            if rawSkin == "OILY":
                skinTypes = ["oily"]
            elif rawSkin == "DRY":
                skinTypes = ["dry"]
            elif rawSkin == "SENSITIVE":
                skinTypes = ["sensitive"]
            elif rawSkin == "ALL_SKIN":
                skinTypes = ["all"]
            elif rawSkin == "COMBINATION":
                skinTypes = ["combination"]
            elif rawSkin == "NORMAL":
                skinTypes = ["normal"]
            else:
                skinTypes = []
            compatibility = skinMap[r['id']]
            if compatibility:
                skinTypes = [('all' if item['code'] == 'ALL_SKIN' else item['code'].lower())
                             for item in compatibility if item['is_recommended'] and float(item['score']) > 0]

            textForConcerns = f"{r['name']} {r['short_description']} {r['description']} {rawCategory}".lower()
            detectedConcerns = [
                concernKey for concernKey, kws in CONCERN_KEYWORDS.items()
                if any(kw in textForConcerns for kw in kws)
            ]
            if concernMap[r['id']]:
                detectedConcerns = [item['code'].lower() for item in concernMap[r['id']] if float(item['score']) > 0]

            benefitsList = []
            if r["short_description"]:
                benefitsList.append(str(r["short_description"]).strip())
            if r["description"] and r["description"] != r["short_description"]:
                benefitsList.append(str(r["description"]).strip())

            rawIngredients = r["ingredients"] or ""
            ingList = [i.strip() for i in rawIngredients.replace(";", ",").split(",") if i.strip()] if isinstance(rawIngredients, str) else []
            ingList = list(dict.fromkeys(ingList + ingredientMap[r["id"]]))
            productVariants = variants[r["id"]]
            for variant in productVariants:
                variant["attributes"].extend(attributes[(r["id"], variant["id"])])
            specs = [
                f"{label}: {r[key]}" for key, label in (
                    ("origin_country", "Xuất xứ"), ("target_gender", "Đối tượng"),
                    ("product_type", "Loại sản phẩm"), ("key_actives_summary", "Hoạt chất chính"),
                ) if r.get(key)
            ]
            if r.get("volume") and not any(variant["volume"] for variant in productVariants):
                specs.append(f"Dung tích: {r['volume']}")
            for key, label in (("has_fragrance", "Hương liệu"), ("has_alcohol", "Cồn")):
                if r.get(key) is not None:
                    specs.append(f"{label}: {'Có' if r[key] else 'Không'}")
            if tags[r["id"]]:
                specs.append(f"Nhãn: {', '.join(tags[r['id']])}")
            specs.extend(attributes[(r["id"], None)])

            usage = [str(r["how_to_use"] or "").strip()]
            for key, label in (
                ("when_to_use", "Thời điểm"), ("frequency", "Tần suất"),
                ("instructions", "Hướng dẫn"), ("warnings", "Lưu ý"),
            ):
                value = r.get(key)
                if value and key != "frequency":
                    value = json.loads(value) if isinstance(value, str) else value
                    value = "; ".join(str(item) for item in value) if isinstance(value, list) else str(value or "")
                if value:
                    usage.append(f"{label}: {value}")

            formattedProducts.append({
                "id": f"mysql_{r['id']}",
                "name": str(r["name"]).strip(),
                "brand": str(r["brand"]).strip(),
                "category": categoryVal,
                "category_names": [name.strip() for name in rawCategory.split(' | ') if name.strip()],
                "price": float(r["price"] or 0.0),
                "stock": int(r["stock"] or 0),
                "rating": float(r.get("rating") or 0.0),
                "total_reviews": int(r.get("total_reviews") or 0),
                "total_sold": int(r.get("total_sold") or 0),
                "description": str(r["description"] or r["short_description"] or "").strip(),
                "ingredients": ingList,
                "mapped_ingredients": ingredientMap[r['id']],
                "has_fragrance": None if r.get('has_fragrance') is None else bool(r['has_fragrance']),
                "has_alcohol": None if r.get('has_alcohol') is None else bool(r['has_alcohol']),
                "is_featured": bool(r.get('is_featured')),
                "not_ideal_skin_types": [item['code'].lower() for item in compatibility if not item['is_recommended']],
                "benefits": benefitsList,
                "skin_type": skinTypes,
                "skin_scores": {('all' if item['code'] == 'ALL_SKIN' else item['code'].lower()): float(item['score'])
                                for item in compatibility if item['is_recommended']},
                "concerns": detectedConcerns,
                "concern_scores": {item['code'].lower(): float(item['score']) for item in concernMap[r['id']]},
                "usage": " | ".join(filter(None, usage)),
                "catalog_details": {"specifications": specs, "variants": productVariants},
                "reviews": [],
                "target_type": "PRODUCT",
                "duration_minutes": 0,
                "image_url": r.get("image_url"),
            })

        return formattedProducts

    extract_products = extractProducts

    @staticmethod
    def readCatalogVariants(cur, product_ids, cutoff):
        variants = defaultdict(list)
        if not product_ids:
            return variants
        placeholders = ", ".join(["%s"] * len(product_ids))
        cur.execute("""
            SELECT pv.id, pv.product_id, pv.variant_name, pv.sku, pv.volume,
                   pv.volume_value, pv.volume_unit, pv.color, pv.currency,
                   COALESCE(pv.discount_price, pv.price) AS price,
                   pv.original_price, COALESCE(safe_stock.available, 0) AS stock,
                   pv.weight, pv.weight_unit, pv.length, pv.width, pv.height, pv.dimension_unit,
                   pv.expiry_months, pv.expiry_after_opening_months, pv.storage_condition,
                   pv.manufacturer_name, pv.manufacturing_country
            FROM product_variants pv
            JOIN products p ON p.id = pv.product_id AND p.is_deleted = 0 AND p.status = 'ACTIVE'
            LEFT JOIN (""" + AVAILABLE_STOCK_SQL + """) safe_stock ON safe_stock.product_variant_id = pv.id
            WHERE pv.is_deleted = 0 AND pv.is_active = 1
              AND pv.product_id IN (""" + placeholders + """)
            ORDER BY pv.product_id, price, pv.id
        """, (cutoff, *product_ids))
        for row in cur.fetchall():
            volume = str(row["volume"] or "").strip()
            if not volume and row["volume_value"] is not None and row["volume_unit"]:
                volume = f"{row['volume_value'].normalize():f} {row['volume_unit']}"
            specs = [
                f"{label}: {row[key]}" for key, label in (
                    ("storage_condition", "Bảo quản"), ("manufacturer_name", "Nhà sản xuất"),
                    ("manufacturing_country", "Nơi sản xuất"),
                ) if row[key]
            ]
            for key, label, unit in (
                ("weight", "Khối lượng", row["weight_unit"]),
                ("length", "Chiều dài", row["dimension_unit"]),
                ("width", "Chiều rộng", row["dimension_unit"]),
                ("height", "Chiều cao", row["dimension_unit"]),
                ("expiry_months", "Hạn sử dụng", "tháng"),
                ("expiry_after_opening_months", "Hạn sau khi mở nắp", "tháng"),
            ):
                if row[key] is not None:
                    specs.append(f"{label}: {row[key]} {unit or ''}".strip())
            variants[row["product_id"]].append({
                "id": row["id"], "name": row["variant_name"], "sku": row["sku"] or "",
                "volume": volume, "color": row["color"] or "",
                "price": float(row["price"]), "currency": row["currency"] or "VND",
                "original_price": float(row["original_price"]) if row["original_price"] is not None else None,
                "in_stock": row["stock"] > 0, "attributes": specs,
            })
        return variants

    def getCatalogVariants(self, product_ids):
        conn = self.getConnection()
        try:
            with conn.cursor() as cur:
                return self.readCatalogVariants(cur, sorted(product_ids), sale_cutoff())
        finally:
            conn.close()

    def getAvailableProductPrices(self, product_ids):
        ids = sorted({int(value) for value in product_ids if str(value).isdigit() and int(value) > 0})
        if not ids:
            return {}
        cutoff = sale_cutoff()
        placeholders = ",".join(["%s"] * len(ids))
        query = """
            SELECT p.id, MIN(COALESCE(pv.discount_price, pv.price)) AS price
            FROM products p
            JOIN product_variants pv ON pv.product_id = p.id
            JOIN (""" + AVAILABLE_STOCK_SQL + """) safe_stock ON safe_stock.product_variant_id = pv.id
            WHERE p.is_deleted = 0 AND p.status = 'ACTIVE'
              AND pv.is_deleted = 0 AND pv.is_active = 1
              AND p.id IN (""" + placeholders + """)
            GROUP BY p.id
        """
        conn = self.getConnection()
        try:
            with conn.cursor() as cur:
                cur.execute(query, (cutoff, *ids))
                return {int(row['id']): float(row['price']) for row in cur.fetchall()}
        finally:
            conn.close()

    def extractServices(self, limit: Optional[int] = None) -> List[dict[str, Any]]:
        conn = self.getConnection()
        query = """
            SELECT 
                bs.id,
                bs.name,
                COALESCE(sc.name, 'Dịch vụ Spa') as category,
                CAST(bs.base_price AS FLOAT) as price,
                bs.duration_minutes,
                COALESCE(bs.short_description, '') as short_description,
                COALESCE(bs.description, '') as description,
                COALESCE(bs.thumbnail_url, '') as thumbnail_url
            FROM beauty_services bs
            LEFT JOIN service_categories sc ON bs.category_id = sc.id
            WHERE bs.is_deleted = 0 AND bs.is_active = 1
            ORDER BY bs.id ASC
        """
        if limit is not None and limit > 0:
            query += f" LIMIT {int(limit)}"

        try:
            with conn.cursor() as cur:
                cur.execute(query)
                rows = cur.fetchall()
        finally:
            conn.close()

        formattedServices: List[dict[str, Any]] = []
        for r in rows:
            name = str(r["name"]).strip()
            desc = str(r["description"]).strip()
            shortDesc = str(r["short_description"]).strip()
            cat = str(r["category"]).strip()
            fullText = f"{name} {shortDesc} {desc} {cat}".lower()

            detectedConcerns = [
                concernKey for concernKey, kws in CONCERN_KEYWORDS.items()
                if any(kw in fullText for kw in kws)
            ]

            if "mụn" in fullText or "acne" in fullText:
                skinTypes = ["oily", "acne_prone", "combination"]
            elif "mẹ bầu" in fullText or "bầu" in fullText or "organic" in fullText:
                skinTypes = ["sensitive", "all", "dry"]
            elif "nám" in fullText or "tàn nhang" in fullText or "lão hóa" in fullText:
                skinTypes = ["all", "dry", "combination"]
            elif "nhạy cảm" in fullText or "phục hồi" in fullText or "b5" in fullText:
                skinTypes = ["sensitive", "all", "dry"]
            else:
                skinTypes = ["all"]

            benefits = [shortDesc] if shortDesc else []
            if desc and desc != shortDesc:
                benefits.append(desc[:200] + "..." if len(desc) > 200 else desc)

            formattedServices.append({
                "id": f"service_{r['id']}",
                "name": name,
                "brand": "Beauty Spa & Clinic",
                "category": cat,
                "price": float(r["price"] or 0.0),
                "stock": 999,
                "rating": 5.0,
                "total_reviews": 120,
                "total_sold": 250,
                "description": desc or shortDesc,
                "ingredients": ["Công nghệ y khoa vô khuẩn", "Dưỡng chất chuyên sâu", "Liệu pháp thư giãn"],
                "benefits": benefits,
                "skin_type": skinTypes,
                "concerns": detectedConcerns,
                "usage": f"Quy trình thực hiện: {desc}",
                "reviews": ["Khách hàng đánh giá liệu trình rất thư giãn, da cải thiện rõ rệt ngay buổi đầu tiên."],
                "target_type": "SERVICE",
                "duration_minutes": int(r.get("duration_minutes") or 60),
                "image_url": r.get("thumbnail_url") or "https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=500",
            })

        return formattedServices

    extract_services = extractServices

    def extractAll(self, limit: Optional[int] = None) -> List[dict[str, Any]]:
        prods = self.extractProducts(limit=limit)
        srvs = self.extractServices(limit=limit)
        return prods + srvs

    extract_all = extractAll

    async def syncMysqlToRag(self, limit: Optional[int] = None) -> dict[str, Any]:
        from app.services.ingestion.pipeline import indexing_service
        allData = self.extractProducts(limit=limit, include_unavailable=True) + self.extractServices(limit=limit)
        return await indexing_service.index_products(allData)

    sync_mysql_to_rag = syncMysqlToRag


dbExtractor = DatabaseExtractor()
db_extractor = dbExtractor
