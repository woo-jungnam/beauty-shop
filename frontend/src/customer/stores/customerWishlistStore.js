import { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';

let identity = null;
let ids = new Set();
let pending = null;
const listeners = new Set();
const emit = () => { listeners.forEach(listener => listener()); window.dispatchEvent(new Event('wishlist:changed')); };
function readIdentity() { try { const value = JSON.parse(localStorage.getItem('beautyshop_admin_user') || 'null'); return localStorage.getItem('beautyshop_admin_token') ? String(value?.id || value?.username || '') : ''; } catch { return ''; } }
async function load() {
  const next = readIdentity();
  if (next !== identity) { identity = next; ids = new Set(); pending = null; }
  if (!next) return;
  if (!pending) {
    const forIdentity = next;
    pending = apiClient.get('/api/v1/users/wishlist').then(response => { if (identity !== forIdentity) return; const data = response?.data ?? response; ids = new Set((Array.isArray(data) ? data : data?.content || []).map(row => String(row.id))); listeners.forEach(listener => listener()); }).catch(() => { pending = null; });
  }
  await pending;
}
window.addEventListener('auth:logout', () => { identity = null; ids = new Set(); pending = null; listeners.forEach(listener => listener()); });
export function useCustomerWishlist(productId) {
  const [, render] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const currentIdentity = readIdentity();
  useEffect(() => { const listener = () => render(value => value + 1); listeners.add(listener); load(); return () => listeners.delete(listener); }, [productId, currentIdentity]);
  const active = ids.has(String(productId));
  const toggle = async () => {
    if (!readIdentity()) { window.location.hash = '/login'; return; }
    if (busy) return;
    setBusy(true); setError(''); const forIdentity = readIdentity();
    try {
      await load();
      const exists = ids.has(String(productId));
      if (exists) await apiClient.delete(`/api/v1/users/wishlist/${productId}`); else await apiClient.put(`/api/v1/users/wishlist/${productId}`);
      if (forIdentity === readIdentity()) { if (exists) ids.delete(String(productId)); else ids.add(String(productId)); emit(); }
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  return { active, toggle, busy, error };
}
