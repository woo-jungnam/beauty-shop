import json
import urllib.request
import time

url = "http://127.0.0.1:18000/chat"
for query in ["Sữa rửa mặt tạo bọt dịu nhẹ cho da nhạy cảm CeraVe", "Nước hoa hồng toner Some By Mi trị mụn có sẵn không shop?"]:
    t0 = time.time()
    req = urllib.request.Request(
        url,
        data=json.dumps({"message": query, "session_id": f"test_retry_{int(time.time())}"}).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            dur = time.time() - t0
            prods = [p.get("name") for p in data.get("products", [])]
            print(f"[THÀNH CÔNG] '{query}' ({dur:.2f}s): {prods}")
    except Exception as e:
        dur = time.time() - t0
        print(f"[LỖI] '{query}' ({dur:.2f}s): {e}")
