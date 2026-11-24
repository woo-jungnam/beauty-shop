import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, CalendarDays, Plus, RefreshCw, Search, Sparkles } from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { Button } from '../../shared/ui/Button';
import { apiClient } from '../../shared/api/client';
import { TreatmentCourseDetail } from './TreatmentCourseDetail';
import { TreatmentActionDialog } from './TreatmentActionDialog';
import { TreatmentPurchaseDialog } from './TreatmentPurchaseDialog';
import { openTreatmentPayment } from './paymentNavigation';
import { TreatmentTemplateEditor } from './TreatmentTemplateEditor';
import { TicketDetails } from '../spa/TicketDetails';
import { Pagination } from '../../customer/components/account/AccountTools';
import { TREATMENT_HERO_POSITION } from '../banners/bannerPositions';
import { CustomerTicketHeading, CustomerTreatmentCatalog, CustomerTreatmentCourses, CustomerTreatmentHero, CustomerTreatmentNotifications } from './CustomerTreatmentViews';
import { dateLabel, errorMessage, hasAction, money, rows, statusLabel, templateRoot, treatmentApi, unwrap } from './treatmentApi';
import { OperationalTreatmentCourses, OperationalTreatmentTemplates } from './OperationalTreatmentLists';
import './treatments.css';

const reportFields = { pendingIntake: 'Chờ tiếp nhận', activeCourses: 'Đang điều trị', completedCourses: 'Hoàn thành', sessionsDue: 'Buổi đến hạn' };
const customerLists = {
  catalog: { size: 3, id: 'treatment-catalog', label: 'Phân trang lộ trình có sẵn' },
  courses: { size: 6, id: 'treatment-records', label: 'Phân trang hồ sơ lộ trình' },
  notifications: { size: 6, id: 'treatment-notifications', label: 'Phân trang thông báo lộ trình' },
};

