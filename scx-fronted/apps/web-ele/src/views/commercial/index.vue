<script lang="ts" setup>
import type { BaseRecord } from '#/api/business';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeOrganizationStatus,
  getAdAccounts,
  getAdvertisers,
  getAgencyRelations,
  getAgreementInstances,
  getDataAuthorizations,
  getOrganizations,
  getQualificationCases,
  reviewQualification,
  saveOrganization,
} from '#/api/commercial';

import '../business-domain.css';

defineOptions({ name: 'CommercialWorkspace' });

type Row = BaseRecord & Record<string, unknown>;
type Column = { label: string; minWidth?: number; prop: string };

const route = useRoute();
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const keyword = ref('');
const status = ref('');
const rows = ref<Row[]>([]);
const drawer = ref(false);
const form = reactive<Partial<Row>>({});

const sectionKey = computed(() => route.path.split('/').findLast(Boolean));
const sections: Record<
  string,
  {
    columns: Column[];
    description: string;
    eyebrow: string;
    permission: string;
    title: string;
  }
> = {
  organizations: {
    columns: [
      { label: '组织名称', prop: 'name' },
      { label: '组织类型', prop: 'orgType' },
      { label: '联系人', prop: 'contactName' },
      { label: '联系电话', prop: 'contactPhone' },
    ],
    description: '维护内部业务单元、直客与代理组织，并作为数据隔离边界。',
    eyebrow: 'COMMERCIAL · SCOPE',
    permission: 'commercial:organization:write',
    title: '组织与授权',
  },
  advertisers: {
    columns: [
      { label: '广告主名称', prop: 'advertiserName' },
      { label: '所属组织', prop: 'orgId' },
      { label: '行业', prop: 'industry' },
    ],
    description: '统一管理广告主档案、行业归属与可投放状态。',
    eyebrow: 'COMMERCIAL · ADVERTISER',
    permission: 'commercial:advertiser:write',
    title: '广告主管理',
  },
  agencies: {
    columns: [
      { label: '代理组织', prop: 'parentAgencyOrgId' },
      { label: '被代理组织', prop: 'childOrgId' },
      { label: '生效日期', prop: 'startDate' },
      { label: '失效日期', prop: 'endDate' },
    ],
    description: '维护代理、直客与代运营之间的有效委托关系。',
    eyebrow: 'COMMERCIAL · AGENCY',
    permission: 'commercial:agency:write',
    title: '代理关系',
  },
  'ad-accounts': {
    columns: [
      { label: '账户名称', prop: 'accountName' },
      { label: '广告主组织', prop: 'advertiserOrgId' },
      { label: '责任组织', prop: 'responsibleOrgId' },
    ],
    description: '建立广告主与结算责任组织之间的投放账户。',
    eyebrow: 'COMMERCIAL · ACCOUNT',
    permission: 'commercial:ad-account:write',
    title: '广告账户',
  },
  qualifications: {
    columns: [
      { label: '组织', prop: 'orgId' },
      { label: '资质类型', prop: 'caseType' },
      { label: '提交时间', prop: 'submittedAt' },
    ],
    description: '查看资质材料、审核状态与完整审核留痕。',
    eyebrow: 'COMMERCIAL · COMPLIANCE',
    permission: 'commercial:qualification:review',
    title: '资质审核',
  },
  agreements: {
    columns: [
      { label: '组织', prop: 'orgId' },
      { label: '模板', prop: 'templateId' },
      { label: '授权方', prop: 'grantorOrgId' },
      { label: '被授权方', prop: 'granteeOrgId' },
    ],
    description: '集中查看协议实例及数据授权，确保发布前商业条件完整。',
    eyebrow: 'COMMERCIAL · AGREEMENT',
    permission: 'commercial:agreement:write',
    title: '协议与数据授权',
  },
};

const section = computed(
  () => sections[sectionKey.value || ''] || sections.organizations!,
);

