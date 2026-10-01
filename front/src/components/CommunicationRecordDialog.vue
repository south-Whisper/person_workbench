<template>
  <el-dialog v-model="visible" title="记录沟通" width="min(760px, 94vw)" class="communication-record-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="dialog-intro">记录本次沟通、双方意愿和结果；只有需要继续推进时才安排下一步。</p>
    <form class="communication-form" novalidate @submit.prevent="save">
      <section class="linked-application">
        <span>本次应聘</span>
        <strong>{{ application.company || '未设置公司' }} · {{ application.jobName || '岗位待补充' }}</strong>
        <small>岗位编号 {{ application.jobCode || String(application.jobId || '').padStart(3, '0') }}</small>
      </section>
      <div class="form-grid">
        <label class="field"><span>沟通时间 <b>必填</b></span><el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm" format="YYYY/MM/DD HH:mm" style="width:100%" /></label>
        <label class="field"><span>沟通人 <b>必填</b></span><HrPicker v-model="form.owner" :employees="employees" placeholder="选择本次沟通人" /></label>
      </div>
      <fieldset class="choice-field full"><legend>沟通方式 <b>必填</b></legend><div class="choices"><button v-for="option in channels" :key="option" type="button" :class="{ selected: form.channel === option }" @click="form.channel = option">{{ option }}</button></div></fieldset>
      <label class="field full"><span>这次沟通了什么 <b>必填</b></span><textarea v-model.trim="form.summary" rows="5" maxlength="4000" placeholder="记录对方关注的问题、已经确认的信息和重要原话" /></label>
      <div class="form-grid">
        <fieldset class="choice-field"><legend>公司意愿 <b>必填</b></legend><div class="choices"><button v-for="option in companyIntentions" :key="option" type="button" :class="{ selected: form.companyIntent === option }" @click="form.companyIntent = option">{{ option }}</button></div></fieldset>
        <fieldset class="choice-field"><legend>人才意愿 <b>必填</b></legend><div class="choices"><button v-for="option in talentIntentions" :key="option" type="button" :class="{ selected: form.talentIntent === option }" @click="form.talentIntent = option">{{ option }}</button></div></fieldset>
      </div>
      <fieldset class="choice-field full"><legend>本次结果 <b>必填</b></legend><div class="choices"><button v-for="option in results" :key="option" type="button" :class="{ selected: form.result === option }" @click="form.result = option">{{ option }}</button></div></fieldset>
      <section v-if="form.result === '安排面试'" class="inline-interview">
        <header><strong>直接安排本次面试</strong><small>保存沟通记录时会同时建立面试安排，不用再去面试页面重复填写。</small></header>
        <div class="form-grid"><label class="field"><span>面试时间 <b>必填</b></span><el-date-picker v-model="form.interviewScheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm" format="YYYY/MM/DD HH:mm" style="width:100%" /></label><fieldset class="choice-field"><legend>面试轮次 <b>必填</b></legend><div class="choices"><button v-for="option in ['初面','二面','三面','终面']" :key="option" type="button" :class="{ selected:form.interviewRound===option }" @click="form.interviewRound=option">{{option}}</button></div></fieldset></div>
        <fieldset class="choice-field"><legend>面试方式 <b>必填</b></legend><div class="choices"><button v-for="option in ['现场','视频','电话','作品测试']" :key="option" type="button" :class="{ selected:form.interviewMethod===option }" @click="form.interviewMethod=option">{{option}}</button></div></fieldset>
      </section>
      <label v-if="['暂缓', '候选人拒绝', '公司淘汰'].includes(form.result)" class="field full"><span>判断依据 / 原因 <b>必填</b></span><textarea v-model.trim="form.reason" rows="3" maxlength="2000" placeholder="写清本次决定的事实和原因，方便以后回顾" /></label>
      <div v-if="!skipsFollowUp" class="form-grid">
        <label class="field"><span>下一步</span><input v-model.trim="form.nextStep" maxlength="600" placeholder="例如：补充作品集后安排面试" /></label>
        <label class="field"><span>下次跟进时间</span><el-date-picker v-model="form.nextContactAt" type="datetime" value-format="YYYY-MM-DDTHH:mm" format="YYYY/MM/DD HH:mm" style="width:100%" /></label>
      </div>
      <section class="material-section">
        <header><div><strong>本次收到的新资料</strong><small>没有新资料可以不上传</small></div></header>
        <div class="material-grid"><label v-for="item in materialTypes" :key="item.key" :class="{ selected: files[item.key] }"><input type="file" :accept="item.accept" @change="selectFile(item.key, $event)" /><span>{{ item.icon }}</span><b>{{ item.label }}</b><small>{{ files[item.key]?.name || item.hint }}</small></label></div>
      </section>
      <p v-if="error" class="form-error">{{ error }}</p>
      <div class="dialog-actions"><button type="button" class="btn" :disabled="saving" @click="visible = false">取消</button><button class="btn btn-primary" :disabled="saving">{{ saving ? '保存中…' : '保存沟通记录' }}</button></div>
    </form>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createRecord, uploadAsset } from '@/api/candidate'
