import React, { useRef, useState } from 'react';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { errorMessage, idempotencyKey, money, treatmentApi } from './treatmentApi';
import { openTreatmentPayment } from './paymentNavigation';

export const TreatmentPurchaseDialog = ({ template, onClose, onPurchased }) => {
  const [accepted, setAccepted] = useState(false);
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const key = useRef(idempotencyKey());
  const payload = useRef(null);
  const lock = useRef(false);
  const submit = async event => {
    event.preventDefault(); if (!accepted || lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      payload.current ||= { templateId: template.id, versionId: template.versionId, acceptedTerms: true, idempotencyKey: key.current, notes: notes.trim() };
      setSubmitted(true);
      const order = await treatmentApi.post('/api/v1/spa/treatment-purchases', payload.current);
      if (!order?.orderId && !order?.id) throw new Error('Chưa xác minh được mã đơn hàng. Bấm thử lại để truy vấn cùng yêu cầu.');
      onPurchased(order); openTreatmentPayment(order);
    } catch (err) { setError(`${errorMessage(err)} Các lần thử lại sử dụng cùng yêu cầu để tránh tạo đơn trùng.`); }
    finally { lock.current = false; setBusy(false); }
  };
  return <Modal isOpen title={`Đăng ký ${template.name}`} onClose={() => { if (!busy) onClose(); }} className="treatment-dialog" maxWidth="760px"><form className="treatment-form" onSubmit={submit}>
    <div className="treatment-card-heading"><span className="treatment-badge">Phiên bản {template.versionNumber}</span><strong className="treatment-price">{money(template.price)}</strong></div><p>{template.description}</p><p>{template.sessions?.length || 0} buổi · Hiệu lực {template.validityDays} ngày từ khi thanh toán</p>
    <ol className="treatment-mini-sessions">{(template.sessions || []).map(session => <li key={session.sequence}><strong>{session.title}</strong><small>{(session.items || []).map(item => item.serviceName || `Dịch vụ #${item.serviceId}`).join(', ')} · {session.required ? 'Bắt buộc' : 'Tùy chọn'} · Cách buổi trước tối thiểu {session.minGapDays || 0} ngày{session.maxGapDays != null ? `, tối đa ${session.maxGapDays} ngày` : ''}</small></li>)}</ol>
    <div className="treatment-form-session"><strong>Điều khoản mua và sử dụng</strong><p style={{ whiteSpace: 'pre-wrap' }}>{template.terms}</p></div>
    <p className="treatment-notice warning">Thanh toán toàn bộ trước khi mở quyền sử dụng. Nhân viên tiếp nhận và điều chỉnh khi cần; mọi thay đổi cam kết cần bạn xác nhận.</p>
    {error && <div className="treatment-notice error" role="alert">{error}</div>}
    <fieldset disabled={busy || submitted}><label>Ghi chú đăng ký<textarea maxLength={500} rows={2} value={notes} onChange={event => setNotes(event.target.value)} /></label><label className="treatment-check"><input type="checkbox" checked={accepted} onChange={event => setAccepted(event.target.checked)} />Tôi đã đọc kế hoạch từng buổi, giá, hiệu lực và đồng ý điều khoản phiên bản {template.versionNumber}.</label></fieldset>
    <div className="treatment-actions"><Button variant="outline" disabled={busy} onClick={onClose}>Đóng</Button><Button type="submit" disabled={!accepted} loading={busy}>{submitted ? 'Kiểm tra lại đơn & thanh toán' : 'Tạo đơn & thanh toán'}</Button></div>
  </form></Modal>;
};
