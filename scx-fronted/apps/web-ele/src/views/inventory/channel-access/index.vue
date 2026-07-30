<script lang="ts" setup>
import ChannelUsers from '../../delivery/channel-users/index.vue';
import Documents from '../../delivery/documents/index.vue';
import MediaChannels from '../../delivery/media-channels/index.vue';
import QueryTabs from '../../workspaces/components/QueryTabs.vue';

const tabs = [
  { label: '投放渠道', name: 'channels' },
  { label: '接入文档', name: 'documents' },
  { label: '渠道用户', name: 'users' },
];
</script>

<template>
  <QueryTabs v-slot="{ active }" default-tab="channels" :tabs="tabs">
    <MediaChannels v-if="active === 'channels'" />
    <Documents v-else-if="active === 'documents'" />
    <ChannelUsers v-else />
  </QueryTabs>
</template>
