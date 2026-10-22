export const IMPORT_HEADERS = ['SKU', 'SoLuong', 'DonGiaNhap', 'MaLo', 'HanSuDung'];
export const MAX_IMPORT_ROWS = 500;
export const MAX_IMPORT_BYTES = 2 * 1024 * 1024;

export function businessToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  return ['year', 'month', 'day'].map(type => parts.find(p => p.type === type).value).join('-');
}

export function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function validateItem(item, today = businessToday()) {
  const errors = { ...item.cellErrors };
  if (item.sku !== undefined) {
    if (!item.sku.trim() || item.sku.trim().length > 100) errors.sku = 'SKU bắt buộc, tối đa 100 ký tự.';
    else if (!item.variantId) errors.sku ||= 'SKU chưa được đối chiếu hoặc không tồn tại.';
  } else if (!/^\d+$/.test(String(item.variantId)) || !Number.isSafeInteger(Number(item.variantId)) || Number(item.variantId) <= 0) {
    errors.variantId = 'ID biến thể phải là số nguyên dương.';
  }
  if (!/^\d+$/.test(String(item.quantity)) || Number(item.quantity) < 1 || Number(item.quantity) > 2147483647) {
    errors.quantity = 'Số lượng phải là số nguyên từ 1 đến 2147483647.';
  }
  if (!/^\d+(\.\d{1,2})?$/.test(String(item.unitCost)) || !Number.isFinite(Number(item.unitCost)) || Number(item.unitCost) > 9999999999.99) {
    errors.unitCost = 'Giá vốn từ 0 đến 9999999999.99, tối đa 2 số lẻ; dùng dấu chấm thập phân.';
  }
  if (item.batchCode.trim().length > 100) errors.batchCode = 'Mã lô tối đa 100 ký tự.';
  if (item.expirationDate && (!validDate(item.expirationDate) || item.expirationDate <= today)) {
    errors.expirationDate = 'Hạn dùng phải là ngày hợp lệ sau hôm nay (YYYY-MM-DD).';
  }
  return errors;
}

export function matchVariants(items, variants) {
  const bySku = new Map();
  for (const variant of variants) {
    const matches = bySku.get(variant.sku) || [];
    matches.push(variant);
    bySku.set(variant.sku, matches);
  }
  return items.map(item => {
    if (item.sku === undefined) return item;
    const matches = bySku.get(item.sku.trim()) || [];
    const variant = matches.length === 1 && !item.cellErrors?.sku ? matches[0] : null;
    return { ...item, variantId: variant ? String(variant.id) : '',
      variantLabel: variant ? `${variant.productName} · ${variant.variantName || variant.sku}${variant.isActive === false ? ' (ngưng bán)' : ''}` : '' };
  });
}

export function orderPayload(form) {
  return {
    supplierId: Number(form.supplierId), warehouseId: Number(form.warehouseId),
    expectedDate: form.expectedDate || null, note: form.note,
    items: form.items.map(item => ({ variantId: Number(item.variantId), quantity: Number(item.quantity),
      unitCost: Number(item.unitCost), batchCode: item.batchCode.trim(), expirationDate: item.expirationDate || null })),
  };
}

export function validateImportFile(file) {
  if (!/\.xlsx$/i.test(file.name)) throw new Error('Chỉ nhận file .xlsx theo mẫu.');
  if (!file.size || file.size > MAX_IMPORT_BYTES) throw new Error('File phải có dữ liệu và không vượt quá 2 MiB.');
}

