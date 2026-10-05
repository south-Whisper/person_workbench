<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCandidate, uploadAsset, downloadAsset, getAssetPreview, updateRecord } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import CandidateFormDialog from '@/components/CandidateFormDialog.vue'
import PersonRecordDialog from '@/components/PersonRecordDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import { dateOnly, dateTime, initials, salary } from '@/utils/format'

function offerPay(record) { return record?.salaryMode === 'annual' ? `谈定年薪 ${record.annualSalary ?? '待补充'} K/年` : `谈定月薪 ${record?.actualSalary ?? '待补充'} K/月 · 折合年薪 ${record?.annualSalary ?? (record?.actualSalary != null ? Number((Number(record.actualSalary) * Number(record.salaryMonths || 12)).toFixed(2)) : '待补充')} K/年` }
function offerMonths(record) { return record?.salaryMode === 'annual' ? '按年薪填写' : `${record?.salaryMonths || 12}薪` }

const route = useRoute(), router = useRouter()
const person = ref(null), positions = ref([]), loading = ref(true), error = ref('')
const activeTab = ref('overview'), timelineFilter = ref('all'), editPerson = ref(false)
const focusedRecordId = ref(null)
const recordOpen = ref(false), recordType = ref('events'), recordMode = ref('schedule'), editingRecord = ref(null), initialRecord = ref({})
const mergeDraft = ref(null)
const uploading = ref(false), fileInput = ref(null)
const idPhotoUrl = ref('')
const assetPreviewOpen = ref(false), assetPreviewLoading = ref(false), assetPreview = ref({}), assetPreviewUrl = ref('')
const tabs = [
  ['overview', '档案总览'], ['timeline', '完整时间轴'], ['applications', '应聘经历'], ['interviews', '面试安排'],
  ['offers', 'Offer 与薪资'], ['assets', '附件作品'], ['experiences', '成长经历'], ['employments', '员工任职']
]
const recordLabels = { applications: '新增应聘', interviews: '安排面试', offers: '新增 Offer', compensations: '记录薪资', experiences: '新增经历' }
const actionTypes = new Set(Object.keys(recordLabels))
let requestId = 0

function eventTimestamp(item) { const value = new Date(String(item.occurredAt || item.createdAt || '').replace(' ', 'T')).getTime(); return Number.isFinite(value) ? value : 0 }
const allTimeline = computed(() => [...(person.value?.events || [])].sort((a, b) => eventTimestamp(b) - eventTimestamp(a) || Number(b.id || 0) - Number(a.id || 0)))
const timeline = computed(() => timelineFilter.value === 'all' ? allTimeline.value : allTimeline.value.filter(item => item.type === timelineFilter.value || item.recordType === timelineFilter.value))
const timelineTypes = computed(() => [...new Set(allTimeline.value.map(item => item.type || item.recordType).filter(Boolean))])
function timelineStage(item) {
  if (item.type === 'interview' || item.recordType === 'interviews') return 'interview'
  if (['offer', 'negotiation', 'salary'].includes(item.type) || ['offers', 'compensations'].includes(item.recordType)) return 'offer'
  return 'communication'
}
function isRejected(item) { return /拒绝|淘汰|退出|关闭|未通过|放弃/.test([item.result, item.reason, item.status, item.title].filter(Boolean).join(' ')) }
const timelineRows = computed(() => {
  let previousYear = '', previousMonth = '', previousDay = ''
  return timeline.value.map(item => {
    const date = String(item.occurredAt || item.createdAt || '')
    const dayKey = date ? date.slice(0, 10) : '时间待补'
    const year = dayKey === '时间待补' ? '' : dayKey.slice(0, 4)
    const month = dayKey === '时间待补' ? '' : Number(dayKey.slice(5, 7))
    const day = dayKey === '时间待补' ? '时间待补' : Number(dayKey.slice(8, 10))
    const showYear = year !== previousYear
    const showMonth = showYear || `${year}-${month}` !== previousMonth
    const showDay = showMonth || dayKey !== previousDay
    previousYear = year; previousMonth = `${year}-${month}`; previousDay = dayKey
    const row = { key: `${dayKey}-${item.id}`, year, month, day, showYear, showMonth, showDay, communication: [], interview: [], offer: [] }
    row[timelineStage(item)].push(item)
    return row
  })
})
const latestApplication = computed(() => person.value?.applications?.[0])
function hasInterviewResult(applicationId) { return (person.value?.interviews || []).some(item => String(item.applicationId) === String(applicationId) && item.status === '已完成' && item.result && !['待评价', '不推荐'].includes(item.result)) }
function canIssueOffer(offer) { return hasInterviewResult(offer?.applicationId) }
const contactLine = computed(() => [person.value?.phone, person.value?.wechat, person.value?.email].filter(Boolean))
const tags = computed(() => Array.isArray(person.value?.tags) ? person.value.tags : String(person.value?.tags || '').split(/[,，]/).map(s => s.trim()).filter(Boolean))

async function load() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try {
    const [profile, jobs] = await Promise.all([getCandidate(route.params.id), positions.value.length ? positions.value : getPositionList()])
    if (id !== requestId) return
    person.value = profile; positions.value = jobs
    await loadIdPhoto(profile)
    const requestedTab = String(route.query.tab || '')
    if (tabs.some(([key]) => key === requestedTab)) activeTab.value = requestedTab
    const action = String(route.query.action || '')
    if (action === 'merge-profile') {
      try { mergeDraft.value = JSON.parse(sessionStorage.getItem('sethubCandidateMergeDraft') || 'null') } catch { mergeDraft.value = null }
      sessionStorage.removeItem('sethubCandidateMergeDraft'); editPerson.value = true; router.replace({ path: route.path })
    } else if (actionTypes.has(action)) {
      const applicationId = Number(route.query.applicationId || 0) || undefined
      openRecord(action, null, applicationId ? { applicationId } : {})
      router.replace({ path: route.path, query: requestedTab ? { tab: requestedTab } : {} })
    }
  } catch (cause) { error.value = cause.message } finally { if (id === requestId) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { if (idPhotoUrl.value) URL.revokeObjectURL(idPhotoUrl.value); if (assetPreviewUrl.value) URL.revokeObjectURL(assetPreviewUrl.value) })
