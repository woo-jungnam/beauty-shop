





























































































































































































































import asyncio
import httpx
import json

async def main():
    session_id = "test_cross_domain_resumption_01"
    url = "http://localhost:8000/chat"
    
    turns = [
        "Tôi muốn tìm hiểu liệu trình trị mụn chuẩn y khoa ở spa bên bạn",
        "Shop có kem dưỡng ẩm phục hồi nào dùng tại nhà kèm theo không?",
        "Thế còn dịch vụ hồi nãy làm trong bao lâu và giá bao nhiêu?"
    ]
    
    async with httpx.AsyncClient(timeout=60.0) as client:
        for idx, msg in enumerate(turns, 1):
            print(f"\n{'='*25} TURN {idx}: '{msg}' {'='*25}")
            resp = await client.post(url, json={"session_id": session_id, "message": msg})
            data = resp.json()
            
            print(f"Status: {resp.status_code}")
            print(f"Intent: {data.get('intent')}")
            print(f"Needs Clarification: {data.get('needs_clarification')}")
            
            und = data.get("understanding") or {}
            print(f"Target Type: {und.get('target_type')}")
            print(f"Category: {und.get('category')}")
            print(f"Semantic Query: {und.get('semantic_query')}")
            
            products = data.get("products", [])
            print(f"Returned Items ({len(products)}):")
            for p in products:
                print(f" - [{p.get('target_type', 'PRODUCT')}] {p.get('name')} | Category: {p.get('category')} | Price: {p.get('price')}")
            
            answer = data.get('answer', '')
            print(f"Bot Answer:\n{answer[:350]}...")

if __name__ == "__main__":
    asyncio.run(main())
