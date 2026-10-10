import axios from 'axios';
import { ENDPOINTS } from './endpoints';

import { serverBaseUrl } from './baseUrl';

const API_BASE_URL = serverBaseUrl(import.meta.env.VITE_API_BASE_URL);

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// Request Interceptor: Attach Access Token & Guest Session ID
apiClient.interceptors.request.use(
  (config) => {
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }

    const isAuthEndpoint =
      config.url?.includes('/api/v1/auth/login') ||
      config.url?.includes('/api/v1/auth/refresh');

    if (!isAuthEndpoint) {
      const token = localStorage.getItem('beautyshop_admin_token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }

    try {
      let guestSessionId = localStorage.getItem('beautyshop_guest_session_id');
      if (!guestSessionId) {
        guestSessionId = 'guest_' + Math.random().toString(36).substring(2, 12) + '_' + Date.now();
        localStorage.setItem('beautyshop_guest_session_id', guestSessionId);
      }
      if (guestSessionId && !config.headers['X-Guest-Session-Id']) {
        config.headers['X-Guest-Session-Id'] = guestSessionId;
      }
    } catch (e) {
      // ignore storage access issues
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Auto Refresh on 401 & ApiResponse normalization
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => {
    // If response matches backend ApiResponse wrapper, extract data or return body
    return response.data;
  },
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('beautyshop_admin_refresh_token');

      if (!refreshToken || originalRequest.url.includes('/api/v1/auth/refresh')) {
        localStorage.removeItem('beautyshop_admin_token');
        localStorage.removeItem('beautyshop_admin_refresh_token');
        localStorage.removeItem('beautyshop_admin_user');
        window.dispatchEvent(new Event('auth:logout'));
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshResponse = await axios.post(
          `${API_BASE_URL}${ENDPOINTS.AUTH.REFRESH}`,
          { refreshToken },
          { headers: { 'Content-Type': 'application/json' } }
        );

        const newTokens = refreshResponse.data?.data || refreshResponse.data;
        if (newTokens?.accessToken) {
          localStorage.setItem('beautyshop_admin_token', newTokens.accessToken);
          if (newTokens.refreshToken) {
            localStorage.setItem('beautyshop_admin_refresh_token', newTokens.refreshToken);
          }
          apiClient.defaults.headers.common.Authorization = `Bearer ${newTokens.accessToken}`;
          originalRequest.headers.Authorization = `Bearer ${newTokens.accessToken}`;
          processQueue(null, newTokens.accessToken);
          return apiClient(originalRequest);
        }
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        localStorage.removeItem('beautyshop_admin_token');
        localStorage.removeItem('beautyshop_admin_refresh_token');
        localStorage.removeItem('beautyshop_admin_user');
        window.dispatchEvent(new Event('auth:logout'));
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    const message =
      error.response?.data?.message ||
      error.response?.data?.errorCode ||
      error.message ||
      'Lỗi kết nối máy chủ';

    return Promise.reject(Object.assign(new Error(message), { status: error.response?.status }));
  }
);

export const uploadFile = async (endpoint, file) => {
  const formData = new FormData();
  formData.append('file', file);
  const token = localStorage.getItem('beautyshop_admin_token');
  const headers = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const url = endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: formData,
    credentials: 'include',
  });
  if (!response.ok) {
    const errorJson = await response.json().catch(() => null);
    const message = errorJson?.message || errorJson?.error || `Lỗi tải tệp: HTTP ${response.status}`;
    throw new Error(message);
  }
  const result = await response.json();
  return result?.data || result;
};
