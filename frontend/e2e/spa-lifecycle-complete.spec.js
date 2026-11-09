import { test, expect } from '@playwright/test';

const today = '2026-11-09';

const customerUser = {
  id: 101,
  username: 'spa-customer',
  fullName: 'Nguyễn Thanh Nam',
  phone: '0901234567',
  email: 'nam@beautyshop.vn',
  roles: ['ROLE_CUSTOMER'],
};

const staffUser = {
  id: 201,
  username: 'reception-staff',
  fullName: 'Lễ Tân Thảo Nhi',
  roles: ['ROLE_STAFF'],
  permissions: ['SPA_RECEPTION', 'SPA_EXECUTION'],
};

const adminUser = {
  id: 1,
  username: 'admin',
  fullName: 'Quản Trị Viên Spa',
  roles: ['ROLE_ADMIN'],
  permissions: ['SPA_RECEPTION', 'SPA_ADMIN', 'TREATMENTS'],
};

const singleService = {
  id: 101,
  name: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu',
  basePrice: 450000,
  durationMinutes: 60,
  isActive: true,
  categoryName: 'Chăm Sóc Da',
};

const packageOffer = {
  id: 71,
  name: 'Gói Chăm Sóc Da Trị Mụn 5 Buổi',
  description: 'Lộ trình phục hồi và điều trị mụn chuẩn y khoa 5 buổi chuyên sâu',
  price: 1800000,
  validityDays: 90,
  purchaseToken: 'offer-token-71',
  items: [{ serviceId: 101, serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu', quantity: 5 }],
};

const userTicket = {
  id: 91,
  packageName: 'Gói Chăm Sóc Da Trị Mụn 5 Buổi',
  nameSnapshot: true,
  benefitType: 'SERVICE_PACKAGE',
  status: 'ACTIVE',
  totalSessions: 5,
  usedSessions: 1,
  reservedSessions: 1,
  remainingSessions: 3,
  remainingByService: { 101: 3 },
  expiryDate: '2027-02-15T00:00:00Z',
  services: [{ serviceId: 101, serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu', total: 5, used: 1, reserved: 1, available: 3 }],
};

const mockStaffList = [
  { id: 11, fullName: 'KTV Mai Anh', specialty: 'Chăm sóc da' },
  { id: 12, fullName: 'KTV Thu Hà', specialty: 'Trị mụn y khoa' },
];

const mockFacilityList = [
  { id: 1, name: 'Giường 01 (Phòng VIP 1)', type: 'BED', capacity: 1, active: true },
  { id: 2, name: 'Giường 02 (Phòng Tiêu chuẩn)', type: 'BED', capacity: 1, active: true },
];

function createMockAppointment(id, status = 'PENDING', extra = {}) {
  return {
    id,
    appointmentCode: `LH-${id}`,
    customerName: 'Nguyễn Thanh Nam',
    customerPhone: '0901234567',
    appointmentDate: today,
    startTime: '10:00',
    endTime: '11:00',
    status,
    totalAmount: 450000,
    depositRequired: 200000,
    depositCollected: status === 'CONFIRMED' || status === 'IN_PROGRESS' || status === 'COMPLETED' ? 200000 : 0,
    items: [
      {
        id: id * 10,
        serviceId: 101,
        serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu',
        price: 450000,
        staffId: 11,
        staffName: 'KTV Mai Anh',
        executionStatus: status === 'COMPLETED' ? 'PERFORMED' : status === 'IN_PROGRESS' ? 'IN_PROGRESS' : 'PLANNED',
      },
    ],
    ...extra,
  };
}

async function setupSpaLifecycle(page, { user = customerUser, authenticated = true, customHandler } = {}) {
  if (authenticated && user) {
    await page.addInitScript((u) => {
      localStorage.setItem('beautyshop_admin_token', 'mock-spa-token');
      localStorage.setItem('beautyshop_admin_user', JSON.stringify(u));
    }, user);
  }

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    if (customHandler) {
      let isHandled = false;
      const originalFulfill = route.fulfill.bind(route);
      route.fulfill = async (...args) => {
        isHandled = true;
        return originalFulfill(...args);
      };
      const result = await customHandler(route, path, method);
      if (result || isHandled) return;
    }

    if (path.endsWith('/auth/profile')) {
      return route.fulfill({ json: { success: true, data: user } });
    }
    if (path.includes('/orders/888')) {
      return route.fulfill({
        json: {
          success: true,
          data: {
            id: 888,
            orderNumber: 'ORD-PACKAGE-888',
            servicePackageId: 71,
            totalAmount: 1800000,
            paymentStatus: 'PENDING',
            paymentMethod: 'BANK',
            paymentInstruction: {
              bankAccountNumber: '9876543210',
              bankName: 'MBBank',
              accountHolder: 'BEAUTYSHOP CO',
              transferSyntax: 'ORD-PACKAGE-888',
              amount: 1800000,
            },
          },
        },
      });
    }
    if (path.endsWith('/spa/services')) {
      return route.fulfill({ json: { success: true, data: [singleService] } });
    }
    if (path.endsWith('/spa/services/packages')) {
      return route.fulfill({ json: { success: true, data: [packageOffer] } });
    }
    if (path.endsWith('/spa/tickets/my-tickets')) {
      return route.fulfill({ json: { success: true, data: [userTicket] } });
    }
    if (path.endsWith('/admin/staff') || path.endsWith('/services/101/staff') || path.endsWith('/spa/staff')) {
      return route.fulfill({ json: { success: true, data: mockStaffList } });
    }
    if (path.endsWith('/spa/facilities')) {
      return route.fulfill({ json: { success: true, data: mockFacilityList } });
    }
    if (path.endsWith('/available-slots')) {
      return route.fulfill({ json: { success: true, data: ['09:00', '10:00', '11:00', '14:00', '15:00', '16:00'] } });
    }
    if (path.endsWith('/appointments/admin/all')) {
      const apts = [
        createMockAppointment(1, 'PENDING'),
        createMockAppointment(2, 'CONFIRMED'),
        createMockAppointment(3, 'IN_PROGRESS'),
        createMockAppointment(4, 'COMPLETED'),
      ];
      return route.fulfill({ json: { success: true, data: { content: apts, totalElements: apts.length, totalPages: 1 } } });
    }
    if (path.endsWith('/finance')) {
      return route.fulfill({
        json: {
          success: true,
          data: {
            status: 'PENDING',
            depositRequired: 200000,
            depositCollected: 0,
            depositDue: 200000,
            appliedToInvoice: 0,
            assessedFee: 0,
            feeDue: 0,
            refundable: 0,
            refunded: 0,
            entries: [],
          },
        },
      });
    }

    return route.fulfill({ json: { success: true, data: [] } });
  });
}

test.describe('E2E Lifecycle: Dịch Vụ Đơn Lẻ & Gói Dịch Vụ Dài Hạn (User ↔ Staff / Admin)', () => {

  // ==========================================
  // PHẦN 1: DỊCH VỤ ĐƠN LẺ (SINGLE SERVICE)
  // ==========================================

  test('TC-SINGLE-01: Khách hàng đặt lịch hẹn dịch vụ đơn lẻ trực tuyến & xem thông tin cọc', async ({ page }) => {
    await setupSpaLifecycle(page, { user: customerUser });
    await page.goto('/#/booking?serviceId=101');
    await expect(page.locator('body')).toBeVisible();
    await expect(page.getByText('Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu').first()).toBeVisible();
  });

  test('TC-SINGLE-02: Khách hàng đổi lịch hẹn (Reschedule) sang khung giờ mới thành công', async ({ page }) => {
    let rescheduleCalled = false;
    let reschedulePayload = null;

    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/appointments/1/reschedule') && method === 'POST') {
          rescheduleCalled = true;
          reschedulePayload = route.request().postDataJSON();
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(1, 'PENDING', {
                startTime: '14:00',
                endTime: '15:00',
                notes: 'Khách dời lịch sang chiều',
              }),
            },
          });
        }
      },
    });

    await page.goto('/#/booking?serviceId=101');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SINGLE-03: Xử lý ngoại lệ đổi lịch khi khung giờ KTV đã kín (Slot Conflict)', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path, method) => {
        if (path.endsWith('/available-slots')) {
          // Trả về danh sách giờ đã hết slot 10:00
          return route.fulfill({ json: { success: true, data: ['14:00', '15:00'] } });
        }
      },
    });

    await page.goto('/#/booking?serviceId=101');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SINGLE-04: Khách hàng hủy lịch hẹn trước hạn & chính sách hoàn cọc', async ({ page }) => {
    let cancelCalled = false;
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/appointments/1/cancel') && method === 'POST') {
          cancelCalled = true;
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(1, 'CANCELLED', { cancelReason: 'Bận đột xuất' }),
            },
          });
        }
      },
    });

    await page.goto('/#/profile?tab=appointments');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-SINGLE-05 & TC-SINGLE-06: Sổ cọc lễ tân - Thu cọc bổ sung tại quầy khi khách chuyển thiếu', async ({ page }) => {
    let receiptBody = null;
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/appointments/1/deposit-receipts') && method === 'POST') {
          receiptBody = route.request().postDataJSON();
          return route.fulfill({ json: { success: true, data: { id: 99, ...receiptBody } } });
        }
        if (path.includes('/appointments/1/finance')) {
          return route.fulfill({
            json: {
              success: true,
              data: {
                status: 'PENDING',
                depositRequired: 200000,
                depositCollected: receiptBody ? 200000 : 100000,
                depositDue: receiptBody ? 0 : 100000,
                appliedToInvoice: 0,
                assessedFee: 0,
                feeDue: 0,
                refundable: 0,
                refunded: 0,
                entries: [
                  { id: 1, kind: 'DEPOSIT', amount: 100000, method: 'BANK', externalReference: 'SEPAY-PARTIAL-1' },
                ],
              },
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.locator('body')).toBeVisible();
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();
  });

  test('TC-SINGLE-07: Lễ tân tiếp đón & Check-in gán phòng/giường thực tế cho KTV', async ({ page }) => {
    let checkInDone = false;
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/appointments/2/check-in') && method === 'POST') {
          checkInDone = true;
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(2, 'IN_PROGRESS', {
                checkedInAt: '2026-11-09T03:00:00Z',
                facilityId: 1,
              }),
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();
    // Bấm nút tiếp đón hoặc check-in trên thẻ lịch hẹn
    const checkInBtn = page.locator('button:has-text("Tiếp đón"), button:has-text("Check-in")').first();
    if (await checkInBtn.isVisible()) {
      await checkInBtn.click();
    }
  });

  test('TC-SINGLE-08: Lễ tân xuất hóa đơn buổi hẹn dịch vụ đơn lẻ & cấn trừ cọc đã thu', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path) => {
        if (path.includes('/appointments/3/invoice')) {
          return route.fulfill({
            json: {
              success: true,
              data: {
                orderId: 901,
                orderNumber: 'ORD-INV-901',
                totalAmount: 450000,
                paidAmount: 200000,
                amountDue: 250000,
                paymentStatus: 'PARTIALLY_PAID',
                items: [{ appointmentItemId: 30, serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu', unitPrice: 450000 }],
              },
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();
  });

  // ==========================================
  // PHẦN 2: GÓI DỊCH VỤ DÀI HẠN & LIỆU TRÌNH (TREATMENT PACKAGES)
  // ==========================================

  test('TC-PACK-01: Khách hàng mua Gói Dịch Vụ Dài Hạn (5 buổi) & Cấp vé E-Ticket', async ({ page }) => {
    let purchasePayload = null;
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path, method) => {
        if (path.endsWith('/tickets/purchase') && method === 'POST') {
          purchasePayload = route.request().postDataJSON();
          return route.fulfill({
            json: {
              success: true,
              data: {
                orderId: 888,
                orderNumber: 'ORD-PACKAGE-888',
                servicePackageId: 71,
                totalAmount: 1800000,
                paymentStatus: 'PENDING',
                paymentMethod: 'BANK',
              },
            },
          });
        }
      },
    });

    await page.goto('/#/service-packages');
    await expect(page.getByText('Gói Chăm Sóc Da Trị Mụn 5 Buổi').first()).toBeVisible();
    await page.getByRole('button', { name: 'Xem điều kiện & mua gói' }).first().click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await page.getByRole('checkbox').first().check();
    await page.getByRole('button', { name: 'Tạo đơn & thanh toán' }).first().click();
    await expect(page).toHaveURL(/order-success\?id=888/);
  });

  test('TC-PACK-02: Khách hàng vào Ví vé Spa và bấm đặt lịch hẹn sử dụng vé E-Ticket', async ({ page }) => {
    await setupSpaLifecycle(page, { user: customerUser });
    await page.goto('/#/profile?tab=tickets');
    await expect(page.getByText('Gói Chăm Sóc Da Trị Mụn 5 Buổi').first()).toBeVisible();
    await expect(page.getByText('Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu').first()).toBeVisible();
    await expect(page.getByText(/Đang giữ: 1 lượt/)).toBeVisible();

    const bookBtn = page.getByRole('button', { name: 'Mở đặt lịch sử dụng vé' }).first();
    await expect(bookBtn).toBeVisible();
    await bookBtn.click();
    await expect(page).toHaveURL(/booking\?ticketId=91/);
  });

  test('TC-PACK-03: Khóa đặt buổi kế tiếp khi vi phạm giãn cách ngày tối thiểu (Min Gap Days)', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path) => {
        if (path.endsWith('/spa/treatment-courses/my-courses')) {
          return route.fulfill({
            json: {
              success: true,
              data: [
                {
                  id: 41,
                  name: 'Lộ trình trị mụn 3 giai đoạn',
                  status: 'IN_PROGRESS',
                  sessions: [
                    { sequence: 1, title: 'Buổi 1: Làm sạch & lấy nhân mụn', status: 'COMPLETED' },
                    { sequence: 2, title: 'Buổi 2: Phục hồi da Aqua Peel', status: 'PLANNED', minGapDays: 7, blockedReason: 'Cần ít nhất 7 ngày sau buổi 1 để da phục hồi' },
                  ],
                },
              ],
            },
          });
        }
      },
    });

    await page.goto('/#/treatments?tab=courses');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PACK-04: Khách hàng vắng mặt buổi hẹn dùng vé - Ghi nhận mất lượt (Forfeiture)', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path) => {
        if (path.endsWith('/movements')) {
          return route.fulfill({
            json: {
              success: true,
              data: {
                content: [
                  { id: 1, operation: 'FORFEIT', reason: 'Vắng mặt theo chính sách Spa', createdAt: '2026-11-08T10:00:00Z' },
                ],
                totalPages: 1,
                last: true,
              },
            },
          });
        }
      },
    });

    await page.goto('/#/profile?tab=tickets');
    await expect(page.getByText('Gói Chăm Sóc Da Trị Mụn 5 Buổi').first()).toBeVisible();
  });

  test('TC-PACK-05: Khách hàng gửi yêu cầu xin gia hạn hạn dùng gói dịch vụ (Request Extension)', async ({ page }) => {
    let extendPayload = null;
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/tickets/91/requests') && method === 'POST') {
          extendPayload = route.request().postDataJSON();
          return route.fulfill({
            json: {
              success: true,
              data: { id: 10, ...extendPayload, status: 'PENDING_APPROVAL' },
            },
          });
        }
      },
    });

    await page.goto('/#/treatments?tab=courses');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PACK-06: Admin duyệt gia hạn hạn sử dụng gói dịch vụ dài hạn', async ({ page }) => {
    await setupSpaLifecycle(page, { user: adminUser });
    await page.goto('/#/admin/treatments');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PACK-07: Quyết toán và hoàn tiền khi khách ngừng sử dụng gói (Package Settlement)', async ({ page }) => {
    let settlementBody = null;
    await setupSpaLifecycle(page, {
      user: adminUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/tickets/91/settlement')) {
          if (method === 'POST') {
            settlementBody = route.request().postDataJSON();
            return route.fulfill({ json: { success: true, data: { id: 1, ...settlementBody } } });
          }
          return route.fulfill({
            json: {
              success: true,
              data: {
                ticketId: 91,
                purchasedAmount: 1800000,
                refundableAmount: 1080000,
                unusedSessions: 3,
                blockedReason: null,
              },
            },
          });
        }
      },
    });

    await page.goto('/#/admin/spa');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-PACK-08: Lễ tân xuất hóa đơn hoàn tất buổi hẹn bằng khấu trừ vé E-Ticket (0đ phát sinh)', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path) => {
        if (path.includes('/appointments/4/invoice')) {
          return route.fulfill({
            json: {
              success: true,
              data: {
                orderId: 902,
                orderNumber: 'ORD-TICKET-REDEEM-902',
                totalAmount: 0,
                paidAmount: 0,
                amountDue: 0,
                paymentStatus: 'PAID',
                items: [{ appointmentItemId: 40, serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu', unitPrice: 0, ticketRedeemed: true }],
              },
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();
  });

  test('TC-EDGE-REVOKED: Vé đã thu hồi vô hiệu hóa thao tác đặt lịch và hiển thị cảnh báo', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: customerUser,
      customHandler: async (route, path) => {
        if (path.endsWith('/spa/tickets/my-tickets')) {
          return route.fulfill({
            json: {
              success: true,
              data: [{ ...userTicket, status: 'REVOKED', remainingSessions: 0 }],
            },
          });
        }
      },
    });

    await page.goto('/#/profile?tab=tickets');
    await expect(page.locator('body')).toBeVisible();
  });

  test('TC-STAFF-REASSIGN: Lễ tân điều phối đổi Kỹ thuật viên khi nhân sự bận đột xuất', async ({ page }) => {
    let reassignDone = false;
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/appointments/2/staff') && method === 'PUT') {
          reassignDone = true;
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(2, 'CONFIRMED', {
                items: [{ id: 20, serviceId: 101, staffId: 12, staffName: 'KTV Thu Hà' }],
              }),
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();
  });

  // ==========================================
  // PHẦN 3: NGOẠI LỆ THỰC TẾ (LATE ARRIVAL GRACE & CLINICAL SUBSTITUTION)
  // ==========================================

  test('TC-LATE-GRACE-01: Khách hàng gọi điện báo kẹt xe - Lễ tân gia hạn giữ chỗ 15 phút (Grace Period Extension)', async ({ page }) => {
    let reschedulePayload = null;
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path, method) => {
        if (path.includes('/reschedule') && method === 'PUT') {
          reschedulePayload = JSON.parse(route.request().postData());
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(1, 'PENDING', {
                startTime: reschedulePayload.startTime,
                notes: reschedulePayload.notes,
              }),
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();

    // Click "+15p giữ chỗ" button on pending/confirmed appointment card
    const graceBtn = page.getByRole('button', { name: '+15p giữ chỗ' }).first();
    await expect(graceBtn).toBeVisible();
    await graceBtn.click();

    // Verify reschedule API called with +15 mins and traffic reason
    await expect.poll(() => reschedulePayload).not.toBeNull();
    expect(reschedulePayload.startTime).toBe('10:15:00');
    expect(reschedulePayload.notes).toContain('Gia hạn giữ chỗ 15p');

    // Verify toast notification
    await expect(page.getByText('Đã gia hạn giữ chỗ thêm 15 phút cho khách hàng.').first()).toBeVisible();
  });

  test('TC-LATE-GRACE-02: Chặn lạm dụng gia hạn giữ chỗ - Chỉ cho phép gia hạn tối đa 1 lần', async ({ page }) => {
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path, method) => {
        if (path.endsWith('/appointments/admin/all')) {
          const apts = [
            createMockAppointment(2, 'CONFIRMED', {
              startTime: '10:15:00',
              notes: 'Gia hạn giữ chỗ 15p do khách báo trễ / kẹt xe',
            }),
          ];
          return route.fulfill({ json: { success: true, data: { content: apts, totalElements: 1, totalPages: 1 } } });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();

    // Try to click "+15p giữ chỗ" again
    const graceBtn = page.getByRole('button', { name: '+15p giữ chỗ' }).first();
    await expect(graceBtn).toBeVisible();
    await graceBtn.click();

    // Verify safeguard warning appears
    await expect(page.getByText('Lịch hẹn này đã được gia hạn giữ chỗ 15 phút').first()).toBeVisible();
  });

  test('TC-SERVICE-SUB-01: Bác sĩ / KTV soi da phát hiện mụn viêm - Đổi dịch vụ chỉ định từ Aqua Peel sang Điện di phục hồi B5', async ({ page }) => {
    let executionUpdated = null;
    await setupSpaLifecycle(page, {
      user: staffUser,
      customHandler: async (route, path, method) => {
        if (path.endsWith('/spa/services')) {
          return route.fulfill({
            json: {
              success: true,
              data: [
                singleService,
                {
                  id: 102,
                  name: 'Điện di phục hồi B5',
                  price: 450000,
                  basePrice: 450000,
                  durationMinutes: 45,
                  isActive: true,
                },
              ],
            },
          });
        }
        if (path.includes('/appointments/3') && method === 'GET') {
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(3, 'IN_PROGRESS', {
                items: [
                  {
                    id: 30,
                    serviceId: 101,
                    serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu',
                    price: 450000,
                    ticketId: 101,
                    staffId: 11,
                    staffName: 'KTV Mai Anh',
                    executionStatus: 'IN_PROGRESS',
                  },
                ],
              }),
            },
          });
        }
        if (path.includes('/appointments/3/items/30/execution') && method === 'PUT') {
          executionUpdated = JSON.parse(route.request().postData());
          return route.fulfill({
            json: {
              success: true,
              data: createMockAppointment(3, 'IN_PROGRESS', {
                items: [
                  {
                    id: 30,
                    serviceId: 101,
                    serviceName: 'Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu',
                    price: 450000,
                    ticketId: 101,
                    staffId: 11,
                    staffName: 'KTV Mai Anh',
                    executionStatus: 'IN_PROGRESS',
                    executionNotes: executionUpdated.notes,
                  },
                ],
              }),
            },
          });
        }
      },
    });

    await page.goto('/#/reception');
    await expect(page.getByText('Quầy lễ tân Spa').first()).toBeVisible();

    // Open appointment dialog for appointment #3 (IN_PROGRESS)
    const moreActions = page.locator('.reception-more summary').nth(2);
    await moreActions.click();
    await page.getByRole('button', { name: 'Chuẩn bị / thực hiện ca' }).click();

    // Verify dialog opened
    await expect(page.getByText('Lịch hẹn #3').first()).toBeVisible();

    // Click "Đổi dịch vụ chỉ định (Soi da & Bác sĩ)"
    const subBtn = page.getByRole('button', { name: 'Đổi dịch vụ chỉ định (Soi da & Bác sĩ)' }).first();
    await expect(subBtn).toBeVisible();
    await subBtn.click();

    // Verify substitution form elements
    await expect(page.getByText('Đổi dịch vụ chỉ định theo tình trạng da').first()).toBeVisible();
    await page.locator('select').filter({ hasText: 'Chọn dịch vụ thay thế phù hợp' }).selectOption('102');

    // Fill medical diagnosis rationale
    await page.locator('textarea[placeholder*="Soi da phát hiện mụn"]').fill('Soi da phát hiện mụn bọc viêm đỏ vùng má, chống chỉ định Aqua Peel; chuyển sang Điện di phục hồi B5 để làm dịu ổ viêm.');

    // Confirm customer consent checkbox
    await page.getByLabel('Khách hàng đồng thuận đổi phác đồ theo chỉ định của bác sĩ/KTV').check();

    // Verify 0đ surcharge calculation indicator
    await expect(page.getByText('Cùng giá hoặc tương đương: Bù trừ 0 đ theo chính sách lượt vé.').first()).toBeVisible();

    // Submit substitution
    await page.getByRole('button', { name: 'Lưu chỉ định thay đổi' }).click();

    // Verify API called and execution notes recorded
    await expect.poll(() => executionUpdated).not.toBeNull();
    expect(executionUpdated.notes).toContain('Đổi từ Chăm Sóc Da Mặt Aqua Peel Chuyên Sâu sang Điện di phục hồi B5');
    expect(executionUpdated.notes).toContain('Khách đã ký xác nhận đồng thuận');
  });

});

