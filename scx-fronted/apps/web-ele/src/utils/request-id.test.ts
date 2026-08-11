import { describe, expect, it } from 'vitest';

import { createClientRequestId } from './request-id';

describe('client request ID', () => {
  it('normalizes a native random UUID', () => {
    expect(
      createClientRequestId({
        randomUUID: () => '123e4567-e89b-12d3-a456-426614174000',
      }),
    ).toBe('123e4567e89b12d3a456426614174000');
  });

  it('uses random values when randomUUID is unavailable', () => {
    const generated = createClientRequestId({
      getRandomValues(array) {
        array.fill(171);
        return array;
      },
      randomUUID() {
        throw new Error('unavailable');
      },
    });
    expect(generated).toBe('ab'.repeat(16));
  });

  it('creates valid distinct fallback IDs without Web Crypto', () => {
    const first = createClientRequestId(null);
    const second = createClientRequestId(null);
    expect(first).toMatch(/^[\w-]{16,64}$/);
    expect(second).toMatch(/^[\w-]{16,64}$/);
    expect(second).not.toBe(first);
  });
});
