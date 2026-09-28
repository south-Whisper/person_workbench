<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAuditLog } from '@/api/candidate'
import AppIcon from '@/components/AppIcon.vue'
import { dateTime } from '@/utils/format'

const logs = ref([]), loading = ref(true), error = ref(''), search = ref(''), action = ref(''), page = ref(1)
const actionLabels = {
  'person.save': '人才档案变更', 'position.save': '岗位与版本变更', 'applications.save': '应聘流程变更',
  'interviews.save': '面试评价变更', 'offers.save': 'Offer 变更', 'events.save': '人才动态新增',
  'tasks.save': '跟进任务变更', 'opportunities.save': '主动寻访变更', 'compensations.save': '薪资事实变更',
  'experiences.save': '成长经历变更', 'employments.save': '任职关系变更', 'collaborations.save': '项目合作变更',
  'asset.upload': '附件上传', 'asset.download': '附件下载', login: '账号登录', initial_setup: '创建首位管理员'
}
const filtered = computed(() => {
  const query = search.value.trim().toLowerCase()
  return logs.value.filter(item => (!action.value || item.action === action.value) && (!query || [item.actor, label(item.action), entity(item)].join(' ').toLowerCase().includes(query)))
})
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10)))
const pageRows = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))
const actions = computed(() => [...new Set(logs.value.map(item => item.action))].sort())
const actors = computed(() => new Set(logs.value.map(item => item.actor)).size)
const today = computed(() => {
  const key = new Date().toLocaleDateString('sv-SE')
  return logs.value.filter(item => String(item.createdAt).slice(0, 10) === key).length
})

