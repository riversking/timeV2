import clientHttp from "@/services/clientHttp";

export interface PortalMenuItem {
  label: string;
  path: string;
  component: string;
  sortOrder: number;
}

/** 接口不可用时的内置兜底菜单（与初始种子数据一致） */
const BUILTIN_MENUS: PortalMenuItem[] = [
  { label: "首页", path: "/portal", component: "/portal/PortalHome", sortOrder: 10 },
  { label: "资讯", path: "/portal/news", component: "/portal/PortalNews", sortOrder: 20 },
  { label: "球员", path: "/portal/players", component: "/portal/PortalPlayers", sortOrder: 30 },
  { label: "球队", path: "/portal/teams", component: "/portal/PortalTeams", sortOrder: 40 },
  { label: "赛程", path: "/portal/schedule", component: "/portal/PortalSchedule", sortOrder: 50 },
  { label: "动态", path: "/portal/moments", component: "/portal/PortalMoments", sortOrder: 55 },
  { label: "排行", path: "/portal/rankings", component: "/portal/PortalRankings", sortOrder: 60 },
];

let cache: PortalMenuItem[] | null = null;
let pending: Promise<PortalMenuItem[]> | null = null;

/**
 * 加载门户菜单（后台 timer_menu 配置，menu_scope=2）
 * 模块级缓存 + 并发去重，接口失败回落内置菜单
 */
export async function loadPortalMenus(): Promise<PortalMenuItem[]> {
  if (cache) return cache;
  if (pending) return pending;
  pending = (async () => {
    try {
      const res = await clientHttp.post("/api/user-server/menu/getPortalMenuTree", {});
      const list = res.data?.data;
      if (Array.isArray(list) && list.length) {
        cache = list
          .filter((m: any) => m.routePath)
          .map((m: any) => ({
            label: m.menuName,
            path: m.routePath,
            component: m.component || "",
            sortOrder: m.sortOrder ?? 0,
          }))
          .sort((a, b) => a.sortOrder - b.sortOrder);
        return cache;
      }
    } catch (error) {
      console.error("加载门户菜单失败，使用内置兜底菜单", error);
    }
    cache = BUILTIN_MENUS;
    return cache;
  })();
  try {
    return await pending;
  } finally {
    pending = null;
  }
}

/** 清除缓存（调试或后台改菜单后强制刷新用） */
export function clearPortalMenuCache() {
  cache = null;
}
