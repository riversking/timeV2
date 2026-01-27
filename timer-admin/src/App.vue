<template>
  <div class="app-container">
    <router-view/>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from "vue-router";
const route = useRoute();
</script>

<style>
#app {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.app-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}
</style>