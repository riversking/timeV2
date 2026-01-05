<template>
  <div class="home-container">
    <div class="sidebar">
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical"
        :collapse="isCollapse"
        router
      >
        <template v-for="menu in menuList" :key="menu.id">
          <el-menu-item v-if="!menu.children" :index="menu.routePath">
            <el-icon v-if="menu.icon">
              <component :is="menu.icon" />
            </el-icon>
            <span>{{ menu.menuName }}</span>
          </el-menu-item>
          
          <el-sub-menu v-else :index="menu.routePath">
            <template #title>
              <el-icon v-if="menu.icon">
                <component :is="menu.icon" />
              </el-icon>
              <span>{{ menu.menuName }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.id"
              :index="child.routePath"
            >
              {{ child.menuName }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </div>
    
    <div class="main-content">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useUserStore } from '@/store/user';
import { Menu, MenuUnfold, Setting } from '@element-plus/icons-vue';

const userStore = useUserStore();
const isCollapse = ref(false);
const activeMenu = ref('');

const menuList = computed(() => {
  return userStore.menuList.map(menu => ({
    ...menu,
    routePath: menu.routePath || menu.menuCode.toLowerCase().replace(/_/g, '-')
  }));
});

const router = useRouter();
router.afterEach((to) => {
  activeMenu.value = to.path;
});

onMounted(() => {
  if (!userStore.isMenuLoaded) {
    userStore.loadMenuFromStorage();
  }
});
</script>

<style scoped>
.home-container {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #0f1a2e, #1a2a4d);
}

.sidebar {
  width: 250px;
  height: 100vh;
  background: #0a1524;
  border-right: 1px solid #2d3a5b;
  transition: width 0.3s;
}

.main-content {
  flex: 1;
  padding: 20px;
  background: #0f1a2e;
  overflow: auto;
}

.el-menu-vertical {
  border-right: none;
}

.el-menu-item, .el-sub-menu__title {
  color: #e6e6e6 !important;
  background: #0a1524 !important;
}

.el-menu-item:hover, .el-sub-menu__title:hover {
  background: #1a2a4d !important;
}

.el-menu-item.is-active, .el-sub-menu__title.is-active {
  background: #1a2a4d !important;
  color: #4fc08d !important;
}
</style>