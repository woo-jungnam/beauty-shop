import React, { useState } from 'react';
import { Star, ShoppingBag, Eye, Sparkles, Check, Heart } from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { cleanDisplayName, resolveMediaUrl } from '../../../shared/utils/formatters';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const resolveProductImage = (product) => {
  const raw = product?.featuredImage || product?.thumbnailUrl || product?.images?.[0]?.imageUrl;
  if (raw && raw.trim().length > 0) {
    if (raw.startsWith('/') && !raw.startsWith('//')) {
      return resolveMediaUrl(raw);
    }
    return raw;
  }
  return 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85';
};

export const ProductCard = ({ product, onNavigate, onQuickView }) => {
  const { addItem } = useCustomerCart();
  const [isHovered, setIsHovered] = useState(false);
  const [justAdded, setJustAdded] = useState(false);

  // Image resolution from backend product data or authentic catalog map
  const imageUrl = resolveProductImage(product);

  // Compute pricing from default variant or direct fields
  const variants = product.variants || [];
  const defaultVariant = variants[0] || null;
  const price = Number(defaultVariant?.discountPrice || defaultVariant?.price || product.minPrice || product.basePrice || product.price || 0);
  const originalPrice = Number(defaultVariant?.originalPrice || (product.maxPrice && product.maxPrice > price ? product.maxPrice : null) || product.originalPrice || price);
  const discountPercent = originalPrice > price
    ? Math.round(((originalPrice - price) / originalPrice) * 100)
    : 0;

  const brandName = product.brandName || product.brand?.name || 'Dược Mỹ Phẩm';
  const rating = Number(product.averageRating ?? 0);
  const reviewCount = Number(product.totalReviews ?? product.reviewCount ?? 0);
  const soldCount = Number(product.totalSold ?? product.soldCount ?? 0);
  const [isWishlisted, setIsWishlisted] = useState(false);

  const handleQuickAdd = (e) => {
    e.stopPropagation();
    addItem(product, defaultVariant, 1);
    setJustAdded(true);
    setTimeout(() => setJustAdded(false), 1200);
  };

  const handleToggleWishlist = (e) => {
    e.stopPropagation();
    setIsWishlisted(!isWishlisted);
  };

  return (
    <div
      className="luxury-card product-card-resolved"
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
      onClick={() => onNavigate(`product/${product.id}`)}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* 1. Image Media Container */}
      <div style={{
        position: 'relative',
        width: '100%',
        paddingTop: '100%', // 1:1 Aspect Ratio
        backgroundColor: '#F8F6F2',
        overflow: 'hidden'
      }}>
        <img 
          src={imageUrl} 
          alt={product.name}
          onError={(e) => {
            e.currentTarget.onerror = null;
            const fallback = resolveProductImage(product);
            if (e.currentTarget.src !== fallback) {
              e.currentTarget.src = fallback;
            }
          }}
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover',
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
          {product.earliestExpirationDate && (
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
          title={isWishlisted ? "Bỏ yêu thích" : "Lưu vào yêu thích"}
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
            onClick={handleQuickAdd}
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
                <span>Thêm vào giỏ</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* 2. Content Body - Pastel Pink Theme */}
      <div style={{ padding: '12px 14px', display: 'flex', flexDirection: 'column', flex: 1 }}>
        {/* Brand label */}
        <div style={{
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
          {product.name}
        </h3>

        {/* Rating and Volume / Sales */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: '6px',
          fontSize: '11px',
          color: 'var(--c-text-light)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
            {reviewCount > 0 ? (
              <>
                <Star size={12} fill="#F59E0B" color="#F59E0B" />
                <span style={{ fontWeight: 700, color: '#1F2937' }}>
                  {rating.toFixed(1)}
                </span>
                <span>({reviewCount})</span>
              </>
            ) : (
              <span style={{ fontWeight: 600, color: 'var(--c-text-muted)' }}>Mới</span>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            {defaultVariant?.volumeMl && (
              <span style={{
                backgroundColor: 'var(--c-primary-light, #FFF0F3)',
                color: 'var(--c-primary, #D45D79)',
                padding: '1px 5px',
                borderRadius: '3px',
                fontWeight: 600,
                fontSize: '10px'
              }}>
                {defaultVariant.volumeMl}ml
              </span>
            )}
            <span style={{ color: 'var(--c-text-muted)', fontSize: '10.5px' }}>
              {soldCount > 0 ? `Đã bán ${soldCount}` : 'Còn hàng'}
            </span>
          </div>
        </div>

        {/* Price Row - Rose Red Bold Accent */}
        <div style={{
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
            {variants.length > 1 ? `Từ ${formatCurrency(price)}` : formatCurrency(price)}
          </span>

          {originalPrice > price && (
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
    </div>
  );
};
