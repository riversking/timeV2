<template>
  <div class="portal-news">
    <!-- 页面头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">资讯中心</h1>
        <p class="page-sub">NBA 最新动态</p>
      </div>
      <span class="source-tag">来源 ESPN</span>
    </div>

    <div class="news-layout">
      <main class="news-main">
        <div v-loading="loadingNews" class="news-list">
          <div
            v-for="n in news"
            :key="n.link"
            class="news-item"
            @click="openDetail(n)"
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
          </div>
        </div>

        <!-- 滚动到底部自动加载更多 -->
        <div ref="sentinelRef" class="load-sentinel">
          <div v-if="loadingMore" class="load-status">
            <el-icon class="is-loading" :size="16"><Loading /></el-icon>
            <span>正在加载更多…</span>
          </div>
          <div v-else-if="!hasMore && news.length" class="load-end">
            — 没有更多了 —
          </div>
        </div>

        <el-empty
          v-if="!loadingNews && !news.length"
          description="暂无新闻，稍后再来看看"
        />
      </main>
    </div>

    <!-- 新闻详情弹窗（站内渲染，不跳转） -->
    <NewsDetailDialog v-model="detailVisible" :news="currentNews" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import { Loading } from "@element-plus/icons-vue";
import { getNews } from "@/api/portal";
import NewsDetailDialog from "@/components/NewsDetailDialog.vue";

/** 每次滚动的加载条数 */
const PAGE_SIZE = 10;
/** 距底部多少像素内触发加载 */
const TRIGGER_MARGIN = 300;

const news = ref<any[]>([]);
const loadingNews = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);
const sentinelRef = ref<HTMLElement | null>(null);

// 详情弹窗：点击列表项站内渲染，不跳转 ESPN
const detailVisible = ref(false);
const currentNews = ref<any>(null);

const openDetail = (n: any) => {
  currentNews.value = n;
  detailVisible.value = true;
};

/**
 * 滚动加载：每次请求「已展示数 + PAGE_SIZE」条，后端按 limit 截取列表前缀，
 * 响应整体替换（前缀覆盖已展示项，key 稳定不会闪烁）
 */
const loadNews = async (first: boolean) => {
  if (first) loadingNews.value = true;
  else loadingMore.value = true;
  const targetLimit = (first ? 0 : news.value.length) + PAGE_SIZE;
  try {
    const res = await getNews({ limit: targetLimit });
    if (res.code === 200) {
      const batch = res.data?.news || [];
      if (batch.length) {
        news.value = batch;
        hasMore.value = batch.length >= targetLimit;
      } else if (first) {
        news.value = [];
        hasMore.value = false;
      }
      // 非首次空批次视为数据源瞬断：保留现状，下次滚动重试
    }
  } catch (error) {
    console.error("加载新闻失败:", error);
  } finally {
    loadingNews.value = false;
    loadingMore.value = false;
    nextTick(maybeLoadMore);
  }
};

/**
 * 底部哨兵进入触发区则加载下一页。
 * 不用 IntersectionObserver：图片懒加载会异步撑高布局，交叉状态可能不再翻转导致加载停摆
 */
const maybeLoadMore = () => {
  if (!sentinelRef.value || loadingNews.value || loadingMore.value || !hasMore.value) {
    return;
  }
  if (sentinelRef.value.getBoundingClientRect().top <= window.innerHeight + TRIGGER_MARGIN) {
    loadNews(false);
  }
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
  loadNews(true);
  window.addEventListener("scroll", maybeLoadMore, { passive: true });
  window.addEventListener("resize", maybeLoadMore, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener("scroll", maybeLoadMore);
  window.removeEventListener("resize", maybeLoadMore);
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

/* 新闻流 */
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
  cursor: pointer;
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

/* 滚动加载状态 */
.load-sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 0 4px;
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

/* 追加条目淡入（仅 opacity，避免覆盖 hover 位移） */
.news-item {
  animation: news-fade-in 0.3s ease;
}

@keyframes news-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 响应式 */
@media (max-width: 900px) {
  .news-thumb {
    width: 130px;
  }
}
</style>
