<script lang="ts" setup>
import type { AsyncServicesQueueStats, Media  } from '#/api/ad';

import { computed, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  dispatchCallbackJob,
  dispatchCallbackJobs,
  dispatchOutboxEvent,
  getAsyncServicesList,
  getAsyncServicesStats,
  getMedia,
  pauseCallbackJob,
  replayCallbackJob,
  resumeCallbackJob,
  retryExportTask,
} from '#/api/ad';
import { downloadBlobFile } from '#/utils/util';

import '../../../delivery/components/mvp-page.css';

type QueueMode = 'async' | 'queue';
type QueueType = 'callback' | 'export' | 'outbox';

type QueueModeTab = 'callback' | 'export' | 'outbox';

defineOptions({ name: 'AdQueueOverview' });

const props = defineProps<{ tab?: QueueMode }>();

const media = ref<Media[]>([]);
const mediaId = ref('');
const queueType = ref<QueueType>(props.tab === 'async' ? 'outbox' : 'callback');
const status = ref('');
const rawEventId = ref('');
const aggregateId = ref('');
const rows = ref<any[]>([]);
const stats = ref<AsyncServicesQueueStats>({
  waitingCount: 0,
  runningCount: 0,
  succeededCount: 0,
  failedCount: 0,
  delayedCount: 0,
  pausedCount: 0,
  deadCount: 0,
});
const loading = ref(false);
const dispatching = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);

const listStatusOptions = computed(() => {
  if (queueType.value === 'callback') {
    return ['PENDING', 'RETRYING', 'PAUSED', 'SUCCEEDED', 'DEAD'];
  }
  if (queueType.value === 'outbox') {
    return ['PENDING', 'RETRYING', 'SENT', 'DEAD'];
  }
  return ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED'];
});

const metricCards = computed(() => [
  { label: '等待', value: stats.value.waitingCount || 0 },
  { label: '执行', value: stats.value.runningCount || 0 },
  { label: '成功', value: stats.value.succeededCount || 0 },
  { label: '失败', value: stats.value.failedCount || 0 },
  { label: '延迟', value: stats.value.delayedCount || 0 },
  { label: '暂停', value: stats.value.pausedCount || 0 },
  { label: '死信', value: stats.value.deadCount || 0 },
]);

function resetFilters() {
  status.value = '';
  rawEventId.value = '';
  aggregateId.value = '';
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) {
    return;
  }

  loading.value = true;
  try {
    const [statResult, listResult] = await Promise.all([
      getAsyncServicesStats(queueType.value, mediaId.value),
      getAsyncServicesList({
        aggregateId:
          queueType.value === 'outbox'
            ? aggregateId.value.trim() || undefined
            : undefined,
        current: current.value,
        mediaId: mediaId.value,
        rawEventId:
          queueType.value === 'callback'
            ? rawEventId.value.trim() || undefined
            : undefined,
        queueType: queueType.value,
        size: size.value,
        status: status.value,
      }),
    ]);

    stats.value = statResult || stats.value;
    rows.value = listResult.records || [];
    total.value = listResult.total || 0;
  } finally {
    loading.value = false;
  }
}

async function dispatchAll() {
  await ElMessageBox.confirm(
    '仅处理待发送回传任务，并且只允许 sandbox 目标。',
    '执行沙箱派发',
    { type: 'warning' },
  );
  dispatching.value = true;
  try {
    const changed = await dispatchCallbackJobs(mediaId.value);
    ElMessage.success(`已处理 ${changed} 条任务`);
    await load();
  } finally {
    dispatching.value = false;
  }
}

async function replay(row: any) {
  await ElMessageBox.confirm(
    '重放只会将任务恢复为待发送，不会绕过去重。',
    '确认重放',
    {
      type: 'warning',
    },
  );
  await replayCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已恢复为待发送');
  await load();
}

async function dispatchCallback(row: any) {
  await ElMessageBox.confirm(
    '仅向当前 sandbox 回传目标派发此单条任务，不处理其他队列。',
    '立即派发回传',
    { type: 'warning' },
  );
  await dispatchCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已完成单条派发');
  await load();
}

async function dispatchOutbox(row: any) {
  await ElMessageBox.confirm(
    '只发布这条领域事件，不处理其他发件箱记录。',
    '立即发布领域事件',
    { type: 'warning' },
  );
  await dispatchOutboxEvent(row.id, mediaId.value);
  ElMessage.success('领域事件已完成发布');
  await load();
}

async function pause(row: any) {
  await pauseCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已暂停');
  await load();
}

async function resume(row: any) {
  await resumeCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已恢复');
  await load();
}

async function retryExport(row: any) {
  await retryExportTask(row.id);
  ElMessage.success('导出任务已重新进入队列');
  await load();
}

function download(row: any) {
  downloadBlobFile(
    `/ad/export-tasks/${row.id}/download`,
    {},
    row.artifactName || `scx-export-${row.id}.csv`,
  );
}

function tagType(value: string) {
  if (value === 'SUCCEEDED' || value === 'SENT') {
    return 'success';
  }
  if (value === 'RUNNING' || value === 'RETRYING') {
    return 'warning';
  }
  if (value === 'PAUSED') {
    return 'info';
  }
  if (value === 'EXPIRED' || value === 'DEAD' || value === 'FAILED') {
    return 'danger';
  }
  return 'info';
}

