<script lang="ts" setup>
import { useRoute, useRouter } from 'vue-router';

import MediaScopes from '../../delivery/media-scopes/index.vue';

const route = useRoute();
const router = useRouter();

function openWorkspace(path: string, mediaId?: string) {
  void router.push({
    path,
    query: { ...route.query, ...(mediaId ? { mediaId } : {}) },
  });
}
</script>

<template>
  <el-alert
    class="mb-4"
    :closable="false"
    show-icon
    title="媒体配置入口已统一"
    type="info"
  >
    <template #default>
      选择媒体后，可沿同一媒体上下文继续配置渠道接入和媒体库存。
      <el-button
        link
        type="primary"
        @click="openWorkspace('/inventory/channel-access')"
      >
        前往渠道接入
      </el-button>
      <el-button
        link
        type="primary"
        @click="openWorkspace('/inventory/supply')"
      >
        前往媒体库存
      </el-button>
    </template>
  </el-alert>
  <MediaScopes />
</template>
