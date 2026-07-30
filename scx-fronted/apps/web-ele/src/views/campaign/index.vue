<script lang="ts" setup>
import type { CampaignUiAction } from './campaign-state';

import type {
  Campaign,
  CampaignDetail,
  CampaignRevision,
  PublishingOperation,
} from '#/api/campaign';
import type { AdAccount } from '#/api/commercial';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@boot/access';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  campaignAction,
  getCampaignDetail,
  getCampaignOperations,
  getCampaignRevisions,
  getCampaigns,
  saveCampaign,
} from '#/api/campaign';
import { getAdAccounts } from '#/api/commercial';

import { getCampaignUiActions } from './campaign-state';

import '../business-domain.css';

defineOptions({ name: 'CampaignWorkspace' });

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const adAccounts = ref<AdAccount[]>([]);
const adAccountId = ref('');
const keyword = ref('');
const status = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const rows = ref<Campaign[]>([]);
const drawer = ref(false);
const detailDrawer = ref(false);
const activeDetailTab = ref('groups');
const detail = ref<CampaignDetail>();
const operations = ref<PublishingOperation[]>([]);
const revisions = ref<CampaignRevision[]>([]);
const reviewTotal = ref(0);
const form = reactive<Partial<Campaign>>({});
const canReview = computed(() => hasAccessByCodes(['campaign:review']));
const isReviewCenter = computed(
  () => route.path.endsWith('/reviews') || route.query.tab === 'review',
);
const activeWorkspaceTab = computed({
  get: () => (isReviewCenter.value && canReview.value ? 'review' : 'all'),
  set: (tab: string) => {
    void router.replace({
      query: {
        ...route.query,
        tab: tab === 'review' ? 'review' : 'all',
      },
    });
  },
});

async function bootstrap() {
  const result = await getAdAccounts({ current: 1, size: 200 });
  adAccounts.value = result.records;
  const requested = String(route.query.adAccountId || '');
  adAccountId.value = result.records.some((item) => item.id === requested)
    ? requested
    : result.records[0]?.id || '';
}

async function loadReviewTotal() {
  if (!adAccountId.value || !canReview.value) {
    reviewTotal.value = 0;
    return;
  }
  const result = await getCampaigns({
    adAccountId: adAccountId.value,
    current: 1,
    size: 1,
    status: 'PENDING_REVIEW',
  });
  reviewTotal.value = result.total;
}

