import React, { useEffect, useState } from 'react';
import {
  Clock,
  Calendar,
  CalendarDays,
  CalendarOff,
  ShieldCheck,
  AlertCircle,
  CheckCircle2,
  RefreshCw,
  Trash2,
  Save,
  Sparkles,
  Info,
  Coins,
  ShieldAlert,
  Copy,
  Ban,
  Check
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { Button } from '../../shared/ui/Button';
import { useAuth } from '../../app/providers/AuthProvider';
import { formatCurrency, formatDate, getVietnamDateString } from '../../shared/utils/formatters';
import './spa-policy.css';

const unwrap = (res) => res?.data ?? res;

export const SpaOperatingPolicy = () => {
  const { isAdmin } = useAuth();
  const [activeTab, setActiveTab] = useState('weekly'); // 'weekly' | 'exceptions' | 'finance'

  // Weekly hours state
  const [hours, setHours] = useState([]);

  // Financial policy state
  const [policy, setPolicy] = useState(null);

  // Exceptions state
  const [exceptions, setExceptions] = useState([]);
  const [loadingExceptions, setLoadingExceptions] = useState(false);
  const [excDate, setExcDate] = useState('');
  const [excClosed, setExcClosed] = useState(true);
  const [excOpens, setExcOpens] = useState('08:00');
  const [excCloses, setExcCloses] = useState('20:00');
  const [excReason, setExcReason] = useState('');

  // UI state
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState({ type: '', text: '' });

  const loadData = async () => {
    setBusy(true);
    setFeedback({ type: '', text: '' });
    try {
      const todayStr = getVietnamDateString();
      const in180Days = new Date(Date.now() + 180 * 24 * 60 * 60 * 1000);
      const toStr = getVietnamDateString(in180Days);

      const [hRes, pRes, excRes] = await Promise.allSettled([
        apiClient.get('/api/v1/admin/spa/calendar/weekly'),
        apiClient.get('/api/v1/spa/financial-policy'),
        apiClient.get(`/api/v1/admin/spa/calendar/exceptions?from=${todayStr}&to=${toStr}`)
      ]);

      if (hRes.status === 'fulfilled') {
        const rawHours = unwrap(hRes.value);
        setHours(Array.isArray(rawHours) ? rawHours : []);
      }
      if (pRes.status === 'fulfilled') {
        setPolicy(unwrap(pRes.value));
      }
      if (excRes.status === 'fulfilled') {
        const rawExc = unwrap(excRes.value);
        setExceptions(Array.isArray(rawExc) ? rawExc : []);
      }
    } catch (error) {
      setFeedback({ type: 'error', text: error?.message || 'Không thể tải dữ liệu chính sách spa.' });
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const calculateDuration = (opensAt, closesAt, closed) => {
    if (closed) return 'Nghỉ cả ngày';
    if (!opensAt || !closesAt) return '--';
    const [oH, oM] = opensAt.slice(0, 5).split(':').map(Number);
    const [cH, cM] = closesAt.slice(0, 5).split(':').map(Number);
    const totalMinutes = (cH * 60 + cM) - (oH * 60 + oM);
    if (totalMinutes <= 0) return 'Giờ không hợp lệ';
    const h = Math.floor(totalMinutes / 60);
    const m = totalMinutes % 60;
    return `${h}h${m > 0 ? m + 'p' : ''} phục vụ`;
  };

  // Quick actions for weekly hours
  const applyPresetHours = (start, end) => {
    setHours((prev) =>
      prev.map((item) => ({
        ...item,
        closed: false,
        opensAt: start,
        closesAt: end
      }))
    );
    setFeedback({
      type: 'success',
      text: `Đã điền nhanh khung giờ ${start} - ${end} cho toàn bộ tuần. Nhấn 'Lưu giờ hoạt động' để áp dụng.`
    });
  };

  const copyMondayToWeekdays = () => {
    const monday = hours.find((h) => h.dayOfWeek === 1);
    if (!monday) return;
    setHours((prev) =>
      prev.map((item) =>
        item.dayOfWeek >= 1 && item.dayOfWeek <= 5
          ? { ...item, closed: monday.closed, opensAt: monday.opensAt, closesAt: monday.closesAt }
          : item
      )
    );
    setFeedback({
      type: 'success',
      text: 'Đã sao chép khung giờ Thứ Hai cho các ngày trong tuần (T2 - T6).'
    });
  };

  // Action: Save Weekly Hours
  const handleSaveWeeklyHours = async () => {
    if (busy || !isAdmin) return;
    setBusy(true);
    setFeedback({ type: '', text: '' });
    try {
      const res = await apiClient.put('/api/v1/admin/spa/calendar/weekly', hours);
      const updated = unwrap(res);
      setHours(Array.isArray(updated) ? updated : hours);
      setFeedback({
        type: 'success',
        text: 'Đã lưu thành công khung giờ hoạt động cố định theo tuần.'
      });
    } catch (error) {
      setFeedback({
        type: 'error',
        text: error?.response?.data?.message || error?.message || 'Không thể lưu giờ hoạt động.'
      });
    } finally {
      setBusy(false);
    }
  };

  // Action: Save Exception
  const handleSaveException = async () => {
    if (busy || !isAdmin || !excDate || !excReason.trim()) return;
    setBusy(true);
    setFeedback({ type: '', text: '' });
    try {
      const payload = {
        closed: excClosed,
        opensAt: excOpens.length === 5 ? `${excOpens}:00` : excOpens,
        closesAt: excCloses.length === 5 ? `${excCloses}:00` : excCloses,
        reason: excReason.trim()
      };
      await apiClient.put(`/api/v1/admin/spa/calendar/exceptions/${excDate}`, payload);
      setExcReason('');
      setExcDate('');
      await loadData();
      setFeedback({
        type: 'success',
        text: `Đã thiết lập ngày ngoại lệ/nghỉ lễ (${excDate}) thành công.`
      });
    } catch (error) {
      setFeedback({
        type: 'error',
        text: error?.response?.data?.message || error?.message || 'Không thể lưu ngày ngoại lệ.'
      });
    } finally {
      setBusy(false);
    }
  };

  // Action: Delete Exception
  const handleDeleteException = async (dateStr) => {
    if (busy || !isAdmin) return;
    if (!window.confirm(`Khôi phục ngày ${formatDate(dateStr)} về giờ hoạt động tiêu chuẩn trong tuần?`)) return;
    setBusy(true);
    setFeedback({ type: '', text: '' });
    try {
      await apiClient.delete(`/api/v1/admin/spa/calendar/exceptions/${dateStr}`);
      await loadData();
      setFeedback({
        type: 'success',
        text: `Đã xóa ngày ngoại lệ ${formatDate(dateStr)}, áp dụng lại giờ tuần chuẩn.`
      });
    } catch (error) {
      setFeedback({
        type: 'error',
        text: error?.response?.data?.message || error?.message || 'Không thể xóa ngày ngoại lệ.'
      });
    } finally {
      setBusy(false);
    }
  };

  // Action: Save Financial Policy
  const handleSaveFinancialPolicy = async () => {
    if (busy || !isAdmin || !policy) return;
    setBusy(true);
    setFeedback({ type: '', text: '' });
    try {
      const res = await apiClient.put('/api/v1/admin/spa/financial-policy', policy);
      const updated = unwrap(res);
      setPolicy(updated);
      setFeedback({
        type: 'success',
        text: `Đã nâng cấp chính sách tài chính lên phiên bản v${updated.version || (policy.version + 1)} thành công.`
      });
    } catch (error) {
      setFeedback({
        type: 'error',
        text: error?.response?.data?.message || error?.message || 'Không thể lưu chính sách cọc & phí.'
      });
    } finally {
      setBusy(false);
    }
  };

  const getDayName = (dayOfWeek) => {
    switch (dayOfWeek) {
      case 1: return 'Thứ Hai';
      case 2: return 'Thứ Ba';
      case 3: return 'Thứ Tư';
      case 4: return 'Thứ Năm';
      case 5: return 'Thứ Sáu';
      case 6: return 'Thứ Bảy';
      case 7: return 'Chủ Nhật';
      default: return `Thứ ${dayOfWeek + 1}`;
    }
  };

  const openDaysCount = hours.filter((h) => !h.closed).length;
  const standardTimeSample = hours.find((h) => !h.closed);

  return (
    <div className="spa-policy-container">
      {/* Header */}
      <div className="spa-policy-header">
        <div>
          <h2>
            <Clock className="text-primary" size={22} />
            Chính sách Vận hành & Lịch làm việc Spa
          </h2>
          <p>
            Cấu hình thời gian phục vụ, quản lý ngày nghỉ lễ đột xuất và quy định hạn mức tiền cọc / phí hủy lịch.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span className={`spa-policy-header-badge ${isAdmin ? 'admin' : 'staff'}`}>
            {isAdmin ? <ShieldCheck size={14} /> : <Info size={14} />}
            {isAdmin ? 'Quản trị viên (Toàn quyền)' : 'Nhân viên (Chỉ xem)'}
          </span>
          <Button
            size="sm"
            variant="outline"
            disabled={busy}
            onClick={loadData}
            title="Tải lại dữ liệu mới nhất"
          >
            <RefreshCw size={14} className={busy ? 'spin' : ''} />
            Làm mới
          </Button>
        </div>
      </div>

      {/* KPI Stats Overview */}
      <div className="spa-policy-stats">
        <div className="spa-policy-stat-card">
          <div className="spa-policy-stat-icon rose">
            <Clock size={22} />
          </div>
          <div className="spa-policy-stat-info">
            <h4>Lịch tuần mở cửa</h4>
            <strong>{openDaysCount} / 7 ngày</strong>
            <small>
              {standardTimeSample
                ? `Giờ chuẩn: ${standardTimeSample.opensAt?.slice(0, 5)} - ${standardTimeSample.closesAt?.slice(0, 5)}`
                : 'Đang đóng cửa toàn bộ'}
            </small>
          </div>
        </div>

        <div className="spa-policy-stat-card">
          <div className="spa-policy-stat-icon gold">
            <CalendarOff size={22} />
          </div>
          <div className="spa-policy-stat-info">
            <h4>Ngày nghỉ & Ngoại lệ</h4>
            <strong>{exceptions.length} ngày</strong>
            <small>Đã thiết lập trong 6 tháng tới</small>
          </div>
        </div>

        <div className="spa-policy-stat-card">
          <div className="spa-policy-stat-icon green">
            <Coins size={22} />
          </div>
          <div className="spa-policy-stat-info">
            <h4>Chính sách cọc hiện hành</h4>
            <strong>
              {policy?.depositAmount > 0
                ? formatCurrency(policy.depositAmount)
                : 'Miễn đặt cọc'}
            </strong>
            <small>Phiên bản quy chế: v{policy?.version || 1}</small>
          </div>
        </div>
      </div>

      {/* Feedback Banner */}
      {feedback.text && (
        <div className={`spa-policy-alert ${feedback.type}`}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {feedback.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
            <span>{feedback.text}</span>
          </div>
          <button
            type="button"
            onClick={() => setFeedback({ type: '', text: '' })}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', fontWeight: 700 }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Sub Tabs */}
      <div className="spa-policy-tabs">
        <button
          type="button"
          className={`spa-policy-tab-btn ${activeTab === 'weekly' ? 'active' : ''}`}
          onClick={() => setActiveTab('weekly')}
        >
          <CalendarDays size={16} />
          Giờ hoạt động theo tuần ({openDaysCount}/7 ngày mở)
        </button>
        <button
          type="button"
          className={`spa-policy-tab-btn ${activeTab === 'exceptions' ? 'active' : ''}`}
          onClick={() => setActiveTab('exceptions')}
        >
          <CalendarOff size={16} />
          Ngày nghỉ Lễ & Ngoại lệ ({exceptions.length})
        </button>
        <button
          type="button"
          className={`spa-policy-tab-btn ${activeTab === 'finance' ? 'active' : ''}`}
          onClick={() => setActiveTab('finance')}
        >
          <ShieldCheck size={16} />
          Chính sách cọc & Phí (v{policy?.version || 1})
        </button>
      </div>

      {/* TAB 1: WEEKLY HOURS */}
      {activeTab === 'weekly' && (
        <div className="spa-policy-card">
          <div className="spa-policy-card-header">
            <div className="spa-policy-card-title">
              <h3>
                <Clock size={18} color="var(--color-accent-600, #7457e8)" />
                Khung giờ phục vụ cố định Thứ Hai - Chủ Nhật
              </h3>
              <p>
                Khách hàng chỉ có thể đặt lịch hẹn và nhân viên chỉ được nhận ca trong khoảng thời gian mở cửa này.
              </p>
            </div>

            {isAdmin && (
              <div className="spa-policy-quick-toolbar">
                <span>Thiết lập nhanh:</span>
                <button
                  type="button"
                  className="spa-policy-quick-btn"
                  onClick={() => applyPresetHours('08:00', '20:00')}
                >
                  08:00 - 20:00
                </button>
                <button
                  type="button"
                  className="spa-policy-quick-btn"
                  onClick={() => applyPresetHours('09:00', '21:00')}
                >
                  09:00 - 21:00
                </button>
                <button
                  type="button"
                  className="spa-policy-quick-btn"
                  onClick={copyMondayToWeekdays}
                >
                  <Copy size={12} style={{ display: 'inline', marginRight: 4 }} />
                  Chép T2 sang T3-T6
                </button>
              </div>
            )}
          </div>

          <fieldset disabled={!isAdmin || busy} style={{ border: 0, padding: 0, margin: 0 }}>
            <div className="spa-policy-schedule-list">
              {hours.map((item, idx) => {
                const dayName = getDayName(item.dayOfWeek);
                const isSunday = item.dayOfWeek === 7;
                const durationLabel = calculateDuration(item.opensAt, item.closesAt, item.closed);

                return (
                  <div
                    key={item.dayOfWeek}
                    className={`spa-policy-schedule-row ${item.closed ? 'is-closed' : ''}`}
                  >
                    <div className={`spa-policy-day-badge ${isSunday ? 'sunday' : ''}`}>
                      <div className="dot" />
                      <span>{dayName}</span>
                    </div>

                    <label className="spa-policy-toggle-label">
                      <input
                        type="checkbox"
                        checked={item.closed}
                        onChange={(e) => {
                          const isChecked = e.target.checked;
                          setHours((rows) =>
                            rows.map((r, n) => (n === idx ? { ...r, closed: isChecked } : r))
                          );
                        }}
                      />
                      <span>Nghỉ đóng cửa</span>
                    </label>

                    <div className="spa-policy-time-group">
                      <div className="spa-policy-time-input-wrap">
                        <span style={{ color: '#85757D', fontSize: 11 }}>Mở:</span>
                        <input
                          type="time"
                          disabled={item.closed}
                          value={item.opensAt?.slice(0, 5) || '08:00'}
                          onChange={(e) => {
                            const val = e.target.value;
                            setHours((rows) =>
                              rows.map((r, n) => (n === idx ? { ...r, opensAt: val } : r))
                            );
                          }}
                        />
                      </div>
                      <span style={{ color: '#9CA3AF' }}>—</span>
                      <div className="spa-policy-time-input-wrap">
                        <span style={{ color: '#85757D', fontSize: 11 }}>Đóng:</span>
                        <input
                          type="time"
                          disabled={item.closed}
                          value={item.closesAt?.slice(0, 5) || '20:00'}
                          onChange={(e) => {
                            const val = e.target.value;
                            setHours((rows) =>
                              rows.map((r, n) => (n === idx ? { ...r, closesAt: val } : r))
                            );
                          }}
                        />
                      </div>
                    </div>

                    <div className="spa-policy-duration-text">
                      {durationLabel}
                    </div>
                  </div>
                );
              })}
            </div>

            {isAdmin && (
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
                <Button
                  variant="primary"
                  disabled={busy}
                  onClick={handleSaveWeeklyHours}
                >
                  <Save size={16} />
                  {busy ? 'Đang lưu giờ tuần...' : 'Lưu giờ hoạt động tuần'}
                </Button>
              </div>
            )}
          </fieldset>
        </div>
      )}

      {/* TAB 2: EXCEPTIONS & HOLIDAYS */}
      {activeTab === 'exceptions' && (
        <div className="spa-policy-card">
          <div className="spa-policy-card-header">
            <div className="spa-policy-card-title">
              <h3>
                <CalendarOff size={18} color="#D97706" />
                Quản lý Ngày nghỉ Lễ & Lịch hoạt động ngoại lệ
              </h3>
              <p>
                Đặt lịch đóng cửa nghỉ lễ (Tết, Quốc khánh) hoặc mở cửa khung giờ đặc biệt phục vụ sự kiện.
              </p>
            </div>
          </div>

          <div className="spa-policy-exceptions-grid">
            {/* Form tạo mới ngoại lệ */}
            <fieldset disabled={!isAdmin || busy} style={{ border: 0, padding: 0, margin: 0 }}>
              <div className="spa-policy-exception-form">
                <h4>
                  <Sparkles size={16} color="#A97C35" style={{ display: 'inline', marginRight: 6 }} />
                  Thêm hoặc cập nhật ngày ngoại lệ
                </h4>

                <div className="spa-policy-field">
                  <label>Chọn ngày áp dụng *</label>
                  <input
                    type="date"
                    min={getVietnamDateString()}
                    value={excDate}
                    onChange={(e) => setExcDate(e.target.value)}
                  />
                </div>

                <div className="spa-policy-field">
                  <label className="spa-policy-toggle-label" style={{ fontWeight: 600 }}>
                    <input
                      type="checkbox"
                      checked={excClosed}
                      onChange={(e) => setExcClosed(e.target.checked)}
                    />
                    <span>Nghỉ cả ngày (Đóng cửa hoàn toàn)</span>
                  </label>
                </div>

                {!excClosed && (
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                    <div className="spa-policy-field">
                      <label>Giờ mở cửa đặc biệt</label>
                      <input
                        type="time"
                        value={excOpens}
                        onChange={(e) => setExcOpens(e.target.value)}
                      />
                    </div>
                    <div className="spa-policy-field">
                      <label>Giờ đóng cửa đặc biệt</label>
                      <input
                        type="time"
                        value={excCloses}
                        onChange={(e) => setExcCloses(e.target.value)}
                      />
                    </div>
                  </div>
                )}

                <div className="spa-policy-field">
                  <label>Lý do ngoại lệ * (Tối đa 500 ký tự)</label>
                  <textarea
                    rows={2}
                    maxLength={500}
                    placeholder="Ví dụ: Nghỉ Tết Nguyên Đán, Bảo trì định kỳ máy móc..."
                    value={excReason}
                    onChange={(e) => setExcReason(e.target.value)}
                  />
                </div>

                <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
                  <Button
                    variant="primary"
                    disabled={!excDate || !excReason.trim() || busy || !isAdmin}
                    onClick={handleSaveException}
                    style={{ flex: 1 }}
                  >
                    <Save size={15} />
                    {busy ? 'Đang lưu...' : 'Lưu ngày ngoại lệ'}
                  </Button>
                </div>
              </div>
            </fieldset>

            {/* Danh sách ngày ngoại lệ */}
            <div>
              <h4 style={{ fontSize: 14, fontWeight: 700, margin: '0 0 12px 0', color: '#352D31' }}>
                Danh sách ngoại lệ đã thiết lập ({exceptions.length} ngày)
              </h4>

              {exceptions.length === 0 ? (
                <div style={{
                  padding: 30,
                  textAlign: 'center',
                  background: '#FAF7F8',
                  borderRadius: 10,
                  border: '1px dashed #E2D3DB',
                  color: '#85757D',
                  fontSize: 13
                }}>
                  <Calendar size={32} style={{ margin: '0 auto 8px', opacity: 0.5, display: 'block' }} />
                  <p style={{ margin: 0 }}>Chưa có ngày nghỉ lễ hoặc lịch ngoại lệ nào trong 6 tháng tới.</p>
                </div>
              ) : (
                <div className="spa-policy-exception-list">
                  {exceptions.map((exc) => (
                    <div key={exc.date} className="spa-policy-exception-item">
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span className="spa-policy-exception-date">{formatDate(exc.date)}</span>
                          {exc.closed ? (
                            <span className="spa-policy-badge-closed">
                              <Ban size={12} /> Nghỉ cả ngày
                            </span>
                          ) : (
                            <span className="spa-policy-badge-special">
                              <Clock size={12} /> Mở {exc.opensAt?.slice(0, 5)} - {exc.closesAt?.slice(0, 5)}
                            </span>
                          )}
                        </div>
                        <div className="spa-policy-exception-reason">
                          Lý do: <strong>{exc.reason || 'Không có ghi chú'}</strong>
                        </div>
                      </div>

                      {isAdmin && (
                        <button
                          type="button"
                          className="btn btn-outline"
                          style={{ padding: '6px 10px', fontSize: 12, color: '#DC2626', borderColor: '#FECACA' }}
                          title="Xóa ngoại lệ & dùng lại giờ tuần"
                          disabled={busy}
                          onClick={() => handleDeleteException(exc.date)}
                        >
                          <Trash2 size={14} /> Xóa
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: FINANCIAL POLICY */}
      {activeTab === 'finance' && policy && (
        <div className="spa-policy-card">
          <div className="spa-policy-card-header">
            <div className="spa-policy-card-title">
              <h3>
                <Coins size={18} color="#059669" />
                Chính sách Tiền cọc & Phí xử lý lịch hẹn
              </h3>
              <p>
                Quy định mức tiền cọc tối đa khi khách đặt dịch vụ lẻ, phí hủy sát giờ và phí vắng mặt (No-Show).
              </p>
            </div>
            <span style={{ fontSize: 12, fontWeight: 700, background: '#ECFDF5', color: '#065F46', padding: '4px 10px', borderRadius: 6, border: '1px solid #A7F3D0' }}>
              Quy chế: Phiên bản v{policy.version}
            </span>
          </div>

          <fieldset disabled={!isAdmin || busy} style={{ border: 0, padding: 0, margin: 0 }}>
            <div className="spa-policy-finance-grid">
              {/* Card 1: Deposit */}
              <div className="spa-policy-finance-card">
                <h4>
                  <Coins size={16} color="var(--color-accent-600, #7457e8)" />
                  Tiền cọc tối đa mỗi lịch
                </h4>
                <p>
                  Khoản tiền cọc yêu cầu thanh toán trước qua SePay hoặc tại quầy khi khách đặt lịch dịch vụ lẻ.
                </p>
                <div className="spa-policy-finance-input-wrap">
                  <input
                    type="number"
                    min="0"
                    max="99999999"
                    step="5000"
                    value={policy.depositAmount}
                    onChange={(e) => setPolicy({ ...policy, depositAmount: Number(e.target.value) })}
                  />
                  <span className="spa-policy-currency-suffix">VND</span>
                </div>
                <div className="spa-policy-preview-amount">
                  Hiển thị: {formatCurrency(policy.depositAmount)}
                </div>
                <div className="spa-policy-presets">
                  <span style={{ fontSize: 10, color: '#85757D', alignSelf: 'center' }}>Gợi ý:</span>
                  {[0, 50000, 100000, 200000].map((val) => (
                    <button
                      key={val}
                      type="button"
                      className="spa-policy-preset-pill"
                      onClick={() => setPolicy({ ...policy, depositAmount: val })}
                    >
                      {val === 0 ? 'Miễn cọc' : formatCurrency(val)}
                    </button>
                  ))}
                </div>
              </div>

              {/* Card 2: Cancellation Fee */}
              <div className="spa-policy-finance-card">
                <h4>
                  <ShieldAlert size={16} color="#D97706" />
                  Phí hủy lịch sau hạn
                </h4>
                <p>
                  Mức phí phạt khấu trừ khi khách yêu cầu hủy lịch sát giờ hẹn hoặc vượt quá thời gian cho phép.
                </p>
                <div className="spa-policy-finance-input-wrap">
                  <input
                    type="number"
                    min="0"
                    max="99999999"
                    step="5000"
                    value={policy.cancellationFee}
                    onChange={(e) => setPolicy({ ...policy, cancellationFee: Number(e.target.value) })}
                  />
                  <span className="spa-policy-currency-suffix">VND</span>
                </div>
                <div className="spa-policy-preview-amount">
                  Hiển thị: {formatCurrency(policy.cancellationFee)}
                </div>
                <div className="spa-policy-presets">
                  <span style={{ fontSize: 10, color: '#85757D', alignSelf: 'center' }}>Gợi ý:</span>
                  {[0, 30000, 50000, 100000].map((val) => (
                    <button
                      key={val}
                      type="button"
                      className="spa-policy-preset-pill"
                      onClick={() => setPolicy({ ...policy, cancellationFee: val })}
                    >
                      {val === 0 ? '0 đ' : formatCurrency(val)}
                    </button>
                  ))}
                </div>
              </div>

              {/* Card 3: No-Show Fee */}
              <div className="spa-policy-finance-card">
                <h4>
                  <Ban size={16} color="#DC2626" />
                  Phí vắng hẹn không báo trước
                </h4>
                <p>
                  Khoản phí áp dụng khi khách không đến đúng giờ hẹn và không thực hiện thao tác hủy/đổi lịch.
                </p>
                <div className="spa-policy-finance-input-wrap">
                  <input
                    type="number"
                    min="0"
                    max="99999999"
                    step="5000"
                    value={policy.noShowFee}
                    onChange={(e) => setPolicy({ ...policy, noShowFee: Number(e.target.value) })}
                  />
                  <span className="spa-policy-currency-suffix">VND</span>
                </div>
                <div className="spa-policy-preview-amount">
                  Hiển thị: {formatCurrency(policy.noShowFee)}
                </div>
                <div className="spa-policy-presets">
                  <span style={{ fontSize: 10, color: '#85757D', alignSelf: 'center' }}>Gợi ý:</span>
                  {[0, 50000, 100000, 150000].map((val) => (
                    <button
                      key={val}
                      type="button"
                      className="spa-policy-preset-pill"
                      onClick={() => setPolicy({ ...policy, noShowFee: val })}
                    >
                      {val === 0 ? '0 đ' : formatCurrency(val)}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Quy chế tóm tắt */}
            <div className="spa-policy-summary-box" style={{ marginTop: 20 }}>
              <h4>
                <Info size={16} />
                Lưu ý quan trọng về chính sách tài chính Spa:
              </h4>
              <ul>
                <li>Giá trị <strong>0 đ</strong> đồng nghĩa với việc không thu khoản cọc hoặc không áp dụng phí phạt tương ứng.</li>
                <li>Khách đặt lịch bằng <strong>Vé liệu trình Spa điện tử</strong> hoặc gói đã mua trước sẽ được tự động miễn thu tiền cọc.</li>
                <li>Toàn bộ tiền cọc đã thu sẽ được <strong>tự động cấn trừ vào tổng hóa đơn</strong> thanh toán khi khách hoàn tất dịch vụ tại quầy lễ tân.</li>
                <li>Khi bạn lưu thay đổi, hệ thống sẽ tự động tạo một phiên bản quy chế mới (v{policy.version + 1}). Các lịch hẹn đã đặt trước đó vẫn giữ nguyên phiên bản đã cam kết tại thời điểm khách đặt.</li>
              </ul>
            </div>

            {isAdmin && (
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
                <Button
                  variant="primary"
                  disabled={busy}
                  onClick={handleSaveFinancialPolicy}
                >
                  <Save size={16} />
                  {busy ? 'Đang lưu chính sách...' : 'Lưu chính sách tài chính'}
                </Button>
              </div>
            )}
          </fieldset>
        </div>
      )}
    </div>
  );
};
