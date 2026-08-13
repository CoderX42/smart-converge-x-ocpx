import { beforeEach, describe, expect, it, vi } from 'vitest';

import { allColumnKeys, copyLongText, PAGE_SIZES, resetPage } from './table';

const message = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: message }));

describe('ocpx table interaction helpers', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('selects all columns by default and uses the agreed page sizes', () => {
    expect(
      allColumnKeys([
        { key: 'id', label: '编号' },
        { key: 'name', label: '名称' },
      ]),
    ).toEqual(['id', 'name']);
    expect(PAGE_SIZES).toEqual([10, 30, 60]);
  });

  it('resets pagination when a query or parent filter changes', () => {
    const query = { current: 8, keyword: 'media' };
    resetPage(query);
    expect(query.current).toBe(1);
  });

  it('copies long text and reports success', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    await copyLongText('https://example.test/callback');
    expect(writeText).toHaveBeenCalledWith('https://example.test/callback');
    expect(message.success).toHaveBeenCalledWith('复制成功');
  });

  it('reports clipboard failures without leaking the copied value', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
    });
    await copyLongText('credential-reference-only');
    expect(message.error).toHaveBeenCalledWith('复制失败，请手动复制');
  });
});
