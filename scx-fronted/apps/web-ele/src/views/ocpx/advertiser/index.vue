<script lang="ts" setup>
import type { AdvertiserV2 } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveAdvertiser,
  changeAdvertiserStatusV2,
  createAdvertiserV2,
  listAdvertisers,
  updateAdvertiserV2,
} from '#/api/ocpx';

defineOptions({ name: 'OcpxAdvertiserManagement' });

const rows = ref<AdvertiserV2[]>([]);
const loading = ref(false);
const total = ref(0);
const filters = reactive({ current: 1, keyword: '', size: 20, status: '' });
const open = ref(false);
const editing = ref<AdvertiserV2>();
const form = reactive({
  adapterCode: 'TEMPLATE_GET',
  advertiserCode: '',
  advertiserName: '',
  bidUrl: '',
  credentialConfigKey: '',
  credentialVersion: 1,
  macroParams: '',
  upstreamHostAllowlist: '',
});

async function load() {
  loading.value = true;
  try {
    const result = await listAdvertisers({
      current: filters.current,
      keyword: filters.keyword || undefined,
      size: filters.size,
      status: filters.status || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function edit(row?: AdvertiserV2) {
  editing.value = row;
  Object.assign(form, {
    adapterCode: row?.adapterCode || 'TEMPLATE_GET',
    advertiserCode: row?.advertiserCode || '',
    advertiserName: row?.advertiserName || '',
    bidUrl: row?.bidUrl || '',
    credentialConfigKey: row?.credentialConfigKey || '',
    credentialVersion: row?.credentialVersion || 1,
    macroParams: row?.macroParams || '',
    upstreamHostAllowlist: row?.upstreamHostAllowlist || '',
  });
  open.value = true;
}

async function save() {
  const data = {
    adapterCode: form.adapterCode,
    bidUrl: form.bidUrl,
    credentialConfigKey: form.credentialConfigKey,
    credentialVersion: form.credentialVersion,
    macroParams: form.macroParams || undefined,
    name: form.advertiserName,
    upstreamHostAllowlist: form.upstreamHostAllowlist || undefined,
  };
  if (editing.value) {
    await updateAdvertiserV2(editing.value.id, {
      ...data,
      expectedVersion: editing.value.configVersion,
    });
  } else {
    await createAdvertiserV2({ ...data, code: form.advertiserCode });
  }
  ElMessage.success('保存成功');
  open.value = false;
  await load();
}

async function toggle(row: AdvertiserV2) {
  await changeAdvertiserStatusV2(row.id, {
    expectedVersion: row.configVersion,
    status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
  });
  await load();
}

async function archive(row: AdvertiserV2) {
  await ElMessageBox.confirm('确认归档该广告主？', '归档广告主');
  await archiveAdvertiser(row.id, { expectedVersion: row.configVersion });
  await load();
}

onMounted(load);
</script>

<template>
  <Page auto-content-height title="广告主管理">
    <el-card shadow="never">
      <div class="mb-4 flex gap-3">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="编码 / 名称"
          @keyup.enter="load"
        />
        <el-select v-model="filters.status" clearable placeholder="状态">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button @click="load">查询</el-button>
        <el-button
          v-access:code="'ocpx:advertiser:add'"
          type="primary"
          @click="edit()"
        >
          新增广告主
        </el-button>
      </div>
      <el-alert
        class="mb-4"
        :closable="false"
        title="账号和 Secret 仅保存在 Engine 配置中；管理库只保存配置键与版本。"
        type="info"
      />
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="编码" prop="advertiserCode" />
        <el-table-column label="名称" prop="advertiserName" />
        <el-table-column label="Adapter" prop="adapterCode" />
        <el-table-column label="凭据引用" min-width="180">
          <template #default="{ row }">
            {{ row.credentialConfigKey }} @ v{{ row.credentialVersion }}
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" />
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:advertiser:edit'"
              link
              type="primary"
              @click="edit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ocpx:advertiser:status'"
              link
              @click="toggle(row)"
            >
              切换状态
            </el-button>
            <el-button
              v-access:code="'ocpx:advertiser:archive'"
              link
              type="danger"
              @click="archive(row)"
            >
              归档
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="filters.current"
        v-model:page-size="filters.size"
        class="mt-4"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="open"
      :title="editing ? '编辑广告主' : '新增广告主'"
      width="720px"
    >
      <el-form :model="form" label-width="130px">
        <el-form-item label="广告主编码" required>
          <el-input v-model="form.advertiserCode" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="广告主名称" required>
          <el-input v-model="form.advertiserName" />
        </el-form-item>
        <el-form-item label="上游 Adapter" required>
          <el-input v-model="form.adapterCode" />
        </el-form-item>
        <el-form-item label="上游地址" required>
          <el-input v-model="form.bidUrl" />
        </el-form-item>
        <el-form-item label="凭据配置键" required>
          <el-input v-model="form.credentialConfigKey" />
        </el-form-item>
        <el-form-item label="凭据版本" required>
          <el-input-number v-model="form.credentialVersion" :min="1" />
        </el-form-item>
        <el-form-item label="宏参数">
          <el-input v-model="form.macroParams" type="textarea" />
        </el-form-item>
        <el-form-item label="上游主机白名单">
          <el-input v-model="form.upstreamHostAllowlist" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </Page>
</template>
