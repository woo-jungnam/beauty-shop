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
  QrCode
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { getGuestSessionId } from '../../shared/utils/session';
import { serverBaseUrl } from '../../shared/api/baseUrl';
import { orderBenefitDestination } from '../../features/spa/benefitModel';
import { resolveMediaUrl } from '../../shared/utils/formatters';

const formatCurrency = (val) => {
  const amount = val == null || typeof val === 'boolean' || String(val).trim() === '' ? NaN : Number(val);
  return Number.isFinite(amount) && amount >= 0
    ? new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
    : 'Chưa có dữ liệu';
};

const validOrderId = value => /^[1-9][0-9]*$/.test(String(value ?? '')) ? String(value) : null;

const getPaymentWsUrl = (subscriptionToken) => {
  const httpUrl = serverBaseUrl(import.meta.env.VITE_API_BASE_URL) || (typeof window !== 'undefined' ? window.location.origin : '');
  const wsProtocol = httpUrl.startsWith('https') ? 'wss:' : 'ws:';
  const host = httpUrl ? httpUrl.replace(/^https?:\/\//, '') : 'localhost:8080';
  return `${wsProtocol}//${host}/ws/payment?subscriptionToken=${encodeURIComponent(subscriptionToken)}`;
};

export const OrderSuccessPage = ({
  onNavigate,
  orderId = null
}) => {
  const [copiedAccount, setCopiedAccount] = useState(false);
  const [copiedContent, setCopiedContent] = useState(false);
  const [countdown, setCountdown] = useState(null); // Derived from the server deadline, never restarted on reload.
  const [clockNow, setClockNow] = useState(() => Date.now());
  const [orderError, setOrderError] = useState('');
  const [orderFetchVersion, setOrderFetchVersion] = useState(0);
  const [cachedOrderId] = useState(() => {
    try {
      const stored = sessionStorage.getItem('last_created_order');
      const cached = stored ? JSON.parse(stored) : null;
      return validOrderId(cached?.id ?? cached?.orderId);
    } catch {
      return null;
    }
  });
  const targetOrderId = orderId == null ? cachedOrderId : validOrderId(orderId);
  const [loadedOrder, setOrderDetail] = useState(null);
  const orderDetail = loadedOrder && String(loadedOrder.id) === targetOrderId ? loadedOrder : null;
  const hasVerifiedOrder = Boolean(orderDetail) && !orderError;
  const isPaid = orderDetail?.paymentStatus === 'PAID';
  const isBankMethod = ['BANK', 'VIETQR_SEPAY'].includes(orderDetail?.paymentMethod);
  const isCodMethod = orderDetail?.paymentMethod === 'COD';
  const isClosed = ['CANCELLED', 'EXPIRED', 'RETURNED', 'REFUNDED', 'RETURN_TO_SENDER'].includes(orderDetail?.status);

  useEffect(() => {
    let isMounted = true;
    const fetchOrder = async () => {
      const targetId = targetOrderId;
      if (!targetId) { setOrderError('Không có mã tham chiếu đơn hợp lệ để xác minh. Vui lòng mở đơn từ lịch sử hoặc đặt hàng lại.'); return; }
      setOrderError('');

      try {
        const sessionId = getGuestSessionId();
        const res = await apiClient.get(`/api/v1/orders/${targetId}`, {
          headers: { 'X-Guest-Session-Id': sessionId }
        });
        const data = res?.data || res;
        if (!data || String(data.id) !== targetId) throw new Error('Invalid order response');
        if (isMounted) {
          setClockNow(Date.now());
          setOrderDetail(data);
        }
      } catch (err) {
        if (isMounted) setOrderError(err.response?.data?.message || 'Chưa tải được hướng dẫn thanh toán của đơn này. Vui lòng thử lại.');
        console.warn('Could not fetch latest order details:', err);
      }
    };

    fetchOrder();
    return () => { isMounted = false; };
  }, [targetOrderId, orderFetchVersion]);

  useEffect(() => {
    if (!hasVerifiedOrder || !isBankMethod || isPaid || isClosed) return;
    const targetId = targetOrderId;

    let ws = null;
    let pingInterval = null;
    let reconnectTimer = null;

    let cancelled = false;
    const connect = async () => {
    try {
      const response = await apiClient.get(`/api/v1/orders/${targetId}/payment-subscription`, { headers: { 'X-Guest-Session-Id': getGuestSessionId() } });
      const data = response?.data || response;
      if (cancelled || !data.subscriptionToken) return;
      const wsUrl = getPaymentWsUrl(data.subscriptionToken);
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
          if (cancelled || event.data === 'pong') return;
          const msg = JSON.parse(event.data);
          if (msg.type === 'PAYMENT_SUCCESS' || msg.status === 'PAID') {
            setOrderDetail(previous => previous && String(previous.id) === targetId ? { ...previous, paymentStatus: 'PAID' } : previous);
            setOrderFetchVersion(value => value + 1);
          }
        } catch {}
      };

      ws.onclose = () => { if (!cancelled) reconnectTimer = setTimeout(connect, 2000); };
      ws.onerror = (err) => {
        console.warn('Payment WebSocket error, falling back to polling:', err);
      };
    } catch (err) {
      console.warn('Could not establish WebSocket:', err);
    }
    }; connect();

    return () => {
      cancelled = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (pingInterval) clearInterval(pingInterval);
      if (ws) {
        ws.close();
      }
    };
  }, [targetOrderId, hasVerifiedOrder, isBankMethod, isPaid, isClosed]);

  useEffect(() => {
    if (!hasVerifiedOrder || !isBankMethod || isPaid || isClosed || (orderDetail?.paymentDeadline && Date.parse(orderDetail.paymentDeadline) <= Date.now())) return;

    const targetId = targetOrderId;
    if (!targetId) return;
    let active = true;

    const interval = setInterval(async () => {
      if (orderDetail?.paymentDeadline && Date.parse(orderDetail.paymentDeadline) <= Date.now()) { clearInterval(interval); setOrderFetchVersion(value => value + 1); return; }
      try {
        const sessionId = getGuestSessionId();
        const res = await apiClient.get(`/api/v1/orders/${targetId}`, {
          headers: { 'X-Guest-Session-Id': sessionId }
        });
        const data = res?.data || res;
        if (!active || !data || String(data.id) !== targetId) return;
        setClockNow(Date.now());
        setOrderDetail(data);
        if (['CANCELLED', 'EXPIRED', 'RETURNED', 'REFUNDED', 'RETURN_TO_SENDER'].includes(data.status) || data.paymentStatus === 'PAID') clearInterval(interval);
      } catch {}
    }, 4000);

    return () => { active = false; clearInterval(interval); };
  }, [targetOrderId, orderDetail?.paymentDeadline, hasVerifiedOrder, isBankMethod, isPaid, isClosed]);

  useEffect(() => {
    const update = () => {
      setClockNow(Date.now());
      const deadline = Date.parse(orderDetail?.paymentDeadline);
      setCountdown(Number.isFinite(deadline) ? Math.max(0, Math.ceil((deadline - Date.now()) / 1000)) : null);
    };
    update(); const timer = setInterval(update, 1000); return () => clearInterval(timer);
  }, [orderDetail?.paymentDeadline]);
  const formattedTime = countdown == null ? 'Không có hạn thanh toán tự động' : countdown === 0 ? 'Đã hết hạn thanh toán' : `${String(Math.floor(countdown / 60)).padStart(2, '0')}:${String(countdown % 60).padStart(2, '0')}`;

  const currentOrderCode = orderDetail?.orderNumber || orderDetail?.orderCode;
  const currentTotalAmount = orderDetail?.totalAmount ?? null;
  const hasKnownTotal = currentTotalAmount != null && typeof currentTotalAmount !== 'boolean' && String(currentTotalAmount).trim() !== '' && Number.isFinite(Number(currentTotalAmount)) && Number(currentTotalAmount) >= 0;
  const paymentDeadline = Date.parse(orderDetail?.paymentDeadline);
  const isExpired = isBankMethod && !isPaid && (countdown === 0 || (Number.isFinite(paymentDeadline) && paymentDeadline <= clockNow) || orderDetail?.paymentStatus === 'EXPIRED');
  const paymentInstruction = orderDetail?.paymentInstruction;
  const hasBankInstructions = Boolean(hasVerifiedOrder && hasKnownTotal && Number(currentTotalAmount) > 0 && paymentInstruction?.bankAccountNumber && paymentInstruction?.transferSyntax && !isClosed && !isExpired);
  const positiveHeader = hasVerifiedOrder && !isClosed && !isExpired && (isPaid || isCodMethod);
  const header = !hasVerifiedOrder
    ? { tag: orderError ? 'CHƯA XÁC MINH ĐƯỢC ĐƠN' : 'ĐANG XÁC MINH ĐƠN', title: orderError ? 'Chưa xác minh được đơn hàng' : 'Đang xác minh đơn hàng' }
    : isClosed
      ? { tag: 'ĐƠN ĐÃ ĐÓNG', title: orderDetail.status === 'CANCELLED' ? 'Đơn hàng đã hủy' : 'Đơn hàng đã đóng thanh toán' }
      : isExpired
        ? { tag: 'HẾT HẠN THANH TOÁN', title: 'Đã hết hạn thanh toán đơn hàng' }
        : isPaid
          ? { tag: 'ĐÃ THANH TOÁN THÀNH CÔNG', title: 'Thanh Toán Thành Công!' }
          : isBankMethod
            ? hasBankInstructions
              ? { tag: 'CHỜ THANH TOÁN CHUYỂN KHOẢN', title: 'Đơn Hàng Đã Tạo — Vui Lòng Chuyển Khoản' }
              : { tag: 'CẦN CẬP NHẬT THÔNG TIN THANH TOÁN', title: 'Đơn hàng đã tạo — Chưa đủ hướng dẫn thanh toán' }
            : isCodMethod
              ? { tag: 'ĐƠN HÀNG COD ĐÃ TIẾP NHẬN', title: 'Đặt Hàng Thành Công!' }
              : { tag: 'THÔNG TIN ĐƠN HÀNG', title: 'Thông tin đơn hàng' };

  const qrImageUrl = useMemo(() => {
    return paymentInstruction?.qrCodeUrl || '';
  }, [paymentInstruction]);

  const bankName = paymentInstruction?.bankName || '';
  const bankAccountName = paymentInstruction?.bankAccountName || '';
  const bankAccountNumber = paymentInstruction?.bankAccountNumber || '';
  const transferSyntax = paymentInstruction?.transferSyntax || '';

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
    <div className="customer-container customer-order-success-page" style={{ padding: '40px 20px 80px 20px', maxWidth: '780px' }}>
      <div style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 'var(--radius-lg)',
        border: '1.5px solid var(--c-border-subtle)',
        padding: '40px 32px',
        boxShadow: 'var(--shadow-card)',
        textAlign: 'center'
      }}>
        <div style={{
          width: '64px',
          height: '64px',
          borderRadius: '50%',
          backgroundColor: positiveHeader ? 'var(--c-safe-green-bg, #ECFDF5)' : 'var(--c-canvas, #F8FAFC)',
          color: positiveHeader ? 'var(--c-safe-green, #059669)' : 'var(--c-text-muted, #64748B)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          margin: '0 auto 16px auto',
          border: positiveHeader ? '2px solid var(--c-safe-green-border, #A7F3D0)' : '2px solid var(--c-border-subtle)'
        }}>
          {positiveHeader ? <CheckCircle2 size={34} /> : hasBankInstructions ? <QrCode size={32} /> : <Clock size={32} />}
        </div>

        <div style={{ marginBottom: '8px' }}>
          <span style={{
            fontSize: '11px',
            fontWeight: 800,
            padding: '3px 10px',
            borderRadius: '9999px',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            backgroundColor: positiveHeader ? '#ECFDF5' : 'var(--c-canvas, #F8FAFC)',
            color: positiveHeader ? '#059669' : 'var(--c-text-muted, #64748B)',
            border: positiveHeader ? '1px solid #A7F3D0' : '1px solid var(--c-border-subtle)'
          }}>
            {header.tag}
          </span>
        </div>

        <h1 style={{ fontSize: '24px', margin: '0 0 8px 0', fontFamily: 'var(--font-sans)', fontWeight: 800, color: '#1F2937' }}>
          {header.title}
        </h1>
        <p style={{ fontSize: '13.5px', color: 'var(--c-text-muted)', margin: '0 0 24px 0' }}>
          {hasVerifiedOrder && currentOrderCode ? <>Mã số đơn hàng: <strong style={{ color: 'var(--c-deal-red, #E11D48)', fontFamily: 'var(--font-mono)' }}>{currentOrderCode}</strong></> : 'Thông tin đơn sẽ hiển thị sau khi hệ thống xác minh quyền truy cập.'}
          {hasBankInstructions && !isPaid && ' • Đơn hàng sẽ được xử lý sau khi hệ thống xác nhận thanh toán.'}
        </p>

        {!orderDetail && !orderError && <p role="status">Đang xác minh đơn và hướng dẫn thanh toán…</p>}
        {orderError && <div role="alert" style={{ padding: 16, background: '#fff0ed', borderRadius: 10, marginBottom: 20 }}><p>{orderError}</p><button type="button" className="btn-luxury-outline" onClick={() => setOrderFetchVersion(value => value + 1)}>Tải lại hướng dẫn thanh toán</button></div>}
        {hasVerifiedOrder && isBankMethod && !isPaid && !isClosed && !hasBankInstructions && <div role="alert" style={{ padding: 16, background: '#fff8e8', borderRadius: 10, marginBottom: 20 }}><p>{isExpired ? 'Đơn đã hết hạn thanh toán. Vui lòng kiểm tra lại trạng thái đơn trước khi thanh toán.' : 'Chưa có đủ thông tin thanh toán đã xác minh cho đơn này. Vui lòng tải lại trước khi chuyển khoản.'}</p><button type="button" className="btn-luxury-outline" onClick={() => setOrderFetchVersion(value => value + 1)}>Tải lại thông tin đơn</button></div>}
        {hasVerifiedOrder && !isClosed && isBankMethod && (isPaid || hasBankInstructions) && (
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
                  {orderDetail?.servicePackageId ? (orderDetail.spaBenefitType === 'TREATMENT_COURSE' ? 'Hệ thống đã xác nhận thanh toán. Mở lộ trình điều trị để theo dõi tiếp nhận và đặt từng buổi.' : 'Hệ thống đã xác nhận thanh toán. Mở ví vé Spa để xem quyền lợi từng dịch vụ và đặt lịch bằng vé.') : 'Hệ thống đã xác nhận thanh toán cho đơn hàng. Mở chi tiết đơn để theo dõi tiến độ xử lý và giao hàng.'}
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
                  {qrImageUrl && <img
                    src={qrImageUrl}
                    alt="VietQR Payment Code"
                    style={{ width: '100%', height: '100%', objectFit: 'contain', borderRadius: '6px' }}
                    onError={(e) => {
                      e.target.style.display = 'none';
                      const fallback = e.target.nextSibling;
                      if (fallback) fallback.style.display = 'flex';
                    }}
                  />}
                  <div style={{ display: qrImageUrl ? 'none' : 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
                    <QrCode size={36} color="var(--c-text-muted)" />
                    <span style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '8px' }}>Chưa tải được mã QR. Bạn có thể dùng thông tin chuyển khoản bên dưới.</span>
                  </div>
                </div>

                <div style={{ fontSize: '11px', color: 'var(--c-text-muted)', marginBottom: '16px' }}>
                  Mở ứng dụng ngân hàng bất kỳ (Vietcombank, MB, Techcombank, VPBank...) quét mã để tự động điền số tiền và nội dung.
                </div>

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

        {hasVerifiedOrder && !isClosed && isCodMethod && hasKnownTotal && (
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
          </div>
        )}

        {hasVerifiedOrder && orderDetail?.items && orderDetail.items.length > 0 && (
          <div style={{
            maxWidth: '520px',
            margin: '0 auto 28px auto',
            padding: '20px',
            borderRadius: 'var(--radius-md)',
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--c-border)',
            textAlign: 'left',
            fontSize: '13px'
          }}>
            <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '14px', color: 'var(--c-primary)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Chi tiết đơn hàng ({orderDetail.items.length} món)</span>
              <span style={{ fontSize: '11px', color: 'var(--c-text-muted)', fontWeight: 600 }}>
                {orderDetail.orderNumber}
              </span>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '14px' }}>
              {orderDetail.items.map((item, idx) => {
                const img = item.imageUrl ? resolveMediaUrl(item.imageUrl) : null;
                const price = Number(item.price || 0) - Number(item.discount || 0);
                return (
                  <div key={idx} style={{ display: 'flex', gap: '10px', alignItems: 'center', paddingBottom: '10px', borderBottom: idx < orderDetail.items.length - 1 ? '1px solid var(--c-border-subtle)' : 'none' }}>
                    <div style={{ width: '42px', height: '42px', borderRadius: '4px', backgroundColor: '#F3F4F6', overflow: 'hidden', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      {img ? (
                        <img src={img} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={e => { e.currentTarget.style.display = 'none'; }} />
                      ) : (
                        <Sparkles size={16} color="var(--c-gold)" />
                      )}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, color: 'var(--c-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {item.productName}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>
                        {item.variantName ? `${item.variantName} · ` : ''}Số lượng: {item.quantity}
                      </div>
                    </div>
                    <div style={{ fontWeight: 700, fontFamily: 'var(--font-mono)', fontSize: '13px' }}>
                      {formatCurrency(price * item.quantity)}
                    </div>
                  </div>
                );
              })}
            </div>

            <div style={{ borderTop: '1px solid var(--c-border-subtle)', paddingTop: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--c-text-muted)' }}>Địa chỉ nhận:</span>
              <span style={{ fontWeight: 600, textAlign: 'right', maxWidth: '300px', fontSize: '12px' }}>
                {orderDetail.customerName} ({orderDetail.customerPhone}) · {orderDetail.shippingAddress}
              </span>
            </div>
          </div>
        )}

        {hasVerifiedOrder && !isClosed && <div style={{
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
              <span>Theo Dõi Đơn Hàng</span>
            </div>
            <div style={{ color: 'var(--c-text-muted)' }}>
              Xem trạng thái xử lý và giao hàng được cập nhật trong chi tiết đơn.
            </div>
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '4px' }}>
              <ShieldCheck size={16} color="var(--c-safe-green)" />
              <span>Yêu Cầu Đổi Trả</span>
            </div>
            <div style={{ color: 'var(--c-text-muted)' }}>
              Điều kiện đổi trả được kiểm tra theo đơn hàng và chính sách cửa hàng.
            </div>
          </div>
        </div>}

        <div style={{ display: 'flex', justifyContent: 'center', gap: '14px' }}>
          <button onClick={() => onNavigate('products')} className="btn-luxury-outline">
            Tiếp Tục Mua Sắm
          </button>
          {hasVerifiedOrder && orderDetail?.servicePackageId && <button onClick={() => onNavigate(orderBenefitDestination(orderDetail))} className="btn-luxury-primary">{orderDetail.spaBenefitType === 'TREATMENT_COURSE' ? 'Mở lộ trình điều trị của tôi' : 'Mở ví vé Spa của tôi'} <ArrowRight size={16} /></button>}
          <button onClick={() => onNavigate(targetOrderId ? `order-lookup?id=${targetOrderId}` : 'order-lookup')} className="btn-luxury-primary">
            <span>Theo Dõi Đơn Hàng Của Tôi</span>
            <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default OrderSuccessPage;
