<template>
  <div class="positions-page">
    <header class="positions-heading">
      <div><p class="eyebrow">OPPORTUNITIES, WITH INTENTION</p><h1>岗位管理<span class="heading-dot">.</span></h1><p class="muted page-description">让每一个岗位清晰，也让每一次选择有迹可循。</p></div>
      <button class="btn btn-primary" type="button" @click="openEditor()"><span aria-hidden="true">＋</span> 新建岗位</button>
    </header>

    <div class="position-stats">
      <article class="surface position-stat"><span class="stat-label">全部岗位</span><div><strong>{{ positions.length }}</strong><span class="stat-note">个长期岗位定义</span></div><span class="stat-mark" aria-hidden="true">▦</span></article>
      <article class="surface position-stat"><span class="stat-label">正在招聘</span><div><strong>{{ activeCount }}</strong><span class="stat-note">个机会正在开放</span></div><span class="stat-mark green" aria-hidden="true">↗</span></article>
      <article class="surface position-stat"><span class="stat-label">暂停 / 关闭</span><div><strong>{{ positions.length - activeCount }}</strong><span class="stat-note">历史与人才关联保留</span></div><span class="stat-mark sand" aria-hidden="true">◷</span></article>
    </div>

    <section class="surface positions-panel" aria-label="岗位列表">
      <div class="positions-toolbar">
        <div class="status-tabs" role="group" aria-label="按招聘状态筛选"><button v-for="item in statusFilters" :key="item.value" type="button" :class="{ active: statusFilter === item.value }" :aria-pressed="statusFilter === item.value" @click="statusFilter = item.value">{{ item.label }}</button></div>
        <label class="position-search"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" /></svg><input v-model="search" type="search" aria-label="搜索岗位、部门、城市或 HR" placeholder="搜索岗位、部门、城市…" /></label>
      </div>
      <div v-if="loading" class="empty-state loading-state" role="status">正在整理岗位信息…</div>
      <div v-else-if="loadError" class="empty-state" role="alert"><p>{{ loadError }}</p><button class="btn btn-secondary" type="button" @click="loadPositions">重新加载</button></div>
      <div v-else-if="!filteredPositions.length" class="empty-state position-empty"><span aria-hidden="true">⌁</span><h3>{{ positions.length ? '没有符合条件的岗位' : '为下一位伙伴，留一个位置' }}</h3><p>{{ positions.length ? '试试其他关键词，或清除当前筛选。' : '创建岗位后，人才档案和每一次应聘都能与它关联。' }}</p><button class="btn btn-secondary" type="button" @click="positions.length ? clearFilters() : openEditor()">{{ positions.length ? '清除筛选' : '创建第一个岗位' }}</button></div>
      <div v-else class="positions-table-wrap">
        <table class="positions-table"><thead><tr><th scope="col">公司 / 岗位 / 编号</th><th scope="col">岗位性质</th><th scope="col">月薪预算</th><th scope="col">Base 地</th><th scope="col">HR</th><th scope="col">招聘状态</th><th scope="col">版本</th><th scope="col">操作</th></tr></thead><tbody>
          <tr v-for="job in paginatedPositions" :key="job.id" class="position-row" tabindex="0" role="button" :aria-label="`查看${job.name}岗位详情`" @click="showVersions(job)" @keydown.enter="showVersions(job)"><td><span class="job-title"><span class="job-icon" aria-hidden="true">{{ job.name?.slice(0, 1) || '岗' }}</span><span><strong>{{ positionLabel(job) }}</strong><small>{{ job.department || '未设置部门' }}</small></span></span></td><td><span class="badge gray">{{ job.employmentType || '全职' }}</span></td><td><span class="budget-text">{{ salaryText(job) }}</span><small v-if="job.minSalary != null && job.maxSalary != null" class="cell-caption">K / 月</small></td><td>{{ job.baseLocation || job.location || '待补充' }}</td><td>{{ job.owner || '待分配' }}</td><td><span class="badge position-status" :class="statusClass(job.status)"><i aria-hidden="true"></i>{{ job.status || '招聘中' }}</span></td><td><button type="button" class="version-link" @click.stop="showVersions(job)">V{{ job.version || 1 }} <span aria-hidden="true">↗</span></button></td><td><div class="row-actions"><button type="button" class="row-action" @click.stop="openEditor(job)">编辑</button><button type="button" class="row-action secondary-action" :disabled="changingStatus === job.id" @click.stop="prepareStatusChange(job)">{{ changingStatus === job.id ? '处理中…' : (isActive(job) ? '停招' : '重启') }}</button></div></td></tr>
        </tbody></table>
      </div>
      <footer v-if="!loading && !loadError && filteredPositions.length" class="position-pagination"><span class="muted">共 {{ filteredPositions.length }} 个岗位 · 每页最多 10 条</span><div><button type="button" class="page-button" aria-label="上一页" :disabled="page === 1" @click="page--">‹</button><span>{{ page }} <span class="muted">/ {{ totalPages }}</span></span><button type="button" class="page-button" aria-label="下一页" :disabled="page >= totalPages" @click="page++">›</button></div></footer>
    </section>

    <div class="position-footnote"><span aria-hidden="true">◈</span><p>岗位是一条长期的线索。薪资和要求的每次变化都会形成版本，历史应聘始终保留当时的岗位快照。</p></div>

    <el-dialog v-model="editorOpen" :title="editingJob ? '编辑岗位 · 发布新版本' : '创建一个新的机会'" width="min(700px, 94vw)" top="6vh" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
      <p class="dialog-intro muted">{{ editingJob ? '更新岗位的当前要求，并留下变化的原因。' : '定义岗位职责和预算，让人才与岗位准确对应。' }}</p>
      <form id="position-form" class="position-form" novalidate @submit.prevent="savePosition">
        <div class="form-grid">
          <div class="field" :class="{ missing: attempted && !form.companyId }"><span>所属公司 <span class="required">必填</span></span><div class="company-selector" role="listbox" aria-label="选择所属公司"><button v-for="company in companies" :key="company.id" type="button" :class="{ selected: String(form.companyId) === String(company.id) }" :aria-selected="String(form.companyId) === String(company.id)" @click="selectCompany(company)"><span class="company-mark">{{ company.name.slice(0, 1) }}</span><span><b>{{ company.name }}</b><small>{{ company.locations?.length || 0 }} 个办公地点</small></span><i aria-hidden="true">✓</i></button></div></div>
          <label class="field" :class="{ missing: attempted && !form.name?.trim() }">岗位名称 <span class="required">必填</span><input v-model.trim="form.name" name="positionName" maxlength="200" placeholder="例如：品牌设计师" /></label>
          <label class="field">所属部门<input v-model.trim="form.department" name="department" maxlength="100" placeholder="例如：品牌与创意" /></label>
          <label class="field" :class="{ missing: attempted && !form.baseLocation?.trim() }">Base 城市 <span class="required">必填</span><select v-model="form.baseLocation" name="baseLocation" :disabled="!selectedCompany" @change="selectBase(form.baseLocation)"><option value="">{{ selectedCompany ? '请选择 Base 城市' : '请先选择公司' }}</option><option v-for="city in baseOptions" :key="city" :value="city">{{ city }}</option></select></label>
          <label v-if="form.baseLocation" class="field" :class="{ missing: attempted && !form.location?.trim() }">办公地点 <span class="required">必填</span><span class="field-hint">区 / 街道 / 详细地址</span><select v-model="form.location" name="location"><option value="">请选择街道办公地点</option><option v-for="office in officeOptions" :key="office.id" :value="office.value">{{ office.district }} · {{ office.street }} · {{ office.address }}</option></select></label>
          <label class="field">HR<HrPicker v-model="form.owner" :employees="hrs" placeholder="输入姓名模糊搜索 HR" /></label>
          <fieldset class="choice-field full-width"><legend>岗位性质 <span class="muted">选填，默认全职</span></legend><div class="status-options"><label v-for="type in ['全职','兼职','外包']" :key="type" :class="{ selected: form.employmentType === type }"><input v-model="form.employmentType" type="radio" name="employmentType" :value="type" /><span>{{ type }}</span></label></div></fieldset>
          <div class="field full-width" :class="{ missing: attempted && (form.minSalary === '' || form.maxSalary === '') }"><span>岗位期望预算 <span class="required">必填</span> <span class="muted">K / 月</span></span><div class="salary-inputs"><input v-model="form.minSalary" name="minSalary" type="number" min="0" max="100000" step="0.1" inputmode="decimal" aria-label="岗位最低月薪预算，单位 K" placeholder="最低预算" /><span aria-hidden="true">—</span><input v-model="form.maxSalary" name="maxSalary" type="number" min="0" max="100000" step="0.1" inputmode="decimal" aria-label="岗位最高月薪预算，单位 K" placeholder="最高预算" /></div></div>
          <fieldset class="choice-field full-width"><legend>招聘状态</legend><div class="status-options"><label v-for="status in statuses" :key="status" :class="{ selected: form.status === status }"><input v-model="form.status" type="radio" name="jobStatus" :value="status" /><span>{{ status }}</span></label></div></fieldset>
          <label class="field full-width">岗位说明<textarea v-model.trim="form.description" name="description" rows="6" maxlength="10000" placeholder="主要工作内容、能力要求、合作方式，以及希望共同完成的事情…" /></label>
          <label class="field full-width">{{ editingJob ? '本次变更原因' : '版本说明' }} <span v-if="editingJob" class="required">*</span><textarea v-model.trim="form.versionNote" name="versionNote" rows="2" maxlength="2000" :required="!!editingJob" :placeholder="editingJob ? '例如：增加项目管理职责，预算由 15–20K 调整为 18–25K' : '例如：品牌团队新增设计岗位'" /></label>
        </div>
        <p v-if="saveError" class="inline-error" role="alert">{{ saveError }}</p>
      </form>
      <template #footer><div class="dialog-actions"><button type="button" class="btn btn-secondary" :disabled="saving" @click="editorOpen = false">取消</button><button type="submit" form="position-form" class="btn btn-primary" :disabled="saving">{{ saving ? '保存中…' : (editingJob ? '保存新版本' : '创建岗位') }}</button></div></template>
    </el-dialog>

    <el-dialog v-model="statusDialogOpen" :title="statusTarget && isActive(statusTarget) ? '暂停这个岗位的招聘' : '重新开放这个岗位'" width="min(500px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!changingStatus" :show-close="!changingStatus" destroy-on-close>
      <form id="position-status-form" class="position-form" @submit.prevent="changeStatus"><p class="dialog-intro muted">{{ statusTarget?.name }} · {{ statusTarget && isActive(statusTarget) ? '确认后，该岗位仍在应聘中的人才会统一记为“公司淘汰”；已经入职的人不会受影响。' : '岗位重新开放后，可以继续建立新的应聘流程。' }}</p><label class="field">原因 <span class="required">*</span><textarea v-model.trim="statusReason" rows="3" required maxlength="2000" placeholder="记录这次暂停或重新开放的原因" /></label><p v-if="statusError" class="inline-error" role="alert">{{ statusError }}</p></form>
      <template #footer><div class="dialog-actions"><button type="button" class="btn btn-secondary" :disabled="!!changingStatus" @click="statusDialogOpen = false">取消</button><button type="submit" form="position-status-form" class="btn btn-primary" :disabled="!!changingStatus">{{ changingStatus ? '保存中…' : '确认并记录' }}</button></div></template>
    </el-dialog>

    <el-dialog v-model="versionsOpen" :title="`${versionJob?.name || '岗位'} · 版本记录`" width="min(760px, 94vw)" top="5vh" destroy-on-close>
      <p class="dialog-intro muted">每次要求与预算的变化，都有一个可以回看的时点。</p>
      <div v-if="versionsLoading" class="empty-state" role="status">正在加载岗位版本…</div>
      <div v-else-if="versionsError" class="empty-state" role="alert"><p>{{ versionsError }}</p><button type="button" class="btn btn-secondary" @click="showVersions(versionJob)">重试</button></div>
      <div v-else-if="!versions.length" class="empty-state">暂无历史版本。下次调整岗位时，会记录新的版本。</div>
      <ol v-else class="version-timeline"><li v-for="(version, index) in versions" :key="version.id || version.version || index" class="version-entry"><span class="version-point" :class="{ current: index === 0 }" aria-hidden="true"></span><div class="version-top"><div><strong>V{{ version.version || versions.length - index }}</strong><span v-if="index === 0" class="badge current-version">当前版本</span></div><time :datetime="version.createdAt || version.updatedAt">{{ formatTime(version.createdAt || version.updatedAt) }}</time></div><h3>{{ version.name || versionJob?.name }}</h3><div class="version-facts"><span><small>月薪预算</small><b>{{ salaryText(version) }}{{ version.minSalary != null && version.maxSalary != null ? ' K / 月' : '' }}</b></span><span><small>部门 / 城市</small><b>{{ [version.department, version.location].filter(Boolean).join(' · ') || '未设置' }}</b></span><span><small>HR / 状态</small><b>{{ [version.owner, version.status].filter(Boolean).join(' · ') || '未设置' }}</b></span></div><p class="version-note">{{ version.versionNote || version.changeReason || '创建岗位初始版本' }}</p><details v-if="version.description" :open="index === 0" class="version-description"><summary>岗位说明</summary><p>{{ version.description }}</p></details></li></ol>
      <template #footer><div class="dialog-actions"><button type="button" class="btn btn-secondary" @click="versionsOpen = false">关闭</button><button type="button" class="btn btn-primary" @click="versionsOpen = false; openEditor(versionJob)">修改岗位</button></div></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPositionList, createPosition, updatePosition, updatePositionStatus, getPositionVersions } from '@/api/position'
