export const ACTIVE_INGREDIENT_FILTERS = [
  { id: 'niacinamide', label: 'Niacinamide (Làm sáng/Kiềm dầu)' },
  { id: 'panthenol', label: 'Panthenol B5 (Phục hồi da)' },
  { id: 'salicylic acid', label: 'BHA / Salicylic Acid (Trị mụn)' },
  { id: 'hyaluronic acid', label: 'Hyaluronic Acid (Cấp nước)' },
  { id: 'ceramide', label: 'Ceramide (Hàng rào bảo vệ)' },
];
export const SKIN_TYPES = [
  { id: 'ALL', label: 'Tất cả loại da' },
  { id: 'OILY', label: 'Da Dầu & Lỗ Chân Lông To' },
  { id: 'DRY', label: 'Da Khô & Thiếu Ẩm' },
  { id: 'SENSITIVE', label: 'Da Nhạy Cảm / Mẩn Đỏ' },
  { id: 'COMBINATION', label: 'Da Hỗn Hợp Thiên Dầu' },
];
export const PRICE_RANGES = [
  { id: 'ALL', label: 'Mọi mức giá' },
  { id: 'UNDER_300', label: 'Dưới 300.000 ₫', maxPrice: '299999' },
  { id: '300_600', label: 'Từ 300.000 ₫ - 600.000 ₫', minPrice: '300000', maxPrice: '600000' },
  { id: 'OVER_600', label: 'Trên 600.000 ₫', minPrice: '600001' },
];

const values = (params, ...keys) => [...new Set(keys.flatMap(key =>
  params.getAll(key).flatMap(value => value.split(',').map(item => item.trim()).filter(Boolean))
))];

export const readCatalogFilters = (query = '') => {
  const params = new URLSearchParams(query);
  const page = Number(params.get('page') || 0);
  return {
    keyword: params.get('search') || '',
    categoryIds: values(params, 'categoryId', 'categoryIds'),
    brandIds: values(params, 'brandId', 'brandIds'),
    activeIngredients: values(params, 'activeIngredients'),
    skinType: params.get('skinType') || 'ALL',
    priceRange: params.get('priceRange') || 'ALL',
    sortBy: params.get('sort') || 'popular',
    activeFilter: params.get('filter') || '',
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    categorySlug: params.get('category') || '',
    categoryName: params.get('name') || '',
    brandSlug: params.get('brand') || '',
    brandName: params.get('brandName') || '',
  };
};

export const catalogRoute = (filters) => {
  const params = new URLSearchParams();
  if (filters.keyword.trim()) params.set('search', filters.keyword.trim());
  for (const key of ['categoryIds', 'brandIds', 'activeIngredients']) {
    if (filters[key].length) params.set(key, filters[key].join(','));
  }
  if (filters.skinType !== 'ALL') params.set('skinType', filters.skinType);
  if (filters.priceRange !== 'ALL') params.set('priceRange', filters.priceRange);
  if (filters.sortBy !== 'popular') params.set('sort', filters.sortBy);
  if (filters.activeFilter) params.set('filter', filters.activeFilter);
  if (filters.page > 0) params.set('page', String(filters.page));
  return `products${params.size ? `?${params}` : ''}`;
};

export const catalogSearchParams = (filters) => {
  const params = new URLSearchParams({ size: '60', page: String(filters.page) });
  if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim());
  for (const key of ['categoryIds', 'brandIds', 'activeIngredients']) {
    if (filters[key].length) params.set(key, filters[key].join(','));
  }
  if (filters.skinType !== 'ALL') params.set('skinType', filters.skinType);
  const price = PRICE_RANGES.find(item => item.id === filters.priceRange);
  if (price?.minPrice) params.set('minPrice', price.minPrice);
  if (price?.maxPrice) params.set('maxPrice', price.maxPrice);
  if (filters.activeFilter === 'sale') params.set('onSale', 'true');
  params.set('sort', { price_asc: 'price,asc', price_desc: 'price,desc', rating: 'averageRating,desc' }[filters.sortBy] || 'totalSold,desc');
  return params.toString();
};

export const flattenCategories = (categories, depth = 0) => categories.flatMap(category => [
  { ...category, depth },
  ...flattenCategories(Array.isArray(category.children) ? category.children : [], depth + 1),
]);

export const toggleFilterValue = (selected, value) => selected.includes(value)
  ? selected.filter(item => item !== value) : [...selected, value];
