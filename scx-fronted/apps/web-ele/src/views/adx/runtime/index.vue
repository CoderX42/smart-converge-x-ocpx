<script lang="ts" setup>
import type {
  RuntimeDelivery,
  RuntimeDeliveryAttempt,
  RuntimeEvent,
  RuntimeIngressAudit,
  RuntimeIngressDlq,
  RuntimeOverview,
  RuntimeTimelineItem,
} from '#/api/adx';

import { onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  getRuntimeDeliveries,
  getRuntimeDeliveryAttempts,
  getRuntimeEvents,
  getRuntimeEventTimeline,
  getRuntimeIngressAudits,
  getRuntimeIngressDlq,
  getRuntimeOverview,
  replayRuntimeDelivery,
  replayRuntimeIngressDlq,
} from '#/api/adx';

defineOptions({ name: 'AdxRuntimeCenter' });

const loading = ref(false);
const activeTab = ref('events');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const eventId = ref('');
const direction = ref('');
const eventType = ref('');
const status = ref('');
const channelId = ref('');
const advertiserId = ref('');
const monitorId = ref('');
const timeRange = ref<[string, string]>();
const eventRows = ref<RuntimeEvent[]>([]);
const deliveryRows = ref<RuntimeDelivery[]>([]);
const auditRows = ref<RuntimeIngressAudit[]>([]);
const dlqRows = ref<RuntimeIngressDlq[]>([]);
const dlqTotal = ref(0);
const drawerVisible = ref(false);
const drawerTitle = ref('链路详情');
const timeline = ref<RuntimeTimelineItem[]>([]);
const attempts = ref<RuntimeDeliveryAttempt[]>([]);
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
    overview.value = await getRuntimeOverview();
    if (activeTab.value === 'events') {
      const result = await getRuntimeEvents({
        current: current.value,
        direction: direction.value || undefined,
        channelId: channelId.value || undefined,
        advertiserId: advertiserId.value || undefined,
        eventId: eventId.value || undefined,
        eventType: eventType.value || undefined,
        endTime: timeRange.value?.[1],
        monitorId: monitorId.value || undefined,
        size: size.value,
        status: status.value || undefined,
        startTime: timeRange.value?.[0],
      });
      eventRows.value = result.records;
      total.value = result.total;
    } else if (activeTab.value === 'deliveries') {
      const result = await getRuntimeDeliveries({
        current: current.value,
        direction: direction.value || undefined,
        size: size.value,
        status: status.value || undefined,
      });
      deliveryRows.value = result.records;
      total.value = result.total;
    } else {
      const [audits, dlq] = await Promise.all([
        getRuntimeIngressAudits({
          current: current.value,
          channelCode: channelId.value || undefined,
          endTime: timeRange.value?.[1],
          eventId: eventId.value || undefined,
          resultStatus: status.value || undefined,
          routeReference: monitorId.value || undefined,
          size: size.value,
          startTime: timeRange.value?.[0],
        }),
        getRuntimeIngressDlq({ current: 1, size: 20 }),
      ]);
      auditRows.value = audits.records;
      total.value = audits.total;
      dlqRows.value = dlq.records;
      dlqTotal.value = dlq.total;
    }
  } finally {
    loading.value = false;
  }
}

function changeTab() {
  current.value = 1;
  direction.value = '';
  eventType.value = '';
  channelId.value = '';
  advertiserId.value = '';
  monitorId.value = '';
  timeRange.value = undefined;
  status.value = '';
  void load();
}

async function showEvent(row: RuntimeEvent) {
  drawerTitle.value = `事件链路 · ${row.eventId}`;
  attempts.value = [];
  timeline.value = await getRuntimeEventTimeline(row.id);
  drawerVisible.value = true;
}

