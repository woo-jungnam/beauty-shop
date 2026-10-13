import urllib.request
import json
import time

def query_chat(message: str, session_id: str = "test_live_session"):
    url = "http://127.0.0.1:8000/chat"
    payload = {
        "message": message,
        "session_id": session_id
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data
    except Exception as e:
        return {"error": str(e)}

def main():
    print("=== KIỂM THỬ LIVE API CHATBOT ===")
    test_cases = [
        ("serum", "PRODUCT", True),
        ("dịch vụ chăm sóc", "SERVICE", True),
        ("Có loại kem chống nắng nào bôi lên mát da thấm nhanh không?", "PRODUCT", False),
    ]

    for q, exp_target, exp_clarify in test_cases:
        res = query_chat(q, session_id=f"test_{int(time.time()*1000)}")
        print(f"\n[QUERY]: '{q}'")
        und = res.get("understanding") or {}
        target = und.get("target_type") or und.get("target")
        clarify = und.get("needs_clarification")
        p_intent = und.get("primary_intent")
        action = und.get("action_routing")
        reply = res.get("response") or res.get("reply") or ""

        print(f"   -> Target: {target} (Expected: {exp_target}) | Is None: {target is None}")
        print(f"   -> Primary Intent: {p_intent} | Needs Clarify: {clarify} (Expected: {exp_clarify})")
        print(f"   -> Action: {action}")
        print(f"   -> Bot Reply: {reply[:160]}...")
        
        assert target is not None, f"Target bị NULL cho '{q}'"
        assert target == exp_target, f"Target sai: {target} != {exp_target}"
        assert clarify == exp_clarify, f"Clarify sai: {clarify} != {exp_clarify}"

    print("\n>>> TẤT CẢ CÁC CA LIVE API ĐỀU ĐẠT CHUẨN!")

if __name__ == "__main__":
    main()
