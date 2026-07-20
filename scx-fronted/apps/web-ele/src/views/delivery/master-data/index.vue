<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import type { MasterType, Media } from '#/api/ad';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage } from 'element-plus';

import {
  getMaster,
  getMedia,
  getUpstreamCustomers,
  saveMaster,
  uploadIntegrationDocument,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdMasterData' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const route = useRoute();
const routeMasterType: Partial<Record<string, MasterType>> = {
  '/delivery/documents': 'integration-documents',
  '/delivery/media-channels': 'media-channels',
  '/delivery/products': 'products',
};
const active = ref<MasterType>(routeMasterType[route.path] ?? 'partners');
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const current = ref(1);
const size = ref(10);
const keyword = ref('');
const drawer = ref(false);
const saving = ref(false);
const partners = ref<any[]>([]);
const customers = ref<any[]>([]);
const documents = ref<any[]>([]);
const form = reactive<any>({});

const tabs = [
  { key: 'partners', label: '合作方', noun: '合作方' },
  { key: 'products', label: '产品', noun: '产品' },
  { key: 'media-channels', label: '媒体渠道', noun: '渠道' },
  { key: 'integration-documents', label: '接入文档', noun: '文档' },
] as const;
const currentTab = computed(() =>
  tabs.find((item) => item.key === active.value)!,
);
const directEntry = computed(() => Boolean(routeMasterType[route.path]));

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function loadOptions() {
  if (!mediaId.value) return;
  const [partnerResult, customerResult, documentResult] = await Promise.all([
    getMaster('partners', {
      current: 1,
      size: 200,
      mediaId: mediaId.value,
      status: 'ACTIVE',
    }),
    getUpstreamCustomers({
      current: 1,
      size: 200,
      mediaId: mediaId.value,
      status: 'ACTIVE',
    }),
    getMaster('integration-documents', {
      current: 1,
      size: 200,
      mediaId: mediaId.value,
    }),
  ]);
  partners.value = partnerResult.records || [];
  customers.value = customerResult.records || [];
  documents.value = documentResult.records || [];
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getMaster(active.value, {
      current: current.value,
      size: size.value,
      mediaId: mediaId.value,
      keyword: keyword.value,
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
      status: active.value === 'integration-documents' ? 'DRAFT' : 'ACTIVE',
      type: active.value === 'partners' ? 'UPSTREAM' : undefined,
      platform: active.value === 'products' ? 'ANDROID' : undefined,
      storageType:
        active.value === 'integration-documents' ? 'EXTERNAL' : undefined,
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.name || (active.value !== 'integration-documents' && !form.code)) {
    ElMessage.warning('请填写名称和编码');
    return;
  }
  form.mediaId = mediaId.value;
  saving.value = true;
  try {
    await saveMaster(active.value, form);
    ElMessage.success('已保存');
    drawer.value = false;
    await Promise.all([load(), loadOptions()]);
  } finally {
    saving.value = false;
  }
}

const beforeDocumentUpload: UploadProps['beforeUpload'] = (file) => {
  const extension = file.name.split('.').pop()?.toLowerCase();
  const allowedExtensions = [
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
  if (!extension || !allowedExtensions.includes(extension)) {
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
    ElMessage.success('文档已上传至对象存储，请保存文档信息');
  } catch (error) {
    options.onError(error as Parameters<typeof options.onError>[0]);
    ElMessage.error('文档上传失败');
  }
}

watch([mediaId, active], async () => {
  current.value = 1;
  await Promise.all([load(), loadOptions()]);
});
watch(
  () => route.path,
  (path) => {
    const type = routeMasterType[path];
    if (type) active.value = type;
  },
);
bootstrap().then(() => Promise.all([load(), loadOptions()]));
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control">
        <div class="mvp-scope-label mb-2 text-xs">当前媒体范围</div>
        <el-select v-model="mediaId" class="w-full" placeholder="选择媒体主体">
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
      <el-tabs v-if="!directEntry" v-model="active">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.key"
          :label="tab.label"
          :name="tab.key"
        />
      </el-tabs>
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="按名称或编码搜索"
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
          新增{{ currentTab.noun }}
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column
          v-if="active !== 'integration-documents'"
          label="编码"
          prop="code"
          min-width="150"
        >
          <template #default="{ row }">
            <span class="mvp-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" min-width="180" />
        <el-table-column
          v-if="active === 'products'"
          label="平台"
          prop="platform"
          width="110"
        />
        <el-table-column
          v-if="active === 'integration-documents'"
          label="版本"
          prop="documentVersion"
          width="90"
        />
        <el-table-column
          v-if="active === 'integration-documents'"
          label="受控引用"
          min-width="220"
        >
          <template #default="{ row }">
            <span class="mvp-code">{{ row.objectKey || row.externalUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="120">
          <template #default="{ row }">
            <el-tag
              class="mvp-status"
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" min-width="170" />
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
          <div class="mvp-empty">当前范围尚无{{ currentTab.noun }}数据</div>
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
      :title="`${form.id ? '编辑' : '新增'}${currentTab.noun}`"
      size="480px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item
          v-if="active !== 'integration-documents'"
          label="唯一编码"
          required
        >
          <el-input v-model="form.code" :disabled="Boolean(form.id)" />
        </el-form-item>
        <el-form-item v-if="active === 'partners'" label="合作方类型">
          <el-select v-model="form.type" class="w-full">
            <el-option label="上游业务合作方" value="UPSTREAM" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="active === 'products'" label="归属合作方" required>
          <el-select v-model="form.partnerId" class="w-full" filterable>
            <el-option
              v-for="item in partners"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="active === 'products'" label="上游客户">
          <el-select
            v-model="form.customerId"
            class="w-full"
            clearable
            filterable
          >
            <el-option
              v-for="item in customers"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="active === 'products'" label="产品平台">
          <el-select v-model="form.platform" class="w-full">
            <el-option label="Android" value="ANDROID" /><el-option
              label="iOS"
              value="IOS"
            /><el-option label="Web/其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="active === 'media-channels'" label="接入文档">
          <el-select v-model="form.documentId" class="w-full" clearable>
            <el-option
              v-for="item in documents"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <template v-if="active === 'integration-documents'">
          <el-form-item label="文档类型">
            <el-input
              v-model="form.type"
              placeholder="MEDIA_API / PRODUCT_API"
            />
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
            <div v-if="form.objectKey" class="mt-2 break-all text-sm text-gray-500">
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
        </template>
        <el-form-item v-if="active !== 'integration-documents'" label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="w-full">
            <el-option label="启用" value="ACTIVE" /><el-option
              label="停用"
              value="DISABLED"
            /><el-option
              v-if="active === 'integration-documents'"
              label="草稿"
              value="DRAFT"
            /><el-option
              v-if="active === 'integration-documents'"
              label="已退役"
              value="RETIRED"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
