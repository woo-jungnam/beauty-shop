import React, { useState, useEffect, useRef } from 'react';
import {
  Star,
  ShoppingBag,
  ShieldCheck,
  Truck,
  RotateCcw,
  Sparkles,
  CheckCircle2,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  Check,
  Award,
  Layers,
  Info
} from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { apiClient } from '../../shared/api/client';
import { useCustomerCart } from '../stores/customerCartStore';
import { ProductCard, resolveProductImage } from '../components/product/ProductCard';
import { cleanDisplayName, resolveMediaUrl } from '../../shared/utils/formatters';
import { getGuestSessionId } from '../../shared/utils/session';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

const formatReviewDate = (value) => {
  if (!value) return '';
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value));
};

const PRODUCT_IMAGE_FALLBACK = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85';

const getReliableProductImage = (url) => {
  if (!url) return PRODUCT_IMAGE_FALLBACK;
  if (url.startsWith('/') && !url.startsWith('//')) {
    return resolveMediaUrl(url);
  }
  return url;
};

const SKIN_TYPE_LABELS = {
  OILY: 'Da dầu',
  DRY: 'Da khô',
  COMBINATION: 'Da hỗn hợp',
  SENSITIVE: 'Da nhạy cảm',
  NORMAL: 'Da thường',
  ALL: 'Mọi loại da',
};

const FRIENDLY_SKIN_TYPES = {
  OILY: 'Da dầu',
  DRY: 'Da khô',
  COMBINATION: 'Da hỗn hợp',
  SENSITIVE: 'Da nhạy cảm',
  NORMAL: 'Da thường',
  ALL: 'Mọi loại da',
  OSPW: 'Da dầu mụn, nhạy cảm',
  OSPT: 'Da dầu, dễ sạm nắng',
  OSNW: 'Da dầu mụn, không nhạy cảm',
  OSNT: 'Da dầu khỏe',
  ORPW: 'Da dầu lão hóa, có mụn',
  ORPT: 'Da dầu lão hóa',
  ORNW: 'Da dầu có đốm thâm',
  ORNT: 'Da dầu mụn nhẹ',
  DSPW: 'Da khô nhạy cảm, dễ mụn',
  DSPT: 'Da khô nhạy cảm, dễ bắt nắng',
  DSNW: 'Da khô mụn, không đều màu',
  DSNT: 'Da khô nhạy cảm',
  DRPW: 'Da khô lão hóa',
  DRPT: 'Da khô xỉn màu',
  DRNW: 'Da khô thâm sạm',
  DRNT: 'Da khô khỏe mạnh',
};

const getFriendlySkinType = (code, fallbackName) => {
  if (code && FRIENDLY_SKIN_TYPES[code]) return FRIENDLY_SKIN_TYPES[code];
  if (fallbackName && FRIENDLY_SKIN_TYPES[fallbackName]) return FRIENDLY_SKIN_TYPES[fallbackName];
  return fallbackName || code || 'Mọi loại da';
};

const getVariantLabel = (variant) => {
  if (variant.volume) return variant.volume;
  if (variant.volumeValue) return `${variant.volumeValue}${variant.volumeUnit || 'ml'}`;
  if (variant.color && variant.color !== 'Tiêu chuẩn') return variant.color;
  return variant.variantName || 'Tiêu chuẩn';
};

