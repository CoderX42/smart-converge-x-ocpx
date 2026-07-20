<script lang="ts" setup>
import type { EchartsUIType } from '@boot/plugins/echarts';

import type { Media } from '#/api/ad';

import { computed, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@boot/plugins/echarts';
import { Page } from '@boot/common-ui';

import { getMedia, getReportBreakdown } from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdReportingOverview' });

type ReportTrendPoint = {
  reportDate: string;
  impressionCount?: number | null;
  uniqueClickCount?: number | null;
  uniqueDeduplicatedClickCount?: number | null;
  attributedActivateCount?: number | null;
  attributedRegisterCount?: number | null;
  attributedPayCount?: number | null;
  attributedOrderCount?: number | null;
  nextDayReturnCount?: number | null;
  firstActivateCount?: number | null;
};

const media = ref<Media[]>([]);
const mediaId = ref('');
const dateRange = ref<[string, string]>([
  toDateString(new Date(Date.now() - 6 * 86400000)),
  toDateString(new Date()),
]);
const trend = ref<ReportTrendPoint[]>([]);
const loading = ref(false);
const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const startDate = computed(() => dateRange.value[0]);
const end = computed(() => dateRange.value[1]);

const selectedRangeDays = computed(() => {
  const start = new Date(`${startDate.value}T00:00:00`);
  const finish = new Date(`${end.value}T00:00:00`);
  return Math.floor((finish.getTime() - start.getTime()) / 86400000) + 1;
});

const today = computed<ReportTrendPoint>(() => trend.value.at(-1) ?? ({} as ReportTrendPoint));
const yesterday = computed<ReportTrendPoint>(() => trend.value.at(-2) ?? ({} as ReportTrendPoint));

const isRangeTooLong = computed(() => selectedRangeDays.value > 31);

const metricCards = computed(() => [
  {
    label: '当日点击',
    value: today.value.uniqueClickCount ?? 0,
    delta: percentChange('uniqueClickCount'),
    note: '口径：report_daily.unique_click_count，按成功日报快照按媒体汇总。',
  },
  {
    label: '当日激活',
    value: today.value.attributedActivateCount ?? 0,
    delta: percentChange('attributedActivateCount'),
    note: '口径：report_daily.attributed_activate_count，剔除无效归因事件。',
  },
  {
    label: '当日首活',
    value: today.value.firstActivateCount ?? today.value.attributedRegisterCount ?? 0,
    delta: percentChange('firstActivateCount'),
    note: '口径：report_daily.first_activate_count；无首活数据时回退到 attributed_register_count。',
  },
  {
    label: '当日次留',
    value: today.value.nextDayReturnCount ?? 0,
    delta: percentChange('nextDayReturnCount'),
    note: '口径：report_daily.next_day_return_count。',
  },
  {
    label: '当日付费',
    value: today.value.attributedPayCount ?? 0,
    delta: percentChange('attributedPayCount'),
    note: '口径：report_daily.attributed_pay_count，保留审核通过结果。',
  },
]);

function toDateString(value: Date) {
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${value.getFullYear()}-${month}-${day}`;
}

function percentChange(key: keyof ReportTrendPoint) {
  const get = (point: ReportTrendPoint) => {
    const value = (point as unknown as Record<string, unknown>)[key];
    return typeof value === 'number' ? value : 0;
  };

  const current = get(today.value);
  const previous = get(yesterday.value);
  if (previous === 0) return current === 0 ? '—' : '新增';
  const value = ((current - previous) / previous) * 100;
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function renderTrend() {
  if (trend.value.length === 0) return;

  renderEcharts({
    color: ['#14b8a6', '#38bdf8', '#f59e0b', '#f97316', '#8b5cf6'],
    grid: { bottom: 24, containLabel: true, left: 16, right: 20, top: 42 },
    legend: { data: ['点击', '激活', '首活', '次留', '付费'] },
    tooltip: { trigger: 'axis' },
    xAxis: {
      boundaryGap: false,
      data: trend.value.map((item) => item.reportDate),
      type: 'category',
    },
    yAxis: { type: 'value' },
    series: [
      {
        areaStyle: { opacity: 0.14 },
        data: trend.value.map((item) => item.uniqueClickCount || 0),
        name: '点击',
        smooth: true,
        type: 'line',
      },
      {
        data: trend.value.map((item) => item.attributedActivateCount || 0),
        name: '激活',
        smooth: true,
        type: 'line',
      },
      {
        data: trend.value.map((item) => item.firstActivateCount || item.attributedRegisterCount || 0),
        name: '首活',
        smooth: true,
        type: 'line',
      },
      {
        data: trend.value.map((item) => item.nextDayReturnCount || 0),
        name: '次留',
        smooth: true,
        type: 'line',
      },
      {
        areaStyle: { opacity: 0.14 },
        data: trend.value.map((item) => item.attributedPayCount || 0),
        name: '付费',
        smooth: true,
        type: 'line',
      },
    ],
  });
}

async function load() {
  if (!mediaId.value) return;
  if (isRangeTooLong.value) return;

  loading.value = true;
  try {
    const result = await getReportBreakdown({
      dimension: 'DATE',
      startDate: startDate.value,
      endDate: end.value,
      mediaId: mediaId.value,
    });

    trend.value = result
      .map((item: Record<string, any>) => ({
        reportDate: String(item.dimensionId ?? ''),
        impressionCount: Number(item.impressionCount || 0),
        uniqueClickCount: Number(item.uniqueClickCount || 0),
        attributedActivateCount: Number(item.attributedActivateCount || 0),
        attributedRegisterCount: Number(item.attributedRegisterCount || 0),
        attributedPayCount: Number(item.attributedPayCount || 0),
        attributedOrderCount: Number(item.attributedOrderCount || 0),
        nextDayReturnCount: Number(item.nextDayReturnCount || 0),
        firstActivateCount: Number(item.firstActivateCount || 0),
      }))
      .filter((item) => item.reportDate)
      .sort((a, b) => a.reportDate.localeCompare(b.reportDate));

    renderTrend();
  } finally {
    loading.value = false;
  }
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
  await load();
}

function disabledRange(date: Date) {
  const value = toDateString(date);
  return value > end.value;
}

watch(mediaId, load);
watch(
  dateRange,
  () => {
    void load();
  },
  { deep: true },
);
watch(trend, renderTrend, { deep: true });
onMounted(bootstrap);
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control grid grid-cols-2 gap-2 md:grid-cols-4">
        <el-select v-model="mediaId">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          :disabled-date="disabledRange"
          end-placeholder="结束日期"
          range-separator="~"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="load"
        />
        <div class="col-span-2 flex items-center gap-2">
          <el-button :disabled="isRangeTooLong" size="small" @click="load">
            刷新
          </el-button>
          <span v-if="isRangeTooLong" class="text-xs text-red-500">
            时间范围最多 31 天
          </span>
          <span class="text-xs text-gray-500">时区：Asia/Shanghai</span>
        </div>
      </div>
    </div>

    <section class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
      <div v-for="item in metricCards" :key="item.label" class="mvp-metric">
        <strong>{{ item.value }}</strong
        ><span>
          {{ item.label }}
          <template v-if="item.delta">
            · {{ item.delta }}
          </template>
        </span>
        <div class="mt-1 text-xs text-gray-500">{{ item.note }}</div>
      </div>
    </section>

    <el-card v-loading="loading" class="mvp-card" shadow="never">
      <template #header>
        <div class="mvp-toolbar">
          <div>
            <div class="font-semibold">指标趋势（{{ startDate }} ~ {{ end }}）</div>
            <div class="mt-1 text-xs text-gray-500">
              范围内共 {{ selectedRangeDays }} 天；来源：成功快照
              <template v-if="!isRangeTooLong">，点击量为空时请先生成对应日期的日报。</template>
            </div>
          </div>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <div v-if="trend.length > 0" class="h-80">
        <EchartsUI ref="chartRef" />
      </div>
      <div v-else class="mvp-empty">
        当前筛选范围内无成功日报快照。
        <div class="mt-1 text-xs text-gray-500">请先在“效果日报”触发汇总，再返回此页查看。</div>
      </div>
    </el-card>

    <el-card class="mvp-card mt-4" shadow="never">
      <template #header><div class="font-semibold">日报趋势明细</div></template>
      <el-table :data="trend" row-key="reportDate">
        <el-table-column label="日期" prop="reportDate" min-width="130" />
        <el-table-column label="曝光" prop="impressionCount" />
        <el-table-column label="点击" prop="uniqueClickCount" />
        <el-table-column label="有效点击" prop="uniqueDeduplicatedClickCount" />
        <el-table-column label="激活" prop="attributedActivateCount" />
        <el-table-column label="注册" prop="attributedRegisterCount" />
        <el-table-column label="付费" prop="attributedPayCount" />
        <el-table-column label="下单" prop="attributedOrderCount" />
        <el-table-column label="次留" prop="nextDayReturnCount" />
      </el-table>
    </el-card>
  </main>
</template>
