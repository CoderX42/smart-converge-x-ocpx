import type {
  APIRequestContext,
  APIResponse,
  Browser,
  Page,
  TestInfo,
} from '@playwright/test';

import { randomUUID } from 'node:crypto';

import { expect, request, test } from '@playwright/test';

type ApiResult<T> = {
  code: number;
  data?: T;
  msg?: string;
};

type PageResult<T> = {
  records: T[];
  total: number;
};

type Media = {
  code: string;
  id: string;
  name: string;
};

type ChannelClient = {
  clientId: string;
  currentKeyVersion: string;
  id: string;
  mediaId: string;
  sourceCode: string;
  status: string;
};

type CredentialIssue = {
  channelClientId: string;
  clientId: string;
  keyVersion: string;
  secret: string;
};

type RoleName = 'admin' | 'analyst' | 'noscope' | 'operator';

type RoleCredentials = {
  password: string;
  username: string;
};

type RoleSession = {
  api: APIRequestContext;
  page: Page;
  token: string;
};

const apiTarget = process.env.E2E_API_TARGET ?? 'http://127.0.0.1:9999';
const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173';
const authorizedMediaId = process.env.L0_AUTHORIZED_MEDIA_ID;
const unauthorizedMediaId = process.env.L0_UNAUTHORIZED_MEDIA_ID;
const safeWriteMediaId = process.env.L0_SAFE_WRITE_MEDIA_ID;
const productionWriteAllowed = process.env.L0_ALLOW_PRODUCTION_WRITE === 'true';
const runId =
  process.env.L0_RUN_ID ??
  randomUUID().replaceAll('-', '').slice(0, 12).toLowerCase();

const roles: Record<RoleName, RoleCredentials | undefined> = {
  admin: credentials('ADMIN'),
  analyst: credentials('ANALYST'),
  noscope: credentials('NOSCOPE'),
  operator: credentials('OPERATOR'),
};

const readyForRead =
  Object.values(roles).every(Boolean) &&
  Boolean(authorizedMediaId && unauthorizedMediaId);
const readyForWrite =
  readyForRead &&
  productionWriteAllowed &&
  Boolean(safeWriteMediaId) &&
  safeWriteMediaId === authorizedMediaId;

function credentials(role: string): RoleCredentials | undefined {
  const username = process.env[`L0_${role}_USERNAME`];
  const password = process.env[`L0_${role}_PASSWORD`];
  return username && password ? { password, username } : undefined;
}

async function login(browser: Browser, role: RoleName): Promise<RoleSession> {
  const roleCredentials = roles[role];
  if (!roleCredentials) {
    throw new Error(`Missing credentials for role ${role}.`);
  }

  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  await page.goto('/#/auth/login');
  await expect(page.getByText('智汇广告平台', { exact: true })).toBeVisible();
  await page.getByPlaceholder('请输入用户名').fill(roleCredentials.username);
  await page
    .getByRole('textbox', { name: '密码', exact: true })
    .fill(roleCredentials.password);

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
    throw new Error(`Login did not return a token for role ${role}.`);
  }
  await expect(page).not.toHaveURL(/#\/auth\/login/);

  const api = await request.newContext({
    baseURL: apiTarget,
    extraHTTPHeaders: { satoken: token },
  });
  return { api, page, token };
}

async function closeSession(session: RoleSession | undefined) {
  if (!session) return;
  await session.api.dispose();
  await session.page.context().close();
}

async function json<T>(response: APIResponse): Promise<ApiResult<T>> {
  return (await response.json()) as ApiResult<T>;
}

async function browserApi<T>(
  page: Page,
  token: string,
  path: string,
): Promise<{ body: ApiResult<T>; status: number }> {
  return page.evaluate(
    async ({ path, token }) => {
      const response = await fetch(`/api/boot${path}`, {
        credentials: 'same-origin',
        headers: { satoken: token },
      });
      return {
        body: (await response.json()) as ApiResult<T>,
        status: response.status,
      };
    },
    { path, token },
  );
}

async function saveEvidence(
  page: Page,
  testInfo: TestInfo,
  role: RoleName,
  name: string,
) {
  await page.screenshot({
    fullPage: true,
    path: testInfo.outputPath(`${role}-${name}.png`),
  });
}

function expectResourceHidden(
  unauthorized: ApiResult<unknown>,
  nonexistent: ApiResult<unknown>,
) {
  expect(unauthorized.code).toBe(nonexistent.code);
  expect(unauthorized.msg).toBe(nonexistent.msg);
  expect(unauthorized.data).toBe(nonexistent.data);
}

