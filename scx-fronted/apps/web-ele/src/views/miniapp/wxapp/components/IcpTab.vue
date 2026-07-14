<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import type {
  CertificateTypeOption,
  IcpFormData,
  PreApprovalTypeOption,
  RegionOption,
  ServiceTypeOption,
  SubjectTypeOption,
} from './icp';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElCard,
  ElForm,
  ElMessage,
  ElStep,
  ElSteps,
} from 'element-plus';

import {
  addIcp,
  getCertificateTypes,
  getLastIcp,
  getPreApprovalTypes,
  getRegionTree,
  getServiceTypes,
  getSubjectTypes,
} from '#/api/open/mini-icp';
import { deepMerge } from '#/utils/util';

import {
  AppletsInfo,
  FaceVerify,
  Materials,
  PrincipalInfo,
  SubjectInfo,
} from './icp';

// 定义props
interface Props {
  appId?: string;
  miniBaseInfo?: any;
}

const props = withDefaults(defineProps<Props>(), {
  appId: '',
  miniBaseInfo: () => ({}),
});

// 当前步骤
const currentStep = ref(1);

// 表单引用
const icpFormRef = ref<FormInstance>();

// 提交状态
const submitting = ref(false);

// 页面加载状态
const loading = ref(false);

// 字典数据
const subjectTypeOptions = ref<SubjectTypeOption[]>([]);
const regionOptions = ref<RegionOption[]>([]);
const certificateTypeOptions = ref<CertificateTypeOption[]>([]);
const serviceTypeOptions = ref<ServiceTypeOption[]>([]);
const preApprovalTypeOptions = ref<PreApprovalTypeOption[]>([]);

// 备案表单数据
const icpForm = reactive<IcpFormData>({
  id: '',
  // 基础信息
  taskId: '', // 备案人脸核身任务ID
  appId: '', // 小程序ID
  subjectType: undefined, // 主体性质
  subjectName: '', // 主办单位名称
  subjectProvince: '', // 备案省份
  subjectCity: '', // 备案城市
  subjectDistrict: '', // 备案县区
  regionSelection: [], // 地区选择（级联选择器）
  subjectAddress: '', // 通讯地址
  subjectComment: '', // 主体信息备注
  subjectRecordNumber: '', // 主体备案号
  usePrincipalInfo: '0', // 是否使用主体负责人信息

  // 小程序信息
  appletsAppid: '', // 小程序ID(备案用)
  appletsName: '', // 小程序名称
  appletsServiceContentTypes: [], // 小程序服务内容类型(JSON数组)
  appletsComment: '', // 小程序实际经营内容
  appletsRecordNumber: '', // 小程序备案号

  // JSON字段
  subjectJson: {
    personalInfo: {
      residencePermit: '', // 临时居住证明照片
    },
    organizeInfo: {
      certificateType: undefined, // 主体证件类型
      certificateNumber: '', // 主体证件号码
      certificateAddress: '', // 主体证件住所
      certificatePhoto: '', // 主体证件照片
    },
    principalInfo: {
      name: '', // 负责人姓名
      mobile: '', // 负责人联系方式
      email: '', // 负责人电子邮件
      emergencyContact: '', // 负责人应急联系方式
      certificateType: undefined, // 负责人证件类型
      certificateNumber: '', // 负责人证件号码
      certificateValidityDateStart: '', // 负责人证件有效期起始日期
      certificateValidityDateEnd: '', // 负责人证件有效期终止日期
      certificatePhotoFront: '', // 负责人证件正面照片
      certificatePhotoBack: '', // 负责人证件背面照片
      authorizationLetter: '', // 授权书
      verifyTaskId: '', // 扫脸认证任务id
      isLegalPerson: 1, // 是否为法人
    },
    legalPersonInfo: {
      name: '', // 法人代表姓名
      certificateNumber: '', // 法人证件号码
    },
  },

  appletsJson: {
    nrlxDetails: [], // 前置审批项
    principalInfo: {
      name: '', // 负责人姓名
      mobile: '', // 负责人联系方式
      email: '', // 负责人电子邮件
      emergencyContact: '', // 负责人应急联系方式
      certificateType: undefined, // 负责人证件类型
      certificateNumber: '', // 负责人证件号码
      certificateValidityDateStart: '', // 负责人证件有效期起始日期
      certificateValidityDateEnd: '', // 负责人证件有效期终止日期
      certificatePhotoFront: '', // 负责人证件正面照片
      certificatePhotoBack: '', // 负责人证件背面照片
      authorizationLetter: '', // 授权书
      verifyTaskId: '', // 扫脸认证任务id
    },
  },

  materialsJson: {
    commitmentLetter: [], // 互联网信息服务承诺书
    businessNameChangeLetter: [], // 主体更名函
    partyConstructionConfirmationLetter: [], // 党建确认函
    promiseVideo: [], // 承诺视频
    authenticityResponsibilityLetter: [], // 网站备案信息真实性责任告知书
    authenticityCommitmentLetter: [], // 小程序备案信息真实性承诺书
    websiteConstructionProposal: [], // 小程序建设方案书
    subjectOtherMaterials: [], // 主体其它附件
    appletsOtherMaterials: [], // 小程序其它附件
    holdingCertificatePhoto: [], // 手持证件照
  },

  // 状态信息
  faceStatus: undefined, // 人脸核身状态
  status: undefined, // 备案状态
  smsVerifyStatus: undefined, // 管局短信核验状态
  failReason: '', // 驳回原因

  // 系统字段（只读）
  createBy: '', // 创建人
  updateBy: '', // 修改人
  createTime: '', // 创建时间
  updateTime: '', // 修改时间
  delFlag: '', // 逻辑删除

  // 临时字段（用于UI交互）
  authUrl: '', // 人脸核身链接
  authMsg: '', // 提示信息
});

