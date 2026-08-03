<script lang="ts" setup>
import type { DashboardSummary } from '#/api/adx';

import { computed, onMounted, ref } from 'vue';

import { getDashboardSummary } from '#/api/adx';

defineOptions({ name: 'AdxDashboard' });

const loading = ref(false);
const summary = ref<DashboardSummary>({
  sevenDayIncome: 0,
  thirtyDayIncome: 0,
  totalIncome: 0,
  trend: [],
  yesterdayIncome: 0,
});

const cards = computed(() => [
  { label: '昨日收入', tone: 'blue', value: summary.value.yesterdayIncome },
  { label: '七日收入', tone: 'indigo', value: summary.value.sevenDayIncome },
  { label: '30天收入', tone: 'violet', value: summary.value.thirtyDayIncome },
  { label: '累积总收入', tone: 'emerald', value: summary.value.totalIncome },
]);

const chartPoints = computed(() => {
  const values = summary.value.trend.map((item) => Number(item.income));
  if (values.length === 0) return '';
  const max = Math.max(...values, 1);
  return values
    .map((value, index) => {
      const x =
        values.length === 1 ? 500 : (index / (values.length - 1)) * 1000;
      const y = 250 - (value / max) * 210;
      return `${x},${y}`;
    })
    .join(' ');
});

function money(value: number) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    style: 'currency',
    currency: 'CNY',
  });
}

async function load() {
  loading.value = true;
  try {
    summary.value = await getDashboardSummary();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading" class="dashboard-page">
    <header>
      <p class="eyebrow">ADX · OVERVIEW</p>
      <h1>广告服务平台</h1>
      <p>收入概览与近 30 天趋势</p>
    </header>

    <section class="metric-grid" aria-label="收入指标">
      <article v-for="card in cards" :key="card.label" :class="card.tone">
        <span>{{ card.label }}</span>
        <strong>{{ money(card.value) }}</strong>
        <i aria-hidden="true"></i>
      </article>
    </section>

    <el-card shadow="never" class="trend-card">
      <div class="card-title">
        <div>
          <h2>收入趋势</h2>
          <p>按自然日汇总最近 30 天收入</p>
        </div>
        <el-button :loading="loading" @click="load">刷新数据</el-button>
      </div>
      <div v-if="summary.trend.length > 0" class="chart-wrap">
        <svg
          role="img"
          aria-label="最近三十天收入折线图"
          preserveAspectRatio="none"
          viewBox="0 0 1000 280"
        >
          <defs>
            <linearGradient id="adxTrendFill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stop-color="#2563eb" stop-opacity=".22" />
              <stop offset="1" stop-color="#2563eb" stop-opacity="0" />
            </linearGradient>
          </defs>
          <line
            v-for="n in 5"
            :key="n"
            x1="0"
            x2="1000"
            :y1="n * 48"
            :y2="n * 48"
          />
          <polygon
            :points="`0,270 ${chartPoints} 1000,270`"
            fill="url(#adxTrendFill)"
          />
          <polyline
            :points="chartPoints"
            fill="none"
            stroke="#2563eb"
            stroke-width="4"
          />
        </svg>
        <div class="chart-labels">
          <span>{{ summary.trend[0]?.reportDate }}</span>
          <span>{{ summary.trend.at(-1)?.reportDate }}</span>
        </div>
      </div>
      <el-empty v-else description="当前时间范围暂无报表收入数据" />
    </el-card>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-height: 100%;
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0, #f3f5f9 100%);
}

header {
  margin-bottom: 22px;
}

header h1 {
  margin: 3px 0 6px;
  font-size: 28px;
  font-weight: 680;
  color: #172033;
}

header p {
  margin: 0;
  color: #667085;
}

.eyebrow {
  font-size: 11px;
  font-weight: 750;
  color: #2563eb !important;
  letter-spacing: 0.15em;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.metric-grid article {
  position: relative;
  min-height: 132px;
  padding: 22px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e6eaf0;
  border-radius: 12px;
  box-shadow: 0 4px 18px rgb(16 24 40 / 4%);
}

.metric-grid span {
  font-size: 13px;
  color: #667085;
}

.metric-grid strong {
  display: block;
  margin-top: 17px;
  font-size: 26px;
  font-weight: 700;
  color: #172033;
  letter-spacing: -0.03em;
}

.metric-grid i {
  position: absolute;
  top: 0;
  right: 0;
  width: 72px;
  height: 72px;
  background: #eff6ff;
  border-radius: 0 0 0 72px;
}

.metric-grid .indigo i {
  background: #eef2ff;
}

.metric-grid .violet i {
  background: #f5f3ff;
}

.metric-grid .emerald i {
  background: #ecfdf3;
}

.trend-card {
  border-color: #e6eaf0;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #172033;
}

.card-title p {
  margin: 0;
  font-size: 13px;
  color: #98a2b3;
}

.chart-wrap {
  height: 330px;
  margin-top: 22px;
}

.chart-wrap svg {
  width: 100%;
  height: 290px;
  overflow: visible;
}

.chart-wrap line {
  stroke: #eef1f5;
  stroke-width: 1;
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #98a2b3;
}

@media (max-width: 1024px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .dashboard-page {
    padding: 14px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
