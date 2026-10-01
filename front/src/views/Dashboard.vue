<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCandidateList, downloadAsset } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import CandidateFormDialog from '@/components/CandidateFormDialog.vue'
import DataTransferDialog from '@/components/DataTransferDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import { dateTime, initials, salary } from '@/utils/format'
import { exportCandidatesCSV } from '@/utils/transfer'
import { currentRelease, loadReleases } from '@/data/releases'
const route = useRoute(), router = useRouter()
const persons = ref([]), positions = ref([]), total = ref(0), stats = ref({})
const loading = ref(true), error = ref(''), page = ref(1), showAdd = ref(false), showImport = ref(false), view = ref('all'), exporting = ref(false)
const showQuestionnaire = ref(false), questionnaireLink = ref('')
const showReleaseNotes = ref(false)
const photoUrls = reactive({})
const filters = reactive({ search: String(route.query.search || ''), status: '', source: '', jobId: '' })
const isDashboard = computed(() => route.path === '/dashboard')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 10)))
const pages = computed(() => { const start = Math.max(1, Math.min(page.value - 2, pageCount.value - 4)); return Array.from({ length: Math.min(5, pageCount.value) }, (_, i) => start + i) })
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
    persons.value = data.items || []; total.value = data.total || 0; stats.value = data.stats || {}; await loadPhotos(persons.value)
    if (page.value > pageCount.value) { page.value = pageCount.value; return loadPersons() }
  } catch (e) { if (request === requestId) error.value = e.message } finally { if (request === requestId) loading.value = false }
}
async function refresh() { await loadPersons() }
function showReleaseOnce(){const version=currentRelease.value.version;if(route.path==='/dashboard'&&version!=='—'){const key=`person-workbench-release-${version}-seen`;if(!localStorage.getItem(key)){showReleaseNotes.value=true;localStorage.setItem(key,'1')}}}
onMounted(async () => { await Promise.all([refresh(), getPositionList().then(data => { positions.value = data }).catch(e => ElMessage.error(e.message)), loadReleases().catch(() => {})]); showReleaseOnce() })
watch(() => [filters.status, filters.source, filters.jobId], () => { page.value = 1; loadPersons() })
watch(() => filters.search, () => { clearTimeout(timer); timer = setTimeout(() => { page.value = 1; loadPersons() }, 300) })
watch(() => route.query.search, value => { filters.search = String(value || '') })
watch(() => route.path, () => { view.value = 'all'; showReleaseOnce() })
onBeforeUnmount(() => { clearTimeout(timer); requestId++; Object.values(photoUrls).forEach(url => URL.revokeObjectURL(url)) })
function setPage(value) { if (value < 1 || value > pageCount.value || loading.value) return; page.value = value; loadPersons() }
function clearFilters() { filters.search = ''; filters.status = ''; filters.source = ''; filters.jobId = ''; view.value = 'all'; page.value = 1; loadPersons() }
async function saved() { page.value = 1; await refresh() }
function openQuestionnaireSender() { questionnaireLink.value = `${location.origin}/questionnaire`; showQuestionnaire.value = true }
async function copyQuestionnaire(){try{await navigator.clipboard.writeText(questionnaireLink.value);ElMessage.success('问卷链接已复制，可以直接发给人才')}catch{ElMessage.error('复制失败，请选中链接后手动复制')}}
async function loadPhotos(rows){
  const current=new Set(rows.map(item=>String(item.id)));for(const [id,url] of Object.entries(photoUrls))if(!current.has(id)){URL.revokeObjectURL(url);delete photoUrls[id]}
  await Promise.all(rows.map(async person=>{const photo=(person.assets||[]).find(asset=>asset.type==='证件照');if(!photo||photoUrls[person.id])return;try{photoUrls[person.id]=URL.createObjectURL(await downloadAsset(person.id,photo.id))}catch{}}))
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
    exportCandidatesCSV(rows, `人才管理-人才摘要-${new Date().toISOString().slice(0, 10)}.csv`)
    ElMessage.success(`已导出 ${rows.length} 份人才摘要`)
  } catch (e) { ElMessage.error(e.message || '导出失败') }
  finally { exporting.value = false }
}
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">YOUR TALENT UNIVERSE</div><h1>{{ isDashboard ? '让人才的故事，持续发生。' : '人才库' }}</h1><p>{{ isDashboard ? '每一次沟通、每一段成长，都在这里连接。' : '从第一次相遇，到每一段合作。所有人才，一处连接。' }}</p></div><div class="heading-actions"><button v-if="isDashboard" class="btn btn-secondary" @click="openQuestionnaireSender"><AppIcon name="arrow" :size="15" />发送人才问卷</button><button class="btn btn-secondary" :disabled="exporting" @click="exportFiltered"><AppIcon name="download" :size="15" />{{ exporting ? '整理中…' : '导出摘要' }}</button><button class="btn btn-secondary" @click="showImport = true"><AppIcon name="download" :size="15" />导入人才</button><button class="btn btn-primary" @click="showAdd = true"><AppIcon name="plus" :size="16" />新增人才</button></div></div>
  <div class="summary-grid"><div v-for="card in summaryCards" :key="card.title" class="surface summary-card"><div class="summary-top"><span>{{ card.title }}</span><span class="summary-icon"><AppIcon :name="card.icon" :size="17" /></span></div><div class="summary-number"><strong>{{ card.value ?? '—' }}</strong><small>{{ card.unit }}</small></div><div class="summary-foot"><span>{{ card.caption }}</span><AppIcon name="arrow" :size="13" /></div></div></div>
  <div v-if="isDashboard" class="overview-grid"><section class="surface overview-panel" style="grid-column: 1 / -1"><div class="section-heading"><div><h2>从相识，到同行</h2><p class="panel-caption">人才当前阶段概览</p></div></div><div class="funnel"><div v-for="[name, count] in funnel" :key="name" class="funnel-step"><div class="funnel-pill"><b>{{ count ?? '—' }}</b><span>{{ name }}</span></div></div></div><div class="funnel-note">按当前状态统计 · 非历史转化率</div></section></div>
  <section class="surface talent-panel"><div class="talent-panel-header"><div><h2>{{ isDashboard ? '人才，一览而知' : '全部人才档案' }} <span class="table-count">{{ total }} 位</span></h2><p>记录当下，也记得每一段过去。</p></div><div class="segmented"><button :class="{ active: view === 'all' }" @click="setView('all')">全部人才</button><button :class="{ active: view === 'active' }" @click="setView('active')">沟通中</button><button :class="{ active: view === 'reserve' }" @click="setView('reserve')">人才储备</button></div></div><div class="table-tools"><label class="table-search"><AppIcon name="search" :size="15" /><input v-model="filters.search" placeholder="搜索姓名、岗位、联系方式" aria-label="筛选人才" /></label><select v-model="filters.jobId" aria-label="按岗位筛选"><option value="">全部岗位</option><option v-for="job in positions" :key="job.id" :value="job.id">{{ job.name }}</option></select><select v-model="filters.status" aria-label="按状态筛选"><option value="">全部状态</option><option v-for="status in ['待联系','沟通中','面试中','Offer中','待入职','已入职','人才储备','已关闭']" :key="status">{{ status }}</option></select><select v-model="filters.source" aria-label="按来源筛选"><option value="">全部来源</option><option v-for="source in ['BOSS直聘','猎聘','智联招聘','内推','历史导入','其他']" :key="source">{{ source }}</option></select><button v-if="filters.search || filters.status || filters.jobId || filters.source" class="text-link" @click="clearFilters">重置</button></div>
    <div class="pagination pagination-top"><span>共 {{ total }} 位人才<span v-if="total"> · 当前 {{ (page - 1) * 10 + 1 }}–{{ Math.min(page * 10, total) }} 位</span></span><nav class="page-buttons" aria-label="人才列表分页"><button :disabled="page === 1 || loading" aria-label="上一页" @click="setPage(page - 1)">‹</button><button v-for="number in pages" :key="number" :class="{ active: number === page }" :aria-current="number === page ? 'page' : undefined" :disabled="loading" @click="setPage(number)">{{ number }}</button><span v-if="pageCount > 5">/ {{ pageCount }}</span><button :disabled="page === pageCount || loading" aria-label="下一页" @click="setPage(page + 1)">›</button></nav></div>
    <div v-if="error" class="error-state" role="alert">{{ error }} <button class="btn btn-small" @click="loadPersons">重新加载</button></div><div v-else-if="loading" class="loading-state"><span class="spinner"></span>正在连接人才档案…</div><div v-else-if="!persons.length" class="empty-state"><AppIcon name="people" :size="36" /><h3>{{ total ? '没有匹配的人才' : '下一次相遇，从这里开始' }}</h3><p>新增一份人才档案，或调整筛选条件。</p><button class="btn btn-primary" @click="showAdd = true">新增人才</button></div>
    <div v-else class="talent-table-wrap"><table class="talent-table"><thead><tr><th>人才</th><th>当前应聘岗位</th><th>状态</th><th>下一步</th><th>负责 HR</th><th>最近更新</th></tr></thead><tbody><tr v-for="(person,index) in persons" :key="person.id" tabindex="0" :aria-label="`打开${person.name}的人才档案`" @click="router.push('/person/'+person.id)" @keydown.enter="router.push('/person/'+person.id)"><td><div class="talent-person-cell"><span class="person-avatar large" :class="'tone-'+index%4"><img v-if="photoUrls[person.id]" :src="photoUrls[person.id]" :alt="person.name+'的证件照'" /><template v-else>{{initials(person.name)}}</template></span><div><strong>{{person.name}}</strong><small>{{person.location||'所在地待补充'}} · {{person.experience||'经验待了解'}} · {{person.source||'来源待补充'}}</small></div></div></td><td><strong class="job-name">{{person.job||'暂未关联岗位'}}</strong><small>{{salary(person)}}</small></td><td><div class="status-cell"><span class="badge" :class="statusTone(person.status)">{{person.status||'待联系'}}</span><span v-if="person.result&&person.result!=='待定'" class="badge" :class="['候选人拒绝','公司淘汰'].includes(person.result)?'rose':person.result==='录用'?'green':'gray'">{{person.result}}</span></div></td><td>{{person.nextStep||'待安排'}}</td><td>{{person.owner||'待分配'}}</td><td>{{dateTime(person.updatedAt)}}</td></tr></tbody></table></div>
  </section><CandidateFormDialog v-model="showAdd" @saved="saved" /><DataTransferDialog v-model="showImport" @saved="saved" />
  <el-dialog v-model="showQuestionnaire" title="人才问卷链接" width="min(560px,94vw)"><p class="questionnaire-send-intro">复制下面的链接发给人才。对方打开后自行选择岗位并填写资料；提交后会自动建立人才档案和应聘记录。</p><label class="field questionnaire-link"><span>公开问卷链接</span><input :value="questionnaireLink" readonly @focus="$event.target.select()" /></label><div class="dialog-actions"><button type="button" class="btn" @click="showQuestionnaire=false">关闭</button><button type="button" class="btn btn-primary" @click="copyQuestionnaire">复制链接</button></div></el-dialog>
  <el-dialog v-model="showReleaseNotes" :title="`${currentRelease.version} 版本更新`" width="min(650px,94vw)" class="release-welcome"><div class="release-welcome-head"><span>{{currentRelease.version}}</span><div><small>{{currentRelease.date}}</small><h2>{{currentRelease.title}}</h2></div></div><p>{{currentRelease.summary}}</p><ul><li v-for="change in currentRelease.changes" :key="change">{{change}}</li></ul><div class="dialog-actions"><button type="button" class="btn" @click="showReleaseNotes=false">知道了</button><router-link class="btn btn-primary" to="/updates" @click="showReleaseNotes=false">查看历史版本</router-link></div></el-dialog>
</template>

<style scoped>
.release-welcome-head{display:flex;align-items:center;gap:14px}.release-welcome-head>span{display:grid;place-items:center;width:54px;height:54px;border-radius:16px;background:#3370ff;color:#fff;font-size:18px;font-weight:850}.release-welcome-head small{color:#667085;font-size:10px}.release-welcome-head h2{margin:5px 0 0;color:#172033;font-size:18px}.release-welcome p{margin:18px 0;color:#475467;font-size:12px;line-height:1.75}.release-welcome ul{display:grid;gap:9px;margin:0 0 20px;padding:16px 18px 16px 34px;border:1px solid #d9e2ec;border-radius:12px;background:#fff;color:#344054}.release-welcome li{font-size:11px;line-height:1.65}.release-welcome li::marker{color:#3370ff}
</style>
