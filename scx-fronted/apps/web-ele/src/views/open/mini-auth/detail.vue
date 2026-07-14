<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElTag,
} from 'element-plus';

import { getById } from '#/api/open/mini-auth';

// 认证状态枚举
enum AuthStatus {
  AUTHING = 'authing', // 认证中
  EXPIRED = 'expired', // 认证到期
  REJECTED = 'rejected', // 认证驳回
  SUCCESS = 'success', // 认证成功
  UNAUTH = 'unauth', // 未认证
}

// 对话框显示状态
const dialogVisible = ref(false);

// 认证数据
const authData = reactive({
  id: '',
  customerType: undefined as number | undefined,
  taskId: '',
  contactInfo: {
    name: '',
    email: '',
    mobile: '',
  },
  invoiceInfo: {
    invoiceType: undefined as number | undefined,
    electronic: {
      id: '',
      desc: '',
    },
    vat: {
      enterprisePhone: '',
      id: '',
      enterpriseAddress: '',
      bankName: '',
      bankAccount: '',
      desc: '',
    },
  },
  qualification: '',
  qualificationOther: [] as string[],
  accountName: '',
  accountNameType: undefined as number | undefined,
  accountSupplemental: [] as string[],
  payType: undefined as number | undefined,
  authIdentification: '',
  authIdentMaterial: [] as string[],
  thirdPartyPhone: '',
  serviceAppId: '',
  status: AuthStatus.UNAUTH,
  createTime: '',
  updateTime: '',
});

// 认证状态映射
const authStatusMap = {
  [AuthStatus.UNAUTH]: { text: '未认证', type: 'info' },
  [AuthStatus.AUTHING]: { text: '认证中', type: 'warning' },
  [AuthStatus.REJECTED]: { text: '认证驳回', type: 'danger' },
  [AuthStatus.EXPIRED]: { text: '认证到期', type: 'danger' },
  [AuthStatus.SUCCESS]: { text: '认证成功', type: 'success' },
};

// 主体类型映射
const customerTypeMap = {
  1: '企业',
  12: '个体工商户',
  15: '个人',
};

// 账号名称类型映射
const accountNameTypeMap = {
  1: '基于自选词汇命名',
  2: '基于商标命名',
};

// 支付方式映射
const payTypeMap = {
  1: '消耗服务商预购包',
  2: '小程序开发者自行支付',
};

// 发票类型映射
const invoiceTypeMap = {
  1: '不开发票',
  2: '电子发票',
  4: '增值税专票（数电类型）',
};

// 计算属性
const dialogTitle = computed(() => {
  return `认证详情 - ${authData.accountName}`;
});

const statusInfo = computed(() => {
  return authStatusMap[authData.status as AuthStatus];
});

// 方法
const handleClose = () => {
  dialogVisible.value = false;
};

// 初始化表单
const initForm = async (row: any) => {
  dialogVisible.value = true;

  // 加载详情数据
  const data = await getById(row.id);
  Object.assign(authData, data);
};

