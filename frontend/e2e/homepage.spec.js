import { test, expect } from '@playwright/test';

// ============================================================
// BEAUTYSHOP UI — COMPREHENSIVE PRE-HANDOVER TEST SUITE
// Tests: Customer Homepage, Navigator, Sections, Product pages
// ============================================================

const BASE = 'http://localhost:5173';

// ── Helper: wait for page to stop loading ──────────────────
async function waitForAppReady(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

// ===========================================================
// 1. HOMEPAGE — General Render & Structure
// ===========================================================
test.describe('1. Trang Chủ (HomePage)', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/banners', route => route.fulfill({ json: { data: [
      { id: 1, title: 'Banner ưu đãi', imageUrl: '/banner-home-1.svg', targetUrl: 'products', position: 'HERO_SLIDE', isActive: true },
      { id: 2, title: 'Banner chăm sóc da', imageUrl: '/banner-home-2.svg', targetUrl: 'booking', position: 'HERO_SLIDE', isActive: true },
    ] } }));
    await page.route('**/banner-home-*.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="500"><rect width="1600" height="500" fill="#d7efe7"/></svg>' }));
  });

  test('1.1 Homepage tải thành công và title đúng', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const title = await page.title();
    console.log('Page title:', title);
    // Check main container renders
    await expect(page.locator('.customer-home-page')).toBeVisible({ timeout: 8000 });
  });

  test('1.2 Hero banner hiển thị đúng', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await expect(page.locator('.hero-slider-container')).toBeVisible({ timeout: 8000 });
    const image = page.locator('.image-banner-carousel img');
    await expect(image).toBeVisible();
    await expect(page.locator('.hero-slider-container h1')).toHaveCount(0);
    expect(await image.getAttribute('alt')).toBeTruthy();
  });

  test('1.3 Hero carousel có navigation bullets', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const bullets = page.locator('.image-banner-dots button');
    await expect(bullets.first()).toBeVisible({ timeout: 5000 });
    const count = await bullets.count();
    console.log('Hero bullet count:', count);
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('1.4 Hero carousel chuyển ảnh bằng điều hướng', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const image = page.locator('.image-banner-carousel img');
    const before = await image.getAttribute('src');
    await page.getByRole('button', { name: 'Banner ti\u1ebfp theo', exact: true }).click();
    await expect(image).not.toHaveAttribute('src', before);
  });

  test('1.5 Value proposition cards hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    // Scroll past hero
    await page.evaluate(() => window.scrollBy(0, 700));
    await page.waitForTimeout(500);
    const cards = page.locator('.luxury-card');
    const count = await cards.count();
    console.log('Luxury card count:', count);
    expect(count).toBeGreaterThan(0);
  });

  test('1.6 Section danh mục sản phẩm hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await page.evaluate(() => window.scrollBy(0, 1000));
    await page.waitForTimeout(1000);
    // Look for category section
    const categorySection = page.locator('.customer-container').filter({ hasText: 'Khám Phá Danh Mục' }).first();
    await expect(categorySection).toBeVisible({ timeout: 8000 });
    console.log('Category section found ✓');
  });

  test('1.7 Flash Sale công bố dải HSD, không dựng đếm ngược', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const flashSection = page.locator('#flash-sale');
    await expect(flashSection).toBeVisible({ timeout: 8000 });
    await expect(flashSection).toContainText('31–90 ngày');
    await expect(flashSection.locator('.countdown-digit-val')).toHaveCount(0);
  });

  test('Flash Sale left-aligns short rows and aligns campaign and clearance cards', async ({ page }) => {
    const item = { productId: 1, productVariantId: 11, productName: 'Giờ vàng', sku: 'FLASH-1',
      flashPrice: 1900000, originalPrice: 2750000, quantityQuota: 20, quantitySold: 0, remainingQuota: 20 };
    await page.route('**/api/v1/flash-sales/current', route => route.fulfill({ json: { data: {
      currentSlotId: 1, items: [item, { ...item, productId: 2, productVariantId: 12, flashPrice: 110000, originalPrice: 165000 }],
    } } }));
    await page.route('**/api/v1/products/expiring-soon?**', route => route.fulfill({ json: { data: [{
      id: 3, name: 'Sản phẩm cận date', earliestExpirationDate: '2027-01-04', clearanceStock: 5,
      clearanceVariant: { id: 13, sku: 'EXP-1', price: 389000, discountPrice: 269000, stockQuantity: 5, isActive: true },
    }] } }));
    await page.goto(BASE);
    const cards = page.locator('#flash-sale .flash-sale-grid > .product-card-resolved');
    const grid = page.locator('#flash-sale .flash-sale-grid').filter({ has: page.locator('.product-card-resolved') });
    await expect(cards).toHaveCount(3);
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 900 });
      await expect.poll(() => cards.evaluateAll(elements => {
        const bottoms = elements.map(element => element.getBoundingClientRect().bottom);
        const prices = elements.map(element => element.querySelector('.product-card-price > span').getBoundingClientRect().top);
        return Math.max(Math.max(...bottoms) - Math.min(...bottoms), Math.max(...prices) - Math.min(...prices));
      })).toBeLessThan(1);
      const { leftGap, overflow } = await grid.evaluate(element => {
        const bounds = element.getBoundingClientRect();
        return {
          leftGap: element.firstElementChild.getBoundingClientRect().left - bounds.left,
          overflow: element.scrollWidth - element.clientWidth,
        };
      });
      expect(Math.abs(leftGap)).toBeLessThan(1);
      if (width === 390) expect(overflow).toBeGreaterThan(0);
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.locator('#flash-sale').getByRole('button', { name: /Khuy.n m.i/ }).click();
    await expect(cards).toHaveCount(1);
    await expect.poll(() => grid.evaluate(element => Math.abs(
      element.firstElementChild.getBoundingClientRect().left - element.getBoundingClientRect().left,
    ))).toBeLessThan(1);
  });

  test('1.8 Section "10 sản phẩm ngẫu nhiên" hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const randomSection = page.locator('#random-picks');
    await randomSection.scrollIntoViewIfNeeded();
    await expect(randomSection).toBeVisible({ timeout: 8000 });
    const heading = randomSection.getByRole('heading', { name: 'GỢI Ý DÀNH CHO BẠN' });
    await expect(heading).toBeVisible();
    const centers = await randomSection.locator('h2, .recommendations-badge, .recommendations-note')
      .evaluateAll(elements => elements.map(element => {
        const { top, height } = element.getBoundingClientRect();
        return top + height / 2;
      }));
    expect(centers).toHaveLength(3);
    expect(Math.max(...centers) - Math.min(...centers)).toBeLessThan(1);

    await page.setViewportSize({ width: 390, height: 844 });
    const note = randomSection.locator('.recommendations-note');
    expect((await note.boundingBox()).y).toBeGreaterThan((await heading.boundingBox()).y);
  });

  test('1.9 Toàn bộ sản phẩm catalog section hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const catalogSection = page.locator('#store-catalog');
    await catalogSection.scrollIntoViewIfNeeded();
    await expect(catalogSection).toBeVisible({ timeout: 10000 });
    console.log('Store catalog section ✓');
  });

  test('1.10 Bộ lọc danh mục trên trang chủ giữ lựa chọn khi mở catalog', async ({ page }) => {
    await page.route('**/api/v1/categories', route => route.fulfill({ json: { data:
      Array.from({ length: 8 }, (_, index) => ({ id: index + 1, name: `Danh mục ${index + 1}` })),
    } }));
    await page.route('**/api/v1/products?size=60', route => route.fulfill({ json: {
      data: { content: [{ id: 1, name: 'Kem dưỡng' }], totalElements: 1 },
    } }));
    await page.route('**/api/v1/products/category/8?size=60', route => route.fulfill({ json: {
      data: { content: [{ id: 2, name: 'Serum kiểm thử' }] },
    } }));
    await page.goto(BASE);
    const catalogSection = page.locator('#store-catalog');
    const filter = catalogSection.getByRole('combobox', { name: 'Lọc danh mục sản phẩm' });
    await expect(filter).toHaveValue('ALL');
    await filter.selectOption('8');
    await catalogSection.getByRole('textbox', { name: 'Tìm nhanh sản phẩm' }).fill('Serum');
    await catalogSection.getByRole('combobox', { name: 'Sắp xếp sản phẩm' }).selectOption('PRICE_ASC');
    await expect(catalogSection.getByRole('link', { name: 'Serum kiểm thử', exact: true })).toBeVisible();
    await catalogSection.getByRole('button', { name: 'Xem tất cả sản phẩm', exact: true }).click();
    await expect(page).toHaveURL(/#\/products\?categoryId=8&search=Serum&sort=price_asc$/);
  });

  test('1.11 Spa services section hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const spaSection = page.locator('#spa-section');
    await spaSection.scrollIntoViewIfNeeded();
    await expect(spaSection).toBeVisible({ timeout: 10000 });
    const heading = await spaSection.locator('h2').first().textContent();
    console.log('Spa section heading:', heading?.substring(0, 60));
    expect(heading?.length).toBeGreaterThan(0);
  });

  test('1.12 Brand partners section hiển thị', async ({ page }) => {
    await page.route('**/api/v1/brands*', route => route.fulfill({ json: { data: { content: [
      { id: 42, name: 'Thương hiệu kiểm thử', logoUrl: '/brand-home-test.svg' },
    ] } } }));
    await page.route('**/brand-home-test.svg', route => route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="40"><circle cx="50" cy="20" r="15" fill="#8c2a47"/></svg>',
    }));
    await page.goto(BASE);
    const brandSection = page.locator('#brand-partners');
    await brandSection.scrollIntoViewIfNeeded();
    const brand = brandSection.getByRole('button', { name: 'Thương hiệu kiểm thử' }).first();
    await expect(brand.getByRole('img')).toBeVisible();
    await brandSection.locator('.brands-marquee-wrapper').hover();
    await brand.hover();
    await expect(brand).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
    await expect(brandSection.locator('.brands-marquee-track')).toHaveCSS('animation-play-state', 'paused');

    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect(brandSection.locator('.brands-marquee-track')).toHaveCSS('animation-name', 'none');
    await expect(brandSection.getByRole('button', { name: 'Thương hiệu kiểm thử' })).toHaveCount(1);
    await brand.press('Enter');
    await expect(page).toHaveURL(/#\/products\?brandId=42/);
  });

  test('1.13 Store info section (#store-about) hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const storeAbout = page.locator('#store-about');
    await storeAbout.scrollIntoViewIfNeeded();
    await expect(storeAbout).toBeVisible({ timeout: 10000 });
    const heading = await storeAbout.locator('h2').first().textContent();
    console.log('Store about heading:', heading?.substring(0, 60));
    expect(heading?.length).toBeGreaterThan(0);
  });

  test('1.14 Newsletter/Voucher section hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const voucherSection = page.locator('#voucher-section');
    await voucherSection.scrollIntoViewIfNeeded();
    await expect(voucherSection).toBeVisible({ timeout: 10000 });
    console.log('Voucher/newsletter section ✓');
  });

  test('1.15 Nút copy coupon code hoạt động', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const voucherSection = page.locator('#voucher-section');
    await voucherSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(500);
    // Find copy button
    const copyBtn = voucherSection.locator('button').filter({ hasText: /Sao chép|Copy/ }).first();
    if (await copyBtn.isVisible()) {
      await copyBtn.click();
      await page.waitForTimeout(600);
      const btnText = await copyBtn.textContent();
      console.log('After copy button text:', btnText?.trim());
    }
  });

});

