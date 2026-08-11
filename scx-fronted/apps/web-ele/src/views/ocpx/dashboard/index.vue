<script lang="ts" setup>
import type {
  DashboardMetricCard,
  DashboardOverview,
  DashboardRanking,
  DashboardSection,
  UnifiedReportMetricCode,
  UnifiedReportOptions,
} from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';
import { EchartsUI, useEcharts } from '@boot/plugins/echarts';

import { Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import { getDashboardOptions, getDashboardSummary } from '#/api/ocpx';
import { shanghaiDate } from '#/utils/report-date';

import {
  buildDashboardTrendSeries,
  comparisonTone,
  dashboardEmptyKind,
  dashboardQuery,
  formatComparison,
  formatDashboardMetric,
  isDashboardDateRangeValid,
  sectionFailureText,
} from './model';

defineOptions({ name: 'OcpxDashboard' });

type RankingSection = {
  accent: string;
  section?: DashboardSection<DashboardRanking>;
  title: string;
};

const loading = ref(false);
const refreshing = ref(false);
const optionsLoading = ref(false);
const firstRequestDone = ref(false);
const filterTouched = ref(false);
const forbidden = ref(false);
const errorMessage = ref('');
const traceId = ref('');
const optionsError = ref('');
const summary = ref<DashboardOverview>();
const trendRef = ref();
const trendChart = useEcharts(trendRef);
let requestSequence = 0;

const options = reactive<UnifiedReportOptions>({
  advertisers: [],
  appLinks: [],
  channels: [],
  mediaAccounts: [],
  products: [],
  tasks: [],
});

function defaultFilters() {
  return {
    advertiserId: '',
    appLinkId: '',
    channelId: '',
    dates: [shanghaiDate(-6), shanghaiDate()] as [string, string],
    mediaAccountId: '',
    productId: '',
    taskId: '',
  };
}

const filters = reactive(defaultFilters());

const cards = computed(() => summary.value?.cards.data || []);
const trendPoints = computed(() => summary.value?.trend.data || []);
const emptyKind = computed(() =>
  dashboardEmptyKind({
    filterTouched: filterTouched.value,
    firstRequestDone: firstRequestDone.value,
    loading: loading.value,
    summary: summary.value,
  }),
);
const rankings = computed<RankingSection[]>(() => [
  {
    accent: '#2563eb',
    section: summary.value?.productRanking,
    title: '产品有效点击排行',
  },
  {
    accent: '#0f9f8f',
    section: summary.value?.taskRanking,
    title: '任务有效点击排行',
  },
  {
    accent: '#d97706',
    section: summary.value?.mediaRanking,
    title: '媒体账号有效点击排行',
  },
]);

function errorDetails(error: any) {
  const status = Number(error?.response?.status || error?.status || 0);
  forbidden.value = status === 403;
  errorMessage.value =
    error?.response?.data?.msg ||
    error?.data?.msg ||
    error?.message ||
    '经营总览加载失败';
  traceId.value =
    error?.response?.headers?.['x-trace-id'] ||
    error?.response?.data?.traceId ||
    '';
}

async function loadOptions() {
  optionsLoading.value = true;
  optionsError.value = '';
  try {
    Object.assign(options, await getDashboardOptions());
  } catch (error: any) {
    optionsError.value =
      error?.response?.data?.msg || error?.message || '筛选候选加载失败';
  } finally {
    optionsLoading.value = false;
  }
}

async function load(silent = false) {
  const sequence = ++requestSequence;
  if (silent) refreshing.value = true;
  else loading.value = true;
  errorMessage.value = '';
  traceId.value = '';
  forbidden.value = false;
  try {
    const value = await getDashboardSummary(dashboardQuery(filters));
    if (sequence !== requestSequence) return;
    summary.value = value;
    await nextTick();
    await renderTrend();
  } catch (error) {
    if (sequence !== requestSequence) return;
    errorDetails(error);
  } finally {
    if (sequence === requestSequence) {
      loading.value = false;
      refreshing.value = false;
      firstRequestDone.value = true;
    }
  }
}

function search() {
  if (!isDashboardDateRangeValid(filters.dates)) {
    ElMessage.warning('日期范围必须按顺序选择，且最多包含 90 个自然日');
    return;
  }
  filterTouched.value = true;
  void load();
}

function reset() {
  Object.assign(filters, defaultFilters());
  filterTouched.value = false;
  void load();
}

function refresh() {
  void load(true);
}

function disabledDate(date: Date) {
  return date.getTime() > Date.now();
}

function metricValue(card: DashboardMetricCard) {
  return formatDashboardMetric(card.metric, card.current);
}

function comparisonValue(card: DashboardMetricCard) {
  return formatDashboardMetric(card.metric, card.comparison);
}

function rankingMetric(
  row: DashboardRanking['records'][number],
  metric: UnifiedReportMetricCode,
) {
  return formatDashboardMetric(metric, row.metrics[metric]);
}

function rankingPercent(
  section: DashboardSection<DashboardRanking>,
  index: number,
) {
  const records = section.data?.records || [];
  const maximum = Math.max(
    ...records.map((item) => Number(item.metrics.validClicks?.value || 0)),
    1,
  );
  return Math.max(
    2,
    (Number(records[index]?.metrics.validClicks?.value || 0) / maximum) * 100,
  );
}

function freshnessType(value?: string) {
  if (value === 'FINAL') return 'success';
  if (value === 'DELAYED') return 'warning';
  return 'info';
}

function freshnessLabel(value?: string) {
  if (value === 'FINAL') return '数据已更新';
  if (value === 'DELAYED') return '数据延迟';
  return '数据汇总中';
}

function metricStatusLabel(value?: string) {
  if (value === 'AVAILABLE') return '数据可用';
  if (value === 'ZERO_DENOMINATOR') return '暂无可计算数据';
  return '暂无数据';
}

function compactNumber(value: unknown) {
  const number = Number(value || 0);
  if (Math.abs(number) >= 100_000_000)
    return `${(number / 100_000_000).toFixed(1)}亿`;
  if (Math.abs(number) >= 10_000) return `${(number / 10_000).toFixed(1)}万`;
  return number.toLocaleString('zh-CN');
}

function trendTooltip(value: unknown) {
  const params = (Array.isArray(value) ? value : [value]) as Array<{
    axisValueLabel?: string;
    marker?: string;
    seriesName?: string;
    value?: null | number;
  }>;
  const bucket = params[0]?.axisValueLabel || '—';
  const series = buildDashboardTrendSeries(summary.value as DashboardOverview);
  const rows = series.map((definition) => {
    const item = params.find(
      (candidate) => candidate.seriesName === definition.label,
    );
    const metric = definition.key;
    const formatted = formatDashboardMetric(metric, {
      status: item?.value === null ? 'UNAVAILABLE' : 'AVAILABLE',
      value: item?.value ?? undefined,
    });
    return `<div style="display:flex;min-width:190px;justify-content:space-between;gap:20px;line-height:24px"><span>${item?.marker || ''}${definition.label}</span><strong>${formatted}</strong></div>`;
  });
  return `<div style="margin-bottom:6px;font-weight:600">${bucket}</div>${rows.join('')}`;
}

async function renderTrend() {
  const data = summary.value;
  if (!data || data.trend.status !== 'READY' || trendPoints.value.length === 0)
    return;
  const series = buildDashboardTrendSeries(data);
  await trendChart.renderEcharts({
    animationDuration: 360,
    color: series.map((item) => item.color),
    grid: { bottom: 52, containLabel: true, left: 12, right: 20, top: 34 },
    legend: {
      bottom: 0,
      data: series.map((item) => item.label),
      type: 'scroll',
    },
    series: series.map((item) => ({
      connectNulls: false,
      data: item.data,
      emphasis: { focus: 'series' },
      lineStyle: { color: item.color, width: item.rate ? 1.8 : 2.2 },
      name: item.label,
      showSymbol: false,
      smooth: 0.25,
      type: 'line',
      yAxisIndex: item.yAxisIndex,
    })),
    tooltip: {
      confine: true,
      formatter: trendTooltip,
      trigger: 'axis',
    },
    xAxis: {
      axisTick: { show: false },
      boundaryGap: false,
      data: trendPoints.value.map((item) => item.bucket),
      type: 'category',
    },
    yAxis: [
      {
        axisLabel: { formatter: compactNumber },
        name: '数量',
        type: 'value',
      },
      {
        axisLabel: { formatter: '{value}%' },
        max: 100,
        min: 0,
        name: '比率',
        splitLine: { show: false },
        type: 'value',
      },
    ],
  });
}

onMounted(() => Promise.all([loadOptions(), load()]));
</script>

<template>
  <Page auto-content-height>
    <div class="dashboard-page">
      <section class="dashboard-hero">
        <div>
          <h1>经营总览</h1>
          <p class="dashboard-intro">
            汇总展示点击、转化趋势与对象排行，数据按当前账号可见范围统计。
          </p>
        </div>
        <div class="hero-actions">
          <el-tag
            v-if="summary"
            effect="plain"
            :type="freshnessType(summary.freshnessStatus)"
          >
            {{ freshnessLabel(summary.freshnessStatus) }}
          </el-tag>
          <el-button
            aria-label="刷新经营总览"
            :icon="Refresh"
            :loading="refreshing"
            @click="refresh"
          >
            刷新
          </el-button>
        </div>
      </section>

      <el-card class="filter-card" shadow="never">
        <el-form label-position="top" @submit.prevent="search">
          <div class="filter-grid">
            <el-form-item label="日期范围 · Asia/Shanghai">
              <el-date-picker
                v-model="filters.dates"
                :disabled-date="disabledDate"
                end-placeholder="结束日期"
                range-separator="至"
                start-placeholder="开始日期"
                type="daterange"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
            <el-form-item label="广告主">
              <el-select
                v-model="filters.advertiserId"
                clearable
                filterable
                :loading="optionsLoading"
              >
                <el-option
                  v-for="item in options.advertisers"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="产品">
              <el-select v-model="filters.productId" clearable filterable>
                <el-option
                  v-for="item in options.products"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="AppLink">
              <el-select v-model="filters.appLinkId" clearable filterable>
                <el-option
                  v-for="item in options.appLinks"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Task">
              <el-select v-model="filters.taskId" clearable filterable>
                <el-option
                  v-for="item in options.tasks"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="渠道">
              <el-select v-model="filters.channelId" clearable filterable>
                <el-option
                  v-for="item in options.channels"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="媒体账号">
              <el-select v-model="filters.mediaAccountId" clearable filterable>
                <el-option
                  v-for="item in options.mediaAccounts"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item class="filter-actions" label=" ">
              <el-button
                :icon="Search"
                :loading="loading"
                type="primary"
                @click="search"
              >
                查询
              </el-button>
              <el-button @click="reset">重置</el-button>
            </el-form-item>
          </div>
        </el-form>
        <p v-if="optionsError" class="options-error">{{ optionsError }}</p>
      </el-card>

      <el-alert
        v-if="summary && summary.freshnessStatus !== 'FINAL'"
        class="status-alert"
        :closable="false"
        show-icon
        :title="
          summary.freshnessStatus === 'DELAYED'
            ? '数据更新存在延迟，近期数据可能尚未完整，请稍后刷新。'
            : '所选范围仍在汇总，结果可能随后续回补更新。'
        "
        :type="summary.freshnessStatus === 'DELAYED' ? 'warning' : 'info'"
      />
      <el-alert
        v-if="summary?.partialFailure"
        class="status-alert"
        :closable="false"
        show-icon
        title="部分区块加载失败；已成功区块保持原值，不会被清零。"
        type="warning"
      />
      <el-alert
        v-if="summary && errorMessage"
        class="status-alert"
        :closable="false"
        show-icon
        :title="traceId ? `${errorMessage} · traceId ${traceId}` : errorMessage"
        type="error"
      />

      <el-result
        v-if="forbidden"
        icon="warning"
        sub-title="需要 ocpx:dashboard:view 与 Advertiser、Channel、MediaAccount 对象范围交集"
        title="无权查看经营总览"
      />
      <el-result
        v-else-if="errorMessage && !summary"
        icon="error"
        :sub-title="
          traceId ? `${errorMessage} · traceId ${traceId}` : errorMessage
        "
        title="经营总览加载失败"
      >
        <template #extra>
          <el-button type="primary" @click="load()">重新加载</el-button>
        </template>
      </el-result>
      <el-empty
        v-else-if="emptyKind === 'INITIAL'"
        description="当前还没有统一小时事实；不会展示样例经营数值"
      />
      <el-empty
        v-else-if="emptyKind === 'FILTER'"
        description="没有符合当前筛选范围的经营事实"
      >
        <el-button @click="reset">清空筛选</el-button>
      </el-empty>
      <template v-else>
        <section v-if="loading && !summary" class="metric-grid">
          <el-skeleton v-for="index in 5" :key="index" animated :rows="3" />
        </section>
        <section v-else class="metric-grid" v-loading="refreshing">
          <article v-for="card in cards" :key="card.metric" class="metric-card">
            <div class="metric-card-head">
              <span>{{ card.label }}</span>
              <el-tooltip placement="top">
                <template #content>
                  分子 {{ card.current.numerator ?? '—' }} · 分母
                  {{ card.current.denominator ?? '—' }} ·
                  {{ metricStatusLabel(card.current.status) }}
                </template>
                <span class="metric-status">指标说明</span>
              </el-tooltip>
            </div>
            <strong>{{ metricValue(card) }}</strong>
            <div
              class="comparison-row"
              :class="comparisonTone(card.changeRate)"
            >
              <span>{{ formatComparison(card.changeRate) }}</span>
              <span>基准 {{ comparisonValue(card) }}</span>
            </div>
            <p>{{ card.comparisonLabel }}</p>
          </article>
          <el-empty
            v-if="summary?.cards.status === 'EMPTY'"
            class="metric-empty"
            description="当前范围没有卡片事实"
          />
          <el-result
            v-if="
              summary && ['ERROR', 'TIMEOUT'].includes(summary.cards.status)
            "
            class="metric-empty"
            icon="error"
            :sub-title="sectionFailureText(summary.cards)"
            title="指标卡加载失败"
          />
        </section>

        <el-card class="trend-card" shadow="never" v-loading="refreshing">
          <template #header>
            <div class="section-heading">
              <h2>经营趋势</h2>
            </div>
          </template>
          <el-skeleton v-if="loading && !summary" animated :rows="8" />
          <EchartsUI
            v-else-if="summary?.trend.status === 'READY'"
            ref="trendRef"
            aria-label="经营指标日期趋势"
            height="380px"
          />
          <el-empty
            v-else-if="summary?.trend.status === 'EMPTY'"
            description="当前范围没有趋势事实"
          />
          <el-result
            v-else-if="summary"
            icon="error"
            :sub-title="sectionFailureText(summary.trend)"
            title="趋势加载失败"
          />
        </el-card>

        <section class="ranking-grid" v-loading="refreshing">
          <el-card
            v-for="ranking in rankings"
            :key="ranking.title"
            class="ranking-card"
            shadow="never"
          >
            <template #header>
              <div class="section-heading compact">
                <h2>{{ ranking.title }}</h2>
              </div>
            </template>
            <el-skeleton v-if="loading && !summary" animated :rows="6" />
            <div
              v-else-if="ranking.section?.status === 'READY'"
              class="ranking-scroll"
            >
              <div class="ranking-list">
                <div
                  v-for="(row, index) in ranking.section.data?.records"
                  :key="row.dimensionKey"
                  class="ranking-row"
                >
                  <span class="ranking-index">{{ index + 1 }}</span>
                  <div class="ranking-main">
                    <div class="ranking-label">
                      <span>对象 {{ row.objectId }}</span>
                      <strong>{{ rankingMetric(row, 'validClicks') }}</strong>
                    </div>
                    <div class="ranking-track">
                      <span
                        :style="{
                          background: ranking.accent,
                          width: `${rankingPercent(ranking.section, index)}%`,
                        }"
                      ></span>
                    </div>
                    <div class="ranking-meta">
                      <span
                        >激活率 {{ rankingMetric(row, 'activationRate') }}</span
                      >
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <el-empty
              v-else-if="ranking.section?.status === 'EMPTY'"
              description="当前范围没有排行事实"
            />
            <el-result
              v-else-if="ranking.section"
              icon="error"
              :sub-title="sectionFailureText(ranking.section)"
              title="排行区块加载失败"
            />
          </el-card>
        </section>
      </template>
    </div>
  </Page>
