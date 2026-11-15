from dataclasses import dataclass
from io import BytesIO
import json
import logging
import base64
from typing import Any, Dict, List, Optional

import httpx
from PIL import Image

logger = logging.getLogger("vision_analyzer")

GEMINI_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"

VISION_SYSTEM_PROMPT = """You are an expert cosmetics and retail e-commerce visual inspector.
The user uploads this image to search for an authentic cosmetic/skincare product in our store catalog.

Your mission:
1. Determine if there is a specific retail cosmetic or skincare product in the image (such as a bottle, jar, tube, compact, lipstick, dropper, cosmetic box, or packaging).
2. If the image is solely a human face, a person, a spa facial/massage treatment, aesthetic clinic procedure, general lifestyle scene, pet, or landscape without a clear retail cosmetic product for purchase, set "has_product" to false and provide a helpful, polite explanation in "rejection_reason" in Vietnamese.
3. If a cosmetic product is found:
   - Identify the primary cosmetic product and return its 2D bounding box "box_2d" in normalized coordinates [ymin, xmin, ymax, xmax] scaled 0 to 1000.
   - Extract the brand name ("extracted_brand") if visible (e.g., Vichy, La Roche-Posay, COSRX, The Ordinary, Clinique).
   - Extract the product name ("extracted_product_name") if readable.
   - Extract any other key active ingredients or text printed on the packaging ("packaging_text") (e.g., Retinol, Niacinamide 10%, Hyaluronic Acid, SPF 50+).

Respond with ONLY a valid JSON object matching this schema:
{
  "has_product": true,
  "rejection_reason": null,
  "box_2d": [ymin, xmin, ymax, xmax],
  "extracted_brand": "brand name or null",
  "extracted_product_name": "product name or null",
  "packaging_text": "all readable packaging text or null"
}
"""


@dataclass
class VisionAnalysisResult:
    has_product: bool
    rejection_reason: Optional[str] = None
    box_2d: Optional[List[int]] = None
    extracted_brand: Optional[str] = None
    extracted_product_name: Optional[str] = None
    packaging_text: Optional[str] = None
    cropped_image: Optional[Image.Image] = None
    error: Optional[str] = None


def crop_to_product(image: Image.Image, box_2d: List[int], margin_ratio: float = 0.05) -> Image.Image:
    """
    Cat rieng vung chua san pham dua theo toa do box_2d [ymin, xmin, ymax, xmax] (0-1000)
    co bo sung them mot luong vien padding (margin_ratio) de bao toan vien nap/than chai.
    """
    if not box_2d or len(box_2d) != 4:
        return image

    w, h = image.size
    if w <= 0 or h <= 0:
        return image

    ymin, xmin, ymax, xmax = box_2d

    # Gioi han toa do hop le [0, 1000]
    ymin = max(0, min(1000, ymin))
    xmin = max(0, min(1000, xmin))
    ymax = max(0, min(1000, ymax))
    xmax = max(0, min(1000, xmax))

    if ymax <= ymin or xmax <= xmin:
        return image

    box_w = xmax - xmin
    box_h = ymax - ymin

    # Bo sung them margin le vien
    pad_x = int(box_w * margin_ratio)
    pad_y = int(box_h * margin_ratio)

    xmin = max(0, xmin - pad_x)
    ymin = max(0, ymin - pad_y)
    xmax = min(1000, xmax + pad_x)
    ymax = min(1000, ymax + pad_y)

    px_min_x = int(xmin * w / 1000.0)
    px_min_y = int(ymin * h / 1000.0)
    px_max_x = int(xmax * w / 1000.0)
    px_max_y = int(ymax * h / 1000.0)

    # Kiem tra kich thuoc vung cat
    if (px_max_x - px_min_x) < 20 or (px_max_y - px_min_y) < 20:
        return image

    try:
        cropped = image.crop((px_min_x, px_min_y, px_max_x, px_max_y))
        return cropped
    except Exception as exc:
        logger.warning("Loi khi crop anh san pham: %s, giu nguyen anh goc", exc)
        return image


def _prepare_image_base64(image: Image.Image, max_dimension: int = 1024) -> str:
    """Thu nho nhe anh ve toi da 1024px va encode JPEG de toi uu toc do truyen goi API."""
    img_copy = image.copy()
    w, h = img_copy.size
    if max(w, h) > max_dimension:
        ratio = max_dimension / max(w, h)
        new_w = max(1, int(w * ratio))
        new_h = max(1, int(h * ratio))
        img_copy = img_copy.resize((new_w, new_h), Image.Resampling.BILINEAR)

    buffer = BytesIO()
    img_copy.convert("RGB").save(buffer, format="JPEG", quality=85)
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


async def analyze_product_with_gemini(
    image: Image.Image,
    api_key: str,
    model: str = "gemini-2.5-flash",
    timeout: float = 12.0,
) -> VisionAnalysisResult:
    """
    Goi Gemini Vision de nhan dien san pham, xac dinh Bounding Box va trich xuat text bao bi.
    Neu xay ra loi mang/timeout, tu dong fallback giu he thong hoat dong on dinh.
    """
    if not api_key:
        return VisionAnalysisResult(has_product=True, error="Chua cau hinh Gemini API Key")

    url = GEMINI_API_ENDPOINT.format(model=model, key=api_key)

    try:
        b64_image = _prepare_image_base64(image)
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": VISION_SYSTEM_PROMPT},
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": b64_image,
                            }
                        },
                    ]
                }
            ],
            "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.1,
            },
        }

        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(url, json=payload)

        if resp.status_code != 200:
            logger.warning("Gemini Vision tra ve status %d: %s", resp.status_code, resp.text[:200])
            return VisionAnalysisResult(
                has_product=True,
                error=f"Gemini API status {resp.status_code}",
            )

        data = resp.json()
        candidates = data.get("candidates", [])
        if not candidates:
            return VisionAnalysisResult(has_product=True, error="Empty Gemini candidates")

        text_content = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
        parsed = json.loads(text_content)

        has_product = bool(parsed.get("has_product", True))
        rejection_reason = parsed.get("rejection_reason")
        box_2d = parsed.get("box_2d")
        brand = parsed.get("extracted_brand")
        prod_name = parsed.get("extracted_product_name")
        pkg_text = parsed.get("packaging_text")

        cropped = None
        if has_product and box_2d:
            cropped = crop_to_product(image, box_2d)

        return VisionAnalysisResult(
            has_product=has_product,
            rejection_reason=rejection_reason,
            box_2d=box_2d,
            extracted_brand=brand,
            extracted_product_name=prod_name,
            packaging_text=pkg_text,
            cropped_image=cropped,
        )

    except Exception as exc:
        logger.error("Loi khi phan tich anh voi Gemini Vision: %s", exc)
        # Fallback an toan: van cho phep tim kiem tiep bang CLIP anh goc
        return VisionAnalysisResult(has_product=True, error=str(exc))

