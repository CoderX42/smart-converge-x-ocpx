<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  getMedia,
  getMaster,
  saveMaster,
  uploadIntegrationDocument,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdDocuments' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const keyword = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const form = reactive<any>({});

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getMaster('integration-documents', {
      current: current.value,
      keyword: keyword.value,
      mediaId: mediaId.value,
      size: size.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function open(row?: any) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      mediaId: mediaId.value,
      status: 'DRAFT',
      storageType: 'EXTERNAL',
      type: 'EXTERNAL',
    },
  );
  drawer.value = true;
}

const beforeDocumentUpload: UploadProps['beforeUpload'] = (file) => {
  const extension = file.name.split('.').pop()?.toLowerCase();
  const allowed = [
    'csv',
    'doc',
    'docx',
    'json',
    'md',
    'pdf',
    'txt',
    'xlsx',
    'yaml',
    'yml',
  ];
  if (!extension || !allowed.includes(extension)) {
    ElMessage.warning('仅支持 PDF、Office、文本、CSV、JSON 和 YAML 文档');
    return false;
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文档大小不能超过 20MB');
    return false;
  }
  return true;
};

async function uploadDocumentFile(options: UploadRequestOptions) {
  const data = new FormData();
  data.append('file', options.file);
  data.append('mediaId', mediaId.value);
  try {
    const objectUrl = await uploadIntegrationDocument(data);
    form.objectKey = objectUrl;
    form.externalUrl = undefined;
    form.storageType = 'OBJECT';
    options.onSuccess(objectUrl);
    ElMessage.success('文档已上传到对象存储，请保存文档信息');
  } catch (error) {
    options.onError(error as Parameters<typeof options.onError>[0]);
    ElMessage.error('文档上传失败');
  }
}

async function submit() {
  if (!form.name) {
    ElMessage.warning('请填写文档名称');
    return;
  }
  if (form.storageType === 'EXTERNAL' && !form.externalUrl) {
    ElMessage.warning('请填写文档外链或使用对象存储上传');
    return;
  }
  if (form.storageType === 'OBJECT' && !form.objectKey) {
    ElMessage.warning('请先上传对象存储文档');
    return;
  }

  saving.value = true;
  try {
    await saveMaster('integration-documents', {
      ...form,
      mediaId: mediaId.value,
    });
    ElMessage.success('接入文档已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

watch(mediaId, async () => {
  current.value = 1;
  await load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control">
        <div class="mvp-scope-label mb-2 text-xs">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="`${item.name} · ${item.code}`"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="文档名称搜索"
            style="width: 260px"
            @keyup.enter="load"
          />
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:master:write'"
          type="primary"
          @click="open()"
        >
          新增文档
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="文档名称" prop="name" min-width="180" />
        <el-table-column label="文档类型" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">
              {{ row.type || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文档版本" prop="documentVersion" width="110" />
        <el-table-column label="受控引用" min-width="240">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.objectKey || row.externalUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" min-width="175" />
        <el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:master:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">当前范围尚无接入文档</div>
        </template>
      </el-table>
      <div class="mt-4 flex justify-end">
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
      v-model="drawer"
      :title="form.id ? '编辑文档' : '新增文档'"
      size="640px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="文档名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="form.type" class="w-full" clearable placeholder="选择文档类型">
            <el-option label="媒体回传协议" value="MEDIA_CALLBACK" />
            <el-option label="媒体接口规范" value="MEDIA_API" />
            <el-option label="产品接口规范" value="PRODUCT_API" />
            <el-option label="RTA 决策协议" value="RTA_SPEC" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="存储方式">
          <el-radio-group v-model="form.storageType">
            <el-radio-button value="EXTERNAL">沙箱外链</el-radio-button>
            <el-radio-button value="OBJECT">对象存储</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="form.storageType === 'OBJECT'"
          label="对象存储文档"
          required
        >
          <el-upload
            accept=".csv,.doc,.docx,.json,.md,.pdf,.txt,.xlsx,.yaml,.yml"
            :before-upload="beforeDocumentUpload"
            :disabled="!mediaId || saving"
            :http-request="uploadDocumentFile"
            :show-file-list="false"
          >
            <el-button type="primary">上传至对象存储</el-button>
          </el-upload>
          <div
            v-if="form.objectKey"
            class="mt-2 break-all text-sm text-gray-500"
          >
            已上传：{{ form.objectKey }}
          </div>
          <div v-else class="mt-2 text-sm text-gray-500">
            支持 PDF、Office、文本、CSV、JSON、YAML，单文件不超过 20MB。
          </div>
        </el-form-item>
        <el-form-item v-else label="沙箱外链" required>
          <el-input
            v-model="form.externalUrl"
            placeholder="http://localhost/..."
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="w-full">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
            <el-option label="已退役" value="RETIRED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit"
          >保存</el-button
        >
      </template>
    </el-drawer>
  </main>
</template>
