<script lang="ts" setup>
import type {
  MonitorTask,
  PromotionLink,
  ReportOption,
  SummaryReportRow,
} from '#/api/ocpx';

import { onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import {
  exportSummaryReport,
  getSummaryReport,
  getUnifiedReportOptions,
  listMonitorTasks,
  listPromotionLinks,
} from '#/api/ocpx';

defineOptions({ name: 'OcpxUnifiedReport' });

type SummaryColumn = {
  field: keyof SummaryReportRow;
  title: string;
};

const columns: SummaryColumn[] = [
  { field: 'reportTime', title: '时间' },
  { field: 'mediaName', title: '渠道名称' },
  { field: 'taskName', title: '渠道链接信息' },
  { field: 'advertiserName', title: 'DSP名称' },
  { field: 'promotionLinkName', title: 'DSP链接信息' },
  { field: 'linkRadio', title: 'DSP链接扣量' },
  { field: 'uniShowSmt', title: '展示数' },
  { field: 'uniClkSmt', title: '点击数' },
  { field: 'clickRate', title: '点击率' },
  { field: 'basePrice', title: '消耗' },
  { field: 'activate', title: '激活' },
  { field: 'evoke', title: '唤起' },
  { field: 'login', title: '登录' },
  { field: 'register', title: '注册' },
  { field: 'payment', title: '付费' },
  { field: 'customerAcquisition', title: '获客' },
  { field: 'nextStay', title: '次留' },
  { field: 'visits', title: '访问' },
  { field: 'authentication', title: '认证' },
  { field: 'placeOrder', title: '下单' },
  { field: 'finishTheOrder', title: '完单' },
];

const SORTABLE_FIELDS = new Set(['reportTime', 'uniClkSmt', 'uniShowSmt']);

const dateShortcuts = [
  {
    text: '今天',
    value: () => [new Date(), new Date()],
  },
  {
    text: '昨天',
    value: () => [
      dayjs().subtract(1, 'day').toDate(),
      dayjs().subtract(1, 'day').toDate(),
    ],
  },
  {
    text: '最近一周',
    value: () => [dayjs().subtract(6, 'day').toDate(), new Date()],
  },
  {
    text: '最近一个月',
    value: () => [
      dayjs().startOf('month').toDate(),
      dayjs().endOf('month').toDate(),
    ],
  },
  {
    text: '最近二个月',
    value: () => [
      dayjs().subtract(1, 'month').startOf('month').toDate(),
      dayjs().endOf('month').toDate(),
    ],
  },
  {
    text: '本月',
    value: () => [
      dayjs().startOf('month').toDate(),
      dayjs().endOf('month').toDate(),
    ],
  },
  {
    text: '上月',
    value: () => [
      dayjs().subtract(1, 'month').startOf('month').toDate(),
      dayjs().subtract(1, 'month').endOf('month').toDate(),
    ],
  },
];

const rows = ref<SummaryReportRow[]>([]);
const total = ref(0);
const loading = ref(false);
const mediaOptions = ref<ReportOption[]>([]);
const advertiserOptions = ref<ReportOption[]>([]);
const taskOptions = ref<MonitorTask[]>([]);
const linkOptions = ref<PromotionLink[]>([]);
const filters = reactive({
  advertiserId: '',
  asc: '',
  current: 1,
  dateType: 1,
  dates: [dayjs().format('YYYYMMDD'), dayjs().format('YYYYMMDD')] as [
    string,
    string,
  ],
  desc: '',
  mediaId: '',
  promotionLinkId: '',
  size: 20,
  taskId: '',
});

function camelToSnake(value: string) {
  return value.replaceAll(/([A-Z])/g, '_$1').toLowerCase();
}

function currentParams() {
  return {
    advertiserId: filters.advertiserId || undefined,
    dateType: filters.dateType,
    endTime: filters.dates[1],
    mediaId: filters.mediaId || undefined,
    promotionLinkId: filters.promotionLinkId || undefined,
    startTime: filters.dates[0],
    taskId: filters.taskId || undefined,
  };
}

async function load() {
  loading.value = true;
  try {
    const result = await getSummaryReport({
      ...currentParams(),
      asc: filters.asc || undefined,
      current: filters.current,
      desc: filters.desc || undefined,
      size: filters.size,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function toQuery() {
  filters.current = 1;
  load();
}

function download() {
  exportSummaryReport(currentParams());
}

function sortChange({
  prop,
  order,
}: {
  order: 'ascending' | 'descending' | null;
  prop: string;
}) {
  filters.asc = '';
  filters.desc = '';
  if (order === 'ascending') {
    filters.asc = camelToSnake(prop);
  } else if (order === 'descending') {
    filters.desc = camelToSnake(prop);
  }
  load();
}

function sumField(data: SummaryReportRow[], field: keyof SummaryReportRow) {
  return data.reduce((acc, item) => acc + Number(item[field]), 0);
}

function tableSummary({ data }: { data: SummaryReportRow[] }) {
  const out: (number | string)[] = [];
  for (const [i, column] of columns.entries()) {
    if (i === 0) {
      out[i] = '合计';
    } else if (column.field === 'clickRate') {
      const shows = sumField(data, 'uniShowSmt');
      out.push(
        shows === 0
          ? '0.00'
          : ((sumField(data, 'uniClkSmt') / shows) * 100).toFixed(2),
      );
    } else if (column.field === 'uniShowSmt' || column.field === 'uniClkSmt') {
      out.push(sumField(data, column.field));
    } else {
      out.push('-');
    }
  }
  return out;
}

async function loadTasks() {
  const page = await listMonitorTasks({
    current: 1,
    mediaId: filters.mediaId || undefined,
    size: 500,
  });
  taskOptions.value = page.records;
}

async function loadLinks() {
  const page = await listPromotionLinks({
    advertiserId: filters.advertiserId || undefined,
    current: 1,
    size: 500,
  });
  linkOptions.value = page.records;
}

watch(
  () => filters.mediaId,
  () => {
    filters.taskId = '';
    loadTasks();
  },
);

watch(
  () => filters.advertiserId,
  () => {
    filters.promotionLinkId = '';
    loadLinks();
  },
);

onMounted(async () => {
  const options = await getUnifiedReportOptions();
  mediaOptions.value = options.media;
  advertiserOptions.value = options.advertisers;
  await Promise.all([loadTasks(), loadLinks()]);
  await load();
});
</script>

<template>
  <Page auto-content-height title="报表中心">
    <el-card shadow="never">
      <div class="mb-4 flex flex-wrap gap-3">
        <el-select
          v-model="filters.dateType"
          placeholder="显示类型"
          style="width: 110px"
        >
          <el-option label="按小时" :value="0" />
          <el-option label="按日期" :value="1" />
          <el-option label="按月份" :value="2" />
          <el-option label="按年份" :value="3" />
        </el-select>
        <el-select
          v-model="filters.mediaId"
          clearable
          filterable
          placeholder="请选择渠道"
        >
          <el-option
            v-for="item in mediaOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.taskId"
          clearable
          filterable
          placeholder="请选择监测链接"
        >
          <el-option
            v-for="item in taskOptions"
            :key="item.id"
            :label="item.taskName"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.advertiserId"
          clearable
          filterable
          placeholder="请选择DSP"
        >
          <el-option
            v-for="item in advertiserOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.promotionLinkId"
          clearable
          filterable
          placeholder="请选择广告主应用链接"
        >
          <el-option
            v-for="item in linkOptions"
            :key="item.id"
            :label="item.linkName"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="filters.dates"
          type="daterange"
          unlink-panels
          value-format="YYYYMMDD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :shortcuts="dateShortcuts"
        />
        <el-button type="primary" @click="toQuery">搜索</el-button>
      </div>
      <div class="mb-4">
        <el-button type="warning" size="small" @click="download">
          下载
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        show-summary
        :summary-method="tableSummary"
        @sort-change="sortChange"
      >
        <el-table-column
          v-for="column in columns"
          :key="column.field"
          :label="column.title"
          :prop="column.field"
          :sortable="SORTABLE_FIELDS.has(column.field) ? 'custom' : false"
          align="center"
        >
          <template #default="{ row }">
            <el-tag v-if="column.field === 'clickRate'" type="info">
              {{ row.clickRate }}%
            </el-tag>
            <span v-else>{{ row[column.field] }}</span>
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
