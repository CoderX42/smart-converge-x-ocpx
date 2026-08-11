<script lang="ts" setup>
import type {
  RuntimeFact,
  RuntimeFailure,
  RuntimeOverview,
  RuntimeReplayPreview,
  RuntimeTimeline,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import { Refresh, Search, WarningFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import {
  confirmRuntimeReplay,
  getRuntimeClicks,
  getRuntimeConversions,
  getRuntimeExecution,
  getRuntimeFacts,
  getRuntimeFailures,
  getRuntimeOverview,
  previewRuntimeReplay,
  secondaryAuthenticate,
} from '#/api/ocpx';

import { canConfirmRuntimeReplay } from './runtime-model';

defineOptions({ name: 'OcpxRuntimeCenter' });

type Tab = 'clicks' | 'conversions' | 'dlq' | 'facts';

const { hasAccessByCodes } = useAccess();
const canView = computed(() => hasAccessByCodes(['ocpx:runtime:view']));
const canDetail = computed(() => hasAccessByCodes(['ocpx:runtime:detail']));
const canViewDlq = computed(() => hasAccessByCodes(['ocpx:runtime:dlq:view']));
const canReplay = computed(() => hasAccessByCodes(['ocpx:runtime:replay']));

const activeTab = ref<Tab>('facts');
const loading = ref(false);
const initialized = ref(false);
const forbidden = ref(false);
const errorMessage = ref('');
const traceId = ref('');
const freshness = ref<'DELAYED' | 'FINAL' | 'PROVISIONAL'>('FINAL');
const asOf = ref('');
const qualityFlags = ref<string[]>([]);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const factRows = ref<RuntimeFact[]>([]);
const failureRows = ref<RuntimeFailure[]>([]);
const overview = ref<RuntimeOverview>();
const filters = reactive({
  advertiserId: '',
  channelId: '',
  direction: '',
  endTime: '',
  eventCode: '',
  exactId: '',
  failureClass: '',
  mediaAccountId: '',
  startTime: '',
  status: '',
  taskId: '',
  traceId: '',
});
const timeRange = computed<[string, string] | undefined>({
  get: () =>
    filters.startTime && filters.endTime
      ? ([filters.startTime, filters.endTime] as [string, string])
      : undefined,
  set: (value: [string, string] | undefined) => {
    filters.startTime = value?.[0] || '';
    filters.endTime = value?.[1] || '';
  },
});
const filtersActive = computed(() => Object.values(filters).some(Boolean));
const rowsEmpty = computed(() =>
  activeTab.value === 'dlq'
    ? failureRows.value.length === 0
    : factRows.value.length === 0,
);

const cards = computed(() => [
  { label: '已接收事实', value: overview.value?.accepted ?? 0 },
  { label: '处理成功', value: overview.value?.succeeded ?? 0 },
  { label: '处理失败', value: overview.value?.failed ?? 0 },
  { label: '失败队列', value: overview.value?.dlq ?? 0 },
  { label: '补偿排队', value: overview.value?.replayQueued ?? 0 },
  { label: '终止命令', value: overview.value?.replayTerminal ?? 0 },
]);

function query() {
  return {
    advertiserId: filters.advertiserId || undefined,
    channelId: filters.channelId || undefined,
    current: current.value,
    direction: filters.direction || undefined,
    endTime: filters.endTime || undefined,
    eventCode: filters.eventCode || undefined,
    exactId: filters.exactId.trim() || undefined,
    failureClass: filters.failureClass || undefined,
    mediaAccountId: filters.mediaAccountId || undefined,
    size: size.value,
    startTime: filters.startTime || undefined,
    status: filters.status || undefined,
    taskId: filters.taskId.trim() || undefined,
    traceId: filters.traceId.trim() || undefined,
  };
}

async function load() {
  if (!canView.value) {
    forbidden.value = true;
    initialized.value = true;
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  forbidden.value = false;
  try {
    const overviewRequest = getRuntimeOverview();
    let pageRequest;
    switch (activeTab.value) {
      case 'clicks': {
        pageRequest = getRuntimeClicks(query());
        break;
      }
      case 'conversions': {
        pageRequest = getRuntimeConversions(query());
        break;
      }
      case 'dlq': {
        pageRequest = getRuntimeFailures(query());
        break;
      }
      default: {
        pageRequest = getRuntimeFacts(query());
      }
    }
    const [overviewResult, page] = await Promise.all([
      overviewRequest,
      pageRequest,
    ]);
    overview.value = overviewResult;
    total.value = page.total;
    asOf.value = page.asOf;
    freshness.value = page.freshnessStatus;
    traceId.value = page.traceId;
    qualityFlags.value = page.qualityFlags;
    if (activeTab.value === 'dlq') {
      failureRows.value = page.records as RuntimeFailure[];
      factRows.value = [];
    } else {
      factRows.value = page.records as RuntimeFact[];
      failureRows.value = [];
    }
  } catch (error: any) {
    factRows.value = [];
    failureRows.value = [];
    total.value = 0;
    forbidden.value = error?.response?.status === 403 || error?.status === 403;
    traceId.value = error?.response?.data?.traceId || error?.traceId || '';
    errorMessage.value = forbidden.value
      ? ''
      : error?.message || '运行事实加载失败';
  } finally {
    loading.value = false;
    initialized.value = true;
  }
}

async function search() {
  current.value = 1;
  await load();
}

async function resetFilters() {
  Object.assign(filters, {
    advertiserId: '',
    channelId: '',
    direction: '',
    endTime: '',
    eventCode: '',
    exactId: '',
    failureClass: '',
    mediaAccountId: '',
    startTime: '',
    status: '',
    taskId: '',
    traceId: '',
  });
  await search();
}

async function changeTab(name: Tab) {
  activeTab.value = name;
  current.value = 1;
  await load();
}

const detailOpen = ref(false);
const detailLoading = ref(false);
const timeline = ref<RuntimeTimeline>();

async function openDetail(id: string) {
  if (!canDetail.value) return;
  detailOpen.value = true;
  detailLoading.value = true;
  timeline.value = undefined;
  try {
    timeline.value = await getRuntimeExecution(id);
  } catch (error: any) {
    ElMessage.error(error?.message || '详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

const replayOpen = ref(false);
const replayLoading = ref(false);
const replayFailure = ref<RuntimeFailure>();
const replayPreview = ref<RuntimeReplayPreview>();
const replayForm = reactive({ password: '', reason: '', ticket: '' });

async function openReplay(row: RuntimeFailure) {
  replayFailure.value = row;
  replayPreview.value = undefined;
  Object.assign(replayForm, { password: '', reason: '', ticket: '' });
  replayOpen.value = true;
  replayLoading.value = true;
  try {
    replayPreview.value = await previewRuntimeReplay({
      expectedFailureRevision: row.failureRevision,
      failureId: row.id,
    });
  } catch (error: any) {
    ElMessage.error(error?.message || '补偿预览失败');
    replayOpen.value = false;
  } finally {
    replayLoading.value = false;
  }
}

const replayBlocked = computed(
  () =>
    !canConfirmRuntimeReplay({
      password: replayForm.password,
      preview: replayPreview.value,
      reason: replayForm.reason,
    }),
);

async function confirmReplay() {
  const preview = replayPreview.value;
  const failure = replayFailure.value;
  if (!preview || !failure || replayBlocked.value) return;
  replayLoading.value = true;
  try {
    await secondaryAuthenticate(replayForm.password);
    const result = await confirmRuntimeReplay({
      confirmed: true,
      expectedFailureRevision: failure.failureRevision,
      failureId: failure.id,
      previewToken: preview.previewToken,
      reason: replayForm.reason.trim(),
      requestId: crypto.randomUUID().replaceAll('-', ''),
      ticket: replayForm.ticket.trim() || undefined,
    });
    ElMessage.success(
      result.workerStatus === 'DISABLED'
        ? '补偿命令已排队；当前 worker 默认关闭，尚未执行'
        : '补偿命令已排队',
    );
    replayOpen.value = false;
    await load();
  } catch (error: any) {
    ElMessage.error(error?.message || '补偿确认失败，请重新预览');
  } finally {
    replayLoading.value = false;
  }
}

function freshnessType() {
  if (freshness.value === 'FINAL') return 'success';
  if (freshness.value === 'DELAYED') return 'danger';
  return 'warning';
}

onMounted(load);
</script>

<template>
  <div class="runtime-page">
    <div class="page-heading">
      <div>
        <h1>运行中心</h1>
        <p>
          查看冻结范围内的接入、点击、转化与失败事实。正文、设备原值、签名和凭据不会在此返回。
        </p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">
        刷新
      </el-button>
    </div>

    <el-alert
      v-if="overview?.replayWorkerStatus === 'DISABLED'"
      class="mb-4"
      :closable="false"
      show-icon
      title="补偿 worker 默认关闭：确认操作只会创建可恢复命令，不会立即外发或计费"
      type="info"
    />

    <section class="metric-grid" aria-label="运行概览">
      <el-card v-for="card in cards" :key="card.label" shadow="never">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-tabs :model-value="activeTab" @tab-change="changeTab">
        <el-tab-pane label="全部事实" name="facts" />
        <el-tab-pane label="点击明细" name="clicks" />
        <el-tab-pane label="转化明细" name="conversions" />
        <el-tab-pane v-if="canViewDlq" label="失败 / DLQ" name="dlq" />
      </el-tabs>

      <div class="filters">
        <el-input v-model="filters.exactId" clearable placeholder="精确 ID" />
        <el-input v-model="filters.traceId" clearable placeholder="traceId" />
        <el-input v-model="filters.taskId" clearable placeholder="Task ID" />
        <el-input
          v-model="filters.advertiserId"
          clearable
          placeholder="Advertiser ID"
        />
        <el-input
          v-model="filters.channelId"
          clearable
          placeholder="Channel ID"
        />
        <el-input
          v-model="filters.mediaAccountId"
          clearable
          placeholder="MediaAccount ID"
        />
        <el-select
          v-if="activeTab !== 'dlq'"
          v-model="filters.direction"
          clearable
          placeholder="方向"
        >
          <el-option label="接入" value="INGRESS" />
          <el-option label="向上游" value="UPSTREAM" />
          <el-option label="向下游" value="DOWNSTREAM" />
          <el-option label="内部" value="INTERNAL" />
        </el-select>
        <el-select
          v-if="activeTab === 'dlq'"
          v-model="filters.failureClass"
          clearable
          placeholder="失败分类"
        >
          <el-option label="可重试" value="RETRYABLE" />
          <el-option label="不可重试" value="NON_RETRYABLE" />
          <el-option label="协议拒绝" value="PROTOCOL_REJECTED" />
          <el-option label="签名失败" value="SIGNATURE_FAILED" />
          <el-option label="范围拒绝" value="SCOPE_REJECTED" />
          <el-option label="重复" value="DUPLICATE" />
          <el-option label="晚到" value="LATE_ARRIVAL" />
        </el-select>
        <el-input
          v-if="activeTab === 'conversions'"
          v-model="filters.eventCode"
          clearable
          placeholder="标准事件 code"
        />
        <el-date-picker
          v-model="timeRange"
          end-placeholder="结束时间（不含）"
          range-separator="至"
          start-placeholder="开始时间（含）"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
        <el-button :icon="Search" type="primary" @click="search">
          查询
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <div class="evidence-bar">
        <span>asOf：{{ asOf || '—' }}</span>
        <el-tag :type="freshnessType()">{{ freshness }}</el-tag>
        <el-tag v-if="qualityFlags.includes('UNMAPPED')" type="warning">
          包含 UNMAPPED，未猜测历史归属
        </el-tag>
        <span v-if="traceId">traceId：{{ traceId }}</span>
      </div>

      <el-skeleton v-if="loading && !initialized" :rows="8" animated />
      <el-result
        v-else-if="forbidden"
        icon="warning"
        sub-title="对象范围、记录总数和筛选候选均不会返回"
        title="无权查看运行中心"
      />
      <el-result
        v-else-if="errorMessage"
        icon="error"
        :sub-title="traceId ? `traceId：${traceId}` : '筛选条件已保留，可重试'"
        :title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>
      <el-empty
        v-else-if="initialized && !loading && rowsEmpty"
        :description="
          filtersActive
            ? '没有符合当前筛选的运行事实'
            : '当前范围内还没有运行事实'
        "
      >
        <el-button v-if="filtersActive" @click="resetFilters"
          >清空筛选</el-button
        >
      </el-empty>

      <el-table
        v-else-if="activeTab !== 'dlq'"
        v-loading="loading"
        :data="factRows"
        border
        class="runtime-table"
      >
        <el-table-column prop="receivedTime" label="接收时间" min-width="180" />
        <el-table-column prop="factType" label="事实类型" min-width="135" />
        <el-table-column prop="direction" label="方向" min-width="110" />
        <el-table-column prop="summaryCode" label="处理摘要" min-width="190" />
        <el-table-column prop="processingStatus" label="状态" min-width="120" />
        <el-table-column
          prop="taskId"
          label="Task"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="Route / Version" min-width="210">
          <template #default="{ row }">
            {{ row.routeId || 'UNMAPPED' }} / v{{ row.routeVersion || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="eventCode" label="标准事件" min-width="130" />
        <el-table-column
          prop="externalClickIdMasked"
          label="点击 ID 摘要"
          min-width="145"
        />
        <el-table-column
          prop="correlationIdMasked"
          label="关联摘要"
          min-width="145"
        />
        <el-table-column
          prop="dimensionStatus"
          label="维度质量"
          min-width="115"
        />
        <el-table-column fixed="right" label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-if="canDetail"
              link
              type="primary"
              @click="openDetail(row.id)"
            >
              时间线
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        v-loading="loading"
        :data="failureRows"
        border
        class="runtime-table"
      >
        <el-table-column prop="receivedTime" label="失败时间" min-width="180" />
        <el-table-column prop="failureClass" label="失败分类" min-width="165" />
        <el-table-column prop="errorCode" label="错误 code" min-width="200" />
        <el-table-column prop="taskId" label="Task" min-width="160" />
        <el-table-column label="原冻结版本" min-width="180">
          <template #default="{ row }">
            {{ row.routeVersionId || 'UNMAPPED' }} / v{{
              row.routeVersion || '—'
            }}
          </template>
        </el-table-column>
        <el-table-column
          prop="summaryFingerprint"
          label="摘要指纹"
          min-width="140"
        />
        <el-table-column
          prop="traceId"
          label="traceId"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="canDetail"
              link
              type="primary"
              @click="openDetail(row.id)"
            >
              详情
            </el-button>
            <el-button
              v-if="canReplay && row.replayable"
              link
              type="danger"
              @click="openReplay(row)"
            >
              补偿
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="!forbidden && !errorMessage && !rowsEmpty"
        v-model:current-page="current"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </el-card>

    <el-drawer
      v-model="detailOpen"
      size="min(760px, 94vw)"
      title="运行事实时间线"
    >
      <div v-loading="detailLoading">
        <el-descriptions v-if="timeline?.fact" :column="2" border>
          <el-descriptions-item label="事实">{{
            timeline.fact.id
          }}</el-descriptions-item>
          <el-descriptions-item label="摘要">{{
            timeline.fact.summaryCode
          }}</el-descriptions-item>
          <el-descriptions-item label="Task">{{
            timeline.fact.taskId || 'UNMAPPED'
          }}</el-descriptions-item>
          <el-descriptions-item label="Route"
            >v{{ timeline.fact.routeVersion || '—' }}</el-descriptions-item
          >
        </el-descriptions>
        <el-alert
          class="my-4"
          :closable="false"
          title="列表与普通详情只展示掩码摘要；原始 header、body、URL、设备值和凭据不回显"
          type="info"
        />
        <el-table
          :data="timeline?.attempts || []"
          border
          empty-text="暂无补偿 attempt"
        >
          <el-table-column prop="attemptNo" label="#" width="56" />
          <el-table-column prop="startedAt" label="开始" min-width="175" />
          <el-table-column prop="status" label="状态" min-width="120" />
          <el-table-column prop="errorClass" label="分类" min-width="145" />
          <el-table-column prop="errorCode" label="错误 code" min-width="180" />
          <el-table-column
            prop="summaryFingerprint"
            label="结果摘要"
            min-width="140"
          />
        </el-table>
      </div>
    </el-drawer>

    <el-dialog
      v-model="replayOpen"
      destroy-on-close
      title="补偿预览与确认"
      width="min(680px, 94vw)"
    >
      <div v-loading="replayLoading" class="space-y-4">
        <el-alert
          v-if="replayPreview?.blockers.length"
          :closable="false"
          :title="replayPreview.blockers.join('；')"
          type="error"
        />
        <el-alert
          v-else
          :closable="false"
          show-icon
          title="确认只创建数据库补偿命令，不会在当前请求中访问第三方"
          type="warning"
        />
        <el-descriptions v-if="replayPreview" :column="1" border>
          <el-descriptions-item label="原失败">
            {{ replayPreview.failureClass }} / {{ replayPreview.errorCode }}
          </el-descriptions-item>
          <el-descriptions-item label="冻结版本">
            {{ replayPreview.routeVersionId }} / v{{
              replayPreview.routeVersion
            }}
          </el-descriptions-item>
          <el-descriptions-item label="模式">只使用原快照</el-descriptions-item>
          <el-descriptions-item label="外发影响">{{
            replayPreview.externalEffect
          }}</el-descriptions-item>
          <el-descriptions-item label="计费影响">{{
            replayPreview.billingEffect
          }}</el-descriptions-item>
          <el-descriptions-item label="命令执行">
            {{
              replayPreview.workerStatus === 'DISABLED'
                ? '默认关闭，确认后仅排队'
                : '异步 worker'
            }}
          </el-descriptions-item>
          <el-descriptions-item label="预览过期">{{
            replayPreview.expiresAt
          }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top">
          <el-form-item label="原因（必填）">
            <el-input
              v-model="replayForm.reason"
              :rows="3"
              maxlength="500"
              show-word-limit
              type="textarea"
            />
          </el-form-item>
          <el-form-item label="工单号（可选）">
            <el-input v-model="replayForm.ticket" maxlength="128" />
          </el-form-item>
          <el-form-item label="二次认证密码">
            <el-input
              v-model="replayForm.password"
              autocomplete="new-password"
              show-password
              type="password"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="replayOpen = false">取消</el-button>
        <el-button
          :disabled="replayBlocked"
          :icon="WarningFilled"
          :loading="replayLoading"
          type="danger"
          @click="confirmReplay"
        >
          确认创建补偿命令
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.runtime-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.page-heading {
  display: flex;
  gap: 16px;
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

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-grid span {
  display: block;
  font-size: 13px;
  color: #667085;
}

.metric-grid strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  color: #172033;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.filters :deep(.el-input),
.filters :deep(.el-select) {
  width: 190px;
}

.evidence-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-height: 34px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #667085;
}

.runtime-table {
  width: 100%;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .runtime-page {
    padding: 12px;
  }

  .page-heading {
    flex-direction: column;
    align-items: stretch;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
