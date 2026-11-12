import { BookingPolicyNotice } from '../../../features/spa/BookingPolicyNotice';
import React, { useEffect, useState } from 'react';
import { Star } from 'lucide-react';
import { apiClient } from '../../../shared/api/client';
import { Modal } from '../../../shared/ui/Modal';
import { TreatmentAppointmentDialog } from '../../../features/treatments/TreatmentAppointmentDialog';
import { listRows } from './AccountTools';
import { getVietnamDateString } from '../../../shared/utils/formatters';

export function AppointmentTools({ appointment, onSaved, autoOpen = false }) {
  const [opened, setOpened] = useState(autoOpen);
  const [reschedule, setReschedule] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  useEffect(() => { if (autoOpen) setOpened(true); }, [autoOpen]);
  return (
    <>
      <BookingPolicyNotice snapshot={appointment.policySnapshot ?? null} />
      <button onClick={() => setOpened(true)} className="btn-luxury-outline">Chi tiết, phiếu chuẩn bị & hướng dẫn</button>
      {['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt && (
        <button className="btn-luxury-outline" onClick={() => setReschedule(true)}>Đổi lịch</button>
      )}
      {appointment.status === 'COMPLETED' && (
        <button className="btn-luxury-outline" onClick={() => setReviewOpen(true)}>Đánh giá buổi Spa</button>
      )}
      {opened && <TreatmentAppointmentDialog appointmentId={appointment.id} role="customer" onClose={() => setOpened(false)} onSaved={onSaved} />}
      {reschedule && <RescheduleDialog appointment={appointment} onClose={() => setReschedule(false)} onSaved={onSaved} />}
      {reviewOpen && <AppointmentReviewDialog appointment={appointment} onClose={() => setReviewOpen(false)} onSaved={onSaved} />}
    </>
  );
}

export function AppointmentReviewDialog({ appointment, onClose, onSaved }) {
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState('');
  const [existingReview, setExistingReview] = useState(null);
  const [checking, setChecking] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  useEffect(() => {
    let active = true;
    apiClient.get(`/api/v1/appointments/${appointment.id}/history?size=50`)
      .then((res) => {
        if (!active) return;
        const found = listRows(res).find((row) => row.action === 'RATE_EXPERIENCE');
        if (found) setExistingReview(found);
      })
      .catch(() => {})
      .finally(() => { if (active) setChecking(false); });
    return () => { active = false; };
  }, [appointment.id]);

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await apiClient.post(`/api/v1/appointments/${appointment.id}/review`, {
        stars: Number(stars),
        comment: comment.trim(),
      });
      setDone(true);
      await onSaved?.();
    } catch (err) {
      setError(err.message || 'Chưa gửi được đánh giá. Vui lòng thử lại.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal isOpen title={`Đánh giá buổi hẹn #${appointment.id}`} onClose={() => { if (!busy) onClose(); }}>
      {checking ? (
        <p role="status">Đang kiểm tra trạng thái đánh giá…</p>
      ) : existingReview || done ? (
        <div className="account-stack">
          <p role="status" style={{ color: '#065F46', fontWeight: 600 }}>
            Cảm ơn bạn đã đánh giá chất lượng buổi Spa!
          </p>
          {existingReview?.details && <p>{existingReview.details}</p>}
          <button type="button" className="btn-luxury-outline" onClick={onClose}>Đóng</button>
        </div>
      ) : (
        <form className="account-form" onSubmit={submit}>
          {error && <p role="alert" style={{ color: '#991B1B' }}>{error}</p>}
          <div>
            <span style={{ display: 'block', marginBottom: 6, fontWeight: 600 }}>Mức độ hài lòng *</span>
            <div style={{ display: 'flex', gap: 8 }}>
              {[1, 2, 3, 4, 5].map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-label={`${value} sao`}
                  aria-pressed={stars === value}
                  onClick={() => setStars(value)}
                  style={{
                    borderColor: value <= stars ? '#C89C46' : '#D1D5DB',
                    backgroundColor: value <= stars ? '#FFFBEB' : '#FFFFFF',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                >
                  <Star size={16} fill={value <= stars ? '#C89C46' : 'none'} color="#C89C46" />
                  <span>{value}</span>
                </button>
              ))}
            </div>
          </div>
          <label>Nhận xét buổi trải nghiệm (tùy chọn)
            <textarea maxLength={500} rows={3} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Chia sẻ cảm nhận về chuyên viên và dịch vụ..." />
          </label>
          <div className="account-actions">
            <button type="button" className="btn-luxury-outline" disabled={busy} onClick={onClose}>Đóng</button>
            <button type="submit" className="btn-luxury-primary" disabled={busy}>{busy ? 'Đang gửi...' : 'Gửi đánh giá'}</button>
          </div>
        </form>
      )}
    </Modal>
  );
}

export function RescheduleDialog({ appointment, onClose, onSaved }) {
  const [date, setDate] = useState(appointment.appointmentDate || '');
  const [slot, setSlot] = useState('');
  const [slots, setSlots] = useState([]);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { let current = true; setSlot(''); setSlots([]); setError(''); apiClient.get(`/api/v1/appointments/${appointment.id}/available-slots`, { params: { date } }).then(result => { if (current) setSlots(listRows(result)); }).catch(e => { if (current) setError(e.message); }); return () => { current = false; }; }, [date, appointment.id]);
  return <Modal isOpen title="Đổi lịch hẹn (giờ Việt Nam)" onClose={() => { if (!busy) onClose(); }}><form className="account-form" onSubmit={async event => { event.preventDefault(); setBusy(true); setError(''); try { await apiClient.put(`/api/v1/appointments/${appointment.id}/reschedule`, { appointmentDate: date, startTime: slot, notes }); await onSaved(); onClose(); } catch (e) { setError(e.message); } finally { setBusy(false); } }}>{error && <p role="alert">{error}</p>}<label>Ngày hẹn mới<input required type="date" min={getVietnamDateString()} value={date} onChange={event => setDate(event.target.value)} /></label><label>Giờ còn trống<select required value={slot} onChange={event => setSlot(event.target.value)}><option value="">Chọn giờ</option>{slots.map(value => <option key={value} value={value}>{value.slice(0, 5)}</option>)}</select></label>{!slots.length && <p>Chưa có giờ khả dụng cho ngày đã chọn. Vui lòng chọn ngày khác.</p>}<label>Lý do đổi lịch<textarea required maxLength={500} value={notes} onChange={event => setNotes(event.target.value)} /></label><button disabled={busy || !slot} className="btn-luxury-primary">Xác nhận đổi lịch</button></form></Modal>;
}
