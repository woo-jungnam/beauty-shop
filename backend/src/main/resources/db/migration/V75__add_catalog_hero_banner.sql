-- Initialize the catalog page banner from an existing image owned by the storefront.
INSERT INTO banners (title, image_url, target_url, position, sort_order, is_active, created_by)
SELECT 'Danh sách sản phẩm', seed.image_url, '#catalog-results', 'CATALOG_HERO', 1, TRUE, 'system'
FROM banners seed
WHERE seed.is_active = TRUE AND seed.is_deleted = FALSE
  AND NULLIF(TRIM(seed.image_url), '') IS NOT NULL
  AND (seed.position = 'SECTION_CATALOG' OR
       (seed.position = 'HERO_SLIDE' AND seed.target_url IN ('products', '#/products', '/#/products')))
  AND NOT EXISTS (SELECT 1 FROM banners existing WHERE existing.position = 'CATALOG_HERO')
ORDER BY (seed.position = 'SECTION_CATALOG') DESC, seed.sort_order, seed.id
LIMIT 1;
