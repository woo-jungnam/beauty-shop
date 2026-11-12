import React, { useState, useEffect, useMemo, useRef } from 'react';
import { AlertCircle, ArrowLeft, Filter, SlidersHorizontal, RefreshCw, X, Sparkles } from 'lucide-react';
import { ProductCard } from '../components/product/ProductCard';
import { ProductGridSkeleton } from '../components/product/ProductCardSkeleton';
import { apiClient } from '../../shared/api/client';
import { cleanDisplayName } from '../../shared/utils/formatters';
import { useCustomerDialog } from '../components/ui/useCustomerDialog';
import {
  ACTIVE_INGREDIENT_FILTERS, SKIN_TYPES, PRICE_RANGES, RATING_OPTIONS, GENDER_OPTIONS,
  readCatalogFilters, catalogRoute, catalogSearchParams, flattenCategories, toggleFilterValue,
} from '../utils/catalogFilters';

const FilterGroup = ({ title, options, selected, onToggle, onClear, className = '', searchable = false }) => {
  const [localQuery, setLocalQuery] = useState('');
  const normalizedQuery = localQuery.trim().toLowerCase();
  const visibleOptions = normalizedQuery
    ? options.filter(option => {
        const text = (option.label || cleanDisplayName(option.name) || '').toLowerCase();
        return selected.includes(String(option.id)) || text.includes(normalizedQuery);
      })
    : options;

  return (
    <fieldset className={`catalog-filter-group ${className}`}>
      <legend>{title}</legend>
      <button
        type="button"
        className="catalog-filter-all"
        onClick={onClear}
        aria-pressed={!selected.length}
        style={{ cursor: 'pointer' }}
      >
        {selected.length > 0 ? 'Tất cả (Bỏ lọc)' : 'Tất cả'}
      </button>
      {searchable && options.length > 8 && (
        <input
          type="search"
          className="catalog-filter-quick-search"
          placeholder="Tìm nhanh..."
          aria-label={`Tìm nhanh trong ${title}`}
          value={localQuery}
          onChange={event => setLocalQuery(event.target.value)}
        />
      )}
      <div className="catalog-filter-options">
        {visibleOptions.map(option => (
          <label key={option.id} className={`catalog-filter-option ${selected.includes(String(option.id)) ? 'is-selected' : ''}`} style={{ '--category-depth': option.depth || 0 }}>
            <input type="checkbox" checked={selected.includes(String(option.id))} onChange={() => onToggle(String(option.id))} />
            <span>{option.label || cleanDisplayName(option.name)}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
};

const RadioGroup = ({ title, name, options, selected, onChange, children }) => (
  <fieldset className="catalog-filter-group">
    <legend>{title}</legend>
    <div className="catalog-filter-options">
      {options.map(option => (
        <label key={option.id} className={`catalog-filter-option ${selected === option.id ? 'is-selected' : ''}`}>
          <input type="radio" name={name} checked={selected === option.id} onChange={() => onChange(option.id)} />
          <span>{option.label}</span>
        </label>
      ))}
    </div>
    {children}
  </fieldset>
);

const FilterChip = ({ label, onRemove }) => (
  <button type="button" className="catalog-filter-chip" onClick={onRemove} aria-label={`Bỏ ${label}`}>
    <span>{label}</span><X size={13} aria-hidden="true" />
  </button>
);

const formatPriceLabel = (min, max) => {
  const fmt = v => `${Number(v).toLocaleString('vi-VN')} ₫`;
  if (min && max) return `${fmt(min)} - ${fmt(max)}`;
  if (min) return `Từ ${fmt(min)}`;
  return `Đến ${fmt(max)}`;
};

export const CatalogPage = ({ onNavigate, initialQuery = '' }) => {
  const routeFilters = useMemo(() => readCatalogFilters(initialQuery), [initialQuery]);
  const [keyword, setKeyword] = useState(routeFilters.keyword);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [skinConcerns, setSkinConcerns] = useState([]);
  const [origins, setOrigins] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const [customMinPrice, setCustomMinPrice] = useState(routeFilters.minPrice);
  const [customMaxPrice, setCustomMaxPrice] = useState(routeFilters.maxPrice);
  const [priceError, setPriceError] = useState('');
  const filterPanelRef = useRef(null);
  useCustomerDialog(mobileFilterOpen, filterPanelRef, () => setMobileFilterOpen(false));

  const categoryOptions = useMemo(() => flattenCategories(categories), [categories]);
  const filters = useMemo(() => {
    const category = categoryOptions.find(item => item.slug === routeFilters.categorySlug);
    const brand = brands.find(item => item.slug === routeFilters.brandSlug);
    return {
      ...routeFilters,
      categoryIds: routeFilters.categoryIds.length ? routeFilters.categoryIds : (routeFilters.categorySlug && category ? [String(category.id)] : []),
      brandIds: routeFilters.brandIds.length ? routeFilters.brandIds : (routeFilters.brandSlug && brand ? [String(brand.id)] : []),
    };
  }, [routeFilters, categoryOptions, brands]);
  const searchParams = useMemo(() => catalogSearchParams(filters), [filters]);

  const changeFilters = (changes) => onNavigate(catalogRoute({
    ...filters, keyword, ...changes, page: changes.page ?? 0,
  }), { scroll: false });
  const clearAllFilters = () => {
    setKeyword('');
    setCustomMinPrice('');
    setCustomMaxPrice('');
    setPriceError('');
    onNavigate('products', { scroll: false });
  };

  useEffect(() => { setKeyword(routeFilters.keyword); }, [routeFilters.keyword]);
  useEffect(() => {
    setCustomMinPrice(routeFilters.minPrice);
    setCustomMaxPrice(routeFilters.maxPrice);
    setPriceError('');
  }, [routeFilters.minPrice, routeFilters.maxPrice]);

  useEffect(() => {
    if (keyword === routeFilters.keyword) return;
    const timer = setTimeout(() => onNavigate(catalogRoute({ ...filters, keyword, page: 0 }), { scroll: false }), 280);
    return () => clearTimeout(timer);
  }, [keyword, routeFilters.keyword, filters, onNavigate]);

  useEffect(() => {
    const media = window.matchMedia('(min-width: 768px)');
    const closeOnDesktop = () => { if (media.matches) setMobileFilterOpen(false); };
    media.addEventListener('change', closeOnDesktop);
    return () => media.removeEventListener('change', closeOnDesktop);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const getAllBrands = async () => {
      const response = await apiClient.get('/api/v1/brands?size=100');
      const data = response?.data ?? response;
      const list = [...(data?.content || (Array.isArray(data) ? data : []))];
      for (let page = 1; page < (data?.totalPages || 1); page++) {
        if (cancelled) return [];
        const next = await apiClient.get(`/api/v1/brands?size=100&page=${page}`);
        list.push(...(next?.data?.content || next?.content || []));
      }
      return list;
    };
    Promise.allSettled([
      apiClient.get('/api/v1/categories/root'),
      getAllBrands(),
      apiClient.get('/api/v1/products/filter-options'),
    ]).then(([catResult, brandResult, optionsResult]) => {
      if (cancelled) return;
      if (catResult.status === 'fulfilled') {
        const data = catResult.value?.data ?? catResult.value;
        setCategories(Array.isArray(data) ? data : []);
      }
      if (brandResult.status === 'fulfilled') setBrands(brandResult.value);
      if (optionsResult.status === 'fulfilled') {
        const data = optionsResult.value?.data ?? optionsResult.value;
        setSkinConcerns(Array.isArray(data?.skinConcerns) ? data.skinConcerns : []);
        setOrigins(Array.isArray(data?.origins) ? data.origins : []);
      }
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError(false);
    apiClient.get(`/api/v1/products/search?${searchParams}`, { signal: controller.signal }).then(response => {
      if (controller.signal.aborted) return;
      const data = response?.data ?? response;
      const list = Array.isArray(data?.content) ? data.content : [];
      setProducts(list);
      setTotalElements(Number(data?.totalElements ?? list.length));
      setTotalPages(Number(data?.totalPages ?? 1));
    }).catch(() => {
      if (controller.signal.aborted) return;
      setProducts([]);
      setLoadError(true);
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [searchParams, requestVersion]);

  const applyCustomPrice = () => {
    const minVal = customMinPrice.replace(/[^\d]/g, '');
    const maxVal = customMaxPrice.replace(/[^\d]/g, '');
    if (!minVal && !maxVal) {
      setPriceError('');
      changeFilters({ priceRange: 'ALL', minPrice: '', maxPrice: '' });
      return;
    }
    if (minVal && maxVal && Number(minVal) > Number(maxVal)) {
      setPriceError('Giá tối đa phải lớn hơn hoặc bằng giá tối thiểu.');
      return;
    }
    setPriceError('');
    changeFilters({
      priceRange: 'CUSTOM',
      minPrice: minVal ? String(Number(minVal)) : '',
      maxPrice: maxVal ? String(Number(maxVal)) : '',
    });
  };

  const categoryName = id => cleanDisplayName(categoryOptions.find(item => String(item.id) === id)?.name
    || (filters.categoryIds.length === 1 ? filters.categoryName : '') || `Danh mục #${id}`);
  const brandName = id => cleanDisplayName(brands.find(item => String(item.id) === id)?.name
    || (filters.brandIds.length === 1 ? filters.brandName : '') || `Thương hiệu #${id}`);
  const concernName = id => cleanDisplayName(skinConcerns.find(item => String(item.id) === id)?.name || `Vấn đề #${id}`);

  const saleViaToggle = filters.onSale && filters.activeFilter !== 'sale';
  const hasActiveFilters = Boolean(
    keyword || filters.categoryIds.length || filters.brandIds.length || filters.activeIngredients.length
    || filters.skinConcernIds.length || filters.skinType !== 'ALL' || filters.priceRange !== 'ALL'
    || filters.minRating !== 'ALL' || saleViaToggle || filters.fragranceFree || filters.alcoholFree
    || filters.originCountry || filters.targetGender !== 'ALL' || filters.activeFilter
  );
  const activeCount = filters.categoryIds.length + filters.brandIds.length + filters.activeIngredients.length + filters.skinConcernIds.length
    + Number(Boolean(keyword)) + Number(filters.skinType !== 'ALL') + Number(filters.priceRange !== 'ALL')
    + Number(filters.minRating !== 'ALL') + Number(saleViaToggle) + Number(filters.fragranceFree)
    + Number(filters.alcoholFree) + Number(Boolean(filters.originCountry)) + Number(filters.targetGender !== 'ALL')
    + Number(Boolean(filters.activeFilter));

  const getBannerTitle = () => {
    if (filters.categoryIds.length === 1) return categoryName(filters.categoryIds[0]);
    if (filters.brandIds.length === 1) return `Thương Hiệu ${brandName(filters.brandIds[0])}`;
    if (filters.categoryIds.length || filters.brandIds.length) return 'Sản phẩm theo lựa chọn của bạn';
    if (filters.activeFilter === 'sale' || filters.onSale) return 'Giờ Vàng Deal — Flash Sale';
    if (filters.activeFilter === 'best-seller') return 'Top Sản Phẩm Bán Chạy Nhất';
    if (keyword) return `Kết Quả Cho: "${cleanDisplayName(keyword)}"`;
    return 'Mỹ Phẩm Khoa Học & Phục Hồi Làn Da';
  };

  return (
    <div className="customer-container catalog-page" style={{ padding: '32px 20px 60px' }}>
      <nav className="catalog-breadcrumb" aria-label="Đường dẫn danh mục" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--c-text-light, #716762)', marginBottom: 16 }}>
        <button type="button" onClick={() => onNavigate?.('')} style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <ArrowLeft size={14} /> Trang chủ
        </button>
        <span aria-hidden="true">/</span>
        <span style={{ color: 'var(--c-primary, #721B29)', fontWeight: 600 }}>Sản phẩm</span>
      </nav>
      <div className="catalog-page-heading">
        <span>DANH MỤC MỸ PHẨM CHÍNH HÃNG</span>
        <h1>{getBannerTitle()}</h1>
        <p>Chọn nhiều danh mục, thương hiệu, vấn đề da và hoạt chất để tìm sản phẩm phù hợp.</p>
        <a href="#/image-search">Tìm sản phẩm bằng ảnh →</a>
      </div>

      <button type="button" className="catalog-mobile-filter-trigger" onClick={() => setMobileFilterOpen(true)} aria-expanded={mobileFilterOpen}>
        <SlidersHorizontal size={17} /><span>Bộ lọc sản phẩm</span>
        {hasActiveFilters && <span className="catalog-filter-count">{activeCount} lựa chọn</span>}
      </button>
      {mobileFilterOpen && <button type="button" className="catalog-filter-backdrop" aria-label="Đóng bộ lọc" onClick={() => setMobileFilterOpen(false)} />}

      <div className="catalog-grid-layout" style={{ display: 'grid', gridTemplateColumns: '260px minmax(0, 1fr)', gap: '32px', alignItems: 'start' }}>
        <aside ref={filterPanelRef} role={mobileFilterOpen ? 'dialog' : undefined} aria-modal={mobileFilterOpen ? 'true' : undefined}
          aria-label="Bộ lọc sản phẩm" tabIndex={-1} className={`catalog-filter-panel catalog-facet-panel ${mobileFilterOpen ? 'is-open' : ''}`}>
          <div className="catalog-filter-heading">
            <strong><Filter size={16} /> Bộ Lọc Chuyên Sâu</strong>
            {hasActiveFilters && <button type="button" className="catalog-filter-reset" onClick={clearAllFilters}>Đặt lại</button>}
            <button type="button" className="catalog-filter-close" onClick={() => setMobileFilterOpen(false)} aria-label="Đóng bộ lọc"><X size={20} /></button>
          </div>
          <div className="catalog-filter-search">
            <label htmlFor="catalog-keyword">Từ khóa tìm kiếm</label>
            <div>
              <input id="catalog-keyword" type="search" placeholder="Nhập tên sản phẩm..." value={keyword} onChange={event => setKeyword(event.target.value)} />
              {keyword && <button type="button" aria-label="Xóa từ khóa" onClick={() => { setKeyword(''); changeFilters({ keyword: '' }); }}><X size={14} /></button>}
            </div>
          </div>
          <p className="catalog-filter-help">Có thể chọn nhiều mục trong cùng nhóm.</p>

          <fieldset className="catalog-filter-group">
            <legend>Ưu đãi</legend>
            <div className="catalog-filter-options">
              <label className={`catalog-filter-option ${filters.onSale ? 'is-selected' : ''}`}>
                <input
                  type="checkbox"
                  checked={filters.onSale}
                  onChange={() => {
                    const nextOnSale = !filters.onSale;
                    changeFilters({
                      onSale: nextOnSale,
                      activeFilter: !nextOnSale && filters.activeFilter === 'sale' ? '' : filters.activeFilter,
                    });
                  }}
                />
                <span>Đang giảm giá / Flash Sale</span>
              </label>
            </div>
          </fieldset>

          {categoryOptions.length > 0 && <FilterGroup title="Danh mục sản phẩm" options={categoryOptions} selected={filters.categoryIds}
            onToggle={id => changeFilters({ categoryIds: toggleFilterValue(filters.categoryIds, id) })} onClear={() => changeFilters({ categoryIds: [], categorySlug: '', categoryName: '' })} />}
          {brands.length > 0 && <FilterGroup title="Thương hiệu chính hãng" options={brands} selected={filters.brandIds} searchable
            onToggle={id => changeFilters({ brandIds: toggleFilterValue(filters.brandIds, id) })} onClear={() => changeFilters({ brandIds: [], brandSlug: '', brandName: '' })} />}
          {skinConcerns.length > 0 && <FilterGroup title="Vấn đề da" options={skinConcerns} selected={filters.skinConcernIds}
            onToggle={id => changeFilters({ skinConcernIds: toggleFilterValue(filters.skinConcernIds, id) })} onClear={() => changeFilters({ skinConcernIds: [] })} />}
          <RadioGroup title="Loại & tình trạng da" name="skinType" options={SKIN_TYPES} selected={filters.skinType} onChange={skinType => changeFilters({ skinType })} />
          <FilterGroup title="Hoạt chất chuyên sâu" options={ACTIVE_INGREDIENT_FILTERS} selected={filters.activeIngredients}
            onToggle={id => changeFilters({ activeIngredients: toggleFilterValue(filters.activeIngredients, id) })} onClear={() => changeFilters({ activeIngredients: [] })} />

          <fieldset className="catalog-filter-group">
            <legend>An toàn thành phần</legend>
            <div className="catalog-filter-options">
              <label className={`catalog-filter-option ${filters.fragranceFree ? 'is-selected' : ''}`}>
                <input type="checkbox" checked={filters.fragranceFree} onChange={() => changeFilters({ fragranceFree: !filters.fragranceFree })} />
                <span>Không hương liệu</span>
              </label>
              <label className={`catalog-filter-option ${filters.alcoholFree ? 'is-selected' : ''}`}>
                <input type="checkbox" checked={filters.alcoholFree} onChange={() => changeFilters({ alcoholFree: !filters.alcoholFree })} />
                <span>Không cồn</span>
              </label>
            </div>
          </fieldset>

          <RadioGroup
            title="Khoảng giá"
            name="priceRange"
            options={PRICE_RANGES}
            selected={filters.priceRange}
            onChange={priceRange => {
              setPriceError('');
              changeFilters({ priceRange, minPrice: '', maxPrice: '' });
            }}
          >
            <div className="catalog-price-custom">
              <div className="catalog-price-inputs">
                <input
                  type="text"
                  inputMode="numeric"
                  aria-label="Giá tối thiểu"
                  placeholder="Từ (₫)"
                  value={customMinPrice}
                  onChange={event => setCustomMinPrice(event.target.value.replace(/[^\d]/g, ''))}
                  onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); applyCustomPrice(); } }}
                />
                <span aria-hidden="true">–</span>
                <input
                  type="text"
                  inputMode="numeric"
                  aria-label="Giá tối đa"
                  placeholder="Đến (₫)"
                  value={customMaxPrice}
                  onChange={event => setCustomMaxPrice(event.target.value.replace(/[^\d]/g, ''))}
                  onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); applyCustomPrice(); } }}
                />
              </div>
              {priceError && <p className="catalog-price-error" role="alert">{priceError}</p>}
              <button type="button" className="catalog-price-apply" onClick={applyCustomPrice}>Áp dụng giá</button>
            </div>
          </RadioGroup>

          <RadioGroup title="Đánh giá" name="minRating" options={RATING_OPTIONS} selected={filters.minRating} onChange={minRating => changeFilters({ minRating })} />

          <fieldset className="catalog-filter-group">
            <legend>Đối tượng sử dụng</legend>
            <select
              aria-label="Đối tượng sử dụng"
              className="catalog-filter-select"
              value={filters.targetGender}
              onChange={event => changeFilters({ targetGender: event.target.value })}
            >
              {GENDER_OPTIONS.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
            </select>
          </fieldset>

          {origins.length > 0 && (
            <fieldset className="catalog-filter-group">
              <legend>Xuất xứ</legend>
              <select
                aria-label="Xuất xứ"
                className="catalog-filter-select"
                value={filters.originCountry}
                onChange={event => changeFilters({ originCountry: event.target.value })}
              >
                <option value="">Tất cả xuất xứ</option>
                {origins.map(origin => <option key={origin} value={origin}>{origin}</option>)}
              </select>
            </fieldset>
          )}

          <button type="button" className="catalog-filter-apply btn-luxury-primary" onClick={() => setMobileFilterOpen(false)}>
            {loading ? 'Đang tìm sản phẩm…' : loadError ? 'Đóng bộ lọc' : `Xem ${totalElements} sản phẩm`}
          </button>
        </aside>

        <div className="catalog-results">
          <div className="catalog-result-toolbar catalog-facet-toolbar">
            <div role="status" aria-live="polite">
              {loading ? 'Đang tìm sản phẩm…' : loadError ? 'Chưa tải được danh sách sản phẩm' : <>Tìm thấy <strong>{totalElements}</strong> sản phẩm phù hợp</>}
            </div>
            <div className="catalog-sort-control">
              <label htmlFor="catalog-sort">Sắp xếp theo:</label>
              <select id="catalog-sort" aria-label="Sắp xếp sản phẩm" value={filters.sortBy} onChange={event => changeFilters({ sortBy: event.target.value })}>
                <option value="popular">Bán chạy & Phổ biến nhất</option>
                <option value="newest">Mới nhất</option>
                <option value="price_asc">Giá: Thấp đến Cao</option>
                <option value="price_desc">Giá: Cao đến Thấp</option>
                <option value="rating">Đánh giá cao nhất</option>
              </select>
            </div>
          </div>

          {hasActiveFilters && <div className="catalog-filter-chips" aria-label="Bộ lọc đang chọn">
            <span>Bộ lọc đang chọn:</span>
            {filters.categoryIds.map(id => <FilterChip key={`category-${id}`} label={`Danh mục: ${categoryName(id)}`} onRemove={() => changeFilters({ categoryIds: filters.categoryIds.filter(value => value !== id), categorySlug: '', categoryName: '' })} />)}
            {filters.brandIds.map(id => <FilterChip key={`brand-${id}`} label={`Thương hiệu: ${brandName(id)}`} onRemove={() => changeFilters({ brandIds: filters.brandIds.filter(value => value !== id), brandSlug: '', brandName: '' })} />)}
            {filters.skinConcernIds.map(id => <FilterChip key={`concern-${id}`} label={`Vấn đề da: ${concernName(id)}`} onRemove={() => changeFilters({ skinConcernIds: filters.skinConcernIds.filter(value => value !== id) })} />)}
            {filters.activeIngredients.map(id => <FilterChip key={`active-${id}`} label={`Hoạt chất: ${ACTIVE_INGREDIENT_FILTERS.find(item => item.id === id)?.label || id}`} onRemove={() => changeFilters({ activeIngredients: filters.activeIngredients.filter(value => value !== id) })} />)}
            {keyword && <FilterChip label={`Từ khóa: "${keyword}"`} onRemove={() => { setKeyword(''); changeFilters({ keyword: '' }); }} />}
            {filters.skinType !== 'ALL' && <FilterChip label={`Loại da: ${SKIN_TYPES.find(item => item.id === filters.skinType)?.label || filters.skinType}`} onRemove={() => changeFilters({ skinType: 'ALL' })} />}
            {filters.priceRange !== 'ALL' && <FilterChip label={`Giá: ${filters.priceRange === 'CUSTOM' ? formatPriceLabel(filters.minPrice, filters.maxPrice) : (PRICE_RANGES.find(item => item.id === filters.priceRange)?.label || filters.priceRange)}`} onRemove={() => changeFilters({ priceRange: 'ALL', minPrice: '', maxPrice: '' })} />}
            {filters.minRating !== 'ALL' && <FilterChip label={`Đánh giá: ${RATING_OPTIONS.find(item => item.id === filters.minRating)?.label || `${filters.minRating}★+`}`} onRemove={() => changeFilters({ minRating: 'ALL' })} />}
            {saleViaToggle && <FilterChip label="Đang giảm giá / Flash Sale" onRemove={() => changeFilters({ onSale: false })} />}
            {filters.fragranceFree && <FilterChip label="Không hương liệu" onRemove={() => changeFilters({ fragranceFree: false })} />}
            {filters.alcoholFree && <FilterChip label="Không cồn" onRemove={() => changeFilters({ alcoholFree: false })} />}
            {filters.targetGender !== 'ALL' && <FilterChip label={`Đối tượng: ${GENDER_OPTIONS.find(item => item.id === filters.targetGender)?.label || filters.targetGender}`} onRemove={() => changeFilters({ targetGender: 'ALL' })} />}
            {filters.originCountry && <FilterChip label={`Xuất xứ: ${filters.originCountry}`} onRemove={() => changeFilters({ originCountry: '' })} />}
            {filters.activeFilter && <FilterChip label={filters.activeFilter === 'sale' ? 'Giờ Vàng Flash Sale' : 'Bán Chạy Nhất'} onRemove={() => changeFilters({ activeFilter: '', onSale: filters.activeFilter === 'sale' ? false : filters.onSale })} />}
            <button type="button" className="catalog-filter-reset" onClick={clearAllFilters}>Xóa tất cả</button>
          </div>}

          {loading ? <ProductGridSkeleton count={8} /> : loadError ? (
            <div className="catalog-results-status" role="alert">
              <AlertCircle size={32} /><h3>Chưa tải được sản phẩm</h3>
              <p>Vui lòng kiểm tra kết nối và thử lại. Các bộ lọc của bạn vẫn được giữ.</p>
              <button type="button" className="btn-luxury-primary" onClick={() => setRequestVersion(version => version + 1)}><RefreshCw size={16} /> Thử lại</button>
            </div>
          ) : products.length === 0 ? (
            <div className="catalog-results-status">
              <Sparkles size={36} /><h3>Không tìm thấy sản phẩm phù hợp</h3>
              <p>Thử thay đổi hoặc bỏ bớt lựa chọn để khám phá nhiều sản phẩm hơn.</p>
              <button type="button" onClick={clearAllFilters} className="btn-luxury-primary">Xóa tất cả bộ lọc</button>
            </div>
          ) : (
            <div className="catalog-products-grid catalog-grid-fade-in" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '20px' }}>
              {products.map(product => <ProductCard key={product.id} product={product} onNavigate={onNavigate} fromSearch={Boolean(filters.keyword?.trim())} />)}
            </div>
          )}
          {!loading && !loadError && totalPages > 1 && <nav className="catalog-pagination" aria-label="Phân trang sản phẩm">
            <button type="button" className="btn-luxury-outline" disabled={filters.page === 0} onClick={() => changeFilters({ page: filters.page - 1 })}>Trang trước</button>
            <span>Trang {filters.page + 1} / {totalPages}</span>
            <button type="button" className="btn-luxury-outline" disabled={filters.page + 1 >= totalPages} onClick={() => changeFilters({ page: filters.page + 1 })}>Trang sau</button>
          </nav>}
        </div>
      </div>
    </div>
  );
};
