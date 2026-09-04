import { beforeEach, describe, expect, it, vi } from 'vitest';

import { listRtaDecisions, listRtaMetrics, updateRtaTaskConfig } from './rta';

const requestClient = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('rta management API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uses the task binding endpoint and keeps external task IDs explicit', async () => {
    const data = {
      bindings: [{ externalTaskId: 'external-1' }],
      enabled: true,
      profileId: 'profile-1',
    };
    await updateRtaTaskConfig('task-1', data);
    expect(requestClient.put).toHaveBeenCalledWith(
      '/ocpx/rta/monitor-tasks/task-1',
      data,
    );
  });

  it('passes asynchronous decision and metric filters through unchanged', async () => {
    const params = {
      endDate: '2026-09-04',
      grain: 'HOUR' as const,
      monitorTaskId: 'task-1',
      startDate: '2026-09-04',
    };
    await listRtaDecisions(params);
    await listRtaMetrics(params);
    expect(requestClient.get).toHaveBeenNthCalledWith(
      1,
      '/ocpx/rta/decisions',
      {
        params,
      },
    );
    expect(requestClient.get).toHaveBeenNthCalledWith(2, '/ocpx/rta/metrics', {
      params,
    });
  });
});
