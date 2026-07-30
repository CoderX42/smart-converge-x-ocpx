<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ElMessage } from 'element-plus';

import { getMaster, getMedia, saveMaster } from '#/api/ad';

import { resolveMediaId, withMediaContext } from '../components/media-context';

import '../components/mvp-page.css';

defineOptions({ name: 'AdProducts' });

const media = ref<Media[]>([]);
const route = useRoute();
const router = useRouter();
const mediaId = ref('');
const keyword = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const partners = ref<any[]>([]);
const form = reactive<any>({});

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = resolveMediaId(media.value, route.query.mediaId);
}

async function loadOptions() {
  if (!mediaId.value) return;
  const partnerResult = await getMaster('partners', {
    current: 1,
    size: 200,
    mediaId: mediaId.value,
    status: 'ACTIVE',
  });
  partners.value = partnerResult.records || [];
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getMaster('products', {
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
      code: `PRODUCT-${Date.now()}`,
      mediaId: mediaId.value,
      platform: 'ANDROID',
      status: 'ACTIVE',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.name || !form.code || !form.partnerId) {
    ElMessage.warning('请填写名称、编码并选择归属合作方');
    return;
  }
  saving.value = true;
  try {
    const payload = { ...form, mediaId: mediaId.value };
    delete payload.customerId;
    await saveMaster('products', payload);
    ElMessage.success('产品配置已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function goToPartners() {
  drawer.value = false;
  void router.push({
    path: '/commercial/catalog',
    query: { ...route.query, mediaId: mediaId.value, tab: 'partners' },
  });
}

watch(mediaId, async () => {
  current.value = 1;
  await router.replace({
    query: withMediaContext(route.query, mediaId.value),
  });
  await Promise.all([load(), loadOptions()]);
});

bootstrap().then(() => Promise.all([load(), loadOptions()]));
</script>

<template>
  <main class="mvp-page">
    <el-alert
      v-if="media.length > 1 && !mediaId"
      class="mb-4"
      :closable="false"
      show-icon
      title="请选择媒体范围后继续"
      type="info"
    />
    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-select
            v-model="mediaId"
            filterable
            placeholder="选择媒体主体"
            style="width: 220px"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="`${item.name} · ${item.code}`"
              :value="item.id"
            />
          </el-select>
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
          :disabled="!mediaId"
          type="primary"
          @click="open()"
        >
          新增产品
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="编码" min-width="170">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" min-width="180" />
        <el-table-column label="平台" prop="platform" width="120" />
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
          <div class="mvp-empty">当前范围尚无产品数据</div>
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
      :title="form.id ? '编辑产品' : '新增产品'"
      size="480px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="产品名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="唯一编码" required>
          <el-input v-model="form.code" :disabled="Boolean(form.id)" />
        </el-form-item>
        <el-form-item label="归属合作方" required>
          <el-select v-model="form.partnerId" class="w-full" filterable>
            <el-option
              v-for="item in partners"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-button class="mt-2" link type="primary" @click="goToPartners">
            没有可选合作方？前往当前媒体的合作方配置
          </el-button>
        </el-form-item>
        <el-form-item label="产品平台" required>
          <el-select v-model="form.platform" class="w-full">
            <el-option label="Android" value="ANDROID" />
            <el-option label="iOS" value="IOS" />
            <el-option label="Web/其他" value="OTHER" />
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
