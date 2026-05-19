<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">用户登录</h2>
      </template>

      <!-- 账号密码登录 -->
      <div v-if="loginType === 'password'" class="password-login">
        <el-form
          @submit.prevent="handleLogin"
          :model="form"
          label-position="top"
        >
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

        <div class="login-switch">
          <span>没有账号？</span>
          <el-link type="primary" @click="switchLoginType('qrcode')"
            >扫码登录</el-link
          >
        </div>
      </div>

      <!-- 扫码登录 -->
      <div v-else-if="loginType === 'qrcode'" class="qrcode-login">
        <div class="qrcode-container">
          <div v-if="qrCodeId" class="qrcode-wrapper">
            <div ref="qrcodeContainer" class="qrcode-svg"></div>
            <div class="qrcode-tip">请使用手机APP扫描二维码</div>
            <div v-if="scanStatus === 'SCANNED'" class="scan-status success">
              {{
                scannedUser
                  ? `已扫码用户：${scannedUser.username}`
                  : "已扫码，请在手机上确认登录"
              }}
            </div>
            <div v-else-if="scanStatus === 'EXPIRED'" class="scan-status error">
              二维码已过期，请重新获取
            </div>
          </div>
          <div v-else class="qrcode-loading">
            <el-skeleton :rows="4" animated />
          </div>
        </div>

        <div class="login-switch">
          <span>使用账号密码登录？</span>
          <el-link type="primary" @click="switchLoginType('password')"
            >返回</el-link
          >
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import { login, getQrCode } from "@/api/auth";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";

// 声明 qrcode 模块类型（如果 @types/qrcode 不可用）
declare module "qrcode" {
  export function toString(
    text: string,
    options: any,
    callback: (error: Error | null, svg: string) => void,
  ): void;
}

// 获取路由实例
const router = useRouter();
// 获取用户状态管理
const userStore = useUserStore();

// 登录类型：password 或 qrcode
const loginType = ref<"password" | "qrcode">("password");

// 表单数据
const form = ref({
  username: "",
  password: "",
});

// 状态
const loading = ref(false);
const error = ref<string | null>(null);

// 扫码登录相关
const qrCodeId = ref<string | null>(null);
const scanStatus = ref<"WAITING" | "SCANNED" | "CONFIRMED" | "EXPIRED">(
  "WAITING",
);
const scannedUser = ref<{ userId: string; username: string } | null>(null);
const ws = ref<WebSocket | null>(null);
const qrcodeContainer = ref<HTMLDivElement | null>(null);

// 切换登录类型
const switchLoginType = (type: "password" | "qrcode") => {
  loginType.value = type;
  if (type === "qrcode") {
    generateQrCode();
  } else {
    // 清理扫码相关的WebSocket和数据
    closeWebSocket();
    qrCodeId.value = null;
    scanStatus.value = "WAITING";
    scannedUser.value = null;
    // 清空二维码容器
    if (qrcodeContainer.value) {
      qrcodeContainer.value.innerHTML = "";
    }
  }
};

// 生成二维码
const generateQrCode = async () => {
  try {
    const res = await getQrCode();
    if (res.code === 200) {
      qrCodeId.value = res.data.qrCodeId;
      scanStatus.value = "WAITING";
      scannedUser.value = null;

      // 延迟生成二维码，确保DOM已更新
      nextTick(() => {
        createQRCode();
        connectWebSocket();
      });
    } else {
      ElMessage.error("获取二维码失败");
    }
  } catch (err) {
    console.error("获取二维码失败:", err);
    ElMessage.error("获取二维码失败，请稍后重试");
  }
};

