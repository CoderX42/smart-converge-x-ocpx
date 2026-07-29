import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getMediaScopeCandidates,
  getPartners,
  revokeMediaScope,
  savePartner,
} from '#/api/ad';
import { requestClient } from '#/api/request';

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    download: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('media management API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('requires a media for the minimal user candidate query', async () => {
    vi.mocked(requestClient.get).mockResolvedValue({ records: [], total: 0 });

    await getMediaScopeCandidates({
      current: 2,
      keyword: '运营',
      mediaId: 'media-1',
      size: 20,
    });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/subjects/media-scopes/candidates',
      {
        params: {
          current: 2,
          keyword: '运营',
          mediaId: 'media-1',
          size: 20,
        },
      },
    );
  });

  it('uses media-scoped partner CRUD and revoke parameters', async () => {
    vi.mocked(requestClient.get).mockResolvedValue({ records: [], total: 0 });
    vi.mocked(requestClient.post).mockResolvedValue(true);
    vi.mocked(requestClient.delete).mockResolvedValue(true);

    await getPartners({
      current: 1,
      mediaId: 'media-1',
      size: 20,
      status: 'ACTIVE',
    });
    await savePartner({
      code: 'PARTNER-1',
      mediaId: 'media-1',
      name: '合作方一',
      status: 'ACTIVE',
      type: 'UPSTREAM',
    });
    await revokeMediaScope('scope-1', 'media-1');

    expect(requestClient.get).toHaveBeenCalledWith('/ad/partners', {
      params: {
        current: 1,
        mediaId: 'media-1',
        size: 20,
        status: 'ACTIVE',
      },
    });
    expect(requestClient.post).toHaveBeenCalledWith('/ad/partners', {
      code: 'PARTNER-1',
      mediaId: 'media-1',
      name: '合作方一',
      status: 'ACTIVE',
      type: 'UPSTREAM',
    });
    expect(requestClient.delete).toHaveBeenCalledWith(
      '/subjects/media-scopes/scope-1',
      { params: { mediaId: 'media-1' } },
    );
  });
});
