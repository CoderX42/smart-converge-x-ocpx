<script setup lang="ts">
import type { MaterialsProps } from './types';

import { InfoFilled } from '@element-plus/icons-vue';
import { ElFormItem, ElIcon } from 'element-plus';

import Upload from './Upload.vue';

const props = defineProps<MaterialsProps>();
</script>

<template>
  <div class="form-section">
    <h4 class="section-title">其他备案媒体材料</h4>

    <ElFormItem
      label="互联网信息服务承诺书"
      prop="materialsJson.commitmentLetter"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.commitmentLetter"
        :app-id="appId"
        :limit="5"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.commitment_letter"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传互联网信息服务承诺书，支持JPG、PNG格式，单个文件不超过2MB
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="主体更名函"
      prop="materialsJson.businessNameChangeLetter"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.businessNameChangeLetter"
        :app-id="appId"
        :limit="3"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.business_name_change_letter"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          如主体名称发生过变更，请上传主体更名函，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="党建确认函"
      prop="materialsJson.partyConstructionConfirmationLetter"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.partyConstructionConfirmationLetter"
        :app-id="appId"
        :limit="3"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.party_building_confirmation_letter"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          如涉及党建相关内容，请上传党建确认函，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="承诺视频"
      prop="materialsJson.promiseVideo"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.promiseVideo"
        :app-id="appId"
        :limit="1"
        :disabled="isDisabled"
        type="video"
        icp-order-field="icp_materials.promise_video"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传承诺视频，支持MP4格式，时长不超过3分钟，文件大小不超过50MB
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="小程序备案信息真实性承诺书"
      prop="materialsJson.authenticityCommitmentLetter"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.authenticityCommitmentLetter"
        :app-id="appId"
        :limit="3"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.authenticity_responsibility_letter"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传小程序备案信息真实性承诺书，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="网站备案信息真实性责任告知书"
      prop="materialsJson.authenticityResponsibilityLetter"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.authenticityResponsibilityLetter"
        :app-id="appId"
        :limit="3"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.authenticity_commitment_letter"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传网站备案信息真实性责任告知书，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="小程序建设方案书"
      prop="materialsJson.websiteConstructionProposal"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.websiteConstructionProposal"
        :app-id="appId"
        :limit="5"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.website_construction_proposal"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text"> 请上传小程序建设方案书，支持JPG、PNG格式 </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="主体其它附件"
      prop="materialsJson.subjectOtherMaterials"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.subjectOtherMaterials"
        :app-id="appId"
        :limit="10"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.subject_other_materials"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传主体相关的其他证明材料，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="小程序其它附件"
      prop="materialsJson.appletsOtherMaterials"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.appletsOtherMaterials"
        :app-id="appId"
        :limit="10"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.applets_other_materials"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传小程序相关的其他证明材料，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="手持证件照"
      prop="materialsJson.holdingCertificatePhoto"
      class="full-width"
    >
      <Upload
        v-model="formData.materialsJson.holdingCertificatePhoto"
        :app-id="appId"
        :limit="3"
        :disabled="isDisabled"
        type="image"
        icp-order-field="icp_materials.holding_certificate_photo"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          请上传手持身份证照片，确保身份证信息清晰可见，支持JPG、PNG格式
        </span>
      </div>
    </ElFormItem>
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
</style>
