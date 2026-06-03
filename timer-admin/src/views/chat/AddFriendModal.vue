<template>
  <el-dialog
    v-model="visible"
    title="添加好友"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="add-friend-container">
      <!-- 搜索框 -->
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="请输入用户名或用户ID搜索"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleClear"
        >
          <template #append>
            <el-button @click="handleSearch" :loading="searching">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>

      <!-- 搜索结果列表 -->
      <div class="user-list">
        <el-scrollbar height="400px">
          <div v-if="loading" class="loading-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>搜索中...</span>
          </div>

          <div v-else-if="searched && userList.length === 0" class="empty-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>未找到相关用户</span>
          </div>

          <div v-else-if="!searched" class="empty-tip">
            <el-icon><Search /></el-icon>
            <span>请输入关键词搜索用户</span>
          </div>

          <div v-for="user in userList" :key="user.userId" class="user-item">
            <div class="user-info">
              <el-avatar size="medium" :src="user.avatar">
                {{ user.username?.charAt(0) }}
              </el-avatar>
              <div class="user-detail">
                <div class="username">{{ user.username }}</div>
                <div class="user-id">ID: {{ user.userId }}</div>
              </div>
            </div>
            <el-button
              type="primary"
              size="small"
              :loading="addingUserId === user.userId"
              :disabled="isFriend(user.userId)"
              @click="handleAddFriend(user)"
            >
              {{ isFriend(user.userId) ? "已添加" : "添加" }}
            </el-button>
          </div>
        </el-scrollbar>
      </div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { Search, InfoFilled, Loading } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  getUserPage
} from "@/api/user";
interface User {
  userId: string;
  username: string;
  avatar?: string;
}

const props = defineProps<{
  modelValue: boolean;
  friendList: Array<{ userId: string }>;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  "add-friend": [data: { userId: string; remark: string }];
}>();

const visible = ref(false);
const searchKeyword = ref("");
const searching = ref(false);
const loading = ref(false);
const searched = ref(false);
const addingUserId = ref("");
const userList = ref<User[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      resetForm();
    }
  },
);

watch(visible, (val) => {
  emit("update:modelValue", val);
});

const resetForm = () => {
  searchKeyword.value = "";
  userList.value = [];
  searched.value = false;
  addingUserId.value = "";
};

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning("请输入搜索关键词");
    return;
  }

  loading.value = true;
  searched.value = false;

  try {
    const response = await getUserPage({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      username: searchKeyword.value,
    });
    console.log("获取用户数据成功:", response);
    if (response.code === 200) {
      userList.value = response.data.users || [];
      total.value = response.data.total || 0;
    } else {
      ElMessage.error(response.message || "获取用户数据失败");
    }

    searched.value = true;
  } catch (error) {
    console.error("搜索用户失败:", error);
    ElMessage.error("搜索用户失败");
  } finally {
    loading.value = false;
  }
};

const handleClear = () => {
  searchKeyword.value = "";
  userList.value = [];
  searched.value = false;
};

const handleAddFriend = async (user: User) => {
  addingUserId.value = user.userId;

  try {
    emit("add-friend", {
      userId: user.userId,
      remark: "",
    });

    ElMessage.success("好友请求已发送");
  } catch (error) {
    console.error("添加好友失败:", error);
    ElMessage.error("添加好友失败");
  } finally {
    addingUserId.value = "";
  }
};

const isFriend = (userId: string) => {
  return props.friendList.some((friend) => friend.userId === userId);
};

const handleClose = () => {
  visible.value = false;
};
</script>

<style scoped>
.add-friend-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-box {
  margin-bottom: 8px;
}

.user-list {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
}

.loading-tip,
.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
}

.user-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
}

.user-item:last-child {
  border-bottom: none;
}

.user-item:hover {
  background-color: #f5f7fa;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.user-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.username {
  font-weight: 500;
  color: #303133;
}

.user-id {
  font-size: 12px;
  color: #909399;
}
</style>
