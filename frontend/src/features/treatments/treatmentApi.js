import { apiClient } from '../../shared/api/client';

export const unwrap = response => response?.data ?? response;
export const rows = response => { const data = unwrap(response); return Array.isArray(data) ? data : data?.content || []; };
export const courseRoot = role => `/api/v1/${role === 'customer' ? '' : `${role}/`}spa/treatments`;
export const templateRoot = admin => `/api/v1/${admin ? 'admin/' : ''}spa/treatment-templates`;
export const errorMessage = error => error.response?.data?.message || error.message || 'Chưa thực hiện được. Vui lòng thử lại.';
export { hasAction, money, dateLabel, today, idempotencyKey, statusLabel } from './treatmentModel';

export const treatmentApi = {
  templates: async admin => rows(await apiClient.get(templateRoot(admin))),
  template: async (id, admin) => unwrap(await apiClient.get(`${templateRoot(admin)}/${id}`)),
  courses: async role => rows(await apiClient.get(role === 'customer' ? '/api/v1/spa/my-treatments' : courseRoot(role))),
  course: async (role, id) => unwrap(await apiClient.get(`${courseRoot(role)}/${id}`)),
  post: async (path, body) => unwrap(await apiClient.post(path, body)),
};
