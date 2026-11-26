INSERT INTO procurement_movements (
    purchase_order_id,
    reference,
    kind,
    amount,
    reason,
    lines_json,
    created_at,
    updated_at,
    created_by,
    updated_by,
    is_deleted
)
SELECT
    po.id,
    LEFT(CONCAT('RCV-', po.order_number), 100),
    'RECEIPT',
    COALESCE(SUM(poi.unit_cost * poi.received_quantity), 0),
    CONCAT('Nhập kho trực tiếp theo phiếu mua ', po.order_number),
    CAST(
        JSON_ARRAYAGG(
            JSON_OBJECT(
                'itemId', poi.id,
                'quantity', poi.received_quantity,
                'batchCode', COALESCE(poi.batch_code, ''),
                'expirationDate', IF(poi.expiration_date IS NULL, NULL, DATE_FORMAT(poi.expiration_date, '%Y-%m-%d'))
            )
        ) AS CHAR
    ),
    COALESCE(po.received_at, po.updated_at, po.created_at),
    COALESCE(po.received_at, po.updated_at, po.created_at),
    po.created_by,
    po.updated_by,
    FALSE
FROM purchase_orders po
JOIN purchase_order_items poi
  ON poi.purchase_order_id = po.id
 AND poi.is_deleted = FALSE
 AND poi.received_quantity > 0
WHERE po.is_deleted = FALSE
  AND po.status IN ('RECEIVED', 'PARTIALLY_RECEIVED')
  AND NOT EXISTS (
      SELECT 1
      FROM procurement_movements pm
      WHERE pm.purchase_order_id = po.id
        AND pm.kind = 'RECEIPT'
        AND pm.is_deleted = FALSE
  )
  AND NOT EXISTS (
      SELECT 1
      FROM procurement_movements pm_ref
      WHERE pm_ref.reference = LEFT(CONCAT('RCV-', po.order_number), 100)
  )
GROUP BY po.id, po.order_number, po.received_at, po.updated_at, po.created_at, po.created_by, po.updated_by;

