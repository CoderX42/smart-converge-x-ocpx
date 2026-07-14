<script lang="ts" setup>
import type { BootFormSchema } from '@boot/common-ui';

import { computed, onMounted, ref } from 'vue';

import { AuthenticationLogin, z } from '@boot/common-ui';
import { $t } from '@boot/locales';

import { ElMessage } from 'element-plus';

import { getByType } from '#/api/system/social-account';
import Verify from '#/components/verifition/index.vue';
import { useAuthStore, useCopyrightStore } from '#/store';

defineOptions({ name: 'Login' });

const cs = useCopyrightStore();

const verifyRef = ref();
const captchaType = ref('blockPuzzle'); // 1）滑动拼图 blockPuzzle 2）文字点选 clickWord
const authStore = useAuthStore();
const loginUser = ref({
  username: '',
  password: '',
  code: '',
  randomStr: 'blockPuzzle',
});
const formSchema = computed((): BootFormSchema[] => {
  return [
    {
      component: 'BootInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'BootInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});
const handleSubmit = (val: any) => {
  loginUser.value.username = val.username;
  loginUser.value.password = val.password;
  verifyRef.value.show();
};
const verifySuccess = async (params: any) => {
  const { captchaVerification } = params;
  loginUser.value.code = captchaVerification;
  try {
    await authStore.authLogin(loginUser.value);
  } catch (error) {
    console.error(error);
  }
};
const wxLogin = async () => {
  const res = await getByType('1');
  if (!res) {
    return ElMessage.error('微信配置存在');
  }
  const appId = res.appId;
  const redirect = encodeURIComponent(
    `${window.location.origin}/#/third-party-success`,
  );
  const url = `https://open.weixin.qq.com/connect/qrconnect?appid=${appId}&redirect_uri=${redirect}&response_type=code&scope=snsapi_login&state=login#wechat_redirect`;

  // Open in a centered popup window with 500px width and height
  const width = 500;
  const height = 500;

  // Center based on viewport (window) rather than screen
  const left = (window.innerWidth - width) / 2 + window.screenX;
  const top = (window.innerHeight - height) / 2 + window.screenY;

  const windowFeatures = `width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes,status=yes`;
  window.open(url, 'WeChatLogin', windowFeatures);
  // 监听来自子窗口的消息
  const handleMessage = async (event) => {
    // 这里建议加安全校验，比如 event.origin === 'https://你的域名'
    if (event.data?.type === 'wechatLoginSuccess') {
      const code = event.data?.code;
      // 刷新页面
      wxCodeLogin(code);
      window.removeEventListener('message', handleMessage);
    }
  };

  window.addEventListener('message', handleMessage);
};
const wxCodeLogin = async (code: string) => {
  const params = {
    code,
    type: '1',
  };
  authStore.authSocialLogin(params);
};
onMounted(async () => {
  await cs.ensureFetched('3');
});
const loginTitle = computed(() => (cs.loading ? '' : cs.copyright?.name));
</script>

<template>
  <div>
    <AuthenticationLogin
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :title="loginTitle"
      @submit="handleSubmit"
      @wx-login="wxLogin"
    />
    <teleport to="body">
      <Verify
        mode="pop"
        :captcha-type="captchaType"
        :img-size="{ width: '320px', height: '150px' }"
        ref="verifyRef"
        @success="verifySuccess"
      />
    </teleport>
  </div>
</template>
