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
    ['deduction', ['规则编号', '推广链接', '扣量比例', '删除']],
    ['report', ['时段报表', 'CTR', '消耗', '完单', '首活', '首购', '订单金额']],
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
      expect(source).toMatch(/v-if="canOperate"[\s\S]*label="操作"/);
    }
    const report = page('report');
    expect(report).toContain("hasAccessByCodes(['ocpx:report:export'])");
    expect(report).toContain(':export-btn="canExport"');
  });

  it('guards advertiser and media delete actions with dedicated codes', () => {
    expect(page('advertiser')).toContain("'ocpx:advertiser:delete'");
    expect(page('advertiser')).toContain('deleteAdvertiserV2(row.id)');
    expect(page('advertiser')).toContain('无余额、无账务流水且无事件事实');
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
