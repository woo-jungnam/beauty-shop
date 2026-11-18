-- Reuse store media to initialize an independently managed treatment banner.
INSERT INTO banners (title, image_url, target_url, position, sort_order, is_active, created_by)
SELECT 'Lộ trình chăm sóc', media.image_url, '#treatment-catalog', 'TREATMENT_HERO', 1, TRUE, 'system'
FROM (
    SELECT spa.image_url, 0 AS source_rank, spa.sort_order, spa.id
    FROM banners spa
    WHERE spa.position = 'SECTION_SPA' AND spa.is_active = TRUE AND spa.is_deleted = FALSE
    UNION ALL
    SELECT service.thumbnail_url, 1 AS source_rank, 0 AS sort_order, template.id
    FROM treatment_templates template
    JOIN beauty_services service ON service.id = template.service_id
    WHERE template.published_version_id IS NOT NULL AND template.archived = FALSE
      AND service.is_active = TRUE AND service.is_deleted = FALSE
) media
WHERE NULLIF(TRIM(media.image_url), '') IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM banners existing WHERE existing.position = 'TREATMENT_HERO')
ORDER BY media.source_rank, media.sort_order, media.id
LIMIT 1;
