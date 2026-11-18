"""Internal HTTP inference service for the bundled Vietnamese review model."""

import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path

import joblib


def load_model():
    directory = Path(__file__).resolve().parent / "models"
    metadata = json.loads((directory / "model_metadata.json").read_text(encoding="utf-8"))
    artifact = directory / "best_model.joblib"
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    if digest != metadata["model_sha256"]:
        raise ValueError("Review sentiment model checksum mismatch")

    model = joblib.load(artifact)
    if list(model.classes_) != [0, 1] or metadata["labels"] != {"0": "Positive", "1": "Negative"}:
        raise ValueError("Unexpected sentiment classes")
    return model, digest


class Handler(BaseHTTPRequestHandler):
    def send_json(self, status, payload):
        body = json.dumps(payload, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "ready", "modelVersion": VERSION})
        else:
            self.send_json(404, {"error": "Unknown endpoint"})

    def do_POST(self):
        if self.path != "/predict":
            self.send_json(404, {"error": "Unknown endpoint"})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 512_000:
                raise ValueError()
            texts = json.loads(self.rfile.read(size))["texts"]
            if not isinstance(texts, list) or not 1 <= len(texts) <= 20:
                raise ValueError()
            if any(not isinstance(text, str) or len(text) > 2000 for text in texts):
                raise ValueError()
        except (ValueError, TypeError, KeyError):
            self.send_json(400, {"error": "Expected 1 to 20 review texts of up to 2000 characters"})
            return

        scores = MODEL.decision_function(texts)
        self.send_json(200, {
            "modelVersion": VERSION,
            "results": [
                {"label": "NEGATIVE" if score > 0 else "POSITIVE", "score": float(score)}
                for score in scores
            ],
        })


if __name__ == "__main__":
    MODEL, VERSION = load_model()
    ThreadingHTTPServer(("0.0.0.0", 8002), Handler).serve_forever()
