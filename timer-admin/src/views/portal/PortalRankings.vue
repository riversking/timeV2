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
          <div class="col-num strong">{{ t.wins }}</div>
          <div class="col-num">{{ t.losses }}</div>
          <div class="col-num">{{ formatPct(t.winPct) }}</div>
          <div class="col-num">{{ formatGamesBack(t.gamesBack) }}</div>
          <div class="col-num">{{ t.lastTenWins }}-{{ t.lastTenLosses }}</div>
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
          v-for="tab in statTabs"
          :key="tab.value"
          class="filter-btn small"
          :class="{ active: statTab === tab.value }"
          @click="switchStat(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-loading="leadersLoading" class="rank-table" :class="{ 'is-loading': leadersLoading }">
        <div class="rank-row rank-head">
          <div class="col-rank">排名</div>
          <div class="col-player">球员</div>
          <div class="col-num">球队</div>
          <div class="col-num">场次</div>
          <div class="col-num">数值</div>
        </div>
        <div
          v-for="(p, idx) in leaders"
          :key="p.playerId"
          class="rank-row"
          :class="{ 'top-three': idx < 3 }"
        >
          <div class="col-rank">
            <span class="rank-num" :class="rankClass(idx + 1)">{{ idx + 1 }}</span>
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
          <div class="col-num">{{ p.games }}</div>
          <div class="col-num strong">{{ formatStatValue(p.value) }}</div>
        </div>
      </div>

      <el-empty
        v-if="!leadersLoading && !leaders.length"
        description="暂无球员数据"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getTeamStandings, getPlayerStatLeaders, getTeamPage } from "@/api/portal";

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
const statTab = ref("points");

const standings = ref<any[]>([]);
const standingsSeason = ref(0);
const standingsLoading = ref(false);

const leaders = ref<any[]>([]);
const leadersLoading = ref(false);
const leadersCache = ref<Record<string, any[]>>({});

const teamLogoMap = ref<Record<string, string>>({});

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

const formatStatValue = (value: number) =>
  statTab.value === "fieldGoalPercentage"
    ? `${(value || 0).toFixed(1)}%`
    : (value || 0).toFixed(1);

const streakClass = (streak: string) => {
  if (!streak) return "";
  if (streak.startsWith("W")) return "streak-win";
  if (streak.startsWith("L")) return "streak-loss";
  return "";
};

/** NBA 官方 CDN 队标 */
const teamLogo = (key: string) => teamLogoMap.value[key] || "";

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

const fetchLeaders = async (statType: string) => {
  if (leadersCache.value[statType]) {
    leaders.value = leadersCache.value[statType];
    return;
  }
  leadersLoading.value = true;
  try {
    const res = await getPlayerStatLeaders({ statType, limit: 20 });
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取球员数据排名失败");
      return;
    }
    leadersCache.value[statType] = res.data?.leaders || [];
    leaders.value = leadersCache.value[statType];
  } catch (error) {
    ElMessage.error("获取球员数据排名失败");
    console.error(error);
  } finally {
    leadersLoading.value = false;
  }
};

const switchStat = (statType: string) => {
  statTab.value = statType;
  fetchLeaders(statType);
};

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
    console.error("加载球队队标失败:", error);
  }
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  fetchStandings();
  fetchLeaders("points");
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
}
</style>
