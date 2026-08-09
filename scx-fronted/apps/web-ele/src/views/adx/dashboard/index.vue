<script lang="ts" setup>
import type {
  DashboardAccountSummary,
  DashboardComparisonMetric,
  DashboardSummary,
  ReportOptions,
  RuntimeOverview,
} from '#/api/adx';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';
import { EchartsUI, useEcharts } from '@boot/plugins/echarts';

import {
  Filter,
  FullScreen,
  InfoFilled,
  Refresh,
} from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  getDashboardAccountSummary,
  getDashboardSummary,
  getReportOptions,
  getRuntimeOverview,
} from '#/api/adx';

import {
  buildDashboardTrendSeries,
  buildRetentionHeatmap,
  cascadeOptions,
  comparisonTone,
  DASHBOARD_TREND_DEFINITIONS,
  formatComparison,
  formatDashboardTrendValue,
  formatInteger,
  formatRate,
  isDashboardDateRangeValid,
  shouldPauseAutoRefresh,
} from './model';

defineOptions({ name: 'AdxDashboard' });

type DateRange = [string, string];
type DashboardPreset = 7 | 30 | 90 | 'CUSTOM';
type FilterKey = 'advertiserId' | 'appLinkId' | 'channelId' | 'monitorId';
type DashboardFilters = {
  advertiserId: string;
  appLinkId: string;
  channelId: string;
  dates: DateRange;
  monitorId: string;
};

const AUTO_REFRESH_MS = 60_000;
const { hasAccessByCodes } = useAccess();
const canViewRuntime = computed(() => hasAccessByCodes(['adx:runtime:read']));
const canViewAccount = computed(() => hasAccessByCodes(['adx:account:read']));

function defaultDateRange(days = 30): DateRange {
  return [
    dayjs()
      .subtract(days - 1, 'day')
      .format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ];
}

function createFilters(): DashboardFilters {
  return {
    advertiserId: '',
    appLinkId: '',
    channelId: '',
    dates: defaultDateRange(),
    monitorId: '',
  };
}

const pageRef = ref<HTMLElement>();
const loading = ref(false);
const refreshing = ref(false);
const optionalLoading = ref(false);
const errorMessage = ref('');
const runtimeError = ref(false);
const accountError = ref(false);
const isFullscreen = ref(false);
const autoRefreshPaused = ref(false);
const filterDrawerVisible = ref(false);
const expandedSections = ref<string[]>([]);
const summary = ref<DashboardSummary>();
const account = ref<DashboardAccountSummary>();
const runtime = ref<RuntimeOverview>();
const options = ref<ReportOptions>({
  advertisers: [],
  appLinks: [],
  channels: [],
  monitors: [],
});
const preset = ref<DashboardPreset>(30);
const draftPreset = ref<DashboardPreset>(30);
const filters = reactive<DashboardFilters>(createFilters());
const draftFilters = reactive<DashboardFilters>(createFilters());

const trendRef = ref();
const funnelRef = ref();
const retentionRef = ref();
const qualityRef = ref();
const configurationRef = ref();
const trendChart = useEcharts(trendRef);
const funnelChart = useEcharts(funnelRef);
const retentionChart = useEcharts(retentionRef);
const qualityChart = useEcharts(qualityRef);
const configurationChart = useEcharts(configurationRef);

let refreshTimer: ReturnType<typeof setInterval> | undefined;

