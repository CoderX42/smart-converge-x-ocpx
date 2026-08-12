<script lang="ts" setup>
import type {
  UnifiedReportGroupBy,
  UnifiedReportOptions,
  UnifiedReportRecord,
} from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import { getReport, getUnifiedReportOptions } from '#/api/ocpx';

defineOptions({ name: 'OcpxUnifiedReport' });

const rows = ref<UnifiedReportRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const options = ref<UnifiedReportOptions>();
const filters = reactive({
  advertiserId: '',
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  groupBy: 'TIME' as UnifiedReportGroupBy,
  mediaId: '',
  monitorTaskId: '',
  promotionLinkId: '',
  size: 20,
});

async function load() {
  loading.value = true;
  try {
    const result = await getReport({
      advertiserId: filters.advertiserId || undefined,
      current: filters.current,
      endDate: filters.dates[1],
      groupBy: filters.groupBy,
      mediaId: filters.mediaId || undefined,
      monitorTaskId: filters.monitorTaskId || undefined,
      promotionLinkId: filters.promotionLinkId || undefined,
      size: filters.size,
      startDate: filters.dates[0],
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  options.value = await getUnifiedReportOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="报表中心">
    <el-card shadow="never">
      <div class="mb-4 flex flex-wrap gap-3">
        <el-date-picker
          v-model="filters.dates"
          type="daterange"
          value-format="YYYY-MM-DD"
        />
        <el-select v-model="filters.groupBy" placeholder="统计维度">
          <el-option
            v-for="value in [
              'TIME',
              'ADVERTISER',
              'PROMOTION_LINK',
              'MEDIA',
              'MONITOR_TASK',
            ]"
            :key="value"
            :label="value"
            :value="value"
          />
        </el-select>
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
        <el-select
          v-model="filters.monitorTaskId"
          clearable
          placeholder="监测任务"
        >
          <el-option
            v-for="item in options?.monitorTasks"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="维度" prop="dimensionType" />
        <el-table-column label="对象" prop="dimensionName" />
        <el-table-column label="指标">
          <template #default="{ row }">
            <el-tag v-for="(value, key) in row.metrics" :key="key" class="mr-2">
              {{ key }}: {{ value.count }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="filters.current"
        v-model:page-size="filters.size"
        class="mt-4"
        :total="total"
        @change="load"
      />
    </el-card>
  </Page>
</template>
