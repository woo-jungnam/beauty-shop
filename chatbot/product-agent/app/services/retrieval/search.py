import asyncio
import hashlib
import json
import math
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
import httpx
from rank_bm25 import BM25Okapi

from app.core.config import get_settings
from app.core.logging import LatencyTracker, get_logger
from app.core.taxonomy import Taxonomy
from app.repositories.product_repository import ProductRepository
from app.repositories.vector_repository import vector_repository
from app.schemas.product import ProductChunk, ProductRead
from app.schemas.query import UnderstandingResult
from app.schemas.retrieval import RetrievalResult, ScoredCandidate
from app.services.ingestion.cleaning import TextCleaner

logger = get_logger("retrieval_search")
settings = get_settings()


class BM25SearchService:

    def __init__(self):
        self.chunks: List[ProductChunk] = []
        self.corpusTokens: List[List[str]] = []
        self.docTokenSets: List[Set[str]] = []
        self.bm25Model: Optional[BM25Okapi] = None
        self.corpus_tokens = self.corpusTokens
        self.doc_token_sets = self.docTokenSets
        self.bm25_model = self.bm25Model

    def tokenize(self, text: str) -> List[str]:
        cleanText = TextCleaner.normalize(text, expand_synonyms=True)
        tokens = [w for w in cleanText.split() if len(w) > 1 or w in ("b5", "ha", "c")]
        biGrams = [f"{tokens[i]}_{tokens[i+1]}" for i in range(len(tokens) - 1)]
        return tokens + biGrams

    _tokenize = tokenize

    def buildIndex(self, chunks: List[ProductChunk]) -> None:
        if not chunks:
            self.chunks = []
            self.corpusTokens = []
            self.docTokenSets = []
            self.bm25Model = None
            self.corpus_tokens = []
            self.doc_token_sets = []
            self.bm25_model = None
            return
        self.chunks = chunks
        self.corpusTokens = [self.tokenize(chunk.content) for chunk in chunks]
        self.docTokenSets = [set(tokens) for tokens in self.corpusTokens]
        self.bm25Model = BM25Okapi(self.corpusTokens)
        self.corpus_tokens = self.corpusTokens
        self.doc_token_sets = self.docTokenSets
        self.bm25_model = self.bm25Model

    build_index = buildIndex

    def indexChunks(self, chunks: List[ProductChunk]) -> None:
        self.buildIndex(chunks)

    index_chunks = indexChunks

    def search(
        self,
        query: str,
        topK: int = 20,
        category: Optional[str] = None,
        brand: Optional[str] = None,
        skinType: Optional[str] = None,
        maxPrice: Optional[float] = None,
        targetType: Optional[str] = None,
        **kwargs,
    ) -> List[Tuple[ProductChunk, float]]:
        topK = kwargs.get("top_k", topK)
        skinType = kwargs.get("skin_type", skinType)
        maxPrice = kwargs.get("max_price", maxPrice)
        targetType = kwargs.get("target_type", targetType)

        if not self.bm25Model or not self.chunks:
            return []

        queryTokens = self._tokenize(query)
        if not queryTokens:
            return []

        scores = self.bm25Model.get_scores(queryTokens)
        queryTokenSet = set(queryTokens)
        scoredResults: List[Tuple[ProductChunk, float]] = []

        catLower = category.lower() if category else None
        brandLower = brand.lower() if brand else None
        skinLower = skinType.lower() if skinType else None
        targetTypeUpper = targetType.upper() if targetType else None

        for chunk, score, docSet in zip(self.chunks, scores, self.docTokenSets):
            hasOverlap = bool(queryTokenSet & docSet)
            effectiveScore = float(score) if score > 0.0 else (0.5 if hasOverlap else 0.0)
            if effectiveScore <= 0.0:
                continue
            metaData = chunk.metadata
            if targetTypeUpper and getattr(metaData, "target_type", "PRODUCT").upper() != targetTypeUpper:
                continue
            if catLower and metaData.category.lower() != catLower:
                continue
            if brandLower and metaData.brand.lower() != brandLower:
                continue
            if maxPrice is not None and metaData.price > maxPrice:
                continue
            if skinLower:
                pSkins = [s.lower() for s in metaData.skin_type]
                if skinLower not in pSkins and "all" not in pSkins:
                    continue
            scoredResults.append((chunk, effectiveScore))

        if not scoredResults and (skinType or maxPrice is not None or brand):
            for chunk, score, docSet in zip(self.chunks, scores, self.docTokenSets):
                hasOverlap = bool(queryTokenSet & docSet)
                effectiveScore = float(score) if score > 0.0 else (0.5 if hasOverlap else 0.0)
                if effectiveScore <= 0.0:
                    continue
                metaData = chunk.metadata
                if targetTypeUpper and getattr(metaData, "target_type", "PRODUCT").upper() != targetTypeUpper:
                    continue
                if catLower and metaData.category.lower() != catLower:
                    continue
                scoredResults.append((chunk, effectiveScore * 0.85))

            if not scoredResults:
                for chunk, score, docSet in zip(self.chunks, scores, self.docTokenSets):
                    hasOverlap = bool(queryTokenSet & docSet)
                    effectiveScore = float(score) if score > 0.0 else (0.5 if hasOverlap else 0.0)
                    if effectiveScore > 0.0:
                        metaData = chunk.metadata
                        if targetTypeUpper and getattr(metaData, "target_type", "PRODUCT").upper() != targetTypeUpper:
                            continue
                        scoredResults.append((chunk, effectiveScore * 0.7))

        scoredResults.sort(key=lambda x: x[1], reverse=True)
        return scoredResults[:topK]


