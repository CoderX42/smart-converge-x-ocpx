import { describe, expect, it } from 'vitest';

import { generateRandomMediaCode } from './media-code';

describe('media code generator', () => {
  it('generates a stable backend-compatible media code from random bytes', () => {
    const code = generateRandomMediaCode(
      new Uint8Array([0, 1, 25, 26, 35, 36, 37, 61, 62, 71, 72, 73]),
    );

    expect(code).toBe('media-abz09abz09ab');
    expect(code).toMatch(/^[a-z0-9][a-z0-9-]{2,63}$/);
  });

  it('rejects an insufficient random byte source', () => {
    expect(() => generateRandomMediaCode(new Uint8Array(11))).toThrow(
      'Expected at least 12 random bytes',
    );
  });
});