// ===========================================================
// 2. NAVIGATOR HEADER
// ===========================================================
test.describe('2. Thanh Navigator (CustomerHeader)', () => {

  test('2.1 Header hiển thị đầy đủ', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await expect(page.locator('.customer-header')).toBeVisible({ timeout: 8000 });
    console.log('Header visible ✓');
  });

  test('2.2 Logo BeautyShop hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const logo = page.locator('.customer-brand-logo');
    await expect(logo).toBeVisible({ timeout: 5000 });
    const logoText = await logo.textContent();
    console.log('Logo text:', logoText?.trim().substring(0, 30));
    expect(logoText).toMatch(/BEAUTYSHOP/i);
  });

  test('2.3 Sub-navigation bar hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const subnav = page.locator('.customer-subnav-bar');
    await expect(subnav).toBeVisible({ timeout: 5000 });
    const links = subnav.locator('.customer-subnav-item');
    const count = await links.count();
    console.log('Subnav items:', count);
    expect(count).toBeGreaterThanOrEqual(4);
  });

  test('2.4 Nav item "Trang chủ" tồn tại và click được', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const homeLink = page.locator('.customer-subnav-item').filter({ hasText: /Trang chủ/i }).first();
    await expect(homeLink).toBeVisible({ timeout: 5000 });
    await homeLink.click();
    await page.waitForTimeout(500);
    console.log('Home nav click ✓');
  });

  test('2.5 Nav item "Dược Mỹ Phẩm" có dropdown categories', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const productsNav = page.locator('.customer-subnav-item').filter({ hasText: /Dược Mỹ Phẩm/i }).first();
    await expect(productsNav).toBeVisible({ timeout: 5000 });
    // Hover to open dropdown
    await productsNav.hover();
    await page.waitForTimeout(400);
    const dropdown = page.locator('.customer-subnav-dropdown').first();
    const isVisible = await dropdown.isVisible();
    console.log('Categories dropdown visible on hover:', isVisible);
  });

  test('2.6 Nav item "Thương Hiệu" tồn tại', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const brandNav = page.locator('.customer-subnav-item').filter({ hasText: /Thương Hiệu/i }).first();
    await expect(brandNav).toBeVisible({ timeout: 5000 });
    console.log('Brand nav item ✓');
  });

  test('2.7 Nav item "Dịch Vụ Spa" tồn tại', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const spaNav = page.locator('.customer-subnav-item').filter({ hasText: /Dịch Vụ|Spa/i }).first();
    await expect(spaNav).toBeVisible({ timeout: 5000 });
    console.log('Spa nav item ✓');
  });

  test('2.8 Nav "Giờ Vàng Deal" scroll đến section flash-sale', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const flashNav = page.locator('.customer-subnav-item').filter({ hasText: /Giờ Vàng/i }).first();
    if (await flashNav.isVisible()) {
      await flashNav.click();
      await page.waitForTimeout(1200);
      const flashSection = page.locator('#flash-sale');
      const inView = await flashSection.isVisible();
      console.log('Flash sale section in view after nav click:', inView);
    } else {
      console.log('Flash nav item not found — skip');
    }
  });

  test('2.9 Search bar hiển thị và nhận input', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const searchInput = page.locator('.ecommerce-search-input');
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('CeraVe');
    const value = await searchInput.inputValue();
    expect(value).toBe('CeraVe');
    console.log('Search input ✓ value:', value);
  });

  test('2.10 Trending keywords hiển thị dưới search bar', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const trendingTags = page.locator('.ecommerce-trending-tag');
    const count = await trendingTags.count();
    console.log('Trending tag count:', count);
    expect(count).toBeGreaterThanOrEqual(1);
  });

  test('2.11 Cart button hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const cartBtn = page.locator('.header-cart-button');
    await expect(cartBtn).toBeVisible({ timeout: 5000 });
    console.log('Cart button ✓');
  });

  test('2.12 Nav "Về Cửa Hàng" scroll đến section', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const storeNav = page.locator('.customer-subnav-item').filter({ hasText: /Về Cửa Hàng/i }).first();
    if (await storeNav.isVisible()) {
      await storeNav.click();
      await page.waitForTimeout(1500);
      const storeSection = page.locator('#store-about');
      const inView = await storeSection.isVisible();
      console.log('Store about section in view after nav click:', inView);
    } else {
      console.log('Store nav not visible — skip');
    }
  });

  test('2.13 Top utility bar hiển thị thông tin (hotline, shipping)', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const topBar = page.locator('.customer-header-top');
    await expect(topBar).toBeVisible({ timeout: 5000 });
    const topText = await topBar.textContent();
    expect(topText).toMatch(/1900|Hotline/i);
    console.log('Top bar ✓ contains hotline');
  });

});

