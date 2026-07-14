<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/balance-config';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
// 字典
const { status } = useDict('status');
const state = reactive({
  form: {
    id: '',
    name: '',
    description: '',
    paymentPrice: 0,
    bonusPrice: 0,
    status: '0',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入名称',
        trigger: 'change',
      },
    ],
    paymentPrice: [
      {
        required: true,
        message: '请输入支付金额',
        trigger: 'change',
      },
    ],
    bonusPrice: [
      {
        required: true,
        message: '请输入赠送金额',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (row: any) => {
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = response;
    })
    .catch(() => {
      loading.value = false;
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
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};
/**
 * 新增
 */
const add = () => {
  addObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 修改
 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改储值配置' : '添加储值配置'"
    width="40%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="100px"
      :rules="state.rules"
    >
      <ElFormItem label="名称" prop="name">
        <ElInput v-model="state.form.name" maxlength="20" show-word-limit />
      </ElFormItem>
      <ElFormItem label="描述" prop="description">
        <ElInput v-model="state.form.description" :rows="2" type="textarea" />
      </ElFormItem>
      <ElFormItem label="支付金额" prop="paymentPrice">
        <ElInputNumber
          style="width: 100%"
          v-model="state.form.paymentPrice"
          :precision="2"
          :min="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="赠送金额" prop="bonusPrice">
        <ElInputNumber
          style="width: 100%"
          v-model="state.form.bonusPrice"
          :precision="2"
          :min="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio v-for="item in status" :key="item.value" :value="item.value">
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
</template>
