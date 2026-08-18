import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getMonitorTaskIntegrationFeedback,
  sendMonitorTaskIntegrationCallback,
} from './monitor-task-integration';

const requestClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('monitor-task media integration API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('queries persisted Feedback details only within a task', async () => {
    const params = {
      current: 1,
      endDate: '2026-08-18',
      requestId: 'AERIM-UAT-01',
      size: 20,
      startDate: '2026-08-18',
    };

    await getMonitorTaskIntegrationFeedback('task-1', params);

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/monitor-tasks/task-1/integration/feedback-requests',
      { params },
    );
  });

  it('sends a callback by Feedback log ID without accepting a target URL', async () => {
    const data = {
      currencyAmount: 1.25,
      eventCode: 'FIRST_ACTIVATION' as const,
      feedbackLogId: 101,
    };

    await sendMonitorTaskIntegrationCallback('task-1', data);

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/monitor-tasks/task-1/integration/callbacks:send',
      data,
    );
    expect(requestClient.post.mock.calls[0]?.[1]).not.toHaveProperty('url');
  });
});
