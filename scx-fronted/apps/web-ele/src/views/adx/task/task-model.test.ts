import { describe, expect, it } from 'vitest';

import {
  buildTaskPayload,
  normalizeTaskRelations,
  resolveRoutePublicationState,
  resolveTaskEmptyState,
  taskEditorIssue,
} from './task-model';

const options = {
  advertisers: [{ id: 'a1', name: '广告主' }],
  appLinks: [
    {
      advertiserId: 'a1',
      id: 'l1',
      linkType: 'ANDROID',
      name: '点击',
      productId: 'p1',
    },
    {
      advertiserId: 'a1',
      id: 'l2',
      linkType: 'ANDROID',
      name: '曝光',
      productId: 'p1',
    },
    {
      advertiserId: 'a1',
      id: 'l3',
      linkType: 'IOS',
      name: '其他',
      productId: 'p2',
    },
  ],
  channels: [{ id: 'c1', name: '渠道' }],
  credentials: [
    {
      id: 'x1',
      label: '凭据',
      mediaAccountId: 'm1',
      secretRevision: 2,
      status: 'ACTIVE',
    },
  ],
  mediaAccounts: [
    { channelId: 'c1', defaultCredentialId: 'x1', id: 'm1', name: '账号' },
  ],
  operators: [{ displayName: '负责人', id: 'u1' }],
  products: [
    { advertiserId: 'a1', id: 'p1', name: '产品一' },
    { advertiserId: 'a1', id: 'p2', name: '产品二' },
  ],
};

function editor() {
  return {
    clickAppLinkId: 'l1',
    dedupWindowSeconds: 86_400,
    description: '',
    exposureAppLinkId: 'l2',
    exposureAsClick: true,
    exposureEnabled: true,
    eventBindings: [
      {
        callbackRatioBasisPoints: 10_000,
        channelEventMappingId: 'mapping-1',
        eventDefinitionId: 'definition-1',
        limitValue: 0,
        overLimitDelivery: false,
        rowKey: 'row-1',
      },
    ],
    mediaAccountId: 'm1',
    mediaCredentialId: 'x1',
    operatorUserId: 'u1',
    productId: 'p1',
    rtaMode: 'OFF' as const,
    taskName: ' 草稿 ',
  };
}

describe('task model', () => {
  it('distinguishes permission, first-empty, filtered-empty and error states', () => {
    expect(
      resolveTaskEmptyState({
        canView: false,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('NO_PERMISSION');
    expect(
      resolveTaskEmptyState({
        canView: true,
        error: false,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('INITIAL_EMPTY');
    expect(
      resolveTaskEmptyState({
        canView: true,
        error: false,
        filtersActive: true,
        total: 0,
      }),
    ).toBe('FILTER_EMPTY');
    expect(
      resolveTaskEmptyState({
        canView: true,
        error: true,
        filtersActive: false,
        total: 0,
      }),
    ).toBe('ERROR');
  });

  it('clears conditional exposure fields and incompatible children', () => {
    const value = editor();
    value.exposureEnabled = false;
    normalizeTaskRelations(value, options, 'exposure');
    expect(value.exposureAppLinkId).toBe('');
    expect(value.exposureAsClick).toBe(false);
    value.productId = 'p2';
    normalizeTaskRelations(value, options, 'product');
    expect(value.clickAppLinkId).toBe('');
    expect(value.eventBindings).toEqual([]);
  });

  it('locks RTA off and validates dedup, exposure, AppLink and credential relations', () => {
    const valid = editor();
    expect(taskEditorIssue(valid, options)).toBe('');
    valid.dedupWindowSeconds = 86_401;
    expect(taskEditorIssue(valid, options)).toContain('86400');
    valid.dedupWindowSeconds = 0;
    valid.exposureAppLinkId = '';
    expect(taskEditorIssue(valid, options)).toContain('曝光');
  });

  it('builds a draft-only payload and clears disabled exposure data', () => {
    const value = editor();
    value.exposureEnabled = false;
    const payload = buildTaskPayload(value);
    expect(payload).toMatchObject({
      exposureAsClick: false,
      exposureEnabled: false,
      rtaMode: 'OFF',
      taskName: '草稿',
    });
    expect(payload.exposureAppLinkId).toBeUndefined();
    expect(payload).not.toHaveProperty('status');
    expect(payload).not.toHaveProperty('publish');
    expect(payload.eventBindings).toEqual([
      {
        callbackRatioBasisPoints: 10_000,
        channelEventMappingId: 'mapping-1',
        eventDefinitionId: 'definition-1',
        limitValue: 0,
        overLimitDelivery: false,
      },
    ]);
  });

  it('rejects duplicate events and ratio or limit boundary violations', () => {
    const value = editor();
    const first = value.eventBindings.at(0);
    expect(first).toBeDefined();
    if (!first) throw new Error('event binding fixture is required');
    value.eventBindings.push({ ...first, rowKey: 'row-2' });
    expect(taskEditorIssue(value, options)).toContain('不能重复');
    value.eventBindings.pop();
    first.callbackRatioBasisPoints = 10_001;
    expect(taskEditorIssue(value, options)).toContain('10000');
    first.callbackRatioBasisPoints = 0;
    first.limitValue = -1;
    expect(taskEditorIssue(value, options)).toContain('非负');
  });

  it('clears event rows when MediaAccount changes', () => {
    const value = editor();
    value.mediaAccountId = 'missing';
    normalizeTaskRelations(value, options, 'mediaAccount');
    expect(value.eventBindings).toEqual([]);
  });

  it('models blocked, expired, conflict and failed publish states', () => {
    expect(
      resolveRoutePublicationState({
        checkStatus: 'FAIL',
        conflict: false,
        error: false,
        forbidden: false,
      }),
    ).toBe('BLOCKED');
    expect(
      resolveRoutePublicationState({
        checkStatus: 'PASS',
        conflict: false,
        error: false,
        expiresAt: '2026-01-01T00:00:00Z',
        forbidden: false,
        now: Date.parse('2026-01-02T00:00:00Z'),
      }),
    ).toBe('STALE');
    expect(
      resolveRoutePublicationState({
        checkStatus: 'PASS',
        conflict: true,
        error: false,
        forbidden: false,
      }),
    ).toBe('CONFLICT');
    expect(
      resolveRoutePublicationState({
        checkStatus: 'PASS',
        conflict: false,
        distributionStatus: 'FAILED',
        error: false,
        forbidden: false,
      }),
    ).toBe('FAILED');
  });
});