</template>

<style scoped>
.dashboard-page {
  --dash-border: var(--el-border-color-light);
  --dash-muted: var(--el-text-color-secondary);

  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 20px;
}

.dashboard-hero {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 6px 2px 2px;
}

.dashboard-hero h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.dashboard-intro {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--dash-muted);
}

.hero-actions,
.section-heading,
.metric-card-head,
.comparison-row,
.ranking-label,
.ranking-meta {
  display: flex;
  align-items: center;
}

.hero-actions {
  gap: 10px;
}

.filter-card :deep(.el-card__body) {
  padding-bottom: 4px;
}

.filter-grid {
  display: grid;
  grid-template-columns:
    minmax(260px, 1.6fr) repeat(6, minmax(130px, 1fr))
    auto;
  gap: 0 12px;
  min-width: 1180px;
}

.filter-card {
  overflow-x: auto;
}

.filter-actions :deep(.el-form-item__content) {
  flex-wrap: nowrap;
}

.options-error {
  margin: -6px 0 12px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.status-alert {
  margin: 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(170px, 1fr));
  gap: 12px;
  min-width: 0;
}

.metric-card {
  position: relative;
  min-width: 0;
  padding: 18px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--dash-border);
  border-radius: 8px;
}

.metric-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  content: '';
  background: linear-gradient(90deg, var(--el-color-primary), transparent);
}

