<script lang="ts" setup>
import type { DashboardOverview, UnifiedReportOptions } from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { Coin, DataLine, Refresh } from '@element-plus/icons-vue';
import dayjs from 'dayjs';

import { getDashboardOptions, getDashboardSummary } from '#/api/ocpx';

import { createLatestRequestGuard } from '../table';

defineOptions({ name: 'OcpxDashboard' });

type DatePreset = 'last-7-days' | 'last-30-days' | 'today' | 'yesterday';
type MetricCode =
  | 'CLICK'
  | 'CONVERSION'
  | 'CPA'
  | 'CPC'
  | 'CPM'
  | 'CTR'
  | 'CVR'
  | 'IMPRESSION'
  | 'SPEND';

const datePresets: Array<{ key: DatePreset; label: string }> = [
  { key: 'today', label: '今天' },
  { key: 'yesterday', label: '昨天' },
  { key: 'last-7-days', label: '过去7天' },
  { key: 'last-30-days', label: '过去30天' },
];
const metricDefinitions: Array<{ code: MetricCode; label: string }> = [
  { code: 'SPEND', label: '广告主消费' },
  { code: 'IMPRESSION', label: '展示数' },
  { code: 'CLICK', label: '点击数' },
  { code: 'CONVERSION', label: '转化数' },
  { code: 'CTR', label: '点击率' },
  { code: 'CVR', label: '转化率' },
  { code: 'CPM', label: 'CPM' },
  { code: 'CPC', label: 'CPC' },
  { code: 'CPA', label: 'CPA' },
];

const loading = ref(false);
const errorMessage = ref('');
const dashboard = ref<DashboardOverview>();
const options = ref<UnifiedReportOptions>();
const activePreset = ref<'custom' | DatePreset>('today');
const filters = reactive({
  advertiserId: '',
  dates: null as [string, string] | null,
  mediaId: '',
  monitorTaskId: '',
  promotionLinkId: '',
});
const loadGuard = createLatestRequestGuard();

const periodLabel = computed(() => {
  const dates = filters.dates;
  if (!dates) return '等待服务端业务日期';
  return dates[0] === dates[1] ? dates[0] : `${dates[0]} 至 ${dates[1]}`;
});

const selectedAccountName = computed(() => {
  const advertiserId = dashboard.value?.account?.advertiserId;
  return (
    options.value?.advertisers.find((item) => item.id === advertiserId)
      ?.label ||
    advertiserId ||
    '当前广告主'
  );
});

const accountCards = computed(() => {
  const account = dashboard.value?.account;
  if (!account) return [];
  return [
    {
      hint: `可用余额 · ${selectedAccountName.value}`,
      icon: Coin,
      label: '总金额',
      value: `¥ ${formatDecimal(account.availableBalance)}`,
    },
    {
      hint: `${dashboard.value?.businessDate} 00:00—23:59`,
      icon: DataLine,
      label: '今日广告主消费',
      value: `¥ ${formatDecimal(account.todaySpend)}`,
    },
  ];
});

const metricCards = computed(() =>
  metricDefinitions.map(({ code, label }) => {
    const metric = dashboard.value?.metrics?.[code];
    const readable = metric?.status === 'OK' && metric.value !== null;
    const numericValue = readable ? Number(metric.value) : undefined;
    const formattedNumericValue =
      numericValue === undefined ? '—' : formatDecimal(numericValue);
    const value =
      metric?.unit === 'COUNT' && numericValue !== undefined
        ? formatInteger(numericValue)
        : formattedNumericValue;
    return {
      code,
      label,
      unit: readable && metric?.unit === 'CNY' ? '¥' : undefined,
      value,
      valueSuffix: readable && metric?.unit === 'PERCENT' ? '%' : undefined,
    };
  }),
);

const dataStatusLabel = computed(() => {
  if (errorMessage.value) return '数据不可用';
  if (!dashboard.value) return '正在读取真实数据';
  return dashboard.value?.dataStatus === 'EMPTY'
    ? '当前范围暂无事件（已核验）'
    : '真实数据已就绪';
});

