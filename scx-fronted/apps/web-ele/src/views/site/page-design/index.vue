<script setup lang="ts">
import { ref } from 'vue';

import { Page } from '@boot/common-ui';

import {
  ElButton,
  ElCard,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/site/page-design';

const loading = ref(false);
const rows = ref<any[]>([]);
const query = ref({ pageCode: '', pageName: '' });

async function loadData() {
  loading.value = true;
  try {
    const result: any = await getPage(query.value);
    rows.value = result?.records || result?.data?.records || [];
  } finally {
    loading.value = false;
  }
}

loadData();
</script>

<template>
  <Page
    title="官网页面装修"
    description="管理公司官网、落地页和动态页面的装修配置"
  >
    <ElCard>
      <div class="mb-4 flex gap-3">
        <ElInput
          v-model="query.pageCode"
          clearable
          placeholder="页面编码"
          style="width: 220px"
        />
        <ElInput
          v-model="query.pageName"
          clearable
          placeholder="页面名称"
          style="width: 220px"
        />
        <ElButton type="primary" @click="loadData">查询</ElButton>
      </div>
      <ElTable v-loading="loading" :data="rows" row-key="id">
        <ElTableColumn prop="pageCode" label="页面编码" min-width="140" />
        <ElTableColumn prop="pageName" label="页面名称" min-width="160" />
        <ElTableColumn prop="pageType" label="页面类型" width="120" />
        <ElTableColumn prop="platform" label="平台" width="100" />
        <ElTableColumn prop="routePath" label="访问路径" min-width="180" />
        <ElTableColumn prop="status" label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="updateTime" label="更新时间" min-width="170" />
      </ElTable>
    </ElCard>
  </Page>
</template>
