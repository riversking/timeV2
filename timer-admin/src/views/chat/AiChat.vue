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
    append-to-body
  >
    <div v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</div>
    <el-icon><Stamp /></el-icon>
  </div>

  <!-- 机器人对话框 -->
  <el-dialog
    v-model="showRobotDialog"
    title="AI助手"
    width="800px"
    :close-on-click-modal="false"
    :destroy-on-close="true"
    @closed="handleDialogClose"
  >
    <div style="height: 500px; display: flex">
      <!-- 左侧用户列表 -->
      <div style="width: 200px; border-right: 1px solid #ebeef5; padding: 10px">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
          <span style="font-weight: bold">在线用户</span>
          <el-button type="primary" size="small" @click="showAddFriendDialog = true">
            <el-icon><Plus /></el-icon>
            添加好友
          </el-button>
        </div>
        <el-scrollbar
          ref="userListScrollbar"
          style="height: 450px"
          @scroll="handleUserListScroll"
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
            <span style="margin-left: 10px">{{ user.username }}</span>
            <el-badge
              is-dot
              :hidden="user.isActive === '0'"
              style="margin-left: auto"
            />
          </div>
          <div v-if="loadingMore" style="text-align: center; padding: 10px">
            加载中...
          </div>
          <div
            v-else-if="!hasMore && onlineUsers.length > 0"
            style="text-align: center; padding: 10px; color: #909399"
          >
            没有更多用户了
          </div>
        </el-scrollbar>
      </div>

      <!-- 右侧聊天框 -->
      <div
        style="flex: 1; display: flex; flex-direction: column; padding: 10px"
      >
        <div v-if="selectedUser" style="margin-bottom: 10px; font-weight: bold">
          与 {{ selectedUser.username }} 聊天中
        </div>
        <div
          ref="messagesContainer"
          style="
            flex: 1;
            overflow-y: auto;
            padding: 10px;
            background: #f5f7fa;
            border-radius: 8px;
            margin-bottom: 10px;
          "
        >
          <div
            v-for="(msg, index) in chatMessages"
            :key="index"
            style="margin-bottom: 10px"
          >
            <div v-if="msg.type === 'user'" style="text-align: right">
              <div
                style="
                  display: inline-block;
                  background: #e6f7ff;
                  padding: 8px 12px;
                  border-radius: 12px;
                  margin-top: 5px;
                  max-width: 80%;
                "
              >
                {{ msg.content }}
              </div>
              <el-tag type="success" size="small">我</el-tag>
            </div>
            <div v-else style="text-align: left">
              <el-tag type="primary" size="small">{{
                selectedUser?.username || "AI助手"
              }}</el-tag>
              <div
                style="
                  display: inline-block;
                  background: #f0f9eb;
                  padding: 8px 12px;
                  border-radius: 12px;
                  margin-top: 5px;
                  max-width: 80%;
                "
              >
                {{ msg.content }}
              </div>
            </div>
          </div>
          <div
            v-if="!isConnected"
            style="text-align: center; color: #909399; margin-top: 10px"
          >
            连接中...
          </div>
        </div>
        <div style="display: flex; gap: 10px">
          <el-input
            v-model="userMessage"
            placeholder="请输入消息..."
            @keyup.enter="sendMessage"
            style="flex: 1"
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
  <el-dialog
    v-model="showAddFriendDialog"
    title="添加好友"
    width="400px"
    :close-on-click-modal="false"
  >
    <el-form :model="addFriendForm" label-width="80px">
      <el-form-item label="用户ID">
        <el-input
          v-model="addFriendForm.userId"
          placeholder="请输入要添加的用户ID"
          clearable
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="addFriendForm.remark"
          placeholder="请输入备注信息（可选）"
          clearable
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="showAddFriendDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddFriend" :loading="addingFriend">
          确定
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from "vue";
import { Stamp, Plus } from "@element-plus/icons-vue";
import useWebSocket from "@/composables/useWebSocket";
import { ElNotification, ElMessage } from "element-plus";
import { getUserActivePage } from "@/api/user";

