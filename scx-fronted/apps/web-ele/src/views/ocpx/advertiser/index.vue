<script lang="ts" setup>
import type { FormInstance, FormRules, TreeInstance } from 'element-plus';

import type {
  AdvertiserRoleMenuNode,
  AdvertiserRolePermissions,
  AdvertiserV2,
  EntityOption,
} from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  advertiserOptions,
  appendAdvertiserMacroParams,
  createAdvertiserV2,
  deleteAdvertiserV2,
  getAdvertiserRolePermissions,
  listAdvertisers,
  rechargeAdvertiser,
  updateAdvertiserRolePermissions,
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

type PermissionTreeNode = AdvertiserRoleMenuNode & {
  children: PermissionTreeNode[];
  disabled: boolean;
};

const rolePermissionOpen = ref(false);
const rolePermissionLoading = ref(false);
const rolePermissionSaving = ref(false);
const rolePermissionConfig = ref<AdvertiserRolePermissions>();
const rolePermissionTreeRef = ref<TreeInstance>();
const rolePermissionForm = reactive({
  allDataScope: false,
  menuIds: [] as string[],
});
const rolePermissionTree = computed<PermissionTreeNode[]>(() =>
  decoratePermissionTree(
    rolePermissionConfig.value?.menuTree || [],
    rolePermissionForm.allDataScope,
  ),
);

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
const rechargeForm = reactive({ amount: '', remark: '', requestId: '' });
const rechargeRules: FormRules = {
  amount: [
    { message: '请输入充值金额', required: true },
    {
      message: '请输入大于 0、最多 6 位小数的金额',
      pattern: /^(?!0+(?:\.0+)?$)\d+(?:\.\d{1,6})?$/,
    },
  ],
};

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

function decoratePermissionTree(
  nodes: AdvertiserRoleMenuNode[],
  allDataScope: boolean,
): PermissionTreeNode[] {
  return nodes.map((node) => ({
    ...node,
    children: decoratePermissionTree(node.children || [], allDataScope),
    disabled: node.requiresAllDataScope && !allDataScope,
  }));
}

function flattenPermissionIds(
  nodes: AdvertiserRoleMenuNode[],
  includeGlobal: boolean,
): string[] {
  return nodes.flatMap((node) => [
    ...(includeGlobal || !node.requiresAllDataScope ? [node.id] : []),
    ...flattenPermissionIds(node.children || [], includeGlobal),
  ]);
}

function captureRolePermissionMenus() {
  rolePermissionForm.menuIds = (
    rolePermissionTreeRef.value?.getCheckedKeys(false) || []
  ).map(String);
}

async function openRolePermissions() {
  rolePermissionOpen.value = true;
  rolePermissionLoading.value = true;
  try {
    const result = await getAdvertiserRolePermissions();
    rolePermissionConfig.value = result;
    rolePermissionForm.allDataScope = result.allDataScope;
    rolePermissionForm.menuIds = [...result.selectedMenuIds];
    await nextTick();
    rolePermissionTreeRef.value?.setCheckedKeys(result.selectedMenuIds, false);
  } catch {
    rolePermissionOpen.value = false;
  } finally {
    rolePermissionLoading.value = false;
  }
}

async function changeAllDataScope(enabled: boolean) {
  rolePermissionForm.allDataScope = enabled;
  if (!enabled && rolePermissionConfig.value) {
    const allowed = new Set(
      flattenPermissionIds(rolePermissionConfig.value.menuTree, false),
    );
    rolePermissionForm.menuIds = rolePermissionForm.menuIds.filter((id) =>
      allowed.has(id),
    );
    await nextTick();
    rolePermissionTreeRef.value?.setCheckedKeys(
      rolePermissionForm.menuIds,
      false,
    );
  }
}

async function selectAllRolePermissions() {
  if (!rolePermissionConfig.value) return;
  rolePermissionForm.allDataScope = true;
  rolePermissionForm.menuIds = flattenPermissionIds(
    rolePermissionConfig.value.menuTree,
    true,
  );
  await nextTick();
  rolePermissionTreeRef.value?.setCheckedKeys(
    rolePermissionForm.menuIds,
    false,
  );
}

function clearRolePermissions() {
  rolePermissionForm.allDataScope = false;
  rolePermissionForm.menuIds = [];
  rolePermissionTreeRef.value?.setCheckedKeys([], false);
}

