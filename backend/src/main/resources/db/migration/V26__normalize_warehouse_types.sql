-- MAIN was the legacy backend name for the central distribution warehouse.
-- The database seed and admin contract use CENTRAL/BRANCH/TRANSIT.
UPDATE warehouses
SET warehouse_type = 'CENTRAL'
WHERE warehouse_type = 'MAIN';
