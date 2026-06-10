<template>
  <!-- 可拖拽的机器人按钮 -->
  <div
    ref="robotButton"
    class="draggable-robot-button"
    :style="{
      right: robotPosition.right + 'px',
      bottom: robotPosition.bottom + 'px',
    }"
    @mousedown="startDrag"
    @click="toggleRobotDialog"
  >
    <div v-if="unreadCount > 0" class="unread-badge">
      {{ unreadCount > 99 ? "99+" : unreadCount }}
    </div>
    <el-icon :size="28"><ChatDotRound /></el-icon>
  </div>

  <!-- 机器人对话框 -->
  <el-dialog
    v-model="showRobotDialog"
    title="消息中心"
    width="800px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="handleDialogClose"
  >
    <div class="chat-container">
      <!-- 左侧Tab区域 -->
      <div class="left-panel">
        <el-tabs
          v-model="activeTab"
          type="border-card"
          class="full-height-tabs"
        >
          <!-- 聊天记录Tab -->
          <el-tab-pane label="聊天记录" name="chat">
            <el-scrollbar style="height: 460px">
              <div v-if="chatHistoryList.length === 0" class="empty-tip">
                暂无聊天记录
              </div>
              <div
                v-for="item in chatHistoryList"
                :key="item.userId"
                :class="[
                  'user-item',
                  { active: selectedUser?.userId === item.userId },
                ]"
                @click="selectChatHistoryUser(item.userId)"
              >
                <el-avatar size="small" :src="item.avatar">{{
                  item.username?.charAt(0)
                }}</el-avatar>
                <div class="friend-info">
                  <div>{{ item.username }}</div>
                  <div class="remark">{{ item.lastMessage }}</div>
                </div>
                <el-badge
                  :value="item.unreadCount"
                  :hidden="item.unreadCount === 0"
                  :max="99"
                  class="status-badge"
                />
              </div>
            </el-scrollbar>
          </el-tab-pane>

          <!-- 好友Tab -->
          <el-tab-pane name="friends">
            <template #label>
              <span>好友</span>
              <el-badge
                v-if="pendingRequestCount > 0"
                :value="pendingRequestCount"
                :max="99"
                class="tab-badge"
              />
            </template>
            <div style="padding: 10px">
              <el-button
                type="primary"
                size="small"
                style="width: 100%"
                @click="showAddFriendDialog = true"
              >
                <el-icon><Plus /></el-icon> 添加好友
              </el-button>
            </div>
            <el-scrollbar ref="friendListScrollbar" style="height: 380px">
              <!-- 好友请求折叠按钮 -->
              <div
                class="request-toggle"
                @click="showRequestList = !showRequestList"
              >
                <div class="toggle-left">
                  <el-icon
                    class="toggle-arrow"
                    :class="{ expanded: showRequestList }"
                  >
                    <ArrowRight />
                  </el-icon>
                  <span>已请求好友</span>
                  <el-badge
                    v-if="pendingRequestCount > 0"
                    :value="pendingRequestCount"
                    :max="99"
                    class="toggle-badge"
                  />
                </div>
                <span class="toggle-count">共 {{ friendRequests.length }} 条</span>
              </div>

              <!-- 好友请求列表（可折叠） -->
              <div v-show="showRequestList" class="request-section">
                <div
                  v-for="request in friendRequests"
                  :key="request.requestId"
                  class="request-item"
                >
                  <div class="request-info">
                    <el-avatar size="small" :src="request.fromAvatar">
                      {{ request.fromUsername?.charAt(0) }}
                    </el-avatar>
                    <div class="request-detail">
                      <div class="request-username">
                        {{ request.fromUsername }}
                      </div>
                      <div class="request-msg">{{ request.msg }}</div>
                    </div>
                  </div>
                  <el-button
                    v-if="request.status === 'pending'"
                    type="primary"
                    size="small"
                    :loading="acceptingRequestId === request.requestId"
                    @click="acceptFriendRequest(request.requestId)"
                  >
                    同意
                  </el-button>
                  <el-tag
                    v-else-if="request.status === 'accepted'"
                    type="success"
                    size="small"
                  >
                    已添加
                  </el-tag>
                  <el-tag
                    v-else-if="request.status === 'rejected'"
                    type="info"
                    size="small"
                  >
                    已拒绝
                  </el-tag>
                </div>

                <div
                  v-if="requestHasMore"
                  class="load-more"
                  @click="loadMoreFriendRequests"
                >
                  <span v-if="requestLoadingMore">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    加载中...
                  </span>
                  <span v-else>加载更多</span>
                </div>
                <div v-if="friendRequests.length === 0" class="loading-text">
                  暂无好友请求
                </div>
              </div>

              <!-- 已添加好友列表（按字母分组 + 右侧字母索引） -->
              <div class="friend-section">
                <div class="section-title">
                  已添加好友 ({{ friendList.length }})
                </div>

                <div v-if="friendList.length === 0" class="loading-text">
                  暂无好友
                </div>

                <div v-else class="friend-index-layout">
                  <!-- 分组好友列表 -->
                  <div class="friend-groups">
                    <div
                      v-for="group in groupedFriends"
                      :key="group.letter"
                      :id="`friend-group-${group.letter}`"
                      class="group-block"
                    >
                      <div class="group-letter">{{ group.letter }}</div>
                      <div
                        v-for="friend in group.items"
                        :key="friend.userId"
                        :class="[
                          'user-item',
                          { active: selectedUser?.userId === friend.userId },
                        ]"
                        @click="selectUser(friend)"
                      >
                        <el-avatar size="small" :src="friend.avatar">{{
                          friend.username?.charAt(0)
                        }}</el-avatar>
                        <div class="friend-info">
                          <div>{{ friend.username }}</div>
                          <div v-if="friend.remark" class="remark">
                            {{ friend.remark }}
                          </div>
                        </div>
                        <el-badge
                          is-dot
                          :hidden="friend.isActive !== '1'"
                          class="status-badge"
                        />
                      </div>
                    </div>
                  </div>

                  <!-- 右侧字母索引栏 -->
                  <div
                    class="letter-index-bar"
                    @touchstart.prevent="handleLetterTouchStart"
                    @touchmove.prevent="handleLetterTouchMove"
                    @touchend="activeLetter = ''"
                  >
                    <div
                      v-for="letter in alphabetIndex"
                      :key="letter"
                      class="letter-index-item"
                      :class="{ active: letter === activeLetter }"
                      @click="scrollToLetter(letter)"
                      @mouseenter="activeLetter = letter"
                      @mouseleave="activeLetter = ''"
                    >
                      {{ letter }}
                    </div>
                  </div>
                </div>
              </div>
            </el-scrollbar>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 右侧聊天框 -->
      <div class="right-panel">
        <div class="chat-header">
          <span v-if="selectedUser"
            >与 <b>{{ selectedUser.username }}</b> 聊天中</span
          >
          <span v-else style="color: #909399">请选择一个用户开始聊天</span>
          <el-tag v-if="isConnected" type="success" size="small" effect="plain"
            >已连接</el-tag
          >
          <el-tag v-else type="danger" size="small" effect="plain"
            >连接中...</el-tag
          >
        </div>

        <div ref="messagesContainer" class="messages-area">
          <div
            v-for="(msg, index) in chatMessages"
            :key="msg.id || index"
            class="message-row"
            :class="msg.type"
          >
            <div v-if="msg.type === 'user'" class="msg-content user-msg">
              <span class="msg-text">{{ msg.content }}</span>
              <el-avatar size="small" style="background: #409eff">我</el-avatar>
            </div>
            <div v-else class="msg-content ai-msg">
              <el-avatar size="small" :src="selectedUser?.avatar">{{
                selectedUser?.username?.charAt(0) || "AI"
              }}</el-avatar>
              <span class="msg-text">{{ msg.content }}</span>
            </div>
          </div>
        </div>

        <div class="input-area">
          <el-input
            v-model="userMessage"
            placeholder="请输入消息..."
            @keyup.enter="sendMessage"
            :disabled="!isConnected || !selectedUser"
          />
          <el-button
            type="primary"
            @click="sendMessage"
            :disabled="!isConnected || !selectedUser"
            >发送</el-button
          >
        </div>
      </div>
    </div>
  </el-dialog>

  <!-- 添加好友对话框 -->
  <AddFriendModal
    v-model="showAddFriendDialog"
    :friend-list="friendList"
    @add-friend="handleAddFriend"
  />
