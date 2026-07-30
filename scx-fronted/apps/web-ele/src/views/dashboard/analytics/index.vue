<script lang="ts" setup>
import type { EchartsUIType } from '@boot/plugins/echarts';

import type { Media } from '#/api/ad';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@boot/common-ui';
import { EchartsUI, useEcharts } from '@boot/plugins/echarts';

import { getMedia, getReportOverview } from '#/api/ad';
import { shanghaiDate } from '#/utils/report-date';

defineOptions({ name: 'AdAnalyticsDashboard' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const endDate = ref(shanghaiDate());
const trend = ref<any[]>([]);
const loading = ref(false);
const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const today = computed(() => trend.value.at(-1) ?? {});
const yesterday = computed(() => trend.value.at(-2) ?? {});

const cards = computed(() => [
  {
    accent: 'bg-sky-500',
    key: 'impressionCount',
    label: '当日曝光',
    value: today.value.impressionCount ?? 0,
  },
  {
    accent: 'bg-cyan-500',
    key: 'uniqueClickCount',
    label: '当日点击',
    value: today.value.uniqueClickCount ?? 0,
  },
  {
    accent: 'bg-emerald-500',
    key: 'attributedActivateCount',
    label: '当日激活',
    value: today.value.attributedActivateCount ?? 0,
  },
  {
    accent: 'bg-amber-500',
    key: 'attributedPayCount',
    label: '当日付费',
    value: today.value.attributedPayCount ?? 0,
  },
]);

function change(key: string) {
  const current = Number(today.value[key] ?? 0);
  const previous = Number(yesterday.value[key] ?? 0);
  if (previous === 0) return current === 0 ? '较昨日持平' : '较昨日新增';
  const value = ((current - previous) / previous) * 100;
  return `较昨日 ${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
}

function renderTrend() {
  if (trend.value.length === 0) return;
  renderEcharts({
    color: ['#0ea5e9', '#10b981', '#f59e0b'],
    grid: { bottom: 24, containLabel: true, left: 12, right: 18, top: 36 },
    legend: { data: ['点击', '激活', '付费'], right: 0 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      boundaryGap: false,
      data: trend.value.map((item) => item.reportDate),
      type: 'category',
    },
    yAxis: { type: 'value' },
    series: [
      {
        areaStyle: { opacity: 0.1 },
        data: trend.value.map((item) => item.uniqueClickCount ?? 0),
        name: '点击',
        smooth: true,
        type: 'line',
      },
      {
        data: trend.value.map((item) => item.attributedActivateCount ?? 0),
        name: '激活',
        smooth: true,
        type: 'line',
      },
      {
        data: trend.value.map((item) => item.attributedPayCount ?? 0),
        name: '付费',
        smooth: true,
        type: 'line',
      },
    ],
  });
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    trend.value = await getReportOverview({
      endDate: endDate.value,
      mediaId: mediaId.value,
    });
    renderTrend();
  } finally {
    loading.value = false;
  }
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records ?? [];
  mediaId.value = media.value[0]?.id ?? '';
  await load();
}

watch([mediaId, endDate], load);
watch(trend, renderTrend, { deep: true });
onMounted(bootstrap);
</script>

<template>
  <Page
    description="从已成功生成的效果日报读取运营信号。"
    title="广告效果工作台"
  >
    <template #extra>
      <div class="flex flex-wrap items-center gap-2">
        <el-date-picker
          v-model="endDate"
          class="w-36"
          type="date"
          value-format="YYYY-MM-DD"
        />
        <el-button :loading="loading" type="primary" @click="load">
          刷新
        </el-button>
      </div>
    </template>

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <el-card
        v-for="card in cards"
        :key="card.key"
        class="relative overflow-hidden"
        shadow="never"
      >
        <div
          class="absolute left-0 top-0 h-full w-1"
          :class="[card.accent]"
        ></div>
        <div class="pl-2">
          <div class="text-sm text-gray-500">{{ card.label }}</div>
          <el-statistic :value="card.value" class="mt-2" />
          <div class="mt-2 text-xs text-gray-400">{{ change(card.key) }}</div>
        </div>
      </el-card>
    </div>

    <el-card v-loading="loading" class="mt-4" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <div>
            <div class="font-semibold">近 7 天转化趋势</div>
            <div class="mt-1 text-xs text-gray-400">统计截止 {{ endDate }}</div>
          </div>
          <el-tag effect="plain" type="info">日报快照</el-tag>
        </div>
      </template>
      <div v-if="trend.length > 0" class="h-80">
        <EchartsUI ref="chartRef" />
      </div>
      <el-empty
        v-else
        description="暂无成功日报快照，请先在“效果日报”生成数据。"
      />
    </el-card>

    <el-card class="mt-4" shadow="never">
      <template #header><div class="font-semibold">当日漏斗</div></template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="有效点击">
          {{ today.eligibleClickCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="激活">
          {{ today.attributedActivateCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="注册">
          {{ today.attributedRegisterCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="下单 / 付费">
          {{ today.attributedOrderCount ?? 0 }} /
          {{ today.attributedPayCount ?? 0 }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </Page>
</template>
