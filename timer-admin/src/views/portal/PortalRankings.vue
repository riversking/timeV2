<template>
  <div class="portal-rankings">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">排行</h1>
        <p class="page-sub">
          球队东西部 / 分区排名与球员数据榜
          <span v-if="seasonText"> · {{ seasonText }}</span>
        </p>
      </div>
      <div class="main-tabs">
        <button
          v-for="tab in mainTabs"
          :key="tab.value"
          class="filter-btn"
          :class="{ active: mainTab === tab.value }"
          @click="mainTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- 球队排名 -->
    <template v-if="mainTab === 'team'">
      <div class="sub-filter">
        <button
          v-for="opt in scopeOptions"
          :key="opt.value"
          class="filter-btn small"
          :class="{ active: teamScope === opt.value }"
          @click="teamScope = opt.value"
        >
          {{ opt.label }}
        </button>
        <template v-if="teamScope === 'division'">
          <span class="filter-divider"></span>
          <button
            v-for="d in divisions"
            :key="d.value"
            class="filter-btn small"
            :class="{ active: division === d.value }"
            @click="division = d.value"
          >
            {{ d.label }}
          </button>
        </template>
      </div>

      <div v-loading="standingsLoading" class="rank-table" :class="{ 'is-loading': standingsLoading }">
        <div class="rank-row rank-head">
          <div class="col-rank">排名</div>
          <div class="col-team">球队</div>
          <div class="col-num">胜</div>
          <div class="col-num">负</div>
          <div class="col-num">胜率</div>
          <div class="col-num">胜差</div>
          <div class="col-num">近10</div>
          <div class="col-num">连续</div>
        </div>
        <div
          v-for="(t, idx) in teamRows"
          :key="t.teamId"
          class="rank-row"
          :class="{ 'top-three': idx < 3 }"
        >
          <div class="col-rank">
            <span class="rank-num" :class="rankClass(idx + 1)">{{ teamRank(t, idx) }}</span>
          </div>
          <div class="col-team">
            <div class="logo-box">
              <img
                v-if="teamLogo(t.key)"
                :src="teamLogo(t.key)"
                class="team-logo"
                loading="lazy"
                @error="onImgError"
              />
              <div v-else class="logo-fallback">{{ t.key }}</div>
            </div>
            <div class="team-text">
              <span class="team-name">{{ t.name }}</span>
              <span class="team-city">{{ t.city }}</span>
            </div>
          </div>
          <div class="col-num strong">{{ t.wins ?? 0 }}</div>
          <div class="col-num">{{ t.losses ?? 0 }}</div>
          <div class="col-num">{{ formatPct(t.winPct) }}</div>
          <div class="col-num">{{ formatGamesBack(t.gamesBack) }}</div>
          <div class="col-num">{{ t.lastTenWins ?? 0 }}-{{ t.lastTenLosses ?? 0 }}</div>
          <div class="col-num">
            <span :class="streakClass(t.streakDescription)">{{ t.streakDescription || "-" }}</span>
          </div>
        </div>
      </div>

      <el-empty
        v-if="!standingsLoading && !teamRows.length"
        description="暂无排名数据"
      />
    </template>

    <!-- 球员数据排名 -->
    <template v-else>
      <div class="sub-filter">
        <button
          v-for="s in scopeTabs"
          :key="s.value"
          class="filter-btn small"
          :class="{ active: playerScope === s.value }"
          @click="switchScope(s.value)"
        >
          {{ s.label }}
        </button>
        <span class="filter-divider"></span>
        <button
          v-for="tab in statTabs"
          :key="tab.value"
          class="filter-btn small"
          :class="{ active: statTab === tab.value }"
          @click="switchStat(tab.value)"
        >
          {{ tab.label }}
        </button>
        <span v-if="playerScope === 'daily' && dailyDate" class="daily-date">
          比赛日 {{ dailyDate }}（美东）
        </span>
        <span v-else-if="playerScope === 'season' && playerSeasonText" class="daily-date">
          {{ playerSeasonText }}
        </span>
      </div>

      <div v-loading="leadersLoading" class="leader-boards" :class="{ 'is-loading': leadersLoading }">
        <!-- 球员榜 -->
        <section class="leader-board">
          <div class="board-head">
            <span class="board-title">球员{{ statLabel }}榜</span>
            <span class="board-hint">{{ scopeHint }}</span>
          </div>

          <div v-if="featuredPlayer" class="featured-card">
            <span class="featured-badge">NO.1</span>
            <div class="featured-photo">
              <img
                v-if="featuredPlayer.photoUrl"
                :src="featuredPlayer.photoUrl"
                loading="lazy"
                @error="onImgError"
              />
              <div v-else class="photo-fallback">{{ (featuredPlayer.name || "?").charAt(0) }}</div>
            </div>
            <div class="featured-main">
              <div class="featured-name">{{ featuredPlayer.name }}</div>
              <div class="featured-meta">
                <span>{{ featuredPlayer.team || "-" }}</span>
                <span>·</span>
                <span>{{ featuredPlayer.position || "-" }}</span>
                <template v-if="playerScope === 'season'">
                  <span>·</span>
                  <span>{{ featuredPlayer.games || 0 }} 场</span>
                </template>
              </div>
              <div v-if="playerScope === 'daily'" class="featured-stats">
                <span v-for="s in dailyStats(featuredPlayer)" :key="s.label" class="featured-stat">
                  <b>{{ s.value }}</b>
                  <i>{{ s.label }}</i>
                </span>
              </div>
            </div>
            <div class="featured-value">
              <span class="featured-value-num">{{ formatValue(playerValue(featuredPlayer)) }}</span>
              <span class="featured-value-label">{{ featuredValueLabel }}</span>
            </div>
          </div>

          <div v-if="restPlayers.length" class="rank-table">
            <div class="rank-row rank-head">
              <div class="col-rank">排名</div>
              <div class="col-player">球员</div>
              <div class="col-num">球队</div>
              <div class="col-num">{{ statLabel }}</div>
              <div class="col-num">{{ playerScope === "daily" ? "板/助" : "场次" }}</div>
            </div>
            <div
              v-for="(p, idx) in restPlayers"
              :key="`${p.playerId}-${p.name}`"
              class="rank-row"
            >
              <div class="col-rank">
                <span class="rank-num" :class="rankClass(idx + 2)">{{ idx + 2 }}</span>
              </div>
              <div class="col-player">
                <div class="photo-box">
                  <img
                    v-if="p.photoUrl"
                    :src="p.photoUrl"
                    class="player-photo"
                    loading="lazy"
                    @error="onImgError"
                  />
                  <div v-else class="photo-fallback">{{ (p.name || "?").charAt(0) }}</div>
                </div>
                <div class="player-text">
                  <span class="player-name">{{ p.name }}</span>
                  <span class="player-pos">{{ p.position || "-" }}</span>
                </div>
              </div>
              <div class="col-num">{{ p.team || "-" }}</div>
              <div class="col-num strong">{{ formatValue(playerValue(p)) }}</div>
              <div class="col-num">{{ playerExtra(p) }}</div>
            </div>
          </div>

          <el-empty v-if="!leadersLoading && !playerRows.length" description="暂无球员数据" />
        </section>

        <!-- 球队榜 -->
        <section class="leader-board">
          <div class="board-head">
            <span class="board-title">球队{{ statLabel }}榜</span>
            <span class="board-hint">{{ teamScopeHint }}</span>
          </div>

          <div v-if="featuredTeam" class="featured-card featured-team">
            <span class="featured-badge">NO.1</span>
            <div class="featured-logo">
              <img
                v-if="teamLogo(featuredTeam.team)"
                :src="teamLogo(featuredTeam.team)"
                loading="lazy"
                @error="onImgError"
              />
              <div v-else class="logo-fallback">{{ featuredTeam.team }}</div>
            </div>
            <div class="featured-main">
              <div class="featured-name">{{ teamName(featuredTeam.team) }}</div>
              <div class="featured-meta">
                <span>{{ featuredTeam.team }}</span>
                <template v-if="playerScope === 'season'">
                  <span>·</span>
                  <span>{{ featuredTeam.games || 0 }} 场</span>
                </template>
                <template v-else>
                  <span>·</span>
                  <span>净胜 {{ formatMargin(featuredTeam.margin) }}</span>
                </template>
              </div>
              <div v-if="playerScope === 'daily'" class="featured-stats">
                <span v-for="s in dailyStats(featuredTeam)" :key="s.label" class="featured-stat">
                  <b>{{ s.value }}</b>
                  <i>{{ s.label }}</i>
                </span>
              </div>
            </div>
            <div class="featured-value">
              <span class="featured-value-num">{{ formatValue(teamValue(featuredTeam)) }}</span>
              <span class="featured-value-label">{{ featuredValueLabel }}</span>
            </div>
          </div>

          <div v-if="restTeams.length" class="rank-table">
            <div class="rank-row rank-head">
              <div class="col-rank">排名</div>
              <div class="col-player">球队</div>
              <div class="col-num">{{ statLabel }}</div>
              <div class="col-num">{{ playerScope === "daily" ? "净胜" : "场次" }}</div>
            </div>
            <div v-for="(t, idx) in restTeams" :key="t.team" class="rank-row">
              <div class="col-rank">
                <span class="rank-num" :class="rankClass(idx + 2)">{{ idx + 2 }}</span>
              </div>
              <div class="col-player">
                <div class="logo-box">
                  <img
                    v-if="teamLogo(t.team)"
                    :src="teamLogo(t.team)"
                    class="team-logo"
                    loading="lazy"
                    @error="onImgError"
                  />
                  <div v-else class="logo-fallback">{{ t.team }}</div>
                </div>
                <div class="player-text">
                  <span class="player-name">{{ teamName(t.team) }}</span>
                  <span class="player-pos">{{ t.team }}</span>
                </div>
              </div>
              <div class="col-num strong">{{ formatValue(teamValue(t)) }}</div>
              <div
                class="col-num"
                :class="playerScope === 'daily' && t.margin > 0 ? 'positive' : playerScope === 'daily' && t.margin < 0 ? 'negative' : ''"
              >
                {{ teamExtra(t) }}
              </div>
            </div>
          </div>

          <el-empty v-if="!leadersLoading && !leaderTeams.length" description="暂无球队数据" />
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getTeamStandings, getPlayerStatLeaders, getDailyLeaders, getTeamPage } from "@/api/portal";

