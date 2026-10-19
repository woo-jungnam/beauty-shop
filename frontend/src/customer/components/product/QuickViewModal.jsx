import React, { useState, useEffect, useRef } from 'react';
import { X, Star, Minus, Plus, Check, ShoppingBag } from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { resolveProductImage } from './ProductCard';
import { apiClient } from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import { useCustomerDialog } from '../ui/useCustomerDialog';
import { recordProductView } from '../../utils/productInteractions';
import { activeVariants, chooseVariant, isClearanceProduct, knownCount, knownNumber, productPath, variantPrice } from '../../utils/productSelection';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const QuickViewModal = ({ product, isOpen, onClose, onNavigate }) => {
  const { addItem, isLoading } = useCustomerCart();
  const [selectedVariantId, setSelectedVariantId] = useState(null);
  const [detailAttempt, setDetailAttempt] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [justAdded, setJustAdded] = useState(false);
  const [activeProduct, setActiveProduct] = useState(product);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailFailed, setDetailFailed] = useState(false);
  const [isAdding, setIsAdding] = useState(false);
  const addPendingRef = useRef(false);
  const dialogRef = useRef(null);
  const productRef = useRef(product?.id);
  const retrySelection = useRef(null);
  useEffect(() => { productRef.current = product?.id; }, [product?.id]);
  useCustomerDialog(isOpen && !!activeProduct, dialogRef, onClose);

  useEffect(() => {
    if (!isOpen || !product?.id) return;
    setActiveProduct(product);
    const retry = retrySelection.current;
    const requestedId = retry?.productId === product.id ? retry.variantId
      : isClearanceProduct(product) ? product.clearanceVariant?.id ?? '' : undefined;
    retrySelection.current = null;
    setSelectedVariantId(requestedId ?? null);
    setQuantity(1);
    setJustAdded(false);
    setDetailLoading(true);
    setDetailFailed(false);

    const controller = new AbortController();
    const fetchDetail = async () => {
      try {
        const res = await apiClient.get(ENDPOINTS.CATALOG.PUBLIC_PRODUCT_DETAIL(product.id), { signal: controller.signal });
        const data = res?.data || res;
        if (res?.success === false || Number(data?.id) !== Number(product.id)) throw new Error('Invalid product response');
        if (!controller.signal.aborted) {
          setActiveProduct(data);
          setSelectedVariantId(requestedId ?? chooseVariant(data)?.id ?? null);
          recordProductView(data.id);
        }
      } catch {
        if (!controller.signal.aborted) setDetailFailed(true);
      } finally {
        if (!controller.signal.aborted) setDetailLoading(false);
      }
    };
    fetchDetail();
    return () => controller.abort();
  }, [isOpen, product, detailAttempt]);

  if (!isOpen || !activeProduct) return null;

  const variants = activeVariants(activeProduct).filter(variant => typeof variant.sku === 'string' && variant.sku.trim());
  const currentVariant = variants.find(variant => String(variant.id) === String(selectedVariantId)) || null;
  const price = variantPrice(currentVariant);
  const originalPrice = knownNumber(currentVariant?.price);
  const discountPercent = price != null && originalPrice > price ? Math.round(((originalPrice - price) / originalPrice) * 100) : 0;
  const stock = knownCount(currentVariant?.stockQuantity);
  const rawRating = knownNumber(activeProduct.averageRating);
  const rating = rawRating !== null && rawRating <= 5 ? rawRating : null;
  const reviewCount = knownCount(activeProduct.totalReviews ?? activeProduct.reviewCount);
  const unavailable = !currentVariant || price == null || stock === 0 || activeProduct.status && activeProduct.status !== 'ACTIVE';
  const ready = !detailLoading && !detailFailed && Number(activeProduct.id) === Number(product?.id);
  const canBuy = ready && !unavailable && Number.isSafeInteger(quantity) && quantity > 0 && (stock == null || quantity <= stock);

  const imageUrl = resolveProductImage(activeProduct);

  const reloadDetail = () => {
    retrySelection.current = { productId: product.id, variantId: selectedVariantId };
    setDetailAttempt(value => value + 1);
  };

  const handleAddToCart = async () => {
    if (addPendingRef.current || isLoading || !canBuy) return;
    addPendingRef.current = true;
    setIsAdding(true);
    setJustAdded(false);
    const submittedProductId = activeProduct.id;
    try {
      if (await addItem(activeProduct, currentVariant, quantity)) {
        if (Number(productRef.current) === Number(submittedProductId)) {
          setJustAdded(true);
          onClose();
        }
      }
    } finally {
      addPendingRef.current = false;
      setIsAdding(false);
    }
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
        ref={dialogRef}
        className="luxury-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="customer-quick-view-title"
        tabIndex={-1}
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
              e.currentTarget.src = resolveProductImage();
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
            }} id="customer-quick-view-title">
              {product.name}
            </h2>

            {/* Rating Stars */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', fontSize: '12px' }}>
              <div style={{ display: 'flex', gap: '2px' }}>
                {[1, 2, 3, 4, 5].map(s => (
                  <Star
                    key={s}
                    size={14}
                    fill={rating !== null && s <= Math.round(rating) ? '#F59E0B' : 'transparent'}
                    color={rating !== null && s <= Math.round(rating) ? '#F59E0B' : '#D1D5DB'}
                  />
                ))}
              </div>
              {rating !== null && reviewCount !== 0 ? (
                <>
                  <span style={{ fontWeight: 700, color: '#1F2937' }}>{rating.toFixed(1)}</span>
                  <span style={{ color: 'var(--c-text-light)' }}>({reviewCount} lượt đánh giá)</span>
                </>
              ) : (
                <span style={{ color: 'var(--c-text-light)' }}>
                  {reviewCount === null ? 'Chưa có dữ liệu đánh giá' : 'Chưa có đánh giá'}
                </span>
              )}
            </div>

            {/* Price Row */}
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px', marginBottom: '18px' }}>
              <span style={{ fontSize: '22px', fontWeight: 800, color: 'var(--c-deal-red, #E11D48)', fontFamily: 'var(--font-mono)' }}>
                {price == null ? 'Chưa xác minh giá bán' : formatCurrency(price)}
              </span>
              {price != null && originalPrice > price && (
                <span style={{ fontSize: '13px', color: '#9CA3AF', textDecoration: 'line-through', fontFamily: 'var(--font-mono)' }}>
                  {formatCurrency(originalPrice)}
                </span>
              )}
            </div>

            {detailLoading ? <p role="status">Đang tải phân loại…</p> : detailFailed ? (
              <div role="alert"><p>Chưa tải được phân loại. Vui lòng thử lại.</p><button type="button" className="btn-luxury-outline" onClick={reloadDetail}>Thử lại phân loại</button></div>
            ) : unavailable ? (
              <div role="alert"><p>Phân loại đã chọn không còn khả dụng. Vui lòng tải lại hoặc chọn phân loại khác.</p><button type="button" className="btn-luxury-outline" onClick={reloadDetail}>Tải lại phân loại</button></div>
            ) : <p role="status">{stock == null ? 'Tồn kho chưa xác minh' : `Còn ${stock} sản phẩm khả dụng`}</p>}

            {/* Description Snippet */}
            <p style={{ fontSize: '13px', color: 'var(--c-text-muted)', lineHeight: 1.55, margin: '0 0 18px 0' }}>
              {activeProduct.shortDescription || activeProduct.description || 'Mô tả sản phẩm đang được cập nhật.'}
            </p>

            {/* Volume / Variant Selector */}
            <div style={{ marginBottom: '18px' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, color: '#1F2937', marginBottom: '8px' }}>
                Chọn phân loại:
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {variants.map((v, idx) => {
                  const isSelected = String(selectedVariantId) === String(v.id);
                  const label = v.volumeMl ? `${v.volumeMl}ml` : (v.sku || `Phân loại ${idx + 1}`);
                  return (
                    <button
                      key={v.id || idx}
                      type="button"
                      disabled={isAdding || !ready}
                      aria-pressed={isSelected}
                      onClick={() => { setSelectedVariantId(v.id); setQuantity(1); setJustAdded(false); }}
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
                  type="button"
                  aria-label="Giảm số lượng"
                  disabled={isAdding || quantity <= 1}
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
                  type="button"
                  aria-label="Tăng số lượng"
                  disabled={isAdding || !ready || unavailable || (stock != null && quantity >= stock)}
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
              type="button"
              onClick={handleAddToCart}
              disabled={isAdding || isLoading || !canBuy}
              aria-busy={isAdding}
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
                  <span>{isAdding ? 'Đang thêm vào giỏ…' : !canBuy ? 'Thêm Vào Giỏ Hàng · Chưa khả dụng' : `Thêm Vào Giỏ Hàng • ${formatCurrency(price * quantity)}`}</span>
                </>
              )}
            </button>

            <button
              onClick={() => {
                onClose();
                onNavigate(productPath(product, selectedVariantId ?? (isClearanceProduct(product) ? product.clearanceVariant?.id ?? '' : undefined)));
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
