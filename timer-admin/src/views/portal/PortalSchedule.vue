<template>
  <div class="portal-schedule">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">赛程</h1>
        <p class="page-sub">{{ pageSub }}</p>
      </div>
      <div class="filter-bar">
        <div class="view-tabs">
          <button
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            @click="handleViewChange('list')"
          >
            列表
          </button>
          <button
            class="view-btn"
            :class="{ active: viewMode === 'calendar' }"
            @click="handleViewChange('calendar')"
          >
            日历
          </button>
        </div>
        <div class="season-tabs">
          <button
            v-for="opt in seasonOptions"
            :key="String(opt.value)"
            class="tab-btn"
            :class="{ active: season === opt.value && !selectedDate }"
            @click="handleSeasonChange(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>
        <el-select
          v-model="selectedTeamId"
          placeholder="按球队筛选"
          clearable
          class="team-select"
          @change="handleTeamChange"
        >
          <el-option
            v-for="t in teams"
            :key="t.teamId"
            :label="t.name"
            :value="t.teamId"
          />
        </el-select>
        <el-date-picker
          v-if="viewMode === 'list'"
          v-model="selectedDate"
          type="date"
          placeholder="按日期筛选"
          value-format="YYYY-MM-DD"
          clearable
          class="date-picker"
          @change="handleDateChange"
        />
      </div>
    </div>

    <!-- 列表视图 -->
    <template v-if="viewMode === 'list'">
      <div v-loading="loading" class="game-list">
        <div v-for="g in games" :key="g.gameId" class="game-row">
          <div class="game-time">
            <div class="time-main">{{ formatGameTime(g.dateTime) }}</div>
            <div class="time-sub">{{ g.season }} 赛季</div>
          </div>

          <div class="team away">
            <span class="team-name">{{ g.awayTeam }}</span>
            <img
              v-if="teamLogo(g.awayTeam)"
              :src="teamLogo(g.awayTeam)"
              class="team-logo"
              loading="lazy"
              @error="onImgError"
            />
          </div>

          <div class="game-center">
            <template v-if="g.isClosed">
              <span class="score" :class="{ win: g.awayTeamScore > g.homeTeamScore }">{{ g.awayTeamScore }}</span>
              <span class="vs-sep">-</span>
              <span class="score" :class="{ win: g.homeTeamScore > g.awayTeamScore }">{{ g.homeTeamScore }}</span>
            </template>
            <span v-else class="vs-text">VS</span>
          </div>

          <div class="team home">
            <img
              v-if="teamLogo(g.homeTeam)"
              :src="teamLogo(g.homeTeam)"
              class="team-logo"
              loading="lazy"
              @error="onImgError"
            />
            <span class="team-name">{{ g.homeTeam }}</span>
          </div>

          <div class="game-status">
            <el-tag :type="statusInfo(g).type" size="small" effect="dark">
              {{ statusInfo(g).text }}
            </el-tag>
            <span class="channel" v-if="g.channel">{{ g.channel }}</span>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !games.length" description="该条件下暂无比赛" />

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 40, 60]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </template>

    <!-- 日历视图 -->
    <template v-else>
      <div class="calendar-layout">
        <div v-loading="loadingCalendar" class="calendar-box">
          <el-calendar v-model="calendarDate">
            <template #date-cell="{ data }">
              <div class="cal-cell" :class="{ 'has-games': dayCountMap[data.day] > 0 }">
                <span class="cal-num">{{ Number(data.day.split("-")[2]) }}</span>
                <span v-if="dayCountMap[data.day]" class="cal-badge">
                  {{ dayCountMap[data.day] }} 场
                </span>
              </div>
            </template>
          </el-calendar>
        </div>

        <div class="calendar-games">
          <h3 class="day-title">{{ selectedDayText }} 的比赛</h3>
          <div v-loading="loadingDayGames" class="day-list">
            <div v-for="g in calendarGames" :key="g.gameId" class="day-item">
              <div class="day-time">{{ formatGameTime(g.dateTime) }}</div>
              <div class="day-match">
                <span class="team-name">{{ g.awayTeam }}</span>
                <img
                  v-if="teamLogo(g.awayTeam)"
                  :src="teamLogo(g.awayTeam)"
                  class="team-logo-sm"
                  loading="lazy"
                  @error="onImgError"
                />
                <span class="day-center">
                  <template v-if="g.isClosed">
                    <span class="score-sm" :class="{ win: g.awayTeamScore > g.homeTeamScore }">{{ g.awayTeamScore }}</span>
                    <span class="vs-sep">-</span>
                    <span class="score-sm" :class="{ win: g.homeTeamScore > g.awayTeamScore }">{{ g.homeTeamScore }}</span>
                  </template>
                  <span v-else class="vs-text">VS</span>
                </span>
                <img
                  v-if="teamLogo(g.homeTeam)"
                  :src="teamLogo(g.homeTeam)"
                  class="team-logo-sm"
                  loading="lazy"
                  @error="onImgError"
                />
                <span class="team-name">{{ g.homeTeam }}</span>
              </div>
              <div class="day-status">
                <el-tag :type="statusInfo(g).type" size="small" effect="dark">
                  {{ statusInfo(g).text }}
                </el-tag>
              </div>
            </div>
          </div>
          <el-empty
            v-if="!loadingDayGames && !calendarGames.length"
            description="该日暂无比赛"
            :image-size="70"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getGamePage, getGameCalendar, getTeamPage } from "@/api/portal";