const mainTabs = [
  { label: "球队排名", value: "team" },
  { label: "球员数据排名", value: "player" },
] as const;

const scopeOptions = [
  { label: "东部", value: "Eastern" },
  { label: "西部", value: "Western" },
  { label: "分区", value: "division" },
];

const divisions = [
  { label: "大西洋", value: "Atlantic" },
  { label: "中部", value: "Central" },
  { label: "东南", value: "Southeast" },
  { label: "西北", value: "Northwest" },
  { label: "太平洋", value: "Pacific" },
  { label: "西南", value: "Southwest" },
];

const scopeTabs = [
  { label: "每日", value: "daily" },
  { label: "赛季", value: "season" },
] as const;

const statTabs = [
  { label: "得分", value: "points" },
  { label: "篮板", value: "rebounds" },
  { label: "助攻", value: "assists" },
  { label: "抢断", value: "steals" },
  { label: "盖帽", value: "blocks" },
  { label: "三分", value: "threePointers" },
  { label: "命中率", value: "fieldGoalPercentage" },
];

const mainTab = ref<"team" | "player">("team");
const teamScope = ref("Eastern");
const division = ref("Atlantic");
const playerScope = ref<"daily" | "season">("daily");
const statTab = ref("points");

const standings = ref<any[]>([]);
const standingsSeason = ref(0);
const standingsLoading = ref(false);

