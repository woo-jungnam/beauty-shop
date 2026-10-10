from enum import Enum
from pydantic import BaseModel, ConfigDict, Field


class ChunkType(str, Enum):
    DESCRIPTION = "description"
    INGREDIENTS = "ingredients"
    BENEFITS = "benefits"
    USAGE = "usage"
    REVIEWS = "reviews"
    PROCEDURE = "procedure"


class ChunkMetadata(BaseModel):
    productId: str = Field(alias="product_id")
    chunkType: ChunkType = Field(alias="chunk_type")
    category: str
    brand: str
    skinType: list[str] = Field(default_factory=list, alias="skin_type")
    concerns: list[str] = Field(default_factory=list)
    price: float
    rating: float = 5.0
    totalReviews: int = Field(default=0, alias="total_reviews")
    totalSold: int = Field(default=0, alias="total_sold")
    targetType: str = Field(default="PRODUCT", alias="target_type")
    durationMinutes: int = Field(default=0, alias="duration_minutes")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def product_id(self) -> str:
        return self.productId

    @property
    def chunk_type(self) -> ChunkType:
        return self.chunkType

    @property
    def skin_type(self) -> list[str]:
        return self.skinType

    @property
    def total_reviews(self) -> int:
        return self.totalReviews

    @property
    def total_sold(self) -> int:
        return self.totalSold

    @property
    def target_type(self) -> str:
        return self.targetType

    @property
    def duration_minutes(self) -> int:
        return self.durationMinutes


class ProductChunk(BaseModel):
    chunkId: str = Field(alias="chunk_id")
    productId: str = Field(alias="product_id")
    chunkType: ChunkType = Field(alias="chunk_type")
    content: str
    metadata: ChunkMetadata

    model_config = ConfigDict(populate_by_name=True)

    @property
    def chunk_id(self) -> str:
        return self.chunkId

    @property
    def product_id(self) -> str:
        return self.productId

    @property
    def chunk_type(self) -> ChunkType:
        return self.chunkType


class ProductBase(BaseModel):
    id: str
    name: str
    brand: str
    category: str
    price: float
    description: str
    ingredients: str
    benefits: str
    skinType: list[str] = Field(default_factory=list, alias="skin_type")
    concerns: list[str] = Field(default_factory=list)
    usage: str
    rating: float = 5.0
    totalReviews: int = Field(default=0, alias="total_reviews")
    totalSold: int = Field(default=0, alias="total_sold")
    reviews: list[str] = Field(default_factory=list)
    inStock: bool = Field(default=True, alias="in_stock")
    imageUrl: str | None = Field(default=None, alias="image_url")
    targetType: str = Field(default="PRODUCT", alias="target_type")
    durationMinutes: int = Field(default=0, alias="duration_minutes")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def skin_type(self) -> list[str]:
        return self.skinType

    @property
    def total_reviews(self) -> int:
        return self.totalReviews

    @property
    def total_sold(self) -> int:
        return self.totalSold

    @property
    def in_stock(self) -> bool:
        return self.inStock

    @property
    def image_url(self) -> str | None:
        return self.imageUrl

    @property
    def target_type(self) -> str:
        return self.targetType

    @property
    def duration_minutes(self) -> int:
        return self.durationMinutes


class ProductCreate(ProductBase):
    pass


class ProductUpdate(BaseModel):
    name: str | None = None
    brand: str | None = None
    category: str | None = None
    price: float | None = None
    description: str | None = None
    ingredients: str | None = None
    benefits: str | None = None
    skinType: list[str] | None = Field(default=None, alias="skin_type")
    concerns: list[str] | None = None
    usage: str | None = None
    rating: float | None = None
    totalReviews: int | None = Field(default=None, alias="total_reviews")
    totalSold: int | None = Field(default=None, alias="total_sold")
    reviews: list[str] | None = None
    inStock: bool | None = Field(default=None, alias="in_stock")
    imageUrl: str | None = Field(default=None, alias="image_url")
    targetType: str | None = Field(default=None, alias="target_type")
    durationMinutes: int | None = Field(default=None, alias="duration_minutes")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def skin_type(self) -> list[str] | None:
        return self.skinType

    @property
    def total_reviews(self) -> int | None:
        return self.totalReviews

    @property
    def total_sold(self) -> int | None:
        return self.totalSold

    @property
    def in_stock(self) -> bool | None:
        return self.inStock

    @property
    def image_url(self) -> str | None:
        return self.imageUrl

    @property
    def target_type(self) -> str | None:
        return self.targetType

    @property
    def duration_minutes(self) -> int | None:
        return self.durationMinutes


class ProductRead(ProductBase):
    pass