export const ProductDetailPage = ({ productId, onNavigate }) => {
  const { addItem } = useCustomerCart();
  const { isAuthenticated } = useAuth();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedVariant, setSelectedVariant] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [selectedImage, setSelectedImage] = useState('');
  const [addedNotice, setAddedNotice] = useState(false);
  const [reviews, setReviews] = useState([]);
  const [reviewTotal, setReviewTotal] = useState(0);
  const [eligibleReviewOrders, setEligibleReviewOrders] = useState([]);
  const [reviewForm, setReviewForm] = useState({ orderId: '', rating: 5, title: '', content: '' });
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewSuccess, setReviewSuccess] = useState('');
  const [reviewError, setReviewError] = useState('');
  const [submittedOrderIds, setSubmittedOrderIds] = useState([]);
  const [relatedProducts, setRelatedProducts] = useState([]);
  const [activeSection, setActiveSection] = useState('product-overview');
  const [showFullInci, setShowFullInci] = useState(false);
  const navRef = useRef(null);

  useEffect(() => {
    const fetchProduct = async () => {
      setLoading(true);
      setReviews([]);
      setReviewTotal(0);
      setEligibleReviewOrders([]);
      setReviewForm({ orderId: '', rating: 5, title: '', content: '' });
      setReviewSuccess('');
      setReviewError('');
      setSubmittedOrderIds([]);
      setRelatedProducts([]);
      setQuantity(1);
      try {
        const res = await apiClient.get(`/api/v1/products/${productId}`);
        const data = res?.data || res;
        setProduct(data);
        if (data.variants?.length > 0) {
          const activeVariants = data.variants.filter((variant) => variant.isActive !== false && !variant.isDeleted);
          const matchedByBasePrice = activeVariants.find((variant) =>
            Number(variant.discountPrice || variant.price) === Number(data.basePrice)
          );
          const defaultVariant = activeVariants.find((variant) => variant.isDefault);
          const lowestVariant = [...activeVariants].sort((a, b) => Number(a.price || 0) - Number(b.price || 0))[0];

          setSelectedVariant(matchedByBasePrice || defaultVariant || lowestVariant || data.variants[0]);
        }
        const initialImg = resolveProductImage(data);
        setSelectedImage(initialImg);

        // Ghi nhận tương tác VIEW phục vụ RecSys
        try {
          const guestSessionId = getGuestSessionId();
          apiClient.post('/api/v1/products/tracking/interaction', {
            productId: Number(productId),
            actionType: 'VIEW',
            sessionId: guestSessionId,
          }).catch(() => {});
        } catch {}

        const variantIds = new Set((data.variants || []).map((variant) => Number(variant.id)).filter(Boolean));
        const [reviewResult, similarResult, orderResult] = await Promise.allSettled([
          apiClient.get(`/api/v1/reviews?productId=${productId}&size=10&sort=createdAt,desc`),
          apiClient.get(`/api/v1/products/${productId}/similar?limit=8`),
          isAuthenticated ? apiClient.get('/api/v1/orders/my-orders?size=100&sort=createdAt,desc') : Promise.resolve(null),
        ]);

        if (reviewResult.status === 'fulfilled') {
          const reviewPage = reviewResult.value?.data || reviewResult.value;
          setReviews(reviewPage?.content || []);
          setReviewTotal(Number(reviewPage?.totalElements || 0));
        }

        if (similarResult.status === 'fulfilled') {
          const similarData = similarResult.value?.data || similarResult.value || [];
          const list = Array.isArray(similarData) ? similarData : (similarData?.content || []);
          setRelatedProducts(list.filter((item) => String(item.id) !== String(productId)).slice(0, 8));
        }

        if (orderResult.status === 'fulfilled' && orderResult.value) {
          const orderPage = orderResult.value?.data || orderResult.value;
          const eligible = (orderPage?.content || [])
            .filter((order) => order.status === 'DELIVERED')
            .filter((order) => (order.items || []).some((item) => variantIds.has(Number(item.variantId))));
          setEligibleReviewOrders(eligible);
          if (eligible[0]?.id) {
            setReviewForm((prev) => ({ ...prev, orderId: String(eligible[0].id) }));
          }
        }
      } catch (err) {
        console.error('Failed to load product detail', err);
      } finally {
        setLoading(false);
      }
    };
    if (productId) fetchProduct();
  }, [productId, isAuthenticated]);

  useEffect(() => {
    const sectionIds = ['product-overview', 'product-ingredients', 'product-usage', 'product-reviews'];

    const handleScroll = () => {
      const scrollBottom = window.innerHeight + window.scrollY;
      const docHeight = document.documentElement.scrollHeight;
      if (docHeight - scrollBottom < 80) {
        setActiveSection('product-reviews');
        return;
      }

      const headerEl = document.querySelector('.customer-header');
      const navEl = document.querySelector('.product-section-nav');
      const headerH = headerEl ? headerEl.offsetHeight : 144;
      const navH = navEl ? navEl.offsetHeight : 48;
      const threshold = headerH + navH + 20;

      let current = 'product-overview';

      for (let i = 0; i < sectionIds.length; i++) {
        const el = document.getElementById(sectionIds[i]);
        if (el) {
          const rect = el.getBoundingClientRect();
          if (rect.top <= threshold) {
            current = sectionIds[i];
          }
        }
      }
      setActiveSection(current);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();

    return () => window.removeEventListener('scroll', handleScroll);
  }, [product, reviewTotal]);

  if (loading) {
    return (
      <div className="customer-container product-skeleton-frame" style={{ padding: '60px 20px', minHeight: '60vh' }}>
        <div className="product-detail-loading-grid" style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.2fr)', gap: '40px' }}>
          <div style={{ height: '480px', borderRadius: 'var(--radius-md)' }} className="skeleton-shimmer product-card-skeleton" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div style={{ height: '24px', width: '40%', borderRadius: '4px' }} className="skeleton-shimmer" />
            <div style={{ height: '44px', width: '90%', borderRadius: '4px' }} className="skeleton-shimmer" />
            <div style={{ height: '36px', width: '50%', borderRadius: '4px' }} className="skeleton-shimmer" />
            <div style={{ height: '140px', width: '100%', borderRadius: '6px' }} className="skeleton-shimmer product-card-skeleton" />
            <div style={{ height: '52px', width: '60%', borderRadius: '4px' }} className="skeleton-shimmer" />
          </div>
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="customer-container" style={{ padding: '80px 20px', textAlign: 'center' }}>
        <h2>Không tìm thấy sản phẩm</h2>
        <p>Sản phẩm này có thể đã ngừng kinh doanh hoặc đường dẫn không chính xác.</p>
        <button onClick={() => onNavigate('products')} className="btn-luxury-primary" style={{ marginTop: '16px' }}>
          Quay lại danh mục sản phẩm
        </button>
      </div>
    );
  }

  const variantPrice = Number(selectedVariant?.price || product.basePrice || product.price || 0);
  const price = Number(selectedVariant?.discountPrice || variantPrice);
  const originalPrice = Number(selectedVariant?.originalPrice || variantPrice || price);
  const discountPercent = originalPrice > price 
    ? Math.round(((originalPrice - price) / originalPrice) * 100) 
    : 0;

  const brandName = product.brandName || product.brand?.name || 'BeautyShop';
  const variants = (product.variants || []).filter((variant) => variant.isActive !== false);
  const defaultImg = resolveProductImage(product);
  const images = product.images?.length > 0 ? product.images : [{ imageUrl: defaultImg }];
  const reviewCount = reviewTotal || Number(product.totalReviews ?? product.reviewCount ?? 0);
  const soldCount = Number(product.totalSold ?? product.soldCount ?? 0);
  const stockQuantity = selectedVariant?.stockQuantity;
  const isInStock = stockQuantity == null || stockQuantity > 0;
  const recommendedSkinTypes = product.skinCompatibility?.recommendedSkinTypes || [];
  const keyActives = product.ingredientSummary?.keyActives || [];
  const usageInstructions = [
    ...(Array.isArray(product.usage?.whenToUse) ? product.usage.whenToUse.map((item) => `Thời điểm dùng: ${item}`) : []),
    ...(Array.isArray(product.usage?.instructions) ? product.usage.instructions : []),
    ...(Array.isArray(product.usageInstructions) ? product.usageInstructions : []),
    ...(Array.isArray(product.instructions) ? product.instructions : []),
  ].filter(Boolean);
  const usageText = product.howToUse || product.usageGuide || product.usageText || product.usageDescription || product.guide || '';

  const handleAddToCart = () => {
    if (!isInStock) return;
    addItem(product, selectedVariant, quantity);
    setAddedNotice(true);
    setTimeout(() => setAddedNotice(false), 2000);
    try {
      apiClient.post('/api/v1/products/tracking/interaction', {
        productId: Number(productId),
        actionType: 'ADD_TO_CART',
        sessionId: getGuestSessionId(),
      }).catch(() => {});
    } catch {}
  };

  const handleBuyNow = () => {
    if (!isInStock) return;
    addItem(product, selectedVariant, quantity);
    try {
      apiClient.post('/api/v1/products/tracking/interaction', {
        productId: Number(productId),
        actionType: 'ADD_TO_CART',
        sessionId: getGuestSessionId(),
      }).catch(() => {});
    } catch {}
    onNavigate('checkout');
  };

  const handleReviewSubmit = async (event) => {
    event.preventDefault();
    setReviewError('');
    setReviewSuccess('');

    const content = reviewForm.content.trim();
    const title = reviewForm.title.trim();
    const canRate = availableReviewOrders.length > 0;
    const rating = canRate ? Number(reviewForm.rating) : null;
    const orderId = canRate ? Number(reviewForm.orderId) : null;

    if (canRate && !orderId) {
      setReviewError('Vui lòng chọn đơn hàng đã giao.');
      return;
    }
    if (canRate && (rating < 1 || rating > 5)) {
      setReviewError('Số sao phải từ 1 đến 5.');
      return;
    }
    if (!content || content.length > 2000) {
      setReviewError('Nội dung bình luận không được trống và tối đa 2000 ký tự.');
      return;
    }

    setReviewSubmitting(true);
    try {
      const payload = { productId: Number(productId), title: title || null, content };
      if (canRate) Object.assign(payload, { orderId, rating });
      await apiClient.post('/api/v1/reviews', payload);
      const nextOrder = canRate ? eligibleReviewOrders.find((order) => Number(order.id) !== orderId && !submittedOrderIds.includes(Number(order.id))) : null;
      if (canRate) setSubmittedOrderIds((ids) => [...new Set([...ids, orderId])]);
      setReviewForm((prev) => ({ ...prev, orderId: nextOrder?.id ? String(nextOrder.id) : '', rating: 5, title: '', content: '' }));
      setReviewSuccess(canRate ? 'Đánh giá đã gửi và đang chờ duyệt.' : 'Bình luận đã gửi và đang chờ duyệt.');
    } catch (err) {
      setReviewError(err.message || 'Không thể gửi đánh giá.');
    } finally {
      setReviewSubmitting(false);
    }
  };

  const availableReviewOrders = eligibleReviewOrders.filter((order) => !submittedOrderIds.includes(Number(order.id)));

  const handleNavClick = (e, sectionId) => {
    e.preventDefault();
    setActiveSection(sectionId);
    const element = document.getElementById(sectionId);
    if (element) {
      const headerEl = document.querySelector('.customer-header');
      const navEl = document.querySelector('.product-section-nav');
      const headerH = headerEl ? headerEl.offsetHeight : 144;
      const navH = navEl ? navEl.offsetHeight : 48;
      const navOffset = headerH + navH + 12;

      const targetY = element.getBoundingClientRect().top + window.pageYOffset - navOffset;
      window.scrollTo({
        top: Math.max(0, targetY),
        behavior: 'smooth',
      });
      window.history.replaceState(null, '', `#/product/${productId}#${sectionId}`); // ponytail: single in-page hash anchor; switch to router state when replacing hash routing.
    }
  };

  return (
    <div className="customer-container product-detail-page" style={{ padding: '24px 20px 80px 20px' }}>
      {/* Breadcrumb */}
      <div className="product-breadcrumb" style={{
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        fontSize: '12px',
        color: 'var(--c-text-light)',
        marginBottom: '24px'
      }}>
        <span style={{ cursor: 'pointer' }} onClick={() => onNavigate('')}>Trang chủ</span>
        <ChevronRight size={12} />
        <span style={{ cursor: 'pointer' }} onClick={() => onNavigate('products')}>Mỹ phẩm</span>
        <ChevronRight size={12} />
        <span style={{ color: 'var(--c-gold-hover)', fontWeight: 600 }}>{cleanDisplayName(brandName)}</span>
      </div>

      <div className="product-mobile-summary">
        <div className="product-brand-label">{cleanDisplayName(brandName)}</div>
        <h1>{product.name}</h1>
        <div className="product-summary-meta">
          {reviewCount > 0 ? (
            <span><Star size={14} fill="#EAB308" color="#EAB308" /> {Number(product.averageRating || 0).toFixed(1)} ({reviewCount})</span>
          ) : (
            <span>Chưa có đánh giá</span>
          )}
          <span>•</span>
          <span>{soldCount > 0 ? `Đã bán ${soldCount}` : 'Sản phẩm mới'}</span>
        </div>
      </div>

      {/* Main 2-Column Product Detail Layout */}
      <div className="product-detail-main" style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: '48px',
        alignItems: 'start'
      }}>
        {/* ================= LEFT GALLERY COLUMN ================= */}
        <div className="product-gallery-column">
          {/* Main Selected Image */}
          <div style={{
            width: '100%',
            paddingTop: '100%',
            position: 'relative',
            backgroundColor: '#F8F6F2',
            borderRadius: 'var(--radius-lg)',
            overflow: 'hidden',
            border: '1px solid var(--c-border-subtle)'
          }}>
            {selectedImage ? (
              <img 
                src={selectedImage} 
                alt={product.name} 
                onError={(event) => {
                  event.currentTarget.onerror = null;
                  event.currentTarget.src = PRODUCT_IMAGE_FALLBACK;
                }}
                style={{
                  position: 'absolute',
                  inset: 0,
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  transition: 'transform 0.3s ease'
                }}
              />
            ) : (
              <div style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--c-gold)'
              }}>
                <Sparkles size={48} />
              </div>
            )}

            {/* Discount Badge */}
            {discountPercent > 0 && (
              <div style={{
                position: 'absolute',
                top: '16px',
                left: '16px',
                backgroundColor: 'var(--c-primary)',
                color: '#FFFFFF',
                fontSize: '12px',
                fontWeight: 700,
                padding: '4px 10px',
                borderRadius: '9999px',
                fontFamily: 'var(--font-mono)'
              }}>
                -{discountPercent}%
              </div>
            )}
          </div>

          {/* Thumbnails Row */}
          {images.length > 1 && (
            <div style={{ display: 'flex', gap: '10px', marginTop: '14px', overflowX: 'auto', paddingBottom: '6px' }}>
              {images.map((img, idx) => (
                <div 
                  key={idx}
                  onClick={() => setSelectedImage(img.imageUrl)}
                  style={{
                    width: '68px',
                    height: '68px',
                    borderRadius: 'var(--radius-sm)',
                    overflow: 'hidden',
                    border: selectedImage === img.imageUrl ? '2px solid var(--c-gold)' : '1px solid var(--c-border)',
                    cursor: 'pointer',
                    flexShrink: 0
                  }}
                >
                  <img
                    src={getReliableProductImage(img.imageUrl)}
                    alt={`${product.name} - ${idx + 1}`}
                    onError={(event) => {
                      event.currentTarget.onerror = null;
                      event.currentTarget.src = PRODUCT_IMAGE_FALLBACK;
                    }}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                </div>
              ))}
            </div>
          )}

        </div>

        {/* ================= RIGHT INFO & PURCHASE COLUMN ================= */}
        <div className="product-purchase-column">
          {/* Brand */}
          <div className="product-brand-label product-desktop-summary" style={{
            fontSize: '13px',
            fontWeight: 700,
            color: 'var(--c-gold-hover)',
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
            marginBottom: '6px'
          }}>
            {cleanDisplayName(brandName)}
          </div>

          {/* Title */}
          <h1 className="product-desktop-summary" style={{
            fontSize: '28px',
            margin: '0 0 14px 0',
            lineHeight: 1.25,
            color: 'var(--c-primary)',
            fontFamily: 'var(--font-serif)'
          }}>
            {product.name}
          </h1>

          {/* Ratings & Sold Stats */}
          <div className="product-rating-row product-desktop-summary" style={{
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            fontSize: '13px',
            color: 'var(--c-text-light)',
            marginBottom: '20px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              {reviewCount > 0 ? (
                <>
                  <Star size={15} fill="#EAB308" color="#EAB308" />
                  <strong style={{ color: 'var(--c-primary)' }}>{Number(product.averageRating || 0).toFixed(1)}</strong>
                  <span>({reviewCount} nhận xét)</span>
                </>
              ) : (
                <span>Chưa có đánh giá</span>
              )}
            </div>
            <span>•</span>
            <span style={{ color: 'var(--c-safe-green)', fontWeight: 600 }}>
              {soldCount > 0 ? `Đã bán ${soldCount} sản phẩm` : 'Sản phẩm mới'}
            </span>
          </div>

          {(product.shortDescription || product.description) && (
            <p className="product-short-description">
              {product.shortDescription || product.description}
            </p>
          )}

          <div className="product-benefit-list">
            {recommendedSkinTypes.length > 0 && (
              <div>
                <CheckCircle2 size={16} />
                <span>Phù hợp: {recommendedSkinTypes.slice(0, 3).map((skin) => skin.name || SKIN_TYPE_LABELS[skin.code] || skin.code).join(', ')}</span>
              </div>
            )}
            {keyActives.length > 0 && (
              <div>
                <CheckCircle2 size={16} />
                <span>Hoạt chất chính: {keyActives.slice(0, 3).join(', ')}</span>
              </div>
            )}
            {product.hasFragrance != null && (
              <div>
                <CheckCircle2 size={16} />
                <span>{product.hasFragrance ? 'Có chứa hương liệu' : 'Không chứa hương liệu'}</span>
              </div>
            )}
          </div>

          {/* Pricing Box - Hasaki Red Accent */}
          <div style={{
            backgroundColor: '#FFFFFF',
            padding: '20px 24px',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--c-border-subtle)',
            marginBottom: '24px'
          }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px' }}>
              <span style={{
                fontSize: '28px',
                fontWeight: 800,
                color: 'var(--c-deal-red, #E11D48)',
                fontFamily: 'var(--font-mono)'
              }}>
                {formatCurrency(price)}
              </span>

              {originalPrice > price && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                  <span style={{
                    fontSize: '15px',
                    color: '#9CA3AF',
                    textDecoration: 'line-through',
                    fontFamily: 'var(--font-mono)'
                  }}>
                    {formatCurrency(originalPrice)}
                  </span>
                  <span style={{
                    fontSize: '12px',
                    color: 'var(--c-deal-red, #E11D48)',
                    fontWeight: 800,
                    backgroundColor: '#FFE4E6',
                    padding: '2px 8px',
                    borderRadius: '4px'
                  }}>
                    Tiết kiệm {formatCurrency(originalPrice - price)} ({discountPercent}%)
                  </span>
                </div>
              )}
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '12px',
              color: 'var(--c-primary, #D45D79)',
              fontWeight: 700,
              marginTop: '10px'
            }}>
              <Truck size={14} />
              <span>Giao Nhanh 2H NowFree nội thành • Miễn phí vận chuyển từ 249K</span>
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '12px',
              color: 'var(--c-primary)',
              marginTop: '6px'
            }}>
              <Sparkles size={14} />
              <span>Tích lũy <strong>+{Math.round(price / 10000)} điểm VIP</strong> khi hoàn tất đơn hàng</span>
            </div>
          </div>

          {/* Variants Selector */}
          {variants.length > 0 && (
            <div style={{ marginBottom: '24px' }}>
              <label style={{ fontSize: '13px', fontWeight: 700, display: 'block', marginBottom: '10px', color: '#1F2937' }}>
                CHỌN DUNG TÍCH / PHÂN LOẠI:
              </label>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                {variants.map((v) => {
                  const active = selectedVariant?.id === v.id;
                  const label = getVariantLabel(v);
                  const variantSellingPrice = Number(v.discountPrice || v.price || 0);
                  return (
                    <button
                      key={v.id}
                      onClick={() => {
                        setSelectedVariant(v);
                        setQuantity(1);
                      }}
                      style={{
                        padding: '10px 18px',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: '13px',
                        fontWeight: 600,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        border: active ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                        backgroundColor: active ? 'var(--c-primary-light, #FFF0F3)' : '#FFFFFF',
                        color: active ? 'var(--c-primary)' : 'var(--c-text-main)'
                      }}
                    >
                      {label} · {formatCurrency(variantSellingPrice)}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Stock information from the selected public variant */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '13px',
            marginBottom: '24px',
            color: 'var(--c-text-muted)'
          }}>
            <span className={`badge-dermatology ${isInStock ? 'safe' : 'amber'}`} style={{ fontSize: '11px' }}>
              {isInStock ? 'CÒN HÀNG' : 'TẠM HẾT HÀNG'}
            </span>
            {stockQuantity != null && <span>Còn {stockQuantity} sản phẩm ở biến thể đã chọn</span>}
          </div>

          {/* Quantity Selector & Action Buttons */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div className="product-primary-actions" style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                border: '1.5px solid var(--c-border)',
                borderRadius: 'var(--radius-pill)',
                height: '44px',
                padding: '0 6px',
                backgroundColor: '#FFFFFF'
              }}>
                <button 
                  onClick={() => setQuantity(Math.max(1, quantity - 1))}
                  style={{ width: '32px', height: '100%', background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '16px', fontWeight: 700 }}
                >
                  -
                </button>
                <span style={{ padding: '0 12px', fontSize: '15px', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                  {quantity}
                </span>
                <button
                  onClick={() => setQuantity(stockQuantity == null ? quantity + 1 : Math.min(stockQuantity, quantity + 1))}
                  disabled={!isInStock || (stockQuantity != null && quantity >= stockQuantity)}
                  style={{ width: '32px', height: '100%', background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '16px', fontWeight: 700 }}
                >
                  +
                </button>
              </div>

              {/* Add to Cart */}
              <button 
                onClick={handleAddToCart}
                disabled={!isInStock}
                className="btn-luxury-primary"
                style={{
                  flex: 1,
                  height: '44px',
                  fontSize: '14px',
                  backgroundColor: addedNotice ? 'var(--c-safe-green)' : undefined,
                  borderColor: addedNotice ? 'var(--c-safe-green)' : 'var(--c-border)',
                  color: '#FFFFFF'
                }}
              >
                {addedNotice ? (
                  <>
                    <Check size={16} />
                    <span>Đã Thêm Vào Giỏ Hàng!</span>
                  </>
                ) : (
                  <>
                    <ShoppingBag size={16} />
                    <span>{isInStock ? 'Thêm Vào Giỏ Hàng' : 'Tạm Hết Hàng'}</span>
                  </>
                )}
              </button>
            </div>

            {/* Buy Now Direct Button */}
            <button 
              onClick={handleBuyNow}
              disabled={!isInStock}
              className="btn-luxury-outline"
              style={{
                width: '100%',
                height: '46px',
                fontSize: '15px'
              }}
            >
              <span>Mua Ngay</span>
            </button>
          </div>

          <div className="product-trust-grid">
            <div>
              <ShieldCheck size={18} />
              <strong>Cam kết chính hãng</strong>
              <span>Thông tin sản phẩm minh bạch</span>
            </div>
            <div>
              <RotateCcw size={18} />
              <strong>Đổi trả trong 7 ngày</strong>
              <span>Theo điều kiện đổi trả áp dụng</span>
            </div>
            <div>
              <Truck size={18} />
              <strong>Thông tin vận chuyển</strong>
              <span>Hiển thị tại bước thanh toán</span>
            </div>
          </div>
        </div>
      </div>

      <div className="product-quick-specs">
        <div><span>Loại da phù hợp</span><strong>{recommendedSkinTypes[0]?.name || SKIN_TYPE_LABELS[product.skinType] || 'Chưa cập nhật'}</strong></div>
        <div><span>Hoạt chất nổi bật</span><strong>{keyActives.slice(0, 2).join(', ') || product.keyActivesSummary || 'Chưa cập nhật'}</strong></div>
        <div><span>Xuất xứ</span><strong>{product.originCountry || 'Chưa cập nhật'}</strong></div>
        <div><span>Tần suất sử dụng</span><strong>{product.usage?.frequency || product.frequency || product.usageFrequency || 'Theo hướng dẫn sản phẩm'}</strong></div>
      </div>

      {/* =========================================================================
          BELOW THE FOLD: SCIENTIFIC INGREDIENTS & DERMATOLOGY DATA
          ========================================================================= */}
      <nav className="product-section-nav" ref={navRef} aria-label="Nội dung chi tiết sản phẩm">
        {[
          { id: 'product-overview', label: 'Công dụng & Xuất xứ' },
          { id: 'product-ingredients', label: 'Thành phần hoạt chất' },
          { id: 'product-usage', label: 'Loại da & Hướng dẫn dùng' },
          { id: 'product-reviews', label: `Đánh giá (${reviewCount})` },
        ].map((item) => (
          <a
            key={item.id}
            href={`#${item.id}`}
            className={activeSection === item.id ? 'active' : ''}
            onClick={(e) => handleNavClick(e, item.id)}
          >
            {item.label}
          </a>
        ))}
      </nav>

      <div className="product-content-sections">
        <section id="product-overview" className="product-content-section">
          <header className="product-section-heading">
            <h2>Mô tả & Công dụng sản phẩm</h2>
          </header>
          <div className="product-section-body product-overview-grid">
            <div>
              <p style={{ fontSize: '15px', color: 'var(--c-text-main)', lineHeight: 1.8, margin: '0 0 20px', whiteSpace: 'pre-line' }}>
                {product.description || product.shortDescription || 'Thông tin mô tả đang được cập nhật.'}
              </p>

              {product.skinConcerns?.length > 0 && (
                <div style={{ marginTop: '20px' }}>
                  <h4 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Sparkles size={16} color="var(--c-gold-hover)" />
                    Hiệu quả cải thiện làn da nổi bật
                  </h4>
                  <div className="product-overview-list">
                    {product.skinConcerns.slice(0, 5).map((concern) => (
                      <div key={concern.concernId || concern.code}>
                        <CheckCircle2 size={16} color="var(--c-safe-green)" />
                        <span><strong>{concern.name}</strong>{concern.notes ? ` — ${concern.notes}` : ''}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <aside className="product-overview-aside">
              <h4>Quy cách & Xuất xứ</h4>
              <dl>
                <div><dt>Thương hiệu</dt><dd><strong>{brandName}</strong></dd></div>
                <div><dt>Xuất xứ thương hiệu</dt><dd>{product.brand?.originCountry || product.originCountry || 'Chính hãng'}</dd></div>
                <div><dt>Nơi sản xuất</dt><dd>{product.originCountry || 'Theo tiêu chuẩn hãng'}</dd></div>
                <div><dt>Dung tích</dt><dd><strong>{selectedVariant?.volume || product.volume || 'Theo phân loại'}</strong></dd></div>
                <div><dt>Phù hợp cho</dt><dd>{SKIN_TYPE_LABELS[product.skinType] || 'Mọi loại da'}</dd></div>
                <div><dt>Hương liệu</dt><dd>{product.hasFragrance == null ? 'Chưa công bố' : product.hasFragrance ? 'Có hương liệu nhẹ' : 'Không chứa hương liệu'}</dd></div>
                <div><dt>Cồn khô</dt><dd>{product.hasAlcohol == null ? 'Chưa công bố' : product.hasAlcohol ? 'Có cồn' : 'Không cồn khô (Alcohol-Free)'}</dd></div>
                {(product.attributeValues || []).filter((attribute) => attribute.productVariantId == null || attribute.productVariantId === selectedVariant?.id).map((attribute) => (
                  <div key={attribute.id}><dt>{attribute.attributeDefinitionName}{attribute.productVariantId != null ? ' (SKU)' : ''}</dt><dd style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{attribute.dataType === 'BOOLEAN' ? (attribute.value === 'true' ? 'Có' : 'Không') : attribute.value}</dd></div>
                ))}
              </dl>

              {product.skinCompatibility?.notIdealFor?.length > 0 && (
                <div className="product-caution-box">
                  <strong>Lưu ý cho da đặc thù</strong>
                  {product.skinCompatibility.notIdealFor.map((item, index) => (
                    <p key={item.skinTypeId || index}>• <strong>{getFriendlySkinType(item.skinType, item.skinType)}:</strong> {item.reason}</p>
                  ))}
                </div>
              )}
            </aside>
          </div>
        </section>

        <section id="product-ingredients" className="product-content-section">
          <header className="product-section-heading">
            <h2>Thành phần & Hoạt chất sinh học</h2>
          </header>
          <div className="product-section-body">
            <p style={{ fontSize: '14px', color: 'var(--c-text-muted)', lineHeight: 1.6, margin: '0 0 20px' }}>
              Công thức sản phẩm được viện da liễu kiểm nghiệm, kết hợp các hoạt chất chuyên sâu giúp nuôi dưỡng và bảo vệ màng da khỏe mạnh.
            </p>

            {/* Key Actives Badges */}
            {product.ingredientSummary?.keyActives?.length > 0 && (
              <div style={{ marginBottom: '24px' }}>
                <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--c-gold-hover)', textTransform: 'uppercase', marginBottom: '12px' }}>
                  Thành phần hoạt tính vàng (Key Actives)
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px' }}>
                  {product.ingredientSummary.keyActives.map((act, i) => (
                    <div key={i} style={{ padding: '12px 14px', backgroundColor: 'var(--c-canvas, #FDFBF7)', border: '1px solid var(--c-border-subtle)', borderRadius: '6px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '4px' }}>
                        <CheckCircle2 size={15} color="var(--c-safe-green)" />
                        <span>{act}</span>
                      </div>
                      <span style={{ fontSize: '12px', color: 'var(--c-text-muted)' }}>
                        Hoạt chất đã được chứng minh hiệu quả lâm sàng cho sức khỏe làn da.
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Collapsible Full INCI section */}
            <div style={{ marginTop: '20px', borderTop: '1px solid var(--c-border-subtle)', paddingTop: '16px' }}>
              <button
                type="button"
                onClick={() => setShowFullInci(!showFullInci)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  width: '100%',
                  padding: '12px 16px',
                  backgroundColor: 'var(--c-canvas, #FDFBF7)',
                  border: '1px solid var(--c-border-subtle)',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  fontWeight: 600,
                  fontSize: '13.5px',
                  color: 'var(--c-primary)'
                }}
              >
                <span>Xem chi tiết bảng thành phần đầy đủ</span>
                {showFullInci ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              </button>

              {showFullInci && (
                <div style={{ overflowX: 'auto', marginTop: '12px' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                    <thead>
                      <tr style={{ backgroundColor: 'var(--c-canvas)', borderBottom: '1px solid var(--c-border)' }}>
                        <th style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 700 }}>Tên thành phần</th>
                        <th style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 700 }}>Nhóm công năng</th>
                        <th style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 700 }}>Lợi ích trên da</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(() => {
                        const list = Array.isArray(product.ingredientsList) && product.ingredientsList.length > 0
                          ? product.ingredientsList
                          : (Array.isArray(product.ingredients) ? product.ingredients : []);

                        if (list.length > 0) {
                          return list.map((ing, i) => (
                            <tr key={i} style={{ borderBottom: '1px solid var(--c-border-subtle)' }}>
                              <td style={{ padding: '10px 14px', fontWeight: 600, color: 'var(--c-primary)' }}>
                                {ing.inciName || ing.name}
                                {ing.concentration != null && <span> — {ing.concentration}{ing.concentrationUnit}</span>}
                                {ing.isKeyActive && (
                                  <span style={{ fontSize: '10px', color: 'var(--c-gold)', marginLeft: '6px', fontWeight: 700 }}>[CHÍNH]</span>
                                )}
                              </td>
                              <td style={{ padding: '10px 14px', color: 'var(--c-text-muted)' }}>
                                {Array.isArray(ing.function) ? ing.function.join(', ') || 'Chưa cập nhật' : (ing.function || 'Chưa cập nhật')}
                              </td>
                              <td style={{ padding: '10px 14px', color: 'var(--c-text-main)' }}>
                                {Array.isArray(ing.benefits) ? ing.benefits.join('; ') || 'Chưa cập nhật' : (ing.benefits || 'Chưa cập nhật')}
                                {ing.potentialConcerns?.length > 0 && <p>Lưu ý: {ing.potentialConcerns.join('; ')}</p>}
                              </td>
                            </tr>
                          ));
                        }

                        if (typeof product.ingredients === 'string' && product.ingredients.trim()) {
                          return (
                            <tr>
                              <td colSpan={3} style={{ padding: '14px', lineHeight: 1.6, color: 'var(--c-text-main)' }}>
                                {product.ingredients}
                              </td>
                            </tr>
                          );
                        }

                        return (
                          <tr>
                            <td colSpan={3} style={{ padding: '16px', textAlign: 'center', color: 'var(--c-text-light)' }}>
                              Đang cập nhật danh mục thành phần từ nhà sản xuất.
                            </td>
                          </tr>
                        );
                      })()}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </section>

        <section id="product-usage" className="product-content-section">
          <header className="product-section-heading">
            <h2>Loại da phù hợp & Hướng dẫn sử dụng</h2>
          </header>
          <div className="product-section-body">
            {/* Friendly Recommended skin types */}
            {product.skinCompatibility?.recommendedSkinTypes?.length > 0 && (
              <div style={{ marginBottom: '24px' }}>
                <h3 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <CheckCircle2 size={16} color="var(--c-safe-green)" />
                  KHUYÊN DÙNG TỐI ƯU CHO LÀN DA:
                </h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                  {product.skinCompatibility.recommendedSkinTypes.map((st, i) => (
                    <div key={i} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '8px 14px', backgroundColor: 'var(--c-safe-green-bg, #ECFDF5)', borderRadius: '6px', border: '1px solid var(--c-safe-green-border, #A7F3D0)', fontSize: '13px', fontWeight: 600, color: 'var(--c-safe-green, #065F46)' }}>
                      <Check size={14} color="var(--c-safe-green, #065F46)" />
                      <span>{getFriendlySkinType(st.code, st.name)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Usage Instructions */}
            <div style={{ marginTop: '20px', paddingTop: '18px', borderTop: '1px solid var(--c-border-subtle)' }}>
              <h3 style={{ fontSize: '15px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '14px' }}>
                Hướng dẫn chu trình dưỡng da chuẩn y khoa
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '14px', color: 'var(--c-text-main)' }}>
                {usageInstructions.length > 0 ? (
                  usageInstructions.map((ins, i) => (
                    <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                      <span style={{ backgroundColor: 'var(--c-gold-light)', color: 'var(--c-gold-hover)', padding: '2px 8px', borderRadius: '4px', fontWeight: 700, fontSize: '12px', flexShrink: 0 }}>
                        Bước {i + 1}
                      </span>
                      <span style={{ lineHeight: 1.6 }}>{ins}</span>
                    </div>
                  ))
                ) : usageText ? (
                  <p style={{ lineHeight: 1.7, whiteSpace: 'pre-line' }}>{usageText}</p>
                ) : (
                  <p style={{ color: 'var(--c-text-muted)' }}>Thông tin hướng dẫn sử dụng đang được cập nhật từ nhà sản xuất.</p>
                )}
              </div>

              {/* Storage & Safety Notices */}
              <div style={{ marginTop: '20px', padding: '14px 16px', backgroundColor: 'var(--c-canvas, #FDFBF7)', borderLeft: '3px solid var(--c-gold)', borderRadius: '4px' }}>
                <strong style={{ fontSize: '13px', color: 'var(--c-primary)', display: 'block', marginBottom: '4px' }}>
                  Lưu ý an toàn & bảo quản:
                </strong>
                <ul style={{ margin: '0 0 0 18px', padding: 0, fontSize: '13px', color: 'var(--c-text-muted)', lineHeight: 1.6 }}>
                  <li>Đậy nắp kín ngay sau khi sử dụng để tránh oxy hóa hoạt chất.</li>
                  <li>Bảo quản nơi khô ráo, thoáng mát dưới 30°C, tránh ánh nắng trực tiếp.</li>
                  <li>Tránh để sản phẩm tiếp xúc trực tiếp vào mắt; nếu dính phải rửa ngay bằng nước sạch.</li>
                  {product.usage?.warnings?.map((w, idx) => (
                    <li key={idx}>{w}</li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </section>

        <section id="product-reviews" className="product-content-section">
          <header className="product-section-heading">
            <h2>Đánh giá ({reviewCount})</h2>
          </header>
          <div className="product-section-body">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div>
                <p style={{ fontSize: '13.5px', color: 'var(--c-text-muted)', margin: 0 }}>
                  Đánh giá thực tế từ khách hàng đã mua và trải nghiệm sản phẩm.
                </p>
              </div>

              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '26px', fontWeight: 800, color: 'var(--c-primary)' }}>
                  {reviewCount > 0 ? `${Number(product.averageRating || 0).toFixed(1)} / 5.0` : '—'}
                </div>
                {reviewCount > 0 && (
                  <div style={{ display: 'flex', gap: '2px', color: '#EAB308' }}>
                    {[1, 2, 3, 4, 5].map((star) => <Star key={star} size={16} fill="#EAB308" />)}
                  </div>
                )}
              </div>
            </div>

            <div style={{ marginBottom: '24px', padding: '18px', border: '1px solid var(--c-border-subtle)', borderRadius: 'var(--radius-sm)', backgroundColor: '#FFFFFF' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '16px', marginBottom: '14px' }}>
                <div>
                  <strong style={{ display: 'block', color: 'var(--c-primary)', marginBottom: '4px' }}>Gửi đánh giá của bạn</strong>
                  <span style={{ color: 'var(--c-text-muted)', fontSize: '12.5px' }}>Đánh giá sẽ hiển thị sau khi BeautyShop duyệt nội dung.</span>
                </div>
                <CheckCircle2 size={20} color="var(--c-safe-green)" />
              </div>

              {reviewSuccess && (
                <div style={{ marginBottom: '12px', padding: '10px 12px', borderRadius: '8px', backgroundColor: 'var(--c-safe-green-bg, #ECFDF5)', color: 'var(--c-safe-green, #065F46)', fontSize: '12.5px', fontWeight: 600 }}>
                  {reviewSuccess}
                </div>
              )}
              {reviewError && (
                <div style={{ marginBottom: '12px', padding: '10px 12px', borderRadius: '8px', backgroundColor: '#FFF1F2', color: '#BE123C', fontSize: '12.5px', fontWeight: 600 }}>
                  {reviewError}
                </div>
              )}

              {!isAuthenticated ? (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
                  <span style={{ color: 'var(--c-text-muted)', fontSize: '13px' }}>Vui lòng đăng nhập để bình luận sản phẩm. Khách đã mua hàng sẽ được chấm sao.</span>
                  <button type="button" onClick={() => onNavigate('login')} className="btn-luxury-primary" style={{ fontSize: '12px', padding: '9px 16px' }}>
                    Đăng nhập để bình luận
                  </button>
                </div>
              ) : (
                <form onSubmit={handleReviewSubmit} style={{ display: 'grid', gap: '12px' }}>
                  {availableReviewOrders.length > 0 ? (
                    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(180px, 1fr) minmax(160px, auto)', gap: '12px' }}>
                      <label style={{ display: 'grid', gap: '6px', fontSize: '12px', fontWeight: 700, color: 'var(--c-primary)' }}>
                        Đơn hàng đã giao
                        <select
                          value={reviewForm.orderId}
                          onChange={(event) => setReviewForm((prev) => ({ ...prev, orderId: event.target.value }))}
                          style={{ height: '40px', border: '1px solid var(--c-border)', borderRadius: '8px', padding: '0 10px', color: 'var(--c-text-main)', backgroundColor: '#FFFFFF' }}
                        >
                          {availableReviewOrders.map((order) => (
                            <option key={order.id} value={order.id}>
                              {order.orderNumber || `Đơn #${order.id}`} {order.createdAt ? `• ${formatReviewDate(order.createdAt)}` : ''}
                            </option>
                          ))}
                        </select>
                      </label>

                      <div style={{ display: 'grid', gap: '6px', fontSize: '12px', fontWeight: 700, color: 'var(--c-primary)' }}>
                        Số sao
                        <div role="radiogroup" aria-label="Chọn số sao" style={{ display: 'flex', alignItems: 'center', gap: '4px', minHeight: '40px' }}>
                          {[1, 2, 3, 4, 5].map((star) => (
                            <button
                              key={star}
                              type="button"
                              onClick={() => setReviewForm((prev) => ({ ...prev, rating: star }))}
                              aria-label={`${star} sao`}
                              aria-pressed={Number(reviewForm.rating) === star}
                              style={{ padding: '4px', border: 'none', background: 'transparent', cursor: 'pointer', color: '#EAB308' }}
                            >
                              <Star size={22} fill={star <= Number(reviewForm.rating) ? '#EAB308' : 'none'} color="#EAB308" />
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div style={{ padding: '10px 12px', borderRadius: '8px', backgroundColor: 'var(--c-canvas)', color: 'var(--c-text-muted)', fontSize: '12.5px' }}>
                      Bạn chưa có đơn đã giao cho sản phẩm này, nên bình luận sẽ không kèm số sao.
                    </div>
                  )}

                  <input
                    type="text"
                    value={reviewForm.title}
                    onChange={(event) => setReviewForm((prev) => ({ ...prev, title: event.target.value }))}
                    maxLength={150}
                    placeholder="Tiêu đề bình luận (không bắt buộc)"
                    style={{ height: '40px', border: '1px solid var(--c-border)', borderRadius: '8px', padding: '0 12px', fontSize: '13px' }}
                  />

                  <textarea
                    value={reviewForm.content}
                    onChange={(event) => setReviewForm((prev) => ({ ...prev, content: event.target.value }))}
                    required
                    maxLength={2000}
                    rows={4}
                    placeholder="Chia sẻ cảm nhận hoặc câu hỏi của bạn về sản phẩm..."
                    style={{ width: '100%', border: '1px solid var(--c-border)', borderRadius: '8px', padding: '12px', fontSize: '13px', resize: 'vertical', lineHeight: 1.5 }}
                  />

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                    <span style={{ color: 'var(--c-text-light)', fontSize: '11.5px' }}>{reviewForm.content.length}/2000 ký tự</span>
                    <button type="submit" disabled={reviewSubmitting} className="btn-luxury-primary" style={{ fontSize: '12px', padding: '10px 18px' }}>
                      {reviewSubmitting ? 'Đang gửi...' : availableReviewOrders.length > 0 ? 'Gửi đánh giá' : 'Gửi bình luận'}
                    </button>
                  </div>
                </form>
              )}
            </div>

            {reviews.length > 0 ? (
              <div className="product-review-list">
                {reviews.map((review) => (
                  <article key={review.id}>
                    <div className="product-review-header">
                      <div>
                        <strong>{review.isVerifiedPurchase ? 'Khách hàng đã mua' : 'Thành viên BeautyShop'}</strong>
                        <span><CheckCircle2 size={12} /> {review.isVerifiedPurchase ? 'Đã xác thực đơn hàng' : 'Bình luận'}</span>
                      </div>
                      <time>{formatReviewDate(review.createdAt)}</time>
                    </div>
                    {review.rating ? (
                      <div className="product-review-stars">
                        {[1, 2, 3, 4, 5].map((star) => (
                          <Star key={star} size={14} fill={star <= review.rating ? '#EAB308' : 'none'} color="#EAB308" />
                        ))}
                      </div>
                    ) : null}
                    {review.title && <h4>{review.title}</h4>}
                    <p>{review.content}</p>
                    {review.adminReply && (
                      <div className="product-review-reply"><strong>BeautyShop phản hồi:</strong> {review.adminReply}</div>
                    )}
                  </article>
                ))}
              </div>
            ) : (
              <div style={{ padding: '24px', backgroundColor: 'var(--c-canvas)', borderRadius: 'var(--radius-sm)', textAlign: 'center' }}>
                <CheckCircle2 size={24} color="var(--c-safe-green)" style={{ margin: '0 auto 8px' }} />
                <strong style={{ display: 'block', marginBottom: '4px' }}>Chưa có đánh giá cho sản phẩm này</strong>
                <span style={{ fontSize: '12px', color: 'var(--c-text-muted)' }}>
                  BeautyShop chỉ công bố nội dung đánh giá được đồng bộ từ hệ thống đơn hàng thực tế.
                </span>
              </div>
            )}
          </div>
        </section>
      </div>

      {relatedProducts.length > 0 && (
        <section className="product-related-section">
          <div className="product-related-heading">
            <div>
              <h2>Sản phẩm tương tự</h2>
            </div>
            <button type="button" onClick={() => onNavigate('products')}>Khám phá thêm</button>
          </div>
          <div className="product-related-grid">
            {relatedProducts.map((item) => (
              <ProductCard key={item.id} product={item} onNavigate={onNavigate} />
            ))}
          </div>
        </section>
      )}

      <div className="product-mobile-sticky-cta" aria-label="Mua sản phẩm">
        <div>
          <span>Giá hiện tại</span>
          <strong>{formatCurrency(price)}</strong>
        </div>
        <button type="button" onClick={handleAddToCart} disabled={!isInStock}>
          <ShoppingBag size={17} />
          {addedNotice ? 'Đã thêm vào giỏ' : isInStock ? 'Thêm vào giỏ' : 'Tạm hết hàng'}
        </button>
      </div>
    </div>
  );
};
