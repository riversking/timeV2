<template>
  <el-container
    style="height: 100vh; background: linear-gradient(135deg, #0f172a, #1e293b)"
  >
    <!-- 顶部头部 - 完全复用Home.vue的头部 -->
    <el-header
      style="
        background: linear-gradient(135deg, #0f172a, #1e293b);
        color: #e2e8f0;
        display: flex;
        align-items: center;
        padding: 0 20px;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
        border-bottom: 1px solid #2d3748;
        position: relative;
        height: 60px;
        flex-shrink: 0;
      "
    >
      <div style="display: flex; align-items: center; flex: 1">
        <el-icon style="font-size: 24px; margin-right: 15px; color: #4cc9f0">
        </el-icon>
        <span
          style="
            font-family: 'Orbitron', 'Arial', sans-serif;
            font-size: 20px;
            font-weight: 700;
            letter-spacing: 1px;
            text-shadow: 0 0 15px rgba(76, 201, 240, 0.7),
              0 0 30px rgba(106, 137, 247, 0.5);
            background: linear-gradient(90deg, #4cc9f0, #6a89f7);
            background-clip: text;
            color: transparent;
            margin-right: 12px;
            line-height: 36px;
            height: 36px;
          "
          >timer admin</span
        >
      </div>

      <div style="display: flex; align-items: center; gap: 15px">
        <el-tooltip content="搜索" placement="bottom">
          <el-input
            v-model="searchQuery"
            placeholder="搜索..."
            size="small"
            style="width: 200px"
            :suffix-icon="Search"
          />
        </el-tooltip>

        <el-dropdown>
          <span style="cursor: pointer; display: flex; align-items: center">
            <el-avatar
              :size="32"
              :src="userProfile.avatar"
            />
            <span style="margin-left: 8px; color: #e2e8f0">{{ userProfile.username }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="showChangePasswordModal = true">修改密码</el-dropdown-item>
              <el-dropdown-item @click="goBack">返回首页</el-dropdown-item>
              <el-dropdown-item>设置</el-dropdown-item>
              <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- 科技感光效 -->
      <div
        style="
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 2px;
          background: linear-gradient(90deg, #4cc9f0, #6a89f7, #4cc9f0);
          opacity: 0.7;
          box-shadow: 0 0 15px rgba(76, 201, 240, 0.5);
        "
      ></div>
    </el-header>

    <!-- 可滚动的主内容区域 -->
    <el-container style="flex: 1; min-height: 0;">
      <el-main
        style="
          background: transparent;
          color: #e2e8f0;
          padding: 20px;
          height: 100%;
          overflow: auto;
        "
      >
        <div class="profile-container">
          <!-- 个人资料卡片 -->
          <div class="profile-card">
            <div class="profile-header">
              <div class="avatar-section">
                <el-upload
                  class="avatar-uploader"
                  action="/api/upload"
                  :show-file-list="false"
                  :on-success="handleAvatarSuccess"
                  :before-upload="beforeAvatarUpload"
                >
                  <el-avatar
                    :size="120"
                    :src="userProfile.avatar"
                    class="profile-avatar"
                  />
                  <div class="avatar-overlay">
                    <el-icon><Camera /></el-icon>
                  </div>
                </el-upload>
                <h2 class="username">{{ userProfile.username }}</h2>
                <p class="user-role">{{ userProfile.role }}</p>
              </div>
            </div>

            <div class="profile-content">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="姓名">
                  <span>{{ userProfile.realName }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="工号">
                  <span>{{ userProfile.employeeId }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="邮箱">
                  <span>{{ userProfile.email }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="手机号">
                  <span>{{ userProfile.phone }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="部门">
                  <span>{{ userProfile.department }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="入职日期">
                  <span>{{ userProfile.joinDate }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="最后登录">
                  <span>{{ userProfile.lastLogin }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="账户状态">
                  <el-tag
                    :type="userProfile.status === 'active' ? 'success' : 'info'"
                  >
                    {{ userProfile.status === "active" ? "正常" : "禁用" }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>

          <!-- 统计卡片 -->
          <div class="stats-container">
            <el-card class="stat-card" shadow="hover">
              <div class="stat-content">
                <div class="stat-icon bg-blue">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="stat-info">
                  <h3>24</h3>
                  <p>本月任务数</p>
                </div>
              </div>
            </el-card>

            <el-card class="stat-card" shadow="hover">
              <div class="stat-content">
                <div class="stat-icon bg-green">
                  <el-icon><Check /></el-icon>
                </div>
                <div class="stat-info">
                  <h3>22</h3>
                  <p>已完成任务</p>
                </div>
              </div>
            </el-card>

            <el-card class="stat-card" shadow="hover">
              <div class="stat-content">
                <div class="stat-icon bg-red">
                  <el-icon><Clock /></el-icon>
                </div>
                <div class="stat-info">
                  <h3>98%</h3>
                  <p>完成率</p>
                </div>
              </div>
            </el-card>

            <el-card class="stat-card" shadow="hover">
              <div class="stat-content">
                <div class="stat-icon bg-purple">
                  <el-icon><StarFilled /></el-icon>
                </div>
                <div class="stat-info">
                  <h3>4.8</h3>
                  <p>平均评分</p>
                </div>
              </div>
            </el-card>
          </div>

          <!-- 活动卡片 -->
          <div class="activity-container">
            <el-card class="activity-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <span>最近活动</span>
                  <el-button type="text">查看全部</el-button>
                </div>
              </template>
              <div class="activity-list">
                <div
                  v-for="(activity, index) in activities"
                  :key="index"
                  class="activity-item"
                >
                  <div class="activity-icon">
                    <el-icon
                      :class="
                        activity.type === 'completed' ? 'text-green' : 'text-blue'
                      "
                    >
                      <Check v-if="activity.type === 'completed'" />
                      <Document v-else-if="activity.type === 'created'" />
                      <Clock v-else />
                    </el-icon>
                  </div>
                  <div class="activity-content">
                    <p>{{ activity.title }}</p>
                    <span class="activity-time">{{ activity.time }}</span>
                  </div>
                </div>
              </div>
            </el-card>

            <el-card class="activity-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <span>快速设置</span>
                  <el-button type="text">更多</el-button>
                </div>
              </template>
              <div class="settings-grid">
                <el-button type="primary" plain @click="openChangePasswordModal">
                  <el-icon><Key /></el-icon>
                  修改密码
                </el-button>
                <el-button type="success" plain @click="openNotificationSettings">
                  <el-icon><Bell /></el-icon>
                  通知设置
                </el-button>
                <el-button type="warning" plain @click="openProfileEdit">
                  <el-icon><Edit /></el-icon>
                  编辑资料
                </el-button>
                <el-button type="info" plain @click="openSecuritySettings">
                  <el-icon><Lock /></el-icon>
                  安全设置
                </el-button>
              </div>
            </el-card>
          </div>
          
          <!-- 修改密码模态框 -->
          <ChangePasswordModal
            v-model="showChangePasswordModal"
            @change-success="handlePasswordChanged"
          />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import {
  Camera,
  Document,
  Check,
  Clock,
  StarFilled,
  Key,
  Bell,
  Edit,
  Lock,
  Search
} from "@element-plus/icons-vue";
import {
  ElAvatar,
  ElUpload,
  ElDescriptions,
  ElDescriptionsItem,
  ElTag,
  ElCard,
  ElButton,
  ElMessage,
  ElContainer,
  ElHeader,
  ElMain,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElTooltip,
  ElInput,
  ElIcon
} from "element-plus";
import ChangePasswordModal from "@/views/users/ResetPasswordModal.vue";

const router = useRouter();
const userStore = useUserStore();

// 用户个人信息
const userProfile = reactive({
  avatar: "https://cube.elemecdn.com/0/88/03d0d0c4d8ab6e68bf7534a7c8164.png",
  username: "张三",
  realName: "张三丰",
  role: "系统管理员",
  employeeId: "EMP001",
  email: "zhangsan@example.com",
  phone: "13800138000",
  department: "技术部",
  joinDate: "2023-01-15",
  lastLogin: "2024-05-15 14:30:22",
  status: "active",
});

// 最近活动
const activities = ref([
  { title: "完成了项目报告", type: "completed", time: "2分钟前" },
  { title: "创建了新任务", type: "created", time: "1小时前" },
  { title: "参加了团队会议", type: "attended", time: "昨天" },
  { title: "提交了代码更新", type: "completed", time: "昨天" },
  { title: "审核了PR请求", type: "completed", time: "2天前" },
]);

// 模态框控制
const showChangePasswordModal = ref(false);
const searchQuery = ref("");

// 头像上传
const handleAvatarSuccess = (response: any, file: any) => {
  userProfile.avatar = URL.createObjectURL(file.raw);
};

const beforeAvatarUpload = (file: any) => {
  const isJPG = file.type === "image/jpeg" || file.type === "image/png";
  const isLt2M = file.size / 1024 / 1024 < 2;

  if (!isJPG) {
    ElMessage.error("头像图片只能是 JPG/PNG 格式!");
  }
  if (!isLt2M) {
    ElMessage.error("头像图片大小不能超过 2MB!");
  }
  return isJPG && isLt2M;
};

// 操作处理
const openChangePasswordModal = () => {
  showChangePasswordModal.value = true;
};

const openNotificationSettings = () => {
  ElMessage.info("通知设置功能开发中...");
};

const openProfileEdit = () => {
  ElMessage.info("编辑资料功能开发中...");
};

const openSecuritySettings = () => {
  ElMessage.info("安全设置功能开发中...");
};

const handlePasswordChanged = () => {
  ElMessage.success("密码修改成功");
};

const goBack = () => {
  router.push('/');
};

const logout = async () => {
  try {
    localStorage.removeItem("token");
    userStore.setToken("");
    userStore.setMenuRoutes([]);
    await router.replace("/login");
  } catch (error) {
    console.error(error);
  }
};

// 页面加载后获取用户信息
onMounted(async () => {
  // 这里可以调用API获取真实的用户信息
  // const res = await getCurrentUser();
  // Object.assign(userProfile, res.data);
});
</script>

<style scoped>
/* 创建一个弹性布局容器 */
.profile-container {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 20px;
  background: transparent;
  color: #e2e8f0;
  box-sizing: border-box;
}

.profile-card {
  background: rgba(30, 41, 59, 0.8);
  border-radius: 16px;
  padding: 30px;
  margin-bottom: 20px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(76, 201, 240, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  flex-shrink: 0;
}

.profile-header {
  text-align: center;
  margin-bottom: 30px;
  position: relative;
}

.avatar-section {
  position: relative;
  display: inline-block;
}

.profile-avatar {
  border: 4px solid #4cc9f0;
  transition: all 0.3s ease;
}

.profile-avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 0 20px rgba(76, 201, 240, 0.5);
}

.avatar-overlay {
  position: absolute;
  bottom: 0;
  right: 0;
  background: #4cc9f0;
  border-radius: 50%;
  padding: 8px;
  display: none;
}

.avatar-section:hover .avatar-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.username {
  font-size: 24px;
  font-weight: bold;
  margin: 15px 0 5px 0;
  color: #e2e8f0;
  text-shadow: 0 0 10px rgba(76, 201, 240, 0.5);
}

.user-role {
  font-size: 16px;
  color: #94a3b8;
  margin: 0;
}

.profile-content {
  flex: 1;
  overflow-y: auto;
  max-height: none;
  height: auto;
}

.stats-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.stat-card {
  background: rgba(30, 41, 59, 0.8);
  border-radius: 12px;
  border: 1px solid rgba(76, 201, 240, 0.1);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
  border-color: rgba(76, 201, 240, 0.3);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 15px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: 24px;
  color: white;
}

.bg-blue {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}

.bg-green {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.bg-orange {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}

.bg-purple {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}

.stat-info h3 {
  font-size: 24px;
  margin: 0 0 5px 0;
  color: #e2e8f0;
}

.stat-info p {
  font-size: 14px;
  margin: 0;
  color: #94a3b8;
}

.activity-container {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
  flex: 1;
  min-height: 0;
}

.activity-card {
  background: rgba(30, 41, 59, 0.8);
  border-radius: 12px;
  border: 1px solid rgba(76, 201, 240, 0.1);
  display: flex;
  flex-direction: column;
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.activity-list {
  flex: 1;
  overflow-y: auto;
  max-height: none;
  min-height: 0;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  padding: 15px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(76, 201, 240, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
}

.activity-content p {
  margin: 0 0 5px 0;
  color: #e2e8f0;
}

.activity-time {
  font-size: 12px;
  color: #94a3b8;
}

.text-green {
  color: #10b981;
}

.text-blue {
  color: #3b82f6;
}

.settings-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

/* 确保页面可以滚动 */
:deep(.el-descriptions__body) {
  background-color: transparent;
  border: 1px solid rgba(76, 201, 240, 0.2);
}

@media (max-width: 768px) {
  .activity-container {
    grid-template-columns: 1fr;
  }

  .stats-container {
    grid-template-columns: 1fr;
  }

  .profile-container {
    padding: 10px;
  }
}
</style>
<style>
/* 关键修复：设置 #app 高度为 100vh */
#app {
  height: 100vh;
}
html,
body {
  height: 100%;
  margin: 0;
  overflow-y: auto;
  background-color: #0f172a; /* 例如，添加一个与组件风格匹配的深色背景 */
  scroll-behavior: smooth; /* 平滑滚动 */
}
.maxai-client--chat-hub--container,
.use-chat-gpt-ai--MuiStack-root.maxai-client--chat-hub--container {
  display: none !important;
  pointer-events: none !important;
}
</style>
