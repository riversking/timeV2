<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">用户登录</h2>
      </template>

      <el-form @submit.prevent="handleLogin" :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            clearable
            required
          />
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            required
          />
        </el-form-item>

        <el-form-item v-if="error">
          <el-alert :title="error" type="error" show-icon :closable="false" />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            style="width: 100%"
            :disabled="!form.username || !form.password"
          >
            {{ loading ? "登录中..." : "登录" }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { login } from "@/api/auth";
import { ElMessage, ElLoading } from "element-plus";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";

// 获取路由实例
const router = useRouter();
// 获取用户状态管理
const userStore = useUserStore();

// 表单数据
const form = ref({
  username: "",
  password: "",
});

// 状态
const loading = ref(false);
const error = ref<string | null>(null);

// 提交登录
const handleLogin = async () => {
  error.value = null;
  loading.value = true;

  try {
    const res = await login({
      username: form.value.username,
      password: form.value.password,
    });
    if (res.code === 200) {
      // 1. 保存token
      const token = res.data.token;
      // 2. 保存用户信息到store
      userStore.setToken(token);
      // 4. 跳转到首页
      ElMessage.success("登录成功！");
      await router.replace({ path: "/home", replace: true });
    } else {
      error.value = "登录失败：未返回有效 token";
    }
  } catch (err: any) {
    console.error("登录请求失败:", err);
    if (err.response?.status === 401) {
      error.value = "用户名或密码错误";
    } else if (err.code === "ECONNABORTED") {
      error.value = "请求超时，请检查网络";
    } else {
      error.value = "网络异常，请稍后再试";
    }
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7fa;
}

.login-card {
  width: 400px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.login-title {
  margin: 0;
  font-size: 24px;
  text-align: center;
  color: #303133;
}
</style>
