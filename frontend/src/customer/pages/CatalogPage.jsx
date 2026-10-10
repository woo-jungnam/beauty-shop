import React, { useState, useEffect, useMemo } from 'react';
import { Filter, SlidersHorizontal, Search, RefreshCw, X, Sparkles, Tag, Check, Flame, Zap } from 'lucide-react';
import { ProductCard } from '../components/product/ProductCard';
import { ProductGridSkeleton } from '../components/product/ProductCardSkeleton';
import { apiClient } from '../../shared/api/client';
import { cleanDisplayName } from '../../shared/utils/formatters';

const ACTIVE_INGREDIENT_FILTERS = [
  { id: 'ALL', label: 'Tất cả hoạt chất' },
  { id: 'niacinamide', label: 'Niacinamide (Làm sáng/Kiềm dầu)' },
  { id: 'panthenol b5', label: 'Panthenol B5 (Phục hồi da)' },
  { id: 'salicylic acid', label: 'BHA / Salicylic Acid (Trị mụn)' },
  { id: 'hyaluronic acid', label: 'Hyaluronic Acid (Cấp nước)' },
  { id: 'ceramide', label: 'Ceramide (Hàng rào bảo vệ)' },
];

export const CatalogPage = ({
  onNavigate,
  initialSearch = '',
  initialCategoryId = '',
  initialCategorySlug = '',
  initialCategoryName = '',
  initialBrandId = '',
  initialBrandSlug = '',
  initialBrandName = '',
  initialSort = 'popular',
  initialFilter = ''
}) => {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [loading, setLoading] = useState(true);

  // Dynamic Filters State
  const [keyword, setKeyword] = useState(initialSearch);
  const [debouncedKeyword, setDebouncedKeyword] = useState(initialSearch);
  const [selectedCategoryId, setSelectedCategoryId] = useState(initialCategoryId);
  const [selectedCategoryName, setSelectedCategoryName] = useState(initialCategoryName);
  const [selectedBrandId, setSelectedBrandId] = useState(initialBrandId);
  const [selectedBrandName, setSelectedBrandName] = useState(initialBrandName);
  const [selectedSkinType, setSelectedSkinType] = useState('ALL');
  const [selectedActive, setSelectedActive] = useState('ALL');
  const [selectedPriceRange, setSelectedPriceRange] = useState('ALL');
  const [sortBy, setSortBy] = useState(initialSort || 'popular');
  const [activeFilter, setActiveFilter] = useState(initialFilter); // 'sale', 'best-seller', etc.

  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);

  // Debounce keyword để tránh fetch spam gây khựng khi nhập tìm kiếm
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword);
    }, 280);
    return () => clearTimeout(timer);
  }, [keyword]);

  // Sync state if initial props change
  useEffect(() => {
    setKeyword(initialSearch);
    setSelectedCategoryId(initialCategoryId);
    setSelectedCategoryName(initialCategoryName);
    setSelectedBrandId(initialBrandId);
    setSelectedBrandName(initialBrandName);
    if (initialSort) setSortBy(initialSort);
    if (initialFilter) setActiveFilter(initialFilter);
  }, [
    initialSearch, 
    initialCategoryId, 
    initialCategoryName, 
    initialBrandId, 
    initialBrandName, 
    initialSort, 
    initialFilter
  ]);

  const getProductPrice = (product) => Number(
    product.minPrice || product.basePrice || product.variants?.[0]?.discountPrice || product.variants?.[0]?.price || product.price || 0
  );

  // 1. Fetch dynamic categories & brands for sidebar filters
  useEffect(() => {
    const fetchMetadata = async () => {
      try {
        const [catRes, brandRes] = await Promise.allSettled([
          apiClient.get('/api/v1/categories/root'),
          apiClient.get('/api/v1/brands?size=24')
        ]);

        if (catRes.status === 'fulfilled') {
          const catData = catRes.value?.data || catRes.value || [];
          setCategories(Array.isArray(catData) ? catData : []);

          // Auto-resolve category by ID or Slug
          if (initialCategoryId && !initialCategoryName && Array.isArray(catData)) {
            for (const root of catData) {
              if (String(root.id) === String(initialCategoryId)) {
                setSelectedCategoryName(root.name);
                break;
              }
              const child = root.children?.find(c => String(c.id) === String(initialCategoryId));
              if (child) {
                setSelectedCategoryName(child.name);
                break;
              }
            }
          } else if (!initialCategoryId && initialCategorySlug && Array.isArray(catData)) {
            for (const root of catData) {
              if (root.slug === initialCategorySlug) {
                setSelectedCategoryId(String(root.id));
                setSelectedCategoryName(root.name);
                break;
              }
              const child = root.children?.find(c => c.slug === initialCategorySlug);
              if (child) {
                setSelectedCategoryId(String(child.id));
                setSelectedCategoryName(child.name);
                break;
              }
            }
          }
        }

        if (brandRes.status === 'fulfilled') {
          const brandData = brandRes.value?.data?.content || brandRes.value?.content || [];
          setBrands(Array.isArray(brandData) ? brandData : []);

          if (initialBrandId && !initialBrandName && Array.isArray(brandData)) {
            const found = brandData.find(b => String(b.id) === String(initialBrandId));
            if (found) setSelectedBrandName(found.name);
          } else if (!initialBrandId && initialBrandSlug && Array.isArray(brandData)) {
            const found = brandData.find(b => b.slug === initialBrandSlug);
            if (found) {
              setSelectedBrandId(String(found.id));
              setSelectedBrandName(found.name);
            }
          }
        }
      } catch (err) {
        console.error('Failed to load filter metadata', err);
      }
    };
    fetchMetadata();
  }, [initialCategoryId, initialCategorySlug, initialCategoryName, initialBrandId, initialBrandSlug, initialBrandName]);

  // 2. Fetch catalog products dynamically based on category / brand / keyword
  useEffect(() => {
    let isCancelled = false;
    const fetchCatalog = async () => {
      setLoading(true);
      try {
        const params = new URLSearchParams({ size: '60' });
        if (debouncedKeyword && debouncedKeyword.trim()) params.set('keyword', debouncedKeyword.trim());
        if (selectedCategoryId) params.set('categoryId', selectedCategoryId);
        if (selectedBrandId) params.set('brandId', selectedBrandId);
        if (selectedSkinType !== 'ALL') params.set('skinType', selectedSkinType);
        if (selectedActive !== 'ALL') params.set('keyword', selectedActive);
        if (selectedPriceRange === 'UNDER_300') params.set('maxPrice', '299999');
        if (selectedPriceRange === '300_600') {
          params.set('minPrice', '300000');
          params.set('maxPrice', '600000');
        }
        if (selectedPriceRange === 'OVER_600') params.set('minPrice', '600001');

        const sortParam = sortBy === 'price_asc' ? '&sort=price,asc' : sortBy === 'price_desc' ? '&sort=price,desc' : sortBy === 'rating' ? '&sort=averageRating,desc' : '';
        const res = await apiClient.get(`/api/v1/products/search?${params.toString()}${sortParam}`);
        const list = res?.data?.content || res?.content || [];
        if (!isCancelled) {
          setProducts(Array.isArray(list) ? list : []);
        }
      } catch (err) {
        console.error('Failed to load catalog products', err);
        if (!isCancelled) setProducts([]);
      } finally {
        if (!isCancelled) setLoading(false);
      }
    };
    fetchCatalog();
    return () => { isCancelled = true; };
  }, [selectedCategoryId, selectedBrandId, debouncedKeyword, selectedSkinType, selectedActive, selectedPriceRange, sortBy]);

  // 3. Client-side faceted filtering & sorting
  const filteredProducts = useMemo(() => {
    let result = [...products];

    // Filter by keyword if browsing within category/brand
    if ((selectedCategoryId || selectedBrandId) && keyword && keyword.trim()) {
      const kw = keyword.trim().toLowerCase();
      result = result.filter(p =>
        p.name?.toLowerCase().includes(kw) ||
        p.shortDescription?.toLowerCase().includes(kw) ||
        p.brand?.name?.toLowerCase().includes(kw)
      );
    }

    // Filter by special deals (Flash Sale)
    if (activeFilter === 'sale') {
      result = result.filter(p => (p.basePrice && p.basePrice < 350000) || p.isFeatured);
    }

    // Sorting
    if (sortBy === 'price_asc') {
      result.sort((a, b) => getProductPrice(a) - getProductPrice(b));
    } else if (sortBy === 'price_desc') {
      result.sort((a, b) => getProductPrice(b) - getProductPrice(a));
    } else if (sortBy === 'rating') {
      result.sort((a, b) => (b.averageRating || 5) - (a.averageRating || 5));
    } else if (sortBy === 'popular' || activeFilter === 'best-seller') {
      result.sort((a, b) => {
        const soldB = (b.totalSold || 0) + (b.isFeatured ? 40 : 0);
        const soldA = (a.totalSold || 0) + (a.isFeatured ? 40 : 0);
        return soldB - soldA;
      });
    }

    return result;
  }, [products, keyword, selectedCategoryId, selectedBrandId, selectedSkinType, selectedActive, selectedPriceRange, sortBy, activeFilter]);

  const clearAllFilters = () => {
    setKeyword('');
    setDebouncedKeyword('');
    setSelectedCategoryId('');
    setSelectedCategoryName('');
    setSelectedBrandId('');
    setSelectedBrandName('');
    setSelectedSkinType('ALL');
    setSelectedActive('ALL');
    setSelectedPriceRange('ALL');
    setActiveFilter('');
    setSortBy('popular');
    if (onNavigate) onNavigate('products', { scroll: false });
  };

  const hasActiveFilters = Boolean(
    keyword || 
    selectedCategoryId || 
    selectedBrandId || 
    selectedSkinType !== 'ALL' || 
    selectedActive !== 'ALL' || 
    selectedPriceRange !== 'ALL' ||
    activeFilter
  );

  // Dynamic Header Title
  const getBannerTitle = () => {
    if (selectedCategoryName) return cleanDisplayName(selectedCategoryName);
    if (selectedBrandName) return `Thương Hiệu ${cleanDisplayName(selectedBrandName)}`;
    if (activeFilter === 'sale') return 'Giờ Vàng Deal — Flash Sale';
    if (activeFilter === 'best-seller') return 'Top Sản Phẩm Bán Chạy Nhất';
    if (keyword) return `Kết Quả Cho: "${cleanDisplayName(keyword)}"`;
    return 'Mỹ Phẩm Khoa Học & Phục Hồi Làn Da';
  };

  return (
    <div className="customer-container" style={{ padding: '32px 20px 60px 20px' }}>
      {/* 1. Header Banner */}
      <div style={{ marginBottom: '28px' }}>
        <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--c-gold-hover)', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
          DANH MỤC DƯỢC MỸ PHẨM CHÍNH HÃNG
        </span>
        <h1 style={{ fontSize: '32px', margin: '4px 0 8px 0', fontFamily: 'var(--font-serif)', color: 'var(--c-primary)' }}>
          {getBannerTitle()}
        </h1>
        <p style={{ fontSize: '14px', color: 'var(--c-text-muted)', margin: 0 }}>
          {selectedCategoryName
            ? `Bộ sưu tập các sản phẩm ${cleanDisplayName(selectedCategoryName).toLowerCase()} chuẩn y khoa, an toàn cho mọi nền da.`
            : 'Lọc sản phẩm chuyên biệt theo tình trạng da, nồng độ hoạt chất (BHA, B5, Niacinamide) và thương hiệu.'}
        </p>
      </div>

      <button
        type="button"
        className="catalog-mobile-filter-trigger"
        onClick={() => setMobileFilterOpen(true)}
        aria-expanded={mobileFilterOpen}
      >
        <SlidersHorizontal size={17} />
        <span>Bộ lọc sản phẩm</span>
        {hasActiveFilters && <span className="catalog-filter-count">Đang áp dụng</span>}
      </button>

      {mobileFilterOpen && (
        <button
          type="button"
          className="catalog-filter-backdrop"
          aria-label="Đóng bộ lọc"
          onClick={() => setMobileFilterOpen(false)}
        />
      )}

      {/* 2. Main 2-Column Faceted Layout */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '260px 1fr',
        gap: '32px',
        alignItems: 'start'
      }} className="catalog-grid-layout">
        
        {/* ================= LEFT SIDEBAR FILTER ================= */}
        <aside className={`catalog-filter-panel ${mobileFilterOpen ? 'is-open' : ''}`} style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 'var(--radius-md)',
          border: '1px solid var(--c-border)',
          padding: '24px 20px',
          boxShadow: 'var(--shadow-card)',
          position: 'sticky',
          top: '90px',
          maxHeight: 'calc(100vh - 110px)',
          overflowY: 'auto'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700, fontSize: '15px' }}>
              <Filter size={16} color="var(--c-gold)" />
              <span>Bộ Lọc Chuyên Sâu</span>
            </div>
            {hasActiveFilters && (
              <button 
                onClick={clearAllFilters}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--c-danger-red)',
                  fontSize: '11px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Đặt lại
              </button>
            )}
            <button
              type="button"
              className="catalog-filter-close"
              onClick={() => setMobileFilterOpen(false)}
              aria-label="Đóng bộ lọc"
            >
              <X size={20} />
            </button>
          </div>

          {/* Group 1: Search by name */}
          <div style={{ marginBottom: '22px' }}>
            <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '8px', color: 'var(--c-primary)' }}>
              TỪ KHÓA TÌM KIẾM
            </label>
            <div style={{ position: 'relative' }}>
              <input 
                type="text"
                placeholder="Nhập tên sản phẩm..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 30px 8px 12px',
                  fontSize: '12px',
                  border: '1px solid var(--c-border)',
                  borderRadius: 'var(--radius-sm)',
                  outline: 'none'
                }}
              />
              {keyword && (
                <button
                  onClick={() => {
                    setKeyword('');
                    setDebouncedKeyword('');
                    if (onNavigate) onNavigate('products', { scroll: false });
                  }}
                  style={{
                    position: 'absolute',
                    right: '6px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'transparent',
                    border: 'none',
                    cursor: 'pointer',
                    color: '#94A3B8'
                  }}
                >
                  <X size={13} />
                </button>
              )}
            </div>
          </div>

          {/* Group 2: Dynamic Categories from Backend */}
          {categories.length > 0 && (
            <div style={{ marginBottom: '22px' }}>
              <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '10px', color: 'var(--c-primary)' }}>
                DANH MỤC SẢN PHẨM
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '5px', fontSize: '13px', maxHeight: '200px', overflowY: 'auto' }}>
                <div
                  onClick={() => {
                    setSelectedCategoryId('');
                    setSelectedCategoryName('');
                    setKeyword('');
                    setDebouncedKeyword('');
                    if (onNavigate) onNavigate('products', { scroll: false });
                  }}
                  style={{
                    padding: '6px 8px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    backgroundColor: !selectedCategoryId ? 'var(--c-gold-light)' : 'transparent',
                    color: !selectedCategoryId ? 'var(--c-gold-hover)' : 'var(--c-primary)',
                    fontWeight: !selectedCategoryId ? 700 : 500,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                  }}
                >
                  <span>Tất cả danh mục</span>
                  {!selectedCategoryId && <Check size={13} />}
                </div>

                {categories.map((root) => {
                  const isRootActive = String(selectedCategoryId) === String(root.id);
                  return (
                    <div key={root.id}>
                      <div
                        onClick={() => {
                          setSelectedCategoryId(root.id);
                          setSelectedCategoryName(root.name);
                          setKeyword('');
                          setDebouncedKeyword('');
                          if (onNavigate) onNavigate(`products?categoryId=${root.id}&name=${encodeURIComponent(root.name)}`, { scroll: false });
                        }}
                        style={{
                          padding: '6px 8px',
                          borderRadius: '6px',
                          cursor: 'pointer',
                          backgroundColor: isRootActive ? 'var(--c-gold-light)' : 'transparent',
                          color: isRootActive ? 'var(--c-gold-hover)' : 'var(--c-primary)',
                          fontWeight: isRootActive ? 700 : 600,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between'
                        }}
                      >
                        <span>{cleanDisplayName(root.name)}</span>
                        {isRootActive && <Check size={13} />}
                      </div>

                      {/* Subcategories */}
                      {root.children && root.children.length > 0 && (
                        <div style={{ paddingLeft: '12px', display: 'flex', flexDirection: 'column', gap: '3px', marginTop: '2px', borderLeft: '1.5px solid var(--c-border-subtle)', marginLeft: '8px' }}>
                          {root.children.map((child) => {
                            const isChildActive = String(selectedCategoryId) === String(child.id);
                            return (
                              <div
                                key={child.id}
                                onClick={() => {
                                  setSelectedCategoryId(child.id);
                                  setSelectedCategoryName(child.name);
                                  setKeyword('');
                                  setDebouncedKeyword('');
                                  if (onNavigate) onNavigate(`products?categoryId=${child.id}&name=${encodeURIComponent(child.name)}`, { scroll: false });
                                }}
                                style={{
                                  padding: '4px 6px',
                                  borderRadius: '4px',
                                  cursor: 'pointer',
                                  fontSize: '12px',
                                  backgroundColor: isChildActive ? 'var(--c-gold-light)' : 'transparent',
                                  color: isChildActive ? 'var(--c-gold-hover)' : 'var(--c-text-muted)',
                                  fontWeight: isChildActive ? 700 : 500,
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'space-between'
                                }}
                              >
                                <span>{cleanDisplayName(child.name)}</span>
                                {isChildActive && <Check size={11} />}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Group 3: Dynamic Brands */}
          {brands.length > 0 && (
            <div style={{ marginBottom: '22px' }}>
              <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '10px', color: 'var(--c-primary)' }}>
                THƯƠNG HIỆU CHÍNH HÃNG
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '5px', fontSize: '13px', maxHeight: '160px', overflowY: 'auto' }}>
                <div
                  onClick={() => {
                    setSelectedBrandId('');
                    setSelectedBrandName('');
                    setKeyword('');
                    setDebouncedKeyword('');
                    if (onNavigate) onNavigate('products', { scroll: false });
                  }}
                  style={{
                    padding: '5px 8px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    backgroundColor: !selectedBrandId ? 'var(--c-gold-light)' : 'transparent',
                    color: !selectedBrandId ? 'var(--c-gold-hover)' : 'var(--c-primary)',
                    fontWeight: !selectedBrandId ? 700 : 500
                  }}
                >
                  Tất cả thương hiệu
                </div>
                {brands.map((b) => {
                  const isBrandActive = String(selectedBrandId) === String(b.id);
                  return (
                    <div
                      key={b.id}
                      onClick={() => {
                        setSelectedBrandId(b.id);
                        setSelectedBrandName(b.name);
                        setKeyword('');
                        setDebouncedKeyword('');
                        if (onNavigate) onNavigate(`products?brandId=${b.id}&brandName=${encodeURIComponent(b.name)}`, { scroll: false });
                      }}
                      style={{
                        padding: '5px 8px',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        backgroundColor: isBrandActive ? 'var(--c-gold-light)' : 'transparent',
                        color: isBrandActive ? 'var(--c-gold-hover)' : 'var(--c-primary)',
                        fontWeight: isBrandActive ? 700 : 500
                      }}
                    >
                      <span>{cleanDisplayName(b.name)}</span>
                      {isBrandActive && <Check size={12} />}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Group 4: Skin Type */}
          <div style={{ marginBottom: '22px' }}>
            <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '10px', color: 'var(--c-primary)' }}>
              LOẠI & TÌNH TRẠNG DA
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px' }}>
              {[
                { id: 'ALL', label: 'Tất cả loại da' },
                { id: 'OILY', label: 'Da Dầu & Lỗ Chân Lông To' },
                { id: 'DRY', label: 'Da Khô & Thiếu Ẩm' },
                { id: 'SENSITIVE', label: 'Da Nhạy Cảm / Mẩn Đỏ' },
                { id: 'COMBINATION', label: 'Da Hỗn Hợp Thiên Dầu' },
              ].map((item) => (
                <label key={item.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                  <input 
                    type="radio" 
                    name="skinType"
                    checked={selectedSkinType === item.id}
                    onChange={() => setSelectedSkinType(item.id)}
                    style={{ accentColor: 'var(--c-gold)' }}
                  />
                  <span style={{ color: selectedSkinType === item.id ? 'var(--c-primary)' : 'var(--c-text-muted)' }}>
                    {item.label}
                  </span>
                </label>
              ))}
            </div>
          </div>

          {/* Group 5: Active Ingredients */}
          <div style={{ marginBottom: '22px' }}>
            <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '10px', color: 'var(--c-primary)' }}>
              HOẠT CHẤT ĐẶC TRỊ (ACTIVES)
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px' }}>
              {ACTIVE_INGREDIENT_FILTERS.map((item) => (
                <label key={item.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                  <input 
                    type="radio" 
                    name="activeIngredient"
                    checked={selectedActive === item.id}
                    onChange={() => setSelectedActive(item.id)}
                    style={{ accentColor: 'var(--c-gold)' }}
                  />
                  <span style={{ color: selectedActive === item.id ? 'var(--c-primary)' : 'var(--c-text-muted)' }}>
                    {item.label}
                  </span>
                </label>
              ))}
            </div>
          </div>

          {/* Group 6: Price Range */}
          <div>
            <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '10px', color: 'var(--c-primary)' }}>
              KHOẢNG GIÁ
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px' }}>
              {[
                { id: 'ALL', label: 'Mọi mức giá' },
                { id: 'UNDER_300', label: 'Dưới 300.000 ₫' },
                { id: '300_600', label: 'Từ 300.000 ₫ - 600.000 ₫' },
                { id: 'OVER_600', label: 'Trên 600.000 ₫' },
              ].map((item) => (
                <label key={item.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                  <input 
                    type="radio" 
                    name="priceRange"
                    checked={selectedPriceRange === item.id}
                    onChange={() => setSelectedPriceRange(item.id)}
                    style={{ accentColor: 'var(--c-gold)' }}
                  />
                  <span style={{ color: selectedPriceRange === item.id ? 'var(--c-primary)' : 'var(--c-text-muted)' }}>
                    {item.label}
                  </span>
                </label>
              ))}
            </div>
          </div>

          <button
            type="button"
            className="catalog-filter-apply btn-luxury-primary"
            onClick={() => setMobileFilterOpen(false)}
            style={{ marginTop: '16px', width: '100%' }}
          >
            Xem {filteredProducts.length} sản phẩm
          </button>
        </aside>

        {/* ================= RIGHT PRODUCT RESULTS ================= */}
        <div>
          {/* Top Control Bar */}
          <div className="catalog-result-toolbar" style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '12px',
            backgroundColor: '#FFFFFF',
            padding: '14px 20px',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--c-border)',
            marginBottom: '16px'
          }}>
            <div style={{ fontSize: '14px', color: 'var(--c-text-muted)' }}>
              Tìm thấy <strong style={{ color: 'var(--c-primary)' }}>{filteredProducts.length}</strong> sản phẩm phù hợp
            </div>

            {/* Sort Selector */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
              <span style={{ color: 'var(--c-text-light)' }}>Sắp xếp theo:</span>
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                style={{
                  padding: '6px 12px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--c-border)',
                  backgroundColor: 'var(--c-canvas)',
                  fontSize: '13px',
                  color: 'var(--c-primary)',
                  fontWeight: 600,
                  outline: 'none',
                  cursor: 'pointer'
                }}
              >
                <option value="popular">Bán chạy & Phổ biến nhất</option>
                <option value="price_asc">Giá: Thấp đến Cao</option>
                <option value="price_desc">Giá: Cao đến Thấp</option>
                <option value="rating">Đánh giá cao nhất</option>
              </select>
            </div>
          </div>

          {/* Active Filter Chips Bar */}
          {hasActiveFilters && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '20px', alignItems: 'center' }}>
              <span style={{ fontSize: '12px', color: 'var(--c-text-muted)', fontWeight: 600 }}>Bộ lọc đang chọn:</span>
              
              {selectedCategoryName && (
                <span
                  className="badge-dermatology gold"
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                  onClick={() => {
                    setSelectedCategoryId('');
                    setSelectedCategoryName('');
                    if (onNavigate) onNavigate('products', { scroll: false });
                  }}
                >
                  <Tag size={12} />
                  Danh mục: {selectedCategoryName} <X size={12} />
                </span>
              )}

              {selectedBrandName && (
                <span
                  className="badge-dermatology gold"
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                  onClick={() => {
                    setSelectedBrandId('');
                    setSelectedBrandName('');
                    if (onNavigate) onNavigate('products', { scroll: false });
                  }}
                >
                  Thương hiệu: {selectedBrandName} <X size={12} />
                </span>
              )}

              {activeFilter === 'sale' && (
                <span
                  className="badge-dermatology red"
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px', backgroundColor: '#FEE2E2', color: '#DC2626' }}
                  onClick={() => setActiveFilter('')}
                >
                  <Zap size={12} /> Giờ Vàng Flash Sale <X size={12} />
                </span>
              )}

              {activeFilter === 'best-seller' && (
                <span
                  className="badge-dermatology red"
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px', backgroundColor: '#FEF2F2', color: '#B91C1C' }}
                  onClick={() => setActiveFilter('')}
                >
                  <Flame size={12} /> Bán Chạy Nhất <X size={12} />
                </span>
              )}

              {keyword && (
                <span
                  className="badge-dermatology gold"
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                  onClick={() => {
                    setKeyword('');
                    setDebouncedKeyword('');
                    if (onNavigate) onNavigate('products', { scroll: false });
                  }}
                >
                  <Search size={12} /> Từ khóa: "{keyword}" <X size={12} />
                </span>
              )}

              {selectedSkinType !== 'ALL' && (
                <span 
                  className="badge-dermatology safe" 
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px' }} 
                  onClick={() => setSelectedSkinType('ALL')}
                >
                  Loại da: {selectedSkinType} <X size={12} />
                </span>
              )}

              {selectedActive !== 'ALL' && (
                <span 
                  className="badge-dermatology safe" 
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px' }} 
                  onClick={() => setSelectedActive('ALL')}
                >
                  Hoạt chất: {ACTIVE_INGREDIENT_FILTERS.find((item) => item.id === selectedActive)?.label || selectedActive} <X size={12} />
                </span>
              )}

              {selectedPriceRange !== 'ALL' && (
                <span 
                  className="badge-dermatology amber" 
                  style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '5px' }} 
                  onClick={() => setSelectedPriceRange('ALL')}
                >
                  Giá: {selectedPriceRange} <X size={12} />
                </span>
              )}

              <button 
                onClick={clearAllFilters}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--c-danger-red)',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  marginLeft: '4px',
                  textDecoration: 'underline'
                }}
              >
                Xóa tất cả
              </button>
            </div>
          )}

          {/* Products Grid */}
          {loading ? (
            <ProductGridSkeleton count={8} />
          ) : filteredProducts.length === 0 ? (
            <div className="catalog-products-grid catalog-grid-fade-in" style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--c-border)',
              padding: '60px 20px',
              textAlign: 'center',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '12px'
            }}>
              <Sparkles size={36} color="var(--c-gold)" />
              <h3 style={{ margin: 0, fontSize: '18px', fontFamily: 'var(--font-serif)' }}>
                Không tìm thấy sản phẩm phù hợp
              </h3>
              <p style={{ margin: 0, fontSize: '13px', color: 'var(--c-text-muted)', maxWidth: '400px' }}>
                Thử thay đổi bộ lọc tình trạng da hoặc xóa bớt tiêu chí lọc để khám phá nhiều sản phẩm hơn.
              </p>
              <button onClick={clearAllFilters} className="btn-luxury-primary" style={{ marginTop: '8px' }}>
                Xóa tất cả bộ lọc
              </button>
            </div>
          ) : (
            <div className="catalog-products-grid catalog-grid-fade-in" style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
              gap: '20px'
            }}>
              {filteredProducts.map((prod) => (
                <ProductCard
                  key={prod.id}
                  product={prod}
                  onNavigate={onNavigate}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
