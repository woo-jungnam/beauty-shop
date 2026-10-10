"""Check real catalog recommendations and SSE through a direct API or backend proxy.

python evaluation/smoke_api.py --base-url http://127.0.0.1:18000
python evaluation/smoke_api.py --base-url http://localhost/api/v1/chatbot --proxy
"""
import argparse
import json
import uuid

import httpx


MESSAGE = "Tư vấn kem chống nắng cho da dầu dưới 500k"
TEMPLATE_PREFIX = "Dưới đây là các lựa chọn phù hợp nhất với tiêu chí của bạn:"


def check_products(products):
    assert products, "No real catalog recommendations returned"
    assert all(str(product["id"]).startswith("mysql_") for product in products), products
    assert all(product["price"] > 0 for product in products), products
    assert all(product.get("image_url") or product.get("imageUrl") for product in products), products


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18000")
    parser.add_argument("--proxy", action="store_true")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")

    with httpx.Client(timeout=120) as client:
        response = client.get(base + "/health")
        response.raise_for_status()
        health = response.json().get("data") if args.proxy else response.json()
        assert str(health.get("status")).lower() in ("ok", "up"), health
        print("health: OK")

        response = client.post(base + "/chat", json={"message": MESSAGE, "session_id": "smoke_" + uuid.uuid4().hex})
        response.raise_for_status()
        chat = response.json().get("data") if args.proxy else response.json()
        answer = chat.get("answer") or chat.get("message") or ""
        assert answer.strip(), "Empty chatbot answer"
        assert not answer.startswith(TEMPLATE_PREFIX), "LLM generation returned a fallback template"
        check_products(chat.get("products", []))
        print("chat: OK; catalog products=" + ",".join(product["id"] for product in chat["products"]))

        events = []
        with client.stream("POST", base + "/chat/stream", json={"message": MESSAGE, "session_id": "smoke_stream_" + uuid.uuid4().hex}) as response:
            response.raise_for_status()
            assert "text/event-stream" in response.headers.get("content-type", "")
            for line in response.iter_lines():
                if line.startswith("data:"):
                    events.append(json.loads(line[5:].strip()))
        assert events and events[-1].get("type") == "done", events
        assert not any(event.get("type") == "error" for event in events), events
        metadata = next(event for event in events if event.get("type") == "metadata")
        check_products(metadata.get("products", []))
        answer = "".join(event.get("content", "") for event in events if event.get("type") == "token")
        assert answer.strip() and not answer.startswith(TEMPLATE_PREFIX), "SSE returned an empty answer or fallback"
        print("stream: OK; events=" + str(len(events)) + "; catalog products=" + ",".join(product["id"] for product in metadata["products"]))


if __name__ == "__main__":
    main()
