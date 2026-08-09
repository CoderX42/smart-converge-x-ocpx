import type { Page } from '@playwright/test';

import { randomUUID } from 'node:crypto';

import { expect, test } from '@playwright/test';

const adminUsername = process.env.E2E_USERNAME;
const adminPassword = process.env.E2E_PASSWORD;
const operatorUsername = process.env.E2E_OPERATOR_USERNAME;
const operatorPassword = process.env.E2E_OPERATOR_PASSWORD;

type ApiResult<T> = { code: number; data?: T; msg?: string };
type PageResult<T> = { records: T[]; total: number };
type IdRecord = { id: string };
type Channel = IdRecord & { channelName: string };
type EventDefinition = IdRecord & {
  category: 'CONVERSION' | 'MEDIA' | 'UPSTREAM';
  channelId?: string;
  eventCode: string;
  eventName: string;
  remark?: string;
  sortOrder: number;
  status: 'ACTIVE' | 'DISABLED';
};
type EventMapping = IdRecord & {
  channelId: string;
  conversionEventId: string;
  mediaEventId: string;
  status: 'ACTIVE' | 'DISABLED';
  upstreamEventId: string;
};
type EventOption = {
  eventMappingId: string;
  upstreamEventCode: string;
};
type Monitor = IdRecord & {
  advertiserId: string;
  appLinkId: string;
  billingMode: string;
  channelId: string;
  clickDedupWindowSeconds: number;
  downstreamCallbackUrl?: string;
  eventBindings: Array<{
    assessmentUnitPrice: number;
    dailyLimit: number;
    eventMappingId: string;
  }>;
  monitorName: string;
  remark?: string;
  routeStatus: string;
  rtaMode: string;
  unitPrice: number;
  upstreamFeedbackUrl?: string;
};

async function login(page: Page, username: string, password: string) {
  await page.goto('/#/auth/login');
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByRole('textbox', { name: '密码', exact: true }).fill(password);
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes('/api/boot/auth/token/login'),
  );
  await page.locator('button').filter({ hasText: '登录' }).click();
  const response = await responsePromise;
  const result = (await response.json()) as ApiResult<{ tokenValue: string }>;
  expect(result.code).toBe(0);
  expect(result.data?.tokenValue).toBeTruthy();
  if (!result.data?.tokenValue) {
    throw new Error('登录成功响应缺少 tokenValue');
  }
  return result.data.tokenValue;
}

