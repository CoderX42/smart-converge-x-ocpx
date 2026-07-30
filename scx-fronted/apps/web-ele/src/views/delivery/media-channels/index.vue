<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Delete, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteMediaChannel, getMaster, getMedia, saveMaster } from '#/api/ad';

import { resolveMediaId, withMediaContext } from '../components/media-context';
import { generateRandomBusinessCode } from '../components/random-code';

import '../components/mvp-page.css';

defineOptions({ name: 'AdMediaChannels' });

const media = ref<Media[]>([]);
const route = useRoute();
const router = useRouter();
const mediaId = ref('');
const keyword = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const drawer = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const documents = ref<any[]>([]);
const form = reactive<any>({});

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = resolveMediaId(media.value, route.query.mediaId);
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
      code: generateRandomBusinessCode('channel'),
      mediaId: mediaId.value,
      status: 'ACTIVE',
    },
  );
  drawer.value = true;
}

function replaceRandomCode() {
  if (form.id) return;
  form.code = generateRandomBusinessCode('channel');
}

async function submit() {
  if (!form.name || !form.code) {
    ElMessage.warning('请填写名称和编码');
    return;
  }
  saving.value = true;
  try {
    await saveMaster('media-channels', { ...form, mediaId: mediaId.value });
    ElMessage.success('投放渠道配置已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function removeCurrent() {
  if (!form.id || form.version === undefined || deleting.value) return;
  try {
    await ElMessageBox.confirm(
      `确定永久删除投放渠道“${form.name}”吗？存在渠道用户、媒体投放账户、监测任务或历史报表时将无法删除。`,
      '删除投放渠道',
      {
        cancelButtonText: '取消',
        confirmButtonText: '永久删除',
        type: 'error',
      },
    );
    deleting.value = true;
    await deleteMediaChannel(form.id, form.version);
    ElMessage.success('投放渠道已删除');
    drawer.value = false;
    if (rows.value.length === 1 && current.value > 1) current.value -= 1;
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      const failure = error as {
        message?: string;
        msg?: string;
        response?: { data?: { msg?: string } };
      };
      ElMessage.error(
        failure.response?.data?.msg ||
          failure.msg ||
          failure.message ||
          '投放渠道删除失败，请重试',
      );
    }
  } finally {
    deleting.value = false;
  }
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
    <header class="channel-heading">
      <div>
        <p>MEDIA DELIVERY · CONFIGURATION</p>
        <h1>投放渠道</h1>
        <span>
          定义媒体主体下的业务投放入口；媒体投放账户和监测任务均沿此层级归属。
        </span>
      </div>
      <div class="configuration-chain" aria-label="媒体配置链路">
        <span>媒体主体</span>
        <b>→</b>
        <strong>投放渠道</strong>
        <b>→</b>
        <span>媒体投放账户</span>
        <b>→</b>
        <span>广告位</span>
      </div>
    </header>

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
          新增投放渠道
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="编码" min-width="170">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" min-width="180" />
        <el-table-column label="关联配置" min-width="180">
          <template #default="{ row }">
            文档 {{ row.documentCount || 0 }} · 渠道用户
            {{ row.channelUserCount || 0 }}
          </template>
        </el-table-column>
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
          <div class="mvp-empty">当前媒体尚未配置投放渠道</div>
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
      :title="form.id ? '编辑投放渠道' : '新增投放渠道'"
      size="480px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="投放渠道名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="唯一编码" required>
          <el-input
            v-model="form.code"
            :disabled="Boolean(form.id)"
            maxlength="64"
          >
            <template v-if="!form.id" #append>
              <el-button :icon="Refresh" @click="replaceRandomCode">
                换一个
              </el-button>
            </template>
          </el-input>
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
        <div class="flex items-center justify-between">
          <el-button
            v-if="form.id"
            :icon="Delete"
            :loading="deleting"
            type="danger"
            plain
            @click="removeCurrent"
          >
            删除投放渠道
          </el-button>
          <span v-else></span>
          <div>
            <el-button :disabled="saving || deleting" @click="drawer = false">
              取消
            </el-button>
            <el-button
              :disabled="deleting"
              :loading="saving"
              type="primary"
              @click="submit"
            >
              保存
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.channel-heading {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.channel-heading p {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--mvp-accent);
  letter-spacing: 0.08em;
}

.channel-heading h1 {
  margin: 0 0 4px;
  font-size: clamp(24px, 3vw, 32px);
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.channel-heading > div > span {
  color: var(--mvp-muted);
}

.configuration-chain {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 14px;
  font-size: 12px;
  color: var(--mvp-muted);
  background: color-mix(in srgb, var(--el-bg-color) 92%, var(--mvp-accent));
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.configuration-chain b {
  font-weight: 500;
  opacity: 0.5;
}

.configuration-chain strong {
  color: var(--mvp-accent);
}

@media (max-width: 768px) {
  .channel-heading {
    flex-direction: column;
    align-items: stretch;
  }

  .configuration-chain {
    justify-content: flex-start;
  }
}
</style>
