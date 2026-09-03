<script lang="ts" setup>
import type { FormInstance, FormRules, TreeInstance } from 'element-plus';

import type {
  AdvertiserRoleMenuNode,
  AdvertiserRolePermissions,
  AdvertiserV2,
  BillingEntry,
  EntityOption,
  OcpxUpstreamProtocol,
  ProtocolConfig,
} from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  advertiserOptions,
  appendAdvertiserMacroParams,
  createAdvertiserV2,
  deductAdvertiser,
  deleteAdvertiserV2,
  getAdvertiserRolePermissions,
  getProtocolConfigs,
  listAdvertisers,
  listBillingEntries,
  rechargeAdvertiser,
  updateAdvertiserRolePermissions,
  updateAdvertiserV2,
  updateProtocolAllowedHosts,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';
import {
  getPasswordStrengthError,
  PASSWORD_COMPLEXITY_HINT,
} from '#/utils/password-rules';
import { createClientRequestId } from '#/utils/request-id';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxAdvertiserManagement' });

const columns = [
  { key: 'id', label: '广告主编号' },
  { key: 'name', label: '名称' },
  { key: 'protocol', label: '上游协议' },
  { key: 'username', label: '用户名' },
  { key: 'balance', label: '余额' },
  { key: 'credential', label: '凭据引用' },
  { key: 'bidUrl', label: '请求地址' },
  { key: 'macro', label: '宏参数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<AdvertiserV2[]>([]);
const advertiserSelects = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ advertiserId: '', current: 1, size: 30 });

const dialogOpen = ref(false);
const editing = ref<AdvertiserV2>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserName: '',
  bidUrl: '',
  credentialConfigKey: '',
  macroParams: '',
  password: '',
  upstreamProtocol: '' as '' | OcpxUpstreamProtocol,
  username: '',
});
const protocolOptions: Array<{ label: string; value: OcpxUpstreamProtocol }> = [
  { label: 'AERIM', value: 'AERIM' },
  { label: '引力', value: 'GRAVITY' },
  { label: '凤凰新闻', value: 'PHOENIX' },
  { label: '人人视频', value: 'RENREN_VIDEO' },
  { label: '微风互动', value: 'WEIFENG' },
  { label: '通用 GET 模板', value: 'TEMPLATE_GET' },
];
const protocolLabels: Record<OcpxUpstreamProtocol, string> = {
  AERIM: 'AERIM',
  GRAVITY: '引力',
  PHOENIX: '凤凰新闻',
  RENREN_VIDEO: '人人视频',
  TEMPLATE_GET: '通用 GET 模板',
  WEIFENG: '微风互动',
};
// Keep the protocol-level credential rule explicit for the management contract.
const showCredentialFields = computed(() => !['GRAVITY', 'RENREN_VIDEO'].includes(form.upstreamProtocol) && form.upstreamProtocol !== 'PHOENIX');
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes([
    'ocpx:advertiser:edit',
    'ocpx:advertiser:recharge',
    'ocpx:advertiser:deduct',
    'ocpx:advertiser:delete',
    'ocpx:billing-entry:view',
  ]),
);
const canViewBilling = computed(() =>
  hasAccessByCodes(['ocpx:billing-entry:view']),
);
const loadGuard = createLatestRequestGuard();

type PermissionTreeNode = AdvertiserRoleMenuNode & {
  children: PermissionTreeNode[];
  disabled: boolean;
};

const rolePermissionOpen = ref(false);
const rolePermissionLoading = ref(false);
const rolePermissionSaving = ref(false);
const rolePermissionConfig = ref<AdvertiserRolePermissions>();
const rolePermissionTreeRef = ref<TreeInstance>();
const rolePermissionForm = reactive({
  allDataScope: false,
  menuIds: [] as string[],
});
const rolePermissionTree = computed<PermissionTreeNode[]>(() =>
  decoratePermissionTree(
    rolePermissionConfig.value?.menuTree || [],
    rolePermissionForm.allDataScope,
  ),
);

function validatePassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) {
  const error = getPasswordStrengthError(value, { required: true });
  callback(error ? new Error(error) : undefined);
}

