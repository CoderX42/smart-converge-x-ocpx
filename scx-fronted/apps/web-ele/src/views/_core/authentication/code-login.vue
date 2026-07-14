<script lang="ts" setup>
import type { BootFormSchema } from '@boot/common-ui';
import type { Recordable } from '@boot/types';

import { computed, onMounted } from 'vue';

import { AuthenticationCodeLogin, z } from '@boot/common-ui';
import { $t } from '@boot/locales';

import { ElMessage } from 'element-plus';

import { useAuthStore, useCopyrightStore } from '#/store';

defineOptions({ name: 'CodeLogin' });

const cs = useCopyrightStore();

const CODE_LENGTH = 4;
const authStore = useAuthStore();
const formSchema = computed((): BootFormSchema[] => {
  return [
    {
      component: 'BootInput',
      componentProps: {
        placeholder: $t('authentication.mobile'),
      },
      fieldName: 'phoneNumber',
      label: $t('authentication.mobile'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.mobileTip') })
        .refine((v) => /^\d{11}$/.test(v), {
          message: $t('authentication.mobileErrortip'),
        }),
    },
    {
      component: 'BootPinInput',
      componentProps: (values) => {
        return {
          codeLength: CODE_LENGTH,
          createText: (countdown: number) => {
            const text =
              countdown > 0
                ? $t('authentication.sendText', [countdown])
                : $t('authentication.sendCode');
            return text;
          },
          handleSendCode: async () => {
            const phoneNumber = `${values.phoneNumber ?? ''}`.trim();
            if (!phoneNumber) {
              const message = $t('authentication.mobileTip');
              ElMessage.warning(message);
              throw new Error(message);
            }
            if (!/^\d{11}$/.test(phoneNumber)) {
              const message = $t('authentication.mobileErrortip');
              ElMessage.warning(message);
              throw new Error(message);
            }
            try {
              await authStore.sendSmsCode('login', phoneNumber);
              ElMessage.success('发送成功');
            } catch (error) {
              console.error(error);
              throw error;
            }
          },
          placeholder: $t('authentication.code'),
        };
      },
      fieldName: 'code',
      label: $t('authentication.code'),
      rules: z.string().length(CODE_LENGTH, {
        message: $t('authentication.codeTip', [CODE_LENGTH]),
      }),
    },
  ];
});
/**
 * 异步处理登录操作
 * Asynchronously handle the login process
 * @param values 登录表单数据
 */
async function handleLogin(values: Recordable<any>) {
  try {
    await authStore.authMobileLogin({
      phone: values.phoneNumber,
      code: values.code,
    });
  } catch (error) {
    console.error(error);
  }
}
onMounted(async () => {
  await cs.ensureFetched('3');
});
const loginTitle = computed(() => (cs.loading ? '' : cs.copyright?.name));
</script>

<template>
  <AuthenticationCodeLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :title="loginTitle"
    @submit="handleLogin"
  />
</template>
