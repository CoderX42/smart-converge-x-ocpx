import type { Page } from '@playwright/test';

import { randomUUID } from 'node:crypto';

import { expect, test } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const credentials = username && password ? { password, username } : undefined;

type ApiResult<T> = {
  code: number;
  data?: T;
  msg?: string;
};

type PageResult<T> = {
  records: T[];
  total: number;
};

type Resource = {
  code: string;
  id: string;
  status: string;
  version?: number;
};

type BrowserApiResponse<T> = {
  body: ApiResult<T>;
  status: number;
};

async function login(page: Page): Promise<string> {
  if (!credentials) {
    throw new Error(
      'E2E credentials are required before creating isolated configuration data.',
    );
  }
  await page.goto('/#/auth/login');
  await expect(page.getByText('OmniLink', { exact: true })).toBeVisible();
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
  const result = (await response.json()) as ApiResult<{ tokenValue: string }>;
  const token = result.data?.tokenValue;
  if (!token) {
    throw new Error(
      'Login must return a session token for authenticated API E2E.',
    );
  }
  await expect(page).not.toHaveURL(/#\/auth\/login/);
  return token;
}

async function api<T>(
  page: Page,
  token: string,
  path: string,
  options: {
    body?: Record<string, unknown>;
    method?: 'GET' | 'POST' | 'PUT';
  } = {},
): Promise<BrowserApiResponse<T>> {
  return page.evaluate(
    async ({ body, method, path, token }) => {
      const response = await fetch(`/api/boot${path}`, {
        body: body ? JSON.stringify(body) : undefined,
        credentials: 'same-origin',
        headers: {
          ...(body ? { 'Content-Type': 'application/json' } : {}),
          satoken: token,
        },
        method: method ?? 'GET',
      });
      return {
        body: (await response.json()) as ApiResult<T>,
        status: response.status,
      };
    },
    { ...options, path, token },
  );
}

function assertApiSuccess<T>(response: BrowserApiResponse<T>) {
  expect(response.status).toBe(200);
  expect(response.body.code).toBe(0);
  return response.body.data;
}

async function firstActiveResource(
  page: Page,
  token: string,
  resource: string,
  mediaId: string,
) {
  const result = assertApiSuccess(
    await api<PageResult<Resource>>(
      page,
      token,
      `/ad/${resource}?current=1&size=1&mediaId=${encodeURIComponent(mediaId)}&status=ACTIVE`,
    ),
  );
  const value = result?.records[0];
  if (!value) {
    throw new Error(
      `The isolated E2E environment must provide one ACTIVE ${resource} for media ${mediaId}.`,
    );
  }
  return value;
}

async function findByCode(
  page: Page,
  token: string,
  resource: string,
  mediaId: string,
  code: string,
) {
  const result = assertApiSuccess(
    await api<PageResult<Resource>>(
      page,
      token,
      `/ad/${resource}?current=1&size=10&mediaId=${encodeURIComponent(mediaId)}&keyword=${encodeURIComponent(code)}`,
    ),
  );
  const value = result?.records.find((item) => item.code === code);
  if (!value) {
    throw new Error(`${resource} creation did not persist code ${code}.`);
  }
  return value;
}

async function expectSearchResult(
  page: Page,
  path: string,
  placeholder: string,
  code: string,
) {
  await page.goto(`/#${path}`);
  await expect(page.locator('.mvp-page')).toBeVisible();
  await page.getByPlaceholder(placeholder).fill(code);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByText(code, { exact: true })).toBeVisible();
}

