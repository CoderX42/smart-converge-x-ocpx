<script lang="ts" setup>
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus';

import type { Media } from '#/api/ad';
import type { Organization } from '#/api/commercial';
import type {
  CreativeAsset,
  CreativeAssetRevision,
  LandingPage,
  ReviewCase,
  ReviewLog,
} from '#/api/creative';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { getMedia } from '#/api/ad';
import { getOrganizations } from '#/api/commercial';
import {
  createAsset,
  getAsset,
  getAssetRevisions,
  getAssets,
  getLandingPages,
  getReviewCases,
  getReviewLogs,
  publishAsset,
  publishLandingPage,
  reviewAsset,
  saveLandingPage,
  submitAsset,
  uploadAsset,
} from '#/api/creative';

import '../business-domain.css';

defineOptions({ name: 'CreativeWorkspace' });

const MAX_IMAGE_SIZE = 50 * 1024 * 1024;
const IMAGE_TYPES = new Set([
  'image/gif',
  'image/jpeg',
  'image/png',
  'image/webp',
]);
const ASSET_STATUS: Record<string, string> = {
  APPROVED: '已通过',
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
  REVIEWING: '审核中',
  SCANNING: '安全扫描中',
  SUSPENDED: '已停用',
  UPLOADED: '待送审',
};
const REVIEW_STATUS: Record<string, string> = {
  APPROVED: '已通过',
  REJECTED: '已驳回',
  SUBMITTED: '待审核',
};
const LANDING_STATUS: Record<string, string> = {
  ARCHIVED: '已归档',
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  SUSPENDED: '已停用',
};

const route = useRoute();
const media = ref<Media[]>([]);
const organizations = ref<Organization[]>([]);
const mediaId = ref('');
const orgId = ref('');
const keyword = ref('');
const status = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const assetRows = ref<CreativeAsset[]>([]);
const reviewRows = ref<ReviewCase[]>([]);
const landingRows = ref<LandingPage[]>([]);
const drawer = ref(false);
const detailDrawer = ref(false);
const detailLoading = ref(false);
const detailMode = ref<'asset' | 'review'>('asset');
const detailAsset = ref<CreativeAsset>();
const detailRevisions = ref<CreativeAssetRevision[]>([]);
const detailReviewLogs = ref<ReviewLog[]>([]);
const uploadMode = ref<'file' | 'url'>('file');
const selectedFile = ref<File>();
const uploadFiles = ref<UploadUserFile[]>([]);
const uploadPreview = ref('');
const uploadProgress = ref(0);
const uploadAbortController = ref<AbortController>();
const form = reactive<Record<string, number | string | undefined>>({});
const sectionKey = computed(() => route.path.split('/').findLast(Boolean));
const isAssets = computed(() => sectionKey.value === 'assets');
const isReviews = computed(() => sectionKey.value === 'reviews');
const pageTitle = computed(() => {
  if (isAssets.value) return '素材库';
  if (isReviews.value) return '素材审核';
  return '落地页';
});
const statusOptions = computed(() => {
  if (isReviews.value) return Object.entries(REVIEW_STATUS);
  if (isAssets.value) return Object.entries(ASSET_STATUS);
  return Object.entries(LANDING_STATUS);
});

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

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, {
    assetName: '',
    assetType: 'IMAGE',
    fileSize: undefined,
    fileUrl: '',
    height: undefined,
    mediaId: mediaId.value,
    mimeType: '',
    orgId: orgId.value,
    width: undefined,
  });
  uploadMode.value = 'file';
  clearSelectedFile();
}

function clearSelectedFile() {
  if (uploadPreview.value) URL.revokeObjectURL(uploadPreview.value);
  selectedFile.value = undefined;
  uploadFiles.value = [];
  uploadPreview.value = '';
  uploadProgress.value = 0;
}

async function bootstrap() {
  try {
    const [mediaResult, orgResult] = await Promise.all([
      getMedia({ current: 1, size: 200 }),
      getOrganizations({ current: 1, size: 200 }),
    ]);
    media.value = mediaResult.records || [];
    organizations.value = orgResult.records || [];
    mediaId.value = media.value[0]?.id || '';
    orgId.value = organizations.value[0]?.id || '';
  } catch (error) {
    ElMessage.error(readableError(error, '创意范围加载失败，请重试'));
  }
}

