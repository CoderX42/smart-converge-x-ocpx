<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import type { MasterType, Media, MediaScope } from '#/api/ad';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage } from 'element-plus';

import {
  changeMediaStatus,
  getManagedMedia,
  getMaster,
  getMedia,
  getMediaScopes,
  getUpstreamCustomers,
  grantMediaScope,
  revokeMediaScope,
  saveMaster,
  saveMedia,
  uploadIntegrationDocument,
} from '#/api/ad';
import { getPage as getSystemUserPage } from '#/api/system/user';

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
const mediaManagerVisible = ref(false);
const mediaEditorVisible = ref(false);
const mediaSaving = ref(false);
const managedMediaLoading = ref(false);
const managedMedia = ref<Media[]>([]);
const managedMediaCurrent = ref(1);
const managedMediaSize = ref(10);
const managedMediaTotal = ref(0);
const mediaForm = reactive<Partial<Media>>({});
const scopeManagerVisible = ref(false);
const scopeLoading = ref(false);
const selectedScopeMedia = ref<Media>();
const mediaScopes = ref<MediaScope[]>([]);
const systemUsers = ref<any[]>([]);
const scopeUserId = ref('');

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

async function loadManagedMedia() {
  managedMediaLoading.value = true;
  try {
    const result = await getManagedMedia({
      current: managedMediaCurrent.value,
      size: managedMediaSize.value,
    });
    managedMedia.value = result.records || [];
    managedMediaTotal.value = result.total || 0;
  } finally {
    managedMediaLoading.value = false;
  }
}

async function openMediaManager() {
  mediaManagerVisible.value = true;
  await loadManagedMedia();
}

function editMedia(item?: Media) {
  Object.keys(mediaForm).forEach((key) => delete mediaForm[key as keyof Media]);
  Object.assign(
    mediaForm,
    item || {
      settlementCurrency: 'CNY',
      status: 'ACTIVE',
      timezone: 'Asia/Shanghai',
    },
  );
  mediaEditorVisible.value = true;
}

async function submitMedia() {
  const code = mediaForm.code?.trim() || '';
  const name = mediaForm.name?.trim() || '';
  const timezone = mediaForm.timezone?.trim() || '';
  if (!name || !code || !timezone) {
    ElMessage.warning('请填写媒体编码、名称和时区');
    return;
  }
  if (
    mediaForm.id &&
    (mediaForm.version === undefined || mediaForm.version === null)
  ) {
    ElMessage.warning('媒体版本已失效，请刷新后重试');
    return;
  }
  if (!/^[a-z0-9][a-z0-9-]{2,63}$/.test(code)) {
    ElMessage.warning('媒体编码须为小写字母、数字或连字符，长度 3-64 位');
    return;
  }
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: timezone }).format();
  } catch {
    ElMessage.warning('请输入有效的 IANA 时区，例如 Asia/Shanghai');
    return;
  }
  mediaForm.code = code;
  mediaForm.name = name;
  mediaForm.timezone = timezone;
  mediaForm.settlementCurrency = 'CNY';
  mediaSaving.value = true;
  try {
    await saveMedia(mediaForm);
    ElMessage.success('媒体已保存');
    mediaEditorVisible.value = false;
    await Promise.all([loadManagedMedia(), bootstrap()]);
    await Promise.all([load(), loadOptions()]);
  } finally {
    mediaSaving.value = false;
  }
}

async function toggleMediaStatus(item: Media) {
  const status = item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  await changeMediaStatus(item.id, { status, version: item.version });
  ElMessage.success(status === 'ACTIVE' ? '媒体已启用' : '媒体已停用');
  await Promise.all([loadManagedMedia(), bootstrap()]);
}

function onManagedMediaPageChange() {
  void loadManagedMedia();
}

async function loadScopes() {
  if (!selectedScopeMedia.value) return;
  scopeLoading.value = true;
  try {
    const [scopeResult, userResult] = await Promise.all([
      getMediaScopes({
        current: 1,
        mediaId: selectedScopeMedia.value.id,
        size: 200,
      }),
      getSystemUserPage({
        current: 1,
        delFlag: '0',
        size: 200,
        status: '0',
      }),
    ]);
    mediaScopes.value = scopeResult.records || [];
    systemUsers.value = (userResult.records || []).filter(
      (user: any) => user.status === '0' && user.delFlag !== '1',
    );
  } finally {
    scopeLoading.value = false;
  }
}

