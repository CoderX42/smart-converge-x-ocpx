<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getList } from '#/api/user/level';
import {
  adjustBalance,
  adjustPoint,
  getPage,
  resetPassword,
} from '#/api/user/user-info';
import { useDict } from '#/utils/dict';
import {
  getPasswordStrengthError,
  PASSWORD_COMPLEXITY_HINT,
} from '#/utils/password-rules';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));

const Detail = defineAsyncComponent(() => import('./detail.vue'));
// 字典
const { user_source, user_sex } = useDict('user_source', 'user_sex');
const state = reactive({
  queryParams: { phone: '', levelId: '', sex: '', nickname: '' },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
  activeName: '',
});
const showSearch = ref(true);
const loading = ref(false);
const detailRef = ref();
const queryRef = ref();
const formRef = ref();
const levelList = ref<any>([]);
const pointFormRef = ref();
const balanceFormRef = ref();
const pointDialog = reactive<any>({
  open: false,
  title: '调整积分',
  form: {
    userId: '',
    totalPoint: 0,
    point: 0,
    type: '1',
  },
  rules: {},
  loading: false,
});
const balanceDialog = reactive<any>({
  open: false,
  title: '调整余额',
  form: {
    userId: '',
    totalBalance: 0,
    balance: 0,
    type: '1',
  },
  rules: {},
  loading: false,
});
const userSourceOptions = reactive([
  {
    label: '全部',
    value: '',
  },
  {
    label: '微信小程序',
    value: 'WX_MA',
  },
  {
    label: 'APP',
    value: 'APP',
  },
  {
    label: '普通H5',
    value: 'H5',
  },
  {
    label: '微信公众号',
    value: 'WX_MP',
  },
  {
    label: 'PC',
    value: 'PC',
  },
]);

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    userSource: state.activeName,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
};
/**
 * 用户来源切换
 */
const tabHandle = (event: string) => {
  state.activeName = event;
  initPage();
};
const doDetail = (row: any) => {
  detailRef.value.initPage(row);
};
// 积分调整
const handlePointAdjust = () => {
  pointDialog.loading = true;
  adjustPoint(pointDialog.form)
    .then(() => {
      ElMessage.success('调整成功');
      pointDialog.loading = false;
      handlePointClose();
      initPage();
    })
    .catch(() => {
      pointDialog.loading = false;
    });
};
// 余额调整
const handleBalanceAdjust = () => {
  balanceDialog.loading = true;
  adjustBalance(balanceDialog.form)
    .then(() => {
      ElMessage.success('调整成功');
      balanceDialog.loading = false;
      handleBalanceClose();
      initPage();
    })
    .catch(() => {
      balanceDialog.loading = false;
    });
};
const handlePointClose = () => {
  pointDialog.open = false;
};
const handleBalanceClose = () => {
  balanceDialog.open = false;
};

const pointAdjust = (item: any) => {
  pointDialog.open = true;
  pointDialog.form.totalPoint = item.point ?? 0;
  pointDialog.form.userId = item.id;
};
const balanceAdjust = (item: any) => {
  balanceDialog.open = true;
  balanceDialog.form.totalBalance = item.userBalance
    ? item.userBalance.accountBalance
    : 0;
  balanceDialog.form.userId = item.id;
};
// 创建用户
const add = () => {
  formRef.value.initForm();
};
const doEdit = (row: any) => {
  formRef.value.initForm(row);
};
const getLevelList = () => {
  getList({}).then((res) => {
    levelList.value = res;
  });
};

// 重置密码
const resetPasswordFormRef = ref();
const resetPasswordDialog = reactive<any>({
  open: false,
  title: '重置密码',
  form: {
    id: '',
    password: '',
  },
  rules: {
    password: [
      {
        validator: (_rule: any, value: any, callback: any) => {
          const err = getPasswordStrengthError(value, { required: true });
          if (err) callback(new Error(err));
          else callback();
        },
        trigger: 'blur',
      },
    ],
  },
  loading: false,
});

const resetPwdHanlder = (row: any) => {
  resetPasswordDialog.open = true;
  resetPasswordDialog.form.id = row.id;
  resetPasswordDialog.form.password = '';
};

const handleResetPasswordClose = () => {
  resetPasswordDialog.open = false;
};

const handleResetPassword = () => {
  resetPasswordFormRef.value.validate((valid: boolean) => {
    if (valid) {
      resetPasswordDialog.loading = true;
      resetPassword(resetPasswordDialog.form)
        .then(() => {
          ElMessage.success('重置成功');
          resetPasswordDialog.loading = false;
          handleResetPasswordClose();
        })
        .catch(() => {
          resetPasswordDialog.loading = false;
        });
    }
  });
};

