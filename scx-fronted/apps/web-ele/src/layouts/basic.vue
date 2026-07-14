<script lang="ts" setup>
import { computed, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@boot/common-ui';
import { useWatermark } from '@boot/hooks';
import { SvgUserIcon } from '@boot/icons';
import { BasicLayout, LockScreen, UserDropdown } from '@boot/layouts';
import { preferences } from '@boot/preferences';
import { useAccessStore, useUserStore } from '@boot/stores';

import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();
const avatar = computed(() => userStore.userInfo?.avatar ?? preferences.app.defaultAvatar);
const menus = computed(() => [{
  handler: () => router.push('/user/profile'), icon: SvgUserIcon, text: '个人中心',
}]);

async function handleLogout() {
  await authStore.logout(false);
}

watch(() => preferences.app.watermark, async (enabled) => {
  if (enabled) {
    await updateWatermark({ content: `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}` });
  } else {
    destroyWatermark();
  }
}, { immediate: true });
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :menus
        :text="userStore.userInfo?.username"
        :description="userStore.userInfo?.email"
        @logout="handleLogout"
      />
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal v-model:open="accessStore.loginExpired" :avatar>
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
