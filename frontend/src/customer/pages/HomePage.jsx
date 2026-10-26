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
      title: 'Mỹ Phẩm Chính Hãng & Clinic Chuẩn Y Khoa.',
      description: 'Cam kết 100% hàng thật, date xa rõ ràng, nguồn gốc minh bạch. Dịch vụ khám da với bác sĩ chuyên khoa.',
      ctaPrimary: 'Mua Sắm Ngay',
      ctaSecondary: 'Xem Tất Cả Mỹ Phẩm',
      targetPrimary: 'products',
      targetSecondary: '#store-catalog',
      stat1: '100% Chính Hãng',
      stat2: 'FDA Chuẩn Y Khoa',
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
      badge: 'CLINIC & SPA',
      title: 'Dịch Vụ Điều Trị & Chăm Sóc Da Chuẩn Y Khoa.',
      description: 'Quy trình Aqua Peel làm sạch sâu đa tầng, điện di vi điểm căng bóng và trị mụn chuẩn y khoa với công nghệ hiện đại.',
      ctaPrimary: 'Đặt Lịch Hẹn Ngay',
      ctaSecondary: 'Xem Bảng Giá Spa',
      targetPrimary: 'booking',
      targetSecondary: '#spa-section',
      stat1: '100% Bác Sĩ Da Liễu',
      stat2: 'FDA Chuẩn Y Khoa',
      stat3: 'Clinic Chuyên Sâu',
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
      eyebrow: 'SPA & CLINIC',
      title: 'Soi Da 3D & Điều Trị Chuẩn Y Khoa',
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
          stat2: 'FDA Chuẩn Y Khoa',
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
              const stat1 = currentSlide.stat1 || '10.000+ Sản Phẩm';
              const stat2 = currentSlide.stat2 || '100% Chính Hãng';
              const stat3 = currentSlide.stat3 || 'Giao Nhanh 2H';

              return (
                <div className="home-banner-main" style={{
                  position: 'relative',
                  overflow: 'hidden',
                  borderRadius: 'calc(var(--radius-lg) - 6px)',
                  padding: '40px 48px',
                  minHeight: '420px',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'center',
                  backgroundImage: `linear-gradient(90deg, rgba(31, 22, 20, 0.92) 0%, rgba(140, 42, 71, 0.74) 48%, rgba(140, 42, 71, 0.18) 100%), url(${currentSlide.image})`,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center'
                }}>
                  {currentSlide.badge && (
                    <div className="hero-eyebrow" style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '5px 12px',
                      borderRadius: '4px',
                      backgroundColor: 'rgba(255, 255, 255, 0.15)',
                      border: '1px solid rgba(255, 255, 255, 0.35)',
                      fontSize: '11px',
                      color: '#FFCCD5',
                      fontWeight: 800,
                      letterSpacing: '0.06em',
                      marginBottom: '16px'
                    }}>
                      <Sparkles size={13} />
                      <span>{currentSlide.badge}</span>
                    </div>
                  )}

                  <h1 style={{
                    fontFamily: 'var(--font-sans)',
                    fontSize: 'clamp(28px, 4vw, 44px)',
                    lineHeight: 1.2,
                    fontWeight: 800,
                    color: '#FFFFFF',
                    margin: '0 0 16px 0',
                    letterSpacing: '-0.02em',
                    maxWidth: '620px'
                  }}>
                    {currentSlide.title}
                  </h1>

                  <p style={{
                    fontSize: '15px',
                    lineHeight: 1.65,
                    color: 'var(--c-text-on-dark-muted)',
                    margin: '0 0 32px 0',
                    maxWidth: '680px'
                  }}>
                    {currentSlide.description}
                  </p>

                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '14px' }}>
                    <button
                      onClick={() => {
                        navigateHeroTarget(currentSlide.targetPrimary || 'products');
                      }}
                      className="btn-luxury-gold"
                      style={{ padding: '13px 26px', fontSize: '14px', borderRadius: '4px' }}
                    >
                      <span>{currentSlide.ctaPrimary || 'Mua Sắm Ngay'}</span>
                      <ArrowRight size={16} />
                    </button>

                    <button
                      onClick={() => {
                        navigateHeroTarget(currentSlide.targetSecondary || '#store-catalog');
                      }}
                      className="btn-luxury-outline"
                      style={{
                        padding: '12px 24px',
                        borderColor: 'rgba(255, 255, 255, 0.25)',
                        color: '#FFFFFF',
                        backgroundColor: 'rgba(255, 255, 255, 0.05)',
                        borderRadius: '4px'
                      }}
                    >
                      <Calendar size={16} color="var(--c-gold)" />
                      <span>{currentSlide.ctaSecondary || 'Xem Tất Cả Mỹ Phẩm'}</span>
                    </button>
                  </div>

                  <div className="hero-stats-grid" style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gap: '16px',
                    marginTop: '40px',
                    paddingTop: '24px',
                    borderTop: '1px solid rgba(255, 255, 255, 0.12)'
                  }}>
                    <div>
                      <div style={{
                        fontSize: '22px',
                        fontWeight: 800,
                        color: 'var(--c-gold)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {stat1.includes(' ') ? stat1.split(' ')[0] : stat1}
                      </div>
                      {stat1.includes(' ') && (
                        <div style={{ fontSize: '11px', color: '#B8A7A0', marginTop: '2px', fontWeight: 600 }}>
                          {stat1.substring(stat1.indexOf(' ') + 1)}
                        </div>
                      )}
                    </div>
                    <div>
                      <div style={{
                        fontSize: '22px',
                        fontWeight: 800,
                        color: 'var(--c-gold)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {stat2.includes(' ') ? stat2.split(' ')[0] : stat2}
                      </div>
                      {stat2.includes(' ') && (
                        <div style={{ fontSize: '11px', color: '#B8A7A0', marginTop: '2px', fontWeight: 600 }}>
                          {stat2.substring(stat2.indexOf(' ') + 1)}
                        </div>
                      )}
                    </div>
                    <div>
                      <div style={{
                        fontSize: '22px',
                        fontWeight: 800,
                        color: 'var(--c-gold)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {stat3.includes(' ') ? stat3.split(' ')[0] : stat3}
                      </div>
                      {stat3.includes(' ') && (
                        <div style={{ fontSize: '11px', color: '#B8A7A0', marginTop: '2px', fontWeight: 600 }}>
                          {stat3.substring(stat3.indexOf(' ') + 1)}
                        </div>
                      )}
                    </div>
                  </div>

                  {heroSlides.length > 1 && (
                    <div style={{
                      position: 'absolute',
                      bottom: '24px',
                      right: '28px',
                      display: 'flex',
                      gap: '7px',
                      zIndex: 3
                    }}>
                      {heroSlides.map((_, idx) => (
                        <button
                          key={idx}
                          onClick={() => setActiveHeroSlide(idx)}
                          style={{
                            width: activeHeroSlide === idx ? '26px' : '7px',
                            height: '7px',
                            borderRadius: '4px',
                            border: 'none',
                            backgroundColor: activeHeroSlide === idx ? 'var(--c-primary, #D45D79)' : 'rgba(255,255,255,0.4)',
                            cursor: 'pointer',
                            transition: 'all 0.25s ease',
                            padding: 0
                          }}
                          title={`Slide ${idx + 1}`}
                        />
                      ))}
                    </div>
                  )}

                  {heroSlides.length > 1 && (
                    <>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setActiveHeroSlide(prev => (prev - 1 + heroSlides.length) % heroSlides.length);
                        }}
                        aria-label="Slide trước"
                        className="hero-carousel-arrow hero-carousel-prev"
                        style={{
                          position: 'absolute',
                          left: '18px',
                          top: '50%',
                          transform: 'translateY(-50%)',
                          width: '42px',
                          height: '42px',
                          borderRadius: '50%',
                          backgroundColor: 'rgba(0, 0, 0, 0.40)',
                          backdropFilter: 'blur(6px)',
                          border: '1px solid rgba(255, 255, 255, 0.25)',
                          color: '#FFFFFF',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          cursor: 'pointer',
                          zIndex: 4,
                          transition: 'all 0.2s ease',
                          boxShadow: '0 4px 12px rgba(0,0,0,0.25)'
                        }}
                        onMouseEnter={e => {
                          e.currentTarget.style.backgroundColor = 'var(--c-primary, #D45D79)';
                          e.currentTarget.style.transform = 'translateY(-50%) scale(1.08)';
                        }}
                        onMouseLeave={e => {
                          e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.40)';
                          e.currentTarget.style.transform = 'translateY(-50%) scale(1)';
                        }}
                      >
                        <ChevronLeft size={22} />
                      </button>

                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setActiveHeroSlide(prev => (prev + 1) % heroSlides.length);
                        }}
                        aria-label="Slide tiếp theo"
                        className="hero-carousel-arrow hero-carousel-next"
                        style={{
                          position: 'absolute',
                          right: '18px',
                          top: '50%',
                          transform: 'translateY(-50%)',
                          width: '42px',
                          height: '42px',
                          borderRadius: '50%',
                          backgroundColor: 'rgba(0, 0, 0, 0.40)',
                          backdropFilter: 'blur(6px)',
                          border: '1px solid rgba(255, 255, 255, 0.25)',
                          color: '#FFFFFF',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          cursor: 'pointer',
                          zIndex: 4,
                          transition: 'all 0.2s ease',
                          boxShadow: '0 4px 12px rgba(0,0,0,0.25)'
                        }}
                        onMouseEnter={e => {
                          e.currentTarget.style.backgroundColor = 'var(--c-primary, #D45D79)';
                          e.currentTarget.style.transform = 'translateY(-50%) scale(1.08)';
                        }}
                        onMouseLeave={e => {
                          e.currentTarget.style.backgroundColor = 'rgba(0, 0, 0, 0.40)';
                          e.currentTarget.style.transform = 'translateY(-50%) scale(1)';
                        }}
                      >
                        <ChevronRight size={22} />
                      </button>
                    </>
                  )}
                </div>
              );
            })()}

            <div className="home-banner-side" style={{ display: 'grid', gap: '16px' }}>
              {displayedSideBanners.map(({ eyebrow, title, desc, cta, target, image, Icon, slotIndex }, idx) => (
                <div
                  key={`${title}-${slotIndex ?? idx}`}
                  className="home-banner-small"
                  onClick={() => navigateHeroTarget(target)}
                  style={{
                    position: 'relative',
                    overflow: 'hidden',
                    minHeight: '202px',
                    padding: '24px',
                    borderRadius: 'calc(var(--radius-lg) - 6px)',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    backgroundImage: `linear-gradient(135deg, rgba(31, 22, 20, 0.92), rgba(140, 42, 71, 0.58)), url(${image})`,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                    border: '1px solid rgba(255, 255, 255, 0.16)',
                    transition: 'all 0.3s ease'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div style={{ flex: 1, paddingRight: '12px' }}>
                      {eyebrow && (
                        <div style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '7px',
                          marginBottom: '12px',
                          color: 'var(--c-gold)',
                          fontSize: '11px',
                          fontWeight: 800,
                          letterSpacing: '0.08em'
                        }}>
                          <Icon size={14} />
                          <span>{eyebrow}</span>
                        </div>
                      )}
                      <h3 style={{ color: '#FFFFFF', fontSize: '19px', fontWeight: 800, lineHeight: 1.25, margin: '0 0 8px 0' }}>
                        {title}
                      </h3>
                      <p style={{ color: '#F3DDE4', fontSize: '12.5px', lineHeight: 1.5, margin: 0 }}>
                        {desc}
                      </p>
                    </div>

                    {heroSideBanners.length > 2 && (
                      <div
                        onClick={(e) => e.stopPropagation()}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          backgroundColor: 'rgba(0,0,0,0.42)',
                          backdropFilter: 'blur(4px)',
                          padding: '3px 8px',
                          borderRadius: '9999px',
                          border: '1px solid rgba(255,255,255,0.22)',
                          flexShrink: 0
                        }}
                      >
                        <span style={{ fontSize: '10px', color: '#FFFFFF', fontWeight: 700, marginRight: '2px' }}>
                          {(slotIndex ?? idx) + 1}/{heroSideBanners.length}
                        </span>
                        <button
                          onClick={() => setSideSlideOffset(prev => (prev - 1 + heroSideBanners.length) % heroSideBanners.length)}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#FFFFFF',
                            cursor: 'pointer',
                            padding: '2px',
                            display: 'flex',
                            alignItems: 'center'
                          }}
                          title="Banner trước"
                        >
                          <ChevronLeft size={13} />
                        </button>
                        <button
                          onClick={() => setSideSlideOffset(prev => (prev + 1) % heroSideBanners.length)}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#FFFFFF',
                            cursor: 'pointer',
                            padding: '2px',
                            display: 'flex',
                            alignItems: 'center'
                          }}
                          title="Banner tiếp theo"
                        >
                          <ChevronRight size={13} />
                        </button>
                      </div>
                    )}
                  </div>

                  <button
                    type="button"
                    className="btn-luxury-outline"
                    style={{
                      alignSelf: 'flex-start',
                      padding: '8px 14px',
                      borderColor: 'rgba(255, 255, 255, 0.28)',
                      backgroundColor: 'rgba(255, 255, 255, 0.10)',
                      color: '#FFFFFF',
                      fontSize: '12px'
                    }}
                  >
                    <span>{cta}</span>
                    <ArrowRight size={14} />
                  </button>
                </div>
              ))}
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

      <section id="categories-section" className="customer-container">
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <h2 style={{
            margin: '0 auto',
            fontSize: '22px',
            fontWeight: 800,
            color: '#8C2A47',
            display: 'inline-block',
            paddingBottom: '6px',
            borderBottom: '2px solid #8C2A47'
          }}>
            Danh mục sản phẩm
          </h2>
        </div>

        <div className="category-grid-8">
          {loading ? (
            Array.from({ length: sectionLimits.categories }, (_, n) => (
              <div key={n} className="skeleton-shimmer home-category-skeleton" />
            ))
          ) : (
            categories.slice(0, sectionLimits.categories).map((cat) => (
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
                <span className="category-tile-hint">
                  <span>Khám phá</span>
                </span>
              </div>
            ))
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
              Dịch vụ Spa & Clinic
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
                      {spa.categoryName || 'Trị liệu Y Khoa'}
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
