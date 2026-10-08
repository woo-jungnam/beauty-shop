import React, { useEffect, useId, useMemo, useRef, useState } from "react";
import {
  ArrowDownToLine,
  ArrowRight,
  ArrowUpRight,
  Banknote,
  CalendarDays,
  Check,
  CircleAlert,
  Clock3,
  CreditCard,
  Flower2,
  Package,
  RefreshCw,
  ShoppingBag,
  Sparkles,
  Users,
  Wallet,
} from "lucide-react";
import { apiClient } from "../shared/api/client";
import { ENDPOINTS } from "../shared/api/endpoints";
import {
  formatCurrency,
  formatNumber,
  formatDate,
} from "../shared/utils/formatters";
import { Button } from "../shared/ui/Button";
import { DataTable } from "../shared/ui/DataTable";
import { Modal } from "../shared/ui/Modal";
import { useAuth } from "../app/providers/AuthProvider";

const BUSINESS_ZONE = "Asia/Ho_Chi_Minh";
const dayFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: BUSINESS_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});
const numberOrNull = (value) =>
  value !== null &&
  value !== undefined &&
  value !== "" &&
  Number.isFinite(Number(value))
    ? Number(value)
    : null;
const currency = (value) =>
  numberOrNull(value) === null ? "—" : formatCurrency(Number(value));
const number = (value) =>
  numberOrNull(value) === null ? "—" : formatNumber(Number(value));
const unwrap = (response) =>
  response && Object.prototype.hasOwnProperty.call(response, "data")
    ? response.data
    : response;
const orderCode = (order) =>
  order.orderCode || order.orderNumber || `#${order.id}`;
