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

  <!-- 消息中心对话框 -->
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
                :class="['user-item', { active: isActiveChat(item.userId) }]"
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

          <!-- 通讯录Tab -->
          <el-tab-pane name="friends">
            <template #label>
              <span>通讯录</span>
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
                <span class="toggle-count"
                  >共 {{ friendRequests.length }} 条</span
                >
              </div>

              <!-- 好友请求列表（可折叠） -->
              <div v-show="showRequestList" class="request-section">
                <div
                  v-for="request in friendRequests"
                  :key="request.requestId || request.friendId"
                  class="request-item"
                >
                  <div class="request-info">
                    <el-avatar size="small" :src="request.friendAvatar">
                      {{ request.friendName?.charAt(0) }}
                    </el-avatar>
                    <div class="request-detail">
                      <div class="request-username">
                        {{ request.friendName }}
                      </div>
                      <div class="request-msg">{{ request.remark }}</div>
                    </div>
                  </div>
                  <el-button
                    v-if="request.status === '待处理'"
                    type="primary"
                    size="small"
                    :loading="acceptingRequestId === request.requestId"
                    @click="acceptFriendRequest(request.requestId)"
                  >
                    同意
                  </el-button>
                  <el-tag
                    v-else-if="request.status === '已同意'"
                    type="success"
                    size="small"
                  >
                    已添加
                  </el-tag>
                  <el-tag
                    v-else-if="request.status === '已拒绝'"
                    type="info"
                    size="small"
                  >
                    已拒绝
                  </el-tag>
                  <el-tag
                    v-else-if="request.status === '等待中'"
                    type="warning"
                    size="small"
                  >
                    等待中
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
                        :key="friend.friendId"
                        :class="[
                          'user-item',
                          {
                            active: selectedUser?.friendId === friend.friendId,
                          },
                        ]"
                        @click="selectUser(friend)"
                      >
                        <el-avatar size="small" :src="friend.friendAvatar">{{
                          friend.friendName?.charAt(0)
                        }}</el-avatar>
                        <div class="friend-info">
                          <div>{{ friend.friendName }}</div>
                          <div v-if="friend.remark" class="remark">
                            {{ friend.remark }}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

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

              <!-- 群组列表 -->
              <div class="friend-section" style="margin-top: 12px">
                <div
                  style="
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 8px 12px;
                    background: #fafafa;
                    border-bottom: 1px solid #ebeef5;
                  "
                >
                  <span
                    style="font-size: 12px; color: #909399; font-weight: 500"
                  >
                    群组 ({{ groupList.length }})
                  </span>
                  <el-button
                    size="small"
                    text
                    type="primary"
                    @click="openCreateGroupDialog"
                  >
                    <el-icon><Plus /></el-icon> 创建
                  </el-button>
                </div>
                <div v-if="groupList.length === 0" class="loading-text">
                  暂无群组
                </div>
                <div
                  v-for="group in groupList"
                  :key="group.groupId"
                  :class="[
                    'user-item',
                    { active: selectedGroup?.groupId === group.groupId },
                  ]"
                  @click="selectGroup(group)"
                >
                  <el-avatar size="small" :src="group.avatar">
                    {{ group.groupName?.charAt(0) }}
                  </el-avatar>
                  <div class="friend-info">
                    <div>{{ group.groupName }}</div>
                    <div class="remark">
                      {{ group.announcement || group.description || "" }}
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
          <span v-if="selectedGroup">
            群聊 <b>{{ selectedGroup.groupName }}</b>
            <span style="color: #909399; font-size: 12px">
              ({{ groupMembers.length }}人)
            </span>
          </span>
          <span v-else-if="selectedUser"
            >与 <b>{{ selectedUser.friendName }}</b> 聊天中</span
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
              <el-avatar size="small" style="background: #409eff">我</el-avatar>
              <span class="msg-text">{{ msg.content }}</span>
            </div>
            <div v-else class="msg-content ai-msg">
              <el-avatar
                size="small"
                :src="
                  msg.fromUserId
                    ? (groupMembers.find(m => m.userId === msg.fromUserId)?.avatar || selectedGroup?.avatar)
                    : (selectedGroup?.avatar || selectedUser?.friendAvatar)
                "
              >{{
                msg.fromUserId
                  ? (groupMembers.find(m => m.userId === msg.fromUserId)?.username?.charAt(0) || '?')
                  : (selectedGroup?.groupName?.charAt(0) || selectedUser?.friendName?.charAt(0) || 'AI')
              }}</el-avatar>
              <span class="msg-text">{{ msg.content }}</span>
            </div>
          </div>
        </div>

        <!-- 群操作栏 -->
        <div v-if="selectedGroup" class="group-toolbar">
          <el-button
            size="small"
            @click="showGroupMemberPanel = !showGroupMemberPanel"
          >
            <el-icon><UserFilled /></el-icon> 成员
          </el-button>
          <el-button
            size="small"
            @click="showGroupAnnounceInput = !showGroupAnnounceInput"
          >
            公告
          </el-button>
          <el-button size="small" @click="openInviteDialog">
            <el-icon><Plus /></el-icon> 邀请
          </el-button>
          <el-button
            v-if="!isGroupOwner"
            size="small"
            type="warning"
            @click="handleLeaveGroup"
          >
            退出
          </el-button>
          <el-button
            v-if="isGroupOwner"
            size="small"
            type="danger"
            @click="handleDismissGroup"
          >
            解散
          </el-button>
        </div>

        <!-- 群公告编辑 -->
        <div
          v-if="selectedGroup && showGroupAnnounceInput"
          class="announce-input-area"
        >
          <el-input
            v-model="groupAnnounceText"
            :placeholder="selectedGroup.announcement || '输入群公告...'"
            size="small"
            @keyup.enter="handleAnnounce"
          />
          <el-button size="small" type="primary" @click="handleAnnounce"
            >发布</el-button
          >
        </div>

        <!-- 群成员面板 -->
        <div
          v-if="selectedGroup && showGroupMemberPanel"
          class="group-member-panel"
        >
          <div class="panel-title">群成员 ({{ groupMembers.length }})</div>
          <el-scrollbar max-height="150px">
            <div
              v-for="member in groupMembers"
              :key="member.userId"
              class="member-item"
            >
              <el-avatar size="small" :src="member.avatar">
                {{ member.username?.charAt(0) }}
              </el-avatar>
              <span class="member-name">{{ member.username }}</span>
              <el-tag
                v-if="member.role === 3"
                type="danger"
                size="small"
                effect="plain"
              >群主</el-tag>
              <el-tag
                v-else-if="member.role === 2"
                type="warning"
                size="small"
                effect="plain"
              >管理员</el-tag>
              <el-button
                v-if="
                  isGroupAdmin &&
                  member.userId !== currentUserId &&
                  member.role <
                    (groupMembers.find((m) => m.userId === currentUserId)
                      ?.role || 0)
                "
                size="small"
                type="danger"
                text
                @click="handleKickMember(member.userId, member.username)"
              >
                踢出
              </el-button>
            </div>
          </el-scrollbar>
        </div>

        <div class="input-area">
          <el-input
            v-model="userMessage"
            placeholder="请输入消息..."
            @keyup.enter="sendMessage"
            :disabled="!isConnected || (!selectedUser && !selectedGroup)"
          />
          <el-button
            type="primary"
            @click="sendMessage"
            :disabled="!isConnected || (!selectedUser && !selectedGroup)"
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

  <!-- 创建群组对话框 -->
  <el-dialog
    v-model="showCreateGroupDialog"
    title="创建群组"
    width="420px"
    :close-on-click-modal="false"
  >
    <el-input
      v-model="groupNameInput"
      placeholder="请输入群名称"
      style="margin-bottom: 12px"
    />
    <div class="select-friends-title">
      选择好友
      <span style="color: #909399; font-size: 12px"
        >(已选 {{ selectedFriendIds.size }} 人)</span
      >
    </div>
    <el-scrollbar max-height="300px">
      <div
        v-for="friend in friendList"
        :key="friend.friendId"
        class="select-friend-item"
        :class="{ selected: selectedFriendIds.has(friend.friendId) }"
        @click="toggleFriendSelect(friend.friendId)"
      >
        <el-checkbox :model-value="selectedFriendIds.has(friend.friendId)" />
        <el-avatar size="small" :src="friend.friendAvatar">
          {{ friend.friendName?.charAt(0) }}
        </el-avatar>
        <span>{{ friend.friendName }}</span>
      </div>
      <div v-if="friendList.length === 0" class="loading-text">
        暂无好友可选
      </div>
    </el-scrollbar>
    <template #footer>
      <el-button @click="showCreateGroupDialog = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!groupNameInput.trim() || selectedFriendIds.size === 0"
        :loading="creatingGroup"
        @click="handleCreateGroup"
      >
        创建
      </el-button>
    </template>
  </el-dialog>

  <!-- 邀请入群对话框 -->
  <el-dialog
    v-model="showInviteDialog"
    title="邀请好友入群"
    width="420px"
    :close-on-click-modal="false"
  >
    <div class="select-friends-title">
      选择好友
      <span style="color: #909399; font-size: 12px"
        >(已选 {{ inviteSelectedIds.size }} 人)</span
      >
    </div>
    <el-scrollbar max-height="300px">
      <div
        v-for="friend in availableInviteFriends"
        :key="friend.friendId"
        class="select-friend-item"
        :class="{ selected: inviteSelectedIds.has(friend.friendId) }"
        @click="toggleInviteSelect(friend.friendId)"
      >
        <el-checkbox :model-value="inviteSelectedIds.has(friend.friendId)" />
        <el-avatar size="small" :src="friend.friendAvatar">
          {{ friend.friendName?.charAt(0) }}
        </el-avatar>
        <span>{{ friend.friendName }}</span>
      </div>
      <div v-if="availableInviteFriends.length === 0" class="loading-text">
        暂无可邀请的好友
      </div>
    </el-scrollbar>
    <template #footer>
      <el-button @click="showInviteDialog = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="inviteSelectedIds.size === 0"
        :loading="invitingMembers"
        @click="handleInviteMembers"
      >
        邀请
      </el-button>
    </template>
  </el-dialog>
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
import {
  ChatDotRound,
  Plus,
  ArrowRight,
  Loading,
  UserFilled,
} from "@element-plus/icons-vue";
import useWebSocket from "@/composables/useWebSocket";
import { ElNotification, ElMessage, ElMessageBox } from "element-plus";
import {
  getFriendRequestPage,
  getFriendPage,
  getMyGroups,
  getGroupMembers,
} from "@/api/im";
import { useUserStore } from "@/store/user";
import AddFriendModal from "./AddFriendModal.vue";

