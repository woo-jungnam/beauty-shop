import React, { useCallback, useEffect, useState } from 'react';
import { apiClient } from '../shared/api/client';
import { useAuth } from '../app/providers/AuthProvider';
import { Button } from '../shared/ui/Button';
import { Modal } from '../shared/ui/Modal';
import { formatDateTime } from '../shared/utils/formatters';

const STATES = { OPEN: 'Chưa xử lý', IN_PROGRESS: 'Đang xử lý', DONE: 'Đã xử lý', CANCELLED: 'Đã hủy' };
const localTime = value => value ? new Date(new Date(value).getTime() - new Date(value).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : '';
export function CrmWorkPanel() {
  const { user, isAdmin } = useAuth();
  const [status, setStatus] = useState('OPEN');
  const [kind, setKind] = useState('');
  const [dueOnly, setDueOnly] = useState(false);
  const [mineOnly, setMineOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0 });
  const [assignees, setAssignees] = useState([]);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setBusy(true); setError('');
    const params = new URLSearchParams({ page: String(page), size: '10' });
    if (status) params.set('status', status);
    if (kind) params.set('kind', kind);
    if (dueOnly) params.set('dueBefore', new Date().toISOString());
    if (mineOnly) params.set('assigneeId', String(user.id));
    try { const r = await apiClient.get(`/api/v1/admin/crm/work-items?${params}`); setData(r.data || r); }
    catch (e) { setError(e.message || 'Không tải được công việc chăm sóc'); }
    finally { setBusy(false); }
  }, [page, status, kind, dueOnly, mineOnly, user.id]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    if (!isAdmin) { setAssignees([user]); return; }
    apiClient.get('/api/v1/admin/users?role=STAFF&size=100').then(r => {
      const rows = (r.data || r).content || [];
      setAssignees([user, ...rows.filter(row => row.permissions?.includes('CRM'))]);
    }).catch(() => setAssignees([user]));
  }, [isAdmin, user]);
  const open = item => { setSelected(item); setForm({ status: item.status, assigneeId: item.assigneeId || '', dueAt: localTime(item.dueAt), outcome: item.outcome || '' }); setError(''); };
  const save = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await apiClient.put(`/api/v1/admin/crm/work-items/${selected.id}`, { ...form, assigneeId: form.assigneeId ? Number(form.assigneeId) : null, dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null, expectedVersion: selected.version });
      setSelected(null); await load();
    } catch (e) { setError(e.message || 'Không lưu được công việc'); } finally { setBusy(false); }
  };
  return <section className="card" style={{ marginBottom: 20 }} aria-label="Công việc chăm sóc và khiếu nại">
    <h2 style={{ fontSize: 18 }}>Công việc chăm sóc và khiếu nại</h2>
    <p>Ghi chú có lịch chăm sóc và khiếu nại được tạo thành công việc để phân công và theo dõi kết quả.</p>
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', margin: '14px 0' }}>
      <label>Trạng thái <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Tất cả</option>{Object.entries(STATES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label>Loại <select value={kind} onChange={e => { setKind(e.target.value); setPage(0); }}><option value="">Tất cả</option><option value="FOLLOW_UP">Chăm sóc lại</option><option value="COMPLAINT">Khiếu nại</option></select></label>
      <label><input type="checkbox" checked={dueOnly} onChange={e => { setDueOnly(e.target.checked); setPage(0); }} /> Đến hạn / quá hạn</label>
      <label><input type="checkbox" checked={mineOnly} onChange={e => { setMineOnly(e.target.checked); setPage(0); }} /> Của tôi</label>
      <Button disabled={busy} onClick={load}>Tải lại</Button>
    </div>
    {error && <p role="alert" style={{ color: '#b91c1c' }}>{error}</p>}
    {!busy && !data.content?.length && <p>Không có công việc phù hợp.</p>}
    <div style={{ display: 'grid', gap: 10 }}>{data.content?.map(item => <article key={item.id} style={{ border: '1px solid var(--border-subtle)', padding: 12 }}>
      <strong>{item.title} · {item.customerName || `Khách ${item.customerId}`}</strong>
      <p>{item.description}</p>
      <p style={{ color: item.overdue ? '#b91c1c' : 'inherit' }}>{STATES[item.status]} · {item.assigneeName || 'Chưa phân công'} · {item.dueAt ? formatDateTime(item.dueAt) : 'Chưa đặt hạn'}</p>
      {item.outcome && <p>Kết quả: {item.outcome}</p>}
      <Button size="sm" onClick={() => open(item)}>Phân công / cập nhật</Button>
    </article>)}</div>
    <div style={{ display: 'flex', gap: 12, marginTop: 12 }}><Button disabled={busy || page === 0} onClick={() => setPage(p => p - 1)}>Trước</Button><span>Trang {page + 1}/{Math.max(1, data.totalPages || 0)}</span><Button disabled={busy || page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Sau</Button></div>
    <Modal isOpen={!!selected} onClose={() => setSelected(null)} title="Cập nhật công việc">
      <form onSubmit={save} style={{ display: 'grid', gap: 12 }}>
        <label>Trạng thái <select value={form.status || 'OPEN'} onChange={e => setForm(f => ({ ...f, status: e.target.value }))}>{Object.entries(STATES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label>Người phụ trách <select value={form.assigneeId || ''} onChange={e => setForm(f => ({ ...f, assigneeId: e.target.value }))}><option value="">Chưa phân công</option>{assignees.map(person => <option key={person.id} value={person.id}>{person.fullName || person.username}</option>)}</select></label>
        <label>Hạn xử lý <input type="datetime-local" value={form.dueAt || ''} onChange={e => setForm(f => ({ ...f, dueAt: e.target.value }))} /></label>
        <label>Kết quả / lý do <textarea required={['DONE', 'CANCELLED'].includes(form.status)} maxLength={2000} value={form.outcome || ''} onChange={e => setForm(f => ({ ...f, outcome: e.target.value }))} /></label>
        <Button type="submit" disabled={busy}>Lưu công việc</Button>
      </form>
    </Modal>
  </section>;
}
