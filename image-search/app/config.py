from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    host: str = "0.0.0.0"
    port: int = 8001
    debug: bool = False

    # MySQL Database credentials
    mysql_host: str = "mysql"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = ""
    mysql_database: str = "beautyshop"

    # Qdrant Vector DB Settings (Isolated dedicated image collection)
    qdrant_storage_path: str = str(BASE_DIR / "data" / "qdrant_image_storage")
    qdrant_collection: str = "product_images"
    vector_dimension: int = 512

    # CLIP Model Settings
    image_search_model_id: str = "sentence-transformers/clip-ViT-B-32"
    image_search_model_revision: str = "327ab6726d33c0e22f920c83f2ff9e4bd38ca37f"
    image_search_cache_dir: str = str(BASE_DIR / "data" / "model-cache")
    image_search_internal_token: str = ""
    min_similarity_threshold: float = 0.65

    # Image upload directory (Shared Docker volume)
    image_search_upload_dir: str = "/app/uploads"
    image_search_max_bytes: int = 5 * 1024 * 1024  # 5 MiB
    image_search_max_pixels: int = 20_000_000
    image_search_batch_size: int = 16

    # Target Detection & Packaging OCR (Gemini Vision)
    gemini_api_key: str = ""
    llm_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash"
    gemini_timeout: float = 12.0
    target_detection_enabled: bool = True
    ocr_hybrid_enabled: bool = True
    hybrid_image_weight: float = 0.30
    hybrid_text_weight: float = 0.70

    @property
    def effective_gemini_api_key(self) -> str:
        return (self.gemini_api_key or self.llm_api_key or "").strip()

    model_config = SettingsConfigDict(
        env_file=(".env", str(BASE_DIR / ".env")),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )


_settings_instance = None


def get_settings() -> Settings:
    global _settings_instance
    if _settings_instance is None:
        _settings_instance = Settings()
    return _settings_instance