import { getHrList } from '@/api/hr'
import { getCompanyList } from '@/api/company'
import HrPicker from '@/components/HrPicker.vue'

const positions = ref([])
const hrs = ref([])
const companies = ref([])
const loading = ref(true)
const loadError = ref('')
const search = ref('')
const statusFilter = ref('all')
const page = ref(1)
const pageSize = 10
const statusFilters = [{ value: 'all', label: '全部岗位' }, { value: 'active', label: '招聘中' }, { value: 'paused', label: '暂停招聘' }, { value: 'closed', label: '已关闭' }]
const statuses = ['招聘中', '暂停招聘', '已关闭']
const editorOpen = ref(false)
const editingJob = ref(null)
const form = reactive({})
const saving = ref(false)
const attempted = ref(false)
const saveError = ref('')
const statusDialogOpen = ref(false)
const statusTarget = ref(null)
const statusReason = ref('')
const statusError = ref('')
const changingStatus = ref(null)
const versionsOpen = ref(false)
const versionsLoading = ref(false)
const versionsError = ref('')
const versions = ref([])
const versionJob = ref(null)
let versionsRequest = 0

function unwrap(response) { let value = response; if (value?.data !== undefined) value = value.data; if (value?.data !== undefined) value = value.data; return value }
function isActive(job) { return !job.status || ['招聘中', '开放', '进行中'].includes(job.status) }
function statusClass(status) { return !status || ['招聘中', '开放', '进行中'].includes(status) ? 'active-status' : ['已关闭', '关闭'].includes(status) ? 'closed-status' : 'paused-status' }
const activeCount = computed(() => positions.value.filter(isActive).length)
const selectedCompany = computed(() => companies.value.find(company => String(company.id) === String(form.companyId)))
const baseOptions = computed(() => [...new Set((selectedCompany.value?.locations || []).map(location => location.baseCity).filter(Boolean))])
const officeOptions = computed(() => (selectedCompany.value?.locations || []).filter(location => location.baseCity === form.baseLocation))
const filteredPositions = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return positions.value.filter(job => {
    const matchesText = !query || [job.company, job.name, job.recruitmentCode, job.department, job.location, job.baseLocation, job.owner, job.description].some(value => String(value || '').toLocaleLowerCase().includes(query))
    const state = statusClass(job.status).replace('-status', '')
    return matchesText && (statusFilter.value === 'all' || state === statusFilter.value)
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredPositions.value.length / pageSize)))
const paginatedPositions = computed(() => filteredPositions.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([search, statusFilter], () => { page.value = 1 })
watch(totalPages, count => { if (page.value > count) page.value = count })
function clearFilters() { search.value = ''; statusFilter.value = 'all'; page.value = 1 }
function salaryText(job) { return job.minSalary != null && job.maxSalary != null ? `${job.minSalary} — ${job.maxSalary}` : '面议 / 待定' }
function positionLabel(job) { return `${job.company || '未设置公司'}：${job.name || '未命名岗位'}：${job.recruitmentCode || String(job.id || '').padStart(3, '0')}` }
function formatTime(value) { if (!value) return '时间待补充'; const date = new Date(String(value).replace(' ', 'T')); return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) }
function selectBase(city) { form.baseLocation = city || ''; form.location = officeOptions.value[0]?.value || '' }
function selectCompany(company) { form.companyId = company?.id || ''; form.baseLocation = ''; form.location = ''; if ((company?.locations || []).length) selectBase(company.locations[0].baseCity) }

