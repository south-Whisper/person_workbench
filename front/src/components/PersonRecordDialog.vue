<template>
  <el-dialog v-model="visible" :title="dialogTitle" :width="recordType === 'offers' ? 'min(1120px, 96vw)' : 'min(760px, 94vw)'" class="person-record-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close>
    <p class="record-dialog-intro">{{ dialogDescription }}</p>
    <form class="record-form" :class="{ 'offer-form-layout': recordType === 'offers' }" novalidate @submit.prevent="save(false)">
      <fieldset class="record-fields" :disabled="readOnlyOffer">
        <label v-for="field in fields" :key="field.key" class="record-field" :class="{ 'record-field-wide': field.wide, 'record-field-missing': attempted && field.required && isMissing(field) }">
          <span>{{ field.label }} <b v-if="field.required && !field.hideRequired" aria-label="必填">必填</b></span>
          <textarea v-if="field.type === 'textarea'" v-model="form[field.key]" :placeholder="field.placeholder || `填写${field.label}`" rows="3" :maxlength="field.maxlength || 4000" :required="field.required" />
          <span v-else-if="field.type === 'choice'" class="record-choices" role="group" :aria-label="field.label">
            <button v-for="option in field.options" :key="option.value ?? option" type="button" :class="{ selected: form[field.key] === (option.value ?? option) }" :aria-pressed="form[field.key] === (option.value ?? option)" @click="form[field.key] = option.value ?? option">{{ option.label ?? option }}</button>
          </span>
          <div v-else-if="field.type === 'job' && recordType === 'applications'" class="application-reference" :class="{ empty: !selectedPosition }">
            <template v-if="selectedPosition">
              <strong>{{ selectedPosition.company || '未设置公司' }} · {{ selectedPosition.name }}</strong>
              <small>岗位编号 {{ selectedPosition.recruitmentCode || String(selectedPosition.id || '').padStart(3, '0') }} · {{ selectedPosition.status || '状态待补充' }}</small>
              <em>系统已按该人才档案中的岗位自动带入，仅供查看</em>
            </template>
            <template v-else>
              <strong>人才档案还没有对应岗位</strong>
              <small>请先在人才档案中设置岗位，再建立应聘流程。</small>
            </template>
          </div>
          <select v-else-if="field.type === 'job'" v-model="form[field.key]" :required="field.required">
            <option value="">选择对应岗位</option>
          <option v-for="job in positions" :key="job.id" :value="job.id">{{ job.company || '未设置公司' }}：{{ job.name }}：{{ job.recruitmentCode || String(job.id).padStart(3, '0') }} · 已招 {{ job.hiredCount || 0 }}/{{ job.headcount || 1 }}{{ job.status ? ` · ${job.status}` : '' }}</option>
          </select>
          <div v-else-if="field.type === 'application' && ['offers', 'interviews'].includes(recordType)" class="application-reference" :class="{ empty: !selectedApplication }">
            <template v-if="selectedApplication">
              <strong>{{ selectedApplication.company || selectedPosition?.company || '未设置公司' }} · {{ selectedApplication.jobName || selectedPosition?.name || jobName(selectedApplication.jobId) }}</strong>
              <small>岗位编号 {{ selectedApplication.jobCode || selectedPosition?.recruitmentCode || String(selectedApplication.jobId || '').padStart(3, '0') }}</small>
            </template>
            <template v-else>
              <strong>暂时找不到可关联的应聘岗位</strong>
              <small>请先为该人才建立应聘流程，再回来新增记录。</small>
            </template>
          </div>
          <select v-else-if="field.type === 'application'" v-model="form[field.key]" :required="field.required">
            <option value="">{{ field.required ? '选择应聘流程' : '不关联特定应聘' }}</option>
            <option v-for="item in person.applications || []" :key="item.id" :value="item.id">{{ item.company || '未设置公司' }}：{{ item.jobName || jobName(item.jobId) }}：{{ item.jobCode || String(item.jobId || '').padStart(3, '0') }} · {{ item.status }}</option>
          </select>
          <HrPicker v-else-if="field.type === 'owner'" v-model="form[field.key]" :employees="employees" :placeholder="field.label === '面试官' ? '输入姓名搜索面试官' : '输入姓名模糊搜索 HR'" />
          <div v-else-if="field.type === 'interview-context'" class="interview-context">
            <strong>{{ record?.round || '面试' }} · {{ record?.method || '方式待补充' }}</strong>
            <small>{{ formatInterviewTime(record?.scheduledAt) }} · 面试官 {{ record?.owner || '待安排' }}</small>
          </div>
          <div v-else-if="field.type === 'budget'" class="budget-reference"><strong>{{ positionBudgetText }}</strong><small>{{ selectedPosition ? `${selectedPosition.company || '未设置公司'}：${selectedPosition.name}` : '请先选择关联应聘' }}</small></div>
          <div v-else-if="field.type === 'annual-preview'" class="annual-salary-preview"><small>按月薪与年薪月数自动计算</small><strong>{{ annualSalaryText }}</strong></div>
          <span v-else-if="field.type === 'salary'" class="record-salary">
            <input v-model="form.salaryMin" type="number" min="0" step="0.01" placeholder="最低金额" :required="field.required" aria-label="薪资最低金额" />
            <span aria-hidden="true">—</span>
            <input v-model="form.salaryMax" type="number" min="0" step="0.01" placeholder="最高金额" :required="field.required" aria-label="薪资最高金额" />
            <small>K / 月</small>
          </span>
          <el-date-picker v-else-if="field.type === 'date-picker'" v-model="form[field.key]" :type="field.withTime ? 'datetime' : 'date'" :value-format="field.withTime ? 'YYYY-MM-DDTHH:mm' : 'YYYY-MM-DD'" :format="field.withTime ? 'YYYY/MM/DD HH:mm' : 'YYYY/MM/DD'" :disabled-date="field.futureOnly ? disablePastDate : undefined" :placeholder="`选择${field.label}`" style="width:100%" />
          <input v-else v-model="form[field.key]" :type="field.type || 'text'" :placeholder="field.placeholder || `填写${field.label}`" :required="field.required" :min="field.min" :max="field.max" :step="field.step" :maxlength="field.maxlength || 240" />
          <small v-if="field.key === 'validityDays'" class="auto-note">有效期至 {{ offerDisplayExpiry }}</small>
        </label>
      </fieldset>
      <aside v-if="recordType === 'offers'" class="offer-live-preview" aria-label="Offer 预览"><div class="offer-preview-paper"><header><BrandLockup icon-only compact /><div><small>OFFER LETTER PREVIEW</small><strong>Offer 预览</strong></div></header><div class="offer-preview-person"><small>候选人</small><h3>{{ person.name }}</h3><p>{{ selectedPosition?.company || '公司待确认' }} · {{ selectedPosition?.name || '岗位待确认' }}</p></div><dl><div><dt>发送邮箱</dt><dd>{{ form.recipientEmail || '待填写' }}</dd></div><div v-if="readOnlyOffer"><dt>发送日期</dt><dd>{{ String(form.sentAt || '未记录').replace('T', ' ').slice(0, 16) }}</dd></div><div v-if="readOnlyOffer"><dt>候选人回复</dt><dd>{{ form.responseStatus || '待回复' }}{{ form.respondedAt ? ` · ${String(form.respondedAt).replace('T', ' ').slice(0, 16)}` : '' }}</dd></div><div><dt>薪资方案</dt><dd>{{ offerSalaryPreview }}</dd></div><div><dt>岗位预算</dt><dd>{{ positionBudgetText }}</dd></div><div><dt>试用期</dt><dd>{{ Number(form.probationMonths) ? `${form.probationMonths} 个月` : '无试用期' }}</dd></div><div><dt>社保与公积金</dt><dd>{{ form.socialInsurance || '待选择' }}</dd></div><div><dt>计划入职</dt><dd>{{ form.expectedStartDate || '待选择' }}</dd></div><div><dt>Offer 有效期</dt><dd>{{ offerDisplayExpiry }}</dd></div><div><dt>HR</dt><dd>{{ form.owner || '待安排' }}</dd></div></dl><p v-if="form.responseReason" class="offer-preview-remark">回复说明：{{ form.responseReason }}</p><p v-else class="offer-preview-remark">{{ form.remark || '补充说明会显示在这里。' }}</p><footer>SetHub 人才管理 · {{ readOnlyOffer ? '已完成版本，只读留档' : '内容随左侧填写实时更新' }}</footer></div></aside>
      <p v-if="error" class="record-error" role="alert">{{ error }}</p>
      <div class="record-dialog-actions">
        <button v-if="!readOnlyOffer" type="button" class="btn btn-secondary" :disabled="saving" @click="visible = false">取消</button>
        <button v-if="readOnlyOffer" type="button" class="btn btn-primary" @click="exportOffer">导出 Offer</button>
        <button v-if="!readOnlyOffer" type="submit" class="btn" :disabled="saving">{{ saving ? '正在保存…' : saveButtonLabel }}</button>
        <button v-if="recordType === 'offers' && !readOnlyOffer" type="button" class="btn btn-primary" :disabled="saving" @click="confirmOfferIssue">{{ saving ? '正在处理…' : '确认并发送 Offer' }}</button>
      </div>
    </form>
  </el-dialog>
  <el-dialog v-model="offerConfirmOpen" width="min(520px, 92vw)" class="offer-confirm-dialog" append-to-body align-center :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <div class="offer-confirm-card">
      <span class="offer-confirm-icon" aria-hidden="true">✓</span>
      <div>
        <small>发送前最终确认</small>
        <h2>确认发送这份 Offer？</h2>
        <p>“{{ selectedPosition?.name || '当前岗位' }}”的 Offer 将发送到：</p>
        <strong>{{ form.recipientEmail }}</strong>
        <p class="offer-confirm-note">发送后本版本会锁定。邮件内提供“接受 Offer”和“拒绝 Offer”两个按钮，候选人点击一次即可提交结果。</p>
      </div>
    </div>
    <template #footer>
      <div class="offer-confirm-actions">
        <button type="button" class="btn btn-secondary" :disabled="saving" @click="offerConfirmOpen = false">返回检查</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="issueConfirmedOffer">{{ saving ? '正在发送…' : '确认发送' }}</button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createRecord, updateRecord } from '@/api/candidate'
