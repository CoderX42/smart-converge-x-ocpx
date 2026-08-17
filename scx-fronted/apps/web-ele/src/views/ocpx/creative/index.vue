<script lang="ts" setup>
import type {
  FormInstance,
  FormRules,
  UploadRequestOptions,
} from 'element-plus';

import type { Creative, CreativeAsset, EntityOption } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createCreative,
  deleteCreative,
  listCreativeAssets,
  listCreatives,
  promotionLinkAdvertisers,
  promotionLinkOptions,
  updateCreative,
  uploadCreativeAsset,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxCreativeManagement' });

type UploadError = Parameters<UploadRequestOptions['onError']>[0];

const columns = [
  { key: 'preview', label: '素材' },
  { key: 'creativeName', label: '创意名称' },
  { key: 'advertiserName', label: '广告主' },
  { key: 'dimension', label: '尺寸' },
  { key: 'fileSize', label: '文件大小' },
  { key: 'status', label: '状态' },
  { key: 'bindings', label: '绑定链接' },
  { key: 'remark', label: '备注' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<Creative[]>([]);
const advertisers = ref<EntityOption[]>([]);
const assets = ref<CreativeAsset[]>([]);
const promotionLinks = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const uploading = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({
  advertiserId: '',
  current: 1,
  keyword: '',
  size: 30,
  status: undefined as number | undefined,
});

const dialogOpen = ref(false);
const editing = ref<Creative>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserId: '',
  creativeName: '',
  promotionLinkIds: [] as string[],
  remark: '',
  sourceMaterialId: '',
  status: 1 as 0 | 1,
});
const rules: FormRules = {
  advertiserId: [
    { message: '请选择广告主', required: true, trigger: 'change' },
  ],
  creativeName: [
    { message: '请输入创意名称', required: true, trigger: 'blur' },
  ],
  sourceMaterialId: [
    { message: '请选择或上传图片素材', required: true, trigger: 'change' },
  ],
};

const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes(['ocpx:creative:edit', 'ocpx:creative:delete']),
);
const selectedAsset = computed(() =>
  assets.value.find((item) => item.sourceMaterialId === form.sourceMaterialId),
);
const loadGuard = createLatestRequestGuard();
const optionGuard = createLatestRequestGuard();

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

