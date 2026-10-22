export const serverBaseUrl = (value = '') => value.trim().replace(/\/+$/, '').replace(/\/api(?:\/v1)?$/, '');
