<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { Media, MediaScope, MediaScopeUserOption } from '#/api/ad';

import { computed, nextTick, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { useUserStore } from '@boot/stores';

import {
  CirclePlus,
  Delete,
  EditPen,
  Key,
  Refresh,
  Search,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeMediaStatus,
  deleteMedia,
  getManagedMedia,
  getMedia,
  getMediaScopeCandidates,
  getMediaScopes,
  grantMediaScope,
  revokeMediaScope,
  saveMedia,
} from '#/api/ad';

import { generateRandomMediaCode } from './media-code';

import '../components/mvp-page.css';

defineOptions({ name: 'AdMediaScopes' });

type MediaForm = {
  code: string;
  id?: string;
  name: string;
  settlementCurrency: 'CNY';
  status: Media['status'];
  timezone: string;
  version?: number;
};

const { hasAccessByCodes } = useAccess();
const userStore = useUserStore();
const canManageMedia = computed(() =>
  hasAccessByCodes(['subject:media:write']),
);
const canManageScopes = computed(() =>
  hasAccessByCodes(['subject:media-scope:write']),
);
const roleDescription = computed(() => {
  if (canManageMedia.value) return '平台管理员 · 全部媒体与用户范围';
  if (canManageScopes.value) return '运营 · 仅管理自己已授权的启用媒体';
  return '分析员 · 仅查看自己已授权的媒体基础信息';
});

const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const changingStatusId = ref('');
const rows = ref<Media[]>([]);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const query = reactive<{ keyword: string; status?: Media['status'] }>({
  keyword: '',
  status: undefined,
});

const editorVisible = ref(false);
const formRef = ref<FormInstance>();
const formSnapshot = ref('');
const form = reactive<MediaForm>({
  code: '',
  name: '',
  settlementCurrency: 'CNY',
  status: 'ACTIVE',
  timezone: 'Asia/Shanghai',
});
const formRules: FormRules<MediaForm> = {
  code: [
    { required: true, message: '请输入媒体编码', trigger: 'blur' },
    {
      pattern: /^[a-z0-9][a-z0-9-]{2,63}$/,
      message: '使用 3–64 位小写字母、数字或连字符',
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '请输入媒体名称', trigger: 'blur' },
    { max: 128, message: '媒体名称不能超过 128 个字符', trigger: 'blur' },
  ],
  timezone: [
    { required: true, message: '请输入 IANA 时区', trigger: 'blur' },
    {
      trigger: 'blur',
      validator: (_rule, value, callback) => {
        try {
          new Intl.DateTimeFormat('zh-CN', { timeZone: value }).format();
          callback();
        } catch {
          callback(new Error('请输入有效的 IANA 时区，例如 Asia/Shanghai'));
        }
      },
    },
  ],
};

const scopeVisible = ref(false);
const scopeLoading = ref(false);
const scopeSaving = ref(false);
const selectedMedia = ref<Media>();
const scopes = ref<MediaScope[]>([]);
const candidates = ref<MediaScopeUserOption[]>([]);
const candidateKeyword = ref('');
const selectedUserId = ref('');
const userDirectory = reactive(new Map<string, MediaScopeUserOption>());
const assignedUserIds = computed(
  () => new Set(scopes.value.map((item) => item.userId)),
);
const availableCandidates = computed(() =>
  candidates.value.filter((item) => !assignedUserIds.value.has(item.id)),
);
const currentUserId = computed(() => userStore.userInfo?.userId || '');

function readableError(error: unknown, fallback: string) {
  const failure = error as {
    message?: string;
    msg?: string;
    response?: { data?: { msg?: string } };
  };
  return (
    failure.response?.data?.msg || failure.msg || failure.message || fallback
  );
}

async function load() {
  loading.value = true;
  try {
    const params = {
      current: current.value,
      keyword: query.keyword.trim() || undefined,
      size: size.value,
      status: canManageMedia.value ? query.status : undefined,
    };
    const result = canManageMedia.value
      ? await getManagedMedia(params)
      : await getMedia(params);
    rows.value = result.records || [];
    total.value = result.total || 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error(readableError(error, '媒体列表加载失败，请重试'));
  } finally {
    loading.value = false;
  }
}

function search() {
  current.value = 1;
  void load();
}

function resetSearch() {
  query.keyword = '';
  query.status = undefined;
  search();
}

function mediaSnapshot() {
  return JSON.stringify({
    code: form.code,
    name: form.name,
    timezone: form.timezone,
  });
}

function openEditor(row?: Media) {
  Object.assign(form, {
    code: row?.code || generateRandomMediaCode(),
    id: row?.id,
    name: row?.name || '',
    settlementCurrency: 'CNY',
    status: row?.status || 'ACTIVE',
    timezone: row?.timezone || 'Asia/Shanghai',
    version: row?.version,
  });
  editorVisible.value = true;
  void nextTick(() => {
    formRef.value?.clearValidate();
    formSnapshot.value = mediaSnapshot();
  });
}

function replaceRandomMediaCode() {
  if (form.id) return;
  form.code = generateRandomMediaCode();
  void nextTick(() => formRef.value?.validateField('code'));
}

async function beforeEditorClose(done: () => void) {
  if (saving.value || mediaSnapshot() === formSnapshot.value) return done();
  try {
    await ElMessageBox.confirm('媒体信息尚未保存，确定关闭吗？', '放弃修改', {
      cancelButtonText: '继续编辑',
      confirmButtonText: '放弃修改',
      type: 'warning',
    });
    done();
  } catch {
    // Keep the editor open.
  }
}

async function submitMedia() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || saving.value) return;
  saving.value = true;
  try {
    await saveMedia({
      ...form,
      code: form.code.trim(),
      name: form.name.trim(),
      timezone: form.timezone.trim(),
    });
    ElMessage.success(form.id ? '媒体信息已更新' : '媒体已新增');
    formSnapshot.value = mediaSnapshot();
    editorVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(readableError(error, '媒体保存失败，请检查后重试'));
  } finally {
    saving.value = false;
  }
}

