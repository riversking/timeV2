<template>
  <el-dialog
    v-model="visible"
    width="820px"
    align-center
    class="news-detail-dialog"
  >
    <template #header>
      <div class="detail-title">
        <span class="name">{{ detail?.title || news?.title || "资讯详情" }}</span>
      </div>
    </template>
    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <div v-if="detail.imageUrl" class="detail-hero">
          <img :src="detail.imageUrl" @error="onImgError" />
        </div>
        <div class="detail-meta">
          <span v-if="detail.byline" class="byline">{{ detail.byline }}</span>
          <span class="time">{{ formatTime(detail.published) }}</span>
          <span class="source">来源 ESPN</span>
        </div>
        <div v-if="detail.content" class="article" v-html="detail.content"></div>
        <div v-else class="no-content">
          <p class="desc">{{ detail.description || news?.description }}</p>
          <p class="tip">
            该报道暂无正文内容（可能为视频或付费内容），可点击下方「查看 ESPN 原文」前往阅读。
          </p>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="详情加载失败，请稍后再试" />
    </div>
    <template #footer>
      <a
        v-if="detail?.link"
        :href="detail.link"
        target="_blank"
        rel="noopener"
        class="origin-link"
      >
        查看 ESPN 原文 →
      </a>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { getNewsDetail } from "@/api/portal";

const props = defineProps<{
  /** 当前新闻条目（列表项，含 id） */
  news: any | null;
}>();

const visible = defineModel<boolean>({ default: false });

const loading = ref(false);
const detail = ref<any>(null);

/** 打开弹窗时加载详情（正文由服务端白名单清洗后以 HTML 返回） */
watch(visible, (open) => {
  if (open) {
    loadDetail();
  }
});

const loadDetail = async () => {
  const id = props.news?.id || parseIdFromLink(props.news?.link);
  detail.value = null;
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const res = await getNewsDetail({ id: String(id) });
    if (res.code === 200) {
      const d = res.data?.detail;
      detail.value = d && (d.title || d.content) ? d : null;
    } else {
      ElMessage.error(res.message || "获取资讯详情失败");
    }
  } catch (error) {
    console.error("加载新闻详情失败:", error);
    ElMessage.error("获取资讯详情失败");
  } finally {
    loading.value = false;
  }
};

/** 兜底：列表项无 id 时从 ESPN 链接中提取文章ID */
const parseIdFromLink = (link: string) => {
  const matched = /\/id\/(\d+)/.exec(link || "");
  return matched ? matched[1] : "";
};

/** ESPN 发布时间 ISO 串 → "YYYY-MM-DD HH:mm" */
const formatTime = (iso: string) => {
  if (!iso) return "";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const onImgError = (e: Event) => {
  (e.target as HTMLImageElement).style.display = "none";
};
</script>

<style scoped>
/* 详情弹窗 */
.detail-title .name {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.4;
  padding-right: 28px;
}

.detail-body {
  min-height: 220px;
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 4px;
}

.detail-hero {
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 14px;
  background: #f1f5f9;
}

.detail-hero img {
  width: 100%;
  display: block;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  font-size: 12.5px;
  color: #64748b;
  flex-wrap: wrap;
}

.detail-meta .byline {
  font-weight: 600;
  color: #334155;
}

.detail-meta .source {
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 2px 10px;
}

/* 正文排版（v-html 内容） */
.article {
  font-size: 14px;
  color: #334155;
  line-height: 1.8;
}

.article :deep(p) {
  margin: 0 0 14px;
}

.article :deep(h2),
.article :deep(h3),
.article :deep(h4) {
  color: #0f172a;
  margin: 22px 0 10px;
  line-height: 1.4;
}

.article :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 10px;
  margin: 6px 0;
}

.article :deep(a) {
  color: #ea580c;
  text-decoration: none;
}

.article :deep(a:hover) {
  text-decoration: underline;
}

.article :deep(ul),
.article :deep(ol) {
  padding-left: 22px;
  margin: 0 0 14px;
}

.article :deep(blockquote) {
  margin: 0 0 14px;
  padding: 8px 14px;
  border-left: 3px solid #f97316;
  background: #f8fafc;
  color: #475569;
}

.article :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0 0 14px;
}

.article :deep(td),
.article :deep(th) {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  font-size: 13px;
  text-align: left;
}

.article :deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 18px 0;
}

.no-content .desc {
  color: #334155;
  line-height: 1.7;
  margin: 0 0 10px;
}

.no-content .tip {
  color: #94a3b8;
  font-size: 12.5px;
  margin: 0;
}

.origin-link {
  color: #ea580c;
  font-size: 13px;
  text-decoration: none;
  margin-right: 14px;
}

.origin-link:hover {
  text-decoration: underline;
}
</style>
