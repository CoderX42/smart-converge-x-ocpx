<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import { computed, defineAsyncComponent, reactive, ref, watch } from 'vue';

import { useQRCode } from '@vueuse/integrations/useQRCode';
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import {
  addObj,
  editObj,
  getIdentityTree,
  getLastAuth,
} from '#/api/open/mini-auth';
import { useDict } from '#/utils/dict';
import AuthUpload from '#/views/open/mini-auth/components/upload.vue';

const props = withDefaults(defineProps<Props>(), {
  appId: '',
  miniBaseInfo: () => ({}),
});
const { mini_auth_apply_status, mini_auth_task_status } = useDict(
  'mini_auth_apply_status',
  'mini_auth_task_status',
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
// 定义props
interface Props {
  appId?: string;
  miniBaseInfo?: any;
}

// 认证状态枚举
enum AuthStatus {
  AUTHING = 'authing', // 认证中
  EXPIRED = 'expired', // 认证到期
  REJECTED = 'rejected', // 认证驳回
  UNAUTH = 'unauth', // 未认证
}

// 认证状态
const authStatus = ref<AuthStatus>(AuthStatus.UNAUTH);

// 表单引用
const authFormRef = ref<FormInstance>();

// 提交状态
const submitting = ref(false);

// 文件列表 - 已移除，使用AuthUpload组件管理

// 认证身份选项
const identityOptions = ref<Array<{ label: string; value: string }>>([]);

// 认证表单数据
const authForm = reactive({
  // 基本信息
  customerType: undefined as number | undefined, // 主体类型：企业=1，个体工商户=12，个人=15
  taskId: '', // 认证任务id，打回重审调用reauth时必填

  // 联系人信息
  contactName: '', // 认证联系人姓名
  contactEmail: '', // 认证联系人邮箱
  contactMobile: '', // 认证联系人手机号，仅打回重填时可填

  // 发票信息
  invoiceType: undefined as number | undefined, // 发票类型 1:不开发票 2:电子发票 4:增值税专票(数电类型)
  electronicId: '', // 电子发票-纳税识别号
  electronicDesc: '', // 电子发票备注
  vatEnterprisePhone: '', // 增值税专票-企业电话
  vatId: '', // 增值税专票-纳税识别号
  vatEnterpriseAddress: '', // 增值税专票-企业注册地址
  vatBankName: '', // 增值税专票-开户银行
  vatBankAccount: '', // 增值税专票-银行账号
  vatDesc: '', // 增值税专票-备注

  // 资质材料
  qualification: '', // 主体资质材料 media_id（非个人必填）
  qualificationOther: [] as string[], // 主体资质其他证明材料 media_id数组，最多10张

  // 账号信息
  accountName: '', // 小程序账号名称
  accountNameType: undefined as number | undefined, // 命名类型 1:自选词汇命名 2:商标命名
  accountSupplemental: [] as string[], // 名称命中关键词补充材料 media_id 数组

  // 支付信息
  payType: undefined as number | undefined, // 支付方式 1:消耗服务商预购包 2:开发者自行支付

  // 个人认证信息
  authIdentification: '', // 认证类型为个人时的身份标识
  authIdentMaterial: [] as string[], // 身份证明材料 media_id 数组

  // 其他信息
  thirdPartyPhone: '', // 第三方联系电话
  serviceAppid: '', // 服务市场appid，服务商代缴必填
  authMsg: '', // 提示信息
  taskStatus: undefined as number | undefined, // 任务状态
  applyStatus: undefined as number | undefined, // 申请状态
  authUrl: '', // 认证链接
});

// 生成二维码
const authUrl = computed(() => authForm.authUrl || '');

const qrcode = useQRCode(authUrl, {
  errorCorrectionLevel: 'H',
  margin: 2,
});

// 表单验证规则
const formRules: FormRules = {
  customerType: [
    { required: true, message: '请选择主体类型', trigger: 'change' },
  ],
  contactName: [
    { required: true, message: '请输入联系人姓名', trigger: 'blur' },
  ],
  contactEmail: [
    { required: true, message: '请输入联系人邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  contactMobile: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号格式',
      trigger: 'blur',
    },
  ],
  invoiceType: [
    { required: true, message: '请选择发票类型', trigger: 'change' },
  ],
  electronicId: [
    {
      required: true,
      message: '请输入纳税识别号',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (authForm.invoiceType === 2 && !value) {
          callback(new Error('请输入纳税识别号'));
        } else {
          callback();
        }
      },
    },
  ],
  vatEnterprisePhone: [
    {
      required: true,
      message: '请输入企业电话',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (authForm.invoiceType === 4 && !value) {
          callback(new Error('请输入企业电话'));
        } else {
          callback();
        }
      },
    },
  ],
  vatId: [
    {
      required: true,
      message: '请输入纳税识别号',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (authForm.invoiceType === 4 && !value) {
          callback(new Error('请输入纳税识别号'));
        } else {
          callback();
        }
      },
    },
  ],
  vatEnterpriseAddress: [
    {
      required: true,
      message: '请输入企业注册地址',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (authForm.invoiceType === 4 && !value) {
          callback(new Error('请输入企业注册地址'));
        } else {
          callback();
        }
      },
    },
  ],
  qualification: [
    {
      required: true,
      message: '请上传主体资质材料',
      trigger: 'change',
      validator: (rule, value, callback) => {
        if (authForm.customerType !== 15 && !value) {
          callback(new Error('请上传主体资质材料'));
        } else {
          callback();
        }
      },
    },
  ],
  accountName: [
    { required: true, message: '请输入小程序账号名称', trigger: 'blur' },
  ],
  accountNameType: [
    { required: true, message: '请选择账号名称类型', trigger: 'change' },
  ],
  payType: [{ required: true, message: '请选择支付方式', trigger: 'change' }],
  thirdPartyPhone: [
    { required: true, message: '请输入第三方联系电话', trigger: 'blur' },
  ],
  serviceAppid: [
    {
      required: true,
      message: '请输入服务商AppID',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (authForm.payType === 1 && !value) {
          callback(new Error('请输入服务商AppID'));
        } else {
          callback();
        }
      },
    },
  ],
  authIdentification: [
    {
      required: true,
      message: '请选择认证身份',
      trigger: 'change',
      validator: (rule, value, callback) => {
        if (authForm.customerType === 15 && !value) {
          callback(new Error('请选择认证身份'));
        } else {
          callback();
        }
      },
    },
  ],
  authIdentMaterial: [
    {
      required: true,
      message: '请上传身份证明材料',
      trigger: 'change',
      validator: (rule, value, callback) => {
        if (
          authForm.customerType === 15 &&
          authForm.authIdentification &&
          value.length === 0
        ) {
          callback(new Error('请上传身份证明材料'));
        } else {
          callback();
        }
      },
    },
  ],
};

