<script lang="ts" setup>
import type { UploadFile } from 'element-plus';

import type { BaseRecord } from '#/api/business';
import type {
  QualificationDocument,
  QualificationReviewLog,
} from '#/api/commercial';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  activateAgreementTemplate,
  changeAdAccountStatus,
  changeAdvertiserStatus,
  changeAgreementInstanceStatus,
  changeOrganizationStatus,
  createAgreementInstance,
  createAgreementTemplate,
  createDataAuthorization,
  createQualificationCase,
  downloadQualificationDocument,
  getAdAccounts,
  getAdvertisers,
  getAgencyRelations,
  getAgreementInstances,
  getAgreementTemplates,
  getDataAuthorizations,
  getOrganizations,
  getOrganizationScopes,
  getPublishingEligibility,
  getQualificationCases,
  getQualificationDocuments,
  getQualificationReviewLogs,
  grantOrganizationScope,
  reviewQualification,
  revokeAgencyRelation,
  revokeDataAuthorization,
  revokeOrganizationScope,
  saveAdAccount,
  saveAdvertiser,
  saveAgencyRelation,
  saveOrganization,
  submitQualification,
  suspendQualification,
  uploadQualificationDocument,
} from '#/api/commercial';

import '../business-domain.css';

defineOptions({ name: 'CommercialWorkspace' });

type Row = BaseRecord & Record<string, any>;
type SectionKey =
  | 'ad-accounts'
  | 'advertisers'
  | 'agencies'
  | 'authorizations'
  | 'eligibility'
  | 'instances'
  | 'organizations'
  | 'qualifications'
  | 'scopes'
  | 'templates';
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
const organizations = ref<Row[]>([]);
const templates = ref<Row[]>([]);
const drawer = ref(false);
const qualificationDrawer = ref(false);
const form = reactive<Record<string, any>>({});
const qualificationDocuments = ref<QualificationDocument[]>([]);
const qualificationLogs = ref<QualificationReviewLog[]>([]);
const selectedQualification = ref<Row>();
const uploadFile = ref<File>();

const sectionKey = computed<SectionKey>(() => {
  const leaf = route.path.split('/').findLast(Boolean);
  if (leaf === 'entities') {
    const tab = String(route.query.tab || 'organizations');
    return ['advertisers', 'agencies', 'organizations', 'scopes'].includes(tab)
      ? (tab as SectionKey)
      : 'organizations';
  }
  if (leaf === 'compliance') {
    const tab = String(route.query.tab || 'qualifications');
    return [
      'authorizations',
      'eligibility',
      'instances',
      'qualifications',
      'templates',
    ].includes(tab)
      ? (tab as SectionKey)
      : 'qualifications';
  }
  return (leaf as SectionKey) || 'organizations';
});

const sections: Record<
  SectionKey,
  {
    action?: string;
    columns: Column[];
    description: string;
    eyebrow: string;
    permission: string;
    title: string;
  }
