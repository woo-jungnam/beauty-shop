import os
from typing import Any, List, Optional
import pymysql
import pymysql.cursors
from dotenv import load_dotenv
from app.core.config import getSettings
from app.core.logging import getLogger

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

    def extractProducts(self, limit: Optional[int] = None) -> List[dict[str, Any]]:
        conn = self.getConnection()
        query = """
            SELECT 
                p.id, 
                p.name, 
                COALESCE(b.name, 'Khác') as brand,
                COALESCE(cat.category_name, 'Khác') as category,
                CAST(prices.price AS DOUBLE) as price,
                p.status,
                p.skin_type,
                COALESCE(p.ingredients, '') as ingredients,
                COALESCE(p.how_to_use, '') as how_to_use,
                COALESCE(p.short_description, '') as short_description,
                COALESCE(p.description, '') as description,
                COALESCE(p.average_rating, 5.0) as rating,
                COALESCE(p.total_reviews, 0) as total_reviews,
                COALESCE(p.total_sold, 0) as total_sold,
                COALESCE(NULLIF(p.thumbnail_url, ''), (SELECT pi.image_url FROM product_images pi
                 WHERE pi.product_id = p.id AND pi.is_deleted = 0
                 ORDER BY pi.is_primary DESC, pi.display_order ASC, pi.id ASC
                 LIMIT 1)) as image_url,
                COALESCE(stk.total_stock, 0) as stock
            FROM products p
            JOIN (
                SELECT product_id, MIN(COALESCE(discount_price, price)) as price
                FROM product_variants
                WHERE is_deleted = 0 AND is_active = 1
                GROUP BY product_id
            ) prices ON p.id = prices.product_id
            LEFT JOIN brands b ON p.brand_id = b.id
            LEFT JOIN (
                SELECT pc.product_id, MIN(c.name) as category_name
                FROM product_categories pc
                JOIN categories c ON pc.category_id = c.id
                WHERE c.is_deleted = 0
                GROUP BY pc.product_id
            ) cat ON p.id = cat.product_id
            LEFT JOIN (
                SELECT pv.product_id, SUM(ws.quantity - ws.reserved_quantity) as total_stock
                FROM product_variants pv
                JOIN warehouse_stocks ws ON pv.id = ws.product_variant_id
                JOIN warehouses w ON ws.warehouse_id = w.id
                WHERE pv.is_deleted = 0 AND pv.is_active = 1 AND ws.is_deleted = 0
                  AND w.is_active = 1 AND w.is_deleted = 0
                  AND (ws.expiration_date IS NULL OR ws.expiration_date > CURRENT_DATE)
                GROUP BY pv.product_id
            ) stk ON p.id = stk.product_id
            WHERE p.is_deleted = 0 AND p.status = 'ACTIVE'
            ORDER BY p.id ASC
        """
        if limit is not None and limit > 0:
            query += f" LIMIT {int(limit)}"

        try:
            with conn.cursor() as cur:
                cur.execute(query)
                rows = cur.fetchall()
        finally:
            conn.close()

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
                skinTypes = ["all", "oily", "dry", "combination", "sensitive"]
            else:
                skinTypes = ["all"]

            textForConcerns = f"{r['name']} {r['short_description']} {r['description']} {rawCategory}".lower()
            detectedConcerns = [
                concernKey for concernKey, kws in CONCERN_KEYWORDS.items()
                if any(kw in textForConcerns for kw in kws)
            ]

            benefitsList = []
            if r["short_description"]:
                benefitsList.append(str(r["short_description"]).strip())
            if r["description"] and r["description"] != r["short_description"]:
                benefitsList.append(str(r["description"]).strip())

            rawIngredients = r["ingredients"] or ""
            ingList = [i.strip() for i in rawIngredients.replace(";", ",").split(",") if i.strip()] if isinstance(rawIngredients, str) else []

            formattedProducts.append({
                "id": f"mysql_{r['id']}",
                "name": str(r["name"]).strip(),
                "brand": str(r["brand"]).strip(),
                "category": categoryVal,
                "price": float(r["price"] or 0.0),
                "stock": int(r["stock"] or 0),
                "rating": float(r.get("rating") or 5.0),
                "total_reviews": int(r.get("total_reviews") or 0),
                "total_sold": int(r.get("total_sold") or 0),
                "description": str(r["description"] or r["short_description"] or "").strip(),
                "ingredients": ingList,
                "benefits": benefitsList,
                "skin_type": skinTypes,
                "concerns": detectedConcerns,
                "usage": str(r["how_to_use"] or "").strip(),
                "reviews": [],
                "target_type": "PRODUCT",
                "duration_minutes": 0,
                "image_url": r.get("image_url"),
            })

        return formattedProducts

    extract_products = extractProducts

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
        allData = self.extractAll(limit=limit)
        return await indexing_service.index_products(allData)

    sync_mysql_to_rag = syncMysqlToRag


dbExtractor = DatabaseExtractor()
db_extractor = dbExtractor