// WebSocket配置
const WS_URL = "/websocket/im-server/ws/chat";

// 使用WebSocket组合式函数
const {
  messages: wsMessages,
  isConnected,
  send: sendWsMessage,
  close: closeWsConnection,
} = useWebSocket(WS_URL);

// 用户列表相关
interface User {
  userId: string;
  username: string;
  avatar?: string;
  isActive: string;
}

const onlineUsers = ref<User[]>([]);
const selectedUser = ref<User | null>(null);

// 机器人对话相关
const showRobotDialog = ref(false);
const userMessage = ref("");
const chatMessages = ref<Array<{ type: "user" | "ai"; content: string }>>([]);
const unreadCount = ref(0);

// 添加好友相关
const showAddFriendDialog = ref(false);
const addingFriend = ref(false);
const addFriendForm = ref({
  userId: "",
  remark: ""
});

// 消息容器引用
const messagesContainer = ref<HTMLDivElement | null>(null);

// 可拖拽机器人按钮 - 使用 right/bottom 定位
const robotButton = ref<HTMLDivElement | null>(null);
const robotPosition = ref({ right: 20, bottom: 20 });
const isDragging = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);

// 状态管理
const isSubscribed = ref(false);

// 选择用户
const selectUser = (user: User) => {
  selectedUser.value = user;
  chatMessages.value = [];
  if (user.userId === "ai") {
    chatMessages.value.push({
      type: "ai",
      content: "你好！我是AI助手，有什么可以帮助你的吗？",
    });
  }
};

// 更新用户状态
const updateUserStatus = (userId: string, isActive: string) => {
  const userIndex = onlineUsers.value.findIndex(user => user.userId === userId);
  if (userIndex !== -1) {
    onlineUsers.value[userIndex].isActive = isActive;
  }
};

// 批量更新用户状态
const batchUpdateUserStatus = (statusList: Array<{ userId: string; isActive: string }>) => {
  const statusMap = new Map(statusList.map(item => [item.userId, item.isActive]));
  onlineUsers.value = onlineUsers.value.map(user => ({
    ...user,
    isActive: statusMap.get(user.userId) || user.isActive
  }));
};

// 订阅用户状态
const subscribeUserStatus = () => {
  if (!isConnected.value || isSubscribed.value || onlineUsers.value.length === 0) {
    return;
  }
  
  const targetUserIds = onlineUsers.value.map(user => user.userId);
  sendWsMessage({
    type: "subscribe_status",
    extraData: JSON.stringify({ targetUserIds })
  });
};

// 重新订阅用户状态（用于重连后）
const resubscribeUserStatus = () => {
  isSubscribed.value = false;
  nextTick(() => {
    subscribeUserStatus();
  });
};

// 处理添加好友
const handleAddFriend = async () => {
  if (!addFriendForm.value.userId.trim()) {
    ElMessage.warning("请输入用户ID");
    return;
  }

  addingFriend.value = true;
  
  try {
    sendWsMessage({
      type: "add_friend",
      extraData: JSON.stringify({
        targetUserId: addFriendForm.value.userId,
        remark: addFriendForm.value.remark
      })
    });

    ElMessage.success("好友请求已发送");
    showAddFriendDialog.value = false;
    addFriendForm.value = {
      userId: "",
      remark: ""
    };
  } catch (error) {
    console.error("添加好友失败:", error);
    ElMessage.error("添加好友失败");
  } finally {
    addingFriend.value = false;
  }
};

