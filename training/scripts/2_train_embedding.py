#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Script: 2_train_embedding.py
Mục đích: Huấn luyện / Fine-tune mô hình Embedding chuyên biệt cho Mỹ phẩm & Spa
sử dụng thư viện Sentence-Transformers với hàm mất mát MultipleNegativesRankingLoss.
Chạy được trên cả GPU (CUDA) và CPU.
"""

import os
import sys
import io
import json
import math
import argparse
from pathlib import Path
import torch
from torch.utils.data import DataLoader
from sentence_transformers import (
    SentenceTransformer,
    InputExample,
    losses,
    evaluation
)

# Đảm bảo in tiếng Việt chuẩn trên mọi console Windows
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "buffer"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")


CURRENT_DIR = Path(__file__).resolve().parent
BASE_DIR = CURRENT_DIR.parent
DATA_DIR = BASE_DIR / "data"
OUTPUT_DIR = BASE_DIR / "export_model" / "my_cosmetics_embedding"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def parse_args():
    parser = argparse.ArgumentParser(description="Huấn luyện Fine-tuning mô hình Embedding cho Mỹ phẩm")
    parser.add_argument(
        "--base_model",
        type=str,
        default="bkai-foundation-models/vietnamese-bi-encoder",
        help="Tên mô hình nền trên Hugging Face (ví dụ: bkai-foundation-models/vietnamese-bi-encoder hoặc intfloat/multilingual-e5-base)"
    )
    parser.add_argument("--epochs", type=int, default=3, help="Số epochs huấn luyện (mặc định: 3)")
    parser.add_argument("--batch_size", type=int, default=16, help="Kích thước batch (mặc định: 16)")
    parser.add_argument("--lr", type=float, default=2e-5, help="Tốc độ học Learning Rate (mặc định: 2e-5)")
    parser.add_argument("--warmup_ratio", type=float, default=0.1, help="Tỷ lệ Warmup steps (mặc định: 0.1)")
    return parser.parse_args()

def load_triplets(file_path: Path):
    """Đọc dữ liệu từ file JSON"""
    if not file_path.exists():
        raise FileNotFoundError(f"Không tìm thấy file dữ liệu tại: {file_path}. Hãy chạy 1_generate_dataset.py trước!")
    with open(file_path, "r", encoding="utf-8") as f:
        return json.load(f)

def main():
    args = parse_args()
    print("=" * 70)
    print("BẮT ĐẦU QUY TRÌNH HUẤN LUYỆN FINE-TUNING EMBEDDING MODEL")
    print(f"Mô hình nền: {args.base_model}")
    print(f"Thiết bị tính toán: {'GPU CUDA' if torch.cuda.is_available() else 'CPU'}")
    print(f"Epochs: {args.epochs} | Batch Size: {args.batch_size} | LR: {args.lr}")
    print("=" * 70)

    # 1. Nạp dữ liệu
    train_file = DATA_DIR / "train_triplets.json"
    val_file = DATA_DIR / "val_triplets.json"

    train_data = load_triplets(train_file)
    val_data = load_triplets(val_file)

    print(f"Số lượng mẫu huấn luyện (Train): {len(train_data)}")
    print(f"Số lượng mẫu kiểm chuẩn (Validation): {len(val_data)}")

    # 2. Chuẩn bị tập InputExample
    # MultipleNegativesRankingLoss hỗ trợ cả cặp (anchor, positive) hoặc bộ ba (anchor, positive, negative)
    train_examples = []
    for item in train_data:
        anchor = item.get("anchor", "").strip()
        pos = item.get("positive", "").strip()
        neg = item.get("negative", "").strip()
        if anchor and pos:
            if neg:
                train_examples.append(InputExample(texts=[anchor, pos, neg]))
            else:
                train_examples.append(InputExample(texts=[anchor, pos]))

    train_dataloader = DataLoader(train_examples, shuffle=True, batch_size=args.batch_size)

    # 3. Chuẩn bị bộ đánh giá trên tập Validation
    val_anchors = [item["anchor"] for item in val_data if item.get("anchor") and item.get("positive") and item.get("negative")]
    val_positives = [item["positive"] for item in val_data if item.get("anchor") and item.get("positive") and item.get("negative")]
    val_negatives = [item["negative"] for item in val_data if item.get("anchor") and item.get("positive") and item.get("negative")]

    evaluator = evaluation.TripletEvaluator(
        anchors=val_anchors,
        positives=val_positives,
        negatives=val_negatives,
        name="cosmetics_val_evaluator",
        show_progress_bar=True
    )

    # 4. Tải mô hình nền
    print(f"\nĐang tải mô hình nền: {args.base_model}...")
    model = SentenceTransformer(args.base_model)

    def extract_score(result):
        if isinstance(result, (int, float)):
            return float(result)
        if isinstance(result, dict):
            for k in ["cosmetics_val_evaluator_cosine_accuracy", "cosmetics_val_evaluator_accuracy", "accuracy"]:
                if k in result:
                    return float(result[k])
            for v in result.values():
                if isinstance(v, (int, float)):
                    return float(v)
        return 0.0

    # Đánh giá độ chính xác của mô hình gốc (Zero-shot Baseline)
    print("\n--- ĐÁNH GIÁ ĐỘ CHÍNH XÁC CỦA MÔ HÌNH GỐC (BEFORE FINE-TUNING) ---")
    initial_res = evaluator(model)
    initial_score = extract_score(initial_res)
    print(f"-> Độ chính xác Baseline ban đầu: {initial_score * 100:.2f}%\n")

    # 5. Cấu hình Loss và Luyện tập
    train_loss = losses.MultipleNegativesRankingLoss(model)

    total_steps = len(train_dataloader) * args.epochs
    warmup_steps = math.ceil(total_steps * args.warmup_ratio)

    print(f"Bắt đầu huấn luyện: Tổng số bước (Total steps) = {total_steps} (Warmup: {warmup_steps} steps)...")

    model.fit(
        train_objectives=[(train_dataloader, train_loss)],
        evaluator=evaluator,
        epochs=args.epochs,
        evaluation_steps=max(10, len(train_dataloader) // 2),
        warmup_steps=warmup_steps,
        output_path=str(OUTPUT_DIR),
        save_best_model=True,
        show_progress_bar=True
    )

    # 6. Đánh giá mô hình sau huấn luyện
    print("\n--- ĐÁNH GIÁ ĐỘ CHÍNH XÁC MÔ HÌNH SAU KHI FINE-TUNE (AFTER) ---")
    best_model = SentenceTransformer(str(OUTPUT_DIR))
    final_res = evaluator(best_model)
    final_score = extract_score(final_res)
    print(f"-> Độ chính xác của Mô hình sau Fine-tuning: {final_score * 100:.2f}%")
    print(f"-> Mức độ cải thiện (Improvement): +{(final_score - initial_score) * 100:.2f}%")

    # 7. Tự động sao chép sang chatbot product-agent
    import shutil
    agent_model_dir = BASE_DIR.parent / "product-agent" / "my_cosmetics_embedding"
    print(f"\nĐang đồng bộ mô hình đã fine-tune sang thư mục chatbot: {agent_model_dir}...")
    if agent_model_dir.exists():
        shutil.rmtree(agent_model_dir)
    shutil.copytree(OUTPUT_DIR, agent_model_dir)
    print("-> ĐÃ ĐỒNG BỘ MÔ HÌNH FINE-TUNED VÀO CHATBOT THÀNH CÔNG!")

    print("\n" + "=" * 70)
    print(f"HOÀN TẤT HUẤN LUYỆN! Mô hình tối ưu đã được lưu tại:")
    print(f"1. {OUTPUT_DIR}")
    print(f"2. {agent_model_dir}")
    print("=" * 70)

if __name__ == "__main__":
    main()
