<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import BrandLockup from './BrandLockup.vue'
import { getInbox } from '@/api/candidate'
import { getEmployeeList } from '@/api/employee'
const route = useRoute(), router = useRouter()
const mobileOpen = ref(false), userMenuOpen = ref(false), inboxCount = ref(0)
const displayName = ref(localStorage.getItem('employeeName') || localStorage.getItem('username') || '工作账号')
const nav = [
  ['/dashboard', '工作台', 'home'], ['/talents', '人才库', 'people'],
  ['/recruitment', '招聘流程', 'board'], ['/communications', '沟通记录', 'globe'], ['/interviews', '面试安排', 'calendar'], ['/offers', 'Offer 发放', 'offer'], ['/onboarding', '入职流程', 'onboarding'], ['/positions', '岗位管理', 'briefcase'],
  ['/employees', '员工管理', 'check'], ['/settings', '系统设置', 'settings']
]
const title = computed(() => route.path === '/inbox' ? '消息信箱' : route.path === '/updates' ? '版本更新' : nav.find(n => n[0] === route.path)?.[1] || '人才档案')
function logout() { for (const key of ['token', 'username', 'employeeName', 'role']) localStorage.removeItem(key); userMenuOpen.value = false; router.replace('/login') }
function closeUserMenu() { userMenuOpen.value = false }
async function loadInboxCount() { try { const result = await getInbox(); inboxCount.value = Number(result.unreadCount ?? result.count ?? 0) } catch { inboxCount.value = 0 } }
async function loadCurrentEmployee() { try { const employees = await getEmployeeList(); const employee = employees.find(item => item.current) || employees[0]; if (employee?.name) { displayName.value = employee.name; localStorage.setItem('employeeName', employee.name) } } catch { /* 登录账号名作为兜底 */ } }
function syncInboxCount(event) { const count = event?.detail?.unreadCount; if (Number.isFinite(Number(count))) inboxCount.value = Number(count); else loadInboxCount() }
onMounted(() => { document.addEventListener('click', closeUserMenu); window.addEventListener('inbox-read-changed', syncInboxCount); loadInboxCount(); loadCurrentEmployee() })
onBeforeUnmount(() => { document.removeEventListener('click', closeUserMenu); window.removeEventListener('inbox-read-changed', syncInboxCount) })
</script>
<template>
  <div class="app-layout">
    <div class="ambient ambient-a"></div><div class="ambient ambient-b"></div>
    <button v-if="mobileOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileOpen = false"></button>
    <aside class="app-sidebar" :class="{ 'is-open': mobileOpen }">
      <router-link class="brand-lockup" aria-label="前往登录页并保留当前登录信息" :to="{ path: '/login', query: { preserveAuth: '1' } }" @click="mobileOpen = false"><BrandLockup compact /></router-link>
      <nav aria-label="主导航"><router-link v-for="[path, label, icon] in nav" :key="path" :to="path" :class="{ active: route.path === path || (path === '/talents' && route.path.startsWith('/person/')) }" @click="mobileOpen = false"><AppIcon :name="icon" /><span>{{ label }}</span><span v-if="route.path === path" class="nav-active-dot"></span></router-link></nav>
      <div class="sidebar-bottom"><div class="sidebar-status-art" aria-label="人才全生命周期管理"><span class="status-orbit orbit-one"></span><span class="status-orbit orbit-two"></span><span class="status-spark spark-one"></span><span class="status-spark spark-two"></span><div><b>人才全生命周期管理</b><small>SetHub · Talent System</small></div></div></div>
    </aside>
    <div class="app-main">
      <header class="app-header"><div class="breadcrumb"><button class="mobile-toggle" @click="mobileOpen = !mobileOpen" aria-label="打开导航">☰</button><b>{{ title }}</b></div><router-link class="header-inbox header-inbox-after-title" to="/inbox" aria-label="打开消息信箱"><AppIcon name="mail" :size="18" /><span v-if="inboxCount">{{ inboxCount > 99 ? '99+' : inboxCount }}</span></router-link><div class="header-date">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</div><div class="header-account" @click.stop><button type="button" class="header-account-trigger" :aria-expanded="userMenuOpen" aria-haspopup="menu" aria-label="打开用户菜单" @click="userMenuOpen = !userMenuOpen"><span class="account-avatar">{{ displayName.slice(0, 1).toUpperCase() }}</span><span class="header-account-name">{{ displayName }}</span><span class="header-account-chevron">⌄</span></button><div v-if="userMenuOpen" class="header-account-menu" role="menu"><div class="account-menu-profile"><span class="account-avatar large">{{ displayName.slice(0, 1).toUpperCase() }}</span><span><b>{{ displayName }}</b><small>当前登录 HR</small></span></div><button type="button" role="menuitem" class="logout-menu-item" @click="logout"><AppIcon name="logout" :size="16" />退出登录</button></div></div></header>
      <main class="page-content"><slot /></main><footer class="app-footer">人才管理 <span>·</span> SetHub 旗下产品</footer>
    </div>
  </div>
</template>
