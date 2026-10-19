import React, { useState, useEffect, useMemo, useRef } from 'react';
import { AlertCircle, Filter, SlidersHorizontal, RefreshCw, X, Sparkles } from 'lucide-react';
import { ProductCard } from '../components/product/ProductCard';
import { ProductGridSkeleton } from '../components/product/ProductCardSkeleton';
import { apiClient } from '../../shared/api/client';
import { cleanDisplayName } from '../../shared/utils/formatters';
import { useCustomerDialog } from '../components/ui/useCustomerDialog';
import {
  ACTIVE_INGREDIENT_FILTERS, SKIN_TYPES, PRICE_RANGES, readCatalogFilters,
  catalogRoute, catalogSearchParams, flattenCategories, toggleFilterValue,
} from '../utils/catalogFilters';

const FilterGroup = ({ title, options, selected, onToggle, onClear, className = '' }) => (
  <fieldset className={`catalog-filter-group ${className}`}>
    <legend>{title}</legend>
    <button type="button" className="catalog-filter-all" onClick={onClear} aria-pressed={!selected.length}>Tất cả</button>
    <div className="catalog-filter-options">
      {options.map(option => (
        <label key={option.id} className={`catalog-filter-option ${selected.includes(String(option.id)) ? 'is-selected' : ''}`} style={{ '--category-depth': option.depth || 0 }}>
          <input type="checkbox" checked={selected.includes(String(option.id))} onChange={() => onToggle(String(option.id))} />
          <span>{option.label || cleanDisplayName(option.name)}</span>
        </label>
      ))}
    </div>
  </fieldset>
);

const RadioGroup = ({ title, name, options, selected, onChange }) => (
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
  </fieldset>
);

const FilterChip = ({ label, onRemove }) => (
  <button type="button" className="catalog-filter-chip" onClick={onRemove} aria-label={`Bỏ ${label}`}>
    <span>{label}</span><X size={13} aria-hidden="true" />
  </button>
);

