import asyncio
import httpx
import json

async def main():
    session_id = "test_user_screenshot_case_01"
    url = "http://localhost:8000/chat"
    
    turns = [
        "Da em dạo này đổ nhiều dầu và có nhiều mụn ẩn",
        "sữa rửa mặt đi",
        "có serum không"
    ]
    
    async with httpx.AsyncClient(timeout=60.0) as client:
        for idx, msg in enumerate(turns, 1):
            print(f"\n{'='*20} TURN {idx}: '{msg}' {'='*20}")
            resp = await client.post(url, json={"session_id": session_id, "message": msg})
            data = resp.json()
            
            print(f"Status Code: {resp.status_code}")
            print(f"Intent: {data.get('intent')}")
            print(f"Needs Clarification: {data.get('needs_clarification')}")
            
            und = data.get("understanding") or {}
            print(f"Extracted Category: {und.get('category')}")
            print(f"Accumulated Skin Type: {und.get('skin_type')}")
            print(f"Accumulated Concerns: {und.get('concerns')}")
            
            products = data.get("products", [])
            print(f"Products Returned ({len(products)}):")
            for p in products:
                print(f" - {p.get('name')} | Brand: {p.get('brand')} | Skin: {p.get('skin_types')} | Concerns: {p.get('concerns')}")
            
            answer = data.get('answer', '')
            print(f"Bot Answer:\n{answer[:300]}...")

if __name__ == "__main__":
    asyncio.run(main())
