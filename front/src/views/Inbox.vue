<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInbox, markInboxRead, markAllInboxRead } from '@/api/candidate'
import AppIcon from '@/components/AppIcon.vue'
import { dateTime } from '@/utils/format'

const router = useRouter()
const items = ref([]), loading = ref(true), error = ref(''), category = ref('全部'), unreadCount = ref(0), markingAll = ref(false)
const categories = computed(() => ['全部', ...new Set(items.value.map(item => item.category))])
const visibleItems = computed(() => category.value === '全部' ? items.value : items.value.filter(item => item.category === category.value))
const counts = computed(() => items.value.reduce((result, item) => { result[item.category] = (result[item.category] || 0) + 1; return result }, {}))

async function load() {
  loading.value = true; error.value = ''
  try { const result = await getInbox(); items.value = result.items || []; unreadCount.value = Number(result.unreadCount || 0); notifyHeader() }
  catch (cause) { error.value = cause.message || '消息加载失败，请稍后重试。' }
  finally { loading.value = false }
}
function notifyHeader() { window.dispatchEvent(new CustomEvent('inbox-read-changed', { detail: { unreadCount: unreadCount.value } })) }
async function read(item) {
  if (item.read) return
  await markInboxRead(item.id)
  item.read = true
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  notifyHeader()
}
async function openMessage(item) {
  try { await read(item) } catch (cause) { ElMessage.error(cause.response?.data?.message || cause.message || '已读状态保存失败') }
  router.push(item.route)
}
async function readAll() {
  if (!unreadCount.value || markingAll.value) return
  markingAll.value = true
  try { await markAllInboxRead(); items.value.forEach(item => { item.read = true }); unreadCount.value = 0; notifyHeader(); ElMessage.success('全部消息已标为已读') }
  catch (cause) { ElMessage.error(cause.response?.data?.message || cause.message || '已读状态保存失败') }
  finally { markingAll.value = false }
}
onMounted(load)
</script>

<template>
  <div class="inbox-page">
    <div class="page-heading"><div><div class="eyebrow">YOUR WORK IN ONE PLACE</div><h1>消息信箱</h1><p>这里只提醒人才填写问卷、面试安排和 Offer 已确认。</p></div><div class="heading-actions"><button class="btn" :disabled="loading || !unreadCount || markingAll" @click="readAll">{{ markingAll ? '处理中…' : '全部标为已读' }}</button><button class="btn" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新消息' }}</button></div></div>
    <section class="surface inbox-summary"><div><span class="inbox-summary-icon"><AppIcon name="mail" :size="22" /></span><p><small>未读消息</small><strong>{{ unreadCount }}</strong></p></div><div v-for="name in ['人才已填写问卷','面试安排','Offer 已确认']" :key="name"><p><small>{{ name }}</small><strong>{{ counts[name] || 0 }}</strong></p></div></section>
    <section class="surface inbox-panel">
      <header><div><h2>我的消息</h2><p>只显示当前账号负责的人才和安排。</p></div><div class="inbox-tabs"><button v-for="name in categories" :key="name" :class="{ active: category === name }" @click="category = name">{{ name }}</button></div></header>
      <div v-if="error" class="error-state" role="alert">{{ error }} <button class="btn btn-small" @click="load">重试</button></div>
      <div v-else-if="loading" class="loading-state"><span class="spinner"></span>正在整理消息…</div>
      <div v-else-if="!visibleItems.length" class="empty-state"><AppIcon name="mail" :size="38" /><h3>这里暂时没有消息</h3><p>人才提交问卷或产生新的安排后，会自动出现在这里。</p></div>
      <div v-else class="inbox-list"><article v-for="item in visibleItems" :key="item.id" class="inbox-item" :class="{ read: item.read }"><router-link :to="item.route" class="inbox-item-main" @click.prevent="openMessage(item)"><span class="inbox-item-icon" :class="`type-${item.category}`"><AppIcon :name="item.category === '面试安排' ? 'calendar' : item.category === 'Offer 已确认' ? 'briefcase' : 'people'" :size="18" /></span><div><div class="inbox-item-top"><span v-if="!item.read" class="unread-dot">未读</span><span>{{ item.category }}</span><time>{{ dateTime(item.createdAt) }}</time></div><h3>{{ item.title }}</h3><p>{{ item.summary }}</p></div></router-link><span class="badge" :class="item.status === '已完成' || item.status === '已接受' ? 'green' : ''">{{ item.status }}</span><button v-if="!item.read" type="button" class="read-button" @click="read(item)">标为已读</button><span v-else class="read-label">已读</span><router-link :to="item.route" class="inbox-arrow" aria-label="查看消息" @click.prevent="openMessage(item)"><AppIcon name="arrow" :size="16" /></router-link></article></div>
    </section>
  </div>
</template>

