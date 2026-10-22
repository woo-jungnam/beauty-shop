import React from 'react';

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
      <div
        className="product-card-media"
        style={{
          position: 'relative',
          width: '100%',
          paddingTop: 'var(--product-card-media-padding, 100%)',
          overflow: 'hidden',
        }}
      >
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

      <div
        className="product-card-body"
        style={{
          padding: 'var(--product-card-body-padding, 12px 14px)',
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
        }}
      >
        <div
          className="skeleton-shimmer"
          style={{
            width: '38%',
            height: '11px',
            borderRadius: '3px',
            marginBottom: '8px',
          }}
        />

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
