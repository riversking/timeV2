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
        <div style="margin-bottom: 10px; font-weight: bold">在线用户</div>
        <el-scrollbar
          ref="userListScrollbar"
          style="height: 450px"
          @scroll="handleUserListScroll"
        >
          <div
            v-for="user in onlineUsers"
            :key="user.id"
            :class="['user-item', { active: selectedUser?.id === user.id }]"
            @click="selectUser(user)"
          >
            <el-avatar size="small" :src="user.avatar">{{
              user.name?.charAt(0)
            }}</el-avatar>
            <span style="margin-left: 10px">{{ user.name }}</span>
            <el-badge
              is-dot
              :hidden="!user.isActive"
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
          与 {{ selectedUser.name }} 聊天中
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
                selectedUser?.name || "AI助手"
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
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from "vue";
import { Stamp } from "@element-plus/icons-vue";
import useWebSocket from "@/composables/useWebSocket";
import { ElNotification, ElMessage } from "element-plus";
import { getUserPage } from "@/api/user";

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
  id: string;
  name: string;
  avatar?: string;
  isActive: boolean;
}

const onlineUsers = ref<User[]>([
  { id: "ri123", name: "AI助手", isActive: true },
  { id: "admin", name: "张三", isActive: true },
  { id: "user2", name: "李四", isActive: false },
  { id: "user3", name: "王五", isActive: true },
]);

const selectedUser = ref<User | null>(null);

// 机器人对话相关
const showRobotDialog = ref(false);
const userMessage = ref("");
const chatMessages = ref<Array<{ type: "user" | "ai"; content: string }>>([]);
const unreadCount = ref(0); // 新增：未读消息计数

// 消息容器引用
const messagesContainer = ref<HTMLDivElement | null>(null);

// 可拖拽机器人按钮 - 使用 right/bottom 定位
const robotButton = ref<HTMLDivElement | null>(null);
const robotPosition = ref({ right: 20, bottom: 20 }); // 默认右下角位置
const isDragging = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const users = ref<User[]>([]);
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);

// 选择用户
const selectUser = (user: User) => {
  selectedUser.value = user;
  // 清空聊天记录或加载历史记录
  chatMessages.value = [];
  if (user.id === "ai") {
    chatMessages.value.push({
      type: "ai",
      content: "你好！我是AI助手，有什么可以帮助你的吗？",
    });
  }
};

// 监听WebSocket消息并添加到聊天记录
watch(
  wsMessages,
  (newMessages) => {
    console.log("Received messages:", newMessages);
    if (newMessages.length > 0) {
      const latestMessage = newMessages[newMessages.length - 1];
      // 处理服务器返回的消息格式：{ "msgId":null,"type":"chat","content":"hi","extraData":null,"from":"admin","to":"ri123",... }
      if (latestMessage.type === "chat") {
        const currentUser = localStorage.getItem("user")
          ? JSON.parse(localStorage.getItem("user")!).userId
          : null;

        // 新增：查找消息相关的用户（无论是发送给谁的）
        let relevantUser: User | null = null;

        // 检查是否是发给当前用户的消息
        if (latestMessage.to === currentUser) {
          // 找到发送者
          const senderUser = onlineUsers.value.find(
            (user) => user.id === latestMessage.from,
          );
          if (senderUser) {
            relevantUser = senderUser;

            // 将发送者移到数组第一个位置
            const senderIndex = onlineUsers.value.findIndex(
              (user) => user.id === latestMessage.from,
            );
            if (senderIndex !== -1) {
              onlineUsers.value.splice(senderIndex, 1);
              onlineUsers.value.unshift(senderUser);
            }
          }
        }
        // 检查是否是当前用户发送的消息的回显
        else if (latestMessage.from === currentUser) {
          // 找到接收者
          const receiverUser = onlineUsers.value.find(
            (user) => user.id === latestMessage.to,
          );
          if (receiverUser) {
            relevantUser = receiverUser;
          }
        }

        // 如果找到了相关用户
        if (relevantUser) {
          // 如果当前没有选中用户，或者选中的不是相关用户，则选中相关用户
          if (
            !selectedUser.value ||
            selectedUser.value.id !== relevantUser.id
          ) {
            selectUser(relevantUser);
          }

          // 添加消息到聊天记录
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
              (selectedUser.value && selectedUser.value.id !== relevantUser.id))
          ) {
            unreadCount.value++;
            ElNotification({
              title: "新消息",
              message: `${relevantUser.name}: ${latestMessage.content}`,
              type: "info",
              duration: 3000,
              position: "bottom-right",
            });
          }
          scrollToBottom();
          // 如果当前没有选中用户，或者选中的不是相关用户，则选中相关用户
          if (
            !selectedUser.value ||
            selectedUser.value.id !== relevantUser.id
          ) {
            selectUser(relevantUser);
          }
        }
      }
    }
  },
  { deep: true },
);