export const TreatmentWorkspace = ({ role = 'customer', onNavigate, initialCourseId = null, initialTab = null }) => {
  const { isAuthenticated, user } = useAuth();
  const admin = role === 'admin';
  const customer = role === 'customer';
  const [tab, setTab] = useState(initialTab || (customer ? 'catalog' : 'courses'));
  const [templates, setTemplates] = useState([]);
  const [courses, setCourses] = useState([]);
  const [tickets, setTickets] = useState([]);
  const [pendingOrders, setPendingOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [services, setServices] = useState([]);
  const [treatmentBanner, setTreatmentBanner] = useState(null);
  const [report, setReport] = useState(null);
  const [courseId, setCourseId] = useState(initialCourseId);
  const [course, setCourse] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('ALL');
  const [pages, setPages] = useState({});
  const [editor, setEditor] = useState(null);
  const [purchase, setPurchase] = useState(null);
  const [action, setAction] = useState(null);
  const requestNumber = useRef(0);
  useEffect(() => { setCourseId(initialCourseId); }, [initialCourseId]);
  useEffect(() => { if (initialTab) setTab(initialTab); }, [initialTab]);
  useEffect(() => { setPurchase(null); setEditor(null); setAction(null); setNotice(null); setPages({}); }, [user?.id]);
  useEffect(() => {
    if (!customer) return;
    const controller = new AbortController();
    const options = { signal: controller.signal };
    apiClient.get('/api/v1/spa/services', options).then(response => {
      if (!controller.signal.aborted) setServices(rows(response));
    }).catch(() => {});
    return () => controller.abort();
  }, [customer]);
  const refresh = useCallback(async (message, tone = 'success') => {
    const request = ++requestNumber.current;
    setRefreshing(true); setError('');
    if (typeof message === 'string') setNotice({ message, tone });
    const jobs = [['templates', () => treatmentApi.templates(admin)]];
    if (customer) jobs.push(['treatmentBanner', async () => {
      const response = await apiClient.get(`/api/v1/banners?position=${TREATMENT_HERO_POSITION}`).catch(() => null);
      return rows(response).filter(item => item.position === TREATMENT_HERO_POSITION && item.isActive !== false && item.imageUrl)
        .sort((first, second) => (first.sortOrder ?? 0) - (second.sortOrder ?? 0) || first.id - second.id)[0] ?? null;
    }]);
    if (isAuthenticated && user?.id != null) {
      const reconciliation = customer ? apiClient.post('/api/v1/spa/tickets/my-tickets/reconcile') : Promise.resolve();
      if (customer) jobs.push(['sync', () => reconciliation]);
      const reconciled = reconciliation.catch(() => {});
      jobs.push(['courses', async () => { await reconciled; return treatmentApi.courses(role); }]);
      if (customer) jobs.push(['tickets', async () => { await reconciled; return rows(await apiClient.get('/api/v1/spa/tickets/my-tickets')); }]);
      if (courseId) jobs.push(['course', () => treatmentApi.course(role, courseId)]);
      if (admin) jobs.push(['report', async () => unwrap(await apiClient.get('/api/v1/admin/spa/treatments/report'))]);
      if (customer) { jobs.push(['orders', async () => rows(await apiClient.get('/api/v1/orders/my-orders?size=100&sort=createdAt,desc'))]); jobs.push(['notifications', async () => rows(await apiClient.get('/api/v1/spa/treatment-notifications'))]); }
    }
    const results = await Promise.allSettled(jobs.map(([, job]) => job()));
    if (request !== requestNumber.current) return;
    const failures = [];
    results.forEach((result, index) => {
      const kind = jobs[index][0];
      if (result.status === 'rejected') { failures.push(`${({ templates: 'Danh mục', sync: 'Đối soát gói đã thanh toán', courses: 'Hồ sơ', tickets: 'Gói dịch vụ đã mua', course: 'Chi tiết hồ sơ', report: 'Báo cáo', orders: 'Đơn chờ thanh toán', notifications: 'Thông báo' })[kind]}: ${errorMessage(result.reason)}`); if (kind === 'course') setCourse(null); return; }
      if (kind === 'templates') setTemplates(result.value);
      else if (kind === 'treatmentBanner') setTreatmentBanner(result.value);
      else if (kind === 'courses') setCourses(result.value);
      else if (kind === 'tickets') setTickets(result.value);
      else if (kind === 'course') setCourse(result.value);
      else if (kind === 'report') setReport(result.value);
      else if (kind === 'orders') setPendingOrders(result.value.filter(order => order.servicePackageId && order.paymentStatus === 'PENDING' && !['CANCELLED', 'EXPIRED'].includes(order.status)));
      else if (kind === 'notifications') setNotifications(result.value);
    });
    setError(failures.join('\n')); setLoading(false); setRefreshing(false);
  }, [admin, customer, isAuthenticated, role, courseId, user?.id]);
  useEffect(() => {
    setCourse(null); setCourses([]); setTickets([]); setNotifications([]); setPendingOrders([]); setLoading(true); refresh();
    return () => { requestNumber.current += 1; };
  }, [refresh]);
  useEffect(() => {
    const onFocus = () => { if (!document.hidden) refresh(); };
    window.addEventListener('focus', onFocus); document.addEventListener('visibilitychange', onFocus);
    const interval = window.setInterval(onFocus, 30000);
    return () => { window.removeEventListener('focus', onFocus); document.removeEventListener('visibilitychange', onFocus); window.clearInterval(interval); };
  }, [refresh]);
  const selectCourse = id => { setCourseId(id); if (customer) window.history.replaceState(null, '', `#/treatments?course=${id}`); };
  const selectTab = value => { setTab(value); setCourseId(null); if (!customer) { setKeyword(''); setStatus('ALL'); } if (customer) window.history.replaceState(null, '', `#/treatments?tab=${value}`); };
  const matches = row => `${row.name || ''} ${row.customerName || ''} ${row.customerPhone || ''} ${row.id} ${row.userId || ''}`.toLocaleLowerCase('vi').includes(keyword.toLocaleLowerCase('vi')) && (status === 'ALL' || row.status === status);
  const packageTickets = tickets.filter(ticket => !courses.some(item => String(item.id) === String(ticket.treatmentCourseId)));
  const visibleTickets = packageTickets.filter(ticket => matches({ ...ticket, name: ticket.packageName }));
  const visibleTemplates = templates.filter(matches);
  const visibleCourses = courses.filter(matches);
  const list = customer ? customerLists[tab] : null;
  const listRows = { catalog: visibleTemplates, courses: visibleCourses, notifications }[tab] || [];
  const totalPages = list ? Math.max(1, Math.ceil(listRows.length / list.size)) : 1;
  const requestedPage = pages[tab] ?? 0;
  const page = Math.min(requestedPage, totalPages - 1);
  const pageRows = list ? listRows.slice(page * list.size, (page + 1) * list.size) : listRows;

  useEffect(() => {
    if (list && !loading && !courseId && requestedPage !== page) {
      setPages(previous => ({ ...previous, [tab]: page }));
    }
  }, [list, loading, courseId, requestedPage, page, tab]);

  const resetFilteredPages = () => setPages(previous => ({ ...previous, catalog: 0, courses: 0 }));
  const changePage = (nextPage) => {
    setPages(previous => ({ ...previous, [tab]: nextPage }));
    const region = document.getElementById(list.id);
    region?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
    region?.focus({ preventScroll: true });
  };
  const templateAction = (template, command, label) => setAction({ title: `${label}: ${template.name}`, description: command === 'publish' ? 'Xuất bản làm phiên bản đang bán. Các hồ sơ đã mua tiếp tục sử dụng snapshot đã chấp thuận.' : command === 'archive' ? 'Ngừng bán cho khách mới. Hồ sơ đã mua vẫn giữ nguyên quyền lợi và tiến độ.' : 'Tạo bản nháp mới từ phiên bản hiện tại để chỉnh sửa; phiên bản đã mua được giữ nguyên.', fields: [], submitLabel: label, submit: () => treatmentApi.post(`${templateRoot(true)}/${template.id}/${command}`), success: `Đã ${label.toLowerCase()} lộ trình.` });
  const showNotification = async notification => {
    selectCourse(notification.courseId);
    if (!notification.readAt) {
      try {
        const updated = unwrap(await apiClient.put(`/api/v1/spa/treatment-notifications/${notification.id}/read`));
        if (updated?.readAt) setNotifications(previous => previous.map(row => row.id === notification.id ? updated : row));
      } catch (err) { setNotice({ message: errorMessage(err), tone: 'warning' }); }
    }
  };
  if (!customer && !isAuthenticated) return <div className="treatment-workspace"><div className="treatment-empty"><h1>Vui lòng đăng nhập</h1><a href="#/login">Đăng nhập để mở hồ sơ điều trị</a></div></div>;
  return <section className={`treatment-workspace${customer ? ' is-customer' : ''}`} aria-label={customer ? 'Lộ trình điều trị của khách hàng' : 'Quản lý lộ trình điều trị'}>
    {!courseId && <>{customer && (
      <nav className="treatment-breadcrumb" aria-label="Đường dẫn lộ trình" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--c-text-light, #716762)', marginBottom: 16 }}>
        <button type="button" onClick={() => onNavigate ? onNavigate('') : (window.location.hash = '#/')} style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <ArrowLeft size={14} /> Trang chủ
        </button>
        <span aria-hidden="true">/</span>
        <span style={{ color: 'var(--c-primary, #721B29)', fontWeight: 600 }}>Điều trị theo lộ trình</span>
      </nav>
    )}{customer ? <CustomerTreatmentHero banner={treatmentBanner} compact={tab !== 'catalog'} /> : <header className="treatment-heading"><div><h1>Liệu trình chuyên sâu</h1></div><div className="treatment-actions">{admin && tab === 'catalog' && <Button onClick={() => setEditor({ template: null })}><Plus size={16} />Tạo mẫu lộ trình</Button>}<Button variant="outline" loading={refreshing} onClick={() => refresh()}><RefreshCw size={16} />Làm mới</Button></div></header>}
      <div className="treatment-toolbar"><nav className="treatment-tabs" aria-label="Nội dung lộ trình"><button type="button" aria-pressed={tab === 'catalog'} onClick={() => selectTab('catalog')}>{admin ? 'Mẫu & phiên bản' : 'Lộ trình có sẵn'}</button><button type="button" aria-pressed={tab === 'courses'} onClick={() => selectTab('courses')}>{customer ? 'Lộ trình của tôi' : 'Hồ sơ điều trị'}</button>{customer && <button type="button" aria-pressed={tab === 'notifications'} onClick={() => selectTab('notifications')}>Thông báo {notifications.some(row => !row.readAt) ? `(${notifications.filter(row => !row.readAt).length})` : ''}</button>}{admin && <button type="button" aria-pressed={tab === 'report'} onClick={() => selectTab('report')}>Báo cáo</button>}</nav>{customer && <><a className="treatment-link" href="#/booking">Đặt dịch vụ một lần →</a><div className="customer-treatment-refresh"><Button variant="outline" loading={refreshing} onClick={() => refresh()}><RefreshCw size={16} />Làm mới</Button></div></>}</div></>}
    {notice && <div className={`treatment-notice ${notice.tone === 'success' ? '' : notice.tone}`} role={notice.tone === 'success' ? 'status' : 'alert'}>{notice.message}<Button size="sm" variant="ghost" onClick={() => setNotice(null)} aria-label="Đóng thông báo lộ trình">×</Button></div>}
    {error && <div className="treatment-notice error" role="alert">{error}<div className="treatment-actions"><Button variant="outline" disabled={refreshing} onClick={() => refresh()}>Thử tải lại</Button></div></div>}
    {loading && <p role="status">Đang tải lộ trình và trạng thái mới nhất…</p>}
    {courseId ? course ? <TreatmentCourseDetail course={course} role={role} refresh={refresh} refreshing={refreshing} onBack={() => selectTab('courses')} /> : !loading && <div className="treatment-empty"><p>Chưa mở được hồ sơ. Vui lòng kiểm tra quyền truy cập hoặc tải lại.</p><Button variant="outline" onClick={() => selectTab('courses')}>Quay về danh sách</Button></div> : <>
      {(tab === 'catalog' || tab === 'courses') && <div className="treatment-toolbar"><label className="treatment-search" style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Search size={16} /><input style={{ padding: '10px 14px', border: '1px solid #d7dce3', borderRadius: 9 }} aria-label="Tìm lộ trình theo tên hoặc mã hồ sơ" placeholder={customer ? 'Tìm tên hoặc mã hồ sơ…' : tab === 'catalog' ? 'Tìm mẫu liệu trình…' : 'Tìm khách, SĐT hoặc liệu trình…'} value={keyword} onChange={event => { setKeyword(event.target.value); resetFilteredPages(); }} /></label><select style={{ padding: '10px 14px', border: '1px solid #d7dce3', borderRadius: 9, background: '#fff' }} aria-label="Lọc trạng thái lộ trình" value={status} onChange={event => { setStatus(event.target.value); resetFilteredPages(); }}><option value="ALL">Tất cả trạng thái</option>{[...new Set((tab === 'catalog' ? templates : [...courses, ...packageTickets]).map(row => row.status))].map(value => <option value={value} key={value}>{statusLabel(value)}</option>)}</select></div>}
      {tab === 'catalog' && (customer ? <CustomerTreatmentCatalog templates={pageRows} services={services} loading={loading} onPurchase={template => isAuthenticated ? setPurchase(template) : (window.location.hash = '/login')} /> : <><OperationalTreatmentTemplates templates={visibleTemplates} renderActions={template => <>{admin && hasAction(template, 'EDIT') && <Button variant="outline" onClick={() => setEditor({ template })}>Sửa bản nháp</Button>}{admin && hasAction(template, 'CREATE_VERSION') && <Button variant="outline" onClick={() => templateAction(template, 'versions', 'Tạo phiên bản mới')}>Tạo phiên bản mới</Button>}{admin && hasAction(template, 'PUBLISH') && <Button onClick={() => templateAction(template, 'publish', 'Xuất bản')}>Xuất bản</Button>}{admin && hasAction(template, 'ARCHIVE') && <Button variant="outline" onClick={() => templateAction(template, 'archive', 'Ngừng bán')}>Ngừng bán</Button>}</>} />{!loading && !visibleTemplates.length && <div className="treatment-empty"><Sparkles size={32} /><strong>Chưa có lộ trình phù hợp</strong><p>{admin ? 'Tạo dịch vụ loại lộ trình rồi thiết kế mẫu có các buổi cụ thể.' : 'Vui lòng thử lại sau hoặc chọn dịch vụ một lần.'}</p></div>}</>)}
      {tab === 'courses' && <div className="treatment-stack">{!isAuthenticated && <div className="treatment-empty"><h2>Đăng nhập để theo dõi lộ trình</h2><a className="treatment-link" href="#/login">Đăng nhập</a></div>}{customer && pendingOrders.length > 0 && <section className="treatment-card"><h2>Đơn Spa chờ thanh toán</h2><p>Đơn chưa thanh toán chưa cấp hồ sơ điều trị. Tiếp tục thanh toán đơn hiện có để tránh mua lại.</p>{pendingOrders.map(order => <div className="treatment-card-heading" key={order.id}><span>#{order.orderNumber || order.id} · {money(order.totalAmount)}{order.paymentDeadline ? ` · Hạn ${dateLabel(order.paymentDeadline)}` : ''}</span><Button size="sm" variant="outline" onClick={() => openTreatmentPayment(order)}>Tiếp tục thanh toán</Button></div>)}</section>}
        {customer && visibleTickets.length > 0 && <section aria-label="Gói dịch vụ đã mua">
          <h2>Gói dịch vụ đã mua</h2>
          <div className="treatment-grid">{visibleTickets.map(ticket => <article className="treatment-card" key={ticket.id} data-service-ticket-id={ticket.id}>
            <CustomerTicketHeading ticket={ticket} />
            <p>{ticket.remainingSessions} lượt khả dụng / {ticket.totalSessions} tổng lượt</p>
            <p className="treatment-muted">{ticket.expiryDate ? `Hiệu lực đến ${dateLabel(ticket.expiryDate)}` : 'Không giới hạn thời hạn'}</p>
            <TicketDetails ticket={ticket} onNavigate={onNavigate || (destination => { window.location.hash = `#/${destination}`; })} />
          </article>)}</div>
        </section>}
        {(customer ? <CustomerTreatmentCourses courses={pageRows} allCourses={visibleCourses} onOpen={selectCourse} /> : <OperationalTreatmentCourses courses={visibleCourses} onOpen={selectCourse} />)}{isAuthenticated && !loading && !visibleCourses.length && (!customer || !visibleTickets.length) && <div className="treatment-empty"><CalendarDays size={32} /><strong>Chưa có hồ sơ điều trị phù hợp</strong><p>{customer ? 'Hồ sơ được cấp tự động sau khi thanh toán lộ trình thành công.' : 'Hồ sơ hiển thị theo phạm vi được phân công của tài khoản.'}</p>{customer && <Button variant="outline" onClick={() => selectTab('catalog')}>Khám phá lộ trình có sẵn</Button>}</div>}</div>}
      {tab === 'notifications' && customer && <CustomerTreatmentNotifications notifications={pageRows} authenticated={isAuthenticated} loading={loading} onOpen={showNotification} />}
      {list && !loading && totalPages > 1 && (
        <div className="customer-treatment-pagination">
          <Pagination page={page} totalPages={totalPages} onChange={changePage} label={list.label} />
        </div>
      )}
      {tab === 'report' && admin && report && <div className="treatment-report-grid">{Object.entries(reportFields).map(([key, label]) => <article className="treatment-card" key={key}><span className="treatment-muted">{label}</span><strong>{report[key] ?? 0}</strong></article>)}</div>}
    </>}
    {editor && <TreatmentTemplateEditor template={editor.template} onClose={() => setEditor(null)} onSaved={refresh} />}
    {purchase && <TreatmentPurchaseDialog template={purchase} onClose={() => setPurchase(null)} onPurchased={() => setPurchase(null)} />}
    {action && <TreatmentActionDialog action={action} onClose={() => setAction(null)} onSaved={refresh} />}
  </section>;
};

export const TreatmentAdminPage = () => { const { isAdmin } = useAuth(); return <TreatmentWorkspace role={isAdmin ? 'admin' : 'staff'} />; };