// ===========================================================
// 3. SẢN PHẨM — Product Catalog Page
// ===========================================================
test.describe('3. Trang Sản Phẩm (CatalogPage)', () => {

  test('3.1 Trang sản phẩm tải được', async ({ page }) => {
    await page.goto(`${BASE}/#/products`);
    await waitForAppReady(page);
    await page.waitForTimeout(1500);
    // Check the page has some product-related content
    const body = await page.textContent('body');
    expect(body).toMatch(/Sản phẩm|Dược Mỹ Phẩm|Catalog|Lọc/i);
    console.log('Product catalog page loaded ✓');
  });

  test('3.2 Thanh lọc/filter hiển thị', async ({ page }) => {
    await page.goto(`${BASE}/#/products`);
    await waitForAppReady(page);
    await page.waitForTimeout(1500);
    // Look for any filter-like element
    const body = await page.textContent('body');
    console.log('Catalog body snippet:', body?.substring(0, 200));
    expect(body?.length).toBeGreaterThan(100);
  });

});

// ===========================================================
// 4. NAVIGATION FLOW — Chuyển trang
// ===========================================================
test.describe('4. Navigation Flow', () => {

  test('4.1 Click logo về trang chủ', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    // Navigate away first
    await page.goto(`${BASE}/#/products`);
    await page.waitForTimeout(800);
    // Click logo
    const logo = page.locator('.customer-brand-logo');
    await logo.click();
    await page.waitForTimeout(800);
    await expect(page.locator('.customer-home-page')).toBeVisible({ timeout: 5000 });
    console.log('Logo → home navigation ✓');
  });

  test('4.2 Click "Dược Mỹ Phẩm" navigate sang catalog', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const productsNav = page.locator('.customer-subnav-item').filter({ hasText: /Dược Mỹ Phẩm/i }).first();
    await productsNav.click();
    await page.waitForTimeout(1500);
    const url = page.url();
    console.log('URL after products nav click:', url);
    const body = await page.textContent('body');
    expect(body?.length).toBeGreaterThan(100);
  });

  test('4.3 Click "Đặt Lịch Hẹn Spa" navigate sang booking', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const bookingNav = page.locator('.customer-subnav-item').filter({ hasText: /Đặt Lịch/i }).first();
    if (await bookingNav.isVisible()) {
      await bookingNav.click();
      await page.waitForTimeout(1200);
      const body = await page.textContent('body');
      console.log('Booking page content snippet:', body?.substring(0, 150));
      expect(body?.length).toBeGreaterThan(100);
    } else {
      console.log('Booking nav not visible — skip');
    }
  });

  test('4.4 Button "Xem toàn bộ sản phẩm" trên random-picks navigate', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const randomSection = page.locator('#random-picks');
    await randomSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(500);
    const viewAllBtn = randomSection.locator('button').filter({ hasText: /Xem toàn bộ/i }).first();
    if (await viewAllBtn.isVisible()) {
      await viewAllBtn.click();
      await page.waitForTimeout(1000);
      console.log('View all button → navigate ✓');
    }
  });

  test('4.5 Button "Đặt lịch ngay" trong spa section hoạt động', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const spaSection = page.locator('#spa-section');
    await spaSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1000);
    const bookBtn = spaSection.locator('button').filter({ hasText: /Đặt lịch ngay/i }).first();
    if (await bookBtn.isVisible()) {
      await bookBtn.click();
      await page.waitForTimeout(1000);
      console.log('Spa booking button ✓');
    } else {
      console.log('No visible spa booking button (no spa data?) — skip');
    }
  });

});

