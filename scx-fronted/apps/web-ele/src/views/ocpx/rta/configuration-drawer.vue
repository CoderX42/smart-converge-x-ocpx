<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@boot/access';

import { rtaOptions } from '#/api/ocpx/rta';

import RtaWorkspace from './workspace.vue';

const props = defineProps<{ kind: 'bindings' | 'media' | 'upstreams' }>();
const emit = defineEmits<{ saved: [] }>();
const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const keys = {
  upstreams: 'rtaAdvertiserId',
  media: 'rtaMediaId',
  bindings: 'rtaTaskId',
};
const entities = {
  upstreams: 'advertisers',
  media: 'media',
  bindings: 'tasks',
} as const;
const key = computed(() => keys[props.kind]);
const scopeId = computed(() =>
  typeof route.query[key.value] === 'string'
    ? String(route.query[key.value])
    : '',
);
const name = ref('');
const error = ref('');
const loading = ref(false);
let sequence = 0;
watch(
  scopeId,
  async (id) => {
    const token = ++sequence;
    name.value = '';
    error.value = '';
    if (!id || !hasAccessByCodes([`ocpx:rta:${props.kind}:view`])) return;
    loading.value = true;
    try {
      const rows = await rtaOptions(entities[props.kind], { keyword: id });
      if (token !== sequence) return;
      const target = rows.find((item) => item.id === id);
      if (!target) throw new Error('对象不存在或无权访问');
      name.value = target.name;
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
function close() {
  const query = { ...route.query };
  delete query[key.value];
  void router.replace({ query });
}
</script>
<template>
  <el-drawer
    :model-value="!!scopeId && hasAccessByCodes([`ocpx:rta:${kind}:view`])"
    :title="`${name || '当前对象'} · RTA 配置`"
    size="min(1080px, 96vw)"
    destroy-on-close
    @close="close"
  >
    <div v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <RtaWorkspace
        v-if="name"
        :key="scopeId"
        :kind="kind"
        :scope-id="scopeId"
        :scope-name="name"
        @saved="emit('saved')"
      />
    </div>
  </el-drawer>
</template>
