<script lang="ts" setup>
import type {
  FlowLog,
  FlowLogOption,
  FlowLogRequestDetail,
  WireAuditDetail,
  WireAuditItem,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  exportWireAudits,
  getFlowLogOptions,
  getFlowLogRequestDetail,
  getFlowLogs,
  getWireAuditDetail,
  getWireAudits,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxFlowLog' });

const route = useRoute();
const { hasAccessByCodes } = useAccess();
const canViewRequestDetail = computed(() =>
  hasAccessByCodes(['ocpx:flow-log:detail']),
);
const canViewRaw = computed(() => hasAccessByCodes(['ocpx:flow-log:raw']));
const canExportRaw = computed(() =>
  hasAccessByCodes(['ocpx:flow-log:raw:export']),
);
const activeTab = ref<'raw' | 'stages'>('stages');

const stageLabels: Record<string, string> = {
  ADVERTISER_CALLBACK: '广告主 Callback',
  ADVERTISER_DELIVERY: '广告主出站 / 响应',
  CALLBACK_ACCOUNTING: '广告主计费',
  LEGACY: '历史日志',
  MEDIA_DEDUCTION: '媒体扣量',
  MEDIA_DELIVERY: '媒体出站 / 响应',
  MEDIA_INGRESS: '媒体入站',
};

const columns = [
  { key: 'time', label: '时间' },
  { key: 'flow', label: '流程' },
  { key: 'event', label: '事件' },
  { key: 'advertiser', label: '广告主' },
  { key: 'link', label: '推广链接' },
  { key: 'media', label: '媒体' },
  { key: 'monitor', label: '监测任务' },
  { key: 'result', label: '结果' },
  { key: 'http', label: 'HTTP' },
  { key: 'duration', label: '耗时' },
  { key: 'message', label: '说明' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<FlowLog[]>([]);
const loading = ref(false);
const total = ref(0);
const showSearch = ref(true);
const detailOpen = ref(false);
const detailLoading = ref(false);
const detailMessage = ref('');
const requestDetail = ref<FlowLogRequestDetail>();
const advertiserOptions = ref<FlowLogOption[]>([]);
const linkOptions = ref<FlowLogOption[]>([]);
const mediaOptions = ref<FlowLogOption[]>([]);
const monitorOptions = ref<FlowLogOption[]>([]);
const filters = reactive({
  advertiserId: '',
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string] | null,
  eventId: typeof route.query.eventId === 'string' ? route.query.eventId : '',
  flowType: '',
  mediaId: '',
  monitorTaskId: '',
  promotionLinkId: '',
  resultStatus: '',
  stageCode: '',
  size: 30,
});
const loadGuard = createLatestRequestGuard();
const optionGuard = createLatestRequestGuard();
const rawRows = ref<WireAuditItem[]>([]);
const rawLoading = ref(false);
const rawTotal = ref(0);
const rawDetailOpen = ref(false);
const rawDetailLoading = ref(false);
const rawDetail = ref<WireAuditDetail>();
const rawFilters = reactive({
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string],
  direction: '',
  httpStatus: '',
  protocol: '',
  rawState: '',
  requestId: '',
  resultStatus: '',
  size: 30,
  stageCode: '',
  traceId: '',
  trafficClass: '',
  wireSent: undefined as boolean | undefined,
});
const rawLoadGuard = createLatestRequestGuard();

function currentDates(): [string, string] {
  if (filters.dates?.[0] && filters.dates[1]) return filters.dates;
  filters.dates = [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ];
  return filters.dates;
}

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  const dates = currentDates();
  loading.value = true;
  try {
    const result = await getFlowLogs({
      advertiserId: filters.advertiserId || undefined,
      current: filters.current,
      endDate: dates[1],
      eventId: filters.eventId || undefined,
      flowType: filters.flowType || undefined,
      mediaId: filters.mediaId || undefined,
      monitorTaskId: filters.monitorTaskId || undefined,
      promotionLinkId: filters.promotionLinkId || undefined,
      resultStatus: filters.resultStatus || undefined,
      stageCode: filters.stageCode || undefined,
      size: filters.size,
      startDate: dates[0],
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

function search() {
  resetPage(filters);
  void load();
}

async function loadOptions() {
  const requestSequence = optionGuard.begin();
  const result = await getFlowLogOptions({
    advertiserId: filters.advertiserId || undefined,
    mediaId: filters.mediaId || undefined,
  });
  if (!optionGuard.isLatest(requestSequence)) return;
  advertiserOptions.value = result.advertisers;
  linkOptions.value = result.promotionLinks;
  mediaOptions.value = result.media;
  monitorOptions.value = result.monitorTasks;
}

async function changeAdvertiser() {
  filters.promotionLinkId = '';
  await loadOptions();
  search();
}

async function changeMedia() {
  filters.monitorTaskId = '';
  await loadOptions();
  search();
}

async function reset() {
  Object.assign(filters, {
    advertiserId: '',
    dates: [
      dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      dayjs().format('YYYY-MM-DD'),
    ],
    eventId: '',
    flowType: '',
    mediaId: '',
    monitorTaskId: '',
    promotionLinkId: '',
    resultStatus: '',
    stageCode: '',
  });
  await loadOptions();
  search();
}

async function openRequestDetail(row: FlowLog) {
  detailOpen.value = true;
  detailLoading.value = true;
  detailMessage.value = '';
  requestDetail.value = undefined;
  try {
    requestDetail.value = await getFlowLogRequestDetail(row.id);
  } catch {
    detailMessage.value = '流程日志不存在或暂时无法读取，请刷新后重试';
  } finally {
    detailLoading.value = false;
  }
}

function closeRequestDetail() {
  detailMessage.value = '';
  requestDetail.value = undefined;
}

function jumpToRaw(row: FlowLog) {
  if (!row.traceId) {
    ElMessage.warning('该阶段记录没有 Trace ID，无法自动关联原文');
    return;
  }
  const businessDate = dayjs(row.eventTime || row.createTime).format(
    'YYYY-MM-DD',
  );
  activeTab.value = 'raw';
  rawFilters.dates = [businessDate, businessDate];
  rawFilters.traceId = row.traceId;
  searchRaw();
}

function stageLabel(value: string) {
  return stageLabels[value] || value;
}

function formatted(value: Record<string, unknown>) {
  return Object.keys(value).length > 0 ? JSON.stringify(value, null, 2) : '';
}

async function loadRaw() {
  const requestSequence = rawLoadGuard.begin();
  rawLoading.value = true;
  try {
    const result = await getWireAudits({
      current: rawFilters.current,
      direction: rawFilters.direction || undefined,
      endDate: rawFilters.dates[1],
      httpStatus: rawFilters.httpStatus
        ? Number(rawFilters.httpStatus)
        : undefined,
      protocol: rawFilters.protocol || undefined,
      rawState: rawFilters.rawState || undefined,
      requestId: rawFilters.requestId || undefined,
      resultStatus: rawFilters.resultStatus || undefined,
      size: rawFilters.size,
      stageCode: rawFilters.stageCode || undefined,
      startDate: rawFilters.dates[0],
      traceId: rawFilters.traceId || undefined,
      trafficClass: rawFilters.trafficClass || undefined,
      wireSent: rawFilters.wireSent,
    });
    if (rawLoadGuard.isLatest(requestSequence)) {
      rawRows.value = result.records;
      rawTotal.value = result.total;
    }
  } finally {
    if (rawLoadGuard.isLatest(requestSequence)) rawLoading.value = false;
  }
}

function searchRaw() {
  resetPage(rawFilters);
  void loadRaw();
}

function resetRaw() {
  Object.assign(rawFilters, {
    current: 1,
    dates: [
      dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      dayjs().format('YYYY-MM-DD'),
    ],
    direction: '',
    httpStatus: '',
    protocol: '',
    rawState: '',
    requestId: '',
    resultStatus: '',
    stageCode: '',
    traceId: '',
    trafficClass: '',
    wireSent: undefined,
  });
  void loadRaw();
}

async function openRawDetail(row: WireAuditItem) {
  rawDetailOpen.value = true;
  rawDetailLoading.value = true;
  rawDetail.value = undefined;
  try {
    rawDetail.value = await getWireAuditDetail(row.id);
  } finally {
    rawDetailLoading.value = false;
  }
}

function closeRawDetail() {
  rawDetail.value = undefined;
}

async function downloadRaw() {
  if (rawFilters.dates[0] !== rawFilters.dates[1]) {
    ElMessage.warning('原始请求导出必须选择同一个自然日');
    return;
  }
  await exportWireAudits(rawFilters.dates[0], {
    direction: rawFilters.direction || undefined,
    httpStatus: rawFilters.httpStatus
      ? Number(rawFilters.httpStatus)
      : undefined,
    protocol: rawFilters.protocol || undefined,
    rawState: rawFilters.rawState || undefined,
    requestId: rawFilters.requestId || undefined,
    resultStatus: rawFilters.resultStatus || undefined,
    stageCode: rawFilters.stageCode || undefined,
    traceId: rawFilters.traceId || undefined,
    trafficClass: rawFilters.trafficClass || undefined,
    wireSent: rawFilters.wireSent,
  });
}

onMounted(async () => {
  await Promise.all([loadOptions(), load(), loadRaw()]);
});
</script>

<template>
  <Page auto-content-height title="流程日志">
    <el-tabs v-model="activeTab" class="flow-log-tabs">
      <el-tab-pane label="阶段日志" name="stages" />
      <el-tab-pane label="原始请求审计" name="raw" />
    </el-tabs>
    <el-card v-show="activeTab === 'stages'" shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-date-picker
          v-model="filters.dates"
          aria-label="流程日志日期范围"
          :clearable="false"
          end-placeholder="结束日期"
          range-separator="至"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="search"
        />
        <el-select
          v-model="filters.advertiserId"
          aria-label="流程日志广告主"
          clearable
          filterable
          placeholder="广告主"
          @change="changeAdvertiser"
        >
          <el-option
            v-for="item in advertiserOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.promotionLinkId"
          aria-label="流程日志推广链接"
          clearable
          filterable
          placeholder="推广链接"
          @change="search"
        >
          <el-option
            v-for="item in linkOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.mediaId"
          aria-label="流程日志媒体"
          clearable
          filterable
          placeholder="媒体"
          @change="changeMedia"
        >
          <el-option
            v-for="item in mediaOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.monitorTaskId"
          aria-label="流程日志监测任务"
          clearable
          filterable
          placeholder="监测任务"
          @change="search"
        >
          <el-option
            v-for="item in monitorOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.flowType"
          aria-label="流程类型"
          clearable
          placeholder="流程"
          @change="search"
          ><el-option label="Feedback" value="FEEDBACK" /><el-option
            label="Callback"
            value="CALLBACK"
        /></el-select>
        <el-select
          v-model="filters.resultStatus"
          aria-label="流程结果"
          clearable
          placeholder="结果"
          @change="search"
          ><el-option label="成功" value="SUCCESS" /><el-option
            label="失败"
            value="FAILED"
        /></el-select>
        <el-select
          v-model="filters.stageCode"
          aria-label="链路阶段"
          clearable
          placeholder="阶段"
          @change="search"
        >
          <el-option
            v-for="(label, value) in stageLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <el-input
          v-model="filters.eventId"
          aria-label="事件 ID"
          clearable
          placeholder="事件 ID"
          @clear="search"
          @keyup.enter="search"
        />
        <el-button :disabled="loading" type="primary" @click="search"
          >查询</el-button
        >
        <el-button :disabled="loading" @click="reset">重置</el-button>
      </div>
      <div class="ocpx-actions">
        <span></span>
        <RightToolbar
          v-model:visible-columns="visibleColumns"
          :columns="columns"
          :loading="loading"
          refresh-btn
          search-btn
          @refresh="load"
          @search="showSearch = !showSearch"
        />
      </div>
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column
          v-if="hasColumn('time')"
          label="时间"
          min-width="180"
          prop="eventTime"
        />
        <el-table-column
          v-if="hasColumn('flow')"
          label="流程"
          min-width="110"
          prop="flowType"
        />
        <el-table-column v-if="hasColumn('event')" label="事件" min-width="210"
          ><template #default="{ row }"
            ><LongText :value="`${row.eventCode} / ${row.eventId}`" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('advertiser')"
          label="广告主"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('link')"
          label="推广链接"
          min-width="150"
          prop="promotionLinkName"
        />
        <el-table-column
          v-if="hasColumn('media')"
          label="媒体"
          min-width="140"
          prop="mediaName"
        />
        <el-table-column
          v-if="hasColumn('monitor')"
          label="监测任务"
          min-width="150"
          prop="monitorTaskName"
        />
        <el-table-column
          v-if="hasColumn('result')"
          label="结果"
          min-width="100"
          prop="resultStatus"
        />
        <el-table-column
          v-if="hasColumn('http')"
          label="HTTP"
          min-width="80"
          prop="partnerHttpStatus"
        />
        <el-table-column
          v-if="hasColumn('duration')"
          label="耗时(ms)"
          min-width="100"
          prop="durationMs"
        />
        <el-table-column
          v-if="hasColumn('message')"
          label="说明"
          min-width="220"
          ><template #default="{ row }"
            ><LongText :value="row.message" /></template
        ></el-table-column>
        <el-table-column
          v-if="canViewRequestDetail || canViewRaw"
          fixed="right"
          label="操作"
          min-width="130"
        >
          <template #default="{ row }">
            <el-button
              v-if="canViewRequestDetail"
              link
              type="primary"
              @click="openRequestDetail(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="canViewRaw"
              :disabled="!row.traceId"
              link
              type="primary"
              @click="jumpToRaw(row)"
            >
              关联原文
            </el-button>
          </template>
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
    <el-card v-show="activeTab === 'raw'" shadow="never">
      <el-alert
        :closable="false"
        class="flow-detail-notice"
        show-icon
        title="原文采用 AES-GCM 加密保存 30 天，元数据保存 180 天；只有系统管理员可以解密查看或导出。"
        type="warning"
      />
      <div class="ocpx-query">
        <el-date-picker
          v-model="rawFilters.dates"
          :clearable="false"
          end-placeholder="结束日期"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
          @change="searchRaw"
        />
        <el-select
          v-model="rawFilters.stageCode"
          clearable
          placeholder="阶段"
          @change="searchRaw"
        >
          <el-option
            v-for="(label, value) in stageLabels"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
        <el-select
          v-model="rawFilters.direction"
          clearable
          placeholder="方向"
          @change="searchRaw"
        >
          <el-option label="入站" value="INBOUND" />
          <el-option label="出站" value="OUTBOUND" />
          <el-option label="内部" value="INTERNAL" />
        </el-select>
        <el-input
          v-model="rawFilters.protocol"
          clearable
          placeholder="协议"
          @keyup.enter="searchRaw"
        />
        <el-select
          v-model="rawFilters.resultStatus"
          clearable
          placeholder="结果"
          @change="searchRaw"
        >
          <el-option label="处理中" value="PENDING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="中断" value="INTERRUPTED" />
        </el-select>
        <el-input
          v-model="rawFilters.httpStatus"
          clearable
          maxlength="3"
          placeholder="HTTP 状态"
          @keyup.enter="searchRaw"
        />
        <el-select
          v-model="rawFilters.rawState"
          clearable
          placeholder="原文状态"
          @change="searchRaw"
        >
          <el-option label="完整" value="COMPLETE" />
          <el-option label="处理中" value="PENDING" />
          <el-option label="中断" value="INTERRUPTED" />
          <el-option label="已过期" value="EXPIRED" />
        </el-select>
        <el-select
          v-model="rawFilters.trafficClass"
          clearable
          placeholder="流量类型"
          @change="searchRaw"
        >
          <el-option label="正式" value="FORMAL" />
          <el-option label="测试" value="TEST" />
          <el-option label="集成" value="INTEGRATION" />
        </el-select>
        <el-select
          v-model="rawFilters.wireSent"
          clearable
          placeholder="是否发网"
          @change="searchRaw"
        >
          <el-option label="已发网" :value="true" />
          <el-option label="未发网" :value="false" />
        </el-select>
        <el-input
          v-model="rawFilters.requestId"
          clearable
          placeholder="request_id"
          @keyup.enter="searchRaw"
        />
        <el-input
          v-model="rawFilters.traceId"
          clearable
          placeholder="Trace ID"
          @keyup.enter="searchRaw"
        />
        <el-button type="primary" @click="searchRaw">查询</el-button>
        <el-button @click="resetRaw">重置</el-button>
        <el-button
          v-if="canExportRaw"
          :loading="rawLoading"
          @click="downloadRaw"
          >导出单日 ZIP</el-button
        >
      </div>
      <el-table
        v-loading="rawLoading"
        :data="rawRows"
        max-height="560"
        size="small"
      >
        <el-table-column label="采集时间" min-width="180" prop="capturedAt" />
        <el-table-column label="阶段" min-width="150">
          <template #default="{ row }">{{
            stageLabel(row.stageCode)
          }}</template>
        </el-table-column>
        <el-table-column label="方向" min-width="90" prop="direction" />
        <el-table-column label="协议" min-width="120" prop="protocol" />
        <el-table-column label="端点" min-width="230">
          <template #default="{ row }"
            ><LongText
              :value="`${row.requestMethod || '-'} ${row.requestPath || '-'}`"
          /></template>
        </el-table-column>
        <el-table-column label="状态" min-width="100" prop="resultStatus" />
        <el-table-column label="HTTP" min-width="80" prop="partnerHttpStatus" />
        <el-table-column label="原文" min-width="100" prop="rawState" />
        <el-table-column label="是否发网" min-width="90">
          <template #default="{ row }">{{
            row.wireSent ? '是' : '否'
          }}</template>
        </el-table-column>
        <el-table-column
          v-if="canViewRaw"
          fixed="right"
          label="操作"
          min-width="90"
        >
          <template #default="{ row }">
            <el-button
              :disabled="row.rawState === 'EXPIRED'"
              link
              type="primary"
              @click="openRawDetail(row)"
              >原文</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="rawFilters.current"
        v-model:page-size="rawFilters.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="rawTotal"
        @change="loadRaw"
      />
    </el-card>
    <el-dialog
      v-model="rawDetailOpen"
      destroy-on-close
      title="原始请求审计"
      width="min(1040px, 94vw)"
      @closed="closeRawDetail"
    >
      <div v-loading="rawDetailLoading" class="flow-detail-body">
        <template v-if="rawDetail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="审计 ID">{{
              rawDetail.metadata.id
            }}</el-descriptions-item>
            <el-descriptions-item label="Key / 版本"
              >{{ rawDetail.metadata.keyId }} /
              {{ rawDetail.metadata.cryptoVersion }}</el-descriptions-item
            >
            <el-descriptions-item label="请求 SHA-256" :span="2"
              ><LongText :value="rawDetail.metadata.requestSha256" wrap
            /></el-descriptions-item>
            <el-descriptions-item label="响应 SHA-256" :span="2"
              ><LongText :value="rawDetail.metadata.responseSha256" wrap
            /></el-descriptions-item>
          </el-descriptions>
          <h3>应用层请求原文</h3>
          <pre class="response-preview">{{
            JSON.stringify(rawDetail.request, null, 2)
          }}</pre>
          <h3>应用层响应原文</h3>
          <pre class="response-preview">{{
            JSON.stringify(rawDetail.response, null, 2)
          }}</pre>
        </template>
        <el-empty
          v-else-if="!rawDetailLoading"
          description="原文已过期或无法读取"
        />
      </div>
      <template #footer
        ><el-button @click="rawDetailOpen = false">关闭</el-button></template
      >
    </el-dialog>
    <el-dialog
      v-model="detailOpen"
      :close-on-click-modal="false"
      destroy-on-close
      title="流程日志详情"
      top="5vh"
      width="min(1040px, 94vw)"
      @closed="closeRequestDetail"
    >
      <el-alert
        :closable="false"
        class="flow-detail-notice"
        show-icon
        title="请求结果来自流程日志；响应仅展示脱敏后的最多 4096 字符预览。下游请求数据来自 Engine 异步规范化快照，不包含原始请求 URL、签名或凭据。"
        type="info"
      />
      <div v-loading="detailLoading" class="flow-detail-body">
        <template v-if="requestDetail">
          <el-alert
            v-if="requestDetail.coverageStatus !== 'TRACE_AVAILABLE'"
            :closable="false"
            class="flow-detail-notice"
            show-icon
            :title="
              requestDetail.coverageStatus === 'HISTORICAL_INCOMPLETE'
                ? '这是 V119 之前的历史日志，链路阶段不完整。'
                : '七日请求明细已经清除，仍保留九十日脱敏阶段元数据。'
            "
            type="warning"
          />
          <section class="flow-detail-section flow-detail-section--result">
            <div class="flow-detail-heading">
              <div>
                <h3>完整链路时间线</h3>
                <p>
                  Trace {{ requestDetail.traceId || '历史记录无 Trace ID' }}
                </p>
              </div>
              <el-tag type="info"
                >{{ requestDetail.stages.length }} 个阶段</el-tag
              >
            </div>
            <el-timeline
              v-if="requestDetail.stages.length > 0"
              class="flow-timeline"
            >
              <el-timeline-item
                v-for="stage in requestDetail.stages"
                :key="stage.id"
                :color="
                  stage.resultStatus === 'SUCCESS' ? '#16a34a' : '#dc2626'
                "
                :timestamp="stage.eventTime"
                placement="top"
              >
                <article class="flow-stage-card">
                  <div class="flow-stage-title">
                    <strong>{{ stageLabel(stage.stageCode) }}</strong>
                    <el-tag
                      :type="
                        stage.resultStatus === 'SUCCESS' ? 'success' : 'danger'
                      "
                      size="small"
                      >{{
                        stage.resultStatus === 'SUCCESS' ? '成功' : '失败'
                      }}</el-tag
                    >
                  </div>
                  <el-descriptions :column="3" border size="small">
                    <el-descriptions-item label="方向 / 事件">
                      {{ stage.flowType }} / {{ stage.eventCode }}
                    </el-descriptions-item>
                    <el-descriptions-item label="端点">
                      {{ stage.requestMethod || '-' }}
                      {{ stage.requestEndpoint || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="HTTP / 耗时">
                      {{ stage.partnerHttpStatus ?? '-' }} /
                      {{ stage.durationMs }} ms
                    </el-descriptions-item>
                    <el-descriptions-item label="稳定结论码" :span="3">
                      {{ stage.message || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item
                      v-if="formatted(stage.requestParams)"
                      label="请求参数（受限原值）"
                      :span="3"
                    >
                      <pre class="response-preview">{{
                        formatted(stage.requestParams)
                      }}</pre>
                      <span
                        v-if="stage.requestTruncated"
                        class="flow-detail-muted"
                        >请求快照已达到大小上限</span
                      >
                    </el-descriptions-item>
                    <el-descriptions-item
                      v-if="formatted(stage.macroAudit)"
                      label="宏审计"
                      :span="3"
                    >
                      <pre class="response-preview">{{
                        formatted(stage.macroAudit)
                      }}</pre>
                    </el-descriptions-item>
                    <el-descriptions-item
                      v-if="stage.responsePreview"
                      label="响应预览（已脱敏）"
                      :span="3"
                    >
                      <pre class="response-preview">{{
                        stage.responsePreview
                      }}</pre>
                    </el-descriptions-item>
                  </el-descriptions>
                </article>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无阶段数据" :image-size="72" />
          </section>
          <section class="flow-detail-section flow-detail-section--result">
            <div class="flow-detail-heading">
              <div>
                <h3>请求结果</h3>
                <p>用于判断本次请求到达哪一步，以及上游返回了什么。</p>
              </div>
              <el-tag
                :type="
                  requestDetail.resultStatus === 'SUCCESS'
                    ? 'success'
                    : 'danger'
                "
              >
                {{ requestDetail.resultStatus === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </div>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="流程 / 事件">
                {{ requestDetail.flowType }} / {{ requestDetail.eventCode }}
              </el-descriptions-item>
              <el-descriptions-item label="HTTP 状态">
                {{ requestDetail.partnerHttpStatus ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="耗时">
                {{ requestDetail.durationMs }} ms
              </el-descriptions-item>
              <el-descriptions-item label="业务结论" :span="2">
                <LongText :value="requestDetail.message" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="事件时间">
                {{ requestDetail.eventTime || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="事件 ID" :span="2">
                <LongText :value="requestDetail.eventId" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="源事件 ID">
                <LongText :value="requestDetail.sourceEventId" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="流程日志 ID" :span="3">
                <LongText :value="requestDetail.flowLogId" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="响应预览（已脱敏）" :span="3">
                <pre
                  v-if="requestDetail.responsePreview"
                  class="response-preview"
                  >{{ requestDetail.responsePreview }}</pre>
                <span v-else class="flow-detail-muted">
                  历史记录、请求未到达上游或上游未返回正文，暂无响应预览
                </span>
              </el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="flow-detail-section">
            <div class="flow-detail-heading">
              <div>
                <h3>下游请求数据</h3>
                <p>展示解析后的业务字段，便于核对设备参数与路由关系。</p>
              </div>
              <el-tag v-if="requestDetail.feedbackLogId" type="info">
                快照 #{{ requestDetail.feedbackLogId }}
              </el-tag>
            </div>
            <el-descriptions
              v-if="requestDetail.feedbackLogId"
              :column="2"
              border
              size="small"
            >
              <el-descriptions-item label="接收时间">
                {{ requestDetail.receivedAt || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="明细 ID">
                {{ requestDetail.feedbackLogId }}
              </el-descriptions-item>
              <el-descriptions-item label="request_id">
                <LongText :value="requestDetail.requestId" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="clickid / impresss_id">
                <LongText
                  :value="`${requestDetail.clickId || '-'} / ${requestDetail.impressionId || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="OS / 系统版本">
                {{ requestDetail.deviceOs || '-' }} /
                {{ requestDetail.osVersion || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="设备型号">
                <LongText :value="requestDetail.model" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="主标识 / 类型">
                <LongText
                  :value="`${requestDetail.userId || '-'} / ${requestDetail.userType || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="IMEI / IMEI MD5">
                <LongText
                  :value="`${requestDetail.imei || '-'} / ${requestDetail.imeiMd5 || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="OAID / OAID MD5">
                <LongText
                  :value="`${requestDetail.oaid || '-'} / ${requestDetail.oaidMd5 || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="Android ID / MD5">
                <LongText
                  :value="`${requestDetail.androidId || '-'} / ${requestDetail.androidIdMd5 || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="IDFA / IDFA MD5">
                <LongText
                  :value="`${requestDetail.idfa || '-'} / ${requestDetail.idfaMd5 || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="AAID / MAC">
                <LongText
                  :value="`${requestDetail.aaid || '-'} / ${requestDetail.mac || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="CAID / CAID1 / CAID2" :span="2">
                <LongText
                  :value="`${requestDetail.caid || '-'} / ${requestDetail.caid1 || '-'} / ${requestDetail.caid2 || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="CAID 列表" :span="2">
                <LongText :value="requestDetail.caidList" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="上次 / 前次 CAID" :span="2">
                <LongText
                  :value="`${requestDetail.lastCaid || '-'} / ${requestDetail.previousCaid || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="IPv4 / IPv6" :span="2">
                <LongText
                  :value="`${requestDetail.ip || '-'} / ${requestDetail.ipv6 || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="时间戳">
                {{ requestDetail.timestamp || '-' }} /
                {{ requestDetail.timestampMs || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="User-Agent">
                <LongText :value="requestDetail.ua" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="callback_url" :span="2">
                <LongText :value="requestDetail.callbackUrl" wrap />
              </el-descriptions-item>
              <el-descriptions-item label="外部广告维度" :span="2">
                <LongText
                  :value="`adv=${requestDetail.externalAdvertiserId || '-'} / group=${requestDetail.groupId || '-'} / plan=${requestDetail.planId || '-'} / creative=${requestDetail.creativeId || '-'}`"
                  wrap
                />
              </el-descriptions-item>
              <el-descriptions-item label="SCX 路由关系" :span="2">
                <LongText
                  :value="`mId=${requestDetail.mediaId || '-'} / tId=${requestDetail.monitorTaskId || '-'} / aId=${requestDetail.advertiserId || '-'} / lId=${requestDetail.promotionLinkId || '-'}`"
                  wrap
                />
              </el-descriptions-item>
            </el-descriptions>
            <el-empty
              v-else
              description="请求参数快照尚未异步落库或已过期；上方请求结果仍然有效"
              :image-size="72"
            />
          </section>
        </template>
        <el-empty
          v-else-if="!detailLoading"
          :description="detailMessage || '暂无流程日志详情'"
        />
      </div>
      <template #footer>
        <el-button @click="detailOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </Page>
</template>

<style scoped>
.flow-detail-notice {
  margin-bottom: 16px;
}

.flow-detail-body {
  min-height: 180px;
}

.flow-detail-section {
  padding: 18px;
  margin-bottom: 16px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.flow-detail-section--result {
  background: var(--el-bg-color);
  border-color: var(--el-border-color);
}

.flow-detail-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.flow-detail-heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.flow-detail-heading p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.response-preview {
  max-height: 240px;
  padding: 12px;
  margin: 0;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--el-fill-color-dark);
  border-radius: 6px;
}

.flow-detail-muted {
  color: var(--el-text-color-secondary);
}

.flow-timeline {
  padding-top: 8px;
}

.flow-stage-card {
  min-width: 0;
}

.flow-stage-title {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
</style>
