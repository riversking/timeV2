<template>
  <div class="game-detail">
    <!-- 返回导航 -->
    <div class="detail-nav">
      <button class="back-btn" @click="goBack">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <span class="nav-title">比赛详情</span>
    </div>

    <div v-loading="loading" class="detail-body">
      <template v-if="game">
        <!-- 比分板：对阵 / 总分 / 状态 -->
        <section class="scoreboard">
          <div class="sb-meta">
            <span class="sb-day">{{ game.day }}</span>
            <span v-if="game.dateTime" class="sb-time">{{ formatClock(game.dateTime) }}</span>
            <el-tag :type="statusInfo.type" size="small" effect="dark">{{ statusInfo.text }}</el-tag>
            <span v-if="game.channel" class="sb-channel">{{ game.channel }}</span>
          </div>
          <div class="sb-main">
            <div class="sb-team">
              <img
                v-if="teamLogo(game.awayTeam)"
                :src="teamLogo(game.awayTeam)"
                class="sb-logo"
                @error="onImgError"
              />
              <div class="sb-tname">{{ game.awayTeam }}</div>
              <div v-if="showScore" class="sb-score" :class="{ win: awayWin }">{{ awayTotal }}</div>
            </div>
            <div class="sb-center">
              <span class="sb-sep">{{ showScore ? "-" : "VS" }}</span>
            </div>
            <div class="sb-team">
              <img
                v-if="teamLogo(game.homeTeam)"
                :src="teamLogo(game.homeTeam)"
                class="sb-logo"
                @error="onImgError"
              />
              <div class="sb-tname">{{ game.homeTeam }}</div>
              <div v-if="showScore" class="sb-score" :class="{ win: homeWin }">{{ homeTotal }}</div>
            </div>
          </div>
        </section>

        <!-- 每节比分 -->
        <section v-if="periods.length" class="card">
          <h3 class="card-title">每节比分</h3>
          <div class="tbl-scroll">
            <table class="stat-table period-table">
              <thead>
                <tr>
                  <th class="col-team">球队</th>
                  <th v-for="p in periods" :key="p.period">{{ periodLabel(p.period) }}</th>
                  <th class="col-total">总分</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="col-team">{{ game.awayTeam }}</td>
                  <td v-for="p in periods" :key="p.period">{{ p.awayScore ?? 0 }}</td>
                  <td class="col-total">{{ awayTotal }}</td>
                </tr>
                <tr>
                  <td class="col-team">{{ game.homeTeam }}</td>
                  <td v-for="p in periods" :key="p.period">{{ p.homeScore ?? 0 }}</td>
                  <td class="col-total">{{ homeTotal }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 球员数据：客队、主队两张表 -->
        <section v-for="box in boxes" :key="box.team" class="card">
          <h3 class="card-title">
            {{ box.team }} 球员数据
            <span class="card-sub">{{ box.players.length }} 人</span>
          </h3>
          <div class="tbl-scroll">
            <table class="stat-table player-table">
              <thead>
                <tr>
                  <th class="col-player">球员</th>
                  <th v-for="(c, i) in box.columns" :key="i">{{ c }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in box.players" :key="p.name" :class="{ dnp: !p.played }">
                  <td class="col-player">
                    <span v-if="p.starter" class="starter-dot" title="首发"></span>
                    <span class="player-name">{{ p.name }}</span>
                  </td>
                  <td v-for="(c, i) in box.columns" :key="i">{{ statCell(p, i) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <el-empty
          v-if="!loading && !periods.length && !hasBox"
          description="暂无详细数据：比赛尚未开始或数据源暂未提供"
          :image-size="80"
        />
      </template>

      <el-empty v-else-if="!loading" :description="errorMsg || '比赛不存在'" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft } from "@element-plus/icons-vue";
import { getGameDetail, getTeamPage, openScoreStream } from "@/api/portal";

const route = useRoute();
const router = useRouter();
const gameId = Number(route.params.gameId);

const loading = ref(false);
const errorMsg = ref("");
const game = ref<any>(null);
const periods = ref<any[]>([]);
const awayBox = ref<any>({});
const homeBox = ref<any>({});

const boxes = computed(() => {
  const list: any[] = [];
  if (awayBox.value.players?.length) {
    list.push(awayBox.value);
  }
  if (homeBox.value.players?.length) {
    list.push(homeBox.value);
  }
  return list;
});
const hasBox = computed(() => boxes.value.length > 0);

const showScore = computed(() => {
  if (!game.value) return false;
  if (periods.value.length || game.value.isClosed) return true;
  return (Number(game.value.awayTeamScore) || 0) + (Number(game.value.homeTeamScore) || 0) > 0;
});

const periodSum = (key: "awayScore" | "homeScore") =>
  periods.value.reduce((acc, p) => acc + (Number(p[key]) || 0), 0);

/** 总分优先取库内实时比分（监控每分钟修正），无实时比分时用每节合计兜底 */
const awayTotal = computed(() => {
  const score = Number(game.value?.awayTeamScore) || 0;
  return score > 0 ? score : periodSum("awayScore");
});
const homeTotal = computed(() => {
  const score = Number(game.value?.homeTeamScore) || 0;
  return score > 0 ? score : periodSum("homeScore");
});

const awayWin = computed(() => showScore.value && awayTotal.value > homeTotal.value);
const homeWin = computed(() => showScore.value && homeTotal.value > awayTotal.value);

const statusInfo = computed<{ text: string; type: "primary" | "warning" | "info" | "success" }>(() => {
  const g = game.value || {};
  if (g.isClosed || g.status === "Final") return { text: "已结束", type: "info" };
  if (g.status === "InProgress") return { text: "进行中", type: "warning" };
  if (!g.status || g.status === "Scheduled") return { text: "未开赛", type: "primary" };
  return { text: g.status, type: "primary" };
});

const periodLabel = (p: number) => (p <= 4 ? `Q${p}` : p === 5 ? "OT" : `OT${p - 4}`);

const statCell = (p: any, index: number | string) => {
  if (!p.played) return "-";
  return p.stats?.[Number(index)] ?? "-";
};

const formatClock = (dt: string) => {
  if (!dt) return "-";
  const m = dt.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
  return m ? `${m[4]}:${m[5]}` : dt;
};

const load = async () => {
  loading.value = true;
  errorMsg.value = "";
  try {
    const res = await getGameDetail({ gameId });
    if (res.code === 200) {
      const detail = res.data?.detail || {};
      game.value = detail.game || null;
      periods.value = detail.periods || [];
      awayBox.value = detail.awayBox || {};
      homeBox.value = detail.homeBox || {};
    } else {
      errorMsg.value = res.message || "加载失败";
    }
  } catch (error) {
    console.error("加载比赛详情失败:", error);
    errorMsg.value = "加载失败，请稍后重试";
  } finally {
    loading.value = false;
  }
};

/* 球队 logo（库内 key → NBA 官方 CDN） */
const teamLogoMap = ref<Record<string, string>>({});
const teamLogo = (key: string) => teamLogoMap.value[key] || "";

const fetchTeams = async () => {
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 50 });
    if (res.code === 200) {
      const map: Record<string, string> = {};
      for (const t of res.data?.teams || []) {
        if (t.key && t.nbaDotComTeamId) {
          map[t.key] = `https://cdn.nba.com/logos/nba/${t.nbaDotComTeamId}/global/L/logo.svg`;
        }
      }
      teamLogoMap.value = map;
    }
  } catch (error) {
    console.error("加载球队信息失败:", error);
  }
};

