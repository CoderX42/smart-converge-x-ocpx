<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type {
  AccountEntry,
  Advertiser,
  AdvertiserAdapterCapability,
  AdvertiserDetail,
  AdvertiserDocument,
} from '#/api/adx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import {
  Document,
  EditPen,
  Key,
  Plus,
  Refresh,
  Search,
  SwitchButton,
  Wallet,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeAdvertiserStatus,
  createAdvertiser,
  downloadAdvertiserDocument,
  getAdvertiserAdapterCapabilities,
  getAdvertiserDetail,
  getAdvertiserDocuments,
  getAdvertiserLedger,
  getAdvertisers,
  rechargeAdvertiser,
  retireAdvertiserDocument,
  rotateAdvertiserCredential,
  secondaryAuthenticate,
  setCurrentAdvertiserDocument,
  updateAdvertiser,
  uploadAdvertiserDocument,
} from '#/api/adx';

import {
  buildAdvertiserCreatePayload,
  buildAdvertiserUpdatePayload,
  editorSnapshot,
  resolveAdvertiserEmptyState,
} from './advertiser-model';

defineOptions({ name: 'AdxAdvertiserManagement' });

const { hasAccessByCodes } = useAccess();
const canView = computed(() => hasAccessByCodes(['adx:advertiser:view']));
const canAdd = computed(() => hasAccessByCodes(['adx:advertiser:add']));
const canEdit = computed(() => hasAccessByCodes(['adx:advertiser:edit']));
const canStatus = computed(() => hasAccessByCodes(['adx:advertiser:status']));
const canCredential = computed(() =>
  hasAccessByCodes(['adx:advertiser:credential']),
);
const canViewDocuments = computed(() =>
  hasAccessByCodes(['adx:advertiser:document:view']),
);
const canEditDocuments = computed(() =>
  hasAccessByCodes(['adx:advertiser:document:edit']),
);
const canRecharge = computed(() => hasAccessByCodes(['adx:account:recharge']));
const canViewLedger = computed(() => hasAccessByCodes(['adx:account:read']));

const filters = reactive({
  adapterKey: '',
  documentConfigured: '' as '' | 'false' | 'true',
  keyword: '',
  status: '',
});
const current = ref(1);
const size = ref(20);
const total = ref(0);
const rows = ref<Advertiser[]>([]);
const capabilities = ref<AdvertiserAdapterCapability[]>([]);
const loading = ref(false);
const loadFailed = ref(false);

const filtersActive = computed(() => Object.values(filters).some(Boolean));
const emptyState = computed(() =>
  resolveAdvertiserEmptyState({
    canView: canView.value,
    error: loadFailed.value,
    filtersActive: filtersActive.value,
    total: total.value,
  }),
);

const editorOpen = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
const conflictMessage = ref('');
const editing = ref<AdvertiserDetail>();
const idempotencyKey = ref('');
const savedSnapshot = ref('');
const formRef = ref<FormInstance>();
const form = reactive({
  account: '',
  adapterKey: '',
  advertiserName: '',
  bidUrl: '',
  code: '',
  credentialInput: '',
  macroParams: '',
  password: '',
  upstreamHostAllowlist: '',
});

const rules: FormRules = {
  account: [
    { max: 100, message: '账号不能超过 100 个字符', trigger: 'blur' },
    { message: '请输入登录账号', required: true, trigger: 'blur' },
  ],
  adapterKey: [{ message: '请选择上游 Adapter', required: true }],
  advertiserName: [
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
    { message: '请输入广告主名称', required: true, trigger: 'blur' },
  ],
  bidUrl: [{ message: '请输入上游 HTTPS 地址', required: true }],
  code: [
    {
      message: '编码为 2–32 位小写字母、数字、下划线或连字符',
      pattern: /^[a-z0-9_-]{2,32}$/,
      trigger: 'blur',
    },
    { message: '请输入广告主编码', required: true, trigger: 'blur' },
  ],
  password: [
    { message: '密码至少 8 位', min: 8, trigger: 'blur' },
    { message: '请输入初始登录密码', required: true, trigger: 'blur' },
  ],
  upstreamHostAllowlist: [
    { message: '请输入上游域名白名单', required: true, trigger: 'blur' },
  ],
};

