"""Run: python -I tests/inventory_expiry_check.py (stdlib only; no DB/provider)."""
import importlib.util
from datetime import date, datetime, timezone
from pathlib import Path
from types import SimpleNamespace


source = Path(__file__).resolve().parents[1] / "app/services/inventory_availability.py"
spec = importlib.util.spec_from_file_location("inventory_availability", source)
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)
assert policy.sale_cutoff(datetime(2026, 10, 17, 16, 59, tzinfo=timezone.utc)) == date(2026, 11, 16)
assert policy.sale_cutoff(datetime(2026, 10, 17, 17, 0, tzinfo=timezone.utc)) == date(2026, 11, 17)
assert "s.is_blocked = 0" in policy.AVAILABLE_STOCK_SQL
assert "s.expiration_date > %s" in policy.AVAILABLE_STOCK_SQL
assert "CURRENT_DATE" not in policy.AVAILABLE_STOCK_SQL


class Row(SimpleNamespace):
    def model_copy(self, update):
        return Row(**(vars(self) | update))


class Extractor:
    def getAvailableProductPrices(self, ids):
        assert set(ids) == {1, 2}
        return {2: 0.0}


old = Row(product=Row(id="mysql_1", targetType="PRODUCT", price=100.0, inStock=True))
valid = Row(product=Row(id="mysql_2", targetType="PRODUCT", price=80.0, inStock=True))
service = Row(product=Row(id="service_3", targetType="SERVICE", price=300.0))
invalid = Row(product=Row(id="sample_no_db_id", targetType="PRODUCT", price=20.0))
result = policy.filter_available_candidates([old, valid, service, invalid], Extractor())
assert len(result) == 2
assert result[0].product.id == "mysql_2" and result[0].product.price == 0.0
assert result[1] is service
assert valid.product.price == 80.0  # Never mutate cached retrieval objects.

extractor = (source.parents[0] / "ingestion/extractor.py").read_text(encoding="utf-8")
assert "cur.execute(query, (cutoff,))" in extractor
assert "safe_stock.product_variant_id = pv.id" in extractor
assert "CURRENT_DATE" not in extractor
orchestrator = (source.parents[0] / "agent/orchestrator.py").read_text(encoding="utf-8")
assert "run_in_threadpool(filter_available_candidates, rerankedCandidates, dbExtractor)" in orchestrator
assert orchestrator.index('with tracker.measure("live_inventory")') < orchestrator.index("rerankedCandidates=rerankedCandidates")
print("inventory expiry checks passed")
