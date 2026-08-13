<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { Media } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage } from 'element-plus';

import { createMedia, listMedia, updateMedia } from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import { allColumnKeys, PAGE_SIZES, resetPage } from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxMediaManagement' });

const columns = [
  { key: 'id', label: '渠道 ID' },
  { key: 'name', label: '渠道名称' },
  { key: 'uri', label: 'URI' },
  { key: 'credential', label: '凭据引用' },
  { key: 'macro', label: '宏参数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<Media[]>([]);
const loading = ref(false);
const saving = ref(false);
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ current: 1, keyword: '', size: 30 });
const dialogOpen = ref(false);
const editing = ref<Media>();
const formRef = ref<FormInstance>();
const form = reactive({
  credentialConfigKey: '',
  macroParams: '',
  mediaName: '',
  uri: '',
});
const rules: FormRules = {
  mediaName: [{ message: '请输入渠道名称', required: true }],
  uri: [
    { message: '请输入 URI', required: true },
    { message: 'URI 必须是以 / 开头的站内路径', pattern: /^\/[\w/-]*$/ },
  ],
};

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  loading.value = true;
  try {
    const result = await listMedia({ ...query });
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

function edit(row?: Media) {
  editing.value = row;
  Object.assign(form, {
    credentialConfigKey: row?.credentialConfigKey || '',
    macroParams: row?.macroParams || '',
    mediaName: row?.mediaName || '',
    uri: row?.uri || '',
  });
  dialogOpen.value = true;
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = {
      credentialConfigKey: form.credentialConfigKey || undefined,
      macroParams: form.macroParams || undefined,
      mediaName: form.mediaName,
      uri: form.uri,
    };
    await (editing.value
      ? updateMedia(editing.value.id, payload)
      : createMedia(payload));
    ElMessage.success('保存成功');
    dialogOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Page auto-content-height title="媒体管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-input
          v-model="query.keyword"
          aria-label="按媒体查询"
          clearable
          placeholder="渠道 ID / 渠道名称"
          @clear="search"
          @keyup.enter="search"
        />
        <el-button :disabled="loading" type="primary" @click="search"
          >查询</el-button
        >
        <el-button
          :disabled="loading"
          @click="
            Object.assign(query, { keyword: '' });
            search();
          "
          >重置</el-button
        >
      </div>
      <div class="ocpx-actions">
        <el-button
          v-access:code="'ocpx:media:add'"
          :disabled="loading || saving"
          type="primary"
          @click="edit()"
          >新增媒体</el-button
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
      <el-alert
        class="mb-4"
        :closable="false"
        title="管理端只维护服务端凭据引用，不显示或提交明文密钥。"
        type="info"
      />
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column v-if="hasColumn('id')" label="渠道 ID" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('name')"
          label="渠道名称"
          min-width="150"
          prop="mediaName"
        />
        <el-table-column v-if="hasColumn('uri')" label="URI" min-width="200"
          ><template #default="{ row }"><LongText :value="row.uri" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('credential')"
          label="凭据引用"
          min-width="180"
          ><template #default="{ row }"
            ><LongText :value="row.credentialConfigKey" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('macro')"
          label="宏参数"
          min-width="220"
          ><template #default="{ row }"
            ><LongText :value="row.macroParams" /></template
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
        <el-table-column fixed="right" label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              v-access:code="'ocpx:media:edit'"
              :disabled="saving"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            ></template
          ></el-table-column
        >
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
      :title="editing ? '编辑媒体' : '新增媒体'"
      width="640px"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="名称" prop="mediaName"
          ><el-input v-model="form.mediaName"
        /></el-form-item>
        <el-form-item label="URI" prop="uri"
          ><el-input v-model="form.uri" placeholder="例如 /feedback"
        /></el-form-item>
        <el-form-item label="凭据引用" prop="credentialConfigKey"
          ><el-input v-model="form.credentialConfigKey"
        /></el-form-item>
        <el-form-item label="宏参数" prop="macroParams"
          ><el-input v-model="form.macroParams" :rows="4" type="textarea"
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
