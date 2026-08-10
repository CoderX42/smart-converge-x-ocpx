<script lang="ts" setup>
import type {
  TaskRecord,
  UnifiedReportMetricCode,
  UnifiedReportRecord,
} from '#/api/adx';

import { computed, ref, watch } from 'vue';

import dayjs from 'dayjs';

import { getReport } from '#/api/adx';

import { formatReportMetric, taskReportQuery } from '../report/report-model';

type Mode = 'DAY_LINK' | 'DAY_TASK' | 'HOUR_TASK';

const props = defineProps<{
  initialMode: Mode;
  modelValue: boolean;
  task?: TaskRecord;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const mode = ref<Mode>('HOUR_TASK');
const rows = ref<UnifiedReportRecord[]>([]);
const loading = ref(false);
const error = ref('');
const forbidden = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const freshness = ref('FINAL');
const pageSummary = ref<Record<string, { status: string; value?: number }>>({});
const dates = ref<[string, string]>([
  dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
]);
const commonMetrics: UnifiedReportMetricCode[] = [
  'impressions',
  'validClicks',
  'activations',
  'registrations',
  'payments',
  'activationRate',
  'registrationRate',
  'paymentRate',
];
const visibleMetrics = ref<UnifiedReportMetricCode[]>([...commonMetrics]);

const metricLabels: Record<string, string> = {
  activationRate: '激活率',
  activations: '激活',
  impressions: '曝光',
  paymentRate: '付费率',
  payments: '付费',
  registrationPaymentRate: '注册付费率',
  registrationRate: '注册率',
  registrations: '注册',
  validClicks: '有效点击',
};

const availableMetrics = computed<UnifiedReportMetricCode[]>(() =>
  mode.value === 'DAY_TASK' || mode.value === 'DAY_LINK'
    ? [...commonMetrics, 'registrationPaymentRate']
    : commonMetrics,
);

async function load() {
  if (!props.task) return;
  loading.value = true;
  error.value = '';
  forbidden.value = false;
  try {
    const result = await getReport(
      taskReportQuery({
        dates: dates.value,
        metrics: visibleMetrics.value,
        mode: mode.value,
        page: current.value,
        size: size.value,
        taskId: props.task.id,
      }),
    );
    rows.value = result.records;
    total.value = result.total;
    freshness.value = result.freshnessStatus;
    pageSummary.value = result.pageSummary.metrics;
  } catch (error_: any) {
    rows.value = [];
    total.value = 0;
    forbidden.value =
      Number(error_?.response?.status || error_?.status) === 403;
    error.value =
      error_?.response?.data?.msg || error_?.message || '报表加载失败';
  } finally {
    loading.value = false;
  }
}

function display(value: null | number | undefined, metric: string) {
  return formatReportMetric(value, metric as UnifiedReportMetricCode);
}

function dimensionValue(row: UnifiedReportRecord, key: string) {
  const value = row.dimensions[key];
  return value === undefined || value === null ? '' : String(value);
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    mode.value = props.initialMode;
    visibleMetrics.value = [
      ...commonMetrics,
      ...(props.initialMode === 'HOUR_TASK'
        ? []
        : (['registrationPaymentRate'] as UnifiedReportMetricCode[])),
    ];
    current.value = 1;
    load();
  },
);

watch(mode, () => {
  visibleMetrics.value = visibleMetrics.value.filter((metric) =>
    availableMetrics.value.includes(metric),
  );
  if (visibleMetrics.value.length === 0)
    visibleMetrics.value = [...commonMetrics];
  current.value = 1;
  load();
});
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="min(1040px, 96vw)"
    :title="`任务报表 · ${task?.taskName || ''}`"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="mb-4 flex flex-wrap items-end gap-3">
      <div>
        <div class="mb-1 text-xs text-gray-500">报表类型</div>
        <el-segmented
          v-model="mode"
          :options="[
            { label: '时报', value: 'HOUR_TASK' },
            { label: '日报', value: 'DAY_TASK' },
            { label: '链接日报', value: 'DAY_LINK' },
          ]"
        />
      </div>
      <div>
        <div class="mb-1 text-xs text-gray-500">独立日期范围</div>
        <el-date-picker
          v-model="dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="load"
        />
      </div>
      <el-popover placement="bottom" trigger="click" width="240">
        <el-checkbox-group
          v-model="visibleMetrics"
          class="grid gap-2"
          @change="load"
        >
          <el-checkbox
            v-for="metric in availableMetrics"
            :key="metric"
            :label="metric"
          >
            {{ metricLabels[metric] }}
          </el-checkbox>
        </el-checkbox-group>
        <template #reference><el-button>独立列设置</el-button></template>
      </el-popover>
    </div>

    <el-alert
      class="mb-3"
      :closable="false"
      show-icon
      :title="
        mode === 'DAY_LINK'
          ? '固定当前 taskId，按 AppLink 日粒度查询；不会改变 Task 列表筛选'
          : '固定当前 taskId；关闭抽屉不会重置 Task 列表'
      "
      type="info"
    />
    <el-alert
      v-if="freshness !== 'FINAL'"
      class="mb-3"
      :closable="false"
      :title="`数据状态 ${freshness}`"
      type="warning"
    />
    <el-result
      v-if="forbidden"
      icon="warning"
      sub-title="Task 报表仍受 Advertiser、Channel、MediaAccount 三对象范围交集限制"
      title="无权查看"
    />
    <el-result
      v-else-if="error"
      icon="error"
      :sub-title="error"
      title="报表加载失败"
    >
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>
    <el-empty
      v-else-if="!loading && rows.length === 0"
      description="当前 Task 暂无事实"
    />
    <template v-else>
      <el-table
        v-loading="loading"
        :data="rows"
        border
        height="calc(100vh - 330px)"
      >
        <el-table-column fixed label="日期/小时" min-width="170">
          <template #default="{ row }">
            {{ dimensionValue(row, 'bucket') }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="mode === 'DAY_LINK'"
          fixed
          label="AppLink ID"
          min-width="190"
        >
          <template #default="{ row }">
            {{ dimensionValue(row, 'objectId') }}
          </template>
        </el-table-column>
        <el-table-column
          v-for="metric in visibleMetrics"
          :key="metric"
          :label="metricLabels[metric]"
          min-width="112"
        >
          <template #default="{ row }">{{
            display(row.metrics[metric]?.value, metric)
          }}</template>
        </el-table-column>
      </el-table>
      <div class="mt-3 rounded bg-gray-50 p-3 text-sm">
        当前页汇总：
        <span v-for="metric in visibleMetrics" :key="metric" class="ml-4">
          {{ metricLabels[metric] }}
          {{ display(pageSummary[metric]?.value, metric) }}
        </span>
      </div>
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        class="mt-4 justify-end"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        :total="total"
        @current-change="load"
        @size-change="load"
      />
    </template>
  </el-drawer>
</template>
