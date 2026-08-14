import type { Page, Route } from '@playwright/test';

import { expect, test } from '@playwright/test';

const accessStoreKey = 'smart-converge-x-2.1.3-dev-core-access';

const menus = [
  ['dashboard', '经营总览'],
  ['advertiser', '广告主管理'],
  ['promotion-link', '推广链接'],
  ['media', '媒体管理'],
  ['monitor-task', '监测任务'],
  ['deduction', '扣量规则'],
  ['report', '时段报表'],
  ['flow-log', '流程日志'],
].map(([path, title]) => ({
  component: `ocpx/${path}/index`,
  meta: { title },
  name: `Ocpx-${path}`,
  path: `/ocpx/${path}`,
}));

const emptyPage = { current: 1, records: [], size: 30, total: 0 };
const defaultPermissions = [
  'ocpx:advertiser:add',
  'ocpx:advertiser:delete',
  'ocpx:advertiser:edit',
  'ocpx:advertiser:recharge',
  'ocpx:advertiser:view',
  'ocpx:dashboard:view',
  'ocpx:deduction-rule:create',
  'ocpx:deduction-rule:delete',
  'ocpx:deduction-rule:list',
  'ocpx:deduction-rule:update',
  'ocpx:flow-log:view',
  'ocpx:media:add',
  'ocpx:media:delete',
  'ocpx:media:edit',
  'ocpx:media:view',
  'ocpx:monitor-task:add',
  'ocpx:monitor-task:delete',
  'ocpx:monitor-task:edit',
  'ocpx:monitor-task:view',
  'ocpx:promotion-link:add',
  'ocpx:promotion-link:delete',
  'ocpx:promotion-link:edit',
  'ocpx:promotion-link:view',
  'ocpx:report:export',
  'ocpx:report:view',
];

type MockManagementOptions = {
  advertiserRows?: Record<string, unknown>[];
  mediaRows?: Record<string, unknown>[];
  permissions?: string[];
};

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    body: JSON.stringify({ code: 0, data, msg: 'ok' }),
    contentType: 'application/json',
    status: 200,
  });
}

async function mockManagementApi(
  page: Page,
  {
    advertiserRows = [],
    mediaRows = [],
    permissions = defaultPermissions,
  }: MockManagementOptions = {},
) {
  await page.addInitScript(
    ({ key }) => {
      localStorage.setItem(
        key,
        JSON.stringify({
          accessCodes: [],
          accessToken: 'mock-ocpx-session',
          isAccessChecked: false,
          isLockScreen: false,
          lockScreenPassword: undefined,
          refreshToken: null,
        }),
      );
    },
    { key: accessStoreKey },
  );
  await page.route('**/api/boot/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/system/user/info')) {
      await fulfill(route, {
        avatar: '',
        desc: '',
        homePath: '/ocpx/advertiser',
        permissions,
        realName: 'Mock Admin',
        roles: ['ROLE_ADMIN'],
        token: 'mock-ocpx-session',
        userId: 'mock-admin',
        username: 'system',
      });
      return;
    }
    if (url.pathname.endsWith('/system/menu')) {
      await fulfill(route, menus);
      return;
    }
    if (url.pathname.endsWith('/ocpx/reports/options')) {
      await fulfill(route, {
        advertisers: [],
        media: [],
        monitorTasks: [],
        promotionLinks: [],
      });
      return;
    }
    if (url.pathname.endsWith('/ocpx/dashboard/options')) {
      await fulfill(route, {
        advertisers: [],
        media: [],
        monitorTasks: [],
        promotionLinks: [],
      });
      return;
    }
    if (url.pathname.endsWith('/ocpx/dashboard/summary')) {
      const ranking = (dimensionName: string, dimensionType: string) => ({
        dimensionKey: dimensionName,
        dimensionName,
        dimensionType,
        metrics: { CLICK: { amount: 0, count: 2 } },
      });
      await fulfill(route, {
        advertiserRanking: [ranking('广告主甲', 'ADVERTISER')],
        asOf: '2026-08-14T12:00:00',
        mediaRanking: [ranking('媒体甲', 'MEDIA')],
        monitorTaskRanking: [ranking('任务甲', 'MONITOR_TASK')],
        promotionLinkRanking: [ranking('链接甲', 'PROMOTION_LINK')],
        summary: { CLICK: { amount: 0, count: 8 } },
      });
      return;
    }
    if (url.pathname.endsWith('/ocpx/promotion-links/advertiser-options')) {
      await fulfill(route, []);
      return;
    }
    if (
      [
        '/ocpx/advertisers/options',
        '/ocpx/media/options',
        '/ocpx/monitor-tasks/options',
        '/ocpx/promotion-links/options',
      ].some((path) => url.pathname.endsWith(path))
    ) {
      await fulfill(route, []);
      return;
    }
    if (
      route.request().method() === 'GET' &&
      url.pathname.endsWith('/ocpx/advertisers')
    ) {
      await fulfill(route, { ...emptyPage, records: advertiserRows });
      return;
    }
    if (
      route.request().method() === 'GET' &&
      url.pathname.endsWith('/ocpx/media')
    ) {
      await fulfill(route, { ...emptyPage, records: mediaRows });
      return;
    }
    await fulfill(route, emptyPage);
  });
}

