import React, { lazy, Suspense, useEffect, useRef, useState } from "react";
import { useAuth } from "./providers/AuthProvider";
import { Sidebar } from "../widgets/Sidebar";
import { Topbar } from "../widgets/Topbar";
import { LoginPage } from "../pages/LoginPage";
import { Loader2 } from "lucide-react";
import { getAdminSections } from "./adminNavigation";

const CustomerApp = lazy(() =>
  import("../customer/CustomerApp").then((module) => ({
    default: module.CustomerApp,
  })),
);

const SpaStaffApp = lazy(() =>
  import("../staff/SpaStaffApp").then((module) => ({
    default: module.SpaStaffApp,
  })),
);

const ADMIN_PAGES = {
  dashboard: lazy(() =>
    import("../pages/DashboardPage").then((module) => ({
      default: module.DashboardPage,
    })),
  ),
  orders: lazy(() =>
    import("../pages/OrdersPage").then((module) => ({
      default: module.OrdersPage,
    })),
  ),
  inventory: lazy(() =>
    import("../pages/InventoryPage").then((module) => ({
      default: module.InventoryPage,
    })),
  ),
  procurement: lazy(() =>
    import("../pages/ProcurementPage").then((module) => ({
      default: module.ProcurementPage,
    })),
  ),
  products: lazy(() =>
    import("../pages/ProductsPage").then((module) => ({
      default: module.ProductsPage,
    })),
  ),
  spa: lazy(() =>
    import("../pages/SpaPage").then((module) => ({ default: module.SpaPage })),
  ),
  "spa-reception": lazy(() =>
    import("../pages/SpaReceptionPage").then((module) => ({
      default: module.SpaReceptionPage,
    })),
  ),
  vouchers: lazy(() =>
    import("../pages/VouchersPage").then((module) => ({
      default: module.VouchersPage,
    })),
  ),
  reviews: lazy(() =>
    import("../pages/ReviewsPage").then((module) => ({
      default: module.ReviewsPage,
    })),
  ),
  users: lazy(() =>
    import("../pages/UsersPage").then((module) => ({
      default: module.UsersPage,
    })),
  ),
  crm: lazy(() =>
    import("../pages/CrmPage").then((module) => ({ default: module.CrmPage })),
  ),
  operations: lazy(() =>
    import("../pages/OperationsPage").then((module) => ({
      default: module.OperationsPage,
    })),
  ),
};

const readPreference = (key, fallback) => {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
};

const AdminShell = ({ tabName, navigationItems }) => {
  const [collapsed, setCollapsed] = useState(
    () =>
      readPreference("beautyshop_admin_sidebar", "expanded") === "collapsed",
  );
  const [mobileOpen, setMobileOpen] = useState(false);
  const [motionEnabled, setMotionEnabled] = useState(
    () => readPreference("beautyshop_admin_motion", "full") !== "reduced",
  );
  const mainRef = useRef(null);
  const previousTab = useRef(tabName);
  const Page = ADMIN_PAGES[tabName] || ADMIN_PAGES.dashboard;
  const currentTitle =
    navigationItems.find((item) => item.id === tabName)?.label || "Quản trị";

  useEffect(() => {
    if (previousTab.current !== tabName) {
      window.scrollTo({ top: 0, behavior: "instant" });
      mainRef.current?.focus({ preventScroll: true });
      previousTab.current = tabName;
    }
    document.title = `${currentTitle} · BeautyShop Atelier`;
    return () => {
      document.title = "BeautyShop";
    };
  }, [tabName, currentTitle]);

  useEffect(() => {
    const media = window.matchMedia("(min-width: 901px)");
    const handleResize = () => {
      if (media.matches) setMobileOpen(false);
    };
    media.addEventListener("change", handleResize);
    return () => media.removeEventListener("change", handleResize);
  }, []);

  const navigate = (tab) => {
    if (!navigationItems.some((item) => item.id === tab)) return;
    setMobileOpen(false);
    window.location.hash = `/admin/${tab}`;
  };
  const toggleCollapse = () => {
    const next = !collapsed;
    try {
      localStorage.setItem(
        "beautyshop_admin_sidebar",
        next ? "collapsed" : "expanded",
      );
    } catch {
      /* Keep the session preference. */
    }
    setCollapsed(next);
  };
  const toggleMotion = () => {
    const next = !motionEnabled;
    try {
      localStorage.setItem(
        "beautyshop_admin_motion",
        next ? "full" : "reduced",
      );
    } catch {
      /* Keep the session preference. */
    }
    setMotionEnabled(next);
  };

  return (
    <div
      className={`app-container admin-shell ${collapsed ? "sidebar-is-collapsed" : ""}`}
      data-motion={motionEnabled ? "full" : "reduced"}
    >
      <a
        className="admin-skip-link"
        href="#admin-main"
        onClick={(event) => {
          event.preventDefault();
          mainRef.current?.focus();
        }}
      >
        Chuyển đến nội dung chính
      </a>
      <div className="admin-ambient" aria-hidden="true" />
      <Sidebar
        activeTab={tabName}
        onSelectTab={navigate}
        collapsed={collapsed}
        onToggleCollapse={toggleCollapse}
        mobileOpen={mobileOpen}
        onCloseMobile={() => setMobileOpen(false)}
      />
      <div className="main-viewport" inert={mobileOpen || undefined}>
        <Topbar
          currentTitle={currentTitle}
          activeTab={tabName}
          navigationItems={navigationItems}
          onNavigate={navigate}
          onOpenMenu={() => setMobileOpen(true)}
          mobileOpen={mobileOpen}
          motionEnabled={motionEnabled}
          onToggleMotion={toggleMotion}
        />
        <main
          ref={mainRef}
          id="admin-main"
          className="admin-main"
          aria-label={currentTitle}
          tabIndex={-1}
        >
          <div key={tabName} className="admin-page-transition">
            <span className="admin-route-progress" aria-hidden="true" />
            <Suspense
              fallback={
                <div
                  className="content-container admin-page-loading"
                  role="status"
                >
                  <Loader2 size={24} className="animate-spin" />
                  <span>Đang mở không gian làm việc...</span>
                </div>
              }
            >
              <Page />
            </Suspense>
          </div>
        </main>
        <footer className="admin-workspace-footer">
          <span>
            BeautyShop <span className="footer-dot">/</span> Admin Atelier
          </span>
          <span>Chăm chút từng trải nghiệm.</span>
        </footer>
      </div>
    </div>
  );
};

