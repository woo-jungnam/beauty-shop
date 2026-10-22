import { test, expect } from '@playwright/test';
import * as XLSX from 'xlsx';

const admin = { id: 1, username: 'administrator', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: [] };
const ordersPath = '/api/v1/admin/procurement/orders';
const lookupPath = '/api/v1/admin/procurement/variants/lookup';
function excel(rows) {
  const book = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(book, XLSX.utils.aoa_to_sheet([
    ['SKU', 'SoLuong', 'DonGiaNhap', 'MaLo', 'HanSuDung'], ...rows,
  ]), 'PhieuMua');
  return { name: 'nhap-hang.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: XLSX.write(book, { type: 'buffer', bookType: 'xlsx' }) };
}
async function setup(page) {
  await page.addInitScript(user => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);
  const state = { calls: [], saveFails: false, lookupFails: false, beforeSave: null, beforeLookup: null };
  await page.route('**/actuator/health', route => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method();
    const body = request.postDataJSON();
    state.calls.push({ path, method, body });
    let data = [], status = 200;
    if (path === '/api/v1/auth/profile') data = admin;
    else if (path === '/api/v1/admin/procurement/suppliers') data = { content: [{ id: 1, code: 'SUP', name: 'Nhà cung cấp A', active: true }], totalPages: 1 };
    else if (path === '/api/v1/admin/warehouses') data = [{ id: 2, name: 'Kho A', isActive: true }];
    else if (path === lookupPath) {
      if (state.beforeLookup) await state.beforeLookup();
      if (state.lookupFails) status = 500;
      else data = body.skus.filter(sku => ['000123', 'NEW'].includes(sku)).map(sku => ({ id: sku === 'NEW' ? 13 : 12, sku, productName: 'Serum kiểm thử', variantName: '30ml', isActive: true }));
    } else if (path === ordersPath && method === 'POST') {
      if (state.beforeSave) await state.beforeSave();
      status = state.saveFails ? 500 : 200;
      data = { id: 9, orderNumber: 'PO-TEST', status: 'DRAFT', ...body };
    } else if (path === ordersPath) data = { content: [], totalPages: 1, totalElements: 0 };
    await route.fulfill({ status, json: { status, success: status === 200, data, message: status === 500 ? 'Lỗi kiểm thử' : '' } });
  });
  await page.goto('/#/admin/procurement');
  await page.getByRole('button', { name: 'Tạo phiếu mua', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Nhà cung cấp *', { exact: true }).selectOption('1');
  await dialog.getByLabel('Kho nhận *', { exact: true }).selectOption('2');
  return { state, dialog };
}
const saves = state => state.calls.filter(call => call.path === ordersPath && call.method === 'POST');

test('Excel preview fixes row errors and changed SKU before creating only one draft', async ({ page }) => {
  const { state, dialog } = await setup(page);
  await dialog.getByLabel('Nhập file Excel').setInputFiles(excel([['000123', 0, 10.25, 'LOT-A', '2099-01-01'], ['missing', 2, 0, 'LOT-B', '']]));
  await expect(dialog.getByText('Dòng Excel 2', { exact: true })).toBeVisible();
  const first = dialog.getByRole('region', { name: 'Dòng hàng 1', exact: true });
  const second = dialog.getByRole('region', { name: 'Dòng hàng 2', exact: true });
  await expect(first.getByText(/Serum kiểm thử/)).toBeVisible();
  const submit = dialog.getByRole('button', { name: 'Tạo phiếu nháp', exact: true });
  await expect(submit).toBeDisabled(); expect(saves(state)).toHaveLength(0);
  await first.getByLabel('Số lượng *', { exact: true }).fill('3');
  await second.getByLabel('SKU *', { exact: true }).fill('000123');
  await dialog.getByRole('button', { name: 'Đối chiếu lại SKU' }).click();
  await expect(submit).toBeEnabled();
  await first.getByLabel('SKU *', { exact: true }).fill('NEW');
  await expect(first.getByText(/ID 12/)).toHaveCount(0); await expect(submit).toBeDisabled();
  await dialog.getByRole('button', { name: 'Đối chiếu lại SKU' }).click();
  await expect(first.getByText(/ID 13/)).toBeVisible();
  let release;
  state.beforeSave = () => new Promise(resolve => { release = resolve; });
  await submit.evaluate(button => { button.click(); button.click(); });
  await expect.poll(() => saves(state).length).toBe(1);
  await expect(dialog.getByLabel('Ghi chú', { exact: true })).toBeDisabled();
  await page.keyboard.press('Escape'); await expect(dialog).toBeVisible();
  release(); await expect(dialog).toHaveCount(0);
  expect(saves(state)[0].body).toEqual({ supplierId: 1, warehouseId: 2, expectedDate: null, note: '', items: [
    { variantId: 13, quantity: 3, unitCost: 10.25, batchCode: 'LOT-A', expirationDate: '2099-01-01' },
    { variantId: 12, quantity: 2, unitCost: 0, batchCode: 'LOT-B', expirationDate: null },
  ] });
  expect(state.calls.some(call => /\/(approve|receive|receipts)$/.test(call.path))).toBe(false);
});

test('lookup and save failures preserve form; file structure errors never replace it', async ({ page }) => {
  const { state, dialog } = await setup(page);
  await dialog.getByLabel('ID biến thể *').fill('99');
  await dialog.getByLabel('Đơn giá vốn *').fill('5');
  page.on('dialog', prompt => prompt.accept());
  state.lookupFails = true;
  await dialog.getByLabel('Nhập file Excel').setInputFiles(excel([['000123', 2, 10, '', '']]));
  await expect(dialog.getByRole('alert')).toBeVisible();
  await expect(dialog.getByLabel('ID biến thể *')).toHaveValue('99');
  state.lookupFails = false;
  await dialog.getByLabel('Nhập file Excel').setInputFiles(excel([['000123', 2, 10, '', '']]));
  await expect(dialog.getByLabel('SKU *')).toHaveValue('000123');
  state.saveFails = true;
  await dialog.getByRole('button', { name: 'Tạo phiếu nháp' }).click();
  await expect(dialog.getByText(/Kiểm tra danh sách phiếu/)).toBeVisible();
  await expect(dialog.getByLabel('SKU *')).toHaveValue('000123');
  await dialog.getByLabel('Nhập file Excel').setInputFiles({ name: 'broken.xlsx', mimeType: 'application/octet-stream', buffer: Buffer.from('broken') });
  await expect(dialog.getByText(/không phải workbook/)).toBeVisible();
  await expect(dialog.getByLabel('SKU *')).toHaveValue('000123');
  expect(saves(state)).toHaveLength(1);
});

test('manual entry and downloading the template still work', async ({ page }) => {
  const { state, dialog } = await setup(page);
  const download = page.waitForEvent('download');
  await dialog.getByRole('button', { name: 'Tải mẫu Excel' }).click();
  expect((await download).suggestedFilename()).toBe('mau-phieu-mua.xlsx');
  await dialog.getByLabel('ID biến thể *').fill('12');
  await dialog.getByLabel('Đơn giá vốn *').fill('0');
  await dialog.getByRole('button', { name: 'Tạo phiếu nháp' }).click();
  await expect(dialog).toHaveCount(0); expect(saves(state)).toHaveLength(1);
  expect(state.calls.some(call => call.path === lookupPath)).toBe(false);
});

test('closing during lookup discards stale async results', async ({ page }) => {
  const { state, dialog } = await setup(page);
  let release;
  state.beforeLookup = () => new Promise(resolve => { release = resolve; });
  await dialog.getByLabel('Nhập file Excel').setInputFiles(excel([['000123', 2, 10, '', '']]));
  await expect.poll(() => state.calls.some(call => call.path === lookupPath)).toBe(true);
  await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0);
  state.beforeLookup = null; release();
  await page.getByRole('button', { name: 'Tạo phiếu mua', exact: true }).click();
  await expect(dialog.getByLabel('ID biến thể *')).toHaveValue('');
  expect(saves(state)).toHaveLength(0);
});
