<script lang="ts" setup>
import type { EntityOption, ReportOption, SummaryReportRow } from '#/api/ocpx';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import {
  exportSummaryReport,
  getSummaryReport,
  getUnifiedReportOptions,
  monitorTaskOptions,
  promotionLinkOptions,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxUnifiedReport' });

type SummaryColumn = {
  field: keyof SummaryReportRow;
  key: string;
  label: string;
  width?: number;
};

const columns: SummaryColumn[] = [
  { field: 'reportTime', key: 'reportTime', label: '时间', width: 130 },
  { field: 'mediaName', key: 'mediaName', label: '媒体', width: 140 },
  { field: 'monitorName', key: 'monitorName', label: '监测任务', width: 150 },
  {
    field: 'advertiserName',
    key: 'advertiserName',
    label: '广告主',
    width: 150,
  },
  {
    field: 'promotionLinkName',
    key: 'promotionLinkName',
    label: '推广链接',
    width: 160,
  },
  {
    field: 'linkRadio',
    key: 'linkRadio',
    label: '媒体回传比例',
    width: 130,
  },
  {
    field: 'callbackCount',
    key: 'callbackCount',
    label: '原始Callback',
    width: 120,
  },
  {
    field: 'deductionPassedCount',
    key: 'deductionPassedCount',
    label: '扣量放行',
    width: 110,
  },
  {
    field: 'deductionDeductedCount',
    key: 'deductionDeductedCount',
    label: '扣量拦截',
    width: 110,
  },
  {
    field: 'deductionPendingCount',
    key: 'deductionPendingCount',
    label: '待判定',
    width: 100,
  },
  { field: 'uniShowSmt', key: 'uniShowSmt', label: '展示', width: 100 },
  { field: 'uniClkSmt', key: 'uniClkSmt', label: '点击', width: 100 },
  { field: 'clickRate', key: 'clickRate', label: 'CTR', width: 90 },
  {
    field: 'basePrice',
    key: 'basePrice',
    label: '广告主消费',
    width: 120,
  },
  { field: 'activate', key: 'activate', label: '激活', width: 90 },
  { field: 'evoke', key: 'evoke', label: '唤起', width: 90 },
  { field: 'login', key: 'login', label: '登录', width: 90 },
  { field: 'register', key: 'register', label: '注册', width: 90 },
  { field: 'payment', key: 'payment', label: '付费', width: 90 },
  {
    field: 'customerAcquisition',
    key: 'customerAcquisition',
    label: '获客',
    width: 90,
  },
  { field: 'nextStay', key: 'nextStay', label: '次留', width: 90 },
  { field: 'visits', key: 'visits', label: '访问', width: 90 },
  { field: 'authentication', key: 'authentication', label: '认证', width: 90 },
  { field: 'placeOrder', key: 'placeOrder', label: '下单', width: 90 },
  { field: 'finishTheOrder', key: 'finishTheOrder', label: '完单', width: 90 },
  {
    field: 'firstActivation',
    key: 'firstActivation',
    label: '首活',
    width: 90,
  },
  { field: 'firstOrder', key: 'firstOrder', label: '首购', width: 90 },
  {
    field: 'currencyAmount',
    key: 'currencyAmount',
    label: '订单金额',
    width: 120,
  },
];
const visibleColumns = ref(allColumnKeys(columns));
const sortableFields = new Set(['reportTime', 'uniClkSmt', 'uniShowSmt']);
const additiveFields = new Set<keyof SummaryReportRow>([
  'activate',
  'authentication',
  'basePrice',
  'callbackCount',
  'currencyAmount',
  'customerAcquisition',
  'deductionDeductedCount',
  'deductionPassedCount',
  'deductionPendingCount',
  'evoke',
  'finishTheOrder',
  'firstActivation',
  'firstOrder',
  'login',
  'nextStay',
  'payment',
  'placeOrder',
  'register',
  'uniClkSmt',
  'uniShowSmt',
  'visits',
]);

const dateShortcuts = [
  { text: '今天', value: () => [new Date(), new Date()] },
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
    text: '本月',
    value: () => [dayjs().startOf('month').toDate(), new Date()],
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
const deductionCoverageStart = ref<null | string>(null);
const deductionCoverageStatus = ref<'FULL' | 'NONE' | 'PARTIAL'>('NONE');
const loading = ref(false);
const exporting = ref(false);
const showSearch = ref(true);
const mediaOptions = ref<ReportOption[]>([]);
const advertiserOptions = ref<ReportOption[]>([]);
const monitorOptions = ref<EntityOption[]>([]);
const linkOptions = ref<EntityOption[]>([]);
const { hasAccessByCodes } = useAccess();
const canExport = computed(() => hasAccessByCodes(['ocpx:report:export']));
const filters = reactive({
  advertiserId: '',
  asc: '',
  current: 1,
  dateType: 1,
  dates: [dayjs().format('YYYYMMDD'), dayjs().format('YYYYMMDD')] as
    [string, string] | null,
  desc: '',
  mediaId: '',
  monitorTaskId: '',
  promotionLinkId: '',
  size: 30,
});
const loadGuard = createLatestRequestGuard();
const monitorOptionGuard = createLatestRequestGuard();
const linkOptionGuard = createLatestRequestGuard();

function currentDates(): [string, string] {
  if (filters.dates?.[0] && filters.dates[1]) return filters.dates;
  const today = dayjs().format('YYYYMMDD');
  filters.dates = [today, today];
  return filters.dates;
}

function currentParams() {
  const dates = currentDates();
  return {
    advertiserId: filters.advertiserId || undefined,
    dateType: filters.dateType,
    endTime: dates[1],
    mediaId: filters.mediaId || undefined,
    monitorTaskId: filters.monitorTaskId || undefined,
    promotionLinkId: filters.promotionLinkId || undefined,
    startTime: dates[0],
  };
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await getSummaryReport({
      ...currentParams(),
      asc: filters.asc || undefined,
      current: filters.current,
      desc: filters.desc || undefined,
      size: filters.size,
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
      deductionCoverageStart.value = result.deductionCoverageStart;
      deductionCoverageStatus.value = result.deductionCoverageStatus;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

function search() {
  resetPage(filters);
  void load();
}

async function download() {
  exporting.value = true;
  try {
    await exportSummaryReport(currentParams());
  } finally {
    exporting.value = false;
  }
}

function sortChange({
  order,
  prop,
}: {
  order: 'ascending' | 'descending' | null;
  prop: string;
}) {
  const field = prop.replaceAll(/([A-Z])/g, '_$1').toLowerCase();
  filters.asc = order === 'ascending' ? field : '';
  filters.desc = order === 'descending' ? field : '';
  search();
}

function sumField(data: SummaryReportRow[], field: keyof SummaryReportRow) {
  return data.reduce((sum, item) => sum + Number(item[field] || 0), 0);
}

function callbackRatio(value: null | string) {
  const ratio = value === null ? 1 : Number(value);
  return `${Math.round(ratio * 10_000) / 100}%`;
}

const coverageMessage = computed(() => {
  if (deductionCoverageStatus.value === 'NONE') {
    return '所选范围无新口径扣量事实；历史数据不使用当前比例反推。';
  }
  const start = deductionCoverageStart.value
    ? dayjs(deductionCoverageStart.value).format('YYYY-MM-DD HH:mm:ss')
    : '-';
  return deductionCoverageStatus.value === 'PARTIAL'
    ? `扣量数据部分覆盖，真实统计从 ${start} 开始；此前数据未估算。`
    : `扣量数据已覆盖所选范围，统计起始时间：${start}。`;
});

function tableSummary({
  columns: renderedColumns,
  data,
}: {
  columns: Array<{ property?: keyof SummaryReportRow }>;
  data: SummaryReportRow[];
}) {
  return renderedColumns.map(({ property }, index) => {
    if (index === 0) return '合计';
    if (!property) return '-';
    if (property === 'clickRate') {
      const shows = sumField(data, 'uniShowSmt');
      return shows
        ? `${((sumField(data, 'uniClkSmt') / shows) * 100).toFixed(2)}%`
        : '0.00%';
    }
    return additiveFields.has(property) ? sumField(data, property) : '-';
  });
}

async function loadMonitors() {
  filters.monitorTaskId = '';
  const requestSequence = monitorOptionGuard.begin();
  const result = await monitorTaskOptions(filters.mediaId || undefined);
  if (monitorOptionGuard.isLatest(requestSequence)) {
    monitorOptions.value = result;
  }
}

async function loadLinks() {
  filters.promotionLinkId = '';
  const requestSequence = linkOptionGuard.begin();
  const result = await promotionLinkOptions(filters.advertiserId || undefined);
  if (linkOptionGuard.isLatest(requestSequence)) linkOptions.value = result;
}

watch(
  () => filters.mediaId,
  async () => {
    await loadMonitors();
    search();
  },
);
watch(
  () => filters.advertiserId,
  async () => {
    await loadLinks();
    search();
  },
);

onMounted(async () => {
  const options = await getUnifiedReportOptions();
  mediaOptions.value = options.media;
  advertiserOptions.value = options.advertisers;
  await Promise.all([loadMonitors(), loadLinks()]);
  await load();
});
</script>

<template>
  <Page auto-content-height title="时段报表">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="filters.dateType"
          aria-label="报表粒度"
          placeholder="报表粒度"
          @change="search"
        >
          <el-option label="小时" :value="0" /><el-option
            label="日"
            :value="1"
          />
          <el-option label="月" :value="2" /><el-option label="年" :value="3" />
        </el-select>
        <el-select
          v-model="filters.mediaId"
          aria-label="报表媒体"
          clearable
          filterable
          placeholder="媒体"
          ><el-option
            v-for="item in mediaOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
        /></el-select>
        <el-select
          v-model="filters.monitorTaskId"
          aria-label="报表监测任务"
          clearable
          filterable
          placeholder="监测任务"
          @change="search"
          ><el-option
            v-for="item in monitorOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
        /></el-select>
        <el-select
          v-model="filters.advertiserId"
          aria-label="报表广告主"
          clearable
          filterable
          placeholder="广告主"
          ><el-option
            v-for="item in advertiserOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
        /></el-select>
        <el-select
          v-model="filters.promotionLinkId"
          aria-label="报表推广链接"
          clearable
          filterable
          placeholder="推广链接"
          @change="search"
          ><el-option
            v-for="item in linkOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
        /></el-select>
        <el-date-picker
          v-model="filters.dates"
          aria-label="报表日期范围"
          :clearable="false"
          end-placeholder="结束日期"
          range-separator="至"
          start-placeholder="开始日期"
          :shortcuts="dateShortcuts"
          type="daterange"
          unlink-panels
          value-format="YYYYMMDD"
          @change="search"
        />
        <el-button :disabled="loading" type="primary" @click="search"
          >查询</el-button
        >
      </div>
      <div class="ocpx-actions">
        <span></span>
        <RightToolbar
          v-model:visible-columns="visibleColumns"
          :columns="columns"
          :export-btn="canExport"
          :loading="loading || exporting"
          refresh-btn
          search-btn
          @export="download"
          @refresh="load"
          @search="showSearch = !showSearch"
        />
      </div>
      <el-alert
        class="mb-3"
        :closable="false"
        :title="coverageMessage"
        :type="deductionCoverageStatus === 'FULL' ? 'success' : 'warning'"
        show-icon
      />
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        show-summary
        size="small"
        :summary-method="tableSummary"
        @sort-change="sortChange"
      >
        <el-table-column
          v-for="column in columns.filter(({ key }) =>
            visibleColumns.includes(key),
          )"
          :key="column.key"
          align="center"
          :label="column.label"
          :min-width="column.width"
          :prop="column.field"
          :sortable="sortableFields.has(column.field) ? 'custom' : false"
        >
          <template #default="{ row }"
            ><span v-if="column.field === 'clickRate'">{{
              row.clickRate === null ? '-' : `${row.clickRate}%`
            }}</span
            ><span v-else-if="column.field === 'linkRadio'">{{
              callbackRatio(row.linkRadio)
            }}</span
            ><span v-else>{{ row[column.field] ?? '-' }}</span></template
          >
        </el-table-column>
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
