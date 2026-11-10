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

    # Image upload directory (Shared Docker volume)
    image_search_upload_dir: str = "/app/uploads"
    image_search_max_bytes: int = 5 * 1024 * 1024  # 5 MiB
    image_search_max_pixels: int = 20_000_000
    image_search_batch_size: int = 16

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

