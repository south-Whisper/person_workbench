<template>
  <el-dialog v-model="visible" :title="person?.id ? '编辑人才档案' : '认识一位新人才'" width="min(820px, 94vw)" top="5vh" class="talent-form-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="dialog-intro muted">先记下姓名或昵称、来源、HR 和一种联系方式，其余信息可以慢慢补全。</p>
    <form id="candidate-profile-form" class="profile-form" :class="{ 'show-required-errors': attempted }" novalidate @submit.prevent="submit">
      <section class="form-section">
        <div class="form-section-heading"><span class="section-number">01</span><h3>从认识开始</h3></div>
        <div class="form-grid">
          <label class="field" :class="{ 'field-missing': attempted && !form.name?.trim() }"><span>姓名 / 昵称 <span class="required-mark">必填</span></span><input v-model.trim="form.name" name="name" autocomplete="off" maxlength="100" placeholder="怎么称呼这位人才" /></label>
          <label class="field" :class="{ 'field-missing': attempted && !form.ownerHrId }"><span>HR <span class="required-mark">必填</span></span><HrPicker v-model="form.ownerHrId" :employees="hrs" value-key="id" placeholder="输入姓名模糊搜索 HR" /></label>
          <label class="field" :class="{ 'contact-missing': attempted && !hasContact, 'field-invalid': phoneError }">手机 <span class="contact-hint">三选一必填</span><input v-model.trim="form.phone" name="phone" type="tel" maxlength="18" placeholder="11 位中国大陆手机号" /><small v-if="phoneError" class="field-error">{{ phoneError }}</small></label>
          <label class="field" :class="{ 'contact-missing': attempted && !hasContact, 'field-invalid': wechatError }">微信 <span class="contact-hint">三选一必填</span><input v-model.trim="form.wechat" name="wechat" maxlength="20" placeholder="6—20 位微信号" /><small v-if="wechatError" class="field-error">{{ wechatError }}</small></label>
          <label class="field" :class="{ 'contact-missing': attempted && !hasContact, 'field-invalid': emailError }">邮箱 <span class="contact-hint">三选一必填</span><input v-model.trim="form.email" name="email" type="email" maxlength="200" placeholder="name@example.com" /><small v-if="emailError" class="field-error">{{ emailError }}</small></label>
          <label class="field">首次接触日期<input v-model="form.applyTime" name="applyTime" type="date" /></label>
        </div>
        <p class="field-help">手机、微信、邮箱至少填写一项；已填写的每一项都会校验格式。历史人才可填写实际首次接触日期。</p>
        <div class="source-nickname-grid"><TalentChoiceGroup v-model="form.source" label="人才来源" :options="optionsWithCurrent(sources, form.source)" required :attempted="attempted" /><label class="field">来源平台昵称<input v-model.trim="form.nickname" name="nickname" maxlength="100" placeholder="人才在来源平台使用的名字" /></label></div>
        <div class="attachment-picker-grid"><div v-for="item in attachmentTypes" :key="item.key" class="attachment-slot"><label class="attachment-picker" :class="{ 'has-attachment': existingAttachment(item.label) }"><span class="attachment-icon">{{ item.icon }}</span><span><b>{{ item.label }}</b><small>{{ selectedFiles[item.key]?.name || existingAttachment(item.label)?.name || item.hint }}</small></span><input type="file" :accept="item.accept" @change="selectFile(item.key, $event)" /></label><button v-if="selectedFiles[item.key]" type="button" class="attachment-action" @click="selectedFiles[item.key] = null">撤销更换</button><button v-else-if="existingAttachment(item.label)" type="button" class="attachment-action remove" :disabled="deletingAssetId === existingAttachment(item.label).id" @click="removeExistingAttachment(item)">{{ deletingAssetId === existingAttachment(item.label).id ? '删除中…' : '删除' }}</button></div></div>
        <div v-if="duplicates.length" class="duplicate-notice" role="status">
          <strong>发现 {{ duplicates.length }} 份可能相关的档案</strong><p>可先核对，也可以继续建档；系统不会自动合并。</p>
          <div v-for="item in duplicates.slice(0, 4)" :key="item.id || item.person?.id" class="duplicate-row"><router-link :to="`/person/${item.id || item.person?.id}`" target="_blank" rel="noopener">{{ item.name || item.person?.name || '查看档案' }} ↗</router-link><span>{{ duplicateReason(item) }}</span></div>
        </div>
        <p v-else-if="duplicateError" class="field-help">{{ duplicateError }}</p>
      </section>

      <section class="form-section">
        <div class="form-section-heading"><span class="section-number">02</span><h3>当前的职业画像</h3></div>
        <TalentChoiceGroup v-model="form.gender" label="性别" :options="['男', '女']" required :attempted="attempted" compact />
        <div class="form-grid">
          <div class="field location-field"><span>所在城市</span><div class="location-control">
            <button type="button" class="select-trigger" :class="{ selected: form.location }" aria-haspopup="dialog" :aria-expanded="locationPickerOpen" @click="locationPickerOpen = !locationPickerOpen"><span>{{ form.location || '请选择省份和城市' }}</span><i aria-hidden="true">⌄</i></button>
            <div v-if="locationPickerOpen" class="location-panel" role="dialog" aria-label="选择所在城市">
              <div class="location-panel-head"><strong>选择所在城市</strong><button type="button" aria-label="关闭城市选择" @click="locationPickerOpen = false">×</button></div>
              <div class="location-cascade"><div ref="provinceTabs" class="province-tabs" role="tablist" aria-label="省份"><button v-for="item in chinaCities" :key="item.province" type="button" role="tab" :aria-selected="selectedProvince === item.province" :class="{ active: selectedProvince === item.province }" @click="selectedProvince = item.province">{{ item.province }}</button></div><div class="city-options" role="listbox" :aria-label="selectedProvince + '城市'"><button v-for="city in currentCities" :key="city" type="button" role="option" :aria-selected="form.location === formatLocation(selectedProvince, city)" :class="{ active: form.location === formatLocation(selectedProvince, city) }" @click="chooseCity(city)">{{ city }}</button></div></div>
              <div class="location-panel-foot"><span>先选省份，再选城市</span><button v-if="form.location" type="button" @click="clearLocation">清空选择</button></div>
            </div>
          </div></div>
          <label class="field" :class="{ 'field-missing': attempted && !form.experience }"><span>工作经验 <span class="required-mark">必填</span></span><select v-model="form.experience" name="experience"><option value="">请选择工作经验</option><option v-for="option in optionsWithCurrent(experienceChoices, form.experience)" :key="option" :value="option">{{ option }}</option></select></label>
          <TalentChoiceGroup v-model="form.workStatus" class="employment-state" label="当前任职状态" :options="['在职','离职']" compact @change="onWorkStatusChange" />
          <label v-if="form.workStatus === '在职'" class="field">当前公司<input v-model.trim="form.company" name="company" maxlength="200" placeholder="当前就职公司" /></label>
          <label v-if="form.workStatus === '在职'" class="field">当前职位<input v-model.trim="form.currentRole" name="currentRole" maxlength="200" placeholder="例如：品牌设计师" /></label>
          <TalentChoiceGroup v-model="form.tags" class="tag-field" label="人才标签（可多选）" :options="talentTags" multiple />
        </div>
      </section>

      <section class="form-section">
        <div class="form-section-heading"><span class="section-number">03</span><h3>招聘与意向</h3></div>
        <div class="form-grid">
          <label class="field" :class="{ 'field-missing': attempted && !form.jobId }"><span>意向岗位 <span class="required-mark">必填</span></span><select v-model="form.jobId" name="jobId" :disabled="loadingPositions"><option :value="null">请选择意向岗位</option><option v-for="job in availablePositions" :key="job.id" :value="job.id" :disabled="isInactive(job) && job.id !== person?.jobId">{{ positionLabel(job) }}{{ isInactive(job) ? '（已停招）' : '' }}</option></select></label>
          <div class="field" :class="{ 'field-invalid': salaryError, 'field-missing': attempted && (form.salaryMin === '' || form.salaryMin == null || form.salaryMax === '' || form.salaryMax == null) }"><span>期望月薪 <span class="muted">K / 月</span> <span class="required-mark">必填</span></span><div class="salary-range"><input v-model="form.salaryMin" name="salaryMin" type="number" min="0" max="100000" step="0.1" inputmode="decimal" aria-label="期望月薪最低值，单位 K" placeholder="最低" /><span aria-hidden="true">—</span><input v-model="form.salaryMax" name="salaryMax" type="number" min="0" max="100000" step="0.1" inputmode="decimal" aria-label="期望月薪最高值，单位 K" placeholder="最高" /></div><small v-if="salaryError" class="field-error">{{ salaryError }}</small></div>
        </div>
        <div v-if="selectedPosition" class="position-preview"><span><small>岗位性质</small><b>{{ selectedPosition.employmentType || '全职' }}</b></span><span><small>岗位预设薪资</small><b>{{ selectedPosition.minSalary != null && selectedPosition.maxSalary != null ? `${selectedPosition.minSalary} — ${selectedPosition.maxSalary} K / 月` : '面议 / 待定' }}</b></span><span><small>Base 地</small><b>{{ selectedPosition.baseLocation || '待补充' }}</b></span></div>
        <div v-if="positionError" class="inline-error" role="alert">{{ positionError }}<button type="button" class="clear-choice" @click="loadPositions">重试</button></div>
        <div class="status-result-control"><fieldset class="choice-field" :class="{ 'choice-missing': attempted && !form.status }"><legend>招聘状态 <span class="required-mark">必填</span><button v-if="form.result" type="button" class="result-summary" :aria-expanded="resultPopoverOpen" @click="resultPopoverOpen = !resultPopoverOpen">当前结果：{{ form.result }} <span aria-hidden="true">{{ resultPopoverOpen ? '收起' : '修改' }}</span></button></legend><div class="segment-options"><label v-for="option in optionsWithCurrent(statuses, form.status)" :key="option" class="segment-option" :class="{ selected: form.status === option }"><input v-model="form.status" type="radio" name="status" :value="option" @change="onStatusSelected" /><span>{{ option }}</span></label></div></fieldset>
          <fieldset v-if="resultPopoverOpen" class="choice-field result-popover"><legend>选择当前结果</legend><div class="segment-options"><button v-for="option in results" :key="option" type="button" class="segment-option" :class="{ selected: form.result === option }" @click="chooseResult(option)"><span>{{ option }}</span></button></div></fieldset>
        </div>
        <label v-if="requiresReason || form.reason" class="field reason-field" :class="{ 'field-missing': attempted && requiresReason && !form.reason?.trim() }"><span>{{ requiresReason ? '拒绝 / 关闭原因' : '历史结果原因' }} <span v-if="requiresReason" class="required-mark">必填</span></span><textarea v-model.trim="form.reason" name="reason" rows="3" maxlength="3000" placeholder="记录具体原因，例如：2021 年通过面试，因薪资预期未达成一致而未继续推进" /></label>
        <div class="form-grid intent-grid">
          <fieldset class="choice-field"><legend>公司意愿</legend><div class="segment-options"><label v-for="option in optionsWithCurrent(companyIntentions, form.companyIntent)" :key="option" class="segment-option" :class="{ selected: form.companyIntent === option }"><input v-model="form.companyIntent" type="radio" name="companyIntent" :value="option" /><span>{{ option }}</span></label></div></fieldset>
          <fieldset class="choice-field"><legend>人才意愿</legend><div class="segment-options"><label v-for="option in optionsWithCurrent(talentIntentions, form.talentIntent)" :key="option" class="segment-option" :class="{ selected: form.talentIntent === option }"><input v-model="form.talentIntent" type="radio" name="talentIntent" :value="option" /><span>{{ option }}</span></label></div></fieldset>
        </div>
      </section>

      <section class="form-section last-section">
        <div class="form-section-heading"><span class="section-number">04</span><h3>让下一次联系有着落</h3></div>
        <div class="followup-box"><div class="form-grid">
          <label class="field">下一步<input v-model.trim="form.nextStep" name="nextStep" maxlength="500" placeholder="例如：周五约一次作品沟通" /></label>
          <label class="field">下次跟进时间<input v-model="form.nextContactAt" name="nextContactAt" type="datetime-local" /></label>
        </div></div>
        <label class="field note-field">人才备注<textarea v-model.trim="form.remark" name="remark" rows="3" maxlength="5000" placeholder="擅长什么、合作偏好、值得记住的细节……" /></label>
      </section>
      <div v-if="visibleError" class="inline-error form-error" role="alert">{{ visibleError }}</div>
    </form>
    <template #footer><div class="dialog-actions"><span class="muted save-hint">{{ person?.id ? '关键变化会保留在人才时间轴' : '每一次认识，都值得被记住' }}</span><button type="button" class="btn btn-secondary" :disabled="saving" @click="visible = false">取消</button><button type="submit" form="candidate-profile-form" class="btn btn-primary" :disabled="saving || loadingPositions">{{ saving ? '正在保存…' : (person?.id ? '保存修改' : '建立人才档案') }}</button></div></template>
  </el-dialog>
  <el-dialog v-model="duplicateDecisionOpen" title="发现可能重复的人才档案" width="min(620px, 94vw)" :close-on-click-modal="false"><p class="duplicate-dialog-copy">姓名、手机或邮箱中至少有一项与已有档案相同。请选择要怎么处理：</p><div class="duplicate-choice-list"><label v-for="item in duplicates" :key="item.id || item.person?.id" :class="{ selected: String(selectedDuplicateId) === String(item.id || item.person?.id) }"><input v-model="selectedDuplicateId" type="radio" :value="item.id || item.person?.id" /><span><b>{{ item.name || item.person?.name || '未命名人才' }}</b><small>{{ duplicateReason(item) }} · {{ item.job || '岗位待补充' }}</small></span></label></div><template #footer><div class="duplicate-dialog-actions"><button class="btn" @click="duplicateDecisionOpen = false">取消</button><button class="btn" :disabled="!selectedDuplicateId" @click="viewDuplicate">进去看</button><button class="btn" :disabled="!selectedDuplicateId" @click="mergeIntoDuplicate">进去看并补充新信息</button><button class="btn btn-primary" @click="continueCreate">仍然新建</button></div></template></el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createCandidate, updateCandidate, getDuplicates, uploadAsset, deleteAsset } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import { getHrList } from '@/api/hr'
