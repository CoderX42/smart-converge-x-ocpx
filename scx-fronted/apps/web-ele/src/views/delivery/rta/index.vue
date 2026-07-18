<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeRtaEndpointStatus,
  checkRtaEndpoint,
  getMedia,
  getRtaDecisionLogs,
  getRtaEndpoints,
  saveRtaEndpoint,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdRtaEndpoint' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const logDrawer = ref(false);
const current = ref(1);
const size = ref(10);
const total = ref(0);
const keyword = ref('');
const status = ref('');
const form = reactive<any>({});
const decisionLogs = ref<any[]>([]);
const selectedEndpoint = ref<any>();

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getRtaEndpoints({
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
    row || {
      code: `RTA-${Date.now()}`,
      endpointUrl: 'http://localhost:8080/sandbox/rta',
      mediaId: mediaId.value,
      status: 'DRAFT',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.name || !form.code || !form.endpointUrl) {
    ElMessage.warning('请完整填写名称、编码和沙箱地址');
    return;
  }
  saving.value = true;
  try {
    await saveRtaEndpoint({ ...form, mediaId: mediaId.value });
    ElMessage.success('RTA 配置已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function changeStatus(row: any, next: string) {
  await ElMessageBox.confirm(`确认将 RTA 配置调整为 ${next}？`, '状态变更', {
    type: 'warning',
  });
  await changeRtaEndpointStatus(row.id, { status: next, version: row.version });
  ElMessage.success('状态已更新');
  await load();
}

async function healthCheck(row: any) {
  const updated = await checkRtaEndpoint(row.id);
  ElMessage.info(`检测结果：${updated.lastHealthStatus}`);
  await load();
}

async function openDecisionLogs(row: any) {
  selectedEndpoint.value = row;
  const result = await getRtaDecisionLogs(row.id, {
    current: 1,
    size: 30,
    mediaId: mediaId.value,
  });
  decisionLogs.value = result.records || [];
  logDrawer.value = true;
}

watch([mediaId, status], () => {
  current.value = 1;
  void load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <header class="mvp-hero">
      <div>
        <div class="mvp-kicker">P1 / Sandbox RTA</div>
        <h1 class="mvp-title">RTA 配置中心</h1>
        <p class="mvp-subtitle">
          后置 RTA
          只允许当前媒体范围内的沙箱端点；任务启用时会校验端点状态，健康检查不会暴露内部错误细节。
        </p>
      </div>
      <div class="mvp-control">
        <div class="mb-2 text-xs text-white/50">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>
    </header>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称或编码"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option
              v-for="item in ['DRAFT', 'ACTIVE', 'DISABLED']"
              :key="item"
              :value="item"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:rta:write'"
          type="primary"
          @click="open()"
        >
          创建 RTA 配置
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="RTA 配置" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <div class="mvp-code mt-1">{{ row.code }}</div>
          </template>
        </el-table-column>
        <el-table-column
          label="RTA 标识"
          prop="rtaIdentifier"
          min-width="150"
        />
        <el-table-column label="端点地址" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mvp-code">{{ row.endpointUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近检测" min-width="180">
          <template #default="{ row }">
            {{ row.lastHealthStatus || '未检测' }} ·
            {{ row.lastHealthCheckedAt || '—' }}
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="310">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'ad:rta:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-access:code="'ad:rta:write'"
              link
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >
              启用
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-access:code="'ad:rta:write'"
              link
              type="warning"
              @click="changeStatus(row, 'DISABLED')"
            >
              停用
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-access:code="'ad:rta:write'"
              link
              @click="healthCheck(row)"
            >
              检测
            </el-button>
            <el-button link type="primary" @click="openDecisionLogs(row)">
              运行记录
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">当前媒体范围内暂无 RTA 配置</div>
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
      :title="form.id ? '编辑 RTA 配置' : '创建 RTA 配置'"
      size="520px"
    >
      <el-form :model="form" label-position="top">
        <div class="grid grid-cols-2 gap-x-4">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" /> </el-form-item
          ><el-form-item label="唯一编码" required>
            <el-input v-model="form.code" :disabled="Boolean(form.id)" />
          </el-form-item>
        </div>
        <el-form-item label="RTA 标识">
          <el-input
            v-model="form.rtaIdentifier"
            placeholder="可选的外部 rtaid"
          />
        </el-form-item>
        <el-form-item label="沙箱端点地址" required>
          <el-input v-model="form.endpointUrl" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" :rows="3" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button
        ><el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>

    <el-drawer
      v-model="logDrawer"
      :title="`${selectedEndpoint?.name || 'RTA'} · 运行记录`"
      size="680px"
    >
      <el-table :data="decisionLogs" row-key="id" size="small">
        <el-table-column label="请求时间" prop="requestedAt" min-width="170" />
        <el-table-column label="决策" width="88">
          <template #default="{ row }">
            <el-tag
              :type="row.decision === 'ALLOW' ? 'success' : 'danger'"
              effect="plain"
            >
              {{ row.decision }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行状态" prop="status" width="100" />
        <el-table-column label="原因" prop="reasonCode" min-width="130" />
        <el-table-column label="耗时" width="80">
          <template #default="{ row }"> {{ row.latencyMs }} ms </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">尚未收到 RTA 决策请求</div>
        </template>
      </el-table>
    </el-drawer>
  </main>
</template>
