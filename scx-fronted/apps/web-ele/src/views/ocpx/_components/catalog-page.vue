<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  AccountEntry,
  AdapterDefinition,
  Advertiser,
  AppLink,
  CatalogKind,
  Channel,
  EventOption,
  MonitorEventBinding,
  OcpxIntegration,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { CopyDocument } from '@element-plus/icons-vue';
import { useClipboard } from '@vueuse/core';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteCatalog,
  getAdapters,
  getAdvertiserLedger,
  getCatalog,
  getChannelEventOptions,
  getMonitor,
  getMonitorIntegration,
  publishMonitor,
  rechargeAdvertiser,
  saveCatalog,
} from '#/api/ocpx';
import { createClientRequestId } from '#/utils/request-id';

import AutoCodeInput from './auto-code-input.vue';
import {
  appendChannelMacros,
  CHANNEL_MACRO_OPTIONS,
  DEFAULT_CHANNEL_MACRO_PARAMS,
} from './channel-macros';

defineOptions({ name: 'OcpxCatalogPage' });

const props = defineProps<{ kind: CatalogKind }>();

type Row = Record<string, any> & { id: string };
type Column = {
  label: string;
  minWidth?: number;
  prop: string;
  width?: number;
};

const current = ref(1);
const size = ref(20);
const total = ref(0);
const keyword = ref('');
const loading = ref(false);
const saving = ref(false);
const rows = ref<Row[]>([]);
const dialog = ref(false);
const rechargeDialog = ref(false);
const rechargeLoading = ref(false);
const rechargeAmount = ref<number>();
const rechargeRequestId = ref('');
const rechargeTarget = ref<Row>();
const ledgerDialog = ref(false);
const ledgerLoading = ref(false);
const ledgerRows = ref<AccountEntry[]>([]);
const ledgerTarget = ref<Row>();
const integrationDrawer = ref(false);
const integrationLoading = ref(false);
const integration = ref<OcpxIntegration>();
const formRef = ref<FormInstance>();
const form = reactive<Record<string, any>>({});
const selectedChannelMacroKeys = ref<string[]>([]);
const channels = ref<Channel[]>([]);
const advertisers = ref<Advertiser[]>([]);
const appLinks = ref<AppLink[]>([]);
const adapters = ref<AdapterDefinition[]>([]);
const eventOptions = ref<EventOption[]>([]);
const eventOptionsLoading = ref(false);
const eventBindingErrors = ref<Record<number, string>>({});
const editorSnapshot = ref('');
const codeGenerationKey = ref('');
const lastChannelId = ref('');
const { copy: copyToClipboard } = useClipboard({ legacy: true });

const config = computed(() => {
  const values = {
    advertiser: {
      add: '新增广告主',
      permission: 'ocpx:advertiser:write',
      title: '广告主管理',
    },
    appLink: {
      add: '新增应用链接',
      permission: 'ocpx:app-link:write',
      title: '应用链接管理',
    },
    channel: {
      add: '新增渠道',
      permission: 'ocpx:channel:write',
      title: '渠道管理',
    },
    deduction: {
      add: '新增扣量规则',
      permission: 'ocpx:deduction:write',
      title: '扣量管理',
    },
    monitor: {
      add: '新增监测链接',
      permission: 'ocpx:monitor:write',
      title: '监测链接管理',
    },
  } as const;
  return values[props.kind];
});

