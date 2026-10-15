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
          v-if="viewMode === 'calendar'"
          v-model="calendarDate"
          type="date"
          placeholder="跳转任意日期"
          :clearable="false"
          class="date-picker"
        />
      </div>
    </div>

    <!-- 列表视图：以当天为锚点的时间线，上滑更早 / 下滑更晚 -->
    <template v-if="viewMode === 'list'">
      <div v-loading="loading" class="timeline">
        <!-- 顶部哨兵：接近页首时加载更早的比赛 -->
        <div ref="topRef" class="load-sentinel">
          <div v-if="loadingOlder" class="load-status">
            <el-icon class="is-loading" :size="16"><Loading /></el-icon>
            <span>正在加载更早的比赛…</span>
          </div>
          <div v-else-if="noMoreOlder && games.length" class="load-end">
            — 已经是本赛季最早的比赛 —
          </div>
        </div>

        <div v-for="group in groupedGames" :key="group.day" class="day-group">
          <div class="day-header">
            <span class="day-label">{{ group.label }}</span>
            <span class="day-count">{{ group.items.length }} 场</span>
          </div>

          <div v-for="g in group.items" :key="g.gameId" class="game-row" @click="openGame(g)">
            <div class="game-time">
              <div class="time-main">{{ formatClock(g.dateTime) }}</div>
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

        <!-- 底部哨兵：接近页尾时加载更晚的比赛 -->
        <div ref="bottomRef" class="load-sentinel">
          <div v-if="loadingNewer" class="load-status">
            <el-icon class="is-loading" :size="16"><Loading /></el-icon>
            <span>正在加载更晚的比赛…</span>
          </div>
          <div v-else-if="noMoreNewer && games.length" class="load-end">
            — 已经是本赛季最新的比赛 —
          </div>
        </div>

        <el-empty v-if="!loading && !games.length" description="暂无赛程数据" />
      </div>
    </template>

    <!-- 日历视图：可浏览任意月份（含往赛季），用日期跳转查找 -->
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
            <div v-for="g in calendarGames" :key="g.gameId" class="day-item" @click="openGame(g)">
              <div class="day-time">{{ formatClock(g.dateTime) }}</div>
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
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from "vue";
import { useRouter } from "vue-router";
import { Loading } from "@element-plus/icons-vue";
import { getGamePage, getGameCalendar, getTeamPage } from "@/api/portal";

const router = useRouter();

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

/** 每批加载条数 */
const PAGE_SIZE = 20;
/** 距页面顶部/底部多少像素内触发加载 */
const EDGE_MARGIN = 400;

/** 中国观赛日（yyyy-MM-dd）：与后端比赛日口径一致 */
const chinaToday = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai" }).format(new Date());

const todayStr = ref(chinaToday());

/** NBA 赛季按结束年份标记（如 2026 年 10 月属于 2027 赛季） */
const currentSeason = () => {
  const [y, m] = todayStr.value.split("-").map(Number);
  return m >= 7 ? y + 1 : y;
};

const seasonLabel = computed(() => {
  const s = currentSeason();
  return `${s - 1}-${String(s % 100).padStart(2, "0")}`;
});

const shiftDay = (day: string, delta: number) => {
  const [y, m, d] = day.split("-").map(Number);
  const date = new Date(y, m - 1, d + delta);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")}`;
};

const dayLabel = (day: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) {
    return day || "未知日期";
  }
  const [y, m, d] = day.split("-").map(Number);
  const base = `${y === Number(todayStr.value.slice(0, 4)) ? "" : `${y}年`}${m}月${d}日 ${
    WEEKDAYS[new Date(y, m - 1, d).getDay()]
  }`;
  if (day === todayStr.value) {
    return `今天 · ${base}`;
  }
  if (day === shiftDay(todayStr.value, -1)) {
    return `昨天 · ${base}`;
  }
  if (day === shiftDay(todayStr.value, 1)) {
    return `明天 · ${base}`;
  }
  return base;
};

const formatClock = (dt: string) => {
  if (!dt) return "-";
  const m = dt.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
  return m ? `${m[4]}:${m[5]}` : dt;
};

/* ---------- 列表时间线 ---------- */

const games = ref<any[]>([]);
const loading = ref(false);
const loadingOlder = ref(false);
const loadingNewer = ref(false);
const noMoreOlder = ref(false);
const noMoreNewer = ref(false);
const topRef = ref<HTMLElement | null>(null);
const bottomRef = ref<HTMLElement | null>(null);

const selectedTeamId = ref<number | null>(null);
const teams = ref<any[]>([]);
const teamLogoMap = ref<Record<string, string>>({});

const teamLogo = (key: string) => teamLogoMap.value[key] || "";

/** 按（比赛日，开赛时间，id）正序：游标要求比赛日 + id，展示层再按时间细化同日顺序 */
const sortGames = (list: any[]) =>
  [...list].sort((a, b) => {
    const da = String(a.day || "");
    const db = String(b.day || "");
    if (da !== db) {
      return da < db ? -1 : 1;
    }
    const ta = String(a.dateTime || "");
    const tb = String(b.dateTime || "");
    if (ta !== tb) {
      return ta < tb ? -1 : 1;
    }
    return Number(a.id || 0) - Number(b.id || 0);
  });