test('seven OCPX list pages honor the mocked management contract', async ({
  page,
}) => {
  await mockManagementApi(page);
  for (const [path, title] of [
    ['advertiser', '广告主管理'],
    ['promotion-link', '推广链接'],
    ['media', '媒体管理'],
    ['monitor-task', '监测任务'],
    ['deduction', '扣量规则'],
    ['report', '时段报表'],
    ['flow-log', '流程日志'],
  ]) {
    await page.goto(`/#/ocpx/${path}`);
    await expect(page.getByText(title, { exact: true }).first()).toBeVisible();
    await expect(page.locator('.el-table')).toBeVisible();
    await expect(
      page.getByRole('button', { name: '选择表格列' }),
    ).toBeVisible();
    if (path === 'report') {
      await expect(page.getByRole('button', { name: '导出' })).toBeVisible();
    }
  }

  await page.goto('/#/ocpx/advertiser');
  const tableHeader = page.locator('.el-table__header');
  await expect(tableHeader.getByText('广告主编号')).toBeVisible();
  const columnButton = page.getByRole('button', { name: '选择表格列' });
  await columnButton.focus();
  await expect(columnButton).toBeFocused();
  await columnButton.click();
  await page
    .locator('.el-popper:visible .el-checkbox')
    .filter({ hasText: '广告主编号' })
    .click();
  await expect(tableHeader.getByText('广告主编号')).toHaveCount(0);
  await page.keyboard.press('Escape');

  const query = page.getByRole('combobox', { name: '按广告主查询' });
  await expect(query).toBeVisible();
  await page.getByRole('button', { name: '显示或隐藏查询条件' }).click();
  await expect(query).toBeHidden();
});

test('dashboard keeps metric cards and four rankings without the list toolbar', async ({
  page,
}) => {
  await mockManagementApi(page);
  await page.goto('/#/ocpx/dashboard');

  await expect(
    page.getByText('经营总览', { exact: true }).first(),
  ).toBeVisible();
  await expect(page.getByText('CLICK', { exact: true })).toBeVisible();
  for (const title of [
    '广告主排行',
    '推广链接排行',
    '媒体排行',
    '监测任务排行',
  ]) {
    await expect(page.getByText(title, { exact: true })).toBeVisible();
  }
  await expect(page.getByRole('button', { name: '选择表格列' })).toHaveCount(0);
});

test('row operations and report export stay hidden without their action codes', async ({
  page,
}) => {
  await mockManagementApi(page, {
    permissions: ['ocpx:advertiser:view', 'ocpx:report:view'],
  });

  await page.goto('/#/ocpx/advertiser');
  await expect(page.getByRole('button', { name: '新增广告主' })).toHaveCount(0);
  await expect(page.locator('.el-table__header').getByText('操作')).toHaveCount(
    0,
  );

  await page.goto('/#/ocpx/report');
  await expect(page.getByRole('button', { name: '导出' })).toHaveCount(0);
});

