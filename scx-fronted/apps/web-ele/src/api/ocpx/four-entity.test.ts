import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createAdvertiserV2,
  createMedia,
  createMonitorTask,
  createPromotionLink,
  deductAdvertiser,
  deleteAdvertiserV2,
  deleteMedia,
  getAdvertiserRolePermissions,
  getProtocolConfigs,
  rechargeAdvertiser,
  updateAdvertiserRolePermissions,
  updateAdvertiserV2,
  updateProtocolAllowedHosts,
} from './four-entity';

const requestClient = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
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
      upstreamProtocol: 'TEMPLATE_GET',
      username: 'advertiser_a',
    });
    expect(requestClient.post).toHaveBeenCalledWith('/ocpx/advertisers', {
      bidUrl: 'custom://127.0.0.1/bid',
      credentialConfigKey: 'credential-ref',
      macroParams: 'id=__ID__',
      name: '广告主甲',
      password: 'Password1!',
      upstreamProtocol: 'TEMPLATE_GET',
      username: 'advertiser_a',
    });

    await updateAdvertiserV2('advertiser-1', {
      bidUrl: 'http://localhost/changed',
      name: '广告主甲-更新',
      upstreamProtocol: 'AERIM',
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
        upstreamProtocol: 'AERIM',
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
      billingEnabled: true,
      billingType: 2,
      conversionEvent: 'ACTIVATE',
      mediaId: 'media-1',
      monitorName: '任务甲',
      promotionLinkId: 'link-1',
      showUrl: 'must-not-leak',
      unitPrice: 1.2,
    } as Parameters<typeof createMonitorTask>[0] & { showUrl: string });
    expect(requestClient.post).toHaveBeenLastCalledWith('/ocpx/monitor-tasks', {
      advertiserId: 'advertiser-1',
      billingEnabled: true,
      billingType: 2,
      bizId: undefined,
      conversionEvent: 'ACTIVATE',
      mediaId: 'media-1',
      monitorName: '任务甲',
      promotionLinkId: 'link-1',
      remark: undefined,
      unitPrice: 1.2,
    });
  });

  it('submits an idempotency key and positive amount for recharge', async () => {
    await rechargeAdvertiser('advertiser-1', {
      amount: 100,
      remark: '首充',
      requestId: 'request-001',
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/advertisers/advertiser-1/recharge',
      { amount: 100, remark: '首充', requestId: 'request-001' },
    );
  });

  it('submits an idempotency key, positive amount and reason for deduction', async () => {
    await deductAdvertiser('advertiser-1', {
      amount: 25.5,
      remark: '线下退款',
      requestId: 'deduct-001',
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/advertisers/advertiser-1/deduct',
      { amount: 25.5, remark: '线下退款', requestId: 'deduct-001' },
    );
  });

  it('uses the aligned delete endpoints for advertiser and media', async () => {
    await deleteAdvertiserV2('advertiser-1');
    await deleteMedia('media-1');

    expect(requestClient.delete).toHaveBeenNthCalledWith(
      1,
      '/ocpx/advertisers/advertiser-1',
    );
    expect(requestClient.delete).toHaveBeenNthCalledWith(
      2,
      '/ocpx/media/media-1',
    );
  });

  it('uses a protected global endpoint for the shared advertiser role', async () => {
    await getAdvertiserRolePermissions();
    await updateAdvertiserRolePermissions({
      allDataScope: false,
      menuIds: ['menu_ocpx_advertiser', 'perm_ocpx_advertiser_edit'],
    });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/advertisers/account-role/permissions',
    );
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/advertisers/account-role/permissions',
      {
        allDataScope: false,
        menuIds: ['menu_ocpx_advertiser', 'perm_ocpx_advertiser_edit'],
      },
    );
  });

  it('loads and replaces administrator-managed protocol host allowlists', async () => {
    await getProtocolConfigs();
    await updateProtocolAllowedHosts('AERIM', ['d.aerim.cn', 'c.aerim.cn']);

    expect(requestClient.get).toHaveBeenCalledWith('/ocpx/protocol-configs');
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/protocol-configs/AERIM/allowed-hosts',
      { allowedHosts: ['d.aerim.cn', 'c.aerim.cn'] },
    );
  });
});
