<script setup>
import { onMounted } from 'vue'
import { releases, currentRelease, releaseError, loadReleases } from '@/data/releases'
onMounted(() => loadReleases().catch(() => {}))
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">PRODUCT UPDATES</div><h1>版本更新</h1><p>查看当前版本和每次更新带来的变化。</p></div><span class="current-version">当前 {{ currentRelease.version }}</span></div>
  <p v-if="releaseError" class="release-error">{{ releaseError }} <button class="text-link" @click="loadReleases(true)">重新读取</button></p>
  <section class="surface release-hero"><div><small>当前版本 · 更新时间 {{ currentRelease.updatedAt || currentRelease.date }}</small><h2>{{ currentRelease.version }} · {{ currentRelease.title }}</h2><p>{{ currentRelease.summary }}</p></div><router-link class="btn" to="/settings">返回系统设置</router-link></section>
  <section class="release-history" aria-label="历史版本">
    <article v-for="release in releases" :key="release.version" class="surface release-card">
      <header><span>{{ release.version }}</span><div><small>更新时间 {{ release.updatedAt || release.date }}</small><h2>{{ release.title }}</h2></div></header>
      <p>{{ release.summary }}</p>
      <ul><li v-for="change in release.changes" :key="change">{{ change }}</li></ul>
    </article>
  </section>
</template>

<style scoped>
.current-version{align-self:flex-start;padding:8px 13px;border:1px solid #a9c2ee;border-radius:999px;background:#fff;color:#245bdb;font-size:12px;font-weight:800}.release-hero{display:flex;align-items:center;justify-content:space-between;gap:22px;padding:26px;border:1px solid #bfd0e8;background:linear-gradient(135deg,#fff,#eef4ff)}.release-hero small{color:#245bdb;font-size:10px;font-weight:750;letter-spacing:.8px}.release-hero h2{margin:8px 0;color:#172033;font-size:22px}.release-hero p{max-width:760px;margin:0;color:#475467;font-size:12px;line-height:1.8}.release-history{display:grid;gap:18px;margin-top:20px}.release-card{padding:27px;border:1px solid #cbd6e4}.release-card header{display:flex;align-items:center;gap:15px}.release-card header>span{display:grid;place-items:center;width:52px;height:52px;border-radius:15px;background:#e8f0ff;color:#245bdb;font-size:17px;font-weight:850}.release-card small{color:#667085;font-size:10px}.release-card h2{margin:5px 0 0;color:#172033;font-size:17px}.release-card>p{margin:18px 0;color:#475467;font-size:12px;line-height:1.8}.release-card ul{display:grid;gap:10px;margin:0;padding:17px 20px 17px 36px;border:1px solid #d9e2ec;border-radius:13px;background:#fff;color:#344054}.release-card li{padding-left:4px;font-size:12px;line-height:1.65}.release-card li::marker{color:#3370ff}@media(max-width:650px){.release-hero{align-items:flex-start;flex-direction:column}.current-version{display:none}}
.release-error{padding:12px 14px;border:1px solid #fecdd3;border-radius:10px;background:#fff1f2;color:#be123c}
</style>
