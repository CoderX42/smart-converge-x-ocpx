<script lang="ts" setup>
import type { PromotionLink } from '#/api/ocpx';
import { onMounted, reactive, ref } from 'vue';
import { Page } from '@boot/common-ui';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  archivePromotionLink,
  changePromotionLinkStatus,
  createPromotionLink,
  listPromotionLinks,
  promotionLinkAdvertisers,
  updatePromotionLink,
  validatePromotionLinkUrl,
} from '#/api/ocpx';

defineOptions({ name: 'OcpxPromotionLinkManagement' });
const rows = ref<PromotionLink[]>([]);
const advertisers = ref<Array<{ code: string; id: string; name: string }>>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const editing = ref<PromotionLink>();
const query = reactive({
  advertiserId: '',
  current: 1,
  keyword: '',
  size: 20,
  status: '',
});
const form = reactive({
  advertiserId: '',
  deeplinkUrl: '',
  downloadUrl: '',
  linkChannel: '',
  linkCode: '',
  linkName: '',
  linkType: 'LANDING',
  linkUrlTemplate: '',
  packageName: '',
  platform: 'ANDROID',
  redirectEnabled: true,
  remark: '',
});
async function load() {
  loading.value = true;
  try {
    const result = await listPromotionLinks(query);
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}
function edit(row?: PromotionLink) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    deeplinkUrl: row?.deeplinkUrl || '',
    downloadUrl: row?.downloadUrl || '',
    linkChannel: row?.linkChannel || '',
    linkCode: row?.linkCode || '',
    linkName: row?.linkName || '',
    linkType: row?.linkType || 'LANDING',
    linkUrlTemplate: row?.linkUrlTemplate || '',
    packageName: row?.packageName || '',
    platform: row?.platform || 'ANDROID',
    redirectEnabled: row?.redirectEnabled ?? true,
    remark: row?.remark || '',
  });
  open.value = true;
}
async function save() {
  const data = {
    ...form,
    expectedVersion: editing.value?.configVersion,
    macroSet: '',
  };
  if (editing.value) await updatePromotionLink(editing.value.id, data);
  else await createPromotionLink(data);
  ElMessage.success('保存成功');
  open.value = false;
  await load();
}
async function toggle(row: PromotionLink) {
  await changePromotionLinkStatus(row.id, {
    expectedVersion: row.configVersion,
    reason: '管理端状态变更',
    status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
  });
  await load();
}
async function validateUrl(row: PromotionLink) {
  const result = await validatePromotionLinkUrl(row.linkUrlTemplate);
  ElMessage.success(
    `URL 校验已完成：${String((result as any).status ?? 'PASS')}`,
  );
}
async function archive(row: PromotionLink) {
  await ElMessageBox.confirm('确认归档该推广链接？', '归档推广链接');
  await archivePromotionLink(row.id, {
    expectedVersion: row.configVersion,
    reason: '管理端归档',
  });
  await load();
}
onMounted(async () => {
  advertisers.value = await promotionLinkAdvertisers();
  await load();
});
</script>
<template>
  <Page auto-content-height title="推广链接"
    ><el-card shadow="never">
      <div class="mb-4 flex gap-3">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="编码 / 名称"
        /><el-select v-model="query.advertiserId" clearable placeholder="广告主"
          ><el-option
            v-for="item in advertisers"
            :key="item.id"
            :label="item.name"
            :value="item.id" /></el-select
        ><el-button @click="load">查询</el-button
        ><el-button
          v-access:code="'ocpx:promotion-link:add'"
          type="primary"
          @click="edit()"
          >新增链接</el-button
        >
      </div>
      <el-table v-loading="loading" :data="rows"
        ><el-table-column prop="linkCode" label="编码" /><el-table-column
          prop="linkName"
          label="名称"
        /><el-table-column
          prop="advertiserName"
          label="广告主"
        /><el-table-column prop="platform" label="平台" /><el-table-column
          prop="linkType"
          label="类型"
        /><el-table-column prop="status" label="状态" /><el-table-column
          label="操作"
          width="260"
          ><template #default="{ row }"
            ><el-button
              v-access:code="'ocpx:promotion-link:edit'"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            ><el-button
              v-access:code="'ocpx:promotion-link:status'"
              link
              @click="toggle(row)"
              >切换状态</el-button
            ><el-button
              v-access:code="'ocpx:promotion-link:validate'"
              link
              @click="validateUrl(row)"
              >校验 URL</el-button
            ><el-button
              v-access:code="'ocpx:promotion-link:archive'"
              link
              type="danger"
              @click="archive(row)"
              >归档</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-4"
        :total="total"
        @change="load"
      /> </el-card
    ><el-dialog
      v-model="open"
      :title="editing ? '编辑推广链接' : '新增推广链接'"
      width="700px"
      ><el-form :model="form" label-width="110px"
        ><el-form-item label="广告主" required
          ><el-select v-model="form.advertiserId"
            ><el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="链接编码" required
          ><el-input
            v-model="form.linkCode"
            :disabled="!!editing" /></el-form-item
        ><el-form-item label="链接名称" required
          ><el-input v-model="form.linkName" /></el-form-item
        ><el-form-item label="平台 / 类型"
          ><el-select v-model="form.platform"
            ><el-option
              v-for="v in ['ANDROID', 'IOS', 'WEB', 'OTHER']"
              :key="v"
              :label="v"
              :value="v" /></el-select
          ><el-select v-model="form.linkType" class="ml-2"
            ><el-option
              v-for="v in ['LANDING', 'DOWNLOAD', 'DEEPLINK', 'OTHER']"
              :key="v"
              :label="v"
              :value="v" /></el-select></el-form-item
        ><el-form-item label="URL 模板" required
          ><el-input
            v-model="form.linkUrlTemplate"
            type="textarea" /></el-form-item
        ><el-form-item label="Deeplink"
          ><el-input v-model="form.deeplinkUrl" /></el-form-item
        ><el-form-item label="下载地址"
          ><el-input v-model="form.downloadUrl" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    ></Page
  >
</template>
