<template>
  <div class="nba-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">球队管理</h1>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索球队名称/城市"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 球队表格 -->
    <div class="table-container">
      <el-table
        :data="teams"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
      >
        <el-table-column prop="teamId" label="球队ID" />
        <el-table-column prop="name" label="球队名" width="180" show-overflow-tooltip />
        <el-table-column prop="key" label="缩写"/>
        <el-table-column prop="city" label="城市" />
        <el-table-column prop="conference" label="分区" />
        <el-table-column prop="division" label="赛区"/>
        <el-table-column prop="headCoach" label="主教练" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small" effect="plain">
              {{ row.active ? "活跃" : "停用" }}
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

    <!-- 球队详情弹窗 -->
    <el-dialog v-model="detailVisible" title="球队详情" width="720px">
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border v-if="currentTeam">
          <el-descriptions-item label="球队ID">{{ currentTeam.teamId }}</el-descriptions-item>
          <el-descriptions-item label="球队名">{{ currentTeam.name }}</el-descriptions-item>
          <el-descriptions-item label="缩写">{{ currentTeam.key || "-" }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentTeam.active ? "活跃" : "停用" }}</el-descriptions-item>
          <el-descriptions-item label="城市">{{ currentTeam.city || "-" }}</el-descriptions-item>
          <el-descriptions-item label="联盟ID">{{ currentTeam.leagueId }}</el-descriptions-item>
          <el-descriptions-item label="球馆ID">{{ currentTeam.stadiumId }}</el-descriptions-item>
          <el-descriptions-item label="分区">{{ currentTeam.conference || "-" }}</el-descriptions-item>
          <el-descriptions-item label="赛区">{{ currentTeam.division || "-" }}</el-descriptions-item>
          <el-descriptions-item label="主教练">{{ currentTeam.headCoach || "-" }}</el-descriptions-item>
          <el-descriptions-item label="主色">{{ currentTeam.primaryColor || "-" }}</el-descriptions-item>
          <el-descriptions-item label="副色">{{ currentTeam.secondaryColor || "-" }}</el-descriptions-item>
          <el-descriptions-item label="第三色">{{ currentTeam.tertiaryColor || "-" }}</el-descriptions-item>
          <el-descriptions-item label="第四色">{{ currentTeam.quaternaryColor || "-" }}</el-descriptions-item>
          <el-descriptions-item label="NBA官网ID">{{ currentTeam.nbaDotComTeamId }}</el-descriptions-item>
          <el-descriptions-item label="全球球队ID">{{ currentTeam.globalTeamId }}</el-descriptions-item>
          <el-descriptions-item label="Logo地址" :span="2">
            <a v-if="currentTeam.wikipediaLogoUrl" :href="currentTeam.wikipediaLogoUrl" target="_blank" rel="noopener">
              {{ currentTeam.wikipediaLogoUrl }}
            </a>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="logo-box" v-if="currentTeam?.wikipediaLogoUrl">
          <el-image
            :src="currentTeam.wikipediaLogoUrl"
            fit="contain"
            style="width: 120px; height: 120px"
            :preview-src-list="[currentTeam.wikipediaLogoUrl]"
            preview-teleported
          />
        </div>
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
import { getTeamDetail, getTeamPage } from "@/api/nba";

// 搜索条件
const searchQuery = ref("");

// 分页参数
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 数据列表
const teams = ref<any[]>([]);
const loading = ref(false);

// 详情弹窗
const detailVisible = ref(false);
const detailLoading = ref(false);
const currentTeam = ref<any>(null);

// 获取球队列表
const fetchTeamList = async () => {
  loading.value = true;
  try {
    const params = {
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      name: searchQuery.value || undefined,
    };
    const response = await getTeamPage(params);
    if (response.code !== 200) {
      ElMessage.error(response.message || "获取球队列表失败");
      return;
    }
    teams.value = response.data.teams || [];
    total.value = response.data.total || 0;
  } catch (error) {
    ElMessage.error("获取球队列表失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// 查看详情（调用详情接口）
const handleViewDetail = async (row: any) => {
  detailVisible.value = true;
  detailLoading.value = true;
  currentTeam.value = null;
  try {
    const response = await getTeamDetail({ teamId: row.teamId });
    if (response.code === 200) {
      currentTeam.value = response.data.team || null;
    } else {
      ElMessage.error(response.message || "获取球队详情失败");
    }
  } catch (error) {
    ElMessage.error("获取球队详情失败");
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  currentPage.value = 1;
  fetchTeamList();
};

// 分页事件
const handleSizeChange = (val: number) => {
  pageSize.value = val;
  fetchTeamList();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchTeamList();
};

onMounted(() => {
  fetchTeamList();
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

.pagination {
  display: flex;
  justify-content: flex-end;
}

.detail-body {
  min-height: 120px;
}

.logo-box {
  margin-top: 16px;
  text-align: center;
}
</style>
