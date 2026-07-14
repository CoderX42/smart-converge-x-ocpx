<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus';

import SelectMaterial from '#/components/select-material/index.vue';
import SelectShop from '#/components/select-shop/index.vue';

interface Form {
  appName: string;
  appId: string;
  appSecret: string;
  qrcodeUrl: string;
  shopId: string;
}
interface Props {
  form: Form;
  isAdd: boolean;
  loading: boolean;
}

defineProps<Props>();
const emit = defineEmits<{
  (e: 'submit'): void;
}>();
const state = reactive({
  rules: {
    appId: [
      {
        required: true,
        message: '请输入APPID',
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
  },
});

const formRef = ref();

const submitForm = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      emit('submit');
    }
  });
};

defineExpose({
  formRef,
});
</script>

<template>
  <ElForm ref="formRef" :model="form" label-width="120px" :rules="state.rules">
    <ElFormItem label="应用名称" prop="appName">
      <ElInput v-model="form.appName" maxlength="20" show-word-limit />
    </ElFormItem>
    <ElFormItem label="appId" prop="appId">
      <ElInput v-model="form.appId" :disabled="!isAdd" />
    </ElFormItem>
    <ElFormItem label="appSecret" prop="appSecret">
      <ElInput v-model="form.appSecret" type="appSecret" />
    </ElFormItem>
    <ElFormItem label="小程序码" prop="qrcodeUrl">
      <SelectMaterial v-model="form.qrcodeUrl" :can-choose-images-num="1" />
    </ElFormItem>
    <ElFormItem label="权限范围" prop="shopId">
      <SelectShop :default-shop="true" v-model="form.shopId" />
    </ElFormItem>
    <ElFormItem>
      <ElButton type="primary" :loading="loading" @click="submitForm">
        保 存
      </ElButton>
    </ElFormItem>
  </ElForm>
</template>