function datesForPreset(preset: DatePreset): [string, string] {
  const today = dayjs(dashboard.value?.businessDate);
  if (preset === 'yesterday') {
    const yesterday = today.subtract(1, 'day').format('YYYY-MM-DD');
    return [yesterday, yesterday];
  }
  if (preset === 'last-7-days') {
    return [
      today.subtract(7, 'day').format('YYYY-MM-DD'),
      today.subtract(1, 'day').format('YYYY-MM-DD'),
    ];
  }
  if (preset === 'last-30-days') {
    return [
      today.subtract(30, 'day').format('YYYY-MM-DD'),
      today.subtract(1, 'day').format('YYYY-MM-DD'),
    ];
  }
  const todayKey = today.format('YYYY-MM-DD');
  return [todayKey, todayKey];
}

function currentDates(): [string, string] {
  if (filters.dates?.[0] && filters.dates[1]) return filters.dates;
  filters.dates = datesForPreset('today');
  activePreset.value = 'today';
  return filters.dates;
}

function formatDecimal(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
  }).format(value);
}

function formatInteger(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(
    value,
  );
}

function selectPreset(preset: DatePreset) {
  if (!dashboard.value?.businessDate) return;
  activePreset.value = preset;
  filters.dates = datesForPreset(preset);
  void load();
}

function selectCustomDates() {
  activePreset.value = 'custom';
  void load();
}

function changeAdvertiser() {
  filters.promotionLinkId = '';
  filters.mediaId = '';
  filters.monitorTaskId = '';
  void load();
}

function changePromotionLink() {
  filters.monitorTaskId = '';
  void load();
}

function changeMedia() {
  filters.monitorTaskId = '';
  void load();
}