const games = ref<any[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);

const season = ref(2027);
const selectedDate = ref<string | null>(null);
const selectedTeamId = ref<number | null>(null);
const teams = ref<any[]>([]);

const viewMode = ref<"list" | "calendar">("list");
const calendarDate = ref(new Date());
const calendarMonth = ref("");
const dayCountMap = ref<Record<string, number>>({});
const calendarGames = ref<any[]>([]);
const loadingCalendar = ref(false);
const loadingDayGames = ref(false);

// 缩写 → 队标 URL 映射
const teamLogoMap = ref<Record<string, string>>({});

const seasonOptions = [
  { label: "2027 赛季", value: 2027 },
  { label: "2026 赛季", value: 2026 },
  { label: "全部", value: 0 },
];

const teamLogo = (key: string) => teamLogoMap.value[key] || "";

const monthKey = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;

const dayKey = (d: Date) =>
  `${monthKey(d)}-${String(d.getDate()).padStart(2, "0")}`;

const selectedDayText = computed(() => dayKey(calendarDate.value));

const pageSub = computed(() => {
  if (viewMode.value === "calendar") {
    return "切换月份浏览赛程，点击日期查看当日比赛";
  }
  return `共 ${total.value} 场比赛，支持赛季 / 球队 / 日期筛选，按比赛日正序`;
});

