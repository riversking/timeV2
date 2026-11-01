<template>
  <div class="portal-players">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">球员库</h1>
        <p class="page-sub">按东西部选择球队，浏览该队现役球员；查看详情需登录</p>
      </div>
    </div>

    <div class="players-layout">
      <!-- 左侧：球队（东部 | 西部） -->
      <aside
        v-loading="teamsLoading"
        class="team-panel"
        :style="panelHeight ? { '--team-panel-height': `${panelHeight}px` } : undefined"
      >
        <div class="conference-columns">
          <div class="conf-col">
            <div class="conf-title">东部</div>
            <button
              v-for="t in eastTeams"
              :key="t.teamId"
              class="team-row"
              :class="{ active: selectedTeamId === t.teamId }"
              @click="selectTeam(t)"
            >
              <img
                v-if="teamLogo(t)"
                :src="teamLogo(t)"
                class="team-logo"
                loading="lazy"
                @error="onImgError"
              />
              <span class="team-name">{{ t.name }}</span>
            </button>
          </div>
          <div class="conf-col">
            <div class="conf-title">西部</div>
            <button
              v-for="t in westTeams"
              :key="t.teamId"
              class="team-row"
              :class="{ active: selectedTeamId === t.teamId }"
              @click="selectTeam(t)"
            >
              <img
                v-if="teamLogo(t)"
                :src="teamLogo(t)"
                class="team-logo"
                loading="lazy"
                @error="onImgError"
              />
              <span class="team-name">{{ t.name }}</span>
            </button>
          </div>
        </div>
      </aside>

      <!-- 右侧：该队球员（列表） -->
      <section class="players-main">
        <div v-loading="loading" class="list-wrap">
          <div v-if="players.length" ref="playerListRef" class="player-list">
            <div class="list-head">
              <span class="head-photo"></span>
              <span class="head-name">球员</span>
              <span class="head-meta">
                <span>号码</span>
                <span>位置</span>
                <span>身高</span>
              </span>
              <span class="head-action">操作</span>
            </div>
            <div
              v-for="p in players"
              :key="p.playerId"
              class="player-row"
              @click="handleCardClick(p)"
            >
              <div class="row-photo">
                <img
                  v-if="p.photoUrl"
                  :src="p.photoUrl"
                  class="row-photo-img"
                  loading="lazy"
                  @error="onImgError"
                />
                <div v-else class="row-photo-fallback">{{ (p.lastName || "?").charAt(0) }}</div>
              </div>
              <div class="row-name">{{ p.firstName }} {{ p.lastName }}</div>
              <div class="row-meta">
                <span class="row-jersey">{{ p.jersey ? `#${p.jersey}` : "-" }}</span>
                <span>{{ p.position || "-" }}</span>
                <span>{{ p.height ? `${p.height}cm` : "-" }}</span>
              </div>
              <div class="row-hint">
                <el-icon><Lock /></el-icon>
                查看详情
              </div>
            </div>
          </div>
          <el-empty v-else-if="!loading" description="该球队暂无球员" />
        </div>

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
      </section>
    </div>

    <!-- 球员详情弹窗（登录后可见） -->
    <el-dialog v-model="detailVisible" width="1180px" align-center class="player-detail-dialog">
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

        <div v-if="currentPlayer" class="season-block">
          <div class="season-head">
            <span class="season-title">赛季数据</span>
            <span v-if="currentSeasonStat?.team" class="season-team">{{ currentSeasonStat.team }}</span>
            <el-select
              v-if="seasonStats.length > 1"
              v-model="selectedSeason"
              size="small"
              class="season-select"
              placeholder="选择赛季"
            >
              <el-option
                v-for="s in seasonStats"
                :key="s.season"
                :label="seasonLabel(s.season)"
                :value="s.season"
              />
            </el-select>
          </div>
          <div v-if="leagueItems.length" class="league-block">
            <div class="league-head">
              <span class="league-title">联盟对比</span>
              <span class="league-sub">柱状图：每个指标按联盟最高值归一，橙色为本球员</span>
              <span class="league-legend">
                <i class="dot dot-self"></i>本球员
                <i class="dot dot-avg"></i>联盟平均
                <i class="dot dot-max"></i>联盟最高
              </span>
            </div>
            <div class="bar-chart">
              <div v-for="item in leagueItems" :key="item.key" class="bar-group">
                <div class="bar-plot">
                  <div class="bar bar-self" :style="{ height: `${item.selfPct}%` }">
                    <span class="bar-value">{{ item.value }}</span>
                  </div>
                  <div class="bar bar-avg" :style="{ height: `${item.avgPct}%` }">
                    <span class="bar-value">{{ item.avg }}</span>
                  </div>
                  <div class="bar bar-max" :style="{ height: `${item.maxPct}%` }">
                    <span class="bar-value">{{ item.max }}</span>
                  </div>
                </div>
                <div class="bar-label">{{ item.label }}</div>
              </div>
            </div>
            <div class="league-ranks">
              <span class="ranks-title">联盟排名</span>
              <span v-for="item in leagueItems" :key="item.key" class="rank-chip">
                {{ item.label }} <b>#{{ item.rank }}</b>
              </span>
              <span v-if="leagueTotal" class="ranks-total">共 {{ leagueTotal }} 人参与排名</span>
            </div>
          </div>

          <div v-loading="statsLoading" class="season-body">
            <div v-if="currentSeasonStat" class="season-grid">
              <div v-for="item in seasonItems" :key="item.label" class="season-item">
                <span class="stat-value">{{ item.value }}</span>
                <span class="stat-label">{{ item.label }}</span>
              </div>
            </div>
            <el-empty
              v-else-if="!statsLoading"
              description="暂无赛季数据"
              :image-size="60"
            />
          </div>

          <div class="history-block">
            <div class="history-head">
              <span class="season-title">历年数据</span>
              <el-radio-group v-model="historyType" size="small">
                <el-radio-button :value="1">季前赛</el-radio-button>
                <el-radio-button :value="2">常规赛</el-radio-button>
                <el-radio-button :value="3">季后赛</el-radio-button>
              </el-radio-group>
            </div>
            <el-table
              v-loading="statsLoading"
              :data="historyRows"
              size="small"
              stripe
              class="history-table"
              empty-text="暂无该分类数据"
            >
              <el-table-column label="赛季" min-width="84">
                <template #default="{ row }">{{ seasonLabel(row.season) }}</template>
              </el-table-column>
              <el-table-column prop="team" label="球队" min-width="62" />
              <el-table-column label="得分" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgPoints) }}</template>
              </el-table-column>
              <el-table-column label="分钟" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgMinutes) }}</template>
              </el-table-column>
              <el-table-column label="命中%" min-width="70" align="right">
                <template #default="{ row }">{{ pctText(row.fieldGoalPct) }}</template>
              </el-table-column>
              <el-table-column label="出手" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgFieldGoalsAttempted) }}</template>
              </el-table-column>
              <el-table-column label="三分%" min-width="70" align="right">
                <template #default="{ row }">{{ pctText(row.threePointPct) }}</template>
              </el-table-column>
              <el-table-column label="三分出手" min-width="80" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgThreePointersAttempted) }}</template>
              </el-table-column>
              <el-table-column label="罚球%" min-width="70" align="right">
                <template #default="{ row }">{{ pctText(row.freeThrowPct) }}</template>
              </el-table-column>
              <el-table-column label="罚球出手" min-width="80" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgFreeThrowsAttempted) }}</template>
              </el-table-column>
              <el-table-column label="篮板" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgRebounds) }}</template>
              </el-table-column>
              <el-table-column label="助攻" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgAssists) }}</template>
              </el-table-column>
              <el-table-column label="抢断" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgSteals) }}</template>
              </el-table-column>
              <el-table-column label="盖帽" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgBlocks) }}</template>
              </el-table-column>
              <el-table-column label="犯规" min-width="60" align="right">
                <template #default="{ row }">{{ oneDecimal(row.avgFouls) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { Lock } from "@element-plus/icons-vue";