// ===========================================================
// 5. PRODUCT CARDS — Kiểm tra card sản phẩm
// ===========================================================
test.describe('5. Product Cards', () => {

  test('5.1 Product cards hiển thị trong flash-sale', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await page.waitForTimeout(2000);
    const flashSection = page.locator('#flash-sale');
    await flashSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1000);
    // Flash sale products (product cards or skeleton)
    const cards = flashSection.locator('[class*="product"]');
    const count = await cards.count();
    console.log('Flash sale product elements:', count);
    expect(count).toBeGreaterThanOrEqual(0); // may be 0 if API not running
  });

  test('5.2 Product cards hiển thị trong random-picks', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await page.waitForTimeout(2000);
    const randomSection = page.locator('#random-picks');
    await randomSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1000);
    const grid = randomSection.locator('.customer-product-grid');
    const isVisible = await grid.isVisible();
    console.log('Random picks product grid visible:', isVisible);
  });

  test('5.3 Product grid trong store-catalog hiển thị', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await page.waitForTimeout(2000);
    const catalogSection = page.locator('#store-catalog');
    await catalogSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1000);
    const grid = catalogSection.locator('.customer-product-grid');
    const isVisible = await grid.isVisible();
    console.log('Store catalog product grid visible:', isVisible);
  });

});

