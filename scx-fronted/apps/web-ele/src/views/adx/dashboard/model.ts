import type {
  DashboardOverview,
  DashboardQuery,
  DashboardSection,
  UnifiedReportMetricCode,
  UnifiedReportMetricValue,
} from '#/api/adx';

import { formatReportMetric } from '../report/report-model';

export type DashboardFilters = Omit<DashboardQuery, 'endDate' | 'startDate'> & {
  dates: [string, string];
};

export type DashboardTrendDefinition = {
  color: string;
  key: UnifiedReportMetricCode;
  label: string;
  rate: boolean;
};

export const DASHBOARD_TREND_DEFINITIONS: DashboardTrendDefinition[] = [
  { color: '#2563eb', key: 'validClicks', label: '有效点击', rate: false },
  { color: '#0f9f8f', key: 'activations', label: '激活', rate: false },
  { color: '#6d5ce7', key: 'activationRate', label: '激活率', rate: true },
  { color: '#db2777', key: 'registrations', label: '注册', rate: false },
  { color: '#d97706', key: 'payments', label: '付费', rate: false },
];

export function dashboardQuery(filters: DashboardFilters): DashboardQuery {
  return {
    advertiserId: filters.advertiserId || undefined,
    appLinkId: filters.appLinkId || undefined,
    channelId: filters.channelId || undefined,
    endDate: filters.dates[1],
    mediaAccountId: filters.mediaAccountId || undefined,
    productId: filters.productId || undefined,
    startDate: filters.dates[0],
    taskId: filters.taskId || undefined,
  };
}

export function buildDashboardTrendSeries(summary: DashboardOverview) {
  const points = summary.trend.data || [];
  return DASHBOARD_TREND_DEFINITIONS.map((definition) => ({
    ...definition,
    data: points.map((point) => point.metrics[definition.key]?.value ?? null),
    yAxisIndex: definition.rate ? 1 : 0,
  }));
}

export function formatDashboardMetric(
  metric: UnifiedReportMetricCode,
  value?: UnifiedReportMetricValue,
) {
  if (!value || value.status !== 'AVAILABLE') return '—';
  return formatReportMetric(value.value, metric);
}

export function formatComparison(changeRate?: number) {
  if (changeRate === null || changeRate === undefined) return '—';
  const prefix = changeRate > 0 ? '+' : '';
  return `${prefix}${changeRate.toFixed(2)}%`;
}

export function comparisonTone(changeRate?: number) {
  if (changeRate === null || changeRate === undefined || changeRate === 0)
    return 'muted';
  return changeRate > 0 ? 'positive' : 'negative';
}

export function dashboardEmptyKind(input: {
  filterTouched: boolean;
  firstRequestDone: boolean;
  loading: boolean;
  summary?: DashboardOverview;
}) {
  if (!input.firstRequestDone || input.loading || !input.summary) return '';
  const sections = [
    input.summary.cards,
    input.summary.trend,
    input.summary.productRanking,
    input.summary.taskRanking,
    input.summary.mediaRanking,
  ];
  if (!sections.every((section) => section.status === 'EMPTY')) return '';
  return input.filterTouched ? 'FILTER' : 'INITIAL';
}

export function sectionFailureText(section: DashboardSection<unknown>) {
  if (section.status !== 'ERROR' && section.status !== 'TIMEOUT') return '';
  const reason =
    section.status === 'TIMEOUT' ? '查询超时，可单独重试' : '区块加载失败';
  return section.traceId ? `${reason} · traceId ${section.traceId}` : reason;
}

export function isDashboardDateRangeValid(dates: [string, string]) {
  const start = Date.parse(`${dates[0]}T00:00:00Z`);
  const end = Date.parse(`${dates[1]}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
    return false;
  }
  return Math.floor((end - start) / 86_400_000) <= 89;
}

export function availableMetricDefinitions(
  summary: DashboardOverview | undefined,
  canViewSensitive: boolean,
) {
  return (summary?.metricDefinitions || []).filter(
    (definition) =>
      definition.status === 'AVAILABLE' &&
      (!definition.sensitive || canViewSensitive),
  );
}
