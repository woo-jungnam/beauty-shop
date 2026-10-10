export const ENDPOINTS = {
  // Authentication & Session
  AUTH: {
    LOGIN: '/api/v1/auth/login',
    REGISTER: '/api/v1/auth/register',
    REFRESH: '/api/v1/auth/refresh',
    LOGOUT: '/api/v1/auth/logout',
    PROFILE: '/api/v1/auth/profile',
  },

  // Admin Dashboard Analytics
  DASHBOARD: {
    OVERVIEW: '/api/v1/admin/dashboard',
    REVENUE: '/api/v1/admin/dashboard/revenue',
    TOP_PRODUCTS: '/api/v1/admin/dashboard/top-products',
    SPA_OCCUPANCY: '/api/v1/admin/dashboard/spa-occupancy',
  },

  // Admin User & Security Moderation
  USERS: {
    LIST: '/api/v1/admin/users',
    DETAIL: (id) => `/api/v1/admin/users/${id}`,
    UPDATE_STATUS: (id) => `/api/v1/admin/users/${id}/status`,
    FORCE_LOGOUT: (id) => `/api/v1/admin/users/${id}/force-logout`,
    UPDATE_ROLES: (id) => `/api/v1/admin/users/${id}/roles`,
    SESSIONS: (id) => `/api/v1/admin/users/${id}/sessions`,
    REVOKE_SESSION: (id, sessionId) => `/api/v1/admin/users/${id}/sessions/${sessionId}`,
    STATUS_HISTORY: (id) => `/api/v1/admin/users/${id}/status-history`,
    RESET_PASSWORD: (id) => `/api/v1/admin/users/${id}/reset-password`,
  },

  // Cart Operations
  CART: {
    GET: (sessionId) => sessionId ? `/api/v1/cart?sessionId=${encodeURIComponent(sessionId)}` : '/api/v1/cart',
    ADD: '/api/v1/cart/add',
    UPDATE_ITEM: (itemId, sessionId) => sessionId ? `/api/v1/cart/items/${itemId}?sessionId=${encodeURIComponent(sessionId)}` : `/api/v1/cart/items/${itemId}`,
    REMOVE_ITEM: (itemId, sessionId) => sessionId ? `/api/v1/cart/items/${itemId}?sessionId=${encodeURIComponent(sessionId)}` : `/api/v1/cart/items/${itemId}`,
    CLEAR: (sessionId) => sessionId ? `/api/v1/cart/clear?sessionId=${encodeURIComponent(sessionId)}` : '/api/v1/cart/clear',
    MERGE: (sessionId) => `/api/v1/cart/merge?sessionId=${encodeURIComponent(sessionId)}`,
  },

  // Order Processing & Checkout
  ORDERS: {
    CHECKOUT: '/api/v1/orders/checkout',
    MY_ORDERS: '/api/v1/orders/my-orders',
    DETAIL: (id) => `/api/v1/orders/${id}`,
    CANCEL: (id) => `/api/v1/orders/${id}/cancel`,
    LIST: '/api/v1/admin/orders',
    UPDATE_STATUS: (id) => `/api/v1/admin/orders/${id}/status`,
    CONFIRM_REFUND: (id) => `/api/v1/admin/orders/${id}/refund-confirmation`,
  },

  // Inventory & Warehouse Management
  INVENTORY: {
    TRANSACTIONS: '/api/v1/admin/inventory/transactions',
    LOW_STOCK: '/api/v1/admin/inventory/low-stock',
    EXPIRING_SOON: '/api/v1/admin/inventory/expiring-soon',
    INSPECTION: (stockId) => `/api/v1/admin/inventory/${stockId}/inspection`,
    WAREHOUSES: '/api/v1/admin/warehouses',
    WAREHOUSE_DETAIL: (id) => `/api/v1/admin/warehouses/${id}`,
    WAREHOUSE_STOCKS: (id) => `/api/v1/admin/warehouses/${id}/stocks`,
    WAREHOUSE_RECEIPTS: (id) => `/api/v1/admin/warehouses/${id}/receipts`,
    STOCK_ADJUSTMENT: (id) => `/api/v1/admin/warehouses/stocks/${id}/adjustment`,
    STOCK_TRANSFER: (id) => `/api/v1/admin/warehouses/stocks/${id}/transfer`,
    STOCK_DELETE: (stockId) => `/api/v1/admin/warehouses/stocks/${stockId}`,
  },

  // Promotions & Vouchers
  VOUCHERS: {
    LIST: '/api/v1/admin/vouchers',
    DETAIL: (id) => `/api/v1/admin/vouchers/${id}`,
    CREATE: '/api/v1/admin/vouchers',
    UPDATE: (id) => `/api/v1/admin/vouchers/${id}`,
    DELETE: (id) => `/api/v1/admin/vouchers/${id}`,
  },

  // Catalog, Products & Media
  CATALOG: {
    PUBLIC_PRODUCTS: '/api/v1/products',
    PUBLIC_PRODUCT_DETAIL: (id) => `/api/v1/products/${id}`,
    PUBLIC_PRODUCT_BY_SLUG: (slug) => `/api/v1/products/slug/${slug}`,
    PUBLIC_BY_CATEGORY: (categoryId) => `/api/v1/products/category/${categoryId}`,
    PUBLIC_BY_BRAND: (brandId) => `/api/v1/products/brand/${brandId}`,
    PUBLIC_SEARCH: (keyword) => `/api/v1/products/search?keyword=${encodeURIComponent(keyword)}`,
    PUBLIC_SIMILAR_PRODUCTS: (id, limit = 8) => `/api/v1/products/${id}/similar?limit=${limit}`,
    PUBLIC_RECOMMENDED_FOR_YOU: (sessionId, limit = 8) => `/api/v1/products/recommended-for-you?limit=${limit}${sessionId ? `&sessionId=${encodeURIComponent(sessionId)}` : ''}`,
    TRACKING_INTERACTION: '/api/v1/products/tracking/interaction',
    PRODUCTS: '/api/v1/admin/products',
    PRODUCT_DETAIL: (id) => `/api/v1/admin/products/${id}`,
    VARIANTS: (productId) => `/api/v1/admin/products/${productId}/variants`,
    VARIANT_DETAIL: (productId, variantId) => `/api/v1/admin/products/${productId}/variants/${variantId}`,
    IMAGES: (productId) => `/api/v1/admin/products/${productId}/images`,
    IMAGE_DETAIL: (productId, imageId) => `/api/v1/admin/products/${productId}/images/${imageId}`,
    CATEGORIES: '/api/v1/categories',
    BRANDS: '/api/v1/brands',
    TAGS: '/api/v1/tags',
    TAG_DETAIL: (id) => `/api/v1/tags/${id}`,
    INGREDIENTS: '/api/v1/admin/ingredients',
    INGREDIENT_DETAIL: (id) => `/api/v1/admin/ingredients/${id}`,
    PRODUCT_INGREDIENTS: (productId) => `/api/v1/admin/ingredients/products/${productId}`,
    PRODUCT_ATTRIBUTES: (productId) => `/api/v1/admin/products/${productId}/attributes`,
    MEDIA_UPLOAD: '/api/v1/admin/media/upload',
    DERMATOLOGY_SKIN_TYPES: '/api/v1/admin/dermatology/skin-types',
    DERMATOLOGY_SKIN_CONCERNS: '/api/v1/admin/dermatology/skin-concerns',
    DERMATOLOGY_PROFILE: (productId) => `/api/v1/admin/dermatology/products/${productId}`,
    DERMATOLOGY_USAGE: (productId) => `/api/v1/admin/dermatology/products/${productId}/usage`,
    DERMATOLOGY_SKIN_TYPES_UPDATE: (productId) => `/api/v1/admin/dermatology/products/${productId}/skin-types`,
    DERMATOLOGY_SKIN_CONCERNS_UPDATE: (productId) => `/api/v1/admin/dermatology/products/${productId}/skin-concerns`,
    PUBLIC_BANNERS: (position) => position ? `/api/v1/banners?position=${encodeURIComponent(position)}` : '/api/v1/banners',
    BANNERS: '/api/v1/admin/banners',
    BANNER_DETAIL: (id) => `/api/v1/admin/banners/${id}`,
  },

  // Spa Services, Staff & Tickets
  SPA: {
    PUBLIC_SERVICES: '/api/v1/spa/services',
    PUBLIC_STAFF: '/api/v1/spa/services/staff',
    PUBLIC_PACKAGES: '/api/v1/spa/services/packages',
    PUBLIC_SERVICE_STAFF: (id) => `/api/v1/spa/services/${id}/staff`,
    PUBLIC_AVAILABLE_SLOTS: (id, date, staffId) => `/api/v1/spa/services/${id}/available-slots?date=${encodeURIComponent(date)}${staffId ? `&staffId=${encodeURIComponent(staffId)}` : ''}`,
    MY_TICKETS: '/api/v1/spa/tickets/my-tickets',
    MY_ACTIVE_TICKETS: '/api/v1/spa/tickets/my-active-tickets',
    MY_APPOINTMENTS: '/api/v1/appointments/my-appointments',
    BOOK_APPOINTMENT: '/api/v1/appointments/book',
    CANCEL_APPOINTMENT: (id) => `/api/v1/appointments/${id}/cancel`,
    RESCHEDULE_APPOINTMENT: (id) => `/api/v1/appointments/${id}/reschedule`,
    SERVICES: '/api/v1/admin/spa/services',
    SERVICE_DETAIL: (id) => `/api/v1/admin/spa/services/${id}`,
    CATEGORIES: '/api/v1/admin/spa/categories',
    CATEGORY_DETAIL: (id) => `/api/v1/admin/spa/categories/${id}`,
    PACKAGES: '/api/v1/admin/spa/packages',
    PACKAGE_DETAIL: (id) => `/api/v1/admin/spa/packages/${id}`,
    STAFF: '/api/v1/admin/staff',
    STAFF_DETAIL: (id) => `/api/v1/admin/staff/${id}`,
    STAFF_SKILLS: (id) => `/api/v1/admin/staff/${id}/skills`,
    STAFF_SKILL_DELETE: (id, serviceId) => `/api/v1/admin/staff/${id}/skills/${serviceId}`,
    STAFF_SCHEDULES: (id) => `/api/v1/admin/staff/${id}/schedules`,
    STAFF_SCHEDULE_DETAIL: (id, scheduleId) => `/api/v1/admin/staff/${id}/schedules/${scheduleId}`,
    TICKETS: '/api/v1/admin/spa/tickets',
    TICKET_DETAIL: (id) => `/api/v1/admin/spa/tickets/${id}`,
    TICKET_EXTEND: (id) => `/api/v1/admin/spa/tickets/${id}/extend`,
    TICKET_COMPENSATE: (id) => `/api/v1/admin/spa/tickets/${id}/compensate`,
    APPOINTMENTS: '/api/v1/appointments/admin/all',
    UPDATE_APPOINTMENT_STATUS: (id) => `/api/v1/appointments/admin/${id}/status`,
    CHECK_IN_APPOINTMENT: (id) => `/api/v1/appointments/${id}/check-in`,
    APPOINTMENT_INVOICE: (id) => `/api/v1/appointments/${id}/invoice`,
    APPOINTMENT_CASH_RECEIPT: (id) => `/api/v1/appointments/${id}/invoice/cash-receipts`,
    FACILITIES: '/api/v1/admin/spa/facilities',
    FACILITY_DETAIL: (id) => `/api/v1/admin/spa/facilities/${id}`,
    FACILITY_BLOCKS: (id) => `/api/v1/admin/spa/facilities/${id}/blocks`,
    FACILITY_BLOCK_DETAIL: (id, blockId) => `/api/v1/admin/spa/facilities/${id}/blocks/${blockId}`,
    SERVICE_RESOURCES: (id) => `/api/v1/admin/spa/services/${id}/resource-requirements`,
    SERVICE_POLICY: (id) => `/api/v1/admin/spa/preparation/services/${id}`,
    PREPARATION_TEMPLATES: '/api/v1/admin/spa/preparation/templates',
    PREPARATION_TEMPLATE_VERSIONS: (id) => `/api/v1/admin/spa/preparation/templates/${id}/versions`,
  },

  // AI Chatbot Consultant
  CHATBOT: {
    CHAT: '/api/v1/chatbot/chat',
    HEALTH: '/api/v1/chatbot/health',
    SYNC_DATABASE: '/api/v1/chatbot/sync/database',
    TEST_UNDERSTAND: '/api/v1/chatbot/test/understand',
  },

  // Reviews Moderation
  REVIEWS: {
    LIST: '/api/v1/admin/reviews',
    DETAIL: (id) => `/api/v1/admin/reviews/${id}`,
    MODERATE: (id) => `/api/v1/admin/reviews/${id}/status`,
    REPLY: (id) => `/api/v1/admin/reviews/${id}/reply`,
    DELETE: (id) => `/api/v1/admin/reviews/${id}`,
  },

  // Roles & RBAC System Permissions
  ROLES: {
    LIST: '/api/v1/roles',
    DETAIL: (id) => `/api/v1/roles/${id}`,
  },

  // Product Attribute Definitions
  ATTRIBUTES: {
    LIST: '/api/v1/attributes',
    DETAIL: (id) => `/api/v1/attributes/${id}`,
    VALUES: (id) => `/api/v1/attributes/${id}/values`,
    DELETE_VALUE: (valueId) => `/api/v1/attributes/values/${valueId}`,
  },

  // System Infrastructure Health & Telemetry
  SYSTEM: {
    HEALTH: '/actuator/health',
    ALERTS: '/api/v1/admin/operational-alerts',
    AUDIT_LOGS: '/api/v1/admin/audit-logs',
    AUDIT_DETAIL: (id) => `/api/v1/admin/audit-logs/${id}`,
    PAYMENTS: '/api/v1/admin/payments',
    PAYMENT_DETAIL: (id) => `/api/v1/admin/payments/${id}`,
    PAYMENT_SUMMARY: '/api/v1/admin/payments/summary',
    CONFIGS: '/api/v1/admin/system-configs',
    CONFIG: (key) => `/api/v1/admin/system-configs/${encodeURIComponent(key)}`,
    CREATE_CONFIG: '/api/v1/admin/system-configs',
    DELETE_CONFIG: (key) => `/api/v1/admin/system-configs/${encodeURIComponent(key)}`,
  },

  PROCUREMENT: {
    SUPPLIERS: '/api/v1/admin/procurement/suppliers',
    SUPPLIER: (id) => `/api/v1/admin/procurement/suppliers/${id}`,
    DELETE_SUPPLIER: (id) => `/api/v1/admin/procurement/suppliers/${id}`,
    ORDERS: '/api/v1/admin/procurement/orders',
    ORDER_DETAIL: (id) => `/api/v1/admin/procurement/orders/${id}`,
    APPROVE: (id) => `/api/v1/admin/procurement/orders/${id}/approve`,
    RECEIVE: (id) => `/api/v1/admin/procurement/orders/${id}/receive`,
    CANCEL: (id) => `/api/v1/admin/procurement/orders/${id}/cancel`,
  },

  CRM: {
    CUSTOMERS: '/api/v1/admin/crm/customers',
    CUSTOMER_DETAIL: (id) => `/api/v1/admin/crm/customers/${id}`,
    NOTES: (id) => `/api/v1/admin/crm/customers/${id}/notes`,
    DELETE_NOTE: (noteId) => `/api/v1/admin/crm/notes/${noteId}`,
  },
};
