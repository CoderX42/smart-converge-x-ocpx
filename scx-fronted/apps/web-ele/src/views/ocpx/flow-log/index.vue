<script lang="ts" setup>
import type { FlowLog } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import { getFlowLogs } from '#/api/ocpx';

defineOptions({ name: 'OcpxFlowLog' });

const rows = ref<FlowLog[]>([]);
const loading = ref(false);
const total = ref(0);
const filters = reactive({
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  eventId: '',
  flowType: '',
  resultStatus: '',
  size: 20,
});

async function load() {
  loading.value = true;
  try {
    const result = await getFlowLogs({
      current: filters.current,
      endDate: filters.dates[1],
      eventId: filters.eventId || undefined,
      flowType: filters.flowType || undefined,
      resultStatus: filters.resultStatus || undefined,
      size: filters.size,
      startDate: filters.dates[0],
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Page auto-content-height title="流程日志">
    <el-card shadow="never">
      <div class="mb-4 flex flex-wrap gap-3">
        <el-date-picker
          v-model="filters.dates"
          type="daterange"
          value-format="YYYY-MM-DD"
        />
        <el-select v-model="filters.flowType" clearable placeholder="流程">
          <el-option label="Feedback" value="FEEDBACK" />
          <el-option label="Callback" value="CALLBACK" />
        </el-select>
        <el-select v-model="filters.resultStatus" clearable placeholder="结果">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-input v-model="filters.eventId" clearable placeholder="事件 ID" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="时间" min-width="180" prop="createTime" />
        <el-table-column label="流程" prop="flowType" />
        <el-table-column label="事件" min-width="180">
          <template #default="{ row }">
            {{ row.eventCode }} / {{ row.eventId }}
          </template>
        </el-table-column>
        <el-table-column label="广告主" prop="advertiserName" />
        <el-table-column label="推广链接" prop="promotionLinkName" />
        <el-table-column label="媒体" prop="mediaName" />
        <el-table-column label="监测任务" prop="monitorTaskName" />
        <el-table-column label="结果" prop="resultStatus" />
        <el-table-column label="HTTP" prop="partnerHttpStatus" width="80" />
        <el-table-column label="耗时(ms)" prop="durationMs" width="100" />
        <el-table-column label="说明" min-width="220" prop="message" />
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
