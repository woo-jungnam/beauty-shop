import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  Bell,
  Check,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  Command,
  Gem,
  LogOut,
  Menu,
  Pause,
  RefreshCw,
  Search,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  WifiOff,
} from "lucide-react";
import { useAuth } from "../app/providers/AuthProvider";
import { getInitials, normalizeSearch } from "../app/adminNavigation";
import { Button } from "../shared/ui/Button";
import { Modal } from "../shared/ui/Modal";
import { Badge } from "../shared/ui/Badge";
import { apiClient } from "../shared/api/client";
import { ENDPOINTS } from "../shared/api/endpoints";

export const Topbar = ({
  currentTitle,
  activeTab,
  navigationItems = [],
  onNavigate,
  onOpenMenu,
  mobileOpen,
  motionEnabled,
  onToggleMotion,
}) => {
  const { user, logout, isAdmin } = useAuth();
  const [commandOpen, setCommandOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const [accountOpen, setAccountOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [health, setHealth] = useState({
    status: "UNKNOWN",
    latency: null,
    details: null,
  });
  const [healthOpen, setHealthOpen] = useState(false);
  const [checkingHealth, setCheckingHealth] = useState(false);
  const [alerts, setAlerts] = useState([]);
  const [alertsOpen, setAlertsOpen] = useState(false);
  const [alertsLoading, setAlertsLoading] = useState(false);
  const [alertsError, setAlertsError] = useState("");
  const [logoutError, setLogoutError] = useState("");
  const accountRef = useRef(null);
  const searchRef = useRef(null);
  const requestRef = useRef(false);
  const mountedRef = useRef(false);
  const name = user?.fullName || user?.username || "Quản trị viên";
  const isMac = /Mac|iPhone|iPad/.test(navigator.platform);
  const filteredItems = useMemo(
    () =>
      navigationItems.filter((item) =>
        normalizeSearch(
          `${item.label} ${item.description} ${item.keywords}`,
        ).includes(normalizeSearch(query)),
      ),
    [navigationItems, query],
  );

  useEffect(() => {
    if (commandOpen)
      document
        .getElementById(`admin-command-${filteredItems[selected]?.id}`)
        ?.scrollIntoView({ block: "nearest", behavior: "instant" });
  }, [commandOpen, selected, filteredItems]);

  const checkHealth = useCallback(async () => {
    if (requestRef.current) return;
    requestRef.current = true;
    setCheckingHealth(true);
    const start = performance.now();
    try {
      const response = await apiClient.get(ENDPOINTS.SYSTEM.HEALTH, {
        timeout: 10000,
      });
      const data = response?.data || response;
      if (mountedRef.current)
        setHealth({
          status: data?.status || "UNKNOWN",
          latency: Math.round(performance.now() - start),
          details: data?.components || null,
        });
    } catch {
      if (mountedRef.current)
        setHealth({ status: "DOWN", latency: null, details: null });
    } finally {
      requestRef.current = false;
      if (mountedRef.current) setCheckingHealth(false);
    }
  }, []);

  const loadAlerts = useCallback(async () => {
    if (!isAdmin) return;
    setAlertsLoading(true);
    setAlertsError("");
    try {
      const response = await apiClient.get(ENDPOINTS.SYSTEM.ALERTS, {
        timeout: 10000,
      });
      const data = response?.data || response;
      if (!Array.isArray(data))
        throw new Error("Không thể đọc dữ liệu thông báo.");
      if (mountedRef.current) setAlerts(data);
    } catch {
      if (mountedRef.current)
        setAlertsError("Chưa thể tải thông báo. Vui lòng thử lại.");
    } finally {
      if (mountedRef.current) setAlertsLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    mountedRef.current = true;
    const frame = requestAnimationFrame(() => {
      checkHealth();
      loadAlerts();
    });
    const interval = setInterval(checkHealth, 60000);
    return () => {
      mountedRef.current = false;
      cancelAnimationFrame(frame);
      clearInterval(interval);
    };
  }, [checkHealth, loadAlerts]);

  useEffect(() => {
    const handleShortcut = (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (document.querySelector('[aria-modal="true"]') && !commandOpen)
          return;
        if (!commandOpen) {
          setQuery("");
          setSelected(0);
        }
        setCommandOpen((open) => !open);
        setAccountOpen(false);
      }
    };
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, [commandOpen]);

  useEffect(() => {
    if (!commandOpen) return;
    const timeout = setTimeout(() => searchRef.current?.focus(), 70);
    return () => clearTimeout(timeout);
  }, [commandOpen]);

  useEffect(() => {
    if (!accountOpen) return;
    const handlePointer = (event) => {
      if (!accountRef.current?.contains(event.target)) setAccountOpen(false);
    };
    const handleKey = (event) => {
      if (event.key === "Escape") {
        setAccountOpen(false);
        accountRef.current?.querySelector("button")?.focus();
      }
    };
    window.addEventListener("pointerdown", handlePointer);
    window.addEventListener("keydown", handleKey);
    return () => {
      window.removeEventListener("pointerdown", handlePointer);
      window.removeEventListener("keydown", handleKey);
    };
  }, [accountOpen]);

  const navigateFromSearch = (item) => {
    if (!item) return;
    setCommandOpen(false);
    onNavigate(item.id);
  };
  const handleSearchKey = (event) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelected((value) =>
        Math.max(0, Math.min(value + 1, filteredItems.length - 1)),
      );
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelected((value) => Math.max(0, value - 1));
    }
    if (event.key === "Enter") {
      event.preventDefault();
      navigateFromSearch(filteredItems[selected]);
    }
  };
  const handleLogout = async () => {
    setLoggingOut(true);
    setLogoutError("");
    try {
      await logout();
      window.location.hash = "/admin/login";
    } catch {
      setLogoutError("Chưa thể đăng xuất. Vui lòng thử lại.");
      setLoggingOut(false);
    }
  };
  const healthLabel =
    health.status === "UP"
      ? "Kết nối ổn định"
      : health.status === "UNKNOWN"
        ? "Đang kiểm tra"
        : "Mất kết nối";

  return (
    <header className="topbar atelier-topbar">
      <div className="topbar-location">
        <button
          className="admin-icon-button mobile-menu-toggle"
          onClick={onOpenMenu}
          aria-label="Mở menu điều hướng"
          aria-controls="admin-navigation"
          aria-expanded={mobileOpen}
        >
          <Menu size={21} />
        </button>
        <span className="topbar-location-mark">
          <Gem size={17} strokeWidth={1.7} />
        </span>
        <span className="topbar-breadcrumb-root">Không gian làm việc</span>
        <ChevronRight size={13} className="topbar-breadcrumb-divider" />
        <span className="topbar-current">{currentTitle}</span>
      </div>
      <button
        className="command-trigger"
        onClick={() => {
          setQuery("");
          setSelected(0);
          setCommandOpen(true);
        }}
        aria-label="Tìm nhanh phân hệ quản trị"
      >
        <Search size={17} />
        <span>Tìm nhanh trong quản trị...</span>
        <kbd>{isMac ? <Command size={11} /> : "Ctrl"} K</kbd>
      </button>
      <div className="topbar-actions">
        <button
          className={`admin-icon-button motion-toggle ${motionEnabled ? "is-enabled" : ""}`}
          onClick={onToggleMotion}
          aria-label={
            motionEnabled
              ? "Giảm hiệu ứng chuyển động"
              : "Bật hiệu ứng chuyển động"
          }
          title={
            motionEnabled
              ? "Giảm hiệu ứng chuyển động"
              : "Bật hiệu ứng chuyển động"
          }
          aria-pressed={motionEnabled}
        >
          {motionEnabled ? <Sparkles size={18} /> : <Pause size={18} />}
        </button>
        <button
          className={`topbar-health ${health.status === "UP" ? "is-up" : "is-down"}`}
          onClick={() => setHealthOpen(true)}
          title={healthLabel}
          aria-label={`Trạng thái hệ thống: ${healthLabel}`}
        >
          <span className="health-dot" />
          <span>
            {health.status === "UP"
              ? "Đã kết nối"
              : health.status === "UNKNOWN"
                ? "Kiểm tra"
                : "Ngoại tuyến"}
          </span>
        </button>
        {isAdmin && (
          <button
            className="admin-icon-button notification-trigger"
            onClick={() => {
              setAlertsOpen(true);
              loadAlerts();
            }}
            aria-label={`Thông báo vận hành${alerts.length ? `, ${alerts.length} nhóm cảnh báo` : ""}`}
            title="Thông báo vận hành"
          >
            <Bell size={19} />
            {alerts.length > 0 && <span className="notification-dot" />}
          </button>
        )}
        <span className="topbar-action-divider" />
        <div className="topbar-account" ref={accountRef}>
          <button
            className={`account-trigger ${accountOpen ? "is-open" : ""}`}
            onClick={() => setAccountOpen((value) => !value)}
            aria-expanded={accountOpen}
            aria-controls="admin-account-popover"
            aria-label={`Tài khoản ${name}`}
          >
            <span className="topbar-avatar">{getInitials(name)}</span>
            <span className="account-trigger-copy">
              <strong>{name}</strong>
              <small>{isAdmin ? "Quản trị viên" : "Nhân viên"}</small>
            </span>
            <ChevronDown size={14} />
          </button>
          {accountOpen && (
            <div className="account-popover" id="admin-account-popover">
              <div className="account-popover-heading">
                <strong>{name}</strong>
                <span>{user?.email || "Phiên đăng nhập đang hoạt động"}</span>
              </div>
              <a href="#/" onClick={() => setAccountOpen(false)}>
                <ShoppingBag size={17} />
                <span>Ghé thăm cửa hàng</span>
                <ArrowUpRight size={14} />
              </a>
              <button
                onClick={() => {
                  setAccountOpen(false);
                  setHealthOpen(true);
                }}
              >
                <Activity size={17} />
                <span>Trạng thái kết nối</span>
              </button>
              <div className="account-popover-separator" />
              <button
                className="account-logout"
                onClick={handleLogout}
                disabled={loggingOut}
              >
                <LogOut size={17} />
                <span>{loggingOut ? "Đang đăng xuất..." : "Đăng xuất"}</span>
              </button>
              {logoutError && <p role="alert">{logoutError}</p>}
            </div>
          )}
        </div>
      </div>
      <Modal
        isOpen={commandOpen}
        onClose={() => setCommandOpen(false)}
        title="Đi đến không gian của bạn"
        maxWidth="620px"
      >
        <div className="command-palette">
          <div className="command-search">
            <Search size={21} aria-hidden="true" />
            <input
              ref={searchRef}
              type="search"
              role="combobox"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setSelected(0);
              }}
              onKeyDown={handleSearchKey}
              placeholder="Tìm đơn hàng, sản phẩm, lịch hẹn..."
              aria-label="Tìm phân hệ quản trị"
              autoComplete="off"
              aria-autocomplete="list"
              aria-expanded="true"
              aria-controls="admin-command-results"
              aria-activedescendant={
                filteredItems[selected]
                  ? `admin-command-${filteredItems[selected].id}`
                  : undefined
              }
            />
          </div>
          <p className="command-section-label" aria-live="polite">
            {query
              ? `${filteredItems.length} kết quả phù hợp`
              : "Các không gian làm việc"}
          </p>
          <div
            className="command-results"
            id="admin-command-results"
            role="listbox"
            aria-label="Phân hệ quản trị"
          >
            {filteredItems.map((item, index) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.id}
                  id={`admin-command-${item.id}`}
                  role="option"
                  tabIndex={-1}
                  aria-selected={index === selected}
                  className={`command-result ${index === selected ? "is-selected" : ""}`}
                  onPointerMove={() => setSelected(index)}
                  onClick={() => navigateFromSearch(item)}
                  aria-label={`Đi đến ${item.label}: ${item.description}`}
                >
                  <span className="command-result-icon">
                    <Icon size={20} strokeWidth={1.7} aria-hidden="true" />
                  </span>
                  <span>
                    <strong>{item.label}</strong>
                    <small>{item.description}</small>
                  </span>
                  {activeTab === item.id ? (
                    <span className="command-current">
                      <Check size={13} />
                      Hiện tại
                    </span>
                  ) : (
                    <ArrowRight size={17} aria-hidden="true" />
                  )}
                </button>
              );
            })}
          </div>
          {!filteredItems.length && (
            <div className="command-empty">
              <CircleHelp size={28} />
              <strong>Chưa tìm thấy phân hệ phù hợp</strong>
              <span>Thử “đơn hàng”, “kho” hoặc “spa”.</span>
            </div>
          )}
          <div className="command-footer">
            <span>
              <kbd>↑</kbd>
              <kbd>↓</kbd> di chuyển
            </span>
            <span>
              <kbd>Enter</kbd> mở phân hệ
            </span>
            <span>
              <kbd>Esc</kbd> đóng
            </span>
          </div>
        </div>
      </Modal>
      <Modal
        isOpen={healthOpen}
        onClose={() => setHealthOpen(false)}
        title="Trạng thái kết nối"
        footer={
          <Button
            variant="accent"
            onClick={checkHealth}
            loading={checkingHealth}
            icon={RefreshCw}
          >
            Kiểm tra lại
          </Button>
        }
      >
        <div
          className={`health-overview ${health.status === "UP" ? "is-up" : ""}`}
        >
          <span>
            {health.status === "UP" ? (
              <Activity size={28} />
            ) : (
              <WifiOff size={28} />
            )}
          </span>
          <div>
            <strong>{healthLabel}</strong>
            <p>
              {health.latency === null
                ? "Chưa có phản hồi từ máy chủ."
                : `Máy chủ phản hồi trong ${health.latency} ms.`}
            </p>
          </div>
          <Badge variant={health.status === "UP" ? "success" : "warning"}>
            {health.status}
          </Badge>
        </div>
        {health.details && (
          <div className="health-components">
            {Object.entries(health.details).map(([key, component]) => (
              <div key={key}>
                <span>
                  {key === "db"
                    ? "Cơ sở dữ liệu"
                    : key === "redis"
                      ? "Bộ nhớ đệm"
                      : key === "diskSpace"
                        ? "Dung lượng lưu trữ"
                        : key}
                </span>
                <Badge
                  variant={component?.status === "UP" ? "success" : "warning"}
                >
                  {component?.status || "Chưa xác minh"}
                </Badge>
              </div>
            ))}
          </div>
        )}
        <p className="health-footnote">
          <ShieldCheck size={15} />
          Trạng thái được cập nhật mỗi phút khi bạn làm việc.
        </p>
      </Modal>
      <Modal
        isOpen={alertsOpen}
        onClose={() => setAlertsOpen(false)}
        title="Thông báo vận hành"
        footer={
          <Button
            variant="outline"
            onClick={loadAlerts}
            loading={alertsLoading}
            icon={RefreshCw}
          >
            Cập nhật thông báo
          </Button>
        }
      >
        {alertsLoading ? (
          <div className="alerts-empty" role="status">
            <RefreshCw className="animate-spin" size={26} />
            <span>Đang cập nhật thông báo...</span>
          </div>
        ) : alertsError ? (
          <div className="alerts-empty" role="alert">
            <WifiOff size={28} />
            <strong>Chưa thể kết nối</strong>
            <p>{alertsError}</p>
          </div>
        ) : !alerts.length ? (
          <div className="alerts-empty">
            <span className="alerts-empty-icon">
              <Check size={27} />
            </span>
            <strong>Bạn đã theo dõi mọi hoạt động</strong>
            <p>Không có cảnh báo vận hành đang mở.</p>
          </div>
        ) : (
          <div className="alerts-list">
            {alerts.map((alert) => (
              <button
                key={alert.code}
                onClick={() => {
                  if (
                    navigationItems.some((item) => item.id === alert.target)
                  ) {
                    onNavigate(alert.target);
                    setAlertsOpen(false);
                  }
                }}
              >
                <span className={`alert-list-icon severity-${alert.severity}`}>
                  <Bell size={17} />
                </span>
                <span>
                  <strong>{alert.title}</strong>
                  <small>Xem chi tiết và xử lý</small>
                </span>
                <Badge variant={alert.severity}>{alert.count}</Badge>
                <ArrowUpRight size={17} />
              </button>
            ))}
          </div>
        )}
      </Modal>
    </header>
  );
};
