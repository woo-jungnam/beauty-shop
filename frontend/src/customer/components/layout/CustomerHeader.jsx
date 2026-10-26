import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight, Calendar, ChevronDown, Home, LayoutDashboard, LogOut, Menu,
  Package, Search, ShieldCheck, ShoppingBag, Sparkles, User, X,
} from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { useAuth } from '../../../app/providers/AuthProvider';
import { apiClient } from '../../../shared/api/client';
import { cleanDisplayName } from '../../../shared/utils/formatters';
import { useCustomerDialog } from '../ui/useCustomerDialog';
import { readCatalogFilters, catalogRoute } from '../../utils/catalogFilters';

const NAV_LINKS = [
  { id: 'home', label: 'Trang chủ', route: '', Icon: Home },
  { id: 'products', label: 'Sản phẩm', route: 'products', dropdown: 'categories' },
  { id: 'brands', label: 'Thương hiệu', route: 'products', dropdown: 'brands' },
  { id: 'deals', label: 'Ưu đãi hôm nay', route: 'products?filter=sale', Icon: Sparkles },
  { id: 'spa', label: 'Dịch vụ Spa', route: 'booking', dropdown: 'services' },
  { id: 'treatments', label: 'Lộ trình điều trị', route: 'treatments', Icon: Calendar },
  { id: 'orders', label: 'Tra cứu đơn hàng', route: 'order-lookup', Icon: Package },
];

