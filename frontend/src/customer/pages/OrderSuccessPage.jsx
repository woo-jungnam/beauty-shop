import React, { useState, useEffect, useMemo } from 'react';
import {
  CheckCircle2,
  Copy,
  Check,
  ArrowRight,
  ShieldCheck,
  Clock,
  Truck,
  Sparkles,
  QrCode,
  Building2,
  CreditCard,
  UserCheck
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { getGuestSessionId } from '../../shared/utils/session';
import { serverBaseUrl } from '../../shared/api/baseUrl';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

const getPaymentWsUrl = (orderCode, orderId) => {
  const httpUrl = serverBaseUrl(import.meta.env.VITE_API_BASE_URL) || (typeof window !== 'undefined' ? window.location.origin : '');
  const wsProtocol = httpUrl.startsWith('https') ? 'wss:' : 'ws:';
  const host = httpUrl ? httpUrl.replace(/^https?:\/\//, '') : 'localhost:8080';
  return `${wsProtocol}//${host}/ws/payment?orderCode=${encodeURIComponent(orderCode || '')}&orderId=${encodeURIComponent(orderId || '')}`;
};

export const OrderSuccessPage = ({
  onNavigate,
  orderId = null,
  orderCode = '',
  paymentMethod = 'BANK',
  amount = 0
}) => {
  const [copiedAccount, setCopiedAccount] = useState(false);
  const [copiedContent, setCopiedContent] = useState(false);
  const [countdown, setCountdown] = useState(1800); // 30 minutes payment window
  const [isPaid, setIsPaid] = useState(false);
  const [orderDetail, setOrderDetail] = useState(() => {
    try {
      const stored = sessionStorage.getItem('last_created_order');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  // Fetch full real order information if orderId exists
  useEffect(() => {
    let isMounted = true;
    const fetchOrder = async () => {
      const targetId = orderId || orderDetail?.id;
      if (!targetId) return;

      try {
        const sessionId = getGuestSessionId();
        const res = await apiClient.get(`/api/v1/orders/${targetId}`, {
          headers: { 'X-Guest-Session-Id': sessionId }
        });
        const data = res?.data || res;
        if (isMounted && data) {
          setOrderDetail(data);
          if (data.paymentStatus === 'PAID') {
            setIsPaid(true);
          }
        }
      } catch (err) {
        console.warn('Could not fetch latest order details:', err);
      }
    };

    fetchOrder();
    return () => { isMounted = false; };
  }, [orderId]);

  // Real-time WebSocket connection for instant payment push (< 100ms)
  useEffect(() => {
    const isBankPayment = paymentMethod === 'BANK' || paymentMethod === 'VIETQR_SEPAY';
    if (!isBankPayment || isPaid) return;

    const targetCode = orderCode || orderDetail?.orderCode || orderDetail?.orderNumber || '';
    const targetId = orderId || orderDetail?.id || '';
    if (!targetCode && !targetId) return;

    let ws = null;
    let pingInterval = null;

    try {
      const wsUrl = getPaymentWsUrl(targetCode, targetId);
      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        pingInterval = setInterval(() => {
          if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send('ping');
          }
        }, 25000);
      };

      ws.onmessage = (event) => {
        try {
          if (event.data === 'pong') return;
          const msg = JSON.parse(event.data);
          if (msg.type === 'PAYMENT_SUCCESS' || msg.status === 'PAID') {
            setIsPaid(true);
            setOrderDetail((prev) => ({ ...(prev || {}), paymentStatus: 'PAID', status: 'PROCESSING' }));
          }
        } catch {
          // ignore non-json
        }
      };

      ws.onerror = (err) => {
        console.warn('Payment WebSocket error, falling back to polling:', err);
      };
    } catch (err) {
      console.warn('Could not establish WebSocket:', err);
    }

    return () => {
      if (pingInterval) clearInterval(pingInterval);
      if (ws) {
        ws.close();
      }
    };
  }, [orderId, orderCode, orderDetail?.orderCode, orderDetail?.orderNumber, orderDetail?.id, paymentMethod, isPaid]);

  // Real-time polling fallback for payment confirmation webhook (every 4 seconds)
  useEffect(() => {
    const isBankPayment = paymentMethod === 'BANK' || paymentMethod === 'VIETQR_SEPAY';
    if (!isBankPayment || isPaid || countdown <= 0) return;

    const targetId = orderId || orderDetail?.id;
    if (!targetId) return;

    const interval = setInterval(async () => {
      try {
        const sessionId = getGuestSessionId();
        const res = await apiClient.get(`/api/v1/orders/${targetId}`, {
          headers: { 'X-Guest-Session-Id': sessionId }
        });
        const data = res?.data || res;
        if (data && data.paymentStatus === 'PAID') {
          setIsPaid(true);
          setOrderDetail(data);
          clearInterval(interval);
        }
      } catch {
        // quiet retry
      }
    }, 4000);

    return () => clearInterval(interval);
  }, [orderId, orderDetail?.id, paymentMethod, isPaid, countdown]);

  // Countdown timer
  useEffect(() => {
    if (countdown <= 0 || isPaid) return;
    const timer = setInterval(() => setCountdown((c) => c - 1), 1000);
    return () => clearInterval(timer);
  }, [countdown, isPaid]);

  const minutes = Math.floor(countdown / 60);
  const seconds = countdown % 60;
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;

  const currentOrderCode = orderDetail?.orderNumber || orderCode || 'ORD-Chờ-Xác-Nhận';
  const currentTotalAmount = orderDetail?.totalAmount || amount || 0;
  const isBankMethod = paymentMethod === 'BANK' || paymentMethod === 'VIETQR_SEPAY' || orderDetail?.paymentMethod === 'BANK';

  // Compute VietQR image URL from instruction or dynamic VietQR format
  const paymentInstruction = orderDetail?.paymentInstruction;
  const qrImageUrl = useMemo(() => {
    if (paymentInstruction?.qrCodeUrl) {
      return paymentInstruction.qrCodeUrl;
    }
    const bankBin = '970422'; // MBBank BIN
    const bankAccount = paymentInstruction?.bankAccountNumber || '0902588750';
    const cleanAmount = Math.round(Number(currentTotalAmount) || 0);
    const syntax = encodeURIComponent(currentOrderCode);
    const accName = encodeURIComponent(paymentInstruction?.bankAccountName || 'BEAUTYSHOP');
    return `https://img.vietqr.io/image/${bankBin}-${bankAccount}-compact.jpg?amount=${cleanAmount}&addInfo=${syntax}&accountName=${accName}`;
  }, [paymentInstruction, currentTotalAmount, currentOrderCode]);

  const bankName = paymentInstruction?.bankName || 'MBBank (Ngân hàng Quân Đội)';
  const bankAccountName = paymentInstruction?.bankAccountName || 'BEAUTYSHOP';
  const bankAccountNumber = paymentInstruction?.bankAccountNumber || '0902588750';
  const transferSyntax = paymentInstruction?.transferSyntax || currentOrderCode;

  const handleCopy = (text, type) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    if (type === 'account') {
      setCopiedAccount(true);
      setTimeout(() => setCopiedAccount(false), 2000);
    } else {
      setCopiedContent(true);
      setTimeout(() => setCopiedContent(false), 2000);
    }
  };

  return (
    <div className="customer-container" style={{ padding: '40px 20px 80px 20px', maxWidth: '780px' }}>
      <div style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 'var(--radius-lg)',
        border: '1.5px solid var(--c-border-subtle)',
        padding: '40px 32px',
        boxShadow: 'var(--shadow-card)',
        textAlign: 'center'
      }}>
        {/* Status Header - Dynamically reflecting Payment State */}
        <div style={{
          width: '64px',
          height: '64px',
          borderRadius: '50%',
          backgroundColor: isPaid || !isBankMethod ? 'var(--c-safe-green-bg, #ECFDF5)' : 'var(--c-primary-light, #FFF0F3)',
          color: isPaid || !isBankMethod ? 'var(--c-safe-green, #059669)' : 'var(--c-primary, #D45D79)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 16px auto',
          border: isPaid || !isBankMethod ? '2px solid var(--c-safe-green-border, #A7F3D0)' : '2px solid var(--c-gold-border)'
        }}>
          {isPaid || !isBankMethod ? <CheckCircle2 size={34} /> : <QrCode size={32} />}
        </div>

        {/* State Tag */}
        <div style={{ marginBottom: '8px' }}>
          <span style={{
            fontSize: '11px',
            fontWeight: 800,
            padding: '3px 10px',
            borderRadius: '9999px',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            backgroundColor: isPaid ? '#ECFDF5' : (isBankMethod ? '#FFF0F3' : '#ECFDF5'),
            color: isPaid ? '#059669' : (isBankMethod ? '#D45D79' : '#059669'),
            border: isPaid ? '1px solid #A7F3D0' : (isBankMethod ? '1px solid var(--c-gold-border)' : '1px solid #A7F3D0')
          }}>
            {isPaid ? 'ĐÃ THANH TOÁN THÀNH CÔNG' : (isBankMethod ? 'CHỜ THANH TOÁN CHUYỂN KHOẢN' : 'ĐƠN HÀNG COD ĐÃ TIẾP NHẬN')}
          </span>
        </div>

        <h1 style={{ fontSize: '24px', margin: '0 0 8px 0', fontFamily: 'var(--font-sans)', fontWeight: 800, color: '#1F2937' }}>
          {isPaid ? 'Thanh Toán Thành Công!' : (isBankMethod ? 'Đơn Hàng Đã Tạo — Vui Lòng Chuyển Khoản' : 'Đặt Hàng Thành Công!')}
        </h1>
        <p style={{ fontSize: '13.5px', color: 'var(--c-text-muted)', margin: '0 0 24px 0' }}>
          Mã số đơn hàng: <strong style={{ color: 'var(--c-deal-red, #E11D48)', fontFamily: 'var(--font-mono)' }}>{currentOrderCode}</strong>
          {isBankMethod && !isPaid && ' • Đơn hàng sẽ được xử lý ngay sau khi hệ thống nhận được tiền chuyển khoản.'}
        </p>

        {/* ================= VIETQR REALTIME PAYMENT SECTION ================= */}
        {isBankMethod && (
          <div style={{
            maxWidth: '520px',
            margin: '0 auto 32px auto',
            padding: '24px',
            borderRadius: 'var(--radius-md)',
            backgroundColor: isPaid ? 'var(--c-safe-green-bg, #ECFDF5)' : '#FFFFFF',
            border: isPaid ? '2px solid var(--c-safe-green)' : '2px dashed var(--c-primary, #D45D79)',
            transition: 'all 0.4s ease'
          }}>
            {isPaid ? (
              <div style={{ padding: '20px 10px' }}>
                <div style={{
                  width: '52px',
                  height: '52px',
                  borderRadius: 'var(--radius-md)',
                  backgroundColor: 'var(--c-safe-green)',
                  color: '#FFFFFF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 12px auto'
                }}>
                  <Check size={28} />
                </div>
                <h3 style={{ fontSize: '20px', fontWeight: 800, color: 'var(--c-safe-green)', margin: '0 0 6px 0' }}>
                  Đã Nhận Thanh Toán Chuyển Khoản!
                </h3>
                <p style={{ fontSize: '13px', color: 'var(--c-text-main)', margin: 0 }}>
                  Hệ thống SePay đã tự động khớp lệnh thanh toán thành công. Đơn hàng đang được điều phối sang kho để đóng gói.
                </p>
              </div>
            ) : (
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--c-text-gold)', fontWeight: 700 }}>
                    <Sparkles size={14} />
                    <span>QUÉT MÃ VIETQR QUA ỨNG DỤNG NGÂN HÀNG</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: '#DC2626', fontWeight: 700 }}>
                    <Clock size={13} />
                    <span>{formattedTime}</span>
                  </div>
                </div>

                {/* Real QR Code Graphic from VietQR */}
                <div style={{
                  width: '210px',
                  height: '210px',
                  backgroundColor: '#FFFFFF',
                  margin: '0 auto 16px auto',
                  padding: '8px',
                  borderRadius: '12px',
                  border: '1px solid var(--c-border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.06)'
                }}>
                  <img
                    src={qrImageUrl}
                    alt="VietQR Payment Code"
                    style={{ width: '100%', height: '100%', objectFit: 'contain', borderRadius: '6px' }}
                    onError={(e) => {
                      e.target.style.display = 'none';
                      const fallback = e.target.nextSibling;
                      if (fallback) fallback.style.display = 'flex';
                    }}
                  />
                  <div style={{ display: 'none', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
                    <QrCode size={120} color="var(--c-primary)" />
                    <span style={{ fontSize: '11px', color: 'var(--c-text-muted)', marginTop: '4px' }}>Mã QR Chuyển Khoản</span>
                  </div>
                </div>

                <div style={{ fontSize: '11px', color: 'var(--c-text-muted)', marginBottom: '16px' }}>
                  Mở ứng dụng ngân hàng bất kỳ (Vietcombank, MB, Techcombank, VPBank...) quét mã để tự động điền số tiền và nội dung.
                </div>

                {/* Account Details Copy Table */}
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                  fontSize: '12px',
                  textAlign: 'left',
                  backgroundColor: '#FFFFFF',
                  padding: '14px',
                  borderRadius: '8px',
                  border: '1px solid var(--c-border-subtle)'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: 'var(--c-text-light)' }}>Ngân hàng thụ hưởng:</span>
                    <strong>{bankName}</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: 'var(--c-text-light)' }}>Chủ tài khoản:</span>
                    <strong>{bankAccountName}</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: 'var(--c-text-light)' }}>Số tài khoản:</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <strong style={{ fontFamily: 'var(--font-mono)' }}>{bankAccountNumber}</strong>
                      <button
                        onClick={() => handleCopy(bankAccountNumber, 'account')}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--c-primary)' }}
                        title="Sao chép số tài khoản"
                      >
                        {copiedAccount ? <Check size={13} color="var(--c-safe-green)" /> : <Copy size={13} />}
                      </button>
                    </div>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ color: 'var(--c-text-light)' }}>Số tiền chính xác:</span>
                    <strong style={{ color: 'var(--c-deal-red, #E11D48)', fontSize: '15px', fontFamily: 'var(--font-mono)' }}>
                      {formatCurrency(currentTotalAmount)}
                    </strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '6px', borderTop: '1px dashed #E2E8F0' }}>
                    <span style={{ color: 'var(--c-text-light)' }}>Nội dung chuyển khoản:</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <strong style={{ color: 'var(--c-primary)', fontFamily: 'var(--font-mono)' }}>{transferSyntax}</strong>
                      <button
                        onClick={() => handleCopy(transferSyntax, 'content')}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--c-primary)' }}
                        title="Sao chép nội dung"
                      >
                        {copiedContent ? <Check size={13} color="var(--c-safe-green)" /> : <Copy size={13} />}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ================= COD DETAILS SECTION ================= */}
        {!isBankMethod && (
          <div style={{
            maxWidth: '520px',
            margin: '0 auto 32px auto',
            padding: '20px',
            borderRadius: 'var(--radius-md)',
            backgroundColor: 'var(--c-canvas)',
            border: '1px solid var(--c-border)',
            textAlign: 'left',
            fontSize: '13px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', color: 'var(--c-primary)', fontWeight: 700 }}>
              <Truck size={18} color="var(--c-gold)" />
              <span>Hình Thức: Thanh Toán Tiền Mặt Khi Nhận Hàng (COD)</span>
            </div>
            <p style={{ color: 'var(--c-text-muted)', margin: '0 0 10px 0', lineHeight: 1.5 }}>
              Đơn hàng của bạn sẽ được nhân viên đóng gói cẩn thận. Bạn vui lòng chuẩn bị số tiền <strong>{formatCurrency(currentTotalAmount)}</strong> khi shipper giao hàng tới.
            </p>
            <div style={{ fontSize: '11.5px', color: 'var(--c-safe-green)', fontWeight: 600 }}>
              Được phép mở hộp đồng kiểm tra tem phụ và hạn sử dụng trước khi thanh toán.
            </div>
          </div>
        )}

        {/* Next Steps */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '16px',
          textAlign: 'left',
          fontSize: '13px',
          padding: '20px 0',
          borderTop: '1px solid var(--c-border-subtle)',
          marginBottom: '28px'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '4px' }}>
              <Truck size={16} color="var(--c-gold)" />
              <span>Giao Hàng Nhanh Chóng</span>
            </div>
            <div style={{ color: 'var(--c-text-muted)' }}>
              Đơn hàng sẽ được chuyển tới tay bạn trong vòng 1-2 ngày làm việc.
            </div>
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '4px' }}>
              <ShieldCheck size={16} color="var(--c-safe-green)" />
              <span>Đồng Kiểm & Bảo Hiểm</span>
            </div>
            <div style={{ color: 'var(--c-text-muted)' }}>
              Được mở hộp kiểm tra tem phụ, bảo hiểm đổi trả 7 ngày nếu không hợp da.
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: '14px' }}>
          <button onClick={() => onNavigate('products')} className="btn-luxury-outline">
            Tiếp Tục Mua Sắm
          </button>
          <button onClick={() => onNavigate('profile?tab=orders')} className="btn-luxury-primary">
            <span>Theo Dõi Đơn Hàng Của Tôi</span>
            <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default OrderSuccessPage;
