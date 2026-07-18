import type { APIRequestContext, Page } from '@playwright/test';

import { createHash, createHmac, randomUUID } from 'node:crypto';

import { expect, test } from '@playwright/test';

const eventApiBase = process.env.E2E_EVENT_API_BASE;
const callbackMockBase = process.env.E2E_CALLBACK_MOCK_BASE;
const signingKey = process.env.E2E_EVENT_SIGNING_KEY;
const allowRemote = process.env.E2E_ALLOW_REMOTE === 'true';
const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const outboxProbeAggregateId = process.env.E2E_OUTBOX_PROBE_AGGREGATE_ID;
const outboxCapacityAggregateId = process.env.E2E_OUTBOX_CAPACITY_AGGREGATE_ID;
const credentials = username && password ? { password, username } : undefined;

type EventRequest = {
  attributes: Record<string, string>;
  businessEventKey?: string;
  deviceKeyHash: string;
  eventId: string;
  eventType: 'ACTIVATE' | 'CLICK';
  occurredAt: string;
  schemaVersion: '1.0';
  source: string;
  taskCode: 'demo-task';
};

type EventResponse = {
  code: number;
  data?: {
    attributionStatus: null | string;
    duplicateDelivery: boolean;
    processingStatus: string;
    rawEventId: string;
    reasonCode: string;
  };
  msg?: string;
};

function assertIsolatedEventApi() {
  if (!eventApiBase) {
    return;
  }
  const { hostname } = new URL(eventApiBase);
  if (
    !allowRemote &&
    hostname !== '127.0.0.1' &&
    hostname !== 'localhost' &&
    hostname !== '::1'
  ) {
    throw new Error(
      'E2E_EVENT_API_BASE must target localhost unless E2E_ALLOW_REMOTE=true is explicitly set.',
    );
  }
}

assertIsolatedEventApi();

function assertIsolatedCallbackMock() {
  if (!callbackMockBase) {
    return;
  }
  const { hostname } = new URL(callbackMockBase);
  if (
    !allowRemote &&
    hostname !== '127.0.0.1' &&
    hostname !== 'localhost' &&
    hostname !== '::1'
  ) {
    throw new Error(
      'E2E_CALLBACK_MOCK_BASE must target localhost unless E2E_ALLOW_REMOTE=true is explicitly set.',
    );
  }
}

assertIsolatedCallbackMock();