.metric-card-head {
  justify-content: space-between;
  font-size: 13px;
  color: var(--dash-muted);
}

.metric-status {
  font-size: 10px;
  letter-spacing: 0.05em;
}

.metric-card > strong {
  display: block;
  margin-top: 18px;
  font-size: clamp(24px, 2.2vw, 34px);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.comparison-row {
  gap: 8px;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 12px;
}

.comparison-row.positive {
  color: var(--el-color-success);
}

.comparison-row.negative {
  color: var(--el-color-danger);
}

.comparison-row.muted,
.metric-card p {
  color: var(--dash-muted);
}

.metric-card p {
  margin: 8px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  white-space: nowrap;
}

.metric-empty {
  grid-column: 1 / -1;
}

.section-heading {
  gap: 16px;
  justify-content: space-between;
}

.section-heading h2 {
  margin: 0;
  font-size: 16px;
}

.section-heading.compact h2 {
  font-size: 15px;
}

.ranking-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  min-width: 0;
}

.ranking-card {
  min-width: 0;
}

.ranking-scroll {
  overflow-x: auto;
}

.ranking-list {
  display: grid;
  gap: 14px;
  min-width: 310px;
}

.ranking-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
}

.ranking-index {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 11px;
  font-weight: 700;
  color: var(--dash-muted);
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.ranking-main {
  min-width: 0;
}

.ranking-label,
.ranking-meta {
  gap: 12px;
  justify-content: space-between;
  font-size: 12px;
}

.ranking-label span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-label strong {
  font-variant-numeric: tabular-nums;
}

.ranking-track {
  height: 5px;
  margin: 7px 0;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: 999px;
}

.ranking-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.ranking-meta {
  font-size: 11px;
  color: var(--dash-muted);
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(170px, 1fr));
  }

  .ranking-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-page {
    padding: 12px;
  }

  .dashboard-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions {
    justify-content: space-between;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
