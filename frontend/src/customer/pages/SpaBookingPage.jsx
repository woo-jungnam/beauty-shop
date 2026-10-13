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
  Flower2,
  Leaf,
  LoaderCircle,
  Mail,
  MapPin,
  Moon,
  Phone,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Sun,
  Ticket,
  User,
} from "lucide-react";
import { apiClient } from "../../shared/api/client";
import { ENDPOINTS } from "../../shared/api/endpoints";
import { useAuth } from "../../app/providers/AuthProvider";
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

export const SpaBookingPage = ({ onNavigate, preselectedServiceId }) => {
  const { user, isAuthenticated } = useAuth();
  const [services, setServices] = useState([]);
  const [loadingServices, setLoadingServices] = useState(true);
  const [serviceError, setServiceError] = useState(false);
  const [serviceVersion, setServiceVersion] = useState(0);
  const [category, setCategory] = useState("ALL");
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
          ? data.filter((service) => service.isActive !== false)
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
        (ticket) => (ticket.remainingByService?.[selectedService?.id] ?? 0) > 0,
      ),
    [tickets, selectedService?.id],
  );
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
  const visibleServices =
    category === "ALL"
      ? services
      : services.filter((service) => getCategory(service) === category);
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
      setBookingError(
        "Vui lòng chọn một khung giờ còn trống trước khi xác nhận.",
      );
      goToStep(2);
      return;
    }
    if (!contactComplete) {
      goToStep(3);
      return;
    }
    setSubmitting(true);
    try {
      const notes = [...skinTags, contact.note.trim()]
        .filter(Boolean)
        .join("; ");
      const response = await apiClient.post(ENDPOINTS.SPA.BOOK_APPOINTMENT, {
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
            <CheckCircle2 size={34} />
          </span>
          <span className="booking-eyebrow">MỘT CUỘC HẸN DÀNH CHO BẠN</span>
          <h1>Hẹn gặp bạn tại BeautyShop.</h1>
          <p>
            Lịch hẹn đã được ghi nhận. Bạn có thể theo dõi trạng thái trong tài
            khoản và xuất trình mã đặt lịch khi đến Spa.
          </p>
          <div className="booking-confirmation-ticket">
            <div className="booking-confirmation-top">
              <Flower2 size={24} />
              <span>
                BEAUTYSHOP <small>CLINIC & SPA</small>
              </span>
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
                  {copied ? <Check size={19} /> : <Copy size={19} />}
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
                  {bookingResult.time.slice(0, 5)} · {bookingResult.duration}{" "}
                  phút
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
                <dt>
                  <Sparkles size={16} /> Chuyên viên
                </dt>
                <dd>{bookingResult.staff}</dd>
              </div>
              <div>
                <dt>Thanh toán tại Spa</dt>
                <dd>
                  {formatCurrency(bookingResult.price)}
                  {bookingResult.ticketId && (
                    <small>Áp dụng vé #{bookingResult.ticketId}</small>
                  )}
                </dd>
              </div>
            </dl>
          </div>
          <div className="booking-success-actions">
            <button
              type="button"
              className="booking-primary-button"
              onClick={() => onNavigate("profile?tab=appointments")}
            >
              Xem lịch hẹn của tôi <ArrowRight size={17} />
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
            <span className="booking-eyebrow">
              <span /> BEAUTYSHOP CLINIC & SPA
            </span>
            <h1 id="spa-booking-title">
              Một chút thời gian,
              <br />
              <em>một làn da được yêu thương.</em>
            </h1>
            <p>
              Chọn liệu trình phù hợp, tìm một khung giờ thuận tiện. Phần còn
              lại, hãy để BeautyShop chăm sóc bạn.
            </p>
            <div className="booking-hero-benefits">
              <span>
                <Leaf size={16} /> Chăm sóc riêng cho bạn
              </span>
              <span>
                <CalendarDays size={16} /> Chủ động chọn lịch hẹn
              </span>
              <span>
                <ShieldCheck size={16} /> Thanh toán tại Spa
              </span>
            </div>
          </div>
          <div className="booking-hero-photo">
            <img
              src="/images/spa/spa-ritual.jpg"
              alt="Khăn mềm, hoa và đồ chăm sóc cho một buổi Spa thư giãn"
              width="1000"
              height="667"
              fetchPriority="high"
            />
            <span className="booking-photo-label">
              <Flower2 size={17} /> Your moment of care
            </span>
            <div className="booking-photo-note">
              <span>
                <CalendarDays size={21} />
              </span>
              <div>
                <strong>Cuộc hẹn của riêng bạn</strong>
                <small>Dễ dàng đặt lịch trong 3 bước</small>
              </div>
              <CheckCircle2 size={18} />
            </div>
          </div>
        </section>

        <nav className="booking-stepper" aria-label="Các bước đặt lịch">
          {[
            {
              number: 1,
              title: "Chọn dịch vụ",
              subtitle: "Liệu trình dành cho bạn",
              complete: Boolean(selectedService),
            },
            {
              number: 2,
              title: "Ngày & giờ hẹn",
              subtitle: "Lịch phù hợp với bạn",
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
                {step.complete ? <Check size={18} /> : `0${step.number}`}
              </span>
              <div>
                <strong>{step.title}</strong>
                <small>{step.subtitle}</small>
              </div>
              <ArrowRight size={17} />
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
                description="Khám phá và chọn một liệu trình phù hợp với bạn."
                complete={Boolean(selectedService)}
              />
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
                  <Flower2 size={28} />
                  <strong>Chưa có dịch vụ trong danh mục này</strong>
                  <p>Hãy chọn danh mục khác hoặc quay lại sau.</p>
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
                  <Ticket size={23} />
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
                    Một khung giờ dành riêng cho bạn
                  </span>
                }
                description="Chọn ngày và giờ còn trống. Giờ hẹn theo múi giờ Việt Nam."
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
                    <User size={17} />{" "}
                    {selectedStaff === "AUTO"
                      ? "Để Spa sắp xếp chuyên viên"
                      : selectedStaffName}
                    <small>Bạn có thể chọn chuyên viên nếu muốn</small>
                  </span>
                  <ChevronDown size={17} />
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
                      <Sparkles size={20} />
                    </span>
                    <span>
                      <strong>Không có yêu cầu riêng</strong>
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
                    Chưa tải được danh sách chuyên viên. Bạn vẫn có thể chọn để
                    Spa sắp xếp.
                  </p>
                )}
              </details>

              <div className="booking-slot-heading">
                <strong>Chọn giờ bắt đầu</strong>
                <span>
                  <i /> Giờ còn trống
                </span>
              </div>
              {loadingSlots ? (
                <div className="booking-slot-loading" role="status">
                  <LoaderCircle size={22} className="booking-spinner" />
                  <span>Đang tìm khung giờ phù hợp…</span>
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
                  <p>Chọn dịch vụ ở bước 1 để xem các khung giờ phù hợp.</p>
                </div>
              ) : slots.length === 0 ? (
                <div className="booking-empty-state">
                  <CalendarDays size={27} />
                  <strong>Chưa có giờ trống vào ngày này</strong>
                  <p>
                    Chọn một ngày khác hoặc để Spa sắp xếp chuyên viên để xem
                    thêm lịch.
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
                          <group.Icon size={16} /> {group.name}
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
                <InfoIcon /> Lịch trống được cập nhật theo dịch vụ và chuyên
                viên bạn chọn.
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
                    Để chúng mình đón tiếp bạn tốt hơn
                  </span>
                }
                description="Thông tin liên hệ giúp Spa xác nhận cuộc hẹn của bạn."
                complete={contactComplete}
              />
              {!isAuthenticated && (
                <div className="booking-login-note">
                  <User size={18} />
                  <span>Đăng nhập để xác nhận và theo dõi lịch hẹn.</span>
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
                      placeholder="Email của bạn"
                      value={contact.email}
                      onChange={(event) =>
                        setContact({ ...contact, email: event.target.value })
                      }
                    />
                  </div>
                </label>
              </div>
              <div className="booking-skin-tags">
                <strong>Bạn muốn Spa lưu ý điều gì?</strong>
                <span>Chọn nếu phù hợp với bạn · Không bắt buộc</span>
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
                Lời nhắn cho chuyên viên{" "}
                <textarea
                  id="spa-note"
                  name="notes"
                  rows={3}
                  placeholder="Mong muốn chăm sóc, tình trạng da hoặc điều bạn muốn chia sẻ…"
                  value={contact.note}
                  onChange={(event) =>
                    setContact({ ...contact, note: event.target.value })
                  }
                />
              </label>
              <div className="booking-contact-note">
                <ShieldCheck size={16} />
                <span>
                  Thông tin được sử dụng để phục vụ lịch hẹn và chăm sóc bạn tại
                  Spa.
                </span>
              </div>
            </section>
          </div>

          <aside
            className="booking-summary"
            aria-labelledby="spa-summary-title"
          >
            <div className="booking-summary-heading">
              <span className="booking-eyebrow">CUỘC HẸN CỦA BẠN</span>
              <Flower2 size={23} />
              <h2 id="spa-summary-title">Hẹn một buổi chăm sóc.</h2>
              <p>Mọi thông tin, gọn trong một nơi.</p>
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
                <Flower2 size={28} />
                <p>Chọn dịch vụ để bắt đầu cuộc hẹn.</p>
              </div>
            )}
            <dl className="booking-summary-details">
              <div>
                <dt>
                  <CalendarDays size={17} /> Ngày hẹn
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
                  <Clock size={17} /> Giờ bắt đầu
                </dt>
                <dd className={validSlot ? "is-ready" : "is-pending"}>
                  {validSlot ? selectedSlot.slice(0, 5) : "Bạn chưa chọn giờ"}
                </dd>
              </div>
              <div>
                <dt>
                  <User size={17} /> Chuyên viên
                </dt>
                <dd>{selectedStaffName}</dd>
              </div>
              <div>
                <dt>
                  <MapPin size={17} /> Địa điểm
                </dt>
                <dd>123 Đồng Khởi, Quận 1, TP.HCM</dd>
              </div>
            </dl>
            <div className="booking-summary-price">
              <div>
                <span>Giá dịch vụ</span>
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
                <div>
                  <strong>Thanh toán tại Spa</strong>
                  <small>
                    {selectedTicketId
                      ? "Sử dụng 1 buổi từ vé liệu trình"
                      : "Bạn chưa cần thanh toán lúc đặt lịch"}
                  </small>
                </div>
                <b>{selectedService ? formatCurrency(total) : "—"}</b>
              </div>
            </div>
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
              <ShieldCheck size={15} />
              <span>
                {validSlot
                  ? "Kiểm tra thông tin trước khi xác nhận"
                  : "Chọn giờ trống để tiếp tục đặt lịch"}
              </span>
            </div>
            <div className="booking-help">
              <span>Cần hỗ trợ chọn liệu trình?</span>
              <a href="tel:18006324">
                <Phone size={14} /> 1800 6324
              </a>
            </div>
          </aside>
        </form>
        <div className="booking-bottom-note">
          <Leaf size={18} />
          <p>
            Mỗi làn da đều có một câu chuyện. BeautyShop ở đây để lắng nghe và
            chăm sóc.
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
          <ArrowRight size={17} />
        </button>
      </div>
    </div>
  );
};

const InfoIcon = () => (
  <span className="booking-info-icon" aria-hidden="true">
    i
  </span>
);
export default SpaBookingPage;
