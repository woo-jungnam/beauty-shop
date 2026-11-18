# Review sentiment

Docker Compose runs this internal service on port 8002 within the application network.
The image contains the trained model, its metadata, and the original preprocessing
module. The Windows training directory is only needed to develop a new model.

Run `docker compose up -d --build` from the repository root to update the local stack.
The backend applies Flyway V77 and processes existing and new reviews in batches of
20. The admin review page shows sentiment and supports filtering it alongside moderation
status. `REVIEW_SENTIMENT_ENABLED=false` disables the background worker.

The worker retries failed batches up to five times per review. After the service is
healthy, an operator can requeue terminal failures using the following SQL:

```sql
UPDATE product_reviews
SET sentiment_state = 'PENDING',
    sentiment_attempts = 0,
    sentiment_next_attempt_at = NULL
WHERE is_deleted = FALSE AND sentiment_state = 'FAILED';
```

## Confirm a deployment

Start from the Compose directory used by the backend serving the admin page. A
deployment using this Compose file starts the model service even when targeting
only the backend. A standalone Spring Boot process needs
REVIEW_SENTIMENT_ENABLED=true and REVIEW_SENTIMENT_URL set to a model service
reachable from that process. The Compose service name only resolves within its
Docker network.

```sh
docker compose ps backend review-sentiment
docker compose exec -T backend printenv REVIEW_SENTIMENT_ENABLED REVIEW_SENTIMENT_URL
docker compose exec -T backend wget -qO- http://review-sentiment:8002/health
docker compose logs --tail=150 backend review-sentiment
```

The backend logs "Review sentiment worker enabled" when the worker is created.
The model health endpoint returns "ready" with the model version. A successful
inference appears as POST /predict with HTTP 200. To see whether jobs are being
processed, run this read-only query against the database used by that backend:

```sql
SELECT sentiment_state, COUNT(*) AS reviews,
       MAX(sentiment_attempts) AS max_attempts,
       MAX(sentiment_analyzed_at) AS last_analyzed_at
FROM product_reviews
WHERE is_deleted = FALSE
GROUP BY sentiment_state;
```

A backlog with zero attempts points to a worker that has not picked up those
rows. Increasing attempts or failed rows indicate errors during processing.
Check that the database has a successful V77 in flyway_schema_history before
troubleshooting the worker.