async function loadPositions() {
  loading.value = true; loadError.value = ''
  try { const data = unwrap(await getPositionList()); positions.value = Array.isArray(data) ? data : data?.records || data?.items || [] }
  catch (err) { loadError.value = err.response?.data?.message || err.message || '岗位加载失败，请稍后重试。' }
  finally { loading.value = false }
}

function openEditor(job = null) {
  editingJob.value = job ? { ...job } : null
  const defaultCompany = companies.value.find(company => company.name === job?.company)?.id || companies.value[0]?.id || ''
  const defaultHr = hrs.value.find(hr => hr.current) || hrs.value[0]
  Object.assign(form, { companyId: job?.companyId || defaultCompany, name: job?.name || '', department: job?.department || '', baseLocation: job?.baseLocation || job?.location || '', location: job?.location || '', owner: job?.owner || defaultHr?.name || '', employmentType: job?.employmentType || '全职', minSalary: job?.minSalary ?? '', maxSalary: job?.maxSalary ?? '', status: job?.status || '招聘中', description: job?.description || '', versionNote: '' })
  const company = companies.value.find(item => String(item.id) === String(form.companyId))
  const bases = [...new Set((company?.locations || []).map(location => location.baseCity))]
  if (!bases.includes(form.baseLocation)) form.baseLocation = bases[0] || ''
  const offices = (company?.locations || []).filter(location => location.baseCity === form.baseLocation)
  if (!offices.some(office => office.value === form.location)) form.location = offices[0]?.value || ''
  attempted.value = false; saveError.value = ''; editorOpen.value = true
}

