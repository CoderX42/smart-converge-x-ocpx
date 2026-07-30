import { describe, expect, it } from 'vitest';

import { parseCapability, serializeCapability } from './capability-editor';

describe('capability editor', () => {
  it('parses and serializes structured capability JSON', () => {
    const state = parseCapability({
      billingModes: '["CPC","CPM"]',
      creativeFormats: '["IMAGE"]',
      targetEvents: '["PURCHASE"]',
      targetingFields: '{"region":"省市"}',
    });
    expect(state.advanced).toBe(false);
    expect(state.targetingFields).toEqual([{ key: 'region', value: '省市' }]);
    expect(JSON.parse(serializeCapability(state).billingModes)).toEqual([
      'CPC',
      'CPM',
    ]);
  });

  it('enters validated advanced mode for historical invalid JSON', () => {
    const state = parseCapability({ billingModes: 'CPC,CPM' });
    expect(state.advanced).toBe(true);
    expect(() => serializeCapability(state)).toThrow();
    state.raw.billingModes = '["CPC","CPM"]';
    expect(() => serializeCapability(state)).not.toThrow();
  });
});