function formatFileSize(bytes?: number) {
  if (!bytes) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listCreatives({
      advertiserId: query.advertiserId || undefined,
      current: query.current,
      keyword: query.keyword || undefined,
      size: query.size,
      status: query.status,
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

async function loadOwnedOptions(advertiserId: string, keep = false) {
  if (!keep) {
    form.sourceMaterialId = '';
    form.promotionLinkIds = [];
  }
  if (!advertiserId) {
    assets.value = [];
    promotionLinks.value = [];
    return;
  }
  const requestSequence = optionGuard.begin();
  const [assetResult, linkResult] = await Promise.all([
    listCreativeAssets(advertiserId),
    promotionLinkOptions(advertiserId),
  ]);
  if (!optionGuard.isLatest(requestSequence)) return;
  assets.value = assetResult;
  promotionLinks.value = linkResult;
}

function search() {
  resetPage(query);
  void load();
}

async function edit(row?: Creative) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    creativeName: row?.creativeName || '',
    promotionLinkIds: row?.promotionLinks.map(({ id }) => id) || [],
    remark: row?.remark || '',
    sourceMaterialId: row?.sourceMaterialId || '',
    status: row?.status ?? 1,
  });
  await loadOwnedOptions(form.advertiserId, true);
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function upload(options: UploadRequestOptions) {
  if (!form.advertiserId) {
    ElMessage.warning('请先选择广告主');
    options.onError(new Error('请先选择广告主') as unknown as UploadError);
    return;
  }
  uploading.value = true;
  try {
    const uploaded = await uploadCreativeAsset(
      form.advertiserId,
      options.file as File,
    );
    if (uploaded.duplicateCreativeIds.length > 0) {
      await ElMessageBox.confirm(
        `该图片已被 ${uploaded.duplicateCreativeIds.length} 个创意使用，仍可复用素材创建新创意。`,
        '发现重复素材',
        { confirmButtonText: '继续使用', type: 'warning' },
      );
    }
    assets.value.unshift(uploaded);
    form.sourceMaterialId = uploaded.sourceMaterialId;
    options.onSuccess(uploaded);
    ElMessage.success('素材上传成功');
  } catch (error) {
    options.onError(error as UploadError);
  } finally {
    uploading.value = false;
  }
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = {
      advertiserId: form.advertiserId,
      creativeName: form.creativeName,
      promotionLinkIds: form.promotionLinkIds,
      remark: form.remark || undefined,
      sourceMaterialId: form.sourceMaterialId,
      status: form.status,
    };
    await (editing.value
      ? updateCreative(editing.value.id, payload)
      : createCreative(payload));
    ElMessage.success(editing.value ? '编辑成功' : '创建成功');
    dialogOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  assets.value = [];
  promotionLinks.value = [];
  Object.assign(form, {
    advertiserId: '',
    creativeName: '',
    promotionLinkIds: [],
    remark: '',
    sourceMaterialId: '',
    status: 1,
  });
}

async function remove(row: Creative) {
  await ElMessageBox.confirm(
    '删除创意会解除全部推广链接绑定，但不会删除已上传素材。确认继续？',
    '删除创意',
    { confirmButtonText: '确认删除', type: 'warning' },
  );
  deletingId.value = row.id;
  try {
    await deleteCreative(row.id);
    ElMessage.success('删除成功');
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  advertisers.value = await promotionLinkAdvertisers();
  await load();
});
</script>

<template>
  <Page auto-content-height title="创意管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.advertiserId"
          aria-label="按广告主查询创意"
          clearable
          filterable
          placeholder="请选择广告主"
        >
          <el-option
            v-for="item in advertisers"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="创意名称 / 素材文件名"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button :disabled="loading" type="primary" @click="search">
          搜索
        </el-button>
      </div>
      <div class="ocpx-actions">
        <el-button
          v-access:code="'ocpx:creative:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
        >
          新增创意
        </el-button>
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
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        max-height="560"
        size="small"
      >
        <el-table-column v-if="hasColumn('preview')" label="素材" width="88">
          <template #default="{ row }">
            <el-image
              class="h-12 w-12 rounded object-cover"
              fit="cover"
              :preview-src-list="[row.assetUrl]"
              preview-teleported
              :src="row.assetUrl"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('creativeName')"
          label="创意名称"
          min-width="160"
          prop="creativeName"
        />
        <el-table-column
          v-if="hasColumn('advertiserName')"
          label="广告主"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column v-if="hasColumn('dimension')" label="尺寸" width="110">
          <template #default="{ row }"
            >{{ row.width }} × {{ row.height }}</template
          >
        </el-table-column>
        <el-table-column
          v-if="hasColumn('fileSize')"
          label="文件大小"
          width="110"
        >
          <template #default="{ row }">{{
            formatFileSize(row.fileSize)
          }}</template>
        </el-table-column>
        <el-table-column v-if="hasColumn('status')" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('bindings')"
          label="绑定链接"
          width="100"
        >
          <template #default="{ row }">{{
            row.promotionLinks.length
          }}</template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('remark')"
          label="备注"
          min-width="150"
        >
          <template #default="{ row }"
            ><LongText :value="row.remark"
          /></template>
        </el-table-column>
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
          v-if="canOperate"
          fixed="right"
          label="操作"
          width="130"
        >
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:creative:edit'"
              link
              type="primary"
              @click="edit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ocpx:creative:delete'"
              :loading="deletingId === row.id"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
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
      :title="editing ? '编辑创意' : '新增创意'"
      width="720px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="所属广告主" prop="advertiserId">
          <el-select
            v-model="form.advertiserId"
            :disabled="Boolean(editing)"
            filterable
            placeholder="请选择广告主"
            @change="loadOwnedOptions(form.advertiserId)"
          >
            <el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创意名称" prop="creativeName">
          <el-input
            v-model="form.creativeName"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="图片素材" prop="sourceMaterialId">
          <div class="flex w-full flex-col gap-3">
            <el-select
              v-model="form.sourceMaterialId"
              filterable
              placeholder="选择已上传的自有素材"
            >
              <el-option
                v-for="item in assets"
                :key="item.sourceMaterialId"
                :label="`${item.fileName}（${formatFileSize(item.fileSize)}）`"
                :value="item.sourceMaterialId"
              />
            </el-select>
            <el-upload
              accept="image/jpeg,image/png,image/webp"
              :disabled="!form.advertiserId || uploading"
              :http-request="upload"
              :show-file-list="false"
            >
              <el-button :loading="uploading">上传新图片</el-button>
              <template #tip>
                <div class="el-upload__tip">
                  JPG / PNG / WEBP，最大 20MB，最长边 8192px，总像素不超过 4000
                  万。
                </div>
              </template>
            </el-upload>
            <el-image
              v-if="selectedAsset"
              class="h-32 w-48 rounded border object-contain"
              fit="contain"
              :src="selectedAsset.assetUrl"
            />
          </div>
        </el-form-item>
        <el-form-item label="绑定推广链接" prop="promotionLinkIds">
          <el-select
            v-model="form.promotionLinkIds"
            clearable
            collapse-tags
            filterable
            multiple
            placeholder="可多选；清空即解除全部绑定"
          >
            <el-option
              v-for="item in promotionLinks"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch
            v-model="form.status"
            active-text="启用"
            :active-value="1"
            inactive-text="停用"
            :inactive-value="0"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            maxlength="500"
            :rows="3"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
        <el-alert
          :closable="false"
          title="创意与推广链接是多对多绑定关系，不代表广告组，也不会生成创意级监测地址。"
          type="info"
        />
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        >
        <el-button :loading="saving" type="primary" @click="save"
          >确定</el-button
        >
      </template>
    </el-dialog>
  </Page>
</template>
