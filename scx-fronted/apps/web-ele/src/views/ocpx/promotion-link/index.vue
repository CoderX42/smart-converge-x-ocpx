<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { EntityOption, PlatformCode, PromotionLink } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createPromotionLink,
  deletePromotionLink,
  listPromotionLinks,
  promotionLinkAdvertisers,
  updatePromotionLink,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import { allColumnKeys, PAGE_SIZES, platformLabel, resetPage } from '../table';

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
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ advertiserId: '', current: 1, keyword: '', size: 30 });
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
  platform: 1 as PlatformCode,
  remark: '',
});
const rules: FormRules = {
  advertiserId: [{ message: '请选择广告主', required: true }],
  linkName: [{ message: '请输入链接名称', required: true }],
  linkUrl: [{ message: '请输入投放链接', required: true }],
  platform: [{ message: '请选择平台', required: true }],
};

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  loading.value = true;
  try {
    const result = await listPromotionLinks({
      advertiserId: query.advertiserId || undefined,
      current: query.current,
      keyword: query.keyword || undefined,
      size: query.size,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
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
    platform: row?.platform || 1,
    remark: row?.remark || '',
  });
  dialogOpen.value = true;
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = {
      ...form,
      deeplink: form.deeplink || undefined,
      linkChannel: form.linkChannel || undefined,
      linkExt: form.linkExt || undefined,
      remark: form.remark || undefined,
    };
    await (editing.value
      ? updatePromotionLink(editing.value.id, payload)
      : createPromotionLink(payload));
    ElMessage.success('保存成功');
    dialogOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
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
          placeholder="广告主"
          @change="search"
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
          aria-label="按链接查询"
          clearable
          placeholder="链接编号 / 名称"
          @clear="search"
          @keyup.enter="search"
        />
        <el-button :disabled="loading" type="primary" @click="search"
          >查询</el-button
        >
        <el-button
          :disabled="loading"
          @click="
            Object.assign(query, { advertiserId: '', keyword: '' });
            search();
          "
          >重置</el-button
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
        <el-table-column fixed="right" label="操作" width="130">
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
      :title="editing ? '编辑推广链接' : '新增推广链接'"
      width="700px"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="广告主" prop="advertiserId"
          ><el-select v-model="form.advertiserId" filterable
            ><el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="平台" prop="platform"
          ><el-select v-model="form.platform"
            ><el-option
              v-for="(label, value) in platformLabel"
              :key="value"
              :label="label"
              :value="Number(value)" /></el-select
        ></el-form-item>
        <el-form-item label="名称" prop="linkName"
          ><el-input v-model="form.linkName"
        /></el-form-item>
        <el-form-item label="渠道号" prop="linkChannel"
          ><el-input v-model="form.linkChannel"
        /></el-form-item>
        <el-form-item label="投放链接" prop="linkUrl"
          ><el-input v-model="form.linkUrl" :rows="3" type="textarea"
        /></el-form-item>
        <el-form-item label="唤起链接" prop="deeplink"
          ><el-input v-model="form.deeplink"
        /></el-form-item>
        <el-form-item label="扩展参数" prop="linkExt"
          ><el-input v-model="form.linkExt" :rows="3" type="textarea"
        /></el-form-item>
        <el-form-item label="备注" prop="remark"
          ><el-input v-model="form.remark" :rows="3" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        ><el-button :loading="saving" type="primary" @click="save"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </Page>
</template>