const moreExpanded = computed(() => expandedSections.value.includes('more'));
const draftCascaded = computed(() =>
  cascadeOptions(options.value, draftFilters),
);
const hasData = computed(() => {
  const totals = summary.value?.totals;
  return Boolean(
    totals &&
    (totals.clicks ||
      totals.impressions ||
      totals.rawConversions ||
      totals.activations),
  );
});
const coverageMessage = computed(() => {
  const coverage = summary.value?.dataCoverage;
  if (!coverage || coverage.rateStatus === 'NO_ELIGIBLE_CLICK') {
    return '暂无有效点击口径';
  }
  if (coverage.rateStatus === 'INCOMPLETE_HISTORY') {
    return `历史点击质量覆盖不足（${formatInteger(coverage.classifiedClicks)}/${formatInteger(coverage.totalClicks)}）`;
  }
  return '设备窗有效点击口径完整';
});
const coverageTagType = computed(() =>
  summary.value?.dataCoverage.rateStatus === 'COMPLETE' ? 'success' : 'warning',
);
const coreCards = computed(() => {
  const highlights = summary.value?.highlights;
  return [
    {
      key: 'clicks',
      label: '今日点击',
      metric: highlights?.todayClicks,
      note: '唯一 event_id 的全部点击事实',
    },
    {
      key: 'activate',
      label: '今日激活',
      metric: highlights?.todayActivations,
      note: '上游 ACTIVATE 事件次数',
    },
    {
      key: 'retention',
      label: '昨日次留',
      metric: highlights?.yesterdayRetentionD2,
      note: '上游明确回传的 D2 留存事件',
    },
    {
      key: 'first',
      label: '今日首活',
      metric: highlights?.todayFirstActivations,
      note: 'FIRST_ACTIVATION 首次激活事件',
    },
    {
      key: 'payment',
      label: '今日付费',
      metric: highlights?.todayPayments,
      note: '付费事件次数，不表示支付金额',
    },
  ];
});
const auxiliaryCards = computed(() => {
  const totals = summary.value?.totals;
  return [
    { label: '展示量', value: formatInteger(totals?.impressions) },
    { label: '有效点击', value: formatInteger(totals?.eligibleClicks) },
    { label: '重复点击', value: formatInteger(totals?.deduplicatedClicks) },
    { label: '注册', value: formatInteger(totals?.registrations) },
    { label: '拉活', value: formatInteger(totals?.reactivations) },
    { label: '激活率', value: formatRate(totals?.activationRate) },
    { label: '首活率', value: formatRate(totals?.firstActivationRate) },
    { label: '拉活率', value: formatRate(totals?.reactivationRate) },
    { label: 'CPC 消耗', value: money(totals?.costAmount) },
  ];
});
const lastUpdated = computed(() => {
  const value = summary.value?.dataUpdatedAt || summary.value?.generatedAt;
  return value ? dayjs(value).format('MM-DD HH:mm:ss') : '尚未更新';
});
const dateRangeLabel = computed(
  () => `${filters.dates[0]} 至 ${filters.dates[1]}`,
);
const activeFilterTags = computed(() => {
  const tags: Array<{ key: FilterKey; label: string }> = [];
  const channel = options.value.channels.find(
    (item) => item.id === filters.channelId,
  );
  const advertiser = options.value.advertisers.find(
    (item) => item.id === filters.advertiserId,
  );
  const appLink = options.value.appLinks.find(
    (item) => item.id === filters.appLinkId,
  );
  const monitor = options.value.monitors.find(
    (item) => item.id === filters.monitorId,
  );
  if (channel)
    tags.push({ key: 'channelId', label: `渠道：${channel.channelName}` });
  if (advertiser) {
    tags.push({
      key: 'advertiserId',
      label: `广告主：${advertiser.advertiserName}`,
    });
  }
  if (appLink)
    tags.push({ key: 'appLinkId', label: `应用：${appLink.linkName}` });
  if (monitor)
    tags.push({ key: 'monitorId', label: `监测：${monitor.monitorName}` });
  return tags;
});

function query() {
  return {
    advertiserId: filters.advertiserId || undefined,
    appLinkId: filters.appLinkId || undefined,
    channelId: filters.channelId || undefined,
    endDate: filters.dates[1],
    monitorId: filters.monitorId || undefined,
    startDate: filters.dates[0],
  };
}

function money(value: null | number | undefined) {
  return Number(value || 0).toLocaleString('zh-CN', {
    currency: 'CNY',
    minimumFractionDigits: 2,
    style: 'currency',
  });
}

function metricValue(metric?: DashboardComparisonMetric) {
  return formatInteger(metric?.value);
}

function invalidateOptionalData() {
  runtime.value = undefined;
  account.value = undefined;
  runtimeError.value = false;
  accountError.value = false;
}

function setPreset(value: 7 | 30 | 90) {
  preset.value = value;
  filters.dates = defaultDateRange(value);
  invalidateOptionalData();
  void load();
}

function openFilterDrawer() {
  Object.assign(draftFilters, {
    ...filters,
    dates: [...filters.dates] as DateRange,
  });
  draftPreset.value = preset.value;
  filterDrawerVisible.value = true;
}

function setDraftPreset(value: 7 | 30 | 90) {
  draftPreset.value = value;
  draftFilters.dates = defaultDateRange(value);
}

function resetDraftFilters() {
  Object.assign(draftFilters, createFilters());
  draftPreset.value = 30;
}

function applyDraftFilters() {
  if (!isDashboardDateRangeValid(draftFilters.dates)) {
    ElMessage.warning('日期范围必须按顺序选择，且最多包含 90 个自然日');
    return;
  }
  Object.assign(filters, {
    ...draftFilters,
    dates: [...draftFilters.dates] as DateRange,
  });
  preset.value = draftPreset.value;
  filterDrawerVisible.value = false;
  invalidateOptionalData();
  void load();
}

function clearFilter(key: FilterKey) {
  filters[key] = '';
  invalidateOptionalData();
  void load();
}

function disabledDate(date: Date) {
  return dayjs(date).isAfter(dayjs(), 'day');
}

async function loadOptions() {
  try {
    options.value = await getReportOptions();
  } catch {
    ElMessage.warning('筛选项加载失败，数据概览仍可使用');
  }
}

