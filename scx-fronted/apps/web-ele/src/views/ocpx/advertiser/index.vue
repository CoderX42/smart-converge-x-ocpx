<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { AdvertiserV2 } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage } from 'element-plus';

import {
  createAdvertiserV2,
  listAdvertisers,
  rechargeAdvertiser,
  updateAdvertiserV2,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import { allColumnKeys, PAGE_SIZES, resetPage } from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxAdvertiserManagement' });

const columns = [
  { key: 'id', label: '广告主编号' },
  { key: 'name', label: '名称' },
  { key: 'balance', label: '余额' },
  { key: 'credential', label: '凭据引用' },
  { key: 'bidUrl', label: '请求地址' },
  { key: 'macro', label: '宏参数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<AdvertiserV2[]>([]);
const loading = ref(false);
const saving = ref(false);
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ current: 1, keyword: '', size: 30 });

const dialogOpen = ref(false);
const editing = ref<AdvertiserV2>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserName: '',
  bidUrl: '',
  credentialConfigKey: '',
  macroParams: '',
});
const rules: FormRules = {
  advertiserName: [{ message: '请输入广告主名称', required: true }],
  bidUrl: [{ message: '请输入请求地址', required: true }],
};

const rechargeOpen = ref(false);
const rechargeLoading = ref(false);
const rechargeId = ref('');
const rechargeFormRef = ref<FormInstance>();
const rechargeForm = reactive({ balance: '' });
const rechargeRules: FormRules = {
  balance: [
    { message: '请输入充值金额', required: true },
    {
      message: '请输入不小于 0 的数字',
      pattern: /^\d+(?:\.\d+)?$/,
    },
  ],
};

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  loading.value = true;
  try {
    const result = await listAdvertisers({ ...query });
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

function edit(row?: AdvertiserV2) {
  editing.value = row;
  Object.assign(form, {
    advertiserName: row?.advertiserName || '',
    bidUrl: row?.bidUrl || '',
    credentialConfigKey: row?.credentialConfigKey || '',
    macroParams: row?.macroParams || '',
  });
  dialogOpen.value = true;
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = {
      bidUrl: form.bidUrl,
      credentialConfigKey: form.credentialConfigKey || undefined,
      macroParams: form.macroParams || undefined,
      name: form.advertiserName,
    };
    await (editing.value
      ? updateAdvertiserV2(editing.value.id, payload)
      : createAdvertiserV2(payload));
    ElMessage.success('保存成功');
    dialogOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function recharge(row: AdvertiserV2) {
  rechargeId.value = row.id;
  rechargeForm.balance = '';
  rechargeOpen.value = true;
}

async function submitRecharge() {
  if (!(await rechargeFormRef.value?.validate().catch(() => false))) return;
  rechargeLoading.value = true;
  try {
    await rechargeAdvertiser(rechargeId.value, Number(rechargeForm.balance));
    ElMessage.success('充值成功');
    rechargeOpen.value = false;
    await load();
  } finally {
    rechargeLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Page auto-content-height title="广告主管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-input
          v-model="query.keyword"
          aria-label="按广告主查询"
          clearable
          placeholder="广告主编号 / 名称"
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
        >
          重置
        </el-button>
      </div>

      <div class="ocpx-actions">
        <el-button
          v-access:code="'ocpx:advertiser:add'"
          :disabled="loading || saving"
          type="primary"
          @click="edit()"
        >
          新增广告主
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
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column
          v-if="hasColumn('id')"
          label="广告主编号"
          min-width="170"
        >
          <template #default="{ row }"><LongText :value="row.id" /></template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('name')"
          label="名称"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('balance')"
          label="余额"
          min-width="110"
        >
          <template #default="{ row }">{{ row.balance ?? 0 }}</template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('credential')"
          label="凭据引用"
          min-width="180"
        >
          <template #default="{ row }"
            ><LongText :value="row.credentialConfigKey"
          /></template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('bidUrl')"
          label="请求地址"
          min-width="240"
        >
          <template #default="{ row }"
            ><LongText :value="row.bidUrl"
          /></template>
        </el-table-column>
        <el-table-column
          v-if="hasColumn('macro')"
          label="宏参数"
          min-width="220"
        >
          <template #default="{ row }"
            ><LongText :value="row.macroParams"
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
        <el-table-column fixed="right" label="操作" width="130">
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:advertiser:edit'"
              :disabled="saving || rechargeLoading"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-access:code="'ocpx:advertiser:recharge'"
              :disabled="saving || rechargeLoading"
              link
              type="primary"
              @click="recharge(row)"
              >充值</el-button
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
      :title="editing ? '编辑广告主' : '新增广告主'"
      width="680px"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="名称" prop="advertiserName"
          ><el-input v-model="form.advertiserName"
        /></el-form-item>
        <el-form-item label="请求地址" prop="bidUrl"
          ><el-input v-model="form.bidUrl" type="textarea"
        /></el-form-item>
        <el-form-item label="凭据引用" prop="credentialConfigKey"
          ><el-input v-model="form.credentialConfigKey"
        /></el-form-item>
        <el-form-item label="宏参数" prop="macroParams"
          ><el-input v-model="form.macroParams" :rows="4" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        >
        <el-button :loading="saving" type="primary" @click="save"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="rechargeOpen" title="广告主充值" width="460px">
      <el-form
        ref="rechargeFormRef"
        :model="rechargeForm"
        :rules="rechargeRules"
        label-width="100px"
      >
        <el-form-item label="充值金额" prop="balance"
          ><el-input v-model="rechargeForm.balance" inputmode="decimal"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="rechargeLoading" @click="rechargeOpen = false"
          >取消</el-button
        >
        <el-button
          :loading="rechargeLoading"
          type="primary"
          @click="submitRecharge"
          >确定</el-button
        >
      </template>
    </el-dialog>
  </Page>
</template>
