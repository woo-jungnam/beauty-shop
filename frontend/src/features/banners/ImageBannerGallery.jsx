import React, { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import { bannerHref } from './bannerLinks';
import './image-banners.css';

function BannerImage({ banner, priority = false }) {
  const href = bannerHref(banner.targetUrl);
  const image = <img src={resolveMediaUrl(banner.imageUrl)} alt={banner.title || 'Ưu đãi nổi bật'}
    loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : 'auto'} decoding="async" />;
  if (!href) return <div className="image-banner-art">{image}</div>;
  return <a className="image-banner-art" href={href} onClick={event => {
    if (href.startsWith('#') && !href.startsWith('#/')) {
      const element = document.getElementById(href.slice(1));
      if (element) { event.preventDefault(); element.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' }); }
    }
  }}>{image}</a>;
}

export function ImageBannerGallery({ banners = [] }) {
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const visible = banners.filter(banner => banner.imageUrl && banner.isActive !== false)
    .slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  const slides = visible.filter(banner => !banner.position || banner.position === 'HERO_SLIDE');
  const sides = visible.filter(banner => banner.position === 'HERO_SIDE');
  const count = slides.length;
  const index = count ? active % count : 0;
  useEffect(() => {
    if (paused || count <= 1 || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const timer = window.setInterval(() => { if (!document.hidden) setActive(value => (value + 1) % count); }, 5000);
    return () => window.clearInterval(timer);
  }, [paused, count]);
  if (!count && !sides.length) return null;
  const sideBanners = sides.length > 2 ? [sides[active % sides.length], sides[(active + 1) % sides.length]] : sides;
  return <div className={`customer-container image-banner-grid ${!count || !sides.length ? 'image-banner-grid--single' : ''}`}
    aria-label="Banner nổi bật" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
    onFocusCapture={() => setPaused(true)} onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false); }}>
    {count > 0 && <div className="image-banner-carousel" aria-roledescription="trình chiếu" aria-label="Banner chính">
      <BannerImage banner={slides[index]} priority />
      {count > 1 && <>
        <button className="image-banner-prev" type="button" aria-label="Banner trước" onClick={() => setActive(value => (value - 1 + count) % count)}><ChevronLeft size={20} /></button>
        <button className="image-banner-next" type="button" aria-label="Banner tiếp theo" onClick={() => setActive(value => (value + 1) % count)}><ChevronRight size={20} /></button>
        <div className="image-banner-dots" aria-label="Chọn banner">{slides.map((banner, position) => <button key={banner.id || position} type="button"
          aria-label={`Xem banner ${position + 1}`} aria-pressed={position === index} onClick={() => setActive(position)} />)}</div>
      </>}
    </div>}
    {sideBanners.length > 0 && <div className={`image-banner-sides ${!count ? 'image-banner-sides--only' : ''}`}>
      {sideBanners.map((banner, position) => <BannerImage key={banner.id || position} banner={banner} />)}
    </div>}
  </div>;
}