const WS_URL = "/websocket/im-server/ws";
const userStore = useUserStore();

const {
  messages: wsMessages,
  isConnected,
  send: sendWsMessage,
  close: closeWsConnection,
  connect: connectWs,
} = useWebSocket(WS_URL);

interface Friend {
  requestId: number;
  friendId: string;
  friendName: string;
  friendAvatar?: string;
  status: string;
  remark?: string;
  updateTime?: string;
}

interface ChatMessage {
  id?: string;
  type: "user" | "ai";
  content: string;
  fromUserId?: string;
}

interface Group {
  groupId: number;
  groupName: string;
  avatar?: string;
  description?: string;
  announcement?: string;
  maxMembers: number;
  ownerId: string;
  createTime: string;
}

interface GroupMember {
  id: number;
  groupId: number;
  userId: string;
  username: string;
  avatar?: string;
  role: number;
  nickname?: string;
  joinedAt: string;
}

// ========== 基础状态 ==========
const onlineUsers = ref<Friend[]>([]);
const friendList = ref<Friend[]>([]);
const selectedUser = ref<Friend | null>(null);
const activeTab = ref("chat");

const showRobotDialog = ref(false);
const userMessage = ref("");
const chatMessages = ref<ChatMessage[]>([]);
const unreadCount = ref(0);
const messageCache = ref<Map<string, ChatMessage[]>>(new Map());
const lastMessageUser = ref<Friend | null>(null);
const userUnreadCounts = ref<Map<string, number>>(new Map());
const chatHistoryUserIds = ref<string[]>([]);

