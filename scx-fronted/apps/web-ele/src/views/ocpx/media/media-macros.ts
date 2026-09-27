import type { MediaMacro } from '#/api/ocpx';

/**
 * CAID_LIST and CAID1 are aliases understood by older media adapters. They
 * are deliberately not offered as choices for a new media: the canonical
 * public parameter is `caid` and the engine owns the compatibility mapping.
 */
export const CAID_ALIAS_KEYS = new Set(['caid1', 'caid_list']);

/** Fields with no canonical replacement yet. They remain an explicit,
 * opt-in compatibility extension and are preserved when editing old media. */
export const CAID_ADVANCED_KEYS = new Set(['caid2', 'last_caid', 'prev_caid']);

/** Deprecated aliases stay readable in old media but are hidden from new choices. */
export const RETIRED_MACRO_KEYS = new Set(['mts']);

export const AUTO_MACRO_KEY = 'request_id';
export const AUTO_MACRO = '__REQUEST_ID__';

const DEVICE_VARIANT_PAIRS: Record<string, string> = {
  androidid_md5: 'androidid',
  idfa_md5: 'idfa',
  imei_md5: 'imei',
  mac_md5: 'mac',
  oaid_md5: 'oaid',
};

export type MediaMacroOption = Pick<MediaMacro, 'key' | 'label' | 'macro'> & {
  attributionIdentity?: boolean;
  osSupport?: MediaMacro['osSupport'];
};

export type MediaMacroItem = MediaMacroOption & {
  /** Original and MD5 are one logical field with selectable variants. */
  variants?: MediaMacroOption[];
};

export type MediaMacroGroup = {
  items: MediaMacroItem[];
  name: string;
};

export type ParsedMediaMacros = {
  custom: string;
  legacy: string[];
  selected: string[];
};

const DEFAULT_MACROS: MediaMacro[] = [
  ['caid', '__CAID__', 'CAID', '设备标识', 'IOS', true],
  ['caid_list', '__CAID_LIST__', 'CAID 列表', '设备标识', 'IOS', true],
  ['caid1', '__CAID1__', 'CAID1', '设备标识', 'IOS', true],
  ['caid2', '__CAID2__', 'CAID2', '设备标识', 'IOS', true],
  ['last_caid', '__LAST_CAID__', '历史 CAID', '设备标识', 'IOS', true],
  ['prev_caid', '__PREV_CAID__', '上一 CAID', '设备标识', 'IOS', true],
  ['idfa', '__IDFA__', 'IDFA', '设备标识', 'IOS', true],
  ['idfa_md5', '__IDFA_MD5__', 'IDFA MD5', '设备标识', 'IOS', true],
  ['aaid', '__AAID__', 'AAID', '设备标识', 'ANDROID', true],
  ['imei', '__IMEI__', 'IMEI', '设备标识', 'ANDROID', true],
  ['imei_md5', '__IMEI_MD5__', 'IMEI MD5', '设备标识', 'ANDROID', true],
  ['oaid', '__OAID__', 'OAID', '设备标识', 'ANDROID', true],
  ['oaid_md5', '__OAID_MD5__', 'OAID MD5', '设备标识', 'ANDROID', true],
  ['androidid', '__ANDROIDID__', 'Android ID', '设备标识', 'ANDROID', true],
  [
    'androidid_md5',
    '__ANDROIDID_MD5__',
    'Android ID MD5',
    '设备标识',
    'ANDROID',
    true,
  ],
  ['mac', '__MAC__', 'MAC', '设备标识', 'BOTH', false],
  ['mac_md5', '__MAC_MD5__', 'MAC MD5', '设备标识', 'BOTH', false],
  ['ip', '__IP__', 'IPv4', '网络/终端', 'BOTH', false],
  ['ipv6', '__IPV6__', 'IPv6', '网络/终端', 'BOTH', false],
  ['ua', '__UA__', 'User-Agent', '网络/终端', 'BOTH', false],
  ['os', '__OS__', '操作系统', '网络/终端', 'BOTH', false],
  ['osv', '__OSV__', '系统版本', '网络/终端', 'BOTH', false],
  ['model', '__MODEL__', '机型', '网络/终端', 'BOTH', false],
  ['ts', '__TS__', '秒时间戳', '网络/终端', 'BOTH', false],
  ['callback_url', '__CALLBACK_URL__', '回调地址', '平台路由', 'BOTH', false],
  ['clickid', '__CLICKID__', '点击 ID', '平台路由', 'BOTH', false],
  ['impresss_id', '__IMPRESSS_ID__', '曝光 ID', '平台路由', 'BOTH', false],
  ['plan_id', '__ORDER_PLAN_ID__', '广告计划', '广告维度', 'BOTH', false],
  ['group_id', '__ORDER_GROUP_ID__', '广告组', '广告维度', 'BOTH', false],
  ['creative_id', '__CREATIVE_ID__', '创意', '广告维度', 'BOTH', false],
  ['adv_id', '__ADVERTISER_ID__', '广告主', '广告维度', 'BOTH', false],
  [AUTO_MACRO_KEY, AUTO_MACRO, '请求 ID', '平台路由', 'BOTH', false],
].map(([key, macro, label, group, osSupport, attributionIdentity]) => ({
  attributionIdentity: Boolean(attributionIdentity),
  group: String(group),
  key: String(key),
  label: String(label),
  macro: String(macro),
  osSupport: osSupport as MediaMacro['osSupport'],
}));

