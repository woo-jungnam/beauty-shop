import { test, expect } from '@playwright/test';

const customer = { id: 7, username: 'detail-test', fullName: 'Khách hàng kiểm thử', roles: ['ROLE_CUSTOMER'] };
const artwork = '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="800" viewBox="0 0 640 800"><rect width="640" height="800" fill="#faf7f8"/><rect x="200" y="130" width="240" height="540" rx="30" fill="#f4d8df"/><text x="320" y="390" text-anchor="middle" font-size="32" fill="#9a4363">B5 SERUM</text></svg>';
const baseProduct = {
  id: 42, name: 'Serum phục hồi B5', slug: 'serum-phuc-hoi-b5', status: 'ACTIVE', productType: 'PRODUCT',
  shortDescription: 'Thông tin sản phẩm từ danh mục công khai.', description: 'Mô tả sản phẩm đã được công bố.',
  thumbnailUrl: '/media/detail-front.svg', minPrice: 250000, maxPrice: 400000, basePrice: 250000,
  averageRating: 2.5, totalReviews: 2, totalSold: 20,
  brand: { id: 5, name: 'Beauty Lab', slug: 'beauty-lab', logoUrl: null },
  categories: [{ id: 4, name: 'Serum', slug: 'serum' }], tags: [{ id: 1, name: 'Dưỡng da', slug: 'duong-da' }],
  volume: '50 ml', originCountry: 'Việt Nam', hasFragrance: null, hasAlcohol: false,
  ingredients: 'Aqua, Niacinamide, Panthenol, Glycerin', keyActivesSummary: 'Niacinamide và Panthenol',
  howToUse: 'Dùng theo hướng dẫn trên nhãn sản phẩm.',
  variants: [
    { id: 99, sku: 'B5-50', variantName: '50 ml', price: 280000, originalPrice: 300000, discountPrice: 250000, currency: 'VND', volume: '50 ml', volumeValue: 50, volumeUnit: 'ml', isDefault: true, isActive: true, stockQuantity: 10 },
    { id: 100, sku: 'B5-100', variantName: '100 ml', price: 400000, originalPrice: 400000, discountPrice: null, currency: 'VND', volume: '100 ml', volumeValue: 100, volumeUnit: 'ml', isDefault: false, isActive: true, stockQuantity: 3 },
  ],
  images: [
    { id: 1, imageUrl: '/media/detail-front.svg', altText: 'Mặt trước chai serum B5', imageType: 'PRODUCT_FRONT', displayOrder: 1, isPrimary: true },
    { id: 2, imageUrl: '/media/detail-back.svg', altText: 'Nhãn sau chai serum B5', imageType: 'PRODUCT_BACK', displayOrder: 2, isPrimary: false },
    { id: 3, imageUrl: '/media/detail-texture.svg', altText: 'Kết cấu sản phẩm B5', imageType: 'TEXTURE', displayOrder: 3, isPrimary: false },
  ],
  ingredientsList: [
    { ingredientId: 11, name: 'Niacinamide', inciName: 'Niacinamide', concentration: 5, concentrationUnit: '%', isKeyActive: true, function: ['skin conditioning'], benefits: ['Thông tin lợi ích đã lưu'], potentialConcerns: ['Lưu ý theo nhãn sản phẩm'] },
    { ingredientId: 12, name: 'Panthenol', inciName: 'Panthenol', concentration: null, concentrationUnit: null, isKeyActive: true, function: ['humectant'], benefits: [], potentialConcerns: [] },
  ],
  ingredientSummary: { keyActives: ['Niacinamide', 'Panthenol'], hydratingIngredients: ['Panthenol'], exfoliatingIngredients: [], fragrance: false, alcohol: false },
  skinCompatibility: { recommendedSkinTypes: [{ skinTypeId: 2, code: 'DRY', name: 'Da khô', score: 90 }], notIdealFor: [{ skinTypeId: 3, skinType: 'Da nhạy cảm', reason: 'Kiểm tra phản ứng theo hướng dẫn trên nhãn' }] },
  skinConcerns: [{ concernId: 4, code: 'DEHYDRATION', name: 'Da thiếu ẩm', score: 80, notes: 'Thông tin công khai từ danh mục' }],
  usage: { whenToUse: ['Buổi sáng', 'Buổi tối'], frequency: 'Theo hướng dẫn trên nhãn', instructions: ['Rửa sạch da.', 'Thoa lượng vừa đủ.'], warnings: ['Tránh tiếp xúc với mắt.'] },
  attributeValues: [
    { id: 1, attributeDefinitionName: 'Quy cách đóng gói', value: 'Chai thủy tinh', dataType: 'TEXT', productVariantId: null },
    { id: 2, attributeDefinitionName: 'Mã quy cách', value: 'B5-NHO', dataType: 'TEXT', productVariantId: 99 },
    { id: 3, attributeDefinitionName: 'Mã quy cách', value: 'B5-LON', dataType: 'TEXT', productVariantId: 100 },
  ],
};

const publicReviews = (count) => Array.from({ length: count }, (_, index) => ({
  id: index + 1, productId: 42, userId: index + 20, orderId: index < 2 ? 77 + index : null,
  rating: index < 2 ? 2 + index : null, isVerifiedPurchase: index < 2,
  title: `Nhận xét công khai ${index + 1}`, content: `Nội dung bình luận ${index + 1}`,
  status: 'APPROVED', createdAt: '2026-10-01T08:00:00Z', adminReply: null,
}));

