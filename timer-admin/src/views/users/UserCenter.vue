<template>
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
            <el-avatar :size="120" :src="userProfile.avatar" class="profile-avatar" />
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
            <el-tag :type="userProfile.status === 'active' ? 'success' : 'info'">
              {{ userProfile.status === 'active' ? '正常' : '禁用' }}
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
          <div class="stat-icon bg-orange">
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
              <el-icon :class="activity.type === 'completed' ? 'text-green' : 'text-blue'">
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
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, onUpdated } from 'vue';
import { 
  Camera,
  Document,
  Check,
  Clock,
  StarFilled,
  Key,
  Bell,
  Edit,
  Lock
} from '@element-plus/icons-vue';
import { 
  ElAvatar, 
  ElUpload, 
  ElDescriptions, 
  ElDescriptionsItem, 
  ElTag, 
  ElCard, 
  ElButton,
  ElMessage
} from 'element-plus';
import ChangePasswordModal from '@/views/users/ResetPasswordModal.vue';

// 用户个人信息
const userProfile = reactive({
  avatar: 'https://cube.elemecdn.com/0/88/03d0d0c4d8ab6e68bf7534a7c8164.png',
  username: '张三',
  realName: '张三丰',
  role: '系统管理员',
  employeeId: 'EMP001',
  email: 'zhangsan@example.com',
  phone: '13800138000',
  department: '技术部',
  joinDate: '2023-01-15',
  lastLogin: '2024-05-15 14:30:22',
  status: 'active'
});

// 最近活动
const activities = ref([
  { title: '完成了项目报告', type: 'completed', time: '2分钟前' },
  { title: '创建了新任务', type: 'created', time: '1小时前' },
  { title: '参加了团队会议', type: 'attended', time: '昨天' },
  { title: '提交了代码更新', type: 'completed', time: '昨天' },
  { title: '审核了PR请求', type: 'completed', time: '2天前' }
]);

// 模态框控制
const showChangePasswordModal = ref(false);

// 头像上传
const handleAvatarSuccess = (response: any, file: any) => {
  userProfile.avatar = URL.createObjectURL(file.raw);
};

const beforeAvatarUpload = (file: any) => {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png';
  const isLt2M = file.size / 1024 / 1024 < 2;

  if (!isJPG) {
    ElMessage.error('头像图片只能是 JPG/PNG 格式!');
  }
  if (!isLt2M) {
    ElMessage.error('头像图片大小不能超过 2MB!');
  }
  return isJPG && isLt2M;
};

// 操作处理
const openChangePasswordModal = () => {
  showChangePasswordModal.value = true;
};

const openNotificationSettings = () => {
  ElMessage.info('通知设置功能开发中...');
};

const openProfileEdit = () => {
  ElMessage.info('编辑资料功能开发中...');
};

const openSecuritySettings = () => {
  ElMessage.info('安全设置功能开发中...');
};

const handlePasswordChanged = () => {
  ElMessage.success('密码修改成功');
};

// 页面加载后触发重排，确保高度计算正确
onMounted(async () => {
  await nextTick();
  // 强制浏览器重新计算布局
  setTimeout(() => {
    window.dispatchEvent(new Event('resize'));
  }, 100);
});

// 页面更新后再次触发重排，确保高度计算正确
onUpdated(async () => {
  await nextTick();
  setTimeout(() => {
    window.dispatchEvent(new Event('resize'));
  }, 100);
});
</script>

<style scoped>
.profile-container {
  padding: 20px;
  background: linear-gradient(135deg, #0f172a, #1e293b);
  min-height: 100vh;
  color: #e2e8f0;
  padding-bottom: 20px;
  overflow-x: hidden;
}

.profile-card {
  background: rgba(30, 41, 59, 0.8);
  border-radius: 16px;
  padding: 30px;
  margin-bottom: 20px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(76, 201, 240, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
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
  padding: 20px 0;
}

.stats-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
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
}

.activity-card {
  background: rgba(30, 41, 59, 0.8);
  border-radius: 12px;
  border: 1px solid rgba(76, 201, 240, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.activity-list {
  max-height: 400px;
  overflow-y: auto;
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
    padding-bottom: 10px;
  }
}
</style>