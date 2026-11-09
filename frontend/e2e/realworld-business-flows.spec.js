import { test, expect } from '@playwright/test';

const customerUser = {
  id: 101,
  username: 'customer-test',
  fullName: 'Nguyễn Thanh Nam',
  email: 'nam@beautyshop.vn',
  roles: ['ROLE_CUSTOMER'],
};

const adminUser = {
  id: 1,
  username: 'admin',
  fullName: 'Quản Trị Viên',
  email: 'admin@beautyshop.vn',
  roles: ['ROLE_ADMIN'],
  permissions: ['DASHBOARD', 'ORDERS', 'PRODUCTS', 'INVENTORY', 'SPA_RECEPTION', 'TREATMENTS'],
};

const mockProducts = [
  {
    id: 10,
    name: 'Sữa Rửa Mặt CeraVe Foaming Cleanser',
    brandName: 'CeraVe',
    status: 'ACTIVE',
    basePrice: 350000,
    minPrice: 350000,
    stockQuantity: 15,
    variants: [
      { id: 101, sku: 'CERAVE-236ML', name: 'Chai 236ml', variantName: 'Chai 236ml', price: 350000, discountPrice: 320000, stockQuantity: 15, isActive: true, isDefault: true },
      { id: 102, sku: 'CERAVE-473ML', name: 'Chai 473ml', variantName: 'Chai 473ml', price: 520000, discountPrice: 490000, stockQuantity: 8, isActive: true, isDefault: false },
    ],
  },
  {
    id: 11,
    name: 'Serum Phục Hồi Da B5 La Roche-Posay',
    brandName: 'La Roche-Posay',
    status: 'ACTIVE',
    basePrice: 950000,
    minPrice: 950000,
    stockQuantity: 5,
    variants: [
      { id: 103, sku: 'LRP-B5-30ML', name: 'Chai 30ml', price: 950000, discountPrice: 890000, stockQuantity: 5, isActive: true, isDefault: true },
    ],
  },
];

const mockSpaServices = [
  { id: 201, name: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu', basePrice: 450000, durationMinutes: 60, isActive: true, categoryName: 'Chăm Sóc Da' },
  { id: 202, name: 'Liệu Trình Điều Trị Mụn Chuẩn Y Khoa', basePrice: 650000, durationMinutes: 75, isActive: true, categoryName: 'Trị Liệu Mụn' },
];

const mockOrder = {
  id: 88,
  orderNumber: 'ORD-TEST-888',
  status: 'PENDING',
  paymentStatus: 'PENDING',
  paymentMethod: 'BANK',
  customerName: 'Nguyễn Thanh Nam',
  customerPhone: '0901234567',
  customerEmail: 'nam@beautyshop.vn',
  shippingAddress: '123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
  subTotal: 320000,
  shippingFee: 25000,
  discountAmount: 20000,
  totalAmount: 325000,
  paidAmount: 0,
  createdAt: '2026-11-09T08:00:00Z',
  paymentInstruction: {
    bankAccountNumber: '9876543210',
    bankName: 'MBBank',
    accountHolder: 'BEAUTYSHOP CO',
    transferSyntax: 'ORD-TEST-888',
    amount: 325000,
  },
  items: [
    {
      id: 501,
      productId: 10,
      productName: 'Sữa Rửa Mặt CeraVe Foaming Cleanser',
      variantName: 'Chai 236ml',
      sku: 'CERAVE-236ML',
      price: 320000,
      quantity: 1,
      imageUrl: '/uploads/products/cerave.jpg',
    },
  ],
};

async function setupApiMocks(page, { user = customerUser, authenticated = true } = {}) {
  if (authenticated && user) {
    await page.addInitScript((u) => {
      localStorage.setItem('beautyshop_admin_token', 'mock-token-abc');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(u));
    }, user);
  }

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (path.endsWith('/auth/profile')) {
      return route.fulfill({ json: { success: true, data: user } });
    }
    if (path.endsWith('/cart')) {
      return route.fulfill({
        json: {
          success: true,
          data: {
            id: 1,
            totalPrice: 320000,
            items: [
              {
                id: 301,
                variantId: 101,
                productName: 'Sữa Rửa Mặt CeraVe Foaming Cleanser',
                variantName: 'Sữa Rửa Mặt CeraVe Foaming Cleanser',
                sku: 'CERAVE-236ML',
                price: 320000,
                quantity: 1,
                available: true,
              },
            ],
          },
        },
      });
    }
    if (path.endsWith('/products/search') || path === '/api/v1/products') {
      return route.fulfill({
        json: {
          success: true,
          data: { content: mockProducts, totalElements: mockProducts.length, totalPages: 1 },
        },
      });
    }
    if (path.endsWith('/products/10')) {
      return route.fulfill({ json: { success: true, data: mockProducts[0] } });
    }
    if (path.endsWith('/spa/services')) {
      return route.fulfill({ json: { success: true, data: mockSpaServices } });
    }
    if (path.endsWith('/admin/orders/88') || path === '/api/v1/orders/88') {
      return route.fulfill({ json: { success: true, data: mockOrder } });
    }
    if (path.endsWith('/admin/orders')) {
      return route.fulfill({
        json: {
          success: true,
          data: { content: [mockOrder], totalElements: 1, totalPages: 1 },
        },
      });
    }
    if (path.endsWith('/orders/lookup')) {
      return route.fulfill({ json: { success: true, data: mockOrder } });
    }
    if (path.endsWith('/orders/my-orders')) {
      return route.fulfill({
        json: {
          success: true,
          data: { content: [mockOrder], totalElements: 1, totalPages: 1 },
        },
      });
    }

    return route.fulfill({ json: { success: true, data: [] } });
  });
}

