import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getUserMenu } from "@/api/user";
import { MenuTreeVO } from "@/proto";

interface UserState {
  token: string | null;
  userInfo: any;
  menuList: MenuTreeVO[]; // 确保类型正确
}

export const useUserStore = defineStore("user", () => {
  const token = ref<string | null>(localStorage.getItem("token") || null);
  const userInfo = ref<any>(null);
  const menuList = ref<MenuTreeVO[]>([]);
  const isMenuLoaded = ref<boolean>(false);
  const setToken = (newToken: string) => {
    token.value = newToken;
    localStorage.setItem("token", newToken);
  };

  const setUserInfo = (user: any) => {
    userInfo.value = user;
  };

  const fetchMenu = async () => {
    if (!token.value) return;

    try {
      const res = await getUserMenu(); // 调用获取菜单API
      console.log("Fetched Menu Data:", res);
      if (res.code === 200) {
        menuList.value = res.data;
        console.log("Menu List Set In Store:", menuList.value);
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
    token,
    userInfo,
    menuList,
    setToken,
    setUserInfo,
    fetchMenu,
    isMenuLoaded,
    setMenuRoutes,
    setIsMenuLoaded
  };
});
