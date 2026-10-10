import { test, expect } from '@playwright/test';

async function catalog(page, { failLoad = false } = {}) {
  const operator = { id: 1, username: 'admin', roles: ['ROLE_ADMIN'] };
  await page.addInitScript((user) => {
    localStorage.setItem('beautyshop_admin_token', 'test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, operator);
  const product = { id: 101, name: 'Serum test', slug: 'serum-test', status: 'ACTIVE', attributeValues: [], variants: [{ id: 201, sku: 'TEST', variantName: '30ml', price: 100000 }] };
  const ingredient = { id: 7, name: 'Niacinamide', inciName: 'Niacinamide', slug: 'niacinamide', functions: ['humectant'], benefits: ['Dưỡng ẩm'], potentialConcerns: ['Thử trên vùng da nhỏ'], activeIngredient: true };
  let mappings = [];
  const writes = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (request.method() === 'PUT') {
      const body = request.postDataJSON();
      writes.push({ path, body });
      if (path.endsWith('/attributes')) product.attributeValues = body.map((row, index) => ({ ...row, id: index + 1, attributeDefinitionName: 'SPF', dataType: 'NUMBER' }));
      if (path === '/api/v1/admin/ingredients/products/101') mappings = body;
      return route.fulfill({ json: { data: body } });
    }
    if (failLoad && path === '/api/v1/admin/ingredients/products/101') return route.fulfill({ status: 503, json: { message: 'Không tải được thành phần' } });
    let data = [];
    if (path.endsWith('/auth/profile')) data = operator;
    else if (path === '/api/v1/admin/products') data = { content: [product], totalPages: 1, totalElements: 1 };
    else if (path === '/api/v1/admin/products/101') data = product;
    else if (path === '/api/v1/attributes') data = [{ id: 1, name: 'SPF', dataType: 'NUMBER' }];
    else if (path === '/api/v1/admin/ingredients/products/101') data = mappings;
    else if (path === '/api/v1/admin/ingredients') data = { content: [ingredient], totalPages: 1 };
    return route.fulfill({ json: { data } });
  });
  await page.goto('/#/admin/products');
  return writes;
}

test('Admin assigns product/SKU attributes and ingredient concentrations independently', async ({ page }) => {
  const writes = await catalog(page);
  await page.getByRole('button', { name: 'Thuộc tính & Thành phần', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: /Thuộc tính & Thành phần/ });
  await dialog.getByRole('button', { name: 'Thêm dòng thuộc tính' }).click();
  await dialog.getByLabel('Thuộc tính 1', { exact: true }).selectOption('1');
  await dialog.getByLabel('Phạm vi thuộc tính 1').selectOption('201');
  await dialog.getByLabel('Giá trị thuộc tính 1').fill('50');
  await dialog.getByRole('button', { name: 'Lưu thuộc tính', exact: true }).click();
  await expect(dialog.getByRole('status')).toContainText('Đã lưu thuộc tính');
  await dialog.getByRole('button', { name: 'Thêm dòng thành phần' }).click();
  await dialog.getByLabel('Thành phần 1', { exact: true }).selectOption('7');
  await dialog.getByLabel('Nồng độ 1 (tùy chọn)').fill('10');
  await dialog.getByLabel('Hoạt chất nổi bật của sản phẩm').check();
  await dialog.getByRole('button', { name: 'Lưu thành phần', exact: true }).click();
  await expect(dialog.getByRole('status')).toContainText('Đã lưu thành phần');
  expect(writes[0].body).toEqual([{ attributeDefinitionId: 1, productVariantId: 201, value: '50' }]);
  expect(writes[1].body).toEqual([{ ingredientId: 7, concentration: 10, concentrationUnit: '%', keyActive: true, displayOrder: 0 }]);
  await dialog.getByRole('button', { name: 'Đóng', exact: true }).click();
  await page.getByRole('button', { name: 'Thuộc tính & Thành phần', exact: true }).click();
  await expect(dialog.getByLabel('Giá trị thuộc tính 1')).toHaveValue('50');
  await expect(dialog.getByLabel('Nồng độ 1 (tùy chọn)')).toHaveValue('10');
});

test('Failed load cannot save empty replacements', async ({ page }) => {
  const writes = await catalog(page, { failLoad: true });
  await page.getByRole('button', { name: 'Thuộc tính & Thành phần', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: /Thuộc tính & Thành phần/ });
  await expect(dialog.getByRole('alert')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Lưu thuộc tính', exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: 'Lưu thành phần', exact: true })).toHaveCount(0);
  expect(writes).toEqual([]);
});

test('Editing ingredient keeps function, benefit and warning metadata', async ({ page }) => {
  const writes = await catalog(page);
  await page.getByRole('button', { name: /Bảng Hoạt chất & Thành phần/ }).click();
  await page.getByTitle('Sửa hoạt chất').click();
  const dialog = page.getByRole('dialog', { name: 'Cập nhật hoạt chất' });
  await expect(dialog.getByLabel('Chức năng', { exact: true })).toHaveValue('humectant');
  await dialog.getByRole('button', { name: 'Lưu hoạt chất', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(writes[0].body.functions).toEqual(['humectant']);
  expect(writes[0].body.benefits).toEqual(['Dưỡng ẩm']);
  expect(writes[0].body.potentialConcerns).toEqual(['Thử trên vùng da nhỏ']);
});