</template>

<script setup lang="ts">
import {
  ref,
  onMounted,
  onBeforeUnmount,
  nextTick,
  watch,
  computed,
} from "vue";
import { ChatDotRound, Plus, ArrowRight, Loading } from "@element-plus/icons-vue";
import useWebSocket from "@/composables/useWebSocket";
import { ElNotification, ElMessage } from "element-plus";
import { getUserActivePage } from "@/api/user";
import { getFriendRequestPage, getFriendPage } from "@/api/im";
import AddFriendModal from "./AddFriendModal.vue";

const WS_URL = "/websocket/im-server/ws";

const {
  messages: wsMessages,
  isConnected,
  send: sendWsMessage,
  close: closeWsConnection,
  connect: connectWs,
} = useWebSocket(WS_URL);

interface User {
  userId: string;
  username: string;
  avatar?: string;
  isActive: string;
}

interface Friend extends User {
  remark?: string;
}

interface ChatMessage {
  id?: string;
  type: "user" | "ai";
  content: string;
}

interface FriendRequest {
  requestId: number;
  from: string;
  fromUsername: string;
  fromAvatar?: string;
  msg: string;
  status?: "pending" | "accepted" | "rejected";
  createTime?: string;
}

const onlineUsers = ref<User[]>([]);
const friendList = ref<Friend[]>([]);
const selectedUser = ref<User | null>(null);
const activeTab = ref("chat");

