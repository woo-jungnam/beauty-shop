import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock,
  Copy,
  Info,
  Leaf,
  LoaderCircle,
  Mail,
  MapPin,
  Moon,
  Phone,
  RefreshCw,
  Search,
  ShieldCheck,
  Sun,
  Ticket,
  User,
} from "lucide-react";
import { apiClient } from "../../shared/api/client";
import { ENDPOINTS } from "../../shared/api/endpoints";
import { useAuth } from "../../app/providers/AuthProvider";
import { AppointmentFinance } from "../../features/treatments/AppointmentFinance";
import {
  getVietnamDateString,
  resolveMediaUrl,
} from "../../shared/utils/formatters";
import "../styles/spa-booking.css";

const formatCurrency = (value) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    value || 0,
  );
const readData = (response) => response?.data ?? response;
const getCategory = (service) =>
  service.categoryName ||
  (typeof service.category === "string"
    ? service.category
    : service.category?.name) ||
  "Chăm sóc da";
const getServiceImage = (service) => {
  const url = service?.thumbnailUrl;
  if (!url) return "/images/spa/facial-care.jpg";
  return url.startsWith("/images/spa/") ? url : resolveMediaUrl(url);
};
const handleServiceImageError = (event) => {
  if (
    event.currentTarget.getAttribute("src") !== "/images/spa/facial-care.jpg"
  ) {
    event.currentTarget.src = "/images/spa/facial-care.jpg";
  }
};
const formatDate = (
  date,
  options = { day: "2-digit", month: "2-digit", year: "numeric" },
) =>
  new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    ...options,
  }).format(new Date(date + "T12:00:00+07:00"));
const getStaffName = (staff) =>
  staff.fullName || staff.username || `Chuyên viên #${staff.id}`;
const SKIN_TAGS = [
  "Da dầu, dễ nổi mụn",
  "Da nhạy cảm",
  "Da khô, thiếu ẩm",
  "Đang dùng Retinol / BHA",
  "Tiền sử dị ứng",
  "Mang thai / Cho con bú",
];

const StepHeading = ({ number, title, description, complete }) => (
  <div className="booking-section-heading">
    <span className={`booking-step-number ${complete ? "is-complete" : ""}`}>
      {complete ? <Check size={18} aria-hidden="true" /> : number}
    </span>
    <div>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  </div>
);

