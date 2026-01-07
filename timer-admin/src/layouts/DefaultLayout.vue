<template>
  <div class="default-layout">
    <!-- 顶部导航栏 -->
    <header class="layout-header">
      <div class="header-left">
        <div class="logo">
          <img src="@/assets/logo.png" alt="系统Logo" />
          <span class="logo-text">Admin Dashboard</span>
        </div>
      </div>
      <div class="header-center">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          background-color="#fff"
          text-color="#333"
          active-text-color="#1890ff"
          class="header-menu"
        >
          <template v-for="item in menuList" :key="item.routePath">
            <el-menu-item :index="item.routePath" v-if="item.meta?.isDynamic">
              <span>{{ item.menuName }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
      <div class="header-right">
        <div class="user-info">
          <el-dropdown trigger="click">
            <span class="user-avatar">
              <img src="@/assets/avatar.png" alt="用户头像" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleProfile">个人资料</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <div class="layout-container">
      <!-- 侧边栏导航 -->
      <div class="layout-sidebar">
        <el-menu
          :default-active="activeMenu"
          :router="true"
          background-color="#2c3e50"
          text-color="#e0e0e0"
          active-text-color="#1890ff"
          class="sidebar-menu"
        >
          <template v-for="item in menuList" :key="item.routePath">
            <el-sub-menu
              v-if="item.children && item.children.length > 0"
              :index="item.routePath"
            >
              <template #title>
                <span>{{ item.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.routePath"
                :index="child.routePath"
              >
                {{ child.meta?.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.routePath">
              {{ item.meta?.title }}
            </el-menu-item>
          </template>
        </el-menu>
      </div>

      <!-- 主内容 -->
      <div class="layout-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/store/user';
import { ElMessage } from 'element-plus';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 当前激活的菜单项
const activeMenu = computed(() => {
  const { path } = route;
  return path;
});

// 获取当前菜单列表（从store中获取）
const menuList = computed(() => {
  return userStore.menuList;
});

// 退出登录
const handleLogout = () => {
  ElMessage.success('退出成功');
  router.push('/login');
};

// 跳转到个人资料
const handleProfile = () => {
  router.push('/profile');
};

// 初始化加载菜单
onMounted(() => {
  if (!userStore.token) {
    router.push('/login');
  }
});
</script>

<style scoped lang="scss">
.default-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', Arial, sans-serif;
}

.layout-header {
  display: flex;
  align-items: center;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 0 20px;
  height: 60px;
  position: relative;
  z-index: 100;

  .header-left {
    display: flex;
    align-items: center;
    flex: 1;
    .logo {
      display: flex;
      align-items: center;
      img {
        width: 32px;
        height: 32px;
        margin-right: 8px;
      }
      .logo-text {
        font-size: 20px;
        font-weight: bold;
        color: #1890ff;
      }
    }
  }

  .header-center {
    flex: 3;
    .header-menu {
      height: 60px;
      line-height: 60px;
      border: none;
      .el-menu-item {
        height: 60px;
        line-height: 60px;
        padding: 0 20px;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    flex: 1;
    justify-content: flex-end;
    .user-info {
      .user-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: #eef1f6;
        cursor: pointer;
        img {
          width: 100%;
          height: 100%;
          border-radius: 50%;
        }
      }
    }
  }
}

.layout-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: calc(100vh - 60px);

  .layout-sidebar {
    width: 240px;
    background: #2c3e50;
    overflow-y: auto;
    .sidebar-menu {
      border-right: none;
      height: 100%;
      .el-sub-menu__title {
        padding-left: 20px !important;
      }
      .el-menu-item {
        padding-left: 40px !important;
      }
    }
  }

  .layout-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    background: #f5f7fa;
    min-height: 100%;
  }
}

@media (max-width: 768px) {
  .layout-container {
    flex-direction: column;
    .layout-sidebar {
      width: 100%;
      height: auto;
      position: fixed;
      top: 60px;
      left: 0;
      z-index: 99;
      transform: translateX(-100%);
      transition: transform 0.3s ease;
      &.active {
        transform: translateX(0);
      }
    }
    .layout-content {
      padding-top: 20px;
    }
  }
}
</style>