-- CartItemRepository.save() used to merge a new item (because row_version was
-- initialized to 0) and the cart cascade then persisted the original object.
-- The second/later row is the object returned to clients, so preserve it.
DELETE older
FROM cart_items older
JOIN cart_items newer
  ON newer.cart_id = older.cart_id
 AND newer.product_variant_id = older.product_variant_id
 AND newer.id > older.id;

ALTER TABLE cart_items
    ADD CONSTRAINT uk_cart_variant UNIQUE (cart_id, product_variant_id);
