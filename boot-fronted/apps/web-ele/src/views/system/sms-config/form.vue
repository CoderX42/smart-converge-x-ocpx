<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/system/sms-config';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const { status, sms_supplier, sms_config_code } = useDict(
  'status',
  'sms_supplier',
  'sms_config_code',
);

const state = reactive<any>({
  form: {
    id: '',
    supplier: '',
    accessKeyId: '',
    accessKeySecret: '',
    signature: '',
    templateId: '',
    extraParams: '',
    name: '',
    smsCode: '',
    status: '0',
    extraParamList: [],
  },
  rules: {
    supplier: [
      {
        required: true,
        message: '请选择厂商标识',
        trigger: 'change',
      },
    ],
    accessKeyId: [
      {
        required: true,
        message: '请输入accessKeyId',
        trigger: 'change',
      },
    ],
    accessKeySecret: [
      {
        required: true,
        message: '请输入accessKeySecret',
        trigger: 'change',
      },
    ],
    signature: [
      {
        required: true,
        message: '请输入短信签名',
        trigger: 'change',
      },
    ],
    name: [
      {
        required: true,
        message: '请输入短信名称',
        trigger: 'change',
      },
    ],
    smsCode: [
      {
        required: true,
        message: '请输入短信编码',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (id: string) => {
  if (id) {
    state.form.id = id;
    getDetail(id);
  }
  state.form.extraParamList = [];
  dialog.value = true;
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = response;
      // 把state.form.extraParams 转换成数组

      if (state.form.extraParams) {
        const extraParams = JSON.parse(state.form.extraParams);
        state.form.extraParamList = Object.keys(extraParams).map((key) => ({
          label: key,
          value: extraParams[key],
        }));
      }
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
      if (state.form.extraParamList) {
        // 把state.form.extraParamList label 转换成key  value 转换成value
        state.form.extraParams = JSON.stringify(
          state.form.extraParamList?.reduce((pre: any, cur: any) => {
            pre[cur.label] = cur.value;
            return pre;
          }, {}),
        );
      }
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
    :title="state.form.id ? '修改短信配置' : '新增短信配置'"
    width="50%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="短信厂商" prop="supplier">
        <ElSelect v-model="state.form.supplier" placeholder="请选择短信厂商">
          <ElOption
            v-for="item in sms_supplier"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="短信名称" prop="name">
        <ElInput v-model="state.form.name" />
      </ElFormItem>
      <ElFormItem label="短信编码" prop="smsCode">
        <ElSelect v-model="state.form.smsCode" placeholder="请选择短信编码">
          <ElOption
            v-for="item in sms_config_code"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="accessKeyId" prop="accessKeyId">
        <ElInput v-model="state.form.accessKeyId" />
      </ElFormItem>
      <ElFormItem label="accessKeySecret" prop="accessKeySecret">
        <ElInput v-model="state.form.accessKeySecret" />
      </ElFormItem>
      <ElFormItem label="短信签名" prop="signature">
        <ElInput v-model="state.form.signature" />
      </ElFormItem>
      <ElFormItem label="模板ID" prop="templateId">
        <ElInput v-model="state.form.templateId" />
      </ElFormItem>
      <ElFormItem label="额外参数" prop="extraParams">
        <ElButton
          type="text"
          @click="state.form.extraParamList.push({ label: '', value: '' })"
        >
          新增
        </ElButton>
        <ElTable :data="state.form.extraParamList" style="width: 100%">
          <ElTableColumn prop="label" label="参数名" width="180">
            <template #default="scope">
              <ElInput v-model="scope.row.label" />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="value" label="参数值" width="180">
            <template #default="scope">
              <ElInput v-model="scope.row.value" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作">
            <template #default="scope">
              <ElButton
                type="text"
                @click="state.form.extraParamList.splice(scope.$index, 1)"
              >
                删除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <!-- <JsonEditorVue
          v-model="state.form.extraParams"
          :options="editorOptions"
        /> -->
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