export const CustomerHeader = ({ onNavigate, currentRoute = '' }) => {
  const { totalItems, setCartOpen } = useCustomerCart();
  const { user, isAuthenticated, isOperator, logout } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [openDropdown, setOpenDropdown] = useState(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [services, setServices] = useState([]);
  const headerRef = useRef(null);
  const dialogRef = useRef(null);
  const searchInputRef = useRef(null);
  const hoverTimer = useRef(null);
  const closeMobileMenu = () => setMobileOpen(false);
  useCustomerDialog(mobileOpen, dialogRef, closeMobileMenu);

  useEffect(() => {
    const header = headerRef.current;
    const updateHeight = () => document.documentElement.style.setProperty(
      '--customer-header-height', `${header?.offsetHeight || 0}px`,
    );
    const observer = new ResizeObserver(updateHeight);
    if (header) observer.observe(header);
    updateHeight();
    return () => {
      observer.disconnect();
      document.documentElement.style.removeProperty('--customer-header-height');
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      apiClient.get('/api/v1/categories'),
      apiClient.get('/api/v1/brands?size=12'),
      apiClient.get('/api/v1/spa/services'),
    ]).then(([categoryResult, brandResult, serviceResult]) => {
      if (cancelled) return;
      const getData = (result) => result.status === 'fulfilled'
        ? result.value?.data ?? result.value : [];
      const categoryData = getData(categoryResult);
      const brandData = getData(brandResult);
      const serviceData = getData(serviceResult);
      setCategories(Array.isArray(categoryData) ? categoryData : []);
      setBrands(Array.isArray(brandData?.content) ? brandData.content : Array.isArray(brandData) ? brandData : []);
      setServices(Array.isArray(serviceData) ? serviceData : []);
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    setOpenDropdown(null);
    setSearchOpen(false);
    setMobileOpen(false);
    setKeyword(new URLSearchParams(currentRoute.split('?')[1] || '').get('search') || '');
  }, [currentRoute]);

  useEffect(() => () => {
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
  }, []);

  useEffect(() => {
    const handleScroll = () => {
      if (openDropdown) setOpenDropdown(null);
      if (searchOpen) setSearchOpen(false);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, [openDropdown, searchOpen]);

  useEffect(() => {
    const closeOutside = (event) => {
      if (!headerRef.current?.contains(event.target)) {
        setOpenDropdown(null);
        setSearchOpen(false);
      }
    };
    const closeOnEscape = (event) => {
      if (event.key !== 'Escape') return;
      if (openDropdown) {
        headerRef.current?.querySelector(`[data-dropdown="${openDropdown}"]`)?.focus();
      }
      setOpenDropdown(null);
      setSearchOpen(false);
    };
    const desktop = window.matchMedia('(min-width: 901px)');
    const closeOnDesktop = () => { if (desktop.matches) setMobileOpen(false); };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    desktop.addEventListener('change', closeOnDesktop);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
      desktop.removeEventListener('change', closeOnDesktop);
    };
  }, [openDropdown]);

  const handleNavMouseEnter = (dropdown) => {
    if (!dropdown) {
      if (hoverTimer.current) clearTimeout(hoverTimer.current);
      hoverTimer.current = setTimeout(() => {
        setOpenDropdown((current) => (current === 'account' ? current : null));
      }, 150);
      return;
    }
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
    if (openDropdown && openDropdown !== 'account') {
      setOpenDropdown(dropdown);
      setSearchOpen(false);
    } else {
      hoverTimer.current = setTimeout(() => {
        setOpenDropdown(dropdown);
        setSearchOpen(false);
      }, 120);
    }
  };

  const handleNavMouseLeave = () => {
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
    hoverTimer.current = setTimeout(() => {
      setOpenDropdown((current) => (current === 'account' ? current : null));
    }, 180);
  };

  const suggestions = useMemo(() => [...new Set([
    ...brands.slice(0, 3).map((brand) => cleanDisplayName(brand.name)),
    ...categories.slice(0, 3).map((category) => cleanDisplayName(category.name)),
  ].filter(Boolean))], [brands, categories]);

  const navigate = (route) => {
    setOpenDropdown(null);
    setSearchOpen(false);
    setMobileOpen(false);
    onNavigate(route);
  };
  const search = (event) => {
    event.preventDefault();
    if (keyword.trim()) navigate(catalogFilters
      ? catalogRoute({ ...catalogFilters, keyword: keyword.trim(), page: 0 })
      : `products?search=${encodeURIComponent(keyword.trim())}`);
  };
  const catalogFilters = /^(products|catalog)(\?|$)/.test(currentRoute)
    ? readCatalogFilters(currentRoute.split('?')[1] || '') : null;
  const categoryRoute = (category) => catalogFilters ? catalogRoute({
    ...catalogFilters, categoryIds: [...new Set([...catalogFilters.categoryIds, String(category.id)])], page: 0,
  }) : `products?${new URLSearchParams({
    categoryId: String(category.id), name: cleanDisplayName(category.name),
  })}`;
  const brandRoute = (brand) => catalogFilters ? catalogRoute({
    ...catalogFilters, brandIds: [...new Set([...catalogFilters.brandIds, String(brand.id)])], page: 0,
  }) : `products?${new URLSearchParams({
    brandId: String(brand.id), brandName: cleanDisplayName(brand.name),
  })}`;
  const isActive = (link) => {
    const path = currentRoute.split('?')[0];
    if (link.id === 'home') return !path || path === 'home';
    if (link.id === 'deals') return currentRoute.includes('filter=sale');
    if (link.id === 'products') return /^(products|catalog|product\/)/.test(path)
      && !catalogFilters?.brandIds.length && !currentRoute.includes('filter=sale');
    if (link.id === 'brands') return Boolean(catalogFilters?.brandIds.length);
    if (link.id === 'spa') return /^(booking|spa)/.test(path);
    return path === 'profile' && currentRoute.includes('tab=orders');
  };

  const renderDropdown = (type) => {
    const items = type === 'categories' ? categories : type === 'brands' ? brands : services;
    return (
      <div id={`storefront-${type}`} className="storefront-dropdown storefront-catalog-dropdown">
        <div className="storefront-dropdown-heading">
          <strong>{type === 'categories' ? 'Mua sắm theo danh mục' : type === 'brands' ? 'Thương hiệu yêu thích' : 'Chọn dịch vụ chăm sóc da'}</strong>
          <button type="button" onClick={() => navigate(type === 'services' ? 'booking' : 'products')}>
            Xem tất cả <ArrowRight size={14} />
          </button>
        </div>
        <div className="storefront-dropdown-grid">
          {items.slice(0, 18).map((item) => (
            <a key={item.id} href={`#/${type === 'categories' ? categoryRoute(item) : type === 'brands' ? brandRoute(item) : item.serviceType === 'TREATMENT_COURSE' ? 'treatments' : `booking?serviceId=${item.id}`}`} onClick={() => { setOpenDropdown(null); setMobileOpen(false); }}>
              {cleanDisplayName(item.name)} <ArrowRight size={13} aria-hidden="true" />
            </a>
          ))}
        </div>
        {items.length === 0 && <p className="storefront-dropdown-empty">Khám phá {type === 'services' ? 'dịch vụ tại trang đặt lịch.' : 'toàn bộ sản phẩm tại cửa hàng.'}</p>}
      </div>
    );
  };

  return (
    <header className="customer-header storefront-header" ref={headerRef}>
      <div className="storefront-announcement">
        <div className="storefront-container">
          <span><ShieldCheck size={14} aria-hidden="true" /> Mỹ phẩm chính hãng · Chăm sóc da cùng BeautyShop</span>
          {!isAuthenticated && <a href="#/recover-password">Quên mật khẩu?</a>}
          <a href="tel:18006324">Hỗ trợ: 1800 6324</a>
        </div>
      </div>

      <div className="storefront-container storefront-main-row">
        <a className="storefront-brand" href="#/" aria-label="BeautyShop — Trang chủ">
          <span>BEAUTY<span>SHOP</span><i aria-hidden="true">✦</i></span>
          <small>Chăm sóc da, yêu thương mình</small>
        </a>

        <div className="storefront-search">
          <form role="search" onSubmit={search}>
            <Search size={19} aria-hidden="true" />
            <input ref={searchInputRef} type="search" aria-label="Tìm kiếm sản phẩm" placeholder="Tìm sản phẩm, thương hiệu, hoạt chất…" value={keyword} onChange={(event) => setKeyword(event.target.value)} onFocus={() => { setSearchOpen(true); setOpenDropdown(null); }} />
            {keyword && <button className="storefront-search-clear" type="button" aria-label="Xóa từ khóa tìm kiếm" onClick={() => { setKeyword(''); searchInputRef.current?.focus(); }}><X size={16} /></button>}
            <button className="storefront-search-submit" type="submit" aria-label="Tìm kiếm"><ArrowRight size={20} /></button>
          </form>
          {searchOpen && suggestions.length > 0 && (
            <div className="storefront-dropdown storefront-suggestions">
              <strong>Gợi ý tìm kiếm</strong>
              <div>{suggestions.map((suggestion) => <button key={suggestion} type="button" onClick={() => navigate(`products?search=${encodeURIComponent(suggestion)}`)}>{suggestion}</button>)}</div>
            </div>
          )}
        </div>

        <div className="storefront-actions">
          <button type="button" className="storefront-action" onClick={() => navigate('image-search')}>Tìm bằng ảnh</button>
          <button type="button" className="storefront-booking" onClick={() => navigate('booking')}><Calendar size={18} /><span>Đặt lịch Spa</span></button>
          <button type="button" className="storefront-action storefront-cart" aria-label={`Giỏ hàng${totalItems > 0 ? `, ${totalItems} sản phẩm` : ''}`} onClick={() => { setMobileOpen(false); setOpenDropdown(null); setCartOpen(true); }}>
            <ShoppingBag size={21} /><span>Giỏ hàng</span>{totalItems > 0 && <b>{totalItems}</b>}
          </button>
          <div className="storefront-account">
            <button type="button" className="storefront-action" data-dropdown="account" aria-label={isAuthenticated ? 'Mở menu tài khoản' : 'Đăng nhập'} aria-expanded={isAuthenticated ? openDropdown === 'account' : undefined} aria-controls={isAuthenticated ? 'storefront-account-menu' : undefined} onClick={() => isAuthenticated ? setOpenDropdown(openDropdown === 'account' ? null : 'account') : navigate('login')}>
              <User size={21} /><span>{isAuthenticated ? user?.fullName?.split(' ').pop() || 'Tài khoản' : 'Đăng nhập'}</span>
            </button>
            {openDropdown === 'account' && isAuthenticated && (
              <div id="storefront-account-menu" className="storefront-dropdown storefront-account-menu">
                <strong>{user?.fullName || user?.username || 'Tài khoản của tôi'}</strong>
                <button type="button" onClick={() => navigate('profile')}><User size={16} /> Tài khoản của tôi</button>
                <button type="button" onClick={() => navigate('profile?tab=orders')}><Package size={16} /> Đơn hàng của tôi</button>
                <button type="button" onClick={() => navigate('profile?tab=tickets')}><Calendar size={16} /> Vé Spa & lịch hẹn</button>
                {isOperator && <button type="button" onClick={() => navigate('admin/dashboard')}><LayoutDashboard size={16} /> Quản trị hệ thống</button>}
                <button type="button" onClick={() => { setOpenDropdown(null); logout(); }}><LogOut size={16} /> Đăng xuất</button>
              </div>
            )}
          </div>
          <button type="button" className="storefront-action storefront-menu-toggle" aria-label="Mở menu điều hướng" aria-expanded={mobileOpen} aria-controls="storefront-mobile-menu" onClick={() => { setOpenDropdown(null); setMobileOpen(true); }}><Menu size={23} /></button>
        </div>
      </div>

      <nav className="storefront-nav" aria-label="Điều hướng chính">
        <div className="storefront-container storefront-nav-row">
          {NAV_LINKS.map((link) => (
            <div
              className="storefront-nav-group"
              key={link.id}
              onMouseEnter={() => handleNavMouseEnter(link.dropdown)}
              onMouseLeave={handleNavMouseLeave}
            >
              <a className={`storefront-nav-link ${isActive(link) ? 'is-active' : ''}`} href={`#/${link.route}`} aria-current={isActive(link) ? 'page' : undefined}>{link.Icon && <link.Icon size={16} aria-hidden="true" />}{link.label}</a>
              {link.dropdown && <button type="button" className="storefront-dropdown-toggle" data-dropdown={link.dropdown} aria-label={`Mở danh sách ${link.label.toLowerCase()}`} aria-expanded={openDropdown === link.dropdown} aria-controls={`storefront-${link.dropdown}`} onClick={() => { setSearchOpen(false); setOpenDropdown(openDropdown === link.dropdown ? null : link.dropdown); }}><ChevronDown size={14} /></button>}
              {openDropdown === link.dropdown && link.dropdown && renderDropdown(link.dropdown)}
            </div>
          ))}
          <span className="storefront-nav-note"><ShieldCheck size={15} /> An tâm mua sắm</span>
        </div>
      </nav>

      {mobileOpen && (
        <div className="storefront-mobile-overlay" onClick={(event) => { if (event.target === event.currentTarget) closeMobileMenu(); }}>
          <section ref={dialogRef} id="storefront-mobile-menu" className="storefront-mobile-panel" role="dialog" aria-modal="true" aria-labelledby="storefront-menu-title" tabIndex={-1}>
            <div className="storefront-mobile-heading"><div><small>BEAUTYSHOP</small><h2 id="storefront-menu-title">Khám phá cửa hàng</h2></div><button type="button" className="storefront-action" aria-label="Đóng menu điều hướng" onClick={closeMobileMenu}><X size={22} /></button></div>
            <nav aria-label="Điều hướng trên điện thoại">
              {NAV_LINKS.map((link) => <a key={link.id} href={`#/${link.route}`} aria-current={isActive(link) ? 'page' : undefined} onClick={closeMobileMenu}>{link.label}<ArrowRight size={17} /></a>)}
            </nav>
            {categories.length > 0 && <div className="storefront-mobile-categories"><strong>Danh mục sản phẩm</strong>{categories.slice(0, 8).map((category) => <a key={category.id} href={`#/${categoryRoute(category)}`} onClick={closeMobileMenu}>{cleanDisplayName(category.name)}</a>)}</div>}
            <div className="storefront-mobile-footer">
              <button type="button" className="btn-luxury-primary" onClick={() => navigate(isAuthenticated ? 'profile' : 'login')}><User size={18} /> {isAuthenticated ? 'Tài khoản của tôi' : 'Đăng nhập / Đăng ký'}</button>
              <a href="tel:18006324">Cần hỗ trợ? Gọi 1800 6324</a>
            </div>
          </section>
        </div>
      )}
    </header>
  );
};
