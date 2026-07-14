<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

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
  ElSwitch,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/mission';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

// 字典
const { status, mission_type } = useDict('status', 'mission_type');
const state = reactive({
  form: {
    id: undefined,
    name: '',
    type: '',
    icon: '',
    dailyLimit: '0',
    status: '0',
    dailyLimitNum: 0,
    count: 0,
    rewardNum: 0,
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入名称',
        trigger: 'change',
      },
    ],
    type: [
      {
        required: true,
        message: '请选择类型',
        trigger: 'change',
      },
    ],
    icon: [
      {
        required: true,
        message: '请选择等级图标',
        trigger: 'change',
      },
    ],
    rewardNum: [
      {
        required: true,
        message: '请输入成长值',
        trigger: 'change',
      },
    ],
    dailyLimit: [
      {
        required: true,
        message: '请选择每日领取限制',
        trigger: 'change',
      },
    ],
    dailyLimitNum: [
      {
        required: true,
        message: '请输入每日限制领取数量',
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
  state.form.id = undefined;
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
    :title="state.form.id ? '修改任务配置' : '添加任务配置'"
    width="40%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="名称" prop="name">
        <ElInput v-model="state.form.name" maxlength="20" show-word-limit />
      </ElFormItem>
      <ElFormItem label="类型" prop="type">
        <ElRadioGroup v-model="state.form.type">
          <ElRadio
            v-for="item in mission_type"
            :value="item.value"
            :key="item.value"
          >
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="图标" prop="icon">
        <SelectMaterial v-model="state.form.icon" :can-choose-images-num="1" />
      </ElFormItem>
      <ElFormItem label="每日领取限制" prop="dailyLimit">
        <ElSwitch
          v-model="state.form.dailyLimit"
          inline-prompt
          active-value="1"
          inactive-value="0"
          active-text="是"
          inactive-text="否"
        />
      </ElFormItem>
      <ElFormItem
        v-if="state.form.dailyLimit === '1'"
        label="限制领取数量"
        prop="dailyLimitNum"
      >
        <ElInputNumber
          v-model="state.form.dailyLimitNum"
          :precision="0"
          :min="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="成长值" prop="rewardNum">
        <div v-if="state.form.type === '1'">
          订单每
          <ElInputNumber
            style="width: 100px"
            v-model="state.form.count"
            :precision="2"
            :min="0"
            :controls="false"
          />
          元，获得
          <ElInputNumber
            style="width: 100px"
            v-model="state.form.rewardNum"
            :precision="0"
            :min="0"
            :controls="false"
          />成长值
        </div>
        <div v-if="state.form.type === '2'">
          内容分享赠送
          <ElInputNumber
            style="width: 100px"
            v-model="state.form.rewardNum"
            :precision="0"
            :min="0"
            :controls="false"
          />成长值
        </div>
        <div v-if="state.form.type === '3'">
          <ElInputNumber
            style="width: 100px"
            v-model="state.form.count"
            :precision="0"
            :min="0"
            :controls="false"
          />
          积分兑换
          <ElInputNumber
            style="width: 100px"
            v-model="state.form.rewardNum"
            :precision="0"
            :min="0"
            :controls="false"
          />成长值
        </div>
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
