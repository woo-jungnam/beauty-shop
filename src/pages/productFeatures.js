export const lines = (text) => text.split('\n').map((line) => line.trim()).filter(Boolean);

export async function ingredientCatalog(fetchPage) {
  const items = [];
  for (let page = 0; ; page++) {
    const response = await fetchPage(page);
    const result = response.data ?? response;
    if (!Array.isArray(result) && !Array.isArray(result.content)) throw new Error('Danh mục thành phần không hợp lệ.');
    items.push(...(Array.isArray(result) ? result : result.content));
    if (Array.isArray(result) || page + 1 >= (result.totalPages ?? 1)) return items;
  }
}

export function attributePayload(rows, definitions) {
  const seen = new Set();
  return rows.map((row) => {
    const definition = definitions.find((item) => String(item.id) === String(row.attributeDefinitionId));
    if (!definition) throw new Error('Cần chọn thuộc tính hợp lệ.');
    const productVariantId = row.productVariantId ? Number(row.productVariantId) : null;
    const key = `${definition.id}:${productVariantId}`;
    if (seen.has(key)) throw new Error('Thuộc tính trùng trong cùng sản phẩm/SKU.');
    seen.add(key);
    const value = String(row.value).trim();
    if (!value) throw new Error('Giá trị thuộc tính không được trống.');
    if (definition.dataType === 'NUMBER' && !Number.isFinite(Number(value))) throw new Error('Giá trị phải là số hữu hạn.');
    if (definition.dataType === 'BOOLEAN' && !['true', 'false'].includes(value)) throw new Error('Chọn Có hoặc Không.');
    if (definition.dataType === 'STRING' && value.length > 500) throw new Error('Giá trị tối đa 500 ký tự.');
    return { attributeDefinitionId: definition.id, productVariantId, value };
  });
}

export function ingredientPayload(rows) {
  const seen = new Set();
  return rows.map((row) => {
    const ingredientId = Number(row.ingredientId);
    if (!Number.isSafeInteger(ingredientId) || ingredientId <= 0) throw new Error('Cần chọn thành phần hợp lệ.');
    if (seen.has(ingredientId)) throw new Error('Không chọn trùng thành phần.');
    seen.add(ingredientId);
    const concentration = String(row.concentration).trim() === '' ? null : Number(row.concentration);
    if (concentration !== null && (!Number.isFinite(concentration) || concentration < 0)) throw new Error('Nồng độ phải là số không âm.');
    const displayOrder = Number(row.displayOrder);
    if (!Number.isSafeInteger(displayOrder) || displayOrder < 0) throw new Error('Thứ tự phải là số nguyên không âm.');
    const concentrationUnit = row.concentrationUnit.trim();
    if (!concentrationUnit || concentrationUnit.length > 10) throw new Error('Đơn vị cần 1–10 ký tự.');
    return { ingredientId, concentration, concentrationUnit, keyActive: Boolean(row.keyActive), displayOrder };
  });
}