test.describe('广告投放配置写入 E2E', () => {
  test.skip(
    !credentials,
    'Set E2E_USERNAME and E2E_PASSWORD for an isolated local sandbox before running configuration E2E.',
  );

  test('创建并启用产品、追踪链接和监测任务后可在运营页追溯', async ({
    page,
  }) => {
    const token = await login(page);

    const mediaResult = assertApiSuccess(
      await api<PageResult<Resource>>(
        page,
        token,
        '/subjects/media?current=1&size=100',
      ),
    );
    const media = mediaResult?.records[0];
    if (!media) {
      throw new Error(
        'The isolated E2E environment must provide one writable media.',
      );
    }
    const [partner, channel] = await Promise.all([
      firstActiveResource(page, token, 'partners', media.id),
      firstActiveResource(page, token, 'media-channels', media.id),
    ]);

    const runId = randomUUID().replaceAll('-', '').slice(0, 12);
    const productCode = `e2e-sandbox-product-${runId}`;
    const linkCode = `e2e-sandbox-link-${runId}`;
    const taskCode = `e2e-sandbox-task-${runId}`;

    assertApiSuccess(
      await api<boolean>(page, token, '/ad/products', {
        body: {
          code: productCode,
          mediaId: media.id,
          name: `E2E sandbox product ${runId}`,
          partnerId: partner.id,
          platform: 'ANDROID',
          status: 'ACTIVE',
        },
        method: 'POST',
      }),
    );
    const product = await findByCode(
      page,
      token,
      'products',
      media.id,
      productCode,
    );

    assertApiSuccess(
      await api<boolean>(page, token, '/ad/tracking-links', {
        body: {
          code: linkCode,
          deviceType: 'ANDROID',
          mediaId: media.id,
          name: `E2E sandbox link ${runId}`,
          partnerId: partner.id,
          productId: product.id,
          redirectEnabled: false,
          status: 'DRAFT',
          targetUrl: 'http://localhost/e2e-sandbox-landing',
          templateText: 'click_id={{click_id}}&task_code={{task_code}}',
        },
        method: 'POST',
      }),
    );
    const draftLink = await findByCode(
      page,
      token,
      'tracking-links',
      media.id,
      linkCode,
    );
    assertApiSuccess(
      await api<boolean>(
        page,
        token,
        `/ad/tracking-links/${draftLink.id}/status`,
        {
          body: {
            reason: 'isolated E2E task dependency',
            status: 'ACTIVE',
            version: draftLink.version,
          },
          method: 'PUT',
        },
      ),
    );
    const activeLink = await findByCode(
      page,
      token,
      'tracking-links',
      media.id,
      linkCode,
    );
    expect(activeLink.status).toBe('ACTIVE');

    assertApiSuccess(
      await api<boolean>(page, token, '/ad/tracking-tasks', {
        body: {
          attributionWindowHours: 168,
          callbackTargetRef: 'sandbox',
          channelId: channel.id,
          code: taskCode,
          dailyConversionLimit: 1,
          dedupWindowSec: 300,
          impressionEnabled: false,
          linkId: activeLink.id,
          mediaId: media.id,
          name: `E2E sandbox task ${runId}`,
          status: 'DRAFT',
        },
        method: 'POST',
      }),
    );
    const draftTask = await findByCode(
      page,
      token,
      'tracking-tasks',
      media.id,
      taskCode,
    );
    assertApiSuccess(
      await api<boolean>(
        page,
        token,
        `/ad/tracking-tasks/${draftTask.id}/status`,
        {
          body: {
            reason: 'isolated E2E lifecycle verification',
            status: 'ACTIVE',
            version: draftTask.version,
          },
          method: 'PUT',
        },
      ),
    );
    const activeTask = await findByCode(
      page,
      token,
      'tracking-tasks',
      media.id,
      taskCode,
    );
    expect(activeTask.status).toBe('ACTIVE');

    await expectSearchResult(
      page,
      '/delivery/partners',
      '搜索合作方名称或编码',
      partner.code,
    );
    await expectSearchResult(
      page,
      '/delivery/products',
      '按名称或编码搜索',
      productCode,
    );
    await expectSearchResult(
      page,
      '/delivery/tracking-link',
      '链接名称或编码',
      linkCode,
    );
    await expectSearchResult(
      page,
      '/delivery/tracking-task',
      '任务名称或编码',
      taskCode,
    );
  });
});
