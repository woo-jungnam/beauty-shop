# Private configuration

Use environment variables or an ignored `.env` file for DB_PASSWORD, JWT_SECRET,
SEPAY_WEBHOOK_API_KEY, SEPAY_BANK_ACCOUNT_NUMBER, mail credentials and the
recommendation internal token. `.env.example` contains placeholders only.
JWT_SECRET must use a non-default value of at least 32 bytes.

Demo seed accounts have NULL password hashes. Provision their credentials
securely outside version control before allowing login.

This branch is a sanitized standalone snapshot with its own Git history.
Database files, uploads, runtime logs and generated validation results are excluded.

V7 and V22 were sanitized to remove demo credential hashes. V7, V22, V46 and V47
use generic demo emails. Their Flyway checksums differ from the original project; use a fresh
database or reconcile migration history through your deployment process before
using this snapshot with an existing database. Never disable migration validation
just to bypass a checksum mismatch.
