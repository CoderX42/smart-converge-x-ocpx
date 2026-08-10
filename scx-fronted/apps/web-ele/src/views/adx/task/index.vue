<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { TaskEditor, TaskEventBindingEditor } from './task-model';

import type {
  RouteDistribution,
  RoutePublishCheck,
  RouteVersion,
  TaskDetail,
  TaskEventBindingOptions,
  TaskOptions,
  TaskRecord,
} from '#/api/adx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import {
  EditPen,
  FolderDelete,
  Plus,
  Refresh,
  Search,
  SetUp,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveTask,
  changeTaskStatus,
  copyTaskRouteAddress,
  createTask,
  getTask,
  getTaskDistribution,
  getTaskEventBindingOptions,
  getTaskEventBindings,
  getTaskOptions,
  getTaskPublishCheck,
  getTaskRouteVersions,
  getTasks,
  pauseTaskRoute,
  previewTaskPause,
  publishTaskRoute,
  restoreTask,
  secondaryAuthenticate,
  updateTask,
} from '#/api/adx';

import {
  buildTaskPayload,
  normalizeTaskRelations,
  resolveTaskEmptyState,
  taskEditorFromDetail,
  taskEditorIssue,
  taskEditorSnapshot,
} from './task-model';
import TaskReportDrawer from './task-report-drawer.vue';
import TaskRuntimeDrawer from './task-runtime-drawer.vue';

defineOptions({ name: 'AdxTaskManagement' });

const { hasAccessByCodes } = useAccess();
const canView = computed(() => hasAccessByCodes(['adx:task:view']));
const canAdd = computed(() => hasAccessByCodes(['adx:task:add']));
const canEdit = computed(() => hasAccessByCodes(['adx:task:edit']));
const canStatus = computed(() => hasAccessByCodes(['adx:task:status']));
const canArchive = computed(() => hasAccessByCodes(['adx:task:archive']));
const canEditEvents = computed(() => hasAccessByCodes(['adx:task:event:edit']));
const canPublish = computed(
  () =>
    hasAccessByCodes(['adx:task:publish']) &&
    hasAccessByCodes(['adx:monitor:publish']),
);
const canViewRoutes = computed(() => hasAccessByCodes(['adx:monitor:view']));
const canCopyRoute = computed(() => hasAccessByCodes(['adx:monitor:copy']));
const canPauseRoute = computed(
  () =>
    hasAccessByCodes(['adx:task:status']) &&
    hasAccessByCodes(['adx:monitor:status']),
);
const canViewReport = computed(() => hasAccessByCodes(['adx:report:view']));
const canViewRuntime = computed(() => hasAccessByCodes(['adx:runtime:view']));
const reportOpen = ref(false);
const reportTask = ref<TaskRecord>();
const reportMode = ref<'DAY_LINK' | 'DAY_TASK' | 'HOUR_TASK'>('HOUR_TASK');

function openReport(
  task: TaskRecord,
  mode: 'DAY_LINK' | 'DAY_TASK' | 'HOUR_TASK',
) {
  reportTask.value = task;
  reportMode.value = mode;
  reportOpen.value = true;
}

const runtimeOpen = ref(false);
const runtimeTask = ref<TaskRecord>();
const runtimeMode = ref<'CLICK' | 'CONVERSION' | 'INTEGRATION'>('INTEGRATION');

function openRuntime(
  task: TaskRecord,
  mode: 'CLICK' | 'CONVERSION' | 'INTEGRATION',
) {
  runtimeTask.value = task;
  runtimeMode.value = mode;
  runtimeOpen.value = true;
}

const emptyOptions: TaskOptions = {
  advertisers: [],
  appLinks: [],
  channels: [],
  credentials: [],
  mediaAccounts: [],
  operators: [],
  products: [],
};
const options = ref<TaskOptions>(emptyOptions);
const rows = ref<TaskRecord[]>([]);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const loadFailed = ref(false);
const forbidden = ref(false);
const filters = reactive({
  advertiserId: '',
  archived: false,
  channelId: '',
  exposure: '',
  keyword: '',
  mediaAccountId: '',
  operatorUserId: '',
  productId: '',
  status: '',
});
const filtersActive = computed(() =>
  Object.entries(filters).some(([key, value]) =>
    key === 'archived' ? value === true : Boolean(value),
  ),
);
const emptyState = computed(() =>
  resolveTaskEmptyState({
    canView: canView.value && !forbidden.value,
    error: loadFailed.value,
    filtersActive: filtersActive.value,
    total: total.value,
  }),
);

const filteredProducts = computed(() =>
  options.value.products.filter(
    (item) =>
      !filters.advertiserId || item.advertiserId === filters.advertiserId,
  ),
);
const filteredAccounts = computed(() =>
  options.value.mediaAccounts.filter(
    (item) => !filters.channelId || item.channelId === filters.channelId,
  ),
);

async function loadOptions() {
  options.value = await getTaskOptions();
}

