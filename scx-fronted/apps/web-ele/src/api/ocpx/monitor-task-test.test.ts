import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  executeMonitorTaskTestLink,
  generateMonitorTaskTestLink,
  getMonitorTaskTestResult,
} from './monitor-task-test';

const requestClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('#/api/request', () => ({ requestClient }));

describe('monitor-task click test API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('generates only from a task, OS and selected parameter names', async () => {
    await generateMonitorTaskTestLink('task-1', {
      os: 'ANDROID',
      parameters: ['oaid', 'ip'],
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/monitor-tasks/task-1/test-links:generate',
      { os: 'ANDROID', parameters: ['oaid', 'ip'] },
    );
  });

  it('executes without accepting an arbitrary target URL', async () => {
    await executeMonitorTaskTestLink('task-1', {
      os: 'ANDROID',
      parameters: { oaid: 'device-value' },
      requestId: 'SCX-UAT-20260817183000-abcdef123456',
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/ocpx/monitor-tasks/task-1/test-links:execute',
      {
        os: 'ANDROID',
        parameters: { oaid: 'device-value' },
        requestId: 'SCX-UAT-20260817183000-abcdef123456',
      },
    );
    expect(requestClient.post.mock.calls[0]?.[1]).not.toHaveProperty('url');
  });

  it('encodes the request ID while querying reusable fact details', async () => {
    await getMonitorTaskTestResult('task-1', 'SCX UAT/1');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/ocpx/monitor-tasks/task-1/test-results/SCX%20UAT%2F1',
    );
  });
});