const showAddFriendDialog = ref(false);
const addingFriend = ref(false);
const friendRequests = ref<Friend[]>([]);
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

const isSubscribed = ref(false);
const currentUserId = ref("");

// ========== 群组相关状态 ==========
const groupList = ref<Group[]>([]);
const selectedGroup = ref<Group | null>(null);
const groupMembers = ref<GroupMember[]>([]);
const showCreateGroupDialog = ref(false);
const creatingGroup = ref(false);
const groupNameInput = ref("");
const selectedFriendIds = ref<Set<string>>(new Set());
const showGroupMemberPanel = ref(false);
const showGroupAnnounceInput = ref(false);
const groupAnnounceText = ref("");
const showInviteDialog = ref(false);
const inviteSelectedIds = ref<Set<string>>(new Set());
const invitingMembers = ref(false);

// ========== 计算属性 ==========
const pendingRequestCount = computed(() => {
  return friendRequests.value.filter((r) => r.status === "待处理").length;
});

const chatCacheKey = computed(() => {
  if (selectedGroup.value) return `group:${selectedGroup.value.groupId}`;
  if (selectedUser.value) return selectedUser.value.friendId;
  return "";
});

const isGroupOwner = computed(() => {
  if (!selectedGroup.value) return false;
  return selectedGroup.value.ownerId === currentUserId.value;
});

