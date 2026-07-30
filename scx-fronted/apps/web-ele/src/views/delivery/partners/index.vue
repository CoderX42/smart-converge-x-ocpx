<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { Media, Partner, PartnerInput, PartnerStatus } from '#/api/ad';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useAccess } from '@boot/access';

import {
  CirclePlus,
  Connection,
  Delete,
  EditPen,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deletePartner,
  getManagedMedia,
  getMedia,
  getPartners,
  savePartner,
} from '#/api/ad';

import { generateRandomBusinessCode } from '../components/random-code';

import '../components/mvp-page.css';

defineOptions({ name: 'AdPartners' });

type PartnerForm = PartnerInput;

const { hasAccessByCodes } = useAccess();
const canWrite = computed(() => hasAccessByCodes(['ad:partner:write']));
const canManageMedia = computed(() =>
  hasAccessByCodes(['subject:media:write']),
);

const bootstrapping = ref(true);
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<Partner[]>([]);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const query = reactive<{ keyword: string; status?: PartnerStatus }>({
  keyword: '',
  status: undefined,
});

const drawerVisible = ref(false);
const formRef = ref<FormInstance>();
const initialSnapshot = ref('');
const form = reactive<PartnerForm>({
  code: '',
  description: '',
  mediaId: '',
  name: '',
  status: 'ACTIVE',
  type: 'UPSTREAM',
});
const rules: FormRules<PartnerForm> = {
  code: [
    { required: true, message: '请输入合作方编码', trigger: 'blur' },
    {
      pattern: /^[A-Z0-9][\w-]{1,63}$/i,
      message: '使用 2–64 位字母、数字、下划线或连字符',
      trigger: 'blur',
    },
  ],
  description: [
    { max: 500, message: '说明不能超过 500 个字符', trigger: 'blur' },
  ],
  mediaId: [{ required: true, message: '请选择媒体范围', trigger: 'change' }],
  name: [
    { required: true, message: '请输入合作方名称', trigger: 'blur' },
    { max: 128, message: '名称不能超过 128 个字符', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
};

const selectedMedia = computed(() =>
  media.value.find((item) => item.id === mediaId.value),
);

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

async function bootstrap() {
  bootstrapping.value = true;
  try {
    const result = canManageMedia.value
      ? await getManagedMedia({ current: 1, size: 200 })
      : await getMedia({ current: 1, size: 200 });
    media.value = result.records || [];
    mediaId.value = media.value[0]?.id || '';
  } catch (error) {
    ElMessage.error(readableError(error, '媒体范围加载失败，请重试'));
  } finally {
    bootstrapping.value = false;
  }
}

async function load() {
  if (!mediaId.value) {
    rows.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getPartners({
      current: current.value,
      keyword: query.keyword.trim() || undefined,
      mediaId: mediaId.value,
      size: size.value,
      status: query.status,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error(readableError(error, '合作方列表加载失败，请重试'));
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

function snapshot() {
  return JSON.stringify({
    code: form.code,
    description: form.description,
    mediaId: form.mediaId,
    name: form.name,
    status: form.status,
  });
}

function openEditor(row?: Partner) {
  Object.assign(form, {
    code: row?.code || generateRandomBusinessCode('partner'),
    description: row?.description || '',
    id: row?.id,
    mediaId: row?.mediaId || mediaId.value,
    name: row?.name || '',
    status: row?.status || 'ACTIVE',
    type: 'UPSTREAM',
    version: row?.version,
  });
  drawerVisible.value = true;
  void nextTick(() => {
    formRef.value?.clearValidate();
    initialSnapshot.value = snapshot();
  });
}

function replaceRandomCode() {
  if (form.id) return;
  form.code = generateRandomBusinessCode('partner');
  void nextTick(() => formRef.value?.validateField('code'));
}

async function beforeClose(done: () => void) {
  if (saving.value || snapshot() === initialSnapshot.value) return done();
  try {
    await ElMessageBox.confirm('合作方信息尚未保存，确定关闭吗？', '放弃修改', {
      cancelButtonText: '继续编辑',
      confirmButtonText: '放弃修改',
      type: 'warning',
    });
    done();
  } catch {
    // Keep the drawer open.
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || saving.value) return;
  saving.value = true;
  try {
    const saved = await savePartner({
      ...form,
      code: form.code.trim(),
      description: form.description?.trim() || undefined,
      name: form.name.trim(),
    });
    if (!saved) throw new Error('合作方已被其他用户修改，请刷新后重试');
    ElMessage.success(form.id ? '合作方信息已更新' : '合作方已新增');
    initialSnapshot.value = snapshot();
    drawerVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(readableError(error, '合作方保存失败，请检查后重试'));
  } finally {
    saving.value = false;
  }
}

async function deleteCurrentPartner() {
  if (!form.id || form.version === undefined || deleting.value) return;
  try {
    await ElMessageBox.confirm(
      `确定永久删除合作方“${form.name}”吗？已被产品、追踪链接或历史报表引用时将无法删除。`,
      '删除合作方',
      {
        cancelButtonText: '取消',
        confirmButtonText: '永久删除',
        type: 'error',
      },
    );
    deleting.value = true;
    await deletePartner(form.id, form.version);
    ElMessage.success('合作方已删除');
    drawerVisible.value = false;
    if (rows.value.length === 1 && current.value > 1) current.value -= 1;
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, '合作方删除失败，请重试'));
    }
  } finally {
    deleting.value = false;
  }
}

watch(mediaId, () => {
  current.value = 1;
  void load();
});

void bootstrap().then(load);
</script>

<template>
  <main class="mvp-page partners-page">
    <section class="page-heading" aria-labelledby="partner-page-title">
      <div>
        <div class="eyebrow">
          <Connection :size="16" aria-hidden="true" />
          媒体业务关系
        </div>
        <h1 id="partner-page-title">合作方</h1>
        <p>维护媒体范围内的上游业务合作方，供产品和追踪链路引用。</p>
      </div>
      <div class="media-scope-card">
        <span>当前媒体范围</span>
        <el-select
          v-model="mediaId"
          :loading="bootstrapping"
          placeholder="请选择媒体"
        >
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="`${item.name} · ${item.code}`"
            :value="item.id"
          >
            <div class="media-option">
              <span>{{ item.name }}</span>
              <span>{{ item.code }}</span>
            </div>
          </el-option>
        </el-select>
      </div>
    </section>

    <el-card v-if="media.length > 0" class="mvp-card" shadow="never">
      <div class="scope-context">
        <div>
          <strong>{{ selectedMedia?.name }}</strong>
          <span class="mvp-code">{{ selectedMedia?.code }}</span>
        </div>
        <el-tag
          effect="plain"
          :type="selectedMedia?.status === 'ACTIVE' ? 'success' : 'info'"
        >
          {{ selectedMedia?.status === 'ACTIVE' ? '媒体启用' : '媒体停用' }}
        </el-tag>
      </div>

      <div class="mvp-toolbar">
        <div class="mvp-filters" role="search" aria-label="合作方查询">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="搜索合作方名称或编码"
            :prefix-icon="Search"
            @clear="search"
            @keyup.enter="search"
          />
          <el-select
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
          v-if="canWrite"
          :icon="CirclePlus"
          type="primary"
          @click="openEditor()"
        >
          新增合作方
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        table-layout="fixed"
      >
        <el-table-column label="合作方" min-width="240">
          <template #default="{ row }">
            <div class="entity-cell">
              <strong>{{ row.name }}</strong>
              <span class="mvp-code">{{ row.code }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="业务类型" width="130">
          <template #default>
            <span>上游合作方</span>
          </template>
        </el-table-column>
        <el-table-column
          class-name="secondary-column"
          label="说明"
          min-width="260"
          prop="description"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          class-name="secondary-column"
          label="更新时间"
          min-width="180"
          prop="updateTime"
        />
        <el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="canWrite"
              :icon="EditPen"
              link
              type="primary"
              @click="openEditor(row)"
            >
              编辑
            </el-button>
            <span v-else class="read-only">只读</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="当前媒体尚无合作方" :image-size="92">
            <el-button v-if="canWrite" type="primary" @click="openEditor()">
              新增首个合作方
            </el-button>
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

    <el-card v-else v-loading="bootstrapping" class="mvp-card" shadow="never">
      <el-empty
        description="当前账号没有可访问的媒体，暂时无法管理合作方"
        :image-size="112"
      >
        <p class="empty-help">
          请先在“媒体与授权”中创建媒体或联系管理员分配媒体范围。
        </p>
      </el-empty>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :before-close="beforeClose"
      :close-on-click-modal="false"
      :title="form.id ? '编辑合作方' : '新增合作方'"
      size="min(580px, 100vw)"
    >
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <div class="drawer-intro">
          新增时自动生成合作方编码，可手动修改或换一个随机编码。合作方用于业务归属与链路引用，不会自动成为登录安全主体。
        </div>
        <el-form-item label="媒体范围" prop="mediaId">
          <el-select
            v-model="form.mediaId"
            class="w-full"
            :disabled="Boolean(form.id)"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="`${item.name} · ${item.code}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="合作方编码" prop="code">
          <el-input
            v-model="form.code"
            autocomplete="off"
            :disabled="Boolean(form.id)"
            maxlength="64"
            placeholder="例如 upstream-main"
          >
            <template v-if="!form.id" #append>
              <el-button :icon="Refresh" @click="replaceRandomCode">
                换一个
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="合作方名称" prop="name">
          <el-input
            v-model="form.name"
            autocomplete="off"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input model-value="上游合作方" readonly />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input
            v-model="form.description"
            :rows="4"
            maxlength="500"
            placeholder="补充业务关系、联系人职责或使用范围"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
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
            @click="deleteCurrentPartner"
          >
            删除合作方
          </el-button>
          <span v-else></span>
          <div>
            <el-button
              :disabled="saving || deleting"
              @click="drawerVisible = false"
            >
              取消
            </el-button>
            <el-button
              :disabled="deleting"
              :loading="saving"
              type="primary"
              @click="submit"
            >
              保存合作方
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.page-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 380px);
  gap: 24px;
  align-items: end;
  margin-bottom: 18px;
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

.eyebrow {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  font-size: 12px;
  font-weight: 650;
  color: var(--mvp-accent);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.media-scope-card {
  display: grid;
  gap: 7px;
}

.media-scope-card > span {
  font-size: 12px;
  font-weight: 620;
  color: var(--mvp-muted);
}

.media-option,
.scope-context {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.media-option span:last-child {
  font-family: SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--mvp-muted);
}

.scope-context {
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.scope-context > div,
.entity-cell {
  display: grid;
  gap: 3px;
}

.mvp-filters :deep(.el-input) {
  width: 260px;
}

.mvp-filters :deep(.el-select) {
  width: 140px;
}

.entity-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 620;
  white-space: nowrap;
}

.read-only {
  font-size: 12px;
  color: var(--mvp-muted);
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-intro {
  padding: 14px 16px;
  margin-bottom: 20px;
  line-height: 1.6;
  color: var(--mvp-muted);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

:deep(.el-button:focus-visible),
:deep(.el-input__wrapper:focus-within),
:deep(.el-select__wrapper:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

@media (max-width: 767px) {
  .page-heading {
    grid-template-columns: 1fr;
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