async function showDelivery(row: RuntimeDelivery) {
  drawerTitle.value = `投递详情 · ${row.id}`;
  [attempts.value, timeline.value] = await Promise.all([
    getRuntimeDeliveryAttempts(row.id),
    getRuntimeEventTimeline(row.sourceEventId),
  ]);
  drawerVisible.value = true;
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
        <p>查询事件事实、外发尝试和异常入口；敏感参数只保留脱敏摘要或哈希。</p>
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
      <el-tabs v-model="activeTab" @tab-change="changeTab">
        <el-tab-pane label="事件流水" name="events" />
        <el-tab-pane label="投递任务" name="deliveries" />
        <el-tab-pane label="入口异常 / DLQ" name="audits" />
      </el-tabs>

      <div class="filters">
        <el-input
          v-if="activeTab !== 'deliveries'"
          v-model="eventId"
          clearable
          placeholder="事件 ID"
        />
        <el-select
          v-if="activeTab !== 'audits'"
          v-model="direction"
          clearable
          placeholder="方向"
        >
          <el-option
            v-if="activeTab === 'events'"
            label="下游 Feedback"
            value="DOWNSTREAM_FEEDBACK"
          />
          <el-option
            v-if="activeTab === 'events'"
            label="上游 Callback"
            value="UPSTREAM_CALLBACK"
          />
          <el-option
            v-if="activeTab === 'deliveries'"
            label="Feedback → 上游"
            value="FEEDBACK_UPSTREAM"
          />
          <el-option
            v-if="activeTab === 'deliveries'"
            label="Callback → 下游"
            value="CALLBACK_DOWNSTREAM"
          />
        </el-select>
        <el-input
          v-if="activeTab === 'events'"
          v-model="eventType"
          clearable
          placeholder="事件类型，如 CLICK"
        />
        <el-input v-model="status" clearable placeholder="状态" />
        <el-input
          v-if="activeTab !== 'deliveries'"
          v-model="channelId"
          clearable
          :placeholder="activeTab === 'audits' ? '渠道编码' : '渠道 ID'"
        />
        <el-input
          v-if="activeTab === 'events'"
          v-model="advertiserId"
          clearable
          placeholder="广告主 ID"
        />
        <el-input
          v-if="activeTab !== 'deliveries'"
          v-model="monitorId"
          clearable
          placeholder="监测/路由 ID"
        />
        <el-date-picker
          v-if="activeTab !== 'deliveries'"
          v-model="timeRange"
          end-placeholder="结束时间"
          range-separator="至"
          start-placeholder="开始时间"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table
        v-if="activeTab === 'events'"
        v-loading="loading"
        :data="eventRows"
        border
        empty-text="暂无事件流水"
        @row-dblclick="showEvent"
      >
        <el-table-column
          prop="eventId"
          label="事件 ID"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column prop="direction" label="方向" min-width="190" />
        <el-table-column prop="eventType" label="类型" width="130" />
        <el-table-column prop="channelName" label="渠道" min-width="130" />
        <el-table-column prop="advertiserName" label="广告主" min-width="130" />
        <el-table-column prop="monitorName" label="监测链接" min-width="150" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="receivedAt" label="接收时间" min-width="175" />
        <el-table-column fixed="right" label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="showEvent(row)"
              >链路</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else-if="activeTab === 'deliveries'"
        v-loading="loading"
        :data="deliveryRows"
        border
        empty-text="暂无投递任务"
      >
        <el-table-column
          prop="eventId"
          label="事件 ID"
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
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDelivery(row)"
              >详情</el-button
            >
            <el-button
              v-if="row.status === 'DEAD'"
              v-access:code="'adx:runtime:replay'"
              link
              type="danger"
              @click="replay(row)"
            >
              重放
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        v-loading="loading"
        :data="auditRows"
        border
        empty-text="暂无入口异常"
      >
        <el-table-column prop="createTime" label="时间" min-width="175" />
        <el-table-column prop="resultStatus" label="结果" width="150" />
        <el-table-column prop="httpStatus" label="HTTP" width="80" />
        <el-table-column prop="channelCode" label="渠道" width="130" />
        <el-table-column
          prop="eventId"
          label="事件 ID"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="routeReference"
          label="路由引用"
          min-width="150"
        />
        <el-table-column
          prop="reason"
          label="脱敏原因"
          min-width="260"
          show-overflow-tooltip
        />
        <el-table-column prop="remoteIpMasked" label="来源 IP" width="140" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
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

    <el-card v-if="activeTab === 'audits'" class="dlq-card" shadow="never">
      <template #header><strong>入口 DLQ</strong></template>
      <el-table :data="dlqRows" border empty-text="暂无入口 DLQ">
        <el-table-column prop="eventId" label="事件 ID" min-width="220" />
        <el-table-column prop="partitionNo" label="分区" width="80" />
        <el-table-column
          prop="streamRecordId"
          label="Stream 记录"
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
              >重放</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <p class="dlq-total">共 {{ dlqTotal }} 条</p>
    </el-card>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="60%">
      <h3>链路时间线</h3>
      <el-timeline v-if="timeline.length > 0">
        <el-timeline-item
          v-for="item in timeline"
          :key="`${item.stage}-${item.referenceId}`"
          :timestamp="item.occurredAt"
        >
          <strong>{{ item.stage }} · {{ item.status }}</strong>
          <p>{{ item.detail || item.referenceId }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无链路记录" />

      <template v-if="attempts.length > 0">
        <h3>投递 Attempts</h3>
        <el-table :data="attempts" border>
          <el-table-column prop="attemptNo" label="#" width="55" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="httpStatus" label="HTTP" width="80" />
          <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
          <el-table-column
            prop="targetHost"
            label="目标 Host"
            min-width="150"
          />
          <el-table-column
            prop="targetPath"
            label="Path"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="requestSummary"
            label="脱敏请求摘要"
            min-width="240"
            show-overflow-tooltip
          />
          <el-table-column
            prop="responseExcerpt"
            label="脱敏响应摘要"
            min-width="220"
            show-overflow-tooltip
          />
          <el-table-column
            prop="errorMessage"
            label="业务/错误原因"
            min-width="220"
            show-overflow-tooltip
          />
        </el-table>
      </template>
    </el-drawer>
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

h3 {
  margin: 18px 0 12px;
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
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.filters :deep(.el-input),
.filters :deep(.el-select) {
  width: 210px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.dlq-card {
  margin-top: 16px;
}

.dlq-total {
  text-align: right;
}

@media (max-width: 1200px) {
  .metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
