import React, { useEffect, useState } from 'react';
import {
  Users,
  Search,
  Trash2,
  Phone,
  Mail,
  HeartHandshake,
  FileText,
  ShieldAlert,
  Calendar,
  Sparkles,
  ShoppingBag,
  Award,
  Plus,
  RefreshCw,
  Clock,
  UserCheck,
  Send,
  X,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Modal } from '../shared/ui/Modal';
import { DataTable } from '../shared/ui/DataTable';
import { Badge } from '../shared/ui/Badge';
import { ConfirmModal } from '../shared/ui/ConfirmModal';
import { CrmWorkPanel } from './CrmWorkPanel';
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  getMembershipTierLabel,
} from '../shared/utils/formatters';
import './crm.css';

const NOTE_TYPE_MAP = {
  GENERAL: { label: 'Chăm sóc chung', variant: 'default' },
  SKIN_ASSESSMENT: { label: 'Đánh giá da', variant: 'info' },
  TREATMENT: { label: 'Theo dõi liệu trình', variant: 'success' },
  COMPLAINT: { label: 'Khiếu nại', variant: 'danger' },
};

export const CrmPage = () => {
  const [customers, setCustomers] = useState([]);
  const [customerPage, setCustomerPage] = useState(0);
  const [customerPages, setCustomerPages] = useState(1);
  const [totalCustomers, setTotalCustomers] = useState(0);

  const [notePage, setNotePage] = useState(0);
  const [notePages, setNotePages] = useState(1);
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
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);
  const [notes, setNotes] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalTab, setModalTab] = useState('notes'); // 'notes' | 'clinical'
  const [noteLoading, setNoteLoading] = useState(false);

  const [form, setForm] = useState({
    noteType: 'GENERAL',
    content: '',
    skinProfile: '',
    allergies: '',
    contraindications: '',
    followUpAt: '',
  });

  const search = async (page = 0) => {
    setLoading(true);
    try {
      const res = await apiClient.get(
        `${ENDPOINTS.CRM.CUSTOMERS}/page?keyword=${encodeURIComponent(keyword.trim())}&page=${page}&size=15`
      );
      const data = res?.data ?? res;
      setCustomers(data.content || []);
      setCustomerPage(data.page || 0);
      setCustomerPages(data.totalPages || 1);
      setTotalCustomers(data.totalElements || 0);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchNotes = async (customerId, page = 0) => {
    setNoteLoading(true);
    try {
      const res = await apiClient.get(
        `${ENDPOINTS.CRM.NOTES(customerId)}/page?page=${page}&size=20`
      );
      const data = res?.data ?? res;
      setNotes(data.content || []);
      setNotePage(data.page || 0);
      setNotePages(data.totalPages || 1);
    } catch (err) {
      console.error(err);
    } finally {
      setNoteLoading(false);
    }
  };

  useEffect(() => {
    search();
  }, []);

  const openCustomer = async (customer) => {
    setSelected(customer);
    setModalTab('notes');
    setModalOpen(true);
    setForm({
      noteType: 'GENERAL',
      content: '',
      skinProfile: '',
      allergies: '',
      contraindications: '',
      followUpAt: '',
    });
    fetchNotes(customer.id, 0);
  };

  const addNote = async (e) => {
    e.preventDefault();
    if (!form.content.trim()) return;
    try {
      await apiClient.post(ENDPOINTS.CRM.NOTES(selected.id), {
        ...form,
        followUpAt: form.followUpAt ? new Date(form.followUpAt).toISOString() : null,
      });
      await fetchNotes(selected.id, 0);
      setForm({
        noteType: 'GENERAL',
        content: '',
        skinProfile: '',
        allergies: '',
        contraindications: '',
        followUpAt: '',
      });
    } catch (err) {
      alert(err.message || 'Lỗi thêm ghi chú chăm sóc');
    }
  };

  const deleteNote = (noteId) => {
    askConfirm({
      title: 'Xác nhận xóa ghi chú chăm sóc',
      message: 'Bạn có chắc chắn muốn xóa ghi chú chăm sóc này?',
      detail: 'Ghi chú sẽ được đánh dấu xóa mềm khỏi hồ sơ chăm sóc của khách hàng.',
      isPermanent: false,
      onConfirm: async () => {
        await apiClient.delete(ENDPOINTS.CRM.DELETE_NOTE(noteId));
        await fetchNotes(selected.id, notePage);
      },
    });
  };

  const vipCount = customers.filter((c) =>
    ['SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'].includes(String(c.membershipTier || '').toUpperCase())
  ).length;

  const totalLifetimeValue = customers.reduce((sum, c) => sum + Number(c.lifetimeValue || 0), 0);

  const columns = [
    {
      header: 'Khách hàng',
      accessor: (row) => {
        const name = row.fullName || row.username || 'Khách hàng';
        const letter = name.charAt(0).toUpperCase();

        return (
          <div className="crm-customer-cell">
            <div className="crm-customer-avatar">{letter}</div>
            <div>
              <div style={{ fontWeight: 700, color: 'var(--text-main)', fontSize: '13.5px' }}>
                {name}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', gap: '6px', marginTop: '2px' }}>
                <span>@{row.username}</span>
                <span>·</span>
                <span>Mã #{row.id}</span>
              </div>
            </div>
          </div>
        );
      },
      width: '260px',
    },
    {
      header: 'Liên hệ',
      accessor: (row) => (
        <div style={{ fontSize: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '5px', color: 'var(--text-main)', fontWeight: 550 }}>
            <Phone size={12} color="var(--text-muted)" />
            <span>{row.phone || 'Chưa có SĐT'}</span>
          </div>
          {row.email && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '5px', color: 'var(--text-muted)', fontSize: '11px', marginTop: '3px' }}>
              <Mail size={12} />
              <span>{row.email}</span>
            </div>
          )}
        </div>
      ),
      width: '180px',
    },
    {
      header: 'Hạng & Tích điểm',
      accessor: (row) => (
        <div>
          <Badge variant="info">
            {getMembershipTierLabel(row.membershipTier)}
          </Badge>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '3px', fontWeight: 600 }}>
            {formatNumber(row.loyaltyPoints || 0)} điểm
          </div>
        </div>
      ),
      width: '140px',
      align: 'center',
    },
    {
      header: 'Hoạt động',
      accessor: (row) => (
        <div style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
          <div>
            Đơn hàng: <strong style={{ color: 'var(--text-main)' }}>{row.orderCount || 0}</strong>
          </div>
          <div style={{ marginTop: '2px' }}>
            Lịch Spa: <strong style={{ color: 'var(--color-accent-700, #6040ce)' }}>{row.appointmentCount || 0}</strong>
          </div>
        </div>
      ),
      width: '120px',
    },
    {
      header: 'Giá trị trọn đời (LTV)',
      accessor: (row) => (
        <strong style={{
          color: Number(row.lifetimeValue || 0) > 0 ? 'var(--color-success-700, #16766b)' : 'var(--text-muted)',
          fontSize: '13px',
          fontVariantNumeric: 'tabular-nums',
        }}>
          {formatCurrency(row.lifetimeValue || 0)}
        </strong>
      ),
      align: 'right',
      width: '150px',
    },
    {
      header: 'Thao tác',
      align: 'right',
      accessor: (row) => (
        <Button
          size="sm"
          variant="primary"
          icon={FileText}
          onClick={() => openCustomer(row)}
          style={{ fontSize: '11.5px', padding: '6px 12px' }}
        >
          Hồ sơ 360°
        </Button>
      ),
      width: '120px',
    },
  ];

  return (
    <div className="content-container crm-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Hồ sơ Khách hàng & CRM 360°</h1>
          <p className="page-subtitle">
            Quản lý quan hệ khách hàng, lịch sử chi tiêu vòng đời (LTV), cảnh báo da liễu và theo dõi tiến độ chăm sóc
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          icon={RefreshCw}
          loading={loading}
          onClick={() => search(customerPage)}
        >
          Làm mới
        </Button>
      </div>

      {/* KPI Stats Overview */}
      <div className="crm-stats-grid">
        <div className="crm-stat-card">
          <div className="crm-stat-icon violet">
            <Users size={20} />
          </div>
          <div className="crm-stat-info">
            <h4>Khách hàng quản lý</h4>
            <strong>{formatNumber(totalCustomers)}</strong>
            <small>Tài khoản mua sắm & dịch vụ</small>
          </div>
        </div>

        <div className="crm-stat-card">
          <div className="crm-stat-icon amber">
            <Award size={20} />
          </div>
          <div className="crm-stat-info">
            <h4>Hạng thẻ VIP / Thân thiết</h4>
            <strong>{vipCount}</strong>
            <small>Bạc, Vàng, Bạch kim, Kim cương</small>
          </div>
        </div>

        <div className="crm-stat-card">
          <div className="crm-stat-icon emerald">
            <ShoppingBag size={20} />
          </div>
          <div className="crm-stat-info">
            <h4>Tổng LTV trang hiện tại</h4>
            <strong>{formatCurrency(totalLifetimeValue)}</strong>
            <small>Giá trị thực thu sau hoàn trả</small>
          </div>
        </div>
      </div>

      {/* Collapsible Work Items Panel */}
      <CrmWorkPanel />

      {/* Customer List Card */}
      <div className="card">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            search(0);
          }}
          className="crm-search-bar"
        >
          <div className="crm-search-input-wrap">
            <Search size={16} />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="Tìm kiếm theo họ tên, username, số điện thoại hoặc email khách hàng..."
            />
          </div>

          <Button type="submit" variant="primary" icon={Search}>
            Tìm kiếm
          </Button>

          {keyword && (
            <Button
              type="button"
              variant="outline"
              icon={X}
              onClick={() => {
                setKeyword('');
                search(0);
              }}
            >
              Xóa bộ lọc
            </Button>
          )}
        </form>

        <DataTable
          columns={columns}
          data={customers}
          loading={loading}
          emptyMessage="Không tìm thấy hồ sơ khách hàng nào phù hợp với từ khóa."
          page={customerPage}
          totalPages={customerPages}
          totalElements={totalCustomers}
          onPageChange={(p) => search(p)}
        />
      </div>

      {/* Modal Hồ sơ Khách hàng 360° (Widescreen 1140px) */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={`Hồ sơ Khách hàng 360° — ${selected?.fullName || selected?.username || ''}`}
        maxWidth="1140px"
      >
        {selected && (
          <div className="crm-360-layout">
            {/* Left Sidebar: Profile Summary */}
            <aside className="crm-360-sidebar">
              <div className="crm-360-profile-header">
                <div className="crm-360-avatar-large">
                  {(selected.fullName || selected.username || 'K').charAt(0).toUpperCase()}
                </div>
                <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: 'var(--text-main)' }}>
                  {selected.fullName || selected.username}
                </h3>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  @{selected.username} · ID: #{selected.id}
                </div>
                <Badge variant="info">
                  {getMembershipTierLabel(selected.membershipTier)}
                </Badge>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Phone size={14} color="var(--text-muted)" />
                  <strong>{selected.phone || 'Chưa cung cấp'}</strong>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Mail size={14} color="var(--text-muted)" />
                  <span style={{ overflowWrap: 'anywhere' }}>{selected.email || 'Chưa cung cấp'}</span>
                </div>
              </div>

              <div className="crm-360-stats-stack">
                <div className="crm-360-stat-row">
                  <span>Giá trị vòng đời (LTV):</span>
                  <strong style={{ color: 'var(--color-success-700, #16766b)', fontSize: '13.5px' }}>
                    {formatCurrency(selected.lifetimeValue || 0)}
                  </strong>
                </div>
                <div className="crm-360-stat-row">
                  <span>Điểm thưởng tích lũy:</span>
                  <strong>{formatNumber(selected.loyaltyPoints || 0)} điểm</strong>
                </div>
                <div className="crm-360-stat-row">
                  <span>Số đơn mua mỹ phẩm:</span>
                  <strong>{formatNumber(selected.orderCount || 0)} đơn</strong>
                </div>
                <div className="crm-360-stat-row">
                  <span>Lịch hẹn chăm sóc Spa:</span>
                  <strong style={{ color: 'var(--color-accent-700, #6040ce)' }}>
                    {formatNumber(selected.appointmentCount || 0)} lần
                  </strong>
                </div>
              </div>
            </aside>

            {/* Right Main Content: 360 Tabs */}
            <div>
              <div className="crm-360-tabs">
                <button
                  type="button"
                  className={`crm-360-tab-btn ${modalTab === 'notes' ? 'active' : ''}`}
                  onClick={() => setModalTab('notes')}
                >
                  <FileText size={15} />
                  Nhật ký Chăm sóc & Lịch hẹn ({notes.length})
                </button>
                <button
                  type="button"
                  className={`crm-360-tab-btn ${modalTab === 'clinical' ? 'active' : ''}`}
                  onClick={() => setModalTab('clinical')}
                >
                  <ShieldAlert size={15} />
                  Hồ sơ Da liễu & An toàn
                </button>
              </div>

              {/* TAB 1: NOTES & FOLLOW-UP */}
              {modalTab === 'notes' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {/* Form Thêm Ghi Chú Chăm Sóc */}
                  <form
                    onSubmit={addNote}
                    style={{
                      background: 'var(--bg-surface-subtle, #f6f3fb)',
                      border: '1px solid var(--border-subtle, #eee9f4)',
                      borderRadius: '10px',
                      padding: '16px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <strong style={{ fontSize: '13px', color: 'var(--text-main)' }}>
                        Thêm ghi chú chăm sóc nội bộ
                      </strong>
                      <Select
                        value={form.noteType}
                        onChange={(e) => setForm({ ...form, noteType: e.target.value })}
                        options={[
                          { label: 'Chăm sóc chung', value: 'GENERAL' },
                          { label: 'Đánh giá da', value: 'SKIN_ASSESSMENT' },
                          { label: 'Theo dõi liệu trình', value: 'TREATMENT' },
                          { label: 'Khiếu nại', value: 'COMPLAINT' },
                        ]}
                      />
                    </div>

                    <textarea
                      required
                      className="form-textarea"
                      rows={2}
                      placeholder="Ghi nhận nội dung trao đổi, tư vấn routine hoặc phản hồi của khách hàng..."
                      value={form.content}
                      onChange={(e) => setForm({ ...form, content: e.target.value })}
                    />

                    <div className="grid-2">
                      <Input
                        label="Lên lịch hẹn chăm sóc lại (Follow-up)"
                        type="datetime-local"
                        value={form.followUpAt}
                        onChange={(e) => setForm({ ...form, followUpAt: e.target.value })}
                      />
                      <Input
                        label="Tình trạng da ghi nhận (nếu có)"
                        placeholder="VD: Da dầu mụn, đang cải thiện..."
                        value={form.skinProfile}
                        onChange={(e) => setForm({ ...form, skinProfile: e.target.value })}
                      />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
                      <Button type="submit" variant="primary" size="sm" icon={Send}>
                        Lưu ghi chú
                      </Button>
                    </div>
                  </form>

                  {/* Notes Timeline List */}
                  <div>
                    {noteLoading ? (
                      <p style={{ textAlign: 'center', padding: '20px', color: 'var(--text-muted)' }}>
                        Đang tải nhật ký chăm sóc…
                      </p>
                    ) : notes.length === 0 ? (
                      <div style={{ textAlign: 'center', padding: '30px', color: 'var(--text-muted)', fontSize: '13px', background: 'var(--bg-surface-subtle)', borderRadius: '8px' }}>
                        Chưa có ghi chú chăm sóc nào cho khách hàng này.
                      </div>
                    ) : (
                      <div className="crm-note-timeline">
                        {notes.map((note) => {
                          const typeMeta = NOTE_TYPE_MAP[note.noteType] || { label: note.noteType, variant: 'default' };

                          return (
                            <div key={note.id} className="crm-note-item">
                              <div className="crm-note-header">
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <Badge variant={typeMeta.variant}>
                                    {typeMeta.label}
                                  </Badge>
                                  <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                                    {formatDateTime(note.createdAt)}
                                  </span>
                                  {note.createdBy && (
                                    <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                                      · Bởi: <strong>{note.createdBy}</strong>
                                    </span>
                                  )}
                                </div>

                                <button
                                  type="button"
                                  onClick={() => deleteNote(note.id)}
                                  style={{
                                    background: 'none',
                                    border: 'none',
                                    cursor: 'pointer',
                                    color: 'var(--color-danger-700, #b53d59)',
                                    padding: '4px',
                                  }}
                                  title="Xóa ghi chú này"
                                >
                                  <Trash2 size={14} />
                                </button>
                              </div>

                              <div className="crm-note-content">
                                {note.content}
                              </div>

                              {(note.skinProfile || note.allergies || note.contraindications || note.followUpAt) && (
                                <div className="crm-note-clinical-chips">
                                  {note.followUpAt && (
                                    <span className="crm-clinical-chip" style={{ color: 'var(--color-accent-700)', borderColor: 'var(--color-accent-200)' }}>
                                      <Clock size={11} style={{ display: 'inline', marginRight: 3 }} />
                                      Hẹn chăm sóc: <strong>{formatDateTime(note.followUpAt)}</strong>
                                    </span>
                                  )}
                                  {note.skinProfile && (
                                    <span className="crm-clinical-chip">
                                      Da: <strong>{note.skinProfile}</strong>
                                    </span>
                                  )}
                                  {note.allergies && (
                                    <span className="crm-clinical-chip danger">
                                      Dị ứng: <strong>{note.allergies}</strong>
                                    </span>
                                  )}
                                  {note.contraindications && (
                                    <span className="crm-clinical-chip danger">
                                      Chống chỉ định: <strong>{note.contraindications}</strong>
                                    </span>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* TAB 2: CLINICAL & SAFETY PROFILE */}
              {modalTab === 'clinical' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <div style={{
                    background: '#FFFDF9',
                    border: '1px solid #FDE68A',
                    borderRadius: '10px',
                    padding: '16px 20px',
                  }}>
                    <h4 style={{ margin: '0 0 6px 0', fontSize: '13.5px', color: '#926C2A', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <ShieldAlert size={16} />
                      Cảnh báo Lâm sàng & An toàn Liệu trình
                    </h4>
                    <p style={{ margin: 0, fontSize: '12px', color: '#785A24', lineHeight: 1.5 }}>
                      Thông tin được tổng hợp từ khảo sát tiền liệu trình Spa và ghi nhận của chuyên viên để đảm bảo an toàn tuyệt đối khi phục vụ khách.
                    </p>
                  </div>

                  <div style={{ display: 'grid', gap: '12px' }}>
                    <div style={{ background: 'var(--bg-surface-subtle)', padding: '14px 18px', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                      <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                        Tình trạng da ghi nhận gần nhất
                      </div>
                      <div style={{ fontSize: '13px', color: 'var(--text-main)', marginTop: '4px', fontWeight: 550 }}>
                        {notes.find((n) => n.skinProfile)?.skinProfile || 'Chưa có ghi nhận tình trạng da cụ thể.'}
                      </div>
                    </div>

                    <div style={{ background: 'var(--bg-surface-subtle)', padding: '14px 18px', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                      <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--color-danger-700)', textTransform: 'uppercase' }}>
                        Dị ứng mỹ phẩm & thành phần đã biết
                      </div>
                      <div style={{ fontSize: '13px', color: 'var(--text-main)', marginTop: '4px', fontWeight: 550 }}>
                        {notes.find((n) => n.allergies)?.allergies || 'Không ghi nhận tiền sử dị ứng.'}
                      </div>
                    </div>

                    <div style={{ background: 'var(--bg-surface-subtle)', padding: '14px 18px', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                      <div style={{ fontSize: '11px', fontWeight: 700, color: '#D97706', textTransform: 'uppercase' }}>
                        Lưu ý & Chống chỉ định y tế
                      </div>
                      <div style={{ fontSize: '13px', color: 'var(--text-main)', marginTop: '4px', fontWeight: 550 }}>
                        {notes.find((n) => n.contraindications)?.contraindications || 'Không có chống chỉ định đặc biệt.'}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
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
