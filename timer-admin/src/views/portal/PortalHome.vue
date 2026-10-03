<template>
  <div class="portal-home">
    <!-- Hero 区 -->
    <section class="hero">
      <div class="hero-inner">
        <h1 class="hero-title">NBA 数据中心</h1>
        <p class="hero-sub">球员 · 球队 · 赛程 · 热点资讯，一站掌握</p>
        <div class="hero-stats">
          <div class="stat">
            <span class="num">{{ playerTotal }}</span>
            <span class="label">球员</span>
          </div>
          <div class="divider"></div>
          <div class="stat">
            <span class="num">{{ teamTotal }}</span>
            <span class="label">球队</span>
          </div>
          <div class="divider"></div>
          <div class="stat">
            <span class="num">{{ gameTotal }}</span>
            <span class="label">场赛程</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 新闻资讯 -->
    <section class="section">
      <div class="section-header">
        <h2 class="section-title">新闻资讯</h2>
        <div class="news-header-right">
          <span class="source-tag">来源 ESPN</span>
          <router-link to="/portal/news" class="more-link">更多资讯 →</router-link>
        </div>
      </div>
      <div v-loading="loadingNews" class="news-grid">
        <a
          v-for="n in news"
          :key="n.link"
          :href="n.link"
          target="_blank"
          rel="noopener"
          class="news-card"
        >
          <div class="news-img-box">
            <img
              v-if="n.imageUrl"
              :src="n.imageUrl"
              class="news-img"
              loading="lazy"
              @error="onImgError"
            />
            <div v-else class="news-img-placeholder">🏀</div>
          </div>
          <div class="news-body">
            <h3 class="news-title">{{ n.title }}</h3>
            <p class="news-desc">{{ n.description }}</p>
            <div class="news-time">{{ formatTime(n.published) }}</div>
          </div>
        </a>
      </div>
      <el-empty
        v-if="!loadingNews && !news.length"
        description="暂无新闻，稍后再来看看"
      />
    </section>

    <!-- 球员速览 -->
    <section class="section">
      <div class="section-header">
        <h2 class="section-title">球员速览</h2>
        <router-link to="/portal/players" class="more-link">查看全部 →</router-link>
      </div>
      <div v-loading="loadingPlayers" class="player-grid">
        <div
          v-for="p in players"
          :key="p.playerId"
          class="player-card"
          @click="handlePlayerClick(p)"
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
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getPlayerPage, getTeamPage, getGamePage, getNews } from "@/api/portal";
import { useClientAuth } from "@/composables/useClientAuth";

const router = useRouter();
const { isClientLoggedIn, openLoginDialog } = useClientAuth();

// 门控暂存：未登录点球员卡 → 登录成功后自动跳转球员页打开详情
const pendingPlayerId = ref<number | null>(null);

const players = ref<any[]>([]);
const news = ref<any[]>([]);
const loadingPlayers = ref(false);
const loadingNews = ref(false);

const playerTotal = ref(0);
const teamTotal = ref(0);
const gameTotal = ref(0);

const loadPlayers = async () => {
  loadingPlayers.value = true;
  try {
    const res = await getPlayerPage({ currentPage: 1, pageSize: 8 });
    if (res.code === 200) {
      players.value = res.data?.players || [];
      playerTotal.value = res.data?.total || 0;
    }
  } catch (error) {
    console.error("加载球员失败:", error);
  } finally {
    loadingPlayers.value = false;
  }
};

const loadTeams = async () => {
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 1 });
    if (res.code === 200) {
      teamTotal.value = res.data?.total || 0;
    }
  } catch (error) {
    console.error("加载球队统计失败:", error);
  }
};

const loadGames = async () => {
  try {
    const res = await getGamePage({ currentPage: 1, pageSize: 1 });
    if (res.code === 200) {
      gameTotal.value = res.data?.total || 0;
    }
  } catch (error) {
    console.error("加载赛程统计失败:", error);
  }
};

const loadNews = async () => {
  loadingNews.value = true;
  try {
    const res = await getNews({ limit: 9 });
    if (res.code === 200) {
      news.value = res.data?.news || [];
    }
  } catch (error) {
    console.error("加载新闻失败:", error);
  } finally {
    loadingNews.value = false;
  }
};

