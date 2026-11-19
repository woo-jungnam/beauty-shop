"""Fine-tune the Vietnamese embedding encoder on CPU and export a standalone checkpoint."""
import argparse
import json
from pathlib import Path

import torch
from sentence_transformers import SentenceTransformer, models
from torch.nn import functional as F
from torch.utils.data import DataLoader

TRAINING = Path(__file__).resolve().parents[1]
BASE_MODEL = "bkai-foundation-models/vietnamese-bi-encoder"
FIELDS = ("anchor", "positive", "negative")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-model", default=BASE_MODEL)
    parser.add_argument("--from-cross-encoder", action="store_true",
                        help="Use the backbone of a local classification checkpoint with mean pooling")
    parser.add_argument("--output", type=Path, default=TRAINING / "export_model/my_cosmetics_embedding")
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    if args.epochs < 1 or args.threads < 1:
        parser.error("epochs and threads must be positive")
    if args.output.exists() and (not args.output.is_dir() or any(args.output.iterdir())):
        parser.error("output must be a new or empty directory")

    torch.manual_seed(42)
    torch.set_num_threads(args.threads)
    rows = [
        {field: row[field] for field in FIELDS}
        for row in json.loads((TRAINING / "data/train_triplets.json").read_bytes())
    ]
    if args.from_cross_encoder:
        encoder = models.Transformer(args.base_model, max_seq_length=128,
                                     model_args={"local_files_only": True},
                                     tokenizer_args={"local_files_only": True})
        pooling = models.Pooling(encoder.get_word_embedding_dimension(), pooling_mode="mean")
        model = SentenceTransformer(modules=[encoder, pooling], device="cpu")
    else:
        model = SentenceTransformer(args.base_model, device="cpu")
    model.max_seq_length = min(model.max_seq_length, 128)
    model.requires_grad_(False)
    layers = model[0].auto_model.encoder.layer
    for layer in layers[-2:]:
        layer.requires_grad_(True)
    trainable = [parameter for parameter in model.parameters() if parameter.requires_grad]
    optimizer = torch.optim.AdamW(trainable, lr=3e-5)
    loader = DataLoader(rows, batch_size=4, shuffle=True, generator=torch.Generator().manual_seed(42))
    print(f"Train: {len(rows)} triplets, {len(layers)} encoder blocks, {len(trainable)} trainable tensors", flush=True)

    model.train()
    losses = []
    for epoch in range(1, args.epochs + 1):
        total_loss = 0.0
        for step, batch in enumerate(loader, 1):
            features = model.tokenize([text for field in FIELDS for text in batch[field]])
            anchor, positive, negative = model(features)["sentence_embedding"].chunk(3)
            loss = F.relu(0.2 - F.cosine_similarity(anchor, positive) + F.cosine_similarity(anchor, negative)).mean()
            if not torch.isfinite(loss):
                raise RuntimeError("Non-finite training loss")
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(trainable, 1.0)
            optimizer.step()
            total_loss += loss.item()
            if step % 10 == 0:
                print(f"Epoch {epoch}/{args.epochs}, step {step}/{len(loader)}", flush=True)
        losses.append(total_loss / len(loader))
        print(f"Epoch {epoch}: loss={losses[-1]:.5f}", flush=True)

    args.output.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(args.output), safe_serialization=True)
    metadata = {
        "base_model": args.base_model,
        "train_triplets": len(rows), "seed": 42, "batch_size": 4,
        "epochs": args.epochs, "sequence_length": model.max_seq_length,
        "trainable_encoder_blocks": 2, "loss": "cosine_triplet_margin", "margin": 0.2,
        "learning_rate": 3e-5, "epoch_losses": losses,
        "from_cross_encoder": args.from_cross_encoder,
    }
    provenance = Path(args.base_model) / "source.txt"
    if provenance.is_file():
        metadata["base_model_source"] = provenance.read_text(encoding="utf-8").strip()
    (args.output / "training_run.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(f"Saved checkpoint: {args.output}", flush=True)


if __name__ == "__main__":
    main()
