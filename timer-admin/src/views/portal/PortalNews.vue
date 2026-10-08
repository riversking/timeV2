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
import { getNews } from "@/api/portal";

const news = ref<any[]>([]);
const loadingNews = ref(false);

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
  .news-thumb {
    width: 130px;
  }
}
</style>
