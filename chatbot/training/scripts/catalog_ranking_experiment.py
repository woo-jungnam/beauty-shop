"""Train and compare catalog embedding rankers on a frozen, brand-disjoint split."""
import argparse
import hashlib
import json
import random
import re
from pathlib import Path

import numpy as np
from rank_bm25 import BM25Okapi

from benchmark_google_vs_finetune import AGENT, TRAINING, gemini_vectors, local_vectors

ASPECTS = {
    "usage": ("Cách dùng", "Dùng {name} như thế nào?"),
    "ingredients": ("Thành phần", "{name} có những hoạt chất nào?"),
    "benefits": ("Công dụng", "{name} có tác dụng gì?"),
}
SEED = 42


def tokenize(text):
    return re.findall(r"[^\W_]+", text.casefold())


def load_catalog(paths):
    products = {}
    seen_names = set()
    for path in paths:
        for row in json.loads(path.read_bytes()):
            name = " ".join(tokenize(row["name"]))
            if name in seen_names or row["id"] in products or row.get("target_type") == "SERVICE":
                continue
            seen_names.add(name)
            products[row["id"]] = row
    return products


def passages(products):
    result = []
    for row in products:
        for field, (label, _) in ASPECTS.items():
            content = row.get(field)
            if isinstance(content, list):
                content = "; ".join(content)
            if content and content.strip():
                result.append({"id": f"{row['id']}:{field}", "product": row["id"], "field": field,
                               "text": f"{row['name']} — {label}: {content.strip()}"})
    return result


def queries(documents, products):
    return [{"query": ASPECTS[doc["field"]][1].format(name=products[doc["product"]]["name"]),
             "target": doc["id"], "product": doc["product"], "field": doc["field"]}
            for doc in documents]


def prepare(paths, output):
    rng = random.Random(SEED)
    products = load_catalog(paths)
    old_rows = json.loads((TRAINING / "data/train_triplets.json").read_bytes())
    old_ids = {row["product_id"] for row in old_rows}
    old_brands = {tokenize(row["brand"])[0] for row in old_rows if row.get("brand") and tokenize(row["brand"])}
    by_brand = {}
    for row in products.values():
        brand = " ".join(tokenize(row.get("brand", "")))
        if brand and len(passages([row])) == len(ASPECTS):
            by_brand.setdefault(brand, []).append(row)

    available = [brand for brand, rows in sorted(by_brand.items()) if len(rows) >= 4
                 and not any(row["id"] in old_ids for row in rows)
                 and brand.split()[0] not in old_brands]
    rng.shuffle(available)
    test_brands, val_brands = available[:5], available[5:8]
    if len(val_brands) != 3:
        raise ValueError("Need eight eligible brands")
    test = [row for brand in test_brands for row in rng.sample(by_brand[brand], 4)]
    val = [row for brand in val_brands for row in rng.sample(by_brand[brand], 3)]
    held_brands = set(test_brands + val_brands)
    eligible = [row for brand, rows in sorted(by_brand.items()) if brand not in held_brands for row in rows]
    train = rng.sample(eligible, min(80, len(eligible)))
    test_distractors = rng.sample(eligible, min(20, len(eligible)))
    train_docs = passages(train)
    lexical = BM25Okapi([tokenize(doc["text"]) for doc in train_docs])
    examples = []
    for item in queries(train_docs, products):
        target = item["target"]
        indices = np.argsort(-lexical.get_scores(tokenize(item["query"])), kind="stable")
        siblings = [i for i in indices if train_docs[i]["product"] == item["product"] and train_docs[i]["id"] != target]
        neighbors = [i for i in indices if train_docs[i]["product"] != item["product"] and train_docs[i]["field"] == item["field"]]
        positive = next(doc["text"] for doc in train_docs if doc["id"] == target)
        negatives = [train_docs[i]["text"] for i in siblings[:1] + neighbors[:2]]
        examples.append([item["query"], positive, *negatives])
    snapshot = {"seed": SEED, "catalog_hashes": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in paths},
                "train_brands": sorted({" ".join(tokenize(row["brand"])) for row in train}),
                "val_brands": val_brands, "test_brands": test_brands, "train": examples,
                "validation": {"queries": queries(passages(val), products), "docs": passages(val + train[:12])},
                "test": {"queries": queries(passages(test), products), "docs": passages(test + test_distractors)}}
    write_json(output, snapshot)
    print(f"Prepared {len(examples)} training lists, {len(test)*3} test queries", flush=True)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def ranking(scores):
    return np.argsort(-scores, axis=1, kind="stable")


def metrics(order, dataset):
    docs = dataset["docs"]
    ids = {doc["id"]: i for i, doc in enumerate(docs)}
    ranks, product_ranks = [], []
    cases = []
    for indices, query in zip(order, dataset["queries"]):
        rank = int(np.flatnonzero(indices == ids[query["target"]])[0]) + 1
        product_rank = next(j for j, i in enumerate(indices, 1) if docs[i]["product"] == query["product"])
        ranks.append(rank)
        product_ranks.append(product_rank)
        cases.append({"query": query["query"], "target": query["target"], "rank": rank,
                      "top3": [docs[i]["id"] for i in indices[:3]]})
    r = np.asarray(ranks)
    return {"queries": len(r), "hit_at_1": float(np.mean(r == 1)), "hit_at_3": float(np.mean(r <= 3)),
            "product_hit_at_3": float(np.mean(np.asarray(product_ranks) <= 3)), "mrr": float(np.mean(1 / r)),
            "cases": cases}


