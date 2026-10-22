import { useLayoutEffect, useState } from 'react';

const initialLimits = {
  deals: 4,
  categories: 4,
  brands: 4,
  recommendations: 4,
  catalog: 4,
  spa: 2,
};

export const useHomeSectionLimits = (pageRef) => {
  const [limits, setLimits] = useState(initialLimits);

  useLayoutEffect(() => {
    const page = pageRef.current;
    if (!page) return;

    const columns = (selector) => {
      const grid = page.querySelector(selector);
      const tracks = grid ? getComputedStyle(grid).gridTemplateColumns : 'none';
      return tracks === 'none' ? 1 : tracks.trim().split(/\s+/).length;
    };
    const updateLimits = () => {
      const productColumns = columns('#random-picks .customer-product-grid');
      const dealColumns = columns('.flash-sale-grid');
      const categoryColumns = columns('.category-grid-8');
      const brandColumns = columns('.brands-grid-12');
      const spaColumns = columns('.spa-services-grid');
      const next = {
        deals: dealColumns * (dealColumns <= 3 ? 2 : 1),
        categories: categoryColumns * (categoryColumns < 8 ? 2 : 1),
        brands: brandColumns * (brandColumns < 12 ? 2 : 1),
        recommendations: productColumns * (productColumns <= 3 ? 2 : 1),
        catalog: productColumns * 2,
        spa: spaColumns * (spaColumns <= 4 ? 2 : 1),
      };
      setLimits(current => Object.keys(next).every(key => current[key] === next[key]) ? current : next);
    };

    updateLimits();
    const observer = new ResizeObserver(updateLimits);
    observer.observe(page);
    return () => observer.disconnect();
  }, [pageRef]);

  return limits;
};
