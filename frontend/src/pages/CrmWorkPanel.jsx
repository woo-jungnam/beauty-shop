import React, { useCallback, useEffect, useState } from 'react';
import {
  ClipboardCheck,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  Clock,
  UserCheck,
  RefreshCw,
  Edit,
  Save,
  CheckCircle2,
  Filter,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { useAuth } from '../app/providers/AuthProvider';
import { Button } from '../shared/ui/Button';
import { Modal } from '../shared/ui/Modal';
import { Badge } from '../shared/ui/Badge';
import { formatDateTime } from '../shared/utils/formatters';

const STATES = {
  OPEN: { label: 'Chưa xử lý', variant: 'warning' },
  IN_PROGRESS: { label: 'Đang xử lý', variant: 'info' },
  DONE: { label: 'Đã xử lý', variant: 'success' },
  CANCELLED: { label: 'Đã hủy', variant: 'default' },
};

const localTime = (value) =>
  value ? new Date(new Date(value).getTime() - new Date(value).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : '';

export function CrmWorkPanel() {
  const { user, isAdmin } = useAuth();
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('beautyshop_crm_work_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  const [status, setStatus] = useState('OPEN');
  const [kind, setKind] = useState('');
  const [dueOnly, setDueOnly] = useState(false);
  const [mineOnly, setMineOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [assignees, setAssignees] = useState([]);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const toggleCollapse = () => {
    const next = !collapsed;
    setCollapsed(next);
    try {
      localStorage.setItem('beautyshop_crm_work_collapsed', String(next));
    } catch {}
  };

  const load = useCallback(async () => {
    setBusy(true);
    setError('');
    const params = new URLSearchParams({ page: String(page), size: '6' });
    if (status) params.set('status', status);
    if (kind) params.set('kind', kind);
    if (dueOnly) params.set('dueBefore', new Date().toISOString());
    if (mineOnly) params.set('assigneeId', String(user.id));
    try {
      const r = await apiClient.get(`/api/v1/admin/crm/work-items?${params}`);
      const resData = r?.data ?? r;
      setData({
        content: resData.content || [],
        totalPages: resData.totalPages || 0,
        totalElements: resData.totalElements || 0,
      });
    } catch (e) {
      setError(e.message || 'Không tải được danh sách công việc');
    } finally {
      setBusy(false);
    }
  }, [page, status, kind, dueOnly, mineOnly, user.id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!isAdmin) {
      setAssignees([user]);
      return;
    }
    apiClient
      .get('/api/v1/admin/users?role=STAFF&size=100')
      .then((r) => {
        const rows = (r?.data ?? r).content || [];
        setAssignees([user, ...rows.filter((row) => row.permissions?.includes('CRM'))]);
      })
      .catch(() => setAssignees([user]));
  }, [isAdmin, user]);

  const open = (item) => {
    setSelected(item);
    setForm({
      status: item.status,
      assigneeId: item.assigneeId || '',
      dueAt: localTime(item.dueAt),
      outcome: item.outcome || '',
    });
    setError('');
  };

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await apiClient.put(`/api/v1/admin/crm/work-items/${selected.id}`, {
        ...form,
        assigneeId: form.assigneeId ? Number(form.assigneeId) : null,
        dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null,
        expectedVersion: selected.version,
      });
      setSelected(null);
      await load();
    } catch (e) {
      setError(e.message || 'Không lưu được công việc');
    } finally {
      setBusy(false);
    }
  };

  const overdueCount = data.content?.filter((i) => i.overdue).length || 0;

  return (
    <section className={`crm-collapsible-panel ${collapsed ? 'collapsed' : 'expanded'}`}>
      {/* Collapsible Header */}
      <div className="crm-collapsible-header" onClick={toggleCollapse} title="Bấm để mở rộng hoặc thu gọn không gian làm việc">
        <div className="crm-collapsible-title">
          <ClipboardCheck size={18} color="var(--color-accent-600, #7457e8)" />
          <h3>Nhiệm vụ Chăm sóc & Xử lý Khiếu nại</h3>
          <span style={{
            fontSize: '11px',
            background: 'var(--color-accent-100, #eae3ff)',
            color: 'var(--color-accent-700, #6040ce)',
            padding: '2px 8px',
            borderRadius: '12px',
            fontWeight: 700
          }}>
            {data.totalElements || data.content?.length || 0} nhiệm vụ
          </span>
          {overdueCount > 0 && (
            <span style={{
              fontSize: '11px',
              background: '#FEE2E2',
              color: '#B91C1C',
              padding: '2px 8px',
              borderRadius: '12px',
              fontWeight: 700
            }}>
              {overdueCount} quá hạn
            </span>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            {collapsed ? 'Mở rộng bảng công việc' : 'Thu gọn không gian'}
          </span>
          <button
            type="button"
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: 'var(--text-muted)',
              display: 'grid',
              placeItems: 'center'
            }}
          >
            {collapsed ? <ChevronDown size={18} /> : <ChevronUp size={18} />}
          </button>
        </div>
      </div>

      {/* Expanded Content Body */}
      {!collapsed && (
        <div className="crm-collapsible-body">
          {/* Filter Toolbar */}
          <div className="crm-filter-toolbar">
            <div className="crm-filter-group">
              <Filter size={14} color="var(--text-muted)" />
              <label>Trạng thái:</label>
              <select
                className="crm-filter-select"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Tất cả trạng thái</option>
                {Object.entries(STATES).map(([k, meta]) => (
                  <option key={k} value={k}>{meta.label}</option>
                ))}
              </select>
            </div>

            <div className="crm-filter-group">
              <label>Phân loại:</label>
              <select
                className="crm-filter-select"
                value={kind}
                onChange={(e) => {
                  setKind(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Tất cả loại</option>
                <option value="FOLLOW_UP">Chăm sóc lại</option>
                <option value="COMPLAINT">Khiếu nại</option>
              </select>
            </div>

            <label className="crm-filter-checkbox">
              <input
                type="checkbox"
                checked={dueOnly}
                onChange={(e) => {
                  setDueOnly(e.target.checked);
                  setPage(0);
                }}
              />
              <span>Đến hạn / Quá hạn</span>
            </label>

            <label className="crm-filter-checkbox">
              <input
                type="checkbox"
                checked={mineOnly}
                onChange={(e) => {
                  setMineOnly(e.target.checked);
                  setPage(0);
                }}
              />
              <span>Nhiệm vụ của tôi</span>
            </label>

            <Button
              size="sm"
              variant="outline"
              icon={RefreshCw}
              loading={busy}
              onClick={load}
              style={{ marginLeft: 'auto' }}
            >
              Làm mới
            </Button>
          </div>

          {error && <p role="alert" style={{ color: 'var(--color-danger-700)', margin: 0 }}>{error}</p>}

          {!busy && data.content?.length === 0 && (
            <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)', fontSize: '13px' }}>
              <CheckCircle2 size={24} style={{ margin: '0 auto 6px', color: 'var(--color-success-700)', display: 'block' }} />
              Không có nhiệm vụ chăm sóc nào cần xử lý với bộ lọc hiện tại.
            </div>
          )}

          {/* Cards Grid */}
          <div className="crm-work-grid">
            {data.content?.map((item) => {
              const stateMeta = STATES[item.status] || { label: item.status, variant: 'default' };

              return (
                <article
                  key={item.id}
                  className={`crm-work-card ${item.overdue ? 'overdue' : ''}`}
                >
                  <div>
                    <div className="crm-work-card-header">
                      <span className="crm-work-card-title">
                        {item.title}
                      </span>
                      <Badge variant={stateMeta.variant}>
                        {stateMeta.label}
                      </Badge>
                    </div>

                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--color-accent-700)', marginTop: '4px' }}>
                      Khách hàng: {item.customerName || `Mã #${item.customerId}`}
                    </div>

                    {item.description && (
                      <p style={{ fontSize: '12px', color: 'var(--text-main)', margin: '6px 0', lineHeight: 1.4 }}>
                        {item.description}
                      </p>
                    )}
                  </div>

                  <div>
                    <div className="crm-work-card-meta">
                      <div>Phụ trách: <strong>{item.assigneeName || 'Chưa phân công'}</strong></div>
                      <div style={{ color: item.overdue ? 'var(--color-danger-700)' : 'inherit', fontWeight: item.overdue ? 700 : 400 }}>
                        Hạn xử lý: {item.dueAt ? formatDateTime(item.dueAt) : 'Chưa đặt hạn'}
                        {item.overdue && ' (Đã quá hạn)'}
                      </div>
                    </div>

                    {item.outcome && (
                      <div className="crm-work-card-outcome" style={{ marginTop: '8px' }}>
                        Kết quả: <strong>{item.outcome}</strong>
                      </div>
                    )}

                    <div style={{ marginTop: '10px', display: 'flex', justifyContent: 'flex-end' }}>
                      <Button size="sm" variant="outline" icon={Edit} onClick={() => open(item)}>
                        Cập nhật tiến độ
                      </Button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>

          {data.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px', marginTop: '10px' }}>
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
        </div>
      )}

      {/* Modal Cập nhật tiến độ nhiệm vụ */}
      <Modal isOpen={!!selected} onClose={() => setSelected(null)} title={`Cập nhật nhiệm vụ: ${selected?.title || ''}`} maxWidth="560px">
        <form onSubmit={save} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div>
            <label className="form-label">Trạng thái xử lý *</label>
            <select
              className="form-select"
              value={form.status || 'OPEN'}
              onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
            >
              {Object.entries(STATES).map(([key, meta]) => (
                <option key={key} value={key}>{meta.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="form-label">Người phụ trách xử lý</label>
            <select
              className="form-select"
              value={form.assigneeId || ''}
              onChange={(e) => setForm((f) => ({ ...f, assigneeId: e.target.value }))}
            >
              <option value="">Chưa phân công</option>
              {assignees.map((person) => (
                <option key={person.id} value={person.id}>{person.fullName || person.username}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="form-label">Hạn xử lý (Deadline)</label>
            <input
              type="datetime-local"
              className="form-input"
              value={form.dueAt || ''}
              onChange={(e) => setForm((f) => ({ ...f, dueAt: e.target.value }))}
            />
          </div>

          <div>
            <label className="form-label">
              Kết quả giải quyết / Ghi chú xử lý {['DONE', 'CANCELLED'].includes(form.status) && '*'}
            </label>
            <textarea
              required={['DONE', 'CANCELLED'].includes(form.status)}
              maxLength={2000}
              className="form-textarea"
              rows={3}
              placeholder="Ghi nhận phương án xử lý, phản hồi của khách hàng..."
              value={form.outcome || ''}
              onChange={(e) => setForm((f) => ({ ...f, outcome: e.target.value }))}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '10px' }}>
            <Button type="button" variant="outline" onClick={() => setSelected(null)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary" icon={Save} loading={busy}>
              Lưu tiến độ
            </Button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
