import type { Page } from '@playwright/test';

import { expect, test } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const credentials = username && password ? { password, username } : undefined;

async function login(page: Page) {
  if (!credentials) {
    throw new Error(
      'E2E credentials are required before starting the login flow.',
    );
  }
  await page.goto('/#/auth/login');
  await expect(page.getByText('智汇广告平台', { exact: true })).toBeVisible();
  await page.getByPlaceholder('请输入用户名').fill(credentials.username);
  await page
    .getByRole('textbox', { name: '密码', exact: true })
    .fill(credentials.password);
  const loginResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes('/api/boot/auth/token/login'),
  );
  await page.locator('button').filter({ hasText: '登录' }).click();
  const response = await loginResponse;
  expect(
    response.status(),
    '登录端点必须返回 HTTP 200，才能继续验证受保护运营页面。',
  ).toBe(200);
  await expect(page).not.toHaveURL(/#\/auth\/login/);
}

async function expectOperationalPage(
  page: Page,
  path: string,
  heading: string,
) {
  await page.goto(`/#${path}`);
  await expect(page.locator('.mvp-page')).toBeVisible();
  await expect(
    page.getByRole('heading', { name: heading, exact: true }),
  ).toBeVisible();
  await expect(page.locator('.el-table').first()).toBeVisible();
}

test.describe('广告运营只读 E2E', () => {
  test.skip(
    !credentials,
    'Set E2E_USERNAME and E2E_PASSWORD for an isolated local sandbox before running browser E2E.',
  );

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('登录后可读取合作方与产品主数据', async ({ page }) => {
    await expectOperationalPage(page, '/delivery/partners', '合作方');
    await expectOperationalPage(page, '/delivery/products', '产品管理');
  });

  test('登录后可读取追踪链接与监测任务', async ({ page }) => {
    await expectOperationalPage(
      page,
      '/delivery/tracking-link',
      '追踪链接编排',
    );
    await expectOperationalPage(
      page,
      '/delivery/tracking-task',
      '监测任务控制台',
    );
  });

  test('登录后可读取效果概览与导出任务', async ({ page }) => {
    await expectOperationalPage(page, '/reporting/overview', '广告效果概览');
    await expectOperationalPage(page, '/operations/exports', '报表导出任务');
  });
});
