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
        prefix-icon="el-icon-search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 用户表格 -->
    <div class="table-container">
      <el-table
        :data="filteredUsers"
        style="width: 100%"
        :header-cell-style="{ background: '#1a2a4d', color: '#fff' }"
        :cell-style="{ backgroundColor: '#0f1a2e', color: '#e6e6e6' }"
      >
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="name" label="姓名" width="180" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleType(row.role)">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
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
        :total="totalUsers"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { ElTable, ElTableColumn, ElTag, ElSwitch, ElPagination, ElInput, ElButton } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';

// 模拟用户数据
const users = ref([
  { id: 1, name: '张三', email: 'zhangsan@example.com', role: '管理员', status: 1 },
  { id: 2, name: '李四', email: 'lisi@example.com', role: '普通用户', status: 1 },
  { id: 3, name: '王五', email: 'wangwu@example.com', role: '普通用户', status: 0 },
  { id: 4, name: '赵六', email: 'zhaoliu@example.com', role: '管理员', status: 1 },
  { id: 5, name: '孙七', email: 'sunqi@example.com', role: '普通用户', status: 0 }
]);

// 分页相关
const currentPage = ref(1);
const pageSize = ref(10);
const totalUsers = computed(() => users.value.length);

// 搜索相关
const searchQuery = ref('');

// 过滤后的用户列表
const filteredUsers = computed(() => {
  return users.value.filter(user => {
    return (
      user.name.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      user.email.toLowerCase().includes(searchQuery.value.toLowerCase())
    );
  });
});

// 角色类型映射
const roleType = (role) => {
  const types = {
    '管理员': 'danger',
    '普通用户': 'success'
  };
  return types[role] || 'info';
};

// 操作方法
const handleAddUser = () => {
  // 跳转到添加用户页面
  console.log('添加新用户');
  // 在实际项目中，应该使用 router.push('/user-add')
};

const handleEdit = (row) => {
  console.log('编辑用户:', row);
  // 在实际项目中，应该使用 router.push(`/user-edit/${row.id}`)
};

const handleDelete = (row) => {
  console.log('删除用户:', row);
  // 在实际项目中，应该调用删除API
};

const handleStatusChange = (row) => {
  console.log('状态变更:', row);
  // 在实际项目中，应该调用API更新状态
};

const handleSearch = () => {
  console.log('搜索:', searchQuery.value);
};

const handleSizeChange = (size) => {
  pageSize.value = size;
  currentPage.value = 1;
};

const handleCurrentChange = (page) => {
  currentPage.value = page;
};
</script>

<style scoped>
.user-list-container {
  background: linear-gradient(135deg, #0f1a2e, #1a2a4d);
  min-height: 100vh;
  padding: 20px;
  color: #e6e6e6;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #2d3a5b;
}

.title {
  font-size: 24px;
  font-weight: bold;
  color: #4fc08d;
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
  background: #0a1524;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
}

.table-container {
  background: #0a1524;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 深蓝科技感标签样式 */
.el-tag {
  background: #1a2a4d;
  border: 1px solid #2d3a5b;
  color: #4fc08d;
}

.el-tag--success {
  color: #4fc08d;
}

.el-tag--danger {
  color: #ff6b6b;
}
</style>