> = {
  organizations: {
    action: '新增组织',
    columns: [
      { label: '组织名称', prop: 'name' },
      { label: '组织类型', prop: 'orgType' },
      { label: '联系人', prop: 'contactName' },
      { label: '联系电话', prop: 'contactPhone' },
    ],
    description: '维护内部业务单元、直客与代理组织，并作为数据隔离边界。',
    eyebrow: 'COMMERCIAL · SCOPE',
    permission: 'commercial:organization:write',
    title: '商业主体',
  },
  advertisers: {
    action: '新增广告主',
    columns: [
      { label: '广告主名称', prop: 'advertiserName' },
      { label: '所属组织', prop: 'orgId' },
      { label: '行业', prop: 'industry' },
      { label: '网站', prop: 'websiteUrl' },
    ],
    description: '创建广告主组织与档案，维护从草稿到归档的完整状态。',
    eyebrow: 'COMMERCIAL · ADVERTISER',
    permission: 'commercial:advertiser:write',
    title: '商业主体',
  },
  agencies: {
    action: '新增代理关系',
    columns: [
      { label: '代理组织', prop: 'parentAgencyOrgId' },
      { label: '被代理组织', prop: 'childOrgId' },
      { label: '关系类型', prop: 'relationType' },
      { label: '授权范围', prop: 'authorizedScopes' },
      { label: '有效期', prop: 'dateRange' },
    ],
    description: '维护代理范围和有效期；修改关系不会再隐式撤销。',
    eyebrow: 'COMMERCIAL · AGENCY',
    permission: 'commercial:agency:write',
    title: '商业主体',
  },
  scopes: {
    action: '授予数据范围',
    columns: [
      { label: '用户 ID', prop: 'userId' },
      { label: '组织 ID', prop: 'subjectId' },
      { label: '范围', prop: 'scopeType' },
    ],
    description: '按用户和组织查询、授予或撤销数据范围。',
    eyebrow: 'COMMERCIAL · DATA SCOPE',
    permission: 'commercial:organization-scope:write',
    title: '商业主体',
  },
  'ad-accounts': {
    action: '新增广告账户',
    columns: [
      { label: '账户名称', prop: 'accountName' },
      { label: '广告主组织', prop: 'advertiserOrgId' },
      { label: '责任组织', prop: 'responsibleOrgId' },
      { label: '返点组织', prop: 'rebateOrgId' },
      { label: '退款组织', prop: 'refundOrgId' },
      { label: '币种 / 时区', prop: 'currencyTimezone' },
    ],
    description: '维护投放账户、组织责任链，并在激活前检查发布资格。',
    eyebrow: 'COMMERCIAL · ACCOUNT',
    permission: 'commercial:ad-account:write',
    title: '广告账户',
  },
  qualifications: {
    action: '创建资质案件',
    columns: [
      { label: '组织', prop: 'orgId' },
      { label: '资质类型', prop: 'caseType' },
      { label: '提交时间', prop: 'submittedAt' },
      { label: '有效期', prop: 'expiredAt' },
    ],
    description: '受控上传资质材料，保留不可变审核日志和完整状态流转。',
    eyebrow: 'COMMERCIAL · COMPLIANCE',
    permission: 'commercial:qualification:write',
    title: '合规中心',
  },
  templates: {
    action: '新增协议模板',
    columns: [
      { label: '模板名称', prop: 'name' },
      { label: '模板类型', prop: 'templateType' },
      { label: '版本', prop: 'version' },
    ],
    description: '独立维护协议模板及启用状态。',
    eyebrow: 'COMMERCIAL · AGREEMENT TEMPLATE',
    permission: 'commercial:agreement:write',
    title: '合规中心',
  },
  instances: {
    action: '创建协议实例',
    columns: [
      { label: '组织', prop: 'orgId' },
      { label: '模板', prop: 'templateId' },
      { label: '模板版本', prop: 'templateVersion' },
      { label: '签署时间', prop: 'signedAt' },
      { label: '到期时间', prop: 'expiredAt' },
    ],
    description: '协议实例与模板分开呈现，支持签署和终止。',
    eyebrow: 'COMMERCIAL · AGREEMENT INSTANCE',
    permission: 'commercial:agreement:write',
    title: '合规中心',
  },
  authorizations: {
    action: '新增数据授权',
    columns: [
      { label: '授权方', prop: 'grantorOrgId' },
      { label: '被授权方', prop: 'granteeOrgId' },
      { label: '授权类型', prop: 'authorizationType' },
      { label: '授权范围', prop: 'scope' },
    ],
    description: '独立维护组织间的数据授权及撤销记录。',
    eyebrow: 'COMMERCIAL · DATA AUTHORIZATION',
    permission: 'commercial:data-authorization:write',
    title: '合规中心',
  },
  eligibility: {
    columns: [
      { label: '账户名称', prop: 'accountName' },
      { label: '广告主组织', prop: 'advertiserOrgId' },
      { label: '检查结果', prop: 'eligibilityText' },
      { label: '缺口', prop: 'eligibilityReasons' },
    ],
    description: '检查广告主、资质、协议和授权是否满足发布前置条件。',
    eyebrow: 'COMMERCIAL · PUBLISHING ELIGIBILITY',
    permission: 'commercial:ad-account:read',
    title: '合规中心',
  },
};

