export const TREATMENT_HERO_POSITION = 'TREATMENT_HERO';
export const CATALOG_HERO_POSITION = 'CATALOG_HERO';
export const BOOKING_HERO_POSITION = 'BOOKING_HERO';

export const BANNER_POSITIONS = [
  {
    value: 'HERO_SLIDE',
    label: 'Ảnh trình chiếu đầu trang chủ',
    shortLabel: 'Ảnh trình chiếu chính',
    group: 'Đầu trang chủ',
    isSectionBanner: false,
    recommendedRatio: '16:7 (~1600×700px)',
  },
  {
    value: 'HERO_SIDE',
    label: 'Ảnh phụ cạnh ảnh trình chiếu',
    shortLabel: 'Ảnh phụ',
    group: 'Đầu trang chủ',
    isSectionBanner: false,
    recommendedRatio: '8:4 (~800×400px)',
  },
  {
    value: 'SECTION_FLASH_SALE',
    label: 'Ảnh dưới tiêu đề Giảm giá theo khung giờ',
    shortLabel: 'Giảm giá theo khung giờ',
    group: 'Ảnh dưới tiêu đề từng mục',
    isSectionBanner: true,
    recommendedRatio: 'Rộng bằng khu vực hiển thị (~1600 x 332px)',
  },
  {
    value: 'SECTION_CATEGORIES',
    label: '3 ảnh nhỏ dưới tiêu đề Danh mục sản phẩm',
    shortLabel: 'Danh mục SP',
    group: 'Ảnh dưới tiêu đề từng mục',
    isSectionBanner: true,
    recommendedRatio: '3:1 (~900 x 300px), tối đa 3 ảnh theo thứ tự',
  },
  {
    value: 'SECTION_BRANDS',
    label: '3 ảnh nhỏ dưới tiêu đề Thương hiệu đối tác',
    shortLabel: 'Thương hiệu',
    group: 'Ảnh dưới tiêu đề từng mục',
    isSectionBanner: true,
    recommendedRatio: '3:1 (~900 x 300px), tối đa 3 ảnh theo thứ tự',
  },
  {
    value: 'SECTION_RECOMMENDATIONS',
    label: 'Ảnh dưới tiêu đề Gợi ý cho bạn',
    shortLabel: 'Gợi ý cho bạn',
    group: 'Ảnh dưới tiêu đề từng mục',
    isSectionBanner: true,
    recommendedRatio: 'Rộng bằng khu vực hiển thị (~1600 x 332px)',
  },
  {
    value: 'SECTION_CATALOG',
    label: 'Ảnh dưới tiêu đề Tất cả sản phẩm',
    shortLabel: 'Tất cả sản phẩm',
    group: 'Ảnh dưới tiêu đề từng mục',
    isSectionBanner: true,
    recommendedRatio: 'Rộng bằng khu vực hiển thị (~1600 x 332px)',
  },
  {
    value: 'SECTION_SPA',
    label: 'Ảnh dưới tiêu đề Dịch vụ Spa làm đẹp',
    shortLabel: 'Dịch vụ Spa',
    group: 'Ảnh dưới tiêu đề từng mục',
    isSectionBanner: true,
    recommendedRatio: 'Rộng bằng khu vực hiển thị (~1600 x 332px)',
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
    label: 'Khuyến mãi khác',
    shortLabel: 'Khác',
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
  return BANNER_POSITION_MAP[position]?.shortLabel || BANNER_POSITION_MAP[position]?.label || 'Vị trí chưa xác định';
}

export function isSectionBanner(position) {
  return Boolean(BANNER_POSITION_MAP[position]?.isSectionBanner);
}
