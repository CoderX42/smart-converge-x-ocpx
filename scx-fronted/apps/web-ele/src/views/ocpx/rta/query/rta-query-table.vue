<script setup lang="ts">
import type { RtaLabel } from '../labels';

import type { EntityOption } from '#/api/ocpx';
import type { RtaRow } from '#/api/ocpx/rta';

import { onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage } from 'element-plus';

import { queryRta, rtaOptions } from '#/api/ocpx/rta';

import {
  decisionStatus,
  deliveryStatus,
  formatTime,
  logStages,
  logStatus,
  protocols,
  reasonText,
  tagOf,
} from '../labels';
import RecordTimeline from './record-timeline.vue';

type RtaQueryKind = 'logs' | 'records' | 'reports';

const props = defineProps<{ kind: RtaQueryKind }>();

type Column = {
  key: keyof RtaRow;
  label: string;
  tag?: (row: RtaRow) => RtaLabel;
  text?: (row: RtaRow) => unknown;
  width?: number;
};

function display(value: unknown) {
  return value === undefined || value === null || value === ''
    ? '—'
    : String(value);
}

const titles: Record<RtaQueryKind, string> = {
  logs: '流程日志',
  records: '请求记录',
  reports: '统计报表',
};
const descriptions: Record<RtaQueryKind, string> = {
  logs: '',
  records:
    '',
  reports:
    '',
};
const columns: Record<RtaQueryKind, Column[]> = {
  records: [
    { key: 'requestId', label: '媒体请求 ID', width: 220 },
    {
      key: 'protocol',
      label: 'RTA 协议',
      tag: (row) => tagOf(protocols, row.protocol),
    },
    { key: 'mediaName', label: '媒体' },
    {
      key: 'status',
      label: '决策',
      tag: (row) => tagOf(decisionStatus, row.status),
    },
    {
      key: 'matchedTaskIds',
      label: '命中任务',
      text: (row) => row.matchedTaskIds?.join('、'),
    },
    {
      key: 'deliveryStatus',
      label: '媒体通知',
      tag: (row) => tagOf(deliveryStatus, row.deliveryStatus),
    },
    {
      key: 'receivedAt',
      label: '受理时间',
      text: (row) => formatTime(row.receivedAt),
      width: 180,
    },
    {
      key: 'expiresAt',
      label: '放行失效时间',
      text: (row) => (row.status === 'ALLOW' ? formatTime(row.expiresAt) : ''),
      width: 180,
    },
    {
      key: 'reasonCode',
      label: '原因',
      text: (row) => reasonText(row.reasonCode),
    },
  ],
  logs: [
    { key: 'requestId', label: '媒体请求 ID', width: 220 },
    { key: 'mediaName', label: '媒体' },
    { key: 'taskName', label: '任务' },
    {
      key: 'stage',
      label: '阶段',
      text: (row) => logStages[row.stage || ''] || row.stage,
    },
    {
      key: 'status',
      label: '结果',
      tag: (row) => tagOf(logStatus, row.status),
    },
    {
      key: 'reason',
      label: '原因',
      text: (row) => reasonText(row.reason),
    },
    { key: 'durationMs', label: '耗时 / ms' },
    { key: 'eventTime', label: '事件时间', width: 180 },
    { key: 'updateTime', label: '落库时间', width: 180 },
  ],
  reports: [
    { key: 'timeHour', label: '接收时段', width: 180 },
    { key: 'mediaName', label: '媒体' },
    { key: 'taskName', label: '任务' },
    { key: 'requestCount', label: '受理' },
    { key: 'pendingCount', label: '待决策' },
    { key: 'allowCount', label: '放行' },
    { key: 'denyCount', label: '拒绝' },
    { key: 'errorCount', label: '错误' },
    { key: 'timeoutCount', label: '超时' },
    {
      key: 'allowRate',
      label: 'RTA 命中率',
      text: (row) => `${(Number(row.allowRate || 0) * 100).toFixed(2)}%`,
    },
    { key: 'deliverySuccessCount', label: '通知成功' },
    { key: 'deliveryFailedCount', label: '通知失败' },
  ],
};

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const loading = ref(false);
const error = ref('');
const rows = ref<RtaRow[]>([]);
const total = ref(0);
const dataAsOf = ref('');
const detail = ref<RtaRow>();
const media = ref<EntityOption[]>([]);
const advertisers = ref<EntityOption[]>([]);
const tasks = ref<EntityOption[]>([]);
const query = reactive({
  advertiserId: '',
  current: 1,
  grain: 'HOUR',
  mediaId: '',
  monitorTaskId: '',
  requestId: '',
  size: 30,
  status: '',
});
const range = ref<[Date, Date]>([
  new Date(Date.now() - 86_400_000),
  new Date(),
]);

function cellText(column: Column, row: RtaRow) {
  return display(column.text ? column.text(row) : row[column.key]);
}

