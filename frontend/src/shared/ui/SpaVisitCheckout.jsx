import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../api/client';
import { ENDPOINTS } from '../api/endpoints';
import { formatCurrency, getPaymentStatusBadge } from '../utils/formatters';
import { Button } from './Button';
import { Input } from './Input';
import { Select } from './Select';

export const SpaVisitCheckout = ({ appointment, onUpdated }) => {
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
          setError(err.message);
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
    // ponytail: poll while this checkout is mounted; use server events if traffic warrants it.
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
        if (!controller.signal.aborted) setError(`Chưa kiểm tra được thanh toán: ${err.message}`);
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
    } catch (err) { setError(err.message); }
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
    // Keep the same receipt payload/key on an uncertain network result to prevent double collection.
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
    } catch (err) { setError(err.message); }
    finally { updating.current = false; setBusy(false); }
  };

  const refresh = async () => {
    if (updating.current) return;
    updating.current = true;
    setBusy(true);
    try { const res = await apiClient.get(route); accept(res.data || res); }
    catch (err) {
      if (err.status === 404 && !invoice) { setLoadFailed(false); setError(''); }
      else setError(err.message);
    }
    finally { updating.current = false; setBusy(false); }
  };

  const copy = async (text) => {
    try { await navigator.clipboard.writeText(text); setMessage('Đã sao chép.'); }
    catch { setError('Không thể sao chép. Vui lòng chọn và sao chép thông tin thủ công.'); }
  };

  const instruction = invoice?.paymentInstruction;
  const paid = invoice?.paymentStatus === 'PAID';
  const canCollect = invoice?.paymentStatus === 'PENDING' && Number(invoice?.amountDue) > 0 && Number(invoice?.refundedAmount) === 0;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, overflowWrap: 'anywhere' }}>
      <div>Khách hàng: <strong>{appointment.customerName || 'Khách vãng lai'}</strong></div>
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      {loading ? <p>Đang tra cứu hóa đơn...</p> : !invoice ? (
        loadFailed ? <Button type="button" onClick={refresh} loading={busy}>Thử tải lại hóa đơn</Button> : <>
          <p>Hóa đơn chỉ tính dịch vụ đã thực hiện ngoài vé liệu trình.</p>
          <Select label="Phương thức thanh toán" value={method} disabled={busy} onChange={(e) => setMethod(e.target.value)} options={[
            { value: 'BANK', label: 'Chuyển khoản VietQR / SePay' },
            { value: 'CASH', label: 'Tiền mặt tại quầy' },
          ]} />
          <Button type="button" onClick={createInvoice} loading={busy}>Lập hóa đơn{method === 'BANK' ? ' & tạo QR' : ''}</Button>
        </>
      ) : <>
        <div>Mã hóa đơn: <strong>{invoice.orderNumber}</strong></div>
        <div>Tổng tiền: <strong>{formatCurrency(invoice.totalAmount)}</strong></div>
        <div>Đã thu: <strong>{formatCurrency(invoice.paidAmount)}</strong></div>
        <div>Còn thiếu: <strong>{formatCurrency(invoice.amountDue)}</strong></div>
        <ul>{invoice.items.map((item) => <li key={item.appointmentItemId}>{item.serviceName}: {formatCurrency(item.unitPrice)}</li>)}</ul>
        <p role="status">{paid ? 'Thanh toán hoàn tất' : `Trạng thái: ${getPaymentStatusBadge(invoice.paymentStatus).text}`}</p>
        {pendingBank && instruction && <div style={{ textAlign: 'center' }}>
          {instruction.qrCodeUrl && !qrFailed && <img src={instruction.qrCodeUrl} alt="Mã VietQR thanh toán hóa đơn Spa" onError={() => setQrFailed(true)} style={{ width: 280, maxWidth: '100%', height: 'auto' }} />}
          {qrFailed && <p role="alert">Không tải được QR. Chuyển khoản theo thông tin dưới đây.</p>}
          <p>Ngân hàng: <strong>{instruction.bankName}</strong></p>
          <p>Chủ tài khoản: <strong>{instruction.bankAccountName}</strong></p>
          <p>Số tài khoản: <strong>{instruction.bankAccountNumber}</strong> <Button type="button" size="sm" variant="outline" onClick={() => copy(instruction.bankAccountNumber)}>Sao chép số tài khoản</Button></p>
          <p>Nội dung: <strong>{instruction.transferSyntax}</strong> <Button type="button" size="sm" variant="outline" onClick={() => copy(instruction.transferSyntax)}>Sao chép nội dung</Button></p>
          <p>Chuyển đúng nội dung. Hệ thống tự kiểm tra mỗi 5 giây; chỉ hoàn tất khi nhận xác nhận thanh toán.</p>
        </div>}
        <Button type="button" variant="outline" onClick={refresh} loading={busy}>Kiểm tra thanh toán</Button>
        {canCollect && <form onSubmit={collectCash} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Input label="Số tiền thực thu tiền mặt (VND)" type="number" required min={1} max={invoice.amountDue} step={1} value={amount} onChange={(e) => setAmount(e.target.value)} />
          <Button type="submit" loading={busy}>Xác nhận đã nhận tiền mặt</Button>
        </form>}
        {paid && <Button type="button" variant="outline" onClick={() => window.print()}>In hóa đơn</Button>}
      </>}
    </div>
  );
};
