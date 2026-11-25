import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Select } from '../../shared/ui/Select';
import { useAuth } from '../../app/providers/AuthProvider';
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatNumber,
  getVietnamDateString,
} from '../../shared/utils/formatters';
import {
  EXPENSE_CATEGORIES,
  calcPercent,
  exclusiveEndDate,
  filterAndSortSpaServices,
  formatSpaDuration,
  getExpenseCategoryLabel,
  isInstantInRange,
  reportParams,
  summarizeProfitability,
  summarizeSpaPerformance,
} from './dashboardFinanceHelpers';
import './dashboard-finance-panels.css';

const extractData = (response) => response?.data ?? response;
const createDocumentRef = () => crypto.randomUUID();

function Pager({ page, pages, totalElements, setPage, disabled }) {
  const safePages = Math.max(1, pages || 1);
  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 10,
        marginTop: 12,
      }}
    >
      <div style={{ fontSize: 12, color: '#4b5563' }}>
        {totalElements != null ? `${formatNumber(totalElements)} bản ghi · ` : ''}
        Trang {page + 1}/{safePages}
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || page <= 0}
          onClick={() => setPage(page - 1)}
        >
          Trước
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={disabled || page + 1 >= safePages}
          onClick={() => setPage(page + 1)}
        >
          Sau
        </Button>
      </div>
    </div>
  );
}

