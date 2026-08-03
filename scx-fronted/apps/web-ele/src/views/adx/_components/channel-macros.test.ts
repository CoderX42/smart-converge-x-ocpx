import { describe, expect, it } from 'vitest';

import {
  appendChannelMacros,
  DEFAULT_CHANNEL_MACRO_PARAMS,
} from './channel-macros';

describe('channel macro parameters', () => {
  it('provides the complete default macro template', () => {
    expect(DEFAULT_CHANNEL_MACRO_PARAMS).toBe(
      '?request_id=__REQUEST_ID__&imei=__IMEI__&imei_md5=__IMEI_MD5__&oaid=__OAID__&oaid_md5=__OAID_MD5__&idfa=__IDFA__&idfa_md5=__IDFA_MD5__&ip=__IP__&ua=__UA__&model=__MODEL__&os=__OS__&mac=__MAC__&mac_md5=__MAC_MD5__&mac1_md5=__MAC1_MD5__&caid=__CAID__&callback_url=__CALLBACK_URL__&mts=__MTS__',
    );
  });

  it('adds selected macros in their standard order', () => {
    expect(appendChannelMacros('', ['oaid', 'imei'])).toBe(
      '?imei=__IMEI__&oaid=__OAID__',
    );
  });

  it('keeps existing values and does not add duplicate keys', () => {
    expect(appendChannelMacros('?imei=custom-value', ['imei', 'idfa'])).toBe(
      '?imei=custom-value&idfa=__IDFA__',
    );
  });

  it('supports existing parameter strings without a leading question mark', () => {
    expect(appendChannelMacros('source=scx', ['ip'])).toBe(
      'source=scx&ip=__IP__',
    );
  });
});
