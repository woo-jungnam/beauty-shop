import { Button } from '../../shared/ui/Button';
import { getVietnamDateString, resolveMediaUrl } from '../../shared/utils/formatters';
import { dateLabel, money, statusLabel } from './treatmentApi';
import './operational-treatment-lists.css';

const sessionServices = session => [...new Set((session.items || [])
  .map(item => item.serviceName || (item.serviceId ? `Dịch vụ #${item.serviceId}` : null))
  .filter(Boolean))];

export function TreatmentSchedule({ sessions = [], template = false }) {
  if (!sessions.length) return <p className="treatment-muted">Chưa có lịch trình.</p>;

  return <ol className="operational-treatment-schedule">
    {sessions.map((session, index) => {
      const services = sessionServices(session);
      return <li key={session.id ?? session.sequence}>
        <span><strong>Buổi {session.sequence ?? index + 1}</strong><small>{template
          ? (session.minGapDays > 0 || session.maxGapDays != null)
            ? `Giãn cách tham khảo: ${session.minGapDays || 0}${session.maxGapDays != null ? `–${session.maxGapDays}` : '+'} ngày`
            : null
          : session.appointmentDate ? `${dateLabel(session.appointmentDate)}${session.startTime ? ` · ${session.startTime.slice(0, 5)}` : ''}` : statusLabel(session.status)}</small></span>
        <span>{session.title}{services.length > 0 && !(services.length === 1 && services[0] === session.title) && <small>{services.join(' · ')}</small>}</span>
      </li>;
    })}
  </ol>;
}

export function OperationalTreatmentCourses({ courses, onOpen }) {
  const today = getVietnamDateString();
  return <div className="operational-treatment-list">{courses.map(course => {
    const next = course.sessions?.filter(session => ['SCHEDULED', 'IN_PROGRESS'].includes(session.status)
      && session.appointmentDate >= today).sort((a, b) =>
      `${a.appointmentDate}T${a.startTime || ''}`.localeCompare(`${b.appointmentDate}T${b.startTime || ''}`))[0];
    return <article className="treatment-card operational-treatment-record" key={course.id} data-treatment-course-id={course.id}>
      <div className="operational-treatment-row">
        <div><h2>{course.customerName || `Khách #${course.userId}`}</h2><small>{[course.customerPhone, `Hồ sơ #${course.id}`].filter(Boolean).join(' · ')}</small></div>
        <div><strong>{course.name}</strong><small>{next ? `Buổi ${next.sequence} · ${dateLabel(next.appointmentDate)}${next.startTime ? ` · ${next.startTime.slice(0, 5)}` : ''}` : 'Chưa có lịch tiếp theo'}</small></div>
        <div><span className="treatment-badge">{statusLabel(course.status)}</span><small>{course.completedSessions ?? 0}/{course.totalSessions ?? 0} buổi hoàn thành</small><progress aria-label="Tiến độ liệu trình" max="100" value={course.progressPercent || 0} /></div>
        <Button variant="outline" size="sm" onClick={() => onOpen(course.id)}>Mở hồ sơ & tiến độ</Button>
      </div>
      <details><summary>Lịch trình từng buổi</summary><TreatmentSchedule sessions={course.sessions} /></details>
    </article>;
  })}</div>;
}

export function OperationalTreatmentTemplates({ templates, renderActions }) {
  return <div className="operational-treatment-list">{templates.map(template => (
    <article className="treatment-card operational-treatment-record" key={template.id} data-treatment-template-id={template.id}>
      <div className="treatment-card-heading">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
          {template.thumbnailUrl && (
            <img
              src={resolveMediaUrl(template.thumbnailUrl)}
              alt={template.name}
              style={{ width: 52, height: 52, borderRadius: 8, objectFit: 'cover', flexShrink: 0, border: '1px solid #ececf1' }}
              onError={event => { event.currentTarget.style.display = 'none'; }}
            />
          )}
          <div>
            <h2>{template.name}</h2>
            <small>
              {template.sessions?.length || 0} buổi
              {template.totalDurationMinutes > 0 ? ` · ${template.totalDurationMinutes} phút` : ''}
              {` · ${template.validityDays ? `${template.validityDays} ngày · ` : ''}v${template.versionNumber}`}
            </small>
          </div>
        </div>
        <span className="treatment-badge">{statusLabel(template.status)}</span>
      </div>
      <details>
        <summary>Dịch vụ và lịch trình</summary>
        <TreatmentSchedule sessions={template.sessions} template />
        {template.price != null && <p>Giá liệu trình <strong>{money(template.price)}</strong></p>}
        {template.description && <p>{template.description}</p>}
        {template.terms && <details><summary>Điều khoản sử dụng</summary><p>{template.terms}</p></details>}
      </details>
      <div className="treatment-actions">{renderActions(template)}</div>
    </article>
  ))}</div>;
}