import { getPlayerPage, getPlayerDetail, getPlayerSeasonStats, getTeamPage } from "@/api/portal";
import { useClientAuth } from "@/composables/useClientAuth";

const route = useRoute();
const { isClientLoggedIn, openLoginDialog } = useClientAuth();

const teams = ref<any[]>([]);
const teamsLoading = ref(false);
const selectedTeamId = ref<number | null>(null);

const currentPage = ref(1);
const pageSize = ref(12);
const total = ref(0);
const players = ref<any[]>([]);
const loading = ref(false);

const detailVisible = ref(false);
const detailLoading = ref(false);
const currentPlayer = ref<any>(null);

// 赛季数据（按赛季倒序，默认选中最近赛季）
const seasonStats = ref<any[]>([]);
const statsLoading = ref(false);
const selectedSeason = ref<number | null>(null);

// 历年数据（1季前赛 2常规赛 3季后赛，默认常规赛）
const seasonHistory = ref<any[]>([]);
const historyType = ref(2);

const historyRows = computed(() =>
  seasonHistory.value.filter((row) => Number(row.seasonType) === historyType.value),
);

const currentSeasonStat = computed(
  () => seasonStats.value.find((s) => s.season === selectedSeason.value) || null,
);

const seasonLabel = (season: number) => `${season - 1}-${String(season).slice(2)}`;