import { getHrList } from '@/api/hr'
import HrPicker from './HrPicker.vue'

const props = defineProps({ modelValue: Boolean, person: { type: Object, required: true }, application: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const saving = ref(false), error = ref(''), employees = ref([])
const files = reactive({ idPhoto: null, resume: null, other: null })
const channels = ['微信', '电话', '面谈', 'BOSS直聘', '邮件', '视频', '其他']
const companyIntentions = ['未判断', '低', '一般', '较高', '很高', '放弃']
const talentIntentions = ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']
const results = ['继续沟通', '安排面试', '暂缓', '候选人拒绝', '公司淘汰']
const skipsFollowUp = computed(() => ['安排面试', '暂缓', '候选人拒绝', '公司淘汰'].includes(form.result))
const materialTypes = [
  { key: 'idPhoto', label: '证件照', icon: '证', hint: 'JPG、PNG 或 WebP', accept: 'image/jpeg,image/png,image/webp' },
  { key: 'resume', label: '简历', icon: '历', hint: 'PDF、Word 或图片', accept: '.pdf,.doc,.docx,.png,.jpg,.jpeg,.webp' },
  { key: 'other', label: '其它资料', icon: '资', hint: '作品集、证书等', accept: '.pdf,.doc,.docx,.png,.jpg,.jpeg,.webp,.ppt,.pptx,.zip' }
]
const form = reactive({})
function localNow() { const date = new Date(); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
function reset() { const tomorrow=new Date(Date.now()+24*60*60*1000);tomorrow.setHours(10,0,0,0);Object.assign(form, { occurredAt: localNow(), channel: '微信', summary: '', companyIntent: props.application.companyIntent || '未判断', talentIntent: props.application.talentIntent || '未知', result: '继续沟通', reason: '', nextStep: props.application.nextStep || '', nextContactAt: '', owner: props.application.owner || props.person.owner || '',interviewScheduledAt:new Date(tomorrow.getTime()-tomorrow.getTimezoneOffset()*60000).toISOString().slice(0,16),interviewRound:'初面',interviewMethod:'现场' }); Object.keys(files).forEach(key => { files[key] = null }); error.value = '' }
function selectFile(key, event) { files[key] = event.target.files?.[0] || null }
watch(visible, async open => { if (!open) return; reset(); try { employees.value = await getHrList(); if (!form.owner) form.owner = (employees.value.find(item => item.current) || employees.value[0])?.name || '' } catch { error.value = 'HR 名单暂时无法读取。' } }, { immediate: true })
async function save() {
  if (saving.value) return
  if (!form.occurredAt || !form.channel || !form.summary || !form.companyIntent || !form.talentIntent || !form.result || !form.owner) { error.value = '请填写沟通时间、沟通人、方式、沟通内容、双方意愿和本次结果。'; return }
  if (['暂缓', '候选人拒绝', '公司淘汰'].includes(form.result) && !form.reason) { error.value = '请写清本次判断的事实和原因。'; return }
  if(form.result==='安排面试'&&(!form.interviewScheduledAt||!form.interviewRound||!form.interviewMethod)){error.value='请填写面试时间、轮次和方式。';return}
  const schedulesInterview = form.result === '安排面试'
  if (!skipsFollowUp.value && form.nextContactAt && !form.nextStep) { error.value = '设置了跟进时间，请同时写清下一步。'; return }
  saving.value = true; error.value = ''
  try {
    const title = `${form.channel}沟通 · ${props.application.jobName || '应聘岗位'}`
    const materialNames = materialTypes.filter(item => files[item.key]).map(item => `${item.label}：${files[item.key].name}`)
    await createRecord(props.person.id, 'communications', { ...form, nextStep: skipsFollowUp.value ? '' : form.nextStep, nextContactAt: skipsFollowUp.value ? null : form.nextContactAt, type: 'communication', title, applicationId: props.application.id, actor: form.owner, remark: materialNames.length ? `本次收到新资料：${materialNames.join('；')}` : '' })
    if(form.result==='安排面试')await createRecord(props.person.id,'interviews',{applicationId:props.application.id,scheduledAt:form.interviewScheduledAt,round:form.interviewRound,method:form.interviewMethod,status:'待面试',result:'待评价',owner:form.owner})
    const uploads = materialTypes.filter(item => files[item.key]).map(item => uploadAsset(props.person.id, files[item.key], item.label))
    const uploaded = await Promise.allSettled(uploads)
    const failed = uploaded.filter(item => item.status === 'rejected').length
    if (failed) ElMessage.warning(`沟通记录已保存，${failed} 份资料上传失败，可在人才附件中补传`)
    else ElMessage.success('沟通记录已保存')
    emit('saved'); visible.value = false
  } catch (cause) { error.value = cause.response?.data?.message || cause.message || '保存失败，请稍后重试。' }
  finally { saving.value = false }
}
</script>

<style scoped>
.dialog-intro{margin:0 0 20px;color:#7b8796;font-size:12px}.communication-form{display:grid;gap:20px}.linked-application{display:grid;grid-template-columns:auto 1fr;gap:5px 14px;padding:14px 16px;border:1px solid #dbe5f3;border-radius:12px;background:#f3f7ff}.linked-application>span{grid-row:1/3;color:#7c8da3;font-size:10px}.linked-application strong{color:#3e5570;font-size:13px}.linked-application small{color:#8b99aa;font-size:10px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.field{display:flex;flex-direction:column;gap:8px;color:#4e5969;font-size:12px}.field b,.choice-field b{color:#c75d4b;font-size:9px}.field input,.field textarea{box-sizing:border-box;width:100%;border:1px solid #dce2e9;border-radius:11px;padding:11px 13px;background:#fff;font:inherit}.field textarea{resize:vertical}.choice-field{min-width:0;margin:0;padding:0;border:0}.choice-field legend{margin-bottom:9px;color:#4e5969;font-size:12px}.choices{display:flex;flex-wrap:wrap;gap:6px}.choices button{border:1px solid #dfe4ea;border-radius:9px;padding:8px 11px;background:#fff;color:#475467;font-size:11px}.choices button.selected{border-color:#3370ff;background:#fff;color:#245bdb;box-shadow:0 0 0 2px rgba(51,112,255,.1)}.inline-interview{display:grid;gap:17px;padding:18px;border:1px solid #9fb9e5;border-radius:14px;background:#fff}.inline-interview header strong,.inline-interview header small{display:block}.inline-interview header strong{color:#173f7a;font-size:14px}.inline-interview header small{margin-top:6px;color:#667085;font-size:11px}.full{grid-column:1/-1}.material-section{padding-top:18px;border-top:1px solid #e8edf2}.material-section header strong,.material-section header small{display:block}.material-section header strong{font-size:13px}.material-section header small{margin-top:5px;color:#929daa;font-size:10px}.material-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:12px}.material-grid label{position:relative;display:grid;grid-template-columns:auto 1fr;gap:3px 9px;padding:12px;border:1px dashed #ccd7e3;border-radius:11px;background:#fff;cursor:pointer}.material-grid label.selected{border-style:solid;border-color:#3370ff;background:#fff}.material-grid input{position:absolute;inset:0;opacity:0}.material-grid label>span{grid-row:1/3;display:grid;place-items:center;width:30px;height:30px;border-radius:8px;background:#fff;border:1px solid #cbd5e1;color:#557392;font-size:10px}.material-grid b{font-size:11px}.material-grid small{overflow:hidden;color:#667085;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.form-error{margin:0;padding:11px 13px;border-radius:10px;background:#fff1ef;color:#a65e53;font-size:11px}.dialog-actions{display:flex;justify-content:flex-end;gap:9px;padding-top:18px;border-top:1px solid #e7ebf0}@media(max-width:620px){.form-grid,.material-grid{grid-template-columns:1fr}}
</style>
