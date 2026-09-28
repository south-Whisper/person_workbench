<template>
  <el-dialog v-model="visible" :title="person?.id ? '编辑人才档案' : '认识一位新人才'" width="min(820px, 94vw)" top="5vh" class="talent-form-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="dialog-intro muted">先记下姓名或昵称、来源、负责人和一种联系方式，其余信息可以慢慢补全。</p>
    <form id="candidate-profile-form" class="profile-form" @submit.prevent="submit">
      <section class="form-section">
        <div class="form-section-heading"><span class="section-number">01</span><h3>从认识开始</h3></div>
        <div class="form-grid">
          <label class="field"><span>姓名 / 昵称 <span class="required">*</span></span><input v-model.trim="form.name" name="name" autocomplete="off" maxlength="100" placeholder="怎么称呼这位人才" required /></label>
          <label class="field"><span>负责人 <span class="required">*</span></span><input v-model.trim="form.owner" name="owner" maxlength="100" placeholder="由谁持续跟进" required /></label>
          <label class="field">手机<input v-model.trim="form.phone" name="phone" type="tel" maxlength="40" placeholder="手机或电话号码" /></label>
          <label class="field">微信<input v-model.trim="form.wechat" name="wechat" maxlength="100" placeholder="微信号" /></label>
          <label class="field">邮箱<input v-model.trim="form.email" name="email" type="email" maxlength="200" placeholder="name@example.com" /></label>
          <label class="field">首次接触日期<input v-model="form.applyTime" name="applyTime" type="date" /></label>
        </div>
        <p class="field-help">手机、微信、邮箱至少填写一项。历史人才可填写实际首次接触日期。</p>
        <fieldset class="choice-field"><legend>人才来源 <span class="required">*</span></legend><div class="segment-options">
          <label v-for="option in optionsWithCurrent(sources, form.source)" :key="option" class="segment-option" :class="{ selected: form.source === option }"><input v-model="form.source" type="radio" name="source" :value="option" required /><span>{{ option }}</span></label>
        </div></fieldset>
        <div v-if="duplicates.length" class="duplicate-notice" role="status">
          <strong>发现 {{ duplicates.length }} 份可能相关的档案</strong><p>可先核对，也可以继续建档；系统不会自动合并。</p>
          <div v-for="item in duplicates.slice(0, 4)" :key="item.id || item.person?.id" class="duplicate-row"><router-link :to="`/person/${item.id || item.person?.id}`" target="_blank" rel="noopener">{{ item.name || item.person?.name || '查看档案' }} ↗</router-link><span>{{ duplicateReason(item) }}</span></div>
        </div>
        <p v-else-if="duplicateError" class="field-help">{{ duplicateError }}</p>
      </section>

      <section class="form-section">
        <div class="form-section-heading"><span class="section-number">02</span><h3>当前的职业画像</h3><span class="muted optional-label">选填</span></div>
        <fieldset class="choice-field"><legend>性别</legend><div class="segment-options compact"><label v-for="option in ['男', '女']" :key="option" class="segment-option" :class="{ selected: form.gender === option }"><input v-model="form.gender" type="radio" name="gender" :value="option" /><span>{{ option }}</span></label><button v-if="form.gender" type="button" class="clear-choice" @click="form.gender = ''">清空</button></div></fieldset>
        <div class="form-grid">
          <div class="field location-field"><span>所在城市</span><div class="location-control">
            <button type="button" class="select-trigger" :class="{ selected: form.location }" aria-haspopup="dialog" :aria-expanded="locationPickerOpen" @click="locationPickerOpen = !locationPickerOpen"><span>{{ form.location || '请选择省份和城市' }}</span><i aria-hidden="true">⌄</i></button>
            <div v-if="locationPickerOpen" class="location-panel" role="dialog" aria-label="选择所在城市">
              <div class="location-panel-head"><strong>选择所在城市</strong><button type="button" aria-label="关闭城市选择" @click="locationPickerOpen = false">×</button></div>
              <div class="location-cascade"><div ref="provinceTabs" class="province-tabs" role="tablist" aria-label="省份"><button v-for="item in chinaCities" :key="item.province" type="button" role="tab" :aria-selected="selectedProvince === item.province" :class="{ active: selectedProvince === item.province }" @click="selectedProvince = item.province">{{ item.province }}</button></div><div class="city-options" role="listbox" :aria-label="selectedProvince + '城市'"><button v-for="city in currentCities" :key="city" type="button" role="option" :aria-selected="form.location === formatLocation(selectedProvince, city)" :class="{ active: form.location === formatLocation(selectedProvince, city) }" @click="chooseCity(city)">{{ city }}</button></div></div>
              <div class="location-panel-foot"><span>先选省份，再选城市</span><button v-if="form.location" type="button" @click="clearLocation">清空选择</button></div>
            </div>
          </div></div>
          <label class="field">工作经验<select v-model="form.experience" name="experience"><option value="">请选择工作经验</option><option v-for="option in optionsWithCurrent(experienceOptions, form.experience)" :key="option" :value="option">{{ option }}</option></select></label>
          <label class="field">当前公司<input v-model.trim="form.company" name="company" maxlength="200" placeholder="当前就职或最近一家公司" /></label>
          <label class="field">当前职位<input v-model.trim="form.currentRole" name="currentRole" maxlength="200" placeholder="例如：品牌设计师" /></label>
          <label class="field">其他昵称<input v-model.trim="form.nickname" name="nickname" maxlength="100" placeholder="平台昵称或常用称呼" /></label>
          <label class="field">人才标签<input v-model.trim="form.tags" name="tags" maxlength="500" placeholder="品牌设计，电商经验，长期储备" /></label>
        </div>
      </section>

      <section class="form-section">
        <div class="form-section-heading"><span class="section-number">03</span><h3>招聘与意向</h3></div>
        <div class="form-grid">
          <label class="field">意向岗位<select v-model="form.jobId" name="jobId" :disabled="loadingPositions"><option :value="null">暂不关联岗位 · 先放进人才库</option><option v-for="job in availablePositions" :key="job.id" :value="job.id" :disabled="isInactive(job) && job.id !== person?.jobId">{{ job.name }}{{ isInactive(job) ? '（已停招）' : '' }}</option></select></label>
          <div class="field"><span>期望月薪 <span class="muted">K / 月</span></span><div class="salary-range"><input v-model="form.salaryMin" name="salaryMin" type="number" min="0" max="100000" step="0.1" inputmode="decimal" aria-label="期望月薪最低值，单位 K" placeholder="最低" /><span aria-hidden="true">—</span><input v-model="form.salaryMax" name="salaryMax" type="number" min="0" max="100000" step="0.1" inputmode="decimal" aria-label="期望月薪最高值，单位 K" placeholder="最高" /></div></div>
        </div>
        <p v-if="selectedPosition" class="field-help">关联 {{ selectedPosition.name }}<template v-if="selectedPosition.minSalary != null && selectedPosition.maxSalary != null"> · 岗位预算 {{ selectedPosition.minSalary }} — {{ selectedPosition.maxSalary }} K / 月</template>。人才期望薪资单独记录。</p>
        <div v-if="positionError" class="inline-error" role="alert">{{ positionError }}<button type="button" class="clear-choice" @click="loadPositions">重试</button></div>
        <fieldset class="choice-field"><legend>招聘状态</legend><div class="segment-options"><label v-for="option in optionsWithCurrent(statuses, form.status)" :key="option" class="segment-option" :class="{ selected: form.status === option }"><input v-model="form.status" type="radio" name="status" :value="option" /><span>{{ option }}</span></label></div></fieldset>
        <fieldset class="choice-field"><legend>当前结果</legend><div class="segment-options"><label v-for="option in optionsWithCurrent(results, form.result)" :key="option" class="segment-option" :class="{ selected: form.result === option }"><input v-model="form.result" type="radio" name="result" :value="option" /><span>{{ option }}</span></label></div></fieldset>
        <label v-if="requiresReason || form.reason" class="field reason-field"><span>{{ requiresReason ? '未入职 / 关闭原因' : '历史结果原因' }} <span v-if="requiresReason" class="required">*</span></span><textarea v-model.trim="form.reason" name="reason" rows="3" :required="requiresReason" maxlength="3000" placeholder="记录具体原因，例如：2021 年通过面试，因薪资预期未达成一致而未入职" /></label>
        <div class="form-grid intent-grid">
          <fieldset class="choice-field"><legend>公司意愿</legend><div class="segment-options"><label v-for="option in optionsWithCurrent(companyIntentions, form.companyIntent)" :key="option" class="segment-option" :class="{ selected: form.companyIntent === option }"><input v-model="form.companyIntent" type="radio" name="companyIntent" :value="option" /><span>{{ option }}</span></label></div></fieldset>
          <fieldset class="choice-field"><legend>人才意愿</legend><div class="segment-options"><label v-for="option in optionsWithCurrent(talentIntentions, form.talentIntent)" :key="option" class="segment-option" :class="{ selected: form.talentIntent === option }"><input v-model="form.talentIntent" type="radio" name="talentIntent" :value="option" /><span>{{ option }}</span></label></div></fieldset>
        </div>
      </section>

      <section class="form-section last-section">
        <div class="form-section-heading"><span class="section-number">04</span><h3>让下一次联系有着落</h3></div>
        <div class="form-grid">
          <label class="field">下一步<input v-model.trim="form.nextStep" name="nextStep" maxlength="500" placeholder="例如：周五约一次作品沟通" /></label>
          <label class="field">下次跟进时间<input v-model="form.nextContactAt" name="nextContactAt" type="datetime-local" /></label>
        </div>
        <label class="field note-field">人才备注<textarea v-model.trim="form.remark" name="remark" rows="3" maxlength="5000" placeholder="擅长什么、合作偏好、值得记住的细节……" /></label>
      </section>
      <div v-if="error" class="inline-error form-error" role="alert">{{ error }}</div>
    </form>
    <template #footer><div class="dialog-actions"><span class="muted save-hint">{{ person?.id ? '关键变化会保留在人才时间轴' : '每一次认识，都值得被记住' }}</span><button type="button" class="btn btn-secondary" :disabled="saving" @click="visible = false">取消</button><button type="submit" form="candidate-profile-form" class="btn btn-primary" :disabled="saving || loadingPositions">{{ saving ? '正在保存…' : (person?.id ? '保存修改' : '建立人才档案') }}</button></div></template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createCandidate, updateCandidate, getDuplicates } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import { chinaCities, experienceOptions, findProvinceByLocation, formatLocation } from '@/data/chinaCities'

