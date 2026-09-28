<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
const route = useRoute(), router = useRouter()
const search = ref(''), mobileOpen = ref(false), userMenuOpen = ref(false)
const username = localStorage.getItem('username') || '工作账号'
const nav = [
  ['/dashboard', '工作台', 'home'], ['/talents', '人才库', 'people'], ['/opportunities', '主动寻访', 'globe'],
  ['/recruitment', '招聘流程', 'board'], ['/positions', '岗位管理', 'briefcase'], ['/interviews', '面试与评价', 'calendar'],
  ['/employees', '员工生命周期', 'check'], ['/collaborations', '项目合作', 'globe'], ['/tasks', '跟进任务', 'clock'], ['/analytics', '数据概览', 'chart'],
  ['/settings', '数据与审计', 'settings']
]
const title = computed(() => nav.find(n => n[0] === route.path)?.[1] || '人才档案')
function logout() { for (const key of ['token', 'username', 'role']) localStorage.removeItem(key); userMenuOpen.value = false; router.replace('/login') }
function submitSearch() { router.push({ path: '/talents', query: search.value.trim() ? { search: search.value.trim() } : {} }); mobileOpen.value = false }
function closeUserMenu() { userMenuOpen.value = false }
onMounted(() => document.addEventListener('click', closeUserMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeUserMenu))
</script>
<template>
  <div class="app-layout">
    <div class="ambient ambient-a"></div><div class="ambient ambient-b"></div>
    <button v-if="mobileOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileOpen = false"></button>
    <aside class="app-sidebar" :class="{ 'is-open': mobileOpen }">
      <router-link class="brand-lockup" aria-label="前往登录页并保留当前登录信息" :to="{ path: '/login', query: { preserveAuth: '1' } }" @click="mobileOpen = false"><span class="brand-symbol">招</span><span>人才<span class="brand-light">招聘</span><small>SetHub 旗下产品</small></span></router-link>
      <div class="workspace-label"><span class="workspace-dot"></span>人才招聘工作空间<span class="workspace-chevron">⌄</span></div>
      <div class="nav-caption">工作空间</div>
      <nav aria-label="主导航"><router-link v-for="[path, label, icon] in nav" :key="path" :to="path" :class="{ active: route.path === path || (path === '/talents' && route.path.startsWith('/person/')) }" @click="mobileOpen = false"><AppIcon :name="icon" /><span>{{ label }}</span><span v-if="route.path === path" class="nav-active-dot"></span></router-link></nav>
      <div class="sidebar-bottom"><div class="workspace-note"><span class="small-orbit"></span><b>让每一次相遇，都有迹可循。</b><p>连接人才的过去、现在与未来。</p></div></div>
    </aside>
    <div class="app-main">
      <header class="app-header"><div class="breadcrumb"><button class="mobile-toggle" @click="mobileOpen = !mobileOpen" aria-label="打开导航">☰</button><span>工作空间</span><span class="breadcrumb-slash">/</span><b>{{ title }}</b></div><form class="global-search" @submit.prevent="submitSearch"><AppIcon name="search" :size="17" /><input v-model="search" aria-label="搜索人才或岗位" placeholder="搜索人才、岗位、联系方式…" /><kbd>↵</kbd></form><div class="header-date">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</div><div class="header-account" @click.stop><button type="button" class="header-account-trigger" :aria-expanded="userMenuOpen" aria-haspopup="menu" aria-label="打开用户菜单" @click="userMenuOpen = !userMenuOpen"><span class="account-avatar">{{ username.slice(0, 1).toUpperCase() }}</span><span class="header-account-name">{{ username }}</span><span class="header-account-chevron">⌄</span></button><div v-if="userMenuOpen" class="header-account-menu" role="menu"><div class="account-menu-profile"><span class="account-avatar large">{{ username.slice(0, 1).toUpperCase() }}</span><span><b>{{ username }}</b><small>当前登录账号</small></span></div><button type="button" role="menuitem" class="logout-menu-item" @click="logout"><AppIcon name="logout" :size="16" />退出登录</button></div></div></header>
      <main class="page-content"><slot /></main><footer class="app-footer">人才招聘 <span>·</span> SetHub 旗下产品</footer>
    </div>
  </div>
</template>
