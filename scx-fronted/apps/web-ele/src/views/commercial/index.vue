<script lang="ts" setup>
import type { BaseRecord } from '#/api/business';
import type { AgreementTemplate, Organization } from '#/api/commercial';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  activateAgreementTemplate,
  changeAdAccountStatus,
  changeAdvertiserStatus,
  changeOrganizationStatus,
  deleteAdAccount,
  deleteAdvertiser,
  deleteAgencyRelation,
  deleteAgreementInstance,
  deleteAgreementTemplate,
  deleteDataAuthorization,
  deleteOrganization,
  deleteQualification,
  getAdAccounts,
  getAdvertisers,
  getAgencyRelations,
  getAgreementInstances,
  getAgreementTemplates,
  getDataAuthorizations,
  getOrganizations,
  getOrganizationScopes,
  getQualificationCases,
  grantOrganizationScope,
  reviewQualification,
  revokeOrganizationScope,
  saveAdAccount,
  saveAdvertiser,
  saveAgencyRelation,
  saveAgreementInstance,
  saveAgreementTemplate,
  saveDataAuthorization,
  saveOrganization,
  saveQualification,
  signAgreementInstance,
  submitQualification,
  terminateAgreementInstance,
} from '#/api/commercial';

import '../business-domain.css';

defineOptions({ name: 'CommercialWorkspace' });

type Row = BaseRecord & Record<string, any>;
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
const form = reactive<Record<string, any>>({});
const organizations = ref<Organization[]>([]);
const templates = ref<AgreementTemplate[]>([]);
const organizationTab = ref<'organizations' | 'scopes'>('organizations');
const agreementTab = ref<'authorizations' | 'instances' | 'templates'>(
  'instances',
);

const sectionKey = computed(() => route.path.split('/').findLast(Boolean));
const sections = {
  organizations: {
    addLabel: '新增组织',
    description: '维护商业组织与用户数据范围，授权变更可随时撤销。',
    eyebrow: 'COMMERCIAL · SCOPE',
    permission: 'commercial:organization:write',
    title: '组织与授权',
  },
  advertisers: {
    addLabel: '新增广告主',
    description: '统一管理广告主档案、行业归属与可投放状态。',
    eyebrow: 'COMMERCIAL · ADVERTISER',
    permission: 'commercial:advertiser:write',
    title: '广告主管理',
  },
  agencies: {
    addLabel: '新增代理关系',
    description: '维护代理、直客与代运营之间的有效委托关系。',
    eyebrow: 'COMMERCIAL · AGENCY',
    permission: 'commercial:agency:write',
    title: '代理关系',
  },
  'ad-accounts': {
    addLabel: '新增广告账户',
    description: '建立广告主与结算责任组织之间的投放账户。',
    eyebrow: 'COMMERCIAL · ACCOUNT',
    permission: 'commercial:ad-account:write',
    title: '广告账户',
  },
  qualifications: {
    addLabel: '新增资质案件',
    description: '维护资质材料、审核状态与不可变审核留痕。',
    eyebrow: 'COMMERCIAL · COMPLIANCE',
    permission: 'commercial:qualification:write',
    title: '资质审核',
  },
  agreements: {
    addLabel: '新增协议',
    description: '管理协议模板、签署实例与跨组织数据授权。',
    eyebrow: 'COMMERCIAL · AGREEMENT',
    permission: 'commercial:agreement:write',
    title: '协议与数据授权',
  },
} as const;

const section = computed(
  () =>
    sections[sectionKey.value as keyof typeof sections] ||
    sections.organizations,
);

const activeKind = computed(() => {
  if (sectionKey.value === 'organizations') return organizationTab.value;
  if (sectionKey.value === 'agreements') return agreementTab.value;
  return sectionKey.value || 'organizations';
});