/* 比分实时推送：更新比分板；完场后重拉详情补齐每节比分与球员数据 */
let scoreSource: EventSource | null = null;
let scoreRetryTimer: number | null = null;

const handleScoreUpdate = (e: MessageEvent) => {
  let data: any;
  try {
    data = JSON.parse(e.data);
  } catch (error) {
    console.error("解析比分推送失败:", error);
    return;
  }
  if (!game.value || data.gameId !== gameId) {
    return;
  }
  const wasClosed = !!game.value.isClosed;
  game.value.awayTeamScore = data.awayTeamScore;
  game.value.homeTeamScore = data.homeTeamScore;
  game.value.isClosed = data.isClosed;
  if (!wasClosed && data.isClosed) {
    load();
  }
};

const connectScoreStream = () => {
  scoreSource = openScoreStream();
  scoreSource.addEventListener("scoreUpdate", handleScoreUpdate as EventListener);
  scoreSource.onerror = () => {
    scoreSource?.close();
    scoreSource = null;
    if (scoreRetryTimer) {
      window.clearTimeout(scoreRetryTimer);
    }
    scoreRetryTimer = window.setTimeout(connectScoreStream, 10_000);
  };
};

const goBack = () => {
  if (window.history.state?.back) {
    router.back();
  } else {
    router.push("/portal/schedule");
  }
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  load();
  fetchTeams();
  connectScoreStream();
});

