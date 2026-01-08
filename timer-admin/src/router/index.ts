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
      name: "Home",
      component: () => import("@/views/Home.vue"),
      children: [],
    },
  ],
});

export async function setupDynamicRoutes() {
  const userStore = useUserStore();
  if (userStore.isMenuLoaded) {
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
    const menuItems = menuData.map((item: any) => {
      console.log("Normalized Path in setupDynamicRoutes:", item.routePath);
      return {
        ...item,
        routePath: item.routePath,
      };
    });

    const routes = convertToRoutes(menuItems);
    const homeRoute = router
      .getRoutes()
      .find((route) => route.path === "/home");

    if (homeRoute) {
      routes.forEach((route) => {
        homeRoute.children.push(route);
        router.addRoute("Home", route);
      });
    }
    router.addRoute({
      path: "/:pathMatch(.*)*",
      // 修改重定向目标，避免循环
      redirect: "/home",
    });
    userStore.setMenuRoutes(menuItems);
  } catch (error) {
    console.error("动态添加路由失败:", error);
  }
}

function convertToRoutes(menuList: MenuTreeVO[]): RouteRecordRaw[] {
  const modules = import.meta.glob("/src/views/**/*.vue");
  return menuList
    .map((item) => {
      console.log("Normalized Path:", item.routePath);
      const componentPath = item.children
        ? () => import(`@/layouts/DefaultLayout.vue`)
        : modules[`/src/views${item.routePath}.vue`]; // ✅ 添加斜杠
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
      await setupDynamicRoutes();
      next();
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
