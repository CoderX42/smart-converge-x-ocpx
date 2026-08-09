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
import { EchartsUI, useEcharts } from '@boot/plugins/echarts';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  getDashboardAccountSummary,
  getDashboardSummary,
  getReportOptions,
  getRuntimeOverview,
} from '#/api/adx';

import {
  buildRetentionHeatmap,
  cascadeOptions,
  formatComparison,
  formatInteger,
  formatRate,
  shouldPauseAutoRefresh,
} from './model';

defineOptions({ name: 'AdxDashboard' });

const REPORT_TIMEZONE = 'Asia/Shanghai';
const AUTO_REFRESH_MS = 60_000;
const { hasAccessByCodes } = useAccess();
const canViewRuntime = computed(() => hasAccessByCodes(['adx:runtime:read']));
const canViewAccount = computed(() => hasAccessByCodes(['adx:account:read']));

const pageRef = ref<HTMLElement>();
const loading = ref(false);
const refreshing = ref(false);
const errorMessage = ref('');
const isFullscreen = ref(false);
const autoRefreshPaused = ref(false);
const summary = ref<DashboardSummary>();
const account = ref<DashboardAccountSummary>();
const runtime = ref<RuntimeOverview>();
const options = ref<ReportOptions>({
  advertisers: [],
  appLinks: [],
  channels: [],
  monitors: [],
});
const preset = ref<7 | 30 | 90 | 'CUSTOM'>(30);
const filters = reactive({
  advertiserId: '',
  appLinkId: '',
  channelId: '',
  dates: [
    dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  monitorId: '',
});

const trafficRef = ref();
const conversionRef = ref();
const rateRef = ref();
const funnelRef = ref();
const retentionRef = ref();
const qualityRef = ref();
const configurationRef = ref();
const trafficChart = useEcharts(trafficRef);
const conversionChart = useEcharts(conversionRef);
const rateChart = useEcharts(rateRef);
const funnelChart = useEcharts(funnelRef);
const retentionChart = useEcharts(retentionRef);
const qualityChart = useEcharts(qualityRef);
const configurationChart = useEcharts(configurationRef);

let refreshTimer: ReturnType<typeof setInterval> | undefined;

const cascaded = computed(() => cascadeOptions(options.value, filters));
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
const coverageStatusClass = computed(() => {
  const status = summary.value?.dataCoverage.rateStatus;
  return status ? `is-${status.toLowerCase().replaceAll('_', '-')}` : '';
});
const coreCards = computed(() => {
  const highlights = summary.value?.highlights;
  return [
    {
      accent: 'cyan',
      key: 'clicks',
      label: '今日点击',
      metric: highlights?.todayClicks,
      note: '唯一 event_id 点击事实',
    },
    {
      accent: 'amber',
      key: 'activate',
      label: '今日激活',
      metric: highlights?.todayActivations,
      note: '上游 ACTIVATE 事件',
    },
    {
      accent: 'blue',
      key: 'retention',
      label: '昨日次留',
      metric: highlights?.yesterdayRetentionD2,
      note: '上游 D2 回传事件',
    },
    {
      accent: 'green',
      key: 'first',
      label: '今日首活',
      metric: highlights?.todayFirstActivations,
      note: 'FIRST_ACTIVATION',
    },
    {
      accent: 'red',
      key: 'payment',
      label: '今日付费',
      metric: highlights?.todayPayments,
      note: '付费事件次数，不含金额',
    },
  ];
});
const auxiliaryCards = computed(() => {
  const totals = summary.value?.totals;
  return [
    { label: '注册', value: formatInteger(totals?.registrations) },
    { label: '拉活', value: formatInteger(totals?.reactivations) },
    { label: '激活率', value: formatRate(totals?.activationRate) },
    { label: '首活率', value: formatRate(totals?.firstActivationRate) },
    { label: '拉活率', value: formatRate(totals?.reactivationRate) },
    { label: '展示量', value: formatInteger(totals?.impressions) },
    { label: 'CPC 消耗', value: money(totals?.costAmount) },
  ];
});
const lastUpdated = computed(() => {
  const value = summary.value?.dataUpdatedAt || summary.value?.generatedAt;
  return value ? dayjs(value).format('MM-DD HH:mm:ss') : '尚未更新';
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

function comparisonTone(metric?: DashboardComparisonMetric) {
  if (metric?.changeRate === undefined || metric.changeRate === null) {
    return metric && metric.value > 0 && metric.comparisonValue === 0
      ? 'positive'
      : 'muted';
  }
  if (metric.changeRate > 0) return 'positive';
  if (metric.changeRate < 0) return 'negative';
  return 'muted';
}

function setPreset(value: 7 | 30 | 90 | 'CUSTOM') {
  preset.value = value;
  if (value === 'CUSTOM') return;
  filters.dates = [
    dayjs()
      .subtract(value - 1, 'day')
      .format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ];
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

async function load(silent = false) {
  if (loading.value || refreshing.value) return;
  if (silent) refreshing.value = true;
  else loading.value = true;
  errorMessage.value = '';
  try {
    summary.value = await getDashboardSummary(query());
    const optionalRequests: Promise<void>[] = [];
    if (canViewRuntime.value) {
      optionalRequests.push(
        getRuntimeOverview()
          .then((value) => {
            runtime.value = value;
          })
          .catch(() => {
            runtime.value = undefined;
          }),
      );
    }
    if (canViewAccount.value) {
      optionalRequests.push(
        getDashboardAccountSummary(query())
          .then((value) => {
            account.value = value;
          })
          .catch(() => {
            account.value = undefined;
          }),
      );
    }
    await Promise.all(optionalRequests);
    await nextTick();
    await renderCharts();
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '仪表盘数据加载失败';
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function renderCharts() {
  const data = summary.value;
  if (!data || !hasData.value) return;
  const dates = data.trend.map((item) => item.reportDate.slice(5));
  const axis = {
    axisLabel: { color: '#8b9aaf' },
    axisLine: { lineStyle: { color: '#324156' } },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: 'rgba(130,150,175,.14)' } },
  };
  const tooltip = {
    axisPointer: { type: 'cross' as const },
    backgroundColor: 'rgba(12,24,39,.94)',
    borderColor: '#334155',
    textStyle: { color: '#f8fafc' },
    trigger: 'axis' as const,
  };
  const line = (
    name: string,
    values: Array<number | undefined>,
    color: string,
  ) => ({
    data: values.map((value) => value ?? null),
    emphasis: { focus: 'series' as const },
    name,
    showSymbol: false,
    smooth: 0.28,
    type: 'line' as const,
    lineStyle: { color, width: 2 },
    itemStyle: { color },
  });
  const grid = { bottom: 40, containLabel: true, left: 12, right: 20, top: 42 };
  const legend = { right: 6, textStyle: { color: '#8b9aaf' }, top: 4 };

  const heatmap = buildRetentionHeatmap(data);
  const heatMax = Math.max(...heatmap.data.map((item) => Number(item[2])), 1);
  await Promise.all([
    trafficChart.renderEcharts({
      grid,
      legend,
      series: [
        line(
          '全部点击',
          data.trend.map((item) => item.metrics.clicks),
          '#38bdf8',
        ),
        line(
          '有效点击',
          data.trend.map((item) => item.metrics.eligibleClicks),
          '#34d399',
        ),
        line(
          '重复点击',
          data.trend.map((item) => item.metrics.deduplicatedClicks),
          '#fb7185',
        ),
      ],
      tooltip,
      xAxis: { ...axis, boundaryGap: false, data: dates, type: 'category' },
      yAxis: { ...axis, type: 'value' },
    }),
    conversionChart.renderEcharts({
      grid,
      legend,
      series: [
        line(
          '激活',
          data.trend.map((item) => item.metrics.activations),
          '#f59e0b',
        ),
        line(
          '首活',
          data.trend.map((item) => item.metrics.firstActivations),
          '#22c55e',
        ),
        line(
          '拉活',
          data.trend.map((item) => item.metrics.reactivations),
          '#06b6d4',
        ),
        line(
          '注册',
          data.trend.map((item) => item.metrics.registrations),
          '#60a5fa',
        ),
        line(
          '付费',
          data.trend.map((item) => item.metrics.payments),
          '#fb7185',
        ),
      ],
      tooltip,
      xAxis: { ...axis, boundaryGap: false, data: dates, type: 'category' },
      yAxis: { ...axis, type: 'value' },
    }),
    rateChart.renderEcharts({
      grid,
      legend,
      series: [
        line(
          '激活率',
          data.trend.map((item) => item.metrics.activationRate),
          '#f59e0b',
        ),
        line(
          '首活率',
          data.trend.map((item) => item.metrics.firstActivationRate),
          '#22c55e',
        ),
        line(
          '拉活率',
          data.trend.map((item) => item.metrics.reactivationRate),
          '#06b6d4',
        ),
      ],
      tooltip: {
        ...tooltip,
        valueFormatter: (value: unknown) => `${value ?? '—'}%`,
      },
      xAxis: { ...axis, boundaryGap: false, data: dates, type: 'category' },
      yAxis: {
        ...axis,
        axisLabel: { color: '#8b9aaf', formatter: '{value}%' },
        type: 'value',
      },
    }),
    funnelChart.renderEcharts({
      grid: { bottom: 20, containLabel: true, left: 16, right: 30, top: 20 },
      series: [
        {
          barMaxWidth: 32,
          data: [...data.funnel].reverse().map((item, index) => ({
            itemStyle: {
              color: ['#fb7185', '#60a5fa', '#f59e0b', '#34d399'][index],
            },
            value: item.value,
          })),
          label: {
            color: '#cbd5e1',
            formatter: '{c}',
            position: 'right',
            show: true,
          },
          type: 'bar',
        },
      ],
      tooltip,
      xAxis: { ...axis, type: 'value' },
      yAxis: {
        ...axis,
        data: [...data.funnel].reverse().map((item) => item.label),
        type: 'category',
      },
    }),
    retentionChart.renderEcharts({
      grid: { bottom: 55, containLabel: true, left: 20, right: 28, top: 18 },
      series: [
        {
          data: heatmap.data,
          emphasis: { itemStyle: { borderColor: '#f8fafc', borderWidth: 1 } },
          label: { color: '#e2e8f0', formatter: '{@[2]}', show: true },
          type: 'heatmap',
        },
      ],
      tooltip: {
        ...tooltip,
        formatter: (params: unknown) => {
          const item = params as { value: [number, number, number] };
          const [dateIndex, dayIndex, value] = item.value;
          return `${heatmap.dates[dateIndex] || '—'}<br/>${heatmap.days[dayIndex] || '—'}：<strong>${formatInteger(value)}</strong>`;
        },
        trigger: 'item',
      },
      visualMap: {
        calculable: true,
        inRange: { color: ['#13293d', '#0f766e', '#f59e0b'] },
        max: heatMax,
        min: 0,
        orient: 'horizontal',
        textStyle: { color: '#8b9aaf' },
        bottom: 0,
        left: 'center',
      },
      xAxis: {
        ...axis,
        data: heatmap.dates.map((date) => date.slice(5)),
        splitArea: { show: true },
        type: 'category',
      },
      yAxis: {
        ...axis,
        data: heatmap.days,
        splitArea: { show: true },
        type: 'category',
      },
    }),
    qualityChart.renderEcharts({
      grid: { bottom: 24, containLabel: true, left: 20, right: 20, top: 40 },
      legend,
      series: [
        {
          data: [data.totals.rawConversions],
          itemStyle: { color: '#38bdf8' },
          name: '原始',
          type: 'bar',
        },
        {
          data: [data.totals.forwardedConversions],
          itemStyle: { color: '#34d399' },
          name: '成功回传',
          type: 'bar',
        },
        {
          data: [data.totals.deductedConversions],
          itemStyle: { color: '#f59e0b' },
          name: '扣除',
          type: 'bar',
        },
        {
          data: [data.totals.deliveryFailures],
          itemStyle: { color: '#fb7185' },
          name: '死亡失败',
          type: 'bar',
        },
      ],
      tooltip,
      xAxis: { ...axis, data: ['当前区间'], type: 'category' },
      yAxis: { ...axis, type: 'value' },
    }),
    configurationChart.renderEcharts({
      legend: { bottom: 0, textStyle: { color: '#8b9aaf' } },
      series: [
        {
          center: ['50%', '44%'],
          data: [
            { name: '已发布', value: data.configuration.publishedRoutes },
            { name: '草稿', value: data.configuration.draftRoutes },
            { name: '异常', value: data.configuration.abnormalRoutes },
          ],
          itemStyle: { borderColor: '#101c2c', borderWidth: 3 },
          label: { color: '#cbd5e1', formatter: '{b} {c}' },
          radius: ['46%', '70%'],
          type: 'pie',
        },
      ],
      tooltip: { ...tooltip, trigger: 'item' },
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

function handleFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === pageRef.value;
  nextTick(() => {
    trafficChart.resize();
    conversionChart.resize();
    rateChart.resize();
    funnelChart.resize();
    retentionChart.resize();
    qualityChart.resize();
    configurationChart.resize();
  });
}

function handleVisibilityChange() {
  autoRefreshPaused.value = shouldPauseAutoRefresh(document.hidden);
  if (!document.hidden) void load(true);
}

watch(
  () => filters.advertiserId,
  () => {
    if (
      filters.appLinkId &&
      !cascaded.value.appLinks.some((item) => item.id === filters.appLinkId)
    ) {
      filters.appLinkId = '';
    }
    if (
      filters.monitorId &&
      !cascaded.value.monitors.some((item) => item.id === filters.monitorId)
    ) {
      filters.monitorId = '';
    }
  },
);

watch(
  () => filters.channelId,
  () => {
    if (
      filters.advertiserId &&
      !cascaded.value.advertisers.some(
        (item) => item.id === filters.advertiserId,
      )
    ) {
      filters.advertiserId = '';
    }
    if (
      filters.appLinkId &&
      !cascaded.value.appLinks.some((item) => item.id === filters.appLinkId)
    ) {
      filters.appLinkId = '';
    }
    if (
      filters.monitorId &&
      !cascaded.value.monitors.some((item) => item.id === filters.monitorId)
    ) {
      filters.monitorId = '';
    }
  },
);

watch(
  () => filters.appLinkId,
  () => {
    if (
      filters.monitorId &&
      !cascaded.value.monitors.some((item) => item.id === filters.monitorId)
    ) {
      filters.monitorId = '';
    }
  },
);

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
  <main
    ref="pageRef"
    class="dashboard-page"
    :class="{ fullscreen: isFullscreen }"
  >
    <header class="command-bar">
      <div class="brand-block">
        <div class="live-mark"><i></i> ADX EFFECT</div>
        <h1>效果仪表盘</h1>
        <p>广告流量、转化、留存与投递质量的统一观测面</p>
      </div>
      <div class="status-block">
        <span class="refresh-status" :class="[{ paused: autoRefreshPaused }]">
          {{ autoRefreshPaused ? '页面隐藏 · 自动刷新暂停' : '60 秒自动刷新' }}
        </span>
        <span>数据更新 {{ lastUpdated }}</span>
        <span>{{ summary?.timezone || REPORT_TIMEZONE }}</span>
      </div>
    </header>

    <section class="filter-shell" aria-label="仪表盘筛选">
      <div class="filter-row">
        <el-select
          v-model="filters.channelId"
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
        <el-select
          v-model="filters.advertiserId"
          clearable
          filterable
          placeholder="全部广告主"
        >
          <el-option
            v-for="item in cascaded.advertisers"
            :key="item.id"
            :label="item.advertiserName"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.appLinkId"
          clearable
          filterable
          placeholder="全部应用链接"
        >
          <el-option
            v-for="item in cascaded.appLinks"
            :key="item.id"
            :label="item.linkName"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.monitorId"
          clearable
          filterable
          placeholder="全部监测链接"
        >
          <el-option
            v-for="item in cascaded.monitors"
            :key="item.id"
            :label="item.monitorName"
            :value="item.id"
          />
        </el-select>
        <el-radio-group
          :model-value="preset"
          size="default"
          @change="setPreset"
        >
          <el-radio-button :value="7">7天</el-radio-button>
          <el-radio-button :value="30">30天</el-radio-button>
          <el-radio-button :value="90">90天</el-radio-button>
          <el-radio-button value="CUSTOM">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="filters.dates"
          :disabled-date="disabledDate"
          end-placeholder="结束日期"
          range-separator="—"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="preset = 'CUSTOM'"
        />
        <el-button
          type="primary"
          :loading="loading || refreshing"
          @click="load(false)"
          >应用筛选</el-button
        >
        <el-button :loading="refreshing" @click="load(true)">刷新</el-button>
        <el-button @click="toggleFullscreen">{{
          isFullscreen ? '退出全屏' : '全屏'
        }}</el-button>
      </div>
    </section>

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
        <el-button link type="danger" @click="load(false)">局部重试</el-button>
      </template>
    </el-alert>

    <section v-if="loading && !summary" class="core-grid" aria-label="加载中">
      <el-skeleton
        v-for="index in 5"
        :key="index"
        animated
        class="metric-skeleton"
      >
        <template #template
          ><el-skeleton-item variant="rect" class="skeleton-box"
        /></template>
      </el-skeleton>
    </section>
    <section v-else class="core-grid" aria-label="今日核心指标">
      <article
        v-for="card in coreCards"
        :key="card.key"
        class="core-card"
        :class="[card.accent]"
      >
        <div class="card-topline">
          <span>{{ card.label }}</span
          ><i></i>
        </div>
        <strong>{{ metricValue(card.metric) }}</strong>
        <div class="comparison-row">
          <span :class="comparisonTone(card.metric)">{{
            formatComparison(
              card.metric || {
                value: 0,
                comparisonValue: 0,
                comparisonLabel: '',
              },
            )
          }}</span>
          <small>{{ card.metric?.comparisonLabel || '较昨日' }}</small>
        </div>
        <p>{{ card.note }}</p>
      </article>
    </section>

    <section class="auxiliary-strip" aria-label="区间辅助指标">
      <article v-for="item in auxiliaryCards" :key="item.label">
        <span>{{ item.label }}</span
        ><strong>{{ item.value }}</strong>
      </article>
      <div class="coverage-pill" :class="coverageStatusClass">
        <i></i>{{ coverageMessage }}
      </div>
    </section>

    <section v-if="!loading && summary && !hasData" class="empty-state">
      <div class="empty-radar" aria-hidden="true"><i></i><i></i><i></i></div>
      <h2>当前筛选范围暂无效果事实</h2>
      <p>可以扩大日期范围或清除渠道、广告主、应用及监测链接筛选。</p>
      <el-button type="primary" @click="setPreset(30)"
        >查看最近 30 天</el-button
      >
    </section>

    <section v-if="hasData" class="chart-grid">
      <article class="chart-card span-2">
        <div class="panel-title">
          <div>
            <span>01 / TRAFFIC</span>
            <h2>流量趋势</h2>
          </div>
          <p>全部、有效与设备窗重复点击</p>
        </div>
        <EchartsUI ref="trafficRef" height="320px" />
      </article>
      <article class="chart-card span-2">
        <div class="panel-title">
          <div>
            <span>02 / CONVERSION</span>
            <h2>转化趋势</h2>
          </div>
          <p>上游 actionTime 自然日</p>
        </div>
        <EchartsUI ref="conversionRef" height="320px" />
      </article>
      <article class="chart-card span-2">
        <div class="panel-title">
          <div>
            <span>03 / RATE</span>
            <h2>转化率趋势</h2>
          </div>
          <p>分母：设备窗有效点击</p>
        </div>
        <EchartsUI ref="rateRef" height="300px" />
      </article>
      <article class="chart-card">
        <div class="panel-title">
          <div>
            <span>04 / FUNNEL</span>
            <h2>转化漏斗</h2>
          </div>
        </div>
        <EchartsUI ref="funnelRef" height="300px" />
      </article>
      <article class="chart-card span-3 retention-card">
        <div class="panel-title">
          <div>
            <span>05 / RETENTION</span>
            <h2>D2–D7 留存矩阵</h2>
          </div>
          <p>上游明确回传事件口径 · 数值为事件次数</p>
        </div>
        <EchartsUI ref="retentionRef" height="340px" />
      </article>
      <article class="chart-card span-2">
        <div class="panel-title">
          <div>
            <span>06 / DELIVERY</span>
            <h2>回传质量</h2>
          </div>
          <p>原始、成功、扣除与死亡失败</p>
        </div>
        <EchartsUI ref="qualityRef" height="290px" />
      </article>
      <article class="chart-card span-2 configuration-panel">
        <div class="panel-title">
          <div>
            <span>07 / COVERAGE</span>
            <h2>配置覆盖</h2>
          </div>
        </div>
        <div class="configuration-body">
          <EchartsUI ref="configurationRef" height="250px" />
          <dl>
            <div>
              <dt>启用渠道</dt>
              <dd>
                {{ formatInteger(summary?.configuration.activeChannels) }}
              </dd>
            </div>
            <div>
              <dt>启用广告主</dt>
              <dd>
                {{ formatInteger(summary?.configuration.activeAdvertisers) }}
              </dd>
            </div>
            <div>
              <dt>应用链接</dt>
              <dd>
                {{ formatInteger(summary?.configuration.activeAppLinks) }}
              </dd>
            </div>
          </dl>
        </div>
      </article>
    </section>

    <section v-if="canViewRuntime || canViewAccount" class="privileged-grid">
      <article v-if="canViewRuntime" class="ops-panel">
        <div class="panel-title">
          <div>
            <span>OPS / RUNTIME</span>
            <h2>投递运行健康</h2>
          </div>
          <b :class="{ danger: (runtime?.deadDeliveries || 0) > 0 }">{{
            (runtime?.deadDeliveries || 0) > 0 ? '需要关注' : '运行正常'
          }}</b>
        </div>
        <div v-if="runtime" class="ops-metrics">
          <div>
            <span>待投递</span
            ><strong>{{ formatInteger(runtime.pendingDeliveries) }}</strong>
          </div>
          <div>
            <span>重试</span
            ><strong>{{ formatInteger(runtime.retryDeliveries) }}</strong>
          </div>
          <div>
            <span>死亡任务</span
            ><strong>{{ formatInteger(runtime.deadDeliveries) }}</strong>
          </div>
          <div>
            <span>成功量</span
            ><strong>{{ formatInteger(runtime.successfulDeliveries) }}</strong>
          </div>
          <div>
            <span>入口死信</span
            ><strong>{{ formatInteger(runtime.ingressDlq) }}</strong>
          </div>
          <div>
            <span>Stream 积压</span
            ><strong>{{ formatInteger(runtime.feedbackStreamPending) }}</strong>
          </div>
        </div>
        <el-empty v-else :image-size="56" description="运行概览暂不可用" />
      </article>
      <article v-if="canViewAccount" class="ops-panel account-panel">
        <div class="panel-title">
          <div>
            <span>FIN / ACCOUNT</span>
            <h2>账户与账务</h2>
          </div>
          <b>CNY</b>
        </div>
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
            <span>CPC 消耗</span><strong>{{ money(account.cpcSpend) }}</strong>
          </div>
          <div>
            <span>拒绝扣费</span
            ><strong>{{ formatInteger(account.rejectedCharges) }}</strong>
          </div>
        </div>
        <el-empty v-else :image-size="56" description="账务概览暂不可用" />
      </article>
    </section>

    <footer>
      SCX ADX · 数据均按 {{ summary?.timezone || REPORT_TIMEZONE }} 自然日汇总 ·
      付费仅表示事件次数
    </footer>
  </main>
</template>

<style scoped>
.dashboard-page {
  --dash-bg: #07111e;
  --dash-panel: #0d1a2a;
  --dash-panel-2: #101f31;
  --dash-border: rgb(125 155 190 / 18%);
  --dash-text: #eef5ff;
  --dash-muted: #8291a7;

  min-height: 100%;
  padding: 24px;
  overflow: auto;
  color: var(--dash-text);
  background:
    linear-gradient(rgb(255 255 255 / 1.5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 1.5%) 1px, transparent 1px),
    radial-gradient(circle at 84% -10%, rgb(14 165 233 / 15%), transparent 32%),
    var(--dash-bg);
  background-size:
    32px 32px,
    32px 32px,
    auto,
    auto;
}

.dashboard-page.fullscreen {
  height: 100vh;
}

.command-bar {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.brand-block h1 {
  margin: 7px 0 3px;
  font-family: 'Arial Narrow', 'PingFang SC', sans-serif;
  font-size: clamp(27px, 3vw, 38px);
  font-weight: 720;
  letter-spacing: -0.04em;
}

.brand-block p,
.status-block {
  font-size: 12px;
  color: var(--dash-muted);
}

.brand-block p {
  margin: 0;
}

.live-mark {
  display: flex;
  gap: 8px;
  align-items: center;
  font: 700 11px/1 monospace;
  color: #38bdf8;
  letter-spacing: 0.18em;
}

.live-mark i,
.coverage-pill i {
  width: 6px;
  height: 6px;
  background: #34d399;
  border-radius: 50%;
  box-shadow: 0 0 12px #34d399;
}

.status-block {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  justify-content: flex-end;
}

.refresh-status {
  color: #86efac;
}

.refresh-status.paused {
  color: #fbbf24;
}

.filter-shell {
  position: sticky;
  top: 0;
  z-index: 5;
  padding: 12px;
  margin-bottom: 16px;
  background: rgb(10 23 38 / 92%);
  border: 1px solid var(--dash-border);
  border-radius: 12px;
  backdrop-filter: blur(18px);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-row :deep(.el-select) {
  width: 150px;
}

.filter-row :deep(.el-date-editor) {
  width: 250px;
}

.filter-row :deep(.el-input__wrapper),
.filter-row :deep(.el-select__wrapper) {
  background: #0b1726;
  box-shadow: 0 0 0 1px var(--dash-border) inset;
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

.core-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.core-card {
  position: relative;
  min-height: 170px;
  padding: 18px;
  overflow: hidden;
  background: linear-gradient(145deg, rgb(18 35 55 / 96%), rgb(10 24 39 / 96%));
  border: 1px solid var(--dash-border);
  border-radius: 10px;
}

.core-card::after {
  position: absolute;
  right: -28px;
  bottom: -42px;
  width: 110px;
  height: 110px;
  content: '';
  border: 1px solid rgb(56 189 248 / 15%);
  border-radius: 50%;
}

.card-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #a9b7c9;
}

.card-topline i {
  width: 28px;
  height: 2px;
  background: #38bdf8;
}

.core-card.amber .card-topline i {
  background: #f59e0b;
}

.core-card.blue .card-topline i {
  background: #60a5fa;
}

.core-card.green .card-topline i {
  background: #34d399;
}

.core-card.red .card-topline i {
  background: #fb7185;
}

.core-card strong {
  display: block;
  margin: 18px 0 9px;
  font:
    720 clamp(26px, 3vw, 38px)/1 'DIN Alternate',
    'Arial Narrow',
    sans-serif;
  letter-spacing: -0.04em;
}

.comparison-row {
  display: flex;
  gap: 7px;
  align-items: baseline;
}

.comparison-row span {
  font: 700 13px/1 monospace;
}

.comparison-row small {
  color: var(--dash-muted);
}

.comparison-row .positive {
  color: #34d399;
}

.comparison-row .negative {
  color: #fb7185;
}

.comparison-row .muted {
  color: #8b9aaf;
}

.core-card p {
  margin: 15px 0 0;
  font-size: 11px;
  color: #6f8096;
}

.metric-skeleton,
.skeleton-box {
  width: 100%;
  height: 170px;
}

.auxiliary-strip {
  display: grid;
  grid-template-columns: repeat(7, minmax(90px, 1fr)) 2fr;
  margin: 12px 0 18px;
  background: rgb(9 22 36 / 75%);
  border: 1px solid var(--dash-border);
  border-radius: 10px;
}

.auxiliary-strip article {
  padding: 13px 15px;
  border-right: 1px solid var(--dash-border);
}

.auxiliary-strip span {
  display: block;
  font-size: 11px;
  color: var(--dash-muted);
}

.auxiliary-strip strong {
  display: block;
  margin-top: 5px;
  font: 700 16px/1.2 monospace;
  color: #dce8f7;
}

.coverage-pill {
  display: flex;
  gap: 9px;
  align-items: center;
  justify-content: center;
  padding: 12px;
  font-size: 11px;
  color: #86efac;
  text-align: center;
}

.coverage-pill.is-incomplete-history,
.coverage-pill.is-no-eligible-click {
  color: #fbbf24;
}

.coverage-pill.is-incomplete-history i,
.coverage-pill.is-no-eligible-click i {
  background: #f59e0b;
  box-shadow: 0 0 12px #f59e0b;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.chart-card,
.ops-panel {
  min-width: 0;
  padding: 17px;
  background: linear-gradient(150deg, rgb(14 29 47 / 96%), rgb(9 21 35 / 96%));
  border: 1px solid var(--dash-border);
  border-radius: 10px;
}

.span-2 {
  grid-column: span 2;
}

.span-3 {
  grid-column: span 3;
}

.panel-title {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.panel-title span {
  font: 700 9px/1 monospace;
  color: #38bdf8;
  letter-spacing: 0.13em;
}

.panel-title h2 {
  margin: 5px 0 0;
  font-size: 16px;
  font-weight: 650;
}

.panel-title p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--dash-muted);
  text-align: right;
}

.panel-title b {
  padding: 5px 8px;
  font: 600 10px/1 monospace;
  color: #86efac;
  background: rgb(52 211 153 / 10%);
  border: 1px solid rgb(52 211 153 / 25%);
  border-radius: 4px;
}

.panel-title b.danger {
  color: #fda4af;
  background: rgb(251 113 133 / 10%);
  border-color: rgb(251 113 133 / 25%);
}

.configuration-body {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(110px, 0.6fr);
  align-items: center;
}

.configuration-body dl {
  margin: 0;
}

.configuration-body dl div {
  padding: 10px 0;
  border-bottom: 1px solid var(--dash-border);
}

.configuration-body dt {
  font-size: 11px;
  color: var(--dash-muted);
}

.configuration-body dd {
  margin: 5px 0 0;
  font: 700 20px/1 monospace;
}

.privileged-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.ops-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin-top: 20px;
  overflow: hidden;
  background: var(--dash-border);
  border: 1px solid var(--dash-border);
  border-radius: 6px;
}

.ops-metrics div,
.account-metrics div {
  padding: 14px;
  background: #0b1828;
}

.ops-metrics span,
.account-metrics span {
  display: block;
  font-size: 11px;
  color: var(--dash-muted);
}

.ops-metrics strong,
.account-metrics strong {
  display: block;
  margin-top: 7px;
  font: 700 20px/1 monospace;
}

.account-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin-top: 20px;
  overflow: hidden;
  background: var(--dash-border);
  border: 1px solid var(--dash-border);
  border-radius: 6px;
}

.account-metrics .balance {
  grid-column: span 3;
  background: linear-gradient(90deg, rgb(14 116 144 / 22%), #0b1828);
}

.account-metrics .balance strong {
  font-size: 26px;
  color: #67e8f9;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  background: var(--dash-panel);
  border: 1px dashed rgb(125 155 190 / 28%);
  border-radius: 10px;
}

.empty-state h2 {
  margin: 18px 0 6px;
  font-size: 18px;
}

.empty-state p {
  margin: 0 0 18px;
  color: var(--dash-muted);
}

.empty-radar {
  position: relative;
  width: 86px;
  height: 86px;
  border: 1px solid rgb(56 189 248 / 30%);
  border-radius: 50%;
}

.empty-radar i {
  position: absolute;
  inset: 14px;
  border: 1px solid rgb(56 189 248 / 22%);
  border-radius: 50%;
}

.empty-radar i:nth-child(2) {
  inset: 28px;
}

.empty-radar i:nth-child(3) {
  inset: 41px;
  background: #38bdf8;
  box-shadow: 0 0 18px #38bdf8;
}

footer {
  padding: 22px 0 4px;
  font: 10px/1.5 monospace;
  color: #526278;
  text-align: center;
  letter-spacing: 0.05em;
}

@media (max-width: 1280px) {
  .core-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .auxiliary-strip {
    grid-template-columns: repeat(4, 1fr);
  }

  .coverage-pill {
    min-height: 55px;
  }

  .retention-card {
    grid-column: span 2;
  }
}

@media (max-width: 900px) {
  .dashboard-page {
    padding: 14px;
  }

  .command-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .status-block {
    justify-content: flex-start;
  }

  .core-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }

  .span-2,
  .span-3,
  .retention-card {
    grid-column: auto;
  }

  .privileged-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .core-grid,
  .auxiliary-strip {
    grid-template-columns: 1fr;
  }

  .auxiliary-strip article {
    border-right: 0;
    border-bottom: 1px solid var(--dash-border);
  }

  .filter-row :deep(.el-select),
  .filter-row :deep(.el-date-editor) {
    width: 100%;
  }

  .configuration-body {
    grid-template-columns: 1fr;
  }

  .ops-metrics {
    grid-template-columns: repeat(2, 1fr);
  }

  .account-metrics {
    grid-template-columns: 1fr;
  }

  .account-metrics .balance {
    grid-column: auto;
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