/** 首页球员卡点击：详情门控——未登录先提示登录，已登录跳到球员页并打开详情 */
const handlePlayerClick = (p: any) => {
  if (!isClientLoggedIn.value) {
    ElMessage.warning("查看球员详情需要登录");
    pendingPlayerId.value = p.playerId;
    openLoginDialog();
    return;
  }
  goPlayerDetail(p.playerId);
};

const goPlayerDetail = (playerId: number) => {
  router.push({ path: "/portal/players", query: { focus: String(playerId) } });
};

// 登录成功后自动衔接暂存的球员详情
watch(isClientLoggedIn, (loggedIn) => {
  if (loggedIn && pendingPlayerId.value) {
    const id = pendingPlayerId.value;
    pendingPlayerId.value = null;
    goPlayerDetail(id);
  }
});

const onImgError = (e: Event) => {
  const img = e.target as HTMLImageElement;
  img.style.visibility = "hidden";
};

const formatTime = (iso: string) => {
  if (!iso) return "";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

onMounted(() => {
  loadPlayers();
  loadTeams();
  loadGames();
  loadNews();
});
</script>

<style scoped>
/* Hero */
.hero {
  border-radius: 16px;
  padding: 44px 40px;
  background:
    radial-gradient(circle at 85% 20%, rgba(249, 115, 22, 0.28), transparent 45%),
    radial-gradient(circle at 15% 90%, rgba(37, 99, 235, 0.25), transparent 45%),
    linear-gradient(135deg, #1e293b, #0f172a);
  border: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 32px;
}

.hero-title {
  margin: 0 0 10px;
  font-size: 38px;
  font-weight: 900;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #fb923c, #f97316, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-sub {
  margin: 0 0 28px;
  color: #94a3b8;
  font-size: 15px;
  letter-spacing: 1px;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 28px;
}

.stat {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.stat .num {
  font-size: 28px;
  font-weight: 800;
  color: #f8fafc;
  font-variant-numeric: tabular-nums;
}

.stat .label {
  font-size: 13px;
  color: #94a3b8;
}

.divider {
  width: 1px;
  height: 26px;
  background: rgba(255, 255, 255, 0.12);
}

/* 区块通用 */
.section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #f1f5f9;
  position: relative;
  padding-left: 14px;
}

.section-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: linear-gradient(180deg, #fb923c, #ea580c);
}

.more-link {
  color: #fb923c;
  font-size: 14px;
  text-decoration: none;
}

.more-link:hover {
  text-decoration: underline;
}

.source-tag {
  font-size: 12px;
  color: #94a3b8;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  padding: 3px 10px;
}

.news-header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 球员网格 */
.player-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  min-height: 180px;
}

.player-card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 18px 14px 14px;
  text-align: center;
  cursor: pointer;
  transition: all 0.22s ease;
}

.player-card:hover {
  transform: translateY(-4px);
  border-color: rgba(249, 115, 22, 0.5);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.35);
}

.photo-box {
  width: 90px;
  height: 90px;
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
  font-size: 30px;
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
}

.team-tag {
  color: #fb923c;
  font-weight: 700;
}

/* 新闻网格 */
.news-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  min-height: 160px;
}

.news-card {
  display: flex;
  flex-direction: column;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  overflow: hidden;
  text-decoration: none;
  transition: all 0.22s ease;
}

.news-card:hover {
  transform: translateY(-4px);
  border-color: rgba(249, 115, 22, 0.5);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.35);
}

.news-img-box {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #0f172a;
  overflow: hidden;
}

.news-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.news-card:hover .news-img {
  transform: scale(1.05);
}

.news-img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  opacity: 0.5;
}

.news-body {
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.news-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-desc {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: #94a3b8;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.news-time {
  font-size: 12px;
  color: #64748b;
}

/* 响应式 */
@media (max-width: 900px) {
  .player-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .news-grid {
    grid-template-columns: 1fr;
  }
  .hero {
    padding: 32px 24px;
  }
  .hero-title {
    font-size: 28px;
  }
}
</style>
