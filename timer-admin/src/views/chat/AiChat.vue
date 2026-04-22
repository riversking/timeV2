<template>
  <!-- 可拖拽的机器人按钮 -->
  <div 
    ref="robotButton"
    class="draggable-robot-button"
    :style="{ left: robotPosition.x + 'px', top: robotPosition.y + 'px' }"
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
    <div style="height: 400px; display: flex; flex-direction: column;">
      <div style="flex: 1; overflow-y: auto; padding: 10px; background: #f5f7fa; border-radius: 8px; margin-bottom: 10px;">
        <div v-for="(msg, index) in chatMessages" :key="index" style="margin-bottom: 10px;">
          <div v-if="msg.type === 'user'" style="text-align: right;">
            <el-tag type="success" size="small">我</el-tag>
            <div style="display: inline-block; background: #e6f7ff; padding: 8px 12px; border-radius: 12px; margin-top: 5px; max-width: 80%;">
              {{ msg.content }}
            </div>
          </div>
          <div v-else style="text-align: left;">
            <el-tag type="primary" size="small">AI助手</el-tag>
            <div style="display: inline-block; background: #f0f9eb; padding: 8px 12px; border-radius: 12px; margin-top: 5px; max-width: 80%;">
              {{ msg.content }}
            </div>
          </div>
        </div>
      </div>
      <div style="display: flex; gap: 10px;">
        <el-input
          v-model="userMessage"
          placeholder="请输入消息..."
          @keyup.enter="sendMessage"
          style="flex: 1;"
        />
        <el-button type="primary" @click="sendMessage">发送</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from "vue";
import { Stamp } from "@element-plus/icons-vue";

// 机器人对话相关
const showRobotDialog = ref(false);
const userMessage = ref("");
const chatMessages = ref<Array<{type: 'user' | 'ai', content: string}>>([
  { type: 'ai', content: '你好！我是AI助手，有什么可以帮助你的吗？' }
]);

// 可拖拽机器人按钮
const robotButton = ref<HTMLDivElement | null>(null);
const robotPosition = ref({ x: 20, y: 100 });
const isDragging = ref(false);
const dragOffset = ref({ x: 0, y: 0 });

// 组件挂载时恢复位置
onMounted(() => {
  // 从 localStorage 恢复机器人位置
  const savedPosition = localStorage.getItem('robotPosition');
  if (savedPosition) {
    try {
      const position = JSON.parse(savedPosition);
      robotPosition.value = position;
    } catch (e) {
      console.error('Failed to parse robot position from localStorage');
    }
  }
});

// 发送消息
const sendMessage = () => {
  if (!userMessage.value.trim()) return;
  
  // 添加用户消息
  chatMessages.value.push({ type: 'user', content: userMessage.value });
  
  // 模拟AI回复
  setTimeout(() => {
    const replies = [
      '感谢您的消息！这是一个示例回复。',
      '我已经收到您的信息，正在处理中...',
      '您好！请问还有其他问题需要帮助吗？',
      '这是一个AI助手的演示功能，实际项目中可以集成真实的AI服务。',
      '我可以帮您解答关于系统使用的问题！'
    ];
    const randomReply = replies[Math.floor(Math.random() * replies.length)];
    chatMessages.value.push({ type: 'ai', content: randomReply });
  }, 1000);
  
  userMessage.value = '';
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
      y: e.clientY - rect.top
    };
  }
  
  // 添加全局事件监听器
  document.addEventListener('mousemove', handleDrag);
  document.addEventListener('mouseup', stopDrag);
};

const handleDrag = (e: MouseEvent) => {
  if (!isDragging.value) return;
  
  // 计算新的位置
  const newX = e.clientX - dragOffset.value.x;
  const newY = e.clientY - dragOffset.value.y;
  
  // 限制在视口范围内
  const maxX = window.innerWidth - 60; // 按钮宽度为60px
  const maxY = window.innerHeight - 60; // 按钮高度为60px
  
  robotPosition.value.x = Math.max(0, Math.min(newX, maxX));
  robotPosition.value.y = Math.max(0, Math.min(newY, maxY));
};

const stopDrag = () => {
  isDragging.value = false;
  document.removeEventListener('mousemove', handleDrag);
  document.removeEventListener('mouseup', stopDrag);
  
  // 保存位置到 localStorage
  localStorage.setItem('robotPosition', JSON.stringify(robotPosition.value));
};

// 切换机器人对话框
const toggleRobotDialog = () => {
  if (!isDragging.value) {
    showRobotDialog.value = !showRobotDialog.value;
  }
};

// 清理事件监听器
onBeforeUnmount(() => {
  document.removeEventListener('mousemove', handleDrag);
  document.removeEventListener('mouseup', stopDrag);
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
  transition: transform 0.2s, box-shadow 0.2s;
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