/** 后台已按场次换算，proto 零值字段可能缺省，按 0 处理 */
const oneDecimal = (value: any) => Number(value || 0).toFixed(1);

/** 命中率：出手为 0 时后台给 0，展示为 "-" */
const pctText = (value: any) => (Number(value || 0) > 0 ? `${oneDecimal(value)}%` : "-");

/** 联盟对比（后台同步时预计算落库）：柱状图每组三根柱，按该指标联盟最高值归一 */
const leagueItems = computed(() => {
  const items = (currentSeasonStat.value?.leagueCompare || []) as any[];
  return items.map((it) => {
    const max = Number(it.leagueMax || 0);
    const scale = max > 0 ? max : 1;
    const pct = (v: number) => Math.min(100, Math.max(v > 0 ? 2 : 0, (v / scale) * 100));
    return {
      key: it.key,
      label: it.label,
      rank: Number(it.rank || 0),
      totalPlayers: Number(it.totalPlayers || 0),
      value: oneDecimal(it.playerValue),
      avg: oneDecimal(it.leagueAvg),
      max: oneDecimal(it.leagueMax),
      selfPct: pct(Number(it.playerValue || 0)),
      avgPct: pct(Number(it.leagueAvg || 0)),
      maxPct: pct(max),
    };
  });
});

const leagueTotal = computed(() => leagueItems.value[0]?.totalPlayers || 0);

const seasonItems = computed(() => {
  const stat = currentSeasonStat.value;
  if (!stat) {
    return [];
  }
  return [
    { label: "场次", value: stat.games || 0 },
    { label: "场均得分", value: oneDecimal(stat.avgPoints) },
    { label: "篮板", value: oneDecimal(stat.avgRebounds) },
    { label: "助攻", value: oneDecimal(stat.avgAssists) },
    { label: "抢断", value: oneDecimal(stat.avgSteals) },
    { label: "盖帽", value: oneDecimal(stat.avgBlocks) },
    { label: "三分", value: oneDecimal(stat.avgThreePointersMade) },
    {
      label: "投篮命中率",
      value: Number(stat.fieldGoalPct || 0) > 0 ? `${oneDecimal(stat.fieldGoalPct)}%` : "-",
    },
  ];
});

const fetchSeasonStats = async (playerId: number) => {
  statsLoading.value = true;
  seasonStats.value = [];
  seasonHistory.value = [];
  selectedSeason.value = null;
  historyType.value = 2;
  try {
    const res = await getPlayerSeasonStats({ playerId });
    if (res.code === 200) {
      seasonStats.value = res.data?.seasons || [];
      seasonHistory.value = res.data?.history || [];
      selectedSeason.value = seasonStats.value[0]?.season ?? null;
    }
  } catch (error) {
    console.error("获取球员赛季数据失败:", error);
  } finally {
    statsLoading.value = false;
  }
};

// 门控暂存：未登录点详情 → 登录成功后自动打开
const pendingPlayerId = ref<number | null>(null);

// 左侧球队面板高度跟随右侧球员列表
const playerListRef = ref<HTMLElement | null>(null);
const panelHeight = ref<number | null>(null);
let listResizeObserver: ResizeObserver | null = null;

watch(
  playerListRef,
  (el) => {
    listResizeObserver?.disconnect();
    listResizeObserver = null;
    if (!el) {
      return;
    }
    panelHeight.value = el.offsetHeight;
    listResizeObserver = new ResizeObserver(() => {
      panelHeight.value = el.offsetHeight;
    });
    listResizeObserver.observe(el);
  },
  { flush: "post" },
);