export async function parseExcel(buffer) {
  if (!buffer.byteLength || buffer.byteLength > MAX_IMPORT_BYTES) throw new Error('File vượt giới hạn 2 MiB hoặc rỗng.');
  const magic = new Uint8Array(buffer, 0, Math.min(4, buffer.byteLength));
  if (magic[0] !== 0x50 || magic[1] !== 0x4b || magic[2] !== 3 || magic[3] !== 4) throw new Error('File không phải workbook .xlsx hợp lệ.');
  const XLSX = await import('xlsx');
  let book;
  try {
    book = XLSX.read(buffer, { type: 'array', cellFormula: true, cellDates: false, sheetRows: MAX_IMPORT_ROWS + 2 });
  } catch {
    throw new Error('Không đọc được Excel. Hãy lưu lại bằng định dạng .xlsx.');
  }
  if (book.SheetNames.length !== 1) throw new Error('Excel phải có đúng một sheet theo mẫu.');
  const sheet = book.Sheets[book.SheetNames[0]];
  if (!sheet?.['!ref']) throw new Error('Sheet không có dữ liệu.');
  const range = XLSX.utils.decode_range(sheet['!fullref'] || sheet['!ref']);
  // ponytail: tối đa 501 hàng vật lý kể cả header; file lớn hơn cần luồng import riêng.
  if (range.e.r > MAX_IMPORT_ROWS) throw new Error('Excel chỉ được có tối đa 500 dòng dưới tiêu đề; không cắt dữ liệu.');
  for (let col = 0; col < IMPORT_HEADERS.length; col++) {
    const cell = sheet[XLSX.utils.encode_cell({ r: 0, c: col })];
    if (cell?.f || cell?.t === 'e' || String(cell?.v ?? '').trim() !== IMPORT_HEADERS[col]) {
      throw new Error(`Tiêu đề phải đúng thứ tự: ${IMPORT_HEADERS.join(', ')}.`);
    }
  }
  for (const [address, cell] of Object.entries(sheet)) {
    if (address.startsWith('!')) continue;
    if (XLSX.utils.decode_cell(address).c >= IMPORT_HEADERS.length && (cell.f || String(cell.v ?? '').trim())) {
      throw new Error('Có dữ liệu ngoài năm cột của mẫu.');
    }
  }
  const keys = ['sku', 'quantity', 'unitCost', 'batchCode', 'expirationDate'];
  const rows = [];
  for (let r = 1; r <= range.e.r; r++) {
    const cells = keys.map((_, c) => sheet[XLSX.utils.encode_cell({ r, c })]);
    if (cells.every(cell => !cell?.f && String(cell?.v ?? '').trim() === '')) continue;
    const item = { sourceRow: r + 1, variantId: '', cellErrors: {} };
    cells.forEach((cell, c) => {
      const key = keys[c];
      item[key] = String(cell?.v ?? '').trim();
      if (cell?.f || cell?.t === 'e') {
        item[key] = '';
        item.cellErrors[key] = 'Ô lỗi hoặc công thức không được nhập; thay bằng giá trị thực.';
      } else if (key === 'sku' && cell && cell.t !== 's') {
        item.cellErrors.sku = 'SKU phải là text để giữ số 0 đầu; nhập lại mã chính xác.';
      } else if (key === 'expirationDate' && cell?.t === 'n') {
        const date = Number.isInteger(cell.v) && XLSX.SSF.parse_date_code(cell.v, { date1904: !!book.Workbook?.WBProps?.date1904 });
        item.expirationDate = date ? `${String(date.y).padStart(4, '0')}-${String(date.m).padStart(2, '0')}-${String(date.d).padStart(2, '0')}` : item.expirationDate;
      }
    });
    rows.push(item);
  }
  if (!rows.length) throw new Error('Excel chưa có dòng hàng hóa.');
  return rows;
}

export async function downloadTemplate() {
  const XLSX = await import('xlsx');
  const sheet = XLSX.utils.aoa_to_sheet([IMPORT_HEADERS, ['000123', 10, 150000, 'LOT-001', '']]);
  sheet.A2.z = '@';
  sheet['!cols'] = [{ wch: 24 }, { wch: 14 }, { wch: 20 }, { wch: 24 }, { wch: 18 }];
  const book = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(book, sheet, 'PhieuMua');
  XLSX.writeFile(book, 'mau-phieu-mua.xlsx');
}
