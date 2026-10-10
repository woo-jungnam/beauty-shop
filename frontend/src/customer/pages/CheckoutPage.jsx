import React, { useState } from 'react';
import {
  ShieldCheck,
  CreditCard,
  QrCode,
  Check,
  ArrowLeft,
  Lock,
  Sparkles,
  AlertCircle
} from 'lucide-react';
import { useCustomerCart } from '../stores/customerCartStore';
import { useAuth } from '../../app/providers/AuthProvider';
import { apiClient } from '../../shared/api/client';
import { getGuestSessionId } from '../../shared/utils/session';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const CheckoutPage = ({ onNavigate }) => {
  const {
    items,
    subtotal,
    discountAmount,
    voucher,
    shippingFee,
    isFreeShipping,
    totalAmount,
    clearCart
  } = useCustomerCart();

  const { user, isAuthenticated } = useAuth();

  const [formData, setFormData] = useState({
    fullName: user?.fullName || '',
    phone: user?.phoneNumber || user?.phone || '',
    email: user?.email || '',
    city: 'TP. Hồ Chí Minh',
    district: 'Quận 1',
    ward: 'Phường Bến Nghé',
    address: '',
    note: '',
    paymentMethod: 'BANK', // 'BANK' (VietQR / SePay) or 'COD' (Tiền mặt)
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  if (items.length === 0) {
    return (
      <div className="customer-container" style={{ padding: '80px 20px', textAlign: 'center' }}>
        <h2 style={{ fontFamily: 'var(--font-serif)', color: 'var(--c-primary)' }}>Giỏ hàng của bạn đang trống</h2>
        <p style={{ color: 'var(--c-text-muted)', marginTop: '8px' }}>Vui lòng chọn sản phẩm vào giỏ hàng trước khi tiến hành thanh toán.</p>
        <button onClick={() => onNavigate('products')} className="btn-luxury-primary" style={{ marginTop: '20px' }}>
          Khám phá sản phẩm
        </button>
      </div>
    );
  }

  const handleSubmitOrder = async (e) => {
    e.preventDefault();
    setErrorMessage(null);

    // 1. Validation
    if (!formData.fullName.trim() || !formData.phone.trim() || !formData.address.trim()) {
      setErrorMessage('Vui lòng điền đầy đủ họ tên, số điện thoại và địa chỉ nhận hàng.');
      return;
    }

    const phoneRegex = /^(0|\+84)[35789][0-9]{8}$/;
    if (!phoneRegex.test(formData.phone.trim())) {
      setErrorMessage('Số điện thoại không hợp lệ. Vui lòng nhập số điện thoại Việt Nam 10 chữ số (ví dụ: 0987654321).');
      return;
    }

    setIsSubmitting(true);

    try {
      const sessionId = getGuestSessionId();

      // 2. Ensure backend cart has items before placing order
      try {
        const cartCheckRes = await apiClient.get(`/api/v1/cart?sessionId=${encodeURIComponent(sessionId)}`);
        const currentCart = cartCheckRes?.data || cartCheckRes;
        const hasBackendItems = Array.isArray(currentCart?.items) && currentCart.items.length > 0;

        if (!hasBackendItems) {
          for (const item of items) {
            let variantId = item.variantId;
            if (!variantId || isNaN(Number(variantId))) {
              try {
                const pDetail = await apiClient.get(`/api/v1/products/${item.productId}`);
                const prod = pDetail?.data || pDetail;
                variantId = prod?.variants?.[0]?.id || item.productId;
              } catch {
                variantId = item.productId;
              }
            }

            if (variantId && !isNaN(Number(variantId))) {
              await apiClient.post('/api/v1/cart/add', {
                variantId: Number(variantId),
                quantity: item.quantity || 1,
                sessionId: sessionId
              });
            }
          }
        }
      } catch (syncErr) {
        console.warn('Cart check notice:', syncErr);
      }

      // 3. Prepare real Checkout Payload
      const idempotencyKey = `ord_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
      const checkoutPayload = {
        sessionId: sessionId,
        customerName: formData.fullName.trim(),
        customerPhone: formData.phone.trim(),
        shippingAddress: formData.address.trim(),
        ward: formData.ward.trim(),
        district: formData.district.trim(),
        city: formData.city.trim(),
        paymentMethod: formData.paymentMethod, // 'BANK' or 'COD'
        notes: formData.note ? formData.note.trim() : '',
        voucherCode: voucher?.code || null
      };

      // 4. Send POST /api/v1/orders/checkout to backend
      const res = await apiClient.post('/api/v1/orders/checkout', checkoutPayload, {
        headers: {
          'Idempotency-Key': idempotencyKey,
          'X-Guest-Session-Id': sessionId
        }
      });

      const orderData = res?.data || res;

      if (!orderData || (!orderData.orderNumber && !orderData.id)) {
        throw new Error('Không nhận được thông tin xác nhận đơn hàng từ máy chủ.');
      }

      // 5. Store completed order information for OrderSuccessPage
      sessionStorage.setItem('last_created_order', JSON.stringify(orderData));

      // 6. Clear local cart
      clearCart();

      // 7. Navigate to Order Success Page
      const orderId = orderData.id || '';
      const orderCode = orderData.orderNumber || `BS-${orderId}`;
      const method = orderData.paymentMethod || formData.paymentMethod;
      const amount = orderData.totalAmount || totalAmount;

      onNavigate(`order-success?id=${orderId}&code=${orderCode}&method=${method}&amount=${amount}`);
    } catch (err) {
      console.error('Checkout error:', err);
      const msg = err.response?.data?.message || err.message || 'Có lỗi xảy ra khi tạo đơn hàng. Vui lòng kiểm tra lại tồn kho hoặc thử lại.';
      setErrorMessage(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="customer-container" style={{ padding: '32px 20px 80px 20px' }}>
      {/* Title */}
      <div style={{ marginBottom: '28px' }}>
        <button
          onClick={() => onNavigate('products')}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--c-text-muted)',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            cursor: 'pointer',
            padding: 0,
            marginBottom: '10px'
          }}
        >
          <ArrowLeft size={14} /> Quay lại mua sắm
        </button>
        <h1 style={{ fontSize: '28px', margin: 0, fontFamily: 'var(--font-serif)', color: 'var(--c-primary)' }}>
          Thanh Toán Đơn Hàng (1-Page Checkout)
        </h1>
      </div>

      {errorMessage && (
        <div style={{
          backgroundColor: '#FEF2F2',
          border: '1px solid #FCA5A5',
          borderRadius: 'var(--radius-md)',
          padding: '14px 18px',
          marginBottom: '24px',
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          color: '#991B1B',
          fontSize: '13.5px'
        }}>
          <AlertCircle size={20} color="#DC2626" style={{ flexShrink: 0 }} />
          <span>{errorMessage}</span>
        </div>
      )}

      <form onSubmit={handleSubmitOrder}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
          gap: '36px',
          alignItems: 'start'
        }}>
          {/* ================= LEFT COLUMN: FORM DETAILS ================= */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>

            {/* Section 1: Customer Contact Info */}
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border)',
              padding: '24px'
            }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, margin: '0 0 16px 0', color: 'var(--c-primary)' }}>
                1. Thông Tin Người Nhận
              </h3>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div>
                  <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '6px' }}>HỌ VÀ TÊN *</label>
                  <input
                    required
                    type="text"
                    placeholder="Nguyễn Văn A"
                    value={formData.fullName}
                    onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                    style={{ width: '100%', padding: '10px 12px', fontSize: '13px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', outline: 'none' }}
                  />
                </div>

                <div>
                  <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '6px' }}>SỐ ĐIỆN THOẠI *</label>
                  <input
                    required
                    type="tel"
                    placeholder="0987654321"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    style={{ width: '100%', padding: '10px 12px', fontSize: '13px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', outline: 'none' }}
                  />
                </div>
              </div>

              <div style={{ marginTop: '14px' }}>
                <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '6px' }}>EMAIL NHẬN THÔNG BÁO VẬN ĐƠN</label>
                <input
                  type="email"
                  placeholder="email@example.com"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', fontSize: '13px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', outline: 'none' }}
                />
              </div>
            </div>

            {/* Section 2: Shipping Address */}
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border)',
              padding: '24px'
            }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, margin: '0 0 16px 0', color: 'var(--c-primary)' }}>
                2. Địa Chỉ Nhận Hàng
              </h3>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '10px', marginBottom: '14px' }}>
                <div>
                  <label style={{ fontSize: '11px', fontWeight: 700, display: 'block', marginBottom: '4px' }}>TỈNH / THÀNH *</label>
                  <select
                    value={formData.city}
                    onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                    style={{ width: '100%', padding: '9px 10px', fontSize: '12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', backgroundColor: '#FFFFFF', outline: 'none' }}
                  >
                    <option value="TP. Hồ Chí Minh">TP. Hồ Chí Minh</option>
                    <option value="Hà Nội">Hà Nội</option>
                    <option value="Đà Nẵng">Đà Nẵng</option>
                    <option value="Cần Thơ">Cần Thơ</option>
                    <option value="Hải Phòng">Hải Phòng</option>
                  </select>
                </div>

                <div>
                  <label style={{ fontSize: '11px', fontWeight: 700, display: 'block', marginBottom: '4px' }}>QUẬN / HUYỆN *</label>
                  <select
                    value={formData.district}
                    onChange={(e) => setFormData({ ...formData, district: e.target.value })}
                    style={{ width: '100%', padding: '9px 10px', fontSize: '12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', backgroundColor: '#FFFFFF', outline: 'none' }}
                  >
                    <option value="Quận 1">Quận 1</option>
                    <option value="Quận 3">Quận 3</option>
                    <option value="Quận 7">Quận 7</option>
                    <option value="Bình Thạnh">Bình Thạnh</option>
                    <option value="Phú Nhuận">Phú Nhuận</option>
                    <option value="Hoàn Kiếm">Hoàn Kiếm</option>
                    <option value="Ba Đình">Ba Đình</option>
                  </select>
                </div>

                <div>
                  <label style={{ fontSize: '11px', fontWeight: 700, display: 'block', marginBottom: '4px' }}>PHƯỜNG / XÃ *</label>
                  <select
                    value={formData.ward}
                    onChange={(e) => setFormData({ ...formData, ward: e.target.value })}
                    style={{ width: '100%', padding: '9px 10px', fontSize: '12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', backgroundColor: '#FFFFFF', outline: 'none' }}
                  >
                    <option value="Phường Bến Nghé">Phường Bến Nghé</option>
                    <option value="Phường Bến Thành">Phường Bến Thành</option>
                    <option value="Phường Đa Kao">Phường Đa Kao</option>
                    <option value="Phường Tân Định">Phường Tân Định</option>
                    <option value="Phường Tràng Tiền">Phường Tràng Tiền</option>
                  </select>
                </div>
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '6px' }}>ĐỊA CHỈ CHI TIẾT (SỐ NHÀ, TÊN ĐƯỜNG) *</label>
                <input
                  required
                  type="text"
                  placeholder="Ví dụ: 123 Lê Lợi, Tòa nhà Vincom..."
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', fontSize: '13px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', outline: 'none' }}
                />
              </div>

              <div style={{ marginTop: '14px' }}>
                <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '6px' }}>GHI CHÚ GIAO HÀNG</label>
                <input
                  type="text"
                  placeholder="Ví dụ: Giao giờ hành chính, gọi trước khi đến..."
                  value={formData.note}
                  onChange={(e) => setFormData({ ...formData, note: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', fontSize: '13px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--c-border)', outline: 'none' }}
                />
              </div>
            </div>

            {/* Section 3: Payment Method */}
            <div style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border)',
              padding: '24px'
            }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, margin: '0 0 16px 0', color: 'var(--c-primary)' }}>
                3. Phương Thức Thanh Toán
              </h3>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {/* Method 1: VietQR (BANK) */}
                <label style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '14px 16px',
                  borderRadius: 'var(--radius-sm)',
                  border: formData.paymentMethod === 'BANK' ? '2px solid var(--c-gold)' : '1px solid var(--c-border)',
                  backgroundColor: formData.paymentMethod === 'BANK' ? 'var(--c-gold-light)' : '#FFFFFF',
                  cursor: 'pointer'
                }}>
                  <input
                    type="radio"
                    name="paymentMethod"
                    value="BANK"
                    checked={formData.paymentMethod === 'BANK'}
                    onChange={(e) => setFormData({ ...formData, paymentMethod: e.target.value })}
                    style={{ accentColor: 'var(--c-gold)' }}
                  />
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <strong style={{ fontSize: '13px', color: 'var(--c-primary)' }}>
                        Quét Mã VietQR Chuyển Khoản Tức Thì (SePay)
                      </strong>
                      <span className="badge-dermatology safe" style={{ fontSize: '10px' }}>KHUYÊN DÙNG</span>
                    </div>
                    <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '2px' }}>
                      Mã VietQR tự động sinh từ hệ thống ngân hàng, tự khớp lệnh sau 1 giây.
                    </div>
                  </div>
                </label>

                {/* Method 2: COD */}
                <label style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '14px 16px',
                  borderRadius: 'var(--radius-sm)',
                  border: formData.paymentMethod === 'COD' ? '2px solid var(--c-gold)' : '1px solid var(--c-border)',
                  backgroundColor: formData.paymentMethod === 'COD' ? 'var(--c-gold-light)' : '#FFFFFF',
                  cursor: 'pointer'
                }}>
                  <input
                    type="radio"
                    name="paymentMethod"
                    value="COD"
                    checked={formData.paymentMethod === 'COD'}
                    onChange={(e) => setFormData({ ...formData, paymentMethod: e.target.value })}
                    style={{ accentColor: 'var(--c-gold)' }}
                  />
                  <div style={{ flex: 1 }}>
                    <strong style={{ fontSize: '13px', color: 'var(--c-primary)' }}>
                      Thanh Toán Tiền Mặt Khi Nhận Hàng (COD)
                    </strong>
                    <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '2px' }}>
                      Kiểm tra hàng trước khi thanh toán cho nhân viên giao hàng.
                    </div>
                  </div>
                </label>
              </div>
            </div>
          </div>

          {/* ================= RIGHT COLUMN: ORDER SUMMARY ================= */}
          <div style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--c-border)',
            padding: '24px',
            boxShadow: 'var(--shadow-card)',
            position: 'sticky',
            top: '90px'
          }}>
            <h3 style={{ fontSize: '17px', fontWeight: 700, margin: '0 0 16px 0', fontFamily: 'var(--font-serif)' }}>
              Đơn Hàng ({items.length} món)
            </h3>

            {/* Items mini list */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '280px', overflowY: 'auto', marginBottom: '20px', paddingRight: '4px' }}>
              {items.map((item) => (
                <div key={item.itemId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0, flex: 1 }}>
                    <div style={{ width: '40px', height: '40px', borderRadius: '4px', backgroundColor: 'var(--c-canvas-subtle)', overflow: 'hidden', flexShrink: 0 }}>
                      {item.image && <img src={item.image} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />}
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600, color: 'var(--c-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {item.name}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>
                        {item.variantName} × {item.quantity}
                      </div>
                    </div>
                  </div>
                  <span style={{ fontWeight: 700, fontFamily: 'var(--font-mono)', marginLeft: '12px' }}>
                    {formatCurrency(item.price * item.quantity)}
                  </span>
                </div>
              ))}
            </div>

            {/* Calculations Breakdown */}
            <div style={{ borderTop: '1px solid var(--c-border-subtle)', paddingTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--c-text-muted)' }}>
                <span>Tạm tính</span>
                <span style={{ fontFamily: 'var(--font-mono)' }}>{formatCurrency(subtotal)}</span>
              </div>
              {discountAmount > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--c-safe-green)' }}>
                  <span>Voucher giảm giá</span>
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
                fontSize: '18px',
                fontWeight: 800,
                color: 'var(--c-primary)',
                paddingTop: '12px',
                borderTop: '1px solid var(--c-border-subtle)',
                marginTop: '4px'
              }}>
                <span>Tổng cộng</span>
                <span style={{ color: 'var(--c-gold-hover)', fontFamily: 'var(--font-mono)' }}>
                  {formatCurrency(totalAmount)}
                </span>
              </div>
            </div>

            {/* Submit Order Button */}
            <button
              type="submit"
              disabled={isSubmitting}
              className="btn-luxury-gold pulse-glow"
              style={{
                width: '100%',
                padding: '14px',
                fontSize: '15px',
                marginTop: '20px',
                cursor: isSubmitting ? 'not-allowed' : 'pointer',
                opacity: isSubmitting ? 0.75 : 1
              }}
            >
              <Lock size={15} />
              <span>{isSubmitting ? 'Đang Xử Lý Đơn Hàng...' : 'Hoàn Tất Đặt Hàng Ngay'}</span>
            </button>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', fontSize: '11px', color: '#94A3B8', marginTop: '12px' }}>
              <ShieldCheck size={14} color="var(--c-safe-green)" />
              <span>Thông tin thanh toán được mã hóa SSL 256-bit an toàn</span>
            </div>
          </div>
        </div>
      </form>
    </div>
  );
};