const columns = computed<Column[]>(() => {
  const values: Record<CatalogKind, Column[]> = {
    advertiser: [
      { label: '广告主ID', minWidth: 170, prop: 'id' },
      { label: '广告主名称', minWidth: 150, prop: 'advertiserName' },
      { label: '上游适配器', minWidth: 120, prop: 'adapterCode' },
      { label: '状态', minWidth: 100, prop: 'status' },
      { label: '登录账号', minWidth: 140, prop: 'account' },
      { label: '余额', minWidth: 120, prop: 'balance' },
      { label: '请求地址', minWidth: 240, prop: 'bidUrl' },
      { label: '密钥', minWidth: 90, prop: 'secretConfigured' },
      { label: 'Coco必填宏', minWidth: 220, prop: 'macroParams' },
      {
        label: '上游域名白名单',
        minWidth: 200,
        prop: 'upstreamHostAllowlist',
      },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    appLink: [
      { label: '链接ID', minWidth: 170, prop: 'id' },
      { label: '广告主', minWidth: 150, prop: 'advertiserName' },
      { label: '链接名称', minWidth: 160, prop: 'linkName' },
      { label: '平台', minWidth: 110, prop: 'linkPlatform' },
      { label: '渠道标识', minWidth: 130, prop: 'linkChannel' },
      { label: '应用链接', minWidth: 260, prop: 'linkUrl' },
      { label: 'DeepLink', minWidth: 240, prop: 'deeplink' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    channel: [
      { label: '渠道ID', minWidth: 170, prop: 'id' },
      { label: '渠道名称', minWidth: 160, prop: 'channelName' },
      { label: '渠道编码', minWidth: 150, prop: 'channelCode' },
      { label: '下游适配器', minWidth: 120, prop: 'adapterCode' },
      { label: '鉴权', minWidth: 100, prop: 'authType' },
      { label: '状态', minWidth: 100, prop: 'status' },
      { label: 'SCX OCPX引擎地址', minWidth: 300, prop: 'uri' },
      { label: '密钥', minWidth: 90, prop: 'secretConfigured' },
      { label: '宏参数', minWidth: 240, prop: 'macroParams' },
      {
        label: '回调域名白名单',
        minWidth: 200,
        prop: 'callbackHostAllowlist',
      },
      { label: '创建时间', minWidth: 170, prop: 'createTime' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    deduction: [
      { label: '规则ID', minWidth: 170, prop: 'id' },
      { label: '渠道', minWidth: 160, prop: 'channelName' },
      { label: '应用链接', minWidth: 180, prop: 'appLinkName' },
      { label: '保留比例', minWidth: 120, prop: 'ratio' },
      { label: '创建时间', minWidth: 170, prop: 'createTime' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    monitor: [
      { label: '监测ID', minWidth: 170, prop: 'id' },
      { label: '监测名称', minWidth: 160, prop: 'monitorName' },
      { label: '渠道', minWidth: 140, prop: 'channelName' },
      { label: '广告主', minWidth: 140, prop: 'advertiserName' },
      { label: '应用链接', minWidth: 160, prop: 'appLinkName' },
      { label: '业务ID', minWidth: 130, prop: 'bizId' },
      { label: '点击单价（CPC）', minWidth: 140, prop: 'unitPrice' },
      { label: '事件绑定', minWidth: 260, prop: 'bindingSummary' },
      { label: '发布状态', minWidth: 110, prop: 'routeStatus' },
      { label: '版本', minWidth: 90, prop: 'configVersion' },
      { label: '点击验重窗口', minWidth: 130, prop: 'clickDedupWindowSeconds' },
      {
        label: '上游点击地址（配置/已发布）',
        minWidth: 300,
        prop: 'effectiveUpstreamFeedbackUrl',
      },
      {
        label: '上游地址来源',
        minWidth: 140,
        prop: 'upstreamFeedbackSource',
      },
      { label: '下游回调', minWidth: 260, prop: 'downstreamCallbackUrl' },
      { label: '展示上报接口', minWidth: 300, prop: 'showUrl' },
      { label: '点击上报接口', minWidth: 300, prop: 'clickUrl' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
  };
  return values[props.kind];
});

const rules = computed<FormRules>(() => {
  const required: Record<CatalogKind, string[]> = {
    advertiser: [
      'advertiserName',
      'adapterCode',
      'bidUrl',
      'upstreamHostAllowlist',
      'account',
      'status',
    ],
    appLink: ['advertiserId', 'linkPlatform', 'linkName', 'linkUrl'],
    channel: [
      'channelCode',
      'channelName',
      'adapterCode',
      'authType',
      'keyVersion',
      'uri',
      'macroParams',
      'callbackHostAllowlist',
      'status',
    ],
    deduction: ['channelId', 'appLinkId', 'ratio'],
    monitor: [
      'channelId',
      'advertiserId',
      'appLinkId',
      'monitorName',
      'unitPrice',
      'rtaMode',
      'billingMode',
    ],
  };
  return Object.fromEntries(
    required[props.kind].map((field) => [
      field,
      { message: '此项为必填项', required: true, trigger: 'blur' },
    ]),
  );
});

const filteredAppLinks = computed(() => {
  if (!form.advertiserId) return appLinks.value;
  return appLinks.value.filter(
    (item) => item.advertiserId === form.advertiserId,
  );
});

function display(row: Row, prop: string) {
  const value = row[prop];
  if (prop === 'bindingSummary') {
    return row.bindingCount
      ? `${row.bindingCount} 个 · ${row.bindingSummary || ''}`
      : '0 个';
  }
  if (prop === 'secretConfigured') return value ? '已配置' : '未配置';
  if (prop === 'linkPlatform') {
    return { 1: 'Android', 2: 'iOS', 3: 'Android + iOS', 4: '快应用' }[
      value as 1 | 2 | 3 | 4
    ];
  }
  if (prop === 'ratio') return `${(Number(value || 0) * 100).toFixed(2)}%`;
  if (prop === 'clickDedupWindowSeconds') {
    return Number(value || 0) === 0 ? '关闭' : `${Number(value)} 秒`;
  }
  if (prop === 'upstreamFeedbackSource') {
    return {
      ADVERTISER_DEFAULT: '已发布 · 广告主默认',
      DRAFT_ADVERTISER_DEFAULT: '草稿 · 广告主默认',
      DRAFT_MONITOR_OVERRIDE: '草稿 · 监测链接覆盖',
      MONITOR_OVERRIDE: '已发布 · 监测链接覆盖',
    }[value as string];
  }
  if (prop === 'balance' || prop === 'unitPrice') {
    return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`;
  }
  return value ?? '—';
}

function isCopyableColumn(prop: string) {
  return prop === 'bidUrl' || prop === 'macroParams';
}

async function loadDependencies() {
  const [channelResult, advertiserResult, linkResult, adapterResult] =
    await Promise.all([
      getCatalog('channel', { current: 1, size: 500 }),
      getCatalog('advertiser', { current: 1, size: 500 }),
      getCatalog('appLink', { current: 1, size: 1000 }),
      getAdapters(),
    ]);
  channels.value = channelResult.records;
  advertisers.value = advertiserResult.records;
  appLinks.value = linkResult.records;
  adapters.value = adapterResult;
}

async function load() {
  loading.value = true;
  try {
    const result = await getCatalog(props.kind, {
      current: current.value,
      keyword: keyword.value || undefined,
      size: size.value,
    });
    rows.value = result.records as Row[];
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key]);
  eventBindingErrors.value = {};
  selectedChannelMacroKeys.value = [];
  const defaults: Record<CatalogKind, Record<string, any>> = {
    advertiser: { adapterCode: 'COCO', balance: 0, status: 'ACTIVE' },
    appLink: { linkPlatform: 1 },
    channel: {
      adapterCode: 'STANDARD',
      authType: 'HMAC',
      keyVersion: 1,
      macroParams: DEFAULT_CHANNEL_MACRO_PARAMS,
      status: 'ACTIVE',
    },
    deduction: { ratio: 1 },
    monitor: {
      billingMode: 'OFF',
      clickDedupWindowSeconds: 3600,
      eventBindings: [],
      rtaMode: 'OFF',
      unitPrice: 0,
    },
  };
  Object.assign(form, defaults[props.kind]);
}

async function openEditor(row?: Row) {
  resetForm();
  codeGenerationKey.value =
    !row && props.kind === 'channel' ? createClientRequestId() : '';
  if (row && props.kind === 'monitor') {
    Object.assign(form, await getMonitor(row.id));
  } else if (row) {
    Object.assign(form, row);
  }
  lastChannelId.value = form.channelId || '';
  if (props.kind === 'monitor' && form.channelId) {
    await loadEventOptions(form.channelId);
  }
  editorSnapshot.value = JSON.stringify(form);
  dialog.value = true;
}

async function loadEventOptions(channelId: string) {
  if (!channelId) {
    eventOptions.value = [];
    return;
  }
  eventOptionsLoading.value = true;
  try {
    eventOptions.value = await getChannelEventOptions(channelId);
  } finally {
    eventOptionsLoading.value = false;
  }
}

async function changeMonitorChannel(nextChannelId: string) {
  if (form.eventBindings?.length) {
    try {
      await ElMessageBox.confirm(
        '切换渠道会清空当前事件绑定，确认继续？',
        '切换渠道',
        { type: 'warning' },
      );
      form.eventBindings = [];
    } catch {
      form.channelId = lastChannelId.value;
      return;
    }
  }
  lastChannelId.value = nextChannelId;
  await loadEventOptions(nextChannelId);
}

function addEventBinding() {
  form.eventBindings ||= [];
  form.eventBindings.push({
    assessmentUnitPrice: 0,
    dailyLimit: 0,
    eventMappingId: '',
  });
}

function removeEventBinding(index: number) {
  form.eventBindings.splice(index, 1);
  eventBindingErrors.value = {};
}

function clearEventBindingError(index: number) {
  const next = { ...eventBindingErrors.value };
  delete next[index];
  eventBindingErrors.value = next;
}

function validateEventBindings() {
  const errors: Record<number, string> = {};
  const selected = new Set<string>();
  (form.eventBindings || []).forEach(
    (binding: MonitorEventBinding, index: number) => {
      if (!binding.eventMappingId) {
        errors[index] = '请选择上游事件';
      } else if (selected.has(binding.eventMappingId)) {
        errors[index] = '同一上游事件不能重复绑定';
      } else if (
        !Number.isFinite(Number(binding.assessmentUnitPrice)) ||
        Number(binding.assessmentUnitPrice) < 0
      ) {
        errors[index] = '考核单价不能小于 0';
      } else if (
        !Number.isInteger(Number(binding.dailyLimit)) ||
        Number(binding.dailyLimit) < 0
      ) {
        errors[index] = '日限量须为不小于 0 的整数';
      }
      if (binding.eventMappingId) selected.add(binding.eventMappingId);
    },
  );
  eventBindingErrors.value = errors;
  return Object.keys(errors).length === 0;
}

function eventOption(mappingId?: string) {
  return eventOptions.value.find((item) => item.eventMappingId === mappingId);
}

function eventOptionDisabled(mappingId: string, rowIndex: number) {
  return form.eventBindings?.some(
    (item: MonitorEventBinding, index: number) =>
      index !== rowIndex && item.eventMappingId === mappingId,
  );
}

async function beforeEditorClose(done: () => void) {
  if (JSON.stringify(form) === editorSnapshot.value) {
    done();
    return;
  }
  try {
    await ElMessageBox.confirm('尚有未保存修改，确定关闭？', '未保存修改', {
      type: 'warning',
    });
    done();
  } catch {
    // Keep the editor open.
  }
}

function insertSelectedChannelMacros() {
  form.macroParams = appendChannelMacros(
    form.macroParams,
    selectedChannelMacroKeys.value,
  );
  selectedChannelMacroKeys.value = [];
}

function useDefaultChannelMacroParams() {
  form.macroParams = DEFAULT_CHANNEL_MACRO_PARAMS;
  selectedChannelMacroKeys.value = [];
}

async function save() {
  if (!(await formRef.value?.validate())) return;
  if (props.kind === 'advertiser' && !form.id && !form.password) {
    ElMessage.warning('新增广告主时密码必填，至少 8 位');
    return;
  }
  saving.value = true;
  try {
    const payload = { ...form };
    if (props.kind === 'monitor') {
      payload.eventBindings = (form.eventBindings || []).map(
        (binding: MonitorEventBinding) => ({
          assessmentUnitPrice: binding.assessmentUnitPrice,
          dailyLimit: binding.dailyLimit,
          eventMappingId: binding.eventMappingId,
        }),
      );
      if (!validateEventBindings()) return;
    }
    await saveCatalog(props.kind, payload as never);
    ElMessage.success('保存成功');
    editorSnapshot.value = JSON.stringify(form);
    dialog.value = false;
    await Promise.all([load(), loadDependencies()]);
  } finally {
    saving.value = false;
  }
}

async function remove(row: Row) {
  await ElMessageBox.confirm('删除后不可在页面恢复，确认继续？', '确认删除', {
    type: 'warning',
  });
  await deleteCatalog(props.kind, row.id);
  ElMessage.success('已删除');
  await Promise.all([load(), loadDependencies()]);
}

async function publish(row: Row) {
  await ElMessageBox.confirm(
    '发布后数据面将按此版本接收并转发流量，确认继续？',
    '发布路由',
    { type: 'warning' },
  );
  await publishMonitor(row.id);
  ElMessage.success('路由已发布');
  await load();
}

function openRecharge(row: Row) {
  rechargeTarget.value = row;
  rechargeAmount.value = undefined;
  rechargeRequestId.value = createClientRequestId();
  rechargeDialog.value = true;
}

async function openLedger(row: Row) {
  ledgerTarget.value = row;
  ledgerDialog.value = true;
  ledgerLoading.value = true;
  try {
    const result = await getAdvertiserLedger(row.id, {
      current: 1,
      size: 100,
    });
    ledgerRows.value = result.records;
  } finally {
    ledgerLoading.value = false;
  }
}

async function openIntegration(row: Row) {
  integrationDrawer.value = true;
  integrationLoading.value = true;
  integration.value = undefined;
  try {
    integration.value = await getMonitorIntegration(row.id);
  } finally {
    integrationLoading.value = false;
  }
}

async function recharge() {
  if (
    !rechargeTarget.value ||
    !rechargeAmount.value ||
    rechargeAmount.value <= 0
  ) {
    ElMessage.warning('请输入大于 0 的充值金额');
    return;
  }
  rechargeLoading.value = true;
  try {
    await rechargeAdvertiser(
      rechargeTarget.value.id,
      rechargeAmount.value,
      rechargeRequestId.value,
    );
    ElMessage.success('充值成功，账务流水已生成');
    rechargeDialog.value = false;
    await load();
  } finally {
    rechargeLoading.value = false;
  }
}

async function copy(value?: string, label = '链接') {
  if (!value) return;
  try {
    await copyToClipboard(value);
    ElMessage.success(`${label}已复制`);
  } catch {
    ElMessage.error('复制失败，请手动选择并复制');
  }
}

onMounted(async () => {
  await Promise.all([load(), loadDependencies()]);
});
</script>

<template>
  <div class="ocpx-page">
    <section class="page-heading">
      <h1>{{ config.title }}</h1>
      <el-button
        v-access:code="config.permission"
        type="primary"
        @click="openEditor()"
      >
        {{ config.add }}
      </el-button>
    </section>

    <el-card shadow="never" class="data-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="输入名称、账号或地址搜索"
          @keyup.enter="load"
        />
        <el-button type="primary" :loading="loading" @click="load">
          查询
        </el-button>
        <el-button
          @click="
            keyword = '';
            load();
          "
        >
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        height="calc(100vh - 310px)"
      >
        <el-table-column
          v-for="column in columns"
          :key="column.prop"
          :label="column.label"
          :min-width="column.minWidth"
          :prop="column.prop"
          :width="column.width"
          :show-overflow-tooltip="!isCopyableColumn(column.prop)"
        >
          <template #default="{ row }">
            <el-tag
              v-if="column.prop === 'secretConfigured'"
              :type="row.secretConfigured ? 'success' : 'info'"
              size="small"
            >
              {{ display(row, column.prop) }}
            </el-tag>
            <el-tooltip
              v-else-if="isCopyableColumn(column.prop) && row[column.prop]"
              :content="row[column.prop]"
              placement="top"
            >
              <button
                :aria-label="`复制${column.label}：${row[column.prop]}`"
                class="copyable-cell"
                type="button"
                @click="copy(row[column.prop], column.label)"
              >
                <span class="copyable-cell__text">{{ row[column.prop] }}</span>
                <el-icon aria-hidden="true" class="copyable-cell__icon">
                  <CopyDocument />
                </el-icon>
              </button>
            </el-tooltip>
            <span v-else>{{ display(row, column.prop) }}</span>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="380">
          <template #default="{ row }">
            <el-button
              v-access:code="config.permission"
              link
              type="primary"
              @click="openEditor(row)"
              >编辑</el-button
            >
            <el-button
              v-if="kind === 'advertiser'"
              v-access:code="'ocpx:account:recharge'"
              link
              type="success"
              @click="openRecharge(row)"
            >
              充值
            </el-button>
            <el-button
              v-if="kind === 'advertiser'"
              v-access:code="'ocpx:account:read'"
              link
              @click="openLedger(row)"
            >
              账务
            </el-button>
            <el-button
              v-if="kind === 'monitor'"
              link
              @click="copy(row.clickUrl, '链接')"
            >
              复制
            </el-button>
            <el-button
              v-if="kind === 'monitor'"
              v-access:code="'ocpx:monitor:read'"
              link
              type="primary"
              @click="openIntegration(row)"
            >
              接入信息
            </el-button>
            <el-button
              v-if="kind === 'monitor'"
              v-access:code="'ocpx:monitor:publish'"
              link
              type="success"
              @click="publish(row)"
            >
              发布
            </el-button>
            <el-button
              v-access:code="config.permission"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <el-dialog
      v-model="dialog"
      :before-close="beforeEditorClose"
      :close-on-click-modal="false"
      :title="form.id ? `编辑${config.title}` : config.add"
      :width="kind === 'monitor' ? '1080px' : '640px'"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="118px">
        <template v-if="kind === 'channel'">
          <el-form-item label="渠道编码" prop="channelCode">
            <AutoCodeInput
              v-model="form.channelCode"
              :disabled="Boolean(form.id)"
              :generation-key="codeGenerationKey"
              :maxlength="32"
              resource-type="CHANNEL"
            />
          </el-form-item>
          <el-form-item label="渠道名称" prop="channelName">
            <el-input v-model="form.channelName" maxlength="100" />
          </el-form-item>
          <el-form-item label="下游适配器" prop="adapterCode">
            <el-select v-model="form.adapterCode">
              <el-option
                v-for="adapter in adapters.filter(
                  (item) =>
                    item.direction === 'DOWNSTREAM' && item.status === 'ACTIVE',
                )"
                :key="adapter.adapterCode"
                :label="adapter.adapterName"
                :value="adapter.adapterCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="鉴权方式" prop="authType">
            <el-select v-model="form.authType">
              <el-option label="HMAC-SHA256" value="HMAC" />
              <el-option label="无鉴权（仅联调）" value="NONE" />
            </el-select>
          </el-form-item>
          <el-form-item label="密钥版本" prop="keyVersion">
            <el-input-number v-model="form.keyVersion" :min="1" />
          </el-form-item>
          <el-form-item label="SCX OCPX引擎地址" prop="uri">
            <el-input
              v-model="form.uri"
              placeholder="https://ocpx.example.com/ocpx"
            />
            <span class="field-hint">
              系统据此生成 V70 HMAC POST 上报接口；不是媒体旧式 GET 监测模板。
            </span>
          </el-form-item>
          <el-form-item label="渠道密钥">
            <el-input
              v-model="form.secretKey"
              show-password
              :placeholder="form.id ? '留空表示不修改' : '发布前至少配置 32 位'"
            />
          </el-form-item>
          <el-form-item label="宏参数" prop="macroParams">
            <div class="macro-editor">
              <el-input
                v-model="form.macroParams"
                maxlength="1000"
                :rows="4"
                show-word-limit
                type="textarea"
              />
              <div class="macro-picker">
                <el-select
                  v-model="selectedChannelMacroKeys"
                  collapse-tags
                  collapse-tags-tooltip
                  multiple
                  placeholder="选择需要追加的常用宏参数"
                >
                  <el-option
                    v-for="option in CHANNEL_MACRO_OPTIONS"
                    :key="option.key"
                    :label="`${option.label} · ${option.key}=${option.placeholder}`"
                    :value="option.key"
                  />
                </el-select>
                <div class="macro-picker__actions">
                  <el-button
                    :disabled="selectedChannelMacroKeys.length === 0"
                    type="primary"
                    plain
                    @click="insertSelectedChannelMacros"
                  >
                    追加所选
                  </el-button>
                  <el-button @click="useDefaultChannelMacroParams">
                    使用完整模板
                  </el-button>
                </div>
              </div>
              <p class="macro-editor__hint">
                可手动编辑；追加时按标准顺序写入，已存在的参数不会重复。
              </p>
            </div>
          </el-form-item>
          <el-form-item label="回调域名白名单" prop="callbackHostAllowlist">
            <el-input
              v-model="form.callbackHostAllowlist"
              placeholder="callback.media.example，多个用逗号分隔"
            />
          </el-form-item>
          <el-form-item label="渠道状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio-button value="ACTIVE">启用</el-radio-button>
              <el-radio-button value="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>

        <template v-else-if="kind === 'advertiser'">
          <el-form-item label="广告主名称" prop="advertiserName">
            <el-input v-model="form.advertiserName" maxlength="100" />
          </el-form-item>
          <el-form-item label="上游适配器" prop="adapterCode">
            <el-select v-model="form.adapterCode">
              <el-option
                v-for="adapter in adapters.filter(
                  (item) =>
                    item.direction === 'UPSTREAM' && item.status === 'ACTIVE',
                )"
                :key="adapter.adapterCode"
                :label="adapter.adapterName"
                :value="adapter.adapterCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="请求地址" prop="bidUrl">
            <el-input v-model="form.bidUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="广告主密钥">
            <el-input
              v-model="form.secretKey"
              show-password
              :placeholder="form.id ? '留空表示不修改' : '可选'"
            />
          </el-form-item>
          <el-form-item label="上游必填宏">
            <el-input
              v-model="form.macroParams"
              :rows="3"
              placeholder="例如 __REQUEST_ID__,__CALLBACK_URL__；按所选适配器填写"
              type="textarea"
            />
          </el-form-item>
          <el-form-item label="上游域名白名单" prop="upstreamHostAllowlist">
            <el-input
              v-model="form.upstreamHostAllowlist"
              placeholder="api.coco.example，多个用逗号分隔"
            />
          </el-form-item>
          <el-form-item label="登录账号" prop="account">
            <el-input v-model="form.account" />
          </el-form-item>
          <el-form-item label="登录密码" :required="!form.id">
            <el-input
              v-model="form.password"
              minlength="8"
              show-password
              :placeholder="form.id ? '留空表示不修改' : '至少 8 位'"
            />
          </el-form-item>
          <el-form-item label="广告主状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio-button value="ACTIVE">启用</el-radio-button>
              <el-radio-button value="DISABLED">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>

        <template v-else-if="kind === 'appLink'">
          <el-form-item label="所属广告主" prop="advertiserId">
            <el-select v-model="form.advertiserId" filterable>
              <el-option
                v-for="item in advertisers"
                :key="item.id"
                :label="item.advertiserName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="应用平台" prop="linkPlatform">
            <el-radio-group v-model="form.linkPlatform">
              <el-radio-button :value="1">Android</el-radio-button>
              <el-radio-button :value="2">iOS</el-radio-button>
              <el-radio-button :value="3">双端</el-radio-button>
              <el-radio-button :value="4">快应用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="链接名称" prop="linkName">
            <el-input v-model="form.linkName" />
          </el-form-item>
          <el-form-item label="渠道标识">
            <el-input v-model="form.linkChannel" />
          </el-form-item>
          <el-form-item label="应用链接" prop="linkUrl">
            <el-input v-model="form.linkUrl" />
          </el-form-item>
          <el-form-item label="DeepLink">
            <el-input v-model="form.deeplink" />
          </el-form-item>
          <el-form-item label="扩展参数">
            <el-input v-model="form.linkExt" :rows="3" type="textarea" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.linkRemark" :rows="2" type="textarea" />
          </el-form-item>
        </template>

        <template v-else-if="kind === 'monitor'">
          <el-form-item label="所属渠道" prop="channelId">
            <el-select
              v-model="form.channelId"
              filterable
              @change="changeMonitorChannel"
            >
              <el-option
                v-for="item in channels"
                :key="item.id"
                :label="item.channelName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="所属广告主" prop="advertiserId">
            <el-select v-model="form.advertiserId" filterable>
              <el-option
                v-for="item in advertisers"
                :key="item.id"
                :label="item.advertiserName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="投放链接" prop="appLinkId">
            <el-select v-model="form.appLinkId" filterable>
              <el-option
                v-for="item in filteredAppLinks"
                :key="item.id"
                :label="item.linkName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="监测名称" prop="monitorName">
            <el-input v-model="form.monitorName" />
          </el-form-item>
          <el-form-item label="业务ID">
            <el-input v-model="form.bizId" />
          </el-form-item>
          <el-form-item label="点击单价（CPC）" prop="unitPrice">
            <el-input-number v-model="form.unitPrice" :min="0" :precision="6" />
            <span class="field-hint"
              >仅用于现有点击计费；转化事件按下方 CPA 考核单价扣费。</span
            >
          </el-form-item>
          <el-form-item label="事件绑定">
            <div v-loading="eventOptionsLoading" class="event-bindings">
              <div class="event-bindings__header" aria-hidden="true">
                <span>上游事件</span>
                <span>系统转化事件</span>
                <span>媒体/渠道事件</span>
                <span>考核单价（CPA）</span>
                <span>日限量</span>
                <span>操作</span>
              </div>
              <div
                v-for="(binding, index) in form.eventBindings"
                :key="binding.id || index"
                class="event-bindings__row"
              >
                <div class="event-bindings__select">
                  <el-select
                    v-model="binding.eventMappingId"
                    :aria-label="`第 ${index + 1} 行上游事件`"
                    filterable
                    placeholder="选择上游事件"
                    @change="clearEventBindingError(index)"
                  >
                    <el-option
                      v-for="option in eventOptions"
                      :key="option.eventMappingId"
                      :disabled="
                        eventOptionDisabled(option.eventMappingId, index)
                      "
                      :label="option.bindingDisplayName"
                      :value="option.eventMappingId"
                    />
                  </el-select>
                  <span
                    v-if="eventBindingErrors[index]"
                    class="event-bindings__error"
                    role="alert"
                  >
                    {{ eventBindingErrors[index] }}
                  </span>
                </div>
                <el-input
                  :model-value="
                    eventOption(binding.eventMappingId)?.conversionEventName ||
                    '—'
                  "
                  aria-label="自动匹配的系统转化事件"
                  readonly
                />
                <el-input
                  :model-value="
                    eventOption(binding.eventMappingId)?.mediaEventName || '—'
                  "
                  aria-label="自动匹配的媒体渠道事件"
                  readonly
                />
                <el-input-number
                  v-model="binding.assessmentUnitPrice"
                  :aria-label="`第 ${index + 1} 行考核单价`"
                  :min="0"
                  :precision="6"
                  controls-position="right"
                />
                <el-input-number
                  v-model="binding.dailyLimit"
                  :aria-label="`第 ${index + 1} 行日限量`"
                  :min="0"
                  :precision="0"
                  controls-position="right"
                />
                <el-button
                  :aria-label="`删除第 ${index + 1} 条事件绑定`"
                  link
                  type="danger"
                  @click="removeEventBinding(index)"
                >
                  删除
                </el-button>
              </div>
              <el-empty
                v-if="!form.eventBindings?.length"
                :image-size="48"
                description="暂未绑定事件；新链接默认不添加事件"
              />
              <el-button
                :disabled="!form.channelId"
                plain
                type="primary"
                @click="addEventBinding"
              >
                + 增加事件映射
              </el-button>
              <p class="field-hint">
                0 元表示免费事件；0
                次表示不限量。保存后进入草稿，重新发布才影响新流量。
              </p>
            </div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" :rows="3" type="textarea" />
          </el-form-item>
          <el-form-item label="上游点击地址覆盖">
            <el-input
              v-model="form.upstreamFeedbackUrl"
              placeholder="留空时使用广告主默认 URL"
            />
            <span class="field-hint">
              仅覆盖当前监测链接；保存后路由回到草稿，重新发布才生效。
            </span>
          </el-form-item>
          <el-form-item label="下游回调地址">
            <el-input
              v-model="form.downstreamCallbackUrl"
              placeholder="标准 POST 地址，或 GET 宏模板地址"
            />
            <span class="field-hint">
              TEMPLATE_GET_DOWNSTREAM 至少包含 __EVENT__、__EVENT_ID__。
            </span>
          </el-form-item>
          <el-form-item label="点击验重窗口" prop="clickDedupWindowSeconds">
            <el-input-number
              v-model="form.clickDedupWindowSeconds"
              :max="604800"
              :min="0"
              :step="60"
            />
            <span class="field-hint">
              按监测路由和设备指纹验重，单位秒；0 仅关闭设备窗口，事件 ID
              幂等始终生效。
            </span>
          </el-form-item>
          <el-form-item label="RTA模式" prop="rtaMode">
            <el-select v-model="form.rtaMode">
              <el-option label="关闭" value="OFF" />
            </el-select>
          </el-form-item>
          <el-form-item label="计费模式" prop="billingMode">
            <el-select v-model="form.billingMode">
              <el-option label="关闭" value="OFF" />
              <el-option label="仅审计" value="AUDIT_ONLY" />
              <el-option label="真实 CPC 点击扣费" value="CPC" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="所属渠道" prop="channelId">
            <el-select v-model="form.channelId" filterable>
              <el-option
                v-for="item in channels"
                :key="item.id"
                :label="item.channelName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="应用链接" prop="appLinkId">
            <el-select v-model="form.appLinkId" filterable>
              <el-option
                v-for="item in appLinks"
                :key="item.id"
                :label="`${item.advertiserName} · ${item.linkName}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="保留比例" prop="ratio">
            <el-input-number
              v-model="form.ratio"
              :max="1"
              :min="0"
              :precision="4"
              :step="0.01"
            />
            <span class="field-hint">1.0 表示不扣量，0.8 表示保留 80%</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="beforeEditorClose(() => (dialog = false))"
          >取消</el-button
        >
        <el-button type="primary" :loading="saving" @click="save">
          确认
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rechargeDialog" title="广告主充值" width="440px">
      <el-form label-width="100px">
        <el-form-item label="广告主">
          {{ rechargeTarget?.advertiserName }}
        </el-form-item>
        <el-form-item label="充值金额" required>
          <el-input-number
            v-model="rechargeAmount"
            :min="0.01"
            :precision="2"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialog = false">取消</el-button>
        <el-button type="primary" :loading="rechargeLoading" @click="recharge">
          确认充值
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ledgerDialog"
      :title="`${ledgerTarget?.advertiserName || ''} · 账务流水`"
      width="1280px"
    >
      <el-alert
        :closable="false"
        class="ledger-alert"
        show-icon
        title="充值、CPC 点击和 CPA 转化均按业务事件幂等入账；REJECTED 表示余额不足且未扣款。"
        type="info"
      />
      <el-table
        v-loading="ledgerLoading"
        :data="ledgerRows"
        border
        max-height="520"
      >
        <el-table-column label="时间" min-width="170" prop="createTime" />
        <el-table-column label="类型" min-width="110" prop="mode" />
        <el-table-column label="状态" min-width="100" prop="status" />
        <el-table-column
          label="源事件码"
          min-width="150"
          prop="sourceEventCode"
          show-overflow-tooltip
        />
        <el-table-column
          label="系统事件码"
          min-width="150"
          prop="conversionEventCode"
          show-overflow-tooltip
        />
        <el-table-column
          label="媒体事件码"
          min-width="150"
          prop="mediaEventCode"
          show-overflow-tooltip
        />
        <el-table-column label="金额" min-width="120">
          <template #default="{ row }">
            {{ row.amount == null ? '—' : `¥${Number(row.amount).toFixed(6)}` }}
          </template>
        </el-table-column>
        <el-table-column label="账前余额" min-width="130">
          <template #default="{ row }">
            {{
              row.balanceBefore == null
                ? '—'
                : `¥${Number(row.balanceBefore).toFixed(6)}`
            }}
          </template>
        </el-table-column>
        <el-table-column label="账后余额" min-width="130">
          <template #default="{ row }">
            {{
              row.balanceAfter == null
                ? '—'
                : `¥${Number(row.balanceAfter).toFixed(6)}`
            }}
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="180" prop="reason" />
        <el-table-column label="操作人" min-width="120" prop="operatorId" />
      </el-table>
    </el-dialog>

    <el-drawer
      v-model="integrationDrawer"
      :title="`${integration?.monitorName || '监测链接'} · OCPX接入信息`"
      size="620px"
    >
      <div v-loading="integrationLoading" class="integration-panel">
        <template v-if="integration">
          <el-alert
            :closable="false"
            show-icon
            title="页面不会展示渠道密钥；X-Sign 需由下游服务端或 Apifox 前置脚本计算。"
            type="info"
          />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="协议版本">
              {{ integration.schemaVersion }}
            </el-descriptions-item>
            <el-descriptions-item label="路由状态">
              {{ integration.routeStatus }}
            </el-descriptions-item>
            <el-descriptions-item label="上下游适配器">
              {{ integration.downstreamAdapterCode }} →
              {{ integration.upstreamAdapterCode }}
            </el-descriptions-item>
            <el-descriptions-item label="Feedback URL">
              <div class="integration-copy-row">
                <code
                  >{{ integration.feedbackMethod }}
                  {{ integration.feedbackUrl }}</code
                >
                <el-button
                  link
                  type="primary"
                  @click="copy(integration.feedbackUrl, 'Feedback URL')"
                >
                  复制
                </el-button>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="鉴权">
              {{ integration.authType }} · Key Version
              {{ integration.keyVersion }} ·
              {{ integration.secretConfigured ? '密钥已配置' : '密钥未配置' }}
            </el-descriptions-item>
            <el-descriptions-item label="上游 Callback">
              <code>{{ integration.callbackPathPattern }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="Postman / Apifox">
              <code>{{ integration.postmanCollectionPath }}</code>
            </el-descriptions-item>
          </el-descriptions>

          <h3>请求头</h3>
          <pre>{{ integration.requiredHeaders.join('\n') }}</pre>
          <h3>签名公式</h3>
          <pre>{{ integration.signatureFormula }}</pre>
          <h3>CLICK 请求示例</h3>
          <div class="integration-code-heading">
            <span>JSON Body</span>
            <el-button
              link
              type="primary"
              @click="copy(integration.requestExample, '请求示例')"
            >
              复制
            </el-button>
          </div>
          <pre>{{ integration.requestExample }}</pre>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.ocpx-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.page-heading {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  color: #172033;
}

.data-card {
  border-color: #e6eaf0;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.toolbar .el-input {
  width: 360px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.el-select {
  width: 100%;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #98a2b3;
}

.copyable-cell {
  display: flex;
  width: 100%;
  min-width: 0;
  padding: 2px 0;
  color: var(--el-color-primary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 4px;
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

.copyable-cell:hover {
  color: var(--el-color-primary-light-3);
  background: var(--el-color-primary-light-9);
}

.copyable-cell:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.copyable-cell__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copyable-cell__icon {
  flex: none;
  margin: 2px 0 0 6px;
}

.macro-editor {
  width: 100%;
}

.macro-picker {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 10px;
}

.macro-picker__actions {
  display: flex;
  flex: none;
}

.macro-editor__hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.integration-panel {
  min-height: 300px;
}

.integration-panel .el-alert,
.integration-panel .el-descriptions {
  margin-bottom: 18px;
}

.integration-panel h3 {
  margin: 18px 0 8px;
  font-size: 14px;
}

.integration-panel pre {
  padding: 12px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.6;
  color: #d7e1f3;
  white-space: pre-wrap;
  background: #172033;
  border-radius: 6px;
}

.integration-copy-row,
.integration-code-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.integration-copy-row code {
  overflow-wrap: anywhere;
}

.event-bindings {
  width: 100%;
}

.event-bindings__header,
.event-bindings__row {
  display: grid;
  grid-template-columns:
    minmax(150px, 1.2fr) minmax(130px, 1fr) minmax(130px, 1fr)
    150px 130px 56px;
  gap: 10px;
  align-items: center;
}

.event-bindings__header {
  padding: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.event-bindings__row {
  align-items: start;
  margin-bottom: 10px;
}

.event-bindings__select {
  min-width: 0;
}

.event-bindings__error {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.3;
  color: var(--el-color-danger);
}

.event-bindings :deep(.el-input-number),
.event-bindings :deep(.el-select) {
  width: 100%;
}

@media (max-width: 768px) {
  .ocpx-page {
    padding: 12px;
  }

  .page-heading {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .toolbar .el-input {
    width: 100%;
  }

  .macro-picker {
    flex-direction: column;
  }

  .event-bindings__header {
    display: none;
  }

  .event-bindings__row {
    grid-template-columns: 1fr;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
