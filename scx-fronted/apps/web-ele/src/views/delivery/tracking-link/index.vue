<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeTrackingLinkStatus,
  getEventMappings,
  getMaster,
  getMedia,
  getTrackingLinkRevisions,
  getTrackingLinks,
  getTrackingLinkStatusHistory,
  saveEventMappings,
  saveTrackingLink,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdTrackingLink' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const partners = ref<any[]>([]);
const products = ref<any[]>([]);
const documents = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const mappingDrawer = ref(false);
const historyDrawer = ref(false);
const current = ref(1);
const size = ref(10);
const total = ref(0);
const keyword = ref('');
const status = ref('');
const partnerFilter = ref('');
const productFilter = ref('');
const documentFilter = ref('');
const form = reactive<any>({});
const mappingLink = ref<any>();
const mappings = ref<any[]>([]);
const revisions = ref<any[]>([]);
const statusHistory = ref<any[]>([]);
const historyLink = ref<any>();

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function loadOptions() {
  if (!mediaId.value) return;
  const params = {
    current: 1,
    size: 200,
    mediaId: mediaId.value,
    status: 'ACTIVE',
  };
  const [p, product, doc] = await Promise.all([
    getMaster('partners', params),
    getMaster('products', params),
    getMaster('integration-documents', { ...params, status: undefined }),
  ]);
  partners.value = p.records || [];
  products.value = product.records || [];
  documents.value = doc.records || [];
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getTrackingLinks({
      current: current.value,
      size: size.value,
      mediaId: mediaId.value,
      keyword: keyword.value,
      status: status.value,
      partnerId: partnerFilter.value,
      productId: productFilter.value,
      documentId: documentFilter.value,
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
      mediaId: mediaId.value,
      code: `LINK-${Date.now()}`,
      deviceType: 'ANDROID',
      redirectEnabled: false,
      targetUrl: 'http://localhost/landing',
      status: 'DRAFT',
      templateText: 'click_id={{click_id}}&task_code={{task_code}}',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (
    !form.name ||
    !form.code ||
    !form.partnerId ||
    !form.productId ||
    !form.targetUrl
  ) {
    ElMessage.warning('请完整填写名称、编码、合作方、产品和目标地址');
    return;
  }
  saving.value = true;
  try {
    await saveTrackingLink({ ...form, mediaId: mediaId.value });
    ElMessage.success('追踪链接已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function changeStatus(row: any, next: string) {
  const { value } = await ElMessageBox.prompt(
    `请输入将链接变更为 ${next} 的原因`,
    '状态变更',
    {
      inputValidator: (text) => Boolean(text?.trim()) || '必须填写原因',
    },
  );
  await changeTrackingLinkStatus(row.id, {
    status: next,
    reason: value,
    version: row.version,
  });
  ElMessage.success('状态已更新');
  await load();
}

async function openMappings(row: any) {
  mappingLink.value = row;
  const result = await getEventMappings(row.id);
  mappings.value = result.map((item) => ({
    ...item,
  }));
  if (mappings.value.length === 0)
    mappings.value = [
      { internalEvent: 'ACTIVATE', externalEvent: 'activate', unitPrice: 0 },
    ];
  mappingDrawer.value = true;
}

async function submitMappings() {
  if (
    mappings.value.some((item) => !item.internalEvent || !item.externalEvent)
  ) {
    ElMessage.warning('事件映射不能留空');
    return;
  }
  await saveEventMappings(mappingLink.value.id, mappings.value);
  ElMessage.success('已生成新的事件映射版本');
  mappingDrawer.value = false;
  await load();
}

async function openHistory(row: any) {
  historyLink.value = row;
  const [revisionResult, statusResult] = await Promise.all([
    getTrackingLinkRevisions(row.id),
    getTrackingLinkStatusHistory(row.id),
  ]);
  revisions.value = revisionResult || [];
  statusHistory.value = statusResult || [];
  historyDrawer.value = true;
}

function redirectUrl(code: string) {
  return `${window.location.origin}/api/boot/ad/r/${encodeURIComponent(code)}`;
}

function verifyRedirect(row: any) {
  window.open(redirectUrl(row.code), '_blank', 'noopener,noreferrer');
}

watch(mediaId, async () => {
  current.value = 1;
  await Promise.all([load(), loadOptions()]);
});
bootstrap().then(() => Promise.all([load(), loadOptions()]));
</script>

<template>
  <main class="mvp-page">
    <header class="mvp-hero">
      <div>
        <div class="mvp-kicker">M2 / Versioned Links</div>
        <h1 class="mvp-title">追踪链接编排</h1>
        <p class="mvp-subtitle">
          把产品、合作方和受控文档组合成可版本化的事件入口。归档保留历史，启停不会改写已经发生的事实。
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
            placeholder="链接名称或编码"
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
              v-for="item in ['DRAFT', 'ACTIVE', 'DISABLED', 'ARCHIVED']"
              :key="item"
              :value="item"
            />
          </el-select>
          <el-select
            v-model="partnerFilter"
            clearable
            filterable
            placeholder="全部合作方"
            style="width: 170px"
          >
            <el-option
              v-for="item in partners"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="productFilter"
            clearable
            filterable
            placeholder="全部产品"
            style="width: 170px"
          >
            <el-option
              v-for="item in products"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="documentFilter"
            clearable
            filterable
            placeholder="全部接入文档"
            style="width: 170px"
          >
            <el-option
              v-for="item in documents"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:link:write'"
          type="primary"
          @click="open()"
        >
          创建追踪链接
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="链接" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <div class="mvp-code mt-1">{{ row.code }}</div>
          </template>
        </el-table-column>
        <el-table-column label="设备" prop="deviceType" width="110" />
        <el-table-column label="配置版本" width="110">
          <template #default="{ row }"> v{{ row.configVersion }} </template>
        </el-table-column>
        <el-table-column label="目标地址" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mvp-code">{{ row.targetUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="
                row.status === 'ACTIVE'
                  ? 'success'
                  : row.status === 'ARCHIVED'
                    ? 'info'
                    : 'warning'
              "
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="跳转验证" min-width="170">
          <template #default="{ row }">
            <el-link
              v-if="row.status === 'ACTIVE'"
              :href="redirectUrl(row.code)"
              rel="noopener noreferrer"
              target="_blank"
              type="primary"
            >
              /api/boot/ad/r/{{ row.code }}
            </el-link>
            <span v-else class="text-xs text-gray-400">启用后可验证</span>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="300">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'ad:link:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ad:link:write'"
              link
              @click="openMappings(row)"
            >
              事件映射
            </el-button>
            <el-button link @click="openHistory(row)">版本记录</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="primary"
              @click="verifyRedirect(row)"
            >
              验证跳转
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'DISABLED'"
              v-access:code="'ad:link:status'"
              link
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >
              启用
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-access:code="'ad:link:status'"
              link
              type="warning"
              @click="changeStatus(row, 'DISABLED')"
            >
              停用
            </el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ad:link:status'"
              link
              type="danger"
              @click="changeStatus(row, 'ARCHIVED')"
            >
              归档
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">尚未创建追踪链接</div>
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
      v-model="historyDrawer"
      :title="`链接版本记录 · ${historyLink?.name || ''}`"
      size="620px"
    >
      <h3 class="mb-3 text-base font-semibold">配置快照</h3>
      <el-table :data="revisions" size="small">
        <el-table-column label="版本" prop="configVersion" width="90">
          <template #default="{ row }">v{{ row.configVersion }}</template>
        </el-table-column>
        <el-table-column label="生成时间" min-width="180" prop="createTime" />
        <el-table-column label="快照" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mvp-code">{{ row.snapshotJson }}</span>
          </template>
        </el-table-column>
      </el-table>
      <h3 class="mb-3 mt-6 text-base font-semibold">状态流转</h3>
      <el-table :data="statusHistory" size="small">
        <el-table-column label="变更" min-width="160">
          <template #default="{ row }">
            {{ row.previousStatus }} → {{ row.nextStatus }}
          </template>
        </el-table-column>
        <el-table-column
          label="原因"
          min-width="180"
          prop="reason"
          show-overflow-tooltip
        />
        <el-table-column label="时间" min-width="180" prop="createTime" />
      </el-table>
    </el-drawer>

    <el-drawer
      v-model="drawer"
      :title="form.id ? '编辑追踪链接' : '创建追踪链接'"
      size="540px"
    >
      <el-form :model="form" label-position="top">
        <div class="grid grid-cols-2 gap-x-4">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="唯一编码" required>
            <el-input v-model="form.code" :disabled="Boolean(form.id)" />
          </el-form-item>
        </div>
        <div class="grid grid-cols-2 gap-x-4">
          <el-form-item label="合作方" required>
            <el-select v-model="form.partnerId" class="w-full" filterable>
              <el-option
                v-for="item in partners"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="产品" required>
            <el-select v-model="form.productId" class="w-full" filterable>
              <el-option
                v-for="item in products.filter(
                  (p) => !form.partnerId || p.partnerId === form.partnerId,
                )"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>
        <div class="grid grid-cols-2 gap-x-4">
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
          <el-form-item label="设备类型">
            <el-select v-model="form.deviceType" class="w-full">
              <el-option label="Android" value="ANDROID" /><el-option
                label="iOS"
                value="IOS"
              /><el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="沙箱目标地址" required>
          <el-input
            v-model="form.targetUrl"
            placeholder="http://localhost/landing"
          />
        </el-form-item>
        <el-form-item label="下载地址">
          <el-input
            v-model="form.downloadUrl"
            placeholder="仅允许 localhost、callback-mock、minio"
          />
        </el-form-item>
        <el-form-item label="参数模板">
          <el-input v-model="form.templateText" type="textarea" :rows="4" />
          <div class="mt-1 text-xs text-gray-400">
            允许 click_id、event_id、device_key_hash、task_code、timestamp
          </div>
        </el-form-item>
        <el-form-item label="重定向">
          <el-switch v-model="form.redirectEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="mappingDrawer" title="事件映射版本" size="500px">
      <el-alert
        class="mb-4"
        :closable="false"
        type="info"
        title="保存会追加新版本，不覆盖历史映射。"
      />
      <div
        v-for="(item, index) in mappings"
        :key="index"
        class="mb-3 grid grid-cols-[1fr_1fr_110px_120px_auto] gap-2"
      >
        <el-select v-model="item.internalEvent">
          <el-option
            v-for="event in ['CLICK', 'ACTIVATE', 'REGISTER', 'PAY']"
            :key="event"
            :value="event"
          />
        </el-select>
        <el-input v-model="item.externalEvent" placeholder="外部事件名" />
        <el-input-number
          v-model="item.unitPrice"
          :min="0"
          :precision="6"
          controls-position="right"
          placeholder="单价"
        />
        <el-input-number
          v-model="item.dailyLimit"
          :min="1"
          controls-position="right"
          placeholder="日限量"
        />
        <el-button text type="danger" @click="mappings.splice(index, 1)">
          移除
        </el-button>
      </div>
      <el-button
        class="w-full"
        plain
        @click="
          mappings.push({
            internalEvent: 'ACTIVATE',
            externalEvent: '',
            unitPrice: 0,
          })
        "
      >
        增加映射
      </el-button>
      <template #footer>
        <el-button @click="mappingDrawer = false">取消</el-button>
        <el-button type="primary" @click="submitMappings">
          保存新版本
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