const leadersLoading = ref(false);

const dailyLeaders = ref<any[]>([]);
const dailyTeams = ref<any[]>([]);
const dailyDate = ref("");
const dailyCache = ref<Record<string, { leaders: any[]; teams: any[]; date: string }>>({});

const seasonLeaders = ref<any[]>([]);
const seasonTeams = ref<any[]>([]);
const playerSeason = ref(0);
const seasonCache = ref<Record<string, { leaders: any[]; teams: any[]; season: number }>>({});

let requestSeq = 0;

const statLabel = computed(
  () => statTabs.find((tab) => tab.value === statTab.value)?.label || "",
);

const isDaily = computed(() => playerScope.value === "daily");

const isPercentage = computed(() => statTab.value === "fieldGoalPercentage");

const scopeHint = computed(() => {
  if (isPercentage.value) return "投篮命中率 · 前 5";
  return isDaily.value ? "比赛单场数据 · 前 5" : "赛季场均数据 · 前 5";
});

const teamScopeHint = computed(() => {
  if (isPercentage.value) return "球队投篮命中率 · 前 5";
  return isDaily.value ? "球队单场数据 · 前 5" : "球队赛季场均数据 · 前 5";
});

const featuredValueLabel = computed(() => {
  if (isPercentage.value) return statLabel.value;
  return `${statLabel.value} · ${isDaily.value ? "单场" : "场均"}`;
});