async function load() {
  if (!canView.value) return;
  loading.value = true;
  loadFailed.value = false;
  forbidden.value = false;
  try {
    const result = await getTasks({
      advertiserId: filters.advertiserId || undefined,
      archived: filters.archived || undefined,
      channelId: filters.channelId || undefined,
      current: current.value,
      exposureEnabled:
        filters.exposure === '' ? undefined : filters.exposure === 'true',
      keyword: filters.keyword.trim() || undefined,
      mediaAccountId: filters.mediaAccountId || undefined,
      operatorUserId: filters.operatorUserId || undefined,
      productId: filters.productId || undefined,
      size: size.value,
      status: filters.archived ? undefined : filters.status || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error: any) {
    rows.value = [];
    total.value = 0;
    forbidden.value = isForbidden(error);
    loadFailed.value = !forbidden.value;
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
    advertiserId: '',
    archived: false,
    channelId: '',
    exposure: '',
    keyword: '',
    mediaAccountId: '',
    operatorUserId: '',
    productId: '',
    status: '',
  });
  await search();
}

function advertiserFilterChanged() {
  if (!filteredProducts.value.some((item) => item.id === filters.productId)) {
    filters.productId = '';
  }
}

function channelFilterChanged() {
  if (
    !filteredAccounts.value.some((item) => item.id === filters.mediaAccountId)
  ) {
    filters.mediaAccountId = '';
  }
}

const editorOpen = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
const editing = ref<TaskDetail>();
const savedSnapshot = ref('');
const conflictMessage = ref('');
const formError = ref('');
const eventOptions = ref<TaskEventBindingOptions>();
const eventOptionsLoading = ref(false);
const eventOptionsError = ref('');
const idempotencyKey = ref('');
const formRef = ref<FormInstance>();
const form = reactive<TaskEditor>({
  clickAppLinkId: '',
  dedupWindowSeconds: 3600,
  description: '',
  exposureAppLinkId: '',
  exposureAsClick: false,
  exposureEnabled: false,
  eventBindings: [],
  mediaAccountId: '',
  mediaCredentialId: '',
  operatorUserId: '',
  productId: '',
  rtaMode: 'OFF',
  taskName: '',
});
const rules: FormRules = {
  clickAppLinkId: [
    { message: '请选择启用且兼容的点击 AppLink', required: true },
  ],
  mediaAccountId: [{ message: '请选择媒体账号', required: true }],
  mediaCredentialId: [{ message: '请选择已配置的媒体凭据', required: true }],
  operatorUserId: [{ message: '请选择有效内部负责人', required: true }],
  productId: [{ message: '请选择启用产品', required: true }],
  taskName: [
    { max: 64, message: '任务名称不能超过 64 个字符', trigger: 'blur' },
    { message: '请输入任务名称', required: true, trigger: 'blur' },
  ],
};
const selectedProduct = computed(() =>
  options.value.products.find((item) => item.id === form.productId),
);
const editorProducts = computed(() => options.value.products);
const editorAppLinks = computed(() =>
  options.value.appLinks.filter(
    (item) =>
      item.productId === form.productId &&
      (!selectedProduct.value ||
        item.advertiserId === selectedProduct.value.advertiserId),
  ),
);
const editorAccounts = computed(() => options.value.mediaAccounts);
const selectedAccount = computed(() =>
  options.value.mediaAccounts.find((item) => item.id === form.mediaAccountId),
);
const editorCredentials = computed(() =>
  options.value.credentials.filter(
    (item) => item.mediaAccountId === form.mediaAccountId,
  ),
);
const selectedCredential = computed(() =>
  options.value.credentials.find((item) => item.id === form.mediaCredentialId),
);

function resetEditor() {
  editing.value = undefined;
  conflictMessage.value = '';
  formError.value = '';
  Object.assign(form, {
    clickAppLinkId: '',
    dedupWindowSeconds: 3600,
    description: '',
    exposureAppLinkId: '',
    exposureAsClick: false,
    exposureEnabled: false,
    eventBindings: [],
    mediaAccountId: '',
    mediaCredentialId: '',
    operatorUserId: options.value.operators[0]?.id || '',
    productId: '',
    rtaMode: 'OFF',
    taskName: '',
  });
}

function openCreate() {
  if (options.value.products.length === 0) {
    ElMessage.warning('当前对象范围内没有可用产品');
    return;
  }
  resetEditor();
  idempotencyKey.value = crypto.randomUUID().replaceAll('-', '');
  savedSnapshot.value = taskEditorSnapshot(form);
  editorOpen.value = true;
}

async function openEdit(row: TaskRecord) {
  editorOpen.value = true;
  editorLoading.value = true;
  conflictMessage.value = '';
  formError.value = '';
  try {
    const [detail, bindings] = await Promise.all([
      getTask(row.id),
      getTaskEventBindings(row.id),
    ]);
    editing.value = detail;
    Object.assign(form, taskEditorFromDetail(detail));
    form.eventBindings = bindings.bindings.map((binding) => ({
      callbackRatioBasisPoints: binding.callbackRatioBasisPoints,
      channelEventMappingId: binding.channelEventMappingId,
      eventDefinitionId: binding.eventDefinitionId,
      limitValue: binding.limitValue,
      overLimitDelivery: binding.overLimitDelivery,
      rowKey: binding.id,
    }));
    await loadEventOptions();
    savedSnapshot.value = taskEditorSnapshot(form);
  } catch (error: any) {
    editorOpen.value = false;
    ElMessage.error(isForbidden(error) ? '无权查看该任务' : '任务详情加载失败');
  } finally {
    editorLoading.value = false;
  }
}

async function productChanged() {
  const cleared = form.eventBindings.length > 0;
  normalizeTaskRelations(form, options.value, 'product');
  formError.value = '';
  if (cleared)
    ElMessage.warning('切换 Product 已清空事件绑定，请重新确认默认策略');
  await loadEventOptions();
}

async function accountChanged() {
  const cleared = form.eventBindings.length > 0;
  normalizeTaskRelations(form, options.value, 'mediaAccount');
  formError.value = '';
  if (cleared) {
    ElMessage.warning('切换媒体账号已清空旧 Channel / Adapter 事件绑定');
  }
  await loadEventOptions();
}

async function loadEventOptions() {
  const account = options.value.mediaAccounts.find(
    (item) => item.id === form.mediaAccountId,
  );
  eventOptions.value = undefined;
  eventOptionsError.value = '';
  if (!account || !form.productId) return;
  eventOptionsLoading.value = true;
  try {
    eventOptions.value = await getTaskEventBindingOptions({
      channelId: account.channelId,
      mediaAccountId: account.id,
      productId: form.productId,
    });
  } catch (error: any) {
    eventOptionsError.value = isForbidden(error)
      ? '无权查看当前 Channel / MediaAccount 的事件选项'
      : errorText(error);
  } finally {
    eventOptionsLoading.value = false;
  }
}

function addEventBinding() {
  form.eventBindings.push({
    callbackRatioBasisPoints: 10_000,
    channelEventMappingId: '',
    eventDefinitionId: '',
    limitValue: undefined,
    overLimitDelivery: false,
    rowKey: crypto.randomUUID(),
  });
}

function removeEventBinding(index: number) {
  form.eventBindings.splice(index, 1);
}

function bindingDefinitionChanged(binding: TaskEventBindingEditor) {
  const mapping = eventOptions.value?.mappings.find(
    (item) => item.id === binding.channelEventMappingId,
  );
  if (mapping?.eventDefinitionId !== binding.eventDefinitionId) {
    binding.channelEventMappingId = '';
  }
}

function mappingOptions(binding: TaskEventBindingEditor) {
  return (eventOptions.value?.mappings || []).filter(
    (item) => item.eventDefinitionId === binding.eventDefinitionId,
  );
}

function applyProductDefaults() {
  const templates = (eventOptions.value?.productTemplates || []).filter(
    (item) => item.applicable && item.channelEventMappingId,
  );
  const unique = new Map(
    templates.map((item) => [item.eventDefinitionId, item]),
  );
  form.eventBindings = [...unique.values()].map((item) => ({
    callbackRatioBasisPoints: 10_000,
    channelEventMappingId: item.channelEventMappingId || '',
    eventDefinitionId: item.eventDefinitionId,
    limitValue: item.limitValue,
    overLimitDelivery: false,
    rowKey: `product-${item.productPolicyId}`,
  }));
  ElMessage.success(
    form.eventBindings.length > 0
      ? '已复制可追踪的 Product 默认策略；保存后不再动态重读'
      : '没有可安全套用的默认策略，旧策略不会被猜测',
  );
}

function exposureChanged() {
  normalizeTaskRelations(form, options.value, 'exposure');
  formError.value = '';
}

async function beforeEditorClose(done: () => void) {
  if (saving.value) return;
  if (taskEditorSnapshot(form) === savedSnapshot.value) {
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
    // X、遮罩/ESC 与取消按钮均保留编辑器和当前输入。
  }
}

function requestEditorClose() {
  beforeEditorClose(() => {
    editorOpen.value = false;
  });
}

async function saveTask() {
  if (!(await formRef.value?.validate())) return;
  const issue = taskEditorIssue(form, options.value, eventOptions.value);
  if (issue) {
    formError.value = issue;
    return;
  }
  saving.value = true;
  conflictMessage.value = '';
  formError.value = '';
  try {
    const payload = buildTaskPayload(form);
    if (!canEditEvents.value) delete payload.eventBindings;
    const saved = editing.value
      ? await updateTask(editing.value.id, {
          ...payload,
          expectedVersion: editing.value.configVersion,
        })
      : await createTask(payload, idempotencyKey.value);
    editing.value = saved;
    savedSnapshot.value = taskEditorSnapshot(form);
    editorOpen.value = false;
    ElMessage.success('任务草稿已保存；尚未发布或生效');
    await load();
  } catch (error: any) {
    if (isConflict(error)) conflictMessage.value = errorText(error);
    else formError.value = errorText(error);
  } finally {
    saving.value = false;
  }
}

async function toggleReadiness(row: TaskRecord) {
  const status = row.status === 'READY' ? 'DRAFT' : 'READY';
  try {
    const { value } = await ElMessageBox.prompt(
      status === 'READY'
        ? '仅标记“配置就绪”，不会发布、不生成承流版本，也不会生效。'
        : '退回草稿不会停流；真实停流属于后续发布切片。',
      status === 'READY' ? '标记配置就绪' : '退回草稿',
      { inputPlaceholder: '填写原因（可选）', type: 'warning' },
    );
    await changeTaskStatus(row.id, row.configVersion, status, value || '');
    ElMessage.success(
      status === 'READY' ? '已标记配置就绪，尚未发布' : '已退回草稿',
    );
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

async function changeArchive(row: TaskRecord) {
  const restoring = row.status === 'ARCHIVED';
  try {
    const detail = await getTask(row.id);
    const { value } = await ElMessageBox.prompt(
      restoring
        ? '恢复后回到草稿，不会自动发布。'
        : `归档受引用保护。当前关联 Monitor ${detail.references.monitorCount} 条。`,
      restoring ? '恢复任务' : '归档任务',
      { inputPlaceholder: '填写原因（可选）', type: 'warning' },
    );
    await (restoring
      ? restoreTask(row.id, detail.configVersion, value || '')
      : archiveTask(row.id, detail.configVersion, value || ''));
    ElMessage.success(restoring ? '任务已恢复为草稿' : '任务已归档');
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

const publicationOpen = ref(false);
const publicationLoading = ref(false);
const publishing = ref(false);
const publicationTask = ref<TaskRecord>();
const publishCheck = ref<RoutePublishCheck>();
const routeVersions = ref<RouteVersion[]>([]);
const routeDistribution = ref<RouteDistribution>();
const publicationError = ref('');
const publicationForbidden = ref(false);
const publicationConflict = ref(false);
const pausePassword = ref('');
const pauseReason = ref('');

const publishConfirmDisabled = computed(
  () =>
    !canPublish.value ||
    !publishCheck.value ||
    publishCheck.value.overallStatus === 'FAIL' ||
    Date.parse(publishCheck.value.expiresAt) <= Date.now() ||
    publicationConflict.value,
);

async function openPublication(row: TaskRecord) {
  publicationTask.value = row;
  publicationOpen.value = true;
  pausePassword.value = '';
  pauseReason.value = '';
  await refreshPublication();
}

async function refreshPublication() {
  const row = publicationTask.value;
  if (!row) return;
  publicationLoading.value = true;
  publicationError.value = '';
  publicationForbidden.value = false;
  publicationConflict.value = false;
  try {
    const [check, versions, distribution] = await Promise.all([
      canPublish.value ? getTaskPublishCheck(row.id) : undefined,
      canViewRoutes.value ? getTaskRouteVersions(row.id) : [],
      canViewRoutes.value ? getTaskDistribution(row.id) : undefined,
    ]);
    publishCheck.value = check;
    routeVersions.value = versions;
    routeDistribution.value = distribution;
  } catch (error: any) {
    publicationForbidden.value = isForbidden(error);
    publicationError.value = publicationForbidden.value
      ? '当前账号无权查看或发布此 Task 的 Monitor 运行维度'
      : errorText(error);
  } finally {
    publicationLoading.value = false;
  }
}

async function confirmPublish() {
  const row = publicationTask.value;
  const check = publishCheck.value;
  if (!row || !check || publishConfirmDisabled.value) return;
  publishing.value = true;
  publicationError.value = '';
  try {
    const result = await publishTaskRoute(row.id, {
      checkToken: check.checkToken,
      expectedTaskVersion: check.taskConfigVersion,
      publishRequestId: crypto.randomUUID().replaceAll('-', ''),
    });
    ElMessage.success(
      result.active
        ? '发布版本已有实例应用证据'
        : '发布事务已完成，正在等待实例应用；当前不显示为已生效',
    );
    await refreshPublication();
    await load();
  } catch (error: any) {
    publicationConflict.value = isConflict(error);
    publicationError.value = publicationConflict.value
      ? '任务或依赖 revision 已变化，检查结果已过期，请重新检查'
      : errorText(error);
  } finally {
    publishing.value = false;
  }
}

async function copyRouteAddress() {
  const row = publicationTask.value;
  if (!row) return;
  try {
    const address = await copyTaskRouteAddress(row.id);
    await navigator.clipboard.writeText(address.template);
    ElMessage.success(`地址已复制并审计（${address.auditId.slice(0, 8)}…）`);
  } catch (error: any) {
    ElMessage.error(errorText(error));
  }
}

async function pauseRoute() {
  const row = publicationTask.value;
  if (!row) return;
  if (!pausePassword.value || !pauseReason.value.trim()) {
    publicationError.value = '停流需要当前登录密码二次认证并填写原因';
    return;
  }
  try {
    await secondaryAuthenticate(pausePassword.value);
    const preview = await previewTaskPause(row.id, row.configVersion);
    await ElMessageBox.confirm(
      `${preview.impact}；影响 ${preview.activeRouteCount} 个运行版本。恢复必须重新检查并发布新版本。`,
      '确认写入停流 deny marker',
      { confirmButtonText: '确认停流', type: 'warning' },
    );
    await pauseTaskRoute(row.id, {
      confirmed: true,
      expectedTaskVersion: preview.taskConfigVersion,
      pauseRequestId: crypto.randomUUID().replaceAll('-', ''),
      previewToken: preview.previewToken,
      reason: pauseReason.value.trim(),
    });
    pausePassword.value = '';
    ElMessage.success('停流 deny marker 已写入；不会翻转旧缓存布尔值');
    await refreshPublication();
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    publicationConflict.value = isConflict(error);
    publicationError.value = errorText(error);
  }
}

function isConflict(error: any) {
  return (
    error?.response?.status === 409 ||
    error?.response?.data?.code === 409 ||
    error?.data?.code === 409
  );
}

function isForbidden(error: any) {
  return (
    error?.response?.status === 403 ||
    error?.response?.data?.code === 403 ||
    error?.data?.code === 403
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

function statusLabel(status: TaskRecord['status']) {
  if (status === 'ACTIVE') return '运行中';
  if (status === 'PAUSED') return '已停流';
  if (status === 'READY') return '配置就绪';
  if (status === 'ARCHIVED') return '已归档';
  return '草稿';
}

function statusType(status: TaskRecord['status']) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'PAUSED') return 'danger';
  if (status === 'READY') return 'success';
  if (status === 'ARCHIVED') return 'info';
  return 'warning';
}

onMounted(async () => {
  if (!canView.value) return;
  try {
    await loadOptions();
  } catch (error: any) {
    if (isForbidden(error)) forbidden.value = true;
    else ElMessage.warning('任务筛选项加载失败，列表仍可继续使用');
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
            <el-icon class="text-primary"><SetUp /></el-icon>
            <h2 class="m-0 text-lg font-semibold">任务管理</h2>
          </div>
          <p class="mb-0 mt-2 max-w-4xl text-sm text-gray-500">
            统一配置主数据与事件绑定，并通过发布检查、不可变版本和实例 ACK
            管理运行状态。保存仍只形成草稿；联调、点击和转化入口只读冻结运行事实，补偿必须在运行中心完成两阶段确认。
          </p>
        </div>
        <el-button v-if="canAdd" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增任务草稿
        </el-button>
      </div>
    </el-card>

    <el-alert
      class="mb-4"
      :closable="false"
      show-icon
      title="S7 安全边界：PUBLISHED 仅表示管理事务完成；只有实例 ACK 达标才显示 ACTIVE"
      type="info"
    />

    <el-card shadow="never">
      <el-form @submit.prevent="search">
        <div class="grid gap-3 lg:grid-cols-4 xl:grid-cols-6">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="任务名称 / 编码 / 描述"
            @keyup.enter="search"
          />
          <el-select
            v-model="filters.advertiserId"
            clearable
            placeholder="全部广告主"
            @change="advertiserFilterChanged"
          >
            <el-option
              v-for="item in options.advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="filters.productId"
            clearable
            placeholder="全部产品"
          >
            <el-option
              v-for="item in filteredProducts"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="filters.channelId"
            clearable
            placeholder="全部渠道"
            @change="channelFilterChanged"
          >
            <el-option
              v-for="item in options.channels"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="filters.mediaAccountId"
            clearable
            placeholder="全部媒体账号"
          >
            <el-option
              v-for="item in filteredAccounts"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="filters.operatorUserId"
            clearable
            placeholder="全部负责人"
          >
            <el-option
              v-for="item in options.operators"
              :key="item.id"
              :label="item.displayName"
              :value="item.id"
            />
          </el-select>
        </div>
        <div class="mt-3 grid gap-3 lg:grid-cols-4 xl:grid-cols-6">
          <el-select
            v-if="!filters.archived"
            v-model="filters.status"
            clearable
            placeholder="全部配置状态"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="配置就绪" value="READY" />
            <el-option label="运行中" value="ACTIVE" />
            <el-option label="已停流" value="PAUSED" />
          </el-select>
          <el-select
            v-model="filters.exposure"
            clearable
            placeholder="曝光配置"
          >
            <el-option label="已开启曝光" value="true" />
            <el-option label="未开启曝光" value="false" />
          </el-select>
          <el-checkbox v-model="filters.archived" class="px-3"
            >查看已归档</el-checkbox
          >
          <div class="flex gap-2">
            <el-button type="primary" @click="search">
              <el-icon><Search /></el-icon>查询
            </el-button>
            <el-button @click="resetFilters">
              <el-icon><Refresh /></el-icon>重置
            </el-button>
          </div>
        </div>
      </el-form>

      <el-result
        v-if="emptyState === 'NO_PERMISSION'"
        icon="warning"
        sub-title="请联系管理员授予 adx:task:view，并配置 Advertiser、Channel 与 MediaAccount 对象范围"
        title="无权查看任务"
      />
      <el-result
        v-else-if="emptyState === 'ERROR'"
        icon="error"
        sub-title="请求失败，未展示缓存或可能过期的数据"
        title="任务加载失败"
      >
        <template #extra
          ><el-button @click="load">重新加载</el-button></template
        >
      </el-result>
      <el-empty
        v-else-if="emptyState === 'INITIAL_EMPTY'"
        description="还没有任务草稿"
      >
        <el-button v-if="canAdd" type="primary" @click="openCreate"
          >创建第一个草稿</el-button
        >
      </el-empty>
      <el-empty
        v-else-if="emptyState === 'FILTER_EMPTY'"
        description="没有符合当前筛选条件的任务"
      >
        <el-button @click="resetFilters">清空筛选</el-button>
      </el-empty>

      <template v-else>
        <el-table v-loading="loading" class="mt-4" :data="rows" row-key="id">
          <el-table-column label="任务" min-width="190">
            <template #default="{ row }">
              <div class="font-medium">{{ row.taskName }}</div>
              <div class="text-xs text-gray-400">{{ row.bizCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="广告主 / 产品" min-width="190">
            <template #default="{ row }">
              <div>{{ row.advertiserName }}</div>
              <div class="text-xs text-gray-400">{{ row.productName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="媒体账号 / 渠道" min-width="190">
            <template #default="{ row }">
              <div>{{ row.mediaAccountName }}</div>
              <div class="text-xs text-gray-400">{{ row.channelName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="AppLink" min-width="210">
            <template #default="{ row }">
              <div>点击：{{ row.clickAppLinkName }}</div>
              <div class="text-xs text-gray-400">
                曝光：{{
                  row.exposureEnabled ? row.exposureAppLinkName : '关闭'
                }}
                <span v-if="row.exposureAsClick"> · 大点击</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="负责人" min-width="110" prop="operatorName" />
          <el-table-column label="配置" min-width="155">
            <template #default="{ row }">
              <div>去重 {{ row.dedupWindowSeconds }} 秒</div>
              <div class="text-xs text-gray-400">
                RTA OFF · 凭据 rev {{ row.mediaCredentialSecretRevision }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="135">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{
                statusLabel(row.status)
              }}</el-tag>
              <div
                v-if="row.republishRequired"
                class="mt-1 text-xs text-amber-600"
              >
                待显式发布
              </div>
            </template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" min-width="300">
            <template #default="{ row }">
              <el-button
                v-if="canEdit && row.status !== 'ARCHIVED'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                <el-icon><EditPen /></el-icon>编辑
              </el-button>
              <el-button
                v-if="canStatus && ['DRAFT', 'READY'].includes(row.status)"
                link
                type="primary"
                @click="toggleReadiness(row)"
              >
                {{ row.status === 'READY' ? '退回草稿' : '配置就绪' }}
              </el-button>
              <el-button
                v-if="canPublish || canViewRoutes"
                link
                type="primary"
                @click="openPublication(row)"
                >发布与运行</el-button
              >
              <el-dropdown v-if="canViewReport" trigger="click">
                <el-button link type="primary">报表</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openReport(row, 'HOUR_TASK')"
                      >时报</el-dropdown-item
                    >
                    <el-dropdown-item @click="openReport(row, 'DAY_TASK')"
                      >日报</el-dropdown-item
                    >
                    <el-dropdown-item @click="openReport(row, 'DAY_LINK')"
                      >链接日报</el-dropdown-item
                    >
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-dropdown v-if="canViewRuntime" trigger="click">
                <el-button link type="primary">运行明细</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openRuntime(row, 'INTEGRATION')"
                      >联调</el-dropdown-item
                    >
                    <el-dropdown-item @click="openRuntime(row, 'CLICK')"
                      >点击</el-dropdown-item
                    >
                    <el-dropdown-item @click="openRuntime(row, 'CONVERSION')"
                      >转化</el-dropdown-item
                    >
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button
                v-if="canArchive"
                link
                :type="row.status === 'ARCHIVED' ? 'success' : 'warning'"
                @click="changeArchive(row)"
              >
                <el-icon><FolderDelete /></el-icon
                >{{ row.status === 'ARCHIVED' ? '恢复' : '归档' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="current"
            v-model:page-size="size"
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            :total="total"
            @current-change="load"
            @size-change="search"
          />
        </div>
      </template>
    </el-card>

    <TaskReportDrawer
      v-model="reportOpen"
      :initial-mode="reportMode"
      :task="reportTask"
    />

    <TaskRuntimeDrawer
      v-model="runtimeOpen"
      :initial-mode="runtimeMode"
      :task="runtimeTask"
    />

    <el-dialog
      v-model="publicationOpen"
      destroy-on-close
      width="min(920px, 96vw)"
      :title="`发布与运行 · ${publicationTask?.taskName || ''}`"
    >
      <div v-loading="publicationLoading" class="space-y-4">
        <el-alert
          v-if="publicationForbidden"
          :closable="false"
          show-icon
          title="无权查看该 Task 的发布与运行维度"
          type="warning"
        />
        <el-alert
          v-else-if="publicationError"
          :closable="false"
          show-icon
          :title="publicationError"
          :type="publicationConflict ? 'warning' : 'error'"
        />
        <el-alert
          v-if="routeDistribution?.overallStatus === 'DEGRADED'"
          :closable="false"
          show-icon
          title="部分实例应用失败，当前为 DEGRADED；不得视为全量生效"
          type="warning"
        />
        <el-alert
          v-if="routeDistribution?.overallStatus === 'FAILED'"
          :closable="false"
          show-icon
          title="分发失败；运行入口不会把该版本当作 ACTIVE"
          type="error"
        />

        <section v-if="publishCheck">
          <div class="mb-2 flex items-center justify-between">
            <h3 class="m-0 text-base font-semibold">发布检查</h3>
            <div class="text-xs text-gray-500">
              Task rev {{ publishCheck.taskConfigVersion }} · 有效至
              {{ publishCheck.expiresAt }}
            </div>
          </div>
          <el-alert
            class="mb-3"
            :closable="false"
            show-icon
            :title="
              publishCheck.overallStatus === 'FAIL'
                ? '存在 FAIL，确认发布已阻断；未配置目标 tracking host 时不会生成承流版本'
                : '检查通过后仍需确认；事务成功只进入分发中'
            "
            :type="publishCheck.overallStatus === 'FAIL' ? 'error' : 'success'"
          />
          <div class="grid gap-2 md:grid-cols-2">
            <div
              v-for="item in publishCheck.items"
              :key="item.code"
              class="rounded border border-gray-200 p-3"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="font-medium">{{ item.code }}</span>
                <el-tag
                  size="small"
                  :type="
                    item.status === 'PASS'
                      ? 'success'
                      : item.status === 'WARN'
                        ? 'warning'
                        : 'danger'
                  "
                  >{{ item.status }}</el-tag
                >
              </div>
              <div class="mt-1 text-xs text-gray-500">{{ item.message }}</div>
            </div>
          </div>
        </section>

        <section v-if="canViewRoutes">
          <div class="mb-2 flex items-center justify-between">
            <h3 class="m-0 text-base font-semibold">分发与实例证据</h3>
            <el-tag :type="routeDistribution?.active ? 'success' : 'warning'">{{
              routeDistribution?.overallStatus || 'NONE'
            }}</el-tag>
          </div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="门槛">
              {{ routeDistribution?.minimumRequiredAcks || 0 }} ACK
            </el-descriptions-item>
            <el-descriptions-item label="已应用">
              {{ routeDistribution?.appliedCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="失败">
              {{ routeDistribution?.failedCount || 0 }}
            </el-descriptions-item>
          </el-descriptions>
          <el-table
            class="mt-3"
            :data="routeDistribution?.instances || []"
            empty-text="尚无实例 ACK；PUBLISHED 不等于 ACTIVE"
            size="small"
          >
            <el-table-column label="实例" min-width="150" prop="instanceId" />
            <el-table-column label="应用版本" prop="appliedVersion" />
            <el-table-column label="状态" prop="status" />
            <el-table-column label="错误码" prop="errorCode" />
            <el-table-column label="ACK 时间" min-width="170" prop="ackedAt" />
          </el-table>
        </section>

        <section v-if="canViewRoutes">
          <h3 class="mb-2 text-base font-semibold">不可变版本历史</h3>
          <el-table
            :data="routeVersions"
            empty-text="尚无发布版本"
            max-height="260"
            size="small"
          >
            <el-table-column label="类型" prop="routeKind" />
            <el-table-column label="版本" prop="routeVersion" />
            <el-table-column label="发布" prop="publicationStatus" />
            <el-table-column label="承流" prop="acceptanceStatus" />
            <el-table-column label="分发" prop="distributionStatus" />
            <el-table-column label="Task rev" prop="taskConfigRevision" />
            <el-table-column
              label="事件 schema"
              prop="eventSnapshotSchemaVersion"
            />
            <el-table-column
              label="发布时间"
              min-width="170"
              prop="publishedAt"
            />
          </el-table>
        </section>

        <section v-if="canPauseRoute && publicationTask?.status === 'ACTIVE'">
          <el-divider content-position="left">受控停流</el-divider>
          <div class="grid gap-3 md:grid-cols-2">
            <el-input
              v-model="pauseReason"
              maxlength="255"
              placeholder="停流原因（必填，不记录敏感值）"
            />
            <el-input
              v-model="pausePassword"
              autocomplete="current-password"
              placeholder="当前登录密码（二次认证）"
              show-password
              type="password"
            />
          </div>
          <p class="mb-0 mt-2 text-xs text-gray-500">
            预览不会停流；确认后写 deny
            marker。恢复不能翻旧缓存，必须重新检查并发布新版本。
          </p>
        </section>
      </div>
      <template #footer>
        <div class="flex flex-wrap justify-end gap-2">
          <el-button @click="refreshPublication">刷新证据</el-button>
          <el-button
            v-if="canCopyRoute"
            :disabled="!routeDistribution?.active"
            @click="copyRouteAddress"
            >授权复制地址</el-button
          >
          <el-button
            v-if="canPauseRoute && publicationTask?.status === 'ACTIVE'"
            type="danger"
            @click="pauseRoute"
            >预览并确认停流</el-button
          >
          <el-button
            v-if="canPublish"
            :disabled="publishConfirmDisabled"
            :loading="publishing"
            type="primary"
            @click="confirmPublish"
            >确认发布（先进入分发中）</el-button
          >
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="editorOpen"
      :before-close="beforeEditorClose"
      size="min(760px, 94vw)"
      :title="editing ? '编辑任务草稿' : '新增任务草稿'"
    >
      <div v-loading="editorLoading">
        <el-alert
          v-if="conflictMessage"
          class="mb-4"
          :closable="false"
          show-icon
          :title="conflictMessage"
          type="error"
        >
          数据已过期。请关闭抽屉并刷新后重新编辑，系统不会静默覆盖。
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
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="form.taskName" maxlength="64" show-word-limit />
          </el-form-item>
          <div class="grid gap-4 md:grid-cols-2">
            <el-form-item label="Product" prop="productId">
              <el-select
                v-model="form.productId"
                filterable
                @change="productChanged"
              >
                <el-option
                  v-for="item in editorProducts"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="点击 AppLink" prop="clickAppLinkId">
              <el-select v-model="form.clickAppLinkId" filterable>
                <el-option
                  v-for="item in editorAppLinks"
                  :key="item.id"
                  :label="`${item.name} · ${item.linkType}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-divider content-position="left">曝光与验重</el-divider>
          <el-form-item label="曝光监测">
            <el-switch
              v-model="form.exposureEnabled"
              @change="exposureChanged"
            />
            <span class="ml-3 text-sm text-gray-500"
              >关闭后会清空曝光链接与大点击</span
            >
          </el-form-item>
          <div v-if="form.exposureEnabled" class="grid gap-4 md:grid-cols-2">
            <el-form-item label="曝光 AppLink" required>
              <el-select v-model="form.exposureAppLinkId" filterable>
                <el-option
                  v-for="item in editorAppLinks"
                  :key="item.id"
                  :label="`${item.name} · ${item.linkType}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="大点击">
              <el-switch v-model="form.exposureAsClick" />
              <span class="ml-3 text-sm text-gray-500"
                >仅记录配置，S5 不产生事件</span
              >
            </el-form-item>
          </div>
          <el-form-item label="点击去重窗口（秒）">
            <el-input-number
              v-model="form.dedupWindowSeconds"
              controls-position="right"
              :max="86400"
              :min="0"
              :step="60"
            />
            <span class="ml-3 text-sm text-gray-500">允许 0；最大 86400</span>
          </el-form-item>
          <el-divider content-position="left">媒体与负责人</el-divider>
          <div class="grid gap-4 md:grid-cols-2">
            <el-form-item label="媒体账号" prop="mediaAccountId">
              <el-select
                v-model="form.mediaAccountId"
                filterable
                @change="accountChanged"
              >
                <el-option
                  v-for="item in editorAccounts"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="媒体凭据" prop="mediaCredentialId">
              <el-select v-model="form.mediaCredentialId" filterable>
                <el-option
                  v-for="item in editorCredentials"
                  :key="item.id"
                  :label="`${item.label} · rev ${item.secretRevision}`"
                  :value="item.id"
                />
              </el-select>
              <div v-if="selectedCredential" class="mt-1 text-xs text-gray-500">
                保存时冻结 secret revision
                {{ selectedCredential.secretRevision }}，不回显凭据内容
              </div>
            </el-form-item>
          </div>
          <div v-if="selectedAccount" class="mb-3 text-xs text-gray-500">
            所属渠道：{{
              options.channels.find(
                (item) => item.id === selectedAccount?.channelId,
              )?.name || '-'
            }}
          </div>
          <el-form-item label="内部负责人" prop="operatorUserId">
            <el-select v-model="form.operatorUserId" filterable>
              <el-option
                v-for="item in options.operators"
                :key="item.id"
                :label="item.displayName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-divider content-position="left">事件绑定草稿</el-divider>
          <el-alert
            class="mb-3"
            :closable="false"
            show-icon
            title="S6 只保存 TaskEventBinding 草稿并标记待重发；不会发布 RouteEventSnapshot、写 Outbox 或发送 Callback"
            type="info"
          />
          <el-alert
            v-if="!canEditEvents"
            class="mb-3"
            :closable="false"
            show-icon
            title="缺少 adx:task:event:edit，事件行只读；保存其他字段不会绕过服务端权限"
            type="warning"
          />
          <div v-loading="eventOptionsLoading" class="mb-4 rounded border p-3">
            <el-result
              v-if="eventOptionsError"
              icon="error"
              :sub-title="eventOptionsError"
              title="事件选项加载失败"
            >
              <template #extra>
                <el-button @click="loadEventOptions">重新加载</el-button>
              </template>
            </el-result>
            <el-empty
              v-else-if="!form.productId || !form.mediaAccountId"
              description="先选择 Product 和媒体账号，再配置对应 Channel / Adapter 的事件映射"
            />
            <el-empty
              v-else-if="eventOptions && eventOptions.mappings.length === 0"
              description="当前对象范围内没有启用且兼容的渠道事件映射"
            />
            <template v-else-if="eventOptions">
              <div
                class="mb-3 flex flex-wrap items-center justify-between gap-2"
              >
                <div class="text-sm text-gray-500">
                  Channel {{ eventOptions.channelId }} · Adapter
                  {{ eventOptions.adapterCode }} · 自然日 Asia/Shanghai ·
                  SHA-256 确定性采样
                </div>
                <div class="flex gap-2">
                  <el-button
                    :disabled="!canEditEvents"
                    @click="applyProductDefaults"
                  >
                    套用 Product 默认策略
                  </el-button>
                  <el-button
                    :disabled="!canEditEvents"
                    type="primary"
                    plain
                    @click="addEventBinding"
                  >
                    <el-icon><Plus /></el-icon>添加事件行
                  </el-button>
                </div>
              </div>
              <el-alert
                v-if="
                  eventOptions.productTemplates.some((item) => !item.applicable)
                "
                class="mb-3"
                :closable="false"
                show-icon
                title="部分 Product 旧策略待补标准事件或映射不唯一，系统未自动猜测"
                type="warning"
              />
              <el-empty
                v-if="form.eventBindings.length === 0"
                description="尚未添加事件绑定；可保持为空草稿或套用明确的 Product 默认策略"
              />
              <div
                v-for="(binding, index) in form.eventBindings"
                :key="binding.rowKey"
                class="mb-3 rounded border border-gray-200 p-3"
              >
                <div class="grid gap-3 md:grid-cols-2">
                  <el-form-item label="上游标准事件" required>
                    <el-select
                      v-model="binding.eventDefinitionId"
                      :disabled="!canEditEvents"
                      filterable
                      @change="bindingDefinitionChanged(binding)"
                    >
                      <el-option
                        v-for="definition in eventOptions.definitions"
                        :key="definition.id"
                        :disabled="
                          form.eventBindings.some(
                            (row, rowIndex) =>
                              rowIndex !== index &&
                              row.eventDefinitionId === definition.id,
                          )
                        "
                        :label="`${definition.name} · ${definition.code}`"
                        :value="definition.id"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="渠道 externalCode 映射" required>
                    <el-select
                      v-model="binding.channelEventMappingId"
                      :disabled="!canEditEvents || !binding.eventDefinitionId"
                      filterable
                    >
                      <el-option
                        v-for="mapping in mappingOptions(binding)"
                        :key="mapping.id"
                        :label="`${mapping.externalCode} · ${mapping.parameterTemplateCode}`"
                        :value="mapping.id"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="Callback 比例（basis points）">
                    <el-input-number
                      v-model="binding.callbackRatioBasisPoints"
                      :disabled="!canEditEvents"
                      :max="10000"
                      :min="0"
                      :step="100"
                    />
                    <div class="mt-1 text-xs text-gray-500">
                      0 = 0%，10000 = 100%；重放使用稳定事实键，结果不变
                    </div>
                  </el-form-item>
                  <el-form-item label="自然日限额">
                    <el-input-number
                      v-model="binding.limitValue"
                      :disabled="!canEditEvents"
                      :min="0"
                      :precision="0"
                    />
                    <div class="mt-1 text-xs text-gray-500">
                      留空 = 不限额；0 = 所有采样事件均超限
                    </div>
                  </el-form-item>
                  <el-form-item label="超限后继续投递">
                    <el-switch
                      v-model="binding.overLimitDelivery"
                      :disabled="!canEditEvents"
                    />
                    <span class="ml-2 text-xs text-gray-500">
                      只控制投递，不删除或改写原始事实
                    </span>
                  </el-form-item>
                </div>
                <div class="flex justify-end">
                  <el-button
                    :disabled="!canEditEvents"
                    link
                    type="danger"
                    @click="removeEventBinding(index)"
                  >
                    移除该草稿行
                  </el-button>
                </div>
              </div>
            </template>
          </div>
          <el-form-item label="RTA">
            <el-input model-value="OFF" disabled />
            <div class="mt-1 text-xs text-gray-500">
              服务端同样拒绝任何非 OFF 请求或历史重放
            </div>
          </el-form-item>
          <el-form-item label="说明">
            <el-input
              v-model="form.description"
              maxlength="500"
              :rows="3"
              show-word-limit
              type="textarea"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="requestEditorClose">取消</el-button>
          <el-button
            :disabled="Boolean(conflictMessage)"
            :loading="saving"
            type="primary"
            @click="saveTask"
          >
            保存草稿（不发布）
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
