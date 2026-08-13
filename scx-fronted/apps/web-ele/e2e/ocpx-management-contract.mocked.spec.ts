import type { Page, Route } from '@playwright/test';

import { expect, test } from '@playwright/test';

const accessStoreKey = 'smart-converge-x-2.1.3-dev-core-access';

const menus = [
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

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    body: JSON.stringify({ code: 0, data, msg: 'ok' }),
    contentType: 'application/json',
    status: 200,
  });
}

async function mockManagementApi(page: Page) {
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
        permissions: [
          'ocpx:advertiser:view',
          'ocpx:promotion-link:view',
          'ocpx:media:view',
          'ocpx:monitor-task:view',
          'ocpx:deduction-rule:list',
          'ocpx:report:view',
          'ocpx:flow-log:view',
        ],
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
    if (url.pathname.endsWith('/ocpx/promotion-links/advertiser-options')) {
      await fulfill(route, []);
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

  const query = page.getByLabel('按广告主查询');
  await expect(query).toBeVisible();
  await page.getByRole('button', { name: '显示或隐藏查询条件' }).click();
  await expect(query).toBeHidden();
});