const paged = (content, totalElements = content.length, page = 0, size = 10) => ({
  content, totalElements, page, size, totalPages: Math.ceil(totalElements / size), last: (page + 1) * size >= totalElements,
});
const emptyCart = () => ({ id: 1, sessionId: 'detail-guest', userId: null, items: [], totalPrice: 0 });
const clone = (value) => JSON.parse(JSON.stringify(value));
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};

async function mockDetail(page, options = {}) {
  const product = clone(options.product || baseProduct);
  const gate = deferred();
  const api = {
    product, cart: clone(options.cart || emptyCart()), cartRequests: [], reviewRequests: [], submittedReviews: [], tracking: [], pageErrors: [],
    detailStatus: options.detailStatus || 200, cartFailure: options.cartFailure || false,
    reviewFailurePage: options.reviewFailurePage ?? null, auxiliaryFailure: options.auxiliaryFailure || false,
    releaseCart: gate.resolve, delayedCart: options.delayedCart || false,
  };
  page.on('pageerror', (error) => api.pageErrors.push(error.message));
  if (options.authenticated) {
    await page.addInitScript((user) => {
      localStorage.setItem('beautyshop_admin_token', 'product-detail-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
    }, customer);
  }
  await page.route('**/fonts.googleapis.com/**', (route) => route.abort());
  await page.route('**/fonts.gstatic.com/**', (route) => route.abort());
  await page.route('**/media/detail-*.svg', async (route) => {
    if (options.brokenImages) return route.fulfill({ status: 404, body: '' });
    return route.fulfill({ contentType: 'image/svg+xml', body: artwork });
  });
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    let data = [];
    if (path === '/api/v1/auth/profile') data = customer;
    else if (path === '/api/v1/cart') {
      if (options.emptyCartEnvelope) return route.fulfill({ json: { status: 200, message: 'Success', timestamp: '2026-10-13T08:30:00', path: '/api/v1/cart' } });
      data = api.cart;
    }
    else if (path === '/api/v1/cart/add') {
      const payload = request.postDataJSON();
      api.cartRequests.push(payload);
      if (api.delayedCart) await gate.promise;
      if (api.cartFailure) return route.fulfill({ status: options.cartFailureStatus || 409, json: { success: false, message: 'Không đủ hàng trong kho cho sản phẩm này' } });
      if (options.missingCartResponse) return route.fulfill({ json: { status: 200, message: 'Success', timestamp: '2026-10-13T08:30:00', path: '/api/v1/cart/add' } });
      if (options.invalidCartResponse) return route.fulfill({ json: { success: true, data: { items: [] } } });
      const variant = product.variants.find((item) => Number(item.id) === Number(payload.variantId));
      const existing = api.cart.items.find((item) => Number(item.variantId) === Number(payload.variantId));
      if (existing) existing.quantity += payload.quantity;
      else api.cart.items.push({ id: 10 + api.cart.items.length, variantId: variant.id, sku: variant.sku, variantName: product.name, quantity: payload.quantity, price: variant.discountPrice ?? variant.price, imageUrl: product.thumbnailUrl, available: true });
      api.cart.totalPrice = api.cart.items.reduce((sum, item) => sum + item.quantity * item.price, 0);
      data = api.cart;
    } else if (path === '/api/v1/products/42') {
      if (options.detailGate) await options.detailGate.promise;
      if (api.detailStatus !== 200) return route.fulfill({ status: api.detailStatus, json: { success: false, message: api.detailStatus === 404 ? 'Không tìm thấy sản phẩm' : 'Service unavailable' } });
      data = product;
    } else if (path === '/api/v1/products/43') data = { ...clone(baseProduct), id: 43, name: 'Kem dưỡng Ceramide', slug: 'kem-duong-ceramide' };
    else if (/^\/api\/v1\/products\/\d+\/similar$/.test(path)) {
      if (options.auxiliaryGate) await options.auxiliaryGate.promise;
      if (api.auxiliaryFailure) return route.fulfill({ status: 503, json: { message: 'Recommendation unavailable' } });
      data = [];
    } else if (path === '/api/v1/products/tracking/interaction') {
      api.tracking.push(request.postDataJSON());
      data = {};
    } else if (path === '/api/v1/reviews' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      api.submittedReviews.push(payload);
      if (options.reviewSubmissionFailure) return route.fulfill({ status: 409, json: { message: 'Đơn hàng này đã được đánh giá trước đó' } });
      return route.fulfill({ status: 201, json: { success: true, data: { id: 400, ...payload, status: 'PENDING' }, message: 'Review submitted for moderation' } });
    } else if (path === '/api/v1/reviews') {
      if (options.auxiliaryGate) await options.auxiliaryGate.promise;
      const pageNumber = Number(url.searchParams.get('page') || 0);
      const size = Number(url.searchParams.get('size') || 10);
      api.reviewRequests.push(pageNumber);
      if (api.auxiliaryFailure || api.reviewFailurePage === pageNumber) return route.fulfill({ status: 503, json: { message: 'Reviews unavailable' } });
      const reviews = options.reviews || publicReviews(3);
      data = paged(reviews.slice(pageNumber * size, (pageNumber + 1) * size), reviews.length, pageNumber, size);
    } else if (path === '/api/v1/orders/my-orders') {
      if (options.auxiliaryGate) await options.auxiliaryGate.promise;
      if (api.auxiliaryFailure || options.eligibilityFailure) return route.fulfill({ status: 503, json: { message: 'Orders unavailable' } });
      const orders = options.orders || [{ id: 77, orderNumber: 'ORD-77', status: 'DELIVERED', items: [{ variantId: 99 }] }];
      const pageNumber = Number(url.searchParams.get('page') || 0);
      const size = Number(url.searchParams.get('size') || 100);
      data = paged(orders.slice(pageNumber * size, (pageNumber + 1) * size), orders.length, pageNumber, size);
    } else if (path === '/api/v1/products/search' || path === '/api/v1/products') data = paged([product]);
    else if (path === '/api/v1/brands') data = paged([product.brand]);
    else if (path === '/api/v1/categories' || path === '/api/v1/categories/root') data = product.categories;
    else if (path === '/api/v1/chatbot/health') data = { status: 'ok' };
    return route.fulfill({ json: { success: true, data } });
  });
  return api;
}