async function load() {
  if (!orgId.value || ((isAssets.value || isReviews.value) && !mediaId.value)) {
    return;
  }
  loading.value = true;
  try {
    const query = {
      current: current.value,
      keyword: keyword.value.trim() || undefined,
      mediaId: mediaId.value,
      orgId: orgId.value,
      size: size.value,
      status: status.value || undefined,
    };
    if (isAssets.value) {
      const result = await getAssets(query);
      assetRows.value = result.records || [];
      total.value = result.total || 0;
    } else if (isReviews.value) {
      const result = await getReviewCases(query);
      reviewRows.value = result.records || [];
      total.value = result.total || 0;
    } else {
      const result = await getLandingPages(query);
      landingRows.value = result.records || [];
      total.value = result.total || 0;
    }
  } catch (error) {
    ElMessage.error(readableError(error, `${pageTitle.value}加载失败，请重试`));
  } finally {
    loading.value = false;
  }
}

function openAsset(mode: 'file' | 'url' = 'file') {
  resetForm();
  uploadMode.value = mode;
  drawer.value = true;
}

function openLanding(row?: LandingPage) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      orgId: orgId.value,
      pageType: 'EXTERNAL',
      schemaVersion: 1,
    },
  );
  drawer.value = true;
}

function validateImage(file: File) {
  if (!IMAGE_TYPES.has(file.type)) return '仅支持 JPG、PNG、GIF、WebP 图片';
  if (file.size > MAX_IMAGE_SIZE) return '图片不能超过 50MB';
  return '';
}

function readImageSize(file: File) {
  const image = new Image();
  const url = URL.createObjectURL(file);
  image.addEventListener('load', () => {
    form.width = image.naturalWidth;
    form.height = image.naturalHeight;
    URL.revokeObjectURL(url);
  });
  image.addEventListener('error', () => URL.revokeObjectURL(url));
  image.src = url;
}

function selectImage(file: File, files: UploadUserFile[]) {
  const message = validateImage(file);
  if (message) {
    ElMessage.warning(message);
    clearSelectedFile();
    return;
  }
  clearSelectedFile();
  selectedFile.value = file;
  uploadFiles.value = files.slice(-1);
  uploadPreview.value = URL.createObjectURL(file);
  form.fileSize = file.size;
  form.mimeType = file.type;
  form.assetType = 'IMAGE';
  if (!String(form.assetName || '').trim()) {
    form.assetName = file.name.replace(/\.[^.]+$/, '');
  }
  readImageSize(file);
}

function handleFileChange(uploadFile: UploadFile, files: UploadFiles) {
  const file = uploadFile.raw;
  if (!file) return;
  selectImage(file, files);
}

function handleExceed(files: File[]) {
  const file = files[0];
  if (!file) return;
  selectImage(file, [{ name: file.name, size: file.size }]);
}

async function submit() {
  if (saving.value) return;
  saving.value = true;
  uploadAbortController.value = new AbortController();
  try {
    if (isAssets.value) {
      if (!String(form.assetName || '').trim()) {
        ElMessage.warning('请输入素材名称');
        return;
      }
      if (uploadMode.value === 'file') {
        if (!selectedFile.value) {
          ElMessage.warning('请选择要上传的图片');
          return;
        }
        await uploadAsset(
          {
            assetName: String(form.assetName).trim(),
            assetType: 'IMAGE',
            height: Number(form.height || 0) || undefined,
            mediaId: mediaId.value,
            orgId: orgId.value,
            width: Number(form.width || 0) || undefined,
          },
          selectedFile.value,
          (percent) => (uploadProgress.value = percent),
          uploadAbortController.value.signal,
        );
      } else {
        if (!String(form.fileUrl || '').trim()) {
          ElMessage.warning('请输入图片 URL');
          return;
        }
        await createAsset({
          assetName: String(form.assetName).trim(),
          assetType: 'IMAGE',
          fileHash: String(form.fileHash || '').trim(),
          fileSize: Number(form.fileSize || 1),
          fileUrl: String(form.fileUrl).trim(),
          mediaId: mediaId.value,
          mimeType: String(form.mimeType || 'image/*'),
          orgId: orgId.value,
        });
      }
      ElMessage.success('图片已上传到系统 OSS 并创建素材');
    } else {
      if (!form.pageName || !form.targetUrl) {
        ElMessage.warning('请填写落地页名称和目标地址');
        return;
      }
      await saveLandingPage(form);
      ElMessage.success('落地页已保存');
    }
    drawer.value = false;
    clearSelectedFile();
    await load();
  } catch (error) {
    if ((error as { code?: string }).code === 'ERR_CANCELED') {
      ElMessage.info('上传已取消，表单内容已保留');
    } else {
      ElMessage.error(readableError(error, '保存失败，请重试'));
    }
  } finally {
    saving.value = false;
    uploadAbortController.value = undefined;
  }
}

