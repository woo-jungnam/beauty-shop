import { test, expect } from "@playwright/test";

const customer = {
  id: 7,
  username: "spa-customer",
  fullName: "Khách hàng kiểm thử",
  phoneNumber: "0901234567",
  email: "spa@example.test",
  roles: ["ROLE_CUSTOMER"],
};
const services = [
  {
    id: 8,
    name: "Chăm sóc da phục hồi",
    basePrice: 300000,
    durationMinutes: 60,
    isActive: true,
    categoryId: 1,
    categoryName: "Chăm sóc da",
    shortDescription: "Làm dịu và chăm sóc làn da sau một ngày dài.",
  },
  {
    id: 9,
    name: "Làm sạch da chuyên sâu",
    basePrice: 250000,
    durationMinutes: 45,
    isActive: true,
    categoryId: 1,
    categoryName: "Chăm sóc da",
    shortDescription: "Một buổi làm sạch và thư giãn dành cho làn da.",
  },
  {
    id: 10,
    name: "Massage thư giãn",
    basePrice: 450000,
    durationMinutes: 90,
    isActive: true,
    categoryId: 2,
    categoryName: "Thư giãn",
    shortDescription: "Thả lỏng cơ thể, dành thời gian chăm sóc chính mình.",
    thumbnailUrl: "/images/spa/spa-ritual.jpg",
  },
  {
    id: 11,
    name: "Chăm sóc da cấp ẩm",
    basePrice: 350000,
    durationMinutes: 60,
    isActive: true,
    categoryId: 1,
    categoryName: "Chăm sóc da",
    shortDescription: "Chăm sóc da khô và thiếu ẩm.",
    thumbnailUrl: "/images/spa/facial-care.jpg",
  },
];

async function spaStorefront(
  page,
  {
    authenticated = true,
    withTicket = false,
    failSlots = false,
    failServices = false,
    conflict = false,
  } = {},
) {
  const writes = [];
  const slotRequests = [];
  await page.clock.setFixedTime(new Date("2026-11-18T00:00:00Z"));
  let slotsFailed = failSlots;
  let holdNextSlots = false;
  let releaseSlots;
  if (authenticated) {
    await page.addInitScript((user) => {
      localStorage.setItem("beautyshop_admin_token", "spa-ui-test-token");
      localStorage.setItem("beautyshop_admin_user", JSON.stringify(user));
    }, customer);
  }
  await page.route("**/fonts.googleapis.com/**", (route) => route.abort());
  await page.route("**/fonts.gstatic.com/**", (route) => route.abort());
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = [];
    if (path.endsWith("/auth/profile")) data = customer;
    else if (path.endsWith("/auth/login")) data = { ...customer, accessToken: "spa-ui-test-token" };
    else if (path === "/api/v1/banners" && url.searchParams.get("position") === "BOOKING_HERO")
      data = [{ id: 1, position: "BOOKING_HERO", title: "Đặt lịch Spa",
        imageUrl: "/images/spa/spa-ritual.jpg", targetUrl: "#spa-booking-form", isActive: true }];
    else if (path === "/api/v1/spa/services") {
      if (failServices)
        return route.fulfill({
          status: 503,
          json: { message: "Services unavailable" },
        });
      data = services;
    } else if (path.endsWith("/available-slots")) {
      slotRequests.push({
        service: path.split("/")[5],
        date: url.searchParams.get("date"),
        staff: url.searchParams.get("staffId"),
      });
      if (holdNextSlots) {
        holdNextSlots = false;
        await new Promise((resolve) => {
          releaseSlots = resolve;
        });
      }
      if (slotsFailed)
        return route.fulfill({
          status: 503,
          json: { message: "Availability unavailable" },
        });
      data =
        url.searchParams.get("staffId") === "21"
          ? ["14:00", "15:00"]
          : ["09:00", "10:30", "11:00", "13:30", "14:00", "15:30", "17:00"];
    } else if (/\/spa\/services\/\d+\/staff$/.test(path))
      data = [
        { id: 21, fullName: "Ngọc Anh", specialty: "Chăm sóc da" },
        { id: 22, fullName: "Minh Thư", specialty: "Chăm sóc da" },
      ];
    else if (path === "/api/v1/spa/tickets/my-active-tickets")
      data = withTicket ? [{ id: 77, remainingByService: { 8: 3 } }] : [];
    else if (path === "/api/v1/spa/financial-policy")
      data = { version: 1, depositAmount: 0 };
    else if (path === "/api/v1/spa/appointments/120/finance")
      data = { entries: [], depositDue: 0, status: "PENDING" };
    else if (path === "/api/v1/appointments/book") {
      const payload = route.request().postDataJSON();
      writes.push(payload);
      if (conflict)
        return route.fulfill({
          status: 409,
          json: { message: "Khung giờ vừa được đặt. Vui lòng chọn giờ khác." },
        });
      data = {
        id: 120,
        status: "PENDING",
        appointmentDate: payload.appointmentDate,
        startTime: payload.startTime,
        customerName: customer.fullName,
        customerPhone: customer.phoneNumber,
        items: [
          {
            serviceName: services.find(
              (service) => service.id === payload.items[0].serviceId,
            )?.name,
          },
        ],
      };
    } else if (path === "/api/v1/brands" || path === "/api/v1/products")
      data = { content: [], totalPages: 0 };
    else if (path === "/api/v1/cart") data = null;
    else if (path === "/api/v1/chatbot/health") data = { status: "ok" };
    return route.fulfill({ json: { success: true, data } });
  });
  return {
    writes,
    slotRequests,
    recoverSlots: () => {
      slotsFailed = false;
    },
    holdSlots: () => {
      holdNextSlots = true;
    },
    releaseSlots: () => releaseSlots?.(),
    hasHeldSlots: () => Boolean(releaseSlots),
  };
}