async function savePosition() {
  if (saving.value) return
  attempted.value = true
  saveError.value = ''
  if (!form.companyId || !form.name.trim() || !form.baseLocation.trim() || !form.location.trim()) { saveError.value = '请选择公司、Base 城市和细分到街道的办公地点，并填写岗位名称。'; return }
  const minSalary = form.minSalary === '' || form.minSalary == null ? null : Number(form.minSalary)
  const maxSalary = form.maxSalary === '' || form.maxSalary == null ? null : Number(form.maxSalary)
  if (minSalary == null || maxSalary == null) { saveError.value = '请填写岗位期望预算的最低和最高金额。'; return }
  if (!Number.isFinite(minSalary) || !Number.isFinite(maxSalary) || minSalary < 0 || maxSalary < 0 || minSalary > maxSalary) { saveError.value = '预算需为有效的非负数，最低预算不能高于最高预算。'; return }
  if (editingJob.value && !form.versionNote.trim()) { saveError.value = '请填写本次变更原因。'; return }
  saving.value = true
  try {
    const payload = { ...form, minSalary, maxSalary, versionNote: form.versionNote || '创建岗位初始版本' }
    if (editingJob.value) payload.version = editingJob.value.version
    await (editingJob.value ? updatePosition(editingJob.value.id, payload) : createPosition(payload))
    ElMessage.success(editingJob.value ? '岗位新版本已保存，历史应聘保持原快照' : '新岗位已创建')
    editorOpen.value = false
    await loadPositions()
  } catch (err) { saveError.value = err.response?.data?.message || err.message || '保存失败，请稍后重试。' }
  finally { saving.value = false }
}

