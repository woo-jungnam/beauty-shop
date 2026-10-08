import React, { useState, useEffect, useMemo } from 'react';
import {
  Calendar,
  Clock,
  User,
  Sparkles,
  ArrowRight,
  QrCode,
  Check,
  ShieldCheck,
  Award,
  Stethoscope,
  Copy,
  Building2,
  Ticket,
  AlertCircle,
  Phone,
  Mail,
  FileText,
  Sun,
  Moon,
  Star,
  ChevronRight,
  Info
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { useAuth } from '../../app/providers/AuthProvider';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

const getSpaServiceImage = (service) => {
  if (service?.thumbnailUrl) return service.thumbnailUrl;
  const name = (service?.name || '').toLowerCase();
  const cat = (service?.categoryName || '').toLowerCase();

  if (name.includes('aqua peel') || name.includes('làm sạch') || name.includes('peel') || cat.includes('làm sạch'))
    return 'https://images.unsplash.com/photo-1570554520913-ce2a90ccc23b?auto=format&fit=crop&w=700&q=80';
  if (name.includes('mụn') || name.includes('acne') || name.includes('bio-light') || name.includes('vi khuẩn'))
    return 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=700&q=80';
  if (name.includes('nước') || name.includes('vitamin') || name.includes('niacinamide') || name.includes('sáng da'))
    return 'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=700&q=80';
  if (name.includes('hifu') || name.includes('nâng cơ') || name.includes('v-line'))
    return 'https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=700&q=80';
  if (name.includes('galvanic') || name.includes('điện di') || name.includes('ion'))
    return 'https://images.unsplash.com/photo-1600428877878-1a0fcc0ba376?auto=format&fit=crop&w=700&q=80';
  if (name.includes('massage') || name.includes('body'))
    return 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=700&q=80';

  const spaPool = [
    'https://images.unsplash.com/photo-1570554520913-ce2a90ccc23b?auto=format&fit=crop&w=700&q=80',
    'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=700&q=80',
    'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=700&q=80',
    'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=700&q=80',
  ];
  return spaPool[(service?.id || 0) % spaPool.length];
};

const PRESET_SKIN_TAGS = [
  'Da dầu nhờn vùng chữ T',
  'Mụn sưng viêm / Mụn cám',
  'Da nhạy cảm, dễ đỏ rát',
  'Đang dùng Retinol / BHA',
  'Da sạm màu, thâm mụn',
  'Phụ nữ mang thai / Cho con bú',
];

export const SpaBookingPage = ({ onNavigate, preselectedServiceId }) => {
  const { user, isAuthenticated } = useAuth();
  const [services, setServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(true);
  const [activeCategoryFilter, setActiveCategoryFilter] = useState('ALL');

  // Form Booking State
  const [selectedService, setSelectedService] = useState(null);
  const [selectedStaff, setSelectedStaff] = useState('AUTO');
  const [qualifiedStaff, setQualifiedStaff] = useState([]);
  const [availableSlots, setAvailableSlots] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [myActiveTickets, setMyActiveTickets] = useState([]);
  const [selectedTicketId, setSelectedTicketId] = useState(null);
  const [selectedDate, setSelectedDate] = useState(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });
  const [selectedSlot, setSelectedSlot] = useState('');
  const [selectedSkinTags, setSelectedSkinTags] = useState([]);
  const [customerInfo, setCustomerInfo] = useState({
    fullName: '',
    phone: '',
    email: '',
    skinNote: '',
  });

  const [bookingResult, setBookingResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState(null);
  const [copiedCode, setCopiedCode] = useState(false);

  // Auto-fill from authenticated user
  useEffect(() => {
    if (user) {
      setCustomerInfo((prev) => ({
        ...prev,
        fullName: prev.fullName || user.fullName || '',
        phone: prev.phone || user.phone || '',
        email: prev.email || user.email || '',
      }));
    }
  }, [user]);

  // Load active user tickets
  useEffect(() => {
    if (!isAuthenticated) {
      setMyActiveTickets([]);
      setSelectedTicketId(null);
      return;
    }
    let isCancelled = false;
    const loadTickets = async () => {
      try {
        const res = await apiClient.get(ENDPOINTS.SPA.MY_ACTIVE_TICKETS || '/api/v1/spa/tickets/my-active-tickets');
        const list = res?.data || res || [];
        if (!isCancelled) {
          setMyActiveTickets(Array.isArray(list) ? list : []);
        }
      } catch (err) {
        console.warn('Failed to load active tickets', err);
      }
    };
    loadTickets();
    return () => { isCancelled = true; };
  }, [isAuthenticated]);

  // Reset ticket if not applicable for selected service
  useEffect(() => {
    if (selectedTicketId && selectedService?.id) {
      const ticket = myActiveTickets.find((t) => t.id === selectedTicketId);
      if (!ticket || (ticket.remainingByService?.[selectedService.id] ?? 0) <= 0) {
        setSelectedTicketId(null);
      }
    }
  }, [selectedService?.id, myActiveTickets, selectedTicketId]);

  // Load Services from backend API
  useEffect(() => {
    const loadServices = async () => {
      setLoadingServices(true);
      try {
        const res = await apiClient.get(ENDPOINTS.SPA.PUBLIC_SERVICES || '/api/v1/spa/services');
        const list = res?.data || res || [];
        const validList = Array.isArray(list) ? list : [];
        setServices(validList);
        if (preselectedServiceId) {
          const matched = validList.find((s) => String(s.id) === String(preselectedServiceId));
          if (matched) setSelectedService(matched);
        } else if (validList.length > 0) {
          setSelectedService(validList[0]);
        }
      } catch (err) {
        console.error('Failed to load spa services', err);
      } finally {
        setLoadingServices(false);
      }
    };
    loadServices();
  }, [preselectedServiceId]);

  // Load qualified staff when selected service changes
  useEffect(() => {
    if (!selectedService?.id) return;
    let isCancelled = false;
    const loadStaff = async () => {
      try {
        const res = await apiClient.get(`/api/v1/spa/services/${selectedService.id}/staff`);
        let list = res?.data || res || [];
        list = Array.isArray(list) ? list : (list?.content || []);
        if (!list || list.length === 0) {
          try {
            const allRes = await apiClient.get(ENDPOINTS.SPA.PUBLIC_STAFF || '/api/v1/spa/services/staff');
            const allData = allRes?.data || allRes || [];
            list = Array.isArray(allData) ? allData : (allData?.content || []);
          } catch (_) {}
        }
        if (!isCancelled) {
          setQualifiedStaff(Array.isArray(list) ? list : []);
        }
      } catch (e) {
        console.warn('Failed to load staff for service', e);
      }
    };
    loadStaff();
    return () => { isCancelled = true; };
  }, [selectedService?.id]);

  // Load available time slots from backend
  useEffect(() => {
    if (!selectedService?.id || !selectedDate) return;
    let isCancelled = false;
    const loadSlots = async () => {
      setLoadingSlots(true);
      try {
        const staffParam = selectedStaff !== 'AUTO' ? `&staffId=${encodeURIComponent(selectedStaff)}` : '';
        const res = await apiClient.get(`/api/v1/spa/services/${selectedService.id}/available-slots?date=${encodeURIComponent(selectedDate)}${staffParam}`);
        const slots = res?.data || res || [];
        if (!isCancelled) {
          if (Array.isArray(slots) && slots.length > 0) {
            setAvailableSlots(slots);
            setSelectedSlot((prev) => (slots.includes(prev) ? prev : slots[0]));
          } else {
            setAvailableSlots([]);
            setSelectedSlot('');
          }
        }
      } catch (e) {
        console.warn('Failed to load available slots', e);
        if (!isCancelled) {
          setAvailableSlots([]);
          setSelectedSlot('');
        }
      } finally {
        if (!isCancelled) setLoadingSlots(false);
      }
    };
    loadSlots();
    return () => { isCancelled = true; };
  }, [selectedService?.id, selectedDate, selectedStaff]);

  // Categories list
  const serviceCategories = useMemo(() => {
    const set = new Set();
    services.forEach(s => {
      if (s.categoryName) set.add(s.categoryName);
      else if (s.category) set.add(s.category);
    });
    return ['ALL', ...Array.from(set)];
  }, [services]);

  const filteredServices = useMemo(() => {
    if (activeCategoryFilter === 'ALL') return services;
    return services.filter(s => (s.categoryName || s.category) === activeCategoryFilter);
  }, [services, activeCategoryFilter]);

  const getQuickDates = () => {
    const dates = [];
    const days = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
    for (let i = 0; i < 4; i++) {
      const d = new Date();
      d.setDate(d.getDate() + i);
      const iso = d.toISOString().split('T')[0];
      const label = i === 0 ? 'Hôm nay' : i === 1 ? 'Ngày mai' : days[d.getDay()];
      const dayMonth = `${d.getDate()}/${d.getMonth() + 1}`;
      dates.push({ iso, label, dayMonth });
    }
    return dates;
  };

  const morningSlots = useMemo(() => {
    return availableSlots.filter(s => {
      const hour = parseInt(s.split(':')[0], 10);
      return hour < 12;
    });
  }, [availableSlots]);

  const afternoonSlots = useMemo(() => {
    return availableSlots.filter(s => {
      const hour = parseInt(s.split(':')[0], 10);
      return hour >= 12;
    });
  }, [availableSlots]);

  const toggleSkinTag = (tag) => {
    setSelectedSkinTags(prev => {
      const exists = prev.includes(tag);
      const next = exists ? prev.filter(t => t !== tag) : [...prev, tag];
      setCustomerInfo(c => ({
        ...c,
        skinNote: next.join(', ')
      }));
      return next;
    });
  };

  const handleCopyTicket = (code) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleConfirmBooking = async (e) => {
    if (e) e.preventDefault();
    setBookingError(null);

    if (!isAuthenticated) {
      alert('Vui lòng đăng nhập tài khoản để xác nhận và quản lý lịch hẹn.');
      if (typeof onNavigate === 'function') onNavigate('login');
      return;
    }
    if (!selectedService?.id) {
      alert('Vui lòng chọn một liệu trình Spa.');
      return;
    }
    if (!selectedSlot) {
      alert('Vui lòng chọn khung giờ hẹn khả dụng.');
      return;
    }
    if (!customerInfo.fullName.trim() || !customerInfo.phone.trim()) {
      alert('Vui lòng điền đầy đủ Họ tên và Số điện thoại liên hệ.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        appointmentDate: selectedDate,
        startTime: selectedSlot.length === 5 ? `${selectedSlot}:00` : selectedSlot,
        notes: customerInfo.skinNote?.trim() || undefined,
        items: [
          {
            serviceId: selectedService.id,
            staffId: selectedStaff !== 'AUTO' ? Number(selectedStaff) : null,
            ticketId: selectedTicketId ? Number(selectedTicketId) : null,
          }
        ]
      };
      const res = await apiClient.post(ENDPOINTS.SPA.BOOK_APPOINTMENT || '/api/v1/appointments/book', payload);
      const createdAppointment = res?.data || res;
      if (!createdAppointment || !createdAppointment.id) {
        throw new Error('Máy chủ không trả về thông tin lịch hẹn hợp lệ.');
      }

      const effectivePrice = selectedTicketId ? 0 : (selectedService.basePrice ?? selectedService.price ?? 0);
      const assignedStaffName = selectedStaff === 'AUTO'
        ? 'Chuyên viên / Bác sĩ chỉ định'
        : (qualifiedStaff.find((s) => String(s.id) === String(selectedStaff))?.fullName || `Chuyên viên #${selectedStaff}`);

      setBookingResult({
        ticketCode: `SPA-${createdAppointment.id}`,
        appointmentId: createdAppointment.id,
        status: createdAppointment.status || 'PENDING',
        serviceName: selectedService.name,
        duration: selectedService.durationMinutes || 60,
        price: effectivePrice,
        isTicketUsed: Boolean(selectedTicketId),
        ticketId: selectedTicketId,
        date: selectedDate,
        time: selectedSlot,
        staff: assignedStaffName,
        customerName: customerInfo.fullName || user?.fullName || 'Khách hàng',
        phone: customerInfo.phone || user?.phone || '',
        email: customerInfo.email || user?.email || '',
        branch: 'Chi nhánh 1: 123 Đồng Khởi, P. Bến Nghé, Quận 1, TP. Hồ Chí Minh'
      });
      window.scrollTo({ top: 0, behavior: 'instant' });
    } catch (err) {
      console.error('Booking failed:', err);
      const msg = err.response?.data?.message || err.message || 'Không thể đặt lịch hẹn. Vui lòng kiểm tra lại thời gian hoặc tài khoản.';
      setBookingError(msg);
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // If already booked, show the Digital VIP Boarding Pass ticket
  if (bookingResult) {
    return (
      <div className="spa-booking-wide-layout" style={{ maxWidth: '680px' }}>
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 0,
          border: '1px solid var(--c-border, #E5E7EB)',
          padding: '36px 28px',
          boxShadow: 'none',
          textAlign: 'center'
        }}>
          <div style={{
            width: '56px',
            height: '56px',
            borderRadius: 0,
            backgroundColor: '#ECFDF5',
            color: '#059669',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px auto',
            border: '1px solid #A7F3D0'
          }}>
            <Check size={32} strokeWidth={2} />
          </div>

          <h2 style={{ fontSize: '22px', margin: '0 0 8px 0', fontFamily: 'sans-serif', fontWeight: 700, color: 'var(--c-primary, #8C2A47)' }}>
            Đặt lịch hẹn Spa thành công
          </h2>
          <p style={{ fontSize: '13.5px', color: 'var(--c-text-muted)', margin: '0 0 28px 0', lineHeight: 1.5 }}>
            Phiếu giữ chỗ điện tử đã được ghi nhận. Vui lòng xuất trình mã QR bên dưới khi đến phòng khám để check-in:
          </p>

          {/* Boarding Pass Ticket */}
          <div style={{
            margin: '0 auto 32px auto',
            backgroundColor: '#FAFAFA',
            borderRadius: 0,
            border: '1px solid var(--c-border)',
            overflow: 'hidden',
            boxShadow: 'none'
          }}>
            <div style={{
              backgroundColor: 'var(--c-primary, #8C2A47)',
              color: '#FFFFFF',
              padding: '14px 20px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              borderRadius: 0
            }}>
              <div>
                <div style={{ fontSize: '10px', letterSpacing: '0.08em', fontWeight: 700, opacity: 0.9 }}>BEAUTY CLINIC & SPA</div>
                <div style={{ fontSize: '15px', fontWeight: 700 }}>PHIẾU KHÁM ĐIỆN TỬ</div>
              </div>
              <span style={{ border: '1px solid rgba(255,255,255,0.4)', padding: '3px 8px', fontSize: '11px', fontWeight: 600 }}>
                CHECK-IN
              </span>
            </div>

            <div style={{ padding: '24px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginBottom: '16px' }}>
                <span style={{ fontSize: '12.5px', color: 'var(--c-text-light)', fontWeight: 600 }}>Mã vé giữ chỗ:</span>
                <strong style={{ fontSize: '24px', color: '#9F1239', fontFamily: 'monospace' }}>
                  {bookingResult.ticketCode}
                </strong>
                <button
                  onClick={() => handleCopyTicket(bookingResult.ticketCode)}
                  title="Sao chép mã vé"
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--c-primary)', padding: '4px' }}
                >
                  {copiedCode ? <Check size={18} color="#059669" /> : <Copy size={18} />}
                </button>
              </div>

              {/* QR Container */}
              <div style={{
                width: '150px',
                height: '150px',
                backgroundColor: '#FFFFFF',
                margin: '0 auto 20px auto',
                padding: '10px',
                borderRadius: 0,
                border: '1px solid var(--c-border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: 'none'
              }}>
                <QrCode size={130} color="#1F2937" />
              </div>

              {/* Details List */}
              <div style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 0,
                padding: '16px',
                fontSize: '13px',
                lineHeight: 1.6,
                textAlign: 'left',
                border: '1px solid var(--c-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px'
              }}>
                <div>Liệu trình: <strong style={{ color: '#1F2937' }}>{bookingResult.serviceName}</strong> ({bookingResult.duration} phút)</div>
                <div>Thời gian: <strong style={{ color: 'var(--c-primary)' }}>{bookingResult.time} ngày {bookingResult.date}</strong></div>
                <div>Khách hàng: <strong>{bookingResult.customerName}</strong> ({bookingResult.phone})</div>
                <div>Chuyên viên: <strong>{bookingResult.staff}</strong></div>
                <div>Trạng thái: <strong style={{ color: '#92400E' }}>{bookingResult.status === 'PENDING' ? 'Chờ xác nhận' : bookingResult.status}</strong></div>
                <div>Chi nhánh: <strong style={{ color: '#1F2937' }}>{bookingResult.branch}</strong></div>
                <div style={{ borderTop: '1px solid var(--c-border)', paddingTop: '10px', marginTop: '4px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>{bookingResult.isTicketUsed ? 'Hình thức:' : 'Chi phí tại quầy:'}</span>
                  <strong style={{ fontSize: '15px', color: bookingResult.isTicketUsed ? '#059669' : '#9F1239', fontFamily: 'monospace' }}>
                    {bookingResult.isTicketUsed ? `0 ₫ (Vé liệu trình #${bookingResult.ticketId})` : formatCurrency(bookingResult.price)}
                  </strong>
                </div>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', flexWrap: 'wrap' }}>
            <button onClick={() => onNavigate('')} className="btn-luxury-outline" style={{ padding: '10px 24px', fontSize: '13px', borderRadius: 0 }}>
              Về Trang Chủ
            </button>
            <button onClick={() => onNavigate('profile?tab=appointments')} className="btn-luxury-primary" style={{ padding: '10px 24px', fontSize: '13px', borderRadius: 0 }}>
              Xem Lịch Hẹn Của Tôi
            </button>
          </div>
        </div>
      </div>
    );
  }

  const applicableTicket = myActiveTickets.find((t) => (t.remainingByService?.[selectedService?.id] ?? 0) > 0);

  return (
    <div className="spa-booking-wide-layout">

      {/* 1. Page Header & Clinic Brand Assurance */}
      <div style={{ textAlign: 'center', marginBottom: '32px' }}>
        <h1 style={{
          fontSize: 'clamp(24px, 3.2vw, 32px)',
          margin: '0 0 10px 0',
          fontFamily: 'sans-serif',
          fontWeight: 700,
          color: '#111827',
          letterSpacing: '-0.02em'
        }}>
          Đặt lịch khám và trị liệu da liễu chuẩn y khoa
        </h1>

        <p style={{
          fontSize: '14px',
          color: 'var(--c-text-muted)',
          maxWidth: '780px',
          margin: '0 auto 18px auto',
          lineHeight: 1.6
        }}>
          Phác đồ điều trị 1:1 cùng Bác sĩ Da liễu, quy trình vô trùng khép kín, giữ chỗ phòng khám riêng biệt không chờ đợi.
        </p>

        {/* Top Assurance Strip - Flat, No radius, No shadow */}
        <div style={{
          display: 'inline-flex',
          justifyContent: 'center',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '14px 24px',
          fontSize: '12.5px',
          color: '#374151',
          padding: '10px 20px',
          backgroundColor: '#FFFFFF',
          borderRadius: 0,
          border: '1px solid var(--c-border)',
          boxShadow: 'none'
        }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600 }}>
            <Stethoscope size={15} color="var(--c-primary)" />
            Soi da 3D và tư vấn 1:1
          </span>
          <span style={{ color: '#D1D5DB' }}>|</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600 }}>
            <Award size={15} color="var(--c-primary)" />
            Quy trình chuẩn hóa FDA
          </span>
          <span style={{ color: '#D1D5DB' }}>|</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600 }}>
            <ShieldCheck size={15} color="var(--c-primary)" />
            Thanh toán tại quầy
          </span>
        </div>
      </div>

      {/* 2. Main 80% Wide 2-Column Consolidated Booking Layout */}
      <div className="spa-booking-grid-layout">

        {/* LEFT COLUMN: Layered Structured Sections */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

          {/* ================= SECTION 1: CHỌN DỊCH VỤ ================= */}
          <section style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 0,
            border: '1px solid var(--c-border)',
            padding: '24px',
            boxShadow: 'none'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '18px' }}>
              <div style={{
                width: '30px',
                height: '30px',
                borderRadius: 0,
                backgroundColor: 'var(--c-primary)',
                color: '#FFFFFF',
                fontWeight: 700,
                fontSize: '14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                1
              </div>
              <div>
                <h2 style={{ fontSize: '16.5px', fontWeight: 700, margin: 0, color: '#111827' }}>
                  Chọn liệu trình điều trị và chăm sóc da
                </h2>
                <div style={{ fontSize: '12.5px', color: 'var(--c-text-muted)' }}>
                  Danh mục dịch vụ thẩm mỹ y khoa chuyên sâu theo từng loại da
                </div>
              </div>
            </div>

            {/* Category Filter Pills - Flat Rectangular */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '16px' }}>
              {serviceCategories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setActiveCategoryFilter(cat)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 0,
                    fontSize: '12px',
                    fontWeight: activeCategoryFilter === cat ? 700 : 500,
                    cursor: 'pointer',
                    border: activeCategoryFilter === cat ? '1px solid var(--c-primary)' : '1px solid var(--c-border)',
                    backgroundColor: activeCategoryFilter === cat ? 'var(--c-primary)' : '#FFFFFF',
                    color: activeCategoryFilter === cat ? '#FFFFFF' : '#374151',
                    boxShadow: 'none'
                  }}
                >
                  {cat === 'ALL' ? 'Tất cả dịch vụ' : cat}
                </button>
              ))}
            </div>

            {/* Service Cards Grid - Flat, No Radius, No Shadow */}
            {loadingServices ? (
              <div style={{ padding: '40px', textAlign: 'center', color: 'var(--c-text-muted)' }}>
                Đang tải danh sách dịch vụ Spa...
              </div>
            ) : (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
                gap: '14px'
              }}>
                {filteredServices.map((s) => {
                  const isSelected = selectedService?.id === s.id;
                  const hasTicket = myActiveTickets.some((t) => (t.remainingByService?.[s.id] ?? 0) > 0);

                  return (
                    <div
                      key={s.id}
                      onClick={() => setSelectedService(s)}
                      style={{
                        borderRadius: 0,
                        border: isSelected ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                        backgroundColor: isSelected ? '#FFF8F9' : '#FFFFFF',
                        cursor: 'pointer',
                        overflow: 'hidden',
                        display: 'flex',
                        flexDirection: 'column',
                        position: 'relative',
                        boxShadow: 'none'
                      }}
                    >
                      {/* Image Preview Box */}
                      <div style={{ position: 'relative', width: '100%', height: '140px', overflow: 'hidden' }}>
                        <img
                          src={getSpaServiceImage(s)}
                          alt={s.name}
                          style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover'
                          }}
                        />

                        {/* Top Category Label */}
                        <div style={{
                          position: 'absolute',
                          top: '8px',
                          left: '8px',
                          backgroundColor: '#18181B',
                          color: '#FFFFFF',
                          padding: '3px 7px',
                          fontSize: '10.5px',
                          fontWeight: 600,
                          borderRadius: 0
                        }}>
                          {s.categoryName || s.category || 'Spa Clinic'}
                        </div>

                        {/* Top Duration Label */}
                        <div style={{
                          position: 'absolute',
                          top: '8px',
                          right: '8px',
                          backgroundColor: '#18181B',
                          color: '#FFFFFF',
                          padding: '3px 7px',
                          fontSize: '11px',
                          fontWeight: 600,
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                          borderRadius: 0
                        }}>
                          <Clock size={11} />
                          <span>{s.durationMinutes || 60} phút</span>
                        </div>
                      </div>

                      {/* Card Content */}
                      <div style={{ padding: '14px', display: 'flex', flexDirection: 'column', flex: 1, justifyContent: 'space-between' }}>
                        <div>
                          <strong style={{ fontSize: '14.5px', color: '#111827', display: 'block', lineHeight: 1.35, marginBottom: '4px' }}>
                            {s.name}
                          </strong>

                          <p style={{
                            fontSize: '12px',
                            color: 'var(--c-text-muted)',
                            margin: '0 0 10px 0',
                            lineHeight: 1.45,
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden'
                          }}>
                            {s.shortDescription || 'Quy trình chuẩn hóa khử khuẩn, làm sạch sâu tế bào sừng và phục hồi da.'}
                          </p>
                        </div>

                        {/* Card Footer: Price & Ticket */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--c-border)', paddingTop: '8px' }}>
                          <div>
                            <span style={{ fontSize: '11px', color: 'var(--c-text-light)', display: 'block' }}>Chi phí:</span>
                            <span style={{ fontSize: '15px', fontWeight: 700, color: '#9F1239', fontFamily: 'monospace' }}>
                              {formatCurrency(s.basePrice ?? s.price ?? 0)}
                            </span>
                          </div>

                          {hasTicket ? (
                            <span style={{ fontSize: '10.5px', color: '#065F46', fontWeight: 600, border: '1px solid #A7F3D0', padding: '2px 6px' }}>
                              Có vé liệu trình
                            </span>
                          ) : (
                            <span style={{ fontSize: '11.5px', fontWeight: 700, color: isSelected ? 'var(--c-primary)' : 'var(--c-text-muted)' }}>
                              {isSelected ? 'Đã chọn' : 'Chọn gói'}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* Smart Ticket Attachment Banner */}
            {applicableTicket && (
              <div style={{
                marginTop: '16px',
                padding: '12px 14px',
                backgroundColor: '#F0FDF4',
                border: '1px solid #86EFAC',
                borderRadius: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '10px'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Ticket size={16} color="#065F46" />
                  <div>
                    <div style={{ fontSize: '12.5px', fontWeight: 700, color: '#065F46' }}>
                      Có vé liệu trình cho dịch vụ này ({applicableTicket.remainingByService[selectedService?.id]} buổi còn lại)
                    </div>
                    <div style={{ fontSize: '11.5px', color: '#047857' }}>
                      Áp dụng vé sẽ giữ chỗ 1 buổi và miễn phí thanh toán tại quầy.
                    </div>
                  </div>
                </div>

                <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: 700, color: '#065F46', cursor: 'pointer', backgroundColor: '#FFFFFF', padding: '5px 12px', border: '1px solid #86EFAC' }}>
                  <input
                    type="checkbox"
                    checked={selectedTicketId === applicableTicket.id}
                    onChange={(e) => setSelectedTicketId(e.target.checked ? applicableTicket.id : null)}
                  />
                  <span>Áp dụng vé (0 ₫)</span>
                </label>
              </div>
            )}
          </section>

          {/* ================= SECTION 2: CHỌN CHUYÊN VIÊN ================= */}
          <section style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 0,
            border: '1px solid var(--c-border)',
            padding: '24px',
            boxShadow: 'none'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '18px' }}>
              <div style={{
                width: '30px',
                height: '30px',
                borderRadius: 0,
                backgroundColor: 'var(--c-primary)',
                color: '#FFFFFF',
                fontWeight: 700,
                fontSize: '14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                2
              </div>
              <div>
                <h2 style={{ fontSize: '16.5px', fontWeight: 700, margin: 0, color: '#111827' }}>
                  Lựa chọn Bác sĩ và Kỹ thuật viên phụ trách
                </h2>
                <div style={{ fontSize: '12.5px', color: 'var(--c-text-muted)' }}>
                  Chuyên viên đạt chứng chỉ y khoa và được phân ca chuyên môn
                </div>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '12px' }}>
              {/* Option: Auto Dispatch */}
              <div
                onClick={() => setSelectedStaff('AUTO')}
                style={{
                  padding: '16px',
                  borderRadius: 0,
                  border: selectedStaff === 'AUTO' ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                  backgroundColor: selectedStaff === 'AUTO' ? '#FFF8F9' : '#FFFFFF',
                  cursor: 'pointer',
                  textAlign: 'center',
                  boxShadow: 'none'
                }}
              >
                <div style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: 0,
                  backgroundColor: selectedStaff === 'AUTO' ? 'var(--c-primary)' : '#F4F4F5',
                  color: selectedStaff === 'AUTO' ? '#FFFFFF' : '#3F3F46',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 10px auto'
                }}>
                  <Sparkles size={22} />
                </div>

                <strong style={{ fontSize: '14px', display: 'block', color: '#111827', marginBottom: '2px' }}>
                  Hệ thống tự xếp
                </strong>
                <span style={{ fontSize: '11.5px', color: 'var(--c-text-muted)', display: 'block', marginBottom: '4px' }}>
                  Điều phối chuyên viên phù hợp
                </span>
                <span style={{ fontSize: '11px', color: 'var(--c-primary)', fontWeight: 600 }}>
                  Tối ưu thời gian
                </span>
              </div>

              {/* Specific Staff Cards */}
              {qualifiedStaff.map((st) => {
                const isSelected = selectedStaff === String(st.id);

                return (
                  <div
                    key={st.id}
                    onClick={() => setSelectedStaff(String(st.id))}
                    style={{
                      padding: '16px',
                      borderRadius: 0,
                      border: isSelected ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                      backgroundColor: isSelected ? '#FFF8F9' : '#FFFFFF',
                      cursor: 'pointer',
                      textAlign: 'center',
                      boxShadow: 'none'
                    }}
                  >
                    <div
                      style={{
                        width: '48px',
                        height: '48px',
                        borderRadius: 0,
                        margin: '0 auto 10px auto',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backgroundColor: isSelected ? 'var(--c-primary)' : '#F4F4F5',
                        color: isSelected ? '#FFFFFF' : '#52525B',
                        border: isSelected ? '1px solid var(--c-primary)' : '1px solid var(--c-border)'
                      }}
                    >
                      {st.avatarUrl ? (
                        <img src={st.avatarUrl} alt={st.fullName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        <User size={22} />
                      )}
                    </div>

                    <strong style={{ fontSize: '14px', display: 'block', color: '#111827', marginBottom: '2px' }}>
                      {st.fullName || st.username || `KTV #${st.id}`}
                    </strong>
                    <span style={{ fontSize: '11.5px', color: 'var(--c-text-muted)', display: 'block', marginBottom: '4px' }}>
                      {st.specialty || 'Chuyên viên da liễu'}
                    </span>
                    <span style={{ fontSize: '11px', color: '#92400E', fontWeight: 600 }}>
                      Chứng chỉ y khoa
                    </span>
                  </div>
                );
              })}
            </div>
          </section>

          {/* ================= SECTION 3: CHỌN NGÀY & KHUNG GIỜ ================= */}
          <section style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 0,
            border: '1px solid var(--c-border)',
            padding: '24px',
            boxShadow: 'none'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '18px' }}>
              <div style={{
                width: '30px',
                height: '30px',
                borderRadius: 0,
                backgroundColor: 'var(--c-primary)',
                color: '#FFFFFF',
                fontWeight: 700,
                fontSize: '14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                3
              </div>
              <div>
                <h2 style={{ fontSize: '16.5px', fontWeight: 700, margin: 0, color: '#111827' }}>
                  Lựa chọn ngày hẹn và khung giờ khả dụng
                </h2>
                <div style={{ fontSize: '12.5px', color: 'var(--c-text-muted)' }}>
                  Hệ thống tự động kiểm tra và giữ phòng theo thời gian thực
                </div>
              </div>
            </div>

            {/* Quick Date Cards - Flat */}
            <div style={{ marginBottom: '20px' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, marginBottom: '8px', color: '#374151' }}>
                NGÀY THỰC HIỆN:
              </div>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                {getQuickDates().map((qd) => {
                  const isSelected = selectedDate === qd.iso;
                  return (
                    <button
                      key={qd.iso}
                      type="button"
                      onClick={() => setSelectedDate(qd.iso)}
                      style={{
                        padding: '8px 16px',
                        borderRadius: 0,
                        border: isSelected ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                        backgroundColor: isSelected ? 'var(--c-primary)' : '#FFFFFF',
                        color: isSelected ? '#FFFFFF' : '#111827',
                        fontWeight: 600,
                        fontSize: '12.5px',
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        minWidth: '90px',
                        boxShadow: 'none'
                      }}
                    >
                      <span>{qd.label}</span>
                      <span style={{ fontSize: '10.5px', opacity: 0.9, marginTop: '2px', fontFamily: 'monospace' }}>
                        {qd.dayMonth}
                      </span>
                    </button>
                  );
                })}

                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span style={{ fontSize: '12px', color: 'var(--c-text-light)' }}>Chọn ngày khác:</span>
                  <input
                    type="date"
                    value={selectedDate}
                    min={new Date().toISOString().split('T')[0]}
                    onChange={(e) => setSelectedDate(e.target.value)}
                    style={{
                      padding: '7px 10px',
                      borderRadius: 0,
                      border: '1px solid var(--c-border)',
                      fontSize: '12.5px',
                      outline: 'none',
                      fontFamily: 'monospace'
                    }}
                  />
                </div>
              </div>
            </div>

            {/* Available Time Slots Organized By Shifts */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: '#374151' }}>
                  KHUNG GIỜ KHẢ DỤNG ({selectedDate}):
                </span>
                {loadingSlots && (
                  <span style={{ fontSize: '11.5px', color: 'var(--c-primary)', fontWeight: 600 }}>
                    Đang kiểm tra phòng trống...
                  </span>
                )}
              </div>

              {availableSlots.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {/* Morning Slots */}
                  {morningSlots.length > 0 && (
                    <div style={{ border: '1px solid var(--c-border)', padding: '12px', backgroundColor: '#FAFAFA' }}>
                      <div style={{ fontSize: '11.5px', fontWeight: 700, color: 'var(--c-primary)', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                        <Sun size={13} />
                        <span>Ca sáng (08:00 - 12:00)</span>
                      </div>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                        {morningSlots.map((slot) => {
                          const isSelected = selectedSlot === slot;
                          return (
                            <button
                              key={slot}
                              type="button"
                              onClick={() => setSelectedSlot(slot)}
                              style={{
                                padding: '8px 16px',
                                borderRadius: 0,
                                border: isSelected ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                                backgroundColor: isSelected ? 'var(--c-primary)' : '#FFFFFF',
                                color: isSelected ? '#FFFFFF' : '#111827',
                                fontWeight: 700,
                                fontSize: '12.5px',
                                cursor: 'pointer',
                                fontFamily: 'monospace',
                                boxShadow: 'none'
                              }}
                            >
                              {slot}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Afternoon / Evening Slots */}
                  {afternoonSlots.length > 0 && (
                    <div style={{ border: '1px solid var(--c-border)', padding: '12px', backgroundColor: '#FAFAFA' }}>
                      <div style={{ fontSize: '11.5px', fontWeight: 700, color: 'var(--c-primary)', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                        <Moon size={13} />
                        <span>Ca chiều và tối (13:00 - 20:30)</span>
                      </div>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                        {afternoonSlots.map((slot) => {
                          const isSelected = selectedSlot === slot;
                          return (
                            <button
                              key={slot}
                              type="button"
                              onClick={() => setSelectedSlot(slot)}
                              style={{
                                padding: '8px 16px',
                                borderRadius: 0,
                                border: isSelected ? '2px solid var(--c-primary)' : '1px solid var(--c-border)',
                                backgroundColor: isSelected ? 'var(--c-primary)' : '#FFFFFF',
                                color: isSelected ? '#FFFFFF' : '#111827',
                                fontWeight: 700,
                                fontSize: '12.5px',
                                cursor: 'pointer',
                                fontFamily: 'monospace',
                                boxShadow: 'none'
                              }}
                            >
                              {slot}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              ) : loadingSlots ? (
                <div style={{ padding: '24px', textAlign: 'center', color: 'var(--c-text-muted)', fontSize: '12.5px' }}>
                  Đang đồng bộ dữ liệu phòng khám...
                </div>
              ) : (
                <div style={{
                  padding: '14px 16px',
                  backgroundColor: '#FFFBEB',
                  border: '1px solid #FDE68A',
                  color: '#92400E',
                  fontSize: '12.5px',
                  lineHeight: 1.5
                }}>
                  <div style={{ fontWeight: 700, marginBottom: '4px' }}>
                    Hiện không còn khung giờ trống vào ngày {selectedDate}
                  </div>
                  <div>Vui lòng chọn ngày khác hoặc chọn chuyên viên tự động để xem thêm giờ trống.</div>
                </div>
              )}
            </div>
          </section>

          {/* ================= SECTION 4: THÔNG TIN KHÁCH HÀNG & TIỀN SỬ DA ================= */}
          <section style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 0,
            border: '1px solid var(--c-border)',
            padding: '24px',
            boxShadow: 'none'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '18px' }}>
              <div style={{
                width: '30px',
                height: '30px',
                borderRadius: 0,
                backgroundColor: 'var(--c-primary)',
                color: '#FFFFFF',
                fontWeight: 700,
                fontSize: '14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                4
              </div>
              <div>
                <h2 style={{ fontSize: '16.5px', fontWeight: 700, margin: 0, color: '#111827' }}>
                  Thông tin khách hàng và ghi chú da liễu
                </h2>
                <div style={{ fontSize: '12.5px', color: 'var(--c-text-muted)' }}>
                  Hồ sơ đặt hẹn bảo mật đồng bộ trên hệ thống phòng khám
                </div>
              </div>
            </div>

            {!isAuthenticated && (
              <div style={{
                marginBottom: '16px',
                padding: '10px 14px',
                backgroundColor: '#FFFBEB',
                border: '1px solid #FDE68A',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '12px'
              }}>
                <div style={{ fontSize: '12px', color: '#92400E' }}>
                  Đăng nhập tài khoản để quản lý lịch hẹn trực tuyến trên thiết bị của bạn.
                </div>
                <button
                  type="button"
                  onClick={() => onNavigate('login')}
                  className="btn-luxury-outline"
                  style={{ fontSize: '11px', padding: '5px 12px', borderRadius: 0 }}
                >
                  Đăng nhập
                </button>
              </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '14px', marginBottom: '14px' }}>
              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '5px', color: '#374151' }}>
                  HỌ VÀ TÊN KHÁCH HÀNG *
                </label>
                <input
                  required
                  type="text"
                  placeholder="Ví dụ: Nguyễn Thị Mai Lan"
                  value={customerInfo.fullName}
                  onChange={(e) => setCustomerInfo({ ...customerInfo, fullName: e.target.value })}
                  style={{ width: '100%', padding: '9px 12px', borderRadius: 0, border: '1px solid var(--c-border)', outline: 'none', fontSize: '13px', boxSizing: 'border-box' }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '5px', color: '#374151' }}>
                  SỐ ĐIỆN THOẠI LIÊN HỆ *
                </label>
                <input
                  required
                  type="tel"
                  placeholder="Ví dụ: 0901234567"
                  value={customerInfo.phone}
                  onChange={(e) => setCustomerInfo({ ...customerInfo, phone: e.target.value })}
                  style={{ width: '100%', padding: '9px 12px', borderRadius: 0, border: '1px solid var(--c-border)', outline: 'none', fontSize: '13px', boxSizing: 'border-box' }}
                />
              </div>
            </div>

            <div style={{ marginBottom: '14px' }}>
              <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '5px', color: '#374151' }}>
                EMAIL NHẬN VÉ ĐIỆN TỬ (TÙY CHỌN)
              </label>
              <input
                type="email"
                placeholder="mailan@gmail.com"
                value={customerInfo.email}
                onChange={(e) => setCustomerInfo({ ...customerInfo, email: e.target.value })}
                style={{ width: '100%', padding: '9px 12px', borderRadius: 0, border: '1px solid var(--c-border)', outline: 'none', fontSize: '13px', boxSizing: 'border-box' }}
              />
            </div>

            {/* Quick Medical / Skin Screening Tags - Flat */}
            <div style={{ marginBottom: '14px' }}>
              <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '6px', color: '#374151' }}>
                TIỀN SỬ DA LIỄU NHANH:
              </label>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                {PRESET_SKIN_TAGS.map((tag) => {
                  const isChecked = selectedSkinTags.includes(tag);
                  return (
                    <button
                      key={tag}
                      type="button"
                      onClick={() => toggleSkinTag(tag)}
                      style={{
                        padding: '5px 10px',
                        borderRadius: 0,
                        fontSize: '11.5px',
                        cursor: 'pointer',
                        fontWeight: isChecked ? 700 : 500,
                        border: isChecked ? '1px solid var(--c-primary)' : '1px solid var(--c-border)',
                        backgroundColor: isChecked ? 'var(--c-primary)' : '#FFFFFF',
                        color: isChecked ? '#FFFFFF' : '#374151',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '5px',
                        boxShadow: 'none'
                      }}
                    >
                      {isChecked && <Check size={11} strokeWidth={2.5} />}
                      <span>{tag}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div>
              <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '5px', color: '#374151' }}>
                GHI CHÚ THÊM CHO BÁC SĨ (NẾU CÓ):
              </label>
              <textarea
                rows={2}
                placeholder="Ghi chú thêm về mong muốn điều trị hoặc tiền sử dị ứng khác..."
                value={customerInfo.skinNote}
                onChange={(e) => setCustomerInfo({ ...customerInfo, skinNote: e.target.value })}
                style={{ width: '100%', padding: '9px 12px', borderRadius: 0, border: '1px solid var(--c-border)', outline: 'none', resize: 'vertical', fontSize: '12.5px', boxSizing: 'border-box' }}
              />
            </div>
          </section>

        </div>

        {/* RIGHT COLUMN: Sticky Real-time Appointment Ticket Summary */}
        <div style={{ position: 'sticky', top: '24px' }}>
          <div style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 0,
            border: '1px solid var(--c-border)',
            padding: '20px',
            boxShadow: 'none'
          }}>
            {/* Summary Top Tag */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', borderBottom: '1px solid var(--c-border)', paddingBottom: '10px' }}>
              <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--c-primary)', letterSpacing: '0.04em' }}>
                PHIẾU ĐẶT HẸN DỊCH VỤ
              </span>
              <span style={{ fontSize: '11px', color: 'var(--c-text-muted)' }}>
                BeautyShop Spa
              </span>
            </div>

            {/* Selected Service Card */}
            {selectedService ? (
              <div style={{ display: 'flex', gap: '12px', marginBottom: '14px' }}>
                <img
                  src={getSpaServiceImage(selectedService)}
                  alt={selectedService.name}
                  style={{ width: '64px', height: '64px', borderRadius: 0, objectFit: 'cover', flexShrink: 0 }}
                />
                <div style={{ minWidth: 0 }}>
                  <strong style={{ fontSize: '14px', color: '#111827', display: 'block', lineHeight: 1.35 }}>
                    {selectedService.name}
                  </strong>
                  <span style={{ fontSize: '11.5px', color: 'var(--c-text-muted)', display: 'block', marginTop: '2px' }}>
                    Thời lượng: {selectedService.durationMinutes || 60} phút
                  </span>
                </div>
              </div>
            ) : (
              <div style={{ padding: '16px', textAlign: 'center', color: 'var(--c-text-muted)', fontSize: '12.5px' }}>
                Chưa chọn liệu trình
              </div>
            )}

            {/* Schedule & Staff Info */}
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
              fontSize: '12.5px',
              color: '#374151',
              backgroundColor: '#FAFAFA',
              padding: '12px',
              borderRadius: 0,
              border: '1px solid var(--c-border)',
              marginBottom: '16px'
            }}>
              <div>Ngày hẹn: <strong>{selectedDate}</strong></div>
              <div>Khung giờ: <strong>{selectedSlot || 'Chưa chọn'}</strong></div>
              <div>Chuyên viên: <strong>{selectedStaff === 'AUTO' ? 'Tự động xếp tối ưu' : qualifiedStaff.find(s => String(s.id) === String(selectedStaff))?.fullName || selectedStaff}</strong></div>
              <div>Địa điểm: <strong>123 Đồng Khởi, Quận 1, TP.HCM</strong></div>
            </div>

            {/* Pricing Section */}
            <div style={{ borderTop: '1px solid var(--c-border)', paddingTop: '12px', marginBottom: '18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12.5px', color: 'var(--c-text-muted)', marginBottom: '6px' }}>
                <span>Giá niêm yết:</span>
                <span>{formatCurrency(selectedService?.basePrice ?? selectedService?.price ?? 0)}</span>
              </div>

              {selectedTicketId && (
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12.5px', color: '#065F46', marginBottom: '6px', fontWeight: 600 }}>
                  <span>Khấu trừ vé liệu trình:</span>
                  <span>-{formatCurrency(selectedService?.basePrice ?? selectedService?.price ?? 0)}</span>
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginTop: '10px', paddingTop: '8px', borderTop: '1px solid var(--c-border)' }}>
                <strong style={{ fontSize: '13px', color: '#111827' }}>Thanh toán tại quầy:</strong>
                <strong style={{ fontSize: '18px', fontWeight: 700, color: '#9F1239', fontFamily: 'monospace' }}>
                  {selectedTicketId ? '0 ₫' : formatCurrency(selectedService?.basePrice ?? selectedService?.price ?? 0)}
                </strong>
              </div>

              <div style={{ fontSize: '11px', color: selectedTicketId ? '#065F46' : 'var(--c-primary)', fontWeight: 600, marginTop: '4px', textAlign: 'right' }}>
                {selectedTicketId ? `Khấu trừ 1 buổi từ vé #${selectedTicketId}` : 'Thanh toán sau khi làm dịch vụ'}
              </div>
            </div>

            {bookingError && (
              <div style={{
                marginBottom: '14px',
                padding: '8px 12px',
                backgroundColor: '#FEF2F2',
                border: '1px solid #FECACA',
                color: '#9F1239',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}>
                <AlertCircle size={14} color="#9F1239" />
                <span>{bookingError}</span>
              </div>
            )}

            {/* Action CTA Button - Flat */}
            <button
              onClick={handleConfirmBooking}
              disabled={submitting || !selectedService || !selectedSlot}
              style={{
                width: '100%',
                padding: '12px',
                fontSize: '14px',
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                backgroundColor: (submitting || !selectedService || !selectedSlot) ? '#E5E7EB' : 'var(--c-primary)',
                color: (submitting || !selectedService || !selectedSlot) ? '#9CA3AF' : '#FFFFFF',
                border: 'none',
                borderRadius: 0,
                boxShadow: 'none',
                cursor: (submitting || !selectedService || !selectedSlot) ? 'not-allowed' : 'pointer'
              }}
            >
              <span>{submitting ? 'Đang xử lý...' : 'HOÀN TẤT ĐẶT LỊCH HẸN'}</span>
              <Check size={16} />
            </button>

            <div style={{ textAlign: 'center', fontSize: '11px', color: '#6B7280', marginTop: '10px' }}>
              Không cần đặt cọc trước · Hỗ trợ dời lịch hẹn trước 2 giờ
            </div>
          </div>
        </div>

      </div>
    </div>
  );
};

export default SpaBookingPage;
