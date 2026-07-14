<script lang="ts" setup>
import { BootSpinner } from '@boot-core/shadcn-ui';

import { useContentSpinner } from './use-content-spinner';

defineOptions({ name: 'LayoutContentSpinner' });

const { spinning } = useContentSpinner();
</script>
<template>
  <BootSpinner :spinning="spinning" />
</template>