export function ProfitabilityPanel({ dashboardRange, dashboardRefresh } = {}) {
  const { isAdmin } = useAuth();
  const baseKey = `${dashboardRange?.from || ''}|${dashboardRange?.to || ''}`;
  const defaultFrom = dashboardRange?.from || '';
  const defaultTo = dashboardRange?.to ? exclusiveEndDate(dashboardRange.to) : '';
  const [customRange, setCustomRange] = useState(null);
  const from = customRange?.baseKey === baseKey ? customRange.from : defaultFrom;
  const to = customRange?.baseKey === baseKey ? customRange.to : defaultTo;
  const setFrom = useCallback(
    (nextFrom) =>
      setCustomRange((prev) => ({
        baseKey,
        from: nextFrom,
        to: prev?.baseKey === baseKey ? prev.to : defaultTo,
      })),
    [baseKey, defaultTo]
  );
  const setTo = useCallback(
    (nextTo) =>
      setCustomRange((prev) => ({
        baseKey,
        from: prev?.baseKey === baseKey ? prev.from : defaultFrom,
        to: nextTo,
      })),
    [baseKey, defaultFrom]
  );
  const [report, setReport] = useState(null);
  const [expenses, setExpenses] = useState([]);
  const [expensePage, setExpensePage] = useState(0);
  const [expensePages, setExpensePages] = useState(1);
  const [expenseTotal, setExpenseTotal] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [periodOnly, setPeriodOnly] = useState(false);

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saveMessage, setSaveMessage] = useState('');

  const [form, setForm] = useState(() => ({
    reference: createDocumentRef(),
    category: 'OPERATING',
    description: '',
    amount: '',
    incurredAt: getVietnamDateString(),
  }));

  const buildExpenseQuery = useCallback(
    (selectedPage, nextFrom = from, nextTo = to, nextCat = categoryFilter, onlyPeriod = periodOnly) => {
      const params = new URLSearchParams({
        page: String(selectedPage),
        size: '20',
        sort: 'id,desc',
      });
      if (nextCat && nextCat !== 'ALL') params.set('category', nextCat);
      if (onlyPeriod) {
        if (nextFrom) params.set('from', new Date(`${nextFrom}T00:00:00+07:00`).toISOString());
        if (nextTo) params.set('to', new Date(`${nextTo}T00:00:00+07:00`).toISOString());
      }
      return `/api/v1/admin/dashboard/expenses?${params.toString()}`;
    },
    [from, to, categoryFilter, periodOnly]
  );

  const loadExpenses = useCallback(
    async (selectedPage, opts = {}) => {
      const url = buildExpenseQuery(
        selectedPage,
        opts.from ?? from,
        opts.to ?? to,
        opts.category ?? categoryFilter,
        opts.periodOnly ?? periodOnly
      );
      const result = extractData(await apiClient.get(url));
      setExpenses(result?.content || []);
      setExpensePages(Math.max(1, result?.totalPages || 1));
      setExpenseTotal(result?.totalElements ?? (result?.content?.length || 0));
      setExpensePage(selectedPage);
    },
    [buildExpenseQuery, from, to, categoryFilter, periodOnly]
  );

  const loadAll = useCallback(
    async (selectedPage = 0, nextFrom = from, nextTo = to) => {
      setLoading(true);
      setLoadError('');
      try {
        const [reportResponse, expenseResponse] = await Promise.all([
          apiClient.get(`/api/v1/admin/dashboard/profitability?${reportParams(nextFrom, nextTo)}`),
          apiClient.get(buildExpenseQuery(selectedPage, nextFrom, nextTo, categoryFilter, periodOnly)),
        ]);
        setReport(extractData(reportResponse));
        const expenseResult = extractData(expenseResponse);
        setExpenses(expenseResult?.content || []);
        setExpensePages(Math.max(1, expenseResult?.totalPages || 1));
        setExpenseTotal(expenseResult?.totalElements ?? (expenseResult?.content?.length || 0));
        setExpensePage(selectedPage);
      } catch (error) {
        setLoadError(error.message || 'Không thể tải báo cáo lãi gộp và sổ chi phí.');
      } finally {
        setLoading(false);
      }
    },
    [from, to, buildExpenseQuery, categoryFilter, periodOnly]
  );

  useEffect(() => {
    if (!dashboardRange) return undefined;
    const nextFrom = dashboardRange.from || '';
    const nextTo = dashboardRange.to ? exclusiveEndDate(dashboardRange.to) : '';

    const controller = new AbortController();
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return;
      setLoading(true);
      setLoadError('');
    });

    Promise.all([
      apiClient.get(`/api/v1/admin/dashboard/profitability?${reportParams(nextFrom, nextTo)}`, {
        signal: controller.signal,
      }),
      apiClient.get(
        buildExpenseQuery(0, nextFrom, nextTo, categoryFilter, periodOnly),
        { signal: controller.signal }
      ),
    ])
      .then(([reportResponse, expenseResponse]) => {
        if (!active) return;
        setReport(extractData(reportResponse));
        const expenseResult = extractData(expenseResponse);
        setExpenses(expenseResult?.content || []);
        setExpensePages(Math.max(1, expenseResult?.totalPages || 1));
        setExpenseTotal(expenseResult?.totalElements ?? (expenseResult?.content?.length || 0));
        setExpensePage(0);
      })
      .catch((error) => {
        if (active && error?.name !== 'CanceledError') {
          setLoadError(error.message || 'Không thể tải báo cáo lãi gộp và sổ chi phí.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [dashboardRange, dashboardRefresh]); // eslint-disable-line react-hooks/exhaustive-deps

  const summary = useMemo(() => summarizeProfitability(report, expenses), [report, expenses]);

  const handleFieldChange = (key) => (event) => {
    setSaveError('');
    setSaveMessage('');
    setForm((prev) => ({ ...prev, [key]: event.target.value }));
  };

  const handleCreateExpense = async (event) => {
    event.preventDefault();
    setSaving(true);
    setSaveError('');
    setSaveMessage('');
    try {
      await apiClient.post('/api/v1/admin/dashboard/expenses', {
        ...form,
        amount: Number(form.amount),
        incurredAt: new Date(`${form.incurredAt}T00:00:00+07:00`).toISOString(),
      });
      const createdAmount = formatCurrency(form.amount);
      const createdCategory = getExpenseCategoryLabel(form.category);
      setForm({
        reference: createDocumentRef(),
        category: form.category,
        description: '',
        amount: '',
        incurredAt: getVietnamDateString(),
      });
      // Reset period filter if user just logged an expense so it never looks missing in table
      const nextPeriodOnly = periodOnly && !isInstantInRange(`${form.incurredAt}T00:00:00+07:00`, from, to) ? false : periodOnly;
      const nextCategoryFilter = categoryFilter !== 'ALL' && categoryFilter !== form.category ? 'ALL' : categoryFilter;
      if (nextPeriodOnly !== periodOnly) setPeriodOnly(nextPeriodOnly);
      if (nextCategoryFilter !== categoryFilter) setCategoryFilter(nextCategoryFilter);

      const [reportResponse, expenseResponse] = await Promise.all([
        apiClient.get(`/api/v1/admin/dashboard/profitability?${reportParams(from, to)}`),
        apiClient.get(buildExpenseQuery(0, from, to, nextCategoryFilter, nextPeriodOnly)),
      ]);
      setReport(extractData(reportResponse));
      const expenseResult = extractData(expenseResponse);
      setExpenses(expenseResult?.content || []);
      setExpensePages(Math.max(1, expenseResult?.totalPages || 1));
      setExpenseTotal(expenseResult?.totalElements ?? (expenseResult?.content?.length || 0));
      setExpensePage(0);
      setSaveMessage(`Đã ghi nhận phiếu chi ${createdCategory} (${createdAmount}).`);
    } catch (error) {
      setSaveError(error.message || 'Không thể ghi nhận phiếu chi.');
    } finally {
      setSaving(false);
    }
  };

  const handleExpensePageChange = async (nextPage) => {
    setLoading(true);
    setLoadError('');
    try {
      await loadExpenses(nextPage);
    } catch (error) {
      setLoadError(error.message || 'Không thể tải danh sách chi phí.');
    } finally {
      setLoading(false);
    }
  };

  const handleLedgerFilterChange = async (nextCategory, nextPeriodOnly) => {
    setCategoryFilter(nextCategory);
    setPeriodOnly(nextPeriodOnly);
    setLoading(true);
    setLoadError('');
    try {
      await loadExpenses(0, { category: nextCategory, periodOnly: nextPeriodOnly });
    } catch (error) {
      setLoadError(error.message || 'Không thể lọc sổ chi phí.');
    } finally {
      setLoading(false);
    }
  };

  const cogsPercent = summary ? calcPercent(summary.knownCost, summary.netSales) : 0;
  const grossPercent = summary?.grossProfit != null ? calcPercent(summary.grossProfit, summary.netSales) : 0;
  const unknownMarginGapPercent =
    summary && summary.grossProfit == null && summary.netSales > 0 ? Math.max(0, 100 - cogsPercent) : 0;
  const opSharePercent = summary ? calcPercent(summary.operatingExpenses, summary.periodOutflowTotal) : 0;
  const spaConsumeSharePercent = summary ? calcPercent(summary.serviceConsumables, summary.periodOutflowTotal) : 0;

  return (
    <details className="card fin-panel">
      <summary
        className="fin-summary"
        onClick={() => {
          if (!report && !dashboardRange) loadAll(0);
        }}
      >
        <span className="fin-summary-left">
          <span className="fin-summary-chevron" aria-hidden="true">
            ▸
          </span>
          <span>Lãi gộp mỹ phẩm, giá vốn và sổ chi phí</span>
        </span>
        {summary && (
          <span className="fin-summary-pills">
            <span className="fin-pill">Doanh số thuần: {formatCurrency(summary.netSales)}</span>
            <span className="fin-pill fin-pill--strong">
              Lãi gộp:{' '}
              {summary.grossProfit == null
                ? 'Thiếu giá vốn'
                : `${formatCurrency(summary.grossProfit)}${summary.grossMarginPercent != null ? ` (${summary.grossMarginPercent}%)` : ''}`}
            </span>
            <span className="fin-pill">Chi phí kỳ: {formatCurrency(summary.operatingExpenses)}</span>
          </span>
        )}
      </summary>

      <div className="fin-body">
        <p className="fin-basis">
          Biên lợi nhuận tính đúng trên tập đơn mỹ phẩm đã giao thành công trong khoảng chọn, đã trừ phí giao
          hàng và tiền hoàn trả hàng được duyệt hiện tại. Doanh thu Spa, chi phí vận hành, tiêu hao Spa và dòng
          tiền NCC được hạch toán tách biệt theo thời điểm phát sinh.
        </p>

        <form
          className="fin-filter-bar"
          onSubmit={(event) => {
            event.preventDefault();
            loadAll(0);
          }}
        >
          <Input
            label="Giao hàng từ ngày"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
          <Input
            label="Trước ngày (không gồm)"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
          <div className="fin-filter-actions">
            <Button type="submit" loading={loading}>
              Xem báo cáo
            </Button>
            {dashboardRange && (from !== dashboardRange.from || to !== exclusiveEndDate(dashboardRange.to)) && (
              <Button
                type="button"
                variant="outline"
                disabled={loading}
                onClick={() => {
                  const resetFrom = dashboardRange.from || '';
                  const resetTo = dashboardRange.to ? exclusiveEndDate(dashboardRange.to) : '';
                  setFrom(resetFrom);
                  setTo(resetTo);
                  loadAll(0, resetFrom, resetTo);
                }}
              >
                Theo kỳ tổng quan
              </Button>
            )}
          </div>
        </form>

        {loadError && (
          <p role="alert" className="fin-feedback-err">
            {loadError}
          </p>
        )}

        {summary && (
          <>
            {/* Group 1: Cohort Gross Margin KPI Cards */}
            <div className="fin-kpi-grid">
              <div className="fin-kpi-card">
                <span className="fin-kpi-label">Doanh số mỹ phẩm sau trả hàng</span>
                <strong className="fin-kpi-value">{formatCurrency(summary.netSales)}</strong>
                <span className="fin-kpi-sub">Đơn sản phẩm đã giao (không gồm phí ship & Spa)</span>
              </div>

              <div className="fin-kpi-card">
                <span className="fin-kpi-label">Giá vốn có chứng cứ</span>
                <strong className="fin-kpi-value">{formatCurrency(summary.knownCost)}</strong>
                <span className="fin-kpi-sub">
                  {summary.netSales > 0 ? `${summary.costRatioPercent}% doanh số` : 'Theo giá vốn lúc xuất kho'}
                  {summary.missingCostCount > 0 ? ` · ${summary.missingCostCount} phân bổ thiếu giá vốn` : ''}
                </span>
              </div>

              <div className="fin-kpi-card fin-kpi-card--emphasis">
                <span className="fin-kpi-label">Lãi gộp mỹ phẩm</span>
                <strong className="fin-kpi-value">
                  {summary.grossProfit == null ? 'Chưa đủ dữ liệu giá vốn' : formatCurrency(summary.grossProfit)}
                </strong>
                <span className="fin-kpi-sub">
                  {summary.grossMarginPercent != null
                    ? `Biên lãi gộp ${summary.grossMarginPercent}%`
                    : 'Không tự suy diễn từ giá nhập hiện tại'}
                </span>
              </div>

              <div className="fin-kpi-card">
                <span className="fin-kpi-label">Chi phí vận hành trong kỳ</span>
                <strong className="fin-kpi-value">{formatCurrency(summary.operatingExpenses)}</strong>
                <span className="fin-kpi-sub">
                  Tiêu hao Spa kỳ: {formatCurrency(summary.serviceConsumables)}
                </span>
              </div>
            </div>

            {/* Visualization 1 & 2: Product Margin Waterfall Bar + Period Outflows & Supplier Cashflow */}
            <div className="fin-viz-grid">
              <div className="fin-viz-box">
                <div className="fin-viz-header">
                  <h4 className="fin-viz-title">Cấu trúc biên lãi gộp mỹ phẩm</h4>
                  <span className="fin-viz-note">
                    {summary.grossMarginPercent != null
                      ? `Lãi gộp đạt ${summary.grossMarginPercent}%`
                      : 'Tỷ trọng giá vốn đã ghi nhận'}
                  </span>
                </div>

                {summary.netSales > 0 ? (
                  <>
                    <div className="fin-stacked-track" role="img" aria-label="Biểu đồ tỷ trọng giá vốn và lãi gộp">
                      {cogsPercent > 0 && (
                        <div
                          className="fin-seg-mid"
                          style={{ width: `${cogsPercent}%` }}
                          title={`Giá vốn: ${formatCurrency(summary.knownCost)} (${cogsPercent}%)`}
                        />
                      )}
                      {grossPercent > 0 && (
                        <div
                          className="fin-seg-solid"
                          style={{ width: `${grossPercent}%` }}
                          title={`Lãi gộp: ${formatCurrency(summary.grossProfit)} (${grossPercent}%)`}
                        />
                      )}
                      {unknownMarginGapPercent > 0 && (
                        <div
                          className="fin-seg-dashed"
                          style={{ width: `${unknownMarginGapPercent}%` }}
                          title={`Chưa chốt lãi gộp vì thiếu giá vốn lịch sử (${summary.missingCostCount} phân bổ)`}
                        />
                      )}
                    </div>
                    <div className="fin-legend">
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-mid" />
                        Giá vốn: {formatCurrency(summary.knownCost)} ({cogsPercent}%)
                      </span>
                      {summary.grossProfit != null ? (
                        <span className="fin-legend-item">
                          <span className="fin-swatch fin-seg-solid" />
                          Lãi gộp: {formatCurrency(summary.grossProfit)} ({grossPercent}%)
                        </span>
                      ) : (
                        <span className="fin-legend-item">
                          <span className="fin-swatch fin-seg-dashed" />
                          Phần còn lại chưa chốt (thiếu {summary.missingCostCount} giá vốn)
                        </span>
                      )}
                    </div>
                  </>
                ) : (
                  <p className="fin-basis" style={{ margin: 0 }}>
                    Chưa có đơn mỹ phẩm giao thành công trong khoảng thời gian này.
                  </p>
                )}

                <div
                  style={{
                    marginTop: 14,
                    paddingTop: 12,
                    borderTop: '1px solid #f3f4f6',
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                    gap: 10,
                    fontSize: 12,
                  }}
                >
                  <div>
                    <div style={{ color: '#4b5563' }}>Đã trả nhà cung cấp</div>
                    <strong style={{ color: '#111827' }}>{formatCurrency(summary.supplierPayments)}</strong>
                  </div>
                  <div>
                    <div style={{ color: '#4b5563' }}>Thu hồi từ NCC</div>
                    <strong style={{ color: '#111827' }}>{formatCurrency(summary.supplierRecoveries)}</strong>
                  </div>
                  <div>
                    <div style={{ color: '#4b5563' }}>Hoàn tiền chưa gán đơn</div>
                    <strong style={{ color: '#111827' }}>{formatCurrency(summary.unassignedRefunds)}</strong>
                  </div>
                </div>
              </div>

              <div className="fin-viz-box">
                <div className="fin-viz-header">
                  <h4 className="fin-viz-title">Phân bổ chi phí & tiêu hao trong kỳ</h4>
                  <span className="fin-viz-note">
                    Tổng chi ghi nhận: {formatCurrency(summary.periodOutflowTotal)}
                  </span>
                </div>

                {summary.periodOutflowTotal > 0 ? (
                  <>
                    <div className="fin-stacked-track fin-stacked-track--sm">
                      {opSharePercent > 0 && (
                        <div
                          className="fin-seg-solid"
                          style={{ width: `${opSharePercent}%` }}
                          title={`Chi phí vận hành: ${formatCurrency(summary.operatingExpenses)}`}
                        />
                      )}
                      {spaConsumeSharePercent > 0 && (
                        <div
                          className="fin-seg-hatch"
                          style={{ width: `${spaConsumeSharePercent}%` }}
                          title={`Tiêu hao Spa: ${formatCurrency(summary.serviceConsumables)}`}
                        />
                      )}
                    </div>
                    <div className="fin-legend">
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-solid" />
                        Chi phí vận hành: {formatCurrency(summary.operatingExpenses)} ({opSharePercent}%)
                      </span>
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-hatch" />
                        Tiêu hao kho Spa: {formatCurrency(summary.serviceConsumables)} ({spaConsumeSharePercent}%)
                      </span>
                    </div>
                  </>
                ) : (
                  <p className="fin-basis" style={{ margin: 0 }}>
                    Chưa có chi phí vận hành hoặc tiêu hao kho được ghi nhận trong kỳ.
                  </p>
                )}

                {summary.breakdown.length > 0 && (
                  <div className="fin-bar-list" style={{ marginTop: 14 }}>
                    {summary.breakdown.map((cat) => (
                      <div key={cat.category} className="fin-bar-row">
                        <div className="fin-bar-meta">
                          <span>
                            <strong>{cat.label}</strong> ({formatNumber(cat.count)} phiếu)
                          </span>
                          <span>
                            <strong>{formatCurrency(cat.amount)}</strong> · {cat.sharePercent}%
                          </span>
                        </div>
                        <div className="fin-bar-track">
                          <div className="fin-seg-mid" style={{ width: `${cat.sharePercent}%` }} />
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <div className="fin-links" style={{ marginTop: 14 }}>
                  <a className="fin-link-btn" href="#/admin/procurement">
                    Mở nhập hàng & công nợ NCC →
                  </a>
                  <a className="fin-link-btn" href="#/admin/operations?tab=payments">
                    Đối soát hoàn tiền →
                  </a>
                </div>
              </div>
            </div>

            {(summary.missingCostCount > 0 || summary.legacyMissingTime > 0) && (
              <div className="fin-notice" role="status">
                <span>
                  Lưu ý toàn vẹn dữ liệu: có <strong>{summary.missingCostCount}</strong> phân bổ thiếu giá vốn
                  lịch sử và <strong>{summary.legacyMissingTime}</strong> đơn mỹ phẩm cũ thiếu thời điểm giao.
                  Hệ thống giữ nguyên trạng thái thay vì ước lượng từ giá nhập hiện tại.
                </span>
                <div className="fin-links">
                  <a className="fin-link-btn" href="#/admin/orders">
                    Kiểm tra đơn hàng →
                  </a>
                  <a className="fin-link-btn" href="#/admin/inventory">
                    Kiểm tra lô kho →
                  </a>
                </div>
              </div>
            )}
          </>
        )}

        {/* Section 3: Operating Expense Form & Ledger */}
        <div className="fin-section-heading">
          <h4>Sổ chi phí vận hành</h4>
          <span className="fin-viz-note">
            {isAdmin
              ? 'Chỉ quản trị viên được phép ghi nhận phiếu chi mới; mã chứng từ chống trùng tự động.'
              : 'Chế độ xem sổ chi phí (cần quyền Quản trị viên để ghi phiếu chi mới).'}
          </span>
        </div>

        {isAdmin && (
          <form className="fin-form-box" onSubmit={handleCreateExpense}>
            <div className="fin-filter-bar" style={{ marginBottom: 0, alignItems: 'start' }}>
              <div>
                <Input
                  label="Mã phiếu chi"
                  value={form.reference}
                  onChange={handleFieldChange('reference')}
                  required
                  maxLength={100}
                />
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                  <span style={{ fontSize: 11, color: '#4b5563' }}>Mã chứng từ duy nhất</span>
                  <button
                    type="button"
                    onClick={() => {
                      setSaveError('');
                      setSaveMessage('');
                      setForm((prev) => ({ ...prev, reference: createDocumentRef() }));
                    }}
                    style={{
                      border: 'none',
                      background: 'transparent',
                      padding: 0,
                      fontSize: 11,
                      fontWeight: 600,
                      color: '#111827',
                      textDecoration: 'underline',
                      cursor: 'pointer',
                    }}
                  >
                    Tạo mã mới
                  </button>
                </div>
              </div>

              <Select label="Nhóm chi phí" value={form.category} onChange={handleFieldChange('category')}>
                {EXPENSE_CATEGORIES.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </Select>

              <Input
                label="Nội dung"
                placeholder="Ví dụ: Tiền mặt bằng tháng 11, chạy quảng cáo..."
                value={form.description}
                onChange={handleFieldChange('description')}
                required
                maxLength={500}
              />

              <Input
                label="Số tiền (VND)"
                type="number"
                min="1"
                step="1"
                placeholder="500000"
                value={form.amount}
                onChange={handleFieldChange('amount')}
                required
              />

              <Input
                label="Ngày chi"
                type="date"
                value={form.incurredAt}
                onChange={handleFieldChange('incurredAt')}
                required
              />

              <div style={{ paddingTop: 22 }}>
                <Button type="submit" loading={saving}>
                  Ghi phiếu chi
                </Button>
              </div>
            </div>

            {saveError && (
              <p role="alert" className="fin-feedback-err">
                {saveError}
              </p>
            )}
            {saveMessage && (
              <p role="status" className="fin-feedback-ok">
                {saveMessage}
              </p>
            )}
          </form>
        )}

        <div className="fin-table-controls">
          <div style={{ minWidth: 200 }}>
            <Select
              label="Lọc nhóm chi phí"
              value={categoryFilter}
              onChange={(event) => handleLedgerFilterChange(event.target.value, periodOnly)}
            >
              <option value="ALL">Tất cả nhóm chi phí</option>
              {EXPENSE_CATEGORIES.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </Select>
          </div>
          <label
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 13,
              fontWeight: 500,
              color: '#111827',
              cursor: 'pointer',
              marginTop: 18,
            }}
          >
            <input
              type="checkbox"
              checked={periodOnly}
              onChange={(event) => handleLedgerFilterChange(categoryFilter, event.target.checked)}
            />
            Chỉ hiện phiếu chi có ngày chi trong khoảng đang xem
          </label>
        </div>

        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Chứng từ</th>
                <th>Nhóm chi phí</th>
                <th>Nội dung</th>
                <th>Ngày chi</th>
                <th>Ghi nhận lúc</th>
                <th className="fin-num">Số tiền</th>
              </tr>
            </thead>
            <tbody>
              {expenses.length === 0 ? (
                <tr>
                  <td colSpan={6} className="fin-empty-row">
                    {categoryFilter !== 'ALL' || periodOnly
                      ? 'Không có phiếu chi nào khớp bộ lọc đang chọn.'
                      : 'Chưa có phiếu chi vận hành nào được ghi nhận. Bạn có thể tạo phiếu chi ở biểu mẫu phía trên.'}
                  </td>
                </tr>
              ) : (
                expenses.map((expense) => {
                  const inSelectedPeriod = isInstantInRange(expense.incurredAt, from, to);
                  return (
                    <tr key={expense.id}>
                      <td className="fin-ref-cell" title={expense.reference}>
                        {String(expense.reference || '').length > 18
                          ? `${String(expense.reference).slice(0, 14)}…`
                          : expense.reference}
                      </td>
                      <td>
                        <span className="fin-tag">{getExpenseCategoryLabel(expense.category)}</span>
                      </td>
                      <td style={{ color: '#111827' }}>{expense.description}</td>
                      <td style={{ whiteSpace: 'nowrap', color: '#111827' }}>
                        {formatDate(expense.incurredAt)}{' '}
                        {(from || to) && !periodOnly && (
                          <span className="fin-tag" style={{ marginLeft: 4 }}>
                            {inSelectedPeriod ? 'Trong kỳ' : 'Ngoài kỳ'}
                          </span>
                        )}
                      </td>
                      <td style={{ whiteSpace: 'nowrap', color: '#4b5563', fontSize: 12 }}>
                        {formatDateTime(expense.createdAt)}
                      </td>
                      <td className="fin-num">
                        <strong>{formatCurrency(expense.amount)}</strong>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <Pager
          page={expensePage}
          pages={expensePages}
          totalElements={expenseTotal}
          setPage={handleExpensePageChange}
          disabled={loading}
        />
      </div>
    </details>
  );
}

export function SpaPerformancePanel({ dashboardRange, dashboardRefresh } = {}) {
  const baseKey = `${dashboardRange?.from || ''}|${dashboardRange?.to || ''}`;
  const defaultFrom = dashboardRange?.from || '';
  const defaultTo = dashboardRange?.to ? exclusiveEndDate(dashboardRange.to) : '';
  const [customRange, setCustomRange] = useState(null);
  const from = customRange?.baseKey === baseKey ? customRange.from : defaultFrom;
  const to = customRange?.baseKey === baseKey ? customRange.to : defaultTo;
  const setFrom = useCallback(
    (nextFrom) =>
      setCustomRange((prev) => ({
        baseKey,
        from: nextFrom,
        to: prev?.baseKey === baseKey ? prev.to : defaultTo,
      })),
    [baseKey, defaultTo]
  );
  const setTo = useCallback(
    (nextTo) =>
      setCustomRange((prev) => ({
        baseKey,
        from: prev?.baseKey === baseKey ? prev.from : defaultFrom,
        to: nextTo,
      })),
    [baseKey, defaultFrom]
  );
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');

  const [searchQuery, setSearchQuery] = useState('');
  const [evidenceFilter, setEvidenceFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('REVENUE');

  const loadReport = useCallback(
    async (nextFrom = from, nextTo = to) => {
      setLoading(true);
      setLoadError('');
      try {
        const response = await apiClient.get(
          `/api/v1/admin/dashboard/spa-performance?${reportParams(nextFrom, nextTo)}`
        );
        setReport(extractData(response));
      } catch (error) {
        setLoadError(error.message || 'Không thể tải báo cáo hiệu quả dịch vụ Spa.');
      } finally {
        setLoading(false);
      }
    },
    [from, to]
  );

  useEffect(() => {
    if (!dashboardRange) return undefined;
    const nextFrom = dashboardRange.from || '';
    const nextTo = dashboardRange.to ? exclusiveEndDate(dashboardRange.to) : '';

    const controller = new AbortController();
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return;
      setLoading(true);
      setLoadError('');
    });

    apiClient
      .get(`/api/v1/admin/dashboard/spa-performance?${reportParams(nextFrom, nextTo)}`, {
        signal: controller.signal,
      })
      .then((response) => {
        if (active) setReport(extractData(response));
      })
      .catch((error) => {
        if (active && error?.name !== 'CanceledError') {
          setLoadError(error.message || 'Không thể tải báo cáo hiệu quả dịch vụ Spa.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [dashboardRange, dashboardRefresh]);

  const summary = useMemo(() => summarizeSpaPerformance(report), [report]);
  const visibleServices = useMemo(
    () =>
      filterAndSortSpaServices(report?.services || [], {
        query: searchQuery,
        evidenceFilter,
        sortBy,
      }),
    [report, searchQuery, evidenceFilter, sortBy]
  );

  const chartServices = useMemo(() => {
    if (!report?.services?.length) return [];
    return filterAndSortSpaServices(report.services, { sortBy: 'REVENUE' }).slice(0, 8);
  }, [report]);

  const visitShare = summary ? calcPercent(summary.visitInvoiceRevenue, summary.earnedRevenue) : 0;
  const ordinaryShare = summary ? calcPercent(summary.ordinaryPackageRevenue, summary.earnedRevenue) : 0;
  const treatmentShare = summary ? calcPercent(summary.treatmentRevenue, summary.earnedRevenue) : 0;

  return (
    <details className="card fin-panel">
      <summary
        className="fin-summary"
        onClick={() => {
          if (!report && !dashboardRange) loadReport();
        }}
      >
        <span className="fin-summary-left">
          <span className="fin-summary-chevron" aria-hidden="true">
            ▸
          </span>
          <span>Doanh thu đã thực hiện và vật tư theo dịch vụ Spa</span>
        </span>
        {summary && (
          <span className="fin-summary-pills">
            <span className="fin-pill fin-pill--strong">
              Thực hiện: {formatCurrency(summary.earnedRevenue)} ({formatNumber(summary.performedItems)} lượt)
            </span>
            <span className="fin-pill">Vật tư: {formatCurrency(summary.recordedMaterialCost)}</span>
            {summary.unbilledItems > 0 && (
              <span className="fin-pill">
                Chưa hóa đơn: {formatNumber(summary.unbilledItems)} mục ({formatCurrency(summary.unbilledSnapshotValue)})
              </span>
            )}
          </span>
        )}
      </summary>

      <div className="fin-body">
        <p className="fin-basis">
          Chỉ tính các mục dịch vụ đã hoàn tất thực tế theo giờ hoàn thành (<code>actual_completed_at</code>). Doanh
          thu được tách theo chứng từ thật từ 3 nguồn: hóa đơn buổi lẻ, quyền lợi gói thường và sổ thực hiện liệu
          trình. Khoản mất quyền lợi do vắng hẹn không tính vào doanh thu đã thực hiện.
        </p>

        <form
          className="fin-filter-bar"
          onSubmit={(event) => {
            event.preventDefault();
            loadReport();
          }}
        >
          <Input
            label="Hoàn thành từ ngày"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
          <Input
            label="Trước ngày (không gồm)"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
          <div className="fin-filter-actions">
            <Button type="submit" loading={loading}>
              Xem hiệu quả dịch vụ
            </Button>
            {dashboardRange && (from !== dashboardRange.from || to !== exclusiveEndDate(dashboardRange.to)) && (
              <Button
                type="button"
                variant="outline"
                disabled={loading}
                onClick={() => {
                  const resetFrom = dashboardRange.from || '';
                  const resetTo = dashboardRange.to ? exclusiveEndDate(dashboardRange.to) : '';
                  setFrom(resetFrom);
                  setTo(resetTo);
                  loadReport(resetFrom, resetTo);
                }}
              >
                Theo kỳ tổng quan
              </Button>
            )}
          </div>
        </form>

        {loadError && (
          <p role="alert" className="fin-feedback-err">
            {loadError}
          </p>
        )}

        {summary && (
          <>
            {/* Top Summary KPI Cards */}
            <div className="fin-kpi-grid">
              <div className="fin-kpi-card fin-kpi-card--emphasis">
                <span className="fin-kpi-label">Tổng doanh thu đã thực hiện</span>
                <strong className="fin-kpi-value">{formatCurrency(summary.earnedRevenue)}</strong>
                <span className="fin-kpi-sub">
                  {formatNumber(summary.performedItems)} lượt · {formatSpaDuration(summary.performedItems, summary.performedMinutes)}
                </span>
              </div>

              <div className="fin-kpi-card">
                <span className="fin-kpi-label">Cơ cấu nguồn doanh thu</span>
                <strong className="fin-kpi-value">{formatNumber(summary.serviceCount)} dịch vụ</strong>
                <span className="fin-kpi-sub">
                  Lẻ: {formatCurrency(summary.visitInvoiceRevenue)} · Gói: {formatCurrency(summary.ordinaryPackageRevenue)} · Liệu
                  trình: {formatCurrency(summary.treatmentRevenue)}
                </span>
              </div>

              <div className="fin-kpi-card">
                <span className="fin-kpi-label">Vật tư thực tế đã ghi nhận</span>
                <strong className="fin-kpi-value">{formatCurrency(summary.recordedMaterialCost)}</strong>
                <span className="fin-kpi-sub">
                  {summary.earnedRevenue > 0 ? `${summary.materialRatioPercent}% doanh thu thực hiện · ` : ''}
                  {summary.sufficientServiceCount}/{summary.serviceCount} dịch vụ đủ chứng cứ
                </span>
              </div>

              <div className="fin-kpi-card">
                <span className="fin-kpi-label">Mục thiếu chứng cứ / chưa hóa đơn</span>
                <strong className="fin-kpi-value">{formatNumber(summary.evidenceGapCount)} mục</strong>
                <span className="fin-kpi-sub">
                  {summary.unbilledItems > 0
                    ? `${summary.unbilledItems} chưa hóa đơn (${formatCurrency(summary.unbilledSnapshotValue)})`
                    : `${summary.untrackedMaterialItems + summary.missingPlannedMaterialItems} thiếu theo dõi vật tư`}
                </span>
              </div>
            </div>

            {/* Visualization Chart Grid */}
            <div className="fin-viz-grid">
              <div className="fin-viz-box">
                <div className="fin-viz-header">
                  <h4 className="fin-viz-title">Tỷ trọng nguồn thu Spa đã thực hiện</h4>
                  <span className="fin-viz-note">Tổng: {formatCurrency(summary.earnedRevenue)}</span>
                </div>

                {summary.earnedRevenue > 0 ? (
                  <>
                    <div className="fin-stacked-track">
                      {visitShare > 0 && (
                        <div
                          className="fin-seg-solid"
                          style={{ width: `${visitShare}%` }}
                          title={`Buổi lẻ đã lập hóa đơn: ${formatCurrency(summary.visitInvoiceRevenue)} (${visitShare}%)`}
                        />
                      )}
                      {ordinaryShare > 0 && (
                        <div
                          className="fin-seg-mid"
                          style={{ width: `${ordinaryShare}%` }}
                          title={`Gói thường: ${formatCurrency(summary.ordinaryPackageRevenue)} (${ordinaryShare}%)`}
                        />
                      )}
                      {treatmentShare > 0 && (
                        <div
                          className="fin-seg-hatch"
                          style={{ width: `${treatmentShare}%` }}
                          title={`Liệu trình đã thực hiện: ${formatCurrency(summary.treatmentRevenue)} (${treatmentShare}%)`}
                        />
                      )}
                    </div>
                    <div className="fin-legend">
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-solid" />
                        Buổi lẻ: {formatCurrency(summary.visitInvoiceRevenue)} ({visitShare}%)
                      </span>
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-mid" />
                        Gói thường: {formatCurrency(summary.ordinaryPackageRevenue)} ({ordinaryShare}%)
                      </span>
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-hatch" />
                        Liệu trình: {formatCurrency(summary.treatmentRevenue)} ({treatmentShare}%)
                      </span>
                    </div>
                  </>
                ) : (
                  <p className="fin-basis" style={{ margin: 0 }}>
                    Chưa có doanh thu Spa đã ghi nhận trong khoảng thời gian này.
                  </p>
                )}

                <div className="fin-links" style={{ marginTop: 14 }}>
                  <a className="fin-link-btn" href="#/admin/spa?tab=services">
                    Cấu hình định mức vật tư dịch vụ →
                  </a>
                  <a className="fin-link-btn" href="#/admin/spa-reception">
                    Quầy thu ngân & lập hóa đơn →
                  </a>
                </div>
              </div>

              <div className="fin-viz-box">
                <div className="fin-viz-header">
                  <h4 className="fin-viz-title">Biểu đồ dịch vụ dẫn đầu (thực hiện vs chưa hóa đơn)</h4>
                  <span className="fin-viz-note">Tối đa 8 dịch vụ đầu</span>
                </div>

                {chartServices.length > 0 && summary.maxScaleValue > 0 ? (
                  <>
                    <div className="fin-bar-list">
                      {chartServices.map((row) => {
                        const rowScaleBase = summary.maxScaleValue;
                        const wVisit = calcPercent(row.visitInvoiceRevenue, rowScaleBase);
                        const wPkg = calcPercent(row.ordinaryPackageRevenue, rowScaleBase);
                        const wTreat = calcPercent(row.treatmentRevenue, rowScaleBase);
                        const wUnbilled = calcPercent(row.unbilledSnapshotValue, rowScaleBase);
                        const rowGaps =
                          Number(row.unbilledItems || 0) +
                          Number(row.missingRevenueItems || 0) +
                          Number(row.missingPlannedMaterialItems || 0) +
                          Number(row.untrackedMaterialItems || 0);
                        return (
                          <div key={row.serviceId} className="fin-bar-row">
                            <div className="fin-bar-meta">
                              <span style={{ fontWeight: 600 }}>
                                {row.serviceName} ({row.performedItems} lượt)
                              </span>
                              <span>
                                <strong>{formatCurrency(row.earnedRevenue)}</strong>
                                {Number(row.unbilledSnapshotValue || 0) > 0 &&
                                  ` + ${formatCurrency(row.unbilledSnapshotValue)} chờ HĐ`}
                                {rowGaps > 0 && ' · Thiếu chứng cứ'}
                              </span>
                            </div>
                            <div className="fin-stacked-track fin-stacked-track--sm">
                              {wVisit > 0 && <div className="fin-seg-solid" style={{ width: `${wVisit}%` }} />}
                              {wPkg > 0 && <div className="fin-seg-mid" style={{ width: `${wPkg}%` }} />}
                              {wTreat > 0 && <div className="fin-seg-hatch" style={{ width: `${wTreat}%` }} />}
                              {wUnbilled > 0 && (
                                <div
                                  className="fin-seg-dashed"
                                  style={{ width: `${wUnbilled}%` }}
                                  title={`Giá trị tham chiếu chưa lập hóa đơn: ${formatCurrency(row.unbilledSnapshotValue)}`}
                                />
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                    <div className="fin-legend">
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-solid" />
                        Buổi lẻ
                      </span>
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-mid" />
                        Gói thường
                      </span>
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-hatch" />
                        Liệu trình
                      </span>
                      <span className="fin-legend-item">
                        <span className="fin-swatch fin-seg-dashed" />
                        Chưa lập hóa đơn
                      </span>
                    </div>
                  </>
                ) : (
                  <p className="fin-basis" style={{ margin: 0 }}>
                    Chưa có lượt dịch vụ Spa hoàn thành trong khoảng thời gian đã chọn.
                  </p>
                )}
              </div>
            </div>

            {summary.incompleteServiceCount > 0 && (
              <div className="fin-notice" role="status">
                <span>
                  Có <strong>{summary.incompleteServiceCount}</strong> dịch vụ bị khóa mục &ldquo;Chênh lệch sau
                  vật tư&rdquo; thành <strong>Chưa đủ dữ liệu</strong> vì có lượt thực hiện chưa ghi nhận định
                  mức vật tư, chưa có lý do không dùng vật tư, hoặc chưa lập hóa đơn buổi lẻ.
                </span>
                <div className="fin-links">
                  <a className="fin-link-btn" href="#/admin/spa?tab=appointments">
                    Xem lịch hẹn đã thực hiện →
                  </a>
                </div>
              </div>
            )}

            {/* Table Filters */}
            <div className="fin-table-controls">
              <div style={{ minWidth: 220, flex: '1 1 220px' }}>
                <Input
                  label="Tìm theo tên dịch vụ"
                  placeholder="Nhập tên dịch vụ Spa..."
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                />
              </div>
              <div style={{ minWidth: 190 }}>
                <Select
                  label="Tình trạng chứng cứ"
                  value={evidenceFilter}
                  onChange={(event) => setEvidenceFilter(event.target.value)}
                >
                  <option value="ALL">Tất cả dịch vụ ({summary.serviceCount})</option>
                  <option value="GAPS">Thiếu chứng cứ ({summary.incompleteServiceCount})</option>
                  <option value="COMPLETE">Đủ chứng cứ ({summary.sufficientServiceCount})</option>
                </Select>
              </div>
              <div style={{ minWidth: 190 }}>
                <Select label="Sắp xếp" value={sortBy} onChange={(event) => setSortBy(event.target.value)}>
                  <option value="REVENUE">Doanh thu giảm dần</option>
                  <option value="ITEMS">Số lượt giảm dần</option>
                  <option value="GAPS">Thiếu chứng cứ ưu tiên</option>
                </Select>
              </div>
            </div>

            <p className="fin-basis">
              Gói thường phân bổ giá trị gốc đều theo số lượt quyền lợi lúc mua và chặn theo thực thu sau hoàn
              tiền. Chênh lệch sau vật tư chỉ trừ vật tư thực tế gắn từng lượt, chưa trừ lương, hoa hồng, khấu
              hao máy và chi phí chung.
            </p>

            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Dịch vụ</th>
                    <th>Lượt / thời lượng</th>
                    <th className="fin-num">Buổi lẻ đã hóa đơn</th>
                    <th className="fin-num">Gói thường</th>
                    <th className="fin-num">Liệu trình thực hiện</th>
                    <th className="fin-num">Tổng thực hiện</th>
                    <th className="fin-num">Vật tư thực tế</th>
                    <th className="fin-num">Chênh lệch sau vật tư</th>
                    <th>Thiếu chứng cứ & ghi chú</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleServices.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="fin-empty-row">
                        {report?.services?.length
                          ? 'Không có dịch vụ nào khớp điều kiện lọc đang chọn.'
                          : 'Chưa có lượt dịch vụ Spa nào hoàn tất trong khoảng thời gian này.'}
                      </td>
                    </tr>
                  ) : (
                    visibleServices.map((row) => {
                      const rowShare = calcPercent(row.earnedRevenue, summary.earnedRevenue);
                      const hasGaps =
                        Number(row.unbilledItems || 0) +
                          Number(row.missingRevenueItems || 0) +
                          Number(row.missingPlannedMaterialItems || 0) +
                          Number(row.untrackedMaterialItems || 0) >
                        0;
                      const marginPercent =
                        row.contributionAfterRecordedMaterials != null && Number(row.earnedRevenue || 0) > 0
                          ? calcPercent(row.contributionAfterRecordedMaterials, row.earnedRevenue)
                          : null;

                      return (
                        <tr key={row.serviceId}>
                          <td>
                            <div style={{ fontWeight: 600, color: '#111827' }}>{row.serviceName}</div>
                            {summary.earnedRevenue > 0 && (
                              <div style={{ fontSize: 11, color: '#4b5563', marginTop: 2 }}>
                                Đóng góp {rowShare}% doanh thu thực hiện
                              </div>
                            )}
                          </td>
                          <td>
                            <div style={{ fontWeight: 600, color: '#111827' }}>{row.performedItems} lượt</div>
                            <div
                              style={{ fontSize: 12, color: '#4b5563' }}
                              title={
                                Number(row.performedItems) > 0 && Number(row.performedMinutes) === 0
                                  ? 'Thời gian từ lúc bấm Bắt đầu đến Hoàn tất dưới 1 phút'
                                  : undefined
                              }
                            >
                              {formatSpaDuration(row.performedItems, row.performedMinutes)}
                            </div>
                          </td>
                          <td className="fin-num">{formatCurrency(row.visitInvoiceRevenue)}</td>
                          <td className="fin-num">{formatCurrency(row.ordinaryPackageRevenue)}</td>
                          <td className="fin-num">{formatCurrency(row.treatmentRevenue)}</td>
                          <td className="fin-num">
                            <strong>{formatCurrency(row.earnedRevenue)}</strong>
                          </td>
                          <td className="fin-num">{formatCurrency(row.recordedMaterialCost)}</td>
                          <td className="fin-num">
                            {row.contributionAfterRecordedMaterials == null ? (
                              <span className="fin-tag">Chưa đủ dữ liệu</span>
                            ) : (
                              <div>
                                <strong>{formatCurrency(row.contributionAfterRecordedMaterials)}</strong>
                                {marginPercent != null && (
                                  <div style={{ fontSize: 11, color: '#4b5563' }}>{marginPercent}%</div>
                                )}
                              </div>
                            )}
                          </td>
                          <td>
                            <div className="fin-spa-evidence-list">
                              {!hasGaps && Number(row.materialWaiverItems || 0) === 0 && (
                                <span className="fin-tag">Đủ chứng cứ</span>
                              )}
                              {row.unbilledItems > 0 && (
                                <div>
                                  <span className="fin-tag">Chưa hóa đơn</span> {row.unbilledItems} mục ·{' '}
                                  {formatCurrency(row.unbilledSnapshotValue)}
                                </div>
                              )}
                              {row.missingRevenueItems > 0 && (
                                <div>
                                  <span className="fin-tag">Thiếu giá trị gói/LT</span> {row.missingRevenueItems}{' '}
                                  mục
                                </div>
                              )}
                              {row.missingPlannedMaterialItems > 0 && (
                                <div>
                                  <span className="fin-tag">Thiếu vật tư KH</span>{' '}
                                  {row.missingPlannedMaterialItems} mục
                                </div>
                              )}
                              {row.untrackedMaterialItems > 0 && (
                                <div>
                                  <span className="fin-tag">Chưa theo dõi vật tư</span>{' '}
                                  {row.untrackedMaterialItems} mục
                                </div>
                              )}
                              {row.materialWaiverItems > 0 && (
                                <div>{row.materialWaiverItems} mục đã ghi lý do không dùng vật tư</div>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
                {visibleServices.length > 0 && (
                  <tfoot>
                    <tr className="fin-tfoot-row">
                      <td>Tổng cộng ({summary.serviceCount} dịch vụ)</td>
                      <td>
                        {formatNumber(summary.performedItems)} lượt · {formatNumber(summary.performedMinutes)} phút
                      </td>
                      <td className="fin-num">{formatCurrency(summary.visitInvoiceRevenue)}</td>
                      <td className="fin-num">{formatCurrency(summary.ordinaryPackageRevenue)}</td>
                      <td className="fin-num">{formatCurrency(summary.treatmentRevenue)}</td>
                      <td className="fin-num">{formatCurrency(summary.earnedRevenue)}</td>
                      <td className="fin-num">{formatCurrency(summary.recordedMaterialCost)}</td>
                      <td className="fin-num">
                        {summary.sufficientServiceCount > 0
                          ? formatCurrency(summary.contributionKnownTotal)
                          : 'Chưa đủ dữ liệu'}
                      </td>
                      <td>
                        {summary.evidenceGapCount > 0
                          ? `${formatNumber(summary.evidenceGapCount)} mục thiếu chứng cứ`
                          : 'Tất cả dịch vụ đủ chứng cứ'}
                      </td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>

            <p className="fin-basis" style={{ marginTop: 12, marginBottom: 0 }}>
              Tính toàn vẹn lịch sử: <strong>{formatNumber(report.performedItemsMissingTime)}</strong> mục đã thực
              hiện thiếu giờ hoàn thành (<code>actual_completed_at</code>) và{' '}
              <strong>{formatNumber(report.excludedLegacyItems)}</strong> mục cũ trạng thái{' '}
              <code>LEGACY_FINALIZED</code> không có chứng cứ thực hiện được tách riêng khỏi báo cáo này.
            </p>
          </>
        )}
      </div>
    </details>
  );
}
