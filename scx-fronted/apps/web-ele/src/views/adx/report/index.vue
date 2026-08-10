<script lang="ts" setup>
import type {
  ReportExportTask,
  UnifiedReportMetricCode,
  UnifiedReportOptions,
  UnifiedReportQuery,
  UnifiedReportRecord,
} from '#/api/adx';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@boot/access';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  cancelReportExport,
  downloadReportExport,
  exportDeductionReport,
  exportReport,
  getReport,
  getReportExport,
  getUnifiedReportOptions,
} from '#/api/adx';

import {
  formatReportMetric,
  reportEmptyKind,
  reportGroupBy,
} from './report-model';

defineOptions({ name: 'AdxUnifiedReport' });

type MetricColumn = {
  code: UnifiedReportMetricCode;
  label: string;
  minWidth: number;
  ratio?: boolean;
  sensitive?: boolean;
};

const route = useRoute();
const { hasAccessByCodes } = useAccess();
const canViewSensitiveMetrics = computed(() =>
  hasAccessByCodes(['adx:report:metric:sensitive']),
);
const loading = ref(false);
const optionsLoading = ref(false);
const rows = ref<UnifiedReportRecord[]>([]);
const total = ref(0);
const current = ref(1);
const size = ref(20);
const asOf = ref<string>();
const freshness = ref<'DELAYED' | 'FINAL' | 'PROVISIONAL'>('FINAL');
const errorMessage = ref('');
const traceId = ref('');
const forbidden = ref(false);
const firstRequestDone = ref(false);
const filterTouched = ref(false);
const pageSummary = ref<Record<string, { status: string; value?: number }>>({});
const querySummary = ref<Record<string, { status: string; value?: number }>>(
  {},
);
const metricVersion = ref('S8_V1');
const exportTask = ref<ReportExportTask>();
const exportDialogOpen = ref(false);
const exportBusy = ref(false);
const options = reactive<UnifiedReportOptions>({
  advertisers: [],
  appLinks: [],
  channels: [],
  mediaAccounts: [],
  products: [],
  tasks: [],
});

const allColumns: MetricColumn[] = [
  { code: 'impressions', label: '曝光', minWidth: 108 },
  { code: 'rawClicks', label: '原始点击', minWidth: 112 },
  { code: 'validClicks', label: '有效点击', minWidth: 112 },
  { code: 'duplicateClicks', label: '重复点击', minWidth: 112 },
  { code: 'activations', label: '激活', minWidth: 96 },
  { code: 'registrations', label: '注册', minWidth: 96 },
  { code: 'payments', label: '付费', minWidth: 96 },
  { code: 'orders', label: '订单', minWidth: 96 },
  {
    code: 'activationRate',
    label: '激活率',
    minWidth: 104,
    ratio: true,
  },
  {
    code: 'registrationRate',
    label: '注册率',
    minWidth: 104,
    ratio: true,
  },
  { code: 'paymentRate', label: '付费率', minWidth: 104, ratio: true },
  {
    code: 'costAmount',
    label: '消耗',
    minWidth: 120,
    sensitive: true,
  },
];

const visibleCodes = ref<UnifiedReportMetricCode[]>(
  allColumns.filter((item) => !item.sensitive).map((item) => item.code),
);

const availableColumns = computed(() =>
  allColumns.filter((item) => !item.sensitive || canViewSensitiveMetrics.value),
);

const filters = reactive({
  advertiserId: '',
  appLinkId: '',
  channelId: '',
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  grain: 'DAY' as 'DAY' | 'HOUR',
  mediaAccountId: '',
  mediaLevel: 'ACCOUNT' as 'ACCOUNT' | 'CHANNEL',
  productId: '',
  taskId: '',
});

const groupBy = computed<UnifiedReportQuery['groupBy']>(() => {
  return reportGroupBy(route.path);
});

const title = computed(
  () =>
    ({
      LINK: '链接汇总',
      MEDIA: '媒体汇总',
      PRODUCT: '产品汇总',
      TASK: '任务汇总',
      TIME: '数据汇总',
    })[groupBy.value],
);

