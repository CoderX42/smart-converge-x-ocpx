<script lang="ts" setup>
import { Delete, Edit } from '@element-plus/icons-vue';
import {
  ElButton,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { convertToFormal, delObj } from '#/api/miniapp/wxapp';

interface WxappItem {
  appId: string;
  appName: string;
  appSecret: string;
  miniCodeUrl?: string;
  createTime: string;
  betaStatus: string; // 试用状态：0-否，1-是
  [key: string]: any;
}

const props = defineProps<{
  data: WxappItem[];
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: 'edit', row: WxappItem): void;
  (e: 'initPage'): void;
}>();

const handleEdit = (row: any) => {
  emit('edit', row);
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('此操作将删除该微信应用，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id).then(() => {
      emit('initPage');
    });
  });
};

const handleConvert = (row: WxappItem) => {
  ElMessageBox.confirm('确定要将该小程序转为正式版吗？', '转正确认', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await convertToFormal(row.appId);
      ElMessage.success('转正成功');
      emit('initPage');
    } catch {
      ElMessage.error('转正失败');
    }
  });
};
</script>

<template>
  <ElTable v-loading="loading" :data="data" border>
    <ElTableColumn prop="appName" label="应用名称">
      <template #default="scope">
        {{ scope.row.appName }}
        <ElTag :type="scope.row.betaStatus === '1' ? 'warning' : 'success'">
          {{ scope.row.betaStatus === '1' ? '试用' : '' }}
        </ElTag>
      </template>
    </ElTableColumn>
    <ElTableColumn prop="miniCodeUrl" label="应用图片">
      <template #default="scope">
        <ElImage
          v-if="scope.row.miniCodeUrl"
          style="width: 50px; height: 50px"
          :src="scope.row.miniCodeUrl"
          :preview-src-list="[scope.row.miniCodeUrl]"
          fit="cover"
          :preview-teleported="true"
        />
      </template>
    </ElTableColumn>
    <ElTableColumn prop="appId" label="应用ID" />
    <ElTableColumn prop="appSecret" label="秘钥" />
    <ElTableColumn prop="createTime" label="创建时间" />
    <ElTableColumn label="操作" width="280" align="center" fixed="right">
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
          v-if="scope.row.betaStatus === 1"
          link
          type="success"
          @click="handleConvert(scope.row)"
          v-access:code="'open:openPlatformApp:convert'"
        >
          转正
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
