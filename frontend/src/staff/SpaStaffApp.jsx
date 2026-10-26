import React, { useState, useEffect, useMemo } from 'react';
import './styles/staff-portal.css';
import {
  Calendar,
  Clock,
  User,
  UserCheck,
  Sparkles,
  Phone,
  Search,
  CheckCircle2,
  AlertCircle,
  Plus,
  RefreshCw,
  QrCode,
  DollarSign,
  Receipt,
  Ticket,
  Check,
  X,
  Building2,
  LogOut,
  SlidersHorizontal,
  Sun,
  Moon,
  CreditCard,
  Copy,
  Printer,
  ChevronRight,
  ShieldCheck,
  Award,
  Stethoscope
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { formatCurrency, formatDate, getVietnamDateString } from '../shared/utils/formatters';
import { useAuth } from '../app/providers/AuthProvider';
import { SpaVisitCheckout } from '../shared/ui/SpaVisitCheckout';
import { TreatmentWorkspace } from '../features/treatments/TreatmentWorkspace';
import { TreatmentAppointmentDialog } from '../features/treatments/TreatmentAppointmentDialog';
import { WalkInDrawer } from '../pages/spa-reception/WalkInDrawer';

export const SpaStaffApp = ({ onSwitchToAdmin }) => {
  const { user, logout, isAdmin, isSpaReception } = useAuth();
  const [activeTab, setActiveTab] = useState(() => window.location.hash.includes('staff/treatments') ? 'treatments' : 'queue');
  useEffect(() => {
    const syncTreatmentRoute = () => { if (window.location.hash.includes('staff/treatments')) setActiveTab('treatments'); };
    window.addEventListener('hashchange', syncTreatmentRoute);
    return () => window.removeEventListener('hashchange', syncTreatmentRoute);
  }, []);
  const [currentTime, setCurrentTime] = useState(new Date());

  const [appointments, setAppointments] = useState([]);
  const [workspaceId, setWorkspaceId] = useState(null);
  const [services, setServices] = useState([]);
  const [staffList, setStaffList] = useState([]);
  const [facilitiesList, setFacilitiesList] = useState([]);
  const [loading, setLoading] = useState(false);

  const [dateFilter, setDateFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [roomStatusFilter, setRoomStatusFilter] = useState('ALL');

  const [selectedAptForAssign, setSelectedAptForAssign] = useState(null);
  const [selectedStaffId, setSelectedStaffId] = useState('');
  const [assignSubmitting, setAssignSubmitting] = useState(false);

  const [posSelectedApt, setPosSelectedApt] = useState(null);

  const [ticketSearch, setTicketSearch] = useState('');
  const [ticketsData, setTicketsData] = useState([]);
  const [loadingTickets, setLoadingTickets] = useState(false);

  const [rescheduleApt, setRescheduleApt] = useState(null);
  const [rescheduleDate, setRescheduleDate] = useState('');
  const [rescheduleTime, setRescheduleTime] = useState('09:00:00');
  const [rescheduleNote, setRescheduleNote] = useState('');
  const [rescheduleSubmitting, setRescheduleSubmitting] = useState(false);
  const [rescheduleSlots,setRescheduleSlots]=useState([]),[rescheduleSlotsLoading,setRescheduleSlotsLoading]=useState(false);
  useEffect(()=>{
    if(!rescheduleApt||!rescheduleDate)return;
    const controller=new AbortController();setRescheduleSlots([]);setRescheduleSlotsLoading(true);
    apiClient.get(`/api/v1/appointments/${rescheduleApt.id}/available-slots?date=${rescheduleDate}`,{signal:controller.signal}).then(response=>{
      if(controller.signal.aborted)return;const body=response?.data??response;const slots=(Array.isArray(body)?body:[]).map(s=>String(s).slice(0,5));setRescheduleSlots(slots);
    }).catch(error=>{if(!controller.signal.aborted)alert(error.message);}).finally(()=>{if(!controller.signal.aborted)setRescheduleSlotsLoading(false);});
    return()=>controller.abort();
  },[rescheduleApt?.id,rescheduleDate]);

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    fetchAppointments();
    fetchMasterData();
  }, [dateFilter, statusFilter]);

  const fetchAppointments = async () => {
    setLoading(true);
    try {
      let url = `${ENDPOINTS.SPA.APPOINTMENTS}?size=100`;
      if (dateFilter && dateFilter !== 'ALL') {
        url += `&date=${encodeURIComponent(dateFilter)}`;
      }
      if (statusFilter && statusFilter !== 'ALL') {
        url += `&status=${encodeURIComponent(statusFilter)}`;
      }
      const res = await apiClient.get(url);
      const data = res.data || res;
      setAppointments(data?.content || (Array.isArray(data) ? data : []));
    } catch (e) {
      console.warn('Failed to load appointments for reception portal', e);
    } finally {
      setLoading(false);
    }
  };

  const fetchMasterData = async () => {
    try {
      const [srvRes, stfRes, facRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.SPA.PUBLIC_SERVICES || '/api/v1/spa/services'),
        apiClient.get(isSpaReception ? (ENDPOINTS.SPA.STAFF || '/api/v1/admin/staff') : '/api/v1/spa/services/staff'),
        isSpaReception ? apiClient.get(ENDPOINTS.SPA.FACILITIES || '/api/v1/admin/spa/facilities') : Promise.resolve([])
      ]);

      if (srvRes.status === 'fulfilled') {
        const sList = srvRes.value?.data || srvRes.value || [];
        setServices(Array.isArray(sList) ? sList : []);
      }

      let loadedStaff = [];
      if (stfRes.status === 'fulfilled') {
        const stList = stfRes.value?.data || stfRes.value || [];
        loadedStaff = Array.isArray(stList) ? stList : (stList?.content || []);
      }
      if (!loadedStaff || loadedStaff.length === 0) {
        try {
          const publicStaffRes = await apiClient.get(ENDPOINTS.SPA.PUBLIC_STAFF || '/api/v1/spa/services/staff');
          const pData = publicStaffRes?.data || publicStaffRes || [];
          loadedStaff = Array.isArray(pData) ? pData : (pData?.content || []);
        } catch (_) {}
      }
      setStaffList(loadedStaff || []);

      if (facRes.status === 'fulfilled') {
        const fList = facRes.value?.data || facRes.value || [];
        setFacilitiesList(Array.isArray(fList) ? fList : (fList?.content || []));
      }
    } catch (err) {
      console.warn('Lỗi tải dữ liệu cơ sở vật chất và nhân sự từ database', err);
    }
  };

  const filteredAppointments = useMemo(() => {
    let list = [...appointments];
    if (searchTerm.trim()) {
      const lower = searchTerm.trim().toLowerCase();
      list = list.filter((a) =>
        (a.customerName || '').toLowerCase().includes(lower) ||
        (a.customerPhone || '').includes(lower) ||
        String(a.id).includes(lower) ||
        (a.items || []).some((i) => (i.serviceName || '').toLowerCase().includes(lower))
      );
    }
    return list;
  }, [appointments, searchTerm]);

  const todayKPIs = useMemo(() => {
    const todayStr = getVietnamDateString();
    const todayList = appointments.filter(a => !dateFilter || dateFilter === 'ALL' || a.appointmentDate === todayStr);
    return {
      total: todayList.length,
      waiting: todayList.filter(a => (a.status === 'PENDING' || a.status === 'CONFIRMED') && !a.checkedInAt).length,
      inClinic: todayList.filter(a => a.status === 'IN_PROGRESS' || a.checkedInAt).length,
      unpaidCompleted: todayList.filter(a => a.status === 'COMPLETED' && !a.orderId).length,
      completed: todayList.filter(a => a.status === 'COMPLETED').length
    };
  }, [appointments, dateFilter]);

  const handleCheckIn = async (appointmentId) => {
    try {
      await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(appointmentId));
      fetchAppointments();
    } catch (e) {
      alert(e.response?.data?.message || e.message || 'Không thể check-in khách');
    }
  };

  const handleStartAppointment = async (appointmentId) => {
    try {
      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointmentId), {
        status: 'IN_PROGRESS',
        ...(isSpaReception ? { notes: 'Tiếp đón quầy kích hoạt bắt đầu buổi trị liệu' } : {})
      });
      fetchAppointments();
    } catch (e) {
      alert(e.response?.data?.message || e.message || 'Không thể bắt đầu ca dịch vụ');
    }
  };

  const handleCompleteAppointment = async (apt) => {
    setWorkspaceId(apt.id);
  };

  const handleSaveStaffAssignment = async (e) => {
    e.preventDefault();
    if (!selectedAptForAssign) return;
    setAssignSubmitting(true);
    try {
      const assignments = {};
      (selectedAptForAssign.items || []).forEach(it => {
        assignments[it.id] = Number(selectedStaffId);
      });

      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(selectedAptForAssign.id), {
        status: 'CONFIRMED',
        staffAssignments: assignments,
        notes: 'Lễ tân điều phối chuyên viên phụ trách ca'
      });
      setSelectedAptForAssign(null);
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Lỗi khi gán chuyên viên');
    } finally {
      setAssignSubmitting(false);
    }
  };

  const handleOpenReschedule = (apt) => {
    setRescheduleApt(apt);
    setRescheduleDate(apt.appointmentDate);
    setRescheduleTime(apt.startTime || '09:00:00');
    setRescheduleNote('');
  };

  const handleSaveReschedule = async (e) => {
    e.preventDefault();
    if (!rescheduleApt) return;
    if(rescheduleSlotsLoading||!rescheduleSlots.includes(rescheduleTime.slice(0,5))) return;
    setRescheduleSubmitting(true);
    try {
      await apiClient.put(ENDPOINTS.SPA.RESCHEDULE_APPOINTMENT(rescheduleApt.id), {
        appointmentDate: rescheduleDate,
        startTime: rescheduleTime.length === 5 ? `${rescheduleTime}:00` : rescheduleTime,
        notes: rescheduleNote.trim() || 'Lễ tân tiếp nhận dời lịch hẹn theo yêu cầu khách'
      });
      setRescheduleApt(null);
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Không thể dời lịch hẹn');
    } finally {
      setRescheduleSubmitting(false);
    }
  };

  const handleCancelAppointment = async (apt) => {
    const reason = prompt('Nhập lý do hủy lịch hẹn:', 'Khách báo bận việc đột xuất cần hủy ca');
    if (!reason) return;
    try {
      await apiClient.put(ENDPOINTS.SPA.CANCEL_APPOINTMENT(apt.id), { reason: reason.trim() });
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Không thể hủy lịch hẹn');
    }
  };

  const handleMarkNoShow = async (apt) => {
    const reason = prompt('Nhập lý do khách vắng mặt (No-show):', 'Khách không đến quá 30 phút và không thể liên lạc');
    if (!reason) return;
    try {
      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(apt.id), {
        status: 'NO_SHOW',
        notes: reason.trim()
      });
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Không thể đánh dấu No-show');
    }
  };

  const handleLookupTickets = async (e) => {
    e?.preventDefault();
    if (!ticketSearch.trim()) return;
    setLoadingTickets(true);
    try {
      const res = await apiClient.get(`${ENDPOINTS.SPA.TICKETS || '/api/v1/admin/spa/tickets'}?keyword=${encodeURIComponent(ticketSearch.trim())}`);
      const data = res.data || res;
      setTicketsData(data?.content || (Array.isArray(data) ? data : []));
    } catch (err) {
      console.warn('Failed to search tickets', err);
    } finally {
      setLoadingTickets(false);
    }
  };

  const currentHour = currentTime.getHours();
  const currentShiftLabel = currentHour < 13 ? 'Ca Sáng (08:00 - 13:00)' : 'Ca Chiều (13:00 - 20:30)';

  const displayFacilities = facilitiesList || [];

  const handleToggleRoomStatus = async (facility) => {
    const nextActive = facility.active === false ? true : false;
    try {
      await apiClient.put(`${ENDPOINTS.SPA.FACILITIES}/${facility.id}`, {
        name: facility.name,
        type: facility.type,
        capacity: facility.capacity,
        active: nextActive
      });
      fetchMasterData();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Lỗi cập nhật trạng thái phòng');
    }
  };

  const getRoomOccupancy = (facility) => {
    const matched = appointments.filter((a) => {
      if (a.status === 'CANCELLED' || a.status === 'NO_SHOW') return false;
      const item = a.items?.[0];
      if (!item) return false;
      if (item.facilityId === facility.id || (item.resources && item.resources.some((r) => r.facilityId === facility.id))) {
        return true;
      }
      const sName = (item.serviceName || '').toLowerCase();
      const fType = (facility.type || '').toUpperCase();
      if (fType === 'AQUA_PEEL' && (sName.includes('aqua peel') || sName.includes('làm sạch'))) return true;
      if (fType === 'ACNE_CLINIC' && (sName.includes('mụn') || sName.includes('bio-light'))) return true;
      if (fType === 'GALVANIC' && (sName.includes('điện di') || sName.includes('vitamin c') || sName.includes('căng bóng'))) return true;
      if (fType === 'HIGH_TECH' && (sName.includes('hifu') || sName.includes('laser') || sName.includes('nâng cơ') || sName.includes('toning'))) return true;
      if (fType === 'VIP_SUITE' && (sName.includes('collagen') || sName.includes('hoàng gia') || sName.includes('vip'))) return true;
      if (fType === 'BODY_THERAPY' && (sName.includes('massage') || sName.includes('đá nóng') || sName.includes('body'))) return true;
      if (fType === 'WHITENING_ROOM' && (sName.includes('tắm trắng') || sName.includes('phi thuyền'))) return true;
      if (fType === 'CONSULTATION' && (sName.includes('khám') || sName.includes('soi da'))) return true;
      return false;
    });

    const activeApt = matched.find((a) => a.status === 'IN_PROGRESS' || (a.items && a.items.some((i) => i.executionStatus === 'IN_PROGRESS')));
    const upcomingApt = matched.find((a) => a.status === 'CONFIRMED' || a.status === 'PENDING');

    const isRoomActive = facility.active !== undefined ? facility.active : (facility.isActive !== undefined ? facility.isActive : true);
    let state = 'FREE';
    if (!isRoomActive) state = 'MAINTENANCE';
    else if (activeApt) state = 'BUSY';
    else if (upcomingApt) state = 'UPCOMING';

    return { state, activeApt, upcomingApt, count: matched.length };
  };

  return (
    <div className="spa-portal-container">
      <header className="spa-portal-header">
        <div className="spa-portal-brand">
          <div className="spa-portal-brand-badge">
            <UserCheck size={18} />
          </div>
          <div>
            <h1 className="spa-portal-brand-title">
              Lễ Tân Spa
            </h1>
            <div className="spa-portal-brand-sub">
              <span>Tiếp đón và điều phối ca dịch vụ</span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '20px', flexWrap: 'wrap' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 600, fontFamily: 'monospace' }}>
              <span>{currentTime.toLocaleTimeString('vi-VN')}</span>
              <span style={{ opacity: 0.5 }}>-</span>
              <span>{currentTime.toLocaleDateString('vi-VN')}</span>
            </div>
          </div>

          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            backgroundColor: '#27272A',
            padding: '6px 12px',
            border: '1px solid #3F3F46',
            borderRadius: 0
          }}>
            <User size={14} color="#D4D4D8" />
            <div>
              <strong style={{ fontSize: '12.5px', display: 'block', lineHeight: 1.2, color: '#FFFFFF' }}>
                {user?.fullName || user?.username || 'Lễ Tân'}
              </strong>
              <span style={{ fontSize: '10px', color: '#A1A1AA' }}>
                Lễ Tân Trực Quầy
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <a href="#/treatments?tab=courses" style={{ color: '#FFFFFF', fontSize: 12, padding: '6px 12px' }}>Lộ trình của tôi</a>
            {typeof onSwitchToAdmin === 'function' && (
              <button
                onClick={onSwitchToAdmin}
                style={{
                  backgroundColor: '#27272A',
                  color: '#FFFFFF',
                  border: '1px solid #3F3F46',
                  borderRadius: 0,
                  padding: '6px 12px',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
              >
                <SlidersHorizontal size={13} />
                <span>Quản lý dịch vụ Spa</span>
              </button>
            )}

            <button
              onClick={logout}
              style={{
                backgroundColor: 'var(--sp-primary)',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: 0,
                padding: '6px 12px',
                fontSize: '12px',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '5px'
              }}
            >
              <LogOut size={13} />
              <span>Đăng xuất</span>
            </button>
          </div>
        </div>
      </header>

      <nav className="spa-portal-nav">
        <button className={`spa-nav-tab ${activeTab === 'treatments' ? 'active' : ''}`} onClick={() => setActiveTab('treatments')}><Stethoscope size={16} /><span>Hồ sơ lộ trình điều trị</span></button>
        <button
          className={`spa-nav-tab ${activeTab === 'queue' ? 'active' : ''}`}
          onClick={() => setActiveTab('queue')}
        >
          <UserCheck size={16} />
          <span>Sảnh Tiếp Đón & Check-in ({todayKPIs.total})</span>
        </button>

        {isSpaReception && <>
        <button
          className={`spa-nav-tab ${activeTab === 'pos' ? 'active' : ''}`}
          onClick={() => {
            setActiveTab('pos');
          }}
        >
          <Receipt size={16} />
          <span>Thu Ngân & Hoá Đơn POS{todayKPIs.unpaidCompleted > 0 ? ` (${todayKPIs.unpaidCompleted} chờ thu)` : ''}</span>
        </button>

        <button
          className={`spa-nav-tab ${activeTab === 'walk-in' ? 'active' : ''}`}
          onClick={() => setActiveTab('walk-in')}
        >
          <Plus size={16} />
          <span>Tiếp Nhận Khách Vãng Lai & Hotline</span>
        </button>

        <button
          className={`spa-nav-tab ${activeTab === 'tickets' ? 'active' : ''}`}
          onClick={() => {
            setActiveTab('tickets');
            handleLookupTickets();
          }}
        >
          <Ticket size={16} />
          <span>Tra Cứu Vé Liệu Trình Khách Hàng</span>
        </button>

        <button
          className={`spa-nav-tab ${activeTab === 'resources' ? 'active' : ''}`}
          onClick={() => setActiveTab('resources')}
        >
          <Building2 size={16} />
          <span>Sơ Đồ Phòng Chăm Sóc & KTV ({displayFacilities.length})</span>
        </button>
        </>}
      </nav>

      <main className="spa-portal-main">
        {activeTab === 'treatments' && <TreatmentWorkspace role={isAdmin ? 'admin' : 'staff'} />}

        {activeTab === 'queue' && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', marginBottom: '16px' }}>
              <div className="spa-card" style={{ padding: '14px 16px', borderLeft: '3px solid var(--sp-primary)' }}>
                <div style={{ fontSize: '12px', color: 'var(--sp-text-muted)', fontWeight: 600 }}>Tổng lịch hẹn</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#111827', marginTop: '2px', fontFamily: 'monospace' }}>
                  {todayKPIs.total}
                </div>
              </div>

              <div className="spa-card" style={{ padding: '14px 16px', borderLeft: '3px solid #065F46' }}>
                <div style={{ fontSize: '12px', color: '#065F46', fontWeight: 600 }}>Đã check-in / Đang làm</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#065F46', marginTop: '2px', fontFamily: 'monospace' }}>
                  {todayKPIs.inClinic}
                </div>
              </div>

              <div className="spa-card" style={{ padding: '14px 16px', borderLeft: '3px solid #92400E' }}>
                <div style={{ fontSize: '12px', color: '#92400E', fontWeight: 600 }}>Chờ tiếp đón</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#92400E', marginTop: '2px', fontFamily: 'monospace' }}>
                  {todayKPIs.waiting}
                </div>
              </div>

              <div className="spa-card" style={{ padding: '14px 16px', borderLeft: '3px solid #1E40AF' }}>
                <div style={{ fontSize: '12px', color: '#1E40AF', fontWeight: 600 }}>Đã hoàn tất</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#1E40AF', marginTop: '2px', fontFamily: 'monospace' }}>
                  {todayKPIs.completed}
                </div>
              </div>
            </div>

            <div className="spa-card" style={{ padding: '12px 18px', display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  onClick={() => setDateFilter(getVietnamDateString())}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: dateFilter === getVietnamDateString() ? 'var(--sp-primary)' : '#FFF',
                    color: dateFilter === getVietnamDateString() ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Hôm nay
                </button>
                <button
                  onClick={() => setDateFilter(getVietnamDateString(new Date(Date.now() + 86400000)))}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: dateFilter === getVietnamDateString(new Date(Date.now() + 86400000)) ? 'var(--sp-primary)' : '#FFF',
                    color: dateFilter === getVietnamDateString(new Date(Date.now() + 86400000)) ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Ngày mai
                </button>
                <button
                  onClick={() => setDateFilter('ALL')}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: dateFilter === 'ALL' ? 'var(--sp-primary)' : '#FFF',
                    color: dateFilter === 'ALL' ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Tất cả
                </button>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontSize: '12px', color: 'var(--sp-text-muted)' }}>Ngày:</span>
                <input
                  type="date"
                  value={dateFilter === 'ALL' ? '' : dateFilter}
                  onChange={(e) => setDateFilter(e.target.value || 'ALL')}
                  style={{
                    padding: '5px 8px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    fontSize: '12px',
                    fontFamily: 'monospace'
                  }}
                />
              </div>

              <div style={{ flex: 1, minWidth: '220px', position: 'relative' }}>
                <Search size={14} style={{ position: 'absolute', left: '10px', top: '9px', color: '#9CA3AF' }} />
                <input
                  type="text"
                  placeholder="Tìm theo Tên khách, Số điện thoại, Mã SPA-..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '6px 10px 6px 30px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    fontSize: '12px',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <button
                onClick={fetchAppointments}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '6px 12px',
                  backgroundColor: '#FFF',
                  border: '1px solid var(--sp-border)',
                  borderRadius: 0,
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                <RefreshCw size={12} />
                <span>Làm mới</span>
              </button>
            </div>

            <div className="spa-card" style={{ padding: 0, overflow: 'hidden' }}>
              <table className="spa-pos-table">
                <thead>
                  <tr>
                    <th>Khung giờ</th>
                    <th>Khách hàng</th>
                    <th>Liệu trình</th>
                    <th>Chuyên viên thực hiện</th>
                    <th>Chi phí / Vé</th>
                    <th>Trạng thái</th>
                    <th style={{ textAlign: 'right' }}>Thao tác tiếp đón</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAppointments.length === 0 ? (
                    <tr>
                      <td colSpan={7} style={{ textAlign: 'center', padding: '48px 24px' }}>
                        <div style={{ maxWidth: '420px', margin: '0 auto' }}>
                          <UserCheck size={36} color="var(--sp-primary)" style={{ opacity: 0.8, marginBottom: '8px' }} />
                          <h4 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 6px 0', color: '#111827' }}>
                            Chưa có lịch hẹn trong khung lọc này
                          </h4>
                          <p style={{ fontSize: '12.5px', color: 'var(--sp-text-muted)', margin: '0 0 16px 0', lineHeight: 1.5 }}>
                            Sảnh tiếp đón hiện chưa ghi nhận lịch khách. Bạn có thể chọn "Tất cả ngày" hoặc tạo nhanh lịch tiếp nhận khách vãng lai trực tiếp tại quầy.
                          </p>
                          <div style={{ display: 'flex', gap: '8px', justifyContent: 'center' }}>
                            <button
                              type="button"
                              onClick={() => setDateFilter('ALL')}
                              style={{
                                padding: '8px 16px',
                                border: '1px solid var(--sp-border)',
                                backgroundColor: '#FFF',
                                color: '#111827',
                                fontSize: '12.5px',
                                fontWeight: 600,
                                cursor: 'pointer'
                              }}
                            >
                              Xem tất cả ngày
                            </button>
                            {isSpaReception && <button
                              type="button"
                              onClick={() => setActiveTab('walk-in')}
                              style={{
                                padding: '8px 16px',
                                border: 'none',
                                backgroundColor: 'var(--sp-primary)',
                                color: '#FFF',
                                fontSize: '12.5px',
                                fontWeight: 600,
                                cursor: 'pointer'
                              }}
                            >
                              Tiếp nhận khách vãng lai
                            </button>}
                          </div>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    filteredAppointments.map((apt) => {
                      const isCheckedIn = Boolean(apt.checkedInAt);
                      const item = apt.items?.[0];
                      const staffName = item?.staffName && item.staffName !== 'No preference' ? item.staffName : 'Chưa phân công';

                      return (
                        <tr key={apt.id}>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, fontSize: '13.5px', color: '#111827' }}>
                              <Clock size={13} color="var(--sp-primary)" />
                              <span>{apt.startTime ? String(apt.startTime).slice(0, 5) : '09:00'}</span>
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--sp-text-light)', marginTop: '2px' }}>
                              {apt.appointmentDate}
                            </div>
                          </td>

                          <td>
                            <strong style={{ fontSize: '13.5px', color: '#111827', display: 'block' }}>
                              {apt.customerName || 'Khách vãng lai'}
                            </strong>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11.5px', color: 'var(--sp-text-muted)', marginTop: '2px' }}>
                              <Phone size={11} />
                              <span>{apt.customerPhone || 'Chưa có SĐT'}</span>
                            </div>
                          </td>

                          <td>
                            <span style={{ fontWeight: 600, color: '#111827' }}>
                              {item?.serviceName || 'Dịch vụ Spa'}
                            </span>
                          </td>

                          <td>
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '6px',
                              fontSize: '12px',
                              fontWeight: staffName !== 'Chưa phân công' ? 600 : 400,
                              color: staffName !== 'Chưa phân công' ? '#111827' : '#9CA3AF'
                            }}>
                              <User size={12} color="var(--sp-primary)" />
                              <span>{staffName}</span>
                            </span>
                          </td>

                          <td>
                            {item?.isTicketUsed ? (
                              <span style={{ border: '1px solid #A7F3D0', color: '#065F46', fontSize: '11px', fontWeight: 600, padding: '2px 6px' }}>
                                Vé liệu trình (0 ₫)
                              </span>
                            ) : (
                              <strong style={{ fontSize: '13px', color: '#9F1239', fontFamily: 'monospace' }}>
                                {formatCurrency(item?.price || 0)}
                              </strong>
                            )}
                          </td>

                          <td>
                            <span style={{
                              padding: '2px 6px',
                              fontSize: '11px',
                              fontWeight: 600,
                              border: '1px solid var(--sp-border)',
                              backgroundColor:
                                apt.status === 'COMPLETED' ? '#ECFDF5' :
                                apt.status === 'IN_PROGRESS' ? '#EFF6FF' :
                                isCheckedIn ? '#ECFDF5' :
                                apt.status === 'CONFIRMED' ? '#F0FDF4' :
                                apt.status === 'PENDING' ? '#FFFBEB' : '#F4F4F5',
                              color:
                                apt.status === 'COMPLETED' ? '#065F46' :
                                apt.status === 'IN_PROGRESS' ? '#1E40AF' :
                                isCheckedIn ? '#065F46' :
                                apt.status === 'CONFIRMED' ? '#166534' :
                                apt.status === 'PENDING' ? '#92400E' : '#52525B'
                            }}>
                              {apt.status === 'COMPLETED' ? 'Đã hoàn tất' :
                               apt.status === 'IN_PROGRESS' ? 'Đang trị liệu' :
                               isCheckedIn ? 'Đã check-in' :
                               apt.status === 'CONFIRMED' ? 'Đã xác nhận' :
                               apt.status === 'PENDING' ? 'Chờ duyệt' :
                               apt.status === 'CANCELLED' ? 'Đã hủy' :
                               apt.status === 'NO_SHOW' ? 'Vắng mặt' : (apt.status || 'Chưa xác định')}
                            </span>
                          </td>

                          <td style={{ textAlign: 'right' }}>
                            <div style={{ display: 'inline-flex', gap: '4px', alignItems: 'center' }}>
                              {isSpaReception && apt.status === 'PENDING' && (
                                <button
                                  onClick={async () => {
                                    let list = staffList;
                                    if (!list || list.length === 0) {
                                      try {
                                        const res = await apiClient.get(ENDPOINTS.SPA.STAFF || '/api/v1/admin/staff');
                                        const data = res?.data || res || [];
                                        list = Array.isArray(data) ? data : (data?.content || []);
                                        setStaffList(list);
                                      } catch (err) {
                                        console.warn('Lỗi tải danh sách chuyên viên', err);
                                      }
                                    }
                                    setSelectedAptForAssign(apt);
                                    setSelectedStaffId(list?.[0]?.id ? String(list[0].id) : '');
                                  }}
                                  style={{
                                    backgroundColor: 'var(--sp-primary)',
                                    color: '#FFF',
                                    border: 'none',
                                    borderRadius: 0,
                                    padding: '5px 10px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer'
                                  }}
                                >
                                  Gán KTV
                                </button>
                              )}

                              {isSpaReception && apt.status === 'CONFIRMED' && !isCheckedIn && (
                                <button
                                  onClick={() => handleCheckIn(apt.id)}
                                  style={{
                                    backgroundColor: '#065F46',
                                    color: '#FFF',
                                    border: 'none',
                                    borderRadius: 0,
                                    padding: '5px 12px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                  }}
                                >
                                  <UserCheck size={13} />
                                  <span>Check-in</span>
                                </button>
                              )}

                              <button type="button" onClick={() => setWorkspaceId(apt.id)} style={{ padding: '6px 10px' }}>Chuẩn bị / thực hiện</button>
                              {apt.status === 'CONFIRMED' && isCheckedIn && (
                                <button
                                  onClick={() => handleStartAppointment(apt.id)}
                                  style={{
                                    backgroundColor: 'var(--sp-primary)',
                                    color: '#FFF',
                                    border: 'none',
                                    borderRadius: 0,
                                    padding: '5px 12px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                  }}
                                >
                                  <Clock size={13} />
                                  <span>Vào phòng làm</span>
                                </button>
                              )}

                              {apt.status === 'IN_PROGRESS' && (
                                <button
                                  onClick={() => handleCompleteAppointment(apt)}
                                  style={{
                                    backgroundColor: '#065F46',
                                    color: '#FFF',
                                    border: 'none',
                                    borderRadius: 0,
                                    padding: '5px 12px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                  }}
                                >
                                  <CheckCircle2 size={13} />
                                  <span>Hoàn tất</span>
                                </button>
                              )}

                              {isSpaReception && apt.status === 'COMPLETED' && (
                                <button
                                  onClick={() => {
                                    setPosSelectedApt(apt);
                                                            setActiveTab('pos');
                                  }}
                                  style={{
                                    backgroundColor: '#9F1239',
                                    color: '#FFF',
                                    border: 'none',
                                    borderRadius: 0,
                                    padding: '5px 10px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px'
                                  }}
                                >
                                  <DollarSign size={12} />
                                  <span>Thu tiền POS</span>
                                </button>
                              )}

                              {isSpaReception && (apt.status === 'PENDING' || apt.status === 'CONFIRMED') && (
                                <button
                                  type="button"
                                  onClick={() => handleOpenReschedule(apt)}
                                  style={{
                                    backgroundColor: '#FFF',
                                    color: '#374151',
                                    border: '1px solid var(--sp-border)',
                                    borderRadius: 0,
                                    padding: '5px 8px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer'
                                  }}
                                >
                                  Dời giờ
                                </button>
                              )}

                              {isSpaReception && apt.status === 'CONFIRMED' && !isCheckedIn && (
                                <button
                                  type="button"
                                  onClick={() => handleMarkNoShow(apt)}
                                  style={{
                                    backgroundColor: '#FFF',
                                    color: '#92400E',
                                    border: '1px solid #FDE68A',
                                    borderRadius: 0,
                                    padding: '5px 8px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer'
                                  }}
                                >
                                  No-show
                                </button>
                              )}

                              {isSpaReception && (apt.status === 'PENDING' || apt.status === 'CONFIRMED') && (
                                <button
                                  type="button"
                                  onClick={() => handleCancelAppointment(apt)}
                                  style={{
                                    backgroundColor: '#FFF',
                                    color: '#9F1239',
                                    border: '1px solid #FECACA',
                                    borderRadius: 0,
                                    padding: '5px 8px',
                                    fontSize: '11.5px',
                                    fontWeight: 600,
                                    cursor: 'pointer'
                                  }}
                                >
                                  Hủy ca
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {isSpaReception && activeTab === 'pos' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.3fr) minmax(360px, 1fr)', gap: '20px' }}>
            <div className="spa-card">
              <h2 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 12px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Receipt size={16} color="var(--sp-primary)" />
                <span>Danh sách ca điều trị hoàn tất chờ xuất hoá đơn</span>
              </h2>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {appointments.filter(a => a.status === 'COMPLETED').length === 0 ? (
                  <div style={{ padding: '30px', textAlign: 'center', color: 'var(--sp-text-muted)', fontSize: '13px' }}>
                    Không có ca điều trị nào đang chờ thanh toán.
                  </div>
                ) : (
                  appointments.filter(a => a.status === 'COMPLETED').map(apt => {
                    const isSelected = posSelectedApt?.id === apt.id;
                    const price = apt.items?.reduce((s, i) => s + Number(i.price || 0), 0) || 0;
                    const hasInvoice = Boolean(apt.orderId);

                    return (
                      <div
                        key={apt.id}
                        onClick={() => {
                          setPosSelectedApt(apt);
                                      }}
                        style={{
                          padding: '12px 14px',
                          borderRadius: 0,
                          border: isSelected ? '2px solid var(--sp-primary)' : '1px solid var(--sp-border)',
                          backgroundColor: isSelected ? 'var(--sp-primary-light)' : '#FFF',
                          cursor: 'pointer',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center'
                        }}
                      >
                        <div>
                          <strong style={{ fontSize: '13.5px', color: '#111827' }}>{apt.customerName}</strong>
                          <div style={{ fontSize: '12px', color: 'var(--sp-text-muted)' }}>{apt.items?.[0]?.serviceName}</div>
                          <div style={{ fontSize: '11px', color: 'var(--sp-text-light)' }}>Giờ hẹn: {String(apt.startTime).slice(0, 5)} ngày {apt.appointmentDate}</div>
                        </div>

                        <div style={{ textAlign: 'right' }}>
                          <div style={{ fontSize: '15px', fontWeight: 700, color: '#9F1239', fontFamily: 'monospace' }}>
                            {formatCurrency(price)}
                          </div>
                          <span style={{ fontSize: '11px', fontWeight: 600, color: '#92400E' }}>
                            {hasInvoice ? 'Đã lập hóa đơn — kiểm tra thanh toán' : 'Chưa lập hóa đơn'}
                          </span>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            <div className="spa-card" style={{ border: '1px solid var(--sp-border)' }}>
              <h2 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 14px 0', borderBottom: '1px solid var(--sp-border)', paddingBottom: '10px' }}>
                Thu ngân và thanh toán tại quầy
              </h2>

              {posSelectedApt ? (
                <SpaVisitCheckout key={posSelectedApt.id} appointment={posSelectedApt} onUpdated={fetchAppointments} />
              ) : (
                <p>Chọn một ca hoàn tất để xem hóa đơn và thanh toán.</p>
              )}

            </div>
          </div>
        )}

        {isSpaReception && activeTab === 'walk-in' && <WalkInDrawer services={services} facilities={facilitiesList} onClose={() => setActiveTab('queue')} onBooked={() => { fetchAppointments(); }} />}

        {isSpaReception && activeTab === 'tickets' && (
          <div className="spa-card">
            <h2 style={{ fontSize: '15px', fontWeight: 700, margin: '0 0 12px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Ticket size={16} color="var(--sp-primary)" />
              <span>Tra cứu vé và thẻ liệu trình điện tử của khách hàng</span>
            </h2>

            <form onSubmit={handleLookupTickets} style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
              <input
                type="text"
                placeholder="Nhập Số điện thoại hoặc Tên khách hàng để tra cứu..."
                value={ticketSearch}
                onChange={(e) => setTicketSearch(e.target.value)}
                style={{ flex: 1, padding: '8px 12px', borderRadius: 0, border: '1px solid var(--sp-border)', fontSize: '13px' }}
              />
              <button
                type="submit"
                style={{
                  padding: '8px 16px',
                  backgroundColor: 'var(--sp-primary)',
                  color: '#FFF',
                  border: 'none',
                  borderRadius: 0,
                  fontSize: '12.5px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Tra cứu
              </button>
            </form>

            <table className="spa-pos-table">
              <thead>
                <tr>
                  <th>Mã vé</th>
                  <th>Gói liệu trình</th>
                  <th>Số buổi sử dụng</th>
                  <th>Buổi khả dụng</th>
                  <th>Hạn sử dụng</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {ticketsData.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '28px', color: 'var(--sp-text-muted)' }}>
                      {loadingTickets ? 'Đang tra cứu vé liệu trình...' : 'Chưa có thông tin vé liệu trình nào.'}
                    </td>
                  </tr>
                ) : (
                  ticketsData.map(tk => (
                    <tr key={tk.id}>
                      <td><strong style={{ fontFamily: 'monospace' }}>#{tk.id}</strong></td>
                      <td><strong>{tk.packageName || 'Combo Spa'}</strong></td>
                      <td>{tk.usedSessions} / {tk.totalSessions} buổi</td>
                      <td><strong style={{ color: '#065F46' }}>{tk.totalSessions - tk.usedSessions - (tk.reservedSessions || 0)} buổi</strong></td>
                      <td>{tk.expiryDate ? formatDate(tk.expiryDate) : 'Không thời hạn'}</td>
                      <td>
                        <span style={{
                          padding: '2px 6px',
                          borderRadius: 0,
                          fontSize: '11px',
                          fontWeight: 600,
                          border: '1px solid var(--sp-border)',
                          backgroundColor: tk.status === 'ACTIVE' ? '#ECFDF5' : '#F4F4F5',
                          color: tk.status === 'ACTIVE' ? '#065F46' : '#52525B'
                        }}>
                          {tk.status === 'ACTIVE' ? 'Còn hiệu lực' :
                           tk.status === 'EXPIRED' ? 'Đã hết hạn' :
                           tk.status === 'USED' ? 'Đã dùng hết' :
                           tk.status === 'CANCELLED' ? 'Đã hủy' : (tk.status || 'Chưa xác định')}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {isSpaReception && activeTab === 'resources' && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', marginBottom: '16px' }}>
              <div className="spa-card" style={{ padding: '14px 18px', borderLeft: '3px solid var(--sp-primary)' }}>
                <div style={{ fontSize: '11px', color: 'var(--sp-text-muted)', fontWeight: 700 }}>TỔNG SỐ PHÒNG ĐIỀU TRỊ</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#111827', marginTop: '2px', fontFamily: 'monospace' }}>
                  {displayFacilities.length} phòng
                </div>
                <div style={{ fontSize: '11px', color: 'var(--sp-text-light)' }}>
                  Tổng sức chứa: {displayFacilities.reduce((sum, f) => sum + (f.capacity || 1), 0)} giường
                </div>
              </div>

              <div className="spa-card" style={{ padding: '14px 18px', borderLeft: '3px solid #9F1239' }}>
                <div style={{ fontSize: '11px', color: '#9F1239', fontWeight: 700 }}>ĐANG TRỊ LIỆU (BẬN)</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#9F1239', marginTop: '2px', fontFamily: 'monospace' }}>
                  {displayFacilities.filter(f => getRoomOccupancy(f).state === 'BUSY').length} phòng
                </div>
                <div style={{ fontSize: '11px', color: 'var(--sp-text-light)' }}>
                  Có khách và KTV đang làm ca
                </div>
              </div>

              <div className="spa-card" style={{ padding: '14px 18px', borderLeft: '3px solid #92400E' }}>
                <div style={{ fontSize: '11px', color: '#92400E', fontWeight: 700 }}>SẮP VÀO CA TIẾP THEO</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#92400E', marginTop: '2px', fontFamily: 'monospace' }}>
                  {displayFacilities.filter(f => getRoomOccupancy(f).state === 'UPCOMING').length} phòng
                </div>
                <div style={{ fontSize: '11px', color: 'var(--sp-text-light)' }}>
                  Đã có khách hẹn chờ vào sảnh
                </div>
              </div>

              <div className="spa-card" style={{ padding: '14px 18px', borderLeft: '3px solid #065F46' }}>
                <div style={{ fontSize: '11px', color: '#065F46', fontWeight: 700 }}>PHÒNG TRỐNG SẴN SÀNG</div>
                <div style={{ fontSize: '24px', fontWeight: 700, color: '#065F46', marginTop: '2px', fontFamily: 'monospace' }}>
                  {displayFacilities.filter(f => getRoomOccupancy(f).state === 'FREE').length} phòng
                </div>
                <div style={{ fontSize: '11px', color: 'var(--sp-text-light)' }}>
                  Sẵn sàng đón khách mới ngay
                </div>
              </div>
            </div>

            <div className="spa-card" style={{ padding: '10px 18px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px', marginBottom: '16px' }}>
              <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: '#374151', marginRight: '6px' }}>Lọc phòng:</span>
                <button
                  type="button"
                  onClick={() => setRoomStatusFilter('ALL')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: roomStatusFilter === 'ALL' ? 'var(--sp-primary)' : '#FFF',
                    color: roomStatusFilter === 'ALL' ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Tất cả ({displayFacilities.length})
                </button>
                <button
                  type="button"
                  onClick={() => setRoomStatusFilter('BUSY')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: roomStatusFilter === 'BUSY' ? 'var(--sp-primary)' : '#FFF',
                    color: roomStatusFilter === 'BUSY' ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Đang bận ({displayFacilities.filter(f => getRoomOccupancy(f).state === 'BUSY').length})
                </button>
                <button
                  type="button"
                  onClick={() => setRoomStatusFilter('UPCOMING')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: roomStatusFilter === 'UPCOMING' ? 'var(--sp-primary)' : '#FFF',
                    color: roomStatusFilter === 'UPCOMING' ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Sắp có ca ({displayFacilities.filter(f => getRoomOccupancy(f).state === 'UPCOMING').length})
                </button>
                <button
                  type="button"
                  onClick={() => setRoomStatusFilter('FREE')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: 0,
                    border: '1px solid var(--sp-border)',
                    backgroundColor: roomStatusFilter === 'FREE' ? 'var(--sp-primary)' : '#FFF',
                    color: roomStatusFilter === 'FREE' ? '#FFF' : '#374151',
                    fontSize: '12px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Đang trống ({displayFacilities.filter(f => getRoomOccupancy(f).state === 'FREE').length})
                </button>
              </div>

              <div style={{ fontSize: '12px', color: 'var(--sp-text-muted)' }}>
                Đang trực: <strong>{staffList.length} chuyên viên</strong> · Giờ làm việc: <strong>08:00 - 20:00</strong>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.8fr) minmax(300px, 1fr)', gap: '16px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '10px' }}>
                {displayFacilities
                  .filter(f => {
                    const { state } = getRoomOccupancy(f);
                    if (roomStatusFilter === 'ALL') return true;
                    return state === roomStatusFilter;
                  })
                  .map((f) => {
                    const { state } = getRoomOccupancy(f);
                    const isBusy = state === 'BUSY';
                    const isUpcoming = state === 'UPCOMING';
                    const isFree = state === 'FREE';

                    return (
                      <div
                        key={f.id}
                        className="spa-card"
                        style={{
                          margin: 0,
                          padding: '14px 16px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          border: isBusy ? '1px solid #9F1239' : isUpcoming ? '1px solid #D97706' : isFree ? '1px solid #065F46' : '1px solid var(--sp-border)',
                          backgroundColor: isBusy ? '#FFF8F9' : isUpcoming ? '#FFFDF7' : isFree ? '#F7FCF9' : '#FAFAFA'
                        }}
                      >
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                          <strong style={{ fontSize: '14px', color: '#111827' }}>
                            {f.name}
                          </strong>
                          <span style={{ fontSize: '11px', color: 'var(--sp-text-muted)' }}>
                            Tiếp nhận: {isBusy ? 0 : (f.capacity || 1)} / {f.capacity || 1} chỗ
                          </span>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <span style={{
                            padding: '3px 8px',
                            fontSize: '11.5px',
                            fontWeight: 600,
                            border: '1px solid',
                            borderColor: isBusy ? '#9F1239' : isUpcoming ? '#D97706' : isFree ? '#065F46' : '#71717A',
                            backgroundColor: isBusy ? '#FFE4E6' : isUpcoming ? '#FEF3C7' : isFree ? '#D1FAE5' : '#F4F4F5',
                            color: isBusy ? '#9F1239' : isUpcoming ? '#92400E' : isFree ? '#065F46' : '#52525B'
                          }}>
                            {isBusy ? 'Đang có ca' : isUpcoming ? 'Sắp có ca' : isFree ? 'Đang trống' : 'Tạm dừng'}
                          </span>

                          <button
                            type="button"
                            onClick={() => handleToggleRoomStatus(f)}
                            title={f.active === false ? "Mở phòng lại hoạt động" : "Tạm dừng bảo trì phòng"}
                            style={{
                              padding: '2px 7px',
                              fontSize: '11px',
                              border: '1px solid var(--sp-border)',
                              backgroundColor: '#FFFFFF',
                              color: 'var(--sp-text-muted)',
                              cursor: 'pointer'
                            }}
                          >
                            {f.active === false ? 'Mở lại' : 'Tạm dừng'}
                          </button>
                        </div>
                      </div>
                    );
                  })}
              </div>

              <div className="spa-card" style={{ margin: 0, padding: '16px' }}>
                <h3 style={{ fontSize: '14px', fontWeight: 700, margin: '0 0 12px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <User size={15} color="var(--sp-primary)" />
                  <span>Chuyên viên trong ca ({staffList.length})</span>
                </h3>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {staffList.map(st => {
                    const busyApt = appointments.find(a =>
                      (a.status === 'IN_PROGRESS' || (a.items && a.items.some(i => i.executionStatus === 'IN_PROGRESS'))) &&
                      a.items?.some(i => i.staffId === st.id)
                    );
                    const isBusy = Boolean(busyApt);

                    return (
                      <div
                        key={st.id}
                        style={{
                          padding: '10px 12px',
                          border: '1px solid var(--sp-border)',
                          backgroundColor: isBusy ? '#FFF8F9' : '#FFFFFF',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center'
                        }}
                      >
                        <div>
                          <strong style={{ fontSize: '13px', color: '#111827', display: 'block' }}>
                            {st.fullName || `KTV #${st.id}`}
                          </strong>
                          <span style={{ fontSize: '11px', color: 'var(--sp-text-muted)' }}>
                            {st.specialty || 'Chuyên viên'}
                          </span>
                        </div>

                        <span style={{
                          fontSize: '11px',
                          fontWeight: 600,
                          padding: '2px 6px',
                          border: '1px solid',
                          borderColor: isBusy ? '#9F1239' : '#065F46',
                          backgroundColor: isBusy ? '#FFE4E6' : '#D1FAE5',
                          color: isBusy ? '#9F1239' : '#065F46'
                        }}>
                          {isBusy ? 'Đang có ca' : 'Sẵn sàng'}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        )}

      </main>

      {isSpaReception && selectedAptForAssign && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999
        }}>
          <div style={{ backgroundColor: '#FFF', borderRadius: 0, padding: '20px', width: '90%', maxWidth: '440px', border: '1px solid var(--sp-border)' }}>
            <h3 style={{ margin: '0 0 10px 0', fontSize: '15px' }}>Phân công Kỹ thuật viên phụ trách</h3>
            <p style={{ fontSize: '12.5px', color: 'var(--sp-text-muted)', margin: '0 0 14px 0' }}>
              Khách hàng: <strong>{selectedAptForAssign.customerName}</strong> ({String(selectedAptForAssign.startTime).slice(0, 5)} ngày {selectedAptForAssign.appointmentDate})
            </p>

            <form onSubmit={handleSaveStaffAssignment}>
              <select
                value={selectedStaffId}
                onChange={(e) => setSelectedStaffId(e.target.value)}
                style={{ width: '100%', padding: '8px', borderRadius: 0, border: '1px solid var(--sp-border)', marginBottom: '14px', fontSize: '13px' }}
              >
                {staffList.length === 0 ? (
                  <option value="">Không có chuyên viên khả dụng</option>
                ) : (
                  staffList.map(st => (
                    <option key={st.id} value={st.id}>
                      {st.fullName || `KTV #${st.id}`} - {st.specialty || 'Chuyên viên'}
                    </option>
                  ))
                )}
              </select>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button
                  type="button"
                  onClick={() => setSelectedAptForAssign(null)}
                  style={{ padding: '7px 14px', borderRadius: 0, border: '1px solid var(--sp-border)', backgroundColor: '#FFF', cursor: 'pointer' }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={assignSubmitting}
                  style={{ padding: '7px 14px', borderRadius: 0, backgroundColor: 'var(--sp-primary)', color: '#FFF', border: 'none', fontWeight: 600, cursor: 'pointer' }}
                >
                  {assignSubmitting ? 'Đang lưu...' : 'Xác nhận gán ca'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {workspaceId && <TreatmentAppointmentDialog appointmentId={workspaceId} role={isAdmin ? 'admin' : 'staff'} onClose={() => setWorkspaceId(null)} onSaved={fetchAppointments} />}
      {isSpaReception && rescheduleApt && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999
        }}>
          <div style={{ backgroundColor: '#FFF', borderRadius: 0, padding: '20px', width: '90%', maxWidth: '440px', border: '1px solid var(--sp-border)' }}>
            <h3 style={{ margin: '0 0 10px 0', fontSize: '15px' }}>Dời khung giờ lịch hẹn</h3>
            <p style={{ fontSize: '12.5px', color: 'var(--sp-text-muted)', margin: '0 0 14px 0' }}>
              Khách hàng: <strong>{rescheduleApt.customerName}</strong> ({rescheduleApt.items?.[0]?.serviceName})
            </p>

            <form onSubmit={handleSaveReschedule} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Ngày hẹn mới *</label>
                <input
                  type="date"
                  required
                  value={rescheduleDate}
                  onChange={(e) => setRescheduleDate(e.target.value)}
                  style={{ width: '100%', padding: '7px 10px', borderRadius: 0, border: '1px solid var(--sp-border)', fontSize: '13px', boxSizing: 'border-box' }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Giờ bắt đầu mới *</label>
                <select
                  value={rescheduleSlots.includes(rescheduleTime.slice(0,5))?rescheduleTime.slice(0,5):''}
                  onChange={(e) => setRescheduleTime(e.target.value)}
                  style={{ width: '100%', padding: '7px 10px', borderRadius: 0, border: '1px solid var(--sp-border)', fontSize: '13px' }}
                >
                  <option value="">{rescheduleSlotsLoading?'Đang kiểm tra giờ trống…':'Chọn giờ khả dụng'}</option>
                  {rescheduleSlots.map(slot=><option key={slot} value={slot}>{slot}</option>)}
                </select>
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 600, display: 'block', marginBottom: '4px' }}>Lý do dời lịch</label>
                <input
                  type="text"
                  placeholder="Khách xin lùi ca, đổi giờ..."
                  value={rescheduleNote}
                  onChange={(e) => setRescheduleNote(e.target.value)}
                  style={{ width: '100%', padding: '7px 10px', borderRadius: 0, border: '1px solid var(--sp-border)', fontSize: '12.5px', boxSizing: 'border-box' }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '6px' }}>
                <button
                  type="button"
                  onClick={() => setRescheduleApt(null)}
                  style={{ padding: '7px 14px', borderRadius: 0, border: '1px solid var(--sp-border)', backgroundColor: '#FFF', cursor: 'pointer' }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={rescheduleSubmitting||rescheduleSlotsLoading||!rescheduleSlots.includes(rescheduleTime.slice(0,5))}
                  style={{ padding: '7px 14px', borderRadius: 0, backgroundColor: 'var(--sp-primary)', color: '#FFF', border: 'none', fontWeight: 600, cursor: 'pointer' }}
                >
                  {rescheduleSubmitting ? 'Đang lưu...' : 'Xác nhận dời lịch'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SpaStaffApp;