const showRobotDialog = ref(false);
const userMessage = ref("");
const chatMessages = ref<ChatMessage[]>([]);
const unreadCount = ref(0);
const messageCache = ref<Map<string, ChatMessage[]>>(new Map());
const lastMessageUser = ref<User | null>(null);
const userUnreadCounts = ref<Map<string, number>>(new Map());
const chatHistoryUserIds = ref<string[]>([]);

const showAddFriendDialog = ref(false);
const addingFriend = ref(false);
const friendRequests = ref<FriendRequest[]>([]);
const acceptingRequestId = ref<number | null>(null);
const showRequestList = ref(false);

const requestLastId = ref("");
const requestLastCreateTime = ref("");
const requestHasMore = ref(true);
const requestLoadingMore = ref(false);

const activeLetter = ref("");

const messagesContainer = ref<HTMLDivElement | null>(null);
const robotButton = ref<HTMLDivElement | null>(null);

const robotPosition = ref({ right: 20, bottom: 20 });
const isDragging = ref(false);
const hasMoved = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

const currentPage = ref(1);
const pageSize = ref(15);
const total = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);

const isSubscribed = ref(false);
const currentUserId = ref("");

const pendingRequestCount = computed(() => {
  return friendRequests.value.filter((r) => r.status === "pending").length;
});

const chatHistoryList = computed(() => {
  return chatHistoryUserIds.value
    .map((userId) => {
      const msgs = messageCache.value.get(userId);
      if (!msgs || msgs.length === 0) return null;
      const lastMsg = msgs[msgs.length - 1];
      const user =
        onlineUsers.value.find((u) => u.userId === userId) ||
        friendList.value.find((f) => f.userId === userId);
      return {
        userId,
        username: user?.username || "未知用户",
        avatar: user?.avatar,
        lastMessage: lastMsg.content,
        unreadCount: userUnreadCounts.value.get(userId) || 0,
      };
    })
    .filter(Boolean) as Array<{
    userId: string;
    username: string;
    avatar?: string;
    lastMessage: string;
    unreadCount: number;
  }>;
});

