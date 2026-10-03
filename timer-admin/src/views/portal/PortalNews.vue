<template>
  <div class="portal-news">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">资讯中心</h1>
        <p class="page-sub">NBA 最新动态 · 最近比赛一览</p>
      </div>
      <span class="source-tag">来源 ESPN</span>
    </div>

    <div class="news-layout">
      <!-- 左侧：最近的比赛 -->
      <aside class="recent-side">
        <h3 class="side-title">最近的比赛</h3>
        <div v-loading="loadingRecent" class="recent-list">
          <div v-for="g in recentGames" :key="g.gameId" class="recent-item">
            <div class="recent-top">
              <span class="recent-date">{{ formatDay(g.day) }}</span>
              <span v-if="g.channel" class="recent-channel">{{ g.channel }}</span>
            </div>
            <div class="recent-match">
              <span class="team away">{{ g.awayTeam }}</span>
              <span class="at">@</span>
              <span class="team home">{{ g.homeTeam }}</span>
            </div>
            <div class="recent-bottom">
              <template v-if="g.isClosed">
                <span class="score" :class="{ win: g.awayTeamScore > g.homeTeamScore }">
                  {{ g.awayTeamScore }}
                </span>
                <span class="score-sep">-</span>
                <span class="score" :class="{ win: g.homeTeamScore > g.awayTeamScore }">
                  {{ g.homeTeamScore }}
                </span>
              </template>
              <span v-else class="recent-time">{{ formatGameTime(g.dateTime) }}</span>
            </div>
          </div>
          <el-empty
            v-if="!loadingRecent && !recentGames.length"
            description="暂无比赛"
            :image-size="60"
          />
        </div>
      </aside>

      <!-- 右侧：新闻流 -->
      <main class="news-main">
        <div v-loading="loadingNews" class="news-list">
          <a
            v-for="n in news"
            :key="n.link"
            :href="n.link"
            target="_blank"
            rel="noopener"
            class="news-item"
          >
            <div class="news-thumb">
              <img
                v-if="n.imageUrl"
                :src="n.imageUrl"
                class="thumb-img"
                loading="lazy"
                @error="onImgError"
              />
              <div v-else class="thumb-placeholder">🏀</div>
            </div>
            <div class="news-content">
              <h3 class="news-title">{{ n.title }}</h3>
              <p class="news-desc">{{ n.description }}</p>
              <span class="news-time">{{ formatTime(n.published) }}</span>
            </div>
          </a>
        </div>
        <el-empty
          v-if="!loadingNews && !news.length"
          description="暂无新闻，稍后再来看看"
        />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { getNews, getRecentGames } from "@/api/portal";

const news = ref<any[]>([]);
const recentGames = ref<any[]>([]);
const loadingNews = ref(false);
const loadingRecent = ref(false);

const loadNews = async () => {
  loadingNews.value = true;
  try {
    const res = await getNews({ limit: 30 });
    if (res.code === 200) {
      news.value = res.data?.news || [];
    }
  } catch (error) {
    console.error("加载新闻失败:", error);
  } finally {
    loadingNews.value = false;
  }
};

const loadRecentGames = async () => {
  loadingRecent.value = true;
  try {
    const res = await getRecentGames();
    if (res.code === 200) {
      recentGames.value = res.data?.games || [];
    }
  } catch (error) {
    console.error("加载最近比赛失败:", error);
  } finally {
    loadingRecent.value = false;
  }
};

/** 比赛日 "2027-04-11" → "04-11" */
const formatDay = (day: string) => {
  if (!day || day.length < 10) return day || "-";
  return day.slice(5, 10);
};

/** 比赛时间 "2027-04-11 19:00:00" → "19:00" */
const formatGameTime = (dt: string) => {
  if (!dt) return "-";
  const m = dt.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
  if (!m) return dt;
  return `${m[4]}:${m[5]}`;
};

/** ESPN 发布时间 ISO 串 → "MM-DD HH:mm" */
const formatTime = (iso: string) => {
  if (!iso) return "";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  loadNews();
  loadRecentGames();
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

.source-tag {
  font-size: 12px;
  color: #94a3b8;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  padding: 3px 10px;
}

.news-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

/* 左侧最近比赛 */
.recent-side {
  width: 300px;
  flex-shrink: 0;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 18px 16px;
  position: sticky;
  top: 88px;
}

.side-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: #f1f5f9;
  padding-left: 12px;
  position: relative;
}

.side-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #fb923c, #ea580c);
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}

.recent-item {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 10px 12px;
  transition: border-color 0.2s;
}

.recent-item:hover {
  border-color: rgba(249, 115, 22, 0.4);
}

.recent-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.recent-date {
  font-size: 12px;
  font-weight: 700;
  color: #fb923c;
  font-variant-numeric: tabular-nums;
}

.recent-channel {
  font-size: 11px;
  color: #64748b;
}

.recent-match {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.team {
  font-size: 13px;
  font-weight: 700;
  color: #e2e8f0;
}

.team.away {
  text-align: left;
}

.team.home {
  text-align: right;
}

.at {
  font-size: 11px;
  color: #64748b;
}

.recent-bottom {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.recent-time {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.score {
  font-size: 14px;
  font-weight: 800;
  color: #cbd5e1;
  font-variant-numeric: tabular-nums;
}

.score.win {
  color: #fb923c;
}

.score-sep {
  color: #475569;
  font-size: 12px;
}

/* 右侧新闻流 */
.news-main {
  flex: 1;
  min-width: 0;
}

.news-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}

.news-item {
  display: flex;
  gap: 16px;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  transition: all 0.22s ease;
}

.news-item:hover {
  transform: translateY(-3px);
  border-color: rgba(249, 115, 22, 0.5);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.35);
}

.news-thumb {
  width: 200px;
  flex-shrink: 0;
  background: #0f172a;
  overflow: hidden;
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.news-item:hover .thumb-img {
  transform: scale(1.05);
}

.thumb-placeholder {
  width: 100%;
  height: 100%;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  opacity: 0.5;
}

.news-content {
  flex: 1;
  min-width: 0;
  padding: 14px 16px 14px 0;
  display: flex;
  flex-direction: column;
}

.news-title {
  margin: 0 0 8px;
  font-size: 15.5px;
  font-weight: 600;
  color: #f1f5f9;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-desc {
  margin: 0 0 10px;
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
  .news-layout {
    flex-direction: column;
  }
  .recent-side {
    width: 100%;
    position: static;
  }
  .news-thumb {
    width: 130px;
  }
}
</style>