function prepareStatusChange(job) { statusTarget.value = { ...job }; statusReason.value = ''; statusError.value = ''; statusDialogOpen.value = true }
async function changeStatus() {
  if (changingStatus.value || !statusTarget.value) return
  if (!statusReason.value.trim()) { statusError.value = '请填写本次状态变化的原因。'; return }
  const job = statusTarget.value
  changingStatus.value = job.id; statusError.value = ''
  try {
    await updatePositionStatus(job.id, { version: job.version, status: isActive(job) ? '暂停招聘' : '招聘中', reason: statusReason.value })
    ElMessage.success(isActive(job) ? '已暂停招聘；在途应聘已统一记录为公司淘汰，入职员工未受影响' : '岗位已重新开放')
    statusDialogOpen.value = false
    await loadPositions()
  } catch (err) { statusError.value = err.response?.data?.message || err.message || '更新失败，请稍后重试。' }
  finally { changingStatus.value = null }
}

async function showVersions(job) {
  if (!job) return
  versionJob.value = job; versionsOpen.value = true; versionsLoading.value = true; versionsError.value = ''; versions.value = []
  const requestId = ++versionsRequest
  try {
    const data = unwrap(await getPositionVersions(job.id))
    if (requestId !== versionsRequest) return
    const list = Array.isArray(data) ? data : data?.records || data?.items || []
    versions.value = list.map(item => {
      let snapshot = item.snapshot || item.snapshotJson || {}
      if (typeof snapshot === 'string') { try { snapshot = JSON.parse(snapshot) } catch { snapshot = {} } }
      return { ...snapshot, ...item }
    }).sort((a, b) => Number(b.version || 0) - Number(a.version || 0))
  } catch (err) { if (requestId === versionsRequest) versionsError.value = err.response?.data?.message || err.message || '版本记录加载失败。' }
  finally { if (requestId === versionsRequest) versionsLoading.value = false }
}
onMounted(async () => {
  await Promise.all([
    getHrList().then(value => { hrs.value = value }).catch(() => { hrs.value = [] }),
    getCompanyList().then(value => { companies.value = value }).catch(() => { companies.value = [] })
  ])
  await loadPositions()
})
</script>

