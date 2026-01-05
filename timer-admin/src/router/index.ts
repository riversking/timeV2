// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/store/user';
import { setupDynamicRoutes } from './dynamicRoutes';
import { componentMap } from './componentMap';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/home',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
      children: [],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/NotFound.vue'),
    },
  ],
});

// 路由状态标记
let hasLoadedRoutes = false;

// 重置路由状态
export const resetHasLoadedRoutes = () => {
  hasLoadedRoutes = false;
};

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore();
  const token = userStore.token;
  
  // 1. 处理登录页面
  if (to.path === '/login') {
    if (token) {
      next('/');
    } else {
      next();
    }
    return;
  }
  
  // 2. 路由状态检查（避免重复请求）
  if (hasLoadedRoutes) {
    next();
    return;
  }
  
  // 3. 处理未登录情况
  if (!token) {
    next('/login');
    return;
  }
  
  // 4. 动态加载路由
  try {
    if (!userStore.isMenuLoaded) {
      await userStore.fetchMenu();
    }
    
    // 修复：显式等待并忽略返回值
    await setupDynamicRoutes(router);
    
    // 标记路由已加载
    hasLoadedRoutes = true;
    
    // 重定向到当前路径
    if (to.path !== '/') {
      next({ path: to.path, replace: true });
    } else {
      next();
    }
  } catch (error) {
    console.error('动态路由加载失败:', error);
    next('/login');
  }
});

// 修复：确保类型正确
export default router;