const detail = (page) => page.locator('.product-detail-page.pd-page');
const buyActions = (page) => detail(page).locator('.pd-purchase-actions');
const addButton = (page) => buyActions(page).getByRole('button', { name: 'Thêm vào giỏ hàng', exact: true });
const buyButton = (page) => buyActions(page).getByRole('button', { name: 'Mua ngay', exact: true });
async function openDetail(page) {
  await page.goto('/#/product/42');
  await expect(detail(page).getByRole('heading', { level: 1, name: 'Serum phục hồi B5' })).toBeVisible();
}

test('GET giỏ trống đúng envelope không có data xóa cache cũ, không báo lỗi và vẫn thêm được hàng', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('beautyshop_customer_cart', JSON.stringify({
      cartId: 1, voucher: null,
      items: [{ itemId: '5', backendId: 5, variantId: 100, sku: 'B5-100', name: 'Mặt hàng cũ trong cache', variantName: '100 ml', quantity: 1, price: 400000, available: true }],
    }));
  });
  const api = await mockDetail(page, { emptyCartEnvelope: true });
  const fetched = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/cart');
  await page.goto('/');
  expect(await (await fetched).json()).toEqual({ status: 200, message: 'Success', timestamp: '2026-10-13T08:30:00', path: '/api/v1/cart' });
  await expect(page.locator('.product-card-resolved').first()).toBeVisible();
  await expect(page.locator('.customer-cart-error')).toHaveCount(0);
  await page.getByRole('button', { name: 'Giỏ hàng', exact: true }).click();
  const drawer = page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ });
  await expect(drawer).toContainText('Giỏ hàng của bạn đang trống');
  await expect(drawer.getByText('Mặt hàng cũ trong cache', { exact: true })).toHaveCount(0);
  expect((await page.evaluate(() => JSON.parse(localStorage.getItem('beautyshop_customer_cart')))).items).toEqual([]);
  await page.keyboard.press('Escape');
  await page.locator('.product-card-resolved').first().getByRole('button', { name: `Thêm ${baseProduct.name} vào giỏ`, exact: true }).click();
  await expect(drawer).toBeVisible();
  await expect(drawer).toContainText(baseProduct.name);
  await expect(drawer.getByText('Giỏ hàng của bạn đang trống', { exact: true })).toHaveCount(0);
  await expect(page.locator('.customer-cart-error')).toHaveCount(0);
  expect(api.cartRequests).toHaveLength(1);
  expect(api.pageErrors).toEqual([]);
});