const columns = computed<Column[]>(() => {
  switch (activeKind.value) {
    case 'ad-accounts': {
      return [
        { label: '账户名称', prop: 'accountName' },
        { label: '广告主组织', prop: 'advertiserOrgId' },
        { label: '责任组织', prop: 'responsibleOrgId' },
        { label: '币种', minWidth: 90, prop: 'currency' },
      ];
    }
    case 'advertisers': {
      return [
        { label: '广告主名称', prop: 'advertiserName' },
        { label: '所属组织', prop: 'orgId' },
        { label: '行业', prop: 'industry' },
        { label: '网站', prop: 'websiteUrl' },
      ];
    }
    case 'agencies': {
      return [
        { label: '代理组织', prop: 'parentAgencyOrgId' },
        { label: '被代理组织', prop: 'childOrgId' },
        { label: '关系类型', prop: 'relationType' },
        { label: '生效日期', prop: 'startDate' },
        { label: '失效日期', prop: 'endDate' },
      ];
    }
    case 'authorizations': {
      return [
        { label: '授权方', prop: 'grantorOrgId' },
        { label: '被授权方', prop: 'granteeOrgId' },
        { label: '授权类型', prop: 'authorizationType' },
        { label: '授权范围', prop: 'scope' },
      ];
    }
    case 'instances': {
      return [
        { label: '所属组织', prop: 'orgId' },
        { label: '模板 ID', prop: 'templateId' },
        { label: '模板版本', minWidth: 100, prop: 'templateVersion' },
        { label: '到期时间', prop: 'expiredAt' },
      ];
    }
    case 'qualifications': {
      return [
        { label: '组织', prop: 'orgId' },
        { label: '资质类型', prop: 'caseType' },
        { label: '提交时间', prop: 'submittedAt' },
        { label: '审核意见', prop: 'reviewComment' },
      ];
    }
    case 'scopes': {
      return [
        { label: '用户 ID', prop: 'userId' },
        { label: '组织 ID', prop: 'subjectId' },
        { label: '授权时间', prop: 'createTime' },
      ];
    }
    case 'templates': {
      return [
        { label: '模板名称', prop: 'name' },
        { label: '模板类型', prop: 'templateType' },
        { label: '版本', minWidth: 90, prop: 'version' },
        { label: '内容摘要', prop: 'content' },
      ];
    }
    default: {
      return [
        { label: '组织名称', prop: 'name' },
        { label: '组织类型', prop: 'orgType' },
        { label: '上级组织', prop: 'parentOrgId' },
        { label: '联系人', prop: 'contactName' },
        { label: '联系电话', prop: 'contactPhone' },
      ];
    }
  }
});

const writePermission = computed(() => {
  if (activeKind.value === 'scopes')
    return 'commercial:organization-scope:write';
  if (activeKind.value === 'authorizations')
    return 'commercial:data-authorization:write';
  return section.value.permission;
});

const addLabel = computed(() => {
  const labels: Record<string, string> = {
    authorizations: '新增数据授权',
    instances: '新增协议实例',
    scopes: '新增用户授权',
    templates: '新增协议模板',
  };
  return labels[activeKind.value] || section.value.addLabel;
});

function orgName(id?: string) {
  return organizations.value.find((item) => item.id === id)?.name || id || '—';
}

function displayValue(row: Row, prop: string) {
  if (
    [
      'advertiserOrgId',
      'childOrgId',
      'granteeOrgId',
      'grantorOrgId',
      'orgId',
      'parentAgencyOrgId',
      'parentOrgId',
      'responsibleOrgId',
      'subjectId',
    ].includes(prop)
  ) {
    return orgName(row[prop]);
  }
  const value = row[prop];
  if (prop === 'content' && value) {
    return String(value).slice(0, 48);
  }
  return value || '—';
}

async function loadOptions() {
  const result = await getOrganizations({ current: 1, size: 500 });
  organizations.value = result.records;
  if (sectionKey.value === 'agreements') {
    const templateResult = await getAgreementTemplates({
      current: 1,
      size: 200,
      status: 'ACTIVE',
    });
    templates.value = templateResult.records;
  }
}

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
    switch (activeKind.value) {
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
      case 'authorizations': {
        result = await getDataAuthorizations(query);
        break;
      }
      case 'instances': {
        result = await getAgreementInstances(query);
        break;
      }
      case 'qualifications': {
        result = await getQualificationCases(query);
        break;
      }
      case 'scopes': {
        result = await getOrganizationScopes(query);
        break;
      }
      case 'templates': {
        result = await getAgreementTemplates(query);
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

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key]);
}