class EmbeddingService:

    def __init__(self, dimension: int = 768, cacheSize: int = 2048, **kwargs):
        self.dimension = dimension
        self.cacheSize = kwargs.get("cache_size", cacheSize)
        self._cache: dict[str, List[float]] = {}
        self._client: Optional[httpx.AsyncClient] = None
        self.is_fallback_active: bool = False
        cacheDir = Path(settings.embedding_cache_dir) if settings.embedding_cache_dir else Path(__file__).resolve().parent.parent.parent / "data"
        self._cacheFile = cacheDir / "embedding_cache.json"
        self._cache_file = self._cacheFile
        self._isSaving = False
        self.local_model = None
        self.active_provider = getattr(settings, "embedding_provider", "local").lower()
        self._initLocalModel()
        modelKey = hashlib.sha256(settings.embedding_model_name.encode()).hexdigest()[:12] if self.active_provider == "gemini" else ""
        cacheName = f"embedding_cache_{self.active_provider}_{modelKey}_{self.dimension}.json" if modelKey else f"embedding_cache_{self.active_provider}.json"
        self._cacheFile = cacheDir / cacheName
        self._cache_file = self._cacheFile
        self._loadCacheFromDisk()

    def _initLocalModel(self) -> None:
        """Khởi tạo mô hình Embedding cục bộ nếu cấu hình embedding_provider == 'local'"""
        if self.active_provider != "local":
            return

        base_dir = Path(__file__).resolve().parent.parent.parent
        # Danh sách đường dẫn ưu tiên kiểm tra model fine-tuned
        candidate_paths = [
            base_dir / getattr(settings, "embedding_local_model_path", "my_cosmetics_embedding"),
            base_dir.parent / "training" / "export_model" / "my_cosmetics_embedding",
            base_dir.parent / "training" / "my_cosmetics_embedding",
            base_dir.parent / "embedding-finetuning" / "export_model" / "my_cosmetics_embedding",
            base_dir.parent / "colab_embedding_training" / "my_cosmetics_embedding",
            Path(getattr(settings, "embedding_local_model_path", "my_cosmetics_embedding")),
        ]

        found_path = None
        for cp in candidate_paths:
            if cp.exists() and (cp / "config.json").exists():
                found_path = cp
                break

        model_to_load = str(found_path) if found_path else getattr(settings, "embedding_base_model", "bkai-foundation-models/vietnamese-bi-encoder")
        try:
            from sentence_transformers import SentenceTransformer
            logger.info("Đang nạp mô hình Local Embedding: %s...", model_to_load)
            try:
                self.local_model = SentenceTransformer(model_to_load, local_files_only=True)
            except Exception:
                self.local_model = SentenceTransformer(model_to_load)
            self.active_provider = "local_finetuned" if found_path else "local_base"
            self.is_fallback_active = False
            logger.info("Đã nạp thành công mô hình Local Embedding (%s)!", self.active_provider)
        except Exception as exc:
            logger.warning("Không thể nạp mô hình cục bộ (%s). Chuyển sang chế độ API / Cache: %s", model_to_load, exc)
            self.local_model = None
            self.active_provider = "gemini"

    @property
    def activeProvider(self) -> str:
        return self.active_provider

    @property
    def isFallbackActive(self) -> bool:
        return self.is_fallback_active

    def loadCacheFromDisk(self) -> None:
        if self._cacheFile.exists():
            try:
                with open(self._cacheFile, "r", encoding="utf-8") as f:
                    self._cache = json.load(f)
            except Exception:
                pass

    _loadCacheFromDisk = loadCacheFromDisk
    _load_cache_from_disk = loadCacheFromDisk

    def saveCacheToDisk(self) -> None:
        if getattr(self, "_isSaving", False):
            return
        self._isSaving = True
        try:
            import shutil
            import uuid
            self._cacheFile.parent.mkdir(parents=True, exist_ok=True)
            cacheCopy = dict(self._cache)
            tmpFile = self._cacheFile.with_name(f"embedding_cache_{uuid.uuid4().hex[:8]}.tmp")
            with open(tmpFile, "w", encoding="utf-8") as f:
                json.dump(cacheCopy, f)
            shutil.move(str(tmpFile), str(self._cacheFile))
        except Exception:
            pass
        finally:
            self._isSaving = False

    _saveCacheToDisk = saveCacheToDisk
    _save_cache_to_disk = saveCacheToDisk

    async def getClient(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                timeout=settings.llm_timeout,
                limits=httpx.Limits(
                    max_keepalive_connections=20,
                    max_connections=50,
                    keepalive_expiry=60.0,
                ),
            )
        return self._client

    get_client = getClient

    async def aclose(self) -> None:
        if self._client and not self._client.is_closed:
            await self._client.aclose()
            self._client = None

    def fallbackVector(self, text: str) -> List[float]:
        self.is_fallback_active = True
        vec = [0.0] * self.dimension
        tokens = text.lower().split()
        if not tokens:
            return vec
        for token in tokens:
            hVal = int(hashlib.md5(token.encode("utf-8")).hexdigest(), 16)
            idx = hVal % self.dimension
            sign = 1.0 if ((hVal >> 4) & 1) else -1.0
            vec[idx] += sign
        norm = math.sqrt(sum(x * x for x in vec)) or 1.0
        return [x / norm for x in vec]

    _fallback_vector = fallbackVector

    async def embedText(self, text: str) -> List[float]:
        cleaned = TextCleaner.clean(text)
        if not cleaned:
            return [0.0] * self.dimension
        if cleaned in self._cache:
            return self._cache[cleaned]
        cacheKey = hashlib.sha256(cleaned.encode("utf-8")).hexdigest()
        if cacheKey in self._cache:
            return self._cache[cacheKey]

        # 1. Ưu tiên mô hình Local Embedding nếu đã nạp
        if self.local_model is not None:
            try:
                loop = asyncio.get_running_loop()
                vec = await loop.run_in_executor(
                    None,
                    lambda: self.local_model.encode(cleaned, normalize_embeddings=True).tolist()
                )
                if len(vec) == self.dimension:
                    self.is_fallback_active = False
                    self._cache[cleaned] = vec
                    return vec
            except Exception as exc:
                logger.warning("Lỗi tính toán vector bằng Local Model: %s", exc)

        # 2. Sử dụng API Gemini nếu được cấu hình hoặc fallback
        apiKey = getattr(settings, "llm_api_key", "") or os.getenv("GEMINI_API_KEY", "")
        if apiKey:
            modelName = settings.embedding_model_name
            if not modelName.startswith("models/"):
                modelName = f"models/{modelName}"
            reqUrl = f"https://generativelanguage.googleapis.com/v1beta/{modelName}:embedContent"
            payload = {
                "model": modelName,
                "content": {"parts": [{"text": cleaned}]},
                "outputDimensionality": self.dimension,
            }
            httpClient = await self.getClient()
            try:
                res = await httpClient.post(reqUrl, json=payload, headers={"x-goog-api-key": apiKey})
                if res.status_code == 200:
                    values = res.json().get("embedding", {}).get("values", [])
                    if len(values) == self.dimension:
                        self.is_fallback_active = False
                        self._cache[cleaned] = values
                        if len(self._cache) % 20 == 0:
                            try:
                                asyncio.create_task(asyncio.to_thread(self.saveCacheToDisk))
                            except RuntimeError:
                                pass
                        return values
            except Exception:
                pass

        vec = self.fallbackVector(cleaned)
        return vec

    embed_text = embedText

    async def embedQuery(self, query: str) -> List[float]:
        return await self.embedText(query)

    embed_query = embedQuery

    async def embedBatch(self, texts: List[str], batchSize: int = 16, **kwargs) -> List[List[float]]:
        batchSize = kwargs.get("batch_size", batchSize)
        batchSize = max(1, min(int(batchSize), 100))
        cleaned_texts = [TextCleaner.clean(t) for t in texts]

        # Xử lý nhanh hàng loạt bằng Local Model nếu có
        if self.local_model is not None:
            try:
                loop = asyncio.get_running_loop()
                vecs = await loop.run_in_executor(
                    None,
                    lambda: self.local_model.encode(
                        cleaned_texts,
                        batch_size=batchSize,
                        show_progress_bar=False,
                        normalize_embeddings=True
                    ).tolist()
                )
                for t, v in zip(cleaned_texts, vecs):
                    self._cache[t] = v
                self.saveCacheToDisk()
                return vecs
            except Exception as exc:
                logger.warning("Lỗi embedBatch trên Local Model: %s. Chuyển sang xử lý tuần tự.", exc)

        # Batch only uncached texts; startup must not issue one API request per chunk.
        apiKey = settings.llm_api_key or os.getenv("GEMINI_API_KEY", "")
        if apiKey and self.local_model is None:
            pending = list(dict.fromkeys(
                text for text in cleaned_texts
                if text and text not in self._cache
                and hashlib.sha256(text.encode("utf-8")).hexdigest() not in self._cache
            ))
            modelName = settings.embedding_model_name
            if not modelName.startswith("models/"):
                modelName = f"models/{modelName}"
            reqUrl = f"https://generativelanguage.googleapis.com/v1beta/{modelName}:batchEmbedContents"
            client = await self.getClient()
            for offset in range(0, len(pending), batchSize):
                batch = pending[offset:offset + batchSize]
                payload = {"requests": [
                    {"model": modelName, "content": {"parts": [{"text": text}]},
                     "outputDimensionality": self.dimension}
                    for text in batch
                ]}
                try:
                    response = None
                    for attempt in range(settings.llm_max_retries + 1):
                        response = await client.post(reqUrl, json=payload, headers={"x-goog-api-key": apiKey})
                        if response.status_code not in (429, 502, 503, 504) or attempt == settings.llm_max_retries:
                            break
                        await asyncio.sleep(max(1.0, settings.llm_retry_delay) * 2 ** attempt)
                    response.raise_for_status()
                    vectors = [item.get("values", []) for item in response.json().get("embeddings", [])]
                    if len(vectors) != len(batch) or any(len(vector) != self.dimension for vector in vectors):
                        raise ValueError("Invalid embedding batch size or vector dimension")
                    self._cache.update(zip(batch, vectors))
                    self.is_fallback_active = False
                except Exception as exc:
                    logger.warning("Embedding batch failed (%s); retrying individual texts.", type(exc).__name__)

        results: List[List[float]] = []
        for i in range(0, len(texts), batchSize):
            batch = texts[i : i + batchSize]
            tasks = [self.embedText(t) for t in batch]
            batchVectors = await asyncio.gather(*tasks)
            results.extend(batchVectors)
        self.saveCacheToDisk()
        return results

    embed_batch = embedBatch



