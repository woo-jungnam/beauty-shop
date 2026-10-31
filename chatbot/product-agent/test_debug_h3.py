import sys
import io
import time
import traceback
import httpx

# Fix Windows console UTF-8 output
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "buffer"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

url = "http://127.0.0.1:18000/chat"
payload = {
    "message": "Cơ chế kem chống nắng vật lý là gì, tư vấn cho mình 1 loại cho da dầu mụn không nâng tone với?",
    "session_id": "test_h3_debug"
}

print(f"Gửi request tới {url}...")
t0 = time.time()
try:
    with httpx.Client(timeout=120) as client:
        resp = client.post(url, json=payload)
        duration = time.time() - t0
        print(f"Status Code: {resp.status_code} (took {duration:.2f}s)")
        print("Response JSON:")
        try:
            data = resp.json()
            print("Intent:", data.get("intent"))
            print("Answer:", data.get("answer") or data.get("message"))
            print("Products count:", len(data.get("products", [])))
            for p in data.get("products", []):
                print(f" - {p.get('name')} | {p.get('price'):,.0f} VND")
            print("Sources count:", len(data.get("sources", [])))
            print("Stage Latencies:", data.get("stage_latencies_ms"))
        except Exception:
            print("Text:", resp.text)
except Exception as e:
    duration = time.time() - t0
    print(f"LỖI sau {duration:.2f}s: {e}")
    traceback.print_exc()