function openEditor(row?: Row) {
  resetForm();
  if (row) {
    Object.assign(form, row);
  } else {
    const defaults: Record<string, Record<string, any>> = {
      'ad-accounts': {
        currency: 'CNY',
        status: 'DRAFT',
        timezone: 'Asia/Shanghai',
      },
      advertisers: {
        orgType: 'DIRECT_ADVERTISER',
        status: 'DRAFT',
      },
      agencies: { relationType: 'DIRECT', status: 'ACTIVE' },
      authorizations: { status: 'ACTIVE' },
      organizations: { orgType: 'BUSINESS_UNIT', status: 'ACTIVE' },
      qualifications: { status: 'DRAFT' },
      scopes: {},
      templates: { status: 'DRAFT' },
    };
    Object.assign(form, defaults[activeKind.value] || {});
  }
  drawer.value = true;
}

function required(...fields: string[]) {
  const missing = fields.some((field) => !form[field]);
  if (missing) ElMessage.warning('请完整填写必填项');
  return !missing;
}

function applyDateRange(value: [string, string] | null) {
  form.startDate = value?.[0];
  form.endDate = value?.[1];
}

async function submit() {
  saving.value = true;
  try {
    switch (activeKind.value) {
      case 'ad-accounts': {
        if (!required('accountName', 'advertiserOrgId')) return;
        await saveAdAccount(form);
        break;
      }
      case 'advertisers': {
        if (
          !required(
            'advertiserName',
            ...(form.id ? [] : ['orgName', 'orgType']),
          )
        )
          return;
        await saveAdvertiser(form);
        break;
      }
      case 'agencies': {
        if (!required('parentAgencyOrgId', 'childOrgId')) return;
        await saveAgencyRelation(form);
        break;
      }
      case 'authorizations': {
        if (!required('authorizationType', 'grantorOrgId', 'granteeOrgId'))
          return;
        await saveDataAuthorization(form);
        break;
      }
      case 'instances': {
        if (!required('orgId', 'templateId')) return;
        await saveAgreementInstance(form);
        break;
      }
      case 'qualifications': {
        if (!required('orgId', 'caseType')) return;
        await saveQualification(form);
        break;
      }
      case 'scopes': {
        if (!required('organizationId', 'userId')) return;
        await grantOrganizationScope({
          organizationId: form.organizationId,
          userId: form.userId,
        });
        break;
      }
      case 'templates': {
        if (!required('name', 'templateType', 'content')) return;
        await saveAgreementTemplate(form);
        break;
      }
      default: {
        if (!required('name', 'orgType')) return;
        await saveOrganization(form);
      }
    }
    ElMessage.success('保存成功');
    drawer.value = false;
    await Promise.all([load(), loadOptions()]);
  } finally {
    saving.value = false;
  }
}

async function remove(row: Row) {
  const semantic =
    activeKind.value === 'organizations' ||
    activeKind.value === 'advertisers' ||
    activeKind.value === 'ad-accounts'
      ? '归档/关闭'
      : '删除/撤销';
  await ElMessageBox.confirm(
    `确认${semantic}这条记录？关联中或已生效的数据会被服务端保护。`,
    '操作确认',
    { type: 'warning' },
  );
  switch (activeKind.value) {
    case 'ad-accounts': {
      await deleteAdAccount(row.id);
      break;
    }
    case 'advertisers': {
      await deleteAdvertiser(row.id);
      break;
    }
    case 'agencies': {
      await deleteAgencyRelation(row.id);
      break;
    }
    case 'authorizations': {
      await deleteDataAuthorization(row.id);
      break;
    }
    case 'instances': {
      await deleteAgreementInstance(row.id);
      break;
    }
    case 'qualifications': {
      await deleteQualification(row.id);
      break;
    }
    case 'scopes': {
      await revokeOrganizationScope(row.id, row.subjectId);
      break;
    }
    case 'templates': {
      await deleteAgreementTemplate(row.id);
      break;
    }
    default: {
      await deleteOrganization(row.id);
    }
  }
  ElMessage.success('操作成功');
  await load();
}