const groupedFriends = computed(() => {
  const sorted = [...friendList.value].sort((a, b) =>
    (a.username || "").localeCompare(b.username || "", "zh-CN"),
  );

  const groups: Map<string, Friend[]> = new Map();

  for (const friend of sorted) {
    const firstChar = (friend.username || "#").charAt(0).toUpperCase();
    const letter = /[A-Z]/.test(firstChar) ? firstChar : "#";
    if (!groups.has(letter)) {
      groups.set(letter, []);
    }
    groups.get(letter)!.push(friend);
  }

  const result = Array.from(groups.entries()).map(([letter, items]) => ({
    letter,
    items,
  }));

  result.sort((a, b) => {
    if (a.letter === "#") return 1;
    if (b.letter === "#") return -1;
    return a.letter.localeCompare(b.letter);
  });

  return result;
});

const alphabetIndex = computed(() => {
  return groupedFriends.value.map((g) => g.letter);
});

const scrollToLetter = (letter: string) => {
  activeLetter.value = letter;
  const el = document.getElementById(`friend-group-${letter}`);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  setTimeout(() => {
    activeLetter.value = "";
  }, 800);
};

const handleLetterTouchStart = (e: TouchEvent) => {
  const touch = e.touches[0];
  updateLetterFromTouch(touch);
};

const handleLetterTouchMove = (e: TouchEvent) => {
  const touch = e.touches[0];
  updateLetterFromTouch(touch);
};

const updateLetterFromTouch = (touch: Touch) => {
  const target = document.elementFromPoint(touch.clientX, touch.clientY);
  if (target) {
    const letter = (target as HTMLElement).textContent?.trim();
    if (letter && alphabetIndex.value.includes(letter)) {
      scrollToLetter(letter);
    }
  }
};

const processedMsgMap = new Map<string, number>();
let cleanUpTimer: any = null;

const generateMsgFingerprint = (payload: any): string => {
  if (payload.msgId) {
    return `id_${payload.msgId}`;
  }
  const secondTimestamp = Math.floor(Date.now() / 1000);
  return `hash_${payload.from}_${payload.content}_${secondTimestamp}`;
};

const selectUser = (user: User) => {
  selectedUser.value = user;
  userUnreadCounts.value.set(user.userId, 0);
  const cached = messageCache.value.get(user.userId);
  chatMessages.value = cached ? [...cached] : [];
  nextTick(scrollToBottom);
};

const selectChatHistoryUser = (userId: string) => {
  userUnreadCounts.value.set(userId, 0);
  const user =
    onlineUsers.value.find((u) => u.userId === userId) ||
    friendList.value.find((f) => f.userId === userId);
  if (user) {
    selectUser(user);
  }
};

const updateUserStatus = (userId: string, isActive: string) => {
  const updateList = (list: any[]) => {
    const idx = list.findIndex((u) => u.userId === userId);
    if (idx !== -1) {
      list[idx].isActive = isActive;
    }
  };
  updateList(onlineUsers.value);
  updateList(friendList.value);
};

const batchUpdateUserStatus = (
  statusList: Array<{ userId: string; isActive: string }>,
) => {
  const statusMap = new Map(
    statusList.map((item) => [item.userId, item.isActive]),
  );
  onlineUsers.value.forEach((u) => {
    if (statusMap.has(u.userId)) u.isActive = statusMap.get(u.userId)!;
  });
  friendList.value.forEach((f) => {
    if (statusMap.has(f.userId)) f.isActive = statusMap.get(f.userId)!;
  });
};

