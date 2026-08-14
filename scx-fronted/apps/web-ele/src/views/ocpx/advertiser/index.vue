<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { AdvertiserV2, EntityOption } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  advertiserOptions,
  appendAdvertiserMacroParams,
  createAdvertiserV2,
  deleteAdvertiserV2,
  listAdvertisers,
  rechargeAdvertiser,
  updateAdvertiserV2,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';
import {
  getPasswordStrengthError,
  PASSWORD_COMPLEXITY_HINT,
} from '#/utils/password-rules';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxAdvertiserManagement' });

const columns = [
  { key: 'id', label: '广告主编号' },
  { key: 'name', label: '名称' },
  { key: 'username', label: '用户名' },
  { key: 'balance', label: '余额' },
  { key: 'credential', label: '凭据引用' },
  { key: 'bidUrl', label: '请求地址' },
  { key: 'macro', label: '宏参数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<AdvertiserV2[]>([]);
const advertiserSelects = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ advertiserId: '', current: 1, size: 30 });

const dialogOpen = ref(false);
const editing = ref<AdvertiserV2>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserName: '',
  bidUrl: '',
  credentialConfigKey: '',
  macroParams: '',
  password: '',
  username: '',
});
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes([
    'ocpx:advertiser:edit',
    'ocpx:advertiser:recharge',
    'ocpx:advertiser:delete',
  ]),
);
const loadGuard = createLatestRequestGuard();

function validatePassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) {
  const error = getPasswordStrengthError(value, { required: true });
  callback(error ? new Error(error) : undefined);
}

const rules: FormRules = {
  advertiserName: [
    { message: '请输入广告主名称', required: true, trigger: 'blur' },
  ],
  bidUrl: [{ message: '请输入请求地址', required: true, trigger: 'blur' }],
  password: [{ trigger: 'blur', validator: validatePassword }],
  username: [
    {
      message: '请输入用户名',
      pattern: /\S/,
      required: true,
      trigger: 'blur',
    },
    { max: 100, message: '用户名不能超过 100 个字符', trigger: 'blur' },
  ],
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
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listAdvertisers({
      advertiserId: query.advertiserId || undefined,
      current: query.current,
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
    password: '',
    username: '',
  });
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const editingId = editing.value?.id;
    const createPayload = {
      bidUrl: form.bidUrl,
      credentialConfigKey: form.credentialConfigKey || undefined,
      macroParams: form.macroParams || undefined,
      name: form.advertiserName,
    };
    await (editingId
      ? updateAdvertiserV2(editingId, createPayload)
      : createAdvertiserV2({
          ...createPayload,
          password: form.password,
          username: form.username.trim(),
        }));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    advertiserSelects.value = await advertiserOptions();
    await load();
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, {
    advertiserName: '',
    bidUrl: '',
    credentialConfigKey: '',
    macroParams: '',
    password: '',
    username: '',
  });
}

function recharge(row: AdvertiserV2) {
  rechargeId.value = row.id;
  rechargeForm.balance = '';
  rechargeOpen.value = true;
  void nextTick(() => rechargeFormRef.value?.clearValidate());
}

function handleRechargeClose() {
  rechargeId.value = '';
  rechargeFormRef.value?.resetFields();
  rechargeForm.balance = '';
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

async function remove(row: AdvertiserV2) {
  await ElMessageBox.confirm(
    '仅未被推广链接、监测任务或历史数据引用的广告主可删除，绑定登录账号也会同步注销。确认继续？',
    '删除广告主',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deleteAdvertiserV2(row.id);
    ElMessage.success('删除成功');
    advertiserSelects.value = await advertiserOptions();
    if (query.advertiserId === row.id) query.advertiserId = '';
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  advertiserSelects.value = await advertiserOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="广告主管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.advertiserId"
          aria-label="按广告主查询"
          clearable
          filterable
          placeholder="请选择广告主"
        >
          <el-option
            v-for="item in advertiserSelects"
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
          v-access:code="'ocpx:advertiser:add'"
          :disabled="loading || saving || Boolean(deletingId)"
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
          v-if="hasColumn('username')"
          label="用户名"
          min-width="140"
        >
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </el-table-column>
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
            ><LongText
              :value="appendAdvertiserMacroParams(row.bidUrl, row.macroParams)"
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
        <el-table-column
          v-if="canOperate"
          fixed="right"
          label="操作"
          width="190"
        >
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:advertiser:edit'"
              :disabled="saving || rechargeLoading || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-access:code="'ocpx:advertiser:recharge'"
              :disabled="saving || rechargeLoading || Boolean(deletingId)"
              link
              type="primary"
              @click="recharge(row)"
              >充值</el-button
            >
            <el-button
              v-access:code="'ocpx:advertiser:delete'"
              :disabled="saving || rechargeLoading || Boolean(deletingId)"
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
      :title="editing ? '编辑广告主' : '新增广告主'"
      width="680px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="广告主名称" prop="advertiserName"
          ><el-input
            v-model="form.advertiserName"
            placeholder="请输入广告主名称"
        /></el-form-item>
        <el-form-item label="请求地址" prop="bidUrl"
          ><el-input
            v-model="form.bidUrl"
            autosize
            placeholder="请输入请求地址"
            type="textarea"
        /></el-form-item>
        <el-form-item label="凭据引用" prop="credentialConfigKey"
          ><el-input
            v-model="form.credentialConfigKey"
            placeholder="请输入凭据引用"
        /></el-form-item>
        <el-form-item label="宏参数" prop="macroParams">
          <el-input
            v-model="form.macroParams"
            autosize
            placeholder="请输入宏参数"
            type="textarea"
          />
        </el-form-item>
        <template v-if="!editing">
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              maxlength="100"
              placeholder="请输入登录用户名"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              autocomplete="new-password"
              maxlength="32"
              placeholder="请输入登录密码"
              show-password
              type="password"
            />
            <div class="field-hint">{{ PASSWORD_COMPLEXITY_HINT }}</div>
          </el-form-item>
        </template>
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

    <el-dialog
      v-model="rechargeOpen"
      :close-on-click-modal="false"
      title="广告主充值"
      width="460px"
      @close="handleRechargeClose"
    >
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

<style scoped>
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
