import React, { useState } from 'react';
import { ArrowRight, Bell, CalendarDays, Check, Clock, CreditCard, FileText, Sparkles } from 'lucide-react';
import { Button } from '../../shared/ui/Button';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import { bannerHref } from '../banners/bannerLinks';
import { dateLabel, hasAction, money, statusLabel } from './treatmentApi';
import './customer-treatment-views.css';

function StoreImage({ source, className, label = '', eager = false, href }) {
  const [failed, setFailed] = useState(false);
  if (!source || failed) return null;
  const image = <img className={className} src={resolveMediaUrl(source)} alt={label}
    loading={eager ? 'eager' : 'lazy'} onError={() => setFailed(true)} />;
  if (!href) return image;
  return <a href={href} className="customer-treatment-hero-link" onClick={event => {
    if (href !== '#treatment-catalog' || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const catalog = document.getElementById('treatment-catalog');
    if (!catalog) return;
    event.preventDefault();
    catalog.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
    catalog.focus({ preventScroll: true });
  }}>{image}</a>;
}

export function CustomerTreatmentHero({ banner, compact }) {
  return (
    <header className={`customer-treatment-hero${compact ? ' is-compact' : ''}`}>
      {!compact && banner && <StoreImage key={banner.imageUrl} source={banner.imageUrl} label={banner.title || 'Lộ trình chăm sóc'}
        className="customer-treatment-hero-image" href={bannerHref(banner.targetUrl)} eager />}
      <div className="customer-treatment-hero-copy">
        <h1>Điều trị theo lộ trình</h1>
        {!compact && <p>Chọn lộ trình phù hợp và theo dõi từng buổi chăm sóc trong hồ sơ của bạn.</p>}
      </div>
    </header>
  );
}

export function CustomerTreatmentCatalog({ templates, services, loading, onPurchase }) {
  const serviceImages = new Map(services.filter(service => service.isActive !== false && service.thumbnailUrl)
    .map(service => [String(service.id), service.thumbnailUrl]));
  return (
    <div id="treatment-catalog" tabIndex={-1} className="treatment-grid customer-treatment-catalog">
      {templates.map(template => {
        const sessions = template.sessions || [];
        const image = serviceImages.get(String(template.serviceId))
          || sessions.flatMap(session => session.items || []).map(item => serviceImages.get(String(item.serviceId))).find(Boolean);
        return (
          <article className="treatment-card customer-treatment-plan" key={template.id} data-treatment-template-id={template.id}>
            <StoreImage key={image} source={image} className="customer-treatment-cover" />
            <div className="customer-treatment-plan-body">
              <h2>{template.name}</h2>
              {template.price != null && <div className="treatment-price">{money(template.price)}</div>}
              <div className="treatment-meta">
                <span><CalendarDays size={15} aria-hidden="true" />{sessions.length} buổi</span>
                {template.validityDays != null && <span><Clock size={15} aria-hidden="true" />{template.validityDays} ngày</span>}
              </div>
              <ol className="customer-treatment-timeline" aria-label="Các buổi trong lộ trình">
                {sessions.map(session => (
                  <li key={session.sequence}>
                    <span className="customer-treatment-number" aria-hidden="true">{session.sequence}</span>
                    <div>
                      <strong>Buổi {session.sequence}: {session.title}</strong>
                      {(session.items || []).length > 0 && (
                        <small>{session.items.map(item => item.serviceName || `Dịch vụ #${item.serviceId}`).join(', ')}</small>
                      )}
                      {session.minGapDays > 0 && <small>Cách tối thiểu {session.minGapDays} ngày</small>}
                    </div>
                  </li>
                ))}
              </ol>
              {(template.description || template.terms) && <details>
                <summary>{template.description && template.terms ? 'Mô tả và điều khoản' : template.terms ? 'Điều khoản sử dụng' : 'Mô tả lộ trình'}</summary>
                {template.description && <p>{template.description}</p>}
                {template.terms && <p>{template.terms}</p>}
              </details>}
              {hasAction(template, 'PURCHASE') && <div className="treatment-actions">
                <Button onClick={() => onPurchase(template)}>Xem & đăng ký lộ trình<ArrowRight size={15} /></Button>
              </div>}
            </div>
          </article>
        );
      })}
      {!loading && templates.length === 0 && <div className="treatment-empty">
        <Sparkles size={28} aria-hidden="true" /><strong>Chưa có lộ trình phù hợp</strong>
        <a className="treatment-link" href="#/booking">Xem dịch vụ Spa đặt một lần</a>
      </div>}
    </div>
  );
}

function nextAppointment(courses) {
  const now = Date.now();
  return courses.flatMap(course => (course.sessions || []).flatMap(session =>
    (session.items || []).filter(item => item.appointmentId && ['PENDING', 'CONFIRMED'].includes(item.appointmentStatus))
      .map(item => ({
        course,
        session,
        item,
        startsAt: Date.parse(`${item.appointmentDate}T${item.startTime}+07:00`),
      })),
  )).filter(appointment => appointment.startsAt > now).sort((first, second) => first.startsAt - second.startsAt)[0];
}

export function CustomerTreatmentCourses({ courses, allCourses, onOpen }) {
  if (!courses.length) return null;
  const upcoming = nextAppointment(allCourses);
  return (
    <>
      {upcoming && <section className="treatment-card customer-treatment-upcoming">
        <span className="customer-treatment-event-icon"><CalendarDays size={22} aria-hidden="true" /></span>
        <div>
          <h2>Lịch điều trị sắp tới</h2>
          <p>{upcoming.item.serviceName || upcoming.session.title} · {dateLabel(new Date(upcoming.startsAt).toISOString())}</p>
          <span className="treatment-muted">{statusLabel(upcoming.item.appointmentStatus)}</span>
        </div>
        <Button variant="outline" onClick={() => onOpen(upcoming.course.id)}>Mở hồ sơ</Button>
      </section>}
      <section id="treatment-records" tabIndex={-1} className="treatment-stack customer-treatment-records" aria-label="Hồ sơ và tiến độ">
        <h2>Hồ sơ và tiến độ</h2>
        {courses.map(course => (
          <article className="treatment-card customer-treatment-record" key={course.id} data-treatment-course-id={course.id}>
            <div className="treatment-card-heading">
              <h3>{course.name}</h3>
              <span className="treatment-badge" data-status={course.status}>{statusLabel(course.status)}</span>
            </div>
            <p className="treatment-muted">Hồ sơ #{course.id} · {course.completedSessions}/{course.totalSessions} buổi hoàn thành</p>
            {(course.sessions || []).length > 0 && (
              <ol className="customer-treatment-progress" aria-label="Trạng thái từng buổi">
                {course.sessions.map(session => (
                  <li key={session.id ?? session.sequence} data-status={session.status}>
                    <span className="customer-treatment-number" aria-hidden="true">
                      {session.status === 'COMPLETED' ? <Check size={15} /> : session.sequence}
                    </span>
                    <span title={session.title}>Buổi {session.sequence}</span>
                    <small>{statusLabel(session.status)}</small>
                  </li>
                ))}
              </ol>
            )}
            <div className="treatment-actions">
              <Button variant="outline" onClick={() => onOpen(course.id)}>Mở hồ sơ & tiến độ<ArrowRight size={15} /></Button>
              {course.expiryDate && <span className="treatment-muted">Hiệu lực đến {dateLabel(course.expiryDate)}</span>}
            </div>
          </article>
        ))}
      </section>
    </>
  );
}

export function CustomerTicketHeading({ ticket }) {
  const hasUsage = ticket.totalSessions > 0 && ticket.usedSessions != null;
  const usage = hasUsage ? Math.min(100, Math.max(0, 100 * ticket.usedSessions / ticket.totalSessions)) : 0;
  return (
    <div className="customer-treatment-ticket-heading">
      <div>
        <small>Vé #{ticket.id}{ticket.orderId != null && ` · Đơn #${ticket.orderId}`}</small>
        <h3>{ticket.packageName || `Gói dịch vụ #${ticket.packageId}`}</h3>
        <span>{ticket.status === 'ACTIVE' ? 'Đang hiệu lực' : statusLabel(ticket.status)}</span>
      </div>
      {hasUsage && <div className="customer-treatment-usage" style={{ '--usage': `${usage}%` }}>
        <span><small>Đã dùng</small><strong>{ticket.usedSessions}/{ticket.totalSessions}</strong></span>
      </div>}
    </div>
  );
}

const eventIcons = { SESSION_BOOKED: CalendarDays, SESSION_REMINDER: CalendarDays, PURCHASE_PAID: CreditCard, PAYMENT: CreditCard, SESSION_COMPLETED: FileText, SESSION_RESULT: FileText };

export function CustomerTreatmentNotifications({ notifications, authenticated, loading, onOpen }) {
  if (!authenticated) return <div className="treatment-empty"><h2>Đăng nhập để xem thông báo</h2><a className="treatment-link" href="#/login">Đăng nhập</a></div>;
  return (
    <div id="treatment-notifications" tabIndex={-1} className="treatment-stack customer-treatment-notifications">
      {notifications.map(item => {
        const Icon = eventIcons[item.kind] || Bell;
        return (
          <article className="treatment-card customer-treatment-event" key={item.id} data-unread={!item.readAt}>
            <span className="customer-treatment-event-icon"><Icon size={22} aria-hidden="true" /></span>
            <div>
              <h2>{item.title}</h2>
              {item.message && <p>{item.message}</p>}
              <small>{[item.createdAt && dateLabel(item.createdAt), item.courseId != null && `Hồ sơ #${item.courseId}`].filter(Boolean).join(' · ')}</small>
            </div>
            <div className="customer-treatment-event-actions">
              {!item.readAt && <span className="treatment-badge">Chưa đọc</span>}
              {item.courseId != null && <Button variant="outline" size="sm" onClick={() => onOpen(item)}>Mở hồ sơ #{item.courseId}</Button>}
            </div>
          </article>
        );
      })}
      {!loading && notifications.length === 0 && <div className="treatment-empty">Chưa có thông báo lộ trình.</div>}
    </div>
  );
}
