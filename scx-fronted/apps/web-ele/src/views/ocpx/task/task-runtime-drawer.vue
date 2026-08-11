<script lang="ts" setup>
import type { RuntimeFact, TaskRecord } from '#/api/ocpx';

import { computed, reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  getRuntimeClicks,
  getRuntimeConversions,
  getTaskIntegrationFacts,
} from '#/api/ocpx';

type Mode = 'CLICK' | 'CONVERSION' | 'INTEGRATION';

const props = defineProps<{
  initialMode: Mode;
  modelValue: boolean;
  task?: TaskRecord;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();

const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});
const mode = ref<Mode>(props.initialMode);
const loading = ref(false);
const initialized = ref(false);
const forbidden = ref(false);
const errorMessage = ref('');
const traceId = ref('');
const freshness = ref('FINAL');
const qualityFlags = ref<string[]>([]);
const rows = ref<RuntimeFact[]>([]);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const timeRange = ref<[string, string]>();
const filters = reactive({ eventCode: '', exactId: '', status: '' });

const title = computed(() => {
  const labels = {
    CLICK: '点击详情',
    CONVERSION: '转化详情',
    INTEGRATION: '媒体联调详情',
  } as const;
  const suffix = labels[mode.value];
  return `${props.task?.taskName || 'Task'} · ${suffix}`;
});

async function load() {
  if (!props.task?.id) return;
  loading.value = true;
  forbidden.value = false;
  errorMessage.value = '';
  try {
    const query = {
      current: current.value,
      endTime: timeRange.value?.[1],
      eventCode:
        mode.value === 'CONVERSION'
          ? filters.eventCode || undefined
          : undefined,
      exactId: filters.exactId.trim() || undefined,
      size: size.value,
      startTime: timeRange.value?.[0],
      status: filters.status || undefined,
      taskId: props.task.id,
    };
    let result;
    switch (mode.value) {
      case 'CLICK': {
        result = await getRuntimeClicks(query);
        break;
      }
      case 'CONVERSION': {
        result = await getRuntimeConversions(query);
        break;
      }
      default: {
        result = await getTaskIntegrationFacts(props.task.id, query);
      }
    }
    rows.value = result.records;
    total.value = result.total;
    traceId.value = result.traceId;
    freshness.value = result.freshnessStatus;
    qualityFlags.value = result.qualityFlags;
  } catch (error: any) {
    rows.value = [];
    total.value = 0;
    forbidden.value = error?.response?.status === 403 || error?.status === 403;
    traceId.value = error?.response?.data?.traceId || error?.traceId || '';
    errorMessage.value = forbidden.value
      ? ''
      : error?.message || '运行详情加载失败';
  } finally {
    loading.value = false;
    initialized.value = true;
  }
}

async function search() {
  current.value = 1;
  await load();
}

async function changeMode(value: Mode) {
  mode.value = value;
  current.value = 1;
  await load();
}

async function copyTrace(value?: string) {
  if (!value) return;
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success('traceId 已复制');
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限');
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (!value) return;
    mode.value = props.initialMode;
    current.value = 1;
    void load();
  },
);
</script>

<template>
  <el-drawer v-model="open" :title="title" size="min(920px, 96vw)">
    <el-alert
      class="mb-4"
      :closable="false"
      title="打开抽屉不会产生测试点击、事件外发或补偿命令；所有 ID 仅显示服务端掩码摘要"
      type="info"
    />

    <el-segmented
      :model-value="mode"
      :options="[
        { label: '联调', value: 'INTEGRATION' },
        { label: '点击', value: 'CLICK' },
        { label: '转化', value: 'CONVERSION' },
      ]"
      @change="changeMode"
    />

    <div class="drawer-filters">
      <el-input v-model="filters.exactId" clearable placeholder="精确 ID" />
      <el-input v-model="filters.status" clearable placeholder="处理状态" />
      <el-input
        v-if="mode === 'CONVERSION'"
        v-model="filters.eventCode"
        clearable
        placeholder="标准事件 code"
      />
      <el-date-picker
        v-model="timeRange"
        end-placeholder="结束（不含）"
        range-separator="至"
        start-placeholder="开始（含）"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <div class="drawer-evidence">
      <el-tag :type="freshness === 'FINAL' ? 'success' : 'warning'">
        {{ freshness }}
      </el-tag>
      <el-tag v-if="qualityFlags.includes('UNMAPPED')" type="warning">
        UNMAPPED
      </el-tag>
      <el-button v-if="traceId" link type="primary" @click="copyTrace(traceId)">
        traceId：{{ traceId }}
      </el-button>
    </div>

    <el-skeleton v-if="loading && !initialized" :rows="7" animated />
    <el-result
      v-else-if="forbidden"
      icon="warning"
      sub-title="Task 固定范围、总数和候选不会泄露"
      title="无权查看该 Task 的运行事实"
    />
    <el-result
      v-else-if="errorMessage"
      icon="error"
      :sub-title="traceId ? `traceId：${traceId}` : '筛选条件已保留'"
      :title="errorMessage"
    >
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>
    <el-empty
      v-else-if="initialized && !loading && rows.length === 0"
      :description="
        filters.exactId || filters.status || timeRange
          ? '没有符合当前筛选的事实'
          : mode === 'INTEGRATION'
            ? '暂时还没有联调点击数据'
            : '暂无运行事实'
      "
    />
    <el-table v-else v-loading="loading" :data="rows" border>
      <el-table-column prop="receivedTime" label="接收时间" min-width="180" />
      <el-table-column prop="summaryCode" label="处理摘要" min-width="190" />
      <el-table-column prop="processingStatus" label="状态" min-width="120" />
      <el-table-column prop="eventCode" label="标准事件" min-width="130" />
      <el-table-column
        prop="externalClickIdMasked"
        label="点击 ID 摘要"
        min-width="150"
      />
      <el-table-column
        prop="mediaTaskIdMasked"
        label="媒体任务摘要"
        min-width="150"
      />
      <el-table-column
        prop="correlationIdMasked"
        label="关联摘要"
        min-width="145"
      />
      <el-table-column prop="dimensionStatus" label="维度" min-width="110" />
    </el-table>

    <el-pagination
      v-if="rows.length > 0"
      v-model:current-page="current"
      v-model:page-size="size"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="search"
    />
  </el-drawer>
</template>

<style scoped>
.drawer-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0 12px;
}

.drawer-filters :deep(.el-input) {
  width: 180px;
}

.drawer-evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 34px;
  margin-bottom: 12px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