class HybridRetrievalService:

    def __init__(self, productRepo: Optional[ProductRepository] = None, **kwargs):
        self.productRepo = productRepo or kwargs.get("product_repo") or ProductRepository()
        self.product_repo = self.productRepo

    async def retrieve(
        self,
        understanding: UnderstandingResult,
        topK: int = 15,
        **kwargs,
    ) -> RetrievalResult:
        topK = kwargs.get("top_k", topK)
        tracker = LatencyTracker("hybrid_retrieval")

        queryText = understanding.semantic_query or understanding.raw_query or ""
        constraints = understanding.constraints
        preferences = understanding.preferences

        if isinstance(constraints, dict):
            catC = constraints.get("category")
            brand = constraints.get("brand")
            minPrice = constraints.get("min_price")
            maxPrice = constraints.get("max_price")
            skinType = constraints.get("skin_type")
            formFactor = constraints.get("form_factor")
        else:
            catC = getattr(constraints, "category", None)
            brand = getattr(constraints, "brand", None)
            minPrice = getattr(constraints, "min_price", None)
            maxPrice = getattr(constraints, "max_price", None)
            skinType = getattr(constraints, "skin_type", None)
            formFactor = getattr(constraints, "form_factor", None)

        if not formFactor and getattr(understanding, "form_factor", None):
            formFactor = understanding.form_factor

        rawCat = understanding.category or catC
        category = Taxonomy.normalize_category(str(rawCat)) if rawCat else None
        if category and Taxonomy.get_domain(category) is None:
            category = None
        uIntentVal = getattr(understanding.intent, "value", str(understanding.intent))
        if uIntentVal in ("ROUTINE_RECOMMENDATION", "ROUTINE", "COMPARISON", "PRODUCT_COMPARISON"):
            category = None
            rawCat = None
            if uIntentVal in ("COMPARISON", "PRODUCT_COMPARISON"):
                brand = None
                maxPrice = None
                minPrice = None

        targetType = None
        if isinstance(constraints, dict):
            targetType = constraints.get("target_type")
        else:
            targetType = getattr(constraints, "target_type", None)
        if not targetType and getattr(understanding, "entities", None):
            targetType = getattr(understanding.entities, "target_type", None)
        if targetType:
            targetType = str(targetType).upper().strip()
            if targetType in ("COMBO", "ALL", "NONE"):
                targetType = None

        if isinstance(preferences, dict):
            concerns = preferences.get("concerns") or preferences.get("concern")
            if isinstance(concerns, str):
                concerns = [concerns]
            ingredients = preferences.get("ingredients") or []
            if isinstance(ingredients, str):
                ingredients = [ingredients]
        else:
            concerns = getattr(preferences, "concerns", None)
            if isinstance(concerns, str):
                concerns = [concerns]
            ingredients = getattr(preferences, "ingredients", None) or []
            if isinstance(ingredients, str):
                ingredients = [ingredients]

        def _sqlSearchSync():
            with tracker.measure("sql_search"):
                return self.productRepo.filterProducts(
                    category=category,
                    brand=brand,
                    minPrice=minPrice,
                    maxPrice=maxPrice,
                    skinType=skinType,
                    queryKeyword=rawCat,
                    formFactor=formFactor,
                    targetType=targetType,
                    limit=settings.retrieval_sql_limit,
                )

        def _bm25SearchSync():
            with tracker.measure("bm25_search"):
                bm25Query = (getattr(understanding, "bm25_query", None) or "").strip()
                if not bm25Query:
                    bm25Query = queryText
                    extraTokens = []
                    if concerns:
                        extraTokens.extend(concerns)
                    if ingredients:
                        extraTokens.extend(ingredients)
                    if extraTokens:
                        bm25Query += " " + " ".join(extraTokens)
                return bm25Service.search(
                    query=bm25Query,
                    topK=settings.retrieval_bm25_top_k,
                    category=category,
                    brand=brand,
                    skinType=skinType,
                    maxPrice=maxPrice,
                    targetType=targetType,
                )

        async def _runVector():
            with tracker.measure("vector_search"):
                vecQuery = queryText
                if concerns and not any(c in vecQuery for c in concerns):
                    vecQuery += f" Dành cho vấn đề: {', '.join(concerns)}."
                queryVec = await embeddingService.embedQuery(vecQuery)
                return await asyncio.to_thread(
                    vector_repository.searchVectors,
                    queryVector=queryVec,
                    limit=settings.retrieval_vector_top_k,
                    category=category,
                    brand=brand,
                    minPrice=minPrice,
                    maxPrice=maxPrice,
                    skinType=skinType,
                    concerns=concerns,
                    targetType=targetType,
                )

        sqlProducts, bm25Hits, vectorPoints = await asyncio.gather(
            asyncio.to_thread(_sqlSearchSync),
            asyncio.to_thread(_bm25SearchSync),
            _runVector(),
            return_exceptions=False,
        )

        with tracker.measure("rrf_fusion"):
            rrfK = settings.hybrid_rrf_k
            wSql = 1.0
            if getattr(embeddingService, "is_fallback_active", False):
                wVector = 0.0
                wBm25 = 2.5
            else:
                wBm25 = 1.2
                wVector = 1.5

            candidateMap: Dict[str, ScoredCandidate] = {}

            for rankIdx, prod in enumerate(sqlProducts):
                pId = prod.id
                if pId not in candidateMap:
                    candidateMap[pId] = ScoredCandidate(
                        product_id=pId,
                        product=prod,
                        sql_matched=True,
                    )
                else:
                    candidateMap[pId].sql_matched = True
                    candidateMap[pId].product = prod
                candidateMap[pId].rrf_score += wSql / (rrfK + (rankIdx + 1))

            bm25SeenPids: Set[str] = set()
            bm25Rank = 0
            for chunk, score in bm25Hits:
                pId = chunk.product_id
                if pId not in candidateMap:
                    candidateMap[pId] = ScoredCandidate(product_id=pId)

                cand = candidateMap[pId]
                if score > cand.bm25_score:
                    cand.bm25_score = score
                if chunk.chunk_type.value not in cand.matched_chunk_types:
                    cand.matched_chunk_types.append(chunk.chunk_type.value)
                cand.matched_snippets.append(f"[{chunk.chunk_type.value}] {chunk.content[:140]}...")

                if pId not in bm25SeenPids:
                    bm25SeenPids.add(pId)
                    bm25Rank += 1
                    cand.bm25_rank = bm25Rank
                    cand.rrf_score += wBm25 / (rrfK + bm25Rank)

            vectorSeenPids: Set[str] = set()
            vecRank = 0
            for pt in vectorPoints:
                payload = pt.payload or {}
                pId = str(payload.get("product_id", ""))
                if not pId:
                    continue

                if pId not in candidateMap:
                    candidateMap[pId] = ScoredCandidate(product_id=pId)

                cand = candidateMap[pId]
                if pt.score > cand.vector_score:
                    cand.vector_score = pt.score

                cType = payload.get("chunk_type", "")
                if cType and cType not in cand.matched_chunk_types:
                    cand.matched_chunk_types.append(cType)

                cSnippet = payload.get("content", "")
                if cSnippet:
                    cand.matched_snippets.append(f"[{cType}] {cSnippet[:140]}...")

                if pId not in vectorSeenPids:
                    vectorSeenPids.add(pId)
                    vecRank += 1
                    cand.vector_rank = vecRank
                    cand.rrf_score += wVector / (rrfK + vecRank)

            pidsToFetch = [pId for pId, c in candidateMap.items() if c.product is None]
            if pidsToFetch:
                fetchedProds = await asyncio.to_thread(self.productRepo.getByIds, pidsToFetch)
                for p in fetchedProds:
                    if p.id in candidateMap:
                        candidateMap[p.id].product = p

            validCandidates = [c for c in candidateMap.values() if c.product is not None]
            if targetType == "SERVICE":
                validCandidates = [c for c in validCandidates if getattr(c.product, "target_type", "PRODUCT").upper() == "SERVICE"]
            elif targetType == "PRODUCT":
                validCandidates = [c for c in validCandidates if getattr(c.product, "target_type", "PRODUCT").upper() == "PRODUCT"]

            validCandidates.sort(key=lambda x: x.rrf_score, reverse=True)
            topCandidates = validCandidates[:topK]

        return RetrievalResult(
            query=queryText,
            candidates=topCandidates,
            total_candidates=len(validCandidates),
            stage_latencies_ms=tracker.get_durations(),
        )

    search = retrieve


bm25Service = BM25SearchService()
bm25_service = bm25Service

embeddingService = EmbeddingService()
embedding_service = embeddingService

retrievalService = HybridRetrievalService()
retrieval_service = retrievalService
