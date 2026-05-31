// composables/useWebSocket.ts
import { ref, onBeforeUnmount } from "vue";
import { createTicket } from "../api/im";

const generateUUID = (): string => {
  // 优先尝试使用原生 API (在 https 或 localhost 下)
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // 降级方案：使用 Math.random 生成标准 UUID v4 格式
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};


export default function useWebSocket(url: string) {
  const messages = ref<any[]>([]);
  const isConnected = ref(false);
  const socket = ref<WebSocket | null>(null);
  
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  const MAX_RETRY = 5;
  let retryCount = 0;
  
  // 🌟 核心修复 1：增加连接锁，防止并发建立多个连接
  let isConnecting = false; 

  const connect = async () => {
    // 🌟 核心修复 2：如果正在连接中，或者已经连接成功，直接拦截
    if (isConnecting || (socket.value && socket.value.readyState === WebSocket.OPEN)) {
      console.warn("WebSocket 正在连接或已连接，跳过重复调用");
      return;
    }

    isConnecting = true; // 加锁

    // 安全关闭旧连接（如果存在）
    if (socket.value) {
      socket.value.close(1000, "reconnecting");
      socket.value = null;
    }

    try {
      const res = await createTicket();
      if (res.code !== 200) {
        isConnecting = false; // 失败解锁
        return;
      }
      
      const ticket = res.data.ticket;
      const separator = url.includes("?") ? "&" : "?";
      const wsUrl = `${url}${separator}ticket=${ticket}`;
      
      socket.value = new WebSocket(wsUrl);
      
      socket.value.onopen = () => {
        isConnected.value = true;
        isConnecting = false; // 成功解锁
        retryCount = 0;
        console.log("✅ WebSocket 连接成功");
      };
      
      socket.value.onmessage = (event) => {
        try {
          messages.value.push(JSON.parse(event.data));
        } catch (e) {
          console.error("解析消息失败:", e);
        }
      };
      
      socket.value.onclose = (event) => {
        isConnected.value = false;
        isConnecting = false; // 关闭解锁
        socket.value = null;
        
        // 仅非主动关闭时重连
        if (event.code !== 1000 && retryCount < MAX_RETRY) {
          retryCount++;
          console.log(`⏳ WebSocket 断开，${2 * retryCount}秒后尝试第 ${retryCount} 次重连...`);
          reconnectTimer = setTimeout(connect, 2000 * retryCount);
        }
      };

      socket.value.onerror = (error) => {
        console.error("❌ WebSocket 错误:", error);
        isConnecting = false; // 错误解锁
        socket.value?.close(); 
      };
    } catch (error) {
      console.error("获取 Ticket 失败:", error);
      isConnecting = false; // 异常解锁
    }
  };

  const send = (topic: string, payload: any) => {
    if (socket.value?.readyState === WebSocket.OPEN) {
      const envelope = {
        topic: topic,
        msgId: generateUUID(), 
        payload: payload,
      };
      socket.value.send(JSON.stringify(envelope));
    } else {
      console.warn("⚠️ WebSocket 未连接，消息发送失败:", payload);
    }
  };

  const safeClose = (code = 1000, reason = "manual close") => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (socket.value) {
      socket.value.close(code, reason);
      socket.value = null; 
    }
    isConnected.value = false;
    isConnecting = false;
  };

  // 🌟 核心修复 3：移除自动连接！把控制权交给组件的 onMounted
  // connect(); 

  onBeforeUnmount(() => {
    safeClose(); 
  });

  return {
    messages,
    isConnected,
    send,
    close: safeClose, 
    connect,
  };
}