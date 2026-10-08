import { test, expect } from "@playwright/test";
import { mkdir, readFile } from "node:fs/promises";

const operator = {
  id: 1,
  username: "admin",
  fullName: "Thanh Nam",
  email: "admin@example.test",
  roles: ["ROLE_ADMIN"],
};
const emptyPage = {
  content: [],
  totalPages: 0,
  totalElements: 0,
  number: 0,
  size: 15,
};
const overview = {
  totalOrders: 128,
  revenue: 84500000,
  paidOrders: 104,
  pendingPaymentOrders: 12,
  openOrders: 14,
  averageOrderValue: 812500,
  activeCustomers: 426,
  lowStockItems: 5,
  inventoryValue: 235000000,
  refundedAmount: 2400000,
  codAmount: 18500000,
  bankAmount: 60000000,
  cashAmount: 6000000,
  receiptCount: 109,
};
const businessDate = (daysAgo) =>
  new Date(Date.now() - daysAgo * 86400000).toLocaleDateString("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
  });
const revenue = Array.from({ length: 30 }, (_, index) => ({
  period: businessDate(29 - index),
  revenue: [1800000, 2600000, 2200000, 4100000, 3300000, 5100000, 2900000][
    index % 7
  ],
  orderCount: 3 + (index % 6),
  receiptCount: 5 + (index % 7),
  refundedAmount: index % 5 ? 0 : 200000,
  codAmount: 400000,
  cashAmount: 200000,
  bankAmount: 1800000,
}));
const topProducts = [
  {
    productId: 1,
    productName: "La Roche-Posay Cicaplast Baume B5+",
    unitsSold: 32,
    grossSales: 12160000,
  },
  {
    productId: 2,
    productName: "Bioderma Sensibio H2O",
    unitsSold: 26,
    grossSales: 10400000,
  },
  {
    productId: 3,
    productName: "Vichy Minéral 89",
    unitsSold: 18,
    grossSales: 11700000,
  },
];
const orders = [
  {
    id: 1,
    orderNumber: "BS-01028",
    fullName: "Nguyễn Minh Anh",
    customerName: "Nguyễn Minh Anh",
    status: "CONFIRMED",
    paymentStatus: "PAID",
    paymentMethod: "BANK_TRANSFER",
    totalAmount: 1280000,
    createdAt: new Date().toISOString(),
    items: [],
  },
  {
    id: 2,
    orderNumber: "BS-01027",
    fullName: "Trần Thảo Linh",
    customerName: "Trần Thảo Linh",
    status: "DELIVERED",
    paymentStatus: "PAID",
    paymentMethod: "COD",
    totalAmount: 890000,
    createdAt: new Date().toISOString(),
    items: [],
  },
];

async function mockApi(page, { roles = ["ROLE_ADMIN"], failure = false } = {}) {
  await page.addInitScript(
    (profile) => {
      localStorage.setItem("beautyshop_admin_token", "browser-test-token");
      localStorage.setItem("beautyshop_admin_user", JSON.stringify(profile));
    },
    { ...operator, roles },
  );
  await page.route("**/actuator/health", (route) =>
    route.fulfill({
      json: {
        status: "UP",
        components: { db: { status: "UP" }, redis: { status: "UP" } },
      },
    }),
  );
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (!path.startsWith("/api/")) return route.continue();
    if (failure && path.includes("/admin/dashboard"))
      return route.fulfill({
        status: 503,
        json: { message: "Máy chủ chưa sẵn sàng" },
      });
    let data = [];
    if (path.endsWith("/auth/profile")) data = { ...operator, roles };
    else if (path.endsWith("/dashboard")) data = overview;
    else if (path.endsWith("/dashboard/revenue")) {
      data =
        url.searchParams.get("period") === "month"
          ? Object.values(
              revenue.reduce((buckets, record) => {
                const period = record.period.slice(0, 7);
                const bucket = (buckets[period] ||= {
                  period,
                  revenue: 0,
                  refundedAmount: 0,
                  orderCount: 0,
                  receiptCount: 0,
                });
                for (const key of [
                  "revenue",
                  "refundedAmount",
                  "orderCount",
                  "receiptCount",
                ])
                  bucket[key] += record[key];
                return buckets;
              }, {}),
            )
          : revenue;
    } else if (path.endsWith("/dashboard/top-products")) data = topProducts;
    else if (path.endsWith("/dashboard/spa-occupancy"))
      data = {
        scheduledMinutes: 4800,
        bookedMinutes: 3264,
        occupancyRatePercent: 68,
        servedMinutes: 2640,
        servedRatePercent: 55,
        noShowMinutes: 120,
        noShowAppointments: 2,
        overCapacityMinutes: 0,
        overCapacity: false,
        reservedMinutes: 624,
        legacyFinalizedMinutes: 0,
      };
    else if (path.endsWith("/operational-alerts"))
      data = [
        {
          code: "ORDERS",
          title: "Đơn hàng cần xử lý",
          count: 14,
          severity: "warning",
          target: "orders",
        },
      ];
    else if (path.endsWith("/admin/orders"))
      data = {
        ...emptyPage,
        content: orders,
        totalPages: 2,
        totalElements: 22,
      };
    else if (path.endsWith("/payments/summary"))
      data = {
        totalTransactions: 109,
        receivedAmount: 84500000,
        failedTransactions: 0,
      };
    else if (path.endsWith("/system-configs"))
      data = [
        {
          id: 1,
          configKey: "inventory_expiry_warning_days",
          configValue: "30",
          description: "Số ngày cảnh báo lô hàng gần hết hạn",
        },
      ];
    else if (
      /\/(admin\/users|admin\/reviews|admin\/products|inventory\/transactions|procurement\/suppliers|procurement\/orders|admin\/payments|audit-logs|appointments\/admin\/all)$/.test(
        path,
      )
    )
      data = emptyPage;
    return route.fulfill({ json: { success: true, data } });
  });
}

