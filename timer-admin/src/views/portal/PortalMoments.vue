<template>
  <div class="portal-moments">
    <div class="page-header">
      <div>
        <h1 class="page-title">球迷动态</h1>
        <p class="page-sub">分享你的 NBA 时刻</p>
      </div>
      <span v-if="total > 0" class="moment-count">共 {{ total }} 条</span>
    </div>

    <div class="moments-layout">
      <!-- 左侧：关注统计 / 社区 / 推荐 -->
      <aside class="left-rail">
        <section class="side-card follow-card">
          <div class="side-head">
            <h3>我的关注</h3>
            <span v-if="!isClientLoggedIn" class="side-more">登录后查看</span>
          </div>
          <div class="follow-stats">
            <div class="follow-stat">
              <span class="follow-value">{{ isClientLoggedIn ? myCommunityIds.length : "—" }}</span>
              <span class="follow-label">球队</span>
            </div>
            <div class="follow-stat">
              <span class="follow-value">{{ isClientLoggedIn ? following.length : "—" }}</span>
              <span class="follow-label">关注的人</span>
            </div>
            <div class="follow-stat">
              <span class="follow-value">{{ isClientLoggedIn ? followersCount : "—" }}</span>
              <span class="follow-label">粉丝</span>
            </div>
          </div>
        </section>

        <section class="side-card">
          <div class="side-head">
            <h3>球队社区</h3>
            <button v-if="activeCommunity" class="side-more side-reset" @click="exitCommunity">
              全部动态
            </button>
          </div>
          <div v-loading="teamsLoading" class="community-list scrollable">
            <div
              v-for="team in teams"
              :key="team.teamId"
              class="community-row"
              :class="{ active: isActiveCommunity(team.teamId) }"
              @click="openCommunity(team)"
            >
              <img :src="teamLogo(team)" class="team-logo" alt="" @error="onImgError" />
              <span class="community-name">{{ team.name }}</span>
              <button
                class="follow-toggle"
                :class="{ followed: isCommunityJoined(team.teamId) }"
                :disabled="communityUpdating === Number(team.teamId)"
                @click.stop="toggleCommunity(team)"
              >
                {{ isCommunityJoined(team.teamId) ? "已加入" : "+ 加入" }}
              </button>
            </div>
            <p v-if="!teamsLoading && !teams.length" class="side-empty">暂无球队数据</p>
          </div>
        </section>

        <section class="side-card">
          <div class="side-head">
            <h3>热门推荐</h3>
            <router-link to="/portal/news" class="side-more">更多 →</router-link>
          </div>
          <div v-loading="newsLoading" class="hot-list">
            <button
              v-for="(news, index) in hotNews"
              :key="news.link || index"
              class="hot-item"
              @click="openNews(news)"
            >
              <span class="hot-index" :class="{ top: index < 3 }">{{ index + 1 }}</span>
              <span class="hot-body">
                <span class="hot-title">{{ news.title }}</span>
                <span class="hot-time">{{ newsTime(news.published) }}</span>
              </span>
            </button>
            <p v-if="!newsLoading && !hotNews.length" class="side-empty">暂无推荐内容</p>
          </div>
        </section>
      </aside>

      <!-- 中间：动态流 -->
      <main class="moments-feed">
        <div v-if="activeCommunity" class="community-view">
          <img
            class="community-view-logo"
            :src="teamLogo(activeCommunity)"
            alt=""
            @error="onImgError"
          />
          <div class="community-view-main">
            <div class="community-view-title">{{ activeCommunity.name }}社区</div>
            <div class="community-view-sub">{{ total }} 篇文章</div>
          </div>
          <div class="community-view-actions">
            <button class="community-view-publish" @click="handlePublishEntry">发布到社区</button>
            <button
              class="follow-toggle"
              :class="{ followed: isCommunityJoined(activeCommunity.teamId) }"
              :disabled="communityUpdating === Number(activeCommunity.teamId)"
              @click="toggleCommunity(activeCommunity)"
            >
              {{ isCommunityJoined(activeCommunity.teamId) ? "已加入" : "+ 加入社区" }}
            </button>
            <button class="community-view-exit" @click="exitCommunity">全部动态</button>
          </div>
        </div>

        <div v-loading="loading" class="moment-list">
          <article v-for="moment in moments" :key="moment.id" class="moment-card">
            <header class="moment-head">
              <span class="avatar">{{ initial(moment.userName || moment.userId) }}</span>
              <div class="moment-meta">
                <span class="moment-author">{{ moment.userName || moment.userId }}</span>
                <span class="moment-sub">
                  <span class="moment-time">{{ relativeTime(moment.createTime) }}</span>
                  <button
                    v-if="communityLabel(moment.teamId)"
                    class="moment-community"
                    @click="openCommunityById(moment.teamId)"
                  >
                    {{ communityLabel(moment.teamId) }}
                  </button>
                </span>
              </div>
              <button
                v-if="!isMine(moment)"
                class="author-follow"
                :class="{ followed: isFollowing(moment.userId) }"
                :disabled="socialUpdatingId === moment.userId"
                @click="toggleUserFollow(moment.userId, moment.userName)"
              >
                {{ isFollowing(moment.userId) ? "已关注" : "+ 关注" }}
              </button>
              <el-popconfirm
                v-if="isMine(moment)"
                title="确定删除这条动态吗？"
                confirm-button-text="删除"
                cancel-button-text="取消"
                @confirm="handleDelete(moment)"
              >
                <template #reference>
                  <button class="delete-btn" title="删除动态">
                    <el-icon :size="15"><Delete /></el-icon>
                  </button>
                </template>
              </el-popconfirm>
            </header>

            <p v-if="moment.content" class="moment-content">{{ moment.content }}</p>

            <div
              v-if="imageList(moment).length"
              class="moment-images"
              :class="{ single: imageList(moment).length === 1 }"
            >
              <el-image
                v-for="(url, index) in imageList(moment)"
                :key="`${url}-${index}`"
                :src="url"
                :preview-src-list="imageList(moment)"
                :initial-index="index"
                fit="cover"
                lazy
                class="moment-image"
              >
                <template #error>
                  <div class="image-error">图片加载失败</div>
                </template>
              </el-image>
            </div>

            <footer class="moment-actions">
              <button
                class="moment-action"
                :class="{ active: moment.liked }"
                :disabled="likeUpdatingId === moment.id"
                :title="moment.liked ? '取消点赞' : '点赞'"
                aria-label="点赞"
                @click="handleLike(moment)"
              >
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M7 10v11H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1h3Z" />
                  <path d="M7 10l4-7a2 2 0 0 1 2 2v4h5.2a2 2 0 0 1 2 2.4l-1.2 7A2 2 0 0 1 17 20H7" />
                </svg>
                <span v-if="Number(moment.likeCount) > 0" class="action-count">{{ moment.likeCount }}</span>
              </button>
              <button class="moment-action" title="评论" aria-label="评论" @click="openComments(moment)">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M21 12a8 8 0 0 1-8 8H8l-5 3 1.5-5A8 8 0 1 1 21 12Z" />
                </svg>
                <span v-if="Number(moment.commentCount) > 0" class="action-count">{{ moment.commentCount }}</span>
              </button>
              <button class="moment-action" title="分享" aria-label="分享" @click="shareMoment(moment)">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                     stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <circle cx="18" cy="5" r="3" />
                  <circle cx="6" cy="12" r="3" />
                  <circle cx="18" cy="19" r="3" />
                  <path d="m8.6 10.5 6.8-4M8.6 13.5l6.8 4" />
                </svg>
              </button>
            </footer>
          </article>
        </div>

        <el-empty
          v-if="!loading && !moments.length"
          :description="activeCommunity ? '该社区还没有文章，来发第一篇吧' : '还没有动态，来发第一条吧'"
        />

        <div v-if="moments.length" class="load-more">
          <el-button v-if="hasMore" :loading="loadingMore" @click="loadPage(false)">
            加载更多
          </el-button>
          <span v-else class="load-end">— 没有更多了 —</span>
        </div>
      </main>

      <!-- 右侧：身份与发布入口 -->
      <aside class="right-rail">
        <div class="user-card">
          <span class="avatar">{{ avatarText }}</span>
          <span class="user-name">{{ isClientLoggedIn ? clientUsername : "未登录" }}</span>
          <span class="user-hint">
            {{ isClientLoggedIn ? "分享你的 NBA 时刻" : "登录后参与互动" }}
          </span>
        </div>
        <el-button class="publish-entry" type="primary" @click="handlePublishEntry">
          <el-icon><EditPen /></el-icon>
          <span>{{ activeCommunity ? "发布到社区" : "发布动态" }}</span>
        </el-button>
        <p class="rail-tip">支持文字与图片链接，最多 9 张图片</p>
      </aside>
    </div>

    <!-- 发布弹窗 -->
    <el-dialog
      v-model="composeVisible"
      width="560px"
      align-center
      class="compose-dialog"
      :close-on-click-modal="false"
    >
      <template #header>
        <div class="dialog-title">
          <span class="logo-dot"></span>
          {{ activeCommunity ? `发布到${activeCommunity.name}社区` : "发布动态" }}
        </div>
      </template>

      <div v-if="activeCommunity" class="compose-community">
        <span class="compose-community-label">发布到</span>
        <span class="compose-team">
          <img
            v-if="teamLogo(activeCommunity)"
            :src="teamLogo(activeCommunity)"
            alt=""
            @error="onImgError"
          />
          {{ activeCommunity.name }}社区
        </span>
      </div>

      <el-input
        v-model="content"
        type="textarea"
        :rows="5"
        :maxlength="MAX_CONTENT"
        show-word-limit
        resize="none"
        placeholder="分享比赛感受、球员表现或你的篮球故事…"
        class="compose-input"
      />

      <div v-if="draftImages.length" class="draft-images">
        <div v-for="(url, index) in draftImages" :key="`${url}-${index}`" class="draft-image">
          <el-image :src="url" fit="cover">
            <template #error>
              <div class="image-error">加载失败</div>
            </template>
          </el-image>
          <button class="draft-remove" title="移除图片" @click="removeDraftImage(index)">
            <el-icon :size="12"><Close /></el-icon>
          </button>
        </div>
      </div>

      <div class="image-input-row">
        <el-input
          v-model="imageUrl"
          placeholder="粘贴图片链接（http/https）"
          clearable
          @keyup.enter="addImage"
        />
        <el-button :disabled="draftImages.length >= MAX_IMAGES" @click="addImage">
          添加图片
        </el-button>
      </div>

      <div class="compose-meta">
        <span>{{ draftImages.length }}/{{ MAX_IMAGES }} 张图片</span>
        <span>图片仅支持 http/https 外链</span>
      </div>

      <template #footer>
        <el-button @click="composeVisible = false">取消</el-button>
        <el-button
          class="publish-btn"
          type="primary"
          :loading="publishing"
          @click="handlePublish"
        >
          发布
        </el-button>
      </template>
    </el-dialog>

    <!-- 评论弹窗 -->
    <el-dialog
      v-model="commentVisible"
      width="520px"
      align-center
      append-to-body
      class="comment-dialog"
      :close-on-click-modal="false"
    >
      <template #header>
        <div class="dialog-title">
          <span class="logo-dot"></span>
          评论<span v-if="commentMoment && Number(commentMoment.commentCount) > 0" class="comment-title-count">
            {{ commentMoment.commentCount }}
          </span>
        </div>
      </template>

      <div v-loading="commentLoading" class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-item">
          <span class="avatar comment-avatar">{{ initial(comment.userName || comment.userId) }}</span>
          <div class="comment-main">
            <div class="comment-meta">
              <span class="comment-author">{{ comment.userName || comment.userId }}</span>
              <span class="comment-time">{{ relativeTime(comment.createTime) }}</span>
            </div>
            <p class="comment-text">{{ comment.content }}</p>
          </div>
        </div>
        <p v-if="!commentLoading && !comments.length" class="comment-empty">
          还没有评论，来说两句吧
        </p>
        <button
          v-if="comments.length < commentTotal"
          class="comment-more"
          :disabled="commentLoading"
          @click="loadCommentPage(false)"
        >
          加载更多
        </button>
      </div>

      <div class="comment-compose">
        <el-input
          v-model="commentContent"
          type="textarea"
          :rows="2"
          :maxlength="MAX_COMMENT"
          show-word-limit
          resize="none"
          :placeholder="isClientLoggedIn ? '写下你的评论…' : '登录后参与评论'"
          :disabled="!isClientLoggedIn"
          @keydown.enter.exact.prevent="submitComment"
        />
        <el-button
          class="comment-submit"
          type="primary"
          :loading="commentSubmitting"
          @click="submitComment"
        >
          {{ isClientLoggedIn ? "发送" : "登录" }}
        </el-button>
      </div>
    </el-dialog>

    <!-- 热门推荐新闻详情 -->
    <NewsDetailDialog v-model="newsDetailVisible" :news="currentNews" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { ElMessage } from "element-plus";
