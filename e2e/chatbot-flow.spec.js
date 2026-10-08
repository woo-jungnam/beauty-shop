import { test, expect } from '@playwright/test';

const success = (data) => ({ success: true, data });

async function mockStorefront(page, { unavailable = false, targetType = 'PRODUCT' } = {}) {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path === '/api/v1/chatbot/health') {
      return route.fulfill({ json: success({ status: 'ok' }) });
    }
    if (path === '/api/v1/chatbot/chat') {
      if (unavailable) return route.fulfill({ status: 503, json: { message: 'AI unavailable' } });
      return route.fulfill({ json: success({
        answer: 'Đề xuất được lấy từ danh mục BeautyShop.',
        products: [{ id: `${targetType === 'SERVICE' ? 'service' : 'mysql'}_42`, name: 'Sản phẩm từ danh mục', price: 250000, target_type: targetType }],
      }) });
    }
    if (path === '/api/v1/products/42') {
      return route.fulfill({ json: success({ id: 42, variants: [{ id: 99, price: 250000, isActive: true, isDefault: true }] }) });
    }
    if (path === '/api/v1/cart/add') {
      expect(route.request().postDataJSON().variantId).toBe(99);
      return route.fulfill({ json: success({ id: 1, items: [{ id: 10, variantId: 99, variantName: 'Sản phẩm từ danh mục', price: 250000, quantity: 1 }] }) });
    }
    if (path === '/api/v1/cart') return route.fulfill({ json: success(null) });
    if (path === '/api/v1/products' || path === '/api/v1/brands') {
      return route.fulfill({ json: success({ content: [], totalElements: 0, totalPages: 0 }) });
    }
    return route.fulfill({ json: success([]) });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Hỏi Bác Sĩ & AI Da Liễu' }).click();
}

async function sendQuestion(page) {
  await page.getByPlaceholder('Nhập câu hỏi về da, thành phần...').fill('Tư vấn kem chống nắng cho da dầu');
  await page.getByRole('button', { name: 'Gửi câu hỏi' }).click();
}

test('The customer catalog remains public and the admin catalog requires authentication', async ({ page }) => {
  await mockStorefront(page);
  await page.goto('/#/products');
  await expect(page.getByRole('heading', { name: 'Mỹ Phẩm Khoa Học & Phục Hồi Làn Da' })).toBeVisible();
  await expect(page.locator('#admin-auth-username')).toHaveCount(0);
  await page.goto('/#/admin/products');
  await expect(page.locator('#admin-auth-username')).toBeVisible();
});

test('AI product recommendations resolve their MySQL IDs before adding a backend cart item', async ({ page }) => {
  await mockStorefront(page);
  await sendQuestion(page);
  await expect(page.getByText('Đề xuất được lấy từ danh mục BeautyShop.')).toBeVisible();
  const productRequest = page.waitForRequest((request) => new URL(request.url()).pathname === '/api/v1/products/42');
  const cartResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/cart/add');
  await page.getByRole('button', { name: 'Thêm Vào Giỏ Ngay' }).click();
  await productRequest;
  expect((await cartResponse).ok()).toBeTruthy();
  await expect(page.locator('.drawer-panel')).toBeVisible();
});

test('AI service recommendations open booking with the correct catalog service selected', async ({ page }) => {
  await mockStorefront(page, { targetType: 'SERVICE' });
  await sendQuestion(page);
  await page.getByRole('button', { name: 'Đặt Lịch Liệu Trình Này' }).click();
  await expect(page).toHaveURL(/#\/booking\?serviceId=42$/);
});

test('An unavailable chatbot shows connection failure without invented product recommendations', async ({ page }) => {
  await mockStorefront(page, { unavailable: true });
  await sendQuestion(page);
  await expect(page.getByText('Trợ lý AI hiện chưa kết nối được. Vui lòng gửi lại câu hỏi sau ít phút.')).toBeVisible();
  await expect(page.getByText('○ AI tạm thời chưa kết nối')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Thêm Vào Giỏ Ngay' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled();
});