const rules: FormRules = {
  advertiserName: [
    { message: '请输入广告主名称', required: true, trigger: 'blur' },
  ],
  bidUrl: [
    {
      trigger: 'blur',
      validator: (
        _rule: unknown,
        value: string,
        callback: (error?: Error) => void,
      ) =>
        callback(
          form.upstreamProtocol === 'TEMPLATE_GET' && !value.trim()
            ? new Error('通用 GET 模板必须填写请求地址')
            : undefined,
        ),
    },
  ],
  password: [{ trigger: 'blur', validator: validatePassword }],
  username: [
    {
      message: '请输入用户名',
      pattern: /\S/,
      required: true,
      trigger: 'blur',
    },
    { max: 100, message: '用户名不能超过 100 个字符', trigger: 'blur' },
  ],
  upstreamProtocol: [
    { message: '请选择上游协议', required: true, trigger: 'change' },
  ],
};

const protocolConfigOpen = ref(false);
const protocolConfigLoading = ref(false);
const protocolConfigSaving = ref<ProtocolConfig['protocol']>();
const protocolConfigs = reactive<Record<ProtocolConfig['protocol'], string[]>>({
  AERIM: [],
  GRAVITY: [],
  RENREN_VIDEO: [],
  PHOENIX: [],
  WEIFENG: [],
});

async function openProtocolConfig() {
  protocolConfigOpen.value = true;
  protocolConfigLoading.value = true;
  try {
    const result = await getProtocolConfigs();
    protocolConfigs.AERIM = [
      ...(result.find((item) => item.protocol === 'AERIM')?.allowedHosts || []),
    ];
    protocolConfigs.GRAVITY = [
      ...(result.find((item) => item.protocol === 'GRAVITY')?.allowedHosts ||
        []),
    ];
    protocolConfigs.RENREN_VIDEO = [
      ...(result.find((item) => item.protocol === 'RENREN_VIDEO')
        ?.allowedHosts || []),
    ];
    protocolConfigs.PHOENIX = [
      ...(result.find((item) => item.protocol === 'PHOENIX')?.allowedHosts ||
        []),
    ];
    protocolConfigs.WEIFENG = [
      ...(result.find((item) => item.protocol === 'WEIFENG')?.allowedHosts ||
        []),
    ];
  } catch {
    protocolConfigOpen.value = false;
  } finally {
    protocolConfigLoading.value = false;
  }
}

function addProtocolHost(protocol: ProtocolConfig['protocol']) {
  protocolConfigs[protocol].push('');
}

function removeProtocolHost(
  protocol: ProtocolConfig['protocol'],
  index: number,
) {
  protocolConfigs[protocol].splice(index, 1);
}

async function saveProtocolHosts(protocol: ProtocolConfig['protocol']) {
  const allowedHosts = protocolConfigs[protocol]
    .map((host) => host.trim())
    .filter(Boolean);
  if (
    new Set(allowedHosts.map((host) => host.toLowerCase())).size !==
    allowedHosts.length
  ) {
    ElMessage.warning('允许域名不能重复');
    return;
  }
  if (allowedHosts.length === 0) {
    await ElMessageBox.confirm(
      `保存空白名单后，${protocolLabels[protocol]} 的所有上游请求都会被拒绝。确认继续？`,
      '确认停用协议出网',
      { confirmButtonText: '确认保存', type: 'warning' },
    );
  }
  protocolConfigSaving.value = protocol;
  try {
    const result = await updateProtocolAllowedHosts(protocol, allowedHosts);
    protocolConfigs[protocol] = [...result.allowedHosts];
    ElMessage.success(`${protocolLabels[protocol]} 允许域名已更新`);
  } finally {
    protocolConfigSaving.value = undefined;
  }
}

const rechargeOpen = ref(false);
const rechargeLoading = ref(false);
const rechargeId = ref('');
const rechargeFormRef = ref<FormInstance>();
const rechargeForm = reactive({ amount: '', remark: '', requestId: '' });
const rechargeRules: FormRules = {
  amount: [
    { message: '请输入充值金额', required: true },
    {
      message: '请输入大于 0、最多 6 位小数的金额',
      pattern: /^(?!0+(?:\.0+)?$)\d+(?:\.\d{1,6})?$/,
    },
  ],
};

const deductOpen = ref(false);
const deductLoading = ref(false);
const deductTarget = ref<AdvertiserV2>();
const deductFormRef = ref<FormInstance>();
const deductForm = reactive({ amount: '', remark: '', requestId: '' });
const moneyPattern = /^(?!0+(?:\.0+)?$)\d+(?:\.\d{1,6})?$/;
const deductRules: FormRules = {
  amount: [
    { message: '请输入扣减金额', required: true },
    {
      trigger: 'blur',
      validator: (
        _rule: unknown,
        value: string,
        callback: (error?: Error) => void,
      ) => {
        if (!moneyPattern.test(value)) {
          callback(new Error('请输入大于 0、最多 6 位小数的金额'));
          return;
        }
        const balance = Number(deductTarget.value?.balance || 0);
        callback(
          Number(value) > balance
            ? new Error('扣减金额不能超过当前余额')
            : undefined,
        );
      },
    },
  ],
  remark: [
    { message: '请输入扣减原因', required: true, trigger: 'blur' },
    {
      message: '扣减原因不能只包含空格',
      pattern: /\S/,
      trigger: 'blur',
    },
  ],
};
const accountActionLoading = computed(
  () => rechargeLoading.value || deductLoading.value,
);