const subscribeUserStatus = () => {
  if (!isConnected.value || isSubscribed.value) return;
  const targetUserIds = [
    ...onlineUsers.value.map((u) => u.userId),
    ...friendList.value.map((f) => f.userId),
  ];
  if (targetUserIds.length === 0) return;
  sendWsMessage("status", {
    action: "subscribe",
    targetUserIds: targetUserIds,
  });
};

const sendMessage = () => {
  if (!userMessage.value.trim() || !isConnected.value || !selectedUser.value)
    return;
  const content = userMessage.value;
  const msgId = `local_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
  const userMsg: ChatMessage = { id: msgId, type: "user", content };
  chatMessages.value.push(userMsg);
  processedMsgMap.set(msgId, Date.now());

  if (!messageCache.value.has(selectedUser.value.userId)) {
    messageCache.value.set(selectedUser.value.userId, []);
  }
  messageCache.value.get(selectedUser.value.userId)!.push(userMsg);

  if (!chatHistoryUserIds.value.includes(selectedUser.value.userId)) {
    chatHistoryUserIds.value.unshift(selectedUser.value.userId);
  }

  scrollToBottom();
  sendWsMessage("chat", {
    msgId: msgId,
    to: selectedUser.value.userId,
    content: content,
  });
  userMessage.value = "";
};

const handleAddFriend = (formData: { userId: string; remark: string }) => {
  addingFriend.value = true;
  try {
    sendWsMessage("friend", {
      action: "request",
      to: formData.userId,
      msg: formData.remark,
    });
    ElMessage.success("好友请求已发送");
    showAddFriendDialog.value = false;
  } catch (error) {
    ElMessage.error("发送失败");
  } finally {
    addingFriend.value = false;
  }
};

const acceptFriendRequest = (requestId: number) => {
  acceptingRequestId.value = requestId;
  try {
    sendWsMessage("friend", {
      action: "accept",
      requestId: requestId,
    });
    const request = friendRequests.value.find((r) => r.requestId === requestId);
    if (request) {
      request.status = "accepted";
    }
  } catch (error) {
    console.error("同意好友请求失败:", error);
    ElMessage.error("操作失败");
  } finally {
    acceptingRequestId.value = null;
  }
};

const loadFriendRequests = async (append = false) => {
  try {
    if (append) {
      requestLoadingMore.value = true;
    }
    const response = await getFriendRequestPage({
      pageSize: 20,
      lastId: append ? requestLastId.value : "",
      lastCreateTime: append ? requestLastCreateTime.value : "",
    });
    if (response.code === 200) {
      const list =
        response.data?.records || response.data?.list || response.data || [];
      const items = Array.isArray(list) ? list : [];

      if (append) {
        friendRequests.value = [...friendRequests.value, ...items];
      } else {
        friendRequests.value = items;
      }

      if (items.length > 0) {
        const lastItem = items[items.length - 1];
        requestLastId.value = lastItem.requestId || lastItem.id || "";
        requestLastCreateTime.value = lastItem.createTime || "";
        requestHasMore.value = items.length >= 20;
      } else {
        requestHasMore.value = false;
      }
    }
  } catch (error) {
    console.error("获取好友请求列表失败:", error);
  } finally {
    requestLoadingMore.value = false;
  }
};

const loadMoreFriendRequests = () => {
  if (!requestLoadingMore.value && requestHasMore.value) {
    loadFriendRequests(true);
  }
};

const loadFriendList = async () => {
  try {
    const response = await getFriendPage();
    if (response.code === 200) {
      const list =
        response.data?.records || response.data?.list || response.data || [];
      friendList.value = Array.isArray(list) ? list : [];
    }
  } catch (error) {
    console.error("获取好友列表失败:", error);
  }
};

watch(
  () => wsMessages.value.length,
  (newLen, oldLen) => {
    const prevLen = oldLen ?? 0;
    if (newLen > prevLen) {
      for (let i = prevLen; i < newLen; i++) {
        const envelope = wsMessages.value[i];
        if (!envelope) continue;

        const { topic, payload } = envelope;
        if (topic === "chat") handleChatMessage(payload);
        else if (topic === "status") handleStatusMessage(payload);
        else if (topic === "notification") handleFriendMessage(payload);
      }
    }
  },
  { immediate: true },
);

const handleChatMessage = (payload: any) => {
  if (payload.from === currentUserId.value) {
    return;
  }
  const fingerprint = generateMsgFingerprint(payload);
  if (processedMsgMap.has(fingerprint)) {
    console.warn("拦截到重复消息:", fingerprint);
    return;
  }
  processedMsgMap.set(fingerprint, Date.now());
  const targetUserId = payload.from;
  const targetUser =
    onlineUsers.value.find((u) => u.userId === targetUserId) ||
    friendList.value.find((f) => f.userId === targetUserId);
  if (!showRobotDialog.value || selectedUser.value?.userId !== payload.from) {
    unreadCount.value++;
    userUnreadCounts.value.set(
      targetUserId,
      (userUnreadCounts.value.get(targetUserId) || 0) + 1,
    );
    if (targetUser && !showRobotDialog.value) {
      lastMessageUser.value = targetUser;
    }
    ElNotification({
      title: targetUser?.username || "新消息",
      message: payload.content,
      type: "info",
      duration: 3000,
      position: "top-right",
    });
  }
  const newMessage: ChatMessage = {
    id: fingerprint,
    type: "ai",
    content: payload.content,
  };

  if (!messageCache.value.has(targetUserId)) {
    messageCache.value.set(targetUserId, []);
  }
  messageCache.value.get(targetUserId)!.push(newMessage);

  if (!chatHistoryUserIds.value.includes(targetUserId)) {
    chatHistoryUserIds.value.unshift(targetUserId);
  }

  if (selectedUser.value && selectedUser.value.userId === targetUserId) {
    chatMessages.value.push(newMessage);
    scrollToBottom();
  }

  if (targetUser) {
    const idx = onlineUsers.value.findIndex(
      (u) => u.userId === targetUser.userId,
    );
    if (idx > 0) {
      onlineUsers.value.splice(idx, 1);
      onlineUsers.value.unshift(targetUser);
    }
  }
};

const handleStatusMessage = (payload: any) => {
  if (payload.action === "update" && payload.userId) {
    updateUserStatus(payload.userId, payload.isActive);
  } else if (
    payload.action === "batch_update" &&
    Array.isArray(payload.statusList)
  ) {
    batchUpdateUserStatus(payload.statusList);
  } else if (payload.action === "subscribe_success") {
    isSubscribed.value = true;
  }
};

const handleFriendMessage = (payload: any) => {
  if (payload.action === "friend_request") {
    const request: FriendRequest = {
      requestId: payload.requestId,
      from: payload.from,
      fromUsername: payload.fromUsername,
      fromAvatar: payload.fromAvatar,
      msg: payload.msg,
      status: "pending",
      createTime: payload.createTime || new Date().toISOString(),
    };
    friendRequests.value.unshift(request);
    ElNotification({
      title: "好友请求",
      message: `${payload.fromUsername || payload.from} 请求添加你为好友`,
      type: "info",
      duration: 5000,
      position: "top-right",
    });
  } else if (payload.action === "accept_response") {
    if (payload.success) {
      ElMessage.success("已添加为好友");
      if (payload.friend) {
        const exists = friendList.value.some(
          (f) => f.userId === payload.friend.userId,
        );
        if (!exists) {
          friendList.value.unshift(payload.friend);
        }
      }
    } else {
      ElMessage.error(payload.message || "操作失败");
    }
  }
};

const loadUserList = async (page: number) => {
  try {
    if (page === 1) {
      loading.value = true;
    }
    const response = await getUserActivePage({
      currentPage: page,
      pageSize: pageSize.value,
    });
    if (response.code === 200) {
      const users = response.data.users || [];
      if (page === 1) onlineUsers.value = users;
      else onlineUsers.value = [...onlineUsers.value, ...users];

      total.value = response.data.total || 0;
      hasMore.value = onlineUsers.value.length < total.value;

      if (page === 1) nextTick(subscribeUserStatus);
    }
  } catch (error) {
    ElMessage.error("获取用户数据失败");
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const loadMoreUsers = () => {
  if (!loadingMore.value && hasMore.value) {
    loadingMore.value = true;
    currentPage.value++;
    loadUserList(currentPage.value);
  }
};

const handleChatListScroll = (event: any) => {
  const { scrollTop, clientHeight, scrollHeight } = event.target;
  if (scrollTop + clientHeight >= scrollHeight - 20) loadMoreUsers();
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const startDrag = (e: MouseEvent) => {
  e.preventDefault();
  isDragging.value = true;
  hasMoved.value = false;
  if (robotButton.value) {
    const rect = robotButton.value.getBoundingClientRect();
    dragOffset.value = { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }
  document.addEventListener("mousemove", handleDrag);
  document.addEventListener("mouseup", stopDrag);
};

const handleDrag = (e: MouseEvent) => {
  if (!isDragging.value) {
    return;
  }
  hasMoved.value = true;
  const newRight = window.innerWidth - e.clientX - (60 - dragOffset.value.x);
  const newBottom = window.innerHeight - e.clientY - (60 - dragOffset.value.y);
  robotPosition.value.right = Math.max(
    0,
    Math.min(newRight, window.innerWidth - 60),
  );
  robotPosition.value.bottom = Math.max(
    0,
    Math.min(newBottom, window.innerHeight - 60),
  );
};

const stopDrag = () => {
  isDragging.value = false;
  document.removeEventListener("mousemove", handleDrag);
  document.removeEventListener("mouseup", stopDrag);
  localStorage.setItem("robotPosition", JSON.stringify(robotPosition.value));
};

const toggleRobotDialog = () => {
  if (!hasMoved.value) {
    showRobotDialog.value = !showRobotDialog.value;
    if (showRobotDialog.value) {
      unreadCount.value = 0;
      userUnreadCounts.value.clear();
      requestLastId.value = "";
      requestLastCreateTime.value = "";
      requestHasMore.value = true;
      loadFriendRequests();
      loadFriendList();
      if (lastMessageUser.value && !selectedUser.value) {
        selectUser(lastMessageUser.value);
      } else if (selectedUser.value) {
        const cached = messageCache.value.get(selectedUser.value.userId);
        chatMessages.value = cached ? [...cached] : [];
        nextTick(scrollToBottom);
      }
    }
  }
};

const handleDialogClose = () => {
  selectedUser.value = null;
  chatMessages.value = [];
};

onMounted(() => {
  try {
    currentUserId.value =
      JSON.parse(localStorage.getItem("user") || "{}").userId || "";
  } catch (e) {
    currentUserId.value = "";
  }
  const savedPosition = localStorage.getItem("robotPosition");
  if (savedPosition) {
    try {
      robotPosition.value = JSON.parse(savedPosition);
    } catch (e) {}
  }
  connectWs();
  loadUserList(1);
  cleanUpTimer = setInterval(() => {
    const now = Date.now();
    for (const [key, time] of processedMsgMap.entries()) {
      if (now - time > 5000) {
        processedMsgMap.delete(key);
      }
    }
  }, 5000);
});

watch(isConnected, (connected) => {
  if (connected) {
    isSubscribed.value = false;
    nextTick(subscribeUserStatus);
  }
});

onBeforeUnmount(() => {
  document.removeEventListener("mousemove", handleDrag);
  document.removeEventListener("mouseup", stopDrag);
  closeWsConnection();
  if (cleanUpTimer) clearInterval(cleanUpTimer);
});
</script>

<style scoped>
.chat-container {
  height: 500px;
  display: flex;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.left-panel {
  width: 220px;
  background: #fafafa;
}

.full-height-tabs {
  height: 100%;
}
.full-height-tabs :deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow: hidden;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s;
}
.user-item:hover {
  background: #f5f7fa;
}
.user-item.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
}
.username {
  margin-left: 10px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.friend-info {
  margin-left: 10px;
  flex: 1;
  overflow: hidden;
}
.friend-info .remark {
  font-size: 12px;
  color: #909399;
}
.status-badge {
  margin-left: auto;
}
.tab-badge {
  margin-left: 6px;
}
.toggle-badge {
  margin-left: 6px;
}

.request-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  cursor: pointer;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  transition: background 0.2s;
  user-select: none;
}
.request-toggle:hover {
  background: #f0f2f5;
}
.toggle-left {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.toggle-arrow {
  transition: transform 0.25s;
  font-size: 12px;
  color: #909399;
}
.toggle-arrow.expanded {
  transform: rotate(90deg);
}
.toggle-count {
  font-size: 12px;
  color: #c0c4cc;
}

.request-section {
  border-bottom: 1px solid #ebeef5;
}
.request-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.request-item:last-child {
  border-bottom: none;
}
.request-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}
.request-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.request-username {
  font-weight: 500;
  font-size: 14px;
}
.request-msg {
  font-size: 12px;
  color: #909399;
}

.load-more {
  text-align: center;
  padding: 12px;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
  transition: background 0.2s;
  border-top: 1px solid #f0f0f0;
}
.load-more:hover {
  background: #ecf5ff;
}

.friend-section {
  margin-top: 0;
}
.section-title {
  padding: 8px 12px;
  font-size: 12px;
  color: #909399;
  font-weight: 500;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
}

/* 字母索引布局 */
.friend-index-layout {
  position: relative;
}

.friend-groups {
  padding-right: 24px;
}

.group-block {
  /* 分组容器 */
}
.group-letter {
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 600;
  color: #909399;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  line-height: 20px;
}

/* 右侧字母索引栏 */
.letter-index-bar {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  z-index: 10;
  padding: 4px 0;
}
.letter-index-item {
  width: 18px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #409eff;
  cursor: pointer;
  border-radius: 2px;
  user-select: none;
  transition: all 0.15s;
}
.letter-index-item:hover,
.letter-index-item.active {
  background: #409eff;
  color: #fff;
  font-weight: 600;
  transform: scale(1.2);
}

.empty-tip {
  text-align: center;
  padding: 40px;
  color: #909399;
  font-size: 14px;
}
.loading-text {
  text-align: center;
  padding: 15px;
  color: #909399;
  font-size: 12px;
}

.chat-header {
  padding: 12px 15px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
  background: #f5f7fa;
}

.message-row {
  margin-bottom: 15px;
}
.msg-content {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  max-width: 80%;
}
.user-msg {
  margin-left: auto;
  flex-direction: row-reverse;
}
.msg-text {
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.5;
  word-break: break-word;
}
.user-msg .msg-text {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 0;
}
.ai-msg .msg-text {
  background: #fff;
  color: #333;
  border-top-left-radius: 0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.input-area {
  padding: 15px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 10px;
}

.draggable-robot-button {
  position: fixed;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #6a89f7);
  box-shadow: 0 4px 15px rgba(64, 158, 255, 0.4);
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  color: #fff;
  transition: transform 0.2s;
  user-select: none;
}
.draggable-robot-button:hover {
  transform: scale(1.05);
}
.draggable-robot-button:active {
  cursor: grabbing;
}

.unread-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background: #f56c6c;
  color: #fff;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 12px;
  min-width: 18px;
  text-align: center;
  line-height: 14px;
  box-shadow: 0 2px 6px rgba(245, 108, 108, 0.5);
}
</style>