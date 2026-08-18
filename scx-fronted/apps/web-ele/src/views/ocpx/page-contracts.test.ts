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
        '用户名',
        '凭据引用',
        '请求地址',
        '宏参数',
        '充值',
        '扣减',
        '删除',
        '账号角色权限',
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
    ['deduction', ['规则编号', '推广链接', '媒体回传比例', '删除']],
    [
      'report',
      [
        '时段报表',
        'CTR',
        '广告主消费',
        '扣量放行',
        '扣量拦截',
        '完单',
        '首活',
        '首购',
        '订单金额',
      ],
    ],
    ['flow-log', ['流程日志', '事件 ID', 'HTTP', '耗时(ms)', '说明']],
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
    expect(source).toContain('不是网络层原始报文');
    expect(source).toContain('确认发送真实 Callback');
    expect(source).toContain('不会生成广告主转化');
    expect(source).toContain('不代表媒体已经完成业务入库');
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
          /v-if="canOperate \|\| canTest"[\s\S]*label="操作"/,
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
