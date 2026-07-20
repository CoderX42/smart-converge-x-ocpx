<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Check, RefreshRight } from '@element-plus/icons-vue';
import {
  ElButton,
  ElEmpty,
  ElIcon,
  ElImage,
  ElPagination,
  ElSkeleton,
  ElSkeletonItem,
} from 'element-plus';

import { getPage } from '#/api/system/material';

interface MaterialItem {
  id: string;
  name?: string;
  url: string;
}

interface MaterialPage {
  records?: MaterialItem[];
  total?: number;
}

const props = withDefaults(
  defineProps<{
    maxSelect?: number;
    mode?: 'dialog' | 'page';
    selectable?: boolean;
    shopId?: string;
  }>(),
  {
    maxSelect: 1,
    mode: 'page',
    selectable: false,
    shopId: '',
  },
);

const emit = defineEmits<{
  selectChange: [urls: string[]];
}>();

const loading = ref(false);
const loadError = ref('');
const selectedUrls = ref<string[]>([]);
const materials = ref<MaterialItem[]>([]);
const pagination = reactive({
  current: 1,
  size: 12,
  total: 0,
});

const selectionHint = computed(() => {
  if (!props.selectable) return `共 ${pagination.total} 张图片`;
  return props.maxSelect === 1
    ? '请选择 1 张图片作为头像'
    : `最多选择 ${props.maxSelect} 张图片`;
});

const loadMaterials = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const response = (await getPage({
      current: pagination.current,
      shopId: props.shopId || undefined,
      size: pagination.size,
      type: '1',
    })) as MaterialPage;
    materials.value = (response.records || []).filter((item) => item.url);
    pagination.total = Number(response.total || 0);
  } catch {
    materials.value = [];
    pagination.total = 0;
    loadError.value = '素材库加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
};

const toggleMaterial = (item: MaterialItem) => {
  if (!props.selectable) return;

  if (props.maxSelect === 1) {
    selectedUrls.value = [item.url];
  } else if (selectedUrls.value.includes(item.url)) {
    selectedUrls.value = selectedUrls.value.filter((url) => url !== item.url);
  } else if (selectedUrls.value.length < props.maxSelect) {
    selectedUrls.value = [...selectedUrls.value, item.url];
  }
  emit('selectChange', [...selectedUrls.value]);
};

const changePage = (page: number) => {
  pagination.current = page;
  loadMaterials();
};

const changePageSize = (size: number) => {
  pagination.current = 1;
  pagination.size = size;
  loadMaterials();
};

watch(
  () => props.shopId,
  () => {
    pagination.current = 1;
    loadMaterials();
  },
);

onMounted(loadMaterials);
</script>

<template>
  <section class="material-gallery" :class="`is-${mode}`">
    <header class="gallery-header">
      <div>
        <div class="gallery-title">图片素材</div>
        <div class="gallery-hint">{{ selectionHint }}</div>
      </div>
      <ElButton :icon="RefreshRight" :loading="loading" @click="loadMaterials">
        刷新
      </ElButton>
    </header>

    <ElSkeleton v-if="loading" :rows="0" animated>
      <template #template>
        <div class="material-grid">
          <div v-for="index in 8" :key="index" class="material-card">
            <ElSkeletonItem variant="image" class="skeleton-image" />
            <ElSkeletonItem variant="text" class="skeleton-text" />
          </div>
        </div>
      </template>
    </ElSkeleton>

    <div v-else-if="loadError" class="gallery-state">
      <ElEmpty :description="loadError">
        <ElButton type="primary" @click="loadMaterials">重新加载</ElButton>
      </ElEmpty>
    </div>

    <ElEmpty
      v-else-if="materials.length === 0"
      class="gallery-state"
      description="素材库中还没有图片"
    />

    <div v-else class="material-grid">
      <button
        v-for="item in materials"
        :key="item.id"
        type="button"
        class="material-card"
        :class="{
          'is-selectable': selectable,
          'is-selected': selectedUrls.includes(item.url),
        }"
        :aria-label="`${selectedUrls.includes(item.url) ? '取消选择' : '选择'}${item.name || '图片素材'}`"
        :aria-pressed="selectedUrls.includes(item.url)"
        @click="toggleMaterial(item)"
      >
        <ElImage class="material-image" :src="item.url" fit="cover" lazy />
        <span
          v-if="selectedUrls.includes(item.url)"
          class="selected-mark"
          aria-hidden="true"
        >
          <ElIcon><Check /></ElIcon>
        </span>
        <span class="material-name" :title="item.name || '未命名图片'">
          {{ item.name || '未命名图片' }}
        </span>
      </button>
    </div>

    <ElPagination
      v-if="pagination.total > pagination.size"
      class="gallery-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :current-page="pagination.current"
      :page-size="pagination.size"
      :page-sizes="[12, 24, 48]"
      :total="pagination.total"
      @current-change="changePage"
      @size-change="changePageSize"
    />
  </section>
</template>

<style lang="scss" scoped>
.material-gallery {
  min-height: 320px;
}

.gallery-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.gallery-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.gallery-hint {
  margin-top: 2px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(128px, 1fr));
  gap: 14px;
}

.material-card {
  position: relative;
  min-width: 0;
  padding: 0;
  overflow: hidden;
  font: inherit;
  color: inherit;
  text-align: left;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;

  &.is-selectable {
    cursor: pointer;
  }

  &.is-selectable:hover,
  &.is-selectable:focus-visible {
    outline: none;
    border-color: var(--el-color-primary-light-3);
    box-shadow: 0 8px 20px rgb(15 23 42 / 10%);
    transform: translateY(-2px);
  }

  &.is-selected {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
  }
}

.material-image,
.skeleton-image {
  display: block;
  width: 100%;
  height: 116px;
  background: var(--el-fill-color-light);
}

.material-name {
  display: block;
  padding: 9px 10px 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 18px;
  white-space: nowrap;
}

.selected-mark {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #fff;
  background: var(--el-color-primary);
  border: 2px solid #fff;
  border-radius: 50%;
}

.gallery-state {
  display: grid;
  place-items: center;
  min-height: 260px;
}

.gallery-pagination {
  justify-content: flex-end;
  margin-top: 20px;
}

.skeleton-text {
  width: calc(100% - 20px);
  margin: 12px 10px;
}

.is-dialog {
  max-height: 62vh;
  padding-right: 4px;
  overflow-y: auto;
}
</style>