// 创建二维码
const createQRCode = () => {
  if (!qrCodeId.value || !qrcodeContainer.value) return;

  try {
    // 生成二维码内容（扫码链接）
    const protocol = window.location.protocol === "https:" ? "https:" : "http:";
    const host = window.location.host;
    const qrContent = `${protocol}//${host}/scan/${qrCodeId.value}`;

    // 清空容器
    qrcodeContainer.value.innerHTML = "";

    // 使用 qrcode 库生成 SVG 二维码
    import("qrcode")
      .then((module) => {
        const QRCode = module.default;
        QRCode.toString(
          qrContent,
          { type: "svg", width: 200, height: 200 },
          (err: Error | null, svg: string) => {
            if (err) {
              console.error("生成二维码失败:", err);
              qrcodeContainer.value!.innerHTML =
                '<div style="color: red; text-align: center;">二维码生成失败</div>';
            } else {
              qrcodeContainer.value!.innerHTML = svg;
            }
          },
        );
      })
      .catch((err) => {
        console.error("加载qrcode库失败:", err);
        qrcodeContainer.value!.innerHTML =
          '<div style="color: red; text-align: center;">二维码生成失败</div>';
      });
  } catch (err) {
    console.error("生成二维码失败:", err);
    if (qrcodeContainer.value) {
      qrcodeContainer.value.innerHTML =
        '<div style="color: red; text-align: center;">二维码生成失败</div>';
    }
  }
};

// 连接WebSocket
const connectWebSocket = () => {
  if (!qrCodeId.value) return;

  // 关闭现有连接
  closeWebSocket();

  // 创建新的WebSocket连接（不需要用户认证）
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const wsHost = `${protocol}//${window.location.host}`;
  const wsUrl = `${wsHost}/websocket/user-server/ws/qrcode?qrCodeId=${qrCodeId.value}`;

  ws.value = new WebSocket(wsUrl);

  ws.value.onopen = () => {
    console.log("WebSocket连接已建立");
  };

  ws.value.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data);
      console.log("收到扫码状态消息:", message);

      // 处理扫码状态更新
      if (message.status === "SCANNED") {
        scanStatus.value = "SCANNED";
        scannedUser.value = message.data;
      } else if (message.status === "CONFIRMED") {
        // 扫码登录成功
        handleQrCodeLoginSuccess(message.data);
      } else if (message.status === "EXPIRED") {
        scanStatus.value = "EXPIRED";
        // 自动重新生成二维码
        setTimeout(() => {
          if (loginType.value === "qrcode") {
            generateQrCode();
          }
        }, 3000);
      }
    } catch (e) {
      console.error("解析WebSocket消息失败:", e);
    }
  };

  ws.value.onclose = (event) => {
    console.log("WebSocket连接已关闭:", event.code, event.reason);
    // 如果不是主动关闭且不是扫码成功的情况，尝试重连
    if (event.code !== 1000 && scanStatus.value !== "CONFIRMED") {
      setTimeout(() => {
        if (loginType.value === "qrcode" && qrCodeId.value) {
          connectWebSocket();
        }
      }, 3000);
    }
  };

  ws.value.onerror = (error) => {
    console.error("WebSocket错误:", error);
  };
};

// 关闭WebSocket
const closeWebSocket = () => {
  if (ws.value) {
    ws.value.close(1000, "manual close");
    ws.value = null;
  }
};

// 处理扫码登录成功
const handleQrCodeLoginSuccess = (loginData: any) => {
  closeWebSocket();
  // 保存token和用户信息
  userStore.setToken(loginData.token);
  userStore.setRefreshToken(loginData.refreshToken);
  ElMessage.success("扫码登录成功！");
  router.replace({ path: "/home", replace: true });
};

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
      const refreshToken = res.data.refreshToken;
      // 2. 保存用户信息到store
      userStore.setToken(token);
      userStore.setRefreshToken(refreshToken);
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

// 组件卸载时清理WebSocket
onUnmounted(() => {
  closeWebSocket();
});
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

.password-login,
.qrcode-login {
  padding: 20px 0;
}

.qrcode-container {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 20px;
}

.qrcode-wrapper {
  text-align: center;
  position: relative;
}

.qrcode-svg {
  width: 200px;
  height: 200px;
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px;
  background: white;
}

.qrcode-tip {
  margin-top: 10px;
  color: #606266;
  font-size: 14px;
}

.scan-status {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: bold;
}

.scan-status.success {
  background-color: #f0f9eb;
  color: #67c23a;
}

.scan-status.error {
  background-color: #fef0f0;
  color: #f56c6c;
}

.qrcode-loading {
  width: 200px;
  height: 200px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.login-switch {
  text-align: center;
  margin-top: 20px;
  color: #606266;
  font-size: 14px;
}

.login-switch .el-link {
  margin-left: 8px;
}
</style>