const ledgerOpen = ref(false);
const ledgerLoading = ref(false);
const ledgerTarget = ref<AdvertiserV2>();
const ledgerRows = ref<BillingEntry[]>([]);
const ledgerTotal = ref(0);
const ledgerQuery = reactive({
  businessKey: '',
  current: 1,
  entryType: '',
  range: [] as string[],
  size: 30,
  status: '',
});
const billingTypeLabels: Record<string, string> = {
  CLICK_CHARGE: '点击消费',
  CONVERSION_CHARGE: '转化消费',
  MANUAL_DEBIT: '人工扣减',
  RECHARGE: '充值',
};

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

function decoratePermissionTree(
  nodes: AdvertiserRoleMenuNode[],
  allDataScope: boolean,
): PermissionTreeNode[] {
  return nodes.map((node) => ({
    ...node,
    children: decoratePermissionTree(node.children || [], allDataScope),
    disabled: node.requiresAllDataScope && !allDataScope,
  }));
}

function flattenPermissionIds(
  nodes: AdvertiserRoleMenuNode[],
  includeGlobal: boolean,
): string[] {
  return nodes.flatMap((node) => [
    ...(includeGlobal || !node.requiresAllDataScope ? [node.id] : []),
    ...flattenPermissionIds(node.children || [], includeGlobal),
  ]);
}

function captureRolePermissionMenus() {
  rolePermissionForm.menuIds = (
    rolePermissionTreeRef.value?.getCheckedKeys(false) || []
  ).map(String);
}

async function openRolePermissions() {
  rolePermissionOpen.value = true;
  rolePermissionLoading.value = true;
  try {
    const result = await getAdvertiserRolePermissions();
    rolePermissionConfig.value = result;
    rolePermissionForm.allDataScope = result.allDataScope;
    rolePermissionForm.menuIds = [...result.selectedMenuIds];
    await nextTick();
    rolePermissionTreeRef.value?.setCheckedKeys(result.selectedMenuIds, false);
  } catch {
    rolePermissionOpen.value = false;
  } finally {
    rolePermissionLoading.value = false;
  }
}

async function changeAllDataScope(enabled: boolean) {
  rolePermissionForm.allDataScope = enabled;
  if (!enabled && rolePermissionConfig.value) {
    const allowed = new Set(
      flattenPermissionIds(rolePermissionConfig.value.menuTree, false),
    );
    rolePermissionForm.menuIds = rolePermissionForm.menuIds.filter((id) =>
      allowed.has(id),
    );
    await nextTick();
    rolePermissionTreeRef.value?.setCheckedKeys(
      rolePermissionForm.menuIds,
      false,
    );
  }
}

async function selectAllRolePermissions() {
  if (!rolePermissionConfig.value) return;
  rolePermissionForm.allDataScope = true;
  rolePermissionForm.menuIds = flattenPermissionIds(
    rolePermissionConfig.value.menuTree,
    true,
  );
  await nextTick();
  rolePermissionTreeRef.value?.setCheckedKeys(
    rolePermissionForm.menuIds,
    false,
  );
}

function clearRolePermissions() {
  rolePermissionForm.allDataScope = false;
  rolePermissionForm.menuIds = [];
  rolePermissionTreeRef.value?.setCheckedKeys([], false);
}