const isGroupAdmin = computed(() => {
  if (!selectedGroup.value) return false;
  const me = groupMembers.value.find((m) => m.userId === currentUserId.value);
  return me ? me.role >= 2 : false;
});

const availableInviteFriends = computed(() => {
  const memberIds = new Set(groupMembers.value.map((m) => m.userId));
  return friendList.value.filter((f) => !memberIds.has(f.friendId));
});

const isActiveChat = (id: string) => {
  if (id.startsWith("group:")) {
    return selectedGroup.value?.groupId === Number(id.slice(6));
  }
  return selectedUser.value?.friendId === id;
};

const chatHistoryList = computed(() => {
  return chatHistoryUserIds.value
    .map((id) => {
      const msgs = messageCache.value.get(id);
      if (!msgs || msgs.length === 0) return null;
      const lastMsg = msgs[msgs.length - 1];
      if (id.startsWith("group:")) {
        const gid = Number(id.slice(6));
        const g = groupList.value.find((x) => x.groupId === gid);
        return {
          userId: id,
          username: g?.groupName || "群聊",
          avatar: g?.avatar,
          lastMessage: lastMsg.content,
          unreadCount: 0,
        };
      }
      const user =
        onlineUsers.value.find((u) => u.friendId === id) ||
        friendList.value.find((f) => f.friendId === id);
      return {
        userId: id,
        username: user?.friendName || "未知用户",
        avatar: user?.friendAvatar,
        lastMessage: lastMsg.content,
        unreadCount: userUnreadCounts.value.get(id) || 0,
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
    (a.friendName || "").localeCompare(b.friendName || "", "zh-CN"),
  );
  const groups: Map<string, Friend[]> = new Map();
  for (const friend of sorted) {
    const firstChar = (friend.friendName || "#").charAt(0).toUpperCase();
    const letter = /[A-Z]/.test(firstChar) ? firstChar : "#";
    if (!groups.has(letter)) groups.set(letter, []);
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

const alphabetIndex = computed(() => groupedFriends.value.map((g) => g.letter));

// ========== 字母索引 ==========
const scrollToLetter = (letter: string) => {
  activeLetter.value = letter;
  const el = document.getElementById(`friend-group-${letter}`);
  if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
  setTimeout(() => {
    activeLetter.value = "";
  }, 800);
};

const handleLetterTouchStart = (e: TouchEvent) =>
  updateLetterFromTouch(e.touches[0]);
const handleLetterTouchMove = (e: TouchEvent) =>
  updateLetterFromTouch(e.touches[0]);

const updateLetterFromTouch = (touch: Touch) => {
  const target = document.elementFromPoint(touch.clientX, touch.clientY);
  if (target) {
    const letter = (target as HTMLElement).textContent?.trim();
    if (letter && alphabetIndex.value.includes(letter)) scrollToLetter(letter);
  }
};

// ========== 消息处理 ==========
const processedMsgMap = new Map<string, number>();
let cleanUpTimer: any = null;

const generateMsgFingerprint = (payload: any): string => {
  if (payload.msgId) return `id_${payload.msgId}`;
  const secondTimestamp = Math.floor(Date.now() / 1000);
  return `hash_${payload.from}_${payload.content}_${secondTimestamp}`;
};

const selectUser = (user: Friend) => {
  selectedGroup.value = null;
  selectedUser.value = user;
  userUnreadCounts.value.set(user.friendId, 0);
  const cached = messageCache.value.get(user.friendId);
  chatMessages.value = cached ? [...cached] : [];
  nextTick(scrollToBottom);
};

const selectGroup = async (group: Group) => {
  selectedUser.value = null;
  selectedGroup.value = group;
  const cacheKey = `group:${group.groupId}`;
  const cached = messageCache.value.get(cacheKey);
  chatMessages.value = cached ? [...cached] : [];
  showGroupMemberPanel.value = false;
  showGroupAnnounceInput.value = false;
  groupAnnounceText.value = group.announcement || "";
  nextTick(scrollToBottom);
  loadGroupMembers(group.groupId);
};

const selectChatHistoryUser = (id: string) => {
  if (id.startsWith("group:")) {
    const gid = Number(id.slice(6));
    const g = groupList.value.find((x) => x.groupId === gid);
    if (g) selectGroup(g);
    return;
  }
  userUnreadCounts.value.set(id, 0);
  const user =
    onlineUsers.value.find((u) => u.friendId === id) ||
    friendList.value.find((f) => f.friendId === id);
  if (user) selectUser(user);
};

const updateUserStatus = (userId: string, isActive: string) => {
  [onlineUsers.value, friendList.value].forEach((list) => {
    const idx = list.findIndex((u: any) => u.userId === userId);
    // if (idx !== -1) list[idx].isActive = isActive;
  });
};

const subscribeUserStatus = () => {
  if (!isConnected.value || isSubscribed.value) return;
  const targetUserIds = [
    ...onlineUsers.value.map((u) => u.friendId),
    ...friendList.value.map((f) => f.friendId),
  ];
  if (targetUserIds.length === 0) return;
  sendWsMessage("status", { action: "subscribe", targetUserIds });
};

const sendMessage = () => {
  if (!userMessage.value.trim() || !isConnected.value) return;
  if (!selectedUser.value && !selectedGroup.value) return;

  const content = userMessage.value;
  const msgId = `local_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
  const userMsg: ChatMessage = { id: msgId, type: "user", content };
  chatMessages.value.push(userMsg);
  processedMsgMap.set(msgId, Date.now());

  const cacheKey = chatCacheKey.value;
  if (!messageCache.value.has(cacheKey)) messageCache.value.set(cacheKey, []);
  messageCache.value.get(cacheKey)!.push(userMsg);
  if (!chatHistoryUserIds.value.includes(cacheKey))
    chatHistoryUserIds.value.unshift(cacheKey);

  if (selectedGroup.value) {
    sendWsMessage("chat", {
      msgId,
      to: `group:${selectedGroup.value.groupId}`,
      content,
    });
  } else if (selectedUser.value) {
    sendWsMessage("chat", { msgId, to: selectedUser.value.friendId, content });
  }
  scrollToBottom();
  userMessage.value = "";
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

// ========== 好友请求 ==========
const handleAddFriend = (formData: { userId: string; remark: string }) => {
  addingFriend.value = true;
  try {
    sendWsMessage("friend", {
      action: "request",
      to: formData.userId,
      msg: formData.remark,
    });
    const localRequest: Friend = {
      requestId: 0,
      friendId: formData.userId,
      friendName: formData.userId,
      friendAvatar: "",
      status: "等待中",
      remark: formData.remark,
    };
    friendRequests.value.unshift(localRequest);
    ElMessage.success("好友请求已发送");
    showAddFriendDialog.value = false;
  } catch {
    ElMessage.error("发送失败");
  } finally {
    addingFriend.value = false;
  }
};

const acceptFriendRequest = async (requestId: number) => {
  acceptingRequestId.value = requestId;
  try {
    sendWsMessage("friend", { action: "accept", requestId });
    const request = friendRequests.value.find((r) => r.requestId === requestId);
    if (request) request.status = "已同意";
    ElMessage.success("已同意好友请求");
    await new Promise((resolve) => setTimeout(resolve, 500));
    await loadFriendList();
    await loadFriendRequests();
  } catch {
    ElMessage.error("操作失败");
  } finally {
    acceptingRequestId.value = null;
  }
};

const loadFriendRequests = async (append = false) => {
  try {
    if (append) requestLoadingMore.value = true;
    const response = await getFriendRequestPage({
      pageSize: 20,
      lastId: append ? requestLastId.value : 0,
      lastCreateTime: append ? requestLastCreateTime.value : "",
    });
    if (response.code === 200) {
      const list =
        response.data?.friendRequests ||
        response.data?.list ||
        response.data ||
        [];
      const items = Array.isArray(list) ? list : [];
      if (append) {
        const existingIds = new Set(
          friendRequests.value.map((r) => r.requestId),
        );
        friendRequests.value = [
          ...friendRequests.value,
          ...items.filter((i: Friend) => !existingIds.has(i.requestId)),
        ];
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
  } catch {
    /* silent */
  } finally {
    requestLoadingMore.value = false;
  }
};

const loadMoreFriendRequests = () => {
  if (!requestLoadingMore.value && requestHasMore.value)
    loadFriendRequests(true);
};

const loadFriendList = async () => {
  try {
    const response = await getFriendPage();
    if (response.code === 200) {
      const list =
        response.data?.friendList || response.data?.list || response.data || [];
      friendList.value = Array.isArray(list) ? list : [];
    }
  } catch {
    /* silent */
  }
};

// ========== 群组操作 ==========
const loadMyGroups = async () => {
  try {
    const res = await getMyGroups({});
    if (res.code === 200)
      groupList.value = Array.isArray(res.data.groups) ? res.data.groups : [];
  } catch {
    /* silent */
  }
};

const loadGroupMembers = async (groupId: number) => {
  try {
    const res = await getGroupMembers({ groupId });
    if (res.code === 200)
      groupMembers.value = Array.isArray(res.data.groupMembers) ? res.data.groupMembers : [];
  } catch {
    /* silent */
  }
};

const openCreateGroupDialog = () => {
  groupNameInput.value = "";
  selectedFriendIds.value = new Set();
  showCreateGroupDialog.value = true;
};

const toggleFriendSelect = (friendId: string) => {
  const next = new Set(selectedFriendIds.value);
  next.has(friendId) ? next.delete(friendId) : next.add(friendId);
  selectedFriendIds.value = next;
};

const handleCreateGroup = async () => {
  if (!groupNameInput.value.trim() || selectedFriendIds.value.size === 0)
    return;
  creatingGroup.value = true;
  try {
    sendWsMessage("group", {
      action: "create",
      name: groupNameInput.value.trim(),
      userIds: [...selectedFriendIds.value],
    });
    ElMessage.success("群组创建成功");
    showCreateGroupDialog.value = false;
    setTimeout(() => loadMyGroups(), 600);
  } catch {
    ElMessage.error("创建群组失败");
  } finally {
    creatingGroup.value = false;
  }
};

const openInviteDialog = () => {
  inviteSelectedIds.value = new Set();
  showInviteDialog.value = true;
};

const toggleInviteSelect = (friendId: string) => {
  const next = new Set(inviteSelectedIds.value);
  next.has(friendId) ? next.delete(friendId) : next.add(friendId);
  inviteSelectedIds.value = next;
};

const handleInviteMembers = async () => {
  if (!selectedGroup.value || inviteSelectedIds.value.size === 0) return;
  invitingMembers.value = true;
  try {
    sendWsMessage("group", {
      action: "invite",
      groupId: selectedGroup.value.groupId,
      userIds: [...inviteSelectedIds.value],
    });
    ElMessage.success("邀请已发送");
    showInviteDialog.value = false;
    setTimeout(() => {
      if (selectedGroup.value) {
        loadGroupMembers(selectedGroup.value.groupId);
      }
    }, 500);
  } catch {
    ElMessage.error("邀请失败");
  } finally {
    invitingMembers.value = false;
  }
};

const handleKickMember = async (userId: string, username: string) => {
  const group = selectedGroup.value;
  if (!group) return;
  try {
    await ElMessageBox.confirm(`确定要将 ${username} 踢出群组？`, "踢出成员", {
      type: "warning",
    });
  } catch {
    return;
  }
  sendWsMessage("group", {
    action: "kick",
    groupId: group.groupId,
    targetUserId: userId,
  });
  ElMessage.success(`已将 ${username} 踢出`);
  loadGroupMembers(group.groupId);
};

const handleLeaveGroup = async () => {
  const group = selectedGroup.value;
  if (!group) return;
  try {
    await ElMessageBox.confirm("确定要退出该群组？", "退出群组", {
      type: "warning",
    });
  } catch {
    return;
  }
  sendWsMessage("group", { action: "leave", groupId: group.groupId });
  ElMessage.success("已退出群组");
  selectedGroup.value = null;
  chatMessages.value = [];
  loadMyGroups();
};

const handleDismissGroup = async () => {
  const group = selectedGroup.value;
  if (!group) return;
  try {
    await ElMessageBox.confirm(
      "确定要解散该群组？所有成员将被移除。",
      "解散群组",
      { type: "error" },
    );
  } catch {
    return;
  }
  sendWsMessage("group", { action: "dismiss", groupId: group.groupId });
  ElMessage.success("群组已解散");
  selectedGroup.value = null;
  chatMessages.value = [];
  loadMyGroups();
};

const handleAnnounce = () => {
  if (!selectedGroup.value || !groupAnnounceText.value.trim()) return;
  sendWsMessage("group", {
    action: "announce",
    groupId: selectedGroup.value.groupId,
    announcement: groupAnnounceText.value.trim(),
  });
  selectedGroup.value.announcement = groupAnnounceText.value.trim();
  ElMessage.success("公告已发布");
  showGroupAnnounceInput.value = false;
};

const handleGroupNotification = (payload: any) => {
  const group = selectedGroup.value;
  switch (payload.action) {
    case "group_created":
      loadMyGroups();
      break;
    case "group_dismissed":
      if (group !== null && group.groupId === payload.groupId) {
        selectedGroup.value = null;
        chatMessages.value = [];
      }
      loadMyGroups();
      break;
    case "group_left":
    case "kicked_from_group":
      loadMyGroups();
      break;
    case "members_invited":
      if (group !== null && group.groupId === payload.groupId)
        loadGroupMembers(payload.groupId);
      break;
    case "group_announce":
      if (group !== null && group.groupId === payload.groupId) {
        group.announcement = payload.announcement;
        groupAnnounceText.value = payload.announcement || "";
      }
      loadMyGroups();
      break;
  }
};

// ========== WebSocket 消息路由 ==========
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
        else if (topic === "notification") {
          if (
            payload.action?.startsWith("group_") ||
            payload.action === "members_invited" ||
            payload.action === "kicked_from_group"
          ) {
            handleGroupNotification(payload);
          } else {
            handleFriendMessage(payload);
          }
        }
      }
    }
  },
  { immediate: true },
);

const handleChatMessage = (payload: any) => {
  if (payload.from === currentUserId.value) return;
  const fingerprint = generateMsgFingerprint(payload);
  if (processedMsgMap.has(fingerprint)) return;
  processedMsgMap.set(fingerprint, Date.now());

  const isGroupMsg = payload.chatType === "group";
  const targetId = isGroupMsg ? `group:${payload.groupId}` : payload.from;
  const targetName = isGroupMsg
    ? groupList.value.find((g) => g.groupId === payload.groupId)?.groupName || "群聊"
    : (
        onlineUsers.value.find((u) => u.friendId === payload.from) ||
        friendList.value.find((f) => f.friendId === payload.from)
      )?.friendName;

  const isCurrentChat = isGroupMsg
    ? selectedGroup.value?.groupId === payload.groupId
    : selectedUser.value?.friendId === payload.from;

  if (!showRobotDialog.value || !isCurrentChat) {
    unreadCount.value++;
    if (!isGroupMsg) {
      userUnreadCounts.value.set(
        targetId,
        (userUnreadCounts.value.get(targetId) || 0) + 1,
      );
    }
    if (!showRobotDialog.value) {
      ElNotification({
        title: targetName || "新消息",
        message: payload.content,
        type: "info",
        duration: 3000,
        position: "top-right",
      });
    }
  }

  const newMessage: ChatMessage = {
    id: fingerprint,
    type: "ai",
    content: payload.content,
    fromUserId: isGroupMsg ? payload.from : undefined,
  };
  if (!messageCache.value.has(targetId)) messageCache.value.set(targetId, []);
  messageCache.value.get(targetId)!.push(newMessage);
  if (!chatHistoryUserIds.value.includes(targetId))
    chatHistoryUserIds.value.unshift(targetId);

  if (isGroupMsg && selectedGroup.value?.groupId === payload.groupId) {
    chatMessages.value.push(newMessage);
    scrollToBottom();
  } else if (!isGroupMsg && selectedUser.value?.friendId === payload.from) {
    chatMessages.value.push(newMessage);
    scrollToBottom();
  }
};

const handleStatusMessage = (payload: any) => {
  if (payload.action === "update" && payload.userId)
    updateUserStatus(payload.userId, payload.isActive);
  else if (payload.action === "subscribe_success") isSubscribed.value = true;
};

const handleFriendMessage = (payload: any) => {
  if (payload.action === "friend_request") {
    const request: Friend = {
      requestId: payload.requestId,
      friendId: payload.from,
      friendName: payload.fromUsername || "",
      friendAvatar: payload.fromAvatar,
      remark: payload.msg,
      status: "待处理",
      updateTime: payload.createTime || new Date().toISOString(),
    };
    if (!friendRequests.value.some((r) => r.requestId === request.requestId)) {
      friendRequests.value.unshift(request);
    }
    ElNotification({
      title: "好友请求",
      message: `${payload.fromUsername || payload.from} 请求添加你为好友`,
      type: "info",
      duration: 5000,
      position: "top-right",
    });
  } else if (payload.action === "friend_accept") {
    const matched = friendRequests.value.find(
      (r) => r.requestId === payload.requestId,
    );
    if (matched) matched.status = "已同意";
    ElMessage.success("对方已同意你的好友请求");
    loadFriendList();
  } else if (payload.action === "friend_reject") {
    const matched = friendRequests.value.find(
      (r) => r.requestId === payload.requestId,
    );
    if (matched) matched.status = "已拒绝";
    ElMessage.info("对方已拒绝你的好友请求");
  }
};

// ========== 拖拽 ==========
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
  if (!isDragging.value) return;
  hasMoved.value = true;
  robotPosition.value.right = Math.max(
    0,
    Math.min(
      window.innerWidth - e.clientX - (60 - dragOffset.value.x),
      window.innerWidth - 60,
    ),
  );
  robotPosition.value.bottom = Math.max(
    0,
    Math.min(
      window.innerHeight - e.clientY - (60 - dragOffset.value.y),
      window.innerHeight - 60,
    ),
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
      loadMyGroups();
      if (
        lastMessageUser.value &&
        !selectedUser.value &&
        !selectedGroup.value
      ) {
        selectUser(lastMessageUser.value);
      } else if (selectedUser.value) {
        const cached = messageCache.value.get(selectedUser.value.friendId);
        chatMessages.value = cached ? [...cached] : [];
        nextTick(scrollToBottom);
      }
    }
  }
};

const handleDialogClose = () => {
  selectedUser.value = null;
  selectedGroup.value = null;
  groupMembers.value = [];
  chatMessages.value = [];
};

// ========== 生命周期 ==========
onMounted(() => {
  currentUserId.value =
    userStore.userInfo?.userId ||
    (() => {
      try {
        return JSON.parse(localStorage.getItem("user") || "{}").userId || "";
      } catch {
        return "";
      }
    })();
  const savedPosition = localStorage.getItem("robotPosition");
  if (savedPosition) {
    try {
      robotPosition.value = JSON.parse(savedPosition);
    } catch {}
  }
  connectWs();
  cleanUpTimer = setInterval(() => {
    const now = Date.now();
    for (const [key, time] of processedMsgMap.entries()) {
      if (now - time > 5000) processedMsgMap.delete(key);
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

.friend-index-layout {
  position: relative;
}
.friend-groups {
  padding-right: 24px;
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

/* 群组工具栏 */
.group-toolbar {
  display: flex;
  gap: 6px;
  padding: 6px 12px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
  flex-wrap: wrap;
}
.announce-input-area {
  display: flex;
  gap: 6px;
  padding: 6px 12px;
  border-top: 1px solid #ebeef5;
  background: #fffbe6;
}
.group-member-panel {
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}
.panel-title {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}
.member-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
}
.member-name {
  flex: 1;
  font-size: 13px;
}
.select-friends-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
}
.select-friend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 4px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.select-friend-item:hover {
  background: #f5f7fa;
}
.select-friend-item.selected {
  background: #ecf5ff;
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