import { Close, Delete, EditPen } from "@element-plus/icons-vue";
import {
  publishMoment,
  getMomentPage,
  deleteMoment,
  toggleMomentLike,
  getMomentComments,
  addMomentComment,
  getNews,
  getTeamPage,
  getMySocial,
  joinCommunity,
  leaveCommunity,
  followUser,
  unfollowUser,
} from "@/api/portal";
import { useClientAuth } from "@/composables/useClientAuth";
import NewsDetailDialog from "@/components/NewsDetailDialog.vue";

const PAGE_SIZE = 10;
const COMMENT_PAGE_SIZE = 10;
const MAX_IMAGES = 9;
const MAX_CONTENT = 1000;
const MAX_COMMENT = 500;

interface MomentItem {
  id: string | number;
  content: string;
  images?: string[];
  userId: string;
  userName: string;
  createTime: string;
  teamId?: number;
  likeCount?: number;
  commentCount?: number;
  liked?: boolean;
}

interface CommentItem {
  id: string | number;
  momentId: string | number;
  userId: string;
  userName: string;
  content: string;
  createTime: string;
}

interface PortalTeam {
  teamId: number;
  name: string;
  nbaDotComTeamId?: number;
}

interface SocialUser {
  userId: string;
  userName: string;
}

const teamLogo = (team: PortalTeam) =>
  team?.nbaDotComTeamId
    ? `https://cdn.nba.com/logos/nba/${team.nbaDotComTeamId}/global/L/logo.svg`
    : "";