watch(() => route.params.id, load)
watch(editPerson, open => { if (!open) mergeDraft.value = null })
async function loadIdPhoto(profile) {
  if (idPhotoUrl.value) URL.revokeObjectURL(idPhotoUrl.value)
  idPhotoUrl.value = ''
  const photo = (profile.assets || []).find(asset => asset.type === '证件照')
  if (!photo || photo.external) return
  try { idPhotoUrl.value = URL.createObjectURL(await downloadAsset(profile.id, photo.id)) } catch { idPhotoUrl.value = '' }
}
function openRecord(type, record = null, initial = {}, mode = 'schedule') {
  const linkedApplication = ['interviews', 'offers'].includes(type)
    ? (person.value?.applications || []).find(item => String(item.jobId) === String(person.value?.jobId)) || person.value?.applications?.[0]
    : null
  const applicationId = initial.applicationId || record?.applicationId || linkedApplication?.id
  if (type === 'offers' && !record && !hasInterviewResult(applicationId)) { ElMessage.warning('请先填写这次应聘的面试结果，再发放 Offer'); activeTab.value = 'interviews'; return }
  recordType.value = type
  recordMode.value = mode
  editingRecord.value = record
  initialRecord.value = { ...(linkedApplication ? { applicationId: linkedApplication.id } : {}), ...initial }
  recordOpen.value = true
}
async function refreshed() { await load() }
async function finishOffer(offer) {
  if (!canIssueOffer(offer)) { ElMessage.warning('请先填写这次应聘的面试结果，再发放 Offer'); activeTab.value = 'interviews'; return }
  try { await updateRecord(person.value.id, 'offers', offer.id, { ...offer, status: '已完成' }); ElMessage.success('Offer 已发送并完成，当前版本已锁定'); await load() }
  catch (cause) { ElMessage.error(cause.message) }
}
function tone(value) { return ['已入职','在职','已完成','已确认','已接受','通过'].includes(value) ? 'green' : ['已拒绝','未通过','候选人退出','公司淘汰','已关闭','已离职'].includes(value) ? 'rose' : ['Offer中','协商中','待入职'].includes(value) ? 'amber' : 'gray' }
function eventIcon(type) { return ({ interview: 'calendar', offer: 'briefcase', asset: 'download', onboarding: 'check', departure: 'logout', communication: 'globe', application: 'board', employment: 'people' })[type] || 'people' }
function eventType(type) { return ({ communication:'沟通', discovery:'发现人才', interview:'面试', negotiation:'谈薪', offer:'Offer', asset:'附件', onboarding:'入职', probation:'转正', transfer:'调岗', promotion:'晋升', salary:'薪资', departure:'离职', rehire:'返聘', application:'应聘', profile:'档案更新', import:'数据迁移', created:'建立档案', experience:'经历', employment:'员工任职' })[type] || '其他动态' }
function eventTarget(event) {
  if (['profile', 'created', 'import'].includes(event.type)) return 'overview'
  if (event.type === 'asset') return 'assets'
  if (event.type === 'interview') return 'interviews'
  if (['offer', 'negotiation', 'salary'].includes(event.type)) return 'offers'
  if (event.type === 'application') return 'applications'
  if (event.type === 'experience') return 'experiences'
  if (['employment', 'onboarding', 'probation', 'transfer', 'promotion', 'departure', 'rehire'].includes(event.type)) return 'employments'
  return 'timeline'
}
async function jumpToEvent(event) {
  const target = eventTarget(event)
  focusedRecordId.value = event.recordId || event.id
  activeTab.value = target
  if (target === 'timeline') timelineFilter.value = 'all'
  await nextTick()
  const selector = target === 'timeline' ? `[data-event-id="${event.id}"]` : `[data-record-id="${event.recordId}"]`
  document.querySelector(selector)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
function money(value) { return value == null || value === '' ? '—' : Number(value).toLocaleString('zh-CN') }
async function pickFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || uploading.value) return
  uploading.value = true
  try { await uploadAsset(person.value.id, file, '附件'); ElMessage.success('附件已上传并写入时间轴'); await load(); activeTab.value = 'assets' }
  catch (cause) { ElMessage.error(cause.message) } finally { uploading.value = false }
}
async function download(asset) {
  if (asset.external && asset.url) { window.open(asset.url, '_blank', 'noopener,noreferrer'); return }
  try {
    const blob = await downloadAsset(person.value.id, asset.id)
    const url = URL.createObjectURL(blob), anchor = document.createElement('a')
    anchor.href = url; anchor.download = asset.name || '附件'; document.body.appendChild(anchor); anchor.click(); anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) { ElMessage.error(cause.message) }
}
async function previewAsset(asset) {
  if (asset.external && asset.url) { window.open(asset.url, '_blank', 'noopener,noreferrer'); return }
  if (assetPreviewUrl.value) URL.revokeObjectURL(assetPreviewUrl.value)
  assetPreviewUrl.value = ''; assetPreview.value = { asset, kind: 'text', text: '' }; assetPreviewOpen.value = true; assetPreviewLoading.value = true
  try {
    const extension = String(asset.name || '').split('.').pop().toLowerCase()
    if (['png','jpg','jpeg','webp'].includes(extension)) { const blob = await downloadAsset(person.value.id, asset.id); assetPreviewUrl.value = URL.createObjectURL(blob); assetPreview.value.kind = 'image' }
    else if (extension === 'pdf') { const blob = await downloadAsset(person.value.id, asset.id); assetPreviewUrl.value = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' })); assetPreview.value.kind = 'pdf' }
    else { const data = await getAssetPreview(person.value.id, asset.id); assetPreview.value = { asset, kind: 'text', text: data.text || '没有可显示的内容。', extension: data.extension } }
  } catch (cause) { assetPreview.value = { asset, kind: 'error', text: cause.response?.data?.message || cause.message || '附件预览失败' } }
  finally { assetPreviewLoading.value = false }
}
</script>

<template>
  <div v-if="loading" class="surface loading-state"><span class="spinner"></span>正在展开人才的完整故事…</div>
  <section v-else-if="error" class="surface empty-state"><h1>人才档案暂时无法打开</h1><p>{{ error }}</p><button class="btn btn-primary" @click="load">重新加载</button></section>
  <template v-else-if="person">
    <div class="detail-back"><router-link to="/talents">← 返回人才库</router-link><span>Person ID · {{ person.id }}</span></div>
    <section class="surface person-hero">
      <div class="hero-avatar"><img v-if="idPhotoUrl" :src="idPhotoUrl" :alt="person.name + '的证件照'" /><span v-else>{{ initials(person.name) }}</span></div>
      <div class="hero-main"><div class="hero-title"><h1>{{ person.name }}</h1><span class="badge" :class="tone(person.status)">{{ person.status || '待联系' }}</span></div><p>{{ person.currentRole || person.job || '职业方向待补充' }}<span>·</span>{{ person.company || '长期人才档案' }}</p><div class="hero-tags"><span v-for="tag in tags.slice(0, 6)" :key="tag">{{ tag }}</span><span v-if="!tags.length">待丰富人才标签</span></div></div>
      <div class="hero-actions"><button class="btn" @click="editPerson = true">编辑档案</button><router-link class="btn btn-primary" to="/communications"><AppIcon name="globe" :size="15" />前往沟通记录</router-link></div>
      <div class="hero-facts"><div><small>当前岗位</small><b>{{ person.job || '储备人才' }}</b></div><div><small>期望薪资</small><b>{{ salary(person) }}</b></div><div><small>最近互动</small><b>{{ dateOnly(allTimeline[0]?.occurredAt || allTimeline[0]?.createdAt) }}</b></div><div><small>下一步</small><b>{{ person.nextStep || '待安排' }}</b></div></div>
    </section>

    <nav class="detail-tabs" aria-label="人才档案分区"><button v-for="[key, label] in tabs" :key="key" :class="{ active: activeTab === key }" @click="activeTab = key">{{ label }}<span v-if="Array.isArray(person[key]) && (key === 'offers' ? person.offers.length + (person.compensations?.length || 0) : person[key].length) > 0">{{ key === 'offers' ? person.offers.length + (person.compensations?.length || 0) : person[key].length }}</span></button></nav>

    <div v-if="activeTab === 'overview'" class="detail-grid">
      <div class="detail-main-column">
        <section class="surface detail-section"><div class="section-heading"><div><div class="eyebrow">PROFILE</div><h2>人才画像</h2></div><button class="text-link" @click="editPerson = true">完善资料 →</button></div><div class="profile-grid"><div><small>姓名 / 昵称</small><b>{{ person.name }}{{ person.nickname ? ' · ' + person.nickname : '' }}</b></div><div><small>性别</small><b>{{ person.gender || '未填写' }}</b></div><div><small>所在城市</small><b>{{ person.location || '待补充' }}</b></div><div><small>工作经验</small><b>{{ person.experience || '待了解' }}</b></div><div><small>当前公司</small><b>{{ person.workStatus === '在职' ? person.company || '待补充' : '离职 / 未在职' }}</b></div><div><small>当前职位</small><b>{{ person.workStatus === '在职' ? person.currentRole || '待补充' : '—' }}</b></div><div><small>HR</small><b>{{ person.owner || '待分配' }}</b></div><div><small>人才来源</small><b>{{ person.source || '待补充' }}</b></div></div><div class="profile-note"><small>人才摘要</small><p>{{ person.remark || '还没有摘要。记录擅长领域、合作偏好和值得记住的细节。' }}</p></div><div v-if="person.assets?.some(asset => asset.type === '简历')" class="resume-overview"><div><small>个人简历</small><b>{{ person.assets.find(asset => asset.type === '简历').name }}</b></div><button class="btn btn-small" @click="previewAsset(person.assets.find(asset => asset.type === '简历'))">预览</button><button class="btn btn-small" @click="download(person.assets.find(asset => asset.type === '简历'))"><AppIcon name="download" :size="14" />下载</button></div></section>
        <section class="surface detail-section"><div class="section-heading"><div><div class="eyebrow">RECENT CHAPTERS</div><h2>最近改动</h2></div><button class="text-link" @click="activeTab = 'timeline'">查看完整时间轴 →</button></div><div v-if="!allTimeline.length" class="empty-state compact-detail">还没有历史记录。</div><button v-for="event in allTimeline.slice(0, 5)" :key="event.id" type="button" class="recent-event" @click="jumpToEvent(event)"><span class="event-icon"><AppIcon :name="eventIcon(event.type)" :size="16" /></span><span class="recent-event-body"><small>{{ dateTime(event.occurredAt || event.createdAt) }} · {{ eventType(event.type) }}</small><b>{{ event.title }}</b><span>{{ event.summary || event.result || '已记录' }}</span></span><span v-if="event.result" class="badge" :class="tone(event.result)">{{ event.result }}</span><AppIcon class="recent-arrow" name="arrow" :size="14" /></button></section>
      </div>
      <aside class="detail-side-column">
        <section class="surface side-card"><h3>双方意愿</h3><div class="intent-row"><span>公司意愿</span><b>{{ person.companyIntent || latestApplication?.companyIntent || '未判断' }}</b></div><div class="intent-row"><span>人才意愿</span><b>{{ person.talentIntent || latestApplication?.talentIntent || '未知' }}</b></div><p v-if="person.reason" class="reason-box">当前结果原因：{{ person.reason }}</p></section>
        <section class="surface side-card"><h3>联系与身份</h3><div v-if="contactLine.length" class="contact-list"><span v-if="person.phone">电话 · {{ person.phone }}</span><span v-if="person.wechat">微信 · {{ person.wechat }}</span><span v-if="person.email">邮箱 · {{ person.email }}</span></div><p v-else class="muted">尚未补充联系方式。</p><span class="privacy-note">仅在当前系统内可见</span></section>
      </aside>
    </div>

    <section v-else-if="activeTab === 'timeline'" class="surface detail-section timeline-section"><div class="section-heading"><div><div class="eyebrow">UNIFIED TIMELINE</div><h2>人才历程</h2></div></div><div class="timeline-filters"><button :class="{ active: timelineFilter === 'all' }" @click="timelineFilter = 'all'">全部</button><button v-for="type in timelineTypes" :key="type" :class="{ active: timelineFilter === type }" @click="timelineFilter = type">{{ eventType(type) }}</button></div><div v-if="!timeline.length" class="empty-state">没有匹配的历史记录。</div><div v-else class="timeline-matrix"><header class="timeline-matrix-head"><span>时间</span><b>沟通及其他</b><b>面试</b><b>Offer</b></header><section v-for="row in timelineRows" :key="row.key" class="timeline-period-row"><time class="timeline-time-branch"><strong v-if="row.showYear">{{ row.year }} 年</strong><b v-if="row.showMonth">{{ row.month }} 月</b><span v-if="row.showDay">{{ row.day }}{{ row.day === '时间待补' ? '' : ' 日' }}</span></time><div v-for="stage in ['communication','interview','offer']" :key="stage" class="timeline-cell"><article v-for="event in row[stage]" :key="event.id" class="lane-event" :class="{ rejected: isRejected(event), focused: focusedRecordId === event.id }" :data-event-id="event.id"><small>{{ dateTime(event.occurredAt || event.createdAt) }}</small><h4>{{ event.title }}</h4><p>{{ event.summary || event.result || '已记录该事件' }}</p><div v-if="event.result || event.reason" class="lane-result"><span v-if="event.result">{{ event.result }}</span><small v-if="event.reason">{{ event.reason }}</small></div></article><span v-if="!row[stage].length" class="cell-empty">—</span></div></section></div></section>

    <section v-else class="surface detail-section record-section"><div class="section-heading"><div><div class="eyebrow">{{ activeTab.toUpperCase() }}</div><h2>{{ tabs.find(tab => tab[0] === activeTab)?.[1] }}</h2></div><div class="heading-actions"><input v-if="activeTab === 'assets'" ref="fileInput" hidden type="file" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.webp,.ppt,.pptx,.xls,.xlsx,.csv,.txt,.zip,.rtf,.odt" @change="pickFile"><button v-if="activeTab === 'assets'" class="btn btn-primary" :disabled="uploading" @click="fileInput?.click()">{{ uploading ? '上传中…' : '上传附件' }}</button><router-link v-else-if="activeTab === 'employments'" class="btn" to="/employees">前往员工管理</router-link><button v-else class="btn btn-primary" @click="openRecord(activeTab)"><AppIcon name="plus" :size="15" />{{ recordLabels[activeTab] }}</button></div></div>
      <div v-if="!person[activeTab]?.length" class="empty-state"><AppIcon :name="activeTab === 'interviews' ? 'calendar' : 'briefcase'" :size="36" /><h3>{{ activeTab === 'offers' ? '还没有正式 Offer 记录' : '还没有' + tabs.find(tab => tab[0] === activeTab)?.[1] + '记录' }}</h3><p>{{ activeTab === 'offers' && person.compensations?.length ? '期望薪资已经在下方自动形成薪资轨迹。' : '新增的内容会自动汇入统一时间轴。' }}</p></div>
      <div v-else-if="activeTab === 'applications'" class="application-history-grid"><article v-for="record in person.applications" :key="record.id" class="application-history-card" role="button" tabindex="0" @click="openRecord('applications', record)" @keydown.enter="openRecord('applications', record)"><header><div><small>一次独立应聘</small><h3>{{ record.jobName || '岗位待补充' }}</h3></div><span class="badge" :class="tone(record.status)">{{ record.status }}</span></header><p>{{ record.company || '未设置公司' }} · 岗位编号 {{ record.jobCode || String(record.jobId || '').padStart(3, '0') }}</p><dl><div><dt>开始日期</dt><dd>{{ dateOnly(record.startedAt || record.createdAt) }}</dd></div><div><dt>负责 HR</dt><dd>{{ record.owner || '待分配' }}</dd></div><div><dt>公司意愿</dt><dd>{{ record.companyIntent || '未判断' }}</dd></div><div><dt>人才意愿</dt><dd>{{ record.talentIntent || '未知' }}</dd></div></dl><footer><span>{{ record.nextStep || '下一步待安排' }}</span><button class="text-link" @click.stop="openRecord('applications', record)">查看并更新</button></footer></article></div>
      <div v-else class="records-list"><article v-for="record in person[activeTab]" :key="record.id" class="record-line" :class="{ focused: focusedRecordId === record.id }" :data-record-id="record.id"><div class="record-date"><b>{{ dateOnly(record.occurredAt || record.scheduledAt || record.startedAt || record.startDate || record.dueAt || record.createdAt) }}</b><small>{{ record.version ? 'V' + record.version : record.type || '' }}</small></div><div class="record-body"><div class="record-title"><h3>{{ record.title || record.jobName || record.projectName || record.organization || record.name || '人才记录' }}</h3><span v-if="record.status || record.result" class="badge" :class="tone(record.status || record.result)">{{ record.status || record.result }}</span></div>
        <template v-if="activeTab === 'applications'"><p>{{ record.company || '未设置公司' }}：{{ record.jobName }}：{{ record.jobCode || String(record.jobId || '').padStart(3, '0') }} · 岗位版本 V{{ record.jobVersion || 1 }}</p><p>公司意愿 {{ record.companyIntent || '未判断' }} · 人才意愿 {{ record.talentIntent || '未知' }} · HR {{ record.owner || '待分配' }}</p></template>
        <template v-else-if="activeTab === 'interviews'"><p>{{ record.round }} · {{ record.method }} · 面试官 {{ record.owner || '待安排' }}</p><p v-if="record.status === '已完成'">{{ record.feedback || '面试已完成，结果待补充' }}<span v-if="record.score != null"> · {{ record.score }} 分</span></p><p v-else>{{ record.reason || '面试尚未完成，暂不显示结论' }}</p></template>
        <template v-else-if="activeTab === 'offers'"><p>{{ offerPay(record) }} · 岗位预算 {{ salary(record) }} · 计划入职 {{ dateOnly(record.expectedStartDate) }} · 有效期 {{ dateOnly(record.expiresAt) }}</p><p>{{ offerMonths(record) }} · {{ record.probationMonths ? record.probationMonths + '个月试用期' : '无试用期' }} · {{ record.socialInsurance || '社保待填写' }} · HR {{ record.owner || '待安排' }}</p><p>{{ record.remark || '暂无补充说明' }}</p></template>
        <template v-else-if="activeTab === 'assets'"><p>{{ record.type || '附件' }} · {{ record.size ? Math.ceil(record.size / 1024) + ' KB' : '外部链接' }} · {{ dateTime(record.createdAt) }}</p><div class="asset-actions"><button class="text-link" @click="previewAsset(record)">预览</button><button class="text-link" @click="download(record)"><AppIcon name="download" :size="14" /> 下载</button></div></template>
        <template v-else-if="activeTab === 'experiences'"><p>{{ record.title }} · {{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }}</p><p>{{ record.description }}</p></template>
        <template v-else-if="activeTab === 'employments'"><p>{{ record.jobName || '关联岗位' }} · {{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }}</p><p>{{ record.reason || record.remark || '任职持续记录中' }}</p></template>
        <template v-else-if="activeTab === 'collaborations'"><p>{{ record.role }} · {{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }} · ¥{{ money(record.amount) }}</p><p>{{ record.feedback || '合作评价待补充' }}</p></template>
        <p v-if="record.reason && !['offers','employments'].includes(activeTab)" class="reason-inline">原因：{{ record.reason }}</p></div><div v-if="activeTab === 'offers' && record.currentRecord && record.status !== '已完成'" style="display:flex;gap:7px;flex-wrap:wrap"><button class="btn btn-small btn-plain" @click="openRecord(activeTab, record)">修改并生成 V{{ Number(record.version || 0) + 1 }}</button><button v-if="record.status === '已创建' && canIssueOffer(record)" class="btn btn-small btn-primary" @click="finishOffer(record)">发送并完成</button><span v-else-if="record.status === '已创建'" class="offer-result-required">填写面试结果后可发送</span></div><div v-else-if="activeTab === 'interviews'" class="record-interview-actions"><button class="btn btn-small btn-plain" @click="openRecord('interviews', record, {}, 'schedule')">修改安排</button><button class="btn btn-small btn-primary" @click="openRecord('interviews', record, {}, 'evaluation')">填写结果</button></div><button v-else-if="activeTab !== 'assets' && activeTab !== 'employments' && activeTab !== 'offers'" class="btn btn-small btn-plain" @click="openRecord(activeTab, record)">更新</button></article></div>
      <section v-if="activeTab === 'offers'" class="compensation-block"><div class="section-heading"><div><h2>薪资轨迹</h2><p class="muted">期望、报价、还价与入职薪资分别留存。</p></div><button class="btn" @click="openRecord('compensations')">记录薪资事实</button></div><div v-if="!person.compensations?.length" class="empty-state compact-detail">暂无独立薪资事实。</div><div v-for="item in person.compensations || []" :key="item.id" class="compensation-line"><time>{{ dateOnly(item.occurredAt) }}</time><b>{{ item.type }}</b><span>{{ salary(item) }}</span><small>{{ item.source || '来源待补充' }}</small><button class="text-link" @click="openRecord('compensations', item)">更新</button></div></section>
    </section>

    <el-dialog v-model="assetPreviewOpen" :title="assetPreview.asset?.name || '附件预览'" width="min(980px, 96vw)" top="4vh" class="asset-preview-dialog" destroy-on-close><div v-if="assetPreviewLoading" class="asset-preview-loading"><span class="spinner"></span>正在生成预览…</div><img v-else-if="assetPreview.kind === 'image'" class="asset-preview-image" :src="assetPreviewUrl" :alt="assetPreview.asset?.name" /><iframe v-else-if="assetPreview.kind === 'pdf'" class="asset-preview-frame" :src="assetPreviewUrl" title="PDF 附件预览"></iframe><pre v-else class="asset-preview-text" :class="{ error: assetPreview.kind === 'error' }">{{ assetPreview.text }}</pre><template #footer><div class="dialog-actions"><button class="btn" @click="assetPreviewOpen = false">关闭</button><button class="btn btn-primary" @click="download(assetPreview.asset)"><AppIcon name="download" :size="14" />下载原文件</button></div></template></el-dialog>
    <CandidateFormDialog v-model="editPerson" :person="person" :initial-draft="mergeDraft" @saved="refreshed" />
    <PersonRecordDialog v-model="recordOpen" :record-type="recordType" :mode="recordMode" :record="editingRecord" :person="person" :positions="positions" :initial="initialRecord" @saved="refreshed" />
  </template>
</template>

<style scoped>
.record-interview-actions{display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.offer-result-required{align-self:center;color:#a26b51;font-size:10px}
.detail-back{display:flex;justify-content:space-between;color:#9aa5af;font-size:11px;margin-bottom:16px}.detail-back a:hover{color:#60778a}.person-hero{padding:28px;display:grid;grid-template-columns:auto 1fr auto;gap:20px;align-items:center}.hero-avatar{width:72px;height:72px;border-radius:21px;display:grid;place-items:center;background:linear-gradient(145deg,#dbe4eb,#b9c9d5);color:#587084;font-size:23px}.hero-title{display:flex;align-items:center;gap:12px}.hero-title h1{font-size:27px;margin:0}.hero-main>p{font-size:13px;color:#84909c;margin:8px 0}.hero-main>p span{padding:0 8px}.hero-tags{display:flex;gap:6px;flex-wrap:wrap}.hero-tags span{font-size:10px;padding:4px 8px;border-radius:6px;background:#f0f3f6;color:#82909c}.hero-actions{display:flex;gap:8px}.hero-facts{grid-column:1/-1;display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#edf0f3;border-radius:12px;overflow:hidden;margin-top:5px}.hero-facts>div{background:#fafbfc;padding:15px 19px}.hero-facts small,.profile-grid small,.profile-note small{display:block;color:#9da7b1;font-size:10px;margin-bottom:7px}.hero-facts b{font-size:12px;font-weight:550;color:#607080}.detail-tabs{display:flex;gap:3px;overflow:auto;margin:20px 0 17px;padding:4px;background:#edf0f3aa;border-radius:12px}.detail-tabs button{border:0;background:transparent;color:#8b96a1;padding:9px 13px;border-radius:9px;font-size:11px;white-space:nowrap}.detail-tabs button.active{background:#fff;color:#546d82;box-shadow:0 1px 5px #40506310}.detail-tabs span{margin-left:6px;font-size:9px;color:#adb5be}.detail-grid{display:grid;grid-template-columns:minmax(0,1.8fr) minmax(260px,.8fr);gap:18px}.detail-main-column,.detail-side-column{display:flex;flex-direction:column;gap:18px}.detail-section{padding:25px}.profile-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:21px;padding-bottom:23px;border-bottom:1px solid #edf0f2}.profile-grid b{font-size:12px;font-weight:500;color:#586978}.profile-note{padding-top:20px}.profile-note p{font-size:12px;color:#74828e;margin:0;white-space:pre-wrap}.side-card{padding:21px}.side-card h3{font-size:13px;margin-bottom:18px}.intent-row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #eef0f2;font-size:11px;color:#97a1ab}.intent-row b{color:#5e7385}.reason-box{font-size:11px;background:#f7f2eb;color:#917e64;padding:11px;border-radius:8px;margin:14px 0 0}.contact-list{display:flex;flex-direction:column;gap:10px;font-size:11px;color:#718291}.privacy-note{display:block;font-size:9px;color:#abb3bc;margin-top:15px}.side-task{display:flex;gap:10px;padding:10px 0;border-bottom:1px solid #eef0f2}.side-task>span{width:7px;height:7px;background:#9caf9d;border-radius:50%;margin-top:4px}.side-task>span.overdue{background:#bc8f89}.side-task b{display:block;font-size:11px;font-weight:500}.side-task small{display:block;font-size:9px;color:#a1abb4;margin-top:5px}.recent-event{display:grid;grid-template-columns:auto 1fr auto;gap:12px;padding:15px 0;border-bottom:1px solid #eef0f2}.recent-event:last-child{border-bottom:0}.event-icon{width:31px;height:31px;border-radius:9px;display:grid;place-items:center;background:#eef3f6;color:#8196a8}.recent-event small{display:block;font-size:9px;color:#a4adb7}.recent-event b{display:block;font-size:12px;margin-top:5px}.recent-event p{font-size:11px;color:#89949e;margin:5px 0 0}.compact-detail{padding:28px 10px}.timeline-section{max-width:1050px;margin:0 auto}.timeline-filters{display:flex;gap:6px;overflow:auto;border-top:1px solid #eef0f2;padding-top:17px;margin-bottom:20px}.timeline-filters button{border:1px solid #e6eaee;background:#fafbfc;border-radius:7px;color:#929ca7;font-size:10px;padding:7px 10px;white-space:nowrap}.timeline-filters button.active{background:#e8eef3;color:#5c7387}.year-group{display:grid;grid-template-columns:72px 1fr;gap:18px}.year-label{font-size:18px;color:#72889b;font-weight:550;padding-top:8px}.timeline-list{list-style:none;margin:0;padding:0}.timeline-list li{position:relative;padding:0 0 22px 35px;border-left:1px solid #dce3e9}.timeline-list li:last-child{border-left-color:transparent}.timeline-dot{position:absolute;left:-14px;top:0;width:28px;height:28px;border-radius:9px;background:#e8eef3;color:#6d879b;display:grid;place-items:center;border:3px solid #fff}.timeline-card{border:1px solid #ebedf0;border-radius:13px;padding:18px;background:#fff}.timeline-top{display:flex;justify-content:space-between;gap:12px;font-size:9px;color:#a4adb6}.timeline-top>div{display:flex;align-items:center;gap:8px}.timeline-card h3{font-size:13px;margin:12px 0 7px}.timeline-card>p{font-size:12px;color:#7f8d99;margin-bottom:0;white-space:pre-wrap}.event-result{display:grid;grid-template-columns:1fr 2fr;gap:10px;background:#f7f8fa;border-radius:9px;padding:11px 13px;margin-top:13px;font-size:11px;color:#667684}.event-result small{display:block;color:#a1aab4;font-size:9px;margin-bottom:5px}.next-box{font-size:10px;color:#658070;background:#eef3ef;border-radius:8px;padding:9px 12px;margin-top:10px}.record-section{min-height:420px}.records-list{display:flex;flex-direction:column}.record-line{display:grid;grid-template-columns:105px 1fr auto;gap:20px;padding:21px 0;border-top:1px solid #edf0f2}.record-date b{display:block;font-size:11px;color:#677b8d}.record-date small{font-size:9px;color:#a6afb8}.record-title{display:flex;align-items:center;gap:10px}.record-title h3{font-size:13px;margin:0}.record-body p{font-size:11px;color:#87939f;margin:8px 0 0;white-space:pre-wrap}.reason-inline{background:#faf5ef;padding:8px;border-radius:7px;color:#967d64!important}.record-body .text-link{display:inline-flex;align-items:center;gap:5px;margin-top:8px}.compensation-block{border-top:1px solid #e7eaed;margin-top:25px;padding-top:25px}.compensation-line{display:grid;grid-template-columns:90px 1fr 1fr 1fr auto;gap:12px;align-items:center;padding:12px 0;border-top:1px solid #eef0f2;font-size:11px}.compensation-line time,.compensation-line small{color:#98a2ac}.compensation-line b{font-weight:500}
.horizontal-timeline{display:grid;grid-template-columns:repeat(4,minmax(210px,1fr));gap:13px;overflow-x:auto;padding:6px 2px 15px}.timeline-lane{position:relative;min-width:210px;padding:0 11px 16px;border-radius:15px;background:#f7f9fc}.timeline-lane header{position:sticky;top:0;z-index:2;display:flex;align-items:center;gap:8px;padding:14px 1px 11px;background:#f7f9fc}.timeline-lane header>span{width:8px;height:8px;border-radius:50%;background:#5d8ef7}.timeline-lane header h3{font-size:12px;margin:0;flex:1}.timeline-lane header b{font-size:10px;color:#8b96a2}.lane-rejection{background:#fff5f3;border:1px solid #f1c8c1}.lane-rejection header{background:#fff5f3}.lane-rejection header>span{background:#e05252}.lane-interview header>span{background:#7a68d8}.lane-offer header>span{background:#d49335}.lane-line{height:2px;margin:0 0 12px;background:linear-gradient(90deg,currentColor,transparent);color:#bfd0ef}.lane-rejection .lane-line{color:#efaaa0}.lane-event{position:relative;margin:0 0 10px;padding:13px;border:1px solid #e5eaf0;border-radius:12px;background:#fff;box-shadow:0 4px 12px rgba(45,65,90,.04)}.lane-rejection .lane-event{border-color:#edb9b1}.lane-event time{font-size:9px;color:#98a4b2}.lane-event h4{font-size:11px;margin:7px 0}.lane-event p{font-size:10px;line-height:1.65;color:#7d8996;margin:0}.lane-result{display:flex;flex-direction:column;gap:4px;margin-top:9px;padding:8px;border-radius:8px;background:#f3f6fa;color:#68798b}.lane-rejection .lane-result{background:#fff0ed;color:#ae4e43}.lane-result span{font-size:10px;font-weight:600}.lane-result small{font-size:9px}.lane-empty{padding:28px 0;text-align:center;color:#a5aeb8;font-size:10px}
.asset-actions{display:flex;gap:12px;margin-top:8px}.asset-preview-loading{display:flex;align-items:center;justify-content:center;gap:10px;min-height:420px;color:#7e8b9a}.asset-preview-image{display:block;max-width:100%;max-height:68vh;margin:auto;object-fit:contain;border-radius:10px}.asset-preview-frame{display:block;width:100%;height:68vh;border:0;border-radius:10px;background:#f2f4f7}.asset-preview-text{box-sizing:border-box;min-height:420px;max-height:68vh;overflow:auto;margin:0;padding:22px;border-radius:12px;background:#f7f9fc;color:#43556b;font:12px/1.85 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;word-break:break-word}.asset-preview-text.error{color:#a35347;background:#fff3f1}
.hero-avatar{overflow:hidden}.hero-avatar img{width:100%;height:100%;object-fit:cover}.resume-overview{display:flex;align-items:center;justify-content:space-between;gap:15px;margin-top:20px;padding:14px 16px;border:1px solid #e6ebf0;border-radius:12px;background:#f8faff}.resume-overview small{display:block;color:#9aa7b5;font-size:10px;margin-bottom:5px}.resume-overview b{font-size:12px;color:#53687e}.timeline-matrix{min-width:760px;overflow-x:auto;border:1px solid #e6ebf1;border-radius:15px}.timeline-matrix-head,.timeline-period-row{display:grid;grid-template-columns:118px repeat(3,minmax(190px,1fr))}.timeline-matrix-head{position:sticky;top:0;z-index:3;background:#f2f6fb;border-bottom:1px solid #dfe7f0}.timeline-matrix-head>*{padding:13px 15px;font-size:11px;color:#677b91}.timeline-matrix-head b:not(:last-child),.timeline-period-row>*:not(:last-child){border-right:1px solid #e8edf3}.timeline-period-row{border-bottom:1px solid #e8edf3}.timeline-period-row:last-child{border-bottom:0}.timeline-period-row>time{padding:17px 13px;background:#fafbfd;color:#5d7188;font-size:11px;font-weight:600;line-height:1.6}.timeline-cell{min-height:94px;padding:11px;background:#f8fafd}.timeline-cell:nth-child(3){background:#faf9ff}.timeline-cell:nth-child(4){background:#fffaf2}.timeline-cell .lane-event{margin-bottom:8px}.timeline-cell .lane-event:last-of-type{margin-bottom:0}.lane-event>small{font-size:9px;color:#96a3b1}.lane-event.rejected{border-color:#e56a5a;background:#fff5f3;box-shadow:inset 3px 0 0 #e05252}.lane-event.rejected .lane-result{background:#ffe9e5;color:#ad4135}.cell-empty{display:grid;place-items:center;height:100%;min-height:65px;color:#c0c7d0}
.recent-event{width:100%;border:0;background:transparent;text-align:left;color:inherit;cursor:pointer}.recent-event:hover{background:#f7f9fb}.recent-event-body{min-width:0}.recent-event-body small,.recent-event-body b,.recent-event-body>span{display:block}.recent-event-body b{font-size:12px;margin-top:5px}.recent-event-body>span{font-size:11px;color:#89949e;margin-top:5px}.recent-arrow{color:#9aa8b6;align-self:center}.timeline-time-branch{position:relative!important;padding:18px 10px 18px 36px!important;background:#fbfcfd!important}.timeline-time-branch::before{content:'';position:absolute;left:19px;top:0;bottom:0;width:2px;background:#d7e1eb}.timeline-time-branch::after{content:'';position:absolute;left:19px;top:33px;width:11px;height:2px;background:#d7e1eb}.timeline-time-branch strong,.timeline-time-branch b,.timeline-time-branch span{display:block}.timeline-time-branch strong{font-size:18px;line-height:1.2;color:#334b64;font-weight:750;margin-bottom:6px}.timeline-time-branch b{font-size:14px;color:#59718a;margin-bottom:4px}.timeline-time-branch span{font-size:11px;color:#8090a1}.lane-event.focused,.record-line.focused{outline:2px solid #6f99c4;outline-offset:2px;animation:focus-arrive 1.4s ease}.record-line.focused{background:#f2f7fb;border-radius:10px;padding-left:10px;padding-right:10px}@keyframes focus-arrive{0%,45%{box-shadow:0 0 0 7px rgba(90,137,182,.16)}100%{box-shadow:none}}
@media(max-width:950px){.detail-grid{grid-template-columns:1fr}.detail-side-column{display:grid;grid-template-columns:repeat(3,1fr)}.profile-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){.person-hero{grid-template-columns:auto 1fr;padding:20px}.hero-actions{grid-column:1/-1}.hero-actions .btn{flex:1}.hero-facts{grid-template-columns:repeat(2,1fr)}.detail-side-column{display:flex}.profile-grid{grid-template-columns:1fr 1fr}.year-group{grid-template-columns:1fr}.year-label{margin-top:15px}.record-line{grid-template-columns:1fr}.record-line>.btn{justify-self:start}.compensation-line{grid-template-columns:1fr 1fr}.person-hero .hero-avatar{width:57px;height:57px;border-radius:17px}.timeline-section .section-heading{align-items:flex-start;flex-wrap:wrap}}@media(max-width:450px){.profile-grid,.hero-facts{grid-template-columns:1fr}.hero-title{align-items:flex-start;flex-direction:column;gap:6px}.detail-section{padding:18px}.event-result{grid-template-columns:1fr}}
</style>
<style scoped>
.application-history-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.application-history-card{padding:19px;border:1px solid #e1e7ef;border-radius:15px;background:linear-gradient(145deg,#fff,#f7faff);cursor:pointer;transition:transform .18s,box-shadow .18s,border-color .18s}.application-history-card:hover,.application-history-card:focus-visible{transform:translateY(-2px);border-color:#b9cae3;box-shadow:0 12px 28px rgba(48,73,105,.1);outline:0}.application-history-card header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.application-history-card header small{color:#8598af;font-size:9px;letter-spacing:.5px}.application-history-card h3{margin:6px 0 0;color:#344a64;font-size:15px}.application-history-card>p{margin:12px 0;color:#7c8998;font-size:10px}.application-history-card dl{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:0}.application-history-card dl>div{padding:10px;border-radius:9px;background:#f2f5f9}.application-history-card dt{color:#9ba6b3;font-size:8px}.application-history-card dd{margin:5px 0 0;color:#5f7187;font-size:10px}.application-history-card footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px;padding-top:12px;border-top:1px solid #e8edf3;color:#8c98a6;font-size:10px}@media(max-width:700px){.application-history-grid{grid-template-columns:1fr}}
</style>
