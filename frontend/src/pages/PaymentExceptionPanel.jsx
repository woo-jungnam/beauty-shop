import React, { useCallback, useEffect, useState } from 'react';
import { apiClient } from '../shared/api/client';
import { Button } from '../shared/ui/Button';
import { Modal } from '../shared/ui/Modal';
import { formatCurrency, formatDateTime } from '../shared/utils/formatters';

const unwrap = response => response.data || response;
export function PaymentExceptionPanel({ onChanged }) {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0 });
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState({ kind: 'MATCH', orderNumber: '', reference: '', reason: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setBusy(true); setError('');
    try {
      const params = new URLSearchParams({ page: String(page), size: '10' }); if (status) params.set('status', status);
      const [exceptions, resolutions] = await Promise.all([apiClient.get(`/api/v1/admin/payments/exceptions?${params}`), apiClient.get('/api/v1/admin/payments/resolutions?size=20&sort=id,desc')]);
      setData(unwrap(exceptions)); setHistory(unwrap(resolutions).content || []);
    } catch (e) { setError(e.message || 'Không tải được giao dịch cần đối soát'); } finally { setBusy(false); }
  }, [page, status]);
  useEffect(() => { Promise.resolve().then(load); }, [load]);
  const save = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await apiClient.post(`/api/v1/admin/payments/${selected.id}/resolution`, { ...form, orderNumber: form.kind === 'MATCH' ? form.orderNumber.trim() : null, reference: form.kind === 'REFUND' ? form.reference.trim() : null });
      setSelected(null); await load(); await onChanged?.();
    } catch (e) { setError(e.message || 'Không ghi nhận được kết quả đối soát'); } finally { setBusy(false); }
  };
  return <section className="card" style={{ marginBottom: 20 }} aria-label="Xử lý giao dịch chưa nhận diện">
    <h2 style={{ fontSize: 18 }}>Giao dịch cần đối soát</h2>
    <p>Gắn khoản tiền thực nhận vào đúng đơn hoặc ghi nhận khoản đã hoàn bằng chứng từ. Thao tác này không tự chuyển tiền ngân hàng.</p>
    <div style={{ display: 'flex', gap: 12, margin: '14px 0' }}><label>Kết quả nhận diện <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Tất cả ngoại lệ</option><option value="ORDER_NOT_FOUND">Chưa tìm thấy đơn</option><option value="IGNORED">Chưa xác định nội dung</option></select></label><Button disabled={busy} onClick={load}>Tải lại</Button></div>
    {error && <p role="alert" style={{ color: '#b91c1c' }}>{error}</p>}
    <div className="table-wrapper"><table className="data-table"><thead><tr><th>Giao dịch</th><th>Thời điểm</th><th>Nội dung</th><th>Tiền nhận</th><th>Xử lý</th></tr></thead><tbody>{data.content?.map(row => <tr key={row.id}><td>{row.referenceCode}</td><td>{formatDateTime(row.createdAt)}</td><td>{row.content}</td><td>{formatCurrency(row.amount)}</td><td><Button size="sm" onClick={() => { setSelected(row); setForm({ kind: 'MATCH', orderNumber: '', reference: '', reason: '' }); }}>Đối soát</Button></td></tr>)}</tbody></table></div>
    {!busy && !data.content?.length && <p>Không còn giao dịch chưa xử lý trong bộ lọc.</p>}
    <div style={{ display: 'flex', gap: 12, margin: '12px 0' }}><Button disabled={busy || page === 0} onClick={() => setPage(p => p - 1)}>Trước</Button><span>Trang {page + 1}/{Math.max(1, data.totalPages || 0)}</span><Button disabled={busy || page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Sau</Button></div>
    <details><summary>20 kết quả xử lý gần nhất</summary>{history.map(row => <p key={row.id}>{row.kind === 'MATCH' ? 'Đã gắn vào đơn' : 'Đã ghi nhận hoàn'} · {row.orderNumber || row.reference} · {formatCurrency(row.amount)} · {row.reason}</p>)}</details>
    <Modal isOpen={!!selected} onClose={() => setSelected(null)} title="Ghi nhận kết quả đối soát">
      <p>Khoản thực nhận: <strong>{formatCurrency(selected?.amount || 0)}</strong> · {selected?.referenceCode}</p>
      <form onSubmit={save} style={{ display: 'grid', gap: 12 }}>
        <label>Cách xử lý <select value={form.kind} onChange={e => setForm(f => ({ ...f, kind: e.target.value }))}><option value="MATCH">Gắn đúng đơn hàng</option><option value="REFUND">Xác nhận đã hoàn khoản tiền này</option></select></label>
        {form.kind === 'MATCH' ? <label>Mã đơn hàng đích <input required maxLength={50} value={form.orderNumber} onChange={e => setForm(f => ({ ...f, orderNumber: e.target.value }))} /></label> : <label>Mã chứng từ ngân hàng đã hoàn <input required maxLength={100} value={form.reference} onChange={e => setForm(f => ({ ...f, reference: e.target.value }))} /></label>}
        <label>Căn cứ và lý do <textarea required maxLength={500} value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} /></label>
        <Button disabled={busy} type="submit">{form.kind === 'MATCH' ? 'Gắn vào đơn hàng' : 'Ghi nhận đã hoàn tiền'}</Button>
      </form>
    </Modal>
  </section>;
}
