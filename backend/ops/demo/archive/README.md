# Historical demo reset - not an upgrade script

`V27_legacy_destructive_reset.sql` is preserved only as evidence of the former
demo reset. It deletes operational data and must not run against a database
that contains customer orders, stock reservations or purchase orders.

Application Flyway V27 is now non-destructive. Automatic checksum repair has
been removed. A database that already applied the previous V27 requires an
explicit reconciliation of its history and data from a verified backup; the
application intentionally fails validation instead of accepting a different
migration checksum silently. No data recovery or checksum repair is executed
by this code change.
