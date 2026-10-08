import assert from 'node:assert/strict';
import { serverBaseUrl } from './baseUrl.js';

const root = 'https://api.dermascan.world';
assert.equal(serverBaseUrl(`${root}/api`), root);
assert.equal(serverBaseUrl(`${root}/api/`), root);
assert.equal(`${serverBaseUrl(`${root}/api`)}/api/v1/products`, `${root}/api/v1/products`);
assert.equal(serverBaseUrl(`${root}/api/v1`), root);
assert.equal(serverBaseUrl(`${root}/api/v1/`), root);
assert.equal(serverBaseUrl(`${root}/`), root);
assert.equal(serverBaseUrl(), '');
assert.equal(`${serverBaseUrl(`${root}/api/v1`)}/api/v1/products`, `${root}/api/v1/products`);
assert.equal(`${serverBaseUrl(`${root}/api/v1`)}/uploads/test.png`, `${root}/uploads/test.png`);
console.log('API base URL checks passed');
