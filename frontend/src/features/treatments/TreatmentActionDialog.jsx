import React, { useEffect, useRef, useState } from 'react';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/Button';
import { apiClient } from '../../shared/api/client';
import { errorMessage, rows } from './treatmentApi';
import { useAuth } from '../../app/providers/AuthProvider';

export const TreatmentActionDialog = ({ action, onClose, onSaved }) => {
  const { user } = useAuth();
  const [values, setValues] = useState(() => Object.fromEntries((action.fields || []).map(field => [field.key, field.value ?? (field.type === 'checkbox' ? false : '')])));
  const [staff, setStaff] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loadingStaff, setLoadingStaff] = useState(action.fields?.some(field => field.type === 'staff'));
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  useEffect(() => {
    if (!action.fields?.some(field => field.type === 'staff')) return;
    const controller = new AbortController();
    apiClient.get('/api/v1/spa/services/staff', { signal: controller.signal }).then(response => {
      if (controller.signal.aborted) return;
      const available = rows(response).filter(row => row.active !== false); setStaff(available);
      const ownStaff = available.find(row => String(row.userId) === String(user?.id));
      if (ownStaff) setValues(previous => Object.fromEntries(Object.entries(previous).map(([key, value]) => [key, action.fields.some(field => field.key === key && field.type === 'staff') && !value ? String(ownStaff.id) : value])));
    }).catch(err => { if (!controller.signal.aborted) setError(errorMessage(err)); }).finally(() => { if (!controller.signal.aborted) setLoadingStaff(false); });
    return () => controller.abort();
  }, [action, user?.id]);
  const submit = async event => {
    event.preventDefault(); if (lock.current || uncertain) return;
    lock.current = true; setBusy(true); setError('');
    try { await action.submit(values); await onSaved(action.success || 'Đã cập nhật hồ sơ.'); onClose(); }
    catch (err) {
      if (!err.response || err.response.status >= 500) { setUncertain(true); await onSaved('Chưa xác minh được kết quả. Hồ sơ đã được tải lại; kiểm tra trước khi gửi lại.', 'warning'); }
      setError(errorMessage(err));
    } finally { lock.current = false; setBusy(false); }
  };
  return <Modal isOpen title={action.title} onClose={() => { if (!busy) onClose(); }} className="treatment-dialog" maxWidth="620px"><form className="treatment-form" onSubmit={submit}>
    {action.description && <p className="treatment-muted">{action.description}</p>}{error && <div role="alert" className="treatment-notice error">{error}</div>}
    <fieldset disabled={busy || uncertain || loadingStaff}>{(action.fields || []).map(field => <label className={field.type === 'checkbox' ? 'treatment-check' : ''} key={field.key}>{field.type === 'checkbox' ? <><input type="checkbox" checked={Boolean(values[field.key])} onChange={event => setValues(previous => ({ ...previous, [field.key]: event.target.checked }))} />{field.label}</> : <>{field.label}{field.type === 'staff' ? <select required value={values[field.key]} onChange={event => setValues(previous => ({ ...previous, [field.key]: event.target.value }))}><option value="">{loadingStaff ? 'Đang tải nhân sự…' : 'Chọn nhân viên phụ trách'}</option>{staff.map(row => <option key={row.id} value={row.id}>{row.fullName || row.name || `Nhân viên #${row.id}`}</option>)}</select> : field.type === 'number' || field.type === 'text' ? <input required={field.required !== false} type={field.type} min={field.min} max={field.max} maxLength={field.maxLength || 500} value={values[field.key]} onChange={event => setValues(previous => ({ ...previous, [field.key]: event.target.value }))} /> : <textarea required={field.required !== false} rows={field.rows || 3} maxLength={field.maxLength || 2000} value={values[field.key]} onChange={event => setValues(previous => ({ ...previous, [field.key]: event.target.value }))} />}</>}</label>)}</fieldset>
    {uncertain && <p className="treatment-notice warning">Đóng cửa sổ để xem kết quả mới nhất và xác minh trước khi gửi lại.</p>}
    <div className="treatment-actions"><Button variant="outline" onClick={onClose} disabled={busy}>Đóng</Button><Button type="submit" loading={busy} disabled={uncertain || loadingStaff}>{action.submitLabel || 'Xác nhận'}</Button></div>
  </form></Modal>;
};
