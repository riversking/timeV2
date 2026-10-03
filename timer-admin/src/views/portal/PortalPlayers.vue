<template>
  <div class="portal-players">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">球员库</h1>
        <p class="page-sub">共 {{ total }} 名现役球员，查看详情需登录</p>
      </div>
      <div class="search-box">
        <el-input
          v-model="searchQuery"
          placeholder="搜索球员姓名"
          clearable
          size="large"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="default" size="large" @click="handleSearch">
          <el-icon><Search /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 球员卡片网格 -->
    <div v-loading="loading" class="player-grid">
      <div
        v-for="p in players"
        :key="p.playerId"
        class="player-card"
        @click="handleCardClick(p)"
      >
        <div class="photo-box">
          <img
            v-if="p.photoUrl"
            :src="p.photoUrl"
            class="player-photo"
            loading="lazy"
            @error="onImgError"
          />
          <div v-else class="photo-fallback">{{ (p.lastName || "?").charAt(0) }}</div>
        </div>
        <div class="player-name">{{ p.firstName }} {{ p.lastName }}</div>
        <div class="player-meta">
          <span class="team-tag">{{ p.team }}</span>
          <span>#{{ p.jersey }} · {{ p.position }}</span>
        </div>
        <div class="card-hint">
          <el-icon><Lock /></el-icon>
          查看详情
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !players.length" description="没有找到匹配的球员" />

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 球员详情弹窗（登录后可见） -->
    <el-dialog v-model="detailVisible" width="760px" align-center class="player-detail-dialog">
      <template #header>
        <div class="detail-title">
          <span class="name">{{ currentPlayer ? `${currentPlayer.firstName} ${currentPlayer.lastName}` : "球员详情" }}</span>
          <span class="badge" v-if="currentPlayer?.team">{{ currentPlayer.team }} #{{ currentPlayer.jersey }}</span>
        </div>
      </template>
      <div v-loading="detailLoading" class="detail-body">
        <div class="detail-layout" v-if="currentPlayer">
          <div class="detail-photo" v-if="currentPlayer.photoUrl">
            <img :src="currentPlayer.photoUrl" alt="player" @error="onImgError" />
          </div>
          <el-descriptions :column="2" border class="detail-desc">
            <el-descriptions-item label="位置">{{ currentPlayer.position || "-" }}</el-descriptions-item>
            <el-descriptions-item label="位置大类">{{ currentPlayer.positionCategory || "-" }}</el-descriptions-item>
            <el-descriptions-item label="身高(cm)">{{ currentPlayer.height || "-" }}</el-descriptions-item>
            <el-descriptions-item label="体重(磅)">{{ currentPlayer.weight || "-" }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ currentPlayer.birthDate || "-" }}</el-descriptions-item>
            <el-descriptions-item label="出生地">
              {{ [currentPlayer.birthCity, currentPlayer.birthState, currentPlayer.birthCountry].filter(Boolean).join(", ") || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="大学">{{ currentPlayer.college || "-" }}</el-descriptions-item>
            <el-descriptions-item label="球龄">{{ currentPlayer.experience ?? "-" }} 季</el-descriptions-item>
            <el-descriptions-item label="薪资">
              {{ currentPlayer.salary ? `$${Number(currentPlayer.salary).toLocaleString()}` : "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">{{ currentPlayer.status || "-" }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { Search, Lock } from "@element-plus/icons-vue";
import { getPlayerPage, getPlayerDetail } from "@/api/portal";
import { useClientAuth } from "@/composables/useClientAuth";

const route = useRoute();
const { isClientLoggedIn, openLoginDialog } = useClientAuth();

const searchQuery = ref("");
const currentPage = ref(1);
const pageSize = ref(12);
const total = ref(0);
const players = ref<any[]>([]);
const loading = ref(false);

const detailVisible = ref(false);
const detailLoading = ref(false);
const currentPlayer = ref<any>(null);

// 门控暂存：未登录点详情 → 登录成功后自动打开
const pendingPlayerId = ref<number | null>(null);

const fetchPlayerList = async () => {
  loading.value = true;
  try {
    const res = await getPlayerPage({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      name: searchQuery.value || undefined,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取球员列表失败");
      return;
    }
    players.value = res.data?.players || [];
    total.value = res.data?.total || 0;
  } catch (error) {
    ElMessage.error("获取球员列表失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

/** 打开球员详情（调详情接口，需登录） */
const openDetail = async (playerId: number) => {
  detailVisible.value = true;
  detailLoading.value = true;
  currentPlayer.value = null;
  try {
    const res = await getPlayerDetail({ playerId });
    if (res.code === 200) {
      currentPlayer.value = res.data?.player || null;
    } else {
      ElMessage.error(res.message || "获取球员详情失败");
    }
  } catch (error) {
    ElMessage.error("获取球员详情失败");
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
};

/** 卡片点击：详情门控——未登录先提示登录 */
const handleCardClick = (p: any) => {
  if (!isClientLoggedIn.value) {
    ElMessage.warning("查看球员详情需要登录");
    pendingPlayerId.value = p.playerId;
    openLoginDialog();
    return;
  }
  openDetail(p.playerId);
};

// 登录成功后自动打开暂存的详情
watch(isClientLoggedIn, (loggedIn) => {
  if (loggedIn && pendingPlayerId.value) {
    const id = pendingPlayerId.value;
    pendingPlayerId.value = null;
    openDetail(id);
  }
});

const handleSearch = () => {
  currentPage.value = 1;
  fetchPlayerList();
};

const handleSizeChange = (val: number) => {
  pageSize.value = val;
  fetchPlayerList();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchPlayerList();
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  fetchPlayerList();
  // 首页点击球员卡片跳转过来：focus=playerId 直接打开详情
  const focus = Number(route.query.focus);
  if (focus) {
    if (isClientLoggedIn.value) {
      openDetail(focus);
    } else {
      pendingPlayerId.value = focus;
      openLoginDialog();
    }
  }
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 20px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 800;
  color: #f1f5f9;
}

.page-sub {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}

.search-box {
  display: flex;
  gap: 10px;
}

.search-box .el-input {
  width: 260px;
}

/* 球员网格 */
.player-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  min-height: 200px;
}

.player-card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 20px 14px 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.22s ease;
  position: relative;
}

.player-card:hover {
  transform: translateY(-4px);
  border-color: rgba(249, 115, 22, 0.5);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.35);
}

.photo-box {
  width: 96px;
  height: 96px;
  margin: 0 auto 12px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #334155, #1e293b);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.1);
}

.player-photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-fallback {
  font-size: 32px;
  font-weight: 800;
  color: #64748b;
}

.player-name {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-meta {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 10px;
}

.team-tag {
  color: #fb923c;
  font-weight: 700;
}

.card-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: #64748b;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
  padding-top: 8px;
}

.player-card:hover .card-hint {
  color: #fb923c;
}

/* 分页（深色适配） */
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 26px;
}

.pagination :deep(.el-pagination) {
  --el-pagination-text-color: #cbd5e1;
  --el-pagination-button-color: #cbd5e1;
  --el-pagination-hover-color: #fb923c;
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: rgba(255, 255, 255, 0.06);
  --el-pagination-button-disabled-bg-color: transparent;
  --el-pagination-button-disabled-color: #475569;
}

.pagination :deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12) inset;
}

.pagination :deep(.el-input__inner) {
  color: #cbd5e1;
}

/* 详情弹窗 */
.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title .name {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.detail-title .badge {
  font-size: 12px;
  font-weight: 600;
  color: #ea580c;
  background: rgba(249, 115, 22, 0.12);
  border-radius: 20px;
  padding: 3px 10px;
}

.detail-body {
  min-height: 180px;
}

.detail-layout {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.detail-photo {
  flex-shrink: 0;
  width: 150px;
  border-radius: 12px;
  overflow: hidden;
  background: #f1f5f9;
}

.detail-photo img {
  width: 100%;
  display: block;
}

.detail-desc {
  flex: 1;
}

/* 响应式 */
@media (max-width: 900px) {
  .player-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .detail-layout {
    flex-direction: column;
  }
}
</style>
