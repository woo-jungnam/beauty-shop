"""Read-only catalog image extraction and restricted media loading."""
import hashlib
from pathlib import Path
from urllib.parse import unquote, urlsplit

import pymysql


def catalog_images(settings):
    connection = pymysql.connect(
        host=settings.mysql_host, port=settings.mysql_port, user=settings.mysql_user,
        password=settings.mysql_password, database=settings.mysql_database,
        charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10, read_timeout=30,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT p.id AS product_id, pi.id AS image_id, pi.image_url
                FROM products p JOIN product_images pi ON pi.product_id=p.id
                WHERE p.is_deleted=0 AND p.status='ACTIVE' AND pi.is_deleted=0
                  AND TRIM(pi.image_url)<>''
                UNION ALL
                SELECT p.id AS product_id, -p.id AS image_id, p.thumbnail_url AS image_url
                FROM products p WHERE p.is_deleted=0 AND p.status='ACTIVE'
                  AND p.thumbnail_url IS NOT NULL AND TRIM(p.thumbnail_url)<>''
                ORDER BY product_id, image_id DESC
            """)
            rows = cursor.fetchall()
    finally:
        connection.close()
    seen, result = set(), []
    for row in rows:
        row["image_url"] = row["image_url"].strip()
        key = (row["product_id"], row["image_url"])
        if key not in seen:
            seen.add(key)
            result.append(row)
    return result


def origin(url):
    parts = urlsplit(url)
    if parts.scheme not in {"http", "https"} or not parts.hostname or parts.username or parts.password:
        raise ValueError("Invalid media origin")
    return (parts.scheme.lower(), parts.hostname.lower(), parts.port or (443 if parts.scheme == "https" else 80))


def is_placeholder(settings, url):
    excluded = {host.strip().lower() for host in settings.image_search_excluded_hosts.split(",") if host.strip()}
    host = (urlsplit(url).hostname or "").lower().rstrip(".")
    # A config override must never permit Unsplash as a catalog image source.
    return host in excluded or host == "unsplash.com" or host.endswith(".unsplash.com")


def catalog_upload_path(settings, url):
    """Resolve catalog references into the mounted uploads directory; never fetch HTTP."""
    parts = urlsplit(url)
    if is_placeholder(settings, url):
        raise ValueError("Stock/placeholder images cannot identify catalog products")
    allowed = {origin(value.strip()) for value in settings.image_search_allowed_origins.split(",") if value.strip()}
    # Origins only identify aliases of our upload URLs. No network downloads are allowed.
    own_origin = origin(settings.image_search_media_origin)
    trusted = not parts.netloc and not parts.scheme
    if parts.scheme in {"http", "https"}:
        trusted = origin(url) in allowed | {own_origin}
    if not trusted:
        raise ValueError("Catalog image origin is not allowed")
    path = unquote(parts.path)
    if not path.startswith("/uploads/") or "\\" in path:
        raise ValueError("Only files in the uploads volume can be indexed")
    root = Path(settings.image_search_upload_dir).resolve()
    target = (root / path.removeprefix("/uploads/")).resolve()
    if not target.is_relative_to(root):
        raise ValueError("Media path escapes upload directory")
    return target


def is_upload_reference(settings, url):
    try:
        catalog_upload_path(settings, url)
        return True
    except ValueError:
        return False


def read_catalog_image(settings, url):
    target = catalog_upload_path(settings, url)
    # Missing files fail explicitly; there is no fallback to any HTTP image source.
    with target.open("rb") as stream:
        raw = stream.read(settings.image_search_max_bytes + 1)
    if len(raw) > settings.image_search_max_bytes:
        raise ValueError("Catalog image is too large")
    return raw


def fingerprint(raw):
    return hashlib.sha256(raw).hexdigest()