async function loadOptionalData() {
  if (!moreExpanded.value || optionalLoading.value) return;
  optionalLoading.value = true;
  const requests: Promise<void>[] = [];
  if (canViewRuntime.value) {
    requests.push(
      getRuntimeOverview()
        .then((value) => {
          runtime.value = value;
          runtimeError.value = false;
        })
        .catch(() => {
          runtime.value = undefined;
          runtimeError.value = true;
        }),
    );
  }
  if (canViewAccount.value) {
    requests.push(
      getDashboardAccountSummary(query())
        .then((value) => {
          account.value = value;
          accountError.value = false;
        })
        .catch(() => {
          account.value = undefined;
          accountError.value = true;
        }),
    );
  }
  try {
    await Promise.all(requests);
  } finally {
    optionalLoading.value = false;
  }
}

async function load(silent = false) {
  if (loading.value || refreshing.value) return;
  if (silent) refreshing.value = true;
  else loading.value = true;
  errorMessage.value = '';
  try {
    summary.value = await getDashboardSummary(query());
    await nextTick();
    await renderOverviewChart();
    if (moreExpanded.value) {
      await Promise.all([renderDetailCharts(), loadOptionalData()]);
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '仪表盘数据加载失败';
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

function compactAxisNumber(value: unknown) {
  const number = Number(value || 0);
  if (Math.abs(number) >= 100_000_000) return `${number / 100_000_000}亿`;
  if (Math.abs(number) >= 10_000) return `${number / 10_000}万`;
  return formatInteger(number);
}

function trendTooltipFormatter(value: unknown) {
  const params = (Array.isArray(value) ? value : [value]) as Array<{
    axisValueLabel?: string;
    marker?: string;
    seriesName?: string;
    value?: null | number;
  }>;
  const date = params[0]?.axisValueLabel || '—';
  const rows = DASHBOARD_TREND_DEFINITIONS.map((definition) => {
    const item = params.find(
      ({ seriesName }) => seriesName === definition.label,
    );
    return `<div style="display:flex;min-width:210px;align-items:center;justify-content:space-between;gap:24px;line-height:24px"><span>${item?.marker || ''}${definition.label}</span><strong>${formatDashboardTrendValue(definition, item?.value)}</strong></div>`;
  });
  return `<div style="margin-bottom:6px;font-weight:600">${date}</div>${rows.join('')}`;
}

async function renderOverviewChart() {
  const data = summary.value;
  if (!data || !hasData.value) return;
  const series = buildDashboardTrendSeries(data);
  await trendChart.renderEcharts({
    animationDuration: 420,
    color: series.map(({ color }) => color),
    grid: { bottom: 68, containLabel: true, left: 18, right: 24, top: 38 },
    legend: {
      bottom: 4,
      data: series.map(({ label }) => label),
      type: 'scroll' as const,
    },
    series: series.map((item) => ({
      connectNulls: false,
      data: item.data,
      emphasis: { focus: 'series' as const },
      itemStyle: { color: item.color },
      lineStyle: {
        color: item.color,
        width: item.key === 'clicks' ? 2.6 : 1.8,
      },
      name: item.label,
      showSymbol: false,
      smooth: 0.28,
      symbol: 'circle',
      symbolSize: 7,
      type: 'line' as const,
      yAxisIndex: item.yAxisIndex,
    })),
    tooltip: {
      axisPointer: { type: 'line' as const },
      confine: true,
      extraCssText: 'max-height:70vh;overflow-y:auto;',
      formatter: trendTooltipFormatter,
      trigger: 'axis' as const,
    },
    xAxis: {
      axisTick: { show: false },
      boundaryGap: false,
      data: data.trend.map((item) => item.reportDate),
      type: 'category' as const,
    },
    yAxis: [
      {
        axisLabel: { formatter: compactAxisNumber },
        name: '数量',
        type: 'value' as const,
      },
      {
        axisLabel: { formatter: '{value}%' },
        min: 0,
        name: '比率',
        splitLine: { show: false },
        type: 'value' as const,
      },
    ],
  });
}

async function renderDetailCharts() {
  const data = summary.value;
  if (!data || !hasData.value || !moreExpanded.value) return;
  const heatmap = buildRetentionHeatmap(data);
  const heatMax = Math.max(...heatmap.data.map((item) => Number(item[2])), 1);
  const tooltip = { confine: true, trigger: 'axis' as const };
  await Promise.all([
    funnelChart.renderEcharts({
      grid: { bottom: 20, containLabel: true, left: 16, right: 34, top: 20 },
      series: [
        {
          barMaxWidth: 32,
          data: [...data.funnel].reverse().map((item, index) => ({
            itemStyle: {
              color: ['#f56c6c', '#409eff', '#e6a23c', '#67c23a'][index],
            },
            value: item.value,
          })),
          label: { formatter: '{c}', position: 'right', show: true },
          type: 'bar',
        },
      ],
      tooltip,
      xAxis: { type: 'value' },
      yAxis: {
        axisTick: { show: false },
        data: [...data.funnel].reverse().map((item) => item.label),
        type: 'category',
      },
    }),
    retentionChart.renderEcharts({
      grid: { bottom: 58, containLabel: true, left: 20, right: 28, top: 18 },
      series: [
        {
          data: heatmap.data,
          emphasis: { itemStyle: { borderColor: '#ffffff', borderWidth: 1 } },
          label: { formatter: '{@[2]}', show: true },
          type: 'heatmap',
        },
      ],
      tooltip: {
        confine: true,
        formatter: (params: unknown) => {
          const item = params as { value: [number, number, number] };
          const [dateIndex, dayIndex, pointValue] = item.value;
          return `${heatmap.dates[dateIndex] || '—'}<br/>${heatmap.days[dayIndex] || '—'}：<strong>${formatInteger(pointValue)}</strong>`;
        },
        trigger: 'item',
      },
      visualMap: {
        calculable: true,
        inRange: { color: ['#ecf5ff', '#79bbff', '#337ecc'] },
        max: heatMax,
        min: 0,
        orient: 'horizontal',
        bottom: 0,
        left: 'center',
      },
      xAxis: {
        data: heatmap.dates.map((date) => date.slice(5)),
        splitArea: { show: true },
        type: 'category',
      },
      yAxis: {
        data: heatmap.days,
        splitArea: { show: true },
        type: 'category',
      },
    }),
    qualityChart.renderEcharts({
      grid: { bottom: 24, containLabel: true, left: 20, right: 20, top: 42 },
      legend: { top: 4 },
      series: [
        {
          data: [data.totals.rawConversions],
          itemStyle: { color: '#409eff' },
          name: '原始',
          type: 'bar',
        },
        {
          data: [data.totals.forwardedConversions],
          itemStyle: { color: '#67c23a' },
          name: '成功回传',
          type: 'bar',
        },
        {
          data: [data.totals.deductedConversions],
          itemStyle: { color: '#e6a23c' },
          name: '扣除',
          type: 'bar',
        },
        {
          data: [data.totals.deliveryFailures],
          itemStyle: { color: '#f56c6c' },
          name: '死亡失败',
          type: 'bar',
        },
      ],
      tooltip,
      xAxis: { data: ['当前区间'], type: 'category' },
      yAxis: { type: 'value' },
    }),
    configurationChart.renderEcharts({
      legend: { bottom: 0 },
      series: [
        {
          center: ['50%', '44%'],
          data: [
            { name: '已发布', value: data.configuration.publishedRoutes },
            { name: '草稿', value: data.configuration.draftRoutes },
            { name: '异常', value: data.configuration.abnormalRoutes },
          ],
          label: { formatter: '{b} {c}' },
          radius: ['46%', '70%'],
          type: 'pie',
        },
      ],
      tooltip: { confine: true, trigger: 'item' },
    }),
  ]);
}

async function toggleFullscreen() {
  try {
    await (document.fullscreenElement
      ? document.exitFullscreen()
      : pageRef.value?.requestFullscreen());
  } catch {
    ElMessage.warning('当前浏览器未允许全屏显示');
  }
}

function resizeCharts() {
  trendChart.resize();
  if (moreExpanded.value) {
    funnelChart.resize();
    retentionChart.resize();
    qualityChart.resize();
    configurationChart.resize();
  }
}

function handleFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === pageRef.value;
  nextTick(resizeCharts);
}

function handleVisibilityChange() {
  autoRefreshPaused.value = shouldPauseAutoRefresh(document.hidden);
  if (!document.hidden) void load(true);
}

watch(
  () => draftFilters.advertiserId,
  () => {
    if (
      draftFilters.appLinkId &&
      !draftCascaded.value.appLinks.some(
        (item) => item.id === draftFilters.appLinkId,
      )
    ) {
      draftFilters.appLinkId = '';
    }
    if (
      draftFilters.monitorId &&
      !draftCascaded.value.monitors.some(
        (item) => item.id === draftFilters.monitorId,
      )
    ) {
      draftFilters.monitorId = '';
    }
  },
);

watch(
  () => draftFilters.channelId,
  () => {
    if (
      draftFilters.advertiserId &&
      !draftCascaded.value.advertisers.some(
        (item) => item.id === draftFilters.advertiserId,
      )
    ) {
      draftFilters.advertiserId = '';
    }
    if (
      draftFilters.appLinkId &&
      !draftCascaded.value.appLinks.some(
        (item) => item.id === draftFilters.appLinkId,
      )
    ) {
      draftFilters.appLinkId = '';
    }
    if (
      draftFilters.monitorId &&
      !draftCascaded.value.monitors.some(
        (item) => item.id === draftFilters.monitorId,
      )
    ) {
      draftFilters.monitorId = '';
    }
  },
);

watch(
  () => draftFilters.appLinkId,
  () => {
    if (
      draftFilters.monitorId &&
      !draftCascaded.value.monitors.some(
        (item) => item.id === draftFilters.monitorId,
      )
    ) {
      draftFilters.monitorId = '';
    }
  },
);

watch(expandedSections, async () => {
  if (!moreExpanded.value) return;
  await nextTick();
  await Promise.all([renderDetailCharts(), loadOptionalData()]);
});

onMounted(async () => {
  document.addEventListener('fullscreenchange', handleFullscreenChange);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  await Promise.all([loadOptions(), load()]);
  refreshTimer = setInterval(() => {
    if (!document.hidden) void load(true);
  }, AUTO_REFRESH_MS);
});

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer);
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<template>
  <Page>
    <main
      ref="pageRef"
      class="dashboard-page"
      :class="{ fullscreen: isFullscreen }"
    >
      <section class="action-bar" aria-label="仪表盘操作">
        <div class="range-actions">
          <el-radio-group
            :model-value="preset"
            size="small"
            @change="setPreset($event as 7 | 30 | 90)"
          >
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
            <el-radio-button :value="90">90 天</el-radio-button>
          </el-radio-group>
          <button
            class="date-range-button"
            type="button"
            @click="openFilterDrawer"
          >
            {{ dateRangeLabel }}
          </button>
        </div>
        <div class="page-actions">
          <div class="refresh-meta">
            <span :class="{ paused: autoRefreshPaused }">
              {{ autoRefreshPaused ? '自动刷新已暂停' : '60 秒自动刷新' }}
            </span>
            <small>更新于 {{ lastUpdated }}</small>
          </div>
          <el-button :icon="Filter" @click="openFilterDrawer">
            筛选<span v-if="activeFilterTags.length > 0"
              >（{{ activeFilterTags.length }}）</span
            >
          </el-button>
          <el-button
            :icon="Refresh"
            :loading="refreshing"
            aria-label="刷新仪表盘"
            @click="load(true)"
          >
            刷新
          </el-button>
          <el-button :icon="FullScreen" @click="toggleFullscreen">
            {{ isFullscreen ? '退出全屏' : '全屏' }}
          </el-button>
        </div>
      </section>

      <div v-if="activeFilterTags.length > 0" class="active-filters">
        <span>当前筛选</span>
        <el-tag
          v-for="tag in activeFilterTags"
          :key="tag.key"
          closable
          effect="plain"
          @close="clearFilter(tag.key)"
        >
          {{ tag.label }}
        </el-tag>
      </div>

      <el-alert
        v-if="errorMessage"
        class="error-alert"
        :closable="false"
        show-icon
        title="数据暂时无法加载"
        type="error"
      >
        <template #default>
          <span>{{ errorMessage }}</span>
          <el-button link type="danger" @click="load(false)"
            >重新加载</el-button
          >
        </template>
      </el-alert>

      <section
        v-if="loading && !summary"
        class="metric-grid"
        aria-label="加载中"
      >
        <el-card
          v-for="index in 5"
          :key="index"
          shadow="never"
          class="metric-card"
        >
          <el-skeleton animated :rows="2" />
        </el-card>
      </section>
      <section v-else class="metric-grid" aria-label="今日核心指标">
        <el-card
          v-for="card in coreCards"
          :key="card.key"
          shadow="never"
          class="metric-card"
        >
          <div class="metric-title">
            <span>{{ card.label }}</span>
            <el-tooltip :content="card.note" placement="top">
              <el-icon class="metric-help" :aria-label="card.note">
                <InfoFilled />
              </el-icon>
            </el-tooltip>
          </div>
          <div class="metric-value-row">
            <strong>{{ metricValue(card.metric) }}</strong>
            <span class="comparison-label">
              {{ card.metric?.comparisonLabel || '较昨日' }}
            </span>
            <b :class="comparisonTone(card.metric)">
              {{
                formatComparison(
                  card.metric || {
                    value: 0,
                    comparisonValue: 0,
                    comparisonLabel: '',
                  },
                )
              }}
            </b>
          </div>
        </el-card>
      </section>

      <el-card shadow="never" class="trend-card">
        <template #header>
          <div class="card-heading">
            <div>
              <h2>数据趋势</h2>
              <p>{{ dateRangeLabel }}</p>
            </div>
            <el-tooltip :content="coverageMessage" placement="left">
              <el-tag :type="coverageTagType" effect="plain" size="small">
                {{
                  summary?.dataCoverage.clickQualityComplete
                    ? '口径完整'
                    : '口径说明'
                }}
              </el-tag>
            </el-tooltip>
          </div>
        </template>
        <el-skeleton v-if="loading && !summary" animated class="chart-skeleton">
          <template #template>
            <el-skeleton-item variant="rect" class="chart-skeleton__item" />
          </template>
        </el-skeleton>
        <EchartsUI v-else-if="hasData" ref="trendRef" height="500px" />
        <el-empty
          v-else
          :image-size="96"
          description="当前筛选范围暂无效果数据"
        >
          <el-button type="primary" @click="setPreset(30)"
            >查看最近 30 天</el-button
          >
        </el-empty>
      </el-card>

      <el-collapse
        v-if="summary"
        v-model="expandedSections"
        class="more-collapse"
      >
        <el-collapse-item name="more">
          <template #title>
            <div class="more-title">
              <div>
                <strong>更多数据</strong>
                <span>辅助指标、漏斗、留存、回传质量与运行信息</span>
              </div>
              <el-tag effect="plain" size="small">
                {{ moreExpanded ? '收起' : '展开查看' }}
              </el-tag>
            </div>
          </template>

          <div class="more-content">
            <section class="auxiliary-grid" aria-label="区间辅助指标">
              <article v-for="item in auxiliaryCards" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </article>
            </section>

            <section v-if="hasData" class="detail-grid">
              <el-card shadow="never" class="detail-card">
                <template #header>
                  <div class="detail-heading">
                    <h3>转化漏斗</h3>
                    <span>有效点击 → 激活 → 注册 → 付费</span>
                  </div>
                </template>
                <EchartsUI ref="funnelRef" height="300px" />
              </el-card>
              <el-card shadow="never" class="detail-card retention-card">
                <template #header>
                  <div class="detail-heading">
                    <h3>D2–D7 留存矩阵</h3>
                    <span>上游明确回传事件次数</span>
                  </div>
                </template>
                <EchartsUI ref="retentionRef" height="320px" />
              </el-card>
              <el-card shadow="never" class="detail-card">
                <template #header>
                  <div class="detail-heading">
                    <h3>回传质量</h3>
                    <span>原始、成功、扣除与死亡失败</span>
                  </div>
                </template>
                <EchartsUI ref="qualityRef" height="290px" />
              </el-card>
              <el-card shadow="never" class="detail-card">
                <template #header>
                  <div class="detail-heading">
                    <h3>配置覆盖</h3>
                    <span>渠道、广告主、应用与路由状态</span>
                  </div>
                </template>
                <div class="configuration-body">
                  <EchartsUI ref="configurationRef" height="250px" />
                  <dl>
                    <div>
                      <dt>启用渠道</dt>
                      <dd>
                        {{
                          formatInteger(summary.configuration.activeChannels)
                        }}
                      </dd>
                    </div>
                    <div>
                      <dt>启用广告主</dt>
                      <dd>
                        {{
                          formatInteger(summary.configuration.activeAdvertisers)
                        }}
                      </dd>
                    </div>
                    <div>
                      <dt>应用链接</dt>
                      <dd>
                        {{
                          formatInteger(summary.configuration.activeAppLinks)
                        }}
                      </dd>
                    </div>
                  </dl>
                </div>
              </el-card>
            </section>

            <section
              v-if="canViewRuntime || canViewAccount"
              class="privileged-grid"
            >
              <el-card
                v-if="canViewRuntime"
                v-loading="optionalLoading"
                shadow="never"
                class="detail-card"
              >
                <template #header>
                  <div class="detail-heading detail-heading--status">
                    <div>
                      <h3>投递运行健康</h3>
                      <span>队列、重试和死信状态</span>
                    </div>
                    <el-tag
                      :type="
                        (runtime?.deadDeliveries || 0) > 0
                          ? 'danger'
                          : 'success'
                      "
                      effect="plain"
                      size="small"
                    >
                      {{
                        (runtime?.deadDeliveries || 0) > 0
                          ? '需要关注'
                          : '运行正常'
                      }}
                    </el-tag>
                  </div>
                </template>
                <div v-if="runtime" class="ops-metrics">
                  <div>
                    <span>待投递</span
                    ><strong>{{
                      formatInteger(runtime.pendingDeliveries)
                    }}</strong>
                  </div>
                  <div>
                    <span>重试</span
                    ><strong>{{
                      formatInteger(runtime.retryDeliveries)
                    }}</strong>
                  </div>
                  <div>
                    <span>死亡任务</span
                    ><strong>{{
                      formatInteger(runtime.deadDeliveries)
                    }}</strong>
                  </div>
                  <div>
                    <span>成功量</span
                    ><strong>{{
                      formatInteger(runtime.successfulDeliveries)
                    }}</strong>
                  </div>
                  <div>
                    <span>入口死信</span
                    ><strong>{{ formatInteger(runtime.ingressDlq) }}</strong>
                  </div>
                  <div>
                    <span>Stream 积压</span
                    ><strong>{{
                      formatInteger(runtime.feedbackStreamPending)
                    }}</strong>
                  </div>
                </div>
                <el-empty
                  v-else
                  :image-size="56"
                  :description="
                    runtimeError ? '运行概览加载失败' : '运行概览暂不可用'
                  "
                >
                  <el-button
                    v-if="runtimeError"
                    link
                    type="primary"
                    @click="loadOptionalData"
                    >重试</el-button
                  >
                </el-empty>
              </el-card>

              <el-card
                v-if="canViewAccount"
                v-loading="optionalLoading"
                shadow="never"
                class="detail-card"
              >
                <template #header>
                  <div class="detail-heading detail-heading--status">
                    <div>
                      <h3>账户与账务</h3>
                      <span>当前筛选范围 · CNY</span>
                    </div>
                    <el-tag effect="plain" size="small">CNY</el-tag>
                  </div>
                </template>
                <div v-if="account" class="account-metrics">
                  <div class="balance">
                    <span>当前范围余额</span
                    ><strong>{{ money(account.totalBalance) }}</strong>
                  </div>
                  <div>
                    <span>区间充值</span
                    ><strong>{{ money(account.rechargeAmount) }}</strong>
                  </div>
                  <div>
                    <span>CPC 消耗</span
                    ><strong>{{ money(account.cpcSpend) }}</strong>
                  </div>
                  <div>
                    <span>CPA 消耗</span
                    ><strong>{{ money(account.cpaSpend) }}</strong>
                  </div>
                  <div>
                    <span>CPC 拒绝</span
                    ><strong>{{
                      formatInteger(account.rejectedCharges)
                    }}</strong>
                  </div>
                  <div>
                    <span>CPA 拒绝</span
                    ><strong>{{
                      formatInteger(account.cpaRejectedCharges)
                    }}</strong>
                  </div>
                </div>
                <el-empty
                  v-else
                  :image-size="56"
                  :description="
                    accountError ? '账务概览加载失败' : '账务概览暂不可用'
                  "
                >
                  <el-button
                    v-if="accountError"
                    link
                    type="primary"
                    @click="loadOptionalData"
                    >重试</el-button
                  >
                </el-empty>
              </el-card>
            </section>
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-drawer
        v-model="filterDrawerVisible"
        :append-to-body="!isFullscreen"
        direction="rtl"
        size="420px"
        title="数据筛选"
      >
        <el-form class="filter-form" label-position="top">
          <el-form-item label="时间范围">
            <el-radio-group
              :model-value="draftPreset"
              size="small"
              @change="setDraftPreset($event as 7 | 30 | 90)"
            >
              <el-radio-button :value="7">7 天</el-radio-button>
              <el-radio-button :value="30">30 天</el-radio-button>
              <el-radio-button :value="90">90 天</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="自定义日期">
            <el-date-picker
              v-model="draftFilters.dates"
              :disabled-date="disabledDate"
              end-placeholder="结束日期"
              range-separator="至"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD"
              @change="draftPreset = 'CUSTOM'"
            />
          </el-form-item>
          <el-form-item label="渠道">
            <el-select
              v-model="draftFilters.channelId"
              clearable
              filterable
              placeholder="全部渠道"
            >
              <el-option
                v-for="item in options.channels"
                :key="item.id"
                :label="item.channelName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="广告主">
            <el-select
              v-model="draftFilters.advertiserId"
              clearable
              filterable
              placeholder="全部广告主"
            >
              <el-option
                v-for="item in draftCascaded.advertisers"
                :key="item.id"
                :label="item.advertiserName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="应用链接">
            <el-select
              v-model="draftFilters.appLinkId"
              clearable
              filterable
              placeholder="全部应用链接"
            >
              <el-option
                v-for="item in draftCascaded.appLinks"
                :key="item.id"
                :label="item.linkName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="监测链接">
            <el-select
              v-model="draftFilters.monitorId"
              clearable
              filterable
              placeholder="全部监测链接"
            >
              <el-option
                v-for="item in draftCascaded.monitors"
                :key="item.id"
                :label="item.monitorName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="drawer-footer">
            <el-button @click="resetDraftFilters">重置</el-button>
            <div>
              <el-button @click="filterDrawerVisible = false">取消</el-button>
              <el-button type="primary" @click="applyDraftFilters"
                >应用筛选</el-button
              >
            </div>
          </div>
        </template>
      </el-drawer>
    </main>
  </Page>
</template>

<style scoped>
.dashboard-page {
  min-width: 0;
  color: var(--el-text-color-primary);
}

.dashboard-page.fullscreen {
  height: 100vh;
  padding: 20px;
  overflow: auto;
  background: hsl(var(--background));
}

.action-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.range-actions,
.page-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.date-range-button {
  padding: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.date-range-button:hover,
.date-range-button:focus-visible {
  color: var(--el-color-primary);
}

.date-range-button:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 3px;
  border-radius: 3px;
}

.refresh-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-right: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.refresh-meta span::before {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  content: '';
  background: var(--el-color-success);
  border-radius: 50%;
}

.refresh-meta span.paused::before {
  background: var(--el-color-warning);
}

.refresh-meta small {
  color: var(--el-text-color-placeholder);
}

.active-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 16px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.active-filters > span {
  margin-right: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.error-alert {
  margin-bottom: 16px;
}

.error-alert :deep(.el-alert__content) {
  width: 100%;
}

.error-alert :deep(.el-alert__description) {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  min-width: 0;
  border-color: var(--el-border-color-lighter);
}

.metric-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 156px;
  padding: 24px 20px;
}

.metric-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: clamp(18px, 1.45vw, 25px);
  font-weight: 650;
  color: var(--el-text-color-secondary);
}