test('POST thêm giỏ không có data vẫn bị từ chối dù envelope HTTP 200 báo Success', async ({ page }) => {
  const api = await mockDetail(page, { missingCartResponse: true });
  await openDetail(page);
  await buyButton(page).click();
  await expect(detail(page).getByRole('alert')).toContainText('Máy chủ chưa xác nhận');
  await expect(page).toHaveURL(/#\/product\/42$/);
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  expect((await page.evaluate(() => JSON.parse(localStorage.getItem('beautyshop_customer_cart')))).items).toEqual([]);
  expect(api.cartRequests).toHaveLength(1);
  expect(api.tracking.filter((event) => event.actionType === 'ADD_TO_CART')).toEqual([]);
  expect(api.pageErrors).toEqual([]);
});

test('ProductCard thêm giỏ thất bại không hiện trạng thái đã thêm hoặc mở drawer', async ({ page }) => {
  const api = await mockDetail(page, { cartFailure: true, cartFailureStatus: 503 });
  await page.goto('/#/products');
  const card = page.locator('.product-card-resolved').filter({ has: page.getByRole('link', { name: baseProduct.name, exact: true }) });
  await expect(card, `Page errors: ${api.pageErrors.join('; ')}`).toBeVisible();
  await card.getByRole('button', { name: `Thêm ${baseProduct.name} vào giỏ`, exact: true }).click();
  await expect(page.locator('.customer-cart-error')).toContainText('Không đủ hàng');
  await expect(card.getByText(/đã thêm/i)).toHaveCount(0);
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  expect(api.cartRequests).toHaveLength(1);
});

test('ProductCard and QuickView use neutral placeholders when actual product images fail', async ({ page }) => {
  const api = await mockDetail(page, { brokenImages: true });
  await page.goto('/');
  const card = page.locator('.product-card-resolved').first();
  await expect(card, `Page errors: ${api.pageErrors.join('; ')}`).toBeVisible();
  await expect(card.locator('img')).toHaveAttribute('src', /^data:image\/svg\+xml,/);
  await expect(card.locator('img')).not.toHaveAttribute('src', /unsplash|picsum/);
  await card.getByTitle('Xem nhanh chi tiết').click();
  const quickView = page.getByRole('dialog', { name: baseProduct.name, exact: true });
  await expect(quickView).toBeVisible();
  await expect(quickView.locator('img')).toHaveAttribute('src', /^data:image\/svg\+xml,/);
  await expect(quickView.locator('img')).not.toHaveAttribute('src', /unsplash|picsum/);
  expect(api.pageErrors).toEqual([]);
});

test('QuickView giữ dialog khi thêm giỏ thất bại và hiển thị lỗi dùng chung', async ({ page }) => {
  const api = await mockDetail(page, { cartFailure: true, cartFailureStatus: 503 });
  await page.goto('/');
  await page.locator('.product-card-resolved').getByTitle('Xem nhanh chi tiết').first().click();
  const quickView = page.getByRole('dialog', { name: baseProduct.name, exact: true });
  await expect(quickView).toBeVisible();
  await quickView.getByRole('button', { name: /Thêm Vào Giỏ Hàng/ }).click();
  await expect(page.locator('.customer-cart-error')).toContainText('Không đủ hàng');
  await expect(quickView).toBeVisible();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  expect(api.cartRequests[0].variantId).toBe(99);
  expect(api.pageErrors).toEqual([]);
});

test('QuickView chỉ đóng và mở giỏ sau khi response xác nhận thành công', async ({ page }) => {
  const api = await mockDetail(page, { delayedCart: true });
  await page.goto('/');
  await page.locator('.product-card-resolved').getByTitle('Xem nhanh chi tiết').first().click();
  const quickView = page.getByRole('dialog', { name: baseProduct.name, exact: true });
  await quickView.getByRole('button', { name: /Thêm Vào Giỏ Hàng/ }).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  await expect(quickView).toBeVisible();
  await expect(quickView.getByRole('button', { name: 'Đang thêm vào giỏ…', exact: true })).toBeDisabled();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  api.releaseCart();
  await expect(quickView).toHaveCount(0);
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toBeVisible();
  expect(api.pageErrors).toEqual([]);
});

test('Thêm giỏ chờ máy chủ xác nhận và chống thao tác lặp khi request chậm', async ({ page }) => {
  const api = await mockDetail(page, { delayedCart: true });
  await openDetail(page);
  await addButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  await expect(addButton(page)).toBeDisabled();
  await expect(buyButton(page)).toBeDisabled();
  await addButton(page).evaluate((element) => element.click());
  expect(api.cartRequests).toHaveLength(1);
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  expect(api.tracking.filter((event) => event.actionType === 'ADD_TO_CART')).toHaveLength(0);
  api.releaseCart();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toBeVisible();
  // Successful cart mutations are tracked once by the backend after commit.
  expect(api.tracking.filter((event) => event.actionType === 'ADD_TO_CART')).toHaveLength(0);
  expect(api.cartRequests[0]).toMatchObject({ variantId: 99, quantity: 1 });
  expect(api.pageErrors).toEqual([]);
});

test('Máy chủ từ chối thêm giỏ giữ nguyên giỏ đã xác nhận và không báo thành công', async ({ page }) => {
  const cart = { ...emptyCart(), items: [{ id: 5, variantId: 100, sku: 'B5-100', variantName: 'Mặt hàng đã có', quantity: 1, price: 400000, imageUrl: '', available: true }], totalPrice: 400000 };
  const api = await mockDetail(page, { cart, cartFailure: true });
  await openDetail(page);
  await addButton(page).click();
  await expect(detail(page).getByRole('alert')).toContainText('Không đủ hàng');
  const headerBounds = await page.locator('.customer-header').boundingBox();
  const errorBounds = await page.locator('.customer-cart-error').boundingBox();
  expect(errorBounds.y).toBeGreaterThanOrEqual(headerBounds.y + headerBounds.height);
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('beautyshop_customer_cart')));
  expect(saved.items).toHaveLength(1);
  expect(saved.items[0].variantId).toBe(100);
  expect(api.tracking.filter((event) => event.actionType === 'ADD_TO_CART')).toEqual([]);
  expect(api.pageErrors).toEqual([]);
});

