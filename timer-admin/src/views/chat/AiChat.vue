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
          <!-- 好友请求Tab -->
          <el-tab-pane name="requests">
            <template #label>
              <span>好友请求</span>
              <el-badge
                v-if="pendingRequestCount > 0"
                :value="pendingRequestCount"
                :max="99"
                class="tab-badge"
              />
            </template>
            <el-scrollbar style="height: 420px">
              <div v-if="friendRequests.length === 0" class="empty-tip">
                暂无好友请求
              </div>
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
                    <div class="request-username">{{ request.fromUsername }}</div>
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
                <el-tag v-else-if="request.status === 'accepted'" type="success" size="small">
                  已添加
                </el-tag>
              </div>
            </el-scrollbar>
          </el-tab-pane>

          <!-- 聊天列表Tab -->
          <el-tab-pane label="在线用户" name="chat">
            <el-scrollbar
              ref="chatListScrollbar"
              style="height: 420px"
              @scroll="handleChatListScroll"
            >
              <div
                v-for="user in onlineUsers"
                :key="user.userId"
                :class="[
                  'user-item',
                  { active: selectedUser?.userId === user.userId },
                ]"
                @click="selectUser(user)"
              >
                <el-avatar size="small" :src="user.avatar">{{
                  user.username?.charAt(0)
                }}</el-avatar>
                <span class="username">{{ user.username }}</span>
                <el-badge
                  is-dot
                  :hidden="user.isActive !== '1'"
                  class="status-badge"
                />
              </div>
              <div v-if="loadingMore" class="loading-text">加载中...</div>
              <div
                v-else-if="!hasMore && onlineUsers.length > 0"
                class="loading-text"
              >
                没有更多用户了
              </div>
            </el-scrollbar>
          </el-tab-pane>

          <!-- 好友列表Tab -->
          <el-tab-pane label="我的好友" name="friends">
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
              <div
                v-for="friend in friendList"
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
              <div v-if="friendList.length === 0" class="loading-text">
                暂无好友
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
import { ref, onMounted, onBeforeUnmount, nextTick, watch, computed } from "vue";
import { ChatDotRound, Plus } from "@element-plus/icons-vue";
import useWebSocket from "@/composables/useWebSocket";
import { ElNotification, ElMessage } from "element-plus";
import { getUserActivePage } from "@/api/user";
import AddFriendModal from "./AddFriendModal.vue";

const WS_URL = "/websocket/im-server/ws";

const {
  messages: wsMessages,
  isConnected,
  send: sendWsMessage,
  close: closeWsConnection,
  connect: connectWs,
} = useWebSocket(WS_URL);

// 类型定义
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
}

// 状态响应式变量
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

const showAddFriendDialog = ref(false);
const addingFriend = ref(false);
const friendRequests = ref<FriendRequest[]>([]);
const acceptingRequestId = ref<number | null>(null);

const messagesContainer = ref<HTMLDivElement | null>(null);
const robotButton = ref<HTMLDivElement | null>(null);

// 拖拽状态
const robotPosition = ref({ right: 20, bottom: 20 });
const isDragging = ref(false);
const hasMoved = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

// 分页状态
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

// 🌟 核心修复：使用 Map 记录已处理的消息指纹和时间戳，用于自动清理防内存泄漏
const processedMsgMap = new Map<string, number>();
let cleanUpTimer: any = null;

// 🌟 生成强哈希指纹（彻底解决接收方收到两条重复消息的问题）
const generateMsgFingerprint = (payload: any): string => {
  // 优先使用后端返回的唯一ID
  if (payload.msgId) {
    return `id_${payload.msgId}`;
  }
  // 🌟 致命修复：绝对不能使用 Date.now()！
  // 使用 发送者 + 内容 + 秒级时间戳。这样即使后端1秒内推送了两次，指纹也完全一样。
  const secondTimestamp = Math.floor(Date.now() / 1000);
  return `hash_${payload.from}_${payload.content}_${secondTimestamp}`;
};