async function load(selectedDates: boolean | Event = true) {
  const requestSequence = loadGuard.begin();
  const useSelectedDates =
    typeof selectedDates === 'boolean' ? selectedDates : true;
  const dates = useSelectedDates ? currentDates() : undefined;
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await getDashboardSummary({
      advertiserId: filters.advertiserId || undefined,
      endDate: dates?.[1],
      mediaId: filters.mediaId || undefined,
      monitorTaskId: filters.monitorTaskId || undefined,
      promotionLinkId: filters.promotionLinkId || undefined,
      startDate: dates?.[0],
    });
    if (!loadGuard.isLatest(requestSequence)) return;
    dashboard.value = result;
    if (!useSelectedDates) {
      activePreset.value = 'today';
      filters.dates = [result.startDate, result.endDate];
    }
  } catch {
    if (!loadGuard.isLatest(requestSequence)) return;
    dashboard.value = undefined;
    errorMessage.value = '首页真实数据读取失败，请检查服务状态后重试。';
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

onMounted(async () => {
  try {
    options.value = await getDashboardOptions();
  } catch {
    errorMessage.value = '筛选项加载失败，请刷新页面重试。';
  }
  await load(false);
});
</script>

<template>
  <Page auto-content-height title="经营总览">
    <div class="dashboard-shell">
      <section class="dashboard-header">
        <div>
          <p class="eyebrow">PERFORMANCE OVERVIEW</p>
          <div class="title-line">
            <h2>广告数据</h2>
            <span class="period-label">{{ periodLabel }}</span>
          </div>
        </div>
        <el-button :disabled="loading" :icon="Refresh" plain @click="load">
          刷新数据
        </el-button>
      </section>

      <section class="filter-panel" aria-label="广告数据筛选">
        <div class="preset-tabs" role="group" aria-label="快捷日期筛选">
          <button
            v-for="preset in datePresets"
            :key="preset.key"
            :aria-pressed="activePreset === preset.key"
            class="preset-button"
            :class="{ active: activePreset === preset.key }"
            :disabled="loading || !dashboard?.businessDate"
            type="button"
            @click="selectPreset(preset.key)"
          >
            {{ preset.label }}
          </button>
        </div>
        <el-date-picker
          v-model="filters.dates"
          aria-label="自定义日期范围"
          :clearable="false"
          end-placeholder="结束日期"
          range-separator="→"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="selectCustomDates"
        />
        <el-select
          v-model="filters.advertiserId"
          aria-label="筛选广告主"
          clearable
          filterable
          placeholder="全部广告主"
          @change="changeAdvertiser"
        >
          <el-option
            v-for="item in options?.advertisers"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.promotionLinkId"
          aria-label="筛选推广链接"
          clearable
          filterable
          placeholder="全部推广链接"
          @change="changePromotionLink"
        >
          <el-option
            v-for="item in options?.promotionLinks"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.mediaId"
          aria-label="筛选媒体"
          clearable
          filterable
          placeholder="全部媒体"
          @change="changeMedia"
        >
          <el-option
            v-for="item in options?.media"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.monitorTaskId"
          aria-label="筛选监测任务"
          clearable
          filterable
          placeholder="全部监测任务"
          @change="load"
        >
          <el-option
            v-for="item in options?.monitorTasks"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
      </section>

      <el-alert
        v-if="errorMessage"
        class="dashboard-alert"
        :closable="false"
        show-icon
        :title="errorMessage"
        type="error"
      />

      <el-alert
        v-if="dashboard?.account?.rejectedClickCount"
        class="dashboard-alert"
        :closable="false"
        show-icon
        :title="`今日有 ${dashboard.account.rejectedClickCount} 次有效点击因余额不足未计费、未向广告主投递。`"
        type="warning"
      />

      <el-alert
        v-if="dashboard?.account?.rejectedConversionCount"
        class="dashboard-alert"
        :closable="false"
        show-icon
        :title="`今日有 ${dashboard.account.rejectedConversionCount} 次目标转化因余额不足未计费；扣量判定和媒体 Callback 仍按原流程执行。`"
        type="warning"
      />

      <section
        v-if="accountCards.length > 0"
        class="account-grid"
        aria-label="账户概况"
      >
        <article
          v-for="card in accountCards"
          :key="card.label"
          class="account-card"
        >
          <div class="account-icon"><component :is="card.icon" /></div>
          <div>
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
            <small>{{ card.hint }}</small>
          </div>
        </article>
      </section>

      <section v-loading="loading" class="metric-panel">
        <div class="section-heading">
          <div>
            <h3>核心指标</h3>
            <p>
              {{ dataStatusLabel }} · 业务时区
              {{ dashboard?.timezone || 'Asia/Shanghai' }}
            </p>
          </div>
          <span class="live-badge">
            <i></i> 数据更新至 {{ dashboard?.asOf || '—' }}
          </span>
        </div>
        <div class="metric-grid">
          <article
            v-for="card in metricCards"
            :key="card.code"
            class="metric-card"
          >
            <span>{{ card.label }}</span>
            <strong>
              <small v-if="card.unit === '¥'">¥</small>{{ card.value
              }}<small v-if="card.valueSuffix">{{ card.valueSuffix }}</small>
            </strong>
          </article>
        </div>
      </section>
    </div>
  </Page>
</template>

<style scoped>
.dashboard-shell {
  --dashboard-accent: #1769e0;
  --dashboard-accent-soft: rgb(23 105 224 / 9%);
  --dashboard-border: var(--el-border-color-lighter);

  padding: 22px;
  background:
    linear-gradient(135deg, rgb(23 105 224 / 4%), transparent 32%),
    var(--el-bg-color-page);
  border: 1px solid var(--dashboard-border);
  border-radius: 18px;
}

.dashboard-header,
.title-line,
.section-heading,
.filter-panel,
.account-card {
  display: flex;
  align-items: center;
}

.dashboard-header,
.section-heading {
  justify-content: space-between;
}

.dashboard-header {
  gap: 24px;
  margin-bottom: 20px;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--dashboard-accent);
  letter-spacing: 0.14em;
}

.title-line {
  gap: 14px;
}

.title-line h2,
.section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
}