const selectedCapability = computed(() =>
  capabilities.value.find((item) => item.adapterKey === form.adapterKey),
);

const credentialOpen = ref(false);
const credentialSaving = ref(false);
const credentialTarget = ref<AdvertiserDetail>();
const credentialForm = reactive({
  confirmation: '',
  credentialInput: '',
  password: '',
  reason: '',
});

const documentsOpen = ref(false);
const documentsLoading = ref(false);
const documentTarget = ref<AdvertiserDetail>();
const documents = ref<AdvertiserDocument[]>([]);
const documentUploading = ref(false);
const documentFile = ref<File>();
const documentForm = reactive({ documentType: 'PROTOCOL', summary: '' });

const rechargeOpen = ref(false);
const rechargeSaving = ref(false);
const rechargeTarget = ref<Advertiser>();
const rechargeAmount = ref<number>();
const rechargeRequestId = ref('');

const ledgerOpen = ref(false);
const ledgerLoading = ref(false);
const ledgerTarget = ref<Advertiser>();
const ledgerRows = ref<AccountEntry[]>([]);

async function loadCapabilities() {
  if (!canView.value) return;
  capabilities.value = await getAdvertiserAdapterCapabilities();
}

async function load() {
  if (!canView.value) return;
  loading.value = true;
  loadFailed.value = false;
  try {
    const result = await getAdvertisers({
      adapterKey: filters.adapterKey || undefined,
      current: current.value,
      documentConfigured:
        filters.documentConfigured === ''
          ? undefined
          : filters.documentConfigured === 'true',
      keyword: filters.keyword.trim() || undefined,
      size: size.value,
      status: filters.status || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch {
    rows.value = [];
    total.value = 0;
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

async function search() {
  current.value = 1;
  await load();
}

async function resetFilters() {
  Object.assign(filters, {
    adapterKey: '',
    documentConfigured: '',
    keyword: '',
    status: '',
  });
  await search();
}

function resetEditor() {
  editing.value = undefined;
  conflictMessage.value = '';
  Object.assign(form, {
    account: '',
    adapterKey: capabilities.value[0]?.adapterKey || '',
    advertiserName: '',
    bidUrl: '',
    code: '',
    credentialInput: '',
    macroParams: '',
    password: '',
    upstreamHostAllowlist: '',
  });
}

function openCreate() {
  resetEditor();
  idempotencyKey.value = crypto.randomUUID();
  savedSnapshot.value = editorSnapshot(form);
  editorOpen.value = true;
}

function clearEditorSensitive() {
  form.password = '';
  form.credentialInput = '';
}

async function openEdit(row: Advertiser) {
  editorOpen.value = true;
  editorLoading.value = true;
  conflictMessage.value = '';
  try {
    const detail = await getAdvertiserDetail(row.id);
    editing.value = detail;
    Object.assign(form, {
      account: detail.accountMasked,
      adapterKey: detail.adapterKey,
      advertiserName: detail.advertiserName,
      bidUrl: detail.bidUrl || '',
      code: detail.code,
      credentialInput: '',
      macroParams: detail.macroParams || '',
      password: '',
      upstreamHostAllowlist: detail.upstreamHostAllowlist || '',
    });
    savedSnapshot.value = editorSnapshot(form);
  } catch {
    editorOpen.value = false;
  } finally {
    editorLoading.value = false;
  }
}

async function beforeEditorClose(done: () => void) {
  if (editorSnapshot(form) === savedSnapshot.value || saving.value) {
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

async function saveAdvertiser() {
  if (!(await formRef.value?.validate())) return;
  saving.value = true;
  conflictMessage.value = '';
  try {
    await (editing.value
      ? updateAdvertiser(
          editing.value.id,
          buildAdvertiserUpdatePayload(form, editing.value),
        )
      : createAdvertiser(
          buildAdvertiserCreatePayload(form),
          idempotencyKey.value,
        ));
    savedSnapshot.value = editorSnapshot(form);
    editorOpen.value = false;
    ElMessage.success(
      editing.value ? '广告主配置已更新' : '广告主已创建并保持禁用',
    );
    await load();
  } catch (error: any) {
    if (
      error?.response?.status === 409 ||
      error?.response?.data?.code === 409
    ) {
      conflictMessage.value = '数据已被其他操作修改，请刷新详情后重新提交。';
    }
  } finally {
    saving.value = false;
  }
}

async function changeStatus(row: Advertiser) {
  const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `${nextStatus === 'ACTIVE' ? '启用' : '禁用'}不会改写已发布快照；相关路由会显示待重发。请输入原因。`,
      `${nextStatus === 'ACTIVE' ? '启用' : '禁用'}广告主`,
      {
        confirmButtonText: '确认变更',
        inputPattern: /\S+/,
        inputPlaceholder: '必填，最多 255 字',
        inputValidator: (value) =>
          value.length <= 255 || '原因不能超过 255 个字符',
        type: 'warning',
      },
    );
    await changeAdvertiserStatus(row.id, {
      confirmed: true,
      expectedVersion: row.configVersion,
      reason,
      status: nextStatus,
    });
    ElMessage.success('广告主状态已更新');
    await load();
  } catch {
    // Cancellation and request errors already have dedicated feedback.
  }
}

async function openCredential(row: Advertiser) {
  credentialTarget.value = await getAdvertiserDetail(row.id);
  Object.assign(credentialForm, {
    confirmation: '',
    credentialInput: '',
    password: '',
    reason: '',
  });
  credentialOpen.value = true;
}

function clearCredentialSensitive() {
  credentialForm.password = '';
  credentialForm.credentialInput = '';
  credentialForm.confirmation = '';
}

async function rotateCredential() {
  const target = credentialTarget.value;
  if (!target) return;
  if (credentialForm.credentialInput.length < 16) {
    ElMessage.warning('新凭据至少 16 位');
    return;
  }
  if (credentialForm.credentialInput !== credentialForm.confirmation) {
    ElMessage.warning('两次输入的新凭据不一致');
    return;
  }
  if (!credentialForm.password || !credentialForm.reason.trim()) {
    ElMessage.warning('请输入当前登录密码和轮换原因');
    return;
  }
  credentialSaving.value = true;
  try {
    await secondaryAuthenticate(credentialForm.password);
    const result = await rotateAdvertiserCredential(target.id, {
      credentialInput: credentialForm.credentialInput,
      expectedCredentialRevision: target.credentialRevision,
      reason: credentialForm.reason.trim(),
    });
    credentialOpen.value = false;
    ElMessage.success(
      `凭据已轮换至 revision ${result.revision}，Secret 不会再次显示`,
    );
    await load();
  } finally {
    credentialForm.password = '';
    credentialForm.credentialInput = '';
    credentialForm.confirmation = '';
    credentialSaving.value = false;
  }
}

async function openDocuments(row: Advertiser) {
  documentsOpen.value = true;
  documentsLoading.value = true;
  try {
    documentTarget.value = await getAdvertiserDetail(row.id);
    documents.value = await getAdvertiserDocuments(row.id);
  } finally {
    documentsLoading.value = false;
  }
}

function selectDocument(uploadFile: UploadFile) {
  documentFile.value = uploadFile.raw;
}

async function uploadDocument() {
  if (!documentTarget.value || !documentFile.value) {
    ElMessage.warning('请选择资料文件');
    return;
  }
  documentUploading.value = true;
  try {
    await uploadAdvertiserDocument(
      documentTarget.value.id,
      documentForm.documentType,
      documentForm.summary.trim(),
      documentFile.value,
    );
    documentFile.value = undefined;
    documentForm.summary = '';
    documents.value = await getAdvertiserDocuments(documentTarget.value.id);
    ElMessage.success('资料新版本已上传并通过本地安全检查');
  } finally {
    documentUploading.value = false;
  }
}

async function setCurrentDocument(document: AdvertiserDocument) {
  if (!documentTarget.value) return;
  const latest = await getAdvertiserDetail(documentTarget.value.id);
  await setCurrentAdvertiserDocument(
    latest.id,
    document.id,
    latest.configVersion,
  );
  documentTarget.value = await getAdvertiserDetail(latest.id);
  documents.value = await getAdvertiserDocuments(latest.id);
  ElMessage.success('当前资料版本已切换，依赖路由已标记待重发');
  await load();
}

async function retireDocument(document: AdvertiserDocument) {
  if (!documentTarget.value) return;
  const { value: reason } = await ElMessageBox.prompt(
    '停用后仍保留版本和审计，不会删除存储对象。',
    '停用资料版本',
    { inputPattern: /\S+/, inputPlaceholder: '请输入原因', type: 'warning' },
  );
  await retireAdvertiserDocument(documentTarget.value.id, document.id, reason);
  documents.value = await getAdvertiserDocuments(documentTarget.value.id);
  ElMessage.success('资料版本已停用');
}

async function downloadDocument(document: AdvertiserDocument) {
  if (!documentTarget.value) return;
  const blob = await downloadAdvertiserDocument(
    documentTarget.value.id,
    document.id,
  );
  const objectUrl = URL.createObjectURL(blob);
  const link = window.document.createElement('a');
  link.href = objectUrl;
  link.download = document.fileName;
  link.click();
  URL.revokeObjectURL(objectUrl);
}

function openRecharge(row: Advertiser) {
  rechargeTarget.value = row;
  rechargeAmount.value = undefined;
  rechargeRequestId.value = crypto.randomUUID();
  rechargeOpen.value = true;
}

async function recharge() {
  if (
    !rechargeTarget.value ||
    !rechargeAmount.value ||
    rechargeAmount.value <= 0
  )
    return;
  rechargeSaving.value = true;
  try {
    await rechargeAdvertiser(
      rechargeTarget.value.id,
      rechargeAmount.value,
      rechargeRequestId.value,
    );
    rechargeOpen.value = false;
    ElMessage.success('充值成功，账务流水已生成');
    await load();
  } finally {
    rechargeSaving.value = false;
  }
}

async function openLedger(row: Advertiser) {
  ledgerTarget.value = row;
  ledgerOpen.value = true;
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

function formatMoney(value?: number) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
  })}`;
}

function formatBytes(value: number) {
  return value < 1024 * 1024
    ? `${Math.ceil(value / 1024)} KB`
    : `${(value / 1024 / 1024).toFixed(1)} MB`;
}

onMounted(async () => {
  if (!canView.value) return;
  await Promise.all([loadCapabilities(), load()]);
});
</script>

<template>
  <div class="advertiser-page">
    <el-result
      v-if="emptyState === 'NO_PERMISSION'"
      icon="warning"
      sub-title="当前账号未获得广告主查看权限，也不会发起列表请求。"
      title="无权查看广告主"
    />

    <template v-else>
      <header class="page-heading">
        <div>
          <p class="eyebrow">ADX · 上游接入</p>
          <h1>广告主管理</h1>
          <p class="subtitle">
            接入配置、凭据与资料独立修订；变更只标记依赖路由待重发。
          </p>
        </div>
        <el-button
          v-if="canAdd"
          :icon="Plus"
          type="primary"
          @click="openCreate"
        >
          新增广告主
        </el-button>
      </header>

      <el-card class="filter-card" shadow="never">
        <div class="filters">
          <el-input
            v-model="filters.keyword"
            clearable
            :prefix-icon="Search"
            placeholder="名称或编码"
            @keyup.enter="search"
          />
          <el-select
            v-model="filters.adapterKey"
            clearable
            placeholder="Adapter"
          >
            <el-option
              v-for="adapter in capabilities"
              :key="adapter.adapterKey"
              :label="`${adapter.adapterName} · v${adapter.version}`"
              :value="adapter.adapterKey"
            />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
          <el-select
            v-model="filters.documentConfigured"
            clearable
            placeholder="接入资料"
          >
            <el-option label="已配置" value="true" />
            <el-option label="未配置" value="false" />
          </el-select>
          <div class="filter-actions">
            <el-button :icon="Search" type="primary" @click="search"
              >查询</el-button
            >
            <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          </div>
        </div>
      </el-card>

      <el-card class="table-card" shadow="never">
        <el-table v-loading="loading" :data="rows" height="calc(100vh - 330px)">
          <el-table-column label="广告主" min-width="190">
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.advertiserName }}</strong>
                <code>{{ row.code }}</code>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Adapter" min-width="145">
            <template #default="{ row }">
              {{ row.adapterKey }}
              <span class="muted">v{{ row.adapterVersion }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="接入地址"
            min-width="230"
            prop="maskedBidUrl"
          />
          <el-table-column label="凭据" min-width="120">
            <template #default="{ row }">
              <el-tag
                :type="row.credentialConfigured ? 'success' : 'info'"
                size="small"
              >
                {{
                  row.credentialConfigured
                    ? `revision ${row.credentialRevision}`
                    : '未配置'
                }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="资料版本" min-width="105">
            <template #default="{ row }">v{{ row.documentRevision }}</template>
          </el-table-column>
          <el-table-column label="余额" min-width="130">
            <template #default="{ row }">
              <span :class="{ warning: row.balanceStatus === 'EMPTY' }">
                {{ formatMoney(row.balance) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'ACTIVE' ? 'success' : 'info'"
                size="small"
              >
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="配置版本" min-width="105">
            <template #default="{ row }">v{{ row.configVersion }}</template>
          </el-table-column>
          <el-table-column label="路由" min-width="110">
            <template #default="{ row }">
              <el-tag v-if="row.republishRequired" type="warning" size="small"
                >待重发</el-tag
              >
              <span v-else class="muted">已同步</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170" prop="updateTime" />
          <el-table-column fixed="right" label="操作" width="370">
            <template #default="{ row }">
              <el-button
                v-if="canEdit"
                :icon="EditPen"
                link
                type="primary"
                @click="openEdit(row)"
                >编辑</el-button
              >
              <el-button
                v-if="canViewDocuments"
                :icon="Document"
                link
                @click="openDocuments(row)"
                >资料</el-button
              >
              <el-button
                v-if="canCredential"
                :icon="Key"
                link
                type="warning"
                @click="openCredential(row)"
                >凭据</el-button
              >
              <el-button
                v-if="canStatus"
                :icon="SwitchButton"
                link
                @click="changeStatus(row)"
              >
                {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
              </el-button>
              <el-button
                v-if="canRecharge"
                :icon="Wallet"
                link
                type="success"
                @click="openRecharge(row)"
                >充值</el-button
              >
              <el-button v-if="canViewLedger" link @click="openLedger(row)"
                >账务</el-button
              >
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-panel">
              <template v-if="emptyState === 'ERROR'">
                <strong>列表加载失败</strong>
                <span>筛选条件已保留，可直接重试。</span>
                <el-button type="primary" @click="load">重新加载</el-button>
              </template>
              <template v-else-if="emptyState === 'FILTER_EMPTY'">
                <strong>没有符合条件的广告主</strong>
                <span>调整筛选条件或重置后再试。</span>
                <el-button @click="resetFilters">清空筛选</el-button>
              </template>
              <template v-else>
                <strong>尚未接入广告主</strong>
                <span>新建后默认禁用，完成凭据和资料检查后再启用。</span>
                <el-button v-if="canAdd" type="primary" @click="openCreate"
                  >新增广告主</el-button
                >
              </template>
            </div>
          </template>
        </el-table>
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </el-card>
    </template>

    <el-drawer
      v-model="editorOpen"
      :before-close="beforeEditorClose"
      :close-on-click-modal="false"
      :title="editing ? '编辑广告主' : '新增广告主'"
      size="620px"
      @closed="clearEditorSensitive"
    >
      <div v-loading="editorLoading">
        <el-alert
          v-if="conflictMessage"
          :closable="false"
          class="drawer-alert"
          :title="conflictMessage"
          type="warning"
        />
        <el-alert
          :closable="false"
          class="drawer-alert"
          title="普通编辑不会读取或覆盖 Secret；保存后已发布快照保持不变。"
          type="info"
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
        >
          <div class="form-grid">
            <el-form-item label="广告主名称" prop="advertiserName">
              <el-input v-model="form.advertiserName" maxlength="64" />
            </el-form-item>
            <el-form-item label="广告主编码" prop="code">
              <el-input
                v-model="form.code"
                :disabled="Boolean(editing)"
                maxlength="32"
              />
            </el-form-item>
          </div>
          <el-form-item label="上游 Adapter" prop="adapterKey">
            <el-select v-model="form.adapterKey" class="full-width">
              <el-option
                v-for="adapter in capabilities"
                :key="adapter.adapterKey"
                :label="`${adapter.adapterName} · capability v${adapter.version}`"
                :value="adapter.adapterKey"
              />
            </el-select>
            <span v-if="selectedCapability" class="field-hint">
              鉴权：{{
                selectedCapability.authTypes.join(' / ') || '未声明'
              }}；超时上限：{{ selectedCapability.maxTimeoutMs || '—' }}ms
            </span>
          </el-form-item>
          <el-form-item label="上游 HTTPS 地址" prop="bidUrl">
            <el-input
              v-model="form.bidUrl"
              placeholder="https://partner.example/callback"
            />
          </el-form-item>
          <el-form-item label="上游域名白名单" prop="upstreamHostAllowlist">
            <el-input
              v-model="form.upstreamHostAllowlist"
              placeholder="partner.example，多个域名用逗号分隔"
            />
          </el-form-item>
          <el-form-item label="必填宏声明">
            <el-input v-model="form.macroParams" :rows="3" type="textarea" />
          </el-form-item>
          <template v-if="!editing">
            <div class="form-grid">
              <el-form-item label="登录账号" prop="account">
                <el-input v-model="form.account" maxlength="100" />
              </el-form-item>
              <el-form-item label="初始登录密码" prop="password">
                <el-input
                  v-model="form.password"
                  show-password
                  type="password"
                />
              </el-form-item>
            </div>
            <el-form-item label="初始凭据（可选）">
              <el-input
                v-model="form.credentialInput"
                autocomplete="new-password"
                placeholder="只写；提交后不会回显"
                show-password
                type="password"
              />
            </el-form-item>
          </template>
          <el-descriptions v-else :column="2" border>
            <el-descriptions-item label="登录账号">{{
              editing.accountMasked
            }}</el-descriptions-item>
            <el-descriptions-item label="凭据">{{
              editing.credentialConfigured
                ? `已配置 · revision ${editing.credentialRevision}`
                : '未配置'
            }}</el-descriptions-item>
          </el-descriptions>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="beforeEditorClose(() => (editorOpen = false))"
          >取消</el-button
        >
        <el-button :loading="saving" type="primary" @click="saveAdvertiser"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <el-dialog
      v-model="credentialOpen"
      destroy-on-close
      title="轮换广告主凭据"
      width="520px"
      @closed="clearCredentialSensitive"
    >
      <el-alert
        :closable="false"
        class="drawer-alert"
        title="需要当前登录密码完成二次认证。新旧修订短暂重叠，Secret 不回显且不会写入操作日志。"
        type="warning"
      />
      <el-form label-position="top">
        <el-form-item label="当前登录密码" required>
          <el-input
            v-model="credentialForm.password"
            autocomplete="current-password"
            show-password
            type="password"
          />
        </el-form-item>
        <el-form-item label="新凭据" required>
          <el-input
            v-model="credentialForm.credentialInput"
            autocomplete="new-password"
            show-password
            type="password"
          />
        </el-form-item>
        <el-form-item label="再次输入新凭据" required>
          <el-input
            v-model="credentialForm.confirmation"
            autocomplete="new-password"
            show-password
            type="password"
          />
        </el-form-item>
        <el-form-item label="轮换原因" required>
          <el-input v-model="credentialForm.reason" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="credentialOpen = false">取消</el-button>
        <el-button
          :loading="credentialSaving"
          type="warning"
          @click="rotateCredential"
          >确认轮换</el-button
        >
      </template>
    </el-dialog>

    <el-drawer
      v-model="documentsOpen"
      :title="`${documentTarget?.advertiserName || ''} · 接入资料`"
      size="760px"
    >
      <div v-loading="documentsLoading">
        <el-alert
          :closable="false"
          class="drawer-alert"
          title="页面不暴露存储 objectKey 或永久 URL；下载始终经过服务端权限与对象范围检查。"
          type="info"
        />
        <el-card v-if="canEditDocuments" class="upload-card" shadow="never">
          <div class="upload-row">
            <el-select v-model="documentForm.documentType">
              <el-option label="协议说明" value="PROTOCOL" />
              <el-option label="接入指南" value="INTEGRATION_GUIDE" />
              <el-option label="事件映射" value="EVENT_MAPPING" />
              <el-option label="其他" value="OTHER" />
            </el-select>
            <el-input
              v-model="documentForm.summary"
              maxlength="500"
              placeholder="版本摘要（可选）"
            />
            <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="selectDocument"
              :show-file-list="true"
            >
              <el-button>选择文件</el-button>
            </el-upload>
            <el-button
              :loading="documentUploading"
              type="primary"
              @click="uploadDocument"
              >上传新版本</el-button
            >
          </div>
          <p class="field-hint">
            支持 PDF、JSON、TXT、DOC、DOCX，单文件不超过 10MB。
          </p>
        </el-card>
        <el-table :data="documents" border>
          <el-table-column label="资料" min-width="180">
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.fileName }}</strong
                ><span>{{ row.documentType }} · v{{ row.version }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90"
            ><template #default="{ row }">{{
              formatBytes(row.fileSize)
            }}</template></el-table-column
          >
          <el-table-column label="扫描" width="90"
            ><template #default="{ row }"
              ><el-tag
                :type="row.scanStatus === 'CLEAN' ? 'success' : 'danger'"
                size="small"
                >{{ row.scanStatus }}</el-tag
              ></template
            ></el-table-column
          >
          <el-table-column label="状态" width="100"
            ><template #default="{ row }"
              ><el-tag
                :type="
                  row.current
                    ? 'primary'
                    : row.status === 'ACTIVE'
                      ? 'info'
                      : 'warning'
                "
                size="small"
                >{{ row.current ? '当前' : row.status }}</el-tag
              ></template
            ></el-table-column
          >
          <el-table-column label="上传人" width="110" prop="createdBy" />
          <el-table-column label="操作" fixed="right" width="190">
            <template #default="{ row }">
              <el-button link @click="downloadDocument(row)">下载</el-button>
              <el-button
                v-if="
                  canEditDocuments && !row.current && row.status === 'ACTIVE'
                "
                link
                type="primary"
                @click="setCurrentDocument(row)"
                >设为当前</el-button
              >
              <el-button
                v-if="
                  canEditDocuments && !row.current && row.status === 'ACTIVE'
                "
                link
                type="danger"
                @click="retireDocument(row)"
                >停用</el-button
              >
            </template>
          </el-table-column>
          <template #empty
            ><div class="empty-panel">
              <strong>暂无接入资料</strong
              ><span>上传新版本后可设为当前资料。</span>
            </div></template
          >
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="rechargeOpen" title="广告主充值" width="440px">
      <el-form label-width="90px">
        <el-form-item label="广告主">{{
          rechargeTarget?.advertiserName
        }}</el-form-item>
        <el-form-item label="充值金额" required
          ><el-input-number v-model="rechargeAmount" :min="0.01" :precision="2"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="rechargeOpen = false">取消</el-button
        ><el-button :loading="rechargeSaving" type="primary" @click="recharge"
          >确认充值</el-button
        ></template
      >
    </el-dialog>

    <el-dialog
      v-model="ledgerOpen"
      :title="`${ledgerTarget?.advertiserName || ''} · 账务流水`"
      width="1180px"
    >
      <el-table
        v-loading="ledgerLoading"
        :data="ledgerRows"
        border
        max-height="520"
      >
        <el-table-column label="时间" min-width="170" prop="createTime" />
        <el-table-column label="模式" width="100" prop="mode" />
        <el-table-column label="状态" width="100" prop="status" />
        <el-table-column label="事件" min-width="170" prop="chargeEventId" />
        <el-table-column label="金额" width="120"
          ><template #default="{ row }">{{
            formatMoney(row.amount)
          }}</template></el-table-column
        >
        <el-table-column label="账前余额" width="130"
          ><template #default="{ row }">{{
            formatMoney(row.balanceBefore)
          }}</template></el-table-column
        >
        <el-table-column label="账后余额" width="130"
          ><template #default="{ row }">{{
            formatMoney(row.balanceAfter)
          }}</template></el-table-column
        >
        <el-table-column label="原因" min-width="160" prop="reason" />
        <el-table-column label="操作人" width="120" prop="operatorId" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.advertiser-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.page-heading h1 {
  margin: 2px 0 6px;
  font-size: 26px;
  line-height: 1.2;
  color: #172033;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #3568d4;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtitle,
.field-hint,
.muted {
  color: #7a8498;
}

.subtitle {
  margin: 0;
  font-size: 14px;
}

.filter-card,
.table-card {
  border: 1px solid #e4e9f2;
  border-radius: 12px;
}

.filter-card {
  margin-bottom: 14px;
}

.filters {
  display: grid;
  grid-template-columns:
    minmax(210px, 1.5fr) repeat(3, minmax(140px, 1fr))
    auto;
  gap: 12px;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

.table-card :deep(.el-pagination) {
  justify-content: flex-end;
  padding: 14px 18px;
  border-top: 1px solid #edf0f5;
}

.primary-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.primary-cell code,
.primary-cell span {
  font-size: 12px;
  color: #8791a5;
}

.warning {
  font-weight: 600;
  color: #d97706;
}

.empty-panel {
  display: flex;
  flex-direction: column;
  gap: 9px;
  align-items: center;
  justify-content: center;
  min-height: 190px;
  color: #8791a5;
}

.empty-panel strong {
  font-size: 16px;
  color: #344054;
}

.drawer-alert {
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.full-width {
  width: 100%;
}

.field-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
}

.upload-card {
  margin-bottom: 16px;
  background: #fafbfc;
}

.upload-row {
  display: grid;
  grid-template-columns: 130px minmax(180px, 1fr) auto auto;
  gap: 10px;
  align-items: start;
}

@media (max-width: 1100px) {
  .filters {
    grid-template-columns: 1fr 1fr;
  }

  .filter-actions {
    grid-column: 1 / -1;
  }
}
</style>
