import json
from datetime import datetime
from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Float,
    Index,
    Integer,
    String,
    Text,
    create_engine,
    event,
)
from sqlalchemy.orm import declarative_base, sessionmaker, Session, synonym
from app.core.config import getSettings

settings = getSettings()

connectArgs = {}
if settings.database_url.startswith("sqlite"):
    connectArgs = {"check_same_thread": False}
connect_args = connectArgs

engine = create_engine(
    settings.database_url,
    echo=False,
    connect_args=connectArgs,
    pool_pre_ping=True,
)

if settings.database_url.startswith("sqlite"):
    @event.listens_for(engine, "connect")
    def setSqlitePragma(dbapiConnection, connectionRecord):
        cursor = dbapiConnection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.execute("PRAGMA synchronous=NORMAL")
        cursor.close()

    set_sqlite_pragma = setSqlitePragma

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


class ProductModel(Base):
    __tablename__ = "products"

    id = Column(String(64), primary_key=True, index=True)
    name = Column(String(255), nullable=False, index=True)
    brand = Column(String(128), nullable=False, index=True)
    category = Column(String(64), nullable=False, index=True)
    price = Column(Float, nullable=False, index=True)

    description = Column(Text, default="")
    ingredients = Column(Text, default="")
    benefits = Column(Text, default="")
    usage = Column(Text, default="")
    rating = Column(Float, default=5.0)

    totalReviews = Column("total_reviews", Integer, default=0)
    totalSold = Column("total_sold", Integer, default=0)

    _skin_type = Column("skin_type", Text, default="[]")
    _concerns = Column("concerns", Text, default="[]")
    _reviews = Column("reviews", Text, default="[]")

    inStock = Column("in_stock", Boolean, default=True, index=True)
    imageUrl = Column("image_url", String(500), nullable=True)
    targetType = Column("target_type", String(32), default="PRODUCT", index=True)
    durationMinutes = Column("duration_minutes", Integer, default=0)
    createdAt = Column("created_at", DateTime, default=datetime.utcnow)
    updatedAt = Column("updated_at", DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    total_reviews = synonym("totalReviews")
    total_sold = synonym("totalSold")
    in_stock = synonym("inStock")
    image_url = synonym("imageUrl")
    target_type = synonym("targetType")
    duration_minutes = synonym("durationMinutes")
    created_at = synonym("createdAt")
    updated_at = synonym("updatedAt")
    basePrice = synonym("price")
    base_price = synonym("price")

    __table_args__ = (
        Index("idx_category_price", "category", "price"),
        Index("idx_category_brand_price", "category", "brand", "price"),
        Index("idx_brand_price", "brand", "price"),
        Index("idx_target_type", "target_type"),
        Index("idx_instock_rating_reviews", "in_stock", "rating", "total_reviews"),
        Index("idx_cat_instock_rating", "category", "in_stock", "rating"),
    )

    @property
    def skinType(self) -> list[str]:
        try:
            return json.loads(self._skin_type) if self._skin_type else []
        except Exception:
            return []

    @skinType.setter
    def skinType(self, value: list[str]) -> None:
        self._skin_type = json.dumps([v.upper().strip() for v in value], ensure_ascii=False)

    skin_type = skinType

    @property
    def concerns(self) -> list[str]:
        try:
            return json.loads(self._concerns) if self._concerns else []
        except Exception:
            return []

    @concerns.setter
    def concerns(self, value: list[str]) -> None:
        self._concerns = json.dumps([v.upper().strip() for v in value], ensure_ascii=False)

    @property
    def reviews(self) -> list[str]:
        try:
            return json.loads(self._reviews) if self._reviews else []
        except Exception:
            return []

    @reviews.setter
    def reviews(self, value: list[str]) -> None:
        self._reviews = json.dumps(value, ensure_ascii=False)


def getDbSession() -> Session:
    return SessionLocal()


get_db_session = getDbSession


def initDb() -> None:
    Base.metadata.create_all(bind=engine)
    try:
        if engine.dialect.name == "sqlite":
            with engine.begin() as conn:
                res = conn.exec_driver_sql("PRAGMA table_info(products)").fetchall()
                existingCols = {row[1] for row in res}
                if existingCols:
                    if "total_reviews" not in existingCols:
                        conn.exec_driver_sql("ALTER TABLE products ADD COLUMN total_reviews INTEGER DEFAULT 0")
                    if "total_sold" not in existingCols:
                        conn.exec_driver_sql("ALTER TABLE products ADD COLUMN total_sold INTEGER DEFAULT 0")
                    if "image_url" not in existingCols:
                        conn.exec_driver_sql("ALTER TABLE products ADD COLUMN image_url VARCHAR(500) DEFAULT NULL")
                    if "target_type" not in existingCols:
                        conn.exec_driver_sql("ALTER TABLE products ADD COLUMN target_type VARCHAR(32) DEFAULT 'PRODUCT'")
                    if "duration_minutes" not in existingCols:
                        conn.exec_driver_sql("ALTER TABLE products ADD COLUMN duration_minutes INTEGER DEFAULT 0")
                conn.exec_driver_sql("CREATE INDEX IF NOT EXISTS idx_instock_rating_reviews ON products (in_stock, rating, total_reviews)")
                conn.exec_driver_sql("CREATE INDEX IF NOT EXISTS idx_cat_instock_rating ON products (category, in_stock, rating)")
    except Exception:
        pass


init_db = initDb
initDb()