async function login(page: Page) {
  if (!credentials) {
    throw new Error(
      'E2E credentials are required before starting the report-detail flow.',
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
  expect(response.status()).toBe(200);
  await expect(page).not.toHaveURL(/#\/auth\/login/);
}

function hash(value: string) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

function canonicalOccurredAt(value: string) {
  const [dateTime, fractionalSeconds] = value.split('.');
  if (fractionalSeconds === undefined) {
    return value;
  }
  const normalizedFractionalSeconds = fractionalSeconds.replace(/0+$/, '');
  return normalizedFractionalSeconds
    ? `${dateTime}.${normalizedFractionalSeconds}`
    : dateTime;
}

function canonicalPayload(request: EventRequest) {
  return JSON.stringify({
    schemaVersion: request.schemaVersion,
    eventId: request.eventId,
    source: request.source,
    taskCode: request.taskCode,
    eventType: request.eventType,
    occurredAt: canonicalOccurredAt(request.occurredAt),
    deviceKeyHash: request.deviceKeyHash,
    businessEventKeyHash: request.businessEventKey
      ? hash(request.businessEventKey)
      : null,
    attributes: Object.fromEntries(
      Object.entries(request.attributes).sort(([left], [right]) =>
        left.localeCompare(right),
      ),
    ),
  });
}

function signedHeaders(request: EventRequest) {
  if (!signingKey) {
    throw new Error('E2E_EVENT_SIGNING_KEY is required for signed event E2E.');
  }
  const timestamp = String(Date.now());
  const nonce = `e2e-sandbox-${randomUUID()}`;
  const sign = createHmac('sha256', signingKey)
    .update(`${timestamp}${nonce}${hash(canonicalPayload(request))}`, 'utf8')
    .digest('hex');
  return {
    'X-Nonce': nonce,
    'X-Sign': sign,
    'X-Timestamp': timestamp,
  };
}

async function ingest(
  requestContext: APIRequestContext,
  path: 'clicks' | 'conversions',
  body: EventRequest,
) {
  if (!eventApiBase) {
    throw new Error('E2E_EVENT_API_BASE is required for signed event E2E.');
  }
  const response = await requestContext.post(
    `${eventApiBase}/boot/open-api/events/v1/${path}`,
    {
      data: body,
      headers: signedHeaders(body),
    },
  );
  expect(response.status(), '公开事件接口必须返回 HTTP 200。').toBe(200);
  return (await response.json()) as EventResponse;
}

async function installTargetedCallbackFailure(
  requestContext: APIRequestContext,
  callbackJobId: string,
) {
  if (!callbackMockBase) {
    throw new Error(
      'E2E_CALLBACK_MOCK_BASE is required for callback fault injection.',
    );
  }
  const response = await requestContext.post(
    `${callbackMockBase}/__admin/mappings`,
    {
      data: {
        priority: 1,
        request: {
          headers: {
            'Idempotency-Key': { equalTo: callbackJobId },
          },
          method: 'POST',
          urlPath: '/callback',
        },
        response: { status: 503 },
      },
    },
  );
  expect(response.status()).toBe(201);
  const mapping = (await response.json()) as { id?: string };
  if (!mapping.id) {
    throw new Error('WireMock did not return the temporary fault mapping ID.');
  }
  return mapping.id;
}

async function removeCallbackFailure(
  requestContext: APIRequestContext,
  mappingId: string,
) {
  if (!callbackMockBase) {
    return;
  }
  const response = await requestContext.delete(
    `${callbackMockBase}/__admin/mappings/${mappingId}`,
  );
  expect(response.status()).toBe(200);
}

test.describe('广告签名事件归因 E2E', () => {
  test.skip(
    !eventApiBase || !signingKey,
    'Set E2E_EVENT_API_BASE and E2E_EVENT_SIGNING_KEY for an isolated local sandbox before running signed-event E2E.',
  );

  test('签名点击、激活、重复投递形成同一归因闭环', async ({ request }) => {
    const runId = `e2e-sandbox-${randomUUID()}`;
    const occurredAt = new Date().toISOString().replace('Z', '');
    const deviceKeyHash = hash(`${runId}:device`);
    const common = {
      attributes: { e2eRun: runId, scenario: 'signed-event-attribution' },
      deviceKeyHash,
      occurredAt,
      schemaVersion: '1.0' as const,
      source: 'sandbox-e2e',
      taskCode: 'demo-task' as const,
    };
    const click: EventRequest = {
      ...common,
      eventId: `${runId}:click`,
      eventType: 'CLICK',
    };

    const clickResult = await ingest(request, 'clicks', click);
    expect(clickResult.code).toBe(0);
    expect(clickResult.data).toMatchObject({
      attributionStatus: null,
      duplicateDelivery: false,
      processingStatus: 'ELIGIBLE',
      reasonCode: 'FIRST_IN_WINDOW',
    });

    const conversionResult = await ingest(request, 'conversions', {
      ...common,
      businessEventKey: `${runId}:activation`,
      eventId: `${runId}:activate`,
      eventType: 'ACTIVATE',
    });
    expect(conversionResult.code).toBe(0);
    expect(conversionResult.data).toMatchObject({
      attributionStatus: 'ATTRIBUTED',
      duplicateDelivery: false,
      processingStatus: 'CANONICAL',
      reasonCode: 'FIRST_BUSINESS_EVENT',
    });

    const duplicateResult = await ingest(request, 'clicks', click);
    expect(duplicateResult.code).toBe(0);
    expect(duplicateResult.data).toMatchObject({
      duplicateDelivery: true,
      processingStatus: 'ACCEPTED',
      rawEventId: clickResult.data?.rawEventId,
      reasonCode: 'DUPLICATE_DELIVERY',
    });
  });

  test('签名点击生成的领域事件可按聚合 ID 单条发布到 RocketMQ', async ({
    page,
    request,
  }) => {
    test.skip(
      !credentials,
      'Set E2E_USERNAME and E2E_PASSWORD before validating the protected Outbox page.',
    );
    const runId = `e2e-sandbox-${randomUUID()}`;
    const result = await ingest(request, 'clicks', {
      attributes: { e2eRun: runId, scenario: 'targeted-outbox-publish' },
      deviceKeyHash: hash(`${runId}:device`),
      eventId: `${runId}:click`,
      eventType: 'CLICK',
      occurredAt: new Date().toISOString().replace('Z', ''),
      schemaVersion: '1.0',
      source: 'sandbox-e2e',
      taskCode: 'demo-task',
    });
    expect(result.data).toMatchObject({
      processingStatus: 'ELIGIBLE',
      reasonCode: 'FIRST_IN_WINDOW',
    });
    const rawEventId = result.data?.rawEventId;
    if (!rawEventId) {
      throw new Error('The accepted click must return its raw event ID.');
    }

    await login(page);
    await page.goto('/#/operations/jobs');
    const outboxCard = page.locator('.el-card').filter({
      hasText: '领域事件发件箱',
    });
    await expect(outboxCard).toBeVisible();
    await outboxCard.getByPlaceholder('事件聚合 ID').fill(rawEventId);
    await outboxCard.getByRole('button', { name: '查询', exact: true }).click();
    const outboxRows = outboxCard.locator('.el-table__body-wrapper tbody tr');
    await expect(outboxRows).toHaveCount(2);

    for (const eventType of ['RAW_EVENT_INGESTED', 'CLICK_CLASSIFIED']) {
      const outboxRow = outboxRows.filter({ hasText: eventType });
      await expect(outboxRow).toHaveCount(1);
      if ((await outboxRow.getByText('SENT', { exact: true }).count()) === 0) {
        const dispatchResponse = page.waitForResponse(
          (response) =>
            response.request().method() === 'POST' &&
            /\/ad\/callback-jobs\/outbox\/[^/]+\/dispatch\?/.test(
              response.url(),
            ),
        );
        await outboxRow
          .getByRole('button', { name: '立即发布', exact: true })
          .click();
        await page.getByRole('button', { name: '确定', exact: true }).click();
        const response = await dispatchResponse;
        expect(response.status()).toBe(200);
        const dispatchResult = (await response.json()) as {
          code: number;
          data?: boolean;
          msg?: string;
        };
        expect(
          dispatchResult,
          `单条 Outbox 发布失败：${dispatchResult.msg ?? '未返回原因'}`,
        ).toMatchObject({
          code: 0,
          data: true,
        });
      }
      await expect(outboxRow.getByText('SENT', { exact: true })).toBeVisible();
      await expect(outboxRow.getByText('1', { exact: true })).toBeVisible();
    }
  });

  test('受控 Outbox 积压批次保留可追溯的 sandbox 事件', async ({ request }) => {
    const runId = `e2e-sandbox-${randomUUID()}`;
    const rawEventIds: string[] = [];
    for (let index = 0; index < 20; index += 1) {
      const result = await ingest(request, 'clicks', {
        attributes: {
          batchIndex: String(index),
          e2eRun: runId,
          scenario: 'outbox-capacity-backlog',
        },
        deviceKeyHash: hash(`${runId}:device:${index}`),
        eventId: `${runId}:click:${index}`,
        eventType: 'CLICK',
        occurredAt: new Date().toISOString().replace('Z', ''),
        schemaVersion: '1.0',
        source: 'sandbox-e2e',
        taskCode: 'demo-task',
      });
      expect(result.data).toMatchObject({
        processingStatus: 'ELIGIBLE',
        reasonCode: 'FIRST_IN_WINDOW',
      });
      if (!result.data?.rawEventId) {
        throw new Error(
          'Each accepted capacity probe event must return a raw event ID.',
        );
      }
      rawEventIds.push(result.data.rawEventId);
    }
    expect(new Set(rawEventIds).size).toBe(20);
    console.log(`OUTBOX_CAPACITY_RUN_ID=${runId}`);
    console.log(`OUTBOX_CAPACITY_AGGREGATE_ID=${rawEventIds[0]}`);
  });

  test('Outbox 发布器不可用时保留事件并进入重试队列', async ({
    page,
    request,
  }) => {
    test.skip(
      !credentials,
      'Set E2E_USERNAME and E2E_PASSWORD before validating the protected Outbox page.',
    );
    const runId = `e2e-sandbox-${randomUUID()}`;
    const result = await ingest(request, 'clicks', {
      attributes: { e2eRun: runId, scenario: 'outbox-publisher-unavailable' },
      deviceKeyHash: hash(`${runId}:device`),
      eventId: `${runId}:click`,
      eventType: 'CLICK',
      occurredAt: new Date().toISOString().replace('Z', ''),
      schemaVersion: '1.0',
      source: 'sandbox-e2e',
      taskCode: 'demo-task',
    });
    const rawEventId = result.data?.rawEventId;
    if (!rawEventId) {
      throw new Error('The accepted click must return its raw event ID.');
    }

    await login(page);
    await page.goto('/#/operations/jobs');
    const outboxCard = page.locator('.el-card').filter({
      hasText: '领域事件发件箱',
    });
    await outboxCard.getByPlaceholder('事件聚合 ID').fill(rawEventId);
    await outboxCard.getByRole('button', { name: '查询', exact: true }).click();
    const outboxRow = outboxCard
      .locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: 'RAW_EVENT_INGESTED' });
    await expect(outboxRow).toHaveCount(1);
    await expect(outboxRow.getByText('PENDING', { exact: true })).toBeVisible();

    const dispatchResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        /\/ad\/callback-jobs\/outbox\/[^/]+\/dispatch\?/.test(response.url()),
    );
    await outboxRow
      .getByRole('button', { name: '立即发布', exact: true })
      .click();
    await page.getByRole('button', { name: '确定', exact: true }).click();
    const response = await dispatchResponse;
    await expect(response.json()).resolves.toMatchObject({
      code: 0,
      data: true,
    });
    await expect(
      outboxRow.getByText('RETRYING', { exact: true }),
    ).toBeVisible();
    await expect(outboxRow.getByText('1', { exact: true })).toBeVisible();
    console.log(`OUTBOX_PROBE_AGGREGATE_ID=${rawEventId}`);
  });

  test('Outbox 发布器恢复后可授权单条投递重试事件', async ({ page }) => {
    test.skip(
      !credentials || !outboxProbeAggregateId,
      'Set E2E credentials and E2E_OUTBOX_PROBE_AGGREGATE_ID after the isolated failure probe.',
    );
    await login(page);
    await page.goto('/#/operations/jobs');
    const outboxCard = page.locator('.el-card').filter({
      hasText: '领域事件发件箱',
    });
    await outboxCard
      .getByPlaceholder('事件聚合 ID')
      .fill(outboxProbeAggregateId);
    await outboxCard.getByRole('button', { name: '查询', exact: true }).click();
    const outboxRow = outboxCard
      .locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: 'RAW_EVENT_INGESTED' });
    await expect(outboxRow).toHaveCount(1);
    await expect(
      outboxRow.getByText('RETRYING', { exact: true }),
    ).toBeVisible();
    await expect(outboxRow.getByText('1', { exact: true })).toBeVisible();

    const dispatchResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        /\/ad\/callback-jobs\/outbox\/[^/]+\/dispatch\?/.test(response.url()),
    );
    await outboxRow
      .getByRole('button', { name: '立即发布', exact: true })
      .click();
    await page.getByRole('button', { name: '确定', exact: true }).click();
    const response = await dispatchResponse;
    await expect(response.json()).resolves.toMatchObject({
      code: 0,
      data: true,
    });
    await expect(outboxRow.getByText('SENT', { exact: true })).toBeVisible();
    await expect(outboxRow.getByText('2', { exact: true })).toBeVisible();
  });

  test('Outbox 故障恢复结果可在运营页面只读复核', async ({ page }) => {
    test.skip(
      !credentials || !outboxProbeAggregateId,
      'Set E2E credentials and E2E_OUTBOX_PROBE_AGGREGATE_ID after the isolated recovery probe.',
    );
    await login(page);
    await page.goto('/#/operations/jobs');
    const outboxCard = page.locator('.el-card').filter({
      hasText: '领域事件发件箱',
    });
    await outboxCard
      .getByPlaceholder('事件聚合 ID')
      .fill(outboxProbeAggregateId);
    await outboxCard.getByRole('button', { name: '查询', exact: true }).click();
    const outboxRow = outboxCard
      .locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: 'RAW_EVENT_INGESTED' });
    await expect(outboxRow).toHaveCount(1);
    await expect(outboxRow.getByText('SENT', { exact: true })).toBeVisible();
    await expect(outboxRow.getByText('2', { exact: true })).toBeVisible();
  });

  test('Outbox 积压批次恢复结果可在运营页面只读复核', async ({ page }) => {
    test.skip(
      !credentials || !outboxCapacityAggregateId,
      'Set E2E credentials and E2E_OUTBOX_CAPACITY_AGGREGATE_ID after the capacity probe.',
    );
    await login(page);
    await page.goto('/#/operations/jobs');
    const outboxCard = page.locator('.el-card').filter({
      hasText: '领域事件发件箱',
    });
    await outboxCard
      .getByPlaceholder('事件聚合 ID')
      .fill(outboxCapacityAggregateId);
    await outboxCard.getByRole('button', { name: '查询', exact: true }).click();
    const outboxRows = outboxCard.locator('.el-table__body-wrapper tbody tr');
    await expect(outboxRows).toHaveCount(2);
    await expect(outboxRows.getByText('SENT', { exact: true })).toHaveCount(2);
    await expect(outboxRows.getByText('1', { exact: true })).toHaveCount(2);
  });

  test('已归因激活可按事件 ID 在报表明细页追溯', async ({ page, request }) => {
    test.skip(
      !credentials,
      'Set E2E_USERNAME and E2E_PASSWORD before validating the protected report page.',
    );
    const runId = `e2e-sandbox-${randomUUID()}`;
    const occurredAt = new Date().toISOString().replace('Z', '');
    const deviceKeyHash = hash(`${runId}:device`);
    const common = {
      attributes: { e2eRun: runId, scenario: 'event-report-trace' },
      deviceKeyHash,
      occurredAt,
      schemaVersion: '1.0' as const,
      source: 'sandbox-e2e',
      taskCode: 'demo-task' as const,
    };
    const click: EventRequest = {
      ...common,
      eventId: `${runId}:click`,
      eventType: 'CLICK',
    };
    const clickResult = await ingest(request, 'clicks', click);
    expect(clickResult.code).toBe(0);

    const conversion: EventRequest = {
      ...common,
      businessEventKey: `${runId}:activation`,
      eventId: `${runId}:activate`,
      eventType: 'ACTIVATE',
    };
    const conversionResult = await ingest(request, 'conversions', conversion);
    expect(conversionResult.data).toMatchObject({
      attributionStatus: 'ATTRIBUTED',
      processingStatus: 'CANONICAL',
    });

    await login(page);
    await page.goto('/#/reporting/event-detail');
    await expect(page.locator('.mvp-page')).toBeVisible();
    await page.getByPlaceholder('事件 ID 或 click_id').fill(conversion.eventId);
    await page.getByRole('button', { name: '查询', exact: true }).click();
    const eventRow = page
      .locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: conversion.eventId });
    await expect(eventRow).toHaveCount(1);
    await expect(
      eventRow.getByText(conversion.eventId, { exact: true }),
    ).toBeVisible();
    await expect(
      eventRow.getByText('ATTRIBUTED', { exact: true }),
    ).toBeVisible();
  });

  test('已归因激活可精确定位并单条派发 sandbox 回传', async ({
    page,
    request,
  }) => {
    test.skip(
      !credentials,
      'Set E2E_USERNAME and E2E_PASSWORD before validating the protected callback page.',
    );
    const runId = `e2e-sandbox-${randomUUID()}`;
    const occurredAt = new Date().toISOString().replace('Z', '');
    const deviceKeyHash = hash(`${runId}:device`);
    const common = {
      attributes: { e2eRun: runId, scenario: 'targeted-callback-dispatch' },
      deviceKeyHash,
      occurredAt,
      schemaVersion: '1.0' as const,
      source: 'sandbox-e2e',
      taskCode: 'demo-task' as const,
    };
    const click: EventRequest = {
      ...common,
      eventId: `${runId}:click`,
      eventType: 'CLICK',
    };
    const clickResult = await ingest(request, 'clicks', click);
    expect(clickResult.code).toBe(0);

    const conversionResult = await ingest(request, 'conversions', {
      ...common,
      businessEventKey: `${runId}:activation`,
      eventId: `${runId}:activate`,
      eventType: 'ACTIVATE',
    });
    expect(conversionResult.data).toMatchObject({
      attributionStatus: 'ATTRIBUTED',
      processingStatus: 'CANONICAL',
    });
    const rawEventId = conversionResult.data?.rawEventId;
    if (!rawEventId) {
      throw new Error('The accepted conversion must return its raw event ID.');
    }

    await login(page);
    await page.goto('/#/operations/jobs');
    await expect(page.locator('.mvp-page')).toBeVisible();
    await page.getByPlaceholder('归因原始事件 ID').fill(rawEventId);
    await page
      .locator('.mvp-filters')
      .filter({ has: page.getByPlaceholder('归因原始事件 ID') })
      .getByRole('button', { name: '查询', exact: true })
      .click();
    const callbackRows = page
      .locator('.el-card')
      .filter({
        has: page.getByRole('button', {
          name: '执行沙箱派发',
          exact: true,
        }),
      })
      .locator('.el-table__body-wrapper tbody tr');
    await expect(callbackRows).toHaveCount(1);
    const callbackRow = callbackRows.first();
    await expect(
      callbackRow.getByText('PENDING', { exact: true }),
    ).toBeVisible();

    const dispatchResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        /\/ad\/callback-jobs\/[^/]+\/dispatch\?/.test(response.url()),
    );
    await callbackRow
      .getByRole('button', { name: '立即派发', exact: true })
      .click();
    await page.getByRole('button', { name: '确定', exact: true }).click();
    const response = await dispatchResponse;
    expect(response.status()).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      code: 0,
      data: true,
    });
    await expect(
      callbackRow.getByText('SUCCEEDED', { exact: true }),
    ).toBeVisible();
  });

  test('回传目标短暂故障后可授权重放并恢复', async ({ page, request }) => {
    test.skip(
      !credentials || !callbackMockBase,
      'Set E2E credentials and E2E_CALLBACK_MOCK_BASE before injecting a callback fault.',
    );
    const runId = `e2e-sandbox-${randomUUID()}`;
    const occurredAt = new Date().toISOString().replace('Z', '');
    const common = {
      attributes: { e2eRun: runId, scenario: 'callback-failure-recovery' },
      deviceKeyHash: hash(`${runId}:device`),
      occurredAt,
      schemaVersion: '1.0' as const,
      source: 'sandbox-e2e',
      taskCode: 'demo-task' as const,
    };
    const clickResult = await ingest(request, 'clicks', {
      ...common,
      eventId: `${runId}:click`,
      eventType: 'CLICK',
    });
    expect(clickResult.code).toBe(0);
    const conversionResult = await ingest(request, 'conversions', {
      ...common,
      businessEventKey: `${runId}:activation`,
      eventId: `${runId}:activate`,
      eventType: 'ACTIVATE',
    });
    expect(conversionResult.data).toMatchObject({
      attributionStatus: 'ATTRIBUTED',
      processingStatus: 'CANONICAL',
    });
    const rawEventId = conversionResult.data?.rawEventId;
    if (!rawEventId) {
      throw new Error('The accepted conversion must return its raw event ID.');
    }

    await login(page);
    await page.goto('/#/operations/jobs');
    await page.getByPlaceholder('归因原始事件 ID').fill(rawEventId);
    await page
      .locator('.mvp-filters')
      .filter({ has: page.getByPlaceholder('归因原始事件 ID') })
      .getByRole('button', { name: '查询', exact: true })
      .click();
    const callbackRow = page
      .locator('.el-card')
      .filter({
        has: page.getByRole('button', {
          name: '执行沙箱派发',
          exact: true,
        }),
      })
      .locator('.el-table__body-wrapper tbody tr');
    await expect(callbackRow).toHaveCount(1);
    await expect(
      callbackRow.getByText('PENDING', { exact: true }),
    ).toBeVisible();
    const callbackJobIdElement = callbackRow.locator('.mvp-code').first();
    const callbackJobId = await callbackJobIdElement.textContent();
    if (!callbackJobId) {
      throw new Error('The targeted callback row must expose its task ID.');
    }

    const faultMappingId = await installTargetedCallbackFailure(
      request,
      callbackJobId,
    );
    try {
      const failedDispatch = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST' &&
          /\/ad\/callback-jobs\/[^/]+\/dispatch\?/.test(response.url()),
      );
      await callbackRow
        .getByRole('button', { name: '立即派发', exact: true })
        .click();
      await page.getByRole('button', { name: '确定', exact: true }).click();
      const failedDispatchResponse = await failedDispatch;
      expect(failedDispatchResponse.status()).toBe(200);
      await expect(
        callbackRow.getByText('RETRYING', { exact: true }),
      ).toBeVisible();
    } finally {
      await removeCallbackFailure(request, faultMappingId);
    }

    await callbackRow
      .getByRole('button', { name: '重放', exact: true })
      .click();
    await page.getByRole('button', { name: '确定', exact: true }).click();
    await expect(
      callbackRow.getByText('PENDING', { exact: true }),
    ).toBeVisible();

    const recoveredDispatch = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        /\/ad\/callback-jobs\/[^/]+\/dispatch\?/.test(response.url()),
    );
    await callbackRow
      .getByRole('button', { name: '立即派发', exact: true })
      .click();
    await page.getByRole('button', { name: '确定', exact: true }).click();
    const response = await recoveredDispatch;
    expect(response.status()).toBe(200);
    await expect(response.json()).resolves.toMatchObject({
      code: 0,
      data: true,
    });
    await expect(
      callbackRow.getByText('SUCCEEDED', { exact: true }),
    ).toBeVisible();
  });
});
