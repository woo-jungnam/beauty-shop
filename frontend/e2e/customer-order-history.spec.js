import { test, expect } from '@playwright/test';

const user = {
  id: 7,
  username: 'khachhang',
  fullName: 'Trần Thị Mai',
  email: 'mai.tran@example.test',
  phone: '0987654321',
  roles: ['ROLE_CUSTOMER']
};

const mockOrders = [
  {
    id: 101,
    orderNumber: 'ORD-20261109-001',
    status: 'SHIPPED',
    paymentStatus: 'PAID',
    paymentMethod: 'BANK',
    customerName: 'Trần Thị Mai',
    customerPhone: '0987654321',
    shippingAddress: '123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    carrierName: 'Giao Hàng Tiết Kiệm',
    trackingCode: 'GHTK123456789VN',
    createdAt: '2026-11-09T08:30:00Z',
    deliveredAt: null,
    subTotal: 850000,
    shippingFee: 30000,
    discountAmount: 50000,
    totalAmount: 830000,
    paidAmount: 830000,
    refundedAmount: 0,
    items: [
      {
        id: 501,
        productId: 12,
        variantId: 44,
        sku: 'LRP-ANTHELIOS-50ML',
        productName: 'Kem Chống Nắng La Roche-Posay Anthelios XL',
        variantName: 'Chai 50ml',
        imageUrl: '/images/products/sunscreen.jpg',
        quantity: 2,
        price: 425000,
        discount: 25000
      }
    ],
    spaVisitItems: []
  },
  {
    id: 102,
    orderNumber: 'ORD-20261109-002',
    status: 'PENDING',
    paymentStatus: 'PENDING',
    paymentMethod: 'COD',
    customerName: 'Trần Thị Mai',
    customerPhone: '0987654321',
    shippingAddress: '123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    createdAt: '2026-11-09T09:15:00Z',
    subTotal: 250000,
    shippingFee: 0,
    discountAmount: 0,
    totalAmount: 250000,
    paidAmount: 0,
    items: [
      {
        id: 502,
        productId: 15,
        variantId: 48,
        sku: 'SERUM-B5-30ML',
        productName: 'Serum Phục Hồi Da Hyalu B5',
        variantName: 'Chai 30ml',
        imageUrl: '',
        quantity: 1,
        price: 250000,
        discount: 0
      }
    ]
  }
];

test('Customer Order History displays detailed product information and related order information', async ({ page }) => {
  await page.addInitScript(profile => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(profile));
  }, user);

  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data = [];

    if (path.endsWith('/auth/profile') || path.endsWith('/users/profile')) {
      data = user;
    } else if (path.endsWith('/orders/my-orders')) {
      data = {
        content: mockOrders,
        totalElements: mockOrders.length,
        totalPages: 1,
        page: 0
      };
    } else if (path.endsWith('/orders/101')) {
      data = mockOrders[0];
    } else if (path.endsWith('/orders/101/returns')) {
      data = [];
    } else if (path.endsWith('/my-service-orders')) {
      data = { content: [], totalElements: 0, totalPages: 1 };
    } else if (path.endsWith('/users/order-statistics')) {
      data = { totalOrders: 2, totalPaid: 830000 };
    } else if (path.endsWith('/users/notifications')) {
      data = { content: [] };
    }

    await route.fulfill({ json: { status: 200, success: true, data } });
  });

  await page.goto('/#/profile?tab=orders');

  // Verify page heading
  await expect(page.getByRole('heading', { name: 'Lịch sử đơn hàng của tôi' })).toBeVisible();

  // Verify filter tabs
  await expect(page.getByRole('button', { name: 'Tất cả' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Chờ xác nhận' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đang xử lý / Vận chuyển' })).toBeVisible();

  // Verify first order details
  await expect(page.getByText('ORD-20261109-001')).toBeVisible();
  await expect(page.getByText('Kem Chống Nắng La Roche-Posay Anthelios XL')).toBeVisible();
  await expect(page.getByText('Phân loại: Chai 50ml')).toBeVisible();
  await expect(page.getByText('LRP-ANTHELIOS-50ML')).toBeVisible();

  // Verify shipping and carrier info
  await expect(page.getByText('Giao Hàng Tiết Kiệm')).toBeVisible();
  await expect(page.getByText('GHTK123456789VN')).toBeVisible();
  await expect(page.getByText('123 Đường Nguyễn Huệ').first()).toBeVisible();

  // Verify order detail modal
  const firstDetailBtn = page.getByRole('button', { name: 'Chi tiết & thao tác' }).first();
  await firstDetailBtn.click();

  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Danh sách sản phẩm trong đơn (1)')).toBeVisible();
  await expect(dialog.getByText('Kem Chống Nắng La Roche-Posay Anthelios XL')).toBeVisible();
  await expect(dialog.getByText('Chai 50ml')).toBeVisible();
  await expect(dialog.getByText('LRP-ANTHELIOS-50ML')).toBeVisible();
  await expect(dialog.getByText('GHTK123456789VN')).toBeVisible();

  // Check financial breakdown in modal
  await expect(dialog.getByText('Voucher giảm giá:')).toBeVisible();
  await expect(dialog.getByText('Phí vận chuyển:')).toBeVisible();
  await expect(dialog.getByText('Tổng thanh toán:')).toBeVisible();

  // Close modal with Escape
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);

  // Test filter tab: "Chờ xác nhận"
  await page.getByRole('button', { name: 'Chờ xác nhận' }).click();
  await expect(page.getByText('ORD-20261109-002')).toBeVisible();
  await expect(page.getByText('ORD-20261109-001')).toHaveCount(0);
});
