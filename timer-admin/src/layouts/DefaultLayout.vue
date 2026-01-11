<template>
  <el-container
    style="height: 100vh; background: linear-gradient(135deg, #0f172a, #1e293b)"
  >
    <!-- 顶部头部 - 深蓝色科技感主题 -->
    <el-header
      style="
        background: linear-gradient(135deg, #0f172a, #1e293b);
        color: #e2e8f0;
        display: flex;
        align-items: center;
        padding: 0 20px;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
        border-bottom: 1px solid #2d3748;
        position: relative;
        height: 60px;
      "
    >
      <div style="display: flex; align-items: center; flex: 1">
        <el-icon style="font-size: 24px; margin-right: 15px; color: #4cc9f0">
        </el-icon>
        <span
          style="
            font-family: 'Orbitron', 'Arial', sans-serif;
            font-size: 20px;
            font-weight: 700;
            letter-spacing: 1px;
            text-shadow: 0 0 15px rgba(76, 201, 240, 0.7),
              0 0 30px rgba(106, 137, 247, 0.5);
            background: linear-gradient(90deg, #4cc9f0, #6a89f7);
            background-clip: text; /* ✅ 标准属性 (Firefox/Edge/Chrome) */
            color: transparent; /* ✅ 标准属性 (所有浏览器) */
            margin-right: 12px;
            line-height: 36px;
            height: 36px;
          "
          >timer admin</span
        >
      </div>

      <div style="display: flex; align-items: center; gap: 15px">
        <el-tooltip content="搜索" placement="bottom">
          <el-input
            v-model="searchQuery"
            placeholder="搜索..."
            size="small"
            style="width: 200px"
            :suffix-icon="Search"
          />
        </el-tooltip>

        <el-dropdown>
          <span style="cursor: pointer; display: flex; align-items: center">
            <el-avatar
              :size="32"
              src="https://cube.elemecdn.com/0/88/03d0d0c4d8ab6e68bf7534a7c8164.png"
            />
            <span style="margin-left: 8px; color: #e2e8f0">Admin</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>个人中心</el-dropdown-item>
              <el-dropdown-item>设置</el-dropdown-item>
              <el-dropdown-item divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- 科技感光效 -->
      <div
        style="
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 2px;
          background: linear-gradient(90deg, #4cc9f0, #6a89f7, #4cc9f0);
          opacity: 0.7;
          box-shadow: 0 0 15px rgba(76, 201, 240, 0.5);
        "
      ></div>
    </el-header>

    <el-container>
      <!-- 侧边菜单 - 深蓝色科技感主题 -->
      <el-aside
        width="200px"
        style="border-right: 1px solid #2d3748; height: calc(100vh - 60px)"
      >
        <el-menu
          :default-active="route.path"
          class="el-menu-vertical"
          background-color="linear-gradient(135deg, #0f172a, #1e293b)"
          text-color="#e2e8f0"
          active-text-color="#4cc9f0"
          :router="true"
        >
          <template v-for="item in menuList" :key="item.routePath">
            <el-menu-item v-if="!item.children" :index="item.routePath">
              {{ item.menuName }}
            </el-menu-item>
            <el-sub-menu v-else :index="item.routePath">
              <template #title>{{ item.menuName }}</template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.routePath"
                :index="child.routePath"
              >
                {{ child.menuName }}
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-aside>

      <!-- 主内容区域 - 修复撑满问题 -->
      <el-container
        style="
          height: calc(100vh - 60px);
          display: flex;
          flex-direction: column;
        "
      >
        <el-main
          style="
            background: #ffffff;
            color: #333333;
            padding: 20px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
            border-radius: 16px;
            min-height: 100%;
            height: 100%;
            position: relative;
            flex: 1;
          "
        >
          <div
            style="
              position: absolute;
              top: 0;
              left: 0;
              right: 0;
              height: 2px;
              background: linear-gradient(90deg, #4cc9f0, #6a89f7, #4cc9f0);
              opacity: 0.4;
            "
          ></div>
          <router-view v-slot="{ Component }">
            <keep-alive>
              <component :is="Component" v-if="route.meta.keepAlive" />
            </keep-alive>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/store/user";
import { Search } from "@element-plus/icons-vue";
import { MenuTreeVO } from "@/proto";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const menuList = ref<MenuTreeVO[]>([]);

// 默认展开所有菜单
const defaultOpeneds = ref<string[]>([]);

// 获取菜单数据后设置默认展开
onMounted(() => {
  if (!userStore.menuList.length) {
    menuList.value = userStore.menuList;
  } else {
    const interval = setInterval(() => {
      if (userStore.menuList.length) {
        menuList.value = userStore.menuList;
        clearInterval(interval);
      }
    }, 100);
  }
  console.log(userStore.menuList);
});


const searchQuery = ref("");
</script>

<style>
/* 修复：全局重置 body 样式 - 关键修复 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  margin: 0;
  padding: 0;
  height: 100vh;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}
#app {
  height: 100%;
}

/* 深蓝色科技感主题样式 */
.el-header {
  font-size: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid #2d3748;
  height: 60px;
}

.el-aside {
  border-right: 1px solid #2d3748;
  height: calc(100vh - 60px);
}

.el-menu {
  border-right: none;
  border-radius: 0;
  overflow: hidden;
  height: 100%;
}

.el-menu-item {
  height: 44px;
  line-height: 44px;
  padding-left: 32px !important;
  transition: all 0.2s;
  border-radius: 0;
}

.el-sub-menu__title {
  height: 44px;
  line-height: 44px;
  padding-left: 22px !important;
  transition: all 0.2s;
  border-radius: 0;
}

/* 悬停效果 - 科技感渐变 */
.el-menu-item:hover,
.el-sub-menu__title:hover {
  background-color: rgba(76, 201, 240, 0.15) !important;
  color: #4cc9f0 !important;
  box-shadow: 0 0 15px rgba(76, 201, 240, 0.3);
  border-radius: 0;
}

/* 激活项效果 - 科技感强调 */
.el-menu-item.is-active,
.el-sub-menu__title.is-active {
  background-color: rgba(76, 201, 240, 0.25) !important;
  color: #4cc9f0 !important;
  box-shadow: 0 0 20px rgba(76, 201, 240, 0.4);
  border-radius: 0;
}

/* 路由链接样式 */
.el-menu-item a,
.el-sub-menu__title a {
  color: inherit;
  text-decoration: none;
  display: block;
  width: 100%;
  height: 100%;
  padding: 0 18px;
  transition: all 0.2s;
}

/* 科技感光效 - 侧边菜单 */
.el-menu-item::after,
.el-sub-menu__title::after {
  content: "";
  position: absolute;
  width: 3px;
  height: 100%;
  background: linear-gradient(to bottom, #4cc9f0, #6a89f7);
  right: 0;
  top: 0;
  opacity: 0;
  transition: opacity 0.3s;
}

.el-menu-item.is-active::after,
.el-sub-menu__title.is-active::after {
  opacity: 1;
}

/* 悬停光效 */
.el-menu-item:hover::after,
.el-sub-menu__title:hover::after {
  opacity: 0.7;
}

/* 主内容区域 - 修复撑满问题 */
.el-main {
  border-radius: 16px;
  overflow: hidden;
  transition: all 0.3s ease;
  min-height: 100%;
  height: 100%;
  position: relative;
  flex: 1;
}

.el-main:hover {
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

/* 科技感光效 - 主内容区域 */
.el-main::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, #4cc9f0, #6a89f7, #4cc9f0);
  opacity: 0.3;
}

span.timer-admin-text {
  font-family: "Orbitron", "Arial", sans-serif;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 0 15px rgba(76, 201, 240, 0.7),
    0 0 30px rgba(106, 137, 247, 0.5);
  background: linear-gradient(90deg, #4cc9f0, #6a89f7);
  background-clip: text; /* ✅ 标准属性 (关键修复!) */
  color: transparent; /* ✅ 标准属性 (关键修复!) */
  -webkit-text-fill-color: transparent;
  margin-right: 12px;
  line-height: 36px;
  height: 36px;
}
</style>
