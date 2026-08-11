<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  MediaAccountEditor,
  MediaCredentialEditor,
} from './media-account-model';

import type {
  MediaAccount,
  MediaAccountDetail,
  MediaAccountOwner,
  MediaChannelOption,
  MediaCredential,
  MediaDocumentOption,
  MediaOwnerOption,
  MediaPreflight,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import {
  Connection,
  EditPen,
  FolderDelete,
  Key,
  Plus,
  Refresh,
  Search,
  User,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveMediaAccount,
  changeMediaAccountStatus,
  changeMediaCredentialStatus,
  createMediaAccount,
  createMediaCredential,
  getMediaAccount,
  getMediaAccountOwners,
  getMediaAccounts,
  getMediaChannelOptions,
  getMediaCredentials,
  getMediaDocumentOptions,
  getMediaOwnerOptions,
  preflightMediaAccount,
  replaceMediaAccountOwners,
  restoreMediaAccount,
  rotateMediaCredential,
  secondaryAuthenticate,
  updateMediaAccount,
  updateMediaCredential,
} from '#/api/ocpx';

import {
  buildMediaAccountCreatePayload,
  buildMediaAccountUpdatePayload,
  mediaAccountEditorSnapshot,
  resetCredentialEditor,
  resolveMediaAccountEmptyState,
} from './media-account-model';

defineOptions({ name: 'OcpxMediaAccountManagement' });

const { hasAccessByCodes } = useAccess();
const canView = computed(() => hasAccessByCodes(['ocpx:media-account:view']));
const canAdd = computed(() => hasAccessByCodes(['ocpx:media-account:add']));
const canEdit = computed(() => hasAccessByCodes(['ocpx:media-account:edit']));
const canStatus = computed(() =>
  hasAccessByCodes(['ocpx:media-account:status']),
);
const canArchive = computed(() =>
  hasAccessByCodes(['ocpx:media-account:archive']),
);
const canCredentialView = computed(() =>
  hasAccessByCodes(['ocpx:media-account:credential:view']),
);
const canCredentialAdd = computed(() =>
  hasAccessByCodes(['ocpx:media-account:credential:add']),
);
const canCredentialEdit = computed(() =>
  hasAccessByCodes(['ocpx:media-account:credential:edit']),
);
const canCredentialRotate = computed(() =>
  hasAccessByCodes(['ocpx:media-account:credential:rotate']),
);
const canCredentialStatus = computed(() =>
  hasAccessByCodes(['ocpx:media-account:credential:status']),
);
const canOwnerView = computed(() =>
  hasAccessByCodes(['ocpx:media-account:owner:view']),
);
const canOwnerEdit = computed(() =>
  hasAccessByCodes(['ocpx:media-account:owner:edit']),
);
const canDebug = computed(() => hasAccessByCodes(['ocpx:media-account:debug']));

const filters = reactive({
  archived: false,
  channelId: '',
  keyword: '',
  status: '',
});
const current = ref(1);
const size = ref(20);
const total = ref(0);
const rows = ref<MediaAccount[]>([]);
const channels = ref<MediaChannelOption[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
const filtersActive = computed(
  () =>
    filters.archived ||
    Boolean(filters.channelId) ||
    Boolean(filters.keyword.trim()) ||
    Boolean(filters.status),
);
const emptyState = computed(() =>
  resolveMediaAccountEmptyState({
    canView: canView.value,
    error: loadFailed.value,
    filtersActive: filtersActive.value,
    total: total.value,
  }),
);

const editorOpen = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
const editing = ref<MediaAccountDetail>();
const documents = ref<MediaDocumentOption[]>([]);
const savedSnapshot = ref('');
const idempotencyKey = ref('');
const editorConflict = ref('');
const formRef = ref<FormInstance>();
const form = reactive<MediaAccountEditor>({
  channelId: '',
  description: '',
  documentId: '',
  name: '',
});
const rules: FormRules = {
  channelId: [
    { message: '请选择启用且在对象范围内的协议渠道', required: true },
  ],
  description: [
    { max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' },
  ],
  name: [
    { message: '请输入媒体账号名称', required: true, trigger: 'blur' },
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
  ],
};

const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<MediaAccountDetail>();
const credentials = ref<MediaCredential[]>([]);
const owners = ref<MediaAccountOwner[]>([]);
const ownerOptions = ref<MediaOwnerOption[]>([]);
const selectedOwnerIds = ref<string[]>([]);
const ownerBaseline = ref('[]');
const ownerSaving = ref(false);
const detailError = ref('');
const detailConflict = ref('');
const preflight = ref<MediaPreflight>();
const preflightLoading = ref(false);

type CredentialMode = 'CREATE' | 'EDIT' | 'ROTATE';
const credentialOpen = ref(false);
const credentialMode = ref<CredentialMode>('CREATE');
const credentialTarget = ref<MediaCredential>();
const credentialSaving = ref(false);
const credentialError = ref('');
const credentialForm = reactive<MediaCredentialEditor>({
  description: '',
  loginIdentifier: '',
  password: '',
  reason: '',
  secretInput: '',
});

async function loadOptions() {
  channels.value = await getMediaChannelOptions();
}

async function load() {
  if (!canView.value) return;
  loading.value = true;
  loadFailed.value = false;
  try {
    const result = await getMediaAccounts({
      archived: filters.archived || undefined,
      channelId: filters.channelId || undefined,
      current: current.value,
      keyword: filters.keyword.trim() || undefined,
      size: size.value,
      status: filters.archived ? undefined : filters.status || undefined,
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
    archived: false,
    channelId: '',
    keyword: '',
    status: '',
  });
  await search();
}

async function changeEditorChannel() {
  form.documentId = '';
  documents.value = [];
  if (!form.channelId) return;
  try {
    documents.value = await getMediaDocumentOptions(form.channelId);
  } catch {
    ElMessage.warning('渠道资料选项加载失败；可以先不关联资料');
  }
}

function resetEditor() {
  editing.value = undefined;
  editorConflict.value = '';
  documents.value = [];
  Object.assign(form, {
    channelId:
      channels.value.find(
        (item) => item.status === 'ACTIVE' && item.adapterAvailable,
      )?.id || '',
    description: '',
    documentId: '',
    name: '',
  });
}

async function openCreate() {
  resetEditor();
  if (!form.channelId) {
    ElMessage.warning('当前没有可操作且适配器可用的协议渠道');
    return;
  }
  await changeEditorChannel();
  idempotencyKey.value = crypto.randomUUID().replaceAll('-', '');
  savedSnapshot.value = mediaAccountEditorSnapshot(form);
  editorOpen.value = true;
}

async function openEdit(row: MediaAccount) {
  editorOpen.value = true;
  editorLoading.value = true;
  editorConflict.value = '';
  try {
    const result = await getMediaAccount(row.id);
    editing.value = result;
    Object.assign(form, {
      channelId: result.channelId,
      description: result.description || '',
      documentId: result.documentId || '',
      name: result.name,
    });
    try {
      documents.value = await getMediaDocumentOptions(result.channelId);
    } catch {
      documents.value = [];
    }
    savedSnapshot.value = mediaAccountEditorSnapshot(form);
  } catch {
    editorOpen.value = false;
    ElMessage.error('媒体账号详情加载失败');
  } finally {
    editorLoading.value = false;
  }
}

async function beforeEditorClose(done: () => void) {
  if (saving.value) return;
  if (savedSnapshot.value === mediaAccountEditorSnapshot(form)) {
    done();
    return;
  }
  try {
    await ElMessageBox.confirm('尚有未保存修改，确定关闭？', '未保存修改', {
      confirmButtonText: '放弃修改',
      type: 'warning',
    });
    done();
  } catch {
    // Keep the editor open.
  }
}

async function saveAccount() {
  if (!(await formRef.value?.validate())) return;
  saving.value = true;
  editorConflict.value = '';
  try {
    await (editing.value
      ? updateMediaAccount(
          editing.value.id,
          buildMediaAccountUpdatePayload(form, editing.value),
        )
      : createMediaAccount(
          buildMediaAccountCreatePayload(form),
          idempotencyKey.value,
        ));
    editorOpen.value = false;
    ElMessage.success(editing.value ? '媒体账号已保存' : '媒体账号已创建');
    await load();
  } catch (error: any) {
    if (isConflict(error)) editorConflict.value = errorText(error);
    else ElMessage.error(errorText(error));
  } finally {
    saving.value = false;
  }
}

async function loadDetail(rowId: string) {
  detailLoading.value = true;
  detailError.value = '';
  detailConflict.value = '';
  try {
    const detailRequest = getMediaAccount(rowId);
    const credentialRequest = canCredentialView.value
      ? getMediaCredentials(rowId)
      : Promise.resolve([]);
    const ownerRequest = canOwnerView.value
      ? getMediaAccountOwners(rowId)
      : Promise.resolve([]);
    const ownerOptionRequest = canOwnerView.value
      ? getMediaOwnerOptions(rowId)
      : Promise.resolve([]);
    const [accountResult, credentialResult, ownerResult, ownerOptionResult] =
      await Promise.all([
        detailRequest,
        credentialRequest,
        ownerRequest,
        ownerOptionRequest,
      ]);
    detail.value = accountResult;
    credentials.value = credentialResult;
    owners.value = ownerResult;
    ownerOptions.value = ownerOptionResult;
    selectedOwnerIds.value = ownerResult.map((item) => item.userId);
    ownerBaseline.value = JSON.stringify([...selectedOwnerIds.value].sort());
  } catch (error) {
    detailError.value = errorText(error);
  } finally {
    detailLoading.value = false;
  }
}

async function openDetail(row: MediaAccount) {
  detailOpen.value = true;
  preflight.value = undefined;
  await loadDetail(row.id);
}

async function saveOwners() {
  if (!detail.value) return;
  ownerSaving.value = true;
  detailConflict.value = '';
  try {
    await replaceMediaAccountOwners(
      detail.value.id,
      detail.value.configVersion,
      selectedOwnerIds.value,
    );
    await loadDetail(detail.value.id);
    await load();
    ElMessage.success('负责人已更新');
  } catch (error: any) {
    if (isConflict(error)) detailConflict.value = errorText(error);
    else ElMessage.error(errorText(error));
  } finally {
    ownerSaving.value = false;
  }
}

function openCredential(mode: CredentialMode, target?: MediaCredential) {
  resetCredentialEditor(credentialForm);
  credentialMode.value = mode;
  credentialTarget.value = target;
  credentialError.value = '';
  if (mode === 'EDIT' && target) {
    credentialForm.description = target.description || '';
  }
  credentialOpen.value = true;
}

async function saveCredential() {
  if (!detail.value) return;
  if (!credentialForm.password) {
    credentialError.value = '请输入当前登录密码完成二次认证';
    return;
  }
  if (
    credentialMode.value !== 'EDIT' &&
    credentialForm.secretInput.length < 8
  ) {
    credentialError.value = 'Secret 至少 8 位；保存后不会再次显示';
    return;
  }
  if (
    credentialMode.value === 'CREATE' &&
    !credentialForm.loginIdentifier.trim()
  ) {
    credentialError.value = '请输入媒体登录邮箱';
    return;
  }
  if (credentialMode.value === 'ROTATE' && !credentialForm.reason.trim()) {
    credentialError.value = '轮换必须填写原因';
    return;
  }
  credentialSaving.value = true;
  credentialError.value = '';
  try {
    await secondaryAuthenticate(credentialForm.password);
    if (credentialMode.value === 'CREATE') {
      await createMediaCredential(detail.value.id, {
        description: credentialForm.description.trim() || undefined,
        loginIdentifier: credentialForm.loginIdentifier.trim(),
        loginType: 'EMAIL',
        secretInput: credentialForm.secretInput,
      });
    } else if (credentialMode.value === 'EDIT' && credentialTarget.value) {
      await updateMediaCredential(detail.value.id, credentialTarget.value.id, {
        description: credentialForm.description.trim() || undefined,
        expectedVersion: credentialTarget.value.version,
        loginIdentifier: credentialForm.loginIdentifier.trim() || undefined,
      });
    } else if (credentialTarget.value) {
      await rotateMediaCredential(detail.value.id, credentialTarget.value.id, {
        expectedSecretRevision: credentialTarget.value.secretRevision,
        reason: credentialForm.reason.trim(),
        secretInput: credentialForm.secretInput,
      });
    }
    const accountId = detail.value.id;
    resetCredentialEditor(credentialForm);
    credentialOpen.value = false;
    await loadDetail(accountId);
    await load();
    ElMessage.success('凭据操作已完成；Secret 不会回显');
  } catch (error: any) {
    credentialError.value = errorText(error);
  } finally {
    credentialForm.password = '';
    credentialForm.secretInput = '';
    credentialSaving.value = false;
  }
}

async function changeCredentialStatus(target: MediaCredential) {
  if (!detail.value) return;
  const activating = target.status !== 'ACTIVE';
  try {
    const { value: password } = await ElMessageBox.prompt(
      activating
        ? '启用后，如当前未设置默认凭据，将自动设为默认。'
        : '停用或撤销前会检查已发布路由引用。',
      activating ? '启用凭据' : '停用凭据',
      { inputType: 'password', type: activating ? 'info' : 'warning' },
    );
    await secondaryAuthenticate(password);
    await changeMediaCredentialStatus(detail.value.id, target.id, {
      expectedVersion: target.version,
      reason: '管理端状态变更',
      status: activating ? 'ACTIVE' : 'DISABLED',
    });
    await loadDetail(detail.value.id);
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

async function runPreflight() {
  if (!detail.value) return;
  preflightLoading.value = true;
  try {
    preflight.value = await preflightMediaAccount(
      detail.value.id,
      detail.value.configVersion,
    );
    ElMessage.success('配置预检完成；未发起媒体网络请求');
  } catch (error) {
    ElMessage.error(errorText(error));
  } finally {
    preflightLoading.value = false;
  }
}

async function changeAccountStatus(row: MediaAccount) {
  const next = row.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
  try {
    const { value: password } = await ElMessageBox.prompt(
      next === 'ACTIVE'
        ? '启用前将执行仅配置预检，不会连接媒体。'
        : `暂停前检查引用；当前已发布引用 ${row.publishedRouteCount} 条。`,
      next === 'ACTIVE' ? '启用媒体账号' : '暂停媒体账号',
      { inputType: 'password', type: next === 'ACTIVE' ? 'info' : 'warning' },
    );
    await secondaryAuthenticate(password);
    await changeMediaAccountStatus(row.id, {
      expectedVersion: row.configVersion,
      reason: '管理端状态变更',
      status: next,
    });
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

async function changeArchive(row: MediaAccount) {
  const restoring = row.status === 'ARCHIVED';
  try {
    const { value: password } = await ElMessageBox.prompt(
      restoring
        ? '恢复后回到停用状态，需要重新预检和启用。'
        : `归档前检查任务/路由引用；当前路由引用 ${row.publishedRouteCount} 条。`,
      restoring ? '恢复媒体账号' : '归档媒体账号',
      { inputType: 'password', type: restoring ? 'info' : 'warning' },
    );
    await secondaryAuthenticate(password);
    await (restoring
      ? restoreMediaAccount(row.id, row.configVersion, '管理端恢复')
      : archiveMediaAccount(row.id, row.configVersion, '管理端归档'));
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

function closeCredential() {
  resetCredentialEditor(credentialForm);
  credentialOpen.value = false;
}

function isConflict(error: any) {
  return Number(error?.response?.status || error?.response?.data?.code) === 409;
}

function errorText(error: any) {
  return (
    error?.response?.data?.msg ||
    error?.response?.data?.message ||
    error?.data?.msg ||
    error?.message ||
    '请稍后重试'
  );
}

function statusLabel(status: MediaAccount['status']) {
  return (
    {
      ACTIVE: '启用',
      ARCHIVED: '已归档',
      DISABLED: '停用',
      PAUSED: '暂停',
    }[status] || status
  );
}

function statusType(status: MediaAccount['status']) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'ARCHIVED') return 'info';
  if (status === 'PAUSED') return 'warning';
  return 'danger';
}

const ownerDirty = computed(
  () =>
    JSON.stringify([...selectedOwnerIds.value].sort()) !== ownerBaseline.value,
);

onMounted(async () => {
  if (!canView.value) return;
  try {
    await loadOptions();
  } catch {
    ElMessage.warning('协议渠道选项加载失败，列表仍可继续使用');
  }
  await load();
});
</script>

<template>
  <div class="media-account-page">
    <el-result
      v-if="emptyState === 'NO_PERMISSION'"
      icon="warning"
      sub-title="当前账号缺少媒体账号功能权限；系统不会发起列表请求。"
      title="无权查看媒体账号"
    />

    <template v-else>
      <header class="page-heading">
        <div>
          <p class="eyebrow">OCPX · MEDIA OPERATIONS</p>
          <h1>媒体账号</h1>
          <p class="subtitle">
            Channel 是协议目录，媒体账号是业务账号；凭据修订只写、不可回显。
          </p>
        </div>
        <el-button
          v-if="canAdd"
          :icon="Plus"
          type="primary"
          @click="openCreate"
        >
          新增媒体账号
        </el-button>
      </header>

      <el-alert
        class="boundary-alert"
        :closable="false"
        show-icon
        title="连接检查仅做配置预检，不发起任何真实媒体网络请求；真实连通性仍待第三方 UAT。"
        type="info"
      />

      <el-card class="filter-card" shadow="never">
        <div class="filters">
          <el-input
            v-model="filters.keyword"
            clearable
            :prefix-icon="Search"
            placeholder="名称或内部编码"
            @keyup.enter="search"
          />
          <el-select
            v-model="filters.channelId"
            clearable
            filterable
            placeholder="协议渠道"
          >
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="`${channel.name} · ${channel.adapterCode}`"
              :value="channel.id"
            />
          </el-select>
          <el-select
            v-model="filters.status"
            clearable
            :disabled="filters.archived"
            placeholder="状态"
          >
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
            <el-option label="暂停" value="PAUSED" />
          </el-select>
          <el-checkbox v-model="filters.archived" border
            >查看已归档</el-checkbox
          >
          <div class="filter-actions">
            <el-button :icon="Search" type="primary" @click="search"
              >查询</el-button
            >
            <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          </div>
        </div>
      </el-card>

      <el-card class="table-card" shadow="never">
        <el-table v-loading="loading" :data="rows" height="calc(100vh - 360px)">
          <el-table-column label="媒体账号" min-width="210">
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.name }}</strong>
                <code>{{ row.code }}</code>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="协议渠道" min-width="180">
            <template #default="{ row }">
              <strong>{{ row.channelName }}</strong>
              <small>{{ row.adapterCode || '适配器未配置' }}</small>
            </template>
          </el-table-column>
          <el-table-column label="凭据" width="130">
            <template #default="{ row }">
              {{ row.activeCredentialCount }}/{{ row.credentialCount }} 启用
              <small>revision {{ row.credentialRevision }}</small>
            </template>
          </el-table-column>
          <el-table-column label="负责人" width="90" prop="ownerCount" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="路由影响" min-width="140">
            <template #default="{ row }">
              <el-tag
                v-if="row.republishRequiredCount"
                size="small"
                type="warning"
              >
                {{ row.republishRequiredCount }} 条待重发
              </el-tag>
              <span v-else class="muted"
                >已发布引用 {{ row.publishedRouteCount }}</span
              >
            </template>
          </el-table-column>
          <el-table-column label="版本" width="90">
            <template #default="{ row }">v{{ row.configVersion }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170" prop="updateTime" />
          <el-table-column fixed="right" label="操作" width="310">
            <template #default="{ row }">
              <el-button
                :icon="Key"
                link
                type="primary"
                @click="openDetail(row)"
              >
                账号资料
              </el-button>
              <el-button
                v-if="canEdit && row.status !== 'ARCHIVED'"
                :icon="EditPen"
                link
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canStatus && row.status !== 'ARCHIVED'"
                :icon="Connection"
                link
                type="warning"
                @click="changeAccountStatus(row)"
              >
                {{ row.status === 'ACTIVE' ? '暂停' : '启用' }}
              </el-button>
              <el-button
                v-if="canArchive"
                :icon="FolderDelete"
                link
                :type="row.status === 'ARCHIVED' ? 'success' : 'danger'"
                @click="changeArchive(row)"
              >
                {{ row.status === 'ARCHIVED' ? '恢复' : '归档' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-panel">
              <template v-if="emptyState === 'ERROR'">
                <strong>媒体账号列表加载失败</strong>
                <span>筛选条件已保留，可直接重试。</span>
                <el-button type="primary" @click="load">重新加载</el-button>
              </template>
              <template v-else-if="emptyState === 'FILTER_EMPTY'">
                <strong>未找到匹配媒体账号</strong>
                <span>调整渠道、状态或关键词后再试。</span>
                <el-button @click="resetFilters">清空筛选</el-button>
              </template>
              <template v-else>
                <strong>暂无媒体账号</strong>
                <span>先从启用的协议渠道创建业务账号。</span>
                <el-button v-if="canAdd" type="primary" @click="openCreate">
                  新增媒体账号
                </el-button>
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
      destroy-on-close
      size="560px"
      :title="editing ? '编辑媒体账号' : '新增媒体账号'"
    >
      <div v-loading="editorLoading">
        <el-alert
          v-if="editorConflict"
          class="drawer-alert"
          :closable="false"
          :title="editorConflict"
          type="warning"
          show-icon
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
        >
          <el-form-item label="协议渠道" prop="channelId">
            <el-select
              v-model="form.channelId"
              class="full-width"
              :disabled="Boolean(editing)"
              filterable
              @change="changeEditorChannel"
            >
              <el-option
                v-for="channel in channels"
                :key="channel.id"
                :disabled="
                  channel.status !== 'ACTIVE' || !channel.adapterAvailable
                "
                :label="`${channel.name} · ${channel.adapterCode}`"
                :value="channel.id"
              />
            </el-select>
            <span class="field-hint">协议归属创建后不可变更。</span>
          </el-form-item>
          <el-form-item label="媒体账号名称" prop="name">
            <el-input v-model="form.name" maxlength="64" show-word-limit />
          </el-form-item>
          <el-form-item label="渠道接入资料（可选）">
            <el-select v-model="form.documentId" class="full-width" clearable>
              <el-option
                v-for="document in documents"
                :key="document.id"
                :label="`${document.type} · v${document.version}`"
                :value="document.id"
              />
            </el-select>
            <span class="field-hint"
              >仅可关联当前渠道下已扫描通过的受管资料。</span
            >
          </el-form-item>
          <el-form-item label="说明" prop="description">
            <el-input
              v-model="form.description"
              maxlength="500"
              :rows="4"
              show-word-limit
              type="textarea"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="editorOpen = false"
          >取消</el-button
        >
        <el-button :loading="saving" type="primary" @click="saveAccount"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <el-drawer
      v-model="detailOpen"
      destroy-on-close
      size="760px"
      title="媒体账号资料"
    >
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <el-result
        v-else-if="detailError"
        icon="error"
        :sub-title="detailError"
        title="账号资料加载失败"
      >
        <template #extra>
          <el-button
            v-if="detail"
            type="primary"
            @click="loadDetail(detail.id)"
          >
            重试
          </el-button>
        </template>
      </el-result>
      <template v-else-if="detail">
        <div class="detail-hero">
          <div>
            <p class="eyebrow">{{ detail.adapterCode }} · {{ detail.code }}</p>
            <h2>{{ detail.name }}</h2>
            <span
              >{{ detail.channelName }} · 配置版本
              {{ detail.configVersion }}</span
            >
          </div>
          <el-tag :type="statusType(detail.status)">{{
            statusLabel(detail.status)
          }}</el-tag>
        </div>
        <el-alert
          v-if="detailConflict"
          :closable="false"
          :title="detailConflict"
          type="warning"
          show-icon
        />
        <el-descriptions :column="3" border class="reference-grid">
          <el-descriptions-item label="任务引用">
            {{ detail.references.taskCount }}
          </el-descriptions-item>
          <el-descriptions-item label="路由引用">
            {{ detail.references.routeCount }}
          </el-descriptions-item>
          <el-descriptions-item label="已发布引用">
            {{ detail.references.publishedRouteCount }}
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs class="detail-tabs">
          <el-tab-pane label="外部媒体凭据">
            <el-alert
              :closable="false"
              show-icon
              title="媒体登录身份与系统用户完全分离；Secret 只写，保存后不再显示。"
              type="warning"
            />
            <div class="section-toolbar">
              <span
                >账号 credential revision {{ detail.credentialRevision }}</span
              >
              <el-button
                v-if="canCredentialAdd"
                :icon="Plus"
                type="primary"
                @click="openCredential('CREATE')"
              >
                新增凭据
              </el-button>
            </div>
            <el-table v-if="canCredentialView" :data="credentials">
              <el-table-column
                label="登录账号"
                min-width="190"
                prop="maskedIdentifier"
              />
              <el-table-column label="Secret" width="110">
                <template #default="{ row }"
                  >已配置 · r{{ row.secretRevision }}</template
                >
              </el-table-column>
              <el-table-column label="状态" width="100" prop="status" />
              <el-table-column label="配置预检" min-width="130">
                <template #default="{ row }">{{
                  row.lastPreflightStatus || '未检查'
                }}</template>
              </el-table-column>
              <el-table-column fixed="right" label="操作" width="230">
                <template #default="{ row }">
                  <el-button
                    v-if="canCredentialEdit"
                    link
                    @click="openCredential('EDIT', row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-if="canCredentialRotate"
                    link
                    type="primary"
                    @click="openCredential('ROTATE', row)"
                  >
                    轮换 Secret
                  </el-button>
                  <el-button
                    v-if="canCredentialStatus"
                    link
                    type="warning"
                    @click="changeCredentialStatus(row)"
                  >
                    {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty
                  description="暂无凭据；新增后先启用，再执行配置预检"
                />
              </template>
            </el-table>
            <el-result
              v-else
              icon="warning"
              sub-title="当前账号缺少凭据查看权限。"
              title="凭据区域无权限"
            />
          </el-tab-pane>

          <el-tab-pane label="内部负责人">
            <el-alert
              :closable="false"
              show-icon
              title="负责人引用系统用户，不会创建外部媒体登录账号。"
              type="info"
            />
            <template v-if="canOwnerView">
              <el-select
                v-model="selectedOwnerIds"
                class="owner-select"
                collapse-tags
                filterable
                multiple
                placeholder="选择内部负责人"
              >
                <el-option
                  v-for="owner in ownerOptions"
                  :key="owner.id"
                  :label="owner.name"
                  :value="owner.id"
                />
              </el-select>
              <el-button
                v-if="canOwnerEdit"
                :disabled="!ownerDirty"
                :icon="User"
                :loading="ownerSaving"
                type="primary"
                @click="saveOwners"
              >
                保存负责人
              </el-button>
            </template>
            <el-result v-else icon="warning" title="负责人区域无权限" />
          </el-tab-pane>

          <el-tab-pane label="配置预检">
            <el-alert
              :closable="false"
              show-icon
              title="固定为 CONFIG_ONLY；不解密 Secret，不连接媒体，不代表真实连通性验收。"
              type="info"
            />
            <el-button
              v-if="canDebug"
              :icon="Connection"
              :loading="preflightLoading"
              type="primary"
              @click="runPreflight"
            >
              执行配置预检
            </el-button>
            <el-result v-else icon="warning" title="缺少配置预检权限" />
            <section v-if="preflight" class="preflight-result">
              <div class="preflight-heading">
                <el-tag
                  :type="preflight.status === 'READY' ? 'success' : 'danger'"
                >
                  {{ preflight.status }}
                </el-tag>
                <span>networkCalled = false · {{ preflight.checkedAt }}</span>
              </div>
              <div
                v-for="check in preflight.checks"
                :key="check.code"
                class="check-row"
              >
                <el-tag
                  :type="
                    check.level === 'PASS'
                      ? 'success'
                      : check.level === 'WARN'
                        ? 'warning'
                        : 'danger'
                  "
                  size="small"
                >
                  {{ check.level }}
                </el-tag>
                <span>{{ check.message }}</span>
              </div>
            </section>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog
      v-model="credentialOpen"
      :close-on-click-modal="false"
      destroy-on-close
      width="520px"
      :title="
        credentialMode === 'CREATE'
          ? '新增外部媒体凭据'
          : credentialMode === 'EDIT'
            ? '编辑凭据元数据'
            : '轮换 Secret'
      "
      @closed="closeCredential"
    >
      <el-alert
        v-if="credentialError"
        :closable="false"
        :title="credentialError"
        type="error"
        show-icon
      />
      <el-form label-position="top">
        <el-form-item v-if="credentialMode !== 'ROTATE'" label="媒体登录邮箱">
          <el-input
            v-model="credentialForm.loginIdentifier"
            autocomplete="off"
            :placeholder="
              credentialMode === 'EDIT'
                ? '留空表示不修改；原值不会回显'
                : 'name@example.com'
            "
          />
        </el-form-item>
        <el-form-item v-if="credentialMode !== 'EDIT'" label="Secret（只写）">
          <el-input
            v-model="credentialForm.secretInput"
            autocomplete="new-password"
            placeholder="保存后无法查看"
            show-password
            type="password"
          />
        </el-form-item>
        <el-form-item v-if="credentialMode === 'ROTATE'" label="轮换原因">
          <el-input v-model="credentialForm.reason" maxlength="255" />
        </el-form-item>
        <el-form-item v-else label="说明">
          <el-input
            v-model="credentialForm.description"
            maxlength="500"
            :rows="2"
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="当前登录密码（二次认证 120 秒）">
          <el-input
            v-model="credentialForm.password"
            autocomplete="current-password"
            show-password
            type="password"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="credentialSaving" @click="closeCredential"
          >取消</el-button
        >
        <el-button
          :loading="credentialSaving"
          type="primary"
          @click="saveCredential"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.media-account-page {
  min-height: 100%;
  padding: 20px;
  background: #f4f6f8;
}

.page-heading,
.detail-hero,
.section-toolbar,
.preflight-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.page-heading {
  margin-bottom: 16px;
}

.page-heading h1,
.detail-hero h2 {
  margin: 3px 0 5px;
  font-size: 25px;
  color: #172033;
  letter-spacing: -0.02em;
}

.eyebrow {
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.14em;
}

.subtitle,
.detail-hero span,
.field-hint,
.muted,
small {
  display: block;
  font-size: 12px;
  color: #667085;
}

.boundary-alert,
.filter-card,
.table-card {
  margin-bottom: 14px;
}

.filters {
  display: grid;
  grid-template-columns:
    minmax(200px, 1.2fr) minmax(180px, 1fr)
    150px auto auto;
  gap: 12px;
  align-items: center;
}

.filter-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.primary-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.primary-cell code {
  width: fit-content;
  padding: 2px 5px;
  font-size: 11px;
  color: #475467;
  background: #eef2f6;
  border-radius: 4px;
}

.empty-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  padding: 50px 0;
  color: #667085;
}

.empty-panel strong {
  font-size: 16px;
  color: #273244;
}

.table-card :deep(.el-pagination) {
  justify-content: flex-end;
  margin-top: 16px;
}

.full-width,
.owner-select {
  width: 100%;
}

.drawer-alert,
.reference-grid,
.detail-tabs,
.section-toolbar,
.owner-select,
.preflight-result {
  margin-bottom: 16px;
}

.detail-hero {
  padding: 16px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #182434, #27364a);
  border-radius: 8px;
}

.detail-hero h2,
.detail-hero span,
.detail-hero .eyebrow {
  color: #f8fafc;
}

.section-toolbar {
  align-items: center;
  padding: 14px 0 8px;
  color: #667085;
}

.preflight-result {
  padding: 14px;
  margin-top: 14px;
  background: #f8fafc;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.preflight-heading {
  align-items: center;
  padding-bottom: 10px;
  font-size: 12px;
  color: #667085;
  border-bottom: 1px solid #e4e7ed;
}

.check-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-top: 10px;
}

@media (max-width: 980px) {
  .filters {
    grid-template-columns: 1fr 1fr;
  }

  .filter-actions {
    justify-content: flex-start;
  }
}
</style>
