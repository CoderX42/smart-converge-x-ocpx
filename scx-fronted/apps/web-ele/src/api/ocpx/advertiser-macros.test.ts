import { describe, expect, it } from 'vitest';

import { appendAdvertiserMacroParams } from './advertiser-macros';

describe('advertiser macro contract', () => {
  it('appends only missing query keys before a fragment', () => {
    expect(
      appendAdvertiserMacroParams(
        'custom://host/feedback?idfa=fixed#part',
        '?idfa=__IDFA__&ip=__IP__&ua=__UA__&',
      ),
    ).toBe('custom://host/feedback?idfa=fixed&ip=__IP__&ua=__UA__#part');
    expect(
      appendAdvertiserMacroParams('custom://host/feedback', '__IDFA__,__IP__'),
    ).toBe('custom://host/feedback');
  });
});
