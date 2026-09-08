import { requestClient } from '#/api/request';

/**
 * 获取用户所有菜单
 */
export async function getAllMenusApi() {
  const menus = await requestClient.get<any[]>('/system/menu', {
    headers: {},
  });
  for (const menu of menus) {
    if (menu.path === '/ocpx/rta' && menu.children?.length)
      menu.redirect = menu.children[0].path;
  }
  return menus.filter(
    (menu) => menu.path !== '/ocpx/rta' || menu.children?.length,
  );
}