function label(value) { return actionLabels[value] || value.replace('.save', '变更') }
function entity(item) {
  const value = item.details || {}
  if (value.personId) return `Person #${value.personId}${value.recordId ? ` · Record #${value.recordId}` : ''}`
  if (value.positionId) return `Job #${value.positionId}`
  return '当前工作空间'
}
function changedFields(item) {
  const before = item.details?.before || {}, after = item.details?.after || {}
  const hidden = new Set(['password', 'storageName', 'sha256'])
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter(key => !hidden.has(key) && JSON.stringify(before[key]) !== JSON.stringify(after[key]))
  return keys.length ? keys.slice(0, 6).join('、') + (keys.length > 6 ? ` 等 ${keys.length} 项` : '') : '已记录操作对象与时间'
}
async function load() {
  loading.value = true; error.value = ''
  try { logs.value = await getAuditLog(); page.value = 1 }
  catch (cause) { error.value = cause.message || '审计日志加载失败' }
  finally { loading.value = false }
}
function changePage(value) { page.value = Math.min(Math.max(1, value), pageCount.value) }
onMounted(load)
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">TRUSTED BY DESIGN</div><h1>数据与审计</h1><p>知道谁在什么时候改了什么，同时尽量减少敏感信息暴露。</p></div><button class="btn btn-secondary" :disabled="loading" @click="load"><AppIcon name="clock" :size="15" />刷新日志</button></div>

  <div class="summary-grid audit-summary">
    <section class="surface summary-card"><div class="summary-top">最近审计记录</div><div class="summary-number"><strong>{{ logs.length }}</strong><small>条</small></div><div class="summary-foot">最多保留在此页查看最近 200 条</div></section>
    <section class="surface summary-card"><div class="summary-top">今日操作</div><div class="summary-number"><strong>{{ today }}</strong><small>条</small></div><div class="summary-foot">按实际写入时间统计</div></section>
    <section class="surface summary-card"><div class="summary-top">操作成员</div><div class="summary-number"><strong>{{ actors }}</strong><small>位</small></div><div class="summary-foot">当前工作空间内可见</div></section>
    <section class="surface summary-card"><div class="summary-top">组织隔离</div><div class="summary-number"><strong>开启</strong></div><div class="summary-foot">查询、导出与审计均按组织隔离</div></section>
  </div>

  <div class="governance-grid">
    <section class="surface governance-card"><span class="governance-icon"><AppIcon name="people" :size="18" /></span><div><h2>人才摘要导出</h2><p>人才库可按当前筛选导出 CSV 摘要。手机号、微信、邮箱和薪资数字默认不写入摘要，降低二次传播风险。</p></div></section>
    <section class="surface governance-card"><span class="governance-icon"><AppIcon name="briefcase" :size="18" /></span><div><h2>历史版本保留</h2><p>岗位调薪会生成新版本；旧应聘继续引用当时的岗位快照。Offer 条件变化需要新建版本。</p></div></section>
    <section class="surface governance-card"><span class="governance-icon"><AppIcon name="settings" :size="18" /></span><div><h2>并发与可追溯</h2><p>人才、岗位和业务记录使用版本号防止互相覆盖。关键写入、附件上传与下载都会留下审计记录。</p></div></section>
  </div>

  <section class="surface audit-panel">
    <div class="section-heading"><div><h2>操作审计</h2><p class="panel-caption">仅展示对象编号和变更字段名，不在列表展开联系方式、薪资或附件内容。</p></div></div>
    <div class="workspace-toolbar"><label class="table-search"><AppIcon name="search" :size="15" /><input v-model="search" aria-label="搜索审计日志" placeholder="搜索成员、操作或对象编号" @input="page = 1" /></label><select v-model="action" aria-label="按操作类型筛选" @change="page = 1"><option value="">全部操作</option><option v-for="value in actions" :key="value" :value="value">{{ label(value) }}</option></select><span class="muted">{{ filtered.length }} 条</span></div>
    <div v-if="error" class="error-state" role="alert">{{ error }} <button class="btn btn-small" @click="load">重试</button></div>
    <div v-else-if="loading" class="loading-state"><span class="spinner"></span>正在读取审计记录…</div>
    <div v-else-if="!pageRows.length" class="empty-state"><AppIcon name="clock" :size="34" /><h3>没有匹配的审计记录</h3><p>调整筛选条件后再试。</p></div>
    <div v-else class="table-scroll"><table class="audit-table"><thead><tr><th>时间</th><th>成员</th><th>操作</th><th>对象</th><th>变更范围</th></tr></thead><tbody><tr v-for="item in pageRows" :key="item.id"><td>{{ dateTime(item.createdAt) }}</td><td><span class="actor-pill">{{ item.actor }}</span></td><td>{{ label(item.action) }}</td><td><code>{{ entity(item) }}</code></td><td>{{ changedFields(item) }}</td></tr></tbody></table></div>
    <div class="pagination"><span>第 {{ page }} / {{ pageCount }} 页 · 每页 10 条</span><nav class="page-buttons" aria-label="审计日志分页"><button :disabled="page === 1" aria-label="上一页" @click="changePage(page - 1)">‹</button><button v-for="number in pageCount" :key="number" v-show="Math.abs(number - page) <= 2" :class="{ active: number === page }" @click="changePage(number)">{{ number }}</button><button :disabled="page === pageCount" aria-label="下一页" @click="changePage(page + 1)">›</button></nav></div>
  </section>
</template>

<style scoped>
.audit-summary{margin-bottom:18px}.governance-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:18px}.governance-card{display:flex;gap:15px;padding:22px}.governance-icon{width:38px;height:38px;border-radius:12px;background:#edf1f4;color:#64798c;display:grid;place-items:center;flex:none}.governance-card h2{font-size:14px;margin:1px 0 8px;color:#394552}.governance-card p{font-size:12px;line-height:1.75;color:#7f8792;margin:0}.audit-panel{padding:26px}.audit-table{width:100%;border-collapse:collapse;font-size:12px}.audit-table th{text-align:left;color:#939aa4;font-size:10px;font-weight:600;letter-spacing:.06em;padding:12px;border-bottom:1px solid #e9edf0}.audit-table td{padding:15px 12px;border-bottom:1px solid #eef1f3;color:#5f6874;vertical-align:top}.audit-table code{font:inherit;color:#556c7e;background:#eef2f4;padding:4px 7px;border-radius:6px}.actor-pill{display:inline-flex;padding:4px 8px;border-radius:999px;background:#eef2ed;color:#607264}.workspace-toolbar{margin:20px 0 8px}.pagination{margin-top:16px}@media(max-width:900px){.governance-grid{grid-template-columns:1fr}.audit-summary{grid-template-columns:repeat(2,1fr)}}@media(max-width:620px){.audit-summary{grid-template-columns:1fr}.audit-panel{padding:18px}.audit-table{min-width:760px}}
</style>
