import { Router, RouteRecordRaw } from 'vue-router';
import { componentMap } from './componentMap';
import { useUserStore } from '@/store/user';

export async function setupDynamicRoutes(router: Router): Promise<RouteRecordRaw[]> {
  const userStore = useUserStore();
  
  // 清理已注册的动态路由
  router.getRoutes().forEach(route => {
    if (route.meta?.isDynamic) {
      router.removeRoute(route.name as string);
    }
  });

  // 生成路由配置
  const generateRoutes = (menuList: any[], parentPath = ''): RouteRecordRaw[] => {
    return menuList.map((menu: any) => {
      // 确保 routePath 是 kebab-case
      let routePath = menu.routePath || menu.menuCode.toLowerCase().replace(/_/g, '-');
      routePath = routePath.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
      
      const fullPath = parentPath ? `${parentPath}/${routePath}` : `/${routePath}`;
      
      // 关键修复：显式初始化 children 为 []
      const route: RouteRecordRaw = {
        path: fullPath,
        name: `Menu_${menu.id}`,
        component: componentMap[routePath],
        meta: {
          title: menu.menuName,
          icon: menu.icon,
          isDynamic: true
        },
        children: [] // 确保 children 有默认值
      };
      
      if (menu.children && menu.children.length > 0) {
        route.children = generateRoutes(menu.children, fullPath);
      }
      
      return route;
    });
  };

  // 添加路由
  const dynamicRoutes = generateRoutes(userStore.menuList);
  
  dynamicRoutes.forEach(route => {
    router.addRoute('Home', route);
  });
  
  return dynamicRoutes;
};