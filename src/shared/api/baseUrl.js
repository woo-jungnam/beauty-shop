// Endpoints already include /api/v1; media and WebSocket routes use the server root.
export const serverBaseUrl = (value = '') => value.trim().replace(/\/+$/, '').replace(/\/api(?:\/v1)?$/, '');
