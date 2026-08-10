import { describe, expect, it } from 'vitest';

import {
  formatReportMetric,
  reportEmptyKind,
  reportGroupBy,
  taskReportQuery,
} from './report-model';

describe('s8 unified report model', () => {
  it('maps all five routes to the one query contract', () => {
    expect(reportGroupBy('/adx/report/time')).toBe('TIME');
    expect(reportGroupBy('/adx/report/product')).toBe('PRODUCT');
    expect(reportGroupBy('/adx/report/link')).toBe('LINK');
    expect(reportGroupBy('/adx/report/task')).toBe('TASK');
    expect(reportGroupBy('/adx/report/media')).toBe('MEDIA');
  });

  it('keeps initial empty and filtered empty distinct', () => {
    expect(
      reportEmptyKind({
        filterTouched: false,
        firstRequestDone: true,
        loading: false,
        rowCount: 0,
      }),
    ).toBe('INITIAL');
    expect(
      reportEmptyKind({
        filterTouched: true,
        firstRequestDone: true,
        loading: false,
        rowCount: 0,
      }),
    ).toBe('FILTER');
  });

  it('renders zero but keeps an unavailable denominator as dash', () => {
    expect(formatReportMetric(0, 'validClicks')).toBe('0');
    expect(formatReportMetric(null, 'activationRate')).toBe('—');
    expect(formatReportMetric(12.345, 'activationRate')).toBe('12.35%');
  });

  it('locks the three task drawers to taskId and their declared grain', () => {
    const base = {
      dates: ['2026-08-01', '2026-08-07'] as [string, string],
      metrics: ['validClicks'] as const,
      page: 2,
      size: 20,
      taskId: 'task-1',
    };
    expect(
      taskReportQuery({
        ...base,
        metrics: [...base.metrics],
        mode: 'HOUR_TASK',
      }),
    ).toMatchObject({ grain: 'HOUR', groupBy: 'TASK', taskId: 'task-1' });
    expect(
      taskReportQuery({
        ...base,
        metrics: [...base.metrics],
        mode: 'DAY_TASK',
      }),
    ).toMatchObject({ grain: 'DAY', groupBy: 'TASK', taskId: 'task-1' });
    expect(
      taskReportQuery({
        ...base,
        metrics: [...base.metrics],
        mode: 'DAY_LINK',
      }),
    ).toMatchObject({ grain: 'DAY', groupBy: 'LINK', taskId: 'task-1' });
  });
});
