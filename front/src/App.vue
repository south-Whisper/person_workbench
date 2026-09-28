<script setup>
import AppShell from './components/AppShell.vue'
</script>
<template>
  <router-view v-slot="{ Component, route }">
    <component :is="Component" v-if="route.path === '/login'" />
    <AppShell v-else><component :is="Component" /></AppShell>
  </router-view>
</template>
<style src="./assets/theme.css"></style>
