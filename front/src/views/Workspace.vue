<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCandidateList, getWorkspace, createRecord, updateRecord } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import AppIcon from '@/components/AppIcon.vue'
import { dateTime, dateOnly, isOverdue, salary } from '@/utils/format'
const route = useRoute(), router = useRouter()
const data = ref({}), loading = ref(true), error = ref(''), search = ref(''), state = ref(''), tab = ref('applications'), busy = ref(false)
const picker = ref(false), people = ref([]), personSearch = ref(''), personLoading = ref(false), pickerError = ref('')
const transition = ref(null), transitionForm = reactive({ status: '', reason: '' })
const converting = ref(null), jobs = ref([]), convertJob = ref('')
const definitions = {
  opportunities: ['主动寻访', '从主动发现，到双向奔赴。长期维护每一份有价值的连接。', 'opportunities', '新增寻访机会', 'globe'],
  recruitment: ['招聘流程', '每一次应聘独立推进，每一次选择都保留原因。', 'applications', '新增应聘流程', 'board'],
  interviews: ['面试与评价', '记录真实表现，让每一个判断都有依据。', 'interviews', '安排面试', 'calendar'],
  employees: ['员工生命周期', '从入职到成长、离职与返聘，同一份档案始终相连。', 'employments', '新增任职记录', 'people'],
  collaborations: ['项目合作', '连接导演、摄影师、顾问与伙伴，沉淀每一段合作。', 'collaborations', '新增合作记录', 'globe'],
  tasks: ['跟进任务', '让每一次沟通，都有明确的下一步。', 'tasks', '新增任务', 'clock'],
  analytics: ['数据概览', '从真实的人才与招聘记录中，理解连接的进展。', '', '', 'chart']
}
const key = computed(() => route.path.slice(1))
const config = computed(() => definitions[key.value] || definitions.tasks)
const type = computed(() => key.value === 'recruitment' ? tab.value : config.value[2])
const records = computed(() => (data.value[type.value] || []).filter(r => {
  const query = search.value.trim().toLowerCase()
  const searchable = [r.personName, r.jobName, r.title, r.projectName, r.owner, r.interviewer, r.reason, r.feedback].join(' ').toLowerCase()
  return (!query || searchable.includes(query)) && (!state.value || (state.value === '逾期' ? isOverdue(r) : state.value === '待完成' ? r.status !== '已完成' : r.status === state.value))
}))
const states = computed(() => [...new Set((data.value[type.value] || []).map(r => r.status).filter(Boolean))])
const columns = [
  { title: '初识与沟通', match: ['已创建','初筛','沟通','沟通中','待联系','新建'] },
  { title: '面试与评估', match: ['面试安排','面试中','内部决策','面试'] },
  { title: '谈薪与 Offer', match: ['谈薪','Offer','Offer中','待入职'] },
  { title: '已入职', match: ['已入职'] },
  { title: '储备与关闭', match: [] }
]
const board = computed(() => columns.map((column, index) => ({ ...column, records: records.value.filter(r => index === 4 ? !columns.slice(0, 4).some(c => c.match.includes(r.status)) : column.match.includes(r.status)) })))
const currentStats = computed(() => data.value.stats || {})
const applicationStatuses = ['已创建','初筛','沟通','面试安排','面试中','内部决策','谈薪','Offer','待入职','已入职','候选人退出','公司淘汰','岗位暂停','岗位取消','人才储备','长期无响应','已关闭']
const opportunityStatuses = ['新发现','待联系','已联系','有效沟通','持续培育','暂不考虑','对方拒绝','公司放弃','重新激活']
const transitionOptions = computed(() => transition.value?.type === 'opportunities' ? opportunityStatuses : applicationStatuses)
let epoch = 0, pickEpoch = 0
async function load() {
  const id = ++epoch; loading.value = true; error.value = ''
  try { const result = await getWorkspace(); if (id === epoch) data.value = result } catch (e) { if (id === epoch) error.value = e.message } finally { if (id === epoch) loading.value = false }
}
onMounted(load)
watch(() => route.path, () => { search.value = ''; state.value = ''; tab.value = 'applications'; load() })
async function searchPeople() {
  const id = ++pickEpoch; personLoading.value = true; pickerError.value = ''
  try { const result = await getCandidateList({ search: personSearch.value, page: 1, size: 10 }); if (id === pickEpoch) people.value = result.items || [] }
  catch (e) { if (id === pickEpoch) pickerError.value = e.message } finally { if (id === pickEpoch) personLoading.value = false }
}
function openPicker() { picker.value = true; personSearch.value = ''; searchPeople() }
function choosePerson(person) { picker.value = false; router.push({ path: '/person/' + person.id, query: { action: type.value } }) }
async function complete(record) {
  if (busy.value) return; busy.value = true
  try { await updateRecord(record.personId, 'tasks', record.id, { ...record, status: '已完成' }); ElMessage.success('任务已完成'); await load() }
  catch (e) { ElMessage.error(e.message) } finally { busy.value = false }
}
function openTransition(record, recordType) { transition.value = { record, type: recordType }; transitionForm.status = record.status; transitionForm.reason = '' }
async function saveTransition() {
  if (busy.value) return
  if (!transitionForm.reason.trim()) { ElMessage.warning('请填写本次推进或关闭的原因'); return }
  busy.value = true
  try { const { record, type: recordType } = transition.value; await updateRecord(record.personId, recordType, record.id, { ...record, ...transitionForm }); transition.value = null; ElMessage.success('已更新并保留历史'); await load() }
  catch (e) { ElMessage.error(e.message) } finally { busy.value = false }
}
async function openConversion(record) {
  try { jobs.value = await getPositionList(); converting.value = record; convertJob.value = record.jobId || '' } catch (e) { ElMessage.error(e.message) }
}
async function convert() {
  if (!convertJob.value) { ElMessage.warning('请先选择本次应聘岗位'); return }
  busy.value = true
  try {
    await createRecord(converting.value.personId, 'applications', { jobId: Number(convertJob.value), opportunityId: converting.value.id, status: '已创建', owner: converting.value.owner, startedAt: new Date().toISOString(), companyIntent: converting.value.companyIntent, talentIntent: converting.value.talentIntent })
    converting.value = null; ElMessage.success('已创建独立应聘流程，寻访历史保留'); await load()
  } catch (e) { ElMessage.error(e.message) } finally { busy.value = false }
}
function topEntries(object) { return Object.entries(object || {}).sort((a, b) => b[1] - a[1]).slice(0, 10) }
function barWidth(object, value) { return Math.max(2, value / Math.max(1, ...Object.values(object || {})) * 100) + '%' }
function label(record) { return record.title || record.projectName || record.jobName || record.job || record.round || '人才记录' }
function timing(record) { return record.scheduledAt || record.dueAt || record.startedAt || record.startDate || record.createdAt }
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">CONNECTED THROUGH EVERY CHAPTER</div><h1>{{ config[0] }}</h1><p>{{ config[1] }}</p></div><button v-if="config[3]" class="btn btn-primary" @click="openPicker"><AppIcon name="plus" :size="16" />{{ type === 'offers' ? '创建 Offer' : config[3] }}</button><button v-else class="btn" @click="load">刷新数据</button></div>
  <div v-if="error" class="error-state" role="alert">{{ error }} <button class="btn btn-small" @click="load">重试</button></div><div v-else-if="loading" class="surface loading-state"><span class="spinner"></span>正在读取工作空间…</div>
  <template v-else-if="key === 'analytics'">
    <div class="summary-grid"><section v-for="[name, count, unit] in [['人才总量',currentStats.total,'位'],['应聘流程',(data.applications || []).length,'次'],['Offer记录',(data.offers || []).length,'份'],['待完成任务',(data.tasks || []).filter(t => t.status !== '已完成').length,'项']]" :key="name" class="surface summary-card"><div class="summary-top">{{ name }}</div><div class="summary-number"><strong>{{ count ?? '—' }}</strong><small>{{ unit }}</small></div></section></div>
    <div class="notice-inline">按当前工作空间可见记录统计。一个人可有多次应聘，流程数不等于人数；Offer 数包含不同版本。</div>
    <div class="analytics-grid"><section v-for="[title, object] in [['人才来源',data.sources],['当前人才阶段',data.stages],['招聘结果',data.outcomes],['关联岗位分布',data.jobs]]" :key="title" class="surface analysis-panel"><h2>{{ title }}</h2><div v-if="!topEntries(object).length" class="empty-state">新增业务记录后，这里会呈现真实数据。</div><div v-for="[name, value] in topEntries(object)" :key="name" class="bar-row"><span>{{ name || '未填写' }}</span><div class="bar-track"><div class="bar-fill" :style="{ width: barWidth(object, value) }"></div></div><b>{{ value }}</b></div></section></div>
  </template>
  <template v-else>
    <div class="workspace-toolbar"><label class="table-search"><AppIcon name="search" :size="15" /><input v-model="search" :aria-label="'搜索' + config[0]" placeholder="搜索人才、岗位、负责人…" /></label><select v-model="state" aria-label="筛选状态"><option value="">全部状态</option><option v-if="key === 'tasks'" value="待完成">待完成</option><option v-if="key === 'tasks'" value="逾期">已逾期</option><option v-for="option in states" :key="option">{{ option }}</option></select><div v-if="key === 'recruitment'" class="segmented"><button :class="{ active: tab === 'applications' }" @click="tab = 'applications'; state = ''">应聘看板</button><button :class="{ active: tab === 'offers' }" @click="tab = 'offers'; state = ''">Offer 记录</button></div><span class="muted" style="align-self:center">{{ records.length }} 条记录</span></div>
    <div v-if="key === 'recruitment' && tab === 'applications'" class="board-grid"><section v-for="column in board" :key="column.title" class="board-column"><h2 class="board-column-title">{{ column.title }}<span>{{ column.records.length }}</span></h2><article v-for="record in column.records" :key="record.id" class="board-card"><router-link :to="'/person/' + record.personId"><h3>{{ record.personName }}</h3></router-link><p>{{ record.jobName || '岗位待补充' }}<span v-if="record.jobVersion"> · V{{ record.jobVersion }}</span></p><span class="badge" :class="record.status === '已入职' ? 'green' : ''">{{ record.status }}</span><small>公司：{{ record.companyIntent || '未判断' }} · 人才：{{ record.talentIntent || '未知' }}</small><small>{{ dateOnly(record.startedAt || record.createdAt) }} · {{ record.owner || '待分配' }}</small><p v-if="record.reason" style="margin-top:12px">{{ record.reason }}</p><button class="text-link" style="margin-top:14px;font-size:10px" @click="openTransition(record, 'applications')">推进 / 记录结果 →</button></article><div v-if="!column.records.length" class="compact-empty">暂无流程</div></section></div>
    <div v-else-if="!records.length" class="surface empty-state"><AppIcon :name="config[4]" :size="36" /><h3>还没有{{ config[0] }}记录</h3><p>从一位已建档人才开始，记录下一段故事。</p><button class="btn btn-primary" @click="openPicker">{{ type === 'offers' ? '创建 Offer' : config[3] }}</button></div>
    <div v-else class="workspace-grid"><article v-for="record in records" :key="record.id" class="surface record-card"><div class="record-topline"><router-link :to="'/person/' + record.personId" class="text-link">{{ record.personName }}</router-link><span class="badge" :class="record.status === '已完成' || record.status === '已入职' || record.status === '已接受' ? 'green' : isOverdue(record) ? 'rose' : ''">{{ isOverdue(record) && key === 'tasks' ? '已逾期' : record.status || '已记录' }}</span></div><h3>{{ label(record) }}</h3>
      <template v-if="key === 'interviews'"><p>{{ record.round || '面试' }} · {{ record.method || '方式待补充' }} · {{ record.interviewer || '面试官待安排' }}</p><p v-if="record.feedback">{{ record.feedback }}</p><span v-if="record.score != null" class="badge">面评 {{ record.score }} 分</span><span v-if="record.result" class="badge gray" style="margin-left:6px">{{ record.result }}</span></template>
      <template v-else-if="type === 'offers'"><p>Offer V{{ record.version || 1 }} · {{ salary(record) }}</p><p>预计入职 {{ dateOnly(record.expectedStartDate) }}<br>有效期至 {{ dateOnly(record.expiresAt) }}</p></template>
      <template v-else-if="key === 'opportunities'"><p>公司：{{ record.companyIntent || '未判断' }} · 人才：{{ record.talentIntent || '未知' }}</p><p>{{ record.nextStep || record.summary || '下一步待安排' }}</p></template>
      <template v-else-if="key === 'collaborations'"><p>{{ record.role || '合作角色待补充' }} · {{ dateOnly(record.startDate) }} — {{ dateOnly(record.endDate) }}</p><p>{{ record.feedback || '合作评价待补充' }}</p></template>
      <template v-else-if="key === 'employees'"><p>{{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }}</p><p>{{ record.reason || '持续记录这段任职中的成长与变化。' }}</p></template>
      <p v-if="record.reason && key !== 'employees'">原因：{{ record.reason }}</p><div class="record-meta"><span>{{ dateTime(timing(record)) }}</span><span>{{ record.owner || record.actor || '已记录' }}</span></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:19px"><button v-if="key === 'tasks' && record.status !== '已完成'" class="btn btn-small" :disabled="busy" @click="complete(record)">标记完成</button><template v-if="key === 'opportunities' && record.status !== '已转化'"><button class="btn btn-small" @click="openTransition(record, 'opportunities')">更新进展</button><button class="btn btn-small btn-primary" @click="openConversion(record)">进入正式招聘</button></template><router-link class="btn btn-small btn-plain" :to="{ path: '/person/' + record.personId, query: { tab: type } }">查看完整档案 →</router-link></div>
    </article></div>
  </template>
  <el-dialog v-model="picker" title="选择一位人才" width="560px"><p class="muted">每条业务记录都关联到人才的永久档案。</p><form class="workspace-toolbar" @submit.prevent="searchPeople"><input v-model="personSearch" placeholder="搜索姓名或联系方式" aria-label="查找关联人才" style="flex:1" /><button class="btn" :disabled="personLoading">搜索</button></form><p v-if="pickerError" class="error-state">{{ pickerError }}</p><div v-else-if="personLoading" class="loading-state">正在查找…</div><div v-else><button v-for="person in people" :key="person.id" class="picker-person" @click="choosePerson(person)"><span><b>{{ person.name }}</b><small>{{ person.job || '暂未关联岗位' }} · {{ person.source || '来源待补充' }}</small></span><AppIcon name="arrow" :size="16" /></button><p v-if="!people.length" class="empty-state">未找到人才。请先在人才库建立档案。</p></div><div class="dialog-actions"><button class="btn" @click="picker = false">取消</button><router-link class="btn btn-primary" to="/talents" @click="picker = false">前往人才库</router-link></div></el-dialog>
  <el-dialog :model-value="!!transition" @update:model-value="value => { if (!value && !busy) transition = null }" title="记录流程进展" width="540px"><form @submit.prevent="saveTransition"><p class="muted">{{ transition?.record.personName }} · 当前 {{ transition?.record.status }}</p><label class="field">更新状态<select v-model="transitionForm.status"><option v-for="option in transitionOptions" :key="option">{{ option }}</option></select></label><label class="field" style="margin-top:20px">本次原因 / 结果<textarea v-model="transitionForm.reason" rows="4" required placeholder="说明本次决定；若未入职，请记录具体原因。" maxlength="4000"></textarea></label><div class="dialog-actions"><button type="button" class="btn" :disabled="busy" @click="transition = null">取消</button><button class="btn btn-primary" :disabled="busy">{{ busy ? '保存中…' : '保存并生成时间轴' }}</button></div></form></el-dialog>
  <el-dialog :model-value="!!converting" @update:model-value="value => { if (!value && !busy) converting = null }" title="进入正式招聘" width="510px"><p class="muted">{{ converting?.personName }} 的寻访记录和历史沟通将被保留。</p><label class="field">本次应聘岗位<select v-model="convertJob"><option value="">选择岗位版本</option><option v-for="job in jobs.filter(j => j.status === '招聘中')" :key="job.id" :value="job.id">{{ job.name }} · V{{ job.version }}</option></select></label><div class="dialog-actions"><button class="btn" :disabled="busy" @click="converting = null">取消</button><button class="btn btn-primary" :disabled="busy" @click="convert">{{ busy ? '创建中…' : '创建应聘流程' }}</button></div></el-dialog>
</template>
<style scoped>
.picker-person{width:100%;display:flex;align-items:center;justify-content:space-between;padding:15px;border:0;border-bottom:1px solid #edf0f3;background:none;text-align:left;color:#6d8195;border-radius:7px}.picker-person:hover{background:#f0f4f7}.picker-person b{display:block;font-size:14px;font-weight:500}.picker-person small{display:block;font-size:11px;color:#9aa6b1;margin-top:6px}
</style>
