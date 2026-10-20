import type { RtaProtocol, RtaRow } from '#/api/ocpx/rta';

export type MediaProtocol = 'GENERIC_ASYNC' | 'OPPO';

const blank = (value: unknown) => !String(value ?? '').trim();
const httpUrl = /^https:\/\/[^\s?#@]+$/i;
const identifier = /^[\w-]{1,64}$/;
const packageName = /^[A-Z]\w*(?:\.[A-Z]\w*)+$/i;

export function defaultMediaConfig(
  protocol: MediaProtocol,
): Record<string, string> {
  return protocol === 'OPPO'
    ? { ownerMode: 'EMPTY', timeoutMs: '1000', uploadCredentialRef: '' }
    : {};
}

export function mediaErrors(form: RtaRow): string[] {
  const errors: string[] = [];
  if (blank(form.mediaId)) errors.push('请选择媒体');
  if (blank(form.credentialRef)) errors.push('请填写服务端凭据引用');
  else if (!identifier.test(String(form.credentialRef)))
    errors.push('凭据引用只能包含字母、数字、下划线或短横线');
  if (form.protocol === 'OPPO') {
    const config = form.protocolConfig || {};
    const timeout = Number(config.timeoutMs);
    if (!Number.isInteger(timeout) || timeout < 100 || timeout > 5000)
      errors.push('同步等待时限须为 100–5000 毫秒');
    if (config.ownerMode === 'VALUE' && blank(config.ownerId))
      errors.push('请填写 OPPO owner_id');
  } else if (blank(form.notifyUrl)) errors.push('请填写决策通知地址');
  else if (!httpUrl.test(String(form.notifyUrl)))
    errors.push('决策通知地址须为 https 地址，且不含查询参数');
  return errors;
}

export function upstreamErrors(
  form: RtaRow,
  protocol: RtaProtocol | undefined,
): string[] {
  if (!protocol) return ['请选择 RTA 协议'];
  const errors: string[] = [];
  if (blank(form.advertiserId)) errors.push('缺少广告主');
  if (blank(form.name)) errors.push('请填写接入名称');
  if (blank(form.endpoint)) errors.push('请填写广告主请求地址');
  else if (!httpUrl.test(String(form.endpoint)))
    errors.push('广告主请求地址须为 https 地址，且不含查询参数');
  if (protocol.credentialRequired && blank(form.credentialRef))
    errors.push('请填写服务端凭据引用');
  for (const field of protocol.configFields) {
    const value = form.protocolConfig?.[field.key] || '';
    if (!new RegExp(`^(?:${field.pattern})$`).test(value))
      errors.push(`请正确填写${field.label}`);
  }
  return errors;
}

export function bindingErrors(
  form: RtaRow,
  protocol: RtaProtocol | undefined,
  mediaProtocol?: string,
): string[] {
  const errors: string[] = [];
  if (blank(form.upstreamId)) return ['请选择广告主接入'];
  if (!protocol) return ['所选接入的协议不可用'];
  if (mediaProtocol === 'OPPO' && protocol.code !== 'WEIFENG_RTA')
    errors.push('OPPO 媒体只能绑定微风 RTA 接入');
  for (const field of protocol.bindingFields)
    if (blank(form[field.key as keyof RtaRow]))
      errors.push(`请填写${field.label}`);
  if (form.oppoPkg && !packageName.test(form.oppoPkg))
    errors.push('OPPO 应用包名格式无效');
  return errors;
}
