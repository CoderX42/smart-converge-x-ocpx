import { describe, expect, it } from 'vitest';

import { generateRandomPlacementCode } from './placement-code';

describe('placement code generator', () => {
  it('generates a backend-compatible placement code', () => {
    const code = generateRandomPlacementCode(
      new Uint8Array([0, 1, 25, 26, 35, 36, 37, 61, 62, 71, 72, 73]),
    );

    expect(code).toBe('placement-abz09abz09ab');
    expect(code).toMatch(/^[a-z0-9][a-z0-9-]{2,63}$/);
  });
});
