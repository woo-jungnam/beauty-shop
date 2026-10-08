import React, { useState, useEffect, useMemo } from 'react';
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
  CalendarDays,
  Check,
  X,
  ChevronRight,
  ShieldAlert,
  ArrowRight
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { formatCurrency, formatDate } from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';
import { useAuth } from '../app/providers/AuthProvider';

export const SpaReceptionPage = () => {
  const { user } = useAuth();
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [services, setServices] = useState([]);
  const [staffList, setStaffList] = useState([]);

  // Filter States - Default to ALL so queue is never blank on load
  const [dateFilter, setDateFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Modal 1: Walk-in Booking
  const [walkInModalOpen, setWalkInModalOpen] = useState(false);
  const [walkInForm, setWalkInForm] = useState({
    customerName: '',
    phone: '',
    serviceId: '',
    staffId: '',
    appointmentDate: new Date().toISOString().split('T')[0],
    startTime: '09:00:00',
    notes: '',
    autoCheckIn: true
  });
  const [submittingWalkIn, setSubmittingWalkIn] = useState(false);

  // Modal 2: Cashier & Invoicing Checkout
  const [checkoutModalOpen, setCheckoutModalOpen] = useState(false);
  const [checkoutAppointment, setCheckoutAppointment] = useState(null);
  const [cashReceived, setCashReceived] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [checkoutSubmitting, setCheckoutSubmitting] = useState(false);
  const [checkoutSuccessInfo, setCheckoutSuccessInfo] = useState(null);

  // Modal 3: Staff Assignment & Confirmation
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [assignAppointment, setAssignAppointment] = useState(null);
  const [assignedStaffId, setAssignedStaffId] = useState('');
  const [submittingAssign, setSubmittingAssign] = useState(false);

  // Modal 4: Reschedule
  const [rescheduleModalOpen, setRescheduleModalOpen] = useState(false);
  const [rescheduleAppointment, setRescheduleAppointment] = useState(null);
  const [newDate, setNewDate] = useState('');
  const [newTime, setNewTime] = useState('09:00:00');
  const [rescheduleNote, setRescheduleNote] = useState('');
  const [submittingReschedule, setSubmittingReschedule] = useState(false);

  // Modal 5: Ticket Balance Lookup
  const [ticketModalOpen, setTicketModalOpen] = useState(false);
  const [ticketSearchPhone, setTicketSearchPhone] = useState('');
  const [ticketsList, setTicketsList] = useState([]);
  const [loadingTickets, setLoadingTickets] = useState(false);

  // Load Initial Data
  useEffect(() => {
    fetchAppointments();
    fetchServicesAndStaff();
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
      console.error('Failed to fetch reception appointments', e);
    } finally {
      setLoading(false);
    }
  };

  const fetchServicesAndStaff = async () => {
    try {
      const [srvRes, stfRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.SPA.PUBLIC_SERVICES || '/api/v1/spa/services'),
        apiClient.get(ENDPOINTS.SPA.STAFF || '/api/v1/admin/staff')
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
    } catch (err) {
      console.warn('Lỗi tải dữ liệu chuyên viên từ database', err);
    }
  };

  // Reception Filtered Appointments
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

  // Reception Stats for today
  const stats = useMemo(() => {
    const todayStr = new Date().toISOString().split('T')[0];
    const todayList = appointments.filter(a => !dateFilter || dateFilter === 'ALL' || a.appointmentDate === todayStr);
    return {
      total: todayList.length,
      checkedInOrInProgress: todayList.filter(a => a.status === 'IN_PROGRESS' || a.checkedInAt).length,
      waiting: todayList.filter(a => (a.status === 'PENDING' || a.status === 'CONFIRMED') && !a.checkedInAt).length,
      completed: todayList.filter(a => a.status === 'COMPLETED').length,
    };
  }, [appointments, dateFilter]);

  // Action: Check-in Guest
  const handleCheckIn = async (appointmentId) => {
    try {
      await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(appointmentId));
      fetchAppointments();
    } catch (e) {
      alert(e.response?.data?.message || e.message || 'Không thể ghi nhận check-in');
    }
  };

  // Action: Start Appointment (IN_PROGRESS)
  const handleStartAppointment = async (appointmentId) => {
    try {
      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(appointmentId), {
        status: 'IN_PROGRESS',
        notes: 'Tiếp tân kích hoạt bắt đầu buổi chăm sóc'
      });
      fetchAppointments();
    } catch (e) {
      alert(e.response?.data?.message || e.message || 'Không thể bắt đầu buổi hẹn');
    }
  };

  // Action: Complete Appointment (COMPLETED)
  const handleCompleteAppointment = async (apt) => {
    try {
      // Complete each item if planned
      for (const item of (apt.items || [])) {
        if (item.executionStatus === 'PLANNED' || item.executionStatus === 'IN_PROGRESS') {
          try {
            await apiClient.put(`/api/v1/appointments/${apt.id}/items/${item.id}/execute`, {
              status: 'PERFORMED',
              notes: 'Hoàn tất dịch vụ bởi kỹ thuật viên'
            });
          } catch (_) {}
        }
      }

      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(apt.id), {
        status: 'COMPLETED',
        notes: 'Tiếp tân xác nhận hoàn tất buổi trị liệu'
      });
      fetchAppointments();

      // Open checkout invoice modal if un-invoiced
      if (!apt.orderId) {
        setCheckoutAppointment(apt);
        setPaymentMethod('CASH');
        setCashReceived('');
        setCheckoutModalOpen(true);
      }
    } catch (e) {
      alert(e.response?.data?.message || e.message || 'Không thể hoàn tất buổi hẹn');
    }
  };

  // Action: Confirm & Assign Staff
  const handleOpenAssign = async (apt) => {
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
    setAssignAppointment(apt);
    const existingStaff = apt.items?.[0]?.staffId;
    setAssignedStaffId(existingStaff ? String(existingStaff) : (list?.[0]?.id ? String(list[0].id) : ''));
    setAssignModalOpen(true);
  };

  const handleConfirmAssignment = async (e) => {
    e.preventDefault();
    if (!assignAppointment) return;
    setSubmittingAssign(true);
    try {
      const assignments = {};
      (assignAppointment.items || []).forEach(it => {
        assignments[it.id] = Number(assignedStaffId);
      });

      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(assignAppointment.id), {
        status: 'CONFIRMED',
        staffAssignments: assignments,
        notes: 'Lễ tân phân công chuyên viên phụ trách'
      });
      setAssignModalOpen(false);
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Lỗi khi gán chuyên viên');
    } finally {
      setSubmittingAssign(false);
    }
  };

  // Action: Walk-in Booking Submit
  const handleWalkInSubmit = async (e) => {
    e.preventDefault();
    setSubmittingWalkIn(true);
    try {
      const payload = {
        appointmentDate: walkInForm.appointmentDate,
        startTime: walkInForm.startTime,
        notes: `[Tiếp đón tại quầy] ${walkInForm.customerName} - ${walkInForm.phone}. ${walkInForm.notes || ''}`.trim(),
        items: [
          {
            serviceId: Number(walkInForm.serviceId),
            staffId: walkInForm.staffId ? Number(walkInForm.staffId) : null
          }
        ]
      };

      const res = await apiClient.post(ENDPOINTS.SPA.BOOK_APPOINTMENT, payload);
      const created = res.data || res;

      // Auto check-in if requested
      if (walkInForm.autoCheckIn && created?.id) {
        try {
          // If staff assigned, confirm first
          if (walkInForm.staffId) {
            const assignMap = {};
            if (created.items?.[0]?.id) {
              assignMap[created.items[0].id] = Number(walkInForm.staffId);
            }
            await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(created.id), {
              status: 'CONFIRMED',
              staffAssignments: assignMap
            });
          }
          await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(created.id));
        } catch (_) {}
      }

      setWalkInModalOpen(false);
      setWalkInForm({
        customerName: '',
        phone: '',
        serviceId: services[0]?.id ? String(services[0].id) : '',
        staffId: '',
        appointmentDate: new Date().toISOString().split('T')[0],
        startTime: '09:00:00',
        notes: '',
        autoCheckIn: true
      });
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Lỗi khi đặt lịch tiếp đón');
    } finally {
      setSubmittingWalkIn(false);
    }
  };

  // Action: Cashier Checkout Invoice & Cash Receipt
  const handleCashierCheckout = async (e) => {
    e.preventDefault();
    if (!checkoutAppointment) return;
    setCheckoutSubmitting(true);
    try {
      // 1. Create Invoice
      const invoicePayload = {
        paymentMethod: paymentMethod,
        notes: `Phiếu thanh toán quầy lễ tân thu ngân - Thu ngân: ${user?.fullName || user?.username || 'Reception'}`
      };
      const invRes = await apiClient.post(
        ENDPOINTS.SPA.APPOINTMENT_INVOICE(checkoutAppointment.id),
        invoicePayload,
        { headers: { 'Idempotency-Key': `inv_${checkoutAppointment.id}_${Date.now()}` } }
      );
      const invoiceData = invRes.data || invRes;

      // 2. If Cash, record cash collection
      if (paymentMethod === 'CASH' && invoiceData?.orderNumber) {
        const cashAmount = Number(invoiceData.totalAmount || checkoutAppointment.items?.reduce((s, i) => s + Number(i.price || 0), 0) || 0);
        if (cashAmount > 0) {
          await apiClient.post(
            ENDPOINTS.SPA.APPOINTMENT_CASH_RECEIPT(checkoutAppointment.id),
            { amount: cashAmount },
            { headers: { 'Idempotency-Key': `rcp_${checkoutAppointment.id}_${Date.now()}` } }
          );
        }
      }

      setCheckoutSuccessInfo({
        orderNumber: invoiceData?.orderNumber || `ORD-SPA-${checkoutAppointment.id}`,
        amount: invoiceData?.totalAmount || 0,
        customerName: checkoutAppointment.customerName
      });
      setCheckoutModalOpen(false);
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Lỗi khi xuất hoá đơn thanh toán');
    } finally {
      setCheckoutSubmitting(false);
    }
  };

  // Action: Reschedule
  const handleOpenReschedule = (apt) => {
    setRescheduleAppointment(apt);
    setNewDate(apt.appointmentDate);
    setNewTime(apt.startTime || '09:00:00');
    setRescheduleNote('');
    setRescheduleModalOpen(true);
  };

  const handleConfirmReschedule = async (e) => {
    e.preventDefault();
    if (!rescheduleAppointment) return;
    setSubmittingReschedule(true);
    try {
      await apiClient.put(ENDPOINTS.SPA.RESCHEDULE_APPOINTMENT(rescheduleAppointment.id), {
        appointmentDate: newDate,
        startTime: newTime.length === 5 ? `${newTime}:00` : newTime,
        notes: rescheduleNote.trim() || 'Lễ tân tiếp nhận dời lịch hẹn theo yêu cầu khách hàng'
      });
      setRescheduleModalOpen(false);
      fetchAppointments();
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Không thể dời lịch hẹn');
    } finally {
      setSubmittingReschedule(false);
    }
  };

  // Action: No-show
  const handleMarkNoShow = async (apt) => {
    const reason = prompt('Nhập lý do khách vắng mặt (No-show):', 'Khách không đến quá 30 phút và không liên lạc được');
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

  // Table Columns
  const columns = [
    {
      header: 'Thời gian',
      cell: (row) => (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 800, fontSize: '13.5px', color: '#1F2937' }}>
            <Clock size={14} color="var(--c-primary, #D45D79)" />
            <span>{row.startTime ? String(row.startTime).slice(0, 5) : '09:00'}</span>
          </div>
          <div style={{ fontSize: '11px', color: 'var(--c-text-light)', marginTop: '2px' }}>
            {row.appointmentDate}
          </div>
        </div>
      ),
    },
    {
      header: 'Khách hàng',
      cell: (row) => (
        <div>
          <strong style={{ fontSize: '14px', color: '#1F2937', display: 'block' }}>
            {row.customerName || 'Khách vãng lai'}
          </strong>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '2px' }}>
            <Phone size={11} />
            <span>{row.customerPhone || 'Chưa có SĐT'}</span>
          </div>
        </div>
      ),
    },
    {
      header: 'Dịch vụ liệu trình',
      cell: (row) => (
        <div>
          {row.items && row.items.length > 0 ? (
            row.items.map((it, idx) => (
              <div key={idx} style={{ marginBottom: '2px' }}>
                <span style={{ fontWeight: 600, fontSize: '13px', color: '#1F2937' }}>
                  {it.serviceName || `Dịch vụ #${it.serviceId}`}
                </span>
                <span style={{ fontSize: '11px', color: 'var(--c-text-muted)', marginLeft: '6px' }}>
                  ({it.endTime && it.startTime ? '60p' : 'Chuẩn vô trùng'})
                </span>
              </div>
            ))
          ) : (
            <span style={{ color: 'var(--c-text-muted)' }}>Chưa có dịch vụ</span>
          )}
        </div>
      ),
    },
    {
      header: 'Chuyên viên / Bác sĩ',
      cell: (row) => {
        const staffName = row.items?.[0]?.staffName;
        const hasStaff = staffName && staffName !== 'No preference';
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <User size={13} color={hasStaff ? 'var(--c-primary)' : '#9CA3AF'} />
            <span style={{ fontSize: '12.5px', fontWeight: hasStaff ? 700 : 500, color: hasStaff ? '#1F2937' : '#9CA3AF' }}>
              {hasStaff ? staffName : 'Chưa phân công'}
            </span>
          </div>
        );
      },
    },
    {
      header: 'Loại vé / Chi phí',
      cell: (row) => {
        const item = row.items?.[0];
        const isTicket = item?.isTicketUsed;
        const price = item?.price || 0;
        return (
          <div>
            {isTicket ? (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', backgroundColor: '#DCFCE7', color: '#15803D', fontSize: '11px', fontWeight: 700, padding: '2px 8px', borderRadius: '4px' }}>
                <Ticket size={11} />
                <span>Vé liệu trình</span>
              </span>
            ) : (
              <span style={{ fontSize: '13px', fontWeight: 800, color: 'var(--c-deal-red, #E11D48)', fontFamily: 'var(--font-mono)' }}>
                {formatCurrency(price)}
              </span>
            )}
          </div>
        );
      },
    },
    {
      header: 'Trạng thái',
      cell: (row) => {
        const isCheckedIn = Boolean(row.checkedInAt);
        const statusMap = {
          PENDING: { label: 'Chờ duyệt', variant: 'warning' },
          CONFIRMED: { label: isCheckedIn ? 'Đã check-in' : 'Đã xác nhận', variant: isCheckedIn ? 'success' : 'info' },
          IN_PROGRESS: { label: 'Đang thực hiện', variant: 'primary' },
          COMPLETED: { label: 'Hoàn tất', variant: 'success' },
          CANCELLED: { label: 'Đã hủy', variant: 'danger' },
          NO_SHOW: { label: 'Vắng mặt', variant: 'neutral' }
        };
        const st = statusMap[row.status] || { label: row.status, variant: 'neutral' };
        return (
          <div>
            <Badge variant={st.variant} size="sm">
              {st.label}
            </Badge>
            {isCheckedIn && row.status === 'CONFIRMED' && (
              <div style={{ fontSize: '10px', color: '#059669', fontWeight: 700, marginTop: '2px' }}>
                Đã vào sảnh chờ
              </div>
            )}
          </div>
        );
      },
    },
    {
      header: 'Thao tác tiếp đón',
      cell: (row) => {
        const status = row.status;
        const isCheckedIn = Boolean(row.checkedInAt);

        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
            {/* Step 1: PENDING -> Assign Staff */}
            {status === 'PENDING' && (
              <Button size="sm" variant="primary" onClick={() => handleOpenAssign(row)}>
                Xác nhận & Gán KTV
              </Button>
            )}

            {/* Step 2: CONFIRMED & NOT CHECKED-IN -> Check-in Guest */}
            {status === 'CONFIRMED' && !isCheckedIn && (
              <Button size="sm" variant="success" onClick={() => handleCheckIn(row.id)} icon={UserCheck}>
                Check-in khách đến
              </Button>
            )}

            {/* Step 3: CHECKED-IN -> Start Service */}
            {status === 'CONFIRMED' && isCheckedIn && (
              <Button size="sm" variant="primary" onClick={() => handleStartAppointment(row.id)} icon={Sparkles}>
                Bắt đầu trị liệu
              </Button>
            )}

            {/* Step 4: IN_PROGRESS -> Complete */}
            {status === 'IN_PROGRESS' && (
              <Button size="sm" variant="success" onClick={() => handleCompleteAppointment(row)} icon={CheckCircle2}>
                Hoàn tất buổi
              </Button>
            )}

            {/* Step 5: COMPLETED -> Cashier Checkout */}
            {status === 'COMPLETED' && !row.orderId && (
              <Button size="sm" variant="primary" onClick={() => {
                setCheckoutAppointment(row);
                setPaymentMethod('CASH');
                setCashReceived('');
                setCheckoutModalOpen(true);
              }} icon={DollarSign}>
                Thu tiền quầy
              </Button>
            )}

            {/* Secondary actions dropdown */}
            {(status === 'PENDING' || status === 'CONFIRMED') && (
              <Button size="sm" variant="ghost" onClick={() => handleOpenReschedule(row)}>
                Dời giờ
              </Button>
            )}

            {status === 'CONFIRMED' && !isCheckedIn && (
              <Button size="sm" variant="ghost" onClick={() => handleMarkNoShow(row)}>
                No-show
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="content-container">
      {/* 1. Page Header */}
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 className="page-title" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <UserCheck size={28} color="var(--c-primary, #D45D79)" />
            <span>Tiếp Đón & Quầy Lễ Tân Spa</span>
          </h1>
          <p className="page-subtitle">
            Tiếp đón khách hàng, phân công chuyên viên và xử lý lịch hẹn tại quầy.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
          <Button
            variant="primary"
            icon={Plus}
            onClick={() => {
              if (services.length > 0) {
                setWalkInForm(prev => ({ ...prev, serviceId: String(services[0].id) }));
              }
              setWalkInModalOpen(true);
            }}
          >
            Tiếp đón khách vãng lai
          </Button>
          <Button
            variant="secondary"
            icon={RefreshCw}
            loading={loading}
            onClick={fetchAppointments}
          >
            Làm mới
          </Button>
        </div>
      </div>

      {/* 2. Today Reception KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', marginBottom: '20px' }}>
        <div className="stat-card" style={{ padding: '14px 16px', borderLeft: '4px solid var(--c-primary, #D45D79)' }}>
          <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', fontWeight: 600 }}>Tổng lịch hẹn</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#1F2937', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
            {stats.total}
          </div>
        </div>

        <div className="stat-card" style={{ padding: '14px 16px', borderLeft: '4px solid #059669' }}>
          <div style={{ fontSize: '12px', color: '#059669', fontWeight: 600 }}>Đã check-in / Đang làm</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#059669', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
            {stats.checkedInOrInProgress}
          </div>
        </div>

        <div className="stat-card" style={{ padding: '14px 16px', borderLeft: '4px solid #D97706' }}>
          <div style={{ fontSize: '12px', color: '#D97706', fontWeight: 600 }}>Chờ tiếp đón</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#D97706', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
            {stats.waiting}
          </div>
        </div>

        <div className="stat-card" style={{ padding: '14px 16px', borderLeft: '4px solid #2563EB' }}>
          <div style={{ fontSize: '12px', color: '#2563EB', fontWeight: 600 }}>Đã hoàn tất</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: '#2563EB', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
            {stats.completed}
          </div>
        </div>
      </div>

      {/* 3. Filter & Search Toolbar */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
          {/* Quick Date Buttons */}
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
            <Button
              size="sm"
              variant={dateFilter === new Date().toISOString().split('T')[0] ? 'primary' : 'outline'}
              onClick={() => setDateFilter(new Date().toISOString().split('T')[0])}
            >
              Hôm nay
            </Button>
            <Button
              size="sm"
              variant={dateFilter === new Date(Date.now() + 86400000).toISOString().split('T')[0] ? 'primary' : 'outline'}
              onClick={() => setDateFilter(new Date(Date.now() + 86400000).toISOString().split('T')[0])}
            >
              Ngày mai
            </Button>
            <Button
              size="sm"
              variant={dateFilter === 'ALL' ? 'primary' : 'outline'}
              onClick={() => setDateFilter('ALL')}
            >
              Tất cả ngày
            </Button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '12px', color: 'var(--c-text-muted)' }}>Ngày cụ thể:</span>
            <input
              type="date"
              value={dateFilter === 'ALL' ? '' : dateFilter}
              onChange={(e) => setDateFilter(e.target.value || 'ALL')}
              style={{
                padding: '6px 10px',
                borderRadius: '6px',
                border: '1px solid var(--c-border)',
                fontSize: '12.5px',
                fontFamily: 'var(--font-mono)',
                outline: 'none'
              }}
            />
          </div>

          {/* Status Filter */}
          <div style={{ minWidth: '150px' }}>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái' },
                { value: 'PENDING', label: 'Chờ duyệt' },
                { value: 'CONFIRMED', label: 'Đã xác nhận' },
                { value: 'IN_PROGRESS', label: 'Đang thực hiện' },
                { value: 'COMPLETED', label: 'Đã hoàn tất' },
                { value: 'NO_SHOW', label: 'Vắng mặt (No-show)' },
                { value: 'CANCELLED', label: 'Đã hủy' },
              ]}
            />
          </div>

          {/* Quick Search */}
          <div style={{ flex: 1, minWidth: '220px', position: 'relative' }}>
            <Input
              placeholder="Tìm theo Tên khách, Số điện thoại, Mã vé SPA-..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              icon={Search}
            />
          </div>
        </div>
      </div>

      {/* 4. Reception Appointments Table */}
      <div className="card">
        <DataTable
          columns={columns}
          data={filteredAppointments}
          loading={loading}
          emptyMessage="Không có lịch hẹn Spa nào phù hợp với bộ lọc tiếp đón hiện tại."
        />
      </div>

      {/* ================= MODAL 1: WALK-IN BOOKING ================= */}
      <Modal
        isOpen={walkInModalOpen}
        onClose={() => setWalkInModalOpen(false)}
        title="Tiếp Đón Khách Vãng Lai / Đặt Lịch Tại Quầy"
        maxWidth="600px"
      >
        <form onSubmit={handleWalkInSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <Input
              label="Họ và tên khách hàng *"
              required
              placeholder="Ví dụ: Nguyễn Văn A"
              value={walkInForm.customerName}
              onChange={(e) => setWalkInForm({ ...walkInForm, customerName: e.target.value })}
            />
            <Input
              label="Số điện thoại liên hệ *"
              required
              placeholder="Ví dụ: 0901234567"
              value={walkInForm.phone}
              onChange={(e) => setWalkInForm({ ...walkInForm, phone: e.target.value })}
            />
          </div>

          <Select
            label="Chọn Dịch vụ / Liệu trình *"
            required
            value={walkInForm.serviceId}
            onChange={(e) => setWalkInForm({ ...walkInForm, serviceId: e.target.value })}
            options={services.map(s => ({
              value: String(s.id),
              label: `${s.name} - ${formatCurrency(s.basePrice || s.price || 0)} (${s.durationMinutes || 60}p)`
            }))}
          />

          <Select
            label="Bác sĩ / Kỹ thuật viên phụ trách"
            value={walkInForm.staffId}
            onChange={(e) => setWalkInForm({ ...walkInForm, staffId: e.target.value })}
            options={[
              { value: '', label: '-- Lễ tân tự điều phối KTV rảnh --' },
              ...staffList.map(st => ({
                value: String(st.id),
                label: `${st.fullName || st.username || `KTV #${st.id}`} - ${st.specialty || 'Chuyên viên'}`
              }))
            ]}
          />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <Input
              type="date"
              label="Ngày thực hiện"
              value={walkInForm.appointmentDate}
              onChange={(e) => setWalkInForm({ ...walkInForm, appointmentDate: e.target.value })}
            />
            <Select
              label="Giờ bắt đầu"
              value={walkInForm.startTime}
              onChange={(e) => setWalkInForm({ ...walkInForm, startTime: e.target.value })}
              options={[
                { value: '08:30:00', label: '08:30' },
                { value: '09:00:00', label: '09:00' },
                { value: '09:30:00', label: '09:30' },
                { value: '10:00:00', label: '10:00' },
                { value: '10:30:00', label: '10:30' },
                { value: '11:00:00', label: '11:00' },
                { value: '13:30:00', label: '13:30' },
                { value: '14:00:00', label: '14:00' },
                { value: '14:30:00', label: '14:30' },
                { value: '15:00:00', label: '15:00' },
                { value: '16:00:00', label: '16:00' },
                { value: '17:00:00', label: '17:00' },
                { value: '18:00:00', label: '18:00' },
              ]}
            />
          </div>

          <Input
            label="Ghi chú da liễu / Yêu cầu riêng"
            placeholder="Ví dụ: Da kích ứng nhẹ, khách muốn làm phòng yên tĩnh..."
            value={walkInForm.notes}
            onChange={(e) => setWalkInForm({ ...walkInForm, notes: e.target.value })}
          />

          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', cursor: 'pointer', padding: '8px 12px', backgroundColor: '#F0FDF4', borderRadius: '6px', border: '1px solid #BBF7D0' }}>
            <input
              type="checkbox"
              checked={walkInForm.autoCheckIn}
              onChange={(e) => setWalkInForm({ ...walkInForm, autoCheckIn: e.target.checked })}
            />
            <span style={{ fontWeight: 600, color: '#166534' }}>Tự động ghi nhận Check-in khách đã có mặt tại sảnh</span>
          </label>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
            <Button variant="secondary" onClick={() => setWalkInModalOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" type="submit" loading={submittingWalkIn}>
              Xác nhận tiếp đón
            </Button>
          </div>
        </form>
      </Modal>

      {/* ================= MODAL 2: CASHIER CHECKOUT ================= */}
      <Modal
        isOpen={checkoutModalOpen}
        onClose={() => setCheckoutModalOpen(false)}
        title="Thu Ngân & Hoá Đơn Thanh Toán Tại Quầy"
        maxWidth="540px"
      >
        {checkoutAppointment && (
          <form onSubmit={handleCashierCheckout} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ padding: '14px 16px', backgroundColor: '#FAF7F8', borderRadius: '8px', border: '1px solid var(--c-border)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ fontSize: '12px', color: 'var(--c-text-muted)' }}>Khách hàng:</span>
                <strong style={{ fontSize: '13.5px', color: '#1F2937' }}>{checkoutAppointment.customerName}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ fontSize: '12px', color: 'var(--c-text-muted)' }}>Dịch vụ hoàn tất:</span>
                <span style={{ fontSize: '13px', fontWeight: 600 }}>{checkoutAppointment.items?.[0]?.serviceName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px dashed var(--c-border)', paddingTop: '8px', marginTop: '8px' }}>
                <strong style={{ fontSize: '13px', color: '#1F2937' }}>Số tiền phải thu:</strong>
                <strong style={{ fontSize: '18px', color: 'var(--c-deal-red, #E11D48)', fontFamily: 'var(--font-mono)' }}>
                  {formatCurrency(checkoutAppointment.items?.reduce((s, i) => s + Number(i.price || 0), 0) || 0)}
                </strong>
              </div>
            </div>

            <Select
              label="Phương thức thanh toán *"
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value)}
              options={[
                { value: 'CASH', label: 'Tiền mặt tại quầy (Cash)' },
                { value: 'BANK', label: 'Chuyển khoản VietQR / SePay' },
              ]}
            />

            {paymentMethod === 'CASH' && (
              <Input
                label="Tiền khách đưa (VND)"
                type="number"
                placeholder="Ví dụ: 500000"
                value={cashReceived}
                onChange={(e) => setCashReceived(e.target.value)}
              />
            )}

            {paymentMethod === 'CASH' && cashReceived && Number(cashReceived) >= (checkoutAppointment.items?.[0]?.price || 0) && (
              <div style={{ padding: '10px 14px', backgroundColor: '#ECFDF5', borderRadius: '6px', fontSize: '13px', color: '#065F46' }}>
                Tiền thối lại khách: <strong>{formatCurrency(Number(cashReceived) - Number(checkoutAppointment.items?.[0]?.price || 0))}</strong>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
              <Button variant="secondary" onClick={() => setCheckoutModalOpen(false)}>
                Để sau
              </Button>
              <Button variant="primary" type="submit" loading={checkoutSubmitting}>
                Xác nhận thu tiền & Xuất hoá đơn
              </Button>
            </div>
          </form>
        )}
      </Modal>

      {/* ================= MODAL 3: ASSIGN STAFF ================= */}
      <Modal
        isOpen={assignModalOpen}
        onClose={() => setAssignModalOpen(false)}
        title="Xác Nhận & Phân Công Chuyên Viên Phụ Trách"
        maxWidth="500px"
      >
        {assignAppointment && (
          <form onSubmit={handleConfirmAssignment} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ fontSize: '13px', color: 'var(--c-text-muted)' }}>
              Lịch hẹn của: <strong style={{ color: '#1F2937' }}>{assignAppointment.customerName}</strong> ({assignAppointment.appointmentDate} lúc {String(assignAppointment.startTime).slice(0, 5)})
            </div>

            <Select
              label="Chọn Bác sĩ / Kỹ thuật viên phụ trách *"
              required
              value={assignedStaffId}
              onChange={(e) => setAssignedStaffId(e.target.value)}
              options={staffList.map(st => ({
                value: String(st.id),
                label: `${st.fullName || st.username || `KTV #${st.id}`} - ${st.specialty || 'Chuyên viên'}`
              }))}
            />

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
              <Button variant="secondary" onClick={() => setAssignModalOpen(false)}>
                Hủy
              </Button>
              <Button variant="primary" type="submit" loading={submittingAssign}>
                Xác nhận lịch hẹn
              </Button>
            </div>
          </form>
        )}
      </Modal>

      {/* ================= MODAL 4: RESCHEDULE ================= */}
      <Modal
        isOpen={rescheduleModalOpen}
        onClose={() => setRescheduleModalOpen(false)}
        title="Dời Khung Giờ Lịch Hẹn"
        maxWidth="480px"
      >
        {rescheduleAppointment && (
          <form onSubmit={handleConfirmReschedule} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <Input
              type="date"
              label="Ngày hẹn mới *"
              required
              value={newDate}
              onChange={(e) => setNewDate(e.target.value)}
            />

            <Select
              label="Giờ hẹn mới *"
              value={newTime}
              onChange={(e) => setNewTime(e.target.value)}
              options={[
                { value: '08:30:00', label: '08:30' },
                { value: '09:00:00', label: '09:00' },
                { value: '09:30:00', label: '09:30' },
                { value: '10:00:00', label: '10:00' },
                { value: '10:30:00', label: '10:30' },
                { value: '11:00:00', label: '11:00' },
                { value: '13:30:00', label: '13:30' },
                { value: '14:00:00', label: '14:00' },
                { value: '14:30:00', label: '14:30' },
                { value: '15:00:00', label: '15:00' },
                { value: '16:00:00', label: '16:00' },
                { value: '17:00:00', label: '17:00' },
                { value: '18:00:00', label: '18:00' },
              ]}
            />

            <Input
              label="Lý do dời lịch"
              placeholder="Khách kẹt xe xin lùi giờ, khách đổi ca..."
              value={rescheduleNote}
              onChange={(e) => setRescheduleNote(e.target.value)}
            />

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
              <Button variant="secondary" onClick={() => setRescheduleModalOpen(false)}>
                Hủy
              </Button>
              <Button variant="primary" type="submit" loading={submittingReschedule}>
                Xác nhận dời lịch
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
};

export default SpaReceptionPage;