.title-line h2 {
  font-size: 25px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.period-label,
.live-badge {
  color: var(--el-text-color-secondary);
}

.period-label {
  padding-left: 14px;
  font-size: 13px;
  border-left: 1px solid var(--dashboard-border);
}

.filter-panel {
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
  margin-bottom: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--dashboard-border);
  border-radius: 13px;
  box-shadow: 0 8px 28px rgb(31 46 68 / 4%);
}

.preset-tabs {
  display: inline-flex;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--dashboard-border);
  border-radius: 9px;
}

.preset-button {
  min-height: 34px;
  padding: 0 16px;
  font: inherit;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--dashboard-border);
  transition:
    color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease;
}

.preset-button:last-child {
  border-right: 0;
}

.preset-button:hover {
  color: var(--dashboard-accent);
}

.preset-button:disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}

.preset-button.active {
  color: #fff;
  background: var(--dashboard-accent);
  box-shadow: 0 5px 12px rgb(23 105 224 / 22%);
}

.filter-panel :deep(.el-date-editor) {
  width: 310px;
}

.filter-panel :deep(.el-select) {
  width: 170px;
}

.dashboard-alert {
  margin-bottom: 16px;
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.account-card {
  position: relative;
  gap: 16px;
  min-height: 122px;
  padding: 22px 24px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--dashboard-border);
  border-radius: 15px;
  box-shadow: 0 10px 35px rgb(31 46 68 / 5%);
}

.account-card::after {
  position: absolute;
  top: -55px;
  right: -48px;
  width: 150px;
  height: 150px;
  content: '';
  background: radial-gradient(
    circle,
    var(--dashboard-accent-soft),
    transparent 67%
  );
}

.account-icon {
  z-index: 1;
  display: grid;
  flex: 0 0 48px;
  place-items: center;
  width: 48px;
  height: 48px;
  color: var(--dashboard-accent);
  background: var(--dashboard-accent-soft);
  border-radius: 13px;
}

.account-icon svg {
  width: 23px;
  height: 23px;
}

.account-card p,
.account-card small,
.metric-card span,
.section-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.account-card p {
  margin-bottom: 5px;
  font-size: 13px;
}

.account-card strong {
  display: block;
  margin-bottom: 4px;
  font-size: 26px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.account-card small {
  font-size: 12px;
}

.metric-panel {
  padding: 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--dashboard-border);
  border-radius: 15px;
  box-shadow: 0 10px 35px rgb(31 46 68 / 5%);
}

.section-heading {
  gap: 18px;
  margin-bottom: 18px;
}

.section-heading h3 {
  margin-bottom: 3px;
  font-size: 17px;
  font-weight: 680;
}

.section-heading p,
.live-badge {
  font-size: 12px;
}

.live-badge i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  background: #20a56a;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgb(32 165 106 / 12%);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  min-height: 118px;
  padding: 20px;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 13px;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.metric-card:hover {
  border-color: rgb(23 105 224 / 22%);
  box-shadow: 0 12px 26px rgb(23 105 224 / 8%);
  transform: translateY(-2px);
}

.metric-card span {
  display: block;
  margin-bottom: 24px;
  font-size: 13px;
}

.metric-card strong {
  font-size: 25px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.metric-card strong small {
  margin: 0 3px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.metric-card strong small:first-child {
  margin-left: 0;
}

@media (width <= 1000px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 760px) {
  .dashboard-shell {
    padding: 14px;
    border-radius: 14px;
  }

  .dashboard-header,
  .section-heading {
    align-items: flex-start;
  }

  .dashboard-header,
  .title-line,
  .section-heading {
    flex-direction: column;
  }

  .title-line {
    gap: 6px;
    align-items: flex-start;
  }

  .period-label {
    padding-left: 0;
    border-left: 0;
  }

  .preset-tabs {
    width: 100%;
  }

  .preset-button {
    flex: 1;
    padding: 0 8px;
  }

  .filter-panel :deep(.el-date-editor),
  .filter-panel :deep(.el-select) {
    width: 100%;
  }

  .account-grid,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .metric-panel {
    padding: 16px;
  }
}
</style>