async function noPageOverflow(page) {
  await expect
    .poll(() =>
      page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth + 1,
      ),
    )
    .toBe(true);
}

test("Desktop workspace renders real API metrics with a custom visual system", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await mockApi(page);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".atelier-dashboard")).toBeVisible();
  await expect(page.locator(".sidebar-link")).toHaveCount(11);
  await expect(
    page.locator('.sidebar-link[aria-current="page"]'),
  ).toContainText("Tổng quan");
  await expect(page.locator(".atelier-dashboard")).toContainText(
    "La Roche-Posay",
  );
  await noPageOverflow(page);
  expect(
    await page
      .locator(".atelier-sidebar")
      .evaluate((node) => getComputedStyle(node).backgroundColor),
  ).toBe("rgb(33, 26, 53)");
  await page.evaluate(() => document.fonts.ready);
  await expect(
    page.locator(".atelier-metric--violet .atelier-metric-value"),
  ).toContainText("84.500.000");
  await mkdir("artifacts", { recursive: true });
  await page.screenshot({
    path: "artifacts/admin-dashboard-desktop.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(errors).toEqual([]);
});

test("Command search supports unaccented Vietnamese, Enter, Escape and focus restoration", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".atelier-topbar")).toBeVisible();
  await page.keyboard.press("Control+k");
  const search = page.getByRole("combobox", { name: "Tìm phân hệ quản trị" });
  await expect(search).toBeFocused();
  await search.fill("san pham");
  await expect(page.locator(".command-result")).toHaveCount(1);
  await search.press("Enter");
  await expect(page).toHaveURL(/admin\/products/);
  await expect(page.locator(".page-title")).toContainText("Sản phẩm");
  await page
    .getByRole("button", { name: "Tìm nhanh phân hệ quản trị", exact: true })
    .click();
  await expect(search).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(
    page.getByRole("button", {
      name: "Tìm nhanh phân hệ quản trị",
      exact: true,
    }),
  ).toBeFocused();
});

test("Sidebar and motion preferences persist after reload", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await page.getByRole("button", { name: "Thu gọn thanh điều hướng" }).click();
  await expect(page.locator(".admin-shell")).toHaveClass(
    /sidebar-is-collapsed/,
  );
  await page.getByRole("button", { name: "Giảm hiệu ứng chuyển động" }).click();
  await expect(page.locator(".admin-shell")).toHaveAttribute(
    "data-motion",
    "reduced",
  );
  await page.reload();
  await expect(page.locator(".admin-shell")).toHaveClass(
    /sidebar-is-collapsed/,
  );
  await expect(page.locator(".admin-shell")).toHaveAttribute(
    "data-motion",
    "reduced",
  );
  await expect(page.locator(".atelier-dashboard")).toBeVisible();
  await noPageOverflow(page);
});

