"""Read-only full rebuild: python -m scripts.build_image_index."""
import json
import logging
import time

from app.core.config import getSettings
from app.image_search.catalog import catalog_images, fingerprint, read_catalog_image, is_upload_reference
from app.image_search.service import ImageSearchService, decode_image, publish_index, CATALOG_FORMATS

logger = logging.getLogger("image_index")


def build(settings):
    watch = time.perf_counter()
    rows = catalog_images(settings)
    eligible = [row for row in rows if is_upload_reference(settings, row["image_url"])]
    if not eligible:
        logger.error("No uploads-volume references: %d catalog images, %d excluded sources", len(rows), len(rows) - len(eligible))
        raise RuntimeError("No eligible catalog images; previous index was preserved")
    service = ImageSearchService(settings)
    service.load_model()
    vectors, ids, image_ids, hashes = [], [], [], []
    batch, pending = [], []
    failed = 0
    seen = set()

    def flush():
        if not batch:
            return
        try:
            encoded = service.encode_images(batch)
            if len(encoded) != len(pending):
                raise ValueError("Encoder batch size mismatch")
            vectors.extend(encoded)
            for row, digest in pending:
                ids.append(row["product_id"])
                image_ids.append(row["image_id"])
                hashes.append(digest)
        finally:
            for image in batch:
                image.close()
            batch.clear()
            pending.clear()

    for row in eligible:
        try:
            raw = read_catalog_image(settings, row["image_url"])
            digest = fingerprint(raw)
            key = (row["product_id"], digest)
            if key in seen:
                continue
            image = decode_image(raw, settings.image_search_max_pixels, CATALOG_FORMATS)
            seen.add(key)
        except Exception:
            failed += 1
            logger.warning("Cannot read catalog image_id=%s product_id=%s", row["image_id"], row["product_id"])
            continue
        batch.append(image)
        pending.append((row, digest))
        if len(batch) >= settings.image_search_batch_size:
            flush()
    flush()
    if not vectors:
        raise RuntimeError("No valid catalog images; previous index was preserved")
    metadata = publish_index(service, vectors, ids, image_ids, hashes)
    return {**metadata, "catalog_images": len(rows), "indexed_images": len(ids),
            "eligible_upload_references": len(eligible),
            "excluded_external_sources": len(rows) - len(eligible),
            "failed_images": failed, "products": len(set(ids)),
            "elapsed_seconds": round(time.perf_counter() - watch, 3)}


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    try:
        print(json.dumps(build(getSettings()), ensure_ascii=False, indent=2))
    except Exception as exc:
        # Exception messages from DB/HTTP can contain credentials or media URLs.
        logger.error("Build failed (%s); previous index preserved", type(exc).__name__)
        raise SystemExit(1)