const mergeGames = (incoming: any[]) => {
  const map = new Map<any, any>();
  games.value.forEach((g) => map.set(g.gameId, g));
  incoming.forEach((g) => map.set(g.gameId, g));
  games.value = sortGames([...map.values()]);
};

/** 已加载集中 (day, id) 最大 / 最小的条目：作为行级翻页游标 */
const latestCursor = () => {
  let day = "";
  let id = 0;
  for (const g of games.value) {
    const d = String(g.day || "");
    const i = Number(g.id || 0);
    if (d > day || (d === day && i > id)) {
      day = d;
      id = i;
    }
  }
  return day ? { day, id } : null;
};

const earliestCursor = () => {
  let day = "";
  let id = 0;
  for (const g of games.value) {
    const d = String(g.day || "");
    const i = Number(g.id || 0);
    if (!day || d < day || (d === day && i < id)) {
      day = d;
      id = i;
    }
  }
  return day ? { day, id } : null;
};

const timelineReq = (mode: "forward" | "backward") => {
  const params: any = {
    currentPage: 1,
    pageSize: PAGE_SIZE,
    season: currentSeason(),
    teamId: selectedTeamId.value || 0,
    timelineMode: mode,
  };
  const cursor = mode === "forward" ? latestCursor() : earliestCursor();
  if (cursor) {
    params.cursorDay = cursor.day;
    params.cursorId = cursor.id;
  } else {
    params.anchorDay = todayStr.value;
  }
  return params;
};

const loadInitial = async () => {
  loading.value = true;
  games.value = [];
  noMoreOlder.value = false;
  noMoreNewer.value = false;
  try {
    const res = await getGamePage(timelineReq("forward"));
    if (res.code === 200) {
      const batch = res.data?.games || [];
      if (batch.length) {
        mergeGames(batch);
      }
      noMoreNewer.value = batch.length < PAGE_SIZE;
    }
  } catch (error) {
    console.error("加载赛程失败:", error);
  } finally {
    loading.value = false;
  }
  await nextTick();
  if (!games.value.length) {
    // 当天及以后没有比赛（休赛期/赛季末）：向前取最近的比赛
    await loadOlder();
  }
  maybeLoadNewer();
};

const loadOlder = async () => {
  if (loadingOlder.value || noMoreOlder.value || loading.value) {
    return;
  }
  loadingOlder.value = true;
  const prevHeight = document.documentElement.scrollHeight;
  const prevScroll = window.scrollY;
  try {
    const res = await getGamePage(timelineReq("backward"));
    if (res.code === 200) {
      const batch = res.data?.games || [];
      if (batch.length) {
        mergeGames(batch);
        await nextTick();
        const delta = document.documentElement.scrollHeight - prevHeight;
        // 顶部插入后补偿滚动位置：视口内容不跳变
        if (delta > 0) {
          window.scrollTo(0, prevScroll + delta);
        }
      }
      noMoreOlder.value = batch.length < PAGE_SIZE;
    }
  } catch (error) {
    console.error("加载更早比赛失败:", error);
  } finally {
    loadingOlder.value = false;
  }
};

const loadNewer = async () => {
  if (loadingNewer.value || noMoreNewer.value || loading.value) {
    return;
  }
  loadingNewer.value = true;
  let success = false;
  try {
    const res = await getGamePage(timelineReq("forward"));
    if (res.code === 200) {
      success = true;
      const batch = res.data?.games || [];
      if (batch.length) {
        mergeGames(batch);
      }
      noMoreNewer.value = batch.length < PAGE_SIZE;
    }
  } catch (error) {
    console.error("加载更晚比赛失败:", error);
  } finally {
    loadingNewer.value = false;
    if (success) {
      nextTick(maybeLoadNewer);
    }
  }
};

/** 底部哨兵进入触发区则加载更晚的比赛（加载完成后链式补查，覆盖内容不满一屏的情况） */
const maybeLoadNewer = () => {
  const el = bottomRef.value;
  if (!el || loading.value || loadingNewer.value || noMoreNewer.value) {
    return;
  }
  if (el.getBoundingClientRect().top <= window.innerHeight + EDGE_MARGIN) {
    loadNewer();
  }
};

/** 顶部哨兵进入触发区（页首附近）则加载更早的比赛；仅在向上滚动时触发，避免下滑浏览时无谓预取 */
let lastScrollY = 0;
const onScroll = () => {
  if (viewMode.value !== "list") {
    return;
  }
  const goingUp = window.scrollY < lastScrollY;
  lastScrollY = window.scrollY;
  const el = topRef.value;
  if (el && goingUp && games.value.length && !loadingOlder.value && !noMoreOlder.value) {
    const top = el.getBoundingClientRect().top;
    if (top >= -EDGE_MARGIN && top <= window.innerHeight) {
      loadOlder();
    }
  }
  maybeLoadNewer();
};

/** 页首向上滑动意图（滚轮/触摸）：页面已无法继续向上滚动时，同样加载更早的比赛 */
const canLoadOlder = () =>
  viewMode.value === "list" && games.value.length && !loadingOlder.value && !noMoreOlder.value;

