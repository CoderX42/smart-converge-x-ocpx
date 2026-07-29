<script lang="ts" setup>
import type { BaseRecord } from '#/api/business';
import type { Organization } from '#/api/commercial';

import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { getOrganizations } from '#/api/commercial';
import {
  calculateRebate,
  closePeriod,
  createClosingPeriod,
  createInvoice,
  createReconciliation,
  createRefund,
  getBudgetReservations,
  getClosingPeriods,
  getCreditLines,
  getFinancialAccounts,
  getInvoices,
  getJournals,
  getRebates,
  getReconciliations,
  getRefunds,
  issueInvoice,
  recharge,
  reviewRefund,
  submitRefund,
} from '#/api/finance';

import '../business-domain.css';

defineOptions({ name: 'FinanceWorkspace' });

type Row = BaseRecord & Record<string, unknown>;
type Column = { label: string; money?: boolean; prop: string };

const route = useRoute();
const organizations = ref<Organization[]>([]);
const orgId = ref('');
const keyword = ref('');
const status = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const rows = ref<Row[]>([]);
const drawer = ref(false);
const createMode = ref<
  'closing' | 'invoice' | 'rebate' | 'recharge' | 'reconciliation' | 'refund'
>('recharge');
const rechargeForm = reactive({
  amount: 0,
  end: '',
  orgId: '',
  reason: '',
  remark: '',
  ruleId: '',
  start: '',
});

const sectionKey = computed(() => route.path.split('/').findLast(Boolean));
const sections: Record<
  string,
  { columns: Column[]; description: string; eyebrow: string; title: string }
> = {
  accounts: {
    columns: [
      { label: '账户类型', prop: 'accountType' },
      { label: '账面余额', money: true, prop: 'balance' },
      { label: '冻结金额', money: true, prop: 'frozenBalance' },
      { label: '可用余额', money: true, prop: 'availableBalance' },
    ],
    description: '以组织为结算边界查看 CNY 资金、冻结和可用余额。',
    eyebrow: 'FINANCE · LEDGER',
    title: '资金账户',
  },
  journals: {
    columns: [
      { label: '业务类型', prop: 'businessType' },
      { label: '金额', money: true, prop: 'amount' },
      { label: '幂等键', prop: 'idempotencyKey' },
      { label: '发生时间', prop: 'createTime' },
    ],
    description: '追踪每笔业务流水及其借贷分录，确保账务可解释、可核对。',
    eyebrow: 'FINANCE · JOURNAL',
    title: '财务流水与借贷明细',
  },
  'credit-budget': {
    columns: [
      { label: '授信额度', money: true, prop: 'creditLimit' },
      { label: '已用额度', money: true, prop: 'usedAmount' },
      { label: '预算金额', money: true, prop: 'amount' },
      { label: '已消耗', money: true, prop: 'consumedAmount' },
    ],
    description: '并列查看授信额度和 Campaign 预算预留，避免超额投放。',
    eyebrow: 'FINANCE · CREDIT',
    title: '授信与预算',
  },
  refunds: {
    columns: [
      { label: '退款金额', money: true, prop: 'amount' },
      { label: '申请人', prop: 'makerId' },
      { label: '审批人', prop: 'checkerId' },
      { label: '原因', prop: 'refundReason' },
    ],
    description: '执行退款申请、复核与冲销，经办人与审批人严格分离。',
    eyebrow: 'FINANCE · REFUND',
    title: '退款管理',
  },
  rebates: {
    columns: [
      { label: '规则', prop: 'ruleId' },
      { label: '周期开始', prop: 'periodStart' },
      { label: '周期结束', prop: 'periodEnd' },
      { label: '返点金额', money: true, prop: 'rebateAmount' },
    ],
    description: '管理返点规则版本与周期计算结果。',
    eyebrow: 'FINANCE · REBATE',
    title: '返点管理',
  },
  invoices: {
    columns: [
      { label: '开票金额', money: true, prop: 'invoiceAmount' },
      { label: '发票号码', prop: 'invoiceNo' },
    ],
    description: '跟踪开票申请、开具结果和组织归属。',
    eyebrow: 'FINANCE · INVOICE',
    title: '发票管理',
  },
  reconciliation: {
    columns: [
      { label: '期间开始', prop: 'periodStart' },
      { label: '期间结束', prop: 'periodEnd' },
      { label: '差异金额', money: true, prop: 'difference' },
    ],
    description: '统一处理对账差异、期间关账和调整留痕。',
    eyebrow: 'FINANCE · CLOSING',
    title: '对账与关账',
  },
};
const section = computed(
  () => sections[sectionKey.value || ''] || sections.accounts!,
);

function money(value: unknown) {
  const amount = Number(value || 0);
  return new Intl.NumberFormat('zh-CN', {
    currency: 'CNY',
    style: 'currency',
  }).format(amount);
}

async function loadOrganizations() {
  const result = await getOrganizations({ current: 1, size: 200 });
  organizations.value = result.records;
  if (!orgId.value) orgId.value = result.records[0]?.id || '';
}

