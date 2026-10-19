import React, { useEffect, useState } from 'react';
import { apiClient } from '../../../shared/api/client';
import { Modal } from '../../../shared/ui/Modal';
import { TreatmentAppointmentDialog } from '../../../features/treatments/TreatmentAppointmentDialog';
import { listRows } from './AccountTools';

export function AppointmentTools({ appointment, onSaved, autoOpen = false }) {
  const [opened, setOpened] = useState(autoOpen);
  const [reschedule, setReschedule] = useState(false);
  useEffect(() => { if (autoOpen) setOpened(true); }, [autoOpen]);
  return <><button onClick={() => setOpened(true)} className="btn-luxury-outline">Chi tiết, phiếu chuẩn bị & hướng dẫn</button>{['PENDING', 'CONFIRMED'].includes(appointment.status) && !appointment.checkedInAt && <button className="btn-luxury-outline" onClick={() => setReschedule(true)}>Đổi lịch</button>}{opened && <TreatmentAppointmentDialog appointmentId={appointment.id} role="customer" onClose={() => setOpened(false)} onSaved={onSaved} />}{reschedule && <RescheduleDialog appointment={appointment} onClose={() => setReschedule(false)} onSaved={onSaved} />}</>;
}

export function RescheduleDialog({ appointment, onClose, onSaved }) {
  const [date, setDate] = useState(appointment.appointmentDate || '');
  const [slot, setSlot] = useState('');
  const [slots, setSlots] = useState([]);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { let current = true; setSlot(''); setSlots([]); setError(''); apiClient.get(`/api/v1/appointments/${appointment.id}/available-slots`, { params: { date } }).then(result => { if (current) setSlots(listRows(result)); }).catch(e => { if (current) setError(e.message); }); return () => { current = false; }; }, [date, appointment.id]);
  return <Modal isOpen title="Đổi lịch hẹn (giờ Việt Nam)" onClose={() => { if (!busy) onClose(); }}><form className="account-form" onSubmit={async event => { event.preventDefault(); setBusy(true); setError(''); try { await apiClient.put(`/api/v1/appointments/${appointment.id}/reschedule`, { appointmentDate: date, startTime: slot, notes }); await onSaved(); onClose(); } catch (e) { setError(e.message); } finally { setBusy(false); } }}>{error && <p role="alert">{error}</p>}<label>Ngày hẹn mới<input required type="date" value={date} onChange={event => setDate(event.target.value)} /></label><label>Giờ còn trống<select required value={slot} onChange={event => setSlot(event.target.value)}><option value="">Chọn giờ</option>{slots.map(value => <option key={value} value={value}>{value.slice(0, 5)}</option>)}</select></label>{!slots.length && <p>Chưa có giờ khả dụng cho ngày đã chọn. Vui lòng chọn ngày khác.</p>}<label>Lý do đổi lịch<textarea required maxLength={500} value={notes} onChange={event => setNotes(event.target.value)} /></label><button disabled={busy || !slot} className="btn-luxury-primary">Xác nhận đổi lịch</button></form></Modal>;
}
