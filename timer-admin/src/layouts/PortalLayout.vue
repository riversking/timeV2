<template>
  <div class="portal-layout">
    <!-- 顶部导航 -->
    <header class="portal-header">
      <div class="header-inner">
        <div class="brand" @click="router.push('/portal')">
          <span class="brand-logo">🏀</span>
          <span class="brand-name">NBA 数据中心</span>
        </div>

        <nav class="nav-menu">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="header-right">
          <el-button
            v-if="!isClientLoggedIn"
            class="login-btn"
            size="default"
            @click="openLoginDialog"
          >
            登录
          </el-button>
          <el-dropdown v-else trigger="click" @command="handleCommand">
            <span class="user-chip">
              <span class="avatar">{{ (clientUsername || "U").charAt(0).toUpperCase() }}</span>
              {{ clientUsername }}
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="portal-main">
      <router-view />
    </main>

    <footer class="portal-footer">
      <span>NBA 数据中心 · 数据来源 SportsData / ESPN · 仅供学习交流</span>
    </footer>

    <!-- C 端登录弹窗（全局挂载一次） -->
    <ClientLoginDialog />
  </div>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowDown } from "@element-plus/icons-vue";
import { useClientAuth } from "@/composables/useClientAuth";
import ClientLoginDialog from "@/components/ClientLoginDialog.vue";

const router = useRouter();
const route = useRoute();
const { clientUsername, isClientLoggedIn, clientLogout, openLoginDialog } = useClientAuth();

const navItems = [
  { label: "首页", path: "/portal" },
  { label: "资讯", path: "/portal/news" },
  { label: "球员", path: "/portal/players" },
  { label: "球队", path: "/portal/teams" },
  { label: "赛程", path: "/portal/schedule" },
];

const isActive = (path: string) => {
  if (path === "/portal") {
    return route.path === "/portal";
  }
  return route.path.startsWith(path);
};

const handleCommand = async (command: string) => {
  if (command === "logout") {
    try {
      await ElMessageBox.confirm("确定退出登录吗？", "提示", {
        confirmButtonText: "退出",
        cancelButtonText: "取消",
        type: "warning",
      });
    } catch {
      return;
    }
    await clientLogout();
    ElMessage.success("已退出登录");
  }
};
</script>

<style scoped>
.portal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  color: #f1f5f9;
}

/* 顶部导航 */
.portal-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(15, 23, 42, 0.88);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 36px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.brand-logo {
  font-size: 22px;
}

.brand-name {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #fb923c, #f97316);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.nav-menu {
  display: flex;
  gap: 6px;
  flex: 1;
}

.nav-item {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 15px;
  color: #94a3b8;
  text-decoration: none;
  transition: all 0.2s;
}

.nav-item:hover {
  color: #f1f5f9;
  background: rgba(255, 255, 255, 0.06);
}

.nav-item.active {
  color: #fb923c;
  background: rgba(249, 115, 22, 0.12);
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
}

.login-btn {
  background: linear-gradient(135deg, #f97316, #ea580c);
  border: none;
  color: #fff;
  font-weight: 600;
  padding: 8px 22px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #e2e8f0;
  font-size: 14px;
  padding: 6px 10px;
  border-radius: 8px;
  transition: background 0.2s;
  outline: none;
}

.user-chip:hover {
  background: rgba(255, 255, 255, 0.06);
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316, #ea580c);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.arrow {
  font-size: 12px;
  color: #94a3b8;
}

/* 内容区 */
.portal-main {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

/* 底部 */
.portal-footer {
  text-align: center;
  padding: 18px 0;
  font-size: 12px;
  color: #64748b;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
</style>