const { clientUsername, isClientLoggedIn, openLoginDialog } = useClientAuth();

const moments = ref<MomentItem[]>([]);
const total = ref(0);
const currentPage = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const publishing = ref(false);

const composeVisible = ref(false);
const content = ref("");
const imageUrl = ref("");
const draftImages = ref<string[]>([]);
const pendingCompose = ref(false);
const likeUpdatingId = ref<string | number | null>(null);

const commentVisible = ref(false);
const commentMoment = ref<MomentItem | null>(null);
const comments = ref<CommentItem[]>([]);
const commentTotal = ref(0);
const commentPageNum = ref(0);
const commentLoading = ref(false);
const commentContent = ref("");
const commentSubmitting = ref(false);

const teams = ref<PortalTeam[]>([]);
const teamsLoading = ref(false);
const myCommunityIds = ref<number[]>([]);
const communityUpdating = ref<number | null>(null);
const pendingJoinTeamId = ref<number | null>(null);
const activeCommunity = ref<PortalTeam | null>(null);

const following = ref<SocialUser[]>([]);
const followersCount = ref(0);
const socialUpdatingId = ref<string | null>(null);
const pendingFollowUser = ref<SocialUser | null>(null);

const hotNews = ref<any[]>([]);
const newsLoading = ref(false);
const newsDetailVisible = ref(false);
const currentNews = ref<any>(null);

