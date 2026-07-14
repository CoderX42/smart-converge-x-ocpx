<script setup lang="ts">
import type { AppletsInfoProps } from './types';

import { Delete, Document, InfoFilled, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElIcon,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import Upload from './Upload.vue';

const props = defineProps<AppletsInfoProps>();

// 添加前置审批项
const addPreApprovalItem = () => {
  props.formData.appletsJson.nrlxDetails.push({
    type: 0,
    code: '',
    media: '',
  });
};

// 删除前置审批项
const removePreApprovalItem = (index: number) => {
  props.formData.appletsJson.nrlxDetails.splice(index, 1);
};
</script>

<template>
  <div class="form-section">
    <h4 class="section-title">微信小程序信息</h4>

    <ElFormItem label="小程序名称" prop="appletsName">
      <ElInput
        v-model="formData.appletsName"
        placeholder="请输入小程序名称"
        disabled
      />
    </ElFormItem>

    <ElFormItem label="小程序ID" prop="appletsAppid">
      <ElInput
        v-model="formData.appletsAppid"
        placeholder="请输入小程序ID"
        disabled
      />
    </ElFormItem>

    <ElFormItem
      label="小程序服务内容类型"
      prop="appletsServiceContentTypes"
      class="full-width"
    >
      <ElSelect
        v-model="formData.appletsServiceContentTypes"
        placeholder="请选择小程序服务内容类型"
        :disabled="isDisabled"
        multiple
      >
        <ElOption
          v-for="item in serviceTypeOptions"
          :key="item.type"
          :label="item.name"
          :value="item.type"
        />
      </ElSelect>
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text">
          小程序服务内容类型，只能填写二级服务内容类型，最多5个
        </span>
      </div>
    </ElFormItem>

    <ElFormItem
      label="前置审批项"
      prop="appletsJson.nrlxDetails"
      class="full-width"
    >
      <div class="pre-approval-container">
        <div class="pre-approval-header">
          <ElButton
            type="primary"
            size="small"
            :disabled="isDisabled"
            @click="addPreApprovalItem"
          >
            <ElIcon class="mr-1">
              <Plus />
            </ElIcon>
            新增前置审批项
          </ElButton>
          <div class="form-tip">
            <ElIcon class="tip-icon">
              <InfoFilled />
            </ElIcon>
            <span class="tip-text">
              如不涉及前置审批项，也需要填"以上都不涉及"
            </span>
          </div>
        </div>

        <div class="pre-approval-list">
          <div
            v-for="(item, index) in formData.appletsJson.nrlxDetails"
            :key="index"
            class="pre-approval-item"
          >
            <div class="pre-approval-item-header">
              <span class="item-title">前置审批项 {{ index + 1 }}</span>
              <ElButton
                v-if="formData.appletsJson.nrlxDetails.length > 1"
                type="danger"
                size="small"
                text
                :disabled="isDisabled"
                @click="removePreApprovalItem(index)"
              >
                <ElIcon>
                  <Delete />
                </ElIcon>
                删除
              </ElButton>
            </div>

            <div class="pre-approval-item-content">
              <ElFormItem
                :prop="`appletsJson.nrlxDetails.${index}.type`"
                label="审批类型"
                class="mb-3"
              >
                <ElSelect
                  v-model="item.type"
                  placeholder="请选择前置审批类型"
                  style="width: 100%"
                  :disabled="isDisabled"
                >
                  <ElOption
                    v-for="option in preApprovalTypeOptions"
                    :key="option.type"
                    :label="option.name"
                    :value="option.type"
                  />
                </ElSelect>
              </ElFormItem>

              <ElFormItem
                :prop="`appletsJson.nrlxDetails.${index}.code`"
                label="审批号"
                class="mb-3"
              >
                <ElInput
                  v-model="item.code"
                  placeholder="请输入前置审批号"
                  :disabled="isDisabled"
                />
                <span class="form-tip">
                  前置审批号，如果前置审批类型不是"以上都不涉及"，则必填，示例值："粤-12345号"
                </span>
              </ElFormItem>

              <ElFormItem
                :prop="`appletsJson.nrlxDetails.${index}.media`"
                label="媒体材料"
                class="mb-3"
              >
                <Upload
                  v-model="item.media"
                  :app-id="appId"
                  :limit="1"
                  :disabled="isDisabled"
                  type="image"
                  icp-order-field="icp_applets.base_info.nrlx_details"
                />
                <span class="form-tip">
                  前置审批媒体材料如果前置审批类型不是"以上都不涉及"，则必填
                </span>
              </ElFormItem>
            </div>
          </div>

          <div
            v-if="formData.appletsJson.nrlxDetails.length === 0"
            class="empty-state"
          >
            <ElIcon class="empty-icon">
              <Document />
            </ElIcon>
            <p class="empty-text">暂无前置审批项，请点击上方按钮添加</p>
          </div>
        </div>
      </div>
    </ElFormItem>

    <ElFormItem
      label="小程序实际经营内容"
      prop="appletsComment"
      class="full-width"
    >
      <ElInput
        v-model="formData.appletsComment"
        type="textarea"
        :rows="2"
        maxlength="200"
        minlength="20"
        placeholder="请输入小程序实际经营内容"
        :disabled="isDisabled"
      />
      <div class="form-tip">
        <ElIcon class="tip-icon">
          <InfoFilled />
        </ElIcon>
        <span class="tip-text"> 备注内容字数限制20-200字，请认真填写 </span>
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

/* 前置审批项样式 */
.pre-approval-container {
  padding: 16px;
  background-color: #f9fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.pre-approval-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.pre-approval-header .el-button {
  padding: 8px 12px;
}

.pre-approval-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pre-approval-item {
  padding: 16px;
  background-color: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 10%);
}

.pre-approval-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: #262626;
}

.pre-approval-item-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pre-approval-item-content .el-form-item {
  margin-bottom: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  color: #909399;
}

.empty-icon {
  margin-bottom: 10px;
  font-size: 40px;
  color: #c0c4cc;
}

.empty-text {
  font-size: 14px;
}

.mr-1 {
  margin-right: 4px;
}

.mb-3 {
  margin-bottom: 12px;
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
