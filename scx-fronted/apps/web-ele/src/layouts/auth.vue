<script lang="ts" setup>
import { AuthPageLayout } from '@boot/layouts';
import { preferences } from '@boot/preferences';

import { $t } from '#/locales';

const appName = '瑶知智汇广告平台';
const pageTitle = '瑶知智汇广告平台';
const pageDesc = '单企业效果广告投放与归因管理平台';
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :logo="preferences.logo.source"
    :page-description="pageDesc || $t('authentication.pageDesc')"
    :page-title="pageTitle || $t('authentication.pageTitle')"
  >
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>