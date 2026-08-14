import { beforeEach, describe, expect, it, vi } from 'vitest';

import { editPassword } from './user';

const requestClient = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock('#/api/request', () => ({ requestClient }));

describe('system user write contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits a self-password change without a caller-controlled user id', async () => {
    await editPassword({
      checkPassword: 'NewPassword1!',
      id: 'another-user',
      newPassword: 'NewPassword1!',
      password: 'OldPassword1!',
    } as Parameters<typeof editPassword>[0] & { id: string });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/system/user/password',
      {
        checkPassword: 'NewPassword1!',
        newPassword: 'NewPassword1!',
        password: 'OldPassword1!',
      },
      { headers: {} },
    );
  });
});
