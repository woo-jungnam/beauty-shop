import secrets

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, UploadFile, File
from starlette.concurrency import run_in_threadpool
from starlette.responses import JSONResponse

from app.core.config import getSettings
from app.image_search.service import InvalidImage, UnsupportedImage, Unavailable

router = APIRouter(prefix="/image-search", tags=["Tìm kiếm bằng ảnh"])


def authorize(x_image_search_token: str = Header("")):
    expected = getSettings().image_search_internal_token
    if not expected or not secrets.compare_digest(x_image_search_token, expected):
        raise HTTPException(403, "Không có quyền truy cập tìm kiếm ảnh.")


@router.get("/status", dependencies=[Depends(authorize)])
async def status(request: Request):
    return await run_in_threadpool(request.app.state.image_search.status)


@router.post("/search", dependencies=[Depends(authorize)])
async def search(request: Request, image: UploadFile = File(...), limit: int = Query(20, ge=1, le=50)):
    settings = getSettings()
    try:
        raw = await image.read(settings.image_search_max_bytes + 1)
    finally:
        await image.close()
    if not raw:
        raise HTTPException(400, "Vui lòng chọn ảnh.")
    if len(raw) > settings.image_search_max_bytes:
        raise HTTPException(413, "Ảnh vượt quá dung lượng cho phép (tối đa 5 MiB).")
    try:
        return await run_in_threadpool(request.app.state.image_search.search, raw, limit)
    except UnsupportedImage as exc:
        raise HTTPException(415, str(exc)) from None
    except InvalidImage as exc:
        raise HTTPException(400, str(exc)) from None
    except Unavailable as exc:
        raise HTTPException(503, str(exc)) from None
    except Exception:
        raise HTTPException(503, "Không thể xử lý tìm kiếm ảnh. Vui lòng thử lại.") from None


class ImageSearchBodyLimit:
    """Bound and authenticate the request before Starlette parses multipart files.

    A bounded buffer also covers chunked requests without Content-Length.
    """
    def __init__(self, app, max_bytes, token):
        self.app, self.maximum, self.token = app, max_bytes + 64 * 1024, token

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["path"].rstrip("/") != "/image-search/search" or scope["method"] != "POST":
            return await self.app(scope, receive, send)
        headers = dict(scope.get("headers", []))
        supplied = headers.get(b"x-image-search-token", b"")
        if not self.token or not secrets.compare_digest(supplied, self.token.encode()):
            return await JSONResponse({"detail": "Không có quyền truy cập tìm kiếm ảnh."}, 403)(scope, receive, send)
        try:
            length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            return await JSONResponse({"detail": "Content-Length không hợp lệ."}, 400)(scope, receive, send)
        if length > self.maximum:
            return await JSONResponse({"detail": "Ảnh vượt quá dung lượng cho phép."}, 413)(scope, receive, send)
        data = bytearray()
        while True:
            event = await receive()
            if event["type"] == "http.disconnect":
                return
            data.extend(event.get("body", b""))
            if len(data) > self.maximum:
                return await JSONResponse({"detail": "Ảnh vượt quá dung lượng cho phép."}, 413)(scope, receive, send)
            if not event.get("more_body", False):
                break
        delivered = False

        async def bounded_receive():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": bytes(data), "more_body": False}
            return await receive()

        await self.app(scope, bounded_receive, send)
