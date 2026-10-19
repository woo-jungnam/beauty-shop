import React, { useEffect, useState } from 'react';
import { apiClient } from '../shared/api/client';
import { Modal } from '../shared/ui/Modal';
import { Button } from '../shared/ui/Button';

const LABELS = { ORDERS: 'Đơn hàng và hậu mãi', CATALOG: 'Sản phẩm và khuyến mãi', INVENTORY: 'Kho và kiểm kê', PROCUREMENT: 'Mua hàng và nhà cung cấp', CRM: 'Chăm sóc khách hàng', SPA_RECEPTION: 'Lễ tân và điều phối Spa', SPA_EXECUTION: 'Thực hiện dịch vụ được phân công', REPORTS: 'Báo cáo kinh doanh' };
export function StaffPermissionEditor({ user, onClose, onSaved }) {
  const [selected, setSelected] = useState([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    apiClient.get(`/api/v1/admin/users/${user.id}/permissions`).then(r => { if (active) setSelected(r.data || r || []); })
      .catch(e => { if (active) setError(e.message || 'Không tải được quyền'); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [user.id]);
  const save = async () => {
    setBusy(true); setError('');
    try { await apiClient.put(`/api/v1/admin/users/${user.id}/permissions`, { permissions: selected }); onSaved?.(); onClose(); }
    catch (e) { setError(e.message || 'Không lưu được quyền'); } finally { setBusy(false); }
  };
  return <Modal isOpen onClose={onClose} title={`Quyền công việc: ${user.fullName || user.username}`}>
    <p>Chọn đúng công việc được giao. Lưu thay đổi sẽ thu hồi các phiên đăng nhập cũ của nhân viên.</p>
    {error && <p role="alert" style={{ color: 'var(--color-danger-600, #b91c1c)' }}>{error}</p>}
    <div style={{ display: 'grid', gap: 12, margin: '20px 0' }}>{Object.entries(LABELS).map(([key, label]) => <label key={key} style={{ display: 'flex', gap: 10 }}>
      <input type="checkbox" disabled={busy} checked={selected.includes(key)} onChange={e => setSelected(s => e.target.checked ? [...s, key] : s.filter(v => v !== key))} />{label}
    </label>)}</div>
    <Button disabled={busy} onClick={save}>{busy ? 'Đang xử lý...' : 'Lưu quyền công việc'}</Button>
  </Modal>;
}
