FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    DEBUG=false \
    EMBEDDING_PROVIDER=gemini \
    EMBEDDING_CACHE_DIR=/app/data \
    DATABASE_URL=sqlite:////app/data/cosmetics_ecommerce.db \
    QDRANT_URL=/app/data/qdrant_storage

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
    && mkdir -p /app/data
COPY app ./app

EXPOSE 8000
HEALTHCHECK --interval=20s --timeout=5s --start-period=600s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
