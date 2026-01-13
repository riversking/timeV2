<template>
  <router-view/>
   
</template>

<script setup>
import { useRouter, useRoute } from "vue-router";
const route = useRoute();

</script>

<style>
.app-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}
</style>