async function saveRolePermissions() {
  captureRolePermissionMenus();
  const affected = rolePermissionConfig.value?.affectedAccountCount || 0;
  await ElMessageBox.confirm(
    `此配置由全部广告主账号共享，将影响 ${affected} 个账号并注销其现有会话。确认保存？`,
    '保存账号角色权限',
    {
      confirmButtonText: '保存并使会话失效',
      type: 'warning',
    },
  );
  rolePermissionSaving.value = true;
  try {
    rolePermissionConfig.value = await updateAdvertiserRolePermissions({
      allDataScope: rolePermissionForm.allDataScope,
      menuIds: rolePermissionForm.menuIds,
    });
    ElMessage.success('广告主账号角色权限已更新，请相关账号重新登录');
    rolePermissionOpen.value = false;
  } finally {
    rolePermissionSaving.value = false;
  }
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
  Object.assign(rechargeForm, {
    amount: '',
    remark: '',
    requestId: crypto.randomUUID(),
  });
  rechargeOpen.value = true;
  void nextTick(() => rechargeFormRef.value?.clearValidate());
}

function handleRechargeClose() {
  rechargeId.value = '';
  rechargeFormRef.value?.resetFields();
  Object.assign(rechargeForm, { amount: '', remark: '', requestId: '' });
}

async function submitRecharge() {
  if (!(await rechargeFormRef.value?.validate().catch(() => false))) return;
  rechargeLoading.value = true;
  try {
    await rechargeAdvertiser(rechargeId.value, {
      amount: Number(rechargeForm.amount),
      remark: rechargeForm.remark.trim() || undefined,
      requestId: rechargeForm.requestId,
    });
    ElMessage.success('充值成功');
    rechargeOpen.value = false;
    await load();
  } finally {
    rechargeLoading.value = false;
  }
}

async function remove(row: AdvertiserV2) {
  await ElMessageBox.confirm(
    '仅无余额、无账务流水且无事件事实的广告主可以删除。删除会同时移除其空账户、推广链接、监测任务、扣量规则并注销绑定账号；共享媒体不会删除。确认继续？',
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
        <el-button
          v-access:code="'ocpx:advertiser:role:manage'"
          :disabled="loading || saving || Boolean(deletingId)"
          @click="openRolePermissions"
        >
          账号角色权限
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
          <template #default="{ row }">{{
            row.balance == null ? '—' : `¥${Number(row.balance).toFixed(2)}`
          }}</template>
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
      v-model="rolePermissionOpen"
      :close-on-click-modal="false"
      title="广告主账号角色权限"
      width="760px"
    >
      <div v-loading="rolePermissionLoading" class="role-permission-dialog">
        <el-alert
          :closable="false"
          show-icon
          :title="`共享角色 ${rolePermissionConfig?.roleName || '广告主'}（${rolePermissionConfig?.roleCode || 'ROLE_ADVERTISER'}）会影响 ${rolePermissionConfig?.affectedAccountCount || 0} 个广告主账号。`"
          type="warning"
        />
        <div class="role-permission-scope">
          <div>
            <strong>全量 OCPX 数据范围</strong>
            <p>开启后，所选权限可访问全部广告主及共享媒体、扣量数据。</p>
          </div>
          <el-switch
            :model-value="rolePermissionForm.allDataScope"
            aria-label="全量 OCPX 数据范围"
            @change="changeAllDataScope(Boolean($event))"
          />
        </div>
        <el-alert
          v-if="rolePermissionForm.allDataScope"
          :closable="false"
          show-icon
          title="全量范围已开启：获得对应删除权限的广告主账号可以级联删除任意广告主。"
          type="error"
        />
        <div class="role-permission-toolbar">
          <strong>OCPX 菜单与操作权限</strong>
          <div>
            <el-button
              :disabled="rolePermissionLoading || rolePermissionSaving"
              link
              type="primary"
              @click="selectAllRolePermissions"
              >全选</el-button
            >
            <el-button
              :disabled="rolePermissionLoading || rolePermissionSaving"
              link
              @click="clearRolePermissions"
              >清空</el-button
            >
          </div>
        </div>
        <el-tree
          ref="rolePermissionTreeRef"
          check-on-click-node
          check-strictly
          :data="rolePermissionTree"
          default-expand-all
          node-key="id"
          :props="{ children: 'children', disabled: 'disabled', label: 'name' }"
          show-checkbox
          @check="captureRolePermissionMenus"
        >
          <template #default="{ data }">
            <span>{{ data.name }}</span>
            <span v-if="data.requiresAllDataScope" class="permission-scope-tag">
              需全量范围
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button
          :disabled="rolePermissionSaving"
          @click="rolePermissionOpen = false"
          >取消</el-button
        >
        <el-button
          :disabled="rolePermissionLoading"
          :loading="rolePermissionSaving"
          type="primary"
          @click="saveRolePermissions"
          >保存权限</el-button
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
        <el-form-item label="充值金额" prop="amount"
          ><el-input v-model="rechargeForm.amount" inputmode="decimal"
        /></el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="rechargeForm.remark"
            maxlength="255"
            placeholder="选填"
            type="textarea"
          />
        </el-form-item>
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

.role-permission-dialog {
  display: grid;
  gap: 16px;
}

.role-permission-scope,
.role-permission-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.role-permission-scope p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role-permission-dialog :deep(.el-tree) {
  max-height: 360px;
  padding: 12px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.permission-scope-tag {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
