from pydantic import BaseModel, ConfigDict, Field
from app.schemas.product import ProductRead


class ScoredCandidate(BaseModel):
    productId: str = Field(alias="product_id")
    product: ProductRead | None = None
    sqlMatched: bool = Field(default=False, alias="sql_matched")
    bm25Score: float = Field(default=0.0, alias="bm25_score")
    bm25Rank: int | None = Field(default=None, alias="bm25_rank")
    vectorScore: float = Field(default=0.0, alias="vector_score")
    vectorRank: int | None = Field(default=None, alias="vector_rank")
    rrfScore: float = Field(default=0.0, alias="rrf_score")
    matchedChunkTypes: list[str] = Field(default_factory=list, alias="matched_chunk_types")
    matchedSnippets: list[str] = Field(default_factory=list, alias="matched_snippets")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def product_id(self) -> str:
        return self.productId

    @product_id.setter
    def product_id(self, val: str) -> None:
        self.productId = val

    @property
    def sql_matched(self) -> bool:
        return self.sqlMatched

    @sql_matched.setter
    def sql_matched(self, val: bool) -> None:
        self.sqlMatched = val

    @property
    def bm25_score(self) -> float:
        return self.bm25Score

    @bm25_score.setter
    def bm25_score(self, val: float) -> None:
        self.bm25Score = val

    @property
    def bm25_rank(self) -> int | None:
        return self.bm25Rank

    @bm25_rank.setter
    def bm25_rank(self, val: int | None) -> None:
        self.bm25Rank = val

    @property
    def vector_score(self) -> float:
        return self.vectorScore

    @vector_score.setter
    def vector_score(self, val: float) -> None:
        self.vectorScore = val

    @property
    def vector_rank(self) -> int | None:
        return self.vectorRank

    @vector_rank.setter
    def vector_rank(self, val: int | None) -> None:
        self.vectorRank = val

    @property
    def rrf_score(self) -> float:
        return self.rrfScore

    @rrf_score.setter
    def rrf_score(self, val: float) -> None:
        self.rrfScore = val

    @property
    def matched_chunk_types(self) -> list[str]:
        return self.matchedChunkTypes

    @matched_chunk_types.setter
    def matched_chunk_types(self, val: list[str]) -> None:
        self.matchedChunkTypes = val

    @property
    def matched_snippets(self) -> list[str]:
        return self.matchedSnippets

    @matched_snippets.setter
    def matched_snippets(self, val: list[str]) -> None:
        self.matchedSnippets = val


class RerankedCandidate(BaseModel):
    productId: str = Field(alias="product_id")
    product: ProductRead
    initialScore: float = Field(alias="initial_score")
    rerankScore: float = Field(alias="rerank_score")
    relevanceExplanation: str | None = Field(default=None, alias="relevance_explanation")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def product_id(self) -> str:
        return self.productId

    @property
    def initial_score(self) -> float:
        return self.initialScore

    @property
    def rerank_score(self) -> float:
        return self.rerankScore

    @property
    def relevance_explanation(self) -> str | None:
        return self.relevanceExplanation


class RetrievalResult(BaseModel):
    query: str
    candidates: list[ScoredCandidate] = Field(default_factory=list)
    totalCandidates: int = Field(default=0, alias="total_candidates")
    stageLatenciesMs: dict[str, float] = Field(default_factory=dict, alias="stage_latencies_ms")

    model_config = ConfigDict(populate_by_name=True)

    @property
    def total_candidates(self) -> int:
        return self.totalCandidates

    @property
    def stage_latencies_ms(self) -> dict[str, float]:
        return self.stageLatenciesMs
