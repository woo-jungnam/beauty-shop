import React, { useState, useEffect, useRef } from "react";
import {
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Gem,
  X,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "../app/providers/AuthProvider";
import { adminItemKey, getAdminSections, getInitials } from "../app/adminNavigation";

export const Sidebar = ({
  activeItemKey,
  activeSubTab = null,
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
  const sections = getAdminSections(user?.roles, user?.permissions);
  const name = user?.fullName || user?.username || "BeautyShop";

  const [expandedItems, setExpandedItems] = useState(() => {
    const initial = {};
    if (activeItemKey) initial[activeItemKey] = true;
    return initial;
  });

  useEffect(() => {
    if (activeItemKey) {
      setExpandedItems((prev) => ({ ...prev, [activeItemKey]: true }));
    }
  }, [activeItemKey]);

  const toggleExpand = (id, event) => {
    event.stopPropagation();
    event.preventDefault();
    setExpandedItems((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

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
            <small>HỆ THỐNG QUẢN TRỊ</small>
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
            <strong>BeautyShop</strong>
            <span>Quản trị & Vận hành</span>
          </div>
          <ShieldCheck size={15} className="workspace-shield" />
        </div>
        <nav className="sidebar-nav" aria-label="Các phân hệ quản trị">
          {sections.map((section) => (
            <div className="sidebar-section" key={section.label}>
              <p className="sidebar-section-label">{section.label}</p>
              {section.items.map((item) => {
                const { id, label, icon: Icon, subTab, subItems } = item;
                const itemKey = adminItemKey(item);
                const hasSub = Array.isArray(subItems) && subItems.length > 0;
                const isExpanded = !!expandedItems[itemKey];
                const isParentActive = activeItemKey === itemKey;
                const isParentCurrent = isParentActive && !subItems?.some(sub => sub.id === activeSubTab);

                if (collapsed || !hasSub) {
                  return (
                    <button
                      key={itemKey}
                      type="button"
                      className={`sidebar-link ${isParentActive ? "is-active" : ""}`}
                      onClick={() => onSelectTab(id, subTab)}
                      aria-label={label}
                      aria-current={isParentActive ? "page" : undefined}
                      title={collapsed ? label : undefined}
                    >
                      <span className="nav-icon">
                        <Icon size={18} strokeWidth={1.7} />
                      </span>
                      <span className="nav-label">{label}</span>
                      <ChevronRight size={13} className="nav-chevron" />
                    </button>
                  );
                }

                return (
                  <div
                    key={itemKey}
                    className={`sidebar-split-item ${isParentActive ? "is-parent-active" : ""}`}
                  >
                    <div
                      className={`sidebar-split-row ${isParentCurrent ? "is-active" : ""}`}
                    >
                      <button
                        type="button"
                        className="sidebar-split-main"
                        onClick={() => onSelectTab(id, subTab)}
                        aria-label={label}
                        aria-current={isParentCurrent ? "page" : undefined}
                        title={`Vào không gian ${label}`}
                      >
                        <span className="nav-icon">
                          <Icon size={18} strokeWidth={1.7} />
                        </span>
                        <span className="nav-label">{label}</span>
                      </button>
                      <button
                        type="button"
                        className={`sidebar-split-arrow ${isExpanded ? "is-expanded" : ""}`}
                        onClick={(e) => toggleExpand(itemKey, e)}
                        aria-label={
                          isExpanded
                            ? `Thu gọn menu con ${label}`
                            : `Mở rộng menu con ${label}`
                        }
                        title={isExpanded ? "Thu gọn menu con" : "Mở rộng menu con"}
                      >
                        <ChevronRight size={13} className="nav-chevron-split" />
                      </button>
                    </div>

                    {isExpanded && (
                      <div
                        className="sidebar-sub-nav"
                        role="group"
                        aria-label={`Chức năng con của ${label}`}
                      >
                        {subItems.map((sub) => {
                          const isSubActive =
                            isParentActive && activeSubTab === sub.id;
                          return (
                            <button
                              key={sub.id}
                              type="button"
                              className={`sidebar-sub-link ${isSubActive ? "is-active" : ""}`}
                              onClick={() => onSelectTab(id, sub.id)}
                              aria-label={sub.label}
                              aria-current={isSubActive ? "page" : undefined}
                            >
                              <span className="sub-bullet" aria-hidden="true" />
                              <span className="sub-label">{sub.label}</span>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
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
