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

import { getList } from '#/api/system/article-category';
import { addObj, editObj, getById } from '#/api/system/copyright';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const { copyright_client_type } = useDict('copyright_client_type');

const state = reactive({
  form: {
    id: '',
    logoUrl: '',
    name: '',
    copyrightDesc: '',
    copyrightLink: '',
    icp: '',
    icpUrl: '',
    clientType: '',
    pageTitle: '',
    pageDesc: '',
    pagePic: '',
  },
  rules: {
    logoUrl: [
      {
        required: true,
        message: '请上传logo',
        trigger: 'change',
      },
    ],
    name: [
      {
        required: true,
        message: '请输入名称',
        trigger: 'change',
      },
    ],
    copyrightDesc: [
      {
        required: true,
        message: '请输入版权信息',
        trigger: 'change',
      },
    ],
    copyrightLink: [
      {
        required: true,
        message: '请输入版权链接',
        trigger: 'change',
      },
    ],
    clientType: [
      {
        required: true,
        message: '请选择客户端类型',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const categoryList = ref();
const initForm = (row: any) => {
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
  getCategoryList();
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
// 查询文章分类
const getCategoryList = () => {
  getList({ status: '0' }).then((response) => {
    categoryList.value = response;
  });
};
defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改版权' : '新增版权'"
    width="50%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="客户端类型" prop="clientType">
        <ElRadioGroup v-model="state.form.clientType">
          <ElRadio
            v-for="item in copyright_client_type"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="logo" prop="logo">
        <SelectMaterial
          v-model="state.form.logoUrl"
          :can-choose-images-num="1"
        />
      </ElFormItem>
      <ElFormItem label="名称" prop="name">
        <ElInput v-model="state.form.name" maxlength="30" show-word-limit />
      </ElFormItem>
      <ElFormItem label="版权信息" prop="copyrightDesc">
        <ElInput
          v-model="state.form.copyrightDesc"
          maxlength="100"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="版权链接" prop="copyrightLink">
        <ElInput
          v-model="state.form.copyrightLink"
          maxlength="100"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="ICP备案号" prop="icp">
        <ElInput v-model="state.form.icp" maxlength="100" show-word-limit />
      </ElFormItem>
      <ElFormItem label="ICP网站链接" prop="icpUrl">
        <ElInput v-model="state.form.icpUrl" maxlength="100" show-word-limit />
      </ElFormItem>
      <template v-if="state.form.clientType === '3'">
        <ElFormItem label="页面标题" prop="pageTitle">
          <ElInput
            v-model="state.form.pageTitle"
            maxlength="100"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="页面副标题" prop="pageDesc">
          <ElInput
            v-model="state.form.pageDesc"
            maxlength="100"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="页面图片" prop="pagePic">
          <SelectMaterial
            v-model="state.form.pagePic"
            :can-choose-images-num="1"
          />
        </ElFormItem>
      </template>
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
