<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { MediaMacroGroup, MediaMacroOption } from './media-macros';

import type { EntityOption, Media } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createMedia,
  deleteMedia,
  getMediaMacros,
  listMedia,
  mediaOptions,
  updateMedia,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import RtaConfigurationDrawer from '../rta/configuration-drawer.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';
import {
  AUTO_MACRO,
  AUTO_MACRO_KEY,
  buildMediaMacroGroups,
  CAID_ADVANCED_KEYS,
  CAID_ALIAS_KEYS,
  compatibilityMacroItems,
  parseMediaMacroParams,
  serializeMediaMacroParams,
} from './media-macros';

import '../table.scss';

defineOptions({ name: 'OcpxMediaManagement' });
const router = useRouter();

const MACRO_GROUPS = ref<MediaMacroGroup[]>(buildMediaMacroGroups());
const COMPAT_MACRO_ITEMS = ref<MediaMacroOption[]>(compatibilityMacroItems());

const columns = [
  { key: 'id', label: '渠道 ID' },
  { key: 'name', label: '渠道名称' },
  { key: 'uri', label: 'URI' },
  { key: 'credential', label: '凭据引用' },
  { key: 'macro', label: '宏参数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<Media[]>([]);
const mediaSelects = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ current: 1, mediaId: '', size: 30 });
const dialogOpen = ref(false);
const editing = ref<Media>();
const formRef = ref<FormInstance>();
const form = reactive({
  credentialConfigKey: '',
  customMacros: '',
  legacyMacros: [] as string[],
  mediaName: '',
  selectedMacros: [] as string[],
  uri: '/feedback',
});
const rules: FormRules = {
  mediaName: [{ message: '请输入渠道名称', required: true, trigger: 'blur' }],
  uri: [{ message: '请输入 URI', required: true, trigger: 'blur' }],
};
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes(['ocpx:media:edit', 'ocpx:media:delete']),
);
const loadGuard = createLatestRequestGuard();

const macroParams = computed(() => {
  return serializeMediaMacroParams(
    MACRO_GROUPS.value,
    form.selectedMacros,
    form.legacyMacros,
    form.customMacros,
    COMPAT_MACRO_ITEMS.value,
  );
});
const macroPreviewParams = computed(() =>
  [`${AUTO_MACRO_KEY}=${AUTO_MACRO}`, macroParams.value]
    .filter(Boolean)
    .join('&'),
);

function parseMacroParams(raw?: null | string) {
  return parseMediaMacroParams(raw, MACRO_GROUPS.value);
}

const newMacroKeys = () => ['callback_url'];

function isMacroSelected(key: string) {
  if (form.selectedMacros.includes(key)) return true;
  return form.legacyMacros.some((pair) => {
    const separator = pair.indexOf('=');
    const legacyKey = (
      separator === -1 ? pair : pair.slice(0, separator)
    ).trim();
    return (
      legacyKey === key || (key === 'caid' && CAID_ALIAS_KEYS.has(legacyKey))
    );
  });
}

function toggleMacro(key: string, value: unknown) {
  const checked = value === true;
  const selected = new Set(form.selectedMacros);
  if (checked) selected.add(key);
  else selected.delete(key);

  // Touching the canonical CAID field upgrades old aliases to the canonical
  // key. Until the user touches it, old media keeps its exact alias pair.
  if (key === 'caid') {
    form.legacyMacros = form.legacyMacros.filter((pair) => {
      const separator = pair.indexOf('=');
      const legacyKey = (
        separator === -1 ? pair : pair.slice(0, separator)
      ).trim();
      return !CAID_ALIAS_KEYS.has(legacyKey);
    });
  }
  if (CAID_ADVANCED_KEYS.has(key)) {
    form.legacyMacros = form.legacyMacros.filter((pair) => {
      const separator = pair.indexOf('=');
      const legacyKey = (
        separator === -1 ? pair : pair.slice(0, separator)
      ).trim();
      return legacyKey !== key;
    });
  }
  form.selectedMacros = [...selected];
}