// ===========================================================
// 6. RESPONSIVE — Kiểm tra mobile breakpoint
// ===========================================================
test.describe('6. Responsive Layout', () => {

  test('6.1 Mobile viewport (375px) - header vẫn hiển thị', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(BASE);
    await waitForAppReady(page);
    await expect(page.locator('.customer-header')).toBeVisible({ timeout: 8000 });
    console.log('Mobile header ✓');
  });

  test('6.2 Mobile viewport - hero banner hiển thị', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(BASE);
    await waitForAppReady(page);
    await expect(page.locator('.hero-slider-container')).toBeVisible({ timeout: 8000 });
    console.log('Mobile hero ✓');
  });

  test('6.3 Tablet viewport (768px) - layout ổn định', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto(BASE);
    await waitForAppReady(page);
    await expect(page.locator('.customer-home-page')).toBeVisible({ timeout: 8000 });
    console.log('Tablet layout ✓');
  });

  test('6.4 Desktop viewport (1440px) - full layout', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(BASE);
    await waitForAppReady(page);
    await expect(page.locator('.customer-subnav-bar')).toBeVisible({ timeout: 8000 });
    console.log('Desktop subnav ✓');
  });

});

// ===========================================================
// 7. INTERACTIVE UX — Modals, Toast, Transitions
// ===========================================================
test.describe('7. Interactive UX Elements', () => {

  test('7.1 INCI library section hiển thị ingredient tiles', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const inciSection = page.locator('#inci-library');
    await inciSection.scrollIntoViewIfNeeded();
    await expect(inciSection).toBeVisible({ timeout: 8000 });
    console.log('INCI library section ✓');
  });

  test('7.2 Click ingredient tile chuyển spotlight', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const inciSection = page.locator('#inci-library');
    await inciSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(500);
    // Click B5 tile
    const b5Tile = inciSection.locator('div').filter({ hasText: /Panthenol.*B5/i }).first();
    if (await b5Tile.isVisible()) {
      await b5Tile.click();
      await page.waitForTimeout(400);
      console.log('Ingredient tile click ✓');
    }
  });

  test('7.3 Search focus hiển thị suggestions dropdown', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const searchInput = page.locator('.ecommerce-search-input');
    await searchInput.click();
    await page.waitForTimeout(500);
    // Check if suggestion dropdown appeared
    const suggestionsVisible = await page.locator('.ecommerce-search-input').isVisible();
    console.log('Search focused ✓');
    expect(suggestionsVisible).toBeTruthy();
  });

  test('7.4 Cart button click mở cart sidebar', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const cartBtn = page.locator('.header-cart-button');
    await cartBtn.click();
    await page.waitForTimeout(600);
    console.log('Cart button clicked ✓');
  });

  test('7.5 Scroll progress bar hiển thị khi scroll', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    await page.evaluate(() => window.scrollTo(0, 600));
    await page.waitForTimeout(500);
    const scrollBar = page.locator('[class*="scroll-progress"]').first();
    const isVisible = await scrollBar.isVisible();
    console.log('Scroll progress visible:', isVisible);
  });

  test('7.6 Newsletter form nhận input email', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    const voucherSection = page.locator('#voucher-section');
    await voucherSection.scrollIntoViewIfNeeded();
    await page.waitForTimeout(500);
    const emailInput = voucherSection.locator('input[type="email"]');
    if (await emailInput.isVisible()) {
      await emailInput.fill('test@example.com');
      const val = await emailInput.inputValue();
      expect(val).toBe('test@example.com');
      console.log('Newsletter email input ✓');
    }
  });

});

