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
  'ocpx:advertiser:role:manage',
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
  'ocpx:monitor-task:billing',
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
  dashboardOptions?: Record<string, unknown>;
  dashboardSummary?: null | Record<string, unknown>;
  mediaRows?: Record<string, unknown>[];
  permissions?: string[];
};

const readyDashboard = {
  account: {
    advertiserId: 'advertiser-1',
    availableBalance: 75,
    rejectedClickCount: 0,
    todaySpend: 25,
  },
  asOf: '2026-08-14T12:00:00',
  businessDate: '2026-08-14',
  dataStatus: 'READY',
  endDate: '2026-08-14',
  metrics: {
    CLICK: { status: 'OK', unit: 'COUNT', value: 10 },
    CONVERSION: { status: 'OK', unit: 'COUNT', value: 2 },
    CPA: { status: 'OK', unit: 'CNY', value: 12.5 },
    CPC: { status: 'OK', unit: 'CNY', value: 2.5 },
    CPM: { status: 'OK', unit: 'CNY', value: 250 },
    CTR: { status: 'OK', unit: 'PERCENT', value: 10 },
    CVR: { status: 'OK', unit: 'PERCENT', value: 20 },
    IMPRESSION: { status: 'OK', unit: 'COUNT', value: 100 },
    SPEND: { status: 'OK', unit: 'CNY', value: 25 },
  },
  startDate: '2026-08-14',
  timezone: 'Asia/Shanghai',
};