const playerSeasonText = computed(() =>
  playerSeason.value
    ? `${playerSeason.value - 1}-${String(playerSeason.value).slice(2)} 赛季`
    : "",
);

const playerRows = computed(() => (isDaily.value ? dailyLeaders.value : seasonLeaders.value));
const leaderTeams = computed(() => (isDaily.value ? dailyTeams.value : seasonTeams.value));
const featuredPlayer = computed(() => playerRows.value[0]);
const featuredTeam = computed(() => leaderTeams.value[0]);
const restPlayers = computed(() => playerRows.value.slice(1, 5));
const restTeams = computed(() => leaderTeams.value.slice(1, 5));

const formatMargin = (value: number) => {
  const margin = value || 0;
  return margin > 0 ? `+${margin}` : `${margin}`;
};

/** 每日各统计字段（proto 零值不输出，统一兜底 0） */
const statsOf = (row: any) => ({
  points: row.points || 0,
  rebounds: row.rebounds || 0,
  assists: row.assists || 0,
  steals: row.steals || 0,
  blocks: row.blocks || 0,
  threePointers: row.threePointers || 0,
  fieldGoalPercentage: row.fieldGoalPercentage || 0,
});

const playerValue = (row: any) => (isDaily.value ? dailyValue(row) : row.value || 0);

const teamValue = (row: any) => (isDaily.value ? dailyValue(row) : row.value || 0);

const dailyValue = (row: any) => {
  const stats = statsOf(row);
  switch (statTab.value) {
    case "rebounds":
      return stats.rebounds;
    case "assists":
      return stats.assists;
    case "steals":
      return stats.steals;
    case "blocks":
      return stats.blocks;
    case "threePointers":
      return stats.threePointers;
    case "fieldGoalPercentage":
      return stats.fieldGoalPercentage;
    default:
      return stats.points;
  }
};

const formatValue = (value: number) => {
  if (isPercentage.value) return `${(value || 0).toFixed(1)}%`;
  return isDaily.value ? `${Math.round(value || 0)}` : (value || 0).toFixed(1);
};

const dailyStats = (row: any) => {
  const stats = statsOf(row);
  return [
    { label: "得分", value: stats.points },
    { label: "篮板", value: stats.rebounds },
    { label: "助攻", value: stats.assists },
    { label: "抢断", value: stats.steals },
    { label: "盖帽", value: stats.blocks },
    { label: "三分", value: stats.threePointers },
    { label: "命中率", value: `${stats.fieldGoalPercentage.toFixed(1)}%` },
  ];
};

const playerExtra = (row: any) =>
  isDaily.value ? `${row.rebounds || 0} 板 / ${row.assists || 0} 助` : `${row.games || 0} 场`;

const teamExtra = (row: any) =>
  isDaily.value ? formatMargin(row.margin) : `${row.games || 0} 场`;

const teamLogoMap = ref<Record<string, string>>({});
const teamNameMap = ref<Record<string, string>>({});

const seasonText = computed(() => {
  const s = standingsSeason.value;
  return s ? `${s - 1}-${String(s).slice(2)} 赛季` : "";
});

const teamRows = computed(() => {
  if (teamScope.value === "division") {
    return standings.value.filter((t) => t.division === division.value);
  }
  return standings.value.filter((t) => t.conference === teamScope.value);
});

/** 展示排名：东西部用 conferenceRank，分区用 divisionRank（服务端按胜率计算） */
const teamRank = (t: any, idx: number) =>
  teamScope.value === "division" ? t.divisionRank || idx + 1 : t.conferenceRank || idx + 1;

const rankClass = (rank: number) => {
  if (rank === 1) return "gold";
  if (rank === 2) return "silver";
  if (rank === 3) return "bronze";
  return "";
};

const formatPct = (pct: number) => `${((pct || 0) * 100).toFixed(1)}%`;

const formatGamesBack = (gb: number) => (!gb ? "-" : gb.toFixed(1));

