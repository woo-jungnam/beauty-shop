import React, { useState, useEffect, useRef } from 'react';
import './styles/customer-theme.css';
import './styles/customer-experience.css';
import { CustomerHeader } from './components/layout/CustomerHeader';
import { CustomerFooter } from './components/layout/CustomerFooter';
import { MiniCartDrawer } from './components/cart/MiniCartDrawer';
import { ChatbotSkincareWidget } from './components/ai/ChatbotSkincareWidget';

import { HomePage } from './pages/HomePage';
import { CatalogPage } from './pages/CatalogPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { SpaBookingPage } from './pages/SpaBookingPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { OrderSuccessPage } from './pages/OrderSuccessPage';
import { CustomerProfilePage } from './pages/CustomerProfilePage';

export const CustomerApp = () => {
  const getHashRoute = () => {
    return window.location.hash.replace(/^#\/?/, '').split('#')[0];
  };

  const [route, setRoute] = useState(getHashRoute);
  const mainRef = useRef(null);
  const previousRoute = useRef(route);

  useEffect(() => {
    if (previousRoute.current.split('?')[0] !== route.split('?')[0]) {
      mainRef.current?.focus({ preventScroll: true });
    }
    previousRoute.current = route;
  }, [route]);

  useEffect(() => {
    const updateTitle = (current) => {
      if (current.startsWith('product/')) document.title = 'Chi Tiết Sản Phẩm — BeautyShop';
      else if (current.startsWith('products') || current === 'catalog') document.title = 'Danh Mục Dược Mỹ Phẩm — BeautyShop';
      else if (current.startsWith('booking') || current === 'spa') document.title = 'Đặt Lịch Hẹn Spa & Trị Liệu — BeautyShop';
      else if (current.startsWith('checkout')) document.title = 'Thanh Toán Đơn Hàng — BeautyShop';
      else if (current.startsWith('order-success')) document.title = 'Đặt Hàng Thành Công — BeautyShop';
      else if (current.startsWith('profile')) document.title = 'Hồ Sơ & Vé Spa Điện Tử — BeautyShop';
      else document.title = 'BeautyShop — Mỹ Phẩm Chính Hãng & Clinic Chuẩn Y Khoa';
    };

    updateTitle(route);
    const handleHashChange = () => {
      const nextRoute = getHashRoute();
      const isCatalogTransition = (route.startsWith('products') || route === 'catalog') &&
                                 (nextRoute.startsWith('products') || nextRoute === 'catalog');
      const isProfileTransition = route.startsWith('profile') && nextRoute.startsWith('profile');
      setRoute(nextRoute);
      updateTitle(nextRoute);
      if (!isCatalogTransition && !isProfileTransition) {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    };
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, [route]);

  const navigate = (to, { scroll = true } = {}) => {
    window.location.hash = `/${to}`;
    setRoute(to);
    if (scroll) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  // Route dispatcher
  const renderCurrentPage = () => {
    // 1. Product Detail: product/123
    if (route.startsWith('product/')) {
      const id = route.split('/')[1]?.split(/[?#]/)[0];
      return <ProductDetailPage productId={id} onNavigate={navigate} />;
    }

    // 2. Catalog / Products
    if (route.startsWith('products') || route === 'catalog') {
      const qIndex = route.indexOf('?');
      const queryString = qIndex !== -1 ? route.substring(qIndex + 1) : '';
      return (
        <CatalogPage
          key="catalog-view"
          onNavigate={navigate}
          initialQuery={queryString}
        />
      );
    }

    // 3. Spa Booking
    if (route.startsWith('booking') || route === 'spa') {
      const serviceMatch = route.match(/serviceId=([^&]+)/);
      const serviceId = serviceMatch ? serviceMatch[1] : null;
      return <SpaBookingPage onNavigate={navigate} preselectedServiceId={serviceId} />;
    }

    // 4. Checkout
    if (route.startsWith('checkout')) {
      return <CheckoutPage onNavigate={navigate} />;
    }

    // 5. Order Success
    if (route.startsWith('order-success')) {
      const idMatch = route.match(/id=([^&]+)/);
      const codeMatch = route.match(/code=([^&]+)/);
      const methodMatch = route.match(/method=([^&]+)/);
      const amountMatch = route.match(/amount=([^&]+)/);
      return (
        <OrderSuccessPage
          onNavigate={navigate}
          orderId={idMatch ? decodeURIComponent(idMatch[1]) : null}
          orderCode={codeMatch ? decodeURIComponent(codeMatch[1]) : ''}
          paymentMethod={methodMatch ? decodeURIComponent(methodMatch[1]) : 'BANK'}
          amount={amountMatch ? Number(amountMatch[1]) : 0}
        />
      );
    }

    // 6. Profile / Digital Spa Tickets
    if (route.startsWith('profile')) {
      const tabMatch = route.match(/tab=([^&]+)/);
      const tab = tabMatch ? tabMatch[1] : 'overview';
      return <CustomerProfilePage onNavigate={navigate} initialTab={tab} />;
    }

    // Default: Home Page
    return <HomePage onNavigate={navigate} />;
  };

  return (
    <div className="customer-app">
      <a className="customer-skip-link" href="#customer-main" onClick={(event) => { event.preventDefault(); mainRef.current?.focus(); mainRef.current?.scrollIntoView({ block: 'start' }); }}>Bỏ qua điều hướng, đến nội dung</a>
      {/* 1. Global Luxury Navigation */}
      <CustomerHeader onNavigate={navigate} currentRoute={route} />

      {/* 2. Main Page Container */}
      <main id="customer-main" ref={mainRef} tabIndex={-1} style={{ flex: 1 }}>
        {renderCurrentPage()}
      </main>

      {/* 3. Global Mini-Cart Slide-out Drawer */}
      <MiniCartDrawer onNavigate={navigate} />

      {/* 4. AI Skincare Consultant Floating Widget */}
      <ChatbotSkincareWidget onNavigate={navigate} />

      {/* 5. Luxury Brand Footer */}
      <CustomerFooter onNavigate={navigate} />
    </div>
  );
};