initPage();
getLevelList();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <Detail ref="detailRef" />
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="手机号" prop="phone">
          <ElInput
            v-model="state.queryParams.phone"
            placeholder="请输入手机号"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="昵称" prop="nickname">
          <ElInput
            v-model="state.queryParams.nickname"
            placeholder="请输入昵称"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="等级" prop="levelId">
          <ElSelect
            v-model="state.queryParams.levelId"
            style="width: 200px"
            placeholder="请选择等级"
            clearable
          >
            <ElOption
              v-for="item in levelList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="性别" prop="sex">
          <ElSelect
            v-model="state.queryParams.sex"
            style="width: 200px"
            placeholder="请选择性别"
            clearable
          >
            <ElOption
              v-for="item in user_sex"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>

      <!-- 工具栏 -->
      <div class="app-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'user:userinfo:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Form ref="formRef" @init-page="initPage" />

      <ElSegmented
        v-model="state.activeName"
        :options="userSourceOptions"
        style="width: 440px; margin-bottom: 5px"
        @change="tabHandle"
      />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="avatarUrl" label="头像">
          <template #default="scope">
            <ElAvatar :src="scope.row.avatarUrl" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="nickname" label="昵称" />
        <ElTableColumn prop="phone" label="手机号" />
        <ElTableColumn prop="userLevel.name" label="等级" />
        <ElTableColumn prop="sex" label="性别">
          <template #default="scope">
            <DictTag :options="user_sex" :value="scope.row.sex" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="userSource" label="来源">
          <template #default="scope">
            <DictTag :options="user_source" :value="scope.row.userSource" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="province" label="所在省份" />
        <ElTableColumn prop="city" label="所在城市" />
        <ElTableColumn prop="accountBalance" label="账户余额">
          <template #default="scope">
            <div style="display: flex; align-items: center">
              <div style="color: red">
                ￥{{
                  scope.row.userBalance
                    ? scope.row.userBalance.accountBalance
                    : 0
                }}
              </div>
              <ElButton
                link
                type="primary"
                v-access:code="'user:userinfo:balance'"
                style="padding-left: 4px"
                @click="balanceAdjust(scope.row)"
                :icon="Edit"
              />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="point" label="积分">
          <template #default="scope">
            <div style="display: flex; align-items: center">
              <div class="text-red-500">
                {{
                  scope.row.point ? scope.row.point - scope.row.frozenPoint : 0
                }}
              </div>
              <ElButton
                link
                type="primary"
                v-access:code="'user:userinfo:point'"
                style="padding-left: 4px"
                @click="pointAdjust(scope.row)"
                :icon="Edit"
              />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="240" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'user:userinfo:get'"
              @click="doDetail(scope.row)"
              :icon="View"
            >
              详情
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'user:userinfo:edit'"
              @click="doEdit(scope.row)"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'user:userinfo:edit'"
              @click="resetPwdHanlder(scope.row)"
              :icon="Refresh"
            >
              重置密码
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />

      <ElDialog
        v-model="pointDialog.open"
        :title="pointDialog.title"
        width="500"
      >
        <ElForm
          ref="pointFormRef"
          :model="pointDialog.form"
          label-width="160px"
          :rules="pointDialog.rules"
        >
          <ElFormItem label="当前积分" prop="totalPoint">
            {{ pointDialog.form.totalPoint }}
          </ElFormItem>
          <ElFormItem label="调整类型" prop="type">
            <ElRadioGroup v-model="pointDialog.form.type">
              <ElRadio value="1">增加</ElRadio>
              <ElRadio value="2">减少</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="调整积分" prop="point">
            <ElInputNumber
              :controls="false"
              v-model="pointDialog.form.point"
              :precision="0"
              :min="0"
              :max="99999999"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="dialog-footer">
            <ElButton :disabled="pointDialog.loading" @click="handlePointClose">
              取 消
            </ElButton>
            <ElButton
              type="primary"
              :disabled="pointDialog.loading"
              @click="handlePointAdjust"
            >
              确 认
            </ElButton>
          </div>
        </template>
      </ElDialog>
      <ElDialog
        v-model="balanceDialog.open"
        :title="balanceDialog.title"
        width="500"
      >
        <ElForm
          ref="balanceFormRef"
          :model="balanceDialog.form"
          label-width="160px"
          :rules="balanceDialog.rules"
        >
          <ElFormItem label="当前余额" prop="totalBalance">
            {{ balanceDialog.form.totalBalance }}
          </ElFormItem>
          <ElFormItem label="调整类型" prop="type">
            <ElRadioGroup v-model="balanceDialog.form.type">
              <ElRadio value="1">增加</ElRadio>
              <ElRadio value="2">减少</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="调整余额" prop="point">
            <ElInputNumber
              :controls="false"
              v-model="balanceDialog.form.balance"
              :precision="2"
              :min="0"
              :max="99999999"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="dialog-footer">
            <ElButton
              :disabled="balanceDialog.loading"
              @click="handleBalanceClose"
            >
              取 消
            </ElButton>
            <ElButton
              type="primary"
              :disabled="balanceDialog.loading"
              @click="handleBalanceAdjust"
            >
              确 认
            </ElButton>
          </div>
        </template>
      </ElDialog>
      <ElDialog
        v-model="resetPasswordDialog.open"
        :title="resetPasswordDialog.title"
        width="500"
      >
        <ElForm
          ref="resetPasswordFormRef"
          :model="resetPasswordDialog.form"
          label-width="100px"
          :rules="resetPasswordDialog.rules"
        >
          <ElFormItem label="新密码" prop="password">
            <ElInput
              v-model="resetPasswordDialog.form.password"
              placeholder="请输入新密码"
              type="password"
              maxlength="32"
              show-password
              autocomplete="new-password"
            />
            <div class="password-hint">{{ PASSWORD_COMPLEXITY_HINT }}</div>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="dialog-footer">
            <ElButton
              :disabled="resetPasswordDialog.loading"
              @click="handleResetPasswordClose"
            >
              取 消
            </ElButton>
            <ElButton
              type="primary"
              :loading="resetPasswordDialog.loading"
              @click="handleResetPassword"
            >
              确 认
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
.password-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
