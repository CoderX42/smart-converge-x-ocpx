<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  ConversionEvent,
  EntityOption,
  MonitorTask,
  MonitorTaskIntegrationCallbackResult,
  MonitorTaskIntegrationFeedback,
  MonitorTaskTestExecution,
  MonitorTaskTestOs,
  MonitorTaskTestPreview,
  MonitorTaskTestResult,
} from '#/api/ocpx';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import CryptoJS from 'crypto-js';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createMonitorTask,
  deleteMonitorTask,
  executeMonitorTaskTestLink,
  generateMonitorTaskTestLink,
  getMonitorTaskIntegrationFeedback,
  getMonitorTaskTestResult,
  listMonitorTasks,
  mediaOptions,
  monitorTaskOptions,
  promotionLinkAdvertisers,
  promotionLinkOptions,
  sendMonitorTaskIntegrationCallback,
  updateMonitorTask,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  copyLongText,
  createLatestRequestGuard,
  PAGE_SIZES,
  platformLabel,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxMonitorTaskManagement' });

const router = useRouter();

const columns = [
  { key: 'id', label: '监测编号' },
  { key: 'media', label: '媒体' },
  { key: 'link', label: '推广链接' },
  { key: 'advertiser', label: '广告主' },
  { key: 'bizId', label: '业务编号' },
  { key: 'platform', label: '平台' },
  { key: 'billingEnabled', label: '扣费状态' },
  { key: 'billingType', label: '扣费形式' },
  { key: 'unitPrice', label: '单价' },
  { key: 'conversionEvent', label: '目标转化事件' },
  { key: 'showUrl', label: '展示监测链接' },
  { key: 'clkUrl', label: '点击监测链接' },
  { key: 'remark', label: '备注' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<MonitorTask[]>([]);
const mediaSelects = ref<EntityOption[]>([]);
const advertiserSelects = ref<EntityOption[]>([]);
const promotionOptions = ref<EntityOption[]>([]);
const monitorSelects = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const testDialogOpen = ref(false);
const testGenerating = ref(false);
const testExecuting = ref(false);
const testPolling = ref(false);
const testTarget = ref<MonitorTask>();
const testOs = ref<MonitorTaskTestOs>('ANDROID');
const testPreview = ref<MonitorTaskTestPreview>();
const testExecution = ref<MonitorTaskTestExecution>();
const testResult = ref<MonitorTaskTestResult>();
const integrationDialogOpen = ref(false);
const integrationLoading = ref(false);
const integrationSending = ref(false);
const integrationTarget = ref<MonitorTask>();
const integrationRows = ref<MonitorTaskIntegrationFeedback[]>([]);
const integrationTotal = ref(0);
const integrationDetailOpen = ref(false);
const integrationDetail = ref<MonitorTaskIntegrationFeedback>();
const integrationCallbackOpen = ref(false);
const integrationCallbackSource = ref<MonitorTaskIntegrationFeedback>();
const integrationCallbackResult = ref<MonitorTaskIntegrationCallbackResult>();
const integrationQuery = reactive({
  current: 1,
  dates: [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')] as [
    string,
    string,
  ],
  requestId: '',
  size: 20,
});
const integrationCallbackForm = reactive({
  currencyAmount: '' as number | string,
  eventCode: 'ACTIVATE' as ConversionEvent,
});
let testPollTimer: ReturnType<typeof setTimeout> | undefined;
let testPollCount = 0;
const total = ref(0);
const showSearch = ref(true);
const query = reactive({
  current: 1,
  mediaId: '',
  monitorTaskId: '',
  size: 30,
});

const dialogOpen = ref(false);
const editing = ref<MonitorTask>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserId: '',
  billingEnabled: false,
  billingType: 1 as 1 | 2,
  bizId: '',
  conversionEvent: 'ACTIVATE' as ConversionEvent,
  mediaId: '',
  monitorName: '',
  promotionLinkId: '',
  remark: '',
  unitPrice: '' as number | string,
});

const conversionEvents: Array<{ label: string; value: ConversionEvent }> = [
  { label: '激活', value: 'ACTIVATE' },
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
];

const conversionEventLabels = Object.fromEntries(
  conversionEvents.map((item) => [item.value, item.label]),
);
const testTimelineStageLabel = {
  BILLING: '计费',
  ENGINE_INGRESS: 'Engine 入站',
  UPSTREAM: '上游投递',
} as const;

function validateNumber(
  _rule: unknown,
  value: number | string,
  callback: (error?: Error) => void,
) {
  if (value === '' || value === undefined || value === null) {
    callback(new Error('输入不能为空'));
    return;
  }
  if (!/^\d+(?:\.\d+)?$/.test(String(value))) {
    callback(new Error('请输入正确的数字格式'));
    return;
  }
  if (form.billingEnabled && Number(value) <= 0) {
    callback(new Error('开启扣费时单价必须大于 0'));
    return;
  }
  callback();
}

