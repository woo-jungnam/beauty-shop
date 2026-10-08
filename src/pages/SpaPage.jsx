import React, { useState, useEffect } from 'react';
import {
  CalendarCheck,
  UserCheck,
  Sparkles,
  Ticket,
  Clock,
  Edit,
  Plus,
  RefreshCw,
  Gift,
  Calendar,
  Trash2,
  Building,
  Receipt,
  DollarSign,
  AlertCircle,
  FolderTree,
  Wrench,
  ShieldCheck,
  Award,
  FileText,
  Check,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatCurrency,
  formatDate,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';
import { useAuth } from '../app/providers/AuthProvider';

const slugify = (value) =>
  value
    .trim()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');

export const SpaPage = () => {
  const { isAdmin } = useAuth();
  const [activeTab, setActiveTab] = useState('appointments');
  const [loading, setLoading] = useState(false);

  // ==========================================
  // TAB 1: APPOINTMENTS
  // ==========================================
  const [appointments, setAppointments] = useState([]);
  const [aptPage, setAptPage] = useState(0);
  const [aptTotalPages, setAptTotalPages] = useState(1);
  const [aptTotalElements, setAptTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');

  // Status Change Modal
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [selectedApt, setSelectedApt] = useState(null);
  const [targetAptStatus, setTargetAptStatus] = useState('CONFIRMED');
  const [staffNote, setStaffNote] = useState('');
  const [appointmentAssignments, setAppointmentAssignments] = useState({});
  const [submittingStatus, setSubmittingStatus] = useState(false);

  // ==========================================
  // TAB 2: SPA SERVICES & PACKAGES
  // ==========================================
  const [services, setServices] = useState([]);
  const [packages, setPackages] = useState([]);
  const [spaCategories, setSpaCategories] = useState([]);

  // Create / Edit Package Modal
  const [packageModalOpen, setPackageModalOpen] = useState(false);
  const [editingPackage, setEditingPackage] = useState(null);
  const [packageForm, setPackageForm] = useState({
    name: '', description: '', price: '', validityDays: 90, items: [{ serviceId: '', quantity: 1 }], active: true,
  });
  const [packageSubmitting, setPackageSubmitting] = useState(false);

  // Create / Edit Service Modal
  const [serviceModalOpen, setServiceModalOpen] = useState(false);
  const [editingService, setEditingService] = useState(null);
  const [srvName, setSrvName] = useState('');
  const [srvSlug, setSrvSlug] = useState('');
  const [srvPrice, setSrvPrice] = useState('500000');
  const [srvDuration, setSrvDuration] = useState('60');
  const [srvPrepMinutes, setSrvPrepMinutes] = useState('15');
  const [srvDesc, setSrvDesc] = useState('');
  const [srvCatId, setSrvCatId] = useState('');
  const [srvActive, setSrvActive] = useState(true);
  const [srvSubmitting, setSrvSubmitting] = useState(false);

  // Resource Requirements Modal for Service
  const [resourceModalOpen, setResourceModalOpen] = useState(false);
  const [selectedServiceForResource, setSelectedServiceForResource] = useState(null);
  const [resourceRequirements, setResourceRequirements] = useState([]);
  const [resourceSubmitting, setResourceSubmitting] = useState(false);

  // Safety Policy & Preparation Modal for Service
  const [policyModalOpen, setPolicyModalOpen] = useState(false);
  const [selectedServiceForPolicy, setSelectedServiceForPolicy] = useState(null);
  const [policyWarningsRequired, setPolicyWarningsRequired] = useState(false);
  const [policySelectedFormVersions, setPolicySelectedFormVersions] = useState([]);
  const [formTemplates, setFormTemplates] = useState([]);
  const [policySubmitting, setPolicySubmitting] = useState(false);

  // ==========================================
  // TAB: SPA CATEGORIES
  // ==========================================
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [catName, setCatName] = useState('');
  const [catSlug, setCatSlug] = useState('');
  const [catDesc, setCatDesc] = useState('');
  const [catActive, setCatActive] = useState(true);
  const [catSubmitting, setCatSubmitting] = useState(false);

  // ==========================================
  // TAB 3: STAFF & SHIFT SCHEDULES
  // ==========================================
  const [staffList, setStaffList] = useState([]);
  const [createStaffModalOpen, setCreateStaffModalOpen] = useState(false);
  const [staffUserId, setStaffUserId] = useState('');
  const [staffSpecialty, setStaffSpecialty] = useState('Chuyên viên Chăm sóc Da & Trị liệu');
  const [staffBio, setStaffBio] = useState('');
  const [staffSubmitting, setStaffSubmitting] = useState(false);

  // Shift Schedules Modal
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [selectedStaffForSchedule, setSelectedStaffForSchedule] = useState(null);
  const [shiftDate, setShiftDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [shiftStart, setShiftStart] = useState('09:00:00');
  const [shiftEnd, setShiftEnd] = useState('17:00:00');
  const [shiftNote, setShiftNote] = useState('Ca làm việc chuẩn');
  const [shiftSubmitting, setShiftSubmitting] = useState(false);

  // Staff Skills Modal
  const [skillsModalOpen, setSkillsModalOpen] = useState(false);
  const [selectedStaffForSkills, setSelectedStaffForSkills] = useState(null);
  const [staffSkillList, setStaffSkillList] = useState([]);
  const [newSkillServiceId, setNewSkillServiceId] = useState('');
  const [newSkillCertified, setNewSkillCertified] = useState(true);
  const [skillSubmitting, setSkillSubmitting] = useState(false);

  // ==========================================
  // TAB: PREPARATION FORM TEMPLATES
  // ==========================================
  const [createTemplateModalOpen, setCreateTemplateModalOpen] = useState(false);
  const [tplTitle, setTplTitle] = useState('');
  const [tplQuestions, setTplQuestions] = useState([
    { key: 'allergies', label: 'Bạn có tiền sử dị ứng với hương liệu hoặc mỹ phẩm không?', type: 'BOOLEAN', required: true },
    { key: 'skinCondition', label: 'Tình trạng da hiện tại hoặc bệnh lý cần lưu ý:', type: 'TEXT', required: false },
  ]);
  const [tplSubmitting, setTplSubmitting] = useState(false);

  // ==========================================
  // TAB 4: TICKETS & PACKAGES
  // ==========================================
  const [tickets, setTickets] = useState([]);
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [extendModalOpen, setExtendModalOpen] = useState(false);
  const [compensateModalOpen, setCompensateModalOpen] = useState(false);
  const [extendDays, setExtendDays] = useState(30);
  const [extendReason, setExtendReason] = useState('Gia hạn theo phê duyệt quản lý');
  const [compensateServiceId, setCompensateServiceId] = useState('');
  const [compensateSessions, setCompensateSessions] = useState(1);
  const [compensateReason, setCompensateReason] = useState('Bồi thường buổi theo biên bản xử lý');
  const [invoicePaymentMethod, setInvoicePaymentMethod] = useState('CASH');

  // ==========================================
  // TAB 5: SPA FACILITIES (ROOMS / BEDS / MACHINES)
  // ==========================================
  const [facilities, setFacilities] = useState([]);
  const [facilityModalOpen, setFacilityModalOpen] = useState(false);
  const [editingFacility, setEditingFacility] = useState(null);
  const [facName, setFacName] = useState('');
  const [facType, setFacType] = useState('BED');
  const [facCapacity, setFacCapacity] = useState('1');
  const [facActive, setFacActive] = useState(true);
  const [facSubmitting, setFacSubmitting] = useState(false);

  // Facility Blocks / Maintenance Modal
  const [blocksModalOpen, setBlocksModalOpen] = useState(false);
  const [selectedFacForBlocks, setSelectedFacForBlocks] = useState(null);
  const [blocksList, setBlocksList] = useState([]);
  const [blockStart, setBlockStart] = useState(() => new Date().toISOString().slice(0, 16));
  const [blockEnd, setBlockEnd] = useState(() => new Date(Date.now() + 3600000 * 2).toISOString().slice(0, 16));
  const [blockReason, setBlockReason] = useState('Bảo trì thiết bị định kỳ');
  const [blockSubmitting, setBlockSubmitting] = useState(false);

  // ==========================================
  // APPOINTMENT CHECKOUT / INVOICING MODAL
  // ==========================================
  const [invoiceModalOpen, setInvoiceModalOpen] = useState(false);
  const [selectedAptForInvoice, setSelectedAptForInvoice] = useState(null);
  const [invoiceData, setInvoiceData] = useState(null);
  const [invoiceLoading, setInvoiceLoading] = useState(false);
  const [invoiceSubmitting, setInvoiceSubmitting] = useState(false);
  const [cashAmount, setCashAmount] = useState('');
  const [cashSubmitting, setCashSubmitting] = useState(false);

  // ==========================================
  // DATA FETCHING
  // ==========================================
  const fetchAppointments = async (p = 0) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', p);
      params.append('size', 15);
      if (statusFilter) params.append('status', statusFilter);

      const res = await apiClient.get(`${ENDPOINTS.SPA.APPOINTMENTS}?${params.toString()}`);
      const pageData = res.data || res;
      setAppointments(pageData.content || []);
      setAptPage(pageData.page ?? p);
      setAptTotalPages(pageData.totalPages ?? 1);
      setAptTotalElements(pageData.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  };

  const fetchSpaCatalog = async () => {
    try {
      const [srvRes, catRes, pkgRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.SPA.SERVICES),
        apiClient.get(ENDPOINTS.SPA.CATEGORIES),
        apiClient.get(ENDPOINTS.SPA.PACKAGES),
      ]);
      if (srvRes.status === 'fulfilled') {
        const d = srvRes.value.data || srvRes.value;
        setServices(d.content || d || []);
      }
      if (catRes.status === 'fulfilled') {
        const d = catRes.value.data || catRes.value;
        setSpaCategories(d.content || d || []);
      }
      if (pkgRes.status === 'fulfilled') {
        const d = pkgRes.value.data || pkgRes.value;
        setPackages(d.content || d || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchStaff = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.STAFF);
      let list = res?.data || res || [];
      list = Array.isArray(list) ? list : (list?.content || []);
      if (!list || list.length === 0) {
        try {
          const publicRes = await apiClient.get(ENDPOINTS.SPA.PUBLIC_STAFF || '/api/v1/spa/services/staff');
          const pData = publicRes?.data || publicRes || [];
          list = Array.isArray(pData) ? pData : (pData?.content || []);
        } catch (_) {}
      }
      setStaffList(list || []);
    } catch (e) {
      console.error('Lỗi tải danh sách chuyên viên từ database', e);
      setStaffList([]);
    }
  };

  const fetchTickets = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.TICKETS);
      const data = res.data || res;
      setTickets(data.content || data || []);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchFacilities = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.FACILITIES);
      const data = res.data || res;
      setFacilities(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchCategories = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.CATEGORIES);
      const d = res.data || res;
      setSpaCategories(d.content || (Array.isArray(d) ? d : []));
    } catch (e) {
      console.error(e);
    }
  };

  const fetchFormTemplates = async () => {
    try {
      const res = await apiClient.get(`${ENDPOINTS.SPA.PREPARATION_TEMPLATES}?page=0&size=50`);
      const d = res.data || res;
      setFormTemplates(d.content || (Array.isArray(d) ? d : []));
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    if (activeTab === 'appointments') {
      fetchAppointments(0);
      fetchStaff();
    } else if (activeTab === 'services') {
      fetchSpaCatalog();
    } else if (activeTab === 'categories') {
      fetchCategories();
    } else if (activeTab === 'staff') {
      fetchStaff();
      fetchSpaCatalog();
    } else if (activeTab === 'tickets') {
      fetchTickets();
    } else if (activeTab === 'facilities') {
      fetchFacilities();
    } else if (activeTab === 'preparation') {
      fetchFormTemplates();
    }
  }, [activeTab, statusFilter]);

  // Handlers: Appointment Status
  const handleOpenStatusModal = (apt) => {
    setSelectedApt(apt);
    setTargetAptStatus(apt.status || 'CONFIRMED');
    setStaffNote(apt.notes || '');
    setAppointmentAssignments(Object.fromEntries((apt.items || []).filter((item) => item.staffId).map((item) => [item.id, item.staffId])));
    if (staffList.length === 0) fetchStaff();
    setStatusModalOpen(true);
  };

  const handleCheckIn = async (aptId) => {
    try {
      await apiClient.put(ENDPOINTS.SPA.CHECK_IN_APPOINTMENT(aptId));
      alert('Đã ghi nhận khách check-in thành công!');
      fetchAppointments(aptPage);
    } catch (err) {
      alert(err.message || 'Lỗi check-in');
    }
  };

  const handleUpdateStatus = async (e) => {
    e.preventDefault();
    if (!selectedApt) return;
    setSubmittingStatus(true);
    try {
      await apiClient.put(ENDPOINTS.SPA.UPDATE_APPOINTMENT_STATUS(selectedApt.id), {
        status: targetAptStatus,
        notes: staffNote.trim() || undefined,
        staffAssignments: targetAptStatus === 'CONFIRMED'
          ? Object.fromEntries(
              Object.entries(appointmentAssignments)
                .filter(([, staffId]) => staffId !== '')
                .map(([itemId, staffId]) => [itemId, Number(staffId)]),
            )
          : undefined,
      });
      setStatusModalOpen(false);
      fetchAppointments(aptPage);
    } catch (err) {
      alert(err.message || 'Lỗi cập nhật lịch hẹn');
    } finally {
      setSubmittingStatus(false);
    }
  };

  // Handlers: Spa Service
  // Spa Category Handlers
  const handleOpenCreateCategory = () => {
    setEditingCategory(null);
    setCatName('');
    setCatSlug('');
    setCatDesc('');
    setCatActive(true);
    setCategoryModalOpen(true);
  };

  const handleOpenEditCategory = (cat) => {
    setEditingCategory(cat);
    setCatName(cat.name || '');
    setCatSlug(cat.slug || '');
    setCatDesc(cat.description || '');
    setCatActive(cat.isActive ?? cat.active ?? true);
    setCategoryModalOpen(true);
  };

  const handleSubmitCategory = async (e) => {
    e.preventDefault();
    setCatSubmitting(true);
    try {
      const payload = {
        name: catName.trim(),
        slug: catSlug.trim() || slugify(catName),
        description: catDesc.trim(),
        active: catActive,
      };
      if (editingCategory) {
        await apiClient.put(ENDPOINTS.SPA.CATEGORY_DETAIL(editingCategory.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.SPA.CATEGORIES, payload);
      }
      setCategoryModalOpen(false);
      fetchCategories();
      fetchSpaCatalog();
    } catch (err) {
      alert(err.message || 'L\u1ed7i l\u01b0u danh m\u1ee5c spa');
    } finally {
      setCatSubmitting(false);
    }
  };

  const handleDeleteCategory = async (id) => {
    if (!window.confirm(`X\u00f3a danh m\u1ee5c spa #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.SPA.CATEGORY_DETAIL(id));
      fetchCategories();
      fetchSpaCatalog();
    } catch (err) {
      alert(err.message || 'L\u1ed7i x\u00f3a danh m\u1ee5c spa');
    }
  };

  // Spa Service Handlers (Create & Edit)
  const handleOpenCreateService = () => {
    setEditingService(null);
    setSrvName('');
    setSrvSlug('');
    setSrvPrice('500000');
    setSrvDuration('60');
    setSrvPrepMinutes('15');
    setSrvDesc('');
    setSrvCatId(spaCategories[0]?.id ? String(spaCategories[0].id) : '');
    setSrvActive(true);
    setServiceModalOpen(true);
  };

  const handleOpenEditService = (srv) => {
    setEditingService(srv);
    setSrvName(srv.name || '');
    setSrvSlug(srv.slug || '');
    setSrvPrice(String(srv.basePrice ?? '500000'));
    setSrvDuration(String(srv.durationMinutes ?? '60'));
    setSrvPrepMinutes(String(srv.preparationTimeMinutes ?? '15'));
    setSrvDesc(srv.description || srv.shortDescription || '');
    setSrvCatId(srv.categoryId ? String(srv.categoryId) : '');
    setSrvActive(srv.isActive ?? true);
    setServiceModalOpen(true);
  };

  const handleSubmitService = async (e) => {
    e.preventDefault();
    setSrvSubmitting(true);
    try {
      const payload = {
        name: srvName.trim(),
        slug: srvSlug.trim() || slugify(srvName),
        shortDescription: srvDesc.trim(),
        description: srvDesc.trim(),
        basePrice: parseFloat(srvPrice),
        durationMinutes: parseInt(srvDuration, 10),
        preparationTimeMinutes: parseInt(srvPrepMinutes, 10) || 15,
        categoryId: srvCatId ? parseInt(srvCatId, 10) : undefined,
        active: srvActive,
      };
      if (editingService) {
        await apiClient.put(ENDPOINTS.SPA.SERVICE_DETAIL(editingService.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.SPA.SERVICES, payload);
      }
      setServiceModalOpen(false);
      fetchSpaCatalog();
    } catch (err) {
      alert(err.message || 'L\u1ed7i l\u01b0u d\u1ecbch v\u1ee5 spa');
    } finally {
      setSrvSubmitting(false);
    }
  };

  const handleDeleteService = async (id) => {
    if (!window.confirm(`Xóa dịch vụ spa #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.SPA.SERVICE_DETAIL(id));
      fetchSpaCatalog();
    } catch (err) {
      alert(err.message || 'Lỗi xóa dịch vụ');
    }
  };

  // Spa Package Handlers (Create & Edit)
  const handleOpenCreatePackage = () => {
    setEditingPackage(null);
    setPackageForm({
      name: '',
      description: '',
      price: '',
      validityDays: 90,
      items: [{ serviceId: services[0]?.id ? String(services[0].id) : '', quantity: 1 }],
      active: true,
    });
    setPackageModalOpen(true);
  };

  const handleOpenEditPackage = (pkg) => {
    setEditingPackage(pkg);
    setPackageForm({
      name: pkg.name || '',
      description: pkg.description || '',
      price: String(pkg.price ?? ''),
      validityDays: pkg.validityDays ?? 90,
      items: (pkg.items || []).map((it) => ({ serviceId: String(it.serviceId || ''), quantity: it.quantity || 1 })),
      active: pkg.isActive ?? true,
    });
    setPackageModalOpen(true);
  };

  const handleSubmitPackage = async (event) => {
    event.preventDefault();
    setPackageSubmitting(true);
    try {
      const payload = {
        name: packageForm.name.trim(),
        description: packageForm.description.trim(),
        price: Number(packageForm.price),
        validityDays: Number(packageForm.validityDays),
        active: packageForm.active ?? true,
        items: packageForm.items.map((item) => ({
          serviceId: Number(item.serviceId),
          quantity: Number(item.quantity),
        })),
      };
      if (editingPackage) {
        await apiClient.put(ENDPOINTS.SPA.PACKAGE_DETAIL(editingPackage.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.SPA.PACKAGES, payload);
      }
      setPackageModalOpen(false);
      fetchSpaCatalog();
    } catch (err) {
      alert(err.message || 'Không thể lưu gói liệu trình');
    } finally {
      setPackageSubmitting(false);
    }
  };

  const handleDeletePackage = async (id) => {
    if (!window.confirm(`Xóa gói liệu trình #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.SPA.PACKAGE_DETAIL(id));
      fetchSpaCatalog();
    } catch (err) {
      alert(err.message || 'Không thể xóa gói liệu trình');
    }
  };

  // Service Resource Requirements Handlers
  const handleOpenResourceModal = async (srv) => {
    setSelectedServiceForResource(srv);
    setResourceRequirements([]);
    setResourceModalOpen(true);
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.SERVICE_RESOURCES(srv.id));
      const list = res.data || res;
      setResourceRequirements(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
    }
  };

  const handleSaveResourceRequirements = async (e) => {
    e.preventDefault();
    if (!selectedServiceForResource) return;
    setResourceSubmitting(true);
    try {
      await apiClient.put(
        ENDPOINTS.SPA.SERVICE_RESOURCES(selectedServiceForResource.id),
        resourceRequirements.map((r) => ({ type: r.type, units: Number(r.units) }))
      );
      setResourceModalOpen(false);
      alert('Đã cập nhật định mức tài nguyên dịch vụ thành công!');
    } catch (err) {
      alert(err.message || 'Lỗi lưu định mức tài nguyên');
    } finally {
      setResourceSubmitting(false);
    }
  };

  // Service Policy & Preparation Handlers
  const handleOpenPolicyModal = async (srv) => {
    setSelectedServiceForPolicy(srv);
    setPolicyWarningsRequired(false);
    setPolicySelectedFormVersions([]);
    setPolicyModalOpen(true);
    try {
      const [policyRes, tplRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.SPA.SERVICE_POLICY(srv.id)),
        apiClient.get(`${ENDPOINTS.SPA.PREPARATION_TEMPLATES}?page=0&size=50`),
      ]);
      if (policyRes.status === 'fulfilled') {
        const p = policyRes.value.data || policyRes.value;
        setPolicyWarningsRequired(Boolean(p.warningsRequired));
        setPolicySelectedFormVersions((p.requiredForms || []).map((f) => f.id));
      }
      if (tplRes.status === 'fulfilled') {
        const t = tplRes.value.data || tplRes.value;
        setFormTemplates(t.content || (Array.isArray(t) ? t : []));
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleSavePolicy = async (e) => {
    e.preventDefault();
    if (!selectedServiceForPolicy) return;
    setPolicySubmitting(true);
    try {
      await apiClient.put(ENDPOINTS.SPA.SERVICE_POLICY(selectedServiceForPolicy.id), {
        warningsRequired: policyWarningsRequired,
        requiredFormVersionIds: policySelectedFormVersions.map(Number),
      });
      setPolicyModalOpen(false);
      alert('Đã cập nhật chính sách an toàn & biểu mẫu cho dịch vụ!');
    } catch (err) {
      alert(err.message || 'Lỗi lưu chính sách an toàn');
    } finally {
      setPolicySubmitting(false);
    }
  };

  // Staff Skills Handlers
  const handleOpenSkillsModal = (staff) => {
    setSelectedStaffForSkills(staff);
    setStaffSkillList(staff.skills || []);
    setNewSkillServiceId(services[0]?.id ? String(services[0].id) : '');
    setNewSkillCertified(true);
    setSkillsModalOpen(true);
  };

  const handleAddSkill = async (e) => {
    e.preventDefault();
    if (!selectedStaffForSkills || !newSkillServiceId) return;
    setSkillSubmitting(true);
    try {
      const res = await apiClient.put(ENDPOINTS.SPA.STAFF_SKILLS(selectedStaffForSkills.id), {
        serviceId: Number(newSkillServiceId),
        certified: newSkillCertified,
      });
      const updated = res.data || res;
      setStaffSkillList(updated.skills || []);
      fetchStaff();
    } catch (err) {
      alert(err.message || 'Lỗi gán kỹ năng');
    } finally {
      setSkillSubmitting(false);
    }
  };

  const handleDeleteSkill = async (serviceId) => {
    if (!window.confirm('Xóa kỹ năng dịch vụ này khỏi chuyên viên?')) return;
    try {
      await apiClient.delete(ENDPOINTS.SPA.STAFF_SKILL_DELETE(selectedStaffForSkills.id, serviceId));
      setStaffSkillList((prev) => prev.filter((s) => s.serviceId !== serviceId));
      fetchStaff();
    } catch (err) {
      alert(err.message || 'Lỗi xóa kỹ năng');
    }
  };

  // Preparation Template Creation Handlers
  const handleOpenCreateTemplate = () => {
    setTplTitle('');
    setTplQuestions([
      { key: 'allergies', label: 'Bạn có tiền sử dị ứng với mỹ phẩm hoặc hoạt chất nào không?', type: 'BOOLEAN', required: true },
      { key: 'skinCondition', label: 'Mô tả tình trạng da và các vấn đề cần lưu ý:', type: 'TEXT', required: false },
    ]);
    setCreateTemplateModalOpen(true);
  };

  const handleCreateTemplate = async (e) => {
    e.preventDefault();
    setTplSubmitting(true);
    try {
      await apiClient.post(ENDPOINTS.SPA.PREPARATION_TEMPLATES, {
        title: tplTitle.trim(),
        questions: tplQuestions,
      });
      setCreateTemplateModalOpen(false);
      fetchFormTemplates();
    } catch (err) {
      alert(err.message || 'Lỗi tạo mẫu khảo sát');
    } finally {
      setTplSubmitting(false);
    }
  };

  // Handlers: Staff & Schedules
  const handleCreateStaff = async (e) => {
    e.preventDefault();
    setStaffSubmitting(true);
    try {
      const payload = {
        userId: parseInt(staffUserId, 10),
        specialty: staffSpecialty.trim(),
        bio: staffBio.trim(),
        active: true,
      };
      await apiClient.post(ENDPOINTS.SPA.STAFF, payload);
      setCreateStaffModalOpen(false);
      fetchStaff();
    } catch (err) {
      alert(err.message || 'Lỗi thêm chuyên viên');
    } finally {
      setStaffSubmitting(false);
    }
  };

  const handleAddShift = async (e) => {
    e.preventDefault();
    if (!selectedStaffForSchedule) return;
    setShiftSubmitting(true);
    try {
      const payload = {
        workDate: shiftDate,
        startTime: shiftStart,
        endTime: shiftEnd,
        status: 'SCHEDULED',
        note: shiftNote.trim(),
      };
      await apiClient.post(ENDPOINTS.SPA.STAFF_SCHEDULES(selectedStaffForSchedule.id), payload);
      alert('Đã xếp ca trực thành công cho chuyên viên!');
      setScheduleModalOpen(false);
    } catch (err) {
      alert(err.message || 'Lỗi xếp ca trực');
    } finally {
      setShiftSubmitting(false);
    }
  };

  // Handlers: Tickets
  const handleExtendTicket = async (e) => {
    e.preventDefault();
    if (!selectedTicket) return;
    try {
      const key = `tkt-${selectedTicket.id}:ext:${Date.now()}`;
      await apiClient.put(ENDPOINTS.SPA.TICKET_EXTEND(selectedTicket.id), {
        days: parseInt(extendDays, 10),
        reason: extendReason.trim() || 'Gia hạn theo phê duyệt quản lý',
        idempotencyKey: key,
      });
      setExtendModalOpen(false);
      fetchTickets();
    } catch (err) {
      alert(err.message || 'Lỗi gia hạn vé');
    }
  };

  const handleCompensateTicket = async (e) => {
    e.preventDefault();
    if (!selectedTicket) return;
    try {
      const key = `tkt-${selectedTicket.id}:comp:${Date.now()}`;
      await apiClient.put(ENDPOINTS.SPA.TICKET_COMPENSATE(selectedTicket.id), {
        serviceId: Number(compensateServiceId || selectedTicket.serviceId || (services[0]?.id)),
        sessions: parseInt(compensateSessions, 10),
        reason: compensateReason.trim() || 'Bồi thường buổi theo biên bản xử lý',
        idempotencyKey: key,
      });
      setCompensateModalOpen(false);
      fetchTickets();
    } catch (err) {
      alert(err.message || 'Lỗi bồi thường buổi');
    }
  };

  // Handlers: Facilities
  const handleOpenCreateFacility = () => {
    setEditingFacility(null);
    setFacName('');
    setFacType('BED');
    setFacCapacity('1');
    setFacActive(true);
    setFacilityModalOpen(true);
  };

  const handleOpenEditFacility = (fac) => {
    setEditingFacility(fac);
    setFacName(fac.name || '');
    setFacType(fac.type || 'BED');
    setFacCapacity(String(fac.capacity ?? 1));
    setFacActive(fac.active ?? true);
    setFacilityModalOpen(true);
  };

  const handleSubmitFacility = async (e) => {
    e.preventDefault();
    setFacSubmitting(true);
    try {
      const payload = {
        name: facName.trim(),
        type: facType,
        capacity: parseInt(facCapacity, 10) || 1,
        active: facActive,
      };
      if (editingFacility) {
        await apiClient.put(ENDPOINTS.SPA.FACILITY_DETAIL(editingFacility.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.SPA.FACILITIES, payload);
      }
      setFacilityModalOpen(false);
      fetchFacilities();
    } catch (err) {
      alert(err.message || 'Lỗi lưu thông tin cơ sở vật chất');
    } finally {
      setFacSubmitting(false);
    }
  };

  const handleDeleteFacility = async (id) => {
    if (!window.confirm(`Xóa cơ sở vật chất #${id}? Lưu ý không được có lịch đang dùng.`)) return;
    try {
      await apiClient.delete(ENDPOINTS.SPA.FACILITY_DETAIL(id));
      fetchFacilities();
    } catch (err) {
      alert(err.message || 'Lỗi xóa cơ sở vật chất');
    }
  };

  // Blocks / Maintenance
  const handleOpenBlocks = async (fac) => {
    setSelectedFacForBlocks(fac);
    setBlocksModalOpen(true);
    setBlockReason('Bảo trì thiết bị định kỳ');
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.FACILITY_BLOCKS(fac.id));
      setBlocksList(res.data || res || []);
    } catch (err) {
      console.error(err);
    }
  };

  const handleCreateBlock = async (e) => {
    e.preventDefault();
    if (!selectedFacForBlocks) return;
    setBlockSubmitting(true);
    try {
      const s = blockStart.length === 16 ? `${blockStart}:00` : blockStart;
      const end = blockEnd.length === 16 ? `${blockEnd}:00` : blockEnd;
      await apiClient.post(ENDPOINTS.SPA.FACILITY_BLOCKS(selectedFacForBlocks.id), {
        startAt: s,
        endAt: end,
        reason: blockReason.trim(),
      });
      const res = await apiClient.get(ENDPOINTS.SPA.FACILITY_BLOCKS(selectedFacForBlocks.id));
      setBlocksList(res.data || res || []);
    } catch (err) {
      alert(err.message || 'Lỗi tạo khoảng bảo trì');
    } finally {
      setBlockSubmitting(false);
    }
  };

  const handleDeleteBlock = async (blockId) => {
    if (!window.confirm('Xóa khoảng bảo trì này?')) return;
    try {
      await apiClient.delete(ENDPOINTS.SPA.FACILITY_BLOCK_DETAIL(selectedFacForBlocks.id, blockId));
      const res = await apiClient.get(ENDPOINTS.SPA.FACILITY_BLOCKS(selectedFacForBlocks.id));
      setBlocksList(res.data || res || []);
    } catch (err) {
      alert(err.message || 'Lỗi xóa khoảng bảo trì');
    }
  };

  // Invoicing & Checkout
  const handleOpenInvoiceModal = async (apt) => {
    setSelectedAptForInvoice(apt);
    setInvoiceData(null);
    setInvoiceModalOpen(true);
    setInvoiceLoading(true);
    setCashAmount('');
    try {
      const res = await apiClient.get(ENDPOINTS.SPA.APPOINTMENT_INVOICE(apt.id));
      const inv = res.data || res;
      setInvoiceData(inv);
      if (inv.balanceDue || inv.totalAmount) {
        setCashAmount(String(inv.balanceDue ?? inv.totalAmount));
      }
    } catch {
      // Invoice may not exist yet
      setInvoiceData(null);
    } finally {
      setInvoiceLoading(false);
    }
  };

  const handleCreateInvoice = async () => {
    if (!selectedAptForInvoice) return;
    setInvoiceSubmitting(true);
    try {
      const key = `spa-inv-${selectedAptForInvoice.id}-${Date.now().toString().slice(-6)}`;
      const res = await apiClient.post(
        ENDPOINTS.SPA.APPOINTMENT_INVOICE(selectedAptForInvoice.id),
        { paymentMethod: invoicePaymentMethod || 'CASH', notes: 'Lập hóa đơn chốt buổi Spa' },
        { headers: { 'Idempotency-Key': key } }
      );
      const inv = res.data || res;
      setInvoiceData(inv);
      if (inv.balanceDue !== undefined && inv.balanceDue !== null) {
        setCashAmount(String(inv.balanceDue));
      }
      fetchAppointments(aptPage);
    } catch (err) {
      alert(err.message || 'Lỗi tạo hóa đơn chốt buổi');
    } finally {
      setInvoiceSubmitting(false);
    }
  };

  const handleCollectCash = async (e) => {
    e.preventDefault();
    if (!selectedAptForInvoice || !cashAmount) return;
    setCashSubmitting(true);
    try {
      const receiptKey = `cash-rcpt-${selectedAptForInvoice.id}-${Date.now().toString().slice(-6)}`;
      const res = await apiClient.post(
        ENDPOINTS.SPA.APPOINTMENT_CASH_RECEIPT(selectedAptForInvoice.id),
        { amount: Math.round(parseFloat(cashAmount)) },
        { headers: { 'Idempotency-Key': receiptKey } }
      );
      setInvoiceData(res.data || res);
      alert('Đã ghi nhận phiếu thu tiền mặt thành công!');
      fetchAppointments(aptPage);
    } catch (err) {
      alert(err.message || 'Lỗi ghi nhận phiếu thu tiền mặt');
    } finally {
      setCashSubmitting(false);
    }
  };

  // ==========================================
  // TABLE COLUMNS
  // ==========================================
  const appointmentColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    {
      header: 'Khách hàng',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.customerName || 'Khách vãng lai'}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{row.customerPhone || 'N/A'}</div>
        </div>
      ),
    },
    {
      header: 'Thời gian hẹn',
      accessor: (row) => (
        <div>
          <strong>{formatDate(row.appointmentDate)}</strong>
          <div style={{ fontSize: '11px', color: 'var(--color-primary-600)' }}>
            {row.startTime} - {row.endTime}
          </div>
        </div>
      ),
    },
    {
      header: 'Dịch vụ',
      accessor: (row) => row.items?.map((it) => it.serviceName).join(', ') || 'Chăm sóc da',
    },
    {
      header: 'Chuyên viên',
      accessor: (row) => row.items?.map((item) => item.staffName).filter(Boolean).join(', ') || 'Chưa chỉ định',
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        let variant = 'default';
        if (row.status === 'CONFIRMED' || row.status === 'COMPLETED') variant = 'success';
        else if (row.status === 'IN_PROGRESS') variant = 'info';
        else if (row.status === 'CANCELLED' || row.status === 'NO_SHOW') variant = 'danger';
        else if (row.status === 'PENDING') variant = 'warning';
        return (
          <div>
            <Badge variant={variant}>{row.status}</Badge>
            {row.checkedInAt && (
              <div style={{ fontSize: '10.5px', color: '#059669', fontWeight: 700, marginTop: '2px' }}>
                ✓ Đã check-in
              </div>
            )}
          </div>
        );
      },
      width: '120px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          {row.status === 'CONFIRMED' && !row.checkedInAt && (
            <Button variant="outline" size="sm" onClick={() => handleCheckIn(row.id)} icon={UserCheck} title="Check-in khách">
              Check-in
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={() => handleOpenStatusModal(row)} icon={Edit} title="Cập nhật trạng thái">
            Cập nhật
          </Button>
          {(row.status === 'COMPLETED' || row.status === 'IN_PROGRESS') && (
            <Button variant="primary" size="sm" onClick={() => handleOpenInvoiceModal(row)} icon={Receipt} title="Chốt hóa đơn / Thu tiền">
              Hóa đơn
            </Button>
          )}
        </div>
      ),
      align: 'right',
      width: '240px',
    },
  ];

  const facilityColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    { header: 'Tên phòng / Thiết bị', accessor: (row) => <strong>{row.name}</strong> },
    {
      header: 'Phân loại',
      accessor: (row) => (
        <Badge variant={row.type === 'ROOM' ? 'success' : row.type === 'BED' ? 'info' : row.type === 'MACHINE' ? 'warning' : 'default'}>
          {row.type === 'ROOM' ? 'Phòng' : row.type === 'BED' ? 'Giường' : row.type === 'MACHINE' ? 'Thiết bị' : 'Chung'}
        </Badge>
      ),
      width: '120px',
    },
    { header: 'Sức chứa', accessor: (row) => <strong>{row.capacity ?? 1}</strong>, width: '100px', align: 'center' },
    {
      header: 'Trạng thái',
      accessor: (row) => <Badge variant={row.active ? 'success' : 'danger'}>{row.active ? 'HOẠT ĐỘNG' : 'TẠM KHÓA'}</Badge>,
      width: '120px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenBlocks(row)} icon={Clock} title="Lịch bảo trì / tạm ngừng">
            Bảo trì
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenEditFacility(row)} icon={Edit} title="Sửa cơ sở" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteFacility(row.id)} icon={Trash2} title="Xóa cơ sở" />
        </div>
      ),
      align: 'right',
      width: '180px',
    },
  ];

  const serviceColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    { header: 'Tên dịch vụ Spa', accessor: (row) => <strong>{row.name}</strong> },
    {
      header: 'Thời lượng',
      accessor: (row) => `${row.durationMinutes} phút (+${row.preparationTimeMinutes ?? 15}p chuẩn bị)`,
      width: '160px',
    },
    { header: 'Giá dịch vụ', accessor: (row) => <strong>{formatCurrency(row.basePrice)}</strong>, align: 'right' },
    {
      header: 'Trạng thái',
      accessor: (row) => <Badge variant={row.isActive ? 'success' : 'default'}>{row.isActive ? 'ACTIVE' : 'OFF'}</Badge>,
      width: '90px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenResourceModal(row)} icon={Wrench} title="Cấu hình tài nguyên buồng/phòng/máy">
            Tài nguyên
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenPolicyModal(row)} icon={ShieldCheck} title="Chính sách an toàn & khảo sát">
            An toàn
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenEditService(row)} icon={Edit} title="Sửa dịch vụ" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteService(row.id)} icon={Trash2} title="Xóa dịch vụ" />
        </div>
      ),
      align: 'right',
      width: '280px',
    },
  ];

  const categoryColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    { header: 'Tên danh mục', accessor: (row) => <strong>{row.name}</strong> },
    { header: 'Slug', accessor: (row) => <code style={{ fontSize: '12px' }}>/{row.slug}</code> },
    { header: 'Mô tả', accessor: (row) => row.description || '—' },
    {
      header: 'Trạng thái',
      accessor: (row) => <Badge variant={row.active ? 'success' : 'default'}>{row.active ? 'HOẠT ĐỘNG' : 'TẠM TẮT'}</Badge>,
      width: '120px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenEditCategory(row)} icon={Edit} title="Sửa danh mục" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteCategory(row.id)} icon={Trash2} title="Xóa danh mục" />
        </div>
      ),
      align: 'right',
      width: '120px',
    },
  ];

  const packageColumns = [
    { header: 'Gói liệu trình', accessor: (row) => <strong>{row.name}</strong> },
    {
      header: 'Dịch vụ bao gồm',
      accessor: (row) => (row.items || [])
        .map((item) => `${item.serviceName || ('Dịch vụ #' + (item.serviceId || ''))} × ${item.quantity}`)
        .join(', ') || 'Chưa cấu hình dịch vụ',
    },
    { header: 'Hiệu lực', accessor: (row) => `${row.validityDays || 0} ngày`, width: '100px' },
    { header: 'Giá gói', accessor: (row) => <strong>{formatCurrency(row.price)}</strong>, align: 'right' },
    {
      header: 'Trạng thái',
      accessor: (row) => <Badge variant={row.isActive ? 'success' : 'default'}>{row.isActive ? 'ACTIVE' : 'OFF'}</Badge>,
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenEditPackage(row)} icon={Edit} title="Sửa gói liệu trình" />
          <Button variant="outline" size="sm" onClick={() => handleDeletePackage(row.id)} icon={Trash2} title="Xóa gói" />
        </div>
      ),
      align: 'right',
      width: '120px',
    },
  ];

  const staffColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    { header: 'Họ tên', accessor: (row) => <strong>{row.fullName || `Nhân viên #${row.id}`}</strong> },
    { header: 'Chuyên môn', accessor: (row) => row.specialty || 'Kỹ thuật viên Spa' },
    {
      header: 'Đánh giá',
      accessor: (row) => (row.rating ? `${row.rating} / 5 (${row.totalReviews || 0})` : 'Mới'),
      width: '120px',
    },
    {
      header: 'Kỹ năng gán',
      accessor: (row) => row.skills?.length || 0,
      width: '100px',
      align: 'center',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenSkillsModal(row)}
            icon={Award}
            title="Quản lý kỹ năng & dịch vụ được làm"
          >
            Kỹ năng
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedStaffForSchedule(row);
              setScheduleModalOpen(true);
            }}
            icon={Calendar}
            title="Xếp ca trực"
          >
            Ca trực
          </Button>
        </div>
      ),
      align: 'right',
      width: '180px',
    },
  ];

  const templateColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    { header: 'Tiêu đề khảo sát', accessor: (row) => <strong>{row.title}</strong> },
    { header: 'Phiên bản', accessor: (row) => <Badge variant="info">v{row.version ?? row.versionNumber ?? 1}</Badge>, width: '100px', align: 'center' },
    {
      header: 'Số câu hỏi',
      accessor: (row) => (row.questions ? `${row.questions.length} câu hỏi` : '—'),
      width: '120px',
      align: 'center',
    },
    {
      header: 'Ngày tạo',
      accessor: (row) => (row.createdAt ? formatDate(row.createdAt) : '—'),
      width: '140px',
    },
  ];

  const ticketColumns = [
    { header: 'Mã vé', accessor: 'id', width: '70px' },
    { header: 'Mã gói / Dịch vụ', accessor: (row) => row.serviceName || `Service #${row.serviceId}` },
    { header: 'Số buổi còn lại', accessor: (row) => <strong>{row.remainingSessions} / {row.totalSessions} buổi</strong> },
    { header: 'Hạn dùng', accessor: (row) => formatDate(row.expiryDate) },
    {
      header: 'Trạng thái',
      accessor: (row) => <Badge variant={row.status === 'ACTIVE' ? 'success' : 'danger'}>{row.status}</Badge>,
      width: '90px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedTicket(row);
              setExtendModalOpen(true);
            }}
            icon={Clock}
            title="Gia hạn thời gian vé"
          >
            Gia hạn
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setSelectedTicket(row);
              setCompensateModalOpen(true);
            }}
            icon={Gift}
            title="Bồi thường thêm buổi"
          >
            Bồi thường
          </Button>
        </div>
      ),
      align: 'right',
      width: '180px',
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản trị Spa, Gói Liệu trình & Ca trực Chuyên viên</h1>
          <p className="page-subtitle">
            Điều phối lịch hẹn khách hàng, danh mục dịch vụ spa, xếp ca làm việc chuyên viên và vé liệu trình
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          {activeTab === 'services' && (
            <>
              <Button variant="outline" size="sm" onClick={handleOpenCreatePackage} icon={Plus}>
                Thêm gói liệu trình
              </Button>
              <Button variant="primary" size="sm" onClick={handleOpenCreateService} icon={Plus}>
                Thêm dịch vụ Spa
              </Button>
            </>
          )}
          {activeTab === 'categories' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateCategory} icon={Plus}>
              Thêm danh mục Spa
            </Button>
          )}
          {activeTab === 'staff' && (
            <Button variant="primary" size="sm" onClick={() => setCreateStaffModalOpen(true)} icon={Plus}>
              Thêm chuyên viên mới
            </Button>
          )}
          {activeTab === 'facilities' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateFacility} icon={Plus}>
              Thêm phòng / thiết bị
            </Button>
          )}
          {activeTab === 'preparation' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateTemplate} icon={Plus}>
              Tạo mẫu khảo sát mới
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (activeTab === 'appointments') fetchAppointments(aptPage);
              else if (activeTab === 'services') fetchSpaCatalog();
              else if (activeTab === 'categories') fetchCategories();
              else if (activeTab === 'staff') { fetchStaff(); fetchSpaCatalog(); }
              else if (activeTab === 'tickets') fetchTickets();
              else if (activeTab === 'facilities') fetchFacilities();
              else if (activeTab === 'preparation') fetchFormTemplates();
            }}
            loading={loading}
            icon={RefreshCw}
          >
            Làm mới
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'appointments' ? 'active' : ''}`}
          onClick={() => setActiveTab('appointments')}
        >
          <CalendarCheck size={15} />
          Lịch hẹn Spa ({aptTotalElements})
        </button>
        {isAdmin && <>
          <button
            className={`tab-btn ${activeTab === 'services' ? 'active' : ''}`}
            onClick={() => setActiveTab('services')}
          >
            <Sparkles size={15} />
            Dịch vụ & Gói Liệu trình ({services.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'categories' ? 'active' : ''}`}
            onClick={() => setActiveTab('categories')}
          >
            <FolderTree size={15} />
            Danh mục dịch vụ ({spaCategories.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'staff' ? 'active' : ''}`}
            onClick={() => setActiveTab('staff')}
          >
            <UserCheck size={15} />
            Chuyên viên & Kỹ năng ({staffList.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'tickets' ? 'active' : ''}`}
            onClick={() => setActiveTab('tickets')}
          >
            <Ticket size={15} />
            Vé dịch vụ & Gói khách hàng ({tickets.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'facilities' ? 'active' : ''}`}
            onClick={() => setActiveTab('facilities')}
          >
            <Building size={15} />
            Phòng & Thiết bị ({facilities.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'preparation' ? 'active' : ''}`}
            onClick={() => setActiveTab('preparation')}
          >
            <FileText size={15} />
            Biểu mẫu khảo sát & An toàn ({formTemplates.length})
          </button>
        </>}
      </div>

      {/* Tab: Appointments */}
      {activeTab === 'appointments' && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '14px' }}>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={[
                { label: 'Tất cả trạng thái', value: '' },
                { label: 'Chờ xác nhận (PENDING)', value: 'PENDING' },
                { label: 'Đã xác nhận (CONFIRMED)', value: 'CONFIRMED' },
                { label: 'Đang phục vụ (IN_PROGRESS)', value: 'IN_PROGRESS' },
                { label: 'Hoàn tất (COMPLETED)', value: 'COMPLETED' },
                { label: 'Khách không đến (NO_SHOW)', value: 'NO_SHOW' },
                { label: 'Đã hủy (CANCELLED)', value: 'CANCELLED' },
              ]}
              style={{ width: '220px' }}
            />
          </div>

          <DataTable
            columns={appointmentColumns}
            data={appointments}
            loading={loading}
            emptyMessage="Không có lịch hẹn spa nào phù hợp."
          />
          {aptTotalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 10, marginTop: 16 }}>
              <Button variant="outline" size="sm" disabled={aptPage === 0} onClick={() => fetchAppointments(aptPage - 1)}>Trước</Button>
              <span>Trang {aptPage + 1}/{aptTotalPages}</span>
              <Button variant="outline" size="sm" disabled={aptPage + 1 >= aptTotalPages} onClick={() => fetchAppointments(aptPage + 1)}>Sau</Button>
            </div>
          )}
        </div>
      )}

      {/* Tab: Services */}
      {activeTab === 'services' && (
        <>
          <div className="card">
            <h3>Dịch vụ Spa đơn lẻ</h3>
            <DataTable columns={serviceColumns} data={services} loading={false} emptyMessage="Chưa có dịch vụ spa." />
          </div>
          <div className="card" style={{ marginTop: 16 }}>
            <h3>Gói liệu trình nhiều buổi</h3>
            <DataTable columns={packageColumns} data={packages} loading={false} emptyMessage="Chưa có gói liệu trình." />
          </div>
        </>
      )}

      {/* Tab: Categories */}
      {activeTab === 'categories' && (
        <div className="card">
          <DataTable
            columns={categoryColumns}
            data={spaCategories}
            loading={false}
            emptyMessage="Chưa có danh mục dịch vụ spa nào."
          />
        </div>
      )}

      {/* Tab: Staff */}
      {activeTab === 'staff' && (
        <div className="card">
          <DataTable
            columns={staffColumns}
            data={staffList}
            loading={false}
            emptyMessage="Chưa có chuyên viên nào. Nhấn 'Thêm chuyên viên mới' để tạo hồ sơ."
          />
        </div>
      )}

      {/* Tab: Tickets */}
      {activeTab === 'tickets' && (
        <div className="card">
          <DataTable
            columns={ticketColumns}
            data={tickets}
            loading={false}
            emptyMessage="Không có vé dịch vụ liệu trình nào."
          />
        </div>
      )}

      {/* Tab: Facilities */}
      {activeTab === 'facilities' && (
        <div className="card">
          <DataTable
            columns={facilityColumns}
            data={facilities}
            loading={false}
            emptyMessage="Chưa có cơ sở vật chất hoặc thiết bị spa nào."
          />
        </div>
      )}

      {/* Tab: Preparation Form Templates */}
      {activeTab === 'preparation' && (
        <div className="card">
          <div style={{ marginBottom: 12, color: 'var(--text-muted)', fontSize: 13 }}>
            Các mẫu biểu câu hỏi khảo sát tiền sử dị ứng & bệnh lý trước khi thực hiện dịch vụ Spa (bất biến sau khi lưu).
          </div>
          <DataTable
            columns={templateColumns}
            data={formTemplates}
            loading={false}
            emptyMessage="Chưa có biểu mẫu khảo sát an toàn tiền liệu trình nào."
          />
        </div>
      )}

      {/* MODAL: UPDATE APPOINTMENT STATUS */}
      <Modal
        isOpen={statusModalOpen}
        onClose={() => setStatusModalOpen(false)}
        title={`Cập nhật lịch hẹn #${selectedApt?.id}`}
      >
        <form onSubmit={handleUpdateStatus}>
          <Select
            label="Trạng thái lịch hẹn"
            value={targetAptStatus}
            onChange={(e) => setTargetAptStatus(e.target.value)}
            options={[
              { label: 'Xác nhận lịch (CONFIRMED)', value: 'CONFIRMED' },
              { label: 'Bắt đầu phục vụ (IN_PROGRESS)', value: 'IN_PROGRESS' },
              { label: 'Hoàn tất buổi dịch vụ (COMPLETED)', value: 'COMPLETED' },
              { label: 'Khách không đến (NO_SHOW)', value: 'NO_SHOW' },
              { label: 'Hủy lịch hẹn (CANCELLED)', value: 'CANCELLED' },
            ]}
          />
          {isAdmin && targetAptStatus === 'CONFIRMED' && (selectedApt?.items || []).map((item) => (
            <div key={item.id} style={{ marginTop: '12px' }}>
              <Select
                label={`Chuyên viên phụ trách: ${item.serviceName || ('Dịch vụ #' + item.serviceId)}`}
                value={String(appointmentAssignments[item.id] || '')}
                onChange={(e) => setAppointmentAssignments((current) => ({
                  ...current,
                  [item.id]: e.target.value,
                }))}
                options={[
                  { label: 'Chưa chỉ định', value: '' },
                  ...(() => {
                    const activeStaff = staffList.filter((s) => s.active !== false);
                    const matchingStaff = activeStaff.filter((s) => s.skills?.some((sk) => Number(sk.serviceId || sk.id) === Number(item.serviceId)));
                    const listToUse = matchingStaff.length > 0 ? matchingStaff : activeStaff;
                    return listToUse.map((staff) => ({
                      label: `${staff.fullName || `Chuyên viên #${staff.id}`}${staff.skills?.some((sk) => Number(sk.serviceId || sk.id) === Number(item.serviceId)) ? ' (Đúng chuyên môn)' : ''}`,
                      value: String(staff.id),
                    }));
                  })(),
                ]}
              />
            </div>
          ))}
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Ghi chú của chuyên viên / Lễ tân</label>
            <textarea
              className="form-textarea"
              rows={3}
              value={staffNote}
              onChange={(e) => setStaffNote(e.target.value)}
              placeholder="VD: Khách hàng hài lòng, liệu trình bước 2..."
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setStatusModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={submittingStatus}>Cập nhật trạng thái</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: CREATE / EDIT SPA SERVICE */}
      <Modal
        isOpen={serviceModalOpen}
        onClose={() => setServiceModalOpen(false)}
        title={editingService ? `Cập nhật Dịch Vụ Spa: ${editingService.name}` : 'Thêm Dịch Vụ Spa Mới'}
        maxWidth="540px"
      >
        <form onSubmit={handleSubmitService}>
          <Input label="Tên dịch vụ Spa *" value={srvName} onChange={(e) => setSrvName(e.target.value)} required placeholder="Massage Mặt Chuyên Sâu Cấp Ẩm" />
          <div style={{ marginTop: '12px' }}>
            <Input label="Đường dẫn Slug (tự tạo nếu để trống)" value={srvSlug} onChange={(e) => setSrvSlug(e.target.value)} placeholder="massage-mat-chuyen-sau" />
          </div>
          <div className="grid-3" style={{ marginTop: '12px' }}>
            <Input label="Giá dịch vụ (VNĐ) *" type="number" value={srvPrice} onChange={(e) => setSrvPrice(e.target.value)} required />
            <Input label="Thời lượng (Phút) *" type="number" value={srvDuration} onChange={(e) => setSrvDuration(e.target.value)} required />
            <Input label="Thời gian chuẩn bị (Phút)" type="number" value={srvPrepMinutes} onChange={(e) => setSrvPrepMinutes(e.target.value)} />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Select
              label="Danh mục Spa"
              value={srvCatId}
              onChange={(e) => setSrvCatId(e.target.value)}
              options={[{ label: '-- Chưa chọn danh mục --', value: '' }, ...spaCategories.map((c) => ({ label: c.name, value: String(c.id) }))]}
            />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả quy trình dịch vụ</label>
            <textarea className="form-textarea" rows={3} value={srvDesc} onChange={(e) => setSrvDesc(e.target.value)} placeholder="Chi tiết các bước thực hiện..." />
          </div>
          {editingService && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px' }}>
              <input type="checkbox" id="srvActiveCheck" checked={srvActive} onChange={(e) => setSrvActive(e.target.checked)} />
              <label htmlFor="srvActiveCheck" style={{ fontSize: '13px', fontWeight: 600 }}>Dịch vụ đang hoạt động (Active)</label>
            </div>
          )}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setServiceModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={srvSubmitting}>Lưu dịch vụ</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: CREATE / EDIT SERVICE PACKAGE */}
      <Modal
        isOpen={packageModalOpen}
        onClose={() => setPackageModalOpen(false)}
        title={editingPackage ? `Cập nhật gói liệu trình: ${editingPackage.name}` : 'Tạo gói liệu trình mới'}
        maxWidth="680px"
      >
        <form onSubmit={handleSubmitPackage}>
          <Input label="Tên gói *" required value={packageForm.name} onChange={(e) => setPackageForm({ ...packageForm, name: e.target.value })} placeholder="Combo 5 buổi Peel da sinh học" />
          <div className="grid-2" style={{ marginTop: 12 }}>
            <Input label="Giá gói (VNĐ) *" type="number" min="0" required value={packageForm.price} onChange={(e) => setPackageForm({ ...packageForm, price: e.target.value })} />
            <Input label="Hiệu lực (ngày) *" type="number" min="1" required value={packageForm.validityDays} onChange={(e) => setPackageForm({ ...packageForm, validityDays: e.target.value })} />
          </div>
          <div style={{ marginTop: 12 }}>
            <label className="form-label">Mô tả</label>
            <textarea className="form-textarea" rows={2} value={packageForm.description} onChange={(e) => setPackageForm({ ...packageForm, description: e.target.value })} />
          </div>
          <h4 style={{ marginTop: 14, marginBottom: 8, fontSize: 13, textTransform: 'uppercase' }}>Dịch vụ trong gói</h4>
          {packageForm.items.map((item, index) => (
            <div key={index} className="grid-2" style={{ marginBottom: 10 }}>
              <Select
                label="Dịch vụ *"
                required
                value={item.serviceId}
                onChange={(e) => setPackageForm({
                  ...packageForm,
                  items: packageForm.items.map((current, itemIndex) => (
                    itemIndex === index ? { ...current, serviceId: e.target.value } : current
                  )),
                })}
                options={services.map((service) => ({ label: service.name, value: String(service.id) }))}
              />
              <Input
                label="Số buổi *"
                type="number"
                min="1"
                required
                value={item.quantity}
                onChange={(e) => setPackageForm({
                  ...packageForm,
                  items: packageForm.items.map((current, itemIndex) => (
                    itemIndex === index ? { ...current, quantity: e.target.value } : current
                  )),
                })}
              />
              {packageForm.items.length > 1 && (
                <Button type="button" variant="danger" size="sm" onClick={() => setPackageForm({ ...packageForm, items: packageForm.items.filter((_, itemIndex) => itemIndex !== index) })}>
                  Xóa dòng
                </Button>
              )}
            </div>
          ))}
          <Button type="button" variant="outline" size="sm" onClick={() => setPackageForm({ ...packageForm, items: [...packageForm.items, { serviceId: services[0]?.id ? String(services[0].id) : '', quantity: 1 }] })}>
            + Thêm dịch vụ vào gói
          </Button>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
            <Button type="button" variant="outline" onClick={() => setPackageModalOpen(false)}>Hủy</Button>
            <Button type="submit" loading={packageSubmitting}>Lưu gói liệu trình</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: CREATE / EDIT SPA CATEGORY */}
      <Modal
        isOpen={categoryModalOpen}
        onClose={() => setCategoryModalOpen(false)}
        title={editingCategory ? `Cập nhật danh mục: ${editingCategory.name}` : 'Thêm Danh Mục Dịch Vụ Spa'}
        maxWidth="500px"
      >
        <form onSubmit={handleSubmitCategory}>
          <Input label="Tên danh mục *" value={catName} onChange={(e) => setCatName(e.target.value)} required placeholder="Chăm sóc da mặt chuyên sâu" />
          <div style={{ marginTop: '12px' }}>
            <Input label="Đường dẫn Slug" value={catSlug} onChange={(e) => setCatSlug(e.target.value)} placeholder="cham-soc-da-mat" />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả danh mục</label>
            <textarea className="form-textarea" rows={3} value={catDesc} onChange={(e) => setCatDesc(e.target.value)} placeholder="Các liệu trình phục hồi, trẻ hóa..." />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px' }}>
            <input type="checkbox" id="catActiveCheck" checked={catActive} onChange={(e) => setCatActive(e.target.checked)} />
            <label htmlFor="catActiveCheck" style={{ fontSize: '13px', fontWeight: 600 }}>Hiển thị danh mục trên menu dịch vụ</label>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setCategoryModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={catSubmitting}>Lưu danh mục</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: SERVICE RESOURCE REQUIREMENTS */}
      <Modal
        isOpen={resourceModalOpen}
        onClose={() => setResourceModalOpen(false)}
        title={`Định mức Tài nguyên: ${selectedServiceForResource?.name || ''}`}
        maxWidth="580px"
      >
        <form onSubmit={handleSaveResourceRequirements}>
          <div style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '14px' }}>
            Cấu hình số giường, phòng hoặc máy móc bắt buộc để hệ thống tự động giữ chỗ khi khách đặt lịch dịch vụ này.
          </div>
          {resourceRequirements.length === 0 ? (
            <div style={{ padding: '16px', backgroundColor: 'var(--color-primary-50)', textAlign: 'center', color: 'var(--text-muted)', marginBottom: '14px' }}>
              Chưa cấu hình ràng buộc tài nguyên. Dịch vụ không bị kiểm tra xung đột buồng/máy.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
              {resourceRequirements.map((req, idx) => (
                <div key={idx} style={{ display: 'flex', gap: '10px', alignItems: 'center', padding: '10px', border: '1px solid var(--border-subtle)', borderRadius: '6px' }}>
                  <div style={{ flex: 1 }}>
                    <Select
                      label="Loại tài nguyên"
                      value={req.type}
                      onChange={(e) => {
                        const val = e.target.value;
                        setResourceRequirements((prev) => prev.map((item, i) => i === idx ? { ...item, type: val } : item));
                      }}
                      options={[
                        { label: 'Giường chăm sóc (BED)', value: 'BED' },
                        { label: 'Phòng trị liệu (ROOM)', value: 'ROOM' },
                        { label: 'Máy móc công nghệ cao (MACHINE)', value: 'MACHINE' },
                        { label: 'Khu vực chung (GENERAL)', value: 'GENERAL' },
                      ]}
                    />
                  </div>
                  <div style={{ width: '110px' }}>
                    <Input
                      label="Số lượng"
                      type="number"
                      min={1}
                      value={req.units}
                      onChange={(e) => {
                        const val = e.target.value;
                        setResourceRequirements((prev) => prev.map((item, i) => i === idx ? { ...item, units: val } : item));
                      }}
                      required
                    />
                  </div>
                  <div style={{ alignSelf: 'flex-end', marginBottom: 2 }}>
                    <Button
                      type="button"
                      variant="danger"
                      size="sm"
                      onClick={() => setResourceRequirements((prev) => prev.filter((_, i) => i !== idx))}
                    >
                      Xóa
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setResourceRequirements((prev) => [...prev, { type: 'BED', units: 1 }])}
          >
            + Thêm ràng buộc tài nguyên
          </Button>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setResourceModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={resourceSubmitting}>Lưu định mức tài nguyên</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: SERVICE SAFETY POLICY & PREPARATION */}
      <Modal
        isOpen={policyModalOpen}
        onClose={() => setPolicyModalOpen(false)}
        title={`Chính sách An toàn & Khảo sát: ${selectedServiceForPolicy?.name || ''}`}
        maxWidth="620px"
      >
        <form onSubmit={handleSavePolicy}>
          <div style={{ padding: '12px 14px', backgroundColor: 'var(--color-primary-50)', border: '1px solid var(--border-subtle)', borderRadius: '6px', marginBottom: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <input
                type="checkbox"
                id="chkWarningsReq"
                checked={policyWarningsRequired}
                onChange={(e) => setPolicyWarningsRequired(e.target.checked)}
                style={{ width: '16px', height: '16px' }}
              />
              <label htmlFor="chkWarningsReq" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                Bắt buộc chuyên viên xác nhận cảnh báo an toàn & rủi ro trước khi bắt đầu dịch vụ
              </label>
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', marginLeft: '24px' }}>
              Áp dụng cho các liệu trình peel nồng độ cao, laser vi điểm hoặc xâm lấn cần đọc kỹ tiền sử da của khách.
            </div>
          </div>

          <div style={{ marginBottom: '14px' }}>
            <label className="form-label" style={{ fontWeight: 600 }}>Biểu mẫu câu hỏi sàng lọc khách hàng cần hoàn tất:</label>
            {formTemplates.length === 0 ? (
              <div style={{ padding: '12px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                Chưa có biểu mẫu nào trong hệ thống. Hãy qua tab 'Biểu mẫu khảo sát & An toàn' để tạo mẫu.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '200px', overflowY: 'auto' }}>
                {formTemplates.map((tpl) => {
                  const isChecked = policySelectedFormVersions.includes(tpl.id);
                  return (
                    <div
                      key={tpl.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '10px 12px',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: '6px',
                        backgroundColor: isChecked ? 'var(--color-primary-50)' : '#fff',
                      }}
                    >
                      <div>
                        <strong>{tpl.title}</strong>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                          Phiên bản: v{tpl.version ?? tpl.versionNumber ?? 1} · {tpl.questions?.length || 0} câu hỏi
                        </div>
                      </div>
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setPolicySelectedFormVersions((prev) => [...prev, tpl.id]);
                          } else {
                            setPolicySelectedFormVersions((prev) => prev.filter((id) => id !== tpl.id));
                          }
                        }}
                        style={{ width: '18px', height: '18px' }}
                      />
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setPolicyModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={policySubmitting}>Lưu chính sách an toàn</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: STAFF SKILLS MANAGEMENT */}
      <Modal
        isOpen={skillsModalOpen}
        onClose={() => setSkillsModalOpen(false)}
        title={`Kỹ Năng & Dịch Vụ Cho Phép: ${selectedStaffForSkills?.fullName || `Chuyên viên #${selectedStaffForSkills?.id}`}`}
        maxWidth="600px"
      >
        <div>
          <div style={{ marginBottom: '16px' }}>
            <label className="form-label">Kỹ năng dịch vụ hiện có ({staffSkillList.length})</label>
            {staffSkillList.length === 0 ? (
              <div style={{ padding: '14px', backgroundColor: 'var(--color-primary-50)', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                Chuyên viên này chưa được gán kỹ năng dịch vụ nào.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '220px', overflowY: 'auto' }}>
                {staffSkillList.map((skill) => (
                  <div key={skill.serviceId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px', border: '1px solid var(--border-subtle)', borderRadius: '6px' }}>
                    <div>
                      <strong>{skill.serviceName || `Dịch vụ #${skill.serviceId}`}</strong>
                      <div style={{ marginTop: '2px' }}>
                        {skill.certified ? (
                          <Badge variant="success">Đã cấp chứng chỉ (Certified)</Badge>
                        ) : (
                          <Badge variant="default">Kỹ năng cơ bản</Badge>
                        )}
                      </div>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => handleDeleteSkill(skill.serviceId)} icon={Trash2} title="Xóa kỹ năng" />
                  </div>
                ))}
              </div>
            )}
          </div>

          <form onSubmit={handleAddSkill} style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
            <div style={{ fontWeight: 600, fontSize: '13px', marginBottom: '10px' }}>Gán thêm kỹ năng dịch vụ</div>
            <div className="grid-2">
              <Select
                label="Chọn dịch vụ Spa *"
                value={newSkillServiceId}
                onChange={(e) => setNewSkillServiceId(e.target.value)}
                options={services.map((s) => ({ label: s.name, value: String(s.id) }))}
                required
              />
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '24px' }}>
                <input
                  type="checkbox"
                  id="chkCertified"
                  checked={newSkillCertified}
                  onChange={(e) => setNewSkillCertified(e.target.checked)}
                />
                <label htmlFor="chkCertified" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                  Có chứng chỉ chuyên môn
                </label>
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
              <Button variant="outline" type="button" onClick={() => setSkillsModalOpen(false)}>Đóng</Button>
              <Button variant="primary" type="submit" loading={skillSubmitting} icon={Award}>Gán kỹ năng</Button>
            </div>
          </form>
        </div>
      </Modal>

      {/* MODAL: CREATE FORM TEMPLATE */}
      <Modal
        isOpen={createTemplateModalOpen}
        onClose={() => setCreateTemplateModalOpen(false)}
        title="Tạo Biểu Mẫu Khảo Sát Tiền Liệu Trình"
        maxWidth="680px"
      >
        <form onSubmit={handleCreateTemplate}>
          <Input
            label="Tiêu đề biểu mẫu *"
            value={tplTitle}
            onChange={(e) => setTplTitle(e.target.value)}
            placeholder="VD: Phiếu khảo sát tiền liệu trình Peel da sinh học"
            required
          />

          <div style={{ marginTop: '16px', marginBottom: '10px' }}>
            <label className="form-label" style={{ fontWeight: 600 }}>Danh sách câu hỏi ({tplQuestions.length})</label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {tplQuestions.map((q, idx) => (
                <div key={idx} style={{ padding: '10px', border: '1px solid var(--border-subtle)', borderRadius: '6px' }}>
                  <div className="grid-2">
                    <Input
                      label={`Mã câu hỏi (key) #${idx + 1} *`}
                      value={q.key}
                      onChange={(e) => {
                        const val = e.target.value;
                        setTplQuestions((prev) => prev.map((item, i) => i === idx ? { ...item, key: val } : item));
                      }}
                      placeholder="VD: hasAllergy, skinType"
                      required
                    />
                    <Select
                      label="Kiểu câu trả lời"
                      value={q.type}
                      onChange={(e) => {
                        const val = e.target.value;
                        setTplQuestions((prev) => prev.map((item, i) => i === idx ? { ...item, type: val } : item));
                      }}
                      options={[
                        { label: 'Đúng / Sai (BOOLEAN)', value: 'BOOLEAN' },
                        { label: 'Văn bản (TEXT)', value: 'TEXT' },
                      ]}
                    />
                  </div>
                  <div style={{ marginTop: '8px' }}>
                    <Input
                      label="Nội dung câu hỏi *"
                      value={q.label}
                      onChange={(e) => {
                        const val = e.target.value;
                        setTplQuestions((prev) => prev.map((item, i) => i === idx ? { ...item, label: val } : item));
                      }}
                      placeholder="VD: Bạn có đang mang thai hoặc cho con bú không?"
                      required
                    />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <input
                        type="checkbox"
                        id={`reqCheck_${idx}`}
                        checked={q.required}
                        onChange={(e) => {
                          const val = e.target.checked;
                          setTplQuestions((prev) => prev.map((item, i) => i === idx ? { ...item, required: val } : item));
                        }}
                      />
                      <label htmlFor={`reqCheck_${idx}`} style={{ fontSize: '12px' }}>Bắt buộc trả lời</label>
                    </div>
                    {tplQuestions.length > 1 && (
                      <Button
                        type="button"
                        variant="danger"
                        size="sm"
                        onClick={() => setTplQuestions((prev) => prev.filter((_, i) => i !== idx))}
                      >
                        Xóa câu hỏi
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              style={{ marginTop: '10px' }}
              onClick={() => setTplQuestions((prev) => [
                ...prev,
                { key: `question_${prev.length + 1}`, label: '', type: 'TEXT', required: false },
              ])}
            >
              + Thêm câu hỏi
            </Button>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setCreateTemplateModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={tplSubmitting}>Tạo mẫu khảo sát</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: CREATE STAFF PROFILE */}
      <Modal
        isOpen={createStaffModalOpen}
        onClose={() => setCreateStaffModalOpen(false)}
        title="Tạo Hồ Sơ Chuyên Viên Mới"
        maxWidth="500px"
      >
        <form onSubmit={handleCreateStaff}>
          <Input label="Mã User ID tài khoản chuyên viên *" type="number" value={staffUserId} onChange={(e) => setStaffUserId(e.target.value)} required placeholder="VD: 2, 3..." />
          <div style={{ marginTop: '12px' }}>
            <Input label="Chuyên môn trị liệu *" value={staffSpecialty} onChange={(e) => setStaffSpecialty(e.target.value)} required />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Tiểu sử & Kinh nghiệm chuyên viên</label>
            <textarea className="form-textarea" rows={3} value={staffBio} onChange={(e) => setStaffBio(e.target.value)} placeholder="5 năm kinh nghiệm trị liệu da..." />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setCreateStaffModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={staffSubmitting}>Tạo chuyên viên</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: ASSIGN SHIFT SCHEDULE TO STAFF */}
      <Modal
        isOpen={scheduleModalOpen}
        onClose={() => setScheduleModalOpen(false)}
        title={`Xếp Ca Trực cho: ${selectedStaffForSchedule?.fullName || `Chuyên viên #${selectedStaffForSchedule?.id}`}`}
        maxWidth="500px"
      >
        <form onSubmit={handleAddShift}>
          <Input label="Ngày làm việc (Work Date) *" type="date" value={shiftDate} onChange={(e) => setShiftDate(e.target.value)} required />
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Giờ bắt đầu *" type="time" step="1" value={shiftStart} onChange={(e) => setShiftStart(e.target.value)} required />
            <Input label="Giờ kết thúc *" type="time" step="1" value={shiftEnd} onChange={(e) => setShiftEnd(e.target.value)} required />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input label="Ghi chú ca trực" value={shiftNote} onChange={(e) => setShiftNote(e.target.value)} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setScheduleModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={shiftSubmitting}>Lưu ca trực</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: EXTEND TICKET */}
      <Modal
        isOpen={extendModalOpen}
        onClose={() => setExtendModalOpen(false)}
        title={`Gia hạn Vé dịch vụ #${selectedTicket?.id}`}
      >
        <form onSubmit={handleExtendTicket}>
          <Input label="Số ngày gia hạn thêm *" type="number" min={1} max={3650} value={extendDays} onChange={(e) => setExtendDays(e.target.value)} required />
          <div style={{ marginTop: '12px' }}>
            <Input label="Lý do gia hạn *" value={extendReason} onChange={(e) => setExtendReason(e.target.value)} required placeholder="VD: Gia hạn theo phê duyệt quản lý" />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setExtendModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit">Xác nhận gia hạn</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: COMPENSATE SESSIONS */}
      <Modal
        isOpen={compensateModalOpen}
        onClose={() => setCompensateModalOpen(false)}
        title={`Bồi thường buổi cho Vé #${selectedTicket?.id}`}
      >
        <form onSubmit={handleCompensateTicket}>
          <div style={{ marginBottom: '12px' }}>
            <Select
              label="Dịch vụ bồi thường"
              value={compensateServiceId}
              onChange={(e) => setCompensateServiceId(e.target.value)}
              options={services.map((s) => ({ label: s.name, value: String(s.id) }))}
            />
          </div>
          <Input label="Số buổi bồi thường cộng thêm *" type="number" min={1} max={100} value={compensateSessions} onChange={(e) => setCompensateSessions(e.target.value)} required />
          <div style={{ marginTop: '12px' }}>
            <Input label="Lý do bồi thường *" value={compensateReason} onChange={(e) => setCompensateReason(e.target.value)} required placeholder="VD: Bồi thường buổi theo biên bản xử lý" />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setCompensateModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit">Cộng buổi bồi thường</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: CREATE / EDIT FACILITY */}
      <Modal
        isOpen={facilityModalOpen}
        onClose={() => setFacilityModalOpen(false)}
        title={editingFacility ? `Cập nhật: ${editingFacility.name}` : 'Thêm Phòng / Thiết bị Spa'}
        maxWidth="500px"
      >
        <form onSubmit={handleSubmitFacility}>
          <Input label="Tên phòng / thiết bị *" value={facName} onChange={(e) => setFacName(e.target.value)} required placeholder="VD: Phòng 101 - Khám da, Giường VIP 01" />
          <div style={{ marginTop: '12px' }}>
            <Select
              label="Phân loại phòng / tài nguyên *"
              value={facType}
              onChange={(e) => setFacType(e.target.value)}
              options={[
                { label: 'Phòng trị liệu riêng (ROOM)', value: 'ROOM' },
                { label: 'Giường chăm sóc (BED)', value: 'BED' },
                { label: 'Máy móc / Thiết bị (MACHINE)', value: 'MACHINE' },
                { label: 'Khu vực chung (GENERAL)', value: 'GENERAL' },
              ]}
            />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input
              label="Sức chứa giường / người *"
              type="number"
              min={1}
              value={facCapacity}
              onChange={(e) => setFacCapacity(e.target.value)}
              required
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '16px' }}>
            <input
              type="checkbox"
              id="facActiveCheck"
              checked={facActive}
              onChange={(e) => setFacActive(e.target.checked)}
            />
            <label htmlFor="facActiveCheck" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
              Đang hoạt động và sẵn sàng nhận khách
            </label>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setFacilityModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={facSubmitting}>Lưu tài nguyên</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: FACILITY BLOCKS / MAINTENANCE */}
      <Modal
        isOpen={blocksModalOpen}
        onClose={() => setBlocksModalOpen(false)}
        title={`Quản lý lịch bảo trì: ${selectedFacForBlocks?.name || ''}`}
        maxWidth="620px"
      >
        <div>
          <div style={{ marginBottom: '16px' }}>
            <label className="form-label">Các khoảng tạm ngừng / bảo trì hiện có</label>
            {blocksList.length === 0 ? (
              <div style={{ padding: '14px', backgroundColor: 'var(--color-primary-50)', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                Thiết bị đang hoạt động bình thường, không có lịch bảo trì nào.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {blocksList.map((b) => (
                  <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px', border: '1px solid var(--border-subtle)', borderRadius: '6px' }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '13px' }}>{b.reason}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        {new Date(b.startAt).toLocaleString('vi-VN')} → {new Date(b.endAt).toLocaleString('vi-VN')}
                      </div>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => handleDeleteBlock(b.id)} icon={Trash2} title="Hủy bảo trì" />
                  </div>
                ))}
              </div>
            )}
          </div>

          <form onSubmit={handleCreateBlock} style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
            <div style={{ fontWeight: 600, fontSize: '13px', marginBottom: '10px' }}>Tạo khoảng ngừng phục vụ mới</div>
            <div className="grid-2">
              <Input
                label="Bắt đầu bảo trì *"
                type="datetime-local"
                value={blockStart}
                onChange={(e) => setBlockStart(e.target.value)}
                required
              />
              <Input
                label="Kết thúc bảo trì *"
                type="datetime-local"
                value={blockEnd}
                onChange={(e) => setBlockEnd(e.target.value)}
                required
              />
            </div>
            <div style={{ marginTop: '12px' }}>
              <Input
                label="Lý do ngừng phục vụ *"
                value={blockReason}
                onChange={(e) => setBlockReason(e.target.value)}
                placeholder="VD: Vệ sinh máy, thay lõi lọc, bảo dưỡng..."
                required
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
              <Button variant="outline" type="button" onClick={() => setBlocksModalOpen(false)}>Đóng</Button>
              <Button variant="primary" type="submit" loading={blockSubmitting}>Thêm lịch bảo trì</Button>
            </div>
          </form>
        </div>
      </Modal>

      {/* MODAL: APPOINTMENT INVOICE & CHECKOUT */}
      <Modal
        isOpen={invoiceModalOpen}
        onClose={() => setInvoiceModalOpen(false)}
        title={`Thanh toán buổi Spa #${selectedAptForInvoice?.id}`}
        maxWidth="600px"
      >
        <div>
          {invoiceLoading ? (
            <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>Đang tra cứu hóa đơn buổi...</div>
          ) : !invoiceData ? (
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <AlertCircle size={40} color="var(--color-warning-500)" style={{ margin: '0 auto 12px' }} />
              <p style={{ fontSize: '14px', marginBottom: '16px' }}>
                Buổi spa chưa được lập hóa đơn thanh toán chính thức.
              </p>
              <div style={{ maxWidth: '300px', margin: '0 auto 16px', textAlign: 'left' }}>
                <Select
                  label="Phương thức thanh toán *"
                  value={invoicePaymentMethod}
                  onChange={(e) => setInvoicePaymentMethod(e.target.value)}
                  options={[
                    { label: 'Tiền mặt tại quầy (CASH)', value: 'CASH' },
                    { label: 'Chuyển khoản / QR ngân hàng (BANK)', value: 'BANK' },
                  ]}
                />
              </div>
              <Button variant="primary" onClick={handleCreateInvoice} loading={invoiceSubmitting} icon={Receipt}>
                Lập hóa đơn chốt buổi (Create Invoice)
              </Button>
            </div>
          ) : (
            <div>
              <div style={{ padding: '12px 14px', backgroundColor: 'var(--color-primary-50)', border: '1px solid var(--border-subtle)', borderRadius: '8px', marginBottom: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <span>Khách hàng:</span>
                  <strong>{selectedAptForInvoice?.customerName || 'Khách vãng lai'}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <span>Tổng tiền dịch vụ:</span>
                  <strong>{formatCurrency(invoiceData.totalAmount || 0)}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <span>Đã thanh toán:</span>
                  <strong style={{ color: 'var(--color-success-700)' }}>{formatCurrency(invoiceData.paidAmount || 0)}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '8px', borderTop: '1px solid var(--border-subtle)' }}>
                  <span>Số tiền còn thiếu (Balance Due):</span>
                  <strong style={{ color: invoiceData.balanceDue > 0 ? 'var(--color-danger-700)' : 'var(--color-success-700)', fontSize: '15px' }}>
                    {formatCurrency(invoiceData.balanceDue ?? 0)}
                  </strong>
                </div>
              </div>

              {invoiceData.balanceDue > 0 ? (
                <form onSubmit={handleCollectCash} style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
                  <div style={{ fontWeight: 600, fontSize: '13px', marginBottom: '10px' }}>
                    Thu tiền mặt tại quầy (Cash Receipt)
                  </div>
                  <Input
                    label="Số tiền thu (VNĐ) *"
                    type="number"
                    min={1}
                    max={invoiceData.balanceDue}
                    value={cashAmount}
                    onChange={(e) => setCashAmount(e.target.value)}
                    required
                  />
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                    <Button variant="outline" type="button" onClick={() => setInvoiceModalOpen(false)}>Đóng</Button>
                    <Button variant="primary" type="submit" loading={cashSubmitting} icon={DollarSign}>
                      Xác nhận thu tiền mặt
                    </Button>
                  </div>
                </form>
              ) : (
                <div style={{ textAlign: 'center', padding: '10px', color: 'var(--color-success-700)', fontWeight: 600 }}>
                  ✓ Buổi spa này đã được thanh toán đầy đủ.
                </div>
              )}
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
};