import HrPicker from '@/components/HrPicker.vue'
import TalentChoiceGroup from '@/components/TalentChoiceGroup.vue'
import { chinaCities, experienceOptions, findProvinceByLocation, formatLocation } from '@/data/chinaCities'

const props = defineProps({ modelValue: Boolean, person: { type: Object, default: null }, initialDraft: { type: Object, default: null } })
const router = useRouter()
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const sources = ['BOSS直聘', '猎聘', '智联招聘', '内推', '历史导入', '其他']
const statuses = ['待联系', '沟通中', '面试中', 'Offer中', '已入职', '人才储备', '已关闭']
const results = ['待定', '录用', '候选人拒绝', '公司淘汰', '暂缓']
const talentTags = ['品牌设计', '视觉设计', '电商经验', '内容运营', '市场营销', '软件开发', '产品经理', '数据分析', '项目管理', '管理经验', '校招人才', '跨境业务', '长期储备', '可远程', '可出差', '高潜人才']
const currentYear = new Date().getFullYear()
const experienceChoices = [`${currentYear - 1}届应届生`, `${currentYear}届应届生`, `${currentYear + 1}届应届生`, `${currentYear + 2}届应届生`, ...experienceOptions.filter(option => option !== '应届生')]
const attachmentTypes = [{ key: 'idPhoto', label: '证件照', hint: '选择 JPG、PNG 或 WebP', accept: 'image/jpeg,image/png,image/webp', icon: '证' }, { key: 'resume', label: '简历', hint: '选择 PDF、Word 或图片', accept: '.pdf,.doc,.docx,.png,.jpg,.jpeg,.webp', icon: '历' }, { key: 'otherMaterial', label: '其它资料', hint: '作品集、证书等资料', accept: '.pdf,.doc,.docx,.png,.jpg,.jpeg,.webp,.ppt,.pptx,.zip', icon: '资' }]
const companyIntentions = ['未判断', '低', '一般', '较高', '很高', '放弃']
const talentIntentions = ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']
const form = reactive({})
const positions = ref([])
const hrs = ref([])
const duplicates = ref([])
const duplicateError = ref('')
const duplicateDecisionOpen = ref(false)
const selectedDuplicateId = ref('')
const forceCreate = ref(false)
const error = ref('')
const positionError = ref('')
const saving = ref(false)
const attempted = ref(false)
const resultPopoverOpen = ref(false)
const selectedFiles = reactive({ idPhoto: null, resume: null, otherMaterial: null })
const existingAssets = ref([])
const deletingAssetId = ref(null)
const loadingPositions = ref(false)
const locationPickerOpen = ref(false)
const selectedProvince = ref(chinaCities[0].province)
const provinceTabs = ref(null)
let duplicateTimer
let duplicateRequest = 0

