import { describe, expect, it } from 'vitest';

import {
  buildDashboardTrendSeries,
  buildRetentionHeatmap,
  buildTrendSeries,
  cascadeOptions,
  comparisonTone,
  DASHBOARD_TREND_DEFINITIONS,
  formatComparison,
  formatDashboardTrendValue,
  formatRate,
  isDashboardDateRangeValid,
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

  it('builds the 15 reference trend series in a stable order and axis', () => {
    const metrics: Record<string, number | undefined> = Object.fromEntries(
      DASHBOARD_TREND_DEFINITIONS.map(({ key }, index) => [key, index]),
    );
    metrics.activationRate = undefined;
    const summary = {
      trend: [{ metrics, reportDate: '2026-08-08' }],
    } as never;
    const series = buildDashboardTrendSeries(summary);

    expect(series.map(({ label }) => label)).toEqual([
      '点击',
      '激活',
      '激活率',
      '首活',
      '首活率',
      '拉活',
      '拉活率',
      '注册',
      '支付',
      '二留',
      '三留',
      '四留',
      '五留',
      '六留',
      '七留',
    ]);
    expect(series.find(({ label }) => label === '激活率')).toMatchObject({
      data: [null],
      yAxisIndex: 1,
    });
    expect(series.find(({ label }) => label === '点击')?.yAxisIndex).toBe(0);
  });

  it('formats trend values, comparison tones and the inclusive 90-day limit', () => {
    const rateDefinition = DASHBOARD_TREND_DEFINITIONS.find(
      ({ label }) => label === '激活率',
    );
    expect(rateDefinition).toBeDefined();
    if (!rateDefinition) return;
    expect(formatDashboardTrendValue(rateDefinition, null)).toBe(
      '暂无有效点击口径',
    );
    expect(formatDashboardTrendValue(rateDefinition, 12.345)).toBe('12.35%');
    expect(
      comparisonTone({
        changeRate: 1,
        comparisonLabel: '',
        comparisonValue: 1,
        value: 2,
      }),
    ).toBe('positive');
    expect(
      comparisonTone({
        changeRate: -1,
        comparisonLabel: '',
        comparisonValue: 2,
        value: 1,
      }),
    ).toBe('negative');
    expect(isDashboardDateRangeValid(['2026-05-12', '2026-08-09'])).toBe(true);
    expect(isDashboardDateRangeValid(['2026-05-11', '2026-08-09'])).toBe(false);
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
