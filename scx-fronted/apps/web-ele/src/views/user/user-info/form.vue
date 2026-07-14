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
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { getList } from '#/api/user/level';
import { addObj, editObj, getById } from '#/api/user/user-info';
import { useDict } from '#/utils/dict';
import {
  getPasswordStrengthError,
  PASSWORD_COMPLEXITY_HINT,
} from '#/utils/password-rules';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

const { user_sex, user_source } = useDict('user_sex', 'user_source');

// 字典
const state = reactive({
  form: {
    id: '',
    nickname: '',
    levelId: '',
    avatarUrl: '',
    phone: '',
    password: '',
    province: '',
    city: '',
    sex: '',
    userSource: '',
  },
  rules: {
    nickname: [
      {
        required: true,
        message: '请输入昵称',
        trigger: 'change',
      },
    ],
    levelId: [
      {
        required: true,
        message: '请选择等级',
        trigger: 'change',
      },
    ],
    userSource: [
      {
        required: true,
        message: '请选择用户来源',
        trigger: 'change',
      },
    ],
    avatarUrl: [
      {
        required: true,
        message: '请上传头像',
        trigger: 'change',
      },
    ],
    phone: [
      {
        required: true,
        message: '请输入手机号',
        trigger: 'change',
      },
      {
        pattern: /^1[3-9]\d{9}$/,
        message: '请输入正确的手机号格式',
        trigger: 'blur',
      },
    ],
    password: [
      {
        validator: (_rule: any, value: any, callback: any) => {
          if (state.form.id) {
            callback();
            return;
          }
          const err = getPasswordStrengthError(value, { required: true });
          if (err) callback(new Error(err));
          else callback();
        },
        trigger: 'blur',
      },
    ],
  },
});
const levelList = ref<any>([]);
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (row: any) => {
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
  getLevelList();
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      Object.assign(state.form, response);
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
  state.form.password = '';
  state.form.phone = '';
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

const getLevelList = () => {
  getList({}).then((res: any) => {
    levelList.value = res;
  });
};
defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改用户信息' : '新增用户信息'"
    width="40%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="昵称" prop="nickname">
        <ElInput v-model="state.form.nickname" maxlength="20" show-word-limit />
      </ElFormItem>
      <ElFormItem label="等级" prop="levelId">
        <ElSelect
          v-model="state.form.levelId"
          style="width: 100%"
          placeholder="请选择等级"
        >
          <ElOption
            v-for="item in levelList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="来源" prop="userSource">
        <ElRadioGroup
          v-model="state.form.userSource"
          style="width: 100%"
          placeholder="请选择来源"
        >
          <ElRadio
            v-for="item in user_source"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="头像" prop="avatarUrl">
        <SelectMaterial
          v-model="state.form.avatarUrl"
          :can-choose-images-num="1"
        />
      </ElFormItem>
      <ElFormItem label="性别" prop="sex">
        <ElSelect
          v-model="state.form.sex"
          style="width: 100%"
          placeholder="请选择性别"
        >
          <ElOption
            v-for="item in user_sex"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="手机号" prop="phone" v-if="!state.form.id">
        <ElInput v-model="state.form.phone" />
      </ElFormItem>
      <ElFormItem label="密码" prop="password" v-if="!state.form.id">
        <ElInput
          v-model="state.form.password"
          type="password"
          maxlength="32"
          show-password
          autocomplete="new-password"
        />
        <div class="password-hint">{{ PASSWORD_COMPLEXITY_HINT }}</div>
      </ElFormItem>
      <ElFormItem label="所在省份" prop="province">
        <ElInput v-model="state.form.province" />
      </ElFormItem>
      <ElFormItem label="所在城市" prop="city">
        <ElInput v-model="state.form.city" />
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

<style scoped>
.password-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