function today() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}` }
function unwrap(response) { let value = response; if (value?.data !== undefined) value = value.data; if (value?.data !== undefined) value = value.data; return value }
function optionsWithCurrent(options, current) { return current && !options.includes(current) ? [...options, current] : options }
function positionLabel(job) { return `${job.company || '未设置公司'}：${job.name || '未命名岗位'}：${job.recruitmentCode || String(job.id || '').padStart(3, '0')}（已招 ${job.hiredCount || 0}/${job.headcount || 1}）` }
function onStatusSelected() { if (form.status === '已入职') form.result = '录用'; else if (form.result === '录用') form.result = '待定'; resultPopoverOpen.value = true }
function chooseResult(option) { form.result = option; resultPopoverOpen.value = false }
function onWorkStatusChange() { if (form.workStatus === '离职') { form.company = ''; form.currentRole = '' } }
function selectFile(key, event) { selectedFiles[key] = event.target.files?.[0] || null }
function existingAttachment(type) { return existingAssets.value.find(asset => asset.type === type) }
async function removeExistingAttachment(item) {
  const asset = existingAttachment(item.label)
  if (!asset || deletingAssetId.value) return
  try { await ElMessageBox.confirm(`将删除${item.label}“${asset.name}”，删除后不能恢复。`, `删除${item.label}`, { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }) }
  catch { return }
  deletingAssetId.value = asset.id
  try { await deleteAsset(props.person.id, asset.id); existingAssets.value = existingAssets.value.filter(current => current.id !== asset.id); ElMessage.success(`${item.label}已删除`); emit('saved', props.person) }
  catch (err) { ElMessage.error(err.response?.data?.message || err.message || '删除失败，请稍后重试') }
  finally { deletingAssetId.value = null }
}
function isInactive(job) { return ['暂停招聘', '已关闭', '已停招', '停招', '暂停', '关闭'].includes(job.status) }
const selectedPosition = computed(() => positions.value.find(job => String(job.id) === String(form.jobId)))
const currentCities = computed(() => chinaCities.find(item => item.province === selectedProvince.value)?.cities || [])
const availablePositions = computed(() => {
  const list = [...positions.value]
  if (props.person?.jobId && !list.some(job => String(job.id) === String(props.person.jobId))) list.push({ id: props.person.jobId, name: props.person.job || '原关联岗位', status: '已停招' })
  return list
})
const hasContact = computed(() => [form.phone, form.wechat, form.email].some(value => String(value || '').trim()))
const phoneError = computed(() => { const value=String(form.phone || '').trim(); if(!value)return ''; return /^(?:\+?86)?1[3-9]\d{9}$/.test(value.replace(/[\s()-]/g,'')) ? '' : '请输入正确的 11 位中国大陆手机号' })
const wechatError = computed(() => { const value=String(form.wechat || '').trim(); if(!value)return ''; return /^[A-Za-z][-_A-Za-z0-9]{5,19}$/.test(value) ? '' : '微信号需以字母开头，共 6—20 位，可含数字、减号和下划线' })
const emailError = computed(() => { const value=String(form.email || '').trim(); if(!value)return ''; return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ? '' : '请输入正确的邮箱地址，例如 name@example.com' })
const salaryError = computed(() => { const emptyMin=form.salaryMin===''||form.salaryMin==null,emptyMax=form.salaryMax===''||form.salaryMax==null;if(emptyMin&&emptyMax)return '';if(emptyMin||emptyMax)return '最低薪资和最高薪资需要同时填写';const min=Number(form.salaryMin),max=Number(form.salaryMax);if(!Number.isFinite(min)||!Number.isFinite(max)||min<0||max<0)return '薪资必须是有效的非负数';return max<min?'最高薪资必须大于或等于最低薪资':'' })
const visibleError = computed(() => error.value === '最高薪资必须大于最低薪资' && !salaryError.value ? '' : error.value)
const requiresReason = computed(() => ['候选人拒绝', '公司淘汰'].includes(form.result) || form.status === '已关闭')

function resetForm() {
  const person = props.person || {}
  const legacySalary = String(person.salary || '').match(/([\d.]+)\s*[-—~～至]\s*([\d.]+)/)
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, {
    name: person.name || person.nickname || '', nickname: person.nickname || '', gender: person.gender || '',
    phone: person.phone || '', wechat: person.wechat || '', email: person.email || '',
    source: person.source || '', ownerHrId: person.ownerHrId || '',
    jobId: person.jobId || null, job: person.job || '', applyTime: (person.applyTime || today()).slice(0, 10),
    salaryMin: person.salaryMin ?? (legacySalary ? Number(legacySalary[1]) : ''), salaryMax: person.salaryMax ?? (legacySalary ? Number(legacySalary[2]) : ''),
    status: person.status || '沟通中', result: person.result || '待定', reason: person.reason || '',
    location: person.location || '', workStatus: person.workStatus || (person.company || person.currentRole ? '在职' : ''), company: person.company || '', currentRole: person.currentRole || '',
    tags: Array.isArray(person.tags) ? [...person.tags] : String(person.tags || '').split(/[,，]/).map(value => value.trim()).filter(Boolean), experience: person.experience || '', remark: person.remark || '',
    nextStep: person.nextStep || '', nextContactAt: String(person.nextContactAt || '').replace(' ', 'T').slice(0, 16),
    companyIntent: person.companyIntent || '未判断', talentIntent: person.talentIntent || '未知'
  })
  if (props.initialDraft && person.id) {
    for (const [key, value] of Object.entries(props.initialDraft)) {
      if (key === 'tags') { form.tags = [...new Set([...(form.tags || []), ...(Array.isArray(value) ? value : [])])]; continue }
      const current = form[key]
      if ((current === '' || current == null) && value !== '' && value != null) form[key] = value
    }
  }
  selectedProvince.value = findProvinceByLocation(form.location).province
  attempted.value = false; resultPopoverOpen.value = false
  Object.keys(selectedFiles).forEach(key => { selectedFiles[key] = null })
  existingAssets.value = [...(person.assets || [])]
  locationPickerOpen.value = false
  error.value = ''; duplicates.value = []; duplicateError.value = ''; duplicateDecisionOpen.value = false; selectedDuplicateId.value = ''; forceCreate.value = false
}

function chooseCity(city) { form.location = formatLocation(selectedProvince.value, city); locationPickerOpen.value = false }
function clearLocation() { form.location = ''; locationPickerOpen.value = false }

async function loadPositions() {
  loadingPositions.value = true; positionError.value = ''
  try { const data = unwrap(await getPositionList()); positions.value = Array.isArray(data) ? data : data?.records || data?.items || [] }
  catch (err) { positionError.value = err.message || '岗位加载失败，可重试或暂不关联岗位建档。' }
  finally { loadingPositions.value = false }
}
async function loadHrs() {
  try { hrs.value = await getHrList(); if (!form.ownerHrId && hrs.value.length) form.ownerHrId = (hrs.value.find(hr => hr.current) || hrs.value[0]).id }
  catch (err) { error.value = err.message || 'HR 名单加载失败，请稍后重试。' }
}

function duplicateReason(item) {
  const reasons = item.matchReasons || item.reasons || item.matchReason
  return Array.isArray(reasons) ? reasons.join(' · ') : reasons || [item.phone && item.phone === form.phone ? '手机号相同' : '', item.wechat && item.wechat === form.wechat ? '微信相同' : '', item.email && item.email === form.email ? '邮箱相同' : '', item.name === form.name ? '姓名相同' : ''].filter(Boolean).join(' · ') || '联系线索相似'
}
function viewDuplicate() { if (!selectedDuplicateId.value) return; window.open(`/person/${selectedDuplicateId.value}`, '_blank', 'noopener,noreferrer') }
function mergeIntoDuplicate() {
  if (!selectedDuplicateId.value) return
  sessionStorage.setItem('sethubCandidateMergeDraft', JSON.stringify({ ...form, tags: [...(form.tags || [])] }))
  duplicateDecisionOpen.value = false; visible.value = false
  router.push({ path: `/person/${selectedDuplicateId.value}`, query: { action: 'merge-profile' } })
}
function continueCreate() { forceCreate.value = true; duplicateDecisionOpen.value = false; submit() }
async function checkDuplicates() {
  const requestId = ++duplicateRequest
  if (!visible.value || ![form.name, form.phone, form.wechat, form.email].some(value => value?.trim())) { duplicates.value = []; return }
  try {
    const data = unwrap(await getDuplicates({ name: form.name, phone: form.phone, wechat: form.wechat, email: form.email, excludeId: props.person?.id }))
    if (requestId !== duplicateRequest) return
    const matches = Array.isArray(data) ? data : data?.records || data?.matches || []
    duplicates.value = matches.filter(item => String(item.id || item.person?.id) !== String(props.person?.id))
    duplicateError.value = ''
  } catch { if (requestId === duplicateRequest) duplicateError.value = '重复档案检查暂不可用，仍可保存，稍后请核对联系信息。' }
}
watch(() => props.modelValue, open => { if (open) { resetForm(); loadPositions(); loadHrs() } else { clearTimeout(duplicateTimer); duplicateRequest++ } }, { immediate: true })
watch(locationPickerOpen, open => {
  if (!open) return
  nextTick(() => {
    const list = provinceTabs.value, active = list?.querySelector('.active')
    if (list && active) {
      const top = active.offsetTop - list.offsetTop - (list.clientHeight - active.clientHeight) / 2
      list.scrollTop = Math.max(0, top)
    }
  })
})
watch(() => [form.name, form.phone, form.wechat, form.email], () => { forceCreate.value = false; clearTimeout(duplicateTimer); if (visible.value) duplicateTimer = setTimeout(checkDuplicates, 450) })
onBeforeUnmount(() => { clearTimeout(duplicateTimer); duplicateRequest++ })

async function submit() {
  if (saving.value) return
  attempted.value = true
  error.value = ''
  if (!form.name?.trim() || !form.ownerHrId || !form.source) { error.value = '请填写姓名或昵称、HR，并选择人才来源。'; return }
  if (!form.gender || !form.experience || !form.jobId || !form.status) { error.value = '请填写性别、工作经验、意向岗位和招聘状态。'; return }
  if (!hasContact.value) { error.value = '请至少填写手机、微信或邮箱中的一种联系方式。'; return }
  if (phoneError.value || wechatError.value || emailError.value) { error.value = phoneError.value || wechatError.value || emailError.value; return }
  const min = form.salaryMin === '' || form.salaryMin == null ? null : Number(form.salaryMin)
  const max = form.salaryMax === '' || form.salaryMax == null ? null : Number(form.salaryMax)
  if (min == null || max == null) { error.value = '请填写期望薪资的最低和最高金额。'; return }
  if (salaryError.value) { error.value = salaryError.value; return }
  if (requiresReason.value && !form.reason?.trim()) { error.value = '请记录拒绝、淘汰或关闭的具体原因，方便以后回顾。'; return }
  if (form.nextContactAt && !form.nextStep?.trim()) { error.value = '设置了跟进时间，请补充要完成的下一步。'; return }
  if (!props.person?.id && duplicates.value.length && !forceCreate.value) { selectedDuplicateId.value = duplicates.value[0]?.id || duplicates.value[0]?.person?.id || ''; duplicateDecisionOpen.value = true; return }
  const selectedHr = hrs.value.find(hr => String(hr.id) === String(form.ownerHrId))
  const payload = { ...form, ownerHrId: Number(form.ownerHrId), owner: selectedHr?.name || '', salaryMin: min, salaryMax: max, salary: min == null ? '' : `${min}-${max}K`, jobId: form.jobId || null, job: selectedPosition.value?.name || (form.jobId ? form.job : ''), nextContactAt: form.nextContactAt ? `${form.nextContactAt}:00` : null }
  if (props.person?.version != null) payload.version = props.person.version
  saving.value = true
  try {
    const saved = unwrap(await (props.person?.id ? updateCandidate(props.person.id, payload) : createCandidate(payload)))
    const uploads = attachmentTypes.filter(item => selectedFiles[item.key]).map(item => uploadAsset(saved.id, selectedFiles[item.key], item.label))
    if (uploads.length) { const uploadResults = await Promise.allSettled(uploads); const failed = uploadResults.filter(result => result.status === 'rejected').length; if (failed) ElMessage.warning(`人才档案已保存，${failed} 个附件上传失败，可在详情页重新上传`) }
    ElMessage.success(props.person?.id ? '人才档案已更新' : '人才档案已建立')
    emit('saved', saved); visible.value = false
  } catch (err) { error.value = err.response?.data?.message || err.message || '保存失败，请稍后重试。' }
  finally { saving.value = false }
}
</script>

<style scoped>
.dialog-intro{margin:0 0 24px;line-height:1.8;font-size:13px}.profile-form{color:#1f2329}.form-section{padding:0 0 24px;margin:0 0 24px;border-bottom:1px solid #e5e6eb}.last-section{margin-bottom:0;padding-bottom:0;border:0}.form-section-heading{display:flex;align-items:center;gap:10px;margin-bottom:18px}.form-section-heading h3{font-size:15px;margin:0;font-weight:650;color:#1f2329}.section-number{color:#3370ff;font-size:10px;letter-spacing:1px;background:#edf3ff;border-radius:8px;padding:6px}.optional-label{font-size:11px;margin-left:auto}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px}.field{display:flex;flex-wrap:wrap;flex-direction:column;gap:8px;font-size:12px;font-weight:550;color:#4e5969}.field .required{color:#d65c5c}.required{color:#d65c5c}.field input,.field select,.field textarea{box-sizing:border-box;width:100%;min-width:0;border:1px solid #d9dce3;background:#f7f8fa;border-radius:12px;padding:11px 13px;color:#1f2329;font:inherit;font-size:13px;line-height:1.45;outline:none;transition:border-color .2s,box-shadow .2s,background .2s}.field input:focus,.field select:focus,.field textarea:focus{border-color:#3370ff;box-shadow:0 0 0 3px rgba(51,112,255,.12);background:#fff}.field input::placeholder,.field textarea::placeholder{color:#8f959e;font-weight:400}.field textarea{resize:vertical}.field-help{margin:11px 0 0;color:#8f959e;font-size:11px;line-height:1.8}.choice-field{border:0;margin:20px 0 0;padding:0;min-width:0}.choice-field legend{padding:0;margin-bottom:9px;color:#4e5969;font-size:12px;font-weight:550}.segment-options{display:flex;align-items:center;flex-wrap:wrap;gap:6px}.segment-option{position:relative;cursor:pointer;border:1px solid #d9dce3;background:#f7f8fa;color:#646a73;border-radius:10px;transition:background .18s,border-color .18s,color .18s;overflow:hidden}.segment-option span{display:block;padding:8px 12px;font-size:12px;line-height:1.3}.segment-option input{position:absolute;opacity:0;inset:0;cursor:pointer;margin:0;width:100%;height:100%}.segment-option:hover{background:#eef2ff}.segment-option.selected{background:#e8f0ff;border-color:#bacefd;color:#245bdb;box-shadow:0 2px 5px rgba(51,112,255,.08)}.segment-option:focus-within{outline:2px solid #8fb0ff;outline-offset:2px}.compact{margin-bottom:16px}.compact .segment-option span{padding:9px 25px}.clear-choice{border:0;background:transparent;color:#3370ff;font:inherit;font-size:12px;padding:6px;cursor:pointer}.salary-range{display:flex;align-items:center;gap:10px}.salary-range>span{font-weight:400;color:#8f959e}.salary-range input{width:calc(50% - 15px)}.reason-field,.note-field{margin-top:17px}.intent-grid .choice-field{margin-top:20px}.intent-grid .segment-option span{padding:8px 9px}.dialog-actions{display:flex;align-items:center;justify-content:flex-end;gap:10px;border-top:1px solid #e5e6eb;padding-top:18px}.save-hint{font-size:11px;margin-right:auto}.dialog-actions .btn{padding:11px 19px;border-radius:12px;font-size:12px;cursor:pointer}.duplicate-notice{margin-top:16px;padding:14px 16px;border:1px solid #ded6c5;border-radius:13px;background:#f7f4ec;color:#89764e;font-size:12px}.duplicate-notice p{font-size:11px;margin:5px 0 12px;color:#968766}.duplicate-row{display:flex;justify-content:space-between;gap:12px;margin-top:8px}.duplicate-row a{color:#766649;text-decoration:underline;text-underline-offset:3px}.duplicate-row span{font-size:11px}.inline-error{padding:12px 14px;background:#f8eeec;border:1px solid #e6d0ca;color:#9c6659;border-radius:12px;font-size:12px;line-height:1.6;margin-top:12px}.form-error{position:sticky;bottom:0;box-shadow:0 -6px 15px rgba(255,255,255,.9)}
.location-field{position:relative}.location-control{position:relative}.select-trigger{width:100%;min-height:43px;display:flex;align-items:center;justify-content:space-between;gap:12px;border:1px solid #dbe2e9;background:rgba(247,249,252,.85);border-radius:12px;padding:11px 13px;color:#99a4b1;font:inherit;font-size:13px;text-align:left}.select-trigger.selected{color:#2c3b4d}.select-trigger i{font-style:normal;color:#8896a5;transition:transform .2s}.select-trigger[aria-expanded=true] i{transform:rotate(180deg)}.select-trigger:focus-visible{border-color:#3370ff;box-shadow:0 0 0 3px rgba(51,112,255,.12);outline:0}.location-panel{position:absolute;z-index:20;top:calc(100% + 8px);left:0;width:min(610px,calc(94vw - 70px));padding:15px;background:#fff;border:1px solid #dde4ea;border-radius:15px;box-shadow:0 18px 50px rgba(31,35,41,.16)}.location-panel-head,.location-panel-foot{display:flex;align-items:center;justify-content:space-between;gap:12px}.location-panel-head{padding:0 2px 12px;border-bottom:1px solid #edf0f3}.location-panel-head strong{font-size:13px;color:#1f2329}.location-panel-head button{border:0;background:#f2f3f5;color:#646a73;width:27px;height:27px;border-radius:8px;font-size:18px}.location-cascade{display:grid;grid-template-columns:148px 1fr;height:300px;min-height:0}.province-tabs{min-height:0;overflow:auto;padding:8px 8px 8px 0;border-right:1px solid #edf0f3;display:flex;flex-direction:column;gap:3px}.province-tabs button{border:0;background:transparent;color:#646a73;text-align:left;padding:8px 10px;border-radius:8px;font-size:11px}.province-tabs button:hover,.province-tabs button.active{background:#e8f0ff;color:#245bdb;font-weight:600}.city-options{min-height:0;overflow:auto;align-content:start;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px;padding:12px}.city-options button{border:1px solid #e5e6eb;background:#f7f8fa;color:#4e5969;border-radius:9px;min-height:35px;padding:7px;font-size:11px}.city-options button:hover,.city-options button.active{background:#e8f0ff;border-color:#bacefd;color:#245bdb}.location-panel-foot{border-top:1px solid #edf0f3;padding:11px 2px 0;color:#8f959e;font-size:10px}.location-panel-foot button{border:0;background:transparent;color:#3370ff;font-size:11px}.field select{appearance:auto}
.required-mark{display:inline-flex;align-items:center;margin-left:5px;padding:2px 6px;border-radius:999px;background:#fff0ed;color:#c75d4b;font-size:9px;font-weight:700;vertical-align:1px}.contact-hint{margin-left:5px;color:#9a6d63;font-size:9px;font-weight:500}.field-invalid>input,.field-invalid .salary-range input{background:#fff4f4!important;border-color:#df8d8d!important}.field-error{color:#c94b4b;font-size:10px;font-weight:500;line-height:1.5}.field-missing>input,.field-missing>select,.field-missing>textarea,.contact-missing>input,.choice-missing{background:#fff3f1!important;border-color:#df7667!important;animation:required-flash .42s ease 2}.choice-missing{padding:12px;border:1px solid;border-radius:12px}.result-reveal{animation:result-arrive .24s ease both}.tag-field{grid-column:1/-1;margin-top:0}.attachment-picker-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:17px}.attachment-picker{position:relative;display:flex;align-items:center;gap:10px;padding:12px;border:1px dashed #cad5e1;border-radius:12px;background:#f8fafc;cursor:pointer;min-width:0}.attachment-picker:hover{border-color:#90a9c1;background:#f1f6fb}.attachment-picker>input{position:absolute;inset:0;opacity:0;cursor:pointer}.attachment-icon{display:grid;place-items:center;width:32px;height:32px;flex:none;border-radius:9px;background:#e6eef8;color:#547391;font-size:11px;font-weight:700}.attachment-picker b,.attachment-picker small{display:block}.attachment-picker b{font-size:11px;color:#4c6077}.attachment-picker small{margin-top:4px;color:#95a2b1;font-size:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:150px}@keyframes required-flash{50%{box-shadow:0 0 0 4px rgba(213,89,70,.17)}}@keyframes result-arrive{from{opacity:0;transform:translateY(-5px)}to{opacity:1;transform:none}}
.profile-form{color:#384657}.form-section{border-bottom-color:rgba(104,124,147,.13)}.form-section-heading h3{color:#29394c}.section-number{color:#73859c;background:#edf1f6}.field,.choice-field legend{color:#566477}.field .required,.required{color:#a87566}.field input,.field select,.field textarea{border-color:#dbe2e9;background:rgba(247,249,252,.85);color:#2c3b4d}.field input:focus,.field select:focus,.field textarea:focus{border-color:#8ea6c0;box-shadow:0 0 0 3px rgba(110,144,176,.12)}.field input::placeholder,.field textarea::placeholder{color:#99a4b1}.field-help{color:#8692a1}.segment-option{border-color:#e2e7ed;background:#f5f7fa;color:#7b8796}.segment-option:hover{background:#eaf0f5}.segment-option.selected{background:#e7eef6;border-color:#aabed3;color:#47617e;box-shadow:0 2px 5px rgba(82,112,143,.06)}.segment-option:focus-within{outline-color:#829eba}.clear-choice{color:#768ba2}.salary-range>span{color:#97a3b1}.dialog-actions{border-top-color:#e9edf2}.select-trigger:focus-visible{border-color:#8ea6c0;box-shadow:0 0 0 3px rgba(110,144,176,.12)}.location-panel{box-shadow:0 18px 50px rgba(50,70,90,.18)}.location-panel-head strong{color:#405064}.location-panel-head button{background:#eef2f5;color:#7c8997}.province-tabs button{color:#7e8995}.province-tabs button:hover,.province-tabs button.active{background:#eaf0f4;color:#4e687d}.city-options button{border-color:#e5e9ed;background:#f8f9fb;color:#72808e}.city-options button:hover,.city-options button.active{background:#e7eef4;border-color:#b7c7d5;color:#4d687e}.location-panel-foot{color:#9aa4ae}.location-panel-foot button{color:#6e8598}
.field input,.field select,.field textarea,.select-trigger{background:#fff}.field input:disabled,.field select:disabled,.field textarea:disabled{background:#f2f3f5;color:#8f959e}.employment-state{margin-top:0}.position-preview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin:12px 0 4px;padding:13px;border:1px solid #dfe5ec;border-radius:12px;background:#f7f8fa}.position-preview small,.position-preview b{display:block}.position-preview small{font-size:9px;color:#8f959e;margin-bottom:5px}.position-preview b{font-size:11px;color:#4e5969}.status-result-control{position:relative}.result-summary{margin-left:8px;padding:0;border:0;background:transparent;color:#3370ff;font:inherit;font-size:10px;cursor:pointer}.result-summary>span{margin-left:3px;color:#8b96a4;font-size:9px}.result-popover{position:absolute;z-index:25;top:100%;left:0;max-width:560px;padding:13px!important;border:1px solid #d9e2ef!important;border-radius:13px;background:#fff;box-shadow:0 14px 40px rgba(31,35,41,.16);animation:result-arrive .2s ease both}.result-popover .segment-option{font:inherit;padding:0}
.field-missing :deep(.hr-picker>input){background:#fff3f1!important;border-color:#df7667!important;animation:required-flash .42s ease 2}
.attachment-slot{position:relative;min-width:0}.attachment-slot .attachment-picker{box-sizing:border-box;width:100%;padding-right:58px}.attachment-picker.has-attachment{border-style:solid;background:#f1f6fb}.attachment-action{position:absolute;z-index:2;right:8px;top:50%;transform:translateY(-50%);border:0;background:#e8eef5;color:#60768e;border-radius:7px;padding:5px 7px;font-size:9px;cursor:pointer}.attachment-action.remove{background:#fff0ed;color:#b65d50}.attachment-action:disabled{opacity:.55;cursor:wait}
.duplicate-dialog-copy{margin:0 0 16px;color:#68788b;font-size:12px;line-height:1.8}.duplicate-choice-list{display:grid;gap:9px}.duplicate-choice-list label{display:flex;align-items:center;gap:11px;padding:13px;border:1px solid #e0e5eb;border-radius:12px;background:#f8fafc;cursor:pointer}.duplicate-choice-list label.selected{border-color:#8aa9db;background:#edf4ff}.duplicate-choice-list b,.duplicate-choice-list small{display:block}.duplicate-choice-list b{font-size:13px;color:#3c4f66}.duplicate-choice-list small{margin-top:5px;color:#8d99a7;font-size:10px}.duplicate-dialog-actions{display:flex;justify-content:flex-end;gap:8px;flex-wrap:wrap}
@media(max-width:600px){.form-grid,.attachment-picker-grid{grid-template-columns:1fr}.dialog-intro{font-size:12px}.save-hint{display:none}.dialog-actions .btn{flex:1}.segment-option span{padding:8px 10px}.duplicate-row{flex-direction:column;gap:3px}.form-section{padding-bottom:20px;margin-bottom:20px}.location-panel{position:fixed;left:14px;right:14px;top:18vh;width:auto}.location-cascade{grid-template-columns:120px 1fr;height:280px}.city-options{grid-template-columns:repeat(2,minmax(0,1fr));padding:9px}}
</style>
<style scoped>
.initial-status-card{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:7px 14px;padding:14px 16px;border:1px solid #dbe5f3;border-radius:12px;background:linear-gradient(135deg,#f7faff,#eef4ff)}.initial-status-card>span{font-size:13px;color:#4d5563;font-weight:550}.initial-status-card>strong{justify-self:end;color:#2f6fe6;font-size:14px}.initial-status-card>small{grid-column:1/-1;color:#7d8da0;font-size:11px}
</style>

