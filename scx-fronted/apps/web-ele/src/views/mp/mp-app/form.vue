<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/open/open-platform-app';

const emit = defineEmits(['initPage']);

const state = reactive({
  form: {
    appId: '',
    appSecret: '',
    token: '',
    aesKey: '',
    appName: '',
    componentStatus: '0',
    platformType: 'WX_H5',
    extraJson: '',
  },
  rules: {
    appId: [
      {
        required: true,
        message: '请输入APPID',
        trigger: 'change',
      },
    ],
    appSecret: [
      {
        required: true,
        message: '请输入AppSecret',
        trigger: 'change',
      },
    ],
    appName: [
      {
        required: true,
        message: '请输入应用名称',
        trigger: 'change',
      },
    ],
    aesKey: [
      {
        required: true,
        message: '请输入消息加解密密钥',
        trigger: 'change',
      },
    ],
    token: [
      {
        required: true,
        message: '请输入令牌(Token)',
        trigger: 'change',
      },
    ],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const isAdd = ref(false);

const initForm = (row: any | undefined) => {
  dialog.value = true;
  isAdd.value = true;
  if (row && row.appId) {
    getDetail(row.appId);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response) => {
    state.form = response;
    loading.value = false;
    isAdd.value = false;
  });
};
/**
 * 关闭事件
 */
const handleClose = () => {
  resetForm(formRef.value);
};
/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.appId = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (isAdd.value) {
        // 新增
        add();
      } else {
        // 修改
        edit();
      }
    }
  });
};
/**
 * 新增
 */
const add = async () => {
  addObj(state.form).then(() => {
    resetForm(formRef.value);
    ElMessage.success('新增成功');
    emit('initPage');
  });
};
/**
 * 修改
 */
const edit = async () => {
  editObj(state.form).then(() => {
    resetForm(formRef.value);
    ElMessage.success('修改成功');
    emit('initPage');
  });
};

defineExpose({
  initForm,
});
</script>
<template>
  <div class="system-add-user-container">
    <ElDialog
      v-model="dialog"
      :title="!isAdd ? '修改公众号应用' : '新增公众号应用'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="120px"
        :rules="state.rules"
      >
        <ElFormItem label="应用名称" prop="appName">
          <ElInput
            v-model="state.form.appName"
            maxlength="20"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="appId" prop="appId">
          <ElInput v-model="state.form.appId" :disabled="!isAdd" />
        </ElFormItem>
        <ElFormItem label="appSecret" prop="appSecret">
          <ElInput v-model="state.form.appSecret" type="appSecret" />
        </ElFormItem>
        <ElFormItem label="消息加解密密钥" prop="aesKey">
          <ElInput v-model="state.form.aesKey" />
        </ElFormItem>
        <ElFormItem label="令牌(Token)" prop="token">
          <ElInput v-model="state.form.token" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="handleClose">关 闭</ElButton>
          <ElButton
            type="primary"
            :loading="loading"
            @click="submitForm(formRef)"
          >
            确 认
          </ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>