// 计算属性
const statusIcon = computed(() => {
  switch (authStatus.value) {
    case AuthStatus.AUTHING: {
      return 'Loading';
    }
    case AuthStatus.EXPIRED: {
      return 'Clock';
    }
    case AuthStatus.REJECTED: {
      return 'WarningFilled';
    }
    case AuthStatus.UNAUTH: {
      return 'InfoFilled';
    }
    default: {
      return 'InfoFilled';
    }
  }
});

const statusIconClass = computed(() => {
  switch (authStatus.value) {
    case AuthStatus.AUTHING: {
      return 'status-icon-authing';
    }
    case AuthStatus.EXPIRED: {
      return 'status-icon-expired';
    }
    case AuthStatus.REJECTED: {
      return 'status-icon-rejected';
    }
    case AuthStatus.UNAUTH: {
      return 'status-icon-unauth';
    }
    default: {
      return 'status-icon-unauth';
    }
  }
});

const submitButtonText = computed(() => {
  switch (authStatus.value) {
    case AuthStatus.EXPIRED: {
      return '重新认证';
    }
    case AuthStatus.REJECTED: {
      return '重新提交';
    }
    default: {
      return '提交认证';
    }
  }
});

// 判断是否禁用输入
const isInputDisabled = computed(() => {
  const verifyStatus = props.miniBaseInfo.wx_verify_info?.qualification_verify;

  // 已通过认证 → 禁用
  if (verifyStatus) return true;

  // 未通过认证
  if (!verifyStatus) {
    // 无认证表单 → 允许输入
    if (!authForm) return false;
    // 仅在驳回(3)或最新记录已认证的情况下(4)时允许输入
    return !(authForm.applyStatus === 3 || authForm.applyStatus === 4);
  }

  // 其他情况（未知/未认证）→ 允许输入
  return false;
});

// 方法
const resetForm = () => {
  authFormRef.value?.resetFields();
  // 重置文件相关字段
  authForm.qualification = '';
  authForm.qualificationOther = [];
  authForm.accountSupplemental = [];
  authForm.authIdentMaterial = [];
};

