import React, { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-react';
import { ProductCard } from './ProductCard';
import './related-product-carousel.css';

export function RelatedProductCarousel({ products, onNavigate }) {
  const trackRef = useRef(null);
  const [paused, setPaused] = useState(false);
  const [interacting, setInteracting] = useState(false);
  const move = (direction) => {
    const track = trackRef.current;
    if (!track) return;
    const step = track.firstElementChild?.getBoundingClientRect().width + 16;
    const end = track.scrollWidth - track.clientWidth;
    const next = direction > 0 && track.scrollLeft >= end - 2 ? 0
      : direction < 0 && track.scrollLeft <= 2 ? end : track.scrollLeft + direction * step;
    track.scrollTo({ left: next, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
  };
  useEffect(() => {
    if (paused || interacting || products.length < 2 || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const timer = window.setInterval(() => {
      const track = trackRef.current;
      if (document.hidden || !track || track.scrollWidth <= track.clientWidth) return;
      const box = track.getBoundingClientRect();
      if (box.bottom > 0 && box.top < window.innerHeight) move(1);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [paused, interacting, products.length]);

  return <div className="related-carousel" onMouseEnter={() => setInteracting(true)} onMouseLeave={() => setInteracting(false)}
    onFocusCapture={() => setInteracting(true)} onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget)) setInteracting(false); }}
    onTouchStart={() => setInteracting(true)} onTouchEnd={() => setInteracting(false)} onTouchCancel={() => setInteracting(false)}>
    <div className="related-carousel-controls" aria-label="Điều khiển sản phẩm tương tự">
      <button type="button" onClick={() => move(-1)} aria-label="Sản phẩm trước"><ChevronLeft size={17} /></button>
      <button type="button" onClick={() => setPaused(value => !value)} aria-label={paused ? 'Tự chuyển sản phẩm' : 'Dừng tự chuyển sản phẩm'} aria-pressed={paused}>
        {paused ? <Play size={15} /> : <Pause size={15} />}
      </button>
      <button type="button" onClick={() => move(1)} aria-label="Sản phẩm tiếp theo"><ChevronRight size={17} /></button>
    </div>
    <div className="related-carousel-track" ref={trackRef} tabIndex={0} aria-label="Danh sách sản phẩm tương tự, vuốt để xem thêm">
      {products.map(item => <ProductCard key={item.id} product={item} onNavigate={onNavigate} />)}
    </div>
  </div>;
}
