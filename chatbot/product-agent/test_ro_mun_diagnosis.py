import json
import urllib.request
import sys
import io

if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

url = "http://127.0.0.1:18000/chat"
payload = {
    "message": "tư vấn dịch về chăm sóc da mặt bị rổ do nặn mun",
    "session_id": "test_ro_mun_diagnosis"
}
req = urllib.request.Request(
    url,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"}
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print("=== KẾT QUẢ CHATBOT ===")
        print("Intent:", data.get("intent"))
        und = data.get("understanding", {})
        print("Understanding Target Type:", und.get("target_type") or und.get("target"))
        print("Understanding Category:", und.get("category"))
        print("Understanding Constraints:", und.get("constraints"))
        print("Understanding Preferences:", und.get("preferences"))
        print("Understanding Semantic Query:", und.get("semantic_query"))
        print("Understanding BM25 Query:", und.get("bm25_query"))
        print("\nProducts returned:")
        for idx, p in enumerate(data.get("products", []), 1):
            print(f"  {idx}. {p.get('name')} | Type: {p.get('target_type')} | Reason: {p.get('reason_for_recommendation')}")
        print("\nAnswer:\n", data.get("answer") or data.get("message"))
except Exception as e:
    print("Lỗi:", e)
