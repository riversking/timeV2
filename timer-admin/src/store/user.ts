import { defineStore } from "pinia";
import { ref } from "vue";
import { getUserMenu } from "@/api/user";
import { getCurrentUser } from "@/api/auth";
import { MenuTreeVO } from "@/proto";

export const useUserStore = defineStore("user", () => {
  const userInfo = ref<{ userId: string; username: string } | null>(null);
  const menuList = ref<MenuTreeVO[]>([]);
  const isMenuLoaded = ref(false);

  /** ★ 登录成功后调用：存储用户信息 */
  const setUserInfo = (user: { userId: string; username: string }) => {
    userInfo.value = user;
  };

  /** ★ 判断是否已登录 */
  const isLoggedIn = () => !!userInfo.value;

  /**
   * ★ 应用启动时调用：通过 cookie 自动验证登录态
   * 成功 → 拿到 userId/username，失败 → 跳登录
   */
  const initAuth = async (): Promise<boolean> => {
    try {
      const res = await getCurrentUser();
      if (res.data.code === 200 && res.data.data) {
        userInfo.value = res.data.data;
        return true;
      }
    } catch {
      // cookie 无效或过期
    }
    userInfo.value = null;
    return false;
  };

  /** 退出登录：调后端清除 cookie + 清本地状态 */
  const logout = async () => {
    try {
      await fetch("/api/user-server/logout", { method: "POST" });
    } finally {
      userInfo.value = null;
      menuList.value = [];
      window.location.href = "/login";
    }
  };

  const fetchMenu = async () => {
    if (!userInfo.value) return;
    try {
      const res = await getUserMenu();
      if (res.code === 200) {
        menuList.value = res.data;
        return res.data;
      }
    } catch (error) {
      console.error("获取菜单失败:", error);
    }
  };

  const setMenuRoutes = (menus: MenuTreeVO[]) => {
    menuList.value = menus;
  };

  const setIsMenuLoaded = (loaded: boolean) => {
    isMenuLoaded.value = loaded;
  };

  return {
    userInfo,
    menuList,
    isMenuLoaded,
    setUserInfo,
    isLoggedIn,
    initAuth,
    logout,
    fetchMenu,
    setMenuRoutes,
    setIsMenuLoaded,
  };
});