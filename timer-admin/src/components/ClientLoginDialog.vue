<template>
  <el-dialog
    v-model="visible"
    width="420px"
    :show-close="true"
    align-center
    class="client-login-dialog"
    :close-on-click-modal="false"
  >
    <template #header>
      <div class="dialog-title">
        <span class="logo-dot"></span>
        登录 NBA 数据中心
      </div>
    </template>

    <el-form @submit.prevent="handleLogin" :model="form" label-position="top">
      <el-form-item label="用户名">
        <el-input
          v-model="form.username"
          placeholder="请输入用户名"
          clearable
          size="large"
        />
      </el-form-item>
      <el-form-item label="密码">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          show-password
          size="large"
        />
      </el-form-item>
      <div v-if="error" class="error-tip">{{ error }}</div>
      <el-button
        type="primary"
        native-type="submit"
        :loading="loading"
        :disabled="!form.username || !form.password"
        size="large"
        class="login-btn"
      >
        {{ loading ? "登录中..." : "登 录" }}
      </el-button>
    </el-form>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useClientAuth } from "@/composables/useClientAuth";

const { loginDialogVisible, clientLogin, closeLoginDialog } = useClientAuth();

const visible = ref(false);

// 与全局登录弹窗状态双向同步
watch(loginDialogVisible, (val) => {
  visible.value = val;
  if (val) {
    error.value = null;
  }
});
watch(visible, (val) => {
  if (!val && loginDialogVisible.value) {
    closeLoginDialog();
  }
});

const form = ref({ username: "", password: "" });
const loading = ref(false);
const error = ref<string | null>(null);

const handleLogin = async () => {
  error.value = null;
  loading.value = true;
  try {
    await clientLogin(form.value.username, form.value.password);
    ElMessage.success("登录成功！");
    closeLoginDialog();
    form.value.password = "";
  } catch (err: any) {
    if (err?.response?.status === 401) {
      error.value = "用户名或密码错误";
    } else if (err?.response?.status === 400) {
      error.value = "用户名或密码错误";
    } else if (err?.message && !err?.response) {
      error.value = err.message;
    } else {
      error.value = "网络异常，请稍后再试";
    }
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.dialog-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.logo-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316, #ea580c);
  box-shadow: 0 0 8px rgba(249, 115, 22, 0.7);
}

.error-tip {
  margin: -8px 0 12px;
  color: #f56c6c;
  font-size: 13px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
  background: linear-gradient(135deg, #f97316, #ea580c);
  border: none;
  font-weight: 600;
  letter-spacing: 4px;
}

.login-btn:hover,
.login-btn:focus {
  background: linear-gradient(135deg, #fb923c, #f97316);
}
</style>
