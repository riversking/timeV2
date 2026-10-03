import { ref, computed } from "vue";
import { ElMessage } from "element-plus";
import clientHttp, {
  getClientToken,
  getClientUser,
  setClientToken,
  setClientUser,
  clearClientAuth,
} from "@/services/clientHttp";

const API_PREFIX = "/api/user-server";

// 模块级单例：整个应用共享同一份 C 端登录态
const clientToken = ref<string | null>(getClientToken());
const clientUsername = ref<string | null>(getClientUser());
const loginDialogVisible = ref(false);

const isClientLoggedIn = computed(() => !!clientToken.value);

// clientHttp 401 时广播：清状态并提示（多次并发只合并提示）
window.addEventListener("client-unauthorized", () => {
  if (!clientToken.value) return;
  clientToken.value = null;
  clientUsername.value = null;
  ElMessage({ message: "登录已过期，请重新登录", type: "warning", grouping: true });
});

export function useClientAuth() {
  /** C 端登录：独立存储凭证，与后台管理登录互不影响；接口复用 user-server */
  const clientLogin = async (username: string, password: string) => {
    const res = await clientHttp
      .post(`${API_PREFIX}/login`, { username, password })
      .then((r) => r.data);
    if (res.code === 200 && res.data?.token) {
      setClientToken(res.data.token);
      setClientUser(username);
      clientToken.value = res.data.token;
      clientUsername.value = username;
      return true;
    }
    throw new Error(res.message || "登录失败");
  };

  /** C 端退出：清本地登录态即可，不跳后台登录页（user-server 无 logout 端点，与后台管理行为一致） */
  const clientLogout = async () => {
    clearClientAuth();
    clientToken.value = null;
    clientUsername.value = null;
  };

  /** 打开登录弹窗（详情门控处调用） */
  const openLoginDialog = () => {
    loginDialogVisible.value = true;
  };

  const closeLoginDialog = () => {
    loginDialogVisible.value = false;
  };

  return {
    clientToken,
    clientUsername,
    isClientLoggedIn,
    loginDialogVisible,
    clientLogin,
    clientLogout,
    openLoginDialog,
    closeLoginDialog,
  };
}
