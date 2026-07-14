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
  ElTreeSelect,
} from 'element-plus';

import { addObj, editObj, getById, getTree } from '#/api/system/region';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
// 字典
const { status, region_level } = useDict('status', 'region_level');
const state = reactive({
  form: {
    id: '',
    name: '',
    parentId: '',
    code: '',
    level: '',
    sort: 0,
    status: '0',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入区域名称',
        trigger: 'change',
      },
    ],
    code: [
      {
        required: true,
        message: '请输入区域代码',
        trigger: 'change',
      },
    ],
    parentId: [
      {
        required: true,
        message: '请选择上级区域',
        trigger: 'change',
      },
    ],
    sort: [
      {
        required: true,
        message: '请输入排序序号',
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
  regionTree: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const defaultProps = {
  children: 'children',
  label: 'name',
};
const initForm = (row: any) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }
  getDeptData();
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
const getDeptData = async () => {
  await getTree().then((response: any) => {
    const region = {
      id: '0',
      name: '顶级区域',
      children: [],
      parentId: '0',
    };
    const regionList = response || [];
    regionList.unshift(region);
    state.regionTree = regionList;
  });
};
defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改行政区划' : '添加行政区划'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="区域名称" prop="name">
        <ElInput v-model="state.form.name" />
      </ElFormItem>
      <ElFormItem label="区域代码" prop="code">
        <ElInput v-model="state.form.code" />
      </ElFormItem>
      <ElFormItem label="上级区域" prop="parentId">
        <ElTreeSelect
          style="width: 100%"
          v-model="state.form.parentId"
          :data="state.regionTree"
          check-strictly
          :props="defaultProps"
          :render-after-expand="false"
          node-key="id"
        />
      </ElFormItem>
      <ElFormItem label="层级" prop="level">
        <ElRadioGroup v-model="state.form.level">
          <ElRadio
            v-for="item in region_level"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="排序序号" prop="sort">
        <ElInputNumber v-model="state.form.sort" :min="0" />
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
          @click="submitForm(formRef)"
          :loading="loading"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