test('advertiser create dialog validates and submits the exact onboarding payload', async ({
  page,
}) => {
  await mockManagementApi(page);
  await page.goto('/#/ocpx/advertiser');
  await page.getByRole('button', { name: '新增广告主' }).click();

  await expect(page.getByRole('dialog', { name: '新增广告主' })).toBeVisible();
  await expect(page.getByPlaceholder('请输入登录用户名')).toBeVisible();
  await expect(page.getByPlaceholder('请输入登录密码')).toBeVisible();
  await expect(page.getByPlaceholder('请输入宏参数')).toHaveValue('');

  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click();
  await expect(page.getByText('请输入广告主名称')).toBeVisible();

  await page.getByPlaceholder('请输入广告主名称').fill('测试广告主');
  await page.getByPlaceholder('请输入请求地址').fill('custom://127.0.0.1/bid');
  await page.getByPlaceholder('请输入登录用户名').fill('   ');
  await page.getByPlaceholder('请输入登录密码').fill('Advertiser1');
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click();
  await expect(page.getByText('请输入用户名')).toBeVisible();
  await expect(page.getByText('密码需包含特殊字符')).toBeVisible();
  await page.getByPlaceholder('请输入登录用户名').fill('advertiser_test');
  await page.getByPlaceholder('请输入登录密码').fill('Advertiser1!');
  await page.getByPlaceholder('请输入宏参数').fill('clickid=__CLICKID__');

  const requestPromise = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return (
      request.method() === 'POST' && url.pathname.endsWith('/ocpx/advertisers')
    );
  });
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click();
  const request = await requestPromise;
  expect(request.postDataJSON()).toEqual({
    bidUrl: 'custom://127.0.0.1/bid',
    macroParams: 'clickid=__CLICKID__',
    name: '测试广告主',
    password: 'Advertiser1!',
    username: 'advertiser_test',
  });
  await expect(page.getByRole('dialog', { name: '新增广告主' })).toBeHidden();
});

test('advertiser edit never renders or submits account credentials', async ({
  page,
}) => {
  await mockManagementApi(page, {
    advertiserRows: [
      {
        advertiserName: '广告主甲',
        balance: 0,
        bidUrl: 'custom://127.0.0.1/bid',
        createTime: '2026-08-14 10:00:00',
        id: 'advertiser-1',
        macroParams: 'clickid=__CLICKID__',
        updateTime: '2026-08-14 10:00:00',
        username: 'advertiser_a',
      },
    ],
  });
  await page.goto('/#/ocpx/advertiser');
  await expect(
    page.getByText('custom://127.0.0.1/bid?clickid=__CLICKID__'),
  ).toBeVisible();
  await page.getByRole('button', { name: '编辑' }).click();

  const dialog = page.getByRole('dialog', { name: '编辑广告主' });
  await expect(dialog).toBeVisible();
  await expect(page.getByPlaceholder('请输入登录用户名')).toHaveCount(0);
  await expect(page.getByPlaceholder('请输入登录密码')).toHaveCount(0);
  await page.getByPlaceholder('请输入广告主名称').fill('广告主甲-更新');

  const requestPromise = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return (
      request.method() === 'PUT' &&
      url.pathname.endsWith('/ocpx/advertisers/advertiser-1')
    );
  });
  await dialog.getByRole('button', { name: '确定' }).click();
  const request = await requestPromise;
  expect(request.postDataJSON()).toEqual({
    bidUrl: 'custom://127.0.0.1/bid',
    macroParams: 'clickid=__CLICKID__',
    name: '广告主甲-更新',
  });
});

test('ROLE_ADMIN delete actions call the aligned advertiser and media APIs', async ({
  page,
}) => {
  await mockManagementApi(page, {
    advertiserRows: [
      {
        advertiserName: '待删除广告主',
        balance: 0,
        bidUrl: 'custom://delete/bid',
        createTime: '2026-08-14 10:00:00',
        id: 'advertiser-delete',
        updateTime: '2026-08-14 10:00:00',
      },
    ],
    mediaRows: [
      {
        createTime: '2026-08-14 10:00:00',
        id: 'media-delete',
        mediaName: '待删除媒体',
        uri: '/feedback',
        updateTime: '2026-08-14 10:00:00',
      },
    ],
  });

  await page.goto('/#/ocpx/advertiser');
  const advertiserDelete = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return (
      request.method() === 'DELETE' &&
      url.pathname.endsWith('/ocpx/advertisers/advertiser-delete')
    );
  });
  await page.getByRole('button', { exact: true, name: '删除' }).click();
  await page.getByRole('button', { name: '确认删除' }).click();
  await advertiserDelete;

  await page.goto('/#/ocpx/media');
  const mediaDelete = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return (
      request.method() === 'DELETE' &&
      url.pathname.endsWith('/ocpx/media/media-delete')
    );
  });
  await page.getByRole('button', { exact: true, name: '删除' }).click();
  await page.getByRole('button', { name: '确认删除' }).click();
  await mediaDelete;
});
