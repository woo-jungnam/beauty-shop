"""Legacy CLI entry point for the read-only temporal Content-Based evaluation.

Mathematical correctness: python -m pytest tests/test_content_based.py -q
Observed ranking quality: python test_recsys_behavior.py --k 8

Requires the configured MySQL catalog; never inserts/deletes synthetic behavior.
"""
import importlib.util
from pathlib import Path


def run_test():
    path = Path(__file__).resolve().parent / 'evaluation' / 'content_based_holdout.py'
    spec = importlib.util.spec_from_file_location('content_based_evaluation', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.main()


if __name__ == '__main__':
    run_test()
