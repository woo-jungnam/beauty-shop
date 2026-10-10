import time
import logging
from contextlib import contextmanager
from typing import Any, Dict, Generator

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
)

logger = logging.getLogger("cosmetics_rag")


def getLogger(name: str = "cosmetics_rag") -> logging.Logger:
    return logging.getLogger(name)


get_logger = getLogger


class LatencyTracker:

    def __init__(self, operationName: str = "rag_pipeline", **kwargs) -> None:
        self.operationName = kwargs.get("operation_name", operationName)
        self.operation_name = self.operationName
        self.stages: Dict[str, float] = {}
        self.activeStarts: Dict[str, float] = {}
        self._active_starts = self.activeStarts
        self.startTime = time.perf_counter()
        self.start_time = self.startTime

    @contextmanager
    def measure(self, stageName: str, **kwargs) -> Generator[None, Any, None]:
        stageName = kwargs.get("stage_name", stageName)
        t0 = time.perf_counter()
        try:
            yield
        finally:
            elapsedMs = (time.perf_counter() - t0) * 1000
            self.stages[stageName] = round(elapsedMs, 2)

    def startStage(self, stageName: str, **kwargs) -> None:
        stageName = kwargs.get("stage_name", stageName)
        self.activeStarts[stageName] = time.perf_counter()

    def endStage(self, stageName: str, **kwargs) -> None:
        stageName = kwargs.get("stage_name", stageName)
        t0 = self.activeStarts.pop(stageName, self.startTime)
        elapsedMs = (time.perf_counter() - t0) * 1000
        self.stages[stageName] = round(elapsedMs, 2)

    def getDurations(self) -> Dict[str, float]:
        return dict(self.stages)

    def getTotalDuration(self) -> float:
        return round((time.perf_counter() - self.startTime) * 1000, 2)

    def finish(self) -> Dict[str, float]:
        self.stages["total_latency"] = self.getTotalDuration()
        return self.stages

    start_stage = startStage
    end_stage = endStage
    get_durations = getDurations
    get_total_duration = getTotalDuration