async function deleteCurrentMedia() {
  if (!form.id || form.version === undefined || deleting.value) return;
  try {
    await ElMessageBox.confirm(
      `确定永久删除媒体“${form.name}”吗？仅未产生授权和业务数据的媒体可以删除。`,
      '删除媒体',
      {
        cancelButtonText: '取消',
        confirmButtonText: '永久删除',
        type: 'error',
      },
    );
    deleting.value = true;
    await deleteMedia(form.id, form.version);
    ElMessage.success('媒体已删除');
    editorVisible.value = false;
    if (rows.value.length === 1 && current.value > 1) current.value -= 1;
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, '媒体删除失败，请重试'));
    }
  } finally {
    deleting.value = false;
  }
}

async function toggleStatus(row: Media) {
  const nextStatus: Media['status'] =
    row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  const action = nextStatus === 'ACTIVE' ? '启用' : '停用';
  try {
    await ElMessageBox.confirm(
      `确定${action}媒体“${row.name}”吗？`,
      `${action}媒体`,
      {
        cancelButtonText: '取消',
        confirmButtonText: action,
        type: nextStatus === 'ACTIVE' ? 'info' : 'warning',
      },
    );
    changingStatusId.value = row.id;
    await changeMediaStatus(row.id, {
      status: nextStatus,
      version: row.version,
    });
    ElMessage.success(`媒体已${action}`);
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, `媒体${action}失败，请重试`));
    }
  } finally {
    changingStatusId.value = '';
  }
}

function rememberUsers(items: MediaScopeUserOption[]) {
  items.forEach((item) => userDirectory.set(item.id, item));
}

async function loadScopesAndCandidates() {
  if (!selectedMedia.value) return;
  scopeLoading.value = true;
  try {
    const mediaId = selectedMedia.value.id;
    const [scopeResult, candidateResult] = await Promise.all([
      getMediaScopes({ current: 1, mediaId, size: 200 }),
      getMediaScopeCandidates({
        current: 1,
        keyword: candidateKeyword.value.trim() || undefined,
        mediaId,
        size: 200,
      }),
    ]);
    scopes.value = scopeResult.records || [];
    candidates.value = candidateResult.records || [];
    rememberUsers(candidates.value);
  } catch (error) {
    ElMessage.error(readableError(error, '授权信息加载失败，请重试'));
  } finally {
    scopeLoading.value = false;
  }
}

async function openScopeManager(row: Media) {
  selectedMedia.value = row;
  selectedUserId.value = '';
  candidateKeyword.value = '';
  scopes.value = [];
  candidates.value = [];
  userDirectory.clear();
  scopeVisible.value = true;
  await loadScopesAndCandidates();
}

function userLabel(userId: string) {
  const user = userDirectory.get(userId);
  if (!user) return userId;
  const displayName = user.nickname?.trim() || user.username;
  return displayName === user.username
    ? user.username
    : `${displayName}（${user.username}）`;
}