const rules: FormRules = {
  advertiserId: [{ message: '请选择广告主', required: true, trigger: 'blur' }],
  conversionEvent: [
    { message: '请选择目标转化事件', required: true, trigger: 'change' },
  ],
  mediaId: [{ message: '请选择渠道', required: true, trigger: 'blur' }],
  monitorName: [{ message: '请输入监测名称', required: true, trigger: 'blur' }],
  promotionLinkId: [
    { message: '请选择投放链接', required: true, trigger: 'blur' },
  ],
  unitPrice: [
    { message: '请输入投放单价', required: true, trigger: 'blur' },
    { trigger: 'blur', validator: validateNumber },
  ],
};
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes(['ocpx:monitor-task:edit', 'ocpx:monitor-task:delete']),
);
const canManageBilling = computed(() =>
  hasAccessByCodes(['ocpx:monitor-task:billing']),
);
const canTest = computed(() => hasAccessByCodes(['ocpx:monitor-task:test']));
const canIntegrate = computed(() =>
  hasAccessByCodes(['ocpx:monitor-task:integration']),
);
const currentTestUrl = computed(() => {
  const preview = testPreview.value;
  if (!preview) return '';
  try {
    const url = new URL(preview.testUrl);
    for (const parameter of preview.parameters) {
      if (parameter.required) continue;
      url.searchParams.delete(parameter.key);
      if (parameter.selected && parameter.value) {
        url.searchParams.set(parameter.key, parameter.value);
      }
    }
    return url.toString();
  } catch {
    return preview.testUrl;
  }
});
const loadGuard = createLatestRequestGuard();
const monitorOptionGuard = createLatestRequestGuard();
const promotionOptionGuard = createLatestRequestGuard();

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listMonitorTasks({
      current: query.current,
      mediaId: query.mediaId || undefined,
      monitorTaskId: query.monitorTaskId || undefined,
      size: query.size,
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

async function loadMonitorSelects(reset = true) {
  if (reset) query.monitorTaskId = '';
  const requestSequence = monitorOptionGuard.begin();
  const result = await monitorTaskOptions(query.mediaId || undefined);
  if (monitorOptionGuard.isLatest(requestSequence)) {
    monitorSelects.value = result;
  }
}

async function loadPromotionOptions(advertiserId: string, keepId = false) {
  if (!keepId) form.promotionLinkId = '';
  const requestSequence = promotionOptionGuard.begin();
  if (!advertiserId) {
    promotionOptions.value = [];
    return;
  }
  const result = await promotionLinkOptions(advertiserId);
  if (!promotionOptionGuard.isLatest(requestSequence)) return;
  promotionOptions.value = result;
  if (
    form.promotionLinkId &&
    !promotionOptions.value.some(({ id }) => id === form.promotionLinkId)
  ) {
    form.promotionLinkId = '';
  }
}

function search() {
  resetPage(query);
  void load();
}

async function edit(row?: MonitorTask) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    billingEnabled: row?.billingEnabled ?? false,
    billingType: row?.billingType ?? 1,
    bizId: row?.bizId || '',
    conversionEvent: row?.conversionEvent || 'ACTIVATE',
    mediaId: row?.mediaId || '',
    monitorName: row?.monitorName || '',
    promotionLinkId: row?.promotionLinkId || '',
    remark: row?.remark || '',
    unitPrice: row?.unitPrice ?? '',
  });
  await loadPromotionOptions(form.advertiserId, true);
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  const billingModeChanged =
    (form.billingEnabled && !editing.value?.billingEnabled) ||
    (Boolean(editing.value) && editing.value?.billingType !== form.billingType);
  if (billingModeChanged) {
    await ElMessageBox.confirm(
      form.billingEnabled
        ? `保存后将按${form.billingType === 1 ? '点击' : '目标转化'}扣费，确认继续？`
        : `已切换为按${form.billingType === 1 ? '点击' : '目标转化'}扣费；当前仍处于关闭状态，重新开启后生效。确认继续？`,
      '确认计费配置',
      {
        confirmButtonText: '确认保存',
        type: 'warning',
      },
    );
  }
  saving.value = true;
  try {
    const editingId = editing.value?.id;
    const payload = {
      advertiserId: form.advertiserId,
      billingEnabled: form.billingEnabled,
      billingType: form.billingType,
      bizId: form.bizId || undefined,
      conversionEvent: form.conversionEvent,
      mediaId: form.mediaId,
      monitorName: form.monitorName,
      promotionLinkId: form.promotionLinkId,
      remark: form.remark || undefined,
      unitPrice: Number(form.unitPrice),
    };
    await (editingId
      ? updateMonitorTask(editingId, payload)
      : createMonitorTask(payload));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    await loadMonitorSelects(false);
    await load();
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  promotionOptions.value = [];
  Object.assign(form, {
    advertiserId: '',
    billingEnabled: false,
    billingType: 1,
    bizId: '',
    conversionEvent: 'ACTIVATE',
    mediaId: '',
    monitorName: '',
    promotionLinkId: '',
    remark: '',
    unitPrice: '',
  });
}

async function remove(row: MonitorTask) {
  await ElMessageBox.confirm(
    '删除后该监测任务不再可用，确认继续？',
    '删除监测任务',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deleteMonitorTask(row.id);
    ElMessage.success('删除成功');
    await load();
  } finally {
    deletingId.value = '';
  }
}

function clearTestPolling() {
  if (testPollTimer) clearTimeout(testPollTimer);
  testPollTimer = undefined;
  testPolling.value = false;
}

function selectedTestParameters() {
  return (
    testPreview.value?.parameters
      .filter((parameter) => !parameter.required && parameter.selected)
      .map((parameter) => parameter.key) || []
  );
}

function selectedTestValues() {
  return Object.fromEntries(
    (testPreview.value?.parameters || [])
      .filter(
        (parameter) =>
          !parameter.required && parameter.selected && parameter.value,
      )
      .map((parameter) => [parameter.key, parameter.value as string]),
  );
}

