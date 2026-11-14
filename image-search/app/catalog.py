import hashlib
from io import BytesIO
from pathlib import Path
from urllib.parse import unquote, urlsplit
import warnings
from typing import Dict, List, Optional

from PIL import Image, ImageOps, UnidentifiedImageError
import pymysql


class InvalidImageError(ValueError):
    pass


class UnsupportedImageError(InvalidImageError):
    pass


ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP", "AVIF", "GIF", "BMP"}


def preprocess_image_square(image: Image.Image, target_size: int = 224) -> Image.Image:
    """
    Dua anh ve kich thuoc vuong chuan bang cach giu nguyen ti le goc
    va them vien trang (letterbox padding viền trắng), giup mo hinh CLIP nhan dien
    chinh xac ty le hinh hoc san pham ma khong bi bop meo hay bien dang.
    """
    w, h = image.size
    if w <= 0 or h <= 0:
        return image

    if w == h:
        return image.resize((target_size, target_size), Image.Resampling.LANCZOS)

    ratio = target_size / max(w, h)
    new_w = max(1, int(w * ratio))
    new_h = max(1, int(h * ratio))

    resized_img = image.resize((new_w, new_h), Image.Resampling.LANCZOS)
    padded_img = Image.new("RGB", (target_size, target_size), (255, 255, 255))
    paste_x = (target_size - new_w) // 2
    paste_y = (target_size - new_h) // 2
    padded_img.paste(resized_img, (paste_x, paste_y))
    return padded_img


def decode_image_bytes(raw: bytes, max_pixels: int = 20_000_000, square_pad: bool = True) -> Image.Image:
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(raw)) as image:
                if image.format not in {"JPEG", "PNG", "WEBP", "AVIF", "GIF", "BMP"}:
                    raise UnsupportedImageError("Chi ho tro anh JPEG, PNG, WEBP, AVIF, GIF hoac BMP.")
                if image.width * image.height > max_pixels:
                    raise InvalidImageError("Do phan giai anh qua lon vuot nguong an toan.")
                image.load()
                rgb = ImageOps.exif_transpose(image).convert("RGB")
                if square_pad:
                    return preprocess_image_square(rgb)
                return rgb
    except InvalidImageError:
        raise
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        raise InvalidImageError("Tep anh bi hong hoac khong dung dinh dang.") from exc


def resolve_upload_path(upload_dir: str, url: str) -> Optional[Path]:
    """Chuyen duong dan URL nhu /uploads/{filename} hoac http://.../uploads/{filename}
    thanh duong dan tap tin cuc bo tren disk volume.
    """
    if not url or not isinstance(url, str):
        return None
    url = url.strip()
    parts = urlsplit(url)
    path = unquote(parts.path)

    # Chặn URL chứa ký tự nguy hiểm escape path
    if "\\" in path or ".." in path:
        return None

    if path.startswith("/uploads/"):
        relative = path.removeprefix("/uploads/")
    elif not parts.scheme and not parts.netloc:
        relative = path.lstrip("/")
    else:
        # Nếu là URL tuyệt đối có path /uploads/...
        idx = path.find("/uploads/")
        if idx != -1:
            relative = path[idx + len("/uploads/") :]
        else:
            return None

    root = Path(upload_dir).resolve()
    target = (root / relative).resolve()

    if not target.is_relative_to(root):
        return None
    if not target.exists() or not target.is_file():
        return None

    return target


def read_catalog_image(upload_dir: str, url: str, max_bytes: int = 5 * 1024 * 1024) -> Optional[bytes]:
    target = resolve_upload_path(upload_dir, url)
    if target:
        try:
            with target.open("rb") as stream:
                raw = stream.read(max_bytes + 1)
            if len(raw) > max_bytes:
                return None
            return raw
        except Exception:
            return None

    # Fallback doc anh tu URL ngoai (vi du: anh seed unsplash, CDN)
    if url and (url.startswith("http://") or url.startswith("https://")):
        try:
            import urllib.request
            req = urllib.request.Request(url, headers={"User-Agent": "BeautyShop-AI/1.0"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                raw = resp.read(max_bytes + 1)
                if len(raw) > max_bytes:
                    return None
                return raw
        except Exception:
            return None

    return None


def fetch_all_catalog_images(settings) -> List[Dict]:
    """Doc toan bo anh san pham va thumbnail tu MySQL database gom ca category_ids va brand_id."""
    connection = pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10,
        read_timeout=30,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT p.id AS product_id, pi.id AS image_id, pi.image_url,
                       p.status AS product_status, p.name AS product_name,
                       p.brand_id, 0 AS is_thumbnail,
                       GROUP_CONCAT(DISTINCT pc.category_id SEPARATOR ',') AS category_ids_str
                FROM products p
                JOIN product_images pi ON pi.product_id = p.id
                LEFT JOIN product_categories pc ON pc.product_id = p.id
                WHERE p.is_deleted = 0 AND pi.is_deleted = 0
                  AND TRIM(pi.image_url) <> ''
                GROUP BY p.id, pi.id, pi.image_url, p.status, p.name, p.brand_id
                UNION ALL
                SELECT p.id AS product_id, -p.id AS image_id, p.thumbnail_url AS image_url,
                       p.status AS product_status, p.name AS product_name,
                       p.brand_id, 1 AS is_thumbnail,
                       GROUP_CONCAT(DISTINCT pc.category_id SEPARATOR ',') AS category_ids_str
                FROM products p
                LEFT JOIN product_categories pc ON pc.product_id = p.id
                WHERE p.is_deleted = 0
                  AND p.thumbnail_url IS NOT NULL AND TRIM(p.thumbnail_url) <> ''
                GROUP BY p.id, p.thumbnail_url, p.status, p.name, p.brand_id
                ORDER BY product_id, image_id DESC
            """
            )
            rows = cursor.fetchall()
    finally:
        connection.close()

    seen = set()
    result = []
    for row in rows:
        url = (row["image_url"] or "").strip()
        if not url:
            continue
        row["image_url"] = url
        
        # Parse chuoi category_ids thanh danh sach so nguyen
        cat_str = str(row.pop("category_ids_str", "") or "")
        category_ids = [int(c.strip()) for c in cat_str.split(",") if c.strip().isdigit()]
        row["category_ids"] = category_ids

        key = (row["product_id"], url)
        if key not in seen:
            seen.add(key)
            result.append(row)
    return result


def compute_sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

