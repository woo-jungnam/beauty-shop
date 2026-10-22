import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, Check, ChevronDown, ChevronRight, Expand, FlaskConical, ImageOff, Info, Loader2, Minus, Plus, ShoppingBag, Sparkles, Star, X } from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { formatCurrency, resolveMediaUrl } from '../../shared/utils/formatters';
import { getGuestSessionId } from '../../shared/utils/session';
import { useCustomerCart } from '../stores/customerCartStore';
import { useCustomerDialog } from '../components/ui/useCustomerDialog';
import { ProductCard } from '../components/product/ProductCard';
import { ProductReviews } from './product-detail/ProductReviews';
import { knownCount, knownNumber, variantPrice } from '../utils/productSelection';
import { activeVariants, chooseVariant, detailError, IMAGE_TYPES, productGallery, SKIN_LABELS, skinName, textList, unitPriceReference, unwrap, variantChoiceLabels, variantLabel } from './product-detail/detailUtils';

const SECTIONS = [ ['product-overview', 'Thông tin sản phẩm'], ['product-ingredients', 'Thành phần & INCI'], ['product-usage', 'Hướng dẫn sử dụng'], ['product-reviews', 'Đánh giá & bình luận'] ];
const normalText = value => String(value || '').replace(/\s+/g, ' ').trim();
const IngredientList = ({ label, items }) => items.length ? <div className="pd-function-group"><h3>{label}</h3><div className="pd-tags">{items.map(item => <span key={item}>{item}</span>)}</div></div> : null;
const track = (productId, actionType) => {
  try { apiClient.post(ENDPOINTS.CATALOG.TRACKING_INTERACTION, { productId: Number(productId), actionType, sessionId: getGuestSessionId() }).catch(() => {}); }
  catch {}
};

export const ProductDetailPage = props => <ProductDetailContent key={JSON.stringify([props.productId, props.requestedVariantId ?? null])} {...props} />;

