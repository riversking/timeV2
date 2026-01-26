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
        <el-table-column prop="orderNum" label="排序" />
        <el-table-column label="操作">
          <template #default="{ row, $index }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import {
  ElTable,
  ElTableColumn,
  ElPagination,
  ElInput,
  ElButton,
  ElMessage,
  ElMessageBox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElCascader,
  ElIcon,
} from "element-plus";
import { Plus, Search, Delete, CircleCheck } from "@element-plus/icons-vue";
import { getMenuTree, saveMenu, deleteMenu } from "@/api/menu";
import { MenuTreeVO } from "@/proto";

// 定义菜单类型
interface Menu {
  menuCode: string;
  menuName: string;
  routePath: string;
  orderNum: number;
  parentId?: string;
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
const showMenuModal = ref(false);
const editingMenu = ref<Menu>({
  menuCode: "",
  menuName: "",
  routePath: "",
  orderNum: 0,
});
const menuFormRef = ref();

// 表单验证规则
const menuFormRules = {
  menuName: [{ required: true, message: "请输入菜单名称", trigger: "blur" }],
  menuCode: [{ required: true, message: "请输入菜单编码", trigger: "blur" }],
};

// 级联选择器配置
const cascaderProps = {
  value: "menuCode",
  label: "menuName",
  checkStrictly: true,
};

// 计算所有菜单的数量（包含子菜单）
const getAllMenuCount = (menuList: Menu[]): number => {
  let count = 0;
  menuList.forEach(menu => {
    count++;
    if (menu.children && menu.children.length) {
      count += getAllMenuCount(menu.children);
    }
  });
  return count;
};

// 获取所有菜单项（包含子菜单）
const getAllMenuItems = (menuList: Menu[]): Menu[] => {
  let items: Menu[] = [];
  menuList.forEach(menu => {
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
const handleSearch = () => {
  // 过滤菜单数据
  if (!searchQuery.value) {
    filteredMenus.value = menus.value;
  } else {
    const filterFunction = (menuList: Menu[]): Menu[] => {
      return menuList
        .filter((menu) => {
          const match =
            menu.menuName.includes(searchQuery.value) ||
            menu.menuCode.includes(searchQuery.value);
          if (menu.children && menu.children.length) {
            const filteredChildren = filterFunction(menu.children);
            if (filteredChildren.length) {
              // 如果子菜单匹配，也要包含父菜单
              return true;
            }
          }
          return match;
        })
        .map((menu) => {
          // 保留匹配的子菜单
          if (menu.children && menu.children.length) {
            return {
              ...menu,
              children: filterFunction(menu.children),
            };
          }
          return menu;
        });
    };

    filteredMenus.value = filterFunction(menus.value);
  }
};

// 添加菜单
const handleAddMenu = () => {
  editingMenu.value = {
    menuCode: "",
    menuName: "",
    routePath: "",
    orderNum: 0,
  };
  showMenuModal.value = true;
};

// 编辑菜单
const handleEdit = (row: Menu) => {
  editingMenu.value = { ...row };
  showMenuModal.value = true;
};

// 删除菜单
const handleDelete = async (row: Menu, index: number) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除菜单 "${row.menuName}" 吗？`,
      "确认删除",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

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

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${multipleSelection.value.length} 项吗？`,
      "确认删除",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    // 批量删除逻辑
    for (const menu of multipleSelection.value) {
      await deleteMenu({ menuCode: menu.menuCode });
    }

    ElMessage.success("批量删除成功");
    fetchMenus(); // 重新加载数据
  } catch (error) {
    console.log("取消批量删除");
  }
};

// 保存菜单
const handleSaveMenu = async () => {
  try {
    await menuFormRef.value.validate();

    const response = await saveMenu(editingMenu.value);
    if (response.code === 200) {
      ElMessage.success("保存成功");
      showMenuModal.value = false;
      fetchMenus(); // 重新加载数据
    } else {
      ElMessage.error(response.message || "保存失败");
    }
  } catch (error) {
    console.error("保存菜单失败:", error);
    ElMessage.error("请检查输入信息");
  }
};

// 表格选中状态变化
const handleSelectionChange = (val: Menu[]) => {
  multipleSelection.value = val;

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
    allMenuItems.forEach(item => {
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