async function load() {
  if (!adAccountId.value) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    const result = await getCampaigns({
      adAccountId: adAccountId.value,
      current: current.value,
      keyword: keyword.value || undefined,
      size: size.value,
      status: isReviewCenter.value
        ? 'PENDING_REVIEW'
        : status.value || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
    if (isReviewCenter.value) reviewTotal.value = result.total;
  } finally {
    loading.value = false;
  }
}

function open(row?: Campaign) {
  Object.keys(form).forEach((key) => Reflect.deleteProperty(form, key));
  Object.assign(
    form,
    row || {
      adAccountId: adAccountId.value,
      billingMode: 'CPC',
      currency: 'CNY',
      objective: 'CONVERSION',
      timezone: 'Asia/Shanghai',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.name || !form.adAccountId || !form.objective || !form.billingMode) {
    ElMessage.warning('请完整填写计划名称、广告账户、目标和计费方式');
    return;
  }
  saving.value = true;
  try {
    await saveCampaign(form);
    ElMessage.success('推广计划已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function showDetail(row: Campaign) {
  detailDrawer.value = true;
  detail.value = undefined;
  operations.value = [];
  revisions.value = [];
  activeDetailTab.value = 'groups';
  const [campaignDetail, operationPage, revisionPage] = await Promise.all([
    getCampaignDetail(row.id),
    getCampaignOperations(row.id),
    getCampaignRevisions(row.id),
  ]);
  detail.value = campaignDetail;
  operations.value = operationPage.records;
  revisions.value = revisionPage.records;
}

async function runAction(
  row: Campaign,
  action:
    | 'approve'
    | 'archive'
    | 'end'
    | 'pause'
    | 'publish'
    | 'reject'
    | 'resume'
    | 'submit',
) {
  const labels = {
    approve: '批准',
    archive: '归档',
    end: '结束',
    pause: '暂停',
    publish: '发布',
    reject: '驳回',
    resume: '恢复',
    submit: '提交审批',
  };
  await ElMessageBox.confirm(
    `确认${labels[action]}计划「${row.name}」？`,
    '状态确认',
  );
  await campaignAction(row.id, action);
  ElMessage.success(`计划已${labels[action]}`);
  await load();
}

function money(value?: number) {
  return new Intl.NumberFormat('zh-CN', {
    currency: 'CNY',
    style: 'currency',
  }).format(value || 0);
}

function can(row: Campaign, action: CampaignUiAction) {
  return getCampaignUiActions(row.status, isReviewCenter.value).includes(
    action,
  );
}

watch([adAccountId, () => route.path, () => route.query.tab], () => {
  current.value = 1;
  if (adAccountId.value) {
    void router.replace({
      query: { ...route.query, adAccountId: adAccountId.value },
    });
  }
  void Promise.all([load(), loadReviewTotal()]);
});
bootstrap().then(() => Promise.all([load(), loadReviewTotal()]));
</script>

<template>
  <main class="domain-page">
    <el-tabs v-model="activeWorkspaceTab" class="workspace-tabs mb-4">
      <el-tab-pane label="全部计划" name="all" />
      <el-tab-pane v-if="canReview" name="review">
        <template #label>
          待我审核
          <el-badge
            v-if="reviewTotal"
            class="ml-2"
            :value="reviewTotal"
            :max="99"
          />
        </template>
      </el-tab-pane>
    </el-tabs>
    <header class="domain-hero">
      <div>
        <p class="domain-eyebrow">CAMPAIGN · DELIVERY CONTROL</p>
        <h1 class="domain-title">
          {{ isReviewCenter ? '计划审核中心' : '推广计划' }}
        </h1>
        <p class="domain-description">
          从草稿、审批、发布到暂停、恢复和结束，统一控制预算与媒体发布状态。
        </p>
      </div>
      <el-button
        v-if="!isReviewCenter"
        v-access:code="'campaign:write'"
        type="primary"
        @click="open()"
      >
        新建推广计划
      </el-button>
    </header>

    <el-alert
      v-if="adAccounts.length === 0"
      class="mb-4"
      :closable="false"
      show-icon
      title="尚无可用广告账户"
      type="warning"
    >
      <template #default>
        创建并激活广告账户后才能新建推广计划。
        <el-button
          link
          type="primary"
          @click="router.push('/commercial/ad-accounts')"
        >
          前往广告账户
        </el-button>
      </template>
    </el-alert>

    <el-card class="domain-card" shadow="never">
      <div class="domain-toolbar">
        <div class="domain-filters">
          <el-select
            v-model="adAccountId"
            filterable
            placeholder="选择广告账户"
            style="width: 240px"
          >
            <el-option
              v-for="item in adAccounts"
              :key="item.id"
              :label="item.accountName"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="计划名称"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-select
            v-if="!isReviewCenter"
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 160px"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING_REVIEW" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="投放中" value="DELIVERING" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已结束" value="ENDED" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="计划名称" min-width="210">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">
              {{ row.name }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="目标" prop="objective" width="140" />
        <el-table-column label="计费" prop="billingMode" width="110" />
        <el-table-column label="总预算" width="150">
          <template #default="{ row }">
            <span class="domain-money">{{ money(row.totalBudget) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="日预算" width="150">
          <template #default="{ row }">
            <span class="domain-money">{{ money(row.dailyBudget) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="260">
          <template #default="{ row }">
            <template v-if="isReviewCenter">
              <el-button
                v-access:code="'campaign:review'"
                link
                type="success"
                @click="runAction(row, 'approve')"
              >
                批准
              </el-button>
              <el-button
                v-access:code="'campaign:review'"
                link
                type="danger"
                @click="runAction(row, 'reject')"
              >
                驳回
              </el-button>
            </template>
            <template v-else>
              <el-button
                v-if="can(row, 'edit')"
                v-access:code="'campaign:write'"
                link
                @click="open(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="can(row, 'submit')"
                v-access:code="'campaign:write'"
                link
                type="primary"
                @click="runAction(row, 'submit')"
              >
                提交
              </el-button>
              <el-button
                v-if="can(row, 'publish')"
                v-access:code="'campaign:write'"
                link
                type="success"
                @click="runAction(row, 'publish')"
              >
                发布
              </el-button>
              <el-button
                v-if="can(row, 'pause')"
                v-access:code="'campaign:write'"
                link
                @click="runAction(row, 'pause')"
              >
                暂停
              </el-button>
              <el-button
                v-if="can(row, 'resume')"
                v-access:code="'campaign:write'"
                link
                @click="runAction(row, 'resume')"
              >
                恢复
              </el-button>
              <el-button
                v-if="can(row, 'end')"
                v-access:code="'campaign:write'"
                link
                type="danger"
                @click="runAction(row, 'end')"
              >
                结束
              </el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <div class="domain-empty">
            {{ adAccountId ? '当前账户暂无推广计划' : '请先选择广告账户' }}
          </div>
        </template>
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
      :title="form.id ? '编辑推广计划' : '新建推广计划'"
      size="560px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="计划名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="广告账户" required>
          <el-select v-model="form.adAccountId" class="w-full">
            <el-option
              v-for="item in adAccounts"
              :key="item.id"
              :label="item.accountName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <div class="grid grid-cols-2 gap-4">
          <el-form-item label="投放目标" required>
            <el-select v-model="form.objective" class="w-full">
              <el-option label="转化" value="CONVERSION" />
              <el-option label="流量" value="TRAFFIC" />
              <el-option label="应用安装" value="APP_INSTALL" />
              <el-option label="销售" value="SALES" />
            </el-select>
          </el-form-item>
          <el-form-item label="计费方式" required>
            <el-select v-model="form.billingMode" class="w-full">
              <el-option label="CPC" value="CPC" />
              <el-option label="CPM" value="CPM" />
              <el-option label="媒体托管 oCPC" value="MEDIA_MANAGED_OCPC" />
            </el-select>
          </el-form-item>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <el-form-item label="总预算（CNY）">
            <el-input-number
              v-model="form.totalBudget"
              :min="0.01"
              :precision="2"
              class="!w-full"
            />
          </el-form-item>
          <el-form-item label="日预算（CNY）">
            <el-input-number
              v-model="form.dailyBudget"
              :min="0.01"
              :precision="2"
              class="!w-full"
            />
          </el-form-item>
        </div>
        <el-form-item label="目标 CPA（CNY）">
          <el-input-number
            v-model="form.targetCpa"
            :min="0.01"
            :precision="2"
            class="!w-full"
          />
        </el-form-item>
        <div class="grid grid-cols-2 gap-4">
          <el-form-item label="开始时间">
            <el-date-picker
              v-model="form.startAt"
              class="!w-full"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker
              v-model="form.endAt"
              class="!w-full"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit"
          >保存草稿</el-button
        >
      </template>
    </el-drawer>

    <el-drawer v-model="detailDrawer" size="76%" title="推广计划详情">
      <el-skeleton v-if="!detail" :rows="8" animated />
      <template v-else>
        <div class="domain-detail-grid mb-5">
          <div class="domain-metric">
            <div class="domain-metric-label">计划状态</div>
            <div class="domain-metric-value">{{ detail.campaign.status }}</div>
          </div>
          <div class="domain-metric">
            <div class="domain-metric-label">广告组 / 广告</div>
            <div class="domain-metric-value">
              {{ detail.adGroups.length }} / {{ detail.ads.length }}
            </div>
          </div>
          <div class="domain-metric">
            <div class="domain-metric-label">媒体绑定</div>
            <div class="domain-metric-value">
              {{ detail.mediaBindings.length }}
            </div>
          </div>
        </div>
        <el-tabs v-model="activeDetailTab">
          <el-tab-pane label="广告组" name="groups">
            <el-table :data="detail.adGroups">
              <el-table-column label="名称" prop="name" />
              <el-table-column label="状态" prop="status" width="120" />
              <el-table-column label="版本" prop="version" width="90" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="广告" name="ads">
            <el-table :data="detail.ads">
              <el-table-column label="名称" prop="name" />
              <el-table-column label="广告组" prop="adGroupId" />
              <el-table-column label="状态" prop="status" width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="创意绑定" name="creative">
            <el-table :data="detail.ads">
              <el-table-column label="广告" prop="name" />
              <el-table-column label="创意绑定 ID" prop="creativeBindingId" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="媒体绑定" name="media">
            <el-table :data="detail.mediaBindings">
              <el-table-column label="媒体投放账户" prop="mediaAccountId" />
              <el-table-column label="广告位" prop="placementId" />
              <el-table-column label="外部状态" prop="externalStatus" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="发布校验" name="validation">
            <el-result
              v-if="detail.validationErrors.length === 0"
              icon="success"
              title="发布校验通过"
            />
            <el-alert
              v-for="error in detail.validationErrors"
              :key="error"
              class="mb-3"
              :closable="false"
              :title="error"
              type="warning"
            />
          </el-tab-pane>
          <el-tab-pane label="操作记录" name="operations">
            <el-table :data="operations" empty-text="暂无发布操作记录">
              <el-table-column label="操作" prop="operationType" width="150" />
              <el-table-column label="状态" prop="status" width="120" />
              <el-table-column
                label="结果"
                min-width="220"
                prop="resultDetail"
              />
              <el-table-column label="开始时间" prop="createdAt" width="180" />
              <el-table-column
                label="完成时间"
                prop="completedAt"
                width="180"
              />
            </el-table>
            <el-divider content-position="left">计划版本</el-divider>
            <el-table :data="revisions" empty-text="暂无版本记录">
              <el-table-column label="版本" prop="version" width="100" />
              <el-table-column
                label="前序版本 ID"
                min-width="220"
                prop="supersedes"
              />
              <el-table-column label="创建时间" prop="createdAt" width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </main>
</template>
