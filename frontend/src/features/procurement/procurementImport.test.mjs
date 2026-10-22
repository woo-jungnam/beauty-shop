import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as XLSX from 'xlsx';
import { IMPORT_HEADERS, MAX_IMPORT_BYTES, businessToday, matchVariants, orderPayload, parseExcel, validateImportFile, validateItem } from './procurementImport.js';

function fixture(rows = [['000123', 2, 10.25, 'LOT-A', '2099-01-01']], edit = () => {}) {
  const book = XLSX.utils.book_new();
  const sheet = XLSX.utils.aoa_to_sheet([IMPORT_HEADERS, ...rows]);
  XLSX.utils.book_append_sheet(book, sheet, 'PhieuMua');
  edit(book, sheet);
  return XLSX.write(book, { type: 'array', bookType: 'xlsx' });
}

test('preserves leading zero SKU, source rows and separate batches; payload excludes import metadata', async () => {
  const rows = await parseExcel(fixture([['000123', 2, 0, 'LOT-A', ''], [], ['000123', 3, 10.25, 'LOT-B', '2099-01-01']]));
  assert.equal(rows.length, 2); assert.equal(rows[1].sourceRow, 4);
  const matched = matchVariants(rows, [{ id: 12, sku: '000123', productName: 'Serum', isActive: false }]);
  assert.equal(matched[0].variantId, '12'); assert.match(matched[0].variantLabel, /ngưng bán/);
  assert.deepEqual(validateItem(matched[0]), {});
  assert.deepEqual(orderPayload({ supplierId: '1', warehouseId: '2', note: '', expectedDate: '', items: matched }).items[0], {
    variantId: 12, quantity: 2, unitCost: 0, batchCode: 'LOT-A', expirationDate: null,
  });
});

test('rejects blank, fractional, negative or overflowing numbers and invalid dates', () => {
  const base = { variantId: '12', quantity: '1', unitCost: '0', batchCode: '', expirationDate: '' };
  for (const value of ['', '0', '-1', '1.5', '2147483648', '1e2']) assert.ok(validateItem({ ...base, quantity: value }).quantity);
  for (const value of ['', '-1', 'NaN', 'Infinity', '1.001', '1,25', '10000000000.00']) assert.ok(validateItem({ ...base, unitCost: value }).unitCost);
  for (const value of ['2099-02-30', '2099-13-01', '2026-10-22']) assert.ok(validateItem({ ...base, expirationDate: value }, '2026-10-22').expirationDate);
  assert.ok(validateItem({ ...base, batchCode: 'x'.repeat(101) }).batchCode);
  assert.equal(businessToday(new Date('2026-10-22T18:00:00Z')), '2026-10-23');
});

test('Excel dates support 1900/1904 systems without timezone shift', async () => {
  for (const date1904 of [false, true]) {
    const rows = await parseExcel(fixture([['001', 1, 1, '', 50000]], book => { book.Workbook = { WBProps: { date1904 } }; }));
    const date = XLSX.SSF.parse_date_code(50000, { date1904 });
    assert.equal(rows[0].expirationDate, `${date.y}-${String(date.m).padStart(2, '0')}-${String(date.d).padStart(2, '0')}`);
  }
  const rows = await parseExcel(fixture([['001', 1, 1, '', 60]]));
  assert.ok(validateItem({ ...rows[0], variantId: '1' }).expirationDate);
});

test('formulas, errors and numeric SKU remain explicit editable errors', async () => {
  const rows = await parseExcel(fixture([[123, 1, 1, '', '']], (_, sheet) => {
    sheet.B2 = { t: 'n', v: 2, f: '1+1' }; sheet.D2 = { t: 'e', v: 7 };
  }));
  assert.ok(rows[0].cellErrors.sku); assert.ok(rows[0].cellErrors.quantity); assert.ok(rows[0].cellErrors.batchCode);
  assert.equal(matchVariants(rows, [{ id: 1, sku: '123' }])[0].variantId, '');
});

test('SKU resolution never guesses casing or ambiguous matches', () => {
  const item = { sku: 'Abc', variantId: 'old', quantity: '1', unitCost: '1', batchCode: '', expirationDate: '' };
  assert.equal(matchVariants([item], [{ id: 1, sku: 'ABC' }])[0].variantId, '');
  assert.equal(matchVariants([item], [{ id: 1, sku: 'Abc' }, { id: 2, sku: 'Abc' }])[0].variantId, '');
  assert.ok(validateItem(matchVariants([item], [])[0]).sku);
});

test('rejects bad structure, extra columns, sheets, empty and oversized workbooks without truncating', async () => {
  await assert.rejects(parseExcel(fixture([], (_, sheet) => { sheet.A1.v = 'MaHang'; })), /Tiêu đề/);
  await assert.rejects(parseExcel(fixture([], (_, sheet) => { sheet.B1.v = 'SKU'; })), /Tiêu đề/);
  await assert.rejects(parseExcel(fixture([], (_, sheet) => { sheet.F2 = { t: 's', v: 'extra' }; sheet['!ref'] = 'A1:F2'; })), /ngoài năm cột/);
  await assert.rejects(parseExcel(fixture([], book => { XLSX.utils.book_append_sheet(book, XLSX.utils.aoa_to_sheet([['extra']]), 'Extra'); })), /một sheet/);
  await assert.rejects(parseExcel(fixture([])), /chưa có dòng/);
  assert.equal((await parseExcel(fixture(Array.from({ length: 500 }, () => ['A', 1, 1, '', ''])))).length, 500);
  await assert.rejects(parseExcel(fixture(Array.from({ length: 501 }, () => ['A', 1, 1, '', '']))), /500 dòng/);
  await assert.rejects(parseExcel(new ArrayBuffer(MAX_IMPORT_BYTES + 1)), /2 MiB/);
  await assert.rejects(parseExcel(new TextEncoder().encode('SKU,SoLuong').buffer), /workbook/);
  assert.throws(() => validateImportFile({ name: 'test.xls', size: 10 }), /\.xlsx/);
  assert.throws(() => validateImportFile({ name: 'test.xlsx', size: MAX_IMPORT_BYTES + 1 }), /2 MiB/);
});