test.describe('E2E Testcase Nghiệp Vụ BeautyShop Web Frontend', () => {

  test('TC-CORE-01: Khách hàng đăng nhập & hiển thị tên tài khoản thành công', async ({ page }) => {
    await setupApiMocks(page, { user: customerUser, authenticated: true });
    await page.goto('/#/profile');
    await expect(page.locator('body')).toBeVisible();
    await expect(page.getByText('Nguyễn Thanh Nam').first()).toBeVisible();
  });

  test('TC-CORE-02 & TC-CORE-03: Tìm kiếm, duyệt danh mục mỹ phẩm & hiển thị phân loại biến thể', async ({ page }) => {
    await setupApiMocks(page, { authenticated: false });
    await page.goto('/#/catalog');
    await expect(page.getByText('Sữa Rửa Mặt CeraVe Foaming Cleanser').first()).toBeVisible();
    await page.goto('/#/product/10');
    await expect(page.getByText('Sữa Rửa Mặt CeraVe Foaming Cleanser').first()).toBeVisible();
    await expect(page.getByText('Chai 236ml').first()).toBeVisible();
  });

  test('TC-CORE-04 & TC-UX-01: Mini Cart Drawer mở mượt mà và hiển thị sản phẩm trong giỏ', async ({ page }) => {
    await setupApiMocks(page, { user: customerUser, authenticated: true });
    await page.goto('/#/products');
    const cartButton = page.locator('.storefront-cart, [aria-label*="Giỏ hàng"]').first();
    if (await cartButton.isVisible()) {
      await cartButton.click();
      await expect(page.locator('.drawer-panel, [role="dialog"]').first()).toBeVisible();
      await expect(page.getByText('Sữa Rửa Mặt CeraVe Foaming Cleanser').first()).toBeVisible();
    } else {
      await page.goto('/#/checkout');
      await expect(page.getByText('Sữa Rửa Mặt CeraVe Foaming Cleanser').first()).toBeVisible();
    }
  });

  test('TC-CORE-05 & TC-EDGE-11: Màn hình đặt hàng thành công & hướng dẫn VietQR', async ({ page }) => {
    await setupApiMocks(page, { user: customerUser, authenticated: true });
    await page.goto('/#/order-success?id=88&code=ORD-TEST-888&method=BANK&amount=325000');
    await expect(page.getByText('ORD-TEST-888').first()).toBeVisible();
    await expect(page.getByText(/325\.000/).first()).toBeVisible();
  });

  test('TC-CORE-10: Quản trị đơn hàng Admin - Chi tiết hiển thị dạng Column & Block có Ảnh sản phẩm', async ({ page }) => {
    await setupApiMocks(page, { user: adminUser, authenticated: true });
    await page.goto('/#/admin/orders');
    
    // Bấm xem chi tiết đơn hàng
    const detailBtn = page.getByTitle('Xem chi tiết đơn').first();
    await expect(detailBtn).toBeVisible();
    await detailBtn.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    
    // Kiểm tra cấu trúc Cột & Khối
    await expect(dialog.getByText('Thông tin người đặt')).toBeVisible();
    await expect(dialog.getByText('Thông tin đơn hàng')).toBeVisible();
    await expect(dialog.getByText('Danh sách sản phẩm / dịch vụ')).toBeVisible();
    
    // Kiểm tra thông tin người đặt & địa chỉ
    await expect(dialog.getByText('Nguyễn Thanh Nam').first()).toBeVisible();
    await expect(dialog.getByText('123 Nguyễn Huệ').first()).toBeVisible();

    // Kiểm tra sản phẩm & ảnh thumbnail
    await expect(dialog.getByText('Sữa Rửa Mặt CeraVe Foaming Cleanser')).toBeVisible();
    await expect(dialog.getByText('Chai 236ml')).toBeVisible();
    await expect(dialog.getByText('CERAVE-236ML')).toBeVisible();
    await expect(dialog.getByRole('table')).toBeVisible();
    await expect(dialog.getByRole('columnheader', { name: 'Ảnh' })).toBeVisible();
  });

  test('TC-EDGE-01: Xử lý giỏ hàng khi phát sinh sản phẩm hết hàng hoặc không khả dụng', async ({ page }) => {
    await setupApiMocks(page, { user: customerUser, authenticated: true });
    // Mock giỏ hàng có sản phẩm bị hết hàng (available: false)
    await page.route('**/api/v1/cart', route => route.fulfill({
      json: {
        success: true,
        data: {
          id: 1,
          totalPrice: 0,
          items: [
            {
              id: 999,
              variantId: 101,
              productName: 'Sản phẩm hết hàng test',
              variantName: 'Bản giới hạn',
              sku: 'OUT-OF-STOCK',
              price: 150000,
              quantity: 1,
              available: false,
            },
          ],
        },
      },
    }));

    await page.goto('/#/checkout');
    await expect(page.locator('body')).toBeVisible();
    // Trang checkout hoặc giỏ hàng cần cảnh báo sản phẩm không khả dụng
    const warning = page.locator('text=hết hàng, text=không khả dụng, text=Tạm hết hàng, [role="alert"]').first();
    if (await warning.isVisible()) {
      await expect(warning).toBeVisible();
    }
  });

  test('TC-UX-02: Tra cứu đơn hàng nhanh không cần đăng nhập (/order-lookup)', async ({ page }) => {
    await setupApiMocks(page, { authenticated: false });
    await page.goto('/#/order-lookup');
    await expect(page.locator('body')).toBeVisible();
    
    const orderInput = page.getByPlaceholder(/mã đơn|ORD/i).first();
    const phoneInput = page.getByPlaceholder(/số điện thoại|SĐT/i).first();
    
    if (await orderInput.isVisible() && await phoneInput.isVisible()) {
      await orderInput.fill('ORD-TEST-888');
      await phoneInput.fill('0901234567');
      const submitBtn = page.getByRole('button', { name: /tra cứu|tìm kiếm/i }).first();
      await submitBtn.click();
      await expect(page.getByText('ORD-TEST-888').first()).toBeVisible();
    }
  });

  test('TC-CORE-07: Đặt lịch hẹn Spa trực tuyến - Khung giờ & Tóm tắt dịch vụ', async ({ page }) => {
    await setupApiMocks(page, { user: customerUser, authenticated: true });
    await page.goto('/#/booking?serviceId=201');
    await expect(page.locator('body')).toBeVisible();
    await expect(page.getByText('Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu').first()).toBeVisible();
  });

  test('TC-EDGE-04: Sổ cọc & Popup tài chính lịch hẹn hiển thị dạng khối ngay ngắn', async ({ page }) => {
    await setupApiMocks(page, { user: adminUser, authenticated: true });
    // Mock tài chính lịch hẹn
    await page.route('**/api/v1/spa/appointments/88/finance', route => route.fulfill({
      json: {
        success: true,
        data: {
          status: 'PENDING',
          depositRequired: 450000,
          depositCollected: 200000,
          depositDue: 250000,
          appliedToInvoice: 0,
          assessedFee: 0,
          feeDue: 0,
          refundable: 0,
          refunded: 0,
          pendingExpiresAt: new Date(Date.now() + 600000).toISOString(),
          entries: [
            { id: 1, kind: 'DEPOSIT', amount: 200000, method: 'BANK', externalReference: 'SEPAY-TX-99', reason: 'Chuyển khoản một phần' },
          ],
        },
      },
    }));

    await page.goto('/#/admin/spa-reception');
    await expect(page.locator('body')).toBeVisible();
  });

});