// 监听WebSocket消息并添加到聊天记录
watch(
  wsMessages,
  (newMessages) => {
    console.log("Received messages:", newMessages);
    if (newMessages.length > 0) {
      const latestMessage = newMessages[newMessages.length - 1];
      
      // 处理添加好友响应
      if (latestMessage.type === "add_friend_response") {
        try {
          const responseData = JSON.parse(latestMessage.extraData || "{}");
          if (responseData.success) {
            ElMessage.success(responseData.message || "好友添加成功");
            loadUserList(1);
          } else {
            ElMessage.error(responseData.message || "好友添加失败");
          }
        } catch (e) {
          console.error("解析添加好友响应失败:", e);
        }
      }
      
      // 处理服务器返回的消息格式
      if (latestMessage.type === "chat") {
        const currentUser = localStorage.getItem("user")
          ? JSON.parse(localStorage.getItem("user")!).userId
          : null;

        let relevantUser: User | null = null;

        if (latestMessage.to === currentUser) {
          const senderUser = onlineUsers.value.find(
            (user) => user.userId === latestMessage.from,
          );
          if (senderUser) {
            relevantUser = senderUser;
            const senderIndex = onlineUsers.value.findIndex(
              (user) => user.userId === latestMessage.from,
            );
            if (senderIndex !== -1) {
              onlineUsers.value.splice(senderIndex, 1);
              onlineUsers.value.unshift(senderUser);
            }
          }
        } else if (latestMessage.from === currentUser) {
          const receiverUser = onlineUsers.value.find(
            (user) => user.userId === latestMessage.to,
          );
          if (receiverUser) {
            relevantUser = receiverUser;
          }
        }

        if (relevantUser) {
          if (
            !selectedUser.value ||
            selectedUser.value.userId !== relevantUser.userId
          ) {
            selectUser(relevantUser);
          }

          const messageType: "ai" | "user" =
            latestMessage.from === currentUser ? "user" : "ai";
          const chatMessage = {
            type: messageType,
            content: latestMessage.content || "",
          };
          chatMessages.value.push(chatMessage);
          if (
            latestMessage.to === currentUser &&
            (!showRobotDialog.value ||
              (selectedUser.value &&
                selectedUser.value.userId !== relevantUser.userId))
          ) {
            unreadCount.value++;
            ElNotification({
              title: "新消息",
              message: `${relevantUser.username}: ${latestMessage.content}`,
              type: "info",
              duration: 3000,
              position: "bottom-right",
            });
          }
          scrollToBottom();
          if (
            !selectedUser.value ||
            selectedUser.value.userId !== relevantUser.userId
          ) {
            selectUser(relevantUser);
          }
        }
      } else if (latestMessage.type === "status_update") {
        if (latestMessage.extraData) {
          try {
            const statusData = JSON.parse(latestMessage.extraData);
            if (statusData.userId && statusData.isActive !== undefined) {
              updateUserStatus(statusData.userId, statusData.isActive);
            }
          } catch (e) {
            console.error("解析用户状态数据失败:", e);
          }
        }
      } else if (latestMessage.type === "status_batch_update") {
        if (latestMessage.extraData) {
          try {
            const statusList = JSON.parse(latestMessage.extraData);
            if (Array.isArray(statusList)) {
              batchUpdateUserStatus(statusList);
            }
          } catch (e) {
            console.error("解析批量用户状态数据失败:", e);
          }
        }
      } else if (latestMessage.type === "subscribe_success") {
        console.log("用户状态订阅成功:", latestMessage.content);
        isSubscribed.value = true;
      }
    }
  },
  { deep: true },
);

// 组件挂载时恢复位置
onMounted(() => {
  const savedPosition = localStorage.getItem("robotPosition");
  if (savedPosition) {
    try {
      const position = JSON.parse(savedPosition);
      if (position.x !== undefined && position.y !== undefined) {
        robotPosition.value = {
          right: window.innerWidth - position.x - 60,
          bottom: window.innerHeight - position.y - 60,
        };
      } else {
        robotPosition.value = position;
      }
    } catch (e) {
      console.error("Failed to parse robot position from localStorage");
      robotPosition.value = { right: 20, bottom: 20 };
    }
  }
  loading.value = true;
  loadUserList(1);
});