const ProductDetailContent = ({ productId, requestedVariantId, onNavigate }) => {
  const { isAuthenticated } = useAuth();
  const { addItem, cartError, isLoading: cartBusy, clearCartError, setCartOpen } = useCustomerCart();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [attempt, setAttempt] = useState(0);
  const [variantId, setVariantId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [imageIndex, setImageIndex] = useState(0);
  const [brokenImages, setBrokenImages] = useState(() => new Set());
  const [zoomOpen, setZoomOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const [purchaseResult, setPurchaseResult] = useState(null);
  const [related, setRelated] = useState([]);
  const [relatedError, setRelatedError] = useState('');
  const [relatedLoading, setRelatedLoading] = useState(true);
  const [relatedAttempt, setRelatedAttempt] = useState(0);
  const [inciOpen, setInciOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('product-overview');
  const [headerHeight, setHeaderHeight] = useState(0);
  const zoomRef = useRef(null);
  const navRef = useRef(null);
  const pageRef = useRef(null);
  const addLock = useRef(false);
  const generation = useRef(0);
  const mounted = useRef(true);
  const readyProduct = product && String(product.id) === String(productId) ? product : null;
  const loadedProductId = readyProduct?.id;
  useCustomerDialog(zoomOpen && Boolean(readyProduct), zoomRef, () => setZoomOpen(false));
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    generation.current += 1;
    clearCartError();
    setLoading(true); setLoadError(null);
    const selection = attempt > 0 ? variantId : requestedVariantId;
    const fetch = async () => {
      try {
        const data = unwrap(await apiClient.get(ENDPOINTS.CATALOG.PUBLIC_PRODUCT_DETAIL(productId), { signal: controller.signal }));
        if (controller.signal.aborted) return;
        if (!data || String(data.id) !== String(productId) || !data.name) throw new Error('Dữ liệu sản phẩm chưa hợp lệ. Vui lòng tải lại.');
        setProduct(data); setVariantId(String(chooseVariant(data, selection)?.id ?? selection ?? ''));
        track(data.id, 'VIEW');
      } catch (err) {
        if (!controller.signal.aborted) setLoadError({ notFound: (err.status || err.response?.status) === 404,
          message: detailError(err, 'Chưa tải được thông tin sản phẩm. Vui lòng kiểm tra kết nối và thử lại.') });
      } finally { if (!controller.signal.aborted) setLoading(false); }
    };
    fetch();
    return () => { controller.abort(); generation.current += 1; };
  }, [productId, requestedVariantId, attempt, clearCartError]);
  useEffect(() => {
    const controller = new AbortController();
    if (!loadedProductId) return;
    apiClient.get(ENDPOINTS.CATALOG.PUBLIC_SIMILAR_PRODUCTS(loadedProductId, 8), { signal: controller.signal })
      .then(response => {
        if (controller.signal.aborted) return;
        const data = unwrap(response);
        const list = Array.isArray(data) ? data : data?.content;
        if (!Array.isArray(list)) throw new Error('Chưa đọc được danh sách sản phẩm tương tự.');
        setRelated(list.filter(item => String(item.id) !== String(loadedProductId)).slice(0, 8));
      }).catch(err => { if (!controller.signal.aborted) setRelatedError(detailError(err, 'Chưa tải được sản phẩm tương tự. Bạn vẫn có thể xem và mua sản phẩm này.')); })
      .finally(() => { if (!controller.signal.aborted) setRelatedLoading(false); });
    return () => controller.abort();
  }, [loadedProductId, relatedAttempt]);
  useEffect(() => {
    const header = document.querySelector('.customer-header');
    if (!header) return;
    const app = header.closest('.customer-app');
    const previousOffset = app?.style.getPropertyValue('--pd-header-height');
    const measure = () => {
      const height = Math.round(header.getBoundingClientRect().height);
      setHeaderHeight(height);
      app?.style.setProperty('--pd-header-height', `${height}px`);
    };
    measure();
    const observer = new ResizeObserver(measure); observer.observe(header);
    return () => {
      observer.disconnect();
      if (previousOffset) app?.style.setProperty('--pd-header-height', previousOffset);
      else app?.style.removeProperty('--pd-header-height');
    };
  }, []);
  useEffect(() => {
    if (!loadedProductId) return;
    let frame;
    const measure = () => {
      frame = undefined;
      const offset = headerHeight + (navRef.current?.offsetHeight || 50) + 25;
      let current = SECTIONS[0][0];
      for (const [id] of SECTIONS) { const section = document.getElementById(id); if (section && section.getBoundingClientRect().top <= offset) current = id; }
      setActiveSection(current);
    };
    const scroll = () => { if (!frame) frame = requestAnimationFrame(measure); };
    measure(); window.addEventListener('scroll', scroll, { passive: true });
    return () => { window.removeEventListener('scroll', scroll); cancelAnimationFrame(frame); };
  }, [loadedProductId, headerHeight]);

  const changeVariant = id => { setVariantId(String(id)); setQuantity('1'); setPurchaseResult(null); clearCartError(); };
  const variants = activeVariants(readyProduct);
  const variantChoices = variantChoiceLabels(variants);
  const selectedVariant = variants.find(variant => String(variant.id) === variantId) || null;
  const price = variantPrice(selectedVariant);
  const original = knownNumber(selectedVariant?.price);
  const discount = price != null && original > price ? Math.round((original - price) / original * 100) : 0;
  const stock = knownCount(selectedVariant?.stockQuantity);
  const validVariant = Boolean(selectedVariant && typeof selectedVariant.sku === 'string' && selectedVariant.sku.trim() && price != null);
  const amount = Number(quantity);
  const validQuantity = quantity !== '' && Number.isSafeInteger(amount) && amount > 0 && (stock == null || amount <= stock);
  const purchasable = validVariant && stock !== 0 && validQuantity && (!readyProduct?.status || readyProduct.status === 'ACTIVE');
  const purchase = async buyNow => {
    if (addLock.current || loading || cartBusy || !purchasable) return;
    addLock.current = true; setAdding(true); setPurchaseResult(null); clearCartError();
    const requestGeneration = generation.current;
    const purchasedProduct = readyProduct;
    try {
      const added = await addItem(purchasedProduct, selectedVariant, amount);
      if (!mounted.current || requestGeneration !== generation.current) return;
      if (!added) { setPurchaseResult({ success: false, message: 'Chưa thêm được sản phẩm. Giỏ hàng đã xác nhận được giữ nguyên.' }); return; }
      setPurchaseResult({ success: true, message: 'Đã thêm sản phẩm vào giỏ hàng.' });
      if (buyNow) { setCartOpen(false); onNavigate('checkout'); }
    } finally { addLock.current = false; if (mounted.current) setAdding(false); }
  };
  const navigateSection = (event, id) => {
    event.preventDefault(); setActiveSection(id);
    const element = document.getElementById(id);
    if (!element) return;
    element.focus({ preventScroll: true });
    const offset = headerHeight + (navRef.current?.offsetHeight || 50) + 14;
    window.scrollTo({ top: Math.max(0, element.getBoundingClientRect().top + window.scrollY - offset), behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  };
  const markBroken = url => setBrokenImages(previous => new Set([...previous, url]));
  const reloadProduct = () => { setLoading(true); setLoadError(null); setAttempt(previous => previous + 1); };
  const reloadRelated = () => { setRelatedLoading(true); setRelatedError(''); setRelatedAttempt(previous => previous + 1); };
  const image = (entry, className = '') => entry?.imageUrl && !brokenImages.has(entry.imageUrl)
    ? <img className={className} src={resolveMediaUrl(entry.imageUrl)} alt={entry.altText || readyProduct?.name || 'Ảnh sản phẩm'} onError={() => markBroken(entry.imageUrl)} />
    : <span className={`pd-image-placeholder ${className}`}><ImageOff size={35} /><span>{entry?.imageUrl ? 'Chưa tải được ảnh sản phẩm' : 'Ảnh sản phẩm đang cập nhật'}</span></span>;

  if (!readyProduct) return <div ref={pageRef} className="customer-container product-detail-page pd-page pd-loading" style={{ '--pd-header-height': `${headerHeight}px` }}>
    {loading || !loadError ? <div className="pd-loading-grid" role="status" aria-label="Đang tải sản phẩm"><div className="pd-loading-image skeleton-shimmer" /><div className="pd-loading-content"><span className="skeleton-shimmer" /><span className="skeleton-shimmer" /><span className="skeleton-shimmer" /><p>Đang tải thông tin sản phẩm…</p></div></div>
      : <div className="pd-load-error" role="alert"><ImageOff size={37} /><h1>{loadError.notFound ? 'Không tìm thấy sản phẩm' : 'Chưa tải được sản phẩm'}</h1><p>{loadError.notFound ? 'Sản phẩm không còn hiển thị hoặc đường dẫn chưa chính xác.' : loadError.message}</p>{!loadError.notFound && <button type="button" className="btn-luxury-primary" onClick={reloadProduct}>Thử tải lại sản phẩm</button>}<a href="#/products" onClick={event => { event.preventDefault(); onNavigate('products'); }}>Xem danh mục sản phẩm <ArrowRight size={15} /></a></div>}
  </div>;

  const gallery = productGallery(readyProduct);
  const selectedImage = gallery[imageIndex] || gallery[0];
  const categories = Array.isArray(readyProduct.categories) ? readyProduct.categories : [];
  const brand = readyProduct.brand?.name || readyProduct.brandName;
  const recommendations = readyProduct.skinCompatibility?.recommendedSkinTypes || [];
  const cautions = readyProduct.skinCompatibility?.notIdealFor || [];
  const ingredients = Array.isArray(readyProduct.ingredientsList) ? readyProduct.ingredientsList : [];
  const whenToUse = textList(readyProduct.usage?.whenToUse);
  const instructions = textList(readyProduct.usage?.instructions);
  const warnings = textList(readyProduct.usage?.warnings);
  const frequency = typeof readyProduct.usage?.frequency === 'string' ? readyProduct.usage.frequency.trim() : '';
  const rawUsage = typeof readyProduct.howToUse === 'string' ? readyProduct.howToUse.trim() : '';
  const showRawUsage = rawUsage && ![...instructions, ...whenToUse, frequency, instructions.join(' '), instructions.join('\n')].some(value => normalText(value) === normalText(rawUsage));
  const reference = unitPriceReference(selectedVariant, price);
  const attrs = (readyProduct.attributeValues || []).filter(attribute => attribute.productVariantId == null || String(attribute.productVariantId) === String(selectedVariant?.id));
  const formula = value => value == null ? 'Chưa công bố' : value ? 'Có trong công thức được công bố' : 'Không có theo dữ liệu công bố';
  const buyDisabled = loading || adding || cartBusy || !purchasable;
  const missingSelection = requestedVariantId != null && !selectedVariant;
  const stockLabel = missingSelection ? 'Phân loại trong đường dẫn không còn khả dụng. Không tự đổi sang SKU khác.' : !selectedVariant ? 'Chưa có SKU đang hoạt động' : !validVariant ? 'Phân loại chưa đủ thông tin để đặt mua' : stock === 0 ? 'Phân loại này đã hết hàng' : stock == null ? 'Tồn kho chưa xác minh · Hệ thống sẽ kiểm tra khi thêm giỏ' : `Còn ${stock} sản phẩm khả dụng`;

  return <div ref={pageRef} className="customer-container product-detail-page pd-page" style={{ '--pd-header-height': `${headerHeight}px` }}>
    <nav className="pd-breadcrumb" aria-label="Đường dẫn sản phẩm"><a href="#/" onClick={event => { event.preventDefault(); onNavigate(''); }}>Trang chủ</a><ChevronRight size={12} /><a href="#/products" onClick={event => { event.preventDefault(); onNavigate('products'); }}>Sản phẩm</a>{categories[0]?.id && <><ChevronRight size={12} /><a href={`#/products?categoryId=${categories[0].id}`} onClick={event => { event.preventDefault(); onNavigate(`products?categoryId=${categories[0].id}`); }}>{categories[0].name}</a></>}<ChevronRight size={12} /><span aria-current="page">{readyProduct.name}</span></nav>
    <div className="pd-main-grid">
      <section className="pd-gallery" aria-label="Ảnh sản phẩm"><button type="button" className="pd-main-image" aria-label="Phóng to ảnh sản phẩm" disabled={!selectedImage || brokenImages.has(selectedImage.imageUrl)} onClick={() => setZoomOpen(true)}>{image(selectedImage)}{discount > 0 && <span className="pd-discount">-{discount}%</span>}{selectedImage && !brokenImages.has(selectedImage.imageUrl) && <span className="pd-expand"><Expand size={17} /></span>}</button>
        {gallery.length > 0 && <div className="pd-thumbnails" aria-label="Chọn ảnh sản phẩm">{gallery.map((entry, index) => <button type="button" key={entry.key} aria-pressed={imageIndex === index} aria-label={`Xem ảnh ${index + 1}: ${entry.altText || IMAGE_TYPES[entry.imageType] || readyProduct.name}`} onClick={() => setImageIndex(index)}>{image(entry)}<span>{IMAGE_TYPES[entry.imageType] || `Ảnh ${index + 1}`}</span></button>)}</div>}
        <p className="pd-gallery-caption">{selectedImage?.altText || (gallery.length ? IMAGE_TYPES[selectedImage?.imageType] || 'Ảnh sản phẩm' : 'Chưa có ảnh trong dữ liệu sản phẩm.')}</p>
      </section>
      <section className="pd-buy-panel" aria-label="Thông tin mua sản phẩm">{brand && <p className="pd-brand">{brand}</p>}<h1>{readyProduct.name}</h1><div className="pd-top-meta">{Number(readyProduct.totalReviews) > 0 ? <a href="#product-reviews" onClick={event => navigateSection(event, 'product-reviews')}><Star size={15} fill="currentColor" />{Number(readyProduct.averageRating ?? 0).toFixed(1)} / 5 · {readyProduct.totalReviews} lượt chấm sao mua xác thực</a> : <span>Chưa có lượt chấm sao mua xác thực</span>}{Number(readyProduct.totalSold) > 0 && <span>Đã bán {readyProduct.totalSold}</span>}</div>
        {textList((readyProduct.tags || []).map(tag => tag.name)).length > 0 && <div className="pd-tags">{textList(readyProduct.tags.map(tag => tag.name)).map(tag => <span key={tag}>{tag}</span>)}</div>}
        <div className="pd-price"><strong>{price == null ? 'Chưa xác minh giá bán' : formatCurrency(price)}</strong>{discount > 0 && <del>{formatCurrency(original)}</del>}</div>{reference && <p className="pd-unit-price">Tham khảo: {formatCurrency(reference.value)} / 100 {reference.unit} · Không phải giá bán</p>}
        {readyProduct.shortDescription && <p className="pd-lead">{readyProduct.shortDescription}</p>}
        <div className="pd-variants"><span className="pd-label">Chọn phân loại</span>{variants.length ? <div role="group" aria-label="Phân loại sản phẩm">{variants.map(variant => <button type="button" key={variant.id} aria-pressed={String(variant.id) === variantId} disabled={adding} onClick={() => changeVariant(variant.id)}>{variantChoices.get(String(variant.id))}{String(variant.id) === variantId && <Check size={14} />}</button>)}</div> : <p className="pd-muted">Sản phẩm chưa có phân loại đang kinh doanh.</p>}</div>
        <div role={missingSelection || stock === 0 ? 'alert' : undefined} className={`pd-stock ${stock === 0 || !validVariant ? 'unavailable' : stock == null ? 'unknown' : ''}`}><i />{stockLabel}</div>
        {(missingSelection || stock === 0) && <button type="button" className="pd-text-button" onClick={reloadProduct}>Tải lại phân loại đã chọn</button>}
        <div className="pd-quantity-row"><label htmlFor="pd-quantity">Số lượng</label><div className="pd-quantity-control"><button type="button" aria-label="Giảm số lượng" disabled={adding || amount <= 1} onClick={() => setQuantity(String(Math.max(1, Number.isInteger(amount) ? amount - 1 : 1)))}><Minus size={15} /></button><input id="pd-quantity" type="number" min={1} max={stock ?? undefined} step={1} inputMode="numeric" value={quantity} disabled={adding} aria-invalid={!validQuantity} onChange={event => { setQuantity(event.target.value); setPurchaseResult(null); }} /><button type="button" aria-label="Tăng số lượng" disabled={adding || (stock != null && amount >= stock)} onClick={() => setQuantity(String(Math.min(stock ?? Number.MAX_SAFE_INTEGER, Number.isInteger(amount) && amount > 0 ? amount + 1 : 1)))}><Plus size={15} /></button></div>{!validQuantity && <span className="pd-quantity-error">Nhập số nguyên dương{stock != null ? `, tối đa ${stock}` : ''}.</span>}</div>
        <div className="pd-purchase-actions"><button type="button" className="btn-luxury-outline" disabled={buyDisabled} aria-busy={adding || undefined} onClick={() => purchase(false)}>{adding ? <Loader2 size={17} className="animate-spin" /> : <ShoppingBag size={17} />}Thêm vào giỏ hàng</button><button type="button" className="btn-luxury-primary" disabled={buyDisabled} onClick={() => purchase(true)}>Mua ngay <ArrowRight size={16} /></button></div>
        {purchaseResult && <div className={`pd-purchase-feedback ${purchaseResult.success ? 'is-success' : 'is-error'}`} role={purchaseResult.success ? 'status' : 'alert'}>{purchaseResult.success ? purchaseResult.message : cartError || purchaseResult.message}</div>}
        <p className="pd-purchase-note">Giá và tồn kho được kiểm tra lại khi thêm vào giỏ. Giỏ hàng chưa giữ chỗ sản phẩm.</p>
        {(recommendations.length > 0 || cautions.length > 0) && <div className="pd-buy-guidance">{recommendations.length > 0 && <div><Sparkles size={16} /><span>Loại da được gợi ý: {recommendations.map(skinName).filter(Boolean).join(', ')}</span></div>}{cautions.length > 0 && <div><Info size={16} /><span>{cautions.map(item => [skinName(item), item.reason].filter(Boolean).join(': ')).join(' · ')}</span></div>}</div>}
      </section>
    </div>
    <nav ref={navRef} className="product-section-nav pd-section-nav" aria-label="Nội dung chi tiết sản phẩm">{SECTIONS.map(([id, label]) => <a key={id} href={`#/product/${productId}#${id}`} aria-current={activeSection === id ? 'location' : undefined} className={activeSection === id ? 'active' : ''} onClick={event => navigateSection(event, id)}>{label}</a>)}</nav>
    <div className="product-content-sections pd-content">
      <section id="product-overview" className="product-content-section pd-overview" tabIndex={-1}><header className="product-section-heading"><h2>Thông tin sản phẩm</h2></header><div className="product-section-body pd-overview-grid"><div><p className="pd-eyebrow">THÔNG TIN CÔNG KHAI</p><h3>Về {readyProduct.name}</h3><p className="pd-copy">{readyProduct.description || readyProduct.shortDescription || 'Mô tả sản phẩm đang được cập nhật.'}</p><div className="pd-category-links">{categories.map(category => <a key={category.id} href={`#/products?categoryId=${category.id}`} onClick={event => { event.preventDefault(); onNavigate(`products?categoryId=${category.id}`); }}>{category.name}</a>)}</div><h3>Phù hợp với ai?</h3>{recommendations.length ? <div className="pd-skin-list">{recommendations.map((item, index) => <div key={item.skinTypeId || index}><strong>{skinName(item)}</strong>{item.score != null && <small>Điểm phù hợp trong dữ liệu: {item.score}</small>}</div>)}</div> : <p className="pd-muted">Chưa có gợi ý loại da trong dữ liệu sản phẩm.</p>}{(readyProduct.skinConcerns || []).length > 0 && <div className="pd-concern-list"><h4>Nhu cầu da được ghi nhận</h4>{readyProduct.skinConcerns.map((item, index) => <span key={item.skinConcernId || index}>{item.name || item.skinConcernName || item.code}{item.notes && <small className="pd-concern-note">{item.notes}</small>}</span>)}</div>}{cautions.length > 0 && <div className="pd-cautions"><h4>Lưu ý về loại da</h4>{cautions.map((item, index) => <p key={item.skinTypeId || index}><strong>{skinName(item)}:</strong> {item.reason || 'Chưa bổ sung lý do trong dữ liệu.'}</p>)}</div>}</div><aside className="pd-specification"><h3>Quy cách & công thức</h3><dl><div><dt>Thương hiệu</dt><dd>{brand || 'Chưa cập nhật'}</dd></div><div><dt>Xuất xứ sản phẩm</dt><dd>{readyProduct.originCountry || 'Chưa cập nhật'}</dd></div><div><dt>Quy cách SKU đang chọn</dt><dd>{selectedVariant ? variantLabel(selectedVariant) : 'Chưa cập nhật'}</dd></div><div><dt>Mã SKU</dt><dd>{selectedVariant?.sku || 'Chưa cập nhật'}</dd></div>{readyProduct.skinType && <div><dt>Loại da được công bố</dt><dd>{SKIN_LABELS[readyProduct.skinType] || readyProduct.skinType}</dd></div>}<div><dt>Hương liệu</dt><dd>{formula(readyProduct.hasFragrance)}</dd></div><div><dt>Cồn khô</dt><dd>{formula(readyProduct.hasAlcohol)}</dd></div>{attrs.map((attribute, index) => <div key={attribute.id || index}><dt>{attribute.attributeDefinitionName}{attribute.productVariantId != null ? ' (SKU)' : ''}</dt><dd>{attribute.dataType === 'BOOLEAN' ? attribute.value === 'true' ? 'Có' : attribute.value === 'false' ? 'Không' : 'Chưa công bố' : attribute.value || 'Chưa cập nhật'}</dd></div>)}</dl></aside></div></section>
      <section id="product-ingredients" className="product-content-section pd-ingredients" tabIndex={-1}><header className="product-section-heading"><h2>Thành phần & hoạt chất</h2><p>Dữ liệu thành phần được công bố trong hồ sơ sản phẩm.</p></header><div className="product-section-body"><IngredientList label="Hoạt chất nổi bật" items={textList(readyProduct.ingredientSummary?.keyActives)} />{readyProduct.keyActivesSummary && <p className="pd-copy pd-actives-summary">{readyProduct.keyActivesSummary}</p>}{ingredients.length > 0 && <div className="pd-ingredient-grid">{ingredients.map((ingredient, index) => <article key={ingredient.ingredientId || index}><div className="pd-ingredient-heading"><FlaskConical size={17} /><h3>{ingredient.name || ingredient.inciName || 'Thành phần'}</h3>{ingredient.isKeyActive && <span>Nổi bật</span>}</div>{ingredient.inciName && <p className="pd-inci-name">INCI: {ingredient.inciName}</p>}{ingredient.concentration != null && <p className="pd-concentration">Nồng độ công bố: {ingredient.concentration}{ingredient.concentrationUnit || ' (chưa công bố đơn vị)'}</p>}{textList(ingredient.function).length > 0 && <p><strong>Chức năng:</strong> {textList(ingredient.function).join(', ')}</p>}{textList(ingredient.benefits).length > 0 && <ul>{textList(ingredient.benefits).map(value => <li key={value}>{value}</li>)}</ul>}{textList(ingredient.potentialConcerns).length > 0 && <div className="pd-ingredient-cautions"><strong>Lưu ý:</strong><ul>{textList(ingredient.potentialConcerns).map(value => <li key={value}>{value}</li>)}</ul></div>}</article>)}</div>}{!ingredients.length && !readyProduct.keyActivesSummary && !textList(readyProduct.ingredientSummary?.keyActives).length && <p className="pd-muted">Thông tin hoạt chất đang được cập nhật.</p>}<div className="pd-functional-groups"><IngredientList label="Nhóm cấp ẩm" items={textList(readyProduct.ingredientSummary?.hydratingIngredients)} /><IngredientList label="Nhóm tẩy tế bào chết" items={textList(readyProduct.ingredientSummary?.exfoliatingIngredients)} /></div><div className="pd-inci-accordion"><button type="button" aria-expanded={inciOpen} aria-controls="pd-full-inci" onClick={() => setInciOpen(previous => !previous)}>Bảng thành phần nguyên văn (INCI)<ChevronDown size={18} className={inciOpen ? 'rotated' : ''} /></button><div id="pd-full-inci" hidden={!inciOpen}><p className="pd-copy">{typeof readyProduct.ingredients === 'string' && readyProduct.ingredients.trim() ? readyProduct.ingredients : 'Bảng thành phần nguyên văn đang được cập nhật.'}</p><small>Danh sách thành phần được ánh xạ ở trên không thay thế bảng INCI nguyên văn.</small></div></div></div></section>
      <section id="product-usage" className="product-content-section pd-usage" tabIndex={-1}><header className="product-section-heading"><h2>Hướng dẫn sử dụng</h2></header><div className="product-section-body"><div className="pd-usage-meta">{whenToUse.length > 0 && <div><h3>Thời điểm sử dụng</h3><div className="pd-tags">{whenToUse.map(value => <span key={value}>{value}</span>)}</div></div>}{frequency && <div><h3>Tần suất</h3><p>{frequency}</p></div>}</div>{instructions.length > 0 && <div className="pd-usage-steps"><h3>Các bước hướng dẫn</h3><ol>{instructions.map(value => <li key={value}>{value}</li>)}</ol></div>}{showRawUsage && <div className="pd-raw-usage"><h3>Hướng dẫn nguyên văn</h3><p className="pd-copy">{rawUsage}</p></div>}{warnings.length > 0 && <div className="pd-cautions"><h3>Cảnh báo sử dụng</h3><ul>{warnings.map(value => <li key={value}>{value}</li>)}</ul></div>}{!whenToUse.length && !frequency && !instructions.length && !rawUsage && !warnings.length && <p className="pd-muted">Hướng dẫn sử dụng đang được cập nhật.</p>}</div></section>
      <ProductReviews key={readyProduct.id} product={readyProduct} isAuthenticated={isAuthenticated} onNavigate={onNavigate} />
    </div>
    <section className="pd-related" aria-label="Sản phẩm tương tự"><header><div><p className="pd-eyebrow">KHÁM PHÁ THÊM</p><h2>Sản phẩm tương tự</h2></div><a href="#/products" onClick={event => { event.preventDefault(); onNavigate('products'); }}>Xem danh mục <ArrowRight size={15} /></a></header>{relatedLoading ? <p role="status" className="pd-muted">Đang tải sản phẩm tương tự…</p> : relatedError ? <div className="pd-aux-error" role="alert"><p>{relatedError}</p><button type="button" className="pd-text-button" onClick={reloadRelated}>Thử lại sản phẩm tương tự</button></div> : related.length ? <div className="pd-related-grid">{related.map(item => <ProductCard key={item.id} product={item} onNavigate={onNavigate} />)}</div> : <p className="pd-muted">Chưa có sản phẩm tương tự trong dữ liệu hiện tại.</p>}</section>
    <div className="pd-mobile-purchase" aria-label="Thanh mua sản phẩm"><div><small>{variantLabel(selectedVariant)}</small><strong>{price == null ? 'Chưa có giá' : formatCurrency(price)}</strong></div><button type="button" className="btn-luxury-outline" aria-label="Thêm vào giỏ" disabled={buyDisabled} onClick={() => purchase(false)}>{adding ? <Loader2 size={17} className="animate-spin" /> : <ShoppingBag size={17} />}</button><button type="button" className="btn-luxury-primary" disabled={buyDisabled} onClick={() => purchase(true)}>Mua ngay</button></div>
    {zoomOpen && <div className="pd-zoom-backdrop" onClick={event => { if (event.target === event.currentTarget) setZoomOpen(false); }}><div ref={zoomRef} role="dialog" aria-modal="true" aria-label="Ảnh sản phẩm" tabIndex={-1} className="pd-zoom-dialog"><button type="button" aria-label="Đóng ảnh phóng to" onClick={() => setZoomOpen(false)}><X size={22} /></button>{image(selectedImage)}<p>{selectedImage?.altText || readyProduct.name}</p></div></div>}
  </div>;
};
