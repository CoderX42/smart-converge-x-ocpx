<script lang="ts" setup>
import type { FlowLog, FlowLogRequestDetail } from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import { getFlowLogRequestDetail, getFlowLogs } from '#/api/ocpx';
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
  hasAccessByCodes(['ocpx:monitor-task:integration']),
);

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
const filters = reactive({
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string] | null,
  eventId: typeof route.query.eventId === 'string' ? route.query.eventId : '',
  flowType: '',
  resultStatus: '',
  size: 30,
});
const loadGuard = createLatestRequestGuard();

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
      current: filters.current,
      endDate: dates[1],
      eventId: filters.eventId || undefined,
      flowType: filters.flowType || undefined,
      resultStatus: filters.resultStatus || undefined,
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

function reset() {
  Object.assign(filters, {
    dates: [
      dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      dayjs().format('YYYY-MM-DD'),
    ],
    eventId: '',
    flowType: '',
    resultStatus: '',
  });
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

onMounted(load);
</script>

<template>
  <Page auto-content-height title="流程日志">
    <el-card shadow="never">
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
          v-if="canViewRequestDetail"
          fixed="right"
          label="操作"
          min-width="90"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openRequestDetail(row)">
              详情
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
</style>
