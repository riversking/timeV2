<template>
  <el-container>
    <el-aside width="200px">
      <el-menu
        default-active="/home"
        class="el-menu-vertical"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <!-- 修复：1. 用 router-link 代替 location 2. 添加 #title 3. index 添加默认值 -->
        <template v-for="item in userStore.menuList" :key="item.id">
          <el-sub-menu
            v-if="item.children && item.children.length > 0"
            :index="item.menuCode || 'default'"
          >
            <template #title>
              {{ item.menuName }}
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.id"
              :index="child.menuCode || 'default'"
            >
              <RouterView :to="child.routePath || '/'">
                {{ child.menuName }}
              </RouterView>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item
            v-else
            :index="item.menuCode || 'default'"
          >
            <router-link :to="item.routePath || '/'">
              {{ item.menuName }}
            </router-link>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/store/user';

const router = useRouter();
const userStore = useUserStore();

const activeMenu = computed(() => {
  return router.currentRoute.value.path;
});

// const handleLogout = async () => {
//   try {
//     await logout();
//     localStorage.removeItem('token');
//     userStore.$reset();
//     router.push('/login');
//     ElMessage.success('退出成功');
//   } catch (error) {
//     ElMessage.error('退出失败');
//   }
// };

// 在组件挂载时获取菜单（如果之前未获取）
onMounted(() => {
  if (!userStore.menuList.length) {
    userStore.fetchMenu();
  }
});
</script>

<style scoped>
.home-container {
  height: 100vh;
  overflow: hidden;
}

.el-menu-vertical {
  height: 100%;
  border-right: none;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fff;
  padding: 0 20px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.header-left h1 {
  font-size: 20px;
  color: #333;
}

.header-right .el-dropdown-link {
  cursor: pointer;
  color: #333;
}
</style>