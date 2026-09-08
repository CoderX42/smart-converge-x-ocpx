import type { Page } from '@playwright/test';

import { expect, test } from '@playwright/test';

const resources = ['records', 'logs', 'reports'];
const titles = ['请求记录', '流程日志', '统计报表'];
const definitions = [
  {
    code: 'GENERIC_ASYNC',
    name: '通用异步',
    mode: 'ASYNC_CALLBACK',
    maxCandidates: 100,
    credentialRequired: true,
    fixedEndpoint: '',
    configFields: [],
    bindingFields: [
      { key: 'productId', label: '上游产品 ID' },
      { key: 'externalTaskId', label: '上游任务 ID' },
    ],
  },
  {
    code: 'WEIFENG_RTA',
    name: '微风 RTA',
    mode: 'SYNC_HTTP',
    maxCandidates: 1,
    credentialRequired: false,
    fixedEndpoint: 'https://rta.adapi.cn/rtaCommon/sendRta',
    configFields: [
      { key: 'aid', label: '微风广告计划 ID', pattern: '[0-9]{1,32}' },
      { key: 'did', label: '微风媒体标识', pattern: '[A-Za-z0-9._-]{1,64}' },
    ],
    bindingFields: [],
  },
];
async function setup(
  page: Page,
  visible = resources,
  configPermissions = true,
) {
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
  const saved: any[] = [];
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
        permissions: [
          ...visible.map((kind) => `ocpx:rta:${kind}:view`),
          ...['advertiser', 'media', 'monitor-task'].map(
            (kind) => `ocpx:${kind}:view`,
          ),
          ...(configPermissions
            ? ['upstreams', 'media', 'bindings'].flatMap((kind) => [
                `ocpx:rta:${kind}:view`,
                `ocpx:rta:${kind}:edit`,
              ])
            : []),
        ],
      };
    else if (path.endsWith('/system/menu'))
      data = [
        ...['advertiser', 'media', 'monitor-task'].map((kind) => ({
          name: `Ocpx-${kind}`,
          path: `/ocpx/${kind}`,
          component: `ocpx/${kind}/index`,
          meta: { title: kind },
        })),
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
    else if (path.endsWith('/rta/protocols')) data = definitions;
    else if (path.includes('/rta/options/')) {
      const kind = path.split('/').at(-1);
      data = {
        advertisers: [{ id: 'adv-a', name: '广告主甲' }],
        media: [{ id: 'media-a', name: '既有媒体名称' }],
        tasks: [{ id: 'A', name: '任务 A' }],
        upstreams: [
          { id: 'u-a', name: '微风计划5103', protocol: 'WEIFENG_RTA' },
        ],
      }[kind as 'advertisers' | 'media' | 'tasks' | 'upstreams'];
    } else if (path.includes('/ocpx/rta/')) {
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
      else if (path.endsWith('/bindings'))
        data = {
          current: 1,
          size: 30,
          total: 1,
          records: [
            {
              monitorTaskId: 'A',
              taskName: '任务 A',
              advertiserId: 'adv-a',
              advertiserName: '广告主甲',
              mediaId: 'media-a',
              mediaName: '既有媒体名称',
              version: 0,
              enabled: false,
            },
          ],
        };
    } else if (
      path.endsWith('/options') ||
      path.endsWith('/protocol-configs') ||
      path.endsWith('/macros') ||
      path.endsWith('/media-macros')
    )
      data = [];
    else if (path.endsWith('/ocpx/advertisers'))
      data = {
        current: 1,
        size: 30,
        total: 1,
        records: [
          {
            id: 'adv-a',
            advertiserName: '广告主甲',
            rtaAccessCount: 2,
            rtaEnabledCount: 1,
          },
        ],
      };
    else if (path.endsWith('/ocpx/media'))
      data = {
        current: 1,
        size: 30,
        total: 1,
        records: [{ id: 'media-a', mediaName: '既有媒体名称' }],
      };
    else if (path.endsWith('/ocpx/monitor-tasks'))
      data = {
        current: 1,
        size: 30,
        total: 1,
        records: [
          {
            id: 'A',
            monitorName: '任务 A',
            advertiserId: 'adv-a',
            mediaId: 'media-a',
            rtaEnabled: false,
          },
        ],
      };
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, msg: 'ok', data }),
    });
  });
  return { seen, saved };
}
test('three operational pages refresh directly and root selects first permitted page', async ({
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
  await page.goto('/#/ocpx/rta');
  await expect(page).toHaveURL(/rta\/records/);
});
test('media owns its configuration and new configuration defaults off', async ({
  page,
}) => {
  const { saved, seen } = await setup(page);
  await page.goto('/#/ocpx/media');
  await page.getByRole('button', { name: 'RTA 配置', exact: true }).click();
  await expect(page).toHaveURL(/rtaMediaId=media-a/);
  await page.reload();
  await page.getByRole('button', { name: '新增媒体接入' }).click();
  const drawer = page.getByRole('dialog', { name: '配置媒体接入' });
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
  expect(seen.some((url) => url.includes('mediaId=media-a'))).toBe(true);
});
test('advertiser owns multiple access configurations and protocol fields', async ({
  page,
}) => {
  const { saved, seen } = await setup(page);
  await page.goto('/#/ocpx/advertiser');
  await expect(page.getByText('1 已启用 / 2 份')).toBeVisible();
  await page.getByRole('button', { name: 'RTA 配置', exact: true }).click();
  await page.getByRole('button', { name: '新增广告主接入' }).click();
  const drawer = page.getByRole('dialog', { name: '配置广告主接入' });
  await drawer
    .locator('.el-form-item')
    .filter({ hasText: '接入名称' })
    .locator('input')
    .fill('微风 RTA');
  await drawer
    .locator('.el-form-item')
    .filter({ hasText: 'RTA 协议' })
    .locator('.el-select')
    .click();
  await page.getByRole('option', { name: '微风 RTA', exact: true }).click();
  await expect(
    drawer.getByText('同步返回决策 · 每次最多 1 个任务'),
  ).toBeVisible();
  await drawer
    .locator('.el-form-item')
    .filter({ hasText: '微风广告计划 ID' })
    .locator('input')
    .fill('5103');
  await drawer
    .locator('.el-form-item')
    .filter({ hasText: '微风媒体标识' })
    .locator('input')
    .fill('zsyd');
  await expect(drawer.getByText('服务端凭据引用', { exact: true })).toHaveCount(
    0,
  );
  await expect(drawer.getByRole('switch')).not.toBeChecked();
  await drawer.getByRole('button', { name: '保存配置' }).click();
  await expect.poll(() => saved.length).toBe(1);
  expect(saved[0]).toMatchObject({
    advertiserId: 'adv-a',
    protocol: 'WEIFENG_RTA',
    protocolConfig: { aid: '5103', did: 'zsyd' },
    enabled: false,
  });
  expect(seen.some((url) => url.includes('advertiserId=adv-a'))).toBe(true);
});
test('task binding inherits owner and hides unsupported mapping fields', async ({
  page,
}) => {
  const { saved } = await setup(page);
  await page.goto('/#/ocpx/monitor-task?rtaTaskId=A');
  await page
    .getByRole('dialog', { name: '任务 A · RTA 配置' })
    .getByRole('button', { name: '配置', exact: true })
    .click();
  const drawer = page.getByRole('dialog', { name: '配置任务绑定' });
  await drawer.locator('.el-select').click();
  await page.getByRole('option', { name: '微风计划5103' }).click();
  await expect(drawer.getByText('上游产品 ID', { exact: true })).toHaveCount(0);
  await drawer.getByRole('button', { name: '保存配置' }).click();
  await expect.poll(() => saved.length).toBe(1);
  expect(saved[0]).toMatchObject({
    monitorTaskId: 'A',
    upstreamId: 'u-a',
    enabled: false,
  });
});
test('configuration permission is independent of advertiser view permission', async ({
  page,
}) => {
  await setup(page, resources, false);
  await page.goto('/#/ocpx/advertiser?rtaAdvertiserId=adv-a');
  await expect(
    page.getByRole('button', { name: 'RTA 配置', exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
test('request details link to scoped logs', async ({ page }) => {
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