const visibleColumns = computed(() =>
  availableColumns.value.filter((item) =>
    visibleCodes.value.includes(item.code),
  ),
);

const emptyKind = computed(() => {
  return reportEmptyKind({
    filterTouched: filterTouched.value,
    firstRequestDone: firstRequestDone.value,
    loading: loading.value,
    rowCount: rows.value.length,
  });
});

function payload(): UnifiedReportQuery {
  return {
    advertiserId: filters.advertiserId || undefined,
    appLinkId: filters.appLinkId || undefined,
    channelId: filters.channelId || undefined,
    current: current.value,
    direction: 'DESC',
    endDate: filters.dates[1],
    grain: filters.grain,
    groupBy: groupBy.value,
    mediaAccountId: filters.mediaAccountId || undefined,
    mediaLevel: filters.mediaLevel,
    metrics: visibleCodes.value,
    productId: filters.productId || undefined,
    size: size.value,
    sort: 'dimension',
    startDate: filters.dates[0],
    taskId: filters.taskId || undefined,
  };
}

function dimensionValue(row: UnifiedReportRecord, key: string) {
  const value = row.dimensions[key];
  return value === undefined || value === null ? '' : String(value);
}

function errorDetails(error: any) {
  const status = Number(error?.response?.status || error?.status || 0);
  forbidden.value = status === 403;
  errorMessage.value =
    error?.response?.data?.msg ||
    error?.data?.msg ||
    error?.message ||
    '报表加载失败';
  traceId.value =
    error?.response?.headers?.['x-trace-id'] ||
    error?.response?.data?.traceId ||
    '';
}

async function load() {
  loading.value = true;
  errorMessage.value = '';
  forbidden.value = false;
  try {
    const result = await getReport(payload());
    rows.value = result.records;
    total.value = result.total;
    asOf.value = result.asOf;
    freshness.value = result.freshnessStatus;
    pageSummary.value = result.pageSummary.metrics;
    querySummary.value = result.querySummary.metrics;
    metricVersion.value = result.metricDefinitionsVersion;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    errorDetails(error);
  } finally {
    loading.value = false;
    firstRequestDone.value = true;
  }
}

async function loadOptions() {
  optionsLoading.value = true;
  try {
    Object.assign(options, await getUnifiedReportOptions());
  } catch (error) {
    errorDetails(error);
  } finally {
    optionsLoading.value = false;
  }
}

function search() {
  filterTouched.value = true;
  current.value = 1;
  load();
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
    grain: 'DAY',
    mediaAccountId: '',
    mediaLevel: 'ACCOUNT',
    productId: '',
    taskId: '',
  });
  filterTouched.value = false;
  current.value = 1;
  load();
}

function display(value: null | number | undefined, column: MetricColumn) {
  return formatReportMetric(value, column.code);
}

function summaryValue(
  summary: Record<string, { status: string; value?: number }>,
  column: MetricColumn,
) {
  return display(summary[column.code]?.value, column);
}

async function createExport(deduction = false) {
  exportBusy.value = true;
  try {
    exportTask.value = deduction
      ? await exportDeductionReport(payload())
      : await exportReport(payload());
    exportDialogOpen.value = true;
    ElMessage.success('已创建异步导出任务；页面不会同步生成或下载文件');
  } finally {
    exportBusy.value = false;
  }
}

async function refreshExport() {
  if (!exportTask.value) return;
  exportTask.value = await getReportExport(exportTask.value.id);
}

async function cancelExport() {
  if (!exportTask.value) return;
  exportTask.value = await cancelReportExport(exportTask.value.id);
  ElMessage.success('导出任务已取消');
}

