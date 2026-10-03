<template>
  <div class="nba-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">球馆管理</h1>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索球馆名称"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 球馆表格 -->
    <div class="table-container">
      <el-table
        :data="stadiums"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
      >
        <el-table-column prop="stadiumId" label="球馆ID" />
        <el-table-column prop="name" label="球馆名称" width="230" show-overflow-tooltip />
        <el-table-column prop="city" label="城市" />
        <el-table-column prop="state" label="州/省" />
        <el-table-column prop="country" label="国家"  />
        <el-table-column prop="capacity" label="容量" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small" effect="plain">
              {{ row.active ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleViewDetail(row)">
              详情
            </el-button>
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

    <!-- 球馆详情弹窗 -->
    <el-dialog v-model="detailVisible" title="球馆详情" width="640px">
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border v-if="currentStadium">
          <el-descriptions-item label="球馆ID">{{ currentStadium.stadiumId }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ currentStadium.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentStadium.active ? "启用" : "停用" }}</el-descriptions-item>
          <el-descriptions-item label="容量">{{ currentStadium.capacity }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ currentStadium.address || "-" }}</el-descriptions-item>
          <el-descriptions-item label="城市">{{ currentStadium.city || "-" }}</el-descriptions-item>
          <el-descriptions-item label="州/省">{{ currentStadium.state || "-" }}</el-descriptions-item>
          <el-descriptions-item label="邮编">{{ currentStadium.zip || "-" }}</el-descriptions-item>
          <el-descriptions-item label="国家">{{ currentStadium.country || "-" }}</el-descriptions-item>
          <el-descriptions-item label="纬度">{{ currentStadium.geoLat }}</el-descriptions-item>
          <el-descriptions-item label="经度">{{ currentStadium.geoLong }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import { getStadiumDetail, getStadiumPage } from "@/api/nba";

// 搜索条件
const searchQuery = ref("");

// 分页参数
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 数据列表
const stadiums = ref<any[]>([]);
const loading = ref(false);

// 详情弹窗
const detailVisible = ref(false);
const detailLoading = ref(false);
const currentStadium = ref<any>(null);

// 获取球馆列表
const fetchStadiumList = async () => {
  loading.value = true;
  try {
    const params = {
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      name: searchQuery.value || undefined,
    };
    const response = await getStadiumPage(params);
    if (response.code !== 200) {
      ElMessage.error(response.message || "获取球馆列表失败");
      return;
    }
    stadiums.value = response.data.stadiums || [];
    total.value = response.data.total || 0;
  } catch (error) {
    ElMessage.error("获取球馆列表失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// 查看详情（调用详情接口）
const handleViewDetail = async (row: any) => {
  detailVisible.value = true;
  detailLoading.value = true;
  currentStadium.value = null;
  try {
    const response = await getStadiumDetail({ stadiumId: row.stadiumId });
    if (response.code === 200) {
      currentStadium.value = response.data.stadium || null;
    } else {
      ElMessage.error(response.message || "获取球馆详情失败");
    }
  } catch (error) {
    ElMessage.error("获取球馆详情失败");
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  currentPage.value = 1;
  fetchStadiumList();
};

// 分页事件
const handleSizeChange = (val: number) => {
  pageSize.value = val;
  fetchStadiumList();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchStadiumList();
};

onMounted(() => {
  fetchStadiumList();
});
</script>

<style scoped>
.nba-list-container {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px 24px;
  background: linear-gradient(135deg, #1f3a8a 0%, #2563eb 100%);
  border-radius: 8px;
}

.header .title {
  margin: 0;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-bar .el-input {
  width: 280px;
}

.table-container {
  margin-bottom: 16px;
}

.detail-body {
  min-height: 120px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
