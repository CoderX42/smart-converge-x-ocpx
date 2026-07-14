<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { editObj, getConfig } from '#/api/user/signin-config';
import { useDict } from '#/utils/dict';

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const SigninStreakReward = defineAsyncComponent(
  () => import('../signin-streak-reward/index.vue'),
);

// 字典
const { status } = useDict('status');
const active = ref('config');
const state = reactive({
  form: {
    id: undefined,
    day: 1,
    pointNum: 0,
    growthNum: 0,
    status: '0',
    picUr: '',
  },
  rules: {
    status: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
    pointNum: [
      {
        required: true,
        message: '请输入奖励积分',
        trigger: 'change',
      },
    ],
    growthNum: [
      {
        required: true,
        message: '请输入奖励成长值',
        trigger: 'change',
      },
    ],
    picUrl: [
      {
        required: true,
        message: '请上传图片',
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
      <ElTabs v-model="active" class="demo-tabs">
        <ElTabPane label="签到配置" name="config" />
        <ElTabPane label="连续签到奖励" name="second" />
      </ElTabs>
      <div v-if="active === 'config'">
        <div style="height: 100vh; padding: 20px; overflow-y: auto">
          <ElForm
            ref="formRef"
            :model="state.form"
            label-width="160px"
            :rules="state.rules"
          >
            <ElFormItem label="每日签到奖励积分" prop="pointNum">
              <ElInputNumber
                style="width: 30%"
                :controls="false"
                v-model="state.form.pointNum"
                :precision="0"
                :min="0"
              />
            </ElFormItem>
            <ElFormItem label="每日签到奖励成长值" prop="growthNum">
              <ElInputNumber
                style="width: 30%"
                :controls="false"
                v-model="state.form.growthNum"
                :precision="0"
                :min="0"
              />
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
            <ElFormItem label="图片" prop="picUrl">
              <SelectMaterial
                v-model="state.form.picUrl"
                :can-choose-images-num="1"
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
      <SigninStreakReward v-else-if="active === 'second'" />
    </div>
  </div>
</template>
