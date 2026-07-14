<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

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

import { addObj, editObj, getById } from '#/api/system/agreement';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const Editor = defineAsyncComponent(
  () => import('#/components/editor/index.vue'),
);

const { agreement_type } = useDict('agreement_type');

const state = reactive({
  form: {
    id: '',
    title: '',
    content: '',
    type: '',
  },
  rules: {
    type: [
      {
        required: true,
        message: '请选择内容',
        trigger: 'change',
      },
    ],
    title: [
      {
        required: true,
        message: '请输入标题',
        trigger: 'change',
      },
    ],
    content: [
      {
        required: true,
        message: '请输入内容',
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
    state.form.id = row.id;
    getDetail(row.id);
  }
  dialog.value = true;
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response) => {
      loading.value = false;
      Object.assign(state.form, response);
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
    :title="state.form.id ? '修改协议' : '新增协议'"
    width="50%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="协议类型" prop="type">
        <ElRadioGroup v-model="state.form.type">
          <ElRadio
            v-for="item in agreement_type"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <ElFormItem label="标题" prop="title">
        <ElInput v-model="state.form.title" maxlength="60" show-word-limit />
      </ElFormItem>
      <ElFormItem label="内容" prop="content">
        <Editor
          v-if="dialog"
          v-model:get-html="state.form.content"
          height="500px"
        />
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
