import type {
  Advertiser,
  AppLink,
  DashboardComparisonMetric,
  DashboardMetricSet,
  DashboardSummary,
  MonitorLink,
  ReportOptions,
} from '#/api/adx';

export type DashboardTrendDefinition = {
  color: string;
  key: keyof DashboardMetricSet;
  label: string;
  rate: boolean;
};

export const DASHBOARD_TREND_DEFINITIONS: DashboardTrendDefinition[] = [
  { color: '#409eff', key: 'clicks', label: '点击', rate: false },
  { color: '#14b8a6', key: 'activations', label: '激活', rate: false },
  { color: '#6d5ce7', key: 'activationRate', label: '激活率', rate: true },
  { color: '#67c23a', key: 'firstActivations', label: '首活', rate: false },
  {
    color: '#e6a23c',
    key: 'firstActivationRate',
    label: '首活率',
    rate: true,
  },
  { color: '#2ab8df', key: 'reactivations', label: '拉活', rate: false },
  {
    color: '#b84cdb',
    key: 'reactivationRate',
    label: '拉活率',
    rate: true,
  },
  { color: '#ec4899', key: 'registrations', label: '注册', rate: false },
  { color: '#337ecc', key: 'payments', label: '支付', rate: false },
  { color: '#0ea5a4', key: 'retentionD2', label: '二留', rate: false },
  { color: '#7559e8', key: 'retentionD3', label: '三留', rate: false },
  { color: '#52b800', key: 'retentionD4', label: '四留', rate: false },
  { color: '#f59e0b', key: 'retentionD5', label: '五留', rate: false },
  { color: '#38bdf8', key: 'retentionD6', label: '六留', rate: false },
  { color: '#c04ce3', key: 'retentionD7', label: '七留', rate: false },
];

export function formatInteger(value: null | number | undefined) {
  return Number(value || 0).toLocaleString('zh-CN');
}

export function formatRate(value: null | number | undefined) {
  return value === null || value === undefined ? '—' : `${value.toFixed(2)}%`;
}

export function formatComparison(metric: DashboardComparisonMetric) {
  if (metric.changeRate === null || metric.changeRate === undefined) {
    return metric.comparisonValue === 0 && metric.value > 0 ? '新增' : '—';
  }
  const prefix = metric.changeRate > 0 ? '+' : '';
  return `${prefix}${metric.changeRate.toFixed(2)}%`;
}

export function comparisonTone(metric?: DashboardComparisonMetric) {
  if (metric?.changeRate === undefined || metric.changeRate === null) {
    return metric && metric.value > 0 && metric.comparisonValue === 0
      ? 'positive'
      : 'muted';
  }
  if (metric.changeRate > 0) return 'positive';
  if (metric.changeRate < 0) return 'negative';
  return 'muted';
}

export function buildDashboardTrendSeries(summary: DashboardSummary) {
  return DASHBOARD_TREND_DEFINITIONS.map((definition) => ({
    ...definition,
    data: summary.trend.map((point) => point.metrics[definition.key] ?? null),
    yAxisIndex: definition.rate ? 1 : 0,
  }));
}

export function formatDashboardTrendValue(
  definition: DashboardTrendDefinition,
  value: null | number | undefined,
) {
  if (definition.rate) {
    return value === null || value === undefined
      ? '暂无有效点击口径'
      : `${value.toFixed(2)}%`;
  }
  return formatInteger(value);
}

export function isDashboardDateRangeValid(dates: [string, string]) {
  const start = Date.parse(`${dates[0]}T00:00:00Z`);
  const end = Date.parse(`${dates[1]}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
    return false;
  }
  return Math.floor((end - start) / 86_400_000) <= 89;
}

export function buildTrendSeries(
  summary: DashboardSummary,
  keys: Array<keyof DashboardSummary['totals']>,
) {
  return keys.map((key) => ({
    data: summary.trend.map((point) => point.metrics[key] ?? null),
    key,
  }));
}

export function buildRetentionHeatmap(summary: DashboardSummary) {
  const dates = summary.trend.map((point) => point.reportDate);
  const days = ['D2', 'D3', 'D4', 'D5', 'D6', 'D7'];
  const dateIndex = new Map(dates.map((date, index) => [date, index]));
  const dayIndex = new Map(days.map((day, index) => [day, index]));
  return {
    data: summary.retention.map((point) => [
      dateIndex.get(point.reportDate) ?? 0,
      dayIndex.get(point.day) ?? 0,
      point.value,
    ]),
    dates,
    days,
  };
}

export function cascadeOptions(
  options: ReportOptions,
  filters: {
    advertiserId?: string;
    appLinkId?: string;
    channelId?: string;
  },
): {
  advertisers: Advertiser[];
  appLinks: AppLink[];
  monitors: MonitorLink[];
} {
  const monitors = options.monitors.filter(
    (item) =>
      (!filters.channelId || item.channelId === filters.channelId) &&
      (!filters.advertiserId || item.advertiserId === filters.advertiserId) &&
      (!filters.appLinkId || item.appLinkId === filters.appLinkId),
  );
  const advertiserIds = new Set(monitors.map((item) => item.advertiserId));
  const appLinkIds = new Set(monitors.map((item) => item.appLinkId));
  return {
    advertisers: options.advertisers.filter(
      (item) => !filters.channelId || advertiserIds.has(item.id),
    ),
    appLinks: options.appLinks.filter(
      (item) =>
        (!filters.advertiserId || item.advertiserId === filters.advertiserId) &&
        (!filters.channelId || appLinkIds.has(item.id)),
    ),
    monitors,
  };
}

export function shouldPauseAutoRefresh(documentHidden: boolean) {
  return documentHidden;
}