test("Notifications navigate into the admin orders route and health modal traps focus", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await page.getByRole("button", { name: /Thông báo vận hành/ }).click();
  await page.locator(".alerts-list > button").click();
  await expect(page).toHaveURL(/#\/admin\/orders$/);
  await expect(page.locator(".page-title")).toContainText("Đơn hàng");
  await page.getByRole("button", { name: /Trạng thái hệ thống/ }).click();
  const dialog = page.getByRole("dialog", { name: "Trạng thái kết nối" });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText("Kết nối ổn định");
  for (let index = 0; index < 8; index++) await page.keyboard.press("Tab");
  expect(
    await dialog.evaluate((node) => node.contains(document.activeElement)),
  ).toBe(true);
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe(
    "hidden",
  );
});

test("Mobile drawer traps focus, closes on Escape and keeps content within 390px", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".atelier-dashboard")).toBeVisible();
  await noPageOverflow(page);
  const trigger = page.getByRole("button", { name: "Mở menu điều hướng" });
  await trigger.click();
  await expect(page.locator(".atelier-sidebar")).toHaveClass(/is-mobile-open/);
  await expect(page.locator(".main-viewport")).toHaveAttribute("inert", "");
  for (let index = 0; index < 16; index++) await page.keyboard.press("Tab");
  expect(
    await page
      .locator(".atelier-sidebar")
      .evaluate((node) => node.contains(document.activeElement)),
  ).toBe(true);
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await trigger.click();
  await page.locator(".sidebar-link").filter({ hasText: "Đơn hàng" }).click();
  await expect(page).toHaveURL(/admin\/orders/);
  await expect(page.locator(".atelier-sidebar")).not.toHaveClass(
    /is-mobile-open/,
  );
  await noPageOverflow(page);
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".atelier-dashboard")).toContainText(
    "La Roche-Posay",
  );
  await expect(
    page.locator(".atelier-metric--violet .atelier-metric-value"),
  ).toContainText("84.500.000");
  await page.screenshot({
    path: "artifacts/admin-dashboard-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("Every admin module renders on desktop and mobile without runtime errors or page overflow", async ({
  page,
}) => {
  test.setTimeout(60000);
  await mockApi(page);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const modules = [
    "orders",
    "products",
    "inventory",
    "procurement",
    "spa",
    "crm",
    "vouchers",
    "reviews",
    "users",
    "operations",
  ];
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 960 });
    for (const module of modules) {
      await page.goto(`/#/admin/${module}`);
      await expect(page.locator(".page-title")).toBeVisible();
      await noPageOverflow(page);
    }
    await page
      .getByRole("button", { name: "Cấu hình KPI", exact: true })
      .click();
    await expect(page.locator(".content-container")).toContainText(
      "inventory_expiry_warning_days",
    );
    await noPageOverflow(page);
  }
  expect(errors).toEqual([]);
});

test("Operator navigation and command results honor assigned roles", async ({
  page,
}) => {
  await mockApi(page, { roles: ["ROLE_ORDER_STAFF"] });
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".page-title")).toContainText("Đơn hàng");
  await expect(page.locator(".sidebar-link")).toHaveCount(1);
  await expect(page.locator(".sidebar-section")).toHaveCount(1);
  await expect(
    page.getByRole("button", { name: /Thông báo vận hành/ }),
  ).toHaveCount(0);
  await page.keyboard.press("Control+k");
  await expect(page.locator(".command-result")).toHaveCount(1);
  await expect(page.locator(".command-result")).toContainText("Đơn hàng");
});

test("Dashboard displays unavailable data and allows retry after API failure", async ({
  page,
}) => {
  await mockApi(page, { failure: true });
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".atelier-dashboard")).toBeVisible();
  await expect(
    page.locator(".atelier-dashboard").getByRole("alert").first(),
  ).toBeVisible();
  await noPageOverflow(page);
  await expect(
    page
      .locator(".atelier-dashboard")
      .getByRole("button", { name: /Thử lại|Tải lại|Làm mới|Cập nhật/ })
      .first(),
  ).toBeVisible();
});

test("Admin login protects access and offers accessible password visibility", async ({
  page,
}) => {
  await page.goto("/#/admin/login");
  await expect(page.locator(".admin-auth")).toBeVisible();
  await expect(page.locator(".admin-shell")).toHaveCount(0);
  const password = page.locator('.admin-auth input[type="password"]');
  await expect(password).toHaveAttribute("autocomplete", "current-password");
  await page
    .getByRole("button", { name: /Hiện mật khẩu|Hiển thị mật khẩu/ })
    .click();
  await expect(
    page.locator('.admin-auth input[autocomplete="current-password"]'),
  ).toHaveAttribute("type", "text");
  await page.setViewportSize({ width: 390, height: 844 });
  await noPageOverflow(page);
});

