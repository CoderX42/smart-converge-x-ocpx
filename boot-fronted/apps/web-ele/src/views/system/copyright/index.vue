<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { editObj, getById } from '#/api/system/copyright';
import { useDict } from '#/utils/dict';

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const { copyright_client_type } = useDict('copyright_client_type');
const active = ref('1');

const state = reactive({
  queryParams: {
    name: '',
  },
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
const loading = ref(false);
const formRef = ref();
const initPage = async () => {
  loading.value = true;
  getById(active.value).then((res) => {
    state.form = res;
    loading.value = false;
  });
};

/**
 * 重置搜索表单
 */
const resetQuery = () => {
  formRef.value.resetFields();
};
const handleClick = (tab: any) => {
  active.value = tab.paneName;
  initPage();
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      editObj(state.form)
        .then(() => {
          ElMessage.success('修改成功');
          loading.value = false;
        })
        .catch(() => {
          loading.value = false;
        });
    }
  });
};
initPage();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <ElTabs v-model="active" class="demo-tabs" @tab-click="handleClick">
        <ElTabPane
          :label="item.label"
          v-for="(item, index) in copyright_client_type"
          :key="index"
          :name="item.value"
        />
      </ElTabs>
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="140px"
        :rules="state.rules"
      >
        <ElFormItem label="logo" prop="logoUrl">
          <SelectMaterial
            v-model="state.form.logoUrl"
            :can-choose-images-num="1"
          />
        </ElFormItem>
        <ElFormItem label="名称" prop="name">
          <ElInput
            style="width: 30%"
            v-model="state.form.name"
            maxlength="30"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="版权信息" prop="copyrightDesc">
          <ElInput
            style="width: 30%"
            v-model="state.form.copyrightDesc"
            maxlength="100"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="版权链接" prop="copyrightLink">
          <ElInput
            style="width: 30%"
            v-model="state.form.copyrightLink"
            maxlength="100"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="ICP备案号" prop="icp">
          <ElInput
            v-model="state.form.icp"
            maxlength="100"
            style="width: 30%"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="ICP网站链接" prop="icpUrl">
          <ElInput
            v-model="state.form.icpUrl"
            maxlength="100"
            style="width: 30%"
            show-word-limit
          />
        </ElFormItem>
        <template v-if="state.form.clientType === '3'">
          <ElFormItem label="登录页标题" prop="pageTitle">
            <ElInput
              v-model="state.form.pageTitle"
              maxlength="60"
              style="width: 30%"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="登录页副标题" prop="pageDesc">
            <ElInput
              v-model="state.form.pageDesc"
              maxlength="128"
              style="width: 30%"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="登录页图标" prop="pagePic">
            <SelectMaterial
              v-model="state.form.pagePic"
              :can-choose-images-num="1"
            />
          </ElFormItem>
        </template>
        <ElFormItem>
          <ElButton
            type="primary"
            :loading="loading"
            @click="submitForm(formRef)"
          >
            确 认
          </ElButton>
          <ElButton @click="resetQuery">重 置</ElButton>
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>
