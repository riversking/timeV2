<template>
  <div class="job-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">定时任务管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleAddJob">
          <el-icon><Plus /></el-icon> 添加任务
        </el-button>
      </div>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索任务名称"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 定时任务表格 -->
    <div class="table-container">
      <el-table
        :data="jobs"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
      >
        <el-table-column prop="jobName" label="任务名称" min-width="180" />
        <el-table-column prop="cronExpression" label="Cron表达式" min-width="150" />
        <el-table-column prop="targetUrl" label="目标URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'RUNNING' ? 'success' : row.status === 'PAUSED' ? 'warning' : 'danger'"
              size="small"
              effect="plain"
            >
              {{ row.status === 'RUNNING' ? '运行中' : row.status === 'PAUSED' ? '已暂停' : '停止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column prop="lastExecuteTime" label="最后执行时间" width="160" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button 
              size="small" 
              type="success" 
              v-if="row.status !== 'RUNNING'" 
              @click="handleStart(row)"
            >
              启动
            </el-button>
            <el-button 
              size="small" 
              type="warning" 
              v-if="row.status === 'RUNNING'" 
              @click="handlePause(row)"
            >
              暂停
            </el-button>
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

    <!-- 添加/编辑任务模态框 -->
    <AddJobModal
      v-model="showAddJobModal"
      :jobData="editingJob"
      @save="handleSaveJob"
      @edit="handleUpdateJob"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from "vue";
import {
  ElTable,
  ElTableColumn,
  ElTag,
  ElPagination,
  ElInput,
  ElButton,
  ElMessage,
  ElMessageBox,
} from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import { 
  getJobPage, 
  saveJob, 
  updateJob, 
  deleteJob, 
  startJob, 
  pauseJob 
} from "@/api/job";
import AddJobModal from "./AddJobModal.vue";

// 搜索条件
const searchQuery = ref("");

// 分页参数
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 数据列表
const jobs = ref<any[]>([]);
const loading = ref(false);

// 模态框控制
const showAddJobModal = ref(false);
const editingJob = ref<any>(null);

// 获取任务列表
const fetchJobList = async () => {
  loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      jobName: searchQuery.value || undefined
    };
    const response = await getJobPage(params);
    jobs.value = response.records || [];
    total.value = response.total || 0;
  } catch (error) {
    ElMessage.error("获取任务列表失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  currentPage.value = 1;
  fetchJobList();
};

// 分页事件
const handleSizeChange = (val: number) => {
  pageSize.value = val;
  fetchJobList();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchJobList();
};

// 添加任务
const handleAddJob = () => {
  editingJob.value = null;
  showAddJobModal.value = true;
};

// 编辑任务
const handleEdit = (row: any) => {
  editingJob.value = { ...row };
  showAddJobModal.value = true;
};

// 保存任务
const handleSaveJob = async (jobData: any) => {
  try {
    await saveJob(jobData);
    ElMessage.success("任务添加成功");
    showAddJobModal.value = false;
    fetchJobList();
  } catch (error) {
    ElMessage.error("任务添加失败");
  }
};

// 更新任务
const handleUpdateJob = async (jobData: any) => {
  try {
    await updateJob(jobData);
    ElMessage.success("任务更新成功");
    showAddJobModal.value = false;
    fetchJobList();
  } catch (error) {
    ElMessage.error("任务更新失败");
  }
};

// 删除任务
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除任务 "${row.jobName}" 吗？`, "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(async () => {
    try {
      await deleteJob(row.id);
      ElMessage.success("任务删除成功");
      fetchJobList();
    } catch (error) {
      ElMessage.error("任务删除失败");
    }
  });
};

// 启动任务
const handleStart = async (row: any) => {
  try {
    await startJob(row.id);
    ElMessage.success("任务启动成功");
    fetchJobList();
  } catch (error) {
    ElMessage.error("任务启动失败");
  }
};

// 暂停任务
const handlePause = async (row: any) => {
  try {
    await pauseJob(row.id);
    ElMessage.success("任务暂停成功");
    fetchJobList();
  } catch (error) {
    ElMessage.error("任务暂停失败");
  }
};

// 初始化数据
onMounted(() => {
  fetchJobList();
});

// 对话框标题
const dialogTitle = computed(() => {
  return editingJob.value ? "编辑定时任务" : "添加定时任务";
});
</script>

<style scoped>
.job-list-container {
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa, #e4edf5);
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e6ed;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  margin-bottom: 20px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>