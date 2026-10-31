import { test, expect } from '@playwright/test';

const pageErrors = new WeakMap();
test.beforeEach(({ page }) => {
  const errors = [];
  pageErrors.set(page, errors);
  page.on('pageerror', error => errors.push(error.message));
});
test.afterEach(({ page }) => {
  expect(pageErrors.get(page), 'Spa catalog must not raise unhandled JavaScript errors').toEqual([]);
});

const profile = { id: 99, username: 'spa-admin-test', fullName: 'Quản lý Spa', roles: ['ROLE_ADMIN'] };
const categories = [
  { id: 801, name: 'Trị mụn', slug: 'tri-mun', active: true },
  { id: 802, name: 'Phục hồi', slug: 'phuc-hoi', active: true },
  { id: 803, name: 'Triệt lông', slug: 'triet-long', active: true },
];
const fixtureServices = [
  { id: 901, name: 'Điều trị mụn chuyên sâu', slug: 'dieu-tri-mun', categoryId: 801, categoryName: 'Trị mụn', basePrice: 300000, durationMinutes: 60, preparationTimeMinutes: 15, thumbnailUrl: '/images/spa-catalog-fixture.svg', shortDescription: 'Làm sạch và chăm sóc da mụn.', description: 'Quy trình chăm sóc da chuyên sâu.', isActive: true },
  { id: 902, name: 'Phục hồi hàng rào da', slug: 'phuc-hoi-da', categoryId: 802, categoryName: 'Phục hồi', basePrice: 400000, durationMinutes: 45, preparationTimeMinutes: 10, thumbnailUrl: '', shortDescription: 'Chăm sóc làn da nhạy cảm.', isActive: true },
  { id: 903, name: 'Tư vấn chăm sóc da', slug: 'tu-van-da', categoryId: null, categoryName: null, basePrice: 0, durationMinutes: 30, preparationTimeMinutes: 0, thumbnailUrl: '', isActive: false },
  { id: 904, name: 'Triệt lông công nghệ Laser', slug: 'triet-long-laser', categoryId: 803, categoryName: 'Triệt lông', basePrice: 650000, durationMinutes: 40, preparationTimeMinutes: 15, thumbnailUrl: '', isActive: true },
];
const fixtureStaff = [
  { id: 1, userId: 1001, fullName: 'Mai Anh', specialty: 'Chăm sóc da', active: true, skills: [{ serviceId: 901, serviceName: fixtureServices[0].name, certified: false }] },
  { id: 2, userId: 1002, fullName: 'Thu Hà', specialty: 'Chăm sóc da', active: false, skills: [{ serviceId: 901, serviceName: fixtureServices[0].name, certified: true }] },
  { id: 3, userId: 1003, fullName: 'Ngọc Linh', specialty: 'Phục hồi da', active: true, skills: [{ serviceId: 902, serviceName: fixtureServices[1].name, certified: true }] },
  { id: 4, userId: 1004, fullName: 'Minh Châu', specialty: 'Điều trị da', active: true, skills: [{ serviceId: 901, serviceName: fixtureServices[0].name, certified: true }] },
];

const clone = (value) => structuredClone(value);
const serviceCard = (page, id) => page.locator(`article[data-service-id="${id}"]`);
const packageCard = (page, id) => page.locator(`article[data-package-id="${id}"]`);
const catalogEndpoints = { services: '/api/v1/admin/spa/services', categories: '/api/v1/admin/spa/categories', packages: '/api/v1/admin/spa/packages', staff: '/api/v1/admin/staff', facilities: '/api/v1/admin/spa/facilities' };

