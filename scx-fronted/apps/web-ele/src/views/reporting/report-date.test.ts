import { afterEach, describe, expect, it, vi } from 'vitest';

import { shanghaiDate } from '#/utils/report-date';

describe('reporting date in Asia/Shanghai', () => {
  afterEach(() => vi.useRealTimers());

  it('uses the Shanghai calendar day instead of truncating UTC', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-29T16:30:00.000Z'));

    expect(shanghaiDate()).toBe('2026-07-30');
    expect(shanghaiDate(-1)).toBe('2026-07-29');
  });

  it('rolls over exactly at Shanghai midnight', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-30T15:59:00.000Z'));
    expect(shanghaiDate()).toBe('2026-07-30');

    vi.setSystemTime(new Date('2026-07-30T16:00:00.000Z'));
    expect(shanghaiDate()).toBe('2026-07-31');
  });
});
