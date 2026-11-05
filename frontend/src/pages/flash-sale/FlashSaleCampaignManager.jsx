import React, { useState, useEffect } from 'react';
import {
  Zap,
  Plus,
  Edit,
  Trash2,
  RefreshCw,
  Clock,
  Boxes,
  Percent,
  CheckCircle2,
  AlertCircle,
  Calendar,
  Search,
  Sliders,
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
} from '../../shared/utils/formatters';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { Badge } from '../../shared/ui/Badge';
import { DataTable } from '../../shared/ui/DataTable';
import { Modal } from '../../shared/ui/Modal';
import { AlertBanner } from '../../shared/ui/AlertBanner';

const STATUS_MAP = {
  ACTIVE: { label: 'Đang diễn ra', variant: 'success' },
  SCHEDULED: { label: 'Đã lên lịch', variant: 'info' },
  PAUSED: { label: 'Tạm dừng', variant: 'warning' },
  ENDED: { label: 'Đã kết thúc', variant: 'default' },
  DRAFT: { label: 'Bản nháp', variant: 'default' },
};

const toLocalDatetimeString = (isoString) => {
  if (!isoString) return '';
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  const year = d.getFullYear();
  const month = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const hours = pad(d.getHours());
  const minutes = pad(d.getMinutes());
  return `${year}-${month}-${day}T${hours}:${minutes}`;
};

