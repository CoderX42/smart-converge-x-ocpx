<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@boot/layouts';
import { preferences } from '@boot/preferences';

import { $t } from '#/locales';
import { useCopyrightStore } from '#/store';

const cs = useCopyrightStore();
cs.ensureFetched('3');
const getCopyrightValue = (value?: string, fallback = '') =>
  cs.loading ? '' : value || fallback;

const appName = computed(() =>
  getCopyrightValue(cs.copyright?.name, preferences.app.name),
);
const logo = computed(() =>
  getCopyrightValue(cs.copyright?.logoUrl, preferences.logo.source),
);
const copyrightDesc = computed(() =>
  getCopyrightValue(cs.copyright?.copyrightDesc),
);
const copyrightLink = computed(() =>
  getCopyrightValue(cs.copyright?.copyrightLink),
);
const sloganImage = computed(() => getCopyrightValue(cs.copyright?.pagePic));
const icp = computed(() => getCopyrightValue(cs.copyright?.icp));
const icpUrl = computed(() => getCopyrightValue(cs.copyright?.icpUrl));
const pageDesc = computed(() => getCopyrightValue(cs.copyright?.pageDesc));
const pageTitle = computed(() => getCopyrightValue(cs.copyright?.pageTitle));
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :logo="logo"
    :page-description="pageDesc || $t('authentication.pageDesc')"
    :page-title="pageTitle || $t('authentication.pageTitle')"
    :copyright-desc="copyrightDesc"
    :copyright-link="copyrightLink"
    :icp="icp"
    :icp-url="icpUrl"
    :slogan-image="sloganImage"
  >
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>