def dense(embed, dataset):
    inputs = [doc["text"] for doc in dataset["docs"]] + [query["query"] for query in dataset["queries"]]
    vectors = np.asarray(embed(inputs), dtype=np.float32)
    if vectors.ndim != 2 or len(vectors) != len(inputs) or not np.isfinite(vectors).all():
        raise ValueError("Invalid embedding matrix")
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    if np.any(norms == 0):
        raise ValueError("Zero vector")
    vectors = vectors / norms
    n = len(dataset["docs"])
    return ranking(vectors[n:] @ vectors[:n].T)


def train(snapshot, base, output, epochs):
    import torch
    from torch.nn import functional as F
    from sentence_transformers import SentenceTransformer, models

    if output.exists():
        raise ValueError("Choose a new checkpoint directory")
    torch.manual_seed(SEED)
    torch.set_num_threads(4)
    encoder = models.Transformer(str(base), max_seq_length=128,
                                 model_args={"local_files_only": True}, tokenizer_args={"local_files_only": True})
    model = SentenceTransformer(modules=[encoder, models.Pooling(encoder.get_word_embedding_dimension())], device="cpu")
    model.requires_grad_(False)
    for layer in encoder.auto_model.encoder.layer[-2:]:
        layer.requires_grad_(True)
    optimizer = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=2e-5)
    history = []
    best_score = (-1.0, -1.0)
    stale = 0
    for epoch in range(epochs + 1):
        if epoch:
            model.train()
            rows = snapshot["train"][:]
            random.Random(SEED + epoch).shuffle(rows)
            total_loss = 0.0
            for step in range(0, len(rows), 4):
                batch = rows[step:step + 4]
                features = model.tokenize([text for example in batch for text in example])
                vectors = F.normalize(model(features)["sentence_embedding"], dim=-1).view(len(batch), 5, -1)
                logits = (vectors[:, :1] * vectors[:, 1:]).sum(-1) / 0.08
                loss = F.cross_entropy(logits, torch.zeros(len(batch), dtype=torch.long))
                optimizer.zero_grad(set_to_none=True)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step()
                total_loss += loss.item()
        validation = metrics(dense(lambda t: model.encode(t, batch_size=16), snapshot["validation"]), snapshot["validation"])
        validation.pop("cases")
        validation["epoch"] = epoch
        if epoch:
            validation["loss"] = total_loss / ((len(rows) + 3) // 4)
        history.append(validation)
        score = (validation["hit_at_3"], validation["mrr"])
        if score > best_score:
            best_score = score
            model.save_pretrained(str(output), safe_serialization=True)
            best_epoch = epoch
            stale = 0
        else:
            stale += 1
        print(json.dumps(validation), flush=True)
        if stale >= 2:
            break
    write_json(output / "ranking_training.json", {"selected_epoch": best_epoch, "history": history})


def compare(snapshot, old, new, output, skip_gemini=False):
    import torch
    torch.set_num_threads(4)
    dataset = snapshot["test"]
    bm25 = BM25Okapi([tokenize(doc["text"]) for doc in dataset["docs"]])
    lexical = ranking(np.stack([bm25.get_scores(tokenize(item["query"])) for item in dataset["queries"]]))
    results = {"BM25": metrics(lexical, dataset)}
    lexical_positions = np.argsort(lexical, axis=1)
    models = [("V1", lambda t: local_vectors(t, old)), ("V2", lambda t: local_vectors(t, new))]
    if not skip_gemini:
        models.append(("Gemini", lambda t: gemini_vectors(
            t, batch_size=40, cache_path=output.with_suffix(".gemini-cache.json")
        )))
    for name, embed in models:
        order = dense(embed, dataset)
        positions = np.argsort(order, axis=1)
        results[name] = metrics(order, dataset)
        results[f"BM25 + {name}"] = metrics(ranking(1 / (61 + lexical_positions) + 1 / (61 + positions)), dataset)
        print(name, results[name]["hit_at_3"], flush=True)
        write_json(output, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    sub = parser.add_subparsers(dest="action", required=True)
    prep = sub.add_parser("prepare")
    prep.add_argument("--catalog", type=Path, nargs="+", default=[
        AGENT / "app/data/authentic_catalog_expansion.json", AGENT / "app/data/authentic_catalog_part2.json"])
    training = sub.add_parser("train")
    training.add_argument("--base", type=Path, required=True)
    training.add_argument("--output", type=Path, required=True)
    training.add_argument("--epochs", type=int, default=4)
    evaluation = sub.add_parser("compare")
    evaluation.add_argument("--old", type=Path, required=True)
    evaluation.add_argument("--new", type=Path, required=True)
    evaluation.add_argument("--output", type=Path, required=True)
    evaluation.add_argument("--skip-gemini", action="store_true")
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args.catalog, args.dataset)
        return
    snapshot = json.loads(args.dataset.read_bytes())
    if args.action == "train":
        train(snapshot, args.base, args.output, args.epochs)
    else:
        compare(snapshot, args.old, args.new, args.output, args.skip_gemini)


if __name__ == "__main__":
    main()
