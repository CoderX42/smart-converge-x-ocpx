import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createAdvertiserV2,
  createMedia,
  createMonitorTask,
  createPromotionLink,
  updateAdvertiserV2,
} from './four-entity';

const requestClient = vi.hoisted(() => ({
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('ocpx four-entity write contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits account credentials only while creating an advertiser', async () => {
    await createAdvertiserV2({
      bidUrl: 'custom://127.0.0.1/bid',
      credentialConfigKey: 'credential-ref',
      macroParams: 'id=__ID__',
      name: '广告主甲',
      password: 'Password1!',
      username: 'advertiser_a',
    });
    expect(requestClient.post).toHaveBeenCalledWith('/ocpx/advertisers', {
      bidUrl: 'custom://127.0.0.1/bid',
      credentialConfigKey: 'credential-ref',
      macroParams: 'id=__ID__',
      name: '广告主甲',
      password: 'Password1!',
      username: 'advertiser_a',
    });

    await updateAdvertiserV2('advertiser-1', {
      bidUrl: 'http://localhost/changed',
      name: '广告主甲-更新',
      password: 'must-not-leak',
      username: 'must-not-leak',
    } as Parameters<typeof updateAdvertiserV2>[1] & {
      password: string;
      username: string;
    });
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/advertisers/advertiser-1',
      {
        bidUrl: 'http://localhost/changed',
        credentialConfigKey: undefined,
        macroParams: undefined,
        name: '广告主甲-更新',
      },
    );
  });

  it('whitelists the promotion, media and monitor write bodies', async () => {
    await createPromotionLink({
      advertiserId: 'advertiser-1',
      linkName: '链接甲',
      linkUrl: 'scheme://landing',
      platform: 1,
      status: 'must-not-leak',
    } as Parameters<typeof createPromotionLink>[0] & { status: string });
    expect(requestClient.post).toHaveBeenLastCalledWith(
      '/ocpx/promotion-links',
      {
        advertiserId: 'advertiser-1',
        deeplink: undefined,
        linkChannel: undefined,
        linkExt: undefined,
        linkName: '链接甲',
        linkUrl: 'scheme://landing',
        platform: 1,
        remark: undefined,
      },
    );

    await createMedia({
      macroParams: 'click=__CLICK__',
      mediaName: '媒体甲',
      status: 'must-not-leak',
      uri: '/feedback',
    } as Parameters<typeof createMedia>[0] & { status: string });
    expect(requestClient.post).toHaveBeenLastCalledWith('/ocpx/media', {
      credentialConfigKey: undefined,
      macroParams: 'click=__CLICK__',
      mediaName: '媒体甲',
      uri: '/feedback',
    });

    await createMonitorTask({
      advertiserId: 'advertiser-1',
      mediaId: 'media-1',
      monitorName: '任务甲',
      promotionLinkId: 'link-1',
      showUrl: 'must-not-leak',
      unitPrice: 1.2,
    } as Parameters<typeof createMonitorTask>[0] & { showUrl: string });
    expect(requestClient.post).toHaveBeenLastCalledWith('/ocpx/monitor-tasks', {
      advertiserId: 'advertiser-1',
      bizId: undefined,
      mediaId: 'media-1',
      monitorName: '任务甲',
      promotionLinkId: 'link-1',
      remark: undefined,
      unitPrice: 1.2,
    });
  });
});