function selectVariant(item: MediaMacroOption, value: unknown) {
  toggleMacro(item.key, value);
}

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listMedia({
      current: query.current,
      mediaId: query.mediaId || undefined,
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

function edit(row?: Media) {
  editing.value = row;
  const parsed = parseMacroParams(row?.macroParams);
  Object.assign(form, {
    credentialConfigKey: row?.credentialConfigKey || '',
    customMacros: row ? parsed.custom : '',
    legacyMacros: row ? parsed.legacy : [],
    mediaName: row?.mediaName || '',
    selectedMacros: row ? parsed.selected : newMacroKeys(),
    uri: row?.uri || '/feedback',
  });
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const editingId = editing.value?.id;
    const payload = {
      credentialConfigKey: form.credentialConfigKey || undefined,
      macroParams: macroParams.value,
      mediaName: form.mediaName,
      uri: form.uri,
    };
    await (editingId ? updateMedia(editingId, payload) : createMedia(payload));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    mediaSelects.value = await mediaOptions();
    await load();
  } catch {
    // 请求拦截器已统一提示错误，这里吞掉避免未捕获的 Promise 拒绝
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, {
    credentialConfigKey: '',
    customMacros: '',
    legacyMacros: [],
    mediaName: '',
    selectedMacros: newMacroKeys(),
    uri: '/feedback',
  });
}

async function remove(row: Media) {
  await ElMessageBox.confirm(
    '仅未被监测任务、扣量规则或历史数据引用的媒体可删除。确认继续？',
    '删除媒体',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deleteMedia(row.id);
    ElMessage.success('删除成功');
    mediaSelects.value = await mediaOptions();
    if (query.mediaId === row.id) query.mediaId = '';
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  const macros = await getMediaMacros();
  MACRO_GROUPS.value = buildMediaMacroGroups(macros);
  COMPAT_MACRO_ITEMS.value = compatibilityMacroItems(macros);
  mediaSelects.value = await mediaOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="媒体管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.mediaId"
          aria-label="按媒体查询"
          clearable
          filterable
          placeholder="请选择渠道"
        >
          <el-option
            v-for="item in mediaSelects"
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
          v-access:code="'ocpx:media:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
          >新增媒体</el-button
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
      <el-alert
        class="mb-4"
        :closable="false"
        title="管理端只维护服务端凭据引用，不显示或提交明文密钥。"
        type="info"
      />
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column v-if="hasColumn('id')" label="渠道 ID" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('name')"
          label="渠道名称"
          min-width="150"
          prop="mediaName"
        />
        <el-table-column v-if="hasColumn('uri')" label="URI" min-width="200"
          ><template #default="{ row }"><LongText :value="row.uri" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('credential')"
          label="凭据引用"
          min-width="180"
          ><template #default="{ row }"
            ><LongText :value="row.credentialConfigKey" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('macro')"
          label="宏参数"
          min-width="220"
          ><template #default="{ row }"
            ><LongText :value="row.macroParams" /></template
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
          v-if="canOperate || hasAccessByCodes(['ocpx:rta:media:view'])"
          fixed="right"
          label="操作"
          width="130"
        >
          <template #default="{ row }">
            <el-button
              v-if="hasAccessByCodes(['ocpx:rta:media:view'])"
              link
              type="primary"
              @click="
                router.push({
                  query: { rtaMediaId: row.id },
                })
              "
              >RTA 配置</el-button
            >
            <el-button
              v-access:code="'ocpx:media:edit'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-access:code="'ocpx:media:delete'"
              :disabled="saving || Boolean(deletingId)"
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
      class="media-dialog"
      :title="editing ? '编辑媒体' : '新增媒体'"
      top="4vh"
      width="900px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form media-dialog-form"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <div class="media-base-grid">
          <el-form-item label="渠道名称" prop="mediaName">
            <el-input v-model="form.mediaName" placeholder="请输入渠道名称" />
          </el-form-item>
          <el-form-item label="URI" prop="uri">
            <el-input v-model="form.uri" placeholder="请输入 URI" />
          </el-form-item>
          <el-form-item label="凭据引用" prop="credentialConfigKey">
            <el-input
              v-model="form.credentialConfigKey"
              placeholder="请输入凭据引用"
            />
          </el-form-item>
        </div>
        <el-form-item label="宏参数">
          <div class="macro-picker">
            <div class="macro-system-row">
              <el-checkbox :model-value="true" disabled>
                <span class="macro-label">请求 ID</span>
                <code class="macro-value"
                  >{{ AUTO_MACRO_KEY }}={{ AUTO_MACRO }}</code
                >
              </el-checkbox>
              <span class="macro-system-badge">系统自动</span>
            </div>
            <template v-for="group in MACRO_GROUPS" :key="group.name">
              <section class="macro-group-card">
                <div class="macro-group-title">{{ group.name }}</div>
                <div class="macro-group">
                  <template v-for="item in group.items" :key="item.key">
                    <div v-if="item.variants" class="macro-variant-item">
                      <div class="macro-variant-label">
                        <span class="macro-label">{{ item.label }}</span>
                      </div>
                      <div class="macro-variants">
                        <el-checkbox
                          v-for="variant in item.variants"
                          :key="variant.key"
                          :model-value="isMacroSelected(variant.key)"
                          @change="selectVariant(variant, $event)"
                        >
                          <span class="macro-label">{{ variant.label }}</span>
                          <code class="macro-value"
                            >{{ variant.key }}={{ variant.macro }}</code
                          >
                        </el-checkbox>
                      </div>
                    </div>
                    <el-checkbox
                      v-else
                      class="macro-option"
                      :model-value="isMacroSelected(item.key)"
                      @change="toggleMacro(item.key, $event)"
                    >
                      <span class="macro-label">{{ item.label }}</span>
                      <code class="macro-value"
                        >{{ item.key }}={{ item.macro }}</code
                      >
                    </el-checkbox>
                  </template>
                </div>
              </section>
            </template>
            <el-collapse
              v-if="COMPAT_MACRO_ITEMS.length > 0"
              class="macro-compat"
            >
              <el-collapse-item title="更多兼容字段" name="compat">
                <div class="macro-group macro-compat-group">
                  <el-checkbox
                    v-for="item in COMPAT_MACRO_ITEMS"
                    :key="item.key"
                    class="macro-option"
                    :model-value="isMacroSelected(item.key)"
                    @change="toggleMacro(item.key, $event)"
                  >
                    <span class="macro-label">{{ item.label }}</span>
                    <code class="macro-value"
                      >{{ item.key }}={{ item.macro }}</code
                    >
                  </el-checkbox>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
          <div class="macro-preview">
            <span class="macro-preview-label">预览</span>
            <code class="macro-preview-text">{{
              macroPreviewParams || '（未选择任何宏参数）'
            }}</code>
          </div>
          <el-input
            v-model="form.customMacros"
            class="macro-custom-input"
            autosize
            placeholder="自定义参数：key=__MACRO__，多个用 & 连接"
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        ><el-button :loading="saving" type="primary" @click="save"
          >确定</el-button
        ></template
      >
    </el-dialog>
    <RtaConfigurationDrawer kind="media" @saved="load" />
  </Page>
</template>

<style scoped>
.media-base-grid {
  display: grid;
  grid-template-columns: 1.25fr 1fr 1fr;
  gap: 0 16px;
}

.media-base-grid :deep(.el-form-item) {
  margin-bottom: 14px;
}

.media-dialog-form > :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.macro-picker {
  width: 100%;
}

.macro-system-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  padding: 8px 10px;
  margin-bottom: 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.macro-system-badge {
  padding: 2px 6px;
  font-size: 11px;
  line-height: 1.4;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 3px;
}

.macro-group-card {
  padding: 10px 12px 8px;
  margin-top: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.macro-group-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.macro-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

.macro-variant-item {
  min-width: 0;
  padding: 4px 6px;
  background: var(--el-fill-color-lighter);
  border-radius: 5px;
}

.macro-variant-label {
  display: flex;
  align-items: center;
  min-height: 24px;
  font-size: 13px;
  font-weight: 600;
}

.macro-variants {
  display: grid;
  gap: 2px;
  padding-left: 2px;
}

.macro-option,
.macro-variants :deep(.el-checkbox) {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  padding: 5px 6px;
  margin: 0;
  border-radius: 5px;
}

.macro-option:hover,
.macro-variants :deep(.el-checkbox:hover) {
  background: var(--el-fill-color-light);
}

.macro-option :deep(.el-checkbox__label),
.macro-variants :deep(.el-checkbox__label) {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 2px 7px;
  align-items: baseline;
  padding-left: 6px;
  line-height: 1.4;
}

.macro-compat {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.macro-compat :deep(.el-collapse-item__header) {
  height: 36px;
  padding: 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-bottom: 0;
}

.macro-compat :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
}

.macro-compat :deep(.el-collapse-item__content) {
  padding: 0 12px 10px;
}

.macro-compat-group {
  gap: 4px 18px;
}

.macro-label {
  white-space: nowrap;
}

.macro-value {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.macro-preview {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 8px 10px;
  margin: 10px 0 8px;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.macro-preview-label {
  flex: 0 0 auto;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.macro-preview-text {
  font-size: 11px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.macro-custom-input {
  margin-top: 2px;
}

:deep(.media-dialog .el-dialog__body) {
  max-height: calc(100vh - 190px);
  padding: 8px 22px 4px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

:deep(.media-dialog .el-dialog__header) {
  padding: 18px 22px 10px;
}

:deep(.media-dialog .el-dialog__footer) {
  padding: 12px 22px 18px;
}

@media (max-width: 820px) {
  .media-base-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .media-base-grid :deep(.el-form-item:last-child) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .media-base-grid,
  .macro-group {
    grid-template-columns: minmax(0, 1fr);
  }

  .media-base-grid :deep(.el-form-item:last-child) {
    grid-column: auto;
  }

  :deep(.media-dialog) {
    width: calc(100vw - 24px) !important;
  }
}
</style>