const hasMore = computed(() => moments.value.length < total.value);
const avatarText = computed(() => initial(clientUsername.value));

function initial(name?: string | null) {
  return (name || "U").charAt(0).toUpperCase();
}

/** 服务端时间形如 "yyyy-MM-dd HH:mm:ss"，替换空格以兼容 Safari 解析 */
function relativeTime(time: string) {
  if (!time) return "";
  const date = new Date(time.replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return time;
  const diff = Date.now() - date.getTime();
  if (diff < 60_000) return "刚刚";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`;
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`;
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

function newsTime(iso: string) {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function imageList(moment: MomentItem) {
  return moment.images || [];
}

function isMine(moment: MomentItem) {
  return !!clientUsername.value && moment.userId === clientUsername.value;
}

/** 追加页做 id 去重：发布新动态后旧页边界会整体后移，直接拼接会重复 */
function appendMoments(batch: MomentItem[]) {
  const seen = new Set(moments.value.map((item) => String(item.id)));
  moments.value = [...moments.value, ...batch.filter((item) => !seen.has(String(item.id)))];
}

async function loadPage(reset: boolean) {
  if (reset) loading.value = true;
  else loadingMore.value = true;
  const nextPage = reset ? 1 : currentPage.value + 1;
  try {
    const res = await getMomentPage({
      currentPage: nextPage,
      pageSize: PAGE_SIZE,
      teamId: activeCommunity.value ? Number(activeCommunity.value.teamId) : 0,
      viewerId: clientUsername.value || undefined,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "加载动态失败");
      return;
    }
    const batch: MomentItem[] = res.data?.moments || [];
    total.value = Number(res.data?.total || 0);
    if (reset) {
      moments.value = batch;
    } else if (batch.length) {
      appendMoments(batch);
    } else {
      total.value = moments.value.length;
    }
    currentPage.value = nextPage;
  } catch (error) {
    console.error("加载动态失败:", error);
    ElMessage.error("加载动态失败，请稍后重试");
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

function handlePublishEntry() {
  if (!isClientLoggedIn.value) {
    pendingCompose.value = true;
    openLoginDialog();
    return;
  }
  openCompose();
}

function openCompose() {
  composeVisible.value = true;
}

function addImage() {
  const url = imageUrl.value.trim();
  if (!url) return;
  if (draftImages.value.length >= MAX_IMAGES) {
    ElMessage.warning(`最多添加 ${MAX_IMAGES} 张图片`);
    return;
  }
  if (!/^https?:\/\//i.test(url)) {
    ElMessage.warning("图片链接需以 http:// 或 https:// 开头");
    return;
  }
  try {
    new URL(url);
  } catch {
    ElMessage.warning("图片链接格式不正确");
    return;
  }
  if (draftImages.value.includes(url)) {
    ElMessage.warning("该图片已添加");
    return;
  }
  draftImages.value.push(url);
  imageUrl.value = "";
}

function removeDraftImage(index: number) {
  draftImages.value.splice(index, 1);
}

async function handlePublish() {
  if (!isClientLoggedIn.value) {
    composeVisible.value = false;
    pendingCompose.value = true;
    openLoginDialog();
    return;
  }
  const text = content.value.trim();
  if (!text && !draftImages.value.length) {
    ElMessage.warning("请输入内容或添加图片");
    return;
  }
  publishing.value = true;
  try {
    const teamId = activeCommunity.value ? Number(activeCommunity.value.teamId) : 0;
    const res = await publishMoment({ content: text, images: [...draftImages.value], teamId });
    if (res.code !== 200 || !res.data?.moment) {
      ElMessage.error(res.message || "发布失败");
      return;
    }
    moments.value = [res.data.moment, ...moments.value];
    total.value += 1;
    content.value = "";
    draftImages.value = [];
    imageUrl.value = "";
    composeVisible.value = false;
    ElMessage.success(teamId ? "已发布到社区" : "发布成功");
  } catch (error) {
    console.error("发布动态失败:", error);
    ElMessage.error("发布失败，请稍后重试");
  } finally {
    publishing.value = false;
  }
}

async function handleDelete(moment: MomentItem) {
  try {
    const res = await deleteMoment({ momentId: Number(moment.id) });
    if (res.code !== 200) {
      ElMessage.error(res.message || "删除失败");
      return;
    }
    moments.value = moments.value.filter((item) => item.id !== moment.id);
    total.value = Math.max(0, total.value - 1);
    ElMessage.success("已删除");
  } catch (error) {
    console.error("删除动态失败:", error);
    ElMessage.error("删除失败，请稍后重试");
  }
}

async function handleLike(moment: MomentItem) {
  if (!isClientLoggedIn.value) {
    openLoginDialog();
    return;
  }
  if (likeUpdatingId.value !== null) return;
  likeUpdatingId.value = moment.id;
  const previousLiked = !!moment.liked;
  const previousCount = Number(moment.likeCount || 0);
  moment.liked = !previousLiked;
  moment.likeCount = Math.max(0, previousCount + (moment.liked ? 1 : -1));
  try {
    const res = await toggleMomentLike({ momentId: Number(moment.id) });
    if (res.code !== 200) {
      ElMessage.error(res.message || "操作失败");
      moment.liked = previousLiked;
      moment.likeCount = previousCount;
      return;
    }
    moment.liked = !!res.data?.liked;
    moment.likeCount = Number(res.data?.likeCount || 0);
  } catch (error) {
    console.error("点赞操作失败:", error);
    moment.liked = previousLiked;
    moment.likeCount = previousCount;
    ElMessage.error("操作失败，请稍后重试");
  } finally {
    likeUpdatingId.value = null;
  }
}

async function openComments(moment: MomentItem) {
  commentMoment.value = moment;
  comments.value = [];
  commentTotal.value = Number(moment.commentCount || 0);
  commentPageNum.value = 0;
  commentContent.value = "";
  commentVisible.value = true;
  await loadCommentPage(true);
}

async function loadCommentPage(reset: boolean) {
  const moment = commentMoment.value;
  if (!moment || commentLoading.value) return;
  commentLoading.value = true;
  const nextPage = reset ? 1 : commentPageNum.value + 1;
  try {
    const res = await getMomentComments({
      momentId: Number(moment.id),
      currentPage: nextPage,
      pageSize: COMMENT_PAGE_SIZE,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "加载评论失败");
      return;
    }
    const batch: CommentItem[] = res.data?.comments || [];
    commentTotal.value = Number(res.data?.total || 0);
    if (reset) {
      comments.value = batch;
    } else {
      const seen = new Set(comments.value.map((item) => String(item.id)));
      comments.value = [...comments.value, ...batch.filter((item) => !seen.has(String(item.id)))];
    }
    commentPageNum.value = nextPage;
  } catch (error) {
    console.error("加载评论失败:", error);
    ElMessage.error("加载评论失败，请稍后重试");
  } finally {
    commentLoading.value = false;
  }
}

async function submitComment() {
  const moment = commentMoment.value;
  if (!moment) return;
  if (!isClientLoggedIn.value) {
    openLoginDialog();
    return;
  }
  const text = commentContent.value.trim();
  if (!text) {
    ElMessage.warning("请输入评论内容");
    return;
  }
  if (commentSubmitting.value) return;
  commentSubmitting.value = true;
  try {
    const res = await addMomentComment({ momentId: Number(moment.id), content: text });
    if (res.code !== 200 || !res.data?.comment) {
      ElMessage.error(res.message || "评论失败");
      return;
    }
    comments.value = [res.data.comment, ...comments.value];
    commentTotal.value = Number(res.data.commentCount || commentTotal.value + 1);
    commentContent.value = "";
    moment.commentCount = commentTotal.value;
    const target = moments.value.find((item) => String(item.id) === String(moment.id));
    if (target) target.commentCount = commentTotal.value;
    ElMessage.success("评论成功");
  } catch (error) {
    console.error("发表评论失败:", error);
    ElMessage.error("评论失败，请稍后重试");
  } finally {
    commentSubmitting.value = false;
  }
}

async function shareMoment(moment: MomentItem) {
  const url = window.location.href;
  const title = moment.userName ? `${moment.userName}的 NBA 动态` : "NBA 球迷动态";
  const text = moment.content?.trim() || "来看看这条 NBA 动态";
  try {
    if (typeof navigator.share === "function") {
      await navigator.share({ title, text, url });
      return;
    }
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(`${text} ${url}`);
      ElMessage.success("链接已复制");
      return;
    }
    ElMessage.info(url);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") return;
    ElMessage.error("分享失败，请稍后重试");
  }
}

function isActiveCommunity(teamId: number | string) {
  return Number(activeCommunity.value?.teamId) === Number(teamId);
}

function openCommunity(team: PortalTeam) {
  if (isActiveCommunity(team.teamId)) return;
  activeCommunity.value = team;
  moments.value = [];
  currentPage.value = 0;
  total.value = 0;
  loadPage(true);
}

function exitCommunity() {
  if (!activeCommunity.value) return;
  activeCommunity.value = null;
  moments.value = [];
  currentPage.value = 0;
  total.value = 0;
  loadPage(true);
}

function communityLabel(teamId?: number) {
  const id = Number(teamId || 0);
  if (!id) return "";
  const team = teams.value.find((item) => Number(item.teamId) === id);
  return team ? `${team.name}社区` : "";
}

function openCommunityById(teamId?: number) {
  const id = Number(teamId || 0);
  const team = teams.value.find((item) => Number(item.teamId) === id);
  if (team) openCommunity(team);
}

async function loadTeams() {
  teamsLoading.value = true;
  try {
    const res = await getTeamPage({ currentPage: 1, pageSize: 50 });
    if (res.code === 200) {
      teams.value = res.data?.teams || [];
    }
  } catch (error) {
    console.error("加载球队社区失败:", error);
  } finally {
    teamsLoading.value = false;
  }
}

function isCommunityJoined(teamId: number | string) {
  return myCommunityIds.value.includes(Number(teamId));
}

function isFollowing(userId: string) {
  return following.value.some((user) => user.userId === userId);
}

function resetSocial() {
  myCommunityIds.value = [];
  following.value = [];
  followersCount.value = 0;
}

async function loadSocial() {
  if (!isClientLoggedIn.value) {
    resetSocial();
    return;
  }
  try {
    const res = await getMySocial();
    if (res.code === 200) {
      myCommunityIds.value = (res.data?.communityTeamIds || []).map(Number);
      following.value = res.data?.following || [];
      followersCount.value = (res.data?.followers || []).length;
    }
  } catch (error) {
    console.error("加载社交数据失败:", error);
  }
}

async function updateCommunity(team: PortalTeam, join: boolean) {
  const teamId = Number(team.teamId);
  if (communityUpdating.value !== null) return;
  communityUpdating.value = teamId;
  try {
    const res = join ? await joinCommunity({ teamId }) : await leaveCommunity({ teamId });
    if (res.code !== 200) {
      ElMessage.error(res.message || "操作失败");
      return;
    }
    myCommunityIds.value = join
      ? Array.from(new Set([...myCommunityIds.value, teamId]))
      : myCommunityIds.value.filter((id) => id !== teamId);
    ElMessage.success(join ? `已加入${team.name}社区` : `已退出${team.name}社区`);
  } catch (error) {
    console.error("社区操作失败:", error);
    ElMessage.error("操作失败，请稍后重试");
  } finally {
    communityUpdating.value = null;
  }
}

async function toggleCommunity(team: PortalTeam) {
  if (!isClientLoggedIn.value) {
    pendingJoinTeamId.value = Number(team.teamId);
    ElMessage.warning("加入社区需要登录");
    openLoginDialog();
    return;
  }
  await updateCommunity(team, !isCommunityJoined(team.teamId));
}

async function joinPendingTeam() {
  const teamId = pendingJoinTeamId.value;
  pendingJoinTeamId.value = null;
  if (teamId === null || isCommunityJoined(teamId)) return;
  const team = teams.value.find((item) => Number(item.teamId) === teamId);
  if (team) {
    await updateCommunity(team, true);
  }
}

async function toggleUserFollow(userId: string, userName: string) {
  if (!userId || userId === clientUsername.value) return;
  if (!isClientLoggedIn.value) {
    pendingFollowUser.value = { userId, userName };
    ElMessage.warning("关注用户需要登录");
    openLoginDialog();
    return;
  }
  if (socialUpdatingId.value) return;
  socialUpdatingId.value = userId;
  const currentlyFollowing = isFollowing(userId);
  try {
    const res = currentlyFollowing
      ? await unfollowUser({ targetUserId: userId })
      : await followUser({ targetUserId: userId, targetUserName: userName || userId });
    if (res.code !== 200) {
      ElMessage.error(res.message || "操作失败");
      return;
    }
    following.value = currentlyFollowing
      ? following.value.filter((user) => user.userId !== userId)
      : [{ userId, userName: userName || userId }, ...following.value];
    ElMessage.success(currentlyFollowing ? "已取消关注" : "已关注");
  } catch (error) {
    console.error("关注操作失败:", error);
    ElMessage.error("操作失败，请稍后重试");
  } finally {
    socialUpdatingId.value = null;
  }
}

// 登录成功后恢复社交数据并接续未完成的发布/加入社区/关注操作
watch(isClientLoggedIn, async (logged) => {
  if (!logged) {
    resetSocial();
    pendingJoinTeamId.value = null;
    pendingFollowUser.value = null;
    commentVisible.value = false;
    await loadPage(true);
    return;
  }
  await loadSocial();
  await loadPage(true);
  if (pendingCompose.value) {
    pendingCompose.value = false;
    openCompose();
  }
  if (pendingJoinTeamId.value !== null) {
    await joinPendingTeam();
  }
  if (pendingFollowUser.value) {
    const target = pendingFollowUser.value;
    pendingFollowUser.value = null;
    if (!isFollowing(target.userId)) {
      await toggleUserFollow(target.userId, target.userName);
    }
  }
});

async function loadHotNews() {
  newsLoading.value = true;
  try {
    const res = await getNews({ limit: 5 });
    if (res.code === 200) {
      hotNews.value = (res.data?.news || []).slice(0, 5);
    }
  } catch (error) {
    console.error("加载热门推荐失败:", error);
  } finally {
    newsLoading.value = false;
  }
}

function openNews(news: any) {
  currentNews.value = news;
  newsDetailVisible.value = true;
}

function onImgError(e: Event) {
  (e.target as HTMLImageElement).style.visibility = "hidden";
}

onMounted(() => {
  loadPage(true);
  loadHotNews();
  loadTeams();
  if (isClientLoggedIn.value) {
    loadSocial();
  }
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

.moment-count {
  font-size: 12px;
  color: #94a3b8;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  padding: 3px 10px;
}

/* 三栏布局 */
.moments-layout {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr) 190px;
  grid-template-areas: "widgets feed compose";
  gap: 18px;
  align-items: start;
}

/* 左侧：关注 / 社区 / 推荐 */
.left-rail {
  grid-area: widgets;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.user-card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 18px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
}

.avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316, #ea580c);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  max-width: 100%;
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-hint {
  font-size: 11.5px;
  color: #64748b;
}

.publish-entry {
  width: 100%;
  height: 42px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #f97316, #ea580c);
  font-size: 14px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 8px 20px rgba(249, 115, 22, 0.25);
}

.publish-entry:hover {
  background: linear-gradient(135deg, #fb923c, #f97316);
}

.rail-tip {
  margin: 0;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1.6;
  color: #64748b;
}

/* 中间动态流 */
.moments-feed {
  grid-area: feed;
  min-width: 0;
}

.community-view {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid rgba(249, 115, 22, 0.28);
  background: linear-gradient(135deg, rgba(249, 115, 22, 0.14), rgba(30, 41, 59, 0.8) 55%);
}

.community-view-logo {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  object-fit: contain;
}

.community-view-main {
  flex: 1;
  min-width: 0;
}

.community-view-title {
  font-size: 17px;
  font-weight: 800;
  color: #f1f5f9;
}

.community-view-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}

.community-view-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.community-view-publish {
  border: none;
  border-radius: 12px;
  padding: 7px 12px;
  background: linear-gradient(135deg, #f97316, #ea580c);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.community-view-publish:hover {
  background: linear-gradient(135deg, #fb923c, #f97316);
}

.community-view-exit {
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  padding: 6px 11px;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.community-view-exit:hover {
  color: #fb923c;
  border-color: rgba(249, 115, 22, 0.45);
}

.moment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 120px;
}

.moment-card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 16px 18px;
  transition: border-color 0.2s ease;
}

.moment-card:hover {
  border-color: rgba(249, 115, 22, 0.35);
}

.moment-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.moment-head .avatar {
  width: 40px;
  height: 40px;
  font-size: 15px;
}

.moment-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.moment-author {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.moment-time {
  font-size: 12px;
  color: #64748b;
}

.moment-sub {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.moment-community {
  border: 1px solid rgba(249, 115, 22, 0.35);
  border-radius: 10px;
  padding: 1px 7px;
  background: rgba(249, 115, 22, 0.1);
  color: #fb923c;
  font-size: 11px;
  line-height: 1.5;
  cursor: pointer;
  transition: all 0.2s;
}

.moment-community:hover {
  background: rgba(249, 115, 22, 0.2);
}

.delete-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.delete-btn:hover {
  color: #f87171;
  background: rgba(248, 113, 113, 0.12);
}

.moment-content {
  margin: 12px 0 0;
  font-size: 14.5px;
  line-height: 1.7;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
}

.moment-images {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-top: 12px;
}

.moment-images.single {
  grid-template-columns: minmax(0, 300px);
}

.moment-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  background: #0f172a;
  cursor: zoom-in;
}

.moment-images.single .moment-image {
  aspect-ratio: 4 / 3;
}

.moment-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.moment-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 34px;
  height: 28px;
  padding: 0 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}

.moment-action:hover:not(:disabled) {
  color: #fb923c;
  background: rgba(249, 115, 22, 0.1);
}

.moment-action.active {
  color: #fb923c;
}

.moment-action:disabled {
  opacity: 0.6;
  cursor: default;
}

.action-count {
  font-variant-numeric: tabular-nums;
}

.image-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.moment-image .image-error {
  color: #64748b;
  background: #0f172a;
}

.load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0 4px;
}

.load-more :deep(.el-button) {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
}

.load-more :deep(.el-button:hover:not(.is-disabled)) {
  border-color: rgba(249, 115, 22, 0.6);
  color: #fb923c;
  background: rgba(249, 115, 22, 0.12);
}

.load-end {
  font-size: 12.5px;
  color: #64748b;
  letter-spacing: 1px;
}

/* 右侧：发布入口 */
.right-rail {
  grid-area: compose;
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-card {
  background: rgba(30, 41, 59, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px 16px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.side-head h3 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: #e2e8f0;
}

.side-head h3::before {
  content: "";
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: linear-gradient(180deg, #fb923c, #ea580c);
}

.follow-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.follow-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 4px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
}

.follow-value {
  font-size: 18px;
  font-weight: 800;
  line-height: 1.2;
  color: #fb923c;
  font-variant-numeric: tabular-nums;
}

.follow-label {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}

.side-count {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 1px 7px;
}

.side-more {
  font-size: 11.5px;
  color: #94a3b8;
  text-decoration: none;
  white-space: nowrap;
}

.side-more:hover {
  color: #fb923c;
}

.side-empty {
  margin: 0;
  padding: 4px 0 2px;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.side-reset {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  font-size: 11.5px;
}

.community-list.scrollable {
  max-height: 330px;
  overflow-y: auto;
  padding-right: 2px;
}

.community-list.scrollable::-webkit-scrollbar {
  width: 4px;
}

.community-list.scrollable::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.3);
  border-radius: 2px;
}

.team-logo {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  object-fit: contain;
}

.community-list {
  display: flex;
  flex-direction: column;
}

.community-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.community-row:hover {
  background: rgba(255, 255, 255, 0.05);
}

.community-row.active {
  background: rgba(249, 115, 22, 0.12);
  box-shadow: inset 0 0 0 1px rgba(249, 115, 22, 0.35);
}

.community-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: #cbd5e1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-toggle {
  flex-shrink: 0;
  border: 1px solid rgba(249, 115, 22, 0.5);
  background: rgba(249, 115, 22, 0.08);
  color: #fb923c;
  font-size: 11px;
  line-height: 1;
  padding: 5px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.follow-toggle:hover {
  background: rgba(249, 115, 22, 0.18);
}

.follow-toggle.followed {
  border-color: rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: #94a3b8;
}

.follow-toggle:disabled {
  opacity: 0.6;
  cursor: default;
}

.author-follow {
  flex-shrink: 0;
  padding: 6px 10px;
  border: 1px solid rgba(249, 115, 22, 0.45);
  border-radius: 12px;
  background: rgba(249, 115, 22, 0.08);
  color: #fb923c;
  font-size: 11.5px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s;
}

.author-follow:hover:not(:disabled) {
  background: rgba(249, 115, 22, 0.18);
}

.author-follow.followed {
  border-color: rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: #94a3b8;
}

.author-follow:disabled {
  opacity: 0.6;
  cursor: default;
}

.hot-list {
  min-height: 60px;
}

.hot-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  width: 100%;
  padding: 7px 2px;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.hot-item:last-child {
  border-bottom: none;
}

.hot-item:hover .hot-title {
  color: #fb923c;
}

.hot-index {
  flex-shrink: 0;
  width: 16px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  line-height: 1.5;
}

.hot-index.top {
  color: #f97316;
}

.hot-body {
  display: block;
  min-width: 0;
}

.hot-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12.5px;
  line-height: 1.5;
  color: #cbd5e1;
  transition: color 0.2s;
}

.hot-time {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}

/* 发布弹窗 */
.dialog-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.logo-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316, #ea580c);
  box-shadow: 0 0 8px rgba(249, 115, 22, 0.7);
}

.compose-community {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.compose-community-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #64748b;
}

.compose-team {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 8px;
  border: 1px solid rgba(249, 115, 22, 0.3);
  background: rgba(249, 115, 22, 0.08);
  color: #ea580c;
  font-size: 13px;
  font-weight: 600;
}

.compose-team img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

/* 评论弹窗 */
.comment-title-count {
  margin-left: 2px;
  padding: 1px 7px;
  border-radius: 10px;
  background: rgba(249, 115, 22, 0.1);
  color: #ea580c;
  font-size: 12px;
  font-weight: 600;
}

.comment-list {
  max-height: 380px;
  min-height: 120px;
  overflow-y: auto;
  padding-right: 4px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  font-size: 12px;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-author {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.comment-time {
  font-size: 11px;
  color: #94a3b8;
}

.comment-text {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-empty {
  margin: 0;
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}

.comment-more {
  display: block;
  margin: 10px auto 0;
  border: none;
  background: transparent;
  color: #ea580c;
  font-size: 12px;
  cursor: pointer;
}

.comment-compose {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.comment-compose :deep(.el-textarea) {
  flex: 1;
}

.comment-submit {
  flex-shrink: 0;
}

.compose-input :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.65;
}

.draft-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.draft-image {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.draft-image :deep(.el-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.draft-image .image-error {
  color: #94a3b8;
  background: #f8fafc;
}

.draft-remove {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.75);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
}

.draft-remove:hover {
  background: rgba(239, 68, 68, 0.9);
}

.image-input-row {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.image-input-row :deep(.el-button) {
  flex-shrink: 0;
}

.compose-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: #94a3b8;
}

.publish-btn {
  background: linear-gradient(135deg, #f97316, #ea580c);
  border: none;
}

.publish-btn:hover {
  background: linear-gradient(135deg, #fb923c, #f97316);
}

/* 响应式 */
@media (max-width: 1080px) {
  .moments-layout {
    grid-template-columns: minmax(0, 1fr) 170px;
    grid-template-areas:
      "feed compose"
      "widgets widgets";
  }

  .left-rail {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }

  .follow-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .moments-layout {
    grid-template-columns: 1fr;
    grid-template-areas:
      "compose"
      "feed"
      "widgets";
  }

  .right-rail {
    position: static;
    flex-direction: row;
    align-items: center;
  }

  .user-card {
    flex: 1;
    flex-direction: row;
    text-align: left;
    padding: 10px 14px;
    gap: 10px;
  }

  .user-hint,
  .rail-tip {
    display: none;
  }

  .publish-entry {
    width: auto;
    min-width: 132px;
    flex-shrink: 0;
  }

  .left-rail {
    grid-template-columns: 1fr;
  }

  .community-view {
    flex-wrap: wrap;
    gap: 10px;
  }

  .community-view-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 700px) {
  .image-input-row {
    flex-direction: column;
  }

  .moment-images {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
