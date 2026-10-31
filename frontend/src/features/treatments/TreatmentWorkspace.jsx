import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Bell, CalendarDays, Clock, Plus, RefreshCw, Search, Sparkles } from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { Button } from '../../shared/ui/Button';
import { apiClient } from '../../shared/api/client';
import { TreatmentCourseDetail } from './TreatmentCourseDetail';
import { TreatmentActionDialog } from './TreatmentActionDialog';
import { TreatmentPurchaseDialog } from './TreatmentPurchaseDialog';
import { openTreatmentPayment } from './paymentNavigation';
import { TreatmentTemplateEditor } from './TreatmentTemplateEditor';
import { dateLabel, errorMessage, hasAction, money, rows, statusLabel, templateRoot, treatmentApi, unwrap } from './treatmentApi';
import './treatments.css';

const reportFields = { totalCourses: 'Tổng hồ sơ', pendingIntake: 'Chờ tiếp nhận', activeCourses: 'Đang điều trị', pausedCourses: 'Tạm dừng', completedCourses: 'Hoàn thành', expiredCourses: 'Hết hiệu lực', sessionsDue: 'Buổi đến hạn', customersWithoutNextBooking: 'Khách chưa đặt buổi tiếp', collectedAmount: 'Đã thu', refundedAmount: 'Đã hoàn', unusedEntitlementAmount: 'Quyền lợi chưa sử dụng' };