async function load() {
  if (!orgId.value) {
    rows.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const query = {
      current: current.value,
      keyword: keyword.value || undefined,
      orgId: orgId.value,
      size: size.value,
      status: status.value || undefined,
    };
    let result;
    switch (sectionKey.value) {
      case 'credit-budget': {
        const [credits, reservations] = await Promise.all([
          getCreditLines(query),
          getBudgetReservations(query),
        ]);
        result = {
          records: [...credits.records, ...reservations.records],
          total: credits.total + reservations.total,
        };
        break;
      }
      case 'invoices': {
        result = await getInvoices(query);
        break;
      }
      case 'journals': {
        result = await getJournals(query);
        break;
      }
      case 'rebates': {
        result = await getRebates(query);
        break;
      }
      case 'reconciliation': {
        const [reconciliations, closings] = await Promise.all([
          getReconciliations(query),
          getClosingPeriods(query),
        ]);
        result = {
          records: [...reconciliations.records, ...closings.records],
          total: reconciliations.total + closings.total,
        };
        break;
      }
      case 'refunds': {
        result = await getRefunds(query);
        break;
      }
      default: {
        result = await getFinancialAccounts(query);
      }
    }
    rows.value = result.records as Row[];
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function openRecharge() {
  createMode.value = 'recharge';
  rechargeForm.amount = 0;
  rechargeForm.orgId = orgId.value;
  rechargeForm.remark = '';
  drawer.value = true;
}

function openCreate(
  mode: 'closing' | 'invoice' | 'rebate' | 'reconciliation' | 'refund',
) {
  createMode.value = mode;
  rechargeForm.amount = 0;
  rechargeForm.end = '';
  rechargeForm.orgId = orgId.value;
  rechargeForm.reason = '';
  rechargeForm.ruleId = '';
  rechargeForm.start = '';
  drawer.value = true;
}

async function submitRecharge() {
  if (!rechargeForm.orgId) {
    ElMessage.warning('请选择结算组织');
    return;
  }
  saving.value = true;
  try {
    switch (createMode.value) {
    case 'invoice': {
      await createInvoice({
        amount: rechargeForm.amount,
        orgId: rechargeForm.orgId,
      });
    
    break;
    }
    case 'rebate': {
      await calculateRebate({
        end: rechargeForm.end,
        orgId: rechargeForm.orgId,
        ruleId: rechargeForm.ruleId,
        start: rechargeForm.start,
      });
    
    break;
    }
    case 'recharge': {
      if (rechargeForm.amount <= 0) throw new Error('金额必须大于 0');
      await recharge({
        amount: rechargeForm.amount,
        idempotencyKey: `WEB_RECHARGE_${Date.now()}`,
        orgId: rechargeForm.orgId,
        remark: rechargeForm.remark,
      });
    
    break;
    }
    case 'reconciliation': {
      await createReconciliation({
        end: rechargeForm.end,
        orgId: rechargeForm.orgId,
        start: rechargeForm.start,
      });
    
    break;
    }
    case 'refund': {
      await createRefund({
        amount: rechargeForm.amount,
        orgId: rechargeForm.orgId,
        reason: rechargeForm.reason,
      });
    
    break;
    }
    default: {
      await createClosingPeriod({
        end: rechargeForm.end,
        orgId: rechargeForm.orgId,
        start: rechargeForm.start,
      });
    }
    }
    ElMessage.success('财务操作已提交');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function submitDraftRefund(row: Row) {
  await ElMessageBox.confirm('确认提交该退款进入复核？', '提交退款');
  await submitRefund(row.id);
  ElMessage.success('退款已提交复核');
  await load();
}

async function closeClosingPeriod(row: Row) {
  await ElMessageBox.confirm('关账后原期间不可覆盖，确认继续？', '执行关账');
  await closePeriod(row.id, 0);
  ElMessage.success('期间已关账');
  await load();
}

async function decideRefund(row: Row, decision: 'APPROVE' | 'REJECT') {
  await ElMessageBox.confirm(
    `确认${decision === 'APPROVE' ? '批准' : '驳回'}该退款？`,
    '退款复核',
  );
  await reviewRefund(row.id, decision);
  ElMessage.success('退款审核已完成');
  await load();
}

async function markInvoiceIssued(row: Row) {
  await ElMessageBox.confirm('确认登记该发票为已开具？', '开票确认');
  await issueInvoice(row.id);
  ElMessage.success('发票已标记为开具');
  await load();
}

watch(
  () => route.path,
  () => {
    current.value = 1;
    void load();
  },
);
watch(orgId, () => {
  current.value = 1;
  void load();
});

loadOrganizations().then(load);
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
        v-if="sectionKey === 'accounts'"
        v-access:code="'finance:account:write'"
        type="primary"
        @click="openRecharge"
      >
        账户充值
      </el-button>
      <el-button
        v-else-if="sectionKey === 'refunds'"
        v-access:code="'finance:refund:write'"
        type="primary"
        @click="openCreate('refund')"
      >
        新建退款
      </el-button>
      <el-button
        v-else-if="sectionKey === 'rebates'"
        v-access:code="'finance:rebate:write'"
        type="primary"
        @click="openCreate('rebate')"
      >
        计算返点
      </el-button>
      <el-button
        v-else-if="sectionKey === 'invoices'"
        v-access:code="'finance:invoice:write'"
        type="primary"
        @click="openCreate('invoice')"
      >
        申请开票
      </el-button>
      <div v-else-if="sectionKey === 'reconciliation'" class="flex gap-2">
        <el-button
          v-access:code="'finance:reconciliation:write'"
          @click="openCreate('reconciliation')"
        >
          发起对账
        </el-button>
        <el-button
          v-access:code="'finance:closing:write'"
          type="primary"
          @click="openCreate('closing')"
        >
          新建关账期间
        </el-button>
      </div>
    </header>

    <el-card class="domain-card" shadow="never">
      <div class="domain-toolbar">
        <div class="domain-filters">
          <el-select
            v-model="orgId"
            filterable
            placeholder="选择结算组织"
            style="width: 240px"
          >
            <el-option
              v-for="item in organizations"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="流水号或关键字"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option label="待处理" value="PENDING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="业务 ID" min-width="180">
          <template #default="{ row }">
            <span class="domain-code">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-for="column in section.columns"
          :key="column.prop"
          :label="column.label"
          min-width="150"
        >
          <template #default="{ row }">
            <span :class="{ 'domain-money': column.money }">
              {{ column.money ? money(row[column.prop]) : row[column.prop] || '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag effect="plain">
              {{ row.status || row.invoiceStatus || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="180">
          <template #default="{ row }">
            <template v-if="sectionKey === 'refunds' && row.status === 'SUBMITTED'">
              <el-button
                v-access:code="'finance:refund:review'"
                link
                type="success"
                @click="decideRefund(row, 'APPROVE')"
              >
                批准
              </el-button>
              <el-button
                v-access:code="'finance:refund:review'"
                link
                type="danger"
                @click="decideRefund(row, 'REJECT')"
              >
                驳回
              </el-button>
            </template>
            <el-button
              v-if="sectionKey === 'refunds' && row.status === 'DRAFT'"
              v-access:code="'finance:refund:write'"
              link
              type="primary"
              @click="submitDraftRefund(row)"
            >
              提交复核
            </el-button>
            <el-button
              v-if="
                sectionKey === 'invoices' &&
                (row.invoiceStatus === 'DRAFT' ||
                  row.invoiceStatus === 'SUBMITTED' ||
                  row.invoiceStatus === 'APPROVED')
              "
              v-access:code="'finance:invoice:write'"
              link
              type="primary"
              @click="markInvoiceIssued(row)"
            >
              登记开票
            </el-button>
            <el-button
              v-if="
                sectionKey === 'reconciliation' &&
                row.status === 'OPEN' &&
                row.diffThreshold !== undefined
              "
              v-access:code="'finance:closing:write'"
              link
              type="danger"
              @click="closeClosingPeriod(row)"
            >
              执行关账
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="domain-empty">
            {{ orgId ? '当前组织暂无财务数据' : '请先选择结算组织' }}
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
      :title="
        {
          closing: '新建关账期间',
          invoice: '申请开票',
          rebate: '计算返点',
          recharge: '账户充值',
          reconciliation: '发起对账',
          refund: '新建退款',
        }[createMode]
      "
      size="480px"
    >
      <el-alert
        :closable="false"
        show-icon
        title="金额币种固定为 CNY；所有操作保留审计与幂等记录。"
        type="info"
      />
      <el-form class="mt-5" label-position="top" :model="rechargeForm">
        <el-form-item label="结算组织" required>
          <el-select v-model="rechargeForm.orgId" class="w-full">
            <el-option
              v-for="item in organizations"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="['recharge', 'refund', 'invoice'].includes(createMode)"
          label="金额（CNY）"
          required
        >
          <el-input-number
            v-model="rechargeForm.amount"
            :min="0.01"
            :precision="2"
            class="!w-full"
          />
        </el-form-item>
        <el-form-item v-if="createMode === 'refund'" label="退款原因" required>
          <el-input v-model="rechargeForm.reason" :rows="3" type="textarea" />
        </el-form-item>
        <el-form-item v-if="createMode === 'rebate'" label="返点规则 ID" required>
          <el-input v-model="rechargeForm.ruleId" />
        </el-form-item>
        <template
          v-if="['rebate', 'reconciliation', 'closing'].includes(createMode)"
        >
          <el-form-item label="开始日期" required>
            <el-date-picker
              v-model="rechargeForm.start"
              class="!w-full"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="结束日期" required>
            <el-date-picker
              v-model="rechargeForm.end"
              class="!w-full"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
        </template>
        <el-form-item v-if="createMode === 'recharge'" label="备注">
          <el-input v-model="rechargeForm.remark" :rows="3" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submitRecharge">
          确认入账
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