const submitAuth = async () => {
  if (!authFormRef.value) return;

  try {
    await authFormRef.value.validate();
    submitting.value = true;
    const taskId = authForm.taskId;

    // 构建提交数据
    const _submitData = {
      ...authForm,
      appId: props.appId,
      // 根据发票类型处理相关字段
      electronicId:
        authForm.invoiceType === 2 ? authForm.electronicId : undefined,
      electronicDesc:
        authForm.invoiceType === 2 ? authForm.electronicDesc : undefined,
      vatEnterprisePhone:
        authForm.invoiceType === 4 ? authForm.vatEnterprisePhone : undefined,
      vatId: authForm.invoiceType === 4 ? authForm.vatId : undefined,
      vatEnterpriseAddress:
        authForm.invoiceType === 4 ? authForm.vatEnterpriseAddress : undefined,
      vatBankName:
        authForm.invoiceType === 4 ? authForm.vatBankName : undefined,
      vatBankAccount:
        authForm.invoiceType === 4 ? authForm.vatBankAccount : undefined,
      vatDesc: authForm.invoiceType === 4 ? authForm.vatDesc : undefined,
    };
    await (taskId ? editObj(_submitData) : addObj(_submitData));

    ElMessage.success('认证申请提交成功');
  } catch (error) {
    console.error('表单验证失败:', error);
  } finally {
    submitting.value = false;
  }
};

// 获取认证状态
const getAuthStatus = async () => {
  try {
    // 获取最新一条认证记录
    const res = await getLastAuth(props.appId);

    if (res) {
      Object.assign(authForm, res);
    }
    // 模拟数据
    // authStatus.value = AuthStatus.UNAUTH;
  } catch (error) {
    console.error('获取认证状态失败:', error);
  }
};

// 获取认证身份选项
const getIdentityOptions = async () => {
  try {
    // 这里调用实际的API获取认证身份选项
    const response = await getIdentityTree(props.appId);
    identityOptions.value = response.data;
    // 使用模拟数据
  } catch (error) {
    console.error('获取认证身份选项失败:', error);
  }
};
const handleCustomerTypeChange = (value: number) => {
  if (value === 15) {
    getIdentityOptions();
  }
};

// 监听 appId 变化，初始化数据
watch(
  () => props.appId,
  (newAppId) => {
    if (newAppId) {
      getAuthStatus();
    }
  },
  { immediate: true },
);

