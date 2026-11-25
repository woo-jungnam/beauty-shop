import React, { useRef, useState } from 'react';
import { useAuth } from '../../app/providers/AuthProvider';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import { errorMessage, idempotencyKey, money, treatmentApi } from './treatmentApi';
import { openTreatmentPayment } from './paymentNavigation';

export const TreatmentPurchaseDialog = ({ template, onClose, onPurchased }) => {
  const { user } = useAuth();
  const [accepted, setAccepted] = useState(false);
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const key = useRef(idempotencyKey());
  const payload = useRef(null);
  const lock = useRef(false);
  const tierPercent = {
    SILVER: 5,
    GOLD: 10,
    PLATINUM: 15,
  }[user?.membershipTier] || 0;
  const membershipDiscount = tierPercent > 0 && template.price > 0
    ? Math.round(template.price * (tierPercent / 100))
    : 0;
  const finalPrice = Math.max(0, template.price - membershipDiscount);
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
  const serviceDetails = template.serviceDetails || {};
  const fallbackCover = '/images/spa/facial-care.jpg';
  const coverImage = template.thumbnailUrl ? resolveMediaUrl(template.thumbnailUrl) : fallbackCover;
  const retailTotal = Number(template.originalPrice || 0);
  return <Modal isOpen title={`Đăng ký ${template.name}`} onClose={() => { if (!busy) onClose(); }} className="treatment-dialog" maxWidth="780px"><form className="treatment-form" onSubmit={submit}>
    <div className="treatment-course-overview">
      <img className="treatment-course-cover" src={coverImage} alt={template.name} loading="lazy" onError={e => { if (e.currentTarget.getAttribute('src') !== fallbackCover) e.currentTarget.src = fallbackCover; }} />
      <div className="treatment-course-overview-copy">
        <div className="treatment-card-heading">
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <span className="treatment-badge">Phiên bản {template.versionNumber}</span>
            {template.categoryName && <span className="treatment-badge">{template.categoryName}</span>}
          </div>
          <div style={{ textAlign: 'right' }}>
            {retailTotal > finalPrice && <del style={{ display: 'block', fontSize: '12px', color: '#9CA3AF' }}>Giá lẻ: {money(retailTotal)}</del>}
            {membershipDiscount > 0 ? (
              <>
                <del style={{ display: 'block', fontSize: '13px', color: '#9CA3AF' }}>{money(template.price)}</del>
                <div style={{ fontSize: '11px', color: 'var(--c-safe-green, #059669)', fontWeight: 600 }}>
                  Ưu đãi {user?.membershipTier} (−{tierPercent}%)
                </div>
                <strong className="treatment-price">{money(finalPrice)}</strong>
              </>
            ) : (
              <strong className="treatment-price">{money(template.price)}</strong>
            )}
          </div>
        </div>
        {template.shortDescription && <p><strong>{template.shortDescription}</strong></p>}
        <p>{template.description}</p>
        <p className="treatment-muted">
          {template.sessions?.length || 0} buổi
          {template.totalDurationMinutes > 0 ? ` · ${template.totalDurationMinutes} phút tổng thời lượng` : ''}
          {` · ${template.validityDays == null ? 'Không giới hạn thời hạn' : `Hiệu lực ${template.validityDays} ngày từ khi thanh toán`}`}
        </p>
      </div>
    </div>
    <ol className="treatment-mini-sessions">{(template.sessions || []).map(session => {
      const gapText = (session.minGapDays > 0 || session.maxGapDays != null)
        ? ` · Giãn cách tham khảo: ${session.minGapDays || 0}${session.maxGapDays != null ? `–${session.maxGapDays}` : '+'} ngày (không bắt buộc)`
        : '';
      return <li key={session.sequence}>
        <strong>{session.title}</strong>
        <small>
          {(session.items || []).map(item => {
            const detail = serviceDetails[item.serviceId];
            return `${item.serviceName || detail?.name || `Dịch vụ #${item.serviceId}`}${detail?.durationMinutes ? ` (${detail.durationMinutes}p)` : ''}`;
          }).join(', ')} · {session.required ? 'Bắt buộc' : 'Tùy chọn'}{gapText}
        </small>
        <div className="treatment-purchase-thumbs">
          {(session.items || []).map(item => {
            const detail = serviceDetails[item.serviceId];
            if (!detail) return null;
            return <div key={item.serviceId} className="treatment-item-row" style={{ marginTop: 6 }}>
              {detail.thumbnailUrl && <img className="treatment-item-thumb" src={resolveMediaUrl(detail.thumbnailUrl)} alt={detail.name} loading="lazy" />}
              <div>
                <small>{detail.shortDescription || detail.description || ''}</small>
                {detail.basePrice != null && <small>Giá lẻ tham chiếu: {money(detail.basePrice)}</small>}
              </div>
            </div>;
          })}
        </div>
      </li>;
    })}</ol>
    <div className="treatment-form-session"><strong>Điều khoản mua và sử dụng</strong><p style={{ whiteSpace: 'pre-wrap' }}>{template.terms}</p></div>
    <p className="treatment-notice">Thanh toán toàn bộ để kích hoạt hồ sơ và đặt từng buổi linh hoạt theo danh sách dịch vụ trong gói. Mọi điều chỉnh phác đồ (nếu phát sinh) đều cần bạn xác nhận.</p>
    {error && <div className="treatment-notice error" role="alert">{error}</div>}
    <fieldset disabled={busy || submitted}><label>Ghi chú đăng ký<textarea maxLength={500} rows={2} value={notes} onChange={event => setNotes(event.target.value)} /></label><label className="treatment-check"><input type="checkbox" checked={accepted} onChange={event => setAccepted(event.target.checked)} />Tôi đã đọc kế hoạch từng buổi, giá, hiệu lực và đồng ý điều khoản phiên bản {template.versionNumber}.</label></fieldset>
    <div className="treatment-actions"><Button variant="outline" disabled={busy} onClick={onClose}>Đóng</Button><Button type="submit" disabled={!accepted} loading={busy}>{submitted ? 'Kiểm tra lại đơn & thanh toán' : 'Tạo đơn & thanh toán'}</Button></div>
  </form></Modal>;
};
