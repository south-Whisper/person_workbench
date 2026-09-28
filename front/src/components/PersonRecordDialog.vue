<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="min(760px, 94vw)" class="person-record-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="record-dialog-intro">{{ descriptions[recordType] }}<span v-if="recordType === 'events'"> 可以补录任意历史日期，保留当时发生的事。</span></p>
    <form class="record-form" @submit.prevent="save">
      <div class="record-fields">
        <label v-for="field in fields" :key="field.key" class="record-field" :class="{ 'record-field-wide': field.wide }">
          <span>{{ field.label }} <b v-if="field.required" aria-label="必填">*</b></span>
          <textarea v-if="field.type === 'textarea'" v-model="form[field.key]" :placeholder="field.placeholder || `填写${field.label}`" rows="3" :maxlength="field.maxlength || 4000" :required="field.required" />
          <span v-else-if="field.type === 'choice'" class="record-choices" role="group" :aria-label="field.label">
            <button v-for="option in field.options" :key="option.value ?? option" type="button" :class="{ selected: form[field.key] === (option.value ?? option) }" :aria-pressed="form[field.key] === (option.value ?? option)" @click="form[field.key] = option.value ?? option">{{ option.label ?? option }}</button>
          </span>
          <select v-else-if="field.type === 'job'" v-model="form[field.key]" :required="field.required">
            <option value="">选择对应岗位</option>
            <option v-for="job in positions" :key="job.id" :value="job.id">{{ job.name }}{{ job.status ? ` · ${job.status}` : '' }}</option>
          </select>
          <select v-else-if="field.type === 'application'" v-model="form[field.key]" :required="field.required">
            <option value="">{{ field.required ? '选择应聘流程' : '不关联特定应聘' }}</option>
            <option v-for="application in person.applications || []" :key="application.id" :value="application.id">{{ application.jobName || jobName(application.jobId) }} · {{ (application.startedAt || application.createdAt || '').slice(0, 10) }} · {{ application.status }}</option>
          </select>
          <span v-else-if="field.type === 'salary'" class="record-salary">
            <input v-model="form.salaryMin" type="number" min="0" step="0.01" placeholder="最低金额" :required="field.required" aria-label="薪资最低金额" />
            <span aria-hidden="true">—</span>
            <input v-model="form.salaryMax" type="number" min="0" step="0.01" placeholder="最高金额" :required="field.required" aria-label="薪资最高金额" />
            <small>K / 月</small>
          </span>
          <input v-else v-model="form[field.key]" :type="field.type || 'text'" :placeholder="field.placeholder || `填写${field.label}`" :required="field.required" :min="field.min" :max="field.max" :step="field.step" :maxlength="field.maxlength || 240" />
        </label>
      </div>
      <div v-if="recordType === 'events'" class="record-link-note">填写下一步、负责人和跟进时间后，将自动生成待办任务。</div>
      <p v-if="error" class="record-error" role="alert">{{ error }}</p>
      <div class="record-dialog-actions">
        <button type="button" class="btn btn-secondary" :disabled="saving" @click="visible = false">取消</button>
        <button type="submit" class="btn btn-primary" :disabled="saving">{{ saving ? '正在保存…' : '保存记录' }}</button>
      </div>
    </form>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createRecord, updateRecord } from '@/api/candidate'

