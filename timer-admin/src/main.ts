// src/main.js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const pinia = createPinia()
const app = createApp(App)

// ✅ 关键：Pinia 必须在 app.use(router) 之前安装
app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')