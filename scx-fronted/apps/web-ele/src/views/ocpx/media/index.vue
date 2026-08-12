<script lang="ts" setup>
import type { Media } from '#/api/ocpx';
import { onMounted, reactive, ref } from 'vue';
import { Page } from '@boot/common-ui';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  archiveMedia,
  changeMediaStatus,
  checkMediaConnectivity,
  createMedia,
  listMedia,
  preflightMedia,
  updateMedia,
} from '#/api/ocpx';
defineOptions({ name: 'OcpxMediaManagement' });
const rows = ref<Media[]>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const editing = ref<Media>();
const query = reactive({ current: 1, keyword: '', size: 20, status: '' });
const form = reactive({
  adapterCode: 'STANDARD',
  authType: 'NONE',
  callbackHostAllowlist: '',
  callbackUrl: '',
  credentialConfigKey: '',
  credentialVersion: 1,
  ingressUri: '/feedback',
  macroParams: '',
  mediaCode: '',
  mediaName: '',
});
async function load() {
  loading.value = true;
  try {
    const result = await listMedia(query);
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}
function edit(row?: Media) {
  editing.value = row;
  Object.assign(form, {
    adapterCode: row?.adapterCode || 'STANDARD',
    authType: row?.authType || 'NONE',
    callbackHostAllowlist: row?.callbackHostAllowlist.join(',') || '',
    callbackUrl: row?.callbackUrl || '',
    credentialConfigKey: row?.credentialConfigKey || '',
    credentialVersion: row?.credentialVersion || 1,
    ingressUri: row?.ingressUri || '/feedback',
    macroParams: row?.macroParams.join(',') || '',
    mediaCode: row?.mediaCode || '',
    mediaName: row?.mediaName || '',
  });
  open.value = true;
}
async function save() {
  const data = {
    ...form,
    credentialConfigKey:
      form.authType === 'NONE' ? undefined : form.credentialConfigKey,
    credentialVersion:
      form.authType === 'NONE' ? undefined : form.credentialVersion,
    expectedVersion: editing.value?.configVersion,
  };
  if (editing.value) await updateMedia(editing.value.id, data);
  else await createMedia(data);
  ElMessage.success('保存成功');
  open.value = false;
  await load();
}
async function check(row: Media) {
  const result = await preflightMedia(row.id);
  result.status === 'PASS'
    ? ElMessage.success('配置预检通过')
    : ElMessage.warning(result.issues.join('、'));
}
async function connectivity(row: Media) {
  const result = await checkMediaConnectivity(row.id);
  result.networkCalled
    ? ElMessage.success(result.message)
    : ElMessage.warning(result.message);
}
async function toggle(row: Media) {
  await changeMediaStatus(row.id, {
    expectedVersion: row.configVersion,
    reason: '管理端状态变更',
    status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
  });
  await load();
}
async function archive(row: Media) {
  await ElMessageBox.confirm('确认归档该媒体？', '归档媒体');
  await archiveMedia(row.id, {
    expectedVersion: row.configVersion,
    reason: '管理端归档',
  });
  await load();
}
onMounted(load);
</script>
<template>
  <Page auto-content-height title="媒体管理"
    ><el-card shadow="never"
      ><div class="mb-4 flex gap-3">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="编码 / 名称"
        /><el-button @click="load">查询</el-button
        ><el-button
          v-access:code="'ocpx:media:add'"
          type="primary"
          @click="edit()"
          >新增媒体</el-button
        >
      </div>
      <el-alert
        class="mb-4"
        :closable="false"
        title="外部媒体账号与 Secret 不入库，Engine ACK 时按配置键和版本校验。"
        type="info" /><el-table v-loading="loading" :data="rows"
        ><el-table-column prop="mediaCode" label="编码" /><el-table-column
          prop="mediaName"
          label="名称"
        /><el-table-column prop="adapterCode" label="Adapter" /><el-table-column
          prop="authType"
          label="鉴权"
        /><el-table-column label="凭据引用"
          ><template #default="{ row }">{{
            row.credentialConfigKey
              ? `${row.credentialConfigKey} @ v${row.credentialVersion}`
              : '无'
          }}</template></el-table-column
        ><el-table-column prop="status" label="状态" /><el-table-column
          label="操作"
          width="300"
          ><template #default="{ row }"
            ><el-button
              v-access:code="'ocpx:media:edit'"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            ><el-button
              v-access:code="'ocpx:media:preflight'"
              link
              @click="check(row)"
              >预检</el-button
            ><el-button
              v-access:code="'ocpx:media:connectivity'"
              link
              @click="connectivity(row)"
              >连通性</el-button
            ><el-button
              v-access:code="'ocpx:media:status'"
              link
              @click="toggle(row)"
              >切换状态</el-button
            ><el-button
              v-access:code="'ocpx:media:archive'"
              link
              type="danger"
              @click="archive(row)"
              >归档</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-4"
        :total="total"
        @change="load" /></el-card
    ><el-dialog
      v-model="open"
      :title="editing ? '编辑媒体' : '新增媒体'"
      width="680px"
      ><el-form :model="form" label-width="110px"
        ><el-form-item label="媒体编码" required
          ><el-input
            v-model="form.mediaCode"
            :disabled="!!editing" /></el-form-item
        ><el-form-item label="媒体名称" required
          ><el-input v-model="form.mediaName" /></el-form-item
        ><el-form-item label="Adapter" required
          ><el-select v-model="form.adapterCode"
            ><el-option label="标准协议" value="STANDARD" /><el-option
              label="GET 模板"
              value="TEMPLATE_GET_DOWNSTREAM" /></el-select></el-form-item
        ><el-form-item label="鉴权类型"
          ><el-select v-model="form.authType"
            ><el-option
              v-for="v in ['NONE', 'TOKEN', 'SIGNATURE']"
              :key="v"
              :value="v"
              :label="v" /></el-select></el-form-item
        ><el-form-item
          v-if="form.authType !== 'NONE'"
          label="凭据配置键"
          required
          ><el-input v-model="form.credentialConfigKey" /></el-form-item
        ><el-form-item v-if="form.authType !== 'NONE'" label="凭据版本"
          ><el-input-number
            v-model="form.credentialVersion"
            :min="1" /></el-form-item
        ><el-form-item label="接入 URI" required
          ><el-input v-model="form.ingressUri" /></el-form-item
        ><el-form-item label="下游回调 URL"
          ><el-input v-model="form.callbackUrl" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    ></Page
  >
</template>
