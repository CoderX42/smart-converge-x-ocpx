import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const root = resolve(process.cwd(), 'apps/web-ele/src/views/ocpx');
const page = (name: string) =>
  readFileSync(resolve(root, name, 'index.vue'), 'utf8');

describe('vientiane-aligned OCPX page contracts', () => {
  it.each([
    [
      'advertiser',
      [
        '广告主编号',
        '上游协议',
        '用户名',
        '凭据引用',
        '请求地址',
        '宏参数',
        '充值',
        '扣减',
        '删除',
        '账号角色权限',
        '协议与域名',
      ],
    ],
    [
      'promotion-link',
      ['链接编号', '渠道号', '投放链接', '唤起链接', '扩展信息', '删除'],
    ],
    ['media', ['渠道 ID', '渠道名称', 'URI', '凭据引用', '宏参数', '删除']],
    [
      'monitor-task',
      [
        '监测编号',
        '业务编号',
        '目标转化事件',
        '展示监测链接',
        '点击监测链接',
        '删除',
      ],
    ],
    ['deduction', ['规则编号', '推广链接', '下游配置放行率', '删除']],
    [
      'report',
      [
        '时段报表',
        'CTR',
        '广告主消费',
        '上游 Callback',
        '下游回传提交',
        'SCX 扣量拦截',
        '上游完单',
        '上游首活',
        '上游首购',
        '订单金额',
      ],
    ],
    ['flow-log', ['流程日志', '事件 ID', 'HTTP', '耗时(ms)', '说明', '详情']],
  ])('%s exposes the agreed table and action labels', (name, labels) => {
    const source = page(name);
    for (const label of labels) expect(source).toContain(label);
    expect(source).toContain('v-model:visible-columns');
    expect(source).toContain('highlight-current-row');
    expect(source).toContain('PAGE_SIZES');
  });

  it.each(['advertiser', 'promotion-link', 'media', 'monitor-task'])(
    '%s no longer exposes status, archive, version or adapter operations',
    (name) => {
      const source = page(name);
      expect(source).not.toMatch(
        /expectedVersion|configVersion|切换状态|归档|Adapter/,
      );
    },
  );

  it('keeps generated monitoring URLs out of the monitor form', () => {
    const source = page('monitor-task');
    expect(source).toContain('展示和点击监测 URL 由后端生成');
    expect(source).not.toMatch(/v-model="form\.(showUrl|clkUrl)"/);
  });

  it('explains the media callback ratio as a downstream pass rate', () => {
    const deduction = page('deduction');
    expect(deduction).toContain('比例表示“下游回传放行率”');
    expect(deduction).toContain('上游回传 10 个 Callback');
    expect(deduction).toContain('向下游提交约 3');
    expect(deduction).toContain('扣量拦截约 7');
    expect(deduction).toContain('不是“扣除率”');
    expect(deduction).toContain('不撤销广告主计费');
    expect(deduction).toContain('不等于媒体已经签收');
    expect(deduction).toContain('ratioPreview.passed');
    expect(deduction).toContain('ratioPreview.deducted');
  });

  it('protects real monitor click testing and exposes traceable details', () => {
    const source = page('monitor-task');
    expect(source).toContain("'ocpx:monitor-task:test'");
    expect(source).toContain('测试链接');
    expect(source).toContain('确认开始完整测试');
    expect(source).toContain('会产生真实点击、上游流量和后续转化处理');
    expect(source).toContain('不可编辑的 SCX 测试 callback_url');
    expect(source).toContain('测试接收器已收到');
    expect(source).toContain('不代表合作方已经入库');
    expect(source).toContain('refreshTestResult(true)');
  });

  it('protects downstream media integration and states its evidence boundary', () => {
    const source = page('monitor-task');
    expect(source).toContain("'ocpx:monitor-task:integration'");
    expect(source).toContain('>联调</el-button');
    expect(source).toContain('下游请求明细（落库快照）');
    expect(source).toContain('ocpx-integration-detail-dialog');
    expect(source).toContain('table-layout: fixed');
    expect(source).toContain(
      '<LongText :value="integrationDetail.callbackUrl" wrap />',
    );
    expect(source).toContain('不是网络层原始报文');
    expect(source).toContain('row.manualCallbackAllowed');
    expect(source).toContain('完整测试 Callback 由 Engine 自动签名投递');
    expect(source).toContain(
      "integrationCallbackResult.resultStatus === 'SUCCESS'",
    );
    expect(source).toContain('Callback 请求失败，媒体接口返回 HTTP');
    expect(source).toContain('确认发送真实 Callback');
    expect(source).toContain('不会生成广告主转化');
    expect(source).toContain('不代表媒体已经完成业务入库');
  });

  it('shows flow-log results before normalized request data and explains its boundary', () => {
    const source = page('flow-log');
    expect(source).toContain("'ocpx:monitor-task:integration'");
    expect(source).toContain('getFlowLogRequestDetail(row.id)');
    expect(source).toContain('流程日志详情');
    expect(source).toContain('请求结果');
    expect(source).toContain('响应预览（已脱敏）');
    expect(source).toContain('最多 4096 字符预览');
    expect(source).toContain('下游请求数据');
    expect(source).toContain('请求参数快照尚未异步落库或已过期');
    expect(source).toContain('不包含原始请求 URL、签名或凭据');
    expect(source).toContain('requestDetail.partnerHttpStatus');
    expect(source).toContain('requestDetail.responsePreview');
    expect(source).toContain('requestDetail.model');
    expect(source).toContain('requestDetail.osVersion');
    expect(source).toContain('requestDetail.ipv6');
    expect(source).toContain('requestDetail.caid2');
  });

  it('keeps advertiser credentials create-only and resets every core dialog', () => {
    const advertiser = page('advertiser');
    expect(advertiser).toContain('请输入登录用户名');
    expect(advertiser).toContain('请输入登录密码');
    expect(advertiser).toContain('v-if="!editing"');
    expect(advertiser).toContain('username: form.username.trim()');
    expect(advertiser).not.toMatch(/updateAdvertiserV2\([^)]*password/);
    expect(advertiser).toContain('appendAdvertiserMacroParams(');
    expect(advertiser).toContain('<el-form-item label="宏参数"');
    expect(advertiser).not.toContain('DEFAULT_ADVERTISER_MACRO_PARAMS');
    for (const name of [
      'advertiser',
      'promotion-link',
      'media',
      'monitor-task',
    ]) {
      expect(page(name)).toContain(':close-on-click-modal="false"');
      expect(page(name)).toContain('@close="handleDialogClose"');
    }
  });

  it('manages database-backed protocols and exact host allowlists', () => {
    const advertiser = page('advertiser');
    expect(advertiser).toContain("'ocpx:protocol-config:manage'");
    expect(advertiser).toContain('getProtocolConfigs()');
    expect(advertiser).toContain('updateProtocolAllowedHosts(protocol');
    expect(advertiser).toContain('上游协议与允许域名');
    expect(advertiser).toContain('不含协议、端口、路径、通配符或 IP');
    expect(advertiser).toContain('一个广告主可以配置多个产品链接');
  });

  it('creates financial idempotency keys in browsers without randomUUID', () => {
    const advertiser = page('advertiser');
    expect(advertiser).toContain('createClientRequestId()');
    expect(advertiser).not.toContain('crypto.randomUUID()');
  });

  it('protects advertiser deductions with a dedicated permission and confirmation', () => {
    const advertiser = page('advertiser');
    expect(advertiser).toContain("'ocpx:advertiser:deduct'");
    expect(advertiser).toContain('deductAdvertiser(target.id');
    expect(advertiser).toContain('扣减金额不能超过当前余额');
    expect(advertiser).toContain('该操作会写入不可变账务流水');
    expect(advertiser).toContain('@close="handleDeductClose"');
  });

  it('clears invalid child filters and form values after parent changes', () => {
    const monitor = page('monitor-task');
    const dashboard = page('dashboard');
    const report = page('report');
    const promotion = page('promotion-link');
    expect(monitor).toContain("query.monitorTaskId = ''");
    expect(monitor).toContain("form.promotionLinkId = ''");
    expect(promotion).toContain("query.promotionLinkId = ''");
    expect(report).toContain("filters.monitorTaskId = ''");
    expect(report).toContain("filters.promotionLinkId = ''");
    expect(dashboard).toContain("filters.promotionLinkId = ''");
    expect(dashboard).toContain("filters.mediaId = ''");
    expect(dashboard).toContain("filters.monitorTaskId = ''");
  });

  it('distinguishes upstream callbacks from downstream delivery submissions', () => {
    const report = page('report');
    expect(report).toContain('下游配置放行率');
    expect(report).toContain('Callback 回传（上游 / 下游）');
    expect(report).toContain('row.callbackCount');
    expect(report).toContain('row.deductionPassedCount');
    expect(report).toContain('下游数表示 SCX 已提交投递，不代表媒体签收');
    expect(report).toContain('callback-direction-metric__line is-upstream');
    expect(report).toContain('callback-direction-metric__line is-downstream');
  });

  it('renders only server-computed dashboard cards and no ranking section', () => {
    const dashboard = page('dashboard');
    for (const metric of [
      'SPEND',
      'IMPRESSION',
      'CLICK',
      'CONVERSION',
      'CTR',
      'CVR',
      'CPM',
      'CPC',
      'CPA',
    ]) {
      expect(dashboard).toContain(`code: '${metric}'`);
    }
    expect(dashboard).toContain("metric?.status === 'OK'");
    expect(dashboard).not.toMatch(/数据排行|业务对象表现|ranking/i);
  });

  it('uses vientiane-style entity selects instead of keyword search', () => {
    expect(page('advertiser')).toContain('query.advertiserId');
    expect(page('advertiser')).toContain('请选择广告主');
    expect(page('promotion-link')).toContain('query.promotionLinkId');
    expect(page('promotion-link')).toContain('所属广告主');
    expect(page('promotion-link')).toContain('platformOptions');
    expect(page('media')).toContain('query.mediaId');
    expect(page('media')).toContain('请选择渠道');
    expect(page('monitor-task')).toContain('所属渠道');
    expect(page('monitor-task')).toContain('投放链接');
  });

  it('guards every page against stale list responses', () => {
    for (const name of [
      'advertiser',
      'dashboard',
      'deduction',
      'flow-log',
      'media',
      'monitor-task',
      'promotion-link',
      'report',
    ]) {
      const source = page(name);
      expect(source).toContain('createLatestRequestGuard');
      expect(source).toContain('loadGuard.begin()');
      expect(source).toContain('loadGuard.isLatest(requestSequence)');
    }
  });

  it('shows operation and export affordances only with matching codes', () => {
    for (const name of [
      'advertiser',
      'deduction',
      'media',
      'monitor-task',
      'promotion-link',
    ]) {
      const source = page(name);
      expect(source).toContain('const canOperate = computed');
      if (name === 'monitor-task') {
        expect(source).toMatch(
          /v-if="canOperate \|\| canTest \|\| canIntegrate"[\s\S]*label="操作"/,
        );
      } else {
        expect(source).toMatch(/v-if="canOperate"[\s\S]*label="操作"/);
      }
    }
    const report = page('report');
    expect(report).toContain("hasAccessByCodes(['ocpx:report:export'])");
    expect(report).toContain(':export-btn="canExport"');
  });

  it('guards advertiser and media delete actions with dedicated codes', () => {
    expect(page('advertiser')).toContain("'ocpx:advertiser:delete'");
    expect(page('advertiser')).toContain('deleteAdvertiserV2(row.id)');
    expect(page('advertiser')).toContain('无计费配置审计的广告主可以删除');
    expect(page('media')).toContain("'ocpx:media:delete'");
    expect(page('media')).toContain('deleteMedia(row.id)');
  });

  it('keeps shared advertiser role management global and protected', () => {
    const advertiser = page('advertiser');
    expect(advertiser).toContain("'ocpx:advertiser:role:manage'");
    expect(advertiser).toContain('getAdvertiserRolePermissions()');
    expect(advertiser).toContain('updateAdvertiserRolePermissions({');
    expect(advertiser).toContain('全量 OCPX 数据范围');
    expect(advertiser).toContain('需全量范围');
    expect(advertiser).toContain('保存并使会话失效');
  });
});
