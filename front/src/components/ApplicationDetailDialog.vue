<script setup>
import { computed } from 'vue'
import { dateOnly, dateTime } from '@/utils/format'

const props = defineProps({
  modelValue: Boolean,
  application: { type: Object, default: null },
  photoUrl: { type: String, default: '' },
  position: { type: Object, default: null },
  communicationOnly: Boolean,
  events: { type: Array, default: () => [] },
  interviews: { type: Array, default: () => [] },
  offers: { type: Array, default: () => [] },
  employments: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'record-communication', 'update-status', 'next-action', 'edit-interview', 'edit-offer'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
function belongs(record) {
  if (!props.application) return false
  if (record.applicationId != null) return String(record.applicationId) === String(props.application.id)
  return String(record.personId) === String(props.application.personId) && (!record.jobId || String(record.jobId) === String(props.application.jobId))
}
const communicationRecords = computed(() => props.events.filter(record => belongs(record) && (record.type === 'communication' || record.channel)))
const interviewRecords = computed(() => props.interviews.filter(belongs))
const offerRecords = computed(() => props.offers.filter(belongs))
const employmentRecords = computed(() => props.employments.filter(belongs))
const latestInterview = computed(() => interviewRecords.value[0] || null)
const hasCompletedInterview = computed(() => interviewRecords.value.some(record => record.status === '已完成' && record.result && !['待评价', '不推荐'].includes(record.result)))
const nextAction = computed(() => {
  const status = props.application?.status
  if (['已创建','初筛','沟通','沟通中','待联系','新建','人才储备'].includes(status)) return 'interview'
  if (['面试安排','面试中','内部决策','面试'].includes(status)) {
    if (!latestInterview.value) return 'interview'
    if (!hasCompletedInterview.value) return 'interview-result'
    return 'offer'
  }
  if (['谈薪','Offer','Offer中','待入职'].includes(status)) return 'hire'
  return ''
})
const nextLabel = computed(() => {
  return { interview: '安排面试', 'interview-result': '填写面试结果', offer: '创建 Offer', hire: '办理入职' }[nextAction.value] || ''
})
</script>

<template>
  <el-dialog v-model="visible" :title="communicationOnly ? '沟通记录' : '应聘详情'" width="min(900px, 95vw)" top="5vh" class="application-detail-dialog" destroy-on-close>
    <div v-if="application" class="application-detail">
      <header>
        <span class="application-avatar"><img v-if="photoUrl" :src="photoUrl" :alt="application.personName + '的证件照'" /><template v-else>{{ application.personName?.slice(0, 1) || '人' }}</template></span>
        <div><h2>{{ application.personName }}</h2><p>{{ application.company || '未设置公司' }} · {{ application.jobName || '岗位待补充' }} · {{ application.jobCode || String(application.jobId || '').padStart(3, '0') }}</p></div>
        <span class="badge" :class="application.status === '已入职' ? 'green' : ''">{{ application.status }}</span>
      </header>
      <section class="position-focus">
        <div class="position-focus-heading"><span>本次应聘岗位</span><strong>{{ application.jobName || position?.name || '岗位待补充' }}</strong></div>
        <dl><div><dt>所属公司</dt><dd>{{ application.company || position?.company || '待补充' }}</dd></div><div><dt>所属部门</dt><dd>{{ position?.department || '待补充' }}</dd></div><div><dt>岗位薪资</dt><dd>{{ position?.minSalary != null && position?.maxSalary != null ? `${position.minSalary} — ${position.maxSalary} K / 月` : '待补充' }}</dd></div><div><dt>工作地点</dt><dd>{{ position?.baseLocation || position?.location || application.baseLocation || '待补充' }}</dd></div></dl>
        <div class="position-copy"><article><b>岗位介绍</b><p>{{ position?.description || '暂无岗位介绍' }}</p></article><article><b>任职要求</b><p>{{ position?.requirements || '暂无任职要求' }}</p></article></div>
      </section>
      <dl class="application-facts">
        <div><dt>进入流程</dt><dd>{{ dateOnly(application.startedAt || application.createdAt) }}</dd></div>
        <div><dt>负责 HR</dt><dd>{{ application.owner || '待分配' }}</dd></div>
        <div><dt>公司意愿</dt><dd>{{ application.companyIntent || '未判断' }}</dd></div>
        <div><dt>人才意愿</dt><dd>{{ application.talentIntent || '未知' }}</dd></div>
        <div><dt>下一步</dt><dd>{{ application.nextStep || '待安排' }}</dd></div>
        <div><dt>下次跟进</dt><dd>{{ application.nextContactAt ? dateTime(application.nextContactAt) : '待安排' }}</dd></div>
      </dl>

      <section class="application-chapter">
        <div class="chapter-heading"><div><small>COMMUNICATION</small><h3>沟通记录</h3></div><button class="text-link" @click="emit('record-communication', application)">＋ 记录沟通</button></div>
        <div v-if="communicationRecords.length" class="chapter-list"><article v-for="record in communicationRecords" :key="record.id"><time>{{ dateTime(record.occurredAt || record.createdAt) }}</time><b>{{ record.title || '沟通记录' }}</b><p>{{ record.summary || record.result || '已记录本次沟通' }}</p><span v-if="record.result">{{ record.result }}</span></article></div>
        <p v-else class="chapter-empty">还没有单独的沟通记录。</p>
      </section>

      <section v-if="!communicationOnly" class="application-chapter">
        <div class="chapter-heading"><div><small>INTERVIEW</small><h3>面试安排与结果</h3></div><button class="text-link" @click="emit('edit-interview', null, 'schedule')">＋ 安排面试</button></div>
        <div v-if="interviewRecords.length" class="chapter-list"><article v-for="record in interviewRecords" :key="record.id"><time>{{ dateTime(record.scheduledAt || record.createdAt) }}</time><b>{{ record.round || '面试' }} · {{ record.method || '方式待补充' }} · 面试官 {{ record.owner || '待安排' }}</b><p>{{ record.status === '已完成' ? (record.feedback || '面试已完成，结果待补充') : (record.reason || '面试尚未完成，暂不显示结论') }}</p><span>{{ record.status === '已完成' && record.result && record.result !== '待评价' ? record.result : (record.status || '待面试') }}</span><button class="chapter-action" @click="emit('edit-interview', record, 'evaluation')">{{ record.status === '已完成' ? '查看 / 修改结果' : '填写结果' }}</button></article></div>
        <p v-else class="chapter-empty">还没有面试记录。</p>
      </section>

      <section v-if="!communicationOnly" class="application-chapter">
        <div class="chapter-heading"><div><small>OFFER</small><h3>Offer 记录</h3></div><button v-if="hasCompletedInterview" class="text-link" @click="emit('edit-offer', application)">＋ 发放 Offer</button><span v-else class="offer-locked-note">填写面试结果后可发 Offer</span></div>
        <div v-if="offerRecords.length" class="chapter-list"><article v-for="record in offerRecords" :key="record.id"><time>{{ dateTime(record.updatedAt || record.createdAt) }}</time><b>第 {{ record.version || 1 }} 版 · {{ record.status || '已创建' }}</b><p>{{ record.salaryMode === 'annual' ? `${record.annualSalary || '—'} K / 年` : `${record.actualSalary || '—'} K / 月 × ${record.salaryMonths || '—'} 薪` }}</p><span>计划入职 {{ dateOnly(record.expectedStartDate) }}</span></article></div>
        <p v-else class="chapter-empty">还没有 Offer 记录。</p>
      </section>

      <section v-if="!communicationOnly && employmentRecords.length" class="application-chapter"><div class="chapter-heading"><div><small>EMPLOYMENT</small><h3>入职与任职</h3></div></div><div class="chapter-list"><article v-for="record in employmentRecords" :key="record.id"><time>{{ dateOnly(record.startDate) }}</time><b>{{ record.status || '任职中' }}</b><p>{{ record.remark || record.reason || '已建立员工记录' }}</p></article></div></section>
      <p v-if="application.reason" class="application-reason">当前原因：{{ application.reason }}</p>
    </div>
    <template #footer><div class="dialog-actions"><button class="btn" @click="visible = false">关闭</button><template v-if="!communicationOnly"><button class="btn" @click="emit('update-status', application)">更新状态</button><button v-if="nextLabel" class="btn btn-primary" @click="emit('next-action', application, nextAction)">{{ nextLabel }}</button></template></div></template>
  </el-dialog>
</template>

<style scoped>
.application-detail header{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:14px;padding-bottom:18px;border-bottom:1px solid #e9edf2}.application-avatar{display:grid;place-items:center;width:48px;height:48px;border-radius:15px;overflow:hidden;background:#e7efff;color:#3470df;font-weight:700}.application-avatar img{width:100%;height:100%;object-fit:cover}.application-detail h2{margin:0;color:#34475c;font-size:20px}.application-detail header p{margin:6px 0 0;color:#8492a2;font-size:11px}.application-facts{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;overflow:hidden;margin:20px 0;border-radius:12px;background:#e8edf3}.application-facts>div{padding:12px;background:#f7f9fc}.application-facts dt{font-size:9px;color:#99a5b3}.application-facts dd{margin:6px 0 0;color:#596d84;font-size:11px}.application-chapter{padding:18px 0;border-top:1px solid #edf0f4}.chapter-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.chapter-heading small{display:block;color:#4980e4;font-size:8px;letter-spacing:1.4px}.chapter-heading h3{margin:4px 0 0;color:#40546c;font-size:14px}.chapter-heading>span{color:#8f9aa8;font-size:10px}.chapter-list{display:grid;gap:8px;margin-top:12px}.chapter-list article{display:grid;grid-template-columns:125px 1fr auto;gap:8px 14px;padding:11px 12px;border-radius:10px;background:#f7f9fc}.chapter-list time{color:#97a2b0;font-size:9px}.chapter-list b{color:#53667d;font-size:11px}.chapter-list p{grid-column:2;margin:0;color:#7e8b99;font-size:10px}.chapter-list article>span{grid-column:3;grid-row:1/3;color:#4d75aa;font-size:9px}.chapter-empty{margin:12px 0 0;color:#a0a9b4;font-size:10px}.application-reason{padding:11px 13px;border-radius:9px;background:#fff5ee;color:#946f54;font-size:11px}@media(max-width:650px){.application-facts{grid-template-columns:repeat(2,1fr)}.chapter-list article{grid-template-columns:1fr}.chapter-list p,.chapter-list article>span{grid-column:1;grid-row:auto}}
.position-focus{margin:18px 0;padding:18px;border:1px solid #d8e4f4;border-radius:16px;background:linear-gradient(135deg,#f7faff,#edf4ff)}.position-focus-heading span,.position-focus-heading strong{display:block}.position-focus-heading span{color:#6480a5;font-size:9px;letter-spacing:1px}.position-focus-heading strong{margin-top:6px;color:#26496f;font-size:20px}.position-focus dl{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin:16px 0}.position-focus dl>div{padding:11px;border-radius:10px;background:#fff}.position-focus dt{color:#94a2b2;font-size:8px}.position-focus dd{margin:6px 0 0;color:#4e647d;font-size:10px}.position-copy{display:grid;grid-template-columns:1fr 1fr;gap:10px}.position-copy article{padding:12px;border-radius:10px;background:#ffffffad}.position-copy b{font-size:10px;color:#405b79}.position-copy p{margin:7px 0 0;color:#71849a;font-size:10px;line-height:1.7;white-space:pre-wrap}@media(max-width:650px){.position-focus dl{grid-template-columns:1fr 1fr}.position-copy{grid-template-columns:1fr}}
.chapter-action{grid-column:2;justify-self:start;border:0;background:transparent;padding:0;color:#3470df;font-size:9px;cursor:pointer}.offer-locked-note{color:#a07862!important}.chapter-list article:has(.chapter-action){grid-template-rows:auto auto auto}.chapter-list article:has(.chapter-action)>span{grid-row:1/4}
</style>
