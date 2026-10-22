import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../api/client';
import { ENDPOINTS } from '../api/endpoints';
import { formatCurrency, getPaymentStatusBadge } from '../utils/formatters';
import { Button } from './Button';
import { Input } from './Input';
import { Select } from './Select';
import { spaErrorMessage } from '../utils/spaMessages';
import './spa-visit-checkout.css';
import { useAuth } from '../../app/providers/AuthProvider';
import { SpaRefundDialog } from '../../features/spa/SpaRefundDialog';
import { BankPaymentInstruction } from './BankPaymentInstruction';

export const SpaVisitCheckout = ({ appointment, onUpdated }) => {
  const { isAdmin, isSpaReception } = useAuth();
  const canManage = isSpaReception || isAdmin;
  const [refundOpen, setRefundOpen] = useState(false);
  const [invoice, setInvoice] = useState(null);
  const [method, setMethod] = useState('BANK');
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [qrFailed, setQrFailed] = useState(false);
  const invoiceKey = useRef(crypto.randomUUID());
  const receipt = useRef(null);
  const updating = useRef(false);
  const updated = useRef(onUpdated);
  updated.current = onUpdated;
  const route = ENDPOINTS.SPA.APPOINTMENT_INVOICE(appointment.id);

  const accept = (data) => {
    setInvoice(data);
    setLoadFailed(false);
    setAmount(String(data.amountDue));
    setError('');
    updated.current?.();
  };

  useEffect(() => {
    const controller = new AbortController();
    apiClient.get(route, { signal: controller.signal })
      .then((res) => { if (!controller.signal.aborted) accept(res.data || res); })
      .catch((err) => {
        if (!controller.signal.aborted && err.status !== 404) {
          setLoadFailed(true);
          setError(spaErrorMessage(err, 'Chưa tải được hóa đơn. Vui lòng thử lại.'));
        }
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [route]);

  const pendingBank = invoice?.paymentMethod === 'BANK' && invoice?.paymentStatus === 'PENDING' && Number(invoice?.amountDue) > 0;
  useEffect(() => {
    if (!pendingBank || busy) return;
    const controller = new AbortController();
    let timer;
    const poll = async () => {
      let keepPolling = true;
      try {
        const res = await apiClient.get(route, { signal: controller.signal });
        if (controller.signal.aborted) return;
        const data = res.data || res;
        setInvoice(data);
        setError('');
        keepPolling = data.paymentStatus === 'PENDING' && Number(data.amountDue) > 0;
        if (!keepPolling) updated.current?.();
      } catch (err) {
        if (!controller.signal.aborted) setError(spaErrorMessage(err, 'Chưa kiểm tra được thanh toán. Hệ thống sẽ thử lại.'));
      } finally {
        if (!controller.signal.aborted && keepPolling) timer = setTimeout(poll, 5000);
      }
    };
    timer = setTimeout(poll, 5000);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [route, pendingBank, busy]);

  useEffect(() => { setQrFailed(false); }, [invoice?.paymentInstruction?.qrCodeUrl]);

  const createInvoice = async () => {
    if (updating.current) return;
    updating.current = true;
    setBusy(true);
    try {
      const res = await apiClient.post(route, { paymentMethod: method, notes: 'Thanh toán dịch vụ tại quầy Spa' },
        { headers: { 'Idempotency-Key': invoiceKey.current } });
      accept(res.data || res);
    } catch (err) { setError(spaErrorMessage(err, 'Chưa lập được hóa đơn. Vui lòng kiểm tra và thử lại.')); }
    finally { updating.current = false; setBusy(false); }
  };

  const collectCash = async (event) => {
    event.preventDefault();
    if (updating.current) return;
    const value = Number(amount);
    if (!Number.isSafeInteger(value) || value <= 0 || value > Number(invoice.amountDue)) {
      setError('Số tiền thu phải là số nguyên dương, không vượt quá số tiền còn thiếu.');
      return;
    }
    if (receipt.current && receipt.current.amount !== value) {
      setError('Phiếu thu trước chưa xác nhận. Giữ nguyên số tiền để thử lại hoặc mở lại hóa đơn kiểm tra.');
      return;
    }
    receipt.current ||= { amount: value, key: crypto.randomUUID() };
    updating.current = true;
    setBusy(true);
    try {
      const res = await apiClient.post(ENDPOINTS.SPA.APPOINTMENT_CASH_RECEIPT(appointment.id),
        { amount: receipt.current.amount }, { headers: { 'Idempotency-Key': receipt.current.key } });
      receipt.current = null;
      accept(res.data || res);
      setMessage('Đã ghi nhận phiếu thu tiền mặt.');
    } catch (err) { setError(spaErrorMessage(err, 'Chưa xác nhận được phiếu thu. Giữ nguyên số tiền và kiểm tra hóa đơn trước khi thử lại.')); }
    finally { updating.current = false; setBusy(false); }
  };

  const refresh = async () => {
    if (updating.current) return;
    updating.current = true;
    setBusy(true);
    try { const res = await apiClient.get(route); accept(res.data || res); }
    catch (err) {
      if (err.status === 404 && !invoice) { setLoadFailed(false); setError(''); }
      else setError(spaErrorMessage(err, 'Chưa kiểm tra được hóa đơn. Vui lòng thử lại.'));
    }
    finally { updating.current = false; setBusy(false); }
  };

  const copy = async (text) => {
    try { await navigator.clipboard.writeText(text); setMessage('Đã sao chép.'); }
    catch { setError('Không thể sao chép. Vui lòng chọn và sao chép thông tin thủ công.'); }
  };

  const instruction = invoice?.paymentInstruction;
  const paid = invoice?.paymentStatus === 'PAID';
  const canCollect = canManage && invoice?.paymentStatus === 'PENDING' && Number(invoice?.amountDue) > 0 && Number(invoice?.refundedAmount) === 0;
  return (
    <div className="spa-visit-checkout">
      {refundOpen && <SpaRefundDialog appointmentId={appointment.id} onClose={() => setRefundOpen(false)} onUpdated={refresh} />}
      {isAdmin && invoice && Number(invoice.paidAmount) > Number(invoice.refundedAmount) && <Button variant="outline" onClick={() => setRefundOpen(true)}>Hoàn tiền theo dịch vụ</Button>}
      <div className="checkout-customer"><span>ĐỐI SOÁT BUỔI CHĂM SÓC</span><strong>{appointment.customerName || 'Khách hàng'}</strong><small>{appointment.customerPhone} · SPA-{String(appointment.id).padStart(5, '0')}</small></div>
      {(appointment.items || []).some(item => item.ticketId || item.isTicketUsed) && <div className="checkout-tickets"><h4>Vé liệu trình sử dụng trong buổi</h4>{(appointment.items || []).filter(item => item.ticketId || item.isTicketUsed).map(item => <div key={item.id}><span><strong>{item.serviceName}</strong><small>Vé #{item.ticketId}</small></span><span>{item.ticketUsageState === 'REDEEMED' ? 'Đã trừ lượt' : item.ticketUsageState === 'RELEASED' ? 'Đã hoàn lượt giữ' : item.ticketUsageState === 'FORFEITED' ? 'Đã ghi nhận mất lượt' : item.ticketUsageState === 'RESERVED' ? 'Đang giữ lượt' : 'Xem đối soát từ hệ thống'}</span></div>)}<p>Lượt vé được đối soát theo kết quả dịch vụ; chỉ dịch vụ ngoài vé đã thực hiện được tính vào hóa đơn.</p></div>}
      {error && <p role="alert" className="checkout-feedback error">{error}</p>}
      {message && <p role="status" className="checkout-feedback success">{message}</p>}
      {loading ? <p>Đang tra cứu hóa đơn...</p> : !invoice ? (
        loadFailed ? <Button type="button" onClick={refresh} loading={busy}>Thử tải lại hóa đơn</Button> : !canManage ? (
          <p className="checkout-explainer">Hóa đơn buổi chăm sóc chưa được nhân viên lễ tân lập.</p>
        ) : appointment?.status !== 'COMPLETED' ? (
          <p className="checkout-explainer">Chỉ có thể lập hóa đơn sau khi buổi hẹn hoàn thành.</p>
        ) : <>
          <p className="checkout-explainer">Hóa đơn tính các dịch vụ đã thực hiện ngoài vé liệu trình, gồm dịch vụ phát sinh được ghi nhận trong ca.</p>
          <Select label="Phương thức thanh toán" value={method} disabled={busy} onChange={(e) => setMethod(e.target.value)} options={[
            { value: 'BANK', label: 'Chuyển khoản VietQR / SePay' },
            { value: 'CASH', label: 'Tiền mặt tại quầy' },
          ]} />
          <Button type="button" onClick={createInvoice} loading={busy}>Lập hóa đơn{method === 'BANK' ? ' & tạo QR' : ''}</Button>
        </>
      ) : <>
        <div className="checkout-invoice-heading"><span>Hóa đơn <strong>{invoice.orderNumber}</strong></span><span role="status" className={`checkout-payment-state ${paid ? 'paid' : ''}`}>{paid ? 'Thanh toán hoàn tất' : `Trạng thái: ${getPaymentStatusBadge(invoice.paymentStatus).text}`}</span></div>
        <ul className="checkout-service-lines">{(invoice.items || []).map(item => <li key={item.appointmentItemId}><span>{item.serviceName}</span><strong>{formatCurrency(item.unitPrice)}</strong></li>)}</ul>
        <div className="checkout-totals"><div><span>Tổng tiền</span><strong>{formatCurrency(invoice.totalAmount)}</strong></div><div><span>Đã thu</span><strong>{formatCurrency(invoice.paidAmount)}</strong></div><div className="amount-due"><span>Còn cần thu</span><strong>{formatCurrency(invoice.amountDue)}</strong></div></div>
        {pendingBank && instruction && <BankPaymentInstruction instruction={instruction} label="Mã VietQR thanh toán hóa đơn Spa" />}
        <Button type="button" variant="outline" onClick={refresh} loading={busy}>Kiểm tra thanh toán</Button>
        {canCollect && <form onSubmit={collectCash} className="checkout-cash-form">
          <h4>Thu tiền mặt tại quầy</h4><p>Nhập đúng số tiền đã nhận từ khách. Có thể thu từng phần.</p>
          <Input label="Số tiền thực thu tiền mặt (VND)" type="number" required min={1} max={invoice.amountDue} step={1} value={amount} onChange={(e) => setAmount(e.target.value)} />
          <Button type="submit" loading={busy}>Xác nhận đã nhận tiền mặt</Button>
        </form>}
        {paid && <Button type="button" variant="outline" onClick={() => window.print()}>In hóa đơn</Button>}
      </>}
    </div>
  );
};