function option(item: MediaMacro): MediaMacroOption {
  return {
    attributionIdentity: item.attributionIdentity,
    key: item.key,
    label: item.label,
    macro: item.macro,
    osSupport: item.osSupport,
  };
}

/** Build the user-facing canonical catalog from the server's compatibility catalog. */
export function buildMediaMacroGroups(
  catalog: MediaMacro[] = DEFAULT_MACROS,
): MediaMacroGroup[] {
  const source = catalog.length > 0 ? catalog : DEFAULT_MACROS;
  const byKey = new Map(source.map((item) => [item.key, item]));
  const groups = new Map<string, MediaMacroGroup>();
  const added = new Set<string>();

  for (const item of source) {
    if (
      item.key === AUTO_MACRO_KEY ||
      RETIRED_MACRO_KEYS.has(item.key) ||
      CAID_ALIAS_KEYS.has(item.key) ||
      CAID_ADVANCED_KEYS.has(item.key) ||
      added.has(item.key)
    ) {
      continue;
    }

    const parentKey = DEVICE_VARIANT_PAIRS[item.key] || item.key;
    if (added.has(parentKey)) continue;
    const parent = byKey.get(parentKey) || item;
    const variantKeys = Object.entries(DEVICE_VARIANT_PAIRS)
      .filter(([, base]) => base === parentKey)
      .map(([key]) => key);
    const variants = [parentKey, ...variantKeys]
      .map((key) => byKey.get(key))
      .filter((value): value is MediaMacro => value !== undefined)
      .map((item) => option(item));
    const macroItem: MediaMacroItem = {
      ...option(parent),
      ...(variants.length > 1 ? { variants } : {}),
    };
    const group =
      groups.get(parent.group) ||
      ({ items: [], name: parent.group } satisfies MediaMacroGroup);
    group.items.push(macroItem);
    groups.set(parent.group, group);
    variants.forEach((variant) => added.add(variant.key));
    added.add(parentKey);
  }

  return [...groups.values()];
}

export function flattenMediaMacroItems(
  groups: MediaMacroGroup[],
): MediaMacroOption[] {
  return groups.flatMap((group) =>
    group.items.flatMap((item) => item.variants || [item]),
  );
}

export function compatibilityMacroItems(
  catalog: MediaMacro[] = DEFAULT_MACROS,
): MediaMacroOption[] {
  // Some deployments intentionally omit legacy entries from the catalog. The
  // UI still knows their exact wire names so old media can be displayed and
  // preserved without making them selectable in the canonical list.
  const sourceByKey = new Map(
    [...DEFAULT_MACROS, ...catalog].map((item) => [item.key, item]),
  );
  return [...sourceByKey.values()]
    .filter((item) => CAID_ADVANCED_KEYS.has(item.key))
    .map((item) => option(item));
}

/** Parse old strings without rewriting legacy aliases until the user touches them. */
export function parseMediaMacroParams(
  raw: null | string | undefined,
  groups: MediaMacroGroup[],
): ParsedMediaMacros {
  const known = new Set(flattenMediaMacroItems(groups).map((item) => item.key));
  const selected: string[] = [];
  const legacy: string[] = [];
  const custom: string[] = [];
  for (const segment of raw?.split('&') || []) {
    const pair = segment.trim();
    if (!pair) continue;
    const eq = pair.indexOf('=');
    const key = (eq === -1 ? pair : pair.slice(0, eq)).trim();
    if (!key || key === AUTO_MACRO_KEY) continue;
    if (known.has(key)) {
      if (!selected.includes(key)) selected.push(key);
    } else if (
      CAID_ALIAS_KEYS.has(key) ||
      CAID_ADVANCED_KEYS.has(key) ||
      RETIRED_MACRO_KEYS.has(key)
    ) {
      legacy.push(pair);
    } else {
      custom.push(pair);
    }
  }
  return { custom: custom.join('&'), legacy, selected };
}

export function serializeMediaMacroParams(
  groups: MediaMacroGroup[],
  selected: string[],
  legacy: string[],
  custom: string,
  extras: MediaMacroOption[] = [],
): string {
  const selectedSet = new Set(selected);
  // request_id is a reserved server-managed route field. It is shown in the
  // preview, but must not be sent in the media macroParams payload.
  const pairs: string[] = [];
  for (const item of [...flattenMediaMacroItems(groups), ...extras]) {
    if (item.key !== AUTO_MACRO_KEY && selectedSet.has(item.key)) {
      pairs.push(`${item.key}=${item.macro}`);
    }
  }
  pairs.push(...legacy.filter(Boolean));
  if (custom.trim()) pairs.push(custom.trim());
  return pairs.join('&');
}
