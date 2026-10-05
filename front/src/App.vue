<script setup>
import AppShell from './components/AppShell.vue'
</script>
<template>
  <router-view v-slot="{ Component, route }">
    <component :is="Component" v-if="['/login', '/questionnaire', '/offer-response', '/offer-action'].includes(route.path)" />
    <AppShell v-else><KeepAlive :max="10"><component :is="Component" /></KeepAlive></AppShell>
  </router-view>
</template>
<style src="./assets/theme.css"></style>
