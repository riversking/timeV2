<template>
  <div class="menu-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">菜单管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleAddMenu">
          <el-icon><Plus /></el-icon> 添加菜单
        </el-button>
        <el-button type="success" @click="toggleSelection">
          <el-icon><CircleCheck /></el-icon>
          {{ isIndeterminate ? "部分选择" : checkAll ? "取消全选" : "全选" }}
        </el-button>
        <el-button type="danger" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon> 批量删除
        </el-button>
      </div>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索菜单名/编码"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 菜单树形表格 -->
    <div class="table-container">
      <el-table
        ref="multipleTableRef"
        :data="filteredMenus"
        style="width: 100%"
        row-key="menuCode"
        :tree-props="{
          children: 'children',
          hasChildren: 'hasChildren',
          checkStrictly: true,
        }"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        :default-expand-all="true"
      >
        <el-table-column type="selection" />
        <el-table-column prop="menuName" label="菜单名称" width="200">
          <template #default="{ row }">
            <span>{{ row.menuName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="menuCode" label="菜单编码" />
        <el-table-column prop="routePath" label="路由路径" />
        <el-table-column prop="sortOrder" label="排序" />
        <el-table-column label="操作">
          <template #default="{ row, $index }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="handleSaveChild(row)"
              >新增子菜单</el-button
            >
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(row, $index)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>
    <AddMenuModal
      v-model="showAddMenuModal"
      :menuData="editingMenuData"
      @save="handleSaveMenu"
      @edit="handleEditMenu"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import {
  ElTable,
  ElTableColumn,
  ElInput,
  ElButton,
  ElMessage,
  ElMessageBox,
  ElIcon,
} from "element-plus";
import { Plus, Search, Delete, CircleCheck } from "@element-plus/icons-vue";
import {
  getMenuTree,
  deleteMenu,
  getMenuDetail,
  saveMenu,
  updateMenu,
  deleteMenus,
} from "@/api/menu";
import AddMenuModal from "./AddMenuModal.vue";

// 定义菜单类型
interface Menu {
  id?: number;
  menuCode: string;
  menuName: string;
  routePath: string;
  sortOrder: number;
  parentId?: number;
  icon?: string;
  component?: string;
  children?: Menu[];
}

// 搜索相关
const searchQuery = ref("");

// 菜单数据
const menus = ref<Menu[]>([]);
const filteredMenus = ref<Menu[]>([]);
const loading = ref(false);

// 选中相关
const multipleSelection = ref<Menu[]>([]);
const checkAll = ref(false);
const isIndeterminate = ref(false);

// 弹窗相关
const showAddMenuModal = ref(false);
const parentId = ref(0);
const editingMenuData = ref<Menu>({
  id: 0,
  parentId: 0,
  menuCode: "",
  menuName: "",
  routePath: "",
  sortOrder: 0,
  icon: "",
  component: "",
});

// 级联选择器配置
const cascaderProps = {
  value: "menuCode",
  label: "menuName",
  checkStrictly: true,
};

// 获取所有菜单项（包含子菜单）
const getAllMenuItems = (menuList: Menu[]): Menu[] => {
  let items: Menu[] = [];
  menuList.forEach((menu) => {
    items.push(menu);
    if (menu.children && menu.children.length) {
      items = items.concat(getAllMenuItems(menu.children));
    }
  });
  return items;
};

// 获取菜单数据
const fetchMenus = async () => {
  loading.value = true;
  try {
    const response = await getMenuTree({ key: searchQuery.value });
    if (response.code === 200) {
      menus.value = response.data;
      filteredMenus.value = response.data;
    } else {
      ElMessage.error(response.message || "获取菜单数据失败");
    }
  } catch (error) {
    console.error("获取菜单数据失败:", error);
    ElMessage.error("获取菜单数据失败");
  } finally {
    loading.value = false;
  }
};

// 搜索菜单
const handleSearch = () => {};

// 添加菜单
const handleAddMenu = () => {
  editingMenuData.value = {
    menuCode: "",
    menuName: "",
    routePath: "",
    sortOrder: 0,
    icon: "",
    component: "",
  };
  parentId.value = 0;
  showAddMenuModal.value = true;
};

const handleSaveChild = (row: Menu) => {
  console.log("保存子菜单:", row.id);
  editingMenuData.value = {
    menuCode: "",
    menuName: "",
    routePath: "",
    sortOrder: 0,
    parentId: 0,
    icon: "",
    component: "",
  };
  parentId.value = row.id || 0;
  showAddMenuModal.value = true;
};

// 编辑菜单
const handleEdit = async (row: Menu) => {
  try {
    const response = await getMenuDetail({ menuCode: row.menuCode });
    if (response.code !== 200) {
      ElMessage.error(response.message || "获取菜单详情失败");
      return;
    }
    editingMenuData.value = { ...row };
  } catch (error: any) {
    console.error("获取菜单详情失败:", error);
    ElMessage.error(error.message || "获取菜单详情失败");
  } finally {
    showAddMenuModal.value = true;
  }
};

const handleSaveMenu = async (data: any) => {
  try {
    console.log("保存菜单数据:", data);
    // 调用保存菜单接口
    const param = {
      menuCode: data.menuCode,
      menuName: data.menuName,
      routePath: data.routePath,
      sortOrder: data.sortOrder,
      parentId: parentId.value,
      icon: data.icon,
      component: data.component,
    };
    const res = await saveMenu(param);
    if (res.code != 200) {
      ElMessage.error(res.message);
      return;
    }
    ElMessage.success("菜单保存成功");
    fetchMenus(); // 重新加载数据
  } catch (error) {
    console.error("保存菜单失败:", error);
    ElMessage.error("保存菜单失败");
  } finally {
    showAddMenuModal.value = false;
  }
};

const handleEditMenu = async (data: any) => {
  try {
    const param = {
      id: data.id,
      menuCode: data.menuCode,
      menuName: data.menuName,
      routePath: data.routePath,
      sortOrder: data.sortOrder,
      parentId: data.parentId,
      icon: data.icon,
      component: data.component,
    };
    const res = await updateMenu(param);
    if (res.code != 200) {
      ElMessage.error(res.message);
      return;
    }
    ElMessage.success("菜单更新成功");
    fetchMenus(); // 重新加载数据
  } catch (error) {
    console.error("更新菜单失败:", error);
    ElMessage.error("更新菜单失败");
  } finally {
    showAddMenuModal.value = false;
  }
};

// 删除菜单
const handleDelete = async (row: Menu, index: number) => {
  await ElMessageBox.confirm(
    `确定要删除菜单 "${row.menuName}" 吗？`,
    "确认删除",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  );
  try {
    const response = await deleteMenu({ menuCode: row.menuCode });
    if (response.code === 200) {
      ElMessage.success("菜单删除成功");
      fetchMenus(); // 重新加载数据
    } else {
      ElMessage.error(response.message || "删除失败");
    }
  } catch (error) {
    console.log("取消删除");
  }
};

// 批量删除
const handleBatchDelete = async () => {
  if (multipleSelection.value.length === 0) {
    ElMessage.warning("请至少选择一项");
    return;
  }
  await ElMessageBox.confirm(
    `确定要删除选中的 ${multipleSelection.value.length} 项吗？`,
    "确认删除",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  );
  try {
    // 批量删除逻辑
    const res = await deleteMenus({
      menuCodes: multipleSelection.value.map((item) => item.menuCode),
    });
    if (res.code !== 200) {
      ElMessage.error("批量删除失败");
      return;
    }
    ElMessage.success("批量删除成功");
    fetchMenus(); // 重新加载数据
  } catch (error) {
    console.log("取消批量删除");
    ElMessage.error("批量删除失败");
  }
};

// 表格选中状态变化
const handleSelectionChange = (val: Menu[]) => {
  multipleSelection.value = val;
  console.log("表格选中状态变化", multipleSelection.value[0]);
  const selectedCount = multipleSelection.value.length;
  const allMenuItems = getAllMenuItems(filteredMenus.value);
  const totalCount = allMenuItems.length;
  checkAll.value = selectedCount === totalCount && totalCount > 0;
  isIndeterminate.value = selectedCount > 0 && selectedCount < totalCount;
};

// 全选/取消全选
const toggleSelection = () => {
  const allMenuItems = getAllMenuItems(filteredMenus.value);
  if (checkAll.value) {
    // 取消全选
    multipleTableRef.value.clearSelection();
    checkAll.value = false;
    isIndeterminate.value = false;
  } else {
    // 全选
    allMenuItems.forEach((item) => {
      multipleTableRef.value.toggleRowSelection(item, true);
    });
    checkAll.value = true;
    isIndeterminate.value = false;
  }
};

// 表格引用
const multipleTableRef = ref();

onMounted(() => {
  fetchMenus();
});
</script>

<style scoped>
.menu-list-container {
  background: #ffffff;
  min-height: 100vh;
  padding: 20px;
  color: #333333;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.title {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  background: #f5f7fa;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.table-container {
  background: #ffffff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: right;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
