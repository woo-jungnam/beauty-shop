"""JSONL: {image_path, product_id}; null ID indicates a product outside the catalog."""
import argparse
import json
from pathlib import Path
import time

import numpy as np
from app.core.config import getSettings
from app.image_search.service import ImageSearchService


def evaluate(manifest):
    settings = getSettings()
    settings.image_search_enabled = True
    service = ImageSearchService(settings)
    service.load_model()
    service.reload_if_changed()
    hits1, hits5, known, outside, total = 0, 0, 0, 0, 0
    latencies = []
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line.strip(): continue
        record = json.loads(line)
        path = (manifest.parent / record["image_path"]).resolve()
        raw = path.read_bytes()
        if len(raw) > settings.image_search_max_bytes:
            raise ValueError("Evaluation image exceeds upload limit")
        started = time.perf_counter()
        ids = [item["product_id"] for item in service.search(raw, 5)["items"]]
        latencies.append((time.perf_counter() - started) * 1000)
        total += 1
        if record.get("product_id") is None:
            outside += 1
            continue
        known += 1
        hits1 += bool(ids and ids[0] == record["product_id"])
        hits5 += record["product_id"] in ids
    if not total: raise ValueError("Empty evaluation manifest")
    return {"queries": total, "known_queries": known, "outside_catalog_queries": outside,
            "accuracy_at_1": hits1 / known if known else None,
            "recall_at_5": hits5 / known if known else None,
            "latency_p95_ms": float(np.percentile(latencies, 95)),
            "outside_catalog_note": "No calibrated rejection threshold: candidates are always returned when the index is available."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    print(json.dumps(evaluate(args.manifest), indent=2))
