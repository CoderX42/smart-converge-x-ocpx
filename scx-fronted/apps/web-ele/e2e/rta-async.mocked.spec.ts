import type { Page } from '@playwright/test';

import { expect, test } from '@playwright/test';

const resources = [
  'upstreams',
  'media',
  'bindings',
  'records',
  'logs',
  'reports',
];
const titles = [
  '广告主接入',
  '媒体接入',
  '任务绑定',
  '请求记录',
  '流程日志',
  '统计报表',
];
async function setup(page: Page, visible = resources) {
  await page.addInitScript(() =>
    localStorage.setItem(
      'smart-converge-x-2.1.3-dev-core-access',
      JSON.stringify({
        accessCodes: [],
        accessToken: 'rta-test',
        isAccessChecked: false,
        isLockScreen: false,
        refreshToken: null,
      }),
    ),
  );
  const seen: string[] = [];
  const saved: unknown[] = [];
  await page.route('**/api/boot/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown = { current: 1, records: [], size: 30, total: 0 };
    if (path.endsWith('/system/user/info'))
      data = {
        userId: 'rta-test',
        username: 'system',
        realName: 'RTA Tester',
        roles: ['ROLE_ADMIN'],
        homePath: '/ocpx/rta',
        permissions: visible.flatMap((kind) => [
          `ocpx:rta:${kind}:view`,
          ...(kind === 'media' ? ['ocpx:rta:media:edit'] : []),
        ]),
      };
    else if (path.endsWith('/system/menu'))
      data = [
        {
          name: 'RtaRoot',
          path: '/ocpx/rta',
          component: '',
          meta: { title: 'RTA 管理' },
          children: visible.map((kind) => ({
            name: `Rta-${kind}`,
            path: `/ocpx/rta/${kind}`,
            component: `ocpx/rta/${kind}/index`,
            meta: { title: titles[resources.indexOf(kind)] },
          })),
        },
      ];
    else if (path.includes('/rta/options/'))
      data = [{ id: 'media-a', name: '既有媒体名称' }];
    else if (path.includes('/ocpx/rta/')) {
      seen.push(`${path}${url.search}`);
      if (route.request().method() === 'POST') {
        saved.push(route.request().postDataJSON());
        data = true;
      } else if (path.endsWith('/records'))
        data = {
          current: 1,
          size: 30,
          total: 1,
          records: [
            {
              requestId: 'request-a',
              relayRequestId: 'a'.repeat(32),
              mediaId: 'media-a',
              mediaName: '既有媒体名称',
              status: 'ALLOW',
              matchedTaskIds: ['A', 'C'],
              deliveryStatus: 'DELIVERED',
              receivedAt: Date.now(),
              expiresAt: Date.now() + 300_000,
            },
          ],
        };
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, msg: 'ok', data }),
    });
  });
  return { seen, saved };
}
test('six independent pages support direct refresh and permission-first redirect', async ({
  page,
}) => {
  const { seen } = await setup(page);
  for (const kind of resources) {
    await page.goto(`/#/ocpx/rta/${kind}`);
    await expect(
      page.getByRole('button', { name: '查询', exact: true }),
    ).toBeVisible();
    await expect
      .poll(() => seen.some((url) => url.includes(`/rta/${kind}?`)))
      .toBe(true);
    await page.reload();
    await expect(
      page.getByRole('button', { name: '刷新', exact: true }),
    ).toBeVisible();
  }
});
test('parent chooses first permitted child', async ({ page }) => {
  await setup(page, ['records', 'logs']);
  await page.goto('/#/ocpx/rta');
  await expect(page).toHaveURL(/\/ocpx\/rta\/records/);
  await expect(page.getByRole('button', { name: '新增媒体接入' })).toHaveCount(
    0,
  );
});
test('media configuration uses existing mediaId and new configuration defaults off', async ({
  page,
}) => {
  const { saved } = await setup(page);
  await page.goto('/#/ocpx/rta/media?mediaId=media-a');
  await page.getByRole('button', { name: '新增媒体接入' }).click();
  const drawer = page.getByRole('dialog');
  await expect(drawer.getByRole('switch')).not.toBeChecked();
  await drawer
    .getByPlaceholder('https://允许的域名/路径')
    .fill('https://m.test/notify');
  await drawer
    .getByPlaceholder('配置文件中的凭据名称，不填写密钥')
    .fill('media-key');
  await drawer.getByRole('button', { name: '保存配置' }).click();
  await expect.poll(() => saved.length).toBe(1);
  expect(saved[0]).toMatchObject({
    mediaId: 'media-a',
    enabled: false,
    credentialRef: 'media-key',
  });
});
test('request details link to filtered logs', async ({ page }) => {
  const { seen } = await setup(page);
  await page.goto('/#/ocpx/rta/records');
  await page.getByRole('button', { name: '查看详情' }).click();
  await expect(page.getByRole('dialog')).toContainText('A、C');
  await page.getByRole('button', { name: '查看关联流程日志' }).click();
  await expect
    .poll(() =>
      seen.some(
        (url) =>
          url.includes('/rta/logs?') &&
          url.includes('requestId=request-a') &&
          url.includes('mediaId=media-a'),
      ),
    )
    .toBe(true);
});
