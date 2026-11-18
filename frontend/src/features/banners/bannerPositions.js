export const TREATMENT_HERO_POSITION = 'TREATMENT_HERO';
export const CATALOG_HERO_POSITION = 'CATALOG_HERO';
export const BOOKING_HERO_POSITION = 'BOOKING_HERO';

export const BANNER_POSITIONS = [
  {
    value: 'HERO_SLIDE',
    label: 'Slide chính trang chủ (Hero Slider)',
    shortLabel: 'Slide chính',
    group: 'Trang chủ - Hero',
    isSectionBanner: false,
    recommendedRatio: '16:7 (~1600×700px)',
  },
  {
    value: 'HERO_SIDE',
    label: 'Banner phụ bên cạnh Hero Slider',
    shortLabel: 'Banner phụ',
    group: 'Trang chủ - Hero',
    isSectionBanner: false,
    recommendedRatio: '8:4 (~800×400px)',
  },
  {
    value: 'SECTION_FLASH_SALE',
    label: 'Banner dưới H2: Flash Sale Giờ Vàng',
    shortLabel: 'Flash Sale',
    group: 'Banner Section (Dưới H2)',
    isSectionBanner: true,
    recommendedRatio: 'Full section width (~1600 x 332px)',
  },
  {
    value: 'SECTION_CATEGORIES',
    label: '3 banner nhỏ dưới H2: Danh Mục Sản Phẩm',
    shortLabel: 'Danh mục SP',
    group: 'Banner Section (Dưới H2)',
    isSectionBanner: true,
    recommendedRatio: '3:1 (~900 x 300px), tối đa 3 banner theo thứ tự',
  },
  {
    value: 'SECTION_BRANDS',
    label: '3 banner nhỏ dưới H2: Thương Hiệu Đối Tác',
    shortLabel: 'Thương hiệu',
    group: 'Banner Section (Dưới H2)',
    isSectionBanner: true,
    recommendedRatio: '3:1 (~900 x 300px), tối đa 3 banner theo thứ tự',
  },
  {
    value: 'SECTION_RECOMMENDATIONS',
    label: 'Banner dưới H2: Gợi Ý Cho Bạn',
    shortLabel: 'Gợi ý cho bạn',
    group: 'Banner Section (Dưới H2)',
    isSectionBanner: true,
    recommendedRatio: 'Full section width (~1600 x 332px)',
  },
  {
    value: 'SECTION_CATALOG',
    label: 'Banner dưới H2: Tất Cả Sản Phẩm',
    shortLabel: 'Tất cả sản phẩm',
    group: 'Banner Section (Dưới H2)',
    isSectionBanner: true,
    recommendedRatio: 'Full section width (~1600 x 332px)',
  },
  {
    value: 'SECTION_SPA',
    label: 'Banner dưới H2: Dịch Vụ Spa Làm Đẹp',
    shortLabel: 'Dịch vụ Spa',
    group: 'Banner Section (Dưới H2)',
    isSectionBanner: true,
    recommendedRatio: 'Full section width (~1600 x 332px)',
  },
  {
    value: TREATMENT_HERO_POSITION,
    label: 'Ảnh đầu trang Lộ trình chăm sóc',
    shortLabel: 'Lộ trình chăm sóc',
    group: 'Trang Lộ trình chăm sóc',
    isSectionBanner: false,
    recommendedRatio: '12:5 (~1200 × 500 px)',
    pageAnchor: '#treatment-catalog',
    previewAspectRatio: '12 / 5',
  },
  {
    value: CATALOG_HERO_POSITION,
    label: 'Ảnh đầu trang Danh sách sản phẩm',
    shortLabel: 'Danh sách sản phẩm',
    group: 'Trang Danh sách sản phẩm',
    isSectionBanner: false,
    recommendedRatio: 'Khoảng 1600 × 332 px',
    pageAnchor: '#catalog-results',
    previewAspectRatio: '1600 / 332',
  },
  {
    value: BOOKING_HERO_POSITION,
    label: 'Ảnh đầu trang Đặt lịch Spa',
    shortLabel: 'Đặt lịch Spa',
    group: 'Trang Đặt lịch Spa',
    isSectionBanner: false,
    recommendedRatio: 'Khoảng 1600 × 360 px',
    pageAnchor: '#spa-booking-form',
    previewAspectRatio: '1600 / 360',
  },
  {
    value: 'PROMO',
    label: 'Khuyến mãi khác / Promo chung',
    shortLabel: 'Khác (Promo)',
    group: 'Khác',
    isSectionBanner: false,
    recommendedRatio: 'Tùy chọn',
  },
];

export const BANNER_POSITION_MAP = Object.fromEntries(
  BANNER_POSITIONS.map((item) => [item.value, item])
);

export function getBannerPositionLabel(position) {
  if (!position) return 'Mặc định';
  return BANNER_POSITION_MAP[position]?.shortLabel || BANNER_POSITION_MAP[position]?.label || position;
}

export function isSectionBanner(position) {
  return Boolean(BANNER_POSITION_MAP[position]?.isSectionBanner);
}