<style scoped>
.positions-page{max-width:1500px;margin:0 auto;padding:4px 0 24px;animation:page-arrive .5s ease both}.positions-heading{display:flex;align-items:center;justify-content:space-between;gap:22px;margin-bottom:30px}.eyebrow{color:#8b99ab;font-size:10px;font-weight:600;letter-spacing:2.2px;margin:0 0 11px}.positions-heading h1{font-size:31px;line-height:1.3;letter-spacing:-1px;margin:0;color:#293a4d;font-weight:650}.heading-dot{color:#93a8c0;margin-left:4px}.page-description{font-size:13px;margin:11px 0 0;line-height:1.7}.positions-heading>.btn{font-size:13px;padding:12px 20px;display:flex;align-items:center;gap:8px;white-space:nowrap}.positions-heading>.btn span{font-size:18px;font-weight:300}.position-stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:17px;margin-bottom:24px}.position-stat{position:relative;padding:24px 26px;overflow:hidden}.position-stat:after{content:'';position:absolute;width:100px;height:100px;right:-35px;bottom:-60px;border-radius:50%;background:rgba(174,193,212,.1);filter:blur(14px)}.stat-label{font-size:12px;color:#7b899b}.position-stat>div{display:flex;align-items:baseline;gap:15px;margin-top:16px}.position-stat strong{font-size:35px;line-height:1;letter-spacing:-1.5px;font-weight:550;color:#34475d}.stat-note{font-size:11px;color:#94a0ae}.stat-mark{position:absolute;top:21px;right:24px;display:grid;place-items:center;width:32px;height:32px;border-radius:10px;font-size:18px;color:#8b9fb6;background:#edf2f8}.stat-mark.green{color:#8ca599;background:#edf3ef}.stat-mark.sand{color:#b0a386;background:#f4f1e9}.positions-panel{overflow:hidden}.positions-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:22px 25px;border-bottom:1px solid rgba(126,147,171,.1)}.status-tabs{display:flex;gap:3px;padding:4px;border:1px solid #e7ecf1;background:#f1f4f8;border-radius:12px}.status-tabs button{border:0;background:transparent;cursor:pointer;font:inherit;color:#8692a2;font-size:12px;padding:8px 13px;white-space:nowrap;border-radius:8px;transition:color .2s,background .2s,box-shadow .2s}.status-tabs button.active{background:rgba(255,255,255,.95);color:#49637f;box-shadow:0 2px 6px rgba(76,99,129,.08)}.position-search{display:flex;align-items:center;gap:9px;padding:10px 12px;min-width:230px;border-radius:11px;background:rgba(246,248,251,.8);border:1px solid #e4e9ef}.position-search svg{width:16px;height:16px;fill:none;stroke:#91a0b2;stroke-width:1.6}.position-search input{border:0;background:none;outline:0;min-width:0;width:100%;font:inherit;font-size:12px;color:#45566c}.position-search input::placeholder{color:#9da8b5}.position-search:focus-within{border-color:#a8bbd0;box-shadow:0 0 0 3px #e9eff6}.positions-table-wrap{overflow-x:auto}.positions-table{width:100%;border-collapse:collapse;white-space:nowrap;text-align:left}.positions-table th{padding:16px 20px;background:rgba(245,248,252,.7);font-size:11px;font-weight:500;color:#91a0b2}.positions-table td{padding:19px 20px;border-bottom:1px solid rgba(128,148,171,.1);color:#758397;font-size:12px;vertical-align:middle}.positions-table th:first-child,.positions-table td:first-child{padding-left:27px}.positions-table tbody tr{transition:background .2s}.positions-table tbody tr:hover{background:rgba(241,246,251,.56)}.positions-table tbody tr:last-child td{border-bottom:0}.job-title{border:0;background:transparent;display:flex;gap:12px;align-items:center;text-align:left;padding:0;cursor:pointer;color:#35475e;font:inherit}.job-icon{display:grid;place-items:center;flex:none;width:39px;height:39px;border-radius:12px;background:linear-gradient(135deg,#e9eff7,#f3f5f8);color:#8296b0;font-size:15px}.job-title strong{display:block;font-size:13px;font-weight:600;max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.job-title small{font-size:10px;color:#9aa7b7;display:block;margin-top:5px}.job-title:hover strong{color:#607e9d}.budget-text{font-size:13px;color:#5e7086;font-variant-numeric:tabular-nums}.cell-caption{display:block;font-size:9px;color:#a3afbc;margin-top:5px}.position-status{display:inline-flex;align-items:center;gap:6px;border-radius:7px;padding:6px 9px;font-size:10px;font-weight:400}.position-status i{width:4px;height:4px;border-radius:50%;background:currentColor}.active-status{background:#edf3ef;color:#789787}.paused-status{background:#f5f1e8;color:#aa9872}.closed-status{background:#f0f1f4;color:#919aaa}.version-link{border:0;background:transparent;padding:5px 0;color:#95a4b7;font-size:11px;cursor:pointer;font-variant-numeric:tabular-nums}.version-link:hover{color:#557696}.version-link span{font-size:10px;margin-left:3px}.row-actions{display:flex;align-items:center;gap:13px}.row-action{padding:4px 0;color:#637f9e;border:0;background:transparent;font-size:11px;cursor:pointer}.secondary-action{color:#a1aab6}.row-action:hover{color:#304e6c}.row-action:disabled{opacity:.5;cursor:wait}.position-pagination{display:flex;justify-content:space-between;align-items:center;gap:16px;border-top:1px solid #edf0f5;padding:17px 25px;font-size:11px}.position-pagination>div{display:flex;align-items:center;gap:17px;color:#6c8098}.page-button{display:grid;place-items:center;width:28px;height:28px;border:1px solid #e2e9f0;background:#f8fafc;border-radius:8px;color:#6e86a0;font-size:20px;cursor:pointer}.page-button:disabled{opacity:.3;cursor:default}.position-footnote{display:flex;align-items:flex-start;gap:9px;justify-content:center;margin:22px 0 0;color:#9ca9b8}.position-footnote>span{font-size:14px}.position-footnote p{margin:0;font-size:11px;line-height:1.8}.position-empty{padding:65px 20px;text-align:center}.position-empty>span{display:block;font-size:38px;color:#a0b2c7;margin-bottom:16px}.position-empty h3{color:#62768e;font-size:17px;font-weight:500;margin:0 0 10px}.position-empty p{font-size:12px;color:#99a6b6;margin-bottom:22px}.loading-state{padding:80px 20px}.dialog-intro{font-size:12px;line-height:1.8;margin:0 0 23px}.position-form{color:#53647b}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:19px}.full-width{grid-column:1/-1}.field{display:flex;flex-direction:column;gap:8px;font-size:12px;font-weight:500;color:#63738a}.required{display:inline-flex;margin-left:4px;padding:2px 6px;border-radius:999px;background:#fff0ed;color:#c75d4b;font-size:9px}.field input,.field select,.field textarea{width:100%;box-sizing:border-box;min-width:0;border:1px solid #dfe6ed;border-radius:12px;padding:11px 13px;background:#f7f9fb;font:inherit;font-size:13px;outline:0;color:#384c63;line-height:1.5;transition:border-color .2s,box-shadow .2s}.field input:focus,.field select:focus,.field textarea:focus{border-color:#9fb4cb;box-shadow:0 0 0 3px #ebf0f6}.field input::placeholder,.field textarea::placeholder{color:#a2adbb;font-weight:400}.field textarea{resize:vertical}.salary-inputs{display:flex;align-items:center;gap:13px}.salary-inputs>span{color:#a2afbe}.salary-inputs input{width:calc(50% - 17px)}.choice-field{padding:0;border:0;margin:0}.choice-field legend{margin-bottom:9px;color:#63738a;font-size:12px;padding:0}.status-options{display:flex;flex-wrap:wrap;gap:8px}.status-options label{position:relative;border:1px solid #e1e7ef;border-radius:10px;overflow:hidden;background:#f7f9fb;color:#8a98a9;cursor:pointer;transition:background .2s,color .2s,border-color .2s}.status-options label.selected{border-color:#b3c4d8;color:#5b7796;background:#eaf0f7}.status-options input{position:absolute;inset:0;opacity:0;margin:0;width:100%;height:100%;cursor:pointer}.status-options span{display:block;padding:9px 15px;font-size:12px}.status-options label:focus-within{outline:2px solid #8da6bf;outline-offset:2px}.inline-error{margin-top:18px;padding:12px 14px;background:#f7eeeb;color:#a2796c;border:1px solid #eadbd4;border-radius:12px;font-size:12px;line-height:1.7}.dialog-actions{display:flex;justify-content:flex-end;gap:10px;padding-top:15px;border-top:1px solid #edf0f4}.dialog-actions .btn{padding:11px 20px;font-size:12px;border-radius:12px}.version-timeline{list-style:none;margin:28px 0 0;padding:0 0 0 15px}.version-entry{position:relative;margin:0;padding:0 0 27px 27px;border-left:1px solid #d9e2ed}.version-entry:last-child{border-left-color:transparent;padding-bottom:8px}.version-point{position:absolute;left:-5px;top:5px;width:9px;height:9px;border-radius:50%;background:#d2dce8;border:2px solid white;box-shadow:0 0 0 1px #d2dce8}.version-point.current{background:#90aac4;box-shadow:0 0 0 4px #edf2f7}.version-top{display:flex;align-items:center;justify-content:space-between;gap:15px}.version-top>div{display:flex;gap:10px;align-items:center}.version-top strong{font-size:13px;color:#687f9b;font-weight:600}.version-top time{font-size:10px;color:#9aaabc}.current-version{font-size:9px;background:#eaf1f6;color:#7894ad;padding:4px 7px}.version-entry h3{font-size:16px;font-weight:550;color:#3c5069;margin:14px 0}.version-facts{display:grid;grid-template-columns:1fr 1fr 1fr;gap:14px;padding:15px 17px;border:1px solid #e8edf3;border-radius:13px;background:#f8fafc}.version-facts small{display:block;color:#a0adbc;font-size:10px;margin-bottom:7px}.version-facts b{font-size:11px;font-weight:500;color:#6c8098;line-height:1.8}.version-note{font-size:12px;color:#8c9bb0;margin:14px 0;line-height:1.9}.version-description{font-size:12px;color:#8c9eb2}.version-description summary{cursor:pointer;color:#6f87a2;font-size:11px}.version-description p{white-space:pre-wrap;line-height:1.9;color:#8292a7;margin:11px 0 0}.btn:disabled{opacity:.55;cursor:wait}button:focus-visible{outline:2px solid #87a1bd;outline-offset:3px}
.company-selector{display:grid;gap:7px}.company-selector>button{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:10px;width:100%;border:1px solid #dfe6ed;border-radius:12px;padding:9px 11px;background:#f7f9fb;color:#53647b;text-align:left;cursor:pointer}.company-selector>button:hover{border-color:#b9c9da;background:#f1f5fa}.company-selector>button.selected{border-color:#80a8ec;background:linear-gradient(135deg,#edf4ff,#f7faff);box-shadow:0 0 0 3px #edf3ff}.company-mark{display:grid;place-items:center;width:30px;height:30px;border-radius:9px;background:#e4edf9;color:#5177ad;font-weight:650}.company-selector b{display:block;font-size:12px}.company-selector small{display:block;margin-top:3px;color:#9aa7b7;font-size:9px;font-weight:400}.company-selector i{opacity:0;color:#3975e8;font-style:normal}.company-selector>button.selected i{opacity:1}.field-hint{font-size:9px;color:#9aa7b7;font-weight:400;margin-top:-5px}.field select:disabled{color:#a0a9b4;cursor:not-allowed}.field.missing input,.field.missing select,.field.missing .company-selector>button{background:#fff3f1!important;border-color:#df7667!important;animation:position-required-flash .42s ease 2}@keyframes position-required-flash{50%{box-shadow:0 0 0 4px rgba(213,89,70,.17)}}@keyframes page-arrive{from{opacity:0;transform:translateY(9px)}to{opacity:1;transform:translateY(0)}}
@media(max-width:1100px){.position-stat{padding:22px}.stat-note{font-size:10px}.stat-mark{right:18px}.positions-toolbar{flex-wrap:wrap}.position-search{min-width:180px;flex:1}.positions-table td,.positions-table th{padding-left:15px;padding-right:15px}}
@media(max-width:700px){.positions-heading{align-items:flex-start;margin-bottom:23px}.positions-heading h1{font-size:26px}.eyebrow{font-size:8px;letter-spacing:1.4px}.page-description{font-size:11px;max-width:230px}.positions-heading>.btn{padding:10px 13px;font-size:11px}.position-stats{gap:9px}.position-stat{padding:16px 13px}.position-stat>div{display:block;margin-top:13px}.position-stat strong{font-size:28px}.stat-label{font-size:10px}.stat-note{display:none}.stat-mark{display:none}.positions-toolbar{padding:15px;gap:13px}.status-tabs{width:100%;justify-content:space-between;box-sizing:border-box}.status-tabs button{font-size:10px;padding:8px 10px;flex:1}.position-search{width:100%;box-sizing:border-box}.position-pagination{padding:15px;font-size:10px}.position-pagination>div{gap:12px}.position-footnote{text-align:left;padding:0 5px}.position-footnote p{font-size:10px}.form-grid{grid-template-columns:1fr}.version-facts{grid-template-columns:1fr;gap:12px}.version-top{align-items:flex-start;flex-direction:column;gap:6px}.version-entry{padding-left:22px}.version-timeline{padding-left:5px}}
@media(prefers-reduced-motion:reduce){.positions-page{animation:none}*{transition:none!important}}
.position-row{cursor:pointer}.position-row:focus-visible{outline:2px solid #8fb0ff;outline-offset:-2px;background:#f1f6ff}.position-row:hover .job-title strong{color:#245bdb}
</style>
