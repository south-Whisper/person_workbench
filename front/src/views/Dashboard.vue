<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCandidateList, getWorkspace, updateRecord } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import CandidateFormDialog from '@/components/CandidateFormDialog.vue'
import DataTransferDialog from '@/components/DataTransferDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import { dateOnly, initials, isOverdue, salary } from '@/utils/format'
import { exportCandidatesCSV } from '@/utils/transfer'
const route = useRoute(), router = useRouter()
const persons = ref([]), positions = ref([]), workspace = ref({}), total = ref(0), stats = ref({})
const loading = ref(true), error = ref(''), workspaceError = ref(''), page = ref(1), showAdd = ref(false), showImport = ref(false), view = ref('all'), busyTask = ref(null), exporting = ref(false)
const filters = reactive({ search: String(route.query.search || ''), status: '', source: '', jobId: '' })
const isDashboard = computed(() => route.path === '/dashboard')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 10)))
const pages = computed(() => { const start = Math.max(1, Math.min(page.value - 2, pageCount.value - 4)); return Array.from({ length: Math.min(5, pageCount.value) }, (_, i) => start + i) })
const pendingTasks = computed(() => (workspace.value.tasks || []).filter(t => t.status !== '已完成').sort((a, b) => (a.dueAt || '9999').localeCompare(b.dueAt || '9999')).slice(0, 3))
const summaryCards = computed(() => [
  { title: '人才总量', value: stats.value.total, unit: '位', caption: '每一份连接，都值得珍藏', icon: 'people' },
  { title: '沟通中', value: stats.value.communicating, unit: '位', caption: '从一次对话，开启可能', icon: 'globe' },
  { title: '面试中', value: stats.value.interviewing, unit: '位', caption: '了解彼此，发现契合', icon: 'calendar' },
  { title: '已入职', value: stats.value.hired, unit: '位', caption: '共同出发，创造更多', icon: 'check' }
])
const funnel = computed(() => [ ['已建档', stats.value.total], ['沟通中', stats.value.communicating], ['面试中', stats.value.interviewing], ['Offer', stats.value.offers], ['已入职', stats.value.hired] ])
let requestId = 0, timer
async function loadPersons() {
  const request = ++requestId
  loading.value = true; error.value = ''
  try {
    const data = await getCandidateList({ ...filters, page: page.value, size: 10 })
    if (request !== requestId) return
    persons.value = data.items || []; total.value = data.total || 0; stats.value = data.stats || {}
    if (page.value > pageCount.value) { page.value = pageCount.value; return loadPersons() }
  } catch (e) { if (request === requestId) error.value = e.message } finally { if (request === requestId) loading.value = false }
}
async function loadWorkspace() { try { workspace.value = await getWorkspace(); workspaceError.value = '' } catch (e) { workspaceError.value = e.message } }
async function refresh() { await Promise.all([loadPersons(), loadWorkspace()]) }
onMounted(async () => { await Promise.all([refresh(), getPositionList().then(data => { positions.value = data }).catch(e => ElMessage.error(e.message))]) })
watch(() => [filters.status, filters.source, filters.jobId], () => { page.value = 1; loadPersons() })
watch(() => filters.search, () => { clearTimeout(timer); timer = setTimeout(() => { page.value = 1; loadPersons() }, 300) })
watch(() => route.query.search, value => { filters.search = String(value || '') })
watch(() => route.path, () => { view.value = 'all' })
onBeforeUnmount(() => { clearTimeout(timer); requestId++ })
function setPage(value) { if (value < 1 || value > pageCount.value || loading.value) return; page.value = value; loadPersons() }
function clearFilters() { filters.search = ''; filters.status = ''; filters.source = ''; filters.jobId = ''; view.value = 'all'; page.value = 1; loadPersons() }
async function saved() { page.value = 1; await refresh() }
async function completeTask(task) {
  if (busyTask.value) return
  busyTask.value = task.id
  try { await updateRecord(task.personId, 'tasks', task.id, { ...task, status: '已完成' }); await loadWorkspace(); ElMessage.success('已完成跟进任务') } catch (e) { ElMessage.error(e.message) } finally { busyTask.value = null }
}
function statusTone(value) { return value === '已入职' ? 'green' : value === 'Offer中' ? 'amber' : ['已关闭', '已结束', '人才储备'].includes(value) ? 'gray' : '' }
function setView(value) { view.value = value; filters.status = value === 'active' ? '沟通中' : value === 'reserve' ? '人才储备' : '' }
async function exportFiltered() {
  if (exporting.value) return
  exporting.value = true
  try {
    const first = await getCandidateList({ ...filters, page: 1, size: 10 })
    const rows = [...(first.items || [])]
    const pages = Math.ceil((first.total || 0) / 10)
    for (let current = 2; current <= pages; current++) {
      const result = await getCandidateList({ ...filters, page: current, size: 10 })
      rows.push(...(result.items || []))
    }
    if (!rows.length) { ElMessage.warning('当前筛选没有可导出的人才'); return }
    exportCandidatesCSV(rows, `人才招聘-人才摘要-${new Date().toISOString().slice(0, 10)}.csv`)
    ElMessage.success(`已导出 ${rows.length} 份人才摘要`)
  } catch (e) { ElMessage.error(e.message || '导出失败') }
  finally { exporting.value = false }
}
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">YOUR TALENT UNIVERSE</div><h1>{{ isDashboard ? '让人才的故事，持续发生。' : '人才库' }}</h1><p>{{ isDashboard ? '每一次沟通、每一段成长，都在这里连接。' : '从第一次相遇，到每一段合作。所有人才，一处连接。' }}</p></div><div class="heading-actions"><button class="btn btn-secondary" :disabled="exporting" @click="exportFiltered"><AppIcon name="download" :size="15" />{{ exporting ? '整理中…' : '导出摘要' }}</button><button class="btn btn-secondary" @click="showImport = true"><AppIcon name="download" :size="15" />导入人才</button><button class="btn btn-primary" @click="showAdd = true"><AppIcon name="plus" :size="16" />新增人才</button></div></div>
  <div class="summary-grid"><div v-for="card in summaryCards" :key="card.title" class="surface summary-card"><div class="summary-top"><span>{{ card.title }}</span><span class="summary-icon"><AppIcon :name="card.icon" :size="17" /></span></div><div class="summary-number"><strong>{{ card.value ?? '—' }}</strong><small>{{ card.unit }}</small></div><div class="summary-foot"><span>{{ card.caption }}</span><AppIcon name="arrow" :size="13" /></div></div></div>
  <div v-if="isDashboard" class="overview-grid"><section class="surface overview-panel"><div class="section-heading"><div><h2>从相识，到同行</h2><p class="panel-caption">人才当前阶段概览</p></div><router-link class="text-link" to="/analytics">查看分析 ↗</router-link></div><div class="funnel"><div v-for="[name, count] in funnel" :key="name" class="funnel-step"><div class="funnel-pill"><b>{{ count ?? '—' }}</b><span>{{ name }}</span></div></div></div><div class="funnel-note">按当前状态统计 · 非历史转化率</div></section><section class="surface overview-panel"><div class="section-heading"><div><h2>接下来，保持连接</h2><p class="panel-caption">需要你关注的跟进任务</p></div><router-link class="text-link" to="/tasks">全部任务 ↗</router-link></div><div v-if="workspaceError" class="compact-empty">{{ workspaceError }} <button class="text-link" @click="loadWorkspace">重试</button></div><template v-else><div v-for="task in pendingTasks" :key="task.id" class="task-compact"><button class="task-check" :aria-label="'完成任务：' + task.title" :disabled="busyTask === task.id" @click="completeTask(task)"></button><router-link :to="'/person/' + task.personId"><b>{{ task.title }}</b><p>{{ task.personName }} · {{ dateOnly(task.dueAt) }}</p></router-link><span class="badge" :class="isOverdue(task) ? 'rose' : 'gray'">{{ isOverdue(task) ? '已逾期' : '待跟进' }}</span></div><div v-if="!pendingTasks.length" class="compact-empty">暂无待办，为人才添加下一次跟进吧。</div></template></section></div>
  <section class="surface talent-panel"><div class="talent-panel-header"><div><h2>{{ isDashboard ? '人才，一览而知' : '全部人才档案' }} <span class="table-count">{{ total }} 位</span></h2><p>记录当下，也记得每一段过去。</p></div><div class="segmented"><button :class="{ active: view === 'all' }" @click="setView('all')">全部人才</button><button :class="{ active: view === 'active' }" @click="setView('active')">沟通中</button><button :class="{ active: view === 'reserve' }" @click="setView('reserve')">人才储备</button></div></div><div class="table-tools"><label class="table-search"><AppIcon name="search" :size="15" /><input v-model="filters.search" placeholder="搜索姓名、岗位、联系方式" aria-label="筛选人才" /></label><select v-model="filters.jobId" aria-label="按岗位筛选"><option value="">全部岗位</option><option v-for="job in positions" :key="job.id" :value="job.id">{{ job.name }}</option></select><select v-model="filters.status" aria-label="按状态筛选"><option value="">全部状态</option><option v-for="status in ['待联系','沟通中','面试中','Offer中','待入职','已入职','人才储备','已关闭']" :key="status">{{ status }}</option></select><select v-model="filters.source" aria-label="按来源筛选"><option value="">全部来源</option><option v-for="source in ['BOSS直聘','猎聘','智联招聘','内推','主动寻访','历史导入','其他']" :key="source">{{ source }}</option></select><button v-if="filters.search || filters.status || filters.jobId || filters.source" class="text-link" @click="clearFilters">重置</button></div>
    <div v-if="error" class="error-state" role="alert">{{ error }} <button class="btn btn-small" @click="loadPersons">重新加载</button></div><div v-else-if="loading" class="loading-state"><span class="spinner"></span>正在连接人才档案…</div><div v-else-if="!persons.length" class="empty-state"><AppIcon name="people" :size="36" /><h3>{{ total ? '没有匹配的人才' : '下一次相遇，从这里开始' }}</h3><p>新增一份人才档案，或调整筛选条件。</p><button class="btn btn-primary" @click="showAdd = true">新增人才</button></div><div v-else class="table-scroll"><table class="talent-table"><thead><tr><th>人才</th><th>关联岗位</th><th>来源</th><th>期望薪资</th><th>当前状态</th><th>招聘结果</th><th>下一步 / 负责人</th><th></th></tr></thead><tbody><tr v-for="(person, index) in persons" :key="person.id"><td><router-link :to="'/person/' + person.id" class="person-cell"><span class="person-avatar" :class="'tone-' + index % 4">{{ initials(person.name) }}</span><span><b>{{ person.name }}</b><small>{{ person.location || '所在地待补充' }} · {{ person.experience || '经验待了解' }}</small></span></router-link></td><td>{{ person.job || '暂未关联岗位' }}</td><td>{{ person.source || '待补充' }}</td><td class="salary-cell">{{ salary(person) }}</td><td><span class="badge" :class="statusTone(person.status)">{{ person.status || '待联系' }}</span></td><td><span class="badge" :class="['未入职','候选人拒绝','公司淘汰'].includes(person.result) ? 'rose' : person.result === '已入职' ? 'green' : 'gray'">{{ person.result || '待定' }}</span></td><td><div class="next-step-cell" :title="person.nextStep">{{ person.nextStep || '下一步待安排' }}</div><small class="muted" style="font-size:9px">{{ person.owner || '负责人待补充' }}</small></td><td><router-link class="text-link" :to="'/person/' + person.id" :aria-label="'查看' + person.name + '详情'"><AppIcon name="arrow" :size="16" /></router-link></td></tr></tbody></table></div>
    <div class="pagination"><span>共 {{ total }} 位人才 · 每页最多 10 位<span v-if="total"> · 当前 {{ (page - 1) * 10 + 1 }}–{{ Math.min(page * 10, total) }} 位</span></span><nav class="page-buttons" aria-label="人才列表分页"><button :disabled="page === 1 || loading" aria-label="上一页" @click="setPage(page - 1)">‹</button><button v-for="number in pages" :key="number" :class="{ active: number === page }" :aria-current="number === page ? 'page' : undefined" :disabled="loading" @click="setPage(number)">{{ number }}</button><span v-if="pageCount > 5">/ {{ pageCount }}</span><button :disabled="page === pageCount || loading" aria-label="下一页" @click="setPage(page + 1)">›</button></nav></div>
  </section><CandidateFormDialog v-model="showAdd" @saved="saved" /><DataTransferDialog v-model="showImport" @saved="saved" />
</template>