const section = computed(() => sections[sectionKey.value]);
const statusOptions = computed(() => {
  const options: Partial<Record<SectionKey, [string, string][]>> = {
    'ad-accounts': [
      ['草稿', 'DRAFT'],
      ['已激活', 'ACTIVE'],
      ['已暂停', 'SUSPENDED'],
      ['已关闭', 'CLOSED'],
    ],
    advertisers: [
      ['草稿', 'DRAFT'],
      ['已激活', 'ACTIVE'],
      ['已暂停', 'SUSPENDED'],
      ['已归档', 'ARCHIVED'],
    ],
    agencies: [
      ['生效', 'ACTIVE'],
      ['已撤销', 'REVOKED'],
    ],
    authorizations: [
      ['生效', 'ACTIVE'],
      ['已撤销', 'REVOKED'],
    ],
    instances: [
      ['草稿', 'DRAFT'],
      ['已签署', 'SIGNED'],
      ['已终止', 'TERMINATED'],
    ],
    organizations: [
      ['启用', 'ACTIVE'],
      ['停用', 'DISABLED'],
    ],
    qualifications: [
      ['草稿', 'DRAFT'],
      ['已提交', 'SUBMITTED'],
      ['已通过', 'APPROVED'],
      ['已驳回', 'REJECTED'],
      ['需补材料', 'SUPPLEMENT_REQUIRED'],
      ['已暂停', 'SUSPENDED'],
    ],
    templates: [
      ['草稿', 'DRAFT'],
      ['已启用', 'ACTIVE'],
    ],
  };
  return options[sectionKey.value] || [];
});

