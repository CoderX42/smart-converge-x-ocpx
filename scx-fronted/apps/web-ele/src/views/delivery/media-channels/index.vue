<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { getMaster, getMedia, saveMaster } from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdMediaChannels' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const keyword = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const documents = ref<any[]>([]);
const form = reactive<any>({});

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function loadOptions() {
  if (!mediaId.value) return;
  const result = await getMaster('integration-documents', {
    current: 1,
    mediaId: mediaId.value,
    size: 200,
  });
  documents.value = result.records || [];
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getMaster('media-channels', {
      current: current.value,
      keyword: keyword.value,
      mediaId: mediaId.value,
      size: size.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function open(row?: any) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      code: `CHANNEL-${Date.now()}`,
      mediaId: mediaId.value,
      status: 'ACTIVE',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.name || !form.code) {
    ElMessage.warning('请填写名称和编码');
    return;
  }
  saving.value = true;
  try {
    await saveMaster('media-channels', { ...form, mediaId: mediaId.value });
    ElMessage.success('媒体渠道配置已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

watch(mediaId, async () => {
  current.value = 1;
  await Promise.all([load(), loadOptions()]);
});
bootstrap().then(() => Promise.all([load(), loadOptions()]));
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
            :label="`${item.name} · ${item.code}`"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称或编码搜索"
            style="width: 260px"
            @keyup.enter="load"
          />
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:master:write'"
          type="primary"
          @click="open()"
        >
          新增渠道
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="编码" min-width="170">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" min-width="175" />
        <el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:master:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">当前范围尚无媒体渠道数据</div>
        </template>
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
      :title="form.id ? '编辑渠道' : '新增渠道'"
      size="480px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="渠道名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="唯一编码" required>
          <el-input v-model="form.code" :disabled="Boolean(form.id)" />
        </el-form-item>
        <el-form-item label="接入文档">
          <el-select v-model="form.documentId" class="w-full" clearable>
            <el-option
              v-for="item in documents"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="w-full">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit"
          >保存</el-button
        >
      </template>
    </el-drawer>
  </main>
</template>
