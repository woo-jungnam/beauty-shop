import { apiClient } from '../../shared/api/client';

export async function searchProductsByImage(file, optionsOrSignal, maybeSignal) {
  let options = {};
  let signal = null;

  if (optionsOrSignal instanceof AbortSignal) {
    signal = optionsOrSignal;
  } else if (optionsOrSignal && typeof optionsOrSignal === 'object') {
    options = optionsOrSignal;
    signal = maybeSignal;
  }

  const form = new FormData();
  form.append('image', file);

  const params = new URLSearchParams();
  if (options.categoryId) params.append('categoryId', String(options.categoryId));
  if (options.brandId) params.append('brandId', String(options.brandId));
  if (options.minSimilarity != null) params.append('minSimilarity', String(options.minSimilarity));

  const queryString = params.toString() ? `?${params.toString()}` : '';

  const response = await apiClient.post(`/api/v1/products/search-by-image${queryString}`, form, {
    signal,
    timeout: 45000,
  });
  return response.data;
}