function required(value: string | undefined, name: string): string {
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

function sessionFor(
  sessions: Partial<Record<RoleName, RoleSession>>,
  role: RoleName,
): RoleSession {
  const session = sessions[role];
  if (!session) throw new Error(`Missing authenticated session for ${role}.`);
  return session;
}

test.use({ trace: 'off', video: 'off' });

test.describe.serial('L0 四角色生产权限门禁', () => {
  test.skip(
    !readyForRead,
    'Inject all four L0 role credentials and authorized/unauthorized media ids.',
  );

  test('浏览器与直接 API 的媒体、渠道客户端只读矩阵', async ({
    browser,
  }, testInfo) => {
    const sessions: Partial<Record<RoleName, RoleSession>> = {};
    try {
      for (const role of Object.keys(roles) as RoleName[]) {
        sessions[role] = await login(browser, role);
      }

      const readableMediaId = required(
        authorizedMediaId,
        'L0_AUTHORIZED_MEDIA_ID',
      );
      const hiddenMediaId = required(
        unauthorizedMediaId,
        'L0_UNAUTHORIZED_MEDIA_ID',
      );
      const nonexistentMediaId = `l0-nonexistent-${runId}`;
      for (const role of ['admin', 'operator', 'analyst'] as RoleName[]) {
        const session = sessionFor(sessions, role);
        const mediaPageResponse = await session.api.get(
          '/boot/subjects/media?current=1&size=100',
        );
        expect(mediaPageResponse.status()).toBe(200);
        const mediaPage = await json<PageResult<Media>>(mediaPageResponse);
        expect(mediaPage.code).toBe(0);
        const ids = mediaPage.data?.records.map((item) => item.id) ?? [];
        expect(ids).toContain(readableMediaId);
        if (role !== 'admin') {
          expect(ids).not.toContain(hiddenMediaId);
        }

        const authorized = await json<Media>(
          await session.api.get(
            `/boot/subjects/media/${encodeURIComponent(readableMediaId)}`,
          ),
        );
        expect(authorized.code).toBe(0);

        const unauthorized = await json<Media>(
          await session.api.get(
            `/boot/subjects/media/${encodeURIComponent(hiddenMediaId)}`,
          ),
        );
        const nonexistent = await json<Media>(
          await session.api.get(
            `/boot/subjects/media/${encodeURIComponent(nonexistentMediaId)}`,
          ),
        );
        if (role === 'admin') {
          expect(unauthorized.code).toBe(0);
          expect(nonexistent.code).toBe(404);
        } else {
          expectResourceHidden(unauthorized, nonexistent);
        }

        const channelPage = await json<PageResult<ChannelClient>>(
          await session.api.get(
            `/boot/ad/channel-clients?current=1&size=20&mediaId=${encodeURIComponent(readableMediaId)}`,
          ),
        );
        expect(channelPage.code).toBe(0);
        expect(
          channelPage.data?.records.every(
            (item) => item.mediaId === readableMediaId,
          ),
        ).toBe(true);

        const browserMedia = await browserApi<PageResult<Media>>(
          session.page,
          session.token,
          '/subjects/media?current=1&size=100',
        );
        expect(browserMedia.status).toBe(200);
        expect(browserMedia.body.code).toBe(0);

        await session.page.goto('/#/delivery/channel-clients');
        if (role === 'analyst') {
          await expect(
            session.page.getByRole('button', { name: /新增渠道/ }),
          ).toHaveCount(0);
        }
        await saveEvidence(session.page, testInfo, role, 'channel-clients');
      }

      const noScope = sessionFor(sessions, 'noscope');
      for (const path of [
        '/boot/subjects/media?current=1&size=20',
        `/boot/subjects/media/${encodeURIComponent(readableMediaId)}`,
        `/boot/ad/channel-clients?current=1&size=20&mediaId=${encodeURIComponent(readableMediaId)}`,
      ]) {
        const response = await noScope.api.get(path);
        expect(response.status()).toBe(403);
      }
      await noScope.page.goto('/#/delivery/channel-clients');
      await saveEvidence(noScope.page, testInfo, 'noscope', 'direct-route');
    } finally {
      await Promise.all(
        Object.values(sessions).map((session) => closeSession(session)),
      );
    }
  });

  test('安全媒体上的创建、轮换、吊销、停用与拒绝矩阵', async ({
    browser,
  }, testInfo) => {
    test.skip(
      !readyForWrite,
      'Set L0_ALLOW_PRODUCTION_WRITE=true and make L0_SAFE_WRITE_MEDIA_ID equal the authorized sandbox media.',
    );

    const sessions: Partial<Record<RoleName, RoleSession>> = {};
    let created: ChannelClient | undefined;
    try {
      for (const role of Object.keys(roles) as RoleName[]) {
        sessions[role] = await login(browser, role);
      }

      const writableMediaId = required(
        safeWriteMediaId,
        'L0_SAFE_WRITE_MEDIA_ID',
      );
      const hiddenMediaId = required(
        unauthorizedMediaId,
        'L0_UNAUTHORIZED_MEDIA_ID',
      );
      const operator = sessionFor(sessions, 'operator');
      const body = {
        allowedScopes: ['CLICK', 'CONVERSION'],
        callbackAllowedHosts: [],
        dynamicCallbackEnabled: false,
        maxQps: 10,
        mediaId: writableMediaId,
        sourceCode: `l0-uat-${runId}`,
      };

      for (const role of ['analyst', 'noscope'] as RoleName[]) {
        const denied = await sessionFor(sessions, role).api.post(
          '/boot/ad/channel-clients',
          { data: body },
        );
        expect(denied.status()).toBe(403);
      }

      const crossMedia = await operator.api.post('/boot/ad/channel-clients', {
        data: { ...body, mediaId: hiddenMediaId },
      });
      expect(crossMedia.status()).toBe(200);
      const crossMediaResult = await json(crossMedia);
      expect(crossMediaResult.code).not.toBe(0);

      const issueResponse = await operator.api.post(
        '/boot/ad/channel-clients',
        { data: body },
      );
      expect(issueResponse.status()).toBe(200);
      const issue = await json<CredentialIssue>(issueResponse);
      expect(issue.code).toBe(0);
      expect(issue.data?.keyVersion).toBe('v1');
      expect(issue.data?.secret).toBeTruthy();

      const pageResponse = await operator.api.get(
        `/boot/ad/channel-clients?current=1&size=20&mediaId=${encodeURIComponent(writableMediaId)}&keyword=${encodeURIComponent(body.sourceCode)}`,
      );
      const channelPage = await json<PageResult<ChannelClient>>(pageResponse);
      created = channelPage.data?.records.find(
        (item) => item.sourceCode === body.sourceCode.toUpperCase(),
      );
      expect(created).toBeTruthy();
      if (!created) throw new Error('Created channel client was not found.');
      const createdClient = created;

      const rotate = await json<CredentialIssue>(
        await operator.api.post(
          `/boot/ad/channel-clients/${createdClient.id}/credentials/rotate?mediaId=${encodeURIComponent(writableMediaId)}`,
        ),
      );
      expect(rotate.code).toBe(0);
      expect(rotate.data?.keyVersion).toBe('v2');
      expect(rotate.data?.secret).toBeTruthy();

      const revoke = await json<boolean>(
        await operator.api.post(
          `/boot/ad/channel-clients/${createdClient.id}/credentials/v1/revoke?mediaId=${encodeURIComponent(writableMediaId)}`,
        ),
      );
      expect(revoke.code).toBe(0);
      expect(revoke.data).toBe(true);

      const disable = await json<boolean>(
        await operator.api.put(`/boot/ad/channel-clients/${createdClient.id}`, {
          data: {
            allowedScopes: ['CLICK', 'CONVERSION'],
            callbackAllowedHosts: [],
            dynamicCallbackEnabled: false,
            maxQps: 10,
            mediaId: writableMediaId,
            status: 'DISABLED',
          },
        }),
      );
      expect(disable.code).toBe(0);
      expect(disable.data).toBe(true);

      await operator.page.goto('/#/delivery/channel-clients');
      await saveEvidence(
        operator.page,
        testInfo,
        'operator',
        'disabled-test-client',
      );
    } finally {
      if (created && sessions.operator) {
        await sessions.operator.api.put(
          `/boot/ad/channel-clients/${created.id}`,
          {
            data: {
              allowedScopes: ['CLICK', 'CONVERSION'],
              callbackAllowedHosts: [],
              dynamicCallbackEnabled: false,
              maxQps: 10,
              mediaId: safeWriteMediaId,
              status: 'DISABLED',
            },
          },
        );
      }
      await Promise.all(
        Object.values(sessions).map((session) => closeSession(session)),
      );
    }
  });
});
