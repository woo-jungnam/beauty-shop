import React, { useState, useEffect } from 'react';
import {
  TicketPercent,
  Plus,
  Edit,
  Trash2,
  RefreshCw,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';

export const VouchersPage = () => {
  const [vouchers, setVouchers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  // Modal create/edit state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingVoucher, setEditingVoucher] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');

  // Form fields
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [discountType, setDiscountType] = useState('PERCENTAGE');
  const [discountValue, setDiscountValue] = useState('');
  const [maxDiscountAmount, setMaxDiscountAmount] = useState('');
  const [minOrderAmount, setMinOrderAmount] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [usageLimit, setUsageLimit] = useState('');
  const [perUserLimit, setPerUserLimit] = useState('1');
  const [active, setActive] = useState(true);

  const fetchVouchers = async (p = 0) => {
    setLoading(true);
    try {
      const res = await apiClient.get(`${ENDPOINTS.VOUCHERS.LIST}?page=${p}&size=15`);
      const pageData = res.data || res;
      setVouchers(pageData.content || []);
      setPage(pageData.page ?? p);
      setTotalPages(pageData.totalPages ?? 1);
      setTotalElements(pageData.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVouchers(0);
  }, []);

  const handleOpenCreate = () => {
    setEditingVoucher(null);
    setCode('');
    setName('');
    setDescription('');
    setDiscountType('PERCENTAGE');
    setDiscountValue('');
    setMaxDiscountAmount('');
    setMinOrderAmount('0');
    // Default next 30 days
    const now = new Date();
    const future = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    setStartsAt(now.toISOString().slice(0, 16));
    setEndsAt(future.toISOString().slice(0, 16));
    setUsageLimit('100');
    setPerUserLimit('1');
    setActive(true);
    setFormError('');
    setModalOpen(true);
  };

  const handleOpenEdit = (v) => {
    setEditingVoucher(v);
    setCode(v.code || '');
    setName(v.name || '');
    setDescription(v.description || '');
    setDiscountType(v.discountType || 'PERCENTAGE');
    setDiscountValue(String(v.discountValue || ''));
    setMaxDiscountAmount(v.maxDiscountAmount ? String(v.maxDiscountAmount) : '');
    setMinOrderAmount(v.minOrderAmount ? String(v.minOrderAmount) : '0');
    setStartsAt(v.startsAt ? new Date(v.startsAt).toISOString().slice(0, 16) : '');
    setEndsAt(v.endsAt ? new Date(v.endsAt).toISOString().slice(0, 16) : '');
    setUsageLimit(v.usageLimit ? String(v.usageLimit) : '');
    setPerUserLimit(v.perUserLimit ? String(v.perUserLimit) : '1');
    setActive(v.active ?? true);
    setFormError('');
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setFormError('');

    try {
      const payload = {
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: description.trim(),
        discountType,
        discountValue: parseFloat(discountValue),
        maxDiscountAmount: maxDiscountAmount ? parseFloat(maxDiscountAmount) : null,
        minOrderAmount: minOrderAmount ? parseFloat(minOrderAmount) : 0,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        usageLimit: usageLimit ? parseInt(usageLimit, 10) : null,
        perUserLimit: perUserLimit ? parseInt(perUserLimit, 10) : 1,
        active,
      };

      if (editingVoucher) {
        await apiClient.put(ENDPOINTS.VOUCHERS.UPDATE(editingVoucher.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.VOUCHERS.CREATE, payload);
      }
      setModalOpen(false);
      fetchVouchers(page);
    } catch (err) {
      setFormError(err.message || 'Lỗi lưu thông tin voucher');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa mã khuyến mãi này không?')) return;
    try {
      await apiClient.delete(ENDPOINTS.VOUCHERS.DELETE(id));
      fetchVouchers(page);
    } catch (err) {
      alert(err.message || 'Lỗi xóa voucher');
    }
  };

  const columns = [
    {
      header: 'Mã Voucher',
      accessor: (row) => (
        <div>
          <span
            style={{
              fontFamily: 'var(--font-mono)',
              fontWeight: 700,
              fontSize: '13px',
              backgroundColor: 'var(--color-primary-100)',
              padding: '2px 6px',
            }}
          >
            {row.code}
          </span>
          <div style={{ fontWeight: 600, marginTop: '4px' }}>{row.name}</div>
        </div>
      ),
      width: '180px',
    },
    {
      header: 'Loại & Giá trị giảm',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 700 }}>
            {row.discountType === 'PERCENTAGE'
              ? `${row.discountValue}%`
              : formatCurrency(row.discountValue)}
          </div>
          {row.maxDiscountAmount && (
            <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
              Tối đa: {formatCurrency(row.maxDiscountAmount)}
            </div>
          )}
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            Đơn tối thiểu: {formatCurrency(row.minOrderAmount)}
          </div>
        </div>
      ),
    },
    {
      header: 'Lượt đã dùng',
      accessor: (row) => (
        <div>
          <span style={{ fontWeight: 700 }}>{row.usedCount || 0}</span> /{' '}
          {row.usageLimit ? row.usageLimit : '∞'}
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            Giới hạn/User: {row.perUserLimit || 1}
          </div>
        </div>
      ),
      align: 'center',
    },
    {
      header: 'Hiệu lực',
      accessor: (row) => (
        <div style={{ fontSize: '12px' }}>
          <div>Từ: {formatDateTime(row.startsAt)}</div>
          <div>Đến: {formatDateTime(row.endsAt)}</div>
        </div>
      ),
    },
    {
      header: 'Trạng thái',
      accessor: (row) => (
        <Badge variant={row.active ? 'success' : 'default'}>
          {row.active ? 'ĐANG KÍCH HOẠT' : 'TẠM TẮT'}
        </Badge>
      ),
    },
    {
      header: 'Thao tác',
      align: 'right',
      render: (row) => (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleOpenEdit(row)}
            icon={Edit}
          >
            Sửa
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={() => handleDelete(row.id)}
            icon={Trash2}
          >
            Xóa
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản lý Khuyến mãi & Voucher</h1>
          <p className="page-subtitle">
            Cấu hình mã giảm giá phần trăm, tiền mặt, kiểm soát số lượt dùng và điều kiện đơn hàng
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <Button
            variant="outline"
            size="sm"
            onClick={() => fetchVouchers(page)}
            loading={loading}
            icon={RefreshCw}
          >
            Làm mới
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleOpenCreate}
            icon={Plus}
          >
            Tạo Voucher mới
          </Button>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={vouchers}
        loading={loading}
        emptyMessage="Chưa có mã khuyến mãi nào trong hệ thống."
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => fetchVouchers(p)}
      />

      {/* Create / Edit Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingVoucher ? `Chỉnh sửa Voucher: ${editingVoucher.code}` : 'Tạo mới mã Voucher'}
        maxWidth="620px"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Hủy
            </Button>
            <Button
              variant="primary"
              onClick={handleSubmit}
              loading={submitting}
              icon={TicketPercent}
            >
              Lưu Voucher
            </Button>
          </>
        }
      >
        <form onSubmit={handleSubmit}>
          {formError && (
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
              {formError}
            </div>
          )}

          <div className="grid-2" style={{ gap: '12px' }}>
            <Input
              label="Mã Voucher (Code)"
              placeholder="BEAUTY2026, SUMMER50..."
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
            />
            <Input
              label="Tên chương trình"
              placeholder="Khuyến mãi hè rực rỡ..."
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>

          <Input
            label="Mô tả chi tiết"
            placeholder="Áp dụng cho tất cả mỹ phẩm dưỡng ẩm..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <div className="grid-2" style={{ gap: '12px' }}>
            <Select
              label="Loại giảm giá"
              value={discountType}
              onChange={(e) => setDiscountType(e.target.value)}
            >
              <option value="PERCENTAGE">Theo phần trăm (%)</option>
              <option value="FIXED_AMOUNT">Theo số tiền cố định (VNĐ)</option>
            </Select>

            <Input
              label={discountType === 'PERCENTAGE' ? 'Giá trị % giảm (1-100)' : 'Số tiền giảm (VNĐ)'}
              type="number"
              value={discountValue}
              onChange={(e) => setDiscountValue(e.target.value)}
              required
            />
          </div>

          <div className="grid-2" style={{ gap: '12px' }}>
            <Input
              label="Giảm tối đa (VNĐ, để trống nếu không giới hạn)"
              type="number"
              value={maxDiscountAmount}
              onChange={(e) => setMaxDiscountAmount(e.target.value)}
            />
            <Input
              label="Đơn hàng tối thiểu (VNĐ)"
              type="number"
              value={minOrderAmount}
              onChange={(e) => setMinOrderAmount(e.target.value)}
            />
          </div>

          <div className="grid-2" style={{ gap: '12px' }}>
            <Input
              label="Ngày giờ bắt đầu"
              type="datetime-local"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              required
            />
            <Input
              label="Ngày giờ kết thúc"
              type="datetime-local"
              value={endsAt}
              onChange={(e) => setEndsAt(e.target.value)}
              required
            />
          </div>

          <div className="grid-2" style={{ gap: '12px' }}>
            <Input
              label="Tổng lượt sử dụng tối đa"
              type="number"
              value={usageLimit}
              onChange={(e) => setUsageLimit(e.target.value)}
            />
            <Input
              label="Giới hạn số lần / Mỗi người dùng"
              type="number"
              value={perUserLimit}
              onChange={(e) => setPerUserLimit(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '10px' }}>
            <input
              type="checkbox"
              id="activeToggle"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
              style={{ width: '16px', height: '16px' }}
            />
            <label htmlFor="activeToggle" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
              Kích hoạt cho phép khách hàng áp dụng mã này ngay
            </label>
          </div>
        </form>
      </Modal>
    </div>
  );
};
