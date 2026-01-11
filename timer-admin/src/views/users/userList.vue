<template>
  <div class="user-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">用户管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleAddUser">
          <el-icon><Plus /></el-icon> 添加用户
        </el-button>
      </div>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索用户名/邮箱"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 用户表格 -->
    <div class="table-container">
      <el-table
        :data="users"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
      >
        <el-table-column prop="nickname" label="姓名" width="180" />
        <el-table-column prop="username" label="用户名" width="180" />
        <el-table-column prop="mail" label="邮箱" />
        <el-table-column prop="phone" label="电话" />
        <el-table-column prop="isEnable" label="是否禁用" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.isEnable"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElTable, ElTableColumn, ElTag, ElSwitch, ElPagination, ElInput, ElButton, ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { getUserPage } from '@/api/user';

// 定义用户类型
interface User {
  id: number;
  name: string;
  email: string;
  role: string;
  status: number; // 1: 启用, 0: 禁用
}

// 分页相关
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 搜索相关
const searchQuery = ref('');

// 用户数据
const users = ref<User[]>([]);
const loading = ref(false);

// 获取用户数据
const fetchUsers = async () => {
  loading.value = true;
  try {
    // 调用后端接口获取用户分页数据
    const response = await getUserPage({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
    });
    console.log('获取用户数据成功:', response);
    if (response.code === 200) {
      users.value = response.data.users || [];
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || '获取用户数据失败');
    }
  } catch (error) {
    console.error('获取用户数据失败:', error);
    ElMessage.error('获取用户数据失败');
  } finally {
    loading.value = false;
  }
};

// 初始加载数据
onMounted(() => {
  fetchUsers();
});

// 角色类型映射
const roleType = (role: string) => {
  const types = {
    '管理员': 'danger',
    '普通用户': 'success'
  };
  return types[role as keyof typeof types] || 'info';
};

// 操作方法
const handleAddUser = () => {
  // 跳转到添加用户页面
  console.log('添加新用户');
  // 在实际项目中，应该使用 router.push('/user-add')
};

const handleEdit = (row: User) => {
  console.log('编辑用户:', row);
  // 在实际项目中，应该使用 router.push(`/user-edit/${row.id}`)
};

const handleDelete = async (row: User) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${row.name}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    
    // 在实际项目中，这里应该调用删除API
    console.log('删除用户:', row);
    ElMessage.success('用户删除成功');
    // 删除成功后重新加载数据
    fetchUsers();
  } catch (error) {
    console.log('取消删除');
  }
};

const handleStatusChange = async (row: User) => {
};

const handleSearch = () => {
  console.log('搜索:', searchQuery.value);
  // 实现搜索功能，通常需要重新调用API
  fetchUsers();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchUsers();
};

const handleCurrentChange = (page: number) => {
  currentPage.value = page;
  fetchUsers();
};
</script>

/* ... existing code ... */
/* ... existing code ... */
<style scoped>
.user-list-container {
  /* Changed from dark gradient to white background */
  background: #ffffff;
  min-height: 100vh;
  padding: 20px;
  color: #333333; /* Changed text color to dark for contrast */
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0; /* Lighter border for white theme */
}

.title {
  font-size: 24px;
  font-weight: bold;
  color: #409eff; /* Blue color that works well on white background */
  margin: 0;
}
/* ... existing code ... */

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  background: #f5f7fa; /* Light gray background instead of dark */
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); /* Softer shadow */
}

.table-container {
  background: #ffffff; /* White background for table container */
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); /* Softer shadow */
}

.pagination {
  margin-top: 20px; /* Add space between table and pagination */
  display: flex;
  justify-content: right;
}

/* ... existing code ... */

/* White theme tags */
.el-tag {
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  color: #409eff;
}

.el-tag--success {
  background: #f0f9ff;
  border-color: #b3d8ff;
  color: #67c23a;
}

.el-tag--danger {
  background: #fef0f0;
  border-color: #fbc4c4;
  color: #f56c6c;
}
</style>