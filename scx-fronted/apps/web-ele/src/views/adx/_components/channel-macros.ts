export type ChannelMacroOption = Readonly<{
  key: string;
  label: string;
  placeholder: string;
}>;

export const CHANNEL_MACRO_OPTIONS: readonly ChannelMacroOption[] = [
  { key: 'request_id', label: '请求 ID', placeholder: '__REQUEST_ID__' },
  { key: 'imei', label: 'IMEI', placeholder: '__IMEI__' },
  { key: 'imei_md5', label: 'IMEI MD5', placeholder: '__IMEI_MD5__' },
  { key: 'oaid', label: 'OAID', placeholder: '__OAID__' },
  { key: 'oaid_md5', label: 'OAID MD5', placeholder: '__OAID_MD5__' },
  { key: 'idfa', label: 'IDFA', placeholder: '__IDFA__' },
  { key: 'idfa_md5', label: 'IDFA MD5', placeholder: '__IDFA_MD5__' },
  { key: 'ip', label: 'IP', placeholder: '__IP__' },
  { key: 'ua', label: 'User Agent', placeholder: '__UA__' },
  { key: 'model', label: '设备型号', placeholder: '__MODEL__' },
  { key: 'os', label: '操作系统', placeholder: '__OS__' },
  { key: 'mac', label: 'MAC', placeholder: '__MAC__' },
  { key: 'mac_md5', label: 'MAC MD5', placeholder: '__MAC_MD5__' },
  { key: 'mac1_md5', label: 'MAC1 MD5', placeholder: '__MAC1_MD5__' },
  { key: 'caid', label: 'CAID', placeholder: '__CAID__' },
  {
    key: 'callback_url',
    label: '回调地址',
    placeholder: '__CALLBACK_URL__',
  },
  { key: 'mts', label: '时间戳', placeholder: '__MTS__' },
] as const;

function toSegment(option: ChannelMacroOption) {
  return `${option.key}=${option.placeholder}`;
}

export const DEFAULT_CHANNEL_MACRO_PARAMS = `?${CHANNEL_MACRO_OPTIONS.map(
  (option) => toSegment(option),
).join('&')}`;

function getExistingKeys(value: string) {
  const keys = new Set<string>();
  for (const segment of value.replace(/^\?/, '').split('&')) {
    const key = segment.split('=', 1)[0]?.trim().toLowerCase();
    if (key) keys.add(key);
  }
  return keys;
}

export function appendChannelMacros(
  value: string | undefined,
  selectedKeys: readonly string[],
) {
  let nextValue = value?.trim() ?? '';
  const existingKeys = getExistingKeys(nextValue);

  for (const option of CHANNEL_MACRO_OPTIONS) {
    if (
      !selectedKeys.includes(option.key) ||
      existingKeys.has(option.key.toLowerCase())
    ) {
      continue;
    }

    let separator = '&';
    if (nextValue.length === 0) separator = '?';
    else if (nextValue.endsWith('&') || nextValue.endsWith('?')) separator = '';
    nextValue += `${separator}${toSegment(option)}`;
    existingKeys.add(option.key.toLowerCase());
  }

  return nextValue;
}
