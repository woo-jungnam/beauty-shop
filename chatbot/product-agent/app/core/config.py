import os
from functools import lru_cache
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent.parent
DEFAULT_DB_PATH = BASE_DIR / "cosmetics_ecommerce.db"


class Settings(BaseSettings):

    app_name: str = "Trợ lý tư vấn mỹ phẩm E-Commerce RAG"
    environment: str = "development"
    debug: bool = True
    host: str = "0.0.0.0"
    port: int = 8000

    database_url: str = f"sqlite:///{DEFAULT_DB_PATH.as_posix()}"

    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "Sql@123456"
    mysql_database: str = "beautyshop"

    qdrant_url: str = ":memory:"
    qdrant_api_key: str = ""
    qdrant_collection: str = "product_aspects"
    vector_dimension: int = 768

    embedding_provider: str = "local"  # "local" hoặc "gemini"
    embedding_local_model_path: str = "my_cosmetics_embedding"
    embedding_base_model: str = "bkai-foundation-models/vietnamese-bi-encoder"
    embedding_model_name: str = "models/gemini-embedding-2"
    embedding_batch_size: int = 64
    embedding_cache_size: int = 10000
    embedding_cache_dir: str = ""

    llm_api_key: str = ""
    llm_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai"
    llm_model: str = "gemini-3.5-flash-lite"
    llm_fallback_model: str = "gemini-2.5-flash"
    llm_timeout: float = 60.0
    llm_max_tokens: int = 2048
    llm_max_retries: int = 2
    llm_retry_delay: float = 1.0

    retrieval_sql_limit: int = 100
    retrieval_bm25_top_k: int = 20
    retrieval_vector_top_k: int = 20
    hybrid_rrf_k: int = 60
    rerank_top_k: int = 5
    max_context_tokens: int = 1200

    enable_cache: bool = True
    cache_ttl_seconds: int = 3600

    model_config = SettingsConfigDict(
        env_file=(
            str(BASE_DIR / ".env"),
            ".env",
        ),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    @property
    def appName(self) -> str:
        return self.app_name

    @property
    def databaseUrl(self) -> str:
        return self.database_url

    @property
    def mysqlHost(self) -> str:
        return self.mysql_host

    @property
    def mysqlPort(self) -> int:
        return self.mysql_port

    @property
    def mysqlUser(self) -> str:
        return self.mysql_user

    @property
    def mysqlPassword(self) -> str:
        return self.mysql_password

    @property
    def mysqlDatabase(self) -> str:
        return self.mysql_database

    @property
    def qdrantUrl(self) -> str:
        return self.qdrant_url

    @property
    def qdrantApiKey(self) -> str:
        return self.qdrant_api_key

    @property
    def qdrantCollection(self) -> str:
        return self.qdrant_collection

    @property
    def vectorDimension(self) -> int:
        return self.vector_dimension

    @property
    def embeddingModelName(self) -> str:
        return self.embedding_model_name

    @property
    def embeddingBatchSize(self) -> int:
        return self.embedding_batch_size

    @property
    def embeddingCacheSize(self) -> int:
        return self.embedding_cache_size

    @property
    def llmApiKey(self) -> str:
        return self.llm_api_key

    @property
    def llmBaseUrl(self) -> str:
        return self.llm_base_url

    @property
    def llmModel(self) -> str:
        return self.llm_model

    @property
    def llmFallbackModel(self) -> str:
        return self.llm_fallback_model

    @property
    def llmTimeout(self) -> float:
        return self.llm_timeout

    @property
    def llmMaxTokens(self) -> int:
        return self.llm_max_tokens

    @property
    def llmMaxRetries(self) -> int:
        return self.llm_max_retries

    @property
    def llmRetryDelay(self) -> float:
        return self.llm_retry_delay

    @property
    def retrievalSqlLimit(self) -> int:
        return self.retrieval_sql_limit

    @property
    def retrievalBm25TopK(self) -> int:
        return self.retrieval_bm25_top_k

    @property
    def retrievalVectorTopK(self) -> int:
        return self.retrieval_vector_top_k

    @property
    def hybridRrfK(self) -> int:
        return self.hybrid_rrf_k

    @property
    def rerankTopK(self) -> int:
        return self.rerank_top_k

    @property
    def maxContextTokens(self) -> int:
        return self.max_context_tokens

    @property
    def enableCache(self) -> bool:
        return self.enable_cache

    @property
    def cacheTtlSeconds(self) -> int:
        return self.cache_ttl_seconds

    def __getattr__(self, item: str):
        import re
        snakeName = re.sub(r"(?<!^)(?=[A-Z])", "_", item).lower()
        try:
            return super().__getattribute__(snakeName)
        except AttributeError:
            raise AttributeError(f"'{type(self).__name__}' object has no attribute '{item}'")


@lru_cache
def getSettings() -> Settings:
    return Settings()


get_settings = getSettings
settings = getSettings()
