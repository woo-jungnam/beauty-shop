import { test, expect } from '@playwright/test';

test.describe('Cart Product Image Handling', () => {
  test('cart drawer renders valid product image and handles broken image fallback gracefully', async ({ page }) => {
    const mockCart = {
      id: 1,
      totalPrice: 450000,
      items: [
        {
          id: 101,
          variantId: 201,
          sku: 'SERUM-B5-50ML',
          productName: 'Serum Phục Hồi Da B5',
          variantName: 'Chai 50ml',
          price: 450000,
          quantity: 1,
          imageUrl: 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=400&q=80',
          available: true,
        },
        {
          id: 102,
          variantId: 202,
          sku: 'CREAM-BROKEN-IMG',
          productName: 'Kem Dưỡng Ẩm Chuyên Sâu',
          variantName: 'Hũ 50g',
          price: 320000,
          quantity: 1,
          imageUrl: 'https://invalid-domain-404-not-found.test/broken-product-image.jpg',
          available: true,
        },
      ],
    };

    await page.route('**/api/v1/**', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/v1/cart') {
        return route.fulfill({ json: { data: mockCart } });
      }
      if (url.pathname.startsWith('/api/v1/products') || url.pathname.startsWith('/api/v1/categories')) {
        return route.fulfill({ json: { data: [] } });
      }
      return route.fulfill({ json: { data: null } });
    });

    await page.route('**/broken-product-image.jpg', (route) => route.abort('failed'));

    await page.goto('/#/');
    const cartButton = page.getByRole('button', { name: /Giỏ hàng/ });
    await cartButton.click();

    const drawer = page.getByRole('dialog', { name: /Giỏ Hàng Của Bạn/ });
    await expect(drawer).toBeVisible();

    const images = drawer.locator('img');
    await expect(images).toHaveCount(2);

    // Verify first item image has resolved image URL
    await expect(images.first()).toHaveAttribute('src', /images\.unsplash\.com/);

    // Verify second item image with broken URL falls back to placeholder SVG without breaking
    await expect(images.nth(1)).toHaveAttribute('src', /data:image\/svg\+xml/, { timeout: 5000 });
  });
});