<style scoped>
.inbox-page{max-width:1120px;margin:0 auto}.heading-actions{display:flex;gap:9px}.inbox-summary{display:grid;grid-template-columns:1.4fr repeat(3,1fr);margin-bottom:18px;overflow:hidden}.inbox-summary>div{min-height:92px;padding:20px 23px;display:flex;align-items:center;gap:14px;border-right:1px solid #edf0f4}.inbox-summary>div:last-child{border-right:0}.inbox-summary-icon{display:grid;place-items:center;width:45px;height:45px;border-radius:14px;background:#e8f0ff;color:#2868e8}.inbox-summary p{margin:0}.inbox-summary small,.inbox-summary strong{display:block}.inbox-summary small{color:#98a3b0;font-size:10px;margin-bottom:7px}.inbox-summary strong{font-size:25px;color:#3e526a;font-weight:600}.inbox-panel{overflow:hidden}.inbox-panel>header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:22px 25px;border-bottom:1px solid #edf0f4}.inbox-panel h2{margin:0;font-size:17px}.inbox-panel header p{margin:7px 0 0;color:#98a3af;font-size:10px}.inbox-tabs{display:flex;gap:5px;flex-wrap:wrap}.inbox-tabs button{border:1px solid #e3e8ef;border-radius:9px;background:#f7f9fb;color:#8190a1;padding:7px 11px;font-size:10px}.inbox-tabs button.active{border-color:#b8cced;background:#eaf1ff;color:#2766dc}.inbox-list{padding:0 25px}.inbox-item{display:grid;grid-template-columns:minmax(0,1fr) auto auto auto;align-items:center;gap:14px;padding:17px 2px;border-bottom:1px solid #edf0f4}.inbox-item:last-child{border-bottom:0}.inbox-item:hover{background:#f8faff}.inbox-item.read{opacity:.68}.inbox-item-main{display:grid;grid-template-columns:auto minmax(0,1fr);align-items:center;gap:16px;min-width:0}.inbox-item-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:13px;background:#eef3f8;color:#66839f}.inbox-item-icon.type-面试安排{background:#f1edff;color:#7257c7}.inbox-item-icon.type-Offer{background:#fff2dc;color:#ad792b}.inbox-item-icon.type-人才问卷{background:#e9f4ee;color:#51846c}.inbox-item-top{display:flex;gap:10px;align-items:center;color:#7990a8;font-size:9px}.inbox-item-top time{color:#a2adba}.unread-dot{padding:2px 6px;border-radius:999px;background:#e7efff;color:#2868e8;font-weight:700}.inbox-item h3{margin:6px 0 4px;font-size:13px;color:#40546c}.inbox-item p{margin:0;color:#8593a3;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.read-button{border:0;background:transparent;color:#3470df;font-size:10px;cursor:pointer;white-space:nowrap}.read-label{color:#a0a9b4;font-size:10px}.inbox-arrow{display:grid;place-items:center;color:#718397}.error-state{margin:20px}.loading-state{min-height:280px}@media(max-width:850px){.inbox-summary{grid-template-columns:repeat(2,1fr)}.inbox-summary>div{border-bottom:1px solid #edf0f4}.inbox-panel>header{align-items:flex-start;flex-direction:column}.inbox-item{grid-template-columns:minmax(0,1fr) auto auto}.inbox-item>.badge{display:none}.inbox-item p{white-space:normal}}@media(max-width:520px){.page-heading{align-items:flex-start}.heading-actions{flex-direction:column}.inbox-summary{grid-template-columns:1fr 1fr}.inbox-summary>div{padding:15px}.inbox-list{padding:0 15px}.inbox-item{gap:8px}.inbox-item-icon{width:36px;height:36px}.read-button{display:none}}
</style>
<style scoped>
.inbox-tabs{gap:8px}.inbox-tabs button{min-height:36px;padding:8px 14px;border:1px solid #cbd5e1;background:#fff;color:#475467;font-size:11px;font-weight:650;cursor:pointer;box-shadow:0 1px 2px rgba(16,24,40,.04)}.inbox-tabs button:hover{border-color:#8fb0ff;background:#f5f8ff;color:#245bdb}.inbox-tabs button.active{border-color:#3370ff;background:#e8f0ff;color:#245bdb;box-shadow:0 0 0 2px rgba(51,112,255,.09)}.inbox-item.read{opacity:1}.inbox-item.read .inbox-item-main{opacity:.82}.read-button,.read-label{display:inline-flex;align-items:center;justify-content:center;min-width:72px;min-height:32px;padding:7px 11px;border-radius:9px;font-size:11px;font-weight:650;white-space:nowrap}.read-button{border:1px solid #8fb0ff;background:#edf3ff;color:#245bdb}.read-button:hover{border-color:#3370ff;background:#e1ebff}.read-label{border:1px solid #d2dae4;background:#f3f5f7;color:#536174}
</style>
