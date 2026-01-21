<template>
  <div class="role-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">角色管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleAddRole">
          <el-icon><Plus /></el-icon> 添加角色
        </el-button>
      </div>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索角色名/描述"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 角色表格 -->
    <div class="table-container">
      <el-table
        :data="roles"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
      >
        <el-table-column prop="roleCode" label="角色编码" />
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column prop="updateTime" label="更新时间" />
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              size="small"
              type="primary"
              @click="handleAddRoleUser(row)"
              >添加人员</el-button
            >
            <el-button
              size="small"
              type="success"
              @click="handlePermission(row)"
              >权限配置</el-button
            >
            <el-button size="small" type="danger" @click="handleDelete(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页组件 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
    <AddRoleModal
      v-model="showAddRoleModal"
      :roleData="editingRole"
      @save="handleSaveRole"
      @edit="handleUpdateRole"
    />
    <AddRoleUserModal v-model="showAddRoleUserModal" :roleCode="roleCode" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import {
  ElTable,
  ElTableColumn,
  ElPagination,
  ElInput,
  ElButton,
  ElMessage,
  ElMessageBox,
} from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import { getRoleDetail, getRolePage } from "@/api/role";
import AddRoleModal from "./AddRoleModal.vue"; // 导入新增的组件
import AddRoleUserModal from "./AddRoleUserModal.vue"; // 导入新增的组件

// 定义角色类型
interface Role {
  id: number;
  roleName: string;
  roleCode: string;
}

// 定义角色用户类型
interface RoleUser {
  id: number;
  username: string;
  userId: string;
}

// 分页相关
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 搜索相关
const searchQuery = ref("");

// 角色数据
const roles = ref<Role[]>([]);
const roleUsers = ref<RoleUser[]>([]);
const loading = ref(false);
// 弹框相关
const showAddRoleModal = ref(false);
const editingRole = ref<Role | null>(null);
const showAddRoleUserModal = ref(false);
const roleCode = ref("");

onMounted(async () => {
  await fetchRoles();
});

// 获取角色数据
const fetchRoles = async () => {
  loading.value = true;
  try {
    // 这里是模拟数据，在实际项目中应替换为真实API调用
    const response = await getRolePage({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      searchQuery: searchQuery.value,
    });
    // 实际API调用示例：
    console.log("获取角色数据成功:", response);
    if (response.code === 200) {
      roles.value = response.data.roles || [];
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || "获取角色数据失败");
    }
  } catch (error) {
    console.error("获取角色数据失败:", error);
    ElMessage.error("获取角色数据失败");
  } finally {
    loading.value = false;
  }
};

const handleAddRole = () => {
  console.log("添加新角色");
  editingRole.value = null;
  showAddRoleModal.value = true;
};

// 操作方法
const handleEdit = async (row: Role) => {
  console.log("编辑角色:", row.roleCode);
  // 在实际项目中，应该跳转到编辑页面或打开编辑对话框
  try {
    const res = await getRoleDetail({
      roleCode: row.roleCode,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    editingRole.value = { ...res.data };
    showAddRoleModal.value = true; // 显示编辑弹框
  } catch (error) {
    console.log("获取用户详情失败");
  }
};

const handleAddRoleUser = async (row: Role) => {
  showAddRoleUserModal.value = true;
  roleCode.value = row.roleCode;
};

const handlePermission = (row: Role) => {
  console.log("配置权限:", row.roleCode);
  // 在实际项目中，应该跳转到权限配置页面
};

const handleDelete = async (row: Role) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色 "${row.roleName}" 吗？`,
      "确认删除",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    // 在实际项目中，这里应该调用删除API
    console.log("删除角色:", row);
    ElMessage.success("角色删除成功");
    // 删除成功后重新加载数据s
    fetchRoles();
  } catch (error) {
    console.log("取消删除");
  }
};

const handleSearch = () => {
  console.log("搜索:", searchQuery.value);
  // 实现搜索功能，通常需要重新调用API
  fetchRoles();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchRoles();
};

const handleCurrentChange = (page: number) => {
  currentPage.value = page;
  fetchRoles();
};
// 保存角色（新增）
const handleSaveRole = async (data: any) => {
  try {
    showAddRoleModal.value = false;
    fetchRoles(); // 重新加载数据
  } catch (error) {
    ElMessage.error("保存角色失败");
  }
};

// 更新角色
const handleUpdateRole = async (data: any) => {
  try {
    showAddRoleModal.value = false;
    fetchRoles(); // 重新加载数据
  } catch (error) {
    ElMessage.error("编辑角色失败");
  }
};
</script>

<style scoped>
.role-list-container {
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
</style>
