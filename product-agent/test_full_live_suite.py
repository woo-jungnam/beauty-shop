import urllib.request
import json
import time

BASE_URL = "http://127.0.0.1:8000"

def test_health():
    print("--- 1. KIỂM TRA HEALTH CHECK ---")
    req = urllib.request.Request(f"{BASE_URL}/health")
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print(f"Status: {resp.status} | Body: {data}")
        assert data.get("status") == "ok"

def test_chat_non_stream():
    print("\n--- 2. KIỂM TRA TƯ VẤN SẢN PHẨM (/chat) ---")
    payload = {
        "message": "Da em nhiều dầu và mụn ẩn, tư vấn giúp em kem chống nắng kiềm dầu với ạ",
        "session_id": f"live_session_{int(time.time())}"
    }
    req = urllib.request.Request(
        f"{BASE_URL}/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=45) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        print(f"Status: {resp.status}")
        und = data.get("understanding", {})
        print(f"Target: {und.get('target_type')} | Primary Intent: {und.get('primary_intent')}")
        print(f"Extracted Category: {und.get('category')} | Extracted Entities: {und.get('entities')}")
        bot_answer = data.get('answer') or data.get('message') or ""
        print(f"Bot Answer:\n{bot_answer}\n")
        products = data.get("products", [])
        print(f"Gợi ý {len(products)} sản phẩm:")
        for idx, p in enumerate(products, 1):
            print(f"  {idx}. {p.get('name')} - {p.get('brand')} (Giá: {p.get('price'):,.0f} VND)")

def test_multiturn():
    print("\n--- 3. KIỂM TRA HỘI THOẠI ĐA LƯỢT (MULTI-TURN STATE TRACKING) ---")
    session_id = f"multiturn_live_{int(time.time())}"
    turns = [
        "Da mình thuộc tuýp da khô, mùa đông rất hay bị tróc vảy",
        "Có serum nào cấp ẩm sâu giá dưới 500k không shop?"
    ]
    for idx, msg in enumerate(turns, 1):
        print(f"\n[Turn {idx}]: {msg}")
        payload = {"message": msg, "session_id": session_id}
        req = urllib.request.Request(
            f"{BASE_URL}/chat",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            und = data.get("understanding", {})
            print(f"  -> State - Skin Type: {und.get('skin_type')} | Concerns: {und.get('concerns')}")
            prods = data.get("products", [])
            print(f"  -> Số sản phẩm đề xuất: {len(prods)}")
            for p in prods[:2]:
                print(f"     * {p.get('name')} | {p.get('price'):,.0f} đ")
            answer = data.get("answer") or data.get("message") or ""
            print(f"  -> Trích dẫn phản hồi: {answer[:180]}...")

def test_stream_sse():
    print("\n--- 4. KIỂM TRA STREAMING SSE (/chat/stream) ---")
    payload = {
        "message": "Xin chào, shop có những dịch vụ chăm sóc da nào?",
        "session_id": f"stream_{int(time.time())}"
    }
    req = urllib.request.Request(
        f"{BASE_URL}/chat/stream",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    events = []
    with urllib.request.urlopen(req, timeout=30) as resp:
        for line in resp:
            line_str = line.decode("utf-8").strip()
            if line_str.startswith("data:"):
                raw_data = line_str[5:].strip()
                if raw_data:
                    try:
                        events.append(json.loads(raw_data))
                    except Exception:
                        events.append(raw_data)
    print(f"Nhận được {len(events)} SSE events từ server!")
    for e in events[:3]:
        if isinstance(e, dict):
            print(f"  Event type: {e.get('type')}")
        else:
            print(f"  Event chunk: {e[:60]}")

if __name__ == "__main__":
    test_health()
    test_chat_non_stream()
    test_multiturn()
    test_stream_sse()
    print("\n==========================================")
    print(">>> TOÀN BỘ CÁC BƯỚC TEST ĐỀU HOÀN THÀNH XUẤT SẮC! <<<")