import { getHrList } from '@/api/hr'
import HrPicker from '@/components/HrPicker.vue'
import BrandLockup from '@/components/BrandLockup.vue'

const props = defineProps({ modelValue: Boolean, recordType: { type: String, default: 'events' }, mode: { type: String, default: 'schedule' }, record: { type: Object, default: null }, person: { type: Object, required: true }, positions: { type: Array, default: () => [] }, initial: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const form = reactive({})
const saving = ref(false)
const offerConfirmOpen = ref(false)
const attempted = ref(false)
const error = ref('')
const employees = ref([])
const readOnlyOffer = computed(() => props.recordType === 'offers' && props.record?.status === '已完成')
const labels = { opportunities: '主动寻访', applications: '应聘流程', interviews: '面试', offers: 'Offer', compensations: '薪资事实', experiences: '经历', employments: '任职关系', collaborations: '项目合作' }
const descriptions = { opportunities: '记录公司主动发现和接触的人才。', applications: '记录本次应聘的岗位与当前阶段。', interviews: '安排面试时间、轮次、面试官和方式。', offers: '记录最终谈定的录用条件。', compensations: '记录薪资变化，金额统一按 K / 月填写。', experiences: '补充教育、工作、项目和证书经历。', employments: '记录入职、转正与离职。', collaborations: '记录合作情况与评价。' }
const dialogTitle = computed(() => readOnlyOffer.value ? 'Offer 详情' : props.recordType === 'interviews' ? (props.mode === 'evaluation' ? '填写面试结果' : (props.record?.id ? '更新面试安排' : '安排面试')) : (props.record?.id ? `更新${labels[props.recordType] || '记录'}` : `新增${labels[props.recordType] || '记录'}`))
const dialogDescription = computed(() => readOnlyOffer.value ? '这份 Offer 已完成并锁定，可以查看和导出，不能再修改。' : props.recordType === 'interviews' && props.mode === 'evaluation' ? '面试安排已确定，这里只记录评分、推荐结论和评价依据。' : descriptions[props.recordType])
const saveButtonLabel = computed(() => props.recordType === 'offers' ? '保存草稿' : props.recordType === 'interviews' ? (props.mode === 'evaluation' ? '保存面试结果' : '保存面试安排') : '保存记录')
const choice = (key, label, options, required = false) => ({ key, label, type: 'choice', options, required, wide: true })
const text = (key, label, required = false, extra = {}) => ({ key, label, required, ...extra })
const area = (key, label, required = false, placeholder = '') => ({ key, label, required, placeholder, type: 'textarea', wide: true })
const date = (key, label, required = false, time = false, futureOnly = false) => ({ key, label, required, type: 'date-picker', withTime: time, futureOnly })
const owner = (label = 'HR', required = false) => ({ key: 'owner', label, required, type: 'owner' })
const application = (required = false) => ({ key: 'applicationId', label: '对应应聘岗位', type: 'application', required, hideRequired: true })
const job = { key: 'jobId', label: '关联岗位', type: 'job', required: true }
const salary = { key: 'salary', label: '薪资范围', type: 'salary', wide: true, required: true }
const fields = computed(() => {
  const schemas = {
    opportunities: [text('title', '寻访机会标题', true, { wide: true, placeholder: '例如：主动接触资深摄影师' }), { ...job, required: false }, date('startedAt', '首次接触日期', true), owner('HR', true), choice('status', '当前状态', ['新发现', '待联系', '已联系', '有效沟通', '持续培育', '暂不考虑', '对方拒绝', '公司放弃', '重新激活'], true), choice('companyIntent', '公司意愿', ['未判断', '低', '一般', '较高', '很高', '放弃']), choice('talentIntent', '人才意愿', ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']), area('summary', '发现背景与沟通摘要'), area('reason', '暂缓 / 拒绝 / 放弃原因'), text('nextStep', '下一步'), date('nextContactAt', '下次跟进时间', false, true)],
    applications: [job, date('startedAt', '应聘开始日期', true), owner('HR', true), choice('status', '应聘状态', ['沟通中', '面试中', 'Offer中', '已入职', '候选人退出', '公司淘汰', '人才储备', '长期无响应'], true), choice('companyIntent', '公司意愿', ['未判断', '低', '一般', '较高', '很高', '放弃']), choice('talentIntent', '人才意愿', ['未知', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈']), area('reason', '结果 / 关闭原因', false, '例如：2021 年面试通过，但候选人因家庭原因没有继续推进'), text('nextStep', '下一步'), date('nextContactAt', '下次跟进时间', false, true)],
    interviews: props.mode === 'evaluation'
      ? [{ key: 'interviewContext', label: '对应面试安排', type: 'interview-context', wide: true }, text('score', '综合评分（1—5）', true, { type: 'number', min: 1, max: 5, step: 0.5 }), choice('result', '推荐结论', ['强烈推荐', '推荐', '保留', '不推荐'], true), area('feedback', '事实、评价与依据', true, '记录具体表现、优势、风险和判断依据')]
      : [application(true), date('scheduledAt', '面试时间', true, true, true), choice('round', '轮次', ['初面', '二面', '三面', '终面'], true), owner('面试官', true), choice('method', '面试方式', ['现场', '视频', '电话', '作品测试']), choice('status', '面试状态', ['待面试', '已改期', '已取消', '未出席'], true), area('reason', '改期 / 取消 / 未出席原因')],
    offers: [application(true), text('recipientEmail', '发送邮箱', true, { type: 'email', wide: true, placeholder: '用于接收 Offer 的邮箱' }), ...(readOnlyOffer.value ? [text('sentAt', 'Offer 发送日期', false, { wide: true })] : []), { key: 'positionBudget', label: '岗位期望预算', type: 'budget', wide: true }, choice('salaryMode', '薪资填写方式', [{ value: 'monthly', label: '按月薪填写' }, { value: 'annual', label: '按年薪填写' }], true), ...(form.salaryMode === 'annual' ? [text('annualSalary', '谈定年薪（K / 年）', true, { type: 'number', min: 0, step: 0.01, wide: true })] : [text('actualSalary', '谈定月薪（K / 月）', true, { type: 'number', min: 0, step: 0.01 }), { key: 'annualPreview', label: '折合年薪', type: 'annual-preview' }, choice('salaryMonths', '年薪月数', [12, 13, 14, 15, 16, 17, 18], true)]), choice('probationMonths', '试用期', [{ value: 0, label: '无试用期' }, { value: 1, label: '1个月' }, { value: 2, label: '2个月' }, { value: 3, label: '3个月' }, { value: 4, label: '4个月' }, { value: 5, label: '5个月' }, { value: 6, label: '6个月' }], true), choice('socialInsurance', '社保与公积金', ['五险一金', '六险一金', '五险', '商业保险', '无社保'], true), date('expectedStartDate', '计划入职日期', true, false, true), choice('validityDays', 'Offer 有效期', [{ value: 7, label: '7 天' }, { value: 15, label: '15 天' }], true), owner('HR', true), area('remark', '补充说明')],
    compensations: [choice('type', '薪资事实类型', ['候选人公开期望', '候选人当前收入', '沟通后期望', '公司内部预算', '公司本轮报价', '候选人还价', '外部Offer', '最终Offer', '实际入职薪资', '转正调薪', '年度调薪', '晋升调薪', '离职时薪资'], true), salary, date('occurredAt', '生效 / 沟通时间', true, true), text('source', '信息来源', true, { placeholder: '例如：候选人口述、合同、面谈' }), application(), area('remark', '结构与备注', false, '说明税前 / 税后、绩效、年终奖及其他待遇')],
    experiences: [choice('type', '经历类型', ['工作', '教育', '项目', '证书', '作品'], true), text('organization', '公司 / 学校 / 机构', true), text('title', '职位 / 专业 / 作品名称', true), date('startDate', '开始日期', true), date('endDate', '结束日期（留空为至今）'), area('description', '经历描述', true)],
    employments: [job, date('startDate', '入职日期', true), date('endDate', '离职日期'), choice('status', '任职状态', ['试用期', '在职', '已转正', '已离职'], true), area('reason', '离职 / 变动原因'), area('remark', '任职说明')],
    collaborations: [text('projectName', '项目名称', true), text('role', '合作角色', true), date('startDate', '开始日期', true), date('endDate', '结束日期'), text('amount', '合作金额（元）', false, { type: 'number', min: 0, step: 0.01 }), choice('status', '合作状态', ['待启动', '进行中', '已完成', '已终止'], true), area('feedback', '交付与合作评价', false, '记录交付质量、准时性、配合度、客户反馈及再合作建议')]
  }
  return schemas[props.recordType] || []
})

const nowLocal = () => { const date = new Date(); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
const todayLocal = () => nowLocal().slice(0, 10)
const formatInterviewTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '时间待补充'
const disablePastDate = value => value < new Date(`${todayLocal()}T00:00:00`)
const jobName = id => props.positions.find(job => String(job.id) === String(id))?.name || '未命名岗位'
const selectedApplication = computed(() => (props.person.applications || []).find(item => String(item.id) === String(form.applicationId)))
const selectedPosition = computed(() => props.positions.find(job => String(job.id) === String(selectedApplication.value?.jobId || form.jobId || props.record?.jobId || props.person.jobId)))
const positionBudgetText = computed(() => selectedPosition.value?.minSalary != null && selectedPosition.value?.maxSalary != null ? `${selectedPosition.value.minSalary} — ${selectedPosition.value.maxSalary} K / 月` : '岗位预算待补充')
const annualSalaryText = computed(() => {
  const monthly = Number(form.actualSalary)
  const months = Number(form.salaryMonths)
  if (!Number.isFinite(monthly) || monthly <= 0 || !Number.isFinite(months)) return '等待填写月薪'
  return `${Number((monthly * months).toFixed(2))} K / 年`
})
const offerSalaryPreview = computed(() => form.salaryMode === 'annual' ? `${form.annualSalary || '—'} K / 年` : `${form.actualSalary || '—'} K / 月 × ${form.salaryMonths || '—'} 薪（${annualSalaryText.value}）`)
const addDays = (dateText, days) => {
  const date = new Date(`${dateText}T12:00:00`)
  date.setDate(date.getDate() + Number(days || 0))
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
const offerExpiryDate = computed(() => addDays(todayLocal(), Number(form.validityDays || 7)))
const offerDisplayExpiry = computed(() => readOnlyOffer.value && form.expiresAt ? String(form.expiresAt).slice(0, 10) : offerExpiryDate.value)
function validateForm() {
  for (const field of fields.value) {
    if (field.required && field.type !== 'salary' && (form[field.key] === undefined || form[field.key] === null || String(form[field.key]).trim() === '')) return field.type === 'application' ? '该人才还没有可关联的应聘流程，请先建立应聘经历' : `请填写${field.label}`
  }
  if (fields.value.some(field => field.type === 'salary') && (form.salaryMin === '' || form.salaryMax === '' || form.salaryMin == null || form.salaryMax == null || !Number.isFinite(Number(form.salaryMin)) || !Number.isFinite(Number(form.salaryMax)) || Number(form.salaryMin) < 0 || Number(form.salaryMax) < Number(form.salaryMin))) return '请填写有效的薪资范围，最高金额应不低于最低金额'
  if (form.startDate && form.endDate && form.endDate < form.startDate) return '结束日期不能早于开始日期'
  if (props.recordType === 'interviews' && props.mode === 'schedule' && !props.record?.id && String(form.scheduledAt || '').slice(0, 10) < todayLocal()) return '面试时间不能早于今天'
  if (props.recordType === 'offers' && String(form.expectedStartDate || '').slice(0, 10) < todayLocal()) return '计划入职日期不能早于今天'
  const needsReason = ['已拒绝', '已撤回', '候选人退出', '公司淘汰', '岗位取消', '已离职'].includes(form.status) || ['未通过', '拒绝Offer', '候选人退出'].includes(form.result) || form.type === 'departure'
  if (needsReason && !String(form.reason || '').trim()) return '请填写原因，让未来的复盘有据可查'
  if (props.recordType === 'interviews' && props.mode === 'evaluation' && (!String(form.feedback || '').trim() || !form.result || form.result === '待评价' || form.score === '' || form.score == null)) return '请填写评分、推荐结论和评价依据'
  if (props.recordType === 'interviews' && props.mode === 'evaluation' && (Number(form.score) < 1 || Number(form.score) > 5)) return '综合评分请填写 1 到 5 分'
  if (props.recordType === 'interviews' && ['已取消', '未出席', '已改期'].includes(form.status) && !String(form.reason || '').trim()) return '请记录改期、取消或未出席的原因'
  if (props.recordType === 'employments' && form.status === '已离职' && !form.endDate) return '请填写离职日期'
  return ''
}
function confirmOfferIssue() {
  attempted.value = true
  error.value = validateForm()
  if (error.value) return
  offerConfirmOpen.value = true
}
async function issueConfirmedOffer() {
  if (saving.value) return
  offerConfirmOpen.value = false
  await save(true)
}
function applyApplicationDefaults() {
  const application = (props.person.applications || []).find(item => String(item.id) === String(form.applicationId))
  if (!application) return
  if (!form.owner) form.owner = application.owner || props.person.owner || ''
}
const closedApplicationStatuses = new Set(['已入职', '候选人退出', '公司淘汰', '已关闭'])
function resolveApplicationId() {
  if (!['offers', 'interviews'].includes(props.recordType)) return
  const applications = props.person.applications || []
  if (!applications.length) { form.applicationId = ''; return }
  if (applications.some(item => String(item.id) === String(form.applicationId))) return
  const activeApplications = applications.filter(item => !closedApplicationStatuses.has(item.status))
  const currentJobId = props.person.jobId
  const preferred = activeApplications.find(item => String(item.jobId) === String(currentJobId))
    || activeApplications[0]
    || applications.find(item => String(item.jobId) === String(currentJobId))
    || applications[0]
  form.applicationId = preferred?.id || ''
}
function isMissing(field) { if (field.type === 'salary') return form.salaryMin === '' || form.salaryMin == null || form.salaryMax === '' || form.salaryMax == null; return form[field.key] === undefined || form[field.key] === null || String(form[field.key]).trim() === '' }
watch(() => props.modelValue, value => {
  if (!value) { offerConfirmOpen.value = false; return }
  Object.keys(form).forEach(key => delete form[key])
  getHrList().then(data => { employees.value = data; if (!form.owner || !data.some(employee => employee.name === form.owner)) form.owner = (data.find(employee => employee.current) || data[0])?.name || ''; applyApplicationDefaults() }).catch(cause => { employees.value = []; error.value = cause.message || 'HR 名单加载失败' })
  const now = nowLocal()
  const defaults = {
    opportunities: { startedAt: now.slice(0, 10), status: '新发现', companyIntent: '未判断', talentIntent: '未知', owner: props.person.owner || '' },
    applications: { jobId: props.person.jobId || '', startedAt: now.slice(0, 10), status: '沟通中', companyIntent: '未判断', talentIntent: '未知', owner: props.person.owner || '' },
    interviews: { scheduledAt: now, round: '初面', method: '现场', status: '待面试', result: '待评价', owner: props.person.owner || '' },
    offers: { recipientEmail: props.person.email || '', salaryMode: 'monthly', actualSalary: '', salaryMonths: 13, probationMonths: 3, socialInsurance: '五险一金', expectedStartDate: todayLocal(), validityDays: 7, expiresAt: addDays(todayLocal(), 7), owner: props.person.owner || '' },
    compensations: { type: '沟通后期望', occurredAt: now, currency: 'CNY', period: '月' },
    experiences: { type: '工作', startDate: now.slice(0, 10) },
    employments: { jobId: props.person.jobId || '', startDate: now.slice(0, 10), status: '试用期' },
    collaborations: { startDate: now.slice(0, 10), status: '待启动' }
  }
  Object.assign(form, defaults[props.recordType] || {}, props.initial || {}, props.record || {})
  if (props.recordType === 'offers') {
    if (!form.recipientEmail) form.recipientEmail = props.person.email || ''
    form.salaryMode = form.salaryMode || (form.annualSalary && !form.actualSalary ? 'annual' : 'monthly')
    form.validityDays = [7, 15].includes(Number(form.validityDays)) ? Number(form.validityDays) : 7
    if (!readOnlyOffer.value) form.expiresAt = addDays(todayLocal(), form.validityDays)
  }
  attempted.value = false
  fields.value.forEach(field => {
    if (field.type === 'date-picker' && form[field.key]) {
      const value = String(form[field.key])
      form[field.key] = field.withTime ? value.slice(0, 16) : value.slice(0, 10)
    }
  })
  resolveApplicationId()
  applyApplicationDefaults()
  error.value = ''
}, { immediate: true })
watch(() => form.applicationId, applyApplicationDefaults)
watch(() => props.person.applications, () => {
  if (!props.modelValue) return
  resolveApplicationId()
  applyApplicationDefaults()
}, { deep: true })

async function save(issueOffer = false) {
  if (saving.value || readOnlyOffer.value) return
  attempted.value = true
  error.value = ''
  error.value = validateForm()
  if (error.value) return
  const payload = { ...form }
  if (props.recordType === 'interviews' && props.mode === 'evaluation') payload.status = '已完成'
  if (props.recordType === 'offers') {
    payload.expiresAt = offerExpiryDate.value
    payload.status = issueOffer ? '已完成' : '已创建'
    if (payload.salaryMode === 'annual') {
      delete payload.actualSalary
      delete payload.salaryMonths
    } else {
      delete payload.annualSalary
    }
  }
  for (const key of ['salaryMin', 'salaryMax', 'actualSalary', 'annualSalary', 'salaryMonths', 'validityDays', 'probationMonths', 'amount', 'score']) {
    if (payload[key] !== undefined && payload[key] !== '' && payload[key] !== null) payload[key] = Number(payload[key])
    else delete payload[key]
  }
  try {
    saving.value = true
    if (props.record?.id) await updateRecord(props.person.id, props.recordType, props.record.id, payload)
    else { delete payload.id; delete payload.revision; delete payload.version; await createRecord(props.person.id, props.recordType, payload) }
    ElMessage.success(props.recordType === 'offers' && issueOffer ? 'Offer 邮件已发送，等待候选人回复' : (props.recordType === 'offers' ? 'Offer 草稿已保存' : '记录已保存'))
    visible.value = false
    emit('saved')
  } catch (cause) { error.value = cause.response?.data?.message || cause.message || '保存失败，请稍后重试' }
  finally { saving.value = false }
}

function exportOffer() {
  const job = selectedPosition.value?.name || selectedApplication.value?.jobName || '岗位待确认'
  const company = selectedPosition.value?.company || selectedApplication.value?.company || '公司待确认'
  const lines = [
    'Offer 录用信息',
    `候选人：${props.person.name || '人才'}`,
    `发送邮箱：${form.recipientEmail || '未记录'}`,
    `公司：${company}`,
    `岗位：${job}`,
    `薪资方案：${offerSalaryPreview.value}`,
    `岗位预算：${positionBudgetText.value}`,
    `试用期：${Number(form.probationMonths) ? `${form.probationMonths} 个月` : '无试用期'}`,
    `社保与公积金：${form.socialInsurance || '未记录'}`,
    `计划入职：${form.expectedStartDate || '未记录'}`,
    `Offer 有效期至：${form.expiresAt || offerExpiryDate.value}`,
    `负责 HR：${form.owner || '未记录'}`,
    `补充说明：${form.remark || '无'}`
  ]
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${String(props.person.name || '人才').replace(/[\\/:*?"<>|]/g, '-')}-${String(job).replace(/[\\/:*?"<>|]/g, '-')}-Offer.txt`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('Offer 已导出')
}
</script>

<style scoped>
.record-fields{margin:0;padding:0;border:0;min-width:0}
.record-dialog-intro{color:#7b8190;line-height:1.8;font-size:13px;margin:0 0 24px}.record-fields{display:grid;grid-template-columns:1fr 1fr;gap:20px}.record-field{display:flex;flex-direction:column;gap:9px;min-width:0;color:#4d5563;font-size:13px;font-weight:550}.record-field>b,.record-field>span>b{display:inline-flex;margin-left:5px;padding:2px 6px;border-radius:999px;background:#fff0ed;color:#c75d4b;font-size:9px;font-weight:700}.record-field-wide{grid-column:1/-1}.record-field input,.record-field select,.record-field textarea{width:100%;box-sizing:border-box;min-height:43px;border:1px solid #e0e4e9;border-radius:11px;padding:10px 12px;background:#f8f9fb;color:#313947;font:inherit;outline:none;transition:border-color .2s,box-shadow .2s}.record-field input:focus,.record-field select:focus,.record-field textarea:focus{border-color:#92a3b9;box-shadow:0 0 0 3px #7b8fa614}.record-field-missing input,.record-field-missing select,.record-field-missing textarea,.record-field-missing .record-choices,.record-field-missing .record-salary{background:#fff3f1!important;border-color:#df7667!important;animation:record-required-flash .42s ease 2}.record-field-missing .record-choices,.record-field-missing .record-salary{padding:9px;border:1px solid;border-radius:11px}.record-field textarea{resize:vertical;line-height:1.7}.record-choices{display:flex;flex-wrap:wrap;gap:7px}.record-choices button{border:1px solid #e2e5e9;color:#777e8a;background:#fafbfc;border-radius:9px;min-height:35px;padding:7px 12px;font:inherit;font-size:12px;cursor:pointer;transition:all .2s}.record-choices button:hover{background:#edf0f3}.record-choices button.selected{background:#e5ebf1;color:#405569;border-color:#bcc9d6;box-shadow:0 2px 4px #26354607}.record-salary{display:grid;grid-template-columns:1fr 20px 1fr auto;align-items:center;gap:8px}.record-salary>span{text-align:center;color:#8c929b}.record-salary small{white-space:nowrap;font-size:11px;color:#8c929b}.record-link-note{margin-top:22px;padding:12px 15px;color:#677b70;background:#edf2ee;border-radius:10px;font-size:12px}.record-error{background:#f7ecea;color:#985d53;border-radius:10px;padding:12px 15px;line-height:1.6}.record-dialog-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:28px;border-top:1px solid #edf0f2;padding-top:20px}@keyframes record-required-flash{50%{box-shadow:0 0 0 4px rgba(213,89,70,.17)}}@media(max-width:580px){.record-fields{grid-template-columns:1fr}.record-salary{grid-template-columns:1fr 14px 1fr}.record-salary small{grid-column:1/-1}}
.record-field input,.record-field select,.record-field textarea{background:#fff}.record-field input:disabled,.record-field select:disabled,.record-field textarea:disabled,.record-field .auto-filled{background:#f0f2f5!important;color:#818995;cursor:not-allowed}.auto-note{font-size:10px;color:#8896a8;font-weight:400}
.application-reference{display:grid;gap:5px;min-height:66px;box-sizing:border-box;padding:12px 14px;border:1px solid #dbe5f3;border-radius:11px;background:linear-gradient(135deg,#f7faff,#eef4ff)}.application-reference strong{color:#344c67;font-size:13px}.application-reference small{color:#71839a;font-size:11px;font-weight:400}.application-reference em{color:#2f6fe6;font-size:10px;font-style:normal;font-weight:500}.application-reference.empty{border-color:#e6d7d2;background:#fff7f4}.application-reference.empty strong{color:#9a5a4c}.application-reference.empty small{color:#9b7c74}
.interview-context{display:flex;align-items:center;justify-content:space-between;gap:14px;min-height:48px;padding:12px 14px;border:1px solid #e0dcf2;border-radius:11px;background:#f8f6ff}.interview-context strong{color:#50466c;font-size:13px}.interview-context small{color:#877f9c;font-size:10px;font-weight:400;text-align:right}
.record-field-missing :deep(.hr-picker>input){background:#fff3f1!important;border-color:#df7667!important;animation:record-required-flash .42s ease 2}
.budget-reference{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:13px 15px;border:1px solid #dfe5ec;border-radius:11px;background:#f3f6f9}.budget-reference strong{font-size:14px;color:#40566d}.budget-reference small{font-size:10px;color:#8b98a6;font-weight:400;text-align:right}
.annual-salary-preview{min-height:43px;box-sizing:border-box;display:flex;align-items:center;justify-content:space-between;gap:14px;padding:10px 14px;border:1px solid #dbe5f3;border-radius:11px;background:linear-gradient(135deg,#f5f8ff,#edf3ff)}.annual-salary-preview small{color:#8290a4;font-size:10px;font-weight:400}.annual-salary-preview strong{color:#2868e8;font-size:15px;white-space:nowrap}
.offer-form-layout{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(300px,.65fr);gap:25px;align-items:start}.offer-form-layout>.record-error,.offer-form-layout>.record-dialog-actions{grid-column:1/-1}.offer-live-preview{position:sticky;top:0}.offer-preview-paper{overflow:hidden;border:1px solid #dce5f1;border-radius:18px;background:linear-gradient(155deg,#fff 0%,#f9fbff 72%,#eef4ff 100%);box-shadow:0 16px 42px rgba(50,77,118,.12);padding:24px;color:#40536a}.offer-preview-paper header{display:flex;align-items:center;gap:12px;padding-bottom:18px;border-bottom:1px solid #e5ebf3}.offer-preview-brand{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:#3975ef;color:#fff;font-size:19px;font-weight:700;box-shadow:0 8px 18px #3975ef35}.offer-preview-paper header small,.offer-preview-paper header strong{display:block}.offer-preview-paper header small{color:#3773ea;font-size:8px;letter-spacing:1.7px}.offer-preview-paper header strong{margin-top:5px;font-size:15px}.offer-preview-person{padding:20px 0 16px}.offer-preview-person small{font-size:9px;color:#98a5b5}.offer-preview-person h3{margin:6px 0 5px;font-size:22px;color:#26384e}.offer-preview-person p{margin:0;font-size:11px;color:#7e8da0}.offer-preview-paper dl{display:grid;gap:1px;margin:0;overflow:hidden;border-radius:12px;background:#e6ebf2}.offer-preview-paper dl>div{display:grid;grid-template-columns:105px 1fr;gap:10px;padding:11px 12px;background:#f8faff}.offer-preview-paper dt{font-size:9px;color:#9aa6b5}.offer-preview-paper dd{margin:0;font-size:10px;color:#52677f;font-weight:550}.offer-preview-remark{min-height:35px;margin:15px 0;padding:11px;border-radius:10px;background:#f2f6fc;color:#7d8da0;font-size:10px;line-height:1.7}.offer-preview-paper footer{text-align:center;color:#a3adba;font-size:8px;letter-spacing:.3px}@media(max-width:880px){.offer-form-layout{grid-template-columns:1fr}.offer-live-preview{position:static;order:2}.offer-form-layout>.record-dialog-actions{order:3}}
.offer-confirm-card{display:grid;grid-template-columns:52px 1fr;gap:18px;align-items:start;padding:8px 2px 4px}.offer-confirm-icon{display:grid;place-items:center;width:52px;height:52px;border-radius:16px;background:linear-gradient(145deg,#edf4ff,#dce9ff);color:#2468e8;font-size:24px;font-weight:800}.offer-confirm-card small{display:block;color:#3474ed;font-size:11px;font-weight:700;letter-spacing:1.4px}.offer-confirm-card h2{margin:7px 0 15px;color:#1f2937;font-size:22px}.offer-confirm-card p{margin:0 0 9px;color:#5f6b7a;line-height:1.7}.offer-confirm-card strong{display:block;padding:12px 14px;border-radius:11px;background:#f3f6fa;color:#24364d;word-break:break-all}.offer-confirm-card .offer-confirm-note{margin:15px 0 0;padding:12px 14px;border-radius:11px;background:#f0f6ff;color:#47617f;font-size:13px}.offer-confirm-actions{display:flex;justify-content:flex-end;gap:10px}@media(max-width:580px){.offer-confirm-card{grid-template-columns:1fr}.offer-confirm-icon{width:44px;height:44px}.offer-confirm-actions{display:grid;grid-template-columns:1fr 1fr}}
</style>

