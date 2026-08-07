<script lang="ts" setup>
import type {
  Advertiser,
  AppLink,
  Channel,
  MonitorLink,
  ReportRow,
} from '#/api/adx';

import { onMounted, reactive, ref } from 'vue';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import { exportReport, getReport, getReportOptions } from '#/api/adx';

defineOptions({ name: 'AdxTimeReport' });

const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const exporting = ref(false);
const rows = ref<ReportRow[]>([]);
const channels = ref<Channel[]>([]);
const advertisers = ref<Advertiser[]>([]);
const appLinks = ref<AppLink[]>([]);
const monitors = ref<MonitorLink[]>([]);
const filters = reactive({
  advertiserId: '',
  appLinkId: '',
  channelId: '',
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  granularity: 'DATE',
  monitorId: '',
});

const metrics: Array<{
  label: string;
  minWidth?: number;
  prop: keyof ReportRow;
}> = [
  { label: '时间', minWidth: 140, prop: 'timeBucket' },
  { label: '渠道名称', minWidth: 140, prop: 'channelName' },
  { label: '监测链接', minWidth: 160, prop: 'monitorName' },
  { label: '广告主', minWidth: 140, prop: 'advertiserName' },
  { label: '应用链接', minWidth: 160, prop: 'appLinkName' },
  { label: '扣量比例', minWidth: 110, prop: 'deductionRatio' },
  { label: '展示数', minWidth: 110, prop: 'impressions' },
  { label: '点击数', minWidth: 110, prop: 'clicks' },
  { label: '点击率', minWidth: 100, prop: 'clickRate' },
  { label: '消耗', minWidth: 120, prop: 'costAmount' },
  { label: '收入', minWidth: 120, prop: 'revenueAmount' },
  { label: '原始转化', minWidth: 110, prop: 'rawConversionCount' },
  { label: '实际回传', minWidth: 110, prop: 'forwardedConversionCount' },
  { label: '扣除转化', minWidth: 110, prop: 'deductedConversionCount' },
  { label: '投递失败', minWidth: 110, prop: 'deliveryFailureCount' },
  { label: '激活', minWidth: 90, prop: 'activateCount' },
  { label: '唤起', minWidth: 90, prop: 'evokeCount' },
  { label: '登录', minWidth: 90, prop: 'loginCount' },
  { label: '注册', minWidth: 90, prop: 'registerCount' },
  { label: '付费', minWidth: 90, prop: 'paymentCount' },
  { label: '获客', minWidth: 90, prop: 'acquisitionCount' },
  { label: '次留', minWidth: 90, prop: 'nextStayCount' },
  { label: '访问', minWidth: 90, prop: 'visitCount' },
  { label: '认证', minWidth: 90, prop: 'authenticationCount' },
  { label: '下单', minWidth: 90, prop: 'orderCount' },
  { label: '首单', minWidth: 90, prop: 'firstOrderCount' },
  { label: '完单', minWidth: 90, prop: 'finishOrderCount' },
];

function query() {
  return {
    advertiserId: filters.advertiserId || undefined,
    appLinkId: filters.appLinkId || undefined,
    channelId: filters.channelId || undefined,
    current: current.value,
    endDate: filters.dates?.[1],
    granularity: filters.granularity,
    monitorId: filters.monitorId || undefined,
    size: size.value,
    startDate: filters.dates?.[0],
  };
}

async function load() {
  loading.value = true;
  try {
    const result = await getReport(query());
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadOptions() {
  const result = await getReportOptions();
  channels.value = result.channels;
  advertisers.value = result.advertisers;
  appLinks.value = result.appLinks;
  monitors.value = result.monitors;
}

function reset() {
  Object.assign(filters, {
    advertiserId: '',
    appLinkId: '',
    channelId: '',
    dates: [
      dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      dayjs().format('YYYY-MM-DD'),
    ],
    granularity: 'DATE',
    monitorId: '',
  });
  current.value = 1;
  load();
}

function display(row: ReportRow, prop: keyof ReportRow) {
  const value = row[prop] as any;
  if (prop === 'clickRate') return `${Number(value || 0).toFixed(2)}%`;
  if (prop === 'deductionRatio')
    return `${(Number(value || 0) * 100).toFixed(2)}%`;
  if (prop === 'costAmount' || prop === 'revenueAmount') {
    return Number(value || 0).toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      style: 'currency',
      currency: 'CNY',
    });
  }
  return value ?? '—';
}

async function download() {
  exporting.value = true;
  try {
    const payload = await exportReport(query());
    const blob = payload instanceof Blob ? payload : new Blob([payload as any]);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `时段报表-${dayjs().format('YYYYMMDD-HHmmss')}.xlsx`;
    link.click();
    URL.revokeObjectURL(url);
    ElMessage.success('报表已下载');
  } finally {
    exporting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([load(), loadOptions()]);
});
</script>

<template>
  <div class="report-page">
    <header>
      <div>
        <p class="eyebrow">ADX · REPORT CENTER</p>
        <h1>时段报表</h1>
        <p>按小时、日期、月份或年份汇总渠道与广告主效果数据。</p>
      </div>
      <el-button
        v-access:code="'adx:report:export'"
        :loading="exporting"
        @click="download"
      >
        下载报表
      </el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" label-position="top">
        <el-form-item label="显示类型">
          <el-select v-model="filters.granularity">
            <el-option label="按小时" value="HOUR" />
            <el-option label="按日期" value="DATE" />
            <el-option label="按月份" value="MONTH" />
            <el-option label="按年份" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="filters.channelId" clearable filterable>
            <el-option
              v-for="item in channels"
              :key="item.id"
              :label="item.channelName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="监测链接">
          <el-select v-model="filters.monitorId" clearable filterable>
            <el-option
              v-for="item in monitors"
              :key="item.id"
              :label="item.monitorName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="广告主">
          <el-select v-model="filters.advertiserId" clearable filterable>
            <el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.advertiserName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="广告主应用链接">
          <el-select v-model="filters.appLinkId" clearable filterable>
            <el-option
              v-for="item in appLinks"
              :key="item.id"
              :label="item.linkName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="filters.dates"
            end-placeholder="结束日期"
            range-separator="至"
            start-placeholder="开始日期"
            type="daterange"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item class="actions" label=" ">
          <el-button type="primary" :loading="loading" @click="load"
            >查询</el-button
          >
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="rows"
        border
        height="calc(100vh - 410px)"
      >
        <el-table-column
          v-for="column in metrics"
          :key="column.prop"
          :label="column.label"
          :min-width="column.minWidth"
          :prop="column.prop"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{
            display(row, column.prop)
          }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.report-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

header {
  display: flex;
  gap: 20px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

header h1 {
  margin: 2px 0 6px;
  font-size: 24px;
  font-weight: 650;
  color: #172033;
}

header p {
  margin: 0;
  color: #667085;
}

.eyebrow {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb !important;
  letter-spacing: 0.14em;
}

.filter-card,
.table-card {
  border-color: #e6eaf0;
}

.filter-card {
  margin-bottom: 14px;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-card :deep(.el-select) {
  width: 170px;
}

.filter-card :deep(.el-date-editor) {
  width: 250px;
}

.table-card :deep(.el-card__body) {
  padding-top: 14px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 768px) {
  .report-page {
    padding: 12px;
  }

  header {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-card :deep(.el-form-item),
  .filter-card :deep(.el-select),
  .filter-card :deep(.el-date-editor) {
    width: 100%;
  }
}
</style>
