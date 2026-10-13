# Private configuration

Use an ignored `.env` file or environment variables for LLM_API_KEY,
MYSQL_PASSWORD, QDRANT_API_KEY and RECOMMENDATION_INTERNAL_TOKEN.
`product-agent/.env.example` contains placeholders only.

This branch is a standalone snapshot with its own Git history. It excludes
SQLite databases and their sidecar files, vector stores, embedding caches,
compiled Python files and generated evaluation/benchmark results.
Notebook outputs and execution counters are cleared before publication.
