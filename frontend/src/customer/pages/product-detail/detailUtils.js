export const unwrap = response => response?.data ?? response;
export const textList = value => [...new Set((Array.isArray(value) ? value : typeof value === 'string' ? [value] : [])
  .filter(item => typeof item === 'string').map(item => item.trim()).filter(Boolean))];
export { activeVariants, chooseVariant } from '../../utils/productSelection';
export const variantLabel = variant => variant?.volume || (variant?.volumeValue != null && variant?.volumeUnit ? `${variant.volumeValue} ${variant.volumeUnit}` : '')
  || variant?.variantName || variant?.color || (variant ? `Phân loại #${variant.id}` : 'Chưa cập nhật');
export const variantChoiceLabels = variants => new Map(variants.map(variant => {
  const label = variantLabel(variant);
  const duplicates = variants.filter(candidate => variantLabel(candidate) === label).length > 1;
  return [String(variant.id), duplicates ? [label, variant.color, variant.sku || `SKU #${variant.id}`].filter(Boolean).join(' · ') : label];
}));
export const productGallery = product => {
  const images = (product?.images || []).filter(image => typeof image.imageUrl === 'string' && image.imageUrl.trim())
    .map((image, index) => ({ ...image, key: image.id ?? index, index }))
    .sort((a, b) => Number(Boolean(b.isPrimary)) - Number(Boolean(a.isPrimary)) || Number(a.displayOrder ?? 9999) - Number(b.displayOrder ?? 9999) || a.index - b.index);
  return images.length ? images : product?.thumbnailUrl ? [{ imageUrl: product.thumbnailUrl, altText: product.name, key: 'thumbnail' }] : [];
};
export const IMAGE_TYPES = { PRODUCT_FRONT: 'Mặt trước', PRODUCT_BACK: 'Mặt sau', INGREDIENT_LIST: 'Thành phần', TEXTURE: 'Kết cấu', HOW_TO_USE: 'Cách dùng', SWATCH: 'Màu sắc', OTHER: 'Ảnh sản phẩm' };
export const SKIN_LABELS = { OILY: 'Da dầu', DRY: 'Da khô', COMBINATION: 'Da hỗn hợp', SENSITIVE: 'Da nhạy cảm', NORMAL: 'Da thường', ALL_SKIN: 'Mọi loại da', ALL: 'Mọi loại da' };
export const skinName = item => item.name || SKIN_LABELS[item.code || item.skinType] || item.code || item.skinType || '';
export const detailError = (error, fallback) => {
  const status = error?.status || error?.response?.status;
  if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (status === 403) return 'Bạn chưa có quyền thực hiện thao tác này.';
  const message = error?.response?.data?.message || error?.message || '';
  return /[àáạảãâăèéẹẻẽêìíịỉĩòóọỏõôơùúụủũưỳýỵỷỹđ]/i.test(message) && !/Exception|SQL|java\./i.test(message) ? message : fallback;
};
export const unitPriceReference = (variant, price) => {
  if (!variant || price == null || !Number.isFinite(Number(price)) || Number(price) < 0) return null;
  let value = Number(variant.volumeValue);
  let unit = String(variant.volumeUnit || '').toLowerCase().trim();
  if (!Number.isFinite(value) || value <= 0 || !['ml', 'l', 'g', 'kg'].includes(unit)) {
    const matched = String(variant.volume || '').match(/^\s*(\d+(?:[.,]\d+)?)\s*(ml|l|g|kg)\s*$/i);
    if (!matched) return null;
    value = Number(matched[1].replace(',', '.')); unit = matched[2].toLowerCase();
  }
  if (value <= 0) return null;
  const amount = value * (['l', 'kg'].includes(unit) ? 1000 : 1);
  return { value: Number(price) * 100 / amount, unit: ['ml', 'l'].includes(unit) ? 'ml' : 'g' };
};
