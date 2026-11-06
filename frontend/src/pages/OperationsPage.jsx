import React, { useEffect, useState, useCallback } from 'react';
import {
  Download,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  Bot,
  Sparkles,
  Database,
  Activity,
  CheckCircle2,
  AlertTriangle,
  Send,
  CreditCard,
  FileText,
  Sliders,
  DollarSign,
  ShieldCheck,
  Server,
  Layers,
  X,
  Cpu,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Modal } from '../shared/ui/Modal';
import { ConfirmModal } from '../shared/ui/ConfirmModal';
import { DataTable } from '../shared/ui/DataTable';
import { Badge } from '../shared/ui/Badge';
import { formatCurrency, formatDateTime, formatNumber } from '../shared/utils/formatters';
import { RecommendationAdminPanel } from './RecommendationAdminPanel';
import { PaymentExceptionPanel } from './PaymentExceptionPanel';
import { AdminSectionNav } from '../shared/ui/AdminSectionNav';
import './operations.css';

const PUBLIC_CONFIG_KEYS = new Set([
  'store.address',
  'store.hotline',
  'store.email',
  'shipping.free_threshold',
  'shipping.default_fee',
  'spa.opening_time',
  'spa.closing_time',
]);

const exportCsv = (name, rows) => {
  if (!rows.length) return;
  const headers = Object.keys(rows[0]);
  const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const csv = [
    headers.map(escape).join(','),
    ...rows.map((row) => headers.map((key) => escape(row[key])).join(',')),
  ].join('\n');
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' }));
  link.download = `${name}-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
};

export const OperationsPage = () => {
  const getInitialTab = () => {
    const match = window.location.hash.match(/[?&]tab=([^&]+)/);
    return match ? match[1] : 'payments';
  };
  const [tab, setTabState] = useState(getInitialTab);

  const setTab = useCallback((nextTab) => {
    setTabState(nextTab);
    const base = window.location.hash.split('?')[0];
    window.location.hash = nextTab === 'payments' ? base : `${base}?tab=${nextTab}`;
  }, []);

  useEffect(() => {
    const handleHashSync = () => {
      const match = window.location.hash.match(/[?&]tab=([^&]+)/);
      if (match && match[1]) {
        setTabState(match[1]);
      } else if (!window.location.hash.includes('?tab=')) {
        setTabState('payments');
      }
    };
    window.addEventListener('hashchange', handleHashSync);
    return () => window.removeEventListener('hashchange', handleHashSync);
  }, []);
  const [rows, setRows] = useState([]);
  const [configs, setConfigs] = useState([]);
  const [summary, setSummary] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newIsPublic, setNewIsPublic] = useState(false);
  const [confirmConfig, setConfirmConfig] = useState({
    isOpen: false,
    title: '',
    message: '',
    detail: '',
    isPermanent: false,
    loading: false,
    onConfirm: null,
  });

  const askConfirm = ({ title, message, detail, isPermanent = false, onConfirm }) => {
    setConfirmConfig({
      isOpen: true,
      title,
      message,
      detail,
      isPermanent,
      loading: false,
      onConfirm,
    });
  };

  const closeConfirm = () => {
    setConfirmConfig((prev) => ({ ...prev, isOpen: false, loading: false }));
  };

  const executeConfirm = async () => {
    if (!confirmConfig.onConfirm) return;
    setConfirmConfig((prev) => ({ ...prev, loading: true }));
    try {
      await confirmConfig.onConfirm();
      closeConfirm();
    } catch (err) {
      alert(err.message || 'Thao tác không thành công');
      setConfirmConfig((prev) => ({ ...prev, loading: false }));
    }
  };

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

  const handleSyncDatabase = () => {
    askConfirm({
      title: 'Đồng bộ dữ liệu vào kho vector AI',
      message: 'Kích hoạt đồng bộ dữ liệu sản phẩm & dịch vụ từ MySQL vào kho vector Qdrant RAG?',
      detail: 'Quá trình này sẽ trích xuất thông tin sản phẩm/dịch vụ mới nhất, vector hóa và nạp vào Qdrant để nâng cao độ chính xác khi trợ lý AI tư vấn.',
      isPermanent: false,
      confirmText: 'Bắt đầu đồng bộ',
      onConfirm: async () => {
        setSyncingDatabase(true);
        setSyncResult(null);
        try {
          const url = syncLimit.trim()
            ? `${ENDPOINTS.CHATBOT.SYNC_DATABASE}?limit=${encodeURIComponent(syncLimit.trim())}`
            : ENDPOINTS.CHATBOT.SYNC_DATABASE;
          const res = await apiClient.post(url);
          const data = res?.data || res;
          setSyncResult(data);
        } catch (err) {
          alert(err.message || 'Lỗi khi đồng bộ dữ liệu vào RAG');
        } finally {
          setSyncingDatabase(false);
        }
      },
    });
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
        const res = await apiClient.get(
          `${ENDPOINTS.SYSTEM.AUDIT_LOGS}?size=50&sort=createdAt,desc&keyword=${encodeURIComponent(keyword)}`
        );
        setRows((res.data || res).content || []);
      } else if (tab === 'chatbot') {
        checkChatbotHealth();
      } else if (tab === 'recommendations') {
        // Managed by RecommendationAdminPanel
      } else {
        const res = await apiClient.get(ENDPOINTS.SYSTEM.CONFIGS);
        setConfigs(res.data || res || []);
      }
    } catch (err) {
      console.error('Không thể tải dữ liệu vận hành', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [tab]);

  const saveConfig = async (config) => {
    try {
      const isPublic =
        config.isPublicDraft == null || config.isPublicDraft === Boolean(config.isPublic)
          ? null
          : config.isPublicDraft;
      await apiClient.put(ENDPOINTS.SYSTEM.CONFIG(config.configKey), {
        value: config.configValue,
        isPublic,
      });
      load();
    } catch (err) {
      alert(err.message || 'Lỗi khi lưu cấu hình');
    }
  };

  const handleCreateConfig = async (e) => {
    e.preventDefault();
    try {
      await apiClient.post(ENDPOINTS.SYSTEM.CREATE_CONFIG, {
        key: newKey.trim(),
        value: newValue.trim(),
        description: newDesc.trim(),
        isPublic: newIsPublic,
      });
      setConfigModalOpen(false);
      setNewKey('');
      setNewValue('');
      setNewDesc('');
      setNewIsPublic(false);
      load();
    } catch (err) {
      alert(err.message || 'Lỗi khi tạo cấu hình');
    }
  };

  const handleDeleteConfig = (key) => {
    askConfirm({
      title: 'Xác nhận xóa cấu hình hệ thống',
      message: `Bạn có chắc muốn xóa cấu hình "${key}"?`,
      detail: 'Khóa cấu hình sẽ được đưa vào lưu trữ ẩn (soft-delete). Hệ thống sẽ tự động chuyển về sử dụng giá trị mặc định được định nghĩa sẵn trong mã nguồn.',
      isPermanent: false,
      onConfirm: async () => {
        await apiClient.delete(ENDPOINTS.SYSTEM.DELETE_CONFIG(key));
        load();
      },
    });
  };

  const paymentColumns = [
    {
      header: 'Đơn hàng',
      accessor: (row) => (
        <div>
          <strong style={{ color: 'var(--text-main)', fontSize: '13px' }}>{row.orderNumber}</strong>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Mã #{row.id}</div>
        </div>
      ),
      width: '150px',
    },
    {
      header: 'Mã đối soát giao dịch',
      accessor: (row) => (
        <code style={{ fontWeight: 700, color: 'var(--color-accent-700)', fontFamily: 'var(--font-mono)' }}>
          {row.referenceCode || '—'}
        </code>
      ),
      width: '180px',
    },
    {
      header: 'Cổng thanh toán',
      accessor: (row) => (
        <span style={{ fontWeight: 600, fontSize: '12px' }}>{row.gateway || 'SePay (VietQR)'}</span>
      ),
      width: '150px',
    },
    {
      header: 'Số tiền thanh toán',
      accessor: (row) => (
        <strong style={{ fontSize: '13.5px', fontVariantNumeric: 'tabular-nums', color: 'var(--text-main)' }}>
          {formatCurrency(row.amount)}
        </strong>
      ),
      align: 'right',
      width: '150px',
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const isSuccess = row.status === 'SUCCESS';
        const isFailed = row.status === 'FAILED';
        const label = isSuccess ? 'Thành công' : isFailed ? 'Thất bại' : 'Đang xử lý';
        return <Badge variant={isSuccess ? 'success' : isFailed ? 'danger' : 'warning'}>{label}</Badge>;
      },
      width: '120px',
      align: 'center',
    },
    {
      header: 'Thời gian',
      accessor: (row) => (
        <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{formatDateTime(row.createdAt)}</span>
      ),
      width: '150px',
    },
  ];

  const auditColumns = [
    {
      header: 'Thời gian',
      accessor: (row) => (
        <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{formatDateTime(row.createdAt)}</span>
      ),
      width: '150px',
    },
    {
      header: 'Người thao tác',
      accessor: (row) => {
        const name = row.username === 'SYSTEM' ? 'Hệ thống tự động' : (row.username || 'Hệ thống');
        const isSystem = row.username === 'SYSTEM';

        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div
              style={{
                width: '28px',
                height: '28px',
                borderRadius: '50%',
                background: isSystem ? 'var(--bg-surface-subtle)' : 'var(--color-accent-100)',
                color: isSystem ? 'var(--text-muted)' : 'var(--color-accent-700)',
                display: 'grid',
                placeItems: 'center',
                fontSize: '11px',
                fontWeight: 700,
                flexShrink: 0,
              }}
            >
              {isSystem ? 'HT' : name.charAt(0).toUpperCase()}
            </div>
            <strong style={{ fontSize: '12.5px', color: 'var(--text-main)' }}>{name}</strong>
          </div>
        );
      },
      width: '180px',
    },
    {
      header: 'Hành động',
      accessor: (row) => (
        <span style={{ fontWeight: 600, fontSize: '12.5px', color: 'var(--color-accent-700)' }}>
          {row.action}
        </span>
      ),
      width: '160px',
    },
    {
      header: 'Đối tượng tác động',
      accessor: (row) => (
        <code style={{ fontFamily: 'var(--font-mono)', fontSize: '11.5px', color: 'var(--text-muted)' }}>
          {row.resourceType || '-'} #{row.resourceId || '-'}
        </code>
      ),
      width: '180px',
    },
    {
      header: 'Kết quả',
      accessor: (row) => {
        const isSuccess = row.status === 'SUCCESS';
        return <Badge variant={isSuccess ? 'success' : 'danger'}>{isSuccess ? 'Thành công' : 'Thất bại'}</Badge>;
      },
      width: '110px',
      align: 'center',
    },
    {
      header: 'Địa chỉ IP',
      accessor: (row) => (
        <span style={{ fontSize: '12px', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
          {row.ipAddress || '—'}
        </span>
      ),
      width: '130px',
    },
  ];

  return (
    <div className="content-container operations-container">
      {/* Header Dropdown Switcher */}
      <AdminSectionNav
        rootTitle="Vận hành & Giám sát"
        activeTab={tab}
        onChange={setTab}
        subtitle="Đối soát giao dịch thanh toán, truy vết thao tác người dùng và kiểm soát cấu hình hệ thống"
        items={[
          {
            id: 'payments',
            label: 'Thanh toán & Đối soát',
            icon: CreditCard,
            description: 'Lịch sử thanh toán đơn hàng, đối soát SePay và xử lý ngoại lệ',
          },
          {
            id: 'audit',
            label: 'Nhật ký thao tác (Audit)',
            icon: FileText,
            description: 'Truy vết thay đổi dữ liệu của quản trị viên và nhân viên',
          },
          {
            id: 'config',
            label: 'Chỉ tiêu & Cấu hình',
            count: configs.length,
            icon: Sliders,
            description: 'Hạn mức miễn phí vận chuyển, hotline và tham số hệ thống',
          },
          {
            id: 'chatbot',
            label: 'Trợ lý tư vấn AI',
            icon: Bot,
            description: 'Giám sát hoạt động Agent tư vấn và kiểm thử kịch bản NLU',
          },
          {
            id: 'recommendations',
            label: 'Gợi ý sản phẩm tương tự',
            icon: Sparkles,
            description: 'Huấn luyện ma trận TF-IDF và thuật toán gợi ý mỹ phẩm',
          },
        ]}
        actions={
          <>
            {tab === 'config' && (
              <Button icon={Plus} variant="primary" size="sm" onClick={() => setConfigModalOpen(true)}>
                Thêm cấu hình
              </Button>
            )}
            {tab !== 'recommendations' && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  icon={Download}
                  onClick={() => exportCsv(tab, tab === 'config' ? configs : rows)}
                >
                  Xuất CSV
                </Button>
                <Button variant="outline" size="sm" icon={RefreshCw} loading={loading} onClick={load}>
                  Làm mới
                </Button>
              </>
            )}
          </>
        }
      />

      {/* TAB 1: THANH TOÁN & ĐỐI SOÁT */}
      {tab === 'payments' && (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {/* Collapsible Payment Exception Panel */}
          <PaymentExceptionPanel onChanged={load} />

          {/* KPI Summary Cards */}
          {summary && (
            <div className="operations-stats-grid">
              <div className="operations-stat-card">
                <div className="operations-stat-icon violet">
                  <CreditCard size={20} />
                </div>
                <div className="operations-stat-info">
                  <h4>Tổng số giao dịch</h4>
                  <strong>{formatNumber(summary.totalTransactions || 0)}</strong>
                  <small>Giao dịch thanh toán SePay</small>
                </div>
              </div>

              <div className="operations-stat-card">
                <div className="operations-stat-icon green">
                  <DollarSign size={20} />
                </div>
                <div className="operations-stat-info">
                  <h4>Số tiền thực nhận</h4>
                  <strong>{formatCurrency(summary.receivedAmount || 0)}</strong>
                  <small>Đã đối soát ghi nhận vào đơn</small>
                </div>
              </div>

              <div className="operations-stat-card">
                <div className="operations-stat-icon rose">
                  <AlertTriangle size={20} />
                </div>
                <div className="operations-stat-info">
                  <h4>Giao dịch thất bại / Lỗi</h4>
                  <strong>{formatNumber(summary.failedTransactions || 0)}</strong>
                  <small>Cần kiểm tra hoặc hoàn tiền</small>
                </div>
              </div>
            </div>
          )}

          {/* Payment List Card */}
          <div className="card">
            <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
              <div>
                <span className="card-title">Lịch sử thanh toán đơn hàng & Đối soát</span>
                <span style={{ fontSize: '12px', color: 'var(--text-muted)', display: 'block', marginTop: '2px' }}>
                  Hiển thị {rows.length} giao dịch gần nhất
                </span>
              </div>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  load();
                }}
                style={{ display: 'flex', gap: '8px', minWidth: '280px' }}
              >
                <div style={{ position: 'relative', flex: 1 }}>
                  <Search size={15} color="var(--text-muted)" style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)' }} />
                  <input
                    type="text"
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                    placeholder="Mã đơn hàng, mã đối soát..."
                    style={{
                      width: '100%',
                      padding: '7px 10px 7px 32px',
                      fontSize: '12.5px',
                      borderRadius: 'var(--radius-sm, 6px)',
                      border: '1px solid var(--border-color, #E5E7EB)',
                      outline: 'none',
                    }}
                  />
                  {keyword && (
                    <button
                      type="button"
                      onClick={() => { setKeyword(''); load(); }}
                      style={{ position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', fontSize: '12px', color: 'var(--text-muted)' }}
                    >
                      ×
                    </button>
                  )}
                </div>
                <Button type="submit" size="sm" variant="primary">
                  Tìm
                </Button>
              </form>
            </div>

            <DataTable columns={paymentColumns} data={rows} loading={loading} />
          </div>
        </div>
      )}

      {/* TAB 2: NHẬT KÝ THAO TÁC AUDIT */}
      {tab === 'audit' && (
        <div className="card">
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <span className="card-title">Nhật ký truy vết thao tác người dùng (Audit Log)</span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)', display: 'block', marginTop: '2px' }}>
                Lưu giữ mọi hành động thêm, sửa, xóa, duyệt của quản trị viên và nhân viên
              </span>
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                load();
              }}
              style={{ display: 'flex', gap: '8px', minWidth: '280px' }}
            >
              <div style={{ position: 'relative', flex: 1 }}>
                <Search size={15} color="var(--text-muted)" style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)' }} />
                <input
                  type="text"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="Tìm theo tài khoản, hành động, IP..."
                  style={{
                    width: '100%',
                    padding: '7px 10px 7px 32px',
                    fontSize: '12.5px',
                    borderRadius: 'var(--radius-sm, 6px)',
                    border: '1px solid var(--border-color, #E5E7EB)',
                    outline: 'none',
                  }}
                />
              </div>
              <Button type="submit" size="sm" variant="primary">
                Tìm
              </Button>
            </form>
          </div>

          <DataTable columns={auditColumns} data={rows} loading={loading} />
        </div>
      )}

      {/* TAB 3: CHỈ TIÊU & CẤU HÌNH */}
      {tab === 'config' && (
        <div className="card">
          <div style={{ background: '#FFFDF9', border: '1px solid #FDE68A', borderRadius: '10px', padding: '14px 18px', marginBottom: '18px' }}>
            <strong style={{ fontSize: '13px', color: '#926C2A', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ShieldCheck size={16} /> Hướng dẫn cấu hình hệ thống:
            </strong>
            <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: '#785A24', lineHeight: 1.5 }}>
              Thông tin cửa hàng, hotline và ngưỡng miễn phí vận chuyển sử dụng các khóa chuẩn (<code>store.address</code>, <code>store.hotline</code>, <code>store.email</code>, <code>shipping.free_threshold</code>). Chỉ tích chọn "Công khai trên website" với các tham số cho phép hiển thị với khách hàng.
            </p>
          </div>

          <div className="operations-config-list">
            {configs.map((config, index) => (
              <div key={config.id || index} className="operations-config-row">
                <div>
                  <span className="operations-config-key">{config.configKey}</span>
                  <div className="operations-config-desc">
                    {config.description || 'Không có mô tả chi tiết'}
                  </div>
                  <label className="operations-config-public-toggle">
                    <input
                      type="checkbox"
                      checked={config.isPublicDraft ?? Boolean(config.isPublic)}
                      disabled={!config.isPublic && !PUBLIC_CONFIG_KEYS.has(config.configKey)}
                      onChange={(e) =>
                        setConfigs((current) =>
                          current.map((item, i) =>
                            i === index ? { ...item, isPublicDraft: e.target.checked } : item
                          )
                        )
                      }
                    />
                    <span>Công khai trên website</span>
                  </label>
                </div>

                <div>
                  <input
                    className="operations-config-input"
                    value={config.configValue || ''}
                    onChange={(e) =>
                      setConfigs((current) =>
                        current.map((item, i) =>
                          i === index ? { ...item, configValue: e.target.value } : item
                        )
                      )
                    }
                  />
                </div>

                <Button size="sm" variant="primary" icon={Save} onClick={() => saveConfig(config)}>
                  Lưu
                </Button>

                <Button
                  size="sm"
                  variant="outline"
                  icon={Trash2}
                  style={{ color: 'var(--color-danger-700)', borderColor: '#FECACA' }}
                  onClick={() => handleDeleteConfig(config.configKey)}
                  title="Xóa cấu hình"
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 4: TRỢ LÝ TƯ VẤN AI */}
      {tab === 'chatbot' && (
        <div className="operations-chatbot-grid">
          {/* Machine Health Card */}
          <div className="operations-card">
            <div className="operations-card-header">
              <div className="operations-card-title">
                <h3>
                  <Activity size={18} color="var(--color-accent-600)" />
                  Trạng thái Máy chủ Trợ lý AI (FastAPI / Gemini / Qdrant)
                </h3>
                <p>
                  Dịch vụ xử lý ngôn ngữ tự nhiên (NLU), truy vấn RAG hybrid và điều phối kịch bản tư vấn da liễu.
                </p>
              </div>
              <Button variant="outline" size="sm" icon={RefreshCw} loading={checkingChatbot} onClick={checkChatbotHealth}>
                Kiểm tra kết nối trực tiếp
              </Button>
            </div>

            <div style={{
              padding: '14px 18px',
              backgroundColor: 'var(--bg-surface-subtle)',
              border: '1px solid var(--border-subtle)',
              borderRadius: '10px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <div
                  style={{
                    width: '12px',
                    height: '12px',
                    borderRadius: '50%',
                    backgroundColor:
                      chatbotStatus?.status === 'ok' || chatbotStatus?.status === 'UP'
                        ? '#16A34A'
                        : chatbotStatus?.status === 'DOWN'
                          ? '#DC2626'
                          : '#94A3B8',
                    boxShadow: chatbotStatus?.status === 'ok' ? '0 0 0 3px rgba(34, 197, 94, 0.2)' : 'none',
                  }}
                />
                <div>
                  <strong style={{ fontSize: '13.5px', color: 'var(--text-main)' }}>
                    {chatbotStatus?.status === 'ok' || chatbotStatus?.status === 'UP'
                      ? 'Máy chủ Trợ lý AI đang hoạt động bình thường'
                      : chatbotStatus?.status === 'DOWN'
                        ? 'Không thể kết nối tới máy chủ Trợ lý AI (Mất kết nối)'
                        : 'Chưa kiểm tra trạng thái máy chủ'}
                  </strong>
                  <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', marginTop: '2px' }}>
                    Phiên bản engine: v2.5 · Cổng kết nối nội bộ Docker network
                  </div>
                </div>
              </div>

              <Badge variant={chatbotStatus?.status === 'ok' || chatbotStatus?.status === 'UP' ? 'success' : 'danger'}>
                {chatbotStatus?.status || 'UNKNOWN'}
              </Badge>
            </div>
          </div>

          {/* Knowledge Sync Card */}
          <div className="operations-card">
            <div className="operations-card-header">
              <div className="operations-card-title">
                <h3>
                  <Database size={18} color="var(--color-accent-600)" />
                  Đồng bộ Kiến thức Sản phẩm & Liệu trình vào Vector RAG
                </h3>
                <p>
                  Nhúng văn bản (Embedding) toàn bộ danh mục sản phẩm, hoạt chất da liễu và dịch vụ spa vào kho vector Qdrant.
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <div style={{ width: '240px' }}>
                <Input
                  label="Giới hạn số lượng bản ghi (trống = tất cả)"
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
                Kích hoạt đồng bộ kiến thức
              </Button>
            </div>

            {syncResult && (
              <div className="operations-json-box">
                <div style={{ fontWeight: 600, color: 'var(--color-success-700)', marginBottom: '6px' }}>
                  Kết quả phản hồi từ dịch vụ AI:
                </div>
                <pre>{JSON.stringify(syncResult, null, 2)}</pre>
              </div>
            )}
          </div>

          {/* NLU Diagnostic Playground */}
          <div className="operations-card">
            <div className="operations-card-header">
              <div className="operations-card-title">
                <h3>
                  <Cpu size={18} color="var(--color-accent-600)" />
                  Kiểm thử Phân tích Ý định & Bóc tách Thực thể NLU
                </h3>
                <p>
                  Mô phỏng 6-layer NLU: Bóc tách loại da, tình trạng mụn/thâm, thành phần hóa học và dịch vụ khách đang quan tâm.
                </p>
              </div>
            </div>

            <form onSubmit={handleTestNlu} style={{ display: 'flex', gap: '10px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <div style={{ flex: 1, minWidth: '280px' }}>
                <Input
                  label="Câu hỏi / Lời thoại của khách hàng *"
                  required
                  value={nluMessage}
                  onChange={(e) => setNluMessage(e.target.value)}
                  placeholder="Nhập câu hỏi test NLU..."
                />
              </div>
              <Button type="submit" variant="primary" icon={Send} loading={testingNlu}>
                Phân tích NLU
              </Button>
            </form>

            {nluResult && (
              <div className="operations-json-box">
                <div style={{ fontWeight: 600, color: 'var(--color-accent-700)', marginBottom: '6px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <CheckCircle2 size={15} color="var(--color-accent-600)" />
                  Dữ liệu thực thể bóc tách từ Gemini NLU:
                </div>
                <pre>{JSON.stringify(nluResult, null, 2)}</pre>
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 5: GỢI Ý SẢN PHẨM TƯƠNG TỰ */}
      {tab === 'recommendations' && <RecommendationAdminPanel />}

      {/* Modal Thêm cấu hình */}
      <Modal isOpen={configModalOpen} onClose={() => setConfigModalOpen(false)} title="Thêm cấu hình hệ thống mới" maxWidth="540px">
        <form onSubmit={handleCreateConfig} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <Input
            label="Khóa cấu hình (KEY) *"
            required
            value={newKey}
            onChange={(e) => {
              setNewKey(e.target.value);
              if (!PUBLIC_CONFIG_KEYS.has(e.target.value.trim())) setNewIsPublic(false);
            }}
            placeholder="VD: store.hotline, shipping.free_threshold..."
          />
          <Input
            label="Giá trị cấu hình (VALUE) *"
            required
            value={newValue}
            onChange={(e) => setNewValue(e.target.value)}
            placeholder="VD: 1900 6868"
          />
          <Input
            label="Mô tả mục đích cấu hình"
            value={newDesc}
            onChange={(e) => setNewDesc(e.target.value)}
            placeholder="Ghi chú mục đích sử dụng..."
          />
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12.5px', fontWeight: 600, cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={newIsPublic}
              disabled={!PUBLIC_CONFIG_KEYS.has(newKey.trim())}
              onChange={(e) => setNewIsPublic(e.target.checked)}
              style={{ accentColor: 'var(--color-accent-600)', width: 15, height: 15 }}
            />
            <span>Công khai trên website (Khách hàng có thể đọc)</span>
          </label>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '10px' }}>
            <Button type="button" variant="outline" onClick={() => setConfigModalOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary">
              Tạo cấu hình
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        isOpen={confirmConfig.isOpen}
        onClose={closeConfirm}
        onConfirm={executeConfirm}
        title={confirmConfig.title}
        message={confirmConfig.message}
        detail={confirmConfig.detail}
        isPermanent={confirmConfig.isPermanent}
        loading={confirmConfig.loading}
      />
    </div>
  );
};
