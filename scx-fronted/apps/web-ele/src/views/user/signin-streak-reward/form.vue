<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/signin-streak-reward';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
// 字典
const { status } = useDict('status');
const state = reactive({
  form: {
    id: undefined,
    streakDays: 1,
    bonusPoints: 0,
    bonusGrowth: 0,
    status: '0',
  },
  rules: {
    streakDays: [
      {
        required: true,
        message: '请输入连续签到天数',
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
    bonusPoints: [
      {
        required: true,
        message: '请输入额外奖励积分',
        trigger: 'change',
      },
    ],
    bonusGrowth: [
      {
        required: true,
        message: '请输入额外奖励成长值',
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
    :title="state.form.id ? '修改连续签到奖励' : '添加连续签到奖励'"
    width="30%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="连续签到天数" prop="streakDays">
        <ElInputNumber
          style="width: 60%"
          :controls="false"
          v-model="state.form.streakDays"
          :precision="0"
          :min="0"
        />
      </ElFormItem>

      <ElFormItem label="额外奖励积分" prop="bonusPoints">
        <ElInputNumber
          style="width: 60%"
          :controls="false"
          v-model="state.form.bonusPoints"
          :precision="0"
          :min="0"
        />
      </ElFormItem>
      <ElFormItem label="额外奖励成长值" prop="bonusGrowth">
        <ElInputNumber
          style="width: 60%"
          :controls="false"
          v-model="state.form.bonusGrowth"
          :precision="0"
          :min="0"
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
          :disabled="loading"
          @click="submitForm(formRef)"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