test('Response thêm giỏ không hợp lệ không tạo hàng local hoặc chuyển checkout', async ({ page }) => {
  const api = await mockDetail(page, { invalidCartResponse: true });
  await openDetail(page);
  await buyButton(page).click();
  await expect(detail(page).getByRole('alert')).toContainText('Máy chủ chưa xác nhận');
  await expect(page).toHaveURL(/#\/product\/42$/);
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
  expect(api.cartRequests).toHaveLength(1);
});

test('Mua ngay chỉ tới checkout sau khi thêm giỏ thành công và đóng drawer', async ({ page }) => {
  const api = await mockDetail(page, { delayedCart: true });
  await openDetail(page);
  await buyButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  await expect(page).toHaveURL(/#\/product\/42$/);
  api.releaseCart();
  await expect(page).toHaveURL(/#\/checkout$/);
  await expect(page.getByRole('heading', { name: 'Thanh toán đơn hàng' })).toBeVisible();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toHaveCount(0);
});

test('Mua ngay thất bại giữ trang và lựa chọn quy cách', async ({ page }) => {
  await mockDetail(page, { cartFailure: true });
  await openDetail(page);
  const variant = detail(page).getByRole('button', { name: '100 ml', exact: true });
  await variant.click();
  await buyButton(page).click();
  await expect(detail(page).getByRole('alert')).toContainText('Không đủ hàng');
  await expect(page).toHaveURL(/#\/product\/42$/);
  await expect(variant).toHaveAttribute('aria-pressed', 'true');
});

for (const scenario of [
  { title: 'không có SKU', change: (product) => { product.variants = []; } },
  { title: 'chỉ có SKU đã ngừng bán', change: (product) => { product.variants.forEach((variant) => { variant.isActive = false; }); } },
  { title: 'SKU thiếu mã', change: (product) => { product.variants = [{ ...product.variants[0], sku: '' }]; } },
  { title: 'SKU hết hàng', change: (product) => { product.variants = [{ ...product.variants[0], stockQuantity: 0 }]; } },
]) {
  test(`Không cho mua sản phẩm ${scenario.title}`, async ({ page }) => {
    const product = clone(baseProduct);
    scenario.change(product);
    const api = await mockDetail(page, { product });
    await openDetail(page);
    await expect(addButton(page)).toBeDisabled();
    await expect(buyButton(page)).toBeDisabled();
    await addButton(page).evaluate((element) => element.click());
    expect(api.cartRequests).toEqual([]);
  });
}

test('Chọn SKU active thực thay vì biến thể mặc định đã dừng bán', async ({ page }) => {
  const product = clone(baseProduct);
  product.variants[0].isActive = false;
  const api = await mockDetail(page, { product });
  await openDetail(page);
  await expect(detail(page).getByRole('button', { name: '50 ml', exact: true })).toHaveCount(0);
  await expect(detail(page).getByRole('button', { name: '100 ml', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await addButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  expect(api.cartRequests[0].variantId).toBe(100);
});

test('Các SKU cùng dung tích có nhãn phân biệt và thêm đúng quy cách đã chọn', async ({ page }) => {
  const product = clone(baseProduct);
  product.variants[0].color = 'Trong suốt';
  Object.assign(product.variants[1], { volume: '50 ml', volumeValue: 50, color: 'Hồng' });
  const api = await mockDetail(page, { product });
  await openDetail(page);
  const choices = detail(page).getByRole('group', { name: 'Phân loại sản phẩm', exact: true });
  await expect(choices.getByRole('button', { name: '50 ml · Trong suốt · B5-50', exact: true })).toBeVisible();
  const second = choices.getByRole('button', { name: '50 ml · Hồng · B5-100', exact: true });
  await second.click();
  await expect(second).toHaveAttribute('aria-pressed', 'true');
  await addButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  expect(api.cartRequests[0].variantId).toBe(100);
});

test('Giữ nguyên SKU và số lượng đang xác nhận, mở lại lựa chọn khi thao tác hoàn tất', async ({ page }) => {
  const api = await mockDetail(page, { delayedCart: true });
  await openDetail(page);
  await addButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  const selected = detail(page).getByRole('button', { name: '50 ml', exact: true });
  const other = detail(page).getByRole('button', { name: '100 ml', exact: true });
  await expect(selected).toBeDisabled();
  await expect(other).toBeDisabled();
  await expect(detail(page).getByLabel('Số lượng', { exact: true })).toBeDisabled();
  await other.evaluate((element) => element.click());
  await expect(selected).toHaveAttribute('aria-pressed', 'true');
  api.releaseCart();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(other).toBeEnabled();
  await other.click();
  await expect(other).toHaveAttribute('aria-pressed', 'true');
  expect(api.cartRequests).toHaveLength(1);
});

test('Tồn kho null được ghi chưa xác minh, không khẳng định còn hàng', async ({ page }) => {
  const product = clone(baseProduct);
  product.variants = [{ ...product.variants[0], stockQuantity: null }];
  await mockDetail(page, { product });
  await openDetail(page);
  await expect(detail(page)).toContainText(/chưa xác minh/i);
  await expect(detail(page).getByText(/còn hàng/i)).toHaveCount(0);
});

test('Giá ưu đãi bằng 0 không bị thay bằng giá gốc và gửi đúng SKU', async ({ page }) => {
  const product = clone(baseProduct);
  product.variants = [{ ...product.variants[0], discountPrice: 0 }];
  const api = await mockDetail(page, { product });
  await openDetail(page);
  await expect(detail(page).getByText(/^0\s*[₫đ]$/).first()).toBeVisible();
  await addButton(page).click();
  await expect(page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ })).toBeVisible();
  expect(api.cart.items[0].price).toBe(0);
});

test('Đổi biến thể cập nhật quy cách, giá và giới hạn số lượng', async ({ page }) => {
  const api = await mockDetail(page);
  await openDetail(page);
  await expect(detail(page)).toContainText('B5-NHO');
  await expect(detail(page).getByText('B5-LON', { exact: true })).toHaveCount(0);
  await detail(page).getByRole('button', { name: '100 ml', exact: true }).click();
  await expect(detail(page)).toContainText('B5-LON');
  await expect(detail(page).getByText('B5-NHO', { exact: true })).toHaveCount(0);
  const increase = detail(page).getByRole('button', { name: 'Tăng số lượng', exact: true });
  await increase.click();
  await increase.click();
  await expect(increase).toBeDisabled();
  await addButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  expect(api.cartRequests[0]).toMatchObject({ variantId: 100, quantity: 3 });
});

test('Số lượng rỗng, bằng 0, phân số hoặc vượt tồn không được gửi vào giỏ', async ({ page }) => {
  const api = await mockDetail(page);
  await openDetail(page);
  const quantity = detail(page).getByLabel('Số lượng', { exact: true });
  for (const invalid of ['', '0', '1.5', '11']) {
    await quantity.fill(invalid);
    await expect(quantity).toHaveAttribute('aria-invalid', 'true');
    await expect(addButton(page)).toBeDisabled();
    await expect(buyButton(page)).toBeDisabled();
  }
  expect(api.cartRequests).toEqual([]);
  await quantity.fill('2');
  await expect(quantity).toHaveAttribute('aria-invalid', 'false');
  await addButton(page).click();
  await expect.poll(() => api.cartRequests.length).toBe(1);
  expect(api.cartRequests[0].quantity).toBe(2);
});

test('INCI nguyên văn và mapping cùng tồn tại, thời điểm sử dụng không biến thành bước', async ({ page }) => {
  await mockDetail(page);
  await openDetail(page);
  await expect(detail(page)).toContainText('Thông tin lợi ích đã lưu');
  const inci = detail(page).getByRole('button', { name: 'Bảng thành phần nguyên văn (INCI)', exact: true });
  await expect(inci).toHaveAttribute('aria-expanded', 'false');
  await inci.click();
  await expect(inci).toHaveAttribute('aria-expanded', 'true');
  const panelId = await inci.getAttribute('aria-controls');
  expect(panelId).toBeTruthy();
  await expect(page.locator(`[id="${panelId}"]`)).toContainText(baseProduct.ingredients);
  for (const name of ['Thời điểm sử dụng', 'Tần suất', 'Các bước hướng dẫn', 'Hướng dẫn nguyên văn', 'Cảnh báo sử dụng']) {
    await expect(detail(page).getByRole('heading', { name, exact: true })).toBeVisible();
  }
  await expect(detail(page).getByText(baseProduct.howToUse, { exact: true })).toHaveCount(1);
  await expect(detail(page)).toContainText('Chưa công bố');
  await expect(detail(page).getByText(/không (chứa|có) hương liệu/i)).toHaveCount(0);
  await expect(detail(page).getByText(/chuẩn y khoa|kiểm nghiệm lâm sàng|hương liệu nhẹ/i)).toHaveCount(0);
});

test('Điều hướng nội dung và accordion INCI dùng được bằng bàn phím', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockDetail(page);
  await openDetail(page);
  const navigation = detail(page).getByRole('navigation', { name: 'Nội dung chi tiết sản phẩm', exact: true });
  const ingredientsLink = navigation.getByRole('link', { name: 'Thành phần & INCI', exact: true });
  await ingredientsLink.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#product-ingredients')).toBeFocused();
  await expect(ingredientsLink).toHaveAttribute('aria-current', 'location');
  const inci = detail(page).getByRole('button', { name: 'Bảng thành phần nguyên văn (INCI)', exact: true });
  await inci.focus();
  await page.keyboard.press('Enter');
  await expect(inci).toHaveAttribute('aria-expanded', 'true');
  await page.keyboard.press('Enter');
  await expect(inci).toHaveAttribute('aria-expanded', 'false');
  await expect(page).toHaveURL(/#\/product\/42$/);
});

test('Ảnh tương đối dùng resolveMediaUrl, thumbnail có trạng thái và dialog trả focus khi Escape', async ({ page }) => {
  const api = await mockDetail(page);
  await openDetail(page);
  const thumbnail = detail(page).getByRole('button', { name: 'Xem ảnh 2: Nhãn sau chai serum B5', exact: true });
  await thumbnail.click();
  await expect(thumbnail).toHaveAttribute('aria-pressed', 'true');
  const zoom = detail(page).getByRole('button', { name: 'Phóng to ảnh sản phẩm', exact: true });
  await expect(zoom.locator('img')).toHaveAttribute('src', /\/media\/detail-back\.svg$/);
  await zoom.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Ảnh sản phẩm', exact: true });
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  await expect(dialog.getByRole('button', { name: 'Đóng ảnh phóng to' })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBeTruthy();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(zoom).toBeFocused();
  expect(api.pageErrors).toEqual([]);
});

test('Ảnh hỏng hiển thị placeholder và không dùng ảnh sản phẩm khác', async ({ page }) => {
  const api = await mockDetail(page, { brokenImages: true });
  await openDetail(page);
  await expect.poll(async () => detail(page).locator('img[src*="detail-front.svg"]').count()).toBe(0);
  await expect(detail(page).locator('img[src*="unsplash"], img[src*="picsum"]')).toHaveCount(0);
  await expect(addButton(page)).toBeEnabled();
  expect(api.pageErrors).toEqual([]);
});

test('Số lượt chấm sao xác thực và tổng bình luận công khai hiển thị riêng', async ({ page }) => {
  await mockDetail(page);
  await openDetail(page);
  const reviews = page.locator('#product-reviews');
  await expect(reviews.getByRole('heading', { name: 'Đánh giá và bình luận' })).toBeVisible();
  await expect(reviews).toContainText('2 lượt chấm sao từ đơn đã mua');
  await expect(reviews).toContainText('3 đánh giá và bình luận công khai');
  await expect(reviews).toContainText('2,5');
  await expect(reviews.getByText('Nhận xét công khai 3', { exact: true })).toBeVisible();
});

test('Tải trang bình luận kế tiếp, giữ trang cũ khi lỗi và thử lại không trùng dòng', async ({ page }) => {
  const api = await mockDetail(page, { reviews: publicReviews(13), reviewFailurePage: 1 });
  await openDetail(page);
  const reviews = page.locator('#product-reviews');
  const list = page.locator('#product-review-list');
  await expect(list.getByText('Nhận xét công khai 10', { exact: true })).toBeVisible();
  await reviews.getByRole('button', { name: 'Tải thêm bình luận', exact: true }).click();
  await expect(reviews.getByRole('alert')).toBeVisible();
  await expect(list.getByText('Nhận xét công khai 1', { exact: true })).toBeVisible();
  await expect(list.getByText('Nhận xét công khai 11', { exact: true })).toHaveCount(0);
  api.reviewFailurePage = null;
  await reviews.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(list.getByText('Nhận xét công khai 13', { exact: true })).toBeVisible();
  await expect(list.getByText('Nhận xét công khai 1', { exact: true })).toHaveCount(1);
  await expect(reviews.getByRole('button', { name: 'Tải thêm bình luận', exact: true })).toHaveCount(0);
  expect(api.reviewRequests).toEqual([0, 1, 1]);
});

test('Chưa tải được đơn hàng không bị diễn giải thành chưa mua, vẫn gửi bình luận', async ({ page }) => {
  const api = await mockDetail(page, { authenticated: true, eligibilityFailure: true });
  await openDetail(page);
  const reviews = page.locator('#product-reviews');
  await expect(reviews).toContainText('Chưa xác minh quyền chấm sao');
  await expect(reviews.getByRole('button', { name: 'Kiểm tra lại quyền chấm sao' })).toBeVisible();
  await expect(reviews.getByText(/bạn chưa mua sản phẩm/i)).toHaveCount(0);
  await reviews.getByRole('textbox', { name: 'Nội dung bình luận', exact: true }).fill('Tôi muốn hỏi thêm về thông tin trên nhãn.');
  await reviews.getByRole('button', { name: 'Gửi bình luận', exact: true }).click();
  await expect(reviews.getByRole('status')).toContainText(/duyệt|kiểm duyệt/i);
  expect(api.submittedReviews).toHaveLength(1);
  expect(api.submittedReviews[0].orderId ?? null).toBeNull();
  expect(api.submittedReviews[0].rating ?? null).toBeNull();
});

test('Gửi đánh giá có nhãn và phản hồi kiểm duyệt bằng tiếng Việt', async ({ page }) => {
  const api = await mockDetail(page, { authenticated: true });
  await openDetail(page);
  const reviews = page.locator('#product-reviews');
  await expect(reviews.getByRole('combobox', { name: 'Đơn hàng đã giao', exact: true })).toBeVisible();
  await reviews.getByRole('radio', { name: '3 sao', exact: true }).check();
  await reviews.getByRole('textbox', { name: 'Tiêu đề (không bắt buộc)', exact: true }).fill('Nhận xét mới');
  await reviews.getByRole('textbox', { name: 'Nội dung bình luận', exact: true }).fill('Thông tin quy cách rõ ràng.');
  await reviews.getByRole('button', { name: 'Gửi đánh giá', exact: true }).click();
  await expect(reviews.getByRole('status')).toContainText(/duyệt|kiểm duyệt/i);
  await expect(page.locator('#product-review-list').getByText('Nhận xét mới', { exact: true })).toHaveCount(0);
  expect(api.submittedReviews[0]).toMatchObject({ productId: 42, orderId: 77, rating: 3, title: 'Nhận xét mới' });
});

test('Đơn đã giao ở trang sau vẫn xác minh được quyền chấm sao', async ({ page }) => {
  const orders = Array.from({ length: 100 }, (_, index) => ({ id: index + 1000, orderNumber: `ORD-${index + 1000}`, status: 'DELIVERED', items: [{ variantId: 500 }] }));
  orders.push({ id: 77, orderNumber: 'ORD-77', status: 'DELIVERED', items: [{ variantId: 99 }] });
  await mockDetail(page, { authenticated: true, orders });
  await openDetail(page);
  await expect(page.locator('#product-reviews').getByRole('combobox', { name: 'Đơn hàng đã giao', exact: true })).toHaveValue('77');
  await expect(page.locator('#product-reviews').getByRole('button', { name: 'Gửi đánh giá', exact: true })).toBeVisible();
});

test('Backend từ chối đánh giá trùng hiển thị lỗi và giữ nội dung', async ({ page }) => {
  await mockDetail(page, { authenticated: true, reviewSubmissionFailure: true });
  await openDetail(page);
  const reviews = page.locator('#product-reviews');
  const content = reviews.getByRole('textbox', { name: 'Nội dung bình luận', exact: true });
  await content.fill('Nội dung giữ lại khi máy chủ từ chối.');
  await reviews.getByRole('button', { name: 'Gửi đánh giá', exact: true }).click();
  await expect(reviews.getByRole('alert')).toContainText('Đơn hàng này đã được đánh giá trước đó');
  await expect(content).toHaveValue('Nội dung giữ lại khi máy chủ từ chối.');
});

test('Lỗi 503 có thử lại, không giả thành sản phẩm không tồn tại', async ({ page }) => {
  const api = await mockDetail(page, { detailStatus: 503 });
  await page.goto('/#/product/42');
  const retry = page.getByRole('button', { name: 'Thử tải lại sản phẩm', exact: true });
  await expect(retry).toBeVisible();
  await expect(page.getByText('Không tìm thấy sản phẩm', { exact: true })).toHaveCount(0);
  api.detailStatus = 200;
  await retry.click();
  await expect(detail(page).getByRole('heading', { level: 1, name: baseProduct.name })).toBeVisible();
});

test('Lỗi 404 hiển thị không tìm thấy sản phẩm riêng', async ({ page }) => {
  await mockDetail(page, { detailStatus: 404 });
  await page.goto('/#/product/42');
  await expect(page.getByRole('heading', { name: /không tìm thấy sản phẩm/i })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Thử tải lại sản phẩm', exact: true })).toHaveCount(0);
});

test('Chuyển productId nhanh không để response cũ ghi đè sản phẩm mới', async ({ page }) => {
  const gate = deferred();
  const api = await mockDetail(page, { detailGate: gate });
  await page.goto('/#/product/42');
  await page.evaluate(() => { window.location.hash = '#/product/43'; });
  await expect(detail(page).getByRole('heading', { level: 1, name: 'Kem dưỡng Ceramide' })).toBeVisible();
  gate.resolve();
  await page.waitForTimeout(100);
  await expect(detail(page).getByRole('heading', { level: 1, name: 'Kem dưỡng Ceramide' })).toBeVisible();
  await expect(detail(page).getByRole('heading', { level: 1, name: baseProduct.name })).toHaveCount(0);
  expect(api.pageErrors).toEqual([]);
});

test('API phụ lỗi không chặn detail và không thông báo danh sách rỗng thành công', async ({ page }) => {
  const api = await mockDetail(page, { authenticated: true, auxiliaryFailure: true });
  await openDetail(page);
  await expect(addButton(page)).toBeEnabled();
  await expect(page.locator('#product-reviews').getByRole('alert')).toBeVisible();
  await expect(page.locator('#product-reviews')).toContainText('Chưa xác minh quyền chấm sao');
  await expect(page.locator('#product-reviews').getByText(/chưa có (đánh giá|bình luận)/i)).toHaveCount(0);
  expect(api.pageErrors).toEqual([]);
});

test('API phụ tải chậm vẫn cho xem và chọn mua sản phẩm ngay', async ({ page }) => {
  const gate = deferred();
  const api = await mockDetail(page, { authenticated: true, auxiliaryGate: gate });
  await openDetail(page);
  await expect(addButton(page)).toBeEnabled();
  await expect(page.locator('#product-reviews')).toContainText('Đang xác minh');
  gate.resolve();
  await expect(page.locator('#product-review-list').getByText('Nhận xét công khai 1', { exact: true })).toBeVisible();
  expect(api.pageErrors).toEqual([]);
});

for (const width of [360, 390, 768, 1440]) {
  test(`Detail không tràn ngang, thanh mua và chatbot không chồng nhau tại ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    const product = clone(baseProduct);
    product.name = 'Serum phục hồi B5 với tên sản phẩm dài để kiểm tra bố cục và quy cách hiển thị trên nhiều kích thước màn hình';
    const api = await mockDetail(page, { product });
    await page.goto('/#/product/42');
    await expect(detail(page).getByRole('heading', { level: 1, name: product.name })).toBeVisible();
    await expect.poll(async () => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBeTruthy();
    await page.locator('#product-reviews').scrollIntoViewIfNeeded();
    const mobileBar = detail(page).locator('.pd-mobile-purchase');
    if (width < 768) {
      await expect(mobileBar).toBeVisible();
      const chatbot = page.locator('.chatbot-toggle-button');
      await expect(chatbot).toBeVisible();
      const bounds = await mobileBar.boundingBox();
      const chatBounds = await chatbot.boundingBox();
      expect(chatBounds.y + chatBounds.height <= bounds.y || chatBounds.x >= bounds.x + bounds.width || chatBounds.x + chatBounds.width <= bounds.x).toBeTruthy();
    }
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: test.info().outputPath(`product-detail-${width}-hero.png`) });
    await page.screenshot({ path: test.info().outputPath(`product-detail-${width}.png`), fullPage: true });
    expect(api.pageErrors).toEqual([]);
  });
}