let sequence = 0;
async function load(reset = false) {
  if (reset) query.current = 1;
  const token = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const result = await queryRta(props.kind, {
      ...query,
      endTime: range.value[1].getTime(),
      startTime: range.value[0].getTime(),
    });
    if (token !== sequence) return;
    rows.value = result.records;
    total.value = result.total;
    dataAsOf.value = result.dataAsOf || '';
  } catch (error_) {
    if (token === sequence) {
      rows.value = [];
      total.value = 0;
      error.value =
        error_ instanceof Error ? error_.message : '查询失败，请重试';
    }
  } finally {
    if (token === sequence) loading.value = false;
  }
}

async function searchOptions(
  kind: 'advertisers' | 'media' | 'tasks',
  keyword: string,
) {
  try {
    const result = await rtaOptions(kind, { keyword });
    if (kind === 'media') media.value = result;
    if (kind === 'advertisers') advertisers.value = result;
    if (kind === 'tasks') tasks.value = result;
  } catch {
    ElMessage.error('选项加载失败，请重试');
  }
}

function routeFilters() {
  const value = (key: string) =>
    typeof route.query[key] === 'string' ? String(route.query[key]) : '';
  query.mediaId = value('mediaId');
  query.monitorTaskId = value('monitorTaskId');
  query.requestId = value('requestId');
  void load(true);
}

function openLogs(row: RtaRow) {
  void router.push({
    path: '/ocpx/rta/logs',
    query: { mediaId: row.mediaId, requestId: row.requestId },
  });
}

watch(() => route.query, routeFilters);
onMounted(async () => {
  routeFilters();
  const result = await Promise.allSettled([
    rtaOptions('media'),
    rtaOptions('advertisers'),
    rtaOptions('tasks'),
  ]);
  if (result[0].status === 'fulfilled') media.value = result[0].value;
  if (result[1].status === 'fulfilled') advertisers.value = result[1].value;
  if (result[2].status === 'fulfilled') tasks.value = result[2].value;
  if (result.some((item) => item.status === 'rejected'))
    ElMessage.error('部分筛选选项加载失败，请刷新重试');
});
</script>

<template>
  <Page :title="titles[kind]" :description="descriptions[kind]">
    <div class="rta-query">
      <el-form inline class="rta-query__filters" @submit.prevent="load(true)">
        <el-form-item label="媒体">
          <el-select
            v-model="query.mediaId"
            remote
            :remote-method="
              (keyword: string) => searchOptions('media', keyword)
            "
            clearable
            filterable
            placeholder="全部媒体"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="广告主">
          <el-select
            v-model="query.advertiserId"
            remote
            :remote-method="
              (keyword: string) => searchOptions('advertisers', keyword)
            "
            clearable
            filterable
            placeholder="全部广告主"
          >
            <el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务">
          <el-select
            v-model="query.monitorTaskId"
            remote
            :remote-method="
              (keyword: string) => searchOptions('tasks', keyword)
            "
            clearable
            filterable
            placeholder="全部任务"
          >
            <el-option
              v-for="item in tasks"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="kind !== 'reports'" label="请求 ID">
          <el-input v-model="query.requestId" clearable />
        </el-form-item>
        <el-form-item v-if="kind === 'records'" label="决策">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option
              v-for="(label, value) in decisionStatus"
              :key="value"
              :label="label.text"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="kind !== 'records'" label="时间">
          <el-date-picker
            v-model="range"
            type="datetimerange"
            :clearable="false"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item v-if="kind === 'reports'" label="粒度">
          <el-select v-model="query.grain">
            <el-option label="小时" value="HOUR" />
            <el-option label="天" value="DAY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button :loading="loading" @click="load()">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />
      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        empty-text="暂无符合条件的记录"
      >
        <el-table-column
          v-for="column in columns[kind]"
          :key="column.key"
          :label="column.label"
          :min-width="column.width || 120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-tag
              v-if="column.tag"
              size="small"
              :type="column.tag(row).type"
              >{{ column.tag(row).text }}</el-tag
            >
            <template v-else>{{ cellText(column, row) }}</template>
          </template>
        </el-table-column>
        <el-table-column
          v-if="kind === 'records'"
          label="操作"
          fixed="right"
          width="100"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="detail = row"
              >查看详情</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 30, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load()"
        @size-change="load(true)"
      />
    </div>
    <el-drawer
      :model-value="!!detail"
      title="请求详情"
      size="min(560px, 100vw)"
      @close="detail = undefined"
    >
      <template v-if="detail">
        <RecordTimeline :record="detail" />
        <el-button
          v-if="hasAccessByCodes(['ocpx:rta:logs:view'])"
          type="primary"
          @click="openLogs(detail)"
          >查看关联流程日志</el-button
        >
      </template>
    </el-drawer>
  </Page>
</template>

<style scoped>
.rta-query {
  padding: 20px;
  background: var(--el-bg-color);
  border-radius: 8px;
}

.rta-query__filters :deep(.el-select) {
  width: 180px;
}

.rta-query__toolbar {
  margin-bottom: 16px;
  color: var(--el-text-color-secondary);
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