async function openScopeManager(item: Media) {
  selectedScopeMedia.value = item;
  scopeUserId.value = '';
  scopeManagerVisible.value = true;
  await loadScopes();
}

async function grantScope() {
  if (!scopeUserId.value || !selectedScopeMedia.value) {
    ElMessage.warning('请选择用户');
    return;
  }
  await grantMediaScope({
    mediaId: selectedScopeMedia.value.id,
    userId: scopeUserId.value,
  });
  scopeUserId.value = '';
  ElMessage.success('媒体范围已授权');
  await loadScopes();
}

async function revokeScope(item: MediaScope) {
  if (!selectedScopeMedia.value) return;
  await revokeMediaScope(item.id, selectedScopeMedia.value.id);
  ElMessage.success('媒体范围已撤销');
  await loadScopes();
}

function userLabel(userId: string) {
  const user = systemUsers.value.find((item) => item.id === userId);
  return user
    ? `${user.nickname || user.username}（${user.username}）`
    : userId;
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
        <div class="flex gap-2">
          <el-button
            v-access:code="'subject:media:write'"
            @click="openMediaManager"
          >
            媒体与授权
          </el-button>
          <el-button
            v-access:code="'ad:master:write'"
            type="primary"
            @click="open()"
          >
            新增{{ currentTab.noun }}
          </el-button>
        </div>
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

    <el-dialog
      v-model="mediaManagerVisible"
      title="媒体与对象范围"
      width="880px"
    >
      <div class="mb-4 flex justify-end">
        <el-button type="primary" @click="editMedia()">新增媒体</el-button>
      </div>
      <el-table
        v-loading="managedMediaLoading"
        :data="managedMedia"
        row-key="id"
      >
        <el-table-column label="编码" prop="code" min-width="150" />
        <el-table-column label="名称" prop="name" min-width="160" />
        <el-table-column label="时区" prop="timezone" min-width="140" />
        <el-table-column label="状态" prop="status" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="230">
          <template #default="{ row }">
            <el-button
              v-access:code="'subject:media:write'"
              link
              type="primary"
              @click="editMedia(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'subject:media-scope:read'"
              link
              type="primary"
              @click="openScopeManager(row)"
            >
              范围授权
            </el-button>
            <el-button
              v-access:code="'subject:media:write'"
              link
              @click="toggleMediaStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="managedMediaCurrent"
          v-model:page-size="managedMediaSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="managedMediaTotal"
          @change="onManagedMediaPageChange"
        />
      </div>
    </el-dialog>

    <el-dialog
      v-model="mediaEditorVisible"
      :title="mediaForm.id ? '编辑媒体' : '新增媒体'"
      width="520px"
    >
      <el-form label-position="top" :model="mediaForm">
        <el-form-item label="媒体编码" required>
          <el-input
            v-model="mediaForm.code"
            :disabled="Boolean(mediaForm.id)"
            maxlength="64"
            placeholder="小写字母、数字和连字符"
          />
        </el-form-item>
        <el-form-item label="媒体名称" required>
          <el-input v-model="mediaForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="时区" required>
          <el-input v-model="mediaForm.timezone" />
        </el-form-item>
        <el-form-item label="结算币种">
          <el-input v-model="mediaForm.settlementCurrency" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mediaEditorVisible = false">取消</el-button>
        <el-button
          v-access:code="'subject:media:write'"
          :loading="mediaSaving"
          type="primary"
          @click="submitMedia"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="scopeManagerVisible"
      :title="`范围授权 · ${selectedScopeMedia?.name || ''}`"
      width="680px"
    >
      <div class="mb-4 flex gap-2">
        <el-select
          v-model="scopeUserId"
          class="flex-1"
          filterable
          placeholder="选择有效用户"
        >
          <el-option
            v-for="user in systemUsers"
            :key="user.id"
            :label="`${user.nickname || user.username}（${user.username}）`"
            :value="user.id"
          />
        </el-select>
        <el-button
          v-access:code="'subject:media-scope:write'"
          type="primary"
          @click="grantScope"
        >
          授权
        </el-button>
      </div>
      <el-table v-loading="scopeLoading" :data="mediaScopes" row-key="id">
        <el-table-column label="用户" min-width="260">
          <template #default="{ row }">
            {{ userLabel(row.userId) }}
          </template>
        </el-table-column>
        <el-table-column label="授权时间" prop="createTime" min-width="180" />
        <el-table-column align="right" label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-access:code="'subject:media-scope:write'"
              link
              type="danger"
              @click="revokeScope(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </main>
</template>
