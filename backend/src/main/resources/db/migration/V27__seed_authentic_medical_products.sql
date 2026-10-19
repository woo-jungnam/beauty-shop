-- Intentionally non-destructive.
-- The former demo reset deleted operational catalog, stock and transaction data.
-- Demo data must not run as an application upgrade. The original script is
-- retained for historical review only under ops/demo/archive, outside Flyway.
-- Databases that already applied the former V27 must be reconciled manually;
-- do not repair its checksum automatically or infer lost stock from seed values.
SELECT 1;
