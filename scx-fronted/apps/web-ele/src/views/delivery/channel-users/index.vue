<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  getChannelUsers,
  getMaster,
  getMedia,
  saveChannelUser,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdChannelUser' });
const channelId = ref('');
const channels = ref<any[]>([]);
const current = ref(1);
const drawer = ref(false);
const form = reactive<any>({});
const keyword = ref('');
const loading = ref(false);
const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const saving = ref(false);
const size = ref(20);
const total = ref(0);
async function bootstrap() {
  const m = await getMedia();
  media.value = m.records || [];
  mediaId.value = media.value[0]?.id || '';
}
async function options() {
  if (!mediaId.value) return;
  const r = await getMaster('media-channels', {
    current: 1,
    size: 200,
    mediaId: mediaId.value,
    status: 'ACTIVE',
  });
  channels.value = r.records || [];
}
async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const r = await getChannelUsers({
      current: current.value,
      size: size.value,
      mediaId: mediaId.value,
      channelId: channelId.value,
      keyword: keyword.value,
    });
    rows.value = r.records || [];
    total.value = r.total || 0;
  } finally {
    loading.value = false;
  }
}
function open(row?: any) {
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(
    form,
    row || {
      mediaId: mediaId.value,
      channelId: channelId.value,
      status: 'ACTIVE',
    },
  );
  drawer.value = true;
}
async function submit() {
  if (!form.channelId || !form.account || !form.name) {
    ElMessage.warning('请填写渠道、账号和名称');
    return;
  }
  saving.value = true;
  try {
    await saveChannelUser({ ...form, mediaId: mediaId.value });
    ElMessage.success('渠道用户已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
watch([mediaId, channelId], async () => {
  current.value = 1;
  await Promise.all([options(), load()]);
});
bootstrap().then(async () => {
  await options();
  await load();
});
</script>
<template>
  <main class="mvp-page">
    <header class="mvp-hero">
      <div>
        <div class="mvp-kicker">M1 / Channel Operations</div>
        <h1 class="mvp-title">渠道用户</h1>
        <p class="mvp-subtitle">按媒体与渠道归属管理运营联系人和账户状态。</p>
      </div>
      <div class="mvp-control">
        <div class="mb-2 text-xs text-white/50">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="x in media"
            :key="x.id"
            :label="x.name"
            :value="x.id"
          />
        </el-select>
      </div>
    </header>
    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-select
            v-model="channelId"
            clearable
            placeholder="全部渠道"
            style="width: 180px"
          >
            <el-option
              v-for="x in channels"
              :key="x.id"
              :label="x.name"
              :value="x.id"
            /> </el-select
          ><el-input
            v-model="keyword"
            clearable
            placeholder="账号或名称"
            style="width: 190px"
            @keyup.enter="load"
          /><el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:channel-user:write'"
          type="primary"
          @click="open()"
        >
          新增渠道用户
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column
          label="账号"
          prop="account"
          min-width="160"
        /><el-table-column
          label="名称"
          prop="name"
          min-width="150"
        /><el-table-column
          label="电话"
          prop="phone"
          min-width="130"
        /><el-table-column
          label="邮箱"
          prop="email"
          min-width="180"
        /><el-table-column
          label="状态"
          prop="status"
          width="100"
        /><el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:channel-user:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table> </el-card
    ><el-drawer
      v-model="drawer"
      :title="form.id ? '编辑渠道用户' : '新增渠道用户'"
      size="460px"
    >
      <el-form label-position="top">
        <el-form-item label="媒体渠道" required>
          <el-select v-model="form.channelId" class="w-full">
            <el-option
              v-for="x in channels"
              :key="x.id"
              :label="x.name"
              :value="x.id"
            />
          </el-select> </el-form-item
        ><el-form-item label="账号" required>
          <el-input v-model="form.account" /> </el-form-item
        ><el-form-item label="名称" required>
          <el-input v-model="form.name" /> </el-form-item
        ><el-form-item label="电话">
          <el-input v-model="form.phone" /> </el-form-item
        ><el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item> </el-form
      ><template #footer>
        <el-button @click="drawer = false">取消</el-button
        ><el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