test("Chart supports keyboard inspection and monthly aggregation", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  const chart = page.locator(".atelier-chart > svg");
  await expect(chart).toBeVisible();
  await chart.focus();
  await chart.press("End");
  await expect(page.locator(".atelier-chart-tooltip")).toContainText(
    "đơn thanh toán",
  );
  await chart.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Dòng tiền trong kỳ" });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator("tbody tr")).toHaveCount(30);
  await expect(
    dialog.locator(".atelier-report-selection strong"),
  ).toContainText(businessDate(0).slice(0, 4));
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  const monthlyResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/dashboard/revenue") &&
      response.url().includes("period=month"),
  );
  await page.getByRole("button", { name: "Theo tháng", exact: true }).click();
  await monthlyResponse;
  await expect(
    page.getByRole("button", { name: "Theo tháng", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await expect(
    page.getByRole("button", { name: "Làm mới dữ liệu tổng quan" }),
  ).toBeEnabled();
  await page
    .getByRole("button", { name: "Xem báo cáo dòng tiền", exact: true })
    .click();
  await expect(dialog.locator("tbody tr")).toHaveCount(
    new Set(revenue.map((record) => record.period.slice(0, 7))).size,
  );
  await noPageOverflow(page);
});

test("Custom date range sends inclusive Vietnam dates and an exclusive end instant", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await expect(
    page.getByRole("button", { name: "Làm mới dữ liệu tổng quan" }),
  ).toBeEnabled();
  const from = businessDate(2),
    to = businessDate(0);
  await page.getByLabel("Từ ngày", { exact: true }).fill(from);
  await page.getByLabel("Đến ngày", { exact: true }).fill(to);
  const request = page.waitForRequest((request) =>
    request.url().includes("/dashboard/revenue"),
  );
  await page.getByRole("button", { name: "Áp dụng", exact: true }).click();
  const params = new URL((await request).url()).searchParams;
  expect(params.get("from")).toBe(
    new Date(`${from}T00:00:00+07:00`).toISOString(),
  );
  const nextDate = new Date(`${to}T00:00:00+07:00`);
  nextDate.setUTCDate(nextDate.getUTCDate() + 1);
  expect(params.get("to")).toBe(nextDate.toISOString());
  await expect(
    page.getByRole("button", { name: "Làm mới dữ liệu tổng quan" }),
  ).toBeEnabled();
  await page
    .getByRole("button", { name: "Xem báo cáo dòng tiền", exact: true })
    .click();
  await expect(page.getByRole("dialog").locator("tbody tr")).toHaveCount(3);
});

test("CSV export contains the seven applied reporting days and real numeric columns", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await expect(
    page.getByRole("button", { name: "7 ngày", exact: true }),
  ).toBeEnabled();
  const response = page.waitForResponse((response) =>
    response.url().includes("/dashboard/revenue"),
  );
  await page.getByRole("button", { name: "7 ngày", exact: true }).click();
  await response;
  const exportButton = page.getByRole("button", {
    name: "Xuất báo cáo",
    exact: true,
  });
  await expect(exportButton).toBeEnabled();
  const downloadEvent = page.waitForEvent("download");
  await exportButton.click();
  const download = await downloadEvent;
  expect(download.suggestedFilename()).toBe(
    `beautyshop-dong-tien-${businessDate(6)}-${businessDate(0)}.csv`,
  );
  const csv = await readFile(await download.path(), "utf8");
  const lines = csv.trim().split(/\r?\n/);
  expect(lines).toHaveLength(8);
  expect(lines[0]).toContain("Tiền vào (VND)");
  expect(lines[1]).toContain(businessDate(6));
  expect(lines.at(-1)).toContain(businessDate(0));
  await expect(
    page.getByRole("button", { name: "Đã xuất CSV", exact: true }),
  ).toBeVisible();
});

test("System reduced-motion preference keeps all UI visible", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await mockApi(page);
  await page.goto("/#/admin/dashboard");
  await expect(page.locator(".atelier-dashboard")).toBeVisible();
  await expect(page.locator(".atelier-dashboard")).toContainText(
    "La Roche-Posay",
  );
  const duration = await page
    .locator(".admin-page-transition")
    .evaluate((node) => getComputedStyle(node).animationDuration);
  expect(parseFloat(duration)).toBeLessThan(0.01);
  await noPageOverflow(page);
});
