<script lang="ts" setup>
import type { Media } from '#/api/ad';
import type { Capability, MediaAccount, Placement } from '#/api/inventory';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { getMedia } from '#/api/ad';
import {
  changeMediaAccountStatus,
  getAdapterHealth,
  getCapability,
  getMediaAccounts,
  getPlacements,
  saveCapability,
  saveMediaAccount,
  savePlacement,
} from '#/api/inventory';

import '../business-domain.css';

defineOptions({ name: 'InventoryWorkspace' });

const route = useRoute();
const media = ref<Media[]>([]);
const mediaId = ref('');
const accounts = ref<MediaAccount[]>([]);
const mediaAccountId = ref('');
const accountRows = ref<MediaAccount[]>([]);
const placementRows = ref<Placement[]>([]);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const keyword = ref('');
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const capabilityDrawer = ref(false);
const editingType = ref<'account' | 'placement'>('account');
const form = reactive<Record<string, string | undefined>>({});
const capability = reactive<Partial<Capability>>({});
const selectedPlacement = ref('');
const sectionKey = computed(() => route.path.split('/').findLast(Boolean));
const isAccounts = computed(() => sectionKey.value === 'media-accounts');

async function bootstrap() {
  const result = await getMedia({ current: 1, size: 200 });
  media.value = result.records;
  mediaId.value = result.records[0]?.id || '';
}

async function loadAccounts() {
  if (!mediaId.value) return;
  const result = await getMediaAccounts({
    current: 1,
    keyword: keyword.value || undefined,
    mediaId: mediaId.value,
    size: isAccounts.value ? size.value : 200,
  });
  accounts.value = result.records;
  accountRows.value = result.records;
  if (!mediaAccountId.value || !result.records.some((item) => item.id === mediaAccountId.value)) {
    mediaAccountId.value = result.records[0]?.id || '';
  }
  if (isAccounts.value) total.value = result.total;
}

async function load() {
  loading.value = true;
  try {
    await loadAccounts();
    if (!isAccounts.value && mediaAccountId.value) {
      const result = await getPlacements({
        current: current.value,
        keyword: keyword.value || undefined,
        mediaAccountId: mediaAccountId.value,
        size: size.value,
      });
      placementRows.value = result.records;
      total.value = result.total;
    }
  } finally {
    loading.value = false;
  }
}

function openAccount(row?: MediaAccount) {
  editingType.value = 'account';
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      adapterType: 'GENERIC_SANDBOX',
      mediaId: mediaId.value,
    },
  );
  drawer.value = true;
}

function openPlacement(row?: Placement) {
  editingType.value = 'placement';
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      mediaAccountId: mediaAccountId.value,
      placementType: 'FEED',
    },
  );
  drawer.value = true;
}

