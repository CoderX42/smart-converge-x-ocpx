import type { MediaMacro } from '#/api/ocpx';

import { describe, expect, it } from 'vitest';

import {
  AUTO_MACRO,
  AUTO_MACRO_KEY,
  buildMediaMacroGroups,
  flattenMediaMacroItems,
  parseMediaMacroParams,
  serializeMediaMacroParams,
} from './media-macros';

const catalog: MediaMacro[] = [
  {
    attributionIdentity: true,
    group: '设备标识',
    key: 'caid',
    label: 'CAID',
    macro: '__CAID__',
    osSupport: 'IOS',
  },
  {
    attributionIdentity: true,
    group: '设备标识',
    key: 'caid_list',
    label: 'CAID 列表',
    macro: '__CAID_LIST__',
    osSupport: 'IOS',
  },
  {
    attributionIdentity: true,
    group: '设备标识',
    key: 'caid1',
    label: 'CAID1',
    macro: '__CAID1__',
    osSupport: 'IOS',
  },
  {
    attributionIdentity: true,
    group: '设备标识',
    key: 'caid2',
    label: 'CAID2',
    macro: '__CAID2__',
    osSupport: 'IOS',
  },
  {
    attributionIdentity: true,
    group: '设备标识',
    key: 'idfa',
    label: 'IDFA',
    macro: '__IDFA__',
    osSupport: 'IOS',
  },
  {
    attributionIdentity: true,
    group: '设备标识',
    key: 'idfa_md5',
    label: 'IDFA MD5',
    macro: '__IDFA_MD5__',
    osSupport: 'IOS',
  },
  {
    attributionIdentity: false,
    group: '平台路由',
    key: 'callback_url',
    label: '回调地址',
    macro: '__CALLBACK_URL__',
    osSupport: 'BOTH',
  },
  {
    attributionIdentity: false,
    group: '平台路由',
    key: AUTO_MACRO_KEY,
    label: '请求 ID',
    macro: AUTO_MACRO,
    osSupport: 'BOTH',
  },
];

describe('media macro canonicalisation', () => {
  it('hides CAID aliases and groups original/MD5 variants', () => {
    const groups = buildMediaMacroGroups([
      ...catalog,
      {
        attributionIdentity: false,
        group: '网络/终端',
        key: 'mts',
        label: '毫秒时间戳',
        macro: '__MTS__',
        osSupport: 'BOTH',
      },
    ]);
    const options = flattenMediaMacroItems(groups);

    expect(options.map((item) => item.key)).toEqual([
      'caid',
      'idfa',
      'idfa_md5',
      'callback_url',
    ]);
    expect(options.some((item) => item.key === 'mts')).toBe(false);
    expect(
      groups[0]?.items.find((item) => item.key === 'idfa')?.variants,
    ).toHaveLength(2);
  });

  it('preserves legacy CAID pairs while editing an old media', () => {
    const groups = buildMediaMacroGroups(catalog);
    const parsed = parseMediaMacroParams(
      'caid_list=__CAID_LIST__&caid1=__CAID1__&request_id=__REQUEST_ID__&foo=__FOO__',
      groups,
    );

    expect(parsed.selected).toEqual([]);
    expect(parsed.legacy).toEqual([
      'caid_list=__CAID_LIST__',
      'caid1=__CAID1__',
    ]);
    expect(parsed.custom).toBe('foo=__FOO__');
  });

  it('preserves the retired millisecond timestamp only for old media', () => {
    const groups = buildMediaMacroGroups(catalog);
    const parsed = parseMediaMacroParams('mts=__MTS__', groups);

    expect(parsed.legacy).toEqual(['mts=__MTS__']);
    expect(serializeMediaMacroParams(groups, [], parsed.legacy, '')).toBe(
      'mts=__MTS__',
    );
  });

  it('emits one canonical CAID and an automatic request id for new media', () => {
    const groups = buildMediaMacroGroups(catalog);
    const value = serializeMediaMacroParams(
      groups,
      ['caid', 'callback_url'],
      [],
      '',
    );

    expect(value).toBe('caid=__CAID__&callback_url=__CALLBACK_URL__');
  });
});