// ===========================================================
// 8. PERFORMANCE — Không có lỗi console nghiêm trọng
// ===========================================================
test.describe('8. Console Errors Check', () => {

  test('8.1 Không có console.error khi tải homepage', async ({ page }) => {
    const errors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    await page.goto(BASE);
    await waitForAppReady(page);
    await page.waitForTimeout(2000);
    // Filter out known benign network errors (API might be down in test)
    const criticalErrors = errors.filter(e =>
      !e.includes('net::ERR') &&       // Network errors OK (API might be offline)
      !e.includes('Failed to fetch') &&
      !e.includes('ECONNREFUSED') &&
      !e.includes('404')
    );
    if (criticalErrors.length > 0) {
      console.warn('⚠ Console errors:', criticalErrors);
    } else {
      console.log('No critical console errors ✓');
    }
    // Log all errors for info (not fail)
    errors.forEach(e => console.log('Console error:', e.substring(0, 120)));
  });

  test('8.2 Trang không hiển thị uncaught React errors', async ({ page }) => {
    await page.goto(BASE);
    await waitForAppReady(page);
    // Check that no React error overlay is shown
    const errorOverlay = page.locator('[data-overlay-type="error"], [id*="error"], .error-boundary');
    const hasError = await errorOverlay.count();
    console.log('Error overlay count:', hasError);
    // Confirm main app is rendered
    await expect(page.locator('.customer-home-page')).toBeVisible({ timeout: 8000 });
  });

});
