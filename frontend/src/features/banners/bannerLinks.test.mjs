import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bannerHref } from './bannerLinks.js';

test('banner destinations preserve configured routes, queries, anchors and external URLs', () => {
  assert.equal(bannerHref('products?categoryId=3'), '#/products?categoryId=3');
  assert.equal(bannerHref('/booking'), '#/booking');
  assert.equal(bannerHref('/#/treatments'), '#/treatments');
  assert.equal(bannerHref('#/products'), '#/products');
  assert.equal(bannerHref('#flash-sale'), '#flash-sale');
  assert.equal(bannerHref('https://example.com/deals'), 'https://example.com/deals');
});

test('unsafe and missing destinations never become clickable banner links', () => {
  for (const value of ['', '#', 'javascript:alert(1)', 'data:text/html,test', '//example.com', 'https://', 'products deals', 'java\nscript:alert(1)']) {
    assert.equal(bannerHref(value), null, value);
  }
});
