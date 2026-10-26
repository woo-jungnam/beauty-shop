import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Sparkles,
  ArrowRight,
  Calendar,
  Clock,
  ShieldCheck,
  Flame,
  CheckCircle2,
  ChevronRight,
  ChevronLeft,
  Star,
  Zap,
  Award,
  Check,
  Search
} from 'lucide-react';
import { ProductCard } from '../components/product/ProductCard';
import { ProductCardSkeleton } from '../components/product/ProductCardSkeleton';
import { ScrollProgressTop } from '../components/ui/ScrollProgressTop';
import { QuickViewModal } from '../components/product/QuickViewModal';
import { ToastNotification } from '../components/ui/ToastNotification';
import { useCustomerCart } from '../stores/customerCartStore';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import { getGuestSessionId } from '../../shared/utils/session';
import { useHomeSectionLimits } from '../components/ui/useHomeSectionLimits';
import heroImage from '../../assets/hero.png';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

const CATEGORY_PLACEHOLDER = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400"><rect width="400" height="400" fill="#FFF5F7"/><g fill="none" stroke="#D45D79" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"><circle cx="200" cy="180" r="55"/><path d="M160 270c0-22 18-36 40-36s40 14 40 36"/></g><text x="200" y="325" text-anchor="middle" fill="#8C2A47" font-family="Arial,sans-serif" font-size="18" font-weight="600">Danh mục</text></svg>')}`;
const BRAND_PLACEHOLDER = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400"><rect width="400" height="400" fill="#F9FAFB"/><g fill="none" stroke="#6B7280" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"><rect x="120" y="140" width="160" height="120" rx="16"/><path d="M160 200h80"/></g><text x="200" y="310" text-anchor="middle" fill="#4B5563" font-family="Arial,sans-serif" font-size="18" font-weight="600">Thương hiệu</text></svg>')}`;
const SPA_PLACEHOLDER = '/images/spa/facial-care.jpg';

const BrandCardLogo = React.memo(({ brand, logoUrl }) => {
  const [imgError, setImgError] = useState(false);
  const isFallback = !logoUrl || logoUrl.includes('data:image/svg') || imgError;

  if (isFallback) {
    return (
      <span className="brand-logo-text">
        {brand?.name}
      </span>
    );
  }

  return (
    <img
      src={logoUrl}
      alt={brand?.name || 'Thương hiệu'}
      loading="lazy"
      onError={() => setImgError(true)}
      className="brand-logo-img"
    />
  );
});

