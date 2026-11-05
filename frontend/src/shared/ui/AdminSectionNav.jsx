import React, { useState, useRef, useEffect } from 'react';
import { ChevronDown, Check, Layers } from 'lucide-react';
import './AdminSectionNav.css';

/**
 * AdminSectionNav (Pattern 3: Header Dropdown Switcher + Breadcrumb)
 * Strictly NO EMOJI - Only clean SVG icons.
 * Replaces bulky horizontal tabs to maximize vertical & horizontal canvas for data tables.
 */
export const AdminSectionNav = ({
  rootTitle = 'Quản trị hệ thống',
  activeTab,
  onChange,
  items = [],
  subtitle = '',
  actions = null,
  showQuickPills = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef(null);

  const activeItem = items.find((it) => it.id === activeTab) || items[0] || {
    id: activeTab,
    label: 'Danh mục quản lý',
    icon: Layers,
    count: null
  };

  const ActiveIcon = activeItem.icon || Layers;

  // Handle outside click & escape key
  useEffect(() => {
    const handleMouseDown = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleMouseDown);
      document.addEventListener('keydown', handleKeyDown);
    }

    return () => {
      document.removeEventListener('mousedown', handleMouseDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const handleSelect = (id) => {
    if (id !== activeTab) {
      onChange(id);
    }
    setIsOpen(false);
  };

  return (
    <div className="admin-section-nav">
      <div className="admin-section-nav-left">
        {/* Breadcrumb + Dropdown Switcher */}
        <div className="admin-section-nav-breadcrumb">
          <span className="admin-section-nav-root">{rootTitle}</span>
          <span className="admin-section-nav-separator">/</span>

          <div className="admin-section-nav-dropdown-wrap" ref={containerRef}>
            <button
              type="button"
              className="admin-section-nav-trigger"
              onClick={() => setIsOpen(!isOpen)}
              aria-expanded={isOpen}
              title="Nhấn để chuyển sang phân mục quản lý khác"
            >
              <ActiveIcon size={16} className="admin-section-nav-trigger-icon" />
              <span className="admin-section-nav-trigger-title">{activeItem.label}</span>
              {activeItem.count !== undefined && activeItem.count !== null && (
                <span className="admin-section-nav-trigger-count">
                  {typeof activeItem.count === 'number'
                    ? activeItem.count.toLocaleString('vi-VN')
                    : activeItem.count}
                </span>
              )}
              <ChevronDown size={14} className="admin-section-nav-trigger-chevron" />
            </button>

            {isOpen && (
              <div className="admin-section-nav-popover" role="menu">
                <div className="admin-section-nav-popover-header">
                  <span>Chuyển phân mục</span>
                  <small>{items.length} mục</small>
                </div>

                <div className="admin-section-nav-popover-list">
                  {items.map((item) => {
                    const ItemIcon = item.icon || Layers;
                    const isSelected = item.id === activeTab;

                    return (
                      <button
                        key={item.id}
                        type="button"
                        className={`admin-section-nav-popover-item ${isSelected ? 'active' : ''}`}
                        onClick={() => handleSelect(item.id)}
                        role="menuitem"
                      >
                        <div className="admin-section-nav-item-icon-wrap">
                          <ItemIcon size={16} />
                        </div>

                        <div className="admin-section-nav-item-info">
                          <span className="admin-section-nav-item-label">{item.label}</span>
                          {item.description && (
                            <span className="admin-section-nav-item-desc">{item.description}</span>
                          )}
                        </div>

                        {item.count !== undefined && item.count !== null && (
                          <span className="admin-section-nav-item-count">
                            {typeof item.count === 'number'
                              ? item.count.toLocaleString('vi-VN')
                              : item.count}
                          </span>
                        )}

                        {isSelected && <Check size={14} className="admin-section-nav-item-check" />}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Subtitle / context description */}
        {subtitle && <p className="admin-section-nav-subtitle">{subtitle}</p>}

        {/* Optional compact quick pills */}
        {showQuickPills && (
          <div className="admin-section-nav-quick-pills">
            {items.map((item) => {
              const ItemIcon = item.icon || Layers;
              const isSelected = item.id === activeTab;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={`admin-section-nav-quick-pill ${isSelected ? 'active' : ''}`}
                  onClick={() => onChange(item.id)}
                >
                  <ItemIcon size={13} />
                  <span>{item.label}</span>
                  {item.count !== undefined && item.count !== null && (
                    <span style={{ opacity: 0.8, fontSize: 10 }}>({item.count})</span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Right Side Action Buttons */}
      {actions && <div className="admin-section-nav-actions">{actions}</div>}
    </div>
  );
};
