<template>
  <div class="portal-teams">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">球队</h1>
        <p class="page-sub">NBA 联盟 30 支球队，查看详情需登录</p>
      </div>
      <div class="conf-filter">
        <button
          v-for="opt in confOptions"
          :key="opt.value"
          class="filter-btn"
          :class="{ active: confFilter === opt.value }"
          @click="confFilter = opt.value"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <!-- 球队卡片网格 -->
    <div v-loading="loading" class="team-grid">
      <div
        v-for="t in filteredTeams"
        :key="t.teamId"
        class="team-card"
        @click="handleCardClick(t)"
      >
        <div class="logo-box">
          <img
            v-if="logoUrl(t)"
            :src="logoUrl(t)"
            class="team-logo"
            loading="lazy"
            @error="onImgError"
          />
          <div v-else class="logo-fallback">{{ t.key }}</div>
        </div>
        <div class="team-name">{{ t.name }}</div>
        <div class="team-city">{{ t.city }}</div>
        <div class="team-tags">
          <span class="tag conf">{{ confLabel(t.conference) }}</span>
          <span class="tag division">{{ t.division }}</span>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !filteredTeams.length" description="暂无球队数据" />

    <!-- 球队详情弹窗（登录后可见） -->
    <el-dialog v-model="detailVisible" width="720px" align-center class="team-detail-dialog">
      <template #header>
        <div class="detail-title">
          <span class="name">{{ currentTeam ? currentTeam.name : "球队详情" }}</span>
          <span class="badge" v-if="currentTeam?.key">{{ currentTeam.key }}</span>
        </div>
      </template>
      <div v-loading="detailLoading" class="detail-body">
        <div class="detail-layout" v-if="currentTeam">
          <div class="detail-logo" v-if="logoUrl(currentTeam)">
            <img :src="logoUrl(currentTeam)" alt="team" @error="onImgError" />
          </div>
          <el-descriptions :column="2" border class="detail-desc">
            <el-descriptions-item label="城市">{{ currentTeam.city || "-" }}</el-descriptions-item>
            <el-descriptions-item label="缩写">{{ currentTeam.key || "-" }}</el-descriptions-item>
            <el-descriptions-item label="分区">{{ confLabel(currentTeam.conference) }}</el-descriptions-item>
            <el-descriptions-item label="赛区">{{ currentTeam.division || "-" }}</el-descriptions-item>
            <el-descriptions-item label="主教练">{{ currentTeam.headCoach || "-" }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ currentTeam.active ? "活跃" : "停用" }}</el-descriptions-item>
            <el-descriptions-item label="主色">
              <span class="color-chip">
                <i class="dot" :style="{ background: `#${currentTeam.primaryColor}` }"></i>
                #{{ currentTeam.primaryColor || "-" }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="副色">
              <span class="color-chip">
                <i class="dot" :style="{ background: `#${currentTeam.secondaryColor}` }"></i>
                #{{ currentTeam.secondaryColor || "-" }}
              </span>
            </el-descriptions-item>
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
import { ref, computed, watch, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getTeamPage, getTeamDetail } from "@/api/portal";
import { useClientAuth } from "@/composables/useClientAuth";

const { isClientLoggedIn, openLoginDialog } = useClientAuth();

const teams = ref<any[]>([]);
const loading = ref(false);
const confFilter = ref("");

const detailVisible = ref(false);
const detailLoading = ref(false);
const currentTeam = ref<any>(null);

const pendingTeamId = ref<number | null>(null);

const confOptions = [
  { label: "全部", value: "" },
  { label: "东部", value: "Eastern" },
  { label: "西部", value: "Western" },
];

const confLabel = (conf: string) => {
  if (conf === "Eastern") return "东部";
  if (conf === "Western") return "西部";
  return conf || "-";
};

const filteredTeams = computed(() =>
  confFilter.value
    ? teams.value.filter((t) => t.conference === confFilter.value)
    : teams.value,
);

/** NBA 官方 CDN 队标 */
const logoUrl = (t: any) =>
  t?.nbaDotComTeamId
    ? `https://cdn.nba.com/logos/nba/${t.nbaDotComTeamId}/global/L/logo.svg`
    : "";

const fetchTeams = async () => {
  loading.value = true;
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 50 });
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取球队列表失败");
      return;
    }
    teams.value = res.data?.teams || [];
  } catch (error) {
    ElMessage.error("获取球队列表失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

/** 打开球队详情（调详情接口，需登录） */
const openDetail = async (teamId: number) => {
  detailVisible.value = true;
  detailLoading.value = true;
  currentTeam.value = null;
  try {
    const res = await getTeamDetail({ teamId });
    if (res.code === 200) {
      currentTeam.value = res.data?.team || null;
    } else {
      ElMessage.error(res.message || "获取球队详情失败");
    }
  } catch (error) {
    ElMessage.error("获取球队详情失败");
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
};

/** 卡片点击：详情门控——未登录先提示登录 */
const handleCardClick = (t: any) => {
  if (!isClientLoggedIn.value) {
    ElMessage.warning("查看球队详情需要登录");
    pendingTeamId.value = t.teamId;
    openLoginDialog();
    return;
  }
  openDetail(t.teamId);
};

// 登录成功后自动打开暂存的详情
watch(isClientLoggedIn, (loggedIn) => {
  if (loggedIn && pendingTeamId.value) {
    const id = pendingTeamId.value;
    pendingTeamId.value = null;
    openDetail(id);
  }
});

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  fetchTeams();
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

.conf-filter {
  display: flex;
  gap: 8px;
}

.filter-btn {
  padding: 7px 18px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: transparent;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  color: #f1f5f9;
  border-color: rgba(255, 255, 255, 0.3);
}

.filter-btn.active {
  color: #fff;
  background: linear-gradient(135deg, #f97316, #ea580c);
  border-color: transparent;
  font-weight: 600;
}

/* 球队网格 */
.team-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  min-height: 220px;
}

.team-card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 20px 12px 14px;
  text-align: center;
  cursor: pointer;
  transition: all 0.22s ease;
}

.team-card:hover {
  transform: translateY(-4px);
  border-color: rgba(249, 115, 22, 0.5);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.35);
}

.logo-box {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.team-logo {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-fallback {
  font-size: 18px;
  font-weight: 800;
  color: #64748b;
}

.team-name {
  font-size: 15px;
  font-weight: 700;
  color: #f1f5f9;
  margin-bottom: 2px;
}

.team-city {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.team-tags {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.tag.conf {
  color: #fb923c;
  background: rgba(249, 115, 22, 0.12);
}

.tag.division {
  color: #93c5fd;
  background: rgba(59, 130, 246, 0.12);
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
  min-height: 160px;
}

.detail-layout {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.detail-logo {
  flex-shrink: 0;
  width: 130px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.detail-logo img {
  width: 100%;
  display: block;
}

.detail-desc {
  flex: 1;
}

.color-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.color-chip .dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid #e2e8f0;
  display: inline-block;
}

/* 响应式 */
@media (max-width: 1100px) {
  .team-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 900px) {
  .team-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .detail-layout {
    flex-direction: column;
  }
}
</style>