defineExpose({
  initForm,
});
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="80%"
    :before-close="handleClose"
  >
    <div class="detail-container">
      <!-- 状态信息 -->
      <ElCard class="status-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">认证状态</span>
            <ElTag :type="statusInfo?.type" size="large">
              {{ statusInfo?.text }}
            </ElTag>
          </div>
        </template>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="创建时间">
            {{ authData.createTime }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">
            {{ authData.updateTime }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="认证任务ID" v-if="authData.taskId">
            {{ authData.taskId }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCard>

      <!-- 基本信息 -->
      <ElCard class="info-card" shadow="never">
        <template #header>
          <span class="card-title">基本信息</span>
        </template>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="小程序账号名称">
            {{ authData.accountName }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="主体类型">
            {{
              customerTypeMap[
                authData.customerType as keyof typeof customerTypeMap
              ]
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="账号名称类型">
            {{
              accountNameTypeMap[
                authData.accountNameType as keyof typeof accountNameTypeMap
              ]
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="支付方式">
            {{ payTypeMap[authData.payType as keyof typeof payTypeMap] }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="第三方联系电话">
            {{ authData.thirdPartyPhone }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="服务商AppID" v-if="authData.serviceAppId">
            {{ authData.serviceAppId }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCard>

      <!-- 联系人信息 -->
      <ElCard class="info-card" shadow="never">
        <template #header>
          <span class="card-title">联系人信息</span>
        </template>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="联系人姓名">
            {{ authData.contactInfo.name }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="联系人邮箱">
            {{ authData.contactInfo.email }}
          </ElDescriptionsItem>
          <ElDescriptionsItem
            label="联系人手机号"
            v-if="authData.contactInfo.mobile"
          >
            {{ authData.contactInfo.mobile }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCard>

      <!-- 发票信息 -->
      <ElCard class="info-card" shadow="never">
        <template #header>
          <span class="card-title">发票信息</span>
        </template>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="发票类型">
            {{
              invoiceTypeMap[
                authData.invoiceInfo.invoiceType as keyof typeof invoiceTypeMap
              ]
            }}
          </ElDescriptionsItem>

          <!-- 电子发票信息 -->
          <template v-if="authData.invoiceInfo.invoiceType === 2">
            <ElDescriptionsItem label="纳税识别号">
              {{ authData.invoiceInfo.electronic.id }}
            </ElDescriptionsItem>
            <ElDescriptionsItem
              label="发票备注"
              v-if="authData.invoiceInfo.electronic.desc"
            >
              {{ authData.invoiceInfo.electronic.desc }}
            </ElDescriptionsItem>
          </template>

          <!-- 增值税专票信息 -->
          <template v-if="authData.invoiceInfo.invoiceType === 4">
            <ElDescriptionsItem label="企业电话">
              {{ authData.invoiceInfo.vat.enterprisePhone }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="纳税识别号">
              {{ authData.invoiceInfo.vat.id }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="企业注册地址">
              {{ authData.invoiceInfo.vat.enterpriseAddress }}
            </ElDescriptionsItem>
            <ElDescriptionsItem
              label="开户银行"
              v-if="authData.invoiceInfo.vat.bankName"
            >
              {{ authData.invoiceInfo.vat.bankName }}
            </ElDescriptionsItem>
            <ElDescriptionsItem
              label="银行账号"
              v-if="authData.invoiceInfo.vat.bankAccount"
            >
              {{ authData.invoiceInfo.vat.bankAccount }}
            </ElDescriptionsItem>
            <ElDescriptionsItem
              label="发票备注"
              v-if="authData.invoiceInfo.vat.desc"
            >
              {{ authData.invoiceInfo.vat.desc }}
            </ElDescriptionsItem>
          </template>
        </ElDescriptions>
      </ElCard>

      <!-- 资质材料 -->
      <ElCard class="info-card" shadow="never">
        <template #header>
          <span class="card-title">资质材料</span>
        </template>
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem
            label="主体资质材料"
            v-if="authData.qualification"
          >
            {{ authData.qualification }}
          </ElDescriptionsItem>
          <ElDescriptionsItem
            label="其他证明材料"
            v-if="authData.qualificationOther.length > 0"
          >
            {{ authData.qualificationOther.join(', ') }}
          </ElDescriptionsItem>
          <ElDescriptionsItem
            label="名称补充材料"
            v-if="authData.accountSupplemental.length > 0"
          >
            {{ authData.accountSupplemental.join(', ') }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCard>

      <!-- 个人认证身份 -->
      <ElCard
        class="info-card"
        shadow="never"
        v-if="authData.customerType === 15"
      >
        <template #header>
          <span class="card-title">个人认证身份</span>
        </template>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="认证身份">
            {{ authData.authIdentification }}
          </ElDescriptionsItem>
          <ElDescriptionsItem
            label="身份证明材料"
            v-if="authData.authIdentMaterial.length > 0"
          >
            {{ authData.authIdentMaterial.join(', ') }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElCard>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <ElButton @click="handleClose">关闭</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.detail-container {
  max-height: 70vh;
  overflow-y: auto;
}

.status-card {
  margin-bottom: 16px;
}

.info-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

:deep(.el-descriptions__label) {
  font-weight: 500;
  color: #595959;
}

:deep(.el-descriptions__content) {
  color: #262626;
}
</style>
