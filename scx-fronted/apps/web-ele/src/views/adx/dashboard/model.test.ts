import { describe, expect, it } from 'vitest';

import {
  buildRetentionHeatmap,
  buildTrendSeries,
  cascadeOptions,
  formatComparison,
  formatRate,
  shouldPauseAutoRefresh,
} from './model';

describe('aDX effect dashboard model', () => {
  it('distinguishes unavailable rates, zero and newly added comparisons', () => {
    expect(formatRate(undefined)).toBe('—');
    expect(formatRate(0)).toBe('0.00%');
    expect(
      formatComparison({
        comparisonLabel: '较昨日',
        comparisonValue: 0,
        value: 12,
      }),
    ).toBe('新增');
    expect(
      formatComparison({
        changeRate: -25,
        comparisonLabel: '较昨日',
        comparisonValue: 8,
        value: 6,
      }),
    ).toBe('-25.00%');
  });

  it('keeps null rate points and exact retention counts in chart models', () => {
    const summary = {
      retention: [{ day: 'D3', reportDate: '2026-08-08', value: 7 }],
      trend: [
        {
          metrics: { activationRate: undefined, clicks: 12 },
          reportDate: '2026-08-08',
        },
      ],
    } as never;
    expect(buildTrendSeries(summary, ['activationRate'])[0]?.data).toEqual([
      null,
    ]);
    expect(buildRetentionHeatmap(summary).data).toEqual([[0, 1, 7]]);
  });

  it('cascades monitor options and pauses refresh only while hidden', () => {
    const options = {
      advertisers: [{ id: 'a1' }, { id: 'a2' }],
      appLinks: [
        { advertiserId: 'a1', id: 'l1' },
        { advertiserId: 'a2', id: 'l2' },
      ],
      channels: [],
      monitors: [
        { advertiserId: 'a1', appLinkId: 'l1', channelId: 'c1', id: 'm1' },
        { advertiserId: 'a2', appLinkId: 'l2', channelId: 'c2', id: 'm2' },
      ],
    } as never;
    expect(cascadeOptions(options, { channelId: 'c1' }).monitors).toHaveLength(
      1,
    );
    expect(shouldPauseAutoRefresh(true)).toBe(true);
    expect(shouldPauseAutoRefresh(false)).toBe(false);
  });
});
