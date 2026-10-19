export const knownNumber = value => {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
};
export const knownCount = value => {
  const number = knownNumber(value);
  return Number.isSafeInteger(number) ? number : null;
};
const validId = value => (typeof value === 'number' || (typeof value === 'string' && /^\d+$/.test(value)))
  && Number.isSafeInteger(Number(value)) && Number(value) > 0;
export const activeVariants = product => (Array.isArray(product?.variants) ? product.variants : [])
  .filter(variant => validId(variant?.id) && variant.isActive === true && !variant.isDeleted);
export const isClearanceProduct = product => product?.clearanceVariant != null
  || product?.earliestExpirationDate != null || product?.clearanceStock != null;
export const chooseVariant = (product, requestedId) => {
  const rows = activeVariants(product);
  // An explicit choice must never fall back to a different SKU.
  if (requestedId != null) return validId(requestedId)
    ? rows.find(row => Number(row.id) === Number(requestedId)) || null : null;
  if (isClearanceProduct(product)) {
    const variant = activeVariants({ variants: [product.clearanceVariant] })[0];
    return typeof variant?.sku === 'string' && variant.sku.trim() ? variant : null;
  }
  const withSku = rows.filter(row => typeof row.sku === 'string' && row.sku.trim());
  const choices = withSku.length ? withSku : rows;
  return choices.find(row => row.isDefault) || choices[0] || null;
};
export const variantPrice = variant => knownNumber(variant?.discountPrice ?? variant?.price);
export const productPath = (product, variantId) => `product/${product.id}${variantId == null ? '' : `?variantId=${encodeURIComponent(variantId)}`}`;
