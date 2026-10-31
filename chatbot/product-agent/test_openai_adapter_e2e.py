import asyncio
import json
import os
import sys
import time
import urllib.request
from pathlib import Path

# Fix Windows console UTF-8 output
if hasattr(sys.stdout, "buffer"):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "buffer"):
    import io
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# Ensure app can be imported
sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.core.config import getSettings
from app.llm.client import OpenAICompatibleAdapter, LLMClientError


def check_models_endpoint(base_url: str, api_key: str):
    print("=== 1. KIỂM TRA ENDPOINT /v1/models ===")
    models_url = f"{base_url.rstrip('/')}/models"
    print(f"Gọi tới: {models_url}")
    req = urllib.request.Request(
        models_url,
        headers={"Authorization": f"Bearer {api_key}"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            models = [m.get("id") for m in data.get("data", [])]
            print(f"[OK] Tìm thấy {len(models)} models: {models}")
            return models
    except Exception as e:
        print(f"[ERROR] Không thể lấy danh sách models: {e}")
        return []


async def run_adapter_tests():
    settings = getSettings()
    print("\n=== CẤU HÌNH HIỆN TẠI ===")
    print(f"LLM_BASE_URL: {settings.llmBaseUrl}")
    print(f"LLM_MODEL: {settings.llmModel}")
    print(f"LLM_FALLBACK_MODEL: {settings.llmFallbackModel}")
    print(f"LLM_API_KEY: {settings.llmApiKey[:8]}... (ẩn)")

    # 1. Kiểm tra /v1/models
    models = check_models_endpoint(settings.llmBaseUrl, settings.llmApiKey)

    adapter = OpenAICompatibleAdapter(settings=settings)

    # 2. Test understand (NLU JSON structured output)
    print("\n=== 2. KIỂM THỬ ADAPTER: understand() (NLU JSON) ===")
    test_msg = "Có loại kem chống nắng nào cho da dầu mụn kiềm dầu tốt không shop?"
    t0 = time.time()
    try:
        und = await adapter.understand(test_msg)
        dur = time.time() - t0
        print(f"[OK] Thời gian: {dur:.2f}s")
        print(f"  - Target type: {und.target_type}")
        print(f"  - Primary intent: {und.primary_intent}")
        print(f"  - Category: {und.category}")
        print(f"  - Needs clarification: {und.needs_clarification}")
        print(f"  - Action routing: {und.action_routing}")
    except Exception as e:
        print(f"[FAIL] Lỗi understand: {e}")
        import traceback
        traceback.print_exc()

    # 3. Test generateText (Text generation)
    print("\n=== 3. KIỂM THỬ ADAPTER: generateText() ===")
    prompt = "Tóm tắt ngắn gọn 2 công dụng chính của BHA trong chăm sóc da (dưới 50 từ)."
    t0 = time.time()
    try:
        reply = await adapter.generateText(prompt)
        dur = time.time() - t0
        print(f"[OK] Thời gian: {dur:.2f}s")
        print(f"  - Phản hồi:\n{reply}\n")
    except Exception as e:
        print(f"[FAIL] Lỗi generateText: {e}")
        import traceback
        traceback.print_exc()

    # 4. Test streamText (Streaming SSE)
    print("\n=== 4. KIỂM THỬ ADAPTER: streamText() ===")
    stream_prompt = "Chào bạn, hãy giới thiệu 1 câu chào mừng đến với cửa hàng mỹ phẩm."
    t0 = time.time()
    try:
        chunks = []
        async for chunk in adapter.streamText(stream_prompt):
            chunks.append(chunk)
            print(chunk, end="", flush=True)
        dur = time.time() - t0
        print(f"\n[OK] Nhận {len(chunks)} chunks sau {dur:.2f}s")
    except Exception as e:
        print(f"\n[FAIL] Lỗi streamText: {e}")
        import traceback
        traceback.print_exc()

    await adapter.aclose()
    print("\n=== HOÀN TẤT KIỂM THỬ ADAPTER ===")


if __name__ == "__main__":
    asyncio.run(run_adapter_tests())
