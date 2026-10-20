<script lang="ts" setup>
import type {
  CallbackEventCode,
  CallbackRecord,
  EntityOption,
  ReportOption,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';

import {
  getCallbackRecords,
  getUnifiedReportOptions,
  monitorTaskOptions,
  promotionLinkOptions,
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

defineOptions({ name: 'OcpxCallbackRecord' });

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const canViewFlowLog = computed(() => hasAccessByCodes(['ocpx:flow-log:view']));

const eventOptions: Array<{ label: string; value: CallbackEventCode }> = [
  { label: '激活', value: 'ACTIVATE' },
  { label: '下载', value: 'DOWNLOAD' },
  { label: '活动', value: 'ACTIVITY' },
  { label: '深度使用', value: 'ADDICTION' },
  { label: '唤起', value: 'EVOKE' },
  { label: '登录', value: 'LOGIN' },
  { label: '注册', value: 'REGISTER' },
  { label: '付费', value: 'PAYMENT' },
  { label: '获客', value: 'CUSTOMER_ACQUISITION' },
  { label: '次留', value: 'NEXT_STAY' },
  { label: '访问', value: 'VISITS' },
  { label: '认证', value: 'AUTHENTICATION' },
  { label: '下单', value: 'ORDER' },
  { label: '完单', value: 'FINISH_THE_ORDER' },
  { label: '首活', value: 'FIRST_ACTIVATION' },
  { label: '首购', value: 'FIRST_ORDER' },
  { label: '旧协议：唤起 C', value: 'EVOKE_C' },
  { label: '旧协议：首次唤起', value: 'EVOKE_FIRST' },
  { label: '旧协议：有效唤起 C', value: 'EVOKE_C_VALID' },
  { label: '旧协议：拉活', value: 'LAHUO' },
  { label: '旧协议：拉回', value: 'LAHUI' },
  { label: '旧协议：回访', value: 'HUIFANG' },
];
const eventLabels = Object.fromEntries(
  eventOptions.map(({ label, value }) => [value, label]),
) as Record<CallbackEventCode, string>;

const columns = [
  { key: 'receivedAt', label: '上游回传时间' },
  { key: 'event', label: '转化事件' },
  { key: 'requestId', label: '请求 ID' },
  { key: 'target', label: '目标转化' },
  { key: 'advertiser', label: '广告主' },
  { key: 'media', label: '媒体' },
  { key: 'monitor', label: '监测任务' },
  { key: 'link', label: '推广链接' },
  { key: 'billing', label: '广告主计费' },
  { key: 'spend', label: '消费金额' },
  { key: 'deduction', label: '下游处理' },
  { key: 'ratio', label: '放行率快照' },
  { key: 'currency', label: '订单金额' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<CallbackRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const showSearch = ref(true);
const advertiserOptions = ref<ReportOption[]>([]);
const mediaOptions = ref<ReportOption[]>([]);
const monitorOptions = ref<EntityOption[]>([]);
const linkOptions = ref<EntityOption[]>([]);
const filters = reactive({
  advertiserId: '',
  current: 1,
  dates: [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ] as [string, string] | null,
  deductionDecision: '' as
    '' | 'DEDUCTED' | 'PASSED' | 'PENDING' | 'UNAVAILABLE',
  eventCode: '' as '' | CallbackEventCode,
  mediaId: '',
  monitorTaskId: '',
  promotionLinkId: '',
  requestId: '',
  size: 30,
  targetConversion: '' as '' | boolean,
});
const loadGuard = createLatestRequestGuard();
const monitorOptionGuard = createLatestRequestGuard();
const linkOptionGuard = createLatestRequestGuard();

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

function currentDates(): [string, string] {
  if (filters.dates?.[0] && filters.dates[1]) return filters.dates;
  filters.dates = [
    dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ];
  return filters.dates;
}

async function load() {
  const requestSequence = loadGuard.begin();
  const dates = currentDates();
  loading.value = true;
  try {
    const result = await getCallbackRecords({
      advertiserId: filters.advertiserId || undefined,
      current: filters.current,
      deductionDecision: filters.deductionDecision || undefined,
      endDate: dates[1],
      eventCode: filters.eventCode || undefined,
      mediaId: filters.mediaId || undefined,
      monitorTaskId: filters.monitorTaskId || undefined,
      promotionLinkId: filters.promotionLinkId || undefined,
      requestId: filters.requestId.trim() || undefined,
      size: filters.size,
      startDate: dates[0],
      targetConversion:
        filters.targetConversion === '' ? undefined : filters.targetConversion,
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
    advertiserId: '',
    dates: [
      dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
      dayjs().format('YYYY-MM-DD'),
    ],
    deductionDecision: '',
    eventCode: '',
    mediaId: '',
    monitorTaskId: '',
    promotionLinkId: '',
    requestId: '',
    targetConversion: '',
  });
  search();
}

async function loadMonitors() {
  filters.monitorTaskId = '';
  const requestSequence = monitorOptionGuard.begin();
  const result = await monitorTaskOptions(filters.mediaId || undefined);
  if (monitorOptionGuard.isLatest(requestSequence)) {
    monitorOptions.value = result;
  }
}

async function loadLinks() {
  filters.promotionLinkId = '';
  const requestSequence = linkOptionGuard.begin();
  const result = await promotionLinkOptions(filters.advertiserId || undefined);
  if (linkOptionGuard.isLatest(requestSequence)) linkOptions.value = result;
}

function eventLabel(code: CallbackEventCode) {
  return eventLabels[code] || code;
}

function billingLabel(status: CallbackRecord['billingStatus']) {
  if (status === 'POSTED') return '已计费';
  if (status === 'REJECTED_INSUFFICIENT_BALANCE') return '余额不足（已记录）';
  return '不计费';
}

function billingType(status: CallbackRecord['billingStatus']) {
  if (status === 'POSTED') return 'success';
  if (status === 'REJECTED_INSUFFICIENT_BALANCE') return 'danger';
  return 'info';
}

function deductionLabel(decision: CallbackRecord['deductionDecision']) {
  if (decision === 'PASSED') return '下游回传提交';
  if (decision === 'DEDUCTED') return 'SCX 扣量拦截';
  if (decision === 'PENDING') return 'SCX 待判定';
  return '历史无判定数据';
}

function deductionType(decision: CallbackRecord['deductionDecision']) {
  if (decision === 'PASSED') return 'success';
  if (decision === 'DEDUCTED') return 'warning';
  return 'info';
}

function ratio(value?: number) {
  if (value === undefined || value === null) return '-';
  return `${Math.round(Number(value) * 10_000) / 100}%`;
}

function amount(value?: number) {
  return Number(value || 0).toFixed(2);
}

function openFlow(row: CallbackRecord) {
  void router.push({
    path: '/ocpx/flow-log',
    query: { eventId: row.requestId },
  });
}

watch(
  () => filters.mediaId,
  async () => {
    await loadMonitors();
    search();
  },
);
watch(
  () => filters.advertiserId,
  async () => {
    await loadLinks();
    search();
  },
);

onMounted(async () => {
  const options = await getUnifiedReportOptions();
  advertiserOptions.value = options.advertisers;
  mediaOptions.value = options.media;
  await Promise.all([loadMonitors(), loadLinks()]);
  await load();
});
</script>

<template>
  <Page auto-content-height title="上游转化记录">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-date-picker
          v-model="filters.dates"
          aria-label="上游转化日期范围"
          :clearable="false"
          end-placeholder="结束日期"
          range-separator="至"
          start-placeholder="开始日期"
          type="daterange"
          unlink-panels
          value-format="YYYY-MM-DD"
          @change="search"
        />
        <el-select
          v-model="filters.advertiserId"
          aria-label="上游转化广告主"
          clearable
          filterable
          placeholder="广告主"
        >
          <el-option
            v-for="item in advertiserOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.mediaId"
          aria-label="上游转化媒体"
          clearable
          filterable
          placeholder="媒体"
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
          aria-label="上游转化监测任务"
          clearable
          filterable
          placeholder="监测任务"
          @change="search"
        >
          <el-option
            v-for="item in monitorOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.promotionLinkId"
          aria-label="上游转化推广链接"
          clearable
          filterable
          placeholder="推广链接"
          @change="search"
        >
          <el-option
            v-for="item in linkOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="filters.eventCode"
          aria-label="上游转化事件"
          clearable
          filterable
          placeholder="转化事件"
          @change="search"
        >
          <el-option
            v-for="item in eventOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="filters.targetConversion"
          aria-label="是否目标转化"
          clearable
          placeholder="目标转化"
          @change="search"
        >
          <el-option label="是" :value="true" />
          <el-option label="否" :value="false" />
        </el-select>
        <el-select
          v-model="filters.deductionDecision"
          aria-label="下游处理结果"
          clearable
          placeholder="下游处理"
          @change="search"
        >
          <el-option label="下游回传提交" value="PASSED" />
          <el-option label="SCX 扣量拦截" value="DEDUCTED" />
          <el-option label="SCX 待判定" value="PENDING" />
          <el-option label="历史无判定数据" value="UNAVAILABLE" />
        </el-select>
        <el-input
          v-model="filters.requestId"
          aria-label="Callback 请求 ID"
          clearable
          placeholder="请求 ID（精确）"
          @clear="search"
          @keyup.enter="search"
        />
        <el-button :disabled="loading" type="primary" @click="search">
          查询
        </el-button>
        <el-button :disabled="loading" @click="reset">重置</el-button>
      </div>

      <div class="ocpx-actions callback-record-actions">
        <div class="callback-record-legend" aria-label="Callback 状态说明">
          <span><i class="is-upstream"></i>上游 Callback</span>
          <span><i class="is-passed"></i>下游回传提交</span>
          <span><i class="is-deducted"></i>SCX 扣量拦截</span>
        </div>
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
          v-if="hasColumn('receivedAt')"
          label="上游回传时间"
          min-width="180"
          prop="receivedAt"
        />
        <el-table-column
          v-if="hasColumn('event')"
          label="转化事件"
          min-width="160"
        >
          <template #default="{ row }">
            <div class="callback-record-event">
              <strong>{{ eventLabel(row.eventCode) }}</strong>
              <small>{{ row.eventCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('requestId')"
          label="请求 ID"
          min-width="210"
        >
          <template #default="{ row }">
            <LongText :value="row.requestId" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('target')"
          align="center"
          label="目标转化"
          min-width="100"
        >
          <template #default="{ row }">
            <el-tag :type="row.targetConversion ? 'success' : 'info'">
              {{ row.targetConversion ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('advertiser')"
          label="广告主"
          min-width="150"
        >
          <template #default="{ row }">
            {{ row.advertiserName || row.advertiserId }}
          </template>
        </el-table-column>
        <el-table-column v-if="hasColumn('media')" label="媒体" min-width="140">
          <template #default="{ row }">
            {{ row.mediaName || row.mediaId }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('monitor')"
          label="监测任务"
          min-width="150"
        >
          <template #default="{ row }">
            {{ row.monitorTaskName || row.monitorTaskId }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('link')"
          label="推广链接"
          min-width="160"
        >
          <template #default="{ row }">
            {{ row.promotionLinkName || row.promotionLinkId }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('billing')"
          label="广告主计费"
          min-width="170"
        >
          <template #default="{ row }">
            <el-tag :type="billingType(row.billingStatus)">
              {{ billingLabel(row.billingStatus) }}
            </el-tag>
            <small v-if="row.billingReasonCode" class="callback-record-reason">
              {{ row.billingReasonCode }}
            </small>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('spend')"
          align="right"
          label="消费金额"
          min-width="110"
        >
          <template #default="{ row }">¥{{ amount(row.spendAmount) }}</template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('deduction')"
          label="下游处理"
          min-width="150"
        >
          <template #default="{ row }">
            <el-tag :type="deductionType(row.deductionDecision)">
              {{ deductionLabel(row.deductionDecision) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('ratio')"
          align="right"
          label="放行率快照"
          min-width="120"
        >
          <template #default="{ row }">
            {{ ratio(row.deductionRatioSnapshot) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('currency')"
          align="right"
          label="订单金额"
          min-width="110"
        >
          <template #default="{ row }">
            ¥{{ amount(row.currencyAmount) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="canViewFlowLog"
          fixed="right"
          label="操作"
          min-width="120"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openFlow(row)">
              查看完整流程
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
  </Page>
</template>

<style scoped>
.callback-record-actions {
  align-items: flex-start;
}

.callback-record-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  min-height: 32px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.callback-record-legend span {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  font-weight: 600;
}

.callback-record-legend i {
  width: 8px;
  height: 8px;
  background: var(--el-text-color-primary);
  border-radius: 50%;
}

.callback-record-legend i.is-passed {
  background: var(--el-color-success);
}

.callback-record-legend i.is-deducted {
  background: var(--el-color-warning);
}

.callback-record-event {
  display: grid;
  gap: 2px;
}

.callback-record-event strong {
  font-size: 13px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.callback-record-event small,
.callback-record-reason {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