function syncRelatedMd5(
  parameter: MonitorTaskTestPreview['parameters'][number],
) {
  const digestKeys: Record<string, string> = {
    androidid: 'androidid_md5',
    idfa: 'idfa_md5',
    imei: 'imei_md5',
    oaid: 'oaid_md5',
  };
  const digestKey = digestKeys[parameter.key];
  if (!(digestKey && parameter.value)) return;
  const digest = testPreview.value?.parameters.find(
    (candidate) => candidate.key === digestKey,
  );
  if (digest) digest.value = CryptoJS.MD5(parameter.value).toString();
}

async function generateTest(parameters?: string[]) {
  if (!testTarget.value) return;
  testGenerating.value = true;
  try {
    testPreview.value = await generateMonitorTaskTestLink(testTarget.value.id, {
      os: testOs.value,
      ...(parameters === undefined ? {} : { parameters }),
    });
    testExecution.value = undefined;
    testResult.value = undefined;
    clearTestPolling();
  } finally {
    testGenerating.value = false;
  }
}

async function openTest(row: MonitorTask) {
  testTarget.value = row;
  testOs.value = row.platform === 2 ? 'IOS' : 'ANDROID';
  testPreview.value = undefined;
  testExecution.value = undefined;
  testResult.value = undefined;
  testDialogOpen.value = true;
  await generateTest();
}

async function handleTestOsChange() {
  await generateTest();
}

async function regenerateSelected() {
  await generateTest(selectedTestParameters());
}

async function refreshTestResult(schedule = false) {
  if (!(testTarget.value && testPreview.value)) return;
  try {
    testResult.value = await getMonitorTaskTestResult(
      testTarget.value.id,
      testPreview.value.requestId,
    );
  } catch {
    if (!schedule) ElMessage.error('测试详情查询失败，请稍后重试');
  } finally {
    if (testDialogOpen.value && testTarget.value && testPreview.value) {
      const terminal = [
        'REJECTED',
        'UPSTREAM_FAILED',
        'UPSTREAM_RESPONDED',
      ].includes(testResult.value?.state || '');
      if (schedule && !terminal && testPollCount < 30) {
        testPollCount += 1;
        testPolling.value = true;
        testPollTimer = setTimeout(() => void refreshTestResult(true), 1000);
      } else {
        clearTestPolling();
        if (schedule && !terminal && testPollCount >= 30) {
          ElMessage.warning('自动查询已结束，可继续手动刷新测试详情');
        }
      }
    } else {
      clearTestPolling();
    }
  }
}

async function executeTest() {
  if (!(testTarget.value && testPreview.value)) return;
  const charge = Number(testPreview.value.expectedCharge || 0);
  await ElMessageBox.confirm(
    `该操作会产生真实点击和上游流量${charge > 0 ? `，预计真实扣费 ${charge}` : ''}，确认发送？`,
    '确认执行点击测试',
    {
      confirmButtonText: '确认发送',
      type: 'warning',
    },
  );
  testExecuting.value = true;
  clearTestPolling();
  try {
    testExecution.value = await executeMonitorTaskTestLink(
      testTarget.value.id,
      {
        os: testOs.value,
        parameters: selectedTestValues(),
        requestId: testPreview.value.requestId,
      },
    );
    testPreview.value.testUrl = testExecution.value.testUrl;
    ElMessage.success(
      testExecution.value.alreadyExecuted
        ? '该测试 ID 已执行，未重复发送'
        : '测试请求已提交',
    );
    testPollCount = 0;
    await refreshTestResult(true);
  } finally {
    testExecuting.value = false;
  }
}

function closeTestDialog() {
  clearTestPolling();
  testTarget.value = undefined;
  testPreview.value = undefined;
  testExecution.value = undefined;
  testResult.value = undefined;
}

async function openTestFlowLog() {
  const requestId = testPreview.value?.requestId;
  if (!requestId) return;
  testDialogOpen.value = false;
  await router.push({ path: '/ocpx/flow-log', query: { eventId: requestId } });
}

async function loadIntegration() {
  if (!integrationTarget.value) return;
  integrationLoading.value = true;
  try {
    const result = await getMonitorTaskIntegrationFeedback(
      integrationTarget.value.id,
      {
        current: integrationQuery.current,
        endDate: integrationQuery.dates[1],
        requestId: integrationQuery.requestId.trim() || undefined,
        size: integrationQuery.size,
        startDate: integrationQuery.dates[0],
      },
    );
    integrationRows.value = result.records;
    integrationTotal.value = result.total;
  } finally {
    integrationLoading.value = false;
  }
}

async function openIntegration(row: MonitorTask) {
  integrationTarget.value = row;
  integrationQuery.current = 1;
  integrationQuery.requestId = '';
  integrationQuery.dates = [
    dayjs().format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD'),
  ];
  integrationRows.value = [];
  integrationTotal.value = 0;
  integrationDialogOpen.value = true;
  await loadIntegration();
}

function searchIntegration() {
  integrationQuery.current = 1;
  void loadIntegration();
}

function showIntegrationDetail(row: MonitorTaskIntegrationFeedback) {
  integrationDetail.value = row;
  integrationDetailOpen.value = true;
}

function openIntegrationCallback(row: MonitorTaskIntegrationFeedback) {
  integrationCallbackSource.value = row;
  integrationCallbackForm.eventCode =
    integrationTarget.value?.conversionEvent || 'ACTIVATE';
  integrationCallbackForm.currencyAmount = '';
  integrationCallbackResult.value = undefined;
  integrationCallbackOpen.value = true;
}