const dateKey = (date = new Date()) => {
  const parts = Object.fromEntries(
    dayFormatter.formatToParts(date).map(({ type, value }) => [type, value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
};
const shiftDate = (key, days) => {
  const date = new Date(`${key}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
};
const makeRange = (days = 30) => ({
  from: shiftDate(dateKey(), 1 - days),
  to: dateKey(),
});
const compactMoney = (value) =>
  value >= 1e9
    ? `${Number((value / 1e9).toFixed(1))} tỷ`
    : value >= 1e6
      ? `${Number((value / 1e6).toFixed(1))} tr`
      : value >= 1e3
        ? `${Number((value / 1e3).toFixed(1))} nghìn`
        : formatNumber(Math.round(value));

// Sparse API buckets become zero only after the revenue request succeeds.
function normalizeTimeline(records, period, range) {
  if (!Array.isArray(records)) return [];
  const map = new Map(records.map((record) => [record.period, record]));
  const points = [];
  const cursor = new Date(`${range.from}T12:00:00Z`);
  const end = new Date(`${range.to}T12:00:00Z`);
  if (period === "month") cursor.setUTCDate(1);
  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, period === "month" ? 7 : 10);
    const record = map.get(key);
    points.push({
      key,
      label:
        period === "month"
          ? `T${cursor.getUTCMonth() + 1}/${String(cursor.getUTCFullYear()).slice(-2)}`
          : `${String(cursor.getUTCDate()).padStart(2, "0")}/${String(cursor.getUTCMonth() + 1).padStart(2, "0")}`,
      fullDate:
        period === "month"
          ? `Tháng ${cursor.getUTCMonth() + 1}/${cursor.getUTCFullYear()}`
          : formatDate(`${key}T12:00:00+07:00`),
      revenue: record ? numberOrNull(record.revenue) : 0,
      orderCount: record ? numberOrNull(record.orderCount) : 0,
      refundedAmount: record ? numberOrNull(record.refundedAmount) : 0,
      receiptCount: record ? numberOrNull(record.receiptCount) : 0,
    });
    if (period === "month") cursor.setUTCMonth(cursor.getUTCMonth() + 1);
    else cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return points;
}

function useAnimatedNumber(value) {
  const [display, setDisplay] = useState(numberOrNull(value));
  const previous = useRef(0);
  useEffect(() => {
    const target = numberOrNull(value);
    const reduced =
      window.matchMedia("(prefers-reduced-motion: reduce)").matches ||
      document.querySelector(".admin-shell")?.dataset.motion === "reduced";
    if (target === null || reduced) {
      previous.current = target || 0;
      const frame = requestAnimationFrame(() => setDisplay(target));
      return () => cancelAnimationFrame(frame);
    }
    const start = previous.current;
    const started = performance.now();
    let frame;
    const animate = (now) => {
      const progress = Math.min((now - started) / 850, 1);
      setDisplay(
        Math.round(start + (target - start) * (1 - Math.pow(1 - progress, 4))),
      );
      if (progress < 1) frame = requestAnimationFrame(animate);
      else previous.current = target;
    };
    frame = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(frame);
  }, [value]);
  return display;
}

const EmptyState = ({
  title = "Chưa có dữ liệu trong kỳ này",
  description,
  unavailable = false,
}) => (
  <div className={`atelier-empty ${unavailable ? "is-unavailable" : ""}`}>
    <span className="atelier-empty-icon">
      {unavailable ? <CircleAlert size={24} /> : <Flower2 size={26} />}
    </span>
    <strong>{title}</strong>
    {description && <p>{description}</p>}
  </div>
);
const ListSkeleton = () => (
  <div className="atelier-list-skeleton">
    {[0, 1, 2, 3].map((index) => (
      <div key={index}>
        <span />
        <span />
        <span />
      </div>
    ))}
  </div>
);

function MiniChart({ points, bars = false }) {
  if (!points.length) return null;
  const values = points.map(
    (point) => (bars ? point.orderCount : point.revenue) || 0,
  );
  const max = Math.max(...values, 1);
  const coordinates = values.map((value, index) => ({
    x: 2 + (index / Math.max(values.length - 1, 1)) * 112,
    y: 35 - (value / max) * 28,
  }));
  return (
    <svg className="atelier-mini-chart" viewBox="0 0 116 40" aria-hidden="true">
      {bars ? (
        coordinates.map((point, index) => (
          <rect
            key={index}
            x={point.x}
            y={point.y}
            width={Math.max(1, 85 / points.length)}
            height={35 - point.y}
            rx="2"
          />
        ))
      ) : (
        <path
          d={coordinates
            .map((point, index) => `${index ? "L" : "M"} ${point.x} ${point.y}`)
            .join(" ")}
          fill="none"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      )}
    </svg>
  );
}

function MetricCard({
  label,
  value,
  money,
  icon: Icon,
  tone,
  detail,
  points,
  bars,
  loading,
  onClick,
  index,
}) {
  const animated = useAnimatedNumber(value);
  return (
    <button
      type="button"
      className={`atelier-metric atelier-metric--${tone} atelier-reveal`}
      style={{ "--reveal-delay": `${index * 65 + 80}ms` }}
      onClick={onClick}
      aria-label={`${label}: ${loading ? "Đang tải" : money ? currency(value) : number(value)}. Xem chi tiết.`}
    >
      <div className="atelier-metric-top">
        <span>{label}</span>
        <span className="atelier-metric-icon">
          <Icon size={18} strokeWidth={1.7} />
        </span>
      </div>
      {loading ? (
        <span className="atelier-skeleton atelier-skeleton-value" />
      ) : (
        <strong className="atelier-metric-value" aria-hidden="true">
          {money ? currency(animated) : number(animated)}
        </strong>
      )}
      <div className="atelier-metric-bottom">
        <span>
          {loading
            ? "Đang đồng bộ dữ liệu"
            : value === null || value === undefined
              ? "Dữ liệu chưa khả dụng"
              : detail}
        </span>
        <ArrowUpRight size={17} />
      </div>
      {!loading && points && <MiniChart points={points} bars={bars} />}
      <span className="atelier-metric-glow" aria-hidden="true" />
    </button>
  );
}

function RevenueChart({ points, onSelect }) {
  const [activeIndex, setActiveIndex] = useState(null);
  const [size, setSize] = useState({ width: 820, height: 285 });
  const containerRef = useRef(null);
  const gradientId = useId().replace(/:/g, "");

  useEffect(() => {
    const observer = new ResizeObserver(([entry]) => {
      const width = Math.round(entry.contentRect.width);
      const height = Math.round(entry.contentRect.height);
      if (width > 0 && height > 0) {
        setSize((current) =>
          current.width === width && current.height === height
            ? current
            : { width, height },
        );
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  const { width, height } = size;
  const left = width < 500 ? 50 : 65,
    right = 18,
    top = 28,
    bottom = 36;
  const max = Math.max(...points.map((point) => point.revenue || 0), 1);
  const ceiling = max === 1 ? 1 : Math.ceil(max / 4) * 4;
  const coordinates = points.map((point, index) => ({
    ...point,
    x: left + (index / Math.max(points.length - 1, 1)) * (width - left - right),
    y:
      height -
      bottom -
      ((point.revenue || 0) / ceiling) * (height - top - bottom),
  }));
  const path = coordinates.reduce((result, point, index) => {
    if (!index) return `M ${point.x},${point.y}`;
    const previous = coordinates[index - 1],
      mid = (previous.x + point.x) / 2;
    return `${result} C ${mid},${previous.y} ${mid},${point.y} ${point.x},${point.y}`;
  }, "");
  const area = coordinates.length
    ? `${path} L ${coordinates.at(-1).x},${height - bottom} L ${coordinates[0].x},${height - bottom} Z`
    : "";
  const selected = coordinates[activeIndex];
  const visibleLabels = new Set(
    Array.from(
      { length: Math.min(points.length, width < 500 ? 5 : 7) },
      (_, index) =>
        Math.round(
          (index * (points.length - 1)) /
            Math.max(Math.min(points.length, width < 500 ? 5 : 7) - 1, 1),
        ),
    ),
  );
  const handlePointer = (event) => {
    const bounds = event.currentTarget.getBoundingClientRect(),
      x = ((event.clientX - bounds.left) / bounds.width) * width;
    setActiveIndex(
      Math.min(
        points.length - 1,
        Math.max(
          0,
          Math.round(
            ((x - left) / (width - left - right)) * (points.length - 1),
          ),
        ),
      ),
    );
  };
  const handleKeyDown = (event) => {
    if (
      ["ArrowRight", "ArrowLeft", "Home", "End", "Enter", " "].includes(
        event.key,
      )
    )
      event.preventDefault();
    if (event.key === "ArrowRight")
      setActiveIndex((index) => Math.min((index ?? -1) + 1, points.length - 1));
    else if (event.key === "ArrowLeft")
      setActiveIndex((index) => Math.max((index ?? 1) - 1, 0));
    else if (event.key === "Home") setActiveIndex(0);
    else if (event.key === "End") setActiveIndex(points.length - 1);
    else if (event.key === "Enter" || event.key === " ")
      onSelect(selected || points[0]);
    else if (event.key === "Escape") setActiveIndex(null);
  };
  return (
    <div ref={containerRef} className="atelier-chart">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="none"
        role="group"
        tabIndex={0}
        aria-label="Biểu đồ tiền vào. Dùng phím mũi tên để chọn kỳ, Enter để xem chi tiết."
        onPointerMove={handlePointer}
        onPointerLeave={() => setActiveIndex(null)}
        onFocus={() => setActiveIndex(0)}
        onBlur={() => setActiveIndex(null)}
        onKeyDown={handleKeyDown}
        onClick={() => onSelect(selected || points[0])}
      >
        <defs>
          <linearGradient id={`area-${gradientId}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#9274f3" stopOpacity=".25" />
            <stop offset="100%" stopColor="#9274f3" stopOpacity=".015" />
          </linearGradient>
          <linearGradient id={`line-${gradientId}`} x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#b29af7" />
            <stop offset="45%" stopColor="#7457e8" />
            <stop offset="100%" stopColor="#b787d8" />
          </linearGradient>
        </defs>
        {[0, 1, 2, 3, 4].map((index) => {
          const y = top + (index * (height - top - bottom)) / 4;
          return (
            <g key={index}>
              <line
                className="atelier-chart-grid"
                x1={left}
                y1={y}
                x2={width - right}
                y2={y}
              />
              <text
                className="atelier-chart-label"
                x={left - 14}
                y={y + 4}
                textAnchor="end"
              >
                {max === 1 && index !== 4
                  ? ""
                  : compactMoney(ceiling * (1 - index / 4))}
              </text>
            </g>
          );
        })}
        <path
          d={area}
          fill={`url(#area-${gradientId})`}
          className="atelier-chart-area"
        />
        <path
          d={path}
          fill="none"
          stroke={`url(#line-${gradientId})`}
          strokeWidth="3.5"
          strokeLinecap="round"
          className="atelier-chart-line"
          pathLength="1"
        />
        {coordinates.length === 1 && (
          <circle
            cx={coordinates[0].x}
            cy={coordinates[0].y}
            r="5"
            fill="#7457e8"
          />
        )}
        {selected && (
          <g aria-hidden="true">
            <line
              className="atelier-chart-cursor"
              x1={selected.x}
              y1={top}
              x2={selected.x}
              y2={height - bottom}
            />
            <circle
              className="atelier-chart-halo"
              cx={selected.x}
              cy={selected.y}
              r="13"
            />
            <circle
              cx={selected.x}
              cy={selected.y}
              r="5.5"
              fill="#7457e8"
              stroke="white"
              strokeWidth="3"
            />
          </g>
        )}
        {coordinates.map(
          (point, index) =>
            visibleLabels.has(index) && (
              <text
                className="atelier-chart-label"
                key={point.key}
                x={point.x}
                y={height - 9}
                textAnchor="middle"
              >
                {point.label}
              </text>
            ),
        )}
      </svg>
      {selected && (
        <div
          className="atelier-chart-tooltip"
          style={{
            left: `${Math.min(78, Math.max(24, (selected.x / width) * 100))}%`,
            top: `${Math.min(59, Math.max(2, (selected.y / height) * 100 - 30))}%`,
          }}
        >
          <span>{selected.fullDate}</span>
          <strong>{currency(selected.revenue)}</strong>
          <small>
            {number(selected.orderCount)} đơn thanh toán · Nhấp / Enter xem chi
            tiết
          </small>
        </div>
      )}
      <span className="atelier-sr-only" role="status">
        {selected &&
          `${selected.fullDate}: ${currency(selected.revenue)}, ${number(selected.orderCount)} đơn thanh toán.`}
      </span>
    </div>
  );
}

const statusLabels = {
  PENDING: ["Chờ xác nhận", "amber"],
  CONFIRMED: ["Đã xác nhận", "violet"],
  PROCESSING: ["Đang xử lý", "violet"],
  SHIPPED: ["Đang giao", "violet"],
  DELIVERED: ["Đã giao", "teal"],
  COMPLETED: ["Hoàn tất", "teal"],
  CANCELLED: ["Đã hủy", "rose"],
  RETURNED: ["Đã trả hàng", "rose"],
};
const paymentLabels = {
  PAID: "Đã thanh toán",
  PENDING: "Chờ thanh toán",
  FAILED: "Thất bại",
  REFUNDED: "Đã hoàn tiền",
  REFUND_PENDING: "Chờ hoàn tiền",
  PARTIALLY_PAID: "Thanh toán một phần",
};
const OrderStatus = ({ status }) => {
  const [label, tone] = statusLabels[status] || [
    status || "Chưa xác định",
    "neutral",
  ];
  return (
    <span className={`atelier-status atelier-status--${tone}`}>
      <i />
      {label}
    </span>
  );
};

function ReportDialog({ report, onClose, data, range, points, loading }) {
  const { overview, spa, products, orders } = data;
  const title = {
    REVENUE: "Dòng tiền trong kỳ",
    ORDERS: "Đơn hàng trong kỳ",
    PRODUCTS: "Hiệu suất sản phẩm",
    SPA: "Hiệu suất lịch hẹn Spa",
    ORDER: `Đơn hàng ${report?.order ? orderCode(report.order) : ""}`,
  }[report?.type];
  const orderColumns = [
    { header: "Mã đơn", accessor: (row) => <strong>{orderCode(row)}</strong> },
    {
      header: "Khách hàng",
      accessor: (row) => row.customerName || "Khách vãng lai",
    },
    { header: "Ngày tạo", accessor: (row) => formatDate(row.createdAt) },
    {
      header: "Trạng thái",
      accessor: (row) => <OrderStatus status={row.status} />,
    },
    {
      header: "Thanh toán",
      accessor: (row) =>
        paymentLabels[row.paymentStatus] || row.paymentStatus || "—",
    },
    {
      header: "Tổng tiền",
      accessor: (row) => currency(row.totalAmount),
      align: "right",
    },
  ];
  const productColumns = [
    { header: "Sản phẩm", accessor: "productName" },
    {
      header: "Đã bán",
      accessor: (row) => number(row.unitsSold),
      align: "right",
    },
    {
      header: "Doanh số gộp",
      accessor: (row) => currency(row.grossSales),
      align: "right",
    },
  ];
  const stat = (label, value) => (
    <div className="atelier-report-stat" key={label}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
  return (
    <Modal
      isOpen={Boolean(report)}
      onClose={onClose}
      title={title}
      maxWidth="940px"
    >
      <div className="atelier-dashboard atelier-report">
        <p className="atelier-report-period">
          {formatDate(`${range.from}T12:00:00+07:00`)} —{" "}
          {formatDate(`${range.to}T12:00:00+07:00`)} · Giờ Việt Nam
        </p>
        {report?.type === "REVENUE" && (
          <>
            <div className="atelier-report-stats">
              {stat("Tổng tiền vào", currency(overview?.revenue))}
              {stat(
                "Tiền hoàn đã xác nhận",
                currency(overview?.refundedAmount),
              )}
              {stat("Giao dịch tiền vào", number(overview?.receiptCount))}
            </div>
            <p className="atelier-report-note">
              Tiền vào được ghi nhận từ sổ giao dịch, gồm tiền mặt, chuyển khoản
              và COD. Tiền hoàn hiển thị riêng. Số đơn thanh toán được thống kê
              theo ngày thanh toán.
            </p>
            {report.point && (
              <div className="atelier-report-selection">
                <CalendarDays size={18} />
                <div>
                  <strong>{report.point.fullDate}</strong>
                  <span>
                    {currency(report.point.revenue)} tiền vào ·{" "}
                    {currency(report.point.refundedAmount)} tiền hoàn ·{" "}
                    {number(report.point.orderCount)} đơn thanh toán
                  </span>
                </div>
              </div>
            )}
            <div className="atelier-report-stats">
              {stat("Chuyển khoản", currency(overview?.bankAmount))}
              {stat("Tiền mặt", currency(overview?.cashAmount))}
              {stat("COD", currency(overview?.codAmount))}
            </div>
            <DataTable
              columns={[
                { header: "Kỳ", accessor: "fullDate" },
                {
                  header: "Tiền vào",
                  accessor: (row) => currency(row.revenue),
                  align: "right",
                },
                {
                  header: "Tiền hoàn",
                  accessor: (row) => currency(row.refundedAmount),
                  align: "right",
                },
                {
                  header: "Đơn thanh toán",
                  accessor: (row) => number(row.orderCount),
                  align: "right",
                },
              ]}
              data={points}
              loading={loading}
              emptyMessage="Dữ liệu dòng tiền chưa khả dụng."
            />
          </>
        )}
        {report?.type === "ORDERS" && (
          <>
            <div className="atelier-report-stats">
              {stat("Đơn được tạo trong kỳ", number(overview?.totalOrders))}
              {stat("Đơn chờ xử lý", number(overview?.openOrders))}
              {stat(
                "Đơn chờ thanh toán",
                number(overview?.pendingPaymentOrders),
              )}
            </div>
            <p className="atelier-report-note">
              Hiển thị {orders?.length ?? 0} đơn gần nhất trong kỳ (tối đa 50).
              Báo cáo tiền vào sử dụng thời gian giao dịch, có thể khác thời
              gian tạo đơn.
            </p>
            {orders === null ? (
              <EmptyState
                unavailable
                title="Chưa tải được danh sách đơn hàng"
              />
            ) : (
              <DataTable
                columns={orderColumns}
                data={orders}
                loading={loading}
                emptyMessage="Chưa có đơn hàng trong kỳ đã chọn."
              />
            )}
            <a className="atelier-text-link" href="#/admin/orders">
              Mở quản lý đơn hàng <ArrowRight size={15} />
            </a>
          </>
        )}
        {report?.type === "PRODUCTS" && (
          <>
            <div className="atelier-report-stats">
              {stat("Mặt hàng cần bổ sung", number(overview?.lowStockItems))}
              {stat(
                "Giá trị vốn tồn kho hiện tại",
                currency(overview?.inventoryValue),
              )}
            </div>
            <p className="atelier-report-note">
              Xếp hạng theo số lượng bán từ các đơn đã giao, với ngày tạo đơn
              thuộc kỳ đã chọn. Tồn kho là số dư hiện tại.
            </p>
            {products === null ? (
              <EmptyState
                unavailable
                title="Chưa tải được hiệu suất sản phẩm"
              />
            ) : (
              <DataTable
                columns={productColumns}
                data={products}
                loading={loading}
                emptyMessage="Chưa có sản phẩm được giao trong kỳ này."
              />
            )}
            <a className="atelier-text-link" href="#/admin/inventory">
              Mở quản lý kho <ArrowRight size={15} />
            </a>
          </>
        )}
        {report?.type === "SPA" && (
          <>
            <div className="atelier-report-stats">
              {stat(
                "Tỷ lệ lấp lịch",
                spa ? `${number(spa.occupancyRatePercent)}%` : "—",
              )}
              {stat("Phút đã đặt", number(spa?.bookedMinutes))}
              {stat("Phút có lịch trực", number(spa?.scheduledMinutes))}
              {stat("Phút thực tế đã phục vụ", number(spa?.servedMinutes))}
              {stat("Lịch khách vắng mặt", number(spa?.noShowAppointments))}
              {stat("Phút vượt lịch trực", number(spa?.overCapacityMinutes))}
            </div>
            <p className="atelier-report-note">
              Tỷ lệ lấp lịch so sánh số phút đã đặt với lịch trực của chuyên
              viên. Thời gian thực tế đã phục vụ được thống kê riêng.
            </p>
            <a className="atelier-text-link" href="#/admin/spa">
              Mở điều phối Spa <ArrowRight size={15} />
            </a>
          </>
        )}
        {report?.type === "ORDER" && (
          <>
            <div className="atelier-order-summary">
              <span className="atelier-customer-avatar">
                {(report.order.customerName || "Khách").charAt(0).toUpperCase()}
              </span>
              <div>
                <strong>{report.order.customerName || "Khách vãng lai"}</strong>
                <span>
                  {report.order.customerPhone || "Chưa có số điện thoại"}
                </span>
              </div>
              <OrderStatus status={report.order.status} />
            </div>
            <div className="atelier-report-stats">
              {stat("Tổng tiền đơn hàng", currency(report.order.totalAmount))}
              {stat("Đã thanh toán", currency(report.order.paidAmount))}
              {stat(
                "Thanh toán",
                paymentLabels[report.order.paymentStatus] ||
                  report.order.paymentStatus ||
                  "—",
              )}
              {stat("Ngày tạo", formatDate(report.order.createdAt))}
            </div>
            <a className="atelier-text-link" href="#/admin/orders">
              Mở quản lý đơn hàng <ArrowRight size={15} />
            </a>
          </>
        )}
      </div>
    </Modal>
  );
}

export const DashboardPage = () => {
  const { user } = useAuth();
  const [today] = useState(() => new Date());
  const [range, setRange] = useState(makeRange),
    [draftRange, setDraftRange] = useState(makeRange);
  const [period, setPeriod] = useState("day"),
    [refresh, setRefresh] = useState(0);
  const [data, setData] = useState({
    overview: null,
    revenue: null,
    products: null,
    spa: null,
    orders: null,
  });
  const [loading, setLoading] = useState(true),
    [errors, setErrors] = useState([]),
    [updatedAt, setUpdatedAt] = useState(null);
  const [report, setReport] = useState(null),
    [exported, setExported] = useState(false);
  const exportTimer = useRef();
  const invalidRange =
    !draftRange.from || !draftRange.to || draftRange.from > draftRange.to;
  const hasDraftChanges =
    draftRange.from !== range.from || draftRange.to !== range.to;
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    const fetchDashboard = async () => {
      setLoading(true);
      setErrors([]);
      const params = new URLSearchParams({
        from: new Date(`${range.from}T00:00:00+07:00`).toISOString(),
        to: new Date(`${shiftDate(range.to, 1)}T00:00:00+07:00`).toISOString(),
      });
      const spaParams = new URLSearchParams({ from: range.from, to: range.to });
      const definitions = [
        ["overview", "Tổng quan", `${ENDPOINTS.DASHBOARD.OVERVIEW}?${params}`],
        [
          "revenue",
          "Dòng tiền",
          `${ENDPOINTS.DASHBOARD.REVENUE}?${params}&period=${period}`,
        ],
        [
          "products",
          "Sản phẩm",
          `${ENDPOINTS.DASHBOARD.TOP_PRODUCTS}?${params}`,
        ],
        [
          "spa",
          "Lịch hẹn Spa",
          `${ENDPOINTS.DASHBOARD.SPA_OCCUPANCY}?${spaParams}`,
        ],
        [
          "orders",
          "Đơn hàng",
          `${ENDPOINTS.ORDERS.LIST}?${params}&size=50&sort=createdAt,desc`,
        ],
      ];
      const results = await Promise.allSettled(
        definitions.map(([, , endpoint]) =>
          apiClient.get(endpoint, { signal: controller.signal }),
        ),
      );
      if (!current) return;
      const next = {},
        failed = [];
      results.forEach((result, index) => {
        const [key, label] = definitions[index],
          payload = result.status === "fulfilled" ? unwrap(result.value) : null;
        next[key] = payload;
        if (key === "orders")
          next[key] = Array.isArray(payload?.content)
            ? payload.content
            : Array.isArray(payload)
              ? payload
              : null;
        if (["revenue", "products"].includes(key) && !Array.isArray(payload))
          next[key] = null;
        if (
          ["overview", "spa"].includes(key) &&
          (!payload || typeof payload !== "object" || Array.isArray(payload))
        )
          next[key] = null;
        if (next[key] === null) failed.push(label);
      });
      setData(next);
      setErrors(failed);
      setUpdatedAt(failed.length < 5 ? new Date() : null);
      setLoading(false);
    };
    fetchDashboard();
    return () => {
      current = false;
      controller.abort();
    };
  }, [range, period, refresh]);
  useEffect(() => () => clearTimeout(exportTimer.current), []);
  const points = useMemo(
    () => normalizeTimeline(data.revenue, period, range),
    [data.revenue, period, range],
  );
  const overview = data.overview,
    spa = data.spa,
    occupancy = numberOrNull(spa?.occupancyRatePercent),
    scheduled = numberOrNull(spa?.scheduledMinutes);
  const bestSale = data.revenue
    ? Math.max(...points.map((point) => point.revenue || 0), 0)
    : null;
  const products = data.products?.slice(0, 5),
    recentOrders = data.orders?.slice(0, 5);
  const maxUnits = Math.max(
    ...(data.products || []).map((product) => Number(product.unitsSold) || 0),
    1,
  );
  const openReport = (type, extra = {}) => setReport({ type, ...extra });
  const setQuickRange = (days) => {
    const next = makeRange(days);
    setRange(next);
    setDraftRange(next);
  };
  const exportCsv = () => {
    const rows = [
      [
        "Kỳ",
        "Tiền vào (VND)",
        "Tiền hoàn (VND)",
        "Đơn thanh toán",
        "Giao dịch tiền vào",
      ],
      ...points.map((point) => [
        point.key,
        point.revenue ?? "",
        point.refundedAmount ?? "",
        point.orderCount ?? "",
        point.receiptCount ?? "",
      ]),
    ];
    const text =
      "\uFEFF" +
      rows
        .map((row) =>
          row
            .map((cell) => {
              const value = String(cell),
                safe = /^[=+\-@]/.test(value) ? `'${value}` : value;
              return `"${safe.replace(/"/g, '""')}"`;
            })
            .join(","),
        )
        .join("\r\n");
    const url = URL.createObjectURL(
        new Blob([text], { type: "text/csv;charset=utf-8;" }),
      ),
      link = document.createElement("a");
    link.href = url;
    link.download = `beautyshop-dong-tien-${range.from}-${range.to}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setExported(true);
    clearTimeout(exportTimer.current);
    exportTimer.current = setTimeout(() => setExported(false), 2400);
  };
  const dateLabel = new Intl.DateTimeFormat("vi-VN", {
    timeZone: BUSINESS_ZONE,
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(today);

  return (
    <div className="content-container atelier-dashboard" aria-busy={loading}>
      {loading && <div className="atelier-loading-line" aria-hidden="true" />}
      <section className="atelier-greeting atelier-reveal">
        <div className="atelier-greeting-copy">
          <div className="atelier-eyebrow">
            <span className="atelier-eyebrow-line" />
            YOUR BEAUTY BUSINESS, IN FOCUS
          </div>
          <p className="atelier-welcome">
            Chào {user?.fullName?.split(" ").at(-1) || "bạn"}, chúc một ngày rực
            rỡ.
          </p>
          <h1>
            Tổng quan <em>kinh doanh.</em>
            <span className="atelier-heading-spark">
              <Sparkles size={28} strokeWidth={1.4} />
            </span>
          </h1>
          <p className="atelier-greeting-description">
            Mọi chuyển động của BeautyShop, trong một góc nhìn.
          </p>
          <div className="atelier-today">
            <CalendarDays size={14} />
            {dateLabel}
          </div>
        </div>
        <div className="atelier-orbit-art" aria-hidden="true">
          <span className="atelier-art-halo" />
          <span className="atelier-orbit atelier-orbit-one" />
          <span className="atelier-orbit atelier-orbit-two" />
          <div className="atelier-art-core">
            <Flower2 size={72} strokeWidth={0.75} />
          </div>
          <span className="atelier-art-pearl atelier-art-pearl-one" />
          <span className="atelier-art-pearl atelier-art-pearl-two" />
          <div className="atelier-art-note">
            <Sparkles size={14} />
            <span>Beauty in every detail</span>
          </div>
          <Sparkles size={24} className="atelier-art-star" />
        </div>
      </section>
      <div
        className="atelier-toolbar atelier-reveal"
        style={{ "--reveal-delay": "40ms" }}
      >
        <div
          className="atelier-range-presets"
          aria-label="Khoảng thời gian nhanh"
        >
          {[7, 30].map((days) => {
            const preset = makeRange(days);
            return (
              <button
                type="button"
                key={days}
                aria-pressed={
                  range.from === preset.from && range.to === preset.to
                }
                className={
                  range.from === preset.from && range.to === preset.to
                    ? "is-active"
                    : ""
                }
                onClick={() => setQuickRange(days)}
                disabled={loading}
              >
                {days} ngày
              </button>
            );
          })}
        </div>
        <form
          className="atelier-range-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (!invalidRange) setRange({ ...draftRange });
          }}
        >
          <div className="atelier-date-field">
            <CalendarDays size={15} />
            <input
              type="date"
              aria-label="Từ ngày"
              required
              value={draftRange.from}
              max={draftRange.to}
              onChange={(event) =>
                setDraftRange((current) => ({
                  ...current,
                  from: event.target.value,
                }))
              }
            />
            <span>—</span>
            <input
              type="date"
              aria-label="Đến ngày"
              required
              value={draftRange.to}
              min={draftRange.from}
              onChange={(event) =>
                setDraftRange((current) => ({
                  ...current,
                  to: event.target.value,
                }))
              }
            />
          </div>
          {hasDraftChanges && (
            <Button
              type="submit"
              variant="accent"
              size="sm"
              disabled={invalidRange || loading}
            >
              Áp dụng
            </Button>
          )}
        </form>
        <div className="atelier-toolbar-actions">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setRefresh((value) => value + 1)}
            loading={loading}
            icon={RefreshCw}
            aria-label="Làm mới dữ liệu tổng quan"
          >
            Làm mới
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={exportCsv}
            disabled={loading || data.revenue === null}
            icon={exported ? Check : ArrowDownToLine}
          >
            {exported ? "Đã xuất CSV" : "Xuất báo cáo"}
          </Button>
        </div>
      </div>
      {errors.length > 0 && (
        <div className="atelier-error" role="alert">
          <CircleAlert size={19} />
          <div>
            <strong>Một phần dữ liệu chưa tải được</strong>
            <span>
              {errors.join(", ")}. Hãy thử kết nối lại để cập nhật đầy đủ.
            </span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setRefresh((value) => value + 1)}
            icon={RefreshCw}
          >
            Thử lại
          </Button>
        </div>
      )}
      <div className="atelier-metrics">
        <MetricCard
          label="Tổng tiền vào"
          value={numberOrNull(overview?.revenue)}
          money
          icon={Wallet}
          tone="violet"
          detail={`${number(overview?.receiptCount)} giao dịch trong kỳ`}
          points={data.revenue ? points : null}
          loading={loading}
          onClick={() => openReport("REVENUE")}
          index={0}
        />
        <MetricCard
          label="Đơn hàng"
          value={numberOrNull(overview?.totalOrders)}
          icon={ShoppingBag}
          tone="rose"
          detail={`${number(overview?.openOrders)} đơn cần xử lý`}
          loading={loading}
          onClick={() => openReport("ORDERS")}
          index={1}
        />
        <MetricCard
          label="Khách hàng hoạt động"
          value={numberOrNull(overview?.activeCustomers)}
          icon={Users}
          tone="teal"
          detail="Tài khoản đang hoạt động"
          loading={loading}
          onClick={() => {
            window.location.hash = "/admin/crm";
          }}
          index={2}
        />
        <MetricCard
          label="Giá trị đơn trung bình"
          value={numberOrNull(overview?.averageOrderValue)}
          money
          icon={CreditCard}
          tone="cream"
          detail="Đơn đã thanh toán trong kỳ"
          loading={loading}
          onClick={() => openReport("REVENUE")}
          index={3}
        />
      </div>
      <div className="atelier-main-grid">
        <section
          className="atelier-panel atelier-revenue-panel atelier-reveal"
          style={{ "--reveal-delay": "230ms" }}
        >
          <div className="atelier-panel-header">
            <div>
              <div className="atelier-panel-kicker">
                <span className="atelier-color-dot" />
                THE BIG PICTURE
              </div>
              <h2>Chuyển động dòng tiền</h2>
            </div>
            <div
              className="atelier-period-control"
              aria-label="Độ chia biểu đồ"
            >
              {[
                ["day", "Theo ngày"],
                ["month", "Theo tháng"],
              ].map(([value, label]) => (
                <button
                  type="button"
                  key={value}
                  aria-pressed={period === value}
                  className={period === value ? "is-active" : ""}
                  disabled={loading}
                  onClick={() => setPeriod(value)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <div className="atelier-revenue-summary">
            <strong>
              {loading ? (
                <span className="atelier-skeleton atelier-skeleton-chart-value" />
              ) : (
                currency(overview?.revenue)
              )}
            </strong>
            <span>
              <span className="atelier-color-dot" />
              Tiền vào trong kỳ
            </span>
            <button
              type="button"
              className="atelier-icon-button"
              onClick={() => openReport("REVENUE")}
              aria-label="Xem báo cáo dòng tiền"
            >
              <ArrowUpRight size={19} />
            </button>
          </div>
          {loading ? (
            <div className="atelier-chart-skeleton">
              <span />
              <span />
              <span />
              <div />
            </div>
          ) : data.revenue === null ? (
            <EmptyState
              unavailable
              title="Biểu đồ đang chờ dữ liệu"
              description="Thử làm mới để xem dòng tiền trong kỳ đã chọn."
            />
          ) : (
            <RevenueChart
              key={`${period}-${range.from}-${range.to}`}
              points={points}
              onSelect={(point) => openReport("REVENUE", { point })}
            />
          )}
          <div className="atelier-chart-footer">
            <div>
              <span>Đỉnh tiền vào trong kỳ</span>
              <strong>{loading ? "—" : currency(bestSale)}</strong>
            </div>
            <div>
              <span>Tiền hoàn đã xác nhận</span>
              <strong>
                {loading ? "—" : currency(overview?.refundedAmount)}
              </strong>
            </div>
            <button
              type="button"
              className="atelier-text-link"
              onClick={() => openReport("REVENUE")}
            >
              Chi tiết dòng tiền <ArrowRight size={16} />
            </button>
          </div>
        </section>
        <section
          className="atelier-panel atelier-spa-panel atelier-reveal"
          style={{ "--reveal-delay": "290ms" }}
        >
          <div className="atelier-panel-header">
            <div>
              <div className="atelier-panel-kicker">A MOMENT OF BALANCE</div>
              <h2>Nhịp điệu Spa</h2>
            </div>
            <span className="atelier-spa-icon">
              <Flower2 size={21} strokeWidth={1.6} />
            </span>
          </div>
          <div className="atelier-spa-gauge">
            <svg viewBox="0 0 210 170" aria-hidden="true">
              <defs>
                <linearGradient
                  id="atelier-spa-gradient"
                  x1="0"
                  y1="0"
                  x2="1"
                  y2="1"
                >
                  <stop offset="0%" stopColor="#87cab9" />
                  <stop offset="100%" stopColor="#259d90" />
                </linearGradient>
              </defs>
              <circle className="atelier-spa-track" cx="105" cy="93" r="69" />
              <circle
                className="atelier-spa-progress"
                cx="105"
                cy="93"
                r="69"
                pathLength="100"
                strokeDasharray={`${Math.max(0, Math.min(100, occupancy || 0)) * 0.75} 100`}
                transform="rotate(135 105 93)"
              />
              <circle className="atelier-spa-inner" cx="105" cy="93" r="55" />
            </svg>
            <div className="atelier-spa-gauge-value">
              <strong>
                {loading || occupancy === null || !scheduled
                  ? "—"
                  : `${Number(occupancy.toFixed(1))}%`}
              </strong>
              <span>
                {loading
                  ? "Đang đồng bộ"
                  : spa === null
                    ? "Dữ liệu chưa khả dụng"
                    : !scheduled
                      ? "Chưa có lịch trực"
                      : "Tỷ lệ lấp lịch"}
              </span>
            </div>
          </div>
          <div className="atelier-spa-stats">
            <div>
              <span>
                <i />
                Phút đã đặt
              </span>
              <strong>
                {loading ? "—" : number(spa?.bookedMinutes)}
                <small> phút</small>
              </strong>
            </div>
            <div>
              <span>
                <i />
                Lịch trực
              </span>
              <strong>
                {loading ? "—" : number(spa?.scheduledMinutes)}
                <small> phút</small>
              </strong>
            </div>
          </div>
          {spa?.overCapacity && (
            <p className="atelier-spa-warning">
              <CircleAlert size={14} />
              Vượt {number(spa.overCapacityMinutes)} phút lịch trực
            </p>
          )}
          <div className="atelier-spa-note">
            <Flower2 size={16} />
            <p>
              {loading || !spa
                ? "Theo dõi lịch hẹn và thời gian phục vụ."
                : `${number(spa.servedMinutes)} phút thực tế đã phục vụ trong kỳ.`}
            </p>
          </div>
          <button
            type="button"
            className="atelier-spa-action"
            onClick={() => openReport("SPA")}
          >
            Khám phá hiệu suất Spa <ArrowUpRight size={17} />
          </button>
        </section>
      </div>
      <div className="atelier-lower-grid">
        <section
          className="atelier-panel atelier-products-panel atelier-reveal"
          style={{ "--reveal-delay": "350ms" }}
        >
          <div className="atelier-panel-header">
            <div>
              <div className="atelier-panel-kicker">THE BEAUTY EDIT</div>
              <h2>Sản phẩm được yêu thích</h2>
            </div>
            <button
              type="button"
              className="atelier-text-link"
              onClick={() => openReport("PRODUCTS")}
            >
              Top 10 <ArrowUpRight size={16} />
            </button>
          </div>
          <p className="atelier-panel-description">
            Xếp hạng theo số lượng bán từ các đơn đã giao trong kỳ.
          </p>
          {loading ? (
            <ListSkeleton />
          ) : products === undefined ? (
            <EmptyState unavailable title="Chưa tải được sản phẩm bán chạy" />
          ) : !products.length ? (
            <EmptyState
              title="Bộ sưu tập đang chờ dấu ấn mới"
              description="Sản phẩm xuất hiện khi có đơn đã giao trong kỳ này."
            />
          ) : (
            <div className="atelier-product-list">
              {products.map((product, index) => (
                <button
                  type="button"
                  key={product.productId}
                  className="atelier-product-row"
                  onClick={() => openReport("PRODUCTS")}
                >
                  <span
                    className={`atelier-product-rank ${index === 0 ? "is-first" : ""}`}
                  >
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <span
                    className={`atelier-product-avatar atelier-product-avatar-${index % 3}`}
                  >
                    <Package size={23} strokeWidth={1.4} />
                  </span>
                  <div className="atelier-product-description">
                    <strong>{product.productName}</strong>
                    <span>{number(product.unitsSold)} sản phẩm đã bán</span>
                    <div className="atelier-product-track">
                      <i
                        style={{
                          width: `${Math.min(100, (Number(product.unitsSold) / maxUnits) * 100)}%`,
                        }}
                      />
                    </div>
                  </div>
                  <strong className="atelier-product-sales">
                    {currency(product.grossSales)}
                  </strong>
                  <ArrowUpRight size={16} className="atelier-product-arrow" />
                </button>
              ))}
            </div>
          )}
          <a className="atelier-panel-bottom-link" href="#/admin/products">
            Khám phá danh mục sản phẩm <ArrowRight size={16} />
          </a>
        </section>
        <section
          className="atelier-panel atelier-operation-panel atelier-reveal"
          style={{ "--reveal-delay": "410ms" }}
        >
          <div className="atelier-panel-header">
            <div>
              <div className="atelier-panel-kicker">KEEP THINGS FLOWING</div>
              <h2>Ưu tiên hôm nay</h2>
            </div>
            <span className="atelier-live-label">
              <i />
              Vận hành
            </span>
          </div>
          {[
            {
              tone: "amber",
              Icon: Clock3,
              value: overview?.openOrders,
              text: "đơn cần xử lý",
              detail: "Xác nhận, chuẩn bị và giao hàng",
              type: "ORDERS",
            },
            {
              tone: "rose",
              Icon: Package,
              value: overview?.lowStockItems,
              text: "mặt hàng cần bổ sung",
              detail: "Theo ngưỡng tồn kho hiện tại",
              type: "PRODUCTS",
            },
            {
              tone: "violet",
              Icon: Banknote,
              value: overview?.pendingPaymentOrders,
              text: "đơn chờ thanh toán",
              detail: "Theo dõi để hoàn tất giao dịch",
              type: "REVENUE",
            },
          ].map(({ tone, Icon, value, text, detail, type }) => (
            <button
              type="button"
              key={tone}
              className={`atelier-task atelier-task--${tone}`}
              onClick={() => openReport(type)}
            >
              <span className="atelier-task-icon">
                <Icon size={20} />
              </span>
              <div>
                <strong>
                  {loading
                    ? "Đang đồng bộ dữ liệu"
                    : `${number(value)} ${text}`}
                </strong>
                <span>{detail}</span>
              </div>
              <ArrowUpRight size={18} />
            </button>
          ))}
          <div className="atelier-shortcuts">
            <span>Lối tắt cho bạn</span>
            <div>
              <a href="#/admin/spa">
                <CalendarDays size={17} />
                Lịch hẹn
                <ArrowUpRight size={13} />
              </a>
              <a href="#/admin/products">
                <ShoppingBag size={17} />
                Sản phẩm
                <ArrowUpRight size={13} />
              </a>
              <a href="#/admin/vouchers">
                <Sparkles size={17} />
                Ưu đãi
                <ArrowUpRight size={13} />
              </a>
            </div>
          </div>
        </section>
      </div>
      <section
        className="atelier-panel atelier-orders-panel atelier-reveal"
        style={{ "--reveal-delay": "450ms" }}
      >
        <div className="atelier-panel-header">
          <div>
            <div className="atelier-panel-kicker">
              EVERY ORDER TELLS A STORY
            </div>
            <h2>Những đơn hàng gần đây</h2>
          </div>
          <a className="atelier-text-link" href="#/admin/orders">
            Tất cả đơn hàng <ArrowUpRight size={16} />
          </a>
        </div>
        {loading ? (
          <ListSkeleton />
        ) : recentOrders === undefined ? (
          <EmptyState unavailable title="Danh sách đơn hàng chưa khả dụng" />
        ) : !recentOrders.length ? (
          <EmptyState
            title="Chưa có đơn hàng trong kỳ này"
            description="Đơn hàng mới sẽ xuất hiện tại đây."
          />
        ) : (
          <div
            className="atelier-orders-scroll"
            tabIndex={0}
            role="region"
            aria-label="Danh sách đơn hàng, có thể cuộn ngang"
          >
            <table className="atelier-orders-table">
              <thead>
                <tr>
                  <th>Đơn hàng</th>
                  <th>Khách hàng</th>
                  <th>Ngày đặt</th>
                  <th>Trạng thái</th>
                  <th className="atelier-align-right">Giá trị đơn</th>
                  <th>
                    <span className="atelier-sr-only">Chi tiết</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {recentOrders.map((order) => (
                  <tr key={order.id}>
                    <td>
                      <button
                        type="button"
                        className="atelier-order-code"
                        onClick={() => openReport("ORDER", { order })}
                      >
                        {orderCode(order)}
                      </button>
                      <small>
                        {paymentLabels[order.paymentStatus] ||
                          order.paymentStatus ||
                          "—"}
                      </small>
                    </td>
                    <td>
                      <div className="atelier-table-customer">
                        <span className="atelier-customer-avatar">
                          {(order.customerName || "Khách")
                            .charAt(0)
                            .toUpperCase()}
                        </span>
                        <span>{order.customerName || "Khách vãng lai"}</span>
                      </div>
                    </td>
                    <td>{formatDate(order.createdAt)}</td>
                    <td>
                      <OrderStatus status={order.status} />
                    </td>
                    <td className="atelier-align-right">
                      <strong>{currency(order.totalAmount)}</strong>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="atelier-icon-button"
                        onClick={() => openReport("ORDER", { order })}
                        aria-label={`Xem đơn hàng ${orderCode(order)}`}
                      >
                        <ArrowUpRight size={17} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <div className="atelier-dashboard-footnote">
        <span>
          <span className={`atelier-sync-dot ${loading ? "is-loading" : ""}`} />
          {loading
            ? "Đang đồng bộ dữ liệu…"
            : updatedAt
              ? `Cập nhật lúc ${new Intl.DateTimeFormat("vi-VN", { timeZone: BUSINESS_ZONE, hour: "2-digit", minute: "2-digit" }).format(updatedAt)}`
              : "Đang chờ kết nối dữ liệu"}
        </span>
        <span>BeautyShop · Chăm chút từng chuyển động</span>
      </div>
      <span className="atelier-sr-only" role="status">
        {exported ? "Đã tải xuống báo cáo CSV." : ""}
      </span>
      {report && (
        <ReportDialog
          report={report}
          onClose={() => setReport(null)}
          data={data}
          range={range}
          points={points}
          loading={loading}
        />
      )}
    </div>
  );
};
