<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { EntityOption, PlatformCode, PromotionLink } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createPromotionLink,
  deletePromotionLink,
  listPromotionLinks,
  promotionLinkAdvertisers,
  promotionLinkOptions,
  updatePromotionLink,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  platformLabel,
  platformOptions,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxPromotionLinkManagement' });

const columns = [
  { key: 'id', label: '链接编号' },
  { key: 'name', label: '名称' },
  { key: 'advertiser', label: '广告主' },
  { key: 'platform', label: '平台' },
  { key: 'channel', label: '渠道号' },
  { key: 'linkUrl', label: '投放链接' },
  { key: 'deeplink', label: '唤起链接' },
  { key: 'linkExt', label: '扩展信息' },
  { key: 'remark', label: '备注' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<PromotionLink[]>([]);
const advertisers = ref<EntityOption[]>([]);
const linkSelects = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({
  advertiserId: '',
  current: 1,
  promotionLinkId: '',
  size: 30,
});
const dialogOpen = ref(false);
const editing = ref<PromotionLink>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserId: '',
  deeplink: '',
  linkChannel: '',
  linkExt: '',
  linkName: '',
  linkUrl: '',
  platform: undefined as PlatformCode | undefined,
  remark: '',
});
const rules: FormRules = {
  advertiserId: [{ message: '请选择广告主', required: true, trigger: 'blur' }],
  linkName: [{ message: '请输入名称', required: true, trigger: 'blur' }],
  linkUrl: [{ message: '请输入投放链接', required: true, trigger: 'blur' }],
  platform: [{ message: '请选择平台', required: true, trigger: 'blur' }],
};
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes(['ocpx:promotion-link:edit', 'ocpx:promotion-link:delete']),
);
const loadGuard = createLatestRequestGuard();
const linkOptionGuard = createLatestRequestGuard();

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listPromotionLinks({
      advertiserId: query.advertiserId || undefined,
      current: query.current,
      promotionLinkId: query.promotionLinkId || undefined,
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

async function loadLinkSelects(advertiserId: string, reset = true) {
  if (reset) query.promotionLinkId = '';
  const requestSequence = linkOptionGuard.begin();
  const result = await promotionLinkOptions(advertiserId || undefined);
  if (linkOptionGuard.isLatest(requestSequence)) linkSelects.value = result;
}

function search() {
  resetPage(query);
  void load();
}

function edit(row?: PromotionLink) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    deeplink: row?.deeplink || '',
    linkChannel: row?.linkChannel || '',
    linkExt: row?.linkExt || '',
    linkName: row?.linkName || '',
    linkUrl: row?.linkUrl || '',
    platform: row?.platform,
    remark: row?.remark || '',
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
      advertiserId: form.advertiserId,
      deeplink: form.deeplink || undefined,
      linkChannel: form.linkChannel || undefined,
      linkExt: form.linkExt || undefined,
      linkName: form.linkName,
      linkUrl: form.linkUrl,
      platform: form.platform!,
      remark: form.remark || undefined,
    };
    await (editingId
      ? updatePromotionLink(editingId, payload)
      : createPromotionLink(payload));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    await loadLinkSelects(query.advertiserId, false);
    await load();
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, {
    advertiserId: '',
    deeplink: '',
    linkChannel: '',
    linkExt: '',
    linkName: '',
    linkUrl: '',
    platform: undefined,
    remark: '',
  });
}

async function remove(row: PromotionLink) {
  await ElMessageBox.confirm(
    '删除后该推广链接不再可用，确认继续？',
    '删除推广链接',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deletePromotionLink(row.id);
    ElMessage.success('删除成功');
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  advertisers.value = await promotionLinkAdvertisers();
  await loadLinkSelects('');
  await load();
});
</script>

<template>
  <Page auto-content-height title="推广链接">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.advertiserId"
          aria-label="按广告主查询推广链接"
          clearable
          filterable
          placeholder="请选择广告主"
          @change="loadLinkSelects(query.advertiserId)"
        >
          <el-option
            v-for="item in advertisers"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="query.promotionLinkId"
          aria-label="按推广链接查询"
          clearable
          filterable
          placeholder="请选择广告主应用链接"
        >
          <el-option
            v-for="item in linkSelects"
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
          v-access:code="'ocpx:promotion-link:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
          >新增链接</el-button
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
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column v-if="hasColumn('id')" label="链接编号" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('name')"
          label="名称"
          min-width="150"
          prop="linkName"
        />
        <el-table-column
          v-if="hasColumn('advertiser')"
          label="广告主"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('platform')"
          label="平台"
          min-width="120"
          ><template #default="{ row }">{{
            platformLabel[row.platform]
          }}</template></el-table-column
        >
        <el-table-column
          v-if="hasColumn('channel')"
          label="渠道号"
          min-width="120"
          prop="linkChannel"
        />
        <el-table-column
          v-if="hasColumn('linkUrl')"
          label="投放链接"
          min-width="240"
          ><template #default="{ row }"
            ><LongText :value="row.linkUrl" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('deeplink')"
          label="唤起链接"
          min-width="220"
          ><template #default="{ row }"
            ><LongText :value="row.deeplink" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('linkExt')"
          label="扩展信息"
          min-width="200"
          ><template #default="{ row }"
            ><LongText :value="row.linkExt" /></template
        ></el-table-column>
        <el-table-column v-if="hasColumn('remark')" label="备注" min-width="160"
          ><template #default="{ row }"
            ><LongText :value="row.remark" /></template
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
          v-if="canOperate"
          fixed="right"
          label="操作"
          width="130"
        >
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:promotion-link:edit'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-access:code="'ocpx:promotion-link:delete'"
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
      :title="editing ? '编辑推广链接' : '新增推广链接'"
      width="700px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="所属广告主" prop="advertiserId"
          ><el-select
            v-model="form.advertiserId"
            filterable
            placeholder="请选择广告主"
            ><el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-radio-group v-model="form.platform">
            <el-radio
              v-for="item in platformOptions"
              :key="item.value"
              :value="item.value"
              >{{ item.label }}</el-radio
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="linkName"
          ><el-input
            v-model="form.linkName"
            autosize
            placeholder="请输入名称"
            type="textarea"
        /></el-form-item>
        <el-form-item label="渠道号" prop="linkChannel"
          ><el-input
            v-model="form.linkChannel"
            autosize
            placeholder="请输入渠道号"
            type="textarea"
        /></el-form-item>
        <el-form-item label="投放链接" prop="linkUrl"
          ><el-input
            v-model="form.linkUrl"
            autosize
            placeholder="请输入投放链接"
            type="textarea"
        /></el-form-item>
        <el-form-item label="唤起链接" prop="deeplink"
          ><el-input
            v-model="form.deeplink"
            autosize
            placeholder="请输入唤起链接"
            type="textarea"
        /></el-form-item>
        <el-form-item label="扩展参数" prop="linkExt"
          ><el-input
            v-model="form.linkExt"
            autosize
            placeholder="请输入参数"
            type="textarea"
        /></el-form-item>
        <el-form-item label="备注" prop="remark"
          ><el-input
            v-model="form.remark"
            autosize
            placeholder="请输入备注"
            type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        ><el-button :loading="saving" type="primary" @click="save"
          >确定</el-button
        ></template
      >
    </el-dialog>
  </Page>
</template>
