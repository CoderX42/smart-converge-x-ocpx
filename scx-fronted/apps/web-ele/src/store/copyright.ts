import { ref } from 'vue';

import { defineStore } from 'pinia';

import { getCopyright } from '#/api/system/copyright';

export const useCopyrightStore = defineStore('copyright', () => {
  const copyright = ref<any>(null);
  const loading = ref(false);
  const clientType = ref<null | string>(null);
  let pending: null | Promise<any> = null;

  async function ensureFetched(type: string) {
    if (clientType.value === type && copyright.value) return copyright.value;
    if (pending) return pending;
    loading.value = true;
    clientType.value = type;
    pending = getCopyright(type)
      .then((res) => {
        copyright.value = res;
        return res;
      })
      .finally(() => {
        loading.value = false;
        pending = null;
      });
    return pending;
  }

  function $reset() {
    copyright.value = null;
    loading.value = false;
    clientType.value = null;
    pending = null;
  }

  return { copyright, loading, ensureFetched, $reset };
});