export const TreatmentWorkspace = ({ role = 'customer', onNavigate, initialCourseId = null, initialTab = null }) => {
  const { isAuthenticated, user } = useAuth();
  const admin = role === 'admin';
  const customer = role === 'customer';
  const [tab, setTab] = useState(initialTab || (customer ? 'catalog' : 'courses'));
  const [templates, setTemplates] = useState([]);
  const [courses, setCourses] = useState([]);
  const [pendingOrders, setPendingOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [report, setReport] = useState(null);
  const [courseId, setCourseId] = useState(initialCourseId);
  const [course, setCourse] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('ALL');
  const [editor, setEditor] = useState(null);
  const [purchase, setPurchase] = useState(null);
  const [action, setAction] = useState(null);
  const requestNumber = useRef(0);
  useEffect(() => { setCourseId(initialCourseId); }, [initialCourseId]);
  useEffect(() => { if (initialTab) setTab(initialTab); }, [initialTab]);
  useEffect(() => { setPurchase(null); setEditor(null); setAction(null); setNotice(null); }, [user?.id]);
  const refresh = useCallback(async (message, tone = 'success') => {
    const request = ++requestNumber.current;
    setRefreshing(true); setError('');
    if (typeof message === 'string') setNotice({ message, tone });
    const jobs = [['templates', () => treatmentApi.templates(admin)]];
    if (isAuthenticated && user?.id != null) {
      jobs.push(['courses', () => treatmentApi.courses(role)]);
      if (courseId) jobs.push(['course', () => treatmentApi.course(role, courseId)]);
      if (admin) jobs.push(['report', async () => unwrap(await apiClient.get('/api/v1/admin/spa/treatments/report'))]);
      if (customer) { jobs.push(['orders', async () => rows(await apiClient.get('/api/v1/orders/my-orders?size=100&sort=createdAt,desc'))]); jobs.push(['notifications', async () => rows(await apiClient.get('/api/v1/spa/treatment-notifications'))]); }
    }
    const results = await Promise.allSettled(jobs.map(([, job]) => job()));
    if (request !== requestNumber.current) return;
    const failures = [];
    results.forEach((result, index) => {
      const kind = jobs[index][0];
      if (result.status === 'rejected') { failures.push(`${({ templates: 'Danh mục', courses: 'Hồ sơ', course: 'Chi tiết hồ sơ', report: 'Báo cáo', orders: 'Đơn chờ thanh toán', notifications: 'Thông báo' })[kind]}: ${errorMessage(result.reason)}`); if (kind === 'course') setCourse(null); return; }
      if (kind === 'templates') setTemplates(result.value);
      else if (kind === 'courses') setCourses(result.value);
      else if (kind === 'course') setCourse(result.value);
      else if (kind === 'report') setReport(result.value);
      else if (kind === 'orders') setPendingOrders(result.value.filter(order => order.servicePackageId && order.paymentStatus === 'PENDING' && !['CANCELLED', 'EXPIRED'].includes(order.status)));
      else if (kind === 'notifications') setNotifications(result.value);
    });
    setError(failures.join('\n')); setLoading(false); setRefreshing(false);
  }, [admin, customer, isAuthenticated, role, courseId, user?.id]);
  useEffect(() => {
    setCourse(null); setCourses([]); setNotifications([]); setPendingOrders([]); setLoading(true); refresh();
    return () => { requestNumber.current += 1; };
  }, [refresh]);
  useEffect(() => {
    const onFocus = () => { if (!document.hidden) refresh(); };
    window.addEventListener('focus', onFocus); document.addEventListener('visibilitychange', onFocus);
    const interval = window.setInterval(onFocus, 30000);
    return () => { window.removeEventListener('focus', onFocus); document.removeEventListener('visibilitychange', onFocus); window.clearInterval(interval); };
  }, [refresh]);
  const selectCourse = id => { setCourseId(id); if (customer) window.history.replaceState(null, '', `#/treatments?course=${id}`); };
  const selectTab = value => { setTab(value); setCourseId(null); if (customer) window.history.replaceState(null, '', `#/treatments?tab=${value}`); };
  const matches = row => `${row.name || ''} ${row.id} ${row.userId || ''}`.toLocaleLowerCase('vi').includes(keyword.toLocaleLowerCase('vi')) && (status === 'ALL' || row.status === status);
  const templateAction = (template, command, label) => setAction({ title: `${label}: ${template.name}`, description: command === 'publish' ? 'Xuất bản làm phiên bản đang bán. Các hồ sơ đã mua tiếp tục sử dụng snapshot đã chấp thuận.' : command === 'archive' ? 'Ngừng bán cho khách mới. Hồ sơ đã mua vẫn giữ nguyên quyền lợi và tiến độ.' : 'Tạo bản nháp mới từ phiên bản hiện tại để chỉnh sửa; phiên bản đã mua được giữ nguyên.', fields: [], submitLabel: label, submit: () => treatmentApi.post(`${templateRoot(true)}/${template.id}/${command}`), success: `Đã ${label.toLowerCase()} lộ trình.` });
  const showNotification = async notification => {
    selectCourse(notification.courseId);
    try { await apiClient.put(`/api/v1/spa/treatment-notifications/${notification.id}/read`); setNotifications(previous => previous.map(row => row.id === notification.id ? { ...row, readAt: new Date().toISOString() } : row)); }
    catch (err) { setNotice({ message: errorMessage(err), tone: 'warning' }); }
  };
  if (!customer && !isAuthenticated) return <div className="treatment-workspace"><div className="treatment-empty"><h1>Vui lòng đăng nhập</h1><a href="#/login">Đăng nhập để mở hồ sơ điều trị</a></div></div>;
  return <section className="treatment-workspace" aria-label={customer ? 'Lộ trình điều trị của khách hàng' : 'Quản lý lộ trình điều trị'}>
    {!courseId && <>{customer && (
      <nav className="treatment-breadcrumb" aria-label="Đường dẫn lộ trình" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--c-text-light, #716762)', marginBottom: 16 }}>
        <button type="button" onClick={() => onNavigate ? onNavigate('') : (window.location.hash = '#/')} style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <ArrowLeft size={14} /> Trang chủ
        </button>
        <span aria-hidden="true">/</span>
        <span style={{ color: 'var(--c-primary, #721B29)', fontWeight: 600 }}>Điều trị theo lộ trình</span>
      </nav>
    )}<header className="treatment-heading"><div><span className="treatment-eyebrow">BEAUTYSHOP · HÀNH TRÌNH CHĂM SÓC</span><h1>{customer ? 'Điều trị theo lộ trình' : 'Quản lý lộ trình điều trị'}</h1><p>{customer ? 'Chọn kế hoạch có sẵn, được tiếp nhận riêng và theo dõi kết quả từng buổi.' : 'Tiếp nhận, điều phối và theo dõi hồ sơ xuyên suốt các buổi điều trị.'}</p></div><div className="treatment-actions">{admin && <Button onClick={() => setEditor({ template: null })}><Plus size={16} />Tạo mẫu lộ trình</Button>}<Button variant="outline" loading={refreshing} onClick={() => refresh()}><RefreshCw size={16} />Làm mới</Button></div></header>
      <div className="treatment-toolbar"><nav className="treatment-tabs" aria-label="Nội dung lộ trình"><button type="button" aria-pressed={tab === 'catalog'} onClick={() => selectTab('catalog')}>{admin ? 'Mẫu & phiên bản' : 'Lộ trình có sẵn'}</button><button type="button" aria-pressed={tab === 'courses'} onClick={() => selectTab('courses')}>{customer ? 'Lộ trình của tôi' : 'Hồ sơ điều trị'}</button>{customer && <button type="button" aria-pressed={tab === 'notifications'} onClick={() => selectTab('notifications')}>Thông báo {notifications.some(row => !row.readAt) ? `(${notifications.filter(row => !row.readAt).length})` : ''}</button>}{admin && <button type="button" aria-pressed={tab === 'report'} onClick={() => selectTab('report')}>Báo cáo</button>}</nav>{customer && <a className="treatment-link" href="#/booking">Đặt dịch vụ một lần →</a>}</div></>}
    {notice && <div className={`treatment-notice ${notice.tone === 'success' ? '' : notice.tone}`} role={notice.tone === 'success' ? 'status' : 'alert'}>{notice.message}<Button size="sm" variant="ghost" onClick={() => setNotice(null)} aria-label="Đóng thông báo lộ trình">×</Button></div>}
    {error && <div className="treatment-notice error" role="alert">{error}<div className="treatment-actions"><Button variant="outline" disabled={refreshing} onClick={() => refresh()}>Thử tải lại</Button></div></div>}
    {loading && <p role="status">Đang tải lộ trình và trạng thái mới nhất…</p>}
    {courseId ? course ? <TreatmentCourseDetail course={course} role={role} refresh={refresh} refreshing={refreshing} onBack={() => selectTab('courses')} /> : !loading && <div className="treatment-empty"><p>Chưa mở được hồ sơ. Vui lòng kiểm tra quyền truy cập hoặc tải lại.</p><Button variant="outline" onClick={() => selectTab('courses')}>Quay về danh sách</Button></div> : <>
      {(tab === 'catalog' || tab === 'courses') && <div className="treatment-toolbar"><label className="treatment-search" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Search size={16} /><input style={{ padding: '10px 14px', border: '1px solid #d7dce3', borderRadius: 9 }} aria-label="Tìm lộ trình theo tên hoặc mã hồ sơ" placeholder="Tìm tên hoặc mã hồ sơ…" value={keyword} onChange={event => setKeyword(event.target.value)} /></label><select style={{ padding: '10px 14px', border: '1px solid #d7dce3', borderRadius: 9, background: '#fff' }} aria-label="Lọc trạng thái lộ trình" value={status} onChange={event => setStatus(event.target.value)}><option value="ALL">Tất cả trạng thái</option>{[...new Set((tab === 'catalog' ? templates : courses).map(row => row.status))].map(value => <option value={value} key={value}>{statusLabel(value)}</option>)}</select></div>}
      {tab === 'catalog' && <div className="treatment-grid">{templates.filter(matches).map(template => <article className="treatment-card" key={template.id} data-treatment-template-id={template.id}><div className="treatment-card-heading"><span className="treatment-badge"><Sparkles size={13} />Điều trị theo lộ trình</span><small>v{template.versionNumber} · {statusLabel(template.status)}</small></div><h2>{template.name}</h2><p>{template.description}</p><div className="treatment-price">{money(template.price)}</div><div className="treatment-meta"><span><CalendarDays size={14} />{template.sessions?.length || 0} buổi</span><span><Clock size={14} />{template.validityDays} ngày</span></div><ol className="treatment-mini-sessions">{(template.sessions || []).map(session => <li key={session.sequence}>{session.title}<small>{(session.items || []).map(item => item.serviceName || `Dịch vụ #${item.serviceId}`).join(', ')} · Cách tối thiểu {session.minGapDays || 0} ngày</small></li>)}</ol><details><summary>Điều khoản sử dụng</summary><p>{template.terms}</p></details><div className="treatment-actions">{customer && hasAction(template, 'PURCHASE') && <Button onClick={() => isAuthenticated ? setPurchase(template) : (window.location.hash = '/login')}>Xem & đăng ký lộ trình<ArrowRight size={15} /></Button>}{admin && hasAction(template, 'EDIT') && <Button variant="outline" onClick={() => setEditor({ template })}>Sửa bản nháp</Button>}{admin && hasAction(template, 'CREATE_VERSION') && <Button variant="outline" onClick={() => templateAction(template, 'versions', 'Tạo phiên bản mới')}>Tạo phiên bản mới</Button>}{admin && hasAction(template, 'PUBLISH') && <Button onClick={() => templateAction(template, 'publish', 'Xuất bản')}>Xuất bản</Button>}{admin && hasAction(template, 'ARCHIVE') && <Button variant="outline" onClick={() => templateAction(template, 'archive', 'Ngừng bán')}>Ngừng bán</Button>}</div></article>)}{!loading && !templates.filter(matches).length && <div className="treatment-empty"><Sparkles size={32} /><strong>Chưa có lộ trình phù hợp</strong><p>{admin ? 'Tạo dịch vụ loại lộ trình rồi thiết kế mẫu có các buổi cụ thể.' : 'Vui lòng thử lại sau hoặc chọn dịch vụ một lần.'}</p></div>}</div>}
      {tab === 'courses' && <div className="treatment-stack">{!isAuthenticated && <div className="treatment-empty"><h2>Đăng nhập để theo dõi lộ trình</h2><a className="treatment-link" href="#/login">Đăng nhập</a></div>}{customer && pendingOrders.length > 0 && <section className="treatment-card"><h2>Đơn Spa chờ thanh toán</h2><p>Đơn chưa thanh toán chưa cấp hồ sơ điều trị. Tiếp tục thanh toán đơn hiện có để tránh mua lại.</p>{pendingOrders.map(order => <div className="treatment-card-heading" key={order.id}><span>#{order.orderNumber || order.id} · {money(order.totalAmount)}{order.paymentDeadline ? ` · Hạn ${dateLabel(order.paymentDeadline)}` : ''}</span><Button size="sm" variant="outline" onClick={() => openTreatmentPayment(order)}>Tiếp tục thanh toán</Button></div>)}</section>}
        <div className="treatment-grid">{courses.filter(matches).map(item => <article className="treatment-card" key={item.id} data-treatment-course-id={item.id}><div className="treatment-card-heading"><small>Hồ sơ #{item.id}{!customer ? ` · Khách #${item.userId}` : ''}</small><span className="treatment-badge">{statusLabel(item.status)}</span></div><h2>{item.name}</h2><p>{item.completedSessions}/{item.totalSessions} buổi hoàn thành · Phiên bản {item.version}</p><div className="treatment-progress"><span style={{ width: `${item.progressPercent || 0}%` }} /></div><p className="treatment-muted">Hiệu lực đến {dateLabel(item.expiryDate)}</p><div className="treatment-actions"><Button variant="outline" onClick={() => selectCourse(item.id)}>Mở hồ sơ & tiến độ<ArrowRight size={15} /></Button></div></article>)}</div>{isAuthenticated && !loading && !courses.filter(matches).length && <div className="treatment-empty"><CalendarDays size={32} /><strong>Chưa có hồ sơ điều trị phù hợp</strong><p>{customer ? 'Hồ sơ được cấp tự động sau khi thanh toán lộ trình thành công.' : 'Hồ sơ hiển thị theo phạm vi được phân công của tài khoản.'}</p>{customer && <Button variant="outline" onClick={() => selectTab('catalog')}>Khám phá lộ trình có sẵn</Button>}</div>}</div>}
      {tab === 'notifications' && <div className="treatment-stack">{notifications.map(item => <article className="treatment-card" key={item.id}><div className="treatment-card-heading"><h2><Bell size={16} /> {item.title}</h2>{!item.readAt && <span className="treatment-badge">Chưa đọc</span>}</div><p>{item.message}</p><small>{dateLabel(item.createdAt)}</small><div className="treatment-actions"><Button variant="outline" onClick={() => showNotification(item)}>Mở hồ sơ #{item.courseId}</Button></div></article>)}{!notifications.length && <div className="treatment-empty">Chưa có thông báo lộ trình.</div>}</div>}
      {tab === 'report' && admin && report && <><div className="treatment-report-grid">{Object.entries(reportFields).map(([key, label]) => <article className="treatment-card" key={key}><span className="treatment-muted">{label}</span><strong>{key.endsWith('Amount') ? money(report[key]) : report[key] ?? 0}</strong></article>)}</div><div className="treatment-card" style={{ marginTop: 20 }}><h2>Hồ sơ theo trạng thái</h2><div className="treatment-table-wrap"><table className="treatment-table"><thead><tr><th>Trạng thái</th><th>Số hồ sơ</th></tr></thead><tbody>{Object.entries(report.byStatus || {}).map(([key, count]) => <tr key={key}><td>{statusLabel(key)}</td><td>{count}</td></tr>)}</tbody></table></div></div></>}
    </>}
    {editor && <TreatmentTemplateEditor template={editor.template} onClose={() => setEditor(null)} onSaved={refresh} />}
    {purchase && <TreatmentPurchaseDialog template={purchase} onClose={() => setPurchase(null)} onPurchased={() => setPurchase(null)} />}
    {action && <TreatmentActionDialog action={action} onClose={() => setAction(null)} onSaved={refresh} />}
  </section>;
};

export const TreatmentAdminPage = () => { const { isAdmin } = useAuth(); return <TreatmentWorkspace role={isAdmin ? 'admin' : 'staff'} />; };
