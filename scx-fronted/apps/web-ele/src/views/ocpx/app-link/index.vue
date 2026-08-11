<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { AppLinkEditor } from './app-link-model';

import type {
  AppLinkDetail,
  AppLinkDocumentOption,
  AppLinkProductOption,
  AppLinkRecord,
  AppLinkValidationReport,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import {
  CircleCheck,
  EditPen,
  FolderDelete,
  Link,
  Plus,
  Refresh,
  Search,
  SwitchButton,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveAppLink,
  changeAppLinkStatus,
  completeAppLinkMapping,
  createAppLink,
  getAppLink,
  getAppLinkDocumentOptions,
  getAppLinkProductOptions,
  getAppLinks,
  restoreAppLink,
  updateAppLink,
  validateAppLinkUrl,
} from '#/api/ocpx';

import {
  appLinkEditorSnapshot,
  appLinkRedirectIssue,
  buildAppLinkCreatePayload,
  buildAppLinkUpdatePayload,
  buildLegacyMappingPayload,
  resolveAppLinkEmptyState,
} from './app-link-model';

defineOptions({ name: 'OcpxAppLinkManagement' });

const { hasAccessByCodes } = useAccess();
const canView = computed(() => hasAccessByCodes(['ocpx:app-link:view']));
const canAdd = computed(() => hasAccessByCodes(['ocpx:app-link:add']));
const canEdit = computed(() => hasAccessByCodes(['ocpx:app-link:edit']));
const canStatus = computed(() => hasAccessByCodes(['ocpx:app-link:status']));
const canArchive = computed(() => hasAccessByCodes(['ocpx:app-link:archive']));
const canValidate = computed(() =>
  hasAccessByCodes(['ocpx:app-link:validate']),
);

const filters = reactive({
  archived: false,
  keyword: '',
  linkType: '',
  mappingStatus: '',
  productId: '',
  status: '',
});
const current = ref(1);
const size = ref(20);
const total = ref(0);
const rows = ref<AppLinkRecord[]>([]);
const products = ref<AppLinkProductOption[]>([]);
const loading = ref(false);
const loadFailed = ref(false);

const filtersActive = computed(
  () =>
    filters.archived ||
    Boolean(filters.keyword) ||
    Boolean(filters.linkType) ||
    Boolean(filters.mappingStatus) ||
    Boolean(filters.productId) ||
    Boolean(filters.status),
);
const emptyState = computed(() =>
  resolveAppLinkEmptyState({
    canView: canView.value,
    error: loadFailed.value,
    filtersActive: filtersActive.value,
    total: total.value,
  }),
);
const pendingCount = computed(
  () =>
    rows.value.filter((row) => row.mappingStatus === 'LEGACY_PENDING_PRODUCT')
      .length,
);

const editorOpen = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
const editing = ref<AppLinkDetail>();
const documents = ref<AppLinkDocumentOption[]>([]);
const documentsLoading = ref(false);
const idempotencyKey = ref('');
const savedSnapshot = ref('');
const conflictMessage = ref('');
const formError = ref('');
const formRef = ref<FormInstance>();
const validation = reactive<
  Partial<Record<'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE', AppLinkValidationReport>>
>({});
const validating = reactive({
  DEEPLINK: false,
  DOWNLOAD: false,
  TEMPLATE: false,
});
const form = reactive<AppLinkEditor>({
  clearDeeplink: false,
  clearDownload: false,
  deeplinkUrl: '',
  description: '',
  documentId: '',
  downloadUrl: '',
  linkName: '',
  linkType: 'ANDROID',
  linkUrlTemplate: '',
  productId: '',
  redirectEnabled: false,
});

const selectedProduct = computed(() =>
  products.value.find((product) => product.id === form.productId),
);
const pendingMapping = computed(
  () => editing.value?.mappingStatus === 'LEGACY_PENDING_PRODUCT',
);
const productLocked = computed(
  () => Boolean(editing.value) && editing.value?.mappingStatus === 'READY',
);
const rules: FormRules = {
  documentId: [{ message: '请选择已通过安全检查的接入资料', required: true }],
  linkName: [
    { max: 64, message: '链接名称不能超过 64 个字符', trigger: 'blur' },
    { message: '请输入链接名称', required: true, trigger: 'blur' },
  ],
  linkType: [{ message: '请选择链接类型', required: true }],
  productId: [{ message: '请选择启用且在对象范围内的产品', required: true }],
};

async function loadOptions() {
  if (!canView.value) return;
  products.value = await getAppLinkProductOptions();
}

async function load() {
  if (!canView.value) return;
  loading.value = true;
  loadFailed.value = false;
  try {
    const result = await getAppLinks({
      archived: filters.archived || undefined,
      current: current.value,
      keyword: filters.keyword.trim() || undefined,
      linkType: filters.linkType || undefined,
      mappingStatus: filters.mappingStatus || undefined,
      productId: filters.productId || undefined,
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
    keyword: '',
    linkType: '',
    mappingStatus: '',
    productId: '',
    status: '',
  });
  await search();
}

function resetValidation() {
  delete validation.DEEPLINK;
  delete validation.DOWNLOAD;
  delete validation.TEMPLATE;
}

function resetEditor() {
  editing.value = undefined;
  documents.value = [];
  conflictMessage.value = '';
  formError.value = '';
  resetValidation();
  Object.assign(form, {
    clearDeeplink: false,
    clearDownload: false,
    deeplinkUrl: '',
    description: '',
    documentId: '',
    downloadUrl: '',
    linkName: '',
    linkType: 'ANDROID',
    linkUrlTemplate: '',
    productId: products.value[0]?.id || '',
    redirectEnabled: false,
  });
}

async function loadDocuments(productId: string, keepDocument = false) {
  documents.value = [];
  if (!productId) {
    form.documentId = '';
    return;
  }
  documentsLoading.value = true;
  try {
    documents.value = await getAppLinkDocumentOptions(productId);
    if (
      !keepDocument ||
      !documents.value.some((item) => item.id === form.documentId)
    ) {
      form.documentId =
        documents.value.find((item) => item.current)?.id ||
        documents.value[0]?.id ||
        '';
    }
  } catch {
    form.documentId = '';
    ElMessage.warning('接入资料加载失败，请稍后重试');
  } finally {
    documentsLoading.value = false;
  }
}

async function productChanged() {
  resetValidation();
  await loadDocuments(form.productId);
}

async function openCreate() {
  if (products.value.length === 0) {
    ElMessage.warning('当前没有可操作的启用产品');
    return;
  }
  resetEditor();
  await loadDocuments(form.productId);
  idempotencyKey.value = crypto.randomUUID().replaceAll('-', '');
  savedSnapshot.value = appLinkEditorSnapshot(form);
  editorOpen.value = true;
}

async function openEdit(row: AppLinkRecord) {
  editorOpen.value = true;
  editorLoading.value = true;
  conflictMessage.value = '';
  formError.value = '';
  resetValidation();
  try {
    const detail = await getAppLink(row.id);
    editing.value = detail;
    Object.assign(form, {
      clearDeeplink: false,
      clearDownload: false,
      deeplinkUrl: '',
      description: detail.description || '',
      documentId: detail.documentId || '',
      downloadUrl: '',
      linkName: detail.linkName,
      linkType: detail.linkType || 'ANDROID',
      linkUrlTemplate: '',
      productId: detail.productId || '',
      redirectEnabled: detail.redirectEnabled,
    });
    if (detail.productId) await loadDocuments(detail.productId, true);
    savedSnapshot.value = appLinkEditorSnapshot(form);
  } catch {
    editorOpen.value = false;
    ElMessage.error('链接详情加载失败');
  } finally {
    editorLoading.value = false;
  }
}

async function beforeEditorClose(done: () => void) {
  if (saving.value) return;
  if (appLinkEditorSnapshot(form) === savedSnapshot.value) {
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

function urlValue(kind: 'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE') {
  if (kind === 'TEMPLATE') return form.linkUrlTemplate;
  if (kind === 'DEEPLINK') return form.deeplinkUrl;
  return form.downloadUrl;
}

async function preflightUrl(kind: 'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE') {
  const value = urlValue(kind).trim();
  if (!value) {
    ElMessage.warning('请先输入要预检的新地址');
    return;
  }
  validating[kind] = true;
  try {
    validation[kind] = await validateAppLinkUrl({
      kind,
      linkType: form.linkType,
      url: value,
    });
  } catch (error) {
    ElMessage.error(errorText(error));
  } finally {
    validating[kind] = false;
  }
}

function resetFieldValidation(kind: 'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE') {
  delete validation[kind];
}

function validateEditor() {
  const replacingTemplate = Boolean(form.linkUrlTemplate.trim());
  if ((!editing.value || pendingMapping.value) && !replacingTemplate) {
    formError.value = '新建或补映射时必须重新输入链接模板。';
    return false;
  }
  const redirectIssue = appLinkRedirectIssue(form);
  if (redirectIssue) {
    formError.value = redirectIssue;
    return false;
  }
  const enteredKinds = (['TEMPLATE', 'DEEPLINK', 'DOWNLOAD'] as const).filter(
    (kind) => urlValue(kind).trim(),
  );
  const blocked = enteredKinds.find(
    (kind) => validation[kind]?.status === 'BLOCK',
  );
  if (blocked) {
    formError.value = '存在未通过 URL 预检的字段，请修正后再保存。';
    return false;
  }
  formError.value = '';
  return true;
}

async function saveLink() {
  if (!(await formRef.value?.validate()) || !validateEditor()) return;
  const completingLegacyMapping = pendingMapping.value;
  saving.value = true;
  conflictMessage.value = '';
  try {
    let saved: AppLinkDetail;
    if (editing.value) {
      saved = pendingMapping.value
        ? await completeAppLinkMapping(
            editing.value.id,
            buildLegacyMappingPayload(form, editing.value),
          )
        : await updateAppLink(
            editing.value.id,
            buildAppLinkUpdatePayload(form, editing.value),
          );
    } else {
      saved = await createAppLink(
        buildAppLinkCreatePayload(form),
        idempotencyKey.value,
      );
    }
    editing.value = saved;
    savedSnapshot.value = appLinkEditorSnapshot(form);
    editorOpen.value = false;
    ElMessage.success(
      completingLegacyMapping ? '存量链接已完成显式补映射' : '链接已保存',
    );
    await load();
  } catch (error: any) {
    if (isConflict(error)) conflictMessage.value = errorText(error);
    else formError.value = errorText(error);
  } finally {
    saving.value = false;
  }
}

function requestEditorClose() {
  beforeEditorClose(() => {
    editorOpen.value = false;
  });
}

async function toggleStatus(row: AppLinkRecord) {
  const status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  try {
    const { value } = await ElMessageBox.prompt(
      status === 'ACTIVE'
        ? '启用前服务端会重新检查 Product、广告主和已保存 URL 元数据。'
        : '禁用不会改写已发布快照，但会标记依赖路由待重发。',
      status === 'ACTIVE' ? '启用链接' : '禁用链接',
      { inputPlaceholder: '填写原因（可选）', type: 'warning' },
    );
    await changeAppLinkStatus(row.id, row.configVersion, status, value || '');
    ElMessage.success(status === 'ACTIVE' ? '链接已启用' : '链接已禁用');
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

async function changeArchive(row: AppLinkRecord) {
  const restoring = row.status === 'ARCHIVED';
  try {
    const detail = await getAppLink(row.id);
    const { value } = await ElMessageBox.prompt(
      restoring
        ? '恢复后链接回到禁用状态，需要人工复核后再启用。'
        : `归档前会执行引用保护。当前路由 ${detail.references.monitorCount} 条、扣量规则 ${detail.references.deductionRuleCount} 条。`,
      restoring ? '恢复链接' : '归档链接',
      { inputPlaceholder: '填写原因（可选）', type: 'warning' },
    );
    await (restoring
      ? restoreAppLink(row.id, detail.configVersion, value || '')
      : archiveAppLink(row.id, detail.configVersion, value || ''));
    ElMessage.success(restoring ? '链接已恢复为禁用' : '链接已归档');
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

function isConflict(error: any) {
  return (
    error?.response?.status === 409 ||
    error?.response?.data?.code === 409 ||
    error?.data?.code === 409
  );
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

function statusLabel(status: AppLinkRecord['status']) {
  if (status === 'ACTIVE') return '启用';
  if (status === 'ARCHIVED') return '已归档';
  return '禁用';
}

function statusType(status: AppLinkRecord['status']) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'ARCHIVED') return 'info';
  return 'warning';
}

function validationLabel(kind: 'DEEPLINK' | 'DOWNLOAD' | 'TEMPLATE') {
  const result = validation[kind];
  if (!result) return '规则预检';
  return result.status === 'PASS' ? '已通过' : '未通过';
}

onMounted(async () => {
  if (!canView.value) return;
  try {
    await loadOptions();
  } catch {
    ElMessage.warning('产品筛选项加载失败，列表仍可继续使用');
  }
  await load();
});
</script>

<template>
  <div class="p-5">
    <el-card class="mb-4" shadow="never">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <el-icon class="text-primary"><Link /></el-icon>
            <h2 class="m-0 text-lg font-semibold">链接管理</h2>
          </div>
          <p class="mb-0 mt-2 max-w-3xl text-sm text-gray-500">
            AppLink 明确归属 Product，并冻结资料版本、链接类型和规范化 URL。
            页面不会打开、探测或复制完整目标地址。
          </p>
        </div>
        <el-button v-if="canAdd" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增链接
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="pendingCount > 0"
      class="mb-4"
      :closable="false"
      show-icon
      title="发现待补 Product 映射的存量链接"
      type="warning"
    >
      当前页有
      {{ pendingCount }}
      条。系统不会猜测归属，也不会沿用旧地址；需人工选择同一广告主下的
      Product、接入资料并重新录入 URL。
    </el-alert>

    <el-card shadow="never">
      <el-form class="mb-4" :inline="true" @submit.prevent="search">
        <el-form-item>
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="名称 / 编码 / 描述"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="filters.productId"
            clearable
            placeholder="全部产品"
            style="width: 220px"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.name} · ${product.advertiserName}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="filters.linkType"
            clearable
            placeholder="全部类型"
            style="width: 130px"
          >
            <el-option label="Android" value="ANDROID" />
            <el-option label="iOS" value="IOS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="filters.mappingStatus"
            clearable
            placeholder="映射状态"
            style="width: 180px"
          >
            <el-option label="已结构化" value="READY" />
            <el-option
              label="待补 Product 映射"
              value="LEGACY_PENDING_PRODUCT"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!filters.archived">
          <el-select
            v-model="filters.status"
            clearable
            placeholder="全部状态"
            style="width: 130px"
          >
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="filters.archived">查看已归档</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"
            ><el-icon><Search /></el-icon>查询</el-button
          >
          <el-button @click="resetFilters"
            ><el-icon><Refresh /></el-icon>重置</el-button
          >
        </el-form-item>
      </el-form>

      <el-result
        v-if="emptyState === 'NO_PERMISSION'"
        icon="warning"
        sub-title="请联系管理员授予 ocpx:app-link:view 权限"
        title="无链接管理权限"
      />
      <el-result
        v-else-if="emptyState === 'ERROR'"
        icon="error"
        sub-title="未使用缓存数据，请重试获取最新结果"
        title="链接列表加载失败"
      >
        <template #extra
          ><el-button type="primary" @click="load"
            >重新加载</el-button
          ></template
        >
      </el-result>
      <el-empty
        v-else-if="emptyState === 'INITIAL_EMPTY' && !loading"
        description="尚未创建结构化链接"
      >
        <el-button v-if="canAdd" type="primary" @click="openCreate"
          >创建第一条链接</el-button
        >
      </el-empty>
      <el-empty
        v-else-if="emptyState === 'FILTER_EMPTY' && !loading"
        description="当前筛选条件下没有链接"
      >
        <el-button @click="resetFilters">清除筛选</el-button>
      </el-empty>

      <el-table v-else v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="链接" min-width="220">
          <template #default="{ row }">
            <div class="font-medium">{{ row.linkName }}</div>
            <div class="mt-1 text-xs text-gray-400">
              {{ row.bizCode || row.id }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="归属" min-width="220">
          <template #default="{ row }">
            <div>{{ row.productName || '待补 Product 映射' }}</div>
            <div class="mt-1 text-xs text-gray-400">
              {{ row.advertiserName }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结构" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.mappingStatus === 'READY'" type="success"
              >已结构化</el-tag
            >
            <el-tag v-else type="warning">待人工补映射</el-tag>
            <div class="mt-2 text-xs text-gray-500">
              {{ row.linkType || '类型待确认' }} · v{{ row.configVersion }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="安全展示" min-width="220">
          <template #default="{ row }">
            <div class="truncate text-sm">
              {{ row.templateMasked || '旧地址未升格' }}
            </div>
            <div class="mt-1 text-xs text-gray-400">
              {{ row.templateHost || '主机待复核' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{
              statusLabel(row.status)
            }}</el-tag>
            <el-badge
              v-if="row.republishRequiredCount"
              class="ml-2"
              :value="row.republishRequiredCount"
            />
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="270">
          <template #default="{ row }">
            <el-button
              v-if="canEdit && row.status !== 'ARCHIVED'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              <el-icon><EditPen /></el-icon
              >{{ row.mappingStatus === 'READY' ? '编辑' : '补映射' }}
            </el-button>
            <el-button
              v-if="canStatus && row.status !== 'ARCHIVED'"
              :disabled="row.mappingStatus !== 'READY'"
              link
              type="primary"
              @click="toggleStatus(row)"
            >
              <el-icon><SwitchButton /></el-icon
              >{{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
            <el-button
              v-if="canArchive"
              link
              type="danger"
              @click="changeArchive(row)"
            >
              <el-icon><FolderDelete /></el-icon
              >{{ row.status === 'ARCHIVED' ? '恢复' : '归档' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="editorOpen"
      :before-close="beforeEditorClose"
      destroy-on-close
      :title="
        pendingMapping ? '补全存量链接映射' : editing ? '编辑链接' : '新增链接'
      "
      width="760px"
    >
      <div v-loading="editorLoading">
        <el-alert
          v-if="pendingMapping"
          class="mb-4"
          :closable="false"
          show-icon
          title="此存量链接没有可信 Product 归属"
          type="warning"
        >
          请选择原广告主下的 Product，并重新输入所有
          URL。完成后保持禁用，不会自动发布。
        </el-alert>
        <el-alert
          v-if="conflictMessage"
          class="mb-4"
          :closable="false"
          show-icon
          :title="conflictMessage"
          type="error"
        >
          数据已被其他操作修改。关闭编辑器并刷新列表后再提交，避免覆盖新版本。
        </el-alert>
        <el-alert
          v-if="formError"
          class="mb-4"
          :closable="false"
          show-icon
          :title="formError"
          type="error"
        />

        <el-form
          ref="formRef"
          label-position="top"
          :model="form"
          :rules="rules"
        >
          <div class="grid grid-cols-2 gap-x-4">
            <el-form-item label="Product" prop="productId">
              <el-select
                v-model="form.productId"
                :disabled="productLocked"
                filterable
                placeholder="选择 Product"
                @change="productChanged"
              >
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="`${product.name} · ${product.advertiserName}`"
                  :value="product.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="所属广告主">
              <el-input
                :model-value="
                  selectedProduct?.advertiserName ||
                  editing?.advertiserName ||
                  '随 Product 确定'
                "
                disabled
              />
            </el-form-item>
            <el-form-item label="接入资料 revision" prop="documentId">
              <el-select
                v-model="form.documentId"
                :loading="documentsLoading"
                placeholder="选择 CLEAN 资料"
              >
                <el-option
                  v-for="document in documents"
                  :key="document.id"
                  :label="`${document.fileName} · v${document.version}${document.current ? ' · 当前' : ''}`"
                  :value="document.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="链接类型" prop="linkType">
              <el-select v-model="form.linkType" @change="resetValidation">
                <el-option label="Android" value="ANDROID" />
                <el-option label="iOS" value="IOS" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="链接名称" prop="linkName">
              <el-input
                v-model="form.linkName"
                maxlength="64"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-tag :type="editing?.status === 'ACTIVE' ? 'success' : 'info'">
                {{ editing?.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
              <span class="ml-2 text-xs text-gray-400">
                新建和补映射均为禁用；请在列表使用独立状态权限启用。
              </span>
            </el-form-item>
          </div>

          <el-divider content-position="left">结构化 URL</el-divider>
          <el-alert class="mb-4" :closable="false" show-icon type="info">
            规则预检只检查协议、主机、userinfo、私网/保留地址、规范化和宏；networkCalled=false，绝不访问目标地址。
          </el-alert>

          <el-form-item label="链接模板 URL">
            <template #label>
              <span>链接模板 URL</span>
              <span
                v-if="editing?.templateConfigured"
                class="ml-2 text-xs text-gray-400"
              >
                已保存：{{ editing.templateMasked }}；留空保持原值
              </span>
            </template>
            <el-input
              v-model="form.linkUrlTemplate"
              :placeholder="
                editing?.templateConfigured && !pendingMapping
                  ? '留空保持原值'
                  : 'https://public.example/path?clickid=__CLICKID__'
              "
              type="textarea"
              @input="resetFieldValidation('TEMPLATE')"
            />
            <div class="mt-2 flex items-center gap-2">
              <el-button
                v-if="canValidate"
                :loading="validating.TEMPLATE"
                size="small"
                @click="preflightUrl('TEMPLATE')"
                ><el-icon><CircleCheck /></el-icon
                >{{ validationLabel('TEMPLATE') }}</el-button
              >
              <el-tag
                v-if="validation.TEMPLATE"
                :type="
                  validation.TEMPLATE.status === 'PASS' ? 'success' : 'danger'
                "
              >
                {{ validation.TEMPLATE.status }} ·
                {{
                  validation.TEMPLATE.host ||
                  validation.TEMPLATE.issues.join(', ')
                }}
              </el-tag>
            </div>
          </el-form-item>

          <el-form-item label="重定向策略">
            <el-switch
              v-model="form.redirectEnabled"
              active-text="启用，至少配置一个目标"
              inactive-text="关闭"
            />
          </el-form-item>

          <el-form-item label="直达 URL">
            <template #label>
              <span>直达 URL</span>
              <span
                v-if="editing?.deeplinkConfigured"
                class="ml-2 text-xs text-gray-400"
              >
                已保存：{{ editing.deeplinkMasked }}
              </span>
            </template>
            <el-input
              v-model="form.deeplinkUrl"
              :disabled="form.clearDeeplink"
              :placeholder="
                editing?.deeplinkConfigured ? '留空保持原值' : '可选'
              "
              @input="resetFieldValidation('DEEPLINK')"
            />
            <div class="mt-2 flex items-center gap-2">
              <el-checkbox
                v-if="editing?.deeplinkConfigured && !pendingMapping"
                v-model="form.clearDeeplink"
              >
                明确清除已保存直达 URL
              </el-checkbox>
              <el-button
                v-if="canValidate"
                :disabled="form.clearDeeplink"
                :loading="validating.DEEPLINK"
                size="small"
                @click="preflightUrl('DEEPLINK')"
                >{{ validationLabel('DEEPLINK') }}</el-button
              >
              <el-tag
                v-if="validation.DEEPLINK"
                :type="
                  validation.DEEPLINK.status === 'PASS' ? 'success' : 'danger'
                "
              >
                {{ validation.DEEPLINK.status }}
              </el-tag>
            </div>
          </el-form-item>

          <el-form-item label="下载 URL">
            <template #label>
              <span>下载 URL</span>
              <span
                v-if="editing?.downloadConfigured"
                class="ml-2 text-xs text-gray-400"
              >
                已保存：{{ editing.downloadMasked }}
              </span>
            </template>
            <el-input
              v-model="form.downloadUrl"
              :disabled="form.clearDownload"
              :placeholder="
                editing?.downloadConfigured ? '留空保持原值' : '可选'
              "
              @input="resetFieldValidation('DOWNLOAD')"
            />
            <div class="mt-2 flex items-center gap-2">
              <el-checkbox
                v-if="editing?.downloadConfigured && !pendingMapping"
                v-model="form.clearDownload"
              >
                明确清除已保存下载 URL
              </el-checkbox>
              <el-button
                v-if="canValidate"
                :disabled="form.clearDownload"
                :loading="validating.DOWNLOAD"
                size="small"
                @click="preflightUrl('DOWNLOAD')"
                >{{ validationLabel('DOWNLOAD') }}</el-button
              >
              <el-tag
                v-if="validation.DOWNLOAD"
                :type="
                  validation.DOWNLOAD.status === 'PASS' ? 'success' : 'danger'
                "
              >
                {{ validation.DOWNLOAD.status }}
              </el-tag>
            </div>
          </el-form-item>

          <el-form-item label="描述">
            <el-input
              v-model="form.description"
              maxlength="500"
              show-word-limit
              type="textarea"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="requestEditorClose">取消</el-button>
        <el-button
          :disabled="Boolean(conflictMessage)"
          :loading="saving"
          type="primary"
          @click="saveLink"
        >
          {{ pendingMapping ? '确认映射并保持禁用' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
