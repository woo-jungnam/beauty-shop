import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem('beautyshop_admin_user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [token, setToken] = useState(() => localStorage.getItem('beautyshop_admin_token'));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const handleLogoutEvent = () => {
      setUser(null);
      setToken(null);
    };

    window.addEventListener('auth:logout', handleLogoutEvent);

    // Initial check: if token exists, fetch current user profile
    const verifySession = async () => {
      if (token) {
        try {
          const res = await apiClient.get(ENDPOINTS.AUTH.PROFILE);
          const profile = res.data || res;
          setUser(profile);
          localStorage.setItem('beautyshop_admin_user', JSON.stringify(profile));
        } catch {
          // Token expired or invalid
          setUser(null);
          setToken(null);
          localStorage.removeItem('beautyshop_admin_token');
          localStorage.removeItem('beautyshop_admin_refresh_token');
          localStorage.removeItem('beautyshop_admin_user');
        }
      }
      setLoading(false);
    };

    verifySession();

    return () => {
      window.removeEventListener('auth:logout', handleLogoutEvent);
    };
  }, [token]);

  const login = async (username, password, { requireOperator = true } = {}) => {
    const response = await apiClient.post(ENDPOINTS.AUTH.LOGIN, {
      usernameOrEmail: username,
      username,
      password,
    });

    const data = response.data || response;
    const roles = data.roles || [];

    // Verify administrator / staff authority for Operator Console login.
    const operationalRoles = [
      'ROLE_ADMIN', 'ADMIN',
      'ROLE_STAFF', 'STAFF',
      'ROLE_SPA_RECEPTION', 'SPA_RECEPTION',
      'ROLE_SPA_THERAPIST', 'SPA_THERAPIST',
      'ROLE_ORDER_STAFF', 'ROLE_INVENTORY_STAFF', 'ROLE_CATALOG_STAFF', 'ROLE_CS_STAFF'
    ];
    if (requireOperator && !roles.some((role) => operationalRoles.includes(role))) {
      throw new Error('Tài khoản của bạn không có quyền truy cập hệ thống vận hành. Vui lòng liên hệ quản trị hệ thống.');
    }

    localStorage.setItem('beautyshop_admin_token', data.accessToken);
    if (data.refreshToken) {
      localStorage.setItem('beautyshop_admin_refresh_token', data.refreshToken);
    }
    const sessionUser = {
      id: data.id,
      username: data.username,
      email: data.email,
      fullName: data.fullName,
      roles: data.roles,
    };
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(sessionUser));

    setToken(data.accessToken);
    setUser(sessionUser);
    return sessionUser;
  };

  const register = async (registerData) => {
    const response = await apiClient.post(ENDPOINTS.AUTH.REGISTER, registerData);
    const data = response.data || response;

    if (data.accessToken) {
      localStorage.setItem('beautyshop_admin_token', data.accessToken);
      if (data.refreshToken) {
        localStorage.setItem('beautyshop_admin_refresh_token', data.refreshToken);
      }
      const sessionUser = {
        id: data.id,
        username: data.username,
        email: data.email,
        fullName: data.fullName,
        roles: data.roles,
      };
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(sessionUser));
      setToken(data.accessToken);
      setUser(sessionUser);
      return sessionUser;
    }
    return data;
  };

  const logout = async () => {
    try {
      const refreshToken = localStorage.getItem('beautyshop_admin_refresh_token');
      if (refreshToken) {
        await apiClient.post(ENDPOINTS.AUTH.LOGOUT, { refreshToken });
      }
    } catch {
      // Ignore logout request errors
    } finally {
      localStorage.removeItem('beautyshop_admin_token');
      localStorage.removeItem('beautyshop_admin_refresh_token');
      localStorage.removeItem('beautyshop_admin_user');
      setToken(null);
      setUser(null);
    }
  };

  const value = {
    user,
    token,
    loading,
    isAuthenticated: !!token && !!user,
    isAdmin: user?.roles?.some((r) => r === 'ROLE_ADMIN' || r === 'ADMIN') ?? false,
    isOperator: user?.roles?.some((r) => [
      'ROLE_ADMIN', 'ADMIN',
      'ROLE_STAFF', 'STAFF',
      'ROLE_SPA_RECEPTION', 'SPA_RECEPTION',
      'ROLE_SPA_THERAPIST', 'SPA_THERAPIST',
      'ROLE_ORDER_STAFF', 'ROLE_INVENTORY_STAFF', 'ROLE_CATALOG_STAFF', 'ROLE_CS_STAFF'
    ].includes(r)) ?? false,
    isSpaReception: user?.roles?.some((r) => ['ROLE_SPA_RECEPTION', 'SPA_RECEPTION', 'ROLE_STAFF', 'ROLE_ADMIN', 'ADMIN'].includes(r)) ?? false,
    isSpaTherapist: user?.roles?.some((r) => ['ROLE_SPA_THERAPIST', 'SPA_THERAPIST'].includes(r)) ?? false,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