const loadUserList = async (page: number) => {
  try {
    const response = await getUserActivePage({
      currentPage: page,
      pageSize: pageSize.value,
    });
    console.log("获取用户数据成功:", response);
    if (response.code === 200) {
      const users = response.data.users || [];
      if (page === 1) {
        onlineUsers.value = users;
      } else {
        onlineUsers.value = [...onlineUsers.value, ...users];
      }
      total.value = response.data.total || 0;
      hasMore.value = onlineUsers.value.length < total.value;
      
      if (page === 1) {
        nextTick(() => {
          subscribeUserStatus();
        });
      }
    } else {
      ElMessage.error(response.message || "获取用户数据失败");
    }
  } catch (error) {
    console.error("Failed to load user list:", error);
    ElMessage.error("获取用户数据失败");
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

// 加载更多用户
const loadMoreUsers = () => {
  if (!loadingMore.value && hasMore.value) {
    loadingMore.value = true;
    currentPage.value++;
    loadUserList(currentPage.value);
  }
};

// 处理用户列表滚动
const handleUserListScroll = (event: any) => {
  const scrollbar = event.target;
  const scrollTop = scrollbar.scrollTop;
  const clientHeight = scrollbar.clientHeight;
  const scrollHeight = scrollbar.scrollHeight;

  if (scrollTop + clientHeight >= scrollHeight - 10) {
    loadMoreUsers();
  }
};

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

// 发送消息
const sendMessage = () => {
  if (!userMessage.value.trim() || !isConnected.value || !selectedUser.value)
    return;

  const userMsg = { type: "user" as const, content: userMessage.value };

  scrollToBottom();

  sendWsMessage({
    type: "chat",
    content: userMessage.value,
    to: selectedUser.value.userId,
  });

  userMessage.value = "";
};

// 拖拽功能
const startDrag = (e: MouseEvent) => {
  e.preventDefault();
  isDragging.value = true;

  if (robotButton.value) {
    const rect = robotButton.value.getBoundingClientRect();
    dragOffset.value = {
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    };
  }

  document.addEventListener("mousemove", handleDrag);
  document.addEventListener("mouseup", stopDrag);
};

const handleDrag = (e: MouseEvent) => {
  if (!isDragging.value) return;

  const newRight = window.innerWidth - e.clientX - dragOffset.value.x;
  const newBottom = window.innerHeight - e.clientY - dragOffset.value.y;

  const minRight = 0;
  const maxRight = window.innerWidth - 60;
  const minBottom = 0;
  const maxBottom = window.innerHeight - 60;

  robotPosition.value.right = Math.max(minRight, Math.min(newRight, maxRight));
  robotPosition.value.bottom = Math.max(
    minBottom,
    Math.min(newBottom, maxBottom),
  );
};

const stopDrag = () => {
  isDragging.value = false;
  document.removeEventListener("mousemove", handleDrag);
  document.removeEventListener("mouseup", stopDrag);

  localStorage.setItem("robotPosition", JSON.stringify(robotPosition.value));
};

// 切换机器人对话框
const toggleRobotDialog = () => {
  if (!isDragging.value) {
    showRobotDialog.value = !showRobotDialog.value;

    if (showRobotDialog.value) {
      nextTick(() => {
        scrollToBottom();
      });
      unreadCount.value = 0;
    }
  }
};

// 处理对话框关闭
const handleDialogClose = () => {
  // 可以在这里添加额外的清理逻辑
};

// 监听WebSocket连接状态变化
watch(isConnected, (connected) => {
  if (connected) {
    nextTick(() => {
      resubscribeUserStatus();
    });
  } else {
    isSubscribed.value = false;
  }
});

// 清理事件监听器
onBeforeUnmount(() => {
  document.removeEventListener("mousemove", handleDrag);
  document.removeEventListener("mouseup", stopDrag);
  closeWsConnection();
});
</script>

<style scoped>
.draggable-robot-button {
  position: fixed;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4cc9f0, #6a89f7);
  border: none;
  box-shadow: 0 4px 20px rgba(76, 201, 240, 0.5);
  cursor: move;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.draggable-robot-button:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 25px rgba(76, 201, 240, 0.7);
}

.draggable-robot-button .el-icon {
  font-size: 24px;
  color: white;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  margin-bottom: 5px;
}

.user-item:hover {
  background-color: #f5f7fa;
}

.user-item.active {
  background-color: #e6f7ff;
  border-left: 3px solid #409eff;
}

.unread-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background-color: #f56c6c;
  color: white;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 12px;
  min-width: 18px;
  text-align: center;
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.3);
}
</style>