export const App = () => {
  const { isAuthenticated, isOperator, isAdmin, isSpaReception, user, loading } = useAuth();
  const navigationItems = getAdminSections(user?.roles).flatMap(
    (section) => section.items,
  );
  const allowedTabs = navigationItems.map((item) => item.id);

  const getRawHash = () =>
    window.location.hash.replace(/^#\/?/, "").split("#")[0];
  const [currentHash, setCurrentHash] = useState(getRawHash);

  useEffect(() => {
    const syncHash = () => setCurrentHash(getRawHash());
    window.addEventListener("hashchange", syncHash);
    return () => window.removeEventListener("hashchange", syncHash);
  }, []);

  if (loading) {
    return (
      <div
        style={{
          minHeight: "100vh",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: "var(--color-primary-950)",
          color: "#FFFFFF",
          gap: "12px",
        }}
      >
        <Loader2
          size={28}
          className="animate-spin"
          color="var(--color-accent-500)"
        />
        <div
          style={{ fontSize: "13px", fontWeight: 600, letterSpacing: "0.04em" }}
        >
          ĐANG TẢI HỆ THỐNG BEAUTYSHOP...
        </div>
      </div>
    );
  }

  // Explicit login page route
  if (currentHash === "login" || currentHash === "admin/login") {
    return <LoginPage />;
  }

  // Check if current route is for Spa Reception portal
  const isReceptionRoute =
    currentHash.startsWith("reception") ||
    currentHash.startsWith("staff") ||
    currentHash === "admin/spa-reception";

  const isSpaStaffOnly = isSpaReception && !isAdmin;

  // Dedicated Spa Receptionist Portal for Spa staff or direct reception route
  if ((isSpaStaffOnly || isReceptionRoute) && isAuthenticated && isOperator) {
    return (
      <Suspense
        fallback={
          <div
            role="status"
            style={{
              minHeight: "100vh",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 12,
              color: "var(--sp-text-muted, #6B7280)",
              backgroundColor: "#F9F8F6"
            }}
          >
            <Loader2 size={24} className="animate-spin" aria-hidden="true" />
            <span>Đang mở Quầy Lễ Tân Spa...</span>
          </div>
        }
      >
        <SpaStaffApp
          onSwitchToAdmin={() => {
            window.location.hash = "/admin/dashboard";
          }}
        />
      </Suspense>
    );
  }

  // Determine whether current route is for Admin Management or Customer Storefront
  const isExplicitAdmin =
    currentHash.startsWith("admin") ||
    (currentHash !== "products" && Object.keys(ADMIN_PAGES).includes(currentHash));

  // If not admin route -> Render the Customer Storefront
  if (!isExplicitAdmin) {
    return (
      <Suspense
        fallback={
          <div
            role="status"
            style={{
              minHeight: "100vh",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 12,
              color: "var(--text-muted)",
            }}
          >
            <Loader2 size={24} className="animate-spin" aria-hidden="true" />
            <span>Đang mở BeautyShop...</span>
          </div>
        }
      >
        <CustomerApp />
      </Suspense>
    );
  }

  // If admin route requested but not logged in or not an operator -> Show Login Page
  if (!isAuthenticated || !isOperator) {
    return <LoginPage />;
  }

  // Extract active admin tab
  let tabName =
    currentHash.replace(/^admin\/?/, "") || allowedTabs[0] || "dashboard";
  if (!allowedTabs.includes(tabName)) {
    tabName = allowedTabs[0] || "dashboard";
  }

  return <AdminShell tabName={tabName} navigationItems={navigationItems} />;
};
