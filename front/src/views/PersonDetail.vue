<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCandidate, uploadAsset, downloadAsset } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import CandidateFormDialog from '@/components/CandidateFormDialog.vue'
import PersonRecordDialog from '@/components/PersonRecordDialog.vue'
import AppIcon from '@/components/AppIcon.vue'
import { dateOnly, dateTime, initials, isOverdue, salary } from '@/utils/format'

const route = useRoute(), router = useRouter()
const person = ref(null), positions = ref([]), loading = ref(true), error = ref('')
const activeTab = ref('overview'), timelineFilter = ref('all'), editPerson = ref(false)
const recordOpen = ref(false), recordType = ref('events'), editingRecord = ref(null), initialRecord = ref({})
const uploading = ref(false), fileInput = ref(null)
const tabs = [
  ['overview', '档案总览'], ['timeline', '完整时间轴'], ['applications', '应聘经历'], ['interviews', '面试评价'],
  ['offers', 'Offer 与薪资'], ['assets', '附件作品'], ['experiences', '成长经历'], ['employments', '任职关系'],
  ['collaborations', '项目合作'], ['tasks', '跟进任务']
]
const recordLabels = { events: '新增动态', opportunities: '新增寻访', applications: '新增应聘', interviews: '安排面试', offers: '新增 Offer 版本', compensations: '记录薪资', experiences: '新增经历', employments: '新增任职', collaborations: '新增合作', tasks: '新增任务' }
const actionTypes = new Set(Object.keys(recordLabels))
let requestId = 0

const allTimeline = computed(() => [...(person.value?.events || [])].sort((a, b) => String(b.occurredAt || b.createdAt || '').localeCompare(String(a.occurredAt || a.createdAt || ''))))
const timeline = computed(() => timelineFilter.value === 'all' ? allTimeline.value : allTimeline.value.filter(item => item.type === timelineFilter.value || item.recordType === timelineFilter.value))
const timelineYears = computed(() => {
  const groups = new Map()
  for (const item of timeline.value) {
    const date = item.occurredAt || item.createdAt
    const year = date ? String(date).slice(0, 4) : '时间待补'
    if (!groups.has(year)) groups.set(year, [])
    groups.get(year).push(item)
  }
  return [...groups.entries()]
})
const timelineTypes = computed(() => [...new Set(allTimeline.value.map(item => item.type || item.recordType).filter(Boolean))])
const openTasks = computed(() => (person.value?.tasks || []).filter(item => item.status !== '已完成' && item.status !== '已取消'))
const latestApplication = computed(() => person.value?.applications?.[0])
const contactLine = computed(() => [person.value?.phone, person.value?.wechat, person.value?.email].filter(Boolean))
const tags = computed(() => Array.isArray(person.value?.tags) ? person.value.tags : String(person.value?.tags || '').split(/[,，]/).map(s => s.trim()).filter(Boolean))

async function load() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try {
    const [profile, jobs] = await Promise.all([getCandidate(route.params.id), positions.value.length ? positions.value : getPositionList()])
    if (id !== requestId) return
    person.value = profile; positions.value = jobs
    const requestedTab = String(route.query.tab || '')
    if (tabs.some(([key]) => key === requestedTab)) activeTab.value = requestedTab
    const action = String(route.query.action || '')
    if (actionTypes.has(action)) {
      openRecord(action)
      router.replace({ path: route.path, query: requestedTab ? { tab: requestedTab } : {} })
    }
  } catch (cause) { error.value = cause.message } finally { if (id === requestId) loading.value = false }
}
onMounted(load)
watch(() => route.params.id, load)
function openRecord(type, record = null, initial = {}) { recordType.value = type; editingRecord.value = record; initialRecord.value = initial; recordOpen.value = true }
async function refreshed() { await load() }
function tone(value) { return ['已入职','在职','已完成','已接受','通过'].includes(value) ? 'green' : ['已拒绝','未通过','候选人退出','公司淘汰','已关闭','已离职'].includes(value) ? 'rose' : ['Offer中','协商中','待入职'].includes(value) ? 'amber' : 'gray' }
function eventIcon(type) { return ({ interview: 'calendar', offer: 'briefcase', onboarding: 'check', departure: 'logout', project: 'globe', communication: 'globe', application: 'board', task: 'clock' })[type] || 'people' }
function eventType(type) { return ({ communication:'沟通', discovery:'发现人才', interview:'面试', negotiation:'谈薪', offer:'Offer', onboarding:'入职', probation:'转正', transfer:'调岗', promotion:'晋升', salary:'薪资', departure:'离职', rehire:'返聘', project:'项目合作', application:'应聘', profile:'档案更新', import:'数据迁移', created:'建立档案', task:'跟进任务', opportunity:'主动寻访' })[type] || '其他动态' }
function money(value) { return value == null || value === '' ? '—' : Number(value).toLocaleString('zh-CN') }
async function pickFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || uploading.value) return
  uploading.value = true
  try { await uploadAsset(person.value.id, file); ElMessage.success('附件已上传并写入时间轴'); await load(); activeTab.value = 'assets' }
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
</script>

