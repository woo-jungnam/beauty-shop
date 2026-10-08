import React, { useState } from 'react';
import { X, Trash2, ShoppingBag, ArrowRight, Tag, Check, Sparkles } from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const MiniCartDrawer = ({ onNavigate }) => {
  const { 
    items, 
    totalItems, 
    subtotal, 
    discountAmount, 
    voucher, 
    shippingFee, 
    isFreeShipping, 
    remainingForFreeShipping, 
    freeShippingThreshold, 
    totalAmount, 
    isCartOpen, 
    setCartOpen, 
    updateQuantity, 
    removeItem, 
    applyVoucher, 
    removeVoucher 
  } = useCustomerCart();

  const [inputVoucher, setInputVoucher] = useState('');
  const [voucherError, setVoucherError] = useState('');

  if (!isCartOpen) return null;

  const handleApplyVoucher = (e) => {
    e.preventDefault();
    if (!inputVoucher.trim()) return;
    const success = applyVoucher(inputVoucher.trim(), 10, 100000);
    if (success) {
      setVoucherError('');
      setInputVoucher('');
    } else {
      setVoucherError('Mã không hợp lệ hoặc đã hết lượt.');
    }
  };

  const progressPercent = Math.min(100, Math.round((subtotal / freeShippingThreshold) * 100));

  return (
    <>
      {/* Backdrop */}
      <div 
        className="drawer-backdrop" 
        onClick={() => setCartOpen(false)} 
      />

      {/* Slide Drawer Panel */}
      <div className="drawer-panel">
        {/* 1. Header */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '18px 20px',
          borderBottom: '1px solid var(--c-border-subtle)',
          backgroundColor: '#FFFFFF'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShoppingBag size={20} color="var(--c-gold)" />
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, fontFamily: 'var(--font-serif)' }}>
              Giỏ Hàng Của Bạn ({totalItems})
            </h3>
          </div>
          <button 
            onClick={() => setCartOpen(false)}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '50%',
              display: 'flex',
              color: 'var(--c-text-muted)'
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* 2. Free Shipping Progress Bar */}
        <div style={{
          padding: '12px 20px',
          backgroundColor: isFreeShipping ? 'var(--c-safe-green-bg)' : 'var(--c-gold-light)',
          borderBottom: '1px solid var(--c-border-subtle)',
          fontSize: '12px'
        }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '6px',
            fontWeight: 600,
            color: isFreeShipping ? 'var(--c-safe-green)' : 'var(--c-text-gold)'
          }}>
            {isFreeShipping ? (
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Check size={14} /> Chúc mừng! Đơn hàng được FREESHIP toàn quốc!
              </span>
            ) : (
              <span>
                Mua thêm <strong>{formatCurrency(remainingForFreeShipping)}</strong> để được <strong>FREESHIP</strong>
              </span>
            )}
            <span>{progressPercent}%</span>
          </div>

          <div style={{
            width: '100%',
            height: '6px',
            backgroundColor: 'rgba(0,0,0,0.06)',
            borderRadius: '9999px',
            overflow: 'hidden'
          }}>
            <div style={{
              width: `${progressPercent}%`,
              height: '100%',
              backgroundColor: isFreeShipping ? 'var(--c-safe-green)' : 'var(--c-gold)',
              transition: 'width 0.4s cubic-bezier(0.16, 1, 0.3, 1)'
            }} />
          </div>
        </div>

        {/* 3. Items List or Empty State */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '16px 20px',
          display: 'flex',
          flexDirection: 'column',
          gap: '14px'
        }}>
          {items.length === 0 ? (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              color: 'var(--c-text-light)',
              textAlign: 'center',
              gap: '12px',
              padding: '40px 20px'
            }}>
              <div style={{
                width: '64px',
                height: '64px',
                borderRadius: '50%',
                backgroundColor: 'var(--c-canvas-subtle)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <ShoppingBag size={28} color="var(--c-text-light)" />
              </div>
              <h4 style={{ margin: 0, fontSize: '15px', color: 'var(--c-primary)' }}>
                Giỏ hàng của bạn đang trống
              </h4>
              <p style={{ margin: 0, fontSize: '13px' }}>
                Khám phá các sản phẩm dược mỹ phẩm chính hãng và liệu trình Spa chăm sóc chuyên sâu!
              </p>
              <button 
                onClick={() => { setCartOpen(false); onNavigate('products'); }}
                className="btn-luxury-primary"
                style={{ marginTop: '8px' }}
              >
                Khám phá sản phẩm
              </button>
            </div>
          ) : (
            items.map((item) => (
              <div 
                key={item.itemId}
                style={{
                  display: 'flex',
                  gap: '12px',
                  paddingBottom: '14px',
                  borderBottom: '1px solid var(--c-border-subtle)'
                }}
              >
                {/* Thumbnail */}
                <div style={{
                  width: '64px',
                  height: '64px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'var(--c-canvas-subtle)',
                  overflow: 'hidden',
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  {item.image ? (
                    <img 
                      src={item.image} 
                      alt={item.name} 
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                    />
                  ) : (
                    <Sparkles size={20} color="var(--c-gold)" />
                  )}
                </div>

                {/* Details */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '11px', color: 'var(--c-gold-hover)', fontWeight: 600, textTransform: 'uppercase' }}>
                    {item.brand}
                  </div>
                  <div style={{
                    fontSize: '13px',
                    fontWeight: 600,
                    color: 'var(--c-primary)',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis'
                  }}>
                    {item.name}
                  </div>
                  <div style={{ fontSize: '12px', color: 'var(--c-text-light)', marginTop: '2px' }}>
                    Phân loại: <strong>{item.variantName}</strong>
                  </div>

                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    marginTop: '8px'
                  }}>
                    {/* Quantity controller */}
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      border: '1px solid var(--c-border)',
                      borderRadius: 'var(--radius-pill)',
                      overflow: 'hidden',
                      height: '26px'
                    }}>
                      <button 
                        onClick={() => updateQuantity(item.itemId, item.quantity - 1)}
                        style={{
                          width: '26px',
                          height: '100%',
                          background: 'transparent',
                          border: 'none',
                          cursor: 'pointer',
                          fontWeight: 700,
                          fontSize: '12px',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center'
                        }}
                      >
                        -
                      </button>
                      <span style={{
                        padding: '0 8px',
                        fontSize: '12px',
                        fontWeight: 600,
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {item.quantity}
                      </span>
                      <button 
                        onClick={() => updateQuantity(item.itemId, item.quantity + 1)}
                        style={{
                          width: '26px',
                          height: '100%',
                          background: 'transparent',
                          border: 'none',
                          cursor: 'pointer',
                          fontWeight: 700,
                          fontSize: '12px',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center'
                        }}
                      >
                        +
                      </button>
                    </div>

                    {/* Price and delete */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span style={{
                        fontSize: '13px',
                        fontWeight: 700,
                        color: 'var(--c-primary)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {formatCurrency(item.price * item.quantity)}
                      </span>
                      <button 
                        onClick={() => removeItem(item.itemId)}
                        style={{
                          background: 'transparent',
                          border: 'none',
                          cursor: 'pointer',
                          color: '#94A3B8',
                          padding: '4px'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.color = 'var(--c-danger-red)'}
                        onMouseLeave={(e) => e.currentTarget.style.color = '#94A3B8'}
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>

        {/* 4. Voucher & Checkout Footer */}
        {items.length > 0 && (
          <div style={{
            padding: '16px 20px',
            borderTop: '1px solid var(--c-border-subtle)',
            backgroundColor: '#FFFFFF',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px'
          }}>
            {/* Voucher input */}
            <div>
              {voucher ? (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '8px 12px',
                  backgroundColor: 'var(--c-gold-light)',
                  border: '1px dashed var(--c-gold)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '12px'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--c-text-gold)' }}>
                    <Tag size={14} />
                    <span>Mã ưu đãi: <strong>{voucher.code}</strong> (-{formatCurrency(discountAmount)})</span>
                  </div>
                  <button 
                    onClick={removeVoucher}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'var(--c-danger-red)',
                      cursor: 'pointer',
                      fontWeight: 600,
                      fontSize: '11px'
                    }}
                  >
                    Gỡ bỏ
                  </button>
                </div>
              ) : (
                <form onSubmit={handleApplyVoucher} style={{ display: 'flex', gap: '6px' }}>
                  <input 
                    type="text"
                    placeholder="Nhập mã voucher (vd: BEAUTY10)..."
                    value={inputVoucher}
                    onChange={(e) => setInputVoucher(e.target.value)}
                    style={{
                      flex: 1,
                      padding: '8px 12px',
                      fontSize: '12px',
                      border: '1px solid var(--c-border)',
                      borderRadius: 'var(--radius-pill)',
                      outline: 'none'
                    }}
                  />
                  <button 
                    type="submit"
                    style={{
                      padding: '8px 14px',
                      backgroundColor: 'var(--c-primary)',
                      color: '#FFFFFF',
                      border: 'none',
                      borderRadius: 'var(--radius-pill)',
                      fontSize: '12px',
                      fontWeight: 600,
                      cursor: 'pointer'
                    }}
                  >
                    Áp dụng
                  </button>
                </form>
              )}
              {voucherError && (
                <div style={{ color: 'var(--c-danger-red)', fontSize: '11px', marginTop: '4px' }}>
                  {voucherError}
                </div>
              )}
            </div>

            {/* Calculations Breakdown */}
            <div style={{ fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--c-text-muted)' }}>
                <span>Tạm tính</span>
                <span style={{ fontFamily: 'var(--font-mono)' }}>{formatCurrency(subtotal)}</span>
              </div>
              {discountAmount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--c-safe-green)' }}>
                  <span>Giảm giá voucher</span>
                  <span style={{ fontFamily: 'var(--font-mono)' }}>-{formatCurrency(discountAmount)}</span>
                </div>
              )}
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--c-text-muted)' }}>
                <span>Phí vận chuyển</span>
                <span style={{ fontFamily: 'var(--font-mono)' }}>
                  {isFreeShipping ? 'MIỄN PHÍ' : formatCurrency(shippingFee)}
                </span>
              </div>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: '16px',
                fontWeight: 700,
                color: 'var(--c-primary)',
                paddingTop: '8px',
                borderTop: '1px solid var(--c-border-subtle)'
              }}>
                <span>Tổng thanh toán</span>
                <span style={{ color: 'var(--c-gold-hover)', fontFamily: 'var(--font-mono)' }}>
                  {formatCurrency(totalAmount)}
                </span>
              </div>
            </div>

            {/* Checkout Action Button */}
            <button 
              onClick={() => {
                setCartOpen(false);
                onNavigate('checkout');
              }}
              className="btn-luxury-gold pulse-glow"
              style={{
                width: '100%',
                padding: '14px',
                fontSize: '14px',
                marginTop: '4px'
              }}
            >
              <span>Tiến hành thanh toán</span>
              <ArrowRight size={16} />
            </button>
          </div>
        )}
      </div>
    </>
  );
};
