<script lang="ts" setup>
import type { DropdownMenuProps } from '@boot-core/shadcn-ui';

import { ChevronDown } from '@boot-core/icons';
import { BootDropdownMenu } from '@boot-core/shadcn-ui';

defineProps<DropdownMenuProps>();
</script>

<template>
  <BootDropdownMenu :menus="menus" :modal="false">
    <div
      class="flex-center hover:bg-muted hover:text-foreground text-muted-foreground border-border h-full cursor-pointer border-l px-2 text-lg font-semibold"
    >
      <ChevronDown class="size-4" />
    </div>
  </BootDropdownMenu>
</template>
