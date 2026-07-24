<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { getMedia, getPlatformConfigs, savePlatformConfig } from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdPlatformConfig' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const keyword = ref('');
const loading = ref(false);
const total = ref(0);
const current = ref(1);
const size = ref(20);
const drawer = ref(false);
const saving = ref(false);
const form = reactive<any>({});

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getPlatformConfigs({
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
  Object.assign(form, row || { mediaId: mediaId.value, status: 'ACTIVE' });
  drawer.value = true;
}

async function submit() {
  if (!form.configKey || !form.configValue) {
    ElMessage.warning('请填写配置键和值');
    return;
  }
  saving.value = true;
  try {
    await savePlatformConfig({ ...form, mediaId: mediaId.value });
    ElMessage.success('配置已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

watch(mediaId, () => {
  current.value = 1;
  void load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <el-alert
      class="mb-4"
      :closable="false"
      show-icon
      title="回传目标配置"
      type="info"
    >
      创建 <code>callback.endpoint.partner-a</code> 一类的自定义配置键，任务填入
      <code>partner-a</code> 即可使用对应 HTTP 回传目标；目标主机仍受服务端
      allowlist 限制。
    </el-alert>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="配置键或说明"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:config:write'"
          type="primary"
          @click="open()"
        >
          新增配置
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="配置键" min-width="180" prop="configKey">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="配置值"
          min-width="300"
          prop="configValue"
          show-overflow-tooltip
        />
        <el-table-column label="说明" min-width="180" prop="description" />
        <el-table-column label="状态" prop="status" width="100" />
        <el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:config:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">暂无广告平台配置</div>
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
      :title="form.id ? '编辑配置' : '新增配置'"
      size="480px"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="配置键" required>
          <el-select
            v-model="form.configKey"
            allow-create
            class="w-full"
            filterable
            :disabled="Boolean(form.id)"
          >
            <el-option label="API 域名" value="API_DOMAIN" />
            <el-option label="点击路径" value="CLICK_PATH" />
            <el-option label="曝光路径" value="IMPRESSION_PATH" />
            <el-option label="事件字段 JSON" value="EVENT_FIELDS" />
            <el-option label="上游参数 JSON" value="UPSTREAM_PARAMS" />
            <el-option label="下游参数 JSON" value="DOWNSTREAM_PARAMS" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.configValue" :rows="5" type="textarea" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