async function saveRolePermissions() {
  captureRolePermissionMenus();
  const affected = rolePermissionConfig.value?.affectedAccountCount || 0;
  await ElMessageBox.confirm(
    `此配置由全部广告主账号共享，将影响 ${affected} 个账号并注销其现有会话。确认保存？`,
    '保存账号角色权限',
    {
      confirmButtonText: '保存并使会话失效',
      type: 'warning',
    },
  );
  rolePermissionSaving.value = true;
  try {
    rolePermissionConfig.value = await updateAdvertiserRolePermissions({
      allDataScope: rolePermissionForm.allDataScope,
      menuIds: rolePermissionForm.menuIds,
    });
    ElMessage.success('广告主账号角色权限已更新，请相关账号重新登录');
    rolePermissionOpen.value = false;
  } finally {
    rolePermissionSaving.value = false;
  }
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listAdvertisers({
      advertiserId: query.advertiserId || undefined,
      current: query.current,
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

function search() {
  resetPage(query);
  void load();
}

function edit(row?: AdvertiserV2) {
  editing.value = row;
  Object.assign(form, {
    advertiserName: row?.advertiserName || '',
    bidUrl: row?.bidUrl || '',
    credentialConfigKey: row?.credentialConfigKey || '',
    macroParams: row?.macroParams || '',
    password: '',
    upstreamProtocol: row?.upstreamProtocol || '',
    username: '',
  });
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const editingId = editing.value?.id;
    const createPayload = {
      bidUrl: form.bidUrl,
      credentialConfigKey: form.credentialConfigKey || undefined,
      macroParams: form.macroParams || undefined,
      name: form.advertiserName,
      upstreamProtocol: form.upstreamProtocol as OcpxUpstreamProtocol,
    };
    await (editingId
      ? updateAdvertiserV2(editingId, createPayload)
      : createAdvertiserV2({
          ...createPayload,
          password: form.password,
          username: form.username.trim(),
        }));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    advertiserSelects.value = await advertiserOptions();
    await load();
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, {
    advertiserName: '',
    bidUrl: '',
    credentialConfigKey: '',
    macroParams: '',
    password: '',
    upstreamProtocol: '',
    username: '',
  });
}

function recharge(row: AdvertiserV2) {
  rechargeId.value = row.id;
  Object.assign(rechargeForm, {
    amount: '',
    remark: '',
    requestId: createClientRequestId(),
  });
  rechargeOpen.value = true;
  void nextTick(() => rechargeFormRef.value?.clearValidate());
}

function handleRechargeClose() {
  rechargeId.value = '';
  rechargeFormRef.value?.resetFields();
  Object.assign(rechargeForm, { amount: '', remark: '', requestId: '' });
}

async function submitRecharge() {
  if (!(await rechargeFormRef.value?.validate().catch(() => false))) return;
  rechargeLoading.value = true;
  try {
    await rechargeAdvertiser(rechargeId.value, {
      amount: Number(rechargeForm.amount),
      remark: rechargeForm.remark.trim() || undefined,
      requestId: rechargeForm.requestId,
    });
    ElMessage.success('充值成功');
    rechargeOpen.value = false;
    await load();
  } finally {
    rechargeLoading.value = false;
  }
}

function deduct(row: AdvertiserV2) {
  deductTarget.value = row;
  Object.assign(deductForm, {
    amount: '',
    remark: '',
    requestId: createClientRequestId(),
  });
  deductOpen.value = true;
  void nextTick(() => deductFormRef.value?.clearValidate());
}

function handleDeductClose() {
  deductTarget.value = undefined;
  deductFormRef.value?.resetFields();
  Object.assign(deductForm, { amount: '', remark: '', requestId: '' });
}

async function submitDeduct() {
  if (!(await deductFormRef.value?.validate().catch(() => false))) return;
  const target = deductTarget.value;
  if (!target) return;
  const amount = Number(deductForm.amount);
  await ElMessageBox.confirm(
    `确认从“${target.advertiserName}”余额中扣减 ¥${amount.toFixed(2)}？该操作会写入不可变账务流水。`,
    '确认扣减余额',
    {
      confirmButtonText: '确认扣减',
      type: 'warning',
    },
  );
  deductLoading.value = true;
  try {
    const result = await deductAdvertiser(target.id, {
      amount,
      remark: deductForm.remark.trim(),
      requestId: deductForm.requestId,
    });
    ElMessage.success(
      `扣减成功，账后余额 ¥${Number(result.balanceAfter).toFixed(2)}`,
    );
    deductOpen.value = false;
    await load();
  } finally {
    deductLoading.value = false;
  }
}

async function loadLedger() {
  if (!ledgerTarget.value) return;
  ledgerLoading.value = true;
  try {
    const result = await listBillingEntries({
      advertiserId: ledgerTarget.value.id,
      businessKey: ledgerQuery.businessKey || undefined,
      current: ledgerQuery.current,
      endTime: ledgerQuery.range[1] || undefined,
      entryType: ledgerQuery.entryType || undefined,
      size: ledgerQuery.size,
      startTime: ledgerQuery.range[0] || undefined,
      status: ledgerQuery.status || undefined,
    });
    ledgerRows.value = result.records;
    ledgerTotal.value = result.total;
  } finally {
    ledgerLoading.value = false;
  }
}

async function openLedger(row: AdvertiserV2) {
  ledgerTarget.value = row;
  Object.assign(ledgerQuery, {
    businessKey: '',
    current: 1,
    entryType: '',
    range: [],
    size: 30,
    status: '',
  });
  ledgerOpen.value = true;
  await loadLedger();
}

function searchLedger() {
  ledgerQuery.current = 1;
  void loadLedger();
}

function signedAmount(row: BillingEntry) {
  const amount = Number(row.amount || 0).toFixed(2);
  return row.entryType === 'RECHARGE' ? `+¥${amount}` : `-¥${amount}`;
}

async function remove(row: AdvertiserV2) {
  await ElMessageBox.confirm(
    '仅无余额、无账务流水、无事件事实且无计费配置审计的广告主可以删除。删除会同时移除其创意配置、空账户、推广链接、监测任务、扣量规则并注销绑定账号；已上传素材和共享媒体不会删除。确认继续？',
    '删除广告主',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deleteAdvertiserV2(row.id);
    ElMessage.success('删除成功');
    advertiserSelects.value = await advertiserOptions();
    if (query.advertiserId === row.id) query.advertiserId = '';
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  advertiserSelects.value = await advertiserOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="广告主管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.advertiserId"
          aria-label="按广告主查询"
          clearable
          filterable
          placeholder="请选择广告主"
        >
          <el-option
            v-for="item in advertiserSelects"
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
          v-access:code="'ocpx:advertiser:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
        >
          新增广告主
        </el-button>
        <el-button
          v-access:code="'ocpx:advertiser:role:manage'"
          :disabled="loading || saving || Boolean(deletingId)"
          @click="openRolePermissions"
        >
          账号角色权限
        </el-button>
        <el-button
          v-access:code="'ocpx:protocol-config:manage'"
          :disabled="loading || saving || Boolean(deletingId)"
          @click="openProtocolConfig"
        >
          协议与域名
        </el-button>
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
          v-if="hasColumn('protocol')"
          label="上游协议"
          min-width="130"
        >
          <template #default="{ row }">{{
            row.upstreamProtocol
              ? protocolLabels[row.upstreamProtocol as OcpxUpstreamProtocol]
              : '未配置'
          }}</template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('id')"
          label="广告主编号"
          min-width="170"
        >
          <template #default="{ row }"><LongText :value="row.id" /></template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('name')"
          label="名称"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('username')"
          label="用户名"
          min-width="140"
        >
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('balance')"
          label="余额"
          min-width="110"
        >
          <template #default="{ row }">{{
            row.balance == null ? '—' : `¥${Number(row.balance).toFixed(2)}`
          }}</template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('credential')"
          label="凭据引用"
          min-width="180"
        >
          <template #default="{ row }"
            ><LongText :value="row.credentialConfigKey"
          /></template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('bidUrl')"
          label="请求地址"
          min-width="240"
        >
          <template #default="{ row }"
            ><LongText
              :value="appendAdvertiserMacroParams(row.bidUrl, row.macroParams)"
          /></template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('macro')"
          label="宏参数"
          min-width="220"
        >
          <template #default="{ row }"
            ><LongText :value="row.macroParams"
          /></template>
        </el-table-column>
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
          v-if="canOperate"
          fixed="right"
          label="操作"
          width="285"
        >
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:advertiser:edit'"
              :disabled="saving || accountActionLoading || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-access:code="'ocpx:advertiser:recharge'"
              :disabled="saving || accountActionLoading || Boolean(deletingId)"
              link
              type="primary"
              @click="recharge(row)"
              >充值</el-button
            >
            <el-button
              v-access:code="'ocpx:advertiser:deduct'"
              :disabled="
                saving ||
                accountActionLoading ||
                Boolean(deletingId) ||
                Number(row.balance || 0) <= 0
              "
              link
              :title="
                Number(row.balance || 0) <= 0
                  ? '余额为 0 时不可扣减'
                  : '扣减广告主余额'
              "
              type="warning"
              @click="deduct(row)"
              >扣减</el-button
            >
            <el-button
              v-if="canViewBilling"
              v-access:code="'ocpx:billing-entry:view'"
              :disabled="saving || accountActionLoading || Boolean(deletingId)"
              link
              type="primary"
              @click="openLedger(row)"
              >流水</el-button
            >
            <el-button
              v-access:code="'ocpx:advertiser:delete'"
              :disabled="saving || accountActionLoading || Boolean(deletingId)"
              :loading="deletingId === row.id"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
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
      v-model="dialogOpen"
      :close-on-click-modal="false"
      :title="editing ? '编辑广告主' : '新增广告主'"
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
        <el-form-item label="广告主名称" prop="advertiserName"
          ><el-input
            v-model="form.advertiserName"
            placeholder="请输入广告主名称"
        /></el-form-item>
        <el-form-item label="上游协议" prop="upstreamProtocol">
          <el-select
            v-model="form.upstreamProtocol"
            aria-label="上游协议"
            placeholder="请选择上游协议"
          >
            <el-option
              v-for="item in protocolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="
            form.upstreamProtocol &&
            form.upstreamProtocol !== 'TEMPLATE_GET' &&
            form.upstreamProtocol !== 'PHOENIX'
          "
          :closable="false"
          class="field-alert"
          show-icon
          title="AERIM、微风互动按推广链接转发，一个广告主可以配置多个产品链接；引力使用一条 Android+IOS 链接；人人视频必须为每个产品分别创建 Android、IOS 完整点击模板，固定 userId 写入模板，系统不保存其 Bearer Token。"
          type="info"
        />
        <el-alert
          v-if="form.upstreamProtocol === 'PHOENIX'"
          :closable="false"
          class="field-alert"
          show-icon
          title="凤凰新闻使用 api.feather.ifeng.com 的 /d/imp 曝光和 /d/cli 点击协议；推广链接填写实际商务分配的 cid/gid/aid/mid/channel 固定参数及对应平台宏，系统自动生成加密 Callback，不填写广告主凭据或 bidUrl。"
          type="info"
        />
        <el-form-item
          v-if="form.upstreamProtocol === 'TEMPLATE_GET'"
          label="请求地址"
          prop="bidUrl"
          ><el-input
            v-model="form.bidUrl"
            autosize
            placeholder="请输入请求地址"
            type="textarea"
        /></el-form-item>
        <el-form-item
          v-if="showCredentialFields"
          label="凭据引用"
          prop="credentialConfigKey"
          ><el-input
            v-model="form.credentialConfigKey"
            placeholder="请输入凭据引用"
        /></el-form-item>
        <el-form-item
          v-if="showCredentialFields"
          label="宏参数"
          prop="macroParams"
        >
          <el-input
            v-model="form.macroParams"
            autosize
            placeholder="请输入宏参数"
            type="textarea"
          />
        </el-form-item>
        <template v-if="!editing">
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              maxlength="100"
              placeholder="请输入登录用户名"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              autocomplete="new-password"
              maxlength="32"
              placeholder="请输入登录密码"
              show-password
              type="password"
            />
            <div class="field-hint">{{ PASSWORD_COMPLEXITY_HINT }}</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        >
        <el-button :loading="saving" type="primary" @click="save"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="protocolConfigOpen"
      :close-on-click-modal="false"
      title="上游协议与允许域名"
      width="760px"
    >
      <div v-loading="protocolConfigLoading" class="protocol-config-dialog">
        <el-alert
          :closable="false"
          show-icon
          title="仅填写精确域名，不含协议、端口、路径、通配符或 IP。保存后由 Engine 动态刷新，空白名单表示拒绝该协议全部出网。"
          type="warning"
        />
        <section
          v-for="protocol in [
            'AERIM',
            'WEIFENG',
            'GRAVITY',
            'RENREN_VIDEO',
            'PHOENIX',
          ] as const"
          :key="protocol"
          class="protocol-host-section"
        >
          <div class="protocol-host-header">
            <div>
              <strong>{{ protocolLabels[protocol] }}</strong>
              <p>精确匹配上游点击监测地址的域名。</p>
            </div>
            <el-button
              :disabled="Boolean(protocolConfigSaving)"
              link
              type="primary"
              @click="addProtocolHost(protocol)"
              >添加域名</el-button
            >
          </div>
          <div
            v-if="protocolConfigs[protocol].length > 0"
            class="protocol-host-list"
          >
            <div
              v-for="(_host, index) in protocolConfigs[protocol]"
              :key="`${protocol}-${index}`"
              class="protocol-host-row"
            >
              <el-input
                v-model="protocolConfigs[protocol][index]"
                :aria-label="`${protocolLabels[protocol]} 允许域名 ${index + 1}`"
                maxlength="253"
                placeholder="例如：track.partner.example"
              />
              <el-button
                :aria-label="`删除 ${protocolLabels[protocol]} 允许域名 ${index + 1}`"
                :disabled="Boolean(protocolConfigSaving)"
                link
                type="danger"
                @click="removeProtocolHost(protocol, index)"
                >删除</el-button
              >
            </div>
          </div>
          <el-empty
            v-else
            :image-size="48"
            description="当前为空：该协议禁止出网"
          />
          <div class="protocol-host-footer">
            <el-button
              :loading="protocolConfigSaving === protocol"
              type="primary"
              @click="saveProtocolHosts(protocol)"
              >保存 {{ protocolLabels[protocol] }}</el-button
            >
          </div>
        </section>
      </div>
    </el-dialog>

    <el-dialog
      v-model="rolePermissionOpen"
      :close-on-click-modal="false"
      title="广告主账号角色权限"
      width="760px"
    >
      <div v-loading="rolePermissionLoading" class="role-permission-dialog">
        <el-alert
          :closable="false"
          show-icon
          :title="`共享角色 ${rolePermissionConfig?.roleName || '广告主'}（${rolePermissionConfig?.roleCode || 'ROLE_ADVERTISER'}）会影响 ${rolePermissionConfig?.affectedAccountCount || 0} 个广告主账号。`"
          type="warning"
        />
        <div class="role-permission-scope">
          <div>
            <strong>全量 OCPX 数据范围</strong>
            <p>开启后，所选权限可访问全部广告主及共享媒体、扣量数据。</p>
          </div>
          <el-switch
            :model-value="rolePermissionForm.allDataScope"
            aria-label="全量 OCPX 数据范围"
            @change="changeAllDataScope(Boolean($event))"
          />
        </div>
        <el-alert
          v-if="rolePermissionForm.allDataScope"
          :closable="false"
          show-icon
          title="全量范围已开启：获得对应删除权限的广告主账号可以级联删除任意广告主。"
          type="error"
        />
        <div class="role-permission-toolbar">
          <strong>OCPX 菜单与操作权限</strong>
          <div>
            <el-button
              :disabled="rolePermissionLoading || rolePermissionSaving"
              link
              type="primary"
              @click="selectAllRolePermissions"
              >全选</el-button
            >
            <el-button
              :disabled="rolePermissionLoading || rolePermissionSaving"
              link
              @click="clearRolePermissions"
              >清空</el-button
            >
          </div>
        </div>
        <el-tree
          ref="rolePermissionTreeRef"
          check-on-click-node
          check-strictly
          :data="rolePermissionTree"
          default-expand-all
          node-key="id"
          :props="{ children: 'children', disabled: 'disabled', label: 'name' }"
          show-checkbox
          @check="captureRolePermissionMenus"
        >
          <template #default="{ data }">
            <span>{{ data.name }}</span>
            <span v-if="data.requiresAllDataScope" class="permission-scope-tag">
              需全量范围
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button
          :disabled="rolePermissionSaving"
          @click="rolePermissionOpen = false"
          >取消</el-button
        >
        <el-button
          :disabled="rolePermissionLoading"
          :loading="rolePermissionSaving"
          type="primary"
          @click="saveRolePermissions"
          >保存权限</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="rechargeOpen"
      :close-on-click-modal="false"
      title="广告主充值"
      width="460px"
      @close="handleRechargeClose"
    >
      <el-form
        ref="rechargeFormRef"
        :model="rechargeForm"
        :rules="rechargeRules"
        label-width="100px"
      >
        <el-form-item label="充值金额" prop="amount"
          ><el-input v-model="rechargeForm.amount" inputmode="decimal"
        /></el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="rechargeForm.remark"
            maxlength="255"
            placeholder="选填"
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="rechargeLoading" @click="rechargeOpen = false"
          >取消</el-button
        >
        <el-button
          :loading="rechargeLoading"
          type="primary"
          @click="submitRecharge"
          >确定</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="deductOpen"
      :close-on-click-modal="false"
      title="扣减广告主余额"
      width="480px"
      @close="handleDeductClose"
    >
      <el-alert
        :closable="false"
        class="deduct-balance-alert"
        show-icon
        :title="`${deductTarget?.advertiserName || '广告主'} · 当前余额 ¥${Number(deductTarget?.balance || 0).toFixed(2)}`"
        type="warning"
      />
      <el-form
        ref="deductFormRef"
        :model="deductForm"
        :rules="deductRules"
        label-width="100px"
      >
        <el-form-item label="扣减金额" prop="amount">
          <el-input
            v-model="deductForm.amount"
            aria-label="扣减金额"
            inputmode="decimal"
            placeholder="请输入不超过当前余额的金额"
          />
        </el-form-item>
        <el-form-item label="扣减原因" prop="remark">
          <el-input
            v-model="deductForm.remark"
            maxlength="255"
            placeholder="必填，例如退款、线下调账"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="deductLoading" @click="deductOpen = false"
          >取消</el-button
        >
        <el-button :loading="deductLoading" type="danger" @click="submitDeduct"
          >确认扣减</el-button
        >
      </template>
    </el-dialog>

    <el-drawer
      v-model="ledgerOpen"
      :title="`${ledgerTarget?.advertiserName || '广告主'} · 账务流水`"
      size="78%"
    >
      <div class="ledger-query">
        <el-select
          v-model="ledgerQuery.entryType"
          clearable
          placeholder="流水类型"
        >
          <el-option label="充值" value="RECHARGE" />
          <el-option label="人工扣减" value="MANUAL_DEBIT" />
          <el-option label="点击消费" value="CLICK_CHARGE" />
          <el-option label="转化消费" value="CONVERSION_CHARGE" />
        </el-select>
        <el-select v-model="ledgerQuery.status" clearable placeholder="状态">
          <el-option label="已入账" value="POSTED" />
          <el-option label="余额不足拒绝" value="REJECTED" />
        </el-select>
        <el-input
          v-model="ledgerQuery.businessKey"
          clearable
          placeholder="业务键"
          @keyup.enter="searchLedger"
        />
        <el-date-picker
          v-model="ledgerQuery.range"
          end-placeholder="结束时间"
          range-separator="至"
          start-placeholder="开始时间"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
        <el-button
          :disabled="ledgerLoading"
          type="primary"
          @click="searchLedger"
        >
          查询
        </el-button>
      </div>
      <el-table
        v-loading="ledgerLoading"
        :data="ledgerRows"
        max-height="620"
        size="small"
      >
        <el-table-column label="时间" min-width="170" prop="occurredAt" />
        <el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            {{ billingTypeLabels[row.entryType] || row.entryType }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'POSTED' ? 'success' : 'danger'">
              {{ row.status === 'POSTED' ? '已入账' : '余额不足拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="110">
          <template #default="{ row }">
            <span
              :class="
                row.entryType === 'RECHARGE' ? 'amount-credit' : 'amount-debit'
              "
            >
              {{ signedAmount(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="账前余额" min-width="110">
          <template #default="{ row }"
            >¥{{ Number(row.balanceBefore).toFixed(2) }}</template
          >
        </el-table-column>
        <el-table-column label="账后余额" min-width="110">
          <template #default="{ row }"
            >¥{{ Number(row.balanceAfter).toFixed(2) }}</template
          >
        </el-table-column>
        <el-table-column
          label="监测任务"
          min-width="150"
          prop="monitorTaskName"
        />
        <el-table-column label="业务键" min-width="210">
          <template #default="{ row }"
            ><LongText :value="row.businessKey"
          /></template>
        </el-table-column>
        <el-table-column label="原因 / 备注" min-width="180">
          <template #default="{ row }">
            <LongText :value="row.remark || row.reasonCode" />
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="ledgerQuery.current"
        v-model:page-size="ledgerQuery.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="ledgerTotal"
        @change="loadLedger"
      />
    </el-drawer>
  </Page>
</template>

<style scoped>
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.field-alert {
  margin-bottom: 18px;
}

.protocol-config-dialog {
  display: grid;
  gap: 18px;
}

.protocol-host-section {
  padding: 16px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.protocol-host-header,
.protocol-host-row,
.protocol-host-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.protocol-host-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.protocol-host-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.protocol-host-row :deep(.el-input) {
  flex: 1;
}

.protocol-host-footer {
  justify-content: flex-end;
  margin-top: 14px;
}

.role-permission-dialog {
  display: grid;
  gap: 16px;
}

.role-permission-scope,
.role-permission-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.role-permission-scope p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role-permission-dialog :deep(.el-tree) {
  max-height: 360px;
  padding: 12px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.permission-scope-tag {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.deduct-balance-alert {
  margin-bottom: 18px;
}

.ledger-query {
  display: grid;
  grid-template-columns: 150px 150px minmax(160px, 1fr) 370px auto;
  gap: 12px;
  margin-bottom: 16px;
}

.amount-credit {
  color: var(--el-color-success);
}

.amount-debit {
  color: var(--el-color-danger);
}
</style>