async function load() {
  loading.value = true;
  try {
    const query = {
      current: current.value,
      keyword: keyword.value || undefined,
      size: size.value,
      status: status.value || undefined,
    };
    let result;
    switch (sectionKey.value) {
      case 'ad-accounts': {
        result = await getAdAccounts(query);
        break;
      }
      case 'advertisers': {
        result = await getAdvertisers(query);
        break;
      }
      case 'agencies': {
        result = await getAgencyRelations(query);
        break;
      }
      case 'agreements': {
        const [agreements, authorizations] = await Promise.all([
          getAgreementInstances(query),
          getDataAuthorizations(query),
        ]);
        result = {
          records: [...agreements.records, ...authorizations.records],
          total: agreements.total + authorizations.total,
        };
        break;
      }
      case 'qualifications': {
        result = await getQualificationCases(query);
        break;
      }
      default: {
        result = await getOrganizations(query);
      }
    }
    rows.value = result.records as Row[];
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function openOrganization(row?: Row) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { orgType: 'BUSINESS_UNIT', status: 'ACTIVE' });
  drawer.value = true;
}

async function submitOrganization() {
  if (!form.name || !form.orgType) {
    ElMessage.warning('请填写组织名称和组织类型');
    return;
  }
  saving.value = true;
  try {
    await saveOrganization(form);
    ElMessage.success('组织信息已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggleOrganization(row: Row) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  await ElMessageBox.confirm(`确认将该组织调整为 ${next}？`, '状态确认');
  await changeOrganizationStatus(row.id, next);
  ElMessage.success('状态已更新');
  await load();
}

async function review(row: Row, decision: 'APPROVE' | 'REJECT') {
  const label = decision === 'APPROVE' ? '通过' : '驳回';
  await ElMessageBox.confirm(`确认${label}该资质？`, '审核确认');
  await reviewQualification(row.id, decision);
  ElMessage.success(`资质已${label}`);
  await load();
}

watch(
  () => route.path,
  () => {
    current.value = 1;
    keyword.value = '';
    status.value = '';
    void load();
  },
  { immediate: true },
);
</script>

<template>
  <main class="domain-page">
    <header class="domain-hero">
      <div>
        <p class="domain-eyebrow">{{ section.eyebrow }}</p>
        <h1 class="domain-title">{{ section.title }}</h1>
        <p class="domain-description">{{ section.description }}</p>
      </div>
      <el-button
        v-if="sectionKey === 'organizations'"
        v-access:code="'commercial:organization:write'"
        type="primary"
        @click="openOrganization()"
      >
        新增组织
      </el-button>
    </header>

    <el-card class="domain-card" shadow="never">
      <div class="domain-toolbar">
        <div class="domain-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称、编号或关键字"
            style="width: 260px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option label="启用 / 已生效" value="ACTIVE" />
            <el-option label="停用 / 已失效" value="INACTIVE" />
            <el-option label="待审核" value="SUBMITTED" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="ID" min-width="180">
          <template #default="{ row }">
            <span class="domain-code">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-for="column in section.columns"
          :key="column.prop"
          :label="column.label"
          :min-width="column.minWidth || 150"
        >
          <template #default="{ row }">
            {{ row[column.prop] || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag effect="plain" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="190">
          <template #default="{ row }">
            <template v-if="sectionKey === 'organizations'">
              <el-button
                v-access:code="'commercial:organization:write'"
                link
                type="primary"
                @click="openOrganization(row)"
              >
                编辑
              </el-button>
              <el-button
                v-access:code="'commercial:organization:write'"
                link
                @click="toggleOrganization(row)"
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
            <template v-else-if="sectionKey === 'qualifications'">
              <el-button
                v-access:code="'commercial:qualification:review'"
                link
                type="success"
                @click="review(row, 'APPROVE')"
              >
                通过
              </el-button>
              <el-button
                v-access:code="'commercial:qualification:review'"
                link
                type="danger"
                @click="review(row, 'REJECT')"
              >
                驳回
              </el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <div class="domain-empty">当前筛选条件下暂无商业数据</div>
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
      :title="form.id ? '编辑组织' : '新增组织'"
      size="520px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="组织名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="组织类型" required>
          <el-select v-model="form.orgType" class="w-full">
            <el-option label="内部业务单元" value="BUSINESS_UNIT" />
            <el-option label="直客广告主" value="DIRECT_ADVERTISER" />
            <el-option label="代理公司" value="AGENCY" />
            <el-option label="代运营广告主" value="MANAGED_ADVERTISER" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="form.contactEmail" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submitOrganization">
          保存
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
