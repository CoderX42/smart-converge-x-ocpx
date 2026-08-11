import { describe, expect, it } from 'vitest';

import {
  availableMetricDefinitions,
  buildDashboardTrendSeries,
  comparisonTone,
  dashboardEmptyKind,
  dashboardQuery,
  formatComparison,
  formatDashboardMetric,
  isDashboardDateRangeValid,
  sectionFailureText,
} from './model';

describe('s9 Dashboard model', () => {
  it('keeps zero denominator and unavailable metrics as missing values', () => {
    expect(
      formatDashboardMetric('activationRate', {
        denominator: 0,
        numerator: 4,
        status: 'ZERO_DENOMINATOR',
      }),
    ).toBe('—');
    expect(
      formatDashboardMetric('activationRate', {
        denominator: 10,
        numerator: 4,
        status: 'AVAILABLE',
        value: 40,
      }),
    ).toBe('40.00%');
  });

  it('builds only S8 AVAILABLE trend metrics and preserves null points', () => {
    const summary = {
      trend: {
        data: [
          {
            bucket: '2026-08-10',
            metrics: {
              activationRate: { status: 'ZERO_DENOMINATOR' },
              validClicks: { status: 'AVAILABLE', value: 12 },
            },
          },
        ],
        status: 'READY',
      },
    } as never;
    const series = buildDashboardTrendSeries(summary);
    expect(series.map((item) => item.label)).toEqual([
      '有效点击',
      '激活',
      '激活率',
      '注册',
      '付费',
    ]);
    expect(series.find((item) => item.key === 'activationRate')?.data).toEqual([
      null,
    ]);
  });

  it('keeps filters in one query and enforces the inclusive 90-day range', () => {
    expect(
      dashboardQuery({
        advertiserId: 'adv-1',
        appLinkId: '',
        channelId: 'channel-1',
        dates: ['2026-08-01', '2026-08-10'],
        mediaAccountId: '',
        productId: '',
        taskId: 'task-1',
      }),
    ).toEqual({
      advertiserId: 'adv-1',
      appLinkId: undefined,
      channelId: 'channel-1',
      endDate: '2026-08-10',
      mediaAccountId: undefined,
      productId: undefined,
      startDate: '2026-08-01',
      taskId: 'task-1',
    });
    expect(isDashboardDateRangeValid(['2026-05-13', '2026-08-10'])).toBe(true);
    expect(isDashboardDateRangeValid(['2026-05-12', '2026-08-10'])).toBe(false);
  });

  it('distinguishes initial and filtered empty plus independent trace failures', () => {
    const summary = {
      cards: { status: 'EMPTY' },
      mediaRanking: { status: 'EMPTY' },
      productRanking: { status: 'EMPTY' },
      taskRanking: { status: 'EMPTY' },
      trend: { status: 'EMPTY' },
    } as never;
    expect(
      dashboardEmptyKind({
        filterTouched: false,
        firstRequestDone: true,
        loading: false,
        summary,
      }),
    ).toBe('INITIAL');
    expect(
      dashboardEmptyKind({
        filterTouched: true,
        firstRequestDone: true,
        loading: false,
        summary,
      }),
    ).toBe('FILTER');
    expect(
      sectionFailureText({ status: 'ERROR', traceId: 'trace-safe' }),
    ).toContain('trace-safe');
  });

  it('hides unavailable or sensitive definitions without permission', () => {
    const summary = {
      metricDefinitions: [
        { code: 'validClicks', sensitive: false, status: 'AVAILABLE' },
        { code: 'costAmount', sensitive: true, status: 'AVAILABLE' },
        { code: 'unknown', sensitive: false, status: 'UNAVAILABLE' },
      ],
    } as never;
    expect(
      availableMetricDefinitions(summary, false).map((item) => item.code),
    ).toEqual(['validClicks']);
    expect(
      availableMetricDefinitions(summary, true).map((item) => item.code),
    ).toEqual(['validClicks', 'costAmount']);
    expect(formatComparison(-12.5)).toBe('-12.50%');
    expect(comparisonTone(2)).toBe('positive');
  });
});