async function downloadExport() {
  if (!exportTask.value || exportTask.value.status !== 'SUCCEEDED') return;
  const blob = await downloadReportExport(exportTask.value.id);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `scx-report-${exportTask.value.id}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}

watch(
  () => route.path,
  () => {
    current.value = 1;
    filterTouched.value = false;
    load();
  },
);

onMounted(() => Promise.all([loadOptions(), load()]));
</script>

<template>
  <div class="p-5">
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-primary m-0 text-xs font-semibold tracking-widest">
            ADX · UNIFIED HOURLY FACT · {{ metricVersion }}
          </p>
          <h2 class="mb-0 mt-2 text-xl font-semibold">{{ title }}</h2>
          <p class="mb-0 mt-2 text-sm text-gray-500">
            五个视图读取同一冻结事实与指标字典；历史归属不回读可变主数据。
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <el-dropdown trigger="click">
            <el-button>列设置</el-button>
            <template #dropdown>
              <el-dropdown-menu class="p-3">
                <el-checkbox-group v-model="visibleCodes" class="grid gap-2">
                  <el-checkbox
                    v-for="column in availableColumns"
                    :key="column.code"
                    :label="column.code"
                  >
                    {{ column.label }}
                    <span v-if="column.sensitive" class="text-xs text-gray-400">
                      · 敏感权限
                    </span>
                  </el-checkbox>
                </el-checkbox-group>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button
            v-access:code="'adx:report:deduction:export'"
            :loading="exportBusy"
            @click="createExport(true)"
          >
            审减导出
          </el-button>
          <el-button
            v-access:code="'adx:report:export'"
            :loading="exportBusy"
            type="primary"
            @click="createExport(false)"
          >
            异步导出
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="mb-4" shadow="never">
      <el-form label-position="top" @submit.prevent="search">
        <div class="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
          <el-form-item label="粒度">
            <el-select v-model="filters.grain">
              <el-option label="小时" value="HOUR" />
              <el-option label="日期" value="DAY" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="groupBy === 'MEDIA'" label="媒体层级">
            <el-select v-model="filters.mediaLevel">
              <el-option label="媒体账号" value="ACCOUNT" />
              <el-option label="渠道" value="CHANNEL" />
            </el-select>
          </el-form-item>
          <el-form-item label="广告主">
            <el-select
              v-model="filters.advertiserId"
              clearable
              filterable
              :loading="optionsLoading"
            >
              <el-option
                v-for="item in options.advertisers"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="产品">
            <el-select v-model="filters.productId" clearable filterable>
              <el-option
                v-for="item in options.products"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="AppLink">
            <el-select v-model="filters.appLinkId" clearable filterable>
              <el-option
                v-for="item in options.appLinks"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Task">
            <el-select v-model="filters.taskId" clearable filterable>
              <el-option
                v-for="item in options.tasks"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="渠道">
            <el-select v-model="filters.channelId" clearable filterable>
              <el-option
                v-for="item in options.channels"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="媒体账号">
            <el-select v-model="filters.mediaAccountId" clearable filterable>
              <el-option
                v-for="item in options.mediaAccounts"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="xl:col-span-2" label="日期范围（Asia/Shanghai）">
            <el-date-picker
              v-model="filters.dates"
              end-placeholder="结束日期"
              range-separator="至"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label=" ">
            <div class="flex gap-2">
              <el-button :loading="loading" type="primary" @click="search">
                查询
              </el-button>
              <el-button @click="reset">重置</el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-alert
      v-if="freshness !== 'FINAL' && !errorMessage"
      class="mb-4"
      :closable="false"
      show-icon
      :title="
        freshness === 'DELAYED'
          ? '数据延迟：水位尚未覆盖所选范围'
          : '临时口径：晚到事实仍可能更新原小时'
      "
      :type="freshness === 'DELAYED' ? 'warning' : 'info'"
    />

    <el-card shadow="never">
      <el-result
        v-if="forbidden"
        icon="warning"
        sub-title="需要 adx:report:view 与 Advertiser、Channel、MediaAccount 对象范围交集"
        title="无权查看报表"
      />
      <el-result
        v-else-if="errorMessage"
        icon="error"
        :sub-title="
          traceId ? `${errorMessage} · traceId ${traceId}` : errorMessage
        "
        title="报表加载失败"
      >
        <template #extra
          ><el-button @click="load">重新加载</el-button></template
        >
      </el-result>
      <el-empty
        v-else-if="emptyKind === 'INITIAL'"
        description="当前还没有统一小时事实；不会显示样例数值"
      />
      <el-empty
        v-else-if="emptyKind === 'FILTER'"
        description="没有符合当前筛选的事实"
      >
        <el-button @click="reset">清空筛选</el-button>
      </el-empty>
      <template v-else>
        <div
          class="mb-3 flex flex-wrap items-center justify-between gap-2 text-xs text-gray-500"
        >
          <span>时区 Asia/Shanghai · asOf {{ asOf || '—' }}</span>
          <el-tag
            :type="
              freshness === 'FINAL'
                ? 'success'
                : freshness === 'DELAYED'
                  ? 'warning'
                  : 'info'
            "
          >
            {{ freshness }}
          </el-tag>
        </div>
        <el-table
          v-loading="loading"
          :data="rows"
          border
          height="calc(100vh - 500px)"
          row-key="dimensionKey"
        >
          <el-table-column fixed label="日期/小时" min-width="170">
            <template #default="{ row }">
              <div class="font-medium">
                {{ dimensionValue(row, 'bucket') }}
              </div>
              <div class="mt-1 text-xs text-gray-400">
                {{ row.dimensionType }} · {{ row.qualityStatus }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            v-if="groupBy !== 'TIME'"
            fixed
            label="冻结对象 ID"
            min-width="190"
          >
            <template #default="{ row }">
              {{ dimensionValue(row, 'objectId') }}
            </template>
          </el-table-column>
          <el-table-column
            v-for="column in visibleColumns"
            :key="column.code"
            :label="column.label"
            :min-width="column.minWidth"
          >
            <template #default="{ row }">
              {{ display(row.metrics[column.code]?.value, column) }}
            </template>
          </el-table-column>
          <template #append>
            <div class="summary-grid">
              <span class="font-medium">{{ '当前页汇总' }}</span>
              <span v-for="column in visibleColumns" :key="column.code">
                {{ summaryValue(pageSummary, column) }}
              </span>
            </div>
          </template>
        </el-table>
        <div class="mt-3 rounded bg-gray-50 px-4 py-3 text-sm">
          <span class="font-medium">筛选范围全量汇总：</span>
          <span
            v-for="column in visibleColumns"
            :key="column.code"
            class="ml-4 whitespace-nowrap"
          >
            {{ column.label }} {{ summaryValue(querySummary, column) }}
          </span>
        </div>
        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="current"
            v-model:page-size="size"
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[20, 50, 100, 200]"
            :total="total"
            @current-change="load"
            @size-change="search"
          />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="exportDialogOpen" title="异步导出任务" width="520px">
      <el-descriptions v-if="exportTask" :column="1" border>
        <el-descriptions-item label="任务 ID">{{
          exportTask.id
        }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{
          exportTask.type
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{
          exportTask.status
        }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">
          {{ exportTask.expiresAt || '生成后开始计算' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="exportTask.errorCode" label="错误码">
          {{ exportTask.errorCode }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        class="mt-3"
        :closable="false"
        show-icon
        title="下载会再次校验权限与对象范围；过期文件返回 410"
        type="info"
      />
      <template #footer>
        <el-button @click="refreshExport">刷新状态</el-button>
        <el-button
          v-if="
            exportTask &&
            ['FAILED', 'PENDING', 'RETRY_WAIT'].includes(exportTask.status)
          "
          type="warning"
          @click="cancelExport"
        >
          取消任务
        </el-button>
        <el-button
          :disabled="exportTask?.status !== 'SUCCEEDED'"
          type="primary"
          @click="downloadExport"
        >
          再鉴权下载
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: 210px repeat(
      var(--metric-count, 12),
      minmax(96px, 1fr)
    );
  gap: 12px;
  min-width: 1400px;
  padding: 12px;
  color: #344054;
  background: #f8fafc;
  border-top: 1px solid #e5e7eb;
}

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-select),
:deep(.el-date-editor) {
  width: 100%;
}
</style>
