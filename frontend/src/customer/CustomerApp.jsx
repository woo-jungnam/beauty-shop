import { OrderLookupPage } from './pages/OrderLookupPage';
import React, { useState, useEffect, useRef } from 'react';
import './styles/customer-theme.css';
import './styles/customer-experience.css';
import { CustomerHeader } from './components/layout/CustomerHeader';
import { CustomerFooter } from './components/layout/CustomerFooter';
import { MiniCartDrawer } from './components/cart/MiniCartDrawer';
import { ChatbotSkincareWidget } from './components/ai/ChatbotSkincareWidget';

import { HomePage } from './pages/HomePage';
import { CatalogPage } from './pages/CatalogPage';
import { ImageSearch } from '../features/image-search/ImageSearch';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { SpaBookingPage } from './pages/SpaBookingPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { OrderSuccessPage } from './pages/OrderSuccessPage';
import { CustomerProfilePage } from './pages/CustomerProfilePage';
import { PasswordRecoveryPage } from './components/account/AccountTools';
import { TreatmentWorkspace } from '../features/treatments/TreatmentWorkspace';
import { ServicePackages } from '../features/spa/ServicePackages';

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
      if (current.split('?')[0] === 'image-search') document.title = 'Tìm Sản Phẩm Bằng Ảnh — BeautyShop';
      else if (current.startsWith('product/')) document.title = 'Chi Tiết Sản Phẩm — BeautyShop';
      else if (current.startsWith('products') || current === 'catalog') document.title = 'Danh Mục Mỹ Phẩm — BeautyShop';
      else if (current.startsWith('booking') || current === 'spa') document.title = 'Đặt Lịch Hẹn Spa & Chăm Sóc Da — BeautyShop';
      else if (current.startsWith('checkout')) document.title = 'Thanh Toán Đơn Hàng — BeautyShop';
      else if (current.startsWith('order-success')) document.title = 'Đặt Hàng Thành Công — BeautyShop';
      else if (current.startsWith('profile')) document.title = 'Hồ Sơ & Vé Spa Điện Tử — BeautyShop';
      else if (current.startsWith('treatments')) document.title = 'Lộ Trình Chăm Sóc Da — BeautyShop';
      else document.title = 'BeautyShop — Mỹ Phẩm Chính Hãng & Spa Chăm Sóc Chuyên Sâu';
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

  const renderCurrentPage = () => {
    if (route.split('?')[0] === 'image-search') return <ImageSearch onNavigate={navigate} />;
    if (route.startsWith('order-lookup')) return <OrderLookupPage onNavigate={navigate} />;
    if (route.startsWith('recover-password') || route.startsWith('reset-password')) return <PasswordRecoveryPage />;
    if (route.startsWith('treatments')) {
      const params = new URLSearchParams(route.split('?')[1] || '');
      return <TreatmentWorkspace role="customer" initialCourseId={params.get('course')} initialTab={params.get('tab')} />;
    }
    if (route.startsWith('product/')) {
      const id = route.split('/')[1]?.split(/[?#]/)[0];
      const params = new URLSearchParams(route.split('?')[1] || '');
      return <ProductDetailPage productId={id} requestedVariantId={params.get('variantId')} onNavigate={navigate} />;
    }

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

    if (route.startsWith('service-packages')) return <ServicePackages onNavigate={navigate} />;
    if (route.startsWith('booking') || route === 'spa') {
      const serviceMatch = route.match(/serviceId=([^&]+)/);
      const serviceId = serviceMatch ? serviceMatch[1] : null;
      const params = new URLSearchParams(route.split('?')[1] || '');
      return <SpaBookingPage onNavigate={navigate} preselectedServiceId={serviceId} preselectedTicketId={params.get('ticketId')} />;
    }

    if (route.startsWith('checkout')) {
      return <CheckoutPage onNavigate={navigate} />;
    }

    if (route.startsWith('order-success')) {
      const idMatch = route.match(/id=([^&]+)/);
      const codeMatch = route.match(/code=([^&]+)/);
      const methodMatch = route.match(/method=([^&]+)/);
      const amountMatch = route.match(/amount=([^&]+)/);
      return (
        <OrderSuccessPage
          key={idMatch ? decodeURIComponent(idMatch[1]) : 'current-order'}
          onNavigate={navigate}
          orderId={idMatch ? decodeURIComponent(idMatch[1]) : null}
          orderCode={codeMatch ? decodeURIComponent(codeMatch[1]) : ''}
          paymentMethod={methodMatch ? decodeURIComponent(methodMatch[1]) : 'BANK'}
          amount={amountMatch ? Number(amountMatch[1]) : 0}
        />
      );
    }

    if (route.startsWith('profile')) {
      const tabMatch = route.match(/tab=([^&]+)/);
      const tab = tabMatch ? tabMatch[1] : 'overview';
      return <CustomerProfilePage onNavigate={navigate} initialTab={tab} />;
    }

    return <HomePage onNavigate={navigate} />;
  };

  return (
    <div className="customer-app">
      <a className="customer-skip-link" href="#customer-main" onClick={(event) => { event.preventDefault(); mainRef.current?.focus(); mainRef.current?.scrollIntoView({ block: 'start' }); }}>Bỏ qua điều hướng, đến nội dung</a>
      <CustomerHeader onNavigate={navigate} currentRoute={route} />

      <main id="customer-main" ref={mainRef} tabIndex={-1} style={{ flex: 1 }}>
        {renderCurrentPage()}
      </main>

      <MiniCartDrawer onNavigate={navigate} />

      <ChatbotSkincareWidget onNavigate={navigate} />

      <CustomerFooter onNavigate={navigate} />
    </div>
  );
};
