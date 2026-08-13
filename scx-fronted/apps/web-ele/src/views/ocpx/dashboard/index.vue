<script lang="ts" setup>
import type { DashboardOverview, UnifiedReportOptions } from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import { getDashboardOptions, getDashboardSummary } from '#/api/ocpx';

defineOptions({ name: 'OcpxDashboard' });

const loading = ref(false);
const dashboard = ref<DashboardOverview>();
const options = ref<UnifiedReportOptions>();
const filters = reactive({
  advertiserId: '',
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  mediaId: '',
  monitorTaskId: '',
  promotionLinkId: '',
});

const cards = computed(() =>
  Object.entries(dashboard.value?.summary ?? {}).map(([code, value]) => ({
    amount: value.amount,
    code,
    count: value.count,
  })),
);

async function load() {
  loading.value = true;
  try {
    dashboard.value = await getDashboardSummary({
      advertiserId: filters.advertiserId || undefined,
      endDate: filters.dates[1],
      mediaId: filters.mediaId || undefined,
      monitorTaskId: filters.monitorTaskId || undefined,
      promotionLinkId: filters.promotionLinkId || undefined,
      startDate: filters.dates[0],
    });
  } finally {
    loading.value = false;
  }
}

function metricCount(metrics: Record<string, { count: number }>) {
  return Object.values(metrics).reduce((sum, item) => sum + item.count, 0);
}

onMounted(async () => {
  options.value = await getDashboardOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="经营总览">
    <el-card shadow="never">
      <div class="mb-4 flex flex-wrap gap-3">
        <el-date-picker
          v-model="filters.dates"
          end-placeholder="结束日期"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
        />
        <el-select
          v-model="filters.advertiserId"
          clearable
          placeholder="广告主"
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
          clearable
          placeholder="推广链接"
        >
          <el-option
            v-for="item in options?.promotionLinks"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="filters.mediaId" clearable placeholder="媒体">
          <el-option
            v-for="item in options?.media"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-row v-loading="loading" :gutter="16">
        <el-col v-for="card in cards" :key="card.code" :span="6">
          <el-statistic :title="card.code" :value="card.count" />
          <small v-if="card.amount">金额 {{ card.amount }}</small>
        </el-col>
      </el-row>
    </el-card>

    <el-row class="mt-4" :gutter="16">
      <el-col :span="12">
        <el-card header="广告主排行" shadow="never">
          <el-table :data="dashboard?.advertiserRanking ?? []">
            <el-table-column label="广告主" prop="dimensionName" />
            <el-table-column label="流程数">
              <template #default="{ row }">{{
                metricCount(row.metrics)
              }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="推广链接排行" shadow="never">
          <el-table :data="dashboard?.promotionLinkRanking ?? []">
            <el-table-column label="推广链接" prop="dimensionName" />
            <el-table-column label="流程数">
              <template #default="{ row }">{{
                metricCount(row.metrics)
              }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row class="mt-4" :gutter="16">
      <el-col :span="12">
        <el-card header="媒体排行" shadow="never">
          <el-table :data="dashboard?.mediaRanking ?? []">
            <el-table-column label="媒体" prop="dimensionName" />
            <el-table-column label="流程数">
              <template #default="{ row }">{{
                metricCount(row.metrics)
              }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="监测任务排行" shadow="never">
          <el-table :data="dashboard?.monitorTaskRanking ?? []">
            <el-table-column label="监测任务" prop="dimensionName" />
            <el-table-column label="流程数">
              <template #default="{ row }">{{
                metricCount(row.metrics)
              }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </Page>
</template>
