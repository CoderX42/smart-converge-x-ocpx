<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useUserStore } from '@boot/stores';

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

import { addObj, editObj, getById } from '#/api/open/miniapp-register';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const router = useRouter();
const { miniapp_register_code_type, miniapp_register_type } = useDict(
  'miniapp_register_code_type',
  'miniapp_register_type',
);
const userStore = useUserStore();

const state = reactive({
  form: {
    id: '',
    name: '',
    code: '',
    codeType: '',
    legalPersonaWechat: '',
    legalPersonaName: '',
    componentPhone: '',
    status: '',
    type: '1',
    openId: userStore.userInfo?.wxOpenId,
    legalPersonaIdCard: '',
  },
  rules: {
    name: [
      {
        required: true,
        message: () =>
          state.form.type === '2' ? '请输入小程序名称' : '请输入企业名',
        trigger: 'change',
      },
    ],
    openId: [
      {
        required: true,
        message: '请输入openId',
        trigger: 'change',
      },
    ],
    code: [
      {
        required: true,
        message: '请输入企业代码',
        trigger: 'change',
      },
    ],
    codeType: [
      {
        required: true,
        message: '请输入企业代码类型',
        trigger: 'change',
      },
    ],
    legalPersonaWechat: [
      {
        required: true,
        message: '请输入法人微信号',
        trigger: 'change',
      },
    ],
    legalPersonaName: [
      {
        required: true,
        message: '请输入法人姓名',
        trigger: 'change',
      },
    ],
    legalPersonaIdCard: [
      {
        required: true,
        message: '请输入法人身份证号',
        trigger: 'change',
      },
    ],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const isAdd = ref(false);

const initForm = (row: any | undefined) => {
  dialog.value = true;
  isAdd.value = true;
  state.form.id = row.id;
  if (row && row.id) {
    getDetail(row.id);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response) => {
    state.form = response;
    loading.value = false;
    isAdd.value = false;
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
      if (isAdd.value) {
        // 新增
        add();
      } else {
        // 修改
        edit();
      }
    }
  });
};
/**
 * 新增
 */
const add = async () => {
  addObj(state.form).then(() => {
    resetForm(formRef.value);
    ElMessage.success('新增成功');
    emit('initPage');
  });
};
/**
 * 修改
 */
const edit = async () => {
  editObj(state.form).then(() => {
    resetForm(formRef.value);
    ElMessage.success('修改成功');
    emit('initPage');
  });
};
const toProfile = () => {
  router.push('/user/profile');
};
defineExpose({
  initForm,
});
</script>
<template>
  <div class="system-add-user-container">
    <ElDialog
      v-model="dialog"
      :title="!isAdd ? '修改小程序注册' : '新增小程序注册'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="200px"
        :rules="state.rules"
      >
        <ElFormItem label="注册类型" prop="type">
          <ElRadioGroup v-model="state.form.type">
            <ElRadio
              v-for="item in miniapp_register_type"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem
          :label="state.form.type === '2' ? '小程序名称' : '企业名'"
          prop="name"
        >
          <ElInput v-model="state.form.name" />
          <div
            v-if="state.form.type === '2'"
            style="margin-top: 5px; font-size: 12px; color: #909399"
          >
            小程序名称，昵称半自动设定，强制后缀“的体验小程序”。且该参数会进行关键字检查，如果命中品牌关键字则会报错。
            如品牌客户要用试用小程序，建议先换个名字，认证后再修改成品牌名。
            只支持4-30个字符
          </div>
          <div v-else style="margin-top: 5px; font-size: 12px; color: #909399">
            企业名（需与工商部门登记信息一致）；如果是"无主体名称个体工商户"则填"个体户+法人姓名"，例如"个体户张三"
          </div>
        </ElFormItem>
        <template v-if="state.form.type === '1' || state.form.type === '3'">
          <ElFormItem label="企业代码类型" prop="codeType">
            <ElRadioGroup v-model="state.form.codeType">
              <ElRadio
                v-for="item in miniapp_register_code_type"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="企业代码" prop="code">
            <ElInput v-model="state.form.code" type="appSecret" />
          </ElFormItem>

          <ElFormItem label="法人微信号" prop="legalPersonaWechat">
            <ElInput v-model="state.form.legalPersonaWechat" />
          </ElFormItem>
          <ElFormItem label="法人姓名" prop="legalPersonaName">
            <ElInput v-model="state.form.legalPersonaName" />
          </ElFormItem>
          <ElFormItem
            v-if="state.form.type === '3'"
            label="法人身份证号"
            prop="legalPersonaIdCard"
          >
            <ElInput v-model="state.form.legalPersonaIdCard" />
          </ElFormItem>
          <ElFormItem label="第三方联系电话" prop="componentPhone">
            <ElInput v-model="state.form.componentPhone" />
          </ElFormItem>
        </template>
        <template v-else>
          <ElFormItem label="openId" prop="openId">
            <ElInput
              v-if="state.form.openId"
              v-model="state.form.openId"
              disabled
            />
            <div v-else>
              请先绑定微信账号
              <ElButton type="primary" @click="toProfile">去绑定</ElButton>
            </div>
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
  </div>
</template>