function cancelUpload() {
  uploadAbortController.value?.abort();
}

function beforeDrawerClose(done: () => void) {
  if (saving.value) {
    ElMessage.warning('正在保存，请先等待完成');
    return;
  }
  done();
}

async function openAssetDetail(row: CreativeAsset) {
  detailMode.value = 'asset';
  detailDrawer.value = true;
  detailLoading.value = true;
  try {
    const [asset, revisions] = await Promise.all([
      getAsset(row.id),
      getAssetRevisions(row.id),
    ]);
    detailAsset.value = asset;
    detailRevisions.value = revisions || [];
    detailReviewLogs.value = [];
  } catch (error) {
    ElMessage.error(readableError(error, '素材详情加载失败'));
  } finally {
    detailLoading.value = false;
  }
}

async function openReviewDetail(row: ReviewCase) {
  detailMode.value = 'review';
  detailDrawer.value = true;
  detailLoading.value = true;
  try {
    const [asset, logs] = await Promise.all([
      getAsset(row.assetId),
      getReviewLogs(row.id),
    ]);
    detailAsset.value = asset;
    detailReviewLogs.value = logs || [];
    detailRevisions.value = [];
  } catch (error) {
    ElMessage.error(readableError(error, '审核详情加载失败'));
  } finally {
    detailLoading.value = false;
  }
}

async function assetAction(row: CreativeAsset, action: 'publish' | 'submit') {
  try {
    await ElMessageBox.confirm(
      action === 'submit' ? '确认提交素材审核？' : '确认发布该素材？',
      '状态确认',
    );
    await (action === 'submit' ? submitAsset(row.id) : publishAsset(row.id));
    ElMessage.success('素材状态已更新');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, '状态更新失败'));
    }
  }
}

async function decide(row: ReviewCase, decision: 'APPROVE' | 'REJECT') {
  try {
    let comment: string | undefined;
    if (decision === 'REJECT') {
      const result = await ElMessageBox.prompt('请输入驳回原因', '审核意见', {
        cancelButtonText: '取消',
        confirmButtonText: '确认驳回',
        inputPlaceholder: '例如：图片尺寸不符合媒体要求',
        inputValidator: (value) => (value.trim() ? true : '驳回原因不能为空'),
        type: 'warning',
      });
      comment = result.value.trim();
    } else {
      await ElMessageBox.confirm('确认通过该素材审核？', '审核确认', {
        confirmButtonText: '通过',
        cancelButtonText: '取消',
        type: 'success',
      });
    }
    await reviewAsset(row.id, decision, comment);
    ElMessage.success('审核已完成');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, '审核失败'));
    }
  }
}

async function publishLanding(row: LandingPage) {
  try {
    await ElMessageBox.confirm(
      '发布后将允许创意绑定使用，确认继续？',
      '发布落地页',
    );
    await publishLandingPage(row.id);
    ElMessage.success('落地页已发布');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(readableError(error, '落地页发布失败'));
    }
  }
}

function statusType(value?: string) {
  if (['APPROVED', 'CLEAN', 'PUBLISHED'].includes(value || ''))
    return 'success';
  if (['ERROR', 'INFECTED', 'REJECTED', 'SUSPENDED'].includes(value || '')) {
    return 'danger';
  }
  if (['PENDING', 'REVIEWING', 'SCANNING', 'SUBMITTED'].includes(value || '')) {
    return 'warning';
  }
  return 'info';
}

function statusLabel(
  value: string | undefined,
  labels: Record<string, string>,
) {
  return (value && labels[value]) || value || '-';
}

