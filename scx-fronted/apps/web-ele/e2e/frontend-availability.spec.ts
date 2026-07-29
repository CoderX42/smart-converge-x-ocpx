import { expect, test } from '@playwright/test';

const persistedAccessStoreKey = 'smart-converge-x-2.1.3-dev-core-access';

test.describe('前端可用性降级', () => {
  test('后端不可用且存在旧会话时仍进入登录页而不是空白页', async ({ page }) => {
    await page.addInitScript(
      ({ key }) => {
        localStorage.setItem(
          key,
          JSON.stringify({
            accessCodes: [],
            accessToken: 'stale-session-for-availability-test',
            isLockScreen: false,
            lockScreenPassword: undefined,
            refreshToken: null,
          }),
        );
      },
      { key: persistedAccessStoreKey },
    );
    await page.route('**/api/boot/system/user/info', (route) =>
      route.abort('connectionfailed'),
    );

    await page.goto('/');

    await expect(page).toHaveURL(/#\/auth\/login/);
    await expect(
      page.getByRole('heading', { name: '智汇广告平台', exact: true }),
    ).toBeVisible();
    await expect(page.locator('#app')).not.toBeEmpty();
  });
});