async function grantScope() {
  if (!selectedMedia.value || !selectedUserId.value || scopeSaving.value)
    return;
  scopeSaving.value = true;
  try {
    await grantMediaScope({
      mediaId: selectedMedia.value.id,
      userId: selectedUserId.value,
    });
    selectedUserId.value = '';
    ElMessage.success('用户媒体范围已授权');
    await loadScopesAndCandidates();
  } catch (error) {
    ElMessage.error(readableError(error, '授权失败，请重试'));
  } finally {
    scopeSaving.value = false;
  }
}

async function revokeScope(scope: MediaScope) {
  if (!selectedMedia.value || scope.userId === currentUserId.value) return;
  try {
    await ElMessageBox.confirm(
      `确定撤销“${userLabel(scope.userId)}”对该媒体的访问吗？`,
      '撤销媒体范围',
      {
        cancelButtonText: '取消',
        confirmButtonText: '撤销授权',
        type: 'warning',
      },
    );
    await revokeMediaScope(scope.id, selectedMedia.value.id);
    ElMessage.success('媒体范围已撤销');
    await loadScopesAndCandidates();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, '撤销失败，请重试'));
    }
  }
}

void load();
</script>

<template>
  <main class="mvp-page media-scopes-page">
    <section class="page-heading" aria-labelledby="media-scope-title">
      <div>
        <div class="eyebrow">
          <Key :size="16" aria-hidden="true" />
          媒体配置入口
        </div>
        <h1 id="media-scope-title">媒体与授权</h1>
        <p>集中维护媒体主体，并在当前媒体内直接管理用户访问范围。</p>
      </div>
      <div class="role-badge">
        <UserFilled :size="18" aria-hidden="true" />
        <span>{{ roleDescription }}</span>
      </div>
    </section>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters" role="search" aria-label="媒体查询">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="搜索媒体名称或编码"
            :prefix-icon="Search"
            @clear="search"
            @keyup.enter="search"
          />
          <el-select
            v-if="canManageMedia"
            v-model="query.status"
            clearable
            placeholder="全部状态"
            @change="search"
          >
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        </div>
        <el-button
          v-if="canManageMedia"
          :icon="CirclePlus"
          type="primary"
          @click="openEditor()"
        >
          新增媒体
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        table-layout="fixed"
      >
        <el-table-column label="媒体" min-width="220">
          <template #default="{ row }">
            <div class="entity-cell">
              <strong>{{ row.name }}</strong>
              <span class="mvp-code">{{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          class-name="secondary-column"
          label="业务时区"
          min-width="170"
          prop="timezone"
        />
        <el-table-column
          class-name="secondary-column"
          label="结算币种"
          prop="settlementCurrency"
          width="110"
        />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" min-width="230">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-if="canManageScopes"
                link
                type="primary"
                @click="openScopeManager(row)"
              >
                管理授权
              </el-button>
              <template v-if="canManageMedia">
                <el-button :icon="EditPen" link @click="openEditor(row)">
                  编辑
                </el-button>
                <el-button
                  :icon="SwitchButton"
                  :loading="changingStatusId === row.id"
                  link
                  :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                  @click="toggleStatus(row)"
                >
                  {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
                </el-button>
              </template>
              <span
                v-if="!canManageScopes && !canManageMedia"
                class="read-only"
              >
                只读
              </span>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            description="当前账号尚未获得可访问的媒体范围"
            :image-size="96"
          >
            <el-button
              v-if="canManageMedia"
              type="primary"
              @click="openEditor()"
            >
              新增首个媒体
            </el-button>
            <p v-else class="empty-help">
              请联系管理员或有权限的运营为你分配媒体。
            </p>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="editorVisible"
      :before-close="beforeEditorClose"
      :close-on-click-modal="false"
      :title="form.id ? '编辑媒体' : '新增媒体'"
      size="min(560px, 100vw)"
    >
      <el-form
        ref="formRef"
        label-position="top"
        :model="form"
        :rules="formRules"
      >
        <div class="drawer-intro">
          新增时自动生成媒体编码，可手动修改或换一个随机编码；创建后不可修改。
          业务时区将用于报表日界线计算。
        </div>
        <el-form-item label="媒体编码" prop="code">
          <el-input
            v-model="form.code"
            autocomplete="off"
            :disabled="Boolean(form.id)"
            maxlength="64"
            placeholder="例如 toutiao-main"
          >
            <template v-if="!form.id" #append>
              <el-button :icon="Refresh" @click="replaceRandomMediaCode">
                换一个
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="媒体名称" prop="name">
          <el-input
            v-model="form.name"
            autocomplete="off"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="业务时区" prop="timezone">
          <el-input
            v-model="form.timezone"
            autocomplete="off"
            placeholder="Asia/Shanghai"
          />
        </el-form-item>
        <el-form-item label="结算币种">
          <el-input model-value="CNY" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-between">
          <el-button
            v-if="form.id"
            :icon="Delete"
            :loading="deleting"
            type="danger"
            plain
            @click="deleteCurrentMedia"
          >
            删除媒体
          </el-button>
          <span v-else></span>
          <div>
            <el-button
              :disabled="saving || deleting"
              @click="editorVisible = false"
            >
              取消
            </el-button>
            <el-button
              :disabled="deleting"
              :loading="saving"
              type="primary"
              @click="submitMedia"
            >
              保存媒体
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="scopeVisible"
      :title="`用户授权 · ${selectedMedia?.name || ''}`"
      size="min(720px, 100vw)"
    >
      <div class="scope-guide">
        <strong>授权边界</strong>
        <span> 只能为当前媒体分配有效用户；运营不能撤销自己的媒体范围。 </span>
      </div>
      <div class="candidate-toolbar">
        <el-input
          v-model="candidateKeyword"
          clearable
          placeholder="按用户名或昵称查找用户"
          @clear="loadScopesAndCandidates"
          @keyup.enter="loadScopesAndCandidates"
        />
        <el-button :icon="Search" @click="loadScopesAndCandidates">
          查找
        </el-button>
      </div>
      <div class="grant-line">
        <el-select
          v-model="selectedUserId"
          filterable
          placeholder="选择要授权的用户"
        >
          <el-option
            v-for="item in availableCandidates"
            :key="item.id"
            :label="userLabel(item.id)"
            :value="item.id"
          />
        </el-select>
        <el-button
          :disabled="!selectedUserId"
          :loading="scopeSaving"
          type="primary"
          @click="grantScope"
        >
          授予访问
        </el-button>
      </div>
      <el-table
        v-loading="scopeLoading"
        :data="scopes"
        row-key="id"
        table-layout="fixed"
      >
        <el-table-column label="已授权用户" min-width="240">
          <template #default="{ row }">
            <div class="entity-cell">
              <strong>{{ userLabel(row.userId) }}</strong>
              <span class="user-id">{{ row.userId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          class-name="secondary-column"
          label="授权时间"
          min-width="180"
          prop="createTime"
        />
        <el-table-column align="right" label="操作" width="140">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.userId === currentUserId && !canManageMedia"
              content="运营不能撤销自己的媒体范围"
            >
              <el-button disabled link>当前用户</el-button>
            </el-tooltip>
            <el-button v-else link type="danger" @click="revokeScope(row)">
              撤销授权
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">该媒体尚未授权给任何用户</div>
        </template>
      </el-table>
    </el-drawer>
  </main>
</template>

<style scoped>
.page-heading {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin: 0 auto 18px;
}

.page-heading h1 {
  margin: 5px 0 2px;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 680;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.page-heading p,
.empty-help {
  margin: 0;
  color: var(--mvp-muted);
}

.eyebrow,
.role-badge {
  display: inline-flex;
  gap: 7px;
  align-items: center;
}

.eyebrow {
  font-size: 12px;
  font-weight: 650;
  color: var(--mvp-accent);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.role-badge {
  max-width: 360px;
  padding: 10px 14px;
  color: var(--mvp-ink);
  background: color-mix(in srgb, var(--mvp-accent) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--mvp-accent) 20%, transparent);
  border-radius: 10px;
}

.mvp-filters :deep(.el-input) {
  width: 260px;
}

.mvp-filters :deep(.el-select) {
  width: 140px;
}

.entity-cell {
  display: grid;
  gap: 3px;
}

.entity-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 620;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: flex-end;
}

.read-only,
.user-id {
  font-size: 12px;
  color: var(--mvp-muted);
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-intro,
.scope-guide {
  padding: 14px 16px;
  margin-bottom: 20px;
  line-height: 1.6;
  color: var(--mvp-muted);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.scope-guide {
  display: grid;
  gap: 3px;
}

.scope-guide strong {
  color: var(--mvp-ink);
}

.candidate-toolbar,
.grant-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.grant-line {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.el-button:focus-visible),
:deep(.el-input__wrapper:focus-within),
:deep(.el-select__wrapper:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

@media (max-width: 767px) {
  .page-heading {
    display: grid;
    align-items: start;
  }

  .role-badge {
    max-width: none;
  }

  .mvp-toolbar {
    align-items: stretch;
  }

  .mvp-filters,
  .mvp-filters :deep(.el-input),
  .mvp-filters :deep(.el-select) {
    width: 100%;
  }

  .mvp-filters :deep(.el-button) {
    flex: 1;
  }

  :deep(.secondary-column) {
    display: none;
  }

  .candidate-toolbar,
  .grant-line {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
