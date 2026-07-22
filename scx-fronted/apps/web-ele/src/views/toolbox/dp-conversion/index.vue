<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { convertDpShortLink } from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdToolboxDpConversion' });

type Platform = 'JD_DP' | 'TAOBAO_DP';
const tabs = [
  { label: '淘宝 DP', value: 'TAOBAO_DP' },
  { label: '京东 DP', value: 'JD_DP' },
];

const current = ref<Platform>('TAOBAO_DP');
const form = reactive<
  Record<Platform, { sourceUrl: string; targetUrl: string }>
>({
  TAOBAO_DP: { sourceUrl: '', targetUrl: '' },
  JD_DP: { sourceUrl: '', targetUrl: '' },
});
const loading = reactive<Record<Platform, boolean>>({
  TAOBAO_DP: false,
  JD_DP: false,
});

function setPlatform(value: string) {
  if (value === 'TAOBAO_DP' || value === 'JD_DP') {
    current.value = value;
  }
}

async function normalize(platform: Platform) {
  const row = form[platform];
  if (!row.sourceUrl) {
    ElMessage.warning('请先填写 DP 原始链接');
    return;
  }
  loading[platform] = true;
  try {
    const result = await convertDpShortLink({
      linkType: platform,
      sourceUrl: row.sourceUrl,
    });
    row.targetUrl = result.targetUrl;
    ElMessage.success('DP 链接已规范化');
  } finally {
    loading[platform] = false;
  }
}

function reset(platform: Platform) {
  form[platform] = { sourceUrl: '', targetUrl: '' };
}
</script>
<template>
  <main class="mvp-page">
    <el-card class="mvp-card" shadow="never">
      <el-tabs model-value="TAOBAO_DP" @tab-change="setPlatform">
        <el-tab-pane
          v-for="item in tabs"
          :key="item.value"
          :label="item.label"
          :name="item.value"
        >
          <template #label>
            <span>{{ item.label }}</span>
          </template>
          <div class="mvp-toolbar">
            <el-form label-position="top">
              <el-form-item label="DP 原始链接">
                <el-input
                  v-model="form[current].sourceUrl"
                  placeholder="https://uland.taobao.com/... 或 https://union-click.jd.com/..."
                  type="textarea"
                  :rows="4"
                />
              </el-form-item>
              <el-form-item label="规范化结果">
                <el-input
                  v-model="form[current].targetUrl"
                  readonly
                  type="textarea"
                  :rows="4"
                />
              </el-form-item>
            </el-form>
            <div class="flex gap-2">
              <el-button plain type="default" @click="reset(current)">
                清空
              </el-button>
              <el-button
                :loading="loading[current]"
                type="primary"
                @click="normalize(current)"
              >
                规范化 DP 链接
              </el-button>
            </div>
          </div>
          <p class="mvp-hint mt-3">
            工具仅做 URL 规范化，不会调用第三方商品 API 或改写推广位。
          </p>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </main>
</template>
