"""Compare embeddings on the same labeled triplets without substituting failed vectors."""
import argparse
import hashlib
import json
import sys
import time
from pathlib import Path

import httpx
import numpy as np

TRAINING = Path(__file__).resolve().parents[1]
AGENT = TRAINING.parent / "product-agent"
sys.path.insert(0, str(AGENT))

from app.core.config import settings

FIELDS = ("anchor", "positive", "negative")


def gemini_vectors(texts, batch_size=16, cache_path=None):
    model = settings.embedding_model_name.removeprefix("models/")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:batchEmbedContents"
    if not settings.llm_api_key:
        raise RuntimeError("Gemini API key is missing")
    cache = json.loads(cache_path.read_bytes()) if cache_path and cache_path.is_file() else {}
    keys = {text: hashlib.sha256(f"{model}:{settings.vector_dimension}:{text}".encode()).hexdigest()
            for text in texts}
    pending = [text for text in dict.fromkeys(texts) if keys[text] not in cache]
    with httpx.Client(timeout=35, headers={"x-goog-api-key": settings.llm_api_key}) as client:
        for offset in range(0, len(pending), batch_size):
            batch = pending[offset:offset + batch_size]
            payload = {"requests": [
                {"model": f"models/{model}", "content": {"parts": [{"text": text}]},
                 "outputDimensionality": settings.vector_dimension}
                for text in batch
            ]}
            for attempt in range(8 if cache_path else 5):
                response = client.post(url, json=payload)
                if response.status_code not in (429, 502, 503, 504) or attempt == (7 if cache_path else 4):
                    break
                retry_after = response.headers.get("Retry-After", "")
                delay = int(retry_after) if retry_after.isdigit() else (15 if response.status_code == 429 else 1)
                time.sleep(min(60, max(delay, 2 ** attempt)))
            if response.status_code != 200:
                raise RuntimeError(f"Gemini embedding returned HTTP {response.status_code} at offset {offset}")
            values = [item["values"] for item in response.json()["embeddings"]]
            if len(values) != len(batch):
                raise ValueError("Gemini embedding count differs from input count")
            cache.update((keys[text], vector) for text, vector in zip(batch, values))
            if cache_path:
                temporary = cache_path.with_suffix(".tmp")
                temporary.write_text(json.dumps(cache), encoding="utf-8")
                temporary.replace(cache_path)
    return [cache[keys[text]] for text in texts]


def local_vectors(texts, checkpoint):
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(str(checkpoint), device="cpu", local_files_only=True)
    model.max_seq_length = min(model.max_seq_length, 128)
    return model.encode(texts, batch_size=16, show_progress_bar=False)


def score(rows, vectors, pool):
    positive_by_query = {}
    for row in rows:
        positive_by_query.setdefault(row["anchor"], set()).add(row["positive"])
    ranks = []
    for anchor, expected in positive_by_query.items():
        similarities = np.stack([vectors[text] for text in pool]) @ vectors[anchor]
        # Treat equal scores conservatively to avoid favoring catalog order.
        best = max(similarities[i] for i, text in enumerate(pool) if text in expected)
        rank = 1 + sum(score >= best for score, text in zip(similarities, pool) if text not in expected)
        ranks.append(rank)
    margins = np.asarray([
        vectors[row["anchor"]] @ vectors[row["positive"]] - vectors[row["anchor"]] @ vectors[row["negative"]]
        for row in rows
    ])
    ranks = np.asarray(ranks)
    return {
        "triplets": len(rows), "queries": len(ranks), "candidates": len(pool),
        "triplet_accuracy": float(np.mean(margins > 0)),
        "mean_margin": float(np.mean(margins)),
        "hit_at_1": float(np.mean(ranks == 1)), "hit_at_3": float(np.mean(ranks <= 3)),
        "mrr": float(np.mean(1 / ranks)),
    }


def evaluate(embed, texts, rows, holdout, pool, dimension=None):
    start = time.perf_counter()
    matrix = np.asarray(embed(texts), dtype=np.float32)
    elapsed = time.perf_counter() - start
    if matrix.ndim != 2 or matrix.shape[0] != len(texts) or not np.isfinite(matrix).all():
        raise ValueError("Invalid embedding matrix")
    if dimension is not None and matrix.shape[1] != dimension:
        raise ValueError("Unexpected embedding dimension")
    norms = np.linalg.norm(matrix, axis=1)
    if not np.all(norms > 0):
        raise ValueError("Zero embedding")
    vectors = dict(zip(texts, matrix / norms[:, None]))
    return {"embedding_seconds": round(elapsed, 3), "dimension": matrix.shape[1],
            "all_validation": score(rows, vectors, pool),
            "unseen_anchor_subset": score(holdout, vectors, pool)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, default=TRAINING / "export_model/my_cosmetics_embedding")
    parser.add_argument("--base-model", help="Optional cached Sentence Transformers baseline")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    validation = TRAINING / "data/val_triplets.json"
    dataset = validation.read_bytes()
    rows = json.loads(dataset)
    train = json.loads((TRAINING / "data/train_triplets.json").read_bytes())
    seen = {row["anchor"] for row in train}
    holdout = [row for row in rows if row["anchor"] not in seen]
    pool = sorted({row[k] for row in rows for k in ("positive", "negative")})
    texts = sorted({row[k] for row in rows for k in FIELDS})
    results = {"validation_sha256": hashlib.sha256(dataset).hexdigest(),
               "unique_texts": len(texts), "models": {}}
    models = results["models"]
    models[settings.embedding_model_name] = evaluate(
        gemini_vectors, texts, rows, holdout, pool, settings.vector_dimension
    )
    if args.base_model:
        models[args.base_model] = evaluate(
            lambda data: local_vectors(data, args.base_model), texts, rows, holdout, pool
        )
    checkpoint = args.checkpoint.expanduser().resolve()
    if checkpoint.is_dir() and any(
        p.is_file() for pattern in ("*.safetensors", "pytorch_model*.bin")
        for p in checkpoint.rglob(pattern)
    ):
        models["fine_tuned"] = evaluate(lambda data: local_vectors(data, checkpoint), texts, rows, holdout, pool)
    else:
        models["fine_tuned"] = {"status": "missing_checkpoint"}
    report = json.dumps(results, indent=2, ensure_ascii=False)
    if args.output:
        args.output.write_text(report + "\n", encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
