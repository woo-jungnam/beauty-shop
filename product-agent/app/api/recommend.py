import secrets
from typing import Optional
from fastapi import APIRouter, Header, HTTPException, Query
from starlette.concurrency import run_in_threadpool

from app.core.config import getSettings
from app.services.recommendation import recommendationService

router = APIRouter(prefix='/recommend', tags=['Gợi ý sản phẩm Content-Based'])


@router.get('/similar/{product_id}')
async def similar(product_id: int, limit: int = Query(8, ge=1, le=50), x_recommendation_token: Optional[str] = Header(None)):
    authorize(x_recommendation_token)
    return await run_in_threadpool(recommendationService.similar, product_id, limit)


@router.get('/for-you')
async def for_user(user_id: Optional[int] = Query(None, ge=1), session_id: Optional[str] = Query(None, max_length=100),
                   limit: int = Query(8, ge=1, le=50), x_recommendation_token: Optional[str] = Header(None)):
    authorize(x_recommendation_token)
    return await run_in_threadpool(recommendationService.for_user, user_id, session_id, limit)


def authorize(token):
    expected = getSettings().recommendation_internal_token
    if not expected or not secrets.compare_digest(token or '', expected):
        raise HTTPException(403, 'Không có quyền quản trị hệ thống gợi ý.')


@router.get('/admin/status')
async def status(x_recommendation_token: Optional[str] = Header(None)):
    authorize(x_recommendation_token)
    return await run_in_threadpool(recommendationService.status)


@router.post('/admin/refresh')
async def refresh(x_recommendation_token: Optional[str] = Header(None)):
    authorize(x_recommendation_token)
    await run_in_threadpool(recommendationService.refresh, True)
    return await run_in_threadpool(recommendationService.status)
