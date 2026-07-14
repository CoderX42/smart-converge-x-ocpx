<script setup lang="ts">
import type { PrincipalInfoProps } from './types';

import { computed } from 'vue';

import { InfoFilled } from '@element-plus/icons-vue';
import {
  ElCol,
  ElDatePicker,
  ElFormItem,
  ElIcon,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import Upload from './Upload.vue';

const props = defineProps<PrincipalInfoProps>();

// 负责人证件类型选项 - 只显示主体性质为个人的证件类型
const principalCertificateTypeOptions = computed(() => {
  // 从主体性质选项中找到"个人"类型的type值
  const personalSubjectType = props.subjectTypeOptions.find(
    (subject) => subject.name === '个人',
  )?.type;

  if (!personalSubjectType) {
    return [];
  }

  // 过滤出适用于个人的证件类型
  return props.certificateTypeOptions.filter(
    (cert) => cert.subjectType === personalSubjectType,
  );
});

// 获取负责人信息的路径前缀
const getPrincipalInfoPath = () => {
  return props.isSubjectPrincipal
    ? 'subjectJson.principalInfo'
    : 'appletsJson.principalInfo';
};

// 获取法人信息的路径前缀
const getLegalPersonInfoPath = () => {
  return 'subjectJson.legalPersonInfo';
};

// 计算是否显示小程序负责人表单
const showAppletsPrincipalForm = computed(() => {
  if (props.isSubjectPrincipal) {
    return true; // 主体负责人始终显示
  }
  // 小程序负责人：当usePrincipalInfo为'0'或不等于'1'时显示表单
  return props.formData.usePrincipalInfo !== '1';
});
</script>

<template>
  <div class="form-section">
    <h4 class="section-title">
      {{ isSubjectPrincipal ? '主体负责人信息' : '小程序负责人信息' }}
    </h4>

    <!-- 小程序负责人使用主体负责人信息开关 -->
    <ElRow :gutter="20" v-if="!isSubjectPrincipal">
      <ElCol :span="24">
        <ElFormItem label="使用主体负责人信息" prop="usePrincipalInfo">
          <ElSwitch
            v-model="formData.usePrincipalInfo"
            active-value="1"
            inactive-value="0"
            :disabled="isDisabled"
          />
          <div class="form-tip" v-if="formData.usePrincipalInfo === '1'">
            <ElIcon class="tip-icon">
              <InfoFilled />
            </ElIcon>
            <span class="tip-text">
              已开启使用主体负责人信息，小程序负责人信息将自动使用主体负责人信息
            </span>
          </div>
        </ElFormItem>
      </ElCol>
    </ElRow>

    <!-- 小程序负责人表单 - 只在未使用主体负责人信息时显示 -->
    <template v-if="showAppletsPrincipalForm">
      <ElRow :gutter="20">
        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}姓名`"
            :prop="`${getPrincipalInfoPath()}.name`"
          >
            <ElInput
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.name
              "
              :placeholder="`请输入${isSubjectPrincipal ? '负责人' : '小程序负责人'}姓名`"
              :disabled="isDisabled"
            />
          </ElFormItem>
        </ElCol>

        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}联系方式`"
            :prop="`${getPrincipalInfoPath()}.mobile`"
          >
            <ElInput
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.mobile
              "
              :placeholder="`请输入${isSubjectPrincipal ? '负责人' : '小程序负责人'}手机号码`"
              :disabled="isDisabled"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElRow :gutter="20">
        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}电子邮件`"
            :prop="`${getPrincipalInfoPath()}.email`"
          >
            <ElInput
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.email
              "
              :placeholder="`请输入${isSubjectPrincipal ? '负责人' : '小程序负责人'}邮箱地址`"
              :disabled="isDisabled"
            />
          </ElFormItem>
        </ElCol>

        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}应急联系方式`"
            :prop="`${getPrincipalInfoPath()}.emergencyContact`"
          >
            <ElInput
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.emergencyContact
              "
              :placeholder="`请输入${isSubjectPrincipal ? '负责人' : '小程序负责人'}应急联系方式`"
              :disabled="isDisabled"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElRow :gutter="20">
        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}证件类型`"
            :prop="`${getPrincipalInfoPath()}.certificateType`"
          >
            <ElSelect
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificateType
              "
              placeholder="请选择证件类型"
              style="width: 100%"
              :disabled="isDisabled"
            >
              <ElOption
                v-for="cert in principalCertificateTypeOptions"
                :key="cert.type"
                :label="cert.name"
                :value="cert.type"
              />
            </ElSelect>
          </ElFormItem>
        </ElCol>

        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}证件号码`"
            :prop="`${getPrincipalInfoPath()}.certificateNumber`"
          >
            <ElInput
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificateNumber
              "
              :placeholder="`请输入${isSubjectPrincipal ? '负责人' : '小程序负责人'}证件号码`"
              :disabled="isDisabled"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElRow :gutter="20">
        <ElCol :span="12">
          <ElFormItem
            label="证件有效期起始日期"
            :prop="`${getPrincipalInfoPath()}.certificateValidityDateStart`"
          >
            <ElDatePicker
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificateValidityDateStart
              "
              type="date"
              placeholder="请选择证件有效期起始日期"
              :disabled="isDisabled"
              value-format="YYYYmmdd"
            />
          </ElFormItem>
        </ElCol>

        <ElCol :span="12">
          <ElFormItem
            label="证件有效期终止日期"
            :prop="`${getPrincipalInfoPath()}.certificateValidityDateEnd`"
          >
            <ElDatePicker
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificateValidityDateEnd
              "
              type="date"
              placeholder="请选择证件有效期终止日期"
              :disabled="isDisabled"
              value-format="YYYYmmdd"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>

      <ElRow :gutter="20">
        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}证件正面照片`"
            :prop="`${getPrincipalInfoPath()}.certificatePhotoFront`"
          >
            <Upload
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificatePhotoFront
              "
              :app-id="appId"
              :limit="1"
              :disabled="isDisabled"
              type="image"
              :certificate-type="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificateType
              "
              :icp-order-field="`${isSubjectPrincipal ? 'icp_subject.principal_info.certificate_photo_front' : 'icp_applets.principal_info.certificate_photo_front'}`"
            />
          </ElFormItem>
        </ElCol>

        <ElCol :span="12">
          <ElFormItem
            :label="`${isSubjectPrincipal ? '负责人' : '小程序负责人'}证件背面照片`"
            :prop="`${getPrincipalInfoPath()}.certificatePhotoBack`"
          >
            <Upload
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificatePhotoBack
              "
              :app-id="appId"
              :limit="1"
              :disabled="isDisabled"
              type="image"
              :certificate-type="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.certificateType
              "
              :icp-order-field="`${isSubjectPrincipal ? 'icp_subject.principal_info.certificate_photo_back' : 'icp_applets.principal_info.certificate_photo_back'}`"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
      <ElRow :gutter="20">
        <ElCol :span="12">
          <ElFormItem
            label="是否为法人"
            :prop="`${getPrincipalInfoPath()}.isLegalPerson`"
          >
            <ElRadioGroup
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.isLegalPerson
              "
              :disabled="isDisabled"
            >
              <ElRadio label="1">是</ElRadio>
              <ElRadio label="0">否</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
        </ElCol>
      </ElRow>
    </template>

    <template
      v-if="
        formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
          .principalInfo.isLegalPerson === '0'
      "
    >
      <ElRow :gutter="20">
        <ElCol :span="24">
          <ElFormItem
            label="授权书"
            :prop="`${getPrincipalInfoPath()}.authorizationLetter`"
          >
            <span
              v-if="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.authorizationLetter
              "
            >
              {{
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.authorizationLetter
              }}
            </span>
            <Upload
              v-model="
                formData[isSubjectPrincipal ? 'subjectJson' : 'appletsJson']
                  .principalInfo.authorizationLetter
              "
              :app-id="appId"
              :limit="1"
              :disabled="isDisabled"
              type="image"
              :icp-order-field="`${isSubjectPrincipal ? 'icp_subject.principal_info.authorization_letter' : 'icp_applets.principal_info.authorization_letter'}`"
            />
            <div class="form-tip">
              <ElIcon class="tip-icon">
                <InfoFilled />
              </ElIcon>
              <span class="tip-text">
                当主体负责人不是法人时需要主体负责人授权书，支持JPG、PNG格式
              </span>
            </div>
          </ElFormItem>
        </ElCol>
      </ElRow>

      <!-- 法人信息 -->
      <div
        v-if="
          isSubjectPrincipal &&
          formData.subjectJson.principalInfo.isLegalPerson === '0'
        "
        class="legal-person-section"
      >
        <h4 class="section-title">法人信息</h4>

        <ElFormItem label="法人姓名" :prop="`${getLegalPersonInfoPath()}.name`">
          <ElInput
            v-model="formData.subjectJson.legalPersonInfo.name"
            placeholder="请输入法人姓名"
            :disabled="isDisabled"
          />
        </ElFormItem>

        <ElFormItem
          label="法人证件号码"
          :prop="`${getLegalPersonInfoPath()}.certificateNumber`"
        >
          <ElInput
            v-model="formData.subjectJson.legalPersonInfo.certificateNumber"
            placeholder="请输入法人证件号码"
            :disabled="isDisabled"
          />
        </ElFormItem>
      </div>
    </template>
  </div>
</template>

<style scoped>
.form-section {
  padding-bottom: 24px;
  margin-bottom: 32px;
  border-bottom: 1px solid #f0f0f0;
}

.section-title {
  padding-left: 8px;
  margin: 0 0 20px;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  border-left: 3px solid #1890ff;
}

.legal-person-section {
  padding-top: 24px;
  margin-top: 24px;
  border-top: 1px solid #f0f0f0;
}

.form-tip {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 8px 12px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  background-color: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
}

.tip-icon {
  flex-shrink: 0;
  margin-top: 1px;
  font-size: 14px;
  color: #0284c7;
}

.tip-text {
  flex: 1;
  color: #0369a1;
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
