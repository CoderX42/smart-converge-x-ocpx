import type { RtaProtocol } from '#/api/ocpx/rta';

import { describe, expect, it } from 'vitest';

import {
  bindingErrors,
  defaultMediaConfig,
  mediaErrors,
  upstreamErrors,
} from './validation';

const generic: RtaProtocol = {
  bindingFields: [
    { key: 'productId', label: '上游产品 ID', pattern: '.+' },
    { key: 'externalTaskId', label: '上游任务 ID', pattern: '.+' },
  ],
  code: 'GENERIC_ASYNC',
  configFields: [],
  credentialRequired: true,
  fixedEndpoint: '',
  maxCandidates: 100,
  mode: 'ASYNC_CALLBACK',
  name: '通用异步',
  retrySafe: true,
};
const weifeng: RtaProtocol = {
  ...generic,
  bindingFields: [],
  code: 'WEIFENG_RTA',
  configFields: [{ key: 'aid', label: '微风 aid', pattern: '[0-9]+' }],
  credentialRequired: false,
  fixedEndpoint: 'https://rta.example.test/api',
  mode: 'SYNC_HTTP',
};

describe('rTA media access form', () => {
  it('requires a notify URL only for the generic protocol', () => {
    const generic = { credentialRef: 'media_key', mediaId: 'm1' };
    expect(mediaErrors({ ...generic, protocol: 'GENERIC_ASYNC' })).toEqual([
      '请填写决策通知地址',
    ]);
    expect(
      mediaErrors({
        ...generic,
        notifyUrl: 'https://notify.example.test/rta?x=1',
        protocol: 'GENERIC_ASYNC',
      }),
    ).toEqual(['决策通知地址须为 https 地址，且不含查询参数']);
    expect(
      mediaErrors({
        ...generic,
        protocol: 'OPPO',
        protocolConfig: defaultMediaConfig('OPPO'),
      }),
    ).toEqual([]);
  });

  it('resets protocol parameters when switching protocol', () => {
    expect(defaultMediaConfig('GENERIC_ASYNC')).toEqual({});
    expect(defaultMediaConfig('OPPO')).toMatchObject({
      ownerMode: 'EMPTY',
      timeoutMs: '1000',
    });
    expect(
      mediaErrors({
        credentialRef: 'k',
        mediaId: 'm1',
        protocol: 'OPPO',
        protocolConfig: { ownerMode: 'VALUE', timeoutMs: '9000' },
      }),
    ).toEqual(['同步等待时限须为 100–5000 毫秒', '请填写 OPPO owner_id']);
    expect(mediaErrors({ protocol: 'OPPO' })).toContain('请选择媒体');
  });
});

describe('rTA upstream form', () => {
  it('validates credentials and protocol parameters per protocol', () => {
    const base = {
      advertiserId: 'a1',
      endpoint: 'https://rta.example.test/api',
      name: '接入',
    };
    expect(upstreamErrors(base, generic)).toEqual(['请填写服务端凭据引用']);
    expect(upstreamErrors(base, weifeng)).toEqual(['请正确填写微风 aid']);
    expect(
      upstreamErrors({ ...base, protocolConfig: { aid: '12' } }, weifeng),
    ).toEqual([]);
    expect(upstreamErrors(base, undefined)).toEqual(['请选择 RTA 协议']);
  });
});

describe('rTA task binding form', () => {
  it('requires mapping fields and blocks OPPO with non-Weifeng upstreams', () => {
    expect(bindingErrors({}, generic)).toEqual(['请选择广告主接入']);
    expect(bindingErrors({ upstreamId: 'u1' }, generic)).toEqual([
      '请填写上游产品 ID',
      '请填写上游任务 ID',
    ]);
    expect(
      bindingErrors(
        { externalTaskId: 'e', productId: 'p', upstreamId: 'u1' },
        generic,
        'OPPO',
      ),
    ).toEqual(['OPPO 媒体只能绑定微风 RTA 接入']);
    expect(
      bindingErrors({ oppoPkg: 'bad pkg', upstreamId: 'u1' }, weifeng, 'OPPO'),
    ).toEqual(['OPPO 应用包名格式无效']);
    expect(
      bindingErrors(
        { oppoPkg: 'com.example.app', upstreamId: 'u1' },
        weifeng,
        'OPPO',
      ),
    ).toEqual([]);
  });
});