const next = (page, name) => page.getByRole("button", { name, exact: true }).click();
const chooseFirstService = async (page) => {
  await next(page, "Chọn dịch vụ Chăm sóc da phục hồi");
  await next(page, "Tiếp tục chọn giờ");
};

test("A chosen service, time and notes create a booking with the existing API contract", async ({
  page,
}) => {
  const api = await spaStorefront(page);
  await page.goto("/#/booking?serviceId=9");
  const categoryFilter = page.getByRole("combobox", { name: "Lọc danh mục dịch vụ" });
  const serviceSearch = page.getByRole("textbox", { name: "Tìm kiếm dịch vụ" });
  await categoryFilter.selectOption("Thư giãn");
  await expect(page.getByRole("button", { name: "Chọn dịch vụ Massage thư giãn", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Chọn dịch vụ Làm sạch da chuyên sâu", exact: true })).toHaveCount(0);
  await serviceSearch.fill("Làm sạch");
  await expect(page.getByText("Chưa tìm thấy dịch vụ phù hợp")).toBeVisible();
  await categoryFilter.selectOption("ALL");
  await serviceSearch.clear();
  await expect(
    page.getByRole("button", {
      name: "Chọn dịch vụ Làm sạch da chuyên sâu",
      exact: true,
    }),
  ).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#spa-step-2, #spa-step-3")).toHaveCount(0);
  await next(page, "Tiếp tục chọn giờ");
  const times = page.getByRole("group", { name: "Buổi sáng", exact: true });
  await expect(page.getByRole("button", { name: "Tiếp tục điền thông tin", exact: true })).toBeDisabled();
  await times.getByRole("button", { name: "10:30", exact: true }).click();
  await next(page, "Tiếp tục điền thông tin");
  await expect(page.locator("#spa-step-1, #spa-step-2")).toHaveCount(0);
  await page.getByRole("button", { name: "Da nhạy cảm", exact: true }).click();
  await page
    .getByLabel("Ghi chú thêm cho chuyên viên")
    .fill("Tôi muốn được tư vấn trước khi bắt đầu.");
  await page
    .getByRole("button", { name: "Tiền sử dị ứng", exact: true })
    .click();
  await next(page, "Quay lại chọn giờ");
  await expect(times.getByRole("button", { name: "10:30", exact: true })).toHaveAttribute("aria-pressed", "true");
  await next(page, "Tiếp tục điền thông tin");
  await expect(page.getByLabel("Ghi chú thêm cho chuyên viên")).toHaveValue(
    "Tôi muốn được tư vấn trước khi bắt đầu.",
  );
  await page
    .getByRole("button", { name: "Xác nhận lịch hẹn", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Hẹn gặp bạn tại BeautyShop." }),
  ).toBeVisible();
  await expect(page.getByText("SPA-120", { exact: true })).toBeVisible();
  await expect(page.getByText("Chờ xác nhận", { exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0].startTime).toBe("10:30:00");
  expect(api.writes[0].items).toEqual([
    { serviceId: 9, staffId: null, facilityId: null, ticketId: null },
  ]);
  expect(api.writes[0].notes).toContain("Da nhạy cảm");
  expect(api.writes[0].notes).toContain("Tiền sử dị ứng");
  expect(api.writes[0].notes).toContain(
    "Tôi muốn được tư vấn trước khi bắt đầu.",
  );
});

test("Changing a date clears the previous slot while availability is loading", async ({
  page,
}) => {
  const api = await spaStorefront(page);
  await page.goto("/#/booking");
  await chooseFirstService(page);
  await page
    .getByRole("group", { name: "Buổi sáng", exact: true })
    .getByRole("button", { name: "09:00", exact: true })
    .click();
  await expect(page.locator(".booking-summary-details")).toContainText("09:00");
  api.holdSlots();
  await page
    .getByRole("group", { name: "Chọn ngày hẹn", exact: true })
    .getByRole("button", { name: /Ngày mai/ })
    .click();
  await expect.poll(api.hasHeldSlots).toBeTruthy();
  await expect(page.locator(".booking-summary-details")).toContainText(
    "Bạn chưa chọn giờ",
  );
  await expect(
    page.getByRole("button", { name: "Xác nhận lịch hẹn", exact: true }),
  ).toHaveCount(0);
  api.releaseSlots();
  await expect(
    page.getByRole("group", { name: "Buổi sáng", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "09:00", exact: true }),
  ).toHaveAttribute("aria-pressed", "false");
  expect(api.writes).toHaveLength(0);
});

test("Choosing a specialist reloads their slots and a prepaid ticket updates the summary", async ({
  page,
}) => {
  const api = await spaStorefront(page, { withTicket: true });
  await page.goto("/#/booking?serviceId=8");
  await page.getByLabel("Áp dụng vé liệu trình").selectOption("77");
  await expect(page.locator(".booking-total b")).toHaveText("0 đ");
  await next(page, "Tiếp tục chọn giờ");
  await page.getByRole("button", { name: /Ngọc Anh/ }).click();
  await expect
    .poll(() => api.slotRequests.some((request) => request.staff === "21"))
    .toBeTruthy();
  await page
    .getByRole("group", { name: "Buổi chiều & tối", exact: true })
    .getByRole("button", { name: "14:00", exact: true })
    .click();
  await next(page, "Tiếp tục điền thông tin");
  await page
    .getByRole("button", { name: "Xác nhận lịch hẹn", exact: true })
    .click();
  await expect(page.getByText("SPA-120", { exact: true })).toBeVisible();
  expect(api.writes[0].items).toEqual([
    { serviceId: 8, staffId: 21, facilityId: null, ticketId: 77 },
  ]);
});

test("Availability errors offer retry and remain distinct from an empty schedule", async ({
  page,
}) => {
  const api = await spaStorefront(page, { failSlots: true });
  await page.goto("/#/booking");
  await chooseFirstService(page);
  await expect(
    page.getByText("Chưa kiểm tra được giờ trống", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Chưa có giờ trống vào ngày này", { exact: true }),
  ).toHaveCount(0);
  api.recoverSlots();
  await page.getByRole("button", { name: "Kiểm tra lại", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "09:00", exact: true }),
  ).toBeVisible();
});

test("A booking conflict shows its error and requires choosing a slot again", async ({
  page,
}) => {
  const api = await spaStorefront(page, { conflict: true });
  await page.goto("/#/booking");
  await chooseFirstService(page);
  await page.getByRole("button", { name: "10:30", exact: true }).click();
  await next(page, "Tiếp tục điền thông tin");
  await page
    .getByRole("button", { name: "Xác nhận lịch hẹn", exact: true })
    .click();
  await expect(page.locator("#spa-booking-error")).toContainText(
    "Khung giờ vừa được đặt.",
  );
  await expect(page.locator("#spa-booking-error")).toBeFocused();
  await expect(page.locator("#spa-step-2")).toBeVisible();
  await expect(page.locator(".booking-summary-details")).toContainText(
    "Bạn chưa chọn giờ",
  );
  await expect(
    page.getByRole("heading", { name: "Hẹn gặp bạn tại BeautyShop." }),
  ).toHaveCount(0);
  expect(api.writes).toHaveLength(1);
});

test("A guest resumes the confirmation step after logging in", async ({
  page,
}) => {
  const api = await spaStorefront(page, { authenticated: false });
  await page.goto("/#/booking");
  await chooseFirstService(page);
  await page.getByRole("button", { name: "10:30", exact: true }).click();
  await next(page, "Tiếp tục điền thông tin");
  await page.locator("#spa-note").fill("Giữ ghi chú sau khi đăng nhập.");
  await page
    .locator(".booking-summary")
    .getByRole("button", { name: "Đăng nhập để đặt lịch", exact: true })
    .click();
  await expect(page).toHaveURL(/#\/login$/);
  await page.locator("#customer-auth-username").fill(customer.username);
  await page.locator("#customer-auth-password").fill("test-password");
  await page.locator(".auth-slide-pane.active form [type=submit]").click();
  await expect(page).toHaveURL(/#\/booking$/);
  await expect(page.locator("#spa-step-3")).toBeVisible();
  await expect(page.locator("#spa-note")).toHaveValue("Giữ ghi chú sau khi đăng nhập.");
  await expect(page.locator(".booking-summary-details")).toContainText("10:30");
  expect(api.writes).toHaveLength(0);
});

test("The mobile action guides missing contact information and submits the booking", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const api = await spaStorefront(page);
  await page.goto("/#/booking");
  const action = page.locator(".booking-mobile-bar");
  await expect(action.getByRole("button", { name: "Tiếp tục chọn giờ", exact: true })).toBeDisabled();
  await chooseFirstService(page);
  await expect(page.locator("#spa-step-2")).toBeFocused();
  await page.getByRole("button", { name: "10:30", exact: true }).click();
  await next(page, "Tiếp tục điền thông tin");
  await page.getByLabel("Số điện thoại *", { exact: true }).fill("");
  await action
    .getByRole("button", { name: "Điền thông tin", exact: true })
    .click();
  await expect(page.locator("#spa-phone")).toBeFocused();
  await page.locator("#spa-phone").fill("0901234567");
  await action
    .getByRole("button", { name: "Xác nhận lịch hẹn", exact: true })
    .click();
  await expect(page.getByText("SPA-120", { exact: true })).toBeVisible();
  expect(api.writes).toHaveLength(1);
});

for (const width of [320, 390, 768, 1440]) {
  test(`The Spa design and selections fit ${width}px screens`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await spaStorefront(page);
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("/#/booking");
    await expect(
      page.getByRole("heading", { name: /Một chút thời gian/ }),
    ).toBeVisible();
    const bannerPhoto = page.locator(".booking-hero-photo img");
    await expect(bannerPhoto).toBeVisible();
    await expect(bannerPhoto).toHaveAttribute("src", "/images/spa/spa-ritual.jpg");
    await page
      .getByRole("button", {
        name: "Chọn dịch vụ Chăm sóc da phục hồi",
        exact: true,
      })
      .focus();
    await page.keyboard.press("Enter");
    await next(page, "Tiếp tục chọn giờ");
    await page.getByRole("button", { name: "10:30", exact: true }).click();
    await expect(page.locator(".booking-summary-details")).toContainText(
      "10:30",
    );
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth),
    ).toBeLessThanOrEqual(width);
    for (const selector of [
      ".booking-hero",
      ".booking-section",
      ".booking-summary",
    ]) {
      for (const bounds of await page
        .locator(selector)
        .evaluateAll((elements) =>
          elements.map((element) => {
            const rect = element.getBoundingClientRect();
            return { left: rect.left, right: rect.right };
          }),
        )) {
        expect(bounds.left).toBeGreaterThanOrEqual(0);
        expect(bounds.right).toBeLessThanOrEqual(width + 1);
      }
    }
    if (width < 768)
      await expect(page.locator(".booking-mobile-bar")).toBeVisible();
    else await expect(page.locator(".booking-mobile-bar")).toBeHidden();
    expect(errors).toEqual([]);
  });
}
