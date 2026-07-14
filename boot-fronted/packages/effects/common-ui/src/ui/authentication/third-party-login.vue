<script setup lang="ts">
import { MdiWechat } from '@boot/icons';
import { $t } from '@boot/locales';

import { BootIconButton } from '@boot-core/shadcn-ui';

defineOptions({
  name: 'ThirdPartyLogin',
});

const emit = defineEmits(['handleLogin']);

const clickWechat = () => {
  emit('handleLogin');
};
</script>

<template>
  <div class="w-full sm:mx-auto md:max-w-md">
    <div class="mt-4 flex items-center justify-between">
      <span class="border-input w-[35%] border-b dark:border-gray-600"></span>
      <span class="text-muted-foreground text-center text-xs uppercase">
        {{ $t('authentication.thirdPartyLogin') }}
      </span>
      <span class="border-input w-[35%] border-b dark:border-gray-600"></span>
    </div>

    <div class="mt-4 flex flex-wrap justify-center">
      <BootIconButton class="mb-3" @click="clickWechat">
        <MdiWechat />
      </BootIconButton>
      <!-- <BootIconButton class="mb-3">
        <MdiQqchat />
      </BootIconButton>
      <BootIconButton class="mb-3">
        <MdiGithub />
      </BootIconButton>
      <BootIconButton class="mb-3">
        <MdiGoogle />
      </BootIconButton> -->
    </div>
  </div>
</template>