onBeforeUnmount(() => listResizeObserver?.disconnect());

const byName = (a: any, b: any) => String(a.name || "").localeCompare(String(b.name || ""));
const eastTeams = computed(() => teams.value.filter((t) => t.conference === "Eastern").sort(byName));
const westTeams = computed(() => teams.value.filter((t) => t.conference === "Western").sort(byName));
const selectedTeam = computed(() => teams.value.find((t) => t.teamId === selectedTeamId.value) || null);

const teamLogo = (t: any) =>
  t.nbaDotComTeamId ? `https://cdn.nba.com/logos/nba/${t.nbaDotComTeamId}/global/L/logo.svg` : "";

const fetchTeams = async () => {
  teamsLoading.value = true;
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 50 });
    if (res.code === 200) {
      teams.value = res.data?.teams || [];
      const first = eastTeams.value[0] || westTeams.value[0];
      if (first) {
        selectedTeamId.value = first.teamId;
      }
    }
  } catch (error) {
    console.error("加载球队失败:", error);
  } finally {
    teamsLoading.value = false;
  }
};

const fetchPlayerList = async () => {
  loading.value = true;
  try {
    const res = await getPlayerPage({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      teamId: selectedTeamId.value || 0,
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

const selectTeam = (t: any) => {
  if (selectedTeamId.value === t.teamId) {
    return;
  }
  selectedTeamId.value = t.teamId;
  currentPage.value = 1;
  fetchPlayerList();
};

/** 打开球员详情（调详情接口，需登录） */
const openDetail = async (playerId: number) => {
  detailVisible.value = true;
  detailLoading.value = true;
  currentPlayer.value = null;
  fetchSeasonStats(playerId);
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

const handleSizeChange = (val: number) => {
  pageSize.value = val;
  currentPage.value = 1;
  fetchPlayerList();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchPlayerList();
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(async () => {
  await fetchTeams();
  await fetchPlayerList();
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

/* 左右布局：左栏固定窄宽，高度与右侧球员列表一致 */
.players-layout {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

/* 左侧球队面板：东西部合并为一张列表卡片 */
.team-panel {
  display: flex;
  flex-direction: column;
  height: var(--team-panel-height, auto);
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  overflow: hidden;
}

.conference-columns {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.conf-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.conf-col + .conf-col {
  border-left: 1px solid rgba(255, 255, 255, 0.08);
}

/* 与右侧球员列表标题行等高 */
.conf-title {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 16px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.5px;
}

/* 球队列表行，15 行均分撑满整列高度 */
.team-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  flex: 1;
  min-height: 34px;
  padding: 0 16px;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: transparent;
  color: #cbd5e1;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.team-row:last-child {
  border-bottom: none;
}

.team-row:hover {
  background: rgba(249, 115, 22, 0.08);
  color: #f1f5f9;
}

.team-row.active {
  background: rgba(249, 115, 22, 0.14);
  color: #fb923c;
  font-weight: 700;
}

.team-logo {
  width: 20px;
  height: 20px;
  object-fit: contain;
  flex-shrink: 0;
}

.team-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 右侧球员区域 */
.players-main {
  flex: 1;
  min-width: 0;
}

/* 球员列表 */
.list-wrap {
  min-height: 200px;
}

.player-list {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  overflow: hidden;
}

/* 列表解释行（列名） */
.list-head {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 36px;
  padding: 0 16px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.5px;
}

.head-photo {
  width: 44px;
  flex-shrink: 0;
}

.head-name {
  flex: 1;
  min-width: 0;
}

.head-meta {
  display: grid;
  grid-template-columns: 48px 44px 58px;
  gap: 12px;
  flex-shrink: 0;
}

.head-action {
  width: 78px;
  flex-shrink: 0;
  text-align: right;
}

.player-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 9px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: background 0.18s ease;
}

.player-row:last-child {
  border-bottom: none;
}

.player-row:hover {
  background: rgba(249, 115, 22, 0.08);
}

.row-photo {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, #334155, #1e293b);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.row-photo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.row-photo-fallback {
  font-size: 16px;
  font-weight: 800;
  color: #64748b;
}

.row-name {
  flex: 1;
  min-width: 0;
  font-size: 14.5px;
  font-weight: 600;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-meta {
  display: grid;
  grid-template-columns: 48px 44px 58px;
  gap: 12px;
  flex-shrink: 0;
  font-size: 12.5px;
  color: #94a3b8;
}

.row-jersey {
  color: #fb923c;
  font-weight: 700;
}

.row-hint {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  width: 78px;
  flex-shrink: 0;
  font-size: 12px;
  color: #64748b;
}

.player-row:hover .row-hint {
  color: #fb923c;
}

/* 分页（深色适配） */
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 22px;
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

/* 赛季数据 */
.season-block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #e2e8f0;
}

.season-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.season-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

.season-team {
  font-size: 12px;
  font-weight: 600;
  color: #ea580c;
  background: rgba(249, 115, 22, 0.12);
  border-radius: 12px;
  padding: 2px 8px;
}

.season-select {
  width: 120px;
  margin-left: auto;
}

/* 联盟对比：柱状图（每指标一组，本球员/联盟平均/联盟最高，按该组最高值归一） */
.league-block {
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.league-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.league-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.league-sub {
  font-size: 11px;
  color: #94a3b8;
}

.league-legend {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  font-size: 11px;
  color: #64748b;
}

.league-legend .dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  margin-left: 6px;
}

.league-legend .dot:first-child {
  margin-left: 0;
}

.dot-max {
  background: #e2e8f0;
  border: 1px solid #cbd5e1;
}

.dot-self {
  background: linear-gradient(180deg, #fb923c, #f97316);
}

.dot-avg {
  background: #94a3b8;
}

.bar-chart {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  padding: 22px 4px 0;
}

.bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bar-plot {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 150px;
  border-bottom: 1px solid #e2e8f0;
}

.bar {
  position: relative;
  width: 18px;
  min-height: 2px;
  border-radius: 4px 4px 0 0;
}

.bar-self {
  background: linear-gradient(180deg, #fb923c, #f97316);
}

.bar-avg {
  background: #94a3b8;
}

.bar-max {
  background: #e2e8f0;
  border: 1px solid #cbd5e1;
  border-bottom: none;
}

.bar-value {
  position: absolute;
  bottom: calc(100% + 3px);
  left: 50%;
  transform: translateX(-50%);
  font-size: 10.5px;
  font-weight: 700;
  color: #475569;
  white-space: nowrap;
}

.bar-self .bar-value {
  color: #ea580c;
}

.bar-label {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
}

.league-ranks {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed #e2e8f0;
}

.ranks-title {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
}

.rank-chip {
  font-size: 12px;
  color: #475569;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 2px 8px;
}

.rank-chip b {
  color: #ea580c;
}

.ranks-total {
  margin-left: auto;
  font-size: 11px;
  color: #94a3b8;
}

.season-body {
  min-height: 96px;
}

.season-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.season-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 6px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.season-item .stat-value {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.season-item .stat-label {
  font-size: 12px;
  color: #64748b;
}

/* 历年数据 */
.history-block {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #e2e8f0;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.history-table :deep(.el-table__cell) {
  padding: 5px 0;
}

.history-table :deep(.el-table__header .cell) {
  white-space: nowrap;
}

/* 响应式 */
@media (max-width: 900px) {
  .players-layout {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
  .team-panel {
    width: 100%;
    height: auto;
    min-height: 0;
    max-height: none;
  }
  .detail-layout {
    flex-direction: column;
  }
  .bar-chart {
    gap: 4px;
  }
  .bar-plot {
    height: 120px;
    gap: 5px;
  }
  .bar {
    width: 14px;
  }
}

@media (max-width: 600px) {
  .row-hint,
  .head-action {
    display: none;
  }
  .row-meta,
  .head-meta {
    gap: 8px;
  }
  .season-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .bar-chart {
    grid-template-columns: repeat(5, 84px);
    justify-content: start;
    overflow-x: auto;
  }
}
</style>

<style>
/* 详情弹窗（teleport 到 body，需全局样式）：限宽并允许内容区滚动 */
.player-detail-dialog {
  max-width: 94vw;
}

.player-detail-dialog .el-dialog__body {
  max-height: 72vh;
  overflow-y: auto;
}
</style>
