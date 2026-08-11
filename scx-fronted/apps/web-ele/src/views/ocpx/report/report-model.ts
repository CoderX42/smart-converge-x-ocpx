import type { UnifiedReportMetricCode, UnifiedReportQuery } from '#/api/ocpx';

export function reportGroupBy(path: string): UnifiedReportQuery['groupBy'] {
  if (path.endsWith('/product')) return 'PRODUCT';
  if (path.endsWith('/link')) return 'LINK';
  if (path.endsWith('/task')) return 'TASK';
  if (path.endsWith('/media')) return 'MEDIA';
  return 'TIME';
}

export function reportEmptyKind(input: {
  filterTouched: boolean;
  firstRequestDone: boolean;
  loading: boolean;
  rowCount: number;
}) {
  if (!input.firstRequestDone || input.loading || input.rowCount) return '';
  return input.filterTouched ? 'FILTER' : 'INITIAL';
}

export function formatReportMetric(
  value: null | number | undefined,
  metric: UnifiedReportMetricCode,
) {
  if (value === null || value === undefined) return '—';
  if (metric.endsWith('Rate')) return `${Number(value).toFixed(2)}%`;
  if (metric === 'costAmount')
    return Number(value).toLocaleString('zh-CN', {
      currency: 'CNY',
      minimumFractionDigits: 2,
      style: 'currency',
    });
  return Number(value).toLocaleString('zh-CN');
}

export function taskReportQuery(input: {
  dates: [string, string];
  metrics: UnifiedReportMetricCode[];
  mode: 'DAY_LINK' | 'DAY_TASK' | 'HOUR_TASK';
  page: number;
  size: number;
  taskId: string;
}): UnifiedReportQuery {
  return {
    current: input.page,
    endDate: input.dates[1],
    grain: input.mode === 'HOUR_TASK' ? 'HOUR' : 'DAY',
    groupBy: input.mode === 'DAY_LINK' ? 'LINK' : 'TASK',
    metrics: input.metrics,
    size: input.size,
    sort: 'dimension',
    startDate: input.dates[0],
    taskId: input.taskId,
  };
}