<template>
  <div v-if="loading" class="surface loading-state"><span class="spinner"></span>正在展开人才的完整故事…</div>
  <section v-else-if="error" class="surface empty-state"><h1>人才档案暂时无法打开</h1><p>{{ error }}</p><button class="btn btn-primary" @click="load">重新加载</button></section>
  <template v-else-if="person">
    <div class="detail-back"><router-link to="/talents">← 返回人才库</router-link><span>Person ID · {{ person.id }}</span></div>
    <section class="surface person-hero">
      <div class="hero-avatar">{{ initials(person.name) }}</div>
      <div class="hero-main"><div class="hero-title"><h1>{{ person.name }}</h1><span class="badge" :class="tone(person.status)">{{ person.status || '待联系' }}</span></div><p>{{ person.currentRole || person.job || '职业方向待补充' }}<span>·</span>{{ person.company || '长期人才档案' }}</p><div class="hero-tags"><span v-for="tag in tags.slice(0, 6)" :key="tag">{{ tag }}</span><span v-if="!tags.length">待丰富人才标签</span></div></div>
      <div class="hero-actions"><button class="btn" @click="editPerson = true">编辑档案</button><button class="btn btn-primary" @click="openRecord('events')"><AppIcon name="plus" :size="15" />新增动态</button></div>
      <div class="hero-facts"><div><small>当前岗位</small><b>{{ person.job || '储备人才' }}</b></div><div><small>期望薪资</small><b>{{ salary(person) }}</b></div><div><small>最近互动</small><b>{{ dateOnly(allTimeline[0]?.occurredAt || allTimeline[0]?.createdAt) }}</b></div><div><small>下一步</small><b>{{ person.nextStep || openTasks[0]?.title || '待安排' }}</b></div></div>
    </section>

    <nav class="detail-tabs" aria-label="人才档案分区"><button v-for="[key, label] in tabs" :key="key" :class="{ active: activeTab === key }" @click="activeTab = key">{{ label }}<span v-if="Array.isArray(person[key])">{{ person[key].length }}</span></button></nav>

    <div v-if="activeTab === 'overview'" class="detail-grid">
      <div class="detail-main-column">
        <section class="surface detail-section"><div class="section-heading"><div><div class="eyebrow">PROFILE</div><h2>人才画像</h2></div><button class="text-link" @click="editPerson = true">完善资料 →</button></div><div class="profile-grid"><div><small>姓名 / 昵称</small><b>{{ person.name }}{{ person.nickname ? ' · ' + person.nickname : '' }}</b></div><div><small>性别</small><b>{{ person.gender || '未填写' }}</b></div><div><small>所在城市</small><b>{{ person.location || '待补充' }}</b></div><div><small>工作经验</small><b>{{ person.experience || '待了解' }}</b></div><div><small>当前公司</small><b>{{ person.company || '待补充' }}</b></div><div><small>当前职位</small><b>{{ person.currentRole || '待补充' }}</b></div><div><small>负责人</small><b>{{ person.owner || '待分配' }}</b></div><div><small>人才来源</small><b>{{ person.source || '待补充' }}</b></div></div><div class="profile-note"><small>人才摘要</small><p>{{ person.remark || '还没有摘要。记录擅长领域、合作偏好和值得记住的细节。' }}</p></div></section>
        <section class="surface detail-section"><div class="section-heading"><div><div class="eyebrow">RECENT CHAPTERS</div><h2>最近发生</h2></div><button class="text-link" @click="activeTab = 'timeline'">查看完整时间轴 →</button></div><div v-if="!allTimeline.length" class="empty-state compact-detail">还没有历史事件，补录过去发生的事，让档案真正连续。</div><div v-for="event in allTimeline.slice(0, 5)" :key="event.id" class="recent-event"><span class="event-icon"><AppIcon :name="eventIcon(event.type)" :size="16" /></span><div><small>{{ dateTime(event.occurredAt || event.createdAt) }} · {{ eventType(event.type) }}</small><b>{{ event.title }}</b><p>{{ event.summary || event.result || '已记录' }}</p></div><span v-if="event.result" class="badge" :class="tone(event.result)">{{ event.result }}</span></div></section>
      </div>
      <aside class="detail-side-column">
        <section class="surface side-card"><h3>双方意愿</h3><div class="intent-row"><span>公司意愿</span><b>{{ person.companyIntent || latestApplication?.companyIntent || '未判断' }}</b></div><div class="intent-row"><span>人才意愿</span><b>{{ person.talentIntent || latestApplication?.talentIntent || '未知' }}</b></div><p v-if="person.reason" class="reason-box">当前结果原因：{{ person.reason }}</p></section>
        <section class="surface side-card"><h3>联系与身份</h3><div v-if="contactLine.length" class="contact-list"><span v-if="person.phone">电话 · {{ person.phone }}</span><span v-if="person.wechat">微信 · {{ person.wechat }}</span><span v-if="person.email">邮箱 · {{ person.email }}</span></div><p v-else class="muted">尚未补充联系方式。</p><span class="privacy-note">仅在当前工作空间内可见</span></section>
        <section class="surface side-card"><div class="section-heading"><h3>待跟进</h3><button class="text-link" @click="openRecord('tasks')">新增</button></div><div v-for="task in openTasks.slice(0, 4)" :key="task.id" class="side-task"><span :class="{ overdue: isOverdue(task) }"></span><div><b>{{ task.title }}</b><small>{{ dateTime(task.dueAt) }} · {{ task.owner }}</small></div></div><p v-if="!openTasks.length" class="muted">没有待完成任务。</p></section>
      </aside>
    </div>

    <section v-else-if="activeTab === 'timeline'" class="surface detail-section timeline-section"><div class="section-heading"><div><div class="eyebrow">UNIFIED TIMELINE</div><h2>完整生命周期</h2><p class="muted">发生时间与录入时间分开保存，可补录 2021 年或更早的历史。</p></div><button class="btn btn-primary" @click="openRecord('events')"><AppIcon name="plus" :size="15" />补录历史 / 新增动态</button></div><div class="timeline-filters"><button :class="{ active: timelineFilter === 'all' }" @click="timelineFilter = 'all'">全部</button><button v-for="type in timelineTypes" :key="type" :class="{ active: timelineFilter === type }" @click="timelineFilter = type">{{ eventType(type) }}</button></div><div v-if="!timeline.length" class="empty-state">没有匹配的历史记录。</div><div v-for="[year, events] in timelineYears" :key="year" class="year-group"><div class="year-label">{{ year }}</div><ol class="timeline-list"><li v-for="event in events" :key="event.id"><span class="timeline-dot"><AppIcon :name="eventIcon(event.type)" :size="15" /></span><div class="timeline-card"><div class="timeline-top"><div><time>{{ dateTime(event.occurredAt || event.createdAt) }}</time><span class="badge gray">{{ eventType(event.type) }}</span></div><span>{{ event.actor || '系统记录' }}</span></div><h3>{{ event.title }}</h3><p>{{ event.summary || '已记录该事件' }}</p><div v-if="event.result || event.reason" class="event-result"><span v-if="event.result"><small>结果</small>{{ event.result }}</span><span v-if="event.reason"><small>原因 / 依据</small>{{ event.reason }}</span></div><div v-if="event.nextStep" class="next-box">下一步：{{ event.nextStep }}<span v-if="event.nextContactAt"> · {{ dateTime(event.nextContactAt) }}</span></div></div></li></ol></div></section>

    <section v-else class="surface detail-section record-section"><div class="section-heading"><div><div class="eyebrow">{{ activeTab.toUpperCase() }}</div><h2>{{ tabs.find(tab => tab[0] === activeTab)?.[1] }}</h2></div><div class="heading-actions"><input v-if="activeTab === 'assets'" ref="fileInput" hidden type="file" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.webp,.ppt,.pptx,.xls,.xlsx,.csv,.txt,.zip,.rtf,.odt" @change="pickFile"><button v-if="activeTab === 'assets'" class="btn btn-primary" :disabled="uploading" @click="fileInput?.click()">{{ uploading ? '上传中…' : '上传附件' }}</button><button v-else class="btn btn-primary" @click="openRecord(activeTab)"><AppIcon name="plus" :size="15" />{{ recordLabels[activeTab] }}</button></div></div>
      <div v-if="!person[activeTab]?.length" class="empty-state"><AppIcon :name="activeTab === 'tasks' ? 'clock' : activeTab === 'interviews' ? 'calendar' : 'briefcase'" :size="36" /><h3>还没有{{ tabs.find(tab => tab[0] === activeTab)?.[1] }}记录</h3><p>新增的内容会自动汇入统一时间轴。</p></div>
      <div v-else class="records-list"><article v-for="record in person[activeTab]" :key="record.id" class="record-line"><div class="record-date"><b>{{ dateOnly(record.occurredAt || record.scheduledAt || record.startedAt || record.startDate || record.dueAt || record.createdAt) }}</b><small>{{ record.version ? 'V' + record.version : record.type || '' }}</small></div><div class="record-body"><div class="record-title"><h3>{{ record.title || record.jobName || record.projectName || record.organization || record.name || '人才记录' }}</h3><span v-if="record.status || record.result" class="badge" :class="tone(record.status || record.result)">{{ record.status || record.result }}</span></div>
        <template v-if="activeTab === 'applications'"><p>{{ record.jobName }} · 岗位版本 V{{ record.jobVersion || 1 }}</p><p>公司意愿 {{ record.companyIntent || '未判断' }} · 人才意愿 {{ record.talentIntent || '未知' }} · {{ record.owner || '负责人待分配' }}</p></template>
        <template v-else-if="activeTab === 'interviews'"><p>{{ record.round }} · {{ record.method }} · 面试官 {{ record.interviewer || '待安排' }}</p><p>{{ record.feedback || '评价待补充' }}<span v-if="record.score != null"> · {{ record.score }} 分</span></p></template>
        <template v-else-if="activeTab === 'offers'"><p>{{ salary(record) }} · 计划入职 {{ dateOnly(record.expectedStartDate) }} · 有效期 {{ dateOnly(record.expiresAt) }}</p><p>{{ record.remark || record.reason || '录用条件待补充' }}</p></template>
        <template v-else-if="activeTab === 'assets'"><p>{{ record.type || '附件' }} · {{ record.size ? Math.ceil(record.size / 1024) + ' KB' : '外部链接' }} · {{ dateTime(record.createdAt) }}</p><button class="text-link" @click="download(record)"><AppIcon name="download" :size="14" /> 下载 / 打开</button></template>
        <template v-else-if="activeTab === 'experiences'"><p>{{ record.title }} · {{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }}</p><p>{{ record.description }}</p></template>
        <template v-else-if="activeTab === 'employments'"><p>{{ record.jobName || '关联岗位' }} · {{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }}</p><p>{{ record.reason || record.remark || '任职持续记录中' }}</p></template>
        <template v-else-if="activeTab === 'collaborations'"><p>{{ record.role }} · {{ dateOnly(record.startDate) }} — {{ record.endDate ? dateOnly(record.endDate) : '至今' }} · ¥{{ money(record.amount) }}</p><p>{{ record.feedback || '合作评价待补充' }}</p></template>
        <template v-else-if="activeTab === 'tasks'"><p>负责人 {{ record.owner }} · 截止 {{ dateTime(record.dueAt) }}</p><p>{{ record.result || (isOverdue(record) ? '该任务已逾期，请尽快跟进。' : '等待完成') }}</p></template>
        <p v-if="record.reason && !['offers','employments'].includes(activeTab)" class="reason-inline">原因：{{ record.reason }}</p></div><button v-if="activeTab !== 'assets'" class="btn btn-small btn-plain" @click="openRecord(activeTab, record)">更新</button></article></div>
      <section v-if="activeTab === 'offers'" class="compensation-block"><div class="section-heading"><div><h2>薪资轨迹</h2><p class="muted">期望、报价、还价与入职薪资分别留存。</p></div><button class="btn" @click="openRecord('compensations')">记录薪资事实</button></div><div v-if="!person.compensations?.length" class="empty-state compact-detail">暂无独立薪资事实。</div><div v-for="item in person.compensations || []" :key="item.id" class="compensation-line"><time>{{ dateOnly(item.occurredAt) }}</time><b>{{ item.type }}</b><span>{{ salary(item) }}</span><small>{{ item.source || '来源待补充' }}</small><button class="text-link" @click="openRecord('compensations', item)">更新</button></div></section>
    </section>

    <CandidateFormDialog v-model="editPerson" :person="person" @saved="refreshed" />
    <PersonRecordDialog v-model="recordOpen" :record-type="recordType" :record="editingRecord" :person="person" :positions="positions" :initial="initialRecord" @saved="refreshed" />
  </template>
