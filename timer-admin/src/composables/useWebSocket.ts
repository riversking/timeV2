// composables/useWebSocket.ts
import { ref, onBeforeUnmount } from "vue";
import { createTicket } from "../api/im";

export default function useWebSocket(url: string) {
  const messages = ref<any[]>([]);
  const isConnected = ref(false);
  const socket = ref<WebSocket | null>(null);
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  const MAX_RETRY = 5;
  let retryCount = 0;

  const connect = async () => {
    // 安全关闭旧连接（如果存在）
    if (socket.value) {
      socket.value.close(1000, "reconnecting");
      socket.value = null;
    }
    const res = await createTicket();
    if (res.code !== 200) {
      return;
    }
    const ticket = res.data.ticket;
    const separator = url.includes("?") ? "&" : "?";
    const wsUrl = `${url}${separator}ticket=${ticket}`;
    socket.value = new WebSocket(wsUrl);
    socket.value.onopen = () => {
      isConnected.value = true;
      retryCount = 0;
    };
    socket.value.onmessage = (event) => {
      console.log("收到消息:", event.data);
      try {
        messages.value.push(JSON.parse(event.data));
      } catch (e) {
        console.error("解析消息失败:", e);
      }
    };
    socket.value.onclose = (event) => {
      isConnected.value = false;
      // 仅非主动关闭时重连
      if (event.code !== 1000 && retryCount < MAX_RETRY) {
        retryCount++;
        reconnectTimer = setTimeout(connect, 2000 * retryCount);
      }
    };

    socket.value.onerror = (error) => {
      console.error("WebSocket 错误:", error);
      socket.value?.close(); // 安全关闭错误连接
    };
  };

  const send = (topic: string, payload: any) => {
    if (socket.value?.readyState === WebSocket.OPEN) {
      const envelope = {
        topic: topic,
        msgId: crypto.randomUUID(), // 生成唯一消息ID
        payload: payload,
      };
      socket.value.send(JSON.stringify(envelope));
    }
  };

  // 安全关闭方法（关键修复点）
  const safeClose = (code = 1000, reason = "manual close") => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }

    if (socket.value) {
      socket.value.close(code, reason);
      socket.value = null; // 显式置空
    }
  };

  // 自动连接
  connect();

  // 组件卸载时安全清理
  onBeforeUnmount(() => {
    safeClose(); // 使用安全关闭
  });

  return {
    messages,
    isConnected,
    send,
    close: safeClose, // 暴露给组件使用
    connect,
  };
}