export const FlashSaleCampaignManager = () => {
  const [campaigns, setCampaigns] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingCampaign, setEditingCampaign] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');

  // Form states
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [bannerUrl, setBannerUrl] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [status, setStatus] = useState('SCHEDULED');
  const [items, setItems] = useState([]);

  // Catalog variants for SKU picker
  const [catalogVariants, setCatalogVariants] = useState([]);
  const [catalogLoading, setCatalogLoading] = useState(false);

  const fetchCampaigns = async (p = 0) => {
    setLoading(true);
    setFormError('');
    try {
      const res = await apiClient.get(`${ENDPOINTS.FLASH_SALES.ADMIN_LIST}?page=${p}&size=15`);
      const pageData = res?.data ?? res;
      setCampaigns(pageData.content || []);
      setPage(pageData.page ?? p);
      setTotalPages(pageData.totalPages ?? 1);
      setTotalElements(pageData.totalElements ?? 0);
    } catch (err) {
      setFormError(err.message || 'Không thể tải danh sách chiến dịch Flash Sale.');
    } finally {
      setLoading(false);
    }
  };

  const loadCatalogVariants = async () => {
    if (catalogVariants.length > 0) return;
    setCatalogLoading(true);
    try {
      const res = await apiClient.get(`${ENDPOINTS.CATALOG.PRODUCTS}?size=100`);
      const data = res?.data ?? res;
      const prods = data?.content || (Array.isArray(data) ? data : []);

      const vList = [];
      prods.forEach((p) => {
        if (Array.isArray(p.variants)) {
          p.variants.forEach((v) => {
            vList.push({
              variantId: v.id,
              productId: p.id,
              productName: p.name,
              variantName: v.variantName,
              sku: v.sku,
              price: v.price || p.minPrice || 0,
            });
          });
        }
      });
      setCatalogVariants(vList);
    } catch (e) {
      console.warn('Could not pre-fetch catalog variants', e);
    } finally {
      setCatalogLoading(false);
    }
  };

  useEffect(() => {
    fetchCampaigns(0);
  }, []);

  const handleOpenCreate = () => {
    setEditingCampaign(null);
    setName('');
    setDescription('');
    setBannerUrl('');
    const now = new Date();
    const in3Hours = new Date(now.getTime() + 3 * 3600 * 1000);
    setStartsAt(toLocalDatetimeString(now.toISOString()));
    setEndsAt(toLocalDatetimeString(in3Hours.toISOString()));
    setStatus('SCHEDULED');
    setItems([]);
    setFormError('');
    setModalOpen(true);
    loadCatalogVariants();
  };

  const handleOpenEdit = async (campaign) => {
    setEditingCampaign(campaign);
    setName(campaign.name);
    setDescription(campaign.description || '');
    setBannerUrl(campaign.bannerUrl || '');
    setStartsAt(toLocalDatetimeString(campaign.startsAt));
    setEndsAt(toLocalDatetimeString(campaign.endsAt));
    setStatus(campaign.status || 'SCHEDULED');
    setFormError('');
    setModalOpen(true);
    loadCatalogVariants();

    try {
      const res = await apiClient.get(ENDPOINTS.FLASH_SALES.ADMIN_DETAIL(campaign.id));
      const detail = res?.data ?? res;
      if (Array.isArray(detail?.items)) {
        setItems(
          detail.items.map((it) => ({
            productVariantId: it.productVariantId,
            productName: it.productName,
            variantName: it.variantName,
            sku: it.sku,
            originalPrice: it.originalPrice,
            flashPrice: String(it.flashPrice),
            quantityQuota: String(it.quantityQuota),
            userLimit: String(it.userLimit || 2),
            displayOrder: it.displayOrder || 0,
          }))
        );
      }
    } catch {
      setItems([]);
    }
  };

  const handleAddItemRow = () => {
    setItems([
      ...items,
      {
        productVariantId: '',
        productName: '',
        variantName: '',
        sku: '',
        originalPrice: 0,
        flashPrice: '',
        quantityQuota: '20',
        userLimit: '2',
        displayOrder: items.length,
      },
    ]);
  };

  const handleRemoveItemRow = (index) => {
    setItems(items.filter((_, idx) => idx !== index));
  };

  const handleSelectVariant = (index, variantId) => {
    const found = catalogVariants.find((v) => String(v.variantId) === String(variantId));
    setItems((prev) =>
      prev.map((it, idx) => {
        if (idx !== index) return it;
        return {
          ...it,
          productVariantId: variantId,
          productName: found?.productName || '',
          variantName: found?.variantName || '',
          sku: found?.sku || '',
          originalPrice: found?.price || 0,
          flashPrice: found?.price ? String(Math.round(found.price * 0.7)) : '',
        };
      })
    );
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    if (!name.trim()) {
      setFormError('Vui lòng nhập tên chiến dịch Flash Sale');
      return;
    }
    if (!startsAt || !endsAt) {
      setFormError('Vui lòng chọn thời gian bắt đầu và kết thúc');
      return;
    }
    if (new Date(endsAt) <= new Date(startsAt)) {
      setFormError('Thời gian kết thúc phải diễn ra sau thời gian bắt đầu');
      return;
    }
    if (items.length === 0) {
      setFormError('Vui lòng thêm ít nhất một biến thể SKU tham gia Flash Sale');
      return;
    }

    // Validate items
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (!it.productVariantId) {
        setFormError(`Dòng #${i + 1}: Vui lòng chọn biến thể SKU sản phẩm`);
        return;
      }
      const priceNum = parseFloat(it.flashPrice);
      if (isNaN(priceNum) || priceNum <= 0) {
        setFormError(`Dòng #${i + 1}: Giá Flash Sale phải là số dương`);
        return;
      }
      if (it.originalPrice > 0 && priceNum >= it.originalPrice) {
        setFormError(`Dòng #${i + 1}: Giá Flash Sale (${formatCurrency(priceNum)}) phải thấp hơn giá niêm yết gốc (${formatCurrency(it.originalPrice)})`);
        return;
      }
      const quotaNum = parseInt(it.quantityQuota, 10);
      if (isNaN(quotaNum) || quotaNum <= 0) {
        setFormError(`Dòng #${i + 1}: Số suất bán phải lớn hơn 0`);
        return;
      }
    }

    setSubmitting(true);
    try {
      const payload = {
        name: name.trim(),
        description: description.trim() || null,
        bannerUrl: bannerUrl.trim() || null,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        status,
        items: items.map((it, idx) => ({
          productVariantId: Number(it.productVariantId),
          flashPrice: parseFloat(it.flashPrice),
          quantityQuota: parseInt(it.quantityQuota, 10),
          userLimit: it.userLimit ? parseInt(it.userLimit, 10) : 2,
          displayOrder: idx,
        })),
      };

      if (editingCampaign) {
        await apiClient.put(ENDPOINTS.FLASH_SALES.ADMIN_UPDATE(editingCampaign.id), payload);
        setNotice(`Đã cập nhật chiến dịch "${name}" thành công.`);
      } else {
        await apiClient.post(ENDPOINTS.FLASH_SALES.ADMIN_CREATE, payload);
        setNotice(`Đã tạo chiến dịch Flash Sale "${name}" thành công.`);
      }
      setModalOpen(false);
      fetchCampaigns(page);
    } catch (err) {
      setFormError(err.message || 'Lỗi khi lưu chiến dịch Flash Sale');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa chiến dịch Flash Sale #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.FLASH_SALES.ADMIN_DELETE(id));
      setNotice(`Đã xóa chiến dịch #${id}`);
      fetchCampaigns(page);
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa chiến dịch');
    }
  };

  const activeCount = campaigns.filter((c) => c.status === 'ACTIVE').length;
  const totalSoldAll = campaigns.reduce((sum, c) => sum + (c.totalSold || 0), 0);

  const columns = [
    {
      header: 'ID',
      accessor: (row) => (
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
          #{row.id}
        </span>
      ),
      width: '65px',
      align: 'center',
    },
    {
      header: 'Tên chiến dịch Flash Sale',
      accessor: (row) => (
        <div style={{ minWidth: '220px' }}>
          <strong style={{ color: 'var(--text-main)', fontSize: '13.5px' }}>{row.name}</strong>
          {row.description && (
            <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', marginTop: '2px' }}>
              {row.description}
            </div>
          )}
        </div>
      ),
    },
    {
      header: 'Khung giờ áp dụng',
      accessor: (row) => (
        <div style={{ fontSize: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontWeight: 600, color: 'var(--text-main)' }}>
            <Clock size={13} color="var(--color-accent-600)" />
            <span>{formatDateTime(row.startsAt)}</span>
          </div>
          <div style={{ color: 'var(--text-muted)', fontSize: '11px', marginTop: '2px' }}>
            Đến: {formatDateTime(row.endsAt)}
          </div>
        </div>
      ),
      width: '180px',
    },
    {
      header: 'Quy mô SKU',
      accessor: (row) => (
        <span style={{ fontWeight: 650, fontSize: '12.5px' }}>
          {row.totalItems || 0} sản phẩm
        </span>
      ),
      width: '110px',
      align: 'center',
    },
    {
      header: 'Suất bán & Tiến độ',
      accessor: (row) => {
        const sold = row.totalSold || 0;
        const quota = row.totalQuota || 0;
        const percent = quota > 0 ? Math.min(100, Math.round((sold / quota) * 100)) : 0;

        return (
          <div style={{ minWidth: '130px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11.5px', fontWeight: 600, marginBottom: '3px' }}>
              <span>{formatNumber(sold)} / {formatNumber(quota)}</span>
              <span style={{ color: percent >= 80 ? 'var(--color-danger-700)' : 'var(--color-accent-700)' }}>{percent}%</span>
            </div>
            <div style={{ width: '100%', height: '5px', background: 'var(--border-subtle, #eee9f4)', borderRadius: '3px', overflow: 'hidden' }}>
              <div
                style={{
                  width: `${percent}%`,
                  height: '100%',
                  background: percent >= 80 ? 'linear-gradient(90deg, #d35476, #b53d59)' : 'linear-gradient(90deg, #7457e8, #6040ce)',
                  borderRadius: '3px',
                  transition: 'width 0.3s ease',
                }}
              />
            </div>
          </div>
        );
      },
      width: '160px',
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const meta = STATUS_MAP[row.status] || { label: row.status, variant: 'default' };
        return <Badge variant={meta.variant}>{meta.label}</Badge>;
      },
      width: '120px',
      align: 'center',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions" style={{ justifyContent: 'flex-end' }}>
          <Button variant="outline" size="sm" icon={Edit} onClick={() => handleOpenEdit(row)} title="Sửa chiến dịch" />
          <Button variant="outline" size="sm" icon={Trash2} onClick={() => handleDelete(row.id)} title="Xóa chiến dịch" />
        </div>
      ),
      align: 'right',
      width: '100px',
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {notice && (
        <AlertBanner
          type="success"
          title="Thành công"
          message={notice}
          action={<Button size="sm" variant="outline" onClick={() => setNotice('')}>Đóng</Button>}
        />
      )}

      {/* KPI Overview Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '14px' }}>
        <div style={{ background: '#FFFFFF', border: '1px solid var(--border-subtle, #eee9f4)', borderRadius: '12px', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '14px', boxShadow: '0 2px 8px -2px rgba(33, 26, 53, 0.05)' }}>
          <div style={{ width: '42px', height: '42px', borderRadius: '10px', background: 'var(--color-accent-50, #f5f1ff)', color: 'var(--color-accent-600, #7457e8)', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
            <Zap size={20} />
          </div>
          <div>
            <div style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', color: 'var(--text-muted)', fontWeight: 650 }}>Đang mở bán</div>
            <div style={{ fontSize: '20px', fontWeight: 750, color: 'var(--color-success-700, #16766b)' }}>
              {activeCount} <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-muted)' }}>chiến dịch</span>
            </div>
            <div style={{ fontSize: '10.5px', color: 'var(--text-muted)', marginTop: '1px' }}>Tự động kích hoạt theo giờ</div>
          </div>
        </div>

        <div style={{ background: '#FFFFFF', border: '1px solid var(--border-subtle, #eee9f4)', borderRadius: '12px', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '14px', boxShadow: '0 2px 8px -2px rgba(33, 26, 53, 0.05)' }}>
          <div style={{ width: '42px', height: '42px', borderRadius: '10px', background: '#F0FDF4', color: '#16A34A', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
            <Boxes size={20} />
          </div>
          <div>
            <div style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', color: 'var(--text-muted)', fontWeight: 650 }}>Suất Flash Sale đã bán</div>
            <div style={{ fontSize: '20px', fontWeight: 750, color: 'var(--text-main)', fontVariantNumeric: 'tabular-nums' }}>
              {formatNumber(totalSoldAll)} <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-muted)' }}>suất</span>
            </div>
            <div style={{ fontSize: '10.5px', color: 'var(--text-muted)', marginTop: '1px' }}>Khấu trừ quota thời gian thực</div>
          </div>
        </div>

        <div style={{ background: '#FFFFFF', border: '1px solid var(--border-subtle, #eee9f4)', borderRadius: '12px', padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '14px', boxShadow: '0 2px 8px -2px rgba(33, 26, 53, 0.05)' }}>
          <div style={{ width: '42px', height: '42px', borderRadius: '10px', background: '#FFF9ED', color: '#C09750', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
            <Calendar size={20} />
          </div>
          <div>
            <div style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', color: 'var(--text-muted)', fontWeight: 650 }}>Tổng số chiến dịch</div>
            <div style={{ fontSize: '20px', fontWeight: 750, color: 'var(--text-main)' }}>
              {totalElements} <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-muted)' }}>đợt sale</span>
            </div>
            <div style={{ fontSize: '10.5px', color: 'var(--text-muted)', marginTop: '1px' }}>Quản lý & đối soát lịch sử</div>
          </div>
        </div>
      </div>

      {/* Main Campaign Data Table */}
      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <div>
            <span className="card-title">Danh sách các chiến dịch Flash Sale theo khung giờ</span>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)', display: 'block', marginTop: '2px' }}>
              Tự động kích hoạt giá ưu đãi & đồng hồ đếm ngược trên Trang chủ khi đến khung giờ
            </span>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button variant="outline" size="sm" icon={RefreshCw} loading={loading} onClick={() => fetchCampaigns(page)}>
              Làm mới
            </Button>
            <Button variant="primary" size="sm" icon={Plus} onClick={handleOpenCreate}>
              Tạo chiến dịch Flash Sale
            </Button>
          </div>
        </div>

        <DataTable
          columns={columns}
          data={campaigns}
          loading={loading}
          emptyMessage="Chưa có chiến dịch Flash Sale nào. Bấm 'Tạo chiến dịch Flash Sale' để lên lịch khung giờ vàng."
          page={page}
          totalPages={totalPages}
          totalElements={totalElements}
          onPageChange={fetchCampaigns}
        />
      </div>

      {/* Modal Tạo/Sửa Chiến Dịch */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingCampaign ? `Cập nhật Chiến dịch #${editingCampaign.id}` : 'Lên lịch Chiến dịch Flash Sale Mới'}
        maxWidth="980px"
      >
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {formError && (
            <AlertBanner
              type="danger"
              title="Lỗi dữ liệu"
              message={formError}
              action={<Button size="sm" variant="outline" onClick={() => setFormError('')}>Đóng</Button>}
            />
          )}

          <div className="grid-2">
            <Input
              label="Tên chiến dịch Flash Sale *"
              required
              placeholder="VD: Flash Sale Giờ Vàng Buổi Trưa (12h - 14h)"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <Select
              label="Trạng thái chiến dịch"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              options={[
                { value: 'SCHEDULED', label: 'Đã lên lịch (Tự động mở khi đến giờ)' },
                { value: 'ACTIVE', label: 'Đang diễn ra (Kích hoạt ngay)' },
                { value: 'PAUSED', label: 'Tạm dừng khẩn cấp' },
                { value: 'ENDED', label: 'Đã kết thúc' },
                { value: 'DRAFT', label: 'Bản nháp' },
              ]}
            />
          </div>

          <div className="grid-2">
            <Input
              label="Thời gian bắt đầu mở bán *"
              type="datetime-local"
              required
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
            />
            <Input
              label="Thời gian kết thúc *"
              type="datetime-local"
              required
              value={endsAt}
              onChange={(e) => setEndsAt(e.target.value)}
            />
          </div>

          <Input
            label="Mô tả thông điệp đợt sale (tùy chọn)"
            placeholder="VD: Giảm sâu tới 45% tinh chất dưỡng da & kem chống nắng chính hãng"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          {/* Section: Sản phẩm & Biến thể SKU tham gia */}
          <div style={{ marginTop: '10px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle, #eee9f4)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <div>
                <strong style={{ fontSize: '14px', color: 'var(--text-main)' }}>
                  Danh mục Biến thể SKU tham gia Flash Sale ({items.length})
                </strong>
                <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', marginTop: '2px' }}>
                  Giá Flash Sale sẽ tự động áp dụng và trừ vào số suất bán khi khách mua hàng
                </div>
              </div>
              <Button type="button" variant="outline" size="sm" icon={Plus} onClick={handleAddItemRow}>
                Thêm SKU tham gia
              </Button>
            </div>

            {items.length === 0 ? (
              <div style={{ padding: '24px', textAlign: 'center', background: 'var(--bg-surface-subtle, #f6f3fb)', borderRadius: '8px', color: 'var(--text-muted)', fontSize: '12.5px' }}>
                Chưa có biến thể SKU nào. Bấm "Thêm SKU tham gia" để chọn mặt hàng giảm giá.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {items.map((it, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1.6fr 110px 110px 100px 90px 40px',
                      gap: '10px',
                      alignItems: 'center',
                      background: 'var(--bg-surface, #ffffff)',
                      border: '1px solid var(--border-subtle, #eee9f4)',
                      borderRadius: '8px',
                      padding: '10px 12px',
                    }}
                  >
                    <div>
                      <label style={{ fontSize: '10.5px', color: 'var(--text-muted)', display: 'block', marginBottom: '2px' }}>
                        Biến thể SKU #{idx + 1} *
                      </label>
                      <select
                        style={{
                          width: '100%',
                          padding: '6px 8px',
                          borderRadius: '6px',
                          border: '1px solid var(--border-strong, #dcd4e9)',
                          fontSize: '12px',
                          background: '#fff',
                        }}
                        value={it.productVariantId || ''}
                        onChange={(e) => handleSelectVariant(idx, e.target.value)}
                        required
                      >
                        <option value="">-- Chọn sản phẩm & SKU --</option>
                        {catalogVariants.map((v) => (
                          <option key={v.variantId} value={String(v.variantId)}>
                            {v.productName} ({v.variantName}) · SKU: {v.sku} · Gốc: {formatCurrency(v.price)}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label style={{ fontSize: '10.5px', color: 'var(--text-muted)', display: 'block', marginBottom: '2px' }}>
                        Giá gốc niêm yết
                      </label>
                      <div style={{ fontSize: '12.5px', fontWeight: 600, color: 'var(--text-muted)', padding: '6px 0' }}>
                        {formatCurrency(it.originalPrice || 0)}
                      </div>
                    </div>

                    <div>
                      <label style={{ fontSize: '10.5px', color: 'var(--color-accent-700)', fontWeight: 700, display: 'block', marginBottom: '2px' }}>
                        Giá Flash Sale (VNĐ) *
                      </label>
                      <input
                        type="number"
                        min="1000"
                        step="1000"
                        required
                        placeholder="Giá bán sale"
                        style={{
                          width: '100%',
                          padding: '6px 8px',
                          borderRadius: '6px',
                          border: '1px solid var(--color-accent-600, #7457e8)',
                          fontSize: '12px',
                          fontWeight: 700,
                          color: 'var(--color-accent-700)',
                        }}
                        value={it.flashPrice}
                        onChange={(e) => {
                          const val = e.target.value;
                          setItems((prev) => prev.map((row, n) => (n === idx ? { ...row, flashPrice: val } : row)));
                        }}
                      />
                    </div>

                    <div>
                      <label style={{ fontSize: '10.5px', color: 'var(--text-muted)', display: 'block', marginBottom: '2px' }}>
                        Suất bán (Quota) *
                      </label>
                      <input
                        type="number"
                        min="1"
                        required
                        placeholder="Số lượng"
                        style={{
                          width: '100%',
                          padding: '6px 8px',
                          borderRadius: '6px',
                          border: '1px solid var(--border-strong, #dcd4e9)',
                          fontSize: '12px',
                        }}
                        value={it.quantityQuota}
                        onChange={(e) => {
                          const val = e.target.value;
                          setItems((prev) => prev.map((row, n) => (n === idx ? { ...row, quantityQuota: val } : row)));
                        }}
                      />
                    </div>

                    <div>
                      <label style={{ fontSize: '10.5px', color: 'var(--text-muted)', display: 'block', marginBottom: '2px' }}>
                        Max / Khách
                      </label>
                      <input
                        type="number"
                        min="1"
                        placeholder="2"
                        style={{
                          width: '100%',
                          padding: '6px 8px',
                          borderRadius: '6px',
                          border: '1px solid var(--border-strong, #dcd4e9)',
                          fontSize: '12px',
                        }}
                        value={it.userLimit}
                        onChange={(e) => {
                          const val = e.target.value;
                          setItems((prev) => prev.map((row, n) => (n === idx ? { ...row, userLimit: val } : row)));
                        }}
                      />
                    </div>

                    <div style={{ textAlign: 'center', paddingTop: '14px' }}>
                      <button
                        type="button"
                        onClick={() => handleRemoveItemRow(idx)}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--color-danger-700, #b53d59)',
                          cursor: 'pointer',
                          padding: '4px',
                        }}
                        title="Xóa biến thể này khỏi chiến dịch"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle, #eee9f4)' }}>
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              {editingCampaign ? 'Cập nhật chiến dịch' : 'Tạo chiến dịch'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
