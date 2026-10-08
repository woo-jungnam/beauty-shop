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
import { getGuestSessionId } from '../../shared/utils/session';

const formatCurrency = (val) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val || 0);
};

export const HomePage = ({ onNavigate }) => {
  const { addItem } = useCustomerCart();
  const [products, setProducts] = useState([]);
  const [spaServices, setSpaServices] = useState([]);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [backendBanners, setBackendBanners] = useState([]);
  const [recommendedProducts, setRecommendedProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  // Interactive UX Modals and Toasts
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

  // 1. Hero Carousel State
  const [activeHeroSlide, setActiveHeroSlide] = useState(0);
  const [isHeroHovered, setIsHeroHovered] = useState(false);

  // 2. Flash Sale Live Countdown Timer & Clearance State
  const [timeLeft, setTimeLeft] = useState({ hours: 7, minutes: 42, seconds: 18 });
  const [clearanceProducts, setClearanceProducts] = useState([]);

  // 3. Product Catalog Filter & Search State
  const [catalogCategoryFilter, setCatalogCategoryFilter] = useState('ALL');
  const [categoryProducts, setCategoryProducts] = useState(null);
  const [loadingCategoryProducts, setLoadingCategoryProducts] = useState(false);
  const [catalogSearchTerm, setCatalogSearchTerm] = useState('');
  const [catalogSortBy, setCatalogSortBy] = useState('DEFAULT');
  const [visibleCount, setVisibleCount] = useState(12);

  // 4. Spa Service Category Filter State
  const [activeSpaCategory, setActiveSpaCategory] = useState('ALL');

  // Fetch dynamic catalog, categories and spa services from backend APIs
  useEffect(() => {
    let isMounted = true;
    const fetchData = async () => {
      setLoading(true);
      try {
        const guestSessionId = getGuestSessionId();
        const [prodRes, spaRes, catRes, brandRes, bannerRes, recRes, clearanceRes] = await Promise.allSettled([
          apiClient.get('/api/v1/products?size=60'),
          apiClient.get('/api/v1/spa/services'),
          apiClient.get('/api/v1/categories'),
          apiClient.get('/api/v1/brands?size=20'),
          apiClient.get('/api/v1/banners'),
          apiClient.get(`/api/v1/products/recommended-for-you?limit=8&sessionId=${encodeURIComponent(guestSessionId)}`),
          apiClient.get('/api/v1/products/expiring-soon?limit=8&thresholdDays=90')
        ]);

        if (isMounted) {
          if (prodRes.status === 'fulfilled') {
            const list = prodRes.value?.data?.content || prodRes.value?.content || [];
            setProducts(list);
          }
          if (clearanceRes.status === 'fulfilled') {
            const clList = clearanceRes.value?.data || clearanceRes.value || [];
            setClearanceProducts(Array.isArray(clList) ? clList : (clList?.content || []));
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

  // Fetch category products dynamically from backend when category filter pill is clicked
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

  // 8 random discovery products — balanced 2x4 on desktop, 4x2 on mobile
  const randomProducts = useMemo(() => {
    if (products.length === 0) return [];
    const arr = [...products];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr.slice(0, 8);
  }, [products]);

  // Dynamic category filter pills from API
  const dynamicCatalogCategories = useMemo(() => {
    const list = [{ id: 'ALL', label: `Tất cả sản phẩm (${products.length})` }];
    if (categories.length > 0) {
      categories.slice(0, 7).forEach(cat => {
        list.push({ id: String(cat.id), label: cat.name });
      });
    }
    return list;
  }, [categories, products.length]);

  // Map category slug/name → curated skincare image (verified Unsplash IDs)
  const getCategoryImage = (cat) => {
    const slug = (cat.slug || '').toLowerCase().replace(/_/g, '-');
    const name = (cat.name || '').toLowerCase();

    // Slug-based lookup (most precise — matches API slugs)
    if (slug.includes('sua-rua-mat') || slug.includes('rua-mat'))
      return 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('tay-trang') || slug.includes('lam-sach'))
      return 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('serum') || slug.includes('tinh-chat'))
      return 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('chong-nang'))
      return 'https://images.unsplash.com/photo-1599305090598-fe179d501227?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('duong-am') || slug.includes('kem-duong'))
      return 'https://images.unsplash.com/photo-1612817288484-6f916006741a?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('toner') || slug.includes('nuoc-can-bang'))
      return 'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('mat-na'))
      return 'https://images.unsplash.com/photo-1587940836072-62f459726a8a?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('trang-diem') || slug.includes('son-moi'))
      return 'https://images.unsplash.com/photo-1512496015851-a90fb38ba796?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('cham-soc-toc') || slug.includes('toc'))
      return 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('cham-soc-co-the') || slug.includes('co-the') || slug.includes('body'))
      return 'https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('cham-soc-da-mat') || slug.includes('da-mat'))
      return 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=500&q=75';
    if (slug.includes('mun') || slug.includes('tri-mun'))
      return 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=500&q=75';

    // Vietnamese name-based fallback
    if (name.includes('sữa rửa') || name.includes('rửa mặt'))
      return 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=500&q=75';
    if (name.includes('tẩy trang') || name.includes('làm sạch'))
      return 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=500&q=75';
    if (name.includes('serum') || name.includes('tinh chất'))
      return 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=500&q=75';
    if (name.includes('chống nắng'))
      return 'https://images.unsplash.com/photo-1599305090598-fe179d501227?auto=format&fit=crop&w=500&q=75';
    if (name.includes('dưỡng ẩm') || name.includes('kem dưỡng') || name.includes('cấp ẩm'))
      return 'https://images.unsplash.com/photo-1612817288484-6f916006741a?auto=format&fit=crop&w=500&q=75';
    if (name.includes('toner') || name.includes('nước cân bằng'))
      return 'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=500&q=75';
    if (name.includes('mặt nạ'))
      return 'https://images.unsplash.com/photo-1587940836072-62f459726a8a?auto=format&fit=crop&w=500&q=75';
    if (name.includes('trang điểm') || name.includes('son') || name.includes('môi'))
      return 'https://images.unsplash.com/photo-1512496015851-a90fb38ba796?auto=format&fit=crop&w=500&q=75';
    if (name.includes('tóc') || name.includes('hair'))
      return 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=500&q=75';
    if (name.includes('cơ thể') || name.includes('body') || name.includes('tắm'))
      return 'https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=500&q=75';
    if (name.includes('mụn') || name.includes('trị mụn'))
      return 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=500&q=75';
    if (name.includes('chăm sóc') || name.includes('da mặt'))
      return 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=500&q=75';

    return 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=500&q=75';
  };

  // Map spa service name/category → appropriate treatment image
  const getSpaServiceImage = (service) => {
    if (service.thumbnailUrl) return service.thumbnailUrl;
    const name = (service.name || '').toLowerCase();
    const cat = (service.categoryName || '').toLowerCase();

    if (name.includes('aqua peel') || name.includes('làm sạch') || name.includes('peel') || cat.includes('làm sạch'))
      return 'https://images.unsplash.com/photo-1570554520913-ce2a90ccc23b?auto=format&fit=crop&w=600&q=80';
    if (name.includes('mụn') || name.includes('acne') || name.includes('bio-light') || name.includes('vi khuẩn'))
      return 'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=600&q=80';
    if (name.includes('hiệu ứng ấm') || name.includes('nước') || name.includes('vitamin') || name.includes('niacinamide'))
      return 'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=600&q=80';
    if (name.includes('hifu') || name.includes('nâng cơ') || name.includes('v-line') || name.includes('sắc nét'))
      return 'https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=600&q=80';
    if (name.includes('galvanic') || name.includes('điện di') || name.includes('ion'))
      return 'https://images.unsplash.com/photo-1600428877878-1a0fcc0ba376?auto=format&fit=crop&w=600&q=80';
    if (name.includes('retinol') || name.includes('trẻ hóa') || name.includes('anti-aging') || cat.includes('nâng cơ'))
      return 'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=600&q=80';
    if (name.includes('massage') || name.includes('thư giãn') || name.includes('body') || cat.includes('body'))
      return 'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=600&q=80';
    if (name.includes('collagen') || name.includes('ngọc trai') || name.includes('vip') || name.includes('hoàng gia'))
      return 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=600&q=80';
    if (cat.includes('chăm sóc da') || cat.includes('điều trị'))
      return 'https://images.unsplash.com/photo-1570554520913-ce2a90ccc23b?auto=format&fit=crop&w=600&q=80';

    // Rotate through curated spa/clinic images by service ID
    const spaPool = [
      'https://images.unsplash.com/photo-1570554520913-ce2a90ccc23b?auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1544161515-4ab6ce6db874?auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1512290923902-8a9f81dc236c?auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=600&q=80',
      'https://images.unsplash.com/photo-1600428877878-1a0fcc0ba376?auto=format&fit=crop&w=600&q=80',
    ];
    return spaPool[(service.id || 0) % spaPool.length];
  };

  // Map brand name → representative product image
  const getBrandImage = (brand) => {
    if (brand.logoUrl) return brand.logoUrl;
    const name = (brand.name || '').toLowerCase();
    if (name.includes('la roche') || name.includes('laroche'))
      return 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=300&q=75';
    if (name.includes('cerave'))
      return 'https://images.unsplash.com/photo-1612817288484-6f916006741a?auto=format&fit=crop&w=300&q=75';
    if (name.includes('paula') || name.includes('choice'))
      return 'https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?auto=format&fit=crop&w=300&q=75';
    if (name.includes('skinceuticals') || name.includes('obagi'))
      return 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=300&q=75';
    if (name.includes('bioderma') || name.includes('avène') || name.includes('avene'))
      return 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=300&q=75';
    if (name.includes('cosrx') || name.includes('anua'))
      return 'https://images.unsplash.com/photo-1587940836072-62f459726a8a?auto=format&fit=crop&w=300&q=75';
    if (name.includes('vichy'))
      return 'https://images.unsplash.com/photo-1583394838336-acd977736f90?auto=format&fit=crop&w=300&q=75';
    // Generic fallback palette
    const fallbacks = [
      'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=300&q=75',
      'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=300&q=75',
      'https://images.unsplash.com/photo-1616394584738-fc6e612e71b9?auto=format&fit=crop&w=300&q=75',
      'https://images.unsplash.com/photo-1526045612212-70caf35c14df?auto=format&fit=crop&w=300&q=75',
    ];
    return fallbacks[(brand.id || 0) % fallbacks.length];
  };

  // Flash Sale Countdown Interval
  useEffect(() => {
    const timer = setInterval(() => {
      setTimeLeft(prev => {
        if (prev.seconds > 0) return { ...prev, seconds: prev.seconds - 1 };
        if (prev.minutes > 0) return { ...prev, minutes: prev.minutes - 1, seconds: 59 };
        if (prev.hours > 0) return { hours: prev.hours - 1, minutes: 59, seconds: 59 };
        return { hours: 12, minutes: 0, seconds: 0 };
      });
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const DEFAULT_HERO_SLIDES = [
    {
      badge: 'CHÍNH HÃNG 100%',
      title: 'Mỹ Phẩm Chính Hãng & Clinic Chuẩn Y Khoa.',
      description: 'Cam kết 100% hàng thật, date xa rõ ràng, đền bù 200% nếu phát hiện hàng giả. Dịch vụ khám da miễn phí với bác sĩ chuyên khoa.',
      ctaPrimary: 'Mua Sắm Ngay',
      ctaSecondary: 'Xem Tất Cả Mỹ Phẩm',
      targetPrimary: 'products',
      targetSecondary: '#store-catalog',
      stat1: '10.000+ Sản Phẩm',
      stat2: '100% Chính Hãng',
      stat3: 'Giao Nhanh 2H',
      image: 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=1000&q=80',
    },
    {
      badge: 'DEAL GIỜ VÀNG',
      title: 'Đại Tiệc Flash Sale - Giảm Giá Đến 50% Hôm Nay.',
      description: 'Cơ hội sở hữu tinh chất phục hồi da B5, kem chống nắng kiềm dầu quang phổ rộng và kem dưỡng ẩm từ các thương hiệu hàng đầu thế giới.',
      ctaPrimary: 'Săn Deal Ngay',
      ctaSecondary: 'Nhận Voucher 100K',
      targetPrimary: '#flash-sale',
      targetSecondary: '#voucher-section',
      stat1: '-50% Giảm Sốc',
      stat2: 'Freeship từ 249K',
      stat3: 'Tặng Quà Fullsize',
      image: 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=1000&q=80',
    },
    {
      badge: 'CLINIC & SPA',
      title: 'Dịch Vụ Điều Trị & Chăm Sóc Da Chuẩn Y Khoa.',
      description: 'Quy trình Aqua Peel làm sạch sâu đa tầng, điện di vi điểm căng bóng và trị mụn chuẩn y khoa không thâm sẹo với công nghệ FDA tiên tiến.',
      ctaPrimary: 'Đặt Lịch Hẹn Ngay',
      ctaSecondary: 'Xem Bảng Giá Spa',
      targetPrimary: 'booking',
      targetSecondary: '#spa-section',
      stat1: 'Bác Sĩ Da Liễu',
      stat2: '100% Chuẩn Y Khoa',
      stat3: 'Hệ Thống Toàn Quốc',
      image: 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1000&q=80',
    }
  ];

  const DEFAULT_SIDE_BANNERS = [
    {
      eyebrow: 'DEAL GIỜ VÀNG',
      title: 'Flash Sale Mỹ Phẩm Chính Hãng',
      desc: 'Giảm đến 50%, freeship từ 249K và quà fullsize giới hạn hôm nay.',
      cta: 'Săn deal',
      target: '#flash-sale',
      image: 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=700&q=80',
      Icon: Flame
    },
    {
      eyebrow: 'SPA & CLINIC',
      title: 'Soi Da 3D & Điều Trị Chuẩn Y Khoa',
      desc: 'Đặt lịch Aqua Peel, điện di phục hồi và tư vấn routine cùng chuyên viên.',
      cta: 'Đặt lịch',
      target: 'booking',
      image: 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=700&q=80',
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
          stat1: '10.000+ Sản Phẩm',
          stat2: '100% Chính Hãng',
          stat3: 'Giao Nhanh 2H',
          image: b.imageUrl,
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
          image: b.imageUrl,
          Icon: idx === 0 ? Flame : Calendar
        }));
      }
    }
    return DEFAULT_SIDE_BANNERS;
  }, [backendBanners]);

  const [sideSlideOffset, setSideSlideOffset] = useState(0);

  // Synchronize side banners rotation with main hero slide
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

  // Hero Carousel Auto-Play
  useEffect(() => {
    if (isHeroHovered || heroSlides.length <= 1) return;
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

  // Filtered Spa Services logic based on dynamic categories
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
    if (activeSpaCategory === 'ALL') return spaServices.slice(0, 6);
    return spaServices.filter(s => String(s.categoryId) === String(activeSpaCategory)).slice(0, 6);
  };

  return (
    <div className="customer-home-page" style={{ display: 'flex', flexDirection: 'column', gap: '56px', paddingBottom: '70px' }}>

      {/* =========================================================================
          1. LUXURY EDITORIAL HERO BANNER (CAROUSEL WITH DYNAMIC SLIDES)
          ========================================================================= */}
      <section
        className="hero-slider-container"
        onMouseEnter={() => setIsHeroHovered(true)}
        onMouseLeave={() => setIsHeroHovered(false)}
        style={{
          position: 'relative',
          padding: 0,
          minHeight: '640px',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
          backgroundColor: '#8C2A47'
        }}
      >
        <div style={{ position: 'relative', zIndex: 3, width: '100%' }}>
          <div className="home-banner-block hero-content-grid" style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1.65fr) minmax(280px, 0.8fr)',
            alignItems: 'stretch',
            gap: '16px',
            background: 'rgba(255, 255, 255, 0.08)',
            border: '1px solid rgba(255, 255, 255, 0.16)',
            borderRadius: 0,
            padding: '28px',
            minHeight: '640px',
            boxShadow: '0 25px 60px -20px rgba(0, 0, 0, 0.55)'
          }}>
            {/* Main Banner */}
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
                  padding: '64px',
                  minHeight: '584px',
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
                    minHeight: '110px'
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

                  {/* Action Buttons */}
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

                  {/* Stat Counters */}
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
                        fontSize: '24px',
                        fontWeight: 800,
                        color: 'var(--c-gold)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {stat1.split(' ')[0]}
                      </div>
                      <div style={{ fontSize: '11px', color: '#B8A7A0', marginTop: '2px', fontWeight: 500 }}>
                        {stat1.substring(stat1.indexOf(' ') + 1)}
                      </div>
                    </div>
                    <div>
                      <div style={{
                        fontSize: '24px',
                        fontWeight: 800,
                        color: 'var(--c-gold)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {stat2.split(' ')[0]}
                      </div>
                      <div style={{ fontSize: '11px', color: '#B8A7A0', marginTop: '2px', fontWeight: 500 }}>
                        {stat2.substring(stat2.indexOf(' ') + 1)}
                      </div>
                    </div>
                    <div>
                      <div style={{
                        fontSize: '24px',
                        fontWeight: 800,
                        color: 'var(--c-gold)',
                        fontFamily: 'var(--font-mono)'
                      }}>
                        {stat3.split(' ')[0]}
                      </div>
                      <div style={{ fontSize: '11px', color: '#B8A7A0', marginTop: '2px', fontWeight: 500 }}>
                        {stat3.substring(stat3.indexOf(' ') + 1)}
                      </div>
                    </div>
                  </div>

                  {/* Slide Indicators */}
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

                  {/* Navigation Arrows for Main Banner */}
                  {heroSlides.length > 1 && (
                    <>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setActiveHeroSlide(prev => (prev - 1 + heroSlides.length) % heroSlides.length);
                        }}
                        aria-label="Slide trước"
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

            {/* Side Banners (2 Slots: Fixed height & Rotating/Synchronized) */}
            <div className="home-banner-side" style={{ display: 'grid', gap: '16px' }}>
              {displayedSideBanners.map(({ eyebrow, title, desc, cta, target, image, Icon, slotIndex }, idx) => (
                <div
                  key={`${title}-${slotIndex ?? idx}`}
                  className="home-banner-small"
                  onClick={() => navigateHeroTarget(target)}
                  style={{
                    position: 'relative',
                    overflow: 'hidden',
                    minHeight: '284px',
                    padding: '34px',
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

                    {/* Side Banner Rotation Indicator & Manual Controls */}
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

      {/* =========================================================================
          3. FLASH SALE / HASAKI DEALS (SCARCITY & URGENCY MODULE)
          ========================================================================= */}
      <section id="flash-sale" className="customer-container">
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 'var(--radius-md, 6px)',
          border: '1px solid var(--c-border)',
          padding: '24px 28px',
          boxShadow: 'var(--shadow-card)'
        }}>
          {/* Flash Sale Header Bar - Hasaki Style */}
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
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: 'var(--c-deal-red, #E31837)',
                fontSize: '20px',
                fontWeight: 900,
                fontFamily: 'var(--font-sans)'
              }}>
                <Flame size={24} fill="#E31837" />
                <span>XẢ KHO CẬN DATE • GIÁ SỐC ONLINE</span>
              </div>
              <span style={{
                backgroundColor: '#B45309',
                color: '#FFFFFF',
                padding: '3px 8px',
                borderRadius: '3px',
                fontSize: '11px',
                fontWeight: 800,
                letterSpacing: '0.3px'
              }}>
                MINH BẠCH HẠN SỬ DỤNG
              </span>
            </div>

            {/* Countdown Digits */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--c-text-muted)' }}>Kết thúc sau:</span>
              <div className="countdown-digit-box">
                <span className="countdown-digit-val">{String(timeLeft.hours).padStart(2, '0')}</span>
                <span className="countdown-digit-label">Giờ</span>
              </div>
              <span style={{ fontWeight: 800, color: 'var(--c-deal-red, #E31837)' }}>:</span>
              <div className="countdown-digit-box">
                <span className="countdown-digit-val">{String(timeLeft.minutes).padStart(2, '0')}</span>
                <span className="countdown-digit-label">Phút</span>
              </div>
              <span style={{ fontWeight: 800, color: 'var(--c-deal-red, #E31837)' }}>:</span>
              <div className="countdown-digit-box">
                <span className="countdown-digit-val">{String(timeLeft.seconds).padStart(2, '0')}</span>
                <span className="countdown-digit-label">Giây</span>
              </div>
            </div>
          </div>

          {/* Flash Sale Product Row - 4 Items Balanced */}
          <div className="flash-sale-grid-4">
            {((clearanceProducts && clearanceProducts.length > 0 ? clearanceProducts : products).slice(0, 4)).map((prod, idx) => {
              const remainingStock = prod.clearanceStock ?? (prod.stock ?? 10);
              const soldCount = prod.totalSold || 0;
              const claimed = prod.clearanceStock
                ? Math.min(95, Math.max(25, Math.round((50 / (50 + prod.clearanceStock)) * 100)))
                : (soldCount > 0 ? Math.min(95, Math.max(15, (soldCount % 80) + 15)) : 50);
              return (
                <div key={prod.id || idx} style={{ display: 'flex', flexDirection: 'column' }}>
                  <ProductCard product={prod} onNavigate={onNavigate} onQuickView={setQuickViewProduct} />
                  {/* Stock claim status bar */}
                  <div style={{ marginTop: '10px', padding: '0 4px' }}>
                    <div className="flash-deal-progress-bg">
                      <div className="flash-deal-progress-fill" style={{ width: `${claimed}%` }} />
                    </div>
                    <div style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontSize: '11px',
                      color: 'var(--c-text-light)',
                      marginTop: '4px'
                    }}>
                      <span style={{ fontWeight: 600, color: '#DC2626', display: 'flex', alignItems: 'center', gap: '5px' }}>
                        <Flame size={12} color="#DC2626" />
                        <span>Đã bán {claimed}%</span>
                      </span>
                      {prod.earliestExpirationDate ? (
                        <span style={{ fontWeight: 600, color: '#B45309' }}>
                          Còn {remainingStock} suất ({prod.daysRemaining != null ? `còn ${prod.daysRemaining} ngày` : 'cận date'})
                        </span>
                      ) : (
                        <span>Chỉ còn {remainingStock} suất</span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* =========================================================================
          5. DANH MỤC NỔI BẬT — HASAKI CIRCULAR ICON TILES
          ========================================================================= */}
      <section id="categories-section" className="customer-container">
        <div className="section-header-editorial">
          <h2 className="section-title-editorial">
            Danh mục sản phẩm
          </h2>
          <p className="section-desc-editorial">
            Chọn danh mục để khám phá các dòng dược mỹ phẩm và dịch vụ trị liệu phù hợp nhất.
          </p>
        </div>

        <div className="category-grid-8">
          {loading ? (
            [1, 2, 3, 4, 5, 6, 7, 8].map(n => (
              <div key={n} style={{ height: '140px', borderRadius: 'var(--radius-md)' }} className="skeleton-shimmer" />
            ))
          ) : (
            categories.slice(0, 8).map((cat) => (
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
                  <ArrowRight size={11} />
                </span>
              </div>
            ))
          )}
        </div>
      </section>

      {/* =========================================================================
          THƯƠNG HIỆU ĐỐI TÁC CHÍNH HÃNG — HASAKI TOP BRANDS
          ========================================================================= */}
      <section id="brand-partners" className="customer-container">
        <div className="section-header-editorial">
          <h2 className="section-title-editorial">
            Thương hiệu chính hãng
          </h2>
          <p className="section-desc-editorial">
            100% sản phẩm có tem phụ tiếng Việt, nguồn gốc minh bạch và chứng từ nhập khẩu chính ngạch từ các tập đoàn dược mỹ phẩm hàng đầu.
          </p>
        </div>

        <div className="brands-grid-12">
          {loading ? (
            [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(n => (
              <div key={n} style={{ height: '90px', borderRadius: 'var(--radius-md)' }} className="skeleton-shimmer" />
            ))
          ) : (
            brands.slice(0, 12).map((brand) => (
              <div
                key={brand.id}
                className="brand-partner-card"
                onClick={() => onNavigate(`products?brandId=${brand.id}&search=${encodeURIComponent(brand.name)}`)}
              >
                <div className="brand-partner-media">
                  <img
                    src={getBrandImage(brand)}
                    alt={brand.name}
                    loading="lazy"
                    onError={e => {
                      e.target.style.display = 'none';
                    }}
                  />
                </div>
                <div className="brand-partner-overlay" />
                <div className="brand-partner-badge">
                  <Check size={10} strokeWidth={3} />
                  <span>Chính hãng</span>
                </div>
                <div className="brand-partner-info">
                  <div className="brand-partner-name">
                    {brand.name}
                  </div>
                  <div className="brand-partner-meta">
                    <span>{brand.productCount !== undefined && brand.productCount > 0 ? `${brand.productCount} sản phẩm` : 'Phân phối chính ngạch'}</span>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '24px' }}>
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

      {/* =========================================================================
          4. GỢI Ý CÁ NHÂN HÓA DÀNH CHO BẠN (AI RECSYS USER-TO-ITEM CENTROID)
          ========================================================================= */}
      <section id="random-picks" className="customer-container">
        <div className="section-header-editorial">
          <h2 className="section-title-editorial">
            Gợi ý cho bạn
          </h2>
          <p className="section-desc-editorial">
            Hệ thống phân tích lịch sử quan tâm và sở thích thành phần để tự động đề xuất dược mỹ phẩm tối ưu nhất cho bạn.
          </p>
        </div>

        {loading ? (
          <div className="customer-product-grid product-skeleton-frame">
            {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
              <ProductCardSkeleton key={n} />
            ))}
          </div>
        ) : (
          <div className="customer-product-grid">
            {((recommendedProducts && recommendedProducts.length > 0) ? recommendedProducts : randomProducts).map((prod) => (
              <ProductCard
                key={prod.id}
                product={prod}
                onNavigate={onNavigate}
                onQuickView={setQuickViewProduct}
              />
            ))}
          </div>
        )}
      </section>

      {/* =========================================================================
          6. TOÀN BỘ SẢN PHẨM TẠI CỬA HÀNG (FULL STORE PRODUCT CATALOG SHOWCASE)
          ========================================================================= */}
      <section id="store-catalog" className="customer-container" style={{ margin: '52px auto' }}>
        <div className="section-header-editorial">
          <h2 className="section-title-editorial">
            Tất cả sản phẩm
          </h2>
          <p className="section-desc-editorial">
            Khám phá đầy đủ {products.length}+ sản phẩm chính ngạch có tem phụ tiếng Việt, bảo quản tiêu chuẩn kho lạnh chuyên dụng.
          </p>
        </div>

        {/* Quick Search & Sort Bar */}
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

        {/* Category Filter Pills — dynamically generated from API categories */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '28px' }}>
          {dynamicCatalogCategories.map((tab) => {
            const active = catalogCategoryFilter === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setCatalogCategoryFilter(tab.id);
                  setVisibleCount(12);
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

        {/* Product Grid */}
        {loading || loadingCategoryProducts ? (
          <div className="customer-product-grid product-skeleton-frame">
            {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
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
            {allStoreProducts.slice(0, visibleCount).map((prod) => (
              <ProductCard
                key={prod.id}
                product={prod}
                onNavigate={onNavigate}
                onQuickView={setQuickViewProduct}
              />
            ))}
          </div>
        )}

        {/* Catalog Navigation & Load More Actions */}
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', marginTop: '36px', flexWrap: 'wrap' }}>
          {allStoreProducts.length > visibleCount && (
            <button
              onClick={() => setVisibleCount((prev) => prev + 8)}
              className="btn-luxury-outline"
              style={{ padding: '12px 28px', fontSize: '13.5px' }}
            >
              <span>Hiển thị thêm ({allStoreProducts.length - visibleCount} sản phẩm còn lại)</span>
            </button>
          )}

          <button
            onClick={() => onNavigate('products')}
            className="btn-luxury-primary"
            style={{ padding: '12px 28px', fontSize: '13.5px' }}
          >
            <span>Khám phá toàn bộ danh mục sản phẩm chi tiết</span>
            <ArrowRight size={15} />
          </button>
        </div>
      </section>

      {/* =========================================================================
          7. SIGNATURE MEDICAL SPA & CLINIC TREATMENTS MENU (DYNAMIC FILTERS)
          ========================================================================= */}
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
            <p className="section-desc-editorial">
              Ứng dụng công nghệ oxy hóa đa tầng, vi kim sinh học và điện di ion giúp dưỡng chất thẩm thấu sâu hơn gấp 10 lần phương pháp bôi thoa thông thường.
            </p>
          </div>

          {/* Dynamic Category Filter Chips for Spa */}
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

          {/* Spa Service Cards Grid - 6 Items Balanced */}
          <div className="spa-grid-6">
            {getFilteredSpa().map((spa) => (
              <div
                key={spa.id}
                className="spa-service-card"
              >
                {/* Spa Service Image */}
                <div className="spa-service-image-box">
                  <img
                    src={getSpaServiceImage(spa)}
                    alt={spa.name}
                    loading="lazy"
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

                <div style={{ padding: '22px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', flex: 1 }}>
                  <div>
                    <span style={{ fontSize: '11px', color: 'var(--c-gold-hover)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                      {spa.categoryName || 'Trị liệu Y Khoa'}
                    </span>
                    <h3 style={{ fontSize: '17px', fontWeight: 700, margin: '6px 0 8px 0', color: 'var(--c-primary)', fontFamily: 'var(--font-sans)', lineHeight: 1.35 }}>
                      {spa.name}
                    </h3>
                    <p style={{ fontSize: '13px', color: 'var(--c-text-muted)', lineHeight: 1.55, margin: '0 0 18px 0' }}>
                      {spa.shortDescription || 'Quy trình chuẩn hóa khử khuẩn khép kín, massage bấm huyệt và phục hồi chuyên sâu.'}
                    </p>
                  </div>

                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    paddingTop: '16px',
                    borderTop: '1px solid var(--c-border-subtle)'
                  }}>
                    <div>
                      <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Giá trọn gói</div>
                      <div style={{ fontSize: '18px', fontWeight: 800, color: 'var(--c-gold-hover)', fontFamily: 'var(--font-mono)' }}>
                        {spa.basePrice ? formatCurrency(spa.basePrice) : 'Liên hệ tư vấn'}
                      </div>
                    </div>

                    <button
                      onClick={() => onNavigate(`booking?serviceId=${spa.id}`)}
                      className="btn-luxury-primary"
                      style={{ padding: '9px 18px', fontSize: '12.5px' }}
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

      {/* =========================================================================
          INTERACTIVE UX OVERLAYS, MODALS & FLOATING CONTROLS
          ========================================================================= */}
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
