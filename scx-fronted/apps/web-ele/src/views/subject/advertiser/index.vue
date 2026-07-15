<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Page } from '@boot/common-ui';
import { ElAlert, ElButton, ElCard, ElTable, ElTableColumn, ElTag } from 'element-plus';

import {
  getAuthorizedAdvertisers,
  type Advertiser,
} from '#/api/subject/advertiser';

const loading = ref(false);
const advertisers = ref<Advertiser[]>([]);
const loadError = ref('');

async function loadAdvertisers() {
  loading.value = true;
  loadError.value = '';
  try {
    advertisers.value = await getAuthorizedAdvertisers();
  } catch {
    loadError.value = '广告主体加载失败，请确认当前账号已被授予主体范围。';
  } finally {
    loading.value = false;
  }
}

onMounted(loadAdvertisers);
</script>

<template>
  <Page title="广告主体" description="仅展示当前账号被授权访问的广告主体。">
    <ElCard>
      <template #header>
        <div class="flex items-center justify-between gap-4">
          <span class="font-medium">已授权主体</span>
          <ElButton :loading="loading" type="primary" @click="loadAdvertisers">
            刷新
          </ElButton>
        </div>
      </template>
      <ElAlert v-if="loadError" :closable="false" :title="loadError" type="error" class="mb-4" />
      <ElTable v-loading="loading" :data="advertisers" empty-text="当前账号暂无已授权广告主体">
        <ElTableColumn label="主体名称" min-width="220" prop="name" />
        <ElTableColumn label="主体编码" min-width="160" prop="code" />
        <ElTableColumn label="时区" min-width="160" prop="timezone" />
        <ElTableColumn label="币种" min-width="100" prop="currency" />
        <ElTableColumn label="状态" min-width="100">
          <template #default="{ row }">
            <ElTag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </Page>
</template>
