<template>
  <div class="nba-list-container">
    <!-- 深蓝科技感头部 -->
    <div class="header">
      <h1 class="title">球员管理</h1>
    </div>

    <!-- 搜索过滤栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索球员姓名"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-button type="default" @click="handleSearch">
        <el-icon><Search /></el-icon> 搜索
      </el-button>
    </div>

    <!-- 球员表格 -->
    <div class="table-container">
      <el-table
        :data="players"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
      >
        <el-table-column label="头像" width="70">
          <template #default="{ row }">
            <el-avatar :size="40" :src="row.photoUrl" shape="circle" />
          </template>
        </el-table-column>
        <el-table-column prop="playerId" label="球员ID"/>
        <el-table-column label="姓名" width="180">
          <template #default="{ row }">
            <el-tooltip :content="`${row.firstName} ${row.lastName}`" placement="top">
              <span class="ellipsis-text">{{ row.firstName }} {{ row.lastName }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="team" label="球队" />
        <el-table-column prop="position" label="位置" />
        <el-table-column prop="jersey" label="球衣号"/>
        <el-table-column label="身高(cm)">
          <template #default="{ row }">{{ row.height }}</template>
        </el-table-column>
        <el-table-column prop="weight" label="体重(磅)" />
        <el-table-column prop="experience" label="球龄" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作"  fixed="right">
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

    <!-- 球员详情弹窗 -->
    <el-dialog v-model="detailVisible" title="球员详情" width="720px">
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border v-if="currentPlayer">
          <el-descriptions-item label="球员ID">{{ currentPlayer.playerId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ currentPlayer.firstName }} {{ currentPlayer.lastName }}</el-descriptions-item>
          <el-descriptions-item label="球队">{{ currentPlayer.team || "-" }}</el-descriptions-item>
          <el-descriptions-item label="球队ID">{{ currentPlayer.teamId }}</el-descriptions-item>
          <el-descriptions-item label="球衣号">{{ currentPlayer.jersey }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ currentPlayer.position || "-" }}</el-descriptions-item>
          <el-descriptions-item label="位置大类">{{ currentPlayer.positionCategory || "-" }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentPlayer.status || "-" }}</el-descriptions-item>
          <el-descriptions-item label="身高(cm)">{{ currentPlayer.height }}</el-descriptions-item>
          <el-descriptions-item label="体重(磅)">{{ currentPlayer.weight }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ currentPlayer.birthDate || "-" }}</el-descriptions-item>
          <el-descriptions-item label="出生地">{{ [currentPlayer.birthCity, currentPlayer.birthState, currentPlayer.birthCountry].filter(Boolean).join(", ") || "-" }}</el-descriptions-item>
          <el-descriptions-item label="大学">{{ currentPlayer.college || "-" }}</el-descriptions-item>
          <el-descriptions-item label="球龄">{{ currentPlayer.experience }}</el-descriptions-item>
          <el-descriptions-item label="薪资">{{ currentPlayer.salary }}</el-descriptions-item>
          <el-descriptions-item label="DraftKings名">{{ currentPlayer.draftKingsName || "-" }}</el-descriptions-item>
        </el-descriptions>
        <div class="photo-box" v-if="currentPlayer?.photoUrl">
          <el-image
            :src="currentPlayer.photoUrl"
            fit="contain"
            style="width: 160px; height: 180px"
            :preview-src-list="[currentPlayer.photoUrl]"
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
import { getPlayerDetail, getPlayerPage } from "@/api/nba";

// 搜索条件
const searchQuery = ref("");

// 分页参数
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

// 数据列表
const players = ref<any[]>([]);
const loading = ref(false);

// 详情弹窗
const detailVisible = ref(false);
const detailLoading = ref(false);
const currentPlayer = ref<any>(null);

// 获取球员列表
const fetchPlayerList = async () => {
  loading.value = true;
  try {
    const params = {
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      playerName: searchQuery.value || undefined,
    };
    const response = await getPlayerPage(params);
    if (response.code !== 200) {
      ElMessage.error(response.message || "获取球员列表失败");
      return;
    }
    players.value = response.data.players || [];
    total.value = response.data.total || 0;
  } catch (error) {
    ElMessage.error("获取球员列表失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// 查看详情（调用详情接口）
const handleViewDetail = async (row: any) => {
  detailVisible.value = true;
  detailLoading.value = true;
  currentPlayer.value = null;
  try {
    const response = await getPlayerDetail({ playerId: row.playerId });
    if (response.code === 200) {
      currentPlayer.value = response.data.player || null;
    } else {
      ElMessage.error(response.message || "获取球员详情失败");
    }
  } catch (error) {
    ElMessage.error("获取球员详情失败");
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  currentPage.value = 1;
  fetchPlayerList();
};

// 分页事件
const handleSizeChange = (val: number) => {
  pageSize.value = val;
  fetchPlayerList();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchPlayerList();
};

onMounted(() => {
  fetchPlayerList();
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

.photo-box {
  margin-top: 16px;
  text-align: center;
}

.ellipsis-text {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}
</style>