async function setup(page, options = {}) {
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, profile);
  const calls = [];
  let services = clone(fixtureServices);
  let staff = clone(fixtureStaff);
  let packages = [{ id: 910, name: 'Liệu trình da khỏe', description: 'Chăm sóc da mụn kết hợp phục hồi.', price: 750000, validityDays: 90, thumbnailUrl: '/images/spa-catalog-fixture.svg', isActive: true, items: [{ serviceId: 901, serviceName: fixtureServices[0].name, quantity: 2 }, { serviceId: 902, serviceName: fixtureServices[1].name, quantity: 1 }] }];
  const requirements = new Map([[901, [{ type: 'BED', units: 1 }, { type: 'LASER', units: 1 }]], [902, [{ type: 'BED', units: 1 }]], [903, []], [904, [{ type: 'LASER', units: 1 }]]]);
  let resourceFailures = options.resourceFailures || 0;
  let resourceReadFailures = options.resourceReadFailures || 0;
  let resourceReadMalformed = options.resourceReadMalformed || 0;
  let policyReadFailures = options.policyReadFailures || 0;
  let releasePolicyA;
  const pendingPolicyA = new Promise(resolve => { releasePolicyA = resolve; });
  await page.route('**/images/spa-catalog-fixture.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="420"><rect width="640" height="420" fill="#F1D5DB"/><circle cx="320" cy="210" r="110" fill="#D45D79"/></svg>' }));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const body = method === 'GET' || method === 'DELETE' ? null : request.postDataJSON();
    if (method !== 'GET') calls.push({ path, method, body: clone(body) });
    const source = Object.keys(catalogEndpoints).find(key => catalogEndpoints[key] === path);
    const failure = method === 'GET' && source && options.catalogFailures?.[source];
    if (failure) return route.fulfill({ status: failure.status || 403, json: { message: failure.message || 'Forbidden' } });
    let data = [];
    if (path.endsWith('/auth/profile')) data = profile;
    else if (path.endsWith('/appointments/admin/all')) data = { content: [], page: 0, totalElements: 0, totalPages: 1, last: true };
    else if (path.endsWith('/spa/facilities')) data = [
      { id: 301, name: 'Giường chăm sóc da', type: 'BED', capacity: 2, active: true },
      { id: 302, name: 'Máy Laser chuyên dụng', type: 'LASER', capacity: 1, active: true },
      { id: 303, name: 'Phòng tư vấn', type: 'GENERAL', capacity: 1, active: false },
    ];
    else if (/\/spa\/preparation\/services\/\d+$/.test(path)) {
      const id = Number(path.split('/').at(-1));
      if (method === 'GET' && policyReadFailures > 0) { policyReadFailures -= 1; return route.fulfill({ status: 500, json: { message: 'Policy read unavailable' } }); }
      if (method === 'GET' && options.policyRace && id === 901) await pendingPolicyA;
      data = { serviceId: id, warningsRequired: method === 'PUT' ? body.warningsRequired : id === 901, requiredForms: [] };
    } else if (path.endsWith('/spa/preparation/templates')) data = { content: [], page: 0, totalPages: 1, totalElements: 0, last: true };
    else if (path.endsWith('/resource-requirements')) {
      const id = Number(path.split('/').at(-2));
      if (method === 'GET' && resourceReadFailures > 0) { resourceReadFailures -= 1; return route.fulfill({ status: 500, json: { message: 'Resource read unavailable' } }); }
      if (method === 'GET' && resourceReadMalformed > 0) { resourceReadMalformed -= 1; return route.fulfill({ json: { data: { unexpected: 'Malformed resource result' } } }); }
      if (method === 'PUT') {
        if (resourceFailures > 0) { resourceFailures -= 1; return route.fulfill({ status: 500, json: { message: 'Resource persistence unavailable' } }); }
        requirements.set(id, clone(body));
      }
      data = clone(requirements.get(id) || []);
    } else if (/\/staff\/\d+\/skills\/\d+$/.test(path)) {
      const staffId = Number(path.split('/').at(-3));
      const serviceId = Number(path.split('/').at(-1));
      if (options.skillRemoveFailure === staffId) return route.fulfill({ status: 409, json: { message: 'Vui lòng chuyển hoặc hủy các lịch hẹn hiện có trước khi thay đổi chuyên viên này' } });
      staff = staff.map(row => row.id === staffId ? { ...row, skills: row.skills.filter(skill => skill.serviceId !== serviceId) } : row);
      data = null;
    } else if (/\/staff\/\d+\/skills$/.test(path)) {
      const staffId = Number(path.split('/').at(-2));
      staff = staff.map(row => row.id === staffId ? { ...row, skills: [...row.skills.filter(skill => skill.serviceId !== body.serviceId), { serviceId: body.serviceId, serviceName: services.find(service => service.id === body.serviceId)?.name, certified: body.certified }] } : row);
      data = staff.find(row => row.id === staffId);
    } else if (path.endsWith('/admin/staff')) {
      if (options.staffReadFailure) return route.fulfill({ status: 500, json: { message: 'Skill data unavailable' } });
      data = clone(staff);
    }
    else if (/\/spa\/services\/\d+$/.test(path)) {
      const id = Number(path.split('/').at(-1));
      if (method === 'PUT') services = services.map(row => row.id === id ? { ...row, ...clone(body), isActive: body.active, categoryName: categories.find(category => category.id === body.categoryId)?.name || null } : row);
      if (method === 'DELETE') {
        if (options.serviceDeleteFailure) return route.fulfill({ status: 409, json: { message: 'Vui lòng xử lý các lịch hẹn, vé đã mua và đơn hàng đang chờ trước khi ngưng dịch vụ này' } });
        services = services.filter(row => row.id !== id);
      }
      data = services.find(row => row.id === id) || null;
    } else if (path.endsWith('/admin/spa/services')) {
      if (method === 'POST') {
        data = { ...clone(body), id: 905, isActive: body.active, categoryName: categories.find(category => category.id === body.categoryId)?.name || null };
        services.push(data);
      } else {
        const pageNumber = Number(url.searchParams.get('page') || 0);
        data = { content: clone(pageNumber === 0 ? services.slice(0, 2) : services.slice(2)), page: pageNumber, size: 2, totalElements: services.length, totalPages: 2, last: pageNumber === 1 };
      }
    } else if (path.endsWith('/admin/spa/categories')) {
      const pageNumber = Number(url.searchParams.get('page') || 0);
      data = { content: clone(pageNumber === 0 ? categories.slice(0, 2) : categories.slice(2)), page: pageNumber, size: 2, totalElements: categories.length, totalPages: 2, last: pageNumber === 1 };
    } else if (/\/spa\/packages\/\d+$/.test(path) || path.endsWith('/spa/packages')) {
      const id = /\/\d+$/.test(path) ? Number(path.split('/').at(-1)) : 911;
      if (method === 'POST' || method === 'PUT') {
        data = { ...clone(body), id, isActive: body.active, items: body.items.map(item => ({ ...item, serviceName: services.find(service => service.id === item.serviceId)?.name })) };
        packages = method === 'POST' ? [...packages, data] : packages.map(row => row.id === id ? data : row);
        if (method === 'POST' && options.packageCreateUncertain) return route.fulfill({ status: 500, json: { message: 'Response interrupted after commit' } });
      } else if (method === 'DELETE') { packages = packages.filter(row => row.id !== id); data = null; }
      else data = /\/\d+$/.test(path) ? packages.find(row => row.id === id) : clone(packages);
    }
    await route.fulfill({ json: { data } });
  });
  await page.goto('/#/admin/spa');
  await page.getByRole('button', { name: /Dịch vụ & Gói Liệu trình/i }).click();
  await expect(page.getByRole('heading', { name: 'Dịch vụ & gói liệu trình', exact: true })).toBeVisible();
  if (options.catalogFailures?.services) await expect(page.locator('.spa-catalog [role="alert"]').first()).toBeVisible();
  else await expect(serviceCard(page, 904)).toBeVisible();
  return { calls, requirements, getServices: () => clone(services), getStaff: () => clone(staff), releasePolicyA };
}

