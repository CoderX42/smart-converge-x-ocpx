<script lang="ts" setup name="material">
import { defineAsyncComponent, ref } from 'vue';

import { ElTabPane, ElTabs } from 'element-plus';

const ImageTab = defineAsyncComponent(() => import('./image-tab/index.vue'));
const active = ref('1');
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <ElTabs v-model="active" class="demo-tabs">
        <ElTabPane label="图片" name="1">
          <ImageTab />
        </ElTabPane>
        <!-- <el-tab-pane label="视频" name="2">暂未开发</el-tab-pane> -->
      </ElTabs>
    </div>
  </div>
</template>
