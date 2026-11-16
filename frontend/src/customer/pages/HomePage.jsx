import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Sparkles,
  ArrowRight,
  Calendar,
  Clock,
  Zap,
  Award,
  Search,
  Ticket,
  Package
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
import { Modal } from '../../shared/ui/Modal';
import { ImageBannerGallery } from '../../features/banners/ImageBannerGallery';
import { SectionStripBanner, CategoryBannerRow, SectionBannerRow } from '../../features/banners/SectionStripBanner';

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
  const [totalProductCount, setTotalProductCount] = useState(null);
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


  const [clearanceProducts, setClearanceProducts] = useState([]);
  const [curatedFlashProducts, setCuratedFlashProducts] = useState([]);
  const [campaignFlashProducts, setCampaignFlashProducts] = useState([]);
  const [flashOverview, setFlashOverview] = useState(null);
  const [selectedSlotId, setSelectedSlotId] = useState(null);
  const [flashFilterType, setFlashFilterType] = useState('ALL'); // 'ALL' | 'CAMPAIGN' | 'CURATED' | 'CLEARANCE'
  const [flashCountdown, setFlashCountdown] = useState({ hours: '00', minutes: '00', seconds: '00' });
  const [clearanceLoading, setClearanceLoading] = useState(true);
  const [clearanceError, setClearanceError] = useState('');
  const [clearanceAttempt, setClearanceAttempt] = useState(0);

  const [catalogCategoryFilter, setCatalogCategoryFilter] = useState('ALL');
  const [categoryProducts, setCategoryProducts] = useState(null);
  const [loadingCategoryProducts, setLoadingCategoryProducts] = useState(false);
  const [catalogSearchTerm, setCatalogSearchTerm] = useState('');
  const [catalogSortBy, setCatalogSortBy] = useState('DEFAULT');
  const [allBrandsModalOpen, setAllBrandsModalOpen] = useState(false);
  const [brandSearch, setBrandSearch] = useState('');

  const [activeSpaCategory, setActiveSpaCategory] = useState('ALL');

  const FLASH_PAGE_SIZE = 6;
  const [currentFlashTab, setCurrentFlashTab] = useState(0);
  const [isFlashHovered, setIsFlashHovered] = useState(false);

  const activeFlashSlot = useMemo(() => {
    if (!flashOverview?.slots?.length) return null;
    return flashOverview.slots.find((s) => s.campaignId === selectedSlotId) || null;
  }, [flashOverview?.slots, selectedSlotId]);

  const countdownTarget = activeFlashSlot
    ? (activeFlashSlot.isCurrent ? activeFlashSlot.endsAt : activeFlashSlot.startsAt)
    : flashOverview?.endsAt;

  useEffect(() => {
    if (!countdownTarget) return;
    const target = new Date(countdownTarget).getTime();
    let isExpiredHandled = false;
    const updateCountdown = () => {
      const now = Date.now();
      const diff = Math.max(0, target - now);
      const hours = String(Math.floor(diff / (1000 * 60 * 60))).padStart(2, '0');
      const minutes = String(Math.floor((diff / (1000 * 60)) % 60)).padStart(2, '0');
      const seconds = String(Math.floor((diff / 1000) % 60)).padStart(2, '0');
      setFlashCountdown({ hours, minutes, seconds });

      if (diff <= 0 && !isExpiredHandled) {
        isExpiredHandled = true;
        // Flash Sale slot/campaign expired! Re-fetch overview immediately to remove expired items or transition slots
        setTimeout(() => {
          setClearanceAttempt(c => c + 1);
        }, 1000);
      }
    };
    updateCountdown();
    const timer = setInterval(updateCountdown, 1000);
    return () => clearInterval(timer);
  }, [countdownTarget]);

  const allFlashProducts = useMemo(() => {
    if (flashFilterType === 'CAMPAIGN') return campaignFlashProducts;
    if (flashFilterType === 'CLEARANCE') return clearanceProducts;
    if (flashFilterType === 'CURATED') return curatedFlashProducts;
    const seen = new Set();
    const merged = [];
    // Only mix in curated catalog deals if there is an active flash sale campaign
    const baseList = campaignFlashProducts.length > 0
      ? [...campaignFlashProducts, ...curatedFlashProducts, ...clearanceProducts]
      : [...clearanceProducts];
    for (const p of baseList) {
      if (p?.id && !seen.has(p.id)) {
        seen.add(p.id);
        merged.push(p);
      }
    }
    return merged;
  }, [campaignFlashProducts, clearanceProducts, curatedFlashProducts, flashFilterType]);

  const totalFlashTabs = useMemo(() => {
    return Math.max(1, Math.ceil(allFlashProducts.length / FLASH_PAGE_SIZE));
  }, [allFlashProducts.length]);

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
    if (!allFlashProducts || allFlashProducts.length === 0) return [];
    const start = currentFlashTab * FLASH_PAGE_SIZE;
    return allFlashProducts.slice(start, start + FLASH_PAGE_SIZE);
  }, [allFlashProducts, currentFlashTab]);

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
          apiClient.get(`/api/v1/products/recommended-for-you?limit=24&sessionId=${encodeURIComponent(guestSessionId)}`)
        ]);

        if (isMounted) {
          if (prodRes.status === 'fulfilled') {
            const pageData = prodRes.value?.data || prodRes.value;
            const list = pageData?.content || [];
            setProducts(list);
            if (pageData?.totalElements != null) {
              setTotalProductCount(Number(pageData.totalElements));
            }
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
    Promise.allSettled([
      apiClient.get('/api/v1/products/expiring-soon?limit=12&thresholdDays=90', { signal: controller.signal }),
      apiClient.get('/api/v1/products/search?onSale=true&size=12', { signal: controller.signal }),
      apiClient.get('/api/v1/flash-sales/current', { signal: controller.signal }),
    ])
      .then(([expiringRes, onSaleRes, flashRes]) => {
        if (controller.signal.aborted) return;
        const allFailed = expiringRes.status === 'rejected'
          && onSaleRes.status === 'rejected'
          && flashRes.status === 'rejected';
        if (allFailed) {
          setClearanceProducts([]);
          setCuratedFlashProducts([]);
          setCampaignFlashProducts([]);
          setClearanceError('Chưa tải được sản phẩm Flash Sale. Vui lòng thử lại.');
          return;
        }

        const expiringData = expiringRes.status === 'fulfilled' ? (expiringRes.value?.data ?? expiringRes.value) : [];
        const expiringList = Array.isArray(expiringData) ? expiringData : expiringData?.content || [];
        setClearanceProducts(expiringList);

        const onSaleData = onSaleRes.status === 'fulfilled' ? (onSaleRes.value?.data ?? onSaleRes.value) : [];
        const onSaleList = Array.isArray(onSaleData) ? onSaleData : onSaleData?.content || [];
        setCuratedFlashProducts(onSaleList);

        if (flashRes.status === 'fulfilled') {
          const fData = flashRes.value?.data ?? flashRes.value;
          if (fData) {
            setFlashOverview(fData);
            if (fData.currentSlotId) setSelectedSlotId(fData.currentSlotId);
            if (Array.isArray(fData.items) && fData.items.length > 0) {
              const mapped = fData.items.map((it) => ({
                id: it.productId,
                name: it.productName,
                slug: `product-${it.productId}`,
                thumbnailUrl: it.thumbnailUrl,
                minPrice: it.flashPrice,
                price: it.flashPrice,
                originalPrice: it.originalPrice,
                discountPrice: it.flashPrice,
                isFeatured: true,
                onSale: true,
                status: 'ACTIVE',
                totalStock: it.remainingQuota,
                totalSold: it.quantitySold,
                flashQuota: it.quantityQuota,
                flashSold: it.quantitySold,
                flashPercent: it.soldPercent,
                variants: [{
                  id: it.productVariantId,
                  sku: it.sku,
                  variantName: it.variantName,
                  price: it.originalPrice,
                  discountPrice: it.flashPrice,
                  stockQuantity: it.remainingQuota,
                  isActive: true,
                }],
              }));
              setCampaignFlashProducts(mapped);
            } else {
              setCampaignFlashProducts([]);
            }
          } else {
            setCampaignFlashProducts([]);
          }
        } else {
          setCampaignFlashProducts([]);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setClearanceProducts([]);
          setCuratedFlashProducts([]);
          setCampaignFlashProducts([]);
          setClearanceError('Chưa tải được sản phẩm Flash Sale. Vui lòng thử lại.');
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

  const displayedRecommendations = useMemo(() => {
    const list = Array.isArray(recommendedProducts) ? [...recommendedProducts] : [];
    const seenIds = new Set(list.map(p => p.id));
    for (const p of randomProducts) {
      if (list.length >= sectionLimits.recommendations) break;
      if (p?.id && !seenIds.has(p.id)) {
        seenIds.add(p.id);
        list.push(p);
      }
    }
    return list.slice(0, sectionLimits.recommendations);
  }, [recommendedProducts, randomProducts, sectionLimits.recommendations]);

  const openCatalog = () => {
    const params = new URLSearchParams();
    if (catalogCategoryFilter !== 'ALL') params.set('categoryId', catalogCategoryFilter);
    if (catalogSearchTerm.trim()) params.set('search', catalogSearchTerm.trim());
    const sort = { PRICE_ASC: 'price_asc', PRICE_DESC: 'price_desc', RATING: 'rating' }[catalogSortBy];
    if (sort) params.set('sort', sort);
    onNavigate(`products${params.size ? `?${params.toString()}` : ''}`);
  };

  const dynamicCatalogCategories = useMemo(() => {
    const countLabel = totalProductCount ?? products.length;
    const list = [{ id: 'ALL', label: `Tất cả sản phẩm (${countLabel})` }];
    if (categories.length > 0) {
      categories.slice(0, 7).forEach(cat => {
        list.push({ id: String(cat.id), label: cat.name });
      });
    }
    return list;
  }, [categories, products.length, totalProductCount]);

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

  const hasFlashCampaign = campaignFlashProducts.length > 0 || Boolean(flashOverview?.slots && flashOverview.slots.length > 0);
  const hasAnyFlashProducts = hasFlashCampaign || clearanceProducts.length > 0;
  const showFlashSection = clearanceLoading || Boolean(clearanceError) || hasAnyFlashProducts;

  return (
    <div ref={homePageRef} className="customer-home-page" style={{ display: 'flex', flexDirection: 'column', gap: '0px', paddingBottom: '0px' }}>

      <section
        className="hero-slider-container"
        aria-label="Bộ sưu tập và ưu đãi nổi bật"
      >
        <div style={{ position: 'relative', zIndex: 3, width: '100%' }}>
          <ImageBannerGallery banners={backendBanners} />

        </div>
      </section>

      {showFlashSection && (
        <section
          id="flash-sale"
          className="customer-container"
          onMouseEnter={() => setIsFlashHovered(true)}
          onMouseLeave={() => setIsFlashHovered(false)}
        >
          <div className="flash-sale-panel" style={{
            backgroundColor: '#FFFFFF',
            borderRadius: '12px',
            border: '1px solid #FFE5D9',
            borderTop: '3px solid #FFB4A2',
            padding: 'clamp(12px, 1.5vw, 20px)',
            boxShadow: '0 4px 16px rgba(255, 180, 162, 0.12)'
          }}>
            <div className="flash-sale-header" style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '16px',
              marginBottom: '20px',
              paddingBottom: '16px',
              borderBottom: '1px solid #FFE5D9'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                  <div className="section-title-wrap">
                    <span className="campaign-eyebrow"><Zap size={12} /> ƯU ĐÃI GIỚI HẠN</span>
                    <h2 className="section-title-highlight">
                      <Zap size={22} className="section-title-icon" />
                      FLASH SALE GIỜ VÀNG • GIÁ SỐC ONLINE
                    </h2>
                  </div>

                  {/* Real-time Countdown Timer */}
                  {countdownTarget && (
                    <div className="flash-sale-countdown" style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '5px',
                      background: '#FFE5D9',
                      border: '1px solid #FFB4A2',
                      padding: '3px 10px',
                      borderRadius: '6px',
                      fontSize: '11.5px',
                      fontWeight: 700,
                      color: '#4A3535'
                    }}>
                      <Clock size={13} color="#E5989B" />
                      <span style={{ textTransform: 'uppercase', fontSize: '10.5px' }}>
                        {activeFlashSlot && !activeFlashSlot.isCurrent ? 'Bắt đầu sau:' : 'Kết thúc trong:'}
                      </span>
                      <span style={{ background: '#FFB4A2', color: '#FFF', padding: '1px 5px', borderRadius: '3px', fontVariantNumeric: 'tabular-nums', fontWeight: 800 }}>{flashCountdown.hours}</span>
                      <span>:</span>
                      <span style={{ background: '#FFB4A2', color: '#FFF', padding: '1px 5px', borderRadius: '3px', fontVariantNumeric: 'tabular-nums', fontWeight: 800 }}>{flashCountdown.minutes}</span>
                      <span>:</span>
                      <span style={{ background: '#FFB4A2', color: '#FFF', padding: '1px 5px', borderRadius: '3px', fontVariantNumeric: 'tabular-nums', fontWeight: 800 }}>{flashCountdown.seconds}</span>
                    </div>
                  )}
                </div>

                <div className="flash-sale-filters" style={{ display: 'inline-flex', gap: '6px', backgroundColor: '#FFE5D9', padding: '3px 6px', borderRadius: '6px', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    style={{
                      border: 0,
                      padding: '4px 10px',
                      borderRadius: '4px',
                      fontSize: '12px',
                      fontWeight: 700,
                      cursor: 'pointer',
                      backgroundColor: flashFilterType === 'ALL' ? '#FFB4A2' : 'transparent',
                      color: flashFilterType === 'ALL' ? '#FFFFFF' : '#4A3535',
                    }}
                    onClick={() => { setFlashFilterType('ALL'); setCurrentFlashTab(0); }}
                  >
                    Tất cả ({allFlashProducts.length})
                  </button>
                  {campaignFlashProducts.length > 0 && (
                    <button
                      type="button"
                      style={{
                        border: 0,
                        padding: '4px 10px',
                        borderRadius: '4px',
                        fontSize: '12px',
                        fontWeight: 700,
                        cursor: 'pointer',
                        backgroundColor: flashFilterType === 'CAMPAIGN' ? '#FFB4A2' : 'transparent',
                        color: flashFilterType === 'CAMPAIGN' ? '#FFFFFF' : '#4A3535',
                      }}
                      onClick={() => { setFlashFilterType('CAMPAIGN'); setCurrentFlashTab(0); }}
                    >
                      Khung giờ vàng ({campaignFlashProducts.length})
                    </button>
                  )}
                  {campaignFlashProducts.length > 0 && curatedFlashProducts.length > 0 && (
                    <button
                      type="button"
                      style={{
                        border: 0,
                        padding: '4px 10px',
                        borderRadius: '4px',
                        fontSize: '12px',
                        fontWeight: 700,
                        cursor: 'pointer',
                        backgroundColor: flashFilterType === 'CURATED' ? '#FFB4A2' : 'transparent',
                        color: flashFilterType === 'CURATED' ? '#FFFFFF' : '#4A3535',
                      }}
                      onClick={() => { setFlashFilterType('CURATED'); setCurrentFlashTab(0); }}
                    >
                      Flash Sale tuyển chọn ({curatedFlashProducts.length})
                    </button>
                  )}
                  {clearanceProducts.length > 0 && (
                    <button
                      type="button"
                      style={{
                        border: 0,
                        padding: '4px 10px',
                        borderRadius: '4px',
                        fontSize: '12px',
                        fontWeight: 700,
                        cursor: 'pointer',
                        backgroundColor: flashFilterType === 'CLEARANCE' ? '#FFB4A2' : 'transparent',
                        color: flashFilterType === 'CLEARANCE' ? '#FFFFFF' : '#4A3535',
                      }}
                      onClick={() => { setFlashFilterType('CLEARANCE'); setCurrentFlashTab(0); }}
                    >
                      Cận date ({clearanceProducts.length})
                    </button>
                  )}
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
                <p style={{ margin: 0, fontSize: '13px', color: 'var(--c-text-muted)' }}>
                  {flashFilterType === 'CLEARANCE' ? 'Lô còn 31–90 ngày, giá ưu đãi do cửa hàng đặt.' : flashFilterType === 'CURATED' ? 'Sản phẩm khuyến mãi sốc được tuyển chọn đặc biệt.' : 'Ưu đãi Flash Sale tuyển chọn và lô cận date chính hãng.'}
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

            <SectionStripBanner banners={backendBanners} position="SECTION_FLASH_SALE" priority />

            {/* Time Slot navigation pills */}
            {flashOverview?.slots && flashOverview.slots.length > 1 && (
              <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '12px', marginBottom: '8px' }}>
                {flashOverview.slots.map((s) => {
                  const isSelected = s.campaignId === (selectedSlotId || flashOverview.currentSlotId);
                  const formatSlotTime = (iso) => {
                    try {
                      const d = new Date(iso);
                      return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
                    } catch { return ''; }
                  };
                  return (
                    <button
                      key={s.campaignId}
                      type="button"
                      onClick={async () => {
                        setSelectedSlotId(s.campaignId);
                        setFlashFilterType('CAMPAIGN');
                        setCurrentFlashTab(0);
                        try {
                          const res = await apiClient.get(ENDPOINTS.FLASH_SALES.PUBLIC_CAMPAIGN_ITEMS(s.campaignId));
                          const items = res?.data ?? res ?? [];
                          if (Array.isArray(items)) {
                            const mapped = items.map((it) => ({
                              id: it.productId,
                              name: it.productName,
                              slug: `product-${it.productId}`,
                              thumbnailUrl: it.thumbnailUrl,
                              minPrice: it.flashPrice,
                              price: it.flashPrice,
                              originalPrice: it.originalPrice,
                              discountPrice: it.flashPrice,
                              isFeatured: true,
                              onSale: true,
                              isUpcomingFlash: !s.isCurrent,
                              status: 'ACTIVE',
                              totalStock: it.remainingQuota,
                              totalSold: it.quantitySold,
                              flashQuota: it.quantityQuota,
                              flashSold: it.quantitySold,
                              flashPercent: it.soldPercent,
                              variants: [{
                                id: it.productVariantId,
                                sku: it.sku,
                                variantName: it.variantName,
                                price: it.originalPrice,
                                discountPrice: it.flashPrice,
                                stockQuantity: it.remainingQuota,
                                isActive: true,
                              }],
                            }));
                            setCampaignFlashProducts(mapped);
                          }
                        } catch (err) {
                          console.warn('Could not load slot items', err);
                        }
                      }}
                      style={{
                        padding: '8px 14px',
                        borderRadius: '8px',
                        border: isSelected ? '1.5px solid #DC2626' : '1px solid #E5E7EB',
                        backgroundColor: isSelected ? '#FEF2F2' : '#FFFFFF',
                        color: isSelected ? '#DC2626' : '#374151',
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        minWidth: '100px',
                        transition: 'all 0.15s ease'
                      }}
                    >
                      <strong style={{ fontSize: '13px', fontVariantNumeric: 'tabular-nums' }}>
                        {formatSlotTime(s.startsAt)} - {formatSlotTime(s.endsAt)}
                      </strong>
                      <span style={{ fontSize: '11px', marginTop: '2px', fontWeight: 600, color: s.isCurrent ? '#059669' : '#6B7280' }}>
                        {s.statusLabel}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}

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
            ) : allFlashProducts.length === 0 ? (
              <p role="status">Hiện chưa có sản phẩm Flash Sale hoặc xả kho khả dụng.</p>
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
      )}

      <section
        id="categories-section"
        className="customer-container"
        onMouseEnter={() => setIsCategoryHovered(true)}
        onMouseLeave={() => setIsCategoryHovered(false)}
      >
        <div className="section-header-editorial">
          <span className="section-eyebrow-badge">
            Danh mục nổi bật
          </span>
          <h2 className="section-title-highlight">
            <Sparkles size={22} className="section-title-icon" />
            Danh Mục Sản Phẩm
          </h2>
        </div>


        <CategoryBannerRow banners={backendBanners} />

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
          <span className="section-eyebrow-badge">
            Đối tác uy tín
          </span>
          <h2 className="section-title-highlight">
            <Award size={22} className="section-title-icon" />
            Thương Hiệu Chính Hãng
          </h2>
        </div>

        <SectionBannerRow banners={backendBanners} position="SECTION_BRANDS" label="Ưu đãi thương hiệu" />

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
                  className="brand-logo-tile brand-partner-box"
                  onClick={() => onNavigate(`products?brandId=${brand.id}&brandName=${encodeURIComponent(brand.name)}`)}
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
            onClick={() => setAllBrandsModalOpen(true)}
            className="btn-luxury-outline"
            style={{ padding: '9px 24px', fontSize: '13px' }}
          >
            <span>Xem tất cả thương hiệu</span>
            <ArrowRight size={14} />
          </button>
        </div>
      </section>

      <section id="random-picks" className="customer-container">
        <div className="recommendations-box" style={{
          backgroundColor: '#FFE5D9',
          borderRadius: '12px',
          border: '1px solid #FFB4A2',
          borderTop: '3px solid #E5989B',
          padding: 'clamp(12px, 1.5vw, 20px)',
          boxShadow: '0 4px 16px rgba(229, 152, 155, 0.12)'
        }}>
          <div className="recommendations-header" style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
            marginBottom: '20px',
            paddingBottom: '16px',
            borderBottom: '1px solid rgba(229, 152, 155, 0.25)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              <div className="section-title-wrap">
                <span className="campaign-eyebrow"><Sparkles size={12} /> BEAUTY SELECTION</span>
                <h2 className="section-title-highlight">
                  <Sparkles size={22} className="section-title-icon" />
                  GỢI Ý DÀNH CHO BẠN
                </h2>
              </div>
              <span style={{
                backgroundColor: '#FAD2E1',
                color: '#8C2A47',
                border: '1px solid #F4ACB7',
                padding: '3px 10px',
                borderRadius: '9999px',
                fontSize: '11px',
                fontWeight: 800,
                letterSpacing: '0.04em',
                textTransform: 'uppercase'
              }}>
                Dành riêng cho bạn
              </span>
            </div>
            <p style={{ margin: 0, fontSize: '13px', color: '#7A666D' }}>
              Cá nhân loại sản phẩm
            </p>
          </div>

          <SectionStripBanner banners={backendBanners} position="SECTION_RECOMMENDATIONS" />

          {loading ? (
            <div className="customer-product-grid product-skeleton-frame">
              {Array.from({ length: sectionLimits.recommendations }, (_, n) => (
                <ProductCardSkeleton key={n} />
              ))}
            </div>
          ) : (
            <div className="customer-product-grid">
              {displayedRecommendations.map((prod) => (
                <ProductCard
                  key={prod.id}
                  product={prod}
                  onNavigate={onNavigate}
                  onQuickView={setQuickViewProduct}
                />
              ))}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'center', marginTop: '24px' }}>
            <button
              onClick={() => onNavigate('products')}
              className="btn-luxury-outline"
              style={{
                borderColor: '#E5989B',
                color: '#E5989B',
                backgroundColor: '#FFFFFF',
                borderRadius: '9999px',
                padding: '9px 24px',
                fontSize: '13px',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                cursor: 'pointer'
              }}
            >
              <span>Xem thêm sản phẩm</span>
              <ArrowRight size={14} />
            </button>
          </div>
        </div>
      </section>

      <section id="store-catalog" className="customer-container" style={{ margin: '0 auto' }}>
        <div className="section-header-editorial">
          <span className="section-eyebrow-badge">
            Khám phá làm đẹp
          </span>
          <h2 className="section-title-highlight">
            <Package size={22} className="section-title-icon" />
            Tất Cả Sản Phẩm
          </h2>
        </div>

        <SectionStripBanner banners={backendBanners} position="SECTION_CATALOG" />

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
        backgroundColor: '#FAD2E1',
        padding: '36px 0',
        borderTop: '1px solid #FFE5D9',
        borderBottom: 'none',
        marginBottom: 0
      }}>
        <div className="customer-container">
          <div className="section-header-editorial">
            <span className="section-eyebrow-badge">
              Liệu trình chuyên sâu
            </span>
            <h2 className="section-title-highlight">
              <Calendar size={22} className="section-title-icon" />
              Dịch Vụ Spa Làm Đẹp
            </h2>
          </div>

          <SectionStripBanner banners={backendBanners} position="SECTION_SPA" />

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
                      onClick={() => onNavigate(spa.serviceType === 'TREATMENT_COURSE' ? 'treatments' : `booking?serviceId=${spa.id}`)}
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

          <div style={{ display: 'flex', justifyContent: 'center', gap: '14px', flexWrap: 'wrap', marginTop: '40px' }}>
            <button
              onClick={() => onNavigate('booking')}
              className="btn-luxury-gold pulse-glow"
              style={{ padding: '14px 32px', fontSize: '14px' }}
            >
              <span>Xem Bảng Giá & Đặt Lịch Hẹn Spa</span>
              <Calendar size={16} />
            </button>
            <button
              onClick={() => onNavigate('service-packages')}
              className="btn-luxury-outline"
              style={{ padding: '14px 28px', fontSize: '14px', background: '#FFFFFF', display: 'inline-flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}
            >
              <Ticket size={16} color="var(--c-primary)" />
              <span>Gói Dịch Vụ Nhiều Lượt Tiết Kiệm</span>
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

      <Modal
        isOpen={allBrandsModalOpen}
        onClose={() => { setAllBrandsModalOpen(false); setBrandSearch(''); }}
        title="Tất cả thương hiệu chính hãng đối tác"
        maxWidth="800px"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ position: 'relative' }}>
            <input
              type="search"
              placeholder="Tìm kiếm thương hiệu..."
              value={brandSearch}
              onChange={(e) => setBrandSearch(e.target.value)}
              style={{
                width: '100%',
                padding: '10px 14px 10px 38px',
                borderRadius: '8px',
                border: '1px solid var(--c-border, #E5E7EB)',
                fontSize: '13.5px'
              }}
            />
            <Search size={16} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: '#9CA3AF' }} />
          </div>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
            gap: 12,
            maxHeight: '440px',
            overflowY: 'auto',
            padding: '4px'
          }}>
            {brands
              .filter(b => !brandSearch.trim() || b.name.toLowerCase().includes(brandSearch.trim().toLowerCase()))
              .map(brand => (
                <div
                  key={brand.id}
                  onClick={() => {
                    setAllBrandsModalOpen(false);
                    setBrandSearch('');
                    onNavigate(`products?brandId=${brand.id}&brandName=${encodeURIComponent(brand.name)}`);
                  }}
                  style={{
                    padding: '14px 10px',
                    borderRadius: '10px',
                    border: '1px solid var(--c-border, #E5E7EB)',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    textAlign: 'center',
                    background: '#FFFFFF',
                    transition: 'all 0.2s ease',
                    minHeight: '110px'
                  }}
                >
                  <BrandCardLogo brand={brand} logoUrl={getBrandImage(brand)} />
                  <span style={{ fontSize: '12px', fontWeight: 600, marginTop: '8px', color: '#1F2937' }}>{brand.name}</span>
                </div>
              ))}
          </div>
        </div>
      </Modal>

      <ToastNotification
        toasts={toasts}
        onDismiss={removeToast}
      />

      <ScrollProgressTop />

    </div>
  );
};

export default HomePage;
