<script lang="ts" setup>
import { Settings } from '@boot/icons';

import { BootIconButton } from '@boot-core/shadcn-ui';

import Preferences from './preferences.vue';

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();

function clearPreferencesAndLogout() {
  emit('clearPreferencesAndLogout');
}
</script>
<template>
  <Preferences @clear-preferences-and-logout="clearPreferencesAndLogout">
    <BootIconButton>
      <Settings class="text-foreground size-4" />
    </BootIconButton>
  </Preferences>
</template>
