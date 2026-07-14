<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
} from 'element-plus';

import { editObj, getConfig } from '#/api/user/point-config';

const state = reactive({
  form: {
    id: undefined,
    redeemRate: 0,
    minOrderAmount: 0,
  },
  rules: {
    redeemRate: [
      {
        required: true,
        message: '请输入1积分可抵扣多少元',
        trigger: 'change',
      },
    ],
    minOrderAmount: [
      {
        required: true,
        message: '请输入订单满多少才能使用积分',
        trigger: 'change',
      },
    ],
  },
});
const loading = ref(false);
const formRef = ref();

const initPage = async () => {
  loading.value = true;
  await getConfig()
    .then((response) => {
      state.form = response;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      edit();
    }
  });
};
/**
 * 修改
 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      ElMessage.success('修改成功');
    })
    .catch(() => {
      loading.value = false;
    });
};

initPage();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <h1>积分配置</h1>
      <div style="height: 100vh; padding: 20px; overflow-y: auto">
        <ElForm
          ref="formRef"
          :model="state.form"
          label-width="180px"
          :rules="state.rules"
        >
          <ElFormItem label="1积分可抵扣多少元" prop="redeemRate">
            <ElInputNumber
              style="width: 30%"
              :controls="false"
              v-model="state.form.redeemRate"
              :precision="2"
              :min="0"
            />
          </ElFormItem>
          <ElFormItem label="订单满多少才可使用积分" prop="minOrderAmount">
            <ElInputNumber
              style="width: 30%"
              :controls="false"
              v-model="state.form.minOrderAmount"
              :precision="2"
              :min="0"
            />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="submitForm(formRef)">
              保 存
            </ElButton>
          </ElFormItem>
        </ElForm>
      </div>
    </div>
  </div>
</template>
