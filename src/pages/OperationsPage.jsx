import React, { useEffect, useState } from 'react';
import { Download, Plus, RefreshCw, Save, Search, Trash2, Bot, Sparkles, Database, Activity, CheckCircle2, AlertTriangle, Send } from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Modal } from '../shared/ui/Modal';
import { DataTable } from '../shared/ui/DataTable';
import { Badge } from '../shared/ui/Badge';
import { formatCurrency, formatDateTime } from '../shared/utils/formatters';

const exportCsv = (name, rows) => {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const csv = [headers.map(escape).join(','), ...rows.map((row) => headers.map((key) => escape(row[key])).join(','))].join('\n');
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' }));
  link.download = `${name}-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
};

export const OperationsPage = () => {
  const [tab, setTab] = useState('payments');
  const [rows, setRows] = useState([]);
  const [configs, setConfigs] = useState([]);
  const [summary, setSummary] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const [newDesc, setNewDesc] = useState('');

  // Chatbot Admin State
  const [chatbotStatus, setChatbotStatus] = useState(null);
  const [checkingChatbot, setCheckingChatbot] = useState(false);
  const [syncLimit, setSyncLimit] = useState('');
  const [syncingDatabase, setSyncingDatabase] = useState(false);
  const [syncResult, setSyncResult] = useState(null);
  const [nluMessage, setNluMessage] = useState('Da tôi dầu mụn, có kem chống nắng nào kiềm dầu tốt không?');
  const [testingNlu, setTestingNlu] = useState(false);
  const [nluResult, setNluResult] = useState(null);

  const checkChatbotHealth = async () => {
    setCheckingChatbot(true);
    try {
      const res = await apiClient.get(ENDPOINTS.CHATBOT.HEALTH);
      const data = res?.data || res;
      setChatbotStatus(data);
    } catch (err) {
      setChatbotStatus({ status: 'DOWN', error: err.message });
    } finally {
      setCheckingChatbot(false);
    }
  };

  const handleSyncDatabase = async () => {
    if (!window.confirm('Kích hoạt đồng bộ dữ liệu sản phẩm & dịch vụ từ MySQL vào kho vector Qdrant RAG?')) return;
    setSyncingDatabase(true);
    setSyncResult(null);
    try {
      const url = syncLimit.trim()
        ? `${ENDPOINTS.CHATBOT.SYNC_DATABASE}?limit=${encodeURIComponent(syncLimit.trim())}`
        : ENDPOINTS.CHATBOT.SYNC_DATABASE;
      const res = await apiClient.post(url);
      const data = res?.data || res;
      setSyncResult(data);
      alert('Đồng bộ cơ sở dữ liệu vào RAG thành công!');
    } catch (err) {
      alert(err.message || 'Lỗi khi đồng bộ dữ liệu vào RAG');
    } finally {
      setSyncingDatabase(false);
    }
  };

  const handleTestNlu = async (e) => {
    e.preventDefault();
    if (!nluMessage.trim()) return;
    setTestingNlu(true);
    setNluResult(null);
    try {
      const res = await apiClient.post(ENDPOINTS.CHATBOT.TEST_UNDERSTAND, {
        message: nluMessage.trim(),
      });
      const data = res?.data || res;
      setNluResult(data);
    } catch (err) {
      alert(err.message || 'Lỗi kiểm tra hiểu truy vấn NLU');
    } finally {
      setTestingNlu(false);
    }
  };

  const load = async () => {
    setLoading(true);
    try {
      if (tab === 'payments') {
        const [list, stats] = await Promise.all([
          apiClient.get(`${ENDPOINTS.SYSTEM.PAYMENTS}?size=50&keyword=${encodeURIComponent(keyword)}`),
          apiClient.get(ENDPOINTS.SYSTEM.PAYMENT_SUMMARY),
        ]);
        setRows((list.data || list).content || []);
        setSummary(stats.data || stats);
      } else if (tab === 'audit') {
        const res = await apiClient.get(`${ENDPOINTS.SYSTEM.AUDIT_LOGS}?size=50&sort=createdAt,desc&keyword=${encodeURIComponent(keyword)}`);
        setRows((res.data || res).content || []);
      } else if (tab === 'chatbot') {
        checkChatbotHealth();
      } else {
        const res = await apiClient.get(ENDPOINTS.SYSTEM.CONFIGS);
        setConfigs(res.data || res || []);
      }
    } catch (err) {
      alert(err.message || 'Không thể tải dữ liệu vận hành');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [tab]);

  const saveConfig = async (config) => {
    await apiClient.put(ENDPOINTS.SYSTEM.CONFIG(config.configKey), { value: config.configValue });
    load();
  };

  const handleCreateConfig = async (e) => {
    e.preventDefault();
    try {
      await apiClient.post(ENDPOINTS.SYSTEM.CREATE_CONFIG, {
        key: newKey.trim(),
        value: newValue.trim(),
        description: newDesc.trim(),
      });
      setConfigModalOpen(false);
      setNewKey('');
      setNewValue('');
      setNewDesc('');
      load();
    } catch (err) {
      alert(err.message || 'Lỗi khi tạo cấu hình');
    }
  };

  const handleDeleteConfig = async (key) => {
    if (!window.confirm(`Xóa cấu hình "${key}"?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.SYSTEM.DELETE_CONFIG(key));
      load();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa cấu hình');
    }
  };

  const paymentColumns = [
    { header: 'Đơn hàng', accessor: 'orderNumber' },
    { header: 'Mã đối soát', accessor: 'referenceCode' },
    { header: 'Cổng', accessor: 'gateway' },
    { header: 'Số tiền', accessor: (row) => <strong>{formatCurrency(row.amount)}</strong>, align: 'right' },
    { header: 'Trạng thái', accessor: (row) => <Badge variant={row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'}>{row.status}</Badge> },
    { header: 'Thời gian', accessor: (row) => formatDateTime(row.createdAt) },
  ];
  const auditColumns = [
    { header: 'Thời gian', accessor: (row) => formatDateTime(row.createdAt), width: '150px' },
    { header: 'Người thao tác', accessor: (row) => row.username || 'SYSTEM' },
    { header: 'Hành động', accessor: 'action' },
    { header: 'Đối tượng', accessor: (row) => `${row.resourceType || '-'} #${row.resourceId || '-'}` },
    { header: 'Kết quả', accessor: (row) => <Badge variant={row.status === 'SUCCESS' ? 'success' : 'danger'}>{row.status}</Badge> },
    { header: 'IP', accessor: 'ipAddress' },
  ];

  return <div className="content-container">
    <div className="page-header">
      <div><h1 className="page-title">Tài chính, Kiểm toán & Cấu hình</h1><p className="page-subtitle">Đối soát giao dịch, truy vết thao tác và quản lý chỉ tiêu vận hành</p></div>
      <div style={{ display: 'flex', gap: 8 }}>
        {tab === 'config' && <Button icon={Plus} onClick={() => setConfigModalOpen(true)}>Thêm cấu hình</Button>}
        <Button variant="outline" icon={Download} onClick={() => exportCsv(tab, tab === 'config' ? configs : rows)}>Xuất CSV</Button>
        <Button variant="outline" icon={RefreshCw} loading={loading} onClick={load}>Làm mới</Button>
      </div>
    </div>
    <div className="tabs-header">
      {[
        ['payments', 'Thanh toán'],
        ['audit', 'Nhật ký kiểm toán'],
        ['config', 'Cấu hình KPI'],
        ['chatbot', 'Trợ lý AI & RAG'],
      ].map(([id, label]) => (
        <button
          key={id}
          className={`tab-btn ${tab === id ? 'active' : ''}`}
          onClick={() => setTab(id)}
        >
          {label}
        </button>
      ))}
    </div>
    {tab === 'chatbot' && (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        {/* Card 1: Chatbot Service Health */}
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div>
              <h3 style={{ margin: 0, fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Activity size={18} color="var(--color-primary-600)" />
                Trạng thái Dịch vụ Chatbot AI Microservice (Port 8000)
              </h3>
              <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--text-muted)' }}>
                Dịch vụ Python FastAPI cung cấp khả năng hiểu truy vấn NLU, Hybrid Search (BM25 + Dense Qdrant Vector) và tư vấn da liễu.
              </p>
            </div>
            <Button variant="outline" size="sm" icon={RefreshCw} loading={checkingChatbot} onClick={checkChatbotHealth}>
              Kiểm tra kết nối Live
            </Button>
          </div>

          <div style={{ padding: 14, backgroundColor: 'var(--color-primary-50)', border: '1px solid var(--border-subtle)', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div
                style={{
                  width: 14,
                  height: 14,
                  borderRadius: '50%',
                  backgroundColor:
                    chatbotStatus?.status === 'ok' || chatbotStatus?.status === 'UP'
                      ? '#22C55E'
                      : chatbotStatus?.status === 'DOWN'
                        ? '#EF4444'
                        : '#94A3B8',
                }}
              />
              <div>
                <strong>
                  {chatbotStatus?.status === 'ok' || chatbotStatus?.status === 'UP'
                    ? 'Máy chủ Chatbot AI đang hoạt động bình thường (Online)'
                    : chatbotStatus?.status === 'DOWN'
                      ? 'Không thể kết nối tới Chatbot AI Microservice (Offline / Down)'
                      : 'Chưa kiểm tra trạng thái'}
                </strong>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                  Endpoint kiểm tra: <code style={{ fontSize: 11 }}>GET /api/v1/chatbot/health</code> → <code style={{ fontSize: 11 }}>http://localhost:8000/health</code>
                </div>
              </div>
            </div>
            <Badge variant={chatbotStatus?.status === 'ok' || chatbotStatus?.status === 'UP' ? 'success' : 'danger'}>
              {chatbotStatus?.status || 'UNKNOWN'}
            </Badge>
          </div>
        </div>

        {/* Card 2: Database Ingestion into RAG */}
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
            <div>
              <h3 style={{ margin: 0, fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Database size={18} color="var(--color-primary-600)" />
                Đồng bộ Dữ liệu từ MySQL sang Kho Vector RAG (Qdrant & Ingestion)
              </h3>
              <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--text-muted)' }}>
                Trích xuất danh mục sản phẩm, biến thể, công dụng, thành phần hoạt chất và dịch vụ spa từ MySQL chính để nhúng vector vào Qdrant.
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', marginBottom: 14 }}>
            <div style={{ width: 220 }}>
              <Input
                label="Giới hạn bản ghi (bỏ trống = tất cả)"
                type="number"
                placeholder="VD: 50, 100..."
                value={syncLimit}
                onChange={(e) => setSyncLimit(e.target.value)}
              />
            </div>
            <Button
              variant="primary"
              icon={Database}
              loading={syncingDatabase}
              onClick={handleSyncDatabase}
            >
              Kích hoạt Đồng bộ MySQL → RAG
            </Button>
          </div>

          {syncResult && (
            <div style={{ padding: 14, backgroundColor: '#F8FAFC', border: '1px solid var(--border-subtle)', borderRadius: 8, fontSize: 13 }}>
              <div style={{ fontWeight: 600, color: 'var(--color-success-700)', marginBottom: 6 }}>
                ✓ Kết quả đồng bộ từ dịch vụ AI:
              </div>
              <pre style={{ margin: 0, fontSize: 12, overflowX: 'auto', backgroundColor: '#FFFFFF', padding: 10, borderRadius: 4, border: '1px solid #E2E8F0' }}>
                {JSON.stringify(syncResult, null, 2)}
              </pre>
            </div>
          )}
        </div>

        {/* Card 3: NLU Understanding Live Tester */}
        <div className="card">
          <div style={{ marginBottom: 14 }}>
            <h3 style={{ margin: 0, fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Sparkles size={18} color="var(--color-primary-600)" />
              Kiểm thử NLU 6 tầng & Phân tích Ý định Người dùng (NLU Test)
            </h3>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--text-muted)' }}>
              Mô phỏng bộ trích xuất ý định (Intent), loại da (Skin Types), vấn đề da liễu (Concerns) và thực thể mỹ phẩm từ truy vấn tiếng Việt.
            </p>
          </div>

          <form onSubmit={handleTestNlu} style={{ display: 'flex', gap: 10, alignItems: 'flex-end', marginBottom: 16 }}>
            <div style={{ flex: 1 }}>
              <Input
                label="Câu hỏi / Truy vấn người dùng cần phân tích *"
                value={nluMessage}
                onChange={(e) => setNluMessage(e.target.value)}
                placeholder="Nhập câu hỏi test NLU..."
                required
              />
            </div>
            <Button type="submit" variant="primary" icon={Send} loading={testingNlu}>
              Phân tích NLU
            </Button>
          </form>

          {nluResult && (
            <div style={{ padding: 14, backgroundColor: '#F8FAFC', border: '1px solid var(--border-subtle)', borderRadius: 8, fontSize: 13 }}>
              <div style={{ fontWeight: 600, marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
                <CheckCircle2 size={16} color="var(--color-success-600)" />
                Kết quả bóc tách ý định & thực thể:
              </div>
              <pre style={{ margin: 0, fontSize: 12, overflowX: 'auto', backgroundColor: '#FFFFFF', padding: 12, borderRadius: 4, border: '1px solid #E2E8F0', maxHeight: 300 }}>
                {JSON.stringify(nluResult, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    )}
    {tab !== 'config' && tab !== 'chatbot' && <div className="card">
      <form onSubmit={(e) => { e.preventDefault(); load(); }} style={{ display: 'flex', gap: 8, marginBottom: 14 }}><Input aria-label="Tìm kiếm" icon={Search} value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Mã đơn, mã giao dịch, tài khoản..." /><Button type="submit">Tìm</Button></form>
      {tab === 'payments' && summary && <div className="grid-3" style={{ marginBottom: 16 }}><div className="stat-card">Giao dịch: <strong>{summary.totalTransactions}</strong></div><div className="stat-card">Thực nhận: <strong>{formatCurrency(summary.receivedAmount)}</strong></div><div className="stat-card">Lỗi: <strong>{summary.failedTransactions}</strong></div></div>}
      <DataTable columns={tab === 'payments' ? paymentColumns : auditColumns} data={rows} loading={loading} />
    </div>}
    {tab === 'config' && <div className="card admin-config-list">
      {configs.map((config, index) => <div key={config.id} className="admin-config-row">
        <div className="admin-config-details"><strong id={`admin-config-key-${config.id ?? index}`}>{config.configKey}</strong><div id={`admin-config-description-${config.id ?? index}`} style={{ fontSize: 12, color: 'var(--text-muted)' }}>{config.description}</div></div>
        <Input aria-labelledby={`admin-config-key-${config.id ?? index}`} aria-describedby={`admin-config-description-${config.id ?? index}`} value={config.configValue} onChange={(e) => setConfigs((current) => current.map((item, i) => i === index ? { ...item, configValue: e.target.value } : item))} />
        <Button size="sm" icon={Save} onClick={() => saveConfig(config)}>Lưu</Button>
        <Button size="sm" variant="danger" className="admin-config-delete" icon={Trash2} onClick={() => handleDeleteConfig(config.configKey)} title="Xóa cấu hình" aria-label={`Xóa cấu hình ${config.configKey}`} />
      </div>)}
    </div>}

    <Modal isOpen={configModalOpen} onClose={() => setConfigModalOpen(false)} title="Thêm cấu hình hệ thống">
      <form onSubmit={handleCreateConfig}>
        <Input label="Khóa cấu hình (KEY) *" required value={newKey} onChange={(e) => setNewKey(e.target.value)} placeholder="Vd: revenue_target_monthly" />
        <Input label="Giá trị *" required value={newValue} onChange={(e) => setNewValue(e.target.value)} placeholder="Vd: 500000000" />
        <Input label="Mô tả" value={newDesc} onChange={(e) => setNewDesc(e.target.value)} placeholder="Mô tả mục đích cấu hình..." />
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <Button type="submit">Tạo cấu hình</Button>
        </div>
      </form>
    </Modal>
  </div>;
};
