import { describe, expect, it } from 'vitest';

import { buildChannelSavePayload } from './channel-form';

describe('channel form payload', () => {
  it('only submits channel catalog fields managed by the channel editor', () => {
    expect(
      buildChannelSavePayload({
        adapterCode: 'STANDARD',
        authType: 'HMAC',
        callbackHostAllowlist: 'callback.example.com',
        channelCode: 'media_main',
        channelName: '主渠道',
        keyVersion: 3,
        macroParams: '?clickid=__CLICK_ID__',
        secretKey: 'must-not-be-submitted',
        status: 'ACTIVE',
        uri: 'https://ocpx.example.com/ocpx',
      }),
    ).toEqual({
      adapterCode: 'STANDARD',
      channelCode: 'media_main',
      channelName: '主渠道',
      macroParams: '?clickid=__CLICK_ID__',
      status: 'ACTIVE',
    });
  });
});