const onWheel = (e: WheelEvent) => {
  if (e.deltaY < 0 && window.scrollY <= 1 && canLoadOlder()) {
    loadOlder();
  }
};

let touchStartY = 0;
const onTouchStart = (e: TouchEvent) => {
  touchStartY = e.touches[0]?.clientY ?? 0;
};
const onTouchMove = (e: TouchEvent) => {
  const y = e.touches[0]?.clientY ?? 0;
  if (y > touchStartY + 10 && window.scrollY <= 1 && canLoadOlder()) {
    loadOlder();
  }
};

const groupedGames = computed(() => {
  const groups: { day: string; label: string; items: any[] }[] = [];
  for (const g of games.value) {
    const day = String(g.day || (g.dateTime ? g.dateTime.slice(0, 10) : ""));
    let last = groups[groups.length - 1];
    if (!last || last.day !== day) {
      last = { day, label: dayLabel(day), items: [] };
      groups.push(last);
    }
    last.items.push(g);
  }
  return groups;
});

/* ---------- 日历视图 ---------- */

const viewMode = ref<"list" | "calendar">("list");
const calendarDate = ref(new Date());
const calendarMonth = ref("");
const dayCountMap = ref<Record<string, number>>({});
const calendarGames = ref<any[]>([]);
const loadingCalendar = ref(false);
const loadingDayGames = ref(false);

const monthKey = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;

const dayKey = (d: Date) =>
  `${monthKey(d)}-${String(d.getDate()).padStart(2, "0")}`;

const selectedDayText = computed(() => {
  const day = dayKey(calendarDate.value);
  const [y, m, d] = day.split("-").map(Number);
  return `${y}年${m}月${d}日 ${WEEKDAYS[new Date(y, m - 1, d).getDay()]}`;
});

const pageSub = computed(() => {
  if (viewMode.value === "calendar") {
    return "翻月浏览或选择任意日期查看赛程，可查看往年赛季";
  }
  return `${seasonLabel.value} 赛季 · 从今天开始：向上滑动查看更早，向下滑动查看更晚`;
});

/** 日历不做赛季过滤：任何月份的赛程（含往赛季）都能查到 */
const fetchCalendar = async () => {
  const month = monthKey(calendarDate.value);
  loadingCalendar.value = true;
  try {
    const res = await getGameCalendar({
      season: 0,
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

/* ---------- 交互 ---------- */

/** 点击比赛进入详情页（每节比分 + 球员数据） */
const openGame = (g: any) => {
  router.push(`/portal/game/${g.gameId}`);
};

const handleViewChange = (mode: "list" | "calendar") => {
  if (viewMode.value === mode) return;
  viewMode.value = mode;
  if (mode === "calendar") {
    fetchCalendar();
    fetchDayGames();
  } else if (!games.value.length) {
    loadInitial();
  }
};

const handleTeamChange = () => {
  if (viewMode.value === "list") {
    loadInitial();
  } else {
    fetchCalendar();
    fetchDayGames();
  }
};

// 日历选日 / 切月：月变化时重拉场次标记，月内选日只拉当日比赛
watch(calendarDate, () => {
  if (viewMode.value !== "calendar") return;
  if (monthKey(calendarDate.value) !== calendarMonth.value) {
    fetchCalendar();
  }
  fetchDayGames();
});

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
  loadInitial();
  fetchTeams();
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onScroll, { passive: true });
  window.addEventListener("wheel", onWheel, { passive: true });
  window.addEventListener("touchstart", onTouchStart, { passive: true });
  window.addEventListener("touchmove", onTouchMove, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener("scroll", onScroll);
  window.removeEventListener("resize", onScroll);
  window.removeEventListener("wheel", onWheel);
  window.removeEventListener("touchstart", onTouchStart);
  window.removeEventListener("touchmove", onTouchMove);
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

.date-picker :deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12) inset;
}

.date-picker :deep(.el-input__inner) {
  color: #cbd5e1;
}

.date-picker :deep(.el-input__inner)::placeholder {
  color: #94a3b8;
}

/* 时间线列表 */
.timeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 320px;
}

.day-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.day-header {
  position: sticky;
  top: 72px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 14px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.92);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.day-label {
  font-size: 14px;
  font-weight: 700;
  color: #fb923c;
  letter-spacing: 0.5px;
}

.day-count {
  font-size: 12px;
  color: #94a3b8;
}

.game-row {
  display: grid;
  grid-template-columns: 90px 1fr 120px 1fr 160px;
  align-items: center;
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 14px 20px;
  transition: all 0.2s ease;
  cursor: pointer;
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
  font-size: 15px;
  font-weight: 700;
  color: #f1f5f9;
  font-variant-numeric: tabular-nums;
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

/* 滚动加载状态 */
.load-sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 0 4px;
  min-height: 18px;
}

.load-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #94a3b8;
}

.load-end {
  font-size: 12.5px;
  color: #64748b;
  letter-spacing: 1px;
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
  cursor: pointer;
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
  .day-header {
    top: 66px;
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