async function submit() {
  saving.value = true;
  try {
    if (editingType.value === 'account') {
      if (!form.accountName || !form.mediaId) {
        ElMessage.warning('请填写账户名称并选择媒体');
        return;
      }
      await saveMediaAccount(form);
      ElMessage.success('媒体账户已保存');
    } else {
      if (!form.placementName || !form.placementCode || !form.mediaAccountId) {
        ElMessage.warning('请填写广告位名称、编码和媒体账户');
        return;
      }
      await savePlacement(form);
      ElMessage.success('广告位已保存');
    }
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggleAccount(row: MediaAccount) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  await ElMessageBox.confirm(`确认将媒体账户调整为 ${next}？`, '状态确认');
  await changeMediaAccountStatus(row.id, next);
  ElMessage.success('账户状态已更新');
  await load();
}

async function inspectAccount(row: MediaAccount) {
  const healthy = await getAdapterHealth(row.id);
  ElMessage({
    message: healthy ? '适配器连接健康' : '适配器当前不可用',
    type: healthy ? 'success' : 'warning',
  });
}

async function openCapability(row: Placement) {
  const data = await getCapability(row.id);
  Object.keys(capability).forEach(
    (key) => delete capability[key as keyof Capability],
  );
  Object.assign(capability, data);
  selectedPlacement.value = row.id;
  capabilityDrawer.value = true;
}

async function submitCapability() {
  saving.value = true;
  try {
    await saveCapability(selectedPlacement.value, capability);
    ElMessage.success('能力矩阵已更新');
    capabilityDrawer.value = false;
  } finally {
    saving.value = false;
  }
}

watch([mediaId, () => route.path], () => {
  current.value = 1;
  void load();
});
watch(mediaAccountId, () => {
  if (!isAccounts.value) {
    current.value = 1;
    void load();
  }
});

bootstrap().then(load);
</script>

<template>
  <main class="domain-page">
    <header class="domain-hero">
      <div>
        <p class="domain-eyebrow">INVENTORY · MEDIA SUPPLY</p>
        <h1 class="domain-title">
          {{ isAccounts ? '媒体账户' : '广告位与能力矩阵' }}
        </h1>
        <p class="domain-description">
          {{
            isAccounts
              ? '集中管理媒体账户、适配器健康、状态同步和报告拉取入口；认证配置始终脱敏。'
              : '维护广告位形态、支持的计费方式、目标事件和素材规格。'
          }}
        </p>
      </div>
      <el-button
        v-if="isAccounts"
        v-access:code="'inventory:media-account:write'"
        type="primary"
        @click="openAccount()"
      >
        新增媒体账户
      </el-button>
      <el-button
        v-else
        v-access:code="'inventory:placement:write'"
        type="primary"
        @click="openPlacement()"
      >
        新增广告位
      </el-button>
    </header>

    <el-card class="domain-card" shadow="never">
      <div class="domain-toolbar">
        <div class="domain-filters">
          <el-select
            v-model="mediaId"
            filterable
            placeholder="选择媒体主体"
            style="width: 220px"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-if="!isAccounts"
            v-model="mediaAccountId"
            filterable
            placeholder="选择媒体账户"
            style="width: 240px"
          >
            <el-option
              v-for="item in accounts"
              :key="item.id"
              :label="item.accountName"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称或编码"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-button @click="load">查询</el-button>
        </div>
      </div>

      <el-table
        v-if="isAccounts"
        v-loading="loading"
        :data="accountRows"
        row-key="id"
      >
        <el-table-column label="账户名称" prop="accountName" min-width="190" />
        <el-table-column label="外部账户 ID" min-width="180">
          <template #default="{ row }">
            <span class="domain-code">{{ row.accountIdExternal || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="适配器" prop="adapterType" min-width="170" />
        <el-table-column label="币种 / 时区" min-width="190">
          <template #default="{ row }">
            {{ row.currency }} · {{ row.timezone }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="230">
          <template #default="{ row }">
            <el-button link @click="inspectAccount(row)">健康检查</el-button>
            <el-button
              v-access:code="'inventory:media-account:write'"
              link
              type="primary"
              @click="openAccount(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'inventory:media-account:write'"
              link
              @click="toggleAccount(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        v-loading="loading"
        :data="placementRows"
        row-key="id"
      >
        <el-table-column label="广告位编码" min-width="180">
          <template #default="{ row }">
            <span class="domain-code">{{ row.placementCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="广告位名称" prop="placementName" min-width="180" />
        <el-table-column label="类型" prop="placementType" width="130" />
        <el-table-column label="素材规格" prop="supportedCreatives" min-width="220" />
        <el-table-column label="状态" prop="status" width="110" />
        <el-table-column align="right" label="操作" width="180">
          <template #default="{ row }">
            <el-button link @click="openCapability(row)">查看能力</el-button>
            <el-button
              v-access:code="'inventory:placement:write'"
              link
              type="primary"
              @click="openPlacement(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="domain-pagination">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawer"
      :title="editingType === 'account' ? '媒体账户' : '广告位'"
      size="500px"
    >
      <el-form label-position="top" :model="form">
        <template v-if="editingType === 'account'">
          <el-form-item label="账户名称" required>
            <el-input v-model="form.accountName" />
          </el-form-item>
          <el-form-item label="外部账户 ID">
            <el-input v-model="form.accountIdExternal" />
          </el-form-item>
          <el-form-item label="适配器类型">
            <el-input v-model="form.adapterType" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="广告位编码" required>
            <el-input v-model="form.placementCode" :disabled="Boolean(form.id)" />
          </el-form-item>
          <el-form-item label="广告位名称" required>
            <el-input v-model="form.placementName" />
          </el-form-item>
          <el-form-item label="广告位类型" required>
            <el-select v-model="form.placementType" class="w-full">
              <el-option label="信息流" value="FEED" />
              <el-option label="搜索" value="SEARCH" />
              <el-option label="开屏" value="SPLASH" />
              <el-option label="激励视频" value="REWARDED_VIDEO" />
            </el-select>
          </el-form-item>
          <el-form-item label="支持素材格式">
            <el-input v-model="form.supportedCreatives" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="capabilityDrawer" title="能力矩阵" size="540px">
      <el-form label-position="top" :model="capability">
        <el-form-item label="计费方式（JSON）">
          <el-input v-model="capability.billingModes" :rows="2" type="textarea" />
        </el-form-item>
        <el-form-item label="目标事件（JSON）">
          <el-input v-model="capability.targetEvents" :rows="2" type="textarea" />
        </el-form-item>
        <el-form-item label="定向字段（JSON）">
          <el-input v-model="capability.targetingFields" :rows="3" type="textarea" />
        </el-form-item>
        <el-form-item label="素材格式（JSON）">
          <el-input v-model="capability.creativeFormats" :rows="2" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="capabilityDrawer = false">关闭</el-button>
        <el-button
          v-access:code="'inventory:capability:write'"
          :loading="saving"
          type="primary"
          @click="submitCapability"
        >
          保存能力矩阵
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