const props = defineProps({ modelValue: Boolean, recordType: { type: String, default: 'events' }, record: { type: Object, default: null }, person: { type: Object, required: true }, positions: { type: Array, default: () => [] }, initial: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const form = reactive({})
const saving = ref(false)
const error = ref('')
const labels = { events: '动态', opportunities: '主动寻访', applications: '应聘流程', interviews: '面试与评价', offers: 'Offer', compensations: '薪资事实', experiences: '经历', employments: '任职关系', collaborations: '项目合作', tasks: '跟进任务' }
const descriptions = { events: '记录沟通、结果和下一步，让每一次联系都有迹可循。', opportunities: '记录公司主动发现和接触的人才；愿意推进时再转为正式应聘。', applications: '每一次应聘独立保存，历史岗位版本与未入职原因会持续保留。', interviews: '记录安排、实际面试结果与评价依据。', offers: '条件变化请新增版本，历史 Offer 始终保留。这里仅记录方案与响应，不会发送给人才。', compensations: '分别记录期望、报价、还价与实际薪资，金额统一按 K / 月记录。', experiences: '补充教育、工作、项目和证书经历，丰富人才画像。', employments: '每一段任职独立保存，结束时请记录真实原因。', collaborations: '沉淀合作角色、金额、交付表现与再次合作建议。', tasks: '明确下一步、负责人和截止时间，持续跟进这位人才。' }
const dialogTitle = computed(() => props.record?.id ? `更新${labels[props.recordType] || '记录'}` : `新增${labels[props.recordType] || '记录'}`)
const choice = (key, label, options, required = false) => ({ key, label, type: 'choice', options, required, wide: true })
const text = (key, label, required = false, extra = {}) => ({ key, label, required, ...extra })
const area = (key, label, required = false, placeholder = '') => ({ key, label, required, placeholder, type: 'textarea', wide: true })
const date = (key, label, required = false, time = false) => ({ key, label, required, type: time ? 'datetime-local' : 'date' })
const application = (required = false) => ({ key: 'applicationId', label: '关联应聘', type: 'application', required })
const job = { key: 'jobId', label: '关联岗位', type: 'job', required: true }
const salary = { key: 'salary', label: '薪资范围', type: 'salary', wide: true, required: true }
const fields = computed(() => {
  const schemas = {
    opportunities: [text('title', '寻访机会标题', true, { wide: true, placeholder: '例如：主动接触资深摄影师' }), { ...job, required: false }, date('startedAt', '首次接触日期', true), text('owner', '负责人', true), choice('status', '当前状态', ['新发现', '待联系', '已联系', '有效沟通', '持续培育', '暂不考虑', '对方拒绝', '公司放弃', '重新激活'], true), choice('companyIntent', '公司意愿', ['未判断', '低', '一般', '较高', '很高', '放弃']), choice('talentIntent', '人才意愿', ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']), area('summary', '发现背景与沟通摘要'), area('reason', '暂缓 / 拒绝 / 放弃原因'), text('nextStep', '下一步'), date('nextContactAt', '下次跟进时间', false, true)],
    events: [choice('type', '动态类型', [{ value: 'communication', label: '沟通' }, { value: 'discovery', label: '发现人才' }, { value: 'interview', label: '面试' }, { value: 'negotiation', label: '谈薪' }, { value: 'offer', label: 'Offer' }, { value: 'onboarding', label: '入职' }, { value: 'probation', label: '转正' }, { value: 'transfer', label: '调岗' }, { value: 'promotion', label: '晋升' }, { value: 'salary', label: '调薪' }, { value: 'departure', label: '离职' }, { value: 'rehire', label: '返聘' }, { value: 'project', label: '项目合作' }, { value: 'other', label: '其他' }], true), date('occurredAt', '发生时间', true, true), text('actor', '记录人', true), choice('channel', '沟通渠道', ['微信', '电话', '面谈', 'BOSS直聘', '邮件', '视频', '其他']), application(), text('title', '动态标题', true, { placeholder: '例如：2021 年初面 · 因通勤距离放弃' }), area('summary', '发生了什么', true), choice('result', '本次结果', ['有效沟通', '持续跟进', '安排面试', '通过', '未通过', '已发送Offer', '接受Offer', '拒绝Offer', '候选人退出', '已入职', '暂不考虑', '无响应', '已完成'], true), area('reason', '原因与判断依据', false, '未通过、拒绝、退出或离职时，请写清原因'), text('nextStep', '下一步', false, { placeholder: '例如：约定下周再次沟通' }), text('owner', '跟进负责人'), date('nextContactAt', '下次跟进时间', false, true)],
    applications: [job, date('startedAt', '应聘开始日期', true), text('owner', '招聘负责人', true), choice('status', '应聘状态', ['已创建', '初筛', '沟通', '面试安排', '面试中', '内部决策', '谈薪', 'Offer', '待入职', '已入职', '候选人退出', '公司淘汰', '岗位暂停', '岗位取消', '人才储备', '长期无响应'], true), choice('companyIntent', '公司意愿', ['未判断', '低', '一般', '较高', '很高', '放弃']), choice('talentIntent', '人才意愿', ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']), area('reason', '结果 / 关闭原因', false, '例如：2021 年面试通过，但候选人因家庭原因未入职'), text('nextStep', '下一步'), date('nextContactAt', '下次跟进时间', false, true)],
    interviews: [application(true), date('scheduledAt', '面试时间', true, true), text('round', '轮次', true, { placeholder: '例如：初面 / 二面 / 终面' }), text('interviewer', '面试官', true), choice('method', '面试方式', ['现场', '视频', '电话', '作品测试']), choice('status', '面试状态', ['待面试', '已完成', '已改期', '已取消', '未出席'], true), text('score', '综合评分（1—5）', false, { type: 'number', min: 1, max: 5, step: 0.5 }), choice('result', '推荐结论', ['待评价', '强烈推荐', '推荐', '保留', '不推荐']), area('feedback', '事实、评价与依据', false, '记录具体表现、优势、风险和判断依据'), area('reason', '改期 / 取消 / 未出席原因')],
    offers: props.record?.id ? [choice('status', 'Offer 状态', ['草稿', '审批中', '已批准', '已发送', '已查看', '协商中', '已接受', '已拒绝', '已过期', '已撤回', '已入职'], true), area('reason', '拒绝 / 撤回 / 未入职原因'), area('remark', '响应说明')] : [application(true), salary, date('expectedStartDate', '计划入职日期', true), date('expiresAt', '有效期至', true), choice('status', 'Offer 状态', ['草稿', '审批中', '已批准', '已发送', '协商中', '已接受', '已拒绝'], true), area('reason', '版本变更 / 拒绝原因'), area('remark', '录用条件与说明', false, '例如：13 薪，试用期 3 个月，绩效及补贴说明')],
    compensations: [choice('type', '薪资事实类型', ['候选人公开期望', '候选人当前收入', '沟通后期望', '公司内部预算', '公司本轮报价', '候选人还价', '外部Offer', '最终Offer', '实际入职薪资', '转正调薪', '年度调薪', '晋升调薪', '离职时薪资'], true), salary, date('occurredAt', '生效 / 沟通时间', true, true), text('source', '信息来源', true, { placeholder: '例如：候选人口述、合同、面谈' }), application(), area('remark', '结构与备注', false, '说明税前 / 税后、绩效、年终奖及其他待遇')],
    experiences: [choice('type', '经历类型', ['工作', '教育', '项目', '证书', '作品'], true), text('organization', '公司 / 学校 / 机构', true), text('title', '职位 / 专业 / 作品名称', true), date('startDate', '开始日期', true), date('endDate', '结束日期（留空为至今）'), area('description', '经历描述', true)],
    employments: [job, date('startDate', '入职日期', true), date('endDate', '离职日期'), choice('status', '任职状态', ['待入职', '试用期', '在职', '已离职'], true), area('reason', '离职 / 变动原因'), area('remark', '任职说明')],
    collaborations: [text('projectName', '项目名称', true), text('role', '合作角色', true), date('startDate', '开始日期', true), date('endDate', '结束日期'), text('amount', '合作金额（元）', false, { type: 'number', min: 0, step: 0.01 }), choice('status', '合作状态', ['待启动', '进行中', '已完成', '已终止'], true), area('feedback', '交付与合作评价', false, '记录交付质量、准时性、配合度、客户反馈及再合作建议')],
    tasks: [text('title', '下一步要做什么', true, { wide: true }), text('owner', '负责人', true), date('dueAt', '截止时间', true, true), choice('status', '任务状态', ['待处理', '进行中', '已完成', '已取消'], true), area('result', '完成结果 / 说明')]
  }
  return schemas[props.recordType] || []
})

const nowLocal = () => { const date = new Date(); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
const jobName = id => props.positions.find(job => String(job.id) === String(id))?.name || '未命名岗位'
watch(() => props.modelValue, value => {
  if (!value) return
  Object.keys(form).forEach(key => delete form[key])
  const now = nowLocal()
  const defaults = {
    events: { type: 'communication', occurredAt: now, channel: '微信', result: '有效沟通', actor: props.person.owner || '', owner: props.person.owner || '' },
    opportunities: { startedAt: now.slice(0, 10), status: '新发现', companyIntent: '未判断', talentIntent: '未知', owner: props.person.owner || '' },
    applications: { jobId: props.person.jobId || '', startedAt: now.slice(0, 10), status: '已创建', companyIntent: '未判断', talentIntent: '未知', owner: props.person.owner || '' },
    interviews: { scheduledAt: now, round: '初面', method: '现场', status: '待面试', result: '待评价' },
    offers: { status: '草稿', salaryMin: props.person.salaryMin ?? '', salaryMax: props.person.salaryMax ?? '' },
    compensations: { type: '沟通后期望', occurredAt: now, currency: 'CNY', period: '月' },
    experiences: { type: '工作', startDate: now.slice(0, 10) },
    employments: { jobId: props.person.jobId || '', startDate: now.slice(0, 10), status: '试用期' },
    collaborations: { startDate: now.slice(0, 10), status: '待启动' },
    tasks: { status: '待处理', owner: props.person.owner || '' }
  }
  Object.assign(form, defaults[props.recordType] || {}, props.initial || {}, props.record || {})
  fields.value.forEach(field => {
    if ((field.type === 'datetime-local' || field.type === 'date') && form[field.key]) {
      const value = String(form[field.key])
      form[field.key] = field.type === 'date' ? value.slice(0, 10) : value.slice(0, 16)
    }
  })
  if (!form.applicationId && ['offers', 'interviews'].includes(props.recordType) && props.person.applications?.length === 1) form.applicationId = props.person.applications[0].id
  error.value = ''
})

async function save() {
  if (saving.value) return
  error.value = ''
  for (const field of fields.value) {
    if (field.required && field.type !== 'salary' && (form[field.key] === undefined || form[field.key] === null || String(form[field.key]).trim() === '')) { error.value = `请填写${field.label}`; return }
  }
  if (fields.value.some(field => field.type === 'salary')) {
    if (form.salaryMin === '' || form.salaryMax === '' || form.salaryMin == null || form.salaryMax == null || !Number.isFinite(Number(form.salaryMin)) || !Number.isFinite(Number(form.salaryMax)) || Number(form.salaryMin) < 0 || Number(form.salaryMax) < Number(form.salaryMin)) { error.value = '请填写有效的薪资范围，最高金额应不低于最低金额'; return }
  }
  if (form.startDate && form.endDate && form.endDate < form.startDate) { error.value = '结束日期不能早于开始日期'; return }
  const needsReason = ['已拒绝', '已撤回', '候选人退出', '公司淘汰', '岗位取消', '已离职'].includes(form.status) || ['未通过', '拒绝Offer', '候选人退出'].includes(form.result) || form.type === 'departure'
  if (needsReason && !String(form.reason || '').trim()) { error.value = '请填写原因，让未来的复盘有据可查'; return }
  if (props.recordType === 'interviews' && form.status === '已完成' && (!String(form.feedback || '').trim() || !form.result || form.result === '待评价')) { error.value = '完成面试前，请填写评价依据与推荐结论'; return }
  if (props.recordType === 'interviews' && ['已取消', '未出席', '已改期'].includes(form.status) && !String(form.reason || '').trim()) { error.value = '请记录改期、取消或未出席的原因'; return }
  if (props.recordType === 'employments' && form.status === '已离职' && !form.endDate) { error.value = '请填写离职日期'; return }
  if (props.recordType === 'events' && (form.nextStep || form.nextContactAt) && (!String(form.nextStep || '').trim() || !form.nextContactAt || !String(form.owner || '').trim())) { error.value = '创建跟进任务需要同时填写下一步、负责人和跟进时间'; return }
  const payload = { ...form }
  for (const key of ['salaryMin', 'salaryMax', 'amount', 'score']) {
    if (payload[key] !== undefined && payload[key] !== '' && payload[key] !== null) payload[key] = Number(payload[key])
    else delete payload[key]
  }
  try {
    saving.value = true
    if (props.record?.id) await updateRecord(props.person.id, props.recordType, props.record.id, payload)
    else { delete payload.id; delete payload.revision; delete payload.version; await createRecord(props.person.id, props.recordType, payload) }
    ElMessage.success('记录已保存')
    visible.value = false
    emit('saved')
  } catch (cause) { error.value = cause.response?.data?.message || cause.message || '保存失败，请稍后重试' }
  finally { saving.value = false }
}
</script>

<style scoped>
.record-dialog-intro{color:#7b8190;line-height:1.8;font-size:13px;margin:0 0 24px}.record-fields{display:grid;grid-template-columns:1fr 1fr;gap:20px}.record-field{display:flex;flex-direction:column;gap:9px;min-width:0;color:#4d5563;font-size:13px;font-weight:550}.record-field>b,.record-field>span>b{color:#9d716c;font-weight:500}.record-field-wide{grid-column:1/-1}.record-field input,.record-field select,.record-field textarea{width:100%;box-sizing:border-box;min-height:43px;border:1px solid #e0e4e9;border-radius:11px;padding:10px 12px;background:#f8f9fb;color:#313947;font:inherit;outline:none;transition:border-color .2s,box-shadow .2s}.record-field input:focus,.record-field select:focus,.record-field textarea:focus{border-color:#92a3b9;box-shadow:0 0 0 3px #7b8fa614}.record-field textarea{resize:vertical;line-height:1.7}.record-choices{display:flex;flex-wrap:wrap;gap:7px}.record-choices button{border:1px solid #e2e5e9;color:#777e8a;background:#fafbfc;border-radius:9px;min-height:35px;padding:7px 12px;font:inherit;font-size:12px;cursor:pointer;transition:all .2s}.record-choices button:hover{background:#edf0f3}.record-choices button.selected{background:#e5ebf1;color:#405569;border-color:#bcc9d6;box-shadow:0 2px 4px #26354607}.record-salary{display:grid;grid-template-columns:1fr 20px 1fr auto;align-items:center;gap:8px}.record-salary>span{text-align:center;color:#8c929b}.record-salary small{white-space:nowrap;font-size:11px;color:#8c929b}.record-link-note{margin-top:22px;padding:12px 15px;color:#677b70;background:#edf2ee;border-radius:10px;font-size:12px}.record-error{background:#f7ecea;color:#985d53;border-radius:10px;padding:12px 15px;line-height:1.6}.record-dialog-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:28px;border-top:1px solid #edf0f2;padding-top:20px}@media(max-width:580px){.record-fields{grid-template-columns:1fr}.record-salary{grid-template-columns:1fr 14px 1fr}.record-salary small{grid-column:1/-1}}
</style>
