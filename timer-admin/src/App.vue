<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { useRouter, useRoute } from "vue-router";
const route = useRoute();
</script>

<style>
/* 重置默认样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  min-height: 100vh;
  background-color: #0f172a; /* 设置默认背景色为深蓝 */
}

#app {
  min-height: 100vh;
  height: auto;
  background: linear-gradient(135deg, #0f172a, #1e293b); /* 统一背景 */
}
</style>