const emptyDashboard = {
  asOf: null,
  businessDate: '2026-08-14',
  dataStatus: 'EMPTY',
  endDate: '2026-08-14',
  metrics: {
    CLICK: { status: 'OK', unit: 'COUNT', value: 0 },
    CONVERSION: { status: 'OK', unit: 'COUNT', value: 0 },
    CPA: { status: 'ZERO_DENOMINATOR', unit: 'CNY', value: null },
    CPC: { status: 'ZERO_DENOMINATOR', unit: 'CNY', value: null },
    CPM: { status: 'ZERO_DENOMINATOR', unit: 'CNY', value: null },
    CTR: { status: 'ZERO_DENOMINATOR', unit: 'PERCENT', value: null },
    CVR: { status: 'ZERO_DENOMINATOR', unit: 'PERCENT', value: null },
    IMPRESSION: { status: 'OK', unit: 'COUNT', value: 0 },
    SPEND: { status: 'OK', unit: 'CNY', value: 0 },
  },
  startDate: '2026-08-14',
  timezone: 'Asia/Shanghai',
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
    dashboardOptions = {
      advertisers: [{ id: 'advertiser-1', label: '广告主甲' }],
      media: [],
      monitorTasks: [],
      promotionLinks: [],
    },
    dashboardSummary = readyDashboard,
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
      await fulfill(route, dashboardOptions);
      return;
    }
    if (url.pathname.endsWith('/ocpx/dashboard/summary')) {
      dashboardSummary === null
        ? await route.fulfill({
            body: JSON.stringify({ code: 1, msg: '真实数据读取失败' }),
            contentType: 'application/json',
            status: 503,
          })
        : await fulfill(route, dashboardSummary);
      return;
    }
    if (url.pathname.endsWith('/ocpx/promotion-links/advertiser-options')) {
      await fulfill(route, []);
      return;
    }
    if (url.pathname.endsWith('/ocpx/advertisers/account-role/permissions')) {
      await fulfill(route, {
        affectedAccountCount: 2,
        allDataScope: true,
        menuTree: [
          {
            children: [
              {
                children: [],
                id: 'perm_ocpx_advertiser_edit',
                name: '编辑广告主',
                parentId: 'menu_ocpx_advertiser',
                permission: 'ocpx:advertiser:edit',
                requiresAllDataScope: false,
                type: '1',
              },
              {
                children: [],
                id: 'perm_ocpx_advertiser_delete',
                name: '删除广告主',
                parentId: 'menu_ocpx_advertiser',
                permission: 'ocpx:advertiser:delete',
                requiresAllDataScope: true,
                type: '1',
              },
            ],
            id: 'menu_ocpx_advertiser',
            name: '广告主管理',
            parentId: '0',
            permission: 'ocpx:advertiser:view',
            requiresAllDataScope: false,
            type: '0',
          },
          {
            children: [],
            id: 'menu_ocpx_media',
            name: '媒体管理',
            parentId: '0',
            permission: 'ocpx:media:view',
            requiresAllDataScope: true,
            type: '0',
          },
        ],
        roleCode: 'ROLE_ADVERTISER',
        roleId: 'role_advertiser',
        roleName: '广告主',
        selectedMenuIds: [
          'menu_ocpx_advertiser',
          'menu_ocpx_media',
          'perm_ocpx_advertiser_delete',
          'perm_ocpx_advertiser_edit',
        ],
      });
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

test('dashboard shows account, date presets and metric cards without rankings', async ({
  page,
}) => {
  await mockManagementApi(page);
  await page.goto('/#/ocpx/dashboard');

  await expect(
    page.getByText('经营总览', { exact: true }).first(),
  ).toBeVisible();
  await expect(page.getByText('广告数据', { exact: true })).toBeVisible();
  await expect(page.getByText('总金额', { exact: true })).toBeVisible();
  await expect(page.getByText('今日消耗', { exact: true })).toBeVisible();
  for (const datePreset of ['今天', '昨天', '过去7天', '过去30天']) {
    await expect(
      page.getByRole('button', { name: datePreset, exact: true }),
    ).toBeVisible();
  }
  for (const metric of [
    '消耗',
    '展示数',
    '点击数',
    '转化数',
    '点击率',
    '转化率',
    'CPM',
    'CPC',
    'CPA',
  ]) {
    await expect(page.getByText(metric, { exact: true })).toBeVisible();
  }
  await expect(page.getByText('数据排行', { exact: true })).toHaveCount(0);
  await expect(
    page.getByText('按当前筛选范围展示业务对象表现', { exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole('button', { name: '选择表格列' })).toHaveCount(0);
});

test('dashboard presets derive their ranges from the server business date', async ({
  page,
}) => {
  await mockManagementApi(page);
  const bootstrapRequest = page.waitForRequest((request) =>
    new URL(request.url()).pathname.endsWith('/ocpx/dashboard/summary'),
  );
  await page.goto('/#/ocpx/dashboard');
  const bootstrap = await bootstrapRequest;
  const bootstrapUrl = new URL(bootstrap.url());
  expect(bootstrapUrl.searchParams.has('startDate')).toBe(false);
  expect(bootstrapUrl.searchParams.has('endDate')).toBe(false);
  await expect(page.getByText('2026-08-14', { exact: true })).toBeVisible();

  for (const [label, startDate, endDate] of [
    ['昨天', '2026-08-13', '2026-08-13'],
    ['过去7天', '2026-08-07', '2026-08-13'],
    ['过去30天', '2026-07-15', '2026-08-13'],
    ['今天', '2026-08-14', '2026-08-14'],
  ]) {
    const requestPromise = page.waitForRequest((request) =>
      new URL(request.url()).pathname.endsWith('/ocpx/dashboard/summary'),
    );
    await page.getByRole('button', { exact: true, name: label }).click();
    const request = await requestPromise;
    const requestUrl = new URL(request.url());
    expect(requestUrl.searchParams.get('startDate')).toBe(startDate);
    expect(requestUrl.searchParams.get('endDate')).toBe(endDate);
  }
});

test('dashboard custom date range is sent to the truthful summary API', async ({
  page,
}) => {
  await mockManagementApi(page);
  await page.goto('/#/ocpx/dashboard');
  await expect(page.getByText('2026-08-14', { exact: true })).toBeVisible();

  await page.locator('.filter-panel .el-date-editor').click();
  const leftCalendar = page.locator(
    '.el-picker-panel:visible .el-date-range-picker__content.is-left',
  );
  await expect(leftCalendar).toBeVisible();
  await leftCalendar
    .locator('td.available:not(.prev-month):not(.next-month)')
    .filter({ hasText: /^1$/ })
    .click();
  const requestPromise = page.waitForRequest((request) =>
    new URL(request.url()).pathname.endsWith('/ocpx/dashboard/summary'),
  );
  await leftCalendar
    .locator('td.available:not(.prev-month):not(.next-month)')
    .filter({ hasText: /^2$/ })
    .click();

  const request = await requestPromise;
  const requestUrl = new URL(request.url());
  expect(requestUrl.searchParams.get('startDate')).toBe('2026-08-01');
  expect(requestUrl.searchParams.get('endDate')).toBe('2026-08-02');
});

test('dashboard distinguishes verified empty data from an unavailable API', async ({
  page,
}) => {
  await mockManagementApi(page, { dashboardSummary: emptyDashboard });
  await page.goto('/#/ocpx/dashboard');

  await expect(page.getByText('当前范围暂无事件（已核验）')).toBeVisible();
  await expect(page.getByText('总金额', { exact: true })).toHaveCount(0);
  await expect(page.getByText('今日消耗', { exact: true })).toHaveCount(0);
  for (const label of ['展示数', '点击数', '转化数']) {
    await expect(
      page.locator('.metric-card').filter({ hasText: label }).locator('strong'),
    ).toHaveText('0');
  }
  for (const label of ['点击率', '转化率', 'CPM', 'CPC', 'CPA']) {
    await expect(
      page.locator('.metric-card').filter({ hasText: label }).locator('strong'),
    ).toHaveText('—');
  }

  await page.unroute('**/api/boot/**');
  await mockManagementApi(page, { dashboardSummary: null });
  await page.reload();
  await expect(
    page.getByText('首页真实数据读取失败，请检查服务状态后重试。'),
  ).toBeVisible();
  await expect(page.getByText('数据不可用')).toBeVisible();
  await expect(page.locator('.metric-card strong')).toHaveText([
    '—',
    '—',
    '—',
    '—',
    '—',
    '—',
    '—',
    '—',
    '—',
  ]);
});

test('dashboard warns when valid clicks were rejected for insufficient balance', async ({
  page,
}) => {
  await mockManagementApi(page, {
    dashboardSummary: {
      ...readyDashboard,
      account: {
        ...readyDashboard.account,
        rejectedClickCount: 3,
      },
    },
  });
  await page.goto('/#/ocpx/dashboard');

  await expect(
    page.getByText('3 次有效点击因余额不足未计费、未向广告主投递。', {
      exact: false,
    }),
  ).toBeVisible();
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

test('ROLE_ADMIN manages the shared advertiser role with explicit data scope', async ({
  page,
}) => {
  await mockManagementApi(page);
  await page.goto('/#/ocpx/advertiser');
  await page.getByRole('button', { name: '账号角色权限' }).click();

  const dialog = page.getByRole('dialog', { name: '广告主账号角色权限' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('会影响 2 个广告主账号')).toBeVisible();
  await expect(
    dialog.getByRole('switch', { name: '全量 OCPX 数据范围' }),
  ).toBeChecked();

  await dialog.getByRole('button', { name: '清空' }).click();
  await expect(
    dialog.getByRole('switch', { name: '全量 OCPX 数据范围' }),
  ).not.toBeChecked();
  await expect(
    dialog.getByRole('treeitem', { name: /媒体管理/ }).locator('.el-checkbox'),
  ).toHaveClass(/is-disabled/);
  await dialog
    .getByRole('treeitem', { exact: true, name: '编辑广告主' })
    .locator('.el-checkbox')
    .click();

  const requestPromise = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return (
      request.method() === 'PUT' &&
      url.pathname.endsWith('/ocpx/advertisers/account-role/permissions')
    );
  });
  await dialog.getByRole('button', { name: '保存权限' }).click();
  await page.getByRole('button', { name: '保存并使会话失效' }).click();
  const request = await requestPromise;
  expect(request.postDataJSON()).toEqual({
    allDataScope: false,
    menuIds: ['perm_ocpx_advertiser_edit'],
  });
  await expect(dialog).toBeHidden();
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
  await expect(
    page.getByText('仅无余额、无账务流水且无事件事实', {
      exact: false,
    }),
  ).toBeVisible();
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
