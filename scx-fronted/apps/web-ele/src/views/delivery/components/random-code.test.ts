import type {BusinessCodePrefix} from './random-code';

import { describe, expect, it } from 'vitest';

import {
  
  generateRandomBusinessCode
} from './random-code';

describe('business code generator', () => {
  const randomValues = new Uint8Array([
    0, 1, 25, 26, 35, 36, 37, 61, 62, 71, 72, 73,
  ]);

  it.each<BusinessCodePrefix>(['media', 'partner', 'customer', 'channel'])(
    'generates a backend-compatible %s code',
    (prefix) => {
      const code = generateRandomBusinessCode(prefix, randomValues);

      expect(code).toBe(`${prefix}-abz09abz09ab`);
      expect(code).toMatch(/^[a-z0-9][a-z0-9-]{2,63}$/);
    },
  );

  it('rejects an insufficient random byte source', () => {
    expect(() =>
      generateRandomBusinessCode('partner', new Uint8Array(11)),
    ).toThrow('Expected at least 12 random bytes');
  });
});
