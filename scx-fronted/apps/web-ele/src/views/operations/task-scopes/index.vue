<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteTaskScope,
  getMedia,
  getSystemUsers,
  getTaskScopes,
  getTrackingTasks,
  saveTaskScope,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdTaskScopes' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const users = ref<any[]>([]);
const tasks = ref<any[]>([]);
const rows = ref<any[]>([]);
const loading = ref(false);
const drawer = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const form = reactive<any>({});

async function bootstrap() {
  const [mediaResult, userResult] = await Promise.all([
    getMedia(),
    getSystemUsers(),
  ]);
  media.value = mediaResult.records || [];
  users.value = userResult || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const [scopeResult, taskResult] = await Promise.all([
      getTaskScopes({
        current: current.value,
        mediaId: mediaId.value,
        size: size.value,
      }),
      getTrackingTasks({ current: 1, mediaId: mediaId.value, size: 200 }),
    ]);
    rows.value = scopeResult.records || [];
    total.value = scopeResult.total || 0;
    tasks.value = taskResult.records || [];
  } finally {
    loading.value = false;
  }
}

function userName(userId: string) {
  const user = users.value.find((item) => item.id === userId);
  return user?.nickname || user?.username || userId;
}

function taskName(taskId: string) {
  const task = tasks.value.find((item) => item.id === taskId);
  return task ? `${task.name} · ${task.code}` : taskId;
}

function open(row?: any) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { mediaId: mediaId.value });
  drawer.value = true;
}

async function submit() {
  if (!form.userId || !form.taskId) {
    ElMessage.warning('请选择系统用户和监测任务');
    return;
  }
  await saveTaskScope({ ...form, mediaId: mediaId.value });
  ElMessage.success('任务数据范围已保存');
  drawer.value = false;
  await load();
}

async function revoke(row: any) {
  await ElMessageBox.confirm(
    '撤销后，该用户将按其余任务授权或媒体范围重新计算可见数据。',
    '确认撤销',
    {
      type: 'warning',
    },
  );
  await deleteTaskScope(row.id, mediaId.value);
  ElMessage.success('任务授权已撤销');
  await load();
}

watch(mediaId, () => {
  current.value = 1;
  load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control">
        <div class="mvp-scope-label mb-2 text-xs">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="text-sm text-gray-500">
          已授权 {{ total }} 条用户—任务关系
        </div>
        <el-button
          v-access:code="'ad:task-scope:write'"
          type="primary"
          @click="open()"
        >
          新增授权
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="系统用户" min-width="180">
          <template #default="{ row }">{{ userName(row.userId) }}</template>
        </el-table-column>
        <el-table-column label="监测任务" min-width="260">
          <template #default="{ row }">{{ taskName(row.taskId) }}</template>
        </el-table-column>
        <el-table-column label="授予时间" prop="createTime" min-width="180" />
        <el-table-column align="right" label="操作" width="140">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:task-scope:write'"
              link
              @click="open(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ad:task-scope:write'"
              link
              type="danger"
              @click="revoke(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawer"
      :title="form.id ? '编辑任务授权' : '新增任务授权'"
      size="480px"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="系统用户" required>
          <el-select v-model="form.userId" class="w-full" filterable>
            <el-option
              v-for="item in users"
              :key="item.id"
              :label="item.nickname || item.username"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="监测任务" required>
          <el-select v-model="form.taskId" class="w-full" filterable>
            <el-option
              v-for="item in tasks"
              :key="item.id"
              :label="`${item.name} · ${item.code}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-drawer>
  </main>
</template>
