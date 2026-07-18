<script lang="ts" setup>
import type { EchartsUIType } from '@boot/plugins/echarts';

import type { Media } from '#/api/ad';

import { computed, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@boot/plugins/echarts';

import { getMedia, getReportOverview } from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdReportingOverview' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const endDate = ref(new Date().toISOString().slice(0, 10));
const trend = ref<any[]>([]);
const loading = ref(false);
const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const today = computed(() => trend.value.at(-1) || {});
const yesterday = computed(() => trend.value.at(-2) || {});

function percentChange(key: string) {
  const current = Number(today.value[key] || 0);
  const previous = Number(yesterday.value[key] || 0);
  if (previous === 0) return current === 0 ? '—' : '新增';
  const value = ((current - previous) / previous) * 100;
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function renderTrend() {
  if (trend.value.length === 0) return;
  renderEcharts({
    color: ['#14b8a6', '#38bdf8', '#f59e0b'],
    grid: { bottom: 24, containLabel: true, left: 16, right: 20, top: 42 },
    legend: { data: ['点击', '激活', '付费'] },
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
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
  await load();
}

watch([mediaId, endDate], load);
watch(trend, renderTrend, { deep: true });
onMounted(bootstrap);
</script>

<template>
  <main class="mvp-page">
    <header class="mvp-hero">
      <div>
        <div class="mvp-kicker">M5 / Operating Signal</div>
        <h1 class="mvp-title">广告效果概览</h1>
        <p class="mvp-subtitle">
          从已成功的日报快照读取最近 7
          天运营信号；没有快照时明确展示空态，不展示模拟数据。
        </p>
      </div>
      <div class="mvp-control grid grid-cols-2 gap-2">
        <el-select v-model="mediaId">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="endDate"
          type="date"
          value-format="YYYY-MM-DD"
        />
      </div>
    </header>

    <section class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <div class="mvp-metric">
        <strong>{{ today.impressionCount || 0 }}</strong
        ><span>当日曝光</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ today.uniqueClickCount || 0 }}</strong
        ><span>当日点击 · {{ percentChange('uniqueClickCount') }}</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ today.attributedActivateCount || 0 }}</strong
        ><span>当日激活 · {{ percentChange('attributedActivateCount') }}</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ today.nextDayReturnCount || 0 }}</strong
        ><span>次日留存</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ today.attributedOrderCount || 0 }}</strong
        ><span>当日下单</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ today.attributedPayCount || 0 }}</strong
        ><span>当日付费 · {{ percentChange('attributedPayCount') }}</span>
      </div>
    </section>

    <el-card v-loading="loading" class="mvp-card" shadow="never">
      <template #header>
        <div class="mvp-toolbar">
          <div>
            <div class="font-semibold">点击、激活与付费趋势</div>
            <div class="mt-1 text-xs text-gray-500">
              日期结束于 {{ endDate }}
            </div>
          </div>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>
      <div v-if="trend.length > 0" class="h-80">
        <EchartsUI ref="chartRef" />
      </div>
      <div v-else class="mvp-empty">
        最近 7 天尚无成功日报快照，请先在“效果日报”生成快照。
      </div>
    </el-card>

    <el-card class="mvp-card mt-4" shadow="never">
      <template #header><div class="font-semibold">日报趋势明细</div></template>
      <el-table :data="trend" row-key="reportDate">
        <el-table-column label="日期" prop="reportDate" min-width="130" />
        <el-table-column label="曝光" prop="impressionCount" />
        <el-table-column label="点击" prop="uniqueClickCount" />
        <el-table-column label="有效点击" prop="eligibleClickCount" />
        <el-table-column label="激活" prop="attributedActivateCount" />
        <el-table-column label="注册" prop="attributedRegisterCount" />
        <el-table-column label="付费" prop="attributedPayCount" />
        <el-table-column label="下单" prop="attributedOrderCount" />
        <el-table-column label="次留" prop="nextDayReturnCount" />
      </el-table>
    </el-card>
  </main>
</template>
