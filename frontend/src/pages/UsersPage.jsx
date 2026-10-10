import React, { useState, useEffect } from 'react';
import {
  Users,
  Shield,
  LogOut,
  RefreshCw,
  Lock,
  Plus,
  Edit,
  Trash2,
  KeyRound,
  Key,
  Copy,
  Check,
  Search,
  History,
  XCircle,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatNumber,
  formatDateTime,
  getUserStatusBadge,
  getMembershipTierLabel,
  getRoleLabel,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';

export const UsersPage = () => {
  const [activeTab, setActiveTab] = useState('users');

  // --- USERS STATE ---
  const [users, setUsers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');

  // Status Modal
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [targetStatus, setTargetStatus] = useState('ACTIVE');
  const [statusReason, setStatusReason] = useState('');
  const [submittingStatus, setSubmittingStatus] = useState(false);
  const [statusErrorMessage, setStatusErrorMessage] = useState('');
  const [rolesModalOpen, setRolesModalOpen] = useState(false);
  const [selectedRoleIds, setSelectedRoleIds] = useState([]);
  const [sessionsModalOpen, setSessionsModalOpen] = useState(false);
  const [sessions, setSessions] = useState([]);
  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [statusHistoryList, setStatusHistoryList] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  // --- RESET PASSWORD STATE ---
  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [resetResult, setResetResult] = useState(null);
  const [resetSubmitting, setResetSubmitting] = useState(false);
  const [copiedPassword, setCopiedPassword] = useState(false);

  // --- ROLES STATE ---
  const [roles, setRoles] = useState([]);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [editingRole, setEditingRole] = useState(null);
  const [roleName, setRoleName] = useState('');
  const [roleDescription, setRoleDescription] = useState('');
  const [submittingRole, setSubmittingRole] = useState(false);
  const [roleErrorMessage, setRoleErrorMessage] = useState('');

  // Fetch Users
  const fetchUsers = async (p = 0) => {
    setLoadingUsers(true);
    try {
      const params = new URLSearchParams();
      params.append('page', p);
      params.append('size', '15');
      if (keyword.trim()) params.append('keyword', keyword.trim());
      if (statusFilter) params.append('status', statusFilter);
      if (roleFilter) params.append('role', roleFilter);

      const res = await apiClient.get(`${ENDPOINTS.USERS.LIST}?${params.toString()}`);
      const pageData = res.data || res;
      setUsers(pageData.content || []);
      setPage(pageData.page ?? p);
      setTotalPages(pageData.totalPages ?? 1);
      setTotalElements(pageData.totalElements ?? 0);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingUsers(false);
    }
  };

  // Fetch Roles
  const fetchRoles = async () => {
    setLoadingRoles(true);
    try {
      const res = await apiClient.get(ENDPOINTS.ROLES.LIST);
      const list = res.data || res;
      setRoles(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingRoles(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'users') {
      fetchUsers(0);
      fetchRoles();
    } else if (activeTab === 'roles') {
      fetchRoles();
    }
  }, [activeTab, statusFilter, roleFilter]);

  // Handle User Status Modal
  const handleOpenStatusModal = (user) => {
    setSelectedUser(user);
    setTargetStatus(user.status || 'ACTIVE');
    setStatusReason('');
    setStatusErrorMessage('');
    setStatusModalOpen(true);
  };

  const handleUpdateStatusSubmit = async (e) => {
    e.preventDefault();
    if (!selectedUser) return;
    setSubmittingStatus(true);
    setStatusErrorMessage('');
    try {
      await apiClient.put(ENDPOINTS.USERS.UPDATE_STATUS(selectedUser.id), {
        status: targetStatus,
        reason: statusReason.trim() || 'Cập nhật từ bảng điều khiển quản trị',
      });
      setStatusModalOpen(false);
      fetchUsers(page);
    } catch (err) {
      setStatusErrorMessage(err.message || 'Lỗi cập nhật trạng thái tài khoản');
    } finally {
      setSubmittingStatus(false);
    }
  };

  const handleForceLogout = async (userId) => {
    if (!window.confirm('Bạn có chắc muốn hủy bỏ tất cả phiên đăng nhập của người dùng này không?')) return;
    try {
      await apiClient.post(ENDPOINTS.USERS.FORCE_LOGOUT(userId));
      alert('Đã thu hồi token và buộc đăng xuất tài khoản thành công.');
    } catch (err) {
      alert(err.message || 'Lỗi buộc đăng xuất');
    }
  };

  const handleResetPassword = async (user) => {
    if (!window.confirm(`Bạn có chắc chắn muốn đặt lại mật khẩu tạm thời cho tài khoản "${user.username}"?\n\nToàn bộ phiên đăng nhập hiện tại của người dùng này sẽ bị thu hồi ngay lập tức.`)) {
      return;
    }
    setResetSubmitting(true);
    try {
      const res = await apiClient.post(ENDPOINTS.USERS.RESET_PASSWORD(user.id));
      const data = res.data || res;
      setResetResult(data);
      setCopiedPassword(false);
      setResetModalOpen(true);
      fetchUsers(page);
    } catch (err) {
      alert(err.message || 'Lỗi khi đặt lại mật khẩu');
    } finally {
      setResetSubmitting(false);
    }
  };

  const handleCopyPassword = () => {
    if (!resetResult?.temporaryPassword) return;
    navigator.clipboard.writeText(resetResult.temporaryPassword);
    setCopiedPassword(true);
    setTimeout(() => setCopiedPassword(false), 2000);
  };

  const handleOpenRoles = async (user) => {
    setSelectedUser(user);
    let availableRoles = roles;
    if (!availableRoles.length) {
      const res = await apiClient.get(ENDPOINTS.ROLES.LIST);
      availableRoles = res.data || res || [];
      setRoles(availableRoles);
    }
    const currentNames = new Set(user.roles || []);
    setSelectedRoleIds(availableRoles.filter((role) => currentNames.has(role.roleName)).map((role) => role.id));
    setRolesModalOpen(true);
  };

  const handleAssignRoles = async () => {
    if (!selectedUser || selectedRoleIds.length === 0) return;
    setSubmittingRole(true);
    try {
      await apiClient.put(ENDPOINTS.USERS.UPDATE_ROLES(selectedUser.id), { roleIds: selectedRoleIds });
      setRolesModalOpen(false);
      fetchUsers(page);
    } catch (err) {
      alert(err.message || 'Lỗi phân quyền người dùng');
    } finally {
      setSubmittingRole(false);
    }
  };
  const handleOpenSessions = async (user) => {
    setSelectedUser(user);
    const res = await apiClient.get(ENDPOINTS.USERS.SESSIONS(user.id));
    setSessions(res.data || res || []);
    setSessionsModalOpen(true);
  };

  const handleRevokeSession = async (sessionId) => {
    if (!selectedUser || !sessionId) return;
    if (!window.confirm(`Thu hồi phiên #${sessionId}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.USERS.REVOKE_SESSION(selectedUser.id, sessionId));
      const res = await apiClient.get(ENDPOINTS.USERS.SESSIONS(selectedUser.id));
      setSessions(res.data || res || []);
    } catch (err) {
      alert(err.message || 'Lỗi khi thu hồi phiên');
    }
  };

  const handleOpenStatusHistory = async (user) => {
    setSelectedUser(user);
    setHistoryModalOpen(true);
    setHistoryLoading(true);
    try {
      const res = await apiClient.get(ENDPOINTS.USERS.STATUS_HISTORY(user.id));
      const data = res.data || res;
      setStatusHistoryList(data?.content || (Array.isArray(data) ? data : []));
    } catch (err) {
      console.error(err);
    } finally {
      setHistoryLoading(false);
    }
  };

  // Handle Role Create / Edit Modal
  const handleOpenCreateRole = () => {
    setEditingRole(null);
    setRoleName('');
    setRoleDescription('');
    setRoleErrorMessage('');
    setRoleModalOpen(true);
  };

  const handleOpenEditRole = (role) => {
    setEditingRole(role);
    setRoleName(role.roleName || '');
    setRoleDescription(role.description || '');
    setRoleErrorMessage('');
    setRoleModalOpen(true);
  };

  const handleRoleSubmit = async (e) => {
    e.preventDefault();
    setSubmittingRole(true);
    setRoleErrorMessage('');
    try {
      let formattedName = roleName.trim().toUpperCase();
      if (!formattedName.startsWith('ROLE_')) {
        formattedName = `ROLE_${formattedName}`;
      }

      const payload = {
        roleName: formattedName,
        description: roleDescription.trim(),
      };

      if (editingRole) {
        await apiClient.put(ENDPOINTS.ROLES.DETAIL(editingRole.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.ROLES.LIST, payload);
      }
      setRoleModalOpen(false);
      fetchRoles();
    } catch (err) {
      setRoleErrorMessage(err.message || 'Lỗi lưu thông tin vai trò');
    } finally {
      setSubmittingRole(false);
    }
  };

  const handleDeleteRole = async (role) => {
    if (['ROLE_ADMIN', 'ROLE_STAFF', 'ROLE_USER', 'ROLE_CUSTOMER'].includes(role.roleName)) {
      alert('Đây là vai trò hệ thống mặc định (Core System Role), không được phép xóa.');
      return;
    }
    if (!window.confirm(`Bạn có chắc muốn xóa vai trò "${role.roleName}" không?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.ROLES.DETAIL(role.id));
      fetchRoles();
    } catch (err) {
      alert(err.message || 'Lỗi xóa vai trò');
    }
  };

  // User Columns
  const userColumns = [
    {
      header: 'ID',
      accessor: 'id',
      width: '60px',
    },
    {
      header: 'Tài khoản & Họ tên',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>{row.username}</div>
          <div style={{ fontSize: '12px', color: 'var(--text-main)' }}>{row.fullName || '-'}</div>
        </div>
      ),
    },
    {
      header: 'Liên hệ',
      accessor: (row) => (
        <div>
          <div>{row.email}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{row.phone || 'Chưa cập nhật SĐT'}</div>
        </div>
      ),
    },
    {
      header: 'Hạng & Điểm thưởng',
      accessor: (row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Badge variant="info">{getMembershipTierLabel(row.membershipTier)}</Badge>
          <span style={{ fontSize: '12px', fontWeight: 600 }}>
            {formatNumber(row.loyaltyPoints || 0)} điểm
          </span>
        </div>
      ),
    },
    {
      header: 'Vai trò tài khoản',
      accessor: (row) => (
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {(row.roles || []).map((r, idx) => (
            <span
              key={idx}
              style={{
                fontSize: '11px',
                fontWeight: 600,
                padding: '2px 6px',
                borderRadius: '3px',
                backgroundColor: r.includes('ADMIN') ? 'var(--color-primary-900)' : 'var(--color-primary-100)',
                color: r.includes('ADMIN') ? '#FFFFFF' : 'var(--color-primary-900)',
              }}
            >
              {getRoleLabel(r)}
            </span>
          ))}
        </div>
      ),
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const badge = getUserStatusBadge(row.status);
        return <Badge variant={badge.variant}>{badge.text}</Badge>;
      },
    },
    {
      header: 'Bảo mật & Quản lý',
      align: 'right',
      accessor: (row) => (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenRoles(row)} icon={KeyRound} title="Gán vai trò">
            Phân quyền
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleResetPassword(row)}
            icon={Key}
            title="Đặt lại mật khẩu tạm thời"
          >
            Đổi mật khẩu
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenSessions(row)} title="Xem phiên đăng nhập">
            Phiên
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenStatusHistory(row)} icon={History} title="Xem lịch sử thay đổi trạng thái">
            Lịch sử
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenStatusModal(row)}
            icon={Lock}
            title="Đổi trạng thái tài khoản"
          >
            Trạng thái
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={() => handleForceLogout(row.id)}
            icon={LogOut}
            title="Buộc đăng xuất toàn bộ thiết bị"
          >
            Kill Session
          </Button>
        </div>
      ),
      render: (row) => (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
          <Button variant="outline" size="sm" onClick={() => handleOpenRoles(row)} icon={KeyRound} title="Gán vai trò">
            Phân quyền
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleResetPassword(row)}
            icon={Key}
            title="Đặt lại mật khẩu tạm thời"
          >
            Đổi mật khẩu
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenSessions(row)} title="Xem phiên đăng nhập">
            Phiên
          </Button>
          <Button variant="outline" size="sm" onClick={() => handleOpenStatusHistory(row)} icon={History} title="Xem lịch sử thay đổi trạng thái">
            Lịch sử
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenStatusModal(row)}
            icon={Lock}
            title="Đổi trạng thái tài khoản"
          >
            Trạng thái
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={() => handleForceLogout(row.id)}
            icon={LogOut}
            title="Buộc đăng xuất toàn bộ thiết bị"
          >
            Kill Session
          </Button>
        </div>
      ),
    },
  ];

  // Role Columns
  const roleColumns = [
    {
      header: 'ID',
      accessor: 'id',
      width: '60px',
    },
    {
      header: 'Mã Vai trò (Role Identifier)',
      accessor: (row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Shield
            size={16}
            color={row.roleName.includes('ADMIN') ? 'var(--color-primary-900)' : 'var(--color-accent-600)'}
          />
          <div>
            <div style={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '13px' }}>
              {row.roleName}
            </div>
            {['ROLE_ADMIN', 'ROLE_STAFF', 'ROLE_USER', 'ROLE_CUSTOMER'].includes(row.roleName) && (
              <span style={{ fontSize: '10px', color: 'var(--color-accent-700)', fontWeight: 600 }}>
                Hệ thống cốt lõi (Core)
              </span>
            )}
          </div>
        </div>
      ),
    },
    {
      header: 'Mô tả phạm vi quyền hạn',
      accessor: (row) => (
        <div style={{ fontSize: '13px', color: 'var(--text-main)', maxWidth: '400px' }}>
          {row.description || 'Chưa có mô tả chi tiết quyền hạn'}
        </div>
      ),
    },
    {
      header: 'Ngày khởi tạo / Cập nhật',
      accessor: (row) => (
        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
          {row.updatedAt ? formatDateTime(row.updatedAt) : (row.createdAt ? formatDateTime(row.createdAt) : 'Mặc định')}
        </div>
      ),
    },
    {
      header: 'Thao tác',
      align: 'right',
      render: (row) => {
        const isCoreRole = ['ROLE_ADMIN', 'ROLE_STAFF', 'ROLE_USER', 'ROLE_CUSTOMER'].includes(row.roleName);
        return (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleOpenEditRole(row)}
              icon={Edit}
            >
              Sửa
            </Button>
            {!isCoreRole && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => handleDeleteRole(row)}
                icon={Trash2}
              >
                Xóa
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản trị Người dùng & Phân quyền RBAC</h1>
          <p className="page-subtitle">
            Quản lý tài khoản người dùng, cấp bậc thẻ tích điểm, giám sát bảo mật tài khoản và ma trận vai trò quyền hạn
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          {activeTab === 'users' ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => fetchUsers(page)}
              loading={loadingUsers}
              icon={RefreshCw}
            >
              Làm mới
            </Button>
          ) : (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={fetchRoles}
                loading={loadingRoles}
                icon={RefreshCw}
              >
                Làm mới
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={handleOpenCreateRole}
                icon={Plus}
              >
                Thêm vai trò
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Modernist Navigation Tabs */}
      <div
        style={{
          display: 'flex',
          borderBottom: '2px solid var(--border-subtle)',
          marginBottom: '20px',
          gap: '8px',
        }}
      >
        <button
          onClick={() => setActiveTab('users')}
          style={{
            padding: '10px 18px',
            backgroundColor: activeTab === 'users' ? 'var(--color-primary-900)' : 'transparent',
            color: activeTab === 'users' ? '#FFFFFF' : 'var(--text-main)',
            border: 'none',
            borderRadius: '0px',
            cursor: 'pointer',
            fontSize: '13px',
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          <Users size={15} />
          Tài khoản Người dùng
          {totalElements > 0 && (
            <span
              style={{
                fontSize: '11px',
                padding: '1px 6px',
                backgroundColor: activeTab === 'users' ? 'rgba(255,255,255,0.2)' : 'var(--border-subtle)',
              }}
            >
              {totalElements}
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab('roles')}
          style={{
            padding: '10px 18px',
            backgroundColor: activeTab === 'roles' ? 'var(--color-primary-900)' : 'transparent',
            color: activeTab === 'roles' ? '#FFFFFF' : 'var(--text-main)',
            border: 'none',
            borderRadius: '0px',
            cursor: 'pointer',
            fontSize: '13px',
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          <KeyRound size={15} />
          Phân quyền & Vai trò (RBAC)
          {roles.length > 0 && (
            <span
              style={{
                fontSize: '11px',
                padding: '1px 6px',
                backgroundColor: activeTab === 'roles' ? 'rgba(255,255,255,0.2)' : 'var(--border-subtle)',
              }}
            >
              {roles.length}
            </span>
          )}
        </button>
      </div>

      {/* Tab 1: Users */}
      {activeTab === 'users' && (
        <div>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              fetchUsers(0);
            }}
            style={{
              display: 'flex',
              gap: '10px',
              alignItems: 'flex-end',
              marginBottom: '16px',
              padding: '16px',
              backgroundColor: '#fff',
              border: '1px solid var(--border-subtle)',
              borderRadius: '8px',
            }}
          >
            <div style={{ flex: 1 }}>
              <Input
                label="Tìm kiếm người dùng"
                placeholder="Tên đăng nhập, email, họ tên, số điện thoại..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                icon={Search}
              />
            </div>
            <div style={{ width: '180px' }}>
              <Select
                label="Trạng thái tài khoản"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                options={[
                  { label: 'Tất cả trạng thái', value: '' },
                  { label: 'Kích hoạt (ACTIVE)', value: 'ACTIVE' },
                  { label: 'Tạm khóa (INACTIVE)', value: 'INACTIVE' },
                  { label: 'Cấm tài khoản (Khóa vĩnh viễn)', value: 'BLOCKED' },
                ]}
              />
            </div>
            <div style={{ width: '180px' }}>
              <Select
                label="Lọc theo vai trò"
                value={roleFilter}
                onChange={(e) => setRoleFilter(e.target.value)}
                options={[
                  { label: 'Tất cả vai trò', value: '' },
                  ...roles.map((r) => ({ label: getRoleLabel(r.roleName), value: r.roleName.replace('ROLE_', '') })),
                ]}
              />
            </div>
            <Button type="submit" variant="primary">
              Tìm kiếm
            </Button>
          </form>

          <DataTable
            columns={userColumns}
            data={users}
            loading={loadingUsers}
            emptyMessage="Không có dữ liệu người dùng nào phù hợp."
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={(p) => fetchUsers(p)}
          />
        </div>
      )}

      {/* Tab 2: Roles */}
      {activeTab === 'roles' && (
        <div>
          <div
            style={{
              padding: '14px 18px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              marginBottom: '16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div>
              <strong style={{ fontSize: '13px', color: 'var(--color-primary-900)' }}>
                Chính sách phân quyền RBAC (Role-Based Access Control)
              </strong>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
                Hệ thống áp dụng chuẩn phân quyền Spring Security với tiền tố <code>ROLE_</code>. Các vai trò được gán trực tiếp vào token xác thực JWT để kiểm soát truy cập trên từng Controller.
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <Badge variant="success">Chính sách: Khởi tạo sẵn</Badge>
              <Badge variant="info">Tổng: {roles.length} vai trò</Badge>
            </div>
          </div>

          <DataTable
            columns={roleColumns}
            data={roles}
            loading={loadingRoles}
            emptyMessage="Chưa có vai trò nào được cấu hình."
          />
        </div>
      )}

      {/* User Sessions Modal */}
      <Modal isOpen={sessionsModalOpen} onClose={() => setSessionsModalOpen(false)} title={`Phiên đăng nhập: ${selectedUser?.username || ''}`} maxWidth="700px">
        <DataTable columns={[
          { header: 'ID', accessor: 'id', width: '60px' },
          { header: 'Mã họ phiên (Family ID)', accessor: 'familyId' },
          { header: 'Tạo lúc', accessor: (row) => formatDateTime(row.createdAt) },
          { header: 'Hết hạn', accessor: (row) => formatDateTime(row.expiresAt) },
          {
            header: 'Trạng thái',
            accessor: (row) => <Badge variant={row.revokedAt ? 'danger' : 'success'}>{row.revokedAt ? 'ĐÃ THU HỒI' : 'HOẠT ĐỘNG'}</Badge>,
            width: '120px',
          },
          {
            header: 'Thao tác',
            align: 'right',
            accessor: (row) => !row.revokedAt && (
              <Button
                size="sm"
                variant="danger"
                onClick={() => handleRevokeSession(row.id)}
                icon={XCircle}
                title="Thu hồi phiên đăng nhập này"
              >
                Thu hồi
              </Button>
            ),
          },
        ]} data={sessions} emptyMessage="Người dùng hiện không có phiên đăng nhập nào." />
      </Modal>

      {/* User Status History Modal */}
      <Modal isOpen={historyModalOpen} onClose={() => setHistoryModalOpen(false)} title={`Lịch sử thay đổi trạng thái: ${selectedUser?.username || ''}`} maxWidth="680px">
        {historyLoading ? (
          <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>Đang tải lịch sử...</div>
        ) : (
          <DataTable
            columns={[
              { header: 'Thời điểm', accessor: (row) => formatDateTime(row.createdAt), width: '150px' },
              { header: 'Trạng thái cũ', accessor: (row) => <Badge variant={getUserStatusBadge(row.previousStatus).variant}>{getUserStatusBadge(row.previousStatus).text}</Badge> },
              { header: 'Trạng thái mới', accessor: (row) => <Badge variant={getUserStatusBadge(row.newStatus).variant}>{getUserStatusBadge(row.newStatus).text}</Badge> },
              { header: 'Người thực hiện', accessor: (row) => row.changedBy === 'SYSTEM' ? 'Hệ thống tự động' : (row.changedBy || 'Hệ thống') },
              { header: 'Lý do', accessor: (row) => row.reason || '—' },
            ]}
            data={statusHistoryList}
            emptyMessage="Tài khoản này chưa có lịch sử thay đổi trạng thái nào."
          />
        )}
      </Modal>

      <Modal
        isOpen={rolesModalOpen}
        onClose={() => setRolesModalOpen(false)}
        title={`Phân quyền: ${selectedUser?.username || ''}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setRolesModalOpen(false)}>Hủy</Button>
            <Button variant="primary" onClick={handleAssignRoles} loading={submittingRole} disabled={selectedRoleIds.length === 0}>Lưu quyền</Button>
          </>
        }
      >
        <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px' }}>
          Việc thay đổi vai trò sẽ thu hồi các phiên đăng nhập hiện tại của người dùng.
        </p>
        <div style={{ display: 'grid', gap: '8px' }}>
          {roles.map((role) => (
            <label key={role.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px', border: '1px solid var(--border-subtle)' }}>
              <input
                type="checkbox"
                checked={selectedRoleIds.includes(role.id)}
                onChange={(e) => setSelectedRoleIds((current) => e.target.checked ? [...current, role.id] : current.filter((id) => id !== role.id))}
              />
              <span><strong>{role.roleName}</strong><br /><small>{role.description}</small></span>
            </label>
          ))}
        </div>
      </Modal>

      <Modal
        isOpen={statusModalOpen}
        onClose={() => setStatusModalOpen(false)}
        title={`Điều chỉnh trạng thái tài khoản: ${selectedUser?.username}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setStatusModalOpen(false)}>
              Hủy
            </Button>
            <Button
              variant="primary"
              onClick={handleUpdateStatusSubmit}
              loading={submittingStatus}
            >
              Lưu trạng thái
            </Button>
          </>
        }
      >
        <form onSubmit={handleUpdateStatusSubmit}>
          {statusErrorMessage && (
            <div
              style={{
                padding: '8px 12px',
                backgroundColor: 'var(--color-danger-50)',
                border: '1px solid var(--color-danger-600)',
                color: 'var(--color-danger-700)',
                fontSize: '12px',
                marginBottom: '14px',
              }}
            >
              {statusErrorMessage}
            </div>
          )}

          <div
            style={{
              padding: '10px 12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '12px',
              marginBottom: '16px',
            }}
          >
            Họ tên: <strong>{selectedUser?.fullName || selectedUser?.username}</strong>
            <br />
            Email: {selectedUser?.email}
          </div>

          <Select
            label="Trạng thái tài khoản mới"
            value={targetStatus}
            onChange={(e) => setTargetStatus(e.target.value)}
          >
            <option value="ACTIVE">Kích hoạt bình thường (ACTIVE)</option>
            <option value="INACTIVE">Tạm khóa tài khoản (INACTIVE)</option>
            <option value="BLOCKED">Cấm truy cập vĩnh viễn (BLOCKED)</option>
          </Select>

          {targetStatus !== 'ACTIVE' && (
            <div
              style={{
                padding: '8px 10px',
                backgroundColor: 'var(--color-warning-50)',
                border: '1px solid var(--color-warning-600)',
                fontSize: '12px',
                color: 'var(--color-warning-700)',
                marginTop: '10px',
                marginBottom: '10px',
              }}
            >
              Chú ý: Khi chuyển sang INACTIVE hoặc BLOCKED, hệ thống sẽ tự động hủy toàn bộ phiên làm việc của người dùng này trên toàn bộ các thiết bị.
            </div>
          )}

          <Input
            label="Lý do thay đổi trạng thái (Lưu nhật ký kiểm toán)"
            value={statusReason}
            onChange={(e) => setStatusReason(e.target.value)}
            placeholder="Phát hiện gian lận điểm, yêu cầu từ khách hàng..."
            required
          />
        </form>
      </Modal>

      {/* Role Create / Edit Modal */}
      <Modal
        isOpen={roleModalOpen}
        onClose={() => setRoleModalOpen(false)}
        title={editingRole ? `Chỉnh sửa vai trò: ${editingRole.roleName}` : 'Tạo mới vai trò quyền hạn (Role)'}
        footer={
          <>
            <Button variant="outline" onClick={() => setRoleModalOpen(false)}>
              Hủy
            </Button>
            <Button
              variant="primary"
              onClick={handleRoleSubmit}
              loading={submittingRole}
            >
              {editingRole ? 'Lưu thay đổi' : 'Tạo vai trò'}
            </Button>
          </>
        }
      >
        <form onSubmit={handleRoleSubmit}>
          {roleErrorMessage && (
            <div
              style={{
                padding: '8px 12px',
                backgroundColor: 'var(--color-danger-50)',
                border: '1px solid var(--color-danger-600)',
                color: 'var(--color-danger-700)',
                fontSize: '12px',
                marginBottom: '14px',
              }}
            >
              {roleErrorMessage}
            </div>
          )}

          <Input
            label="Tên vai trò (Định danh hệ thống)"
            value={roleName}
            onChange={(e) => setRoleName(e.target.value)}
            placeholder="ROLE_STAFF, ROLE_USER..."
            required
          />
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '-8px', marginBottom: '12px' }}>
            Hệ thống sẽ tự động thêm tiền tố <code>ROLE_</code> nếu bạn chỉ nhập tên thường.
          </div>

          <Input
            label="Mô tả quyền hạn & Trách nhiệm"
            value={roleDescription}
            onChange={(e) => setRoleDescription(e.target.value)}
            placeholder="Quyền nhập kho, kiểm kê, kiểm duyệt đơn hàng..."
            required
          />
        </form>
      </Modal>

      {/* Reset Password Result Modal */}
      <Modal
        isOpen={resetModalOpen}
        onClose={() => setResetModalOpen(false)}
        title={`Đặt lại mật khẩu: ${resetResult?.username || ''}`}
        maxWidth="500px"
        footer={
          <Button variant="primary" onClick={() => setResetModalOpen(false)}>
            Đã lưu & Đóng
          </Button>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div
            style={{
              padding: '12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '13px',
              borderRadius: '6px',
            }}
          >
            <div>Tài khoản: <strong>{resetResult?.username}</strong></div>
            <div>Email: <strong>{resetResult?.email}</strong></div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
              {resetResult?.message}
            </div>
          </div>

          <div>
            <label className="form-label" style={{ fontWeight: 700 }}>Mật khẩu tạm thời mới:</label>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '6px' }}>
              <div
                style={{
                  flex: 1,
                  padding: '10px 14px',
                  backgroundColor: '#F3F4F6',
                  border: '1px solid var(--border-subtle)',
                  fontFamily: 'monospace',
                  fontSize: '18px',
                  fontWeight: 700,
                  letterSpacing: '0.08em',
                  color: 'var(--color-primary-900)',
                  borderRadius: '4px',
                  userSelect: 'all',
                }}
              >
                {resetResult?.temporaryPassword}
              </div>
              <Button
                variant={copiedPassword ? 'primary' : 'outline'}
                onClick={handleCopyPassword}
                icon={copiedPassword ? Check : Copy}
              >
                {copiedPassword ? 'Đã sao chép' : 'Sao chép'}
              </Button>
            </div>
          </div>

          <div
            style={{
              padding: '10px',
              backgroundColor: 'var(--color-warning-50)',
              border: '1px solid var(--color-warning-600)',
              fontSize: '12px',
              color: 'var(--color-warning-700)',
              borderRadius: '4px',
            }}
          >
            ⚠️ <strong>Lưu ý bảo mật:</strong> Hãy sao chép và gửi mật khẩu tạm này cho người dùng qua kênh liên lạc an toàn. Yêu cầu họ đổi lại mật khẩu cá nhân ngay sau khi đăng nhập thành công.
          </div>
        </div>
      </Modal>
    </div>
  );
};
