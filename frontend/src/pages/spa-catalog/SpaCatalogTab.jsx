import React, { useMemo, useRef, useState } from 'react';
import { Award, Building, Clock, Edit, Plus, RefreshCw, Search, ShieldCheck, Sparkles, Ticket, Trash2 } from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { Modal } from '../../shared/ui/Modal';
import { formatCurrency, resolveMediaUrl } from '../../shared/utils/formatters';
import { spaErrorMessage } from '../../shared/utils/spaMessages';
import { packageValue } from './catalogUtils';
import { useSpaCatalog } from './useSpaCatalog';
import { ServiceEditor } from './ServiceEditor';
import { PackageBuilder } from './PackageBuilder';
import SkillsMatrix from './SkillsMatrix';
import './spa-catalog.css';

const normalizedText = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase();
const ActiveBadge = ({ active }) => <span className={`spa-business-status ${active ? 'active' : 'paused'}`}><i />{active ? 'Đang hoạt động' : 'Tạm dừng'}</span>;
const Thumbnail = ({ item, packagePhoto = false }) => {
  const [failed, setFailed] = useState(false);
  return <div className={`spa-catalog-thumbnail ${packagePhoto ? 'package' : ''}`}>{item.thumbnailUrl && !failed
    ? <img src={resolveMediaUrl(item.thumbnailUrl)} alt={item.name} loading="lazy" onError={() => setFailed(true)} />
    : <><span>{packagePhoto ? <Ticket size={32} strokeWidth={1.3} /> : <Sparkles size={32} strokeWidth={1.3} />}</span><small>{packagePhoto ? 'LIỆU TRÌNH CHĂM SÓC' : 'BEAUTYSHOP ATELIER'}</small></>}</div>;
};