export const SpaBookingPage = ({ onNavigate, preselectedServiceId, preselectedTicketId }) => {
  const { user, isAuthenticated } = useAuth();
  const [services, setServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(true);
  const [serviceError, setServiceError] = useState(false);
  const [serviceVersion, setServiceVersion] = useState(0);
  const [category, setCategory] = useState("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedService, setSelectedService] = useState(null);
  const [staff, setStaff] = useState([]);
  const [loadingStaff, setLoadingStaff] = useState(false);
  const [staffError, setStaffError] = useState(false);
  const [selectedStaff, setSelectedStaff] = useState("AUTO");
  const [selectedDate, setSelectedDate] = useState(() =>
    getVietnamDateString(),
  );
  const [slots, setSlots] = useState([]);
  const [selectedSlot, setSelectedSlot] = useState("");
  const [loadedSlotContext, setLoadedSlotContext] = useState("");
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [slotError, setSlotError] = useState(false);
  const [slotVersion, setSlotVersion] = useState(0);
  const [tickets, setTickets] = useState([]);
  const [selectedTicketId, setSelectedTicketId] = useState(null);
  const [skinTags, setSkinTags] = useState([]);
  const [contact, setContact] = useState({
    fullName: "",
    phone: "",
    email: "",
    note: "",
  });
  const [submitting, setSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const [financialPolicy, setFinancialPolicy] = useState(null);
  const [financialAccepted, setFinancialAccepted] = useState(false);
  const [paymentOption, setPaymentOption] = useState("DEPOSIT_AT_COUNTER");

  useEffect(() => {
    apiClient
      .get("/api/v1/spa/financial-policy")
      .then((response) => setFinancialPolicy(readData(response)))
      .catch(() =>
        setBookingError("Chưa tải được chính sách cọc. Vui lòng tải lại trước khi đặt."),
      );
  }, []);

  const [bookingResult, setBookingResult] = useState(null);
  const [copied, setCopied] = useState(false);
  const formRef = useRef(null);
  const successRef = useRef(null);

  const today = getVietnamDateString();
  const slotContext = `${selectedService?.id || ""}/${selectedDate}/${selectedStaff}`;
  const validSlot = Boolean(
    selectedSlot &&
      loadedSlotContext === slotContext &&
      slots.includes(selectedSlot) &&
      !loadingSlots &&
      !slotError,
  );
  const contactComplete = Boolean(
    contact.fullName.trim() && contact.phone.trim(),
  );
  const currentStep = !selectedService ? 1 : !validSlot ? 2 : 3;
  const servicePrice =
    selectedService?.basePrice ?? selectedService?.price ?? 0;
  const total = selectedTicketId ? 0 : servicePrice;
  const depositDueEstimate = Math.min(Number(financialPolicy?.depositAmount || 0), total);
  const remainingAtCounter = Math.max(0, total - depositDueEstimate);
  const selectedStaffName =
    selectedStaff === "AUTO"
      ? "Spa sắp xếp chuyên viên"
      : getStaffName(
          staff.find((person) => String(person.id) === selectedStaff) || {
            id: selectedStaff,
          },
        );

  useEffect(() => {
    if (!user) return;
    setContact((previous) => ({
      ...previous,
      fullName: previous.fullName || user.fullName || "",
      phone: previous.phone || user.phoneNumber || user.phone || "",
      email: previous.email || user.email || "",
    }));
  }, [user]);

  useEffect(() => {
    let cancelled = false;
    setLoadingServices(true);
    setServiceError(false);
    apiClient
      .get(ENDPOINTS.SPA.PUBLIC_SERVICES)
      .then((response) => {
        if (cancelled) return;
        const data = readData(response);
        const list = Array.isArray(data)
          ? data.filter(
              (service) =>
                service.isActive !== false &&
                service.serviceType !== "TREATMENT_COURSE",
            )
          : [];
        setServices(list);
        setSelectedService(
          (previous) =>
            list.find(
              (service) => String(service.id) === String(preselectedServiceId),
            ) ||
            list.find((service) => service.id === previous?.id) ||
            list[0] ||
            null,
        );
      })
      .catch(() => {
        if (!cancelled) setServiceError(true);
      })
      .finally(() => {
        if (!cancelled) setLoadingServices(false);
      });
    return () => {
      cancelled = true;
    };
  }, [preselectedServiceId, serviceVersion]);

  useEffect(() => {
    let cancelled = false;
    setStaff([]);
    setStaffError(false);
    setSelectedStaff("AUTO");
    if (!selectedService?.id) {
      setLoadingStaff(false);
      return;
    }
    setLoadingStaff(true);
    apiClient
      .get(`/api/v1/spa/services/${selectedService.id}/staff`)
      .then((response) => {
        if (cancelled) return;
        const data = readData(response);
        setStaff(Array.isArray(data) ? data : data?.content || []);
      })
      .catch(() => {
        if (!cancelled) setStaffError(true);
      })
      .finally(() => {
        if (!cancelled) setLoadingStaff(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedService?.id]);

  useEffect(() => {
    let cancelled = false;
    setSelectedSlot("");
    setSlots([]);
    setLoadedSlotContext("");
    setSlotError(false);
    if (!selectedService?.id || !selectedDate) {
      setLoadingSlots(false);
      return;
    }
    setLoadingSlots(true);
    const query = new URLSearchParams({ date: selectedDate });
    if (selectedStaff !== "AUTO") query.set("staffId", selectedStaff);
    apiClient
      .get(
        `/api/v1/spa/services/${selectedService.id}/available-slots?${query}`,
      )
      .then((response) => {
        if (cancelled) return;
        const data = readData(response);
        setSlots(
          Array.isArray(data)
            ? data.filter((slot) => typeof slot === "string")
            : [],
        );
        setLoadedSlotContext(slotContext);
      })
      .catch(() => {
        if (!cancelled) setSlotError(true);
      })
      .finally(() => {
        if (!cancelled) setLoadingSlots(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedService?.id, selectedDate, selectedStaff, slotVersion]);

  useEffect(() => {
    let cancelled = false;
    if (!isAuthenticated) {
      setTickets([]);
      setSelectedTicketId(null);
      return;
    }
    apiClient
      .get(ENDPOINTS.SPA.MY_ACTIVE_TICKETS)
      .then((response) => {
        if (cancelled) return;
        const data = readData(response);
        setTickets(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!cancelled) setTickets([]);
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  const applicableTickets = useMemo(
    () =>
      tickets.filter(
        (ticket) =>
          ticket.benefitType !== "TREATMENT_COURSE" &&
          (ticket.remainingByService?.[selectedService?.id] ?? 0) > 0,
      ),
    [tickets, selectedService?.id],
  );
  const appliedPreselection = useRef(null);
  useEffect(() => {
    if (
      preselectedTicketId &&
      appliedPreselection.current !== preselectedTicketId &&
      applicableTickets.some((t) => String(t.id) === String(preselectedTicketId))
    ) {
      setSelectedTicketId(Number(preselectedTicketId));
      appliedPreselection.current = preselectedTicketId;
    }
  }, [preselectedTicketId, applicableTickets]);
  useEffect(() => {
    if (
      selectedTicketId &&
      !applicableTickets.some(
        (ticket) => String(ticket.id) === String(selectedTicketId),
      )
    )
      setSelectedTicketId(null);
  }, [applicableTickets, selectedTicketId]);

  useEffect(() => {
    if (bookingResult) {
      window.scrollTo({ top: 0, behavior: "instant" });
      successRef.current?.focus({ preventScroll: true });
    }
  }, [bookingResult]);

  useEffect(() => {
    if (!bookingError) return;
    const error = document.getElementById("spa-booking-error");
    error?.scrollIntoView({
      block: "center",
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
    });
    error?.focus({ preventScroll: true });
  }, [bookingError]);

  const categories = useMemo(
    () => [...new Set(services.map(getCategory))],
    [services],
  );
  const visibleServices = useMemo(() => {
    let list =
      category === "ALL"
        ? services
        : services.filter((service) => getCategory(service) === category);
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      list = list.filter(
        (service) =>
          (service.name || "").toLowerCase().includes(q) ||
          (service.shortDescription || "").toLowerCase().includes(q) ||
          getCategory(service).toLowerCase().includes(q),
      );
    }
    return list;
  }, [services, category, searchQuery]);

  const dates = useMemo(
    () =>
      Array.from({ length: 7 }, (_, index) => {
        const date = new Date(today + "T12:00:00+07:00");
        date.setUTCDate(date.getUTCDate() + index);
        const iso = getVietnamDateString(date);
        return {
          iso,
          label:
            index === 0
              ? "Hôm nay"
              : index === 1
                ? "Ngày mai"
                : formatDate(iso, { weekday: "short" }),
          day: formatDate(iso, { day: "2-digit", month: "2-digit" }),
        };
      }),
    [today],
  );

  const goToStep = (step) => {
    const element = document.getElementById(`spa-step-${step}`);
    element?.scrollIntoView({
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
      block: "start",
    });
    element?.focus({ preventScroll: true });
  };
  const chooseService = (service) => {
    setSelectedService(service);
    setSelectedStaff("AUTO");
    setSelectedSlot("");
    setBookingError("");
  };
  const changeDate = (date) => {
    if (!date || date < today) return;
    setSelectedDate(date);
    setSelectedSlot("");
    setBookingError("");
  };
  const toggleTag = (tag) =>
    setSkinTags((previous) =>
      previous.includes(tag)
        ? previous.filter((item) => item !== tag)
        : [...previous, tag],
    );

  const confirmBooking = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setBookingError("");
    if (!isAuthenticated) return onNavigate("login");
    if (!selectedService || !validSlot) {
      if (!selectedService) goToStep(1);
      else if (!validSlot) goToStep(2);
      return;
    }
    setSubmitting(true);
    try {
      if (!financialPolicy)
        throw new Error("Chưa tải được chính sách cọc. Vui lòng tải lại trang.");
      if (
        [
          financialPolicy.depositAmount,
          financialPolicy.cancellationFee,
          financialPolicy.noShowFee,
        ].some((value) => Number(value) > 0) &&
        !financialAccepted
      )
        throw new Error("Vui lòng xác nhận chính sách cọc, hủy và vắng hẹn.");
      const notes = [...skinTags, contact.note.trim()]
        .filter(Boolean)
        .join("; ");
      const response = await apiClient.post(ENDPOINTS.SPA.BOOK_APPOINTMENT, {
        acceptedFinancialPolicy: financialAccepted,
        financialPolicyVersion: financialPolicy.version,
        paymentOption: selectedTicketId ? "WAIVE_WALK_IN" : paymentOption,
        appointmentDate: selectedDate,
        startTime:
          selectedSlot.length === 5 ? selectedSlot + ":00" : selectedSlot,
        notes: notes || undefined,
        items: [
          {
            serviceId: selectedService.id,
            staffId: selectedStaff === "AUTO" ? null : Number(selectedStaff),
            ticketId: selectedTicketId ? Number(selectedTicketId) : null,
          },
        ],
      });
      const created = readData(response);
      if (!created?.id)
        throw new Error("Chưa nhận được xác nhận lịch hẹn. Vui lòng thử lại.");
      setBookingResult({
        id: created.id,
        code: `SPA-${created.id}`,
        status: created.status || "PENDING",
        date: created.appointmentDate || selectedDate,
        time: created.startTime || selectedSlot,
        service: created.items?.[0]?.serviceName || selectedService.name,
        duration: selectedService.durationMinutes || 60,
        staff: selectedStaffName,
        price: total,
        ticketId: selectedTicketId,
        paymentOption: selectedTicketId ? "TICKET" : paymentOption,
        depositEstimate: Math.min(Number(financialPolicy?.depositAmount || 0), total),
        customer: created.customerName || contact.fullName,
        phone: created.customerPhone || contact.phone,
      });
    } catch (error) {
      setBookingError(
        error.response?.data?.message ||
          error.message ||
          "Chưa thể đặt lịch. Vui lòng thử lại.",
      );
      if ((error.status || error.response?.status) === 409) {
        setSelectedSlot("");
        setSlotVersion((version) => version + 1);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const continueBooking = () => {
    if (!selectedService) return goToStep(1);
    if (!validSlot) return goToStep(2);
    if (!isAuthenticated) return onNavigate("login");
    if (!contactComplete) {
      goToStep(3);
      document
        .getElementById(
          !contact.fullName.trim() ? "spa-full-name" : "spa-phone",
        )
        ?.focus({ preventScroll: true });
      return;
    }
    formRef.current?.requestSubmit();
  };
  const actionLabel = !selectedService
    ? "Chọn dịch vụ"
    : !validSlot
      ? "Chọn giờ hẹn"
      : !isAuthenticated
        ? "Đăng nhập để đặt lịch"
        : !contactComplete
          ? "Điền thông tin"
          : "Xác nhận lịch hẹn";

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(bookingResult.code);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  if (bookingResult) {
    return (
      <div className="spa-booking-page booking-success-page">
        <div ref={successRef} className="booking-success" tabIndex={-1}>
          <span className="booking-success-icon">
            <CheckCircle2 size={36} />
          </span>
          <h1>Hẹn gặp bạn tại BeautyShop.</h1>
          <p>
            Lịch hẹn đã được ghi nhận vào hệ thống. Bạn có thể theo dõi chi tiết
            trong tài khoản hoặc xuất trình mã xác nhận khi đến Spa.
          </p>
          <div className="booking-confirmation-ticket">
            <div className="booking-confirmation-top">
              <span>BEAUTYSHOP BEAUTY & SPA</span>
              <span className="booking-status">
                {bookingResult.status === "CONFIRMED"
                  ? "Đã xác nhận"
                  : bookingResult.status === "PENDING"
                    ? "Chờ xác nhận"
                    : bookingResult.status}
              </span>
            </div>
            <div className="booking-confirmation-code">
              <span>Mã đặt lịch của bạn</span>
              <div>
                <strong>{bookingResult.code}</strong>
                <button
                  type="button"
                  onClick={copyCode}
                  aria-label="Sao chép mã đặt lịch"
                >
                  {copied ? <Check size={18} /> : <Copy size={18} />}
                </button>
              </div>
              {copied && <small role="status">Đã sao chép mã đặt lịch</small>}
            </div>
            <h2>{bookingResult.service}</h2>
            <dl className="booking-confirmation-details">
              <div>
                <dt>
                  <CalendarDays size={16} /> Ngày hẹn
                </dt>
                <dd>{formatDate(bookingResult.date)}</dd>
              </div>
              <div>
                <dt>
                  <Clock size={16} /> Thời gian
                </dt>
                <dd>
                  {bookingResult.time.slice(0, 5)} · {bookingResult.duration} phút
                </dd>
              </div>
              <div>
                <dt>
                  <User size={16} /> Khách hàng
                </dt>
                <dd>{bookingResult.customer}</dd>
              </div>
              <div>
                <dt>
                  <Phone size={16} /> Liên hệ
                </dt>
                <dd>{bookingResult.phone}</dd>
              </div>
              <div>
                <dt>Chuyên viên</dt>
                <dd>{bookingResult.staff}</dd>
              </div>
              <div>
                <dt>Hình thức thanh toán</dt>
                <dd>
                  {bookingResult.ticketId ? (
                    <>
                      0 đ
                      <small>Khấu trừ vé #{bookingResult.ticketId}</small>
                    </>
                  ) : bookingResult.paymentOption === "PAY_FULL" ? (
                    <>
                      {formatCurrency(bookingResult.price)}
                      <small>Thanh toán trước 100% qua VietQR</small>
                    </>
                  ) : (bookingResult.depositEstimate || 0) > 0 ? (
                    <>
                      {formatCurrency(bookingResult.depositEstimate)} (Cọc)
                      <small>Còn lại thanh toán tại Spa: {formatCurrency(Math.max(0, bookingResult.price - bookingResult.depositEstimate))}</small>
                    </>
                  ) : (
                    <>
                      {formatCurrency(bookingResult.price)}
                      <small>Thanh toán tại quầy Spa</small>
                    </>
                  )}
                </dd>
              </div>
            </dl>
          </div>
          {bookingResult.id && (
            <div style={{ marginTop: 24, textAlign: 'left' }}>
              <AppointmentFinance
                appointmentId={bookingResult.id}
                role="customer"
                defaultOpen
              />
            </div>
          )}
          <div className="booking-success-actions">
            <button
              type="button"
              className="booking-primary-button"
              onClick={() => onNavigate("profile?tab=appointments")}
            >
              Xem lịch hẹn của tôi <ArrowRight size={16} />
            </button>
            <button
              type="button"
              className="booking-text-button"
              onClick={() => onNavigate("")}
            >
              Về trang chủ
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="spa-booking-page">
      <div className="booking-container">
        <div className="booking-breadcrumb">
          <button type="button" onClick={() => onNavigate("")}>
            <ArrowLeft size={15} /> Trang chủ
          </button>
          <span>/</span>
          <span>Đặt lịch Spa</span>
        </div>

        <section className="booking-hero" aria-labelledby="spa-booking-title">
          <div className="booking-hero-copy">
            <h1 id="spa-booking-title">
              Đặt lịch dịch vụ Spa & Chăm sóc da
              <small className="booking-hero-sub">
                Một chút thời gian cho làn da được phục hồi chuyên sâu.
              </small>
            </h1>
            <p>
              Chọn dịch vụ và khung giờ phù hợp. Chuyên viên sẽ chuẩn bị phòng và
              quy trình tiếp đón chu đáo nhất.
            </p>
            <div className="booking-hero-benefits">
              <span>
                <ShieldCheck size={15} /> Chuẩn an toàn
              </span>
              <span>
                <CalendarDays size={15} /> Chủ động chọn lịch
              </span>
              <span>
                <Leaf size={15} /> Thanh toán tại Spa
              </span>
            </div>
            <div className="booking-hero-shortcuts">
              <button
                type="button"
                className="booking-link-chip"
                onClick={() => onNavigate("service-packages")}
              >
                Gói dịch vụ tiết kiệm →
              </button>
              <a href="#/treatments" className="booking-link-chip">
                Lộ trình chăm sóc da chuyên sâu →
              </a>
            </div>
          </div>
          <div className="booking-hero-photo">
            <img
              src="/images/spa/spa-ritual.jpg"
              alt="Không gian thư giãn tại BeautyShop Spa"
              width="600"
              height="300"
              loading="lazy"
            />
          </div>
        </section>

        <nav className="booking-stepper" aria-label="Các bước đặt lịch">
          {[
            {
              number: 1,
              title: "Chọn dịch vụ",
              subtitle: "Liệu trình chăm sóc",
              complete: Boolean(selectedService),
            },
            {
              number: 2,
              title: "Ngày & giờ hẹn",
              subtitle: "Khung giờ và chuyên viên",
              complete: validSlot,
            },
            {
              number: 3,
              title: "Thông tin & xác nhận",
              subtitle: "Hoàn tất cuộc hẹn",
              complete: contactComplete && validSlot,
            },
          ].map((step) => (
            <button
              type="button"
              key={step.number}
              className={`${currentStep === step.number ? "is-current" : ""} ${step.complete ? "is-complete" : ""}`}
              aria-current={currentStep === step.number ? "step" : undefined}
              onClick={() => goToStep(step.number)}
            >
              <span>
                {step.complete ? <Check size={16} /> : `0${step.number}`}
              </span>
              <div>
                <strong>{step.title}</strong>
                <small>{step.subtitle}</small>
              </div>
              <ArrowRight size={16} />
            </button>
          ))}
        </nav>

        <form
          id="spa-booking-form"
          ref={formRef}
          className="booking-layout spa-booking-grid-layout"
          onSubmit={confirmBooking}
        >
          <div className="booking-form-sections">
            <section
              id="spa-step-1"
              className="booking-section"
              tabIndex={-1}
              aria-labelledby="spa-services-title"
            >
              <StepHeading
                number="01"
                title={
                  <span id="spa-services-title">
                    Bạn muốn chăm sóc da thế nào?
                  </span>
                }
                description="Khám phá và chọn một liệu trình phù hợp với nhu cầu của bạn."
                complete={Boolean(selectedService)}
              />

              <div className="booking-service-search">
                <Search size={16} />
                <input
                  type="text"
                  placeholder="Tìm tên dịch vụ, liệu trình..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  aria-label="Tìm kiếm dịch vụ"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => setSearchQuery("")}
                    aria-label="Xóa tìm kiếm"
                  >
                    ×
                  </button>
                )}
              </div>

              <div
                className="booking-category-pills"
                role="group"
                aria-label="Lọc danh mục dịch vụ"
              >
                <button
                  type="button"
                  aria-pressed={category === "ALL"}
                  onClick={() => setCategory("ALL")}
                >
                  Tất cả dịch vụ
                </button>
                {categories.map((name) => (
                  <button
                    type="button"
                    key={name}
                    aria-pressed={category === name}
                    onClick={() => setCategory(name)}
                  >
                    {name}
                  </button>
                ))}
              </div>

              {loadingServices ? (
                <div
                  className="booking-service-skeletons"
                  aria-label="Đang tải dịch vụ"
                  role="status"
                >
                  {[0, 1, 2, 3].map((index) => (
                    <div key={index} className="booking-service-skeleton" />
                  ))}
                </div>
              ) : serviceError ? (
                <div className="booking-empty-state" role="alert">
                  <AlertCircle size={26} />
                  <strong>Chưa tải được dịch vụ Spa</strong>
                  <p>Vui lòng kiểm tra kết nối rồi thử lại.</p>
                  <button
                    type="button"
                    className="booking-text-button"
                    onClick={() => setServiceVersion((version) => version + 1)}
                  >
                    <RefreshCw size={15} /> Thử lại
                  </button>
                </div>
              ) : visibleServices.length === 0 ? (
                <div className="booking-empty-state">
                  <Info size={28} />
                  <strong>Chưa tìm thấy dịch vụ phù hợp</strong>
                  <p>Hãy thử từ khóa khác hoặc chọn xem tất cả danh mục.</p>
                </div>
              ) : (
                <div
                  className="booking-service-grid"
                  role="group"
                  aria-label="Chọn dịch vụ Spa"
                >
                  {visibleServices.map((service) => {
                    const selected = selectedService?.id === service.id;
                    return (
                      <button
                        type="button"
                        key={service.id}
                        className={`booking-service-card ${selected ? "is-selected" : ""}`}
                        aria-pressed={selected}
                        aria-label={`Chọn dịch vụ ${service.name}`}
                        onClick={() => chooseService(service)}
                      >
                        <span className="booking-service-image">
                          <img
                            src={getServiceImage(service)}
                            alt=""
                            loading="lazy"
                            onError={handleServiceImageError}
                          />
                        </span>
                        <span className="booking-service-content">
                          <small>{getCategory(service)}</small>
                          <strong>{service.name}</strong>
                          <span className="booking-service-description">
                            {service.shortDescription ||
                              "Xem chi phí và thời lượng để lựa chọn liệu trình phù hợp."}
                          </span>
                          <span className="booking-service-meta">
                            <span>
                              <Clock size={13} />{" "}
                              {service.durationMinutes || 60} phút
                            </span>
                            <b>
                              {formatCurrency(
                                service.basePrice ?? service.price ?? 0,
                              )}
                            </b>
                          </span>
                        </span>
                        <span
                          className="booking-service-check"
                          aria-hidden="true"
                        >
                          {selected && <Check size={12} />}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}

              {applicableTickets.length > 0 && (
                <div className="booking-ticket-benefit">
                  <Ticket size={22} />
                  <div>
                    <strong>Bạn có vé liệu trình phù hợp</strong>
                    <p>Sử dụng 1 buổi từ vé để thanh toán cho cuộc hẹn này.</p>
                  </div>
                  <label>
                    <span className="booking-sr-only">
                      Áp dụng vé liệu trình
                    </span>
                    <select
                      value={selectedTicketId || ""}
                      onChange={(event) =>
                        setSelectedTicketId(
                          event.target.value
                            ? Number(event.target.value)
                            : null,
                        )
                      }
                    >
                      <option value="">Chưa áp dụng</option>
                      {applicableTickets.map((ticket) => (
                        <option key={ticket.id} value={ticket.id}>
                          Vé #{ticket.id} ·{" "}
                          {ticket.remainingByService?.[selectedService.id]} buổi
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              )}
            </section>

            <section
              id="spa-step-2"
              className="booking-section"
              tabIndex={-1}
              aria-labelledby="spa-schedule-title"
            >
              <StepHeading
                number="02"
                title={
                  <span id="spa-schedule-title">
                    Khung giờ và chuyên viên
                  </span>
                }
                description="Chọn ngày và giờ còn trống. Giờ hẹn tính theo múi giờ Việt Nam."
                complete={validSlot}
              />

              <div className="booking-date-heading">
                <label htmlFor="spa-date">Ngày bạn muốn đến</label>
                <div className="booking-custom-date">
                  <CalendarDays size={16} />
                  <input
                    id="spa-date"
                    type="date"
                    value={selectedDate}
                    min={today}
                    onChange={(event) => changeDate(event.target.value)}
                    aria-label="Chọn ngày hẹn khác"
                  />
                </div>
              </div>

              <div
                className="booking-date-strip"
                role="group"
                aria-label="Chọn ngày hẹn"
              >
                {dates.map((date) => (
                  <button
                    type="button"
                    key={date.iso}
                    className={selectedDate === date.iso ? "is-selected" : ""}
                    aria-pressed={selectedDate === date.iso}
                    onClick={() => changeDate(date.iso)}
                  >
                    <small>{date.label}</small>
                    <strong>{date.day}</strong>
                    <span>
                      {selectedDate === date.iso ? (
                        <Check size={14} />
                      ) : (
                        <span />
                      )}
                    </span>
                  </button>
                ))}
              </div>

              <details className="booking-provider">
                <summary>
                  <span>
                    <User size={16} />{" "}
                    {selectedStaff === "AUTO"
                      ? "Để Spa sắp xếp chuyên viên"
                      : selectedStaffName}
                    <small>Bạn có thể chọn chuyên viên mong muốn</small>
                  </span>
                  <ChevronDown size={16} />
                </summary>
                <div className="booking-staff-grid">
                  <button
                    type="button"
                    className={`booking-staff-card ${selectedStaff === "AUTO" ? "is-selected" : ""}`}
                    aria-pressed={selectedStaff === "AUTO"}
                    onClick={() => {
                      setSelectedStaff("AUTO");
                      setSelectedSlot("");
                    }}
                  >
                    <span className="booking-staff-avatar">
                      <User size={18} />
                    </span>
                    <span>
                      <strong>Không yêu cầu riêng</strong>
                      <small>Spa sắp xếp chuyên viên phù hợp</small>
                    </span>
                    {selectedStaff === "AUTO" && <Check size={15} />}
                  </button>
                  {staff.map((person) => (
                    <button
                      type="button"
                      key={person.id}
                      className={`booking-staff-card ${selectedStaff === String(person.id) ? "is-selected" : ""}`}
                      aria-pressed={selectedStaff === String(person.id)}
                      onClick={() => {
                        setSelectedStaff(String(person.id));
                        setSelectedSlot("");
                      }}
                    >
                      <span className="booking-staff-avatar">
                        {person.avatarUrl ? (
                          <img
                            src={resolveMediaUrl(person.avatarUrl)}
                            alt=""
                            loading="lazy"
                          />
                        ) : (
                          getStaffName(person).charAt(0)
                        )}
                      </span>
                      <span>
                        <strong>{getStaffName(person)}</strong>
                        <small>{person.specialty || "Chuyên viên Spa"}</small>
                      </span>
                      {selectedStaff === String(person.id) && (
                        <Check size={15} />
                      )}
                    </button>
                  ))}
                </div>
                {loadingStaff && (
                  <p role="status" className="booking-muted">
                    Đang tải chuyên viên…
                  </p>
                )}
                {staffError && (
                  <p className="booking-muted">
                    Chưa tải được danh sách chuyên viên. Bạn vẫn có thể để Spa sắp xếp.
                  </p>
                )}
              </details>

              <div className="booking-slot-heading">
                <strong>Khung giờ bắt đầu</strong>
                <span>
                  <i /> Khung giờ còn trống
                </span>
              </div>

              {loadingSlots ? (
                <div className="booking-slot-loading" role="status">
                  <LoaderCircle size={22} className="booking-spinner" />
                  <span>Đang kiểm tra lịch trống…</span>
                </div>
              ) : slotError ? (
                <div className="booking-empty-state" role="alert">
                  <AlertCircle size={25} />
                  <strong>Chưa kiểm tra được giờ trống</strong>
                  <p>Thử lại để xem lịch còn trống cho ngày đã chọn.</p>
                  <button
                    type="button"
                    className="booking-text-button"
                    onClick={() => setSlotVersion((version) => version + 1)}
                  >
                    <RefreshCw size={15} /> Kiểm tra lại
                  </button>
                </div>
              ) : !selectedService ? (
                <div className="booking-empty-state">
                  <CalendarDays size={26} />
                  <p>Vui lòng chọn dịch vụ ở bước 1 để xem các khung giờ phù hợp.</p>
                </div>
              ) : slots.length === 0 ? (
                <div className="booking-empty-state">
                  <CalendarDays size={27} />
                  <strong>Chưa có giờ trống vào ngày này</strong>
                  <p>
                    Vui lòng chọn một ngày khác hoặc để Spa tự động sắp xếp chuyên viên.
                  </p>
                </div>
              ) : (
                <div className="booking-slot-groups">
                  {[
                    {
                      name: "Buổi sáng",
                      Icon: Sun,
                      times: slots.filter(
                        (slot) => Number(slot.split(":")[0]) < 12,
                      ),
                    },
                    {
                      name: "Buổi chiều & tối",
                      Icon: Moon,
                      times: slots.filter(
                        (slot) => Number(slot.split(":")[0]) >= 12,
                      ),
                    },
                  ]
                    .filter((group) => group.times.length > 0)
                    .map((group) => (
                      <div className="booking-slot-group" key={group.name}>
                        <span>
                          <group.Icon size={15} /> {group.name}
                        </span>
                        <div role="group" aria-label={group.name}>
                          {group.times.map((slot) => (
                            <button
                              type="button"
                              key={slot}
                              aria-pressed={selectedSlot === slot}
                              className={
                                selectedSlot === slot ? "is-selected" : ""
                              }
                              onClick={() => {
                                setSelectedSlot(slot);
                                setBookingError("");
                              }}
                            >
                              {slot.slice(0, 5)}
                              {selectedSlot === slot && <Check size={12} />}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))}
                </div>
              )}
              <p className="booking-schedule-note">
                <Info size={14} /> Lịch trống được cập nhật theo dịch vụ và chuyên viên bạn chọn.
              </p>
            </section>

            <section
              id="spa-step-3"
              className="booking-section"
              tabIndex={-1}
              aria-labelledby="spa-contact-title"
            >
              <StepHeading
                number="03"
                title={
                  <span id="spa-contact-title">
                    Thông tin liên hệ & xác nhận
                  </span>
                }
                description="Thông tin giúp chuyên viên chuẩn bị phòng và liên hệ xác nhận cuộc hẹn."
                complete={contactComplete}
              />
              {!isAuthenticated && (
                <div className="booking-login-note">
                  <User size={18} />
                  <span>Đăng nhập để lưu và theo dõi lịch hẹn trong tài khoản của bạn.</span>
                  <button type="button" onClick={() => onNavigate("login")}>
                    Đăng nhập <ArrowRight size={14} />
                  </button>
                </div>
              )}
              <div className="booking-contact-grid">
                <label htmlFor="spa-full-name">
                  Họ và tên <span>*</span>
                  <div className="booking-input-wrap">
                    <User size={16} />
                    <input
                      id="spa-full-name"
                      name="fullName"
                      type="text"
                      required
                      autoComplete="name"
                      placeholder="Họ và tên của bạn"
                      value={contact.fullName}
                      onChange={(event) =>
                        setContact({ ...contact, fullName: event.target.value })
                      }
                    />
                  </div>
                </label>
                <label htmlFor="spa-phone">
                  Số điện thoại <span>*</span>
                  <div className="booking-input-wrap">
                    <Phone size={16} />
                    <input
                      id="spa-phone"
                      name="phone"
                      type="tel"
                      required
                      autoComplete="tel"
                      placeholder="Số điện thoại liên hệ"
                      value={contact.phone}
                      onChange={(event) =>
                        setContact({ ...contact, phone: event.target.value })
                      }
                    />
                  </div>
                </label>
                <label className="booking-email-field" htmlFor="spa-email">
                  Email <small>Không bắt buộc</small>
                  <div className="booking-input-wrap">
                    <Mail size={16} />
                    <input
                      id="spa-email"
                      name="email"
                      type="email"
                      autoComplete="email"
                      placeholder="Email nhận thông tin xác nhận"
                      value={contact.email}
                      onChange={(event) =>
                        setContact({ ...contact, email: event.target.value })
                      }
                    />
                  </div>
                </label>
              </div>

              <div className="booking-skin-tags">
                <strong>Tình trạng da cần lưu ý</strong>
                <span>Chọn các mục phù hợp để chuyên viên chuẩn bị dịch vụ chu đáo</span>
                <div role="group" aria-label="Thông tin cần lưu ý">
                  {SKIN_TAGS.map((tag) => (
                    <button
                      type="button"
                      key={tag}
                      aria-pressed={skinTags.includes(tag)}
                      onClick={() => toggleTag(tag)}
                    >
                      {skinTags.includes(tag) && <Check size={13} />}
                      {tag}
                    </button>
                  ))}
                </div>
              </div>

              <label className="booking-note-field" htmlFor="spa-note">
                Ghi chú thêm cho chuyên viên
                <textarea
                  id="spa-note"
                  name="notes"
                  rows={3}
                  placeholder="Nhu cầu chăm sóc da hoặc yêu cầu đặc biệt…"
                  value={contact.note}
                  onChange={(event) =>
                    setContact({ ...contact, note: event.target.value })
                  }
                />
              </label>

              <div className="booking-contact-note">
                <ShieldCheck size={15} />
                <span>
                  Thông tin cá nhân chỉ được sử dụng phục vụ cuộc hẹn tại Spa.
                </span>
              </div>
            </section>
          </div>

          <aside
            className="booking-summary"
            aria-labelledby="spa-summary-title"
          >
            <div className="booking-summary-heading">
              <span className="booking-eyebrow">TÓM TẮT CUỘC HẸN</span>
              <h2 id="spa-summary-title">Chi tiết lịch hẹn</h2>
              <p>Xác nhận dịch vụ và khung giờ trước khi đặt.</p>
            </div>
            {selectedService ? (
              <div className="booking-summary-service">
                <img
                  src={getServiceImage(selectedService)}
                  onError={handleServiceImageError}
                  alt=""
                />
                <div>
                  <small>{getCategory(selectedService)}</small>
                  <strong>{selectedService.name}</strong>
                  <span>
                    <Clock size={13} /> {selectedService.durationMinutes || 60}{" "}
                    phút
                  </span>
                </div>
              </div>
            ) : (
              <div className="booking-summary-placeholder">
                <p>Vui lòng chọn dịch vụ ở Bước 1.</p>
              </div>
            )}
            <dl className="booking-summary-details">
              <div>
                <dt>
                  <CalendarDays size={16} /> Ngày hẹn
                </dt>
                <dd>
                  {formatDate(selectedDate, {
                    weekday: "short",
                    day: "2-digit",
                    month: "2-digit",
                    year: "numeric",
                  })}
                </dd>
              </div>
              <div>
                <dt>
                  <Clock size={16} /> Giờ bắt đầu
                </dt>
                <dd className={validSlot ? "is-ready" : "is-pending"}>
                  {validSlot ? selectedSlot.slice(0, 5) : "Bạn chưa chọn giờ"}
                </dd>
              </div>
              <div>
                <dt>
                  <User size={16} /> Chuyên viên
                </dt>
                <dd>{selectedStaffName}</dd>
              </div>
              <div>
                <dt>
                  <MapPin size={16} /> Cơ sở Spa
                </dt>
                <dd>123 Đồng Khởi, Quận 1, TP.HCM</dd>
              </div>
            </dl>
            <div className="booking-summary-price">
              <div>
                <span>Giá dịch vụ niêm yết</span>
                <span>
                  {selectedService ? formatCurrency(servicePrice) : "—"}
                </span>
              </div>
              {selectedTicketId && (
                <div className="booking-ticket-discount">
                  <span>Áp dụng vé #{selectedTicketId}</span>
                  <span>−{formatCurrency(servicePrice)}</span>
                </div>
              )}
              <div className="booking-total">
                {selectedTicketId ? (
                  <>
                    <div>
                      <strong>Thanh toán bằng vé liệu trình</strong>
                      <small>Khấu trừ 1 buổi từ vé #{selectedTicketId}</small>
                    </div>
                    <b>0 đ</b>
                  </>
                ) : paymentOption === "PAY_FULL" ? (
                  <>
                    <div>
                      <strong>Thanh toán trước (100% qua VietQR)</strong>
                      <small>Chuyển khoản toàn bộ trước khi đến cơ sở</small>
                    </div>
                    <b>{selectedService ? formatCurrency(total) : "—"}</b>
                  </>
                ) : depositDueEstimate > 0 ? (
                  <>
                    <div>
                      <strong>Tiền cọc thanh toán ngay</strong>
                      <small>Còn lại thanh toán tại Spa: {formatCurrency(remainingAtCounter)}</small>
                    </div>
                    <b>{formatCurrency(depositDueEstimate)}</b>
                  </>
                ) : (
                  <>
                    <div>
                      <strong>Thanh toán tại quầy Spa</strong>
                      <small>Thanh toán trực tiếp sau khi hoàn tất trải nghiệm</small>
                    </div>
                    <b>{selectedService ? formatCurrency(total) : "—"}</b>
                  </>
                )}
              </div>
            </div>

            {!selectedTicketId && total > 0 && (
              <div className="booking-payment-options">
                <span className="booking-payment-title">Hình thức thanh toán:</span>
                <label className={`booking-payment-card ${paymentOption === "DEPOSIT_AT_COUNTER" ? "is-selected" : ""}`}>
                  <input
                    type="radio"
                    name="spaPaymentOption"
                    value="DEPOSIT_AT_COUNTER"
                    checked={paymentOption === "DEPOSIT_AT_COUNTER"}
                    onChange={(e) => setPaymentOption(e.target.value)}
                  />
                  <div className="booking-payment-card-copy">
                    <strong>Thanh toán tại quầy</strong>
                    <span>
                      {depositDueEstimate > 0
                        ? `Đặt cọc trước ${formatCurrency(depositDueEstimate)} qua VietQR, thanh toán phần còn lại ${formatCurrency(remainingAtCounter)} tại quầy.`
                        : "Thanh toán toàn bộ trực tiếp tại quầy sau khi trải nghiệm dịch vụ."}
                    </span>
                  </div>
                </label>
                <label className={`booking-payment-card ${paymentOption === "PAY_FULL" ? "is-selected" : ""}`}>
                  <input
                    type="radio"
                    name="spaPaymentOption"
                    value="PAY_FULL"
                    checked={paymentOption === "PAY_FULL"}
                    onChange={(e) => setPaymentOption(e.target.value)}
                  />
                  <div className="booking-payment-card-copy">
                    <strong>Thanh toán trước 100% ({formatCurrency(total)})</strong>
                    <span>Chuyển khoản toàn bộ qua VietQR SePay để giữ lịch ưu tiên và làm thủ tục check-in nhanh.</span>
                  </div>
                </label>
              </div>
            )}

            {financialPolicy &&
              [
                financialPolicy.depositAmount,
                financialPolicy.cancellationFee,
                financialPolicy.noShowFee,
              ].some((value) => Number(value) > 0) && (
                <div className="booking-financial-policy">
                  <label>
                    <input
                      type="checkbox"
                      checked={financialAccepted}
                      onChange={(event) =>
                        setFinancialAccepted(event.target.checked)
                      }
                    />
                    <span>
                      Tôi đồng ý chính sách phiên bản {financialPolicy.version}:
                      cọc tối đa {formatCurrency(financialPolicy.depositAmount)},
                      phí hủy sau hạn {formatCurrency(financialPolicy.cancellationFee)},
                      phí vắng hẹn {formatCurrency(financialPolicy.noShowFee)}.
                      Tiền cọc sẽ được trừ trực tiếp vào hóa đơn tại Spa.
                    </span>
                  </label>
                </div>
              )}

            {bookingError && (
              <div
                id="spa-booking-error"
                className="booking-error"
                role="alert"
                tabIndex={-1}
              >
                <AlertCircle size={18} />
                <span>{bookingError}</span>
              </div>
            )}

            <button
              type={isAuthenticated && validSlot ? "submit" : "button"}
              onClick={
                isAuthenticated && validSlot ? undefined : continueBooking
              }
              className="booking-primary-button"
              disabled={submitting || loadingServices || serviceError}
            >
              {submitting ? (
                <>
                  <LoaderCircle size={18} className="booking-spinner" /> Đang
                  xác nhận…
                </>
              ) : (
                <>
                  {!validSlot
                    ? actionLabel
                    : !isAuthenticated
                      ? "Đăng nhập để đặt lịch"
                      : "Xác nhận lịch hẹn"}
                  <ArrowRight size={17} />
                </>
              )}
            </button>
            <div className="booking-summary-footer">
              <ShieldCheck size={14} />
              <span>
                {validSlot
                  ? "Kiểm tra thông tin trước khi xác nhận"
                  : "Chọn giờ trống để tiếp tục đặt lịch"}
              </span>
            </div>
            <div className="booking-help">
              <span>Cần hỗ trợ tư vấn liệu trình?</span>
              <a href="tel:18006324">
                <Phone size={14} /> 1800 6324
              </a>
            </div>
          </aside>
        </form>

        <div className="booking-bottom-note">
          <Leaf size={16} />
          <p>
            BeautyShop Spa cam kết dịch vụ chăm sóc da chuyên sâu, an toàn và bảo
            mật thông tin khách hàng.
          </p>
        </div>
      </div>

      <div className="booking-mobile-bar">
        <div>
          <small>Thanh toán tại Spa</small>
          <strong>
            {selectedService ? formatCurrency(total) : "Chưa chọn dịch vụ"}
          </strong>
        </div>
        <button
          type="button"
          className="booking-primary-button"
          disabled={submitting || loadingServices || serviceError}
          onClick={continueBooking}
        >
          {submitting ? "Đang xác nhận…" : actionLabel}
          <ArrowRight size={16} />
        </button>
      </div>
    </div>
  );
};