const props = defineProps({ modelValue: Boolean, person: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const sources = ['BOSS直聘', '猎聘', '智联招聘', '内推', '主动寻访', '历史导入', '其他']
const statuses = ['待联系', '沟通中', '面试中', 'Offer中', '已入职', '人才储备', '已关闭']
const results = ['待定', '已入职', '未入职', '候选人拒绝', '公司淘汰', '暂缓']
const companyIntentions = ['未判断', '低', '一般', '较高', '很高', '放弃']
const talentIntentions = ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']
const form = reactive({})
const positions = ref([])
const duplicates = ref([])
const duplicateError = ref('')
const error = ref('')
const positionError = ref('')
const saving = ref(false)
const loadingPositions = ref(false)
const locationPickerOpen = ref(false)
const selectedProvince = ref(chinaCities[0].province)
const provinceTabs = ref(null)
let duplicateTimer
let duplicateRequest = 0

function today() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}` }
function unwrap(response) { let value = response; if (value?.data !== undefined) value = value.data; if (value?.data !== undefined) value = value.data; return value }
function optionsWithCurrent(options, current) { return current && !options.includes(current) ? [...options, current] : options }
function isInactive(job) { return ['暂停招聘', '已关闭', '已停招', '停招', '暂停', '关闭'].includes(job.status) }
const selectedPosition = computed(() => positions.value.find(job => String(job.id) === String(form.jobId)))
const currentCities = computed(() => chinaCities.find(item => item.province === selectedProvince.value)?.cities || [])
const availablePositions = computed(() => {
  const list = [...positions.value]
  if (props.person?.jobId && !list.some(job => String(job.id) === String(props.person.jobId))) list.push({ id: props.person.jobId, name: props.person.job || '原关联岗位', status: '已停招' })
  return list
})
const requiresReason = computed(() => ['未入职', '候选人拒绝', '公司淘汰'].includes(form.result) || form.status === '已关闭')

function resetForm() {
  const person = props.person || {}
  const legacySalary = String(person.salary || '').match(/([\d.]+)\s*[-—~～至]\s*([\d.]+)/)
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, {
    name: person.name || person.nickname || '', nickname: person.nickname || '', gender: person.gender || '',
    phone: person.phone || '', wechat: person.wechat || '', email: person.email || '',
    source: person.source || '', owner: person.owner || localStorage.getItem('username') || '',
    jobId: person.jobId || null, job: person.job || '', applyTime: (person.applyTime || today()).slice(0, 10),
    salaryMin: person.salaryMin ?? (legacySalary ? Number(legacySalary[1]) : ''), salaryMax: person.salaryMax ?? (legacySalary ? Number(legacySalary[2]) : ''),
    status: person.status || '待联系', result: person.result || '待定', reason: person.reason || '',
    location: person.location || '', company: person.company || '', currentRole: person.currentRole || '',
    tags: Array.isArray(person.tags) ? person.tags.join('，') : (person.tags || ''), experience: person.experience || '', remark: person.remark || '',
    nextStep: person.nextStep || '', nextContactAt: String(person.nextContactAt || '').replace(' ', 'T').slice(0, 16),
    companyIntent: person.companyIntent || '未判断', talentIntent: person.talentIntent || '未知'
  })
  selectedProvince.value = findProvinceByLocation(form.location).province
  locationPickerOpen.value = false
  error.value = ''; duplicates.value = []; duplicateError.value = ''
}

function chooseCity(city) { form.location = formatLocation(selectedProvince.value, city); locationPickerOpen.value = false }
function clearLocation() { form.location = ''; locationPickerOpen.value = false }

async function loadPositions() {
  loadingPositions.value = true; positionError.value = ''
  try { const data = unwrap(await getPositionList()); positions.value = Array.isArray(data) ? data : data?.records || data?.items || [] }
  catch (err) { positionError.value = err.message || '岗位加载失败，可重试或暂不关联岗位建档。' }
  finally { loadingPositions.value = false }
}

function duplicateReason(item) {
  const reasons = item.matchReasons || item.reasons || item.matchReason
  return Array.isArray(reasons) ? reasons.join(' · ') : reasons || [item.phone && item.phone === form.phone ? '手机号相同' : '', item.wechat && item.wechat === form.wechat ? '微信相同' : '', item.email && item.email === form.email ? '邮箱相同' : '', item.name === form.name ? '姓名相同' : ''].filter(Boolean).join(' · ') || '联系线索相似'
}
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
watch(() => props.modelValue, open => { if (open) { resetForm(); loadPositions() } else { clearTimeout(duplicateTimer); duplicateRequest++ } }, { immediate: true })
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
watch(() => [form.name, form.phone, form.wechat, form.email], () => { clearTimeout(duplicateTimer); if (visible.value) duplicateTimer = setTimeout(checkDuplicates, 450) })
onBeforeUnmount(() => { clearTimeout(duplicateTimer); duplicateRequest++ })

async function submit() {
  if (saving.value) return
  error.value = ''
  if (!form.name?.trim() || !form.owner?.trim() || !form.source) { error.value = '请填写姓名或昵称、负责人，并选择人才来源。'; return }
  if (![form.phone, form.wechat, form.email].some(value => value?.trim())) { error.value = '请至少填写手机、微信或邮箱中的一种联系方式。'; return }
  const min = form.salaryMin === '' || form.salaryMin == null ? null : Number(form.salaryMin)
  const max = form.salaryMax === '' || form.salaryMax == null ? null : Number(form.salaryMax)
  if ((min == null) !== (max == null)) { error.value = '请同时填写期望薪资的最低值和最高值，或将两项都留空。'; return }
  if (min != null && (!Number.isFinite(min) || !Number.isFinite(max) || min < 0 || max < 0 || min > max)) { error.value = '薪资必须是有效的非负数，最低值不能高于最高值。'; return }
  if (requiresReason.value && !form.reason?.trim()) { error.value = '请记录未入职或关闭的具体原因，方便以后回顾。'; return }
  if (form.nextContactAt && !form.nextStep?.trim()) { error.value = '设置了跟进时间，请补充要完成的下一步。'; return }
  const payload = { ...form, salaryMin: min, salaryMax: max, salary: min == null ? '' : `${min}-${max}K`, jobId: form.jobId || null, job: selectedPosition.value?.name || (form.jobId ? form.job : ''), nextContactAt: form.nextContactAt ? `${form.nextContactAt}:00` : null }
  if (props.person?.version != null) payload.version = props.person.version
  saving.value = true
  try {
    const saved = unwrap(await (props.person?.id ? updateCandidate(props.person.id, payload) : createCandidate(payload)))
    ElMessage.success(props.person?.id ? '人才档案已更新' : '人才档案已建立')
    emit('saved', saved); visible.value = false
  } catch (err) { error.value = err.response?.data?.message || err.message || '保存失败，请稍后重试。' }
  finally { saving.value = false }
}
</script>

<style scoped>
.dialog-intro{margin:0 0 24px;line-height:1.8;font-size:13px}.profile-form{color:#1f2329}.form-section{padding:0 0 24px;margin:0 0 24px;border-bottom:1px solid #e5e6eb}.last-section{margin-bottom:0;padding-bottom:0;border:0}.form-section-heading{display:flex;align-items:center;gap:10px;margin-bottom:18px}.form-section-heading h3{font-size:15px;margin:0;font-weight:650;color:#1f2329}.section-number{color:#3370ff;font-size:10px;letter-spacing:1px;background:#edf3ff;border-radius:8px;padding:6px}.optional-label{font-size:11px;margin-left:auto}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 20px}.field{display:flex;flex-wrap:wrap;flex-direction:column;gap:8px;font-size:12px;font-weight:550;color:#4e5969}.field .required{color:#d65c5c}.required{color:#d65c5c}.field input,.field select,.field textarea{box-sizing:border-box;width:100%;min-width:0;border:1px solid #d9dce3;background:#f7f8fa;border-radius:12px;padding:11px 13px;color:#1f2329;font:inherit;font-size:13px;line-height:1.45;outline:none;transition:border-color .2s,box-shadow .2s,background .2s}.field input:focus,.field select:focus,.field textarea:focus{border-color:#3370ff;box-shadow:0 0 0 3px rgba(51,112,255,.12);background:#fff}.field input::placeholder,.field textarea::placeholder{color:#8f959e;font-weight:400}.field textarea{resize:vertical}.field-help{margin:11px 0 0;color:#8f959e;font-size:11px;line-height:1.8}.choice-field{border:0;margin:20px 0 0;padding:0;min-width:0}.choice-field legend{padding:0;margin-bottom:9px;color:#4e5969;font-size:12px;font-weight:550}.segment-options{display:flex;align-items:center;flex-wrap:wrap;gap:6px}.segment-option{position:relative;cursor:pointer;border:1px solid #d9dce3;background:#f7f8fa;color:#646a73;border-radius:10px;transition:background .18s,border-color .18s,color .18s;overflow:hidden}.segment-option span{display:block;padding:8px 12px;font-size:12px;line-height:1.3}.segment-option input{position:absolute;opacity:0;inset:0;cursor:pointer;margin:0;width:100%;height:100%}.segment-option:hover{background:#eef2ff}.segment-option.selected{background:#e8f0ff;border-color:#bacefd;color:#245bdb;box-shadow:0 2px 5px rgba(51,112,255,.08)}.segment-option:focus-within{outline:2px solid #8fb0ff;outline-offset:2px}.compact{margin-bottom:16px}.compact .segment-option span{padding:9px 25px}.clear-choice{border:0;background:transparent;color:#3370ff;font:inherit;font-size:12px;padding:6px;cursor:pointer}.salary-range{display:flex;align-items:center;gap:10px}.salary-range>span{font-weight:400;color:#8f959e}.salary-range input{width:calc(50% - 15px)}.reason-field,.note-field{margin-top:17px}.intent-grid .choice-field{margin-top:20px}.intent-grid .segment-option span{padding:8px 9px}.dialog-actions{display:flex;align-items:center;justify-content:flex-end;gap:10px;border-top:1px solid #e5e6eb;padding-top:18px}.save-hint{font-size:11px;margin-right:auto}.dialog-actions .btn{padding:11px 19px;border-radius:12px;font-size:12px;cursor:pointer}.duplicate-notice{margin-top:16px;padding:14px 16px;border:1px solid #ded6c5;border-radius:13px;background:#f7f4ec;color:#89764e;font-size:12px}.duplicate-notice p{font-size:11px;margin:5px 0 12px;color:#968766}.duplicate-row{display:flex;justify-content:space-between;gap:12px;margin-top:8px}.duplicate-row a{color:#766649;text-decoration:underline;text-underline-offset:3px}.duplicate-row span{font-size:11px}.inline-error{padding:12px 14px;background:#f8eeec;border:1px solid #e6d0ca;color:#9c6659;border-radius:12px;font-size:12px;line-height:1.6;margin-top:12px}.form-error{position:sticky;bottom:0;box-shadow:0 -6px 15px rgba(255,255,255,.9)}
.location-field{position:relative}.location-control{position:relative}.select-trigger{width:100%;min-height:43px;display:flex;align-items:center;justify-content:space-between;gap:12px;border:1px solid #dbe2e9;background:rgba(247,249,252,.85);border-radius:12px;padding:11px 13px;color:#99a4b1;font:inherit;font-size:13px;text-align:left}.select-trigger.selected{color:#2c3b4d}.select-trigger i{font-style:normal;color:#8896a5;transition:transform .2s}.select-trigger[aria-expanded=true] i{transform:rotate(180deg)}.select-trigger:focus-visible{border-color:#3370ff;box-shadow:0 0 0 3px rgba(51,112,255,.12);outline:0}.location-panel{position:absolute;z-index:20;top:calc(100% + 8px);left:0;width:min(610px,calc(94vw - 70px));padding:15px;background:#fff;border:1px solid #dde4ea;border-radius:15px;box-shadow:0 18px 50px rgba(31,35,41,.16)}.location-panel-head,.location-panel-foot{display:flex;align-items:center;justify-content:space-between;gap:12px}.location-panel-head{padding:0 2px 12px;border-bottom:1px solid #edf0f3}.location-panel-head strong{font-size:13px;color:#1f2329}.location-panel-head button{border:0;background:#f2f3f5;color:#646a73;width:27px;height:27px;border-radius:8px;font-size:18px}.location-cascade{display:grid;grid-template-columns:148px 1fr;height:300px;min-height:0}.province-tabs{min-height:0;overflow:auto;padding:8px 8px 8px 0;border-right:1px solid #edf0f3;display:flex;flex-direction:column;gap:3px}.province-tabs button{border:0;background:transparent;color:#646a73;text-align:left;padding:8px 10px;border-radius:8px;font-size:11px}.province-tabs button:hover,.province-tabs button.active{background:#e8f0ff;color:#245bdb;font-weight:600}.city-options{min-height:0;overflow:auto;align-content:start;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px;padding:12px}.city-options button{border:1px solid #e5e6eb;background:#f7f8fa;color:#4e5969;border-radius:9px;min-height:35px;padding:7px;font-size:11px}.city-options button:hover,.city-options button.active{background:#e8f0ff;border-color:#bacefd;color:#245bdb}.location-panel-foot{border-top:1px solid #edf0f3;padding:11px 2px 0;color:#8f959e;font-size:10px}.location-panel-foot button{border:0;background:transparent;color:#3370ff;font-size:11px}.field select{appearance:auto}
.profile-form{color:#384657}.form-section{border-bottom-color:rgba(104,124,147,.13)}.form-section-heading h3{color:#29394c}.section-number{color:#73859c;background:#edf1f6}.field,.choice-field legend{color:#566477}.field .required,.required{color:#a87566}.field input,.field select,.field textarea{border-color:#dbe2e9;background:rgba(247,249,252,.85);color:#2c3b4d}.field input:focus,.field select:focus,.field textarea:focus{border-color:#8ea6c0;box-shadow:0 0 0 3px rgba(110,144,176,.12)}.field input::placeholder,.field textarea::placeholder{color:#99a4b1}.field-help{color:#8692a1}.segment-option{border-color:#e2e7ed;background:#f5f7fa;color:#7b8796}.segment-option:hover{background:#eaf0f5}.segment-option.selected{background:#e7eef6;border-color:#aabed3;color:#47617e;box-shadow:0 2px 5px rgba(82,112,143,.06)}.segment-option:focus-within{outline-color:#829eba}.clear-choice{color:#768ba2}.salary-range>span{color:#97a3b1}.dialog-actions{border-top-color:#e9edf2}.select-trigger:focus-visible{border-color:#8ea6c0;box-shadow:0 0 0 3px rgba(110,144,176,.12)}.location-panel{box-shadow:0 18px 50px rgba(50,70,90,.18)}.location-panel-head strong{color:#405064}.location-panel-head button{background:#eef2f5;color:#7c8997}.province-tabs button{color:#7e8995}.province-tabs button:hover,.province-tabs button.active{background:#eaf0f4;color:#4e687d}.city-options button{border-color:#e5e9ed;background:#f8f9fb;color:#72808e}.city-options button:hover,.city-options button.active{background:#e7eef4;border-color:#b7c7d5;color:#4d687e}.location-panel-foot{color:#9aa4ae}.location-panel-foot button{color:#6e8598}
@media(max-width:600px){.form-grid{grid-template-columns:1fr}.dialog-intro{font-size:12px}.save-hint{display:none}.dialog-actions .btn{flex:1}.segment-option span{padding:8px 10px}.duplicate-row{flex-direction:column;gap:3px}.form-section{padding-bottom:20px;margin-bottom:20px}.location-panel{position:fixed;left:14px;right:14px;top:18vh;width:auto}.location-cascade{grid-template-columns:120px 1fr;height:280px}.city-options{grid-template-columns:repeat(2,minmax(0,1fr));padding:9px}}
</style>