test('catalog groups all paginated services and counts active staff independently of certification', async ({ page }) => {
  await setup(page);
  for (const name of ['Trị mụn', 'Phục hồi', 'Triệt lông']) await expect(page.getByRole('heading', { level: 3, name: new RegExp(`^${name}(?:\\s|$)`) })).toBeVisible();
  await expect(page.locator('article[data-service-id]')).toHaveCount(4);
  await expect(serviceCard(page, 901)).toContainText('dieu-tri-mun');
  await expect(serviceCard(page, 901)).toContainText(/60\s*phút.*15\s*p/i);
  await expect(serviceCard(page, 901)).toContainText(/300[.,]000/);
  await expect(serviceCard(page, 901)).toContainText(/2\s*KTV/i);
  await expect(serviceCard(page, 901).locator('img')).toHaveAttribute('src', /spa-catalog-fixture\.svg/);
  await expect(serviceCard(page, 903)).toContainText('Tạm dừng');
  await page.screenshot({ path: 'test-results/spa-catalog-desktop.png', fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(packageCard(page, 910)).toBeVisible();
});

test('editing service preserves a zero preparation time and thumbnail through existing backend payload', async ({ page }) => {
  const { calls } = await setup(page);
  await page.getByRole('button', { name: 'Sửa dịch vụ Tư vấn chăm sóc da', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Chỉnh sửa dịch vụ Spa' });
  await expect(dialog.getByLabel('Khử trùng / chuẩn bị (phút)')).toHaveValue('0');
  await dialog.getByLabel('Ảnh thumbnail (URL)').fill('/images/spa-catalog-fixture.svg');
  await dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' }).click();
  await expect(dialog).toHaveCount(0);
  const saved = calls.find(call => call.method === 'PUT' && call.path.endsWith('/services/903'));
  expect(saved.body.preparationTimeMinutes).toBe(0);
  expect(saved.body.basePrice).toBe(0);
  expect(saved.body.active).toBe(false);
  expect(saved.body.thumbnailUrl).toBe('/images/spa-catalog-fixture.svg');
});

test('service creation retains its saved ID when resource save fails and retries without a duplicate service', async ({ page }) => {
  const { calls, requirements, getServices } = await setup(page, { resourceFailures: 1 });
  await page.getByRole('button', { name: 'Thêm dịch vụ', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Tên dịch vụ', { exact: true }).fill('Trẻ hóa da công nghệ mới');
  await dialog.getByLabel('Đường dẫn slug').fill('tre-hoa-da-cong-nghe-moi');
  await dialog.getByLabel('Giá niêm yết (VND)').fill('800000');
  await dialog.getByLabel('Thời gian thực hiện (phút)').fill('60');
  await dialog.getByLabel('Khử trùng / chuẩn bị (phút)').fill('15');
  await dialog.getByLabel('Danh mục liệu trình').selectOption('802');
  await dialog.getByRole('tab', { name: 'Tài nguyên bắt buộc' }).click();
  await dialog.getByRole('button', { name: 'Thêm loại tài nguyên' }).click();
  await dialog.getByLabel('Loại tài nguyên 1').selectOption('BED');
  await dialog.getByLabel('Số đơn vị 1').fill('1');
  await dialog.getByRole('button', { name: 'Thêm loại tài nguyên' }).click();
  await dialog.getByLabel('Loại tài nguyên 2').selectOption('LASER');
  await dialog.getByLabel('Số đơn vị 2').fill('1');
  await dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' }).click();
  await expect(dialog.getByRole('alert')).toContainText(/dịch vụ đã được lưu/i);
  await expect(dialog.getByRole('alert')).not.toContainText('Resource persistence unavailable');
  expect(calls.find(call => call.method === 'POST' && call.path.endsWith('/spa/services')).body.active).toBe(false);
  expect(getServices().find(service => service.id === 905).isActive).toBe(false);
  await dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' }).click();
  await expect(dialog).toHaveCount(0);
  expect(calls.filter(call => call.method === 'POST' && call.path.endsWith('/spa/services'))).toHaveLength(1);
  expect(calls.filter(call => call.method === 'PUT' && call.path.endsWith('/905/resource-requirements'))).toHaveLength(2);
  expect(requirements.get(905)).toEqual([{ type: 'BED', units: 1 }, { type: 'LASER', units: 1 }]);
  expect(calls.filter(call => call.method === 'PUT' && call.path.endsWith('/services/905'))).toHaveLength(1);
  expect(getServices().find(service => service.id === 905).isActive).toBe(true);
});

test('service duration and resource validation prevent invalid backend submissions', async ({ page }) => {
  const { calls } = await setup(page);
  await page.getByRole('button', { name: 'Sửa dịch vụ Điều trị mụn chuyên sâu', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Thời gian thực hiện (phút)').fill('720');
  await dialog.getByLabel('Khử trùng / chuẩn bị (phút)').fill('15');
  await dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' }).click();
  await expect(dialog.getByRole('alert')).toBeVisible();
  expect(calls.filter(call => call.method !== 'GET')).toHaveLength(0);
  await dialog.getByLabel('Thời gian thực hiện (phút)').fill('60');
  await dialog.getByRole('tab', { name: 'Tài nguyên bắt buộc' }).click();
  await dialog.getByLabel('Loại tài nguyên 2').selectOption('BED');
  await dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' }).click();
  await expect(dialog.getByRole('alert')).toContainText(/chỉ được chọn một lần/i);
  expect(calls.filter(call => call.method !== 'GET')).toHaveLength(0);
});

test('catalog and service resource editor fit a narrow mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/spa-catalog-mobile.png', fullPage: true });
  await page.getByRole('button', { name: 'Tài nguyên Điều trị mụn chuyên sâu', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByLabel('Loại tài nguyên 1')).toHaveValue('BED');
  await expect(page.getByLabel('Loại tài nguyên 2')).toHaveValue('LASER');
  expect(await page.getByRole('dialog').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/spa-catalog-resource-mobile.png', fullPage: true });
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Tạo gói liệu trình', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Tạo gói liệu trình' })).toBeVisible();
  expect(await page.getByRole('dialog').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/spa-catalog-package-mobile.png', fullPage: true });
});

test('package builder sums service quantities, compares discount and creates an unlimited package', async ({ page }) => {
  const { calls } = await setup(page);
  await page.getByRole('button', { name: 'Tạo gói liệu trình', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Tạo gói liệu trình' });
  await dialog.getByLabel('Tên gói liệu trình').fill('Combo điều trị và phục hồi');
  await dialog.getByLabel('Dịch vụ 1', { exact: true }).selectOption('901');
  await dialog.getByLabel('Số buổi dịch vụ 1').fill('2');
  await dialog.getByRole('button', { name: 'Thêm dịch vụ', exact: true }).click();
  await dialog.getByLabel('Dịch vụ 2', { exact: true }).selectOption('902');
  await dialog.getByLabel('Số buổi dịch vụ 2').fill('1');
  await dialog.getByLabel('Giá bán gói (VND)').fill('750000');
  await expect(dialog.getByText('Ưu đãi 25%')).toBeVisible();
  await expect(dialog.getByText('3 buổi', { exact: true })).toBeVisible();
  await expect(dialog).toContainText(/1[.,]000[.,]000/);
  await expect(dialog).toContainText(/Khách tiết kiệm\s*250[.,]000/);
  await dialog.getByLabel('Không giới hạn thời hạn').check();
  await expect(dialog.getByLabel('Hiệu lực (ngày)')).toBeDisabled();
  await dialog.getByLabel('Đường dẫn ảnh đại diện').fill('/images/spa-catalog-fixture.svg');
  await dialog.getByRole('button', { name: 'Tạo gói liệu trình', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const created = calls.find(call => call.method === 'POST' && call.path.endsWith('/spa/packages'));
  expect(created.body.items).toEqual([{ serviceId: 901, quantity: 2 }, { serviceId: 902, quantity: 1 }]);
  expect(created.body.price).toBe(750000);
  expect(created.body.validityDays).toBeNull();
  expect(created.body.active).toBe(true);
  await page.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(packageCard(page, 911)).toBeVisible();
  await page.getByRole('button', { name: 'Sửa gói Combo điều trị và phục hồi', exact: true }).click();
  await expect(page.getByRole('dialog').getByLabel('Không giới hạn thời hạn')).toBeChecked();
  await expect(page.getByRole('dialog').getByLabel('Hiệu lực (ngày)')).toBeDisabled();
  await expect(page.getByRole('dialog').getByLabel('Đường dẫn ảnh đại diện')).toHaveValue('/images/spa-catalog-fixture.svg');
});

test('package validation blocks fractional sessions and explains prices above the service total', async ({ page }) => {
  const { calls } = await setup(page);
  await page.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await page.getByRole('button', { name: 'Sửa gói Liệu trình da khỏe', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Chỉnh sửa gói liệu trình' });
  await dialog.getByLabel('Giá bán gói (VND)').fill('1100000');
  await expect(dialog.getByText('Giá gói cao hơn giá gốc')).toBeVisible();
  await expect(dialog.getByText(/Ưu đãi -/)).toHaveCount(0);
  await dialog.getByLabel('Số buổi dịch vụ 1').fill('1.5');
  await dialog.getByRole('button', { name: 'Lưu thay đổi gói' }).click();
  await expect(dialog.getByRole('alert')).toBeVisible();
  await expect(dialog.getByText(/Số buổi phải là số nguyên dương/)).toBeVisible();
  expect(calls.filter(call => call.path.includes('/spa/packages'))).toHaveLength(0);
  await dialog.getByLabel('Số buổi dịch vụ 1').fill('2');
  await dialog.getByLabel('Hiệu lực (ngày)').fill('0');
  await dialog.getByRole('button', { name: 'Lưu thay đổi gói' }).click();
  await expect(dialog.getByRole('alert')).toBeVisible();
  expect(calls.filter(call => call.path.includes('/spa/packages'))).toHaveLength(0);
});

test('an uncertain package creation cannot be resubmitted before checking the refreshed catalog', async ({ page }) => {
  const { calls } = await setup(page, { packageCreateUncertain: true });
  await page.getByRole('button', { name: 'Tạo gói liệu trình', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Tạo gói liệu trình' });
  await dialog.getByLabel('Tên gói liệu trình').fill('Gói cần kiểm tra kết quả');
  await dialog.getByLabel('Dịch vụ 1', { exact: true }).selectOption('901');
  await dialog.getByLabel('Giá bán gói (VND)').fill('300000');
  await dialog.getByRole('button', { name: 'Tạo gói liệu trình', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('Chưa xác định được kết quả tạo gói liệu trình');
  await expect(dialog.getByLabel('Tên gói liệu trình')).toBeDisabled();
  await expect(dialog.getByRole('button', { name: 'Tạo gói liệu trình', exact: true })).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Đóng & kiểm tra danh mục', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(packageCard(page, 911)).toContainText('Gói cần kiểm tra kết quả');
  expect(calls.filter(call => call.method === 'POST' && call.path.endsWith('/spa/packages'))).toHaveLength(1);
});

test('skill matrix updates certification independently of permission and ignores inactive staff in eligibility', async ({ page }) => {
  const { calls } = await setup(page);
  await serviceCard(page, 901).getByRole('button', { name: /^Ma trận KTV\b/ }).click();
  const dialog = page.getByRole('dialog', { name: 'Ma trận kỹ năng & chứng nhận' });
  await expect(dialog.getByLabel('Cấp quyền làm dịch vụ cho Mai Anh')).toBeChecked();
  await expect(dialog.getByLabel('Đã chứng nhận cho Mai Anh')).not.toBeChecked();
  await expect(dialog.getByLabel('Cấp quyền làm dịch vụ cho Thu Hà')).toBeChecked();
  await expect(dialog.getByLabel('Đã chứng nhận cho Ngọc Linh')).toBeDisabled();
  await dialog.getByLabel('Đã chứng nhận cho Mai Anh').check();
  await dialog.getByRole('button', { name: 'Lưu 1 thay đổi', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(calls.filter(call => call.path.endsWith('/staff/1/skills'))).toEqual([{ method: 'PUT', path: '/api/v1/admin/staff/1/skills', body: { serviceId: 901, certified: true } }]);
  expect(calls.filter(call => call.method === 'DELETE')).toHaveLength(0);
  await expect(serviceCard(page, 901)).toContainText(/2\s*KTV/i);
});

test('skill matrix retains successful grants when another technician cannot be removed', async ({ page }) => {
  const { calls, getStaff } = await setup(page, { skillRemoveFailure: 1 });
  await serviceCard(page, 901).getByRole('button', { name: /^Ma trận KTV\b/ }).click();
  const dialog = page.getByRole('dialog', { name: 'Ma trận kỹ năng & chứng nhận' });
  await dialog.getByLabel('Cấp quyền làm dịch vụ cho Mai Anh').uncheck();
  await dialog.getByLabel('Cấp quyền làm dịch vụ cho Ngọc Linh').check();
  await dialog.getByRole('button', { name: 'Lưu 2 thay đổi', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('Đã xác minh 1/2 thay đổi được lưu');
  await expect(dialog).toContainText('Vui lòng chuyển hoặc hủy các lịch hẹn');
  await expect(dialog.getByRole('button', { name: 'Lưu 1 thay đổi' })).toBeVisible();
  await expect(dialog.getByLabel('Cấp quyền làm dịch vụ cho Ngọc Linh')).toBeChecked();
  expect(getStaff().find(row => row.id === 3).skills.find(skill => skill.serviceId === 901)).toMatchObject({ certified: false });
  expect(getStaff().find(row => row.id === 1).skills.some(skill => skill.serviceId === 901)).toBe(true);
  expect(calls.filter(call => call.path.endsWith('/staff/3/skills'))).toHaveLength(1);
  expect(calls.filter(call => call.path.endsWith('/staff/1/skills/901'))).toHaveLength(1);
  await expect(serviceCard(page, 901)).toContainText(/3\s*KTV/i);
});

test('catalog combines group, status and search filters', async ({ page }) => {
  await setup(page);
  await page.getByLabel('Lọc nhóm liệu trình').selectOption('801');
  await page.getByLabel('Lọc trạng thái kinh doanh').selectOption('ACTIVE');
  await page.getByLabel('Tìm dịch vụ hoặc gói liệu trình').fill('dieu-tri-mun');
  await expect(page.locator('article[data-service-id]')).toHaveCount(1);
  await expect(serviceCard(page, 901)).toBeVisible();
  await page.getByLabel('Lọc trạng thái kinh doanh').selectOption('PAUSED');
  await expect(page.locator('article[data-service-id]')).toHaveCount(0);
});

test('an unavailable skill API keeps an explicit unknown state rather than a false zero count', async ({ page }) => {
  await setup(page, { staffReadFailure: true });
  await expect(serviceCard(page, 901)).toContainText('Chưa tải kỹ năng KTV');
  await expect(serviceCard(page, 901)).not.toContainText('0 KTV đủ điều kiện');
  await expect(page.locator('article[data-service-id]')).toHaveCount(4);
});

test('beauty-report: five catalog 403 responses show one actionable banner without false zero or empty data', async ({ page }) => {
  await setup(page, { catalogFailures: Object.fromEntries(Object.keys(catalogEndpoints).map(key => [key, { status: 403 }])) });
  const catalog = page.locator('.spa-catalog');
  await expect(catalog.getByRole('alert')).toHaveCount(1);
  await expect(catalog.getByRole('alert')).toContainText('(403)');
  await expect(catalog.getByRole('alert')).toContainText('kiểm tra phiên đăng nhập và quyền truy cập');
  await expect(catalog.getByRole('alert')).not.toContainText('SPA_RECEPTION');
  await expect(catalog.locator('.spa-catalog-stat strong')).toHaveText(['—', '—', '—', '—']);
  await expect(catalog.locator('.spa-catalog-view button span')).toHaveText(['—', '—']);
  await expect(catalog.locator('.spa-catalog-results')).toContainText('Chưa tải được dữ liệu');
  await expect(catalog.locator('.spa-catalog-empty')).toHaveCount(0);
  await expect(catalog.locator('article')).toHaveCount(0);
  await expect(catalog.getByRole('button', { name: 'Thêm dịch vụ', exact: true })).toBeDisabled();
  await expect(catalog.getByRole('button', { name: 'Tạo gói liệu trình', exact: true })).toBeDisabled();
  await catalog.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(catalog.locator('.spa-catalog-results')).toContainText('Chưa tải được dữ liệu');
  await expect(catalog.locator('.spa-catalog-empty')).toHaveCount(0);
});

test('beauty-report: packages remain available when services return 403 and tab counts stay independent', async ({ page }) => {
  await setup(page, { catalogFailures: { services: { status: 403 } } });
  const catalog = page.locator('.spa-catalog');
  await expect(catalog.locator('.spa-catalog-stat strong')).toHaveText(['—', '—', '1', '3']);
  await expect(catalog.locator('.spa-catalog-view button span')).toHaveText(['—', '1']);
  await expect(catalog.locator('.spa-catalog-results')).toContainText('Chưa tải được dữ liệu');
  await expect(catalog.locator('.spa-catalog-empty')).toHaveCount(0);
  await catalog.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(packageCard(page, 910)).toBeVisible();
  await expect(catalog.locator('.spa-catalog-results > span')).toHaveText('1 gói liệu trình');
  await expect(packageCard(page, 910).getByRole('button', { name: 'Sửa gói Liệu trình da khỏe', exact: true })).toBeDisabled();
  await expect(packageCard(page, 910).locator('.spa-package-discount, del')).toHaveCount(0);
  await catalog.getByLabel('Lọc nhóm liệu trình').selectOption('801');
  await expect(catalog.locator('.spa-catalog-results')).toContainText('Chưa tải được dữ liệu');
  await expect(catalog.locator('.spa-catalog-empty')).toHaveCount(0);
  await expect(packageCard(page, 910)).toHaveCount(0);
  await catalog.getByLabel('Lọc nhóm liệu trình').selectOption('ALL');
  await expect(packageCard(page, 910)).toBeVisible();
  await catalog.getByRole('button', { name: /^Dịch vụ lẻ/ }).click();
  await expect(catalog.locator('article[data-service-id]')).toHaveCount(0);
  await expect(catalog.locator('.spa-catalog-results')).toContainText('Chưa tải được dữ liệu');
});

test('beauty-report: a staff 403 preserves ready services without inventing eligibility', async ({ page }) => {
  await setup(page, { catalogFailures: { staff: { status: 403 } } });
  const catalog = page.locator('.spa-catalog');
  await expect(catalog.getByRole('alert')).toHaveCount(1);
  await expect(catalog.locator('.spa-catalog-stat strong')).toHaveText(['4', '4', '1', '—']);
  await expect(catalog.locator('.spa-catalog-results > span')).toHaveText('4 dịch vụ · 4 nhóm liệu trình');
  await expect(catalog.locator('article[data-service-id]')).toHaveCount(4);
  await expect(serviceCard(page, 901).getByRole('button', { name: /^Ma trận KTV\b/ })).toBeDisabled();
  await expect(serviceCard(page, 901)).toContainText('Chưa tải kỹ năng KTV');
  await expect(serviceCard(page, 901)).not.toContainText('0 KTV đủ điều kiện');
});

test('beauty-report: deduplication retains distinct source errors alongside the 403 banner', async ({ page }) => {
  await setup(page, { catalogFailures: {
    services: { status: 403 }, categories: { status: 403 }, facilities: { status: 403 },
    packages: { status: 500, message: 'Gói liệu trình tạm thời gián đoạn.' },
    staff: { status: 500, message: 'Kỹ năng KTV đang bảo trì.' },
  } });
  const alerts = page.locator('.spa-catalog').getByRole('alert');
  await expect(alerts).toHaveCount(3);
  await expect(alerts.filter({ hasText: '(403)' })).toHaveCount(1);
  await expect(alerts.filter({ hasText: 'Gói liệu trình tạm thời gián đoạn.' })).toHaveCount(1);
  await expect(alerts.filter({ hasText: 'Kỹ năng KTV đang bảo trì.' })).toHaveCount(1);
});

test('beauty-report: failed refresh hides stale catalog data and retry restores current results', async ({ page }) => {
  const failures = {};
  await setup(page, { catalogFailures: failures });
  const catalog = page.locator('.spa-catalog');
  await catalog.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(packageCard(page, 910)).toBeVisible();
  await expect(packageCard(page, 910).locator('.spa-package-discount')).toBeVisible();
  Object.assign(failures, Object.fromEntries(Object.keys(catalogEndpoints).map(key => [key, { status: 403 }])));
  await catalog.getByRole('button', { name: 'Làm mới danh mục Spa', exact: true }).click();
  await expect(catalog.getByRole('alert')).toHaveCount(1);
  await expect(catalog.locator('.spa-catalog-stat strong')).toHaveText(['—', '—', '—', '—']);
  await expect(packageCard(page, 910)).toHaveCount(0);
  await expect(catalog.locator('.spa-catalog-empty')).toHaveCount(0);
  await expect(catalog.getByLabel('Lọc nhóm liệu trình').locator('option[value="801"]')).toHaveCount(0);
  await catalog.getByRole('button', { name: /^Dịch vụ lẻ/ }).click();
  await expect(catalog.locator('article[data-service-id]')).toHaveCount(0);
  await expect(catalog.locator('.spa-catalog-results')).toContainText('Chưa tải được dữ liệu');
  for (const key of Object.keys(failures)) delete failures[key];
  await catalog.getByRole('button', { name: 'Làm mới danh mục Spa', exact: true }).click();
  await expect(catalog.getByRole('alert')).toHaveCount(0);
  await expect(catalog.locator('.spa-catalog-stat strong')).toHaveText(['4', '4', '1', '3']);
  await expect(catalog.locator('.spa-catalog-view button span')).toHaveText(['4', '1']);
  await expect(serviceCard(page, 901)).toBeVisible();
  await expect(catalog.locator('.spa-catalog-results > span')).toHaveText('4 dịch vụ · 4 nhóm liệu trình');
  await catalog.getByRole('button', { name: /^Gói liệu trình\b/ }).click();
  await expect(packageCard(page, 910)).toBeVisible();
  await expect(packageCard(page, 910).locator('.spa-package-discount')).toBeVisible();
});

test('deletion requires confirmation and retains the service when backend obligations reject deletion', async ({ page }) => {
  const { calls } = await setup(page, { serviceDeleteFailure: true });
  await page.getByRole('button', { name: 'Xóa dịch vụ Điều trị mụn chuyên sâu', exact: true }).click();
  expect(calls.filter(call => call.method === 'DELETE')).toHaveLength(0);
  const dialog = page.getByRole('dialog', { name: 'Xác nhận xóa dịch vụ' });
  await dialog.getByRole('button', { name: 'Xác nhận xóa', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('Vui lòng xử lý các lịch hẹn, vé đã mua');
  await expect(serviceCard(page, 901)).toHaveCount(1);
  expect(calls.filter(call => call.method === 'DELETE')).toHaveLength(1);
});

for (const [failureName, options] of [['failed GET', { resourceReadFailures: 1 }], ['malformed successful GET', { resourceReadMalformed: 1 }]]) {
  test(`an existing service cannot erase resource requirements after a ${failureName}`, async ({ page }) => {
    const { calls, requirements } = await setup(page, options);
    await page.getByRole('button', { name: 'Tài nguyên Điều trị mụn chuyên sâu', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Chỉnh sửa dịch vụ Spa' });
    await expect(dialog.getByRole('alert')).toContainText(/định mức/i);
    await expect(dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' })).toBeDisabled();
    expect(calls).toHaveLength(0);
    await dialog.getByRole('button', { name: 'Tải lại định mức', exact: true }).click();
    await expect(dialog.getByLabel('Loại tài nguyên 1')).toHaveValue('BED');
    await expect(dialog.getByLabel('Loại tài nguyên 2')).toHaveValue('LASER');
    await expect(dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' })).toBeEnabled();
    await dialog.getByRole('button', { name: 'Lưu dịch vụ & tài nguyên' }).click();
    await expect(dialog).toHaveCount(0);
    expect(calls).toHaveLength(0);
    expect(requirements.get(901)).toEqual([{ type: 'BED', units: 1 }, { type: 'LASER', units: 1 }]);
  });
}

test('a delayed safety policy from a closed service cannot overwrite the next service policy', async ({ page }) => {
  const { calls, releasePolicyA } = await setup(page, { policyRace: true });
  const firstRequest = page.waitForRequest(request => request.method() === 'GET' && request.url().includes('/spa/preparation/services/901'));
  await serviceCard(page, 901).getByRole('button', { name: 'Chính sách an toàn & khảo sát', exact: true }).click();
  await firstRequest;
  const firstDialog = page.getByRole('dialog', { name: `Chính sách An toàn & Khảo sát: ${fixtureServices[0].name}` });
  await expect(firstDialog.getByRole('button', { name: 'Lưu chính sách an toàn' })).toBeDisabled();
  await firstDialog.getByRole('button', { name: 'Hủy', exact: true }).click();
  await expect(firstDialog).toHaveCount(0);
  await serviceCard(page, 902).getByRole('button', { name: 'Chính sách an toàn & khảo sát', exact: true }).click();
  const secondDialog = page.getByRole('dialog', { name: `Chính sách An toàn & Khảo sát: ${fixtureServices[1].name}` });
  const warnings = secondDialog.getByLabel('Bắt buộc chuyên viên xác nhận cảnh báo an toàn & rủi ro trước khi bắt đầu dịch vụ');
  await expect(secondDialog.getByRole('button', { name: 'Lưu chính sách an toàn' })).toBeEnabled();
  await expect(warnings).not.toBeChecked();
  const delayedResponse = page.waitForResponse(response => response.request().method() === 'GET' && response.url().includes('/spa/preparation/services/901'));
  releasePolicyA();
  await (await delayedResponse).finished();
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await expect(warnings).not.toBeChecked();
  await secondDialog.getByRole('button', { name: 'Lưu chính sách an toàn' }).click();
  await expect(secondDialog).toHaveCount(0);
  expect(calls.filter(call => call.method === 'PUT' && call.path.includes('/spa/preparation/services/'))).toEqual([{ method: 'PUT', path: '/api/v1/admin/spa/preparation/services/902', body: { warningsRequired: false, requiredFormVersionIds: [] } }]);
});

test('a failed safety policy read blocks writes until reopening successfully loads the current policy', async ({ page }) => {
  const { calls } = await setup(page, { policyReadFailures: 1 });
  const openPolicy = () => serviceCard(page, 901).getByRole('button', { name: 'Chính sách an toàn & khảo sát', exact: true }).click();
  await openPolicy();
  const dialog = page.getByRole('dialog', { name: `Chính sách An toàn & Khảo sát: ${fixtureServices[0].name}` });
  const warnings = dialog.getByLabel('Bắt buộc chuyên viên xác nhận cảnh báo an toàn & rủi ro trước khi bắt đầu dịch vụ');
  await expect(dialog.getByRole('alert')).toContainText(/chính sách/i);
  await expect(dialog.getByRole('button', { name: 'Lưu chính sách an toàn' })).toBeDisabled();
  await expect(warnings).toBeDisabled();
  expect(calls).toHaveLength(0);
  await dialog.getByRole('button', { name: 'Hủy', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await openPolicy();
  await expect(dialog.getByRole('button', { name: 'Lưu chính sách an toàn' })).toBeEnabled();
  await expect(warnings).toBeChecked();
  await dialog.getByRole('button', { name: 'Lưu chính sách an toàn' }).click();
  await expect(dialog).toHaveCount(0);
  expect(calls.filter(call => call.method === 'PUT' && call.path.endsWith('/spa/preparation/services/901'))).toEqual([{ method: 'PUT', path: '/api/v1/admin/spa/preparation/services/901', body: { warningsRequired: true, requiredFormVersionIds: [] } }]);
});
