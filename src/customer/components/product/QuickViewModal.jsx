import React, { useState, useEffect } from 'react';
import { X, ShieldCheck, Star, Minus, Plus, Check, ShoppingBag } from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { resolveProductImage } from './ProductCard';
import { apiClient } from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/endpoints';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const QuickViewModal = ({ product, isOpen, onClose, onNavigate }) => {
  const { addItem } = useCustomerCart();
  const [selectedVariantIndex, setSelectedVariantIndex] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [justAdded, setJustAdded] = useState(false);
  const [activeProduct, setActiveProduct] = useState(product);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (!isOpen || !product?.id) return;
    setActiveProduct(product);
    setSelectedVariantIndex(0);
    setQuantity(1);

    let isCancelled = false;
    const fetchDetail = async () => {
      try {
        const res = await apiClient.get(ENDPOINTS.CATALOG.PUBLIC_PRODUCT_DETAIL(product.id));
        const data = res?.data || res;
        if (!isCancelled && data && data.id) {
          setActiveProduct(data);
        }
      } catch (err) {
        console.warn('Quick view detail fetch notice:', err);
      }
    };
    fetchDetail();
    return () => { isCancelled = true; };
  }, [isOpen, product?.id]);

  if (!isOpen || !activeProduct) return null;

  const variants = activeProduct.variants?.length > 0
    ? activeProduct.variants
    : [
        { id: activeProduct.id, volume: activeProduct.volume || 'Tiêu chuẩn', price: activeProduct.minPrice || activeProduct.basePrice || activeProduct.price || 0, sku: activeProduct.slug || 'Standard' }
      ];

  const currentVariant = variants[selectedVariantIndex] || variants[0];
  const price = Number(currentVariant.discountPrice || currentVariant.price || activeProduct.minPrice || activeProduct.basePrice || 0);
  const originalPrice = Number(currentVariant.originalPrice || activeProduct.maxPrice || price);
  const discountPercent = originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : 0;

  const imageUrl = resolveProductImage(activeProduct);

  const handleAddToCart = () => {
    addItem(activeProduct, currentVariant, quantity);
    setJustAdded(true);
    setTimeout(() => {
      setJustAdded(false);
      onClose();
    }, 1000);
  };

  return (
    <div
      className="drawer-backdrop"
      onClick={onClose}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
        zIndex: 1100
      }}
    >
      <div
        className="luxury-card"
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: '820px',
          backgroundColor: '#FFFFFF',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          boxShadow: '0 25px 60px -15px rgba(0, 0, 0, 0.4)',
          position: 'relative',
          animation: 'fadeInUp 0.3s cubic-bezier(0.16, 1, 0.3, 1)'
        }}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          aria-label="Đóng xem nhanh"
          style={{
            position: 'absolute',
            top: '16px',
            right: '16px',
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            backgroundColor: 'rgba(255, 255, 255, 0.9)',
            border: '1px solid var(--c-border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            zIndex: 10,
            color: 'var(--c-primary)',
            transition: 'all 0.2s ease'
          }}
        >
          <X size={16} />
        </button>

        {/* Product Visual Column */}
        <div style={{
          position: 'relative',
          backgroundColor: '#F8F6F2',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden',
          minHeight: '360px'
        }}>
          <img
            src={imageUrl}
            alt={product.name}
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = 'https://images.unsplash.com/photo-1571781926291-c477ebfd024b?auto=format&fit=crop&w=600&q=80';
            }}
            style={{
              width: '100%',
              height: '100%',
              maxHeight: '440px',
              objectFit: 'cover'
            }}
          />

          <div style={{
            position: 'absolute',
            top: '16px',
            left: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px'
          }}>
            <span style={{
              backgroundColor: 'var(--c-primary, #D45D79)',
              color: '#FFFFFF',
              fontSize: '10px',
              fontWeight: 800,
              padding: '3px 8px',
              borderRadius: '3px',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '4px'
            }}>
              NowFree 2H
            </span>
            {discountPercent > 0 && (
              <span style={{
                backgroundColor: 'var(--c-deal-red, #E11D48)',
                color: '#FFFFFF',
                fontSize: '11px',
                fontWeight: 800,
                padding: '2px 8px',
                borderRadius: '3px',
                fontFamily: 'var(--font-mono)'
              }}>
                -{discountPercent}%
              </span>
            )}
          </div>
        </div>

        {/* Product Details Column - Pastel Pink Theme */}
        <div style={{ padding: '28px 24px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{
              fontSize: '12px',
              fontWeight: 800,
              color: 'var(--c-primary, #D45D79)',
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
              marginBottom: '4px'
            }}>
              {product.brandName || product.brand?.name || 'Dược Mỹ Phẩm'}
            </div>

            <h2 style={{
              fontSize: '18px',
              fontWeight: 700,
              margin: '0 0 10px 0',
              color: '#1F2937',
              fontFamily: 'var(--font-sans)',
              lineHeight: 1.35
            }}>
              {product.name}
            </h2>

            {/* Rating Stars */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', fontSize: '12px' }}>
              <div style={{ display: 'flex', gap: '2px', color: '#F59E0B' }}>
                {[1, 2, 3, 4, 5].map(s => (
                  <Star key={s} size={14} fill="#F59E0B" color="#F59E0B" />
                ))}
              </div>
              <span style={{ fontWeight: 700, color: '#1F2937' }}>5.0</span>
              <span style={{ color: 'var(--c-text-light)' }}>(Đánh giá chính hãng)</span>
            </div>

            {/* Price Row */}
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px', marginBottom: '18px' }}>
              <span style={{ fontSize: '22px', fontWeight: 800, color: 'var(--c-deal-red, #E11D48)', fontFamily: 'var(--font-mono)' }}>
                {formatCurrency(price)}
              </span>
              {originalPrice > price && (
                <span style={{ fontSize: '13px', color: '#9CA3AF', textDecoration: 'line-through', fontFamily: 'var(--font-mono)' }}>
                  {formatCurrency(originalPrice)}
                </span>
              )}
            </div>

            {/* Description Snippet */}
            <p style={{ fontSize: '13px', color: 'var(--c-text-muted)', lineHeight: 1.55, margin: '0 0 18px 0' }}>
              {product.shortDescription || 'Sản phẩm chính hãng có kiểm định độ tinh khiết hoạt chất, giúp phục hồi và bảo vệ hàng rào sinh học của làn da.'}
            </p>

            {/* Volume / Variant Selector */}
            <div style={{ marginBottom: '18px' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, color: '#1F2937', marginBottom: '8px' }}>
                Chọn phân loại:
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {variants.map((v, idx) => {
                  const isSelected = selectedVariantIndex === idx;
                  const label = v.volumeMl ? `${v.volumeMl}ml` : (v.sku || `Phân loại ${idx + 1}`);
                  return (
                    <button
                      key={v.id || idx}
                      onClick={() => setSelectedVariantIndex(idx)}
                      style={{
                        padding: '6px 14px',
                        fontSize: '12px',
                        fontWeight: 600,
                        borderRadius: 'var(--radius-sm)',
                        border: isSelected ? '1.5px solid var(--c-primary)' : '1px solid var(--c-border)',
                        backgroundColor: isSelected ? 'var(--c-primary-light, #FFF0F3)' : 'transparent',
                        color: isSelected ? 'var(--c-primary)' : 'var(--c-text-main)',
                        cursor: 'pointer'
                      }}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Quantity Stepper */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px', marginBottom: '20px' }}>
              <span style={{ fontSize: '12px', fontWeight: 700, color: '#1F2937' }}>Số lượng:</span>
              <div style={{
                display: 'inline-flex',
                alignItems: 'center',
                border: '1px solid var(--c-border)',
                borderRadius: 'var(--radius-sm)',
                overflow: 'hidden'
              }}>
                <button
                  onClick={() => setQuantity(prev => Math.max(1, prev - 1))}
                  style={{
                    width: '32px',
                    height: '32px',
                    background: 'transparent',
                    border: 'none',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--c-text-main)'
                  }}
                >
                  <Minus size={14} />
                </button>
                <span style={{ minWidth: '32px', textAlign: 'center', fontSize: '13px', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                  {quantity}
                </span>
                <button
                  onClick={() => setQuantity(prev => prev + 1)}
                  style={{
                    width: '32px',
                    height: '32px',
                    background: 'transparent',
                    border: 'none',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--c-text-main)'
                  }}
                >
                  <Plus size={14} />
                </button>
              </div>
            </div>
          </div>

          {/* Action CTAs */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <button
              onClick={handleAddToCart}
              className="btn-luxury-primary"
              style={{ width: '100%', padding: '12px', fontSize: '13px', justifyContent: 'center', display: 'flex', alignItems: 'center', gap: '8px' }}
            >
              {justAdded ? (
                <>
                  <Check size={16} />
                  <span>Đã thêm vào giỏ hàng!</span>
                </>
              ) : (
                <>
                  <ShoppingBag size={16} />
                  <span>Thêm Vào Giỏ Hàng • {formatCurrency(price * quantity)}</span>
                </>
              )}
            </button>

            <button
              onClick={() => {
                onClose();
                onNavigate(`product/${product.id}`);
              }}
              style={{
                width: '100%',
                background: 'transparent',
                border: 'none',
                color: 'var(--c-text-muted)',
                fontSize: '12px',
                padding: '6px',
                cursor: 'pointer',
                textAlign: 'center'
              }}
            >
              Xem chi tiết toàn bộ thành phần & HDSD
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