.metric-help {
  font-size: 14px;
  color: var(--el-text-color-placeholder);
  cursor: help;
}

.metric-value-row {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: baseline;
  margin-top: 24px;
}

.metric-value-row strong {
  margin-right: 2px;
  font-size: clamp(24px, 1.8vw, 32px);
  line-height: 1;
  color: var(--el-text-color-primary);
  letter-spacing: -0.025em;
}

.comparison-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-value-row b {
  font-size: 14px;
  font-weight: 650;
}

.positive {
  color: var(--el-color-success);
}

.negative {
  color: var(--el-color-danger);
}

.muted {
  color: var(--el-text-color-placeholder);
}

.trend-card {
  margin-top: 16px;
  border-color: var(--el-border-color-lighter);
}

.trend-card :deep(.el-card__header) {
  padding: 18px 20px 12px;
  border-bottom: 0;
}

.trend-card :deep(.el-card__body) {
  padding: 0 16px 14px;
}

.card-heading,
.detail-heading,
.detail-heading--status {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.card-heading h2,
.detail-heading h3 {
  margin: 0;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.card-heading h2 {
  font-size: 18px;
}

.card-heading p,
.detail-heading span {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chart-skeleton,
.chart-skeleton__item {
  width: 100%;
  height: 500px;
}

.more-collapse {
  margin-top: 16px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.more-collapse :deep(.el-collapse-item__header) {
  height: 66px;
  padding: 0 20px;
  border-bottom: 0;
}

.more-collapse :deep(.el-collapse-item__wrap) {
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 0;
}

.more-collapse :deep(.el-collapse-item__content) {
  padding: 0;
}

.more-title {
  display: flex;
  flex: 1;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  margin-right: 12px;
}

.more-title > div {
  display: flex;
  gap: 12px;
  align-items: baseline;
  min-width: 0;
}

.more-title strong {
  font-size: 15px;
}

.more-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.more-content {
  padding: 16px;
  background: var(--el-fill-color-extra-light);
}

.auxiliary-grid {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  background: var(--el-border-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.auxiliary-grid article {
  min-width: 0;
  padding: 14px 12px;
  background: var(--el-bg-color);
}

.auxiliary-grid span,
.ops-metrics span,
.account-metrics span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.auxiliary-grid strong,
.ops-metrics strong,
.account-metrics strong {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 17px;
  color: var(--el-text-color-primary);
}

.detail-grid,
.privileged-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.detail-card {
  min-width: 0;
  border-color: var(--el-border-color-lighter);
}

.detail-card :deep(.el-card__header) {
  padding: 16px 18px;
}

.detail-heading {
  align-items: flex-start;
}

.detail-heading h3 {
  font-size: 16px;
}

.detail-heading > span {
  text-align: right;
}

.retention-card {
  grid-column: span 2;
}

.configuration-body {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(120px, 0.6fr);
  align-items: center;
}

.configuration-body dl {
  margin: 0;
}

.configuration-body dl div {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.configuration-body dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.configuration-body dd {
  margin: 5px 0 0;
  font-size: 20px;
  font-weight: 650;
}

.ops-metrics,
.account-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  overflow: hidden;
  background: var(--el-border-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.ops-metrics div,
.account-metrics div {
  padding: 14px;
  background: var(--el-bg-color);
}

.account-metrics .balance {
  grid-column: span 3;
  background: var(--el-color-primary-light-9);
}

.account-metrics .balance strong {
  font-size: 24px;
  color: var(--el-color-primary);
}

.filter-form :deep(.el-select),
.filter-form :deep(.el-date-editor) {
  width: 100%;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 1399px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .auxiliary-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (max-width: 1023px) {
  .action-bar {
    align-items: flex-start;
  }

  .page-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .refresh-meta {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .auxiliary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .dashboard-page.fullscreen {
    padding: 12px;
  }

  .action-bar,
  .range-actions,
  .page-actions {
    flex-wrap: wrap;
    align-items: stretch;
    width: 100%;
  }

  .action-bar {
    flex-direction: column;
  }

  .date-range-button {
    flex: 1;
    text-align: right;
  }

  .page-actions {
    justify-content: flex-start;
  }

  .refresh-meta {
    align-items: flex-start;
  }

  .metric-title {
    font-size: 19px;
  }

  .detail-grid,
  .privileged-grid {
    grid-template-columns: 1fr;
  }

  .retention-card {
    grid-column: auto;
  }

  .more-title > div {
    display: block;
  }

  .more-title span {
    display: block;
    max-width: 52vw;
    margin-top: 2px;
  }

  .configuration-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 599px) {
  .metric-grid,
  .auxiliary-grid {
    grid-template-columns: 1fr;
  }

  .metric-card :deep(.el-card__body) {
    min-height: 132px;
    padding: 20px;
  }

  .metric-value-row {
    margin-top: 18px;
  }

  .chart-skeleton,
  .chart-skeleton__item {
    height: 420px;
  }

  .account-metrics,
  .ops-metrics {
    grid-template-columns: repeat(2, 1fr);
  }

  .account-metrics .balance {
    grid-column: span 2;
  }

  .more-content {
    padding: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-page *,
  .dashboard-page *::before,
  .dashboard-page *::after {
    scroll-behavior: auto !important;
    transition: none !important;
    animation: none !important;
  }
}
</style>
