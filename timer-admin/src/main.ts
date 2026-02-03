// src/main.js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn' // 引入中文语言包

const pinia = createPinia()
const app = createApp(App)

app.config.errorHandler = (err) => {
  console.error('Global error:', err)
  return false // 阻止错误传播
}

// ✅ 关键：Pinia 必须在 app.use(router) 之前安装
app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn, // 设置 Element Plus 语言为中文
})
app.mount('#app')