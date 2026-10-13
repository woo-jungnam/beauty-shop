import os
import sys
import io
import json
import math
import shutil
from pathlib import Path
import torch
from torch.utils.data import DataLoader, Dataset
from sentence_transformers import SentenceTransformer, losses

if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
OUTPUT_DIR = BASE_DIR / "export_model" / "my_cosmetics_embedding"
AGENT_DIR = BASE_DIR.parent / "product-agent" / "my_cosmetics_embedding"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

class TripletDataset(Dataset):
    def __init__(self, triplets):
        self.triplets = triplets

    def __len__(self):
        return len(self.triplets)

    def __getitem__(self, idx):
        item = self.triplets[idx]
        return item["anchor"], item["positive"], item["negative"]

def collate_fn(batch, model):
    anchors = [x[0] for x in batch]
    positives = [x[1] for x in batch]
    negatives = [x[2] for x in batch]
    
    # Preprocess tokens for SentenceTransformer
    tokenized = [
        model.preprocess(anchors),
        model.preprocess(positives),
        model.preprocess(negatives)
    ]
    return tokenized

def evaluate_triplets(model, val_data):
    correct = 0
    margins = []
    for item in val_data:
        a_vec = model.encode(item["anchor"], normalize_embeddings=True)
        p_vec = model.encode(item["positive"], normalize_embeddings=True)
        n_vec = model.encode(item["negative"], normalize_embeddings=True)
        sim_pos = float(torch.tensor(a_vec) @ torch.tensor(p_vec))
        sim_neg = float(torch.tensor(a_vec) @ torch.tensor(n_vec))
        if sim_pos > sim_neg:
            correct += 1
        margins.append(sim_pos - sim_neg)
    acc = (correct / len(val_data)) * 100
    avg_m = sum(margins) / len(margins)
    return acc, avg_m

def main():
    print("=" * 70)
    print("BẮT ĐẦU FINE-TUNING EMBEDDING BẰNG THUẦN PYTORCH (KHÔNG CẦN DATASETS/PYARROW)")
    print("=" * 70)

    with open(DATA_DIR / "train_triplets.json", "r", encoding="utf-8") as f:
        train_data = json.load(f)
    with open(DATA_DIR / "val_triplets.json", "r", encoding="utf-8") as f:
        val_data = json.load(f)

    print(f"Số mẫu huấn luyện: {len(train_data)} | Số mẫu kiểm chuẩn: {len(val_data)}")

    base_model_name = "bkai-foundation-models/vietnamese-bi-encoder"
    print(f"Đang tải mô hình nền: {base_model_name}...")
    model = SentenceTransformer(base_model_name)

    print("\n--- ĐÁNH GIÁ BASELINE BAN ĐẦU ---")
    base_acc, base_m = evaluate_triplets(model, val_data)
    print(f"-> Độ chính xác Baseline: {base_acc:.2f}% | Margin: {base_m:.4f}")

    dataset = TripletDataset(train_data)
    dataloader = DataLoader(
        dataset,
        batch_size=16,
        shuffle=True,
        collate_fn=lambda b: collate_fn(b, model)
    )

    loss_fn = losses.MultipleNegativesRankingLoss(model)
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-5, weight_decay=0.01)

    epochs = 3
    print(f"\nBắt đầu huấn luyện ({epochs} Epochs, {len(dataloader)} bước/epoch)...")
    model.train()
    best_acc = base_acc

    for epoch in range(1, epochs + 1):
        total_loss = 0.0
        for step, batch_features in enumerate(dataloader, 1):
            optimizer.zero_grad()
            loss = loss_fn(batch_features, labels=None)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            total_loss += loss.item()
        
        avg_loss = total_loss / len(dataloader)
        val_acc, val_m = evaluate_triplets(model, val_data)
        print(f"Epoch {epoch}/{epochs} - Loss: {avg_loss:.4f} - Val Acc: {val_acc:.2f}% - Margin: {val_m:.4f}")

    print("\nĐang lưu mô hình đã fine-tune...")
    model.save(str(OUTPUT_DIR))

    print(f"Đang sao chép mô hình sang chatbot: {AGENT_DIR}...")
    if AGENT_DIR.exists():
        shutil.rmtree(AGENT_DIR)
    shutil.copytree(OUTPUT_DIR, AGENT_DIR)
    print("-> ĐÃ ĐỒNG BỘ VÀO PRODUCT-AGENT THÀNH CÔNG!")
    print("=" * 70)

if __name__ == "__main__":
    main()
