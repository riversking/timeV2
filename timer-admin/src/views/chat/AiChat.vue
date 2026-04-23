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
    <el-icon><Stamp /></el-icon>
  </div>

  <!-- 机器人对话框 -->
  <el-dialog
    v-model="showRobotDialog"
    title="AI助手"
    width="600px"
    :close-on-click-modal="false"
    :destroy-on-close="true"
  >
    <div style="height: 400px; display: flex; flex-direction: column">
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
            <el-tag type="primary" size="small">AI助手</el-tag>
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
      </div>
      <div style="display: flex; gap: 10px">
        <el-input
          v-model="userMessage"
          placeholder="请输入消息..."
          @keyup.enter="sendMessage"
          style="flex: 1"
        />
        <el-button type="primary" @click="sendMessage">发送</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from "vue";
import { Stamp } from "@element-plus/icons-vue";

// 机器人对话相关
const showRobotDialog = ref(false);
const userMessage = ref("");
const chatMessages = ref<Array<{ type: "user" | "ai"; content: string }>>([
  { type: "ai", content: "你好！我是AI助手，有什么可以帮助你的吗？" },
]);

// 消息容器引用
const messagesContainer = ref<HTMLDivElement | null>(null);

// 可拖拽机器人按钮 - 使用 right/bottom 定位
const robotButton = ref<HTMLDivElement | null>(null);
const robotPosition = ref({ right: 20, bottom: 20 }); // 默认右下角位置
const isDragging = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

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
});

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
  if (!userMessage.value.trim()) return;

  // 添加用户消息
  chatMessages.value.push({ type: "user", content: userMessage.value });
  
  // 立即滚动到底部显示用户消息
  scrollToBottom();
  
  userMessage.value = '';

  // 模拟AI回复
  setTimeout(() => {
    const replies = [
      "感谢您的消息！这是一个示例回复。",
      "我已经收到您的信息，正在处理中...",
      "您好！请问还有其他问题需要帮助吗？",
      "这是一个AI助手的演示功能，实际项目中可以集成真实的AI服务。",
      "我可以帮您解答关于系统使用的问题！",
    ];
    const randomReply = replies[Math.floor(Math.random() * replies.length)];
    chatMessages.value.push({ type: "ai", content: randomReply });
    
    // AI回复后再次滚动到底部
    scrollToBottom();
  }, 1000);
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
    }
  }
};

// 清理事件监听器
onBeforeUnmount(() => {
  document.removeEventListener("mousemove", handleDrag);
  document.removeEventListener("mouseup", stopDrag);
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
</style>