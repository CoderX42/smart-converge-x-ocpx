<script setup lang="ts">
import type { SubjectInfoProps } from './types';

import { computed } from 'vue';

import { InfoFilled } from '@element-plus/icons-vue';
import {
  ElCascader,
  ElCol,
  ElFormItem,
  ElIcon,
  ElInput,
  ElOption,
  ElRow,
  ElSelect,
} from 'element-plus';

import Upload from './Upload.vue';

const props = defineProps<SubjectInfoProps>();

// 根据主体性质过滤的证件类型选项
const filteredCertificateTypeOptions = computed(() => {
  if (!props.formData.subjectType) {
    return [];
  }
  return props.certificateTypeOptions.filter(
    (cert) => cert.subjectType === props.formData.subjectType,
  );
});

// 处理地区选择变化
const handleRegionChange = (value: any) => {
  if (value && value.length > 0) {
    props.formData.subjectProvince = value[0]?.toString() || '';
  }
  if (value && value.length > 1) {
    props.formData.subjectCity = value[1]?.toString() || '';
  }
  if (value && value.length > 2) {
    props.formData.subjectDistrict = value[2]?.toString() || '';
  }
};

// 处理主体性质变化
const handleSubjectTypeChange = (_value: number) => {
  // 清空所有证件类型选择，因为主体性质改变了
  props.formData.subjectJson.organizeInfo.certificateType = undefined;
};
</script>

<template>
  <div class="form-section">
    <h4 class="section-title">主体信息</h4>

    <ElRow :gutter="20">
      <ElCol :span="12">
        <ElFormItem label="主体性质" prop="subjectType">
          <ElSelect
            v-model="formData.subjectType"
            placeholder="请选择主体性质"
            style="width: 100%"
            :disabled="isDisabled"
            @change="handleSubjectTypeChange"
          >
            <ElOption
              v-for="(value, index) in subjectTypeOptions"
              :key="index"
              :label="value.name"
              :value="value.type"
            />
          </ElSelect>
        </ElFormItem>
      </ElCol>

      <ElCol :span="12">
        <ElFormItem label="主办单位名称" prop="subjectName">
          <ElInput
            v-model="formData.subjectName"
            placeholder="请输入主办单位名称"
            :disabled="isDisabled"
          />
        </ElFormItem>
      </ElCol>
    </ElRow>

    <ElRow :gutter="20">
      <ElCol :span="24">
        <ElFormItem label="备案地区" prop="regionSelection">
          <ElCascader
            v-model="formData.regionSelection"
            :options="regionOptions"
            :props="{
              value: 'code',
              label: 'name',
              children: 'subList',
              checkStrictly: false,
              emitPath: true,
            }"
            placeholder="请选择备案地区（省/市/区）"
            style="width: 100%"
            :disabled="isDisabled"
            @change="handleRegionChange"
          />
        </ElFormItem>
      </ElCol>
    </ElRow>

    <ElRow :gutter="20">
      <ElCol :span="24">
        <ElFormItem label="通讯地址" prop="subjectAddress">
          <ElInput
            v-model="formData.subjectAddress"
            placeholder="请输入通讯地址"
            :disabled="isDisabled"
          />
          <div class="form-tip">
            <ElIcon class="tip-icon">
              <InfoFilled />
            </ElIcon>
            <span class="tip-text">
              通讯地址必须属于备案省市区，地址开头的省市区不用填入。例如：通信地址为"北京市朝阳区高碑店路181号1栋12345室"时，只需要填写"高碑店路181号1栋12345室"即可
            </span>
          </div>
        </ElFormItem>
      </ElCol>
    </ElRow>

    <ElRow :gutter="20">
      <ElCol :span="24">
        <ElFormItem label="主体信息备注" prop="subjectComment">
          <ElInput
            v-model="formData.subjectComment"
            type="textarea"
            :rows="3"
            placeholder="请输入主体信息备注"
            :disabled="isDisabled"
          />
          <div class="form-tip">
            <ElIcon class="tip-icon">
              <InfoFilled />
            </ElIcon>
            <span class="tip-text"> 主体信息备注，根据需要，如实填写 </span>
          </div>
        </ElFormItem>
      </ElCol>
    </ElRow>

    <ElRow :gutter="20">
      <ElCol :span="12">
        <ElFormItem
          label="证件类型"
          prop="subjectJson.organizeInfo.certificateType"
        >
          <ElSelect
            v-model="formData.subjectJson.organizeInfo.certificateType"
            placeholder="请选择证件类型"
            style="width: 100%"
            :disabled="isDisabled"
          >
            <ElOption
              v-for="(value, index) in filteredCertificateTypeOptions"
              :key="index"
              :label="value.name"
              :value="value.type"
            />
          </ElSelect>
        </ElFormItem>
      </ElCol>

      <ElCol :span="12">
        <ElFormItem
          label="证件号码"
          prop="subjectJson.organizeInfo.certificateNumber"
        >
          <ElInput
            v-model="formData.subjectJson.organizeInfo.certificateNumber"
            placeholder="请输入证件号码"
            :disabled="isDisabled"
          />
        </ElFormItem>
      </ElCol>
    </ElRow>

    <ElRow :gutter="20">
      <ElCol :span="12">
        <ElFormItem
          label="证件住所"
          prop="subjectJson.organizeInfo.certificateAddress"
        >
          <ElInput
            v-model="formData.subjectJson.organizeInfo.certificateAddress"
            placeholder="请输入证件住所"
            :disabled="isDisabled"
          />
        </ElFormItem>
      </ElCol>

      <ElCol :span="12">
        <ElFormItem label="证件照片" prop="certificatePhoto">
          <Upload
            v-model="formData.subjectJson.organizeInfo.certificatePhoto"
            :app-id="appId"
            :limit="3"
            :disabled="isDisabled"
            type="image"
            :certificate-type="
              formData.subjectJson.organizeInfo.certificateType
            "
            icp-order-field="icp_subject.organize_info.certificate_photo"
          />
          <div class="form-tip">
            <ElIcon class="tip-icon">
              <InfoFilled />
            </ElIcon>
            <span class="tip-text"> 如果小程序主体为非个人类型，则必填 </span>
          </div>
        </ElFormItem>
      </ElCol>
    </ElRow>
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
