import React, { useEffect, useState } from 'react';
import { ArrowRight, ImageOff, Sparkles } from 'lucide-react';
import { apiClient } from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import { formatCurrency, resolveMediaUrl } from '../../../shared/utils/formatters';
import { getGuestSessionId } from '../../../shared/utils/session';
import { knownNumber } from '../../utils/productSelection';
import './product-detail-sidebar.css';

function SidebarItem({ product, onNavigate }) {
  const [broken, setBroken] = useState(false);
  const price = knownNumber(product.minPrice ?? product.discountPrice ?? product.basePrice);
  const src = product.thumbnailUrl || product.imageUrl;
  return <a className="pd-side-item" href={`#/product/${product.id}`} onClick={event => {
    if (event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey && onNavigate) {
      event.preventDefault(); onNavigate(`product/${product.id}`);
    }
  }}>
    <span className="pd-side-image">{src && !broken
      ? <img src={resolveMediaUrl(src)} alt={product.name} loading="lazy" onError={() => setBroken(true)} />
      : <ImageOff size={22} aria-label="Chưa có ảnh" />}</span>
    <span className="pd-side-copy">
      {(product.brand?.name || product.brandName) && <small>{product.brand?.name || product.brandName}</small>}
      <span className="pd-side-name">{product.name}</span>
      <strong>{price == null ? 'Chưa có giá' : formatCurrency(price)}</strong>
    </span>
  </a>;
}

function SidebarList({ title, url, productId, onNavigate, categoryLink }) {
  const [state, setState] = useState({ loading: true, items: [], error: false });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    if (!url) { setState({ loading: false, items: [], error: false }); return; }
    setState({ loading: true, items: [], error: false });
    apiClient.get(url, { signal: controller.signal }).then(response => {
      if (controller.signal.aborted) return;
      const data = response?.data?.data ?? response?.data ?? response;
      const items = Array.isArray(data) ? data : data?.content;
      if (!Array.isArray(items)) throw new Error('Invalid product list');
      const seen = new Set([String(productId)]);
      setState({ loading: false, error: false, items: items.filter(item => {
        if (!item?.id || seen.has(String(item.id))) return false;
        seen.add(String(item.id)); return true;
      }).slice(0, 5) });
    }).catch(() => { if (!controller.signal.aborted) setState({ loading: false, items: [], error: true }); });
    return () => controller.abort();
  }, [url, productId, attempt]);
  return <section className="pd-side-section" aria-label={title}>
    <header><Sparkles size={16} /><h2>{title}</h2></header>
    {state.loading ? <p className="pd-side-message" role="status">Đang tải sản phẩm…</p>
      : state.error ? <div className="pd-side-message" role="alert">Chưa tải được danh sách. <button type="button" onClick={() => setAttempt(value => value + 1)}>Thử lại</button></div>
      : state.items.length ? <div className="pd-side-list">{state.items.map(item => <SidebarItem key={item.id} product={item} onNavigate={onNavigate} />)}</div>
      : <p className="pd-side-message">Chưa có sản phẩm phù hợp.</p>}
    <a className="pd-side-more" href={`#/${categoryLink}`} onClick={event => {
      if (!event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey && onNavigate) { event.preventDefault(); onNavigate(categoryLink); }
    }}>Xem thêm <ArrowRight size={13} /></a>
  </section>;
}

export function ProductDetailSidebar({ product, onNavigate }) {
  const brandId = product.brand?.id ?? product.brandId;
  const ids = [...new Set((product.categories || []).map(item => item.id).filter(Boolean))];
  if (!ids.length && product.categoryId) ids.push(product.categoryId);
  const query = ids.length ? `categoryIds=${encodeURIComponent(ids.join(','))}&includeChildCategories=true&size=6` : '';
  return <aside className="pd-sidebar" aria-label="Khám phá sản phẩm">
    <SidebarList title="Gợi ý cho bạn" productId={product.id} onNavigate={onNavigate}
      url={ENDPOINTS.CATALOG.PUBLIC_RECOMMENDED_FOR_YOU(getGuestSessionId(), 6)} categoryLink="products" />
    <SidebarList title="Cùng danh mục" productId={product.id} onNavigate={onNavigate}
      url={query ? `/api/v1/products/search?${query}` : null} categoryLink={query ? `products?categoryIds=${encodeURIComponent(ids.join(','))}` : 'products'} />
    <SidebarList title="Cùng thương hiệu" productId={product.id} onNavigate={onNavigate}
      url={brandId ? `/api/v1/products/search?brandIds=${encodeURIComponent(brandId)}&size=6` : null}
      categoryLink={brandId ? `products?brandIds=${encodeURIComponent(brandId)}` : 'products'} />
  </aside>;
}
