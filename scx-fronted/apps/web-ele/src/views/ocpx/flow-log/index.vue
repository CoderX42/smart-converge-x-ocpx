<script lang="ts" setup>
import type { FlowLog } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import { getFlowLogs } from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxFlowLog' });

const route = useRoute();

const columns = [
  { key: 'time', label: '时间' },
  { key: 'flow', label: '流程' },
  { key: 'event', label: '事件' },
  { key: 'advertiser', label: '广告主' },
  { key: 'link', label: '推广链接' },
  { key: 'media', label: '媒体' },
  { key: 'monitor', label: '监测任务' },
  { key: 'result', label: '结果' },
  { key: 'http', label: 'HTTP' },
  { key: 'duration', label: '耗时' },
  { key: 'message', label: '说明' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<FlowLog[]>([]);
const loading = ref(false);
const total = ref(0);
const showSearch = ref(true);
const filters = reactive({
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string] | null,
  eventId: typeof route.query.eventId === 'string' ? route.query.eventId : '',
  flowType: '',
  resultStatus: '',
  size: 30,
});
const loadGuard = createLatestRequestGuard();

function currentDates(): [string, string] {
  if (filters.dates?.[0] && filters.dates[1]) return filters.dates;
  filters.dates = [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ];
  return filters.dates;
}

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  const dates = currentDates();
  loading.value = true;
  try {
    const result = await getFlowLogs({
      current: filters.current,
      endDate: dates[1],
      eventId: filters.eventId || undefined,
      flowType: filters.flowType || undefined,
      resultStatus: filters.resultStatus || undefined,
      size: filters.size,
      startDate: dates[0],
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

function search() {
  resetPage(filters);
  void load();
}

function reset() {
  Object.assign(filters, {
    dates: [
      dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      dayjs().format('YYYY-MM-DD'),
    ],
    eventId: '',
    flowType: '',
    resultStatus: '',
  });
  search();
}

onMounted(load);
</script>

<template>
  <Page auto-content-height title="流程日志">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-date-picker
          v-model="filters.dates"
          aria-label="流程日志日期范围"
          :clearable="false"
          end-placeholder="结束日期"
          range-separator="至"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="search"
        />
        <el-select
          v-model="filters.flowType"
          aria-label="流程类型"
          clearable
          placeholder="流程"
          @change="search"
          ><el-option label="Feedback" value="FEEDBACK" /><el-option
            label="Callback"
            value="CALLBACK"
        /></el-select>
        <el-select
          v-model="filters.resultStatus"
          aria-label="流程结果"
          clearable
          placeholder="结果"
          @change="search"
          ><el-option label="成功" value="SUCCESS" /><el-option
            label="失败"
            value="FAILED"
        /></el-select>
        <el-input
          v-model="filters.eventId"
          aria-label="事件 ID"
          clearable
          placeholder="事件 ID"
          @clear="search"
          @keyup.enter="search"
        />
        <el-button :disabled="loading" type="primary" @click="search"
          >查询</el-button
        >
        <el-button :disabled="loading" @click="reset">重置</el-button>
      </div>
      <div class="ocpx-actions">
        <span></span>
        <RightToolbar
          v-model:visible-columns="visibleColumns"
          :columns="columns"
          :loading="loading"
          refresh-btn
          search-btn
          @refresh="load"
          @search="showSearch = !showSearch"
        />
      </div>
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column
          v-if="hasColumn('time')"
          label="时间"
          min-width="180"
          prop="eventTime"
        />
        <el-table-column
          v-if="hasColumn('flow')"
          label="流程"
          min-width="110"
          prop="flowType"
        />
        <el-table-column v-if="hasColumn('event')" label="事件" min-width="210"
          ><template #default="{ row }"
            ><LongText :value="`${row.eventCode} / ${row.eventId}`" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('advertiser')"
          label="广告主"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('link')"
          label="推广链接"
          min-width="150"
          prop="promotionLinkName"
        />
        <el-table-column
          v-if="hasColumn('media')"
          label="媒体"
          min-width="140"
          prop="mediaName"
        />
        <el-table-column
          v-if="hasColumn('monitor')"
          label="监测任务"
          min-width="150"
          prop="monitorTaskName"
        />
        <el-table-column
          v-if="hasColumn('result')"
          label="结果"
          min-width="100"
          prop="resultStatus"
        />
        <el-table-column
          v-if="hasColumn('http')"
          label="HTTP"
          min-width="80"
          prop="partnerHttpStatus"
        />
        <el-table-column
          v-if="hasColumn('duration')"
          label="耗时(ms)"
          min-width="100"
          prop="durationMs"
        />
        <el-table-column
          v-if="hasColumn('message')"
          label="说明"
          min-width="220"
          ><template #default="{ row }"
            ><LongText :value="row.message" /></template
        ></el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="filters.current"
        v-model:page-size="filters.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="total"
        @change="load"
      />
    </el-card>
  </Page>
</template>
