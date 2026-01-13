import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/store/user";
import { MenuTreeVO } from "@/proto";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "Login",
      component: () => import("@/components/Login.vue"),
    },
    {
      path: "/home",
      name: "首页",
      component: () => import("@/views/Home.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/:dynamicPath(.*)",
      name: "DynamicRouteLoader",
      component: () => import("@/views/Home.vue"), // 临时组件，将在路由守卫中被替换
      meta: { requiresAuth: true, isDynamicLoader: true },
    },
  ],
});

export async function setupDynamicRoutes() {
  const userStore = useUserStore();
  if (userStore.menuList.length && userStore.isMenuLoaded) {
    return;
  }
  userStore.setIsMenuLoaded(true);
  try {
    const token = localStorage.getItem("token");
    if (!token) {
      return;
    }
    const menuData = await userStore.fetchMenu();
    console.log("Menu Data in setupDynamicRoutes:", menuData);
    if (!Array.isArray(menuData)) {
      return;
    }

    // ✅ 修复: 规范化菜单路径
    const menuItems = menuData
      .map((item: any) => {
        console.log("Normalized Path in setupDynamicRoutes:", item.routePath);
        return {
          ...item,
          routePath:
            item.routePath && item.routePath.startsWith("/")
              ? item.routePath
              : item.routePath
              ? `/${item.routePath}`
              : undefined,
        };
      })
      .filter((item: any) => item.routePath);
    const routes = convertToRoutes(menuItems);
    routes.forEach((route) => {
      router.addRoute(route);
    });
    userStore.setMenuRoutes(menuItems);
  } catch (error) {
    console.error("动态添加路由失败:", error);
  } finally {
    userStore.setIsMenuLoaded(false);
  }
}

function convertToRoutes(menuList: MenuTreeVO[]): RouteRecordRaw[] {
  const modules = import.meta.glob("/src/views/**/*.vue");
  return menuList
    .map((item) => {
      console.log("Normalized Path:", item.routePath);
      const componentPath = !item.children
        ? () => import(`@/layouts/DefaultLayout.vue`)
        : modules[`/src/views${item.routePath}.vue`] ||
          (() => import(`@/views/Home.vue`));

      return {
        path: item.routePath.startsWith("/")
          ? item.routePath
          : `/${item.routePath}`,
        name: item.menuName,
        component: componentPath,
        children: item.children ? convertToRoutes(item.children) : [],
        meta: { keepAlive: true },
      };
    })
    .filter(Boolean);
}

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore();
  if (userStore.token && to.path === "/login") {
    next("/");
    return;
  }

  if (!userStore.token && to.path !== "/login") {
    next("/login");
    return;
  }
  if (
    !userStore.menuList.length &&
    !userStore.isMenuLoaded &&
    to.path !== "/login"
  ) {
    try {
      console.log("用户登录，正在加载菜单...");
      // 菜单加载完成后继续导航
      // 检查当前路由是否已经存在，如果不存在，尝试重新加载菜单
      const routeExists =
        router.hasRoute(to.name as string) ||
        router.getRoutes().some((route) => route.path === to.path);

      if (routeExists && userStore.token && to.path !== "/login") {
        try {
          const newRouteExists =
            router.hasRoute(to.name as string) ||
            router.getRoutes().some((route) => route.path === to.path);
          if (newRouteExists) {
            await setupDynamicRoutes();
            next(to.fullPath);
          } else {
            console.warn(`路由仍然不存在: ${to.path}`);
            next("/home");
          }
        } catch (error) {
          console.error("重新加载菜单失败:", error);
          next("/login");
          return;
        }
      } else {
        await setupDynamicRoutes();
        next();
      }
      return;
    } catch (error) {
      console.error("路由守卫中加载菜单失败:", error);
      next("/login");
      return;
    }
  }
  next();
});

export default router;
