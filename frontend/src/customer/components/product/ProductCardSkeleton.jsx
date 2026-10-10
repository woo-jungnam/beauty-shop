import React from 'react';

/**
 * ProductCardSkeleton
 * Khung placeholder mô phỏng cấu trúc ProductCard 1:1,
 * duy trì layout ổn định (CLS = 0) và hiệu ứng shimmer mượt mà trong lúc tải dữ liệu.
 */
export const ProductCardSkeleton = () => {
  return (
    <div
      className="luxury-card product-card-skeleton"
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: 'var(--radius-md, 6px)',
        overflow: 'hidden',
        height: '100%',
        userSelect: 'none',
      }}
      aria-hidden="true"
    >
      {/* 1. Image Media Container - Tỉ lệ 1:1 chuẩn xác với ProductCard */}
      <div
        style={{
          position: 'relative',
          width: '100%',
          paddingTop: '100%',
          overflow: 'hidden',
        }}
      >
        {/* Lớp nền shimmer toàn ảnh */}
        <div
          className="skeleton-shimmer"
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
          }}
        />

        {/* Khung badge góc trái trên (Hot deal / Discount) */}
        <div
          className="skeleton-shimmer"
          style={{
            position: 'absolute',
            top: '8px',
            left: '8px',
            width: '44px',
            height: '18px',
            borderRadius: '3px',
            zIndex: 2,
          }}
        />

        {/* Khung nút tròn wishlist góc phải trên */}
        <div
          className="skeleton-shimmer"
          style={{
            position: 'absolute',
            top: '10px',
            right: '10px',
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            zIndex: 2,
          }}
        />
      </div>

      {/* 2. Content Body - Đồng bộ padding và khoảng cách dòng */}
      <div
        style={{
          padding: '12px 14px',
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
        }}
      >
        {/* Khung tên thương hiệu */}
        <div
          className="skeleton-shimmer"
          style={{
            width: '38%',
            height: '11px',
            borderRadius: '3px',
            marginBottom: '8px',
          }}
        />

        {/* Khung tiêu đề sản phẩm: 2 dòng giữ vững minHeight 36px tránh giật layout */}
        <div
          style={{
            minHeight: '36px',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px',
            marginBottom: '6px',
          }}
        >
          <div
            className="skeleton-shimmer"
            style={{
              width: '95%',
              height: '13px',
              borderRadius: '3px',
            }}
          />
          <div
            className="skeleton-shimmer"
            style={{
              width: '65%',
              height: '13px',
              borderRadius: '3px',
            }}
          />
        </div>

        {/* Khung rating sao và số lượng đã bán */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: '6px',
            gap: '8px',
          }}
        >
          <div
            className="skeleton-shimmer"
            style={{
              width: '36%',
              height: '12px',
              borderRadius: '3px',
            }}
          />
          <div
            className="skeleton-shimmer"
            style={{
              width: '28%',
              height: '12px',
              borderRadius: '3px',
            }}
          />
        </div>

        {/* Khung giá khuyến mãi và giá gốc */}
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: '8px',
            marginTop: '10px',
          }}
        >
          <div
            className="skeleton-shimmer"
            style={{
              width: '52%',
              height: '18px',
              borderRadius: '3px',
            }}
          />
          <div
            className="skeleton-shimmer"
            style={{
              width: '26%',
              height: '12px',
              borderRadius: '3px',
            }}
          />
        </div>
      </div>
    </div>
  );
};

/**
 * ProductGridSkeleton
 * Lưới khung sản phẩm hiển thị khi reload bộ lọc, tìm kiếm hoặc fetch dữ liệu ban đầu
 */
export const ProductGridSkeleton = ({ count = 8 }) => {
  return (
    <div
      className="catalog-products-grid catalog-grid-fade-in product-skeleton-frame"
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
        gap: '20px',
      }}
      aria-busy="true"
      aria-label="Đang tải danh sách sản phẩm"
    >
      {Array.from({ length: count }).map((_, idx) => (
        <ProductCardSkeleton key={idx} />
      ))}
    </div>
  );
};
