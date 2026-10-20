<script setup lang="ts">
import type { RtaOpenTarget } from './forms/types';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@boot/access';

import { rtaOptions } from '#/api/ocpx/rta';

import MediaAccessForm from './forms/media-access-form.vue';
import TaskBindingForm from './forms/task-binding-form.vue';
import UpstreamPanel from './forms/upstream-panel.vue';

type Kind = 'bindings' | 'media' | 'upstreams';

const emit = defineEmits<{ saved: [] }>();
const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const definitions = {
  media: {
    entity: 'media',
    key: 'rtaMediaId',
    size: 'min(640px, 96vw)',
    title: '媒体 RTA 接入',
  },
  upstreams: {
    entity: 'advertisers',
    key: 'rtaAdvertiserId',
    size: 'min(1080px, 96vw)',
    title: '广告主 RTA 接入',
  },
  bindings: {
    entity: 'tasks',
    key: 'rtaTaskId',
    size: 'min(760px, 96vw)',
    title: '任务 RTA 绑定',
  },
} as const;
const order: Kind[] = ['media', 'upstreams', 'bindings'];
const RETURN_KEY = 'rtaReturnTaskId';
const SELECT_KEY = 'rtaSelect';

function queryText(key: string) {
  const value = route.query[key];
  return typeof value === 'string' ? value : '';
}

const active = computed(() => {
  for (const kind of order) {
    const id = queryText(definitions[kind].key);
    if (id && hasAccessByCodes([`ocpx:rta:${kind}:view`])) return { id, kind };
  }
  return undefined;
});
const returnTaskId = computed(() =>
  active.value?.kind === 'bindings' ? '' : queryText(RETURN_KEY),
);
const name = ref('');
const error = ref('');
const loading = ref(false);
let sequence = 0;

watch(
  () => active.value && `${active.value.kind}:${active.value.id}`,
  async () => {
    const token = ++sequence;
    name.value = '';
    error.value = '';
    const target = active.value;
    if (!target) return;
    loading.value = true;
    try {
      const rows = await rtaOptions(definitions[target.kind].entity, {
        keyword: target.id,
      });
      if (token !== sequence) return;
      const found = rows.find((item) => item.id === target.id);
      if (!found) throw new Error('对象不存在或无权访问');
      name.value = found.name;
    } catch (error_) {
      if (token === sequence)
        error.value =
          error_ instanceof Error ? error_.message : '加载失败，请重新打开';
    } finally {
      if (token === sequence) loading.value = false;
    }
  },
  { immediate: true },
);

function withoutRtaKeys() {
  const query = { ...route.query };
  for (const kind of order) delete query[definitions[kind].key];
  delete query[RETURN_KEY];
  delete query[SELECT_KEY];
  return query;
}

function close() {
  const query = withoutRtaKeys();
  if (returnTaskId.value) query[definitions.bindings.key] = returnTaskId.value;
  void router.replace({ query });
}

function open(target: RtaOpenTarget) {
  const taskId = active.value?.kind === 'bindings' ? active.value.id : '';
  const kind: Kind = target.target === 'media' ? 'media' : 'upstreams';
  const query = withoutRtaKeys();
  query[definitions[kind].key] = target.id;
  if (target.selectId) query[SELECT_KEY] = target.selectId;
  if (taskId) query[RETURN_KEY] = taskId;
  void router.replace({ query });
}
</script>

<template>
  <el-drawer
    :key="active ? `${active.kind}:${active.id}` : 'closed'"
    :model-value="!!active"
    :size="active ? definitions[active.kind].size : undefined"
    destroy-on-close
    @close="close"
  >
    <template #header>
      <div class="rta-drawer__title">
        <el-button v-if="returnTaskId" link type="primary" @click="close"
          >← 返回任务绑定</el-button
        >
        <span
          >{{ name || '当前对象' }} ·
          {{ active ? definitions[active.kind].title : '' }}</span
        >
      </div>
    </template>
    <div v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-if="active && name">
        <MediaAccessForm
          v-if="active.kind === 'media'"
          :key="active.id"
          :media-id="active.id"
          :media-name="name"
          @saved="emit('saved')"
        />
        <UpstreamPanel
          v-else-if="active.kind === 'upstreams'"
          :key="active.id"
          :advertiser-id="active.id"
          :advertiser-name="name"
          :select-id="queryText(SELECT_KEY) || undefined"
          @saved="emit('saved')"
        />
        <TaskBindingForm
          v-else
          :key="active.id"
          :task-id="active.id"
          @open="open"
          @saved="emit('saved')"
        />
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.rta-drawer__title {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 16px;
  color: var(--el-text-color-primary);
}
</style>
