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


def decode_image_bytes(raw: bytes, max_pixels: int = 20_000_000) -> Image.Image:
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(raw)) as image:
                if image.format not in {"JPEG", "PNG", "WEBP", "AVIF", "GIF", "BMP"}:
                    raise UnsupportedImageError("Chi ho tro anh JPEG, PNG, WEBP, AVIF, GIF hoac BMP.")
                if image.width * image.height > max_pixels:
                    raise InvalidImageError("Do phan giai anh qua lon vuot nguong an toan.")
                image.load()
                return ImageOps.exif_transpose(image).convert("RGB")
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
    if not target:
        return None
    try:
        with target.open("rb") as stream:
            raw = stream.read(max_bytes + 1)
        if len(raw) > max_bytes:
            return None
        return raw
    except Exception:
        return None


def fetch_all_catalog_images(settings) -> List[Dict]:
    """Doc toan bo anh san pham va thumbnail tu MySQL database."""
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
                       p.status AS product_status, p.name AS product_name, 0 AS is_thumbnail
                FROM products p
                JOIN product_images pi ON pi.product_id = p.id
                WHERE p.is_deleted = 0 AND pi.is_deleted = 0
                  AND TRIM(pi.image_url) <> ''
                UNION ALL
                SELECT p.id AS product_id, -p.id AS image_id, p.thumbnail_url AS image_url,
                       p.status AS product_status, p.name AS product_name, 1 AS is_thumbnail
                FROM products p
                WHERE p.is_deleted = 0
                  AND p.thumbnail_url IS NOT NULL AND TRIM(p.thumbnail_url) <> ''
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
        key = (row["product_id"], url)
        if key not in seen:
            seen.add(key)
            result.append(row)
    return result


def compute_sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

