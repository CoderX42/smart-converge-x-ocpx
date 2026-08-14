<script setup lang="ts">
import { reactive, ref } from 'vue';

import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus';

import { editPassword } from '#/api/system/user';
import {
  getPasswordStrengthError,
  PASSWORD_COMPLEXITY_HINT,
} from '#/utils/password-rules';

const emit = defineEmits(['upSuccess']);

const formRef = ref();

const validatePass = (rule: any, value: any, callback: any) => {
  const err = getPasswordStrengthError(value, { required: true });
  if (err) {
    callback(new Error(err));
    return;
  }
  if (state.form.checkPassword !== '') {
    if (!formRef.value) return;
    formRef.value.validateField('checkPassword', () => null);
  }
  callback();
};

const validatePass2 = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请输入确认密码'));
  } else if (value === state.form.newPassword) {
    callback();
  } else {
    callback(new Error('新密码与确认密码不一致'));
  }
};

const state = reactive({
  form: {
    password: '',
    checkPassword: '',
    newPassword: '',
  },
  rules: {
    newPassword: [{ validator: validatePass, trigger: 'blur' }],
    checkPassword: [{ validator: validatePass2, trigger: 'blur' }],
    password: [
      {
        required: true,
        message: '请输入原密码',
        trigger: 'blur',
      },
    ],
  },
  loading: false,
});

const submit = () => {
  formRef.value.validate((valid: any) => {
    if (valid) {
      state.loading = true;
      editPassword(state.form)
        .then(() => {
          ElMessage.success('提交成功');
          state.loading = false;
          resetForm();
          emit('upSuccess');
        })
        .catch(() => {
          state.loading = false;
        });
    }
  });
};

const resetForm = () => {
  formRef.value.resetFields();
};
</script>

<template>
  <ElForm
    ref="formRef"
    :model="state.form"
    :rules="state.rules"
    label-width="120px"
    status-icon
  >
    <ElFormItem label="原密码" prop="password">
      <ElInput
        type="password"
        v-model="state.form.password"
        maxlength="32"
        show-password
        autocomplete="current-password"
      />
    </ElFormItem>
    <ElFormItem label="新密码" prop="newPassword">
      <ElInput
        type="password"
        v-model="state.form.newPassword"
        maxlength="32"
        show-password
        autocomplete="new-password"
      />
      <div class="password-hint">{{ PASSWORD_COMPLEXITY_HINT }}</div>
    </ElFormItem>
    <ElFormItem label="确认密码" prop="checkPassword">
      <ElInput
        type="password"
        v-model="state.form.checkPassword"
        maxlength="32"
        show-password
        autocomplete="new-password"
      />
    </ElFormItem>
    <ElFormItem>
      <ElButton type="primary" :loading="state.loading" @click="submit">
        提 交
      </ElButton>
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.password-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
