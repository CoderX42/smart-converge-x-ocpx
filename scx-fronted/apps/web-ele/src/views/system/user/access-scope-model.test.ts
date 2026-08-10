import { describe, expect, it } from 'vitest';

import { normalizedScopes, validateScopes } from './access-scope-model';

describe('aDX access scope model', () => {
  it('normalizes ALL without an object id', () => {
    expect(
      normalizedScopes([
        {
          permissionLevel: 'MANAGE',
          scopeId: 'ignored',
          scopeType: 'ALL',
        },
      ]),
    ).toEqual([
      {
        permissionLevel: 'MANAGE',
        scopeId: undefined,
        scopeType: 'ALL',
        validFrom: undefined,
        validTo: undefined,
      },
    ]);
  });

  it('rejects duplicate and inverted grants', () => {
    const duplicate = {
      permissionLevel: 'VIEW' as const,
      scopeId: 'channel-1',
      scopeType: 'CHANNEL' as const,
    };
    expect(validateScopes([duplicate, duplicate]).message).toContain('重复');
    expect(
      validateScopes([
        {
          ...duplicate,
          validFrom: '2026-08-11T00:00:00',
          validTo: '2026-08-10T00:00:00',
        },
      ]).message,
    ).toContain('早于');
  });

  it('keeps ALL exclusive and accepts a concrete media-account type', () => {
    expect(
      validateScopes([
        { permissionLevel: 'MANAGE', scopeType: 'ALL' },
        {
          permissionLevel: 'VIEW',
          scopeId: 'channel-1',
          scopeType: 'CHANNEL',
        },
      ]).valid,
    ).toBe(false);
    expect(
      validateScopes([
        {
          permissionLevel: 'VIEW',
          scopeId: 'account-1',
          scopeType: 'MEDIA_ACCOUNT',
        },
      ]).valid,
    ).toBe(true);
  });
});
