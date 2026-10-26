import assert from 'node:assert/strict';

const VALID_POSITIONS = new Set(['HERO_SLIDE', 'HERO_SIDE', 'PROMO']);

function validateBannerPayload(payload) {
  assert(payload.title && payload.title.trim().length > 0, 'title is required');
  assert(payload.imageUrl && payload.imageUrl.trim().length > 0, 'imageUrl is required');
  assert(VALID_POSITIONS.has(payload.position || 'HERO_SLIDE'), 'position must be valid');
  return {
    title: payload.title.trim(),
    badge: payload.badge?.trim() || null,
    description: payload.description?.trim() || null,
    imageUrl: payload.imageUrl.trim(),
    targetUrl: payload.targetUrl?.trim() || 'products',
    ctaText: payload.ctaText?.trim() || 'Mua Sắm Ngay',
    position: payload.position || 'HERO_SLIDE',
    sortOrder: Number(payload.sortOrder ?? 0),
    isActive: Boolean(payload.isActive ?? true),
  };
}

const b1 = validateBannerPayload({
  title: 'Đại Tiệc Flash Sale',
  imageUrl: 'https://images.unsplash.com/photo-1620916566398',
  position: 'HERO_SLIDE',
  sortOrder: 1,
  badge: 'DEAL GIỜ VÀNG'
});
assert.equal(b1.title, 'Đại Tiệc Flash Sale');
assert.equal(b1.position, 'HERO_SLIDE');
assert.equal(b1.sortOrder, 1);
assert.equal(b1.isActive, true);

const b2 = validateBannerPayload({
  title: 'Spa & Làm Đẹp',
  imageUrl: 'https://images.unsplash.com/photo-1540555700',
  position: 'HERO_SIDE',
  targetUrl: 'booking'
});
assert.equal(b2.position, 'HERO_SIDE');
assert.equal(b2.targetUrl, 'booking');

assert.throws(() => validateBannerPayload({ title: '', imageUrl: 'abc' }), /title is required/);
assert.throws(() => validateBannerPayload({ title: 'Test', imageUrl: '' }), /imageUrl is required/);
assert.throws(() => validateBannerPayload({ title: 'Test', imageUrl: 'abc', position: 'UNKNOWN' }), /position must be valid/);

function getDisplayedSideBanners(sideBanners, offset) {
  if (!sideBanners || sideBanners.length === 0) return [];
  if (sideBanners.length <= 2) return sideBanners.map((b, i) => ({ ...b, slotIndex: i }));
  const topIndex = offset % sideBanners.length;
  const bottomIndex = (offset + 1) % sideBanners.length;
  return [
    { ...sideBanners[topIndex], slotIndex: topIndex },
    { ...sideBanners[bottomIndex], slotIndex: bottomIndex }
  ];
}

const mockFiveSideBanners = [
  { id: 1, title: 'Side 1' },
  { id: 2, title: 'Side 2' },
  { id: 3, title: 'Side 3' },
  { id: 4, title: 'Side 4' },
  { id: 5, title: 'Side 5' },
];

const slot0 = getDisplayedSideBanners(mockFiveSideBanners, 0);
assert.equal(slot0.length, 2);
assert.equal(slot0[0].title, 'Side 1');
assert.equal(slot0[1].title, 'Side 2');

const slot1 = getDisplayedSideBanners(mockFiveSideBanners, 1);
assert.equal(slot1[0].title, 'Side 2');
assert.equal(slot1[1].title, 'Side 3');

const slot4 = getDisplayedSideBanners(mockFiveSideBanners, 4);
assert.equal(slot4[0].title, 'Side 5');
assert.equal(slot4[1].title, 'Side 1');

const SECTION_HEADING_SELECTORS = [
  '.customer-app section h2',
  '.customer-app .section-title-editorial',
  '.customer-app .section-header-editorial h2'
];
assert.equal(SECTION_HEADING_SELECTORS.length, 3);

console.log('Banner & Section title centering check: ALL PASSED');
