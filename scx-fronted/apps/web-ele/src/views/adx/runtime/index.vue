<script lang="ts" setup>
import type {
  RuntimeDelivery,
  RuntimeIngressDlq,
  RuntimeOverview,
} from '#/api/adx';

import { onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  getRuntimeDeliveries,
  getRuntimeIngressDlq,
  getRuntimeOverview,
  replayRuntimeDelivery,
  replayRuntimeIngressDlq,
} from '#/api/adx';

defineOptions({ name: 'AdxRuntimeCenter' });

const loading = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const status = ref('');
const direction = ref('');
const rows = ref<RuntimeDelivery[]>([]);
const dlqCurrent = ref(1);
const dlqTotal = ref(0);
const dlqRows = ref<RuntimeIngressDlq[]>([]);
const overview = ref<RuntimeOverview>({
  byDirection: {},
  deadDeliveries: 0,
  feedbackStreamPending: 0,
  ingressDlq: 0,
  pendingDeliveries: 0,
  retryDeliveries: 0,
  successfulDeliveries: 0,
});

const cards = [
  ['待投递', 'pendingDeliveries'],
  ['重试中', 'retryDeliveries'],
  ['死亡任务', 'deadDeliveries'],
  ['已成功', 'successfulDeliveries'],
  ['Stream Pending', 'feedbackStreamPending'],
  ['入口 DLQ', 'ingressDlq'],
] as const;

async function load() {
  loading.value = true;
  try {
    const [summary, deliveries, ingressDlq] = await Promise.all([
      getRuntimeOverview(),
      getRuntimeDeliveries({
        current: current.value,
        direction: direction.value || undefined,
        size: size.value,
        status: status.value || undefined,
      }),
      getRuntimeIngressDlq({ current: dlqCurrent.value, size: 20 }),
    ]);
    overview.value = summary;
    rows.value = deliveries.records;
    total.value = deliveries.total;
    dlqRows.value = ingressDlq.records;
    dlqTotal.value = ingressDlq.total;
  } finally {
    loading.value = false;
  }
}

async function replay(row: RuntimeDelivery) {
  await ElMessageBox.confirm(
    '重放会重新执行对外请求，请确认目标方已允许幂等重试。',
    '重放死亡任务',
    { type: 'warning' },
  );
  await replayRuntimeDelivery(row.id);
  ElMessage.success('任务已进入待投递队列');
  await load();
}

async function replayIngress(row: RuntimeIngressDlq) {
  await ElMessageBox.confirm(
    '入口死信会重新进入可靠队列；无效协议数据不能直接重放。',
    '重放入口死信',
    { type: 'warning' },
  );
  await replayRuntimeIngressDlq(row.id);
  ElMessage.success('入口死信已重新入队');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="runtime-page">
    <header>
      <div>
        <h1>转发运行中心</h1>
        <p>观察 feedback、callback 与失败重试；原始事实不会因扣量而删除。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">
        刷新
      </el-button>
    </header>

    <section class="metrics">
      <el-card v-for="[label, key] in cards" :key="key" shadow="never">
        <span>{{ label }}</span>
        <strong>{{ overview[key] }}</strong>
      </el-card>
    </section>

    <el-card shadow="never">
      <div class="filters">
        <el-select v-model="direction" clearable placeholder="投递方向">
          <el-option label="Feedback → 上游" value="FEEDBACK_UPSTREAM" />
          <el-option label="Callback → 下游" value="CALLBACK_DOWNSTREAM" />
        </el-select>
        <el-select v-model="status" clearable placeholder="任务状态">
          <el-option label="待投递" value="PENDING" />
          <el-option label="重试中" value="RETRY" />
          <el-option label="已成功" value="SUCCESS" />
          <el-option label="死亡任务" value="DEAD" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column
          prop="eventId"
          label="事件ID"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column prop="direction" label="方向" min-width="190" />
        <el-table-column prop="adapterCode" label="适配器" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="attemptCount" label="尝试次数" width="100" />
        <el-table-column prop="nextRetryAt" label="下次执行" min-width="175" />
        <el-table-column
          prop="lastError"
          label="最后错误"
          min-width="260"
          show-overflow-tooltip
        />
        <el-table-column fixed="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DEAD'"
              v-access:code="'adx:runtime:replay'"
              link
              type="primary"
              @click="replay(row)"
            >
              重放
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <el-card class="dlq-card" shadow="never">
      <template #header>
        <strong>入口 DLQ</strong>
      </template>
      <el-table v-loading="loading" :data="dlqRows" border>
        <el-table-column
          prop="eventId"
          label="事件ID"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column prop="partitionNo" label="分区" width="80" />
        <el-table-column
          prop="streamRecordId"
          label="Stream记录"
          min-width="170"
        />
        <el-table-column
          prop="reason"
          label="原因"
          min-width="300"
          show-overflow-tooltip
        />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="createTime" label="进入时间" min-width="175" />
        <el-table-column fixed="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="
                row.status === 'PENDING' &&
                !row.reason.startsWith('INVALID_ENVELOPE')
              "
              v-access:code="'adx:runtime:replay'"
              link
              type="primary"
              @click="replayIngress(row)"
            >
              重放
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="dlqCurrent"
        :total="dlqTotal"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.runtime-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

h1 {
  margin: 0;
  font-size: 24px;
  color: #172033;
}

p {
  margin: 6px 0 0;
  color: #667085;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metrics span {
  display: block;
  color: #667085;
}

.metrics strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
  color: #172033;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.filters .el-select {
  width: 190px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.dlq-card {
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
