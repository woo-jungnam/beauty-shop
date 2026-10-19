import React, { useEffect, useRef, useState } from 'react';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { apiClient } from '../../shared/api/client';
import { dateLabel, errorMessage, rows, today, treatmentApi } from './treatmentApi';
import { bookingPayload } from './treatmentModel';

export const TreatmentBookingDialog = ({ course, session, role, reschedule = false, onClose, onSaved }) => {
  const [date, setDate] = useState(reschedule ? session.appointmentDate : today());
  const [slots, setSlots] = useState([]);
  const [slot, setSlot] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  const root = `/api/v1/spa/treatments/${course.id}/sessions/${session.id}`;
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(''); setSlots([]); setSlot('');
    apiClient.get(`${root}/available-slots`, { params: { date, ...(reschedule ? { appointmentId: session.appointmentId } : {}) }, signal: controller.signal }).then(response => { if (!controller.signal.aborted) setSlots(rows(response).filter(value => typeof value === 'string')); }).catch(err => { if (!controller.signal.aborted) setError(errorMessage(err)); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [date, root, retry, reschedule, session.appointmentId]);
  const submit = async event => {
    event.preventDefault(); if (lock.current || !slot || uncertain) return;
    lock.current = true; setBusy(true); setError('');
    try {
      const body = { appointmentDate: date, startTime: slot.length === 5 ? `${slot}:00` : slot, notes: notes.trim() };
      if (reschedule) await apiClient.put(`/api/v1/appointments/${session.appointmentId}/reschedule`, body);
      else await treatmentApi.post(`${root}/book`, bookingPayload({ course, session, role, date, slot, notes }));
      await onSaved(reschedule ? 'Đã đổi lịch hẹn và kiểm tra lại điều kiện lộ trình.' : 'Đã gửi yêu cầu đặt buổi. Lịch đang chờ xác nhận.'); onClose();
    } catch (err) {
      if (!err.response || err.response.status >= 500) { setUncertain(true); await onSaved('Chưa xác minh được kết quả đặt lịch. Đã tải lại hồ sơ; hãy kiểm tra buổi trước khi đặt lại.', 'warning'); }
      setError(errorMessage(err));
    } finally { lock.current = false; setBusy(false); }
  };
  return <Modal isOpen title={`${reschedule ? 'Đổi lịch' : 'Đặt lịch'} buổi ${session.sequence}: ${session.title}`} onClose={() => { if (!busy) onClose(); }} className="treatment-dialog" maxWidth="640px"><form className="treatment-form" onSubmit={submit}>
    <p className="treatment-muted">Giờ Việt Nam (UTC+7). Hệ thống kiểm tra toàn bộ dịch vụ, nhân sự, tài nguyên, khoảng cách buổi và quyền lợi khi đặt.</p>
    {session.earliestBookingAt && <p>Sớm nhất: {dateLabel(session.earliestBookingAt)}{session.latestBookingAt ? ` · Hạn khuyến nghị: ${dateLabel(session.latestBookingAt)}` : ''}</p>}
    {error && <div role="alert" className="treatment-notice error">{error}<Button variant="outline" disabled={busy || uncertain} onClick={() => setRetry(value => value + 1)}>Tải lại giờ trống</Button></div>}
    <fieldset disabled={busy || uncertain}><label>Ngày hẹn<input required type="date" min={today()} value={date} onChange={event => setDate(event.target.value)} /></label>
      {loading ? <p role="status">Đang kiểm tra giờ trống…</p> : <div className="treatment-slots" role="group" aria-label="Giờ trống">{slots.map(value => <button type="button" key={value} aria-pressed={slot === value} onClick={() => setSlot(value)}>{value.slice(0, 5)}</button>)}{!slots.length && !error && <p>Ngày này chưa có giờ phù hợp. Vui lòng chọn ngày khác.</p>}</div>}
      <label>{reschedule || role !== 'customer' ? 'Lý do / ghi chú đặt hộ' : 'Ghi chú'}<textarea maxLength={500} required={reschedule || role !== 'customer'} rows={3} value={notes} onChange={event => setNotes(event.target.value)} /></label>
    </fieldset>
    {uncertain && <p className="treatment-notice warning">Đóng cửa sổ để kiểm tra lịch mới nhất trong hồ sơ. Không gửi lại khi chưa xác minh.</p>}
    <div className="treatment-actions"><Button variant="outline" disabled={busy} onClick={onClose}>Đóng</Button><Button type="submit" loading={busy} disabled={!slot || loading || uncertain}>{reschedule ? 'Xác nhận đổi lịch' : 'Gửi yêu cầu đặt buổi'}</Button></div>
  </form></Modal>;
};
