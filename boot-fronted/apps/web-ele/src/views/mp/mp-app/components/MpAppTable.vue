<script lang="ts" setup>
import { Delete, Edit } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj } from '#/api/open/open-platform-app';

interface MpAppItem {
  appId: string;
  appName: string;
  appSecret: string;
  aesKey: string;
  token: string;
  createTime: string;
  [key: string]: any;
}

defineProps<{
  data: MpAppItem[];
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: 'edit', row: MpAppItem): void;
  (e: 'initPage'): void;
}>();

const handleEdit = (row: any) => {
  emit('edit', row);
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('此操作将删除该公众号，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        emit('initPage');
      })
      .catch(() => {});
  });
};
</script>

<template>
  <ElTable v-loading="loading" :data="data" border>
    <ElTableColumn prop="appName" label="应用名称" />
    <ElTableColumn prop="appId" label="应用ID" />
    <ElTableColumn prop="appSecret" label="秘钥" />
    <ElTableColumn prop="aesKey" label="消息加解密密钥" />
    <ElTableColumn prop="token" label="令牌(Token)" />
    <ElTableColumn prop="createTime" label="创建时间" />
    <ElTableColumn label="操作" width="200" align="center" fixed="right">
      <template #default="scope">
        <ElButton
          link
          type="primary"
          @click="handleEdit(scope.row)"
          v-access:code="'open:openPlatformApp:edit'"
          :icon="Edit"
        >
          修改
        </ElButton>
        <ElButton
          link
          type="danger"
          @click="handleDelete(scope.row.appId)"
          v-access:code="'open:openPlatformApp:del'"
          :icon="Delete"
        >
          删除
        </ElButton>
      </template>
    </ElTableColumn>
  </ElTable>
</template>
