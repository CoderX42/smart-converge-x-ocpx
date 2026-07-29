import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  deleteIntegrationDocument,
  getIntegrationDocuments,
  saveIntegrationDocument,
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

describe('integration document API contract', () => {
  beforeEach(() => vi.clearAllMocks());

  it('builds typed query and delete version parameters', async () => {
    vi.mocked(requestClient.get).mockResolvedValue({ records: [], total: 0 });
    vi.mocked(requestClient.delete).mockResolvedValue(true);

    await getIntegrationDocuments({
      current: 2,
      keyword: '协议',
      mediaId: 'media-1',
      size: 20,
      storageType: 'OBJECT',
    });
    await deleteIntegrationDocument('doc-1', 7);

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ad/integration-documents',
      {
        params: {
          current: 2,
          keyword: '协议',
          mediaId: 'media-1',
          size: 20,
          storageType: 'OBJECT',
        },
      },
    );
    expect(requestClient.delete).toHaveBeenCalledWith(
      '/ad/integration-documents/doc-1',
      { params: { version: 7 } },
    );
  });

  it('sends metadata and file together and exposes progress and cancellation', async () => {
    vi.mocked(requestClient.post).mockResolvedValue({
      documentVersion: 1,
      id: 'doc-1',
      mediaId: 'media-1',
      name: '协议',
      status: 'DRAFT',
      storageType: 'OBJECT',
      type: 'OTHER',
      version: 0,
    });
    const progress = vi.fn();
    const controller = new AbortController();
    const file = new File(['guide'], 'guide.pdf', {
      type: 'application/pdf',
    });

    await saveIntegrationDocument(
      {
        mediaId: 'media-1',
        name: '协议',
        status: 'DRAFT',
        storageType: 'OBJECT',
        type: 'OTHER',
      },
      file,
      progress,
      controller.signal,
    );

    const call = vi.mocked(requestClient.post).mock.calls[0];
    expect(call).toBeDefined();
    if (!call) throw new Error('Expected document upload request');
    const [, body, config] = call;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('file')).toBe(file);
    expect(config?.signal).toBe(controller.signal);
    config?.onUploadProgress?.({
      bytes: 5,
      lengthComputable: true,
      loaded: 5,
      progress: 0.5,
      rate: undefined,
      total: 10,
      upload: true,
    });
    expect(progress).toHaveBeenCalledWith(50);
  });
});
