import { expect, test } from '@playwright/test';

test('admin can filter review sentiment alongside moderation status', async ({ page }) => {
  const admin = { id: 1, username: 'admin', roles: ['ROLE_ADMIN'], permissions: [] };
  await page.addInitScript((user) => {
    localStorage.setItem('beautyshop_admin_token', 'isolated-test-token');
    localStorage.setItem('beautyshop_admin_user', JSON.stringify(user));
  }, admin);

  const reviews = [
    { id: 11, userId: 1, content: 'Da bi kich ung', status: 'APPROVED', sentiment: 'NEGATIVE' },
    { id: 12, userId: 2, content: 'Dung rat tot', status: 'APPROVED', sentiment: 'POSITIVE' },
    { id: 13, userId: 3, content: 'Cho xu ly', status: 'PENDING', sentimentStatus: 'PENDING' },
  ];
  const requests = [];
  await page.route('**/actuator/health', (route) => route.fulfill({ json: { status: 'UP' } }));
  await page.route('**/api/v1/**', async (route) => {
    const { pathname, searchParams } = new URL(route.request().url());
    let data = [];
    if (pathname === '/api/v1/auth/profile') {
      data = admin;
    }
    if (pathname === '/api/v1/admin/reviews') {
      requests.push(Object.fromEntries(searchParams));
      const status = searchParams.get('status');
      const sentiment = searchParams.get('sentiment');
      const content = reviews.filter((review) => (
        (!status || review.status === status)
        && (!sentiment || (review.sentiment ?? review.sentimentStatus) === sentiment)
      ));
      data = { content, page: 0, totalElements: content.length, totalPages: 1 };
    }
    await route.fulfill({ json: { status: 200, success: true, data } });
  });

  await page.goto('/#/admin/reviews');
  await expect(page.getByRole('cell', { name: 'Ti\u00eau c\u1ef1c', exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'T\u00edch c\u1ef1c', exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Ch\u01b0a ph\u00e2n t\u00edch', exact: true })).toBeVisible();

  await page.locator('#admin-review-sentiment').selectOption('NEGATIVE');
  await page.locator('#admin-review-status').selectOption('APPROVED');
  await expect.poll(() => requests.at(-1)).toMatchObject({
    page: '0', status: 'APPROVED', sentiment: 'NEGATIVE',
  });
  await expect(page.getByRole('cell', { name: 'Ti\u00eau c\u1ef1c', exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'T\u00edch c\u1ef1c', exact: true })).toHaveCount(0);
});