function exportTypeLabel(value: string) {
  return value === 'REDUCTION_CSV' ? '审减与超量' : '效果日报';
}

function onQueueChange(type: QueueType) {
  queueType.value = type;
}

function onTabChange(value: QueueModeTab) {
  onQueueChange(value);
}

watch(queueType, () => {
  current.value = 1;
  resetFilters();
  void load();
});

watch(mediaId, () => {
  current.value = 1;
  void load();
});

bootstrap().then(load);
</script>
<template>
  <main class="mvp-page">
    <div class="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-7">
      <div v-for="item in metricCards" :key="item.label" class="mvp-metric">
        <strong>{{ item.value }}</strong
        ><span>{{ item.label }}</span>
      </div>
    </div>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option
              v-for="item in listStatusOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
          <el-input
            v-if="queueType === 'callback'"
            v-model="rawEventId"
            clearable
            placeholder="归因原始事件 ID"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-input
            v-if="queueType === 'outbox'"
            v-model="aggregateId"
            clearable
            placeholder="事件聚合 ID"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-if="queueType === 'callback'"
          v-access:code="'ad:callback:replay'"
          :loading="dispatching"
          type="primary"
          @click="dispatchAll"
        >
          执行沙箱派发
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <template v-if="queueType === 'callback'">
          <el-table-column label="任务 ID" min-width="210">
            <template #default="{ row }">
              <span class="mvp-code">{{ row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="动作" prop="actionType" width="130" />
          <el-table-column label="目标" width="120">
            <template #default="{ row }">
              <span class="mvp-code">{{ row.targetRef }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag effect="plain" :type="tagType(row.status)">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="尝试次数" prop="attempts" width="110" />
          <el-table-column
            label="下次重试"
            prop="nextRetryAt"
            min-width="180"
          />
          <el-table-column label="最近错误" min-width="240">
            <template #default="{ row }">
              <span v-if="row.lastErrorCode" class="mvp-code">
                {{ row.lastErrorCode }} · {{ row.lastErrorMessage }}
              </span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column align="right" fixed="right" label="操作" width="220">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING' || row.status === 'RETRYING'"
                v-access:code="'ad:callback:replay'"
                link
                type="primary"
                @click="dispatchCallback(row)"
              >
                立即派发
              </el-button>
              <el-button
                v-if="row.status === 'PENDING' || row.status === 'RETRYING'"
                v-access:code="'ad:callback:replay'"
                link
                type="warning"
                @click="pause(row)"
              >
                暂停
              </el-button>
              <el-button
                v-if="row.status === 'PAUSED'"
                v-access:code="'ad:callback:replay'"
                link
                type="success"
                @click="resume(row)"
              >
                恢复
              </el-button>
              <el-button
                v-if="row.status === 'DEAD' || row.status === 'RETRYING'"
                v-access:code="'ad:callback:replay'"
                link
                type="primary"
                @click="replay(row)"
              >
                重放
              </el-button>
            </template>
          </el-table-column>
        </template>

        <template v-else-if="queueType === 'outbox'">
          <el-table-column label="发件箱 ID" min-width="210">
            <template #default="{ row }">
              <span class="mvp-code">{{ row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="事件聚合 ID" min-width="210">
            <template #default="{ row }">
              <span class="mvp-code">{{ row.aggregateId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="领域事件" prop="eventType" min-width="180" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag effect="plain" :type="tagType(row.status)">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="尝试次数" prop="attempts" width="110" />
          <el-table-column label="发送时间" prop="sentAt" min-width="180" />
          <el-table-column align="right" fixed="right" label="操作" width="120">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING' || row.status === 'RETRYING'"
                v-access:code="'ad:callback:replay'"
                link
                type="primary"
                @click="dispatchOutbox(row)"
              >
                立即发布
              </el-button>
            </template>
          </el-table-column>
        </template>

        <template v-else>
          <el-table-column label="任务 ID" min-width="210">
            <template #default="{ row }">
              <span class="mvp-code">{{ row.id }}</span>
            </template>
          </el-table-column>
          <el-table-column label="日期范围" min-width="190">
            <template #default="{ row }">
              {{ row.reportStartDate }} ～ {{ row.reportEndDate }}
            </template>
          </el-table-column>
          <el-table-column label="导出内容" min-width="150">
            <template #default="{ row }">
              {{ exportTypeLabel(row.exportType) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag effect="plain" :type="tagType(row.status)">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="行数" prop="rowCount" width="90" />
          <el-table-column label="过期时间" prop="expiresAt" min-width="180" />
          <el-table-column align="right" fixed="right" label="操作" width="140">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'SUCCEEDED'"
                v-access:code="'ad:export:read'"
                link
                type="primary"
                @click="download(row)"
              >
                下载
              </el-button>
              <el-button
                v-else-if="row.status === 'FAILED'"
                v-access:code="'ad:export:create'"
                link
                type="warning"
                @click="retryExport(row)"
              >
                重试
              </el-button>
            </template>
          </el-table-column>
        </template>

        <template #empty>
          <div class="mvp-empty">暂无任务</div>
        </template>
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>
  </main>
</template>