async function api<T>(
  page: Page,
  token: string,
  path: string,
  options: {
    body?: Record<string, unknown>;
    method?: 'DELETE' | 'GET' | 'POST' | 'PUT';
  } = {},
) {
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

function success<T>(response: { body: ApiResult<T>; status: number }) {
  expect(response.status).toBe(200);
  expect(response.body.code).toBe(0);
  if (response.body.data === undefined) {
    throw new Error('成功响应缺少 data');
  }
  return response.body.data;
}

test.describe('ADX 事件管理与监测链接绑定 E2E', () => {
  test.skip(
    !adminUsername || !adminPassword,
    'Set E2E_USERNAME and E2E_PASSWORD for an isolated administrator sandbox.',
  );

  test('管理员维护三段映射、绑定监测链接并发布不可变版本', async ({ page }) => {
    if (!adminUsername || !adminPassword) {
      return;
    }
    const token = await login(page, adminUsername, adminPassword);
    const channels = success(
      await api<PageResult<Channel>>(
        page,
        token,
        '/adx/channels?current=1&size=20',
      ),
    );
    const channel = channels.records[0];
    expect(channel, '隔离环境至少需要一个渠道').toBeTruthy();
    if (!channel) {
      throw new Error('隔离环境至少需要一个渠道');
    }

    const defaultOptions = success(
      await api<EventOption[]>(
        page,
        token,
        `/adx/channels/${channel.id}/event-options`,
      ),
    );
    expect(defaultOptions.map((item) => item.upstreamEventCode)).toEqual(
      expect.arrayContaining([
        'ACTIVATE',
        'NEXT_DAY_RETENTION',
        'PAYMENT',
        'REGISTER',
      ]),
    );

    const sourceMonitors = success(
      await api<PageResult<Monitor>>(
        page,
        token,
        `/adx/monitors?current=1&size=20&channelId=${channel.id}`,
      ),
    );
    const publishedSource = sourceMonitors.records.find(
      (item) => item.routeStatus === 'PUBLISHED',
    );
    if (!publishedSource) {
      test.skip(
        true,
        'The isolated sandbox needs one published monitor whose validated endpoints can be cloned.',
      );
      return;
    }
    const sourceMonitor = success(
      await api<Monitor>(page, token, `/adx/monitors/${publishedSource.id}`),
    );

    const conversions = success(
      await api<PageResult<EventDefinition>>(
        page,
        token,
        '/adx/events?current=1&size=200&category=CONVERSION&status=ACTIVE',
      ),
    );
    const conversion = conversions.records.find(
      (item) => item.eventCode === 'KEY_ACTION',
    );
    expect(conversion, '系统默认 KEY_ACTION 事件必须存在').toBeTruthy();
    if (!conversion) {
      throw new Error('系统默认 KEY_ACTION 事件必须存在');
    }

    const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
    const upstreamCode = `E2E_UP_${suffix.toUpperCase()}`;
    const mediaCode = `e2e.media-${suffix}`;
    let upstream: EventDefinition | undefined;
    let media: EventDefinition | undefined;
    let mapping: EventMapping | undefined;
    let monitor: Monitor | undefined;

    try {
      upstream = success(
        await api<EventDefinition>(page, token, '/adx/events', {
          body: {
            category: 'UPSTREAM',
            channelId: channel.id,
            eventCode: upstreamCode,
            eventName: 'E2E 上游关键行为',
            remark: 'isolated playwright sandbox',
            sortOrder: 9000,
            status: 'ACTIVE',
          },
          method: 'POST',
        }),
      );
      media = success(
        await api<EventDefinition>(page, token, '/adx/events', {
          body: {
            category: 'MEDIA',
            channelId: channel.id,
            eventCode: mediaCode,
            eventName: 'E2E 媒体关键行为',
            remark: 'isolated playwright sandbox',
            sortOrder: 9000,
            status: 'ACTIVE',
          },
          method: 'POST',
        }),
      );
      upstream = success(
        await api<EventDefinition>(page, token, `/adx/events/${upstream.id}`, {
          body: {
            category: upstream.category,
            channelId: upstream.channelId,
            eventCode: upstream.eventCode,
            eventName: 'E2E 上游关键行为（已编辑）',
            remark: upstream.remark,
            sortOrder: upstream.sortOrder,
            status: upstream.status,
          },
          method: 'PUT',
        }),
      );
      mapping = success(
        await api<EventMapping>(page, token, '/adx/event-mappings', {
          body: {
            channelId: channel.id,
            conversionEventId: conversion.id,
            mediaEventId: media.id,
            remark: 'isolated playwright sandbox',
            status: 'ACTIVE',
            upstreamEventId: upstream.id,
          },
          method: 'POST',
        }),
      );

      const options = success(
        await api<EventOption[]>(
          page,
          token,
          `/adx/channels/${channel.id}/event-options`,
        ),
      );
      expect(options).toContainEqual(
        expect.objectContaining({
          eventMappingId: mapping.id,
          upstreamEventCode: upstreamCode,
        }),
      );

      monitor = success(
        await api<Monitor>(page, token, '/adx/monitors', {
          body: {
            advertiserId: sourceMonitor.advertiserId,
            appLinkId: sourceMonitor.appLinkId,
            billingMode: 'OFF',
            channelId: channel.id,
            clickDedupWindowSeconds: 3600,
            downstreamCallbackUrl: sourceMonitor.downstreamCallbackUrl,
            eventBindings: [
              {
                assessmentUnitPrice: 0.125,
                dailyLimit: 10,
                eventMappingId: mapping.id,
              },
            ],
            monitorName: `E2E 事件绑定 ${suffix}`,
            remark: 'isolated playwright sandbox',
            rtaMode: 'OFF',
            unitPrice: 0,
            upstreamFeedbackUrl: sourceMonitor.upstreamFeedbackUrl,
          },
          method: 'POST',
        }),
      );
      const detail = success(
        await api<Monitor>(page, token, `/adx/monitors/${monitor.id}`),
      );
      expect(detail.eventBindings).toContainEqual(
        expect.objectContaining({
          assessmentUnitPrice: 0.125,
          dailyLimit: 10,
          eventMappingId: mapping.id,
        }),
      );
      monitor = success(
        await api<Monitor>(page, token, `/adx/monitors/${monitor.id}`, {
          body: {
            advertiserId: detail.advertiserId,
            appLinkId: detail.appLinkId,
            billingMode: detail.billingMode,
            channelId: detail.channelId,
            clickDedupWindowSeconds: detail.clickDedupWindowSeconds,
            downstreamCallbackUrl: detail.downstreamCallbackUrl,
            eventBindings: [
              {
                assessmentUnitPrice: 0.25,
                dailyLimit: 20,
                eventMappingId: mapping.id,
              },
            ],
            monitorName: `${detail.monitorName}（已编辑）`,
            remark: detail.remark,
            rtaMode: detail.rtaMode,
            unitPrice: detail.unitPrice,
            upstreamFeedbackUrl: detail.upstreamFeedbackUrl,
          },
          method: 'PUT',
        }),
      );
      expect(monitor.eventBindings).toContainEqual(
        expect.objectContaining({
          assessmentUnitPrice: 0.25,
          dailyLimit: 20,
          eventMappingId: mapping.id,
        }),
      );

      success(
        await api<Record<string, unknown>>(
          page,
          token,
          `/adx/monitors/${monitor.id}/publish`,
          { method: 'POST' },
        ),
      );
      await page.goto('/#/system/event');
      await expect(page.getByText('事件管理', { exact: true })).toBeVisible();
      await expect(page.getByText('事件字典', { exact: true })).toBeVisible();
      await expect(page.getByText('映射规则', { exact: true })).toBeVisible();
    } finally {
      if (monitor) {
        await api<boolean>(page, token, `/adx/monitors/${monitor.id}`, {
          method: 'DELETE',
        });
      }
      if (mapping) {
        await api<boolean>(page, token, `/adx/event-mappings/${mapping.id}`, {
          method: 'DELETE',
        });
      }
      if (upstream) {
        await api<boolean>(page, token, `/adx/events/${upstream.id}`, {
          method: 'DELETE',
        });
      }
      if (media) {
        await api<boolean>(page, token, `/adx/events/${media.id}`, {
          method: 'DELETE',
        });
      }
    }
  });

  test('运营角色无法直接读取事件管理 API 或页面', async ({ page }) => {
    test.skip(
      !operatorUsername || !operatorPassword,
      'Set E2E_OPERATOR_USERNAME and E2E_OPERATOR_PASSWORD to validate direct access denial.',
    );
    if (!operatorUsername || !operatorPassword) {
      return;
    }
    const token = await login(page, operatorUsername, operatorPassword);
    const response = await api<PageResult<EventDefinition>>(
      page,
      token,
      '/adx/events?current=1&size=20',
    );
    expect(response.body.code).not.toBe(0);

    await page.goto('/#/system/event');
    await expect(page.getByText('事件管理', { exact: true })).toHaveCount(0);
  });
});
