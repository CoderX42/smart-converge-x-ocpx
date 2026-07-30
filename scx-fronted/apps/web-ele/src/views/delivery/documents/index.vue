<script lang="ts" setup>
import type {
  FormInstance,
  FormRules,
  UploadFile,
  UploadFiles,
  UploadUserFile,
} from 'element-plus';

import type {
  IntegrationDocument,
  IntegrationDocumentInput,
  IntegrationDocumentStatus,
  IntegrationDocumentStorageType,
  IntegrationDocumentType,
  Media,
} from '#/api/ad';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useAccess } from '@boot/access';

import {
  Delete,
  DocumentAdd,
  Download,
  EditPen,
  Link,
  MoreFilled,
  Refresh,
  Search,
  UploadFilled,
  View,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteIntegrationDocument,
  getIntegrationDocumentContent,
  getIntegrationDocuments,
  getMedia,
  saveIntegrationDocument,
} from '#/api/ad';

import DocumentPreview from './document-preview.vue';
import {
  externalDomain,
  formatFileSize,
  normalizeStorageType,
  validateDocumentFile,
  validateExternalUrl,
} from './document-utils';

import '../components/mvp-page.css';

defineOptions({ name: 'AdDocuments' });

type DocumentForm = IntegrationDocumentInput & {
  originalFileName?: string;
};

const TYPE_OPTIONS: Array<{ label: string; value: IntegrationDocumentType }> = [
  { label: '媒体回传协议', value: 'MEDIA_CALLBACK' },
  { label: '媒体接口规范', value: 'MEDIA_API' },
  { label: '产品接口规范', value: 'PRODUCT_API' },
  { label: 'RTA 决策协议', value: 'RTA_SPEC' },
  { label: '其他', value: 'OTHER' },
];
const STATUS_OPTIONS: Array<{
  label: string;
  value: IntegrationDocumentStatus;
}> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '生效中', value: 'ACTIVE' },
  { label: '已停用', value: 'DISABLED' },
  { label: '已退役', value: 'RETIRED' },
];
const TYPE_LABELS = Object.fromEntries(
  TYPE_OPTIONS.map((item) => [item.value, item.label]),
) as Record<IntegrationDocumentType, string>;
const STATUS_LABELS = Object.fromEntries(
  STATUS_OPTIONS.map((item) => [item.value, item.label]),
) as Record<IntegrationDocumentStatus, string>;

const { hasAccessByCodes } = useAccess();
const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<IntegrationDocument[]>([]);
const loading = ref(false);
const bootstrapping = ref(true);
const saving = ref(false);
const deletingId = ref('');
const drawerVisible = ref(false);
const previewVisible = ref(false);
const previewDocument = ref<IntegrationDocument>();
const formRef = ref<FormInstance>();
const current = ref(1);
const size = ref(20);
const total = ref(0);
const selectedFile = ref<File>();
const saveAbortController = ref<AbortController>();
const uploadFiles = ref<UploadUserFile[]>([]);
const uploadProgress = ref(0);
const initialFormSnapshot = ref('');
const query = reactive<{
  keyword: string;
  status?: IntegrationDocumentStatus;
  storageType?: IntegrationDocumentStorageType;
  type?: IntegrationDocumentType;
}>({
  keyword: '',
  status: undefined,
  storageType: undefined,
  type: undefined,
});
const form = reactive<DocumentForm>({
  description: '',
  externalUrl: '',
  mediaId: '',
  name: '',
  status: 'DRAFT',
  storageType: 'OBJECT',
  type: 'OTHER',
});

