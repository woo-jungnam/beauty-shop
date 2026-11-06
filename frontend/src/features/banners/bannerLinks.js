export function bannerHref(value) {
  const target = String(value || '').trim();
  if (!target || target === '#' || target.includes('\\') || [...target].some(char => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127) || target.startsWith('//')) return null;
  if (/^https?:\/\//i.test(target)) {
    try { return new URL(target).href; } catch { return null; }
  }
  if (/^[a-z][a-z\d+.-]*:/i.test(target)) return null;
  if (target.startsWith('/#/')) return target.slice(1);
  if (target.startsWith('#')) return target;
  return `#/${target.replace(/^\/+/, '')}`;
}
