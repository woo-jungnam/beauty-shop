import urllib.request
import json
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')

queries = [
    "Hôm nay trời nóng quá, nên ăn gì?",
    "Bạn có biết nấu phở không?",
    "Cuối tuần nên đi đâu chơi?",
    "Tôi đang buồn, kể chuyện vui đi.",
    "Làm sao để học Java tốt hơn?",
    "Bạn thích chó hay mèo?",
    "Có cách nào kiếm 10 triệu mỗi tháng không?",
    "Tại sao con người phải ngủ?",
    "Tôi muốn mua một chiếc laptop, nên chọn loại nào?",
    "Bạn có biết hôm nay là thứ mấy không?"
]

results = []

for idx, q in enumerate(queries, 1):
    payload = {
        "sessionId": f"ood_test_{idx}_{int(time.time())}",
        "message": q
    }
    req = urllib.request.Request(
        "http://127.0.0.1:18000/chat",
        headers={"Content-Type": "application/json"},
        data=json.dumps(payload).encode("utf-8"),
        method="POST"
    )
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=40) as response:
            dur = time.time() - start
            res_data = json.loads(response.read().decode("utf-8"))
            results.append({
                "index": idx,
                "query": q,
                "status": "success",
                "duration_s": round(dur, 2),
                "intent": res_data.get("intent"),
                "answer": res_data.get("answer") or res_data.get("message"),
                "products_count": len(res_data.get("products") or []),
                "products": [p.get("name") for p in (res_data.get("products") or [])],
                "understanding": res_data.get("understanding")
            })
    except Exception as exc:
        results.append({
            "index": idx,
            "query": q,
            "status": "error",
            "error": str(exc)
        })

output_file = "ood_test_results.json"
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print(f"DONE testing {len(queries)} queries. Results saved to {output_file}.")
for r in results:
    if r["status"] == "success":
        ans = (r["answer"] or "").replace("\n", " ")[:90]
        und = r.get("understanding") or {}
        p_intent = und.get("primary_intent")
        print(f"[{r['index']}] Q: '{r['query']}'")
        print(f"     Intent: {r['intent']} | Primary: {p_intent} | Products: {r['products_count']} | Time: {r['duration_s']}s")
        print(f"     Answer: {ans}...")
        print("-" * 75)
    else:
        print(f"[{r['index']}] Q: '{r['query']}' -> ERROR: {r['error']}")
