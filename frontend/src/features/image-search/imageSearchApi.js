import { apiClient } from '../../shared/api/client';

export async function searchProductsByImage(file, signal) {
  const form = new FormData();
  form.append('image', file);
  const response = await apiClient.post('/api/v1/products/search-by-image', form, {
    signal,
    timeout: 45000,
  });
  return response.data;
}