const streakClass = (streak: string) => {
  if (!streak) return "";
  if (streak.startsWith("W")) return "streak-win";
  if (streak.startsWith("L")) return "streak-loss";
  return "";
};

/** NBA 官方 CDN 队标 */
const teamLogo = (key: string) => teamLogoMap.value[key] || "";
const teamName = (key: string) => teamNameMap.value[key] || key;

const fetchStandings = async () => {
  standingsLoading.value = true;
  try {
    const res = await getTeamStandings({});
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取球队排名失败");
      return;
    }
    standings.value = res.data?.standings || [];
    standingsSeason.value = res.data?.season || 0;
  } catch (error) {
    ElMessage.error("获取球队排名失败");
    console.error(error);
  } finally {
    standingsLoading.value = false;
  }
};

const applyDaily = (data: { leaders: any[]; teams: any[]; date: string }) => {
  dailyLeaders.value = data.leaders;
  dailyTeams.value = data.teams;
  dailyDate.value = data.date;
};

const applySeason = (data: { leaders: any[]; teams: any[]; season: number }) => {
  seasonLeaders.value = data.leaders;
  seasonTeams.value = data.teams;
  playerSeason.value = data.season;
};

const fetchDaily = async (statType: string) => {
  const cached = dailyCache.value[statType];
  if (cached) {
    applyDaily(cached);
    return;
  }
  const requestId = ++requestSeq;
  leadersLoading.value = true;
  try {
    const res = await getDailyLeaders({ limit: 5, statType });
    if (requestId !== requestSeq) return;
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取每日数据失败");
      return;
    }
    const data = {
      leaders: res.data?.leaders || [],
      teams: res.data?.teams || [],
      date: res.data?.date || "",
    };
    dailyCache.value[statType] = data;
    applyDaily(data);
  } catch (error) {
    if (requestId === requestSeq) ElMessage.error("获取每日数据失败");
    console.error(error);
  } finally {
    if (requestId === requestSeq) leadersLoading.value = false;
  }
};

const fetchSeason = async (statType: string) => {
  const cached = seasonCache.value[statType];
  if (cached) {
    applySeason(cached);
    return;
  }
  const requestId = ++requestSeq;
  leadersLoading.value = true;
  try {
    const res = await getPlayerStatLeaders({ statType, limit: 5 });
    if (requestId !== requestSeq) return;
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取球员数据排名失败");
      return;
    }
    const data = {
      leaders: res.data?.leaders || [],
      teams: res.data?.teams || [],
      season: res.data?.season || 0,
    };
    seasonCache.value[statType] = data;
    applySeason(data);
  } catch (error) {
    if (requestId === requestSeq) ElMessage.error("获取球员数据排名失败");
    console.error(error);
  } finally {
    if (requestId === requestSeq) leadersLoading.value = false;
  }
};

const loadLeaders = () => {
  if (isDaily.value) {
    fetchDaily(statTab.value);
  } else {
    fetchSeason(statTab.value);
  }
};

const switchScope = (scope: "daily" | "season") => {
  if (playerScope.value === scope) return;
  playerScope.value = scope;
  loadLeaders();
};

const switchStat = (statType: string) => {
  if (statTab.value === statType) return;
  statTab.value = statType;
  loadLeaders();
};

const fetchTeams = async () => {
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 50 });
    if (res.code === 200) {
      const logos: Record<string, string> = {};
      const names: Record<string, string> = {};
      for (const t of res.data?.teams || []) {
        if (!t.key) continue;
        if (t.nbaDotComTeamId) {
          logos[t.key] = `https://cdn.nba.com/logos/nba/${t.nbaDotComTeamId}/global/L/logo.svg`;
        }
        if (t.name) {
          names[t.key] = t.name;
        }
      }
      teamLogoMap.value = logos;
      teamNameMap.value = names;
    }
  } catch (error) {
    console.error("加载球队队标失败:", error);
  }
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  fetchStandings();
  fetchDaily("points");
  fetchTeams();
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
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

.main-tabs,
.sub-filter {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.sub-filter {
  margin-bottom: 16px;
}

.daily-date {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 4px;
}

.leader-boards {
  position: relative;
  min-height: 340px;
}

.leader-board + .leader-board {
  margin-top: 26px;
}

.board-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0 2px 12px;
}

.board-title {
  font-size: 16px;
  font-weight: 700;
  color: #e2e8f0;
}

