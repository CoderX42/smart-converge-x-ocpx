<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { ProductEditor } from './product-model';

import type {
  Product,
  ProductAdvertiserOption,
  ProductDetail,
  ProductEventOption,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import {
  Delete,
  EditPen,
  FolderDelete,
  Picture,
  Plus,
  Refresh,
  Search,
  UploadFilled,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveProduct,
  createProduct,
  getProduct,
  getProductAdvertiserOptions,
  getProductEventOptions,
  getProducts,
  removeProductLogo,
  restoreProduct,
  updateProduct,
  uploadProductLogo,
} from '#/api/ocpx';

import {
  buildProductCreatePayload,
  buildProductUpdatePayload,
  createProductIdempotencyKey,
  duplicateProductPolicyIndexes,
  productEditorSnapshot,
  resolveProductEmptyState,
} from './product-model';

defineOptions({ name: 'OcpxProductManagement' });

const { hasAccessByCodes } = useAccess();
const canView = computed(() => hasAccessByCodes(['ocpx:product:view']));
const canAdd = computed(() => hasAccessByCodes(['ocpx:product:add']));
const canEdit = computed(() => hasAccessByCodes(['ocpx:product:edit']));
const canArchive = computed(() => hasAccessByCodes(['ocpx:product:archive']));

const filters = reactive({
  advertiserId: '',
  archived: false,
  keyword: '',
  status: '',
});
const current = ref(1);
const size = ref(20);
const total = ref(0);
const rows = ref<Product[]>([]);
const advertisers = ref<ProductAdvertiserOption[]>([]);
const events = ref<ProductEventOption[]>([]);
const loading = ref(false);
const loadFailed = ref(false);

const filtersActive = computed(
  () =>
    Boolean(filters.advertiserId) ||
    filters.archived ||
    Boolean(filters.keyword) ||
    Boolean(filters.status),
);
const emptyState = computed(() =>
  resolveProductEmptyState({
    canView: canView.value,
    error: loadFailed.value,
    filtersActive: filtersActive.value,
    total: total.value,
  }),
);

const editorOpen = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
const editing = ref<ProductDetail>();
const idempotencyKey = ref('');
const savedSnapshot = ref('');
const conflictMessage = ref('');
const policyError = ref('');
const uploadFailure = ref('');
const formRef = ref<FormInstance>();
const form = reactive<ProductEditor>({
  advertiserId: '',
  code: '',
  description: '',
  eventPolicies: [],
  logoFile: undefined,
  name: '',
  packageName: '',
  removeLogo: false,
  status: 'ACTIVE',
});

const rules: FormRules = {
  advertiserId: [
    { message: '请选择启用且在对象范围内的广告主', required: true },
  ],
  code: [
    {
      message: '编码为 2–64 位字母、数字、点、下划线或连字符',
      pattern: /^[A-Z0-9][\w.-]{1,63}$/i,
      trigger: 'blur',
    },
    { message: '请输入产品编码', required: true, trigger: 'blur' },
  ],
  description: [
    { max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' },
  ],
  name: [
    { max: 64, message: '名称不能超过 64 个字符', trigger: 'blur' },
    { message: '请输入产品名称', required: true, trigger: 'blur' },
  ],
  packageName: [
    { max: 255, message: '包名不能超过 255 个字符', trigger: 'blur' },
  ],
  status: [{ message: '请选择产品状态', required: true }],
};

async function loadOptions() {
  if (!canView.value) return;
  const [advertiserResult, eventResult] = await Promise.all([
    getProductAdvertiserOptions(),
    getProductEventOptions(),
  ]);
  advertisers.value = advertiserResult;
  events.value = eventResult;
}

async function load() {
  if (!canView.value) return;
  loading.value = true;
  loadFailed.value = false;
  try {
    const result = await getProducts({
      advertiserId: filters.advertiserId || undefined,
      archived: filters.archived || undefined,
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
    advertiserId: '',
    archived: false,
    keyword: '',
    status: '',
  });
  await search();
}

function resetEditor() {
  editing.value = undefined;
  conflictMessage.value = '';
  policyError.value = '';
  uploadFailure.value = '';
  Object.assign(form, {
    advertiserId: advertisers.value[0]?.id || '',
    code: '',
    description: '',
    eventPolicies: [{ dailyLimit: 0, eventDefinitionId: '' }],
    logoFile: undefined,
    name: '',
    packageName: '',
    removeLogo: false,
    status: 'ACTIVE',
  });
}

function openCreate() {
  if (advertisers.value.length === 0) {
    ElMessage.warning('当前没有可操作的启用广告主');
    return;
  }
  resetEditor();
  idempotencyKey.value = createProductIdempotencyKey();
  savedSnapshot.value = productEditorSnapshot(form);
  editorOpen.value = true;
}

async function openEdit(row: Product) {
  editorOpen.value = true;
  editorLoading.value = true;
  conflictMessage.value = '';
  policyError.value = '';
  uploadFailure.value = '';
  try {
    const detail = await getProduct(row.id);
    editing.value = detail;
    Object.assign(form, {
      advertiserId: detail.advertiserId,
      code: detail.code,
      description: detail.description || '',
      eventPolicies: detail.eventPolicies.map((policy) => ({
        dailyLimit: policy.dailyLimit,
        eventDefinitionId: policy.eventDefinitionId,
      })),
      logoFile: undefined,
      name: detail.name,
      packageName: detail.packageName || '',
      removeLogo: false,
      status: detail.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
    });
    savedSnapshot.value = productEditorSnapshot(form);
  } catch {
    editorOpen.value = false;
    ElMessage.error('产品详情加载失败');
  } finally {
    editorLoading.value = false;
  }
}

async function beforeEditorClose(done: () => void) {
  if (saving.value) return;
  if (productEditorSnapshot(form) === savedSnapshot.value) {
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

function addPolicy() {
  form.eventPolicies.push({ dailyLimit: 0, eventDefinitionId: '' });
  policyError.value = '';
}

function removePolicy(index: number) {
  form.eventPolicies.splice(index, 1);
  policyError.value = '';
}

function selectLogo(uploadFile: UploadFile) {
  const file = uploadFile.raw;
  uploadFailure.value = '';
  if (!file) return;
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    form.logoFile = undefined;
    uploadFailure.value = 'Logo 仅支持 PNG 或 JPEG 图片。';
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    form.logoFile = undefined;
    uploadFailure.value = 'Logo 不能超过 2MB。';
    return;
  }
  form.logoFile = file;
  form.removeLogo = false;
}

function markLogoForRemoval() {
  form.logoFile = undefined;
  form.removeLogo = true;
  uploadFailure.value = '';
}

function validatePolicies() {
  if (form.eventPolicies.some((item) => !item.eventDefinitionId)) {
    policyError.value = '请选择每一行的事件；不需要的空行请移除。';
    return false;
  }
  const duplicates = duplicateProductPolicyIndexes(form.eventPolicies);
  if (duplicates.length > 0) {
    policyError.value = `事件重复：请检查第 ${duplicates.map((index) => index + 1).join('、')} 行。`;
    return false;
  }
  policyError.value = '';
  return true;
}

async function saveProduct() {
  if (!(await formRef.value?.validate()) || !validatePolicies()) return;
  const creating = !editing.value;
  saving.value = true;
  conflictMessage.value = '';
  uploadFailure.value = '';
  try {
    let saved = editing.value
      ? await updateProduct(
          editing.value.id,
          buildProductUpdatePayload(form, editing.value),
        )
      : await createProduct(
          buildProductCreatePayload(form),
          idempotencyKey.value,
        );
    editing.value = saved;
    try {
      if (form.logoFile) {
        saved = await uploadProductLogo(
          saved.id,
          saved.configVersion,
          form.logoFile,
        );
      } else if (form.removeLogo && saved.logoAvailable) {
        saved = await removeProductLogo(saved.id, saved.configVersion);
      }
    } catch (error) {
      editing.value = saved;
      uploadFailure.value = `产品主数据已保存，但 Logo 处理失败：${errorText(error)}`;
      return;
    }
    editing.value = saved;
    form.logoFile = undefined;
    form.removeLogo = false;
    savedSnapshot.value = productEditorSnapshot(form);
    editorOpen.value = false;
    ElMessage.success(creating ? '产品已创建' : '产品已保存');
    await load();
  } catch (error: any) {
    if (
      error?.response?.status === 409 ||
      error?.response?.data?.code === 409 ||
      error?.data?.code === 409
    ) {
      conflictMessage.value = errorText(error);
    }
  } finally {
    saving.value = false;
  }
}

async function changeArchive(row: Product) {
  const restoring = row.status === 'ARCHIVED';
  try {
    const { value } = await ElMessageBox.prompt(
      restoring
        ? '恢复后产品回到启用状态；上游广告主必须仍处于启用状态。'
        : `归档前将检查应用链接和路由引用。当前引用 ${row.appLinkCount} 个应用链接。`,
      restoring ? '恢复产品' : '归档产品',
      {
        inputPlaceholder: '填写原因（可选）',
        type: restoring ? 'info' : 'warning',
      },
    );
    await (restoring
      ? restoreProduct(row.id, row.configVersion, value || '')
      : archiveProduct(row.id, row.configVersion, value || ''));
    ElMessage.success(restoring ? '产品已恢复' : '产品已归档');
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    const message = errorText(error);
    if (message) ElMessage.error(message);
  }
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

function statusLabel(status: Product['status']) {
  if (status === 'ACTIVE') return '启用';
  if (status === 'ARCHIVED') return '已归档';
  return '禁用';
}

function statusType(status: Product['status']) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'ARCHIVED') return 'info';
  return 'warning';
}

onMounted(async () => {
  if (!canView.value) return;
  try {
    await loadOptions();
  } catch {
    ElMessage.warning('筛选选项加载失败，列表仍可继续使用');
  }
  await load();
});
</script>

<template>
  <div class="product-page">
    <el-result
      v-if="emptyState === 'NO_PERMISSION'"
      icon="warning"
      sub-title="当前账号未获得产品查看权限，也不会发起产品列表请求。"
      title="无权查看产品"
    />

    <template v-else>
      <header class="page-heading">
        <div>
          <h1>产品管理</h1>
          <p class="subtitle">
            维护广告主所属产品及事件策略，供链接和任务配置使用。
          </p>
        </div>
        <el-button
          v-if="canAdd"
          :icon="Plus"
          type="primary"
          @click="openCreate"
        >
          新增产品
        </el-button>
      </header>

      <el-card class="filter-card" shadow="never">
        <div class="filters">
          <el-input
            v-model="filters.keyword"
            clearable
            :prefix-icon="Search"
            placeholder="名称、编码或描述"
            @keyup.enter="search"
          />
          <el-select
            v-model="filters.advertiserId"
            clearable
            filterable
            placeholder="上游广告主"
          >
            <el-option
              v-for="advertiser in advertisers"
              :key="advertiser.id"
              :label="`${advertiser.name} · ${advertiser.code}`"
              :value="advertiser.id"
            />
          </el-select>
          <el-select
            v-model="filters.status"
            clearable
            :disabled="filters.archived"
            placeholder="状态"
          >
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
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
        <el-table v-loading="loading" :data="rows" height="calc(100vh - 330px)">
          <el-table-column label="产品" min-width="220">
            <template #default="{ row }">
              <div class="product-cell">
                <div
                  class="logo-mark"
                  :class="{ 'has-logo': row.logoAvailable }"
                >
                  <Picture v-if="row.logoAvailable" />
                  <span v-else>{{ row.name.slice(0, 1).toUpperCase() }}</span>
                </div>
                <div class="primary-cell">
                  <strong>{{ row.name }}</strong>
                  <code>{{ row.code }}</code>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="上游广告主"
            min-width="160"
            prop="advertiserName"
          />
          <el-table-column label="包名" min-width="190">
            <template #default="{ row }">
              <code class="package-name">{{ row.packageName || '—' }}</code>
            </template>
          </el-table-column>
          <el-table-column label="事件默认策略" min-width="130">
            <template #default="{ row }">
              {{ row.eventPolicyCount }} 项
              <span class="muted">· r{{ row.eventPolicyRevision }}</span>
            </template>
          </el-table-column>
          <el-table-column label="AppLink" width="90" prop="appLinkCount" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="90">
            <template #default="{ row }">v{{ row.configVersion }}</template>
          </el-table-column>
          <el-table-column label="路由影响" min-width="110">
            <template #default="{ row }">
              <el-tag
                v-if="row.republishRequiredCount"
                type="warning"
                size="small"
              >
                {{ row.republishRequiredCount }} 条待重发
              </el-tag>
              <span v-else class="muted">无待重发</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170" prop="updateTime" />
          <el-table-column fixed="right" label="操作" width="170">
            <template #default="{ row }">
              <el-button
                v-if="canEdit && row.status !== 'ARCHIVED'"
                :icon="EditPen"
                link
                type="primary"
                @click="openEdit(row)"
                >编辑</el-button
              >
              <el-button
                v-if="canArchive"
                :icon="FolderDelete"
                link
                :type="row.status === 'ARCHIVED' ? 'success' : 'warning'"
                @click="changeArchive(row)"
                >{{ row.status === 'ARCHIVED' ? '恢复' : '归档' }}</el-button
              >
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-panel">
              <template v-if="emptyState === 'ERROR'">
                <strong>产品列表加载失败</strong>
                <span>筛选条件已保留，可直接重试。</span>
                <el-button type="primary" @click="load">重新加载</el-button>
              </template>
              <template v-else-if="emptyState === 'FILTER_EMPTY'">
                <strong>未找到匹配产品</strong>
                <span>调整广告主、状态或关键词后再试。</span>
                <el-button @click="resetFilters">清空筛选</el-button>
              </template>
              <template v-else>
                <strong>暂无产品</strong>
                <span>先建立产品主数据，再由后续 AppLink 选择关联。</span>
                <el-button v-if="canAdd" type="primary" @click="openCreate"
                  >新增产品</el-button
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
      :close-on-press-escape="!saving"
      destroy-on-close
      size="620px"
      :title="editing ? '编辑产品' : '新增产品'"
    >
      <div v-loading="editorLoading">
        <el-alert
          v-if="conflictMessage"
          class="drawer-alert"
          :closable="false"
          :title="conflictMessage"
          type="warning"
          show-icon
        />
        <el-alert
          v-if="uploadFailure"
          class="drawer-alert"
          :closable="false"
          :title="uploadFailure"
          type="error"
          show-icon
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
        >
          <div class="form-grid">
            <el-form-item label="上游广告主" prop="advertiserId">
              <el-select
                v-model="form.advertiserId"
                class="full-width"
                :disabled="Boolean(editing)"
                filterable
              >
                <el-option
                  v-for="advertiser in advertisers"
                  :key="advertiser.id"
                  :label="`${advertiser.name} · ${advertiser.code}`"
                  :value="advertiser.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="产品编码" prop="code">
              <el-input
                v-model="form.code"
                :disabled="Boolean(editing)"
                maxlength="64"
              />
            </el-form-item>
            <el-form-item label="产品名称" prop="name">
              <el-input
                v-model="form.name"
                maxlength="64"
                placeholder="请输入名称"
              />
            </el-form-item>
            <el-form-item label="包名" prop="packageName">
              <el-input
                v-model="form.packageName"
                maxlength="255"
                placeholder="请输入包名"
              />
            </el-form-item>
          </div>

          <el-form-item label="产品状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio-button value="ACTIVE">启用</el-radio-button>
              <el-radio-button value="DISABLED">禁用</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="LOGO">
            <div class="logo-editor">
              <div class="logo-preview">
                <Picture v-if="editing?.logoAvailable && !form.removeLogo" />
                <UploadFilled v-else />
              </div>
              <div>
                <el-upload
                  accept="image/png,image/jpeg"
                  :auto-upload="false"
                  :limit="1"
                  :on-change="selectLogo"
                  :show-file-list="true"
                >
                  <el-button :icon="UploadFilled">选择图片</el-button>
                </el-upload>
                <span class="field-hint"
                  >PNG/JPEG，最大 2MB，尺寸不超过 2048×2048。</span
                >
              </div>
              <el-button
                v-if="
                  editing?.logoAvailable && !form.logoFile && !form.removeLogo
                "
                :icon="Delete"
                link
                type="danger"
                @click="markLogoForRemoval"
                >移除</el-button
              >
            </div>
          </el-form-item>

          <el-form-item label="描述" prop="description">
            <el-input
              v-model="form.description"
              maxlength="500"
              placeholder="请输入描述信息"
              show-word-limit
              :rows="3"
              type="textarea"
            />
          </el-form-item>

          <section class="policy-section">
            <div class="section-heading">
              <div>
                <strong>事件默认策略</strong>
                <p>
                  限量按 Asia/Shanghai
                  自然日；后续绑定时复制，不供运行时动态读取。
                </p>
              </div>
              <el-button :icon="Plus" link type="primary" @click="addPolicy"
                >增加事件映射</el-button
              >
            </div>
            <el-alert
              v-if="policyError"
              class="policy-error"
              :closable="false"
              :title="policyError"
              type="error"
              show-icon
            />
            <div
              v-for="(policy, index) in form.eventPolicies"
              :key="index"
              class="policy-row"
            >
              <span class="row-number">{{ index + 1 }}</span>
              <el-select
                v-model="policy.eventDefinitionId"
                filterable
                placeholder="选择上游事件"
              >
                <el-option
                  v-for="event in events"
                  :key="event.id"
                  :disabled="
                    form.eventPolicies.some(
                      (item, itemIndex) =>
                        itemIndex !== index &&
                        item.eventDefinitionId === event.id,
                    )
                  "
                  :label="`${event.eventName} · ${event.eventCode}`"
                  :value="event.id"
                />
              </el-select>
              <el-input-number
                v-model="policy.dailyLimit"
                :min="0"
                :max="2147483647"
              />
              <el-button
                :icon="Delete"
                circle
                plain
                type="danger"
                @click="removePolicy(index)"
              />
            </div>
            <el-empty
              v-if="form.eventPolicies.length === 0"
              description="未设置默认策略；可稍后增加"
              :image-size="56"
            />
          </section>
        </el-form>
      </div>

      <template #footer>
        <el-button :disabled="saving" @click="editorOpen = false"
          >取消</el-button
        >
        <el-button :loading="saving" type="primary" @click="saveProduct"
          >确定</el-button
        >
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.product-page {
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
    minmax(220px, 1.4fr) minmax(180px, 1fr)
    140px auto auto;
  gap: 12px;
  align-items: center;
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

.product-cell,
.logo-editor {
  display: flex;
  gap: 12px;
  align-items: center;
}

.logo-mark,
.logo-preview {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 42px;
  height: 42px;
  font-weight: 700;
  color: #3159ad;
  background: linear-gradient(145deg, #eef4ff, #dde8ff);
  border: 1px solid #cddcff;
  border-radius: 50%;
}

.logo-mark svg,
.logo-preview svg {
  width: 19px;
}

.logo-mark.has-logo {
  color: #087f5b;
  background: #e8f8f1;
  border-color: #b7ead7;
}

.primary-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.primary-cell code,
.package-name {
  font-size: 12px;
  color: #7d8799;
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

.drawer-alert,
.policy-error {
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

.full-width {
  width: 100%;
}

.logo-editor {
  width: 100%;
  padding: 12px;
  background: #fafbfc;
  border: 1px solid #e8ebf1;
  border-radius: 10px;
}

.field-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}

.policy-section {
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e4e9f2;
  border-radius: 12px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-heading p {
  margin: 5px 0 0;
  font-size: 12px;
  color: #7a8498;
}

.policy-row {
  display: grid;
  grid-template-columns: 24px minmax(220px, 1fr) 150px 34px;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.row-number {
  font-size: 12px;
  color: #8b95a7;
  text-align: center;
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
