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
