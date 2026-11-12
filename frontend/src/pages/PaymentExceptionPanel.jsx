import React, { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  RefreshCw,
  CreditCard,
  CheckCircle2,
  Filter,
  Save,
  Clock,
  ArrowRight,
  History,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { Button } from '../shared/ui/Button';
import { Modal } from '../shared/ui/Modal';
import { Badge } from '../shared/ui/Badge';
import { formatCurrency, formatDateTime } from '../shared/utils/formatters';

const unwrap = (response) => response?.data ?? response;

const PAYMENT_STATUS_KEY = 'beautyshop_payment_exception_status';
const PAYMENT_SYNC_EVENT = 'beautyshop:payment-exceptions-updated';

export function PaymentExceptionPanel({ onChanged }) {
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('beautyshop_payment_exception_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  const [status, setStatus] = useState(() => {
    try {
      return sessionStorage.getItem(PAYMENT_STATUS_KEY) || '';
    } catch {
      return '';
    }
  });
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState({ kind: 'MATCH', orderNumber: '', reference: '', reason: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const toggleCollapse = () => {
    const next = !collapsed;
    setCollapsed(next);
    try {
      localStorage.setItem('beautyshop_payment_exception_collapsed', String(next));
    } catch {}
  };

  const load = useCallback(async () => {
    setBusy(true);
    setError('');
    try {
      const params = new URLSearchParams({ page: String(page), size: '8' });
      if (status) params.set('status', status);
      const [exceptions, resolutions] = await Promise.all([
        apiClient.get(`/api/v1/admin/payments/exceptions?${params}`),
        apiClient.get('/api/v1/admin/payments/resolutions?size=20&sort=id,desc'),
      ]);
      const exData = unwrap(exceptions);
      setData({
        content: exData.content || [],
        totalPages: exData.totalPages || 0,
        totalElements: exData.totalElements || 0,
      });
      setHistory(unwrap(resolutions)?.content || []);
    } catch (e) {
      setError(e.message || 'Không tải được giao dịch cần đối soát');
    } finally {
      setBusy(false);
    }
  }, [page, status]);

  useEffect(() => {
    Promise.resolve().then(load);
  }, [load]);

  useEffect(() => {
    const onSync = () => {
      try {
        setStatus(sessionStorage.getItem(PAYMENT_STATUS_KEY) || '');
      } catch {}
      load();
    };
    window.addEventListener(PAYMENT_SYNC_EVENT, onSync);
    return () => window.removeEventListener(PAYMENT_SYNC_EVENT, onSync);
  }, [load]);

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await apiClient.post(`/api/v1/admin/payments/${selected.id}/resolution`, {
        ...form,
        orderNumber: form.kind === 'MATCH' ? form.orderNumber.trim() : null,
        reference: form.kind === 'REFUND' ? form.reference.trim() : null,
      });
      setSelected(null);
      await load();
      window.dispatchEvent(new Event(PAYMENT_SYNC_EVENT));
      await onChanged?.();
    } catch (e) {
      setError(e.message || 'Không ghi nhận được kết quả đối soát');
    } finally {
      setBusy(false);
    }
  };

  const totalExceptions = data.totalElements || data.content?.length || 0;

  return (
    <section className={`operations-collapsible-panel ${collapsed ? 'collapsed' : 'expanded'}`}>
      {/* Collapsible Header */}
      <div className="operations-collapsible-header" onClick={toggleCollapse} title="Bấm để mở rộng hoặc thu gọn không gian làm việc">
        <div className="operations-collapsible-title">
          <AlertTriangle size={18} color={totalExceptions > 0 ? '#D97706' : 'var(--color-accent-600, #7457e8)'} />
          <h3>Giao dịch Chuyển khoản Cần Đối soát (Ngoại lệ)</h3>
          <span style={{
            fontSize: '11px',
            background: totalExceptions > 0 ? '#FEF3C7' : 'var(--color-accent-50, #f5f1ff)',
            color: totalExceptions > 0 ? '#B45309' : 'var(--color-accent-700, #6040ce)',
            padding: '2px 8px',
            borderRadius: '12px',
            fontWeight: 700,
            border: `1px solid ${totalExceptions > 0 ? '#FDE68A' : 'var(--color-accent-100, #eae3ff)'}`,
          }}>
            {totalExceptions} giao dịch chưa khớp
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            {collapsed ? 'Mở rộng đối soát' : 'Thu gọn không gian'}
          </span>
          <button
            type="button"
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--text-muted)',
              display: 'grid',
              placeItems: 'center',
            }}
          >
            {collapsed ? <ChevronDown size={18} /> : <ChevronUp size={18} />}
          </button>
        </div>
      </div>

      {/* Expanded Body */}
      {!collapsed && (
        <div className="operations-collapsible-body">
          <p style={{ margin: 0, fontSize: '12.5px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
            Gắn khoản tiền thực nhận SePay/Ngân hàng vào đúng đơn hàng bị sai nội dung chuyển khoản, hoặc ghi nhận hoàn trả theo chứng từ đối soát.
          </p>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12.5px' }}>
              <Filter size={14} color="var(--text-muted)" />
              <span style={{ fontWeight: 600, color: 'var(--text-muted)' }}>Lọc ngoại lệ:</span>
              <select
                style={{
                  border: '1px solid var(--border-strong, #dcd4e9)',
                  background: '#ffffff',
                  padding: '5px 10px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  color: 'var(--text-main)',
                  outline: 'none',
                }}
                value={status}
                onChange={(e) => {
                  const next = e.target.value;
                  try { sessionStorage.setItem(PAYMENT_STATUS_KEY, next); } catch {}
                  setStatus(next);
                  setPage(0);
                }}
              >
                <option value="">Tất cả ngoại lệ</option>
                <option value="ORDER_NOT_FOUND">Chưa tìm thấy đơn hàng</option>
                <option value="IGNORED">Chưa xác định nội dung</option>
              </select>
            </div>

            <Button size="sm" variant="outline" icon={RefreshCw} loading={busy} onClick={load}>
              Làm mới
            </Button>
          </div>

          {error && <p role="alert" style={{ color: 'var(--color-danger-700)', margin: 0 }}>{error}</p>}

          {!busy && data.content?.length === 0 && (
            <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)', fontSize: '13px' }}>
              <CheckCircle2 size={24} style={{ margin: '0 auto 6px', color: 'var(--color-success-700)', display: 'block' }} />
              Không còn giao dịch chuyển khoản nào cần đối soát.
            </div>
          )}

          {data.content?.length > 0 && (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: '130px' }}>Mã giao dịch</th>
                    <th style={{ width: '140px' }}>Thời điểm</th>
                    <th style={{ minWidth: '220px' }}>Nội dung chuyển khoản</th>
                    <th style={{ width: '140px', textAlign: 'right' }}>Số tiền nhận</th>
                    <th style={{ width: '110px', textAlign: 'right' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.id}>
                      <td>
                        <code style={{ fontWeight: 700, color: 'var(--color-accent-700)', fontFamily: 'var(--font-mono)' }}>
                          {row.referenceCode}
                        </code>
                      </td>
                      <td style={{ fontSize: '12px' }}>{formatDateTime(row.createdAt)}</td>
                      <td style={{ fontSize: '12.5px', color: 'var(--text-main)' }}>{row.content || '—'}</td>
                      <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--color-success-700)', fontVariantNumeric: 'tabular-nums' }}>
                        {formatCurrency(row.amount)}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <Button
                          size="sm"
                          variant="primary"
                          onClick={() => {
                            setSelected(row);
                            setForm({ kind: 'MATCH', orderNumber: '', reference: '', reason: '' });
                          }}
                        >
                          Đối soát
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {data.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px' }}>
              <Button size="sm" variant="outline" disabled={busy || page === 0} onClick={() => setPage((p) => p - 1)}>
                Trang trước
              </Button>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Trang {page + 1} / {data.totalPages}
              </span>
              <Button size="sm" variant="outline" disabled={busy || page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Trang sau
              </Button>
            </div>
          )}

          {/* History Accordion */}
          {history.length > 0 && (
            <details style={{ background: 'var(--bg-surface-subtle)', borderRadius: '8px', border: '1px solid var(--border-subtle)', padding: '10px 14px', fontSize: '12.5px' }}>
              <summary style={{ cursor: 'pointer', fontWeight: 650, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <History size={14} /> Xem lịch sử 20 kết quả xử lý đối soát gần nhất
              </summary>
              <div style={{ marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {history.map((row) => (
                  <div key={row.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)', fontSize: '12px' }}>
                    <div>
                      <Badge variant={row.kind === 'MATCH' ? 'success' : 'info'}>
                        {row.kind === 'MATCH' ? 'Gắn vào đơn' : 'Đã hoàn trả'}
                      </Badge>
                      <strong style={{ marginLeft: '8px' }}>{row.orderNumber || row.reference}</strong>
                      <span style={{ color: 'var(--text-muted)', marginLeft: '8px' }}>— {row.reason}</span>
                    </div>
                    <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{formatCurrency(row.amount)}</strong>
                  </div>
                ))}
              </div>
            </details>
          )}
        </div>
      )}

      {/* Modal Đối Soát */}
      <Modal isOpen={!!selected} onClose={() => setSelected(null)} title="Ghi nhận kết quả đối soát giao dịch" maxWidth="540px">
        <div style={{ background: 'var(--bg-surface-subtle)', border: '1px solid var(--border-subtle)', borderRadius: '8px', padding: '12px 14px', marginBottom: '14px', fontSize: '13px' }}>
          <div>Mã chuyển khoản: <code style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{selected?.referenceCode}</code></div>
          <div style={{ marginTop: '4px' }}>
            Số tiền thực nhận: <strong style={{ color: 'var(--color-success-700)', fontSize: '15px' }}>{formatCurrency(selected?.amount || 0)}</strong>
          </div>
        </div>

        <form onSubmit={save} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div>
            <label className="form-label">Phương án đối soát *</label>
            <select
              className="form-select"
              value={form.kind}
              onChange={(e) => setForm((f) => ({ ...f, kind: e.target.value }))}
            >
              <option value="MATCH">Gắn vào đúng đơn hàng của khách</option>
              <option value="REFUND">Xác nhận đã hoàn trả khoản tiền này</option>
            </select>
          </div>

          {form.kind === 'MATCH' ? (
            <div>
              <label className="form-label">Mã đơn hàng đích (Order Number) *</label>
              <input
                required
                maxLength={50}
                className="form-input"
                placeholder="VD: ORD-20261104-XXXX"
                value={form.orderNumber}
                onChange={(e) => setForm((f) => ({ ...f, orderNumber: e.target.value }))}
              />
            </div>
          ) : (
            <div>
              <label className="form-label">Mã chứng từ ngân hàng đã hoàn tiền *</label>
              <input
                required
                maxLength={100}
                className="form-input"
                placeholder="VD: FT26081234567..."
                value={form.reference}
                onChange={(e) => setForm((f) => ({ ...f, reference: e.target.value }))}
              />
            </div>
          )}

          <div>
            <label className="form-label">Căn cứ & Lý do đối soát *</label>
            <textarea
              required
              maxLength={500}
              className="form-textarea"
              rows={3}
              placeholder="Ghi nhận lý do sai nội dung, khách chuyển nhầm, hoặc đối soát ngân hàng..."
              value={form.reason}
              onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
            <Button type="button" variant="outline" onClick={() => setSelected(null)}>
              Hủy
            </Button>
            <Button disabled={busy} type="submit" variant="primary" icon={Save}>
              {form.kind === 'MATCH' ? 'Gắn vào đơn hàng' : 'Ghi nhận hoàn tiền'}
            </Button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
