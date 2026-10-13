from typing import Any
from pydantic import BaseModel, ConfigDict, Field, model_validator


class UnderstandTestRequest(BaseModel):
    message: str = Field(..., min_length=1)
    currentState: dict[str, Any] | None = Field(default=None, alias="current_state")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def current_state(self) -> dict[str, Any] | None:
        return self.currentState


class ChatRequest(BaseModel):
    sessionId: str = Field(..., min_length=1)
    message: str = Field(..., min_length=1)

    model_config = ConfigDict(populate_by_name=True)

    @model_validator(mode="before")
    @classmethod
    def checkSessionId(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "session_id" in data and "sessionId" not in data:
                data["sessionId"] = data["session_id"]
        return data

    @property
    def session_id(self) -> str:
        return self.sessionId


class ProductCard(BaseModel):
    id: str
    name: str
    brand: str
    category: str
    price: float
    skinType: list[str] = Field(default_factory=list, alias="skin_type")
    concerns: list[str] = Field(default_factory=list)
    keyIngredients: str = Field(default="", alias="key_ingredients")
    rating: float = 5.0
    totalReviews: int = Field(default=0, alias="total_reviews")
    totalSold: int = Field(default=0, alias="total_sold")
    reasonForRecommendation: str = Field(default="", alias="reason_for_recommendation")
    imageUrl: str | None = Field(default=None, alias="image_url")
    targetType: str = Field(default="PRODUCT", alias="target_type")
    durationMinutes: int = Field(default=0, alias="duration_minutes")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def skin_type(self) -> list[str]:
        return self.skinType

    @property
    def key_ingredients(self) -> str:
        return self.keyIngredients

    @property
    def total_reviews(self) -> int:
        return self.totalReviews

    @property
    def total_sold(self) -> int:
        return self.totalSold

    @property
    def reason_for_recommendation(self) -> str:
        return self.reasonForRecommendation

    @property
    def image_url(self) -> str | None:
        return self.imageUrl

    @property
    def target_type(self) -> str:
        return self.targetType

    @property
    def duration_minutes(self) -> int:
        return self.durationMinutes


class SourceRef(BaseModel):
    productId: str = Field(..., alias="product_id")
    productName: str = Field(..., alias="product_name")
    chunkType: str = Field(..., alias="chunk_type")
    snippet: str

    model_config = ConfigDict(populate_by_name=True)

    @property
    def product_id(self) -> str:
        return self.productId

    @property
    def product_name(self) -> str:
        return self.productName

    @property
    def chunk_type(self) -> str:
        return self.chunkType


class ChatResponse(BaseModel):
    sessionId: str = ""
    intent: str = "PRODUCT_SEARCH"
    answer: str = ""
    message: str | None = None
    products: list[ProductCard] = Field(default_factory=list)
    sources: list[SourceRef] = Field(default_factory=list)
    needsClarification: bool = Field(default=False, alias="needs_clarification")
    understanding: Any | None = None
    stageLatenciesMs: dict[str, float] | None = Field(default=None, alias="stage_latencies_ms")

    model_config = ConfigDict(populate_by_name=True)

    @model_validator(mode="before")
    @classmethod
    def checkSessionId(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "session_id" in data and "sessionId" not in data:
                data["sessionId"] = data["session_id"]
        return data

    @property
    def session_id(self) -> str:
        return self.sessionId

    @property
    def needs_clarification(self) -> bool:
        return self.needsClarification

    @property
    def stage_latencies_ms(self) -> dict[str, float] | None:
        return self.stageLatenciesMs

    @model_validator(mode="after")
    def syncMessageAndAnswer(self) -> "ChatResponse":
        if not self.answer and self.message:
            self.answer = self.message
        elif not self.message and self.answer:
            self.message = self.answer
        return self

    sync_message_and_answer = syncMessageAndAnswer