function formatSize(size?: number) {
  if (!size) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function formatDimensions(row: { height?: number; width?: number }) {
  return row.width && row.height ? `${row.width} × ${row.height}` : '-';
}

watch([mediaId, orgId, () => route.path], () => {
  current.value = 1;
  void load();
});
void bootstrap().then(load);
</script>

<template>
  <main class="domain-page creative-page">
    <header class="domain-hero creative-hero">
      <div>
        <p class="domain-eyebrow">CREATIVE · ASSET LIFECYCLE</p>
        <h1 class="domain-title">{{ pageTitle }}</h1>
        <p class="domain-description">
          从系统 OSS
          上传图片，经过安全扫描、审核和版本管理后，再进入广告投放绑定。
        </p>
      </div>
      <div class="creative-hero-actions">
        <el-button
          v-if="isAssets"
          v-access:code="'creative:asset:write'"
          type="primary"
          @click="openAsset('file')"
        >
          上传图片
        </el-button>
        <el-button
          v-if="isAssets"
          v-access:code="'creative:asset:write'"
          plain
          @click="openAsset('url')"
        >
          URL 引用
        </el-button>
        <el-button
          v-else-if="!isReviews"
          v-access:code="'creative:landing-page:write'"
          type="primary"
          @click="openLanding()"
        >
          新增落地页
        </el-button>
      </div>
    </header>

    <el-card class="domain-card creative-card" shadow="never">
      <div class="domain-toolbar">
        <div class="domain-filters">
          <el-select v-model="orgId" filterable placeholder="选择组织">
            <el-option
              v-for="item in organizations"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-if="isAssets || isReviews"
            v-model="mediaId"
            filterable
            placeholder="选择媒体"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索名称或关键字"
            @keyup.enter="load"
          />
          <el-select v-model="status" clearable placeholder="全部状态">
            <el-option
              v-for="[value, label] in statusOptions"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
          <el-button type="primary" plain @click="load">查询</el-button>
        </div>
        <span class="creative-storage-note"
          >系统存储 · OSS 托管 · 权限隔离</span
        >
      </div>

      <el-table
        v-if="isAssets"
        v-loading="loading"
        :data="assetRows"
        row-key="id"
        class="creative-table"
        @row-click="openAssetDetail"
      >
        <el-table-column label="素材" min-width="270">
          <template #default="{ row }: { row: CreativeAsset }">
            <div class="creative-asset-cell">
              <el-image
                v-if="row.fileUrl"
                class="creative-thumb"
                fit="cover"
                :preview-src-list="[row.fileUrl]"
                preview-teleported
                :src="row.fileUrl"
              />
              <div v-else class="creative-thumb creative-thumb-fallback">
                IMG
              </div>
              <div>
                <strong>{{ row.assetName }}</strong>
                <span
                  >{{ row.mimeType || 'IMAGE' }} ·
                  {{ formatSize(row.fileSize) }}</span
                >
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="尺寸" width="150">
          <template #default="{ row }: { row: CreativeAsset }">
            {{ formatDimensions(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }: { row: CreativeAsset }">
            <el-tag effect="light" :type="statusType(row.status)">
              {{ statusLabel(row.status, ASSET_STATUS) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="170" />
        <el-table-column align="right" label="操作" width="240">
          <template #default="{ row }: { row: CreativeAsset }">
            <el-button link type="primary" @click.stop="openAssetDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'UPLOADED'"
              v-access:code="'creative:asset:write'"
              link
              type="primary"
              @click.stop="assetAction(row, 'submit')"
            >
              送审
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-access:code="'creative:asset:write'"
              link
              type="success"
              @click.stop="assetAction(row, 'publish')"
            >
              发布
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else-if="isReviews"
        v-loading="loading"
        :data="reviewRows"
        row-key="id"
        class="creative-table"
      >
        <el-table-column label="审核任务" min-width="230">
          <template #default="{ row }: { row: ReviewCase }">
            <button
              class="creative-link"
              type="button"
              @click="openReviewDetail(row)"
            >
              {{ row.id }}
            </button>
            <span class="creative-subtext">素材 {{ row.assetId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="安全扫描" width="140">
          <template #default="{ row }: { row: ReviewCase }">
            <el-tag effect="plain" :type="statusType(row.virusScanStatus)">
              {{ row.virusScanStatus || '待扫描' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" prop="submittedAt" min-width="170" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }: { row: ReviewCase }">
            <el-tag effect="light" :type="statusType(row.status)">
              {{ statusLabel(row.status, REVIEW_STATUS) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="200">
          <template #default="{ row }: { row: ReviewCase }">
            <el-button link type="primary" @click="openReviewDetail(row)"
              >查看</el-button
            >
            <template v-if="row.status === 'SUBMITTED'">
              <el-button
                v-access:code="'creative:review'"
                link
                type="success"
                @click="decide(row, 'APPROVE')"
              >
                通过
              </el-button>
              <el-button
                v-access:code="'creative:review'"
                link
                type="danger"
                @click="decide(row, 'REJECT')"
              >
                驳回
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        v-loading="loading"
        :data="landingRows"
        row-key="id"
        class="creative-table"
      >
        <el-table-column label="落地页名称" prop="pageName" min-width="190" />
        <el-table-column label="类型" prop="pageType" width="130" />
        <el-table-column
          label="目标地址"
          prop="targetUrl"
          min-width="280"
          show-overflow-tooltip
        />
        <el-table-column label="版本" prop="schemaVersion" width="90" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }: { row: LandingPage }">
            <el-tag effect="light" :type="statusType(row.status)">
              {{ statusLabel(row.status, LANDING_STATUS) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="170">
          <template #default="{ row }: { row: LandingPage }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'creative:landing-page:write'"
              link
              @click="openLanding(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'creative:landing-page:write'"
              link
              type="success"
              @click="publishLanding(row)"
            >
              发布
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="domain-pagination">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawer"
      :before-close="beforeDrawerClose"
      :title="
        isAssets
          ? uploadMode === 'file'
            ? '上传图片到系统 OSS'
            : 'URL 引用图片'
          : form.id
            ? '编辑落地页'
            : '新增落地页'
      "
      size="520px"
      @closed="clearSelectedFile"
    >
      <el-form label-position="top" :model="form">
        <template v-if="isAssets">
          <el-form-item label="素材来源">
            <el-radio-group v-model="uploadMode" :disabled="saving">
              <el-radio-button label="file">系统 OSS 上传</el-radio-button>
              <el-radio-button label="url">外部 URL</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="素材名称" required>
            <el-input
              v-model="form.assetName"
              maxlength="128"
              show-word-limit
            />
          </el-form-item>
          <template v-if="uploadMode === 'file'">
            <el-upload
              class="creative-uploader"
              drag
              :auto-upload="false"
              :disabled="saving"
              :file-list="uploadFiles"
              :limit="1"
              :on-change="handleFileChange"
              :on-exceed="handleExceed"
              :show-file-list="false"
              accept=".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp"
            >
              <div v-if="uploadPreview" class="creative-upload-preview">
                <el-image fit="contain" :src="uploadPreview" />
                <span>点击重新选择图片</span>
              </div>
              <template v-else>
                <div class="creative-upload-icon">+</div>
                <div class="el-upload__text">
                  拖拽图片到这里，或 <em>点击选择</em>
                </div>
                <div class="el-upload__tip">
                  JPG / PNG / GIF / WebP，单张不超过 50MB
                </div>
              </template>
            </el-upload>
            <el-progress
              v-if="saving && selectedFile"
              :percentage="uploadProgress"
              :status="uploadProgress === 100 ? 'success' : undefined"
              class="creative-progress"
            />
            <div
              v-if="saving && uploadProgress < 100"
              class="creative-upload-actions"
            >
              <span>正在上传到系统 OSS… {{ uploadProgress }}%</span>
              <el-button link type="danger" @click="cancelUpload"
                >取消上传</el-button
              >
            </div>
            <div v-if="selectedFile" class="creative-file-meta">
              <span>{{ selectedFile.name }}</span>
              <span
                >{{ formatSize(selectedFile.size) }} ·
                {{ formatDimensions(form) }}</span
              >
            </div>
          </template>
          <template v-else>
            <el-form-item label="图片 URL" required>
              <el-input
                v-model="form.fileUrl"
                placeholder="https://example.com/image.jpg"
              />
            </el-form-item>
            <el-form-item label="SHA-256">
              <el-input
                v-model="form.fileHash"
                placeholder="外部 URL 素材建议填写文件哈希"
              />
            </el-form-item>
            <el-form-item label="MIME 类型">
              <el-input v-model="form.mimeType" placeholder="image/jpeg" />
            </el-form-item>
          </template>
        </template>
        <template v-else>
          <el-form-item label="落地页名称" required>
            <el-input v-model="form.pageName" />
          </el-form-item>
          <el-form-item label="页面类型">
            <el-select v-model="form.pageType" class="w-full">
              <el-option label="外部链接" value="EXTERNAL" />
              <el-option label="托管页面" value="HOSTED" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标地址" required>
            <el-input v-model="form.targetUrl" />
          </el-form-item>
          <el-form-item label="页面内容（JSON）">
            <el-input v-model="form.content" :rows="5" type="textarea" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <el-drawer
      v-model="detailDrawer"
      :title="detailMode === 'asset' ? '素材详情' : '审核详情'"
      size="620px"
    >
      <div v-loading="detailLoading" class="creative-detail">
        <div v-if="detailAsset" class="creative-detail-head">
          <el-image
            class="creative-detail-preview"
            fit="contain"
            :preview-src-list="[detailAsset.fileUrl]"
            preview-teleported
            :src="detailAsset.fileUrl"
          />
          <div>
            <p class="domain-eyebrow">{{ detailAsset.assetType }}</p>
            <h2>{{ detailAsset.assetName }}</h2>
            <el-tag :type="statusType(detailAsset.status)">
              {{ statusLabel(detailAsset.status, ASSET_STATUS) }}
            </el-tag>
          </div>
        </div>
        <div v-if="detailAsset" class="domain-detail-grid">
          <div class="domain-metric">
            <span class="domain-metric-label">图片尺寸</span
            ><strong class="domain-metric-value">{{
              formatDimensions(detailAsset)
            }}</strong>
          </div>
          <div class="domain-metric">
            <span class="domain-metric-label">文件大小</span
            ><strong class="domain-metric-value">{{
              formatSize(detailAsset.fileSize)
            }}</strong>
          </div>
          <div class="domain-metric">
            <span class="domain-metric-label">当前版本</span
            ><strong class="domain-metric-value">{{
              detailAsset.currentRevisionId ? '已锁定' : '-'
            }}</strong>
          </div>
        </div>
        <template v-if="detailMode === 'asset'">
          <h3>不可变版本</h3>
          <el-timeline v-if="detailRevisions.length > 0">
            <el-timeline-item
              v-for="revision in detailRevisions"
              :key="revision.id"
              :timestamp="revision.createdAt"
            >
              <strong>版本 {{ revision.version }}</strong>
              <span class="creative-subtext"
                >{{ formatDimensions(revision) }} ·
                {{ formatSize(revision.fileSize) }}</span
              >
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无版本记录" />
        </template>
        <template v-else>
          <h3>审核时间线</h3>
          <el-timeline v-if="detailReviewLogs.length > 0">
            <el-timeline-item
              v-for="log in detailReviewLogs"
              :key="log.id"
              :timestamp="log.createdAt"
            >
              <strong
                >{{ log.operatorName || '系统' }} · {{ log.toStatus }}</strong
              >
              <span class="creative-subtext">{{
                log.comment || '无补充意见'
              }}</span>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审核记录" />
        </template>
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.creative-hero {
  background:
    linear-gradient(135deg, rgb(255 255 255 / 92%), rgb(240 247 255 / 94%)),
    radial-gradient(circle at 80% 10%, rgb(45 126 255 / 15%), transparent 36%);
}

.creative-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.creative-card {
  overflow: hidden;
}

.creative-storage-note {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.creative-asset-cell {
  display: flex;
  gap: 12px;
  align-items: center;
}

.creative-asset-cell strong,
.creative-asset-cell span,
.creative-subtext {
  display: block;
}

.creative-asset-cell span,
.creative-subtext {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.creative-thumb {
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  overflow: hidden;
  border-radius: 10px;
}

.creative-thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.creative-link {
  padding: 0;
  color: var(--el-color-primary);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.creative-uploader :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 190px;
  border-radius: 14px;
}

.creative-upload-icon {
  margin-bottom: 10px;
  font-size: 36px;
  font-weight: 300;
  color: var(--el-color-primary);
}

.creative-upload-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.creative-upload-preview .el-image {
  width: 180px;
  height: 120px;
}

.creative-progress {
  margin-top: 12px;
}

.creative-upload-actions,
.creative-file-meta {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.creative-detail {
  min-height: 320px;
}

.creative-detail-head {
  display: flex;
  gap: 18px;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.creative-detail-head h2 {
  margin: 6px 0 12px;
}

.creative-detail-preview {
  flex: 0 0 140px;
  width: 140px;
  height: 120px;
  overflow: hidden;
  background: var(--el-fill-color-lighter);
  border-radius: 12px;
}

.creative-detail h3 {
  margin: 26px 0 14px;
  font-size: 15px;
}

@media (max-width: 768px) {
  .creative-storage-note {
    width: 100%;
  }
}
</style>