.board-hint {
  font-size: 12px;
  color: #94a3b8;
}

/* 第一名放大卡片 */
.featured-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 22px 26px;
  margin-bottom: 14px;
  border-radius: 16px;
  border: 1px solid rgba(249, 115, 22, 0.28);
  background:
    radial-gradient(circle at 82% 18%, rgba(249, 115, 22, 0.26), transparent 52%),
    linear-gradient(135deg, rgba(30, 41, 59, 0.96), rgba(15, 23, 42, 0.96));
  overflow: hidden;
}

.featured-badge {
  position: absolute;
  top: 14px;
  right: 18px;
  padding: 2px 9px;
  border: 1px solid rgba(249, 115, 22, 0.5);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #fb923c;
  pointer-events: none;
}

.featured-photo {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid rgba(249, 115, 22, 0.55);
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
}

.featured-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.featured-logo {
  width: 88px;
  height: 88px;
  flex-shrink: 0;
  padding: 8px;
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
}

.featured-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.featured-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  z-index: 1;
}

.featured-name {
  font-size: 22px;
  font-weight: 800;
  color: #f8fafc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.featured-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #94a3b8;
}

.featured-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  margin-top: 8px;
}

.featured-stat {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.featured-stat b {
  font-size: 14px;
  font-weight: 700;
  color: #e2e8f0;
}

.featured-stat i {
  font-style: normal;
  font-size: 11px;
  color: #94a3b8;
}

.featured-value {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  padding-right: 8px;
}

.featured-value-num {
  font-size: 44px;
  font-weight: 800;
  line-height: 1;
  color: #fb923c;
  font-variant-numeric: tabular-nums;
}

.featured-value-label {
  font-size: 12px;
  color: #94a3b8;
}

.filter-divider {
  width: 1px;
  height: 16px;
  background: rgba(255, 255, 255, 0.14);
  margin: 0 4px;
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

.filter-btn.small {
  padding: 5px 14px;
  font-size: 12px;
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

/* 排行表格 */
.rank-table {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  overflow: hidden;
  min-height: 220px;
}

.rank-row {
  display: flex;
  align-items: center;
  padding: 10px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  transition: background 0.16s;
}

.rank-row:last-child {
  border-bottom: none;
}

.rank-row:not(.rank-head):hover {
  background: rgba(249, 115, 22, 0.06);
}

.rank-row.top-three {
  background: rgba(249, 115, 22, 0.04);
}

.rank-head {
  color: #94a3b8;
  font-size: 12px;
  padding: 12px 18px;
  background: rgba(255, 255, 255, 0.03);
}

.col-rank {
  width: 56px;
  flex-shrink: 0;
  text-align: center;
}

.col-team,
.col-player {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.col-num {
  width: 72px;
  flex-shrink: 0;
  text-align: center;
  color: #cbd5e1;
  font-size: 13px;
}

.col-num.strong {
  color: #f1f5f9;
  font-weight: 700;
}

.col-num.positive {
  color: #4ade80;
}

.col-num.negative {
  color: #f87171;
}

.rank-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.06);
}

.rank-num.gold {
  color: #1e293b;
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
}

.rank-num.silver {
  color: #1e293b;
  background: linear-gradient(135deg, #e2e8f0, #94a3b8);
}

.rank-num.bronze {
  color: #fff;
  background: linear-gradient(135deg, #d97706, #92400e);
}

.logo-box {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
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
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
}

.photo-box {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
}

.player-photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-fallback {
  font-size: 14px;
  font-weight: 700;
  color: #94a3b8;
}

.team-text,
.player-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.team-name,
.player-name {
  font-size: 14px;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.team-city,
.player-pos {
  font-size: 12px;
  color: #94a3b8;
}

.streak-win {
  color: #4ade80;
  font-weight: 600;
}

.streak-loss {
  color: #f87171;
  font-weight: 600;
}

/* 响应式 */
@media (max-width: 900px) {
  .col-num {
    width: 54px;
  }
  .rank-row {
    padding: 10px 12px;
  }
  .featured-card {
    gap: 16px;
    padding: 18px;
  }
  .featured-photo {
    width: 76px;
    height: 76px;
  }
  .featured-logo {
    width: 72px;
    height: 72px;
  }
  .featured-value-num {
    font-size: 34px;
  }
  .featured-stats {
    gap: 6px 14px;
  }
}
</style>
