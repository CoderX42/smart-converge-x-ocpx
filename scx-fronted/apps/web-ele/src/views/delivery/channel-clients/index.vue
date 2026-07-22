<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { computed, reactive, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createChannelClient,
  getChannelClients,
  getChannelCredentials,
  getMedia,
  revokeChannelCredential,
  rotateChannelCredential,
  updateChannelClient,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdChannelClients' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const keyword = ref('');
const status = ref('');
const rows = ref<Record<string, any>[]>([]);
const credentials = ref<Record<string, any>[]>([]);
const selected = ref<Record<string, any>>();
const loading = ref(false);
const saving = ref(false);
const credentialLoading = ref(false);
const drawer = ref(false);
const credentialDialog = ref(false);
const secretDialog = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const issued = ref<Record<string, any>>();
const form = reactive({
  id: '',
  sourceCode: '',
  maxQps: 2000,
  dynamicCallbackEnabled: false,
  callbackAllowedHostsText: '',
  status: 'ACTIVE',
});

const drawerTitle = computed(() =>
  form.id ? '编辑渠道客户端' : '创建渠道客户端',
);

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getChannelClients({
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

function hosts(value?: string) {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function open(row?: Record<string, any>) {
  Object.assign(form, {
    id: row?.id || '',
    sourceCode: row?.sourceCode || '',
    maxQps: row?.maxQps || 2000,
    dynamicCallbackEnabled: Boolean(row?.dynamicCallbackEnabled),
    callbackAllowedHostsText: hosts(row?.callbackAllowedHosts).join('\n'),
    status: row?.status || 'ACTIVE',
  });
  drawer.value = true;
}

function requestBody() {
  return {
    mediaId: mediaId.value,
    sourceCode: form.sourceCode,
    maxQps: form.maxQps,
    dynamicCallbackEnabled: form.dynamicCallbackEnabled,
    callbackAllowedHosts: form.callbackAllowedHostsText
      .split(/[\n,]/)
      .map((item) => item.trim())
      .filter(Boolean),
    status: form.status,
  };
}

async function submit() {
  if (!form.id && !form.sourceCode.trim()) {
    ElMessage.warning('请填写渠道来源编码');
    return;
  }
  if (form.dynamicCallbackEnabled && !form.callbackAllowedHostsText.trim()) {
    ElMessage.warning('启用动态回调时必须配置精确域名白名单');
    return;
  }
  saving.value = true;
  try {
    const body = requestBody();
    if (form.id) {
      const { sourceCode: _sourceCode, ...updateBody } = body;
      await updateChannelClient(form.id, updateBody);
      ElMessage.success('渠道客户端已更新');
    } else {
      const { status: _status, ...createBody } = body;
      issued.value = await createChannelClient(createBody);
      secretDialog.value = true;
    }
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function viewCredentials(row: Record<string, any>) {
  selected.value = row;
  credentialDialog.value = true;
  credentialLoading.value = true;
  try {
    credentials.value = await getChannelCredentials(row.id, mediaId.value);
  } finally {
    credentialLoading.value = false;
  }
}

async function rotate(row: Record<string, any>) {
  await ElMessageBox.confirm(
    '将立即签发新密钥，旧密钥在 24 小时后失效。新密钥只展示一次。',
    '确认轮换密钥',
    { confirmButtonText: '确认轮换', type: 'warning' },
  );
  issued.value = await rotateChannelCredential(row.id, mediaId.value);
  secretDialog.value = true;
  if (credentialDialog.value) await viewCredentials(row);
  await load();
}

async function revoke(item: Record<string, any>) {
  if (!selected.value) return;
  await ElMessageBox.confirm(
    `吊销 ${item.keyVersion} 后，使用该版本签名的请求将立即失败。`,
    '确认吊销旧密钥',
    { confirmButtonText: '确认吊销', type: 'error' },
  );
  await revokeChannelCredential(
    selected.value.id,
    item.keyVersion,
    mediaId.value,
  );
  ElMessage.success('旧密钥已吊销');
  await viewCredentials(selected.value);
}

async function copyValue(value?: string) {
  if (!value) return;
  await navigator.clipboard.writeText(value);
  ElMessage.success('已复制，请立即保存到安全位置');
}

function clearIssued() {
  issued.value = undefined;
}

watch([mediaId, status], async () => {
  current.value = 1;
  await load();
});

bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control">
        <div class="mvp-scope-label mb-2 text-xs">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full" aria-label="媒体安全范围">
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
            placeholder="Client ID 或来源编码"
            style="width: 260px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 140px"
          >
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:channel-client:write'"
          type="primary"
          @click="open()"
        >
          创建渠道客户端
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="Client ID" min-width="230">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.clientId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源编码" prop="sourceCode" min-width="150" />
        <el-table-column
          label="当前版本"
          prop="currentKeyVersion"
          width="110"
        />
        <el-table-column label="QPS 配额" prop="maxQps" width="110" />
        <el-table-column label="动态回调" width="120">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.dynamicCallbackEnabled ? 'success' : 'info'"
            >
              {{ row.dynamicCallbackEnabled ? '已启用' : '未启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" min-width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewCredentials(row)">
              版本记录
            </el-button>
            <el-button
              v-access:code="'ad:channel-client:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ad:channel-client:rotate'"
              :disabled="row.status !== 'ACTIVE'"
              link
              type="warning"
              @click="rotate(row)"
            >
              轮换
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">当前媒体尚未创建渠道接入凭据</div>
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

    <el-drawer v-model="drawer" :title="drawerTitle" size="500px">
      <el-form label-position="top" :model="form">
        <el-form-item label="渠道来源编码" required>
          <el-input
            v-model="form.sourceCode"
            :disabled="Boolean(form.id)"
            maxlength="64"
          />
          <div class="text-muted-foreground mt-1 text-xs">
            创建后不可修改，将作为事件 source 的可信来源。
          </div>
        </el-form-item>
        <el-form-item label="每秒请求配额" required>
          <el-input-number
            v-model="form.maxQps"
            :min="1"
            :max="20000"
            class="w-full"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="动态回调">
          <el-switch v-model="form.dynamicCallbackEnabled" />
        </el-form-item>
        <el-form-item label="动态回调精确域名白名单">
          <el-input
            v-model="form.callbackAllowedHostsText"
            type="textarea"
            :rows="5"
            placeholder="每行一个域名，例如 callback.partner.example"
          />
          <div class="text-muted-foreground mt-1 text-xs">
            不支持通配符、IP、localhost；启用动态回调时至少填写一个域名。
          </div>
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-select v-model="form.status" class="w-full">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">
          {{ form.id ? '保存' : '创建并签发密钥' }}
        </el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="secretDialog"
      title="一次性渠道密钥"
      width="620px"
      :close-on-click-modal="false"
      @closed="clearIssued"
    >
      <el-alert
        class="mb-4"
        :closable="false"
        show-icon
        title="关闭后无法再次查看。请立即保存到渠道方的密钥管理系统。"
        type="warning"
      />
      <el-descriptions v-if="issued" :column="1" border>
        <el-descriptions-item label="Client ID">
          <span class="mvp-code">{{ issued.clientId }}</span>
          <el-button link type="primary" @click="copyValue(issued.clientId)">
            复制
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="Key Version">
          <span class="mvp-code">{{ issued.keyVersion }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Secret">
          <div class="flex items-center gap-2">
            <el-input :model-value="issued.secret" readonly type="password" />
            <el-button type="primary" @click="copyValue(issued.secret)">
              复制密钥
            </el-button>
          </div>
        </el-descriptions-item>
        <el-descriptions-item
          v-if="issued.previousKeyValidUntil"
          label="旧密钥有效至"
        >
          {{ issued.previousKeyValidUntil }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="secretDialog = false">
          我已安全保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="credentialDialog" title="密钥版本记录" width="720px">
      <el-table v-loading="credentialLoading" :data="credentials">
        <el-table-column label="版本" prop="keyVersion" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.current ? '当前' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" prop="validFrom" min-width="175" />
        <el-table-column label="失效时间" prop="validUntil" min-width="175" />
        <el-table-column align="right" label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-if="!row.current && row.status === 'ACTIVE'"
              v-access:code="'ad:channel-client:rotate'"
              link
              type="danger"
              @click="revoke(row)"
            >
              吊销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </main>
</template>