// 核心业务方法
const selectUser = (user: User) => {
  selectedUser.value = user;
  const cached = messageCache.value.get(user.userId);
  chatMessages.value = cached ? [...cached] : [];
  nextTick(scrollToBottom);
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

// 消息发送逻辑
const sendMessage = () => {
  if (!userMessage.value.trim() || !isConnected.value || !selectedUser.value)
    return;
  const content = userMessage.value;
  const msgId = `local_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
  // 本地先渲染自己发的消息，并加入防重 Map
  const userMsg: ChatMessage = { id: msgId, type: "user", content };
  chatMessages.value.push(userMsg);
  processedMsgMap.set(msgId, Date.now());

  // 🌟 同时缓存发送的消息
  if (!messageCache.value.has(selectedUser.value.userId)) {
    messageCache.value.set(selectedUser.value.userId, []);
  }
  messageCache.value.get(selectedUser.value.userId)!.push(userMsg);
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
// ... existing code ...

// 同意好友请求
const acceptFriendRequest = (requestId: number) => {
  acceptingRequestId.value = requestId;
  try {
    sendWsMessage("friend", {
      action: "accept",
      requestId: requestId,
    });
    // 更新本地状态
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

// 监听 WebSocket 消息 (按增量处理)
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
        else if (topic === "friend") handleFriendMessage(payload);
      }
    }
  },
  { immediate: true },
);

const handleChatMessage = (payload: any) => {
  // 1. 拦截自己发送的消息（防止发送方展示两条）
  if (payload.from === currentUserId.value) {
    return;
  }
  // 2. 🌟 核心防重拦截（防止接收方展示两条）
  const fingerprint = generateMsgFingerprint(payload);
  if (processedMsgMap.has(fingerprint)) {
    console.warn("拦截到重复消息:", fingerprint);
    return; // 直接丢弃
  }
  processedMsgMap.set(fingerprint, Date.now());
  const targetUserId = payload.from;
  const targetUser =
    onlineUsers.value.find((u) => u.userId === targetUserId) ||
    friendList.value.find((f) => f.userId === targetUserId);
  // 3. 处理未读消息和通知
  if (!showRobotDialog.value || selectedUser.value?.userId !== payload.from) {
    unreadCount.value++;
    // 🌟 记录最后发消息的用户
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
  // 4. 🌟 核心修复：所有消息都缓存到对应用户
  const newMessage: ChatMessage = {
    id: fingerprint,
    type: "ai",
    content: payload.content,
  };

  if (!messageCache.value.has(targetUserId)) {
    messageCache.value.set(targetUserId, []);
  }
  messageCache.value.get(targetUserId)!.push(newMessage);
  // 如果当前正在和该用户聊天，同时显示
  if (selectedUser.value && selectedUser.value.userId === targetUserId) {
    chatMessages.value.push(newMessage);
    scrollToBottom();
  }

  // 5. 将发消息的人顶到列表最前面
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
  if (payload.action === "request") {
    // 收到好友请求
    const request: FriendRequest = {
      requestId: payload.requestId,
      from: payload.from,
      fromUsername: payload.fromUsername,
      fromAvatar: payload.fromAvatar,
      msg: payload.msg,
      status: "pending",
    };
    friendRequests.value.push(request);
    // 显示通知
    ElNotification({
      title: "好友请求",
      message: `${payload.fromUsername} 请求添加你为好友：${payload.msg}`,
      type: "info",
      duration: 5000,
      position: "bottom-right",
    });
  } else if (payload.action === "accept_response") {
    // 收到同意/拒绝好友请求的响应
    if (payload.success) {
      ElMessage.success("已添加为好友");
      if (payload.friend) {
        friendList.value.push(payload.friend);
      }
    } else {
      ElMessage.error(payload.message || "操作失败");
    }
  }
};

// 数据加载与 UI 辅助
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

// 拖拽逻辑
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
      // 🌟 打开对话框时，如果有最后发消息的用户且当前没有选中用户，自动选中
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

const handleDialogClose = () => {};

// 生命周期与监听
onMounted(() => {
  // 缓存当前用户ID
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
  // 🌟 显式调用连接（useWebSocket 内部有锁，绝对安全）
  connectWs();
  loadUserList(1);
  // 定时清理防重 Map，防止内存泄漏
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
/* 全局布局 */
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

/* 用户列表项 */
.user-item {
  display: flex;
  align-items: center;
  padding: 10px;
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

/* 好友请求项 */
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

/* 聊天区域 */
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

/* 悬浮按钮 */
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