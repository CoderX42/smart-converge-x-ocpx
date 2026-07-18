<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  dispatchCallbackJob,
  dispatchCallbackJobs,
  dispatchOutboxEvent,
  getCallbackJobs,
  getCallbackQueueStats,
  getMedia,
  getOutboxEvents,
  pauseCallbackJob,
  replayCallbackJob,
  resumeCallbackJob,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdCallbackJobs' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const status = ref('');
const rawEventId = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const dispatching = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const stats = ref<any>({});
const outboxAggregateId = ref('');
const outboxRows = ref<any[]>([]);

async function bootstrap() {
  try {
    const result = await getMedia();
    media.value = result.records || [];
    mediaId.value = media.value[0]?.id || '';
  } catch {
    media.value = [];
  }
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const [result, statResult, outboxResult] = await Promise.all([
      getCallbackJobs({
        current: current.value,
        mediaId: mediaId.value,
        size: size.value,
        status: status.value,
        rawEventId: rawEventId.value.trim() || undefined,
      }),
      getCallbackQueueStats(mediaId.value),
      getOutboxEvents({
        aggregateId: outboxAggregateId.value.trim() || undefined,
        current: 1,
        mediaId: mediaId.value,
        size: 20,
      }),
    ]);
    rows.value = result.records || [];
    total.value = result.total || 0;
    stats.value = statResult || {};
    outboxRows.value = outboxResult.records || [];
  } finally {
    loading.value = false;
  }
}

async function dispatch() {
  await ElMessageBox.confirm(
    '仅处理当前媒体范围内的待发送任务，并且只允许 sandbox 目标。',
    '执行沙箱派发',
    { type: 'warning' },
  );
  dispatching.value = true;
  try {
    const changed = await dispatchCallbackJobs(mediaId.value);
    ElMessage.success(`已处理 ${changed} 条任务或发件箱记录`);
    await load();
  } finally {
    dispatching.value = false;
  }
}

async function replay(row: any) {
  await ElMessageBox.confirm(
    '重放只会将任务恢复为待发送，不会绕过去重和归因事实。',
    '确认重放',
    { type: 'warning' },
  );
  await replayCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已进入待发送队列');
  await load();
}

async function dispatchOne(row: any) {
  await ElMessageBox.confirm(
    '仅向当前 sandbox 回传目标派发此单条任务，不处理其他队列或发件箱记录。',
    '立即派发回传',
    { type: 'warning' },
  );
  await dispatchCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已完成单条派发');
  await load();
}

async function dispatchOutboxOne(row: any) {
  await ElMessageBox.confirm(
    '只发布当前媒体与任务范围内的这条领域事件，不处理其他发件箱记录。',
    '立即发布领域事件',
    { type: 'warning' },
  );
  await dispatchOutboxEvent(row.id, mediaId.value);
  ElMessage.success('领域事件已完成单条发布');
  await load();
}

async function pause(row: any) {
  await pauseCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已暂停');
  await load();
}

async function resume(row: any) {
  await resumeCallbackJob(row.id, mediaId.value);
  ElMessage.success('回传任务已恢复为待发送');
  await load();
}

function tagType(value: string) {
  if (value === 'SUCCEEDED') return 'success';
  if (value === 'DEAD') return 'danger';
  if (value === 'RETRYING') return 'warning';
  return 'info';
}

watch([mediaId, status], () => {
  current.value = 1;
  load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <header class="mvp-hero">
      <div>
        <div class="mvp-kicker">M5 / Sandbox Delivery</div>
        <h1 class="mvp-title">回传任务</h1>
        <p class="mvp-subtitle">
          同时观察领域事件发件箱与媒体回传队列；状态均来自持久化任务，不使用模拟运行数据。
        </p>
      </div>
      <div class="mvp-control">
        <div class="mb-2 text-xs text-white/50">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>
    </header>

    <div class="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-6">
      <div class="mvp-metric">
        <strong>{{ stats.pendingCount || 0 }}</strong
        ><span>等待任务</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ stats.retryingCount || 0 }}</strong
        ><span>重试任务</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ stats.pausedCount || 0 }}</strong
        ><span>暂停任务</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ stats.delayedCount || 0 }}</strong
        ><span>延迟任务</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ stats.succeededCount || 0 }}</strong
        ><span>成功任务</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ stats.deadCount || 0 }}</strong
        ><span>死信任务</span>
      </div>
    </div>

    <el-card class="mvp-card mb-4" shadow="never">
      <template #header>
        <div class="mvp-toolbar !mb-0">
          <div>
            <div class="font-semibold">领域事件发件箱</div>
            <div class="mt-1 text-xs text-gray-500">
              提交后的事件只有在 RocketMQ 发布成功后才会标记为已发送。
            </div>
          </div>
          <el-tag effect="plain" type="info">
            {{ stats.outbox?.sentCount || 0 }} 已发布
          </el-tag>
        </div>
      </template>
      <div class="grid grid-cols-2 gap-3 md:grid-cols-5">
        <div class="mvp-metric">
          <strong>{{ stats.outbox?.pendingCount || 0 }}</strong
          ><span>等待发布</span>
        </div>
        <div class="mvp-metric">
          <strong>{{ stats.outbox?.retryingCount || 0 }}</strong
          ><span>发布重试</span>
        </div>
        <div class="mvp-metric">
          <strong>{{ stats.outbox?.delayedCount || 0 }}</strong
          ><span>延迟发布</span>
        </div>
        <div class="mvp-metric">
          <strong>{{ stats.outbox?.sentCount || 0 }}</strong
          ><span>发布成功</span>
        </div>
        <div class="mvp-metric">
          <strong>{{ stats.outbox?.deadCount || 0 }}</strong
          ><span>发布死信</span>
        </div>
      </div>
      <div class="mvp-toolbar mt-4">
        <div class="mvp-filters">
          <el-input
            v-model="outboxAggregateId"
            clearable
            placeholder="事件聚合 ID"
            style="width: 260px"
          />
          <el-button @click="load">查询</el-button>
        </div>
      </div>
      <el-table :data="outboxRows" class="mt-2" row-key="id">
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
              @click="dispatchOutboxOne(row)"
            >
              立即发布
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">当前媒体范围内暂无领域事件发件箱记录</div>
        </template>
      </el-table>
    </el-card>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 160px"
          >
            <el-option
              v-for="item in [
                'PENDING',
                'RETRYING',
                'PAUSED',
                'SUCCEEDED',
                'DEAD',
              ]"
              :key="item"
              :value="item"
            />
          </el-select>
          <el-input
            v-model="rawEventId"
            clearable
            placeholder="归因原始事件 ID"
            style="width: 220px"
          />
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:callback:replay'"
          :loading="dispatching"
          type="primary"
          @click="dispatch"
        >
          执行沙箱派发
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
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
        <el-table-column label="下次重试" prop="nextRetryAt" min-width="180" />
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
              @click="dispatchOne(row)"
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
        <template #empty>
          <div class="mvp-empty">当前媒体范围内暂无回传任务</div>
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