</template>

<style scoped>
.detail-back{display:flex;justify-content:space-between;color:#9aa5af;font-size:11px;margin-bottom:16px}.detail-back a:hover{color:#60778a}.person-hero{padding:28px;display:grid;grid-template-columns:auto 1fr auto;gap:20px;align-items:center}.hero-avatar{width:72px;height:72px;border-radius:21px;display:grid;place-items:center;background:linear-gradient(145deg,#dbe4eb,#b9c9d5);color:#587084;font-size:23px}.hero-title{display:flex;align-items:center;gap:12px}.hero-title h1{font-size:27px;margin:0}.hero-main>p{font-size:13px;color:#84909c;margin:8px 0}.hero-main>p span{padding:0 8px}.hero-tags{display:flex;gap:6px;flex-wrap:wrap}.hero-tags span{font-size:10px;padding:4px 8px;border-radius:6px;background:#f0f3f6;color:#82909c}.hero-actions{display:flex;gap:8px}.hero-facts{grid-column:1/-1;display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#edf0f3;border-radius:12px;overflow:hidden;margin-top:5px}.hero-facts>div{background:#fafbfc;padding:15px 19px}.hero-facts small,.profile-grid small,.profile-note small{display:block;color:#9da7b1;font-size:10px;margin-bottom:7px}.hero-facts b{font-size:12px;font-weight:550;color:#607080}.detail-tabs{display:flex;gap:3px;overflow:auto;margin:20px 0 17px;padding:4px;background:#edf0f3aa;border-radius:12px}.detail-tabs button{border:0;background:transparent;color:#8b96a1;padding:9px 13px;border-radius:9px;font-size:11px;white-space:nowrap}.detail-tabs button.active{background:#fff;color:#546d82;box-shadow:0 1px 5px #40506310}.detail-tabs span{margin-left:6px;font-size:9px;color:#adb5be}.detail-grid{display:grid;grid-template-columns:minmax(0,1.8fr) minmax(260px,.8fr);gap:18px}.detail-main-column,.detail-side-column{display:flex;flex-direction:column;gap:18px}.detail-section{padding:25px}.profile-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:21px;padding-bottom:23px;border-bottom:1px solid #edf0f2}.profile-grid b{font-size:12px;font-weight:500;color:#586978}.profile-note{padding-top:20px}.profile-note p{font-size:12px;color:#74828e;margin:0;white-space:pre-wrap}.side-card{padding:21px}.side-card h3{font-size:13px;margin-bottom:18px}.intent-row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #eef0f2;font-size:11px;color:#97a1ab}.intent-row b{color:#5e7385}.reason-box{font-size:11px;background:#f7f2eb;color:#917e64;padding:11px;border-radius:8px;margin:14px 0 0}.contact-list{display:flex;flex-direction:column;gap:10px;font-size:11px;color:#718291}.privacy-note{display:block;font-size:9px;color:#abb3bc;margin-top:15px}.side-task{display:flex;gap:10px;padding:10px 0;border-bottom:1px solid #eef0f2}.side-task>span{width:7px;height:7px;background:#9caf9d;border-radius:50%;margin-top:4px}.side-task>span.overdue{background:#bc8f89}.side-task b{display:block;font-size:11px;font-weight:500}.side-task small{display:block;font-size:9px;color:#a1abb4;margin-top:5px}.recent-event{display:grid;grid-template-columns:auto 1fr auto;gap:12px;padding:15px 0;border-bottom:1px solid #eef0f2}.recent-event:last-child{border-bottom:0}.event-icon{width:31px;height:31px;border-radius:9px;display:grid;place-items:center;background:#eef3f6;color:#8196a8}.recent-event small{display:block;font-size:9px;color:#a4adb7}.recent-event b{display:block;font-size:12px;margin-top:5px}.recent-event p{font-size:11px;color:#89949e;margin:5px 0 0}.compact-detail{padding:28px 10px}.timeline-section{max-width:1050px;margin:0 auto}.timeline-filters{display:flex;gap:6px;overflow:auto;border-top:1px solid #eef0f2;padding-top:17px;margin-bottom:20px}.timeline-filters button{border:1px solid #e6eaee;background:#fafbfc;border-radius:7px;color:#929ca7;font-size:10px;padding:7px 10px;white-space:nowrap}.timeline-filters button.active{background:#e8eef3;color:#5c7387}.year-group{display:grid;grid-template-columns:72px 1fr;gap:18px}.year-label{font-size:18px;color:#72889b;font-weight:550;padding-top:8px}.timeline-list{list-style:none;margin:0;padding:0}.timeline-list li{position:relative;padding:0 0 22px 35px;border-left:1px solid #dce3e9}.timeline-list li:last-child{border-left-color:transparent}.timeline-dot{position:absolute;left:-14px;top:0;width:28px;height:28px;border-radius:9px;background:#e8eef3;color:#6d879b;display:grid;place-items:center;border:3px solid #fff}.timeline-card{border:1px solid #ebedf0;border-radius:13px;padding:18px;background:#fff}.timeline-top{display:flex;justify-content:space-between;gap:12px;font-size:9px;color:#a4adb6}.timeline-top>div{display:flex;align-items:center;gap:8px}.timeline-card h3{font-size:13px;margin:12px 0 7px}.timeline-card>p{font-size:12px;color:#7f8d99;margin-bottom:0;white-space:pre-wrap}.event-result{display:grid;grid-template-columns:1fr 2fr;gap:10px;background:#f7f8fa;border-radius:9px;padding:11px 13px;margin-top:13px;font-size:11px;color:#667684}.event-result small{display:block;color:#a1aab4;font-size:9px;margin-bottom:5px}.next-box{font-size:10px;color:#658070;background:#eef3ef;border-radius:8px;padding:9px 12px;margin-top:10px}.record-section{min-height:420px}.records-list{display:flex;flex-direction:column}.record-line{display:grid;grid-template-columns:105px 1fr auto;gap:20px;padding:21px 0;border-top:1px solid #edf0f2}.record-date b{display:block;font-size:11px;color:#677b8d}.record-date small{font-size:9px;color:#a6afb8}.record-title{display:flex;align-items:center;gap:10px}.record-title h3{font-size:13px;margin:0}.record-body p{font-size:11px;color:#87939f;margin:8px 0 0;white-space:pre-wrap}.reason-inline{background:#faf5ef;padding:8px;border-radius:7px;color:#967d64!important}.record-body .text-link{display:inline-flex;align-items:center;gap:5px;margin-top:8px}.compensation-block{border-top:1px solid #e7eaed;margin-top:25px;padding-top:25px}.compensation-line{display:grid;grid-template-columns:90px 1fr 1fr 1fr auto;gap:12px;align-items:center;padding:12px 0;border-top:1px solid #eef0f2;font-size:11px}.compensation-line time,.compensation-line small{color:#98a2ac}.compensation-line b{font-weight:500}
@media(max-width:950px){.detail-grid{grid-template-columns:1fr}.detail-side-column{display:grid;grid-template-columns:repeat(3,1fr)}.profile-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){.person-hero{grid-template-columns:auto 1fr;padding:20px}.hero-actions{grid-column:1/-1}.hero-actions .btn{flex:1}.hero-facts{grid-template-columns:repeat(2,1fr)}.detail-side-column{display:flex}.profile-grid{grid-template-columns:1fr 1fr}.year-group{grid-template-columns:1fr}.year-label{margin-top:15px}.record-line{grid-template-columns:1fr}.record-line>.btn{justify-self:start}.compensation-line{grid-template-columns:1fr 1fr}.person-hero .hero-avatar{width:57px;height:57px;border-radius:17px}.timeline-section .section-heading{align-items:flex-start;flex-wrap:wrap}}@media(max-width:450px){.profile-grid,.hero-facts{grid-template-columns:1fr}.hero-title{align-items:flex-start;flex-direction:column;gap:6px}.detail-section{padding:18px}.event-result{grid-template-columns:1fr}}
</style>
