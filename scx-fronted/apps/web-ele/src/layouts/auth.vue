<script lang="ts" setup>
import { AuthPageLayout } from '@boot/layouts';
import { preferences } from '@boot/preferences';
</script>

<template>
  <AuthPageLayout
    :app-name="preferences.app.name"
    :logo="preferences.logo.source"
    :page-title="preferences.app.name"
  >
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>