// 表单验证规则
const formRules = ref({
  subjectType: [
    { required: true, message: '请选择主体性质', trigger: 'change' },
  ],
  subjectName: [
    { required: true, message: '请输入主办单位名称', trigger: 'blur' },
    {
      min: 2,
      max: 100,
      message: '主办单位名称长度在 2 到 100 个字符',
      trigger: 'blur',
    },
  ],
  regionSelection: [
    { required: true, message: '请选择备案地区', trigger: 'change' },
    {
      type: 'array' as const,
      min: 3,
      message: '请选择完整的省/市/区',
      trigger: 'change',
    },
  ],
  subjectAddress: [
    { required: true, message: '请输入通讯地址', trigger: 'blur' },
    {
      min: 5,
      max: 200,
      message: '通讯地址长度在 5 到 200 个字符',
      trigger: 'blur',
    },
  ],
  'subjectJson.organizeInfo.certificateType': [
    {
      required: true,
      message: '请选择主体证件类型',
      trigger: ['change', 'blur'],
    },
  ],
  'subjectJson.organizeInfo.certificateNumber': [
    { required: true, message: '请输入主体证件号码', trigger: 'blur' },
    {
      min: 15,
      max: 18,
      message: '证件号码长度在 15 到 18 个字符',
      trigger: 'blur',
    },
  ],
  'subjectJson.organizeInfo.certificateAddress': [
    { required: true, message: '请输入主体证件住所', trigger: 'blur' },
    {
      min: 5,
      max: 200,
      message: '证件住所长度在 5 到 200 个字符',
      trigger: 'blur',
    },
  ],
  appletsServiceContentTypes: [
    { required: true, message: '请选择小程序服务内容类型', trigger: 'change' },
    {
      type: 'array' as const,
      min: 1,
      message: '至少选择一个服务内容类型',
      trigger: 'change',
    },
  ],
  appletsComment: [
    { required: true, message: '请输入小程序实际经营内容', trigger: 'blur' },
    {
      min: 10,
      max: 500,
      message: '经营内容长度在 10 到 500 个字符',
      trigger: 'blur',
    },
  ],
  'subjectJson.principalInfo.name': [
    { required: true, message: '请输入负责人姓名', trigger: 'blur' },
    {
      min: 2,
      max: 20,
      message: '负责人姓名长度在 2 到 20 个字符',
      trigger: 'blur',
    },
  ],
  'subjectJson.principalInfo.mobile': [
    { required: true, message: '请输入负责人联系方式', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur',
    },
  ],
  'subjectJson.principalInfo.emergencyContact': [
    { required: true, message: '请输入负责人应急联系方式', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur',
    },
  ],
  'subjectJson.principalInfo.email': [
    { required: true, message: '请输入负责人电子邮件', trigger: 'blur' },
    {
      type: 'email' as const,
      message: '请输入正确的邮箱地址',
      trigger: 'blur',
    },
  ],
  'subjectJson.principalInfo.certificateType': [
    { required: true, message: '请选择负责人证件类型', trigger: 'change' },
  ],
  'subjectJson.principalInfo.certificateNumber': [
    { required: true, message: '请输入负责人证件号码', trigger: 'blur' },
    {
      min: 15,
      max: 18,
      message: '证件号码长度在 15 到 18 个字符',
      trigger: 'blur',
    },
  ],
  'subjectJson.principalInfo.certificateValidityDateStart': [
    { required: true, message: '请选择证件有效期起始日期', trigger: 'change' },
  ],
  'subjectJson.principalInfo.certificateValidityDateEnd': [
    { required: true, message: '请选择证件有效期终止日期', trigger: 'change' },
  ],
  'subjectJson.principalInfo.isLegalPerson': [
    { required: true, message: '请选择是否为法人', trigger: 'change' },
  ],
  'appletsJson.principalInfo.name': [
    { required: true, message: '请输入小程序负责人姓名', trigger: 'blur' },
    {
      min: 2,
      max: 20,
      message: '负责人姓名长度在 2 到 20 个字符',
      trigger: 'blur',
    },
  ],
  'appletsJson.principalInfo.mobile': [
    { required: true, message: '请输入小程序负责人联系方式', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur',
    },
  ],
  'appletsJson.principalInfo.emergencyContact': [
    {
      required: true,
      message: '请输入小程序负责人应急联系方式',
      trigger: 'blur',
    },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur',
    },
  ],
  'appletsJson.principalInfo.email': [
    { required: true, message: '请输入小程序负责人电子邮件', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
  'appletsJson.principalInfo.certificateType': [
    {
      required: true,
      message: '请选择小程序负责人证件类型',
      trigger: 'change',
    },
  ],
  'appletsJson.principalInfo.certificateNumber': [
    { required: true, message: '请输入小程序负责人证件号码', trigger: 'blur' },
    {
      min: 15,
      max: 18,
      message: '证件号码长度在 15 到 18 个字符',
      trigger: 'blur',
    },
  ],
  'appletsJson.principalInfo.certificateValidityDateStart': [
    {
      required: true,
      message: '请选择小程序负责人证件有效期起始日期',
      trigger: 'change',
    },
  ],
  'appletsJson.principalInfo.certificateValidityDateEnd': [
    {
      required: true,
      message: '请选择小程序负责人证件有效期终止日期',
      trigger: 'change',
    },
  ],
  'appletsJson.principalInfo.isLegalPerson': [
    { required: true, message: '请选择是否为法人', trigger: 'change' },
  ],
});

// 判断是否禁用输入
const isInputDisabled = computed(() => {
  // 如果人脸核身未通过，禁用输入
  if (!icpForm.faceStatus || icpForm.faceStatus !== 3) {
    return true;
  }

  // 如果已提交申请，平台审核中，管局审核中，已备案 ，禁用输入
  if (icpForm.status === 2 || icpForm.status === 4 || icpForm.status === 6) {
    return true;
  }

  return false;
});

// 判断是否显示第二步
const showStepTwo = computed(() => {
  return icpForm.faceStatus === 3;
});

// 计算属性
const submitButtonText = computed(() => {
  return '提交备案';
});

// 方法
const resetForm = () => {
  icpFormRef.value?.resetFields();
};

const submitIcp = async () => {
  if (!icpFormRef.value) return;

  try {
    await icpFormRef.value.validate();
    submitting.value = true;

    // 如果开启使用主体负责人信息，将主体负责人信息复制到小程序负责人信息
    if (icpForm.usePrincipalInfo === '1') {
      icpForm.appletsJson.principalInfo = {
        ...icpForm.subjectJson.principalInfo,
        verifyTaskId: icpForm.appletsJson.principalInfo.verifyTaskId,
      };
    }

    // 构建提交数据
    const _submitData = {
      ...icpForm,
      appId: props.appId,
    };

    await addIcp(_submitData);

    ElMessage.success('备案申请提交成功');
  } catch (error) {
    console.error('表单验证失败:', error);
  } finally {
    submitting.value = false;
  }
};

// 获取备案信息
const getIcpFormData = async () => {
  try {
    const res = await getLastIcp(props.appId);
    deepMerge(icpForm, res);
  } catch (error) {
    console.error('获取备案信息失败:', error);
  }
};

// 获取字典数据
const getDictData = async () => {
  try {
    // 获取主体性质
    const subjectTypeRes = await getSubjectTypes(props.appId);
    subjectTypeOptions.value = subjectTypeRes;

    // 获取区域信息
    const regionRes = await getRegionTree(props.appId);
    regionOptions.value = regionRes;

    // 获取证件类型
    const certificateRes = await getCertificateTypes(props.appId);
    certificateTypeOptions.value = certificateRes;

    // 获取服务类型
    const serviceRes = await getServiceTypes(props.appId);
    serviceTypeOptions.value = serviceRes || [];

    // 获取前置审批类型
    const preApprovalRes = await getPreApprovalTypes(props.appId);
    preApprovalTypeOptions.value = preApprovalRes || [];
  } catch (error) {
    console.error('获取字典数据失败:', error);
  }
};

// 初始化所有数据
const initializeData = async () => {
  if (!props.appId) return;

  loading.value = true;
  try {
    // 并行获取备案状态和备案信息
    await getIcpFormData();
  } catch (error) {
    console.error('初始化数据失败:', error);
  } finally {
    loading.value = false;
  }
};

// 初始化字典数据（异步加载，不阻塞loading）
const initializeDictData = async () => {
  if (!props.appId) return;

  try {
    await getDictData();
  } catch (error) {
    console.error('获取字典数据失败:', error);
  }
};

// 人脸核身成功回调
const handleFaceVerifySuccess = () => {
  currentStep.value = 2;
};

// 处理人脸核身状态更新
const handleFaceStatusUpdate = (status: number) => {
  icpForm.faceStatus = status;
};

// 监听 appId 变化，初始化数据
watch(
  () => props.appId,
  (newAppId) => {
    if (newAppId) {
      initializeData();
      // 异步加载字典数据，不阻塞loading
      initializeDictData();
    }
  },
  { immediate: true },
);

// 监听 miniBaseInfo 变化，初始化表单数据
watch(
  () => props.miniBaseInfo,
  (newMiniBaseInfo) => {
    if (newMiniBaseInfo) {
      icpForm.appletsAppid = newMiniBaseInfo.appid || '';
      icpForm.appletsName = newMiniBaseInfo.nickname || '';
    }
  },
  { immediate: true },
);
</script>

<template>
  <div
    class="icp-container"
    v-loading="loading"
    element-loading-text="正在加载备案信息..."
  >
    <!-- 步骤指示器 -->
    <ElCard class="steps-card">
      <ElSteps :active="currentStep" finish-status="success">
        <ElStep title="人脸核身" description="小程序管理员人脸核身" />
        <ElStep title="备案申请" description="填写备案信息并提交" />
      </ElSteps>
    </ElCard>

    <!-- 第一步：人脸核身 -->
    <div v-if="currentStep === 1">
      <FaceVerify
        :app-id="appId"
        :task-id="icpForm.taskId"
        :face-status="icpForm.faceStatus"
        :auth-msg="icpForm.authMsg"
        @face-verify-success="handleFaceVerifySuccess"
        @update:face-status="handleFaceStatusUpdate"
      />
    </div>

    <!-- 第二步：备案申请 -->
    <div v-if="currentStep === 2 && showStepTwo">
      <ElCard class="icp-form-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">小程序备案信息</span>
          </div>
        </template>

        <ElForm
          ref="icpFormRef"
          :model="icpForm"
          :rules="formRules"
          label-width="180px"
          class="icp-form"
        >
          <!-- 主体信息 -->
          <SubjectInfo
            :form-data="icpForm"
            :subject-type-options="subjectTypeOptions"
            :region-options="regionOptions"
            :certificate-type-options="certificateTypeOptions"
            :is-disabled="isInputDisabled"
            :app-id="appId"
          />

          <!-- 主体负责人信息 -->
          <PrincipalInfo
            :subject-type-options="subjectTypeOptions"
            :form-data="icpForm"
            :certificate-type-options="certificateTypeOptions"
            :is-disabled="isInputDisabled"
            :is-subject-principal="true"
            :app-id="appId"
          />

          <!-- 小程序负责人信息 -->
          <PrincipalInfo
            :subject-type-options="subjectTypeOptions"
            :form-data="icpForm"
            :certificate-type-options="certificateTypeOptions"
            :is-disabled="isInputDisabled"
            :is-subject-principal="false"
            :app-id="appId"
          />

          <!-- 小程序信息 -->
          <AppletsInfo
            :form-data="icpForm"
            :service-type-options="serviceTypeOptions"
            :pre-approval-type-options="preApprovalTypeOptions"
            :is-disabled="isInputDisabled"
            :app-id="appId"
          />

          <!-- 其他备案媒体材料 -->
          <Materials
            :form-data="icpForm"
            :is-disabled="isInputDisabled"
            :app-id="appId"
          />

          <!-- 操作按钮 -->
          <div class="form-actions" v-if="!isInputDisabled">
            <ElButton @click="resetForm">重置</ElButton>
            <ElButton type="primary" @click="submitIcp" :loading="submitting">
              {{ submitButtonText }}
            </ElButton>
          </div>
        </ElForm>
      </ElCard>
    </div>
  </div>
</template>

<style scoped>
.icp-container {
  min-height: 100vh;
  padding: 20px;
  background-color: #f5f7fa;
}

.steps-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.icp-form-card {
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

.icp-form {
  padding: 20px 0;
}

.form-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  padding-top: 24px;
  margin-top: 32px;
  border-top: 1px solid #f0f0f0;
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
</style>
