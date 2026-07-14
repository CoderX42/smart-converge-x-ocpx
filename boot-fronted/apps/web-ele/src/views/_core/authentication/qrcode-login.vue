<script lang="ts" setup>
import { AuthenticationQrCodeLogin } from '@boot/common-ui';
import { LOGIN_PATH } from '@boot/constants';

defineOptions({ name: 'QrCodeLogin' });
</script>

<template>
  <AuthenticationQrCodeLogin :login-path="LOGIN_PATH" />
</template>