// 组件挂载时恢复位置
onMounted(() => {
  // 从 localStorage 恢复机器人位置
  const savedPosition = localStorage.getItem("robotPosition");
  if (savedPosition) {
    try {
      const position = JSON.parse(savedPosition);
      // 兼容旧的 left/top 格式
      if (position.x !== undefined && position.y !== undefined) {
        // 转换为 right/bottom
        robotPosition.value = {
          right: window.innerWidth - position.x - 60,
          bottom: window.innerHeight - position.y - 60,
        };
      } else {
        robotPosition.value = position;
      }
    } catch (e) {
      console.error("Failed to parse robot position from localStorage");
      // 使用默认右下角位置
      robotPosition.value = { right: 20, bottom: 20 };
    }
  }

  // 加载用户列表
  loading.value = true;
  loadUserList(1);
});

const loadUserList = async (page: number) => {
  try {
    const response = await getUserPage({
      currentPage: page,
      pageSize: pageSize.value,
    });
    console.log("获取用户数据成功:", response);
    if (response.code === 200) {
      const users = response.data.users || [];
      // 添加isActive字段，默认为true
      const usersWithActive = users.map((user: User) => ({
        ...user,
        isActive: true,
      }));
      if (page === 1) {
        onlineUsers.value = usersWithActive;
      } else {
        onlineUsers.value = [...onlineUsers.value, ...usersWithActive];
      }
      total.value = response.data.total || 0;
      hasMore.value = onlineUsers.value.length < total.value;
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

  // 当滚动到底部时加载更多
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

  // 添加用户消息到本地显示
  const userMsg = { type: "user" as const, content: userMessage.value };
  // chatMessages.value.push(userMsg);

  // 立即滚动到底部显示用户消息
  scrollToBottom();

  // 通过WebSocket发送消息，使用指定的报文格式
  sendWsMessage({
    type: "chat",
    content: userMessage.value,
    to: selectedUser.value.id,
  });

  userMessage.value = "";
};

// 拖拽功能
const startDrag = (e: MouseEvent) => {
  e.preventDefault();
  isDragging.value = true;

  // 计算鼠标相对于按钮左上角的偏移量
  if (robotButton.value) {
    const rect = robotButton.value.getBoundingClientRect();
    dragOffset.value = {
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    };
  }

  // 添加全局事件监听器
  document.addEventListener("mousemove", handleDrag);
  document.addEventListener("mouseup", stopDrag);
};

const handleDrag = (e: MouseEvent) => {
  if (!isDragging.value) return;

  // 计算新的位置 (基于 right/bottom)
  const newRight = window.innerWidth - e.clientX - dragOffset.value.x;
  const newBottom = window.innerHeight - e.clientY - dragOffset.value.y;

  // 限制在视口范围内 (确保按钮完全可见)
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

  // 保存位置到 localStorage
  localStorage.setItem("robotPosition", JSON.stringify(robotPosition.value));
};

// 切换机器人对话框
const toggleRobotDialog = () => {
  if (!isDragging.value) {
    showRobotDialog.value = !showRobotDialog.value;

    // 对话框打开后滚动到底部
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

// 清理事件监听器
onBeforeUnmount(() => {
  document.removeEventListener("mousemove", handleDrag);
  document.removeEventListener("mouseup", stopDrag);
  // 关闭WebSocket连接
  closeWsConnection();
});
</script>

<style scoped>
/* 可拖拽机器人按钮样式 */
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

/* 用户列表项样式 */
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