async function changeState(row: Row, action: string) {
  const labels: Record<string, string> = {
    activate: '启用',
    approve: '审核通过',
    reject: '驳回',
    sign: '签署',
    submit: '提交审核',
    suspend: '停用',
    terminate: '终止',
  };
  await ElMessageBox.confirm(`确认执行“${labels[action]}”？`, '状态确认');
  switch (activeKind.value) {
    case 'ad-accounts': {
      await changeAdAccountStatus(
        row.id,
        row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
      );

      break;
    }
    case 'advertisers': {
      await changeAdvertiserStatus(
        row.id,
        row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
      );

      break;
    }
    case 'instances': {
      await (action === 'sign'
        ? signAgreementInstance(row.id)
        : terminateAgreementInstance(row.id));

      break;
    }
    case 'organizations': {
      await changeOrganizationStatus(
        row.id,
        row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
      );

      break;
    }
    case 'qualifications': {
      await (action === 'submit'
        ? submitQualification(row.id)
        : reviewQualification(
            row.id,
            action === 'approve' ? 'APPROVE' : 'REJECT',
          ));

      break;
    }
    case 'templates': {
      await activateAgreementTemplate(row.id);

      break;
    }
    // No default
  }
  ElMessage.success('状态已更新');
  await load();
}

function resetAndLoad() {
  current.value = 1;
  void load();
}

watch(
  () => route.path,
  async () => {
    current.value = 1;
    keyword.value = '';
    status.value = '';
    organizationTab.value = 'organizations';
    agreementTab.value = 'instances';
    await Promise.all([load(), loadOptions()]);
  },
  { immediate: true },
);

