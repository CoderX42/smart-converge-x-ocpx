import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

const root = resolve(process.cwd(), 'apps/web-ele/src/views/ocpx');
const page = (name: string) =>
  readFileSync(resolve(root, name, 'index.vue'), 'utf8');

describe('vientiane-aligned OCPX page contracts', () => {
  it.each([
    ['advertiser', ['广告主编号', '凭据引用', '请求地址', '宏参数', '充值']],
    [
      'promotion-link',
      ['链接编号', '渠道号', '投放链接', '唤起链接', '扩展信息', '删除'],
    ],
    ['media', ['渠道 ID', '渠道名称', 'URI', '凭据引用', '宏参数']],
    [
      'monitor-task',
      ['监测编号', '业务编号', '展示监测链接', '点击监测链接', '删除'],
    ],
    ['deduction', ['规则编号', '推广链接', '扣量比例', '删除']],
    ['report', ['时段报表', 'CTR', '消耗', '完单']],
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

  it('clears invalid child filters and form values after parent changes', () => {
    const monitor = page('monitor-task');
    const report = page('report');
    const promotion = page('promotion-link');
    expect(monitor).toContain("query.monitorTaskId = ''");
    expect(monitor).toContain("form.promotionLinkId = ''");
    expect(promotion).toContain("query.promotionLinkId = ''");
    expect(report).toContain("filters.monitorTaskId = ''");
    expect(report).toContain("filters.promotionLinkId = ''");
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
});
