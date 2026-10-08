import React, { useEffect, useRef } from "react";
import {
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Gem,
  X,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "../app/providers/AuthProvider";
import { getAdminSections, getInitials } from "../app/adminNavigation";

export const Sidebar = ({
  activeTab,
  onSelectTab,
  collapsed,
  onToggleCollapse,
  mobileOpen,
  onCloseMobile,
}) => {
  const { user, isAdmin } = useAuth();
  const sidebarRef = useRef(null);
  const closeRef = useRef(onCloseMobile);
  useEffect(() => {
    closeRef.current = onCloseMobile;
  }, [onCloseMobile]);
  const sections = getAdminSections(user?.roles);
  const name = user?.fullName || user?.username || "BeautyShop";

  useEffect(() => {
    if (!mobileOpen) return;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    sidebarRef.current?.querySelector(".sidebar-mobile-close")?.focus();
    const handleKey = (event) => {
      if (event.key === "Escape") closeRef.current();
      if (event.key !== "Tab") return;
      const focusable = [
        ...sidebarRef.current.querySelectorAll("button, a[href]"),
      ].filter((node) => node.getClientRects().length && !node.disabled);
      const first = focusable[0];
      const last = focusable.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    };
    window.addEventListener("keydown", handleKey);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKey);
      previousFocus?.focus?.();
    };
  }, [mobileOpen]);

  return (
    <>
      {mobileOpen && (
        <div
          className="sidebar-scrim"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}
      <aside
        ref={sidebarRef}
        id="admin-navigation"
        className={`sidebar atelier-sidebar ${mobileOpen ? "is-mobile-open" : ""}`}
        aria-label="Điều hướng quản trị"
        role={mobileOpen ? "dialog" : undefined}
        aria-modal={mobileOpen || undefined}
      >
        <a
          className="sidebar-brand"
          href={`#/admin/${sections[0]?.items[0]?.id || "dashboard"}`}
          aria-label="BeautyShop — trang quản trị"
          onClick={onCloseMobile}
        >
          <span className="brand-mark">
            <Gem size={23} strokeWidth={1.6} />
          </span>
          <span className="brand-type">
            <strong>
              beautyshop<span>.</span>
            </strong>
            <small>THE ADMIN ATELIER</small>
          </span>
        </a>
        <button
          className="sidebar-collapse"
          onClick={onToggleCollapse}
          aria-label={
            collapsed ? "Mở rộng thanh điều hướng" : "Thu gọn thanh điều hướng"
          }
          title={collapsed ? "Mở rộng điều hướng" : "Thu gọn điều hướng"}
        >
          <ChevronLeft size={14} />
        </button>
        <button
          className="sidebar-mobile-close"
          onClick={onCloseMobile}
          aria-label="Đóng menu điều hướng"
        >
          <X size={20} />
        </button>
        <div className="sidebar-workspace">
          <span className="workspace-emblem">
            <span>B</span>
          </span>
          <div>
            <strong>BeautyShop Studio</strong>
            <span>Không gian quản trị</span>
          </div>
          <ShieldCheck size={15} className="workspace-shield" />
        </div>
        <nav className="sidebar-nav" aria-label="Các phân hệ quản trị">
          {sections.map((section) => (
            <div className="sidebar-section" key={section.label}>
              <p className="sidebar-section-label">{section.label}</p>
              {section.items.map(({ id, label, icon: Icon }) => (
                <button
                  key={id}
                  className={`sidebar-link ${activeTab === id ? "is-active" : ""}`}
                  onClick={() => onSelectTab(id)}
                  aria-label={label}
                  aria-current={activeTab === id ? "page" : undefined}
                  title={collapsed ? label : undefined}
                >
                  <span className="nav-icon">
                    <Icon size={18} strokeWidth={1.7} />
                  </span>
                  <span className="nav-label">{label}</span>
                  <ChevronRight size={13} className="nav-chevron" />
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <a
            href="#/"
            className="sidebar-store-link"
            aria-label="Ghé thăm cửa hàng"
            title={collapsed ? "Ghé thăm cửa hàng" : undefined}
          >
            <span className="store-link-icon">
              <ArrowUpRight size={20} />
            </span>
            <span>
              <strong>Ghé thăm cửa hàng</strong>
              <small>Trải nghiệm BeautyShop</small>
            </span>
            <ArrowUpRight size={14} className="store-link-arrow" />
          </a>
          <div className="sidebar-account">
            <span className="sidebar-avatar">{getInitials(name)}</span>
            <div>
              <strong>{name}</strong>
              <span>{isAdmin ? "Quản trị viên" : "Nhân viên vận hành"}</span>
            </div>
            <span
              className="session-dot"
              title="Phiên đăng nhập đang hoạt động"
            />
          </div>
          <div className="sidebar-signature">
            <span>CRAFTED FOR BEAUTY</span>
            <span>BS / 01</span>
          </div>
        </div>
      </aside>
    </>
  );
};