async function sendIntegrationCallback() {
  if (!(integrationTarget.value && integrationCallbackSource.value)) return;
  const amount = integrationCallbackForm.currencyAmount;
  if (
    amount !== '' &&
    (!/^\d+(?:\.\d{1,6})?$/.test(String(amount)) || Number(amount) < 0)
  ) {
    ElMessage.warning('金额必须是非负数字，最多 6 位小数');
    return;
  }
  await ElMessageBox.confirm(
    '该操作会向下游媒体 callback_url 发送一次真实 Callback。不会生成广告主转化、不会扣费，也不会进入正式扣量报表。确认发送？',
    '确认发送媒体 Callback',
    {
      confirmButtonText: '确认真实发送',
      type: 'warning',
    },
  );
  integrationSending.value = true;
  try {
    integrationCallbackResult.value = await sendMonitorTaskIntegrationCallback(
      integrationTarget.value.id,
      {
        ...(amount === '' ? {} : { currencyAmount: Number(amount) }),
        eventCode: integrationCallbackForm.eventCode,
        feedbackLogId: integrationCallbackSource.value.feedbackLogId,
      },
    );
    ElMessage.success(
      integrationCallbackResult.value.resultStatus === 'SUCCESS'
        ? '媒体接口已响应'
        : 'Callback 已执行，请查看失败详情',
    );
  } finally {
    integrationSending.value = false;
  }
}

async function openIntegrationFlowLog() {
  const attemptId = integrationCallbackResult.value?.attemptId;
  if (!attemptId) return;
  integrationCallbackOpen.value = false;
  integrationDialogOpen.value = false;
  await router.push({ path: '/ocpx/flow-log', query: { eventId: attemptId } });
}

function closeIntegrationDialog() {
  integrationTarget.value = undefined;
  integrationRows.value = [];
  integrationDetail.value = undefined;
  integrationCallbackSource.value = undefined;
  integrationCallbackResult.value = undefined;
}

function maskIntegrationValue(value?: string) {
  if (!value) return '-';
  if (value.length <= 8) return value;
  return `${value.slice(0, 4)}…${value.slice(-4)}`;
}

onMounted(async () => {
  const [media, advertisers, monitors] = await Promise.all([
    mediaOptions(),
    promotionLinkAdvertisers(),
    monitorTaskOptions(),
  ]);
  mediaSelects.value = media;
  advertiserSelects.value = advertisers;
  monitorSelects.value = monitors;
  await load();
});

onBeforeUnmount(clearTestPolling);
</script>