watch([organizationTab, agreementTab], resetAndLoad);
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
        v-access:code="writePermission"
        type="primary"
        @click="openEditor()"
      >
        {{ addLabel }}
      </el-button>
    </header>

    <el-card class="domain-card" shadow="never">
      <el-tabs
        v-if="sectionKey === 'organizations'"
        v-model="organizationTab"
        class="domain-tabs"
      >
        <el-tab-pane label="组织档案" name="organizations" />
        <el-tab-pane label="用户数据授权" name="scopes" />
      </el-tabs>
      <el-tabs
        v-if="sectionKey === 'agreements'"
        v-model="agreementTab"
        class="domain-tabs"
      >
        <el-tab-pane label="协议实例" name="instances" />
        <el-tab-pane label="数据授权" name="authorizations" />
        <el-tab-pane label="协议模板" name="templates" />
      </el-tabs>

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
            style="width: 170px"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用 / 生效" value="ACTIVE" />
            <el-option label="待审核" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已签署" value="SIGNED" />
            <el-option label="停用 / 暂停" value="SUSPENDED" />
            <el-option label="已关闭 / 撤销" value="CLOSED" />
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
          v-for="column in columns"
          :key="column.prop"
          :label="column.label"
          :min-width="column.minWidth || 150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ displayValue(row, column.prop) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="activeKind !== 'scopes'"
          label="状态"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="
                ['ACTIVE', 'APPROVED', 'SIGNED'].includes(row.status)
                  ? 'success'
                  : row.status === 'SUBMITTED'
                    ? 'warning'
                    : 'info'
              "
            >
              {{ row.status || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="260">
          <template #default="{ row }">
            <el-button
              v-if="
                !['scopes'].includes(activeKind) &&
                !['ARCHIVED', 'CANCELLED', 'CLOSED', 'REVOKED'].includes(
                  row.status,
                )
              "
              v-access:code="writePermission"
              link
              type="primary"
              @click="openEditor(row)"
            >
              编辑
            </el-button>

            <template
              v-if="
                ['ad-accounts', 'advertisers', 'organizations'].includes(
                  activeKind,
                ) && !['ARCHIVED', 'CLOSED'].includes(row.status)
              "
            >
              <el-button
                v-access:code="writePermission"
                link
                @click="changeState(row, 'suspend')"
              >
                {{ ['ACTIVE'].includes(row.status) ? '停用' : '启用' }}
              </el-button>
            </template>
            <template v-if="activeKind === 'qualifications'">
              <el-button
                v-if="['DRAFT', 'SUPPLEMENT_REQUIRED'].includes(row.status)"
                v-access:code="'commercial:qualification:write'"
                link
                @click="changeState(row, 'submit')"
              >
                提交
              </el-button>
              <el-button
                v-if="row.status === 'SUBMITTED'"
                v-access:code="'commercial:qualification:review'"
                link
                type="success"
                @click="changeState(row, 'approve')"
              >
                通过
              </el-button>
              <el-button
                v-if="row.status === 'SUBMITTED'"
                v-access:code="'commercial:qualification:review'"
                link
                type="danger"
                @click="changeState(row, 'reject')"
              >
                驳回
              </el-button>
            </template>
            <template v-if="activeKind === 'templates'">
              <el-button
                v-if="row.status === 'DRAFT'"
                v-access:code="writePermission"
                link
                type="success"
                @click="changeState(row, 'activate')"
              >
                启用
              </el-button>
            </template>
            <template v-if="activeKind === 'instances'">
              <el-button
                v-if="row.status === 'DRAFT'"
                v-access:code="writePermission"
                link
                type="success"
                @click="changeState(row, 'sign')"
              >
                签署
              </el-button>
              <el-button
                v-if="row.status === 'SIGNED'"
                v-access:code="writePermission"
                link
                @click="changeState(row, 'terminate')"
              >
                终止
              </el-button>
            </template>

            <el-button
              v-if="
                !['ARCHIVED', 'CANCELLED', 'CLOSED', 'REVOKED'].includes(
                  row.status,
                )
              "
              v-access:code="writePermission"
              link
              type="danger"
              @click="remove(row)"
            >
              {{ activeKind === 'scopes' ? '撤销' : '删除' }}
            </el-button>
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
      :title="`${form.id ? '编辑' : '新增'}${addLabel.replace('新增', '')}`"
      size="560px"
    >
      <el-form label-position="top" :model="form">
        <template v-if="activeKind === 'organizations'">
          <el-form-item label="组织名称" required>
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="组织类型" required>
            <el-select v-model="form.orgType" class="w-full">
              <el-option label="企业" value="ENTERPRISE" />
              <el-option label="内部业务单元" value="BUSINESS_UNIT" />
              <el-option label="直客广告主" value="DIRECT_ADVERTISER" />
              <el-option label="代理公司" value="AGENCY" />
              <el-option label="代理子账户" value="AGENCY_SUB_ACCOUNT" />
              <el-option label="代运营广告主" value="MANAGED_ADVERTISER" />
            </el-select>
          </el-form-item>
          <el-form-item label="上级组织">
            <el-select v-model="form.parentOrgId" clearable class="w-full">
              <el-option
                v-for="item in organizations.filter(
                  (item) => item.id !== form.id,
                )"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="法定代表人">
            <el-input v-model="form.legalPerson" />
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
        </template>

        <template v-else-if="activeKind === 'scopes'">
          <el-form-item label="用户 ID" required>
            <el-input v-model="form.userId" placeholder="请输入系统用户 ID" />
          </el-form-item>
          <el-form-item label="授权组织" required>
            <el-select v-model="form.organizationId" class="w-full" filterable>
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'advertisers'">
          <el-form-item v-if="!form.id" label="组织名称" required>
            <el-input v-model="form.orgName" />
          </el-form-item>
          <el-form-item v-if="!form.id" label="组织类型" required>
            <el-select v-model="form.orgType" class="w-full">
              <el-option label="直客广告主" value="DIRECT_ADVERTISER" />
              <el-option label="代运营广告主" value="MANAGED_ADVERTISER" />
            </el-select>
          </el-form-item>
          <el-form-item label="广告主名称" required>
            <el-input v-model="form.advertiserName" maxlength="128" />
          </el-form-item>
          <el-form-item label="所属行业">
            <el-input v-model="form.industry" />
          </el-form-item>
          <el-form-item label="官方网站">
            <el-input v-model="form.websiteUrl" />
          </el-form-item>
          <template v-if="!form.id">
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" />
            </el-form-item>
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" />
            </el-form-item>
            <el-form-item label="联系邮箱">
              <el-input v-model="form.contactEmail" />
            </el-form-item>
          </template>
        </template>

        <template v-else-if="activeKind === 'agencies'">
          <el-form-item label="代理组织" required>
            <el-select
              v-model="form.parentAgencyOrgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations.filter((item) =>
                  ['AGENCY', 'ENTERPRISE'].includes(item.orgType),
                )"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="被代理组织" required>
            <el-select
              v-model="form.childOrgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关系类型">
            <el-select v-model="form.relationType" class="w-full">
              <el-option label="直接代理" value="DIRECT" />
              <el-option label="代运营" value="MANAGED" />
              <el-option label="转授权" value="DELEGATED" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权范围">
            <el-input
              v-model="form.authorizedScopes"
              placeholder="例如：CAMPAIGN,REPORT"
            />
          </el-form-item>
          <el-form-item label="有效期">
            <el-date-picker
              v-model="form.dateRange"
              end-placeholder="结束日期"
              range-separator="至"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD"
              @change="applyDateRange"
            />
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'ad-accounts'">
          <el-form-item label="广告主组织" required>
            <el-select
              v-model="form.advertiserOrgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations.filter((item) =>
                  ['DIRECT_ADVERTISER', 'MANAGED_ADVERTISER'].includes(
                    item.orgType,
                  ),
                )"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="账户名称" required>
            <el-input v-model="form.accountName" maxlength="128" />
          </el-form-item>
          <el-form-item label="责任组织">
            <el-select
              v-model="form.responsibleOrgId"
              clearable
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="返利组织">
            <el-select
              v-model="form.rebateOrgId"
              clearable
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="退款组织">
            <el-select
              v-model="form.refundOrgId"
              clearable
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="币种 / 时区">
            <div class="domain-inline-fields">
              <el-select v-model="form.currency">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
              </el-select>
              <el-input v-model="form.timezone" />
            </div>
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'qualifications'">
          <el-form-item label="所属组织" required>
            <el-select
              v-model="form.orgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="资质类型" required>
            <el-select v-model="form.caseType" class="w-full" filterable>
              <el-option label="营业执照" value="BUSINESS_LICENSE" />
              <el-option label="行业许可证" value="INDUSTRY_LICENSE" />
              <el-option label="广告投放资质" value="ADVERTISING" />
              <el-option label="其他资质" value="OTHER" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'templates'">
          <el-form-item label="模板名称" required>
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="模板类型" required>
            <el-input
              v-model="form.templateType"
              :disabled="Boolean(form.id)"
              placeholder="例如 DATA_PROCESSING"
            />
          </el-form-item>
          <el-form-item label="模板正文" required>
            <el-input v-model="form.content" :rows="12" type="textarea" />
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'instances'">
          <el-form-item label="所属组织" required>
            <el-select
              v-model="form.orgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="协议模板" required>
            <el-select v-model="form.templateId" class="w-full" filterable>
              <el-option
                v-for="item in templates"
                :key="item.id"
                :label="`${item.name} · v${item.version}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="失效时间">
            <el-date-picker
              v-model="form.expiredAt"
              class="w-full"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
        </template>

        <template v-else-if="activeKind === 'authorizations'">
          <el-form-item label="授权方" required>
            <el-select
              v-model="form.grantorOrgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="被授权方" required>
            <el-select
              v-model="form.granteeOrgId"
              :disabled="Boolean(form.id)"
              class="w-full"
              filterable
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="授权类型" required>
            <el-input
              v-model="form.authorizationType"
              placeholder="例如 REPORT_READ"
            />
          </el-form-item>
          <el-form-item label="授权范围">
            <el-input
              v-model="form.scope"
              :rows="3"
              placeholder="JSON 或约定范围编码"
              type="textarea"
            />
          </el-form-item>
          <el-form-item label="有效期">
            <el-date-picker
              v-model="form.dateRange"
              end-placeholder="结束日期"
              range-separator="至"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD"
              @change="applyDateRange"
            />
          </el-form-item>
        </template>
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
