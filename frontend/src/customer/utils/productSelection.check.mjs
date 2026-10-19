import assert from 'node:assert/strict';
import { chooseVariant, isClearanceProduct, productPath, variantPrice } from './productSelection.js';

const regular = { id: 1, sku: 'DEFAULT', price: 100000, isActive: true, isDefault: true, stockQuantity: 10 };
const deal = { id: 2, sku: 'CLEARANCE', price: 120000, discountPrice: 0, isActive: true, stockQuantity: 3 };
const product = { id: 10, variants: [regular, deal], clearanceVariant: deal, clearanceStock: 3 };
assert.equal(isClearanceProduct(product), true);
assert.equal(chooseVariant(product).id, 2);
assert.equal(variantPrice(chooseVariant(product)), 0);
assert.equal(productPath(product, deal.id), 'product/10?variantId=2');
assert.equal(chooseVariant({ variants: [regular] }, 2), null);
assert.equal(chooseVariant({ variants: [regular] }, ''), null);
assert.equal(chooseVariant({ ...product, clearanceVariant: null }), null);
assert.equal(chooseVariant({ ...product, clearanceVariant: { ...deal, isActive: false } }), null);
assert.equal(chooseVariant({ variants: [regular, deal] }).id, 1);
assert.equal(variantPrice({ price: 100000, discountPrice: 80000 }), 80000);
console.log('product selection checks passed');
