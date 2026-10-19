import { useCustomerWishlist } from '../../stores/customerWishlistStore';
import React, { useEffect, useRef, useState } from 'react';
import { Star, ShoppingBag, Eye, Check, Heart } from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { cleanDisplayName, resolveMediaUrl } from '../../../shared/utils/formatters';
import { recordSearchClick } from '../../utils/productInteractions';
import { chooseVariant, isClearanceProduct, knownCount, knownNumber, productPath, variantPrice } from '../../utils/productSelection';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);
};

const PRODUCT_PLACEHOLDER = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="800" height="800" viewBox="0 0 800 800"><rect width="800" height="800" fill="#F8F6F2"/><g fill="none" stroke="#C5BDB4" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"><rect x="310" y="250" width="180" height="230" rx="20"/><path d="M350 250v-45h100v45M365 335h70M365 375h70"/></g><text x="400" y="560" text-anchor="middle" fill="#817970" font-family="Arial,sans-serif" font-size="28">Chưa có ảnh sản phẩm</text></svg>')}`;

export const resolveProductImage = (product) => {
  const raw = product?.featuredImage || product?.thumbnailUrl || product?.images?.[0]?.imageUrl;
  if (typeof raw === 'string' && raw.trim().length > 0) {
    if (raw.startsWith('/') && !raw.startsWith('//')) {
      return resolveMediaUrl(raw);
    }
    return raw;
  }
  return PRODUCT_PLACEHOLDER;
};

