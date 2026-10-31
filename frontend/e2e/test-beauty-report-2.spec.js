import { test, expect } from '@playwright/test';

test.describe('Test Beauty Shop 2 Bugfixes', () => {
  test('Bug 1 & Bug 8: Button primary in account forms has visible white text and primary background', async ({ page }) => {
    await page.goto('/#/recover-password');
    const resetBtn = page.getByRole('button', { name: 'Gửi hướng dẫn qua email' });
    await expect(resetBtn).toBeVisible();

    const styles = await resetBtn.evaluate(el => {
      const computed = window.getComputedStyle(el);
      return {
        bg: computed.backgroundColor,
        color: computed.color,
      };
    });

    // Color should be white (#fff) and background should not be white
    expect(styles.color).toBe('rgb(255, 255, 255)');
    expect(styles.bg).not.toBe('rgb(255, 255, 255)');
    expect(styles.bg).not.toBe('rgba(0, 0, 0, 0)');

    await page.goto('/#/order-lookup');
    const orderLookupBtn = page.getByRole('button', { name: 'Mở đơn hàng' });
    await expect(orderLookupBtn).toBeVisible();
    const orderLookupStyles = await orderLookupBtn.evaluate(el => {
      const computed = window.getComputedStyle(el);
      return {
        bg: computed.backgroundColor,
        color: computed.color,
      };
    });
    expect(orderLookupStyles.color).toBe('rgb(255, 255, 255)');
    expect(orderLookupStyles.bg).not.toBe('rgb(255, 255, 255)');
  });

  test('Bug 3: DataTable pagination summary clearly includes total pages', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('beautyshop_admin_token', 'mock-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify({ id: 1, roles: ['ROLE_ADMIN'] }));
    });
    await page.route('**/api/v1/admin/orders*', route => {
      route.fulfill({
        status: 200,
        json: {
          data: {
            content: Array.from({ length: 9 }, (_, i) => ({ id: i + 16, orderCode: `ORD-${i + 16}`, finalAmount: 100000, status: 'PENDING', createdAt: '2026-10-31T00:00:00Z' })),
            page: 1,
            totalPages: 2,
            totalElements: 24,
          }
        }
      });
    });
    await page.goto('/#/admin/orders');
    const summary = page.locator('.table-pagination-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText('Trang 2 / 2');
    await expect(summary).toContainText('24');
  });

  test('Bug 4 & 6: Home banner height bounded, 2 side cards, all brands modal opens', async ({ page }) => {
    await page.goto('/#/');
    const mainBanner = page.locator('.home-banner-main');
    await expect(mainBanner).toBeVisible();
    const box = await mainBanner.boundingBox();
    expect(box.height).toBeLessThanOrEqual(520);

    const sideBanners = page.locator('.home-banner-side .home-banner-small');
    await expect(sideBanners).toHaveCount(2);

    const allBrandsBtn = page.getByRole('button', { name: 'Xem tất cả thương hiệu' });
    await expect(allBrandsBtn).toBeVisible();
    await allBrandsBtn.click();
    const modalTitle = page.getByRole('heading', { name: 'Tất cả thương hiệu chính hãng đối tác' });
    await expect(modalTitle).toBeVisible();
  });

  test('Bug 5: Admin Spa tabs header wraps instead of truncating the last tab', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('beautyshop_admin_token', 'mock-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify({ id: 1, roles: ['ROLE_ADMIN'] }));
    });
    await page.goto('/#/admin/spa');
    const tabsHeader = page.locator('.admin-shell .tabs-header');
    await expect(tabsHeader).toBeVisible();
    const flexWrap = await tabsHeader.evaluate(el => window.getComputedStyle(el).flexWrap);
    expect(flexWrap).toBe('wrap');

    const lastTab = tabsHeader.getByRole('button', { name: /Vé dịch vụ & Gói khách hàng/ });
    await expect(lastTab).toBeVisible();
    await expect(lastTab).toContainText('Vé dịch vụ & Gói khách hàng');
  });

  test('Bug 7: Catalog "Tất cả" button clears slugs and resets categories/brands filter', async ({ page }) => {
    await page.goto('/#/products?category=serum&name=Serum');
    const categoryAllBtn = page.getByRole('group', { name: 'Danh mục sản phẩm' }).getByRole('button', { name: /Tất cả/ });
    await expect(categoryAllBtn).toBeVisible();
    await categoryAllBtn.click();
    await expect(page).not.toHaveURL(/category=serum/);
  });
});