onBeforeUnmount(() => {
  if (scoreRetryTimer) {
    window.clearTimeout(scoreRetryTimer);
    scoreRetryTimer = null;
  }
  scoreSource?.close();
  scoreSource = null;
});
</script>

<style scoped>
.game-detail {
  max-width: 980px;
  margin: 0 auto;
}

/* 导航 */
.detail-nav {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(30, 41, 59, 0.75);
  color: #cbd5e1;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  color: #fb923c;
  border-color: rgba(249, 115, 22, 0.45);
}

.nav-title {
  font-size: 17px;
  font-weight: 700;
  color: #f1f5f9;
}

.detail-body {
  min-height: 320px;
}

/* 比分板 */
.scoreboard {
  border-radius: 16px;
  padding: 22px 28px 26px;
  background:
    radial-gradient(circle at 85% 10%, rgba(249, 115, 22, 0.18), transparent 45%),
    radial-gradient(circle at 12% 95%, rgba(37, 99, 235, 0.16), transparent 45%),
    linear-gradient(135deg, #1e293b, #0f172a);
  border: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 18px;
}

.sb-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.sb-day {
  font-size: 13.5px;
  font-weight: 700;
  color: #fb923c;
  font-variant-numeric: tabular-nums;
}

.sb-time,
.sb-channel {
  font-size: 12.5px;
  color: #94a3b8;
}

.sb-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28px;
}

.sb-team {
  flex: 1;
  max-width: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.sb-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
}

.sb-tname {
  font-size: 16px;
  font-weight: 800;
  color: #e2e8f0;
  letter-spacing: 1px;
}

.sb-score {
  font-size: 34px;
  font-weight: 900;
  color: #cbd5e1;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.sb-score.win {
  color: #fb923c;
}

.sb-center {
  display: flex;
  align-items: center;
  padding-top: 26px;
}

.sb-sep {
  font-size: 16px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 2px;
}

/* 数据卡片 */
.card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 16px 18px;
  margin-bottom: 18px;
}

.card-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: #f1f5f9;
  padding-left: 12px;
  position: relative;
}

.card-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, #fb923c, #ea580c);
}

.card-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #64748b;
}

/* 数据表 */
.tbl-scroll {
  overflow-x: auto;
}

.stat-table {
  width: 100%;
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

.stat-table th {
  font-size: 11.5px;
  font-weight: 600;
  color: #94a3b8;
  text-align: center;
  padding: 6px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
}

.stat-table td {
  font-size: 13px;
  color: #cbd5e1;
  text-align: center;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  white-space: nowrap;
}

.stat-table tbody tr:last-child td {
  border-bottom: none;
}

.stat-table tbody tr:hover td {
  background: rgba(249, 115, 22, 0.06);
}

.col-team,
.col-player {
  text-align: left !important;
  font-weight: 700;
  color: #e2e8f0 !important;
}

.col-total {
  font-weight: 800;
  color: #fb923c !important;
}

/* 每节比分表 */
.period-table {
  min-width: 380px;
}

/* 球员数据表 */
.player-table {
  min-width: 640px;
}

.player-table tr.dnp td {
  opacity: 0.4;
}

.col-player .player-name {
  margin-left: 2px;
}

.starter-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fb923c;
  margin-right: 6px;
  vertical-align: middle;
}

/* 响应式 */
@media (max-width: 900px) {
  .scoreboard {
    padding: 18px 16px 22px;
  }
  .sb-main {
    gap: 12px;
  }
  .sb-logo {
    width: 48px;
    height: 48px;
  }
  .sb-score {
    font-size: 26px;
  }
  .card {
    padding: 14px 12px;
  }
}
</style>
