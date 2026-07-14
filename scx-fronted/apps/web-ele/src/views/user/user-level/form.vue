<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElColorPicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/level';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

// 字典
const { status } = useDict('status');
const state = reactive({
  form: {
    id: undefined,
    name: '',
    level: 1,
    icon: '',
    bgPic: '',
    status: '0',
    growthValue: 0,
    discount: 0,
    bgColor: '',
    cardBgColor: '',
    cardBgPic: '',
    fontColor: '',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入名称',
        trigger: 'change',
      },
    ],
    level: [
      {
        required: true,
        message: '请输入等级',
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
    bgPic: [
      {
        required: true,
        message: '请选择背景图',
        trigger: 'change',
      },
    ],
    cardBgPic: [
      {
        required: true,
        message: '请选择卡片背景图',
        trigger: 'change',
      },
    ],
    cardBgColor: [
      {
        required: true,
        message: '请选择卡片背景色',
        trigger: 'change',
      },
    ],
    growthValue: [
      {
        required: true,
        message: '请输入成长值',
        trigger: 'change',
      },
    ],
    discount: [
      {
        required: true,
        message: '请输入折扣',
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
    fontColor: [
      {
        required: true,
        message: '请选择字体颜色',
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
    .then((response) => {
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
    :title="state.form.id ? '修改等级配置' : '添加等级配置'"
    width="40%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="等级名称" prop="name">
        <ElInput v-model="state.form.name" maxlength="20" show-word-limit />
      </ElFormItem>
      <ElFormItem label="等级" prop="level">
        <ElInputNumber
          style="width: 100%"
          v-model="state.form.level"
          :precision="0"
          :min="1"
          controls-position="right"
        />
      </ElFormItem>
      <ElFormItem label="等级图标" prop="icon">
        <SelectMaterial v-model="state.form.icon" :can-choose-images-num="1" />
      </ElFormItem>
      <ElFormItem label="字体颜色" prop="fontColor">
        <ElColorPicker v-model="state.form.fontColor" />
      </ElFormItem>
      <ElFormItem label="背景图">
        <div style="display: flex; flex-direction: column; width: 100%">
          <SelectMaterial
            v-model="state.form.bgPic"
            :can-choose-images-num="1"
          />
          <div style="color: #a8abb2">背景图优先级高于背景色</div>
        </div>
      </ElFormItem>
      <ElFormItem label="背景色">
        <div style="display: flex; flex-direction: column; width: 100%">
          <ElColorPicker v-model="state.form.bgColor" show-alpha />
          <div style="color: #a8abb2">背景色优先级低于背景图</div>
        </div>
      </ElFormItem>
      <ElFormItem label="卡片背景图">
        <div style="display: flex; flex-direction: column; width: 100%">
          <SelectMaterial
            v-model="state.form.cardBgPic"
            :can-choose-images-num="1"
          />
          <div style="color: #a8abb2">背景图优先级高于背景色</div>
        </div>
      </ElFormItem>
      <ElFormItem label="卡片背景色">
        <div style="display: flex; flex-direction: column; width: 100%">
          <ElColorPicker v-model="state.form.cardBgColor" show-alpha />
          <div style="color: #a8abb2">背景色优先级低于背景图</div>
        </div>
      </ElFormItem>
      <ElFormItem label="成长值" prop="growthValue">
        <ElInputNumber
          style="width: 100%"
          v-model="state.form.growthValue"
          :precision="0"
          :min="0"
          controls-position="right"
        />
      </ElFormItem>
      <ElFormItem label="折扣（%）" prop="discount">
        <div style="display: flex; flex-direction: column; width: 100%">
          <ElInputNumber
            style="width: 100%"
            v-model="state.form.discount"
            :precision="0"
            :min="0"
            controls-position="right"
          />
          <div style="color: #a8abb2">1-100%，100%代表无折扣，90代表9折。</div>
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