async function loadOptions() {
  const [orgPage, templatePage] = await Promise.all([
    getOrganizations({ current: 1, size: 300 }),
    getAgreementTemplates({ current: 1, size: 300 }),
  ]);
  organizations.value = orgPage.records as Row[];
  templates.value = templatePage.records as Row[];
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
    switch (sectionKey.value) {
      case 'ad-accounts':
      case 'eligibility': {
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
    rows.value = (result.records as Row[]).map((row) => ({
      ...row,
      currencyTimezone:
        row.currency || row.timezone
          ? `${row.currency || '—'} · ${row.timezone || '—'}`
          : '—',
      dateRange:
        row.startDate || row.endDate
          ? `${row.startDate || '不限'} 至 ${row.endDate || '不限'}`
          : '不限',
    }));
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function clearForm() {
  Object.keys(form).forEach((key) => Reflect.deleteProperty(form, key));
}

function open(row?: Row) {
  clearForm();
  Object.assign(form, row || defaults(sectionKey.value));
  drawer.value = true;
}

function defaults(key: SectionKey) {
  switch (key) {
    case 'ad-accounts': {
      return { currency: 'CNY', status: 'DRAFT', timezone: 'Asia/Shanghai' };
    }
    case 'advertisers': {
      return { orgType: 'DIRECT_ADVERTISER', status: 'DRAFT' };
    }
    case 'agencies': {
      return { relationType: 'DIRECT', status: 'ACTIVE' };
    }
    case 'authorizations': {
      return { authorizationType: 'ATTRIBUTION', status: 'ACTIVE' };
    }
    case 'instances': {
      return { status: 'DRAFT' };
    }
    case 'organizations': {
      return { orgType: 'BUSINESS_UNIT', status: 'ACTIVE' };
    }
    case 'qualifications': {
      return { caseType: 'ADVERTISER_QUALIFICATION', status: 'DRAFT' };
    }
    case 'scopes': {
      return { scopeType: 'READ' };
    }
    case 'templates': {
      return { templateType: 'ADVERTISING', status: 'DRAFT' };
    }
    default: {
      return {};
    }
  }
}

async function submit() {
  saving.value = true;
  try {
    switch (sectionKey.value) {
      case 'ad-accounts': {
        await saveAdAccount(form);
        break;
      }
      case 'advertisers': {
        await saveAdvertiser(form);
        break;
      }
      case 'agencies': {
        await saveAgencyRelation(form);
        break;
      }
      case 'authorizations': {
        await createDataAuthorization({
          ...form,
          granteeOrganizationId: form.granteeOrgId,
          grantorOrganizationId: form.grantorOrgId,
        });
        break;
      }
      case 'instances': {
        await createAgreementInstance({
          expiredAt: form.expiredAt,
          organizationId: form.orgId,
          templateId: form.templateId,
        });
        break;
      }
      case 'organizations': {
        await saveOrganization(form);
        break;
      }
      case 'qualifications': {
        await createQualificationCase({
          caseType: form.caseType,
          orgId: form.orgId,
        });
        break;
      }
      case 'scopes': {
        await grantOrganizationScope({
          organizationId: form.organizationId,
          scopeType: form.scopeType,
          userId: form.userId,
        });
        break;
      }
      case 'templates': {
        await createAgreementTemplate(form);
        break;
      }
      default: {
        return;
      }
    }
    ElMessage.success('保存成功');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function updateStatus(row: Row, next: string) {
  await ElMessageBox.confirm(`确认将状态调整为 ${next}？`, '状态确认');
  switch (sectionKey.value) {
    case 'ad-accounts': {
      if (next === 'ACTIVE') {
        const eligibility = await getPublishingEligibility(row.id);
        if (!eligibility.eligible) {
          ElMessage.warning(
            `尚不具备发布资格：${eligibility.reasonCodes.join('、')}`,
          );
          return;
        }
      }
      await changeAdAccountStatus(row.id, next);

      break;
    }
    case 'advertisers': {
      await changeAdvertiserStatus(row.id, next);
      break;
    }
    case 'organizations': {
      await changeOrganizationStatus(row.id, next);
      break;
    }
    // No default
  }
  ElMessage.success('状态已更新');
  await load();
}

async function review(row: Row, decision: 'APPROVE' | 'REJECT') {
  let comment: string | undefined;
  if (decision === 'REJECT') {
    const result = await ElMessageBox.prompt('请输入驳回原因', '驳回资质', {
      inputValidator: (value) => Boolean(value?.trim()) || '驳回原因不能为空',
    });
    comment = result.value;
  }
  await reviewQualification(row.id, decision, comment);
  ElMessage.success(decision === 'APPROVE' ? '资质已通过' : '资质已驳回');
  await load();
}

async function suspend(row: Row) {
  const result = await ElMessageBox.prompt('请输入暂停原因', '暂停资质', {
    inputValidator: (value) => Boolean(value?.trim()) || '暂停原因不能为空',
  });
  await suspendQualification(row.id, result.value);
  ElMessage.success('资质已暂停');
  await load();
}

async function showQualification(row: Row) {
  selectedQualification.value = row;
  qualificationDrawer.value = true;
  uploadFile.value = undefined;
  [qualificationDocuments.value, qualificationLogs.value] = await Promise.all([
    getQualificationDocuments(row.id),
    getQualificationReviewLogs(row.id),
  ]);
}

function selectUpload(file: UploadFile) {
  uploadFile.value = file.raw;
}

async function uploadDocument() {
  if (!selectedQualification.value || !uploadFile.value) {
    ElMessage.warning('请选择需要上传的资质文件');
    return;
  }
  await uploadQualificationDocument(
    selectedQualification.value.id,
    'SUPPORTING_DOCUMENT',
    uploadFile.value,
  );
  ElMessage.success('资质文件已上传');
  await showQualification(selectedQualification.value);
}

async function downloadDocument(document: QualificationDocument) {
  const blob = await downloadQualificationDocument(document.id);
  const url = URL.createObjectURL(blob);
  const anchor = window.document.createElement('a');
  anchor.href = url;
  anchor.download = document.originalFileName || document.docName;
  anchor.click();
  URL.revokeObjectURL(url);
}

async function checkEligibility(row: Row) {
  const result = await getPublishingEligibility(row.id);
  row.eligibilityText = result.eligible ? '可发布' : '存在缺口';
  row.eligibilityReasons = result.reasonCodes.join('、') || '—';
}

async function rowAction(row: Row, action: string) {
  switch (action) {
    case 'activate-template': {
      await activateAgreementTemplate(row.id);
      break;
    }
    case 'revoke-agency': {
      await revokeAgencyRelation(row.id);
      break;
    }
    case 'revoke-authorization': {
      await revokeDataAuthorization(row.id);
      break;
    }
    case 'revoke-scope': {
      await revokeOrganizationScope(row.id, row.subjectId);
      break;
    }
    case 'sign-instance': {
      await changeAgreementInstanceStatus(row.id, 'sign');
      break;
    }
    case 'terminate-instance': {
      await changeAgreementInstanceStatus(row.id, 'terminate');
      break;
    }
  }
  ElMessage.success('操作成功');
  await load();
}

watch(
  [() => route.path, () => route.query.tab],
  () => {
    current.value = 1;
    keyword.value = '';
    status.value = '';
    void Promise.all([load(), loadOptions()]);
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
        v-if="section.action"
        v-access:code="section.permission"
        type="primary"
        @click="open()"
      >
        {{ section.action }}
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
            v-if="statusOptions.length > 0"
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 160px"
          >
            <el-option
              v-for="[label, value] in statusOptions"
              :key="value"
              :label="label"
              :value="value"
            />
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
        <el-table-column
          v-if="sectionKey !== 'eligibility' && sectionKey !== 'scopes'"
          label="状态"
          width="120"
        >
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.status || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="280">
          <template #default="{ row }">
            <template v-if="sectionKey === 'organizations'">
              <el-button link type="primary" @click="open(row)">编辑</el-button>
              <el-button
                link
                @click="
                  updateStatus(
                    row,
                    row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                  )
                "
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
            <template v-else-if="sectionKey === 'advertisers'">
              <el-button link type="primary" @click="open(row)">编辑</el-button>
              <el-button
                v-if="row.status !== 'ACTIVE'"
                link
                @click="updateStatus(row, 'ACTIVE')"
                >激活</el-button
              >
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                @click="updateStatus(row, 'SUSPENDED')"
                >暂停</el-button
              >
              <el-button
                v-if="row.status !== 'ARCHIVED'"
                link
                @click="updateStatus(row, 'ARCHIVED')"
                >归档</el-button
              >
            </template>
            <template v-else-if="sectionKey === 'agencies'">
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                type="primary"
                @click="open(row)"
                >修改</el-button
              >
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                type="danger"
                @click="rowAction(row, 'revoke-agency')"
                >撤销</el-button
              >
            </template>
            <template v-else-if="sectionKey === 'ad-accounts'">
              <el-button link @click="checkEligibility(row)"
                >发布检查</el-button
              >
              <el-button link type="primary" @click="open(row)">编辑</el-button>
              <el-button
                v-if="row.status !== 'ACTIVE'"
                link
                @click="updateStatus(row, 'ACTIVE')"
                >激活</el-button
              >
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                @click="updateStatus(row, 'SUSPENDED')"
                >暂停</el-button
              >
            </template>
            <template v-else-if="sectionKey === 'qualifications'">
              <el-button link @click="showQualification(row)"
                >材料与日志</el-button
              >
              <el-button
                v-if="['DRAFT', 'SUPPLEMENT_REQUIRED'].includes(row.status)"
                link
                type="primary"
                @click="submitQualification(row.id).then(load)"
                >提交</el-button
              >
              <el-button
                v-if="
                  ['SUBMITTED', 'AUTO_CHECKING', 'MANUAL_REVIEW'].includes(
                    row.status,
                  )
                "
                link
                type="success"
                @click="review(row, 'APPROVE')"
                >通过</el-button
              >
              <el-button
                v-if="
                  ['SUBMITTED', 'AUTO_CHECKING', 'MANUAL_REVIEW'].includes(
                    row.status,
                  )
                "
                link
                type="danger"
                @click="review(row, 'REJECT')"
                >驳回</el-button
              >
              <el-button
                v-if="row.status === 'APPROVED'"
                link
                type="warning"
                @click="suspend(row)"
                >暂停</el-button
              >
            </template>
            <template v-else-if="sectionKey === 'templates'">
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="primary"
                @click="rowAction(row, 'activate-template')"
                >启用</el-button
              >
            </template>
            <template v-else-if="sectionKey === 'instances'">
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="primary"
                @click="rowAction(row, 'sign-instance')"
                >签署</el-button
              >
              <el-button
                v-if="row.status === 'SIGNED'"
                link
                type="danger"
                @click="rowAction(row, 'terminate-instance')"
                >终止</el-button
              >
            </template>
            <el-button
              v-else-if="
                sectionKey === 'authorizations' && row.status === 'ACTIVE'
              "
              link
              type="danger"
              @click="rowAction(row, 'revoke-authorization')"
              >撤销</el-button
            >
            <el-button
              v-else-if="sectionKey === 'scopes'"
              link
              type="danger"
              @click="rowAction(row, 'revoke-scope')"
              >撤销</el-button
            >
            <el-button
              v-else-if="sectionKey === 'eligibility'"
              link
              type="primary"
              @click="checkEligibility(row)"
              >立即检查</el-button
            >
          </template>
        </el-table-column>
        <template #empty>
          <div class="domain-empty">当前筛选条件下暂无数据</div>
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

    <el-drawer v-model="drawer" :title="section.action || '编辑'" size="560px">
      <el-form label-position="top" :model="form">
        <template v-if="sectionKey === 'organizations'">
          <el-form-item label="组织名称" required
            ><el-input v-model="form.name"
          /></el-form-item>
          <el-form-item label="组织类型" required>
            <el-select v-model="form.orgType" class="w-full">
              <el-option label="内部业务单元" value="BUSINESS_UNIT" />
              <el-option label="直客广告主" value="DIRECT_ADVERTISER" />
              <el-option label="代理公司" value="AGENCY" />
              <el-option label="代运营广告主" value="MANAGED_ADVERTISER" />
            </el-select>
          </el-form-item>
          <el-form-item label="联系人"
            ><el-input v-model="form.contactName"
          /></el-form-item>
          <el-form-item label="联系电话"
            ><el-input v-model="form.contactPhone"
          /></el-form-item>
          <el-form-item label="联系邮箱"
            ><el-input v-model="form.contactEmail"
          /></el-form-item>
        </template>
        <template v-else-if="sectionKey === 'advertisers'">
          <el-form-item v-if="!form.id" label="组织名称" required
            ><el-input v-model="form.orgName"
          /></el-form-item>
          <el-form-item v-if="!form.id" label="组织类型" required>
            <el-select v-model="form.orgType" class="w-full">
              <el-option label="直客广告主" value="DIRECT_ADVERTISER" />
              <el-option label="代运营广告主" value="MANAGED_ADVERTISER" />
            </el-select>
          </el-form-item>
          <el-form-item label="广告主名称" required
            ><el-input v-model="form.advertiserName"
          /></el-form-item>
          <el-form-item label="行业"
            ><el-input v-model="form.industry"
          /></el-form-item>
          <el-form-item label="网站"
            ><el-input v-model="form.websiteUrl"
          /></el-form-item>
        </template>
        <template v-else-if="sectionKey === 'agencies'">
          <el-form-item label="代理组织" required>
            <el-select
              v-model="form.parentAgencyOrgId"
              class="w-full"
              filterable
              :disabled="Boolean(form.id)"
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="被代理组织" required>
            <el-select
              v-model="form.childOrgId"
              class="w-full"
              filterable
              :disabled="Boolean(form.id)"
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关系类型"
            ><el-input v-model="form.relationType"
          /></el-form-item>
          <el-form-item label="授权范围"
            ><el-input v-model="form.authorizedScopes" type="textarea"
          /></el-form-item>
          <el-form-item label="有效期">
            <el-date-picker
              v-model="form.dateRange"
              class="!w-full"
              type="daterange"
              value-format="YYYY-MM-DD"
              @change="
                (value: string[]) => {
                  form.startDate = value?.[0];
                  form.endDate = value?.[1];
                }
              "
            />
          </el-form-item>
        </template>
        <template v-else-if="sectionKey === 'ad-accounts'">
          <el-form-item label="广告主组织" required>
            <el-select
              v-model="form.advertiserOrgId"
              class="w-full"
              filterable
              :disabled="Boolean(form.id)"
            >
              <el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="账户名称" required
            ><el-input v-model="form.accountName"
          /></el-form-item>
          <el-form-item label="责任组织"
            ><el-select v-model="form.responsibleOrgId" class="w-full" clearable
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="返点组织"
            ><el-select v-model="form.rebateOrgId" class="w-full" clearable
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="退款组织"
            ><el-select v-model="form.refundOrgId" class="w-full" clearable
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <div class="grid grid-cols-2 gap-4">
            <el-form-item label="币种"
              ><el-input v-model="form.currency"
            /></el-form-item>
            <el-form-item label="时区"
              ><el-input v-model="form.timezone"
            /></el-form-item>
          </div>
        </template>
        <template v-else-if="sectionKey === 'qualifications'">
          <el-form-item label="组织" required
            ><el-select v-model="form.orgId" class="w-full" filterable
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="资质类型" required
            ><el-input v-model="form.caseType"
          /></el-form-item>
        </template>
        <template v-else-if="sectionKey === 'templates'">
          <el-form-item label="模板名称" required
            ><el-input v-model="form.name"
          /></el-form-item>
          <el-form-item label="模板类型" required
            ><el-input v-model="form.templateType"
          /></el-form-item>
          <el-form-item label="模板内容" required
            ><el-input v-model="form.content" :rows="10" type="textarea"
          /></el-form-item>
        </template>
        <template v-else-if="sectionKey === 'instances'">
          <el-form-item label="协议模板" required
            ><el-select v-model="form.templateId" class="w-full"
              ><el-option
                v-for="item in templates.filter(
                  (item) => item.status === 'ACTIVE',
                )"
                :key="item.id"
                :label="`${item.name} · v${item.version}`"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="组织" required
            ><el-select v-model="form.orgId" class="w-full"
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="到期时间"
            ><el-date-picker
              v-model="form.expiredAt"
              class="!w-full"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
          /></el-form-item>
        </template>
        <template v-else-if="sectionKey === 'authorizations'">
          <el-form-item label="授权方" required
            ><el-select v-model="form.grantorOrgId" class="w-full"
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="被授权方" required
            ><el-select v-model="form.granteeOrgId" class="w-full"
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="授权类型" required
            ><el-input v-model="form.authorizationType"
          /></el-form-item>
          <el-form-item label="授权范围"
            ><el-input v-model="form.scope" type="textarea"
          /></el-form-item>
        </template>
        <template v-else-if="sectionKey === 'scopes'">
          <el-form-item label="用户 ID" required
            ><el-input v-model="form.userId"
          /></el-form-item>
          <el-form-item label="组织" required
            ><el-select v-model="form.organizationId" class="w-full"
              ><el-option
                v-for="item in organizations"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="范围" required
            ><el-radio-group v-model="form.scopeType"
              ><el-radio value="READ">只读</el-radio
              ><el-radio value="WRITE">可写</el-radio></el-radio-group
            ></el-form-item
          >
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <el-drawer
      v-model="qualificationDrawer"
      size="720px"
      title="资质材料与审核日志"
    >
      <el-upload :auto-upload="false" :limit="1" @change="selectUpload">
        <el-button>选择资质文件</el-button>
        <template #tip
          ><div class="el-upload__tip">
            支持 PDF、PNG、JPG、Word，最大 20MB；文件只保存到系统默认存储。
          </div></template
        >
      </el-upload>
      <el-button class="mt-3" type="primary" @click="uploadDocument"
        >上传材料</el-button
      >
      <el-divider content-position="left">资质材料</el-divider>
      <el-table :data="qualificationDocuments">
        <el-table-column label="文件" prop="docName" min-width="220" />
        <el-table-column label="类型" prop="docType" width="180" />
        <el-table-column label="大小" prop="fileSize" width="120" />
        <el-table-column label="操作" width="100"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="downloadDocument(row)"
              >下载</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-divider content-position="left">不可变审核日志</el-divider>
      <el-timeline>
        <el-timeline-item
          v-for="log in qualificationLogs"
          :key="log.id"
          :timestamp="log.createdAt"
        >
          {{ log.fromStatus || '创建' }} → {{ log.toStatus }} ·
          {{ log.operatorName || 'system' }}
          <div v-if="log.comment" class="text-gray-500">{{ log.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </main>
</template>
