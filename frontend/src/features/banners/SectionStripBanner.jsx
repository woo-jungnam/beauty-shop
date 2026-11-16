import React, { useState } from 'react';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import { bannerHref } from './bannerLinks';
import './image-banners.css';

/**
 * Component hiển thị banner dạng dải ngang (strip banner) trên đầu mỗi section.
 * - Cơ chế fallback tự động ẩn hoàn toàn (0px) nếu banner bị tắt (isActive = false) hoặc lỗi ảnh.
 * - Hỗ trợ liên kết điều hướng nội bộ / neo hash hoặc liên kết ngoài.
 */
export function SectionStripBanner({
  banners = [],
  banner: directBanner,
  position,
  className = '',
  style = {},
  priority = false,
}) {
  const [hasError, setHasError] = useState(false);

  // Chọn banner phù hợp
  let targetBanner = directBanner;
  if (!targetBanner && Array.isArray(banners) && position) {
    const candidates = banners
      .filter((b) => b && b.position === position && b.isActive !== false && b.imageUrl)
      .slice()
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0));
    targetBanner = candidates[0] || null;
  }

  // Nếu không có banner hợp lệ hoặc ảnh bị lỗi tải -> Tự động ẩn hoàn toàn
  if (!targetBanner || !targetBanner.imageUrl || hasError || targetBanner.isActive === false) {
    return null;
  }

  const href = bannerHref(targetBanner.targetUrl);
  const mediaSrc = resolveMediaUrl(targetBanner.imageUrl);
  const bannerTitle = targetBanner.title || 'Banner quảng cáo ưu đãi';

  const handleLinkClick = (event) => {
    if (href && href.startsWith('#') && !href.startsWith('#/')) {
      const targetEl = document.getElementById(href.slice(1));
      if (targetEl) {
        event.preventDefault();
        targetEl.scrollIntoView({
          behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
            ? 'instant'
            : 'smooth',
        });
      }
    }
  };

  const imageElement = (
    <img
      src={mediaSrc}
      alt={bannerTitle}
      title={bannerTitle}
      className="section-strip-banner-img"
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      onError={() => setHasError(true)}
    />
  );

  return (
    <div
      className={`section-strip-banner-wrapper ${className}`.trim()}
      style={style}
      aria-label={bannerTitle}
    >
      {href ? (
        <a
          href={href}
          className="section-strip-banner-link"
          onClick={handleLinkClick}
          aria-label={bannerTitle}
        >
          {imageElement}
        </a>
      ) : (
        <div className="section-strip-banner-static" aria-label={bannerTitle}>
          {imageElement}
        </div>
      )}
    </div>
  );
}

export function SectionBannerRow({ banners = [], position, label }) {
  const items = banners
    .filter((banner) => banner?.position === position && banner.isActive !== false && banner.imageUrl)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0))
    .slice(0, 3);

  if (!items.length) return null;

  return (
    <div className="category-banner-row" aria-label={label}>
      {items.map((banner) => (
        <SectionStripBanner key={`${banner.id}-${banner.imageUrl}`} banner={banner} />
      ))}
    </div>
  );
}

export function CategoryBannerRow({ banners = [] }) {
  return <SectionBannerRow banners={banners} position="SECTION_CATEGORIES" label="Ưu đãi danh mục" />;
}

export default SectionStripBanner;