export const SpaCatalogTab = ({ onOpenPolicy }) => {
  const catalog = useSpaCatalog();
  const [view, setView] = useState('services');
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [serviceEditor, setServiceEditor] = useState(null);
  const [packageEditor, setPackageEditor] = useState(null);
  const [skills, setSkills] = useState(null);
  const [remove, setRemove] = useState(null);
  const [busy, setBusy] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [notice, setNotice] = useState(null);
  const deleteLock = useRef(false);
  const changed = (message, tone = 'success') => { setNotice({ message, tone }); catalog.refresh(); };
  const eligibleStaff = serviceId => catalog.staff.filter(staff => staff.active === true && staff.skills?.some(skill => String(skill.serviceId) === String(serviceId)));
  const groups = useMemo(() => {
    const indexed = new Map(catalog.categories.map(row => [String(row.id), row]));
    const result = new Map();
    const term = normalizedText(keyword).trim();
    for (const service of catalog.services) {
      const key = service.categoryId == null ? 'uncategorized' : String(service.categoryId);
      const group = indexed.get(key) || { id: key, name: service.categoryName || 'Chưa phân nhóm' };
      if ((category !== 'ALL' && category !== key) || (status !== 'ALL' && (service.isActive ? 'ACTIVE' : 'PAUSED') !== status)
          || (term && !normalizedText(`${service.name} ${service.slug} ${group.name}`).includes(term))) continue;
      if (!result.has(key)) result.set(key, { ...group, key, services: [] });
      result.get(key).services.push(service);
    }
    return [...result.values()].sort((a, b) => a.key === 'uncategorized' ? 1 : b.key === 'uncategorized' ? -1 : a.name.localeCompare(b.name, 'vi'));
  }, [catalog.services, catalog.categories, category, status, keyword]);
  const visiblePackages = catalog.packages.filter(pack => (status === 'ALL' || (pack.isActive ? 'ACTIVE' : 'PAUSED') === status)
    && (!keyword.trim() || normalizedText(`${pack.name} ${pack.description || ''} ${(pack.items || []).map(item => item.serviceName).join(' ')}`).includes(normalizedText(keyword).trim()))
    && (category === 'ALL' || (pack.items || []).some(item => {
      const service = catalog.services.find(row => String(row.id) === String(item.serviceId));
      return service && (service.categoryId == null ? 'uncategorized' : String(service.categoryId)) === category;
    })));
  const deleteItem = async () => {
    if (deleteLock.current) return;
    deleteLock.current = true; setBusy(true); setDeleteError('');
    try {
      await apiClient.delete(remove.type === 'service' ? ENDPOINTS.SPA.SERVICE_DETAIL(remove.item.id) : ENDPOINTS.SPA.PACKAGE_DETAIL(remove.item.id));
      changed(remove.type === 'service' ? 'Đã xóa dịch vụ khỏi danh mục.' : 'Đã xóa gói liệu trình khỏi danh mục.'); setRemove(null);
    } catch (err) { setDeleteError(spaErrorMessage(err, 'Chưa xóa được. Vui lòng kiểm tra các lịch hẹn và vé liên quan.')); }
    finally { deleteLock.current = false; setBusy(false); }
  };
  const showDelete = (item, type) => { setRemove({ item, type }); setDeleteError(''); };
  const serviceCount = groups.reduce((total, group) => total + group.services.length, 0);
  return <section className="spa-catalog" aria-label="Danh mục dịch vụ và gói liệu trình">
    <div className="spa-catalog-heading"><div><span className="spa-catalog-eyebrow">ATELIER · DANH MỤC CHĂM SÓC</span><h2>Dịch vụ & gói liệu trình</h2><p>Thiết kế hành trình chăm sóc, định mức tài nguyên và đội ngũ thực hiện.</p></div>
      <div className="spa-catalog-heading-actions"><Button variant="outline" onClick={() => setPackageEditor({ item: null })} disabled={!catalog.ready.services || !catalog.services.some(item => item.isActive)}><Ticket size={16} />Tạo gói liệu trình</Button><Button onClick={() => setServiceEditor({ service: null, tab: 'info' })} disabled={!catalog.ready.categories}><Plus size={17} />Thêm dịch vụ</Button><Button variant="ghost" loading={catalog.loading} onClick={catalog.refresh} aria-label="Làm mới danh mục Spa"><RefreshCw size={17} /></Button></div>
    </div>
    {notice && <div className={`spa-catalog-notice ${notice.tone}`} role={notice.tone === 'success' ? 'status' : 'alert'}>{notice.message}<button type="button" aria-label="Đóng thông báo" onClick={() => setNotice(null)}>×</button></div>}
    {Object.entries(catalog.errors).map(([key, message]) => <div className="spa-catalog-notice error" key={key} role="alert">{message}</div>)}
    <div className="spa-catalog-overview">{[
      { label: 'Dịch vụ trong danh mục', value: catalog.services.length, icon: Sparkles, tone: 'rose' },
      { label: 'Nhóm liệu trình', value: new Set(catalog.services.map(service => service.categoryId ?? 'uncategorized')).size, icon: Building, tone: 'gold' },
      { label: 'Gói liệu trình', value: catalog.packages.length, icon: Ticket, tone: 'purple' },
      { label: 'KTV đang hoạt động', value: catalog.ready.staff ? catalog.staff.filter(staff => staff.active === true).length : '—', icon: Award, tone: 'green' },
    ].map(stat => <div key={stat.label} className={`spa-catalog-stat ${stat.tone}`}><span><stat.icon size={19} /></span><div><strong>{stat.value}</strong><small>{stat.label}</small></div></div>)}</div>
    <div className="spa-catalog-toolbar"><div className="spa-catalog-view" role="group" aria-label="Nội dung danh mục"><button type="button" aria-pressed={view === 'services'} onClick={() => setView('services')}><Sparkles size={15} />Dịch vụ lẻ <span>{catalog.services.length}</span></button><button type="button" aria-pressed={view === 'packages'} onClick={() => setView('packages')}><Ticket size={15} />Gói liệu trình <span>{catalog.packages.length}</span></button></div>
      <div className="spa-catalog-search"><Search size={16} /><Input aria-label="Tìm dịch vụ hoặc gói liệu trình" placeholder="Tìm tên, slug, nhóm liệu trình…" value={keyword} onChange={event => setKeyword(event.target.value)} /></div>
      <Select aria-label="Lọc nhóm liệu trình" value={category} onChange={event => setCategory(event.target.value)} options={[{ value: 'ALL', label: 'Tất cả nhóm liệu trình' }, ...catalog.categories.map(row => ({ value: String(row.id), label: row.name })), { value: 'uncategorized', label: 'Chưa phân nhóm' }]} />
      <Select aria-label="Lọc trạng thái kinh doanh" value={status} onChange={event => setStatus(event.target.value)} options={[{ value: 'ALL', label: 'Tất cả trạng thái' }, { value: 'ACTIVE', label: 'Đang hoạt động' }, { value: 'PAUSED', label: 'Tạm dừng' }]} />
    </div>
    {catalog.loading && !catalog.ready.services && <p role="status" className="spa-catalog-muted">Đang tải danh mục và kỹ năng KTV…</p>}
    <div className="spa-catalog-results"><span>{view === 'services' ? `${serviceCount} dịch vụ · ${groups.length} nhóm liệu trình` : `${visiblePackages.length} gói liệu trình`}</span><small>Giá niêm yết hiện tại · Đơn vị VND</small></div>
    {view === 'services' ? <div className={`spa-catalog-groups ${groups.length > 1 && groups.every(group => group.services.length === 1) ? 'single-service-groups' : ''}`}>{groups.map(group => <section className="spa-treatment-group" key={group.key} aria-label={`Nhóm ${group.name}`}><header><div><span className="spa-treatment-icon"><Sparkles size={18} /></span><div><h3>{group.name} <span>{group.services.length}</span></h3>{group.description && <p>{group.description}</p>}</div></div>{group.active === false && <small>Danh mục đang tạm dừng</small>}</header>
      <div className="spa-service-grid">{group.services.map(service => {
        const qualified = eligibleStaff(service.id);
        return <article className="spa-service-card" key={`${service.id}-${service.thumbnailUrl}`} data-service-id={service.id}><div className="spa-card-image"><Thumbnail item={service} /><ActiveBadge active={service.isActive} /></div><div className="spa-card-body">
          <h4>{service.name}</h4><small className="spa-business-status active">{service.serviceType === 'TREATMENT_COURSE' ? 'Điều trị theo lộ trình' : 'Dịch vụ một lần'}</small><code>/{service.slug}</code><p className="spa-card-description">{service.shortDescription || service.description || 'Chưa bổ sung mô tả dịch vụ.'}</p>
          <div className="spa-card-duration"><Clock size={15} /><strong>{service.durationMinutes} phút</strong><span>+ {service.preparationTimeMinutes ?? 15}p chuẩn bị</span></div>
          <div className="spa-card-price"><span>Giá niêm yết</span><strong>{formatCurrency(service.basePrice)}</strong></div>
          <button type="button" className={`spa-qualified-badge ${qualified.length ? '' : 'empty'}`} aria-label={`Ma trận KTV ${service.name}`} disabled={!catalog.ready.staff} onClick={() => setSkills(service)}><Award size={15} />{catalog.ready.staff ? `${qualified.length} KTV đủ điều kiện` : 'Chưa tải kỹ năng KTV'}<span>Quản lý →</span></button>
          <div className="spa-card-actions"><Button size="sm" variant="outline" aria-label={`Sửa dịch vụ ${service.name}`} onClick={() => setServiceEditor({ service, tab: 'info' })}><Edit size={14} />Chỉnh sửa</Button><Button size="sm" variant="outline" aria-label={`Tài nguyên ${service.name}`} onClick={() => setServiceEditor({ service, tab: 'resources' })}><Building size={14} />Tài nguyên</Button><Button size="sm" variant="ghost" aria-label={`Xóa dịch vụ ${service.name}`} onClick={() => showDelete(service, 'service')}><Trash2 size={15} /></Button></div>
          {onOpenPolicy && <button type="button" className="spa-safety-link" onClick={() => onOpenPolicy(service)}><ShieldCheck size={13} />Chính sách an toàn & khảo sát</button>}
        </div></article>;
      })}</div>
    </section>)}</div> : <div className="spa-package-grid">{visiblePackages.map(pack => {
      const value = packageValue(pack, catalog.services);
      return <article className="spa-package-card" key={`${pack.id}-${pack.thumbnailUrl}`} data-package-id={pack.id}><div className="spa-card-image"><Thumbnail item={pack} packagePhoto /><ActiveBadge active={pack.isActive} />{value.percent > 0 && <span className="spa-package-discount">Ưu đãi {new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value.percent)}%</span>}</div>
        <div className="spa-card-body"><h3>{pack.name}</h3><p className="spa-card-description">{pack.description || 'Gói chăm sóc kết hợp nhiều buổi liệu trình.'}</p><ul className="spa-package-includes">{(pack.items || []).map(item => <li key={item.serviceId}><span>{item.serviceName}</span><strong>{item.quantity} buổi</strong></li>)}</ul><div className="spa-package-validity"><Clock size={14} />{pack.validityDays == null ? 'Không thời hạn' : `${pack.validityDays} ngày từ ngày thanh toán`}</div><div className="spa-card-price"><span>Giá gói{value.known && value.original > Number(pack.price) && <del>{formatCurrency(value.original)}</del>}</span><strong>{formatCurrency(pack.price)}</strong></div><div className="spa-card-actions"><Button variant="outline" size="sm" aria-label={`Sửa gói ${pack.name}`} disabled={!catalog.ready.services} onClick={() => setPackageEditor({ item: pack })}><Edit size={14} />Chỉnh sửa gói</Button><Button variant="ghost" size="sm" aria-label={`Xóa gói ${pack.name}`} onClick={() => showDelete(pack, 'package')}><Trash2 size={15} /></Button></div></div>
      </article>;
    })}</div>}
    {!catalog.loading && ((view === 'services' && !groups.length) || (view === 'packages' && !visiblePackages.length)) && <div className="spa-catalog-empty"><Sparkles size={35} /><strong>Chưa có {view === 'services' ? 'dịch vụ' : 'gói liệu trình'} phù hợp</strong><p>Thử tìm theo từ khóa khác, đổi nhóm hoặc tạo cấu hình mới.</p><Button variant="outline" onClick={() => { setKeyword(''); setCategory('ALL'); setStatus('ALL'); }}>Xóa bộ lọc</Button></div>}
    {serviceEditor && <ServiceEditor {...serviceEditor} initialTab={serviceEditor.tab} categories={catalog.categories} facilities={catalog.facilities} onClose={() => setServiceEditor(null)} onSaved={changed} />}
    {packageEditor && <PackageBuilder packageItem={packageEditor.item} services={catalog.services} onClose={() => setPackageEditor(null)} onSaved={changed} />}
    {skills && <SkillsMatrix service={skills} staff={catalog.staff} onClose={() => setSkills(null)} onSaved={changed} />}
    {remove && <Modal isOpen title={remove.type === 'service' ? 'Xác nhận xóa dịch vụ' : 'Xác nhận xóa gói liệu trình'} onClose={() => { if (!busy) setRemove(null); }} className="spa-catalog-dialog"><div className="spa-delete-dialog"><span><Trash2 size={25} /></span><h4>{remove.item.name}</h4><p>{remove.type === 'service' ? 'Dịch vụ sẽ được xóa khỏi danh mục. Hệ thống sẽ kiểm tra lịch hẹn, vé và đơn hàng liên quan trước khi xóa.' : 'Gói sẽ ngừng được bán trong danh mục. Vé khách đã mua vẫn giữ quyền lợi theo cấu hình tại thời điểm mua.'}</p>{deleteError && <div role="alert" className="spa-catalog-notice error">{deleteError}</div>}<div><Button variant="outline" onClick={() => setRemove(null)} disabled={busy}>Giữ lại</Button><Button variant="danger" loading={busy} onClick={deleteItem}>Xác nhận xóa</Button></div></div></Modal>}
  </section>;
};
