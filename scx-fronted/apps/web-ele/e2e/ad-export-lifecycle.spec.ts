import type { Page } from '@playwright/test';

import { expect, test } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const credentials = username && password ? { password, username } : undefined;

type ExportTaskResponse = {
  code: number;
  data?: {
    id: string;
  };
};

function localDate(value = new Date()) {
  const timezoneOffset = value.getTimezoneOffset() * 60_000;
  return new Date(value.getTime() - timezoneOffset).toISOString().slice(0, 10);
}

async function login(page: Page) {
  if (!credentials) {
    throw new Error('E2E credentials are required before creating an export.');
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
  expect(response.status()).toBe(200);
  await expect(page).not.toHaveURL(/#\/auth\/login/);
}

test.describe('广告报表导出 E2E', () => {
  test.skip(
    !credentials,
    'Set E2E_USERNAME and E2E_PASSWORD before validating the protected export flow.',
  );

  test('创建日报导出、异步完成并下载 CSV 内容', async ({ page }) => {
    await login(page);
    await page.goto('/#/operations/exports');
    await expect(page.locator('.mvp-page')).toBeVisible();

    const reportDate = localDate();
    await page.getByPlaceholder('开始日期').fill(reportDate);
    await page.getByPlaceholder('开始日期').press('Tab');
    await page.getByPlaceholder('结束日期').fill(reportDate);
    await page.getByPlaceholder('结束日期').press('Tab');

    const createResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/boot/ad/export-tasks'),
    );
    await page
      .getByRole('button', { name: '创建导出任务', exact: true })
      .click();
    const response = await createResponse;
    expect(response.status()).toBe(200);
    const created = (await response.json()) as ExportTaskResponse;
    expect(created).toMatchObject({ code: 0 });
    const taskId = created.data?.id;
    if (!taskId) {
      throw new Error('Export creation must return the persisted task ID.');
    }

    const taskRow = page
      .locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: taskId });
    await expect
      .poll(
        async () => {
          await page.getByRole('button', { name: '查询', exact: true }).click();
          return taskRow.getByText('SUCCEEDED', { exact: true }).count();
        },
        { intervals: [250, 500, 1000], timeout: 15_000 },
      )
      .toBe(1);
    await expect(taskRow.getByText('效果日报', { exact: true })).toBeVisible();

    const downloadResponse = page.waitForResponse(
      (candidate) =>
        candidate.request().method() === 'GET' &&
        candidate
          .url()
          .includes(`/api/boot/ad/export-tasks/${taskId}/download`),
    );
    await taskRow.getByRole('button', { name: '下载', exact: true }).click();
    const csvResponse = await downloadResponse;
    expect(csvResponse.status()).toBe(200);
    expect(csvResponse.headers()['content-type']).toContain('text/csv');
    expect(await csvResponse.text()).toMatch(
      /^报告日期,媒体ID,合作方ID,产品ID,链接ID,任务ID,曝光,点击,有效点击/,
    );
  });
});