// 监听 miniBaseInfo 变化，初始化表单数据
watch(
  () => props.miniBaseInfo,
  (newMiniBaseInfo) => {
    if (newMiniBaseInfo && newMiniBaseInfo.nickname) {
      authForm.accountName = newMiniBaseInfo.nickname;
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="auth-container">
    <!-- 认证状态展示 -->
    <div class="status-section">
      <ElCard class="status-card">
        <div class="status-header">
          <ElIcon class="status-icon" :class="statusIconClass">
            <component :is="statusIcon" />
          </ElIcon>
          <div class="status-info">
            <h3 class="status-title">
              <DictTag
                v-if="authForm.taskStatus === 12"
                :options="mini_auth_apply_status"
                :value="authForm.applyStatus"
              />
              <DictTag
                v-else
                :options="mini_auth_task_status"
                :value="authForm.taskStatus"
              />
            </h3>
            <p class="status-desc">{{ authForm.authMsg }}</p>
          </div>
          <!-- 二维码显示 -->
          <div
            class="qrcode-section"
            v-if="authUrl && authForm.taskStatus !== 12"
          >
            <div class="qrcode-title">授权二维码</div>
            <div class="qrcode-content">
              <img
                v-if="qrcode"
                :src="qrcode"
                alt="授权二维码"
                class="qrcode-img"
              />
              <div v-else class="qrcode-loading">生成中...</div>
            </div>
            <div class="qrcode-desc">扫码完成小程序管理员授权</div>
          </div>
        </div>
      </ElCard>
    </div>

    <!-- 认证表单 -->
    <ElCard class="auth-form-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">微信小程序认证信息</span>
        </div>
      </template>

      <ElForm
        ref="authFormRef"
        :model="authForm"
        :rules="formRules"
        label-width="140px"
        class="auth-form"
      >
        <!-- 基本信息 -->
        <div class="form-section">
          <h4 class="section-title">基本信息</h4>

          <ElFormItem label="主体类型" prop="customerType">
            <ElSelect
              v-model="authForm.customerType"
              placeholder="请选择主体类型"
              style="width: 100%"
              :disabled="isInputDisabled"
              @change="handleCustomerTypeChange"
            >
              <ElOption label="企业" :value="1" />
              <ElOption label="个体工商户" :value="12" />
              <ElOption label="个人" :value="15" />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="小程序账号名称" prop="accountName">
            <ElInput
              v-model="authForm.accountName"
              placeholder="请输入小程序账号名称"
              :disabled="isInputDisabled"
            />
          </ElFormItem>

          <ElFormItem label="账号名称类型" prop="accountNameType">
            <ElRadioGroup
              v-model="authForm.accountNameType"
              :disabled="isInputDisabled"
            >
              <ElRadio :label="1">基于自选词汇命名</ElRadio>
              <ElRadio :label="2">基于商标命名</ElRadio>
            </ElRadioGroup>
          </ElFormItem>

          <ElFormItem label="支付方式" prop="payType">
            <ElRadioGroup
              v-model="authForm.payType"
              :disabled="isInputDisabled"
            >
              <ElRadio :label="1">消耗服务商预购包</ElRadio>
              <ElRadio :label="2">小程序开发者自行支付</ElRadio>
            </ElRadioGroup>
          </ElFormItem>

          <ElFormItem label="第三方联系电话" prop="thirdPartyPhone">
            <ElInput
              v-model="authForm.thirdPartyPhone"
              placeholder="请输入第三方联系电话"
              :disabled="isInputDisabled"
            />
          </ElFormItem>

          <ElFormItem
            label="服务商AppID"
            prop="serviceAppid"
            v-if="authForm.payType === 1"
          >
            <ElInput
              v-model="authForm.serviceAppid"
              placeholder="请输入服务商AppID"
              :disabled="isInputDisabled"
            />
          </ElFormItem>
        </div>

        <!-- 联系人信息 -->
        <div class="form-section">
          <h4 class="section-title">联系人信息</h4>

          <ElFormItem label="联系人姓名" prop="contactName">
            <ElInput
              v-model="authForm.contactName"
              placeholder="请输入联系人姓名"
              :disabled="isInputDisabled"
            />
          </ElFormItem>

          <ElFormItem label="联系人邮箱" prop="contactEmail">
            <ElInput
              v-model="authForm.contactEmail"
              placeholder="请输入联系人邮箱"
              :disabled="isInputDisabled"
            />
          </ElFormItem>

          <ElFormItem label="联系人手机号" prop="contactMobile">
            <ElInput
              v-model="authForm.contactMobile"
              placeholder="请输入联系人手机号"
              :disabled="isInputDisabled"
            />
          </ElFormItem>
        </div>

        <!-- 发票信息 -->
        <div class="form-section">
          <h4 class="section-title">发票信息</h4>

          <ElFormItem label="发票类型" prop="invoiceType">
            <ElSelect
              v-model="authForm.invoiceType"
              placeholder="请选择发票类型"
              style="width: 100%"
              :disabled="isInputDisabled"
            >
              <ElOption label="不开发票" :value="1" />
              <ElOption label="电子发票" :value="2" />
              <ElOption label="增值税专票（数电类型）" :value="4" />
            </ElSelect>
          </ElFormItem>

          <!-- 电子发票信息 -->
          <template v-if="authForm.invoiceType === 2">
            <ElFormItem label="纳税识别号" prop="electronicId">
              <ElInput
                v-model="authForm.electronicId"
                placeholder="请输入纳税识别号"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
            <ElFormItem label="发票备注" prop="electronicDesc">
              <ElInput
                v-model="authForm.electronicDesc"
                type="textarea"
                placeholder="请输入发票备注（选填）"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
          </template>

          <!-- 增值税专票信息 -->
          <template v-if="authForm.invoiceType === 4">
            <ElFormItem label="企业电话" prop="vatEnterprisePhone">
              <ElInput
                v-model="authForm.vatEnterprisePhone"
                placeholder="请输入企业电话"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
            <ElFormItem label="纳税识别号" prop="vatId">
              <ElInput
                v-model="authForm.vatId"
                placeholder="请输入纳税识别号"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
            <ElFormItem label="企业注册地址" prop="vatEnterpriseAddress">
              <ElInput
                v-model="authForm.vatEnterpriseAddress"
                placeholder="请输入企业注册地址"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
            <ElFormItem label="开户银行" prop="vatBankName">
              <ElInput
                v-model="authForm.vatBankName"
                placeholder="请输入开户银行（选填）"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
            <ElFormItem label="银行账号" prop="vatBankAccount">
              <ElInput
                v-model="authForm.vatBankAccount"
                placeholder="请输入银行账号（选填）"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
            <ElFormItem label="发票备注" prop="vatDesc">
              <ElInput
                v-model="authForm.vatDesc"
                type="textarea"
                placeholder="请输入发票备注（选填）"
                :disabled="isInputDisabled"
              />
            </ElFormItem>
          </template>
        </div>

        <!-- 资质材料 -->
        <div class="form-section">
          <h4 class="section-title">资质材料</h4>

          <ElFormItem
            label="主体资质材料"
            prop="qualification"
            v-if="authForm.customerType !== 15"
          >
            {{ authForm.qualification }}
            <AuthUpload
              :app-id="appId"
              v-model="authForm.qualification"
              :limit="1"
              type="qualification"
              :disabled="isInputDisabled"
            />
          </ElFormItem>

          <ElFormItem label="其他证明材料" prop="qualificationOther">
            <AuthUpload
              :app-id="appId"
              v-model="authForm.qualificationOther"
              :limit="1"
              type="qualificationOther"
              :disabled="isInputDisabled"
            />
          </ElFormItem>

          <ElFormItem label="名称补充材料" prop="accountSupplemental">
            <AuthUpload
              :app-id="appId"
              v-model="authForm.accountSupplemental"
              :limit="10"
              type="accountSupplemental"
              :disabled="isInputDisabled"
            />
          </ElFormItem>
        </div>

        <!-- 个人认证身份 -->
        <div class="form-section" v-if="authForm.customerType === 15">
          <h4 class="section-title">个人认证身份</h4>

          <ElFormItem label="认证身份">
            <ElSelect
              v-model="authForm.authIdentification"
              placeholder="请选择认证身份"
              style="width: 100%"
              :disabled="isInputDisabled"
            >
              <ElOption
                v-for="identity in identityOptions"
                :key="identity.value"
                :label="identity.label"
                :value="identity.value"
              />
            </ElSelect>
          </ElFormItem>

          <ElFormItem
            label="身份证明材料"
            prop="authIdentMaterial"
            v-if="authForm.authIdentification"
          >
            <AuthUpload
              :app-id="appId"
              v-model="authForm.authIdentMaterial"
              type="authIdentMaterial"
              :limit="10"
              :disabled="isInputDisabled"
            />
          </ElFormItem>
        </div>

        <!-- 操作按钮 -->
        <div class="form-actions" v-if="!isInputDisabled">
          <ElButton @click="resetForm">重置</ElButton>
          <ElButton type="primary" @click="submitAuth" :loading="submitting">
            {{ submitButtonText }}
          </ElButton>
        </div>
      </ElForm>
    </ElCard>
  </div>
</template>

<style scoped>
@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.auth-container {
  min-height: 100vh;
  padding: 20px;
  background-color: #f5f7fa;
}

.status-section {
  margin-bottom: 20px;
}

.status-card {
  border-radius: 8px;
}

.status-header {
  display: flex;
  gap: 16px;
  align-items: center;
}

.status-icon {
  padding: 12px;
  font-size: 24px;
  border-radius: 50%;
}

.status-icon-unauth {
  color: #1890ff;
  background-color: #e6f7ff;
}

.status-icon-authing {
  color: #fa8c16;
  background-color: #fff7e6;
  animation: rotate 2s linear infinite;
}

.status-icon-rejected {
  color: #ff4d4f;
  background-color: #fff2f0;
}

.status-icon-expired {
  color: #52c41a;
  background-color: #f6ffed;
}

.status-info {
  flex: 1;
}

.status-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: #262626;
}

.status-desc {
  margin: 0;
  font-size: 14px;
  color: #8c8c8c;
}

/* 二维码样式 */
.qrcode-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
  margin-left: 24px;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
}

.qrcode-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #495057;
}

.qrcode-content {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  margin-bottom: 8px;
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}

.qrcode-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qrcode-loading {
  font-size: 12px;
  color: #6c757d;
}

.qrcode-desc {
  font-size: 12px;
  color: #6c757d;
  text-align: center;
}

.auth-form-card {
  border-radius: 8px;
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

.auth-form {
  padding: 20px 0;
}

.form-section {
  padding-bottom: 24px;
  margin-bottom: 32px;
  border-bottom: 1px solid #f0f0f0;
}

.form-section:last-of-type {
  margin-bottom: 0;
  border-bottom: none;
}

.section-title {
  padding-left: 8px;
  margin: 0 0 20px;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  border-left: 3px solid #1890ff;
}

.form-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  padding-top: 24px;
  margin-top: 32px;
  border-top: 1px solid #f0f0f0;
}

.upload-demo {
  width: 100%;
}

.el-upload__tip {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #595959;
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}

:deep(.el-select) {
  width: 100%;
}

:deep(.el-radio-group) {
  display: flex;
  gap: 24px;
}

:deep(.el-upload-list) {
  margin-top: 8px;
}
</style>