export const ProductCard = ({ product, onNavigate, onQuickView, fromSearch = false }) => {
  const { addItem, isLoading } = useCustomerCart();
  const [isHovered, setIsHovered] = useState(false);
  const [justAdded, setJustAdded] = useState(false);
  const [isAdding, setIsAdding] = useState(false);
  const addPendingRef = useRef(false);
  const feedbackTimerRef = useRef(null);
  useEffect(() => () => clearTimeout(feedbackTimerRef.current), []);

  // Missing media uses a neutral placeholder, never another product's photo.
  const imageUrl = resolveProductImage(product);

  const variants = product.variants || [];
  const isClearance = isClearanceProduct(product);
  const selectedVariant = chooseVariant(product);
  const price = isClearance ? variantPrice(selectedVariant)
    : knownNumber(selectedVariant?.discountPrice ?? selectedVariant?.price ?? product.minPrice ?? product.basePrice ?? product.price);
  const originalPrice = knownNumber(isClearance ? selectedVariant?.price : selectedVariant?.originalPrice ?? selectedVariant?.price ?? product.originalPrice);
  const stock = knownCount(selectedVariant?.stockQuantity);
  const clearanceStock = knownCount(product.clearanceStock);
  const clearanceDiscount = knownNumber(selectedVariant?.discountPrice);
  const unavailable = product.status && product.status !== 'ACTIVE' || stock === 0
    || isClearance && (!selectedVariant || stock == null || clearanceStock == null || clearanceStock === 0
      || clearanceDiscount == null || originalPrice == null || clearanceDiscount >= originalPrice);
  const path = productPath(product, isClearance ? product.clearanceVariant?.id ?? '' : undefined);
  const discountPercent = price !== null && originalPrice !== null && originalPrice > price
    ? Math.round(((originalPrice - price) / originalPrice) * 100)
    : 0;

  const brandName = product.brandName?.trim() || product.brand?.name?.trim() || 'Chưa có thông tin thương hiệu';
  const rawRating = knownNumber(product.averageRating);
  const rating = rawRating !== null && rawRating <= 5 ? rawRating : null;
  const reviewCount = knownCount(product.totalReviews ?? product.reviewCount);
  const soldCount = knownCount(product.totalSold ?? product.soldCount);
  const { active: isWishlisted, toggle: toggleWishlist, busy: wishlistBusy, error: wishlistError } = useCustomerWishlist(product.id);

  const handleQuickAdd = async (e) => {
    e.stopPropagation();
    if (addPendingRef.current || isLoading || unavailable) return;
    addPendingRef.current = true;
    setIsAdding(true);
    setJustAdded(false);
    try {
      if (await addItem(product, selectedVariant, 1)) {
        setJustAdded(true);
        clearTimeout(feedbackTimerRef.current);
        feedbackTimerRef.current = setTimeout(() => setJustAdded(false), 1200);
      }
    } finally {
      addPendingRef.current = false;
      setIsAdding(false);
    }
  };

  const handleToggleWishlist = (e) => {
    e.stopPropagation();
    toggleWishlist();
  };

  return (
    <article
      className="luxury-card product-card-resolved"
      aria-label={`Xem sản phẩm ${product.name}`}
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: 'var(--radius-md)',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        backgroundColor: '#FFFFFF',
        height: '100%'
      }}
      onClick={() => { if (fromSearch) recordSearchClick(product.id); onNavigate(path); }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* 1. Image Media Container */}
      <div className="product-card-media" style={{
        position: 'relative',
        width: '100%',
        paddingTop: 'var(--product-card-media-padding, 100%)',
        backgroundColor: '#F8F6F2',
        overflow: 'hidden'
      }}>
        <img 
          src={imageUrl} 
          alt={product.name}
          loading="lazy"
          decoding="async"
          onError={(e) => {
            e.currentTarget.onerror = null;
            if (e.currentTarget.src !== PRODUCT_PLACEHOLDER) {
              e.currentTarget.src = PRODUCT_PLACEHOLDER;
            }
          }}
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            objectFit: 'var(--product-card-image-fit, cover)',
            transition: 'transform 0.5s cubic-bezier(0.16, 1, 0.3, 1)',
            transform: isHovered ? 'scale(1.06)' : 'scale(1)'
          }}
        />

        {/* Top Badges - Hasaki Style */}
        <div className="product-card-badges" style={{
          position: 'absolute',
          top: '8px',
          left: '8px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
          zIndex: 2
        }}>
          {discountPercent > 0 && (
            <span style={{
              backgroundColor: 'var(--c-deal-red, #E11D48)',
              color: '#FFFFFF',
              fontSize: '11px',
              fontWeight: 800,
              padding: '2px 7px',
              borderRadius: '3px',
              fontFamily: 'var(--font-mono)',
              boxShadow: '0 2px 4px rgba(225, 29, 72, 0.35)'
            }}>
              -{discountPercent}%
            </span>
          )}
          {isClearance && product.earliestExpirationDate && (
            <span style={{
              backgroundColor: '#B45309',
              color: '#FFFFFF',
              fontSize: '9.5px',
              fontWeight: 800,
              padding: '2px 6px',
              borderRadius: '3px',
              boxShadow: '0 2px 4px rgba(180, 83, 9, 0.35)',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '3px'
            }}>
              HSD: {new Date(product.earliestExpirationDate).toLocaleDateString('vi-VN')}
            </span>
          )}
          {product.isFeatured && (
            <span style={{
              backgroundColor: 'rgba(255, 255, 255, 0.95)',
              color: 'var(--c-primary, #D45D79)',
              fontSize: '9.5px',
              fontWeight: 700,
              padding: '1px 6px',
              borderRadius: '3px',
              border: '1px solid var(--c-primary-light, #FFF0F3)'
            }}>
              Hot Deal
            </span>
          )}
        </div>

        {/* Wishlist Heart Button */}
        <button
          onClick={handleToggleWishlist}
          disabled={wishlistBusy}
          aria-pressed={isWishlisted}
          aria-label={wishlistError || (isWishlisted ? "Bỏ yêu thích" : "Lưu vào yêu thích")}
          title={wishlistError || (isWishlisted ? "Bỏ yêu thích" : "Lưu vào yêu thích")}
          style={{
            position: 'absolute',
            top: '10px',
            right: '10px',
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            backgroundColor: 'rgba(255, 255, 255, 0.92)',
            backdropFilter: 'blur(4px)',
            border: '1px solid rgba(0,0,0,0.06)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            zIndex: 2,
            transition: 'transform 0.2s ease',
            transform: isWishlisted ? 'scale(1.1)' : 'scale(1)'
          }}
        >
          {isWishlisted ? (
            <Heart size={15} fill="#EF4444" color="#EF4444" />
          ) : (
            <Heart size={15} color="var(--c-text-light)" />
          )}
        </button>

        {/* Quick View Eye Button */}
        {onQuickView && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              if (fromSearch) recordSearchClick(product.id);
              onQuickView(product);
            }}
            title="Xem nhanh chi tiết"
            style={{
              position: 'absolute',
              top: '10px',
              right: '48px',
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              backgroundColor: 'rgba(255, 255, 255, 0.92)',
              backdropFilter: 'blur(4px)',
              border: '1px solid rgba(0,0,0,0.06)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              zIndex: 2,
              transition: 'all 0.2s ease',
              color: 'var(--c-primary)'
            }}
          >
            <Eye size={15} />
          </button>
        )}

        {/* Quick Add Button overlay on hover */}
        <div className="product-card-quick-add" style={{
          position: 'absolute',
          bottom: '10px',
          left: '10px',
          right: '10px',
          display: 'flex',
          justifyContent: 'center',
          opacity: isHovered ? 1 : 0,
          transform: isHovered ? 'translateY(0)' : 'translateY(8px)',
          transition: 'all 0.25s ease',
          zIndex: 3
        }}>
          <button
            type="button"
            onClick={handleQuickAdd}
            disabled={isAdding || isLoading || unavailable}
            aria-busy={isAdding}
            aria-label={`Thêm ${product.name} vào giỏ`}
            style={{
              width: '100%',
              padding: '9px 14px',
              backgroundColor: justAdded ? '#059669' : 'var(--c-primary, #D45D79)',
              border: 'none',
              borderRadius: 'var(--radius-sm, 4px)',
              color: '#FFFFFF',
              fontSize: '12px',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
              boxShadow: '0 2px 8px rgba(212, 93, 121, 0.25)'
            }}
          >
            {justAdded ? (
              <>
                <Check size={14} />
                <span>Đã thêm vào giỏ!</span>
              </>
            ) : (
              <>
                <ShoppingBag size={14} color="#FFFFFF" />
                <span>{unavailable ? 'Hiện không khả dụng' : isAdding ? 'Đang thêm…' : 'Thêm vào giỏ'}</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* 2. Content Body - Pastel Pink Theme */}
      <div className="product-card-body" style={{ padding: 'var(--product-card-body-padding, 12px 14px)', display: 'flex', flexDirection: 'column', flex: 1 }}>
        {/* Brand label */}
        <div className="product-card-brand" title={cleanDisplayName(brandName)} style={{
          fontSize: '11px',
          fontWeight: 800,
          color: 'var(--c-primary, #D45D79)',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
          marginBottom: '4px'
        }}>
          {cleanDisplayName(brandName)}
        </div>

        {/* Product Title */}
        <h3 style={{
          margin: 0,
          fontFamily: 'var(--font-sans)',
          fontSize: '13px',
          fontWeight: 600,
          color: '#1F2937',
          lineHeight: 1.4,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
          minHeight: '36px',
          letterSpacing: '-0.01em'
        }}>
          <a className="product-card-title-link" href={`#/${path}`} onClick={(event) => { event.stopPropagation(); if (fromSearch) recordSearchClick(product.id); }}>{product.name}</a>
        </h3>

        {/* Rating and Volume / Sales */}
        <div className="product-card-rating" style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: '6px',
          fontSize: '11px',
          color: 'var(--c-text-light)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
            {rating !== null && reviewCount !== 0 ? (
              <>
                <Star size={12} fill="#F59E0B" color="#F59E0B" />
                <span style={{ fontWeight: 700, color: '#1F2937' }}>
                  {rating.toFixed(1)}
                </span>
                {reviewCount !== null && <span>({reviewCount})</span>}
              </>
            ) : (
              <span style={{ fontWeight: 600, color: 'var(--c-text-muted)' }}>{reviewCount === null ? 'Chưa có dữ liệu đánh giá' : reviewCount === 0 ? 'Chưa có đánh giá' : `${reviewCount} đánh giá`}</span>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            {selectedVariant?.volumeMl && (
              <span style={{
                backgroundColor: 'var(--c-primary-light, #FFF0F3)',
                color: 'var(--c-primary, #D45D79)',
                padding: '1px 5px',
                borderRadius: '3px',
                fontWeight: 600,
                fontSize: '10px'
              }}>
                {selectedVariant.volumeMl}ml
              </span>
            )}
            {soldCount !== null && <span style={{ color: 'var(--c-text-muted)', fontSize: '10.5px' }}>
              Đã bán {soldCount}
            </span>}
          </div>
        </div>

        {isClearance && <p role={unavailable ? 'alert' : undefined} style={{ fontSize: '12px', margin: '8px 0 0', color: 'var(--c-text-muted)' }}>
          {unavailable ? 'Ưu đãi này không còn khả dụng. Vui lòng tải lại.'
            : `SKU ${selectedVariant.sku} · Còn ${clearanceStock} sản phẩm cận hạn tại lúc tải${knownCount(product.daysRemaining) != null ? ` · HSD gần nhất còn ${product.daysRemaining} ngày` : ''}`}
        </p>}

        {/* Price Row - Rose Red Bold Accent */}
        <div className="product-card-price" style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: '8px',
          marginTop: '8px'
        }}>
          <span style={{
            fontSize: '16px',
            fontWeight: 800,
            color: 'var(--c-deal-red, #E11D48)',
            fontFamily: 'var(--font-mono)'
          }}>
            {price === null ? 'Chưa có thông tin giá' : !isClearance && variants.length > 1 ? `Từ ${formatCurrency(price)}` : formatCurrency(price)}
          </span>

          {price !== null && originalPrice !== null && originalPrice > price && (
            <span style={{
              fontSize: '11.5px',
              color: '#9CA3AF',
              textDecoration: 'line-through',
              fontFamily: 'var(--font-mono)'
            }}>
              {formatCurrency(originalPrice)}
            </span>
          )}
        </div>
      </div>
    </article>
  );
};