<template>
  <Page auto-content-height title="监测任务">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.mediaId"
          aria-label="按媒体查询监测任务"
          clearable
          filterable
          placeholder="请选择渠道"
          @change="loadMonitorSelects()"
        >
          <el-option
            v-for="item in mediaSelects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="query.monitorTaskId"
          aria-label="按监测任务查询"
          clearable
          filterable
          placeholder="请选择监测任务"
        >
          <el-option
            v-for="item in monitorSelects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-button :disabled="loading" type="primary" @click="search"
          >搜索</el-button
        >
      </div>
      <div class="ocpx-actions">
        <el-button
          v-if="canManageBilling"
          v-access:code="'ocpx:monitor-task:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
          >新增任务</el-button
        >
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
        <el-table-column v-if="hasColumn('id')" label="监测编号" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('media')"
          label="媒体"
          min-width="140"
          prop="mediaName"
        />
        <el-table-column
          v-if="hasColumn('link')"
          label="推广链接"
          min-width="150"
          prop="promotionLinkName"
        />
        <el-table-column
          v-if="hasColumn('advertiser')"
          label="广告主"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('bizId')"
          label="业务编号"
          min-width="150"
          ><template #default="{ row }"
            ><LongText :value="row.bizId" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('platform')"
          label="平台"
          min-width="120"
          ><template #default="{ row }">{{
            platformLabel[row.platform]
          }}</template></el-table-column
        >
        <el-table-column
          v-if="hasColumn('billingEnabled')"
          label="扣费状态"
          min-width="100"
        >
          <template #default="{ row }">
            <el-tag :type="row.billingEnabled ? 'success' : 'info'">
              {{ row.billingEnabled ? '已开启' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('billingType')"
          label="扣费形式"
          min-width="100"
        >
          <template #default="{ row }">
            {{ row.billingType === 2 ? '目标转化' : '点击' }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('unitPrice')"
          label="单价"
          min-width="100"
          prop="unitPrice"
        />
        <el-table-column
          v-if="hasColumn('conversionEvent')"
          label="目标转化事件"
          min-width="130"
        >
          <template #default="{ row }">
            {{
              conversionEventLabels[row.conversionEvent] || row.conversionEvent
            }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('showUrl')"
          label="展示监测链接"
          min-width="240"
          ><template #default="{ row }"
            ><LongText :value="row.showUrl" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('clkUrl')"
          label="点击监测链接"
          min-width="240"
          ><template #default="{ row }"
            ><LongText :value="row.clkUrl" /></template
        ></el-table-column>
        <el-table-column v-if="hasColumn('remark')" label="备注" min-width="160"
          ><template #default="{ row }"
            ><LongText :value="row.remark" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('createTime')"
          label="创建时间"
          min-width="170"
          prop="createTime"
        />
        <el-table-column
          v-if="hasColumn('updateTime')"
          label="修改时间"
          min-width="170"
          prop="updateTime"
        />
        <el-table-column
          v-if="canOperate || canTest || canIntegrate"
          fixed="right"
          label="操作"
          width="250"
          ><template #default="{ row }"
            ><el-button
              v-if="canTest"
              v-access:code="'ocpx:monitor-task:test'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="success"
              @click="openTest(row)"
              >测试链接</el-button
            ><el-button
              v-if="canIntegrate"
              v-access:code="'ocpx:monitor-task:integration'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="warning"
              @click="openIntegration(row)"
              >联调</el-button
            ><el-button
              v-access:code="'ocpx:monitor-task:edit'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            ><el-button
              v-access:code="'ocpx:monitor-task:delete'"
              :disabled="saving || Boolean(deletingId)"
              :loading="deletingId === row.id"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="testDialogOpen"
      :close-on-click-modal="false"
      title="点击测试链接与联调详情"
      width="960px"
      @close="closeTestDialog"
    >
      <div v-loading="testGenerating" class="ocpx-test-dialog">
        <el-alert
          :closable="false"
          show-icon
          title="本功能会向真实 Engine 发送 CLK 点击；不携带 callback_url，不代表完整回调闭环。"
          type="warning"
        />

        <el-descriptions
          v-if="testPreview"
          :column="3"
          border
          class="ocpx-test-section"
          size="small"
        >
          <el-descriptions-item label="监测任务">
            {{ testPreview.monitorName }}
          </el-descriptions-item>
          <el-descriptions-item label="广告主">
            {{ testPreview.advertiserName }}
          </el-descriptions-item>
          <el-descriptions-item label="媒体">
            {{ testPreview.mediaName }}
          </el-descriptions-item>
          <el-descriptions-item label="推广链接">
            {{ testPreview.promotionLinkName }}
          </el-descriptions-item>
          <el-descriptions-item label="平台">
            {{ platformLabel[testPreview.platform] }}
          </el-descriptions-item>
          <el-descriptions-item label="测试 ID">
            <LongText :value="testPreview.requestId" />
          </el-descriptions-item>
          <el-descriptions-item label="预计扣费">
            {{ testPreview.expectedCharge || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="当前余额">
            {{ testPreview.availableBalance ?? '未建立账户' }}
          </el-descriptions-item>
          <el-descriptions-item label="操作系统">
            <el-radio-group
              v-model="testOs"
              :disabled="testTarget?.platform !== 3 || testExecuting"
              size="small"
              @change="handleTestOsChange"
            >
              <el-radio-button value="ANDROID">Android</el-radio-button>
              <el-radio-button value="IOS">iOS</el-radio-button>
            </el-radio-group>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="testPreview">
          <div class="ocpx-test-section-title">
            <span>测试参数</span>
            <el-button
              :disabled="testExecuting"
              :loading="testGenerating"
              link
              type="primary"
              @click="regenerateSelected"
              >重新随机</el-button
            >
          </div>
          <el-table
            :data="testPreview.parameters"
            border
            max-height="330"
            size="small"
          >
            <el-table-column label="启用" width="70">
              <template #default="{ row }">
                <el-checkbox
                  v-model="row.selected"
                  :disabled="row.required || testExecuting"
                  @change="regenerateSelected"
                />
              </template>
            </el-table-column>
            <el-table-column label="分组" prop="group" width="95" />
            <el-table-column label="参数" min-width="130">
              <template #default="{ row }">
                {{ row.label }}
                <div class="ocpx-test-macro">{{ row.key }}</div>
              </template>
            </el-table-column>
            <el-table-column label="配置检查" min-width="180">
              <template #default="{ row }">
                <el-tag
                  :type="row.configuredByMedia ? 'success' : 'warning'"
                  size="small"
                >
                  {{ row.configuredByMedia ? '媒体已配置' : '测试临时补充' }}
                </el-tag>
                <el-tag
                  v-if="!row.required"
                  :type="row.forwardedUpstream ? 'success' : 'danger'"
                  class="ocpx-test-tag"
                  size="small"
                >
                  {{ row.forwardedUpstream ? '上游可转发' : '上游不转发' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="测试值" min-width="330">
              <template #default="{ row }">
                <el-input
                  v-if="row.selected"
                  v-model="row.value"
                  :disabled="!row.editable || testExecuting"
                  size="small"
                  @input="syncRelatedMd5(row)"
                />
                <span v-else class="ocpx-test-muted">未发送</span>
              </template>
            </el-table-column>
          </el-table>

          <el-alert
            v-for="message in testPreview.blockers"
            :key="`blocker-${message}`"
            :closable="false"
            class="ocpx-test-alert"
            show-icon
            :title="message"
            type="error"
          />
          <el-alert
            v-for="message in testPreview.warnings"
            :key="`warning-${message}`"
            :closable="false"
            class="ocpx-test-alert"
            show-icon
            :title="message"
            type="warning"
          />

          <div class="ocpx-test-section-title">完整测试链接</div>
          <el-input
            :model-value="currentTestUrl"
            :rows="4"
            readonly
            type="textarea"
          />
          <div class="ocpx-test-link-actions">
            <el-button @click="copyLongText(currentTestUrl)"
              >复制链接</el-button
            >
            <el-button
              :disabled="!testPreview.executable || testExecuting"
              :loading="testExecuting"
              type="danger"
              @click="executeTest"
              >确认发送真实点击</el-button
            >
          </div>
        </template>

        <el-card
          v-if="testExecution || testResult"
          class="ocpx-test-section"
          shadow="never"
        >
          <template #header>
            <div class="ocpx-test-result-header">
              <span>测试详情</span>
              <div>
                <el-button
                  :loading="testPolling"
                  link
                  type="primary"
                  @click="refreshTestResult(false)"
                  >刷新</el-button
                >
                <el-button link type="primary" @click="openTestFlowLog">
                  查看流程日志
                </el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="Engine HTTP">
              {{ testExecution?.engineHttpStatus ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Engine 耗时">
              {{ testExecution ? `${testExecution.durationMs} ms` : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="当前状态">
              {{ testResult?.state || '查询中' }}
            </el-descriptions-item>
            <el-descriptions-item label="入站事实">
              {{ testResult?.fact ? '已写入' : '等待中' }}
            </el-descriptions-item>
            <el-descriptions-item label="计费状态">
              {{ testResult?.fact?.billingStatus || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="实际消费">
              {{ testResult?.fact?.spendAmount ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="上游 HTTP">
              {{ testResult?.flow?.partnerHttpStatus ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="上游结果">
              {{ testResult?.flow?.resultStatus || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="上游耗时">
              {{ testResult?.flow ? `${testResult.flow.durationMs} ms` : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="稳定结论码" :span="3">
              {{ testResult?.flow?.message || testExecution?.errorCode || '-' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="testResult"
            :closable="false"
            class="ocpx-test-alert"
            show-icon
            :title="testResult.conclusion"
            :type="
              testResult.state === 'UPSTREAM_RESPONDED'
                ? 'warning'
                : testResult.state === 'UPSTREAM_FAILED' ||
                    testResult.state === 'REJECTED'
                  ? 'error'
                  : 'info'
            "
          />
          <el-timeline v-if="testResult" class="ocpx-test-timeline">
            <el-timeline-item
              v-for="item in testResult.timeline"
              :key="`${item.stage}-${item.status}`"
              :timestamp="item.occurredAt || '等待中'"
              :type="
                item.status === 'SUCCESS' || item.status === 'POSTED'
                  ? 'success'
                  : item.status.startsWith('REJECTED') ||
                      item.status === 'FAILED'
                    ? 'danger'
                    : 'primary'
              "
            >
              {{ testTimelineStageLabel[item.stage] }} · {{ item.status }}
              <span v-if="item.durationMs !== undefined">
                · {{ item.durationMs }} ms
              </span>
              <div v-if="item.detail" class="ocpx-test-result-tip">
                {{ item.detail }}
              </div>
            </el-timeline-item>
          </el-timeline>
          <div class="ocpx-test-result-tip">
            上游 HTTP 200 只表示接口已响应，不代表合作方已经入库。
          </div>
          <el-input
            v-if="testExecution?.engineResponse"
            :model-value="testExecution.engineResponse"
            :rows="2"
            class="ocpx-test-alert"
            readonly
            type="textarea"
          />
        </el-card>
      </div>
    </el-dialog>

    <el-dialog
      v-model="integrationDialogOpen"
      :close-on-click-modal="false"
      title="下游媒体联调"
      width="1080px"
      @close="closeIntegrationDialog"
    >
      <div class="ocpx-integration-dialog">
        <el-alert
          :closable="false"
          show-icon
          title="这里展示 Engine 异步落库的下游 Feedback 规范化快照，不是网络层原始报文；通常约 1 分钟可见，重复请求不会新增明细。"
          type="info"
        />

        <el-descriptions
          v-if="integrationTarget"
          :column="4"
          border
          class="ocpx-test-section"
          size="small"
        >
          <el-descriptions-item label="监测任务">
            {{ integrationTarget.monitorName }}
          </el-descriptions-item>
          <el-descriptions-item label="广告主">
            {{ integrationTarget.advertiserName }}
          </el-descriptions-item>
          <el-descriptions-item label="媒体">
            {{ integrationTarget.mediaName }}
          </el-descriptions-item>
          <el-descriptions-item label="推广链接">
            {{ integrationTarget.promotionLinkName }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="ocpx-integration-query">
          <el-date-picker
            v-model="integrationQuery.dates"
            :clearable="false"
            end-placeholder="结束日期"
            range-separator="至"
            start-placeholder="开始日期"
            type="daterange"
            unlink-panels
            value-format="YYYY-MM-DD"
          />
          <el-input
            v-model="integrationQuery.requestId"
            clearable
            placeholder="精确查询 request_id"
            @keyup.enter="searchIntegration"
          />
          <el-button
            :disabled="integrationLoading"
            type="primary"
            @click="searchIntegration"
            >查询</el-button
          >
          <el-button :loading="integrationLoading" @click="loadIntegration"
            >刷新</el-button
          >
        </div>

        <el-table
          v-loading="integrationLoading"
          :data="integrationRows"
          border
          class="ocpx-test-section"
          max-height="430"
          size="small"
        >
          <el-table-column label="接收时间" min-width="165" prop="receivedAt" />
          <el-table-column label="request_id" min-width="190">
            <template #default="{ row }">
              <LongText :value="row.requestId" />
            </template>
          </el-table-column>
          <el-table-column label="clickid" min-width="150">
            <template #default="{ row }">
              {{ maskIntegrationValue(row.clickId) }}
            </template>
          </el-table-column>
          <el-table-column label="系统 / 标识" min-width="145">
            <template #default="{ row }">
              <div>{{ row.deviceOs || '-' }}</div>
              <div class="ocpx-test-muted">
                {{ row.userType || 'UNKNOWN' }} ·
                {{ maskIntegrationValue(row.userId) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Callback" width="110">
            <template #default="{ row }">
              <el-tag
                :type="row.callbackAvailable ? 'success' : 'info'"
                size="small"
              >
                {{ row.callbackAvailable ? '可回传' : '未携带' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="145">
            <template #default="{ row }">
              <el-button link type="primary" @click="showIntegrationDetail(row)"
                >详情</el-button
              >
              <el-button
                :disabled="!row.callbackAvailable"
                link
                type="danger"
                @click="openIntegrationCallback(row)"
                >发 Callback</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!integrationLoading && integrationRows.length === 0"
          description="当前条件下暂无已落库的下游请求"
        />
        <el-pagination
          v-model:current-page="integrationQuery.current"
          v-model:page-size="integrationQuery.size"
          class="ocpx-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="integrationTotal"
          @change="loadIntegration"
        />
      </div>
    </el-dialog>

    <el-dialog
      v-model="integrationDetailOpen"
      append-to-body
      title="下游请求明细（落库快照）"
      width="820px"
    >
      <el-alert
        :closable="false"
        class="mb-3"
        title="字段来自 Feedback 解析后的异步存储，不保证保留原始 URL 的参数顺序、编码形式或重复参数。"
        type="warning"
      />
      <el-descriptions v-if="integrationDetail" :column="2" border size="small">
        <el-descriptions-item label="接收时间">
          {{ integrationDetail.receivedAt }}
        </el-descriptions-item>
        <el-descriptions-item label="明细 ID">
          {{ integrationDetail.feedbackLogId }}
        </el-descriptions-item>
        <el-descriptions-item label="request_id">
          <LongText :value="integrationDetail.requestId" />
        </el-descriptions-item>
        <el-descriptions-item label="clickid">
          <LongText :value="integrationDetail.clickId" />
        </el-descriptions-item>
        <el-descriptions-item label="OS / 标识类型">
          {{ integrationDetail.deviceOs || '-' }} /
          {{ integrationDetail.userType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="主标识">
          <LongText :value="integrationDetail.userId" />
        </el-descriptions-item>
        <el-descriptions-item label="IMEI">
          <LongText :value="integrationDetail.imei" />
        </el-descriptions-item>
        <el-descriptions-item label="OAID">
          <LongText :value="integrationDetail.oaid" />
        </el-descriptions-item>
        <el-descriptions-item label="Android ID">
          <LongText :value="integrationDetail.androidId" />
        </el-descriptions-item>
        <el-descriptions-item label="IDFA">
          <LongText :value="integrationDetail.idfa" />
        </el-descriptions-item>
        <el-descriptions-item label="CAID / CAID1">
          <LongText
            :value="`${integrationDetail.caid || '-'} / ${integrationDetail.caid1 || '-'}`"
          />
        </el-descriptions-item>
        <el-descriptions-item label="MAC">
          <LongText :value="integrationDetail.mac" />
        </el-descriptions-item>
        <el-descriptions-item label="IP">
          <LongText :value="integrationDetail.ip" />
        </el-descriptions-item>
        <el-descriptions-item label="时间戳">
          {{ integrationDetail.timestamp || '-' }} /
          {{ integrationDetail.timestampMs || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="User-Agent" :span="2">
          <LongText :value="integrationDetail.ua" />
        </el-descriptions-item>
        <el-descriptions-item label="callback_url" :span="2">
          <LongText :value="integrationDetail.callbackUrl" />
        </el-descriptions-item>
        <el-descriptions-item label="外部广告维度" :span="2">
          adv={{ integrationDetail.externalAdvertiserId || '-' }}；plan={{
            integrationDetail.planId || '-'
          }}；group={{ integrationDetail.groupId || '-' }}；creative={{
            integrationDetail.creativeId || '-'
          }}
        </el-descriptions-item>
        <el-descriptions-item label="SCX 路由关系" :span="2">
          mId={{ integrationDetail.mediaId || '-' }}；tId={{
            integrationDetail.monitorTaskId || '-'
          }}；aId={{ integrationDetail.advertiserId || '-' }}；lId={{
            integrationDetail.promotionLinkId || '-'
          }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog
      v-model="integrationCallbackOpen"
      append-to-body
      :close-on-click-modal="false"
      title="发送媒体 Callback"
      width="700px"
    >
      <el-alert
        :closable="false"
        show-icon
        title="这是一次真实外部请求，但不会生成广告主转化事实、不会扣费，也不会进入正式扣量和报表。"
        type="error"
      />
      <el-descriptions
        v-if="integrationCallbackSource"
        :column="2"
        border
        class="ocpx-test-section"
        size="small"
      >
        <el-descriptions-item label="request_id">
          <LongText :value="integrationCallbackSource.requestId" />
        </el-descriptions-item>
        <el-descriptions-item label="clickid">
          <LongText :value="integrationCallbackSource.clickId" />
        </el-descriptions-item>
        <el-descriptions-item label="目标地址" :span="2">
          <LongText :value="integrationCallbackSource.callbackUrl" />
        </el-descriptions-item>
      </el-descriptions>
      <el-form class="ocpx-test-section" label-width="100px">
        <el-form-item label="转化事件">
          <el-select v-model="integrationCallbackForm.eventCode">
            <el-option
              v-for="item in conversionEvents"
              :key="item.value"
              :label="`${item.label}（${item.value}）`"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转化金额">
          <el-input
            v-model="integrationCallbackForm.currencyAmount"
            clearable
            placeholder="可选，最多 6 位小数"
          />
        </el-form-item>
      </el-form>

      <el-card
        v-if="integrationCallbackResult"
        class="ocpx-test-section"
        shadow="never"
      >
        <template #header>
          <div class="ocpx-test-result-header">
            <span>发送结果</span>
            <el-button link type="primary" @click="openIntegrationFlowLog"
              >查看流程日志</el-button
            >
          </div>
        </template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="结果">
            <el-tag
              :type="
                integrationCallbackResult.resultStatus === 'SUCCESS'
                  ? 'success'
                  : 'danger'
              "
            >
              {{ integrationCallbackResult.resultStatus }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="HTTP">
            {{ integrationCallbackResult.partnerHttpStatus ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ integrationCallbackResult.durationMs }} ms
          </el-descriptions-item>
          <el-descriptions-item label="联调 ID" :span="3">
            <LongText :value="integrationCallbackResult.attemptId" />
          </el-descriptions-item>
          <el-descriptions-item label="稳定结论码" :span="3">
            {{ integrationCallbackResult.message }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          :closable="false"
          class="ocpx-test-alert"
          title="HTTP 成功只表示媒体接口已响应，不代表媒体已经完成业务入库。"
          type="warning"
        />
        <el-input
          v-if="integrationCallbackResult.responsePreview"
          :model-value="integrationCallbackResult.responsePreview"
          :rows="3"
          class="ocpx-test-alert"
          readonly
          type="textarea"
        />
      </el-card>
      <template #footer>
        <el-button
          :disabled="integrationSending"
          @click="integrationCallbackOpen = false"
          >关闭</el-button
        >
        <el-button
          :loading="integrationSending"
          type="danger"
          @click="sendIntegrationCallback"
          >确认发送真实 Callback</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="dialogOpen"
      :close-on-click-modal="false"
      :title="editing ? '编辑监测任务' : '新增监测任务'"
      width="680px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="所属渠道" prop="mediaId"
          ><el-select v-model="form.mediaId" filterable placeholder="请选择渠道"
            ><el-option
              v-for="item in mediaSelects"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="所属广告主" prop="advertiserId"
          ><el-select
            v-model="form.advertiserId"
            filterable
            placeholder="请选择广告主"
            @change="loadPromotionOptions(form.advertiserId)"
            ><el-option
              v-for="item in advertiserSelects"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="投放链接" prop="promotionLinkId"
          ><el-select
            v-model="form.promotionLinkId"
            filterable
            placeholder="请选择投放链接"
            ><el-option
              v-for="item in promotionOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="监测名称" prop="monitorName"
          ><el-input
            v-model="form.monitorName"
            autosize
            placeholder="请输入监测名称"
            type="textarea"
        /></el-form-item>
        <el-form-item label="业务编号" prop="bizId"
          ><el-input
            v-model="form.bizId"
            autosize
            placeholder="请输入业务编号"
            type="textarea"
        /></el-form-item>
        <el-form-item label="开启扣费" prop="billingEnabled">
          <el-switch
            v-model="form.billingEnabled"
            :disabled="!canManageBilling"
            active-text="开启"
            inactive-text="关闭"
          />
        </el-form-item>
        <el-form-item label="扣费形式" prop="billingType">
          <el-radio-group
            v-model="form.billingType"
            :disabled="!canManageBilling"
          >
            <el-radio-button :value="1">按点击</el-radio-button>
            <el-radio-button :value="2">按目标转化</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="投放单价" prop="unitPrice"
          ><el-input
            v-model="form.unitPrice"
            :disabled="!canManageBilling"
            placeholder="请输入投放单价"
        /></el-form-item>
        <el-alert
          class="mb-4"
          :closable="false"
          title="投放单价用于广告主账户计费，与媒体回传扣量相互独立。"
          type="info"
        />
        <el-form-item label="目标转化" prop="conversionEvent">
          <el-select
            v-model="form.conversionEvent"
            :disabled="!canManageBilling"
            placeholder="请选择目标转化事件"
          >
            <el-option
              v-for="item in conversionEvents"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark"
          ><el-input
            v-model="form.remark"
            autosize
            placeholder="请输入备注"
            type="textarea"
        /></el-form-item>
        <el-alert
          class="mb-3"
          :closable="false"
          :title="
            canManageBilling
              ? '扣费关闭时仍保留当前单价和目标事件；重新开启后继续使用。'
              : '当前账号没有计费修改权限，计费字段仅可查看。'
          "
          type="warning"
        />
        <el-alert
          :closable="false"
          title="展示和点击监测 URL 由后端生成，无需手工填写。"
          type="info"
        />
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        ><el-button :loading="saving" type="primary" @click="save"
          >确定</el-button
        ></template
      >
    </el-dialog>
  </Page>
</template>

<style scoped>
.ocpx-test-dialog {
  min-height: 180px;
}

.ocpx-test-section {
  margin-top: 16px;
}

.ocpx-test-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 0 10px;
  font-weight: 600;
}

.ocpx-test-macro,
.ocpx-test-muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ocpx-test-tag {
  margin-left: 6px;
}

.ocpx-test-alert {
  margin-top: 10px;
}

.ocpx-test-link-actions,
.ocpx-test-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.ocpx-test-result-header {
  margin-top: 0;
}

.ocpx-test-result-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ocpx-test-timeline {
  margin-top: 18px;
}

.ocpx-integration-dialog {
  min-height: 260px;
}

.ocpx-integration-query {
  display: grid;
  grid-template-columns: minmax(300px, 1.2fr) minmax(220px, 1fr) auto auto;
  gap: 10px;
  margin-top: 16px;
}

@media (width <= 900px) {
  .ocpx-integration-query {
    grid-template-columns: 1fr;
  }
}
</style>
