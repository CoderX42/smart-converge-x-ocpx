import type {
  Advertiser,
  AppLink,
  DashboardComparisonMetric,
  DashboardSummary,
  MonitorLink,
  ReportOptions,
} from '#/api/adx';

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