const hasMedia = computed(() => media.value.length > 0);
const canWrite = computed(() =>
  hasAccessByCodes(['ad:integration-document:write']),
);
const canDelete = computed(() =>
  hasAccessByCodes(['ad:integration-document:delete']),
);
const selectedMediaName = computed(
  () => media.value.find((item) => item.id === mediaId.value)?.name || '',
);
const formChanged = computed(
  () =>
    Boolean(selectedFile.value) ||
    JSON.stringify({
      description: form.description,
      externalUrl: form.externalUrl,
      name: form.name,
      status: form.status,
      storageType: form.storageType,
      type: form.type,
    }) !== initialFormSnapshot.value,
);
const formRules: FormRules<DocumentForm> = {
  description: [
    { max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' },
  ],
  externalUrl: [
    {
      trigger: ['blur', 'change'],
      validator: (_rule, value, callback) => {
        if (form.storageType === 'OBJECT') return callback();
        const message = validateExternalUrl(value);
        return message ? callback(new Error(message)) : callback();
      },
    },
  ],
  name: [
    { required: true, message: '请输入文档名称', trigger: 'blur' },
    { max: 128, message: '名称不能超过 128 个字符', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  type: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
};

function readableError(error: unknown, fallback: string) {
  const failure = error as {
    code?: number;
    message?: string;
    msg?: string;
    response?: { data?: { code?: number; msg?: string }; status?: number };
  };
  return (
    failure.response?.data?.msg || failure.msg || failure.message || fallback
  );
}

async function bootstrap() {
  bootstrapping.value = true;
  try {
    const result = await getMedia({ current: 1, size: 200 });
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
    const result = await getIntegrationDocuments({
      current: current.value,
      keyword: query.keyword.trim() || undefined,
      mediaId: mediaId.value,
      size: size.value,
      status: query.status,
      storageType: query.storageType,
      type: query.type,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } catch (error) {
    ElMessage.error(readableError(error, '文档列表加载失败，请重试'));
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
  query.type = undefined;
  query.storageType = undefined;
  query.status = undefined;
  search();
}

function resetForm(row?: IntegrationDocument) {
  Object.assign(form, {
    description: row?.description || '',
    externalUrl: row?.externalUrl || '',
    id: row?.id,
    mediaId: mediaId.value,
    name: row?.name || '',
    originalFileName: row?.originalFileName,
    status: row?.status || 'DRAFT',
    storageType: row ? normalizeStorageType(row.storageType) : 'OBJECT',
    type: row?.type || 'OTHER',
    version: row?.version,
  });
  selectedFile.value = undefined;
  uploadFiles.value = [];
  uploadProgress.value = 0;
  void nextTick(() => {
    formRef.value?.clearValidate();
    initialFormSnapshot.value = JSON.stringify({
      description: form.description,
      externalUrl: form.externalUrl,
      name: form.name,
      status: form.status,
      storageType: form.storageType,
      type: form.type,
    });
  });
}

function openEditor(row?: IntegrationDocument) {
  resetForm(row);
  drawerVisible.value = true;
}

function handleSourceChange() {
  if (form.storageType === 'EXTERNAL') {
    selectedFile.value = undefined;
    uploadFiles.value = [];
  } else {
    form.externalUrl = '';
  }
  formRef.value?.clearValidate(['externalUrl']);
}

function handleFileChange(uploadFile: UploadFile, files: UploadFiles) {
  const raw = uploadFile.raw;
  if (!raw) return;
  const message = validateDocumentFile(raw);
  if (message) {
    ElMessage.warning(message);
    selectedFile.value = undefined;
    uploadFiles.value = [];
    return;
  }
  selectedFile.value = raw;
  uploadFiles.value = files.slice(-1);
  if (!form.name.trim()) {
    form.name = raw.name.replace(/\.[^.]+$/, '');
  }
}

function removeFile() {
  selectedFile.value = undefined;
  uploadFiles.value = [];
  uploadProgress.value = 0;
}

function handleExceed(files: File[]) {
  const file = files[0];
  if (!file) return;
  const message = validateDocumentFile(file);
  if (message) return ElMessage.warning(message);
  selectedFile.value = file;
  uploadFiles.value = [{ name: file.name, size: file.size }];
}

async function beforeClose(done: () => void) {
  if (saving.value) {
    ElMessage.warning('文档正在保存，请先等待完成或取消上传');
    return;
  }
  if (!formChanged.value) return done();
  try {
    await ElMessageBox.confirm(
      '当前修改尚未保存，关闭后将丢失这些内容。',
      '放弃未保存的修改？',
      {
        cancelButtonText: '继续编辑',
        confirmButtonText: '放弃修改',
        type: 'warning',
      },
    );
    done();
  } catch {
    // User intentionally stays in the drawer.
  }
}

async function submit() {
  if (saving.value) return;
  if (!(await formRef.value?.validate())) return;
  if (form.storageType === 'OBJECT' && !form.id && !selectedFile.value) {
    ElMessage.warning('请选择要上传的文档文件');
    return;
  }
  saving.value = true;
  saveAbortController.value = new AbortController();
  uploadProgress.value = selectedFile.value ? 1 : 0;
  try {
    await saveIntegrationDocument(
      {
        description: form.description?.trim() || undefined,
        externalUrl:
          form.storageType === 'EXTERNAL'
            ? form.externalUrl?.trim()
            : undefined,
        id: form.id,
        mediaId: mediaId.value,
        name: form.name.trim(),
        status: form.status,
        storageType: form.storageType,
        type: form.type,
        version: form.version,
      },
      selectedFile.value,
      (percent) => (uploadProgress.value = percent),
      saveAbortController.value.signal,
    );
    ElMessage.success(form.id ? '文档更新成功' : '文档新增成功');
    initialFormSnapshot.value = JSON.stringify({
      description: form.description,
      externalUrl: form.externalUrl,
      name: form.name,
      status: form.status,
      storageType: form.storageType,
      type: form.type,
    });
    selectedFile.value = undefined;
    drawerVisible.value = false;
    await load();
  } catch (error) {
    const failure = error as { code?: string; name?: string };
    if (failure.code === 'ERR_CANCELED' || failure.name === 'AbortError') {
      ElMessage.info('上传已取消，表单内容仍为你保留');
    } else {
      ElMessage.error(readableError(error, '保存失败，表单内容已为你保留'));
    }
  } finally {
    saving.value = false;
    saveAbortController.value = undefined;
  }
}

function cancelUpload() {
  saveAbortController.value?.abort();
}

function openExternal(row: IntegrationDocument) {
  const target = row.externalUrl;
  if (!target || validateExternalUrl(target)) {
    ElMessage.error('外部链接无效，无法打开');
    return;
  }
  const child = window.open(target, '_blank', 'noopener,noreferrer');
  if (child) child.opener = null;
}

function preview(row: IntegrationDocument) {
  if (row.storageType === 'EXTERNAL') return openExternal(row);
  previewDocument.value = row;
  previewVisible.value = true;
}

async function download(row: IntegrationDocument) {
  try {
    const blob = await getIntegrationDocumentContent(row.id);
    const url = URL.createObjectURL(blob);
    const anchor = window.document.createElement('a');
    anchor.href = url;
    anchor.download = row.originalFileName || row.name;
    anchor.rel = 'noopener';
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
    ElMessage.success('已开始下载');
  } catch (error) {
    ElMessage.error(readableError(error, '下载失败，请稍后重试'));
  }
}

async function remove(row: IntegrationDocument): Promise<boolean> {
  if (!canDelete.value) return false;
  try {
    const { value } = await ElMessageBox.prompt(
      `此操作会永久删除“${row.name}”及其底层文件，且不可恢复。请输入完整文档名称确认。`,
      '永久删除文档',
      {
        beforeClose: (action, instance, done) => {
          if (action === 'confirm' && instance.inputValue !== row.name) {
            instance.editorErrorMessage = '输入的文档名称不一致';
            return;
          }
          done();
        },
        cancelButtonText: '取消',
        confirmButtonText: '永久删除',
        distinguishCancelAndClose: true,
        inputPlaceholder: row.name,
        type: 'error',
      },
    );
    if (value !== row.name) return false;
    deletingId.value = row.id;
    await deleteIntegrationDocument(row.id, row.version);
    ElMessage.success('文档已永久删除');
    if (rows.value.length === 1 && current.value > 1) current.value -= 1;
    await load();
    return true;
  } catch (error) {
    if (error === 'cancel' || error === 'close') return false;
    const failure = error as { response?: { status?: number } };
    const status = failure.response?.status;
    let suggestion = '删除失败，请刷新后重试。';
    if (status === 409) {
      suggestion =
        '文档可能被业务引用或已被他人修改。请刷新列表；存在引用时将状态改为“已退役”。';
    } else if (status === 503) {
      suggestion =
        '底层文件暂未删除，请检查存储服务后重试，文档元数据仍会保留。';
    }
    ElMessage.error(`${readableError(error, '永久删除失败')} ${suggestion}`);
    return false;
  } finally {
    deletingId.value = '';
  }
}

async function removeCurrent() {
  if (!form.id) return;
  const row = rows.value.find((item) => item.id === form.id);
  if (!row) {
    ElMessage.error('文档已不在当前列表，请刷新后重试');
    return;
  }
  if (await remove(row)) drawerVisible.value = false;
}

function statusTagType(status: IntegrationDocumentStatus) {
  if (status === 'ACTIVE') return 'success';
  if (status === 'DRAFT') return 'warning';
  if (status === 'RETIRED') return 'info';
  return 'danger';
}

watch(mediaId, () => {
  current.value = 1;
  void load();
});
void bootstrap();
</script>

<template>
  <main class="document-page">
    <header class="document-hero">
      <div>
        <span class="document-eyebrow">Delivery knowledge</span>
        <h1>接入文档管理</h1>
        <p>
          统一管理媒体接口、回传协议与交付规范。上传文件始终使用系统当前启用的默认存储配置。
        </p>
      </div>
      <div class="hero-actions">
        <div class="media-scope">
          <span>当前媒体范围</span>
          <el-select
            v-model="mediaId"
            :loading="bootstrapping"
            :disabled="!hasMedia"
            filterable
            placeholder="选择媒体"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            >
              <span>{{ item.name }}</span>
              <span class="media-code">{{ item.code }}</span>
            </el-option>
          </el-select>
        </div>
        <el-button
          v-if="canWrite"
          :disabled="!mediaId"
          :icon="DocumentAdd"
          type="primary"
          size="large"
          @click="openEditor()"
        >
          新增文档
        </el-button>
      </div>
    </header>

    <el-card
      v-if="hasMedia || bootstrapping"
      class="document-card"
      shadow="never"
    >
      <section class="filter-bar" aria-label="文档筛选">
        <el-input
          v-model="query.keyword"
          clearable
          :prefix-icon="Search"
          placeholder="搜索名称、文件名或描述"
          @clear="search"
          @keyup.enter="search"
        />
        <el-select v-model="query.type" clearable placeholder="文档类型">
          <el-option
            v-for="item in TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.storageType" clearable placeholder="来源">
          <el-option label="上传文件" value="OBJECT" />
          <el-option label="外部链接" value="EXTERNAL" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option
            v-for="item in STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button :icon="Search" type="primary" plain @click="search">
          查询
        </el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
      </section>

      <div class="table-meta">
        <div>
          <strong>{{ selectedMediaName }}</strong>
          <span>共 {{ total }} 份接入文档</span>
        </div>
        <span class="storage-note">受控内容 · 私有读取 · 权限隔离</span>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        class="document-table"
      >
        <el-table-column label="文档" min-width="260">
          <template #default="{ row }: { row: IntegrationDocument }">
            <div class="document-name-cell">
              <span class="file-mark">{{
                row.storageType === 'OBJECT' ? 'DOC' : 'URL'
              }}</span>
              <div>
                <strong>{{ row.name }}</strong>
                <span>
                  {{
                    row.storageType === 'OBJECT'
                      ? row.originalFileName || '历史受管文件'
                      : externalDomain(row.externalUrl)
                  }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="140">
          <template #default="{ row }: { row: IntegrationDocument }">
            {{ TYPE_LABELS[row.type] || row.type }}
          </template>
        </el-table-column>
        <el-table-column label="来源" width="112">
          <template #default="{ row }: { row: IntegrationDocument }">
            <el-tag
              effect="plain"
              :type="row.storageType === 'OBJECT' ? 'primary' : 'info'"
            >
              {{ row.storageType === 'OBJECT' ? '上传文件' : '外部链接' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务版本" width="96">
          <template #default="{ row }: { row: IntegrationDocument }">
            <span class="version-chip">v{{ row.documentVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column
          class-name="secondary-column"
          label="文件大小"
          width="110"
        >
          <template #default="{ row }: { row: IntegrationDocument }">
            {{
              row.storageType === 'OBJECT' ? formatFileSize(row.fileSize) : '—'
            }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: IntegrationDocument }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          class-name="secondary-column"
          label="更新时间"
          prop="updateTime"
          min-width="168"
        />
        <el-table-column align="right" fixed="right" label="操作" width="250">
          <template #default="{ row }: { row: IntegrationDocument }">
            <div class="desktop-actions">
              <el-button :icon="View" link type="primary" @click="preview(row)">
                {{ row.storageType === 'OBJECT' ? '预览' : '打开' }}
              </el-button>
              <el-button
                v-if="row.storageType === 'OBJECT'"
                :icon="Download"
                link
                @click="download(row)"
              >
                下载
              </el-button>
              <el-button
                v-if="canWrite"
                :icon="EditPen"
                link
                @click="openEditor(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canDelete"
                :loading="deletingId === row.id"
                :icon="Delete"
                link
                type="danger"
                @click="remove(row)"
              >
                删除
              </el-button>
            </div>
            <el-dropdown class="mobile-actions" trigger="click">
              <el-button :icon="MoreFilled" circle aria-label="更多操作" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="View" @click="preview(row)">
                    {{ row.storageType === 'OBJECT' ? '预览' : '打开外链' }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.storageType === 'OBJECT'"
                    :icon="Download"
                    @click="download(row)"
                  >
                    下载
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canWrite"
                    :icon="EditPen"
                    @click="openEditor(row)"
                  >
                    编辑
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canDelete"
                    :icon="Delete"
                    divided
                    @click="remove(row)"
                  >
                    永久删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            :description="
              query.keyword || query.type || query.storageType || query.status
                ? '没有符合当前筛选条件的文档'
                : '当前媒体范围尚无接入文档'
            "
          >
            <el-button
              v-if="canWrite && !query.keyword"
              type="primary"
              @click="openEditor()"
            >
              上传第一份文档
            </el-button>
            <el-button v-else-if="query.keyword" @click="resetSearch">
              清空筛选
            </el-button>
          </el-empty>
        </template>
      </el-table>

      <footer class="pagination-bar">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @change="load"
        />
      </footer>
    </el-card>

    <el-card v-else class="no-scope-card" shadow="never">
      <el-empty description="你当前没有可访问的媒体范围">
        <p>请联系管理员授予媒体对象权限后再管理接入文档。</p>
        <el-button :icon="Refresh" @click="bootstrap">重新检查权限</el-button>
      </el-empty>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      class="document-editor-drawer"
      :before-close="beforeClose"
      :close-on-click-modal="false"
      size="min(720px, 100vw)"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-heading">
          <span>{{ form.id ? '更新接入资料' : '创建接入资料' }}</span>
          <strong>{{ form.id ? '编辑文档' : '新增文档' }}</strong>
          <small>媒体范围：{{ selectedMediaName }}</small>
        </div>
      </template>

      <el-form
        ref="formRef"
        class="document-form"
        label-position="top"
        :model="form"
        :rules="formRules"
        @submit.prevent="submit"
      >
        <div class="form-grid">
          <el-form-item label="文档名称" prop="name">
            <el-input
              v-model="form.name"
              maxlength="128"
              placeholder="例如：巨量引擎转化回传协议"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="文档类型" prop="type">
            <el-select v-model="form.type" class="w-full">
              <el-option
                v-for="item in TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="文档描述" prop="description">
          <el-input
            v-model="form.description"
            maxlength="500"
            :rows="3"
            placeholder="补充适用场景、对接范围或注意事项"
            resize="none"
            show-word-limit
            type="textarea"
          />
        </el-form-item>

        <el-form-item label="文档来源">
          <div class="source-switch">
            <button
              :class="{ active: form.storageType === 'OBJECT' }"
              type="button"
              @click="
                form.storageType = 'OBJECT';
                handleSourceChange();
              "
            >
              <el-icon><UploadFilled /></el-icon>
              <span
                ><strong>上传文件</strong><small>系统默认存储配置</small></span
              >
            </button>
            <button
              :class="{ active: form.storageType === 'EXTERNAL' }"
              type="button"
              @click="
                form.storageType = 'EXTERNAL';
                handleSourceChange();
              "
            >
              <el-icon><Link /></el-icon>
              <span><strong>外部链接</strong><small>HTTP / HTTPS</small></span>
            </button>
          </div>
        </el-form-item>

        <template v-if="form.storageType === 'OBJECT'">
          <el-form-item label="文档文件">
            <el-upload
              v-model:file-list="uploadFiles"
              class="document-uploader"
              drag
              action="#"
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              :on-exceed="handleExceed"
              :on-remove="removeFile"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-copy">
                <strong>
                  {{
                    selectedFile
                      ? selectedFile.name
                      : form.originalFileName || '拖拽文档到此处，或点击选择'
                  }}
                </strong>
                <span>
                  PDF、DOC/DOCX、XLSX、CSV、TXT、MD、JSON、YAML/YML · 最大 20MB
                </span>
              </div>
            </el-upload>
            <el-alert
              class="storage-alert"
              :closable="false"
              show-icon
              type="info"
            >
              <template #title>
                {{
                  form.id
                    ? '选择新文件将替换现有文件，并自动递增业务版本。'
                    : '文件将写入系统当前启用的默认本地或 OSS 存储。'
                }}
              </template>
            </el-alert>
            <el-progress
              v-if="saving && selectedFile"
              :percentage="uploadProgress"
              :status="uploadProgress === 100 ? 'success' : undefined"
            />
          </el-form-item>
        </template>
        <el-form-item v-else label="外部文档链接" prop="externalUrl">
          <el-input
            v-model="form.externalUrl"
            :prefix-icon="Link"
            placeholder="粘贴完整的 http:// 或 https:// 地址"
          />
          <p class="field-help">
            外链不会被后端抓取或站内嵌入；访问时将在安全的新窗口中打开。
          </p>
        </el-form-item>

        <el-form-item label="文档状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button
              v-for="item in STATUS_OPTIONS"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button
            v-if="form.id && canDelete"
            :icon="Delete"
            :loading="deletingId === form.id"
            type="danger"
            plain
            @click="removeCurrent"
          >
            永久删除
          </el-button>
          <span v-else>
            {{
              form.id
                ? `当前业务版本 v${rows.find((item) => item.id === form.id)?.documentVersion || 1}`
                : '新增后可随时编辑元数据或替换文件'
            }}
          </span>
          <div>
            <el-button
              :disabled="saving"
              @click="beforeClose(() => (drawerVisible = false))"
            >
              取消
            </el-button>
            <el-button
              v-if="saving && selectedFile"
              type="danger"
              plain
              @click="cancelUpload"
            >
              取消上传
            </el-button>
            <el-button :loading="saving" type="primary" @click="submit">
              {{ form.id ? '保存更新' : '新增文档' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <DocumentPreview v-model="previewVisible" :document="previewDocument" />
  </main>
</template>

<style scoped>
.document-page {
  --doc-accent: #0f766e;
  --doc-accent-soft: rgb(15 118 110 / 9%);
  --doc-line: rgb(15 23 42 / 9%);

  min-height: 100%;
  padding: 24px;
  color: var(--el-text-color-primary);
  background:
    radial-gradient(circle at 92% 2%, rgb(15 118 110 / 11%), transparent 26rem),
    var(--el-fill-color-lighter);
}

.document-hero {
  display: flex;
  gap: 28px;
  align-items: flex-end;
  justify-content: space-between;
  max-width: 1680px;
  margin: 0 auto 18px;
}

.document-eyebrow {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 800;
  color: var(--doc-accent);
  text-transform: uppercase;
  letter-spacing: 0.16em;
}

.document-hero h1 {
  margin: 0;
  font-size: clamp(26px, 2.4vw, 38px);
  font-weight: 750;
  line-height: 1.12;
  letter-spacing: -0.035em;
}

.document-hero p {
  max-width: 720px;
  margin: 9px 0 0;
  color: var(--el-text-color-secondary);
}

.hero-actions {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: flex-end;
}

.media-scope {
  display: grid;
  gap: 5px;
  width: 260px;
}

.media-scope > span {
  font-size: 12px;
  font-weight: 650;
  color: var(--el-text-color-secondary);
}

.media-code {
  float: right;
  margin-left: 28px;
  color: var(--el-text-color-placeholder);
}

.document-card,
.no-scope-card {
  max-width: 1680px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--doc-line);
  border-radius: 16px;
  box-shadow: 0 18px 48px rgb(15 23 42 / 6%);
}

.filter-bar {
  display: grid;
  grid-template-columns:
    minmax(230px, 1.7fr) repeat(3, minmax(130px, 0.8fr))
    auto auto;
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--doc-line);
}

.table-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 2px 12px;
}

.table-meta > div {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.table-meta span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.storage-note {
  padding: 5px 9px;
  background: var(--doc-accent-soft);
  border-radius: 999px;
}

.document-table :deep(th.el-table__cell) {
  height: 48px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.document-table :deep(td.el-table__cell) {
  height: 68px;
}

.document-name-cell {
  display: flex;
  gap: 12px;
  align-items: center;
}

.document-name-cell > div {
  display: grid;
  min-width: 0;
}

.document-name-cell strong,
.document-name-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-name-cell span {
  margin-top: 3px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.file-mark {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  margin: 0 !important;
  font-family: SFMono-Regular, Consolas, monospace;
  font-size: 10px !important;
  font-weight: 800;
  color: var(--doc-accent) !important;
  background: var(--doc-accent-soft);
  border: 1px solid rgb(15 118 110 / 16%);
  border-radius: 10px;
}

.version-chip {
  font-family: SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  font-weight: 650;
}

.desktop-actions {
  display: flex;
  justify-content: flex-end;
}

.mobile-actions {
  display: none;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

.no-scope-card {
  padding: 48px 0;
}

.no-scope-card p {
  margin: -8px 0 18px;
  color: var(--el-text-color-secondary);
}

.drawer-heading {
  display: grid;
}

.drawer-heading > span {
  font-size: 11px;
  font-weight: 800;
  color: var(--doc-accent);
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.drawer-heading strong {
  font-size: 20px;
}

.drawer-heading small {
  color: var(--el-text-color-secondary);
}

.document-form {
  padding: 4px 4px 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}

.source-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.source-switch button {
  display: flex;
  gap: 12px;
  align-items: center;
  min-height: 72px;
  padding: 14px 16px;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.source-switch button:hover,
.source-switch button:focus-visible {
  outline: none;
  border-color: var(--doc-accent);
  box-shadow: 0 0 0 3px var(--doc-accent-soft);
}

.source-switch button.active {
  color: var(--doc-accent);
  background: var(--doc-accent-soft);
  border-color: var(--doc-accent);
}

.source-switch .el-icon {
  flex: none;
  font-size: 24px;
}

.source-switch span {
  display: grid;
}

.source-switch small {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
}

.document-uploader {
  width: 100%;
}

.document-uploader :deep(.el-upload),
.document-uploader :deep(.el-upload-dragger) {
  width: 100%;
}

.document-uploader :deep(.el-upload-dragger) {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: center;
  padding: 28px 20px;
  text-align: left;
}

.upload-icon {
  flex: none;
  font-size: 34px;
  color: var(--doc-accent);
}

.upload-copy {
  display: grid;
}

.upload-copy span,
.field-help {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.storage-alert {
  margin-top: 10px;
}

.drawer-footer {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.drawer-footer > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.drawer-footer > div {
  display: flex;
  gap: 8px;
}

@media (max-width: 1100px) {
  .document-page {
    padding: 16px;
  }

  .document-hero {
    align-items: flex-start;
  }

  .hero-actions {
    align-items: stretch;
  }

  .filter-bar {
    grid-template-columns: minmax(220px, 2fr) repeat(3, 1fr);
  }

  .filter-bar .el-button {
    grid-column: span 1;
  }

  .document-table :deep(.secondary-column) {
    display: none;
  }

  .desktop-actions {
    display: none;
  }

  .mobile-actions {
    display: inline-flex;
  }
}

@media (max-width: 768px) {
  .document-page {
    padding: 12px;
  }

  .document-hero,
  .hero-actions {
    display: grid;
    width: 100%;
  }

  .media-scope {
    width: 100%;
  }

  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .filter-bar > :first-child {
    grid-column: 1 / -1;
  }

  .table-meta {
    align-items: flex-start;
  }

  .storage-note {
    display: none;
  }

  .form-grid,
  .source-switch {
    grid-template-columns: 1fr;
  }

  .drawer-footer > span {
    display: none;
  }

  .drawer-footer > div {
    width: 100%;
  }

  .drawer-footer .el-button {
    flex: 1;
    min-height: 44px;
  }
}

@media (max-width: 480px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }

  .filter-bar > * {
    grid-column: 1 !important;
  }

  .pagination-bar {
    overflow-x: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .document-page *,
  .document-page *::before,
  .document-page *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}

:global(.dark) .document-page {
  --doc-line: rgb(255 255 255 / 9%);

  background:
    radial-gradient(circle at 92% 2%, rgb(20 184 166 / 8%), transparent 26rem),
    #0d1518;
}
</style>
