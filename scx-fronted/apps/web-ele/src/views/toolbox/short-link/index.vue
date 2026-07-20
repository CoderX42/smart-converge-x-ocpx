<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  convertDpShortLink,
  getMedia,
  getShortLinks,
  saveShortLink,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdToolboxShortLink' });
const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const keyword = ref('');
const status = ref('');
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
    const result = await getShortLinks({
      current: current.value,
      keyword: keyword.value,
      mediaId: mediaId.value,
      size: size.value,
      status: status.value,
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
    row || { linkType: 'GENERAL', mediaId: mediaId.value, status: 'ACTIVE' },
  );
  drawer.value = true;
}

function isDp() {
  return form.linkType === 'TAOBAO_DP' || form.linkType === 'JD_DP';
}

async function normalizeDp() {
  if (!form.sourceUrl) {
    ElMessage.warning('请先填写 DP 原始链接');
    return;
  }
  const result = await convertDpShortLink({
    linkType: form.linkType,
    sourceUrl: form.sourceUrl,
  });
  form.targetUrl = result.targetUrl;
  ElMessage.success('DP 链接已规范化');
}

async function submit() {
  if (!form.name || (!form.targetUrl && !form.sourceUrl)) {
    ElMessage.warning('请填写名称和跳转地址');
    return;
  }
  saving.value = true;
  try {
    await saveShortLink({ ...form, mediaId: mediaId.value });
    ElMessage.success('短链已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

watch([mediaId, status], () => {
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
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称或编码"
            style="width: 220px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="状态"
            style="width: 120px"
          >
            <el-option label="启用" value="ACTIVE" /><el-option
              label="禁用"
              value="DISABLED"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:short-link:write'"
          type="primary"
          @click="open()"
        >
          新建短链
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="短码" min-width="130">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" min-width="150" />
        <el-table-column label="类型" prop="linkType" width="120" />
        <el-table-column
          label="跳转地址"
          prop="targetUrl"
          min-width="280"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:short-link:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">暂无短链</div>
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
      :title="form.id ? '编辑短链' : '新建短链'"
      size="460px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.linkType" class="w-full">
            <el-option label="通用短链" value="GENERAL" />
            <el-option label="淘宝 DP" value="TAOBAO_DP" />
            <el-option label="京东 DP" value="JD_DP" />
          </el-select>
        </el-form-item>
        <template v-if="isDp()">
          <el-form-item label="DP 原始链接" required>
            <el-input
              v-model="form.sourceUrl"
              placeholder="https://uland.taobao.com/... 或 https://union-click.jd.com/..."
              type="textarea"
              :rows="3"
            />
          </el-form-item>
          <el-button plain type="primary" @click="normalizeDp">
            规范化 DP 链接
          </el-button>
          <el-form-item class="mt-4" label="规范化跳转地址">
            <el-input
              v-model="form.targetUrl"
              readonly
              type="textarea"
              :rows="3"
            />
          </el-form-item>
          <p class="mvp-hint">
            转换只校验并规范化 URL，不调用第三方推广位或换链 API。
          </p>
        </template>
        <el-form-item v-else label="跳转地址" required>
          <el-input
            v-model="form.targetUrl"
            placeholder="http://localhost:8080/..."
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ACTIVE">启用</el-radio-button
            ><el-radio-button label="DISABLED">禁用</el-radio-button>
          </el-radio-group>
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