const fetchGames = async () => {
  loading.value = true;
  try {
    const params: any = {
      currentPage: currentPage.value,
      pageSize: pageSize.value,
    };
    if (selectedDate.value) {
      params.date = selectedDate.value;
    } else if (season.value > 0) {
      params.season = season.value;
    }
    if (selectedTeamId.value) {
      params.teamId = selectedTeamId.value;
    }
    const res = await getGamePage(params);
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取赛程失败");
      return;
    }
    games.value = res.data?.games || [];
    total.value = res.data?.total || 0;
  } catch (error) {
    ElMessage.error("获取赛程失败");
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const fetchCalendar = async () => {
  const month = monthKey(calendarDate.value);
  loadingCalendar.value = true;
  try {
    const res = await getGameCalendar({
      season: season.value,
      month,
      teamId: selectedTeamId.value || 0,
    });
    if (res.code === 200) {
      const map: Record<string, number> = {};
      for (const d of res.data?.days || []) {
        map[d.day] = d.count;
      }
      dayCountMap.value = map;
      calendarMonth.value = month;
    }
  } catch (error) {
    console.error("加载日历失败:", error);
  } finally {
    loadingCalendar.value = false;
  }
};

const fetchDayGames = async () => {
  const day = dayKey(calendarDate.value);
  loadingDayGames.value = true;
  try {
    const params: any = {
      currentPage: 1,
      pageSize: 50,
      date: day,
    };
    if (selectedTeamId.value) {
      params.teamId = selectedTeamId.value;
    }
    const res = await getGamePage(params);
    if (res.code === 200) {
      calendarGames.value = res.data?.games || [];
    }
  } catch (error) {
    console.error("加载当日比赛失败:", error);
  } finally {
    loadingDayGames.value = false;
  }
};

const fetchTeams = async () => {
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 50 });
    if (res.code === 200) {
      teams.value = res.data?.teams || [];
      const map: Record<string, string> = {};
      for (const t of teams.value) {
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

const handleViewChange = (mode: "list" | "calendar") => {
  if (viewMode.value === mode) return;
  viewMode.value = mode;
  if (mode === "calendar") {
    fetchCalendar();
    fetchDayGames();
  } else {
    fetchGames();
  }
};

const handleSeasonChange = (val: number) => {
  season.value = val;
  selectedDate.value = null;
  currentPage.value = 1;
  if (viewMode.value === "list") {
    fetchGames();
  } else {
    fetchCalendar();
    fetchDayGames();
  }
};

const handleTeamChange = () => {
  currentPage.value = 1;
  if (viewMode.value === "list") {
    fetchGames();
  } else {
    fetchCalendar();
    fetchDayGames();
  }
};

const handleDateChange = () => {
  currentPage.value = 1;
  fetchGames();
};

const handleSizeChange = (val: number) => {
  pageSize.value = val;
  fetchGames();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchGames();
};

// 日历选日 / 切月：月变化时重拉场次标记，月内选日只拉当日比赛
watch(calendarDate, () => {
  if (viewMode.value !== "calendar") return;
  if (monthKey(calendarDate.value) !== calendarMonth.value) {
    fetchCalendar();
  }
  fetchDayGames();
});

const formatGameTime = (dt: string) => {
  if (!dt) return "-";
  const m = dt.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
  if (!m) return dt;
  return `${m[2]}-${m[3]} ${m[4]}:${m[5]}`;
};

const statusInfo = (g: any): { text: string; type: "primary" | "warning" | "info" | "success" } => {
  if (g.isClosed) return { text: "已结束", type: "info" };
  if (g.status === "InProgress") return { text: "进行中", type: "warning" };
  if (g.status === "Final") return { text: "已结束", type: "info" };
  return { text: "未开赛", type: "primary" };
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.visibility = "hidden";
};

onMounted(() => {
  fetchGames();
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

.filter-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

/* 视图切换 */
.view-tabs {
  display: flex;
  gap: 0;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.view-btn {
  padding: 7px 20px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.view-btn:hover {
  color: #f1f5f9;
}

.view-btn.active {
  color: #fff;
  background: linear-gradient(135deg, #f97316, #ea580c);
  font-weight: 600;
}

.season-tabs {
  display: flex;
  gap: 8px;
}

.tab-btn {
  padding: 7px 18px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: transparent;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #f1f5f9;
  border-color: rgba(255, 255, 255, 0.3);
}

.tab-btn.active {
  color: #fff;
  background: linear-gradient(135deg, #f97316, #ea580c);
  border-color: transparent;
  font-weight: 600;
}

.team-select {
  width: 160px;
}

.team-select :deep(.el-select__wrapper) {
  background-color: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12) inset;
}

.team-select :deep(.el-select__placeholder),
.team-select :deep(.el-select__selected-item) {
  color: #cbd5e1;
}

/* 赛程列表 */
.game-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
}

.game-row {
  display: grid;
  grid-template-columns: 110px 1fr 120px 1fr 160px;
  align-items: center;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 14px 20px;
  transition: all 0.2s ease;
}

.game-row:hover {
  border-color: rgba(249, 115, 22, 0.4);
  background: rgba(30, 41, 59, 0.95);
}

.game-time {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.time-main {
  font-size: 14px;
  font-weight: 600;
  color: #f1f5f9;
  font-variant-numeric: tabular-nums;
}

.time-sub {
  font-size: 11px;
  color: #64748b;
}

.team {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team.away {
  justify-content: flex-end;
}

.team.home {
  justify-content: flex-start;
}

.team-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.team-name {
  font-size: 15px;
  font-weight: 700;
  color: #e2e8f0;
  letter-spacing: 0.5px;
  width: 46px;
}

.team.away .team-name {
  text-align: right;
}

.team.home .team-name {
  text-align: left;
}

.game-center {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.score {
  font-size: 20px;
  font-weight: 800;
  color: #cbd5e1;
  font-variant-numeric: tabular-nums;
  min-width: 34px;
  text-align: center;
}

.score.win {
  color: #fb923c;
}

.vs-sep {
  color: #475569;
  font-size: 16px;
}

.vs-text {
  font-size: 14px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 2px;
}

.game-status {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.channel {
  font-size: 12px;
  color: #64748b;
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

/* 日历视图 */
.calendar-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.calendar-box {
  width: 560px;
  flex-shrink: 0;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 10px;
}

.calendar-box :deep(.el-calendar) {
  background-color: transparent;
  --el-calendar-border: transparent;
  --el-calendar-selected-bg-color: transparent;
  --el-text-color-primary: #cbd5e1;
}

.calendar-box :deep(.el-calendar__header) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.calendar-box :deep(.el-calendar__title) {
  color: #f1f5f9;
  font-weight: 700;
}

.calendar-box :deep(.el-calendar__button-group .el-button) {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.15);
  color: #cbd5e1;
}

.calendar-box :deep(.el-calendar-table thead th) {
  color: #94a3b8;
  font-weight: 600;
}

.calendar-box :deep(.el-calendar-table td) {
  border-color: rgba(255, 255, 255, 0.06);
}

.calendar-box :deep(.el-calendar-table .el-calendar-day) {
  height: 58px;
  padding: 4px;
}

.calendar-box :deep(.el-calendar-table .el-calendar-day:hover) {
  background-color: rgba(249, 115, 22, 0.1);
}

.calendar-box :deep(.el-calendar-table td.is-selected) {
  background-color: transparent;
}

.calendar-box :deep(.el-calendar-table td.is-selected .el-calendar-day) {
  background-color: rgba(249, 115, 22, 0.22);
}

.calendar-box :deep(.el-calendar-table .el-calendar-day.is-selected) {
  color: #f1f5f9;
}

.calendar-box :deep(.el-calendar-table .prev-month .el-calendar-day),
.calendar-box :deep(.el-calendar-table .next-month .el-calendar-day) {
  color: #475569;
}

.cal-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.cal-num {
  font-size: 14px;
  font-weight: 600;
  color: inherit;
}

.cal-cell.has-games .cal-num {
  color: #fb923c;
}

.cal-badge {
  font-size: 10px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(249, 115, 22, 0.18);
  color: #fb923c;
  white-space: nowrap;
}

/* 选中日比赛列表 */
.calendar-games {
  flex: 1;
  min-width: 0;
}

.day-title {
  margin: 0 0 14px;
  font-size: 17px;
  font-weight: 700;
  color: #f1f5f9;
  padding-left: 12px;
  position: relative;
}

.day-title::before {
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

.day-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}

.day-item {
  display: grid;
  grid-template-columns: 90px 1fr 90px;
  align-items: center;
  gap: 10px;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 12px 16px;
  transition: border-color 0.2s;
}

.day-item:hover {
  border-color: rgba(249, 115, 22, 0.4);
}

.day-time {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
  font-variant-numeric: tabular-nums;
}

.day-match {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.team-logo-sm {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

.day-match .team-name {
  font-size: 13.5px;
  width: auto;
  min-width: 40px;
}

.day-center {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 64px;
  justify-content: center;
}

.score-sm {
  font-size: 15px;
  font-weight: 800;
  color: #cbd5e1;
  font-variant-numeric: tabular-nums;
}

.score-sm.win {
  color: #fb923c;
}

.day-status {
  display: flex;
  justify-content: flex-end;
}

/* 响应式 */
@media (max-width: 900px) {
  .game-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .team.away,
  .team.home {
    justify-content: center;
  }
  .calendar-layout {
    flex-direction: column;
  }
  .calendar-box {
    width: 100%;
  }
  .day-item {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