export const CatalogPage = ({ onNavigate, initialQuery = '' }) => {
  const routeFilters = useMemo(() => readCatalogFilters(initialQuery), [initialQuery]);
  const [keyword, setKeyword] = useState(routeFilters.keyword);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const filterPanelRef = useRef(null);
  useCustomerDialog(mobileFilterOpen, filterPanelRef, () => setMobileFilterOpen(false));

  const categoryOptions = useMemo(() => flattenCategories(categories), [categories]);
  const filters = useMemo(() => {
    const category = categoryOptions.find(item => item.slug === routeFilters.categorySlug);
    const brand = brands.find(item => item.slug === routeFilters.brandSlug);
    return {
      ...routeFilters,
      categoryIds: routeFilters.categoryIds.length ? routeFilters.categoryIds : category ? [String(category.id)] : [],
      brandIds: routeFilters.brandIds.length ? routeFilters.brandIds : brand ? [String(brand.id)] : [],
    };
  }, [routeFilters, categoryOptions, brands]);
  const searchParams = useMemo(() => catalogSearchParams(filters), [filters]);

  const changeFilters = (changes) => onNavigate(catalogRoute({
    ...filters, keyword, ...changes, page: changes.page ?? 0,
  }), { scroll: false });
  const clearAllFilters = () => {
    setKeyword('');
    onNavigate('products', { scroll: false });
  };

  useEffect(() => { setKeyword(routeFilters.keyword); }, [routeFilters.keyword]);
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
    Promise.allSettled([apiClient.get('/api/v1/categories/root'), getAllBrands()]).then(([catResult, brandResult]) => {
      if (cancelled) return;
      if (catResult.status === 'fulfilled') {
        const data = catResult.value?.data ?? catResult.value;
        setCategories(Array.isArray(data) ? data : []);
      }
      if (brandResult.status === 'fulfilled') setBrands(brandResult.value);
    });
    return () => { cancelled = true; };
  }, []);

  // Let the API combine filters before sorting and pagination; cancel obsolete requests.
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

  const categoryName = id => cleanDisplayName(categoryOptions.find(item => String(item.id) === id)?.name
    || (filters.categoryIds.length === 1 ? filters.categoryName : '') || `Danh mục #${id}`);
  const brandName = id => cleanDisplayName(brands.find(item => String(item.id) === id)?.name
    || (filters.brandIds.length === 1 ? filters.brandName : '') || `Thương hiệu #${id}`);
  const hasActiveFilters = Boolean(keyword || filters.categoryIds.length || filters.brandIds.length
    || filters.activeIngredients.length || filters.skinType !== 'ALL' || filters.priceRange !== 'ALL' || filters.activeFilter);
  const activeCount = filters.categoryIds.length + filters.brandIds.length + filters.activeIngredients.length
    + Number(Boolean(keyword)) + Number(filters.skinType !== 'ALL') + Number(filters.priceRange !== 'ALL') + Number(Boolean(filters.activeFilter));
  const getBannerTitle = () => {
    if (filters.categoryIds.length === 1) return categoryName(filters.categoryIds[0]);
    if (filters.brandIds.length === 1) return `Thương Hiệu ${brandName(filters.brandIds[0])}`;
    if (filters.categoryIds.length || filters.brandIds.length) return 'Sản phẩm theo lựa chọn của bạn';
    if (filters.activeFilter === 'sale') return 'Giờ Vàng Deal — Flash Sale';
    if (filters.activeFilter === 'best-seller') return 'Top Sản Phẩm Bán Chạy Nhất';
    if (keyword) return `Kết Quả Cho: "${cleanDisplayName(keyword)}"`;
    return 'Mỹ Phẩm Khoa Học & Phục Hồi Làn Da';
  };

  return (
    <div className="customer-container catalog-page" style={{ padding: '32px 20px 60px' }}>
      <div className="catalog-page-heading">
        <span>DANH MỤC DƯỢC MỸ PHẨM CHÍNH HÃNG</span>
        <h1>{getBannerTitle()}</h1>
        <p>Chọn nhiều danh mục, thương hiệu và hoạt chất để tìm sản phẩm phù hợp.</p>
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
          {categoryOptions.length > 0 && <FilterGroup title="Danh mục sản phẩm" options={categoryOptions} selected={filters.categoryIds}
            onToggle={id => changeFilters({ categoryIds: toggleFilterValue(filters.categoryIds, id) })} onClear={() => changeFilters({ categoryIds: [] })} />}
          {brands.length > 0 && <FilterGroup title="Thương hiệu chính hãng" options={brands} selected={filters.brandIds}
            onToggle={id => changeFilters({ brandIds: toggleFilterValue(filters.brandIds, id) })} onClear={() => changeFilters({ brandIds: [] })} />}
          <RadioGroup title="Loại & tình trạng da" name="skinType" options={SKIN_TYPES} selected={filters.skinType} onChange={skinType => changeFilters({ skinType })} />
          <FilterGroup title="Hoạt chất đặc trị" options={ACTIVE_INGREDIENT_FILTERS} selected={filters.activeIngredients}
            onToggle={id => changeFilters({ activeIngredients: toggleFilterValue(filters.activeIngredients, id) })} onClear={() => changeFilters({ activeIngredients: [] })} />
          <RadioGroup title="Khoảng giá" name="priceRange" options={PRICE_RANGES} selected={filters.priceRange} onChange={priceRange => changeFilters({ priceRange })} />
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
                <option value="price_asc">Giá: Thấp đến Cao</option>
                <option value="price_desc">Giá: Cao đến Thấp</option>
                <option value="rating">Đánh giá cao nhất</option>
              </select>
            </div>
          </div>

          {hasActiveFilters && <div className="catalog-filter-chips" aria-label="Bộ lọc đang chọn">
            <span>Bộ lọc đang chọn:</span>
            {filters.categoryIds.map(id => <FilterChip key={`category-${id}`} label={`Danh mục: ${categoryName(id)}`} onRemove={() => changeFilters({ categoryIds: filters.categoryIds.filter(value => value !== id) })} />)}
            {filters.brandIds.map(id => <FilterChip key={`brand-${id}`} label={`Thương hiệu: ${brandName(id)}`} onRemove={() => changeFilters({ brandIds: filters.brandIds.filter(value => value !== id) })} />)}
            {filters.activeIngredients.map(id => <FilterChip key={`active-${id}`} label={`Hoạt chất: ${ACTIVE_INGREDIENT_FILTERS.find(item => item.id === id)?.label || id}`} onRemove={() => changeFilters({ activeIngredients: filters.activeIngredients.filter(value => value !== id) })} />)}
            {keyword && <FilterChip label={`Từ khóa: "${keyword}"`} onRemove={() => { setKeyword(''); changeFilters({ keyword: '' }); }} />}
            {filters.skinType !== 'ALL' && <FilterChip label={`Loại da: ${SKIN_TYPES.find(item => item.id === filters.skinType)?.label || filters.skinType}`} onRemove={() => changeFilters({ skinType: 'ALL' })} />}
            {filters.priceRange !== 'ALL' && <FilterChip label={`Giá: ${PRICE_RANGES.find(item => item.id === filters.priceRange)?.label || filters.priceRange}`} onRemove={() => changeFilters({ priceRange: 'ALL' })} />}
            {filters.activeFilter && <FilterChip label={filters.activeFilter === 'sale' ? 'Giờ Vàng Flash Sale' : 'Bán Chạy Nhất'} onRemove={() => changeFilters({ activeFilter: '' })} />}
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