export const HomePage = ({ onNavigate }) => {
  const homePageRef = useRef(null);
  const sectionLimits = useHomeSectionLimits(homePageRef);
  const { addItem } = useCustomerCart();
  const [products, setProducts] = useState([]);
  const [spaServices, setSpaServices] = useState([]);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [backendBanners, setBackendBanners] = useState([]);
  const [recommendedProducts, setRecommendedProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  const [quickViewProduct, setQuickViewProduct] = useState(null);
  const [toasts, setToasts] = useState([]);

  const addToast = (title, message, type = 'success') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, title, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3500);
  };

  const removeToast = (id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  };

  const [activeHeroSlide, setActiveHeroSlide] = useState(0);
  const [isHeroHovered, setIsHeroHovered] = useState(false);

  const [clearanceProducts, setClearanceProducts] = useState([]);
  const [clearanceLoading, setClearanceLoading] = useState(true);
  const [clearanceError, setClearanceError] = useState('');
  const [clearanceAttempt, setClearanceAttempt] = useState(0);

  const [catalogCategoryFilter, setCatalogCategoryFilter] = useState('ALL');
  const [categoryProducts, setCategoryProducts] = useState(null);
  const [loadingCategoryProducts, setLoadingCategoryProducts] = useState(false);
  const [catalogSearchTerm, setCatalogSearchTerm] = useState('');
  const [catalogSortBy, setCatalogSortBy] = useState('DEFAULT');

  const [activeSpaCategory, setActiveSpaCategory] = useState('ALL');

  const FLASH_PAGE_SIZE = 6;
  const [currentFlashTab, setCurrentFlashTab] = useState(0);
  const [isFlashHovered, setIsFlashHovered] = useState(false);

  const totalFlashTabs = useMemo(() => {
    return Math.max(1, Math.ceil(clearanceProducts.length / FLASH_PAGE_SIZE));
  }, [clearanceProducts.length]);

  useEffect(() => {
    if (currentFlashTab >= totalFlashTabs) {
      setCurrentFlashTab(0);
    }
  }, [totalFlashTabs, currentFlashTab]);

  useEffect(() => {
    if (totalFlashTabs <= 1 || isFlashHovered) return;
    const timer = setInterval(() => {
      setCurrentFlashTab(prev => (prev + 1) % totalFlashTabs);
    }, 5000);
    return () => clearInterval(timer);
  }, [totalFlashTabs, isFlashHovered]);

  const currentFlashProducts = useMemo(() => {
    if (!clearanceProducts || clearanceProducts.length === 0) return [];
    const start = currentFlashTab * FLASH_PAGE_SIZE;
    return clearanceProducts.slice(start, start + FLASH_PAGE_SIZE);
  }, [clearanceProducts, currentFlashTab]);

  const marqueeBrands = useMemo(() => {
    if (!brands || brands.length === 0) return [];
    const repeats = brands.length < 8 ? 4 : 2;
    return Array.from({ length: repeats }, () => brands).flat();
  }, [brands]);

  useEffect(() => {
    let isMounted = true;
    const fetchData = async () => {
      setLoading(true);
      try {
        const guestSessionId = getGuestSessionId();
        const [prodRes, spaRes, catRes, brandRes, bannerRes, recRes] = await Promise.allSettled([
          apiClient.get('/api/v1/products?size=60'),
          apiClient.get('/api/v1/spa/services'),
          apiClient.get('/api/v1/categories'),
          apiClient.get('/api/v1/brands?size=100'),
          apiClient.get('/api/v1/banners'),
          apiClient.get(`/api/v1/products/recommended-for-you?limit=8&sessionId=${encodeURIComponent(guestSessionId)}`)
        ]);

        if (isMounted) {
          if (prodRes.status === 'fulfilled') {
            const list = prodRes.value?.data?.content || prodRes.value?.content || [];
            setProducts(list);
          }
          if (recRes.status === 'fulfilled') {
            const rList = recRes.value?.data || recRes.value || [];
            setRecommendedProducts(Array.isArray(rList) ? rList : (rList?.content || []));
          }
          if (spaRes.status === 'fulfilled') {
            const sList = spaRes.value?.data || spaRes.value || [];
            setSpaServices(Array.isArray(sList) ? sList : []);
          }
          if (catRes.status === 'fulfilled') {
            const cList = catRes.value?.data || catRes.value || [];
            setCategories(Array.isArray(cList) ? cList : []);
          }
          if (brandRes.status === 'fulfilled') {
            const bList = brandRes.value?.data?.content || brandRes.value?.content || brandRes.value?.data || [];
            setBrands(Array.isArray(bList) ? bList : []);
          }
          if (bannerRes.status === 'fulfilled') {
            const bnList = bannerRes.value?.data || bannerRes.value || [];
            setBackendBanners(Array.isArray(bnList) ? bnList : []);
          }
        }
      } catch (err) {
        console.error('Error fetching home data', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    fetchData();
    return () => { isMounted = false; };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setClearanceLoading(true);
    setClearanceError('');
    apiClient.get('/api/v1/products/expiring-soon?limit=8&thresholdDays=90', { signal: controller.signal })
      .then(response => {
        const data = response?.data ?? response;
        const list = Array.isArray(data) ? data : data?.content;
        if (response?.success === false || !Array.isArray(list)) throw new Error('Invalid clearance response');
        if (!controller.signal.aborted) setClearanceProducts(list);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setClearanceProducts([]);
          setClearanceError('Chưa tải được sản phẩm xả kho. Vui lòng thử lại.');
        }
      })
      .finally(() => { if (!controller.signal.aborted) setClearanceLoading(false); });
    return () => controller.abort();
  }, [clearanceAttempt]);

  useEffect(() => {
    if (catalogCategoryFilter === 'ALL') {
      setCategoryProducts(null);
      return;
    }
    let isCancelled = false;
    const fetchCategoryProducts = async () => {
      setLoadingCategoryProducts(true);
      try {
        const res = await apiClient.get(`/api/v1/products/category/${catalogCategoryFilter}?size=60`);
        const list = res?.data?.content || res?.content || res?.data || [];
        if (!isCancelled) {
          setCategoryProducts(Array.isArray(list) ? list : []);
        }
      } catch (err) {
        console.warn('Failed to load category products', err);
        if (!isCancelled) setCategoryProducts([]);
      } finally {
        if (!isCancelled) setLoadingCategoryProducts(false);
      }
    };
    fetchCategoryProducts();
    return () => { isCancelled = true; };
  }, [catalogCategoryFilter]);

  const allStoreProducts = useMemo(() => {
    let list = categoryProducts !== null ? [...categoryProducts] : [...products];
    if (catalogSearchTerm.trim()) {
      const searchLower = catalogSearchTerm.trim().toLowerCase();
      list = list.filter(p =>
        (p.name || '').toLowerCase().includes(searchLower) ||
        (p.brandName || '').toLowerCase().includes(searchLower) ||
        (p.shortDescription || '').toLowerCase().includes(searchLower)
      );
    }
    if (catalogSortBy === 'PRICE_ASC') {
      list.sort((a, b) => Number(a.minPrice || a.basePrice || 0) - Number(b.minPrice || b.basePrice || 0));
    } else if (catalogSortBy === 'PRICE_DESC') {
      list.sort((a, b) => Number(b.minPrice || b.basePrice || 0) - Number(a.minPrice || a.basePrice || 0));
    } else if (catalogSortBy === 'RATING') {
      list.sort((a, b) => Number(b.averageRating || 0) - Number(a.averageRating || 0));
    }
    return list;
  }, [products, categoryProducts, catalogSearchTerm, catalogSortBy]);

  const randomProducts = useMemo(() => {
    if (products.length === 0) return [];
    const arr = [...products];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
  }, [products]);

  const openCatalog = () => {
    const params = new URLSearchParams();
    if (catalogCategoryFilter !== 'ALL') params.set('categoryId', catalogCategoryFilter);
    if (catalogSearchTerm.trim()) params.set('search', catalogSearchTerm.trim());
    const sort = { PRICE_ASC: 'price_asc', PRICE_DESC: 'price_desc', RATING: 'rating' }[catalogSortBy];
    if (sort) params.set('sort', sort);
    onNavigate(`products${params.size ? `?${params.toString()}` : ''}`);
  };

  const dynamicCatalogCategories = useMemo(() => {
    const list = [{ id: 'ALL', label: `Tất cả sản phẩm (${products.length})` }];
    if (categories.length > 0) {
      categories.slice(0, 7).forEach(cat => {
        list.push({ id: String(cat.id), label: cat.name });
      });
    }
    return list;
  }, [categories, products.length]);

  const categoryProductCounts = useMemo(() => {
    const counts = {};
    if (products && products.length > 0) {
      products.forEach(p => {
        if (p.categoryId) {
          counts[p.categoryId] = (counts[p.categoryId] || 0) + 1;
        }
      });
    }
    return counts;
  }, [products]);

  const CATEGORY_PAGE_SIZE = 8;
  const [currentCategoryTab, setCurrentCategoryTab] = useState(0);
  const [isCategoryHovered, setIsCategoryHovered] = useState(false);

  const totalCategoryTabs = useMemo(() => {
    return Math.max(1, Math.ceil(categories.length / CATEGORY_PAGE_SIZE));
  }, [categories.length]);

  useEffect(() => {
    if (currentCategoryTab >= totalCategoryTabs) {
      setCurrentCategoryTab(0);
    }
  }, [totalCategoryTabs, currentCategoryTab]);

  useEffect(() => {
    if (totalCategoryTabs <= 1 || isCategoryHovered) return;
    const timer = setInterval(() => {
      setCurrentCategoryTab(prev => (prev + 1) % totalCategoryTabs);
    }, 5000);
    return () => clearInterval(timer);
  }, [totalCategoryTabs, isCategoryHovered]);

  const currentCategoryBatch = useMemo(() => {
    if (!categories || categories.length === 0) return [];
    const start = currentCategoryTab * CATEGORY_PAGE_SIZE;
    return categories.slice(start, start + CATEGORY_PAGE_SIZE);
  }, [categories, currentCategoryTab]);

  const getCategoryImage = (cat) => {
    const raw = cat?.imageUrl || cat?.thumbnailUrl || cat?.image;
    if (typeof raw === 'string' && raw.trim().length > 0) {
      return resolveMediaUrl(raw);
    }
    return CATEGORY_PLACEHOLDER;
  };

  const getSpaServiceImage = (service) => {
    const raw = service?.thumbnailUrl || service?.imageUrl || service?.image;
    if (typeof raw === 'string' && raw.trim().length > 0) {
      return resolveMediaUrl(raw);
    }
    return SPA_PLACEHOLDER;
  };

  const getBrandImage = (brand) => {
    const raw = brand?.logoUrl || brand?.imageUrl || brand?.image;
    if (typeof raw === 'string' && raw.trim().length > 0) {
      return resolveMediaUrl(raw);
    }
    return BRAND_PLACEHOLDER;
  };

  const DEFAULT_HERO_SLIDES = [
    {
      badge: 'CHÍNH HÃNG 100%',
      title: 'Mỹ Phẩm Chính Hãng & Dịch Vụ Spa Chuyên Sâu.',
      description: 'Cam kết 100% hàng thật, date xa rõ ràng, nguồn gốc minh bạch. Dịch vụ tư vấn routine cùng chuyên viên tận tâm.',
      ctaPrimary: 'Mua Sắm Ngay',
      ctaSecondary: 'Xem Tất Cả Mỹ Phẩm',
      targetPrimary: 'products',
      targetSecondary: '#store-catalog',
      stat1: '100% Chính Hãng',
      stat2: 'Tiêu Chuẩn Quốc Tế',
      stat3: '24/7 Tư Vấn Tận Tâm',
      image: heroImage,
    },
    {
      badge: 'XẢ KHO CẬN DATE',
      title: 'Xả Kho Cận Date - Ưu Đãi Giá Tốt.',
      description: 'Cơ hội sở hữu tinh chất phục hồi da B5, kem chống nắng kiềm dầu quang phổ rộng và kem dưỡng ẩm từ các thương hiệu chính hãng.',
      ctaPrimary: 'Săn Deal Ngay',
      ctaSecondary: 'Xem Tất Cả Mỹ Phẩm',
      targetPrimary: '#flash-sale',
      targetSecondary: '#store-catalog',
      stat1: '100% Minh Bạch HSD',
      stat2: '31–90 Ngày Hạn Dùng',
      stat3: 'Giá Tốt Trực Tiếp',
      image: heroImage,
    },
    {
      badge: 'BEAUTY & SPA',
      title: 'Dịch Vụ Phục Hồi & Chăm Sóc Da Chuyên Sâu.',
      description: 'Quy trình Aqua Peel làm sạch sâu đa tầng, điện di vi điểm căng bóng và chăm sóc da mụn với công nghệ hiện đại.',
      ctaPrimary: 'Đặt Lịch Hẹn Ngay',
      ctaSecondary: 'Xem Bảng Giá Spa',
      targetPrimary: 'booking',
      targetSecondary: '#spa-section',
      stat1: '100% Chuyên Viên Spa',
      stat2: 'Quy Trình Chuyên Sâu',
      stat3: 'Chăm Sóc Toàn Diện',
      image: '/images/spa/spa-ritual.jpg',
    }
  ];

  const DEFAULT_SIDE_BANNERS = [
    {
      eyebrow: 'XẢ KHO CẬN DATE',
      title: 'Xả Kho Cận Date Giá Tốt',
      desc: 'Minh bạch hạn dùng, lô 31–90 ngày với giá ưu đãi trực tiếp.',
      cta: 'Săn deal',
      target: '#flash-sale',
      image: heroImage,
      Icon: Flame
    },
    {
      eyebrow: 'BEAUTY & SPA',
      title: 'Soi Da 3D & Chăm Sóc Da Chuyên Sâu',
      desc: 'Đặt lịch Aqua Peel, điện di phục hồi và tư vấn routine cùng chuyên viên.',
      cta: 'Đặt lịch',
      target: 'booking',
      image: '/images/spa/facial-care.jpg',
      Icon: Calendar
    }
  ];

  const heroSlides = useMemo(() => {
    if (backendBanners && backendBanners.length > 0) {
      const slides = backendBanners.filter(b => b.position === 'HERO_SLIDE' || !b.position);
      if (slides.length > 0) {
        return slides.map(b => ({
          badge: b.badge || '',
          title: b.title,
          description: b.description || '',
          ctaPrimary: b.ctaText || 'Mua Sắm Ngay',
          ctaSecondary: 'Xem Tất Cả Mỹ Phẩm',
          targetPrimary: b.targetUrl || 'products',
          targetSecondary: '#store-catalog',
          stat1: '100% Chính Hãng',
          stat2: 'Tiêu Chuẩn Quốc Tế',
          stat3: '24/7 Tư Vấn Tận Tâm',
          image: resolveMediaUrl(b.imageUrl) || heroImage,
        }));
      }
    }
    return DEFAULT_HERO_SLIDES;
  }, [backendBanners]);

  const heroSideBanners = useMemo(() => {
    if (backendBanners && backendBanners.length > 0) {
      const sides = backendBanners.filter(b => b.position === 'HERO_SIDE');
      if (sides.length > 0) {
        return sides.map((b, idx) => ({
          eyebrow: b.badge || '',
          title: b.title,
          desc: b.description || '',
          cta: b.ctaText || (idx === 0 ? 'Săn deal' : 'Đặt lịch'),
          target: b.targetUrl || (idx === 0 ? '#flash-sale' : 'booking'),
          image: resolveMediaUrl(b.imageUrl) || (idx === 0 ? heroImage : '/images/spa/facial-care.jpg'),
          Icon: idx === 0 ? Flame : Calendar
        }));
      }
    }
    return DEFAULT_SIDE_BANNERS;
  }, [backendBanners]);

  const [sideSlideOffset, setSideSlideOffset] = useState(0);

  useEffect(() => {
    setSideSlideOffset(activeHeroSlide);
  }, [activeHeroSlide]);

  const displayedSideBanners = useMemo(() => {
    if (!heroSideBanners || heroSideBanners.length === 0) return [];
    if (heroSideBanners.length <= 2) {
      return heroSideBanners.map((b, i) => ({ ...b, slotIndex: i }));
    }
    const topIndex = sideSlideOffset % heroSideBanners.length;
    const bottomIndex = (sideSlideOffset + 1) % heroSideBanners.length;
    return [
      { ...heroSideBanners[topIndex], slotIndex: topIndex },
      { ...heroSideBanners[bottomIndex], slotIndex: bottomIndex }
    ];
  }, [heroSideBanners, sideSlideOffset]);

  useEffect(() => {
    if (isHeroHovered || heroSlides.length <= 1 || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const heroTimer = setInterval(() => {
      setActiveHeroSlide(prev => (prev + 1) % heroSlides.length);
    }, 5500);
    return () => clearInterval(heroTimer);
  }, [isHeroHovered, heroSlides.length]);

  const navigateHeroTarget = (target) => {
    if (target.startsWith('#')) {
      const el = document.querySelector(target);
      if (el) el.scrollIntoView({ behavior: 'smooth' });
    } else {
      onNavigate(target);
    }
  };

  const dynamicSpaCategories = useMemo(() => {
    const map = new Map();
    spaServices.forEach(s => {
      if (s.categoryId && s.categoryName && !map.has(s.categoryId)) {
        map.set(s.categoryId, s.categoryName);
      }
    });
    const list = [{ id: 'ALL', label: 'Tất cả liệu trình' }];
    map.forEach((name, id) => {
      list.push({ id: String(id), label: name });
    });
    return list;
  }, [spaServices]);

  const getFilteredSpa = () => {
    if (!spaServices || spaServices.length === 0) return [];
    if (activeSpaCategory === 'ALL') return spaServices.slice(0, sectionLimits.spa);
    return spaServices.filter(s => String(s.categoryId) === String(activeSpaCategory)).slice(0, sectionLimits.spa);
  };

  return (
    <div ref={homePageRef} className="customer-home-page" style={{ display: 'flex', flexDirection: 'column', gap: '40px', paddingBottom: '56px' }}>

      <section
        className="hero-slider-container"
        onMouseEnter={() => setIsHeroHovered(true)}
        onMouseLeave={() => setIsHeroHovered(false)}
        onFocusCapture={() => setIsHeroHovered(true)}
        onBlurCapture={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setIsHeroHovered(false); }}
        aria-label="Bộ sưu tập và ưu đãi nổi bật"
      >
        <div style={{ position: 'relative', zIndex: 3, width: '100%' }}>
          <div className="customer-container home-banner-block hero-content-grid">
            {(() => {
              const currentSlide = heroSlides[activeHeroSlide] || heroSlides[0] || {};

              return (
                <div className="home-banner-main" style={{
                  position: 'relative',
                  overflow: 'hidden',
                  borderRadius: '20px',
                  minHeight: '660px',
                  background: 'linear-gradient(135deg, #FFF7F9 0%, #FFF0F3 52%, #FDE9EE 100%)',
                  border: '1px solid #F5E2E8',
                  boxShadow: '0 8px 30px rgba(140, 42, 71, 0.06)',
                  display: 'grid',
                  gridTemplateColumns: 'minmax(0, 1.25fr) minmax(0, 1fr)',
                  alignItems: 'center'
                }}>
                  <div className="home-banner-main-content" style={{
                    padding: '52px 40px 52px 52px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    zIndex: 2
                  }}>
                    {currentSlide.badge && (
                      <div className="hero-eyebrow" style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '6px 16px',
                        borderRadius: '9999px',
                        backgroundColor: '#FFFFFF',
                        border: '1px solid #F4CBD4',
                        color: '#8C2A47',
                        fontSize: '11.5px',
                        fontWeight: 800,
                        letterSpacing: '0.06em',
                        marginBottom: '18px',
                        boxShadow: '0 2px 6px rgba(140, 42, 71, 0.05)',
                        alignSelf: 'flex-start'
                      }}>
                        <Sparkles size={13} color="#D45D79" />
                        <span>{currentSlide.badge}</span>
                      </div>
                    )}

                    <h1 style={{
                      fontFamily: 'var(--font-sans)',
                      fontSize: 'clamp(28px, 3.8vw, 44px)',
                      lineHeight: 1.24,
                      fontWeight: 800,
                      color: '#2A1820',
                      margin: '0 0 16px 0',
                      letterSpacing: '-0.02em'
                    }}>
                      {currentSlide.title}
                    </h1>

                    <p style={{
                      fontSize: '15px',
                      lineHeight: 1.7,
                      color: '#6E5660',
                      margin: '0 0 28px 0',
                      maxWidth: '520px'
                    }}>
                      {currentSlide.description}
                    </p>

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '14px', alignItems: 'center' }}>
                      <button
                        onClick={() => {
                          navigateHeroTarget(currentSlide.targetPrimary || 'products');
                        }}
                        style={{
                          padding: '14px 28px',
                          fontSize: '14.5px',
                          borderRadius: '8px',
                          fontWeight: 700,
                          backgroundColor: '#8C2A47',
                          color: '#FFFFFF',
                          border: 'none',
                          cursor: 'pointer',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '8px',
                          boxShadow: '0 4px 14px rgba(140, 42, 71, 0.22)',
                          transition: 'background-color 0.2s ease'
                        }}
                      >
                        <span>{currentSlide.ctaPrimary || 'Mua Sắm Ngay'}</span>
                        <ArrowRight size={16} />
                      </button>

                      <button
                        onClick={() => {
                          navigateHeroTarget(currentSlide.targetSecondary || '#store-catalog');
                        }}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: '#8C2A47',
                          fontSize: '14.5px',
                          fontWeight: 700,
                          cursor: 'pointer',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          padding: '8px 12px'
                        }}
                      >
                        <span>{currentSlide.ctaSecondary || 'Xem Tất Cả Mỹ Phẩm'}</span>
                        <ChevronRight size={16} />
                      </button>
                    </div>

                    {heroSlides.length > 1 && (
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px',
                        marginTop: '44px'
                      }}>
                        <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                          {heroSlides.map((_, idx) => (
                            <button
                              key={idx}
                              onClick={() => setActiveHeroSlide(idx)}
                              style={{
                                width: activeHeroSlide === idx ? '28px' : '8px',
                                height: '7px',
                                borderRadius: '9999px',
                                border: 'none',
                                backgroundColor: activeHeroSlide === idx ? '#8C2A47' : '#E2CFD5',
                                cursor: 'pointer',
                                transition: 'all 0.25s ease',
                                padding: 0
                              }}
                              title={`Slide ${idx + 1}`}
                            />
                          ))}
                        </div>
                        <div style={{ display: 'flex', gap: '6px', marginLeft: '6px' }}>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveHeroSlide(prev => (prev - 1 + heroSlides.length) % heroSlides.length);
                            }}
                            aria-label="Slide trước"
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '50%',
                              border: '1px solid #ECCFD6',
                              backgroundColor: '#FFFFFF',
                              color: '#8C2A47',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              cursor: 'pointer',
                              padding: 0
                            }}
                          >
                            <ChevronLeft size={15} />
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveHeroSlide(prev => (prev + 1) % heroSlides.length);
                            }}
                            aria-label="Slide tiếp theo"
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '50%',
                              border: '1px solid #ECCFD6',
                              backgroundColor: '#FFFFFF',
                              color: '#8C2A47',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              cursor: 'pointer',
                              padding: 0
                            }}
                          >
                            <ChevronRight size={15} />
                          </button>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="home-banner-main-visual" style={{
                    position: 'relative',
                    height: '100%',
                    minHeight: '660px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '32px 36px 32px 0'
                  }}>
                    <div style={{
                      width: '100%',
                      height: '560px',
                      borderRadius: '20px',
                      overflow: 'hidden',
                      boxShadow: '0 12px 32px rgba(140, 42, 71, 0.08)',
                      border: '2px solid #FFFFFF',
                      position: 'relative'
                    }}>
                      <img
                        src={currentSlide.image}
                        alt={currentSlide.title}
                        style={{
                          width: '100%',
                          height: '100%',
                          objectFit: 'cover',
                          objectPosition: 'center',
                          display: 'block'
                        }}
                      />
                    </div>
                  </div>
                </div>
              );
            })()}

            <div className="home-banner-side" style={{ display: 'grid', gap: '16px' }}>
              {displayedSideBanners.map(({ eyebrow, title, desc, cta, target, image, Icon, slotIndex }, idx) => {
                const isClearance = idx === 0;
                return (
                  <div
                    key={`${title}-${slotIndex ?? idx}`}
                    className="home-banner-small"
                    onClick={() => navigateHeroTarget(target)}
                    style={{
                      position: 'relative',
                      overflow: 'hidden',
                      minHeight: '322px',
                      padding: '28px 28px',
                      borderRadius: '20px',
                      cursor: 'pointer',
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between',
                      background: isClearance
                        ? 'linear-gradient(135deg, #FFF7F4 0%, #FFEDE7 100%)'
                        : 'linear-gradient(135deg, #F1FBF8 0%, #E2F6F0 100%)',
                      border: isClearance ? '1px solid #FCD4C8' : '1px solid #C4ECE0',
                      boxShadow: isClearance
                        ? '0 4px 16px rgba(234, 88, 12, 0.05)'
                        : '0 4px 16px rgba(15, 118, 110, 0.05)',
                      transition: 'all 0.25s ease'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '16px' }}>
                      <div style={{ flex: 1 }}>
                        {eyebrow && (
                          <div style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                            marginBottom: '12px',
                            color: isClearance ? '#C2410C' : '#0F766E',
                            backgroundColor: '#FFFFFF',
                            border: isClearance ? '1px solid #FDC3B3' : '1px solid #B4E8DA',
                            padding: '4px 12px',
                            borderRadius: '9999px',
                            fontSize: '11px',
                            fontWeight: 800,
                            letterSpacing: '0.06em'
                          }}>
                            <Icon size={13} />
                            <span>{eyebrow}</span>
                          </div>
                        )}
                        <h3 style={{
                          color: isClearance ? '#2E1912' : '#132A25',
                          fontSize: '20px',
                          fontWeight: 800,
                          lineHeight: 1.32,
                          margin: '0 0 8px 0'
                        }}>
                          {title}
                        </h3>
                        <p style={{
                          color: isClearance ? '#7A574D' : '#496E65',
                          fontSize: '13px',
                          lineHeight: 1.6,
                          margin: 0
                        }}>
                          {desc}
                        </p>
                      </div>

                      <div style={{
                        width: '92px',
                        height: '92px',
                        borderRadius: '16px',
                        overflow: 'hidden',
                        flexShrink: 0,
                        border: '2px solid #FFFFFF',
                        boxShadow: '0 6px 14px rgba(0, 0, 0, 0.07)'
                      }}>
                        <img
                          src={image}
                          alt={title}
                          style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                            display: 'block'
                          }}
                        />
                      </div>
                    </div>

                    <div style={{ marginTop: '18px' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '9px 18px',
                          borderRadius: '8px',
                          backgroundColor: '#FFFFFF',
                          border: isClearance ? '1px solid #F97316' : '1px solid #0D9488',
                          color: isClearance ? '#C2410C' : '#0F766E',
                          fontSize: '13px',
                          fontWeight: 700,
                          boxShadow: '0 2px 6px rgba(0, 0, 0, 0.04)'
                        }}
                      >
                        <span>{cta}</span>
                        <ArrowRight size={14} />
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="customer-container">
            <div className="hero-trust-bar">
              <div className="hero-trust-item">
                <div className="hero-trust-icon">
                  <ShieldCheck size={20} />
                </div>
                <div>
                  <div className="hero-trust-title">100% Chính Hãng</div>
                  <div className="hero-trust-desc">Tem phụ & hóa đơn đầy đủ</div>
                </div>
              </div>
              <div className="hero-trust-item">
                <div className="hero-trust-icon">
                  <Award size={20} />
                </div>
                <div>
                  <div className="hero-trust-title">Tiêu Chuẩn Quốc Tế</div>
                  <div className="hero-trust-desc">Mỹ phẩm chính ngạch nguồn gốc minh bạch</div>
                </div>
              </div>
              <div className="hero-trust-item">
                <div className="hero-trust-icon">
                  <Sparkles size={20} />
                </div>
                <div>
                  <div className="hero-trust-title">Tư Vấn Routine</div>
                  <div className="hero-trust-desc">Chuyên viên tư vấn routine tận tâm</div>
                </div>
              </div>
              <div className="hero-trust-item">
                <div className="hero-trust-icon">
                  <Clock size={20} />
                </div>
                <div>
                  <div className="hero-trust-title">Giao Nhanh 2H</div>
                  <div className="hero-trust-desc">Nội thành hỏa tốc nhận liền</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section
        id="flash-sale"
        className="customer-container"
        onMouseEnter={() => setIsFlashHovered(true)}
        onMouseLeave={() => setIsFlashHovered(false)}
      >
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: '2px',
          border: '1px solid #FECACA',
          borderTop: '3px solid #DC2626',
          padding: 'clamp(12px, 1.5vw, 20px)',
          boxShadow: '0 2px 4px rgba(220, 38, 38, 0.04)'
        }}>
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
            marginBottom: '20px',
            paddingBottom: '16px',
            borderBottom: '1px solid var(--c-border-subtle)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
              <h2 style={{
                margin: 0,
                color: '#DC2626',
                fontSize: '20px',
                fontWeight: 800,
                fontFamily: 'var(--font-sans)',
                letterSpacing: '-0.01em'
              }}>
                XẢ KHO CẬN DATE • GIÁ SỐC ONLINE
              </h2>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
              <p style={{ margin: 0, fontSize: '13px', color: 'var(--c-text-muted)' }}>
                Lô còn 31–90 ngày, giá ưu đãi do cửa hàng đặt.
              </p>

              {totalFlashTabs > 1 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <div className="flash-tab-pills">
                    {Array.from({ length: totalFlashTabs }, (_, idx) => (
                      <button
                        key={idx}
                        type="button"
                        className={`flash-tab-indicator ${currentFlashTab === idx ? 'is-active' : ''}`}
                        onClick={() => setCurrentFlashTab(idx)}
                        aria-label={`Chuyển đến nhóm ${idx + 1}`}
                        title={`Nhóm ${idx + 1} (6 sản phẩm)`}
                      />
                    ))}
                  </div>
                  <div style={{ display: 'flex', gap: '4px' }}>
                    <button
                      type="button"
                      className="carousel-nav-btn"
                      style={{ width: '28px', height: '28px', borderRadius: '2px', fontSize: '15px' }}
                      onClick={() => setCurrentFlashTab(prev => (prev - 1 + totalFlashTabs) % totalFlashTabs)}
                      aria-label="Nhóm sản phẩm trước"
                      title="Nhóm trước"
                    >
                      ‹
                    </button>
                    <button
                      type="button"
                      className="carousel-nav-btn"
                      style={{ width: '28px', height: '28px', borderRadius: '2px', fontSize: '15px' }}
                      onClick={() => setCurrentFlashTab(prev => (prev + 1) % totalFlashTabs)}
                      aria-label="Nhóm sản phẩm tiếp theo"
                      title="Nhóm tiếp theo"
                    >
                      ›
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          {clearanceLoading ? (
            <div className="horizontal-carousel-wrapper">
              <div role="status" aria-label="Đang tải sản phẩm xả kho" className="flash-sale-grid">
                {Array.from({ length: 6 }, (_, index) => <ProductCardSkeleton key={index} />)}
              </div>
            </div>
          ) : clearanceError ? (
            <div role="alert">
              <p>{clearanceError}</p>
              <button type="button" className="btn-luxury-outline" onClick={() => setClearanceAttempt(value => value + 1)}>Thử lại sản phẩm xả kho</button>
            </div>
          ) : clearanceProducts.length === 0 ? (
            <p role="status">Hiện chưa có sản phẩm xả kho đủ điều kiện.</p>
          ) : (
            <div className="horizontal-carousel-wrapper">
              {totalFlashTabs > 1 && (
                <button
                  type="button"
                  className="carousel-floating-btn prev"
                  onClick={() => setCurrentFlashTab(prev => (prev - 1 + totalFlashTabs) % totalFlashTabs)}
                  aria-label="Nhóm sản phẩm trước"
                >
                  ‹
                </button>
              )}
              <div
                key={`flash-tab-${currentFlashTab}`}
                className="flash-sale-grid"
                style={{ animation: 'fadeInFlashTab 0.35s ease' }}
              >
                {currentFlashProducts.map(prod => (
                  <ProductCard key={prod.id} product={prod} onNavigate={onNavigate} onQuickView={setQuickViewProduct} />
                ))}
              </div>
              {totalFlashTabs > 1 && (
                <button
                  type="button"
                  className="carousel-floating-btn next"
                  onClick={() => setCurrentFlashTab(prev => (prev + 1) % totalFlashTabs)}
                  aria-label="Nhóm sản phẩm tiếp theo"
                >
                  ›
                </button>
              )}
            </div>
          )}
          <p style={{ margin: '16px 0 0', fontSize: '12px', color: 'var(--c-text-muted)' }}>
            HSD là ngày gần nhất của tồn cận hạn tại lúc tải. Số lượng có thể thay đổi; giỏ hàng chưa giữ chỗ hoặc cam kết một lô cụ thể.
          </p>
        </div>
      </section>

      <section
        id="categories-section"
        className="customer-container"
        onMouseEnter={() => setIsCategoryHovered(true)}
        onMouseLeave={() => setIsCategoryHovered(false)}
      >
        <div style={{ textAlign: 'center', marginBottom: '18px' }}>
          <span style={{
            fontSize: '11px',
            fontWeight: 800,
            color: 'var(--c-gold-hover, #B38E46)',
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            display: 'block',
            marginBottom: '4px'
          }}>
            Danh mục nổi bật
          </span>
          <h2 style={{
            margin: '0 auto',
            fontSize: 'clamp(20px, 2.4vw, 26px)',
            fontWeight: 800,
            color: '#33262C',
            letterSpacing: '-0.02em',
            fontFamily: 'var(--font-sans)'
          }}>
            Danh Mục Sản Phẩm
          </h2>
        </div>

        {totalCategoryTabs > 1 && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '10px',
            marginBottom: '20px'
          }}>
            <button
              type="button"
              className="carousel-nav-btn"
              onClick={() => setCurrentCategoryTab(prev => (prev - 1 + totalCategoryTabs) % totalCategoryTabs)}
              aria-label="Lô danh mục trước"
              title="Lô trước"
              style={{ width: '28px', height: '28px', borderRadius: '4px', fontSize: '15px' }}
            >
              ‹
            </button>
            <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              {Array.from({ length: totalCategoryTabs }, (_, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setCurrentCategoryTab(idx)}
                  aria-label={`Lô danh mục ${idx + 1}`}
                  title={`Lô ${idx + 1}`}
                  style={{
                    backgroundColor: currentCategoryTab === idx ? '#8C2A47' : '#E5E7EB',
                    width: currentCategoryTab === idx ? '28px' : '12px',
                    height: '5px',
                    borderRadius: '3px',
                    border: 'none',
                    cursor: 'pointer',
                    transition: 'all 0.25s ease',
                    padding: 0
                  }}
                />
              ))}
            </div>
            <button
              type="button"
              className="carousel-nav-btn"
              onClick={() => setCurrentCategoryTab(prev => (prev + 1) % totalCategoryTabs)}
              aria-label="Lô danh mục tiếp theo"
              title="Lô tiếp theo"
              style={{ width: '28px', height: '28px', borderRadius: '4px', fontSize: '15px' }}
            >
              ›
            </button>
          </div>
        )}

        <div key={currentCategoryTab} className="category-grid-8 category-batch-animate">
          {loading ? (
            Array.from({ length: 8 }, (_, n) => (
              <div key={n} className="skeleton-shimmer home-category-skeleton" />
            ))
          ) : (
            currentCategoryBatch.map((cat) => {
              const count = categoryProductCounts[cat.id];
              return (
                <div
                  key={cat.id}
                  className="category-tile-card"
                  onClick={() => onNavigate(`products?categoryId=${cat.id}&category=${cat.slug}&name=${encodeURIComponent(cat.name)}`)}
                >
                  <div className="category-tile-avatar">
                    <img
                      src={getCategoryImage(cat)}
                      alt={cat.name}
                      loading="lazy"
                    />
                  </div>
                  <span className="category-tile-title">
                    {cat.name}
                  </span>
                  <span className="category-tile-meta">
                    {count ? `${count} sản phẩm` : 'Khám phá'}
                  </span>
                </div>
              );
            })
          )}
        </div>
      </section>

      <section id="brand-partners" className="customer-container">
        <div className="section-header-editorial">
          <h2 className="section-title-editorial">
            Thương hiệu chính hãng
          </h2>
        </div>

        <div className="brands-marquee-wrapper">
          {loading ? (
            <div className="brands-marquee-track">
              {Array.from({ length: 10 }, (_, n) => (
                <div key={n} className="skeleton-shimmer home-brand-skeleton brand-logo-tile" />
              ))}
            </div>
          ) : (
            <div className="brands-marquee-track brands-grid-12">
              {marqueeBrands.map((brand, idx) => (
                <div
                  key={`${brand.id}-${idx}`}
                  className="brand-logo-tile brand-partner-card brand-partner-box"
                  onClick={() => onNavigate(`products?brandId=${brand.id}&search=${encodeURIComponent(brand.name)}`)}
                  role="button"
                  tabIndex={0}
                  title={brand.name}
                  aria-label={brand.name}
                >
                  <BrandCardLogo brand={brand} logoUrl={getBrandImage(brand)} />
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '20px' }}>
          <button
            onClick={() => onNavigate('products')}
            className="btn-luxury-outline"
            style={{ padding: '9px 24px', fontSize: '13px' }}
          >
            <span>Xem tất cả thương hiệu</span>
            <ArrowRight size={14} />
          </button>
        </div>
      </section>

      <section id="random-picks" className="customer-container">
        <div style={{
          backgroundColor: '#F8FAFC',
          borderRadius: '2px',
          border: '1px solid #CBD5E1',
          borderTop: '3px solid #1E3A8A',
          padding: 'clamp(16px, 2vw, 24px)'
        }}>
          <div style={{ marginBottom: '20px' }}>
            <h2 style={{
              margin: '0 0 4px 0',
              fontSize: '20px',
              fontWeight: 800,
              color: '#1E3A8A',
              fontFamily: 'var(--font-sans)',
              letterSpacing: '-0.01em'
            }}>
              GỢI Ý CHO BẠN
            </h2>
            <p style={{ margin: 0, fontSize: '13px', color: '#64748B' }}>
              Sản phẩm chọn lọc dựa trên xu hướng và thói quen mua sắm
            </p>
          </div>

          {loading ? (
            <div className="customer-product-grid product-skeleton-frame">
              {Array.from({ length: sectionLimits.recommendations }, (_, n) => (
                <ProductCardSkeleton key={n} />
              ))}
            </div>
          ) : (
            <div className="customer-product-grid">
              {((recommendedProducts && recommendedProducts.length > 0) ? recommendedProducts : randomProducts).slice(0, sectionLimits.recommendations).map((prod) => (
                <ProductCard
                  key={prod.id}
                  product={prod}
                  onNavigate={onNavigate}
                  onQuickView={setQuickViewProduct}
                />
              ))}
            </div>
          )}
        </div>
      </section>

      <section id="store-catalog" className="customer-container" style={{ margin: '52px auto' }}>
        <div className="section-header-editorial">
          <h2 className="section-title-editorial">
            Tất cả sản phẩm
          </h2>
        </div>

        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: '12px',
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: '28px',
          paddingBottom: '20px',
          borderBottom: '1px solid var(--c-border)'
        }}>
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
            <Search size={15} color="var(--c-text-light)" style={{ position: 'absolute', left: '12px', pointerEvents: 'none' }} />
            <input
              type="text"
              placeholder="Tìm nhanh mỹ phẩm, thương hiệu..."
              value={catalogSearchTerm}
              onChange={(e) => setCatalogSearchTerm(e.target.value)}
              style={{
                padding: '9px 12px 9px 36px',
                borderRadius: 'var(--radius-pill)',
                border: '1px solid var(--c-border)',
                fontSize: '13px',
                outline: 'none',
                backgroundColor: '#FFFFFF',
                width: '280px',
                transition: 'border-color 0.15s ease'
              }}
            />
          </div>

          <select
            value={catalogSortBy}
            onChange={(e) => setCatalogSortBy(e.target.value)}
            style={{
              padding: '9px 14px',
              borderRadius: 'var(--radius-pill)',
              border: '1px solid var(--c-border)',
              fontSize: '13px',
              backgroundColor: '#FFFFFF',
              outline: 'none',
              cursor: 'pointer',
              fontWeight: 600,
              color: 'var(--c-primary)'
            }}
          >
            <option value="DEFAULT">Sắp xếp: Mặc định</option>
            <option value="PRICE_ASC">Giá: Thấp đến cao</option>
            <option value="PRICE_DESC">Giá: Cao đến thấp</option>
            <option value="RATING">Đánh giá cao nhất</option>
          </select>
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '28px' }}>
          {dynamicCatalogCategories.map((tab) => {
            const active = catalogCategoryFilter === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setCatalogCategoryFilter(tab.id);
                }}
                className={`tab-filter-btn ${active ? 'active' : ''}`}
                style={{
                  padding: '8px 18px',
                  fontSize: '13px',
                  fontWeight: 600,
                  borderRadius: 'var(--radius-pill)',
                  border: active ? '1.5px solid var(--c-gold)' : '1px solid var(--c-border)',
                  backgroundColor: active ? 'var(--c-gold-light)' : '#FFFFFF',
                  color: active ? 'var(--c-gold-hover)' : 'var(--c-primary)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease'
                }}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {loading || loadingCategoryProducts ? (
          <div className="customer-product-grid product-skeleton-frame">
            {Array.from({ length: sectionLimits.catalog }, (_, n) => (
              <ProductCardSkeleton key={n} />
            ))}
          </div>
        ) : allStoreProducts.length === 0 ? (
          <div style={{
            textAlign: 'center',
            padding: '48px 20px',
            backgroundColor: '#FFFFFF',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--c-border-subtle)'
          }}>
            <Sparkles size={32} color="var(--c-gold)" style={{ margin: '0 auto 12px auto' }} />
            <h4 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--c-primary)', marginBottom: '6px' }}>
              Không tìm thấy sản phẩm phù hợp
            </h4>
            <p style={{ fontSize: '13px', color: 'var(--c-text-muted)', marginBottom: '16px' }}>
              Vui lòng thử tìm với từ khóa khác hoặc xóa bộ lọc danh mục.
            </p>
            <button
              onClick={() => { setCatalogCategoryFilter('ALL'); setCatalogSearchTerm(''); }}
              className="btn-luxury-outline"
            >
              Xem toàn bộ sản phẩm
            </button>
          </div>
        ) : (
          <div className="customer-product-grid">
            {allStoreProducts.slice(0, sectionLimits.catalog).map((prod) => (
              <ProductCard
                key={prod.id}
                product={prod}
                onNavigate={onNavigate}
                onQuickView={setQuickViewProduct}
              />
            ))}
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', marginTop: '36px', flexWrap: 'wrap' }}>
          <button
            onClick={openCatalog}
            className="btn-luxury-primary"
            style={{ padding: '12px 28px', fontSize: '13.5px' }}
          >
            <span>Xem tất cả sản phẩm</span>
            <ArrowRight size={15} />
          </button>
        </div>
      </section>

      <section id="spa-section" style={{
        backgroundColor: '#FFFFFF',
        padding: '72px 0',
        borderTop: '1px solid var(--c-border-subtle)',
        borderBottom: '1px solid var(--c-border-subtle)'
      }}>
        <div className="customer-container">
          <div className="section-header-editorial">
            <h2 className="section-title-editorial">
              Dịch vụ Spa Làm Đẹp
            </h2>
          </div>

          <div style={{ display: 'flex', justifyContent: 'center', flexWrap: 'wrap', gap: '8px', marginBottom: '32px' }}>
            {dynamicSpaCategories.map(cat => (
              <button
                key={cat.id}
                onClick={() => setActiveSpaCategory(cat.id)}
                className={`tab-filter-btn ${activeSpaCategory === cat.id ? 'active' : ''}`}
                style={{ fontSize: '12.5px', padding: '9px 20px' }}
              >
                {cat.label}
              </button>
            ))}
          </div>

          <div className="spa-services-grid">
            {getFilteredSpa().map((spa) => (
              <div
                key={spa.id}
                className="spa-service-card"
              >
                <div className="spa-service-image-box">
                  <img
                    src={getSpaServiceImage(spa)}
                    alt={spa.name}
                    loading="lazy"
                    onError={(event) => {
                      if (event.currentTarget.getAttribute('src') !== '/images/spa/facial-care.jpg') {
                        event.currentTarget.src = '/images/spa/facial-care.jpg';
                      }
                    }}
                  />
                  <div style={{
                    position: 'absolute',
                    top: '12px',
                    left: '12px',
                    backgroundColor: 'rgba(26, 20, 18, 0.88)',
                    backdropFilter: 'blur(6px)',
                    color: '#FFFFFF',
                    padding: '4px 10px',
                    borderRadius: 'var(--radius-pill)',
                    fontSize: '11px',
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '5px'
                  }}>
                    <Clock size={12} color="var(--c-gold)" />
                    <span>{spa.durationMinutes} phút</span>
                  </div>
                </div>

                <div className="home-spa-card-body">
                  <div>
                    <span style={{ fontSize: '11px', color: 'var(--c-gold-hover)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                      {spa.categoryName || 'Chăm Sóc Chuyên Sâu'}
                    </span>
                    <h3>
                      {spa.name}
                    </h3>
                    <p>
                      {spa.shortDescription || 'Quy trình chuẩn hóa khử khuẩn khép kín, massage bấm huyệt và phục hồi chuyên sâu.'}
                    </p>
                  </div>

                  <div className="home-spa-card-footer">
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Giá trọn gói</div>
                      <div className="home-spa-card-price">
                        {spa.basePrice ? formatCurrency(spa.basePrice) : 'Liên hệ tư vấn'}
                      </div>
                    </div>

                    <button
                      onClick={() => onNavigate(`booking?serviceId=${spa.id}`)}
                      className="btn-luxury-primary"
                    >
                      <Calendar size={13} />
                      <span>Đặt lịch ngay</span>
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div style={{ textAlign: 'center', marginTop: '40px' }}>
            <button
              onClick={() => onNavigate('booking')}
              className="btn-luxury-gold pulse-glow"
              style={{ padding: '14px 36px', fontSize: '14px' }}
            >
              <span>Xem Bảng Giá & Đặt Lịch Hẹn Spa</span>
              <Calendar size={16} />
            </button>
          </div>
        </div>
      </section>

      <QuickViewModal
        product={quickViewProduct}
        isOpen={!!quickViewProduct}
        onClose={() => setQuickViewProduct(null)}
        onNavigate={onNavigate}
      />

      <ToastNotification
        toasts={toasts}
        onDismiss={removeToast}
      />

      <ScrollProgressTop />

    </div>
  );
};

export default HomePage;
