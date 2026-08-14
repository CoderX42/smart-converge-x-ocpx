import { expect, test } from '@playwright/test';

const persistedAccessStoreKey = 'smart-converge-x-2.1.3-dev-core-access';
const persistedPreferencesStoreKey = 'smart-converge-x-2.1.3-dev-preferences';

test.describe('前端可用性降级', () => {
  test('后端不可用且存在旧会话时仍进入登录页而不是空白页', async ({ page }) => {
    await page.addInitScript(
      ({ accessKey, preferencesKey }) => {
        localStorage.setItem(
          accessKey,
          JSON.stringify({
            accessCodes: [],
            accessToken: 'stale-session-for-availability-test',
            isLockScreen: false,
            lockScreenPassword: undefined,
            refreshToken: null,
          }),
        );
        localStorage.setItem(
          preferencesKey,
          JSON.stringify({
            value: {
              app: { name: '智汇广告平台' },
              copyright: { companyName: '智汇广告平台' },
            },
          }),
        );
      },
      {
        accessKey: persistedAccessStoreKey,
        preferencesKey: persistedPreferencesStoreKey,
      },
    );
    await page.route('**/api/boot/system/user/info', (route) =>
      route.abort('connectionfailed'),
    );

    await page.goto('/');

    await expect(page).toHaveURL(/#\/auth\/login/);
    await expect(
      page.getByRole('heading', { name: 'OmniLink', exact: true }),
    ).toBeVisible();
    await expect(page).toHaveTitle(/OmniLink$/);
    await expect(
      page.getByText('单企业效果广告投放与归因管理平台', { exact: true }),
    ).toHaveCount(0);
    await expect(page.locator('#app')).not.toBeEmpty();
  });
});
