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
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/open/component-info';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const { status } = useDict('status');
const state = reactive({
  form: {
    id: '',
    componentAppId: '',
    componentAppSecret: '',
    componentToken: '',
    componentEncodingAesKey: '',
    authorizedDomains: '',
    status: '',
  },
  rules: {
    componentAppId: [
      {
        required: true,
        message: '请输入APPID',
        trigger: 'change',
      },
    ],
    componentAppSecret: [
      {
        required: true,
        message: '请输入AppSecret',
        trigger: 'change',
      },
    ],
    componentEncodingAesKey: [
      {
        required: true,
        message: '请输入消息加解密密钥',
        trigger: 'change',
      },
    ],
    componentToken: [
      {
        required: true,
        message: '请输入令牌(Token)',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
    authorizedDomains: [
      {
        validator: (rule: any, value: string, callback: any) => {
          if (!value) {
            callback();
            return;
          }

          const domains = value.split(';');
          // Check if any domain is empty after trimming
          if (domains.some((domain) => !domain.trim())) {
            callback(
              new Error(
                '域名白名单格式不正确，请使用分号(;)分隔各个域名，不要有空域名',
              ),
            );
            return;
          }

          callback();
        },
        trigger: 'blur',
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
  state.form.id = row.id;
  if (row && row.id) {
    getDetail(row.id);
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
  state.form.id = '';
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
      :title="!isAdd ? '修改开放平台信息' : '新增开放平台信息'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="200px"
        :rules="state.rules"
      >
        <ElFormItem label="三方平台AppId" prop="componentAppId">
          <ElInput v-model="state.form.componentAppId" />
        </ElFormItem>
        <ElFormItem label="三方平台AppSecret" prop="componentAppSecret">
          <ElInput v-model="state.form.componentAppSecret" type="appSecret" />
        </ElFormItem>
        <ElFormItem label="消息加解密Key" prop="componentEncodingAesKey">
          <ElInput v-model="state.form.componentEncodingAesKey" />
        </ElFormItem>
        <ElFormItem label="消息校验Token" prop="componentToken">
          <ElInput v-model="state.form.componentToken" />
        </ElFormItem>
        <ElFormItem label="域名白名单" prop="authorizedDomains">
          <ElInput
            v-model="state.form.authorizedDomains"
            :rows="2"
            type="textarea"
          />
          <div style="margin-top: 5px; font-size: 12px; color: #909399">
            多个域名请使用分号(;)分隔
          </div>
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElRadioGroup v-model="state.form.status">
            <ElRadio
              v-for="item in status"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </ElRadio>
          </ElRadioGroup>
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
